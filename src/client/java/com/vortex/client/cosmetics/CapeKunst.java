package com.vortex.client.cosmetics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Animierte Vortex-Capes -- jedes Bild wird LAUFEND berechnet (kein Bildstreifen
 * im Jar): Nebel, Flammen, Polarlichter ... bewegen sich frei, nichts wiederholt
 * sich sichtbar.
 *
 * Seit 4.34 komplett neu: glatte, abstrakte Motive (Verlaeufe, Licht, Energie)
 * statt Bildchen. Alles ist mit weichen Kanten (1-2 Pixel Uebergang) gezeichnet,
 * damit nichts treppig oder verpixelt wirkt -- auch nicht in den Mip-Stufen.
 *
 * Ergebnis sind zwei Texturen im Vanilla-Cape-Format (64x32, hier 12-fach = 768x384):
 *   - BASIS: normal beleuchtet (wie jedes Cape)
 *   - GLUEHEN: leuchtet selbst (RenderTypes.eyes, also auch nachts)
 *
 * Jedes Cape hat Einstellungen (Stil): Rand, Glanz, Leuchten, Bewegung, Farbton.
 * Sie stecken in der Cape-Id ("anim_vortex--rs" = ohne Rand, ohne Glanz), so sehen
 * Freunde das Cape genauso -- ohne Aenderung am Server.
 *
 * Bewusst ohne Minecraft-Klassen: so laesst sich jedes Cape auch ausserhalb des
 * Spiels als Bild rendern (CapeVorschau im Test).
 *
 * Koordinaten fuer die Motive: x 0 .. 0.625 (links -> rechts, von hinten
 * gesehen), y 0 .. 1 (oben -> unten). Das Cape ist 10 x 16 Pixel, daher 0.625.
 */
public final class CapeKunst {
    private CapeKunst() {}

    public static final int S = 24, W = 64 * S, H = 32 * S;
    static final float BREITE = 0.625f, MITTE = BREITE / 2f;
    /**
     * Ein "Motivpixel" in Motiv-Koordinaten (x und y haben denselben Massstab): Groessen von
     * Sternen, Linien, Funken. Seit 4.77 rechnen die Capes doppelt so fein (S = 24); PX bleibt
     * beim alten Mass, damit jedes Motiv gleich gross aussieht -- nur schaerfer.
     */
    static final float PX = 1f / (16 * 12);
    /** Ein echter Bildpunkt: nur fuer die weiche Kante (Kantenglaettung). */
    static final float PXR = 1f / (16 * S);

    /** Ein Bildpunkt: Grundfarbe (beleuchtet) und Leuchten (Farbe + Deckkraft). */
    public static final class Px {
        float r, g, b;
        float lr, lg, lb, la;
        void farbe(int rgb) { r = ((rgb >> 16) & 255) / 255f; g = ((rgb >> 8) & 255) / 255f; b = (rgb & 255) / 255f; }
        void farbe(float r, float g, float b) { this.r = r; this.g = g; this.b = b; }
        /** Basis Richtung Farbe mischen (k 0..1). */
        void mische(int rgb, float k) {
            if (k <= 0) return;
            if (k > 1) k = 1;
            r += (((rgb >> 16) & 255) / 255f - r) * k;
            g += (((rgb >> 8) & 255) / 255f - g) * k;
            b += ((rgb & 255) / 255f - b) * k;
        }
        /** Licht auf die Basis (wie "Negativ multiplizieren": hellt auf, ueberstrahlt nicht). */
        void licht(int rgb, float k) {
            if (k <= 0) return;
            if (k > 1) k = 1;
            r = 1 - (1 - r) * (1 - ((rgb >> 16) & 255) / 255f * k);
            g = 1 - (1 - g) * (1 - ((rgb >> 8) & 255) / 255f * k);
            b = 1 - (1 - b) * (1 - (rgb & 255) / 255f * k);
        }
        void hell(float f) { r *= f; g *= f; b *= f; }
        /** Leuchten dazu (Farbe, Staerke 0..1); mehrere Lichter addieren sich weich. */
        void leuchte(int rgb, float a) {
            if (a <= 0.004f) return;
            if (a > 1) a = 1;
            float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
            float neu = 1 - (1 - la) * (1 - a);
            float w = a / Math.max(1e-4f, la + a);
            lr += (cr - lr) * w; lg += (cg - lg) * w; lb += (cb - lb) * w;
            la = neu;
        }
        /** Farbe und Licht zugleich: sichtbar am Tag, leuchtet nachts. */
        void strahle(int rgb, float k, float glut) {
            mische(rgb, k);
            leuchte(rgb, k * glut);
        }
        void leer() { r = g = b = 0; lr = lg = lb = la = 0; }
    }

    public interface Maler { void male(float x, float y, float t, Px p); }

    /**
     * Ein Cape. trim = Farbe des Rands, akzent = Farbe von Glanz und Rand-Linie,
     * glanz = Staerke des wandernden Lichts, funkeln = Glitzer-Sterne (0 = keine).
     */
    public record Design(String id, String name, String text, int trim, int akzent, float glanz, float funkeln, Maler maler) {}

    /**
     * Einstellungen eines Capes. In der Id hinter "--": r = ohne Rand, s = ohne Glanz,
     * g = ohne Leuchten, m = ohne Bewegung, h1..h11 = Farbton um 30-Grad-Schritte gedreht.
     */
    public record Stil(boolean rand, boolean glanz, boolean leuchten, boolean bewegt, int farbton) {
        /** Seit 4.78 ohne Rand: der Rahmen ist eine Option ("R" in der Id), nicht mehr Standard. */
        public static final Stil STANDARD = new Stil(false, true, true, true, 0);

        public static Stil aus(String id) {
            if (id == null) return STANDARD;
            int i = id.indexOf("--");
            if (i < 0) return STANDARD;
            String f = id.substring(i + 2);
            int h = 0;
            int hi = f.indexOf('h');
            if (hi >= 0) {
                try { h = Math.floorMod(Integer.parseInt(f.substring(hi + 1).replaceAll("[^0-9].*$", "")), 12); } catch (NumberFormatException ignored) {}
                f = f.substring(0, hi);
            }
            return new Stil(f.indexOf('R') >= 0, f.indexOf('s') < 0, f.indexOf('g') < 0, f.indexOf('m') < 0, h);
        }

        /** "" fuer Standard, sonst "--" + Kennbuchstaben. */
        public String endung() {
            StringBuilder b = new StringBuilder();
            if (rand) b.append('R');
            if (!glanz) b.append('s');
            if (!leuchten) b.append('g');
            if (!bewegt) b.append('m');
            if (farbton != 0) b.append('h').append(farbton);
            return b.isEmpty() ? "" : "--" + b;
        }

        public Stil mitRand(boolean v) { return new Stil(v, glanz, leuchten, bewegt, farbton); }
        public Stil mitGlanz(boolean v) { return new Stil(rand, v, leuchten, bewegt, farbton); }
        public Stil mitLeuchten(boolean v) { return new Stil(rand, glanz, v, bewegt, farbton); }
        public Stil mitBewegt(boolean v) { return new Stil(rand, glanz, leuchten, v, farbton); }
        public Stil mitFarbton(int v) { return new Stil(rand, glanz, leuchten, bewegt, Math.floorMod(v, 12)); }
    }

    private static final Map<String, Design> ALLE = new LinkedHashMap<>();
    /** Alte Ids (bis 4.33) -> neues Cape, damit eine alte Auswahl nicht einfach verschwindet. */
    private static final Map<String, String> FRUEHER = Map.of(
            "anim_flame", "anim_nether", "anim_pumpkin", "anim_nether", "anim_sunflower", "anim_eclipse",
            "anim_ghost", "anim_phantom", "anim_skull", "anim_phantom");

    public static Map<String, Design> alle() { return ALLE; }

    /** Id ohne Stil-Endung (und alte Ids auf die neuen umgelenkt). */
    public static String basisId(String id) {
        if (id == null) return null;
        int i = id.indexOf("--");
        String b = i < 0 ? id : id.substring(0, i);
        return FRUEHER.getOrDefault(b, b);
    }

    /** Cape-Id mit diesem Stil. */
    public static String mitStil(String id, Stil s) {
        return basisId(id) + s.endung();
    }

    public static Design design(String id) { return id == null ? null : ALLE.get(basisId(id)); }

    private static void neu(String id, String name, String text, int trim, int akzent, float glanz, float funkeln, Maler m) {
        ALLE.put(id, new Design(id, name, text, trim, akzent, glanz, funkeln, m));
    }

    public static void rendern(Design d, float t, int[] basis, int[] glow) {
        rendern(d, Stil.STANDARD, t, basis, glow);
    }

    /**
     * Ein Bild zur Zeit t (Sekunden) in die beiden Puffer (W*H, ABGR wie
     * NativeImage im Speicher). Gefuellt werden nur die genutzten Bereiche:
     * Aussen, Innen, Kanten, Elytra.
     */
    public static void rendern(Design d, Stil stil, float t, int[] basis, int[] glow) {
        rendern(d, stil, t, basis, glow, new int[10 * S * 16 * S], new int[10 * S * 16 * S], -1);
    }

    /**
     * Wie oben, mit eigenen Puffern fuer die rohe Aussenseite (rohB/rohG, 10S x 16S).
     * zeilen = -1: alle Zeilen neu; 0 oder 1: nur jede zweite (gerade/ungerade) -- fuer
     * langsame Rechner, die anderen Zeilen bleiben vom letzten Bild (seit 4.77).
     */
    public static void rendern(Design d, Stil stil, float t, int[] basis, int[] glow, int[] rohB, int[] rohG, int zeilen) {
        final float zeit = stil.bewegt ? t : 6.5f;
        int ax = S, ay = S, aw = 10 * S, ah = 16 * S;
        int schritt = zeilen < 0 ? 1 : 2, start = zeilen < 0 ? 0 : zeilen;
        java.util.stream.IntStream.range(0, (ah - start + schritt - 1) / schritt).parallel().forEach(n -> {
            int j = start + n * schritt;
            Px q = new Px();
            float y = (j + 0.5f) / ah;
            int zeile = j * aw;
            for (int i = 0; i < aw; i++) {
                float x = (i + 0.5f) / aw * BREITE;
                punkt(d, stil, x, y, zeit, q);
                if (!stil.leuchten) {
                    q.r += (1 - q.r) * q.lr * q.la * 0.85f;
                    q.g += (1 - q.g) * q.lg * q.la * 0.85f;
                    q.b += (1 - q.b) * q.lb * q.la * 0.85f;
                    q.la = 0;
                }
                float dz = BAYER[(j & 3) * 4 + (i & 3)];
                rohB[zeile + i] = abgr(q.r + dz, q.g + dz, q.b + dz, 1f);
                rohG[zeile + i] = abgr(q.lr, q.lg, q.lb, q.la);
            }
        });
        for (int j = 0; j < ah; j++) {
            System.arraycopy(rohB, j * aw, basis, (ay + j) * W + ax, aw);
            System.arraycopy(rohG, j * aw, glow, (ay + j) * W + ax, aw);
        }
        verfeinern(basis, ax, ay, aw, ah);
        float tr = ((d.trim >> 16) & 255) / 255f, tg = ((d.trim >> 8) & 255) / 255f, tb = (d.trim & 255) / 255f;
        for (int j = 0; j < ah; j++) {
            int zeile = (ay + j) * W;
            for (int i = 0; i < aw; i++) {
                int q = basis[zeile + ax + (aw - 1 - i)];
                float r = (q & 255) / 255f, g = ((q >> 8) & 255) / 255f, b = ((q >> 16) & 255) / 255f;
                basis[zeile + 12 * S + i] = abgr(r * 0.3f + tr * 0.07f, g * 0.3f + tg * 0.07f, b * 0.3f + tb * 0.07f, 1f);
                int gq = glow[zeile + ax + (aw - 1 - i)];
                int ga = (gq >>> 24) / 4;
                glow[zeile + 12 * S + i] = (ga << 24) | (gq & 0xFFFFFF);
            }
        }
        Px p = new Px();
        for (int j = 0; j < 17 * S; j++) {
            float y = Math.max(0, (j - S + 0.5f) / ah);
            int quelle = (ay + Math.min(ah - 1, Math.max(0, j - S))) * W;
            for (int i = 0; i < S; i++) {
                int[][] spalten = { { i, ax }, { 11 * S + i, ax + aw - 1 } };
                for (int[] sp : spalten) {
                    if (stil.rand) {
                        borte(d, y, zeit, (i + 0.5f) / S, p);
                        basis[j * W + sp[0]] = abgr(p.r, p.g, p.b, 1f);
                        glow[j * W + sp[0]] = stil.leuchten ? abgr(p.lr, p.lg, p.lb, p.la) : 0;
                    } else {
                        basis[j * W + sp[0]] = dunkler(basis[quelle + sp[1]], 0.55f);
                        glow[j * W + sp[0]] = 0;
                    }
                }
            }
        }
        for (int j = 0; j < S; j++) {
            for (int i = S; i < 21 * S; i++) {
                if (stil.rand) {
                    borte(d, (i % (10 * S)) / (10f * S), zeit, (j + 0.5f) / S, p);
                    basis[j * W + i] = abgr(p.r, p.g, p.b, 1f);
                    glow[j * W + i] = stil.leuchten ? abgr(p.lr, p.lg, p.lb, p.la) : 0;
                } else {
                    boolean oben = i < 11 * S;
                    int sx = ax + Math.min(aw - 1, (oben ? i - S : i - 11 * S));
                    int sy = oben ? ay : ay + ah - 1;
                    basis[j * W + i] = dunkler(basis[sy * W + sx], 0.55f);
                    glow[j * W + i] = 0;
                }
            }
        }
        elytraAusCape(basis, W, S, false);
        elytraAusCape(glow, W, S, true);
        if (stil.farbton != 0) {
            farbtonDrehen(basis, stil.farbton * 30f);
            farbtonDrehen(glow, stil.farbton * 30f);
        }
    }

