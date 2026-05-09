'use client'

export const dynamic = 'force-dynamic'

import { useState } from 'react'
import { createClient } from '@/lib/supabase/client'
import { useRouter } from 'next/navigation'

export default function AuthPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSignUp, setIsSignUp] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const router = useRouter()
  const supabase = createClient()

  async function handleEmailAuth(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    setMessage(null)

    if (isSignUp) {
      const { error } = await supabase.auth.signUp({
        email,
        password,
        options: { emailRedirectTo: `${window.location.origin}/auth/callback` },
      })
      if (error) setError(error.message)
      else setMessage('Check your email to confirm your account.')
    } else {
      const { error } = await supabase.auth.signInWithPassword({ email, password })
      if (error) setError(error.message)
      else router.push('/')
    }
    setLoading(false)
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-6">
      {/* Logo */}
      <div className="mb-10 flex flex-col items-center gap-3 animate-slide-up">
        <div className="flex h-20 w-20 items-center justify-center rounded-3xl bg-primary/80 shadow-lg glow-teal">
          <span className="arabic text-4xl text-gold">ن</span>
        </div>
        <div className="text-center">
          <h1 className="text-3xl font-bold tracking-tight text-gold-gradient">NAFS</h1>
          <p className="arabic mt-1 text-lg text-muted-foreground">نَفْس</p>
          <p className="mt-2 text-sm text-muted-foreground">
            The mirror that doesn&apos;t lie.
          </p>
        </div>
      </div>

      {/* Card */}
      <div className="nafs-card w-full max-w-sm p-6 animate-slide-up">
        <h2 className="mb-6 text-center text-xl font-semibold text-foreground">
          {isSignUp ? 'Create your account' : 'Welcome back'}
        </h2>

        {error && (
          <div className="mb-4 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400">
            {error}
          </div>
        )}
        {message && (
          <div className="mb-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-400">
            {message}
          </div>
        )}

        <form onSubmit={handleEmailAuth} className="space-y-4">
          <div>
            <label className="section-header mb-2 block">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="ahmad@example.com"
              required
              className="log-input"
            />
          </div>
          <div>
            <label className="section-header mb-2 block">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              minLength={6}
              className="log-input"
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-xl bg-primary py-3.5 font-semibold text-white
                       transition-all duration-200 hover:bg-teal-light disabled:opacity-50
                       active:scale-95"
          >
            {loading ? 'Loading…' : isSignUp ? 'Create account' : 'Sign in'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-muted-foreground">
          {isSignUp ? 'Already have an account?' : "Don't have an account?"}{' '}
          <button
            onClick={() => { setIsSignUp(!isSignUp); setError(null); setMessage(null) }}
            className="font-semibold text-gold hover:text-gold-light transition-colors"
          >
            {isSignUp ? 'Sign in' : 'Sign up'}
          </button>
        </p>
      </div>

      {/* Islamic footer */}
      <p className="mt-8 max-w-xs text-center text-xs text-muted-foreground/60 leading-relaxed">
        &ldquo;And whoever fears Allah — He will make for him a way out.&rdquo;
        <br />— Quran 65:2
      </p>
    </div>
  )
}
