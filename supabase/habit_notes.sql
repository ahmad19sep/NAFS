-- Habit tracking templates and daily notes
ALTER TABLE public.habits
  ADD COLUMN IF NOT EXISTS note_template TEXT;  -- e.g. "Which book?", "What did you work on?"

ALTER TABLE public.habit_logs
  ADD COLUMN IF NOT EXISTS notes TEXT;  -- daily answer e.g. "Atomic Habits — Ch.4"
