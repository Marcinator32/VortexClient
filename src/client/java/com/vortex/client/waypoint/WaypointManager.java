package com.vortex.client.waypoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltung der eigenen Marker (Waypoints).
 *
 * Ein Waypoint gehoert zu genau einer WELT (siehe {@link WaypointWorlds}) und
 * einer Dimension. Angezeigt wird nur, was zur Welt und Dimension passt, in der
 * man gerade steht -- Marker vom einen Server tauchen also nie auf einem anderen
 * auf, und Lobby und Survival desselben Netzwerks bleiben getrennt.
 *
 * Gespeichert wird seit 4.94 im Format "v2", eine Zeile je Marker:
 *   Name|x|y|z|farbe|welt|dimension|sichtbar|art|bloecke|tracer|symbol
 * Aeltere Dateien ("liste") werden beim Laden einmal umgestellt.
 */
public final class WaypointManager {

    /** Art des Markers: Vorschlagsfarbe und Filter in der Verwaltung. */
    public enum Kind {
        ALLGEMEIN("Waypoint", 0xFF4C8BF5),
        BASE("Base", 0xFFFF5555),
        LAGER("Storage", 0xFFFFAA00),
        PORTAL("Portal", 0xFFAA66FF),
        FARM("Farm", 0xFF55FF7A),
        GEFAHR("Danger", 0xFFFF66C4),
        BLOCK("Blocks", 0xFF22D3D3),
        TOD("Death", 0xFFE0E0E0);

        public final String label;
        public final int color;
        Kind(String label, int color) { this.label = label; this.color = color; }
    }

    /** Ein einzelner Marker. */
    public static final class Waypoint {
        public String name;
        public int x, y, z;
        public int color;
        /** Id der Welt (siehe WaypointWorlds). */
        public String welt;
        /** Dimension, z. B. "minecraft:the_nether". */
        public String dim;
        public boolean visible = true;
        public Kind kind = Kind.ALLGEMEIN;
        /** Ein oder zwei Zeichen im Marker; leer = erster Buchstabe des Namens. */
        public String symbol = "";

        /** Linie vom Fadenkreuz zu diesem Marker (einzeln schaltbar). */
        public boolean tracer = false;

        /**
         * Zusaetzlich markierte Bloecke, die zu diesem Marker gehoeren (etwa die
         * Stellen einer Falle). Sie werden nur in der Naehe eingeblendet.
         */
        public final List<net.minecraft.core.BlockPos> blocks = new ArrayList<>();

        public Waypoint(String name, int x, int y, int z, int color, String welt, String dim) {
            this.name = name;
            this.x = x; this.y = y; this.z = z;
            this.color = color;
            this.welt = welt;
            this.dim = dim;
        }

        /** Schluessel "welt|dimension", wie ihn {@link #matches(Waypoint, String)} erwartet. */
        public String schluessel() {
            return welt + "|" + dim;
        }

        /** Was im Marker steht. */
        public String zeichen() {
            if (symbol != null && !symbol.isBlank()) return symbol.trim();
            if (name == null || name.isBlank()) return "?";
            return name.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static final List<Waypoint> LIST = new ArrayList<>();

    /** Farben, die neuen Markern der Reihe nach zugewiesen werden. */
    public static final int[] PALETTE = {
        0xFF4C8BF5, 0xFF55FF7A, 0xFFFFAA00, 0xFFFF5555,
        0xFFAA66FF, 0xFF22D3D3, 0xFFFF66C4, 0xFFFFFF55,
        0xFFFFFFFF, 0xFF8B5CF6, 0xFFF97316, 0xFF10B981
    };
    private static int nextColor = 0;

    private WaypointManager() {}

    public static synchronized List<Waypoint> all() {
        return LIST;
    }

    /** Naechste Farbe aus der Palette (fuer neue Marker). */
    public static synchronized int naechsteFarbe() {
        return PALETTE[(nextColor++) % PALETTE.length];
    }

    /** Sichtbare Marker im Schluessel "welt|dimension". */
    public static synchronized List<Waypoint> visibleIn(String worldKey) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : LIST) {
            if (!w.visible) continue;
            if (worldKey != null && !matches(w, worldKey)) continue;
            out.add(w);
        }
        return out;
    }

