"use client";

import { useEffect, useState, useRef, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Loader2, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ShortenSuccessModal } from "@/components/shorten-success-modal";
import { QRModal } from "@/components/qr-modal";
import { StatsModal } from "@/components/stats-modal";
import { EditMetadataModal } from "@/components/edit-metadata-modal";
import { BioLinksReorder } from "@/components/bio-links-reorder";
import { UserProfileSettings } from "@/components/user-profile-settings";
import { DashboardImportModal } from "@/components/dashboard/dashboard-import-modal";
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
import { DashboardHeader } from "@/components/dashboard/dashboard-header";
import { EmailVerificationBanner } from "@/components/dashboard/email-verification-banner";
import { DashboardTabBar } from "@/components/dashboard/dashboard-tab-bar";
import { DashboardShortenBlock } from "@/components/dashboard/dashboard-shorten-block";
import { DashboardAnalyticsChart } from "@/components/dashboard/dashboard-analytics-chart";
import { DashboardLinksPanel } from "@/components/dashboard/dashboard-links-panel";
import type { DashboardUrlData } from "@/components/dashboard/types";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface ShortenResponse {
  shortId: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
  title?: string;
  passwordProtected: boolean;
  password?: string;
  category?: string;
  tags?: string[];
}

function DashboardContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [userName, setUserName] = useState<string>("");
  const [urls, setUrls] = useState<DashboardUrlData[]>([]);
  const [totalUrls, setTotalUrls] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [allBioUrls, setAllBioUrls] = useState<DashboardUrlData[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [showQR, setShowQR] = useState(false);
  const [showStats, setShowStats] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedShortId, setSelectedShortId] = useState<string>("");
  const [editingUrl, setEditingUrl] = useState<DashboardUrlData | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [showBulkDeleteConfirm, setShowBulkDeleteConfirm] = useState(false);
  const [isBulkDeleting, setIsBulkDeleting] = useState(false);
  const [activeTab, setActiveTab] = useState<"links" | "settings">(
    (searchParams.get("tab") as "links" | "settings") || "links",
  );
  const [togglingIds, setTogglingIds] = useState<Set<string>>(new Set());
  const [recentResult, setRecentResult] = useState<ShortenResponse | null>(null);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const feedbackRef = useRef<HTMLDivElement>(null);
  const pageRef = useRef(0);

  const [isEmailVerified, setIsEmailVerified] = useState(true);
  const [userEmail, setUserEmail] = useState("");
  const [isResending, setIsResending] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem("authToken");
    const name = localStorage.getItem("userName");

    if (!token) {
      router.push("/login");
      return;
    }

    setUserName(name || "User");
    loadUserUrls(token, true, pageRef.current, searchQuery);
    loadProfile(token);
    loadAllBioUrls(token);

    const intervalId = setInterval(() => {
      const currentToken = localStorage.getItem("authToken");
      if (currentToken) loadUserUrls(currentToken, false, pageRef.current, searchQuery);
    }, 5000);

    return () => clearInterval(intervalId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router, searchQuery]);

  const loadProfile = async (token: string) => {
    try {
      const res = await fetch(`${API_URL}/api/v1/profile/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const data = await res.json();
        setIsEmailVerified(data.emailVerified);
        setUserEmail(data.email);
        if (data.username) {
          localStorage.setItem("userHandle", data.username);
        }
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
        body: JSON.stringify({ email: userEmail }),
      });
      if (res.ok) {
        toast.success(
          "Verification email has been resent to your inbox! Check your spam folder if you don't see it.",
        );
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

  const loadUserUrls = async (
    token: string,
    showLoading: boolean = true,
    targetPage = page,
    search = searchQuery,
  ) => {
    try {
      if (showLoading) setIsLoading(true);
      const urlParams = new URLSearchParams();
      urlParams.append("page", targetPage.toString());
      urlParams.append("size", "10");
      if (search.trim() !== "") {
        urlParams.append("search", search.trim());
      }
      const response = await fetch(
        `${API_URL}/api/v1/url/my-links?${urlParams.toString()}`,
        {
          headers: { Authorization: `Bearer ${token}` },
          cache: "no-store",
        },
      );

      if (response.status === 401 || response.status === 403) {
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
        if (showLoading) {
          setError(null);
        }
      } else {
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

  const loadAllBioUrls = async (token: string) => {
    try {
      const res = await fetch(
        `${API_URL}/api/v1/url/my-links?page=0&size=200`,
        {
          headers: { Authorization: `Bearer ${token}` },
          cache: "no-store",
        },
      );
      if (res.ok) {
        const data = await res.json();
        const bioOnly = (data.content || []).filter((u: DashboardUrlData) => u.showOnBio);
        setAllBioUrls(bioOnly);
      }
    } catch (err) {
      console.error("Failed to load bio URLs", err);
    }
  };

  useEffect(() => {
    if ((recentResult || error) && feedbackRef.current) {
      feedbackRef.current.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  }, [recentResult, error]);

  const handleSuccess = (data: ShortenResponse) => {
    if (urls.some((u) => u.shortId === data.shortId)) {
      return;
    }

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
        tags: data.tags,
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

  const handleLogout = async () => {
    try {
      const token = localStorage.getItem("authToken");
      if (token) {
        await fetch(`${API_URL}/api/v1/auth/logout`, {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` },
        });
      }
    } catch (err) {
      console.error("Logout error:", err);
    } finally {
      localStorage.removeItem("authToken");
      localStorage.removeItem("userEmail");
      localStorage.removeItem("userName");
      router.push("/login");
    }
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
        setUrls((prev) =>
          prev.map((u) =>
            u.shortId === shortId ? { ...u, showOnBio: !currentState } : u,
          ),
        );
      } else {
        const data = await response.json();
        toast.error(data.message || "Failed to toggle visibility");
      }
    } catch (err) {
      console.error("Failed to toggle visibility", err);
      toast.error("Connection error: Failed to toggle visibility");
    } finally {
      setTogglingIds((prev) => {
        const next = new Set(prev);
        next.delete(shortId);
        return next;
      });
    }
  };

  const handleUpdateUrl = (
    shortId: string,
    newTitle: string,
    newProtected: boolean,
    newPassword?: string,
    newTags?: string[]
  ) => {
    setUrls((prev) =>
      prev.map((u) =>
        u.shortId === shortId
          ? {
              ...u,
              title: newTitle,
              passwordProtected: newProtected,
              password: newPassword,
              tags: newTags !== undefined ? newTags : u.tags,
            }
          : u,
      ),
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
        headers: { Authorization: `Bearer ${token}` },
      });

      if (response.ok) {
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
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

  const handleViewProfile = () => {
    const token = localStorage.getItem("authToken");
    if (token) {
      const savedUsername = localStorage.getItem("userHandle");
      if (savedUsername) {
        window.open(`/@${savedUsername}`, "_blank");
      } else {
        setActiveTab("settings");
        toast.info("Please set your username first!");
      }
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-background gradient-bg">
      <DashboardHeader
        userName={userName}
        totalUrls={totalUrls}
        onViewProfile={handleViewProfile}
        onLogout={handleLogout}
      />

      <main className="flex-1 grid-pattern min-h-[calc(100vh-3.5rem)] sm:min-h-[calc(100vh-4rem)]">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8 sm:py-12 space-y-8 pb-16">
          {!isEmailVerified && (
            <EmailVerificationBanner
              onResend={handleResendEmail}
              isResending={isResending}
            />
          )}

          <DashboardTabBar activeTab={activeTab} onTabChange={setActiveTab} />

          {activeTab === "links" ? (
            <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500">
              <DashboardShortenBlock
                ref={feedbackRef}
                error={error}
                onSuccess={handleSuccess}
                onError={handleError}
              />

              <DashboardAnalyticsChart urls={urls} />

              <DashboardLinksPanel
                apiBaseUrl={API_URL}
                urls={urls}
                isLoading={isLoading}
                page={page}
                totalPages={totalPages}
                searchQuery={searchQuery}
                onSearchChange={(q) => {
                  setSearchQuery(q);
                  setPage(0);
                  pageRef.current = 0;
                }}
                selectedIds={selectedIds}
                copiedId={copiedId}
                togglingIds={togglingIds}
                onToggleSelectAll={toggleSelectAll}
                onToggleSelect={toggleSelect}
                onToggleBio={toggleBioVisibility}
                onCopy={copyToClipboard}
                onEdit={(url) => {
                  setEditingUrl(url);
                  setShowEdit(true);
                }}
                onInsights={(shortId) =>
                  router.push(`/dashboard/insights/${shortId}`)
                }
                onQr={(shortId) => {
                  setSelectedShortId(shortId);
                  setShowQR(true);
                }}
                onDelete={deleteUrl}
                onExportCsv={handleCsvExport}
                onImportCsv={() => setShowImportModal(true)}
                onPagePrev={() => {
                  const newPage = page - 1;
                  setPage(newPage);
                  pageRef.current = newPage;
                  const token = localStorage.getItem("authToken");
                  if (token) loadUserUrls(token, true, newPage);
                }}
                onPageNext={() => {
                  const newPage = page + 1;
                  setPage(newPage);
                  pageRef.current = newPage;
                  const token = localStorage.getItem("authToken");
                  if (token) loadUserUrls(token, true, newPage);
                }}
                formatDate={formatDate}
              />
            </div>
          ) : (
            <div className="animate-in fade-in slide-in-from-bottom-4 duration-500 space-y-8">
              <UserProfileSettings />
              <BioLinksReorder urls={allBioUrls} setUrls={setAllBioUrls} API_URL={API_URL} />
            </div>
          )}
        </div>
      </main>

      {recentResult && (
        <ShortenSuccessModal
          isOpen={showSuccessModal}
          onClose={() => setShowSuccessModal(false)}
          shortId={recentResult.shortId}
          shortUrl={`${API_URL}/${recentResult.shortId}`}
          onViewStats={() => {
            router.push(`/dashboard/insights/${recentResult.shortId}`);
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
          initialTags={editingUrl.tags}
          onUpdate={handleUpdateUrl}
        />
      )}

      {selectedIds.size > 0 && (
        <div className="fixed bottom-8 left-1/2 -translate-x-1/2 z-50 animate-in slide-in-from-bottom-8 duration-500 max-w-[calc(100vw-2rem)]">
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-4 sm:gap-6 px-4 sm:px-6 py-4 rounded-2xl bg-foreground/90 text-background shadow-2xl backdrop-blur-md border border-white/10 ring-1 ring-black/20">
            <div className="flex items-center gap-3 sm:border-r sm:border-white/20 sm:pr-6">
              <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center font-bold text-sm shrink-0">
                {selectedIds.size}
              </div>
              <span className="text-sm font-medium">links selected</span>
            </div>

            <div className="flex items-center gap-2 justify-end">
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

      <AlertDialog open={showBulkDeleteConfirm} onOpenChange={setShowBulkDeleteConfirm}>
        <AlertDialogContent className="glass-card border-border/50 rounded-2xl">
          <AlertDialogHeader>
            <AlertDialogTitle className="text-xl">Are you absolutely sure?</AlertDialogTitle>
            <AlertDialogDescription className="text-muted-foreground">
              This will permanently delete{" "}
              <span className="font-bold text-foreground">{selectedIds.size}</span> selected
              links. This action cannot be undone.
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

      <DashboardImportModal
        isOpen={showImportModal}
        onClose={() => setShowImportModal(false)}
        onSuccess={() => {
          const token = localStorage.getItem("authToken");
          if (token) loadUserUrls(token, true, 0); // Reload data fully
        }}
      />
    </div>
  );
}

export default function DashboardPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-background flex items-center justify-center">
          <Loader2 className="w-8 h-8 animate-spin text-primary" />
        </div>
      }
    >
      <DashboardContent />
    </Suspense>
  );
}
