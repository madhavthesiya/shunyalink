"use client";

import { Link2, Github } from "lucide-react";
import { Button } from "@/components/ui/button";

export function Header() {
  return (
    <header className="glass-card sticky top-0 z-50 border-b border-border/50">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="relative flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-primary/80 shadow-lg shadow-primary/25">
            <Link2 className="w-4 h-4 text-primary-foreground" />
            <div className="absolute inset-0 rounded-xl bg-primary/20 blur-md -z-10" />
          </div>
          <span className="text-xl font-semibold tracking-tight text-foreground">
            Shunya<span className="text-gradient">Link</span>
          </span>
        </div>
        
        <nav className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            className="text-muted-foreground hover:text-foreground"
            asChild
          >
            <a
              href="https://github.com/madhavthesiya/shunyalink"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2"
            >
              <Github className="w-4 h-4" />
              <span className="hidden sm:inline">GitHub</span>
            </a>
          </Button>
        </nav>
      </div>
    </header>
  );
}
