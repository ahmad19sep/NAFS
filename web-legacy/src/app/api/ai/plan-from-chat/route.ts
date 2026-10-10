import { NextRequest, NextResponse } from 'next/server'
import { authenticateRequest } from '@/lib/supabase/request'
import { CHAT_PLAN_SYSTEM } from '@/lib/ai-prompts'
import { ApiRouteError } from '@/lib/api-errors'
import { chatSource, generateRecovery, planningRequestId } from '@/lib/recovery-planning'

export const maxDuration = 60

export async function POST(req: NextRequest) {
  const headers = { 'Cache-Control': 'no-store' }
  try {
    const auth = await authenticateRequest(req)
    const text = await req.text()
    if (text.length > (auth.mode === 'bearer' ? 4_000 : 500_000)) throw new ApiRouteError(413, 'invalid_request', 'This conversation request is too long.')
    let body: any
    try { body = JSON.parse(text) } catch { throw new ApiRouteError(400, 'invalid_request', 'Provide a valid conversation request.') }
    if (!body || typeof body !== 'object' || Array.isArray(body)) throw new ApiRouteError(400, 'invalid_request', 'Provide a valid conversation request.')
    const source = chatSource(body, auth.mode === 'bearer')
    const requestId = planningRequestId(body.request_id, auth.mode === 'bearer')
    return NextResponse.json(await generateRecovery(auth, source, requestId, CHAT_PLAN_SYSTEM), { headers })
  } catch (error) {
    if (error instanceof ApiRouteError) return NextResponse.json({ code: error.code, error: error.message }, { status: error.status, headers })
    return NextResponse.json({ code: 'backend_unavailable', error: 'The plan could not be confirmed. Retry the same request.' }, { status: 503, headers })
  }
}
