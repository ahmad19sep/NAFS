import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { grokText } from '@/lib/grok'

export async function POST(req: NextRequest) {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const { title, description, deadline, linkedHabits } = await req.json()
    const daysLeft = deadline ? Math.ceil((new Date(deadline).getTime() - Date.now()) / 86400000) : null

    const system = `You are a strategic life coach. Give practical, actionable plans. Under 200 words. No fluff.`

    const prompt = `GOAL: ${title}
DESCRIPTION: ${description || 'Not specified'}
DEADLINE: ${deadline ? `${deadline} (${daysLeft} days away)` : 'No deadline'}
LINKED HABITS: ${linkedHabits?.length > 0 ? linkedHabits.join(', ') : 'None'}

Write a plan with:
1. The 3 most important actions in the next 7 days
2. The daily habit that will move the needle most
3. How to know if they're on track in 30 days`

    const plan = await grokText(prompt, system)
    return NextResponse.json({ plan })
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 })
  }
}