    /**
     * Elytra-Flaechen aus dem Cape bauen (seit 4.64). Die Elytra ist im Modell ein
     * Kasten 10 x 20 x 2 ab Textur (22, 0) -- jeder Fluegel nutzt dieselben Flaechen,
     * der rechte gespiegelt:
     *
     *   aussen (von hinten zu sehen)  x 36..46, y 2..22  <- Cape aussen  x 1..11,  y 1..17
     *   innen (zum Koerper)           x 24..34, y 2..22  <- Cape innen   x 12..22, y 1..17
     *   Kanten links / rechts         x 22..24 / 34..36  <- Cape-Kanten  x 0..1 / 11..12
     *   oben / unten                  x 24..34 / 34..44, y 0..2 <- Cape oben / unten
     *
     * Vorher wurde das Motiv einfach quer ueber den ganzen Bereich gezogen: auf der
     * Aussenseite fehlte links ein Sechstel, oben ein Stueck, und Kanten und Innenseite
     * zeigten Motivreste. Kanalreihenfolge egal (nur kopiert). s = Pixel je Textur-Pixel.
     */
    public static void elytraAusCape(int[] px, int breite, int s) {
        elytraAusCape(px, breite, s, false);
    }

    /**
     * Wie oben, dazu (seit 4.65) die Fluegelform: Aussen- und Innenseite werden auf einen
     * Fluegel zugeschnitten (alles daneben durchsichtig -- die Elytra wird mit Ausschnitt
     * gezeichnet), mit dunklem Rand. Die schmalen Kanten bleiben leer, sonst stuenden dort
     * gerade Streifen ueber die Form hinaus. leuchten = Leucht-Puffer (ohne Rand).
     */
    public static void elytraAusCape(int[] px, int breite, int s, boolean leuchten) {
        if (VORLAGE != null) { elytraAusVorlage(px, breite, s, leuchten, true); return; }
        kopie(px, breite, s, 1, 1, 10, 16, 36, 2, 10, 20);
        kopie(px, breite, s, 12, 1, 10, 16, 24, 2, 10, 20);
        elytraMaske(px, breite, s, leuchten);
    }

    /** Nur die Fluegelform ausschneiden (fuer Cape-Bilder mit eigener, aber eckiger Elytra). */
    public static void elytraMaske(int[] px, int breite, int s, boolean leuchten) {
        if (VORLAGE != null) { elytraAusVorlage(px, breite, s, leuchten, false); return; }
        int fw = 10 * s, fh = 20 * s;
        FluegelForm form = fluegelForm(s);
        for (int j = 0; j < fh; j++) {
            for (int i = 0; i < fw; i++) {
                int k = j * fw + i;
                int aussen = (2 * s + j) * breite + 36 * s + i, innen = (2 * s + j) * breite + 24 * s + (fw - 1 - i);
                if (!form.drin[k]) { px[aussen] = 0; px[innen] = 0; continue; }
                if (leuchten) continue;
                px[aussen] = hell(px[aussen], form.schatten[k]);
                px[innen] = hell(px[innen], form.schatten[k]);
                if (form.rand[k]) { px[aussen] = randFarbe(px[aussen]); px[innen] = randFarbe(px[innen]); }
            }
        }
        for (int j = 0; j < 22 * s; j++) {
            for (int i = 22 * s; i < 46 * s; i++) {
                boolean flaeche = j >= 2 * s && ((i >= 24 * s && i < 34 * s) || i >= 36 * s);
                if (!flaeche) px[j * breite + i] = 0;
            }
        }
    }

    /** Fluegelform ohne Vorlage, einmal je Aufloesung berechnet (vorher in jedem Bild). */
    private record FluegelForm(boolean[] drin, boolean[] rand, float[] schatten) {}
    private static final Map<Integer, FluegelForm> FORMEN = new java.util.concurrent.ConcurrentHashMap<>();

    private static FluegelForm fluegelForm(int s) {
        return FORMEN.computeIfAbsent(s, k -> {
            int fw = 10 * s, fh = 20 * s;
            boolean[] drin = new boolean[fw * fh], rand = new boolean[fw * fh];
            float[] schatten = new float[fw * fh];
            for (int j = 0; j < fh; j++)
                for (int i = 0; i < fw; i++) drin[j * fw + i] = imFluegel((i + 0.5f) / fw, (j + 0.5f) / fh);
            int r = Math.max(1, Math.round(s * 0.4f));
            for (int j = 0; j < fh; j++) {
                for (int i = 0; i < fw; i++) {
                    if (!drin[j * fw + i]) continue;
                    float fu = (i + 0.5f) / fw, fv = (j + 0.5f) / fh;
                    float reihe = fv * 5.2f + fu * 1.3f + 0.05f * sinf(fu * 19f);
                    float in = reihe - (float) Math.floor(reihe);
                    float fuge = in > 0.9f ? 0.5f : in < 0.05f ? 0.72f : 1f;
                    schatten[j * fw + i] = fuge * (1.12f - 0.3f * in);
                    boolean am = false;
                    for (int dj = -r; dj <= r && !am; dj++)
                        for (int di = -r; di <= r && !am; di++) {
                            int x = i + di, y = j + dj;
                            am = x >= 0 && y >= 0 && x < fw && y < fh && !drin[y * fw + x];
                        }
                    rand[j * fw + i] = am;
                }
            }
            return new FluegelForm(drin, rand, schatten);
        });
    }

    /**
     * Elytra nach Vorlage, vorberechnet: je Zielpixel die Quelle im Cape (oder -1 =
     * vorhandene Farbe), der Helligkeitsfaktor und ob es durchsichtig bleibt.
     */
    private record VorlagenPlan(int[] vorlage, int breite, int s, boolean ausCape, int[] ziel, int[] quelle, float[] faktor, boolean[] kante) {}
    private static volatile VorlagenPlan PLAN_CAPE, PLAN_MASKE;

    private static VorlagenPlan plan(int breite, int s, boolean ausCape) {
        int[] v = VORLAGE;
        VorlagenPlan alt = ausCape ? PLAN_CAPE : PLAN_MASKE;
        if (alt != null && alt.vorlage() == v && alt.breite() == breite && alt.s() == s) return alt;
        int vw = VORLAGE_W, vh = VORLAGE_H;
        float mittel = VORLAGE_HELL, streu = VORLAGE_STREU;
        int n = 22 * s * 24 * s, k = 0;
        int[] ziel = new int[n], quelle = new int[n];
        float[] faktor = new float[n];
        boolean[] kante = new boolean[n];
        for (int y = 0; y < 22 * s; y++) {
            int ty = Math.min(vh - 1, y * vh / (32 * s));
            for (int x = 22 * s; x < 46 * s; x++, k++) {
                int tx = Math.min(vw - 1, x * vw / (64 * s));
                int t = v[ty * vw + tx];
                ziel[k] = y * breite + x;
                if ((t >>> 24) < 128) { faktor[k] = -1f; continue; }
                if (ausCape) {
                    float fx = x / (float) s, fy = y / (float) s;
                    float u = fx >= 34f ? (fx - 34f) / 12f : (fx - 22f) / 12f;
                    float w = (fy - 2f) / 20f;
                    u = 0.08f + 0.84f * Math.max(0f, Math.min(0.999f, u));
                    w = 0.05f + 0.9f * Math.max(0f, Math.min(0.999f, w));
                    boolean seite = y < 2 * s || x < 24 * s || (x >= 34 * s && x < 36 * s);
                    kante[k] = seite;
                    quelle[k] = (s + (int) (w * 16 * s)) * breite + s + (seite ? 5 * s : (int) (u * 10 * s));
                } else quelle[k] = -1;
                float f = 1f + 0.2f * (helligkeit(t) - mittel) / streu;
                boolean rand = false;
                for (int d = -1; d <= 1 && !rand; d += 2) {
                    int nx = Math.min(vw - 1, Math.max(0, tx + d)), ny = Math.min(vh - 1, Math.max(0, ty + d));
                    rand = (v[ty * vw + nx] >>> 24) < 128 || (v[ny * vw + tx] >>> 24) < 128;
                }
                if (rand) f *= 0.72f;
                faktor[k] = Math.max(0.4f, Math.min(1.5f, f));
            }
        }
        VorlagenPlan p = new VorlagenPlan(v, breite, s, ausCape, ziel, quelle, faktor, kante);
        if (ausCape) PLAN_CAPE = p; else PLAN_MASKE = p;
        return p;
    }

    /**
     * Die Elytra-Textur des Spiels (bzw. des Resource Packs) als Vorlage: Form und Federn
     * kommen von dort, die Farben vom Cape. Gesetzt von AnimCapes beim ersten Cape (ARGB oder
     * ABGR -- es zaehlen nur Deckkraft und Helligkeit). null = eigene Fluegelform (Rueckfall).
     */
    private static volatile int[] VORLAGE;
    private static volatile int VORLAGE_W, VORLAGE_H;
    private static volatile float VORLAGE_HELL = 0.5f, VORLAGE_STREU = 0.1f;

    public static void setzeElytraVorlage(int[] px, int w, int h) {
        if (px == null || w < 64 || h < 32 || w != h * 2) { VORLAGE = null; return; }
        double summe = 0, quadrat = 0;
        int n = 0;
        for (int y = 0; y < h * 22 / 32; y++)
            for (int x = w * 22 / 64; x < w * 46 / 64; x++) {
                int q = px[y * w + x];
                if ((q >>> 24) < 128) continue;
                float l = helligkeit(q);
                summe += l;
                quadrat += l * l;
                n++;
            }
        if (n < 20) { VORLAGE = null; return; }
        double mittel = summe / n;
        VORLAGE_HELL = (float) Math.max(0.08, mittel);
        VORLAGE_STREU = (float) Math.max(0.02, Math.sqrt(Math.max(0, quadrat / n - mittel * mittel)));
        VORLAGE_W = w;
        VORLAGE_H = h;
        VORLAGE = px;
    }

    public static boolean hatElytraVorlage() { return VORLAGE != null; }

    private static float helligkeit(int q) {
        return (((q >> 16) & 255) + ((q >> 8) & 255) + (q & 255)) / 765f;
    }

