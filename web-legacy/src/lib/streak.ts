/**
 * Live streak calculation.
 *
 * `habits.current_streak` is a stored column, and it is only recalculated
 * inside /api/habits/log — that is, when you log something. Miss a day and
 * nothing runs, so the stored number keeps showing the old streak until the
 * next time you log, at which point it silently corrects itself. A reading
 * streak of 5 stays on screen through three unread days.
 *
 * Computing it from the logs at read time means a broken streak shows the
 * moment it breaks.
 */

const WEEKDAY_CODES = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat']

export interface StreakHabit {
  type?: string
  target_value?: number
  time_target_mins?: number
  is_paused?: boolean
  schedule_kind?: string
  schedule_days?: string[] | null
}

export interface StreakLog {
  date: string
  completed?: boolean
  value?: number
  duration_mins?: number
}

function weekdayOf(dateStr: string): string {
  return WEEKDAY_CODES[new Date(`${dateStr}T12:00:00`).getDay()]
}

/** Pure calendar step, no timezone involved. */
function shift(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split('-').map(Number)
  const dt = new Date(Date.UTC(y, m - 1, d))
  dt.setUTCDate(dt.getUTCDate() + days)
  return dt.toISOString().slice(0, 10)
}

export function isScheduledOn(habit: StreakHabit, dateStr: string): boolean {
  if (habit.is_paused) return false
  if (habit.schedule_kind === 'weekdays') {
    return (habit.schedule_days ?? []).includes(weekdayOf(dateStr))
  }
  return true
}

/** Did this log actually meet the habit's target? */
export function isComplete(habit: StreakHabit, log?: StreakLog): boolean {
  if (!log?.completed) return false
  if (habit.type === 'counter')  return (log.value ?? 0) >= (habit.target_value ?? 1)
  if (habit.type === 'duration') return (log.duration_mins ?? 0) >= (habit.time_target_mins ?? 1)
  return true
}

/**
 * Consecutive scheduled days completed, counting back from `today`.
 *
 * Days the habit isn't scheduled for are skipped rather than breaking the run —
 * a rest day is not a failure.
 *
 * Today is treated as still in progress: not having done it *yet* leaves
 * yesterday's streak standing rather than wiping it at midnight. Any earlier
 * scheduled day that went unlogged breaks the run, which is the behaviour the
 * stored column was failing to show.
 */
export function currentStreak(
  habit: StreakHabit,
  logs: StreakLog[],
  today: string,
  maxLookback = 400,
): number {
  const byDate = new Map<string, StreakLog>()
  for (const l of logs) byDate.set(l.date, l)

  let streak = 0
  for (let i = 0; i < maxLookback; i++) {
    const day = shift(today, -i)
    if (!isScheduledOn(habit, day)) continue

    if (isComplete(habit, byDate.get(day))) {
      streak++
      continue
    }

    // Today is not over yet, so an empty today is pending, not missed.
    if (i === 0) continue
    break
  }
  return streak
}
