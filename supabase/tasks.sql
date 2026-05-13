-- Tasks system (Daily / Weekly / Monthly)
-- Each task is anchored to a period_date:
--   daily   → the day it's for
--   weekly  → ISO Monday of that week
--   monthly → 1st of that month
-- Status is only 'active' or 'completed'. "Missed" is derived
-- (active + period_date is already past).

CREATE TABLE IF NOT EXISTS public.tasks (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  note TEXT,
  type TEXT NOT NULL DEFAULT 'daily',
  priority TEXT NOT NULL DEFAULT 'medium',
  status TEXT NOT NULL DEFAULT 'active',
  period_date DATE NOT NULL,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT tasks_type_check     CHECK (type     IN ('daily', 'weekly', 'monthly')),
  CONSTRAINT tasks_priority_check CHECK (priority IN ('low', 'medium', 'high')),
  CONSTRAINT tasks_status_check   CHECK (status   IN ('active', 'completed'))
);

ALTER TABLE public.tasks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "own" ON public.tasks;
CREATE POLICY "own" ON public.tasks FOR ALL USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_tasks_user_period
  ON public.tasks (user_id, type, period_date DESC);
CREATE INDEX IF NOT EXISTS idx_tasks_user_status
  ON public.tasks (user_id, status, period_date DESC);
