export type { Database } from './database'

export interface Prayer {
  name: string
  completed: boolean
  onTime: boolean
  time?: string
}

export interface PrayerLog {
  fajr: Prayer
  dhuhr: Prayer
  asr: Prayer
  maghrib: Prayer
  isha: Prayer
}

export interface ActivityWeight {
  id: string
  name: string
  weight: number
  category: string
  hours?: number
}

export interface MappingEngineInput {
  dreamTotalHoursRequired: number
  dreamDate: string
  activityWeights: ActivityWeight[]
  todayActivities: { weightId: string; hours: number }[]
  last30DaysWeightedHours: number[]
}

export interface MappingEngineOutput {
  weightedHoursToday: number
  requiredPerDay: number
  deltaToday: number
  daysPulled: number
  trajectoryArrivalDate: string
  isOnTrack: boolean
  delayDays: number
}

export interface IdentityScoreInput {
  prayersOnTime: number
  totalPrayers: number
  quranPagesRead: number
  dhikrCompleted: boolean
  weightedHoursToday: number
  requiredPerDay: number
  sleepOnTime: boolean
  screenTimeWithinLimit: boolean
  streakIntact: boolean
  exercised: boolean
}

export interface NotificationPayload {
  title: string
  body: string
  icon?: string
  badge?: string
  url?: string
  tag?: string
}

export interface PrayerTime {
  Fajr: string
  Sunrise: string
  Dhuhr: string
  Asr: string
  Sunset: string
  Maghrib: string
  Isha: string
  Midnight: string
}

export interface AladhanResponse {
  code: number
  status: string
  data: {
    timings: PrayerTime
    date: {
      readable: string
      timestamp: string
      hijri: {
        date: string
        day: string
        month: { number: number; en: string; ar: string }
        year: string
      }
      gregorian: {
        date: string
        day: string
        month: { number: number; en: string }
        year: string
      }
    }
    meta: {
      latitude: number
      longitude: number
      timezone: string
      method: { id: number; name: string }
    }
  }
}
