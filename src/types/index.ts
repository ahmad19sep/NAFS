export type { Database } from './database'

// ---------- Habits (v3) ----------
export type HabitType = 'simple' | 'counter' | 'duration' | 'subject'
export type ScheduleKind = 'daily' | 'weekdays' | 'per_week'
export type Weekday = 'mon' | 'tue' | 'wed' | 'thu' | 'fri' | 'sat' | 'sun'

export interface Habit {
  id: string
  user_id: string
  name: string
  emoji: string
  type: HabitType
  // counter / duration target
  target_value: number
  unit: string
  time_target_mins: number
  // subject-tracked
  subject_name: string | null
  subject_total: number | null
  subject_position: number
  subject_unit: string | null
  // schedule
  schedule_kind: ScheduleKind
  schedule_days: Weekday[] | null
  weekly_target: number | null
  reminder_time: string | null   // 'HH:MM' from postgres TIME
  // meta
  why: string | null
  category: string
  score_weight: number
  note_template: string | null
  current_streak: number
  longest_streak: number
  is_active: boolean
  is_paused: boolean
  sort_order: number
  created_at: string
}

export interface HabitLog {
  id: string
  user_id: string
  habit_id: string
  date: string
  value: number          // counter total / minutes / subject pages-today
  completed: boolean
  duration_mins: number
  notes: string | null
  created_at: string
}

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
