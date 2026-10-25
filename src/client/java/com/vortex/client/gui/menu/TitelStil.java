package com.vortex.client.gui.menu;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Zeichnen im Hauptmenue (seit 4.84): Grund, die schlanke Leiste unten und die Umlaufbahn
 * unter der Figur. Farben in Vortex-Lila/Blau -- das Client-Design rechnet sie um.
 */
public final class TitelStil {
    private TitelStil() {}

    /** Anfang und Ende des Vortex-Verlaufs. */
    public static final int A = 0xFF8B5CF6, B = 0xFF3B82F6;

    /** Grund ueber dem Panorama, links hinter der Figur ein Schein, unten die Leiste. */
    public static void grund(GuiGraphicsExtractor g, int breite, int hoehe) {
        MenuStil.titelGrund(g, breite, hoehe);
        MenuLeiste.Lage l = MenuLeiste.lage();
        if (l == null) return;
        if (l.groesse() > 0) {
            int bis = Math.round(l.figurX() * 2f + 10);
            MenuStil.verlaufW(g, 0, 0, Math.min(breite, bis), hoehe, 0x7006050C, 0x0006050C);
        }
        float x = l.leisteX(), y = l.leisteY(), w = l.leisteB(), h = l.leisteH();
        Glatt.schatten(g, x, y, w, h, h / 2f, 6, 0x50000000);
        Glatt.rund(g, x, y, w, h, h / 2f, 0xB00A0812);
        Glatt.rahmen(g, x, y, w, h, h / 2f, 1, 0x22FFFFFF);
    }

    /**
     * Umlaufbahn um die Fuesse: zwei gepunktete Ellipsen und drei helle "Monde".
     * hinten = obere Haelfte (vor der Figur zeichnen), sonst die untere.
     */
    public static void bahn(GuiGraphicsExtractor g, float cx, float fuss, float groesse, float t, boolean hinten) {
        ring(g, cx, fuss, groesse * 1.75f, 64, 0.30f, 0.75f, t * 0.12f, hinten);
        ring(g, cx, fuss, groesse * 1.15f, 48, 0.60f, 0.95f, -t * 0.2f, hinten);
        for (int i = 0; i < 3; i++) {
            double w = t * 0.6 + i * 2.1;
            boolean oben = Math.sin(w) < 0;
            if (oben != hinten) continue;
            float x = cx + (float) Math.cos(w) * groesse * 1.15f;
            float y = fuss + (float) Math.sin(w) * groesse * 1.15f * 0.26f;
            int f = i == 1 ? B : A;
            Glatt.licht(g, x, y, 7f, Glatt.alpha(f, hinten ? 0.25f : 0.45f));
            Glatt.kreis(g, x, y, hinten ? 2.4f : 3.2f, Glatt.alpha(Glatt.mix(f, 0xFFFFFFFF, 0.35f), hinten ? 0.7f : 1f));
        }
    }

    private static void ring(GuiGraphicsExtractor g, float cx, float fuss, float rx, int n, float staerke, float d, float dreh, boolean hinten) {
        for (int i = 0; i < n; i++) {
            double w = dreh + i * Math.PI * 2 / n;
            float s = (float) Math.sin(w);
            if ((s < 0) != hinten) continue;
            float a = staerke * (0.55f + 0.45f * (s + 1f) / 2f);
            Glatt.kreis(g, cx + (float) Math.cos(w) * rx, fuss + s * rx * 0.26f, d, Glatt.alpha(i % 2 == 0 ? A : B, a));
        }
    }
}
