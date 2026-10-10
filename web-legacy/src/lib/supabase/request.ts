import { createClient as createSupabaseClient, type SupabaseClient, type User } from '@supabase/supabase-js'
import { createClient as createCookieClient } from './server'
import { ApiRouteError } from '../api-errors'

/** A supplied but invalid bearer header never falls back to another user's cookie. */
export function bearerToken(header: string | null): string | null {
  if (header === null) return null
  const match = /^Bearer ([A-Za-z0-9._~-]+)$/i.exec(header.trim())
  if (!match || header.length > 8192) throw new ApiRouteError(401, 'unauthorized', 'Sign in again to continue.')
  return match[1]
}

export async function authenticateRequest(request: Pick<Request, 'headers'>): Promise<{
  supabase: SupabaseClient; user: User; mode: 'bearer' | 'cookie'
}> {
  const token = bearerToken(request.headers.get('authorization'))
  let supabase: SupabaseClient
  if (token !== null) {
    const url = process.env.NEXT_PUBLIC_SUPABASE_URL
    const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY
    if (!url || !key) throw new ApiRouteError(503, 'not_configured', 'The backend is not configured.')
    supabase = createSupabaseClient(url, key, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
      global: { headers: { Authorization: `Bearer ${token}` } },
    })
  } else supabase = await createCookieClient()
  try {
    const { data, error } = await supabase.auth.getUser(token ?? undefined)
    if (error && error.status != null && error.status >= 500) {
      throw new ApiRouteError(503, 'auth_unavailable', 'Authentication is temporarily unavailable. Try again.')
    }
    if (error || !data.user) throw new ApiRouteError(401, 'unauthorized', 'Sign in again to continue.')
    return { supabase, user: data.user, mode: token === null ? 'cookie' : 'bearer' }
  } catch (error) {
    if (error instanceof ApiRouteError) throw error
    throw new ApiRouteError(503, 'auth_unavailable', 'Authentication is temporarily unavailable. Try again.')
  }
}