    /**
     * Elytra-Bereich (x 22..46, y 0..22) nach Vorlage fuellen: wo die Vorlage durchsichtig
     * ist, bleibt es durchsichtig (so hat die Elytra ihre echte Form, und wie bei Minecraft
     * ist nur eine Flaeche sichtbar -- keine hohle Kiste aus zwei Platten). Wo sie deckt,
     * kommt die Cape-Farbe, mit den Hell-Dunkel-Stufen der Vorlage: so sieht man die Federn.
     * ausCape = Farbe aus der Cape-Aussenseite holen, sonst die vorhandene Farbe behalten.
     */
    private static void elytraAusVorlage(int[] px, int breite, int s, boolean leuchten, boolean ausCape) {
        VorlagenPlan plan = plan(breite, s, ausCape);
        int[] ziel = plan.ziel(), quelle = plan.quelle();
        float[] faktor = plan.faktor();
        boolean[] kante = plan.kante();
        for (int k = 0; k < ziel.length; k++) {
            int z = ziel[k];
            float f = faktor[k];
            if (f < 0f) { px[z] = 0; continue; }
            int q = quelle[k] >= 0 ? px[quelle[k]] : px[z];
            if (kante[k]) { px[z] = leuchten ? 0 : 0xFF000000 | (hell(q, 0.42f + 0.18f * (f - 1f)) & 0xFFFFFF); continue; }
            px[z] = leuchten ? q : 0xFF000000 | (hell(q, f) & 0xFFFFFF);
        }
    }

    /**
     * Fluegelform auf der Aussenseite (u 0 = Schulter .. 1 = Spitze, v 0 = oben .. 1 = unten).
     * Die Elytra haengt an der Schulter und reicht schraeg ueber den Ruecken: oben sitzt der
     * Fluegel an der Schulter, nach unten wandert er zur Spitze hin. Oben zur Spitze hin
     * ein runder Bogen (dort entsteht zwischen beiden Fluegeln das V), an der Schulterseite
     * und unten enden die Federn in Boegen.
     */
    static boolean imFluegel(float u, float v) {
        if (v < 0.012f) return false;
        float innen = 0.30f * (float) Math.pow(v, 1.6f) + 0.05f * (float) Math.abs(Math.sin(v * Math.PI * 4f)) * v;
        if (u < innen) return false;
        if (v < 0.45f && u > 0.5f + 0.5f * (float) Math.sqrt(v / 0.45f)) return false;
        float unten = 0.985f - 0.06f * (float) Math.abs(Math.sin((u - 0.1f) * Math.PI * 3.3f)) * (0.5f + 0.5f * u);
        return v <= unten;
    }

    /** Farbe heller/dunkler (f), Deckkraft bleibt (Kanalreihenfolge egal). */
    private static int hell(int q, float f) {
        int a = q >>> 24;
        int c1 = Math.min(255, (int) (((q >> 16) & 255) * f)), c2 = Math.min(255, (int) (((q >> 8) & 255) * f)), c3 = Math.min(255, (int) ((q & 255) * f));
        return (a << 24) | (c1 << 16) | (c2 << 8) | c3;
    }

    /** Rand der Fluegelform: dieselbe Farbe, deutlich dunkler (Kanalreihenfolge egal). */
    private static int randFarbe(int q) {
        int a = q >>> 24, c1 = (q >> 16) & 255, c2 = (q >> 8) & 255, c3 = q & 255;
        return (a << 24) | ((c1 * 2 / 5) << 16) | ((c2 * 2 / 5) << 8) | (c3 * 2 / 5);
    }

    private static void kopie(int[] px, int breite, int s, int qx, int qy, int qw, int qh, int zx, int zy, int zw, int zh) {
        int QW = qw * s, QH = qh * s, ZW = zw * s, ZH = zh * s;
        for (int j = 0; j < ZH; j++) {
            int sy = qy * s + Math.min(QH - 1, (int) ((j + 0.5f) * QH / ZH));
            int ziel = (zy * s + j) * breite + zx * s, quelle = sy * breite + qx * s;
            for (int i = 0; i < ZW; i++) px[ziel + i] = px[quelle + Math.min(QW - 1, (int) ((i + 0.5f) * QW / ZW))];
        }
    }

    /**
     * Was ein fertiges Cape-Bild (Verzeichnis, eigenes Bild, Freunde) fuer die Elytra braucht:
     * 2 = neu aus dem Cape bauen (Aussenseite fast leer oder nur eine Farbe),
     * 1 = nur zuschneiden (voll deckend -- saehe aus wie ein Brett), 0 = nichts (eigene Form).
     */
    public static int elytraBedarf(int[] px, int breite, int s) {
        if (elytraFehlt(px, breite, s)) return 2;
        int leer = 0, n = 0;
        for (int j = 2 * s; j < 22 * s; j++)
            for (int i = 36 * s; i < 46 * s; i++) { n++; if ((px[j * breite + i] >>> 24) < 128) leer++; }
        return leer * 33 < n ? 1 : 0;
    }

    /** Aussenseite der Elytra zu 60 % leer oder einfarbig. */
    public static boolean elytraFehlt(int[] px, int breite, int s) {
        int leer = 0, n = 0;
        double summe = 0, quadrat = 0;
        for (int j = 2 * s; j < 22 * s; j++) {
            for (int i = 36 * s; i < 46 * s; i++) {
                int q = px[j * breite + i];
                n++;
                if ((q >>> 24) < 128) { leer++; continue; }
                double l = ((q >> 16) & 255) * 0.3 + ((q >> 8) & 255) * 0.59 + (q & 255) * 0.11;
                summe += l; quadrat += l * l;
            }
        }
        if (leer * 5 > n * 3) return true;
        int voll = n - leer;
        double mittel = summe / voll;
        return quadrat / voll - mittel * mittel < 9;
    }

