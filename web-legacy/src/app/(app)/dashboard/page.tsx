import { createClient } from '@/lib/supabase/server'
import { requireUser } from '@/lib/supabase/require-user'
import HomeClient from './HomeClient'
import { todayString } from '@/lib/utils'
import { countDaysLogged } from '@/lib/levels'

export default async function DashboardPage() {
  const supabase = createClient()
  const user = await requireUser(supabase)

  const today = todayString()
  const thirtyAgo = new Date()
  thirtyAgo.setDate(thirtyAgo.getDate() - 29)
  const thirtyAgoStr = thirtyAgo.toISOString().split('T')[0]
  const ninetyAgo = new Date()
  ninetyAgo.setDate(ninetyAgo.getDate() - 89)
  const ninetyAgoStr = ninetyAgo.toISOString().split('T')[0]

  // Use allSettled so a single slow/failing query doesn't kill the whole page
  const results = await Promise.allSettled([
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
    // All-time, dates only, for the level. Appended so indices above stay put.
    // Each is one row per day (habit_logs one per habit per day), so the
    // payload is tiny even across years; dates are deduplicated in countDaysLogged.
    supabase.from('health_logs').select('date').eq('user_id', user.id).limit(5000),
    supabase.from('prayer_logs').select('date').eq('user_id', user.id).limit(5000),
    supabase.from('habit_logs').select('date').eq('user_id', user.id).limit(5000),
    // What the user told the coach, so a repeated miss can show what they
    // said last time. Newest first; ninety days is plenty for "last time".
    supabase.from('coach_notes').select('id, kind, subject, content, date').eq('user_id', user.id)
      .gte('date', ninetyAgoStr).order('created_at', { ascending: false }).limit(200),
  ])
  const data = (i: number): any =>
    results[i].status === 'fulfilled' ? ((results[i] as any).value?.data ?? null) : null

  const profile             = data(0)
  const habits              = data(1)
  const habitLogs30         = data(2)
  const prayerLogs30        = data(3)
  const challenges          = data(4)
  const challengeCheckins30 = data(5)
  const tasks30             = data(6)
  const goals               = data(7)
  const aiReports           = data(8)
  const healthLogs30        = data(9)

  // Level: distinct days on which anything at all was recorded. Derived here
  // on every load rather than stored, so it is always current.
  const lifetimeDays = countDaysLogged(data(10), data(11), data(12))
  const coachNotes = data(13) ?? []

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
      lifetimeDays={lifetimeDays}
      coachNotes={coachNotes}
    />
  )
}
