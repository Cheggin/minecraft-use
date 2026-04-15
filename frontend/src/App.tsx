import { useState, useEffect, useRef, type ReactNode } from "react";

const SPLASH_TEXTS = [
  "Claude Code in MC!",
  "/spawn lfg",
  "Talk to your villager!",
  "It writes code!",
  "It browses the web!",
  "Powered by Convex!",
  "Ask it anything!",
  "Build anything!",
  "Open source!",
  "Vibe code IRL!",
  "Coding in Minecraft!",
];

const NAV_LINKS = [
  { label: "Features", href: "#features" },
  { label: "Commands", href: "#commands" },
  { label: "Architecture", href: "#architecture" },
  { label: "Schematics", href: "#schematics" },
  { label: "Get Started", href: "#quickstart" },
];

const FEATURES = [
  {
    title: "Spawn AI Agents",
    desc: "Summon Claude Code as a Minecraft villager. It follows you around, displays output above its head with full ANSI color rendering, and responds to your chat messages.",
    command: "/agent alex",
    output: "Spawned librarian villager \"alex\" running Claude Code",
  },
  {
    title: "Find Schematics Online",
    desc: "Browser Use searches schematic sites for you. It finds builds, downloads the .litematica files, and stores them in your Convex database — ready to place in your world.",
    command: "/browser-use get-schematics castle",
    output: "Searching minecraft-schematics.com... Found 12 results",
  },
  {
    title: "Agent-to-Agent Chat",
    desc: "Your agents can talk to each other. Have them debate, review code, or collaborate on tasks — all visible as floating text in your world.",
    command: '/agent-chat alex rex "debate rust vs python"',
    output: "Starting multi-round conversation between alex and rex",
  },
  {
    title: "Build from the Web",
    desc: "Download schematics from the web and place them in your world. The full pipeline: search, download, store in Convex, load in Litematica.",
    command: "/build castle",
    output: "Downloaded castle.litematica — open Litematica (M+C) to place",
  },
];

const COMMANDS = [
  { cmd: "/claude hello", comment: "talk to Claude Code" },
  { cmd: "/agent alex", comment: "spawn a villager agent" },
  { cmd: "/agent rex wolf", comment: "spawn as a wolf" },
  { cmd: "/browser-use get-schematics tower", comment: "search the web" },
  { cmd: "/build list", comment: "list downloaded schematics" },
  { cmd: "/build 27283", comment: "place a schematic" },
  { cmd: '/agent-tell alex rex "review this"', comment: "agents talk" },
  { cmd: "/code", comment: "open VS Code in-game" },
  { cmd: "/agents", comment: "open agent dashboard" },
  { cmd: "/despawn alex", comment: "remove an agent" },
];

const QUICKSTART_STEPS = [
  {
    title: "Install",
    command: "npm install -g @reaganhsu/minecraft-code",
  },
  {
    title: "Check prerequisites",
    command: "minecraft-code doctor",
  },
  {
    title: "Set up everything",
    command: "minecraft-code init",
  },
  {
    title: "Launch",
    command: "minecraft-code start",
  },
];

const MOB_TYPES = [
  "villager", "wolf", "cat", "pig", "cow", "sheep", "chicken",
  "fox", "parrot", "rabbit", "horse", "donkey", "llama", "goat",
  "bee", "axolotl", "frog", "camel", "sniffer", "allay",
  "iron_golem", "snow_golem", "zombie", "skeleton", "creeper", "enderman",
];

/* ===== INTERSECTION OBSERVER HOOK ===== */

function useFadeUp() {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const prefersReduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (prefersReduced) {
      el.classList.add("visible");
      return;
    }

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          el.classList.add("visible");
          observer.unobserve(el);
        }
      },
      { threshold: 0.15 }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return ref;
}

