"use client"

import { User, ArrowRight, ExternalLink, Code2, Youtube, Github, FileText, Share2, BookOpen, Link2 } from "lucide-react"
import Link from "next/link"
import { cn } from "@/lib/utils"
import { StatCard } from "./cp/StatCard"
import { LeetCodeWidget } from "./cp/LeetCodeWidget"
import { CodeforcesWidget } from "./cp/CodeforcesWidget"
import { GithubWidget } from "./cp/GithubWidget"
import { CodeChefWidget } from "./cp/CodeChefWidget"
import { AtCoderWidget } from "./cp/AtCoderWidget"
import { RoastWidget } from "./cp/RoastWidget"

export interface ProfileLink {
  id: string
  label: string
  href: string
  category?: string
  icon?: React.ReactNode
}

export interface ProfileData {
  displayName: string
  username: string
  bio: string
  avatarUrl?: string
  profilePictureUrl?: string | null
  themeColor: string
  profileType?: "NORMAL" | "PROGRAMMER"
  portfolio?: any
  links: ProfileLink[]
}

interface ProfileCardProps {
  profile: ProfileData
  className?: string
  showWatermark?: boolean
}

function getCategoryIcon(category?: string) {
  switch (category?.toUpperCase()) {
    case "VIDEO":
      return <Youtube className="h-4 w-4 text-red-500" />;
    case "GITHUB":
      return <Github className="h-4 w-4" />;
    case "DOCUMENTATION":
      return <FileText className="h-4 w-4 text-blue-500" />;
    case "SOCIAL_MEDIA":
      return <Share2 className="h-4 w-4 text-purple-500" />;
    case "PORTFOLIO":
      return <User className="h-4 w-4 text-emerald-500" />;
    case "BLOG":
      return <BookOpen className="h-4 w-4 text-orange-500" />;
    default:
      return null;
  }
}

/* ─── LINK BUTTON ──────────────────────────────────────────── */
function PremiumLinkButton({ href, label, icon, category, themeColor }: {
  href: string; label: string; icon?: React.ReactNode; category?: string; themeColor: string
}) {
  const finalIcon = icon || getCategoryIcon(category);
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="group relative flex w-full items-center gap-3 overflow-hidden rounded-2xl px-5 py-4 transition-all duration-300 hover:scale-[1.02] active:scale-[0.98]"
      style={{
        background: `linear-gradient(135deg, ${themeColor}18 0%, ${themeColor}08 100%)`,
        border: `1px solid ${themeColor}30`,
      }}
      onMouseEnter={e => {
        e.currentTarget.style.background = `linear-gradient(135deg, ${themeColor}30 0%, ${themeColor}15 100%)`
        e.currentTarget.style.borderColor = `${themeColor}70`
        e.currentTarget.style.boxShadow = `0 8px 32px ${themeColor}25`
      }}
      onMouseLeave={e => {
        e.currentTarget.style.background = `linear-gradient(135deg, ${themeColor}18 0%, ${themeColor}08 100%)`
        e.currentTarget.style.borderColor = `${themeColor}30`
        e.currentTarget.style.boxShadow = "none"
      }}
    >
      {/* left color strip or icon */}
      {finalIcon ? (
        <div className="flex-shrink-0">{finalIcon}</div>
      ) : (
        <div className="h-8 w-1 rounded-full flex-shrink-0" style={{ background: themeColor }} />
      )}

      <span className="flex-1 text-sm font-semibold text-foreground truncate">{label}</span>

      <ExternalLink
        className="h-4 w-4 flex-shrink-0 opacity-0 transition-all duration-300 group-hover:opacity-70 -translate-x-2 group-hover:translate-x-0"
        style={{ color: themeColor }}
      />
    </a>
  )
}

