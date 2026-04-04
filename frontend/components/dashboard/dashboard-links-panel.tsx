"use client";

import {
  Copy,
  Loader2,
  Lock,
  Pencil,
  QrCode,
  Trash2,
  TrendingUp,
  Youtube,
  Github,
  FileText,
  Share2,
  User,
  BookOpen,
  Link2,
  Tag,
  UploadCloud,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { GlassPanel } from "@/components/shell/glass-panel";
import { LoadingSpinner } from "@/components/shell/loading-block";
import { cn } from "@/lib/utils";
import type { DashboardUrlData } from "@/components/dashboard/types";

function BioToggle({
  url,
  togglingIds,
  onToggleBio,
}: {
  url: DashboardUrlData;
  togglingIds: Set<string>;
  onToggleBio: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggleBio}
      disabled={togglingIds.has(url.shortId)}
      className={cn(
        "w-10 h-5 rounded-full transition-colors relative flex items-center shrink-0",
        url.showOnBio ? "bg-primary" : "bg-muted",
        togglingIds.has(url.shortId) && "opacity-50 cursor-not-allowed",
      )}
      title={url.showOnBio ? "Hide from Bio" : "Show on Bio"}
    >
      {togglingIds.has(url.shortId) ? (
        <Loader2 className="w-3 h-3 animate-spin mx-auto text-white" />
      ) : (
        <div
          className={cn(
            "absolute top-1 w-3 h-3 rounded-full bg-white transition-all",
            url.showOnBio ? "left-6" : "left-1",
          )}
        />
      )}
    </button>
  );
}

function getCategoryIcon(category?: string) {
  switch (category?.toUpperCase()) {
    case "VIDEO":
      return <Youtube className="w-3.5 h-3.5 text-red-500" />;
    case "GITHUB":
      return <Github className="w-3.5 h-3.5" />;
    case "DOCUMENTATION":
      return <FileText className="w-3.5 h-3.5 text-blue-500" />;
    case "SOCIAL_MEDIA":
      return <Share2 className="w-3.5 h-3.5 text-purple-500" />;
    case "PORTFOLIO":
      return <User className="w-3.5 h-3.5 text-emerald-500" />;
    case "BLOG":
      return <BookOpen className="w-3.5 h-3.5 text-orange-500" />;
    default:
      return <Link2 className="w-3.5 h-3.5 text-muted-foreground/50" />;
  }
}

function LinkActionButtons({
  url,
  copiedId,
  onCopy,
  onEdit,
  onInsights,
  onQr,
  onDelete,
}: {
  url: DashboardUrlData;
  copiedId: string | null;
  onCopy: () => void;
  onEdit: () => void;
  onInsights: () => void;
  onQr: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="flex items-center justify-end gap-1 flex-wrap">
      <Button
        size="sm"
        variant="ghost"
        onClick={onCopy}
        className="h-8 w-8 p-0"
        title="Copy to clipboard"
      >
        <Copy
          className={cn(
            "w-4 h-4",
            copiedId === url.shortId ? "text-success" : "text-muted-foreground",
          )}
        />
      </Button>
      <Button
        size="sm"
        variant="ghost"
        onClick={onEdit}
        className="h-8 w-8 p-0 text-muted-foreground hover:text-primary transition-colors"
        title="Edit Link Metadata"
      >
        <Pencil className="w-4 h-4" />
      </Button>
      <Button
        size="sm"
        variant="ghost"
        onClick={onInsights}
        className="h-8 w-8 p-0 text-muted-foreground hover:text-primary hover:bg-primary/10"
        title="View detailed insights"
      >
        <TrendingUp className="w-4 h-4" />
      </Button>
      <Button
        size="sm"
        variant="ghost"
        onClick={onQr}
        className="h-8 w-8 p-0 text-muted-foreground"
        title="Generate QR code"
      >
        <QrCode className="w-4 h-4" />
      </Button>
      <Button
        size="sm"
        variant="ghost"
        onClick={onDelete}
        className="h-8 w-8 p-0 text-muted-foreground hover:text-destructive"
        title="Delete URL"
      >
        <Trash2 className="w-4 h-4" />
      </Button>
    </div>
  );
}

