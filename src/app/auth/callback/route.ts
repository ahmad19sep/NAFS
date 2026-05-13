import { createClient } from '@/lib/supabase/server'
import { NextResponse } from 'next/server'

export async function GET(request: Request) {
  const requestUrl = new URL(request.url)
  const code = requestUrl.searchParams.get('code')
  const next = requestUrl.searchParams.get('next') ?? '/'

  if (code) {
    const supabase = createClient()
    const { data } = await supabase.auth.exchangeCodeForSession(code)

    if (data?.user) {
      const meta = data.user.user_metadata ?? {}
      const appMeta = (data.user as any).app_metadata ?? {}
      const provider = appMeta.provider ?? 'email'
      const isOAuth = provider !== 'email'

      const name =
        meta.full_name ||
        meta.name ||
        meta.user_name ||
        data.user.email?.split('@')[0] ||
        ''
      const googleAvatar: string | null =
        meta.avatar_url || meta.picture || null

      // Read existing row so we can preserve user-uploaded fields
      const { data: existing } = await supabase
        .from('users')
        .select('avatar_url, onboarding_complete, name')
        .eq('id', data.user.id)
        .maybeSingle()

      const patch: Record<string, any> = {
        id: data.user.id,
        email: data.user.email!,
      }

      // Always keep name in sync (so Google name change reflects)
      if (name) patch.name = name

      // Only set avatar from OAuth provider if user hasn't uploaded a custom one
      if (googleAvatar && !existing?.avatar_url) {
        patch.avatar_url = googleAvatar
      }

      // For OAuth users on their first sign-in, mark onboarding complete
      // so they go straight to the dashboard. Preserve true if already true.
      if (isOAuth && !existing?.onboarding_complete) {
        patch.onboarding_complete = true
      }

      await supabase
        .from('users')
        .upsert(patch, { onConflict: 'id', ignoreDuplicates: false })
    }
  }

  return NextResponse.redirect(new URL(next, request.url))
}
