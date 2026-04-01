import * as React from "react";
import { Header } from "@/components/header";
import { AuthPageShell } from "@/components/shell/page-shell";
import { GlassPanel } from "@/components/shell/glass-panel";
import { cn } from "@/lib/utils";

export function AuthPageLayout({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <AuthPageShell>
      <Header />
      <div
        className={cn(
          "flex-1 flex items-center justify-center px-4 sm:px-6 py-12 sm:py-16",
          className,
        )}
      >
        {children}
      </div>
    </AuthPageShell>
  );
}

export function AuthCard({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("w-full max-w-md mx-auto", className)}>
      <GlassPanel
        padding="lg"
        className="shadow-2xl shadow-primary/[0.08] animate-in fade-in-0 slide-in-from-bottom-4 duration-700 flex flex-col gap-8 border-border/50"
      >
        {children}
      </GlassPanel>
    </div>
  );
}
