/**
 * Repeated misses — the evidence behind "you keep skipping this".
 *
 * Deterministic, no model. It finds things the user keeps not doing, with the
 * counts, so the coach can name them and the user can decide what to do about
 * it. It does not punish, score, or judge — it reports what the records show.
 *
 * The rule that shapes it most: UNRECORDED IS NOT MISSED.
 *
 *  - A prayer counts as missed only when the user recorded it as missed
 *    (status 0 on a day that has a prayer row). A day with no prayer row is
 *    unknown, and unknown is never counted against anyone.
 *  - A habit counts as missed only on a day the user was demonstrably using
 *    the app — something else was logged that day — but this habit was not
 *    done. A day with nothing logged at all is "forgot the app", not "skipped
 *    this habit", and is left out.
 *
 * Today is excluded from every count: it is still in progress.
 */

import { isScheduledOn, isComplete, type StreakHabit, type StreakLog } from './streak'

export interface RepeatedMiss {
  kind: 'habit' | 'prayer'
  id: string
  label: string
  emoji: string
  /** Days it was missed, within the window. */
  missed: number
  /** Days it could have been done — scheduled and active for habits, recorded for prayers. */
  of: number
  windowDays: number
}

export interface MissInput {
  today: string
  habits: (StreakHabit & { id: string; name: string; emoji?: string })[]
  habitLogs: (StreakLog & { habit_id: string })[]
  /** Rows with the five prayers as 0 | 1 | 2. */
  prayerLogs: { date: string; fajr?: number; dhuhr?: number; asr?: number; maghrib?: number; isha?: number }[]
  deenEnabled: boolean
  /** Days to look back, not counting today. */
  windowDays?: number
  /** Misses needed before it is reported as a pattern. */
  threshold?: number
}

const PRAYERS = [
  { key: 'fajr',    label: 'Fajr',    emoji: '🌅' },
  { key: 'dhuhr',   label: 'Dhuhr',   emoji: '☀️' },
  { key: 'asr',     label: 'Asr',     emoji: '🌤️' },
  { key: 'maghrib', label: 'Maghrib', emoji: '🌇' },
  { key: 'isha',    label: 'Isha',    emoji: '🌙' },
] as const

function shift(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split('-').map(Number)
  const dt = new Date(Date.UTC(y, m - 1, d))
  dt.setUTCDate(dt.getUTCDate() + days)
  return dt.toISOString().slice(0, 10)
}

export function findRepeatedMisses(input: MissInput): RepeatedMiss[] {
  const windowDays = input.windowDays ?? 7
  const threshold = input.threshold ?? 3

  // Yesterday back to (yesterday - windowDays + 1). Today is in progress.
  const days: string[] = []
  for (let i = 1; i <= windowDays; i++) days.push(shift(input.today, -i))

  // Days the user was demonstrably active: anything logged at all.
  const activeDays = new Set<string>()
  for (const l of input.habitLogs) activeDays.add(l.date)
  for (const p of input.prayerLogs) activeDays.add(p.date)

  const out: RepeatedMiss[] = []

  // ── Habits ───────────────────────────────────────────────────────────────
  const logsByHabit = new Map<string, Map<string, StreakLog>>()
  for (const l of input.habitLogs) {
    if (!logsByHabit.has(l.habit_id)) logsByHabit.set(l.habit_id, new Map())
    logsByHabit.get(l.habit_id)!.set(l.date, l)
  }

  for (const h of input.habits) {
    if (h.is_paused) continue
    const logs = logsByHabit.get(h.id) ?? new Map<string, StreakLog>()
    let of = 0
    let missed = 0
    for (const day of days) {
      if (!isScheduledOn(h, day)) continue
      if (!activeDays.has(day)) continue // forgot the app, not skipped this
      of++
      if (!isComplete(h, logs.get(day))) missed++
    }
    if (of > 0 && missed >= threshold) {
      out.push({ kind: 'habit', id: h.id, label: h.name, emoji: h.emoji || '🔁', missed, of, windowDays })
    }
  }

  // ── Prayers ──────────────────────────────────────────────────────────────
  if (input.deenEnabled) {
    const byDate = new Map(input.prayerLogs.map((p) => [p.date, p]))
    for (const pr of PRAYERS) {
      let of = 0
      let missed = 0
      for (const day of days) {
        const row = byDate.get(day)
        if (!row) continue // no row: unknown, never a miss
        const v = row[pr.key]
        if (v == null) continue
        of++
        if (Number(v) === 0) missed++
      }
      if (of > 0 && missed >= threshold) {
        out.push({ kind: 'prayer', id: pr.key, label: pr.label, emoji: pr.emoji, missed, of, windowDays })
      }
    }
  }

  // Worst first: most misses, then the highest miss rate.
  return out.sort((a, b) => (b.missed - a.missed) || (b.missed / b.of - a.missed / a.of))
}

/** One line per pattern, for the coach's context and the Home card. */
export function describeMiss(m: RepeatedMiss): string {
  const what = m.kind === 'prayer' ? `${m.label} recorded missed` : `${m.label} not done`
  return `${what} ${m.missed} of the last ${m.of} ${m.kind === 'prayer' ? 'recorded' : 'active'} day${m.of === 1 ? '' : 's'}`
}
