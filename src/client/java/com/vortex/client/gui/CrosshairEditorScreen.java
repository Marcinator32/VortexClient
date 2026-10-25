package com.vortex.client.gui;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.hud.CrosshairRenderer;
import com.vortex.client.hud.EigenesFadenkreuz;
import com.vortex.client.module.modules.CrosshairModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Crosshair-Editor (seit 4.75): links ein 15 x 15 Raster zum Selbermalen (Stil "Custom"),
 * in der Mitte eine grosse Vorschau auf waehlbarem Hintergrund (plus echte Groesse),
 * rechts die wichtigsten Einstellungen, unten Vorlagen und Teilen-Codes.
 */
public class CrosshairEditorScreen extends Screen {
    private static final int KOPF = 40;
    private static final String[] HINTERGRUENDE = { "Sky", "Grass", "Stone", "Night" };

    private final Screen parent;
    private final CrosshairModule m;
    private record Ziel(int x, int y, int w, int h, Runnable aktion) {}
    private final List<Ziel> ziele = new ArrayList<>();

    private int werkzeug = 1;
    private boolean spiegeln = true;
    private int hintergrund = 0;
    private long trefferSeit = 0;
    private String meldung = "";
    private long meldungSeit = 0;
    private int gx, gy, zelle;
    private int malWert = -1;

    public CrosshairEditorScreen(Screen parent, CrosshairModule m) {
        super(Component.literal("Crosshair Editor"));
        this.parent = parent;
        this.m = m;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(this.width - 70, 10, 58, 20).build());
        if (!m.isEnabled()) m.toggle();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        ziele.clear();
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        g.text(this.font, "Crosshair Editor", 12, 12, VortexStyle.TEXT, true);
        g.text(this.font, "Draw your own, pick a preset or share a code", 12, 24, VortexStyle.TEXT_DIM, false);
        VortexStyle.akzentLinie(g, 8, KOPF - 4, this.width - 16, 1f);

        int unten = this.height - 58;
        int hoehe = unten - KOPF - 24;
        zelle = Math.max(5, Math.min(11, (hoehe - 40) / EigenesFadenkreuz.GROESSE));
        int rasterW = zelle * EigenesFadenkreuz.GROESSE;
        int rechtsW = 150;
        int x0 = 12, y0 = KOPF + 6;

        malbereich(g, x0, y0, rasterW, mouseX, mouseY);
        int mx = x0 + rasterW + 18;
        int mw = Math.max(110, this.width - mx - rechtsW - 24);
        vorschau(g, mx, y0, mw, hoehe + 10, mouseX, mouseY);
        int rechtsX = mx + mw + 12;
        einstellungen(g, rechtsX, y0, rechtsW, mouseX, mouseY);
        vorlagen(g, 12, unten, rechtsX - 8, mouseX, mouseY);

        if (!meldung.isEmpty() && System.currentTimeMillis() - meldungSeit < 3500) {
            g.text(this.font, meldung, this.width - 12 - this.font.width(meldung), this.height - 12, VortexStyle.akzent(0.4f), false);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void malbereich(GuiGraphicsExtractor g, int x0, int y0, int rasterW, int mx, int my) {
        g.text(this.font, "Draw", x0, y0, VortexStyle.TEXT, true);
        boolean eigen = m.style.getIndex() == 9;
        String info = eigen ? "in use" : "click to use";
        g.text(this.font, info, x0 + this.font.width("Draw") + 6, y0, eigen ? VortexStyle.akzent(0.4f) : VortexStyle.TEXT_DIM, false);
        gx = x0;
        gy = y0 + 13;
        int n = EigenesFadenkreuz.GROESSE;
        g.fill(gx - 1, gy - 1, gx + rasterW + 1, gy + rasterW + 1, VortexStyle.LINE);
        byte[] px = EigenesFadenkreuz.pixel();
        Farbthema.rohAn();
        try {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int cx = gx + x * zelle, cy = gy + y * zelle;
                    boolean mitte = x == n / 2 || y == n / 2;
                    int grund = ((x + y) % 2 == 0) ? 0xFF2A2730 : 0xFF24212A;
                    if (mitte) grund = 0xFF332F3B;
                    int w = px[y * n + x];
                    int c = w == 1 ? (m.color.get() | 0xFF000000) : w == 2 ? (m.outlineColor.get() | 0xFF000000) : grund;
                    g.fill(cx, cy, cx + zelle - (zelle > 6 ? 1 : 0), cy + zelle - (zelle > 6 ? 1 : 0), c);
                }
            }
        } finally {
            Farbthema.rohAus();
        }
        if (drin(mx, my, gx, gy, rasterW, rasterW)) {
            int hx = (mx - gx) / zelle, hy = (my - gy) / zelle;
            g.fill(gx + hx * zelle, gy + hy * zelle, gx + hx * zelle + zelle - 1, gy + hy * zelle + 1, VortexStyle.TEXT);
        }

