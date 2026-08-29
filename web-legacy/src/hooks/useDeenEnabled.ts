'use client'

import { useEffect, useState } from 'react'
import { createClient } from '@/lib/supabase/client'

// Faith mode flag (users.deen_enabled). Defaults to true while loading and
// when the column is missing (pre-migration DBs), so prayer features only
// disappear once the user has explicitly opted out.
export function useDeenEnabled(): boolean {
  const [enabled, setEnabled] = useState(true)

  useEffect(() => {
    const supabase = createClient()
    let cancelled = false
    supabase.auth.getUser().then(async ({ data: { user } }) => {
      if (!user || cancelled) return
      const { data } = await supabase
        .from('users').select('deen_enabled').eq('id', user.id).single()
      if (!cancelled && data?.deen_enabled === false) setEnabled(false)
    })
    return () => { cancelled = true }
  }, [])

  return enabled
}
