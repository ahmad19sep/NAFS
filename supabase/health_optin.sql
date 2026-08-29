-- Health metrics become opt-in (like Goals/Challenges): the Health tab
-- starts clean and users add only the metrics they want to track.
-- Built-ins (water, steps, exercise, weight) live in the "+ Add metric" picker.
-- Run in Supabase SQL Editor. Safe to re-run.

ALTER TABLE public.users
  ALTER COLUMN health_defaults_hidden SET DEFAULT ARRAY['water','steps','exercise','weight'];

-- Reset everyone to the clean slate (metrics are re-addable in two taps;
-- past logged data is untouched and still shows in History).
UPDATE public.users
SET health_defaults_hidden = ARRAY['water','steps','exercise','weight'];
