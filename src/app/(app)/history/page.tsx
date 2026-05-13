import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'
import HistoryPageClient from './HistoryClient'
import { todayString } from '@/lib/utils'

export default async function HistoryPage() {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) redirect('/auth')

  const today = todayString()
  const thirtyAgo = new Date()
  thirtyAgo.setDate(thirtyAgo.getDate() - 29)
  const start = thirtyAgo.toISOString().split('T')[0]

  // Pull 6 months of task data so weekly/monthly views have enough range
  const sixMonthsAgo = new Date()
  sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6)
  const tasksStart = sixMonthsAgo.toISOString().split('T')[0]

  const [
    { data: habits },
    { data: habitLogs30 },
    { data: prayerLogs30 },
    { data: challenges },
    { data: challengeCheckins30 },
    { data: allTasks },
    { data: healthLogs30 },
  ] = await Promise.all([
    supabase.from('habits').select('*').eq('user_id', user.id).eq('is_active', true),
    supabase.from('habit_logs').select('*').eq('user_id', user.id).gte('date', start),
    supabase.from('prayer_logs').select('*').eq('user_id', user.id).gte('date', start),
    supabase.from('challenges').select('id, start_date, duration_days, status, title, emoji').eq('user_id', user.id),
    supabase.from('challenge_checkins').select('challenge_id, date, completed').gte('date', start),
    supabase.from('tasks').select('*').eq('user_id', user.id).gte('period_date', tasksStart),
    supabase.from('health_logs').select('*').eq('user_id', user.id).gte('date', start),
  ])

  return (
    <HistoryPageClient
      today={today}
      habits={habits ?? []}
      habitLogs30={habitLogs30 ?? []}
      prayerLogs30={prayerLogs30 ?? []}
      challenges={challenges ?? []}
      challengeCheckins30={challengeCheckins30 ?? []}
      tasks={allTasks ?? []}
      healthLogs30={healthLogs30 ?? []}
    />
  )
}
