import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiJSON } from '@/lib/ai'

interface StarterPack {
  summary: string
  suggested_tasks: Array<{ title: string; priority: 'low' | 'medium' | 'high' }>
  suggested_habits: Array<{
    name: string
    emoji: string
    type: 'simple' | 'counter' | 'duration'
    target_value?: number
    unit?: string
    time_target_mins?: number
  }>
  suggested_challenges: Array<{
    title: string
    emoji: string
    reason: string
    frequency: 'daily' | 'weekly' | 'monthly' | 'yearly'
    duration_days: number
  }>
  generated_at: string
}

const SYSTEM = `You are NAFS — an honest accountability coach for a Muslim user.
The user is creating a goal. Suggest a starter pack of THREE kinds of actions:
1. 2–3 concrete TASKS they can do TODAY toward this goal
2. 2–3 HABITS to build that compound over time
3. 1–2 CHALLENGES — bold commitments (e.g. "30-day no social media", "7-day Tahajjud streak")

Be specific to THIS goal, not generic.

Examples for "Get A+ in programming":
- Tasks: "Solve 3 LeetCode mediums today", "Watch 1 lecture on system design"
- Habits: DSA practice 60 min daily, GitHub commit every day
- Challenges: "30-day code-every-day", "7-day deep work block"

Examples for "Get to Jannat":
- Tasks: "Pray Tahajjud tonight", "Give sadqa to someone today"
- Habits: Quran 1 page daily, Dhikr after Fajr
- Challenges: "30-day Fajr in jamat", "7-day no backbiting"

Return ONLY valid JSON, no markdown:
{
  "summary": "1–2 sentences: what this goal really takes day-to-day",
  "suggested_tasks": [
    { "title": "Action for TODAY", "priority": "low|medium|high" }
  ],
  "suggested_habits": [
    { "name": "name", "emoji": "📚", "type": "simple|counter|duration",
      "target_value": <num>, "unit": "<unit>", "time_target_mins": <num> }
  ],
  "suggested_challenges": [
    { "title": "title", "emoji": "🎯", "reason": "why this builds toward the goal",
      "frequency": "daily|weekly|monthly", "duration_days": <num> }
  ]
}

Rules:
- 2–3 tasks (doable today)
- 2–3 habits (sustainable daily pattern)
- 1–2 challenges (time-bounded streak commitments)
- counter habits MUST include target_value + unit
- duration habits MUST include time_target_mins
- simple habits don't need target/unit
- challenge duration_days: 7 for weekly streak, 21–30 for daily habit-build, 90 for season
- Use real emojis
- Match the user's context (Muslim, age/occupation if available)`

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const body = await req.json()
    const { goalId, goalDraft } = body

    // Two modes: existing goal (goalId) OR preview (goalDraft from the create form)
    let goal: any = null
    let isDraft = false
    if (goalId) {
      const { data } = await supabase.from('goals').select('*').eq('id', goalId).eq('user_id', user.id).single()
      goal = data
      if (!goal) return NextResponse.json({ error: 'Goal not found' }, { status: 404 })
    } else if (goalDraft && typeof goalDraft === 'object' && goalDraft.title) {
      isDraft = true
      goal = {
        title: goalDraft.title,
        description: goalDraft.description ?? '',
        goal_type: goalDraft.goal_type ?? 'monthly',
        category: goalDraft.category ?? null,
        deadline: goalDraft.deadline ?? null,
        target_value: goalDraft.target_value ?? null,
        current_value: goalDraft.current_value ?? null,
        unit: goalDraft.unit ?? null,
      }
    } else {
      return NextResponse.json({ error: 'goalId or goalDraft required' }, { status: 400 })
    }

    const { data: profile } = await supabase
      .from('users').select('about_me').eq('id', user.id).maybeSingle()
    const about = (profile?.about_me ?? {}) as any

    const prompt = `USER GOAL
─────────
Title: ${goal.title}
Description: ${goal.description || '(none)'}
Type: ${goal.goal_type || 'monthly'}
Category: ${goal.category || '(none)'}
Deadline: ${goal.deadline || '(none)'}
${goal.target_value != null ? `Numeric target: ${goal.current_value ?? 0} / ${goal.target_value} ${goal.unit ?? ''}` : ''}

ABOUT THE USER
──────────────
${about.bio        ? `Bio: ${about.bio}` : ''}
${about.occupation ? `Occupation: ${about.occupation}` : ''}
${about.location   ? `Location: ${about.location}` : ''}
${(about.interests ?? []).length ? `Interests: ${(about.interests as string[]).join(', ')}` : ''}

Now generate the starter pack as JSON. Be specific to THIS goal.`

    const result = await aiJSON<Omit<StarterPack, 'generated_at'>>(prompt, SYSTEM)

    if (!result || !result.summary) {
      return NextResponse.json({ error: 'AI returned an unparseable response' }, { status: 502 })
    }

    const stored: StarterPack = {
      summary: result.summary,
      suggested_tasks: Array.isArray(result.suggested_tasks) ? result.suggested_tasks.slice(0, 5) : [],
      suggested_habits: Array.isArray(result.suggested_habits) ? result.suggested_habits.slice(0, 5) : [],
      suggested_challenges: Array.isArray(result.suggested_challenges) ? result.suggested_challenges.slice(0, 3) : [],
      generated_at: new Date().toISOString(),
    }

    // Persist only when we have a real goal
    if (!isDraft && goalId) {
      await supabase.from('goals').update({ ai_starter_pack: stored }).eq('id', goalId).eq('user_id', user.id)
    }

    return NextResponse.json({ starter: stored })
  } catch (err: any) {
    console.error('goal-starter error:', err)
    return NextResponse.json({ error: err?.message ?? 'Failed' }, { status: 500 })
  }
}
