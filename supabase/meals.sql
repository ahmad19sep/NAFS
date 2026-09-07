-- Meals eaten per day. Breakfast, lunch and dinner always exist; extra meals
-- (snack, sehri, iftar, tea…) can be added on top. Each meal holds the foods
-- picked from the menu, so "how many times I ate" is just the number of meals
-- with at least one item.
--
--   [{ "id": "...", "key": "breakfast", "label": "Breakfast", "emoji": "🌅",
--      "items": [{ "id": "...", "name": "Naan", "emoji": "🫓" }] }]
--
-- Run in Supabase SQL Editor. Safe to re-run.

ALTER TABLE public.health_logs
  ADD COLUMN IF NOT EXISTS meals JSONB NOT NULL DEFAULT '[]';

-- Sleep and meals are permanent parts of the Health page, so make sure neither
-- is sitting in anyone's hidden list from an earlier version.
--
-- health_defaults_hidden comes from health_hidden.sql, which not every install
-- has run. Guarded so this migration works with or without it — the column is
-- optional and the app already treats a missing one as "nothing hidden".
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'users'
      AND column_name = 'health_defaults_hidden'
  ) THEN
    UPDATE public.users
    SET health_defaults_hidden =
      array_remove(array_remove(health_defaults_hidden, 'sleep'), 'meals')
    WHERE health_defaults_hidden && ARRAY['sleep', 'meals'];
  END IF;
END $$;