export function DashboardLinksPanel({
  apiBaseUrl,
  urls,
  isLoading,
  page,
  totalPages,
  searchQuery,
  onSearchChange,
  selectedIds,
  copiedId,
  togglingIds,
  onToggleSelectAll,
  onToggleSelect,
  onToggleBio,
  onCopy,
  onEdit,
  onInsights,
  onQr,
  onDelete,
  onExportCsv,
  onImportCsv,
  onPagePrev,
  onPageNext,
  formatDate,
}: {
  apiBaseUrl: string;
  urls: DashboardUrlData[];
  isLoading: boolean;
  page: number;
  totalPages: number;
  searchQuery: string;
  onSearchChange: (q: string) => void;
  selectedIds: Set<string>;
  copiedId: string | null;
  togglingIds: Set<string>;
  onToggleSelectAll: () => void;
  onToggleSelect: (shortId: string) => void;
  onToggleBio: (shortId: string, current: boolean) => void;
  onCopy: (shortId: string) => void;
  onEdit: (url: DashboardUrlData) => void;
  onInsights: (shortId: string) => void;
  onQr: (shortId: string) => void;
  onDelete: (shortId: string) => void;
  onExportCsv: () => void;
  onImportCsv: () => void;
  onPagePrev: () => void;
  onPageNext: () => void;
  formatDate: (iso: string) => string;
}) {
  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h2 className="text-2xl font-bold text-foreground">Your Shortened URLs</h2>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={onImportCsv}
            className="w-full sm:w-auto border-primary/20 hover:bg-primary/5 hover:text-primary transition-all duration-300"
          >
            <UploadCloud className="w-4 h-4 mr-2" />
            Import CSV
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={onExportCsv}
            className="w-full sm:w-auto border-primary/20 hover:bg-primary/5 hover:text-primary transition-all duration-300"
            disabled={isLoading || urls.length === 0}
          >
            <Copy className="w-4 h-4 mr-2" />
            Export CSV
          </Button>
        </div>
      </div>

      <div className="relative">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
          />
        </svg>
        <Input
          placeholder="Search by title, original URL, shortlink, or tags..."
          value={searchQuery}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => onSearchChange(e.target.value)}
          className="pl-9 w-full bg-background/50 border-primary/20 focus:border-primary/50 transition-colors"
        />
      </div>

      {isLoading ? (
        <GlassPanel padding="none" className="overflow-hidden">
          <LoadingSpinner className="p-12" />
        </GlassPanel>
      ) : urls.length === 0 ? (
        <GlassPanel>
          <p className="text-center text-muted-foreground py-4">
            No shortened URLs yet. Create one to get started!
          </p>
        </GlassPanel>
      ) : (
        <GlassPanel padding="none" className="overflow-hidden">
          {/* Mobile cards */}
          <div className="md:hidden divide-y divide-border/50">
            {urls.map((url) => (
              <div
                key={url.shortId}
                className={cn(
                  "p-4 space-y-3",
                  selectedIds.has(url.shortId) && "bg-primary/5",
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <Checkbox
                    checked={selectedIds.has(url.shortId)}
                    onCheckedChange={() => onToggleSelect(url.shortId)}
                    aria-label={`Select ${url.shortId}`}
                    className="data-[state=checked]:bg-primary mt-1"
                  />
                  <div className="min-w-0 flex-1 space-y-1">
                    <a
                      href={`${apiBaseUrl}/${url.shortId}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-primary font-medium flex items-center gap-2 min-w-0"
                      title={url.title || url.shortId}
                    >
                      {getCategoryIcon(url.category)}
                      <span className="truncate flex-1">{url.title || url.shortId}</span>
                      {url.passwordProtected && (
                        <Lock className="w-3 h-3 text-muted-foreground/60 shrink-0" />
                      )}
                    </a>
                    {url.title != null && url.title !== "" && (
                      <span className="text-[10px] text-muted-foreground/60 font-mono block">
                        /{url.shortId}
                      </span>
                    )}
                    <p
                      className="text-sm text-muted-foreground break-all line-clamp-2"
                      title={url.longUrl}
                    >
                      {url.longUrl}
                    </p>
                    {url.tags && url.tags.length > 0 && (
                      <div className="flex flex-wrap gap-1 mt-1 font-mono">
                        {url.tags.map(tag => (
                          <span key={tag} className="inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-medium bg-primary/10 text-primary border border-primary/20 rounded-md">
                            <Tag className="w-2.5 h-2.5" />
                            {tag}
                          </span>
                        ))}
                      </div>
                    )}
                    <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
                      <span>
                        <span className="font-medium text-foreground">
                          {url.clickCount}
                        </span>{" "}
                        clicks
                      </span>
                      <span>{formatDate(url.createdAt)}</span>
                    </div>
                  </div>
                </div>
                <div className="pl-7 space-y-3 border-t border-border/30 pt-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs font-medium text-muted-foreground">
                      Show on bio
                    </span>
                    <BioToggle
                      url={url}
                      togglingIds={togglingIds}
                      onToggleBio={() => onToggleBio(url.shortId, url.showOnBio)}
                    />
                  </div>
                  <LinkActionButtons
                    url={url}
                    copiedId={copiedId}
                    onCopy={() => onCopy(url.shortId)}
                    onEdit={() => onEdit(url)}
                    onInsights={() => onInsights(url.shortId)}
                    onQr={() => onQr(url.shortId)}
                    onDelete={() => onDelete(url.shortId)}
                  />
                </div>
              </div>
            ))}
          </div>

          <div className="hidden md:block overflow-x-auto">
            <table className="w-full text-sm table-fixed">
              <thead className="border-b border-border/50 bg-secondary/50">
                <tr>
                  <th className="px-2 py-4 text-left w-10">
                    <Checkbox
                      checked={urls.length > 0 && selectedIds.size === urls.length}
                      onCheckedChange={onToggleSelectAll}
                      aria-label="Select all"
                    />
                  </th>
                  <th className="px-3 py-4 text-left font-semibold text-foreground w-[200px] lg:w-[280px]">
                    Short URL
                  </th>
                  <th className="px-3 py-4 text-left font-semibold text-foreground">
                    Original URL
                  </th>
                  <th className="px-2 py-4 text-center font-semibold text-foreground w-16">
                    Clicks
                  </th>
                  <th className="px-2 py-4 text-left font-semibold text-foreground w-28">
                    Created
                  </th>
                  <th className="px-2 py-4 text-center font-semibold text-foreground w-16">
                    Bio
                  </th>
                  <th className="px-2 py-4 text-right font-semibold text-foreground w-[180px]">
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
                      selectedIds.has(url.shortId) && "bg-primary/5 hover:bg-primary/10",
                    )}
                  >
                    <td className="px-2 py-4">
                      <Checkbox
                        checked={selectedIds.has(url.shortId)}
                        onCheckedChange={() => onToggleSelect(url.shortId)}
                        aria-label={`Select ${url.shortId}`}
                        className="data-[state=checked]:bg-primary"
                      />
                    </td>
                    <td className="px-3 py-4 max-w-[200px] lg:max-w-[280px]">
                      <a
                        href={`${apiBaseUrl}/${url.shortId}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-primary hover:underline font-medium flex items-center gap-2 min-w-0"
                        title={url.title || url.shortId}
                      >
                        {getCategoryIcon(url.category)}
                        <span className="truncate flex-1">{url.title || url.shortId}</span>
                        {url.passwordProtected && (
                          <Lock className="w-3 h-3 text-muted-foreground/60 shrink-0" />
                        )}
                      </a>
                      {url.title != null && url.title !== "" && (
                        <span className="text-[10px] text-muted-foreground/60 font-mono block truncate">
                          /{url.shortId}
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-4">
                      <div
                        className="truncate text-muted-foreground"
                        title={url.longUrl}
                      >
                        {url.longUrl}
                      </div>
                      {url.tags && url.tags.length > 0 && (
                        <div className="flex flex-wrap gap-1 mt-1 font-mono">
                          {url.tags.map(tag => (
                            <span key={tag} className="inline-flex items-center gap-1 px-1.5 py-0.5 text-[9px] font-medium bg-primary/10 text-primary border border-primary/20 rounded">
                              {tag}
                            </span>
                          ))}
                        </div>
                      )}
                    </td>
                    <td className="px-2 py-4 text-center font-semibold">
                      {url.clickCount}
                    </td>
                    <td className="px-2 py-4 text-muted-foreground whitespace-nowrap overflow-hidden text-ellipsis">
                      {formatDate(url.createdAt)}
                    </td>
                    <td className="px-2 py-4">
                      <div className="flex justify-center">
                        <button
                          type="button"
                          onClick={() => onToggleBio(url.shortId, url.showOnBio)}
                          disabled={togglingIds.has(url.shortId)}
                          className={cn(
                            "w-10 h-5 rounded-full transition-colors relative flex items-center",
                            url.showOnBio ? "bg-primary" : "bg-muted",
                            togglingIds.has(url.shortId) &&
                              "opacity-50 cursor-not-allowed",
                          )}
                          title={url.showOnBio ? "Hide from Bio" : "Show on Bio"}
                        >
                          {togglingIds.has(url.shortId) ? (
                            <Loader2 className="w-3 h-3 animate-spin mx-auto text-white" />
                          ) : (
                            <div
                              className={cn(
                                "absolute top-1 w-3 h-3 rounded-full bg-white transition-all",
                                url.showOnBio ? "left-6" : "left-1",
                              )}
                            />
                          )}
                        </button>
                      </div>
                    </td>
                    <td className="px-2 py-4 text-right">
                      <div className="flex items-center justify-end gap-1 flex-nowrap">
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => onCopy(url.shortId)}
                          className="h-8 w-8 p-0"
                          title="Copy to clipboard"
                        >
                          <Copy
                            className={cn(
                              "w-4 h-4",
                              copiedId === url.shortId
                                ? "text-success"
                                : "text-muted-foreground",
                            )}
                          />
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => onEdit(url)}
                          className="h-8 w-8 p-0 text-muted-foreground hover:text-primary transition-colors"
                          title="Edit Link Metadata"
                        >
                          <Pencil className="w-4 h-4" />
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => onInsights(url.shortId)}
                          className="h-8 w-8 p-0 text-muted-foreground hover:text-primary hover:bg-primary/10"
                          title="View detailed insights"
                        >
                          <TrendingUp className="w-4 h-4" />
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => {
                            onQr(url.shortId);
                          }}
                          className="h-8 w-8 p-0 text-muted-foreground"
                          title="Generate QR code"
                        >
                          <QrCode className="w-4 h-4" />
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => onDelete(url.shortId)}
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

          <div className="flex flex-col sm:flex-row justify-between items-stretch sm:items-center gap-3 px-4 sm:px-6 py-4 border-t border-border/50 bg-secondary/20">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0 || isLoading}
              onClick={onPagePrev}
              className="w-full sm:w-auto sm:order-none order-2"
            >
              Previous
            </Button>

            <div className="text-sm font-medium text-muted-foreground text-center order-1 sm:order-none">
              Page <span className="text-foreground">{page + 1}</span> of {totalPages}
            </div>

            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1 || isLoading}
              onClick={onPageNext}
              className="w-full sm:w-auto order-3"
            >
              Next
            </Button>
          </div>
        </GlassPanel>
      )}
    </div>
  );
}
