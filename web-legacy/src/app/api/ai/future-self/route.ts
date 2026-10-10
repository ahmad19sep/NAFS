import { NextRequest, NextResponse } from 'next/server'
import { authenticateRequest } from '@/lib/supabase/request'
import { aiText, aiProviderInfo, AiError } from '@/lib/ai'
import { ApiRouteError } from '@/lib/api-errors'
import { buildCoachContext } from '@/lib/coach-context'
import { withStrictIdempotency } from '@/lib/strict-idempotency'
import { LETTER_COLUMNS, FutureLetter, futureReplySystem, letterFingerprint, parseLetterReply, replySaved, validLetter } from '@/lib/future-self'

export async function POST(req: NextRequest) {
  const headers = { 'Cache-Control': 'no-store' }
  try {
    const { supabase, user, mode } = await authenticateRequest(req)
    const text = await req.text()
    if (text.length > 4_000) throw new ApiRouteError(413, 'invalid_request', 'The letter reply request is too long.')
    let body: unknown
    try { body = JSON.parse(text) } catch { throw new ApiRouteError(400, 'invalid_request', 'Provide a valid letter request.') }
    const input = parseLetterReply(body, mode === 'bearer')
    const read = async (): Promise<FutureLetter> => {
      const { data, error } = await supabase.from('future_self_letters').select(LETTER_COLUMNS)
        .eq('user_id', user.id).eq('id', input.letterId).maybeSingle()
      if (error) throw new ApiRouteError(503, 'data_unavailable', 'Your saved letter could not be loaded.')
      if (!data) throw new ApiRouteError(404, 'not_found', 'This saved letter is no longer available.')
      if (!validLetter(data, user.id, input.letterId)) throw new ApiRouteError(409, 'conflict', 'Reload a valid saved letter before requesting a reply.')
      return data
    }
    const letter = await read()
    const fingerprint = letterFingerprint(letter)
    if (input.fingerprint && input.fingerprint !== fingerprint) {
      throw new ApiRouteError(409, 'conflict', 'This letter changed. Reload its saved version before requesting a reply.')
    }
    // A committed reply recovers a lost response, including an unconfirmed ledger completion.
    if (replySaved(letter)) return NextResponse.json({ reply: letter.ai_reply_text, letter, replayed: true }, { headers })
    if (letter.ai_reply_text !== null || letter.delivered_at !== null) {
      throw new ApiRouteError(409, 'conflict', 'This letter has delivery data already. Reload its saved version.')
    }
    const context = await buildCoachContext(supabase, user.id)
    if (!context.profile || context.unavailableSources.includes('profile')) {
      throw new ApiRouteError(503, 'data_unavailable', 'Your account timezone and coaching preference could not be loaded.')
    }
    if (letter.target_deliver_date > context.today) throw new ApiRouteError(409, 'not_due', 'This letter will be ready on its revisit date.')
    const parameters = {
      p_letter_id: letter.id, p_content: letter.content, p_written_at: letter.written_at,
      p_target_date: letter.target_deliver_date,
    }
    // This read-only call checks the migration and guarded eligibility before paid generation.
    const ready = await supabase.rpc('complete_future_self_reply', { ...parameters, p_reply_text: null })
    if (ready.error) {
      if (['PGRST202', '42883', '42P01'].includes(ready.error.code)) {
        throw new ApiRouteError(503, 'not_configured', 'Future-self replies need a backend database update.')
      }
      throw new ApiRouteError(503, 'data_unavailable', 'Your saved letter could not be verified. Reconnect and reload before retrying.')
    }
    if (!ready.data?.length) throw new ApiRouteError(409, 'conflict', 'This letter changed. Reload before requesting a reply.')
    // All devices claim the same letter version, regardless of their client request IDs.
    const claimId = `future-self-v1:${letter.id}:${fingerprint}`
    const { result, replayed } = await withStrictIdempotency(supabase, user.id, claimId, {
      operation: 'future-self-reply-v1', letter_id: letter.id, expected_fingerprint: fingerprint,
    }, async () => {
      const providerInfo = aiProviderInfo()
      const reply = (await aiText('chat', JSON.stringify({
        letter: { content: letter.content, written_at: letter.written_at, revisit_date: letter.target_deliver_date },
        account_date: context.today, recorded_context: context.context, unavailable_sources: context.unavailableSources,
      }), futureReplySystem(context.deenOn))).trim()
      if (!reply || reply.length > 30_000) throw new ApiRouteError(502, 'invalid_response', 'The AI service returned an unusable reply.')
      let saved: FutureLetter | undefined
      try {
        const write = await supabase.rpc('complete_future_self_reply', { ...parameters, p_reply_text: reply })
        if (write.error) throw new Error('Unconfirmed reply write')
        saved = write.data?.[0]
      } catch {
        throw new ApiRouteError(503, 'result_unconfirmed', 'Reload your saved letter to recover the reply before retrying.')
      }
      if (!saved) throw new ApiRouteError(409, 'conflict', 'This letter changed while its reply was generated. Reload it.')
      // Do not release a claim after an authoritative write, even for an incompatible response.
      if (!validLetter(saved, user.id, letter.id) || letterFingerprint(saved) !== fingerprint || saved.ai_reply_text !== reply || !replySaved(saved)) {
        throw new ApiRouteError(503, 'result_unconfirmed', 'Reload your saved letter to recover the reply.')
      }
      let historySaved = false
      try {
        const { error } = await supabase.from('ai_reports').insert({
          user_id: user.id, type: 'letter_reply', content_md: reply, model_used: providerInfo.model,
        })
        historySaved = !error
      } catch { /* The saved letter remains authoritative. */ }
      return { reply, letter: saved, ...providerInfo,
        context_partial: context.unavailableSources.length > 0, history_saved: historySaved }
    })
    if (replayed) {
      const current = await read()
      if (letterFingerprint(current) !== fingerprint || current.ai_reply_text !== result.reply || current.delivered_at !== result.letter.delivered_at) {
        throw new ApiRouteError(409, 'conflict', 'This saved reply changed. Reload the letter to read its current version.')
      }
    }
    return NextResponse.json({ ...result, replayed }, { headers })
  } catch (error) {
    if (error instanceof ApiRouteError) return NextResponse.json({ code: error.code, error: error.message }, { status: error.status, headers })
    if (error instanceof AiError) return NextResponse.json({ code: 'ai_unavailable', error: 'The coach is temporarily unavailable. Your saved letter is retained.' }, { status: error.status === 429 ? 429 : 503, headers })
    return NextResponse.json({ code: 'backend_unavailable', error: 'The reply could not be confirmed. Reload your saved letter before retrying.' }, { status: 503, headers })
  }
}
