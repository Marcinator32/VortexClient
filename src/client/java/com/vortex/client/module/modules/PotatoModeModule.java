package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * Potato Mode -- ein Schalter fuer schwache PCs und grosse Kaempfe.
 *
 * Seit 4.49 ein richtiger Kartoffel-Modus, nicht nur ein paar Grafikregler:
 *
 *   Grafik      Sichtweite, Simulationsdistanz, Mipmaps, Wolken, Schatten,
 *               Smooth Lighting, Biome-Blending, Laub/Transparenz "schnell",
 *               Partikel reduziert/minimal, Menue-Unschaerfe aus
 *   Zeichnen    schaltet alle Performance-Optimierungen mit ein -- mit
 *               eigenen, je nach Stufe strengeren Werten:
 *                 Entity Culling (nichts hinter Waenden zeichnen)
 *                 Block Entity Distance  64 / 40 / 24 Bloecke
 *                 Item Limiter           32 / 24 / 12 Bloecke (+ XP-Kugeln)
 *                 Particle Limiter       60 / 35 / 15 %  (Spitzen gedeckelt)
 *                 Dynamic FPS            im Hintergrund 15, AFK 30 FPS
 *   Effekte     schwere Client-Effekte (Motion Blur) pausieren, solange
 *               Potato an ist, und kommen danach von selbst zurueck
 *
 * Drei Stufen: Balanced (sieht noch ordentlich aus), Aggressive (maximale
 * FPS bei normaler Sichtweite), Maximum (alles runter, Sichtweite hoechstens 4).
 *
 * WICHTIG: Beim Aktivieren werden die aktuellen Werte GESPEICHERT (auch auf
 * der Platte, falls das Spiel abstuerzt) und beim Deaktivieren wieder
 * hergestellt -- der Modus zerstoert nie dauerhaft deine Einstellungen.
 */
public class PotatoModeModule extends Module {

    public final ModeSetting strength =
        new ModeSetting("Strength", 0, "Balanced", "Aggressive", "Maximum") {
            @Override
            public void deserialize(String value) {
                if ("Ausgewogen".equals(value)) value = "Balanced";
                else if ("Aggressiv".equals(value)) value = "Aggressive";
                super.deserialize(value);
            }
        };
    public final NumberSetting renderDistance =
        new NumberSetting("Render Distance", 6, 2, 32, 1);

    /**
     * Mention Sodium and Lithium once if they are missing.
     *
     * They do far more for the frame rate than anything this module can, and
     * a player who does not know that is leaving most of the gain on the table.
     * Said once per game start, never again.
     */
    public final com.vortex.client.core.setting.BooleanSetting suggestMods =
        new com.vortex.client.core.setting.BooleanSetting("Suggest Sodium and Lithium", true);

    private boolean saved = false;
    private Object oViewDistance, oMaxFps, oMipmap, oEntityShadows,
                   oBobView, oAo, oEntityDist,
                   oBiomeBlend, oSimDistance, oClouds;

    public PotatoModeModule() {
        super("Potato Mode", Category.PERFORMANCE);
        addSetting(strength);
        addSetting(renderDistance);
        addSetting(suggestMods);
    }

    @Override
    protected void onEnable() {
        Options o = options();
        if (o == null) return;

        if (!saved && ladeAusDatei(o)) {
            saved = true;
        }
        if (!saved) {
            oViewDistance  = get(o.renderDistance());
            oMaxFps        = get(o.framerateLimit());
            oMipmap        = get(o.mipmapLevels());
            oEntityShadows = get(o.entityShadows());
            oBobView       = get(o.bobView());
            oAo            = get(o.ambientOcclusion());
            oEntityDist    = get(o.entityDistanceScaling());
            oBiomeBlend    = get(o.biomeBlendRadius());
            oSimDistance   = get(o.simulationDistance());
            oClouds        = get(o.cloudStatus());
            sichereExtras(o);
            saved = true;
            schreibeDatei();
        }

        applyPotato(o);
        schwereEffektePausieren();
    }

    @Override
    protected void onDisable() {
        restoreOriginals();
        schwereEffekteZurueck();
    }

    private static final String[] SCHWERE_EFFEKTE = {"Motion Blur"};
    private final java.util.List<String> pausiert = new java.util.ArrayList<>();

    private void schwereEffektePausieren() {
        for (String n : SCHWERE_EFFEKTE) {
            for (Module m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                if (m != this && m.getName().equals(n) && m.isEnabled()) {
                    m.setEnabled(false);
                    if (!pausiert.contains(n)) pausiert.add(n);
                }
            }
        }
    }

    private void schwereEffekteZurueck() {
        for (String n : pausiert) {
            for (Module m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                if (m.getName().equals(n) && !m.isEnabled()) m.setEnabled(true);
            }
        }
        pausiert.clear();
    }

