import { useState } from 'react'
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
  Alert,
} from 'react-native'
import { useRouter } from 'expo-router'
import { supabase } from '@/lib/supabase'

type Mode = 'signin' | 'signup' | 'forgot'
type Gender = 'male' | 'female'

export default function AuthScreen() {
  const router = useRouter()
  const [mode, setMode] = useState<Mode>('signin')
  const [loading, setLoading] = useState(false)
  const [showPass, setShowPass] = useState(false)

  const [name, setName] = useState('')
  const [gender, setGender] = useState<Gender | ''>('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function switchMode(next: Mode) {
    setMode(next)
    setPassword('')
  }

  async function handleSubmit() {
    if (!email.trim()) { Alert.alert('Required', 'Please enter your email'); return }

    setLoading(true)
    try {
      if (mode === 'signup') {
        if (!name.trim()) { Alert.alert('Required', 'Please enter your name'); return }
        if (!gender) { Alert.alert('Required', 'Please select your gender'); return }
        if (password.length < 6) { Alert.alert('Password', 'Minimum 6 characters'); return }

        const { data, error } = await supabase.auth.signUp({
          email: email.trim().toLowerCase(),
          password,
          options: { data: { name: name.trim(), gender } },
        })
        if (error) { Alert.alert('Sign up failed', error.message); return }

        if (data.user) {
          await supabase.from('users').upsert({
            id: data.user.id,
            email: data.user.email ?? email.trim().toLowerCase(),
            name: name.trim(),
            onboarding_complete: false,
          })
          router.replace('/onboarding')
        } else {
          Alert.alert(
            'Check your email',
            `We sent a confirmation link to ${email.trim().toLowerCase()}. Tap it to activate your account.`
          )
        }
      } else if (mode === 'signin') {
        const { error } = await supabase.auth.signInWithPassword({
          email: email.trim().toLowerCase(),
          password,
        })
        if (error) { Alert.alert('Sign in failed', error.message); return }
        router.replace('/(app)/dashboard')
      } else if (mode === 'forgot') {
        const { error } = await supabase.auth.resetPasswordForEmail(
          email.trim().toLowerCase()
        )
        if (error) { Alert.alert('Error', error.message); return }
        Alert.alert('Check your email', `Reset link sent to ${email.trim().toLowerCase()}.`)
        switchMode('signin')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-navy"
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView
        contentContainerStyle={{ flexGrow: 1 }}
        className="flex-1"
        keyboardShouldPersistTaps="handled"
      >
        <View className="flex-1 items-center justify-center px-6 py-12">

          {/* Logo */}
          <View className="mb-10 items-center">
            <View className="h-20 w-20 rounded-3xl bg-teal items-center justify-center mb-3"
              style={{ shadowColor: '#0F4C5C', shadowRadius: 20, shadowOpacity: 0.6, elevation: 12 }}>
              <Text style={{ fontSize: 36, fontFamily: 'serif' }}>ن</Text>
            </View>
            <Text className="text-3xl font-bold text-gold tracking-wide">NAFS</Text>
            <Text className="text-lg text-muted-fg mt-1" style={{ fontFamily: 'serif' }}>نَفْس</Text>
            <Text className="text-xs text-muted-fg mt-1">The mirror that doesn't lie.</Text>
          </View>

          {/* Card */}
          <View className="w-full max-w-sm rounded-2xl border border-white/10 bg-white/5 p-6">

            {/* Mode tabs (signin/signup only) */}
            {(mode === 'signin' || mode === 'signup') && (
              <View className="flex-row rounded-xl border border-white/10 bg-white/5 p-1 mb-6">
                {(['signin', 'signup'] as const).map((m) => (
                  <TouchableOpacity
                    key={m}
                    onPress={() => switchMode(m)}
                    className={`flex-1 rounded-lg py-2.5 items-center ${mode === m ? 'bg-teal' : ''}`}
                  >
                    <Text className={`text-sm font-semibold ${mode === m ? 'text-white' : 'text-muted-fg'}`}>
                      {m === 'signin' ? 'Sign in' : 'Sign up'}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            )}

            {/* Forgot header */}
            {mode === 'forgot' && (
              <View className="mb-5">
                <Text className="text-lg font-bold text-white">Forgot password?</Text>
                <Text className="text-xs text-muted-fg mt-1">Enter your email and we'll send a reset link</Text>
              </View>
            )}

            <View className="gap-y-4">
              {/* Name (signup only) */}
              {mode === 'signup' && (
                <View>
                  <Text className="text-xs font-semibold text-muted-fg uppercase tracking-wider mb-1.5">Full name</Text>
                  <TextInput
                    className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                    placeholder="Ahmad Siddique"
                    placeholderTextColor="#6B8CA8"
                    value={name}
                    onChangeText={setName}
                    autoCapitalize="words"
                  />
                </View>
              )}

              {/* Gender (signup only) */}
              {mode === 'signup' && (
                <View>
                  <Text className="text-xs font-semibold text-muted-fg uppercase tracking-wider mb-1.5">Gender</Text>
                  <View className="flex-row gap-x-2">
                    {(['male', 'female'] as const).map((g) => (
                      <TouchableOpacity
                        key={g}
                        onPress={() => setGender(g)}
                        className={`flex-1 rounded-xl border py-3 items-center ${
                          gender === g ? 'border-teal bg-teal/20' : 'border-white/10 bg-white/5'
                        }`}
                      >
                        <Text className={`text-sm font-medium ${gender === g ? 'text-teal-light' : 'text-muted-fg'}`}>
                          {g === 'male' ? '♂ Male' : '♀ Female'}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              )}

              {/* Email */}
              <View>
                <Text className="text-xs font-semibold text-muted-fg uppercase tracking-wider mb-1.5">Email</Text>
                <TextInput
                  className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white"
                  placeholder="ahmad@example.com"
                  placeholderTextColor="#6B8CA8"
                  value={email}
                  onChangeText={setEmail}
                  keyboardType="email-address"
                  autoCapitalize="none"
                  autoComplete="email"
                />
              </View>

              {/* Password (signin/signup only) */}
              {(mode === 'signin' || mode === 'signup') && (
                <View>
                  <View className="flex-row items-center justify-between mb-1.5">
                    <Text className="text-xs font-semibold text-muted-fg uppercase tracking-wider">Password</Text>
                    {mode === 'signin' && (
                      <TouchableOpacity onPress={() => switchMode('forgot')}>
                        <Text className="text-xs font-medium text-gold">Forgot?</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                  <View className="relative">
                    <TextInput
                      className="rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white pr-12"
                      placeholder="••••••••"
                      placeholderTextColor="#6B8CA8"
                      value={password}
                      onChangeText={setPassword}
                      secureTextEntry={!showPass}
                      autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                    />
                    <TouchableOpacity
                      onPress={() => setShowPass(!showPass)}
                      className="absolute right-3 top-3 p-1"
                    >
                      <Text className="text-muted-fg text-sm">{showPass ? '🙈' : '👁️'}</Text>
                    </TouchableOpacity>
                  </View>
                  {mode === 'signup' && (
                    <Text className="mt-1 text-xs text-muted-fg">At least 6 characters</Text>
                  )}
                </View>
              )}

              {/* Submit */}
              <TouchableOpacity
                onPress={handleSubmit}
                disabled={loading}
                className="w-full rounded-xl bg-teal py-3.5 items-center mt-1"
                style={{ opacity: loading ? 0.5 : 1 }}
              >
                {loading
                  ? <ActivityIndicator color="#fff" />
                  : <Text className="text-white font-semibold text-base">
                      {mode === 'signup' ? 'Create account'
                       : mode === 'forgot' ? 'Send reset link'
                       : 'Sign in'}
                    </Text>
                }
              </TouchableOpacity>

              {/* Back to sign-in */}
              {mode === 'forgot' && (
                <TouchableOpacity onPress={() => switchMode('signin')} className="items-center mt-1">
                  <Text className="text-xs text-muted-fg">← Back to sign in</Text>
                </TouchableOpacity>
              )}
            </View>
          </View>

          {/* Footer */}
          <Text className="mt-8 text-center text-xs text-muted-fg/60 leading-5 max-w-xs">
            "And whoever fears Allah — He will make for him a way out."{'\n'}— Quran 65:2
          </Text>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  )
}
