package com.vortex.client.cosmetics;

import com.vortex.client.gui.VortexStyle;
import com.vortex.client.gui.glatt.Glatt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;

/**
 * Dein Charakter im Hauptmenue -- mit Skin, Vortex-Cape und Hut; der Kopf
 * folgt der Maus. Gewaehlte Partikel erscheinen als kleine Funken drumherum
 * (echte Partikel gibt es ohne Welt nicht).
 *
 * Ohne Welt gibt es keine Spieler-Entity: der Renderzustand wird direkt
 * gebaut. Die Hut-Ebene erkennt ihn an {@link #ID}.
 */
public final class TitelFigur {
    private TitelFigur() {}

    /** Entity-Id des Renderzustands der Titel-Figur (gibt es in keiner Welt). */
    public static final int ID = Integer.MIN_VALUE + 7;

    private static volatile PlayerSkin skin;
    private static boolean angefragt;

    /** Nach einem Accountwechsel: Skin des neuen Kontos holen. */
    public static void neuLaden() {
        skin = null;
        angefragt = false;
    }
    private static final long START = System.nanoTime();

    /** Partikel-Farben fuer die Funken (Rest: Vortex-Violett). */
    private static final Map<String, Integer> FARBEN = Map.ofEntries(
            Map.entry("hearts", 0xFFFF4D6D), Map.entry("flames", 0xFFFFA23A), Map.entry("soul_flames", 0xFF4FE3E8),
            Map.entry("notes", 0xFF6EE36E), Map.entry("cherry", 0xFFFFB7D5), Map.entry("snow", 0xFFFFFFFF),
            Map.entry("totem", 0xFFF7D046), Map.entry("emerald", 0xFF3EE07A), Map.entry("glow", 0xFF7DF9E8),
            Map.entry("electric", 0xFFB9E6FF), Map.entry("sparkles", 0xFFFFF4C2));

