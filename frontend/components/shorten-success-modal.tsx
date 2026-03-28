"use client";

import { useState, useEffect } from "react";
import { Check, Copy, BarChart3, QrCode, ExternalLink, Sparkles } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface ShortenSuccessModalProps {
  isOpen: boolean;
  onClose: () => void;
  shortId: string;
  shortUrl: string;
  onViewStats: () => void;
  onGenerateQR: () => void;
}

export function ShortenSuccessModal({
  isOpen,
  onClose,
  shortId,
  shortUrl,
  onViewStats,
  onGenerateQR,
}: ShortenSuccessModalProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(shortUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error("Failed to copy:", err);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md border-none bg-transparent shadow-none p-0 overflow-visible">
        <div className="relative">
          {/* Celebratory Particles (CSS Only) */}
          {isOpen && (
            <div className="absolute inset-0 pointer-events-none -z-10">
              {[...Array(12)].map((_, i) => (
                <div
                  key={i}
                  className="absolute animate-celebrate opacity-0"
                  style={{
                    left: "50%",
                    top: "50%",
                    backgroundColor: i % 2 === 0 ? "var(--primary)" : "var(--success)",
                    width: "8px",
                    height: "8px",
                    borderRadius: i % 3 === 0 ? "50%" : "2px",
                    transform: `rotate(${i * 30}deg) translateY(-100px)`,
                    animationDelay: `${i * 0.1}s`,
                  }}
                />
              ))}
            </div>
          )}

          <div className="glass-card rounded-3xl border border-primary/20 p-8 shadow-2xl shadow-primary/20 animate-in zoom-in-95 duration-300">
            <DialogHeader className="items-center text-center space-y-4">
              <div className="relative">
                <div className="absolute inset-0 bg-success blur-xl opacity-20 animate-pulse" />
                <div className="relative flex items-center justify-center w-20 h-20 rounded-full bg-gradient-to-br from-success to-success/80 shadow-lg shadow-success/25 mb-2 scale-110 animate-in bounce-in duration-500">
                  <Check className="w-10 h-10 text-success-foreground" />
                </div>
              </div>
              
              <div>
                <DialogTitle className="text-3xl font-bold text-foreground flex items-center justify-center gap-2">
                  Excellent! <Sparkles className="w-6 h-6 text-amber-500 animate-pulse" />
                </DialogTitle>
                <DialogDescription className="text-base text-muted-foreground mt-2 font-medium">
                  Your short link is ready to share with the world.
                </DialogDescription>
              </div>
            </DialogHeader>

            <div className="mt-10 space-y-8">
              {/* Short URL Box */}
              <div className="relative group">
                <div className="absolute inset-0 bg-primary/5 rounded-2xl blur-md opacity-0 group-hover:opacity-100 transition-opacity" />
                <div className="relative bg-background/50 rounded-2xl p-6 border border-border/50 backdrop-blur-sm">
                  <p className="text-xs font-bold text-primary mb-3 uppercase tracking-widest">Your unique link</p>
                  <div className="flex items-center gap-4">
                    <p className="text-2xl font-mono font-bold text-foreground break-all flex-1 tracking-tight">
                      {shortUrl.replace(/^https?:\/\//, "")}
                    </p>
                    <a 
                      href={shortUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex-shrink-0 p-3 rounded-xl hover:bg-primary/10 text-muted-foreground hover:text-primary transition-all duration-300 shadow-sm"
                    >
                      <ExternalLink className="w-6 h-6" />
                    </a>
                  </div>
                </div>
              </div>

              {/* Action Grid */}
              <div className="grid grid-cols-1 gap-3">
                <Button
                  onClick={handleCopy}
                  size="lg"
                  className={cn(
                    "w-full h-16 rounded-2xl text-lg font-bold transition-all duration-500 shadow-xl",
                    copied 
                      ? "bg-success hover:bg-success text-success-foreground scale-95" 
                      : "bg-primary hover:bg-primary/90 text-primary-foreground btn-glow hover:scale-[1.02] active:scale-95"
                  )}
                >
                  {copied ? (
                    <>
                      <Check className="w-6 h-6 mr-3 stroke-[3]" />
                      Copied!
                    </>
                  ) : (
                    <>
                      <Copy className="w-6 h-6 mr-3" />
                      Copy Short Link
                    </>
                  )}
                </Button>
                
                <div className="grid grid-cols-2 gap-3">
                  <Button
                    onClick={() => {
                      onViewStats();
                      onClose();
                    }}
                    variant="outline"
                    className="h-14 rounded-2xl font-bold border-border/50 hover:border-primary/30 hover:bg-primary/5 transition-all duration-300"
                  >
                    <BarChart3 className="w-5 h-5 mr-2 text-primary" />
                    Analytics
                  </Button>
                  
                  <Button
                    onClick={() => {
                      onGenerateQR();
                      onClose();
                    }}
                    variant="outline"
                    className="h-14 rounded-2xl font-bold border-border/50 hover:border-primary/30 hover:bg-primary/5 transition-all duration-300"
                  >
                    <QrCode className="w-5 h-5 mr-2 text-primary" />
                    QR Code
                  </Button>
                </div>
              </div>
            </div>

            <div className="mt-8 text-center px-4">
              <button 
                onClick={onClose}
                className="text-sm font-bold text-muted-foreground hover:text-foreground transition-colors py-2"
              >
                Close and return to dashboard
              </button>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