        int y = gy + rasterW + 6;
        int x = x0;
        x = chip(g, x, y, "Color", werkzeug == 1, mx, my, () -> werkzeug = 1);
        x = chip(g, x, y, "Edge", werkzeug == 2, mx, my, () -> werkzeug = 2);
        chip(g, x, y, "Erase", werkzeug == 0, mx, my, () -> werkzeug = 0);
        y += 18;
        x = x0;
        x = chip(g, x, y, "Mirror", spiegeln, mx, my, () -> spiegeln = !spiegeln);
        x = chip(g, x, y, "Clear", false, mx, my, () -> { EigenesFadenkreuz.alles(new byte[n * n]); nimmEigenes(); });
        chip(g, x, y, "Invert", false, mx, my, () -> {
            byte[] p = EigenesFadenkreuz.pixel().clone();
            for (int i = 0; i < p.length; i++) p[i] = (byte) (p[i] == 0 ? 1 : 0);
            EigenesFadenkreuz.alles(p);
            nimmEigenes();
        });
        if (y + 30 < this.height - 62) g.text(this.font, "Right click erases", x0, y + 20, VortexStyle.fade(VortexStyle.TEXT_DIM, 0.8f), false);
    }

    private void vorschau(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        g.text(this.font, "Preview", x, y, VortexStyle.TEXT, true);
        int bx = x + this.font.width("Preview") + 8;
        for (int i = 0; i < HINTERGRUENDE.length; i++) {
            final int nr = i;
            bx = chip(g, bx, y - 3, HINTERGRUENDE[i], hintergrund == i, mx, my, () -> hintergrund = nr);
        }
        int fy = y + 14, fh = h - 30;
        g.fill(x - 1, fy - 1, x + w + 1, fy + fh + 1, VortexStyle.LINE);
        Farbthema.rohAn();
        try {
            hintergrund(g, x, fy, w, fh);
            float treffer = Math.max(0f, 1f - (System.currentTimeMillis() - trefferSeit) / 350f);
            int sk = Math.max(2, Math.min(5, Math.min(w, fh) / 34));
            var p = g.pose();
            p.pushMatrix();
            p.translate(x + w / 2f, fy + fh / 2f);
            p.scale(sk, sk);
            CrosshairRenderer.vorschau(g, m, 0, 0, treffer);
            p.popMatrix();
            int kx = x + w - 30, ky = fy + fh - 30;
            g.fill(kx, ky, kx + 26, ky + 26, 0x40000000);
            CrosshairRenderer.vorschau(g, m, kx + 13, ky + 13, treffer);
        } finally {
            Farbthema.rohAus();
        }
        g.text(this.font, "1:1", x + w - 30, fy + fh - 40, VortexStyle.TEXT_DIM, false);
        chip(g, x, fy + fh + 4, "Test hit", false, mx, my, () -> trefferSeit = System.currentTimeMillis());
    }

    private void hintergrund(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        switch (hintergrund) {
            case 1 -> {
                g.fill(x, y, x + w, y + h, 0xFF5D8A3A);
                for (int yy = y; yy < y + h; yy += 6) {
                    for (int xx = x + ((yy - y) / 6 % 2) * 3; xx < x + w; xx += 6) g.fill(xx, yy, Math.min(x + w, xx + 3), Math.min(y + h, yy + 3), 0xFF4F7A30);
                }
            }
            case 2 -> {
                g.fill(x, y, x + w, y + h, 0xFF7D7D7D);
                for (int yy = y; yy < y + h; yy += 5) {
                    for (int xx = x + ((yy - y) / 5 % 3) * 2; xx < x + w; xx += 7) g.fill(xx, yy, Math.min(x + w, xx + 2), Math.min(y + h, yy + 2), 0xFF6A6A6A);
                }
            }
            case 3 -> g.fill(x, y, x + w, y + h, 0xFF0D0E1A);
            default -> {
                for (int i = 0; i < h; i += 2) {
                    float t = i / (float) Math.max(1, h);
                    int r = (int) (0x78 + (0xC8 - 0x78) * t), gr = (int) (0xA8 + (0xDC - 0xA8) * t), b = (int) (0xE8 + (0xF6 - 0xE8) * t);
                    g.fill(x, y + i, x + w, Math.min(y + h, y + i + 2), 0xFF000000 | r << 16 | gr << 8 | b);
                }
            }
        }
    }

    private void einstellungen(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
        g.text(this.font, "Shape", x, y, VortexStyle.TEXT, true);
        y += 13;
        g.fill(x, y, x + w, y + 16, VortexStyle.CARD);
        g.text(this.font, m.style.get(), x + 18, y + 4, VortexStyle.TEXT, false);
        knopf(g, x, y, 14, 16, "<", mx, my, () -> m.style.setIndex((m.style.getIndex() + m.style.size() - 1) % m.style.size()));
        knopf(g, x + w - 14, y, 14, 16, ">", mx, my, () -> m.style.cycle());
        y += 20;
        y = zahl(g, x, y, w, m.size, mx, my);
        y = zahl(g, x, y, w, m.thickness, mx, my);
        y = zahl(g, x, y, w, m.gap, mx, my);
        y = zahl(g, x, y, w, m.dotSize, mx, my);
        y = zahl(g, x, y, w, m.customScale, mx, my);
        y += 4;
        g.text(this.font, "Colors", x, y, VortexStyle.TEXT, true);
        y += 13;
        y = farbe(g, x, y, w, "Color", m.color, mx, my);
        y = farbe(g, x, y, w, "Edge", m.outlineColor, mx, my);
        y = schalter(g, x, y, w, m.outline, "Auto edge", mx, my);
        y = schalter(g, x, y, w, m.rainbow, "Rainbow", mx, my);
        schalter(g, x, y, w, m.hitMarker, "Hit marker", mx, my);
    }

    private void vorlagen(GuiGraphicsExtractor g, int x, int y, int bis, int mx, int my) {
        g.text(this.font, "Presets", x, y, VortexStyle.TEXT, true);
        int start = x + this.font.width("Presets") + 8;
        int bx = start, zy = y;
        for (EigenesFadenkreuz.Vorlage v : EigenesFadenkreuz.VORLAGEN) {
            if (bx + this.font.width(v.name()) + 10 > bis && bx > start) { bx = start; zy += 16; }
            bx = chip(g, bx, zy - 3, v.name(), false, mx, my, () -> { EigenesFadenkreuz.anwenden(v, m); melden("Preset: " + v.name()); });
        }
        int cy = zy + 18;
        g.text(this.font, "Share", x, cy, VortexStyle.TEXT, true);
        int cx = x + this.font.width("Presets") + 8;
        cx = chip(g, cx, cy - 3, "Copy code", false, mx, my, () -> {
            this.minecraft.keyboardHandler.setClipboard(EigenesFadenkreuz.code(m));
            melden("Code copied");
        });
        chip(g, cx, cy - 3, "Paste code", false, mx, my, () -> {
            boolean ok = EigenesFadenkreuz.ausCode(this.minecraft.keyboardHandler.getClipboard(), m);
            melden(ok ? "Crosshair loaded" : "No crosshair code in the clipboard");
        });
    }

    private int zahl(GuiGraphicsExtractor g, int x, int y, int w, NumberSetting s, int mx, int my) {
        g.fill(x, y, x + w, y + 15, VortexStyle.INNER);
        g.text(this.font, s.getName(), x + 5, y + 4, VortexStyle.TEXT_DIM, false);
        String wert = String.valueOf(s.getInt());
        int rx = x + w - 14;
        knopf(g, rx, y, 14, 15, "+", mx, my, () -> s.set(s.get() + s.getStep()));
        g.text(this.font, wert, rx - 6 - this.font.width(wert), y + 4, VortexStyle.TEXT, false);
        knopf(g, rx - 26 - this.font.width("00"), y, 14, 15, "-", mx, my, () -> s.set(s.get() - s.getStep()));
        return y + 16;
    }

    private int farbe(GuiGraphicsExtractor g, int x, int y, int w, String name, ColorSetting c, int mx, int my) {
        boolean ueber = drin(mx, my, x, y, w, 15);
        g.fill(x, y, x + w, y + 15, ueber ? VortexStyle.HOV : VortexStyle.INNER);
        g.text(this.font, name, x + 5, y + 4, VortexStyle.TEXT_DIM, false);
        Farbthema.rohAn();
        try {
            g.fill(x + w - 22, y + 3, x + w - 4, y + 12, 0xFF000000);
            g.fill(x + w - 21, y + 4, x + w - 5, y + 11, c.get() | 0xFF000000);
        } finally {
            Farbthema.rohAus();
        }
        ziele.add(new Ziel(x, y, w, 15, () -> this.minecraft.gui.setScreen(new ColorPickerScreen(this, c))));
        return y + 16;
    }

    private int schalter(GuiGraphicsExtractor g, int x, int y, int w, BooleanSetting b, String name, int mx, int my) {
        boolean an = b.get();
        boolean ueber = drin(mx, my, x, y, w, 15);
        g.fill(x, y, x + w, y + 15, ueber ? VortexStyle.HOV : VortexStyle.INNER);
        g.text(this.font, name, x + 5, y + 4, VortexStyle.TEXT_DIM, false);
        Glatt.rund(g, x + w - 24, y + 2, 20, 11, 5.5f, an ? VortexStyle.akzent(0.2f) : VortexStyle.TRACK);
        Glatt.kreis(g, an ? x + w - 9.5f : x + w - 18.5f, y + 7.5f, 8, VortexStyle.TEXT);
        ziele.add(new Ziel(x, y, w, 15, () -> b.set(!b.get())));
        return y + 16;
    }

    private void knopf(GuiGraphicsExtractor g, int x, int y, int w, int h, String t, int mx, int my, Runnable aktion) {
        boolean ueber = drin(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, ueber ? VortexStyle.HOV : VortexStyle.CARD);
        g.text(this.font, t, x + (w - this.font.width(t)) / 2, y + (h - 8) / 2, VortexStyle.TEXT, false);
        ziele.add(new Ziel(x, y, w, h, aktion));
    }

    /** Kleiner Knopf; liefert die x-Position fuer den naechsten. */
    private int chip(GuiGraphicsExtractor g, int x, int y, String text, boolean an, int mx, int my, Runnable aktion) {
        int w = this.font.width(text) + 10;
        boolean ueber = drin(mx, my, x, y, w, 14);
        g.fill(x, y, x + w, y + 14, an ? VortexStyle.HOV : ueber ? VortexStyle.CARD : VortexStyle.INNER);
        if (an) g.fill(x, y + 13, x + w, y + 14, VortexStyle.akzent(0.4f));
        g.text(this.font, text, x + 5, y + 3, an || ueber ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, false);
        ziele.add(new Ziel(x, y, w, 14, aktion));
        return x + w + 4;
    }

    private void melden(String text) {
        meldung = text;
        meldungSeit = System.currentTimeMillis();
    }

    private void nimmEigenes() {
        if (m.style.getIndex() != 9) m.style.setIndex(9);
    }

    private boolean imRaster(double mx, double my) {
        int r = zelle * EigenesFadenkreuz.GROESSE;
        return zelle > 0 && drin(mx, my, gx, gy, r, r);
    }

    private void malen(double mx, double my) {
        int n = EigenesFadenkreuz.GROESSE;
        int x = (int) ((mx - gx) / zelle), y = (int) ((my - gy) / zelle);
        if (x < 0 || y < 0 || x >= n || y >= n) return;
        EigenesFadenkreuz.setze(x, y, malWert);
        if (spiegeln) {
            EigenesFadenkreuz.setze(n - 1 - x, y, malWert);
            EigenesFadenkreuz.setze(x, n - 1 - y, malWert);
            EigenesFadenkreuz.setze(n - 1 - x, n - 1 - y, malWert);
        }
        nimmEigenes();
    }

    private static boolean drin(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (imRaster(click.x(), click.y()) && (click.button() == 0 || click.button() == 1)) {
            malWert = click.button() == 1 ? 0 : werkzeug;
            malen(click.x(), click.y());
            return true;
        }
        if (click.button() != 0) return false;
        for (Ziel z : new ArrayList<>(ziele)) {
            if (drin(click.x(), click.y(), z.x(), z.y(), z.w(), z.h())) { z.aktion().run(); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        if (malWert >= 0 && imRaster(click.x(), click.y())) {
            malen(click.x(), click.y());
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (malWert >= 0) {
            malWert = -1;
            EigenesFadenkreuz.speichern();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void onClose() {
        EigenesFadenkreuz.speichern();
        com.vortex.client.core.ConfigManager.save();
        this.minecraft.gui.setScreen(parent);
    }
}