    private static int dunkler(int abgr, float f) {
        int r = (int) ((abgr & 255) * f), g = (int) (((abgr >> 8) & 255) * f), b = (int) (((abgr >> 16) & 255) * f);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /** Farbton aller Pixel drehen (YIQ, Helligkeit bleibt). Durchsichtige Pixel bleiben, wie sie sind. */
    static void farbtonDrehen(int[] px, float grad) {
        float c = (float) Math.cos(Math.toRadians(grad)), s = (float) Math.sin(Math.toRadians(grad));
        for (int k = 0; k < px.length; k++) {
            int q = px[k];
            if ((q >>> 24) == 0) continue;
            float r = (q & 255) / 255f, g = ((q >> 8) & 255) / 255f, b = ((q >> 16) & 255) / 255f;
            float yy = 0.299f * r + 0.587f * g + 0.114f * b;
            float ii = 0.596f * r - 0.274f * g - 0.322f * b;
            float qq = 0.211f * r - 0.523f * g + 0.312f * b;
            float i2 = ii * c - qq * s, q2 = ii * s + qq * c;
            float nr = yy + 0.956f * i2 + 0.621f * q2, ng = yy - 0.272f * i2 - 0.647f * q2, nb = yy - 1.106f * i2 + 1.703f * q2;
            px[k] = (q & 0xFF000000) | (k255(nb) << 16) | (k255(ng) << 8) | k255(nr);
        }
    }

    /**
     * Feinschliff auf dem fertigen Bild: ganz leichte Praegung (Licht von links oben),
     * etwas mehr Farbe und eine sanfte S-Kurve fuer Kontrast. Bewusst schwach --
     * staerker wirkt es koernig.
     */
    private static void verfeinern(int[] basis, int ax, int ay, int aw, int ah) {
        float[] lum = new float[aw * ah];
        for (int j = 0; j < ah; j++) {
            int zeile = (ay + j) * W + ax;
            for (int i = 0; i < aw; i++) {
                int q = basis[zeile + i];
                lum[j * aw + i] = ((q & 255) * 0.30f + ((q >> 8) & 255) * 0.59f + ((q >> 16) & 255) * 0.11f) / 255f;
            }
        }
        final int e = Math.max(1, S / 6);
        java.util.stream.IntStream.range(0, ah).parallel().forEach(j -> {
            int zeile = (ay + j) * W + ax;
            int j0 = Math.max(0, j - e), j1 = Math.min(ah - 1, j + e);
            for (int i = 0; i < aw; i++) {
                int i0 = Math.max(0, i - e), i1 = Math.min(aw - 1, i + e);
                float grad = lum[j0 * aw + i0] - lum[j1 * aw + i1];
                float licht = 1f + Math.max(-0.1f, Math.min(0.1f, grad * 0.7f));
                int q = basis[zeile + i];
                float r = (q & 255) / 255f, g = ((q >> 8) & 255) / 255f, b = ((q >> 16) & 255) / 255f;
                float l = r * 0.3f + g * 0.59f + b * 0.11f;
                r = l + (r - l) * 1.08f; g = l + (g - l) * 1.08f; b = l + (b - l) * 1.08f;
                r = kurve(r * licht); g = kurve(g * licht); b = kurve(b * licht);
                basis[zeile + i] = abgr(r, g, b, 1f);
            }
        });
    }

    /**
     * Mip-Stufen (seit 4.33): ziel[0] = das fertige Bild, jede weitere Stufe halb so
     * gross, je 2x2 Pixel gemittelt. Gemittelt wird mit Deckkraft gewichtet -- so
     * dunkeln leere (durchsichtige) Bereiche die Raender nicht ab, und das Leuchten
     * bleibt gleich hell. Ohne Minecraft-Klassen (im Test pruefbar).
     */
    public static void stufen(int[] quelle, int[][] ziel) {
        System.arraycopy(quelle, 0, ziel[0], 0, quelle.length);
        int w = W, h = H;
        for (int s = 1; s < ziel.length; s++) {
            int[] a = ziel[s - 1], b = ziel[s];
            int nw = Math.max(1, w >> 1), nh = Math.max(1, h >> 1);
            for (int y = 0; y < nh; y++) {
                int z0 = (2 * y) * w, z1 = Math.min(h - 1, 2 * y + 1) * w;
                for (int x = 0; x < nw; x++) {
                    int x0 = 2 * x, x1 = Math.min(w - 1, 2 * x + 1);
                    int p0 = a[z0 + x0], p1 = a[z0 + x1], p2 = a[z1 + x0], p3 = a[z1 + x1];
                    int a0 = p0 >>> 24, a1 = p1 >>> 24, a2 = p2 >>> 24, a3 = p3 >>> 24;
                    int summe = a0 + a1 + a2 + a3;
                    if (summe == 0) { b[y * nw + x] = 0; continue; }
                    int r = ((p0 & 255) * a0 + (p1 & 255) * a1 + (p2 & 255) * a2 + (p3 & 255) * a3 + summe / 2) / summe;
                    int g = (((p0 >> 8) & 255) * a0 + ((p1 >> 8) & 255) * a1 + ((p2 >> 8) & 255) * a2 + ((p3 >> 8) & 255) * a3 + summe / 2) / summe;
                    int bl = (((p0 >> 16) & 255) * a0 + ((p1 >> 16) & 255) * a1 + ((p2 >> 16) & 255) * a2 + ((p3 >> 16) & 255) * a3 + summe / 2) / summe;
                    b[y * nw + x] = ((summe + 2) / 4) << 24 | bl << 16 | g << 8 | r;
                }
            }
            w = nw; h = nh;
        }
    }

    /** Puffer fuer alle Mip-Stufen eines Bilds. */
    public static int[][] stufenPuffer(int anzahl) {
        int[][] p = new int[anzahl][];
        for (int s = 0; s < anzahl; s++) p[s] = new int[Math.max(1, W >> s) * Math.max(1, H >> s)];
        return p;
    }

    private static float kurve(float v) {
        v = v < 0 ? 0 : v > 1 ? 1 : v;
        return v + 0.12f * v * (1 - v) * (2 * v - 1) * 2f;
    }

    /** Ein Punkt der Aussenseite: Motiv + sanfter Stoff-Schatten + Rand + Glanz + Glitzern. */
    static void punkt(Design d, Stil stil, float x, float y, float t, Px p) {
        p.leer();
        d.maler.male(x, y, t, p);
        float wind = sinf(t * 0.7f + y * 2.2f) * 0.35f * y;
        float phase = x / BREITE * 3.0f * (float) Math.PI * (0.85f + 0.3f * y) + wind;
        float falte = 1f + 0.05f * (0.3f + 0.7f * y) * sinf(phase);
        float rand0 = Math.min(Math.min(x, BREITE - x) / BREITE, Math.min(y, 1 - y));
        float vignette = 0.86f + 0.14f * glatt(0f, 0.25f, rand0);
        float kragen = 0.8f + 0.2f * glatt(0f, 0.07f, y);
        p.hell(falte * vignette * kragen);
        if (stil.rand) rand(d, x, y, t, p);
        if (stil.glanz && d.glanz > 0) {
            float pos = (t * 0.26f) % 2.1f - 0.5f;
            float s = x * 0.75f + y * 0.6f - pos;
            if (s > -0.3f && s < 0.3f) {
                float band = expf(-s * s / 0.0009f) * 0.7f + 0.3f * expf(-s * s / 0.01f);
                p.leuchte(mischRgb(d.akzent, 0xFFFFFF, 0.65f), band * d.glanz * 0.6f);
            }
        }
        if (d.funkeln > 0) {
            float f = glitzer(x, y, t, d.funkeln);
            if (f > 0) p.leuchte(mischRgb(d.akzent, 0xFFFFFF, 0.6f), f);
        }
    }

    /** Breite des Rands (in Motiv-Einheiten, ca. 5 Pixel). */
    static final float RAND = 0.026f;

    /**
     * Rand: schmales Metallband in der Trim-Farbe (oben/links heller, unten/rechts dunkler),
     * innen eine feine leuchtende Linie in der Akzentfarbe und ein weicher Schatten zum
     * Motiv hin. Ein Lichtpunkt wandert langsam um das Cape.
     */
    static void rand(Design d, float x, float y, float t, Px p) {
        float l = x, r = BREITE - x, o = y, u = 1 - y;
        float rand = Math.min(Math.min(l, r), Math.min(o, u));
        if (rand > RAND + 0.03f) return;
        if (rand > RAND) {
            p.hell(1f - 0.35f * glatt(RAND + 0.022f, RAND, rand));
        }
        float deckung = glatt(RAND + 0.6f * PX, RAND - 0.6f * PX, rand);
        if (deckung > 0) {
            float q = Math.max(0, Math.min(1, rand / RAND));
            float seite = rand == l ? 0.12f : rand == o ? 0.16f : rand == r ? -0.1f : -0.14f;
            float wulst = 0.55f + 0.5f * (float) Math.pow(Math.sin(q * Math.PI), 0.7);
            int tc = d.trim;
            float rr = ((tc >> 16) & 255) / 255f, gg = ((tc >> 8) & 255) / 255f, bb = (tc & 255) / 255f;
            float hell = wulst + seite;
            float umfang = rand == o ? x : rand == r ? BREITE + y : rand == u ? BREITE + 1 + (BREITE - x) : 2 * BREITE + 1 + (1 - y);
            float pos = (t * 0.35f) % 3.25f;
            float dd = Math.abs(umfang - pos);
            dd = Math.min(dd, 3.25f - dd);
            float glanz = expf(-dd * dd / 0.004f);
            float nr = Math.min(1, rr * hell + glanz * 0.45f), ng = Math.min(1, gg * hell + glanz * 0.45f), nb = Math.min(1, bb * hell + glanz * 0.45f);
            p.r += (nr - p.r) * deckung; p.g += (ng - p.g) * deckung; p.b += (nb - p.b) * deckung;
            p.leuchte(tc, (0.12f + 0.4f * glanz) * deckung);
        }
        float linie = glatt(0.9f * PX, 0.2f * PX, Math.abs(rand - RAND - 0.9f * PX));
        if (linie > 0) p.strahle(mischRgb(d.akzent, 0xFFFFFF, 0.25f), linie * 0.8f, 0.6f);
    }

    /** Rand fuer die Kanten (Dicke des Capes). u = Lage entlang, q = 0 aussen .. 1 innen. */
    static void borte(Design d, float u, float t, float q, Px p) {
        p.leer();
        p.farbe(d.trim);
        float metall = 0.62f + 0.42f * sinf(Math.min(1f, Math.max(0f, q)) * Math.PI);
        p.hell(metall);
        float s = u - ((t * 0.5f) % 2.4f - 0.2f);
        float g = expf(-s * s / 0.004f);
        p.r += g * 0.4f; p.g += g * 0.4f; p.b += g * 0.4f;
        p.leuchte(d.trim, 0.15f + 0.35f * g);
    }

    /** Glitzer: kleine Sterne mit vier weichen Strahlen, die auf- und abblenden (mind. ~1,5 Pixel breit). */
    static float glitzer(float x, float y, float t, float dichte) {
        float n = 5f * dichte;
        float gx = x * n, gy = y * n;
        int cx = fl(gx), cy = fl(gy);
        float h = hash(cx, cy + 131);
        if (h > 0.4f) return 0;
        float zyklus = 2.4f + h * 3f;
        float ph = ((t + h * 17f) % zyklus) / zyklus;
        if (ph > 0.35f) return 0;
        float stk = sinf(ph / 0.35f * Math.PI);
        float sx = (cx + 0.2f + 0.6f * hash(cx + 7, cy)) / n, sy = (cy + 0.2f + 0.6f * hash(cx, cy + 19)) / n;
        float dx = Math.abs(x - sx) / PX, dy = Math.abs(y - sy) / PX;
        float kern = expf(-(dx * dx + dy * dy) / 3.5f);
        float strahl = (float) (Math.exp(-dx / 4.5f) * Math.exp(-dy * dy / 1.4f) + Math.exp(-dy / 4.5f) * Math.exp(-dx * dx / 1.4f));
        return Math.min(1f, (kern + strahl * 0.6f) * stk);
    }

    static int fl(float v) { int i = (int) v; return v < i ? i - 1 : i; }

    private static final float ZWEI_PI = 6.2831855f, PI_F = 3.1415927f, HALB_PI = 1.5707964f;

    /** Schneller Sinus (Fehler unter 1e-5) -- pro Bild werden Millionen gebraucht. */
    static float sinf(float x) {
        x -= ZWEI_PI * Math.round(x / ZWEI_PI);
        if (x > HALB_PI) x = PI_F - x;
        else if (x < -HALB_PI) x = -PI_F - x;
        float x2 = x * x;
        return x * (1f + x2 * (-1f / 6f + x2 * (1f / 120f + x2 * (-1f / 5040f + x2 * (1f / 362880f)))));
    }

    static float cosf(float x) { return sinf(x + HALB_PI); }
    static float sinf(double x) { return sinf((float) x); }
    static float cosf(double x) { return sinf((float) x + HALB_PI); }
    static float expf(double x) { return expf((float) x); }

    /** Schnelle e-Funktion (relativer Fehler unter 1e-5). */
    static float expf(float x) {
        if (x < -87f) return 0f;
        if (x > 88f) x = 88f;
        float y = x * 1.442695f;
        int i = fl(y);
        float f = y - i;
        float p = 1f + f * (0.6931472f + f * (0.2402265f + f * (0.0555041f + f * (0.0096181f + f * 0.0013334f))));
        return Float.intBitsToFloat((i + 127) << 23) * p;
    }

    static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216f;
    }

