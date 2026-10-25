package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Flache Teile in hoher Qualitaet (seit 4.81): Fluegel, Rucksaecke, Schwerter, Brillen,
 * Anhaenger. Vorder- und Rueckseite sind ein geglaettetes, hochaufgeloestes Bild
 * (SpriteKunst.flach) statt einzelner Pixel-Vierecke -- weiche Farbuebergaenge, glatte
 * Kante, leichte Woelbung. Die Seitenwaende bleiben aus dem Pixel-Umriss (sonst waere das
 * Teil hauchduenn). Leuchtende Stellen bekommen eine eigene additive Ebene.
 *
 * Die Bilder werden im Hintergrund berechnet; bis eins fertig ist, wird wie bisher gezeichnet.
 */
public final class FlachBilder {
    private FlachBilder() {}

    private static final int HELL = 0xF000F0;

    private static final class Eintrag {
        volatile SpriteKunst.Bild bild;
        volatile Netz seiten;
        Identifier basis, glow;
        boolean bereit;
    }

    private static final Map<int[][], Eintrag> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService RECHNER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vortex-Flach-Bilder");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static int zaehler;
    private static boolean kaputt;

    private static Eintrag eintrag(int[][] farben, boolean[][] glow, float tiefe, int schnitte) {
        Eintrag e = CACHE.get(farben);
        if (e == null) {
            Eintrag neu = new Eintrag();
            e = CACHE.putIfAbsent(farben, neu);
            if (e == null) {
                e = neu;
                final Eintrag ziel = neu;
                RECHNER.submit(() -> {
                    try {
                        Netz n = new Netz();
                        n.waende(farben, glow, tiefe, schnitte);
                        ziel.seiten = n;
                        ziel.bild = SpriteKunst.flach(farben, glow, schnitte);
                    } catch (Throwable t) {
                        com.vortex.client.core.Errors.report("FlachBilder.flach", t);
                    }
                });
            }
        }
        if (!e.bereit && e.bild != null) hochladen(e);
        return e;
    }

    private static void hochladen(Eintrag e) {
        SpriteKunst.Bild b = e.bild;
        int n = zaehler++;
        var tm = Minecraft.getInstance().getTextureManager();
        CapeTextur tb = new CapeTextur("vortexclient-flach", b.w(), b.h());
        tb.hochladen(AuraTexturen.stufen(b.basis(), b.w(), b.h(), CapeTextur.STUFEN));
        e.basis = Identifier.fromNamespaceAndPath("vortexclient", "flach/teil_" + n);
        tm.register(e.basis, tb);
        if (b.leuchtet()) {
            CapeTextur tg = new CapeTextur("vortexclient-flach-glow", b.w(), b.h());
            tg.hochladen(AuraTexturen.stufen(b.leuchten(), b.w(), b.h(), CapeTextur.STUFEN));
            e.glow = Identifier.fromNamespaceAndPath("vortexclient", "flach/teil_" + n + "_glow");
            tm.register(e.glow, tg);
        }
        e.bereit = true;
    }

    /**
     * Seitenwaende (Netz) des Teils, sobald fertig -- sonst null. schnitte = Kanten, die nur
     * Schnitte sind (Fluegelglieder, siehe SpriteKunst.spalte); bei einem Teil immer gleich.
     */
    public static Netz seiten(int[][] farben, boolean[][] glow, float tiefe, int schnitte) {
        if (kaputt) return null;
        Eintrag e = eintrag(farben, glow, tiefe, schnitte);
        return e.bereit ? e.seiten : null;
    }

    /**
     * Vorder- und Rueckseite zeichnen (pose steht in Bildpixeln, Bildmitte im Ursprung).
     * leucht = Staerke der Leucht-Ebene (1 = normal). Liefert false, wenn noch nicht fertig.
     */
    public static boolean flaechen(PoseStack pose, SubmitNodeCollector c, int[][] farben, boolean[][] glow, float tiefe, int schnitte, int licht, float leucht) {
        if (kaputt) return false;
        try {
            Eintrag e = eintrag(farben, glow, tiefe, schnitte);
            if (!e.bereit) return false;
            SpriteKunst.Bild b = e.bild;
            float hw = b.rasterW() / 2f, hh = b.rasterH() / 2f, z0 = -tiefe / 2f, z1 = tiefe / 2f;
            c.submitCustomGeometry(pose, RenderTypes.entityTranslucent(e.basis), (p, vc) -> {
                seite(p, vc, hw, hh, z0, -1, 0xFFFFFFFF, licht);
                seite(p, vc, hw, hh, z1, 1, 0xFFFFFFFF, licht);
            });
            if (e.glow != null && leucht > 0.01f) {
                int k = Math.max(0, Math.min(255, Math.round(255 * leucht)));
                int farbe = 0xFF000000 | k << 16 | k << 8 | k;
                float d = Math.max(0.004f, tiefe * 0.02f);
                c.submitCustomGeometry(pose, Leuchten.typ(e.glow), (p, vc) -> {
                    seite(p, vc, hw, hh, z0 - d, -1, farbe, HELL);
                    seite(p, vc, hw, hh, z1 + d, 1, farbe, HELL);
                });
            }
            return true;
        } catch (Throwable t) {
            kaputt = true;
            com.vortex.client.core.Errors.report("FlachBilder", t);
            return false;
        }
    }

    /**
     * Ein Bild-Viereck bei z; n = -1 vorne (Normale -z), 1 hinten. Beide Seiten liegen gleich
     * im Raum -- von hinten sieht man das Bild also gespiegelt, genau passend zum Umriss.
     */
    private static void seite(PoseStack.Pose p, VertexConsumer vc, float hw, float hh, float z, int n, int farbe, int licht) {
        if (n < 0) {
            ecke(p, vc, -hw, -hh, z, 0, 1, farbe, licht, n);
            ecke(p, vc, -hw, hh, z, 0, 0, farbe, licht, n);
            ecke(p, vc, hw, hh, z, 1, 0, farbe, licht, n);
            ecke(p, vc, hw, -hh, z, 1, 1, farbe, licht, n);
        } else {
            ecke(p, vc, -hw, -hh, z, 0, 1, farbe, licht, n);
            ecke(p, vc, hw, -hh, z, 1, 1, farbe, licht, n);
            ecke(p, vc, hw, hh, z, 1, 0, farbe, licht, n);
            ecke(p, vc, -hw, hh, z, 0, 0, farbe, licht, n);
        }
    }

    private static void ecke(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int farbe, int licht, int n) {
        vc.addVertex(p, x, y, z).setColor(farbe).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(licht).setNormal(p, 0, 0, n);
    }
}
