// Tiny Resend client. No SDK needed — Resend's REST API is one POST.
// Get a free key at resend.com (3,000 emails/month free).

const RESEND_API = 'https://api.resend.com/emails'

export interface SendEmailInput {
  to: string
  subject: string
  html: string
  text?: string
  from?: string
}

export function hasEmail(): boolean {
  return !!process.env.RESEND_API_KEY
}

export async function sendEmail({ to, subject, html, text, from }: SendEmailInput) {
  const apiKey = process.env.RESEND_API_KEY
  if (!apiKey) throw new Error('RESEND_API_KEY missing')

  const sender = from
    ?? process.env.NAFS_EMAIL_FROM
    ?? 'NAFS <onboarding@resend.dev>'  // works without domain verification for testing

  const res = await fetch(RESEND_API, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({ from: sender, to, subject, html, text }),
  })
  if (!res.ok) {
    const err = await res.text()
    throw new Error(`Resend error ${res.status}: ${err}`)
  }
  return res.json()
}

// ============================================================
// Template helpers
// ============================================================

/** Shared shell for all NAFS emails — dark navy + gold accent. */
function shell(title: string, bodyHtml: string, ctaUrl?: string, ctaLabel = 'Open NAFS'): string {
  const url = ctaUrl ?? process.env.NEXT_PUBLIC_APP_URL ?? 'https://nafs.app'
  return `<!DOCTYPE html>
<html>
<head><meta charset="utf-8" /><title>${escapeHtml(title)}</title></head>
<body style="margin:0;padding:0;background:#0b1a2b;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;color:#e7eef7;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#0b1a2b;padding:32px 16px;">
    <tr><td align="center">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;background:#0f2235;border:1px solid rgba(255,255,255,0.08);border-radius:24px;padding:32px 24px;">
        <tr><td>
          <div style="text-align:center;margin-bottom:24px;">
            <div style="display:inline-block;height:56px;width:56px;border-radius:18px;background:#0F4C5C;line-height:56px;font-size:28px;color:#C9A227;">ن</div>
            <h1 style="margin:8px 0 0 0;font-size:20px;color:#C9A227;letter-spacing:1px;">NAFS</h1>
            <p style="margin:4px 0 0 0;font-size:11px;color:#7e93ad;letter-spacing:2px;text-transform:uppercase;">${escapeHtml(title)}</p>
          </div>
          ${bodyHtml}
          <div style="text-align:center;margin-top:32px;">
            <a href="${escapeHtml(url)}" style="display:inline-block;background:#0F4C5C;color:#fff;text-decoration:none;padding:12px 24px;border-radius:12px;font-weight:600;font-size:14px;">${escapeHtml(ctaLabel)} →</a>
          </div>
          <p style="margin:32px 0 0 0;font-size:11px;color:#5d7185;text-align:center;line-height:1.6;">
            You're receiving this because email reports are enabled in NAFS Settings.<br />
            Disable any time from your profile.
          </p>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`
}

export function escapeHtml(s: string): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

export interface DailyReport {
  name: string
  date: string                // YYYY-MM-DD
  score: number               // 0..100
  delta: number | null        // vs yesterday
  verdict: string             // AI-generated, 2-3 sentences
  stats: { emoji: string; label: string; earned: number; max: number }[]
  tomorrow: string            // AI tip
}

