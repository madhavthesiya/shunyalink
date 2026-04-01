import { ShortenerForm } from "@/components/shortener-form";
import { ResultBox } from "@/components/result-box";
import { GlassPanel } from "@/components/shell/glass-panel";
import { InlineAlert } from "@/components/shell/inline-alert";

interface ShortenResult {
  shortId: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
}

export function HomeShortenSection({
  error,
  result,
  onSuccess,
  onError,
  onViewStats,
  onGenerateQR,
}: {
  error: string | null;
  result: ShortenResult | null;
  onSuccess: (data: ShortenResult) => void;
  onError: (message: string) => void;
  onViewStats: () => void;
  onGenerateQR: () => void;
}) {
  return (
    <section className="pb-12 sm:pb-16 px-4 sm:px-6">
      <div className="max-w-xl mx-auto">
        <GlassPanel
          className="animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-400"
        >
          <ShortenerForm onSuccess={onSuccess} onError={onError} />

          {error != null && (
            <div className="mt-6">
              <InlineAlert variant="destructive" title="Something went wrong">
                {error}
              </InlineAlert>
            </div>
          )}
        </GlassPanel>

        {result != null && (
          <div className="mt-8">
            <ResultBox
              shortUrl={result.shortUrl}
              shortId={result.shortId}
              onViewStats={onViewStats}
              onGenerateQR={onGenerateQR}
            />
          </div>
        )}
      </div>
    </section>
  );
}
