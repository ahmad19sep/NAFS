-- LOG-01: durable record of user intents, so a retry is recognised rather than
-- executed twice.
--
-- The problem it solves: a client sends "create this task", the connection
-- drops before the response arrives, the client retries — and now there are two
-- identical tasks. A disabled button hides this most of the time, but it cannot
-- survive a lost response, a background retry, or a second device.
--
-- The contract:
--   * The client gives each user INTENT a stable id. A retry of that intent
--     reuses the id; a new deliberate action gets a fresh one.
--   * (user_id, request_id) is unique, so only the first attempt can claim it.
--   * A claimed row is filled in with the result once the work succeeds.
--     A replay then returns that stored result instead of re-running.
--   * The same id arriving with a DIFFERENT payload is a conflict, not a
--     second write — it means the client reused an id it should not have.
--
-- Rows are disposable: they exist to absorb retries over minutes, not to be an
-- audit log. See the cleanup note at the bottom.
--
-- Run in Supabase SQL Editor. Safe to re-run.

CREATE TABLE IF NOT EXISTS public.mutations (
  user_id      UUID        NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  request_id   TEXT        NOT NULL,
  -- Hash rather than the payload itself: enough to detect a reused id, without
  -- keeping a second copy of the user's content.
  payload_hash TEXT        NOT NULL,
  -- NULL while the work is in flight; set once it succeeded.
  result       JSONB,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ,
  PRIMARY KEY (user_id, request_id)
);

ALTER TABLE public.mutations ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "own" ON public.mutations;
CREATE POLICY "own" ON public.mutations FOR ALL USING (auth.uid() = user_id);

-- Supports the cleanup below without scanning the table.
CREATE INDEX IF NOT EXISTS idx_mutations_created ON public.mutations (created_at);

-- Housekeeping. A retry that arrives a day later is not a retry, it is a new
-- intent, so nothing here needs keeping long. Run periodically, or schedule with
-- pg_cron if it is enabled:
--
--   DELETE FROM public.mutations WHERE created_at < NOW() - INTERVAL '7 days';
