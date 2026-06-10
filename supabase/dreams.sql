-- ============================================================
-- Dreams & Mapping — makes the /dreams feature functional.
-- (The page previously queried tables that were never created.)
-- Run in Supabase SQL Editor. Safe to re-run.
-- ============================================================

-- One dream per user
CREATE TABLE IF NOT EXISTS public.dreams (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL UNIQUE REFERENCES public.users(id) ON DELETE CASCADE,
  statement TEXT NOT NULL,
  image_url TEXT,
  dream_date DATE NOT NULL,
  total_hours_required NUMERIC NOT NULL DEFAULT 1000,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Daily focused-hours log feeding the trajectory math
CREATE TABLE IF NOT EXISTS public.daily_logs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  weighted_hours_today NUMERIC NOT NULL DEFAULT 0,
  todays_pull_days NUMERIC NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, date)
);

ALTER TABLE public.dreams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_logs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "own" ON public.dreams;
DROP POLICY IF EXISTS "own" ON public.daily_logs;
CREATE POLICY "own" ON public.dreams FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "own" ON public.daily_logs FOR ALL USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_daily_logs_user_date ON public.daily_logs (user_id, date DESC);
