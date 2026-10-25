package com.vortex.client.gui;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.core.setting.Setting;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Eigene Seite fuer die Bots.
 *
 * AB 4.50: LISTE + DETAIL
 *   Vorher stand jeder Bot als Karte mit ALLEN Einstellungen nebeneinander --
 *   eine Wand aus Reglern. Jetzt:
 *   - links eine schmale Liste im Stil des Modul-Menues, gruppiert in
 *     Farming / Building / Travel / Utility; ein gruener, pulsierender Punkt
 *     zeigt, welcher Bot gerade laeuft
 *   - rechts NUR der gewaehlte Bot: Name gross, Start/Stop, darunter was er
 *     gerade tut ("Now"), ein Fortschrittsbalken und Zahlen, wenn der Bot sie
 *     liefert, dann die Beschreibung und die Einstellungen in zwei Spalten
 *
 * Ein Bot liefert (per Reflexion, damit das Addon auch gegen aeltere Clients
 * baut):  String getStatus(), float getFortschritt() (0..1, -1 = keiner),
 *         List<String> getDetails() (z. B. "Placed 412 / 1 280", "ETA 6 min").
 */
public class BotScreen extends Screen {

    private static final int OBEN = 40;
    private static final int KOPF_H = 36;
    private static final int ZEILE_H = 18;
    private static final int ZAHL_H = 24;
    private static final int AUSWAHL_H = 18;

    private final Screen parent;

    private int mx, my;
    private long letzteZeit = 0;
    private float oeffnen = 0f;
    private float dtJetzt = 0.016f;
    private float lauf = 0f, laufZiel = 0f;
    /** Seit 4.62: die Bot-Liste links scrollt selbst (vorher standen Bots unter dem Rand). */
    private float listeLauf = 0f, listeZiel = 0f;
    /** Sichtbare Bereiche {x, y, w, h} -- Klicks zaehlen nur dort (nicht auf weggescrollte Zeilen). */
    private int[] listeBox = new int[4], detailBox = new int[4];

    private final Map<Setting, Float> einstAnim = new HashMap<>();
    private final Map<Module, Float> anAnim = new HashMap<>();

    private NumberSetting zieht = null;
    private int ziehX, ziehB;

    private Module resetFrage = null;
    private long resetFrageZeit = 0;

    private enum Art { AN, BOOL, NUM, MODUS, FARBE, TASTE, AUSWAHL, RESET, WAHL }

