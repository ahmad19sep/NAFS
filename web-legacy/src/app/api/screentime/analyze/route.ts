import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { geminiVision, generateText, safeParseJSON } from '@/lib/gemini'

export const maxDuration = 60

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

    if (!process.env.GEMINI_API_KEY) {
      return NextResponse.json({ error: 'GEMINI_API_KEY not set in .env.local' }, { status: 500 })
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

      const summary = await generateText(prompt, system)

      return NextResponse.json({
        total_mins: manualData.totalMins,
        apps: manualData.apps.map((a: any) => ({ ...a, category: guessCategory(a.app) })),
        summary,
      })
    }

    // --- Vision path (Gemini Flash supports vision natively) ---
    if (!base64) return NextResponse.json({ error: 'No image data' }, { status: 400 })

    const text = await geminiVision(base64, mimeType || 'image/jpeg', VISION_PROMPT)
    const parsed = safeParseJSON<any>(text)

    if (!parsed) {
      return NextResponse.json({
        error: 'Could not read screenshot. Try manual entry.',
      }, { status: 422 })
    }

    if (parsed.error) return NextResponse.json({ error: parsed.error }, { status: 422 })

    return NextResponse.json({
      total_mins: parsed.total_mins ?? 0,
      apps: parsed.apps ?? [],
      summary: parsed.summary ?? '',
    })
  } catch (err: any) {
    console.error('screentime analyze error:', err)
    return NextResponse.json({ error: err.message ?? 'Analysis failed' }, { status: 500 })
  }
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
function guessCategory(app: string): string {
  const name = app.toLowerCase()
  if (/instagram|tiktok|twitter|snapchat|facebook|x\.com/.test(name)) return 'social'
  if (/youtube|netflix|game|spotify|music|pubg|freefire/.test(name)) return 'entertainment'
  if (/whatsapp|message|phone|email|telegram|gmail/.test(name)) return 'communication'
  if (/chrome|safari|browser|maps|notes|calendar/.test(name)) return 'productivity'
  if (/duolingo|course|book|learn|study/.test(name)) return 'learning'
  return 'other'
}
