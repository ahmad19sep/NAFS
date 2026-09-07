/**
 * The rows a report is built from, fetched once.
 *
 * The Reports page, the growth review and the report review all need the same
 * eleven queries. They were written out three times, which is three places for
 * a filter to drift — and under a service client a missing `user_id` is a data
 * leak, not a bug you notice. One definition instead.
 *
 * Fetching is separate from shaping so a caller that needs two periods (the
 * growth review compares a week and a month) queries once and builds twice.
 */

import type { createClient } from '@/lib/supabase/server'
import type { CustomMetric } from '@/lib/health'
import type { ReportInput, ReportPeriod } from '@/lib/report'

type Db = ReturnType<typeof createClient>

export interface ReportRows {
  profile: any
  habits: any[]
  habitLogs: any[]
  prayerLogs: any[]
  tasks: any[]
  healthLogs: any[]
  quranLogs: any[]
  checkins: any[]
  challenges: any[]
  challengeCheckins: any[]
  goals: any[]
}

/**
 * Every table a report reads, over one date range.
 *
 * allSettled throughout: a table that is missing or errors contributes nothing
 * rather than taking the whole report down with it.
 */
export async function fetchReportRows(
  supabase: Db,
  userId: string,
  from: string,
  to: string,
): Promise<ReportRows> {
  const results = await Promise.allSettled([
    supabase.from('users').select('name, email, deen_enabled, health_extras_config').eq('id', userId).maybeSingle(),
    supabase.from('habits').select('*').eq('user_id', userId).eq('is_active', true),
    supabase.from('habit_logs').select('*').eq('user_id', userId).gte('date', from).lte('date', to),
    supabase.from('prayer_logs').select('*').eq('user_id', userId).gte('date', from).lte('date', to),
    supabase.from('tasks').select('*').eq('user_id', userId).gte('period_date', from).lte('period_date', to),
    supabase.from('health_logs').select('*').eq('user_id', userId).gte('date', from).lte('date', to),
    supabase.from('quran_log').select('date, pages_read, surah').eq('user_id', userId).gte('date', from).lte('date', to),
    supabase.from('daily_checkins').select('date, evening_text, ai_verdict').eq('user_id', userId).gte('date', from).lte('date', to),
    supabase.from('challenges').select('*').eq('user_id', userId),
    // challenge_checkins has no user_id column; the rows are reached through
    // challenges, which is filtered above, and RLS scopes them to the owner.
    supabase.from('challenge_checkins').select('challenge_id, date, completed').gte('date', from).lte('date', to),
    supabase.from('goals').select('*, goal_milestones(done)').eq('user_id', userId),
  ])
  const data = (i: number): any =>
    results[i].status === 'fulfilled' ? ((results[i] as any).value?.data ?? null) : null

  return {
    profile: data(0),
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
  }
}

/** Shape fetched rows into the input `buildReport` takes for one period. */
export function toReportInput(
  rows: ReportRows,
  opts: { period: ReportPeriod; offset: number; today: string },
): ReportInput {
  const extrasConfig = rows.profile?.health_extras_config
  const healthMetrics: CustomMetric[] = Array.isArray(extrasConfig) ? extrasConfig : []

  return {
    period: opts.period,
    offset: opts.offset,
    today: opts.today,
    deenEnabled: (rows.profile?.deen_enabled ?? true) as boolean,
    user: { name: rows.profile?.name || 'Friend', email: rows.profile?.email || '' },
    habits: rows.habits,
    habitLogs: rows.habitLogs,
    prayerLogs: rows.prayerLogs,
    tasks: rows.tasks as any,
    healthLogs: rows.healthLogs,
    quranLogs: rows.quranLogs,
    checkins: rows.checkins,
    challenges: rows.challenges,
    challengeCheckins: rows.challengeCheckins,
    goals: rows.goals,
    healthMetrics,
  }
}
