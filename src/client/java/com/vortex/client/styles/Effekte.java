package com.vortex.client.styles;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * Effekte im Styles-Menue (seit 4.66) -- mehr als nur Farben.
 *
 * Jedes Teil hat eine oder zwei Gruppen (Look, Height, Pop size, Frame); in jeder Gruppe
 * waehlt man eine Option. Effekte lassen sich mit jeder Farbe und jedem importierten Style
 * kombinieren: erst kommt die Farbe (bzw. das Pack), dann der Effekt.
 *
 * Fast alles wird aus den Texturen berechnet (wie die Farben) und landet im selben
 * "Vortex Styles"-Pack. Nur die Totem-Groesse ist ein Item-Modell (Darstellung "fixed",
 * die Minecraft fuer die Pop-Animation benutzt).
 *
 * Bewusst ohne Minecraft-Klassen: im Test als Bild pruefbar.
 */
public final class Effekte {
    private Effekte() {}

    public record Option(String id, String name, String info) {}

    public record Gruppe(String id, String name, List<Option> optionen) {
        public Option standard() { return optionen.get(0); }
        public Option option(String id) {
            for (Option o : optionen) if (o.id().equals(id)) return o;
            return null;
        }
    }

    private static Option o(String id, String name, String info) { return new Option(id, name, info); }

    private static final Gruppe BLOCK_LOOK = new Gruppe("look", "Look", List.of(
            o("normal", "Normal", "Default"),
            o("potato", "Potato", "One color"),
            o("smooth", "Smooth", "Less noise"),
            o("pixel", "Chunky", "Big pixels"),
            o("edges", "Edges", "Block outlines")));
    private static final Gruppe ITEM_LOOK = new Gruppe("look", "Look", List.of(
            o("normal", "Normal", "Default"),
            o("potato", "Potato", "Flat color"),
            o("outline", "Outline", "Dark edge"),
            o("smooth", "Smooth", "Less noise")));
    private static final Gruppe EINFACH_LOOK = new Gruppe("look", "Look", List.of(
            o("normal", "Normal", "Default"),
            o("potato", "Potato", "Flat colors"),
            o("smooth", "Smooth", "Less noise")));
    private static final Gruppe FEUER_HOEHE = new Gruppe("height", "Height", List.of(
            o("full", "Full", "Default"),
            o("medium", "Medium", "Half height"),
            o("low", "Low", "A few pixels")));
    private static final Gruppe TOTEM_POP = new Gruppe("pop", "Pop size", List.of(
            o("full", "Full", "Default"),
            o("medium", "Medium", "60 %"),
            o("small", "Small", "30 %"),
            o("off", "Off", "No pop picture")));
    private static final Gruppe KRISTALL_RAHMEN = new Gruppe("frame", "Frame", List.of(
            o("shown", "Shown", "Default"),
            o("hidden", "Hidden", "Only the core")));
    private static final Gruppe HOTBAR_LOOK = new Gruppe("look", "Look", List.of(
            o("normal", "Normal", "Default"),
            o("glass", "See-through", "Half transparent"),
            o("smooth", "Flat", "Less noise")));
    private static final Gruppe INVENTAR_LOOK = new Gruppe("look", "Look", List.of(
            o("normal", "Normal", "Default"),
            o("dark", "Dark", "Dark mode"),
            o("smooth", "Flat", "Less noise")));

    private static final Gruppe LEISTE_LOOK = new Gruppe("look", "Look", List.of(
            o("normal", "Normal", "Default"),
            o("potato", "Potato", "Flat color"),
            o("smooth", "Smooth", "Less shine"),
            o("pastel", "Pastel", "Soft and light"),
            o("glass", "See-through", "Half transparent")));
    private static final Gruppe HERZ_RAHMEN = new Gruppe("empty", "Empty hearts", List.of(
            o("shown", "Shown", "Default"),
            o("faint", "Faint", "Barely visible"),
            o("hidden", "Hidden", "Only full hearts")));
    private static final Gruppe HERZ_BLINKEN = new Gruppe("blink", "Damage blink", List.of(
            o("shown", "Shown", "Default"),
            o("hidden", "Off", "No white flash")));
    private static final Gruppe LEER = new Gruppe("empty", "Empty slots", List.of(
            o("shown", "Shown", "Default"),
            o("faint", "Faint", "Barely visible"),
            o("hidden", "Hidden", "Only what you have")));
    private static final Gruppe RUESTUNG_FARBE = new Gruppe("tint", "Color", List.of(
            o("style", "Style", "Color above"),
            o("worn", "By armor", "Like your armor")));

