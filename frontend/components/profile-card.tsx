"use client"

import { User, Sparkles } from "lucide-react"
import { LinkButton } from "@/components/link-button"
import { cn } from "@/lib/utils"

export interface ProfileLink {
  id: string
  label: string
  href: string
  icon?: React.ReactNode
}

export interface ProfileData {
  displayName: string
  username: string
  bio: string
  avatarUrl?: string
  themeColor: string
  links: ProfileLink[]
}

interface ProfileCardProps {
  profile: ProfileData
  className?: string
  showWatermark?: boolean
}

export function ProfileCard({ 
  profile, 
  className,
  showWatermark = true 
}: ProfileCardProps) {
  const { displayName, username, bio, avatarUrl, themeColor, links } = profile

  return (
    <div className="flex min-h-screen w-full items-center justify-center p-4">
      <div 
        className={cn(
          "relative w-full max-w-md overflow-hidden rounded-3xl border border-border/30 bg-card/30 p-6 backdrop-blur-2xl md:p-8",
          className
        )}
        style={{
          boxShadow: `0 0 60px ${themeColor}15, 0 0 0 1px ${themeColor}10`
        }}
      >
        {/* Top gradient accent */}
        <div 
          className="absolute inset-x-0 top-0 h-1 opacity-80"
          style={{
            background: `linear-gradient(90deg, transparent, ${themeColor}, transparent)`
          }}
        />

        {/* Inner glow */}
        <div 
          className="pointer-events-none absolute inset-0 opacity-30"
          style={{
            background: `radial-gradient(ellipse at top, ${themeColor}20 0%, transparent 50%)`
          }}
        />

        {/* Content */}
        <div className="relative z-10">
          {/* Avatar with glow ring */}
          <div className="mb-5 flex justify-center">
            <div 
              className="relative h-24 w-24 rounded-full md:h-28 md:w-28"
              style={{
                boxShadow: `0 0 30px ${themeColor}40, 0 0 60px ${themeColor}20`
              }}
            >
              {/* Glow ring */}
              <div 
                className="absolute -inset-1 rounded-full opacity-60"
                style={{
                  background: `conic-gradient(from 0deg, ${themeColor}, transparent, ${themeColor})`,
                  animation: 'spin 8s linear infinite'
                }}
              />
              
              {/* Avatar container */}
              <div 
                className="relative flex h-full w-full items-center justify-center overflow-hidden rounded-full border-4"
                style={{ 
                  borderColor: themeColor,
                  background: avatarUrl 
                    ? `url(${avatarUrl}) center/cover` 
                    : `linear-gradient(135deg, ${themeColor}50 0%, ${themeColor}20 100%)`
                }}
              >
                {!avatarUrl && (
                  <User className="h-10 w-10 text-foreground/60 md:h-12 md:w-12" />
                )}
              </div>
            </div>
          </div>

          {/* Display Name */}
          <h1 className="mb-1 text-center text-2xl font-bold tracking-tight text-foreground md:text-3xl">
            {displayName || "Your Name"}
          </h1>

          {/* Username */}
          <p 
            className="mb-4 text-center text-sm font-medium md:text-base"
            style={{ color: themeColor }}
          >
            @{username || "username"}
          </p>

          {/* Bio */}
          {bio && (
            <p className="mx-auto mb-6 max-w-xs text-center text-sm leading-relaxed text-muted-foreground md:text-base">
              {bio}
            </p>
          )}

          {/* Links */}
          {links.length > 0 && (
            <div className="space-y-3">
              {links.map((link) => (
                <LinkButton
                  key={link.id}
                  href={link.href}
                  label={link.label}
                  themeColor={themeColor}
                  icon={link.icon}
                />
              ))}
            </div>
          )}

          {/* Watermark */}
          {showWatermark && (
            <div className="mt-8 flex items-center justify-center gap-1.5 text-xs text-muted-foreground/40">
              <Sparkles className="h-3 w-3" />
              <span>Powered by ShunyaLink</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
