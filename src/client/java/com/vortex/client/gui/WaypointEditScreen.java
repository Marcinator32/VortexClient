package com.vortex.client.gui;

import com.vortex.client.core.ConfigManager;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Glatt.Schrift;
import com.vortex.client.waypoint.WaypointActions;
import com.vortex.client.waypoint.WaypointManager;
import com.vortex.client.waypoint.WaypointManager.Kind;
import com.vortex.client.waypoint.WaypointManager.Waypoint;
import com.vortex.client.waypoint.WaypointWorlds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Einen Waypoint anlegen oder bearbeiten (seit 4.94): Name, Zeichen im Marker,
 * Koordinaten, Farbe, Art und die beiden Schalter -- alles in einem Fenster,
 * gespeichert wird erst mit "Save".
 */
public class WaypointEditScreen extends Screen {

    private record Feld(int x, int y, int w, int h) {
        boolean drin(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final Screen parent;
    private final Waypoint original;
    private final String welt, dim;

    private EditBox name, zeichen, fx, fy, fz;
    private int farbe;
    private Kind art;
    private boolean sichtbar, tracer;
    private String fehler = "";

    private int wx, wy, ww, wh, mx, my;
    private float oeffnen;
    private long letzteZeit;
    private final List<Object[]> felder = new ArrayList<>();

    public WaypointEditScreen(Screen parent, Waypoint original, String welt, String dim) {
        super(Component.literal(original == null ? "New waypoint" : "Edit waypoint"));
        this.parent = parent;
        this.original = original;
        this.welt = welt;
        this.dim = dim;
        if (original != null) {
            farbe = original.color | 0xFF000000;
            art = original.kind;
            sichtbar = original.visible;
            tracer = original.tracer;
        } else {
            farbe = WaypointManager.naechsteFarbe();
            art = Kind.ALLGEMEIN;
            sichtbar = true;
            tracer = false;
        }
    }

    @Override
    protected void init() {
        ww = Math.min(this.width - 24, 380);
        wh = Math.min(this.height - 24, original == null ? 262 : 290);
        wx = (this.width - ww) / 2;
        wy = (this.height - wh) / 2;

        String n, z, x, y, zz;
        if (name != null) {
            n = name.getValue(); z = zeichen.getValue(); x = fx.getValue(); y = fy.getValue(); zz = fz.getValue();
        } else if (original != null) {
            n = original.name; z = original.symbol == null ? "" : original.symbol;
            x = String.valueOf(original.x); y = String.valueOf(original.y); zz = String.valueOf(original.z);
        } else {
            n = "";
            z = "";
            var p = Minecraft.getInstance().player;
            boolean hier = p != null && istHierWelt();
            x = hier ? String.valueOf(p.getBlockX()) : "0";
            y = hier ? String.valueOf(p.getBlockY()) : "64";
            zz = hier ? String.valueOf(p.getBlockZ()) : "0";
        }
        int lx = wx + 16;
        name = feld(lx + 4, wy + 52, ww - 32 - 70, 32, n);
        zeichen = feld(wx + ww - 16 - 52 + 4, wy + 52, 44, 2, z);
        int bw = (ww - 32 - 16 - 70) / 3;
        fx = zahlFeld(lx + 4, wy + 92, bw - 8, x);
        fy = zahlFeld(lx + bw + 8 + 4, wy + 92, bw - 8, y);
        fz = zahlFeld(lx + 2 * (bw + 8) + 4, wy + 92, bw - 8, zz);
        if (original == null) setInitialFocus(name);
    }

    private EditBox feld(int x, int y, int w, int max, String wert) {
        EditBox e = new EditBox(this.font, x, y, w, 12, Component.literal(""));
        e.setBordered(false);
        e.setMaxLength(max);
        e.setValue(wert);
        this.addRenderableWidget(e);
        return e;
    }

    private EditBox zahlFeld(int x, int y, int w, String wert) {
        EditBox e = feld(x, y, w, 9, wert);
        e.setResponder(s -> {
            String t = s.replaceAll("[^0-9-]", "");
            if (t.lastIndexOf('-') > 0) t = (t.startsWith("-") ? "-" : "") + t.replace("-", "");
            if (!t.equals(s)) e.setValue(t);
        });
        return e;
    }

    private boolean istHierWelt() {
        var h = WaypointWorlds.hier(Minecraft.getInstance(), false);
        return h != null && h.welt().id.equals(welt) && h.dim().equals(dim);
    }

    /* ---------------------------------------------------------------- Zeichnen */

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX;
        my = mouseY;
        felder.clear();
        long now = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (now - letzteZeit) / 1e9f);
        letzteZeit = now;
        oeffnen += (1f - oeffnen) * (1f - (float) Math.exp(-14f * dt));
        float al = oeffnen;
        int akzent = Theme.INSTANCE.accent.get() | 0xFF000000;

        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(VortexStyle.DIM, al));
        Glatt.schatten(ctx, wx, wy + 3, ww, wh, 8, 10, Glatt.alpha(0xFF000000, al * 0.5f));
        Glatt.rund(ctx, wx, wy, ww, wh, 8, Glatt.alpha(VortexStyle.WINDOW, al));
        Glatt.rahmen(ctx, wx, wy, ww, wh, 8, 1, Glatt.alpha(0x18FFFFFF, al));
        Glatt.rund(ctx, wx + 16, wy, ww - 32, 1.5f, 0.75f, Glatt.alpha(VortexStyle.akzent(0.5f), al));

