import { useState } from "react";

type Page = "menu" | "schematics" | "catalog" | "settings" | "about";

const SPLASH_TEXTS = [
  "Build anything!",
  "Now with schematics!",
  "Also try Litematica!",
  "Block by block!",
  "Powered by Convex!",
  "100% automated!",
  "NBT parsing!",
  "Ghost overlay mode!",
  "Craft your world!",
  "/build medieval-castle",
  "Open source!",
];

function getRandomSplash() {
  return SPLASH_TEXTS[Math.floor(Math.random() * SPLASH_TEXTS.length)];
}

export default function App() {
  const [page, setPage] = useState<Page>("menu");

  if (page === "menu") {
    return <MainMenu onNavigate={setPage} />;
  }

  return (
    <PageShell title={PAGE_TITLES[page]} onBack={() => setPage("menu")}>
      {page === "schematics" && <SchematicsPage />}
      {page === "catalog" && <CatalogPage />}
      {page === "settings" && <SettingsPage />}
      {page === "about" && <AboutPage />}
    </PageShell>
  );
}

const PAGE_TITLES: Record<Exclude<Page, "menu">, string> = {
  schematics: "My Schematics",
  catalog: "Browse Catalog",
  settings: "Settings",
  about: "About",
};

/* ===== MAIN MENU ===== */

