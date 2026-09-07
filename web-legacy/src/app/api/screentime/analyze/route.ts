import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiText, aiVision, safeParseJSON, AiError, hasCloudflareAi } from '@/lib/ai'

export const maxDuration = 60

const VISION_PROMPT = `This is a phone screen time screenshot (iPhone Screen Time or Android Digital Wellbeing).
Read every app name and its exact time shown.

Return ONLY valid JSON, no markdown:
{
  "total_mins": <integer, whole day total in minutes>,
  "apps": [
    { "app": "App Name", "minutes": <integer>, "category": "social|entertainment|productivity|communication|learning|other" }
  ],
  "summary": "<2-3 sentence honest verdict: what was wasted vs productive, which app dominated, one recommendation>"
}

Convert every duration to whole minutes, so "1h 20m" becomes 80.
If this is NOT a screen time screenshot: {"error": "Not a screen time screenshot"}`

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    if (!hasCloudflareAi()) {
      return NextResponse.json({ error: 'AI is not configured on the server.' }, { status: 503 })
    }

    const body = await req.json()
    const { base64, manualData } = body

    // --- Manual entry path (no vision needed) ---
    if (manualData) {
      const system = `You are Ascend, an AI accountability coach. The user shared their phone screen time data.
Analyze it honestly: what was wasted, what was productive, which apps dominated.
Keep it under 100 words. Be direct. End with one specific recommendation.`

      const prompt = `Screen time data:
Total: ${manualData.total}
Apps: ${manualData.apps.map((a: any) => `${a.app}: ${a.minutes}m`).join(', ')}

Give an honest analysis.`

      const summary = await aiText('verdict', prompt, system)

      return NextResponse.json({
        total_mins: manualData.totalMins,
        apps: manualData.apps.map((a: any) => ({ ...a, category: guessCategory(a.app) })),
        summary,
      })
    }

    // --- Screenshot path. Needs the Worker's optional /vision route; without
    //     it aiVision throws 404 and the client falls back to manual entry.
    if (!base64) return NextResponse.json({ error: 'No image data' }, { status: 400 })

    const text = await aiVision(base64, VISION_PROMPT)
    const parsed = safeParseJSON<{
      total_mins?: number
      apps?: { app: string; minutes: number; category?: string }[]
      summary?: string
      error?: string
    }>(text)

    if (!parsed) {
      return NextResponse.json({
        error: 'Could not read the screenshot. Enter your screen time manually.',
      }, { status: 422 })
    }
    if (parsed.error) return NextResponse.json({ error: parsed.error }, { status: 422 })

    // A reply with no apps is a failed read, not an empty day.
    const apps = (parsed.apps ?? []).filter((a) => a?.app && Number(a.minutes) > 0)
    if (apps.length === 0) {
      return NextResponse.json({
        error: 'No apps could be read from the screenshot. Enter your screen time manually.',
      }, { status: 422 })
    }

    return NextResponse.json({
      total_mins: parsed.total_mins ?? apps.reduce((s, a) => s + Number(a.minutes), 0),
      apps: apps.map((a) => ({
        app: a.app,
        minutes: Number(a.minutes),
        category: a.category ?? guessCategory(a.app),
      })),
      summary: parsed.summary ?? '',
    })
  } catch (err: unknown) {
    if (err instanceof AiError) {
      return NextResponse.json({ error: err.message }, { status: err.status ?? 502 })
    }
    return NextResponse.json({ error: 'Analysis failed' }, { status: 500 })
  }
}

function guessCategory(app: string): string {
  const name = app.toLowerCase()
  if (/instagram|tiktok|twitter|snapchat|facebook|x\.com/.test(name)) return 'social'
  if (/youtube|netflix|game|spotify|music|pubg|freefire/.test(name)) return 'entertainment'
  if (/whatsapp|message|phone|email|telegram|gmail/.test(name)) return 'communication'
  if (/chrome|safari|browser|maps|notes|calendar/.test(name)) return 'productivity'
  if (/duolingo|course|book|learn|study/.test(name)) return 'learning'
  return 'other'
}
