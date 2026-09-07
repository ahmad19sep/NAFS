/**
 * How much of today's health tracking is done — UI-01.
 *
 * One definition, used by both Home and the Health page. They disagreed
 * before: Home hardcoded a denominator of 4 (water, steps, sleep, exercise)
 * while Health derived its own from the categories actually enabled, so the
 * same day showed as 0/4 on one screen and 0/5 on the other. Neither number was
 * wrong on its own terms, which is what made it confusing rather than obviously
 * broken.
 *
 * The rule: sleep and meals are permanent, water/steps/exercise count only
 * while the user keeps them, and custom metrics count when configured.
 */

import { ensureCoreMeals, mealsEaten, type Meal } from './food'
import { sleepSessionMinutes, type SleepSession } from './health'
import { isMetricDone, type CustomMetric, type ExtrasValues } from './health'

/** The optional built-ins, in the order the Health page shows them. */
export const OPTIONAL_HEALTH_IDS = ['water', 'steps', 'exercise'] as const

export interface HealthDayRow {
  sleep_sessions?: SleepSession[] | null
  sleep_hours?: number | null
  meals?: Meal[] | null
  water_glasses?: number | null
  steps?: number | null
  exercise_done?: boolean | null
  extras?: ExtrasValues | null
}

export interface HealthProgress {
  done: number
  total: number
  /** Which categories counted, so a caller can explain the denominator. */
  categories: { id: string; label: string; done: boolean }[]
}

function sleptAnything(row: HealthDayRow | null | undefined): boolean {
  if (!row) return false
  const sessions = Array.isArray(row.sleep_sessions) ? row.sleep_sessions : []
  if (sessions.some((s) => (sleepSessionMinutes(s.start, s.end) ?? 0) > 0)) return true
  // Rows written before sleep sessions existed only have the total.
  return (row.sleep_hours ?? 0) > 0
}

export function healthProgress(
  row: HealthDayRow | null | undefined,
  hiddenDefaults: string[] = [],
  extrasConfig: CustomMetric[] = [],
): HealthProgress {
  const hidden = new Set(hiddenDefaults)
  const extras = (row?.extras ?? {}) as ExtrasValues

  const categories: { id: string; label: string; done: boolean }[] = [
    // Permanent: these two cannot be switched off, so they are always in the
    // denominator whether or not anything was recorded.
    { id: 'sleep', label: 'Sleep', done: sleptAnything(row) },
    { id: 'meals', label: 'Meals', done: mealsEaten(ensureCoreMeals(row?.meals ?? [])) > 0 },
  ]

  if (!hidden.has('water'))    categories.push({ id: 'water',    label: 'Water',    done: (row?.water_glasses ?? 0) > 0 })
  if (!hidden.has('steps'))    categories.push({ id: 'steps',    label: 'Steps',    done: (row?.steps ?? 0) > 0 })
  if (!hidden.has('exercise')) categories.push({ id: 'exercise', label: 'Exercise', done: !!row?.exercise_done })

  for (const m of extrasConfig) {
    categories.push({ id: m.id, label: m.name, done: isMetricDone(m, extras[m.id]) })
  }

  return {
    done: categories.filter((c) => c.done).length,
    total: categories.length,
    categories,
  }
}
