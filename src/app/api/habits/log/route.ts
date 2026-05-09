import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const { habitId, date, completed, value, duration_mins, notes } = await req.json()

  // Upsert log
  await supabase.from('habit_logs').upsert({
    user_id: user.id,
    habit_id: habitId,
    date,
    completed,
    value: value ?? (completed ? 1 : 0),
    duration_mins: duration_mins ?? 0,
    notes: notes ?? null,
  }, { onConflict: 'user_id,habit_id,date' })

  // Recalculate streak
  const { data: recentLogs } = await supabase
    .from('habit_logs')
    .select('date, completed')
    .eq('user_id', user.id)
    .eq('habit_id', habitId)
    .order('date', { ascending: false })
    .limit(400)

  let currentStreak = 0
  let longestStreak = 0
  let tempStreak = 0

  if (recentLogs) {
    // Build streak from today backwards
    const today = new Date(date)
    for (let i = 0; i < recentLogs.length; i++) {
      const logDate = new Date(recentLogs[i].date)
      const expectedDate = new Date(today)
      expectedDate.setDate(today.getDate() - i)

      if (logDate.toISOString().split('T')[0] === expectedDate.toISOString().split('T')[0] && recentLogs[i].completed) {
        currentStreak++
      } else break
    }

    // Longest streak
    for (const log of recentLogs) {
      if (log.completed) {
        tempStreak++
        longestStreak = Math.max(longestStreak, tempStreak)
      } else {
        tempStreak = 0
      }
    }
  }

  await supabase.from('habits').update({
    current_streak: currentStreak,
    longest_streak: longestStreak,
  }).eq('id', habitId).eq('user_id', user.id)

  return NextResponse.json({ ok: true, currentStreak, longestStreak })
}
