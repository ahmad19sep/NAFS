import { NextRequest, NextResponse } from 'next/server'
import { authenticateRequest } from '@/lib/supabase/request'
import { ApiRouteError } from '@/lib/api-errors'
import { aiText, AiError, hasAi } from '@/lib/ai'

export const maxDuration = 60

export async function POST(req: NextRequest) {
  try {
    const { supabase, user } = await authenticateRequest(req)

    if (!hasAi()) {
      return NextResponse.json({ error: 'AI is not configured on the server.' }, { status: 503 })
    }

    // Screen time arrives as numbers the user typed. Reading it off a
    // screenshot was tried and dropped: the small vision models on Workers AI
    // misread durations, and a confident wrong figure is worse than none.
    const { manualData } = await req.json()
    if (!manualData || !Number.isInteger(manualData.totalMins) || manualData.totalMins < 0 || manualData.totalMins > 1440 || !Array.isArray(manualData.apps) || manualData.apps.length > 40 ||
        manualData.apps.some((app: any) => typeof app.app !== "string" || !app.app.trim() || app.app.length > 80 || !Number.isInteger(app.minutes) || app.minutes < 0 || app.minutes > 1440) ||
        manualData.apps.reduce((total: number, app: any) => total + app.minutes, 0) > manualData.totalMins) {
      return NextResponse.json({ error: 'No screen time data supplied' }, { status: 400 })
    }

    {
      const system = `You are Ascend, an AI accountability coach. The user shared their phone screen time data.
Analyze it honestly: what was wasted, what was productive, which apps dominated.
Keep it under 100 words. Be direct. End with one specific recommendation.`

      const prompt = `Screen time data:
Total: ${manualData.totalMins} minutes
Apps: ${manualData.apps.map((a: any) => `${a.app}: ${a.minutes}m`).join(', ')}

Give an honest analysis.`

      const summary = await aiText('verdict', prompt, system)

      return NextResponse.json({
        total_mins: manualData.totalMins,
        apps: manualData.apps.map((a: any) => ({ ...a, category: guessCategory(a.app) })),
        summary,
      })
    }
  } catch (err: unknown) {
    if (err instanceof ApiRouteError) return NextResponse.json({ error: err.message, code: err.code }, { status: err.status })
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
