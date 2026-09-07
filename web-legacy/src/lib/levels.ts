/**
 * Levels — a progress marker that only ever goes up.
 *
 * Deliberately measured in **days you recorded something**, not in score.
 *
 * A level driven by score would fall on a bad week, which turns a motivator
 * into a punishment exactly when someone is already struggling. It would also
 * inherit the missing-day ambiguity documented in docs/SCORING-AUDIT.md — an
 * unlogged day would quietly cost you a level. Counting days shown up is
 * unambiguous, monotonic, and rewards the thing the app is actually for.
 *
 * Nothing here is stored: the level is derived, so it cannot drift out of sync
 * the way habits.current_streak did.
 */

export interface Level {
  /** 1-based. */
  number: number
  name: string
  /** Days needed to reach this level. */
  at: number
}

/**
 * Close together early, so the first few arrive quickly and the habit forms,
 * then widening — the later ones should mean something.
 */
export const LEVELS: Level[] = [
  { number: 1,  name: 'Day one',      at: 1 },
  { number: 2,  name: 'Getting going', at: 3 },
  { number: 3,  name: 'First week',   at: 7 },
  { number: 4,  name: 'Two weeks',    at: 14 },
  { number: 5,  name: 'One month',    at: 30 },
  { number: 6,  name: 'Two months',   at: 60 },
  { number: 7,  name: 'Hundred days', at: 100 },
  { number: 8,  name: 'Half a year',  at: 180 },
  { number: 9,  name: 'Nine months',  at: 270 },
  { number: 10, name: 'One year',     at: 365 },
  { number: 11, name: 'Two years',    at: 730 },
]

export interface LevelProgress {
  /** Null before the first day is recorded — no level yet, rather than "level 0". */
  current: Level | null
  next: Level | null
  daysLogged: number
  /** Days still needed for `next`. Null once every level is reached. */
  daysToNext: number | null
  /** 0–100 through the current band, for a progress bar. */
  pctToNext: number
}

export function levelFor(daysLogged: number): LevelProgress {
  const days = Math.max(0, Math.floor(daysLogged))

  let current: Level | null = null
  for (const l of LEVELS) {
    if (days >= l.at) current = l
    else break
  }

  const next = LEVELS.find((l) => l.at > days) ?? null

  if (!next) {
    return { current, next: null, daysLogged: days, daysToNext: null, pctToNext: 100 }
  }

  // Measure across the band between levels, so the bar fills evenly within a
  // band rather than crawling because later bands are wider.
  const from = current?.at ?? 0
  const span = next.at - from
  const done = days - from

  return {
    current,
    next,
    daysLogged: days,
    daysToNext: next.at - days,
    pctToNext: span > 0 ? Math.round((done / span) * 100) : 0,
  }
}

/**
 * Distinct dates on which anything was recorded.
 *
 * Takes rows from several day-keyed tables; a date counts once however many
 * tables it appears in. Rows without a usable date are ignored rather than
 * counted as a day.
 */
export function countDaysLogged(...sources: ({ date?: string | null }[] | null | undefined)[]): number {
  const days = new Set<string>()
  for (const rows of sources) {
    for (const row of rows ?? []) {
      const d = row?.date
      if (typeof d === 'string' && /^\d{4}-\d{2}-\d{2}/.test(d)) days.add(d.slice(0, 10))
    }
  }
  return days.size
}
