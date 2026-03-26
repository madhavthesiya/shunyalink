"use client"

import { User } from "lucide-react"
import { LinkButton } from "@/components/link-button"

interface ProfileLink {
  shortId: string
  longUrl: string
}

interface ProfileData {
  username: string
  name: string
  bioText: string
  themeColor: string
  links: ProfileLink[]
}

interface ProfileCardProps {
  profile: ProfileData
}

export function ProfileCard({ profile }: ProfileCardProps) {
  return (
    <div className="flex min-h-screen w-full items-center justify-center px-4 py-12">
      {/* Ambient background glow */}
      <div
        className="pointer-events-none fixed inset-0 opacity-20"
        style={{
          background: `radial-gradient(ellipse at 50% 30%, ${profile.themeColor}40 0%, transparent 60%)`,
        }}
      />

      <div className="relative z-10 flex w-full max-w-md flex-col items-center">
        {/* Profile Header */}
        <div className="mb-8 flex flex-col items-center text-center">
          {/* Avatar */}
          <div
            className="mb-6 flex h-24 w-24 items-center justify-center rounded-full backdrop-blur-md"
            style={{
              backgroundColor: `${profile.themeColor}20`,
              borderColor: `${profile.themeColor}50`,
              borderWidth: "2px",
              borderStyle: "solid",
            }}
          >
            <User
              className="h-10 w-10"
              style={{ color: profile.themeColor }}
            />
          </div>

          {/* Name */}
          <h1 className="mb-1 text-2xl font-bold tracking-tight text-foreground">
            {profile.name}
          </h1>

          {/* Username */}
          <p className="mb-4 text-sm text-muted-foreground">
            @{profile.username}
          </p>

          {/* Bio */}
          <p className="max-w-xs text-pretty text-sm leading-relaxed text-muted-foreground">
            {profile.bioText}
          </p>
        </div>

        {/* Links */}
        <div className="flex w-full flex-col gap-3">
          {profile.links.map((link) => (
            <LinkButton
              key={link.shortId}
              shortId={link.shortId}
              longUrl={link.longUrl}
              themeColor={profile.themeColor}
            />
          ))}
        </div>

        {/* Watermark */}
        <div className="mt-12 flex items-center gap-1.5 text-xs text-muted-foreground/50">
          <span>Powered by</span>
          <span
            className="font-semibold"
            style={{ color: `${profile.themeColor}80` }}
          >
            ShunyaLink
          </span>
        </div>
      </div>
    </div>
  )
}
