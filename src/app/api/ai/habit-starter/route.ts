import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiJSON } from '@/lib/ai'

interface HabitStarterPack {
  pattern_insight: string                       // What the user's history reveals
  how_to_succeed: string                        // 1-2 sentences on staying consistent
  best_time: string                             // Specific recommendation, e.g. "after Fajr"
  related_habits: Array<{
    name: string; emoji: string
    type: 'simple' | 'counter' | 'duration'
    target_value?: number; unit?: string; time_target_mins?: number
  }>
  generated_at: string
}

const SYSTEM = `You are NAFS — an honest accountability coach for a Muslim user.
The user just created a new habit. Look at their existing habits and recent
completion patterns. Give them a starter pack with:

1. ONE insight from their past patterns (e.g. "Your morning habits succeed 80% of
   the time, evening ones only 30%")
2. ONE concrete piece of advice for sticking to this specific habit
3. The BEST time of day to do this habit (cite real reasoning if possible)
4. 1-2 RELATED habits that stack well with this one

Return ONLY valid JSON, no markdown:
{
  "pattern_insight": "What you noticed in their past data (1-2 sentences)",
  "how_to_succeed": "Direct advice tailored to this habit (1-2 sentences)",
  "best_time": "e.g. 'After Fajr (06:30)' or 'Right before bed' — be specific",
  "related_habits": [
    { "name": "name", "emoji": "📚",
      "type": "simple|counter|duration",
      "target_value": <num>, "unit": "<unit>", "time_target_mins": <num> }
  ]
}

Rules:
- counter habits MUST include target_value + unit
- duration habits MUST include time_target_mins
- simple habits don't need target/unit
- 1-2 related habits (not too many)
- Use real emojis
- If user has no past data, give general advice based on habit type`

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { habitId } = await req.json()
    if (!habitId) return NextResponse.json({ error: 'habitId required' }, { status: 400 })

    const { data: habit } = await supabase
      .from('habits').select('*').eq('id', habitId).eq('user_id', user.id).single()
    if (!habit) return NextResponse.json({ error: 'Habit not found' }, { status: 404 })

    // Pull user context: existing habits + last 14 days of habit completions
    const fourteenAgo = new Date(); fourteenAgo.setDate(fourteenAgo.getDate() - 13)
    const start = fourteenAgo.toISOString().split('T')[0]
    const [{ data: allHabits }, { data: habitLogs14 }, { data: profile }, { data: prayerLogs14 }] = await Promise.all([
      supabase.from('habits').select('id, name, type, current_streak, longest_streak, schedule_kind').eq('user_id', user.id).eq('is_active', true),
      supabase.from('habit_logs').select('habit_id, date, completed').eq('user_id', user.id).gte('date', start),
      supabase.from('users').select('about_me, usual_sleep_time, usual_wake_time').eq('id', user.id).maybeSingle(),
      supabase.from('prayer_logs').select('date, fajr').eq('user_id', user.id).gte('date', start),
    ])

    const about = (profile?.about_me ?? {}) as any

    // Compute completion rate per existing habit
    const habitStats = ((allHabits ?? []) as any[]).slice(0, 8).map((h) => {
      const logs = (habitLogs14 ?? []).filter((l: any) => l.habit_id === h.id)
      const done = logs.filter((l: any) => l.completed).length
      return `${h.name} (${h.type}): ${done}/14 days last 2 weeks, current streak ${h.current_streak}, best ${h.longest_streak}`
    }).join('\n')

    const fajrCount = (prayerLogs14 ?? []).filter((p: any) => Number(p.fajr ?? 0) >= 1).length

    const prompt = `NEW HABIT
─────────
Name: ${habit.name}
Emoji: ${habit.emoji}
Type: ${habit.type}
Target: ${habit.target_value ?? '?'} ${habit.unit ?? ''}
Time target (if duration): ${habit.time_target_mins ?? 0} min
Schedule: ${habit.schedule_kind}
Why: ${habit.why || '(none)'}

USER'S EXISTING HABITS (last 14 days)
─────────────────────────────────────
${habitStats || '(none — this is their first habit)'}

OTHER CONTEXT
─────────────
Usual sleep: ${profile?.usual_sleep_time?.slice(0,5) ?? '?'} – ${profile?.usual_wake_time?.slice(0,5) ?? '?'}
Fajr prayed last 14 days: ${fajrCount}/14
${about.bio ? `Bio: ${about.bio}` : ''}
${about.occupation ? `Occupation: ${about.occupation}` : ''}

Now generate the starter pack as JSON. Use real patterns from their data.`

    const result = await aiJSON<Omit<HabitStarterPack, 'generated_at'>>(prompt, SYSTEM)

    if (!result || !result.pattern_insight) {
      return NextResponse.json({ error: 'AI returned an unparseable response' }, { status: 502 })
    }

    const stored: HabitStarterPack = {
      pattern_insight: result.pattern_insight,
      how_to_succeed: result.how_to_succeed || '',
      best_time: result.best_time || '',
      related_habits: Array.isArray(result.related_habits) ? result.related_habits.slice(0, 3) : [],
      generated_at: new Date().toISOString(),
    }

    await supabase.from('habits').update({ ai_starter_pack: stored }).eq('id', habitId).eq('user_id', user.id)

    return NextResponse.json({ starter: stored })
  } catch (err: any) {
    console.error('habit-starter error:', err)
    return NextResponse.json({ error: err?.message ?? 'Failed' }, { status: 500 })
  }
}
