import { createClient as createSupabaseClient, type SupabaseClient } from '@supabase/supabase-js'

/**
 * Supabase client for scheduled work — SAFE-02.
 *
 * Background jobs have no user. The normal server client authenticates through
 * the session cookie, so in a cron invocation `auth.uid()` is NULL, every RLS
 * policy of the form `auth.uid() = user_id` evaluates false, and every query
 * returns zero rows. The job then completes successfully having done nothing,
 * which is why the scheduled reports appeared to run for months without ever
 * sending anything.
 *
 * This client uses the service role key and therefore **bypasses RLS entirely**.
 *
 * That trade is the whole point and the whole danger: the database will no
 * longer scope rows to an owner, so every query made with this client must
 * filter by user_id itself. A query that was merely returning nothing under RLS
 * becomes a query returning *everybody's* rows here. Anything reading a child
 * table — challenge_checkins, goal_milestones — must constrain by the parent
 * ids belonging to the user being processed, because those tables carry no
 * user_id of their own.
 *
 * Never use this for a request made on behalf of a signed-in user. Those go
 * through lib/supabase/server, where RLS is the safety net.
 */
export function createServiceClient(): SupabaseClient | null {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY
  if (!url || !key) return null

  return createSupabaseClient(url, key, {
    auth: { persistSession: false, autoRefreshToken: false },
  })
}

/** True when scheduled jobs can actually reach the database. */
export function hasServiceRole(): boolean {
  return !!process.env.SUPABASE_SERVICE_ROLE_KEY && !!process.env.NEXT_PUBLIC_SUPABASE_URL
}
