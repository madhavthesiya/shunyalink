import * as React from "react";
import { cn } from "@/lib/utils";

export function GlassPanel({
  className,
  children,
  padding = "lg",
  ...props
}: React.ComponentProps<"div"> & {
  padding?: "none" | "sm" | "md" | "lg";
}) {
  const paddingClass = {
    none: "",
    sm: "p-4 sm:p-5",
    md: "p-6 sm:p-8",
    lg: "p-8 sm:p-10",
  }[padding];

  return (
    <div
      className={cn(
        "glass-card rounded-3xl shadow-xl shadow-primary/[0.06] ring-1 ring-border/30",
        paddingClass,
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
}
