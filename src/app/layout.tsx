import type { Metadata, Viewport } from 'next'
import { Inter } from 'next/font/google'
import './globals.css'
import PwaBootstrap from '@/components/PwaBootstrap'

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
})

export const metadata: Metadata = {
  title: 'Ascend',
  description: 'The accountability app that holds you to your goals, habits and values — with an AI coach that never sugarcoats.',
  manifest: '/manifest.json',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'black-translucent',
    title: 'Ascend',
  },
  icons: {
    icon: [
      { url: '/icons/icon.svg', type: 'image/svg+xml' },
      { url: '/icons/icon-192x192.svg', sizes: '192x192', type: 'image/svg+xml' },
      { url: '/icons/icon-512x512.svg', sizes: '512x512', type: 'image/svg+xml' },
    ],
    apple: '/icons/icon-192x192.svg',
  },
  keywords: ['accountability', 'self-improvement', 'habits', 'goals', 'productivity', 'AI coach'],
  authors: [{ name: 'Ahmad', url: 'https://github.com/ahmad19sep' }],
}

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  themeColor: '#0F4C5C',
  viewportFit: 'cover',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" className="dark">
      <head>
        {/* Apply saved theme before paint to avoid a flash of the default */}
        <script dangerouslySetInnerHTML={{ __html:
          `try{var t=localStorage.getItem('ascend-theme');if(t&&t!=='midnight')document.documentElement.dataset.theme=t}catch(e){}`
        }} />
        <meta name="mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
        <link rel="apple-touch-icon" href="/icons/icon-192x192.svg" />
      </head>
      <body className={`${inter.variable} font-sans antialiased`}>
        {children}
        <PwaBootstrap />
      </body>
    </html>
  )
}
