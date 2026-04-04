"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { LogIn, UserPlus, LayoutDashboard } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/theme-toggle";
import { cn } from "@/lib/utils";


export function Header() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    setIsLoggedIn(!!localStorage.getItem("authToken"));
    const onScroll = () => setScrolled(window.scrollY > 10);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header
      className={cn(
        "sticky top-0 z-50 border-b transition-all duration-300",
        scrolled
          ? "border-border/60 bg-background/90 backdrop-blur-xl backdrop-saturate-150 shadow-sm"
          : "border-transparent bg-background/60 backdrop-blur-md"
      )}
    >
      <div className="max-w-6xl mx-auto px-4 sm:px-6 h-14 sm:h-16 flex items-center justify-between gap-3 relative">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2.5 min-w-0 group shrink-0">
          <div className="relative flex items-center justify-center w-9 h-9 sm:w-10 sm:h-10 rounded-2xl bg-gradient-to-br from-primary via-primary to-violet-600 shadow-lg shadow-primary/25 overflow-hidden p-1.5 ring-2 ring-background group-hover:shadow-primary/40 transition-shadow">
            <img src="/logo.png" alt="" className="w-full h-full object-contain brightness-0 invert" />
          </div>
          <span className="text-lg sm:text-xl font-semibold tracking-tight text-foreground truncate">
            Shunya<span className="text-gradient">Link</span>
          </span>
        </Link>


        {/* Right: CTA / auth */}
        <nav className="flex items-center gap-1 sm:gap-2">
          <ThemeToggle />

          {!isLoggedIn && (
            <>
              <Button variant="ghost" size="sm" className="rounded-xl" asChild>
                <Link href="/login" className="flex items-center gap-2">
                  <LogIn className="w-4 h-4" />
                  <span className="hidden sm:inline">Login</span>
                </Link>
              </Button>
              <Button size="sm" className="rounded-xl font-semibold btn-glow" asChild>
                <Link href="/register" className="flex items-center gap-2">
                  <UserPlus className="w-4 h-4" />
                  <span>Sign Up</span>
                </Link>
              </Button>
            </>
          )}

          {isLoggedIn && (
            <Button size="sm" className="rounded-xl font-semibold btn-glow" asChild>
              <Link href="/dashboard" className="flex items-center gap-2">
                <LayoutDashboard className="w-4 h-4" />
                Dashboard
              </Link>
            </Button>
          )}
        </nav>
      </div>
    </header>
  );
}
