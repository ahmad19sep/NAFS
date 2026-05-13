import { createClient } from '@/lib/supabase/server'
import { NextResponse } from 'next/server'

export async function GET(request: Request) {
  const requestUrl = new URL(request.url)
  const code = requestUrl.searchParams.get('code')
  const next = requestUrl.searchParams.get('next') ?? '/'

  if (code) {
    const supabase = createClient()
    const { data } = await supabase.auth.exchangeCodeForSession(code)

    // Save user profile if this is first sign-in (e.g. Google OAuth)
    if (data?.user) {
      const meta = data.user.user_metadata ?? {}
      const name = meta.full_name || meta.name || meta.user_name || data.user.email?.split('@')[0] || ''
      const gender = meta.gender || null

      await supabase.from('users').upsert({
        id: data.user.id,
        email: data.user.email!,
        name,
        gender,
        // Don't overwrite onboarding_complete if user already exists
      }, { onConflict: 'id', ignoreDuplicates: false })
    }
  }

  return NextResponse.redirect(new URL(next, request.url))
}
