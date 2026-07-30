import { createBrowserClient } from '@supabase/ssr'

export function createClient() {
  // Use fallback placeholder during build — real values come from env at runtime
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? 'https://placeholder.supabase.co'
  const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? 'placeholder-anon-key'
  return createBrowserClient(url, key)
}
