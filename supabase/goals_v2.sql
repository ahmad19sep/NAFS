-- Goals v2: time-horizon types (weekly/monthly/quarterly/yearly/long_term),
-- category emoji, optional numeric targets, milestone completion timestamps.

ALTER TABLE public.goals
  ADD COLUMN IF NOT EXISTS goal_type TEXT NOT NULL DEFAULT 'monthly',
  ADD COLUMN IF NOT EXISTS category TEXT,
  ADD COLUMN IF NOT EXISTS start_date DATE NOT NULL DEFAULT CURRENT_DATE,
  ADD COLUMN IF NOT EXISTS target_value NUMERIC,
  ADD COLUMN IF NOT EXISTS current_value NUMERIC,
  ADD COLUMN IF NOT EXISTS unit TEXT;

ALTER TABLE public.goals DROP CONSTRAINT IF EXISTS goals_type_check;
ALTER TABLE public.goals
  ADD CONSTRAINT goals_type_check
  CHECK (goal_type IN ('weekly', 'monthly', 'quarterly', 'yearly', 'long_term'));

ALTER TABLE public.goal_milestones
  ADD COLUMN IF NOT EXISTS done_at TIMESTAMPTZ;

-- Backfill done_at for milestones that are already marked done
UPDATE public.goal_milestones
SET done_at = COALESCE(done_at, created_at)
WHERE done = TRUE AND done_at IS NULL;
