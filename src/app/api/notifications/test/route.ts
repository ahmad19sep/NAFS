import { NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import webpush from 'web-push'

// Sends a test push to the signed-in user's own device.
export async function POST() {
  try {
    const supabase = createClient()
    const { data: { user } } = await supabase.auth.getUser()
    if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })

    if (!process.env.VAPID_PRIVATE_KEY || !process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY) {
      return NextResponse.json({ error: 'VAPID keys not configured' }, { status: 500 })
    }
    webpush.setVapidDetails(
      process.env.VAPID_EMAIL || 'mailto:noreply@ascend.app',
      process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY,
      process.env.VAPID_PRIVATE_KEY,
    )

    const { data: profile } = await supabase
      .from('users').select('push_subscription, name').eq('id', user.id).maybeSingle()

    if (!profile?.push_subscription) {
      return NextResponse.json({ error: 'No push subscription on this account yet' }, { status: 400 })
    }

    await webpush.sendNotification(
      profile.push_subscription as any,
      JSON.stringify({
        title: '🔔 Ascend is connected',
        body: `Notifications are working, ${(profile.name || '').split(' ')[0] || 'champion'}. Task reminders will arrive like this.`,
        icon: '/icons/icon-192x192.png',
        badge: '/icons/icon-72x72.png',
        url: '/dashboard',
        tag: 'test-notification',
      }),
    )
    return NextResponse.json({ ok: true })
  } catch (err: any) {
    // 410 means the subscription is stale — client should re-enable
    const gone = err?.statusCode === 410 || err?.statusCode === 404
    return NextResponse.json({
      error: gone ? 'Subscription expired — toggle notifications off and on again.' : (err?.message || 'Send failed'),
    }, { status: gone ? 410 : 500 })
  }
}
