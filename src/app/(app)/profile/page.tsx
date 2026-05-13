import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import ProfileClient from './ProfileClient'
import {
  computeHabitsHistory, computeDeenHistory, computeChallengesHistory,
  computeHealthHistory, computeDailyTasksHistory,
  combineDayScores, type DayScore,
} from '@/lib/history'
import type { Habit, HabitLog } from '@/types'
import { todayString } from '@/lib/utils'
import { earnedBadgeIds, type BadgeContext } from '@/lib/badges'

export default async function ProfilePage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  const meta = (user.user_metadata ?? {}) as Record<string, any>
  const authName: string = (
    (meta.full_name as string) || (meta.name as string) || (meta.user_name as string) || ''
  ).trim()
  const emailPrefix: string = (user.email?.split('@')[0] ?? '').trim()
  // Case-insensitive placeholder check
  const PLACEHOLDERS_LOWER = new Set(['', 'friend', 'no name', 'user'])
  const isPlaceholder = (s: string | null | undefined) =>
    !s || PLACEHOLDERS_LOWER.has(s.trim().toLowerCase())

  // Fetch profile
  let { data: profile } = await supabase
    .from('users')
    .select('*, dreams(*, activity_weights(*))')
    .eq('id', user.id)
    .maybeSingle()

  if (!profile) {
    const insertName = authName || emailPrefix
    const { data: created } = await supabase
      .from('users')
      .insert({
        id: user.id,
        email: user.email ?? '',
        name: insertName,
        onboarding_complete: false,
      })
      .select('*, dreams(*, activity_weights(*))')
      .single()
    profile = created
  } else {
    // Compute the best available real name from any source
    const profileEmailPrefix = (profile.email as string | undefined)?.split('@')[0]?.trim() ?? ''
    const bestName = authName || emailPrefix || profileEmailPrefix
    const currentBad = isPlaceholder(profile.name)

    console.log('[profile] name resolution:', {
      dbName: profile.name,
      currentBad,
      authName,
      emailPrefix,
      profileEmailPrefix,
      bestName,
    })

    const patch: Record<string, any> = {}
    if (currentBad && bestName) patch.name = bestName
    if (!profile.email?.trim() && user.email) patch.email = user.email
    if (Object.keys(patch).length > 0) {
      const { data: updated, error: updErr } = await supabase
        .from('users').update(patch).eq('id', user.id)
        .select('*, dreams(*, activity_weights(*))').maybeSingle()
      if (updErr) console.warn('[profile] backfill failed:', updErr.message)
      if (updated) profile = updated
      else Object.assign(profile, patch)
    }
  }

  if (profile) {
    // Last-resort: never let an empty/placeholder reach the client
    const profileEmailPrefix = (profile.email as string | undefined)?.split('@')[0]?.trim() ?? ''
    if (isPlaceholder(profile.name)) {
      profile.name = authName || emailPrefix || profileEmailPrefix || 'Friend'
    }
    if (!profile.email?.trim()) profile.email = user.email ?? ''
    if (!profile.created_at)    profile.created_at = user.created_at
  }

  // ---- 30-day combined activity stats ----
  const today = todayString()
  const thirtyAgo = new Date(); thirtyAgo.setDate(thirtyAgo.getDate() - 29)
  const start = thirtyAgo.toISOString().split('T')[0]
  // Past 7 days for Fajr/water-streak checks
  const sevenAgo = new Date(); sevenAgo.setDate(sevenAgo.getDate() - 6)
  const start7 = sevenAgo.toISOString().split('T')[0]

  const results = await Promise.allSettled([
    supabase.from('habits').select('*').eq('user_id', user.id).eq('is_active', true),
    supabase.from('habit_logs').select('*').eq('user_id', user.id).gte('date', start),
    supabase.from('prayer_logs').select('*').eq('user_id', user.id).gte('date', start),
    supabase.from('prayer_logs').select('date, fajr').eq('user_id', user.id).gte('date', start7),
    supabase.from('prayer_logs').select('date, fajr, dhuhr, asr, maghrib, isha').eq('user_id', user.id),
    supabase.from('challenges').select('id, start_date, duration_days, status, frequency').eq('user_id', user.id),
    supabase.from('challenge_checkins').select('challenge_id, date, completed').gte('date', start),
    supabase.from('tasks').select('*').eq('user_id', user.id).eq('type', 'daily').gte('period_date', start),
    supabase.from('tasks').select('id, status').eq('user_id', user.id).eq('status', 'completed'),
    supabase.from('health_logs').select('*').eq('user_id', user.id).gte('date', start),
    supabase.from('health_logs').select('date, water_glasses').eq('user_id', user.id).gte('date', start7),
    supabase.from('health_logs').select('date, steps').eq('user_id', user.id),
    supabase.from('goals').select('id, status, goal_type').eq('user_id', user.id),
  ])
  const data = (i: number): any =>
    results[i].status === 'fulfilled' ? ((results[i] as any).value?.data ?? null) : null
  const habits              = data(0)
  const habitLogs30         = data(1)
  const prayerLogs30        = data(2)
  const prayerLogs7         = data(3)
  const prayerLogsAll       = data(4)
  const challenges          = data(5)
  const challengeCheckins30 = data(6)
  const tasks30             = data(7)
  const tasksAllCount       = data(8)
  const healthLogs30        = data(9)
  const healthLogs7         = data(10)
  const healthLogsAll       = data(11)
  const goals               = data(12)

  // Daily score arrays for the stats card
  const habitsHist     = computeHabitsHistory((habits ?? []) as Habit[], (habitLogs30 ?? []) as HabitLog[], today)
  const deenHist       = computeDeenHistory(prayerLogs30 ?? [], today)
  const challengesHist = computeChallengesHistory((challenges ?? []) as any, challengeCheckins30 ?? [], today)
  const healthHist     = computeHealthHistory(healthLogs30 ?? [], today)
  const tasksHist      = computeDailyTasksHistory((tasks30 ?? []) as any, today)
  const dailyScores: DayScore[] = combineDayScores([habitsHist, deenHist, challengesHist, healthHist, tasksHist])

  // ---- Badge context ----
  const daysSinceSignup = profile?.created_at
    ? Math.floor((Date.now() - new Date(profile.created_at).getTime()) / 86400000) : 0

  const maxHabitStreak = ((habits ?? []) as any[]).reduce((m, h) => Math.max(m, h.longest_streak ?? 0, h.current_streak ?? 0), 0)

  const fajrLast7Done = (prayerLogs7 ?? []).filter((p: any) => Number(p.fajr ?? 0) >= 1).length

  const hasJamatPrayer = (prayerLogsAll ?? []).some((p: any) =>
    [p.fajr, p.dhuhr, p.asr, p.maghrib, p.isha].some((v: any) => Number(v ?? 0) === 2))

  const perfectPrayerDays = (prayerLogsAll ?? []).filter((p: any) =>
    [p.fajr, p.dhuhr, p.asr, p.maghrib, p.isha].every((v: any) => Number(v ?? 0) >= 1)).length

  const challengesCompletedCount = ((challenges ?? []) as any[]).filter((c) => c.status === 'completed').length
  const challengesCreatedCount   = ((challenges ?? []) as any[]).length

  const tasksCompletedAllTime = (tasksAllCount ?? []).length
  const tasksByDate: Record<string, { total: number; done: number }> = {}
  for (const t of (tasks30 ?? []) as any[]) {
    const d = t.period_date
    tasksByDate[d] = tasksByDate[d] ?? { total: 0, done: 0 }
    tasksByDate[d].total++
    if (t.status === 'completed') tasksByDate[d].done++
  }
  const hadPerfectDailyTaskDay = Object.values(tasksByDate)
    .some((d) => d.total >= 3 && d.done === d.total)

  const goalsCount = ((goals ?? []) as any[]).length
  const goalsCompletedCount = ((goals ?? []) as any[]).filter((g) => g.status === 'completed').length
  const yearlyGoalCompletedCount = ((goals ?? []) as any[])
    .filter((g) => g.status === 'completed' && g.goal_type === 'yearly').length

  // 7 consecutive days where water_glasses >= 8
  const waterMap: Record<string, number> = {}
  for (const h of (healthLogs7 ?? []) as any[]) waterMap[h.date] = Number(h.water_glasses ?? 0)
  let waterStreak = true
  for (let i = 0; i < 7; i++) {
    const d = new Date(); d.setDate(d.getDate() - i)
    const ds = d.toISOString().split('T')[0]
    if ((waterMap[ds] ?? 0) < 8) { waterStreak = false; break }
  }

  const has10kStepsDay = ((healthLogsAll ?? []) as any[]).some((h) => Number(h.steps ?? 0) >= 10000)

  // 7-day streak with avg score >= 80
  const consistencyKing7 = (() => {
    const last7 = dailyScores.slice(-7)
    if (last7.length < 7) return false
    if (last7.some((d) => d.max === 0)) return false
    return last7.every((d) => d.pct >= 80)
  })()

  const ctx: BadgeContext = {
    daysSinceSignup,
    hasHeightWeight: !!(profile?.height_cm && profile?.weight_kg),
    habitsCount: ((habits ?? []) as any[]).length,
    maxHabitStreak,
    hasAnyPrayerLog: ((prayerLogsAll ?? []) as any[]).length > 0,
    fajrLast7Done,
    hasJamatPrayer,
    perfectPrayerDays,
    challengesCreatedCount,
    challengesCompletedCount,
    tasksCompletedAllTime,
    hadPerfectDailyTaskDay,
    goalsCount,
    goalsCompletedCount,
    yearlyGoalCompletedCount,
    waterTargetStreak7: waterStreak,
    has10kStepsDay,
    consistencyKing7,
  }

  // ---- Diff against stored badges; persist newly-earned ones ----
  const earnedNow = earnedBadgeIds(ctx)
  const stored = (profile?.badges ?? {}) as Record<string, string>
  const newBadges: Record<string, string> = { ...stored }
  const nowIso = new Date().toISOString()
  let changed = false
  for (const id of earnedNow) {
    if (!newBadges[id]) { newBadges[id] = nowIso; changed = true }
  }
  if (changed) {
    await supabase.from('users').update({ badges: newBadges }).eq('id', user.id)
    if (profile) profile.badges = newBadges
  }

  return <ProfileClient profile={profile} dailyScores={dailyScores} earnedBadges={newBadges} />
}
