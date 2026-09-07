/**
 * Health digest — AI-02 from the Improvement Blueprint.
 *
 * Turns raw health_logs rows into the facts a model may reason over. It exists
 * because health-recommend was reading `usual_sleep_time` / `usual_wake_time`,
 * one-time profile fields whose setup UI was removed when per-day sleep
 * sessions landed. For anyone who signed up afterwards those are NULL, so the
 * prompt said "Sleep schedule: ?-? (? hours)" while `health_logs.sleep_sessions`
 * and `health_logs.meals` held the real records, unread.
 *
 * Two rules shape everything below:
 *
 *  - Every metric carries its own observed count and eligible denominator.
 *    A mean over two recorded nights is not a mean over the week, and the
 *    digest must never let a model imply otherwise.
 *  - Absent stays null. Nothing here substitutes zero for "not recorded",
 *    because a zero invites a confident judgement about a day nobody logged.
 *
 * Pure and side-effect free: the caller fetches authorized rows, this shapes
 * them. It carries no name, email or free-text notes.
 */

import { FOOD_MENU, ensureCoreMeals, type Meal } from './food'
import { sleepSessionMinutes, type SleepSession } from './health'

export const DIGEST_SCHEMA_VERSION = 1

/** One health_logs row, narrowed to the fields the digest reads. */
export interface HealthLogRow {
  date: string
  sleep_sessions?: SleepSession[] | null
  sleep_hours?: number | null
  meals?: Meal[] | null
  water_glasses?: number | null
  steps?: number | null
  exercise_done?: boolean | null
  exercise_minutes?: number | null
  weight_kg?: number | null
}

export interface SleepDigest {
  /** Mean total sleep per recorded night, in minutes. Null when nothing recorded. */
  meanMinutes: number | null
  shortestMinutes: number | null
  longestMinutes: number | null
  recordedNights: number
  eligibleNights: number
  /** Nights with more than one session — a nap, a split night, or a broken one. */
  nightsWithNaps: number
  /** Sessions dropped because their times overlapped another the same day. */
  overlappingSessionsIgnored: number
}

export interface MealDigest {
  /** Meals holding at least one food. Empty slots are not meals eaten. */
  recordedMeals: number
  daysWithAnyMeal: number
  eligibleDays: number
  /** Mean meals eaten per day that had any meal recorded. */
  meanMealsPerRecordedDay: number | null
  /** Food counts by menu category, so the model reasons over kinds, not names. */
  categoryCounts: Record<string, number>
  /** Foods typed in by hand that aren't in the menu. */
  unknownFoods: number
  totalFoods: number
}

export interface HealthDigest {
  period: { startDate: string; endDate: string; eligibleDays: number }
  sleep: SleepDigest
  meals: MealDigest
  water: { meanGlasses: number | null; recordedDays: number }
  steps: { meanSteps: number | null; recordedDays: number }
  exercise: { daysExercised: number; totalMinutes: number | null; recordedDays: number }
  weight: { startKg: number | null; endKg: number | null; recordedDays: number }
  /** Plain statements of what is NOT known, carried into the prompt verbatim. */
  limitations: string[]
  schemaVersion: number
}

const CATEGORY_BY_FOOD = new Map(
  FOOD_MENU.map((f) => [f.name.toLowerCase(), f.cat]),
)

function mean(values: number[]): number | null {
  if (values.length === 0) return null
  return values.reduce((s, n) => s + n, 0) / values.length
}

function round(n: number | null, dp = 0): number | null {
  if (n == null) return null
  const f = 10 ** dp
  return Math.round(n * f) / f
}

/**
 * Total sleep for one day, ignoring sessions that overlap one already counted.
 *
 * Overlaps are double-counting, not extra sleep — a night entered twice, or a
 * nap typed inside the night it interrupted. Sessions are taken in order and a
 * later one is dropped if its span intersects a kept one. Sessions crossing
 * midnight are normalised onto a single timeline before comparison.
 */
