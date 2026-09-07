import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { aiChat, AiError } from '@/lib/ai'
import { buildCoachContext } from '@/lib/coach-context'
import { ASK_ASCEND_SYSTEM } from '@/lib/ai-prompts'

// The coach reasons over a month of context before it answers.
export const maxDuration = 60

// Ask Ascend — the coach answers from the user's REAL data across every
// feature: habits, prayers (if faith mode), tasks, health, goals, challenges,
// dream trajectory, and what the user has told it before. The context is
// built by lib/coach-context, shared with the growth review.
export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { messages } = await req.json()
    const userQuestion = messages[messages.length - 1]?.content ?? ''

    const { context } = await buildCoachContext(supabase, user.id)

    const contextPrompt =
      `USER DATA (real, last 30 days):\n${JSON.stringify(context, null, 1)}\n\nUSER QUESTION: ${userQuestion}`

    const aiMessages: { role: 'system' | 'user' | 'assistant'; content: string }[] = [
      { role: 'system', content: ASK_ASCEND_SYSTEM },
      ...messages.slice(-8).map((m: any, i: number, arr: any[]) => ({
        role: m.role as 'user' | 'assistant',
        content: m.role === 'user' && i === arr.length - 1 ? contextPrompt : m.content,
      })),
    ]

    const reply = await aiChat(aiMessages)

    await supabase.from('ai_conversations').upsert({
      user_id: user.id,
      messages: [...messages, { role: 'assistant', content: reply }],
      created_at: new Date().toISOString(),
    })

    return NextResponse.json({ reply })
  } catch (err: unknown) {
    // AiError messages are written for the user; anything else stays generic
    // so internals are never echoed back to the client.
    if (err instanceof AiError) {
      return NextResponse.json({ error: err.message }, { status: err.status ?? 502 })
    }
    return NextResponse.json({ error: 'AI chat failed' }, { status: 500 })
  }
}
