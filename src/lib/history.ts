// Shared helpers for 30-day history charts across all feature pages.

import type { Habit, HabitLog, Weekday } from '@/types'
import { isoMonday, firstOfMonth, addWeeks, addMonths, type Task } from './tasks'

export interface DayScore {
  date: string
  pct: number
  earned: number
  max: number
}

const WEEKDAY_CODES: Weekday[] = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat']

export function last30Days(today: string): string[] {
  const out: string[] = []
  const t = new Date(today + 'T12:00:00')
  for (let i = 29; i >= 0; i--) {
    const d = new Date(t)
    d.setDate(t.getDate() - i)
    out.push(d.toISOString().split('T')[0])
  }
  return out
}

export function weekdayOf(dateStr: string): Weekday {
  return WEEKDAY_CODES[new Date(dateStr + 'T12:00:00').getDay()]
}

export function formatPrettyDate(dateStr: string): string {
  return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', {
    weekday: 'short', month: 'short', day: 'numeric',
  })
}

// ---------- Habits ----------
export function isHabitScheduledOn(habit: Habit, dateStr: string): boolean {
  if (habit.is_paused) return false
  if (habit.schedule_kind === 'weekdays') {
    return (habit.schedule_days ?? []).includes(weekdayOf(dateStr))
  }
  return true
}

export function isHabitLogComplete(habit: Habit, log?: HabitLog): boolean {
  if (!log?.completed) return false
  if (habit.type === 'counter') return log.value >= habit.target_value
  if (habit.type === 'duration') return log.duration_mins >= habit.time_target_mins
  return true
}

export function computeHabitsHistory(
  habits: Habit[],
  logs: HabitLog[],
  today: string
): DayScore[] {
  const active = habits.filter((h) => h.is_active && !h.is_paused)
  return last30Days(today).map((date) => {
    const scheduled = active.filter((h) => isHabitScheduledOn(h, date))
    const earned = scheduled.filter((h) => {
      const log = logs.find((l) => l.habit_id === h.id && l.date === date)
      return isHabitLogComplete(h, log)
    }).length
    const max = scheduled.length
    return { date, pct: max > 0 ? Math.round((earned / max) * 100) : 0, earned, max }
  })
}

// ---------- Prayers / Deen ----------
type PrayerLog = {
  date: string
  fajr: number; dhuhr: number; asr: number; maghrib: number; isha: number
  extra_prayers?: { name: string; type: 'count' | 'boolean'; value?: number; target?: number; score_weight?: number; done?: boolean }[]
}

export function computeDeenHistory(prayerLogs: PrayerLog[], today: string): DayScore[] {
  const byDate = new Map<string, PrayerLog>()
  for (const p of prayerLogs) byDate.set(p.date, p)

  return last30Days(today).map((date) => {
    const log = byDate.get(date)
    if (!log) return { date, pct: 0, earned: 0, max: 10 }

    const prayerEarned =
      (log.fajr ?? 0) + (log.dhuhr ?? 0) + (log.asr ?? 0) + (log.maghrib ?? 0) + (log.isha ?? 0)
    const prayerMax = 10
    const extras = log.extra_prayers ?? []
    const itemEarned = extras.reduce((s, x) => {
      if (x.type === 'count') return s + ((x.value ?? 0) >= (x.target ?? 1) ? (x.score_weight ?? 1) : 0)
      if (x.type === 'boolean') return s + (x.done ? (x.score_weight ?? 1) : 0)
      return s
    }, 0)
    const itemMax = extras.reduce((s, x) => s + (x.score_weight ?? 1), 0)
    const earned = prayerEarned + itemEarned
    const max = prayerMax + itemMax
    return { date, pct: max > 0 ? Math.round((earned / max) * 100) : 0, earned, max }
  })
}

// ---------- Challenges ----------
type ChallengeCheckin = { challenge_id: string; date: string; completed: boolean }
type ChallengeLite = { id: string; start_date: string; duration_days: number; status: string }

export function computeChallengesHistory(
  challenges: ChallengeLite[],
  checkins: ChallengeCheckin[],
  today: string
): DayScore[] {
  const checkinsByDate = new Map<string, Set<string>>()  // date -> challenge_ids done
  for (const c of checkins) {
    if (!c.completed) continue
    if (!checkinsByDate.has(c.date)) checkinsByDate.set(c.date, new Set())
    checkinsByDate.get(c.date)!.add(c.challenge_id)
  }
  return last30Days(today).map((date) => {
    const dateMs = new Date(date + 'T12:00:00').getTime()
    const active = challenges.filter((c) => {
      const start = new Date(c.start_date + 'T12:00:00').getTime()
      const end = start + c.duration_days * 86400000
      return dateMs >= start && dateMs < end
    })
    const doneIds = checkinsByDate.get(date) ?? new Set()
    const earned = active.filter((c) => doneIds.has(c.id)).length
    const max = active.length
    return { date, pct: max > 0 ? Math.round((earned / max) * 100) : 0, earned, max }
  })
}

