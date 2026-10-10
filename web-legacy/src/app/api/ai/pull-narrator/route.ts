import { NextRequest, NextResponse } from 'next/server'
import { authenticateRequest } from '@/lib/supabase/request'
import { ApiRouteError } from '@/lib/api-errors'
import { aiText, aiProviderInfo } from '@/lib/ai'
import { PULL_NARRATOR_SYSTEM, buildPullNarratorPrompt } from '@/lib/ai-prompts'

export async function POST(req: NextRequest) {
  try {
    const { supabase, user } = await authenticateRequest(req)

    const data = await req.json()
    const providerInfo = aiProviderInfo()
    const reply = await aiText('verdict', buildPullNarratorPrompt(data), PULL_NARRATOR_SYSTEM)

    await supabase.from('ai_reports').insert({
      user_id: user.id,
      type: 'pull',
      content_md: reply,
      model_used: providerInfo.model,
    })

    return NextResponse.json({ reply })
  } catch (err: any) {
    if (err instanceof ApiRouteError) return NextResponse.json({ error: err.message, code: err.code }, { status: err.status })
    return NextResponse.json({ error: err.message }, { status: 500 })
  }
}
