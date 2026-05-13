-- v3 Habits — full spec migration
-- Adds: 4 types (simple/counter/duration/subject), schedule (daily/weekdays/per-week),
-- reminder time, why, paused state, and subject-tracking fields.

-- ============================================================
-- 1. New columns on habits
-- ============================================================
ALTER TABLE public.habits
  ADD COLUMN IF NOT EXISTS subject_name TEXT,
  ADD COLUMN IF NOT EXISTS subject_total INTEGER,
  ADD COLUMN IF NOT EXISTS subject_position INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS subject_unit TEXT,
  ADD COLUMN IF NOT EXISTS schedule_kind TEXT NOT NULL DEFAULT 'daily',
  ADD COLUMN IF NOT EXISTS schedule_days TEXT[],
  ADD COLUMN IF NOT EXISTS weekly_target INTEGER,
  ADD COLUMN IF NOT EXISTS reminder_time TIME,
  ADD COLUMN IF NOT EXISTS why TEXT,
  ADD COLUMN IF NOT EXISTS is_paused BOOLEAN NOT NULL DEFAULT FALSE;

-- Schedule kind constraint
ALTER TABLE public.habits DROP CONSTRAINT IF EXISTS habits_schedule_kind_check;
ALTER TABLE public.habits
  ADD CONSTRAINT habits_schedule_kind_check
  CHECK (schedule_kind IN ('daily', 'weekdays', 'per_week'));

-- ============================================================
-- 2. Migrate legacy type values to v3 names
-- ============================================================
UPDATE public.habits SET type = 'simple'   WHERE type = 'boolean';
UPDATE public.habits SET type = 'counter'  WHERE type = 'count';

-- Now lock type constraint to v3 names only
ALTER TABLE public.habits DROP CONSTRAINT IF EXISTS habits_type_check;
ALTER TABLE public.habits
  ADD CONSTRAINT habits_type_check
  CHECK (type IN ('simple', 'counter', 'duration', 'subject'));

-- ============================================================
-- 3. Indexes
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_habits_user_active
  ON public.habits (user_id, is_active, is_paused);
