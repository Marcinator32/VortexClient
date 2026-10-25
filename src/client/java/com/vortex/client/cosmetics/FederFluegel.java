package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zeichnet Federfluegel aus einzelnen 3D-Federn (seit 4.82, Modell aus FederKunst).
 * Federn mit weicher Kante (entityTranslucent, beide Seiten), Licht wie die Spielwelt;
 * leuchtende Federn zusaetzlich mit einer additiven Leucht-Ebene. Die Schwungfedern
 * zittern leicht im Wind -- staerker, je weiter der Fluegel offen ist.
 */
public final class FederFluegel {
    private FederFluegel() {}

    private static final int HELL = 0xF000F0;
    private static Identifier atlas;
    private static boolean kaputt;
    private static final Map<Zubehoer.Teil, FederKunst.Modell> MODELLE = new ConcurrentHashMap<>();

    /** Nur echte Federfluegel (nicht Fledermaus oder Schmetterling). */
    public static boolean passt(Zubehoer.Teil teil) {
        return !kaputt && teil.vox() == null && teil.anim().equals("wing:feather");
    }

    private static Identifier atlas() {
        if (atlas != null) return atlas;
        int w = FederKunst.TW * FederKunst.ARTEN, h = FederKunst.TH;
        CapeTextur t = new CapeTextur("vortexclient-federn", w, h);
        t.hochladen(AuraTexturen.stufen(FederKunst.atlas(), w, h, CapeTextur.STUFEN));
        atlas = Identifier.fromNamespaceAndPath("vortexclient", "fluegel/federn");
        Minecraft.getInstance().getTextureManager().register(atlas, t);
        return atlas;
    }

    private static FederKunst.Modell modell(Zubehoer.Teil teil, int[] grenzen, float gelenk) {
        return MODELLE.computeIfAbsent(teil, k -> FederKunst.bauen(teil.farben(), teil.glow(), grenzen, gelenk));
    }

    /**
     * Ein Glied zeichnen; pose steht am Gelenk des Glieds (Bildpixel). seite 1 = rechts,
     * -1 = links (gespiegelt). flatter = Staerke des Zitterns, leucht = Staerke des Leuchtens.
     * Liefert false, wenn etwas schiefging -- dann zeichnet der Aufrufer wie bisher.
     */
    public static boolean glied(PoseStack pose, SubmitNodeCollector c, Zubehoer.Teil teil, int[] grenzen, float gelenk,
                                int k, int seite, int licht, float t, float flatter, float leucht, boolean glitzer) {
        if (kaputt) return false;
        try {
            FederKunst.Modell m = modell(teil, grenzen, gelenk);
            List<FederKunst.Feder> federn = m.glieder().get(k);
            if (federn.isEmpty()) return true;
            Identifier tex = atlas();
            c.submitCustomGeometry(pose, RenderTypes.entityTranslucent(tex), (p, vc) -> {
                for (FederKunst.Feder f : federn) feder(p, vc, f, seite, t, flatter, f.leuchtet ? HELL : licht, f.farbe, 1f);
            });
            if (m.leuchtet() && leucht > 0.01f) {
                c.submitCustomGeometry(pose, Leuchten.typ(tex), (p, vc) -> {
                    for (FederKunst.Feder f : federn) if (f.leuchtet) feder(p, vc, f, seite, t, flatter, HELL, f.glut, 0.3f * leucht);
                });
            }
            if (glitzer) {
                float pos = (t % 100f) / 100f * 2.6f - 0.4f;
                c.submitCustomGeometry(pose, Leuchten.typ(tex), (p, vc) -> {
                    for (FederKunst.Feder f : federn) glanz(p, vc, f, seite, t, flatter, pos, k);
                });
            }
            return true;
        } catch (Throwable e) {
            kaputt = true;
            com.vortex.client.core.Errors.report("FederFluegel", e);
            return false;
        }
    }

    private static final int[][] VIERECKE = viereckListe();

    private static int[][] viereckListe() {
        int n = FederKunst.REIHEN * (FederKunst.SPALTEN - 1);
        int[][] q = new int[n][];
        int i = 0;
        for (int r = 0; r < FederKunst.REIHEN; r++) {
            for (int s = 0; s < FederKunst.SPALTEN - 1; s++) {
                int a = r * FederKunst.SPALTEN + s;
                q[i++] = new int[] { a, a + 1, a + 1 + FederKunst.SPALTEN, a + FederKunst.SPALTEN };
            }
        }
        return q;
    }

    /**
     * Eine Feder ausgeben. farben = je Eckpunkt (bei der Leucht-Ebene die Glut mit Anteil im
     * Alpha); staerke multipliziert die Farbe (Leucht-Ebene: Helligkeit, Alpha immer voll).
     */
    private static void feder(PoseStack.Pose p, VertexConsumer vc, FederKunst.Feder f, int seite, float t, float flatter,
                              int licht, int[] farben, float staerke) {
        float wehen = f.flattert ? flatter * f.laenge * (float) Math.sin(t * 0.13f + f.phase) : 0f;
        for (int[] q : VIERECKE) {
            for (int k : q) {
                float v = f.entlang[k];
                int c = farben[k];
                int farbe;
                if (staerke == 1f) farbe = c | 0xFF000000;
                else {
                    float a = ((c >>> 24) & 255) / 255f * staerke;
                    farbe = 0xFF000000 | Math.round(((c >> 16) & 255) * a) << 16 | Math.round(((c >> 8) & 255) * a) << 8 | Math.round((c & 255) * a);
                }
                vc.addVertex(p, seite * f.pos[k * 3], f.pos[k * 3 + 1], f.pos[k * 3 + 2] + wehen * v * v)
                        .setColor(farbe).setUv(f.uv[k * 2], f.uv[k * 2 + 1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(licht)
                        .setNormal(p, seite * f.nrm[k * 3], f.nrm[k * 3 + 1], f.nrm[k * 3 + 2]);
            }
        }
    }

    /** Glitzern: ein heller Streifen wandert schraeg ueber den Fluegel (wie bei den anderen Teilen). */
    private static void glanz(PoseStack.Pose p, VertexConsumer vc, FederKunst.Feder f, int seite, float t, float flatter, float pos, int glied) {
        float wehen = f.flattert ? flatter * f.laenge * (float) Math.sin(t * 0.13f + f.phase) : 0f;
        for (int[] q : VIERECKE) {
            boolean da = false;
            int[] farbe = new int[4];
            for (int i = 0; i < 4; i++) {
                int k = q[i];
                float u = (f.pos[k * 3] + glied * 13f) / 40f * 0.6f + (12f - f.pos[k * 3 + 1]) / 32f;
                float d = (u - pos) / 0.16f;
                int a = Math.round(Math.min(1f, (float) Math.exp(-d * d) * 0.45f) * 255f);
                if (a > 4) da = true;
                farbe[i] = 0xFF000000 | a << 16 | a << 8 | a;
            }
            if (!da) continue;
            for (int i = 0; i < 4; i++) {
                int k = q[i];
                float v = f.entlang[k];
                vc.addVertex(p, seite * f.pos[k * 3], f.pos[k * 3 + 1], f.pos[k * 3 + 2] + wehen * v * v + 0.01f)
                        .setColor(farbe[i]).setUv(f.uv[k * 2], f.uv[k * 2 + 1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(HELL)
                        .setNormal(p, seite * f.nrm[k * 3], f.nrm[k * 3 + 1], f.nrm[k * 3 + 2]);
            }
        }
    }
}
