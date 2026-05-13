import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import HomeClient from './HomeClient'
import { todayString } from '@/lib/utils'

export default async function DashboardPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const today = todayString()

  const [
    { data: profile },
    { data: habits },
    { data: habitLogs },
    { data: prayerLog },
    { data: challenges },
    { data: checkin },
    { data: goals },
    { data: aiReports },
    { data: healthLog },
  ] = await Promise.all([
    supabase.from('users').select('*').eq('id', user.id).single(),
    supabase.from('habits').select('*').eq('user_id', user.id).eq('is_active', true).order('sort_order'),
    supabase.from('habit_logs').select('*').eq('user_id', user.id).eq('date', today),
    supabase.from('prayer_logs').select('*').eq('user_id', user.id).eq('date', today).single(),
    supabase.from('challenges').select('*, challenge_checkins(date, completed)').eq('user_id', user.id).eq('status', 'active'),
    supabase.from('daily_checkins').select('*').eq('user_id', user.id).eq('date', today).single(),
    supabase.from('goals').select('id, title, emoji, deadline, progress_pct, goal_milestones(done)').eq('user_id', user.id),
    supabase.from('ai_reports').select('id, type, generated_at').eq('user_id', user.id).gte('generated_at', new Date(Date.now() - 7 * 86400000).toISOString()),
    supabase.from('health_logs').select('*').eq('user_id', user.id).eq('date', today).single(),
  ])

  if (!profile?.onboarding_complete) redirect('/onboarding')

  return (
    <HomeClient
      profile={profile}
      habits={habits ?? []}
      habitLogs={habitLogs ?? []}
      prayerLog={prayerLog}
      challenges={challenges ?? []}
      checkin={checkin}
      goals={goals ?? []}
      aiReports={aiReports ?? []}
      healthLog={healthLog}
      today={today}
    />
  )
}
