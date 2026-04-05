"use client";

import { useState } from "react";
import { User, Code2, ExternalLink, Sparkles, Github, Trophy } from "lucide-react";

const THEME = "#6366f1";

/* ── tiny mock link button ─────────────────────────────────── */
function MockLink({ label }: { label: string }) {
  return (
    <div
      className="flex items-center gap-2 w-full rounded-xl px-3 py-2.5 text-[10px] font-semibold text-white/80"
      style={{ background: `${THEME}20`, border: `1px solid ${THEME}35` }}
    >
      <div className="h-3.5 w-1 rounded-full flex-shrink-0" style={{ background: THEME }} />
      <span className="truncate">{label}</span>
      <ExternalLink className="h-2.5 w-2.5 ml-auto opacity-40" />
    </div>
  );
}

/* ── NORMAL profile preview ─────────────────────────────────── */
function NormalPreview() {
  return (
    <div className="relative w-full h-full overflow-hidden rounded-b-2xl flex flex-col items-center pt-8 px-4 pb-6"
      style={{
        background: `radial-gradient(ellipse 120% 60% at 50% -10%, ${THEME}22 0%, transparent 65%), #0a0a0f`
      }}
    >
      {/* subtle grid */}
      <div className="absolute inset-0 opacity-[0.04]" style={{
        backgroundImage: `linear-gradient(${THEME} 1px, transparent 1px), linear-gradient(90deg, ${THEME} 1px, transparent 1px)`,
        backgroundSize: "24px 24px"
      }} />

      <div className="relative z-10 flex flex-col items-center w-full max-w-[160px]">
        {/* Avatar */}
        <div className="relative mb-3">
          <div className="absolute -inset-1.5 rounded-full opacity-50"
            style={{ background: `conic-gradient(from 0deg, ${THEME}, transparent 40%, ${THEME})`, filter: "blur(3px)", animation: "spin 6s linear infinite" }} />
          <div className="relative w-14 h-14 rounded-full border-2 flex items-center justify-center"
            style={{ borderColor: THEME, background: `linear-gradient(135deg, ${THEME}40, ${THEME}10)` }}>
            <User className="w-6 h-6 text-white/50" />
          </div>
          <div className="absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-[#0a0a0f] bg-emerald-400" />
        </div>

        {/* Name */}
        <p className="text-white font-black text-sm tracking-tight">Madhav Thesiya</p>

        {/* Username badge */}
        <div className="mt-1 mb-3 px-2.5 py-0.5 rounded-full text-[9px] font-bold"
          style={{ background: `${THEME}20`, color: THEME, border: `1px solid ${THEME}35` }}>
          @madhav
        </div>

        {/* Bio */}
        <p className="text-white/40 text-[9px] text-center mb-3 leading-relaxed">
          Full-stack developer · Open source enthusiast
        </p>

        {/* Divider */}
        <div className="flex w-full items-center gap-2 mb-2.5">
          <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, transparent, ${THEME}50)` }} />
          <span className="text-[7px] font-black uppercase tracking-widest" style={{ color: THEME }}>Links</span>
          <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, ${THEME}50, transparent)` }} />
        </div>

        {/* Links */}
        <div className="w-full space-y-1.5">
          <MockLink label="GitHub Profile" />
          <MockLink label="Portfolio Website" />
          <MockLink label="LinkedIn" />
        </div>
      </div>
    </div>
  );
}