        float kx = wx + 26, ky = wy + 20;
        Glatt.kreis(ctx, kx, ky, 20, Glatt.alpha(farbe, al));
        Glatt.kreis(ctx, kx, ky, 16, Glatt.alpha(VortexStyle.mix(farbe, 0xFF000000, 0.35f), al));
        Glatt.textMitte(ctx, vorschauZeichen(), kx, ky - 4, Glatt.alpha(0xFFFFFFFF, al), Schrift.FETT);
        Glatt.text(ctx, original == null ? "New waypoint" : "Edit waypoint", wx + 44, wy + 11, Glatt.alpha(VortexStyle.TEXT, al), Schrift.TITEL);
        WaypointWorlds.Welt w = WaypointWorlds.finde(welt);
        String wo = (w == null ? "?" : WaypointWorlds.anzeige(w)) + "  ·  " + WaypointWorlds.dimName(dim);
        Glatt.textRechts(ctx, Glatt.kuerzen(wo, ww - 200, Schrift.NORMAL), wx + ww - 16, wy + 14, Glatt.alpha(VortexStyle.TEXT_DIM, al), Schrift.NORMAL);

        int lx = wx + 16;
        beschriftung(ctx, "Name", lx, wy + 38, al);
        beschriftung(ctx, "Letter", wx + ww - 16 - 52, wy + 38, al);
        box(ctx, name, lx, wy + 48, ww - 32 - 70 + 8, akzent, al);
        box(ctx, zeichen, wx + ww - 16 - 52, wy + 48, 52, akzent, al);
        if (name.getValue().isEmpty() && !name.isFocused()) {
            Glatt.text(ctx, platzhalterName(), name.getX(), name.getY() + 1, Glatt.alpha(0xFF6A6480, al), Schrift.NORMAL);
        }
        if (zeichen.getValue().isEmpty() && !zeichen.isFocused()) {
            Glatt.text(ctx, "auto", zeichen.getX(), zeichen.getY() + 1, Glatt.alpha(0xFF6A6480, al), Schrift.NORMAL);
        }

        int bw = (ww - 32 - 16 - 70) / 3;
        beschriftung(ctx, "X", lx, wy + 78, al);
        beschriftung(ctx, "Y", lx + bw + 8, wy + 78, al);
        beschriftung(ctx, "Z", lx + 2 * (bw + 8), wy + 78, al);
        box(ctx, fx, lx, wy + 88, bw, akzent, al);
        box(ctx, fy, lx + bw + 8, wy + 88, bw, akzent, al);
        box(ctx, fz, lx + 2 * (bw + 8), wy + 88, bw, akzent, al);
        int hx = lx + 3 * (bw + 8);
        Feld fHier = new Feld(hx, wy + 88, wx + ww - 16 - hx, 20);
        knopf(ctx, fHier, "Here", istHierWelt(), false, akzent, al);
        felder.add(new Object[] { "hier", fHier });

