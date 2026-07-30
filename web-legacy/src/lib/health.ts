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
