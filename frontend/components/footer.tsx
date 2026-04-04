"use client";

import { Zap, Database, Server, Heart, Linkedin, Mail, Github, Tag, Search, Image, FileDown, Link2 } from "lucide-react";
import Link from "next/link";

const TECH_STACK = [
  { icon: Server, label: "Spring Boot 3 + Java 21" },
  { icon: Database, label: "PostgreSQL & Flyway" },
  { icon: Zap, label: "Redis rate limiting + caching" },
];

const LINK_FEATURES = [
  "AI metadata scraping (Gemini)",
  "AES-256 password gates",
  "Custom aliases & expiry",
  "UTM builder integration",
  "CSV bulk import & export",
  "Global dashboard search",
];

const IDENTITY_FEATURES = [
  "Link-in-bio profiles",
  "Cloud profile pictures",
  "CP stats aggregator",
  "Geo-IP analytics",
  "Custom theme colors",
  "Drag-and-drop reorder",
];

export function Footer() {
  return (
    <footer className="border-t border-border/60 bg-muted/40 mt-auto">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-14 sm:py-20">

        {/* New-feature badge strip */}
        <div className="flex flex-wrap gap-2 mb-12">
          {[
            { icon: Search, label: "Global Search" },
            { icon: Tag, label: "Custom Tags" },
            { icon: FileDown, label: "CSV Import/Export" },
            { icon: Image, label: "Cloud Profile Picture" },
            { icon: Link2, label: "UTM Builder" },
          ].map(({ icon: Icon, label }) => (
            <span
              key={label}
              className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border border-primary/20 bg-primary/5 text-primary"
            >
              <Icon className="w-3 h-3" />
              {label}
            </span>
          ))}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-10 lg:gap-12 mb-12">

          {/* Brand */}
          <div>
            <div className="flex items-center gap-3 mb-5">
              <div className="flex items-center justify-center w-10 h-10 rounded-2xl bg-gradient-to-br from-primary to-violet-600 shadow-lg shadow-primary/20 overflow-hidden p-2">
                <img src="/logo.png" alt="" className="w-full h-full object-contain brightness-0 invert" />
              </div>
              <span className="text-lg font-bold text-foreground tracking-tight">ShunyaLink</span>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed max-w-xs">
              &quot;Shunya&quot; means zero in Sanskrit — extremely short, minimal, clean links. Built as an enterprise-grade full-stack system for top-tier portfolios.
            </p>
            <div className="flex gap-2 mt-5">
              <a
                href="https://github.com/madhavthesiya/shunyalink"
                target="_blank"
                rel="noopener noreferrer"
                className="p-2 rounded-xl border border-border/60 bg-card hover:bg-muted hover:border-primary/30 transition-all text-muted-foreground hover:text-foreground"
              >
                <Github className="w-4 h-4" />
              </a>
              <a
                href="https://www.linkedin.com/in/madhavthesiya/"
                target="_blank"
                rel="noopener noreferrer"
                className="p-2 rounded-xl border border-border/60 bg-card hover:bg-muted hover:border-primary/30 transition-all text-muted-foreground hover:text-foreground"
              >
                <Linkedin className="w-4 h-4" />
              </a>
              <a
                href="mailto:madhavthesiya07@gmail.com"
                className="p-2 rounded-xl border border-border/60 bg-card hover:bg-muted hover:border-primary/30 transition-all text-muted-foreground hover:text-foreground"
              >
                <Mail className="w-4 h-4" />
              </a>
            </div>
          </div>

          {/* Tech Stack */}
          <div>
            <h4 className="font-semibold text-foreground mb-5 text-sm uppercase tracking-wider text-muted-foreground">
              Tech stack
            </h4>
            <ul className="space-y-3">
              {TECH_STACK.map(({ icon: Icon, label }) => (
                <li key={label} className="flex items-center gap-3 text-sm text-muted-foreground">
                  <span className="w-9 h-9 rounded-xl bg-card border border-border/60 flex items-center justify-center shrink-0">
                    <Icon className="w-4 h-4 text-primary" />
                  </span>
                  {label}
                </li>
              ))}
              <li className="flex items-center gap-3 text-sm text-muted-foreground">
                <span className="w-9 h-9 rounded-xl bg-card border border-border/60 flex items-center justify-center shrink-0">
                  <Image className="w-4 h-4 text-primary" />
                </span>
                Cloudinary CDN
              </li>
              <li className="flex items-center gap-3 text-sm text-muted-foreground">
                <span className="w-9 h-9 rounded-xl bg-card border border-border/60 flex items-center justify-center shrink-0">
                  <Zap className="w-4 h-4 text-primary" />
                </span>
                Nginx load balancer
              </li>
            </ul>
          </div>

          {/* Link Management */}
          <div>
            <h4 className="font-semibold text-foreground mb-5 text-sm uppercase tracking-wider text-muted-foreground">
              Link management
            </h4>
            <ul className="space-y-3 text-sm text-muted-foreground">
              {LINK_FEATURES.map((feature) => (
                <li key={feature} className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-primary shrink-0" />
                  {feature}
                </li>
              ))}
            </ul>
          </div>

          {/* Identity */}
          <div>
            <h4 className="font-semibold text-foreground mb-5 text-sm uppercase tracking-wider text-muted-foreground">
              Identity & analytics
            </h4>
            <ul className="space-y-3 text-sm text-muted-foreground">
              {IDENTITY_FEATURES.map((feature) => (
                <li key={feature} className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-violet-500 shrink-0" />
                  {feature}
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="pt-8 border-t border-border/60 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-sm text-muted-foreground text-center sm:text-left">
            © 2026 ShunyaLink. A full-stack system design project.
          </p>
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <span className="flex items-center gap-1.5">
              Built with
              <Heart className="w-4 h-4 text-rose-500 fill-rose-500" />
              by{" "}
              <Link
                href="https://www.linkedin.com/in/madhavthesiya/"
                target="_blank"
                rel="noopener noreferrer"
                className="font-semibold text-foreground hover:text-primary transition-colors"
              >
                Madhav Thesiya
              </Link>
            </span>
          </div>
        </div>
      </div>
    </footer>
  );
}
