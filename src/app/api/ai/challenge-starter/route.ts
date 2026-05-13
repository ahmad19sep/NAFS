import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiJSON } from '@/lib/ai'

interface ChallengeStarterPack {
  why_this_works: string                       // Encouragement based on user history
  hardest_obstacle: string                     // Predicted toughest part + how to handle
  daily_anchor: string                         // The single non-negotiable daily action
  supporting_habits: Array<{
    name: string; emoji: string
    type: 'simple' | 'counter' | 'duration'
    target_value?: number; unit?: string; time_target_mins?: number
  }>
  generated_at: string
}

const SYSTEM = `You are NAFS — an honest, sharp accountability coach for a Muslim
user who just committed to a new challenge. Look at their past challenges and
habits, then prepare them.

Return ONLY valid JSON, no markdown:
{
  "why_this_works": "Why this challenge is winnable for THIS user — cite their history (1-2 sentences)",
  "hardest_obstacle": "Predict what will break them + how to handle it (1-2 sentences)",
  "daily_anchor": "The ONE non-negotiable action they must do every period to win",
  "supporting_habits": [
    { "name": "name", "emoji": "📚", "type": "simple|counter|duration",
      "target_value": <num>, "unit": "<unit>", "time_target_mins": <num> }
  ]
}

Rules:
- counter habits MUST include target_value + unit
- duration habits MUST include time_target_mins
- 1-2 supporting habits (the ones that make the challenge easier to win)
- Be specific to THIS challenge — don't be generic
- Use real emojis`

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { challengeId } = await req.json()
    if (!challengeId) return NextResponse.json({ error: 'challengeId required' }, { status: 400 })

    const { data: challenge } = await supabase
      .from('challenges').select('*').eq('id', challengeId).eq('user_id', user.id).single()
    if (!challenge) return NextResponse.json({ error: 'Challenge not found' }, { status: 404 })

    const [{ data: pastChallenges }, { data: habits }, { data: profile }] = await Promise.all([
      supabase.from('challenges').select('title, status, current_streak, longest_streak, frequency, duration_days')
        .eq('user_id', user.id).neq('id', challengeId),
      supabase.from('habits').select('name, type, current_streak').eq('user_id', user.id).eq('is_active', true),
      supabase.from('users').select('about_me').eq('id', user.id).maybeSingle(),
    ])

    const about = (profile?.about_me ?? {}) as any

    const past = ((pastChallenges ?? []) as any[]).slice(0, 8).map((c) =>
      `${c.title} (${c.frequency}, ${c.duration_days}d): ${c.status}, best streak ${c.longest_streak}`
    ).join('\n')

    const habitList = ((habits ?? []) as any[]).slice(0, 8).map((h) =>
      `${h.name} (streak ${h.current_streak})`
    ).join(', ')

    const prompt = `NEW CHALLENGE
─────────────
Title: ${challenge.title}
Why (their reason): ${challenge.description || '(none)'}
Frequency: ${challenge.frequency}
Duration: ${challenge.duration_days} days
Photo proof required: ${challenge.requires_photo ? 'yes' : 'no'}

PAST CHALLENGES
${past || '(none — this is their first challenge)'}

CURRENT HABITS
${habitList || '(none)'}

USER CONTEXT
${about.bio || ''}
${about.occupation ? `Occupation: ${about.occupation}` : ''}

Now generate the starter pack as JSON. Be honest about likely obstacles.`

    const result = await aiJSON<Omit<ChallengeStarterPack, 'generated_at'>>(prompt, SYSTEM)

    if (!result || !result.why_this_works) {
      return NextResponse.json({ error: 'AI returned an unparseable response' }, { status: 502 })
    }

    const stored: ChallengeStarterPack = {
      why_this_works: result.why_this_works,
      hardest_obstacle: result.hardest_obstacle || '',
      daily_anchor: result.daily_anchor || '',
      supporting_habits: Array.isArray(result.supporting_habits) ? result.supporting_habits.slice(0, 3) : [],
      generated_at: new Date().toISOString(),
    }

    await supabase.from('challenges').update({ ai_starter_pack: stored }).eq('id', challengeId).eq('user_id', user.id)

    return NextResponse.json({ starter: stored })
  } catch (err: any) {
    console.error('challenge-starter error:', err)
    return NextResponse.json({ error: err?.message ?? 'Failed' }, { status: 500 })
  }
}
