import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiStructured } from '@/lib/ai'
import { HEALTH_RECOMMENDATION_SCHEMA, statusForAiFailure } from '@/lib/ai-schemas'
import { computeBMI } from '@/lib/bmi'
import { todayInTZ, shiftDate } from '@/lib/utils'
import { buildHealthDigest, describeHealthDigest } from '@/lib/health-digest'

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

const SYSTEM = `You are Ascend — an honest, knowledgeable Muslim health & life coach.
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
- Use real emojis

Evidence rules — these matter more than the advice:
- Only cite numbers that appear in the data below. Never invent a figure.
- Where coverage is partial, say so ("on the 3 nights you recorded"). Never
  describe an unrecorded day as a zero, a skip, or a failure.
- Do not estimate calories or nutrients from food names, and do not diagnose.
  Speak in terms of habits and patterns, not clinical judgements.
- If something is listed as not known, leave it unknown and say what to record.`

/** Two weeks: long enough to show a pattern, short enough to be current. */
const DIGEST_DAYS = 14

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
      .select('gender, height_cm, weight_kg, about_me, timezone')
      .eq('id', user.id).maybeSingle()

    if (!profile?.height_cm || !profile?.weight_kg) {
      return NextResponse.json({ error: 'Complete health setup first' }, { status: 400 })
    }

    // AI-02: the plan reads the last 14 days of actual logs. It used to read
    // usual_sleep_time/usual_wake_time, profile fields whose setup UI is gone,
    // so for anyone who signed up since they were always null.
    // DATA-03: the window is the account's own calendar days. Deriving it from
    // the server's UTC date excluded the user's current day whenever local time
    // was ahead of UTC midnight.
    const endDate = todayInTZ(profile.timezone || 'UTC')
    const startDate = shiftDate(endDate, -(DIGEST_DAYS - 1))

    const { data: healthRows } = await supabase
      .from('health_logs')
      .select('date, sleep_sessions, sleep_hours, meals, water_glasses, steps, exercise_done, exercise_minutes, weight_kg')
      .eq('user_id', user.id)
      .gte('date', startDate)
      .lte('date', endDate)

    const digest = buildHealthDigest(healthRows ?? [], {
      startDate, endDate, eligibleDays: DIGEST_DAYS,
    })

    const bmi = computeBMI(profile.weight_kg, profile.height_cm)
    const about = (profile.about_me ?? {}) as any
    const age = ageFrom(about.birth_date)

    // Name and email are deliberately absent: the plan does not need identity.
    const prompt = `USER HEALTH PROFILE
─────────
Gender: ${profile.gender || '(not set)'}
${age ? `Age: ${age}` : ''}
Height: ${profile.height_cm} cm
Weight: ${profile.weight_kg} kg
BMI: ${bmi ? `${bmi.value} (${bmi.label})` : 'unknown'}

RECENT RECORDS
─────────
${describeHealthDigest(digest)}

ABOUT
${about.bio ? `Bio: ${about.bio}` : ''}
${about.occupation ? `Occupation: ${about.occupation}` : ''}
${(about.interests ?? []).length ? `Interests: ${(about.interests as string[]).join(', ')}` : ''}

Now generate the personalized health plan as JSON.`

    // AI-01: the shape is checked at runtime, with one corrective retry, so a
    // malformed reply produces an explicit failure instead of a silent null.
    const result = await aiStructured<Omit<HealthRecommendation, 'generated_at'>>(
      prompt, HEALTH_RECOMMENDATION_SCHEMA, SYSTEM,
    )

    if (!result.ok) {
      return NextResponse.json({ error: result.message }, { status: statusForAiFailure(result.code) })
    }

    const stored: HealthRecommendation = {
      ...result.data,
      priorities: result.data.priorities.slice(0, 5),
      suggested_goals: result.data.suggested_goals.slice(0, 5),
      suggested_habits: result.data.suggested_habits.slice(0, 5),
      generated_at: new Date().toISOString(),
    }

    await supabase.from('users')
      .update({ ai_health_recommendation: stored })
      .eq('id', user.id)

    return NextResponse.json({ recommendation: stored })
  } catch {
    return NextResponse.json({ error: 'Could not generate a plan right now.' }, { status: 500 })
  }
}
