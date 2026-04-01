import { StatsStripSkeleton } from "@/components/shell/loading-block";

export interface PublicStats {
  totalLinks: number;
  totalUsers: number;
  totalClicks: number;
}

export function PublicStatsStrip({
  stats,
  loading,
}: {
  stats: PublicStats | null;
  loading: boolean;
}) {
  if (!loading && stats == null) {
    return null;
  }

  return (
    <section className="pb-20 px-4 sm:px-6 mt-4 border-t border-border/60 pt-16 bg-muted/20">
      <div className="max-w-4xl mx-auto">
        <div className="text-center mb-10">
          <h2 className="text-2xl sm:text-3xl font-bold text-foreground tracking-tight">
            By the numbers
          </h2>
          <p className="text-sm text-muted-foreground mt-2">Live platform activity</p>
        </div>

        {loading ? (
          <StatsStripSkeleton />
        ) : (
          stats != null && (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 sm:gap-6 text-center animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-300">
              <div className="p-6 sm:p-8 rounded-3xl bg-card border border-border/70 shadow-sm hover:shadow-md hover:border-primary/20 transition-all duration-300">
                <div className="text-3xl sm:text-4xl font-bold tracking-tight text-primary mb-1 tabular-nums">
                  {stats.totalLinks.toLocaleString()}
                </div>
                <div className="text-xs sm:text-sm font-medium text-muted-foreground uppercase tracking-wider">
                  Links created
                </div>
              </div>
              <div className="p-6 sm:p-8 rounded-3xl bg-card border border-border/70 shadow-sm hover:shadow-md hover:border-primary/20 transition-all duration-300">
                <div className="text-3xl sm:text-4xl font-bold tracking-tight text-primary mb-1 tabular-nums">
                  {stats.totalClicks.toLocaleString()}
                </div>
                <div className="text-xs sm:text-sm font-medium text-muted-foreground uppercase tracking-wider">
                  Total clicks
                </div>
              </div>
              <div className="p-6 sm:p-8 rounded-3xl bg-card border border-border/70 shadow-sm hover:shadow-md hover:border-primary/20 transition-all duration-300">
                <div className="text-3xl sm:text-4xl font-bold tracking-tight text-primary mb-1 tabular-nums">
                  {stats.totalUsers.toLocaleString()}
                </div>
                <div className="text-xs sm:text-sm font-medium text-muted-foreground uppercase tracking-wider">
                  Happy users
                </div>
              </div>
            </div>
          )
        )}
      </div>
    </section>
  );
}
