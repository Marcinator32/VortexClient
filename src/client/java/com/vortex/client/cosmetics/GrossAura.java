package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.cosmetics.AuraTexturen.Art;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Grosse Auren (seit 4.79): weiche, leuchtende Effekte mit grossem Radius (gut zwei
 * Bloecke) statt kleiner Pixel-Figuren -- Zauberkreis, Sonnenkrone, Wirbel, Frostwelle,
 * Flammenring, Polarlicht. Alles aus additiv gezeichneten Flaechen mit weichen Texturen
 * (AuraTexturen), Bewegung bildgenau ueber die Zeit -- nichts springt.
 *
 * Koordinaten wie bei den anderen Auren: Pixel, y nach oben, Fuesse bei y = -24.
 * In designs.txt steht so eine Aura mit "motion: big_..." und einem Bildchen fuers Menue.
 */
public final class GrossAura {
    private GrossAura() {}

    private static final int HELL = 0xF000F0;
    private static final Map<Art, Identifier> TEXTUREN = new EnumMap<>(Art.class);
    private static boolean kaputt;
    private static final float[] U = { 0, 1, 1, 0 }, V = { 0, 0, 1, 1 };
    /** Gesammelte Flaechen je Textur. */
    private static final Map<Art, List<AuraFormen.Flaeche>> FLAECHEN = new EnumMap<>(Art.class);

    public static boolean ist(String bewegung) {
        return bewegung != null && bewegung.startsWith("big_");
    }

    static Identifier textur(Art a) {
        Identifier id = TEXTUREN.get(a);
        if (id != null) return id;
        id = Identifier.fromNamespaceAndPath("vortexclient", "aura/" + a.name().toLowerCase(java.util.Locale.ROOT));
        CapeTextur t = new CapeTextur("vortexclient-aura-" + a.name(), a.w, a.h);
        t.hochladen(AuraTexturen.stufen(AuraTexturen.bild(a), a.w, a.h, CapeTextur.STUFEN));
        Minecraft.getInstance().getTextureManager().register(id, t);
        TEXTUREN.put(a, id);
        return id;
    }

    public static void zeichnen(PoseStack pose, SubmitNodeCollector c, String art, float t, int seed, int dichte) {
        if (kaputt) return;
        try {
            for (List<AuraFormen.Flaeche> l : FLAECHEN.values()) l.clear();
            for (AuraFormen.Flaeche f : AuraFormen.bauen(art, t, seed, dichte)) {
                FLAECHEN.computeIfAbsent(f.art(), k -> new ArrayList<>()).add(f);
            }
            for (Map.Entry<Art, List<AuraFormen.Flaeche>> e : FLAECHEN.entrySet()) {
                List<AuraFormen.Flaeche> l = e.getValue();
                if (l.isEmpty()) continue;
                final List<AuraFormen.Flaeche> kopie = new ArrayList<>(l);
                c.submitCustomGeometry(pose, Leuchten.typ(textur(e.getKey())), (p, vc) -> {
                    for (AuraFormen.Flaeche f : kopie) {
                        for (int i = 0; i < 4; i++) {
                            vc.addVertex(p, f.p()[i * 3], f.p()[i * 3 + 1], f.p()[i * 3 + 2]).setColor(f.farbe()).setUv(U[i], V[i])
                                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(HELL).setNormal(p, 0, 1, 0);
                        }
                    }
                });
            }
        } catch (Throwable e) {
            kaputt = true;
            com.vortex.client.core.Errors.report("GrossAura", e);
        }
    }
}
