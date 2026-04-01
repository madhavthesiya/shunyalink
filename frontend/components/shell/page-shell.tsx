import * as React from "react";
import { cn } from "@/lib/utils";

export function MarketingPageShell({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "min-h-screen flex flex-col bg-background gradient-bg",
        className,
      )}
    >
      {children}
    </div>
  );
}

export function AuthPageShell({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "min-h-screen flex flex-col bg-background gradient-bg",
        className,
      )}
    >
      <main className="flex-1 grid-pattern flex flex-col">{children}</main>
    </div>
  );
}
