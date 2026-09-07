import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiDeep, AiError } from '@/lib/ai'
import { buildCoachContext } from '@/lib/coach-context'
import { buildReport, periodRange, type ReportPeriod } from '@/lib/report'
import { fetchReportRows, toReportInput } from '@/lib/report-data'
import { summarizeReport, buildReportReviewPrompt } from '@/lib/growth'
import { REPORT_REVIEW_SYSTEM } from '@/lib/ai-prompts'
import { todayString } from '@/lib/utils'

export const dynamic = 'force-dynamic'
// A period of records, read by a model that thinks before it writes.
export const maxDuration = 60

const SELECT = 'id, period, start_date, content_md, model_used, generated_at'

/**
 * Write the coach's read for one weekly or monthly report.
 *
 * The printed report already carries every number and a ranked list of what to
 * improve. What it cannot do is explain the period in words. This does, and it
 * is stored against (period, start_date) so the printout carries it without a
 * second call — print it on Sunday, print it again in March, same text.
 *
 * Writing again for the same period replaces it: a report reflects its period,
 * and keeping a stale read beside fresher numbers would be worse than either.
 * Paid per call when Claude is configured, so the user starts it.
 */
export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json().catch(() => ({}))
  const period: ReportPeriod = body?.period === 'monthly' ? 'monthly' : 'weekly'
  const offset = Math.min(60, Math.max(0, Math.round(Number(body?.offset)) || 0))

  const today = todayString()
  const current = periodRange(period, offset, today)
  const previous = periodRange(period, offset + 1, today)

  // Fetched once, covering the comparison period too — the same range the
  // Reports page reads, so the review and the printout cannot disagree.
  const [rows, coach] = await Promise.all([
    fetchReportRows(supabase, user.id, previous.start, current.end),
    buildCoachContext(supabase, user.id),
  ])

  const report = buildReport(toReportInput(rows, { period, offset, today }))

  if (report.days_logged === 0) {
    return NextResponse.json({
      error: 'Nothing was logged in this period, so there is nothing to read yet.',
    }, { status: 400 })
  }

  // Only the slices that bear on "why" — the numbers themselves are in the
  // summary, and sending them twice would just cost tokens.
  const c = coach.context as any
  const context = {
    user: c.user,
    existing_habits: (c.last_30_days?.habits ?? []).map((h: any) => h.name),
    repeated_misses: c.last_30_days?.repeated_misses ?? [],
    possible_causes: c.last_30_days?.possible_causes ?? [],
    phone_screen_time: c.last_30_days?.phone_screen_time,
    sleep: c.last_30_days?.health?.sleep,
    goals: c.goals,
    dream: c.dream,
    coach_memory: c.coach_memory,
  }

  const prompt = buildReportReviewPrompt({
    summary: summarizeReport(report),
    context,
    period,
  })

  let out: Awaited<ReturnType<typeof aiDeep>>
  try {
    out = await aiDeep([
      { role: 'system', content: REPORT_REVIEW_SYSTEM },
      { role: 'user', content: prompt },
    ])
  } catch (err: unknown) {
    if (err instanceof AiError) {
      return NextResponse.json({ error: err.message }, { status: err.status ?? 502 })
    }
    return NextResponse.json({ error: 'The review could not be written.' }, { status: 500 })
  }

  const generated_at = new Date().toISOString()
  const { data: saved, error } = await supabase
    .from('report_reviews')
    .upsert({
      user_id: user.id,
      period,
      start_date: current.start,
      content_md: out.text,
      model_used: out.model,
      generated_at,
    }, { onConflict: 'user_id,period,start_date' })
    .select(SELECT)
    .single()

  if (error) {
    // A paid answer is not thrown away over a missing migration: return it
    // unsaved, and say plainly why it will not survive a refresh.
    return NextResponse.json({
      review: { id: 'unsaved', period, start_date: current.start, content_md: out.text, model_used: out.model, generated_at },
      provider: out.provider,
      fellBack: out.fellBack,
      saved: false,
      hint: 'This read was not kept — run supabase/report_reviews.sql once so it is saved and printed.',
    })
  }

  return NextResponse.json({
    review: saved,
    provider: out.provider,
    fellBack: out.fellBack,
    saved: true,
  })
}
