// Custom-metric types for the Health page.
//
// Built-in metrics (water/steps/exercise/weight) live in dedicated columns.
// Anything else the user wants to track is a "custom metric" — its definition
// is stored in users.health_extras_config, and per-day values in
// health_logs.extras.

export type CustomMetricType = 'boolean' | 'counter' | 'number'

export interface CustomMetric {
  id: string                 // stable id (kebab-case of name + random suffix)
  name: string
  emoji: string
  type: CustomMetricType
  /** counter / number target (omit for boolean) */
  target?: number | null
  /** display unit, e.g. 'cups', 'min', 'mg' */
  unit?: string | null
}

export type ExtrasValues = Record<string, number | boolean | null>

// ─── Sleep sessions ───────────────────────────────────────────────────────────
// A day can hold several sleep periods (a night plus naps). They live in
// health_logs.sleep_sessions; the summed total is written to sleep_hours so the
// dashboard, reports and AI prompts keep reading the single column they always did.

/** One sleep period. Times are "HH:MM"; an end at or before start crossed midnight. */
export interface SleepSession {
  id: string
  start: string
  end: string
}

export function makeSleepSessionId(): string {
  return `s-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`
}

/** Accepts "HH:MM" or the "HH:MM:SS" that Postgres TIME columns return. */
export function parseTimeToMinutes(time: string): number | null {
  const m = /^(\d{1,2}):(\d{2})/.exec(time.trim())
  if (!m) return null
  const hours = Number(m[1])
  const mins = Number(m[2])
  if (hours > 23 || mins > 59) return null
  return hours * 60 + mins
}

/**
 * Length of one sleep period in minutes, or null when either time is unusable.
 * An end at or before the start is read as crossing midnight, so 23:00 → 06:30
 * is 7h 30m rather than negative.
 */
export function sleepSessionMinutes(start: string, end: string): number | null {
  const from = parseTimeToMinutes(start)
  const to = parseTimeToMinutes(end)
  if (from == null || to == null) return null
  if (to === from) return 0
  return to > from ? to - from : to + 1440 - from
}

/** Periods with unusable times are skipped rather than counted as zero. */
export function totalSleepMinutes(sessions: SleepSession[]): number {
  return sessions.reduce((sum, s) => sum + (sleepSessionMinutes(s.start, s.end) ?? 0), 0)
}

export function formatDuration(mins: number): string {
  const h = Math.floor(mins / 60)
  const m = mins % 60
  if (!h) return `${m}m`
  if (!m) return `${h}h`
  return `${h}h ${m}m`
}

export function makeMetricId(name: string): string {
  const slug = name.toLowerCase().trim()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  const rand = Math.random().toString(36).slice(2, 6)
  return slug ? `${slug}-${rand}` : `m-${rand}`
}

export function isMetricDone(metric: CustomMetric, value: number | boolean | null | undefined): boolean {
  if (value === undefined || value === null) return false
  if (metric.type === 'boolean') return value === true
  if (metric.type === 'counter' || metric.type === 'number') {
    const n = Number(value)
    if (isNaN(n)) return false
    if (metric.target == null) return n > 0
    return n >= metric.target
  }
  return false
}