function MainMenu({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [splash] = useState(getRandomSplash);

  return (
    <div className="relative w-screen h-screen overflow-hidden select-none mc-panorama">
      <div className="relative z-10 flex flex-col items-center justify-between h-full py-6">
        {/* Title */}
        <div className="flex flex-col items-center mt-12 relative">
          <TitleLogo />
          <div className="absolute -right-16 top-12">
            <span className="mc-splash text-base whitespace-nowrap block">
              {splash}
            </span>
          </div>
        </div>

        {/* Buttons */}
        <div className="flex flex-col items-center gap-2 w-full max-w-[420px] px-6">
          <McButton label="My Schematics" wide onClick={() => onNavigate("schematics")} />
          <McButton label="Browse Catalog" wide onClick={() => onNavigate("catalog")} />

          <div className="flex gap-2 w-full mt-2">
            <McButton label="Settings..." half onClick={() => onNavigate("settings")} />
            <McButton label="About" half onClick={() => onNavigate("about")} />
          </div>
        </div>

        {/* Footer */}
        <div className="flex justify-between items-end w-full px-4">
          <span className="mc-version">Minecraft-Use v0.1.0</span>
          <span className="mc-version opacity-60">
            Not affiliated with Mojang AB
          </span>
        </div>
      </div>
    </div>
  );
}

/* ===== PAGE SHELL ===== */

function PageShell({
  title,
  onBack,
  children,
}: {
  title: string;
  onBack: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="relative w-screen h-screen overflow-hidden select-none mc-dirt-panel">
      <div className="relative z-10 flex flex-col h-full">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b-3 border-black/40">
          <h2
            className="mc-subtitle leading-none"
            style={{ fontSize: "22px", color: "#ffffff" }}
          >
            {title}
          </h2>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto px-6 py-6">
          {children}
        </div>

        {/* Bottom bar */}
        <div className="px-6 py-4 flex justify-center">
          <McButton label="Done" onClick={onBack} style={{ minWidth: "200px" }} />
        </div>
      </div>
    </div>
  );
}

/* ===== PAGES ===== */

function SchematicsPage() {
  return (
    <div className="flex flex-col items-center gap-6">
      <p className="mc-page-text text-center">
        Your saved schematics will appear here.
      </p>
      <div className="grid grid-cols-1 gap-3 w-full max-w-[500px]">
        <SchematicCard
          name="Medieval Watchtower"
          category="Castle"
          dims="15x32x15"
          blocks={3200}
        />
        <SchematicCard
          name="Modern Villa"
          category="House"
          dims="30x12x25"
          blocks={8100}
        />
        <SchematicCard
          name="Dark Fantasy Tower"
          category="Tower"
          dims="12x45x12"
          blocks={5400}
        />
      </div>
      <p className="mc-page-text-dim text-center text-xs mt-2">
        Use /build &lt;name&gt; in-game to place a schematic
      </p>
    </div>
  );
}

function CatalogPage() {
  return (
    <div className="flex flex-col items-center gap-6">
      <div className="w-full max-w-[500px]">
        <input
          type="text"
          placeholder="Search schematics..."
          className="mc-input w-full"
        />
      </div>

      <div className="flex gap-2 flex-wrap justify-center">
        {["All", "Castle", "House", "Tower", "Medieval", "Modern"].map((cat) => (
          <button key={cat} className="mc-tag">
            {cat}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-3 w-full max-w-[500px]">
        <SchematicCard name="Medieval Watchtower" category="Castle" dims="15x32x15" blocks={3200} />
        <SchematicCard name="Stone Keep" category="Castle" dims="20x18x20" blocks={6100} />
        <SchematicCard name="Modern Villa" category="House" dims="30x12x25" blocks={8100} />
        <SchematicCard name="Dark Fantasy Tower" category="Tower" dims="12x45x12" blocks={5400} />
        <SchematicCard name="Cozy Cottage" category="House" dims="10x8x12" blocks={1200} />
        <SchematicCard name="Grand Cathedral" category="Medieval" dims="40x55x30" blocks={42000} />
      </div>
    </div>
  );
}

function SettingsPage() {
  return (
    <div className="flex flex-col gap-4 max-w-[500px] mx-auto">
      <SettingRow label="Blocks per tick" value="1000" />
      <SettingRow label="Ghost preview by default" value="ON" />
      <SettingRow label="Catalog keybind" value="K" />
      <SettingRow label="Sidecar port" value="8765" />
    </div>
  );
}

function AboutPage() {
  return (
    <div className="flex flex-col items-center gap-4 max-w-[500px] mx-auto">
      <h3
        className="mc-title leading-none"
        style={{ fontSize: "32px" }}
      >
        MINECRAFT-USE
      </h3>
      <p className="mc-page-text text-center">
        An in-game schematic catalog with thousands of builds,
        searchable and placeable without ever leaving Minecraft.
      </p>
      <div className="flex flex-col gap-2 mt-4 w-full">
        <InfoRow label="Version" value="0.1.0" />
        <InfoRow label="Mod" value="Fabric 1.21.1" />
        <InfoRow label="Java" value="21" />
        <InfoRow label="Sidecar" value="Python + FastAPI" />
        <InfoRow label="Backend" value="Convex" />
      </div>
      <p className="mc-page-text-dim text-center text-xs mt-4">
        Not affiliated with Mojang Studios or Microsoft
      </p>
    </div>
  );
}

/* ===== SHARED COMPONENTS ===== */

function TitleLogo() {
  return (
    <div className="flex flex-col items-center">
      <h1
        className="mc-title font-bold leading-none"
        style={{ fontSize: "clamp(36px, 8vw, 72px)" }}
      >
        MINECRAFT
      </h1>
      <span
        className="mc-subtitle leading-none -mt-1"
        style={{ fontSize: "clamp(16px, 3.5vw, 28px)" }}
      >
        — USE —
      </span>
      <p
        className="mt-3 text-center"
        style={{
          fontFamily: "var(--font-minecraft)",
          fontSize: "11px",
          color: "#bbbbbb",
          textShadow: "1px 1px 0px #3f3f3f",
        }}
      >
        Save, browse & build schematics in-game
      </p>
    </div>
  );
}

function McButton({
  label,
  wide,
  half,
  onClick,
  style,
}: {
  label: string;
  wide?: boolean;
  half?: boolean;
  onClick?: () => void;
  style?: React.CSSProperties;
}) {
  return (
    <button
      className={`mc-btn ${wide ? "w-full" : ""} ${half ? "mc-btn-half" : ""} flex items-center justify-center gap-2`}
      onClick={onClick}
      style={style}
    >
      {label}
    </button>
  );
}

function SchematicCard({
  name,
  category,
  dims,
  blocks,
}: {
  name: string;
  category: string;
  dims: string;
  blocks: number;
}) {
  return (
    <div className="mc-card flex items-center gap-4">
      {/* Placeholder thumbnail */}
      <div className="mc-thumbnail flex items-center justify-center shrink-0">
        <span style={{ fontSize: "20px" }}>
          {category === "Castle" ? "\u{1F3F0}" : category === "House" ? "\u{1F3E0}" : category === "Tower" ? "\u{1F5FC}" : "\u{1F3DB}"}
        </span>
      </div>
      <div className="flex flex-col gap-1 min-w-0">
        <span className="mc-card-title truncate">{name}</span>
        <span className="mc-card-meta">
          {category} &middot; {dims} &middot; {blocks.toLocaleString()} blocks
        </span>
      </div>
    </div>
  );
}

function SettingRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="mc-card flex items-center justify-between">
      <span className="mc-card-title">{label}</span>
      <span className="mc-card-meta">{value}</span>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between py-2 border-b border-white/10">
      <span className="mc-page-text">{label}</span>
      <span className="mc-page-text-dim">{value}</span>
    </div>
  );
}
