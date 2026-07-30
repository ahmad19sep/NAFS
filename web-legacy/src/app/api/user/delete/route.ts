import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { createServerClient } from '@supabase/ssr'

/**
 * Permanently delete the signed-in user.
 *
 * 1. Always: removes their public.users row (which CASCADEs to habits, tasks,
 *    goals, challenges, prayer_logs, etc — all their data).
 * 2. If SUPABASE_SERVICE_ROLE_KEY is set in env: also removes their auth.users
 *    row, so they can't log back in. Otherwise the auth user remains (they could
 *    sign in again, but they'd start fresh with no data).
 * 3. Signs them out.
 */
export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json().catch(() => ({}))
  if (body?.confirm !== 'DELETE') {
    return NextResponse.json({ error: 'Type DELETE to confirm' }, { status: 400 })
  }

  // Step 1: delete the public.users row (cascades to all child tables)
  const { error: rowErr } = await supabase.from('users').delete().eq('id', user.id)
  if (rowErr) {
    console.error('[delete-account] row delete failed:', rowErr.message)
    return NextResponse.json({ error: rowErr.message }, { status: 500 })
  }

  // Step 2: if service role key is configured, also delete the auth user
  const SERVICE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY
  const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL
  let authDeleted = false
  if (SERVICE_KEY && SUPABASE_URL) {
    try {
      const admin = createServerClient(SUPABASE_URL, SERVICE_KEY, {
        cookies: { getAll: () => [], setAll: () => {} },
      })
      const { error: authErr } = await admin.auth.admin.deleteUser(user.id)
      if (authErr) console.warn('[delete-account] auth delete failed:', authErr.message)
      else authDeleted = true
    } catch (err: any) {
      console.warn('[delete-account] auth delete threw:', err?.message)
    }
  }

  // Step 3: sign out
  await supabase.auth.signOut()

  return NextResponse.json({ ok: true, authDeleted })
}
