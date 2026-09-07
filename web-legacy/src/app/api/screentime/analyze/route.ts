import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiText, AiError, hasCloudflareAi } from '@/lib/ai'

export const maxDuration = 60

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    if (!hasCloudflareAi()) {
      return NextResponse.json({ error: 'AI is not configured on the server.' }, { status: 503 })
    }

    const body = await req.json()
    const { manualData } = body

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

    // Screenshot reading is gone: gpt-oss-20b is text-only, so there is no
    // vision model behind the Worker to send an image to.
    return NextResponse.json({
      error: 'Screenshot reading is no longer available. Enter your screen time manually.',
    }, { status: 415 })
  } catch (err: unknown) {
    if (err instanceof AiError) {
      return NextResponse.json({ error: err.message }, { status: err.status ?? 502 })
    }
    return NextResponse.json({ error: 'Analysis failed' }, { status: 500 })
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