/* ── PROGRAMMER profile preview ─────────────────────────────── */
function ProgrammerPreview() {
  return (
    <div className="relative w-full h-full overflow-hidden rounded-b-2xl flex flex-col items-center pt-6 px-3 pb-4"
      style={{
        background: `radial-gradient(ellipse 100% 50% at 50% 0%, ${THEME}18 0%, transparent 60%), #0a0a0f`
      }}
    >
      {/* subtle grid */}
      <div className="absolute inset-0 opacity-[0.04]" style={{
        backgroundImage: `linear-gradient(${THEME} 1px, transparent 1px), linear-gradient(90deg, ${THEME} 1px, transparent 1px)`,
        backgroundSize: "20px 20px"
      }} />

      <div className="relative z-10 w-full">
        {/* Hero */}
        <div className="flex flex-col items-center mb-4">
          <div className="relative mb-2">
            <div className="absolute -inset-1 rounded-full opacity-40"
              style={{ background: `conic-gradient(from 0deg, ${THEME}, transparent 35%, ${THEME})`, filter: "blur(3px)", animation: "spin 8s linear infinite" }} />
            <div className="relative w-10 h-10 rounded-full border-2 flex items-center justify-center"
              style={{ borderColor: THEME, background: `linear-gradient(135deg, ${THEME}40, ${THEME}10)` }}>
              <User className="w-4 h-4 text-white/50" />
            </div>
          </div>
          <p className="text-white font-black text-xs tracking-tight">Madhav Thesiya</p>
          <div className="mt-0.5 px-2 py-0.5 rounded-full text-[7px] font-bold flex items-center gap-1"
            style={{ background: `${THEME}18`, color: THEME, border: `1px solid ${THEME}30` }}>
            <Code2 className="w-2 h-2" />@madhav
          </div>
        </div>

        {/* Section divider */}
        <div className="flex items-center gap-2 mb-2">
          <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, transparent, ${THEME}50)` }} />
          <span className="text-[6px] font-black uppercase tracking-widest" style={{ color: THEME }}>CP Stats</span>
          <div className="h-px flex-1" style={{ background: `linear-gradient(90deg, ${THEME}50, transparent)` }} />
        </div>

        {/* Stats grid */}
        <div className="grid grid-cols-3 gap-1.5 mb-1.5">
          {/* Total solved */}
          <div className="col-span-3 rounded-xl p-2.5 text-center"
            style={{ background: `${THEME}20`, border: `1px solid ${THEME}30` }}>
            <p className="text-[7px] font-bold text-white/40 uppercase tracking-wider">Total Solved</p>
            <p className="text-xl font-black" style={{ color: THEME }}>842</p>
          </div>

          {/* Platform mini cards */}
          {[
            { label: "LC", value: "342" },
            { label: "CF", value: "1580" },
            { label: "GH", value: "42 ★" },
            { label: "CC", value: "1842" },
            { label: "AC", value: "1203" },
          ].map(({ label, value }) => (
            <div key={label} className="rounded-lg p-2 flex flex-col items-center"
              style={{ background: "hsl(var(--card) / 0.4)", border: `1px solid ${THEME}20` }}>
              <span className="text-[7px] font-black text-white/30 uppercase">{label}</span>
              <span className="text-[10px] font-black text-white">{value}</span>
            </div>
          ))}

          {/* Roast card */}
          <div className="col-span-3 rounded-xl p-2 flex items-center gap-2"
            style={{ background: `${THEME}10`, border: `1px dashed ${THEME}25` }}>
            <Sparkles className="w-3 h-3 flex-shrink-0" style={{ color: THEME }} />
            <p className="text-[7px] italic text-white/40 leading-relaxed truncate">
              &quot;Your CF rating is flatter than my coffee...&quot;
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── MAIN COMPONENT ─────────────────────────────────────────── */
export function ProgrammerShowcase() {
  const [active, setActive] = useState<"normal" | "programmer">("normal");

  return (
    <section className="py-16 sm:py-20 relative overflow-hidden bg-background">
      <div className="absolute inset-0 grid-pattern opacity-20" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 relative z-10">
        <div className="text-center mb-16 max-w-3xl mx-auto">
          <span className="inline-block px-3 py-1 rounded-full bg-primary/10 border border-primary/20 text-primary text-xs font-bold uppercase tracking-wider mb-6">
            Live Preview
          </span>
          <h2 className="text-4xl sm:text-6xl font-bold text-foreground mb-6 leading-tight tracking-tight">
            Two profiles, <br />
            <span className="text-gradient">one identity.</span>
          </h2>
          <p className="text-lg text-muted-foreground leading-relaxed">
            Your classic bio-link for everyone — and a full dev dashboard for recruiters.
            Toggle between them below.
          </p>
        </div>

        {/* Toggle + Preview layout */}
        <div className="flex flex-col lg:flex-row items-center lg:items-start justify-center gap-12 lg:gap-16">

          {/* Left: toggle + description */}
          <div className="flex flex-col items-center lg:items-start gap-8 lg:pt-8 max-w-sm">

            {/* Toggle pill */}
            <div className="flex items-center gap-1 p-1 rounded-2xl border border-border/60 bg-muted/30 backdrop-blur-sm">
              <button
                onClick={() => setActive("normal")}
                className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-bold transition-all duration-300 ${
                  active === "normal"
                    ? "bg-foreground text-background shadow-md"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                <User className="w-4 h-4" />
                Normal
              </button>
              <button
                onClick={() => setActive("programmer")}
                className={`flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-bold transition-all duration-300 ${
                  active === "programmer"
                    ? "bg-foreground text-background shadow-md"
                    : "text-muted-foreground hover:text-foreground"
                }`}
              >
                <Code2 className="w-4 h-4" />
                Programmer
              </button>
            </div>

            {/* Description */}
            <div className="text-center lg:text-left space-y-4">
              {active === "normal" ? (
                <div className="animate-in fade-in slide-in-from-bottom-2 duration-300">
                  <h3 className="text-xl font-bold text-foreground mb-2">Bio-Link Profile</h3>
                  <p className="text-sm text-muted-foreground leading-relaxed">
                    A clean, minimal page with your avatar, bio, and all your links — perfect for social media and sharing.
                  </p>
                  <div className="mt-4 flex flex-col gap-2">
                    {["Custom themeColor", "Staggered link animations", "Dynamic Social Metadata", "Works on mobile"].map(f => (
                      <div key={f} className="flex items-center gap-2 text-sm text-muted-foreground">
                        <div className="w-1.5 h-1.5 rounded-full bg-primary shrink-0" />
                        {f}
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="animate-in fade-in slide-in-from-bottom-2 duration-300">
                  <h3 className="text-xl font-bold text-foreground mb-2">CP Dashboard Profile</h3>
                  <p className="text-sm text-muted-foreground leading-relaxed">
                    A full stats dashboard showing live data from 5 coding platforms — perfect for job applications.
                  </p>
                  <div className="mt-4 flex flex-col gap-2">
                    {["LeetCode · Codeforces · GitHub", "Verified clickable profile tiles", "AI profile roast", "Live data, Redis cached"].map(f => (
                      <div key={f} className="flex items-center gap-2 text-sm text-muted-foreground">
                        <div className="w-1.5 h-1.5 rounded-full bg-primary shrink-0" />
                        {f}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* URL preview */}
            <div className="flex items-center gap-2 px-4 py-2.5 rounded-xl border border-border/50 bg-muted/20 text-xs text-muted-foreground font-mono w-full">
              <div className="flex gap-1">
                <div className="w-2 h-2 rounded-full bg-destructive/60" />
                <div className="w-2 h-2 rounded-full bg-yellow-500/60" />
                <div className="w-2 h-2 rounded-full bg-success/60" />
              </div>
              <span className="truncate">
                {active === "normal" ? "shunyalink.madhavv.me/@madhav" : "shunyalink.madhavv.me/cp/madhav"}
              </span>
            </div>
          </div>

          {/* Right: phone mockup */}
          <div className="relative flex-shrink-0">
            {/* Outer glow */}
            <div
              className="absolute -inset-6 rounded-[3rem] opacity-20 blur-2xl transition-all duration-700"
              style={{ background: THEME }}
            />

            {/* Phone frame */}
            <div className="relative w-[200px] sm:w-[220px] rounded-[2rem] border-4 border-border/60 bg-[#0a0a0f] shadow-2xl overflow-hidden"
              style={{ height: active === "normal" ? "420px" : "460px", transition: "height 0.4s ease" }}>
              {/* Notch */}
              <div className="flex justify-center pt-2 pb-1 bg-[#0a0a0f]">
                <div className="w-16 h-4 rounded-full bg-border/40" />
              </div>

              {/* Screen content — scale down the real profile */}
              <div className="w-full relative overflow-hidden" style={{ height: "calc(100% - 28px)" }}>
                <div
                  key={active}
                  className="animate-in fade-in duration-300 h-full"
                >
                  {active === "normal" ? <NormalPreview /> : <ProgrammerPreview />}
                </div>
              </div>
            </div>

            {/* Bottom home bar */}
            <div className="absolute bottom-3 left-1/2 -translate-x-1/2 w-16 h-1 rounded-full bg-border/50" />
          </div>
        </div>
      </div>
    </section>
  );
}