        beschriftung(ctx, "Colour", lx, wy + 118, al);
        int sx = lx, sy = wy + 128;
        for (int c : WaypointManager.PALETTE) {
            Feld f = new Feld(sx, sy, 18, 18);
            boolean gewaehlt = (c | 0xFF000000) == farbe;
            if (gewaehlt) Glatt.rahmen(ctx, sx - 2, sy - 2, 22, 22, 6, 2, Glatt.alpha(0xFFFFFFFF, al));
            Glatt.rund(ctx, sx, sy, 18, 18, 5, Glatt.alpha(c | 0xFF000000, al));
            if (f.drin(mx, my)) Glatt.rahmen(ctx, sx, sy, 18, 18, 5, 1, Glatt.alpha(0x80FFFFFF, al));
            felder.add(new Object[] { "farbe", f, c });
            sx += 23;
        }
        Feld fEigen = new Feld(sx, sy, 50, 18);
        knopf(ctx, fEigen, "More", true, false, akzent, al);
        felder.add(new Object[] { "eigen", fEigen });

        beschriftung(ctx, "Type", lx, wy + 154, al);
        int tx = lx, ty = wy + 164;
        for (Kind k : Kind.values()) {
            int b = Glatt.breite(k.label, Schrift.NORMAL) + 14;
            if (tx + b > wx + ww - 16) { tx = lx; ty += 20; }
            Feld f = new Feld(tx, ty, b, 17);
            boolean an = k == art;
            int grund = an ? VortexStyle.mix(VortexStyle.CARD, k.color | 0xFF000000, 0.45f) : f.drin(mx, my) ? VortexStyle.HOV : VortexStyle.CARD;
            Glatt.rund(ctx, f.x, f.y, f.w, f.h, 8.5f, Glatt.alpha(grund, al));
            Glatt.text(ctx, k.label, f.x + 7, f.y + 5, Glatt.alpha(an ? 0xFFFFFFFF : VortexStyle.TEXT_DIM, al), Schrift.NORMAL);
            felder.add(new Object[] { "art", f, k });
            tx += b + 5;
        }

        int oy = ty + 26;
        Feld fSicht = schalterZeile(ctx, lx, oy, "Visible", sichtbar, akzent, al);
        felder.add(new Object[] { "sichtbar", fSicht });
        Feld fTracer = schalterZeile(ctx, lx + (ww - 32) / 2, oy, "Tracer line", tracer, akzent, al);
        felder.add(new Object[] { "tracer", fTracer });

        if (original != null) {
            int ey = oy + 24;
            int ex = lx;
            boolean nether = dim != null && dim.contains("nether");
            boolean oberwelt = dim != null && dim.endsWith(":overworld");
            if (nether || oberwelt) {
                Feld f = knopfAuto(ctx, ex, ey, nether ? "Copy to Overworld" : "Copy to Nether", true, akzent, al);
                felder.add(new Object[] { "gegenstueck", f });
                ex += f.w + 6;
            }
            Feld fk = knopfAuto(ctx, ex, ey, "Copy coords", true, akzent, al);
            felder.add(new Object[] { "kopieren", fk });
            ex += fk.w + 6;
            boolean gruppe = WaypointActions.getBlockGroup() == original;
            if (istHierWelt()) {
                Feld fb = knopfAuto(ctx, ex, ey, gruppe ? "Stop marking blocks" : "Mark blocks", true, akzent, al);
                felder.add(new Object[] { "bloecke", fb });
                ex += fb.w + 6;
            }
            if (!original.blocks.isEmpty()) {
                Feld fl = knopfAuto(ctx, ex, ey, "Clear " + original.blocks.size() + " blocks", true, akzent, al);
                if (fl.x + fl.w <= wx + ww - 16) felder.add(new Object[] { "bloeckeWeg", fl });
            }
        }

