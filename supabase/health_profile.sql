-- Health profile fields: height + current weight on the user row,
-- and explicit sleep/wake times on each daily health log.

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS height_cm NUMERIC(5,1),
  ADD COLUMN IF NOT EXISTS weight_kg NUMERIC(5,2);

ALTER TABLE public.health_logs
  ADD COLUMN IF NOT EXISTS sleep_time TIME,
  ADD COLUMN IF NOT EXISTS wake_time  TIME;