// ---------- Health ----------
type HealthLog = {
  date: string
  water_glasses?: number
  steps?: number | null
  sleep_hours?: number | null
  exercise_done?: boolean
}

export function computeHealthHistory(healthLogs: HealthLog[], today: string): DayScore[] {
  const byDate = new Map<string, HealthLog>()
  for (const h of healthLogs) byDate.set(h.date, h)
  return last30Days(today).map((date) => {
    const log = byDate.get(date)
    if (!log) return { date, pct: 0, earned: 0, max: 4 }
    const flags = [
      (log.water_glasses ?? 0) > 0,
      (log.steps ?? 0) > 0,
      (log.sleep_hours ?? 0) > 0,
      !!log.exercise_done,
    ]
    const earned = flags.filter(Boolean).length
    return { date, pct: Math.round((earned / 4) * 100), earned, max: 4 }
  })
}

// ---------- Tasks (Daily / Weekly / Monthly) ----------
// All take rows from the `tasks` table, filtered to one type by the caller.

export function computeDailyTasksHistory(tasks: Task[], today: string): DayScore[] {
  return last30Days(today).map((date) => {
    const onDay = tasks.filter((t) => t.period_date === date)
    const earned = onDay.filter((t) => t.status === 'completed').length
    const max = onDay.length
    return { date, pct: max > 0 ? Math.round((earned / max) * 100) : 0, earned, max }
  })
}

/** Last `n` ISO-Monday anchors going back from today (oldest first). */
export function lastNWeeks(today: string, n: number): string[] {
  const out: string[] = []
  const thisMon = isoMonday(today)
  for (let i = n - 1; i >= 0; i--) out.push(addWeeks(thisMon, -i))
  return out
}

export function computeWeeklyTasksHistory(tasks: Task[], today: string, n = 12): DayScore[] {
  return lastNWeeks(today, n).map((wk) => {
    const inWeek = tasks.filter((t) => t.period_date === wk)
    const earned = inWeek.filter((t) => t.status === 'completed').length
    const max = inWeek.length
    return { date: wk, pct: max > 0 ? Math.round((earned / max) * 100) : 0, earned, max }
  })
}

/** Last `n` first-of-month anchors going back from today (oldest first). */
export function lastNMonths(today: string, n: number): string[] {
  const out: string[] = []
  const thisFirst = firstOfMonth(today)
  for (let i = n - 1; i >= 0; i--) out.push(addMonths(thisFirst, -i))
  return out
}

export function computeMonthlyTasksHistory(tasks: Task[], today: string, n = 6): DayScore[] {
  return lastNMonths(today, n).map((mo) => {
    const inMonth = tasks.filter((t) => t.period_date === mo)
    const earned = inMonth.filter((t) => t.status === 'completed').length
    const max = inMonth.length
    return { date: mo, pct: max > 0 ? Math.round((earned / max) * 100) : 0, earned, max }
  })
}

// ---------- Combined total ----------
// Averages provided per-feature day scores. Skips features with max=0 for that day.
export function combineDayScores(perFeature: DayScore[][]): DayScore[] {
  if (perFeature.length === 0) return []
  const numDays = perFeature[0].length
  const out: DayScore[] = []
  for (let i = 0; i < numDays; i++) {
    const slice = perFeature.map((arr) => arr[i]).filter((d) => d.max > 0)
    if (slice.length === 0) {
      out.push({ date: perFeature[0][i].date, pct: 0, earned: 0, max: 0 })
    } else {
      const avg = slice.reduce((s, d) => s + d.pct, 0) / slice.length
      out.push({
        date: perFeature[0][i].date,
        pct: Math.round(avg),
        earned: slice.length,
        max: perFeature.length,
      })
    }
  }
  return out
}

export function deltaVsPrev(days: DayScore[], date: string): { delta: number; prevPct: number } | null {
  const i = days.findIndex((d) => d.date === date)
  if (i <= 0) return null
  // walk back to the most recent day with max > 0
  for (let j = i - 1; j >= 0; j--) {
    if (days[j].max > 0) {
      return { delta: days[i].pct - days[j].pct, prevPct: days[j].pct }
    }
  }
  return null
}
