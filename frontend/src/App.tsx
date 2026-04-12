import { useState } from "react";
import { useQuery } from "convex/react";
import { api } from "../convex/_generated/api";

type Page = "menu" | "schematics" | "catalog" | "settings" | "about";

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

function getRandomSplash() {
  return SPLASH_TEXTS[Math.floor(Math.random() * SPLASH_TEXTS.length)];
}

export default function App() {
  const [page, setPage] = useState<Page>("menu");

  const navigate = (target: Page) => {
    console.log(`[navigate] ${page} -> ${target}`);
    // Ping the dev server so the request appears in logs
    fetch(`/__log?nav=${page}-to-${target}`).catch(() => {});
    setPage(target);
  };

  if (page === "menu") {
    return <MainMenu onNavigate={navigate} />;
  }

  return (
    <PageShell title={PAGE_TITLES[page]} onBack={() => navigate("menu")}>
      {page === "schematics" && <SchematicsPage />}
      {page === "catalog" && <CatalogPage />}
      {page === "settings" && <SettingsPage />}
      {page === "about" && <AboutPage />}
    </PageShell>
  );
}

const PAGE_TITLES: Record<Exclude<Page, "menu">, string> = {
  schematics: "Schematics",
  catalog: "Schematic Catalog",
  settings: "Settings",
  about: "About",
};

/* ===== MAIN MENU ===== */

function MainMenu({ onNavigate }: { onNavigate: (page: Page) => void }) {
  const [splash] = useState(getRandomSplash);

  return (
    <div className="relative w-screen h-screen overflow-hidden select-none">
      {/* Background image */}
      <img
        src="/minecraft-bg.webp"
        alt=""
        className="absolute inset-0 w-full h-full object-cover"
        style={{ imageRendering: "auto", filter: "blur(3px) brightness(0.7)", transform: "scale(1.05)" }}
      />
      <div className="absolute inset-0 bg-black/25" />

      <div className="relative z-10 flex flex-col items-center justify-between h-full py-4">
        {/* Title */}
        <div className="flex flex-col items-center mt-[4vh] sm:mt-[8vh] relative">
          <TitleLogo />
          <div className="absolute -right-4 sm:-right-28 -bottom-6 sm:top-20">
            <span className="mc-splash text-sm sm:text-2xl whitespace-nowrap block">
              {splash}
            </span>
          </div>
        </div>

        {/* Buttons */}
        <div className="flex flex-col items-center gap-2 sm:gap-3 w-[90vw] sm:w-[75vw] max-w-[800px]">
          <McButton label="My Schematics" wide onClick={() => onNavigate("schematics")} />
          <McButton label="Browse Catalog" wide onClick={() => onNavigate("catalog")} />

          <div className="flex gap-2 sm:gap-3 w-full mt-1 sm:mt-2">
            <McButton label="Settings..." half onClick={() => onNavigate("settings")} />
            <McButton label="About" half onClick={() => onNavigate("about")} />
          </div>
        </div>

        {/* Footer */}
        <div className="flex justify-between items-end w-full px-3 sm:px-4">
          <span className="mc-version">Minecraft-Use v0.1.0</span>
          <span className="mc-version opacity-60 hidden sm:inline">
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
    <div className="relative w-screen h-screen overflow-hidden select-none">
      <img
        src="/minecraft-bg.webp"
        alt=""
        className="absolute inset-0 w-full h-full object-cover"
        style={{ imageRendering: "auto", filter: "blur(3px) brightness(0.55)", transform: "scale(1.05)" }}
      />
      <div className="absolute inset-0 bg-black/30" />

      <div className="relative z-10 flex flex-col h-full">
        <div className="flex items-center justify-center px-6 pt-6 pb-4">
          <h2
            className="mc-title leading-none"
            style={{ fontSize: "24px" }}
          >
            {title}
          </h2>
        </div>

        <div className="flex-1 overflow-y-auto px-3 sm:px-6 py-4">
          {children}
        </div>

        <div className="px-4 sm:px-6 py-4 sm:py-5 flex justify-center">
          <McButton label="Done" onClick={onBack} style={{ minWidth: "min(400px, 80vw)" }} />
        </div>
      </div>
    </div>
  );
}

/* ===== PAGES ===== */

function SchematicsPage() {
  const schematics = useQuery(api.schematics.listSchematics, { count: 50 });

  return (
    <div className="flex flex-col items-center gap-6">
      <p className="mc-page-text text-center">
        Schematics found and downloaded by Claude appear here.
      </p>
      <div className="grid grid-cols-1 gap-3 w-full max-w-[620px]">
        {schematics === undefined ? (
          <p className="mc-page-text-dim text-center py-8">Loading...</p>
        ) : schematics.length === 0 ? (
          <p className="mc-page-text-dim text-center py-8">No schematics yet</p>
        ) : (
          schematics.map((s) => (
            <SchematicCard
              key={s._id}
              name={s.name}
              category={s.category ?? "Uncategorized"}
              dimensions={s.dimensions}
            />
          ))
        )}
      </div>
      <p className="mc-page-text-dim text-center text-xs mt-2">
        Use /build &lt;name&gt; in-game to place a schematic
      </p>
    </div>
  );
}