    public static void zeichnen(GuiGraphicsExtractor g, int breite, int hoehe, int mx, int my) {
        com.vortex.client.gui.menu.MenuLeiste.Lage l = com.vortex.client.gui.menu.MenuLeiste.lage();
        if (l == null || l.groesse() <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (System.nanoTime() - START) / 1_000_000_000f;
        float cx = l.figurX(), fuss = l.fuss(), groesse = l.groesse();

        Glatt.licht(g, cx, fuss - groesse * 1.1f, groesse * 1.9f, Glatt.alpha(com.vortex.client.gui.menu.TitelStil.A, 0.20f));
        float boden = fuss - groesse * 0.2f;
        schatten(g, cx, boden, groesse);
        com.vortex.client.gui.menu.TitelStil.bahn(g, cx, boden, groesse, t, true);

        String funken = Cosmetics.getragen().partikel();
        if (!funken.isEmpty()) funken(g, cx, fuss - groesse * 0.95f, groesse, t, FARBEN.getOrDefault(funken, 0xFFB79CFF), true);

        try {
            AvatarRenderState s = zustand(mc, t, cx, fuss - groesse * 1.6f, mx, my, breite, hoehe);
            Quaternionf drehen = new Quaternionf().rotateZ((float) Math.PI)
                    .mul(new Quaternionf().rotateY((float) Math.sin(t * 0.5f) * 0.18f + (mx - cx) / breite * 0.5f));
            int x0 = Math.round(cx - groesse), x1 = Math.round(cx + groesse);
            int y0 = Math.round(fuss - groesse * 2.4f), y1 = Math.round(fuss + 4);
            Vector3f mitte = new Vector3f(0, 0.9f + (y1 - fuss) / groesse, 0);
            //#if 26
            g.entity(s, groesse, mitte, drehen, null, x0, y0, x1, y1);
            //#else
            //$ g.submitEntityRenderState(s, groesse, mitte, drehen, null, x0, y0, x1, y1);
            //#endif
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("TitelFigur", e);
        }

        com.vortex.client.gui.menu.TitelStil.bahn(g, cx, boden, groesse, t, false);
        if (!funken.isEmpty()) funken(g, cx, fuss - groesse * 0.95f, groesse, t, FARBEN.getOrDefault(funken, 0xFFB79CFF), false);
        namensschild(g, mc.getUser().getName(), untertitel(), cx, l.schildY());
    }

    /** Dunkler, weicher Fleck unter der Figur (Kreis, senkrecht gestaucht). */
    private static void schatten(GuiGraphicsExtractor g, float cx, float fuss, float groesse) {
        var p = g.pose();
        p.pushMatrix();
        try {
            p.translate(cx, fuss);
            p.scale(1f, 0.22f);
            p.translate(-cx, -fuss);
            Glatt.licht(g, cx, fuss, groesse * 0.95f, 0xB0000000);
        } finally {
            p.popMatrix();
        }
    }

    /** Was du traegst, in einer Zeile: "Nebula cape · 3 cosmetics". */
    private static String untertitel() {
        try {
            Cosmetics.Auswahl a = Cosmetics.getragen();
            int n = 0;
            for (String v : new String[] { a.cape(), a.hut(), a.partikel(), a.bandana(), a.gesicht(), a.ruecken(), a.schild(), a.hals() }) {
                if (v != null && !v.isEmpty()) n++;
            }
            if (n == 0) return "No cosmetics yet";
            String cape = a.cape();
            String teil = "";
            if (cape != null && !cape.isEmpty()) {
                CapeKunst.Design d = CapeKunst.design(cape);
                teil = "custom".equals(cape) ? "Own cape" : d != null ? d.name() + " cape" : "";
            }
            String anzahl = n == 1 ? "1 cosmetic" : n + " cosmetics";
            return teil.isEmpty() ? anzahl : teil + "  \u00b7  " + anzahl;
        } catch (Throwable e) {
            return "";
        }
    }

    /** Namensschild: runde Flaeche mit gruenem Punkt und Namen, darunter leise, was du traegst. */
    private static void namensschild(GuiGraphicsExtractor g, String name, String unter, float cx, float y) {
        if (name == null || name.isEmpty()) return;
        int w = Glatt.breite(name, Glatt.Schrift.FETT) + 20;
        float x0 = Math.round(cx - w / 2f);
        Glatt.rund(g, x0, y, w, 13, 6.5f, 0xC80A0812);
        Glatt.rahmen(g, x0, y, w, 13, 6.5f, 1, 0x26FFFFFF);
        Glatt.kreis(g, x0 + 7, y + 6.5f, 4f, 0xFF4ADE80);
        Glatt.text(g, name, x0 + 12, y + 2.5f, 0xFFFFFFFF, Glatt.Schrift.FETT);
        if (unter != null && !unter.isEmpty()) Glatt.textMitte(g, Glatt.kuerzen(unter, Math.round(cx * 2 - 8), Glatt.Schrift.NORMAL), cx, y + 16, 0xC8C4BEDA, Glatt.Schrift.NORMAL);
    }

    private static AvatarRenderState zustand(Minecraft mc, float t, float cx, float kopfY, int mx, int my, int breite, int hoehe) {
        if (!angefragt) {
            angefragt = true;
            mc.getSkinManager().get(mc.getGameProfile()).thenAccept(o -> o.ifPresent(x -> skin = x));
        }
        PlayerSkin basis = skin != null ? skin : DefaultPlayerSkin.get(mc.getGameProfile());
        Identifier cape = ActiveCape.textureId();
        if (cape != null) {
            ClientAsset.ResourceTexture c = new ClientAsset.ResourceTexture(cape, cape);
            basis = basis.with(PlayerSkin.Patch.create(Optional.empty(), Optional.of(c), Optional.of(c), Optional.empty()));
        }
        AvatarRenderState s = new AvatarRenderState();
        //#if 26.2
        s.entityType = net.minecraft.world.entity.EntityTypes.PLAYER;
        //#else
        //$ s.entityType = net.minecraft.world.entity.EntityType.PLAYER;
        //#endif
        s.id = ID;
        s.skin = basis;
        s.showHat = s.showJacket = s.showLeftPants = s.showRightPants = s.showLeftSleeve = s.showRightSleeve = true;
        s.showCape = true;
        s.lightCoords = 0xF000F0;
        s.boundingBoxHeight = 1.8f;
        s.scale = 1f;
        s.ageInTicks = t * 20f;
        s.bodyRot = 180f;
        s.yRot = Math.max(-50f, Math.min(50f, (cx - mx) / breite * 140f));
        s.xRot = Math.max(-30f, Math.min(30f, (my - kopfY) / hoehe * 70f));
        s.capeLean = 6f + (float) Math.sin(t * 1.3f) * 3f;
        s.capeFlap = (float) Math.sin(t * 2.1f) * 2f;
        return s;
    }

    /** Funken auf einer Bahn um die Figur; hinten (vor dem Zeichnen der Figur) und vorne getrennt. */
    private static void funken(GuiGraphicsExtractor g, float cx, float cy, float groesse, float t, int farbe, boolean hinten) {
        for (int i = 0; i < 10; i++) {
            double w = t * 1.1 + i * Math.PI * 2 / 10;
            boolean istHinten = Math.sin(w) < 0;
            if (istHinten != hinten) continue;
            float x = cx + (float) Math.cos(w) * groesse * 0.75f;
            float y = cy + (float) Math.sin(w * 2 + i) * groesse * 0.25f + (float) Math.sin(w) * groesse * 0.12f;
            float d = (hinten ? 2.2f : 3.2f) + (float) Math.sin(t * 3 + i) * 0.8f;
            Glatt.licht(g, x, y, d * 3f, Glatt.alpha(farbe, 0.35f));
            Glatt.kreis(g, x, y, d, Glatt.alpha(farbe, hinten ? 0.55f : 0.95f));
        }
    }
}
