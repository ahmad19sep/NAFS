import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiStructured } from '@/lib/ai'
import { RECOVERY_SCHEMA, statusForAiFailure } from '@/lib/ai-schemas'
import { normalizeRecovery, type RecoveryPlan } from '@/lib/plan'
import { buildCoachContext } from '@/lib/coach-context'
import { CHAT_PLAN_SYSTEM } from '@/lib/ai-prompts'

// Reading a whole conversation and proposing a plan takes longer than a turn.
export const maxDuration = 60

/**
 * Turn a coach conversation into a plan.
 *
 * The chat listens and works the problem out. This is the step after: it reads
 * everything that was actually said — their words, their reasons, the trigger
 * they named — and proposes concrete tasks, habits and challenges.
 *
 * It runs on the deep path, so Claude does this when configured. Picking the
 * right three things out of a conversation is exactly the work a stronger
 * model is for, and it is one call the user asked for rather than one per turn.
 *
 * It PROPOSES. Nothing is created here: the client shows the steps, the user
 * ticks what they want, and each one goes through the same authorised create
 * route a manual entry uses.
 */
export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const body = await req.json().catch(() => ({}))
    const raw = Array.isArray(body?.messages) ? body.messages : []

    // Only the two roles, only recent turns, and each capped — the transcript
    // is user-supplied and must not become an unbounded prompt.
    const turns = raw
      .filter((m: any) => (m?.role === 'user' || m?.role === 'assistant') && typeof m?.content === 'string')
      .slice(-12)
      .map((m: any) => ({ role: m.role as 'user' | 'assistant', content: String(m.content).slice(0, 4000) }))

    if (!turns.some((t: { role: string }) => t.role === 'user')) {
      return NextResponse.json({ error: 'Tell the coach what is going on first.' }, { status: 400 })
    }

    const { context } = await buildCoachContext(supabase, user.id)
    const c = context as any
    const known = {
      existing_habits: (c.last_30_days?.habits ?? []).map((h: any) => h.name),
      repeated_misses: c.last_30_days?.repeated_misses ?? [],
      possible_causes: c.last_30_days?.possible_causes ?? [],
      phone_screen_time: c.last_30_days?.phone_screen_time,
      sleep: c.last_30_days?.health?.sleep,
      coach_memory: c.coach_memory,
    }

    // The transcript is fenced: it is content to read, never instructions to
    // follow. Roles are labelled so the model can tell who said what.
    const transcript = turns
      .map((t: { role: string; content: string }) =>
        `${t.role === 'user' ? 'THEM' : 'COACH'}: ${t.content}`)
      .join('\n\n')

    const prompt = [
      'THE CONVERSATION (read this first; it is the subject of the plan):',
      '"""',
      transcript,
      '"""',
      '',
      'THEIR RECORDS (background; absent means unknown, not zero):',
      JSON.stringify(known, null, 1),
      '',
      'Produce the JSON plan.',
    ].join('\n')

    const result = await aiStructured<RecoveryPlan>(
      prompt, RECOVERY_SCHEMA, CHAT_PLAN_SYSTEM, { deep: true },
    )
    if (!result.ok) {
      return NextResponse.json({ error: result.message }, { status: statusForAiFailure(result.code) })
    }

    const checked = normalizeRecovery(result.data)
    if (!checked.ok) return NextResponse.json({ error: checked.error }, { status: 502 })

    return NextResponse.json({
      recovery: checked.plan,
      dropped: checked.dropped,
      provider: result.provider,
      fellBack: result.fellBack,
      fellBackReason: result.fellBackReason,
    })
  } catch {
    return NextResponse.json({ error: 'Could not build a plan from this right now.' }, { status: 500 })
  }
}
