import { NextRequest, NextResponse } from 'next/server'
import { authenticateRequest } from '@/lib/supabase/request'
import { ApiRouteError } from '@/lib/api-errors'
import { fetchReportRows, toReportInput } from '@/lib/report-data'
import { buildReport, periodRange, type ReportPeriod } from '@/lib/report'
import { todayInTZ, shiftDate } from '@/lib/utils'
import { nativeHistory } from '@/lib/native-history'

export const dynamic = 'force-dynamic'

/** Read-only bearer/cookie projection of the same tested domain used by Reports. */
export async function POST(req: NextRequest) {
  try {
    const { supabase, user } = await authenticateRequest(req)
    const body = await req.json().catch(() => null)
    if (!body || !['weekly', 'monthly'].includes(body.period) || !Number.isInteger(body.offset) || body.offset < 0 || body.offset > 60) {
      throw new ApiRouteError(400, 'invalid_request', 'Choose a valid report period.')
    }
    const { data: profile, error } = await supabase.from('users').select('timezone,deen_enabled').eq('id', user.id).single()
    if (error || !profile) throw new ApiRouteError(503, 'unavailable', 'Your profile could not be loaded.')
    const today = todayInTZ(profile.timezone || 'UTC')
    const period: ReportPeriod = body.period
    const current = periodRange(period, body.offset, today)
    const previous = periodRange(period, body.offset + 1, today)
    const from = previous.start < shiftDate(today, -29) ? previous.start : shiftDate(today, -29)
    const to = current.end > today ? current.end : today
    const rows = await fetchReportRows(supabase, user.id, from, to)
    // Read model owns all feature scoring. Missing tables remain explicitly partial.
    const report = buildReport(toReportInput({ ...rows, profile: { ...rows.profile, deen_enabled: profile.deen_enabled }, tasks: rows.tasks.filter(t => !t.deleted_at) }, { period, offset: body.offset, today }))
    const history = nativeHistory(rows, today, profile.deen_enabled === true)
    const review = await supabase.from('report_reviews').select('content_md,model_used,generated_at').eq('user_id', user.id)
      .eq('period', period).eq('start_date', current.start).maybeSingle()
    return NextResponse.json({ report, history, today, timezone: profile.timezone,
      unavailable: [...(rows.unavailable ?? []), ...(review.error ? ['review'] : [])], review: review.data ?? null })
  } catch (error) {
    if (error instanceof ApiRouteError) return NextResponse.json({ error: error.message, code: error.code }, { status: error.status })
    return NextResponse.json({ error: 'Progress could not be loaded.', code: 'unavailable' }, { status: 503 })
  }
}