function sleepForDay(sessions: SleepSession[]): { minutes: number; kept: number; ignored: number } {
  const spans: { start: number; end: number }[] = []
  let ignored = 0

  for (const s of sessions) {
    const length = sleepSessionMinutes(s.start, s.end)
    if (length == null || length === 0) continue

    const start = timeToMinutes(s.start)
    if (start == null) continue
    const end = start + length // may exceed 1440 when it crosses midnight

    const clashes = spans.some((k) => start < k.end && end > k.start)
    if (clashes) { ignored++; continue }
    spans.push({ start, end })
  }

  return {
    minutes: spans.reduce((sum, s) => sum + (s.end - s.start), 0),
    kept: spans.length,
    ignored,
  }
}

function timeToMinutes(time: string): number | null {
  const m = /^(\d{1,2}):(\d{2})/.exec(time.trim())
  if (!m) return null
  const h = Number(m[1])
  const min = Number(m[2])
  if (h > 23 || min > 59) return null
  return h * 60 + min
}

export function buildHealthDigest(
  rows: HealthLogRow[],
  period: { startDate: string; endDate: string; eligibleDays: number },
): HealthDigest {
  const limitations: string[] = []

  // ── Sleep ──────────────────────────────────────────────────────────────
  const nightTotals: number[] = []
  let nightsWithNaps = 0
  let overlappingSessionsIgnored = 0

  for (const row of rows) {
    const sessions = Array.isArray(row.sleep_sessions) ? row.sleep_sessions : []
    if (sessions.length === 0) continue

    const { minutes, kept, ignored } = sleepForDay(sessions)
    overlappingSessionsIgnored += ignored
    if (kept === 0 || minutes === 0) continue

    nightTotals.push(minutes)
    if (kept > 1) nightsWithNaps++
  }

  const sleep: SleepDigest = {
    meanMinutes: round(mean(nightTotals)),
    shortestMinutes: nightTotals.length ? Math.min(...nightTotals) : null,
    longestMinutes: nightTotals.length ? Math.max(...nightTotals) : null,
    recordedNights: nightTotals.length,
    eligibleNights: period.eligibleDays,
    nightsWithNaps,
    overlappingSessionsIgnored,
  }

  if (sleep.recordedNights === 0) {
    limitations.push(`No sleep was recorded in these ${period.eligibleDays} days.`)
  } else if (sleep.recordedNights < period.eligibleDays) {
    limitations.push(
      `Sleep was recorded on ${sleep.recordedNights} of ${period.eligibleDays} days; the rest are unknown, not zero.`,
    )
  }
  if (overlappingSessionsIgnored > 0) {
    limitations.push(
      `${overlappingSessionsIgnored} sleep session(s) overlapped another and were not counted twice.`,
    )
  }

  // ── Meals ──────────────────────────────────────────────────────────────
  const categoryCounts: Record<string, number> = {}
  let recordedMeals = 0
  let daysWithAnyMeal = 0
  let unknownFoods = 0
  let totalFoods = 0

  for (const row of rows) {
    const meals = ensureCoreMeals(Array.isArray(row.meals) ? row.meals : [])
    const eaten = meals.filter((m) => m.items.length > 0)
    if (eaten.length === 0) continue

    daysWithAnyMeal++
    recordedMeals += eaten.length
    for (const meal of eaten) {
      for (const item of meal.items) {
        totalFoods++
        const cat = CATEGORY_BY_FOOD.get((item.name ?? '').trim().toLowerCase())
        if (cat) categoryCounts[cat] = (categoryCounts[cat] ?? 0) + 1
        else unknownFoods++
      }
    }
  }

  const meals: MealDigest = {
    recordedMeals,
    daysWithAnyMeal,
    eligibleDays: period.eligibleDays,
    meanMealsPerRecordedDay: round(
      daysWithAnyMeal > 0 ? recordedMeals / daysWithAnyMeal : null, 1,
    ),
    categoryCounts,
    unknownFoods,
    totalFoods,
  }

  if (daysWithAnyMeal === 0) {
    limitations.push(`No meals were recorded in these ${period.eligibleDays} days.`)
  } else if (daysWithAnyMeal < period.eligibleDays) {
    limitations.push(
      `Meals were recorded on ${daysWithAnyMeal} of ${period.eligibleDays} days.`,
    )
  }
  if (unknownFoods > 0) {
    limitations.push(
      `${unknownFoods} food(s) were entered by hand and are not in the menu, so their type is unknown.`,
    )
  }

  // ── Everything else ────────────────────────────────────────────────────
  const waterDays = rows.filter((r) => (r.water_glasses ?? 0) > 0)
  const stepDays = rows.filter((r) => (r.steps ?? 0) > 0)
  const exerciseDays = rows.filter((r) => r.exercise_done)
  const exerciseMinuteDays = exerciseDays.filter((r) => (r.exercise_minutes ?? 0) > 0)
  const weightDays = rows
    .filter((r) => (r.weight_kg ?? 0) > 0)
    .sort((a, b) => (a.date < b.date ? -1 : 1))

  if (exerciseDays.length > exerciseMinuteDays.length) {
    limitations.push(
      `${exerciseDays.length - exerciseMinuteDays.length} exercise day(s) have no duration recorded; duration is unknown, not zero.`,
    )
  }

  return {
    period,
    sleep,
    meals,
    water: {
      meanGlasses: round(mean(waterDays.map((r) => r.water_glasses!)), 1),
      recordedDays: waterDays.length,
    },
    steps: {
      meanSteps: round(mean(stepDays.map((r) => r.steps!))),
      recordedDays: stepDays.length,
    },
    exercise: {
      daysExercised: exerciseDays.length,
      totalMinutes: exerciseMinuteDays.length
        ? exerciseMinuteDays.reduce((s, r) => s + (r.exercise_minutes ?? 0), 0)
        : null,
      recordedDays: exerciseDays.length,
    },
    weight: {
      startKg: weightDays.length ? weightDays[0].weight_kg! : null,
      endKg: weightDays.length ? weightDays[weightDays.length - 1].weight_kg! : null,
      recordedDays: weightDays.length,
    },
    limitations,
    schemaVersion: DIGEST_SCHEMA_VERSION,
  }
}

