"use client";

import { useState } from "react";
import { Check, Copy, BarChart3, QrCode, ExternalLink } from "lucide-react";
import { Button } from "@/components/ui/button";

interface ResultBoxProps {
  shortUrl: string;
  shortId: string;
  onViewStats: () => void;
  onGenerateQR: () => void;
}

export function ResultBox({ shortUrl, shortId, onViewStats, onGenerateQR }: ResultBoxProps) {
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
    <div className="animate-in fade-in-0 slide-in-from-bottom-4 duration-500">
      <div className="glass-card rounded-2xl border border-success/20 p-8 space-y-6">
        {/* Success header */}
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-10 h-10 rounded-full bg-gradient-to-br from-success to-success/80 shadow-lg shadow-success/25">
            <Check className="w-5 h-5 text-success-foreground" />
          </div>
          <div>
            <p className="font-semibold text-foreground">Link created successfully</p>
            <p className="text-sm text-muted-foreground">Ready to share with the world</p>
          </div>
        </div>

        {/* Short URL display */}
        <div className="bg-background/50 rounded-xl p-5 border border-border/50">
          <p className="text-xs font-medium text-muted-foreground mb-2 uppercase tracking-wider">Your short link</p>
          <div className="flex items-center gap-3">
            <p className="text-xl sm:text-2xl font-mono font-bold text-foreground break-all flex-1">
              {shortUrl}
            </p>
            <a 
              href={shortUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="flex-shrink-0 p-2 rounded-lg hover:bg-muted transition-colors"
            >
              <ExternalLink className="w-5 h-5 text-muted-foreground hover:text-foreground" />
            </a>
          </div>
        </div>

        {/* Action buttons */}
        <div className="flex flex-col sm:flex-row gap-3">
          <Button
            onClick={handleCopy}
            className={`flex-1 h-12 rounded-xl font-medium transition-all duration-300 ${
              copied 
                ? "bg-success hover:bg-success text-success-foreground" 
                : "bg-primary hover:bg-primary/90 text-primary-foreground btn-glow"
            }`}
          >
            {copied ? (
              <>
                <Check className="w-4 h-4 mr-2" />
                Copied to clipboard
              </>
            ) : (
              <>
                <Copy className="w-4 h-4 mr-2" />
                Copy link
              </>
            )}
          </Button>
          
          <Button
            onClick={onViewStats}
            variant="outline"
            className="flex-1 h-12 rounded-xl font-medium border-border/50 hover:border-primary/30 hover:bg-primary/5 transition-all duration-300"
          >
            <BarChart3 className="w-4 h-4 mr-2" />
            Analytics
          </Button>
          
          <Button
            onClick={onGenerateQR}
            variant="outline"
            className="flex-1 h-12 rounded-xl font-medium border-border/50 hover:border-primary/30 hover:bg-primary/5 transition-all duration-300"
          >
            <QrCode className="w-4 h-4 mr-2" />
            QR Code
          </Button>
        </div>
      </div>
    </div>
  );
}
