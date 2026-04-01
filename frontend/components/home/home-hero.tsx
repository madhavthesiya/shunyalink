import Link from "next/link";
import { ArrowRight, Sparkles } from "lucide-react";

export function HomeHero({ isLoggedIn }: { isLoggedIn: boolean }) {
  return (
    <section className="pt-4 sm:pt-8 pb-14 px-4 sm:px-6 relative">
      <div className="max-w-3xl mx-auto text-center">

        <h1 className="text-4xl sm:text-6xl md:text-7xl font-bold tracking-tight text-foreground mb-6 animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-100">
          <span className="text-balance">Shorten links.</span>
          <br />
          <span className="text-gradient">Own your identity.</span>
        </h1>

        <p className="text-base sm:text-xl text-muted-foreground max-w-2xl mx-auto mb-10 animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-200 text-pretty leading-relaxed">
          Turn long URLs into clean short links and showcase them on a polished{" "}
          <strong className="text-foreground font-semibold">link-in-bio</strong> built for
          creators and teams.
        </p>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 sm:gap-8 animate-in fade-in-0 slide-in-from-bottom-4 duration-700 delay-300">
          <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-sm text-muted-foreground">
            <span className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-success shadow-[0_0_8px_rgba(5,150,105,0.5)]" />
              No signup to shorten
            </span>
            <span className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-success shadow-[0_0_8px_rgba(5,150,105,0.5)]" />
              Fast redirects
            </span>
          </div>
          <div className="hidden sm:block w-px h-5 bg-border" />
          <Link
            href={isLoggedIn ? "/dashboard?tab=settings" : "/register"}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-foreground text-background text-sm font-semibold hover:opacity-90 transition-opacity shadow-lg shadow-foreground/10 group"
          >
            {isLoggedIn ? "Edit bio profile" : "Claim your handle"}
            <ArrowRight className="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
          </Link>
        </div>
      </div>
    </section>
  );
}
