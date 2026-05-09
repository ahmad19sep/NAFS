const PRAYERS = ['Fajr', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'] as const
export type PrayerName = typeof PRAYERS[number]

export interface PrayerLog {
  [key: string]: boolean
}

export interface DailyScore {
  prayerScore: number      // 0-5
  prayerMax: number        // always 5
  habitScore: number       // 0 - sum of all habit weights
  habitMax: number
  totalScore: number       // 0-100 percentage
  breakdown: { name: string; earned: number; max: number; emoji: string }[]
}

export function computeDailyScore(
  prayerLog: PrayerLog,
  habits: { id: string; name: string; emoji: string; score_weight: number }[],
  habitLogs: { habit_id: string; completed: boolean }[]
): DailyScore {
  // Prayer score
  const prayerScore = PRAYERS.filter((p) => prayerLog[p]).length
  const prayerMax = 5

  // Habit score
  const habitMax = habits.reduce((s, h) => s + (h.score_weight ?? 1), 0)
  const habitScore = habits.reduce((s, h) => {
    const done = habitLogs.find((l) => l.habit_id === h.id)?.completed
    return s + (done ? (h.score_weight ?? 1) : 0)
  }, 0)

  const totalPossible = prayerMax + habitMax
  const totalEarned = prayerScore + habitScore
  const totalScore = totalPossible > 0 ? Math.round((totalEarned / totalPossible) * 100) : 0

  const breakdown = [
    { name: 'Prayers', earned: prayerScore, max: prayerMax, emoji: '🕌' },
    ...habits.map((h) => ({
      name: h.name,
      emoji: h.emoji,
      earned: habitLogs.find((l) => l.habit_id === h.id)?.completed ? (h.score_weight ?? 1) : 0,
      max: h.score_weight ?? 1,
    })),
  ]

  return { prayerScore, prayerMax, habitScore, habitMax, totalScore, breakdown }
}

export { PRAYERS }
