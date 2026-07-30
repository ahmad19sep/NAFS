import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { generateText } from '@/lib/gemini'
import { TRIBUNAL_SYSTEM, buildTribunalPrompt } from '@/lib/ai-prompts'

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const sevenDaysAgo = new Date()
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7)

    const { data: logs } = await supabase
      .from('daily_logs')
      .select('*')
      .eq('user_id', user.id)
      .gte('date', sevenDaysAgo.toISOString().split('T')[0])
      .order('date', { ascending: true })

    const { data: profile } = await supabase
      .from('users')
      .select('dreams(*)')
      .eq('id', user.id)
      .single()

    const thisWeekLogs = logs ?? []
    if (thisWeekLogs.length === 0) return NextResponse.json({ error: 'No logs this week' }, { status: 400 })

    const dream = (profile as any)?.dreams
    const weekScoreAvg = Math.round(thisWeekLogs.reduce((s, l) => s + l.identity_score, 0) / thisWeekLogs.length)
    const weekStart = thisWeekLogs[0]?.date

    const verdict = await generateText(buildTribunalPrompt({
      dream_statement: dream?.statement ?? 'Not set',
      week_score_avg: weekScoreAvg,
      last_week_score_avg: weekScoreAvg,
      weighted_hours_total: parseFloat(thisWeekLogs.reduce((s, l) => s + l.weighted_hours_today, 0).toFixed(1)),
      weighted_hours_required: 63,
      prayers_on_time: 0,
      prayers_total: 0,
      screen_time_total_hrs: Math.round(thisWeekLogs.reduce((s, l) => s + (l.screen_time_mins ?? 0), 0) / 60),
      sleep_avg_hrs: 7,
      biggest_drag_day: 'Friday',
      biggest_win_day: 'Tuesday',
      current_streaks: [],
      broken_streaks: [],
    }), TRIBUNAL_SYSTEM)

    await supabase.from('ai_reports').insert({
      user_id: user.id,
      type: 'tribunal',
      week_start: weekStart,
      content_md: verdict,
      model_used: 'gemini-2.0-flash',
    })

    return NextResponse.json({ verdict })
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 })
  }
}
