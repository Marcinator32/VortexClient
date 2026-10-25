package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Aura-Bildchen in hoher Qualitaet (seit 4.80): statt ausgeschnittener Pixel-Kloetzchen
 * ein weiches, hochaufgeloestes Bild (SpriteKunst), das sich wie ein Partikel immer zur
 * Kamera dreht -- man sieht nie mehr eine flache Seite. Dazu ein sanfter Lichtschein
 * in der Farbe des Bildchens (additiv). Alle drei Pipelines zeichnen beide Seiten. Ringe, Heiligenschein und Schmetterlinge behalten ihre
 * Lage im Raum (dort ist die Drehung gewollt), werden aber auch weich gezeichnet.
 *
 * Die Bilder werden im Hintergrund berechnet; bis eins fertig ist, zeichnet die Aura
 * wie bisher.
 */
public final class SpriteBilder {
    private SpriteBilder() {}

    private static final int HELL = 0xF000F0;

    private static final class Eintrag {
        volatile SpriteKunst.Bild bild;
        Identifier basis, glow;
        boolean bereit;
    }

    private static final Map<Zubehoer.Teil, Eintrag> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService RECHNER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vortex-Aura-Sprites");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static int zaehler;
    private static boolean kaputt;

    private static Eintrag eintrag(Zubehoer.Teil sp) {
        Eintrag e = CACHE.get(sp);
        if (e == null) {
            Eintrag neu = new Eintrag();
            e = CACHE.putIfAbsent(sp, neu);
            if (e == null) {
                e = neu;
                final Eintrag ziel = neu;
                RECHNER.submit(() -> {
                    try {
                        ziel.bild = SpriteKunst.machen(sp.farben(), sp.glow());
                    } catch (Throwable t) {
                        com.vortex.client.core.Errors.report("SpriteBilder.machen", t);
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
        CapeTextur tb = new CapeTextur("vortexclient-aura-sprite", b.w(), b.h());
        tb.hochladen(AuraTexturen.stufen(b.basis(), b.w(), b.h(), CapeTextur.STUFEN));
        e.basis = Identifier.fromNamespaceAndPath("vortexclient", "aura/sprite_" + n);
        tm.register(e.basis, tb);
        if (b.leuchtet()) {
            CapeTextur tg = new CapeTextur("vortexclient-aura-sprite-glow", b.w(), b.h());
            tg.hochladen(AuraTexturen.stufen(b.leuchten(), b.w(), b.h(), CapeTextur.STUFEN));
            e.glow = Identifier.fromNamespaceAndPath("vortexclient", "aura/sprite_" + n + "_glow");
            tm.register(e.glow, tg);
        }
        e.bereit = true;
    }

    /**
     * Ein Bildchen zeichnen. pose steht schon an der Aura-Mitte (Pixel, y nach oben).
     * Liefert false, wenn das Bild noch nicht fertig ist -- dann zeichnet der Aufrufer wie bisher.
     */
    public static boolean zeichnen(PoseStack pose, SubmitNodeCollector c, Zubehoer.Teil sp, float x, float y, float z, float s,
                                   float drehY, float drehX, float drehZ, float fluegel, boolean kamera) {
        if (kaputt) return false;
        try {
            Eintrag e = eintrag(sp);
            if (!e.bereit) return false;
            SpriteKunst.Bild b = e.bild;
            float hw = b.rasterW() / 2f, hh = b.rasterH() / 2f;
            pose.pushPose();
            pose.translate(x, y, z);
            Matrix4f m = pose.last().pose();
            float spalte = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
            boolean welt = spalte < 0.5f;
            if (kamera && welt) {
                Quaternionf q = new Quaternionf(Minecraft.getInstance().gameRenderer.mainCamera().rotation());
                float tx = m.m30(), ty = m.m31(), tz = m.m32();
                m.identity().translate(tx, ty, tz).rotate(q).scale(spalte * s);
                pose.last().normal().identity().rotate(q);
                if (drehZ != 0) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(drehZ));
            } else {
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(drehY));
                if (drehX != 0) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(drehX));
                if (drehZ != 0) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(drehZ));
                pose.scale(s * fluegel, s, s);
            }
            final boolean beide = false;
            int schein = b.leuchtet() ? 0x70 : 0x3C;
            int sc = b.mittelFarbe();
            int scheinFarbe = 0xFF000000 | (((sc >> 16) & 255) * schein / 255) << 16 | (((sc >> 8) & 255) * schein / 255) << 8 | ((sc & 255) * schein / 255);
            float hs = Math.max(hw, hh) * 2.3f;
            c.submitCustomGeometry(pose, Leuchten.typ(GrossAura.textur(AuraTexturen.Art.GLUT)), (p, vc) -> quad(p, vc, hs, hs, 0.02f, scheinFarbe, beide));
            c.submitCustomGeometry(pose, RenderTypes.entityTranslucent(e.basis), (p, vc) -> quad(p, vc, hw, hh, 0f, 0xFFFFFFFF, beide));
            if (e.glow != null) {
                c.submitCustomGeometry(pose, Leuchten.typ(e.glow), (p, vc) -> quad(p, vc, hw, hh, -0.02f, 0xFFFFFFFF, beide));
            }
            pose.popPose();
            return true;
        } catch (Throwable t) {
            kaputt = true;
            com.vortex.client.core.Errors.report("SpriteBilder", t);
            return false;
        }
    }

    /** Rechteck um die Mitte (halbe Breite/Hoehe), Bild oben = v 0. beide = auch von hinten sichtbar. */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, float hw, float hh, float z, int farbe, boolean beide) {
        ecke(p, vc, -hw, hh, z, 0, 0, farbe, 1);
        ecke(p, vc, -hw, -hh, z, 0, 1, farbe, 1);
        ecke(p, vc, hw, -hh, z, 1, 1, farbe, 1);
        ecke(p, vc, hw, hh, z, 1, 0, farbe, 1);
        if (!beide) return;
        ecke(p, vc, hw, hh, z, 1, 0, farbe, -1);
        ecke(p, vc, hw, -hh, z, 1, 1, farbe, -1);
        ecke(p, vc, -hw, -hh, z, 0, 1, farbe, -1);
        ecke(p, vc, -hw, hh, z, 0, 0, farbe, -1);
    }

    private static void ecke(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int farbe, float n) {
        vc.addVertex(p, x, y, z).setColor(farbe).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(HELL).setNormal(p, 0, 0, n);
    }
}