    private static final class Treffer {
        final int x, y, w, h;
        final Art art;
        final Module modul;
        final Setting einst;
        Treffer(int x, int y, int w, int h, Art art, Module m, Setting s) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.art = art; this.modul = m; this.einst = s;
        }
        boolean in(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
    private final List<Treffer> treffer = new ArrayList<>();

    public BotScreen(Screen parent) {
        super(Component.literal("Bots"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        com.vortex.client.core.ConfigManager.save();
        super.removed();
    }

    /** Zuletzt gewaehlter Bot (bleibt beim naechsten Oeffnen). */
    private static String gewaehlt = null;

    private static final int RAND = 14;
    private static final int LISTE_B_MAX = 156;
    private static final int LISTE_Z = 18;
    private static final int GRUPPE_H = 16;

    /** Gruppe eines Bots fuer die Liste links. */
    private static String gruppe(Module m) {
        String n = m.getName();
        if (n.contains("Farmer") || n.contains("Fish") || n.contains("Breed")) return "Farming";
        if (n.contains("Schematic") || n.contains("Build") || n.contains("Printer")) return "Building";
        if (n.contains("Elytra") || n.contains("Autopilot") || n.contains("Travel")) return "Travel";
        return "Utility";
    }

    private static final String[] GRUPPEN = {"Farming", "Building", "Travel", "Utility"};

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;
        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        dtJetzt = dt;
        oeffnen = weich(oeffnen, 1f, 10f, dt);
        float a = oeffnen;
        treffer.clear();
        tasteAufnehmen();

        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(0xB4000000, a));

        List<Module> bots = ModuleManager.INSTANCE.getByCategory(Module.Category.BOTS);
        if (bots.isEmpty()) {
            titel(ctx, a);
            String t = "No bots installed -- they come with the Vortex Plus Addon.";
            ctx.text(this.font, Component.literal(t), this.width / 2 - this.font.width(t) / 2,
                    this.height / 2, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
            super.extractRenderState(ctx, mouseX, mouseY, delta);
            return;
        }
        Module sel = null;
        for (Module m : bots) if (m.getName().equals(gewaehlt)) sel = m;
        if (sel == null) {
            for (Module m : bots) if (m.isEnabled()) { sel = m; break; }
            if (sel == null) sel = bots.get(0);
            gewaehlt = sel.getName();
        }

        int listeB = Math.max(110, Math.min(LISTE_B_MAX, (this.width - 2 * RAND) / 3));
        int detailB = Math.max(140, Math.min(560, this.width - 2 * RAND - listeB - 10));
        int gesamtB = listeB + 10 + detailB;
        int x0 = Math.max(4, (this.width - gesamtB) / 2);
        int y0 = OBEN;
        int hoehe = this.height - y0 - 10;

        titel(ctx, a);
        liste(ctx, bots, sel, x0, y0, listeB, hoehe, a);
        detail(ctx, sel, x0 + listeB + 10, y0, detailB, hoehe, a, dt);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void titel(GuiGraphicsExtractor ctx, float a) {
        String titel = "Bots";
        var p = ctx.pose();
        p.pushMatrix();
        p.translate(this.width / 2f - this.font.width(titel), 10f);
        p.scale(2f, 2f);
        ctx.text(this.font, Component.literal(titel), 0, 0, VortexStyle.fade(VortexStyle.TEXT, a), false);
        p.popMatrix();
        VortexStyle.akzentLinie(ctx, this.width / 2 - 30, 30, 60, a);
    }

    private void liste(GuiGraphicsExtractor ctx, List<Module> bots, Module sel, int x, int y, int w, int maxH, float a) {
        int laufen = 0;
        for (Module m : bots) if (m.isEnabled()) laufen++;
        int h = 20 + 4;
        for (String g : GRUPPEN) {
            int n = 0;
            for (Module m : bots) if (gruppe(m).equals(g)) n++;
            if (n > 0) h += GRUPPE_H + n * LISTE_Z;
        }
        int inhalt = h + 4;
        h = Math.min(inhalt, maxH);
        listeBox = new int[] { x, y + 20, w, h - 20 };
        float maxLauf = Math.max(0, inhalt - h);
        listeZiel = Math.max(0, Math.min(listeZiel, maxLauf));
        listeLauf = weich(listeLauf, listeZiel, 16f, dtJetzt);
        VortexStyle.schatten(ctx, x, y, w, h, a);
        roundRect(ctx, x, y, w, h, VortexStyle.fade(0xF5120E1B, a));
        roundRect(ctx, x, y, w, 20, VortexStyle.fade(VortexStyle.BAR, a));
        verlaufBand(ctx, x + 2, y + 1, w - 4, 17, VortexStyle.fade(VortexStyle.VIOLETT, a * 0.18f), VortexStyle.fade(VortexStyle.BLAU, 0));
        VortexStyle.akzentLinie(ctx, x + 3, y + 18, w - 6, a);
        ctx.text(this.font, Component.literal("Bots"), x + 8, y + 6, VortexStyle.fade(VortexStyle.TEXT, a), false);
        String z = laufen + "/" + bots.size();
        ctx.text(this.font, Component.literal(z), x + w - 8 - this.font.width(z), y + 6,
                VortexStyle.fade(laufen > 0 ? 0xFF86EFAC : VortexStyle.TEXT_DIM, a), false);

        ctx.enableScissor(x, y + 20, x + w, y + h);
        try {
        int cy = y + 24 - Math.round(listeLauf);
        for (String g : GRUPPEN) {
            List<Module> inGruppe = new ArrayList<>();
            for (Module m : bots) if (gruppe(m).equals(g)) inGruppe.add(m);
            if (inGruppe.isEmpty()) continue;
            ctx.text(this.font, Component.literal(g), x + 8, cy + 5, VortexStyle.fade(0xFF6B6480, a), false);
            cy += GRUPPE_H;
            for (Module m : inGruppe) {
                boolean drauf = mx >= x && mx < x + w && my >= cy && my < cy + LISTE_Z;
                float sel2 = weich(anAnim.getOrDefault(m, m == sel ? 1f : 0f), m == sel ? 1f : 0f, 18f, dtJetzt);
                anAnim.put(m, sel2);
                if (drauf && m != sel) ctx.fill(x + 2, cy, x + w - 2, cy + LISTE_Z, VortexStyle.fade(VortexStyle.HOV, a));
                if (sel2 > 0.01f) {
                    verlaufBand(ctx, x + 2, cy, w - 4, LISTE_Z,
                            VortexStyle.fade(VortexStyle.akzent(0f), a * sel2 * 0.5f),
                            VortexStyle.fade(VortexStyle.akzent(1f), a * sel2 * 0.06f));
                    int halb = (int) ((LISTE_Z / 2 - 3) * sel2);
                    ctx.fill(x + 2, cy + LISTE_Z / 2 - halb, x + 4, cy + LISTE_Z / 2 + halb, VortexStyle.fade(VortexStyle.akzent(0.2f), a));
                }
                int farbe = VortexStyle.mix(VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, drauf ? 1f : 0f), 0xFFFFFFFF, sel2);
                ctx.text(this.font, Component.literal(kuerzen(m.getName(), w - 30)), x + 10, cy + 5, VortexStyle.fade(farbe, a), false);
                int px = x + w - 13, py = cy + LISTE_Z / 2 - 2;
                if (m.isEnabled()) {
                    float puls = 0.6f + 0.4f * (float) Math.sin(System.nanoTime() / 2.5e8);
                    ctx.fill(px - 1, py - 1, px + 5, py + 5, VortexStyle.fade(0xFF22C55E, a * 0.25f * puls));
                    ctx.fill(px, py, px + 4, py + 4, VortexStyle.fade(0xFF22C55E, a));
                } else {
                    ctx.fill(px, py, px + 4, py + 4, VortexStyle.fade(0xFF3A3450, a));
                }
                treffer.add(new Treffer(x, cy, w, LISTE_Z, Art.WAHL, m, null));
                cy += LISTE_Z;
            }
        }
        } finally {
            ctx.disableScissor();
        }
        if (maxLauf > 0) {
            int spurY = y + 22, spurH = h - 24;
            int griffH = Math.max(14, Math.round(spurH * (h - 20) / (float) (inhalt - 20)));
            int griffY = spurY + Math.round((spurH - griffH) * (listeLauf / maxLauf));
            ctx.fill(x + w - 4, spurY, x + w - 2, spurY + spurH, VortexStyle.fade(0x22FFFFFF, a));
            ctx.fill(x + w - 4, griffY, x + w - 2, griffY + griffH, VortexStyle.fade(VortexStyle.akzent(0.3f), a));
        }
    }

    private int detailHoehe(Module m, int w) {
        int h = 40 + 8;
        h += statusHoehe(m, w);
        h += beschreibung(m, w - 24).size() * 10 + 10;
        h += 22;
        if (m instanceof com.vortex.client.module.HasOwnScreen) h += AUSWAHL_H + 6;
        h += einstellungenHoehe(m, w - 24);
        return h + 10;
    }

    private int statusHoehe(Module m, int w) {
        if (status(m) == null) return 0;
        int h = 20;
        if (fortschritt(m) >= 0) h += 14;
        h += details(m).size() * 11;
        if (h > 20) h += 4;
        return h + 8;
    }

    private void detail(GuiGraphicsExtractor ctx, Module m, int x, int y, int w, int maxH, float a, float dt) {
        int inhalt = detailHoehe(m, w);
        int h = Math.min(inhalt, maxH);
        float maxLauf = Math.max(0, inhalt - maxH);
        if (laufZiel > maxLauf) laufZiel = maxLauf;
        lauf = weich(lauf, laufZiel, 16f, dt);

        boolean an = m.isEnabled();
        VortexStyle.schatten(ctx, x, y, w, h, a);
        roundRect(ctx, x, y, w, h, VortexStyle.fade(0xF5120E1B, a));

        detailBox = new int[] { x, y, w, h };
        ctx.enableScissor(x, y, x + w, y + h);
        try {
            int cy = y - (int) lauf;
            roundRect(ctx, x, cy, w, 40, VortexStyle.fade(VortexStyle.BAR, a));
            float av = weich(einstAnim.getOrDefault(m.getEnabledSetting(), an ? 1f : 0f), an ? 1f : 0f, 12f, dtJetzt);
            einstAnim.put(m.getEnabledSetting(), av);
            verlaufBand(ctx, x + 2, cy + 1, w - 4, 37,
                    VortexStyle.fade(VortexStyle.akzent(0f), a * (0.14f + 0.36f * av)),
                    VortexStyle.fade(VortexStyle.akzent(1f), a * 0.04f));
            VortexStyle.akzentLinie(ctx, x + 3, cy + 38, w - 6, a);
            var p = ctx.pose();
            p.pushMatrix();
            p.translate(x + 12f, cy + 9f);
            p.scale(1.5f, 1.5f);
            ctx.text(this.font, Component.literal(kuerzen(m.getName(), (int) ((w - 120) / 1.5f))), 0, 0,
                    VortexStyle.fade(0xFFFFFFFF, a), false);
            p.popMatrix();
            String zustand = an ? "Running" : "Idle";
            ctx.text(this.font, Component.literal(zustand), x + 12, cy + 26,
                    VortexStyle.fade(an ? 0xFF86EFAC : VortexStyle.TEXT_DIM, a), false);

            String knopf = an ? "Stop" : "Start";
            int kbB = 70, kbH = 22;
            int kx = x + w - kbB - 10, ky = cy + 9;
            boolean kHov = mx >= kx && mx < kx + kbB && my >= ky && my < ky + kbH;
            int grund = an ? VortexStyle.mix(0xFF3A1420, 0xFFEF4444, kHov ? 0.6f : 0.4f)
                           : VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), kHov ? 0.85f : 0.6f);
            roundRect(ctx, kx, ky, kbB, kbH, VortexStyle.fade(grund, a));
            ctx.text(this.font, Component.literal(knopf), kx + kbB / 2 - this.font.width(knopf) / 2, ky + 7,
                    VortexStyle.fade(0xFFFFFFFF, a), false);
            treffer.add(new Treffer(kx, ky, kbB, kbH, Art.AN, m, null));
            cy += 48;

            int ix = x + 12, iw = w - 24;
            String st = status(m);
            if (st != null) {
                int sh = statusHoehe(m, w) - 8;
                roundRect(ctx, ix, cy, iw, sh, VortexStyle.fade(VortexStyle.BAR, a));
                ctx.fill(ix, cy + 3, ix + 2, cy + sh - 3, VortexStyle.fade(an ? 0xFF22C55E : 0xFF3A3450, a));
                ctx.text(this.font, Component.literal("Now"), ix + 10, cy + 6, VortexStyle.fade(0xFF6B6480, a), false);
                ctx.text(this.font, Component.literal(kuerzen(st, iw - 50)), ix + 38, cy + 6,
                        VortexStyle.fade(an ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, a), false);
                int sy = cy + 20;
                float f = fortschritt(m);
                if (f >= 0) {
                    float fz = weich(einstAnim.getOrDefault(m.getToggleKey(), f), f, 6f, dtJetzt);
                    einstAnim.put(m.getToggleKey(), fz);
                    int bw = iw - 60;
                    ctx.fill(ix + 10, sy + 3, ix + 10 + bw, sy + 7, VortexStyle.fade(VortexStyle.TRACK, a));
                    verlaufBand(ctx, ix + 10, sy + 3, Math.round(bw * Math.max(0, Math.min(1, fz))), 4,
                            VortexStyle.fade(VortexStyle.akzent(0f), a), VortexStyle.fade(VortexStyle.akzent(1f), a));
                    String pz = Math.round(f * 100) + "%";
                    ctx.text(this.font, Component.literal(pz), ix + iw - 10 - this.font.width(pz), sy + 1,
                            VortexStyle.fade(VortexStyle.TEXT, a), false);
                    sy += 14;
                }
                for (String d : details(m)) {
                    ctx.text(this.font, Component.literal(kuerzen(d, iw - 20)), ix + 10, sy,
                            VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
                    sy += 11;
                }
                cy += sh + 8;
            }

            for (String l : beschreibung(m, iw)) {
                ctx.text(this.font, Component.literal(l), ix, cy, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
                cy += 10;
            }
            cy += 10;

            ctx.text(this.font, Component.literal("Settings"), ix, cy + 5, VortexStyle.fade(VortexStyle.TEXT, a), false);
            zeichneReset(ctx, m, ix, cy, iw, a);
            ctx.fill(ix, cy + ZEILE_H + 1, ix + iw, cy + ZEILE_H + 2, VortexStyle.fade(VortexStyle.LINE, a));
            cy += 22;
            if (m instanceof com.vortex.client.module.HasOwnScreen hos) {
                boolean hov = mx >= ix && mx < ix + iw && my >= cy && my < cy + AUSWAHL_H;
                roundRect(ctx, ix, cy, iw, AUSWAHL_H, VortexStyle.fade(hov
                        ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.35f) : VortexStyle.CARD, a));
                ctx.text(this.font, Component.literal(kuerzen(hos.screenButtonLabel(), iw - 20)), ix + 6, cy + 5,
                        VortexStyle.fade(VortexStyle.TEXT, a), false);
                ctx.text(this.font, Component.literal(">"), ix + iw - 9, cy + 5,
                        VortexStyle.fade(VortexStyle.akzent(0.5f), a), false);
                treffer.add(new Treffer(ix, cy, iw, AUSWAHL_H, Art.AUSWAHL, m, null));
                cy += AUSWAHL_H + 6;
            }
            einstellungen(ctx, m, ix, cy, iw, a);
        } finally {
            ctx.disableScissor();
        }

        if (maxLauf > 0) {
            int spur = h - 8;
            int balken = Math.max(20, (int) (spur * (h / (float) inhalt)));
            int by = y + 4 + (int) ((spur - balken) * (lauf / maxLauf));
            ctx.fill(x + w - 4, by, x + w - 2, by + balken, VortexStyle.fade(VortexStyle.akzent(0.5f), a * 0.7f));
        }
    }

    /** Einstellungen in zwei Spalten (bei schmalem Fenster in einer). */
    private List<Setting> einstellungsListe(Module m) {
        List<Setting> l = new ArrayList<>();
        for (Setting s : m.getSettings()) if (s != m.getEnabledSetting()) l.add(s);
        return l;
    }

    private static int zeilenHoehe(Setting s) {
        return (s instanceof NumberSetting) ? ZAHL_H : ZEILE_H;
    }

    private int einstellungenHoehe(Module m, int w) {
        List<Setting> l = einstellungsListe(m);
        int spalten = w >= 330 ? 2 : 1;
        int h = 0;
        for (int i = 0; i < l.size(); i += spalten) {
            int zh = zeilenHoehe(l.get(i));
            if (spalten == 2 && i + 1 < l.size()) zh = Math.max(zh, zeilenHoehe(l.get(i + 1)));
            h += zh + 2;
        }
        return h;
    }

    private void einstellungen(GuiGraphicsExtractor ctx, Module m, int x, int y, int w, float a) {
        List<Setting> l = einstellungsListe(m);
        int spalten = w >= 330 ? 2 : 1;
        int sb = spalten == 2 ? (w - 16) / 2 : w;
        int cy = y;
        for (int i = 0; i < l.size(); i += spalten) {
            int zh = zeilenHoehe(l.get(i));
            if (spalten == 2 && i + 1 < l.size()) zh = Math.max(zh, zeilenHoehe(l.get(i + 1)));
            zeichneEinstellung(ctx, m, l.get(i), x, cy, sb, a);
            if (spalten == 2 && i + 1 < l.size()) zeichneEinstellung(ctx, m, l.get(i + 1), x + sb + 16, cy, sb, a);
            cy += zh + 2;
        }
    }

    private List<String> beschreibung(Module m, int breite) {
        List<String> zeilen = new ArrayList<>();
        String text = ModuleInfo.get(m.getName());
        if (text == null || text.isBlank()) return zeilen;
        StringBuilder z = new StringBuilder();
        for (String wort : text.split(" ")) {
            String probe = z.length() == 0 ? wort : z + " " + wort;
            if (this.font.width(probe) > breite && z.length() > 0) {
                zeilen.add(z.toString());
                z = new StringBuilder(wort);
            } else {
                z = new StringBuilder(probe);
            }
        }
        if (z.length() > 0) zeilen.add(z.toString());
        return zeilen;
    }

    private int zeichneEinstellung(GuiGraphicsExtractor ctx, Module m, Setting s,
                                   int x, int y, int w, float a) {
        int dim = VortexStyle.fade(VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, 0.45f), a);
        int txt = VortexStyle.fade(VortexStyle.TEXT, a);
        int akz = VortexStyle.fade(VortexStyle.akzent(0.5f), a);
        int zh = (s instanceof NumberSetting) ? ZAHL_H : ZEILE_H;
        if (mx >= x && mx < x + w && my >= y && my < y + zh) {
            ctx.fill(x - 3, y, x + w + 3, y + zh, VortexStyle.fade(VortexStyle.HOV, a * 0.7f));
        }

        if (s instanceof BooleanSetting b) {
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - 30)), x + 2, y + 5, dim, false);
            float an = weich(einstAnim.getOrDefault(s, b.get() ? 1f : 0f), b.get() ? 1f : 0f, 16f, dtJetzt);
            einstAnim.put(s, an);
            int bx = x + w - 22;
            ctx.fill(bx, y + 5, bx + 22, y + 13,
                    VortexStyle.fade(VortexStyle.mix(VortexStyle.TRACK, VortexStyle.akzent(0.5f), an), a));
            int kx = bx + 1 + (int) (an * 12f);
            ctx.fill(kx, y + 4, kx + 8, y + 14, VortexStyle.fade(VortexStyle.mix(0xFFB8B2CC, 0xFFFFFFFF, an), a));
            treffer.add(new Treffer(x, y, w, zh, Art.BOOL, m, s));

        } else if (s instanceof NumberSetting n) {
            String wert = zahl(n);
            int wertB = this.font.width(wert);
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - wertB - 10)), x + 2, y + 3, dim, false);
            ctx.text(this.font, Component.literal(wert), x + w - wertB, y + 3, txt, false);
            int ty = y + 15;
            ctx.fill(x + 2, ty, x + w, ty + 3, VortexStyle.fade(VortexStyle.TRACK, a));
            double ziel = (n.get() - n.getMin()) / Math.max(1e-9, n.getMax() - n.getMin());
            ziel = Math.max(0, Math.min(1, ziel));
            float pz = weich(einstAnim.getOrDefault(s, (float) ziel), (float) ziel,
                    zieht == n ? 40f : 14f, dtJetzt);
            einstAnim.put(s, pz);
            int fx = x + 2 + (int) ((w - 2) * pz);
            verlaufBand(ctx, x + 2, ty, fx - x - 2, 3,
                    VortexStyle.fade(VortexStyle.akzent(0f), a), VortexStyle.fade(VortexStyle.akzent(1f), a));
            ctx.fill(fx - 2, ty - 3, fx + 3, ty + 6, VortexStyle.fade(0xFFFFFFFF, a));
            treffer.add(new Treffer(x + 2, y, w - 2, zh, Art.NUM, m, s));

        } else if (s instanceof ModeSetting mode) {
            String wert = "< " + mode.get() + " >";
            wert = kuerzen(wert, Math.max(40, w / 2));
            int wertB = this.font.width(wert);
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - wertB - 10)), x + 2, y + 5, dim, false);
            ctx.text(this.font, Component.literal(wert), x + w - wertB, y + 5, akz, false);
            treffer.add(new Treffer(x, y, w, zh, Art.MODUS, m, s));

        } else if (s instanceof ColorSetting c) {
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - 20)), x + 2, y + 5, dim, false);
            int bx = x + w - 14;
            ctx.fill(bx, y + 3, bx + 12, y + 15, VortexStyle.fade(0xFF000000, a));
            for (int i = 0; i < 10; i++) {
                ctx.fill(bx + 1 + i, y + 4, bx + 2 + i, y + 14,
                        VortexStyle.fade(c.at((i + 0.5f) / 10f) | 0xFF000000, a));
            }
            treffer.add(new Treffer(x, y, w, zh, Art.FARBE, m, s));

        } else if (s instanceof KeySetting k) {
            boolean lauscht = k.isListening();
            String wert = lauscht ? "press a key..." : (k.isBound() ? k.getKeyName() : "None");
            wert = kuerzen(wert, Math.max(30, w / 2));
            int kappeB = this.font.width(wert) + 10;
            int kx = x + w - kappeB;
            ctx.fill(kx, y + 2, kx + kappeB, y + zh - 2, VortexStyle.fade(lauscht
                    ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.45f) : VortexStyle.CARD, a));
            ctx.fill(kx, y + zh - 3, kx + kappeB, y + zh - 2,
                    VortexStyle.fade(lauscht ? VortexStyle.akzent(0.5f) : VortexStyle.LINE, a));
            ctx.text(this.font, Component.literal(wert), kx + 5, y + 5, k.isBound() || lauscht ? txt : dim, false);
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - kappeB - 10)), x + 2, y + 5, dim, false);
            treffer.add(new Treffer(x, y, w, zh, Art.TASTE, m, s));
        }
        return y + zh;
    }

    private void zeichneReset(GuiGraphicsExtractor ctx, Module m, int x, int y, int w, float a) {
        boolean frage = resetFrage == m && System.currentTimeMillis() - resetFrageZeit < 3000;
        String text = frage ? "Click again to reset" : "Reset to default";
        int tw = this.font.width(text);
        int bx = x + w - tw - 8;
        boolean hov = mx >= bx && mx < x + w && my >= y && my < y + ZEILE_H;
        if (hov || frage) {
            ctx.fill(bx, y + 1, x + w, y + ZEILE_H - 1, VortexStyle.fade(
                    frage ? VortexStyle.mix(VortexStyle.CARD, 0xFFEF4444, 0.35f) : VortexStyle.HOV, a));
        }
        ctx.text(this.font, Component.literal(text), bx + 4, y + 5,
                VortexStyle.fade(frage ? 0xFFFF8A8A : (hov ? VortexStyle.TEXT : VortexStyle.TEXT_DIM), a), false);
        treffer.add(new Treffer(bx, y, x + w - bx, ZEILE_H, Art.RESET, m, null));
    }

    private static final Map<Class<?>, java.lang.reflect.Method> STATUS = new HashMap<>();

    /**
     * Statuszeile des Bots, oder null.
     *
     * Ein Bot liefert sie ueber eine oeffentliche Methode "getStatus()" --
     * per Reflexion abgefragt, damit das Addon auch gegen einen aelteren
     * Client ohne gemeinsame Schnittstelle baut.
     */
    private static String status(Module m) {
        try {
            java.lang.reflect.Method meth;
            if (STATUS.containsKey(m.getClass())) {
                meth = STATUS.get(m.getClass());
            } else {
                try {
                    meth = m.getClass().getMethod("getStatus");
                    if (meth.getReturnType() != String.class) meth = null;
                } catch (NoSuchMethodException e) {
                    meth = null;
                }
                STATUS.put(m.getClass(), meth);
            }
            if (meth == null) return null;
            Object o = meth.invoke(m);
            return o == null ? null : o.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static float fortschritt(Module m) {
        try {
            java.lang.reflect.Method meth = m.getClass().getMethod("getFortschritt");
            Object o = meth.invoke(m);
            return o instanceof Number n ? n.floatValue() : -1f;
        } catch (Throwable t) {
            return -1f;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> details(Module m) {
        try {
            java.lang.reflect.Method meth = m.getClass().getMethod("getDetails");
            Object o = meth.invoke(m);
            if (o instanceof List<?> l) {
                List<String> r = new ArrayList<>();
                for (Object e : l) if (e != null) r.add(e.toString());
                return r.size() > 6 ? r.subList(0, 6) : r;
            }
        } catch (Throwable ignored) { }
        return java.util.Collections.emptyList();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (my < OBEN) return false;
        if (!drin(listeBox, mx, my) && !drin(detailBox, mx, my)) return false;
        int knopf = click.button();
        for (int i = treffer.size() - 1; i >= 0; i--) {
            Treffer t = treffer.get(i);
            if (!t.in(mx, my)) continue;
            switch (t.art) {
                case AN:
                    t.modul.toggle();
                    return true;
                case WAHL:
                    if (!t.modul.getName().equals(gewaehlt)) {
                        gewaehlt = t.modul.getName();
                        laufZiel = 0;
                        lauf = 0;
                    }
                    return true;
                case BOOL: {
                    BooleanSetting b = (BooleanSetting) t.einst;
                    if (b == t.modul.getEnabledSetting()) t.modul.toggle();
                    else b.toggle();
                    return true;
                }
                case NUM:
                    zieht = (NumberSetting) t.einst;
                    ziehX = t.x;
                    ziehB = t.w;
                    schieber(zieht, mx);
                    return true;
                case MODUS:
                    if (knopf == 1) zurueck((ModeSetting) t.einst);
                    else ((ModeSetting) t.einst).cycle();
                    return true;
                case FARBE:
                    Minecraft.getInstance().gui.setScreen(new ColorPickerScreen(this, (ColorSetting) t.einst));
                    return true;
                case AUSWAHL:
                    if (t.modul instanceof com.vortex.client.module.HasOwnScreen hos) {
                        Minecraft.getInstance().gui.setScreen(hos.createScreen(this));
                    }
                    return true;
                case TASTE:
                    ((KeySetting) t.einst).setListening(true);
                    return true;
                case RESET: {
                    long jetztMs = System.currentTimeMillis();
                    if (resetFrage == t.modul && jetztMs - resetFrageZeit < 3000) {
                        for (Setting s : t.modul.getSettings()) {
                            if (s == t.modul.getEnabledSetting() || s == t.modul.getToggleKey()) continue;
                            s.resetToDefault();
                            einstAnim.remove(s);
                        }
                        com.vortex.client.core.ConfigManager.save();
                        resetFrage = null;
                    } else {
                        resetFrage = t.modul;
                        resetFrageZeit = jetztMs;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        if (zieht != null) {
            schieber(zieht, mx);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        zieht = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (drin(listeBox, mouseX, mouseY) || (mouseX < detailBox[0] && mouseY >= OBEN)) {
            listeZiel = Math.max(0f, listeZiel - (float) vertical * LISTE_Z * 2);
        } else {
            laufZiel = Math.max(0f, laufZiel - (float) vertical * 28f);
        }
        return true;
    }

    private static boolean drin(int[] b, double px, double py) {
        return b != null && b[2] > 0 && px >= b[0] && px < b[0] + b[2] && py >= b[1] && py < b[1] + b[3];
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return lauschend() == null;
    }

    private void schieber(NumberSetting n, int mausX) {
        if (ziehB <= 0) return;
        float q = (mausX - ziehX) / (float) ziehB;
        if (q < 0f) q = 0f;
        if (q > 1f) q = 1f;
        double roh = n.getMin() + q * (n.getMax() - n.getMin());
        double schritt = n.getStep();
        if (schritt > 0) roh = Math.round(roh / schritt) * schritt;
        roh = Math.round(roh * 1000.0) / 1000.0;
        if (roh < n.getMin()) roh = n.getMin();
        if (roh > n.getMax()) roh = n.getMax();
        n.set(roh);
    }

    private void zurueck(ModeSetting mode) {
        int start = mode.getIndex();
        int anzahl = 0;
        do { mode.cycle(); anzahl++; } while (mode.getIndex() != start && anzahl < 64);
        if (anzahl <= 1) return;
        for (int i = 0; i < anzahl - 1; i++) mode.cycle();
    }

    private KeySetting lauschend() {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            for (Setting s : m.getSettings()) {
                if (s instanceof KeySetting k && k.isListening()) return k;
            }
        }
        return null;
    }

    private void tasteAufnehmen() {
        KeySetting k = lauschend();
        if (k == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            k.setKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
            k.setListening(false);
            com.vortex.client.core.ConfigManager.save();
            return;
        }
        for (int code = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
             code <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST; code++) {
            if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), code)) {
                k.setKeyCode(code);
                k.setListening(false);
                com.vortex.client.core.ConfigManager.save();
                return;
            }
        }
    }

    private String zahl(NumberSetting n) {
        double v = n.get();
        if (n.getStep() >= 1 && v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private String kuerzen(String s, int max) {
        if (this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private static float weich(float ist, float ziel, float tempo, float dt) {
        float f = 1f - (float) Math.exp(-tempo * dt);
        float neu = ist + (ziel - ist) * f;
        return Math.abs(ziel - neu) < 0.005f ? ziel : neu;
    }

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(2, Math.min(w / 2, h / 2));
        ctx.fill(x, y + r, x + w, y + h - r, c);
        ctx.fill(x + r, y, x + w - r, y + r, c);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, c);
        if (r >= 2) {
            ctx.fill(x + 1, y + 1, x + r, y + r, c);
            ctx.fill(x + w - r, y + 1, x + w - 1, y + r, c);
            ctx.fill(x + 1, y + h - r, x + r, y + h - 1, c);
            ctx.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, c);
        }
    }

    private static void verlaufBand(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int von, int bis) {
        if (w <= 0 || h <= 0) return;
        int baender = Math.max(1, (w + 5) / 6);
        for (int b = 0; b < baender; b++) {
            int ax = x + w * b / baender, bx = x + w * (b + 1) / baender;
            if (bx <= ax) continue;
            ctx.fill(ax, y, bx, y + h, VortexStyle.mix(von, bis, (b + 0.5f) / baender));
        }
    }
}
