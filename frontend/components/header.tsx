"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Link2, Github, LogIn, UserPlus } from "lucide-react";
import { Button } from "@/components/ui/button";

export function Header() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    setIsLoggedIn(!!localStorage.getItem("authToken"));
  }, []);

  return (
    <header className="glass-card sticky top-0 z-50 border-b border-border/50">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-3">
          <div className="relative flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-primary/80 shadow-lg shadow-primary/25 overflow-hidden p-1.5">
            <img src="/logo.png" alt="Logo" className="w-full h-full object-contain brightness-0 invert" />
            <div className="absolute inset-0 rounded-xl bg-primary/20 blur-md -z-10" />
          </div>
          <span className="text-xl font-semibold tracking-tight text-foreground">
            Shunya<span className="text-gradient">Link</span>
          </span>
        </Link>
        
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

          {!isLoggedIn && (
            <>
              <Button
                variant="ghost"
                size="sm"
                className="text-muted-foreground hover:text-foreground"
                asChild
              >
                <Link href="/login" className="flex items-center gap-2">
                  <LogIn className="w-4 h-4" />
                  <span className="hidden sm:inline">Login</span>
                </Link>
              </Button>
              <Button
                size="sm"
                className="bg-gradient-to-r from-primary to-primary/90 hover:from-primary/90 hover:to-primary/80 text-primary-foreground btn-glow"
                asChild
              >
                <Link href="/register" className="flex items-center gap-2">
                  <UserPlus className="w-4 h-4" />
                  <span>Sign Up</span>
                </Link>
              </Button>
            </>
          )}

          {isLoggedIn && (
            <Button
              size="sm"
              className="bg-gradient-to-r from-primary to-primary/90 hover:from-primary/90 hover:to-primary/80 text-primary-foreground btn-glow"
              asChild
            >
              <Link href="/dashboard">Dashboard</Link>
            </Button>
          )}
        </nav>
      </div>
    </header>
  );
}
