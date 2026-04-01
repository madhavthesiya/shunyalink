import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function DashboardTabBar({
  activeTab,
  onTabChange,
}: {
  activeTab: "links" | "settings";
  onTabChange: (tab: "links" | "settings") => void;
}) {
  return (
    <div
      className="inline-flex flex-wrap items-center gap-1 p-1.5 rounded-2xl bg-muted/80 border border-border/60 shadow-inner w-full sm:w-auto"
      role="tablist"
      aria-label="Dashboard sections"
    >
      <Button
        type="button"
        role="tab"
        aria-selected={activeTab === "links"}
        variant="ghost"
        onClick={() => onTabChange("links")}
        className={cn(
          "rounded-xl px-5 sm:px-7 flex-1 sm:flex-initial font-medium transition-all",
          activeTab === "links" &&
            "bg-card text-foreground shadow-sm ring-1 ring-border/60",
        )}
      >
        Your links
      </Button>
      <Button
        type="button"
        role="tab"
        aria-selected={activeTab === "settings"}
        variant="ghost"
        onClick={() => onTabChange("settings")}
        className={cn(
          "rounded-xl px-5 sm:px-7 flex-1 sm:flex-initial font-medium transition-all",
          activeTab === "settings" &&
            "bg-card text-foreground shadow-sm ring-1 ring-border/60",
        )}
      >
        Profile settings
      </Button>
    </div>
  );
}
