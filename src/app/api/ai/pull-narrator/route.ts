import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { grokText } from '@/lib/grok'
import { PULL_NARRATOR_SYSTEM, buildPullNarratorPrompt } from '@/lib/ai-prompts'

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const data = await req.json()
    const reply = await grokText(buildPullNarratorPrompt(data), PULL_NARRATOR_SYSTEM)

    await supabase.from('ai_reports').insert({
      user_id: user.id,
      type: 'pull',
      content_md: reply,
      model_used: 'grok-3-fast',
    })

    return NextResponse.json({ reply })
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 })
  }
}
