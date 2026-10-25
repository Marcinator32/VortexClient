package com.vortex.client.cosmetics;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pixel-Cosmetics (seit 4.29): Bandanas, Face (Brillen, Masken), Back
 * (Rucksaecke, Schwerter ...), Auras und Shield Skins.
 *
 * Die Designs stehen in assets/vortexclient/cosmetics/designs.txt (erzeugt von
 * tools/cosmetics/art.py) und werden einmal gelesen. Jedes Teil ist ein
 * Pixelbild, das zu einem flachen 3D-Teil wird (wie ein Item in der Hand);
 * Bandanas sind ein Band rund um den Kopf.
 *
 * Ohne Minecraft-Klassen -- die Netze lassen sich auch ausserhalb des Spiels
 * bauen und pruefen.
 */
public final class Zubehoer {
    private Zubehoer() {}

    public enum Kategorie { BANDANA, FACE, BACK, AURA, SHIELD, HAT, NECK, PET }

    /** Ein Pixelbild mit Lage: at (Pixel), px (Groesse eines Bildpixels), Drehung in Grad. */
    /**
     * Ein Teil: Pixelbild (flach mit Tiefe) oder -- wenn vox gesetzt ist -- ein echtes
     * Voxel-Modell (vox[y][z][x], seit 4.32). farben ist dann die Vorderansicht (Vorschaubild).
     */
    public record Teil(int[][] farben, boolean[][] glow, float[] at, float px, float tiefe, float[] rot, String anim,
                       int[][][] vox, boolean[][][] voxGlow) {
        public Teil(int[][] farben, boolean[][] glow, float[] at, float px, float tiefe, float[] rot, String anim) {
            this(farben, glow, at, px, tiefe, rot, anim, null, null);
        }
    }

    /** Band um den Kopf: Zeilen x 32 Spalten (vorne, links, hinten, rechts). */
    /**
     * flow (seit 4.63): Spalten je Sekunde, die das Muster um den Kopf laeuft (negativ = andersherum);
     * fx: was das Leuchten tut -- "pulse" (atmet), "flash" (blitzt auf), "twinkle" (funkelt einzeln).
     */
    public record Ring(int[][] farben, boolean[][] glow, float y0, float px, int knoten, int[] baender, float flow, String fx) {
        public Ring(int[][] farben, boolean[][] glow, float y0, float px, int knoten, int[] baender) {
            this(farben, glow, y0, px, knoten, baender, 0f, "");
        }
    }

    public record Aura(String bewegung, int anzahl, float px, float drehen, float groesse, List<Teil> sprites) {}

    public record Design(Kategorie kat, String id, String name, List<Teil> teile, Ring ring, int buegel, Aura aura,
                         Map<Character, Integer> farben, Map<String, String> werte) {}

    /** Pet, das am Boden neben den Fuessen mitlaeuft (seit 4.98, "ground: 1")? */
    public static boolean amBoden(Design d) {
        return d != null && d.kat() == Kategorie.PET && "1".equals(d.werte().get("ground"));
    }

    private static Map<String, Design> alle;
    private static final Map<String, String> ALTE_PARTIKEL = new HashMap<>();

    public static synchronized Map<String, Design> alle() {
        if (alle == null) alle = laden();
        return alle;
    }

    public static List<Design> von(Kategorie k) {
        List<Design> l = new ArrayList<>();
        for (Design d : alle().values()) if (d.kat() == k) l.add(d);
        return l;
    }

    /**
     * Glitzern (seit 4.35): steht als Endung "--s" an der Id ("lightning--s"). So wird es
     * mit der Auswahl gespeichert und an Freunde geschickt -- ohne Aenderung am Server.
     */
    public static String basisId(String id) {
        if (id == null) return "";
        int i = id.indexOf("--");
        return i < 0 ? id : id.substring(0, i);
    }

    public static boolean glitzert(String id) {
        if (id == null) return false;
        int i = id.indexOf("--");
        return i >= 0 && id.indexOf('s', i + 2) >= 0;
    }

    public static String mitGlitzern(String id, boolean an) {
        String b = basisId(id);
        return b.isEmpty() || !an ? b : b + "--s";
    }

    /** Design einer Kategorie; null = keins (oder unbekannt). Alte Partikel-IDs zeigen auf die passende Aura. */
    public static Design get(Kategorie k, String id) {
        if (id == null || id.isEmpty()) return null;
        id = basisId(id);
        Design d = alle().get(k.name() + ":" + id);
        if (d == null && k == Kategorie.AURA) {
            String neu = ALTE_PARTIKEL.get(id);
            if (neu != null) d = alle().get(k.name() + ":" + neu);
        }
        return d;
    }

