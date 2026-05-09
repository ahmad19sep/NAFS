import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { grokVision, grokText } from '@/lib/grok'

export const maxDuration = 60

// Try multiple vision model names in order
const VISION_MODELS = ['grok-2-vision', 'grok-vision-beta', 'grok-2-vision-1212']

const VISION_PROMPT = `This is a phone screen time screenshot. Extract every app and exact time shown.

Return ONLY valid JSON, no markdown:
{
  "total_mins": <integer>,
  "apps": [
    { "app": "App Name", "minutes": <integer>, "category": "social|entertainment|productivity|communication|learning|other" }
  ],
  "summary": "<2-3 sentence honest verdict: what was wasted vs productive, which app dominated, one recommendation>"
}

If NOT a screen time screenshot: {"error": "Not a screen time screenshot"}`

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    if (!process.env.GROK_API_KEY) {
      return NextResponse.json({ error: 'GROK_API_KEY not set in .env.local' }, { status: 500 })
    }

    const body = await req.json()
    const { base64, mimeType, manualData } = body

    // --- Manual entry path (no vision needed) ---
    if (manualData) {
      const system = `You are NAFS, an AI accountability coach. The user shared their phone screen time data.
Analyze it honestly: what was wasted, what was productive, which apps dominated.
Keep it under 100 words. Be direct. End with one specific recommendation.`

      const prompt = `Screen time data:
Total: ${manualData.total}
Apps: ${manualData.apps.map((a: any) => `${a.app}: ${a.minutes}m`).join(', ')}

Give an honest analysis.`

      const summary = await grokText(prompt, system)

      return NextResponse.json({
        total_mins: manualData.totalMins,
        apps: manualData.apps.map((a: any) => ({ ...a, category: guessCategory(a.app) })),
        summary,
      })
    }

    // --- Vision path (try each model until one works) ---
    if (!base64) return NextResponse.json({ error: 'No image data' }, { status: 400 })

    let text = ''
    let visionWorked = false

    for (const model of VISION_MODELS) {
      try {
        text = await grokVision(base64, mimeType || 'image/jpeg', VISION_PROMPT, model)
        visionWorked = true
        break
      } catch (e: any) {
        // Try next model
        if (!e.message?.includes('not found') && !e.message?.includes('400')) throw e
      }
    }

    if (!visionWorked) {
      return NextResponse.json({
        error: 'vision_not_available',
        message: 'Vision models are not available on your Grok plan. Use manual entry instead.',
      }, { status: 422 })
    }

    const cleaned = text.replace(/```json\s*/gi, '').replace(/```\s*/gi, '').trim()
    let parsed: any
    try {
      parsed = JSON.parse(cleaned)
    } catch {
      const match = cleaned.match(/\{[\s\S]*\}/)
      if (!match) return NextResponse.json({ error: 'Could not read screenshot. Try manual entry.' }, { status: 422 })
      parsed = JSON.parse(match[0])
    }

    if (parsed.error) return NextResponse.json({ error: parsed.error }, { status: 422 })

    return NextResponse.json({
      total_mins: parsed.total_mins ?? 0,
      apps: parsed.apps ?? [],
      summary: parsed.summary ?? '',
    })
  } catch (err: any) {
    return NextResponse.json({ error: err.message ?? 'Analysis failed' }, { status: 500 })
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
