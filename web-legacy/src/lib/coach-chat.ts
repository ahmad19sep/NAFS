import { ApiRouteError } from './api-errors'
import { isValidRequestId } from './idempotency'

export interface CoachMessage { role: 'user' | 'assistant'; content: string }
export interface CoachChatInput {
  messages: CoachMessage[]; conversationId?: string; expectedUpdatedAt?: string | null; requestId?: string
}
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export const MAX_COACH_MESSAGES = 80

export function parseCoachChatInput(value: unknown): CoachChatInput {
  const fail = (): never => { throw new ApiRouteError(400, 'invalid_request', 'Send a valid conversation ending with your message.') }
  if (!value || typeof value !== 'object' || Array.isArray(value)) return fail()
  const body = value as Record<string, unknown>
  const rawMessages = body.messages
  if (!Array.isArray(rawMessages) || !rawMessages.length || rawMessages.length >= MAX_COACH_MESSAGES) return fail()
  let length = 0
  const messages = rawMessages.map((message: unknown, index: number): CoachMessage => {
    if (!message || typeof message !== 'object') return fail()
    const m = message as Record<string, unknown>
    if (m.role !== (index % 2 === 0 ? 'user' : 'assistant') || typeof m.content !== 'string' ||
        !m.content.trim() || m.content.length > (m.role === 'user' ? 4_000 : 12_000)) return fail()
    length += m.content.length
    // Preserve saved content exactly; trim only the newly composed message.
    return { role: m.role as CoachMessage['role'], content: index === rawMessages.length - 1 ? m.content.trim() : m.content }
  })
  if (messages[messages.length - 1].role !== 'user' || length > 180_000) return fail()
  const input: CoachChatInput = { messages }
  if (body.conversation_id !== undefined || body.expected_updated_at !== undefined || body.request_id !== undefined) {
    if (typeof body.conversation_id !== 'string' || !UUID.test(body.conversation_id) || !isValidRequestId(body.request_id) ||
        body.expected_updated_at !== null && (typeof body.expected_updated_at !== 'string' ||
          !/^\d{4}-\d{2}-\d{2}T/.test(body.expected_updated_at) || !Number.isFinite(Date.parse(body.expected_updated_at)))) return fail()
    input.conversationId = body.conversation_id
    input.expectedUpdatedAt = body.expected_updated_at as string | null
    input.requestId = body.request_id
  }
  return input
}

export function sameCoachMessages(a: unknown, b: CoachMessage[]): boolean {
  return Array.isArray(a) && a.length === b.length && a.every((m, i) =>
    m?.role === b[i].role && m?.content === b[i].content)
}
