import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import webpush from 'web-push'
import type { NotificationPayload } from '@/types'

export async function POST(req: NextRequest) {
  if (process.env.VAPID_EMAIL && process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY && process.env.VAPID_PRIVATE_KEY) {
    webpush.setVapidDetails(
      process.env.VAPID_EMAIL,
      process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY,
      process.env.VAPID_PRIVATE_KEY
    )
  }
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    const payload: NotificationPayload = await req.json()

    const { data: profile } = await supabase
      .from('users')
      .select('push_subscription')
      .eq('id', user.id)
      .single()

    if (!profile?.push_subscription) {
      return NextResponse.json({ error: 'No push subscription' }, { status: 400 })
    }

    await webpush.sendNotification(
      profile.push_subscription as any,
      JSON.stringify(payload)
    )

    return NextResponse.json({ ok: true })
  } catch (err) {
    console.error('push error:', err)
    return NextResponse.json({ error: 'Push failed' }, { status: 500 })
  }
}
