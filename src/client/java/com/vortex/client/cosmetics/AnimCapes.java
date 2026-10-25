package com.vortex.client.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Laufzeit der animierten Capes (CapeKunst): je Cape zwei Texturen (Basis +
 * Leuchten), die ein Hintergrund-Thread laufend neu berechnet. Hochgeladen
 * wird im Render-Thread, sobald jemand die Textur abfragt -- also nur, wenn
 * das Cape gerade irgendwo zu sehen ist (Spieler, Vorschau, Menue).
 *
 * Ein Cape, das 1,5 s nicht gefragt wurde, ruht (kein Rechnen, kein Hochladen);
 * nach 30 s werden seine Texturen freigegeben. Seit 4.34 hat jede Kombination aus
 * Cape und Stil (Rand, Glanz, Farbton ...) eigene Texturen; ein Cape ohne Bewegung
 * wird nur einmal gerechnet.
 */
public final class AnimCapes {
    private AnimCapes() {}

    private static final class Platz {
        final CapeKunst.Design design;
        final CapeKunst.Stil stil;
        final String schluessel;
        final Identifier basisId, glowId;
        CapeTextur basis, glow;
        /** Fertiges Bild samt Mip-Stufen (seit 4.33), vom Hintergrund-Thread berechnet. */
        final int[][] fertigB = CapeKunst.stufenPuffer(CapeTextur.STUFEN), fertigG = CapeKunst.stufenPuffer(CapeTextur.STUFEN);
        volatile boolean neu, fertig;
        volatile long gefragt;
        long hochgeladen;
        /** Rohe Aussenseite (fuer das Zeilensprung-Verfahren auf langsamen Rechnern). */
        final int[] rohB = new int[10 * CapeKunst.S * 16 * CapeKunst.S], rohG = new int[10 * CapeKunst.S * 16 * CapeKunst.S];
        /** Wie lange das letzte Bild gedauert hat (ns) und welche Zeilen zuletzt dran waren. */
        long dauer;
        int zeilen;

        Platz(CapeKunst.Design d, CapeKunst.Stil stil, String schluessel) {
            this.design = d;
            this.stil = stil;
            this.schluessel = schluessel;
            this.basisId = Identifier.fromNamespaceAndPath("vortexclient", "cape/anim/" + schluessel);
            this.glowId = Identifier.fromNamespaceAndPath("vortexclient", "cape/anim/" + schluessel + "_glow");
        }
    }

    private static final Map<String, Platz> PLAETZE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Platz> NACH_TEXTUR = new ConcurrentHashMap<>();
    private static final long START = System.nanoTime();
    private static volatile Thread maler;
    private static volatile boolean kaputt;
    private static long aufgeraeumt;

    /** Einheitlicher Schluessel: Cape-Id ohne alte Namen, Stil in fester Reihenfolge. */
    private static String schluessel(String id) {
        return CapeKunst.mitStil(id, CapeKunst.Stil.aus(id));
    }

    /** Ist das ein animiertes Vortex-Cape? */
    public static boolean ist(String id) {
        return CapeKunst.design(id) != null;
    }

    static float zeit() {
        return (System.nanoTime() - START) / 1e9f;
    }

    /** Basistextur des Capes (Render-Thread). Laedt neue Bilder hoch, wenn welche fertig sind. */
    public static Identifier basis(String id) {
        if (kaputt) return null;
        CapeKunst.Design d = CapeKunst.design(id);
        if (d == null) return null;
        String k = schluessel(id);
        Platz p = PLAETZE.get(k);
        try {
            if (p == null) p = anlegen(d, CapeKunst.Stil.aus(id), k);
            p.gefragt = System.currentTimeMillis();
            pumpe(p);
            aufraeumen();
            return p.basisId;
        } catch (Throwable t) {
            kaputt = true;
            com.vortex.client.core.Errors.report("AnimCapes.basis", t);
            return null;
        }
    }

    /** Leucht-Textur zu einer Basistextur (oder null, wenn es kein animiertes Cape ist). */
    public static Identifier glowZu(Identifier basis) {
        Platz p = basis == null ? null : NACH_TEXTUR.get(basis);
        if (p == null) return null;
        p.gefragt = System.currentTimeMillis();
        pumpe(p);
        return p.glowId;
    }

    /** Fuer Vorschaubilder im Menue: Leucht-Textur nach Cape-Id. */
    public static Identifier glow(String id) {
        Identifier b = basis(id);
        return b == null ? null : glowZu(b);
    }

    private static synchronized Platz anlegen(CapeKunst.Design d, CapeKunst.Stil stil, String k) {
        Platz p = PLAETZE.get(k);
        if (p != null) return p;
        elytraVorlageLaden();
        p = new Platz(d, stil, k);
        int[] b = new int[CapeKunst.W * CapeKunst.H], g = new int[CapeKunst.W * CapeKunst.H];
        CapeKunst.rendern(d, stil, zeit(), b, g, p.rohB, p.rohG, -1);
        p.fertig = !stil.bewegt();
        CapeKunst.stufen(b, p.fertigB);
        CapeKunst.stufen(g, p.fertigG);
        p.basis = new CapeTextur("vortexclient-anim-cape", CapeKunst.W, CapeKunst.H);
        p.glow = new CapeTextur("vortexclient-anim-cape-glow", CapeKunst.W, CapeKunst.H);
        p.basis.hochladen(p.fertigB);
        p.glow.hochladen(p.fertigG);
        var tm = Minecraft.getInstance().getTextureManager();
        tm.register(p.basisId, p.basis);
        tm.register(p.glowId, p.glow);
        PLAETZE.put(k, p);
        NACH_TEXTUR.put(p.basisId, p);
        starteMaler();
        return p;
    }

