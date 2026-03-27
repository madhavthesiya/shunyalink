"use client";

import { useState, useEffect, type FormEvent } from "react";
import { ArrowRight, Loader2, Link, Hash, Calendar, Eye, EyeOff, Lock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
interface ShortenResponse {
  shortId: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
  passwordProtected: boolean;
}

interface ShortenerFormProps {
  onSuccess: (data: ShortenResponse) => void;
  onError: (message: string) => void;
}

function resolveErrorMessage(status: number, serverMessage?: string): string {
  if (serverMessage) return serverMessage;
  switch (status) {
    case 400: return "Invalid URL or request. Please check your input.";
    case 409: return "That custom alias is already taken. Try a different one.";
    case 429: return "Too many requests. Please wait a moment and try again.";
    case 500: return "Server error. Please try again in a few seconds.";
    default:  return "Something went wrong. Please try again.";
  }
}

export function ShortenerForm({ onSuccess, onError }: ShortenerFormProps) {
  const [longUrl, setLongUrl] = useState("");
  const [customAlias, setCustomAlias] = useState("");
  const [expiryDays, setExpiryDays] = useState("");
  const [title, setTitle] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordInput, setShowPasswordInput] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    setIsLoggedIn(!!localStorage.getItem("authToken"));
  }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    
    if (!longUrl.trim()) {
      onError("Please enter a URL to shorten");
      return;
    }

    setIsLoading(true);

    try {
      const headers: Record<string, string> = {
        "Content-Type": "application/json",
      };

      // Include auth token if user is logged in
      const token = localStorage.getItem("authToken");
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }

      const response = await fetch(`${API_URL}/api/v1/url/shorten`, {
        method: "POST",
        headers,
        body: JSON.stringify({
          longUrl: longUrl.trim(),
          ...(customAlias.trim() && { customAlias: customAlias.trim() }),
          ...(expiryDays && { expiryDays: parseInt(expiryDays, 10) }),
          ...(title.trim() && { title: title.trim() }),
          ...(password.trim() && { password: password.trim() }),
        }),
      });

      const data = await response.json();

      if (!response.ok) {
      onError(resolveErrorMessage(response.status, data?.message));
      return;
      }

      onSuccess(data);
      setLongUrl("");
      setCustomAlias("");
      setExpiryDays("");
      setTitle("");
      setPassword("");
      setShowPasswordInput(false);
    } catch (err) {
      if (err instanceof TypeError && err.message.includes("fetch")) {
        onError("Cannot reach the server. Make sure the backend is running.");
      } else {
          onError(err instanceof Error ? err.message : "Something went wrong");
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-8">
      {/* Main URL Input */}
      <div className="space-y-3">
        <Label htmlFor="longUrl" className="flex items-center gap-2 text-sm font-medium text-foreground">
          <Link className="w-4 h-4 text-primary" />
          Paste your long URL
        </Label>
        <div className="relative">
          <Input
            id="longUrl"
            type="text"
            placeholder="https://example.com/very/long/url/that/needs/shortening"
            value={longUrl}
            onChange={(e) => setLongUrl(e.target.value)}
            className="h-14 text-base pl-4 pr-4 bg-background/50 border-border/50 rounded-xl premium-input focus:border-primary/50 focus:ring-2 focus:ring-primary/10 transition-all placeholder:text-muted-foreground/50"
            disabled={isLoading}
            required
          />
        </div>
      </div>

      <div className="space-y-3">
        <Label htmlFor="title" className="flex items-center gap-2 text-sm font-medium text-foreground">
          <Hash className="w-4 h-4 text-primary" />
          Link Title (e.g. My Portfolio)
          {!isLoggedIn && (
            <span className="text-[10px] bg-amber-500/10 text-amber-500 px-2 py-0.5 rounded-full border border-amber-500/20 font-bold ml-auto animate-pulse">
              Login Required
            </span>
          )}
          {isLoggedIn && <span className="text-xs text-muted-foreground/60 font-normal ml-auto">Shown on Bio Profile</span>}
        </Label>
        <div className="relative group">
          <Input
            id="title"
            type="text"
            placeholder={isLoggedIn ? "Enter a friendly name for this link" : "🔐 Login first to edit this field"}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className={`h-12 bg-background/50 border-border/50 rounded-xl premium-input focus:border-primary/50 focus:ring-2 focus:ring-primary/10 transition-all placeholder:text-muted-foreground/50 ${!isLoggedIn && "opacity-60 cursor-not-allowed"}`}
            disabled={isLoading || !isLoggedIn}
          />
          {!isLoggedIn && (
            <div className="absolute inset-0 z-10 cursor-not-allowed" title="Login required for premium features" />
          )}
        </div>
      </div>

      {/* Optional Fields */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        <div className="space-y-3">
          <Label htmlFor="customAlias" className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Hash className="w-4 h-4 text-muted-foreground" />
            Custom alias
            <span className="text-xs text-muted-foreground/60 font-normal ml-auto">Optional</span>
          </Label>
          <Input
            id="customAlias"
            type="text"
            placeholder="my-brand or mybrand"
            value={customAlias}
            onChange={(e) => setCustomAlias(e.target.value)}
            className="h-12 bg-background/50 border-border/50 rounded-xl premium-input focus:border-primary/50 focus:ring-2 focus:ring-primary/10 transition-all placeholder:text-muted-foreground/50"
            disabled={isLoading}
          />
        </div>

        <div className="space-y-3">
          <Label htmlFor="expiryDays" className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Calendar className="w-4 h-4 text-muted-foreground" />
            Expires after
            <span className="text-xs text-muted-foreground/60 font-normal ml-auto">Optional</span>
          </Label>
          <Input
            id="expiryDays"
            type="number"
            min="1"
            placeholder="7 days"
            value={expiryDays}
            onChange={(e) => setExpiryDays(e.target.value)}
            className="h-12 bg-background/50 border-border/50 rounded-xl premium-input focus:border-primary/50 focus:ring-2 focus:ring-primary/10 transition-all placeholder:text-muted-foreground/50"
            disabled={isLoading}
          />
        </div>
      </div>

      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <Label htmlFor="password-toggle" className="text-sm font-medium text-foreground cursor-pointer">
            Lock with Password
            <p className="text-[10px] text-muted-foreground/60 font-normal">Require a password to access this link</p>
          </Label>
          <input 
            type="checkbox" 
            id="password-toggle"
            checked={showPasswordInput}
            onChange={(e) => setShowPasswordInput(e.target.checked)}
            className="w-4 h-4 rounded border-border/50 bg-background/50 accent-primary cursor-pointer"
            disabled={isLoading}
          />
        </div>
        {showPasswordInput && (
          <div className="animate-in fade-in slide-in-from-top-2 duration-300 relative">
            <Input
              type={showPassword ? "text" : "password"}
              placeholder="Enter link password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="h-12 bg-background/50 border-border/50 rounded-xl premium-input focus:border-primary/50 focus:ring-2 focus:ring-primary/10 transition-all pr-12"
              disabled={isLoading}
              required={showPasswordInput}
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 rounded-lg text-muted-foreground hover:bg-primary/10 hover:text-primary transition-all transition-colors"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        )}
      </div>

      {/* Submit Button */}
      <Button
        type="submit"
        disabled={isLoading}
        className="w-full h-14 text-base font-semibold rounded-xl bg-gradient-to-r from-primary to-primary/90 hover:from-primary/90 hover:to-primary/80 text-primary-foreground btn-glow transition-all duration-300"
      >
        {isLoading ? (
          <>
            <Loader2 className="w-5 h-5 mr-2 animate-spin" />
            Shortening...
          </>
        ) : (
          <>
            Shorten URL
            <ArrowRight className="w-5 h-5 ml-2 group-hover:translate-x-1 transition-transform" />
          </>
        )}
      </Button>

      {/* Keyboard hint */}
      <p className="text-xs text-center text-muted-foreground/60">
        Press <kbd className="px-1.5 py-0.5 rounded bg-muted text-muted-foreground text-xs font-mono">Enter</kbd> to shorten
      </p>
    </form>
  );
}
