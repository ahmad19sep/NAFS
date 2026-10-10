-- Apply before deploying the bearer-authenticated future-self reply endpoint.
-- No existing columns, originals, dates, or replies are changed by this migration.
BEGIN;

CREATE OR REPLACE FUNCTION public.complete_future_self_reply(
  p_letter_id UUID, p_content TEXT, p_written_at TIMESTAMPTZ,
  p_target_date DATE, p_reply_text TEXT DEFAULT NULL
)
RETURNS SETOF public.future_self_letters
LANGUAGE plpgsql SECURITY INVOKER SET search_path = pg_catalog, public AS $$
DECLARE
  account_today DATE;
BEGIN
  SELECT timezone(COALESCE(u.timezone, 'Asia/Karachi'), clock_timestamp())::date
    INTO account_today FROM public.users u WHERE u.id = auth.uid();
  IF account_today IS NULL OR p_target_date > account_today THEN RETURN; END IF;
  IF p_reply_text IS NULL THEN
    RETURN QUERY SELECT l.* FROM public.future_self_letters l
      WHERE l.user_id = auth.uid() AND l.id = p_letter_id
        AND l.content = p_content AND l.written_at = p_written_at
        AND l.target_deliver_date = p_target_date
        AND l.ai_reply_text IS NULL AND l.delivered_at IS NULL;
  ELSE
    IF length(btrim(p_reply_text)) = 0 OR length(p_reply_text) > 30000 THEN
      RAISE EXCEPTION 'Invalid reply' USING ERRCODE = '22023';
    END IF;
    RETURN QUERY UPDATE public.future_self_letters l
      SET ai_reply_text = p_reply_text, delivered_at = clock_timestamp()
      WHERE l.user_id = auth.uid() AND l.id = p_letter_id
        AND l.content = p_content AND l.written_at = p_written_at
        AND l.target_deliver_date = p_target_date
        AND l.ai_reply_text IS NULL AND l.delivered_at IS NULL
      RETURNING l.*;
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.complete_future_self_reply(UUID, TEXT, TIMESTAMPTZ, DATE, TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.complete_future_self_reply(UUID, TEXT, TIMESTAMPTZ, DATE, TEXT) TO authenticated;
COMMIT;
