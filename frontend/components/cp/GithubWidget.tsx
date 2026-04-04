"use client"

import { GitBranch, Users } from "lucide-react"
import { cn } from "@/lib/utils"

interface GithubWidgetProps {
  stats: any
  themeColor: string
  className?: string
}

export function GithubWidget({ stats, themeColor, className }: GithubWidgetProps) {
  if (!stats || stats.error) return null

  const items = [
    { icon: GitBranch, label: "Repos", value: stats.publicRepos ?? 0 },
    { icon: Users, label: "Followers", value: stats.followers ?? 0 },
  ]

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
        <p className="text-[10px] font-black uppercase tracking-[0.2em] text-foreground/40">GitHub</p>
        <div className="h-7 w-7 rounded-lg bg-white flex items-center justify-center p-0.5 shadow-sm">
          <img src="/github-logo.png" alt="GitHub" className="h-full w-full object-contain" />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {items.map(({ icon: Icon, label, value }) => (
          <div
            key={label}
            className="flex flex-col items-center justify-center rounded-xl py-4 gap-1"
            style={{ background: `${themeColor}10`, border: `1px solid ${themeColor}20` }}
          >
            <Icon className="h-4 w-4 mb-1 opacity-60" style={{ color: themeColor }} />
            <span className="text-2xl font-black text-foreground">{value}</span>
            <span className="text-[10px] font-bold uppercase tracking-wider text-foreground/40">{label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
