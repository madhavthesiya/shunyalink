"use client"

import { cn } from "@/lib/utils"
import { ExternalLink } from "lucide-react"

interface LinkButtonProps {
  href: string
  label: string
  themeColor?: string
  icon?: React.ReactNode
  className?: string
}

export function LinkButton({ 
  href, 
  label, 
  themeColor = "#6366f1",
  icon,
  className 
}: LinkButtonProps) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className={cn(
        "group relative flex w-full items-center justify-center gap-2 overflow-hidden rounded-xl border border-border/50 bg-card/50 px-5 py-3.5 text-sm font-medium text-foreground backdrop-blur-sm transition-all duration-300 hover:scale-[1.02] hover:border-transparent",
        className
      )}
      style={{
        boxShadow: `0 0 0 1px ${themeColor}20`,
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.boxShadow = `0 0 20px ${themeColor}30, 0 0 0 1px ${themeColor}50`
        e.currentTarget.style.borderColor = `${themeColor}50`
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.boxShadow = `0 0 0 1px ${themeColor}20`
        e.currentTarget.style.borderColor = ''
      }}
    >
      {/* Hover gradient overlay */}
      <div 
        className="absolute inset-0 opacity-0 transition-opacity duration-300 group-hover:opacity-100"
        style={{
          background: `linear-gradient(135deg, ${themeColor}15 0%, transparent 50%)`
        }}
      />
      
      {/* Content */}
      <span className="relative z-10 flex items-center gap-2">
        {icon}
        {label}
      </span>
      
      {/* External link icon */}
      <ExternalLink 
        className="relative z-10 h-4 w-4 opacity-0 transition-all duration-300 group-hover:opacity-60" 
        style={{ color: themeColor }}
      />
    </a>
  )
}
