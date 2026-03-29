"use client";

import { Link2, Zap, Database, Server, Heart, Linkedin, Mail } from "lucide-react";

export function Footer() {
  return (
    <footer className="border-t border-border/50 bg-card/30 mt-auto">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-16">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-12 mb-12">
          {/* Brand */}
          <div>
            <div className="flex items-center gap-2.5 mb-5">
              <div className="flex items-center justify-center w-8 h-8 rounded-full bg-primary shadow-md overflow-hidden p-1.5">
                <img src="/logo.png" alt="Logo" className="w-full h-full object-contain brightness-0 invert" />
              </div>
              <span className="text-xl font-bold text-foreground tracking-tight">
                ShunyaLink
              </span>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed max-w-xs">
              {"\"Shunya\" means zero in Sanskrit — representing extremely short, minimal, and clean links."}
            </p>
          </div>

          {/* Tech Stack */}
          <div>
            <h4 className="font-semibold text-foreground mb-6">Tech Stack</h4>
            <ul className="space-y-4">
              <li className="flex items-center gap-3 text-sm text-muted-foreground/80 group">
                <div className="w-9 h-9 rounded-xl bg-muted/30 border border-border/50 flex items-center justify-center group-hover:bg-primary/5 transition-all">
                  <Server className="w-4 h-4 text-muted-foreground group-hover:text-primary transition-colors" />
                </div>
                <span className="group-hover:text-foreground transition-colors">Spring Boot Backend</span>
              </li>
              <li className="flex items-center gap-3 text-sm text-muted-foreground/80 group">
                <div className="w-9 h-9 rounded-xl bg-muted/30 border border-border/50 flex items-center justify-center group-hover:bg-primary/5 transition-all">
                  <Database className="w-4 h-4 text-muted-foreground group-hover:text-primary transition-colors" />
                </div>
                <span className="group-hover:text-foreground transition-colors">PostgreSQL Database</span>
              </li>
              <li className="flex items-center gap-3 text-sm text-muted-foreground/80 group">
                <div className="w-9 h-9 rounded-xl bg-muted/30 border border-border/50 flex items-center justify-center group-hover:bg-primary/5 transition-all">
                  <Zap className="w-4 h-4 text-muted-foreground group-hover:text-primary transition-colors" />
                </div>
                <span className="group-hover:text-foreground transition-colors">Redis Caching</span>
              </li>
            </ul>
          </div>

          {/* Features */}
          <div>
            <h4 className="font-semibold text-foreground mb-6">Link Management</h4>
            <ul className="space-y-4 text-sm text-muted-foreground/80">
              {[
                'AI Metadata Scraping',
                'AES-256 Encryption',
                'Link expiration settings',
                'Custom short aliases'
              ].map((feature) => (
                <li key={feature} className="flex items-center gap-3 group cursor-default">
                  <span className="w-1.5 h-1.5 rounded-full bg-primary ring-4 ring-primary/10" />
                  <span className="group-hover:text-foreground transition-colors">{feature}</span>
                </li>
              ))}
            </ul>
          </div>

          {/* Identity Features */}
          <div>
            <h4 className="font-semibold text-foreground mb-6">Identity Features</h4>
            <ul className="space-y-4 text-sm text-muted-foreground/80">
              {[
                'Link-in-Bio profiles',
                'Geo-IP Distribution',
                'Time-Series Analytics',
                'Secure Revocation'
              ].map((feature) => (
                <li key={feature} className="flex items-center gap-3 group cursor-default">
                  <span className="w-1.5 h-1.5 rounded-full bg-primary ring-4 ring-primary/10" />
                  <span className="group-hover:text-foreground transition-colors">{feature}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="pt-8 border-t border-border/50 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-sm text-muted-foreground">
            {'© 2026 ShunyaLink. A full-stack system design project.'}
          </p>
          <div className="flex items-center gap-3 text-sm text-muted-foreground">
            <span className="flex items-center gap-1">
              Built with
              <Heart className="w-4 h-4 mx-1 text-destructive fill-destructive" />
              by <span className="font-semibold text-foreground ml-1">Madhav Thesiya</span>
            </span>
            <a
              href="https://www.linkedin.com/in/madhavthesiya/"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-primary transition-colors"
              title="LinkedIn"
            >
              <Linkedin className="w-4 h-4" />
            </a>
            <a
              href="mailto:madhavthesiya07@gmail.com"
              className="hover:text-primary transition-colors"
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