-- ai_reports: allow the growth review.
--
-- The type check was written when there were four report kinds. This drops
-- whichever check constrains `type` — found by definition, since the name
-- depends on how the table was created — and recreates it with 'growth'.
--
-- Run once in the Supabase SQL editor. Safe to re-run.

DO $$
DECLARE c record;
BEGIN
  FOR c IN
    SELECT conname FROM pg_constraint
    WHERE conrelid = 'public.ai_reports'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) ILIKE '%type%IN%'
  LOOP
    EXECUTE format('ALTER TABLE public.ai_reports DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;

ALTER TABLE public.ai_reports ADD CONSTRAINT ai_reports_type_check
  CHECK (type IN ('tribunal', 'pull', 'gap', 'letter_reply', 'growth'));

-- The old default named a provider that no longer exists; rows now say
-- which model actually answered. Kept nullable-by-default so older inserts
-- that omit it still work.
ALTER TABLE public.ai_reports ALTER COLUMN model_used SET DEFAULT 'unknown';

-- Rollback: delete rows where type = 'growth', then recreate the constraint
-- without it.
