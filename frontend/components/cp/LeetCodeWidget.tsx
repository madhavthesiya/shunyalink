"use client"

import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from "recharts"
import { cn } from "@/lib/utils"

interface LeetCodeWidgetProps {
  stats: any
  themeColor: string
  className?: string
}

export function LeetCodeWidget({ stats, themeColor, className }: LeetCodeWidgetProps) {
  if (!stats || stats.error) return null

  const data = [
    { name: "Easy", value: stats.easySolved || 0, color: "#22c55e" },
    { name: "Medium", value: stats.mediumSolved || 0, color: "#f59e0b" },
    { name: "Hard", value: stats.hardSolved || 0, color: "#ef4444" },
  ]
  const total = stats.totalSolved || 0

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
      <div className="flex items-center justify-between mb-5">
        <div>
          <p className="text-[10px] font-black uppercase tracking-[0.2em] text-foreground/40">LeetCode</p>
          <p className="text-2xl font-black" style={{ color: themeColor }}>{total} <span className="text-sm font-bold text-foreground/50">solved</span></p>
        </div>
        <img src="/leetcode-logo.png" alt="LeetCode" className="h-7 w-7 opacity-90" />
      </div>

      {/* donut + legend */}
      <div className="flex items-center gap-4">
        <div className="h-28 w-28 flex-shrink-0 relative">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Tooltip 
                offset={80} // Move the tooltip far enough to not cover the center
                contentStyle={{ 
                  background: "var(--background)", 
                  border: "2px solid hsl(var(--primary) / 0.5)", 
                  borderRadius: "12px", 
                  fontSize: "12px",
                  fontWeight: "bold",
                  color: "var(--foreground)",
                  boxShadow: "0 20px 25px -5px rgb(0 0 0 / 0.2)",
                  padding: "8px 12px"
                }}
                itemStyle={{ color: "var(--foreground)", padding: "0" }}
                labelStyle={{ display: "none" }}
                cursor={{ fill: 'transparent' }}
              />
              <Pie data={data} innerRadius={36} outerRadius={50} paddingAngle={3} dataKey="value" strokeWidth={0}>
                {data.map((entry, i) => <Cell key={i} fill={entry.color} />)}
              </Pie>
            </PieChart>
          </ResponsiveContainer>
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
            <span className="text-lg font-black text-foreground">{total}</span>
          </div>
        </div>
        <div className="flex flex-col gap-2.5 flex-1">
          {data.map(d => (
            <div key={d.name} className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="h-2 w-2 rounded-full" style={{ background: d.color }} />
                <span className="text-xs font-semibold text-foreground/60">{d.name}</span>
              </div>
              <span className="text-xs font-black text-foreground">{d.value}</span>
            </div>
          ))}
          {stats.rating > 0 && (
            <div className="mt-1 flex items-center justify-between border-t border-border/30 pt-2">
              <span className="text-[10px] text-foreground/40 uppercase tracking-wider">Rating</span>
              <span className="text-xs font-black" style={{ color: themeColor }}>
                {stats.rating} {stats.maxRating > 0 && <span className="opacity-50 text-[10px]">(Max: {stats.maxRating})</span>}
              </span>
            </div>
          )}
          {stats.badgeName && (
             <div className="mt-0.5 flex items-center justify-between">
              <span className="text-[10px] text-foreground/40 uppercase tracking-wider">Badge</span>
              <span className="text-[11px] font-black tracking-wide" style={{ color: themeColor }}>{stats.badgeName}</span>
            </div>
          )}
          {stats.globalRanking > 0 && (
            <div className="mt-0.5 flex items-center justify-between">
              <span className="text-[10px] text-foreground/40 uppercase tracking-wider">Global Rank</span>
              <span className="text-[11px] font-black" style={{ color: themeColor }}>#{stats.globalRanking.toLocaleString()}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
