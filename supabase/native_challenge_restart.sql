-- Preserve old check-ins while atomically starting a new attempt. Safe to rerun.
CREATE TABLE IF NOT EXISTS public.challenge_attempt_history (
  challenge_id uuid NOT NULL REFERENCES public.challenges(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  attempt integer NOT NULL,
  original_id uuid NOT NULL,
  record jsonb NOT NULL,
  archived_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(challenge_id, attempt, original_id)
);
ALTER TABLE public.challenge_attempt_history ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "own challenge attempts" ON public.challenge_attempt_history;
CREATE POLICY "own challenge attempts" ON public.challenge_attempt_history TO authenticated
  USING(user_id=auth.uid()) WITH CHECK(user_id=auth.uid() AND EXISTS (
    SELECT 1 FROM public.challenges c WHERE c.id=challenge_id AND c.user_id=auth.uid()
  ));
GRANT SELECT, INSERT ON public.challenge_attempt_history TO authenticated;
CREATE OR REPLACE FUNCTION public.native_restart_challenge(p_id uuid, p_expected_count integer, p_date date)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path = public, pg_temp AS $$
DECLARE c public.challenges;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'unauthorized' USING ERRCODE='42501'; END IF;
  SELECT * INTO c FROM public.challenges WHERE id=p_id AND user_id=auth.uid() FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'challenge not found'; END IF;
  IF c.restart_count=p_expected_count+1 AND c.start_date=p_date THEN RETURN to_jsonb(c); END IF;
  IF c.restart_count IS DISTINCT FROM p_expected_count THEN RAISE EXCEPTION 'challenge changed; reload' USING ERRCODE='40001'; END IF;
  IF p_date IS NULL OR p_date < c.start_date OR p_date IS DISTINCT FROM
    (SELECT (now() AT TIME ZONE COALESCE(timezone,'UTC'))::date FROM public.users WHERE id=auth.uid())
    THEN RAISE EXCEPTION 'invalid restart date'; END IF;
  -- Preserve any same-day prior attempt before releasing the unique day key.
  INSERT INTO public.challenge_attempt_history(challenge_id,user_id,attempt,original_id,record)
    SELECT p_id,auth.uid(),c.restart_count,l.id,to_jsonb(l) FROM public.challenge_checkins l
    WHERE l.challenge_id=p_id AND l.date>=p_date ON CONFLICT DO NOTHING;
  DELETE FROM public.challenge_checkins WHERE challenge_id=p_id AND date>=p_date;
  UPDATE public.challenges SET start_date=p_date, status='active', current_streak=0,
    restart_count=restart_count+1, last_restart_at=clock_timestamp()
    WHERE id=p_id AND user_id=auth.uid() RETURNING * INTO c;
  RETURN to_jsonb(c);
END $$;
REVOKE ALL ON FUNCTION public.native_restart_challenge(uuid,integer,date) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.native_restart_challenge(uuid,integer,date) TO authenticated;
