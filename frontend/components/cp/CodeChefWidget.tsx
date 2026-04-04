"use client"

import { cn } from "@/lib/utils"

interface CodeChefWidgetProps {
  stats: any
  themeColor: string
  className?: string
}

export function CodeChefWidget({ stats, themeColor, className }: CodeChefWidgetProps) {
  if (!stats || stats.error) return null

  const items = [
    { label: "Rating", value: stats.rating ?? "—" },
    { label: "Stars", value: stats.stars ?? "—" },
    { label: "Solved", value: stats.totalSolved ?? "—" },
  ].filter(i => i.value !== "—" && i.value !== undefined)

  return (
    <div
      className={cn("relative overflow-hidden rounded-2xl p-6 transition-all", className)}
      style={{
        background: "hsl(var(--card) / 0.5)",
        border: `1px solid ${themeColor}25`,
        backdropFilter: "blur(20px)",
        boxShadow: `0 0 0 1px ${themeColor}10, 0 8px 40px -8px ${themeColor}15`
      }}
    >
      <div className="flex items-center justify-between mb-5">
        <p className="text-[10px] font-black uppercase tracking-[0.2em] text-foreground/40">CodeChef</p>
        <div className="h-7 w-7 rounded-lg bg-white flex items-center justify-center p-0.5 shadow-sm">
          <img src="/codechef-logo.png" alt="CodeChef" className="h-full w-full object-contain" />
        </div>
      </div>

      {/* Primary stat */}
      <p className="text-3xl font-black mb-1 flex items-baseline gap-2" style={{ color: themeColor }}>
        {stats.rating ?? "—"}
        {stats.maxRating > 0 && <span className="text-sm opacity-50 font-semibold">(Max: {stats.maxRating})</span>}
      </p>
      <p className="text-xs font-semibold text-foreground/40 mb-4">Rating</p>

      {/* Secondary stats */}
      <div className="flex gap-4">
        {stats.stars && (
          <div className="flex flex-col">
            <span className="text-lg font-black text-foreground">{stats.stars}</span>
            <span className="text-[10px] font-bold uppercase tracking-wider text-foreground/40">Stars</span>
          </div>
        )}
        {stats.totalSolved != null && (
          <div className="flex flex-col">
            <span className="text-lg font-black text-foreground">{stats.totalSolved}</span>
            <span className="text-[10px] font-bold uppercase tracking-wider text-foreground/40">Solved</span>
          </div>
        )}
      </div>
    </div>
  )
}
