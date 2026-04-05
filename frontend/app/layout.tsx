import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import './globals.css'
import { GoogleProvider } from '@/components/google-provider'
import { ThemeProvider } from '@/components/theme-provider'
import { Toaster } from '@/components/ui/sonner'

const _geist = Geist({ subsets: ["latin"] });
const _geistMono = Geist_Mono({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: 'ShunyaLink',
  description: 'AI-secured short links with real-time Geo-IP analytics and professional developer portfolios. Build your digital legacy with ShunyaLink.',
  keywords: ['ShunyaLink', 'URL Shortener', 'Bio Link', 'Link Management', 'Geo Analytics', 'Distributed Systems'],
  generator: 'ShunyaLink Engine',
  icons: {
    icon: [
      {
        url: '/icon-light-32x32.png',
        media: '(prefers-color-scheme: light)',
      },
      {
        url: '/icon-dark-32x32.png',
        media: '(prefers-color-scheme: dark)',
      },
      {
        url: '/icon.png',
        type: 'image/png',
      },
    ],
    apple: '/apple-icon.png',
  },
  metadataBase: new URL('https://shunyalink.madhavv.me'),
  manifest: '/manifest.json',
  alternates: {
    canonical: '/',
  },
  openGraph: {
    title: 'ShunyaLink | Smart URL Shortener & Digital Identity',
    description: 'AI-secured short links with real-time Geo-IP analytics and professional developer portfolios. Build your digital legacy with ShunyaLink.',
    url: 'https://shunyalink.madhavv.me',
    siteName: 'ShunyaLink',
    images: [
      {
        url: '/logo-social.png', 
        width: 1200,
        height: 630,
        alt: 'ShunyaLink Dashboard Preview',
      },
    ],
    locale: 'en_US',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'ShunyaLink',
    description: 'The distributed URL management platform for professionals.',
    images: ['/logo-social.png'],
  },
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="font-sans antialiased min-h-screen">
        <ThemeProvider attribute="class" defaultTheme="dark" disableTransitionOnChange>
          <GoogleProvider>
            {children}
            <Toaster richColors position="top-right" />
            <Analytics />
          </GoogleProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