    static float rausch(float x, float y) {
        int ix = fl(x), iy = fl(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), e = hash(ix + 1, iy + 1);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + e) * fx * fy;
    }

    static float fbm(float x, float y, int okt) {
        float s = 0, a = 0.5f, n = 0;
        for (int i = 0; i < okt; i++) {
            s += a * rausch(x, y);
            n += a;
            x = x * 2.03f + 17.1f;
            y = y * 2.03f + 9.7f;
            a *= 0.5f;
        }
        return s / n;
    }

    static float glatt(float a, float b, float v) {
        float k = (v - a) / (b - a);
        k = k < 0 ? 0 : k > 1 ? 1 : k;
        return k * k * (3 - 2 * k);
    }

    /** Deckung einer Form mit weicher Kante: abstand = Abstand zur Mitte, halb = halbe Breite. */
    static float form(float abstand, float halb) {
        float k = 0.5f + (halb - abstand) / (1.4f * PXR);
        return k < 0 ? 0 : k > 1 ? 1 : k;
    }

    static float frac(float v) { return v - fl(v); }

    static float klemme(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    /** Abstand eines Punkts zur Strecke a-b. */
    static float strecke(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay, wx = px - ax, wy = py - ay;
        float k = klemme((wx * vx + wy * vy) / Math.max(1e-9f, vx * vx + vy * vy));
        float dx = wx - vx * k, dy = wy - vy * k;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static final ThreadLocal<float[]> ZELLE = ThreadLocal.withInitial(() -> new float[5]);

    /** Voronoi: [0] = naechster Abstand, [1] = zweitnaechster, [2] = Zufallswert der Zelle, [3]/[4] = Richtung zum Kern. */
    static float[] zellen(float x, float y, float t, int saat) {
        float[] o = ZELLE.get();
        int ix = fl(x), iy = fl(y);
        float f1 = 9, f2 = 9, id = 0, rx = 0, ry = 0;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                int cx = ix + i, cy = iy + j;
                float h1 = hash(cx * 3 + saat, cy * 7 - saat), h2 = hash(cx * 11 - saat, cy * 5 + saat * 3);
                float kx = cx + 0.5f + 0.42f * sinf(t * (0.3f + h1) + h1 * 20f);
                float ky = cy + 0.5f + 0.42f * cosf(t * (0.25f + h2) + h2 * 20f);
                float dx = kx - x, dy = ky - y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < f1) { f2 = f1; f1 = d; id = h1; rx = dx; ry = dy; }
                else if (d < f2) f2 = d;
            }
        }
        o[0] = f1; o[1] = f2; o[2] = id; o[3] = rx; o[4] = ry;
        return o;
    }

    static int packe(float r, float g, float b) {
        return (k255(r) << 16) | (k255(g) << 8) | k255(b);
    }

    /**
     * Feines Rastermuster (4 x 4, Bayer) von +-einer halben Helligkeitsstufe (seit 4.81):
     * weiche Verlaeufe zeigen so keine Stufen ("Banding") mehr, vor allem in dunklen Capes.
     * Fest je Pixel, flackert also nicht; die weiche Texturfilterung mittelt es unsichtbar weg.
     */
    private static final float[] BAYER = new float[16];
    static {
        int[] m = { 0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5 };
        for (int i = 0; i < 16; i++) BAYER[i] = (m[i] + 0.5f - 8f) / 16f / 255f;
    }

    static int k255(float v) { return v <= 0 ? 0 : v >= 1 ? 255 : (int) (v * 255f + 0.5f); }

    static int abgr(float r, float g, float b, float a) {
        return (k255(a) << 24) | (k255(b) << 16) | (k255(g) << 8) | k255(r);
    }

    static int mischRgb(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * k));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * k));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * k));
        return (r << 16) | (g << 8) | bl;
    }

    /** Farbverlauf ueber mehrere Stuetzfarben (k 0..1). */
    static int verlauf(float k, int... farben) {
        if (k <= 0) return farben[0];
        if (k >= 1) return farben[farben.length - 1];
        float f = k * (farben.length - 1);
        int i = (int) f;
        return mischRgb(farben[i], farben[i + 1], f - i);
    }

    static int hsv(float h, float s, float v) {
        h = frac(h) * 6f;
        int i = (int) h;
        float f = h - i, p = v * (1 - s), q = v * (1 - s * f), u = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = u; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = u; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = u; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return packe(r, g, b);
    }

    /**
     * Sterne in einem Raster (je Zelle hoechstens einer), weich und mindestens
     * ~1,3 Pixel gross. groesse = Radius in Pixeln. Liefert Helligkeit 0..1.
     */
    static float sterne(float x, float y, float dichte, float t, int saat, float anteil, float groesse) {
        float gx = x * dichte, gy = y * dichte;
        int cx = fl(gx), cy = fl(gy);
        float h = hash(cx + saat * 31, cy - saat * 17);
        if (h > anteil) return 0;
        float sx = (cx + 0.2f + 0.6f * hash(cx * 3 + 1, cy + saat)) / dichte, sy = (cy + 0.2f + 0.6f * hash(cx + saat, cy * 5 + 7)) / dichte;
        float dx = (x - sx) / PX, dy = (y - sy) / PX;
        float r = groesse * (0.7f + 0.6f * hash(cx + 9, cy + saat * 3));
        float d2 = (dx * dx + dy * dy) / (r * r);
        if (d2 > 9) return 0;
        float fun = 0.6f + 0.4f * sinf(t * (1.1f + h * 3f) + h * 40f);
        return expf(-d2) * fun;
    }

    /** Teilchen, die in Spalten auf- oder absteigen. groesse = Radius in Pixeln. Liefert 0..1 (weicher Punkt). */
    static float teilchen(float x, float y, float t, int spalten, float tempo, float groesse, int saat, boolean runter) {
        float gx = x / BREITE * spalten;
        int cx = fl(gx);
        float best = 0;
        for (int nb = -1; nb <= 1; nb++) {
            int c = cx + nb;
            for (int k = 0; k < 2; k++) {
                float h = hash(c * 7 + k * 101, saat);
                float v = tempo * (0.6f + 0.8f * hash(c + 3, k + saat * 5));
                float py = frac(h + t * v);
                if (!runter) py = 1 - py;
                float r = groesse * (0.7f + 0.6f * hash(c, k + saat));
                float dy = (y - py) / PX;
                if (dy * dy > 9f * r * r) continue;
                float pxw = (c + 0.5f + 0.35f * sinf(t * (0.9f + h * 2f) + h * 30f + py * 5f)) / spalten * BREITE;
                float dx = (x - pxw) / PX;
                float d2 = (dx * dx + dy * dy) / (r * r);
                if (d2 > 9) continue;
                float fade = glatt(0f, 0.12f, runter ? py : 1 - py) * glatt(1f, 0.82f, runter ? py : 1 - py);
                best = Math.max(best, expf(-d2) * fade);
            }
        }
        return best;
    }

    /** Vortex-"V" (zwei Balken mit runden Enden): Deckung 0..1. */
    static float vZeichen(float x, float y, float cx, float cy, float g) {
        float d = Math.min(strecke(x, y, cx - g * 0.62f, cy - g * 0.55f, cx, cy + g * 0.6f),
                strecke(x, y, cx + g * 0.62f, cy - g * 0.55f, cx, cy + g * 0.6f));
        return form(d, g * 0.17f);
    }

    static {
        neu("anim_vortex", "Vortex", "A spinning galaxy of violet light", 0x8B5CF6, 0x67E8F9, 0.5f, 0.35f, (x, y, t, p) -> {
            float cy = 0.45f, dx = x - MITTE, dy = y - cy;
            float r = (float) Math.sqrt(dx * dx + dy * dy) * 0.72f, a = (float) Math.atan2(dy, dx);
            p.farbe(verlauf(glatt(0f, 0.75f, r), 0x1C0F45, 0x0E0828, 0x05040F));
            float staub = fbm(x * 5f + t * 0.02f, y * 5f - t * 0.015f, 4);
            float spir = 2f * a - (float) Math.log(r + 0.025f) * 2.7f + t * 0.5f + staub * 1.8f;
            float arm = 0.5f + 0.5f * cosf(spir);
            arm *= arm; arm *= arm;
            float huelle = expf(-r * r / 0.15f);
            float fein = fbm(x * 11f + cosf(spir) * 0.4f, y * 11f, 3);
            float inten = arm * huelle * (0.4f + 0.6f * glatt(0.3f, 0.75f, staub)) * (0.75f + 0.5f * fein);
            int c = verlauf(glatt(0.02f, 0.34f, r), 0xF5D0FE, 0xC084FC, 0x8B5CF6, 0x6366F1, 0x22D3EE);
            p.strahle(c, inten * 0.95f, 0.75f);
            p.mische(0x3B1D7A, huelle * glatt(0.45f, 0.8f, staub) * 0.35f);
            float kern = expf(-r * r / 0.0025f);
            p.strahle(0xFFF7FF, kern * 0.95f, 1f);
            p.leuchte(0x8B5CF6, expf(-r * r / 0.03f) * 0.4f);
            for (int k = 0; k < 2; k++) {
                float s = sterne(x, y, 16f + k * 10f, t, k + 3, 0.22f, 1.1f);
                p.strahle(k == 0 ? 0xE0E7FF : 0xA5F3FC, s * 0.85f, 1f);
            }
        });

        neu("anim_end", "Nebula", "Cosmic clouds and drifting stars", 0xA855F7, 0x67E8F9, 0.45f, 0.4f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x05030D, 0x0B0720, 0x070412));
            float q1 = fbm(x * 2.2f + t * 0.02f, y * 2.2f - t * 0.03f, 4);
            float q2 = fbm(x * 2.2f + 5.2f, y * 2.2f + 1.3f + t * 0.02f, 4);
            float n = fbm(x * 2.6f + 2.4f * q1, y * 2.6f + 2.4f * q2 - t * 0.025f, 5);
            float dichte = glatt(0.36f, 0.82f, n);
            int c = verlauf(glatt(0.35f, 0.9f, n), 0x2E1065, 0x7C3AED, 0xDB2777, 0xFBCFE8);
            p.mische(c, dichte * 0.92f);
            p.mische(0x0891B2, glatt(0.55f, 0.85f, q2) * dichte * 0.45f);
            float bahn = glatt(0.56f, 0.72f, fbm(x * 5f + q1 * 2f, y * 5f + q2, 3));
            p.hell(1f - 0.5f * bahn * dichte);
            p.leuchte(c, dichte * dichte * 0.5f);
            p.leuchte(0x22D3EE, glatt(0.62f, 0.9f, q2) * dichte * 0.3f);
            for (int k = 0; k < 3; k++) {
                float s = sterne(x + t * 0.008f * (k + 1), y + t * 0.012f * (k + 1), 12f + k * 8f, t, k + 1, 0.3f, 1.0f + 0.3f * (2 - k));
                int sc = k == 0 ? 0xFFFFFF : k == 1 ? 0xC4B5FD : 0x99F6E4;
                p.strahle(sc, s * 0.85f, 1f);
            }
        });

        neu("anim_nether", "Inferno", "Rising flames and flying embers", 0xEA580C, 0xFDE047, 0.3f, 0f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x070202, 0x140404, 0x2A0806));
            float warp = fbm(x * 3f, y * 2f + t * 0.55f, 3);
            float f = fbm(x * 4.2f + warp * 1.4f, y * 3.2f + t * 1.05f, 5);
            float zunge = fbm(x * 9f + warp, y * 2.2f + t * 1.4f, 3);
            float flamme = f * 1.15f - (1f - y) * 1.0f + 0.12f + (zunge - 0.5f) * 0.35f;
            float k = glatt(0.0f, 0.75f, flamme);
            int c = verlauf(k, 0x2A0806, 0x7F1D1D, 0xB91C1C, 0xEA580C, 0xF59E0B, 0xFDE68A);
            p.strahle(c, glatt(0.0f, 0.22f, k), k * k * 0.9f);
            p.mische(0x3B0A0A, glatt(0.35f, 0f, y) * 0.3f * fbm(x * 6f, y * 6f - t * 0.4f, 2));
            float glut = teilchen(x, y, t, 9, 0.11f, 1.6f, 4, false);
            p.strahle(0xFDBA74, glut, 1f);
            float glut2 = teilchen(x, y, t * 1.3f, 6, 0.08f, 2.2f, 9, false) * 0.7f;
            p.strahle(0xFB923C, glut2, 1f);
        });

        neu("anim_aurora", "Aurora", "Northern lights dancing in the sky", 0x34D399, 0xA78BFA, 0.3f, 0.5f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x020617, 0x061430, 0x0B2545));
            float s = sterne(x, y, 22f, t, 7, 0.45f, 1.2f) * glatt(0.7f, 0.2f, y);
            p.strahle(0xE0F2FE, s * 0.7f, 0.8f);
            for (int i = 0; i < 3; i++) {
                float yc = 0.3f + i * 0.14f + 0.06f * sinf(x * 6.5f + t * 0.33f + i * 2.1f)
                        + 0.025f * sinf(x * 15f - t * 0.47f + i * 1.3f);
                float dy = y - yc;
                float vorhang = dy > 0 ? expf(-(dy / 0.014f) * (dy / 0.014f)) : expf(dy / (0.1f + 0.03f * i));
                float strahlen = 0.45f + 0.55f * fbm(x * 26f + i * 10f + t * 0.18f, t * 0.12f + i * 3f + y * 1.5f, 2);
                float welle = 0.55f + 0.45f * sinf(x * 4f + t * 0.3f + i * 1.7f);
                float inten = vorhang * strahlen * welle * (i == 1 ? 1f : 0.75f);
                int c = verlauf(glatt(0f, 0.16f, -dy), 0x6EE7B7, 0x34D399, 0x14B8A6, 0xA78BFA);
                p.strahle(c, inten * 0.85f, 0.8f);
            }
            p.mische(0x0F766E, glatt(0.82f, 1f, y) * 0.22f);
        });

        neu("anim_ocean", "Abyss", "Light dancing in the deep sea", 0x0EA5E9, 0x67E8F9, 0.35f, 0f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x0E7490, 0x0B4A6E, 0x062645, 0x020A18));
            float wu = fbm(x * 3f, y * 3f + t * 0.25f, 2) * 1.6f, wv = fbm(x * 3f + 5f, y * 3f - t * 0.2f, 2) * 1.6f;
            float[] z = zellen(x * 7f + wu, y * 7f + wv, t * 0.9f, 3);
            float kaustik = expf(-((z[1] - z[0]) / 0.09f) * ((z[1] - z[0]) / 0.09f));
            float tiefe = (1 - y) * (1 - y) * (1 - y);
            p.strahle(0xA5F3FC, kaustik * tiefe * 0.6f, 0.5f);
            float strahl = 0.5f + 0.5f * sinf(Math.atan2(x - MITTE, y + 0.35f) * 14f + Math.sin(t * 0.3f) * 1.5f + t * 0.15f);
            strahl = strahl * strahl * strahl;
            p.licht(0x7DD3FC, strahl * (1 - y) * 0.22f);
            float b = teilchen(x, y, t, 8, 0.05f, 1.5f, 11, false);
            p.strahle(0x67E8F9, b * 0.9f, 1f);
            float b2 = teilchen(x, y, t, 5, 0.09f, 2.4f, 21, false) * 0.45f;
            p.strahle(0xE0F2FE, b2, 0.6f);
        });

        neu("anim_frost", "Frostbite", "Crystal ice and falling snow", 0xBAE6FD, 0xFFFFFF, 0.55f, 1.1f, (x, y, t, p) -> {
            float[] z = zellen(x * 5.5f + fbm(x * 4f, y * 4f, 2) * 0.6f, y * 5.5f, t * 0.04f, 5);
            float facette = 0.8f + 0.3f * z[2] + (-z[3] * 0.6f - z[4] * 0.8f) * 0.25f;
            int c = verlauf(y * 0.8f + z[2] * 0.2f, 0xE0F2FE, 0x7DD3FC, 0x2563EB, 0x0B1E3F);
            p.farbe(c);
            p.hell(facette);
            float riss = form((z[1] - z[0]) / 5.5f, 0.6f * PX);
            p.strahle(0xF0F9FF, riss * 0.55f, 0.5f);
            float funk = (float) Math.pow(klemme(0.5f + 0.5f * sinf(z[2] * 40f + t * 1.5f)), 12);
            p.licht(0xFFFFFF, funk * 0.35f);
            float reif = glatt(0.55f, 0.85f, fbm(x * 9f, y * 9f, 4)) * glatt(0.2f, 0f, Math.min(Math.min(x, BREITE - x), y));
            p.mische(0xFFFFFF, reif * 0.6f);
            float schnee = teilchen(x, y, t, 10, 0.07f, 1.7f, 8, true);
            p.strahle(0xFFFFFF, schnee * 0.9f, 0.6f);
        });

        neu("anim_storm", "Thunder", "Lightning tearing through storm clouds", 0x60A5FA, 0xE0F2FE, 0.25f, 0f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x0B1020, 0x111827, 0x1E1B4B));
            float wolke = fbm(x * 3f + t * 0.05f, y * 4f, 5);
            float wolke2 = fbm(x * 3f + t * 0.05f + 0.04f, y * 4f - 0.06f, 5);
            p.mische(0x334155, glatt(0.38f, 0.72f, wolke) * 0.85f);
            p.mische(0x64748B, klemme((wolke - wolke2) * 9f) * glatt(0.4f, 0.7f, wolke) * 0.6f);
            p.mische(0x070B16, glatt(0.45f, 0.25f, wolke) * 0.45f);
            float zyklus = 2.8f;
            int k = fl(t / zyklus);
            float ph = t - k * zyklus;
            float x0 = MITTE + (hash(k, 77) - 0.5f) * 0.3f;
            float laenge = glatt(0f, 0.12f, ph);
            float abkl = ph < 0.12f ? 1f : expf(-(ph - 0.12f) * 2.2f) * (0.8f + 0.2f * sinf(ph * 50f))
                    + (ph > 0.3f && ph < 0.42f ? 0.6f : 0f);
            if (abkl > 0.01f && y < laenge * 1.05f) {
                float xb = x0 + 0.09f * (fbm(y * 3f + k * 13f, k, 3) - 0.5f) * 2f + 0.025f * (rausch(y * 28f, k * 3f) - 0.5f);
                float d = Math.abs(x - xb);
                float ast = 9;
                if (y > 0.33f && y < 0.66f) {
                    float y0 = 0.33f, xs = x0 + 0.09f * (fbm(y0 * 3f + k * 13f, k, 3) - 0.5f) * 2f;
                    float richt = hash(k, 5) < 0.5f ? -1 : 1;
                    float xa = xs + (y - y0) * richt * 0.55f + 0.015f * (rausch(y * 30f, k * 7f) - 0.5f);
                    ast = Math.abs(x - xa) * (1.4f - (y - y0) * 1.2f);
                }
                float kern = Math.max(form(d, 0.8f * PX), form(ast, 0.5f * PX) * 0.8f);
                float schein = expf(-(d / 0.02f) * (d / 0.02f)) * 0.6f + expf(-(ast / 0.015f) * (ast / 0.015f)) * 0.35f;
                p.strahle(0xFFFFFF, kern * abkl, 1f);
                p.leuchte(0x93C5FD, schein * abkl);
            }
            p.licht(0xC7D2FE, abkl * 0.18f * glatt(0.35f, 0.75f, wolke));
            float u = (x + y * 0.22f) * 42f;
            int spalte = fl(u);
            if (hash(spalte, 3) > 0.55f) {
                float v = frac(y * 5f + t * 7f + hash(spalte, 9) * 10f);
                float tropfen = glatt(0.75f, 1f, v) * form(Math.abs(frac(u) - 0.5f) / 42f, 0.5f * PX);
                p.licht(0x94A3B8, tropfen * 0.22f);
            }
        });

        neu("anim_neon", "Synthwave", "Retro sun over an endless neon grid", 0xFF2BD6, 0x22D3EE, 0.3f, 0f, (x, y, t, p) -> {
            float hor = 0.56f;
            if (y < hor) {
                p.farbe(verlauf(y / hor, 0x0F0326, 0x3B0764, 0xBE185D, 0xFB923C));
                float st = sterne(x, y, 20f, t, 4, 0.4f, 1.2f) * glatt(0.35f, 0.05f, y);
                p.strahle(0xFFFFFF, st * 0.8f, 0.8f);
                float sx = MITTE, sy = hor - 0.12f, rs = 0.165f;
                float dx = x - sx, dy = y - sy, d = (float) Math.sqrt(dx * dx + dy * dy);
                float scheibe = form(d, rs);
                if (scheibe > 0) {
                    float k = (y - (sy - rs)) / (2 * rs);
                    int sc = verlauf(k, 0xFEF08A, 0xFDBA74, 0xFB7185, 0xEC4899);
                    float unten = (y - sy) / rs;
                    float luecke = 1f;
                    if (unten > -0.1f) {
                        float band = frac(unten * 4.2f - t * 0.25f);
                        float breite = 0.12f + 0.3f * klemme(unten);
                        float dd = Math.abs(band - 0.5f) * rs / 4.2f;
                        luecke = 1f - form(dd, breite * rs / 4.2f * 0.5f);
                    }
                    p.strahle(sc, scheibe * luecke, 0.85f);
                }
                p.leuchte(0xFB7185, expf(-Math.max(0, d - rs) / 0.05f) * 0.4f * (1 - scheibe));
                float berg = hor - 0.035f - 0.045f * fbm(x * 6f + 3f, 1.7f, 3) - 0.02f * Math.abs(sinf(x * 9f));
                float fels = glatt(berg - 0.6f * PX, berg + 0.6f * PX, y);
                if (fels > 0) {
                    p.mische(verlauf(klemme((y - berg) / 0.06f), 0x2A0B4A, 0x12062A), fels);
                    float kante = form(Math.abs(y - berg), 0.6f * PX);
                    p.strahle(0xF472B6, kante * 0.8f, 0.9f);
                }
            } else {
                p.farbe(verlauf((y - hor) / (1 - hor), 0x1A0634, 0x0D0221));
                float tiefe = y - hor + 0.004f;
                float z = 0.09f / tiefe;
                float dzdy = 0.09f / (tiefe * tiefe);
                float zl = z + t * 0.9f;
                float lz = Math.abs(frac(zl) - 0.5f);
                float quer = form((0.5f - lz) / dzdy, 0.6f * PX + 0.002f / (1 + z * 0.4f));
                float xg = (x - MITTE) * z * 6f;
                float lx = Math.abs(frac(xg) - 0.5f);
                float laengs = form((0.5f - lx) / (z * 6f), 0.6f * PX);
                float nebel = glatt(0f, 0.12f, tiefe);
                float gitter = Math.max(quer, laengs) * nebel;
                p.strahle(verlauf(klemme(tiefe * 2.2f), 0xFF2BD6, 0xA855F7, 0x22D3EE), gitter * 0.9f, 1f);
                p.licht(0xFF2BD6, glatt(0.05f, 0f, tiefe) * 0.5f);
            }
        });

        neu("anim_prism", "Hologram", "Iridescent holographic foil", 0xE5E7EB, 0xFFFFFF, 0.8f, 1.2f, (x, y, t, p) -> {
            float u = (x + y) * 8f, v = (x - y) * 8f;
            int cu = fl(u), cv = fl(v);
            float facette = hash(cu, cv);
            float hue = frac(0.62f + x * 0.9f + y * 0.55f + 0.12f * sinf(x * 7f + y * 5f + t * 0.6f) + t * 0.05f + facette * 0.06f);
            p.farbe(mischRgb(0xD1D5DB, hsv(hue, 0.48f, 0.98f), 0.8f));
            p.hell(0.9f + 0.14f * facette);
            float du = Math.abs(frac(u) - 0.5f), dv = Math.abs(frac(v) - 0.5f);
            float naht = Math.max(form((0.5f - du) / 8f / 1.414f, 0.5f * PX), form((0.5f - dv) / 8f / 1.414f, 0.5f * PX));
            p.licht(0xFFFFFF, naht * 0.35f);
            float schein = (float) Math.pow(0.5f + 0.5f * Math.sin((x * 1.2f - y) * 6f + t * 1.1f), 6);
            p.leuchte(hsv(hue + 0.2f, 0.3f, 1f), schein * 0.35f);
        });

        neu("anim_sakura", "Sakura", "Blossoms drifting through a pink dusk", 0xF9A8D4, 0xFFFFFF, 0.4f, 0.6f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0xFCE7F3, 0xF9A8D4, 0xC084FC, 0x4C1D95));
            for (int k = 0; k < 7; k++) {
                float bx = hash(k, 1) * BREITE + 0.02f * sinf(t * 0.2f + k);
                float by = frac(hash(k, 2) - t * 0.012f * (0.5f + hash(k, 3)));
                float br = 0.03f + 0.045f * hash(k, 4);
                float d = (float) Math.sqrt((x - bx) * (x - bx) + (y - by) * (y - by));
                float kreis = glatt(br, br - 0.008f, d) * (0.6f + 0.4f * glatt(br - 0.012f, br, d));
                p.licht(0xFFF1F7, kreis * 0.22f);
            }
            float zy = 0.09f + 0.06f * (x / BREITE) * (x / BREITE) - 0.02f * sinf(x * 9f);
            float dicke = 0.004f + 0.012f * (x / BREITE);
            float zweig = form(Math.abs(y - zy), dicke);
            float twig = form(strecke(x, y, 0.38f, zy(0.38f), 0.3f, 0.2f), 0.0035f);
            float twig2 = form(strecke(x, y, 0.2f, zy(0.2f), 0.12f, 0.03f), 0.003f);
            float holz = Math.max(zweig, Math.max(twig, twig2));
            p.mische(0x3B1F2B, holz);
            for (int k = 0; k < 7; k++) {
                float bx = 0.06f + k * 0.085f, by = zy(bx) + (k % 2 == 0 ? -0.012f : 0.014f);
                if (k == 6) { bx = 0.31f; by = 0.205f; }
                float wipp = 0.004f * sinf(t * 1.2f + k);
                float dx = x - bx - wipp, dy = y - by;
                float d = (float) Math.sqrt(dx * dx + dy * dy), a = (float) Math.atan2(dy, dx) + k + t * 0.05f;
                float rb = 0.024f * (0.78f + 0.22f * (float) Math.abs(Math.cos(2.5f * a)));
                float bl = form(d, rb);
                if (bl > 0) {
                    p.mische(verlauf(d / 0.024f, 0xBE185D, 0xF9A8D4, 0xFFF1F7), bl);
                    p.leuchte(0xFBCFE8, bl * 0.2f);
                }
            }
            float gx = x / BREITE * 6f;
            int cx = fl(gx);
            for (int nb = -1; nb <= 1; nb++) {
                int c = cx + nb;
                for (int k = 0; k < 2; k++) {
                    float h = hash(c * 5 + k * 37, 12);
                    float py = frac(h + t * 0.06f * (0.6f + h));
                    float pxw = (c + 0.5f + 0.4f * sinf(t * 0.8f + h * 30f + py * 6f)) / 6f * BREITE;
                    float w = t * (1.2f + h) + h * 10f;
                    float cw = cosf(w), sw = sinf(w);
                    float lx = (x - pxw) * cw + (y - py) * sw, ly = -(x - pxw) * sw + (y - py) * cw;
                    float e = (float) Math.sqrt((lx / 0.011f) * (lx / 0.011f) + (ly / 0.0065f) * (ly / 0.0065f));
                    float bl = form(e * 0.0065f, 0.0065f);
                    if (bl > 0) p.strahle(mischRgb(0xF472B6, 0xFFF1F7, 0.5f + 0.5f * sinf(w)), bl * 0.95f, 0.25f);
                }
            }
        });

        neu("anim_royal", "Midas", "Black marble veined with liquid gold", 0xD4AF37, 0xFFF1B8, 0.55f, 0.9f, (x, y, t, p) -> {
            float m = fbm(x * 3f, y * 3f, 4);
            p.farbe(verlauf(m, 0x050505, 0x0F0F10, 0x1C1C1F));
            float q = fbm(x * 2.5f, y * 2.5f + t * 0.01f, 4);
            float n = fbm(x * 3.2f + q * 2.2f, y * 3.2f + q * 1.8f + t * 0.008f, 5);
            float ader = Math.abs(n - 0.5f);
            float kern = glatt(0.016f, 0.004f, ader);
            float halo = glatt(0.05f, 0f, ader);
            float n2 = fbm(x * 6f + q * 3f + 4f, y * 6f + 2f, 4);
            float fein = glatt(0.008f, 0.0025f, Math.abs(n2 - 0.5f)) * 0.75f;
            float gold = Math.max(kern, fein);
            float metall = 0.5f + 0.5f * sinf((x * 1.3f + y * 0.7f) * 14f - t * 1.1f + q * 6f);
            int gc = verlauf(metall, 0x7A5A12, 0xD4AF37, 0xFFF1B8);
            p.mische(0x2A2110, halo * 0.5f);
            p.strahle(gc, gold, 0.25f + 0.25f * metall);
            p.leuchte(0xFFD86B, halo * 0.08f);
        });

        neu("anim_phantom", "Phantom", "Shadow smoke with a violet glow", 0x7C3AED, 0xE879F9, 0.3f, 0.4f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x020104, 0x0A0614, 0x150A28));
            float q = fbm(x * 3f, y * 2.5f + t * 0.22f, 3);
            float s = fbm(x * 4f + q * 2f, y * 3f + t * 0.32f, 5);
            float stark = 0.45f + 0.55f * y;
            float schwade = glatt(0.45f, 0.75f, s) * stark;
            float kante = (glatt(0.5f, 0.58f, s) - glatt(0.6f, 0.74f, s)) * stark;
            p.mische(0x2E1065, schwade * 0.8f);
            p.strahle(0xA855F7, kante * 0.65f, 0.8f);
            p.leuchte(0x7C3AED, schwade * 0.18f);
            float seele = teilchen(x, y, t, 7, 0.06f, 1.6f, 14, false);
            p.strahle(0xF0ABFC, seele * 0.9f, 1f);
        });

        neu("anim_toxic", "Toxic", "Glowing acid flowing down", 0x22C55E, 0xBEF264, 0.3f, 0f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x020A04, 0x052E16, 0x041B0B));
            float w = fbm(x * 2f, y * 2f - t * 0.2f, 2) * 1.5f;
            float fluss = fbm(x * 5f + w, y * 1.2f - t * 0.5f, 4);
            float baeche = 0.5f + 0.5f * sinf(x * 30f + fluss * 7f);
            float s = baeche * 0.55f + fbm(x * 8f - w, y * 2.5f - t * 0.6f, 3) * 0.45f;
            float saeure = glatt(0.55f, 0.72f, s) * (0.55f + 0.45f * y);
            int c = verlauf(saeure, 0x14532D, 0x22C55E, 0xA3E635, 0xECFCCB);
            p.strahle(c, saeure * 0.9f, 0.65f);
            p.leuchte(0x4ADE80, glatt(0.45f, 0.6f, s) * 0.12f);
            float gx = x / BREITE * 7f;
            int cx = fl(gx);
            for (int nb = -1; nb <= 1; nb++) {
                int c2 = cx + nb;
                float h = hash(c2, 41);
                float py = 1 - frac(h + t * 0.07f * (0.6f + h));
                float pxw = (c2 + 0.5f + 0.3f * sinf(t + h * 20f)) / 7f * BREITE;
                float rb = 0.007f + 0.006f * hash(c2, 43);
                float d = (float) Math.sqrt((x - pxw) * (x - pxw) + (y - py) * (y - py));
                float ring = form(Math.abs(d - rb), 0.55f * PX) + 0.25f * form(d, rb);
                p.strahle(0xD9F99D, ring * 0.8f * glatt(0f, 0.15f, 1 - py), 0.8f);
            }
        });

        neu("anim_carbon", "Carbon", "Carbon fibre with racing stripes", 0x52525B, 0xEF4444, 0.65f, 0f, (x, y, t, p) -> {
            float zelle = BREITE / 14f;
            float u = x / zelle, v = y / zelle;
            int iu = fl(u), iv = fl(v);
            boolean waag = Math.floorMod(iu + iv, 4) < 2;
            float quer = waag ? frac(v) : frac(u);
            float faser = (float) Math.pow(Math.sin(quer * Math.PI), 0.7);
            float licht = waag ? 0.5f + 0.5f * sinf(y * 7f + t * 0.7f) : 0.5f + 0.5f * sinf(x * 9f - t * 0.7f + 1f);
            float entlang = waag ? frac(u) : frac(v);
            float h = 0.05f + 0.075f * faser * (0.55f + 0.9f * licht) * (0.8f + 0.2f * sinf(entlang * Math.PI));
            p.farbe(h, h, h * 1.08f);
            p.licht(0xFFFFFF, glatt(0.9f, 0f, x + y * 0.5f) * 0.08f);
            for (int s = -1; s <= 1; s += 2) {
                float sx = MITTE + s * 0.052f;
                float d = Math.abs(x - sx);
                float streif = form(d, 0.017f);
                float puls = expf(-Math.pow((frac(y * 0.8f - t * 0.45f) - 0.5f) / 0.08f, 2));
                if (streif > 0) {
                    float metall = 0.75f + 0.25f * sinf(y * 10f + t * 0.5f);
                    p.mische(mischRgb(0x991B1B, 0xEF4444, metall), streif);
                    p.leuchte(0xF87171, streif * (0.15f + 0.55f * puls));
                }
                float nadel = form(Math.abs(d - 0.026f), 0.5f * PX);
                p.strahle(0xE4E4E7, nadel * 0.55f, 0.2f);
            }
            float vz = vZeichen(x, y, MITTE, 0.16f, 0.045f);
            if (vz > 0) {
                p.mische(verlauf(klemme((y - 0.13f) / 0.07f), 0xFAFAFA, 0xA1A1AA), vz);
                p.leuchte(0xFFFFFF, vz * 0.15f);
            }
        });

        neu("anim_eclipse", "Eclipse", "A total eclipse with a blazing corona", 0xF59E0B, 0xFEF3C7, 0.35f, 0.4f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x000000, 0x05050C, 0x0E0B1C));
            float st = sterne(x, y, 20f, t, 6, 0.45f, 1.2f);
            p.strahle(0xFFFFFF, st * 0.75f, 0.9f);
            float cx = MITTE, cy = 0.33f, r0 = 0.105f;
            float dx = x - cx, dy = y - cy, d = (float) Math.sqrt(dx * dx + dy * dy);
            float ca = dx / Math.max(1e-5f, d), sa = dy / Math.max(1e-5f, d);
            float faeden = fbm(ca * 2.2f + 5f + t * 0.03f, sa * 2.2f + 5f, 4);
            float aussen = Math.max(0, d - r0);
            float korona = expf(-aussen / (0.03f + 0.05f * faeden)) * (0.55f + 0.7f * faeden);
            int kc = verlauf(klemme(aussen / 0.14f), 0xFFFFFF, 0xFDE68A, 0xF59E0B, 0x7C2D12);
            p.strahle(kc, korona * (1 - form(d, r0)), 0.9f);
            float ring = form(Math.abs(d - r0), 0.9f * PX);
            p.strahle(0xFFFBEB, ring, 1f);
            float scheibe = form(d, r0 - 0.6f * PX);
            p.mische(0x050508, scheibe);
            float w = -0.9f + 0.25f * sinf(t * 0.25f);
            float px = cx + cosf(w) * r0, py = cy + sinf(w) * r0;
            float ddx = (x - px) / PX, ddy = (y - py) / PX;
            float diamant = expf(-(ddx * ddx + ddy * ddy) / 12f)
                    + 0.5f * (float) (Math.exp(-Math.abs(ddx) / 14f) * Math.exp(-ddy * ddy / 1.5f) + Math.exp(-Math.abs(ddy) / 14f) * Math.exp(-ddx * ddx / 1.5f));
            p.strahle(0xFFFFFF, Math.min(1, diamant) * (0.75f + 0.25f * sinf(t * 2f)), 1f);
            p.mische(0x2A1640, glatt(0.7f, 1f, y) * 0.45f);
        });

        neu("anim_circuit", "Circuit", "Pulses racing through neon circuits", 0x0EA5E9, 0x22D3EE, 0.35f, 0f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x020617, 0x061127, 0x0A1A33));
            float spur = BREITE / 9f;
            int j0 = Math.round(x / spur - 0.5f);
            float best = 9, impuls = 0, pads = 0;
            for (int j = j0 - 1; j <= j0 + 2; j++) {
                if (hash(j, 1) < 0.18f) continue;
                float zeilen = 7f;
                float zy = y * zeilen;
                int zi = fl(zy);
                float off = 0;
                for (int z = 0; z < zi; z++) off += sprung(j, z);
                float sp = sprung(j, zi);
                float f = frac(zy);
                float knick = sp == 0 ? 0 : klemme((f - 0.55f) / 0.45f);
                float xb = (j + 0.5f + off + sp * knick) * spur;
                float d = Math.abs(x - xb) * (knick > 0 && knick < 1 ? 0.707f : 1f);
                if (d < best) best = d;
                float pos = frac(t * (0.18f + 0.1f * hash(j, 2)) + hash(j, 3));
                float dz = Math.abs(y - pos);
                if (d < 0.01f) impuls = Math.max(impuls, expf(-(dz / 0.035f) * (dz / 0.035f)));
                float pa = (float) Math.sqrt((x - (j + 0.5f) * spur) * (x - (j + 0.5f) * spur) + (y - 0.06f) * (y - 0.06f));
                pads = Math.max(pads, form(Math.abs(pa - 0.008f), 0.6f * PX));
            }
            float bahn = form(best, 0.8f * PX);
            p.strahle(0x0E7490, bahn * 0.85f, 0.25f);
            p.strahle(0x67E8F9, bahn * impuls, 1f);
            p.leuchte(0x22D3EE, expf(-(best / 0.01f) * (best / 0.01f)) * impuls * 0.6f);
            p.strahle(0x22D3EE, pads * 0.9f, 0.7f);
            float cxw = Math.abs(x - MITTE), cyw = Math.abs(y - 0.5f);
            float chip = form(Math.max(cxw - 0.055f, cyw - 0.055f), 0.0f);
            if (chip > 0) {
                p.mische(0x0B1324, chip);
                float rahmen = form(Math.abs(Math.max(cxw, cyw) - 0.05f), 0.6f * PX);
                p.strahle(0x22D3EE, rahmen * (0.6f + 0.4f * sinf(t * 2f)), 0.9f);
                float vz = vZeichen(x, y, MITTE, 0.5f, 0.028f);
                p.strahle(0x67E8F9, vz, 0.9f);
            }
        });

        neu("anim_neon_lightning", "Neon Lightning", "Crackling veins of electric light", 0x93C5FD, 0xFFFFFF, 0.35f, 0.3f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x05070F, 0x0A0F24, 0x060914));
            float best = 0, schein = 0;
            for (int ebene = 0; ebene < 2; ebene++) {
                float f = fbm(x * (5f + ebene * 3f) + t * 0.04f * (ebene == 0 ? 1 : -1), y * (4f + ebene * 2f) - t * 0.03f, 4);
                float grat = 1f - Math.abs(f - 0.5f) * 2f;
                float ader = glatt(0.965f - ebene * 0.01f, 0.995f, grat);
                float hof = glatt(0.86f, 0.995f, grat);
                float knistern = 0.55f + 0.45f * sinf(t * (7f + ebene * 3f) + x * 40f + y * 25f) * sinf(t * 2.3f + ebene);
                best = Math.max(best, ader * (ebene == 0 ? 1f : 0.65f) * (0.6f + 0.4f * knistern));
                schein = Math.max(schein, hof * (ebene == 0 ? 0.6f : 0.35f) * knistern);
            }
            p.mische(0x1E3A8A, schein * 0.5f);
            p.strahle(0xE0F2FE, best, 1f);
            p.leuchte(0x60A5FA, schein * 0.75f);
        });

        neu("anim_plasma", "Plasma", "Swirling magenta and cyan energy", 0xE879F9, 0x67E8F9, 0.4f, 0.25f, (x, y, t, p) -> {
            float u = x / BREITE;
            float w1 = sinf(u * 9f + t * 0.9f + sinf(y * 6f + t * 0.5f) * 1.6f);
            float w2 = sinf(y * 11f - t * 0.7f + cosf(u * 7f - t * 0.4f) * 1.8f);
            float w3 = sinf((u + y) * 8f + t * 1.1f);
            float v = (w1 + w2 + w3) / 3f;
            float k = 0.5f + 0.5f * v;
            p.farbe(verlauf(k, 0x12021F, 0x5B0E7A, 0xC026D3, 0x22D3EE));
            float kern = glatt(0.84f, 0.99f, k);
            float linie = form(Math.abs(v - 0.15f) * 0.06f, 0.6f * PX);
            p.strahle(0xFDF4FF, kern * 0.5f, 1f);
            p.strahle(0xF0ABFC, linie * 0.7f, 0.9f);
            p.leuchte(0xD946EF, glatt(0.55f, 0.95f, k) * 0.45f);
        });

        neu("anim_vortex_sigil", "Vortex Sigil", "A glowing emblem with pulsing rings", 0x8B5CF6, 0xC4B5FD, 0.45f, 0.35f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x0C0420, 0x1A0B3D, 0x0B0418));
            float cx = MITTE, cy = 0.46f;
            float dx = x - cx, dy = y - cy, r = (float) Math.sqrt(dx * dx + dy * dy);
            for (int k = 0; k < 3; k++) {
                float rr = frac(t * 0.18f + k / 3f) * 0.42f;
                float ring = form(Math.abs(r - rr), 0.5f * PX) * (1 - rr / 0.42f);
                p.strahle(0xA78BFA, ring * 0.8f, 0.9f);
            }
            float kreis = form(Math.abs(r - 0.15f), 0.9f * PX);
            p.strahle(0xC4B5FD, kreis, 1f);
            p.leuchte(0x8B5CF6, expf(-((r - 0.15f) / 0.025f) * ((r - 0.15f) / 0.025f)) * 0.5f);
            float g = 0.16f;
            float d = Math.min(strecke(x, y, cx - g * 0.62f, cy - g * 0.55f, cx, cy + g * 0.6f),
                    strecke(x, y, cx + g * 0.62f, cy - g * 0.55f, cx, cy + g * 0.6f));
            float puls = 0.75f + 0.25f * sinf(t * 2.4f);
            float rand = form(Math.abs(d - g * 0.17f), 0.8f * PX);
            float innen = form(d, g * 0.17f);
            p.mische(0x05010E, innen * 0.85f);
            p.strahle(0xEDE9FE, rand * puls, 1f);
            p.leuchte(0x7C3AED, expf(-(d / 0.05f) * (d / 0.05f)) * 0.5f * puls);
            for (int k = 0; k < 6; k++) {
                float w = t * 0.8f + k * 1.047f;
                float fx = cx + cosf(w) * 0.22f, fy = cy + sinf(w) * 0.22f * 1.25f;
                float df = (float) Math.sqrt((x - fx) * (x - fx) + (y - fy) * (y - fy));
                p.strahle(0xFFFFFF, form(df, 0.9f * PX), 1f);
                p.leuchte(0xC4B5FD, expf(-(df / 0.02f) * (df / 0.02f)) * 0.7f);
            }
        });

        neu("anim_rift", "Void Rift", "A tear in space pulling in the light", 0xA855F7, 0xF0ABFC, 0.3f, 0.2f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x020104, 0x07030F, 0x030106));
            float mitte = MITTE + 0.075f * (fbm(y * 3.5f + t * 0.03f, 7f, 4) - 0.5f) * 2f + 0.004f * sinf(y * 30f + t);
            float breit = 0.012f + 0.03f * sinf(Math.PI * y) * (0.85f + 0.15f * sinf(t * 1.7f + y * 9f));
            float d = Math.abs(x - mitte);
            float kern = form(d, breit * 0.35f);
            float rand = form(Math.abs(d - breit), 0.8f * PX);
            float hof = expf(-(d / (breit * 3f)) * (d / (breit * 3f)));
            p.mische(0x2E1065, hof * 0.6f);
            p.strahle(0xF5D0FE, kern, 1f);
            p.strahle(0xC084FC, rand * 0.9f, 1f);
            p.leuchte(0xA855F7, hof * 0.55f);
            float tl = teilchen(x, y, t, 14, 0.35f, 0.9f, 31, true) * glatt(0.2f, 0.02f, d);
            p.strahle(0xE9D5FF, tl * 0.8f, 0.8f);
        });

        neu("anim_lava", "Lava Lamp", "Warm blobs rising and melting together", 0xF97316, 0xFDBA74, 0.4f, 0f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x2A0A3D, 0x3B0A2E, 0x1A0612));
            float f = 0;
            for (int i = 0; i < 7; i++) {
                float h = hash(i * 17 + 3, 91);
                float tempo = 0.03f + 0.03f * hash(i, 7);
                float by = 1.2f - frac(h + t * tempo) * 1.45f;
                float bx = BREITE * (0.15f + 0.7f * hash(i * 5, 3)) + 0.035f * sinf(t * 0.4f + i * 2f);
                float r = 0.04f + 0.035f * hash(i * 3, 11);
                float ddx = x - bx, ddy = (y - by) * 0.8f;
                f += r * r / (ddx * ddx + ddy * ddy + 1e-5f);
            }
            f += glatt(0.86f, 1.0f, y) * 1.4f;
            float blob = glatt(0.9f, 1.1f, f);
            int c = verlauf(klemme((f - 1f) / 3f), 0xF43F5E, 0xF97316, 0xFDBA74, 0xFEF3C7);
            p.strahle(c, blob, 0.55f);
            p.licht(0xF97316, glatt(0.35f, 0.95f, f) * 0.3f * (1 - blob));
            p.mische(0x581C87, glatt(0.3f, 0f, y) * 0.25f);
        });

        neu("anim_coderain", "Code Rain", "Falling green glyphs from the machine", 0x22C55E, 0xBBF7D0, 0.25f, 0f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x010A04, 0x02120A, 0x010603));
            float cw = BREITE / 13f, ch = cw * 1.25f;
            int sp = fl(x / cw);
            float lx = x / cw - sp;
            float tempo = 0.16f + 0.2f * hash(sp, 5);
            float kopf = frac(hash(sp, 9) + t * tempo) * 1.5f - 0.25f;
            int zl = fl(y / ch);
            float ly = y / ch - zl;
            float hinter = kopf - (zl + 0.5f) * ch;
            if (hinter < -ch * 0.5f || hinter > 0.6f) return;
            float spur = hinter < 0 ? 1f : 1f - hinter / 0.6f;
            int wechsel = fl(t * (1.5f + 3f * hash(sp, zl)));
            float u = (lx - 0.18f) / 0.64f, v = (ly - 0.1f) / 0.8f;
            if (u < 0 || u >= 1 || v < 0 || v >= 1) return;
            int bx = (int) (u * 3), by = (int) (v * 5);
            if (hash(sp * 97 + bx * 13 + by * 7, zl * 53 + wechsel * 31) < 0.42f) return;
            float fu = frac(u * 3), fv = frac(v * 5);
            float a = glatt(0f, 0.16f, fu) * glatt(1f, 0.84f, fu) * glatt(0f, 0.14f, fv) * glatt(1f, 0.86f, fv);
            boolean spitze = hinter < ch;
            int c = spitze ? 0xECFDF5 : mischRgb(0x14532D, 0x4ADE80, spur);
            p.strahle(c, a * (spitze ? 1f : 0.35f + 0.65f * spur), spitze ? 1f : 0.7f);
        });

        neu("anim_sunset", "Golden Hour", "The sun sinking into a calm sea", 0xF59E0B, 0xFDE68A, 0.35f, 0f, (x, y, t, p) -> {
            float hz = 0.56f;
            float sx = MITTE, sy = 0.5f + 0.015f * sinf(t * 0.12f);
            if (y < hz) {
                p.farbe(verlauf(y / hz, 0x1E1B4B, 0x6D28D9, 0xDB2777, 0xF97316, 0xFCD34D));
                float wolke = fbm(x * 5f + t * 0.03f, y * 14f, 3);
                float band = glatt(0.12f, 0.22f, y) * glatt(0.46f, 0.3f, y);
                float w = glatt(0.5f, 0.75f, wolke) * band;
                int wc = mischRgb(0x7C2D12, 0xFDBA74, glatt(0.2f, 0.45f, y));
                p.mische(wc, w * 0.75f);
            } else {
                float tiefe = (y - hz) / (1 - hz);
                p.farbe(verlauf(tiefe, 0x7C2D12, 0x3B0764, 0x0F172A));
                float breite = 0.02f + 0.11f * tiefe;
                float spalte = expf(-((x - sx) / breite) * ((x - sx) / breite));
                float glitzern = glatt(0.55f, 0.8f, fbm(x * 30f, y * 260f - t * 1.2f, 2));
                p.strahle(0xFDE68A, spalte * glitzern * (1 - tiefe * 0.6f), 0.8f);
                float wellen = 0.5f + 0.5f * sinf(y * 300f - t * 2f + fbm(x * 10f, y * 40f, 2) * 6f);
                p.hell(0.92f + 0.08f * wellen);
            }
            float d = (float) Math.sqrt((x - sx) * (x - sx) + (y - sy) * (y - sy));
            float sonne = form(d, 0.06f) * (y < hz ? 1f : 0f);
            p.strahle(0xFFF7D6, sonne, 1f);
            p.licht(0xFDBA74, expf(-d * d / 0.02f) * 0.55f);
            p.strahle(0xFFFFFF, form(Math.abs(y - hz), 0.4f * PX) * expf(-(x - sx) * (x - sx) / 0.02f) * 0.6f, 0.8f);
        });

        neu("anim_spectrum", "Spectrum", "Silk ribbons in every color", 0xE5E7EB, 0xFFFFFF, 0.5f, 0.3f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x0A0A12, 0x0E0E1A, 0x08080F));
            for (int i = 0; i < 5; i++) {
                float yc = 0.1f + i * 0.2f + 0.06f * sinf(x * 5f + t * 0.45f + i * 1.3f) + 0.02f * sinf(x * 13f - t * 0.6f + i);
                float w = 0.055f + 0.015f * sinf(t * 0.3f + i * 2f);
                float dy = (y - yc) / w;
                float a = expf(-dy * dy * 1.8f);
                if (a < 0.003f) continue;
                float glanz = 0.55f + 0.45f * cosf(dy * 2.6f + sinf(x * 7f + t * 0.8f + i) * 1.2f);
                int c = hsv(frac(x * 0.55f + i * 0.19f + t * 0.04f), 0.72f, 1f);
                p.mische(mischRgb(c, 0x000000, 1f - glanz), a);
                p.leuchte(c, a * glanz * 0.35f);
            }
        });

        neu("anim_starfall", "Starfall", "Meteors streaking across the night", 0x818CF8, 0xE0E7FF, 0.3f, 0.3f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x030712, 0x111A3A, 0x1E1B4B));
            float st = sterne(x, y, 24f, t, 23, 0.5f, 1.1f);
            p.strahle(0xFFFFFF, st * 0.75f, 0.85f);
            float st2 = sterne(x, y, 9f, t, 29, 0.3f, 1.8f);
            p.strahle(0xC7D2FE, st2 * 0.6f, 0.9f);
            float rx = -0.42f, ry = 0.91f;
            for (int i = 0; i < 5; i++) {
                float zyk = 2.6f + 2f * hash(i, 3);
                float ph = (t + hash(i, 9) * 10f) / zyk;
                int runde = fl(ph);
                float f = ph - runde;
                if (f > 0.45f) continue;
                float sx0 = BREITE * (0.35f + 0.75f * hash(i * 7, runde)), sy0 = -0.05f + 0.35f * hash(i * 3, runde + 5);
                float weg = f / 0.45f * 0.9f;
                float kx = sx0 + rx * weg, ky = sy0 + ry * weg;
                float lang = 0.22f * glatt(0f, 0.15f, f / 0.45f);
                float ex = kx - rx * lang, ey = ky - ry * lang;
                float dd = strecke(x, y, ex, ey, kx, ky);
                float entlang = klemme(((x - ex) * rx + (y - ey) * ry) / Math.max(1e-4f, lang));
                float hell = expf(-(dd / (0.6f * PX + 0.7f * PX * entlang)) * (dd / (0.6f * PX + 0.7f * PX * entlang))) * entlang * entlang;
                float aus = glatt(0.45f, 0.35f, f);
                p.strahle(mischRgb(0x818CF8, 0xFFFFFF, entlang), hell * aus, 1f);
            }
            p.mische(0x312E81, glatt(0.7f, 1f, y) * 0.3f);
        });
    }

    /** Hoehe des Sakura-Zweigs an Stelle x. */
    private static float zy(float x) {
        return 0.09f + 0.06f * (x / BREITE) * (x / BREITE) - 0.02f * sinf(x * 9f);
    }

    /** Circuit: springt Bahn j in Zeile z um eine Spur? (-1, 0, +1) */
    private static float sprung(int j, int z) {
        float h = hash(j * 13 + 5, z * 7 + 3);
        return h < 0.18f ? -1 : h > 0.82f ? 1 : 0;
    }
}
