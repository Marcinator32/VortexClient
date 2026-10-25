package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * Realistischeres Cape: statt einer starren Platte (Vanilla) besteht es aus
 * {@link #STREIFEN} Streifen, die sich nacheinander biegen -- wie Stoff.
 *
 *   - Oben haengt es wie das Vanilla-Cape (gleicher Winkel aus capeLean/
 *     capeFlap, gleicher Ansatz am Ruecken, gleiche Textur-Aufteilung).
 *   - Nach unten biegt es sich staerker vom Koerper weg.
 *   - Eine Welle laeuft von oben nach unten durch -- beim Laufen staerker,
 *     im Stehen nur ein leichtes Wehen.
 *
 * Gilt fuer jedes Cape (Mojang, Vortex, eigenes Bild) bei jedem Spieler. Das
 * Vanilla-Cape wird dann ueber Fabric (ALLOW_CAPE_RENDER) ausgeschaltet; mit
 * "Cape physics: Off" im Cosmetics-Menue bleibt alles wie bei Minecraft.
 */
public class CapeEbene extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Seit 4.63 doppelt so fein (vorher 8 x 6): Wellen ohne sichtbare Knicke. */
    static final int STREIFEN = 16;
    private static final float GRAD = (float) Math.PI / 180f;

    public CapeEbene(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    /** Dieselben Bedingungen wie beim Vanilla-Cape: sichtbar, Cape vorhanden, keine Fluegel. */
    static boolean zeigt(AvatarRenderState s) {
        if (s.isInvisible || !s.showCape || s.skin == null || s.skin.cape() == null) return false;
        return s.chestEquipment == null || !s.chestEquipment.has(DataComponents.GLIDER);
    }

    private static boolean brustplatte(ItemStack brust) {
        if (brust == null || brust.isEmpty() || brust.has(DataComponents.GLIDER)) return false;
        var e = brust.get(DataComponents.EQUIPPABLE);
        return e != null && e.slot() == EquipmentSlot.CHEST && e.assetId().isPresent();
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int licht, AvatarRenderState s, float yRot, float xRot) {
        try {
            if (!Cosmetics.capePhysik() || !zeigt(s)) return;
            Identifier tex = s.skin.cape().texturePath();
            Identifier glow = AnimCapes.glowZu(tex);
            float zeit = s.ageInTicks;
            float tempo = Math.min(1f, s.walkAnimationSpeed);
            float oben = 6f + s.capeLean / 2f + s.capeFlap;
            float seite = s.capeLean2 / 2f;

            pose.pushPose();
            if (brustplatte(s.chestEquipment)) pose.translate(0f, -0.053125f, 0.06875f);
            getParentModel().body.translateAndRotate(pose);
            pose.translate(0f, 0f, 2f / 16f);
            pose.mulPose(new Quaternionf().rotationX(oben * GRAD).rotateZ(seite * GRAD));
            final float drehY = (float) Math.PI - seite * GRAD;

            float h = 16f / STREIFEN;
            float biegung = Math.min(40f, 7f + oben * 0.6f);
            float boee = 0.65f + 0.35f * (float) Math.sin(zeit * 0.035f) * (float) Math.sin(zeit * 0.011f + 1.7f);
            float welle = 3.2f * boee + tempo * 9f, schnell = 0.14f + tempo * 0.3f;
            float vorher = 0f;
            for (int k = 0; k < STREIFEN; k++) {
                float anteil = (k + 1f) / STREIFEN;
                float ziel = biegung * anteil * anteil + (float) Math.sin(zeit * schnell - k * 0.375f) * welle * anteil
                        + (float) Math.sin(zeit * (schnell * 2.3f) - k * 0.9f) * welle * 0.25f * anteil * anteil;
                pose.mulPose(new Quaternionf().rotationX((ziel - vorher) * GRAD));
                vorher = ziel;
                final int nr = k;
                pose.pushPose();
                pose.mulPose(new Quaternionf().rotationY(drehY));
                final Falten f = new Falten(zeit, tempo, Cosmetics.capeWellen());
                collector.submitCustomGeometry(pose, RenderTypes.entitySolid(tex), (p, vc) -> streifen(p, vc, nr, h, licht, f));
                if (glow != null) collector.submitCustomGeometry(pose, RenderTypes.eyes(glow), (p, vc) -> streifen(p, vc, nr, h, 0xF000F0, f));
                pose.popPose();
                pose.translate(0f, h / 16f, 0f);
            }
            pose.popPose();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("CapeEbene", t);
        }
    }

    /**
     * Echte Falten (seit 4.32): Das Cape ist nicht mehr flach, sondern wellt sich quer --
     * nach unten staerker, beim Laufen mehr, und die Wellen wandern langsam (Wind).
     * Dazu legt es sich in der Mitte leicht nach aussen (folgt dem Ruecken).
     */
    record Falten(float zeit, float tempo, boolean an) {
        /** Versatz nach hinten (in Pixeln) an Stelle u (0 links .. 1 rechts) und v (0 oben .. 1 unten). Ausgeschaltet: flach. */
        float dz(float u, float v) {
            if (!an) return 0;
            float amp = (0.12f + 0.88f * v) * (0.6f + 0.55f * tempo) * 0.75f;
            float welle = (float) Math.sin(u * Math.PI * 3.0 + zeit * (0.09f + tempo * 0.12f) + v * 2.3f)
                    + 0.35f * (float) Math.sin(u * Math.PI * 5.0 - zeit * 0.13f + v * 4f) * v;
            float bauch = 0.35f * (1 - (2 * u - 1) * (2 * u - 1)) * (0.4f + 0.6f * v);
            return amp * welle + bauch;
        }
        /** Steigung dz/dx (Pixel je Pixel) fuer die Normale. */
        float steigung(float u, float v) {
            if (!an) return 0;
            float e = 0.02f;
            return (dz(u + e, v) - dz(u - e, v)) / (2 * e * 10f);
        }
    }

    static final int SPALTEN = 12;

    /**
     * Ein Streifen (10 x h x 1 Pixel) mit dem passenden Ausschnitt der
     * Cape-Textur -- Aufteilung wie beim Vanilla-Cape (64 x 32):
     * vorne x 1-11, hinten x 12-22, Seiten x 0-1 und 11-12, jeweils ab y 1;
     * oben x 1-11, unten x 11-21 in der Zeile y 0-1. Vorder- und Rueckseite in
     * SPALTEN Spalten mit Falten.
     */
    private static void streifen(PoseStack.Pose p, VertexConsumer vc, int nr, float h, int licht, Falten f) {
        float y0 = 0f, y1 = h / 16f;
        float v0 = 1 + nr * h, v1 = v0 + h;
        float va = nr / (float) STREIFEN, vb = (nr + 1) / (float) STREIFEN;
        for (int c = 0; c < SPALTEN; c++) {
            float ua = c / (float) SPALTEN, ub = (c + 1f) / SPALTEN;
            float xa = (-5 + 10 * ua) / 16f, xb = (-5 + 10 * ub) / 16f;
            float za0 = f.dz(ua, va) / 16f, zb0 = f.dz(ub, va) / 16f, za1 = f.dz(ua, vb) / 16f, zb1 = f.dz(ub, vb) / 16f;
            float sa0 = f.steigung(ua, va), sb0 = f.steigung(ub, va), sa1 = f.steigung(ua, vb), sb1 = f.steigung(ub, vb);
            float tu0 = 1 + 10 * ua, tu1 = 1 + 10 * ub;
            quadWeich(p, vc, licht, -1, sb0, sa0, sa1, sb1,
                    xb, y0, -1 / 16f + zb0, xa, y0, -1 / 16f + za0, xa, y1, -1 / 16f + za1, xb, y1, -1 / 16f + zb1, tu1, v0, tu0, v1);
            float hu0 = 22 - 10 * ua, hu1 = 22 - 10 * ub;
            quadWeich(p, vc, licht, 1, sa0, sb0, sb1, sa1,
                    xa, y0, za0, xb, y0, zb0, xb, y1, zb1, xa, y1, za1, hu0, v0, hu1, v1);
        }
        float l0 = f.dz(0, va) / 16f, l1 = f.dz(0, vb) / 16f, r0 = f.dz(1, va) / 16f, r1 = f.dz(1, vb) / 16f;
        float x0 = -5 / 16f, x1 = 5 / 16f, z0 = -1 / 16f;
        quad(p, vc, licht, -1, 0, 0, x0, y0, z0 + l0, x0, y0, l0, x0, y1, l1, x0, y1, z0 + l1, 0, v0, 1, v1);
        quad(p, vc, licht, 1, 0, 0, x1, y0, r0, x1, y0, z0 + r0, x1, y1, z0 + r1, x1, y1, r1, 11, v0, 12, v1);
        if (nr == 0) {
            for (int c = 0; c < SPALTEN; c++) {
                float ua = c / (float) SPALTEN, ub = (c + 1f) / SPALTEN;
                float xa = (-5 + 10 * ua) / 16f, xb = (-5 + 10 * ub) / 16f, za = f.dz(ua, 0) / 16f, zb = f.dz(ub, 0) / 16f;
                quad(p, vc, licht, 0, -1, 0, xb, y0, zb, xa, y0, za, xa, y0, z0 + za, xb, y0, z0 + zb, 1 + 10 * (1 - ub), 0, 1 + 10 * (1 - ua), 1);
            }
        }
        if (nr == STREIFEN - 1) {
            for (int c = 0; c < SPALTEN; c++) {
                float ua = c / (float) SPALTEN, ub = (c + 1f) / SPALTEN;
                float xa = (-5 + 10 * ua) / 16f, xb = (-5 + 10 * ub) / 16f, za = f.dz(ua, 1) / 16f, zb = f.dz(ub, 1) / 16f;
                quad(p, vc, licht, 0, 1, 0, xb, y1, z0 + zb, xa, y1, z0 + za, xa, y1, za, xb, y1, zb, 11 + 10 * (1 - ub), 0, 11 + 10 * (1 - ua), 1);
            }
        }
    }

    /**
     * Wie quad, aber jede Ecke bekommt ihre eigene Normale aus der Faltensteigung -- das
     * Licht laeuft weich ueber die Falten statt in Streifen. seite -1 = vorne, +1 = hinten.
     */
    private static void quadWeich(PoseStack.Pose p, VertexConsumer vc, int licht, float seite, float sa, float sb, float sc, float sd,
                                  float ax, float ay, float az, float bx, float by, float bz,
                                  float cx, float cy, float cz, float dx, float dy, float dz,
                                  float u0, float v0, float u1, float v1) {
        float a = u0 / 64f, b = u1 / 64f, c = v0 / 32f, d = v1 / 32f;
        eckeN(p, vc, ax, ay, az, a, c, licht, seite, sa);
        eckeN(p, vc, bx, by, bz, b, c, licht, seite, sb);
        eckeN(p, vc, cx, cy, cz, b, d, licht, seite, sc);
        eckeN(p, vc, dx, dy, dz, a, d, licht, seite, sd);
    }

    private static void eckeN(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int licht, float seite, float steig) {
        float l = (float) Math.sqrt(1 + steig * steig);
        ecke(p, vc, x, y, z, u, v, licht, -seite * steig / l, 0, seite / l);
    }

    /** Vier Ecken a-b-c-d; Textur u0..u1 / v0..v1 (in 64x32-Pixeln) von a nach c. */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, int licht, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1) {
        float a = u0 / 64f, b = u1 / 64f, c = v0 / 32f, d = v1 / 32f;
        ecke(p, vc, ax, ay, az, a, c, licht, nx, ny, nz);
        ecke(p, vc, bx, by, bz, b, c, licht, nx, ny, nz);
        ecke(p, vc, cx, cy, cz, b, d, licht, nx, ny, nz);
        ecke(p, vc, dx, dy, dz, a, d, licht, nx, ny, nz);
    }

    private static void ecke(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int licht, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(licht).setNormal(p, nx, ny, nz);
    }
}
