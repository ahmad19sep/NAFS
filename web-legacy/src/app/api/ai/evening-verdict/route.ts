import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { generateText } from '@/lib/gemini'

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { eveningText, tasks, date } = await req.json()

    const { data: habitLogs } = await supabase
      .from('habit_logs')
      .select('*, habits(name, emoji)')
      .eq('user_id', user.id)
      .eq('date', date)

    const { data: goals } = await supabase
      .from('goals')
      .select('title, progress_pct')
      .eq('user_id', user.id)
      .limit(5)

    const completedHabits = (habitLogs ?? []).filter((l: any) => l.completed)
    const missedHabits = (habitLogs ?? []).filter((l: any) => !l.completed)
    const doneTasks = tasks.filter((t: any) => t.done)

    const system = `You are NAFS — a strict but caring AI accountability coach for a Muslim.
The user just told you about their day. Write a verdict in 3 parts:
1. ONE honest sentence on whether today was good or not — cite specific numbers.
2. What they did vs what they planned (tasks + habits).
3. ONE specific thing to do differently tomorrow. End with brief Islamic encouragement.
Rules: Direct, not harsh. Under 150 words. Reference their actual data.`

    const prompt = `USER'S EVENING REPORT: "${eveningText}"

TODAY'S DATA:
- Tasks planned: ${tasks.length}, completed: ${doneTasks.length}
- Habits completed: ${completedHabits.length}/${(habitLogs ?? []).length}
- Done: ${completedHabits.map((l: any) => l.habits?.name).join(', ') || 'none'}
- Missed: ${missedHabits.map((l: any) => l.habits?.name).join(', ') || 'none'}
- Active goals: ${(goals ?? []).map((g: any) => `${g.title} (${g.progress_pct}%)`).join(', ')}

Give the evening verdict.`

    const verdict = await generateText(prompt, system)

    await supabase.from('ai_reports').insert({
      user_id: user.id,
      type: 'pull',
      content_md: verdict,
      model_used: 'gemini-2.0-flash',
    })

    return NextResponse.json({ verdict })
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 })
  }
}
