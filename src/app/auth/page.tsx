'use client'

export const dynamic = 'force-dynamic'

import { useState, useEffect, Suspense } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { createClient } from '@/lib/supabase/client'
import { cn } from '@/lib/utils'
import { validateEmail } from '@/lib/email-validator'

type Mode = 'signin' | 'signup' | 'forgot' | 'reset'
type Gender = 'male' | 'female' | 'prefer_not_to_say'

function AuthInner() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const supabase = createClient()

  const initialMode: Mode = searchParams.get('reset') === '1' ? 'reset' : 'signin'
  const [mode, setMode] = useState<Mode>(initialMode)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [suggestion, setSuggestion] = useState<string | null>(null)

  // Signup fields
  const [name, setName] = useState('')
  const [gender, setGender] = useState<Gender | ''>('')

  // Shared
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  // Reset
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  // Re-sync mode if URL param changes (e.g. after callback redirect)
  useEffect(() => {
    if (searchParams.get('reset') === '1') setMode('reset')
  }, [searchParams])

  function switchMode(next: Mode) {
    setMode(next)
    setError(null); setMessage(null); setSuggestion(null)
    setPassword(''); setNewPassword(''); setConfirmPassword('')
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null); setMessage(null); setSuggestion(null)
    setLoading(true)

    try {
      if (mode === 'signup') {
        if (!name.trim()) { setError('Please enter your name'); return }
        if (!gender) { setError('Please select your gender'); return }

        const v = validateEmail(email)
        if (!v.ok) {
          setError(v.error ?? 'Invalid email')
          if (v.suggestion) setSuggestion(v.suggestion)
          return
        }

        const { data, error } = await supabase.auth.signUp({
          email: email.trim().toLowerCase(),
          password,
          options: {
            emailRedirectTo: `${window.location.origin}/auth/callback`,
            data: { name: name.trim(), gender },
          },
        })

        if (error) { setError(error.message); return }

        if (data.user && data.session) {
          await supabase.from('users').upsert({
            id: data.user.id,
            email: data.user.email!,
            name: name.trim(),
            gender,
            onboarding_complete: false,
          })
          router.push('/onboarding')
        } else {
          setMessage(
            `We sent a confirmation link to ${email.trim().toLowerCase()}. ` +
            `Click it to activate your account — no link, no account.`
          )
        }
      }

      else if (mode === 'signin') {
        const { error } = await supabase.auth.signInWithPassword({ email, password })
        if (error) setError(error.message)
        else router.push('/')
      }

      else if (mode === 'forgot') {
        const v = validateEmail(email)
        if (!v.ok) {
          setError(v.error ?? 'Invalid email')
          if (v.suggestion) setSuggestion(v.suggestion)
          return
        }
        const next = encodeURIComponent('/auth?reset=1')
        const { error } = await supabase.auth.resetPasswordForEmail(email.trim().toLowerCase(), {
          redirectTo: `${window.location.origin}/auth/callback?next=${next}`,
        })
        if (error) { setError(error.message); return }
        setMessage(
          `Reset link sent. Check ${email.trim().toLowerCase()} (and your spam folder).`
        )
      }

      else if (mode === 'reset') {
        if (newPassword.length < 6) { setError('Password must be at least 6 characters'); return }
        if (newPassword !== confirmPassword) { setError('Passwords do not match'); return }
        const { error } = await supabase.auth.updateUser({ password: newPassword })
        if (error) { setError(error.message); return }
        setMessage('Password updated. Redirecting…')
        setTimeout(() => router.push('/dashboard'), 800)
      }
    } finally {
      setLoading(false)
    }
  }

  async function handleGoogle() {
    setLoading(true); setError(null)
    const { error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: { redirectTo: `${window.location.origin}/auth/callback` },
    })
    if (error) { setError(error.message); setLoading(false) }
  }

  // ---------- titles ----------
  const headline = ({
    signin: { title: 'Welcome back', sub: 'Sign in to continue your journey' },
    signup: { title: 'Create account', sub: 'Start your accountability journey' },
    forgot: { title: 'Forgot password?', sub: "Enter your email and we'll send a reset link" },
    reset:  { title: 'Set a new password', sub: 'Choose a password you can remember' },
  } as const)[mode]

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-6 py-10">

      {/* Logo */}
      <div className="mb-8 flex flex-col items-center gap-3 animate-slide-up">
        <div className="flex h-20 w-20 items-center justify-center rounded-3xl bg-primary/80 shadow-lg glow-teal">
          <span className="arabic text-4xl text-gold">ن</span>
        </div>
        <div className="text-center">
          <h1 className="text-3xl font-bold tracking-tight text-gold-gradient">NAFS</h1>
          <p className="arabic mt-1 text-lg text-muted-foreground">نَفْس</p>
          <p className="mt-2 text-sm text-muted-foreground">The mirror that doesn&apos;t lie.</p>
        </div>
      </div>

      {/* Card */}
      <div className="nafs-card w-full max-w-sm p-6 animate-slide-up">

        {/* Tabs (only signin/signup) */}
        {(mode === 'signin' || mode === 'signup') && (
          <div className="flex rounded-xl border border-white/10 bg-white/5 p-1 mb-6">
            <button onClick={() => switchMode('signin')}
              className={cn('flex-1 rounded-lg py-2 text-sm font-semibold transition-all',
                mode === 'signin' ? 'bg-primary text-white' : 'text-muted-foreground'
              )}>
              Sign in
            </button>
            <button onClick={() => switchMode('signup')}
              className={cn('flex-1 rounded-lg py-2 text-sm font-semibold transition-all',
                mode === 'signup' ? 'bg-primary text-white' : 'text-muted-foreground'
              )}>
              Sign up
            </button>
          </div>
        )}

        {/* Mode headline (forgot/reset) */}
        {(mode === 'forgot' || mode === 'reset') && (
          <div className="mb-6">
            <h2 className="text-lg font-bold text-foreground">{headline.title}</h2>
            <p className="text-xs text-muted-foreground mt-1">{headline.sub}</p>
          </div>
        )}

        {error && (
          <div className="mb-4 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-400">
            <p>{error}</p>
            {suggestion && (
              <button
                type="button"
                onClick={() => { setEmail(suggestion); setSuggestion(null); setError(null) }}
                className="mt-2 inline-flex items-center gap-1 rounded-lg border border-gold/40 bg-gold/10
                           px-3 py-1.5 text-xs font-semibold text-gold hover:bg-gold/20 transition-colors"
              >
                Use {suggestion}
              </button>
            )}
          </div>
        )}
        {message && (
          <div className="mb-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-400">
            {message}
          </div>
        )}

        {/* Google button — only on signin/signup */}
        {(mode === 'signin' || mode === 'signup') && (
          <>
            <button
              onClick={handleGoogle}
              disabled={loading}
              type="button"
              className="flex w-full items-center justify-center gap-3 rounded-xl border border-white/10
                         bg-white/5 py-3 font-medium text-foreground transition-all
                         hover:bg-white/10 disabled:opacity-50 active:scale-95"
            >
              <svg className="h-5 w-5" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
              </svg>
              Continue with Google
            </button>

            <div className="my-5 flex items-center gap-3">
              <div className="h-px flex-1 bg-border" />
              <span className="text-xs text-muted-foreground">or {mode === 'signup' ? 'sign up with email' : 'sign in with email'}</span>
              <div className="h-px flex-1 bg-border" />
            </div>
          </>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">

          {mode === 'signup' && (
            <>
              <div>
                <label className="section-header mb-1.5 block">Full name</label>
                <input type="text" value={name} onChange={(e) => setName(e.target.value)}
                  placeholder="Ahmad Siddique" required className="log-input" />
              </div>

              <div>
                <label className="section-header mb-1.5 block">Gender</label>
                <div className="grid grid-cols-2 gap-2">
                  <button type="button" onClick={() => setGender('male')}
                    className={cn('rounded-xl border py-3 text-sm font-medium transition-all',
                      gender === 'male'
                        ? 'border-primary bg-primary/20 text-primary'
                        : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
                    )}>
                    ♂ Male
                  </button>
                  <button type="button" onClick={() => setGender('female')}
                    className={cn('rounded-xl border py-3 text-sm font-medium transition-all',
                      gender === 'female'
                        ? 'border-primary bg-primary/20 text-primary'
                        : 'border-white/10 bg-white/5 text-muted-foreground hover:border-white/20'
                    )}>
                    ♀ Female
                  </button>
                </div>
              </div>
            </>
          )}

          {/* Email — for signin/signup/forgot */}
          {(mode === 'signin' || mode === 'signup' || mode === 'forgot') && (
            <div>
              <label className="section-header mb-1.5 block">Email</label>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                placeholder="ahmad@example.com" required className="log-input" autoComplete="email" />
            </div>
          )}

          {/* Password — for signin/signup */}
          {(mode === 'signin' || mode === 'signup') && (
            <div>
              <div className="flex items-baseline justify-between mb-1.5">
                <label className="section-header">Password</label>
                {mode === 'signin' && (
                  <button type="button" onClick={() => switchMode('forgot')}
                    className="text-xs font-medium text-gold hover:text-gold-light transition-colors">
                    Forgot?
                  </button>
                )}
              </div>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••" required minLength={6} className="log-input"
                autoComplete={mode === 'signin' ? 'current-password' : 'new-password'} />
              {mode === 'signup' && (
                <p className="mt-1 text-xs text-muted-foreground">At least 6 characters</p>
              )}
            </div>
          )}

          {/* Reset password fields */}
          {mode === 'reset' && (
            <>
              <div>
                <label className="section-header mb-1.5 block">New password</label>
                <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="••••••••" required minLength={6} className="log-input"
                  autoComplete="new-password" />
                <p className="mt-1 text-xs text-muted-foreground">At least 6 characters</p>
              </div>
              <div>
                <label className="section-header mb-1.5 block">Confirm new password</label>
                <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="••••••••" required minLength={6} className="log-input"
                  autoComplete="new-password" />
              </div>
            </>
          )}

          <button type="submit" disabled={loading}
            className="w-full rounded-xl bg-primary py-3.5 font-semibold text-white
                       transition-all hover:bg-teal-light disabled:opacity-50 active:scale-95">
            {loading
              ? 'Please wait…'
              : mode === 'signup' ? 'Create account'
              : mode === 'signin' ? 'Sign in'
              : mode === 'forgot' ? 'Send reset link'
              : 'Update password'}
          </button>

          {/* Back to sign-in (forgot/reset) */}
          {(mode === 'forgot' || mode === 'reset') && (
            <button type="button" onClick={() => switchMode('signin')}
              className="w-full text-center text-xs text-muted-foreground hover:text-foreground transition-colors">
              ← Back to sign in
            </button>
          )}
        </form>
      </div>

      {/* Islamic footer */}
      <p className="mt-8 max-w-xs text-center text-xs text-muted-foreground/60 leading-relaxed">
        &ldquo;And whoever fears Allah — He will make for him a way out.&rdquo;
        <br />— Quran 65:2
      </p>
    </div>
  )
}

export default function AuthPage() {
  return (
    <Suspense fallback={null}>
      <AuthInner />
    </Suspense>
  )
}
