import { useEffect } from 'react'

// Locks body scroll while `open` is true. Restores on unmount/close.
// Use inside any modal/sheet to stop the page from scrolling underneath
// when users drag on the backdrop or pull past the modal's edges.
export function useBodyScrollLock(open: boolean) {
  useEffect(() => {
    if (!open) return
    const body = document.body
    const html = document.documentElement
    const prevBodyOverflow = body.style.overflow
    const prevHtmlOverflow = html.style.overflow
    const prevBodyOverscroll = body.style.overscrollBehavior
    const scrollY = window.scrollY

    body.style.overflow = 'hidden'
    html.style.overflow = 'hidden'
    body.style.overscrollBehavior = 'none'

    return () => {
      body.style.overflow = prevBodyOverflow
      html.style.overflow = prevHtmlOverflow
      body.style.overscrollBehavior = prevBodyOverscroll
      window.scrollTo(0, scrollY)
    }
  }, [open])
}
