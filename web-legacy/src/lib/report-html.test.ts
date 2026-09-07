import { describe, it, expect } from 'vitest'
import { reviewToHtml } from './report-html'

/**
 * The coach's read is model output rendered into a document the user prints
 * and may share. The property these pin: it can become bold text and
 * paragraphs, and nothing else.
 */

describe('reviewToHtml', () => {
  it('turns a bold heading into its own heading paragraph', () => {
    const html = reviewToHtml('**In plain words** — a steady week.\nYou logged five of seven days.')
    expect(html).toContain('<strong>In plain words</strong>')
    expect(html).toContain('class="read-h"')
    expect(html).toContain('class="read-p"')
  })

  it('never lets model text become markup', () => {
    const html = reviewToHtml('<script>alert(1)</script> and <img src=x onerror=y>')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;script&gt;')
  })

  it('escapes before bolding, so markup inside a bold run is inert', () => {
    const html = reviewToHtml('**<b>hi</b>**')
    expect(html).toContain('<strong>&lt;b&gt;hi&lt;/b&gt;</strong>')
    expect(html).not.toContain('<b>hi</b>')
  })

  it('escapes quotes and ampersands', () => {
    const html = reviewToHtml('You said "it\'s fine" & moved on')
    expect(html).toContain('&quot;')
    expect(html).toContain('&amp;')
  })

  it('drops blank lines rather than emitting empty paragraphs', () => {
    const html = reviewToHtml('One\n\n\n   \nTwo')
    expect(html.match(/<p /g)).toHaveLength(2)
  })

  it('leaves an unmatched asterisk alone', () => {
    expect(reviewToHtml('a ** b')).toContain('a ** b')
  })

  it('returns nothing for empty input', () => {
    expect(reviewToHtml('')).toBe('')
    expect(reviewToHtml('   \n  ')).toBe('')
  })
})
