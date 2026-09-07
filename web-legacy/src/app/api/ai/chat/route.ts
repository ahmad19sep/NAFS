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

    const { context } = await buildCoachContext(supabase, user.id)

    // The records go in as BACKGROUND, on their own system turn — not stitched
    // onto the front of what the user typed.
    //
    // They used to be: one user turn reading "USER DATA: {5000 tokens of JSON}
    // ... USER QUESTION: <one line>". Someone opening with "I have fallen into
    // this habit, help me" had their words sitting at the bottom of a wall of
    // statistics, and got a reading of their consistency back. Their message is
    // now the last thing the model sees, which is where a question belongs.
    const backgroundPrompt = [
      "BACKGROUND — this person's own records, last 30 days.",
      'Reference material, not the subject of the conversation.',
      '',
      JSON.stringify(context, null, 1),
      '',
      'Draw on this only where it bears on what they actually say to you.',
      'Do not recite it, and do not open with statistics unless they asked about their numbers.',
    ].join('\n')

    const aiMessages: { role: 'system' | 'user' | 'assistant'; content: string }[] = [
      { role: 'system', content: ASK_ASCEND_SYSTEM },
      { role: 'system', content: backgroundPrompt },
      // The turns exactly as they were written. Both transports keep system
      // messages ahead of the turns, so the newest user message stays last.
      ...messages.slice(-8).map((m: any) => ({
        role: m.role as 'user' | 'assistant',
        content: String(m.content ?? ''),
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
