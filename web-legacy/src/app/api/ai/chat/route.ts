import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiChat, AiError } from '@/lib/ai'
import { ASK_ASCEND_SYSTEM } from '@/lib/ai-prompts'

// Ask Ascend — the coach answers from the user's REAL data across every
// feature: habits, prayers (if faith mode), tasks, health, goals,
// challenges and dream trajectory.
export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { messages } = await req.json()
    const userQuestion = messages[messages.length - 1]?.content ?? ''

    const thirtyAgo = new Date()
    thirtyAgo.setDate(thirtyAgo.getDate() - 29)
    const start = thirtyAgo.toISOString().split('T')[0]
    const today = new Date().toISOString().split('T')[0]

    const results = await Promise.allSettled([
      supabase.from('users').select('name, about_me, deen_enabled, height_cm, weight_kg, created_at').eq('id', user.id).maybeSingle(),
      supabase.from('habits').select('name, emoji, type, target_value, current_streak, longest_streak').eq('user_id', user.id).eq('is_active', true),
      supabase.from('habit_logs').select('habit_id, date, completed').eq('user_id', user.id).gte('date', start),
      supabase.from('prayer_logs').select('date, fajr, dhuhr, asr, maghrib, isha').eq('user_id', user.id).gte('date', start),
      supabase.from('tasks').select('title, status, period_date, type').eq('user_id', user.id).gte('period_date', start),
      supabase.from('health_logs').select('date, sleep_hours, water_glasses, exercise_done, steps').eq('user_id', user.id).gte('date', start),
      supabase.from('goals').select('title, progress_pct, deadline, goal_milestones(title, done)').eq('user_id', user.id),
      supabase.from('challenges').select('title, current_streak, duration_days, status').eq('user_id', user.id),
      supabase.from('dreams').select('statement, dream_date, total_hours_required').eq('user_id', user.id).maybeSingle(),
      supabase.from('daily_logs').select('date, weighted_hours_today, todays_pull_days').eq('user_id', user.id).gte('date', start),
      supabase.from('screentime_logs').select('date, total_mins, apps').eq('user_id', user.id).gte('date', start),
    ])
    const data = (i: number): any =>
      results[i].status === 'fulfilled' ? ((results[i] as any).value?.data ?? null) : null

    const profile    = data(0)
    const habits     = data(1) ?? []
    const habitLogs  = data(2) ?? []
    const prayerLogs = data(3) ?? []
    const tasks      = data(4) ?? []
    const healthLogs = data(5) ?? []
    const goals      = data(6) ?? []
    const challenges = data(7) ?? []
    const dream      = data(8)
    const dreamLogs  = data(9) ?? []
    const screenLogs = data(10) ?? []

    const deenOn = (profile?.deen_enabled ?? true) as boolean

    // ---- Compact, number-dense summary the model can actually use ----
    const habitSummary = habits.map((h: any) => ({
      name: `${h.emoji} ${h.name}`,
      streak: h.current_streak,
      longest: h.longest_streak,
    }))
    const habitDoneByDate: Record<string, number> = {}
    for (const l of habitLogs) if (l.completed) habitDoneByDate[l.date] = (habitDoneByDate[l.date] ?? 0) + 1

    const prayerSummary = deenOn ? (() => {
      const days = prayerLogs.length
      let prayed = 0, jamat = 0, fajrDone = 0
      for (const p of prayerLogs) {
        for (const k of ['fajr', 'dhuhr', 'asr', 'maghrib', 'isha']) {
          const v = Number(p[k] ?? 0)
          if (v >= 1) prayed++
          if (v === 2) jamat++
        }
        if (Number(p.fajr ?? 0) >= 1) fajrDone++
      }
      return { days_logged: days, prayers_prayed: prayed, of_possible: days * 5, in_jamat: jamat, fajr_days: fajrDone }
    })() : undefined

    const tasksDone = tasks.filter((t: any) => t.status === 'completed').length
    const sleepVals = healthLogs.map((h: any) => Number(h.sleep_hours ?? 0)).filter((v: number) => v > 0)
    const avgSleep = sleepVals.length ? (sleepVals.reduce((s: number, v: number) => s + v, 0) / sleepVals.length).toFixed(1) : null
    const exerciseDays = healthLogs.filter((h: any) => h.exercise_done).length

    const dreamHours = dreamLogs.reduce((s: number, l: any) => s + Number(l.weighted_hours_today ?? 0), 0)

    // Screen time: daily totals + which apps eat the most minutes overall
    const screenDays = screenLogs.filter((s: any) => Number(s.total_mins ?? 0) > 0)
    const appTotals: Record<string, number> = {}
    for (const s of screenDays) {
      for (const a of (s.apps ?? [])) {
        appTotals[a.app] = (appTotals[a.app] ?? 0) + Number(a.minutes ?? 0)
      }
    }
    const topApps = Object.entries(appTotals)
      .sort((a, b) => b[1] - a[1]).slice(0, 5)
      .map(([app, mins]) => ({ app, total_mins: mins }))
    const screenSummary = screenDays.length ? {
      days_logged: screenDays.length,
      avg_daily_mins: Math.round(screenDays.reduce((s: number, x: any) => s + x.total_mins, 0) / screenDays.length),
      worst_day_mins: Math.max(...screenDays.map((x: any) => x.total_mins)),
      top_apps: topApps,
    } : undefined

    const context = {
      user: {
        name: profile?.name || 'the user',
        about: profile?.about_me ?? undefined,
        faith_mode: deenOn,
        member_since: profile?.created_at?.split('T')[0],
      },
      today,
      last_30_days: {
        habits: habitSummary,
        habit_completions_by_date: habitDoneByDate,
        ...(prayerSummary ? { prayers: prayerSummary } : {}),
        tasks: { total: tasks.length, completed: tasksDone },
        health: { days_logged: healthLogs.length, avg_sleep_hours: avgSleep, exercise_days: exerciseDays },
        ...(screenSummary ? { phone_screen_time: screenSummary } : {}),
      },
      goals: goals.map((g: any) => ({
        title: g.title, progress_pct: g.progress_pct, deadline: g.deadline,
        milestones_done: (g.goal_milestones ?? []).filter((m: any) => m.done).length,
        milestones_total: (g.goal_milestones ?? []).length,
      })),
      active_challenges: challenges.filter((c: any) => c.status === 'active')
        .map((c: any) => ({ title: c.title, streak: c.current_streak, length_days: c.duration_days })),
      dream: dream ? {
        statement: dream.statement,
        deadline: dream.dream_date,
        hours_done_last_30d: Math.round(dreamHours * 10) / 10,
        total_hours_target: dream.total_hours_required,
      } : 'Not set yet',
    }

    const contextPrompt =
      `USER DATA (real, last 30 days):\n${JSON.stringify(context, null, 1)}\n\nUSER QUESTION: ${userQuestion}`

    const aiMessages: { role: 'system' | 'user' | 'assistant'; content: string }[] = [
      { role: 'system', content: ASK_ASCEND_SYSTEM },
      ...messages.slice(-8).map((m: any, i: number, arr: any[]) => ({
        role: m.role as 'user' | 'assistant',
        content: m.role === 'user' && i === arr.length - 1 ? contextPrompt : m.content,
      })),
    ]

    const reply = await aiChat(aiMessages)

    await supabase.from('ai_conversations').upsert({
      user_id: user.id,
      messages: [...messages, { role: 'assistant', content: reply }],
      created_at: new Date().toISOString(),
    })

    return NextResponse.json({ reply })
  } catch (err: unknown) {
    // AiError messages are written for the user; anything else stays generic
    // so internals are never echoed back to the client.
    if (err instanceof AiError) {
      return NextResponse.json({ error: err.message }, { status: err.status ?? 502 })
    }
    return NextResponse.json({ error: 'AI chat failed' }, { status: 500 })
  }
}
