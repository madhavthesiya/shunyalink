"use client"

import { cn } from "@/lib/utils"

interface StatCardProps {
  label: string
  value: string | number
  subValue?: string
  themeColor: string
  className?: string
}

export function StatCard({ label, value, subValue, themeColor, className }: StatCardProps) {
  return (
    <div
      className={cn("relative flex flex-col items-center justify-center overflow-hidden rounded-2xl p-8 transition-all", className)}
      style={{
        background: `linear-gradient(135deg, ${themeColor}22 0%, ${themeColor}08 100%)`,
        border: `1px solid ${themeColor}35`,
        boxShadow: `0 0 60px ${themeColor}12, inset 0 1px 0 ${themeColor}30`
      }}
    >
      {/* bg glow blob */}
      <div className="pointer-events-none absolute -bottom-8 -right-8 h-40 w-40 rounded-full opacity-20"
        style={{ background: themeColor, filter: "blur(40px)" }} />

      <span className="relative text-xs font-bold uppercase tracking-[0.2em] text-foreground/50">{label}</span>
      <span
        className="relative mt-2 text-6xl font-black tracking-tighter leading-none"
        style={{ color: themeColor }}
      >
        {value}
      </span>
      {subValue && (
        <span className="relative mt-3 text-xs font-semibold text-foreground/40">{subValue}</span>
      )}
    </div>
  )
}
