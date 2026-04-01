import { Loader2 } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export function LoadingSpinner({
  className,
  label = "Loading",
}: {
  className?: string;
  label?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-3 p-12",
        className,
      )}
      role="status"
      aria-label={label}
    >
      <Loader2 className="h-8 w-8 animate-spin text-primary" />
    </div>
  );
}

export function StatsStripSkeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "grid grid-cols-1 sm:grid-cols-3 gap-4 sm:gap-6 max-w-4xl mx-auto",
        className,
      )}
    >
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          className="p-6 sm:p-8 rounded-3xl bg-card border border-border/60 shadow-sm"
        >
          <Skeleton className="h-9 sm:h-11 w-full max-w-[120px] mx-auto mb-2" />
          <Skeleton className="h-4 w-24 mx-auto" />
        </div>
      ))}
    </div>
  );
}

export function TableSkeletonRows({
  rows = 5,
  cols = 6,
}: {
  rows?: number;
  cols?: number;
}) {
  return (
    <div className="space-y-3 p-4">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex gap-3">
          {Array.from({ length: cols }).map((_, c) => (
            <Skeleton key={c} className="h-10 flex-1" />
          ))}
        </div>
      ))}
    </div>
  );
}