function FadeUp({ children, className = "", delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  const ref = useFadeUp();
  const delayClass = delay > 0 ? `fade-up-delay-${delay}` : "";
  return (
    <div ref={ref} className={`fade-up ${delayClass} ${className}`}>
      {children}
    </div>
  );
}

/* ===== COPYABLE COMMAND ===== */

function CopyableCommand({ command }: { command: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(command).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="terminal-block w-full max-w-lg text-left mb-8 flex items-center justify-between gap-3" style={{ padding: "20px 16px 16px" }}>
      <div style={{ lineHeight: 1.6, whiteSpace: "nowrap", overflowX: "auto", fontSize: "clamp(10px, 2.8vw, 13px)" }}>
        <span className="terminal-prompt">$ </span>
        <span className="terminal-command">{command}</span>
      </div>
      <button
        onClick={handleCopy}
        className="shrink-0 cursor-pointer"
        style={{
          fontFamily: "var(--font-mc)",
          fontSize: "10px",
          color: copied ? "var(--color-garden)" : "var(--color-text-dim)",
          textShadow: "1px 1px 0px oklch(0.08 0.005 55)",
          background: "transparent",
          border: "none",
          padding: "4px 8px",
          transition: "color 0.15s",
          appearance: "none",
          WebkitAppearance: "none",
          outline: "none",
          boxShadow: "none",
        }}
        aria-label="Copy command"
      >
        {copied ? "copied" : "copy"}
      </button>
    </div>
  );
}

/* ===== MAIN APP ===== */

export default function App() {
  const [splash] = useState(() => SPLASH_TEXTS[Math.floor(Math.random() * SPLASH_TEXTS.length)]);

  return (
    <div className="min-h-screen">
      <Nav />
      <HeroSection splash={splash} />
      <FeaturesSection />
      <CommandsSection />
      <ArchitectureSection />
      <SchematicsSection />
      <QuickStartSection />
      <Footer />
    </div>
  );
}

/* ===== NAVIGATION ===== */

function Nav() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 60);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <nav
      className="fixed top-0 left-0 right-0 z-50 transition-colors duration-300"
      style={{
        background: scrolled ? "oklch(0.12 0.015 55 / 0.92)" : "transparent",
        backdropFilter: scrolled ? "blur(8px)" : "none",
      }}
    >
      <div className="max-w-6xl mx-auto px-6 flex items-center justify-between h-14">
        <a href="#" className="nav-link" style={{ fontSize: "13px", color: "var(--color-text-accent)" }}>
          minecraft-code
        </a>
        <div className="hidden sm:flex items-center gap-8">
          {NAV_LINKS.map((link) => (
            <a key={link.href} href={link.href} className="nav-link">
              {link.label}
            </a>
          ))}
        </div>
        <div className="flex items-center gap-6">
          <a
            href="https://buymeacoffee.com/reaganhsu1b"
            target="_blank"
            rel="noopener noreferrer"
            className="nav-link"
            style={{ color: "var(--color-terracotta)" }}
          >
            Donate
          </a>
          <a
            href="https://github.com/Cheggin/minecraft-use"
            target="_blank"
            rel="noopener noreferrer"
            className="nav-link"
          >
            GitHub
          </a>
        </div>
      </div>
    </nav>
  );
}

/* ===== HERO ===== */