    /** Fertiges Bild hochladen (hoechstens ~40-mal je Sekunde). */
    private static void pumpe(Platz p) {
        if (!p.neu) return;
        long jetzt = System.nanoTime();
        if (jetzt - p.hochgeladen < 24_000_000L) return;
        synchronized (p) {
            p.basis.hochladen(p.fertigB);
            p.glow.hochladen(p.fertigG);
            p.neu = false;
        }
        p.hochgeladen = jetzt;
    }

    /** Texturen von Capes, die 30 s nirgends zu sehen waren, freigeben (Render-Thread). */
    private static void aufraeumen() {
        long jetzt = System.currentTimeMillis();
        if (jetzt - aufgeraeumt < 5000) return;
        aufgeraeumt = jetzt;
        var tm = Minecraft.getInstance().getTextureManager();
        for (Platz p : PLAETZE.values()) {
            if (jetzt - p.gefragt < 30_000) continue;
            synchronized (AnimCapes.class) {
                PLAETZE.remove(p.schluessel);
                NACH_TEXTUR.remove(p.basisId);
            }
            try { tm.release(p.basisId); tm.release(p.glowId); } catch (Throwable ignored) { }
        }
    }

    private static boolean vorlageVersucht;

    /**
     * Elytra-Textur des Spiels als Vorlage fuer die Elytra mit Vortex-Cape (CapeKunst,
     * seit 4.67) -- einmal laden, im Render-Thread. Ein Resource Pack mit eigener Elytra
     * gilt mit. Geht es nicht, nimmt CapeKunst seine eigene Fluegelform.
     */
    static synchronized void elytraVorlageLaden() {
        if (vorlageVersucht) return;
        vorlageVersucht = true;
        try {
            var res = net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png"));
            if (res.isEmpty()) return;
            try (var in = res.get().open(); var bild = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                int w = bild.getWidth(), h = bild.getHeight();
                int[] px = new int[w * h];
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = bild.getPixel(x, y);
                CapeKunst.setzeElytraVorlage(px, w, h);
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("AnimCapes.elytra", t);
        }
    }

    private static synchronized void starteMaler() {
        if (maler != null) return;
        Thread t = new Thread(AnimCapes::malen, "Vortex-Animated-Capes");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        maler = t;
        t.start();
    }

    /**
     * Hintergrund: alle gerade sichtbaren Capes laufend neu rechnen (Ziel 30 Bilder/s).
     * Braucht ein Cape laenger als 26 ms, wird je Bild nur jede zweite Zeile neu gerechnet
     * (abwechselnd) -- die Bewegung bleibt fluessig statt auf 15 Bilder/s zu fallen.
     */
    private static void malen() {
        int[] b = new int[CapeKunst.W * CapeKunst.H], g = new int[CapeKunst.W * CapeKunst.H];
        int[][] sb = CapeKunst.stufenPuffer(CapeTextur.STUFEN), sg = CapeKunst.stufenPuffer(CapeTextur.STUFEN);
        long abstand = 33_000_000L;
        while (true) {
            long start = System.nanoTime();
            long jetzt = System.currentTimeMillis();
            try {
                for (Platz p : PLAETZE.values()) {
                    if (jetzt - p.gefragt > 1500 || p.neu || p.fertig) continue;
                    long t0 = System.nanoTime();
                    int zeilen = p.dauer > 26_000_000L ? (p.zeilen == 0 ? 1 : 0) : -1;
                    CapeKunst.rendern(p.design, p.stil, zeit(), b, g, p.rohB, p.rohG, zeilen);
                    p.zeilen = zeilen;
                    long d = System.nanoTime() - t0;
                    p.dauer = zeilen < 0 ? d : (p.dauer * 3 + d * 2) / 4;
                    CapeKunst.stufen(b, sb);
                    CapeKunst.stufen(g, sg);
                    synchronized (p) {
                        for (int s = 0; s < sb.length; s++) {
                            System.arraycopy(sb[s], 0, p.fertigB[s], 0, sb[s].length);
                            System.arraycopy(sg[s], 0, p.fertigG[s], 0, sg[s].length);
                        }
                        p.neu = true;
                    }
                }
            } catch (Throwable t) {
                com.vortex.client.core.Errors.report("AnimCapes.malen", t);
            }
            long dauer = System.nanoTime() - start;
            abstand = dauer > 40_000_000L ? 50_000_000L : 33_000_000L;
            long rest = abstand - dauer;
            try { Thread.sleep(Math.max(5, rest / 1_000_000L)); } catch (InterruptedException e) { return; }
        }
    }
}
