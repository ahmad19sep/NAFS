'use client'

import { useEffect } from 'react'
import { resyncPushIfGranted } from '@/lib/push'

// Keeps an existing push subscription fresh on app load.
// Never prompts — iOS only allows the permission dialog from a user tap,
// so enabling happens via the toggle in Settings → Notifications.
export default function PushNotificationSetup() {
  useEffect(() => { resyncPushIfGranted() }, [])
  return null
}
