package com.vortex.client.hud;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HudDesignModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Karten-Aussehen der HUD-Elemente (HUD Design "Modern", seit 4.56).
 *
 * Eine Karte: dunkle runde Flaeche, weicher Schatten, links ein schmaler Akzentstreifen im
 * Vortex-Verlauf. Darin Werte hell und Beschriftungen gedimmt, in der glatten Schrift.
 */
public final class HudKarte {
    private HudKarte() {}

    /** Innenabstand links/rechts und oben/unten, Zeilenhoehe (GUI-Einheiten). */
    public static final int PAD_X = 6, PAD_Y = 4, ZEILE = 11;
    public static final int WERT = 0xFFF2F3F7;

    private static HudDesignModule m() { return ModuleManager.INSTANCE.get(HudDesignModule.class); }

    /**
     * "Look" je HUD-Element (seit 4.58): "Auto" folgt HUD Design, "Modern" = immer Karte,
     * "Classic" = immer das alte Aussehen.
     */
    public static com.vortex.client.core.setting.ModeSetting look() {
        return new com.vortex.client.core.setting.ModeSetting("Look", 0, "Auto", "Modern", "Classic");
    }

    /** Neues Aussehen fuer dieses Element? */
    public static boolean modern(com.vortex.client.core.setting.ModeSetting look) {
        if (look != null && look.getIndex() == 1) return true;
        if (look != null && look.getIndex() == 2) return false;
        return modern();
    }

    /** Neues Aussehen aktiv (HUD Design)? */
    public static boolean modern() {
        HudDesignModule m = m();
        return m != null && m.isEnabled() && m.style.getIndex() == 0;
    }

    private static boolean glatt() {
        HudDesignModule m = m();
        return m == null || m.font.getIndex() == 0;
    }

    public static int labelFarbe() {
        HudDesignModule m = m();
        return m == null ? 0xFF8E93A3 : m.labelColor.get() | 0xFF000000;
    }

    public static int akzent(float t) {
        HudDesignModule m = m();
        return m == null ? 0xFF8B5CF6 : m.accent.at(t) | 0xFF000000;
    }

    public static float radius() {
        HudDesignModule m = m();
        return m == null ? 5f : m.radius.getFloat();
    }

    /** Karte zeichnen (x, y, Breite, Hoehe in GUI-Einheiten). */
    public static void karte(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float alpha) {
        HudDesignModule m = m();
        float r = Math.min(radius(), h / 2f);
        int grund = m == null ? 0xFF14151B : m.cardColor.get();
        float deck = m == null ? 0.82f : m.opacity.getFloat();
        if (m == null || m.shadow.get()) Glatt.schatten(ctx, x, y + 1, w, h, r + 1, 3f, Glatt.alpha(0x66000000, alpha));
        Glatt.rund(ctx, x, y, w, h, r, Glatt.alpha((grund & 0xFFFFFF) | ((int) (255 * deck) << 24), alpha));
        if (m == null || m.accentBar.get()) {
            float bh = h - 2 * Math.max(2f, r * 0.6f);
            if (bh > 2) {
                int n = Math.max(1, Math.round(bh / 2f));
                for (int i = 0; i < n; i++) {
                    float y0 = y + (h - bh) / 2f + bh * i / n, y1 = y + (h - bh) / 2f + bh * (i + 1) / n;
                    Glatt.flaeche(ctx, x + 1.5f, y0, x + 2.5f, y1, Glatt.alpha(akzent((i + 0.5f) / n), alpha));
                }
            }
        }
    }

    /** Feiner senkrechter Trennstrich (Mitte cx, mittig in der Hoehe h ab y). */
    public static void trenner(GuiGraphicsExtractor ctx, float cx, float y, float h, float alpha) {
        float l = Math.min(h - 6, 9);
        Glatt.flaeche(ctx, cx, y + (h - l) / 2f, cx + 0.5f, y + (h + l) / 2f, Glatt.alpha(0x40FFFFFF, alpha));
    }

    public static int breite(String s, boolean wert) {
        if (s == null || s.isEmpty()) return 0;
        if (glatt()) return Glatt.breite(s, wert ? Glatt.Schrift.FETT : Glatt.Schrift.NORMAL);
        return Minecraft.getInstance().font.width(s);
    }

    public static void text(GuiGraphicsExtractor ctx, String s, float x, float y, int farbe, boolean wert) {
        if (s == null || s.isEmpty()) return;
        if (glatt()) Glatt.text(ctx, s, x, y, farbe, wert ? Glatt.Schrift.FETT : Glatt.Schrift.NORMAL);
        else ctx.text(Minecraft.getInstance().font, s, Math.round(x), Math.round(y), farbe, false);
    }
}
