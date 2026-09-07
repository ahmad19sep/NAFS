/**
 * Possible causes — what was different on the days a thing was missed.
 *
 * For each repeated miss, this compares the days it was missed against the
 * days it was done on two things the app records: sleep and phone screen
 * time. A difference is reported only when both sides have enough days and
 * the gap is large enough to mean something.
 *
 * It is evidence for a question — "on the four mornings you missed Fajr you
 * had slept five hours; is that it?" — never a verdict. Two numbers moving
 * together is not a cause, and the coach is prompted to say so. When nothing
 * here stands out, the honest answer is to ask the user and remember what
 * they say.
 *
 * Sleep is matched by the date it was recorded on, which for a morning miss
 * is usually the night before it. That is an approximation and is described
 * as "recorded that day", not "the night before".
 */

import type { RepeatedMiss } from './misses'

export type Factor = 'sleep' | 'screen_time'

export interface Correlate {
  subject: string
  label: string
  emoji: string
  factor: Factor
  /** Averages in minutes over the days that had a value for this factor. */
  missedAvgMinutes: number
  doneAvgMinutes: number
  /** How many of the missed / done days had a value. */
  missedDays: number
  doneDays: number
}

export interface CorrelateInput {
  misses: RepeatedMiss[]
  /** Minutes slept, by the date it was recorded on. Absent days are unknown, not zero. */
  sleepMinutesByDate: Record<string, number>
  /** Phone screen minutes, by date. Absent days are unknown, not zero. */
  screenMinutesByDate: Record<string, number>
  /** Days needed on each side before a comparison is made. */
  minDays?: number
}

/** The smallest average gap worth mentioning, in minutes. */
const MIN_GAP: Record<Factor, number> = { sleep: 45, screen_time: 30 }

export function findCorrelates(input: CorrelateInput): Correlate[] {
  const minDays = input.minDays ?? 2
  const out: Correlate[] = []

  const factors: { factor: Factor; byDate: Record<string, number> }[] = [
    { factor: 'sleep', byDate: input.sleepMinutesByDate },
    { factor: 'screen_time', byDate: input.screenMinutesByDate },
  ]

  for (const m of input.misses) {
    for (const { factor, byDate } of factors) {
      const missed = valuesFor(m.missedDates, byDate)
      const done = valuesFor(m.doneDates, byDate)
      if (missed.length < minDays || done.length < minDays) continue

      const missedAvg = mean(missed)
      const doneAvg = mean(done)
      if (Math.abs(missedAvg - doneAvg) < MIN_GAP[factor]) continue

      out.push({
        subject: `${m.kind}:${m.id}`,
        label: m.label,
        emoji: m.emoji,
        factor,
        missedAvgMinutes: Math.round(missedAvg),
        doneAvgMinutes: Math.round(doneAvg),
        missedDays: missed.length,
        doneDays: done.length,
      })
    }
  }

  return out
}

/** One plain sentence with both numbers and both denominators. */
export function describeCorrelate(c: Correlate): string {
  const missedSide = `the ${c.missedDays} day${c.missedDays === 1 ? '' : 's'} it was missed`
  const doneSide = `the ${c.doneDays} it was done`
  if (c.factor === 'sleep') {
    return `${c.label}: sleep recorded that day averaged ${hm(c.missedAvgMinutes)} on ${missedSide}, ${hm(c.doneAvgMinutes)} on ${doneSide}`
  }
  return `${c.label}: phone screen time averaged ${hm(c.missedAvgMinutes)} on ${missedSide}, ${hm(c.doneAvgMinutes)} on ${doneSide}`
}

function valuesFor(dates: string[], byDate: Record<string, number>): number[] {
  const out: number[] = []
  for (const d of dates) {
    const v = byDate[d]
    if (typeof v === 'number' && Number.isFinite(v)) out.push(v)
  }
  return out
}

function mean(xs: number[]): number {
  return xs.reduce((s, x) => s + x, 0) / xs.length
}

/** 310 → "5h 10m", 45 → "45m", 120 → "2h". */
export function hm(minutes: number): string {
  const h = Math.floor(minutes / 60)
  const m = Math.round(minutes % 60)
  if (h === 0) return `${m}m`
  return m === 0 ? `${h}h` : `${h}h ${m}m`
}