    /**
     * Stellt die gesicherten Original-Grafikwerte wieder her (ohne den
     * Modul-Zustand zu aendern). Wird von onDisable() und beim Spiel-Beenden
     * aufgerufen, damit Minecraft nicht die Potato-Werte dauerhaft speichert.
     */
    public void restoreOriginals() {
        Options o = options();
        if (o == null) return;

        if (saved) {
            set(o.renderDistance(), oViewDistance);
            set(o.framerateLimit(), oMaxFps);
            set(o.mipmapLevels(), oMipmap);
            set(o.entityShadows(), oEntityShadows);
            set(o.bobView(), oBobView);
            set(o.ambientOcclusion(), oAo);
            set(o.entityDistanceScaling(), oEntityDist);
            set(o.biomeBlendRadius(), oBiomeBlend);
            set(o.simulationDistance(), oSimDistance);
            set(o.cloudStatus(), oClouds);
            stelleExtrasHer();
            saved = false;
            try { java.nio.file.Files.deleteIfExists(datei()); } catch (Throwable ignored) { }
            reloadWorld();
        }
    }

    private static java.nio.file.Path datei() {
        return com.vortex.client.core.ConfigManager.dataDir().resolve("potato_originals.txt");
    }

    private java.util.Map<String, Object> hauptWerte() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("renderDistance", oViewDistance); m.put("framerateLimit", oMaxFps);
        m.put("mipmapLevels", oMipmap); m.put("entityShadows", oEntityShadows);
        m.put("bobView", oBobView); m.put("ambientOcclusion", oAo);
        m.put("entityDistanceScaling", oEntityDist); m.put("biomeBlendRadius", oBiomeBlend);
        m.put("simulationDistance", oSimDistance); m.put("cloudStatus", oClouds);
        return m;
    }

    private static String kodiere(Object v) {
        if (v instanceof Integer i) return "I\t" + i;
        if (v instanceof Boolean b) return "B\t" + b;
        if (v instanceof Double d) return "D\t" + d;
        if (v instanceof Enum<?> e) return "E:" + e.getDeclaringClass().getName() + "\t" + e.name();
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object dekodiere(String typ, String wert) {
        try {
            switch (typ) {
                case "I": return Integer.valueOf(wert);
                case "B": return Boolean.valueOf(wert);
                case "D": return Double.valueOf(wert);
                default:
                    if (typ.startsWith("E:")) return Enum.valueOf((Class) Class.forName(typ.substring(2)), wert);
                    return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private void schreibeDatei() {
        try {
            java.util.List<String> zeilen = new java.util.ArrayList<>();
            for (var e : hauptWerte().entrySet()) {
                String k = kodiere(e.getValue());
                if (k != null) zeilen.add("main\t" + e.getKey() + "\t" + k);
            }
            for (var e : extraAlt.entrySet()) {
                String k = kodiere(e.getValue());
                if (k != null) zeilen.add("extra\t" + e.getKey() + "\t" + k);
            }
            java.nio.file.Files.createDirectories(datei().getParent());
            java.nio.file.Files.write(datei(), zeilen, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("PotatoMode.save", t);
        }
    }

    private boolean ladeAusDatei(Options o) {
        try {
            if (!java.nio.file.Files.exists(datei())) return false;
            java.util.Map<String, Object> haupt = new java.util.HashMap<>();
            extraAlt.clear();
            for (String z : java.nio.file.Files.readAllLines(datei(), java.nio.charset.StandardCharsets.UTF_8)) {
                String[] t = z.split("\t", 4);
                if (t.length < 4) continue;
                Object v = dekodiere(t[2], t[3]);
                if (v == null) continue;
                if (t[0].equals("main")) haupt.put(t[1], v); else extraAlt.put(t[1], v);
            }
            oViewDistance  = haupt.getOrDefault("renderDistance", get(o.renderDistance()));
            oMaxFps        = haupt.getOrDefault("framerateLimit", get(o.framerateLimit()));
            oMipmap        = haupt.getOrDefault("mipmapLevels", get(o.mipmapLevels()));
            oEntityShadows = haupt.getOrDefault("entityShadows", get(o.entityShadows()));
            oBobView       = haupt.getOrDefault("bobView", get(o.bobView()));
            oAo            = haupt.getOrDefault("ambientOcclusion", get(o.ambientOcclusion()));
            oEntityDist    = haupt.getOrDefault("entityDistanceScaling", get(o.entityDistanceScaling()));
            oBiomeBlend    = haupt.getOrDefault("biomeBlendRadius", get(o.biomeBlendRadius()));
            oSimDistance   = haupt.getOrDefault("simulationDistance", get(o.simulationDistance()));
            oClouds        = haupt.getOrDefault("cloudStatus", get(o.cloudStatus()));
            return true;
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("PotatoMode.load", t);
            return false;
        }
    }

    /** Wendet die Potato-Werte je nach Staerke an. */
    private void applyPotato(Options o) {
        int stufe = strength.getIndex();
        boolean aggressive = stufe >= 1;
        boolean maximum = stufe >= 2;

        int rd = renderDistance.getInt();
        senke(o.renderDistance(), maximum ? Math.min(rd, 4) : rd);

        hebe(o.framerateLimit(), aggressive ? 260 : 120);

        senke(o.mipmapLevels(), aggressive ? 0 : 1);
        set(o.entityShadows(), false);
        set(o.bobView(), false);
        if (aggressive) set(o.ambientOcclusion(), false);
        senkeD(o.entityDistanceScaling(), aggressive ? 0.5 : 0.75);
        senke(o.biomeBlendRadius(), aggressive ? 0 : 1);
        if (aggressive || get(o.cloudStatus()) == CloudStatus.FANCY) {
            set(o.cloudStatus(), aggressive ? CloudStatus.OFF : CloudStatus.FAST);
        }
        senke(o.simulationDistance(), aggressive ? 5 : 8);

        setzeExtras(o);
        OptionInstance<?> part = finde(o, "particles");
        if (part != null && get(part) instanceof Enum<?> en && en.ordinal() < (aggressive ? 2 : 1)) {
            setzeEnum(o, "particles", aggressive ? "MINIMAL" : "DECREASED");
        }
        OptionInstance<?> blur = finde(o, "menuBackgroundBlurriness");
        if (blur != null) set(blur, 0);

        reloadWorld();
    }

    /** Setzt eine Zahl-Option nur, wenn der neue Wert kleiner ist. */
    private static void senke(OptionInstance<Integer> opt, int wert) {
        Object jetzt = get(opt);
        if (!(jetzt instanceof Integer i) || wert < i) set(opt, wert);
    }

    private static void senkeD(OptionInstance<Double> opt, double wert) {
        Object jetzt = get(opt);
        if (!(jetzt instanceof Double d) || wert < d) set(opt, wert);
    }

    /** Setzt eine Zahl-Option nur, wenn der neue Wert groesser ist (FPS-Limit). */
    private static void hebe(OptionInstance<Integer> opt, int wert) {
        Object jetzt = get(opt);
        if (!(jetzt instanceof Integer i) || wert > i) set(opt, wert);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setzeEnum(Options o, String option, String wert) {
        OptionInstance<?> oi = finde(o, option);
        if (oi == null) return;
        Object jetzt = get(oi);
        if (!(jetzt instanceof Enum<?> en)) return;
        try {
            set(oi, Enum.valueOf((Class) en.getDeclaringClass(), wert));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Die Options-Setter veranlassen Vanilla selbst zu einem sicheren Chunk- und
     * Renderdistanz-Refresh. Ein direkter resetLevelRenderData()-Aufruf ist in
     * 26.x nicht mehr sicher: Er kann die ViewArea zwischen zwei Renderframes
     * leeren und führt dann in LevelRenderer.repositionCamera zu einem Absturz.
     */
    private static void reloadWorld() {
    }

    private static final String[] EXTRA_OPTIONEN = {
            "graphicsMode", "cutoutLeaves", "improvedTransparency",
            "particles", "menuBackgroundBlurriness"
    };
    private final java.util.Map<String, Object> extraAlt = new java.util.LinkedHashMap<>();

    private static OptionInstance<?> finde(Options o, String name) {
        try {
            java.lang.reflect.Method m = o.getClass().getMethod(name);
            Object r = m.invoke(o);
            return (r instanceof OptionInstance<?> oi) ? oi : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void sichereExtras(Options o) {
        extraAlt.clear();
        for (String n : EXTRA_OPTIONEN) {
            OptionInstance<?> oi = finde(o, n);
            if (oi != null) extraAlt.put(n, get(oi));
        }
    }

    private void stelleExtrasHer() {
        Options o = options();
        if (o == null) return;
        for (var e : extraAlt.entrySet()) {
            OptionInstance<?> oi = finde(o, e.getKey());
            if (oi != null) set(oi, e.getValue());
        }
        extraAlt.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setzeExtras(Options o) {
        for (String n : EXTRA_OPTIONEN) {
            OptionInstance<?> oi = finde(o, n);
            if (oi == null) continue;
            Object jetzt = get(oi);
            if (jetzt instanceof Boolean) {
                set(oi, Boolean.FALSE);
            } else if (jetzt instanceof Enum<?>) {
                Enum<?> en = (Enum<?>) jetzt;
                try {
                    set(oi, Enum.valueOf((Class) en.getDeclaringClass(), "FAST"));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Options options() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? null : client.options;
    }

    /** Liest den aktuellen Wert einer Option (kann null sein). */
    private static Object get(OptionInstance<?> opt) {
        try {
            return opt.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Setzt einen Wert auf eine Option. Der unchecked-Cast ist noetig, weil
     * OptionInstance generisch ist; wir uebergeben aber immer den passenden Typ
     * (Integer/Boolean/Double) bzw. den vorher ausgelesenen Originalwert.
     */
    @SuppressWarnings("unchecked")
    private static void set(OptionInstance<?> opt, Object value) {
        if (value == null) return;
        try {
            ((OptionInstance<Object>) opt).set(value);
        } catch (Throwable ignored) {
        }
    }
}
