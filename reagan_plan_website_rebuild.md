# Minecraft-Use Website Rebuild Plan

## Goal
Recreate the minecraft-use frontend as a stunning, production-grade landing page + app using the Mediterranean garden pixel art as the visual centerpiece.

## Current State
- Vite + React 19 + TypeScript + Tailwind CSS v4 + Convex backend
- Minecraft title-screen style SPA with schematics browser
- Custom Minecraft fonts (Minecraftia, Minecrafter)
- Stone-textured buttons, pixel art aesthetic

## Design Vision
- **Hero**: Full-bleed Mediterranean garden image as immersive background
- **Aesthetic**: Merge Minecraft pixel art charm with polished modern SaaS design
- **Typography**: Keep Minecraft fonts for brand identity, add a complementary display font
- **Color palette**: Extract from the garden image — deep Mediterranean blues, warm terracotta, lush greens, stone cream
- **Layout**: Landing page hero -> features section -> commands showcase -> schematics browser -> footer
- **Vibe**: "A beautiful garden where AI agents live in your Minecraft world"

## Pages/Sections
1. **Hero** — Full-screen garden background, title, tagline, CTA (npm install command)
2. **Features** — 3-4 cards showing key capabilities (AI agents, schematics, browser use, agent-to-agent chat)
3. **Commands** — Interactive command showcase with terminal-style display
4. **Architecture** — Visual diagram of how it works (tmux bridge)
5. **Schematics Browser** — Keep existing Convex-powered catalog (but restyled)
6. **Quick Start** — Getting started steps
7. **Footer** — Links, MIT license, "not affiliated with Mojang"

## Tech Decisions
- Keep Vite + React 19 + Convex (no framework change)
- Keep Tailwind v4
- Keep existing Convex queries (schematics)
- New image: `/public/hero-garden.png` as hero/background
- Smooth scroll single-page layout with nav

## Skills to Use
- `startup-harness:impeccable` — Core design quality
- `startup-harness:website-creation` — Production website patterns
- `startup-harness:typeset` — Typography refinement
- `startup-harness:colorize` — Strategic color from the garden palette
- `startup-harness:animate` — Tasteful scroll animations
