import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { grokChat } from '@/lib/grok'
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

    // Build message history for Grok
    const grokMessages: { role: 'system' | 'user' | 'assistant'; content: string }[] = [
      { role: 'system', content: ASK_NAFS_SYSTEM },
      ...messages.slice(-8).map((m: any) => ({
        role: m.role as 'user' | 'assistant',
        content: m.role === 'user' && m === messages[messages.length - 1] ? prompt : m.content,
      })),
    ]

    const reply = await grokChat(grokMessages)

    await supabase.from('ai_conversations').upsert({
      user_id: user.id,
      messages: [...messages, { role: 'assistant', content: reply }],
      created_at: new Date().toISOString(),
    })

    return NextResponse.json({ reply })
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 })
  }
}
