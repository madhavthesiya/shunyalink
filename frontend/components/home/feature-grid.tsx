import Link from "next/link";
import {
  Sparkles,
  Zap,
  BarChart3,
  Settings2,
  ArrowRight,
  ShieldCheck,
  Lock,
} from "lucide-react";

const features = [
  {
    icon: Sparkles,
    title: "Smart AI Metadata",
    body:
      "Automatic link titles and descriptions. Our AI-powered scraping engine ensures your links look professional without manual effort.",
  },
  {
    icon: BarChart3,
    title: "Time-Series Analytics",
    body:
      "Go beyond total counts. Visualize click patterns with millisecond precision using high-fidelity Area and Line charts.",
  },
  {
    icon: Zap,
    title: "Geo-Location Tracking",
    body:
      "Understand your global reach. See exactly which countries and regions are engaging with your content in real-time.",
  },
  {
    icon: ShieldCheck,
    title: "Secure Revocation",
    body:
      "Real-time session blacklisting. Your security matters—once you log out, your session is instantly purged from our global network.",
  },
  {
    icon: Lock,
    title: "Password Encryption",
    body:
      "Protect your sensitive assets with AES-256 password protection and link-specific security challenges.",
  },
  {
    icon: Settings2,
    title: "Custom Branding",
    body:
      "Secure your unique @handle and customize your Bio profile storefront to showcase your full online identity.",
  },
] as const;

export function FeatureGrid() {
  return (
    <section className="py-20 sm:py-28 border-t border-border/60 bg-muted/30">
      <div className="max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center mb-14 sm:mb-20 max-w-3xl mx-auto">
          <span className="inline-block px-3 py-1 rounded-full bg-primary/10 border border-primary/15 text-primary text-xs font-semibold uppercase tracking-wider mb-4">
            Platform
          </span>
          <h2 className="text-3xl sm:text-5xl font-bold text-foreground mb-4 text-balance tracking-tight">
            Everything you need for{" "}
            <span className="text-gradient">links & identity</span>
          </h2>
          <p className="text-muted-foreground text-lg leading-relaxed">
            Infrastructure-grade tooling with a product experience that stays out of your way.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 sm:gap-6">
          {features.map(({ icon: Icon, title, body }) => (
            <div
              key={title}
              className="group relative p-7 sm:p-8 rounded-3xl bg-card border border-border/70 shadow-sm hover:shadow-xl hover:shadow-primary/[0.07] hover:border-primary/25 transition-all duration-300 hover:-translate-y-0.5"
            >
              <div className="absolute inset-0 rounded-3xl bg-gradient-to-br from-primary/[0.03] to-transparent opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none" />
              <div className="relative">
                <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-primary/15 to-violet-500/10 flex items-center justify-center mb-5 group-hover:scale-105 transition-transform">
                  <Icon className="w-6 h-6 text-primary" />
                </div>
                <h3 className="text-lg font-bold text-foreground mb-2 tracking-tight">{title}</h3>
                <p className="text-muted-foreground text-sm leading-relaxed">{body}</p>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-14 text-center">
          <Link
            href="/register"
            className="inline-flex items-center gap-2 text-primary font-semibold hover:underline underline-offset-4 decoration-primary/40 group"
          >
            Create a free account
            <ArrowRight className="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
          </Link>
        </div>
      </div>
    </section>
  );
}
