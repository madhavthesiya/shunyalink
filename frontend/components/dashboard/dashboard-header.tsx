import Link from "next/link";
import { LogOut, Smartphone } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/theme-toggle";
import { cn } from "@/lib/utils";

export function DashboardHeader({
  userName,
  totalUrls,
  onViewProfile,
  onLogout,
}: {
  userName: string;
  totalUrls: number;
  onViewProfile: () => void;
  onLogout: () => void;
}) {
  return (
    <header
      className={cn(
        "sticky top-0 z-50 border-b border-border/60",
        "nav-blur shadow-[0_1px_0_0_rgba(15,23,42,0.04)]",
        "dark:shadow-[0_1px_0_0_rgba(255,255,255,0.06)]",
      )}
    >
      <div className="max-w-6xl mx-auto px-4 sm:px-6 h-14 sm:h-16 flex items-center justify-between gap-2">
        <Link
          href="/"
          className="flex items-center gap-3 hover:opacity-90 transition-opacity min-w-0 group"
        >
          <div className="relative flex items-center justify-center w-9 h-9 sm:w-10 sm:h-10 rounded-2xl bg-gradient-to-br from-primary via-primary to-violet-600 shadow-lg shadow-primary/25 overflow-hidden p-1.5 shrink-0 ring-2 ring-background">
            <img
              src="/logo.png"
              alt=""
              className="w-full h-full object-contain brightness-0 invert"
            />
          </div>
          <span className="text-lg sm:text-xl font-semibold tracking-tight text-foreground truncate">
            Shunya<span className="text-gradient">Link</span>
          </span>
        </Link>

        <div className="flex items-center gap-1.5 sm:gap-3 shrink-0">
          <ThemeToggle />
          <div className="text-right hidden sm:block pr-1 border-r border-border/60 mr-1">
            <p className="text-sm font-medium text-foreground leading-tight">{userName}</p>
            <p className="text-xs text-muted-foreground">
              {totalUrls} link{totalUrls !== 1 ? "s" : ""}
            </p>
          </div>

          <Button
            variant="outline"
            size="sm"
            className="hidden lg:flex rounded-xl border-border/80"
            onClick={onViewProfile}
          >
            <Smartphone className="w-4 h-4 mr-2" />
            View Profile
          </Button>

          <Button
            onClick={onLogout}
            variant="ghost"
            size="sm"
            className="rounded-xl text-muted-foreground hover:text-foreground"
          >
            <LogOut className="w-4 h-4 sm:mr-2" />
            <span className="hidden sm:inline">Logout</span>
          </Button>
        </div>
      </div>
    </header>
  );
}
