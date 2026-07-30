export type Json = string | number | boolean | null | { [key: string]: Json | undefined } | Json[]

// ─── Row shapes ───────────────────────────────────────────────────────────────

export interface UserRow {
  id: string
  name: string
  email: string
  avatar_url: string | null
  timezone: string
  onboarding_complete: boolean
  push_subscription: Json | null
  gender: string | null
  height_cm: number | null
  weight_kg: number | null
  usual_sleep_time: string | null
  usual_wake_time: string | null
  health_extras_config: Json
  created_at: string
}

export interface HabitRow {
  id: string
  user_id: string
  name: string
  emoji: string
  type: 'boolean' | 'count' | 'duration'
  target_value: number
  unit: string
  category: string
  score_weight: number
  time_target_mins: number
  current_streak: number
  longest_streak: number
  is_active: boolean
  sort_order: number
  created_at: string
}

export interface HabitLogRow {
  id: string
  user_id: string
  habit_id: string
  date: string
  value: number
  duration_mins: number
  completed: boolean
  created_at: string
}

export interface TaskRow {
  id: string
  user_id: string
  title: string
  note: string | null
  type: 'daily' | 'weekly' | 'monthly'
  priority: 'low' | 'medium' | 'high'
  status: 'active' | 'completed'
  period_date: string
  completed_at: string | null
  created_at: string
  updated_at: string
}

export interface ChallengeRow {
  id: string
  user_id: string
  title: string
  emoji: string
  description: string | null
  duration_days: number
  start_date: string
  requires_photo: boolean
  sadqa_amount: number | null
  sadqa_currency: string
  current_streak: number
  longest_streak: number
  status: 'active' | 'completed' | 'failed'
  created_at: string
}

export interface ChallengeCheckinRow {
  id: string
  challenge_id: string
  date: string
  completed: boolean
  photo_url: string | null
  sadqa_paid: boolean
  sadqa_receipt_url: string | null
  created_at: string
}

export interface GoalRow {
  id: string
  user_id: string
  title: string
  emoji: string
  description: string | null
  deadline: string | null
  progress_pct: number
  ai_plan: string | null
  linked_habit_ids: string[]
  created_at: string
  updated_at: string
}

export interface GoalMilestoneRow {
  id: string
  goal_id: string
  title: string
  done: boolean
  target_date: string | null
  created_at: string
}

// prayer columns are INTEGER after prayer_jamat.sql:
//   0 = missed, 1 = prayed alone, 2 = prayed in jamat
export interface PrayerLogRow {
  id: string
  user_id: string
  date: string
  fajr: number      // 0 | 1 | 2
  dhuhr: number
  asr: number
  maghrib: number
  isha: number
  extra_prayers: Json  // [{ name: string, status: 0|1|2 }]
  created_at: string
}

// health.sql columns
export interface HealthLogRow {
  id: string
  user_id: string
  date: string
  water_glasses: number
  steps: number | null
  sleep_hours: number | null
  sleep_time: string | null    // TIME e.g. "22:30:00"
  wake_time: string | null
  exercise_done: boolean
  exercise_minutes: number | null
  weight_kg: number | null
  mood: number | null          // 1–10
  notes: string | null
  extras: Json
  created_at: string
  updated_at: string
}

export interface QuranLogRow {
  id: string
  user_id: string
  date: string
  pages_read: number
  surah: string | null
  ayah_from: number | null
  ayah_to: number | null
  notes: string | null
  created_at: string
}

export interface DailyCheckinRow {
  id: string
  user_id: string
  date: string
  tasks: Json          // [{ text: string, done: boolean }]
  evening_text: string | null
  ai_verdict: string | null
  created_at: string
  updated_at: string
}

export interface DreamRow {
  id: string
  user_id: string
  statement: string
  dream_date: string
  image_url: string | null
  why: string | null
  total_hours_required: number
  public_board_visible: boolean
  created_at: string
  updated_at: string
}

export interface AiReportRow {
  id: string
  user_id: string
  type: 'tribunal' | 'pull' | 'gap' | 'letter_reply'
  week_start: string | null
  content_md: string
  generated_at: string
  model_used: string
}

export interface AiConversationRow {
  id: string
  user_id: string
  messages: Json
  created_at: string
}

export interface FutureSelfLetterRow {
  id: string
  user_id: string
  content: string
  written_at: string
  target_deliver_date: string
  delivered_at: string | null
  ai_reply_text: string | null
}
