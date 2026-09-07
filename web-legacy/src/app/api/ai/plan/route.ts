import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiStructured } from '@/lib/ai'
import { PLAN_SCHEMA, statusForAiFailure } from '@/lib/ai-schemas'
import { normalizeProposal, type PlanProposal } from '@/lib/plan'

/**
 * Turn "I want to work 12 hours a day for the next 30 days" into a proposal.
 *
 * The user says what they want in their own words; the model decides whether
 * that is a one-off task, an ongoing habit, or a fixed-length challenge, and
 * fills in the fields the matching create route needs.
 *
 * This route PROPOSES. It never creates. The proposal goes back to the client,
 * the user sees it, and only their confirmation goes through /api/tasks,
 * /api/habits or /api/challenges — the same authorised mutations a manual
 * entry uses. Coach output is a suggestion until the user acts on it.
 */

const SYSTEM = `You turn one plain-English intention into exactly ONE item for a self-accountability app. Choose the kind that fits how the person phrased it:

- task — done once. "today", "this once", "by Friday", a single deliverable.
- habit — ongoing, repeats indefinitely. "every day", "daily", "build the habit", "from now on".
- challenge — a fixed-length commitment with a start and an end. "for the next 30 days", "for a month", "30-day", "streak of".

Fill ONLY the block for the kind you chose. Rules:
- Hours or minutes per day -> habit type "duration" with time_target_mins (12 hours = 720). A count per day -> "counter" with target_value and unit. Yes/no -> "simple".
- "for the next N days" with a daily action is a challenge with frequency "daily" and duration_days N. If the person ALSO clearly wants it to continue afterwards, still choose challenge — they can add a habit later.
- Never invent a due time, a deadline, or a duration the person did not state. If no length is given for a challenge, use 30 days and say so in the reason.
- Never invent a schedule. Default habits to "daily" unless specific days are named.
- Keep the title short and concrete, in the person's own words where possible. Pick one fitting emoji.
- "reason" is one or two plain sentences saying why this kind, and naming any default you had to assume. It is shown to the user.

Reply with a single valid JSON value and nothing else.`

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const body = await req.json().catch(() => ({}))
    const intent = typeof body?.intent === 'string' ? body.intent.trim() : ''
    if (intent.length < 3) {
      return NextResponse.json({ error: 'Say what you want to do.' }, { status: 400 })
    }
    if (intent.length > 500) {
      return NextResponse.json({ error: 'Keep it under 500 characters.' }, { status: 400 })
    }

    // The intention is data, not instructions — fenced so the model reads it
    // as content rather than a directive.
    const prompt = `The person wrote:\n"""\n${intent}\n"""\n\nProduce the JSON proposal.`

    const result = await aiStructured<PlanProposal>(prompt, PLAN_SCHEMA, SYSTEM)
    if (!result.ok) {
      return NextResponse.json({ error: result.message }, { status: statusForAiFailure(result.code) })
    }

    const checked = normalizeProposal(result.data)
    if (!checked.ok) return NextResponse.json({ error: checked.error }, { status: 502 })

    return NextResponse.json({ proposal: checked.proposal })
  } catch {
    return NextResponse.json({ error: 'Could not plan that right now.' }, { status: 500 })
  }
}
