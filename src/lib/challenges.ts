// Helpers for challenge frequency cadences.

export type ChallengeFrequency = 'daily' | 'weekly' | 'monthly' | 'yearly'

export interface FrequencyMeta {
  label: string
  unitLabel: string          // 'day' | 'week' | 'month' | 'year'
  unitDays: number           // approx days in one unit
  emoji: string
}

export const FREQUENCY: Record<ChallengeFrequency, FrequencyMeta> = {
  daily:   { label: 'Daily',   unitLabel: 'day',   unitDays: 1,   emoji: '☀️' },
  weekly:  { label: 'Weekly',  unitLabel: 'week',  unitDays: 7,   emoji: '📅' },
  monthly: { label: 'Monthly', unitLabel: 'month', unitDays: 30,  emoji: '🗓️' },
  yearly:  { label: 'Yearly',  unitLabel: 'year',  unitDays: 365, emoji: '🎉' },
}

function parseDate(d: string) { return new Date(d + 'T12:00:00') }
function daysBetween(a: string, b: string) {
  const ms = parseDate(b).getTime() - parseDate(a).getTime()
  return Math.floor(ms / 86400000)
}
function isoMonday(dateStr: string) {
  const d = parseDate(dateStr); const day = (d.getDay() + 6) % 7
  d.setDate(d.getDate() - day); return d.toISOString().split('T')[0]
}
function firstOfMonth(dateStr: string) {
  const d = parseDate(dateStr); d.setDate(1); return d.toISOString().split('T')[0]
}

export function totalUnits(frequency: ChallengeFrequency, durationDays: number): number {
  return Math.max(1, Math.ceil(durationDays / FREQUENCY[frequency].unitDays))
}

export function unitsElapsed(frequency: ChallengeFrequency, startDate: string, today: string): number {
  const days = Math.max(0, daysBetween(startDate, today))
  return Math.max(1, Math.ceil((days + 1) / FREQUENCY[frequency].unitDays))
}

/** Has the user checked in for the current period (for this frequency)? */
export function hasCheckedThisPeriod(
  frequency: ChallengeFrequency,
  checkins: { date: string; completed: boolean }[],
  today: string
): boolean {
  const completed = checkins.filter((c) => c.completed)
  if (frequency === 'daily') {
    return completed.some((c) => c.date === today)
  }
  if (frequency === 'weekly') {
    const monday = isoMonday(today)
    return completed.some((c) => c.date >= monday && c.date <= today)
  }
  if (frequency === 'monthly') {
    const first = firstOfMonth(today)
    return completed.some((c) => c.date >= first && c.date <= today)
  }
  // yearly
  const yearStart = today.slice(0, 4) + '-01-01'
  return completed.some((c) => c.date >= yearStart && c.date <= today)
}

/**
 * Should this challenge auto-restart because the user missed a period?
 * Returns true if the most recent successful check-in is older than one period
 * (and the challenge is older than one period).
 */
export function shouldAutoRestart(
  frequency: ChallengeFrequency,
  startDate: string,
  checkins: { date: string; completed: boolean }[],
  today: string
): boolean {
  const periodLen = FREQUENCY[frequency].unitDays
  const completed = checkins.filter((c) => c.completed).map((c) => c.date).sort()
  const lastCheckin = completed[completed.length - 1]
  // First period is always grace (no auto-fail before user has had a chance)
  if (daysBetween(startDate, today) < periodLen) return false

  if (!lastCheckin) {
    // Never checked in, but we're past the first period
    return daysBetween(startDate, today) >= periodLen
  }
  // Daily: skip a single day (last check-in was 2+ days ago)
  // Weekly: skip a week (last check-in was 8+ days ago)
  // Monthly: skip a month (last check-in was 31+ days ago)
  // Yearly: skip a year (last check-in was 366+ days ago)
  return daysBetween(lastCheckin, today) > periodLen
}

/** Streak in unit periods (days/weeks/months/years). */
export function periodStreak(
  frequency: ChallengeFrequency,
  startDate: string,
  checkins: { date: string; completed: boolean }[],
  today: string
): number {
  if (frequency === 'daily') {
    // Walk back day-by-day from today
    const done = new Set(checkins.filter((c) => c.completed).map((c) => c.date))
    let streak = 0
    let cursor = today
    while (cursor >= startDate) {
      if (done.has(cursor)) {
        streak++
        const d = parseDate(cursor); d.setDate(d.getDate() - 1)
        cursor = d.toISOString().split('T')[0]
      } else break
    }
    return streak
  }
  // For weekly/monthly/yearly, count consecutive periods with at least one check-in
  // walking back from current period
  const completed = checkins.filter((c) => c.completed)
  let streak = 0
  let probe = today
  while (probe >= startDate) {
    let periodStart: string
    let periodEnd = probe
    if (frequency === 'weekly')      periodStart = isoMonday(probe)
    else if (frequency === 'monthly') periodStart = firstOfMonth(probe)
    else /* yearly */                 periodStart = probe.slice(0, 4) + '-01-01'

    const hit = completed.some((c) => c.date >= periodStart && c.date <= periodEnd)
    if (!hit) break
    streak++
    // step to previous period
    const d = parseDate(periodStart); d.setDate(d.getDate() - 1)
    probe = d.toISOString().split('T')[0]
  }
  return streak
}