        int fyy = wy + wh - 32;
        Glatt.rund(ctx, wx + 1, fyy - 8, ww - 2, 1, 0, Glatt.alpha(VortexStyle.LINE, al));
        if (!fehler.isEmpty()) Glatt.text(ctx, fehler, lx, fyy + 7, Glatt.alpha(0xFFF87171, al), Schrift.NORMAL);
        Feld fSpeichern = new Feld(wx + ww - 16 - 70, fyy, 70, 22);
        Feld fAbbrechen = new Feld(fSpeichern.x - 6 - 70, fyy, 70, 22);
        knopf(ctx, fAbbrechen, "Cancel", true, false, akzent, al);
        knopf(ctx, fSpeichern, "Save", true, true, akzent, al);
        felder.add(new Object[] { "abbrechen", fAbbrechen });
        felder.add(new Object[] { "speichern", fSpeichern });

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private String platzhalterName() {
        return art.label + " " + (WaypointManager.anzahlIn(welt) + 1);
    }

    private String vorschauZeichen() {
        String z = zeichen == null ? "" : zeichen.getValue().trim();
        if (!z.isEmpty()) return z;
        String n = name == null || name.getValue().isBlank() ? platzhalterName() : name.getValue().trim();
        return n.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    private void beschriftung(GuiGraphicsExtractor ctx, String text, int x, int y, float al) {
        Glatt.text(ctx, text, x, y, Glatt.alpha(VortexStyle.TEXT_DIM, al), Schrift.FETT);
    }

    private void box(GuiGraphicsExtractor ctx, EditBox e, int x, int y, int w, int akzent, float al) {
        Glatt.rund(ctx, x, y, w, 20, 5, Glatt.alpha(VortexStyle.INNER, al));
        if (e.isFocused()) Glatt.rahmen(ctx, x, y, w, 20, 5, 1, Glatt.alpha(akzent, al * 0.8f));
        e.setX(x + 6);
        e.setY(y + 6);
        e.setWidth(w - 10);
    }

    private Feld schalterZeile(GuiGraphicsExtractor ctx, int x, int y, String text, boolean an, int akzent, float al) {
        int b = Glatt.breite(text, Schrift.NORMAL);
        Feld f = new Feld(x, y, b + 34, 16);
        Glatt.rund(ctx, x, y + 2, 22, 11, 5.5f, Glatt.alpha(an ? akzent : VortexStyle.TRACK, al));
        Glatt.kreis(ctx, x + (an ? 16.5f : 5.5f), y + 7.5f, 8, Glatt.alpha(an ? 0xFFFFFFFF : 0xFFB8B2CC, al));
        Glatt.text(ctx, text, x + 30, y + 4, Glatt.alpha(f.drin(mx, my) ? 0xFFFFFFFF : VortexStyle.TEXT, al), Schrift.NORMAL);
        return f;
    }

    private Feld knopfAuto(GuiGraphicsExtractor ctx, int x, int y, String text, boolean aktiv, int akzent, float al) {
        Feld f = new Feld(x, y, Glatt.breite(text, Schrift.NORMAL) + 16, 20);
        knopf(ctx, f, text, aktiv, false, akzent, al);
        return f;
    }

    private void knopf(GuiGraphicsExtractor ctx, Feld f, String text, boolean aktiv, boolean haupt, int akzent, float al) {
        boolean hov = aktiv && f.drin(mx, my);
        int grund = haupt ? akzent : VortexStyle.CARD;
        if (hov) grund = VortexStyle.mix(grund, 0xFFFFFFFF, haupt ? 0.12f : 0.06f);
        if (!aktiv) grund = VortexStyle.mix(VortexStyle.CARD, VortexStyle.WINDOW, 0.5f);
        Glatt.rund(ctx, f.x, f.y, f.w, f.h, 5, Glatt.alpha(grund, al));
        if (!haupt && aktiv) Glatt.rahmen(ctx, f.x, f.y, f.w, f.h, 5, 1, Glatt.alpha(0x14FFFFFF, al));
        Glatt.textMitte(ctx, text, f.x + f.w / 2f, f.y + (f.h - 9) / 2f + 1, Glatt.alpha(aktiv ? 0xFFFFFFFF : 0xFF5E5872, al), Schrift.NORMAL);
    }

    /* ---------------------------------------------------------------- Bedienung */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        for (EditBox e : new EditBox[] { name, zeichen, fx, fy, fz }) if (e.isFocused() && !e.isMouseOver(click.x(), click.y())) e.setFocused(false);
        Minecraft mc = Minecraft.getInstance();
        for (Object[] f : felder) {
            Feld r = (Feld) f[1];
            if (!r.drin(click.x(), click.y())) continue;
            switch ((String) f[0]) {
                case "hier" -> {
                    var p = mc.player;
                    if (p != null && istHierWelt()) {
                        fx.setValue(String.valueOf(p.getBlockX()));
                        fy.setValue(String.valueOf(p.getBlockY()));
                        fz.setValue(String.valueOf(p.getBlockZ()));
                    }
                }
                case "farbe" -> farbe = ((Integer) f[2]) | 0xFF000000;
                case "eigen" -> {
                    var cs = new com.vortex.client.core.setting.ColorSetting("Waypoint Colour", farbe).noGradient();
                    mc.gui.setScreen(new ColorPickerScreen(this, cs, () -> farbe = cs.get() | 0xFF000000));
                }
                case "art" -> {
                    Kind k = (Kind) f[2];
                    if (k != art) {
                        art = k;
                        if (k != Kind.ALLGEMEIN) farbe = k.color | 0xFF000000;
                    }
                }
                case "sichtbar" -> sichtbar = !sichtbar;
                case "tracer" -> tracer = !tracer;
                case "gegenstueck" -> {
                    if (speichern(false)) {
                        Waypoint c = WaypointActions.createCounterpart(mc, original);
                        ConfigManager.save();
                        mc.gui.setScreen(parent);
                        if (c != null) fehler = "";
                    }
                }
                case "kopieren" -> {
                    mc.keyboardHandler.setClipboard(fx.getValue() + " " + fy.getValue() + " " + fz.getValue());
                    fehler = "";
                }
                case "bloecke" -> {
                    if (WaypointActions.getBlockGroup() == original) {
                        WaypointActions.setBlockGroup(null);
                    } else if (speichern(false)) {
                        WaypointActions.setBlockGroup(original);
                        mc.gui.setScreen(null);
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("[Waypoints] Marking blocks for \"" + original.name
                                    + "\". Use your Mark Block / Mark Area keys (set them in Waypoint Settings)."));
                        }
                    }
                }
                case "bloeckeWeg" -> {
                    original.blocks.clear();
                    ConfigManager.save();
                }
                case "abbrechen" -> onClose();
                case "speichern" -> {
                    if (speichern(true)) mc.gui.setScreen(parent);
                }
                default -> { }
            }
            return true;
        }
        return false;
    }

    /** Eingaben pruefen und uebernehmen. @return true, wenn gespeichert */
    private boolean speichern(boolean neuAnlegen) {
        int x, y, z;
        try {
            x = Integer.parseInt(fx.getValue().trim());
            y = Integer.parseInt(fy.getValue().trim());
            z = Integer.parseInt(fz.getValue().trim());
        } catch (NumberFormatException e) {
            fehler = "X, Y and Z must be whole numbers.";
            return false;
        }
        String n = name.getValue().trim();
        if (n.isEmpty()) n = platzhalterName();
        String sym = zeichen.getValue().trim();
        Waypoint w = original;
        if (w == null) {
            if (!neuAnlegen) return false;
            w = new Waypoint(n, x, y, z, farbe, welt, dim);
            WaypointManager.add(w);
        }
        w.name = n;
        w.x = x; w.y = y; w.z = z;
        w.color = farbe;
        w.kind = art;
        w.symbol = sym;
        w.visible = sichtbar;
        w.tracer = tracer;
        ConfigManager.save();
        fehler = "";
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (speichern(true)) Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
