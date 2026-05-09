import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import ProfileClient from './ProfileClient'

export default async function ProfilePage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const { data: profile } = await supabase
    .from('users')
    .select('*, dreams(*, activity_weights(*))')
    .eq('id', user.id)
    .single()

  // Stats summary
  const { data: logStats } = await supabase
    .from('daily_logs')
    .select('date, identity_score, weighted_hours_today, todays_pull_days')
    .eq('user_id', user.id)
    .order('date', { ascending: false })

  return <ProfileClient profile={profile} logs={logStats ?? []} />
}
