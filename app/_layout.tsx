import '../global.css'
import { useEffect } from 'react'
import { Stack, useRouter, useSegments } from 'expo-router'
import { GestureHandlerRootView } from 'react-native-gesture-handler'
import { BottomSheetModalProvider } from '@gorhom/bottom-sheet'
import { StatusBar } from 'expo-status-bar'
import * as SplashScreen from 'expo-splash-screen'
import { supabase } from '@/lib/supabase'

SplashScreen.preventAutoHideAsync()

export default function RootLayout() {
  const router = useRouter()
  const segments = useSegments()

  useEffect(() => {
    let mounted = true

    supabase.auth.getSession().then(async ({ data }) => {
      if (!mounted) return
      await SplashScreen.hideAsync()

      if (!data.session) {
        router.replace('/auth')
        return
      }

      // Check onboarding
      const { data: profile } = await supabase
        .from('users')
        .select('onboarding_complete')
        .eq('id', data.session.user.id)
        .single()

      if (profile && !profile.onboarding_complete) {
        router.replace('/onboarding')
      } else {
        router.replace('/(app)/dashboard')
      }
    })

    const { data: authSub } = supabase.auth.onAuthStateChange((_event, session) => {
      if (!mounted) return
      const inAuth = segments[0] === 'auth' || segments[0] === 'onboarding'
      if (!session && !inAuth) {
        router.replace('/auth')
      }
    })

    return () => {
      mounted = false
      authSub.subscription.unsubscribe()
    }
  }, [])

  return (
    <GestureHandlerRootView className="flex-1">
      <BottomSheetModalProvider>
        <StatusBar style="light" />
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="auth" />
          <Stack.Screen name="onboarding" />
          <Stack.Screen name="(app)" />
        </Stack>
      </BottomSheetModalProvider>
    </GestureHandlerRootView>
  )
}
