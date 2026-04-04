import { Link2, ShieldAlert, Share2 } from "lucide-react";
import { cn } from "@/lib/utils";

const steps = [
  {
    icon: Link2,
    title: "1. Create & Customize",
    description: "Paste any long URL to instantly generate a branded, tracking-enabled short link.",
  },
  {
    icon: ShieldAlert,
    title: "2. Secure Access",
    description: "Protect sensitive URLs with mathematical AES-level password barriers before sharing.",
  },
  {
    icon: Share2,
    title: "3. Analyze Traffic",
    description: "Share globally and track every click, device type, and geographical location live.",
  },
];

export function HowItWorks() {
  return (
    <section className="py-16 sm:py-20 bg-background relative overflow-hidden">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 relative z-10">
        <div className="text-center mb-16 sm:mb-20">
          <h2 className="text-3xl sm:text-5xl font-bold text-foreground mb-4 tracking-tight">
            Built for speed. <br className="sm:hidden" />
            <span className="text-gradient">Secured by math.</span>
          </h2>
          <p className="text-muted-foreground text-lg max-w-2xl mx-auto">
            Three simple steps to secure your links and scale your reach.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 sm:gap-12 relative">
          {/* Connecting line for desktop */}
          <div className="hidden md:block absolute top-[40px] left-[15%] right-[15%] h-px bg-gradient-to-r from-transparent via-border to-transparent" />

          {steps.map((step, index) => {
            const Icon = step.icon;
            return (
              <div key={step.title} className="relative flex flex-col items-center text-center group">
                <div className="w-20 h-20 rounded-full bg-card border-4 border-background flex items-center justify-center p-1 mb-6 relative z-10 shadow-xl group-hover:scale-110 transition-transform duration-500">
                  <div className="w-full h-full rounded-full bg-primary/10 flex items-center justify-center">
                    <Icon className="w-8 h-8 text-primary" />
                  </div>
                </div>
                <h3 className="text-xl font-bold text-foreground mb-3">{step.title}</h3>
                <p className="text-muted-foreground leading-relaxed max-w-sm">{step.description}</p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
