package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Biegbare Arme und Beine fuer Emotes (seit 4.97).
 *
 * Minecraft zeichnet jedes Glied als einen geraden Quader. Knickt ein Emote einen Ellbogen
 * oder ein Knie ({@link Emotes#biegung}), versteckt EmoteMixin das normale Glied und diese
 * Ebene zeichnet es selbst: derselbe Quader mit denselben Skin-Bildern, aber in der Mitte
 * geknickt. Die Ecken am Gelenk liegen auf der halben Drehung und werden etwas nach aussen
 * geschoben -- so bleibt das Glied am Knick gleich dick und ohne Luecke (wie ein Gehrungsschnitt).
 * Aermel und Hosenbeine (zweite Skin-Ebene) werden genauso gezeichnet, etwas groesser.
 */
public final class BiegeEbene extends RenderLayer<AvatarRenderState, PlayerModel> {

    /** Glieder in derselben Reihenfolge wie in Emotes: rechter Arm, linker Arm, rechtes Bein, linkes Bein. */
    public static final int R_ARM = 0, L_ARM = 1, R_BEIN = 2, L_BEIN = 3;

    /** Hoehe des Gelenks im Glied (Pixel, y nach unten): Ellbogen 4, Knie 6. */
    public static float gelenkY(int glied) {
        return glied < 2 ? 4f : 6f;
    }

    public BiegeEbene(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int licht, AvatarRenderState state, float yRot, float xRot) {
        float[][] b = Emotes.biegung(state);
        if (b == null || state.isInvisible || state.skin == null) return;
        try {
            PlayerModel m = getParentModel();
            boolean slim = state.skin.model() == PlayerModelType.SLIM;
            var typ = RenderTypes.entityTranslucent(state.skin.body().texturePath());
            for (int g = 0; g < 4; g++) {
                if (b[g] == null) continue;
                ModelPart teil = g == R_ARM ? m.rightArm : g == L_ARM ? m.leftArm : g == R_BEIN ? m.rightLeg : m.leftLeg;
                float[] q = quader(g, slim);
                boolean aussen = g == R_ARM ? state.showRightSleeve : g == L_ARM ? state.showLeftSleeve : g == R_BEIN ? state.showRightPants : state.showLeftPants;
                float[] k = b[g].clone();
                float gy = gelenkY(g);
                pose.pushPose();
                teil.translateAndRotate(pose);
                collector.submitCustomGeometry(pose, typ, (p, vc) -> {
                    glied(p, vc, q, 0f, (int) q[6], (int) q[7], gy, k, licht);
                    if (aussen) glied(p, vc, q, 0.25f, (int) q[8], (int) q[9], gy, k, licht);
                });
                pose.popPose();
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("BiegeEbene", t);
        }
    }

    /** x0, y0, z0, w, h, d, u, v (Grundebene), u, v (Aermel/Hose) -- wie Minecrafts Spielermodell. */
    private static float[] quader(int glied, boolean slim) {
        return switch (glied) {
            case R_ARM -> slim ? new float[]{ -2, -2, -2, 3, 12, 4, 40, 16, 40, 32 } : new float[]{ -3, -2, -2, 4, 12, 4, 40, 16, 40, 32 };
            case L_ARM -> slim ? new float[]{ -1, -2, -2, 3, 12, 4, 32, 48, 48, 48 } : new float[]{ -1, -2, -2, 4, 12, 4, 32, 48, 48, 48 };
            case R_BEIN -> new float[]{ -2, 0, -2, 4, 12, 4, 0, 16, 0, 32 };
            default -> new float[]{ -2, 0, -2, 4, 12, 4, 16, 48, 0, 48 };
        };
    }

    /**
     * Ein geknicktes Glied. k = Drehvektor (Achse * Winkel, Bogenmass) des unteren Teils um das
     * Gelenk; Ecken oben bleiben, Ecken am Gelenk drehen um k/2, Ecken unten um k.
     */
    private static void glied(PoseStack.Pose p, VertexConsumer vc, float[] q, float gross, int u, int v, float gy, float[] k, int licht) {
        float x0 = q[0] - gross, y0 = q[1] - gross, z0 = q[2] - gross;
        float x1 = q[0] + q[3] + gross, y1 = q[1] + q[4] + gross, z1 = q[2] + q[5] + gross;
        int w = (int) q[3], h = (int) q[4], d = (int) q[5];
        float[][] oben = new float[8][], mitte = new float[8][];
        float[][] roh = {
                { x0, y0, z0 }, { x1, y0, z0 }, { x1, y1, z0 }, { x0, y1, z0 },
                { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 } };
        for (int i = 0; i < 8; i++) {
            boolean unten = roh[i][1] > gy;
            oben[i] = unten ? drehen(roh[i], gy, k, 1f, false) : roh[i];
            mitte[i] = drehen(new float[]{ roh[i][0], gy, roh[i][2] }, gy, k, 0.5f, true);
        }
        float f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w;
        float f10 = v, f11 = v + d, f12 = v + d + h, fm = f11 + (gy - q[1]);
        viereck(p, vc, oben[5], oben[4], oben[0], oben[1], f5, f10, f6, f11, licht);
        viereck(p, vc, oben[2], oben[3], oben[7], oben[6], f6, f11, f7, f10, licht);
        seite(p, vc, oben, mitte, 0, 4, 7, 3, f4, f5, f11, fm, f12, licht);
        seite(p, vc, oben, mitte, 1, 0, 3, 2, f5, f6, f11, fm, f12, licht);
        seite(p, vc, oben, mitte, 5, 1, 2, 6, f6, f8, f11, fm, f12, licht);
        seite(p, vc, oben, mitte, 4, 5, 6, 7, f8, f9, f11, fm, f12, licht);
    }

    /** Seitenflaeche in zwei Haelften: a, b oben, c unter b, e unter a -- wie Minecrafts Reihenfolge. */
    private static void seite(PoseStack.Pose p, VertexConsumer vc, float[][] e, float[][] mi, int a, int b, int c, int dd,
                              float u1, float u2, float vo, float vm, float vu, int licht) {
        viereck(p, vc, e[a], e[b], mi[b], mi[a], u1, vo, u2, vm, licht);
        viereck(p, vc, mi[a], mi[b], e[c], e[dd], u1, vm, u2, vu, licht);
    }

    /** Viereck mit Minecrafts Bild-Zuordnung: Ecke 0 (u2, v1), 1 (u1, v1), 2 (u1, v2), 3 (u2, v2). */
    private static void viereck(PoseStack.Pose p, VertexConsumer vc, float[] a, float[] b, float[] c, float[] d,
                                float u1, float v1, float u2, float v2, int licht) {
        float ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
        float wx = d[0] - b[0], wy = d[1] - b[1], wz = d[2] - b[2];
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6f) return;
        nx /= l; ny /= l; nz /= l;
        ecke(p, vc, a, u2, v1, nx, ny, nz, licht);
        ecke(p, vc, b, u1, v1, nx, ny, nz, licht);
        ecke(p, vc, c, u1, v2, nx, ny, nz, licht);
        ecke(p, vc, d, u2, v2, nx, ny, nz, licht);
    }

    private static void ecke(PoseStack.Pose p, VertexConsumer vc, float[] e, float u, float v, float nx, float ny, float nz, int licht) {
        vc.addVertex(p, e[0] / 16f, e[1] / 16f, e[2] / 16f).setColor(0xFFFFFFFF).setUv(u / 64f, v / 64f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(licht).setNormal(p, nx, ny, nz);
    }

    /**
     * Punkt um die Gelenkachse (durch y = gy, x = z = 0) drehen: Drehvektor k mal anteil.
     * gehrung: am Gelenk quer zur Knickrichtung um 1/cos(Winkel/2) strecken, damit das Glied
     * am Knick nicht duenner wird.
     */
    private static float[] drehen(float[] pkt, float gy, float[] k, float anteil, boolean gehrung) {
        float kx = k[0] * anteil, ky = k[1] * anteil, kz = k[2] * anteil;
        float a = (float) Math.sqrt(kx * kx + ky * ky + kz * kz);
        float x = pkt[0], y = pkt[1] - gy, z = pkt[2];
        if (a < 1e-5f) return new float[]{ x, y + gy, z };
        float ax = kx / a, ay = ky / a, az = kz / a;
        if (gehrung) {
            float px = -az, pz = ax;
            float s = (float) (1.0 / Math.max(0.72, Math.cos(a)));
            float teil = x * px + z * pz;
            x += px * teil * (s - 1f);
            z += pz * teil * (s - 1f);
        }
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float dot = ax * x + ay * y + az * z;
        float cx = ay * z - az * y, cy = az * x - ax * z, cz = ax * y - ay * x;
        return new float[]{
                x * c + cx * s + ax * dot * (1 - c),
                y * c + cy * s + ay * dot * (1 - c) + gy,
                z * c + cz * s + az * dot * (1 - c) };
    }
}
