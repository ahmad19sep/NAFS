import { NextRequest, NextResponse } from 'next/server'
import { authenticateRequest } from '@/lib/supabase/request'
import { ApiRouteError } from '@/lib/api-errors'
import { BADGES, earnedBadgeIds, type BadgeContext } from '@/lib/badges'
import { countDaysLogged, levelFor } from '@/lib/levels'
import { currentStreak } from '@/lib/streak'
import { todayInTZ, shiftDate } from '@/lib/utils'
import { fetchReportRows } from '@/lib/report-data'
import { nativeHistory } from '@/lib/native-history'
import { readAllOwnedRows } from '@/lib/owned-history'

export const dynamic = 'force-dynamic'

export async function POST(req: NextRequest) {
  try {
    const { supabase, user } = await authenticateRequest(req)
    const { data: profile, error } = await supabase.from('users').select('timezone,deen_enabled,created_at,height_cm,weight_kg,badges').eq('id', user.id).single()
    if (error || !profile) throw new ApiRouteError(503, 'unavailable', 'Profile could not be loaded.')
    const today = todayInTZ(profile.timezone || 'UTC')
    // Stable paging avoids silently treating a Supabase row-limit as lifetime activity.
    const all = (table: string, columns: string) => readAllOwnedRows(supabase, user.id, table, columns)
    const [habits, logs, prayers, health, tasks, goals, challenges, recent] = await Promise.all([
      all('habits', '*'), all('habit_logs', 'id,habit_id,date,completed,value,duration_mins'),
      all('prayer_logs', 'id,date,fajr,dhuhr,asr,maghrib,isha'), all('health_logs', 'id,date,water_glasses,steps'),
      all('tasks', 'id,type,period_date,status,deleted_at'), all('goals', 'id,goal_type,progress_pct,goal_milestones(done)'),
      all('challenges', 'id,status'), fetchReportRows(supabase, user.id, shiftDate(today, -29), today),
    ])
    const active = habits.filter(h => h.is_active)
    const history = nativeHistory(recent, today, profile.deen_enabled === true)
    const daily = tasks.filter(t => !t.deleted_at && t.type === 'daily')
    const taskDates = new Set(daily.map(t => t.period_date))
    const goalDone = (goal: any) => goal.goal_milestones?.length > 0 ? goal.goal_milestones.every((m: any) => m.done) : Number(goal.progress_pct) >= 100
    const context: BadgeContext = {
      daysSinceSignup: Math.max(0, Math.floor((Date.now() - Date.parse(profile.created_at)) / 86400000)),
      hasHeightWeight: !!(profile.height_cm && profile.weight_kg), habitsCount: active.length,
      maxHabitStreak: Math.max(0, ...active.map(h => Math.max(h.longest_streak ?? 0, currentStreak(h, logs.filter(l => l.habit_id === h.id), today)))),
      hasAnyPrayerLog: prayers.length > 0,
      fajrLast7Done: prayers.filter(p => p.date >= shiftDate(today, -6) && p.date <= today && p.fajr >= 1).length,
      hasJamatPrayer: prayers.some(p => [p.fajr,p.dhuhr,p.asr,p.maghrib,p.isha].some(n => n === 2)),
      perfectPrayerDays: prayers.filter(p => [p.fajr,p.dhuhr,p.asr,p.maghrib,p.isha].every(n => n >= 1)).length,
      challengesCreatedCount: challenges.length, challengesCompletedCount: challenges.filter(c => c.status === 'completed').length,
      tasksCompletedAllTime: tasks.filter(t => !t.deleted_at && t.status === 'completed').length,
      hadPerfectDailyTaskDay: Array.from(taskDates).some(date => { const group = daily.filter(t => t.period_date === date); return group.length >= 3 && group.every(t => t.status === 'completed') }),
      goalsCount: goals.length, goalsCompletedCount: goals.filter(goalDone).length,
      yearlyGoalCompletedCount: goals.filter(g => g.goal_type === 'yearly' && goalDone(g)).length,
      waterTargetStreak7: Array.from({ length: 7 }, (_, i) => shiftDate(today, -i)).every(date => health.some(h => h.date === date && h.water_glasses >= 8)),
      has10kStepsDay: health.some(h => h.steps >= 10000),
      consistencyKing7: !recent.unavailable?.length && history.overall.slice(-7).every(d => d.coverage === 'recorded' && d.pct >= 80),
    }
    const earned = new Set([...Object.keys(profile.badges ?? {}), ...earnedBadgeIds(context)])
    // Read-only projection. Stored awards are preserved; no profile mutation on a page view.
    return NextResponse.json({ level: levelFor(countDaysLogged(logs, prayers, health)),
      badges: BADGES.filter(b => profile.deen_enabled || b.feature !== 'deen').map(b => ({ ...b, earned: earned.has(b.id), awardedAt: profile.badges?.[b.id] ?? null })),
      unavailable: recent.unavailable ?? [] })
  } catch (error) {
    if (error instanceof ApiRouteError) return NextResponse.json({ error: error.message, code: error.code }, { status: error.status })
    return NextResponse.json({ error: 'Achievements could not be loaded.', code: 'unavailable' }, { status: 503 })
  }
}
