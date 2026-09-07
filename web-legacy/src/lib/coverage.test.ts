import { describe, it, expect } from 'vitest'
import { coverageMetrics, describeCoverage, type OpportunityState } from './coverage'

/**
 * Fixture D02 and its neighbours from the NAFS Improvement Blueprint.
 *
 * These pin the proposed contract: unknown is never silently converted into a
 * miss, and no rate is ever reported without the denominator behind it.
 */

describe('D02 — 7 eligible: 4 complete, 1 missed, 2 unknown', () => {
  const states: OpportunityState[] = [
    'completed', 'completed', 'completed', 'completed',
    'missed',
    'unknown', 'unknown',
  ]
  const m = coverageMetrics(states)

  it('reports 80% completion among recorded opportunities', () => {
    expect(m.recorded).toBe(5)
    expect(m.completed).toBe(4)
    expect(m.completionOnRecorded).toBeCloseTo(0.8)
  })

  it('reports coverage of 5 of 7', () => {
    expect(m.eligible).toBe(7)
    expect(m.recordingCoverage).toBeCloseTo(5 / 7)
  })

  it('bounds true completion between 4/7 and 6/7', () => {
    // Every unknown treated first as a miss, then as a success.
    expect(m.possibleCompletion!.min).toBeCloseTo(4 / 7)
    expect(m.possibleCompletion!.max).toBeCloseTo(6 / 7)
  })

  it('does not relabel unknown as missed', () => {
    expect(m.unknown).toBe(2)
    // The single explicit miss stays the only miss: 5 recorded, not 7.
    expect(m.recorded).not.toBe(m.eligible)
  })

  it('states the denominator alongside the percentage', () => {
    expect(describeCoverage(m)).toBe(
      '80% completed among recorded opportunities · 5 of 7 recorded',
    )
  })
})

describe('D03 — nothing scheduled yields no rate', () => {
  const m = coverageMetrics(['not_scheduled', 'not_scheduled'])

  it('has no eligible opportunities', () => {
    expect(m.eligible).toBe(0)
  })

  it('returns null rather than 0% or NaN', () => {
    expect(m.completionOnRecorded).toBeNull()
    expect(m.recordingCoverage).toBeNull()
    expect(m.possibleCompletion).toBeNull()
  })

  it('says so in words', () => {
    expect(describeCoverage(m)).toBe('Nothing scheduled')
  })
})

describe('D04 — eligible but nothing recorded', () => {
  const m = coverageMetrics(['unknown', 'unknown', 'unknown', 'unknown',
    'unknown', 'unknown', 'unknown'])

  it('distinguishes "no outcomes" from "0% performance"', () => {
    expect(m.eligible).toBe(7)
    expect(m.recorded).toBe(0)
    expect(m.completionOnRecorded).toBeNull()
    expect(m.recordingCoverage).toBe(0)
    expect(describeCoverage(m)).toBe('No outcomes recorded · 0 of 7')
  })

  it('bounds completion across the full range, since nothing is known', () => {
    expect(m.possibleCompletion).toEqual({ min: 0, max: 1 })
  })
})

describe('states that must not distort a denominator', () => {
  it('excludes pending — an open window is not a failure', () => {
    const m = coverageMetrics(['completed', 'pending', 'pending'])
    expect(m.eligible).toBe(1)
    expect(m.completionOnRecorded).toBe(1)
  })

  it('excludes excused but keeps it visible', () => {
    const m = coverageMetrics(['completed', 'missed', 'excused'])
    expect(m.eligible).toBe(2)
    expect(m.excused).toBe(1)
  })

  it('counts partial as recorded but not completed', () => {
    const m = coverageMetrics(['completed', 'partial'])
    expect(m.recorded).toBe(2)
    expect(m.completed).toBe(1)
    expect(m.completionOnRecorded).toBe(0.5)
  })
})
