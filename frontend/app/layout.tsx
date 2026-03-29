import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import { Analytics } from '@vercel/analytics/next'
import './globals.css'
import { GoogleProvider } from '@/components/google-provider'
import { Toaster } from 'sonner'

const _geist = Geist({ subsets: ["latin"] });
const _geistMono = Geist_Mono({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: 'ShunyaLink | Pro-Tier URL Shortener & Bio-Link Platform',
  description: 'ShunyaLink is the ultimate distributed URL management tool. Shorten links, track geo-analytics, and build professional Bio-Link profiles for your brand.',
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
        url: '/icon.svg',
        type: 'image/svg+xml',
      },
    ],
    apple: '/apple-icon.png',
  },
  metadataBase: new URL('https://shunyalink.madhavv.me'),
  alternates: {
    canonical: '/',
  },
  openGraph: {
    title: 'ShunyaLink | URL Shortener & Bio-Link Platform',
    description: 'Transform long URLs into clean links and build your personalized Bio-Link profile. Ultimate link management for professionals.',
    url: 'https://shunyalink.madhavv.me',
    siteName: 'ShunyaLink',
    images: [
      {
        url: '/placeholder-logo.png', // Fallback until a dedicated OG image is added
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
    title: 'ShunyaLink | Shorten & Brand',
    description: 'The distributed URL management platform for professionals.',
    images: ['/placeholder-logo.png'],
  },
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="font-sans antialiased">
        <GoogleProvider>
          {children}
          <Toaster richColors position="top-right" />
          <Analytics />
        </GoogleProvider>
      </body>
    </html>
  )
}
