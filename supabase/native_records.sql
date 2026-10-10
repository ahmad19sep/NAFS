-- Additive native records contract. Apply after dreams.sql/schema_additions.sql/app_notes.sql.
-- Does not replace or delete existing user records. Safe to rerun.
BEGIN;
CREATE TABLE IF NOT EXISTS public.quran_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  date date NOT NULL,
  pages_read numeric NOT NULL DEFAULT 0 CHECK (pages_read BETWEEN 0 AND 604),
  surah text, ayah_from integer, ayah_to integer, notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id, date)
);
ALTER TABLE public.quran_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "quran own records" ON public.quran_log;
CREATE POLICY "quran own records" ON public.quran_log TO authenticated
  USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
GRANT SELECT, INSERT, UPDATE, DELETE ON public.quran_log TO authenticated;
ALTER TABLE public.dreams ADD COLUMN IF NOT EXISTS why text;
ALTER TABLE public.daily_logs
  ADD COLUMN IF NOT EXISTS dream_work_hours numeric NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS study_hours numeric NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS screen_time_mins integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS mood integer,
  ADD COLUMN IF NOT EXISTS reflection_text text,
  ADD COLUMN IF NOT EXISTS sleep_start text,
  ADD COLUMN IF NOT EXISTS sleep_end text,
  ADD COLUMN IF NOT EXISTS photo_urls text[] NOT NULL DEFAULT '{}',
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

-- Full loaded row is the compare-and-set version. Only these product fields may be written.
CREATE OR REPLACE FUNCTION public.native_save_record(
  p_table text, p_id uuid, p_date date, p_patch jsonb, p_expected jsonb
) RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path = public, pg_temp AS $$
DECLARE
  uid uuid := auth.uid(); allowed text[]; row_key text; current_row jsonb;
  merged jsonb; col_list text; value_list text; set_list text; result jsonb;
BEGIN
  IF uid IS NULL THEN RAISE EXCEPTION 'unauthorized' USING ERRCODE = '42501'; END IF;
  CASE p_table
    WHEN 'dreams' THEN allowed := ARRAY['statement','why','dream_date','total_hours_required']; row_key := 'user_id = $1';
    WHEN 'daily_logs' THEN allowed := ARRAY['dream_work_hours','study_hours','screen_time_mins','mood','reflection_text','sleep_start','sleep_end','photo_urls','weighted_hours_today','todays_pull_days']; row_key := 'user_id = $1 AND date = $2';
    WHEN 'quran_log' THEN allowed := ARRAY['pages_read','surah','ayah_from','ayah_to','notes']; row_key := 'user_id = $1 AND date = $2';
    WHEN 'screentime_logs' THEN allowed := ARRAY['total_mins','apps','screenshot_url','ai_summary']; row_key := 'user_id = $1 AND date = $2';
    WHEN 'app_notes' THEN allowed := ARRAY['kind','title','detail','where_seen','status']; row_key := 'user_id = $1 AND id = $3';
    ELSE RAISE EXCEPTION 'unsupported record' USING ERRCODE = '22023';
  END CASE;
  IF p_id IS NULL OR p_patch IS NULL OR jsonb_typeof(p_patch) <> 'object' OR p_patch = '{}'::jsonb OR length(p_patch::text)>50000 OR
     EXISTS (SELECT 1 FROM jsonb_object_keys(p_patch) k WHERE NOT k = ANY(allowed)) THEN
    RAISE EXCEPTION 'invalid fields' USING ERRCODE = '22023';
  END IF;
  IF p_table NOT IN ('dreams','app_notes') AND p_date IS NULL THEN RAISE EXCEPTION 'date required'; END IF;
  -- Also serializes first inserts, when there is no row to lock yet.
  PERFORM pg_advisory_xact_lock(hashtextextended(uid::text || p_table || CASE WHEN p_table='app_notes' THEN p_id::text WHEN p_table='dreams' THEN '' ELSE p_date::text END, 0));
  EXECUTE format('SELECT to_jsonb(t) FROM public.%I t WHERE %s FOR UPDATE', p_table, row_key)
    INTO current_row USING uid, p_date, p_id;
  IF current_row IS NOT NULL AND current_row @> p_patch THEN RETURN current_row; END IF;
  IF current_row IS DISTINCT FROM p_expected THEN RAISE EXCEPTION 'record changed; reload before saving' USING ERRCODE = '40001'; END IF;
  merged := p_patch || jsonb_build_object('user_id', uid, 'id', COALESCE((current_row->>'id')::uuid, p_id));
  IF p_table IN ('dreams','daily_logs') THEN merged := merged || jsonb_build_object('updated_at', clock_timestamp()); END IF;
  IF p_table NOT IN ('dreams','app_notes') THEN merged := merged || jsonb_build_object('date', p_date); END IF;
  SELECT string_agg(format('%I', k), ',' ORDER BY k),
         string_agg(format('r.%I', k), ',' ORDER BY k),
         string_agg(format('%I = r.%I', k, k), ',' ORDER BY k)
    INTO col_list, value_list, set_list FROM jsonb_object_keys(merged) k;
  IF current_row IS NULL THEN
    EXECUTE format('INSERT INTO public.%I AS t (%s) SELECT %s FROM jsonb_populate_record(NULL::public.%I, $1) r RETURNING to_jsonb(t)', p_table, col_list, value_list, p_table)
      INTO result USING merged;
  ELSE
    EXECUTE format('UPDATE public.%I t SET %s FROM jsonb_populate_record(NULL::public.%I, $4) r WHERE t.%s RETURNING to_jsonb(t)', p_table, set_list, p_table, replace(row_key, ' AND ', ' AND t.'))
      INTO result USING uid, p_date, p_id, merged;
  END IF;
  IF result IS NULL THEN RAISE EXCEPTION 'write unconfirmed'; END IF;
  RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.native_save_record(text,uuid,date,jsonb,jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.native_save_record(text,uuid,date,jsonb,jsonb) TO authenticated;
COMMIT;
