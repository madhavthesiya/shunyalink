"use client"

import { AreaChart, Area, ResponsiveContainer, Tooltip } from "recharts"
import { cn } from "@/lib/utils"

interface CodeforcesWidgetProps {
  stats: any
  themeColor: string
  className?: string
}

export function CodeforcesWidget({ stats, themeColor, className }: CodeforcesWidgetProps) {
  if (!stats || stats.error) return null

  const history = stats.history || stats.ratingHistory || []
  const chartData = history.slice(-15).map((h: any) => ({
    name: new Date(h.ratingUpdateTimeSeconds * 1000).toLocaleDateString("en", { month: "short", year: "2-digit" }),
    rating: h.newRating,
  }))

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
      {/* header */}
      <div className="flex items-center justify-between mb-3">
        <div>
          <p className="text-[10px] font-black uppercase tracking-[0.2em] text-foreground/40">Codeforces</p>
          <p className="text-2xl font-black flex items-baseline gap-2" style={{ color: themeColor }}>
            {stats.rating || "—"} 
            {stats.maxRating > 0 && <span className="text-sm opacity-50 font-semibold">(Max: {stats.maxRating})</span>}
          </p>
          {stats.rank && <p className="text-xs font-bold text-foreground/50 mt-1 capitalize">{stats.rank}</p>}
          {stats.totalSolved != null && (
            <p className="text-xs font-semibold text-foreground/50 mt-0.5">{stats.totalSolved} problems solved</p>
          )}
        </div>
        <img src="/codeforces-logo.png" alt="Codeforces" className="h-7 w-7 opacity-90" />
      </div>

      {/* chart */}
      {chartData.length > 1 && (
        <div className="h-24 w-full mt-4">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData}>
              <defs>
                <linearGradient id={`cfGrad-${themeColor}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={themeColor} stopOpacity={0.4} />
                  <stop offset="95%" stopColor={themeColor} stopOpacity={0.02} />
                </linearGradient>
              </defs>
              <Tooltip
                contentStyle={{ background: "#111", border: "1px solid #333", borderRadius: 8, fontSize: 11 }}
                itemStyle={{ color: themeColor }}
              />
              <Area
                type="monotone"
                dataKey="rating"
                stroke={themeColor}
                strokeWidth={2.5}
                fill={`url(#cfGrad-${themeColor})`}
                dot={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}
