import { describe, expect, it, vi } from 'vitest'
import type { SupabaseClient } from '@supabase/supabase-js'
import { readAllOwnedRows } from './owned-history'

function client(pages: { data: { id: string }[] | null; count: number | null; error?: unknown }[]) {
  const query = { select: vi.fn(), eq: vi.fn(), order: vi.fn(), range: vi.fn(), overrideTypes: vi.fn() }
  query.select.mockReturnValue(query); query.eq.mockReturnValue(query); query.order.mockReturnValue(query)
  query.range.mockReturnValue(query)
  pages.forEach(page => query.overrideTypes.mockResolvedValueOnce({ error: null, ...page }))
  return { query, supabase: { from: vi.fn().mockReturnValue(query) } as unknown as Pick<SupabaseClient, 'from'> }
}

describe('owned lifetime paging', () => {
  it('continues after a server caps the response below the requested page size', async () => {
    const { supabase, query } = client([{ data: [{ id: 'a' }, { id: 'b' }], count: 3 }, { data: [{ id: 'c' }], count: 3 }])
    expect((await readAllOwnedRows(supabase, 'owner', 'tasks', 'id')).map(row => row.id)).toEqual(['a', 'b', 'c'])
    expect(query.range).toHaveBeenNthCalledWith(2, 2, 1001)
    expect(query.eq).toHaveBeenCalledWith('user_id', 'owner')
    expect(query.select).toHaveBeenCalledWith('id', { count: 'exact' })
  })
  it('rejects a silently truncated empty response', async () => {
    const { supabase } = client([{ data: [], count: 3 }])
    await expect(readAllOwnedRows(supabase, 'owner', 'tasks', 'id')).rejects.toMatchObject({ status: 503 })
  })
  it('rejects peer edits and repeated rows during pagination', async () => {
    for (const second of [{ data: [{ id: 'b' }], count: 3 }, { data: [{ id: 'a' }], count: 2 }]) {
      const { supabase } = client([{ data: [{ id: 'a' }], count: 2 }, second])
      await expect(readAllOwnedRows(supabase, 'owner', 'tasks', 'id')).rejects.toMatchObject({ status: 503 })
    }
  })
  it('accepts an empty lifetime and rejects unavailable count/data', async () => {
    const empty = client([{ data: [], count: 0 }])
    expect(await readAllOwnedRows(empty.supabase, 'owner', 'tasks', 'id')).toEqual([])
    for (const page of [{ data: [], count: null }, { data: null, count: 1 }, { data: [], count: 100001 }]) {
      const { supabase } = client([page])
      await expect(readAllOwnedRows(supabase, 'owner', 'tasks', 'id')).rejects.toMatchObject({ status: 503 })
    }
  })
})
