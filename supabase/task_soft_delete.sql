-- LOG-01: make deleting a task reversible.
--
-- Deleting was permanent, guarded only by a browser confirm(). A mis-tap lost
-- the task and everything it recorded about that day.
--
-- Why this is done in RLS rather than in the queries: thirteen places read
-- tasks — the dashboard, history, profile, reports, the tasks page, the coach's
-- context, goal alignment, the daily and weekly report crons, task reminders.
-- Adding "and not deleted" to each is a filter someone will forget, and the
-- failure is silent: a deleted task quietly reappears in a report or in what the
-- AI is told. Hiding them in the SELECT policy means no query CAN forget.
--
-- The single FOR ALL policy is therefore split. SELECT hides soft-deleted rows;
-- UPDATE deliberately does not, because restoring one has to be able to find it.
--
-- Run in Supabase SQL Editor. Safe to re-run.
--
-- ── Rollback ────────────────────────────────────────────────────────────────
-- To undo this migration completely:
--
--   UPDATE public.tasks SET deleted_at = NULL;            -- un-hide everything
--   DROP POLICY IF EXISTS "own_select" ON public.tasks;
--   DROP POLICY IF EXISTS "own_insert" ON public.tasks;
--   DROP POLICY IF EXISTS "own_update" ON public.tasks;
--   DROP POLICY IF EXISTS "own_delete" ON public.tasks;
--   CREATE POLICY "own" ON public.tasks FOR ALL USING (auth.uid() = user_id);
--
-- The deleted_at column can stay; nothing breaks if it is unused.

ALTER TABLE public.tasks
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Replace the combined policy with per-command ones.
DROP POLICY IF EXISTS "own" ON public.tasks;

DROP POLICY IF EXISTS "own_select" ON public.tasks;
CREATE POLICY "own_select" ON public.tasks
  FOR SELECT USING (auth.uid() = user_id AND deleted_at IS NULL);

DROP POLICY IF EXISTS "own_insert" ON public.tasks;
CREATE POLICY "own_insert" ON public.tasks
  FOR INSERT WITH CHECK (auth.uid() = user_id);

-- No deleted_at condition here: a restore updates a row the SELECT policy
-- hides, and must still be able to reach it.
DROP POLICY IF EXISTS "own_update" ON public.tasks;
CREATE POLICY "own_update" ON public.tasks
  FOR UPDATE USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "own_delete" ON public.tasks;
CREATE POLICY "own_delete" ON public.tasks
  FOR DELETE USING (auth.uid() = user_id);

-- Keeps the common "my live tasks" read fast now that it carries a condition.
CREATE INDEX IF NOT EXISTS idx_tasks_user_live
  ON public.tasks (user_id, period_date DESC)
  WHERE deleted_at IS NULL;
