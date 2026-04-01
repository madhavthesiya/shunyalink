import Link from "next/link";
import { Sparkles, Zap, ArrowRight, User } from "lucide-react";

export function BioShowcase({ isLoggedIn }: { isLoggedIn: boolean }) {
  return (
    <section className="py-20 sm:py-32 bg-gradient-to-b from-primary/[0.07] via-muted/40 to-background border-y border-border/60 relative overflow-hidden">
      <div className="absolute inset-0 grid-pattern opacity-30" />
      <div className="max-w-5xl mx-auto px-4 sm:px-6 relative z-10">
        <div className="flex flex-col md:flex-row items-center gap-12 sm:gap-20">
          <div className="flex-1 text-center md:text-left">
            <span className="inline-block px-3 py-1 rounded-full bg-primary/10 text-primary text-xs font-bold uppercase tracking-wider mb-6">
              Personal Branding
            </span>
            <h2 className="text-4xl sm:text-5xl font-bold text-foreground mb-6 leading-tight">
              One Link to <br />
              <span className="text-gradient">Rule them all.</span>
            </h2>
            <p className="text-lg text-muted-foreground mb-8 leading-relaxed">
              Tired of changing Bio links everyday? ShunyaLink gives you a permanent,
              beautiful storefront for your online identity. Connect your Instagram,
              Twitter, Portfolio, and Shop in one place.
            </p>
            <div className="space-y-4 mb-10">
              <div className="flex items-center gap-3 text-foreground font-medium justify-center md:justify-start">
                <div className="w-6 h-6 rounded-full bg-success/20 flex items-center justify-center shrink-0">
                  <Zap className="w-3.5 h-3.5 text-success" />
                </div>
                Claim your unique handle: shunyalink.madhavv.me/@name
              </div>
              <div className="flex items-center gap-3 text-foreground font-medium justify-center md:justify-start">
                <div className="w-6 h-6 rounded-full bg-success/20 flex items-center justify-center shrink-0">
                  <Zap className="w-3.5 h-3.5 text-success" />
                </div>
                Unlimited social links & custom themes
              </div>
            </div>
            <Link
              href={isLoggedIn ? "/dashboard?tab=settings" : "/register"}
              className="inline-flex items-center gap-2 px-8 py-4 rounded-2xl bg-primary text-primary-foreground font-bold btn-glow transition-all group"
            >
              {isLoggedIn ? "Manage your Bio Profile" : "Create your Bio Profile"}
              <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
            </Link>
          </div>

          <div className="flex-1 relative pb-12 sm:pb-0 w-full">
            <div className="relative z-10 max-w-[300px] mx-auto">
              <div className="relative rounded-[3rem] border-8 border-foreground/5 shadow-2xl p-4 bg-background aspect-[9/18.5] overflow-hidden group hover:scale-105 transition-transform duration-500">
                <div className="absolute top-2 left-1/2 -translate-x-1/2 w-20 h-5 bg-foreground/10 rounded-full z-20" />

                <div className="h-full w-full rounded-[2.5rem] bg-gradient-to-b from-primary/5 to-transparent p-6 flex flex-col items-center">
                  <div className="w-20 h-20 rounded-full bg-gradient-to-tr from-primary/20 via-primary/40 to-primary/20 p-1 mb-4 mt-4">
                    <div className="w-full h-full rounded-full bg-background border-2 border-primary/20 overflow-hidden flex items-center justify-center">
                      <User className="w-10 h-10 text-primary/40" />
                    </div>
                  </div>

                  <div className="text-center mb-10">
                    <h4 className="text-lg font-bold text-foreground mb-1">Your Name</h4>
                    <p className="text-sm font-semibold text-primary mb-2">@username</p>
                    <p className="text-[10px] text-muted-foreground leading-tight px-4 opacity-70">
                      Your bio will appear here to welcome your visitors...
                    </p>
                  </div>

                  <div className="w-full space-y-3 mb-auto">
                    {["Portfolio", "Twitter", "GitHub"].map((label) => (
                      <div
                        key={label}
                        className="w-full py-3 px-4 rounded-xl border border-primary/10 bg-background shadow-sm text-center text-[10px] font-bold text-foreground/80 cursor-default"
                      >
                        {label}
                      </div>
                    ))}
                  </div>

                  <div className="flex items-center gap-1.5 opacity-40 mt-6 group-hover:opacity-60 transition-opacity">
                    <Sparkles className="w-3 h-3 text-primary" />
                    <span className="text-[10px] font-bold tracking-tight">
                      shunyalink.madhavv.me
                    </span>
                  </div>
                </div>
              </div>

              <div className="absolute -top-10 -right-10 w-40 h-40 bg-primary/20 blur-[80px] -z-10 animate-pulse" />
              <div className="absolute -bottom-10 -left-10 w-40 h-40 bg-primary/30 blur-[80px] -z-10 animate-pulse delay-700" />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
