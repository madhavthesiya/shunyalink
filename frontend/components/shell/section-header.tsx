import * as React from "react";
import { cn } from "@/lib/utils";

export function SectionHeader({
  badge,
  title,
  description,
  className,
  align = "center",
}: {
  badge?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  className?: string;
  align?: "center" | "left";
}) {
  return (
    <div
      className={cn(
        "mb-10 sm:mb-16",
        align === "center" && "text-center",
        className,
      )}
    >
      {badge != null && (
        <div
          className={cn(
            "mb-4",
            align === "center" && "flex justify-center",
          )}
        >
          {badge}
        </div>
      )}
      <h2
        className={cn(
          "text-3xl sm:text-5xl font-bold text-foreground mb-4 text-balance",
          align === "center" && "mx-auto max-w-3xl",
        )}
      >
        {title}
      </h2>
      {description != null && (
        <p
          className={cn(
            "text-muted-foreground max-w-2xl text-lg leading-relaxed",
            align === "center" && "mx-auto",
          )}
        >
          {description}
        </p>
      )}
    </div>
  );
}
