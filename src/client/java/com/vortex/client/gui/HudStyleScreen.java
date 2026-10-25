package com.vortex.client.gui;

import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.hud.HudKarte;
import com.vortex.client.hud.HudLook;
import com.vortex.client.module.modules.GlobalHudColorModule;
import com.vortex.client.module.modules.HudDesignModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD Style (seit 4.76): Look fuer alle HUD-Elemente waehlen und fein einstellen,
 * mit Live-Vorschau. Erreichbar ueber die Look-Leiste in der HUD-Spalte und den HUD Editor.
 */
public class HudStyleScreen extends Screen {
    private static final int KOPF = 40, KARTE_W = 86, KARTE_H = 44, LUECKE = 6;

    private final Screen parent;
    private record Ziel(int x, int y, int w, int h, Runnable aktion) {}
    private final List<Ziel> ziele = new ArrayList<>();
    private float lauf = 0f, laufZiel = 0f;
    private int inhaltH = 0;

    public HudStyleScreen(Screen parent) {
        super(Component.literal("HUD Style"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(this.width - 70, 10, 58, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        ziele.clear();
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        g.text(this.font, "HUD Style", 12, 12, VortexStyle.TEXT, true);
        g.text(this.font, "One look for every HUD element", 12, 24, VortexStyle.TEXT_DIM, false);
        VortexStyle.akzentLinie(g, 8, KOPF - 4, this.width - 16, 1f);
        HudDesignModule d = HudLook.design();
        GlobalHudColorModule t = HudLook.textFarbe();
        if (d == null) return;

        int vorschauW = this.width >= 470 ? 170 : 0;
        int sicht = this.height - KOPF - 4;
        float maxLauf = Math.max(0, inhaltH - sicht + 10);
        laufZiel = Math.max(0, Math.min(maxLauf, laufZiel));
        lauf += (laufZiel - lauf) * 0.35f;
        if (Math.abs(laufZiel - lauf) < 0.5f) lauf = laufZiel;
        int start = KOPF + 6 - Math.round(lauf);
        g.enableScissor(0, KOPF - 2, this.width, this.height);
        int x0 = 12, y = start;
        int breite = this.width - 24 - (vorschauW > 0 ? vorschauW + 14 : 0);
        int spalten = Math.max(1, Math.min(HudLook.LOOKS.size(), (breite + LUECKE) / (KARTE_W + LUECKE)));

        g.text(this.font, "Looks", x0, y, VortexStyle.TEXT, true);
        y += 13;
        int gew = HudLook.aktuell();
        for (int i = 0; i < HudLook.LOOKS.size(); i++) {
            HudLook.Look l = HudLook.LOOKS.get(i);
            int x = x0 + (i % spalten) * (KARTE_W + LUECKE), yy = y + (i / spalten) * (KARTE_H + LUECKE);
            boolean an = i == gew, ueber = drin(mouseX, mouseY, x, yy, KARTE_W, KARTE_H);
            g.fill(x - 1, yy - 1, x + KARTE_W + 1, yy + KARTE_H + 1, an ? VortexStyle.akzent(0.3f) : ueber ? VortexStyle.VIOLETT : VortexStyle.LINE);
            g.fill(x, yy, x + KARTE_W, yy + KARTE_H, an || ueber ? VortexStyle.HOV : VortexStyle.CARD);
            Farbthema.rohAn();
            try {
                g.fill(x + 5, yy + 5, x + KARTE_W - 5, yy + 27, 0xFF3A5A2A);
                HudLook.mini(g, l, x + 12, yy + 9, KARTE_W - 24, 14, 1f);
            } finally {
                Farbthema.rohAus();
            }
            g.text(this.font, l.name(), x + 6, yy + 32, an ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, false);
            if (an) g.fill(x + KARTE_W - 10, yy + 34, x + KARTE_W - 6, yy + 38, VortexStyle.akzent(0.4f));
            ziele.add(new Ziel(x, yy, KARTE_W, KARTE_H, () -> HudLook.anwenden(l)));
        }
        y += ((HudLook.LOOKS.size() + spalten - 1) / spalten) * (KARTE_H + LUECKE) + 6;

        g.text(this.font, "Fine tune", x0, y, VortexStyle.TEXT, true);
        if (gew < 0) g.text(this.font, "custom", x0 + this.font.width("Fine tune") + 6, y, VortexStyle.akzent(0.4f), false);
        y += 13;
        int sw = Math.min(breite, 2 * 150 + 8);
        int spW = sw >= 2 * 120 + 8 ? (sw - 8) / 2 : sw;
        int sx2 = spW == sw ? x0 : x0 + spW + 8;
        int ly = y, ry = spW == sw ? y + 5 * 16 : y;
        if (t != null) ly = farbe(g, x0, ly, spW, "Text", t.color, mouseX, mouseY, t::applyToAll);
        ly = farbe(g, x0, ly, spW, "Accent", d.accent, mouseX, mouseY, null);
        ly = farbe(g, x0, ly, spW, "Card", d.cardColor, mouseX, mouseY, null);
        ly = farbe(g, x0, ly, spW, "Labels", d.labelColor, mouseX, mouseY, null);
        zahl(g, x0, ly, spW, "Opacity", Math.round(d.opacity.getFloat() * 100) + "%", mouseX, mouseY,
                () -> d.opacity.set(d.opacity.get() - 0.06), () -> d.opacity.set(d.opacity.get() + 0.06));
        ry = zahl(g, sx2, ry, spW, "Corners", String.valueOf(d.radius.getInt()), mouseX, mouseY,
                () -> d.radius.set(d.radius.get() - 1), () -> d.radius.set(d.radius.get() + 1));
        ry = schalter(g, sx2, ry, spW, "Accent bar", d.accentBar.get(), mouseX, mouseY, () -> d.accentBar.set(!d.accentBar.get()));
        ry = schalter(g, sx2, ry, spW, "Shadow", d.shadow.get(), mouseX, mouseY, () -> d.shadow.set(!d.shadow.get()));
        ry = schalter(g, sx2, ry, spW, "Pixel font", d.font.getIndex() == 1, mouseX, mouseY, () -> d.font.setIndex(d.font.getIndex() == 1 ? 0 : 1));
        ry = schalter(g, sx2, ry, spW, "Classic look", d.style.getIndex() == 1, mouseX, mouseY, () -> { if (!d.isEnabled()) d.setEnabled(true); d.style.setIndex(d.style.getIndex() == 1 ? 0 : 1); });
        inhaltH = Math.max(ly + 16, ry) - start;

        if (vorschauW > 0) vorschau(g, this.width - 12 - vorschauW, start, vorschauW, Math.min(this.height - KOPF - 20, 150));
        g.disableScissor();
        if (maxLauf > 0) {
            int spur = sicht - 8;
            int balken = Math.max(16, Math.round(spur * sicht / (float) (inhaltH + 10)));
            int by = KOPF + 2 + Math.round((spur - balken) * (lauf / maxLauf));
            g.fill(this.width - 4, by, this.width - 2, by + balken, VortexStyle.akzent(0.5f));
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /** Live-Vorschau: drei HUD-Elemente mit den aktuellen Einstellungen auf Spielhintergrund. */
    private void vorschau(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.text(this.font, "Preview", x, y, VortexStyle.TEXT, true);
        int fy = y + 13, fh = h - 13;
        g.fill(x - 1, fy - 1, x + w + 1, fy + fh + 1, VortexStyle.LINE);
        Farbthema.rohAn();
        try {
            for (int i = 0; i < fh; i += 2) {
                float tt = i / (float) Math.max(1, fh);
                int r = (int) (0x6E + 0x30 * tt), gr = (int) (0xA4 + 0x20 * tt), b = (int) (0xE0 + 0x10 * tt);
                if (i > fh * 0.62f) { r = 0x5D; gr = 0x8A; b = 0x3A; }
                g.fill(x, fy + i, x + w, Math.min(fy + fh, fy + i + 2), 0xFF000000 | r << 16 | gr << 8 | b);
            }
            GlobalHudColorModule t = HudLook.textFarbe();
            int wert = t == null ? HudKarte.WERT : t.color.get() | 0xFF000000;
            float cy = fy + 10;
            element(g, x + 10, cy, new String[] { "144", " FPS" }, wert);
            element(g, x + 10, cy + 22, new String[] { "11", " | ", "9", " CPS" }, wert);
            element(g, x + 10, cy + 44, new String[] { "X ", "120", "  Y ", "64", "  Z ", "-31" }, wert);
        } finally {
            Farbthema.rohAus();
        }
    }

    /** Ein Beispiel-Element; Teile abwechselnd Wert/Beschriftung (bei FPS: Wert zuerst). */
    private void element(GuiGraphicsExtractor g, float x, float y, String[] teile, int wert) {
        boolean modern = HudKarte.modern();
        boolean wertZuerst = !teile[0].startsWith("X");
        if (!modern) {
            StringBuilder sb = new StringBuilder();
            for (String s : teile) sb.append(s);
            g.text(this.font, sb.toString(), Math.round(x), Math.round(y + 3), wert, true);
            return;
        }
        float w = HudKarte.PAD_X * 2 + (HudLook.design().accentBar.get() ? 2 : 0);
        for (int i = 0; i < teile.length; i++) w += HudKarte.breite(teile[i], (i % 2 == 0) == wertZuerst);
        float h = HudKarte.ZEILE + HudKarte.PAD_Y * 2 - 2;
        HudKarte.karte(g, x, y, w, h, 1f);
        float tx = x + HudKarte.PAD_X + (HudLook.design().accentBar.get() ? 2 : 0);
        for (int i = 0; i < teile.length; i++) {
            boolean istWert = (i % 2 == 0) == wertZuerst;
            HudKarte.text(g, teile[i], tx, y + HudKarte.PAD_Y, istWert ? wert : HudKarte.labelFarbe(), istWert);
            tx += HudKarte.breite(teile[i], istWert);
        }
    }

    private int farbe(GuiGraphicsExtractor g, int x, int y, int w, String name, ColorSetting c, int mx, int my, Runnable danach) {
        boolean ueber = drin(mx, my, x, y, w, 14);
        g.fill(x, y, x + w, y + 14, ueber ? VortexStyle.HOV : VortexStyle.INNER);
        g.text(this.font, name, x + 5, y + 3, VortexStyle.TEXT_DIM, false);
        Farbthema.rohAn();
        try {
            g.fill(x + w - 30, y + 3, x + w - 4, y + 11, 0xFF000000);
            for (int k = 0; k < 24; k += 2) g.fill(x + w - 29 + k, y + 4, x + w - 27 + k, y + 10, c.at(k / 23f) | 0xFF000000);
        } finally {
            Farbthema.rohAus();
        }
        ziele.add(new Ziel(x, y, w, 14, () -> this.minecraft.gui.setScreen(danach == null
                ? new ColorPickerScreen(this, c) : new ColorPickerScreen(this, c, danach))));
        return y + 16;
    }

    private int zahl(GuiGraphicsExtractor g, int x, int y, int w, String name, String wert, int mx, int my, Runnable minus, Runnable plus) {
        g.fill(x, y, x + w, y + 14, VortexStyle.INNER);
        g.text(this.font, name, x + 5, y + 3, VortexStyle.TEXT_DIM, false);
        int rx = x + w - 14;
        knopf(g, rx, y, 14, 14, "+", mx, my, plus);
        g.text(this.font, wert, rx - 5 - this.font.width(wert), y + 3, VortexStyle.TEXT, false);
        knopf(g, rx - 34 - this.font.width("000"), y, 14, 14, "-", mx, my, minus);
        return y + 16;
    }

    private int schalter(GuiGraphicsExtractor g, int x, int y, int w, String name, boolean an, int mx, int my, Runnable aktion) {
        boolean ueber = drin(mx, my, x, y, w, 14);
        g.fill(x, y, x + w, y + 14, ueber ? VortexStyle.HOV : VortexStyle.INNER);
        g.text(this.font, name, x + 5, y + 3, VortexStyle.TEXT_DIM, false);
        Glatt.rund(g, x + w - 24, y + 2, 20, 10, 5f, an ? VortexStyle.akzent(0.2f) : VortexStyle.TRACK);
        Glatt.kreis(g, an ? x + w - 9 : x + w - 19, y + 7, 7, VortexStyle.TEXT);
        ziele.add(new Ziel(x, y, w, 14, aktion));
        return y + 16;
    }

    private void knopf(GuiGraphicsExtractor g, int x, int y, int w, int h, String t, int mx, int my, Runnable aktion) {
        boolean ueber = drin(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, ueber ? VortexStyle.HOV : VortexStyle.CARD);
        g.text(this.font, t, x + (w - this.font.width(t)) / 2, y + (h - 8) / 2, VortexStyle.TEXT, false);
        ziele.add(new Ziel(x, y, w, h, aktion));
    }

    private static boolean drin(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        laufZiel -= (float) vertical * 24f;
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0 || click.y() < KOPF - 2) return false;
        for (Ziel z : new ArrayList<>(ziele)) {
            if (drin(click.x(), click.y(), z.x(), z.y(), z.w(), z.h())) { z.aktion().run(); return true; }
        }
        return false;
    }

    @Override
    public void onClose() {
        com.vortex.client.core.ConfigManager.save();
        this.minecraft.gui.setScreen(parent);
    }
}
