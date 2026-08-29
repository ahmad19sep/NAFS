import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import { todayString } from '@/lib/utils'
import { buildReport, periodRange, type ReportPeriod } from '@/lib/report'
import type { CustomMetric } from '@/lib/health'
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

  const results = await Promise.allSettled([
    supabase.from('users').select('name, email, deen_enabled, health_extras_config').eq('id', user.id).single(),
    supabase.from('habits').select('*').eq('user_id', user.id).eq('is_active', true),
    supabase.from('habit_logs').select('*').eq('user_id', user.id).gte('date', from).lte('date', to),
    supabase.from('prayer_logs').select('*').eq('user_id', user.id).gte('date', from).lte('date', to),
    supabase.from('tasks').select('*').eq('user_id', user.id).gte('period_date', from).lte('period_date', to),
    supabase.from('health_logs').select('*').eq('user_id', user.id).gte('date', from).lte('date', to),
    supabase.from('quran_log').select('date, pages_read, surah').eq('user_id', user.id).gte('date', from).lte('date', to),
    supabase.from('daily_checkins').select('date, evening_text, ai_verdict').eq('user_id', user.id).gte('date', from).lte('date', to),
    supabase.from('challenges').select('*').eq('user_id', user.id),
    supabase.from('challenge_checkins').select('challenge_id, date, completed').gte('date', from).lte('date', to),
    supabase.from('goals').select('*, goal_milestones(done)').eq('user_id', user.id),
  ])

  // Any table that's missing or errors just contributes nothing to the report.
  const data = (i: number): any =>
    results[i].status === 'fulfilled' ? ((results[i] as any).value?.data ?? null) : null

  const profile = data(0)
  const extrasConfig = profile?.health_extras_config
  const healthMetrics: CustomMetric[] = Array.isArray(extrasConfig) ? extrasConfig : []

  const report = buildReport({
    period,
    offset,
    today,
    deenEnabled: (profile?.deen_enabled ?? true) as boolean,
    user: { name: profile?.name || 'Friend', email: profile?.email || '' },
    habits: data(1) ?? [],
    habitLogs: data(2) ?? [],
    prayerLogs: data(3) ?? [],
    tasks: data(4) ?? [],
    healthLogs: data(5) ?? [],
    quranLogs: data(6) ?? [],
    checkins: data(7) ?? [],
    challenges: data(8) ?? [],
    challengeCheckins: data(9) ?? [],
    goals: data(10) ?? [],
    healthMetrics,
  })

  return <ReportsClient report={report} />
}
