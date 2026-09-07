// Printable A4 document for the weekly / monthly report.
//
// Light surface on purpose — dark backgrounds waste ink and most printers drop
// them anyway. Rendered into an iframe (or its own tab) and sent to print.
//
// Colour roles are validated against the white page surface:
//   data-now  #0F4C5C  9.51:1   teal, the current period
//   data-prev #8AB7C3  2.18:1   same hue, light step (ordinal light-end floor 2:1)
//   accent    #C9A227  2.42:1   emphasis only, always directly labelled
//   up/down   #006300 / #d03b3b   delta text (7.54:1 / 4.80:1)
//   status    good #0ca30c · warning #fab219 · serious #ec835a · critical #d03b3b
//             warning and serious are sub-3:1 by design — every status chip
//             carries an icon and a word, so colour never encodes alone.

import { prettyDate, type ReportData, type AreaComparison, type Severity } from '@/lib/report'
import { FOOD_CATEGORIES } from '@/lib/food'

/** Readable names for the food category ids the digest counts. */
const FOOD_CATEGORY_LABELS: Record<string, string> =
  Object.fromEntries(FOOD_CATEGORIES.map((c) => [c.id, c.label]))

const NAVY = '#0B1A2B'
const TEAL = '#0F4C5C'
const GOLD = '#C9A227'

function esc(s: unknown): string {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function clamp(n: number): number {
  return Math.max(0, Math.min(100, n))
}

/** Meter: one ratio against a limit. One hue — never a value ramp. */
function meter(value: number): string {
  return `<span class="meter"><span class="meter-fill" style="width:${clamp(value)}%"></span></span>`
}

/** Signed change, with an arrow so direction is never colour-alone. */
function deltaCell(d: number | null, unit = 'pts'): string {
  if (d == null) return '<span class="muted">—</span>'
  if (d === 0) return '<span class="muted">▬ 0</span>'
  const cls = d > 0 ? 'up' : 'down'
  const arrow = d > 0 ? '▲' : '▼'
  return `<span class="delta ${cls}">${arrow} ${d > 0 ? '+' : ''}${d}${unit ? ' ' + unit : ''}</span>`
}

function section(title: string, body: string, note?: string): string {
  if (!body.trim()) return ''
  return `
  <section>
    <h2>${esc(title)}${note ? `<span class="h2-note">${esc(note)}</span>` : ''}</h2>
    ${body}
  </section>`
}

const SEVERITY_ICON: Record<Severity, string> = {
  critical: '●',
  serious: '▲',
  warning: '■',
}

/** The coach's written read of one period, as stored in `report_reviews`. */
export interface ReportReview {
  content_md: string
  generated_at: string
  model_used?: string | null
}

/**
 * The model writes plain prose with **bold** headings, and nothing else.
 * Escaped first, then the only two things allowed through are the bold runs
 * and the paragraph breaks — model output can never become markup here.
 */
export function reviewToHtml(md: string): string {
  return md
    .split(/\n\s*\n|\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const html = esc(line).replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
      // A line that is only a heading gets its own class, so the six
      // headings read as headings on paper.
      const heading = /^<strong>[^<]+<\/strong>\s*(—.*)?$/.test(html)
      return `<p class="${heading ? 'read-h' : 'read-p'}">${html}</p>`
    })
    .join('')
}

// ─────────────────────────────────────────────────────────────────────────────

