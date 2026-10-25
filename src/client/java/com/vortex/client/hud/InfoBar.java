package com.vortex.client.hud;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.InfoBarModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Info Bar (seit 4.56): Logo | 144 FPS | 23 ms | 20.0 TPS | 20:14 in einer Karte. */
public final class InfoBar {
    private InfoBar() {}

    public static final int HOEHE = 21;
    private static int breite = 120;

    public static int breite() { return breite; }

    private record Teil(String wert, String einheit) {}

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        InfoBarModule m = ModuleManager.INSTANCE.get(InfoBarModule.class);
        if (m == null || !m.isEnabled()) return;
        List<Teil> teile = new ArrayList<>(4);
        if (m.fps.get()) teile.add(new Teil(String.valueOf(mc.getFps()), "FPS"));
        if (m.ping.get() && mc.player != null && mc.getConnection() != null) teile.add(new Teil(String.valueOf(ping(mc)), "ms"));
        if (m.tps.get() && mc.player != null) teile.add(new Teil(String.format(java.util.Locale.ROOT, "%.1f", TickRate.tps()), "TPS"));
        if (m.clock.get()) {
            java.time.LocalTime t = java.time.LocalTime.now();
            if (m.clockFormat.getIndex() == 1) {
                int hh = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
                teile.add(new Teil(String.format(java.util.Locale.ROOT, "%d:%02d", hh, t.getMinute()), t.getHour() < 12 ? "AM" : "PM"));
            } else teile.add(new Teil(String.format(java.util.Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute()), null));
        }
        if (teile.isEmpty() && !m.logo.get()) return;

        final int luecke = 3, trennung = 9, logoG = 12;
        int w = HudKarte.PAD_X + 2;
        if (m.logo.get()) w += logoG + (teile.isEmpty() ? 0 : trennung);
        for (int i = 0; i < teile.size(); i++) {
            Teil t = teile.get(i);
            w += HudKarte.breite(t.wert(), true) + (t.einheit() == null ? 0 : luecke + HudKarte.breite(t.einheit(), false));
            if (i < teile.size() - 1) w += trennung;
        }
        w += HudKarte.PAD_X;
        breite = w;
        int x = m.x.getInt(), y = m.y.getInt();
        float s = m.scale.getFloat();
        HudRenderer.pushScale(ctx, x, y, s);
        try {
            HudKarte.karte(ctx, x, y, w, HOEHE, 1f);
            float cx = x + HudKarte.PAD_X + 2;
            float ty = y + (HOEHE - 8) / 2f;
            if (m.logo.get()) {
                Glatt.logo(ctx, cx, y + (HOEHE - logoG) / 2f, logoG, 1f, 1f);
                cx += logoG;
                if (!teile.isEmpty()) { HudKarte.trenner(ctx, cx + trennung / 2f, y, HOEHE, 1f); cx += trennung; }
            }
            int wert = m.color.get() | 0xFF000000, label = HudKarte.labelFarbe();
            for (int i = 0; i < teile.size(); i++) {
                Teil t = teile.get(i);
                HudKarte.text(ctx, t.wert(), cx, ty, wert, true);
                cx += HudKarte.breite(t.wert(), true);
                if (t.einheit() != null) {
                    cx += luecke;
                    HudKarte.text(ctx, t.einheit(), cx, ty, label, false);
                    cx += HudKarte.breite(t.einheit(), false);
                }
                if (i < teile.size() - 1) { HudKarte.trenner(ctx, cx + trennung / 2f, y, HOEHE, 1f); cx += trennung; }
            }
        } finally {
            HudRenderer.popScale(ctx);
        }
    }

    /** Ping wie das Ping-Element: eigene Messung, wenn frisch, sonst die Zahl des Servers. */
    static int ping(Minecraft mc) {
        int gemessen = PingMeter.get();
        if (gemessen >= 0 && PingMeter.age() < 15_000L) return gemessen;
        try {
            var e = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return e == null ? 0 : e.getLatency();
        } catch (Throwable t) {
            return 0;
        }
    }
}
