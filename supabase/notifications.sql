-- Time-based alerts for daily tasks + user notification preferences.

ALTER TABLE public.tasks
  ADD COLUMN IF NOT EXISTS due_time TIME,
  ADD COLUMN IF NOT EXISTS alerts_sent TEXT[] NOT NULL DEFAULT '{}';

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS notifications_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS notify_task_deadlines  BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS notify_habit_reminders BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_tasks_due_today
  ON public.tasks (period_date, status)
  WHERE due_time IS NOT NULL;