export function reportToHtml(
  d: ReportData,
  opts: { autoPrint?: boolean; review?: ReportReview | null } = {},
): string {
  const periodName = d.period === 'weekly' ? 'Weekly' : 'Monthly'
  const unit = d.period === 'weekly' ? 'week' : 'month'

  // The coach's read, when one has been written. It goes first: a reader
  // holding the paper wants the plain-words version before the tables.
  const review = !opts.review?.content_md ? '' : `
    <div class="read">
      ${reviewToHtml(opts.review.content_md)}
      <p class="read-meta">Written ${esc(prettyDate(opts.review.generated_at.slice(0, 10), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }))} by ${
        opts.review.model_used ? esc(opts.review.model_used) : 'the coach'
      } from the numbers in this report. This is one reading of them — <strong>the tables that follow are the record</strong>, so trust a figure there over a figure here. It reads what you logged, not what you intended: a day you did not log is unknown to it, never counted as a failure.</p>
    </div>`

  // ── Hero: the one number the report leads with ─────────────────────────────
  const trendLine = d.score_delta == null
    ? `<span class="muted">No previous ${unit} to compare against</span>`
    : d.score_delta === 0
      ? `<span class="muted">▬ Level with last ${unit} (${d.prev_avg_score}%)</span>`
      : `<span class="delta ${d.score_delta > 0 ? 'up' : 'down'}">${d.score_delta > 0 ? '▲' : '▼'} ${Math.abs(d.score_delta)} points ${d.score_delta > 0 ? 'up on' : 'down from'} last ${unit}</span>
         <span class="muted">(${d.prev_avg_score}% → ${d.avg_score}%)</span>`

  const hero = `
    <div class="hero">
      <div class="hero-main">
        <div class="hero-label">Average score across the ${d.days_logged} day${d.days_logged === 1 ? '' : 's'} you logged</div>
        <div class="hero-figure">${d.avg_score}<span class="hero-unit">%</span></div>
        <div class="hero-trend">${trendLine}</div>
      </div>
      <div class="hero-tiles">
        <div class="tile">
          <div class="tile-value">${d.days_logged}<span class="tile-of">/${d.days_elapsed}</span></div>
          <div class="tile-label">Days logged</div>
        </div>
        <div class="tile">
          <div class="tile-value">${d.strong_days}</div>
          <div class="tile-label">Strong days (75%+)</div>
        </div>
        <div class="tile">
          <div class="tile-value">${d.best_streak}<span class="tile-of">d</span></div>
          <div class="tile-label">Best streak at 60%+</div>
        </div>
      </div>
    </div>`

  // ── Progress vs last period — dumbbell, one hue in two shades ─────────────
  const pctRows = d.comparison.filter((c) => c.unit === '%')
  const countRows = d.comparison.filter((c) => c.unit !== '%')

  const dumbbell = (c: AreaComparison): string => {
    const now = c.current ?? 0
    const prev = c.previous
    if (prev == null) {
      return `<span class="dumb">
        <span class="dumb-track"></span>
        <span class="dot now" style="left:${clamp(now)}%"></span>
      </span>`
    }
    const lo = Math.min(now, prev)
    const hi = Math.max(now, prev)
    return `<span class="dumb">
      <span class="dumb-track"></span>
      <span class="dumb-seg" style="left:${clamp(lo)}%;width:${clamp(hi - lo)}%"></span>
      <span class="dot prev" style="left:${clamp(prev)}%"></span>
      <span class="dot now" style="left:${clamp(now)}%"></span>
    </span>`
  }

  const comparisonBlock = d.comparison.length === 0 ? '' : `
    <div class="legend">
      <span class="legend-item"><span class="dot prev static"></span>Last ${unit} — ${esc(d.previousLabel)}</span>
      <span class="legend-item"><span class="dot now static"></span>This ${unit} — ${esc(d.label)}</span>
      <span class="legend-axis">scale 0–100%</span>
    </div>
    <table class="cmp">
      <thead>
        <tr><th>Area</th><th class="w-38">Last ${esc(unit)} → this ${esc(unit)}</th><th class="num">Now</th><th class="num">Change</th><th>Detail</th></tr>
      </thead>
      <tbody>
        ${pctRows.map((c) => `
          <tr${c.key === 'overall' ? ' class="row-lead"' : ''}>
            <td>${esc(c.emoji)} <strong>${esc(c.label)}</strong></td>
            <td>${dumbbell(c)}</td>
            <td class="num"><strong>${c.current == null ? '—' : c.current + '%'}</strong></td>
            <td class="num">${deltaCell(c.delta)}</td>
            <td class="muted small">${esc(c.detail)}</td>
          </tr>`).join('')}
        ${countRows.map((c) => `
          <tr>
            <td>${esc(c.emoji)} <strong>${esc(c.label)}</strong></td>
            <td class="muted small">counted, not scored</td>
            <td class="num"><strong>${c.current ?? 0}</strong></td>
            <td class="num">${deltaCell(c.delta, 'pages')}</td>
            <td class="muted small">${esc(c.detail)}</td>
          </tr>`).join('')}
      </tbody>
    </table>
    <p class="note">Every area is measured against all ${d.days_elapsed} elapsed days, so a day you did not log counts against it.</p>`

  // ── Where to improve ───────────────────────────────────────────────────────
  const focusBlock = d.focus.length === 0 ? '' : `
    <ol class="focus">
      ${d.focus.map((f, i) => `
        <li class="focus-item sev-${f.severity}">
          <div class="focus-head">
            <span class="focus-n">${i + 1}</span>
            <span class="focus-title">${esc(f.emoji)} ${esc(f.title)}</span>
            <span class="chip chip-${f.severity}">${SEVERITY_ICON[f.severity]} ${esc(f.severityLabel)}</span>
          </div>
          <div class="focus-stat">${esc(f.area)} · <strong>${esc(f.stat)}</strong>${f.delta != null && f.delta !== 0 ? ' · ' + deltaCell(f.delta) : ''}</div>
          <div class="focus-why">${esc(f.why)}</div>
          <div class="focus-action"><strong>Do this:</strong> ${esc(f.action)}</div>
        </li>`).join('')}
    </ol>`

  const winsBlock = d.wins.length === 0 ? '' : `
    <ul class="wins">
      ${d.wins.map((w) => `
        <li><span class="win-mark">▲</span> <strong>${esc(w.emoji)} ${esc(w.label)}</strong>
          up ${w.delta} ${w.unit === 'pages'
            ? (w.delta === 1 ? 'page' : 'pages')
            : (w.delta === 1 ? 'point' : 'points')}
          <span class="muted">— ${esc(w.previousDetail)} → ${esc(w.detail)}</span></li>`).join('')}
    </ul>`

  // ── Daily score — one series, emphasis on the extremes ────────────────────
  const bestDate = d.best_day?.date
  const worstDate = d.worst_day?.date
  const avgLine = d.days_logged > 0 ? d.avg_score : 0
  const chart = d.days.length === 0 ? '' : `
    <div class="chart">
      <div class="chart-plot">
        ${avgLine > 0 ? `<span class="avg-rule" style="bottom:${clamp(avgLine)}%"><span class="avg-tag">avg ${avgLine}%</span></span>` : ''}
        ${d.days.map((day) => {
          const emphasis = day.logged && (day.date === bestDate || day.date === worstDate)
          return `<span class="col">
            ${emphasis ? `<span class="col-label">${day.score}</span>` : ''}
            <span class="col-bar${emphasis ? ' emph' : ''}${day.logged ? '' : ' none'}" style="height:${Math.max(1.5, clamp(day.score))}%"></span>
          </span>`
        }).join('')}
      </div>
      <div class="chart-axis">
        ${d.days.map((day) => `<span class="col"><span class="tick">${esc(day.weekday.charAt(0))}</span></span>`).join('')}
      </div>
      <div class="chart-foot">
        <span>${esc(prettyDate(d.days[0].date))}</span>
        <span class="muted">${d.best_day ? `best ${esc(d.best_day.weekday)} ${d.best_day.score}% · ` : ''}${d.worst_day ? `low ${esc(d.worst_day.weekday)} ${d.worst_day.score}%` : ''}</span>
        <span>${esc(prettyDate(d.days[d.days.length - 1].date))}</span>
      </div>
    </div>`

  // ── Salah ──────────────────────────────────────────────────────────────────
  const salah = !d.deenEnabled || d.prayers_possible === 0 ? '' : `
    <table>
      <thead><tr><th>Prayer</th><th class="num">Prayed</th><th class="num">Jamaat</th><th class="num">Missed</th><th class="w-32">Consistency</th></tr></thead>
      <tbody>
        ${d.prayer_breakdown.map((p) => `
          <tr>
            <td><strong>${esc(p.label)}</strong></td>
            <td class="num">${p.prayed}/${d.days_elapsed}</td>
            <td class="num">${p.jamat}</td>
            <td class="num${p.missed > 0 ? ' bad' : ''}">${p.missed}</td>
            <td>${meter(p.pct)}<span class="pct">${p.pct}%</span></td>
          </tr>`).join('')}
      </tbody>
      <tfoot>
        <tr>
          <td><strong>Total</strong></td>
          <td class="num"><strong>${d.prayers_prayed}/${d.prayers_possible}</strong></td>
          <td class="num"><strong>${d.prayers_jamat}</strong></td>
          <td class="num"><strong>${d.prayers_possible - d.prayers_prayed}</strong></td>
          <td><strong>${Math.round((d.prayers_prayed / Math.max(d.prayers_possible, 1)) * 100)}%</strong></td>
        </tr>
      </tfoot>
    </table>
    ${d.extra_prayers.length === 0 ? '' : `
      <h3>Extra / nafl</h3>
      <table>
        <thead><tr><th>Prayer</th><th class="num">Done</th><th class="w-32">Rate</th></tr></thead>
        <tbody>
          ${d.extra_prayers.map((x) => `
            <tr><td><strong>${esc(x.name)}</strong></td><td class="num">${x.done}/${x.days}</td>
                <td>${meter(x.pct)}<span class="pct">${x.pct}%</span></td></tr>`).join('')}
        </tbody>
      </table>`}
    <p class="note">
      Quality points ${d.prayer_points}/${d.prayer_points_max} (1 pt prayed alone, 2 pts in jamaat)${
        d.quran_pages > 0
          ? ` · Quran ${d.quran_pages} pages across ${d.quran_days} days${d.quran_surahs.length ? ` (${esc(d.quran_surahs.slice(0, 6).join(', '))})` : ''}`
          : ''}
    </p>`

  const habits = d.habits.length === 0 ? '' : `
    <table>
      <thead><tr><th>Habit</th><th class="num">Done</th><th class="w-32">Rate</th><th class="num">Best run</th><th class="num">Streak</th><th class="num">Volume</th></tr></thead>
      <tbody>
        ${d.habits.map((h) => `
          <tr>
            <td>${esc(h.emoji)} <strong>${esc(h.name)}</strong></td>
            <td class="num">${h.done_days}/${h.scheduled_days}</td>
            <td>${meter(h.pct)}<span class="pct">${h.pct}%</span></td>
            <td class="num">${h.best_run}d</td>
            <td class="num">${h.current_streak}d <span class="muted">(best ${h.longest_streak})</span></td>
            <td class="num">${h.type === 'duration'
                  ? `${h.total_minutes} min`
                  : (h.type === 'counter' || h.type === 'subject')
                    ? `${h.total_value}${h.unit ? ' ' + esc(h.unit) : ''}`
                    : '—'}</td>
          </tr>`).join('')}
      </tbody>
      <tfoot><tr><td colspan="6"><strong>Overall habit completion: ${d.habit_completion_pct}%</strong></td></tr></tfoot>
    </table>`

  const tasks = d.tasks_total === 0 ? '' : `
    <table>
      <thead><tr><th>Type</th><th class="num">Completed</th><th class="num">Total</th><th class="w-32">Rate</th></tr></thead>
      <tbody>
        ${d.tasks.map((t) => `
          <tr>
            <td style="text-transform:capitalize"><strong>${esc(t.type)}</strong></td>
            <td class="num">${t.completed}</td><td class="num">${t.total}</td>
            <td>${meter(t.pct)}<span class="pct">${t.pct}%</span></td>
          </tr>`).join('')}
      </tbody>
      <tfoot><tr><td><strong>All tasks</strong></td><td class="num"><strong>${d.tasks_completed}</strong></td><td class="num"><strong>${d.tasks_total}</strong></td><td><strong>${d.tasks_pct}%</strong></td></tr></tfoot>
    </table>
    ${d.missed_tasks.length === 0 ? '<p class="note good">Nothing left unfinished. 🎉</p>' : `
      <h3>Unfinished (${d.missed_tasks.length})</h3>
      <ul class="list">
        ${d.missed_tasks.slice(0, 40).map((t) => `
          <li><span class="pill pill-${esc(t.priority)}">${esc(t.priority)}</span> ${esc(t.title)}
              <span class="muted">— ${esc(t.type)}, ${esc(prettyDate(t.date))}</span></li>`).join('')}
      </ul>
      ${d.missed_tasks.length > 40 ? `<p class="note">+ ${d.missed_tasks.length - 40} more</p>` : ''}`}`

  const challenges = d.challenges.length === 0 ? '' : `
    <table>
      <thead><tr><th>Challenge</th><th class="num">Check-ins</th><th class="w-32">Rate</th><th class="num">Progress</th><th class="num">Streak</th><th>Status</th></tr></thead>
      <tbody>
        ${d.challenges.map((c) => `
          <tr>
            <td>${esc(c.emoji)} <strong>${esc(c.title)}</strong></td>
            <td class="num">${c.checkins}/${c.possible_days}</td>
            <td>${meter(c.pct)}<span class="pct">${c.pct}%</span></td>
            <td class="num">day ${c.day_of}/${c.duration_days}</td>
            <td class="num">${c.current_streak}d <span class="muted">(best ${c.longest_streak})</span></td>
            <td style="text-transform:capitalize">${esc(c.status)}</td>
          </tr>`).join('')}
      </tbody>
    </table>`

  const goals = d.goals.length === 0 ? '' : `
    <table>
      <thead><tr><th>Goal</th><th class="w-32">Progress</th><th class="num">Milestones</th><th class="num">Alignment</th><th>Deadline</th></tr></thead>
      <tbody>
        ${d.goals.map((g) => `
          <tr>
            <td>${esc(g.emoji)} <strong>${esc(g.title)}</strong></td>
            <td>${meter(g.progress_pct)}<span class="pct">${g.progress_pct}%</span></td>
            <td class="num">${g.milestones_total ? `${g.milestones_done}/${g.milestones_total}` : '—'}</td>
            <td class="num">${g.alignment == null ? '—' : `${g.alignment}%`}</td>
            <td>${g.deadline
                  ? `${esc(prettyDate(g.deadline, { day: 'numeric', month: 'short', year: 'numeric' }))} <span class="${(g.days_left ?? 0) < 0 ? 'bad' : 'muted'}">(${(g.days_left ?? 0) < 0 ? `${Math.abs(g.days_left!)}d overdue` : `${g.days_left}d left`})</span>`
                  : '—'}</td>
          </tr>`).join('')}
      </tbody>
    </table>`

  // DATA-02, stated rather than silently applied: this report counts missing
  // days two different ways, and the reader cannot reconcile the figures
  // without knowing which is which. Nothing here changes a number — it names
  // the basis of each, which is what makes them comparable.
  const unlogged = d.days_elapsed - d.days_logged
  const methods = `
    <p class="note">
      You logged something on <strong>${d.days_logged} of ${d.days_elapsed} days</strong>${
        unlogged > 0 ? ` — ${unlogged} day${unlogged === 1 ? ' has' : 's have'} no record at all` : ''
      }. A day with no record is <strong>unknown</strong>, not a zero: it means
      nothing was written down, not that nothing was done.
    </p>
    <p class="note">
      <strong>The headline average</strong> covers only the days you logged, so it
      answers "how did the days I recorded go?". It does not drop when you simply
      forget to log.
    </p>
    <p class="note">
      <strong>Salah and Health percentages</strong> are counted differently: they
      divide by every day in the period, so an unrecorded day lowers them. That is
      why they can look worse than the headline in a week you logged patchily —
      the difference is missing records, not a change in what you did.
    </p>
    <p class="note">
      <strong>Habits, tasks and challenges</strong> divide by what was actually
      scheduled or created, so days off and periods before an item existed are
      never counted against you.
    </p>`

  const h = d.health
  const sd = h.sleep_detail
  const hm = (mins: number | null) =>
    mins == null ? '—' : `${Math.floor(mins / 60)}h ${String(Math.round(mins % 60)).padStart(2, '0')}m`

  const health = h.days_logged === 0 ? '' : `
    <div class="tiles">
      ${[
        // Sleep leads with the coverage behind it: an average over 3 nights is
        // not an average over the period, and the report must not imply it is.
        ['Avg sleep', hm(sd.avg_minutes), `${sd.recorded_nights} of ${sd.eligible_nights} nights recorded`],
        ['Meals', h.meals.days_with_any_meal === 0 ? '—' : String(h.meals.recorded_meals),
          `across ${h.meals.days_with_any_meal} of ${h.meals.eligible_days} days`],
        ['Avg steps', h.avg_steps == null ? '—' : Math.round(h.avg_steps).toLocaleString(), `${h.total_steps.toLocaleString()} total`],
        ['Exercise', `${h.exercise_days}d`, `${h.exercise_minutes} min total`],
        ['Avg water', h.avg_water == null ? '—' : String(h.avg_water), 'glasses/day'],
        ['Avg mood', h.avg_mood == null ? '—' : `${h.avg_mood}/10`, ''],
        ['Weight', h.weight_end == null ? '—' : `${h.weight_end} kg`,
          h.weight_change == null ? '' : `${h.weight_change > 0 ? '+' : ''}${h.weight_change} kg this ${unit}`],
      ].map(([label, value, sub]) => `
        <div class="tile">
          <div class="tile-value">${esc(value)}</div>
          <div class="tile-label">${esc(label)}</div>
          ${sub ? `<div class="tile-sub">${esc(sub)}</div>` : ''}
        </div>`).join('')}
    </div>
    ${sd.recorded_nights === 0 ? '' : `
      <p class="note">
        Sleep ranged ${hm(sd.shortest_minutes)} to ${hm(sd.longest_minutes)}${
          sd.nights_with_naps > 0
            ? ` · ${sd.nights_with_naps} night${sd.nights_with_naps === 1 ? '' : 's'} included a nap or split sleep`
            : ''}${
          sd.recorded_nights < sd.eligible_nights
            ? ` · the other ${sd.eligible_nights - sd.recorded_nights} day${sd.eligible_nights - sd.recorded_nights === 1 ? '' : 's'} are unrecorded, not zero`
            : ''}
      </p>`}
    ${h.meals.top_categories.length === 0 ? '' : `
      <h3>What you ate</h3>
      <table>
        <thead><tr><th>Food type</th><th class="num">Times</th><th class="w-32">Share</th></tr></thead>
        <tbody>
          ${(() => {
            const total = h.meals.top_categories.reduce((s, c) => s + c.count, 0)
            return h.meals.top_categories.slice(0, 8).map((c) => {
              const share = Math.round((c.count / total) * 100)
              return `<tr><td><strong>${esc(FOOD_CATEGORY_LABELS[c.category] ?? c.category)}</strong></td>
                <td class="num">${c.count}</td>
                <td>${meter(share)}<span class="pct">${share}%</span></td></tr>`
            }).join('')
          })()}
        </tbody>
      </table>
      <p class="note">
        ${h.meals.avg_per_recorded_day ?? '—'} meals on a day you recorded any${
          h.meals.unknown_foods > 0
            ? ` · ${h.meals.unknown_foods} food${h.meals.unknown_foods === 1 ? '' : 's'} typed by hand, so their type is unknown`
            : ''}
      </p>`}
    ${h.metrics.length === 0 ? '' : `
      <h3>Your custom metrics</h3>
      <table>
        <thead><tr><th>Metric</th><th class="num">Hit target</th><th class="w-32">Rate</th></tr></thead>
        <tbody>
          ${h.metrics.map((m) => `
            <tr><td>${esc(m.emoji)} <strong>${esc(m.name)}</strong></td><td class="num">${m.done}/${m.days}</td>
                <td>${meter(m.pct)}<span class="pct">${m.pct}%</span></td></tr>`).join('')}
        </tbody>
      </table>`}`

  const weekdays = d.weekday_avgs.length < 2 ? '' : `
    <table>
      <thead><tr><th>Day</th><th class="w-38">Average score</th><th class="num">Days counted</th></tr></thead>
      <tbody>
        ${d.weekday_avgs.map((w) => `
          <tr><td><strong>${esc(w.weekday)}</strong></td>
              <td>${meter(w.avg)}<span class="pct">${w.avg}%</span></td>
              <td class="num">${w.days}</td></tr>`).join('')}
      </tbody>
    </table>`

  const dailyLog = d.days.length === 0 ? '' : `
    <table class="dense">
      <thead>
        <tr>
          <th>Date</th><th class="num">Score</th>${d.deenEnabled ? '<th class="num">Salah</th><th class="num">Jamaat</th><th class="num">Quran</th>' : ''}
          <th class="num">Habits</th><th class="num">Tasks</th><th class="num">Sleep</th><th class="num">Steps</th><th class="num">Water</th><th class="num">Exercise</th><th class="num">Mood</th>
        </tr>
      </thead>
      <tbody>
        ${d.days.map((day) => `
          <tr class="${day.logged ? '' : 'empty-row'}">
            <td><strong>${esc(day.weekday)}</strong> ${esc(prettyDate(day.date))}</td>
            <td class="num"><strong>${day.logged ? day.score + '%' : '—'}</strong></td>
            ${d.deenEnabled ? `<td class="num">${day.prayers_prayed}/5</td><td class="num">${day.prayers_jamat}</td><td class="num">${day.quran_pages || '—'}</td>` : ''}
            <td class="num">${day.habits_total ? `${day.habits_done}/${day.habits_total}` : '—'}</td>
            <td class="num">${day.tasks_total ? `${day.tasks_done}/${day.tasks_total}` : '—'}</td>
            <td class="num">${day.sleep_hours == null ? '—' : day.sleep_hours + 'h'}</td>
            <td class="num">${day.steps == null ? '—' : Number(day.steps).toLocaleString()}</td>
            <td class="num">${day.water_glasses || '—'}</td>
            <td class="num">${day.exercise_done ? (day.exercise_minutes ? `${day.exercise_minutes}m` : '✓') : '—'}</td>
            <td class="num">${day.mood == null ? '—' : day.mood}</td>
          </tr>`).join('')}
      </tbody>
    </table>`

  const reflections = d.reflections.length === 0 ? '' : d.reflections.map((r) => `
      <div class="quote">
        <div class="quote-date">${esc(prettyDate(r.date, { weekday: 'short', day: 'numeric', month: 'short' }))}</div>
        ${r.text ? `<div class="quote-text">${esc(r.text)}</div>` : ''}
        ${r.verdict ? `<div class="quote-verdict"><strong>NAFS:</strong> ${esc(r.verdict)}</div>` : ''}
      </div>`).join('')

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>NAFS ${esc(periodName)} Report — ${esc(d.label)}</title>
<style>
  @page { size: A4; margin: 13mm 11mm; }
  :root {
    --surface: #ffffff;
    --ink: #0b0b0b;
    --ink-2: #52514e;
    --muted: #898781;
    --grid: #e1e0d9;
    --rule: #c3c2b7;
    --now: ${TEAL};
    --prev: #8AB7C3;
    --accent: ${GOLD};
    --up: #006300;
    --down: #d03b3b;
    --good: #0ca30c;
    --warning: #fab219;
    --serious: #ec835a;
    --critical: #d03b3b;
  }
  * { box-sizing: border-box; }
  body {
    font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
    color: var(--ink); background: var(--surface);
    margin: 0; padding: 10px; font-size: 10.5px; line-height: 1.5;
    -webkit-print-color-adjust: exact; print-color-adjust: exact;
  }
  @media print { body { padding: 0; } }

  header.cover {
    background: linear-gradient(135deg, ${NAVY} 0%, ${TEAL} 100%);
    color: #fff; padding: 18px 20px; border-radius: 10px; margin-bottom: 14px;
  }
  .brand { font-size: 11px; letter-spacing: 4px; text-transform: uppercase; color: ${GOLD}; font-weight: 700; }
  .cover h1 { font-size: 22px; margin: 5px 0 2px; font-weight: 800; letter-spacing: -0.01em; }
  .cover .sub { font-size: 11.5px; opacity: .88; }
  .cover .meta { margin-top: 10px; font-size: 9.5px; opacity: .68; }

  /* ── Hero ─────────────────────────────────────────────────────────────── */
  .hero { display: flex; gap: 12px; align-items: stretch; margin-bottom: 16px; }
  .hero-main {
    flex: 0 0 34%; border: 1px solid var(--grid); border-left: 3px solid var(--now);
    border-radius: 8px; padding: 12px 14px;
  }
  .hero-label { font-size: 9px; text-transform: uppercase; letter-spacing: .7px; color: var(--muted); font-weight: 700; }
  .hero-figure { font-size: 50px; line-height: 1.02; font-weight: 800; color: var(--now); letter-spacing: -0.03em; margin-top: 2px; }
  .hero-unit { font-size: 22px; font-weight: 700; margin-left: 1px; }
  .hero-trend { font-size: 10px; margin-top: 4px; }
  .hero-tiles { flex: 1; display: flex; gap: 8px; }

  .tiles { display: flex; flex-wrap: wrap; gap: 8px; }
  .tile {
    flex: 1 1 90px; min-width: 90px; border: 1px solid var(--grid); border-radius: 8px;
    padding: 10px; text-align: center; display: flex; flex-direction: column; justify-content: center;
  }
  .tile-value { font-size: 22px; font-weight: 800; line-height: 1.1; color: var(--ink); letter-spacing: -0.02em; }
  .tile-of { font-size: 13px; font-weight: 600; color: var(--muted); }
  .tile-label { font-size: 8.5px; text-transform: uppercase; letter-spacing: .5px; color: var(--muted); margin-top: 3px; font-weight: 600; }
  .tile-sub { font-size: 8.5px; color: var(--muted); margin-top: 1px; }

  /* ── Sections ─────────────────────────────────────────────────────────── */
  section { margin-bottom: 15px; page-break-inside: avoid; }
  h2 {
    font-size: 12.5px; font-weight: 800; color: ${NAVY}; margin: 0 0 8px;
    padding-bottom: 4px; border-bottom: 2px solid ${GOLD}; letter-spacing: -0.01em;
  }
  .h2-note { float: right; font-size: 9px; font-weight: 500; color: var(--muted); padding-top: 3px; }
  h3 { font-size: 10.5px; margin: 10px 0 4px; color: var(--now); }

  /* ── Tables ───────────────────────────────────────────────────────────── */
  table { width: 100%; border-collapse: collapse; margin-bottom: 4px; }
  th {
    text-align: left; font-size: 8.5px; text-transform: uppercase; letter-spacing: .5px;
    color: var(--muted); border-bottom: 1px solid var(--rule); padding: 5px 6px; font-weight: 700;
  }
  td { padding: 5px 6px; border-bottom: 1px solid var(--grid); vertical-align: middle; }
  td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
  tfoot td { border-top: 1.5px solid var(--rule); border-bottom: none; background: #fbfcfd; }
  table.dense td, table.dense th { padding: 3px 4px; font-size: 9px; }
  .w-32 { width: 26%; } .w-38 { width: 32%; }
  .empty-row td { color: #a9b5c2; }
  .row-lead td { background: #f6f9fa; }
  .small { font-size: 9px; }

  /* ── Meter: one ratio against a limit, one hue ────────────────────────── */
  .meter { display: inline-block; width: 62%; height: 6px; background: var(--grid); border-radius: 4px; overflow: hidden; vertical-align: middle; }
  .meter-fill { display: block; height: 100%; background: var(--now); border-radius: 4px; }
  .pct { display: inline-block; margin-left: 6px; font-variant-numeric: tabular-nums; font-weight: 600; }

  /* ── Dumbbell: before → after, one hue in two shades ──────────────────── */
  .legend { display: flex; gap: 14px; align-items: center; font-size: 9px; color: var(--ink-2); margin-bottom: 6px; }
  .legend-item { display: inline-flex; align-items: center; gap: 5px; }
  .legend-axis { margin-left: auto; color: var(--muted); }
  .dumb { position: relative; display: block; height: 12px; width: 100%; }
  .dumb-track { position: absolute; top: 5px; left: 0; right: 0; height: 2px; background: var(--grid); border-radius: 2px; }
  .dumb-seg { position: absolute; top: 5px; height: 2px; background: var(--prev); border-radius: 2px; }
  .dot { position: absolute; top: 2px; width: 8px; height: 8px; border-radius: 50%; margin-left: -4px; box-shadow: 0 0 0 2px var(--surface); }
  .dot.prev { background: var(--prev); }
  .dot.now { background: var(--now); }
  .dot.static { position: static; margin: 0; box-shadow: none; flex: 0 0 8px; }

  /* ── Delta ────────────────────────────────────────────────────────────── */
  .delta { font-weight: 700; font-variant-numeric: tabular-nums; white-space: nowrap; }
  .delta.up { color: var(--up); }
  .delta.down { color: var(--down); }

  /* ── Focus list ───────────────────────────────────────────────────────── */
  .focus { list-style: none; margin: 0; padding: 0; }
  .focus-item {
    border: 1px solid var(--grid); border-left: 3px solid var(--muted);
    border-radius: 8px; padding: 9px 11px; margin-bottom: 7px; page-break-inside: avoid;
  }
  .focus-item.sev-critical { border-left-color: var(--critical); }
  .focus-item.sev-serious  { border-left-color: var(--serious); }
  .focus-item.sev-warning  { border-left-color: var(--warning); }
  .focus-head { display: flex; align-items: center; gap: 7px; }
  .focus-n {
    flex: 0 0 16px; height: 16px; border-radius: 50%; background: ${NAVY}; color: #fff;
    font-size: 9px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center;
  }
  .focus-title { font-size: 11.5px; font-weight: 700; flex: 1; }
  .chip {
    font-size: 8.5px; font-weight: 700; text-transform: uppercase; letter-spacing: .4px;
    padding: 2px 7px; border-radius: 20px; white-space: nowrap; color: var(--ink);
  }
  .chip-critical { background: #fbe4e4; }
  .chip-serious  { background: #fdece3; }
  .chip-warning  { background: #fef4dc; }
  .focus-stat { font-size: 9.5px; color: var(--ink-2); margin-top: 3px; }
  .focus-why { font-size: 10px; margin-top: 3px; }
  .focus-action { font-size: 10px; margin-top: 3px; color: var(--ink-2); }

  .wins { list-style: none; margin: 0; padding: 0; font-size: 10px; }
  .wins li { padding: 4px 0; border-bottom: 1px solid var(--grid); }
  .wins li:last-child { border-bottom: none; }
  .win-mark { color: var(--up); font-weight: 700; }

  /* ── Daily column chart: one series + emphasis ────────────────────────── */
  .chart { margin-top: 2px; }
  .chart-plot { position: relative; display: flex; align-items: flex-end; gap: 2px; height: 108px; border-bottom: 1px solid var(--rule); }
  .col { flex: 1; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; height: 100%; }
  .col-bar { width: 62%; max-width: 26px; background: var(--now); border-radius: 3px 3px 0 0; }
  .col-bar.emph { background: var(--accent); }
  .col-bar.none { background: var(--grid); }
  .col-label { font-size: 8px; font-weight: 700; color: var(--ink-2); margin-bottom: 1px; }
  .avg-rule { position: absolute; left: 0; right: 0; height: 1px; background: var(--rule); }
  .avg-tag { position: absolute; right: 0; top: -11px; font-size: 8px; color: var(--muted); background: var(--surface); padding: 0 3px; }
  .chart-axis { display: flex; gap: 2px; margin-top: 2px; }
  .tick { font-size: 7.5px; color: var(--muted); }
  .chart-foot { display: flex; justify-content: space-between; font-size: 8.5px; color: var(--ink-2); margin-top: 4px; }

  /* ── Misc ─────────────────────────────────────────────────────────────── */
  ul.list { margin: 4px 0; padding-left: 15px; font-size: 10px; }
  ul.list li { margin-bottom: 3px; }
  .pill { display: inline-block; font-size: 8px; text-transform: uppercase; font-weight: 700;
          padding: 1px 5px; border-radius: 20px; letter-spacing: .4px; color: var(--ink); }
  .pill-high { background: #fbe4e4; }
  .pill-medium { background: #fef4dc; }
  .pill-low { background: #e2ecf9; }

  .insights { background: #fbfcfd; border: 1px solid var(--grid); border-left: 3px solid ${GOLD};
              border-radius: 8px; padding: 9px 12px 9px 25px; margin: 0; font-size: 10px; }
  .insights li { margin-bottom: 4px; }

  /* ── The coach's read: prose, so it is set to be read, not scanned ────── */
  .read { border: 1px solid var(--grid); border-left: 3px solid ${GOLD};
          border-radius: 8px; padding: 11px 14px; background: #fdfdfb; }
  .read-h { font-size: 10.5px; margin: 9px 0 2px; color: ${NAVY}; }
  .read-h:first-child { margin-top: 0; }
  .read-h strong { letter-spacing: .01em; }
  .read-p { font-size: 10.5px; line-height: 1.62; margin: 0 0 5px; max-width: 62em; }
  .read-meta { font-size: 8.5px; color: var(--muted); margin: 9px 0 0;
               border-top: 1px solid var(--grid); padding-top: 6px; }

  .quote { border-left: 2px solid var(--grid); padding: 2px 0 2px 9px; margin-bottom: 7px; page-break-inside: avoid; }
  .quote-date { font-size: 8.5px; color: var(--now); font-weight: 700; text-transform: uppercase; letter-spacing: .5px; }
  .quote-text { white-space: pre-wrap; color: #33465a; font-size: 10px; }
  .quote-verdict { color: var(--ink-2); font-size: 9.5px; margin-top: 2px; }

  .note { font-size: 9.5px; color: var(--ink-2); margin: 4px 0 0; }
  .muted { color: var(--muted); }
  .bad { color: var(--down); }
  .good { color: var(--up); }

  footer { margin-top: 18px; padding-top: 9px; border-top: 1px solid var(--grid);
           text-align: center; font-size: 8.5px; color: var(--muted); }
  footer .ayah { color: var(--now); font-style: italic; margin-bottom: 3px; }
  .page-break { page-break-before: always; }
</style>
</head>
<body>

<header class="cover">
  <div class="brand">NAFS</div>
  <h1>${esc(periodName)} Progress Report</h1>
  <div class="sub">${esc(d.label)} · ${esc(d.user.name)}</div>
  <div class="meta">
    Generated ${esc(d.generated_at)}${d.user.email ? ` · ${esc(d.user.email)}` : ''}
    · Covering ${d.days_elapsed} of ${d.days_in_period} days · compared against ${esc(d.previousLabel)}
  </div>
</header>

${hero}

${section(`The coach's read of this ${unit}`, review)}
${section(`Progress vs last ${unit}`, comparisonBlock, `${d.wins.length} area${d.wins.length === 1 ? '' : 's'} up`)}
${section('Where to improve next', focusBlock, d.focus.length ? `${d.focus.length} ranked` : '')}
${section('What went well', winsBlock)}
${section('Daily score', chart, `${d.strong_days} strong · ${d.weak_days} weak`)}
${section('What the numbers say', `<ul class="insights">${d.insights.map((i) => `<li>${esc(i)}</li>`).join('')}</ul>`)}
${section('Salah & Quran', salah)}
${section('Habits', habits, `${d.habit_completion_pct}% overall`)}
${section('Tasks', tasks, `${d.tasks_pct}% completed`)}
${section('Challenges', challenges)}
${section('Goals', goals)}
${section('Health', health)}
${section('Weekday pattern', weekdays)}

${dailyLog ? `<div class="page-break"></div>${section('Day-by-day log', dailyLog)}` : ''}
${section('Your reflections', reflections, `${d.reflections.length} entries`)}
${section('How these numbers are counted', methods)}

<footer>
  <div class="ayah">"Indeed, Allah will not change the condition of a people until they change what is in themselves." — Quran 13:11</div>
  NAFS · ${esc(periodName)} report for ${esc(d.user.name)} · ${esc(d.start)} to ${esc(d.end)}
</footer>

${opts.autoPrint ? '<script>window.addEventListener("load",function(){setTimeout(function(){window.print()},250)})</script>' : ''}
</body>
</html>`
}

/** Suggested filename when saving the PDF. */
export function reportFileName(d: ReportData): string {
  const kind = d.period === 'weekly' ? 'Weekly' : 'Monthly'
  const who = d.user.name.replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '') || 'NAFS'
  return `NAFS-${kind}-Report-${who}-${d.start}`
}
