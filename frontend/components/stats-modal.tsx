"use client";

import { useEffect, useState, useCallback } from "react";
import { X, MousePointerClick, Hash, Link2, CalendarDays, Clock, Loader2, AlertCircle, TrendingUp } from "lucide-react";
import { Button } from "@/components/ui/button";
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
interface Stats {
  shortId: string;
  longUrl: string;
  clickCount: number;
  createdAt: string;
  lastAccessedTime: string | null;
}

interface StatsModalProps {
  shortId: string;
  isOpen: boolean;
  onClose: () => void;
}

function formatDate(dateString: string | null): string {
  if (!dateString) return "Never";
  const utc = dateString.endsWith("Z") ? dateString : dateString + "Z";
  const date = new Date(utc);
  return date.toLocaleDateString("en-US", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

function formatDateTime(dateString: string | null): string {
  if (!dateString) return "Never";
  const utc = dateString.endsWith("Z") ? dateString : dateString + "Z";
  const date = new Date(utc);
  return date.toLocaleString("en-US", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function StatsModal({ shortId, isOpen, onClose }: StatsModalProps) {
  const [stats, setStats] = useState<Stats | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchStats = useCallback(async (showLoading: boolean = true) => {
    if (showLoading) setIsLoading(true);
    setError(null);
    try {
      const response = await fetch(`${API_URL}/api/v1/url/stats/${shortId}`);
      const data = await response.json();
      
      if (!response.ok) {
        throw new Error(data.message || "Failed to fetch stats");
      }
      
      setStats(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load stats");
    } finally {
      if (showLoading) setIsLoading(false);
    }
  }, [shortId]);

  useEffect(() => {
    let intervalId: NodeJS.Timeout;
    
    if (isOpen && shortId) {
      fetchStats();
      
      intervalId = setInterval(() => {
        fetchStats(false);
      }, 3000);
    }
    
    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, [isOpen, shortId, fetchStats]);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    if (isOpen) {
      document.addEventListener("keydown", handleEscape);
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.body.style.overflow = "";
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-foreground/10 backdrop-blur-md animate-in fade-in-0 duration-200"
      onClick={onClose}
    >
      <div 
        className="w-full max-w-lg glass-card rounded-2xl shadow-2xl animate-in zoom-in-95 slide-in-from-bottom-4 duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-border/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 flex items-center justify-center">
              <TrendingUp className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-foreground">Link Analytics</h2>
              <p className="text-sm text-muted-foreground">Performance metrics</p>
            </div>
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="rounded-full hover:bg-muted/50"
          >
            <X className="w-5 h-5" />
          </Button>
        </div>

        {/* Content */}
        <div className="p-6">
          {isLoading && (
            <div className="flex flex-col items-center justify-center py-16">
              <Loader2 className="w-8 h-8 text-primary animate-spin mb-3" />
              <p className="text-sm text-muted-foreground">Loading analytics...</p>
            </div>
          )}

          {error && (
            <div className="flex items-center gap-3 p-4 rounded-xl bg-destructive/10 border border-destructive/20 text-destructive">
              <AlertCircle className="w-5 h-5 flex-shrink-0" />
              <p className="text-sm">{error}</p>
            </div>
          )}

          {stats && !isLoading && (
            <div className="space-y-5">
              {/* Click count highlight */}
              <div className="p-6 rounded-2xl bg-gradient-to-br from-primary/10 via-primary/5 to-transparent border border-primary/10">
                <div className="flex items-center gap-3 mb-2">
                  <MousePointerClick className="w-5 h-5 text-primary" />
                  <span className="text-sm font-medium text-muted-foreground">Total Clicks</span>
                </div>
                <p className="text-5xl font-bold text-foreground tracking-tight">
                  {stats.clickCount.toLocaleString()}
                </p>
              </div>

              {/* Details grid */}
              <div className="grid gap-3">
                <div className="flex items-start gap-4 p-4 rounded-xl bg-muted/30 border border-border/30">
                  <div className="w-10 h-10 rounded-lg bg-background flex items-center justify-center flex-shrink-0">
                    <Hash className="w-5 h-5 text-muted-foreground" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Short ID</p>
                    <p className="font-mono text-sm font-semibold text-foreground">{stats.shortId}</p>
                  </div>
                </div>

                <div className="flex items-start gap-4 p-4 rounded-xl bg-muted/30 border border-border/30">
                  <div className="w-10 h-10 rounded-lg bg-background flex items-center justify-center flex-shrink-0">
                    <Link2 className="w-5 h-5 text-muted-foreground" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Original URL</p>
                    <p className="text-sm font-medium text-foreground break-all line-clamp-2">
                      {stats.longUrl}
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div className="flex items-start gap-3 p-4 rounded-xl bg-muted/30 border border-border/30">
                    <CalendarDays className="w-5 h-5 text-muted-foreground flex-shrink-0" />
                    <div>
                      <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Created</p>
                      <p className="text-sm font-semibold text-foreground">{formatDate(stats.createdAt)}</p>
                    </div>
                  </div>

                  <div className="flex items-start gap-3 p-4 rounded-xl bg-muted/30 border border-border/30">
                    <Clock className="w-5 h-5 text-muted-foreground flex-shrink-0" />
                    <div>
                      <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Last Click</p>
                      <p className="text-sm font-semibold text-foreground">{formatDateTime(stats.lastAccessedTime)}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
