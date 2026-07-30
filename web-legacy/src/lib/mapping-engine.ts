import type { ActivityWeight, MappingEngineInput, MappingEngineOutput, IdentityScoreInput } from '@/types'
import { daysUntil, addDays, clampScore } from './utils'

export function computeWeightedHours(
  activities: { weight: number; hours: number }[]
): number {
  return activities.reduce((sum, a) => sum + a.hours * a.weight, 0)
}

export function computeRequiredPerDay(
  totalWeightedRequired: number,
  dreamDate: string
): number {
  const days = Math.max(1, daysUntil(dreamDate))
  return totalWeightedRequired / days
}

export function computeTodaysPull(
  weightedHoursToday: number,
  requiredPerDay: number
): { deltaToday: number; daysPulled: number } {
  const deltaToday = weightedHoursToday - requiredPerDay
  const daysPulled = requiredPerDay > 0 ? deltaToday / requiredPerDay : 0
  return { deltaToday, daysPulled }
}

export function computeTrajectory(
  last30DaysWeightedHours: number[],
  totalWeightedRequired: number,
  alreadyCompletedWeightedHours: number,
  dreamDate: string
): { arrivalDate: string; delayDays: number; isOnTrack: boolean; avgPerDay: number } {
  const days = last30DaysWeightedHours.length || 1
  const avgPerDay = last30DaysWeightedHours.reduce((s, h) => s + h, 0) / days

  const daysLeft = Math.max(1, daysUntil(dreamDate))
  const remainingRequired = Math.max(0, totalWeightedRequired - alreadyCompletedWeightedHours)

  if (avgPerDay <= 0) {
    return { arrivalDate: 'Never', delayDays: 9999, isOnTrack: false, avgPerDay: 0 }
  }

  const daysNeeded = remainingRequired / avgPerDay
  const delayDays = Math.max(0, Math.round(daysNeeded - daysLeft))
  const isOnTrack = delayDays === 0
  const today = new Date().toISOString().split('T')[0]
  const arrivalDate = addDays(today, Math.round(daysNeeded))

  return { arrivalDate, delayDays, isOnTrack, avgPerDay }
}

export function computeMappingEngine(input: MappingEngineInput): MappingEngineOutput {
  const totalWeightedRequired = input.dreamTotalHoursRequired * 1.8

  const todayActivities = input.todayActivities.map((a) => {
    const weight = input.activityWeights.find((w) => w.id === a.weightId)
    return { weight: weight?.weight ?? 1, hours: a.hours }
  })

  const weightedHoursToday = computeWeightedHours(todayActivities)
  const requiredPerDay = computeRequiredPerDay(totalWeightedRequired, input.dreamDate)
  const { deltaToday, daysPulled } = computeTodaysPull(weightedHoursToday, requiredPerDay)

  const { arrivalDate, delayDays, isOnTrack } = computeTrajectory(
    input.last30DaysWeightedHours,
    totalWeightedRequired,
    0,
    input.dreamDate
  )

  return {
    weightedHoursToday,
    requiredPerDay,
    deltaToday,
    daysPulled,
    trajectoryArrivalDate: arrivalDate,
    isOnTrack,
    delayDays,
  }
}

export function computeIdentityScore(input: IdentityScoreInput): number {
  // Deen score (30%)
  const prayerScore = input.totalPrayers > 0
    ? (input.prayersOnTime / input.totalPrayers) * 100
    : 0
  const quranScore = input.quranPagesRead >= 1 ? 100 : input.quranPagesRead * 100
  const dhikrScore = input.dhikrCompleted ? 100 : 0
  const deenScore = (prayerScore * 0.6 + quranScore * 0.3 + dhikrScore * 0.1)

  // Dream Work score (40%)
  const dreamScore = input.requiredPerDay > 0
    ? Math.min(100, (input.weightedHoursToday / input.requiredPerDay) * 100)
    : 0

  // Discipline score (20%)
  const sleepPoints = input.sleepOnTime ? 100 : 40
  const screenPoints = input.screenTimeWithinLimit ? 100 : 30
  const streakPoints = input.streakIntact ? 100 : 50
  const disciplineScore = (sleepPoints + screenPoints + streakPoints) / 3

  // Body & Mind score (10%)
  const bodyScore = input.exercised ? 100 : 40

  const total =
    deenScore * 0.3 +
    dreamScore * 0.4 +
    disciplineScore * 0.2 +
    bodyScore * 0.1

  return clampScore(total)
}

export const DEFAULT_ACTIVITY_WEIGHTS: Omit<ActivityWeight, 'id'>[] = [
  { name: 'Shipping code / production work', weight: 3.0, category: 'dream' },
  { name: 'Portfolio / open source work', weight: 2.5, category: 'dream' },
  { name: 'Deep learning (CS theory, courses)', weight: 1.5, category: 'learning' },
  { name: 'Tutorials / passive video watching', weight: 0.5, category: 'learning' },
  { name: 'Reading about productivity', weight: 0.0, category: 'distraction' },
]
