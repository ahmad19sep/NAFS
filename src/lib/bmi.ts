export type BmiCategory = 'underweight' | 'normal' | 'overweight' | 'obese'

export interface BmiResult {
  value: number          // BMI rounded to 1 decimal
  category: BmiCategory
  label: string          // "Underweight" / "Healthy" / "Overweight" / "Obese"
  emoji: string
  tone: 'red' | 'gold' | 'emerald' | 'orange'
  range: string          // e.g. "18.5 – 24.9"
}

export function computeBMI(weightKg: number, heightCm: number): BmiResult | null {
  if (!weightKg || !heightCm || heightCm <= 0) return null
  const meters = heightCm / 100
  const v = weightKg / (meters * meters)
  const value = Math.round(v * 10) / 10

  if (value < 18.5)
    return { value, category: 'underweight', label: 'Underweight', emoji: '🪶', tone: 'orange', range: '< 18.5' }
  if (value < 25)
    return { value, category: 'normal',      label: 'Healthy',     emoji: '✅', tone: 'emerald', range: '18.5 – 24.9' }
  if (value < 30)
    return { value, category: 'overweight',  label: 'Overweight',  emoji: '⚠️', tone: 'gold',    range: '25.0 – 29.9' }
  return { value, category: 'obese',         label: 'Obese',       emoji: '🚨', tone: 'red',     range: '≥ 30.0' }
}

/** Difference between sleep_time and wake_time as decimal hours, handling overnight. */
export function sleepHoursBetween(sleepTime?: string | null, wakeTime?: string | null): number | null {
  if (!sleepTime || !wakeTime) return null
  const [sh, sm] = sleepTime.split(':').map(Number)
  const [wh, wm] = wakeTime.split(':').map(Number)
  if ([sh, sm, wh, wm].some((n) => isNaN(n))) return null
  let mins = (wh * 60 + wm) - (sh * 60 + sm)
  if (mins <= 0) mins += 24 * 60   // overnight
  return Math.round((mins / 60) * 10) / 10
}
