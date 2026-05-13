const PRAYERS = ['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'] as const
export type PrayerName = typeof PRAYERS[number]

export type PrayerStatus = 0 | 1 | 2  // 0=missed, 1=alone (1pt), 2=jamat (2pts)

export interface PrayerLog {
  [key: string]: PrayerStatus
}

export interface ExtraPrayer {
  name: string
  status: PrayerStatus
}

export interface DailyScore {
  prayerScore: number       // 0 - (5*2 + extras*2)
  prayerMax: number
  habitScore: number
  habitMax: number
  totalScore: number        // 0-100 %
  breakdown: { name: string; earned: number; max: number; emoji: string }[]
}

export function computePrayerScore(
  prayerLog: Record<string, number>,
  extras: ExtraPrayer[] = []
): { earned: number; max: number } {
  const base = PRAYERS.reduce((sum, p) => sum + (prayerLog[p] ?? 0), 0)
  const extraEarned = extras.reduce((sum, e) => sum + (e.status ?? 0), 0)
  const earned = base + extraEarned
  const max = 5 * 2 + extras.length * 2   // 10 base + 2 per extra
  return { earned, max }
}

export function computeDailyScore(
  prayerLog: Record<string, number>,
  habits: { id: string; name: string; emoji: string; score_weight: number }[],
  habitLogs: { habit_id: string; completed: boolean }[],
  extras: ExtraPrayer[] = []
): DailyScore {
  const prayer = computePrayerScore(prayerLog, extras)

  const habitMax = habits.reduce((s, h) => s + (h.score_weight ?? 1), 0)
  const habitScore = habits.reduce((s, h) => {
    const done = habitLogs.find((l) => l.habit_id === h.id)?.completed
    return s + (done ? (h.score_weight ?? 1) : 0)
  }, 0)

  const totalPossible = prayer.max + habitMax
  const totalEarned = prayer.earned + habitScore
  const totalScore = totalPossible > 0 ? Math.round((totalEarned / totalPossible) * 100) : 0

  const breakdown = [
    { name: 'Prayers', earned: prayer.earned, max: prayer.max, emoji: '🕌' },
    ...habits.map((h) => ({
      name: h.name, emoji: h.emoji,
      earned: habitLogs.find((l) => l.habit_id === h.id)?.completed ? (h.score_weight ?? 1) : 0,
      max: h.score_weight ?? 1,
    })),
  ]

  return {
    prayerScore: prayer.earned, prayerMax: prayer.max,
    habitScore, habitMax, totalScore, breakdown,
  }
}

export { PRAYERS }
