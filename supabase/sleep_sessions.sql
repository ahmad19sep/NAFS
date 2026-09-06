-- Multiple sleep/wake periods per day (night sleep + naps), instead of a single
-- bedtime/wake pair. Each entry is { "id": "...", "start": "HH:MM", "end": "HH:MM" };
-- an entry whose end is at or before its start crossed midnight.
-- health_logs.sleep_hours stays the computed total so reports/history are unchanged.
-- Run in Supabase SQL Editor. Safe to re-run.

ALTER TABLE public.health_logs
  ADD COLUMN IF NOT EXISTS sleep_sessions JSONB NOT NULL DEFAULT '[]';

-- Backfill: fold the existing single sleep_time/wake_time pair into one session.
UPDATE public.health_logs
SET sleep_sessions = jsonb_build_array(
  jsonb_build_object(
    'id', gen_random_uuid()::text,
    'start', to_char(sleep_time, 'HH24:MI'),
    'end', to_char(wake_time, 'HH24:MI')
  )
)
WHERE sleep_sessions = '[]'::jsonb
  AND sleep_time IS NOT NULL
  AND wake_time IS NOT NULL;
