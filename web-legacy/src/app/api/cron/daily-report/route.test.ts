import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NextRequest } from 'next/server'

const mocks = vi.hoisted(() => ({ cookieClient: vi.fn(), serviceClient: vi.fn(), ai: vi.fn(), whatsapp: vi.fn() }))
vi.mock('@/lib/supabase/server', () => ({ createClient: mocks.cookieClient }))
vi.mock('@/lib/supabase/service', () => ({ createServiceClient: mocks.serviceClient }))
vi.mock('@/lib/ai', () => ({ aiText: mocks.ai }))
vi.mock('@/lib/whatsapp', () => ({ sendWhatsApp: mocks.whatsapp, hasWhatsApp: () => false }))
import { GET } from './route'
import { GET as notifyTasks } from '../notify-tasks/route'
import { GET as tribunal } from '../tribunal/route'

describe.each([
  ['daily report', GET, 0],
  ['task notifications', notifyTasks, 1],
  ['tribunal', tribunal, 1],
] as const)('%s cron authorization', (_name, handler, authorizedServiceCalls) => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubEnv('CRON_SECRET', undefined)
    vi.stubEnv('VAPID_PRIVATE_KEY', '')
    vi.stubEnv('NEXT_PUBLIC_VAPID_PUBLIC_KEY', '')
    vi.stubEnv('VAPID_EMAIL', '')
    mocks.serviceClient.mockReturnValue(null)
  })
  afterEach(() => vi.unstubAllEnvs())

  const request = (authorization?: string) => new NextRequest('https://nafs.example/api/cron/daily-report', {
    headers: authorization ? { authorization } : {},
  })

  it.each([undefined, 'Bearer undefined', 'Bearer null', 'Bearer '])(
    'rejects cron requests while the secret is missing: %s', async authorization => {
      expect((await handler(request(authorization))).status).toBe(401)
      expect(mocks.serviceClient).not.toHaveBeenCalled()
      expect(mocks.cookieClient).not.toHaveBeenCalled()
      expect(mocks.ai).not.toHaveBeenCalled()
      expect(mocks.whatsapp).not.toHaveBeenCalled()
    },
  )

  it('requires the exact configured secret', async () => {
    vi.stubEnv('CRON_SECRET', 'synthetic-cron-secret')
    expect(mocks.serviceClient).not.toHaveBeenCalled()
    expect((await handler(request('Bearer wrong-secret'))).status).toBe(401)
    expect(mocks.serviceClient).not.toHaveBeenCalled()
    expect((await handler(request('Bearer synthetic-cron-secret'))).status).toBe(500)
    expect(mocks.serviceClient).toHaveBeenCalledTimes(authorizedServiceCalls)
    expect(mocks.ai).not.toHaveBeenCalled()
  })
})