    private static final Gruppe ZAHL = new Gruppe("number", "Number", List.of(
            o("off", "Off", "Default"),
            o("on", "Shown", "Value next to the bar")));
    private static final Gruppe XP_ZAHL = new Gruppe("number", "Number", List.of(
            o("off", "Off", "Default"),
            o("percent", "Percent", "Progress to the next level"),
            o("points", "Points", "XP points to the next level")));
    private static final Gruppe XP_LOOK = new Gruppe("look", "Look", List.of(
            o("normal", "Normal", "Default"),
            o("smooth", "Smooth", "Less noise"),
            o("pastel", "Pastel", "Soft and light"),
            o("glass", "See-through", "Half transparent")));

    /** Die Gruppen eines Teils, in Menue-Reihenfolge. */
    public static List<Gruppe> fuer(Kategorie k) {
        return switch (k) {
            case CRYSTAL -> List.of(KRISTALL_RAHMEN, EINFACH_LOOK);
            case TOTEM -> List.of(TOTEM_POP, ITEM_LOOK);
            case FIRE -> List.of(FEUER_HOEHE, EINFACH_LOOK);
            case PEARL, GAPPLE, SWORD, PICKAXE, AXE -> List.of(ITEM_LOOK);
            case ARMOR -> List.of(EINFACH_LOOK);
            case HOTBAR -> List.of(HOTBAR_LOOK);
            case HEARTS -> List.of(LEISTE_LOOK, HERZ_RAHMEN, HERZ_BLINKEN, ZAHL);
            case HUNGER -> List.of(LEISTE_LOOK, LEER, ZAHL);
            case ARMOR_BAR -> List.of(RUESTUNG_FARBE, LEISTE_LOOK, LEER, ZAHL);
            case XP_BAR -> List.of(XP_LOOK, XP_ZAHL);
            case INVENTORY -> List.of(INVENTAR_LOOK);
            default -> List.of(BLOCK_LOOK);
        };
    }

    public static Gruppe gruppe(Kategorie k, String id) {
        for (Gruppe g : fuer(k)) if (g.id().equals(id)) return g;
        return null;
    }

    /** Aendert irgendeine Wahl die Texturen? (Totem-Groesse allein nicht.) */
    public static boolean aendertBilder(Map<String, String> wahl) {
        if (wahl == null) return false;
        for (Map.Entry<String, String> e : wahl.entrySet()) {
            if (e.getKey().equals("pop") || e.getKey().equals("number")) continue;
            if (!istStandard(e.getKey(), e.getValue())) return true;
        }
        return false;
    }

    private static boolean istStandard(String gruppe, String wert) {
        return wert == null || wert.equals("normal") || wert.equals("full") || wert.equals("shown") || wert.equals("style") || wert.equals("off");
    }

    /**
     * Effekte auf eine Textur anwenden (liefert ein neues Bild oder dasselbe).
     * pfad = Pfad unter assets/minecraft/ (fuer Sonderfaelle wie den Kristall).
     */
    public static BufferedImage anwenden(Kategorie k, Map<String, String> wahl, BufferedImage src, String pfad) {
        if (src == null || wahl == null) return src;
        BufferedImage b = src;
        if (k == Kategorie.ARMOR_BAR && ruestungNachGetragen(wahl) && pfad != null && pfad.matches(".*/armor_(full|half)\\.png")) b = hellFuerToenung(b);
        String rahmen = wahl.get("frame");
        if ("hidden".equals(rahmen) && pfad != null && pfad.endsWith("entity/end_crystal/end_crystal.png")) b = ohneRahmen(b);
        String look = wahl.get("look");
        if (look != null && !look.equals("normal")) {
            boolean nurGrau = k.modus == Kategorie.Modus.GRAU && k != Kategorie.INVENTORY;
            b = switch (look) {
                case "potato" -> k.leiste() ? flachLeiste(b, 1f, k == Kategorie.ARMOR_BAR ? 0.3f : 0.15f) : potato(b, pfad, nurGrau);
                case "smooth" -> k.leiste() ? flachLeiste(b, 0.6f, k == Kategorie.ARMOR_BAR ? 0.3f : 0.15f) : glatt(b, nurGrau, k == Kategorie.HOTBAR || k == Kategorie.INVENTORY ? 0.8f : 0.6f);
                case "pixel" -> grob(b);
                case "edges" -> kanten(glatt(b, nurGrau, 0.55f));
                case "outline" -> umriss(b);
                case "pastel" -> pastell(b, k == Kategorie.ARMOR_BAR ? 0.3f : 0.15f);
                case "glass" -> durchsichtig(b, 0.55f);
                case "dark" -> dunkel(b);
                default -> b;
            };
        }
        String leer = wahl.get("empty");
        if (leer != null && !leer.equals("shown") && pfad != null && leeresStueck(pfad)) {
            b = leer.equals("hidden") ? leer(b) : durchsichtig(b, 0.3f);
        }
        String hoehe = wahl.get("height");
        if ("medium".equals(hoehe)) b = niedriger(b, 0.55f);
        else if ("low".equals(hoehe)) b = niedriger(b, 0.3f);
        return b;
    }