function HeroSection({ splash }: { splash: string }) {
  return (
    <section className="relative w-full min-h-screen flex flex-col items-center justify-center overflow-hidden">
      {/* Garden background */}
      <img
        src="/hero-garden.png"
        alt=""
        className="absolute inset-0 w-full h-full object-cover"
        style={{ imageRendering: "auto" }}
      />
      {/* Gradient overlay — strong at top for nav, heavier center for text readability */}
      <div
        className="absolute inset-0"
        style={{
          background: `linear-gradient(
            to bottom,
            oklch(0.08 0.015 55 / 0.85) 0%,
            oklch(0.10 0.015 55 / 0.55) 20%,
            oklch(0.10 0.015 55 / 0.50) 40%,
            oklch(0.10 0.015 55 / 0.45) 55%,
            oklch(0.10 0.015 55 / 0.55) 70%,
            oklch(0.10 0.015 55 / 0.95) 100%
          )`,
        }}
      />

      <div className="relative z-10 flex flex-col items-center px-6 pt-24 pb-16 max-w-4xl text-center">
        {/* Title block */}
        <div className="relative mb-8">
          <h1
            className="mc-hero-title leading-none"
            style={{ fontSize: "clamp(48px, 12vw, 120px)" }}
          >
            MINECRAFT
          </h1>
          <span
            className="mc-hero-subtitle block leading-none mt-1 sm:mt-2"
            style={{ fontSize: "clamp(22px, 5vw, 56px)" }}
          >
            — CODE —
          </span>

          {/* Splash text */}
          <div className="absolute -right-2 sm:-right-20 top-12 sm:top-16">
            <span className="mc-splash text-sm sm:text-xl whitespace-nowrap block">
              {splash}
            </span>
          </div>
        </div>

        {/* Tagline */}
        <p
          className="text-pixel-strong mb-12"
          style={{
            fontFamily: "var(--font-mc)",
            fontSize: "clamp(12px, 2.5vw, 16px)",
            color: "var(--color-text-primary)",
            lineHeight: 1.8,
            maxWidth: "50ch",
            background: "oklch(0.08 0.01 55 / 0.75)",
            padding: "16px 20px",
          }}
        >
          Turn Minecraft into a coding workstation. Spawn Claude Code
          as a villager, open VS Code in-game, find schematics on
          the web, and build them in your world — all from chat.
        </p>

        {/* Install command */}
        <CopyableCommand command="npm install -g @reaganhsu/minecraft-code" />

        {/* CTA buttons */}
        <div className="flex flex-col sm:flex-row gap-3">
          <a href="#quickstart" className="mc-btn" style={{ minWidth: 180 }}>
            Get Started
          </a>
          <a
            href="https://github.com/Cheggin/minecraft-use"
            target="_blank"
            rel="noopener noreferrer"
            className="mc-btn"
            style={{ minWidth: 180, opacity: 0.85 }}
          >
            View Source
          </a>
          <a
            href="https://buymeacoffee.com/reaganhsu1b"
            target="_blank"
            rel="noopener noreferrer"
            className="mc-btn"
            style={{ minWidth: 180, color: "var(--color-terracotta)" }}
          >
            Buy Me a Coffee
          </a>
        </div>

        {/* Version */}
        <p
          className="mt-12"
          style={{
            fontFamily: "var(--font-mc)",
            fontSize: "10px",
            color: "var(--color-text-dim)",
            textShadow: "1px 1px 0px oklch(0.10 0.005 55)",
          }}
        >
          v0.1.1 &middot; Fabric 1.21.1 &middot; MIT License
        </p>
      </div>

      {/* Scroll hint */}
      <div
        className="absolute bottom-8 left-1/2 -translate-x-1/2 z-10"
        style={{
          fontFamily: "var(--font-mc)",
          fontSize: "10px",
          color: "var(--color-text-dim)",
          textShadow: "1px 1px 0px oklch(0.10 0.005 55)",
        }}
      >
        scroll down
      </div>
    </section>
  );
}

/* ===== FEATURES ===== */

