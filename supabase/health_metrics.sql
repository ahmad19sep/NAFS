-- Health: usual sleep schedule on the user (one-time setup)
--         + custom-metric config (which extras the user wants to track daily)
--         + per-day extras values on health_logs

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS usual_sleep_time TIME,
  ADD COLUMN IF NOT EXISTS usual_wake_time  TIME,
  ADD COLUMN IF NOT EXISTS health_extras_config JSONB NOT NULL DEFAULT '[]';

ALTER TABLE public.health_logs
  ADD COLUMN IF NOT EXISTS extras JSONB NOT NULL DEFAULT '{}';

-- Backfill: if old per-day sleep_time exists on health_logs, copy to users
-- (best-effort; not destructive)
UPDATE public.users u
SET usual_sleep_time = COALESCE(u.usual_sleep_time, hl.sleep_time),
    usual_wake_time  = COALESCE(u.usual_wake_time,  hl.wake_time)
FROM (
  SELECT DISTINCT ON (user_id) user_id, sleep_time, wake_time
  FROM public.health_logs
  WHERE sleep_time IS NOT NULL
  ORDER BY user_id, date DESC
) hl
WHERE hl.user_id = u.id
  AND (u.usual_sleep_time IS NULL OR u.usual_wake_time IS NULL);
