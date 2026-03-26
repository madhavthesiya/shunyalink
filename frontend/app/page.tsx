"use client";

import { useState, useEffect } from "react";
import { AlertCircle, Sparkles, Zap, BarChart3, Settings2, ArrowRight } from "lucide-react";
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

  useEffect(() => {
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
        <section className="pt-12 sm:pt-20 pb-12 px-4 sm:px-6">
          <div className="max-w-3xl mx-auto text-center">
            {/* Badge */}
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-primary/5 border border-primary/10 text-primary text-sm font-medium mb-8 animate-in fade-in-0 slide-in-from-bottom-4 duration-700">
              <Sparkles className="w-4 h-4" />
              <span>Free URL Shortener</span>
              <span className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
            </div>
            
            {/* Headline */}
            <h1 className="text-5xl sm:text-6xl md:text-7xl font-bold tracking-tight text-foreground mb-6 animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-100">
              <span className="text-balance">Make your links</span>
              <br />
              <span className="text-gradient">beautifully short</span>
            </h1>
            
            {/* Subheadline */}
            <p className="text-lg sm:text-xl text-muted-foreground max-w-xl mx-auto mb-4 animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-200 text-pretty leading-relaxed">
              Transform long URLs into clean links and host them on your personalized Link-in-Bio profile. 
              The ultimate Productivity & Branding Platform for your online identity.
            </p>

            {/* Trust indicators */}
            <div className="flex items-center justify-center gap-6 text-sm text-muted-foreground animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-300">
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-success" />
                No signup required
              </span>
              <span className="hidden sm:flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-success" />
                Instant redirects
              </span>
            </div>
          </div>
        </section>

        {/* URL Shortener Card */}
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

        {/* Features Section */}
        <section className="py-12 sm:py-16 border-t border-border/50">
          <div className="max-w-5xl mx-auto px-4 sm:px-6">
            <div className="text-center mb-16">
              <span className="inline-block px-3 py-1 rounded-full bg-primary/5 border border-primary/10 text-primary text-xs font-semibold uppercase tracking-wider mb-4">
                Features
              </span>
              <h2 className="text-3xl sm:text-4xl font-bold text-foreground mb-4 text-balance">
                Why choose ShunyaLink?
              </h2>
              <p className="text-muted-foreground max-w-lg mx-auto">
                Built for speed, designed for simplicity. Everything you need, nothing you do not.
              </p>
            </div>
            
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
              {/* Feature 1 */}
              <div className="group p-8 rounded-2xl bg-card/50 border border-border/50 hover:border-primary/20 hover:bg-card transition-all duration-300">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 flex items-center justify-center mb-6 group-hover:scale-110 transition-transform duration-300">
                  <Zap className="w-6 h-6 text-primary" />
                </div>
                <h3 className="text-lg font-semibold text-foreground mb-2">Lightning Fast</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  Redis caching ensures sub-millisecond redirects. Your links load instantly, every time.
                </p>
              </div>
              
              {/* Feature 2 */}
              <div className="group p-8 rounded-2xl bg-card/50 border border-border/50 hover:border-primary/20 hover:bg-card transition-all duration-300">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 flex items-center justify-center mb-6 group-hover:scale-110 transition-transform duration-300">
                  <BarChart3 className="w-6 h-6 text-primary" />
                </div>
                <h3 className="text-lg font-semibold text-foreground mb-2">Rich Analytics</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  Track total clicks, creation dates, and last access times for every shortened link.
                </p>
              </div>
              
              {/* Feature 3 */}
              <div className="group p-8 rounded-2xl bg-card/50 border border-border/50 hover:border-primary/20 hover:bg-card transition-all duration-300">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 flex items-center justify-center mb-6 group-hover:scale-110 transition-transform duration-300">
                  <Settings2 className="w-6 h-6 text-primary" />
                </div>
                <h3 className="text-lg font-semibold text-foreground mb-2">Custom Aliases</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  Brand your links with memorable custom aliases. Set expiration for temporary campaigns.
                </p>
              </div>
            </div>

            {/* CTA */}
            <div className="mt-16 text-center">
              <a 
                href="#" 
                className="inline-flex items-center gap-2 text-primary hover:text-primary/80 font-medium transition-colors group"
              >
                Start shortening for free
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