    /**
     * Potato/Smooth fuer Herzen, Keulen und Ruestung: nur die Flaeche wird flach, die
     * schwarze Umrandung bleibt -- sonst wird aus dem Symbol ein grauer Klecks.
     */
    static BufferedImage flachLeiste(BufferedImage src, float staerke, float schwelle) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                if ((p >>> 24) < 16 || helligkeit(p) <= schwelle) continue;
                r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++;
            }
        }
        if (n == 0) return src;
        int c = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        BufferedImage out = leer(src);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                out.setRGB(x, y, (p >>> 24) < 16 || helligkeit(p) <= schwelle ? p : mische(p, c, staerke));
            }
        }
        return out;
    }

    /** Pastel: die Flaeche Richtung Weiss, die dunkle Umrandung bleibt. */
    static BufferedImage pastell(BufferedImage src, float schwelle) {
        BufferedImage out = leer(src);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                out.setRGB(x, y, (p >>> 24) < 16 || helligkeit(p) <= schwelle ? p : mische(p, 0xFFFFFF, 0.42f));
            }
        }
        return out;
    }

    private static float helligkeit(int p) {
        return (0.299f * ((p >> 16) & 0xFF) + 0.587f * ((p >> 8) & 0xFF) + 0.114f * (p & 0xFF)) / 255f;
    }

    /** Leerer Platz einer Leiste: Herz-Rahmen, leere Keule, leere Ruestung. */
    static boolean leeresStueck(String pfad) {
        String n = pfad.substring(pfad.lastIndexOf('/') + 1);
        return n.startsWith("container") || n.startsWith("vehicle_container") || n.startsWith("food_empty") || n.equals("armor_empty.png");
    }

    /**
     * Fuer "By armor": die graue Ruestung fast weiss machen (Schattierung bleibt), damit die
     * Toenung im Spiel die echte Materialfarbe ergibt statt einer abgedunkelten.
     */
    static BufferedImage hellFuerToenung(BufferedImage src) {
        java.util.List<Float> helle = new java.util.ArrayList<>();
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                if ((p >>> 24) >= 16 && helligkeit(p) > 0.3f) helle.add(helligkeit(p));
            }
        if (helle.isEmpty()) return src;
        java.util.Collections.sort(helle);
        float mitte = helle.get(helle.size() / 2);
        BufferedImage out = leer(src);
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                if ((p >>> 24) < 16 || helligkeit(p) <= 0.3f) { out.setRGB(x, y, p); continue; }
                int v = Math.min(255, Math.round(helligkeit(p) / mitte * 236f));
                out.setRGB(x, y, (p & 0xFF000000) | v << 16 | v << 8 | v);
            }
        return out;
    }

    /** Ist die Ruestungsleiste auf "nach getragener Ruestung" gestellt? */
    public static boolean ruestungNachGetragen(Map<String, String> wahl) {
        return wahl != null && "worn".equals(wahl.get("tint"));
    }

    /** Ein Stueck Richtung Weiss (Blink-Bilder nach dem Umfaerben wieder heller). */
    public static BufferedImage aufhellen(BufferedImage src, float f) {
        BufferedImage out = leer(src);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                out.setRGB(x, y, (p >>> 24) == 0 ? p : mische(p, 0xFFFFFF, f));
            }
        }
        return out;
    }

    /** Hoehe eines Bilds in einem Animationsstreifen (Feuer, Crying Obsidian): quadratisch. */
    private static int bildHoehe(BufferedImage i) {
        int w = i.getWidth(), h = i.getHeight();
        return (h > w && h % w == 0) ? w : h;
    }

    private static BufferedImage leer(BufferedImage i) {
        return new BufferedImage(i.getWidth(), i.getHeight(), BufferedImage.TYPE_INT_ARGB);
    }

    private static boolean grau(int p) {
        float r = ((p >> 16) & 0xFF) / 255f, g = ((p >> 8) & 0xFF) / 255f, b = (p & 0xFF) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        return (mx <= 0f ? 0f : (mx - mn) / mx) < 0.2f;
    }

    /** Durchschnittsfarbe (RGB) der sichtbaren (bzw. grauen) Pixel eines Bereichs, -1 = keiner. */
    private static int mittel(BufferedImage i, int x0, int y0, int w, int h, boolean nurGrau) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int p = i.getRGB(x, y);
                if ((p >>> 24) < 16 || (nurGrau && !grau(p))) continue;
                r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++;
            }
        }
        if (n == 0) return -1;
        return (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    private static int mische(int p, int c, float k) {
        int r = (int) (((p >> 16) & 0xFF) + (((c >> 16) & 0xFF) - ((p >> 16) & 0xFF)) * k);
        int g = (int) (((p >> 8) & 0xFF) + (((c >> 8) & 0xFF) - ((p >> 8) & 0xFF)) * k);
        int b = (int) ((p & 0xFF) + ((c & 0xFF) - (p & 0xFF)) * k);
        return (p & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int skaliere(int p, float f) {
        int r = Math.min(255, Math.round(((p >> 16) & 0xFF) * f)), g = Math.min(255, Math.round(((p >> 8) & 0xFF) * f)), b = Math.min(255, Math.round((p & 0xFF) * f));
        return (p & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /**
     * Potato: eine Farbe. Bloecke und Items (quadratische Bilder, auch jedes Bild eines
     * Animationsstreifens) bekommen ihre Durchschnittsfarbe; Items behalten ihren Umriss
     * (mit einer etwas dunkleren Kante, damit man sie noch erkennt). Grosse Entity-Texturen
     * (Kristall, Ruestung am Koerper) werden in Kacheln gemittelt -- jede Flaeche eine Farbe.
     * Graue Bloecke (Stein mit Erzen): nur das Graue, Erze bleiben sichtbar.
     */
    static BufferedImage potato(BufferedImage src, String pfad, boolean nurGrau) {
        int w = src.getWidth(), h = src.getHeight(), bh = bildHoehe(src);
        BufferedImage out = leer(src);
        boolean entity = pfad != null && pfad.contains("textures/entity/");
        int kachelW = entity ? Math.max(1, w / 16) : w, kachelH = entity ? Math.max(1, w / 16) : bh;
        if (pfad != null && pfad.endsWith("end_crystal/end_crystal.png")) { kachelW = Math.max(1, w / 8); kachelH = kachelW; }
        for (int ty = 0; ty < h; ty += kachelH) {
            for (int tx = 0; tx < w; tx += kachelW) {
                int kw = Math.min(kachelW, w - tx), kh = Math.min(kachelH, h - ty);
                int c = mittel(src, tx, ty, kw, kh, nurGrau);
                for (int y = ty; y < ty + kh; y++) {
                    for (int x = tx; x < tx + kw; x++) {
                        int p = src.getRGB(x, y);
                        int a = p >>> 24;
                        if (a == 0 || c < 0 || (nurGrau && !grau(p))) { out.setRGB(x, y, p); continue; }
                        out.setRGB(x, y, (a < 128 ? a : 0xFF) << 24 | c);
                    }
                }
            }
        }
        if (!entity && pfad != null && pfad.contains("textures/item/")) return dunkleKante(out, 0.62f);
        return out;
    }

    /** Smooth: jedes Pixel ein Stueck zur Durchschnittsfarbe seines Bilds -- weniger Rauschen, Form bleibt. */
    static BufferedImage glatt(BufferedImage src, boolean nurGrau, float staerke) {
        int w = src.getWidth(), h = src.getHeight(), bh = bildHoehe(src);
        BufferedImage out = leer(src);
        boolean entity = w >= 64 && w != bh;
        int kachel = entity ? Math.max(1, w / 8) : bh;
        for (int ty = 0; ty < h; ty += kachel) {
            for (int tx = 0; tx < w; tx += (entity ? kachel : w)) {
                int kw = Math.min(entity ? kachel : w, w - tx), kh = Math.min(kachel, h - ty);
                int c = mittel(src, tx, ty, kw, kh, nurGrau);
                for (int y = ty; y < ty + kh; y++) {
                    for (int x = tx; x < tx + kw; x++) {
                        int p = src.getRGB(x, y);
                        out.setRGB(x, y, c < 0 || (p >>> 24) == 0 || (nurGrau && !grau(p)) ? p : mische(p, c, staerke));
                    }
                }
            }
        }
        return out;
    }

    /** Chunky: doppelt so grosse Pixel (je 2 x 2 gemittelt), bei hoch aufgeloesten Packs entsprechend mehr. */
    static BufferedImage grob(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int s = Math.max(2, w / 8);
        BufferedImage out = leer(src);
        for (int ty = 0; ty < h; ty += s) {
            for (int tx = 0; tx < w; tx += s) {
                int kw = Math.min(s, w - tx), kh = Math.min(s, h - ty);
                int c = mittel(src, tx, ty, kw, kh, false);
                for (int y = ty; y < ty + kh; y++) {
                    for (int x = tx; x < tx + kw; x++) {
                        int p = src.getRGB(x, y);
                        out.setRGB(x, y, c < 0 || (p >>> 24) == 0 ? p : (p & 0xFF000000) | c);
                    }
                }
            }
        }
        return out;
    }

    /** Edges: am Rand jedes Blocks eine dunklere Linie (oben/links etwas heller) -- man sieht jeden Block einzeln. */
    static BufferedImage kanten(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight(), bh = bildHoehe(src);
        int d = Math.max(1, w / 16);
        BufferedImage out = leer(src);
        for (int y = 0; y < h; y++) {
            int yy = y % bh;
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                if ((p >>> 24) == 0) { out.setRGB(x, y, p); continue; }
                boolean hell = yy < d || x < d, dunkel = yy >= bh - d || x >= w - d;
                out.setRGB(x, y, dunkel ? mische(p, 0x000000, 0.55f) : hell ? mische(p, 0xFFFFFF, 0.22f) : p);
            }
        }
        return out;
    }

    /** Outline: dunkle Kante um das Item (die aeusseren Pixel). */
    static BufferedImage umriss(BufferedImage src) {
        return dunkleKante(src, 0.35f);
    }

    private static BufferedImage dunkleKante(BufferedImage src, float f) {
        int w = src.getWidth(), h = src.getHeight();
        int d = Math.max(1, w / 16);
        BufferedImage out = leer(src);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                if ((p >>> 24) < 128) { out.setRGB(x, y, p); continue; }
                boolean rand = false;
                for (int dy = -d; dy <= d && !rand; dy += d)
                    for (int dx = -d; dx <= d && !rand; dx += d) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        rand = nx < 0 || ny < 0 || nx >= w || ny >= h || (src.getRGB(nx, ny) >>> 24) < 128;
                    }
                out.setRGB(x, y, rand ? skaliere(p, f) : p);
            }
        }
        return out;
    }

    /** See-through: Deckkraft senken (Hotbar). */
    static BufferedImage durchsichtig(BufferedImage src, float f) {
        BufferedImage out = leer(src);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                int a = Math.round((p >>> 24) * f);
                out.setRGB(x, y, a << 24 | (p & 0xFFFFFF));
            }
        }
        return out;
    }

    /**
     * Dark: graue Flaechen werden dunkel, die Reihenfolge bleibt (Kanten dunkler als die
     * Flaeche, Lichtkanten heller) -- so bleibt der 3D-Rand der Slots erhalten. Ein Hauch
     * Violett. Bunte Teile (Pfeile, Symbole) bleiben.
     */
    private static final float[][] DUNKEL_KURVE = { {0f, 0.05f}, {0.22f, 0.07f}, {0.55f, 0.12f}, {0.78f, 0.19f}, {1f, 0.33f} };

    static BufferedImage dunkel(BufferedImage src) {
        BufferedImage out = leer(src);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                if ((p >>> 24) == 0 || !grau(p)) { out.setRGB(x, y, p); continue; }
                float l = (0.299f * ((p >> 16) & 0xFF) + 0.587f * ((p >> 8) & 0xFF) + 0.114f * (p & 0xFF)) / 255f;
                float n = DUNKEL_KURVE[DUNKEL_KURVE.length - 1][1];
                for (int i = 1; i < DUNKEL_KURVE.length; i++) {
                    if (l <= DUNKEL_KURVE[i][0]) {
                        float t = (l - DUNKEL_KURVE[i - 1][0]) / (DUNKEL_KURVE[i][0] - DUNKEL_KURVE[i - 1][0]);
                        n = DUNKEL_KURVE[i - 1][1] + (DUNKEL_KURVE[i][1] - DUNKEL_KURVE[i - 1][1]) * t;
                        break;
                    }
                }
                int r = Math.round(n * 255f * 0.96f), g = Math.round(n * 255f * 0.94f), b = Math.round(Math.min(1f, n * 1.12f) * 255f);
                out.setRGB(x, y, (p & 0xFF000000) | r << 16 | g << 8 | b);
            }
        }
        return out;
    }

    /**
     * Feuer niedriger: jedes Bild wird auf den unteren Teil gestaucht, oben bleibt es leer.
     * Die ganze Flamme bleibt zu sehen (mit Spitzen), nur flacher -- bei "Low" ein paar
     * Pixel hoch. Gilt fuer Feuer auf Bloecken und das Feuer vor den Augen beim Brennen.
     */
    static BufferedImage niedriger(BufferedImage src, float hoehe) {
        int w = src.getWidth(), h = src.getHeight(), bh = bildHoehe(src);
        int neu = Math.max(1, Math.round(bh * hoehe)), oben = bh - neu;
        BufferedImage out = leer(src);
        for (int f = 0; f < h; f += bh) {
            for (int y = 0; y < neu; y++) {
                int q0 = y * bh / neu, q1 = Math.max(q0 + 1, (y + 1) * bh / neu);
                for (int x = 0; x < w; x++) {
                    int best = 0;
                    for (int q = q0; q < q1; q++) {
                        int p = src.getRGB(x, f + q);
                        if ((p >>> 24) > (best >>> 24)) best = p;
                    }
                    out.setRGB(x, f + oben + y, best);
                }
            }
        }
        return out;
    }

    /** Kristall ohne Glasrahmen: die beiden Glas-Wuerfel (Textur 0..32 x 0..16) werden durchsichtig. */
    static BufferedImage ohneRahmen(BufferedImage src) {
        int s = Math.max(1, src.getWidth() / 64);
        BufferedImage out = leer(src);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                boolean glas = x < 32 * s && y < 16 * s;
                out.setRGB(x, y, glas ? 0 : src.getRGB(x, y));
            }
        }
        return out;
    }

    /** Groesse der Pop-Animation (1 = normal), null = nichts aendern. */
    public static Float totemGroesse(Map<String, String> wahl) {
        String pop = wahl == null ? null : wahl.get("pop");
        if (pop == null) return null;
        return switch (pop) {
            case "medium" -> 0.6f;
            case "small" -> 0.3f;
            case "off" -> 0.001f;
            default -> null;
        };
    }

    /**
     * Item-Modell des Totems mit kleinerer Pop-Animation. Minecraft zeichnet das Totem beim
     * Aufploppen in der Darstellung "fixed" -- nur die wird verkleinert (und etwas nach
     * unten geschoben, damit es nicht oben am Bildrand klebt). Hand, Inventar und Boden
     * bleiben normal. Achtung: Totems in Rahmen werden mit kleiner.
     */
    public static String totemModell(float s) {
        float unten = -4f * (1f - s);
        return "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": { \"layer0\": \"minecraft:item/totem_of_undying\" },\n"
                + "  \"display\": {\n    \"fixed\": { \"rotation\": [0, 180, 0], \"translation\": [0, " + unten + ", 0], \"scale\": ["
                + s + ", " + s + ", " + s + "] }\n  }\n}\n";
    }
}
