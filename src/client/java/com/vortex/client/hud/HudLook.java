package com.vortex.client.hud;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.GlobalHudColorModule;
import com.vortex.client.module.modules.HudDesignModule;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD-Look (seit 4.76): fertige Looks fuer alle HUD-Elemente auf einen Klick -- in der
 * HUD-Spalte des Mod-Menues als kleine Leiste, im HUD-Style-Bildschirm mit allen
 * Feinheiten. Ersetzt die Module "HUD Design" und "HUD Color" im Menue; deren
 * Einstellungen bleiben dahinter erhalten (Config, Presets).
 */
public final class HudLook {

    private HudLook() {}

    public record Look(String name, boolean modern, int radius, int karte, float deckkraft,
                       boolean streifen, boolean schatten, boolean pixelSchrift, int label) {}

    public static final List<Look> LOOKS = List.of(
            new Look("Modern", true, 5, 0x14151B, 0.82f, true, true, false, 0x8E93A3),
            new Look("Glass", true, 7, 0x1C1E2A, 0.42f, false, false, false, 0xB4B8C6),
            new Look("Minimal", true, 3, 0x000000, 0.24f, false, false, false, 0xA0A4B0),
            new Look("Sharp", true, 0, 0x101014, 0.90f, true, true, true, 0x8E93A3),
            new Look("Solid", true, 9, 0x22232C, 1.0f, true, true, false, 0x9A9EAD),
            new Look("Classic", false, 5, 0x14151B, 0.82f, true, true, false, 0x8E93A3));

    public static HudDesignModule design() {
        return ModuleManager.INSTANCE.get(HudDesignModule.class);
    }

    public static GlobalHudColorModule textFarbe() {
        return ModuleManager.INSTANCE.get(GlobalHudColorModule.class);
    }

    public static void anwenden(Look l) {
        HudDesignModule m = design();
        if (m == null) return;
        if (!m.isEnabled()) m.setEnabled(true);
        m.style.setIndex(l.modern() ? 0 : 1);
        if (l.modern()) {
            m.radius.set(l.radius());
            m.cardColor.set(0xFF000000 | l.karte());
            m.opacity.set(l.deckkraft());
            m.accentBar.set(l.streifen());
            m.shadow.set(l.schatten());
            m.font.setIndex(l.pixelSchrift() ? 1 : 0);
            m.labelColor.set(0xFF000000 | l.label());
        }
        com.vortex.client.core.ConfigManager.save();
    }

    /** Welcher Look gerade gilt (Index), -1 = eigene Einstellungen. */
    public static int aktuell() {
        HudDesignModule m = design();
        if (m == null) return 0;
        boolean modern = m.isEnabled() && m.style.getIndex() == 0;
        for (int i = 0; i < LOOKS.size(); i++) {
            Look l = LOOKS.get(i);
            if (!l.modern()) { if (!modern) return i; continue; }
            if (!modern) continue;
            if (m.radius.getInt() == l.radius() && (m.cardColor.get() & 0xFFFFFF) == l.karte()
                    && Math.abs(m.opacity.getFloat() - l.deckkraft()) < 0.011f && m.accentBar.get() == l.streifen()
                    && m.shadow.get() == l.schatten() && (m.font.getIndex() == 1) == l.pixelSchrift()) return i;
        }
        return -1;
    }

    /** Mini-Karte eines Looks (fuer die Leiste und die Karten im Style-Bildschirm). */
    public static void mini(GuiGraphicsExtractor ctx, Look l, float x, float y, float w, float h, float alpha) {
        HudDesignModule m = design();
        int akzent0 = m == null ? 0xFF8B5CF6 : m.accent.at(0f) | 0xFF000000;
        int akzent1 = m == null ? 0xFF3B82F6 : m.accent.at(1f) | 0xFF000000;
        if (!l.modern()) {
            ctx.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), Glatt.alpha(0x90000000, alpha));
            float tx = x + 3, ty = y + h / 2f - 1;
            ctx.fill(Math.round(tx), Math.round(ty), Math.round(tx + w * 0.55f), Math.round(ty + 2), Glatt.alpha(0xFFFFFFFF, alpha));
            return;
        }
        float r = Math.min(l.radius() * h / 14f, h / 2f);
        if (l.schatten()) Glatt.schatten(ctx, x, y + 0.5f, w, h, r + 0.5f, 1.5f, Glatt.alpha(0x66000000, alpha));
        Glatt.rund(ctx, x, y, w, h, r, Glatt.alpha(l.karte() | ((int) (255 * l.deckkraft()) << 24), alpha));
        if (l.streifen()) {
            float sy = y + Math.max(1.5f, r * 0.5f), sh = h - 2 * Math.max(1.5f, r * 0.5f);
            Glatt.flaeche(ctx, x + 1f, sy, x + 2f, sy + sh / 2f, Glatt.alpha(akzent0, alpha));
            Glatt.flaeche(ctx, x + 1f, sy + sh / 2f, x + 2f, sy + sh, Glatt.alpha(akzent1, alpha));
        }
        float tx = x + (l.streifen() ? 4f : 3f), ty = y + h / 2f - 1f;
        Glatt.flaeche(ctx, tx, ty, tx + w * 0.38f, ty + 2f, Glatt.alpha(0xFFF2F3F7, alpha));
        Glatt.flaeche(ctx, tx + w * 0.42f, ty, tx + w * 0.62f, ty + 2f, Glatt.alpha(0xFF000000 | l.label(), alpha));
    }
}
