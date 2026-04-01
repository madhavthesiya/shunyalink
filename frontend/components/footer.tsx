"use client";

import { Zap, Database, Server, Heart, Linkedin, Mail } from "lucide-react";

export function Footer() {
  return (
    <footer className="border-t border-border/60 bg-muted/40 mt-auto">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-14 sm:py-20">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-10 lg:gap-12 mb-12">
          <div>
            <div className="flex items-center gap-3 mb-5">
              <div className="flex items-center justify-center w-10 h-10 rounded-2xl bg-gradient-to-br from-primary to-violet-600 shadow-lg shadow-primary/20 overflow-hidden p-2">
                <img
                  src="/logo.png"
                  alt=""
                  className="w-full h-full object-contain brightness-0 invert"
                />
              </div>
              <span className="text-lg font-bold text-foreground tracking-tight">
                ShunyaLink
              </span>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed max-w-xs">
              &quot;Shunya&quot; means zero in Sanskrit — extremely short, minimal, clean links.
            </p>
          </div>

          <div>
            <h4 className="font-semibold text-foreground mb-5 text-sm uppercase tracking-wider text-muted-foreground">
              Tech stack
            </h4>
            <ul className="space-y-3">
              {[
                { icon: Server, label: "Spring Boot backend" },
                { icon: Database, label: "PostgreSQL" },
                { icon: Zap, label: "Redis caching" },
              ].map(({ icon: Icon, label }) => (
                <li key={label} className="flex items-center gap-3 text-sm text-muted-foreground">
                  <span className="w-9 h-9 rounded-xl bg-card border border-border/60 flex items-center justify-center">
                    <Icon className="w-4 h-4 text-primary" />
                  </span>
                  {label}
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="font-semibold text-foreground mb-5 text-sm uppercase tracking-wider text-muted-foreground">
              Link management
            </h4>
            <ul className="space-y-3 text-sm text-muted-foreground">
              {[
                "AI metadata scraping",
                "AES-256 encryption",
                "Link expiration",
                "Custom aliases",
              ].map((feature) => (
                <li key={feature} className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-primary shrink-0" />
                  {feature}
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 className="font-semibold text-foreground mb-5 text-sm uppercase tracking-wider text-muted-foreground">
              Identity
            </h4>
            <ul className="space-y-3 text-sm text-muted-foreground">
              {[
                "Link-in-bio profiles",
                "Geo-IP distribution",
                "Time-series analytics",
                "Secure revocation",
              ].map((feature) => (
                <li key={feature} className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-violet-500 shrink-0" />
                  {feature}
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="pt-8 border-t border-border/60 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-sm text-muted-foreground text-center sm:text-left">
            © 2026 ShunyaLink. A full-stack system design project.
          </p>
          <div className="flex items-center gap-4 text-sm text-muted-foreground">
            <span className="flex items-center gap-1.5">
              Built with
              <Heart className="w-4 h-4 text-rose-500 fill-rose-500" />
              by <span className="font-semibold text-foreground">Madhav Thesiya</span>
            </span>
            <a
              href="https://www.linkedin.com/in/madhavthesiya/"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-primary transition-colors p-1"
              title="LinkedIn"
            >
              <Linkedin className="w-4 h-4" />
            </a>
            <a
              href="mailto:madhavthesiya07@gmail.com"
              className="hover:text-primary transition-colors p-1"
              title="Email"
            >
              <Mail className="w-4 h-4" />
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
