import Link from "next/link";
import {
  BarChart3,
  Settings2,
  ArrowRight,
  Lock,
  Globe2,
  QrCode,
  Tag,
  PieChart,
  Search,
  Image,
  FileDown,
  ShieldCheck,
} from "lucide-react";
import { cn } from "@/lib/utils";

const features = [
  {
    icon: BarChart3,
    title: "Verified CP Stats",
    body: (
      <>
        Connect your LeetCode, Codeforces, and more. We aggregate your results into <strong className="font-bold text-foreground">clickable, interactive tiles</strong> that link directly to your verified profiles.
      </>
    ),
    className: "md:col-span-2 md:row-span-2 bg-gradient-to-br from-indigo-500/10 via-white dark:via-[#0a0a0f] to-indigo-500/5",
    iconColor: "text-indigo-600 dark:text-indigo-400",
    gradient: "from-indigo-500/20 to-violet-500/20"
  },
  {
    icon: Tag,
    title: "AI Auto-Categorization",
    body: "Gemini AI analyzes URL metadata to automatically tag links as GITHUB, VIDEO, or DOCS for organized curation.",
    className: "md:col-span-1 md:row-span-1 bg-gradient-to-br from-pink-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-pink-600 dark:text-pink-400",
    gradient: "from-pink-500/20 to-rose-500/20"
  },
  {
    icon: PieChart,
    title: "Advanced Analytics",
    body: "Track every click with real-time geographical mapping, referrer source tracking, and device-level analytics.",
    className: "md:col-span-1 md:row-span-1 bg-gradient-to-bl from-emerald-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-emerald-600 dark:text-emerald-400",
    gradient: "from-emerald-500/20 to-teal-500/20"
  },
  {
    icon: Lock,
    title: "Password Gates",
    body: "Protect your sensitive links mathematically with AES-256 password challenges.",
    className: "md:col-span-1 md:row-span-1 bg-gradient-to-tr from-blue-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-blue-600 dark:text-blue-400",
    gradient: "from-blue-500/20 to-cyan-500/20"
  },
  {
    icon: Search,
    title: "Global Dashboard Search",
    body: "Instantly search all your links by title, short ID, original URL, or custom tags. Powered by a PostgreSQL full-text join query.",
    className: "md:col-span-1 md:row-span-1 bg-gradient-to-t from-amber-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-amber-600 dark:text-amber-400",
    gradient: "from-amber-500/20 to-orange-500/20",
  },
  {
    icon: Image,
    title: "Cloud Profile Pictures",
    body: "Upload a profile photo that auto-compresses to WebP via Cloudinary CDN. Persistent across Azure deployments.",
    className: "md:col-span-1 md:row-span-1 bg-gradient-to-l from-red-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-red-600 dark:text-red-400",
    gradient: "from-red-500/20 to-pink-500/20",
  },
  {
    icon: FileDown,
    title: "CSV Bulk Import & Export",
    body: "Bulk-upload hundreds of URLs via drag-and-drop CSV, or export your entire link library with tags and categories.",
    className: "md:col-span-1 md:row-span-1 bg-gradient-to-r from-purple-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-purple-600 dark:text-purple-400",
    gradient: "from-purple-500/20 to-fuchsia-500/20",
  },
  {
    icon: Globe2,
    title: "Custom Aliases",
    body: "Create memorable, branded short vanity URLs with UTM campaign builder integration that never expire.",
    className: "md:col-span-1 md:row-span-1 bg-gradient-to-l from-rose-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-rose-600 dark:text-rose-400",
    gradient: "from-rose-500/20 to-pink-500/20"
  },
  {
    icon: ShieldCheck,
    title: "AI Phishing Detection",
    body: "Every URL is screened by Gemini AI before shortening. Malicious and typosquatting links are blocked in real-time before they can be created.",
    className: "md:col-span-1 md:row-span-1 bg-gradient-to-br from-green-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-green-600 dark:text-green-400",
    gradient: "from-green-500/20 to-emerald-500/20"
  },
  {
    icon: QrCode,
    title: "Branded QR Codes",
    body: (
      <>
        Generate high-quality QR codes with <strong className="font-bold text-foreground">ShunyaLink branding</strong> and Level-H error correction for secure offline sharing.
      </>
    ),
    className: "md:col-span-1 md:row-span-1 bg-gradient-to-t from-sky-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-sky-600 dark:text-sky-400",
    gradient: "from-sky-500/20 to-cyan-500/20"
  },
  {
    icon: Settings2,
    title: "Developer Bio-Link Builder",
    body: "Build a professional storefront for your identity. Drag and drop to reorder links, customize themes, and host an integrated portfolio.",
    className: "md:col-span-2 md:row-span-1 bg-gradient-to-tl from-cyan-500/10 via-white dark:via-[#0a0a0f] to-transparent",
    iconColor: "text-cyan-600 dark:text-cyan-400",
    gradient: "from-cyan-500/20 to-sky-500/20"
  },
];


