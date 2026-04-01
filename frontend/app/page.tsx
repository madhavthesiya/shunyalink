"use client";

import { useState, useEffect } from "react";
import { Header } from "@/components/header";
import { Footer } from "@/components/footer";
import { StatsModal } from "@/components/stats-modal";
import { QRModal } from "@/components/qr-modal";
import { MarketingPageShell } from "@/components/shell/page-shell";
import { HomeHero } from "@/components/home/home-hero";
import { HomeShortenSection } from "@/components/home/home-shorten-section";
import { BioShowcase } from "@/components/home/bio-showcase";
import { FeatureGrid } from "@/components/home/feature-grid";
import {
  PublicStatsStrip,
  type PublicStats,
} from "@/components/home/public-stats-strip";

interface ShortenResult {
  shortId: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
}

export default function Home() {
  const [result, setResult] = useState<ShortenResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showStats, setShowStats] = useState(false);
  const [showQR, setShowQR] = useState(false);
  const [stats, setStats] = useState<PublicStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    setIsLoggedIn(!!localStorage.getItem("authToken"));

    const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
    fetch(`${API_URL}/api/v1/url/stats/public`)
      .then((res) => {
        if (!res.ok) throw new Error("Failed to fetch stats");
        return res.json();
      })
      .then((data) => setStats(data))
      .catch((err) => console.error("Error fetching public stats:", err))
      .finally(() => setStatsLoading(false));
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
    <MarketingPageShell>
      <Header />

      <main className="flex-1 grid-pattern">
        <HomeHero isLoggedIn={isLoggedIn} />

        <HomeShortenSection
          error={error}
          result={result}
          onSuccess={handleSuccess}
          onError={handleError}
          onViewStats={() => setShowStats(true)}
          onGenerateQR={() => setShowQR(true)}
        />

        <BioShowcase isLoggedIn={isLoggedIn} />

        <FeatureGrid />

        <PublicStatsStrip stats={stats} loading={statsLoading} />
      </main>

      <Footer />

      {result != null && (
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
    </MarketingPageShell>
  );
}
