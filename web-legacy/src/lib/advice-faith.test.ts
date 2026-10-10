import { describe, expect, it } from 'vitest'
import { adviceSystem, readFaithPreference } from './advice-faith'
describe('advice faith preference', () => {
  it('preserves an enabled prompt and explicitly overrides religious assumptions when disabled', () => {
    expect(adviceSystem('Muslim coach', true)).toBe('Muslim coach')
    expect(adviceSystem('Muslim coach after After Fajr (06:30)', false)).not.toContain('Fajr')
    expect(adviceSystem('coach', false)).toContain('faith mode is OFF')
  })
  it('does not infer a preference when its read fails', async () => {
    const db: any = { from: () => ({ select: () => ({ eq: () => ({ single: async () => ({ data: null, error: {} }) }) }) }) }
    await expect(readFaithPreference(db, 'verified-user')).rejects.toMatchObject({ status: 503 })
  })
})
