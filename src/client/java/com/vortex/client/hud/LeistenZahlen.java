package com.vortex.client.hud;

import com.vortex.client.styles.Farbe;
import com.vortex.client.styles.Kategorie;
import com.vortex.client.styles.Styles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Zahlen neben den Leisten (Styles, Option "Number", seit 4.92): Leben links neben den
 * Herzen (Absorption in Gold dahinter), Ruestung links neben der Ruestungsleiste, Hunger
 * rechts neben den Keulen, beim XP der Fortschritt bis zum naechsten Level (Prozent oder
 * Punkte) rechts neben der Level-Zahl. Farbe = die im Style gewaehlte Farbe, sonst die
 * Farbe der Leiste.
 *
 * Positionen wie in Minecrafts HUD (Hotbar 182 breit, Herzen und Keulen 39 ueber dem
 * unteren Rand, mehrere Herzreihen rutschen zusammen).
 */
public final class LeistenZahlen {
    private LeistenZahlen() {}

    private static int farbe(Kategorie k, int standard) {
        String g = Styles.gewaehlt(k);
        if (g != null && g.startsWith("gen:")) {
            Farbe f = Farbe.von(g.substring(4));
            if (f != null) return 0xFF000000 | f.bei(0.35f);
        }
        return standard;
    }

    private static boolean an(Kategorie k) {
        String v = Styles.effekt(k, "number");
        return v != null && !"off".equals(v);
    }

    public static void render(GuiGraphicsExtractor g, Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || p.isSpectator()) return;
        boolean herz = an(Kategorie.HEARTS), hunger = an(Kategorie.HUNGER), ruestung = an(Kategorie.ARMOR_BAR), xp = an(Kategorie.XP_BAR);
        if (!herz && !hunger && !ruestung && !xp) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int links = w / 2 - 91, rechts = w / 2 + 91, zeile = h - 39;
        var font = mc.font;

        if (mc.gameMode.canHurtPlayer()) {
            float max = Math.max(p.getMaxHealth(), p.getHealth());
            int absorption = (int) Math.ceil(p.getAbsorptionAmount());
            int reihen = (int) Math.ceil((max + absorption) / 2f / 10f);
            int reihenH = Math.max(10 - (reihen - 2), 3);
            if (herz) {
                String leben = String.valueOf((int) Math.ceil(p.getHealth()));
                String extra = absorption > 0 ? "+" + absorption : "";
                int bw = font.width(leben) + font.width(extra);
                int x = links - 3 - bw;
                g.text(font, leben, x, zeile + 1, farbe(Kategorie.HEARTS, 0xFFFF5A5A), true);
                if (!extra.isEmpty()) g.text(font, extra, x + font.width(leben), zeile + 1, 0xFFFFD24A, true);
            }
            int ruestungWert = p.getArmorValue();
            if (ruestung && ruestungWert > 0) {
                int y = zeile - (reihen - 1) * reihenH - 10;
                String t = String.valueOf(ruestungWert);
                g.text(font, t, links - 3 - font.width(t), y + 1, farbe(Kategorie.ARMOR_BAR, 0xFFD8DCE4), true);
            }
            boolean reittier = p.getVehicle() instanceof LivingEntity le && le.showVehicleHealth();
            if (hunger && !reittier) {
                String t = String.valueOf(p.getFoodData().getFoodLevel());
                g.text(font, t, rechts + 3, zeile + 1, farbe(Kategorie.HUNGER, 0xFFE8A04A), true);
            }
        }

        if (xp && mc.gameMode.hasExperience()) {
            String modus = Styles.effekt(Kategorie.XP_BAR, "number");
            String t;
            if ("points".equals(modus)) {
                int fehlt = Math.max(0, Math.round(p.getXpNeededForNextLevel() * (1f - p.experienceProgress)));
                t = fehlt + " to go";
            } else {
                t = (int) Math.floor(p.experienceProgress * 100f) + "%";
            }
            String level = p.experienceLevel > 0 ? String.valueOf(p.experienceLevel) : "";
            int y = h - 24 - 9 - 2;
            int x = level.isEmpty() ? (w - font.width(t)) / 2 : (w + font.width(level)) / 2 + 4;
            g.text(font, t, x, y, farbe(Kategorie.XP_BAR, 0xFF80FF20), true);
        }
    }
}
