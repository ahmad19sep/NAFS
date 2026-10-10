import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NextRequest } from 'next/server'
const mocks = vi.hoisted(() => ({ authenticate: vi.fn(), create: vi.fn(), list: vi.fn(), remove: vi.fn(), deleteUser: vi.fn() }))
vi.mock('@/lib/supabase/request', () => ({ authenticateRequest: mocks.authenticate }))
vi.mock('@supabase/supabase-js', () => ({ createClient: mocks.create }))
import { POST } from './route'
import { ApiRouteError } from '@/lib/api-errors'

describe('native account deletion', () => {
  beforeEach(() => {
    vi.clearAllMocks(); vi.stubEnv('NEXT_PUBLIC_SUPABASE_URL', 'https://test.supabase.co'); vi.stubEnv('SUPABASE_SERVICE_ROLE_KEY', 'server-secret')
    mocks.authenticate.mockResolvedValue({ user: { id: 'verified-owner' }, supabase: {}, mode: 'bearer' })
    mocks.list.mockResolvedValue({ data: [], error: null }); mocks.remove.mockResolvedValue({ error: null }); mocks.deleteUser.mockResolvedValue({ error: null })
    mocks.create.mockReturnValue({ storage: { from: () => ({ list: mocks.list, remove: mocks.remove }) }, auth: { admin: { deleteUser: mocks.deleteUser } } })
  })
  const request = (body: unknown = { confirm: 'DELETE' }) => new NextRequest('https://nafs.example/api/user/delete', { method: 'POST', body: JSON.stringify(body) })
  it('deletes the verified Auth user and confirms complete deletion', async () => {
    const response = await POST(request({ confirm: 'DELETE', userId: 'foreign' }))
    expect(response.status).toBe(200); expect(await response.json()).toEqual({ ok: true, authDeleted: true })
    expect(mocks.deleteUser).toHaveBeenCalledWith('verified-owner'); expect(mocks.list.mock.calls.every(call => call[0] === 'verified-owner')).toBe(true)
  })
  it('refuses to remove public data when the service key is absent', async () => {
    vi.stubEnv('SUPABASE_SERVICE_ROLE_KEY', '')
    expect((await POST(request())).status).toBe(503); expect(mocks.create).not.toHaveBeenCalled(); expect(mocks.deleteUser).not.toHaveBeenCalled()
  })
  it('requires the exact confirmation before accessing admin storage', async () => {
    expect((await POST(request({ confirm: 'delete' }))).status).toBe(400); expect(mocks.create).not.toHaveBeenCalled()
  })
  it('rejects unauthenticated requests without side effects', async () => {
    mocks.authenticate.mockRejectedValue(new ApiRouteError(401, 'unauthorized', 'Sign in'))
    expect((await POST(request())).status).toBe(401); expect(mocks.create).not.toHaveBeenCalled()
  })
  it('cleans nested challenge evidence only inside the verified folder', async () => {
    mocks.list.mockResolvedValueOnce({ data: [{ name: 'challenges', id: null }], error: null })
      .mockResolvedValueOnce({ data: [{ name: 'proof.jpg', id: 'file-id' }], error: null })
    const response = await POST(request()); expect(response.status).toBe(200)
    expect(mocks.remove).toHaveBeenCalledWith(['verified-owner/challenges/proof.jpg'])
  })
  it('does not delete Auth if upload removal fails', async () => {
    mocks.list.mockResolvedValueOnce({ data: [{ name: 'proof.jpg', id: 'file-id' }], error: null })
    mocks.remove.mockResolvedValueOnce({ error: { message: 'failure' } })
    expect((await POST(request())).status).toBe(503); expect(mocks.deleteUser).not.toHaveBeenCalled()
  })
  it('never reports success when Auth deletion fails', async () => {
    mocks.deleteUser.mockResolvedValueOnce({ error: { message: 'internal details' } })
    const response = await POST(request()); expect(response.status).toBe(503); expect(JSON.stringify(await response.json())).not.toContain('internal details')
  })
})
