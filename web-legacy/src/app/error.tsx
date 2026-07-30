'use client'

import { useEffect } from 'react'
import { AlertTriangle, RefreshCw, Home } from 'lucide-react'
import Link from 'next/link'

export default function ErrorBoundary({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => { console.error('[app error]', error) }, [error])

  const isNetworkErr =
    error?.message?.toLowerCase().includes('fetch failed') ||
    error?.message?.toLowerCase().includes('timeout') ||
    error?.message?.toLowerCase().includes('network')

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-6 py-10">
      <div className="nafs-card w-full max-w-sm p-6 text-center space-y-4">
        <div className="mx-auto h-14 w-14 rounded-2xl bg-orange-500/15 border border-orange-400/30 flex items-center justify-center">
          <AlertTriangle size={26} className="text-orange-400" />
        </div>
        <div>
          <h1 className="text-lg font-bold text-foreground">
            {isNetworkErr ? "Can't reach the server" : 'Something went wrong'}
          </h1>
          <p className="text-sm text-muted-foreground mt-2 leading-relaxed">
            {isNetworkErr
              ? 'Your connection to NAFS dropped. Check your wifi/internet and try again — your data is safe.'
              : 'NAFS hit an unexpected error. The team has been notified. Try refreshing.'}
          </p>
        </div>

        {error?.message && !isNetworkErr && (
          <details className="text-left">
            <summary className="text-[11px] text-muted-foreground/70 cursor-pointer">Details</summary>
            <pre className="mt-2 text-[10px] text-muted-foreground/60 whitespace-pre-wrap break-all p-2 rounded-lg bg-white/3">
              {error.message}{error.digest ? `\n\nDigest: ${error.digest}` : ''}
            </pre>
          </details>
        )}

        <div className="flex gap-2 pt-2">
          <button onClick={() => reset()}
            className="flex-1 flex items-center justify-center gap-1.5 rounded-xl bg-primary py-3 text-sm font-semibold text-white hover:bg-teal-light active:scale-95">
            <RefreshCw size={14} /> Try again
          </button>
          <Link href="/dashboard" className="flex-1 flex items-center justify-center gap-1.5 rounded-xl border border-white/10 py-3 text-sm font-semibold text-muted-foreground hover:bg-white/5">
            <Home size={14} /> Home
          </Link>
        </div>
      </div>
    </div>
  )
}
