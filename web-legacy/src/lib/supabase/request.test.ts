import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { authenticateRequest, bearerToken } from './request'

const mocks = vi.hoisted(() => ({ publicClient: vi.fn(), cookieClient: vi.fn(), getUser: vi.fn() }))
vi.mock('@supabase/supabase-js', () => ({ createClient: mocks.publicClient }))
vi.mock('./server', () => ({ createClient: mocks.cookieClient }))

const request = (header?: string) => new Request('https://nafs.example/api/review', {
  headers: header === undefined ? {} : { authorization: header },
})

describe('verified cookie and bearer authentication', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.stubEnv('NEXT_PUBLIC_SUPABASE_URL', 'https://public.example')
    vi.stubEnv('NEXT_PUBLIC_SUPABASE_ANON_KEY', 'public-anon-key')
    const client = { auth: { getUser: mocks.getUser } }
    mocks.publicClient.mockReturnValue(client)
    mocks.cookieClient.mockResolvedValue(client)
    mocks.getUser.mockResolvedValue({ data: { user: { id: 'verified-user' } }, error: null })
  })
  afterEach(() => vi.unstubAllEnvs())

  it('verifies the supplied token using an isolated public-key client', async () => {
    const result = await authenticateRequest(request('Bearer access.jwt-token'))
    expect(result.user.id).toBe('verified-user')
    expect(result.mode).toBe('bearer')
    expect(mocks.publicClient).toHaveBeenCalledWith('https://public.example', 'public-anon-key', {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
      global: { headers: { Authorization: 'Bearer access.jwt-token' } },
    })
    expect(mocks.getUser).toHaveBeenCalledWith('access.jwt-token')
    expect(mocks.cookieClient).not.toHaveBeenCalled()
  })

  it('preserves verified cookie authentication when no bearer header exists', async () => {
    expect((await authenticateRequest(request())).mode).toBe('cookie')
    expect(mocks.cookieClient).toHaveBeenCalledOnce()
    expect(mocks.publicClient).not.toHaveBeenCalled()
    expect(mocks.getUser).toHaveBeenCalledWith(undefined)
  })

  it.each(['Basic access', '', 'Bearer', 'Bearer token extra', `Bearer ${'x'.repeat(8193)}`])(
    'rejects malformed authorization without falling back to a cookie: %s', async header => {
      await expect(authenticateRequest(request(header))).rejects.toMatchObject({ status: 401 })
      expect(mocks.cookieClient).not.toHaveBeenCalled()
      expect(mocks.getUser).not.toHaveBeenCalled()
    },
  )

  it('rejects an expired token without borrowing the cookie session', async () => {
    mocks.getUser.mockResolvedValue({ data: { user: null }, error: { status: 401, message: 'expired secret' } })
    await expect(authenticateRequest(request('Bearer expired'))).rejects.toMatchObject({ status: 401, code: 'unauthorized' })
    expect(mocks.cookieClient).not.toHaveBeenCalled()
  })

  it('sanitizes authentication outages and missing backend configuration', async () => {
    mocks.getUser.mockRejectedValue(new Error('private token and infrastructure details'))
    await expect(authenticateRequest(request('Bearer token'))).rejects.toMatchObject({
      status: 503, message: 'Authentication is temporarily unavailable. Try again.',
    })
    mocks.getUser.mockResolvedValue({ data: { user: null }, error: { status: 503 } })
    await expect(authenticateRequest(request('Bearer token'))).rejects.toMatchObject({ code: 'auth_unavailable' })
    vi.stubEnv('NEXT_PUBLIC_SUPABASE_ANON_KEY', '')
    await expect(authenticateRequest(request('Bearer token'))).rejects.toMatchObject({ code: 'not_configured' })
  })

  it('accepts case-insensitive bearer headers and distinguishes absence from invalid input', () => {
    expect(bearerToken('bearer jwt.token')).toBe('jwt.token')
    expect(bearerToken(null)).toBeNull()
    expect(() => bearerToken('')).toThrow()
  })
})
