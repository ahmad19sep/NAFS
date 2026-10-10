import { NextRequest, NextResponse } from 'next/server'
import { authenticateRequest } from '@/lib/supabase/request'
import { aiText, aiProviderInfo, AiError } from '@/lib/ai'
import { ApiRouteError } from '@/lib/api-errors'
import { parseVerdictInput, sameSavedReview, nextReviewVersion, verdictSystemPrompt } from '@/lib/evening-verdict'
import { withStrictIdempotency } from '@/lib/strict-idempotency'
import { todayInTZ } from '@/lib/utils'

export const maxDuration = 60
const CHECKIN_COLUMNS = 'id,user_id,date,tasks,evening_text,ai_verdict,updated_at'

export async function POST(req: NextRequest) {
  try {
    const { supabase, user, mode } = await authenticateRequest(req)
    const text = await req.text()
    if (text.length > 30_000) throw new ApiRouteError(413, 'invalid_request', 'This evening review is too long.')
    let body: unknown
    try { body = JSON.parse(text) } catch { throw new ApiRouteError(400, 'invalid_request', 'Provide a valid review request.') }
    const input = parseVerdictInput(body)
    if (mode === 'bearer' && !input.checkinId) {
      throw new ApiRouteError(400, 'invalid_request', 'Save your check-in before requesting an AI review.')
    }

    const generate = async () => {
      const { data: profile, error: profileError } = await supabase.from('users')
        .select('timezone,deen_enabled').eq('id', user.id).single()
      if (profileError || !profile) throw new ApiRouteError(503, 'data_unavailable', 'Your coaching context could not be loaded.')
      let saved: any = null
      if (input.checkinId) {
        const { data, error } = await supabase.from('daily_checkins').select(CHECKIN_COLUMNS)
          .eq('user_id', user.id).eq('id', input.checkinId).eq('date', input.date).maybeSingle()
        if (error) throw new ApiRouteError(503, 'data_unavailable', 'Your check-in could not be loaded.')
        if (!data || input.date !== todayInTZ(profile.timezone || 'Asia/Karachi') || !sameSavedReview(data, input)) {
          throw new ApiRouteError(409, 'conflict', 'Your check-in changed. Reload the latest saved entry before generating a review.')
        }
        saved = data
      }
      const [habits, goals] = await Promise.all([
        supabase.from('habit_logs').select('completed,habits(name)').eq('user_id', user.id).eq('date', input.date),
        supabase.from('goals').select('title,progress_pct').eq('user_id', user.id).limit(5),
      ])
      if (habits.error || goals.error) throw new ApiRouteError(503, 'data_unavailable', 'Your coaching context could not be loaded.')
      const logs = habits.data ?? []
      const prompt = JSON.stringify({
        accountDate: input.date, eveningNotes: input.eveningText, priorities: input.tasks,
        completedPriorities: input.tasks.filter(t => t.done).length,
        recordedHabitLogs: logs.length, completedHabitLogs: logs.filter(l => l.completed).length,
        habitLogs: logs, goals: goals.data ?? [],
      })
      const providerInfo = aiProviderInfo()
      const verdict = (await aiText('verdict', prompt, verdictSystemPrompt(profile.deen_enabled === true))).trim()
      if (!verdict || verdict.length > 30_000) throw new ApiRouteError(502, 'invalid_response', 'The AI service returned an unusable review. Try again.')
      let checkin: any = null
      if (saved) {
        const { data, error } = await supabase.from('daily_checkins').update({
          ai_verdict: verdict, updated_at: nextReviewVersion(saved.updated_at),
        }).eq('user_id', user.id).eq('id', saved.id).eq('date', saved.date)
          .eq('updated_at', saved.updated_at).select(CHECKIN_COLUMNS).maybeSingle()
        if (error) throw new ApiRouteError(503, 'save_failed', 'The AI review could not be saved. Reload your saved entry before retrying.')
        if (!data) throw new ApiRouteError(409, 'conflict', 'Your check-in changed while the review was generated. Reload before trying again.')
        checkin = data
      }
      // The check-in is authoritative; secondary history failure must not release its claim.
      let historySaved = false
      try {
        const { error } = await supabase.from('ai_reports').insert({
          user_id: user.id, type: 'pull', content_md: verdict, model_used: providerInfo.model,
        })
        historySaved = !error
      } catch { /* Saved native check-in remains recoverable. */ }
      return { verdict, checkin, ...providerInfo, history_saved: historySaved }
    }

    if (input.requestId) {
      const { result, replayed } = await withStrictIdempotency(supabase, user.id, input.requestId, {
        operation: 'evening-verdict-v1', ...input,
      }, generate)
      if (replayed) {
        const { data, error } = await supabase.from('daily_checkins').select('updated_at')
          .eq('user_id', user.id).eq('id', input.checkinId!).eq('date', input.date).maybeSingle()
        if (error) throw new ApiRouteError(503, 'data_unavailable', 'Your saved review could not be loaded.')
        if (!data || data.updated_at !== result.checkin?.updated_at) {
          throw new ApiRouteError(409, 'conflict', 'Your check-in changed after this review. Reload the latest saved entry.')
        }
      }
      return NextResponse.json({ ...result, replayed }, { headers: { 'Cache-Control': 'no-store' } })
    }
    // Cookie clients still receive {verdict}; their existing save flow is retained.
    return NextResponse.json(await generate(), { headers: { 'Cache-Control': 'no-store' } })
  } catch (error) {
    if (error instanceof ApiRouteError) return NextResponse.json({ code: error.code, error: error.message }, { status: error.status })
    if (error instanceof AiError) return NextResponse.json({
      code: 'ai_unavailable', error: 'AI review is temporarily unavailable. Your saved notes are retained.',
    }, { status: error.status === 429 ? 429 : 503 })
    return NextResponse.json({ code: 'backend_unavailable', error: 'The review could not be completed. Reload your saved entry before retrying.' }, { status: 503 })
  }
}