    /** Alte Partikel-ID (bis 4.28) -> Aura-ID; sonst unveraendert. */
    public static String auraFuer(String id) {
        alle();
        if (id == null) return "";
        String neu = ALTE_PARTIKEL.get(id);
        return neu != null && alle().get("AURA:" + id) == null ? neu : id;
    }

    private static Map<String, Design> laden() {
        Map<String, Design> m = new LinkedHashMap<>();
        try (InputStream in = Zubehoer.class.getResourceAsStream("/assets/vortexclient/cosmetics/designs.txt")) {
            if (in == null) return m;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> zeilen = new ArrayList<>();
            String z;
            while ((z = r.readLine()) != null) zeilen.add(z);
            parse(zeilen, m);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("Zubehoer.laden", t);
        }
        return Collections.unmodifiableMap(m);
    }

    static void parse(List<String> zeilen, Map<String, Design> m) {
        int i = 0;
        while (i < zeilen.size()) {
            String z = zeilen.get(i).trim();
            if (!z.startsWith("== ")) { i++; continue; }
            String[] kopf = z.substring(3).trim().split("\\s+");
            Kategorie kat = Kategorie.valueOf(kopf[0].toUpperCase());
            String id = kopf[1];
            String name = id;
            Map<Character, Integer> farben = new HashMap<>();
            String glowAlle = "";
            int buegel = 0;
            List<Teil> teile = new ArrayList<>();
            List<Teil> sprites = new ArrayList<>();
            Ring ring = null;
            Map<String, String> werte = new HashMap<>();
            i++;
            while (i < zeilen.size() && !zeilen.get(i).trim().startsWith("== ")) {
                String l = zeilen.get(i).trim();
                i++;
                if (l.isEmpty() || l.startsWith("#")) continue;
                int dp = l.indexOf(':');
                if (dp < 0) continue;
                String k = l.substring(0, dp).trim(), v = l.substring(dp + 1).trim();
                switch (k) {
                    case "name" -> name = v;
                    case "colors" -> farben(v, farben);
                    case "glow" -> glowAlle = v;
                    case "arms" -> buegel = v.isEmpty() ? 0 : -v.charAt(0);
                    case "vox" -> {
                        Map<String, String> opt = optionen(v);
                        Map<Character, Integer> eigene = new HashMap<>(farben);
                        List<List<String>> schichten = new ArrayList<>();
                        List<String> schicht = new ArrayList<>();
                        while (i < zeilen.size()) {
                            String b = zeilen.get(i);
                            i++;
                            String tb = b.trim();
                            if (tb.equals("end")) break;
                            if (tb.startsWith("colors:")) { farben(tb.substring(7).trim(), eigene); continue; }
                            if (tb.equals("--")) { schichten.add(schicht); schicht = new ArrayList<>(); continue; }
                            schicht.add(b.startsWith("  ") ? b.substring(2) : tb);
                        }
                        if (!schicht.isEmpty()) schichten.add(schicht);
                        String glow = opt.getOrDefault("glow", "") + glowAlle;
                        int hh = schichten.size();
                        int[][][] vc = new int[hh][][];
                        boolean[][][] vg = new boolean[hh][][];
                        for (int y = 0; y < hh; y++) {
                            List<String> sch = schichten.get(y);
                            vc[y] = new int[sch.size()][];
                            vg[y] = new boolean[sch.size()][];
                            for (int zz = 0; zz < sch.size(); zz++) {
                                String row = sch.get(zz);
                                vc[y][zz] = new int[row.length()];
                                vg[y][zz] = new boolean[row.length()];
                                for (int x = 0; x < row.length(); x++) {
                                    char ch = row.charAt(x);
                                    Integer f = ch == '.' ? null : eigene.get(ch);
                                    vc[y][zz][x] = f == null ? 0 : f;
                                    vg[y][zz][x] = f != null && glow.indexOf(ch) >= 0;
                                }
                            }
                        }
                        int[][] vorne = vorderansicht(vc);
                        boolean[][] vorneG = new boolean[vorne.length][vorne.length == 0 ? 0 : vorne[0].length];
                        teile.add(new Teil(vorne, vorneG, drei(opt.get("at")), f(opt, "px", 0.5f), 1f, drei(opt.get("rot")),
                                opt.getOrDefault("anim", ""), vc, vg));
                    }
                    case "part", "sprite", "ring" -> {
                        Map<String, String> opt = optionen(v);
                        Map<Character, Integer> eigene = new HashMap<>(farben);
                        List<String> bild = new ArrayList<>();
                        while (i < zeilen.size()) {
                            String b = zeilen.get(i);
                            i++;
                            if (b.trim().equals("end")) break;
                            if (b.trim().startsWith("colors:")) { farben(b.trim().substring(7).trim(), eigene); continue; }
                            bild.add(b.startsWith("  ") ? b.substring(2) : b.trim());
                        }
                        String glow = opt.getOrDefault("glow", "") + glowAlle;
                        int[][] c = new int[bild.size()][];
                        boolean[][] g = new boolean[bild.size()][];
                        for (int y = 0; y < bild.size(); y++) {
                            String row = bild.get(y);
                            c[y] = new int[row.length()];
                            g[y] = new boolean[row.length()];
                            for (int x = 0; x < row.length(); x++) {
                                char ch = row.charAt(x);
                                Integer f = ch == '.' ? null : eigene.get(ch);
                                c[y][x] = f == null ? 0 : f;
                                g[y][x] = f != null && glow.indexOf(ch) >= 0;
                            }
                        }
                        if (k.equals("ring")) {
                            String t = opt.getOrDefault("tails", "");
                            int[] baender = new int[t.length()];
                            for (int j = 0; j < t.length(); j++) baender[j] = eigene.getOrDefault(t.charAt(j), 0xFF202020);
                            String kn = opt.getOrDefault("knot", "");
                            ring = new Ring(c, g, f(opt, "y0", 4f), f(opt, "px", 1f),
                                    kn.isEmpty() ? (baender.length > 0 ? baender[0] : 0xFF202020) : eigene.getOrDefault(kn.charAt(0), 0xFF202020), baender,
                                    f(opt, "flow", 0f), opt.getOrDefault("fx", ""));
                        } else {
                            Teil teil = new Teil(c, g, drei(opt.get("at")), f(opt, "px", 0.5f), f(opt, "depth", 1f), drei(opt.get("rot")), opt.getOrDefault("anim", ""));
                            if (k.equals("part")) teile.add(teil); else sprites.add(teil);
                        }
                    }
                    default -> werte.put(k, v);
                }
            }
            if (buegel < 0) buegel = farben.getOrDefault((char) -buegel, 0xFF202020);
            Aura aura = null;
            if (kat == Kategorie.AURA) {
                aura = new Aura(werte.getOrDefault("motion", "orbit"), (int) zahl(werte.get("count"), 6), zahl(werte.get("px"), 0.7f),
                        zahl(werte.get("spin"), 1f), zahl(werte.get("size"), 1f), sprites);
                for (String alt : werte.getOrDefault("legacy", "").split(",")) if (!alt.isBlank()) ALTE_PARTIKEL.put(alt.trim(), id);
            }
            m.put(kat.name() + ":" + id, new Design(kat, id, name, teile, ring, buegel, aura, farben, werte));
        }
    }

