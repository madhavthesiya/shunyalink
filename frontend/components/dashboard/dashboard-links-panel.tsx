"use client";

import {
  Copy,
  Loader2,
  Lock,
  Pencil,
  QrCode,
  Trash2,
  TrendingUp,
} from "lucide-react";
import { Button } from "@/components/ui/button";
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
  onPagePrev,
  onPageNext,
  formatDate,
}: {
  apiBaseUrl: string;
  urls: DashboardUrlData[];
  isLoading: boolean;
  page: number;
  totalPages: number;
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
  onPagePrev: () => void;
  onPageNext: () => void;
  formatDate: (iso: string) => string;
}) {
  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h2 className="text-2xl font-bold text-foreground">Your Shortened URLs</h2>
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
                      className="text-primary font-medium break-words flex items-center gap-2"
                    >
                      {url.title || url.shortId}
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

          {/* Desktop table */}
          <div className="hidden md:block overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border/50 bg-secondary/50">
                <tr>
                  <th className="px-4 lg:px-6 py-4 text-left">
                    <Checkbox
                      checked={urls.length > 0 && selectedIds.size === urls.length}
                      onCheckedChange={onToggleSelectAll}
                      aria-label="Select all"
                    />
                  </th>
                  <th className="px-4 lg:px-6 py-4 text-left font-semibold text-foreground">
                    Short URL
                  </th>
                  <th className="px-4 lg:px-6 py-4 text-left font-semibold text-foreground">
                    Original URL
                  </th>
                  <th className="px-4 lg:px-6 py-4 text-center font-semibold text-foreground">
                    Clicks
                  </th>
                  <th className="px-4 lg:px-6 py-4 text-left font-semibold text-foreground">
                    Created
                  </th>
                  <th className="px-4 lg:px-6 py-4 text-center font-semibold text-foreground">
                    Bio
                  </th>
                  <th className="px-4 lg:px-6 py-4 text-right font-semibold text-foreground">
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
                    <td className="px-4 lg:px-6 py-4">
                      <Checkbox
                        checked={selectedIds.has(url.shortId)}
                        onCheckedChange={() => onToggleSelect(url.shortId)}
                        aria-label={`Select ${url.shortId}`}
                        className="data-[state=checked]:bg-primary"
                      />
                    </td>
                    <td className="px-4 lg:px-6 py-4">
                      <a
                        href={`${apiBaseUrl}/${url.shortId}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-primary hover:underline font-medium truncate flex items-center gap-2"
                      >
                        {url.title || url.shortId}
                        {url.passwordProtected && (
                          <Lock className="w-3 h-3 text-muted-foreground/60 shrink-0" />
                        )}
                      </a>
                      {url.title != null && url.title !== "" && (
                        <span className="text-[10px] text-muted-foreground/60 font-mono block">
                          /{url.shortId}
                        </span>
                      )}
                    </td>
                    <td className="px-4 lg:px-6 py-4">
                      <div
                        className="truncate text-muted-foreground max-w-[180px] lg:max-w-[300px]"
                        title={url.longUrl}
                      >
                        {url.longUrl}
                      </div>
                    </td>
                    <td className="px-4 lg:px-6 py-4 text-center font-semibold">
                      {url.clickCount}
                    </td>
                    <td className="px-4 lg:px-6 py-4 text-muted-foreground whitespace-nowrap">
                      {formatDate(url.createdAt)}
                    </td>
                    <td className="px-4 lg:px-6 py-4">
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
                    <td className="px-4 lg:px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-2 flex-wrap">
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
