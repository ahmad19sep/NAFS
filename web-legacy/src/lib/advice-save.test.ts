import { describe, expect, it } from 'vitest'
import { saveAdvice } from './advice-save'
describe('checked advice persistence', () => {
  it('does not confirm an RLS update that returned no owned row', async () => {
    const query: any = { eq: () => query, select: () => query, maybeSingle: async () => ({ data: null, error: null }) }
    await expect(saveAdvice({ from: () => ({ update: () => query }) } as any, 'goals', 'user', 'goal', {})).rejects.toMatchObject({ code: 'result_unconfirmed' })
  })
  it('stamps the persisted generated pack so a lost response can be recovered exactly', async () => {
    const query: any = { eq: () => query, select: () => query, maybeSingle: async () => ({ data: { id: 'goal' }, error: null }) }
    const pack = { advice: 'Small step' }
    await saveAdvice({ from: () => ({ update: () => query }) } as any, 'goals', 'user', 'goal', { ai_starter_pack: pack }, '4bba5193-5969-4f0c-8e39-31e9ef2d0bdb')
    expect(pack).toHaveProperty('request_id', '4bba5193-5969-4f0c-8e39-31e9ef2d0bdb')
  })
})
