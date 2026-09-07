import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiDeep, AiError } from '@/lib/ai'
import { buildCoachContext } from '@/lib/coach-context'
import { buildReport, periodRange } from '@/lib/report'
import { fetchReportRows, toReportInput } from '@/lib/report-data'
import { summarizeReport, buildGrowthPrompt } from '@/lib/growth'
import { GROWTH_REVIEW_SYSTEM } from '@/lib/ai-prompts'
import { todayInTZ, todayString } from '@/lib/utils'

export const dynamic = 'force-dynamic'
// A month of records, read by a model that thinks before it writes.
export const maxDuration = 60

const SELECT = 'id, content_md, generated_at, model_used'

/**
 * Write the growth review: improving, lacking, the pattern underneath,
 * three rules for the week, one question.
 *
 * On demand and once a day. It is the one call in the app that goes to
 * Claude when ANTHROPIC_API_KEY is set — paid per call — so the user starts
 * it, and asking again the same day returns the day's review rather than
 * spending twice (?force=1 overrides). Everything the model is given is
 * either a record or computed from records by tested code; it is not asked
 * to estimate anything.
 */
export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const force = req.nextUrl.searchParams.get('force') === '1'

  const { data: profile } = await supabase
    .from('users').select('timezone').eq('id', user.id).maybeSingle()
  const tz = profile?.timezone || 'UTC'

  // The latest review, and whether it was written today on the user's calendar.
  const { data: latest } = await supabase
    .from('ai_reports').select(SELECT)
    .eq('user_id', user.id).eq('type', 'growth')
    .order('generated_at', { ascending: false }).limit(1).maybeSingle()

  if (latest && !force) {
    const writtenOn = new Date(latest.generated_at).toLocaleDateString('en-CA', { timeZone: tz })
    if (writtenOn === todayInTZ(tz)) {
      return NextResponse.json({ review: latest, cached: true })
    }
  }

  // Rows covering this week, last week, this month and last month — the same
  // fetch the Reports page makes, so the comparisons are the ones it shows.
  const today = todayString()
  const thisWeek = periodRange('weekly', 0, today)
  const lastWeek = periodRange('weekly', 1, today)
  const lastMonth = periodRange('monthly', 1, today)
  const from = lastMonth.start < lastWeek.start ? lastMonth.start : lastWeek.start
  const to = today

  // One fetch over the widest range, two reports built from it.
  const [ctx, rows] = await Promise.all([
    buildCoachContext(supabase, user.id),
    fetchReportRows(supabase, user.id, from, to),
  ])

  const week = summarizeReport(buildReport(toReportInput(rows, { period: 'weekly', offset: 0, today })))
  const month = summarizeReport(buildReport(toReportInput(rows, { period: 'monthly', offset: 0, today })))

  const prompt = buildGrowthPrompt({
    context: ctx.context,
    week,
    month,
    previousReview: latest ?? null,
  })

  let out: Awaited<ReturnType<typeof aiDeep>>
  try {
    out = await aiDeep([
      { role: 'system', content: GROWTH_REVIEW_SYSTEM },
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
    .from('ai_reports')
    .insert({
      user_id: user.id,
      type: 'growth',
      content_md: out.text,
      generated_at,
      model_used: out.model,
      week_start: thisWeek.start,
    })
    .select(SELECT)
    .single()

  if (error) {
    // A paid answer is not thrown away over a missing migration: return it
    // unsaved and say why it will not be kept.
    return NextResponse.json({
      review: { id: 'unsaved', content_md: out.text, generated_at, model_used: out.model },
      cached: false,
      provider: out.provider,
      fellBack: out.fellBack,
      saved: false,
      hint: 'This review was not kept — run supabase/ai_reports_growth.sql once so reviews are saved.',
    })
  }

  return NextResponse.json({
    review: saved,
    cached: false,
    provider: out.provider,
    fellBack: out.fellBack,
    saved: true,
  })
}
