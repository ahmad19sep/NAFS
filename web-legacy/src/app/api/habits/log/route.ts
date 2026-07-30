import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

const WEEKDAY_CODES = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'] as const

function weekdayCode(dateStr: string) {
  return WEEKDAY_CODES[new Date(dateStr + 'T12:00:00').getDay()]
}

function isoMonday(dateStr: string) {
  const d = new Date(dateStr + 'T12:00:00')
  const day = (d.getDay() + 6) % 7  // 0 = Mon
  d.setDate(d.getDate() - day)
  return d.toISOString().split('T')[0]
}

function addDays(dateStr: string, n: number) {
  const d = new Date(dateStr + 'T12:00:00')
  d.setDate(d.getDate() + n)
  return d.toISOString().split('T')[0]
}

export async function POST(req: NextRequest) {
  const supabase = createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

  const body = await req.json()
  const { habitId, date, completed, value, duration_mins, notes, subject_delta } = body

  const { data: habit, error: habitErr } = await supabase
    .from('habits').select('*').eq('id', habitId).eq('user_id', user.id).single()
  if (habitErr || !habit) return NextResponse.json({ error: 'Not found' }, { status: 404 })

  // ---- Subject-tracked: update position by delta ----
  let logValue: number = value ?? (completed ? 1 : 0)
  let logCompleted: boolean = !!completed

  if (habit.type === 'subject') {
    const { data: existing } = await supabase.from('habit_logs')
      .select('value')
      .eq('user_id', user.id).eq('habit_id', habitId).eq('date', date).maybeSingle()
    const oldDelta = existing?.value ?? 0
    const newDelta = Math.max(0, subject_delta ?? value ?? 0)
    const adjustment = newDelta - oldDelta
    const total = habit.subject_total ?? Number.MAX_SAFE_INTEGER
    const newPosition = Math.max(0, Math.min(total, (habit.subject_position ?? 0) + adjustment))
    await supabase.from('habits').update({ subject_position: newPosition }).eq('id', habitId)
    logValue = newDelta
    logCompleted = newDelta > 0
  }

  await supabase.from('habit_logs').upsert({
    user_id: user.id,
    habit_id: habitId,
    date,
    completed: logCompleted,
    value: logValue,
    duration_mins: duration_mins ?? 0,
    notes: notes ?? null,
  }, { onConflict: 'user_id,habit_id,date' })

  // ---- Recalculate streak (schedule-aware) ----
  const { data: recentLogs } = await supabase
    .from('habit_logs')
    .select('date, completed, value')
    .eq('user_id', user.id).eq('habit_id', habitId)
    .order('date', { ascending: false })
    .limit(400)

  const logsByDate = new Map<string, { completed: boolean; value: number }>()
  for (const l of recentLogs ?? []) logsByDate.set(l.date, { completed: l.completed, value: l.value })

  const isScheduled = (ds: string): boolean => {
    if (habit.schedule_kind === 'weekdays')
      return (habit.schedule_days ?? []).includes(weekdayCode(ds))
    return true
  }
  const isDoneOn = (ds: string): boolean => !!logsByDate.get(ds)?.completed

  let currentStreak = 0
  let longestStreak = habit.longest_streak ?? 0

  if (habit.schedule_kind === 'per_week' && habit.weekly_target) {
    // Group by ISO week, count completions, walk back week by week
    const weekCompletions = new Map<string, number>()
    Array.from(logsByDate.entries()).forEach(([ds, log]) => {
      if (!log.completed) return
      const wk = isoMonday(ds)
      weekCompletions.set(wk, (weekCompletions.get(wk) ?? 0) + 1)
    })
    let wk = isoMonday(date)
    while ((weekCompletions.get(wk) ?? 0) >= habit.weekly_target) {
      currentStreak++
      wk = addDays(wk, -7)
    }
    let run = 0
    const sorted = Array.from(weekCompletions.keys()).sort()
    if (sorted.length) {
      let cursor = sorted[0]
      const end = isoMonday(date)
      while (cursor <= end) {
        if ((weekCompletions.get(cursor) ?? 0) >= habit.weekly_target) {
          run++
          longestStreak = Math.max(longestStreak, run)
        } else run = 0
        cursor = addDays(cursor, 7)
      }
    }
  } else {
    // Daily / weekdays: walk back day by day, skipping non-scheduled
    let cursor = date
    for (let i = 0; i < 365; i++) {
      const ds = i === 0 ? date : addDays(date, -i)
      cursor = ds
      if (!isScheduled(ds)) continue
      if (isDoneOn(ds)) currentStreak++
      else break
    }
    if (recentLogs && recentLogs.length > 0) {
      const oldest = recentLogs[recentLogs.length - 1].date
      let walk = oldest
      let run = 0
      while (walk <= date) {
        if (isScheduled(walk)) {
          if (isDoneOn(walk)) {
            run++
            longestStreak = Math.max(longestStreak, run)
          } else run = 0
        }
        walk = addDays(walk, 1)
      }
    }
  }

  await supabase.from('habits').update({
    current_streak: currentStreak,
    longest_streak: Math.max(longestStreak, currentStreak),
  }).eq('id', habitId).eq('user_id', user.id)

  return NextResponse.json({
    ok: true,
    currentStreak,
    longestStreak: Math.max(longestStreak, currentStreak),
  })
}
