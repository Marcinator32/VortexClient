package com.vortex.client.hud;

import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.Setting;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ModuleListModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Module List (seit 4.55): aktive Module als Liste, vom laengsten zum kuerzesten Namen.
 * Einstellungen: ModuleListModule.
 */
public final class ModulListe {
    private ModulListe() {}

    private static final int ZEILE = 11, RAND = 3;
    private static final int VIOLETT = 0xFF8B5CF6, BLAU = 0xFF3B82F6;

    /** Je Modul: Einblend-Wert 0..1 (weich). */
    private static final Map<Module, Float> ANZEIGE = new IdentityHashMap<>();
    private static long letzte;
    private static int breite = 60, hoehe = 12;

    /** Groesse beim letzten Zeichnen (ohne Skalierung) -- fuer den HUD-Editor. */
    public static int breite() { return breite; }
    public static int hoehe() { return hoehe; }

    private record Zeile(Module modul, String name, String info, int w, float a) {}

    /** Fuer Tests: die angezeigten Namen in Reihenfolge. */
    public static volatile List<String> zuletzt = List.of();

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        ModuleListModule m = ModuleManager.INSTANCE.get(ModuleListModule.class);
        if (m == null || !m.isEnabled() || mc.font == null) { ANZEIGE.clear(); return; }
        Font font = mc.font;
        long jetzt = System.nanoTime();
        float dt = letzte == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzte) / 1e9f);
        letzte = jetzt;
        float schritt = m.animate.get() ? 1f - (float) Math.exp(-12f * dt) : 1f;

        List<Module> alle = ModuleManager.INSTANCE.getModules();
        for (Module mod : alle) {
            boolean soll = zeigen(mod, m);
            Float a = ANZEIGE.get(mod);
            if (!soll && a == null) continue;
            float wert = a == null ? 0f : a;
            wert += ((soll ? 1f : 0f) - wert) * schritt;
            if (!soll && wert < 0.02f) ANZEIGE.remove(mod);
            else ANZEIGE.put(mod, soll && wert > 0.995f ? 1f : wert);
        }
        boolean modern = HudKarte.modern(m.look);
        final int ZEILE = modern ? 13 : ModulListe.ZEILE;
        List<Zeile> zeilen = new ArrayList<>();
        for (var e : ANZEIGE.entrySet()) {
            Module mod = e.getKey();
            String name = text(mod.getName(), m);
            String info = m.info.get() ? info(mod) : "";
            if (!info.isEmpty()) info = text(info, m);
            int w = modern ? HudKarte.breite(name, true) + (info.isEmpty() ? 0 : 4 + HudKarte.breite(info, false))
                    : font.width(name) + (info.isEmpty() ? 0 : 4 + font.width(info));
            zeilen.add(new Zeile(mod, name, info, w, e.getValue()));
        }
        switch (m.sort.getIndex()) {
            case 1 -> zeilen.sort((a, b) -> a.w() != b.w() ? Integer.compare(a.w(), b.w()) : a.name().compareTo(b.name()));
            case 2 -> zeilen.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            default -> zeilen.sort((a, b) -> a.w() != b.w() ? Integer.compare(b.w(), a.w()) : a.name().compareTo(b.name()));
        }
        List<String> namen = new ArrayList<>();
        for (Zeile z : zeilen) if (z.a() > 0.5f) namen.add(z.name());
        zuletzt = namen;

        int maxW = 0;
        float summeH = 0;
        for (Zeile z : zeilen) { maxW = Math.max(maxW, z.w()); summeH += ZEILE * z.a(); }
        int rand = modern ? 5 : RAND;
        breite = maxW + 2 * rand + 1;
        hoehe = Math.max(ZEILE, Math.round(summeH));
        if (zeilen.isEmpty()) return;

        float s = m.scale.getFloat();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float px = Math.max(0, Math.min(m.x.getInt(), sw - breite * s));
        float py = Math.max(0, Math.min(m.y.getInt(), sh - hoehe * s));
        boolean rechts = px + breite * s / 2f > sw / 2f;
        boolean unten = py + hoehe * s / 2f > sh / 2f;

        var pose = ctx.pose();
        pose.pushMatrix();
        try {
            pose.translate(px, py);
            pose.scale(s, s);
            float zeit = (System.currentTimeMillis() % 1_000_000L) / 1000f;
            float yy = unten ? hoehe : 0;
            for (int i = 0; i < zeilen.size(); i++) {
                Zeile z = zeilen.get(i);
                float a = z.a();
                float h = ZEILE * a;
                if (unten) yy -= h;
                int zw = z.w() + 2 * rand;
                float weg = (1f - a) * (zw + 4);
                float x0 = rechts ? breite - zw + weg : -weg;
                int farbe = farbe(m, z.modul(), i, zeilen.size(), zeit);
                int alpha = Math.round(255 * Math.max(0f, Math.min(1f, a)));
                int y0 = Math.round(yy), y1 = Math.round(yy + h);
                if (y1 > y0 && modern) {
                    float fa0 = Math.max(0f, Math.min(1f, a));
                    if (m.background.get()) {
                        float r = Math.min(HudKarte.radius(), 3f);
                        com.vortex.client.gui.glatt.Glatt.rund(ctx, x0, yy, zw, h, r,
                                com.vortex.client.gui.glatt.Glatt.alpha(0x14151B | (Math.round(255 * m.backgroundAlpha.getFloat() * 1.6f) << 24), fa0));
                    }
                    if (m.bar.getIndex() != 0) {
                        boolean aussen = m.bar.getIndex() == 1;
                        boolean linkeSeite = rechts != aussen;
                        float bx = linkeSeite ? x0 - 1.5f : x0 + zw + 0.5f;
                        com.vortex.client.gui.glatt.Glatt.flaeche(ctx, bx, yy + 1, bx + 1f, yy + h - 1, (alpha << 24) | (farbe & 0xFFFFFF));
                    }
                    if (a > 0.35f) {
                        int fa = Math.round(255 * Math.min(1f, (a - 0.35f) / 0.65f));
                        float ty = yy + (ZEILE - 8) / 2f;
                        HudKarte.text(ctx, z.name(), x0 + rand, ty, (fa << 24) | (farbe & 0xFFFFFF), true);
                        if (!z.info().isEmpty())
                            HudKarte.text(ctx, z.info(), x0 + rand + HudKarte.breite(z.name(), true) + 4, ty, (fa << 24) | (HudKarte.labelFarbe() & 0xFFFFFF), false);
                    }
                } else if (y1 > y0) {
                    if (m.background.get()) {
                        int ga = Math.round(255 * m.backgroundAlpha.getFloat() * a);
                        ctx.fill(Math.round(x0), y0, Math.round(x0 + zw), y1, (ga << 24) | 0x0A0812);
                    }
                    int balken = m.bar.getIndex();
                    if (balken != 0) {
                        boolean aussen = balken == 1;
                        boolean linkeSeite = rechts != aussen;
                        int bx = linkeSeite ? Math.round(x0) - 1 : Math.round(x0 + zw);
                        ctx.fill(bx, y0, bx + 1, y1, (alpha << 24) | (farbe & 0xFFFFFF));
                    }
                    if (a > 0.35f) {
                        int ty = Math.round(yy + (ZEILE - 8) / 2f + 0.5f);
                        int tx = Math.round(x0) + RAND;
                        int fa = Math.round(255 * Math.min(1f, (a - 0.35f) / 0.65f));
                        ctx.text(font, z.name(), tx, ty, (fa << 24) | (farbe & 0xFFFFFF), m.shadow.get());
                        if (!z.info().isEmpty()) {
                            ctx.text(font, z.info(), tx + font.width(z.name()) + 4, ty, (fa << 24) | 0xA8A3B8, m.shadow.get());
                        }
                    }
                }
                if (!unten) yy += h;
            }
        } finally {
            pose.popMatrix();
        }
    }

    private static boolean zeigen(Module mod, ModuleListModule m) {
        if (mod == m || !mod.isEnabled() || !mod.imMenue()) return false;
        return m.zeigt(mod.getCategory());
    }

    /** Kurze Info hinter dem Namen: die erste Auswahl-Einstellung des Moduls (z. B. "Skybox"). */
    private static String info(Module mod) {
        for (Setting st : mod.getSettings()) {
            if (st instanceof ModeSetting ms) {
                String v = ms.get();
                return v == null ? "" : v;
            }
        }
        return "";
    }

    private static String text(String s, ModuleListModule m) {
        return switch (m.textCase.getIndex()) {
            case 1 -> s.toLowerCase(Locale.ROOT);
            case 2 -> s.toUpperCase(Locale.ROOT);
            default -> s;
        };
    }

    private static int farbe(ModuleListModule m, Module mod, int i, int n, float zeit) {
        float sp = m.speed.getFloat();
        return switch (m.colorMode.getIndex()) {
            case 1 -> {
                float h = (zeit * sp * 0.15f + i * 0.045f) % 1f;
                yield 0xFF000000 | (net.minecraft.util.Mth.hsvToRgb(h, 0.6f, 1f) & 0xFFFFFF);
            }
            case 2 -> m.color.get();
            case 3 -> switch (mod.getCategory()) {
                case PVP -> 0xFFF0506E;
                case CHEATS -> 0xFFA78BFA;
                case PERFORMANCE -> 0xFF4ADE80;
                case MUSIC -> 0xFFF472B6;
                case BOTS -> 0xFFFBBF24;
                case HUD -> 0xFF60A5FA;
                default -> 0xFFCBD5E1;
            };
            default -> {
                float t = (i * 0.09f + zeit * sp * 0.35f) % 2f;
                if (t > 1f) t = 2f - t;
                yield mix(VIOLETT, BLAU, t);
            }
        };
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
