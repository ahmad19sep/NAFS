import { NextRequest, NextResponse } from 'next/server'
import { authenticateRequest } from '@/lib/supabase/request'
import { aiStructured } from '@/lib/ai'
import { PLAN_SCHEMA, statusForAiFailure } from '@/lib/ai-schemas'
import { normalizeProposal, type PlanProposal } from '@/lib/plan'
import { ApiRouteError } from '@/lib/api-errors'
import { generateRecovery, intentSource, planningRequestId } from '@/lib/recovery-planning'
import { withStrictIdempotency } from '@/lib/strict-idempotency'

// A structured plan is slow on purpose: the model reasons before it writes,
// and a rich context makes it reason longer. Measured live at 36.6s, so the
// default serverless ceiling would kill a correct answer.
export const maxDuration = 60

/**
 * Two jobs, one contract.
 *
 * mode "plan" (default): turn "I want to work 12 hours a day for the next 30
 * days" into ONE proposal — the model decides whether that is a one-off task,
 * an ongoing habit, or a fixed-length challenge, and fills in the fields the
 * matching create route needs.
 *
 * mode "overcome": turn "I've been scrolling three hours a night" into a small
 * plan out of it — one to four proposals of the same shape, grounded in what
 * the app already knows: existing habits (so it builds on them rather than
 * duplicating), repeated misses, measured causes, screen-time apps, sleep,
 * and what the user has told the coach.
 *
 * Both PROPOSE. Neither creates. Proposals go back to the client, the user
 * sees them, and only their confirmation goes through /api/tasks, /api/habits
 * or /api/challenges — the same authorised mutations a manual entry uses.
 * Coach output is a suggestion until the user acts on it.
 */

const PLAN_SYSTEM = `You turn one plain-English intention into exactly ONE item for a self-accountability app. Choose the kind that fits how the person phrased it:

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

const RECOVERY_SYSTEM = `You help someone climb out of a slip — a bad habit they have fallen into, or a good one they have dropped. They describe it in their own words. You reply with a small plan: one to four items for a self-accountability app, each a task, a habit or a challenge, in the same JSON shape as a single plan item.

How to build it:
- "problem": the slip restated in one plain line, so they can see you understood.
- "approach": two or three sentences on the idea behind the plan, in their words where possible. Replace, don't just remove — a slot that scrolling filled needs something in it. Make the environment do the work before willpower does: "phone charging in the kitchen" beats "scroll less". Start today with something too small to fail.
- "steps", in this order:
  1. One TASK for today — the first move, concrete, under fifteen minutes. Priority high.
  2. One HABIT that fills the gap or blocks the trigger. Daily, small, "simple" unless they gave a number.
  3. Optionally one CHALLENGE, 7–30 days, only if the slip is severe or they asked for accountability. Default 14 days and say so in its reason.
  4. Optionally one more task or habit if it clearly earns its place. Never four for the sake of four.
- Never create a habit that is already in existing_habits. If an existing habit fits the slot — Reading while they would be scrolling — propose a task for today that does it, or a challenge built around it, and say so in the reason.
- Use what the app knows when it is there: if phone_screen_time names an app, name that app in the problem or a step; if sleep was short on missed days, the plan should touch sleep; if coach_memory holds a reason they gave, the plan should answer that reason.
- A limit is not a duration. Habit type "duration" means time SPENT doing something ("read 20 min") and needs time_target_mins. "No more than", "at most", "off by 11pm" is a "simple" yes/no habit ("Phone in the kitchen by 11pm").
- Never invent a time, a count, or a length they did not give, except the challenge default above. due_time only when they named a clock time. requires_photo false unless they asked for photo proof. Never shame. No lectures — "approach" is the only prose.
- Each step's "reason": one or two sentences on how it counters the problem, and any default you assumed. Shown to the user.
- Keep titles short and concrete. One fitting emoji each.

Shape — top level: { "problem": string, "approach": string, "steps": [ step, ... ] }.
Each step: { "kind": "task" | "habit" | "challenge", "title": string, "emoji": string, "reason": string, and ONLY the block for its kind:
  "task": { "priority": "low" | "medium" | "high", "due_time"?: "HH:MM", "note"?: string }
  "habit": { "type": "simple" | "counter" | "duration", "target_value"?: number, "unit"?: string, "time_target_mins"?: number, "schedule_kind": "daily" | "weekdays", "schedule_days"?: ["mon", ...] }
  "challenge": { "frequency": "daily" | "weekly" | "monthly" | "yearly", "duration_days": number, "requires_photo": boolean } }


Worked example of one step. The fields live INSIDE the block named after "kind" — never beside it:
{ "kind": "task", "title": "Move the charger to the kitchen", "emoji": "🔌", "reason": "So the phone is not the thing within reach at 11pm.", "task": { "priority": "high", "note": "Kitchen counter, not the bedroom." } }
A step whose fields are written flat, beside "kind" instead of inside the block, is wrong.
Reply with a single valid JSON value and nothing else.`

export async function POST(req: NextRequest) {
  const headers = { 'Cache-Control': 'no-store' }
  try {
    const auth = await authenticateRequest(req)
    const text = await req.text()
    if (text.length > 12_000) throw new ApiRouteError(413, 'invalid_request', 'This planning request is too long.')
    let body: any
    try { body = JSON.parse(text) } catch { throw new ApiRouteError(400, 'invalid_request', 'Provide a valid planning request.') }
    if (!body || typeof body !== 'object' || Array.isArray(body)) throw new ApiRouteError(400, 'invalid_request', 'Provide a valid planning request.')
    const source = intentSource(body.intent)
    const requestId = planningRequestId(body.request_id, auth.mode === 'bearer')
    const mode = body.mode === 'overcome' ? 'overcome' : 'plan'
    if (mode === 'overcome') return NextResponse.json(await generateRecovery(auth, source, requestId, RECOVERY_SYSTEM), { headers })
    const generate = async () => {
      const result = await aiStructured<PlanProposal>(JSON.stringify({ intent: 'intent' in source ? source.intent : '' }), PLAN_SCHEMA, PLAN_SYSTEM)
      if (!result.ok) throw new ApiRouteError(statusForAiFailure(result.code), result.code === 'not_configured' ? 'not_configured' : 'ai_unavailable', 'The coach could not propose an item right now.')
      const checked = normalizeProposal(result.data)
      if (!checked.ok) throw new ApiRouteError(502, 'invalid_response', 'The coach returned an unusable proposal. Try rephrasing.')
      return { proposal: checked.proposal }
    }
    if (requestId) {
      const { result, replayed } = await withStrictIdempotency(auth.supabase, auth.user.id, requestId, { operation: 'single-plan-v1', source }, generate)
      return NextResponse.json({ ...result, replayed, request_id: requestId }, { headers })
    }
    return NextResponse.json(await generate(), { headers })
  } catch (error) {
    if (error instanceof ApiRouteError) return NextResponse.json({ code: error.code, error: error.message }, { status: error.status, headers })
    return NextResponse.json({ code: 'backend_unavailable', error: 'The plan could not be confirmed. Retry the same request.' }, { status: 503, headers })
  }
}
