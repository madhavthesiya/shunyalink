import type { Metadata } from "next";
import Link from "next/link";
import { ShieldAlert, ArrowRight } from "lucide-react";

export const metadata: Metadata = {
  title: "Blocked link | ShunyaLink",
  description: "This link was blocked: ShunyaLink's safety checks flagged its destination as unsafe.",
  robots: { index: false, follow: false },
};

/**
 * Where a blocked short link sends its visitors (roadmap 3.4c). The destination is never shown: the point of the
 * block is that nobody reaches it from here.
 */
export default async function BlockedLinkPage({ params }: { params: Promise<{ shortId: string }> }) {
  const { shortId } = await params;

  return (
    <div className="min-h-screen flex items-center justify-center bg-background gradient-bg px-4 py-12 relative overflow-hidden">
      <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-destructive/5 blur-[120px] rounded-full -mr-64 -mt-64" />
      <div className="absolute bottom-0 left-0 w-[500px] h-[500px] bg-destructive/5 blur-[120px] rounded-full -ml-64 -mb-64" />

      <div className="glass-card max-w-md w-full p-8 sm:p-10 rounded-2xl shadow-2xl relative z-10 border border-destructive/20">
        <div className="w-20 h-20 bg-gradient-to-br from-destructive/20 to-destructive/5 rounded-2xl flex items-center justify-center mx-auto mb-8 border border-destructive/20">
          <ShieldAlert className="w-10 h-10 text-destructive" />
        </div>

        <div className="text-center space-y-3 mb-8">
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Link blocked</h1>
          <p className="text-muted-foreground text-sm leading-relaxed">
            Our safety checks flagged where this link leads as phishing or malware, so we stopped it here. Its
            destination isn&apos;t shown, to keep you safe.
          </p>
          <p className="text-xs font-mono text-muted-foreground/60">/{shortId}</p>
        </div>

        <Link
          href="/"
          className="w-full h-12 inline-flex items-center justify-center rounded-xl bg-primary text-primary-foreground font-semibold hover:bg-primary/90 transition-colors"
        >
          Go to ShunyaLink
          <ArrowRight className="w-4 h-4 ml-2" />
        </Link>

        <p className="mt-8 pt-6 border-t border-primary/5 text-center text-xs text-muted-foreground/70">
          Created this link? It&apos;s marked as blocked in your dashboard.
        </p>
      </div>
    </div>
  );
}
