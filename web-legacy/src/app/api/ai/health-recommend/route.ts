import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiJSON } from '@/lib/ai'
import { computeBMI, sleepHoursBetween } from '@/lib/bmi'

interface HealthRecommendation {
  summary: string
  priorities: string[]
  suggested_goals: Array<{ title: string; type: 'weekly' | 'monthly' | 'yearly'; category: string }>
  suggested_habits: Array<{
    name: string
    emoji: string
    type: 'simple' | 'counter' | 'duration'
    target_value?: number
    unit?: string
    time_target_mins?: number
  }>
  generated_at: string
}

const SYSTEM = `You are NAFS — an honest, knowledgeable Muslim health & life coach.
The user just entered their health profile. Read it, then suggest a personalized
plan: a 1–2 sentence summary, 2–3 priorities, 2–3 specific goals to start, and
2–3 daily habits to build.

Be concrete and culturally aware (Islamic values, halal food, salah times).
Don't sugarcoat. Cite their numbers (BMI, sleep, etc).

Return ONLY valid JSON, no markdown:
{
  "summary": "1–2 sentences citing their BMI, sleep, etc.",
  "priorities": ["...", "...", "..."],
  "suggested_goals": [
    { "title": "Reach 10K steps daily for 30 days", "type": "monthly", "category": "health" },
    { "title": "Lose 5 kg", "type": "yearly", "category": "health" }
  ],
  "suggested_habits": [
    { "name": "Morning walk", "emoji": "🚶", "type": "duration", "time_target_mins": 20 },
    { "name": "8 glasses water", "emoji": "💧", "type": "counter", "target_value": 8, "unit": "glasses" },
    { "name": "Sleep before 11pm", "emoji": "🌙", "type": "simple" }
  ]
}

Rules:
- type for goal: "weekly" | "monthly" | "yearly"
- category for goal: "health" | "deen" | "personal"
- type for habit: "simple" (yes/no) | "counter" (numeric target) | "duration" (minutes)
- counter habits MUST have target_value and unit
- duration habits MUST have time_target_mins
- 2–3 of each (priorities, goals, habits)
- Use real emojis`

function ageFrom(birth_date?: string): number | null {
  if (!birth_date) return null
  const b = new Date(birth_date)
  if (isNaN(b.getTime())) return null
  const now = new Date()
  let age = now.getFullYear() - b.getFullYear()
  const m = now.getMonth() - b.getMonth()
  if (m < 0 || (m === 0 && now.getDate() < b.getDate())) age--
  return age >= 0 && age < 150 ? age : null
}

export async function POST(_req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { data: profile } = await supabase
      .from('users')
      .select('name, gender, height_cm, weight_kg, usual_sleep_time, usual_wake_time, about_me')
      .eq('id', user.id).maybeSingle()

    if (!profile?.height_cm || !profile?.weight_kg) {
      return NextResponse.json({ error: 'Complete health setup first' }, { status: 400 })
    }

    const bmi = computeBMI(profile.weight_kg, profile.height_cm)
    const sleepHrs = sleepHoursBetween(profile.usual_sleep_time, profile.usual_wake_time)
    const about = (profile.about_me ?? {}) as any
    const age = ageFrom(about.birth_date)

    const prompt = `USER HEALTH PROFILE
─────────
Name: ${profile.name || '(not set)'}
Gender: ${profile.gender || '(not set)'}
${age ? `Age: ${age}` : ''}
Height: ${profile.height_cm} cm
Weight: ${profile.weight_kg} kg
BMI: ${bmi ? `${bmi.value} (${bmi.label})` : 'unknown'}
Sleep schedule: ${profile.usual_sleep_time?.slice(0, 5) ?? '?'}–${profile.usual_wake_time?.slice(0, 5) ?? '?'} (${sleepHrs ?? '?'} hours)

ABOUT
${about.bio ? `Bio: ${about.bio}` : ''}
${about.occupation ? `Occupation: ${about.occupation}` : ''}
${about.location ? `Location: ${about.location}` : ''}
${(about.interests ?? []).length ? `Interests: ${(about.interests as string[]).join(', ')}` : ''}

Now generate the personalized health plan as JSON.`

    const result = await aiJSON<Omit<HealthRecommendation, 'generated_at'>>(prompt, SYSTEM)

    if (!result || !result.summary) {
      return NextResponse.json({ error: 'AI returned an unparseable response' }, { status: 502 })
    }

    const stored: HealthRecommendation = {
      summary: result.summary,
      priorities: Array.isArray(result.priorities) ? result.priorities.slice(0, 5) : [],
      suggested_goals: Array.isArray(result.suggested_goals) ? result.suggested_goals.slice(0, 5) : [],
      suggested_habits: Array.isArray(result.suggested_habits) ? result.suggested_habits.slice(0, 5) : [],
      generated_at: new Date().toISOString(),
    }

    await supabase.from('users')
      .update({ ai_health_recommendation: stored })
      .eq('id', user.id)

    return NextResponse.json({ recommendation: stored })
  } catch (err: any) {
    console.error('health-recommend error:', err)
    return NextResponse.json({ error: err?.message ?? 'Failed' }, { status: 500 })
  }
}
