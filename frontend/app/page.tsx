"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { AlertCircle, Sparkles, Zap, BarChart3, Settings2, ArrowRight, User, ShieldCheck, Lock } from "lucide-react";
import { Header } from "@/components/header";
import { ShortenerForm } from "@/components/shortener-form";
import { ResultBox } from "@/components/result-box";
import { StatsModal } from "@/components/stats-modal";
import { QRModal } from "@/components/qr-modal";
import { Footer } from "@/components/footer";

interface ShortenResult {
  shortId: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
}

interface PublicStats {
  totalLinks: number;
  totalUsers: number;
  totalClicks: number;
}

export default function Home() {
  const [result, setResult] = useState<ShortenResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showStats, setShowStats] = useState(false);
  const [showQR, setShowQR] = useState(false);
  const [stats, setStats] = useState<PublicStats | null>(null);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    setIsLoggedIn(!!localStorage.getItem("authToken"));
    
    const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
    fetch(`${API_URL}/api/v1/url/stats/public`)
      .then(res => {
        if (!res.ok) throw new Error("Failed to fetch stats");
        return res.json();
      })
      .then(data => setStats(data))
      .catch(err => console.error("Error fetching public stats:", err));
  }, []);

  const handleSuccess = (data: ShortenResult) => {
    setResult(data);
    setError(null);
  };

  const handleError = (message: string) => {
    setError(message);
    setResult(null);
  };

  return (
    <div className="min-h-screen flex flex-col bg-background gradient-bg">
      <Header />

      <main className="flex-1 grid-pattern">
        {/* Hero Section */}
        <section className="pt-4 sm:pt-10 pb-12 px-4 sm:px-6">
          <div className="max-w-3xl mx-auto text-center">
            
            {/* Headline */}
            <h1 className="text-5xl sm:text-6xl md:text-7xl font-bold tracking-tight text-foreground mb-6 animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-100">
              <span className="text-balance">Shorten Links.</span>
              <br />
              <span className="text-gradient">Build your Identity.</span>
            </h1>
            
            {/* Subheadline */}
            <p className="text-lg sm:text-xl text-muted-foreground max-w-2xl mx-auto mb-8 animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-200 text-pretty leading-relaxed">
              Transform long URLs into clean links and host them on your personalized 
              <strong> Shunya Link-in-Bio</strong> profile. The ultimate Productivity & Branding Platform 
              for your online identity.
            </p>

            {/* Trust indicators & Secondary CTA */}
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4 sm:gap-6 animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-300">
              <div className="flex items-center gap-6 text-sm text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-success" />
                  No signup required
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-success" />
                  Instant redirects
                </span>
              </div>
              <div className="hidden sm:block w-px h-4 bg-border/50" />
              <Link 
                href={isLoggedIn ? "/dashboard?tab=settings" : "/register"} 
                className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-primary/10 text-primary hover:bg-primary/20 text-sm font-semibold transition-all group"
              >
                {isLoggedIn ? "Manage your Bio Link" : "Claim your Bio Link"}
                <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </Link>
            </div>
          </div>
        </section>

        {/* Result Display & Quick Spotlight */}
        <section className="pb-12 sm:pb-16 px-4 sm:px-6">
          <div className="max-w-xl mx-auto">
            <div className="glass-card rounded-2xl shadow-2xl shadow-primary/5 p-8 sm:p-10 animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-400">
              <ShortenerForm onSuccess={handleSuccess} onError={handleError} />
              
              {/* Error Display */}
              {error && (
                <div className="mt-6 flex items-start gap-3 p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive animate-in fade-in-0 slide-in-from-bottom-4 duration-300">
                  <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="font-semibold">Something went wrong</p>
                    <p className="text-sm opacity-80 mt-0.5">{error}</p>
                  </div>
                </div>
              )}
            </div>

            {/* Result Display */}
            {result && (
              <div className="mt-8">
                <ResultBox
                  shortUrl={result.shortUrl}
                  shortId={result.shortId}
                  onViewStats={() => setShowStats(true)}
                  onGenerateQR={() => setShowQR(true)}
                />
              </div>
            )}
          </div>
        </section>

        {/* Branding & Identity Section (The Bio Profile) */}
        <section className="py-20 sm:py-32 bg-primary/5 border-y border-primary/10 relative overflow-hidden">
          <div className="absolute inset-0 grid-pattern opacity-30" />
          <div className="max-w-5xl mx-auto px-4 sm:px-6 relative z-10">
            <div className="flex flex-col md:flex-row items-center gap-12 sm:gap-20">
              <div className="flex-1 text-center md:text-left">
                <span className="inline-block px-3 py-1 rounded-full bg-primary/10 text-primary text-xs font-bold uppercase tracking-wider mb-6">
                  Personal Branding
                </span>
                <h2 className="text-4xl sm:text-5xl font-bold text-foreground mb-6 leading-tight">
                  One Link to <br />
                  <span className="text-gradient">Rule them all.</span>
                </h2>
                <p className="text-lg text-muted-foreground mb-8 leading-relaxed">
                  Tired of changing Bio links everyday? ShunyaLink gives you a permanent, 
                  beautiful storefront for your online identity. Connect your Instagram, 
                  Twitter, Portfolio, and Shop in one place.
                </p>
                <div className="space-y-4 mb-10">
                  <div className="flex items-center gap-3 text-foreground font-medium">
                    <div className="w-6 h-6 rounded-full bg-success/20 flex items-center justify-center">
                      <Zap className="w-3.5 h-3.5 text-success" />
                    </div>
                    Claim your unique handle: shunyalink.me/@name
                  </div>
                  <div className="flex items-center gap-3 text-foreground font-medium">
                    <div className="w-6 h-6 rounded-full bg-success/20 flex items-center justify-center">
                      <Zap className="w-3.5 h-3.5 text-success" />
                    </div>
                    Unlimited social links & custom themes
                  </div>
                </div>
                 <Link 
                  href={isLoggedIn ? "/dashboard?tab=settings" : "/register"} 
                  className="inline-flex items-center gap-2 px-8 py-4 rounded-xl bg-primary text-primary-foreground font-bold shadow-lg shadow-primary/20 hover:scale-105 transition-all group"
                >
                  {isLoggedIn ? "Manage your Bio Profile" : "Create your Bio Profile"}
                  <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
                </Link>
              </div>
              
              <div className="flex-1 relative pb-12 sm:pb-0">
                <div className="relative z-10 max-w-[300px] mx-auto">
                  {/* Mobile Phone Frame */}
                  <div className="relative rounded-[3rem] border-8 border-foreground/5 shadow-2xl p-4 bg-background aspect-[9/18.5] overflow-hidden group hover:scale-105 transition-transform duration-500">
                    {/* Notch/Dynamic Island */}
                    <div className="absolute top-2 left-1/2 -translate-x-1/2 w-20 h-5 bg-foreground/10 rounded-full z-20" />
                    
                    {/* Screen Content */}
                    <div className="h-full w-full rounded-[2.5rem] bg-gradient-to-b from-primary/5 to-transparent p-6 flex flex-col items-center">
                      {/* Avatar Wrapper */}
                      <div className="w-20 h-20 rounded-full bg-gradient-to-tr from-primary/20 via-primary/40 to-primary/20 p-1 mb-4 mt-4">
                        <div className="w-full h-full rounded-full bg-background border-2 border-primary/20 overflow-hidden flex items-center justify-center">
                          <User className="w-10 h-10 text-primary/40" />
                        </div>
                      </div>
                      
                      {/* Text details */}
                      <div className="text-center mb-10">
                        <h4 className="text-lg font-bold text-foreground mb-1">Your Name</h4>
                        <p className="text-sm font-semibold text-primary mb-2">@username</p>
                        <p className="text-[10px] text-muted-foreground leading-tight px-4 opacity-70">
                          Your bio will appear here to welcome your visitors...
                        </p>
                      </div>
                      
                      {/* Link Buttons */}
                      <div className="w-full space-y-3 mb-auto">
                        {['Portfolio', 'Twitter', 'GitHub'].map((label) => (
                          <div key={label} className="w-full py-3 px-4 rounded-xl border border-primary/10 bg-background shadow-sm text-center text-[10px] font-bold text-foreground/80 cursor-default">
                            {label}
                          </div>
                        ))}
                      </div>
                      
                      {/* Brand Footer */}
                      <div className="flex items-center gap-1.5 opacity-40 mt-6 group-hover:opacity-60 transition-opacity">
                        <Sparkles className="w-3 h-3 text-primary" />
                        <span className="text-[10px] font-bold tracking-tight">shunyalink.com</span>
                      </div>
                    </div>
                  </div>
                  
                  {/* Decorative blobs */}
                  <div className="absolute -top-10 -right-10 w-40 h-40 bg-primary/20 blur-[80px] -z-10 animate-pulse" />
                  <div className="absolute -bottom-10 -left-10 w-40 h-40 bg-primary/30 blur-[80px] -z-10 animate-pulse delay-700" />
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Features Section */}
        <section className="py-20 sm:py-32 border-t border-border/50 bg-secondary/5">
          <div className="max-w-6xl mx-auto px-4 sm:px-6">
            <div className="text-center mb-16">
              <span className="inline-block px-3 py-1 rounded-full bg-primary/5 border border-primary/10 text-primary text-xs font-semibold uppercase tracking-wider mb-4">
                Enterprise Mastery
              </span>
              <h2 className="text-3xl sm:text-5xl font-bold text-foreground mb-4 text-balance">
                The absolute <span className="text-gradient">Gold Standard</span> of Link Management.
              </h2>
              <p className="text-muted-foreground max-w-2xl mx-auto text-lg">
                ShunyaLink puts the power of a MAANG-grade infrastructure in your hands. 
                Built for scale, security, and professional branding.
              </p>
            </div>
            
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
              {/* Feature 1: AI Metadata */}
              <div className="group p-8 rounded-3xl bg-card border border-border/50 hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 transition-all duration-500">
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-primary/20 to-primary/5 flex items-center justify-center mb-6 group-hover:rotate-6 transition-transform">
                  <Sparkles className="w-7 h-7 text-primary" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-3">Smart AI Metadata</h3>
                <p className="text-muted-foreground leading-relaxed">
                  Automatic link titles and descriptions. Our AI-powered scraping 
                  engine ensures your links look professional without manual effort.
                </p>
              </div>
              
              {/* Feature 2: High-Fi Analytics */}
              <div className="group p-8 rounded-3xl bg-card border border-border/50 hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 transition-all duration-500">
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-primary/20 to-primary/5 flex items-center justify-center mb-6 group-hover:rotate-6 transition-transform">
                  <BarChart3 className="w-7 h-7 text-primary" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-3">Time-Series Analytics</h3>
                <p className="text-muted-foreground leading-relaxed">
                  Go beyond total counts. Visualize click patterns with millisecond 
                  precision using high-fidelity Area and Line charts.
                </p>
              </div>
              
              {/* Feature 3: Geo-Distribution */}
              <div className="group p-8 rounded-3xl bg-card border border-border/50 hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 transition-all duration-500">
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-primary/20 to-primary/5 flex items-center justify-center mb-6 group-hover:rotate-6 transition-transform">
                  <Zap className="w-7 h-7 text-primary" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-3">Geo-Location Tracking</h3>
                <p className="text-muted-foreground leading-relaxed">
                  Understand your global reach. See exactly which countries and 
                  regions are engaging with your content in real-time.
                </p>
              </div>

              {/* Feature 4: Secure Logout */}
              <div className="group p-8 rounded-3xl bg-card border border-border/50 hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 transition-all duration-500">
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-primary/20 to-primary/5 flex items-center justify-center mb-6 group-hover:rotate-6 transition-transform">
                  <ShieldCheck className="w-7 h-7 text-primary" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-3">Secure Revocation</h3>
                <p className="text-muted-foreground leading-relaxed">
                  Real-time session blacklisting. Your security matters—once you 
                  log out, your session is instantly purged from our global network.
                </p>
              </div>

              {/* Feature 5: Protection */}
              <div className="group p-8 rounded-3xl bg-card border border-border/50 hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 transition-all duration-500">
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-primary/20 to-primary/5 flex items-center justify-center mb-6 group-hover:rotate-6 transition-transform">
                  <Lock className="w-7 h-7 text-primary" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-3">Password Encryption</h3>
                <p className="text-muted-foreground leading-relaxed">
                  Protect your sensitive assets with AES-256 password protection 
                  and link-specific security challenges.
                </p>
              </div>

              {/* Feature 6: Identity */}
              <div className="group p-8 rounded-3xl bg-card border border-border/50 hover:border-primary/30 hover:shadow-xl hover:shadow-primary/5 transition-all duration-500">
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-primary/20 to-primary/5 flex items-center justify-center mb-6 group-hover:rotate-6 transition-transform">
                  <Settings2 className="w-7 h-7 text-primary" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-3">Custom Branding</h3>
                <p className="text-muted-foreground leading-relaxed">
                  Secure your unique @handle and customize your Bio profile storefront 
                  to showcase your full online identity.
                </p>
              </div>
            </div>

            {/* CTA */}
            <div className="mt-16 text-center">
              <a 
                href="/register" 
                className="inline-flex items-center gap-2 text-primary hover:text-primary/80 font-bold transition-colors group"
              >
                Start building your identity for free
                <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </a>
            </div>
          </div>
        </section>

        {/* Public Stats Banner */}
        {stats && (
          <section className="pb-16 px-4 sm:px-6 mt-8 border-t border-border/50 pt-16">
            <div className="max-w-4xl mx-auto">
              <div className="text-center mb-10">
                <h2 className="text-3xl font-bold text-foreground">By the numbers</h2>
              </div>
              <div className="grid grid-cols-3 gap-4 sm:gap-8 text-center animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-300">
                <div className="p-4 sm:p-6 rounded-2xl bg-card border border-border/50 hover:border-primary/20 transition-all duration-300">
                  <div className="text-3xl sm:text-4xl font-bold tracking-tight text-primary mb-1">{stats.totalLinks.toLocaleString()}</div>
                  <div className="text-sm font-medium text-muted-foreground uppercase tracking-wider">Links Created</div>
                </div>
                <div className="p-4 sm:p-6 rounded-2xl bg-card border border-border/50 hover:border-primary/20 transition-all duration-300">
                  <div className="text-3xl sm:text-4xl font-bold tracking-tight text-primary mb-1">{stats.totalClicks.toLocaleString()}</div>
                  <div className="text-sm font-medium text-muted-foreground uppercase tracking-wider">Total Clicks</div>
                </div>
                <div className="p-4 sm:p-6 rounded-2xl bg-card border border-border/50 hover:border-primary/20 transition-all duration-300">
                  <div className="text-3xl sm:text-4xl font-bold tracking-tight text-primary mb-1">{stats.totalUsers.toLocaleString()}</div>
                  <div className="text-sm font-medium text-muted-foreground uppercase tracking-wider">Happy Users</div>
                </div>
              </div>
            </div>
          </section>
        )}
      </main>

      <Footer />

      {/* Modals */}
      {result && (
        <>
          <StatsModal
            shortId={result.shortId}
            isOpen={showStats}
            onClose={() => setShowStats(false)}
          />
          <QRModal
            shortUrl={result.shortUrl}
            shortId={result.shortId}
            isOpen={showQR}
            onClose={() => setShowQR(false)}
          />
        </>
      )}
    </div>
  );
}
