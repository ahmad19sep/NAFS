import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiChat, AiError } from '@/lib/ai'
import { buildHealthDigest } from '@/lib/health-digest'
import { ageFromBirthDate } from '@/lib/utils'
import { findRepeatedMisses, describeMiss } from '@/lib/misses'
import { findCorrelates, describeCorrelate } from '@/lib/correlates'
import { buildCoachMemory, memoryIsEmpty } from '@/lib/coach-notes'
import { totalSleepMinutes } from '@/lib/health'
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
    const ninetyAgo = new Date()
    ninetyAgo.setDate(ninetyAgo.getDate() - 89)
    const memoryStart = ninetyAgo.toISOString().split('T')[0]

    const results = await Promise.allSettled([
      supabase.from('users').select('name, about_me, deen_enabled, height_cm, weight_kg, created_at').eq('id', user.id).maybeSingle(),
      // id and schedule fields are needed to detect repeated misses; value and
      // duration so a counter or duration habit only counts when its target was met.
      supabase.from('habits').select('id, name, emoji, type, target_value, time_target_mins, is_paused, schedule_kind, schedule_days, current_streak, longest_streak').eq('user_id', user.id).eq('is_active', true),
      supabase.from('habit_logs').select('habit_id, date, completed, value, duration_mins').eq('user_id', user.id).gte('date', start),
      supabase.from('prayer_logs').select('date, fajr, dhuhr, asr, maghrib, isha').eq('user_id', user.id).gte('date', start),
      supabase.from('tasks').select('title, status, period_date, type').eq('user_id', user.id).gte('period_date', start),
      supabase.from('health_logs').select('date, sleep_sessions, sleep_hours, meals, water_glasses, steps, exercise_done, exercise_minutes, weight_kg').eq('user_id', user.id).gte('date', start),
      supabase.from('goals').select('title, progress_pct, deadline, goal_milestones(title, done)').eq('user_id', user.id),
      supabase.from('challenges').select('title, current_streak, duration_days, status').eq('user_id', user.id),
      supabase.from('dreams').select('statement, dream_date, total_hours_required').eq('user_id', user.id).maybeSingle(),
      supabase.from('daily_logs').select('date, weighted_hours_today, todays_pull_days').eq('user_id', user.id).gte('date', start),
      supabase.from('screentime_logs').select('date, total_mins, apps').eq('user_id', user.id).gte('date', start),
      // The coach's memory: what the user told it, in their words. Newest first.
      supabase.from('coach_notes').select('kind, subject, content, date').eq('user_id', user.id)
        .gte('date', memoryStart).order('created_at', { ascending: false }).limit(200),
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
    const notes      = data(11) ?? []

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

    // The same digest health-recommend uses: real sleep sessions and meals,
    // each metric carrying its own observed count, absent staying null. It
    // replaces a bare avg_sleep_hours that said nothing about coverage and
    // never saw what the user actually ate.
    const healthDigest = buildHealthDigest(healthLogs, {
      startDate: start,
      endDate: today,
      eligibleDays: 30,
    })

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

    // What keeps not happening, and what was different on those days.
    // Deterministic. Unrecorded is never counted; a day without a sleep or
    // screen value is unknown, not zero.
    const repeated = findRepeatedMisses({ today, habits, habitLogs, prayerLogs, deenEnabled: deenOn })
    const sleepMinutesByDate: Record<string, number> = {}
    for (const l of healthLogs) {
      const sessions = Array.isArray(l.sleep_sessions) ? l.sleep_sessions : []
      const mins = sessions.length ? totalSleepMinutes(sessions)
        : l.sleep_hours != null ? Number(l.sleep_hours) * 60 : null
      if (mins != null && mins > 0) sleepMinutesByDate[l.date] = mins
    }
    const screenMinutesByDate: Record<string, number> = {}
    for (const s of screenDays) screenMinutesByDate[s.date] = Number(s.total_mins)
    const possibleCauses = findCorrelates({ misses: repeated, sleepMinutesByDate, screenMinutesByDate })
      .map(describeCorrelate)

    // The coach's memory, with habit ids turned back into names for the model.
    const PRAYER_NAMES: Record<string, string> =
      { fajr: 'Fajr', dhuhr: 'Dhuhr', asr: 'Asr', maghrib: 'Maghrib', isha: 'Isha' }
    const habitNames = new Map<string, string>(habits.map((h: any) => [h.id, h.name]))
    const coachMemory = buildCoachMemory(notes, {
      labelFor: (s) => {
        const [kind, id] = s.split(':')
        return kind === 'prayer' ? (PRAYER_NAMES[id] ?? id) : (habitNames.get(id) ?? s)
      },
    })

    const context = {
      user: {
        name: profile?.name || 'the user',
        about: profile?.about_me ?? undefined,
        // Real, from the profile, or absent — the prompt is told never to guess it.
        age: ageFromBirthDate((profile?.about_me as any)?.birth_date) ?? undefined,
        faith_mode: deenOn,
        member_since: profile?.created_at?.split('T')[0],
      },
      today,
      last_30_days: {
        habits: habitSummary,
        habit_completions_by_date: habitDoneByDate,
        ...(prayerSummary ? { prayers: prayerSummary } : {}),
        tasks: { total: tasks.length, completed: tasksDone },
        // The system prompt tells the coach to name these plainly, offer a
        // measured cause as a possibility if there is one, and otherwise ask
        // one question and remember the answer.
        repeated_misses: repeated.map(describeMiss),
        ...(possibleCauses.length ? { possible_causes: possibleCauses } : {}),
        health: {
          days_logged: healthLogs.length,
          sleep: {
            avg_minutes_per_recorded_night: healthDigest.sleep.meanMinutes,
            recorded_nights: healthDigest.sleep.recordedNights,
            of_days: healthDigest.sleep.eligibleNights,
            shortest_minutes: healthDigest.sleep.shortestMinutes,
            longest_minutes: healthDigest.sleep.longestMinutes,
            nights_including_a_nap: healthDigest.sleep.nightsWithNaps,
          },
          meals: {
            meals_recorded: healthDigest.meals.recordedMeals,
            days_with_any_meal: healthDigest.meals.daysWithAnyMeal,
            of_days: healthDigest.meals.eligibleDays,
            foods_by_type: healthDigest.meals.categoryCounts,
          },
          exercise_days: healthDigest.exercise.daysExercised,
          water_avg_glasses: healthDigest.water.meanGlasses,
          steps_avg: healthDigest.steps.meanSteps,
          // Stated so the coach cannot read an unrecorded day as a zero.
          not_known: healthDigest.limitations,
        },
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
      // What they told the coach before, in their own words. Absent until
      // they have said something, so the model is never handed empty shapes.
      ...(memoryIsEmpty(coachMemory) ? {} : { coach_memory: coachMemory }),
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
