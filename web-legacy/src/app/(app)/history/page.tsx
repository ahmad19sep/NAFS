import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import HistoryPageClient from './HistoryClient'
import { todayString } from '@/lib/utils'

export default async function HistoryPage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  const today = todayString()
  const thirtyAgo = new Date()
  thirtyAgo.setDate(thirtyAgo.getDate() - 29)
  const start = thirtyAgo.toISOString().split('T')[0]

  // Pull 6 months of task data so weekly/monthly views have enough range
  const sixMonthsAgo = new Date()
  sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6)
  const tasksStart = sixMonthsAgo.toISOString().split('T')[0]

  const results = await Promise.allSettled([
    supabase.from('users').select('deen_enabled').eq('id', user.id).single(),
    supabase.from('habits').select('*').eq('user_id', user.id).eq('is_active', true),
    supabase.from('habit_logs').select('*').eq('user_id', user.id).gte('date', start),
    supabase.from('prayer_logs').select('*').eq('user_id', user.id).gte('date', start),
    supabase.from('challenges').select('id, start_date, duration_days, status, title, emoji').eq('user_id', user.id),
    supabase.from('challenge_checkins').select('challenge_id, date, completed').gte('date', start),
    supabase.from('tasks').select('*').eq('user_id', user.id).gte('period_date', tasksStart),
    supabase.from('health_logs').select('*').eq('user_id', user.id).gte('date', start),
  ])
  const data = (i: number): any =>
    results[i].status === 'fulfilled' ? ((results[i] as any).value?.data ?? null) : null
  const profile             = data(0)
  const habits              = data(1)
  const habitLogs30         = data(2)
  const prayerLogs30        = data(3)
  const challenges          = data(4)
  const challengeCheckins30 = data(5)
  const allTasks            = data(6)
  const healthLogs30        = data(7)

  return (
    <HistoryPageClient
      today={today}
      deenEnabled={(profile?.deen_enabled ?? true) as boolean}
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
