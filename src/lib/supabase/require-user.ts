import { redirect } from 'next/navigation'
import type { SupabaseClient } from '@supabase/supabase-js'

// Wraps supabase.auth.getUser() so a network/transient failure redirects to /auth
// with an error flag instead of bubbling up as a 500 white-screen.
//
// Returns the authenticated user (never null — redirects on absence/failure).
export async function requireUser(supabase: SupabaseClient): Promise<{ id: string; email?: string | null; user_metadata?: any; created_at?: string }> {
  try {
    const { data, error } = await supabase.auth.getUser()
    if (error || !data?.user) redirect('/auth')
    return data.user as any
  } catch (err: any) {
    // Network errors (Supabase auth server unreachable, fetch timeout, etc.)
    // redirect() throws a special signal — re-throw so Next handles it.
    if (err?.digest?.startsWith('NEXT_REDIRECT')) throw err
    console.warn('[requireUser] auth failed:', err?.message || err)
    redirect('/auth?error=network')
  }
}
