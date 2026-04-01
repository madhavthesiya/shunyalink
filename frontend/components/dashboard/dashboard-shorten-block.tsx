"use client";

import { forwardRef } from "react";
import { ShortenerForm } from "@/components/shortener-form";
import { GlassPanel } from "@/components/shell/glass-panel";
import { InlineAlert } from "@/components/shell/inline-alert";

interface ShortenResponse {
  shortId: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
  title?: string;
  passwordProtected: boolean;
  password?: string;
}

export const DashboardShortenBlock = forwardRef<
  HTMLDivElement,
  {
    error: string | null;
    onSuccess: (data: ShortenResponse) => void;
    onError: (message: string) => void;
  }
>(({ error, onSuccess, onError }, ref) => {
  return (
    <div ref={ref}>
      <h2 className="text-2xl font-bold text-foreground mb-6">
        Create New Short URL
      </h2>

      {error != null && (
        <div className="mb-8">
          <InlineAlert variant="destructive" title="Error">
            {error}
          </InlineAlert>
        </div>
      )}

      <GlassPanel>
        <ShortenerForm onSuccess={onSuccess} onError={onError} />
      </GlassPanel>
    </div>
  );
});

DashboardShortenBlock.displayName = "DashboardShortenBlock";
