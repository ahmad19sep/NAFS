import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import ProfileClient from './ProfileClient'

export default async function ProfilePage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const meta = (user.user_metadata ?? {}) as Record<string, any>
  const fallbackName: string =
    meta.full_name || meta.name || meta.user_name || (user.email?.split('@')[0] ?? '')

  // Fetch existing profile; if missing or incomplete, create / backfill from auth
  let { data: profile } = await supabase
    .from('users')
    .select('*, dreams(*, activity_weights(*))')
    .eq('id', user.id)
    .maybeSingle()

  if (!profile) {
    // Row doesn't exist yet — create it with auth metadata
    const { data: created } = await supabase
      .from('users')
      .insert({
        id: user.id,
        email: user.email ?? '',
        name: fallbackName,
        onboarding_complete: false,
      })
      .select('*, dreams(*, activity_weights(*))')
      .single()
    profile = created
  } else {
    // Backfill empty fields from auth
    const patch: Record<string, any> = {}
    if (!profile.name?.trim() && fallbackName) patch.name = fallbackName
    if (!profile.email?.trim() && user.email)  patch.email = user.email
    if (Object.keys(patch).length > 0) {
      const { data: updated, error: updErr } = await supabase
        .from('users')
        .update(patch)
        .eq('id', user.id)
        .select('*, dreams(*, activity_weights(*))')
        .maybeSingle()
      if (updErr) {
        console.warn('[profile] backfill update failed:', updErr.message)
      }
      if (updated) profile = updated
      else {
        // Optimistic local merge so the user sees the right name immediately
        // even if the DB write was blocked by RLS
        Object.assign(profile, patch)
      }
    }
  }

  // Last-resort guarantees so the client never sees empty critical fields
  if (profile) {
    if (!profile.name?.trim())  profile.name  = fallbackName || 'Friend'
    if (!profile.email?.trim()) profile.email = user.email ?? ''
    if (!profile.created_at)    profile.created_at = user.created_at
  }

  const { data: logStats } = await supabase
    .from('daily_logs')
    .select('date, identity_score, weighted_hours_today, todays_pull_days')
    .eq('user_id', user.id)
    .order('date', { ascending: false })

  return <ProfileClient profile={profile} logs={logStats ?? []} />
}
