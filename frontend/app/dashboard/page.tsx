"use client";

import { useEffect, useState, useRef, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { LogOut, Copy, QrCode, Loader2, AlertCircle, TrendingUp, Link2, Trash2, Smartphone, Lock, Pencil } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ShortenerForm } from "@/components/shortener-form";
import { ShortenSuccessModal } from "@/components/shorten-success-modal";
import { ResultBox } from "@/components/result-box";
import { QRModal } from "@/components/qr-modal";
import { StatsModal } from "@/components/stats-modal";
import { EditMetadataModal } from "@/components/edit-metadata-modal";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import { cn } from "@/lib/utils";
import { UserProfileSettings } from "@/components/user-profile-settings";
import { Checkbox } from "@/components/ui/checkbox";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface UrlData {
  showOnBio: boolean;
  shortId: string;
  longUrl: string;
  clickCount: number;
  lastAccessedTime: string | null;
  createdAt: string;
  title?: string;
  passwordProtected: boolean;
  password?: string;
}

interface ShortenResponse {
  shortId: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
  title?: string;
  passwordProtected: boolean;
  password?: string;
}

function DashboardContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [userName, setUserName] = useState<string>("");
  const [urls, setUrls] = useState<UrlData[]>([]);
  const [totalUrls, setTotalUrls] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [showQR, setShowQR] = useState(false);
  const [showStats, setShowStats] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [selectedShortId, setSelectedShortId] = useState<string>("");
  const [editingUrl, setEditingUrl] = useState<UrlData | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [showBulkDeleteConfirm, setShowBulkDeleteConfirm] = useState(false);
  const [isBulkDeleting, setIsBulkDeleting] = useState(false);
  const [activeTab, setActiveTab] = useState<"links" | "settings">((searchParams.get("tab") as "links" | "settings") || "links");
  const [togglingIds, setTogglingIds] = useState<Set<string>>(new Set());
  const [recentResult, setRecentResult] = useState<ShortenResponse | null>(null);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const feedbackRef = useRef<HTMLDivElement>(null);

  // Verification state
  const [isEmailVerified, setIsEmailVerified] = useState(true);
  const [userEmail, setUserEmail] = useState("");
  const [isResending, setIsResending] = useState(false);

  // Check authentication and load data
  useEffect(() => {
    const token = localStorage.getItem("authToken");
    const name = localStorage.getItem("userName");

    if (!token) {
      router.push("/login");
      return;
    }

    setUserName(name || "User");
    loadUserUrls(token, true, page);
    loadProfile(token);

    const intervalId = setInterval(() => {
      loadUserUrls(token, false, page);
    }, 5000);

    return () => clearInterval(intervalId);
  }, [router, page]);

  const loadProfile = async (token: string) => {
    try {
      const res = await fetch(`${API_URL}/api/v1/profile/me`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setIsEmailVerified(data.emailVerified);
        setUserEmail(data.email);
      }
    } catch (err) {
      console.error("Failed to load profile", err);
    }
  };

  const handleResendEmail = async () => {
    if (isResending) return;
    setIsResending(true);
    try {
      const res = await fetch(`${API_URL}/api/v1/auth/resend-verification`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: userEmail })
      });
      if (res.ok) {
        alert("Verification email has been resent to your inbox! Check your spam folder if you don't see it.");
      } else {
        const data = await res.json();
        setError(data.message || "Failed to resend verification email");
      }
    } catch (err) {
      setError("Failed to resend verification email due to network error");
    } finally {
      setIsResending(false);
    }
  };

  const loadUserUrls = async (token: string, showLoading: boolean = true, targetPage = page) => {
    try {
      if (showLoading) setIsLoading(true);
      const response = await fetch(`${API_URL}/api/v1/url/my-links?page=${targetPage}&size=10`, {
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
        setUrls(data.content);
        setTotalPages(data.totalPages);
        setTotalUrls(data.totalElements);
        setPage(data.number);
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

  useEffect(() => {
    if ((recentResult || error) && feedbackRef.current) {
      feedbackRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }, [recentResult, error]);

  const handleSuccess = (data: ShortenResponse) => {
    // Check if the URL already exists in the list to avoid duplicate key errors
    if (urls.some(u => u.shortId === data.shortId)) {
        return;
    }

    // Add newly created URL to the top of the list
    setUrls([
      {
        shortId: data.shortId,
        longUrl: data.longUrl,
        clickCount: 0,
        lastAccessedTime: null,
        showOnBio: false,
        createdAt: data.createdAt,
        title: data.title,
        passwordProtected: data.passwordProtected,
        password: data.password,
      },
      ...urls,
    ]);
    setError(null);
    setRecentResult(data);
    setShowSuccessModal(true);
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

  const toggleBioVisibility = async (shortId: string, currentState: boolean) => {
    const token = localStorage.getItem("authToken");
    setTogglingIds((prev) => new Set(prev).add(shortId));

    try {
      const response = await fetch(`${API_URL}/api/v1/url/${shortId}/bio-visibility`, {
        method: "PUT",
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ showOnBio: !currentState }),
      });

      if (response.ok) {
        setUrls((prev) => prev.map((u) => (u.shortId === shortId ? { ...u, showOnBio: !currentState } : u)));
      } else {
        const data = await response.json();
        alert(data.message || "Failed to toggle visibility");
      }
    } catch (err) {
      console.error("Failed to toggle visibility", err);
      alert("Connection error: Failed to toggle visibility");
    } finally {
      setTogglingIds((prev) => {
        const next = new Set(prev);
        next.delete(shortId);
        return next;
      });
    }
  };

  const handleUpdateUrl = (shortId: string, newTitle: string, newProtected: boolean, newPassword?: string) => {
    setUrls((prev) => 
      prev.map((u) => (u.shortId === shortId ? { ...u, title: newTitle, passwordProtected: newProtected, password: newPassword } : u))
    );
  };

  const toggleSelect = (shortId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(shortId)) next.delete(shortId);
      else next.add(shortId);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (selectedIds.size === urls.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(urls.map((u) => u.shortId)));
    }
  };

  const handleBulkDelete = async () => {
    if (selectedIds.size === 0) return;
    setIsBulkDeleting(true);
    const token = localStorage.getItem("authToken");

    try {
      const response = await fetch(`${API_URL}/api/v1/url/bulk-delete`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(Array.from(selectedIds)),
      });

      if (response.ok) {
        setUrls((prev) => prev.filter((u) => !selectedIds.has(u.shortId)));
        setSelectedIds(new Set());
        toast.success(`Successfully deleted ${selectedIds.size} links`);
      } else {
        toast.error("Failed to delete selected links");
      }
    } catch (err) {
      toast.error("Network error. Please try again.");
    } finally {
      setIsBulkDeleting(false);
      setShowBulkDeleteConfirm(false);
    }
  };

  const handleCsvExport = async () => {
    const token = localStorage.getItem("authToken");
    if (!token) return;
    
    try {
      const response = await fetch(`${API_URL}/api/v1/url/export/csv`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      
      if (response.ok) {
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `shunyalink_export_${new Date().getTime()}.csv`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
      } else {
        const data = await response.json();
        setError(data?.message || "Failed to export CSV");
      }
    } catch (err) {
      setError("Failed to export CSV due to network error");
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-background gradient-bg">
      {/* Custom Dashboard Header */}
      <header className="glass-card sticky top-0 z-50 border-b border-border/50">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <Link href="/" className="flex items-center gap-3 hover:opacity-80 transition-opacity">
            <div className="relative flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-primary/80 shadow-lg shadow-primary/25">
              <Link2 className="w-4 h-4 text-primary-foreground" />
              <div className="absolute inset-0 rounded-xl bg-primary/20 blur-md -z-10" />
            </div>
            <span className="text-xl font-semibold tracking-tight text-foreground">
              Shunya<span className="text-gradient">Link</span>
            </span>
          </Link>

          <div className="flex items-center gap-4">
            <div className="text-right hidden sm:block">
              <p className="text-sm font-medium text-foreground">{userName}</p>
              <p className="text-xs text-muted-foreground">
                {totalUrls} shortened URL{totalUrls !== 1 ? "s" : ""}
              </p>
            </div>
            
            <Button
              variant="outline"
              size="sm"
              className="hidden lg:flex"
              onClick={() => {
                const token = localStorage.getItem("authToken");
                if (token) {
                   // Redirect to profile if username is known
                   // We'll fetch it from localStorage if we saved it there
                   const savedUsername = localStorage.getItem("userHandle");
                   if (savedUsername) {
                     window.open(`/@${savedUsername}`, '_blank');
                   } else {
                     setActiveTab("settings");
                     alert("Please set your username first!");
                   }
                }
              }}
            >
              <Smartphone className="w-4 h-4 mr-2" />
              View Profile
            </Button>

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
          {/* Verification Warning Banner */}
          {!isEmailVerified && (
            <div className="relative group overflow-hidden rounded-2xl border border-amber-500/20 bg-amber-500/5 p-5 sm:p-6 backdrop-blur-xl animate-in fade-in slide-in-from-top-4 duration-500">
              <div className="absolute inset-0 bg-gradient-to-r from-amber-500/10 via-transparent to-amber-500/10 opacity-0 group-hover:opacity-100 transition-opacity duration-700" />
              <div className="relative flex flex-col sm:flex-row items-center justify-between gap-6">
                <div className="flex items-center gap-4 text-center sm:text-left">
                  <div className="relative flex-shrink-0">
                    <div className="absolute inset-0 bg-amber-500 blur-lg opacity-20 animate-pulse" />
                    <div className="relative w-12 h-12 rounded-full bg-amber-500/20 flex items-center justify-center border border-amber-500/30">
                      <AlertCircle className="w-6 h-6 text-amber-500" />
                    </div>
                  </div>
                  <div>
                    <h3 className="text-lg font-bold text-foreground">Verify Your Email Address</h3>
                    <p className="text-sm text-muted-foreground mt-1 leading-relaxed">
                      Please verify your email to unlock all features like <span className="text-foreground font-medium">password-protected links</span> and <span className="text-foreground font-medium">expiry dates</span>.
                    </p>
                  </div>
                </div>
                <Button 
                  onClick={handleResendEmail}
                  disabled={isResending}
                  className="w-full sm:w-auto bg-amber-500 hover:bg-amber-600 text-white font-bold px-8 shadow-lg shadow-amber-500/25 transition-all duration-300 hover:scale-105 active:scale-95 disabled:opacity-70"
                >
                  {isResending ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      Resending...
                    </>
                  ) : (
                    "Resend Link"
                  )}
                </Button>
              </div>
            </div>
          )}

          {/* Tab Switcher */}
          <div className="flex items-center gap-2 p-1 bg-secondary/50 rounded-xl w-fit">
            <Button
              variant={activeTab === "links" ? "secondary" : "ghost"}
              onClick={() => setActiveTab("links")}
              className={cn("rounded-lg px-6", activeTab === "links" && "bg-background shadow-sm")}
            >
              Your Links
            </Button>
            <Button
              variant={activeTab === "settings" ? "secondary" : "ghost"}
              onClick={() => setActiveTab("settings")}
              className={cn("rounded-lg px-6", activeTab === "settings" && "bg-background shadow-sm")}
            >
              Profile Settings
            </Button>
          </div>

          {activeTab === "links" ? (
            <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500">
              {/* Create Short URL Section */}
              <div ref={feedbackRef}>
                <h2 className="text-2xl font-bold text-foreground mb-6">Create New Short URL</h2>
                
                {/* Error Alert (Moved Above Form) */}
                {error && (
                  <div className="flex items-start gap-3 p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive animate-in fade-in-0 slide-in-from-bottom-4 duration-300 mb-8">
                    <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold">Error</p>
                      <p className="text-sm opacity-80 mt-0.5">{error}</p>
                    </div>
                  </div>
                )}

                <div className="glass-card rounded-2xl shadow-2xl shadow-primary/5 p-8 sm:p-10">
                  <ShortenerForm onSuccess={handleSuccess} onError={handleError} />
                </div>
              </div>

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
              <div className="space-y-6">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <h2 className="text-2xl font-bold text-foreground">Your Shortened URLs</h2>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleCsvExport}
                    className="w-full sm:w-auto border-primary/20 hover:bg-primary/5 hover:text-primary transition-all duration-300"
                    disabled={isLoading || urls.length === 0}
                  >
                    <Copy className="w-4 h-4 mr-2" />
                    Export CSV
                  </Button>
                </div>

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
                            <th className="px-6 py-4 text-left">
                              <Checkbox 
                                checked={urls.length > 0 && selectedIds.size === urls.length}
                                onCheckedChange={toggleSelectAll}
                                aria-label="Select all"
                              />
                            </th>
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
                              className={cn(
                                "border-b border-border/50 hover:bg-secondary/30 transition-colors",
                                selectedIds.has(url.shortId) && "bg-primary/5 hover:bg-primary/10"
                              )}
                            >
                              <td className="px-6 py-4">
                                <Checkbox 
                                  checked={selectedIds.has(url.shortId)}
                                  onCheckedChange={() => toggleSelect(url.shortId)}
                                  aria-label={`Select ${url.shortId}`}
                                  className="data-[state=checked]:bg-primary"
                                />
                              </td>
                              <td className="px-6 py-4">
                                <a
                                  href={`${API_URL}/${url.shortId}`}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="text-primary hover:underline font-medium truncate flex items-center gap-2"
                                >
                                  {url.title || url.shortId}
                                  {url.passwordProtected && (
                                    <Lock className="w-3 h-3 text-muted-foreground/60" />
                                  )}
                                </a>
                                {url.title && (
                                  <span className="text-[10px] text-muted-foreground/60 font-mono">
                                    /{url.shortId}
                                  </span>
                                )}
                              </td>
                              <td className="px-6 py-4">
                                <div className="truncate text-muted-foreground max-w-[300px]" title={url.longUrl}>
                                  {url.longUrl}
                                </div>
                              </td>
                              <td className="px-6 py-4 text-center font-semibold">
                                {url.clickCount}
                              </td>
                              <td className="px-6 py-4 text-muted-foreground">
                                {formatDate(url.createdAt)}
                              </td>
                              <td className="px-6 py-4 text-center">
                            <button 
                              onClick={() => toggleBioVisibility(url.shortId, url.showOnBio)}
                              disabled={togglingIds.has(url.shortId)}
                              className={`w-10 h-5 rounded-full transition-colors relative flex items-center ${url.showOnBio ? 'bg-primary' : 'bg-muted'} ${togglingIds.has(url.shortId) ? 'opacity-50 cursor-not-allowed' : ''}`}
                              title={url.showOnBio ? "Hide from Bio" : "Show on Bio"}
                            >
                              {togglingIds.has(url.shortId) ? (
                                <Loader2 className="w-3 h-3 animate-spin mx-auto text-white" />
                              ) : (
                                <div className={`absolute top-1 w-3 h-3 rounded-full bg-white transition-all ${url.showOnBio ? 'left-6' : 'left-1'}`} />
                              )}
                            </button>
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
                                      setEditingUrl(url);
                                      setShowEdit(true);
                                    }}
                                    className="h-8 w-8 p-0 text-muted-foreground hover:text-primary transition-colors"
                                    title="Edit Link Metadata"
                                  >
                                    <Pencil className="w-4 h-4" />
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
                    <div className="flex justify-between items-center px-6 py-4 border-t border-border/50 bg-secondary/20">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={page === 0 || isLoading}
                        onClick={() => {
                          const token = localStorage.getItem("authToken");
                          if (token) loadUserUrls(token, true, page - 1);
                        }}
                      >
                        Previous
                      </Button>
                      
                      <div className="text-sm font-medium text-muted-foreground">
                        Page <span className="text-foreground">{page + 1}</span> of {totalPages}
                      </div>

                      <Button
                        variant="outline"
                        size="sm"
                        disabled={page >= totalPages - 1 || isLoading}
                        onClick={() => {
                          const token = localStorage.getItem("authToken");
                          if (token) loadUserUrls(token, true, page + 1);
                        }}
                      >
                        Next
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500">
               <UserProfileSettings />
            </div>
          )}
        </div>
      </main>

      {/* Modals */}
      {recentResult && (
        <ShortenSuccessModal
          isOpen={showSuccessModal}
          onClose={() => setShowSuccessModal(false)}
          shortId={recentResult.shortId}
          shortUrl={`${API_URL}/${recentResult.shortId}`}
          onViewStats={() => {
            setSelectedShortId(recentResult.shortId);
            setShowStats(true);
          }}
          onGenerateQR={() => {
            setSelectedShortId(recentResult.shortId);
            setShowQR(true);
          }}
        />
      )}

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

      {editingUrl && (
        <EditMetadataModal
          isOpen={showEdit}
          onClose={() => {
            setShowEdit(false);
            setEditingUrl(null);
          }}
          shortId={editingUrl.shortId}
          initialTitle={editingUrl.title}
          initialPasswordProtected={editingUrl.passwordProtected}
          initialPassword={editingUrl.password}
          onUpdate={handleUpdateUrl}
        />
      )}

      {/* Floating Bulk Actions Bar */}
      {selectedIds.size > 0 && (
        <div className="fixed bottom-8 left-1/2 -translate-x-1/2 z-50 animate-in slide-in-from-bottom-8 duration-500">
          <div className="flex items-center gap-6 px-6 py-4 rounded-2xl bg-foreground/90 text-background shadow-2xl backdrop-blur-md border border-white/10 ring-1 ring-black/20">
            <div className="flex items-center gap-3 border-r border-white/20 pr-6">
              <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center font-bold text-sm">
                {selectedIds.size}
              </div>
              <span className="text-sm font-medium">links selected</span>
            </div>
            
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSelectedIds(new Set())}
                className="text-white/70 hover:text-white hover:bg-white/10 h-10 px-4 rounded-xl transition-all"
              >
                Clear
              </Button>
              <Button
                variant="destructive"
                size="sm"
                onClick={() => setShowBulkDeleteConfirm(true)}
                className="h-10 px-4 rounded-xl bg-red-500 hover:bg-red-600 shadow-lg shadow-red-500/20 transition-all font-semibold"
                disabled={isBulkDeleting}
              >
                {isBulkDeleting ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <>
                    <Trash2 className="w-4 h-4 mr-2" />
                    Delete Selected
                  </>
                )}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Bulk Delete Confirmation */}
      <AlertDialog open={showBulkDeleteConfirm} onOpenChange={setShowBulkDeleteConfirm}>
        <AlertDialogContent className="glass-card border-border/50 rounded-2xl">
          <AlertDialogHeader>
            <AlertDialogTitle className="text-xl">Are you absolutely sure?</AlertDialogTitle>
            <AlertDialogDescription className="text-muted-foreground">
              This will permanently delete <span className="font-bold text-foreground">{selectedIds.size}</span> selected links. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel className="rounded-xl border-border/50 h-12">Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleBulkDelete}
              className="rounded-xl bg-destructive text-destructive-foreground hover:bg-destructive/90 h-12 font-semibold btn-glow"
            >
              Confirm Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

export default function DashboardPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-background flex items-center justify-center"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>}>
      <DashboardContent />
    </Suspense>
  );
}

