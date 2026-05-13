-- Daily health tracking
CREATE TABLE IF NOT EXISTS public.health_logs (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  water_glasses INTEGER NOT NULL DEFAULT 0,
  steps INTEGER,
  sleep_hours NUMERIC(3,1),
  exercise_done BOOLEAN NOT NULL DEFAULT FALSE,
  exercise_minutes INTEGER,
  weight_kg NUMERIC(5,2),
  mood INTEGER CHECK (mood BETWEEN 1 AND 10),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, date)
);

ALTER TABLE public.health_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "own" ON public.health_logs;
CREATE POLICY "own" ON public.health_logs FOR ALL USING (auth.uid() = user_id);
CREATE INDEX IF NOT EXISTS idx_health_user_date ON public.health_logs (user_id, date DESC);
