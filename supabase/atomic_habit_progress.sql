-- LOG-01: make subject-habit progress safe under concurrent writes.
--
-- The route used to do this in three steps:
--
--   1. read habit_logs.value for the day        (the old delta)
--   2. read habits.subject_position             (the running total)
--   3. write subject_position + (new - old)
--
-- Between 1 and 3 another request can do the same thing. Both read the same
-- position, both write their own result, and one person's reading progress is
-- silently gone. Two devices, or one device retrying on a flaky connection, is
-- enough — and because the number that survives still looks plausible, nothing
-- ever surfaces the loss.
--
-- This does the whole thing in one statement under a row lock, so concurrent
-- calls queue instead of racing, and returns the position it actually settled
-- on so the client shows the true total rather than its own guess.
--
-- Idempotent by construction: the day's entry stores the ABSOLUTE delta for
-- that date, and the position moves by (new delta - stored delta). Replaying
-- the same request applies a zero adjustment, so a retry after a lost response
-- cannot double-count.
--
-- Run in Supabase SQL Editor. Safe to re-run.

CREATE OR REPLACE FUNCTION public.log_subject_habit(
  p_habit_id      UUID,
  p_date          DATE,
  p_new_delta     INTEGER,
  p_duration_mins INTEGER DEFAULT 0,
  p_notes         TEXT    DEFAULT NULL
)
RETURNS TABLE (subject_position INTEGER, applied_delta INTEGER)
LANGUAGE plpgsql
SECURITY INVOKER          -- runs as the caller, so RLS still applies
SET search_path = public
AS $$
DECLARE
  v_user_id      UUID := auth.uid();
  v_old_delta    INTEGER;
  v_total        INTEGER;
  v_position     INTEGER;
  v_new_delta    INTEGER := GREATEST(0, COALESCE(p_new_delta, 0));
  v_adjustment   INTEGER;
  v_new_position INTEGER;
BEGIN
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  -- Lock the habit row first. Everything below reads and writes under it, so a
  -- second caller waits here rather than reading a position about to change.
  SELECT COALESCE(h.subject_total, 2147483647), COALESCE(h.subject_position, 0)
    INTO v_total, v_position
  FROM public.habits h
  WHERE h.id = p_habit_id AND h.user_id = v_user_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'habit not found';
  END IF;

  SELECT COALESCE(l.value, 0) INTO v_old_delta
  FROM public.habit_logs l
  WHERE l.user_id = v_user_id AND l.habit_id = p_habit_id AND l.date = p_date;

  v_old_delta  := COALESCE(v_old_delta, 0);
  v_adjustment := v_new_delta - v_old_delta;

  -- Clamp to the book's length, and never below zero.
  v_new_position := GREATEST(0, LEAST(v_total, v_position + v_adjustment));

  UPDATE public.habits
  SET subject_position = v_new_position
  WHERE id = p_habit_id AND user_id = v_user_id;

  INSERT INTO public.habit_logs (user_id, habit_id, date, completed, value, duration_mins, notes)
  VALUES (v_user_id, p_habit_id, p_date, v_new_delta > 0, v_new_delta,
          COALESCE(p_duration_mins, 0), p_notes)
  ON CONFLICT (user_id, habit_id, date) DO UPDATE
  SET completed     = EXCLUDED.completed,
      value         = EXCLUDED.value,
      duration_mins = EXCLUDED.duration_mins,
      notes         = EXCLUDED.notes;

  subject_position := v_new_position;
  applied_delta    := v_new_position - v_position;
  RETURN NEXT;
END;
$$;

-- habit_logs needs the unique key the upsert above conflicts on. Present in
-- most installs already; added here so this migration stands alone.
CREATE UNIQUE INDEX IF NOT EXISTS habit_logs_user_habit_date_key
  ON public.habit_logs (user_id, habit_id, date);
