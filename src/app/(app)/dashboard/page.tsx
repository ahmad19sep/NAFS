import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import HomeClient from './HomeClient'
import { todayString } from '@/lib/utils'

export default async function DashboardPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const today = todayString()
  const thirtyAgo = new Date()
  thirtyAgo.setDate(thirtyAgo.getDate() - 29)
  const thirtyAgoStr = thirtyAgo.toISOString().split('T')[0]

  const [
    { data: profile },
    { data: habits },
    { data: habitLogs30 },
    { data: prayerLogs30 },
    { data: challenges },
    { data: challengeCheckins30 },
    { data: tasks30 },
    { data: goals },
    { data: aiReports },
    { data: healthLogs30 },
  ] = await Promise.all([
    supabase.from('users').select('*').eq('id', user.id).single(),
    supabase.from('habits').select('*').eq('user_id', user.id).eq('is_active', true).order('sort_order'),
    supabase.from('habit_logs').select('*').eq('user_id', user.id).gte('date', thirtyAgoStr),
    supabase.from('prayer_logs').select('*').eq('user_id', user.id).gte('date', thirtyAgoStr),
    supabase.from('challenges').select('id, start_date, duration_days, status, title, emoji').eq('user_id', user.id),
    supabase.from('challenge_checkins').select('challenge_id, date, completed').gte('date', thirtyAgoStr),
    supabase.from('tasks').select('*').eq('user_id', user.id).eq('type', 'daily').gte('period_date', thirtyAgoStr),
    supabase.from('goals').select('id, title, emoji, deadline, progress_pct, goal_milestones(done)').eq('user_id', user.id),
    supabase.from('ai_reports').select('id, type, generated_at').eq('user_id', user.id).gte('generated_at', new Date(Date.now() - 7 * 86400000).toISOString()),
    supabase.from('health_logs').select('*').eq('user_id', user.id).gte('date', thirtyAgoStr),
  ])

  if (!profile?.onboarding_complete) redirect('/onboarding')

  const todayPrayerLog = (prayerLogs30 ?? []).find((p: any) => p.date === today) ?? null
  const todayTasks = (tasks30 ?? []).filter((t: any) => t.period_date === today)
  const todayHealth = (healthLogs30 ?? []).find((h: any) => h.date === today) ?? null
  const todayHabitLogs = (habitLogs30 ?? []).filter((l: any) => l.date === today)

  // Attach challenge_checkins to each challenge for the today view
  const challengesWithCheckins = (challenges ?? []).filter((c: any) => c.status === 'active').map((c: any) => ({
    ...c,
    challenge_checkins: (challengeCheckins30 ?? []).filter((ck: any) => ck.challenge_id === c.id),
  }))

  return (
    <HomeClient
      profile={profile}
      habits={habits ?? []}
      habitLogs={todayHabitLogs}
      habitLogs30={habitLogs30 ?? []}
      prayerLog={todayPrayerLog}
      prayerLogs30={prayerLogs30 ?? []}
      challenges={challengesWithCheckins}
      allChallenges={challenges ?? []}
      challengeCheckins30={challengeCheckins30 ?? []}
      todayTasks={todayTasks}
      tasks30={tasks30 ?? []}
      goals={goals ?? []}
      aiReports={aiReports ?? []}
      healthLog={todayHealth}
      healthLogs30={healthLogs30 ?? []}
      today={today}
    />
  )
}
