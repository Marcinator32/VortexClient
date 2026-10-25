package com.vortex.client.gui.glatt;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * Formen fuer den Neon-Stil (seit 4.40): kantige Flaechen mit abgeschraegten
 * Ecken (oben links und unten rechts), leuchtende Raender, Verlaeufe und
 * Bildschirmlinien.
 *
 * Gezeichnet wird -- wie in {@link Glatt} -- in ECHTEN Bildschirmpixeln, damit
 * die Schraegen bei jeder GUI-Groesse eine saubere 1-Pixel-Treppe sind und
 * nicht in groben GUI-Stufen springen.
 */
public final class Neon {

    private Neon() {}

    private static int px(float v, int s) {
        return Math.round(v * s);
    }

    private static Matrix3x2fStack pixel(GuiGraphicsExtractor ctx, int s) {
        Matrix3x2fStack p = ctx.pose();
        p.pushMatrix();
        p.scale(1f / s, 1f / s);
        return p;
    }

    /** Flaeche mit abgeschraegter Ecke oben links und unten rechts (schnitt in GUI-Einheiten). */
    public static void kantig(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float schnitt, int farbe) {
        if ((farbe >>> 24) == 0 || w <= 0 || h <= 0) return;
        int s = Glatt.skala();
        int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
        int c = Math.max(0, Math.min(px(schnitt, s), Math.min(x1 - x0, y1 - y0) / 2));
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            if (c == 0) { ctx.fill(x0, y0, x1, y1, farbe); return; }
            for (int i = 0; i < c; i++) ctx.fill(x0 + (c - i), y0 + i, x1, y0 + i + 1, farbe);
            ctx.fill(x0, y0 + c, x1, y1 - c, farbe);
            for (int j = 0; j < c; j++) ctx.fill(x0, y1 - c + j, x1 - (j + 1), y1 - c + j + 1, farbe);
        } finally {
            p.popMatrix();
        }
    }

    /** Rand derselben Form; dicke in echten Pixeln, aussen um "aus" echte Pixel vergroessert. */
    public static void rahmen(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float schnitt, int dicke, int aus, int farbe) {
        if ((farbe >>> 24) == 0 || w <= 0 || h <= 0) return;
        int s = Glatt.skala();
        int x0 = px(x, s) - aus, y0 = px(y, s) - aus, x1 = px(x + w, s) + aus, y1 = px(y + h, s) + aus;
        int c = Math.max(0, Math.min(px(schnitt, s) + aus / 2, Math.min(x1 - x0, y1 - y0) / 2));
        int t = Math.max(1, dicke);
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            ctx.fill(x0 + c, y0, x1, y0 + t, farbe);
            ctx.fill(x0, y1 - t, x1 - c, y1, farbe);
            ctx.fill(x0, y0 + c, x0 + t, y1, farbe);
            ctx.fill(x1 - t, y0, x1, y1 - c, farbe);
            for (int i = 0; i < c; i++) {
                ctx.fill(x0 + c - i - 1, y0 + i, x0 + c - i - 1 + t, y0 + i + t, farbe);
                ctx.fill(x1 - c + i, y1 - i - t, x1 - c + i + t, y1 - i, farbe);
            }
        } finally {
            p.popMatrix();
        }
    }

    /** Leuchtender Rand: scharfe Linie plus nach aussen auslaufender Schein. */
    public static void leuchten(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float schnitt, int farbe, float staerke) {
        if (staerke <= 0.01f) return;
        int s = Glatt.skala();
        int weite = Math.max(2, Math.round(3.5f * s * staerke));
        for (int e = weite; e >= 1; e--) {
            float f = 1f - e / (float) (weite + 1);
            rahmen(ctx, x, y, w, h, schnitt, 1, e, Glatt.alpha(farbe, 0.22f * f * f * staerke));
        }
        rahmen(ctx, x, y, w, h, schnitt, Math.max(1, s / 2), 0, Glatt.alpha(farbe, Math.min(1f, 0.55f + 0.45f * staerke)));
    }

    /** Waagerechter Verlauf von links (a) nach rechts (b). */
    public static void verlauf(GuiGraphicsExtractor ctx, float x, float y, float w, float h, int a, int b) {
        int s = Glatt.skala();
        int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
        if (x1 <= x0 || y1 <= y0) return;
        int schritt = Math.max(1, (x1 - x0) / 96);
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            for (int xx = x0; xx < x1; xx += schritt) {
                float t = (xx - x0) / (float) Math.max(1, x1 - x0 - 1);
                ctx.fill(xx, y0, Math.min(x1, xx + schritt), y1, Glatt.mix(a, b, t));
            }
        } finally {
            p.popMatrix();
        }
    }

    /** Feine waagerechte Bildschirmlinien ueber die ganze Flaeche. */
    public static void scanlines(GuiGraphicsExtractor ctx, int breite, int hoehe, int farbe) {
        int s = Glatt.skala();
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            int w = breite * s, h = hoehe * s;
            for (int y = 0; y < h; y += 3) ctx.fill(0, y, w, y + 1, farbe);
        } finally {
            p.popMatrix();
        }
    }

    /**
     * Leuchtendes Bodenraster in Perspektive (Synthwave): waagerechte Linien,
     * die zum Horizont dichter werden, und Fluchtlinien als Punktreihen.
     */
    public static void boden(GuiGraphicsExtractor ctx, int breite, int hoehe, float horizont, int farbe, float zeit) {
        int s = Glatt.skala();
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            int w = breite * s, h = hoehe * s, hy = Math.round(horizont * s);
            int tiefe = h - hy;
            if (tiefe <= 4) return;
            float lauf = (zeit * 0.35f) % 1f;
            for (int i = 0; i < 14; i++) {
                float t = (i + lauf) / 14f;
                float z = t * t;
                int y = hy + Math.round(z * tiefe);
                float a = Math.min(1f, z * 1.6f);
                ctx.fill(0, y, w, y + Math.max(1, s / 2), Glatt.alpha(farbe, 0.30f * a));
            }
            int mitte = w / 2;
            for (int k = -10; k <= 10; k++) {
                for (int y = hy; y < h; y += Math.max(2, s)) {
                    float t = (y - hy) / (float) tiefe;
                    int x = mitte + Math.round(k * t * w / 6f);
                    if (x < 0 || x >= w) continue;
                    ctx.fill(x, y, x + Math.max(1, s / 2), y + Math.max(1, s / 2), Glatt.alpha(farbe, 0.28f * t));
                }
            }
        } finally {
            p.popMatrix();
        }
    }
}
