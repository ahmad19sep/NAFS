import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import webpush from 'web-push'

// Vercel Cron — runs every Sunday at 8 PM
// In vercel.json: { "crons": [{ "path": "/api/cron/tribunal", "schedule": "0 20 * * 0" }] }

export async function GET(req: NextRequest) {
  if (process.env.VAPID_EMAIL && process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY && process.env.VAPID_PRIVATE_KEY) {
    webpush.setVapidDetails(
      process.env.VAPID_EMAIL,
      process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY,
      process.env.VAPID_PRIVATE_KEY
    )
  }
  // Verify this is a legitimate Vercel Cron call
  const authHeader = req.headers.get('authorization')
  if (authHeader !== `Bearer ${process.env.CRON_SECRET}`) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  const supabase = createClient()

  // Get all users with push subscriptions
  const { data: users } = await supabase
    .from('users')
    .select('id, push_subscription')
    .not('push_subscription', 'is', null)
    .eq('onboarding_complete', true)

  if (!users?.length) return NextResponse.json({ ok: true, count: 0 })

  let count = 0
  for (const user of users) {
    try {
      // Trigger tribunal generation for this user
      const appUrl = process.env.NEXT_PUBLIC_APP_URL ?? 'http://localhost:3000'
      await fetch(`${appUrl}/api/ai/tribunal`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': user.id,
        },
      })

      // Send push notification
      if (user.push_subscription) {
        await webpush.sendNotification(
          user.push_subscription as any,
          JSON.stringify({
            title: 'NAFS — Weekly Tribunal',
            body: 'Your weekly verdict is ready. Tap to face it.',
            icon: '/icons/icon-192x192.png',
            badge: '/icons/icon-72x72.png',
            url: '/coach',
            tag: 'tribunal',
          })
        )
      }
      count++
    } catch {}
  }

  return NextResponse.json({ ok: true, count })
}