    /** Vorderansicht eines Voxel-Modells (fuer Vorschaubilder): oberste Zeile zuerst. */
    static int[][] vorderansicht(int[][][] vc) {
        int h = vc.length, w = 0, d = 0;
        for (int[][] l : vc) { d = Math.max(d, l.length); for (int[] r : l) w = Math.max(w, r.length); }
        int[][] o = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                for (int z = 0; z < d; z++) {
                    int f = z < vc[y].length && x < vc[y][z].length ? vc[y][z][x] : 0;
                    if ((f >>> 24) != 0) { o[h - 1 - y][x] = f; break; }
                }
        return o;
    }

    private static void farben(String v, Map<Character, Integer> m) {
        for (String p : v.split("\\s+")) {
            int e = p.indexOf('=');
            if (e != 1) continue;
            m.put(p.charAt(0), 0xFF000000 | Integer.parseInt(p.substring(2), 16));
        }
    }

    private static Map<String, String> optionen(String v) {
        Map<String, String> m = new HashMap<>();
        for (String p : v.split("\\s+")) {
            int e = p.indexOf('=');
            if (e > 0) m.put(p.substring(0, e), p.substring(e + 1));
        }
        return m;
    }

    private static float f(Map<String, String> m, String k, float d) { return zahl(m.get(k), d); }

