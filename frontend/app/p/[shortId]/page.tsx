"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Lock, ArrowRight, Loader2, AlertCircle, Eye, EyeOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default function PasswordPage() {
  const { shortId } = useParams();
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!password.trim()) return;
    
    setIsLoading(true);
    setError("");

    try {
      const response = await fetch(`${API_URL}/api/v1/url/resolve/${shortId}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password }),
      });

      if (response.ok) {
        const data = await response.json();
        // Redirect to the actual destination
        window.location.href = data.longUrl;
      } else {
        const errorData = await response.json().catch(() => ({}));
        setError(typeof errorData === 'string' ? errorData : "Incorrect password. Please try again.");
      }
    } catch (err) {
      setError("Failed to verify password. Please check your connection.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background gradient-bg px-4 py-12 relative overflow-hidden">
        {/* Background Decorative Elements */}
        <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-primary/5 blur-[120px] rounded-full -mr-64 -mt-64" />
        <div className="absolute bottom-0 left-0 w-[500px] h-[500px] bg-primary/5 blur-[120px] rounded-full -ml-64 -mb-64" />

      <div className="glass-card max-w-md w-full p-8 sm:p-10 rounded-2xl shadow-2xl relative z-10 border border-primary/10 animate-in fade-in zoom-in duration-500">
        <div className="w-20 h-20 bg-gradient-to-br from-primary/20 to-primary/5 rounded-2xl flex items-center justify-center mx-auto mb-8 shadow-inner shadow-primary/10 border border-primary/20 transform -rotate-6">
          <Lock className="w-10 h-10 text-primary drop-shadow-sm" />
        </div>
        
        <div className="text-center space-y-3 mb-10">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">Link Protected</h1>
            <p className="text-muted-foreground text-sm leading-relaxed max-w-[280px] mx-auto">
                This link is encrypted for security. Please enter the password to proceed.
            </p>
        </div>
        
        <form onSubmit={handleUnlock} className="space-y-6">
          <div className="space-y-2 relative">
            <Input 
                id="password"
                type={showPassword ? "text" : "password"}
                placeholder="Enter password" 
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="h-14 text-center text-lg bg-background/50 border-border/50 rounded-xl premium-input focus:border-primary/50 focus:ring-2 focus:ring-primary/10 transition-all font-mono tracking-widest pr-12"
                autoFocus
                disabled={isLoading}
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-4 top-[28px] -translate-y-1/2 p-2 rounded-lg text-muted-foreground hover:bg-primary/10 hover:text-primary transition-all transition-colors"
            >
              {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
            </button>
          </div>

          {error && (
            <div className="flex items-center gap-2 p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs animate-in shake duration-300">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{error}</span>
            </div>
          )}

          <Button 
            type="submit" 
            className="w-full h-14 text-base font-semibold rounded-xl bg-gradient-to-r from-primary to-primary/90 hover:from-primary/90 hover:to-primary/80 text-primary-foreground btn-glow transition-all duration-300 shadow-lg shadow-primary/20" 
            disabled={isLoading || !password.trim()}
          >
            {isLoading ? (
                <>
                    <Loader2 className="w-5 h-5 mr-3 animate-spin shadow-primary/20" />
                    Unlocking...
                </>
            ) : (
                <>
                    Unlock Link
                    <ArrowRight className="w-5 h-5 ml-3 group-hover:translate-x-1 transition-transform" />
                </>
            )}
          </Button>
        </form>

        <div className="mt-10 pt-6 border-t border-primary/5 text-center space-y-2">
            <p className="text-[10px] uppercase tracking-widest text-muted-foreground/40 font-semibold">
                Securely powered by <span className="text-primary/60">ShunyaLink</span>
            </p>
            <a 
                href="/" 
                className="inline-block text-xs font-medium text-primary/70 hover:text-primary transition-all hover:scale-105 active:scale-95"
            >
                Create your own password protected link →
            </a>
        </div>
      </div>
    </div>
  );
}
