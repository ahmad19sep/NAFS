import BottomNav from '@/components/BottomNav'
import PushNotificationSetup from '@/components/PushNotificationSetup'

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <PushNotificationSetup />
      <main className="flex-1 page-container scroll-container">
        {children}
      </main>
      <BottomNav />
    </div>
  )
}
