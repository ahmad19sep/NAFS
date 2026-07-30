'use client'

import { useEffect } from 'react'

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => { console.error('[global error]', error) }, [error])

  return (
    <html lang="en">
      <body style={{
        margin: 0,
        minHeight: '100vh',
        background: '#0B1A2B',
        color: '#E5E7EB',
        fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px',
      }}>
        <div style={{
          maxWidth: 360,
          width: '100%',
          background: 'rgba(255,255,255,0.04)',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 16,
          padding: 24,
          textAlign: 'center',
        }}>
          <div style={{
            margin: '0 auto 16px',
            width: 56,
            height: 56,
            borderRadius: 16,
            background: 'rgba(249,115,22,0.15)',
            border: '1px solid rgba(251,146,60,0.3)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 28,
          }}>!</div>
          <h1 style={{ fontSize: 18, fontWeight: 700, margin: '0 0 8px' }}>NAFS hit a problem</h1>
          <p style={{ fontSize: 14, color: '#9CA3AF', margin: '0 0 20px', lineHeight: 1.5 }}>
            Something failed before the app could load. Try refreshing — your data is safe.
          </p>
          <button
            onClick={() => reset()}
            style={{
              width: '100%',
              padding: '12px 16px',
              borderRadius: 12,
              background: '#0F4C5C',
              color: '#fff',
              fontWeight: 600,
              fontSize: 14,
              border: 'none',
              cursor: 'pointer',
            }}>
            Try again
          </button>
        </div>
      </body>
    </html>
  )
}