export function dailyReportHTML(d: DailyReport): string {
  const dateStr = new Date(d.date + 'T12:00:00').toLocaleDateString('en-US',
    { weekday: 'long', month: 'long', day: 'numeric' })

  const scoreColor = d.score >= 75 ? '#34d399' : d.score >= 50 ? '#C9A227' : d.score > 0 ? '#fb923c' : '#7e93ad'
  const deltaHtml = d.delta != null
    ? `<span style="display:inline-block;margin-left:8px;font-size:13px;color:${d.delta > 0 ? '#34d399' : d.delta < 0 ? '#f87171' : '#7e93ad'};">${d.delta > 0 ? '↑ +' : d.delta < 0 ? '↓ ' : '→ '}${d.delta}% vs yesterday</span>`
    : ''

  const statsHtml = d.stats.map(s => `
    <tr><td style="padding:6px 0;font-size:13px;">
      <span style="display:inline-block;width:24px;">${s.emoji}</span>
      <span style="color:#e7eef7;">${escapeHtml(s.label)}</span>
      <span style="float:right;color:#a5b7ca;">${s.earned} / ${s.max}</span>
    </td></tr>`).join('')

  const body = `
    <p style="margin:0;font-size:14px;color:#a5b7ca;">${escapeHtml(dateStr)}</p>
    <p style="margin:4px 0 24px 0;font-size:16px;color:#e7eef7;">As-salamu alaykum, ${escapeHtml(d.name)}</p>

    <div style="background:#0b1a2b;border:1px solid rgba(201,162,39,0.2);border-radius:16px;padding:20px;margin-bottom:20px;text-align:center;">
      <p style="margin:0;font-size:11px;color:#7e93ad;letter-spacing:2px;text-transform:uppercase;">Today's score</p>
      <p style="margin:6px 0 0 0;font-size:48px;font-weight:700;color:${scoreColor};line-height:1;">${d.score}%</p>
      ${deltaHtml ? `<p style="margin:8px 0 0 0;">${deltaHtml}</p>` : ''}
    </div>

    <div style="background:rgba(255,255,255,0.03);border:1px solid rgba(255,255,255,0.08);border-radius:16px;padding:16px;margin-bottom:20px;">
      <p style="margin:0 0 12px 0;font-size:11px;color:#7e93ad;letter-spacing:2px;text-transform:uppercase;">AI verdict</p>
      <p style="margin:0;font-size:14px;line-height:1.6;color:#e7eef7;">${escapeHtml(d.verdict)}</p>
    </div>

    <div style="background:rgba(255,255,255,0.03);border:1px solid rgba(255,255,255,0.08);border-radius:16px;padding:16px;margin-bottom:20px;">
      <p style="margin:0 0 8px 0;font-size:11px;color:#7e93ad;letter-spacing:2px;text-transform:uppercase;">Today's breakdown</p>
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
        ${statsHtml}
      </table>
    </div>

    <div style="background:rgba(201,162,39,0.08);border:1px solid rgba(201,162,39,0.25);border-radius:16px;padding:16px;">
      <p style="margin:0 0 8px 0;font-size:11px;color:#C9A227;letter-spacing:2px;text-transform:uppercase;">Tomorrow, focus on</p>
      <p style="margin:0;font-size:14px;line-height:1.6;color:#e7eef7;">${escapeHtml(d.tomorrow)}</p>
    </div>`

  return shell(`Daily verdict — ${dateStr}`, body, undefined, 'View dashboard')
}

export interface WeeklyReport {
  name: string
  week_label: string          // "May 8 – 14, 2026"
  avg_score: number
  best_day:  { day: string; score: number }
  worst_day: { day: string; score: number }
  verdict: string             // longer AI tribunal-style
  goal_alignments: { title: string; score: number }[]
  next_week: string[]         // 2–3 recommendations
}