    private static float zahl(String s, float d) {
        try { return s == null ? d : Float.parseFloat(s); } catch (NumberFormatException e) { return d; }
    }

    private static float[] drei(String s) {
        float[] r = new float[3];
        if (s == null) return r;
        String[] t = s.split(",");
        for (int i = 0; i < Math.min(3, t.length); i++) r[i] = zahl(t[i], 0);
        return r;
    }

    private static final Map<Object, Netz> NETZE = new HashMap<>();

    /** Netz eines Pixelbilds (Ursprung Bildmitte, 1 Bildpixel = 1, Tiefe entlang z). */
    public static synchronized Netz netz(Teil t) {
        return NETZE.computeIfAbsent(t, k -> {
            Netz n = new Netz();
            if (t.vox() != null) n.voxel(t.vox(), t.voxGlow());
            else n.pixelbild(t.farben(), t.glow(), t.tiefe());
            return n;
        });
    }

    /** Abstand des Bands vom Kopfmittelpunkt (ausserhalb der zweiten Hautschicht bei 4,5). */
    public static final float BAND_AUSSEN = 4.56f, BAND_DICKE = 0.34f;

    /**
     * Band rund um den Kopf (Kopf-Koordinaten: x/z -4..4, y 0..8 nach oben).
     * Spalten (durch 4 teilbar) rundherum: vorne, rechts, hinten, links -- je ein Viertel.
     */
    public static synchronized Netz ringNetz(Ring r) {
        return ringNetz(r, 0);
    }

    /** Spalten je Zeile rund um den Kopf (durch 4 teilbar). */
    public static int ringSpalten(Ring r) {
        int s = 4;
        for (int[] z : r.farben()) s = Math.max(s, z.length / 4 * 4);
        return s;
    }

    /**
     * Wie oben, das Muster aber um "versatz" Spalten weitergedreht (seit 4.63: laufende
     * Bandanas). Jede Stellung wird einmal gebaut und gemerkt -- je Bandana hoechstens
     * so viele Netze wie Spalten.
     */
    public static synchronized Netz ringNetz(Ring r, int versatz) {
        final int spaltenAlle = ringSpalten(r);
        final int vs = ((versatz % spaltenAlle) + spaltenAlle) % spaltenAlle;
        return NETZE.computeIfAbsent(vs == 0 ? r : java.util.List.of(r, vs), k -> {
            Netz n = new Netz();
            float a = BAND_AUSSEN, d = BAND_DICKE;
            int zeilen = r.farben().length;
            for (int y = 0; y < zeilen; y++) {
                float y1 = r.y0() + (zeilen - y) * r.px(), y0 = y1 - r.px();
                int spalten = Math.max(4, r.farben()[y].length / 4 * 4), jeSeite = spalten / 4;
                for (int s = 0; s < spalten; s++) {
                    int q = r.farben()[y].length == 0 ? 0 : (s + vs * r.farben()[y].length / spaltenAlle) % Math.max(1, r.farben()[y].length);
                    int f = q < r.farben()[y].length ? r.farben()[y][q] : 0;
                    if ((f >>> 24) == 0) continue;
                    n.leuchten(r.glow()[y].length > q && r.glow()[y][q] ? 1f : 0f);
                    int seite = s / jeSeite, j = s % jeSeite;
                    float br = 2 * a / jeSeite, v0 = -a + j * br, v1 = v0 + br;
                    switch (seite) {
                        case 0 -> n.quader(-v1, y0, -a - d, -v0, y1, -a, f, 0x3F);
                        case 1 -> n.quader(-a - d, y0, v0, -a, y1, v1, f, 0x3F);
                        case 2 -> n.quader(v0, y0, a, v1, y1, a + d, f, 0x3F);
                        default -> n.quader(a, y0, -v1, a + d, y1, -v0, f, 0x3F);
                    }
                    if (j == jeSeite - 1) {
                        switch (seite) {
                            case 0 -> n.quader(-a - d, y0, -a - d, -a, y1, -a, f, 0x3F);
                            case 1 -> n.quader(-a - d, y0, a, -a, y1, a + d, f, 0x3F);
                            case 2 -> n.quader(a, y0, a, a + d, y1, a + d, f, 0x3F);
                            default -> n.quader(a, y0, -a - d, a + d, y1, -a, f, 0x3F);
                        }
                    }
                }
            }
            n.leuchten(0);
            return n;
        });
    }
}
