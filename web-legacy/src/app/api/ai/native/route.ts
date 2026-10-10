import { NextRequest, NextResponse } from 'next/server'
import { authenticateRequest } from '@/lib/supabase/request'
import { ApiRouteError } from '@/lib/api-errors'
import { withStrictIdempotency } from '@/lib/strict-idempotency'
import { isValidRequestId } from '@/lib/idempotency'
import { POST as habitStarter } from '../habit-starter/route'
import { POST as challengeStarter } from '../challenge-starter/route'
import { POST as goalStarter } from '../goal-starter/route'
import { POST as goalAlignment } from '../goal-alignment/route'
import { POST as goalPlan } from '../goal-plan/route'
import { POST as healthRecommendation } from '../health-recommend/route'
import { POST as reportReview } from '../report-review/route'
import { POST as screenAnalysis } from '../../screentime/analyze/route'

export const maxDuration = 60
const handlers = { 'habit-starter': habitStarter, 'challenge-starter': challengeStarter, 'goal-starter': goalStarter,
  'goal-alignment': goalAlignment, 'goal-plan': goalPlan, 'health-recommend': healthRecommendation, 'report-review': reportReview, 'screen-analysis': screenAnalysis }

/** Durable native generation claim around existing schema-validated product handlers. */
export async function POST(req: NextRequest) {
  try {
    const { supabase, user } = await authenticateRequest(req)
    const body = await req.json().catch(() => null)
    if (!body || !isValidRequestId(body.requestId) || typeof body.action !== 'string' || !Object.hasOwn(handlers, body.action) ||
        typeof body.payload !== 'object' || body.payload === null || Array.isArray(body.payload) || JSON.stringify(body.payload).length > 20000) {
      throw new ApiRouteError(400, 'invalid_request', 'Choose a valid advice request.')
    }
    const handler = handlers[body.action as keyof typeof handlers]
    const headers = new Headers(req.headers)
    headers.set('x-nafs-request-id', body.requestId)
    const request = new NextRequest(req.url, { method: 'POST', headers, body: JSON.stringify(body.payload) })
    const { result, replayed } = await withStrictIdempotency(supabase, user.id, body.requestId,
      { action: body.action, payload: body.payload }, async () => {
        const response = await handler(request)
        const output = await response.json().catch(() => null)
        if (!response.ok) {
          if (output?.code === "generation_failed") throw new ApiRouteError(response.status, "unavailable", "Advice generation could not finish. Retry this request.")
          if (response.status >= 500 && ['goal-plan', 'screen-analysis'].includes(body.action)) throw new ApiRouteError(503, 'unavailable', 'Advice generation is temporarily unavailable. Retry this request.')
          if (response.status >= 500) throw new ApiRouteError(503, 'result_unconfirmed', 'Reload saved advice before retrying; the request could not be confirmed.')
          throw new ApiRouteError(response.status, 'invalid_request', response.status === 404 ? 'The selected record no longer exists.' : 'The advice request could not be completed.')
        }
        if (!output || typeof output !== 'object') throw new ApiRouteError(502, 'result_unconfirmed', 'The generated result could not be confirmed.')
        return output
      })
    return NextResponse.json({ result, replayed })
  } catch (error) {
    if (error instanceof ApiRouteError) return NextResponse.json({ error: error.message, code: error.code }, { status: error.status })
    return NextResponse.json({ error: 'Advice is temporarily unavailable.', code: 'unavailable' }, { status: 503 })
  }
}
