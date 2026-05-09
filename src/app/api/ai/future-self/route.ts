import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { generateText } from '@/lib/gemini'
import { FUTURE_SELF_SYSTEM, buildFutureSelfPrompt } from '@/lib/ai-prompts'

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { letterId } = await req.json()

    const { data: letter } = await supabase
      .from('future_self_letters')
      .select('*')
      .eq('id', letterId)
      .eq('user_id', user.id)
      .single()

    if (!letter) return NextResponse.json({ error: 'Letter not found' }, { status: 404 })

    const { data: profile } = await supabase
      .from('users')
      .select('dreams(*)')
      .eq('id', user.id)
      .single()

    const dream = (profile as any)?.dreams

    // Stats for context
    const { data: logs } = await supabase
      .from('daily_logs')
      .select('weighted_hours_today, identity_score')
      .eq('user_id', user.id)
      .order('date', { ascending: false })
      .limit(30)

    const monthsElapsed = Math.round(
      (Date.now() - new Date(letter.written_at).getTime()) / (1000 * 60 * 60 * 24 * 30)
    )
    const totalRequired = dream ? dream.total_hours_required * 1.8 : 5400
    const totalDone = (logs ?? []).reduce((s, l) => s + l.weighted_hours_today, 0)
    const progressPct = Math.round((totalDone / totalRequired) * 100)

    const prompt = buildFutureSelfPrompt({
      letter_text: letter.content,
      letter_date: letter.written_at,
      months_elapsed: monthsElapsed,
      dream_progress_pct: progressPct,
      expected_progress_pct_by_now: Math.round((monthsElapsed / (dream ? Math.ceil((new Date(dream.dream_date).getTime() - new Date(letter.written_at).getTime()) / (1000 * 60 * 60 * 24 * 30)) : 12)) * 100),
      best_recent_metric: 'identity score',
      worst_recent_metric: 'weighted hours',
    })

    const aiReply = await generateText(prompt, FUTURE_SELF_SYSTEM)

    // Save reply and mark delivered
    await supabase.from('future_self_letters').update({
      ai_reply_text: aiReply,
      delivered_at: new Date().toISOString(),
    }).eq('id', letterId)

    await supabase.from('ai_reports').insert({
      user_id: user.id,
      type: 'letter_reply',
      content_md: aiReply,
      model_used: 'gemini-2.0-flash',
    })

    return NextResponse.json({ reply: aiReply })
  } catch (err) {
    console.error('future-self error:', err)
    return NextResponse.json({ error: 'AI unavailable' }, { status: 500 })
  }
}
