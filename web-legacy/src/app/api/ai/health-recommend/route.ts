import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiStructured, type Schema } from '@/lib/ai'
import { computeBMI } from '@/lib/bmi'
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

/**
 * The shape the model must actually return. Enum members are listed so an
 * invented goal type or habit type is rejected rather than stored and later
 * used to create a malformed habit.
 */
const RECOMMENDATION_SCHEMA: Schema = {
  kind: 'object',
  fields: {
    summary: { kind: 'string', minLength: 10, maxLength: 600 },
    priorities: {
      kind: 'array', minItems: 1, maxItems: 6,
      of: { kind: 'string', minLength: 3, maxLength: 200 },
    },
    suggested_goals: {
      kind: 'array', minItems: 1, maxItems: 6,
      of: {
        kind: 'object',
        fields: {
          title: { kind: 'string', minLength: 3, maxLength: 120 },
          type: { kind: 'enum', values: ['weekly', 'monthly', 'yearly'] },
          category: { kind: 'enum', values: ['health', 'deen', 'personal'] },
        },
      },
    },
    suggested_habits: {
      kind: 'array', minItems: 1, maxItems: 6,
      of: {
        kind: 'object',
        optional: ['target_value', 'unit', 'time_target_mins'],
        fields: {
          name: { kind: 'string', minLength: 2, maxLength: 80 },
          emoji: { kind: 'string', minLength: 1, maxLength: 8 },
          type: { kind: 'enum', values: ['simple', 'counter', 'duration'] },
          target_value: { kind: 'number', min: 1, max: 1000 },
          unit: { kind: 'string', maxLength: 24 },
          time_target_mins: { kind: 'number', min: 1, max: 600, int: true },
        },
      },
    },
  },
}

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
      .select('gender, height_cm, weight_kg, about_me')
      .eq('id', user.id).maybeSingle()

    if (!profile?.height_cm || !profile?.weight_kg) {
      return NextResponse.json({ error: 'Complete health setup first' }, { status: 400 })
    }

    // AI-02: the plan reads the last 14 days of actual logs. It used to read
    // usual_sleep_time/usual_wake_time, profile fields whose setup UI is gone,
    // so for anyone who signed up since they were always null.
    const endDate = new Date()
    const startDate = new Date(endDate)
    startDate.setDate(startDate.getDate() - (DIGEST_DAYS - 1))
    const iso = (d: Date) => d.toISOString().slice(0, 10)

    const { data: healthRows } = await supabase
      .from('health_logs')
      .select('date, sleep_sessions, sleep_hours, meals, water_glasses, steps, exercise_done, exercise_minutes, weight_kg')
      .eq('user_id', user.id)
      .gte('date', iso(startDate))
      .lte('date', iso(endDate))

    const digest = buildHealthDigest(healthRows ?? [], {
      startDate: iso(startDate),
      endDate: iso(endDate),
      eligibleDays: DIGEST_DAYS,
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
      prompt, RECOMMENDATION_SCHEMA, SYSTEM,
    )

    if (!result.ok) {
      const status = result.code === 'rate_limited' ? 429
        : result.code === 'unauthorized' || result.code === 'not_configured' ? 503
        : 502
      return NextResponse.json({ error: result.message }, { status })
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