function FeaturesSection() {
  return (
    <section id="features" className="relative py-24 sm:py-32">
      <div className="max-w-5xl mx-auto px-6">
        <FadeUp>
          <span className="section-label block mb-4">What it does</span>
          <h2 className="section-heading mb-16">Your Minecraft world,<br />now with AI agents</h2>
        </FadeUp>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-x-16 gap-y-12">
          {FEATURES.map((feature, i) => (
            <FadeUp key={feature.title} delay={Math.min(i + 1, 3) as 1 | 2 | 3}>
              <div className="feature-item">
                <h3 className="feature-title">{feature.title}</h3>
                <p className="feature-desc mb-4">{feature.desc}</p>
                <div className="terminal-block">
                  <div>
                    <span className="terminal-prompt">{">"} </span>
                    <span className="terminal-command">{feature.command}</span>
                  </div>
                  <div className="mt-1">
                    <span className="terminal-output">{feature.output}</span>
                  </div>
                </div>
              </div>
            </FadeUp>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ===== COMMANDS ===== */

function CommandsSection() {
  return (
    <section id="commands" className="relative py-24 sm:py-32" style={{ background: "var(--color-surface)" }}>
      <div className="max-w-5xl mx-auto px-6">
        <FadeUp>
          <span className="section-label block mb-4">Commands</span>
          <h2 className="section-heading mb-6">Everything from chat</h2>
          <p className="feature-desc mb-12" style={{ maxWidth: "55ch" }}>
            No GUI needed. Every feature is a chat command. Type it in Minecraft,
            the mod sends it to the right tmux pane, and the response appears
            above your villager's head.
          </p>
        </FadeUp>

        <FadeUp delay={1}>
          <div className="terminal-block max-w-2xl">
            {COMMANDS.map((c, i) => (
              <div key={i} className={i > 0 ? "mt-2" : ""}>
                <span className="terminal-prompt">{">"} </span>
                <span className="terminal-command">{c.cmd}</span>
                <span className="terminal-comment">  {"// "}{c.comment}</span>
              </div>
            ))}
          </div>
        </FadeUp>

        {/* Mob types */}
        <FadeUp delay={2}>
          <div className="mt-16">
            <span className="section-label block mb-4">Supported mobs</span>
            <p className="feature-desc mb-6">
              Spawn your agent as any of these mob types:
            </p>
            <div className="flex flex-wrap gap-2">
              {MOB_TYPES.map((mob) => (
                <span
                  key={mob}
                  className="mc-tag"
                >
                  {mob}
                </span>
              ))}
            </div>
          </div>
        </FadeUp>
      </div>
    </section>
  );
}

/* ===== ARCHITECTURE ===== */

function ArchitectureSection() {
  return (
    <section id="architecture" className="relative py-24 sm:py-32">
      <div className="max-w-5xl mx-auto px-6">
        <FadeUp>
          <span className="section-label block mb-4">How it works</span>
          <h2 className="section-heading mb-6">tmux-bridged architecture</h2>
          <p className="feature-desc mb-12" style={{ maxWidth: "60ch" }}>
            The mod connects to tmux terminal panes via smux. Each command
            sends text to a named pane and reads the output back. The mod
            doesn't know or care what's running in each pane.
          </p>
        </FadeUp>

        {/* Flow diagram */}
        <FadeUp delay={1}>
          <div className="flex flex-col sm:flex-row items-center gap-3 mb-16 max-w-2xl">
            <div className="arch-box flex-1 w-full">Minecraft Chat</div>
            <span style={{ fontFamily: "var(--font-mc)", fontSize: "16px", color: "var(--color-text-dim)" }}>→</span>
            <div className="arch-box arch-box-active flex-1 w-full">TmuxBridge.java</div>
            <span style={{ fontFamily: "var(--font-mc)", fontSize: "16px", color: "var(--color-text-dim)" }}>→</span>
            <div className="arch-box flex-1 w-full">tmux pane</div>
            <span style={{ fontFamily: "var(--font-mc)", fontSize: "16px", color: "var(--color-text-dim)" }}>→</span>
            <div className="arch-box arch-box-highlight flex-1 w-full">AI responds</div>
          </div>
        </FadeUp>

        {/* tmux layout */}
        <FadeUp delay={2}>
          <TmuxDiagram />
        </FadeUp>
      </div>
    </section>
  );
}

/* ===== SCHEMATICS ===== */

/* ===== TMUX DIAGRAM ===== */

function TmuxPane({ name, detail, color }: { name: string; detail: string; color?: string }) {
  return (
    <div
      style={{
        background: "var(--color-surface-deep)",
        border: "1px solid var(--color-border)",
        padding: "14px 16px 10px",
        fontFamily: "var(--font-mc)",
        fontSize: "12px",
      }}
    >
      <div style={{ color: color ?? "var(--color-text-primary)", textShadow: "1px 1px 0px oklch(0.08 0.005 55)", marginBottom: 4 }}>
        {name}
      </div>
      <div style={{ color: "var(--color-text-dim)", fontSize: "10px", textShadow: "1px 1px 0px oklch(0.08 0.005 55)" }}>
        {detail}
      </div>
    </div>
  );
}

function TmuxDiagram() {
  return (
    <div style={{ maxWidth: 480 }}>
      <div
        style={{
          border: "2px solid var(--color-border)",
          background: "var(--color-surface)",
          padding: "16px",
        }}
      >
        <div style={{
          fontFamily: "var(--font-mc)",
          fontSize: "10px",
          color: "var(--color-text-dim)",
          textShadow: "1px 1px 0px oklch(0.08 0.005 55)",
          marginBottom: 12,
          paddingTop: 4,
        }}>
          tmux session "minecraft-code"
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 4 }}>
          <TmuxPane name="sidecar" detail="FastAPI" />
          <TmuxPane name="claude" detail="lfg" color="var(--color-garden)" />
          <TmuxPane name="minecraft" detail="gradlew" />
          <TmuxPane name="browser" detail="repl" color="var(--color-sea-bright)" />
          <TmuxPane name="shell" detail="bash" />
        </div>
      </div>
    </div>
  );
}

const SHOWCASE_SCHEMATICS = [
  { name: "Medieval Castle", category: "Castle", dims: "48x35x52" },
  { name: "Japanese Pagoda", category: "Tower", dims: "22x41x22" },
  { name: "Viking Longhouse", category: "House", dims: "32x18x14" },
  { name: "Wizard Tower", category: "Tower", dims: "16x45x16" },
  { name: "Roman Colosseum", category: "Castle", dims: "64x28x64" },
  { name: "Treehouse Village", category: "House", dims: "38x32x38" },
];

function SchematicsSection() {
  return (
    <section id="schematics" className="relative py-24 sm:py-32" style={{ background: "var(--color-surface)" }}>
      <div className="max-w-5xl mx-auto px-6">
        <FadeUp>
          <span className="section-label block mb-4">Schematics</span>
          <h2 className="section-heading mb-6">Build from the web</h2>
          <p className="feature-desc mb-12" style={{ maxWidth: "55ch" }}>
            Ask Claude to find schematics online. They get downloaded, stored in
            your Convex database, and are ready to place in your world
            via Litematica.
          </p>
        </FadeUp>

        {/* Pipeline demo */}
        <FadeUp delay={1}>
          <div className="terminal-block max-w-2xl mb-12">
            <div><span className="terminal-prompt">{">"} </span><span className="terminal-command">/browser-use get-schematics castle</span></div>
            <div className="mt-1"><span className="terminal-output">Searching minecraft-schematics.com... Found 12 results</span></div>
            <div><span className="terminal-output">Downloading "Medieval Castle" (48x35x52)...</span></div>
            <div><span className="terminal-output">Saved to Convex database</span></div>
            <div className="mt-2"><span className="terminal-prompt">{">"} </span><span className="terminal-command">/build list</span></div>
            <div className="mt-1"><span className="terminal-output">1. Medieval Castle — Castle — 48x35x52</span></div>
            <div><span className="terminal-output">2. Japanese Pagoda — Tower — 22x41x22</span></div>
            <div><span className="terminal-output">3. Viking Longhouse — House — 32x18x14</span></div>
            <div className="mt-2"><span className="terminal-prompt">{">"} </span><span className="terminal-command">/build "Medieval Castle"</span></div>
            <div className="mt-1"><span className="terminal-output">Downloaded — open Litematica (M+C) to place</span></div>
          </div>
        </FadeUp>

        {/* Example schematics */}
        <FadeUp delay={2}>
          <span className="section-label block mb-4">Example catalog</span>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 max-w-3xl">
            {SHOWCASE_SCHEMATICS.map((s) => (
              <div key={s.name} className="schematic-card flex flex-col gap-1">
                <span
                  style={{
                    fontFamily: "var(--font-mc)",
                    fontSize: "14px",
                    color: "var(--color-text-accent)",
                    textShadow: "1px 1px 0px oklch(0.10 0.005 55)",
                  }}
                  className="truncate"
                >
                  {s.name}
                </span>
                <span
                  style={{
                    fontFamily: "var(--font-mc)",
                    fontSize: "10px",
                    color: "var(--color-text-dim)",
                    textShadow: "1px 1px 0px oklch(0.08 0.005 55)",
                  }}
                >
                  {s.category} &middot; {s.dims}
                </span>
              </div>
            ))}
          </div>
        </FadeUp>
      </div>
    </section>
  );
}

/* ===== QUICK START ===== */

function QuickStartSection() {
  return (
    <section id="quickstart" className="relative py-24 sm:py-32">
      <div className="max-w-5xl mx-auto px-6">
        <FadeUp>
          <span className="section-label block mb-4">Quick start</span>
          <h2 className="section-heading mb-16">Four commands to go</h2>
        </FadeUp>

        <div className="grid grid-cols-1 gap-12 max-w-xl">
          {QUICKSTART_STEPS.map((step, i) => (
            <FadeUp key={step.title} delay={Math.min(i + 1, 3) as 1 | 2 | 3}>
              <div className="flex gap-6 items-start">
                <span className="step-number">{i + 1}</span>
                <div className="flex-1 pt-1">
                  <h3 className="feature-title mb-3">{step.title}</h3>
                  <div className="terminal-block">
                    <span className="terminal-prompt">$ </span>
                    <span className="terminal-command">{step.command}</span>
                  </div>
                </div>
              </div>
            </FadeUp>
          ))}
        </div>

        {/* Prerequisites note */}
        <FadeUp>
          <div className="mt-16 max-w-xl">
            <span className="section-label block mb-4">Prerequisites</span>
            <div className="terminal-block">
              <div><span className="terminal-comment">{"# Required"}</span></div>
              <div><span className="terminal-command">Java 21+</span><span className="terminal-comment">  brew install openjdk@21</span></div>
              <div><span className="terminal-command">tmux 3.x+</span><span className="terminal-comment">  brew install tmux</span></div>
              <div><span className="terminal-command">Python 3.12+</span><span className="terminal-comment">  brew install python@3.12</span></div>
              <div><span className="terminal-command">Node.js 18+</span><span className="terminal-comment">  brew install node</span></div>
              <div className="mt-2"><span className="terminal-comment">{"# Run minecraft-code doctor to check"}</span></div>
            </div>
          </div>
        </FadeUp>
      </div>
    </section>
  );
}

/* ===== FOOTER ===== */

function Footer() {
  return (
    <footer
      className="py-16 sm:py-24"
      style={{
        background: "var(--color-surface)",
        borderTop: "2px solid var(--color-border)",
      }}
    >
      <div className="max-w-5xl mx-auto px-6">
        <div className="flex flex-col sm:flex-row justify-between gap-12">
          {/* Left */}
          <div className="max-w-xs">
            <h3
              className="mb-4"
              style={{
                fontFamily: "var(--font-mc-title)",
                fontSize: "20px",
                color: "var(--color-text-accent)",
                textShadow: "2px 2px 0px oklch(0.10 0.01 55)",
                letterSpacing: "2px",
              }}
            >
              MINECRAFT CODE
            </h3>
            <p className="feature-desc" style={{ lineHeight: 1.8 }}>
              Claude Code, inside Minecraft. Open source under the MIT license.
            </p>
          </div>

          {/* Links */}
          <div className="flex gap-16">
            <div>
              <span className="section-label block mb-4">Project</span>
              <div className="flex flex-col gap-3">
                <a href="https://github.com/Cheggin/minecraft-use" target="_blank" rel="noopener noreferrer" className="nav-link">GitHub</a>
                <a href="https://www.npmjs.com/package/@reaganhsu/minecraft-code" target="_blank" rel="noopener noreferrer" className="nav-link">npm</a>
                <a href="https://github.com/Cheggin/minecraft-use/issues" target="_blank" rel="noopener noreferrer" className="nav-link">Issues</a>
                <a href="https://buymeacoffee.com/reaganhsu1b" target="_blank" rel="noopener noreferrer" className="nav-link" style={{ color: "var(--color-terracotta)" }}>Buy Me a Coffee</a>
              </div>
            </div>
            <div>
              <span className="section-label block mb-4">Stack</span>
              <div className="flex flex-col gap-3">
                <span className="nav-link" style={{ cursor: "default" }}>Fabric 1.21.1</span>
                <span className="nav-link" style={{ cursor: "default" }}>Claude Code</span>
                <span className="nav-link" style={{ cursor: "default" }}>Browser Use</span>
                <span className="nav-link" style={{ cursor: "default" }}>Convex</span>
              </div>
            </div>
          </div>
        </div>

        {/* Bottom */}
        <div
          className="mt-16 pt-6 flex flex-col sm:flex-row justify-between gap-4"
          style={{ borderTop: "1px solid var(--color-border)" }}
        >
          <span
            style={{
              fontFamily: "var(--font-mc)",
              fontSize: "10px",
              color: "var(--color-text-dim)",
              textShadow: "1px 1px 0px oklch(0.08 0.005 55)",
            }}
          >
            Not affiliated with Mojang Studios or Microsoft
          </span>
          <span
            style={{
              fontFamily: "var(--font-mc)",
              fontSize: "10px",
              color: "var(--color-text-dim)",
              textShadow: "1px 1px 0px oklch(0.08 0.005 55)",
            }}
          >
            Built by Reagan Hsu
          </span>
        </div>
      </div>
    </footer>
  );
}
