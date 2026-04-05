"use client";

import { useEffect, useState } from "react";
import { cn } from "@/lib/utils";
import { 
  Activity, 
  Database, 
  Zap, 
  ShieldCheck, 
  AlertCircle,
  Clock
} from "lucide-react";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface HealthResponse {
  status: string;
  components?: {
    db?: { status: string };
    redis?: { status: string };
    ping?: { status: string };
  };
}

export function SystemStatus() {
  const [status, setStatus] = useState<"loading" | "up" | "degraded" | "down">("loading");
  const [healthData, setHealthData] = useState<HealthResponse | null>(null);
  const [lastChecked, setLastChecked] = useState<Date>(new Date());

  const checkHealth = async () => {
    try {
      const res = await fetch(`${API_URL}/actuator/health`, {
        cache: "no-store",
      });
      
      setLastChecked(new Date());

      if (res.ok) {
        const data: HealthResponse = await res.json();
        setHealthData(data);
        if (data.status === "UP") {
          setStatus("up");
        } else {
          setStatus("degraded");
        }
      } else {
        setStatus("down");
        setHealthData(null);
      }
    } catch (err) {
      console.error("Health check failed", err);
      setStatus("down");
      setHealthData(null);
      setLastChecked(new Date());
    }
  };

  useEffect(() => {
    checkHealth();
    const interval = setInterval(checkHealth, 30000); // Check every 30s
    return () => clearInterval(interval);
  }, []);

  const getStatusColor = () => {
    switch (status) {
      case "up": return "bg-emerald-500 shadow-emerald-500/50";
      case "degraded": return "bg-amber-500 shadow-amber-500/50";
      case "down": return "bg-red-500 shadow-red-500/50";
      default: return "bg-blue-400 animate-pulse";
    }
  };

  const getStatusLabel = () => {
    switch (status) {
      case "up": return "All systems operational";
      case "degraded": return "Degraded performance";
      case "down": return "Service disruption";
      default: return "Checking system status...";
    }
  };

  return (
    <TooltipProvider>
      <div className="fixed bottom-6 left-6 z-[60] group pointer-events-auto">
        <Tooltip>
          <TooltipTrigger asChild>
            <div className="flex items-center gap-2.5 px-4 py-2 rounded-full glass-card border border-border/40 shadow-xl hover:border-primary/40 transition-all duration-300 cursor-default group">
              <div className="relative flex items-center justify-center">
                {/* Heartbeat Animation */}
                {status === "up" && (
                  <span className="absolute inset-0 rounded-full bg-emerald-500/40 animate-ping opacity-75" />
                )}
                <div className={cn("w-2.5 h-2.5 rounded-full relative z-10 shadow-lg", getStatusColor())} />
              </div>
              <span className="text-[11px] font-semibold tracking-wide text-foreground/80 uppercase">
                Status: <span className={cn(
                  status === "up" ? "text-emerald-500" : 
                  status === "down" ? "text-red-500" : "text-amber-500"
                )}>{status === "loading" ? "..." : status}</span>
              </span>
            </div>
          </TooltipTrigger>
          <TooltipContent 
            side="top" 
            align="start" 
            className="p-4 w-64 glass-card border-border/50 shadow-2xl rounded-2xl animate-in fade-in zoom-in-95 duration-200"
          >
            <div className="space-y-4">
              <div className="flex items-center justify-between border-b border-border/50 pb-2">
                <div className="flex items-center gap-2">
                  <Activity className="w-4 h-4 text-primary" />
                  <span className="font-bold text-sm text-foreground">System Health</span>
                </div>
                <div className="flex items-center gap-1 text-[10px] text-muted-foreground">
                  <Clock className="w-3 h-3" />
                  {lastChecked.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
              </div>

              <div className="space-y-2.5">
                {/* Database Health */}
                <div className="flex items-center justify-between text-xs p-2.5 rounded-xl bg-muted/50 border border-border/20">
                  <div className="flex items-center gap-2">
                    <Database className="w-3.5 h-3.5 text-primary/70" />
                    <span className="text-foreground/90 font-medium">PostgreSQL Database</span>
                  </div>
                  {healthData?.components?.db?.status === "UP" ? (
                    <ShieldCheck className="w-4 h-4 text-emerald-500" />
                  ) : (
                    <AlertCircle className="w-4 h-4 text-red-500" />
                  )}
                </div>

                {/* Redis Health */}
                <div className="flex items-center justify-between text-xs p-2.5 rounded-xl bg-muted/50 border border-border/20">
                  <div className="flex items-center gap-2">
                    <Zap className="w-3.5 h-3.5 text-amber-500/70" />
                    <span className="text-foreground/90 font-medium">Redis Cache</span>
                  </div>
                  {healthData?.components?.redis?.status === "UP" ? (
                    <ShieldCheck className="w-4 h-4 text-emerald-500" />
                  ) : (
                    <AlertCircle className="w-4 h-4 text-red-500" />
                  )}
                </div>
              </div>

              <p className={cn(
                "text-[10px] font-bold text-center py-1.5 rounded-lg border",
                status === "up" ? "bg-emerald-500/10 text-emerald-500 border-emerald-500/20" :
                "bg-red-500/10 text-red-500 border-red-500/20"
              )}>
                {getStatusLabel().toUpperCase()}
              </p>
            </div>
          </TooltipContent>
        </Tooltip>
      </div>
    </TooltipProvider>
  );
}
