import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import webpush from 'web-push'

// Vercel Cron — runs every 15 min.
// In vercel.json: { "path": "/api/cron/notify-tasks", "schedule": "*/15 * * * *" }

const ONE_HOUR_KEY = '1h'
const HALF_HOUR_KEY = '30m'

/**
 * Returns the current YYYY-MM-DD + HH:MM in the given IANA timezone.
 * Falls back to UTC if the timezone is invalid.
 */
function nowIn(tz: string): { date: string; minutes: number } {
  try {
    const fmt = new Intl.DateTimeFormat('en-CA', {
      timeZone: tz,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
      hour12: false,
    })
    const parts = fmt.formatToParts(new Date())
    const m: Record<string, string> = {}
    for (const p of parts) m[p.type] = p.value
    return {
      date: `${m.year}-${m.month}-${m.day}`,
      minutes: Number(m.hour) * 60 + Number(m.minute),
    }
  } catch {
    const d = new Date()
    return {
      date: d.toISOString().split('T')[0],
      minutes: d.getUTCHours() * 60 + d.getUTCMinutes(),
    }
  }
}

function dueMinutes(due_time: string): number {
  const [hh, mm] = due_time.split(':').map(Number)
  return hh * 60 + mm
}

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

  // Pull users opted in with a push subscription
  const { data: users } = await supabase
    .from('users')
    .select('id, push_subscription, timezone, notifications_enabled, notify_task_deadlines')
    .not('push_subscription', 'is', null)
    .eq('notifications_enabled', true)
    .eq('notify_task_deadlines', true)

  if (!users?.length) return NextResponse.json({ ok: true, sent: 0 })

  let sent = 0
  let scanned = 0

  for (const user of users) {
    const { date: localDate, minutes: nowMin } = nowIn(user.timezone || 'UTC')

    // Today's active daily tasks with a due_time
    const { data: tasks } = await supabase
      .from('tasks')
      .select('id, title, due_time, alerts_sent')
      .eq('user_id', user.id)
      .eq('type', 'daily')
      .eq('period_date', localDate)
      .eq('status', 'active')
      .not('due_time', 'is', null)

    if (!tasks?.length) continue

    for (const task of tasks) {
      scanned++
      const dueMin = dueMinutes(task.due_time!)
      const minsLeft = dueMin - nowMin
      const alertsSent = (task.alerts_sent ?? []) as string[]

      let key: string | null = null
      let title = ''
      let body = ''
      let urgent = false

      // 30-min HIGH alert window: 20..35 mins remaining
      if (minsLeft >= 20 && minsLeft <= 35 && !alertsSent.includes(HALF_HOUR_KEY)) {
        key = HALF_HOUR_KEY
        title = `🚨 30 min left — ${task.title}`
        body = `Time's almost up. Get it done now.`
        urgent = true
      }
      // 1-hr alert window: 50..70 mins remaining (and 30m hasn't been sent yet)
      else if (minsLeft >= 50 && minsLeft <= 70 && !alertsSent.includes(ONE_HOUR_KEY)) {
        key = ONE_HOUR_KEY
        title = `⏰ 1 hour left — ${task.title}`
        body = `Plan the next 60 minutes. Make it count.`
      }

      if (!key) continue

      try {
        await webpush.sendNotification(
          user.push_subscription as any,
          JSON.stringify({
            title, body,
            icon: '/icons/icon-192x192.png',
            badge: '/icons/icon-72x72.png',
            url: '/tasks',
            tag: `task-${task.id}-${key}`,
            requireInteraction: urgent,
          })
        )
        await supabase
          .from('tasks')
          .update({ alerts_sent: [...alertsSent, key] })
          .eq('id', task.id)
        sent++
      } catch (err) {
        console.error('push failed for task', task.id, err)
      }
    }
  }

  return NextResponse.json({ ok: true, scanned, sent })
}