function CatalogPage() {
  const [search, setSearch] = useState("");
  const [activeCategory, setActiveCategory] = useState("All");

  const categories = useQuery(api.schematics.getCategories, {});

  const searchResults = useQuery(
    api.schematics.searchSchematics,
    search.length > 0
      ? {
          query: search,
          category: activeCategory !== "All" ? activeCategory : undefined,
        }
      : "skip"
  );

  const categoryResults = useQuery(
    api.schematics.listByCategory,
    activeCategory !== "All" && search.length === 0
      ? { category: activeCategory }
      : "skip"
  );

  const allResults = useQuery(
    api.schematics.listSchematics,
    activeCategory === "All" && search.length === 0
      ? { count: 50 }
      : "skip"
  );

  const displayItems = search.length > 0
    ? searchResults
    : activeCategory !== "All"
      ? categoryResults
      : allResults;

  const allCategories = ["All", ...(categories ?? [])];

  return (
    <div className="flex flex-col items-center gap-6">
      <div className="w-full max-w-[620px]">
        <input
          type="text"
          placeholder="Search schematics..."
          className="mc-input w-full"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <div className="flex gap-2 flex-wrap justify-center">
        {allCategories.map((cat) => (
          <button
            key={cat}
            className={`mc-tag ${activeCategory === cat ? "mc-tag-active" : ""}`}
            onClick={() => setActiveCategory(cat)}
          >
            {cat}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-3 w-full max-w-[620px]">
        {displayItems === undefined ? (
          <p className="mc-page-text-dim text-center py-8">Loading...</p>
        ) : displayItems.length === 0 ? (
          <p className="mc-page-text-dim text-center py-8">No schematics found</p>
        ) : (
          displayItems.map((item) => (
            <SchematicCard
              key={item._id}
              name={item.name}
              category={item.category ?? "Uncategorized"}
              dimensions={item.dimensions}
            />
          ))
        )}
      </div>
    </div>
  );
}

function SettingsPage() {
  return (
    <div className="flex flex-col items-center gap-6 max-w-[620px] mx-auto">
      <p className="mc-page-text text-center">
        Settings are configured in-game via the mod.
      </p>
      <p className="mc-page-text-dim text-center">
        This page is a placeholder — nothing here is wired up yet.
      </p>
    </div>
  );
}

function AboutPage() {
  const schematics = useQuery(api.schematics.listSchematics, { count: 200 });
  const schematicCount = schematics?.length ?? 0;

  return (
    <div className="flex flex-col items-center gap-4 max-w-[620px] mx-auto">
      <p className="mc-page-text text-center">
        Use Claude Code inside Minecraft. Spawn villagers that
        can code, browse the web, find schematics, and build
        them in your world — all from in-game chat.
      </p>
      <div className="grid grid-cols-2 gap-2 w-full mt-2">
        <button className="mc-btn">Version: 0.1.0</button>
        <button className="mc-btn">Mod: Fabric 1.21.1</button>
        <button className="mc-btn">AI: Claude Code</button>
        <button className="mc-btn">Web: Browser Use</button>
        <button className="mc-btn">Backend: Convex</button>
        <button className="mc-btn">Schematics: {schematicCount}</button>
      </div>
      <p className="mc-page-text-dim text-center text-xs mt-2">
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
        style={{ fontSize: "clamp(40px, 12vw, 120px)" }}
      >
        MINECRAFT
      </h1>
      <span
        className="mc-subtitle leading-none mt-1 sm:mt-2"
        style={{ fontSize: "clamp(18px, 5vw, 56px)" }}
      >
        — USE —
      </span>
      <p
        className="mt-2 sm:mt-4 text-center px-4"
        style={{
          fontFamily: "var(--font-minecraft)",
          fontSize: "clamp(10px, 2.5vw, 14px)",
          color: "#cccccc",
          textShadow: "1px 1px 0px #3f3f3f",
        }}
      >
        Claude Code, inside Minecraft
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
  dimensions,
}: {
  name: string;
  category: string;
  dimensions?: { width: number; height: number; length: number };
}) {
  const dims = dimensions
    ? `${dimensions.width}x${dimensions.height}x${dimensions.length}`
    : "unknown";
  const blocks = dimensions
    ? dimensions.width * dimensions.height * dimensions.length
    : 0;

  return (
    <div className="mc-card flex items-center gap-4">
      <div className="mc-thumbnail flex items-center justify-center shrink-0">
        <span style={{ fontSize: "20px" }}>
          {category === "Castle"
            ? "\u{1F3F0}"
            : category === "House"
              ? "\u{1F3E0}"
              : category === "Tower"
                ? "\u{1F5FC}"
                : "\u{1F3DB}"}
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
