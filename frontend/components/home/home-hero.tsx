import Link from "next/link";
import { ArrowRight, Code2, Link2, Sparkles } from "lucide-react";
import { cn } from "@/lib/utils";

export function HomeHero({ isLoggedIn }: { isLoggedIn: boolean }) {
  return (
    <section className="pt-8 sm:pt-12 pb-12 sm:pb-16 px-4 sm:px-6 relative overflow-hidden">
      {/* Background dynamic glow */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-3xl h-[400px] bg-gradient-to-b from-primary/15 to-transparent blur-[120px] rounded-full pointer-events-none -z-10" />

      <div className="max-w-4xl mx-auto text-center relative z-10">

        {/* Main Heading */}
        <h1 className="text-5xl sm:text-7xl lg:text-[5.5rem] font-black tracking-tighter text-foreground mb-6 animate-in fade-in-0 slide-in-from-bottom-6 duration-700 delay-100 leading-[1.1]">
          <span className="text-balance text-foreground">Secure short links &</span>
          <br />
          <span className="text-balance text-gradient">developer portfolios.</span>
        </h1>

        {/* Subtitle */}
        <p className="text-lg sm:text-xl md:text-2xl text-muted-foreground max-w-3xl mx-auto mb-10 animate-in fade-in-0 slide-in-from-bottom-6 duration-700 delay-200 text-balance leading-relaxed">
          Generate AI-categorized, password-protected short URLs with <span className="font-bold text-foreground">AI-Phishing detection</span>, while building a stunning bio profile that aggregates your live LeetCode, Codeforces, and GitHub stats.
        </p>

        {/* CTA Buttons */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 sm:gap-6 animate-in fade-in-0 slide-in-from-bottom-6 duration-700 delay-300">
          <Link
            href={isLoggedIn ? "/dashboard?tab=settings" : "/register"}
            className="flex items-center justify-center gap-2 w-full sm:w-auto px-8 py-4 rounded-full bg-foreground text-background text-base font-bold hover:scale-105 transition-all shadow-xl shadow-foreground/15 group"
          >
            {isLoggedIn ? "Edit bio profile" : "Claim your free handle"}
            <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
          </Link>
          
          <Link
            href="/@madhavthesiya"
            className="flex items-center justify-center gap-2 w-full sm:w-auto px-8 py-4 rounded-full border-2 border-border/60 bg-muted/20 backdrop-blur text-foreground text-base font-bold hover:bg-muted/40 transition-colors"
          >
            <Code2 className="w-5 h-5 opacity-50" />
            View demo profile
          </Link>
        </div>
        
        {/* Metric markers */}
        <div className="mt-16 pt-10 border-t border-border/40 flex flex-wrap justify-center gap-x-12 gap-y-6 animate-in fade-in-0 duration-1000 delay-500">
          {[
            { label: "Short Links", value: "Unlimited" },
            { label: "CP Integrations", value: "5 Platforms" },
            { label: "QR Codes", value: "Branded" }
          ].map((stat) => (
            <div key={stat.label} className="flex flex-col items-center">
              <span className="text-3xl font-black text-foreground tracking-tight">{stat.value}</span>
              <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground mt-1">{stat.label}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
