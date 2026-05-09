-- NAFS schema additions — run AFTER schema.sql
-- Run in Supabase SQL editor

-- Add score_weight and time_target_mins to habits
ALTER TABLE public.habits
  ADD COLUMN IF NOT EXISTS score_weight INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS time_target_mins INTEGER NOT NULL DEFAULT 0;

-- Add duration_mins to habit_logs
ALTER TABLE public.habit_logs
  ADD COLUMN IF NOT EXISTS duration_mins INTEGER NOT NULL DEFAULT 0;

-- Prayer logs table (separate from habits for clean scoring)
CREATE TABLE IF NOT EXISTS public.prayer_logs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  fajr BOOLEAN NOT NULL DEFAULT FALSE,
  dhuhr BOOLEAN NOT NULL DEFAULT FALSE,
  asr BOOLEAN NOT NULL DEFAULT FALSE,
  maghrib BOOLEAN NOT NULL DEFAULT FALSE,
  isha BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, date)
);

ALTER TABLE public.prayer_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "own" ON public.prayer_logs;
CREATE POLICY "own" ON public.prayer_logs FOR ALL USING (auth.uid() = user_id);
CREATE INDEX IF NOT EXISTS idx_prayer_logs_user_date ON public.prayer_logs (user_id, date DESC);

-- Screen time logs
CREATE TABLE IF NOT EXISTS public.screentime_logs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  total_mins INTEGER NOT NULL DEFAULT 0,
  apps JSONB NOT NULL DEFAULT '[]',
  screenshot_url TEXT,
  ai_summary TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, date)
);

ALTER TABLE public.screentime_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "own" ON public.screentime_logs;
CREATE POLICY "own" ON public.screentime_logs FOR ALL USING (auth.uid() = user_id);
CREATE INDEX IF NOT EXISTS idx_screentime_user_date ON public.screentime_logs (user_id, date DESC);

-- Add emoji and description columns to challenges (if missing)
ALTER TABLE public.challenges
  ADD COLUMN IF NOT EXISTS emoji TEXT NOT NULL DEFAULT '🎯',
  ADD COLUMN IF NOT EXISTS description TEXT,
  ADD COLUMN IF NOT EXISTS requires_photo BOOLEAN NOT NULL DEFAULT FALSE;

-- Add emoji to goals
ALTER TABLE public.goals
  ADD COLUMN IF NOT EXISTS emoji TEXT NOT NULL DEFAULT '⭐';

-- Storage buckets to create in Supabase Storage UI:
-- screentime-shots  (public)
-- challenge-photos  (public)