/* ─── NORMAL PROFILE ───────────────────────────────────────── */
function NormalProfile({ profile, showWatermark }: { profile: ProfileData; showWatermark: boolean }) {
  const { displayName, username, bio, avatarUrl, profilePictureUrl, themeColor, links } = profile
  const displayAvatar = profilePictureUrl || avatarUrl || null

  return (
    <div className="relative min-h-screen w-full overflow-hidden">
      {/* Full-page layered background */}
      <div className="absolute inset-0" style={{
        background: `radial-gradient(ellipse 120% 60% at 50% -10%, ${themeColor}22 0%, transparent 65%),
                     radial-gradient(ellipse 80% 40% at 100% 100%, ${themeColor}10 0%, transparent 50%)`,
      }} />
      {/* Subtle grid */}
      <div className="absolute inset-0 opacity-[0.03]" style={{
        backgroundImage: `linear-gradient(${themeColor} 1px, transparent 1px), linear-gradient(90deg, ${themeColor} 1px, transparent 1px)`,
        backgroundSize: "48px 48px"
      }} />

      {/* Content */}
      <div className="relative z-10 flex min-h-screen flex-col items-center justify-start px-4 pt-16 pb-24">

        {/* Avatar */}
        <div className="relative mb-6">
          {/* Outer animated glow */}
          <div
            className="absolute -inset-3 rounded-full opacity-50"
            style={{
              background: `conic-gradient(from 0deg, ${themeColor}, transparent 40%, ${themeColor} 60%, transparent)`,
              animation: "spin 6s linear infinite",
              filter: "blur(4px)"
            }}
          />
          {/* Avatar circle */}
          <div
            className="relative flex h-28 w-28 items-center justify-center overflow-hidden rounded-full border-[3px]"
            style={{
              borderColor: themeColor,
              background: displayAvatar
                ? `url(${displayAvatar}) center/cover`
                : `linear-gradient(135deg, ${themeColor}40 0%, ${themeColor}15 100%)`
            }}
          >
            {!displayAvatar && <User className="h-12 w-12 text-foreground/50" />}
          </div>
          {/* Online dot */}
          <div className="absolute bottom-1 right-1 h-4 w-4 rounded-full border-2 border-background bg-emerald-400" />
        </div>

        {/* Name */}
        <h1 className="mb-1 text-3xl font-black tracking-tight text-foreground">{displayName || "Your Name"}</h1>

        {/* Username badge */}
        <div
          className="mb-5 inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-xs font-bold"
          style={{ background: `${themeColor}20`, color: themeColor, border: `1px solid ${themeColor}35` }}
        >
          <span className="opacity-60">@</span>
          <span>{username}</span>
        </div>

        {/* Bio */}
        {bio && (
          <p className="mb-10 max-w-xs text-center text-sm leading-relaxed text-foreground/65">
            {bio}
          </p>
        )}

        {/* Divider */}
        {links.length > 0 && (
          <div className="mb-5 flex w-full max-w-sm items-center gap-3">
            <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, transparent, ${themeColor}40)` }} />
            <span className="text-[10px] font-bold uppercase tracking-[0.25em]" style={{ color: `${themeColor}80` }}>Links</span>
            <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, ${themeColor}40, transparent)` }} />
          </div>
        )}

        {/* Links */}
        <div className="w-full max-w-sm space-y-3">
          {links.map((link, i) => (
            <div
              key={link.id}
              className="animate-in fade-in slide-in-from-bottom-2"
              style={{ animationDelay: `${i * 60}ms`, animationFillMode: "both" }}
            >
              <PremiumLinkButton
                href={link.href}
                label={link.label}
                icon={link.icon}
                category={link.category}
                themeColor={themeColor}
              />
            </div>
          ))}
        </div>

        {/* Watermark */}
        {showWatermark && (
          <div className="mt-14 flex flex-col items-center gap-2 animate-in fade-in duration-1000 delay-700" style={{ animationFillMode: "both" }}>
            <div className="flex items-center gap-2 text-[10px] tracking-[0.2em] uppercase text-foreground/20 font-bold">
              <img src="/logo.png" alt="" className="h-3 w-3 grayscale opacity-30" />
              <span>Powered by ShunyaLink</span>
            </div>
            <Link
              href="/"
              className="group flex items-center gap-1.5 text-[10px] font-semibold text-foreground/25 hover:text-foreground/50 transition-colors"
            >
              Create your free profile
              <ArrowRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}

/* ─── PROGRAMMER PROFILE ───────────────────────────────────── */
function ProgrammerProfile({ profile, showWatermark }: { profile: ProfileData; showWatermark: boolean }) {
  const { displayName, username, bio, avatarUrl, profilePictureUrl, themeColor, links, portfolio } = profile
  const displayAvatar = profilePictureUrl || avatarUrl || null

  return (
    <div className="relative min-h-screen w-full overflow-x-hidden">
      {/* Background */}
      <div className="absolute inset-0" style={{
        background: `radial-gradient(ellipse 100% 50% at 50% 0%, ${themeColor}18 0%, transparent 60%),
                     radial-gradient(ellipse 60% 40% at 0% 100%, ${themeColor}10 0%, transparent 50%),
                     radial-gradient(ellipse 60% 40% at 100% 80%, ${themeColor}08 0%, transparent 50%)`
      }} />
      <div className="absolute inset-0 opacity-[0.025]" style={{
        backgroundImage: `linear-gradient(${themeColor} 1px, transparent 1px), linear-gradient(90deg, ${themeColor} 1px, transparent 1px)`,
        backgroundSize: "40px 40px"
      }} />

      <div className="relative z-10 mx-auto max-w-6xl px-4 py-12 md:py-16">

        {/* ── HERO HEADER ── */}
        <div className="mb-12 flex flex-col items-center text-center">

          {/* Avatar */}
          <div className="relative mb-6">
            <div
              className="absolute -inset-3 rounded-full opacity-40"
              style={{
                background: `conic-gradient(from 0deg, ${themeColor}, transparent 35%, ${themeColor} 65%, transparent)`,
                animation: "spin 8s linear infinite",
                filter: "blur(6px)"
              }}
            />
            <div
              className="relative flex h-24 w-24 md:h-28 md:w-28 items-center justify-center overflow-hidden rounded-full border-[3px]"
              style={{
                borderColor: themeColor,
                background: displayAvatar
                  ? `url(${displayAvatar}) center/cover`
                  : `linear-gradient(135deg, ${themeColor}40, ${themeColor}10)`
              }}
            >
              {!displayAvatar && <User className="h-12 w-12 text-foreground/50" />}
            </div>
          </div>

          {/* Name + handle */}
          <h1 className="mb-2 text-4xl font-black tracking-tight text-foreground md:text-5xl">
            {displayName}
          </h1>
          <div
            className="mb-4 inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-sm font-bold"
            style={{ background: `${themeColor}18`, color: themeColor, border: `1px solid ${themeColor}35` }}
          >
            <Code2 className="h-3.5 w-3.5" />
            <span>@{username}</span>
          </div>

          {bio && (
            <p className="max-w-lg text-base leading-relaxed text-foreground/60">{bio}</p>
          )}
        </div>

        {/* ── CP STATS ── */}
        <section className="mb-12">
          <div className="mb-6 flex items-center gap-4">
            <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, transparent, ${themeColor}50)` }} />
            <span className="text-[10px] font-black uppercase tracking-[0.3em]" style={{ color: themeColor }}>Competitive Programming</span>
            <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, ${themeColor}50, transparent)` }} />
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <StatCard
              label="Total Problems Solved"
              value={portfolio?.totalGloballySolved || 0}
              themeColor={themeColor}
              className="md:col-span-2"
            />
            <RoastWidget username={username} themeColor={themeColor} />
            <LeetCodeWidget stats={portfolio?.leetcode} themeColor={themeColor} />
            <CodeforcesWidget stats={portfolio?.codeforces} themeColor={themeColor} />
            <GithubWidget stats={portfolio?.github} themeColor={themeColor} />
            <CodeChefWidget stats={portfolio?.codechef} themeColor={themeColor} />
            <AtCoderWidget stats={portfolio?.atcoder} themeColor={themeColor} username={username} />
          </div>
        </section>

        {/* ── LINKS ── */}
        {links.length > 0 && (
          <section className="mb-12">
            <div className="mb-6 flex items-center gap-4">
              <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, transparent, ${themeColor}50)` }} />
              <span className="text-[10px] font-black uppercase tracking-[0.3em]" style={{ color: themeColor }}>Links</span>
              <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, ${themeColor}50, transparent)` }} />
            </div>
            <div className="mx-auto grid max-w-2xl grid-cols-1 gap-3 sm:grid-cols-2">
              {links.map((link, i) => (
                <div
                  key={link.id}
                  className="animate-in fade-in slide-in-from-bottom-2"
                  style={{ animationDelay: `${i * 50}ms`, animationFillMode: "both" }}
                >
                  <PremiumLinkButton
                    href={link.href}
                    label={link.label}
                    icon={link.icon}
                    category={link.category}
                    themeColor={themeColor}
                  />
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Watermark */}
        {showWatermark && (
          <div className="flex flex-col items-center gap-2 pb-4">
            <div className="flex items-center gap-2 text-[10px] tracking-[0.2em] uppercase text-foreground/15 font-bold">
              <img src="/logo.png" alt="" className="h-3 w-3 grayscale opacity-25" />
              <span>Powered by ShunyaLink</span>
            </div>
            <Link
              href="/"
              className="group flex items-center gap-1.5 text-[10px] font-semibold text-foreground/20 hover:text-foreground/45 transition-colors"
            >
              Create your free profile
              <ArrowRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}

/* ─── MAIN EXPORT ──────────────────────────────────────────── */
export function ProfileCard({
  profile,
  className,
  showWatermark = true
}: ProfileCardProps) {
  if (profile.profileType === "PROGRAMMER") {
    return <ProgrammerProfile profile={profile} showWatermark={showWatermark} />
  }
  return <NormalProfile profile={profile} showWatermark={showWatermark} />
}