export function FeatureGrid() {
  return (
    <section className="py-16 sm:py-20 border-t border-border/30 bg-background dark:bg-[#060609] relative overflow-hidden">
      {/* Background Glows */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-[1000px] h-[300px] bg-primary/10 blur-[120px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 relative z-10">
        <div className="text-center mb-16 sm:mb-24 max-w-3xl mx-auto">
          <span className="inline-block px-3 py-1 rounded-full bg-primary/10 border border-primary/20 text-primary text-xs font-bold uppercase tracking-wider mb-6">
            The Ultimate Toolkit
          </span>
          <h2 className="text-4xl sm:text-6xl font-bold text-foreground mb-6 text-balance tracking-tight leading-tight">
            Everything you need for <br />
            <span className="text-gradient">links & identity</span>
          </h2>
          <p className="text-muted-foreground text-lg sm:text-xl leading-relaxed">
            Infrastructure-grade URL shortening combined with a developer-first link-in-bio platform. Built for performance and security.
          </p>
        </div>

        {/* Bento Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 sm:gap-6 auto-rows-[220px] md:auto-rows-[260px]">
          {features.map(({ icon: Icon, title, body, className, iconColor, gradient }, index) => (
            <div
              key={title}
              className={cn(
                "group relative p-6 sm:p-8 rounded-[2rem] border border-border/40 bg-card dark:bg-transparent shadow-2xl overflow-hidden transition-all duration-300 hover:border-primary/40 hover:-translate-y-1 flex flex-col justify-between",
                className
              )}
            >
              {/* Mesh background effect for big cells */}
              {className.includes("col-span-2") && (
                <div className="absolute inset-0 opacity-[0.03] grid-pattern pointer-events-none" />
              )}

              <div className="relative z-10 flex-1">
                <div className={cn("w-14 h-14 rounded-2xl bg-gradient-to-br flex items-center justify-center mb-5 group-hover:scale-110 transition-transform duration-500 shadow-inner border border-white/5", gradient)}>
                  <Icon className={cn("w-7 h-7", iconColor)} />
                </div>
                <h3 className={cn("font-bold text-foreground mb-3 tracking-tight", className.includes("md:row-span-2") ? "text-3xl" : "text-xl")}>{title}</h3>
                <p className={cn("text-muted-foreground leading-relaxed", className.includes("md:row-span-2") ? "text-lg max-w-md" : "text-sm")}>{body}</p>
              </div>

              {/* Decorative elements for the master card */}
              {index === 0 && (
                <div className="absolute right-0 bottom-0 pointer-events-none opacity-20 group-hover:opacity-40 transition-opacity duration-700">
                  <BarChart3 className="w-64 h-64 -mb-16 -mr-12" />
                </div>
              )}
            </div>
          ))}
        </div>

        <div className="mt-20 text-center">
          <Link
            href="/register"
            className="inline-flex items-center gap-2 px-8 py-4 rounded-full bg-foreground text-background font-bold hover:scale-105 transition-all duration-300 shadow-xl shadow-foreground/10 group"
          >
            Start building for free
            <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
          </Link>
        </div>
      </div>
    </section>
  );
}
