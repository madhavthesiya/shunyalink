"use client";

import { useEffect, useState } from "react";
import { X, Download, QrCode, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";

interface QRModalProps {
  shortUrl: string;
  shortId: string;
  isOpen: boolean;
  onClose: () => void;
}

export function QRModal({ shortUrl, shortId, isOpen, onClose }: QRModalProps) {

  const [isLoaded, setIsLoaded] = useState(false);
const [imgError, setImgError] = useState(false);
   const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const qrSrc = `${API_URL}/api/v1/url/qr/${shortId}?size=300`;
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    if (isOpen) {
      document.addEventListener("keydown", handleEscape);
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.body.style.overflow = "";
    };
  }, [isOpen, onClose]);


useEffect(() => {
    if (isOpen) {
        setIsLoaded(false);
        setImgError(false);
    }
}, [isOpen, shortId]);



const handleDownload = async () => {
    try {
        const response = await fetch(qrSrc);
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.download = `qr-${shortId}.png`;
        link.href = url;
        link.click();
        URL.revokeObjectURL(url);
    } catch {
        window.open(qrSrc, "_blank");
    }
};
  if (!isOpen) return null;

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-foreground/10 backdrop-blur-md animate-in fade-in-0 duration-200"
      onClick={onClose}
    >
      <div 
        className="w-full max-w-sm glass-card rounded-2xl shadow-2xl animate-in zoom-in-95 slide-in-from-bottom-4 duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-border/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 flex items-center justify-center">
              <QrCode className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-foreground">QR Code</h2>
              <p className="text-sm text-muted-foreground">Scan to open link</p>
            </div>
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="rounded-full hover:bg-muted/50"
          >
            <X className="w-5 h-5" />
          </Button>
        </div>

        {/* Content */}
        <div className="p-6 flex flex-col items-center gap-6">
          <div className="p-6 bg-card rounded-2xl border border-border/50 shadow-lg
                flex items-center justify-center min-h-[220px]">
  {imgError ? (
    <p className="text-sm text-destructive">Failed to load QR code</p>
  ) : (
    <>
      {!isLoaded && <Loader2 className="w-8 h-8 text-primary animate-spin" />}
      <img
        src={qrSrc}
        alt={`QR code for ${shortUrl}`}
        width={220}
        height={220}
        className={`rounded-xl transition-opacity duration-300
                   ${isLoaded ? "opacity-100" : "opacity-0"}`}
        onLoad={() => setIsLoaded(true)}
        onError={() => { setImgError(true); setIsLoaded(true); }}
      />
    </>
  )}
</div>

          <div className="w-full p-4 rounded-xl bg-muted/30 border border-border/30">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-1">Link</p>
            <p className="text-sm font-mono font-medium text-foreground break-all">
              {shortUrl}
            </p>
          </div>

          <Button
            onClick={handleDownload}
            className="w-full h-12 rounded-xl font-semibold bg-gradient-to-r from-primary to-primary/90 hover:from-primary/90 hover:to-primary/80 text-primary-foreground btn-glow transition-all duration-300"
          >
            <Download className="w-4 h-4 mr-2" />
            Download PNG
          </Button>
        </div>
      </div>
    </div>
  );
}
