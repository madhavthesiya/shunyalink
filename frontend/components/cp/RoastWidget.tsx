"use client"

import { useState } from "react"
import { Sparkles, Loader2, RefreshCw } from "lucide-react"
import { cn } from "@/lib/utils"

interface RoastWidgetProps {
  username: string
  themeColor: string
  className?: string
}

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"

export function RoastWidget({ username, themeColor, className }: RoastWidgetProps) {
  const [roast, setRoast] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleRoast() {
    setLoading(true)
    try {
      const res = await fetch(`${API_URL}/api/v1/portfolio/${username}/roast`)
      if (res.ok) {
        const data = await res.json()
        setRoast(data.roast)
      }
    } catch {
      setRoast("The roast engine is running too hot. Try again later.")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className={cn("relative overflow-hidden rounded-2xl p-6 flex flex-col transition-all", className)}
      style={{
        background: "hsl(var(--card) / 0.5)",
        border: `1px solid ${themeColor}25`,
        backdropFilter: "blur(20px)",
        boxShadow: `0 0 0 1px ${themeColor}10, 0 8px 40px -8px ${themeColor}15`
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <p className="text-[10px] font-black uppercase tracking-[0.2em] text-foreground/40">AI Roast</p>
          <p className="text-sm font-bold text-foreground/70">Brutally honest 🔥</p>
        </div>
        <div className="p-2 rounded-xl" style={{ background: `${themeColor}18` }}>
          <Sparkles className="h-5 w-5" style={{ color: themeColor }} />
        </div>
      </div>

      {/* Roast content */}
      <div className="flex-1 flex flex-col justify-between gap-4">
        {roast ? (
          <div
            className="rounded-xl p-4 text-sm italic leading-relaxed text-foreground/80 animate-in fade-in zoom-in-95 duration-500"
            style={{ background: `${themeColor}10`, border: `1px solid ${themeColor}20` }}
          >
            &ldquo;{roast}&rdquo;
          </div>
        ) : (
          <div
            className="rounded-xl p-4 text-sm text-foreground/30 leading-relaxed"
            style={{ background: `${themeColor}08`, border: `1px dashed ${themeColor}20` }}
          >
            Click to get a sarcastic AI take on your CP profile...
          </div>
        )}

        <button
          onClick={handleRoast}
          disabled={loading}
          className="flex w-full items-center justify-center gap-2 rounded-xl py-3 text-xs font-black uppercase tracking-widest text-white transition-all hover:scale-[1.02] active:scale-[0.98] disabled:opacity-50"
          style={{
            background: `linear-gradient(135deg, ${themeColor}, ${themeColor}cc)`,
            boxShadow: `0 4px 20px ${themeColor}40`
          }}
        >
          {loading ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : roast ? (
            <><RefreshCw className="h-3.5 w-3.5" /> Roast Again</>
          ) : (
            <><Sparkles className="h-3.5 w-3.5" /> Roast Me</>
          )}
        </button>
      </div>
    </div>
  )
}
