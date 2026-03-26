"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { LogOut, Copy, QrCode, Loader2, AlertCircle, TrendingUp, Link2, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ShortenerForm } from "@/components/shortener-form";
import { QRModal } from "@/components/qr-modal";
import { StatsModal } from "@/components/stats-modal";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface UrlData {
  showOnBio: boolean;
  shortId: string;
  longUrl: string;
  clickCount: number;
  lastAccessedTime: string | null;
  createdAt: string;
}

interface ShortenResponse {
  shortId: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
}

export default function DashboardPage() {
  const router = useRouter();
  const [userName, setUserName] = useState<string>("");
  const [urls, setUrls] = useState<UrlData[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [showQR, setShowQR] = useState(false);
  const [showStats, setShowStats] = useState(false);
  const [selectedShortId, setSelectedShortId] = useState<string>("");

  // Check authentication and load data
  useEffect(() => {
    const token = localStorage.getItem("authToken");
    const name = localStorage.getItem("userName");

    if (!token) {
      router.push("/login");
      return;
    }

    setUserName(name || "User");
    loadUserUrls(token);

    const intervalId = setInterval(() => {
      loadUserUrls(token, false);
    }, 5000);

    return () => clearInterval(intervalId);
  }, [router]);

  const loadUserUrls = async (token: string, showLoading: boolean = true) => {
    try {
      if (showLoading) setIsLoading(true);
      const response = await fetch(`${API_URL}/api/v1/url/my-links`, {
        headers: { Authorization: `Bearer ${token}` },
        cache: 'no-store',
      });

      if (response.status === 401) {
        localStorage.removeItem("authToken");
        router.push("/login");
        return;
      }

      const data = await response.json();
      if (response.ok) {
        setUrls(data);
        // Only clear the error state on active page loads, not silent background polls.
        // This prevents the 5-sec poller from erasing form errors like "403 Forbidden".
        if (showLoading) {
          setError(null);
        }
      } else {
        // Only surface background polling errors if they persist, or just rely on active operations
        if (showLoading) {
          setError(data?.message || "Failed to load URLs");
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load URLs");
    } finally {
      if (showLoading) setIsLoading(false);
    }
  };

  const handleSuccess = (data: ShortenResponse) => {
    // Add newly created URL to the top of the list
    setUrls([
      {
        shortId: data.shortId,
        longUrl: data.longUrl,
        clickCount: 0,
        lastAccessedTime: null,
        createdAt: data.createdAt,
      },
      ...urls,
    ]);
    setError(null);
  };

  const handleError = (message: string) => {
    setError(message);
  };

  const handleLogout = () => {
    localStorage.removeItem("authToken");
    localStorage.removeItem("userEmail");
    localStorage.removeItem("userName");
    router.push("/");
  };

  const copyToClipboard = (shortId: string) => {
    const shortUrl = `${API_URL}/${shortId}`;
    navigator.clipboard.writeText(shortUrl);
    setCopiedId(shortId);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const deleteUrl = async (shortId: string) => {
    if (!confirm("Are you sure you want to delete this URL?")) return;

    const token = localStorage.getItem("authToken");
    try {
      const response = await fetch(`${API_URL}/api/v1/url/${shortId}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      });

      if (response.ok) {
        setUrls(urls.filter((u) => u.shortId !== shortId));
      } else {
        const data = await response.json();
        setError(data?.message || "Failed to delete URL");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete URL");
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  };

  return (
    <div className="min-h-screen flex flex-col bg-background gradient-bg">
      {/* Custom Dashboard Header */}
      <header className="glass-card sticky top-0 z-50 border-b border-border/50">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="relative flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-primary/80 shadow-lg shadow-primary/25">
              <Link2 className="w-4 h-4 text-primary-foreground" />
              <div className="absolute inset-0 rounded-xl bg-primary/20 blur-md -z-10" />
            </div>
            <span className="text-xl font-semibold tracking-tight text-foreground">
              Shunya<span className="text-gradient">Link</span>
            </span>
          </div>

          <div className="flex items-center gap-4">
            <div className="text-right hidden sm:block">
              <p className="text-sm font-medium text-foreground">{userName}</p>
              <p className="text-xs text-muted-foreground">
                {urls.length} shortened URL{urls.length !== 1 ? "s" : ""}
              </p>
            </div>
            <Button
              onClick={handleLogout}
              variant="ghost"
              size="sm"
              className="text-muted-foreground hover:text-foreground"
            >
              <LogOut className="w-4 h-4 mr-2" />
              <span className="hidden sm:inline">Logout</span>
            </Button>
          </div>
        </div>
      </header>

      <main className="flex-1 grid-pattern">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 sm:py-12 space-y-8">
          {/* Create Short URL Section */}
          <div>
            <h2 className="text-2xl font-bold text-foreground mb-6">Create New Short URL</h2>
            <div className="glass-card rounded-2xl shadow-2xl shadow-primary/5 p-8 sm:p-10">
              <ShortenerForm onSuccess={handleSuccess} onError={handleError} />
            </div>
          </div>

          {/* Error Alert */}
          {error && (
            <div className="flex items-start gap-3 p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive animate-in fade-in-0 slide-in-from-bottom-4 duration-300">
              <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Error</p>
                <p className="text-sm opacity-80 mt-0.5">{error}</p>
              </div>
            </div>
          )}

          {/* Analytics Chart */}
          {urls.length > 0 && (
            <div className="glass-card rounded-2xl shadow-2xl shadow-primary/5 p-8 sm:p-10">
              <div className="flex items-center gap-2 mb-6">
                <TrendingUp className="w-5 h-5 text-primary" />
                <h2 className="text-2xl font-bold text-foreground">Click Analytics</h2>
              </div>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart
                  data={urls.map((url) => ({
                    name: url.shortId,
                    clicks: url.clickCount,
                    label: url.shortId.slice(0, 8),
                  }))}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis
                    dataKey="label"
                    stroke="var(--muted-foreground)"
                    style={{ fontSize: "12px" }}
                  />
                  <YAxis stroke="var(--muted-foreground)" style={{ fontSize: "12px" }} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "var(--card)",
                      border: "1px solid var(--border)",
                      borderRadius: "0.5rem",
                    }}
                    labelStyle={{ color: "var(--foreground)" }}
                  />
                  <Line
                    type="monotone"
                    dataKey="clicks"
                    stroke="var(--primary)"
                    strokeWidth={2}
                    dot={{ fill: "var(--primary)", r: 4 }}
                    activeDot={{ r: 6 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* URLs Table Section */}
          <div>
            <h2 className="text-2xl font-bold text-foreground mb-6">Your Shortened URLs</h2>

            {isLoading ? (
              <div className="glass-card rounded-2xl shadow-2xl shadow-primary/5 p-12 flex items-center justify-center">
                <Loader2 className="w-6 h-6 animate-spin text-primary" />
              </div>
            ) : urls.length === 0 ? (
              <div className="glass-card rounded-2xl shadow-2xl shadow-primary/5 p-12 text-center">
                <p className="text-muted-foreground">
                  No shortened URLs yet. Create one to get started!
                </p>
              </div>
            ) : (
              <div className="glass-card rounded-2xl shadow-2xl shadow-primary/5 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="border-b border-border/50 bg-secondary/50">
                      <tr>
                        <th className="px-6 py-4 text-left font-semibold text-foreground">
                          Short URL
                        </th>
                        <th className="px-6 py-4 text-left font-semibold text-foreground">
                          Original URL
                        </th>
                        <th className="px-6 py-4 text-center font-semibold text-foreground">
                          Clicks
                        </th>
                        <th className="px-6 py-4 text-left font-semibold text-foreground">
                          Created
                        </th>
                        <th className="px-6 py-4 text-center font-semibold text-foreground">Bio</th>
                        <th className="px-6 py-4 text-right font-semibold text-foreground">
                          Actions
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {urls.map((url) => (
                        <tr
                          key={url.shortId}
                          className="border-b border-border/50 hover:bg-secondary/30 transition-colors"
                        >
                          <td className="px-6 py-4">
                            <a
                              href={`${API_URL}/${url.shortId}`}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-primary hover:underline font-medium truncate block"
                            >
                              {url.shortId}
                            </a>
                          </td>
                          <td className="px-6 py-4">
                            <div className="truncate text-muted-foreground max-w-[300px]" title={url.longUrl}>
                              {url.longUrl}
                            </div>
                          </td>
                          <td className="px-6 py-4 text-center">
                            <button 
                              onClick={() => toggleBioVisibility(url.shortId, url.showOnBio)}
                              className={`w-10 h-5 rounded-full transition-colors relative ${url.showOnBio ? 'bg-primary' : 'bg-muted'}`}
                            >
                              <div className={`absolute top-1 w-3 h-3 rounded-full bg-white transition-all ${url.showOnBio ? 'left-6' : 'left-1'}`} />
                            </button>
                          </td>
                          <td className="px-6 py-4 text-center font-semibold">
                            {url.clickCount}
                          </td>
                          <td className="px-6 py-4 text-muted-foreground">
                            {formatDate(url.createdAt)}
                          </td>
                          <td className="px-6 py-4 text-right">
                            <div className="flex items-center justify-end gap-2">
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() => copyToClipboard(url.shortId)}
                                className="h-8 w-8 p-0"
                                title="Copy to clipboard"
                              >
                                <Copy
                                  className={`w-4 h-4 ${copiedId === url.shortId
                                      ? "text-success"
                                      : "text-muted-foreground"
                                    }`}
                                />
                              </Button>
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() => {
                                  setSelectedShortId(url.shortId);
                                  setShowStats(true);
                                }}
                                className="h-8 w-8 p-0 text-muted-foreground"
                                title="View stats"
                              >
                                <TrendingUp className="w-4 h-4" />
                              </Button>
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() => {
                                  setSelectedShortId(url.shortId);
                                  setShowQR(true);
                                }}
                                className="h-8 w-8 p-0 text-muted-foreground"
                                title="Generate QR code"
                              >
                                <QrCode className="w-4 h-4" />
                              </Button>
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() => deleteUrl(url.shortId)}
                                className="h-8 w-8 p-0 text-muted-foreground hover:text-destructive"
                                title="Delete URL"
                              >
                                <Trash2 className="w-4 h-4" />
                              </Button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        </div>
      </main>

      {/* Modals */}
      {selectedShortId && (
        <>
          <StatsModal
            shortId={selectedShortId}
            isOpen={showStats}
            onClose={() => setShowStats(false)}
          />
          <QRModal
            shortUrl={`${API_URL}/${selectedShortId}`}
            shortId={selectedShortId}
            isOpen={showQR}
            onClose={() => setShowQR(false)}
          />
        </>
      )}
    </div>
  );

  const toggleBioVisibility = async (shortId: string, currentState: boolean) => {
  const token = localStorage.getItem("authToken");
  try {
    const response = await fetch(`${API_URL}/api/v1/url/${shortId}/bio-visibility`, {
      method: "PUT",
      headers: { 
        "Authorization": `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ showOnBio: !currentState })
    });

    if (response.ok) {
      // Update the local state instantly
      setUrls(urls.map(u => u.shortId === shortId ? { ...u, showOnBio: !currentState } : u));
    }
  } catch (err) {
    console.error("Failed to toggle visibility", err);
  }
};

}
