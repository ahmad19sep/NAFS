import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { chatGemini, isRateLimit } from '@/lib/gemini'
import { chatGroq, hasGroq } from '@/lib/groq'
import { ASK_NAFS_SYSTEM, buildChatPrompt } from '@/lib/ai-prompts'

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { messages, userId } = await req.json()
    const userQuestion = messages[messages.length - 1]?.content ?? ''

    const ninetyDaysAgo = new Date()
    ninetyDaysAgo.setDate(ninetyDaysAgo.getDate() - 90)

    const [{ data: profile }, { data: logs }, { data: challenges }] = await Promise.all([
      supabase.from('users').select('dreams(*)').eq('id', user.id).single(),
      supabase.from('daily_logs')
        .select('date, weighted_hours_today, identity_score, todays_pull_days, mood, screen_time_mins')
        .eq('user_id', user.id)
        .gte('date', ninetyDaysAgo.toISOString().split('T')[0])
        .order('date', { ascending: false }),
      supabase.from('challenges')
        .select('title, current_streak, status')
        .eq('user_id', user.id)
        .eq('status', 'active'),
    ])

    const dream = (profile as any)?.dreams
    const logList = logs ?? []

    const summary = {
      total_days_logged: logList.length,
      avg_identity_score: logList.length ? Math.round(logList.reduce((s: number, l: any) => s + l.identity_score, 0) / logList.length) : 0,
    }

    const prompt = buildChatPrompt({
      dream: dream?.statement ?? 'Not set',
      last_90_days_summary: summary,
      current_streaks: (challenges ?? []).map((c: any) => `${c.title}: ${c.current_streak} days`),
      recent_logs_sample: logList.slice(0, 7),
      user_question: userQuestion,
    })

    // Build message history (system + last 8 turns, last user msg gets context-injected prompt)
    const aiMessages: { role: 'system' | 'user' | 'assistant'; content: string }[] = [
      { role: 'system', content: ASK_NAFS_SYSTEM },
      ...messages.slice(-8).map((m: any) => ({
        role: m.role as 'user' | 'assistant',
        content: m.role === 'user' && m === messages[messages.length - 1] ? prompt : m.content,
      })),
    ]

    // Try Gemini first; fall back to Groq if rate-limited and Groq key available
    let reply: string
    let provider: 'gemini' | 'groq' = 'gemini'
    const groqAvailable = hasGroq()
    try {
      reply = await chatGemini(aiMessages)
      console.log('[chat] Gemini OK')
    } catch (err: any) {
      const { rateLimited, retryAfterSec } = isRateLimit(err)
      console.warn('[chat] Gemini failed:', { status: err?.status, rateLimited, retryAfterSec, groqAvailable })

      if (rateLimited && groqAvailable) {
        try {
          reply = await chatGroq(aiMessages)
          provider = 'groq'
          console.log('[chat] Groq fallback OK')
        } catch (groqErr: any) {
          console.error('[chat] Groq fallback failed:', groqErr?.message)
          return NextResponse.json({
            error: `Both AI providers failed. Gemini: rate-limited (retry in ${retryAfterSec ?? 60}s). Groq: ${groqErr?.message ?? 'unknown error'}`,
          }, { status: 503 })
        }
      } else if (rateLimited) {
        return NextResponse.json({
          error: `Gemini is busy. Try again in ${retryAfterSec ?? 60} seconds.`,
          hint: groqAvailable ? undefined : 'Add GROQ_API_KEY to .env.local for automatic fallback.',
          retryAfter: retryAfterSec ?? 60,
        }, { status: 429 })
      } else {
        throw err
      }
    }

    await supabase.from('ai_conversations').upsert({
      user_id: user.id,
      messages: [...messages, { role: 'assistant', content: reply }],
      created_at: new Date().toISOString(),
    })

    return NextResponse.json({ reply, provider })
  } catch (err: any) {
    console.error('chat AI error:', err)
    return NextResponse.json({
      error: err?.message || 'AI chat failed',
      hint: 'Check GEMINI_API_KEY (and optionally GROQ_API_KEY) in .env.local. Restart dev server after changes.',
    }, { status: 500 })
  }
}
