/**
 * Minimal markdown for AI replies: **bold**, bullet lines, paragraphs.
 * Deliberately small — model output is rendered as text, never as HTML.
 */
export default function RichText({ text }: { text: string }) {
  const renderInline = (s: string) =>
    s.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
      part.startsWith('**') && part.endsWith('**')
        ? <strong key={i} className="font-semibold text-gold">{part.slice(2, -2)}</strong>
        : <span key={i}>{part}</span>
    )
  return (
    <div className="space-y-1.5">
      {text.split('\n').map((line, i) => {
        const t = line.trim()
        if (!t) return null
        if (/^[-•*]\s+/.test(t)) {
          return (
            <div key={i} className="flex gap-2">
              <span className="text-gold mt-0.5">•</span>
              <span className="flex-1">{renderInline(t.replace(/^[-•*]\s+/, ''))}</span>
            </div>
          )
        }
        return <p key={i}>{renderInline(t)}</p>
      })}
    </div>
  )
}
