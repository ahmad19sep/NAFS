-- Challenges v2: frequency (daily/weekly/monthly/yearly), reason required,
-- auto-restart on skip (no more dead "failed" status).

ALTER TABLE public.challenges
  ADD COLUMN IF NOT EXISTS frequency TEXT NOT NULL DEFAULT 'daily',
  ADD COLUMN IF NOT EXISTS restart_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_restart_at TIMESTAMPTZ;

ALTER TABLE public.challenges DROP CONSTRAINT IF EXISTS challenges_frequency_check;
ALTER TABLE public.challenges
  ADD CONSTRAINT challenges_frequency_check
  CHECK (frequency IN ('daily', 'weekly', 'monthly', 'yearly'));

-- Re-enable previously failed challenges (the new model auto-restarts instead)
UPDATE public.challenges SET status = 'active' WHERE status = 'failed';
