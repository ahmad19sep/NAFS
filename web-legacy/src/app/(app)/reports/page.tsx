import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import { todayString } from '@/lib/utils'
import { buildReport, periodRange, type ReportPeriod } from '@/lib/report'
import { fetchReportRows, toReportInput } from '@/lib/report-data'
import { deepProvider } from '@/lib/ai'
import ReportsClient from './ReportsClient'

export const dynamic = 'force-dynamic'

interface Props {
  searchParams: { period?: string; offset?: string }
}

export default async function ReportsPage({ searchParams }: Props) {
  const supabase = createClient()
  const user = await requireUser(supabase)

  const period: ReportPeriod = searchParams.period === 'monthly' ? 'monthly' : 'weekly'
  const offset = Math.min(60, Math.max(0, parseInt(searchParams.offset ?? '0', 10) || 0))

  const today = todayString()
  const current = periodRange(period, offset, today)
  const previous = periodRange(period, offset + 1, today)
  const from = previous.start          // covers the comparison period too
  const to = current.end

  // The rows, and the coach's written read of this period if one exists. The
  // read is stored, so a report printed weeks later carries the same words.
  const [rows, reviewResult] = await Promise.all([
    fetchReportRows(supabase, user.id, from, to),
    supabase
      .from('report_reviews')
      .select('id, period, start_date, content_md, model_used, generated_at')
      .eq('user_id', user.id).eq('period', period).eq('start_date', current.start)
      .maybeSingle(),
  ])

  const report = buildReport(toReportInput(rows, { period, offset, today }))

  return (
    <ReportsClient
      report={report}
      initialReview={reviewResult.data ?? null}
      deepModel={deepProvider()}
    />
  )
}