/** Renders the digest for a prompt, stating coverage on every line. */
export function describeHealthDigest(d: HealthDigest): string {
  const hm = (mins: number | null) =>
    mins == null ? 'not recorded' : `${Math.floor(mins / 60)}h ${Math.round(mins % 60)}m`

  const lines = [
    `PERIOD: ${d.period.startDate} to ${d.period.endDate} (${d.period.eligibleDays} days)`,
    '',
    'SLEEP',
    `  Average per recorded night: ${hm(d.sleep.meanMinutes)}`,
    `  Recorded on ${d.sleep.recordedNights} of ${d.sleep.eligibleNights} nights`,
    d.sleep.recordedNights > 0
      ? `  Shortest ${hm(d.sleep.shortestMinutes)}, longest ${hm(d.sleep.longestMinutes)}`
      : '  No sleep sessions recorded',
    `  Nights including a nap or split sleep: ${d.sleep.nightsWithNaps}`,
    '',
    'MEALS',
    `  ${d.meals.recordedMeals} meals recorded across ${d.meals.daysWithAnyMeal} of ${d.meals.eligibleDays} days`,
    `  Average per day that had any meal: ${d.meals.meanMealsPerRecordedDay ?? 'not recorded'}`,
    `  Foods by type: ${
      Object.keys(d.meals.categoryCounts).length
        ? Object.entries(d.meals.categoryCounts)
            .sort((a, b) => b[1] - a[1])
            .map(([cat, n]) => `${cat} ${n}`).join(', ')
        : 'none recorded'
    }`,
    '',
    'OTHER',
    `  Water: ${d.water.meanGlasses ?? 'not recorded'} glasses/day over ${d.water.recordedDays} recorded days`,
    `  Steps: ${d.steps.meanSteps ?? 'not recorded'} per day over ${d.steps.recordedDays} recorded days`,
    `  Exercise: ${d.exercise.daysExercised} day(s)${
      d.exercise.totalMinutes != null ? `, ${d.exercise.totalMinutes} min total` : ', duration not recorded'
    }`,
    `  Weight: ${
      d.weight.endKg != null ? `${d.weight.startKg} to ${d.weight.endKg} kg` : 'not recorded'
    }`,
  ]

  if (d.limitations.length) {
    lines.push('', 'WHAT IS NOT KNOWN — do not fill these gaps with assumptions:')
    for (const l of d.limitations) lines.push(`  - ${l}`)
  }

  return lines.join('\n')
}
