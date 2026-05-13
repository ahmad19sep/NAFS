-- Lets users hide built-in health metrics they don't want to track
-- (e.g. someone who doesn't care about steps).

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS health_defaults_hidden TEXT[] NOT NULL DEFAULT '{}';