    /** Alle Marker einer Welt (sichtbar oder nicht), optional nur einer Dimension. */
    public static synchronized List<Waypoint> inWelt(String welt, String dim) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : LIST) {
            if (!java.util.Objects.equals(w.welt, welt)) continue;
            if (dim != null && !dim.equals(w.dim)) continue;
            out.add(w);
        }
        return out;
    }

    public static synchronized int anzahlIn(String welt) {
        int n = 0;
        for (Waypoint w : LIST) if (java.util.Objects.equals(w.welt, welt)) n++;
        return n;
    }

    static synchronized void alleLoeschenIn(String welt) {
        LIST.removeIf(w -> java.util.Objects.equals(w.welt, welt));
    }

    /** Alle Marker einer Welt in eine andere verschieben. @return wie viele */
    public static synchronized int verschieben(String vonWelt, String nachWelt) {
        int n = 0;
        for (Waypoint w : LIST) {
            if (java.util.Objects.equals(w.welt, vonWelt)) {
                w.welt = nachWelt;
                n++;
            }
        }
        return n;
    }

    /** Fuegt einen fertigen Marker hinzu. */
    public static synchronized void add(Waypoint w) {
        if (w != null) LIST.add(0, w);
    }

    /** Gehoert der Marker zum Schluessel "welt|dimension"? */
    public static boolean matches(Waypoint w, String worldKey) {
        if (w == null) return false;
        if (worldKey == null) return true;
        return worldKey.equals(w.schluessel());
    }

    public static boolean matches(Waypoint w, WaypointWorlds.Hier hier) {
        return w != null && hier != null && hier.welt().id.equals(w.welt) && hier.dim().equals(w.dim);
    }

    /** Marker an einer Stelle anlegen; ohne Art bekommt er die naechste Palettenfarbe. */
    public static synchronized Waypoint add(String name, int x, int y, int z, String welt, String dim, Kind kind) {
        int color = (kind == null || kind == Kind.ALLGEMEIN) ? naechsteFarbe() : kind.color;
        Waypoint w = new Waypoint(name, x, y, z, color, welt, dim);
        w.kind = kind == null ? Kind.ALLGEMEIN : kind;
        LIST.add(0, w);
        return w;
    }

    /** Naechstgelegener sichtbarer Marker (fuer die HUD-Zeile). */
    public static synchronized Waypoint nearest(WaypointWorlds.Hier hier, double px, double py, double pz) {
        Waypoint best = null;
        double bestSq = Double.MAX_VALUE;
        for (Waypoint w : LIST) {
            if (!w.visible || !matches(w, hier)) continue;
            double dx = w.x - px, dy = w.y - py, dz = w.z - pz;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < bestSq) { bestSq = sq; best = w; }
        }
        return best;
    }

    public static synchronized boolean remove(String name) {
        return LIST.removeIf(w -> w.name.equalsIgnoreCase(name));
    }

    public static synchronized void remove(Waypoint w) {
        LIST.remove(w);
    }

    public static synchronized Waypoint find(String name) {
        for (Waypoint w : LIST) {
            if (w.name.equalsIgnoreCase(name)) return w;
        }
        return null;
    }

    public static synchronized void clear() {
        LIST.clear();
    }

    /* ---------------------------------------------------------------- Teilen */

    private static final String TEIL_NEU = "vortex-wp2:";
    private static final String TEIL_ALT = "vortex-wp:";

    /** Ein Marker als Text zum Teilen, mit seinen markierten Bloecken (ohne Welt). */
    public static String export(Waypoint w) {
        if (w == null) return "";
        return TEIL_NEU + zeile(w, false);
    }

    /** Steht ein geteilter Marker in diesem Text? */
    public static boolean istGeteilt(String text) {
        if (text == null) return false;
        String t = text.trim();
        return t.startsWith(TEIL_NEU) || t.startsWith(TEIL_ALT);
    }

    /**
     * Liest einen geteilten Marker und legt ihn in der angegebenen Welt an. Die
     * Welt des Absenders bedeutet hier nichts; die Dimension bleibt, wenn sie
     * mitkam.
     *
     * @return der neue Marker, oder null, wenn der Text keiner war
     */
    public static Waypoint importFrom(String text, String welt, String dim) {
        if (text == null) return null;
        String t = text.trim();
        try {
            Waypoint w = null;
            if (t.startsWith(TEIL_NEU)) {
                w = ausZeile(t.substring(TEIL_NEU.length()));
            } else if (t.startsWith(TEIL_ALT)) {
                w = ausAlterZeile(t.substring(TEIL_ALT.length()), false);
            }
            if (w == null) return null;
            w.welt = welt;
            if (w.dim == null || w.dim.isEmpty() || !w.dim.contains(":")) w.dim = dim;
            add(w);
            return w;
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("WaypointManager.import", e);
            return null;
        }
    }

    /* ---------------------------------------------------------------- Speichern */

    /** Kennung im Konfig-Feld fuer das aktuelle Format. */
    public static final String FORMAT = "v2";

    public static synchronized String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Waypoint w : LIST) {
            if (sb.length() > 0) sb.append(';');
            sb.append(zeile(w, true));
        }
        return sb.toString();
    }

    /**
     * Laden. {@code format} ist das zweite Feld der Konfig-Zeile: "v2" ist das
     * aktuelle Format, "liste" das bis 4.93. Beim Umstellen wird die Anzeige im
     * Spiel einmal ausgeschaltet -- sie ist jetzt standardmaessig aus.
     */
    public static synchronized void laden(String format, String data) {
        LIST.clear();
        boolean alt = !FORMAT.equals(format);
        if (alt) WaypointSettings.INSTANCE.enabled.set(false);
        if (data == null || data.isEmpty()) return;
        for (String entry : data.split(";")) {
            Waypoint w = alt ? ausAlterZeile(entry, true) : ausZeile(entry);
            if (w != null) LIST.add(w);
        }
    }

    private static String zeile(Waypoint w, boolean mitWelt) {
        StringBuilder sb = new StringBuilder();
        sb.append(esc(w.name)).append('|')
          .append(w.x).append('|').append(w.y).append('|').append(w.z).append('|')
          .append(w.color).append('|')
          .append(mitWelt ? esc(w.welt) : "").append('|')
          .append(esc(w.dim)).append('|')
          .append(w.visible ? '1' : '0').append('|')
          .append(w.kind.name()).append('|')
          .append(bloecke(w)).append('|')
          .append(w.tracer ? '1' : '0').append('|')
          .append(esc(w.symbol));
        return sb.toString();
    }

    private static Waypoint ausZeile(String entry) {
        if (entry == null || entry.isEmpty()) return null;
        String[] p = entry.split("\\|", -1);
        if (p.length < 8) return null;
        try {
            Waypoint w = new Waypoint(unesc(p[0]),
                    Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()),
                    Integer.parseInt(p[4].trim()), unesc(p[5]), unesc(p[6]));
            w.visible = "1".equals(p[7].trim());
            if (p.length > 8) w.kind = art(p[8]);
            if (p.length > 9) bloeckeLesen(w, p[9]);
            if (p.length > 10) w.tracer = "1".equals(p[10].trim());
            if (p.length > 11) w.symbol = unesc(p[11]);
            return w;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Zeile im Format bis 4.93: Name|x|y|z|farbe|weltschluessel|sichtbar|art|bloecke|tracer.
     * Der alte Weltschluessel enthielt selbst "|" -- ueberzaehlige Teile gehoeren
     * zu ihm.
     */
    private static Waypoint ausAlterZeile(String entry, boolean weltUmstellen) {
        if (entry == null || entry.isEmpty()) return null;
        String[] p = entry.split("\\|");
        if (p.length < 7) return null;
        final int ERWARTET = 10;
        if (p.length > ERWARTET) {
            int extra = p.length - ERWARTET;
            StringBuilder dim = new StringBuilder(p[5]);
            for (int k = 1; k <= extra; k++) dim.append('|').append(p[5 + k]);
            String[] fixed = new String[ERWARTET];
            System.arraycopy(p, 0, fixed, 0, 5);
            fixed[5] = dim.toString();
            for (int k = 6; k < ERWARTET; k++) fixed[k] = p[k + extra];
            p = fixed;
        }
        try {
            String schluessel = unesc(p[5]);
            String welt = null, dim;
            if (weltUmstellen) {
                String[] wd = WaypointWorlds.ausAltemSchluessel(schluessel);
                welt = wd[0];
                dim = wd[1];
            } else {
                String[] teile = schluessel.split("\\|");
                dim = teile[teile.length - 1];
            }
            Waypoint w = new Waypoint(unesc(p[0]),
                    Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim()),
                    Integer.parseInt(p[4].trim()), welt, dim);
            w.visible = "1".equals(p[6].trim());
            if (p.length > 7) w.kind = art(p[7]);
            if (p.length > 8) bloeckeLesen(w, p[8]);
            if (p.length > 9) w.tracer = "1".equals(p[9].trim());
            return w;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Kind art(String s) {
        try {
            return Kind.valueOf(s.trim());
        } catch (Throwable e) {
            return Kind.ALLGEMEIN;
        }
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("%", "%25").replace("|", "%7C").replace(";", "%3B");
    }

    private static String unesc(String v) {
        if (v == null) return "";
        return v.replace("%7C", "|").replace("%3B", ";").replace("%25", "%");
    }

    /** Bloecke als "x,y,z x,y,z ..." (Leerzeichen trennt, Komma innerhalb). */
    private static String bloecke(Waypoint w) {
        if (w.blocks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.core.BlockPos b : w.blocks) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(b.getX()).append(',').append(b.getY()).append(',').append(b.getZ());
        }
        return sb.toString();
    }

    private static void bloeckeLesen(Waypoint w, String data) {
        w.blocks.clear();
        if (data == null || data.isBlank()) return;
        for (String part : data.trim().split(" ")) {
            String[] c = part.split(",");
            if (c.length != 3) continue;
            try {
                w.blocks.add(new net.minecraft.core.BlockPos(
                        Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])));
            } catch (Throwable ignored) {
            }
        }
    }
}
