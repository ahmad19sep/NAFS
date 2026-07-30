import { Redirect } from 'expo-router'

// Root redirect — handled by _layout.tsx auth guard.
// This file satisfies expo-router's requirement for an index route.
export default function Index() {
  return <Redirect href="/(app)/dashboard" />
}
