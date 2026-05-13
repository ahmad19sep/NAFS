import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { generateJSON } from '@/lib/gemini'

interface AlignmentResult {
  score: number
  doing_well: string
  missing: string
  suggested_action: string
  analyzed_at: string
}

const SYSTEM = `You are an honest, sharp accountability coach for a Muslim self-development app called NAFS. Your job is to look at a user's goal and their actual recent activity, then tell them — directly — whether their daily life is moving them toward that goal or not.

Tone: a wise older brother / firm but caring mentor. Never sycophantic. Don't sugarcoat — but don't crush either. Cite real numbers.

Some goal types and what they require:
- Spiritual ("Get to Jannat", "Closer to Allah", "Memorize Quran"): consistent prayers (Fajr especially), Quran reading, dhikr, sadqa, good deeds, avoiding sin
- Financial ("Make money", "Independent income"): skill-building, deep work, building/shipping things, learning, networking
- Health ("Lose weight", "Get fit"): exercise consistency, nutrition tracking, sleep, water
- Career ("Become senior engineer", "Get promoted"): deliberate practice, side projects, focus, reading
- Learning ("Master X"): daily study, deep work, deliberate practice

Return ONLY a JSON object — no prose, no markdown — with this exact shape:
{
  "score": number 0..100,
  "doing_well": "1–2 sentences citing real activity (or 'Nothing yet' if true)",
  "missing": "1–2 sentences naming what's absent that the goal requires (be direct)",
  "suggested_action": "ONE concrete habit, task, or change to add this week"
}`

function fmtPrayers(rows: any[]) {
  if (!rows || rows.length === 0) return 'No prayer logs in the last 7 days'
  const totalDays = rows.length
  const totalPossible = totalDays * 5
  let done = 0
  let jamat = 0
  let fajrDone = 0
  for (const r of rows) {
    const v = (k: string) => Number(r[k] ?? 0)
    const f = v('fajr'), d = v('dhuhr'), a = v('asr'), m = v('maghrib'), i = v('isha')
    if (f >= 1) { done++; fajrDone++ }
    if (d >= 1) done++
    if (a >= 1) done++
    if (m >= 1) done++
    if (i >= 1) done++
    for (const x of [f, d, a, m, i]) if (x === 2) jamat++
  }
  return `${done}/${totalPossible} prayers prayed (Fajr ${fajrDone}/${totalDays}, ${jamat} in jamat) over ${totalDays} days`
}

function fmtHabits(habits: any[], logs: any[]) {
  if (!habits?.length) return 'No habits set up'
  return habits.slice(0, 10).map((h: any) => {
    const dayCount = logs.filter((l: any) => l.habit_id === h.id && l.completed).length
    return `${h.name} (${h.category ?? 'custom'}): done ${dayCount}/7 days, current streak ${h.current_streak}`
  }).join('\n')
}

function fmtTasks(tasks: any[]) {
  if (!tasks?.length) return 'No tasks logged in last 7 days'
  const completed = tasks.filter((t: any) => t.status === 'completed').length
  return `${completed}/${tasks.length} tasks completed (recent titles: ${tasks.slice(0, 5).map((t: any) => `"${t.title}"`).join(', ')})`
}

function fmtChallenges(rows: any[]) {
  const active = rows?.filter((c: any) => c.status === 'active') ?? []
  if (active.length === 0) return 'No active challenges'
  return active.slice(0, 5).map((c: any) =>
    `${c.title} (streak ${c.current_streak} ${c.frequency ?? 'day'}s)`
  ).join('; ')
}

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { goalId } = await req.json()
    if (!goalId) return NextResponse.json({ error: 'goalId required' }, { status: 400 })

    // Fetch the goal
    const { data: goal } = await supabase
      .from('goals').select('*, goal_milestones(title, done)').eq('id', goalId).eq('user_id', user.id)
      .single()
    if (!goal) return NextResponse.json({ error: 'Goal not found' }, { status: 404 })

    // Recent activity (last 7 days)
    const sevenAgo = new Date()
    sevenAgo.setDate(sevenAgo.getDate() - 6)
    const start = sevenAgo.toISOString().split('T')[0]

    const [
      { data: prayerLogs },
      { data: habits },
      { data: habitLogs },
      { data: tasks },
      { data: challenges },
      { data: profile },
    ] = await Promise.all([
      supabase.from('prayer_logs').select('*').eq('user_id', user.id).gte('date', start),
      supabase.from('habits').select('id, name, category, current_streak').eq('user_id', user.id).eq('is_active', true),
      supabase.from('habit_logs').select('habit_id, completed').eq('user_id', user.id).gte('date', start),
      supabase.from('tasks').select('title, status, period_date, type')
        .eq('user_id', user.id).gte('period_date', start).order('period_date', { ascending: false }).limit(40),
      supabase.from('challenges').select('title, current_streak, frequency, status').eq('user_id', user.id),
      supabase.from('users').select('about_me').eq('id', user.id).maybeSingle(),
    ])

    const aboutMe = (profile?.about_me ?? {}) as any
    const milestones = (goal.goal_milestones ?? []) as any[]
    const milestoneSummary = milestones.length > 0
      ? `${milestones.filter((m) => m.done).length}/${milestones.length} milestones done`
      : 'No milestones'

    const prompt =
`USER GOAL
─────────
Title: ${goal.title}
Description: ${goal.description || '(none)'}
Type: ${goal.goal_type || 'monthly'}
Category: ${goal.category || '(none)'}
Deadline: ${goal.deadline || '(none)'}
Milestones: ${milestoneSummary}
Numeric target: ${goal.target_value != null ? `${goal.current_value ?? 0} / ${goal.target_value} ${goal.unit ?? ''}` : '(none)'}

ABOUT THE USER
──────────────
Bio: ${aboutMe.bio || '(none)'}
Occupation: ${aboutMe.occupation || '(none)'}
Interests: ${(aboutMe.interests ?? []).join(', ') || '(none)'}

USER'S LAST 7 DAYS OF ACTIVITY
──────────────────────────────
Prayers: ${fmtPrayers(prayerLogs ?? [])}
Habits:
${fmtHabits(habits ?? [], habitLogs ?? [])}
Tasks (last 7 days): ${fmtTasks(tasks ?? [])}
Active challenges: ${fmtChallenges(challenges ?? [])}

Now analyze: is the user actually working toward this goal? Be honest.
Return JSON only.`

    const result = await generateJSON<Omit<AlignmentResult, 'analyzed_at'>>(prompt, SYSTEM)

    if (!result || typeof result.score !== 'number') {
      return NextResponse.json({ error: 'AI returned an invalid response' }, { status: 502 })
    }

    const stored: AlignmentResult = {
      score: Math.max(0, Math.min(100, Math.round(result.score))),
      doing_well: result.doing_well || '',
      missing: result.missing || '',
      suggested_action: result.suggested_action || '',
      analyzed_at: new Date().toISOString(),
    }

    await supabase.from('goals').update({ ai_alignment: stored }).eq('id', goalId).eq('user_id', user.id)

    return NextResponse.json({ alignment: stored })
  } catch (err: any) {
    console.error('goal-alignment error:', err)
    return NextResponse.json({ error: err?.message || 'Failed' }, { status: 500 })
  }
}
