'use client'

import { useEffect, useState } from 'react'
import { Download, Share, Plus, X } from 'lucide-react'

type BIPEvent = Event & {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

const DISMISS_KEY = 'nafs:install-dismissed-at'
const COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000 // 7 days

function isStandalone() {
  if (typeof window === 'undefined') return false
  const mql = window.matchMedia('(display-mode: standalone)').matches
  const iosStandalone = (window.navigator as any).standalone === true
  return mql || iosStandalone
}

function isIOSSafari() {
  if (typeof window === 'undefined') return false
  const ua = window.navigator.userAgent
  const isIOS = /iPad|iPhone|iPod/.test(ua) && !(window as any).MSStream
  const isSafari = /Safari/.test(ua) && !/CriOS|FxiOS|EdgiOS/.test(ua)
  return isIOS && isSafari
}

function recentlyDismissed() {
  if (typeof window === 'undefined') return false
  const ts = Number(window.localStorage.getItem(DISMISS_KEY) ?? 0)
  return ts > 0 && Date.now() - ts < COOLDOWN_MS
}

export default function InstallPrompt() {
  const [bip, setBip] = useState<BIPEvent | null>(null)
  const [showIOS, setShowIOS] = useState(false)
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (isStandalone() || recentlyDismissed()) return

    const onBIP = (e: Event) => {
      e.preventDefault()
      setBip(e as BIPEvent)
      setOpen(true)
    }
    window.addEventListener('beforeinstallprompt', onBIP)

    if (isIOSSafari()) {
      const t = setTimeout(() => { setShowIOS(true); setOpen(true) }, 4000)
      return () => { window.removeEventListener('beforeinstallprompt', onBIP); clearTimeout(t) }
    }
    return () => window.removeEventListener('beforeinstallprompt', onBIP)
  }, [])

  const dismiss = () => {
    try { window.localStorage.setItem(DISMISS_KEY, String(Date.now())) } catch {}
    setOpen(false)
  }

  const install = async () => {
    if (!bip) return
    await bip.prompt()
    const choice = await bip.userChoice
    if (choice.outcome === 'accepted') setOpen(false)
    else dismiss()
    setBip(null)
  }

  if (!open) return null

  return (
    <div className="fixed inset-x-3 bottom-3 z-[100] sm:left-auto sm:right-3 sm:bottom-3 sm:max-w-sm">
      <div className="nafs-card p-4 shadow-2xl border border-white/10 bg-background/95 backdrop-blur-xl">
        <div className="flex items-start gap-3">
          <div className="h-10 w-10 rounded-xl bg-primary/15 border border-primary/30 flex items-center justify-center shrink-0">
            <Download size={18} className="text-primary" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-foreground">Install NAFS</p>
            {bip && !showIOS && (
              <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
                Add NAFS to your home screen for one-tap access and a full-screen experience.
              </p>
            )}
            {showIOS && (
              <p className="text-xs text-muted-foreground mt-1 leading-relaxed">
                Tap <Share size={11} className="inline mx-0.5" /> Share, then <span className="font-semibold">Add to Home Screen</span> <Plus size={11} className="inline mx-0.5" /> to install NAFS.
              </p>
            )}
          </div>
          <button onClick={dismiss} aria-label="Dismiss" className="p-1 rounded-lg hover:bg-white/5 text-muted-foreground">
            <X size={16} />
          </button>
        </div>

        {bip && !showIOS && (
          <div className="flex gap-2 mt-3">
            <button onClick={install}
              className="flex-1 rounded-xl bg-primary py-2.5 text-sm font-semibold text-white hover:bg-teal-light active:scale-95">
              Install
            </button>
            <button onClick={dismiss}
              className="px-4 rounded-xl border border-white/10 py-2.5 text-sm text-muted-foreground hover:bg-white/5">
              Later
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