export function weeklyReportHTML(d: WeeklyReport): string {
  const avgColor = d.avg_score >= 75 ? '#34d399' : d.avg_score >= 50 ? '#C9A227' : '#fb923c'

  const alignmentsHtml = d.goal_alignments.length === 0
    ? '<p style="margin:0;font-size:13px;color:#7e93ad;">No active goals.</p>'
    : d.goal_alignments.map(g => `
      <tr><td style="padding:8px 0;font-size:13px;">
        <span style="color:#e7eef7;">${escapeHtml(g.title)}</span>
        <span style="float:right;color:${g.score >= 75 ? '#34d399' : g.score >= 50 ? '#C9A227' : g.score >= 25 ? '#fb923c' : '#f87171'};font-weight:600;">${g.score}%</span>
      </td></tr>`).join('')

  const recommendationsHtml = d.next_week.map((r, i) => `
    <li style="margin-bottom:8px;font-size:14px;line-height:1.6;color:#e7eef7;">${escapeHtml(r)}</li>`).join('')

  const body = `
    <p style="margin:0;font-size:14px;color:#a5b7ca;">${escapeHtml(d.week_label)}</p>
    <p style="margin:4px 0 24px 0;font-size:16px;color:#e7eef7;">Weekly tribunal, ${escapeHtml(d.name)}</p>

    <div style="background:#0b1a2b;border:1px solid rgba(201,162,39,0.2);border-radius:16px;padding:20px;margin-bottom:20px;text-align:center;">
      <p style="margin:0;font-size:11px;color:#7e93ad;letter-spacing:2px;text-transform:uppercase;">Average score this week</p>
      <p style="margin:6px 0 0 0;font-size:48px;font-weight:700;color:${avgColor};line-height:1;">${d.avg_score}%</p>
    </div>

    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin-bottom:20px;">
      <tr>
        <td style="width:50%;padding-right:6px;">
          <div style="background:rgba(52,211,153,0.08);border:1px solid rgba(52,211,153,0.25);border-radius:12px;padding:14px;text-align:center;">
            <p style="margin:0;font-size:10px;color:#34d399;letter-spacing:1px;text-transform:uppercase;">Best day</p>
            <p style="margin:6px 0 0 0;font-size:20px;font-weight:700;color:#34d399;">${escapeHtml(d.best_day.day)}</p>
            <p style="margin:2px 0 0 0;font-size:13px;color:#a5b7ca;">${d.best_day.score}%</p>
          </div>
        </td>
        <td style="width:50%;padding-left:6px;">
          <div style="background:rgba(248,113,113,0.08);border:1px solid rgba(248,113,113,0.25);border-radius:12px;padding:14px;text-align:center;">
            <p style="margin:0;font-size:10px;color:#f87171;letter-spacing:1px;text-transform:uppercase;">Tough day</p>
            <p style="margin:6px 0 0 0;font-size:20px;font-weight:700;color:#f87171;">${escapeHtml(d.worst_day.day)}</p>
            <p style="margin:2px 0 0 0;font-size:13px;color:#a5b7ca;">${d.worst_day.score}%</p>
          </div>
        </td>
      </tr>
    </table>

    <div style="background:rgba(255,255,255,0.03);border:1px solid rgba(255,255,255,0.08);border-radius:16px;padding:16px;margin-bottom:20px;">
      <p style="margin:0 0 12px 0;font-size:11px;color:#7e93ad;letter-spacing:2px;text-transform:uppercase;">Tribunal verdict</p>
      <p style="margin:0;font-size:14px;line-height:1.7;color:#e7eef7;white-space:pre-wrap;">${escapeHtml(d.verdict)}</p>
    </div>

    <div style="background:rgba(255,255,255,0.03);border:1px solid rgba(255,255,255,0.08);border-radius:16px;padding:16px;margin-bottom:20px;">
      <p style="margin:0 0 12px 0;font-size:11px;color:#7e93ad;letter-spacing:2px;text-transform:uppercase;">Goal alignment</p>
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">${alignmentsHtml}</table>
    </div>

    <div style="background:rgba(201,162,39,0.08);border:1px solid rgba(201,162,39,0.25);border-radius:16px;padding:16px;">
      <p style="margin:0 0 8px 0;font-size:11px;color:#C9A227;letter-spacing:2px;text-transform:uppercase;">Next week, do this</p>
      <ul style="margin:0;padding-left:18px;">${recommendationsHtml}</ul>
    </div>`

  return shell(`Weekly tribunal — ${d.week_label}`, body, undefined, 'Open dashboard')
}
