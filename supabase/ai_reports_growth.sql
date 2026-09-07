-- ai_reports: allow the growth review.
--
-- The type check was written when there were four report kinds. This drops
-- the existing check on `type` and recreates it with 'growth' added.
--
-- Postgres stores `type IN ('a', 'b')` as `type = ANY (ARRAY['a', 'b'])`, so
-- the constraint is found by its usual name first, and then — in case the
-- table was created under a different name — by a definition match that
-- covers both spellings.
--
-- Run once in the Supabase SQL editor. Safe to re-run.

ALTER TABLE public.ai_reports DROP CONSTRAINT IF EXISTS ai_reports_type_check;

DO $$
DECLARE c record;
BEGIN
  FOR c IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'public.ai_reports'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ~* '\(type\s*(=\s*ANY|IN)\s*\('
  LOOP
    EXECUTE format('ALTER TABLE public.ai_reports DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;

ALTER TABLE public.ai_reports ADD CONSTRAINT ai_reports_type_check
  CHECK (type IN ('tribunal', 'pull', 'gap', 'letter_reply', 'growth'));

-- The old default named a provider that no longer exists; rows now say
-- which model actually answered. Older inserts that omit it still work.
ALTER TABLE public.ai_reports ALTER COLUMN model_used SET DEFAULT 'unknown';

-- Verify: should list exactly one row whose definition includes 'growth'.
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'public.ai_reports'::regclass AND contype = 'c';

-- Rollback: delete rows where type = 'growth', then recreate the constraint
-- without it.
