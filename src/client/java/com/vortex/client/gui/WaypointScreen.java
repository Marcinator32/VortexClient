package com.vortex.client.gui;

import com.vortex.client.core.ConfigManager;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Glatt.Schrift;
import com.vortex.client.gui.glatt.Symbole;
import com.vortex.client.waypoint.WaypointActions;
import com.vortex.client.waypoint.WaypointManager;
import com.vortex.client.waypoint.WaypointManager.Waypoint;
import com.vortex.client.waypoint.WaypointSettings;
import com.vortex.client.waypoint.WaypointWorlds;
import com.vortex.client.waypoint.WaypointWorlds.Welt;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Waypoint-Verwaltung (neu in 4.94, angelehnt an Xaero's Minimap).
 *
 * Oben: Anzeige im Spiel an/aus und die Einstellungen. Darunter die Welt (ein
 * Klick oeffnet die Weltauswahl), die Dimension und eine Suche. Die Liste zeigt
 * die Waypoints der Welt; ein Klick waehlt aus, ein Doppelklick bearbeitet, der
 * Schalter rechts blendet einen einzelnen Waypoint ein oder aus. Unten stehen die
 * Aktionen fuer die Auswahl -- ausgeschrieben statt Buchstaben-Kuerzeln.
 */
public class WaypointScreen extends Screen {

    private record Feld(int x, int y, int w, int h) {
        boolean drin(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private static final int ZEILE = 30;
    private static final String[] DIMS = { "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end" };

    private final Screen parent;
    private EditBox suche;

    private int wx, wy, ww, wh, listeY, listeH;
    private int mx, my;
    private float oeffnen, scroll, scrollZiel, panelScroll;
    private long letzteZeit;

    /** Angezeigte Welt; null = die, in der man steht. */
    private Welt ansicht;
    private String dim;
    private final Set<Waypoint> auswahl = new LinkedHashSet<>();
    private Waypoint anker;
    private boolean weltPanel;
    private long loeschenBis, weltLoeschenBis;
    private String status = "";
    private long statusBis;
    private boolean ablageHatMarker;
    private long ablageGeprueft;

    private Feld fAnzeige, fZahnrad, fWelt, fZurueck, fNeu, fBearbeiten, fLoeschen, fTp, fTeilen, fEinfuegen, fPanel;
    private final Feld[] fDims = new Feld[3];
    private final List<Object[]> panelZeilen = new ArrayList<>();
    private final List<Object[]> zeilen = new ArrayList<>();

    public WaypointScreen(Screen parent) {
        super(Component.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ww = Math.min(this.width - 24, 560);
        wh = Math.min(this.height - 24, 340);
        wx = (this.width - ww) / 2;
        wy = (this.height - wh) / 2;
        listeY = wy + 68;
        listeH = wh - 68 - 42;
        String alt = suche == null ? "" : suche.getValue();
        suche = new EditBox(this.font, wx + ww - 14 - 120 + 6, wy + 42, 112, 12, Component.literal("Search"));
        suche.setBordered(false);
        suche.setMaxLength(40);
        suche.setValue(alt);
        suche.setResponder(s -> scrollZiel = scroll = 0f);
        this.addRenderableWidget(suche);
        if (dim == null) {
            Minecraft mc = Minecraft.getInstance();
            String d = com.vortex.client.hud.WaypointRenderer.currentDimension(mc);
            dim = d == null ? DIMS[0] : d;
        }
        ablageGeprueft = 0;
    }

    /* ---------------------------------------------------------------- Daten */

    private WaypointWorlds.Hier hier() {
        return WaypointWorlds.hier(Minecraft.getInstance(), false);
    }

    /** Welt, deren Waypoints gezeigt werden. */
    private Welt welt() {
        if (ansicht != null && WaypointWorlds.finde(ansicht.id) != null) return ansicht;
        ansicht = null;
        WaypointWorlds.Hier h = hier();
        return h == null ? null : h.welt();
    }

    private boolean istHier(Welt w) {
        WaypointWorlds.Hier h = hier();
        return h != null && w != null && h.welt() == w;
    }

    private List<Waypoint> liste() {
        Welt w = welt();
        if (w == null) return List.of();
        List<Waypoint> l = WaypointManager.inWelt(w.id, dim);
        String q = suche == null ? "" : suche.getValue().trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) l.removeIf(p -> !p.name.toLowerCase(Locale.ROOT).contains(q) && !p.kind.label.toLowerCase(Locale.ROOT).contains(q));
        var me = Minecraft.getInstance().player;
        WaypointWorlds.Hier h = hier();
        if (me != null && h != null && h.welt() == w && dim.equals(h.dim())) {
            l.sort((a, b) -> Double.compare(abstand2(a, me.getX(), me.getZ()), abstand2(b, me.getX(), me.getZ())));
        } else {
            l.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }
        return l;
    }

    private static double abstand2(Waypoint w, double x, double z) {
        double dx = w.x + 0.5 - x, dz = w.z + 0.5 - z;
        return dx * dx + dz * dz;
    }

    private void meldung(String s) {
        status = s;
        statusBis = System.currentTimeMillis() + 3500;
    }

    /* ---------------------------------------------------------------- Zeichnen */

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX;
        my = mouseY;
        long now = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (now - letzteZeit) / 1e9f);
        letzteZeit = now;
        oeffnen += (1f - oeffnen) * (1f - (float) Math.exp(-14f * dt));
        scroll += (scrollZiel - scroll) * (1f - (float) Math.exp(-18f * dt));
        float al = oeffnen;
        int akzent = Theme.INSTANCE.accent.get() | 0xFF000000;
        Minecraft mc = Minecraft.getInstance();
        WaypointSettings cfg = WaypointSettings.INSTANCE;

        if (System.currentTimeMillis() - ablageGeprueft > 1000) {
            ablageGeprueft = System.currentTimeMillis();
            try { ablageHatMarker = WaypointManager.istGeteilt(mc.keyboardHandler.getClipboard()); } catch (Throwable e) { ablageHatMarker = false; }
        }

        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(VortexStyle.DIM, al));
        Glatt.schatten(ctx, wx, wy + 3, ww, wh, 8, 10, Glatt.alpha(0xFF000000, al * 0.5f));
        Glatt.rund(ctx, wx, wy, ww, wh, 8, Glatt.alpha(VortexStyle.WINDOW, al));
        Glatt.rahmen(ctx, wx, wy, ww, wh, 8, 1, Glatt.alpha(0x18FFFFFF, al));
        Glatt.rund(ctx, wx + 16, wy, ww - 32, 1.5f, 0.75f, Glatt.alpha(VortexStyle.akzent(0.5f), al));

        Glatt.text(ctx, "Waypoints", wx + 16, wy + 11, Glatt.alpha(VortexStyle.TEXT, al), Schrift.TITEL);

        fZahnrad = new Feld(wx + ww - 14 - 22, wy + 9, 22, 20);
        boolean zh = fZahnrad.drin(mx, my);
        Glatt.rund(ctx, fZahnrad.x, fZahnrad.y, fZahnrad.w, fZahnrad.h, 5, Glatt.alpha(zh ? VortexStyle.HOV : VortexStyle.CARD, al));
        Glatt.symbol(ctx, Symbole.Symbol.ZAHNRAD, fZahnrad.x + 5, fZahnrad.y + 4, 12, Glatt.alpha(zh ? 0xFFFFFFFF : 0xFFB8B2CC, al));

        String an = "Show in world";
        int anB = Glatt.breite(an, Schrift.NORMAL);
        fAnzeige = new Feld(fZahnrad.x - 10 - anB - 34, wy + 9, anB + 34, 20);
        boolean ah = fAnzeige.drin(mx, my);
        Glatt.text(ctx, an, fAnzeige.x, fAnzeige.y + 6, Glatt.alpha(ah ? 0xFFFFFFFF : VortexStyle.TEXT_DIM, al), Schrift.NORMAL);
        schalter(ctx, fAnzeige.x + anB + 8, fAnzeige.y + 5, cfg.enabled.get(), akzent, al);

        zeichneLeiste(ctx, akzent, al);
        zeichneListe(ctx, mc, akzent, al);
        zeichneUnten(ctx, akzent, al);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        if (suche != null && suche.isVisible() && suche.getValue().isEmpty() && !suche.isFocused()) {
            Glatt.text(ctx, "Search", suche.getX(), suche.getY() + 1, Glatt.alpha(0xFF6A6480, al), Schrift.NORMAL);
        }
        if (weltPanel) zeichnePanel(ctx, mc, akzent, al);
    }

    private void zeichneLeiste(GuiGraphicsExtractor ctx, int akzent, float al) {
        int y = wy + 38;
        Welt w = welt();
        String text = w != null ? WaypointWorlds.anzeige(w)
                : Minecraft.getInstance().level == null ? "Browse worlds" : "Detecting world...";
        boolean fremd = w != null && !istHier(w);
        int textMax = fremd ? 130 : 190;
        int tb = Math.min(Glatt.breite(text, Schrift.FETT), textMax);
        fWelt = new Feld(wx + 14, y, tb + 40, 20);
        boolean wh2 = fWelt.drin(mx, my) || weltPanel;
        Glatt.rund(ctx, fWelt.x, fWelt.y, fWelt.w, fWelt.h, 5, Glatt.alpha(wh2 ? VortexStyle.HOV : VortexStyle.CARD, al));
        Glatt.symbol(ctx, Symbole.Symbol.WELT, fWelt.x + 6, y + 4, 12, Glatt.alpha(fremd ? 0xFFFFD070 : akzent, al));
        Glatt.text(ctx, Glatt.kuerzen(text, textMax, Schrift.FETT), fWelt.x + 22, y + 6, Glatt.alpha(VortexStyle.TEXT, al), Schrift.FETT);
        Glatt.text(ctx, "▾", fWelt.x + fWelt.w - 11, y + 6, Glatt.alpha(VortexStyle.TEXT_DIM, al), Schrift.NORMAL);

        int x = fWelt.x + fWelt.w + 8;
        fZurueck = null;
        if (fremd) {
            String z = "Back to here";
            fZurueck = new Feld(x, y, Glatt.breite(z, Schrift.NORMAL) + 16, 20);
            knopf(ctx, fZurueck, z, true, false, false, akzent, al);
            x += fZurueck.w + 8;
        }

        String[] namen = { "Overworld", "Nether", "End" };
        int segB = 0;
        for (String n : namen) segB += Glatt.breite(n, Schrift.NORMAL) + 16;
        int sx = x;
        Glatt.rund(ctx, sx, y, segB + 4, 20, 5, Glatt.alpha(VortexStyle.INNER, al));
        WaypointWorlds.Hier h = hier();
        for (int i = 0; i < 3; i++) {
            int b = Glatt.breite(namen[i], Schrift.NORMAL) + 16;
            fDims[i] = new Feld(sx + 2, y + 2, b, 16);
            boolean aktiv = DIMS[i].equals(dim);
            boolean hov = fDims[i].drin(mx, my);
            if (aktiv) Glatt.rund(ctx, sx + 2, y + 2, b, 16, 4, Glatt.alpha(VortexStyle.mix(VortexStyle.HOV, akzent, 0.35f), al));
            else if (hov) Glatt.rund(ctx, sx + 2, y + 2, b, 16, 4, Glatt.alpha(VortexStyle.CARD, al));
            Glatt.text(ctx, namen[i], sx + 10, y + 6, Glatt.alpha(aktiv ? 0xFFFFFFFF : VortexStyle.TEXT_DIM, al), Schrift.NORMAL);
            if (h != null && w != null && h.welt() == w && DIMS[i].equals(h.dim())) {
                Glatt.kreis(ctx, sx + b - 2, y + 5, 3, Glatt.alpha(0xFF4ADE80, al));
            }
            sx += b;
        }

        int sw = 120;
        Feld fs = new Feld(wx + ww - 14 - sw, y, sw, 20);
        boolean passt = fs.x > sx + 8;
        boolean sf = suche != null && suche.isFocused();
        if (passt) {
            Glatt.rund(ctx, fs.x, fs.y, fs.w, fs.h, 5, Glatt.alpha(VortexStyle.INNER, al));
            if (sf) Glatt.rahmen(ctx, fs.x, fs.y, fs.w, fs.h, 5, 1, Glatt.alpha(akzent, al * 0.8f));
        }
        if (suche != null) {
            suche.setX(fs.x + 8);
            suche.setY(fs.y + 6);
            suche.setWidth(fs.w - 14);
            suche.setVisible(passt);
        }
    }

    private void zeichneListe(GuiGraphicsExtractor ctx, Minecraft mc, int akzent, float al) {
        zeilen.clear();
        Welt w = welt();
        List<Waypoint> l = liste();
        auswahl.retainAll(WaypointManager.all());
        int inhalt = l.size() * (ZEILE + 4);
        float max = Math.max(0, inhalt - listeH + 6);
        if (scrollZiel > max) scrollZiel = max;
        if (scrollZiel < 0) scrollZiel = 0;

        ctx.enableScissor(wx + 1, listeY, wx + ww - 1, listeY + listeH);
        int y = listeY + 2 - Math.round(scroll);
        WaypointWorlds.Hier h = hier();
        boolean hierDim = h != null && w != null && h.welt() == w && dim.equals(h.dim());
        var me = mc.player;
        for (Waypoint p : l) {
            if (y + ZEILE >= listeY && y <= listeY + listeH) {
                Feld f = new Feld(wx + 12, y, ww - 24, ZEILE);
                Feld sch = new Feld(f.x + f.w - 38, y + 9, 26, 12);
                zeilen.add(new Object[] { p, f, sch });
                boolean sel = auswahl.contains(p);
                boolean hov = f.drin(mx, my) && my >= listeY && my < listeY + listeH && !weltPanel;
                int grund = sel ? VortexStyle.mix(VortexStyle.CARD, akzent, 0.22f) : hov ? VortexStyle.HOV : VortexStyle.CARD;
                Glatt.rund(ctx, f.x, f.y, f.w, f.h, 6, Glatt.alpha(grund, al));
                if (sel) Glatt.rahmen(ctx, f.x, f.y, f.w, f.h, 6, 1, Glatt.alpha(akzent, al * 0.7f));

                int farbe = p.visible ? (p.color | 0xFF000000) : 0xFF56506A;
                float kx = f.x + 17, ky = y + ZEILE / 2f;
                Glatt.kreis(ctx, kx, ky, 18, Glatt.alpha(farbe, al));
                Glatt.kreis(ctx, kx, ky, 14, Glatt.alpha(VortexStyle.mix(farbe, 0xFF000000, 0.35f), al));
                String z = p.zeichen();
                Glatt.textMitte(ctx, z, kx, ky - 4, Glatt.alpha(0xFFFFFFFF, al), Schrift.FETT);

                int tx = f.x + 34;
                int nameMax = f.w - 34 - 120;
                Glatt.text(ctx, Glatt.kuerzen(p.name, nameMax, Schrift.FETT), tx, y + 6,
                        Glatt.alpha(p.visible ? VortexStyle.TEXT : 0xFF8A849C, al), Schrift.FETT);
                StringBuilder unter = new StringBuilder();
                unter.append(p.x).append(", ").append(p.y).append(", ").append(p.z);
                if (p.kind != WaypointManager.Kind.ALLGEMEIN) unter.append("  ·  ").append(p.kind.label);
                if (!p.blocks.isEmpty()) unter.append("  ·  ").append(p.blocks.size()).append(" blocks");
                if (WaypointActions.getBlockGroup() == p) unter.append("  ·  marking blocks");
                Glatt.text(ctx, Glatt.kuerzen(unter.toString(), nameMax, Schrift.NORMAL), tx, y + 17,
                        Glatt.alpha(VortexStyle.TEXT_DIM, al), Schrift.NORMAL);

                if (hierDim && me != null) {
                    String d = abstandText(Math.sqrt(abstand2(p, me.getX(), me.getZ())));
                    Glatt.textRechts(ctx, d, sch.x - 12, y + 11, Glatt.alpha(0xFFB8B2CC, al), Schrift.NORMAL);
                }
                schalter(ctx, sch.x, sch.y, p.visible, akzent, al);
            }
            y += ZEILE + 4;
        }
        ctx.disableScissor();

        if (l.isEmpty()) {
            int cy = listeY + listeH / 2 - 10;
            String a, b;
            if (w == null) {
                a = Minecraft.getInstance().level == null ? "Join a world to see its waypoints" : "Detecting world...";
                b = Minecraft.getInstance().level == null ? "Waypoints of other worlds are in the world menu." : "One moment.";
            } else if (suche != null && !suche.getValue().isBlank()) {
                a = "Nothing found";
                b = "No waypoint matches your search.";
            } else {
                a = "No waypoints in the " + WaypointWorlds.dimName(dim) + " yet";
                b = "Click New, or type /wp add <name>.";
            }
            Glatt.textMitte(ctx, a, wx + ww / 2f, cy, Glatt.alpha(VortexStyle.TEXT, al), Schrift.FETT);
            Glatt.textMitte(ctx, b, wx + ww / 2f, cy + 13, Glatt.alpha(VortexStyle.TEXT_DIM, al), Schrift.NORMAL);
        }
        if (inhalt > listeH) {
            int bahn = listeH - 4;
            int bh = Math.max(20, (int) (bahn * (listeH / (float) inhalt)));
            int by = listeY + 2 + (int) ((bahn - bh) * (scroll / Math.max(1f, max)));
            Glatt.rund(ctx, wx + ww - 7, by, 3, bh, 1.5f, Glatt.alpha(0x40FFFFFF, al));
        }
    }

    private static String abstandText(double m) {
        if (m >= 10000) return String.format(Locale.ROOT, "%.1f km", m / 1000.0);
        return (int) m + " m";
    }

    private void zeichneUnten(GuiGraphicsExtractor ctx, int akzent, float al) {
        int y = wy + wh - 32;
        Glatt.rund(ctx, wx + 1, y - 8, ww - 2, 1, 0, Glatt.alpha(VortexStyle.LINE, al));
        int n = auswahl.size();
        boolean eins = n == 1;
        Welt w = welt();
        int x = wx + 14;
        fNeu = knopfAuto(ctx, x, y, "+ New", w != null, true, false, akzent, al);
        x += fNeu.w + 6;
        fBearbeiten = knopfAuto(ctx, x, y, "Edit", eins, false, false, akzent, al);
        x += fBearbeiten.w + 6;
        boolean bestaetigen = System.currentTimeMillis() < loeschenBis;
        String del = bestaetigen ? (n > 1 ? "Delete " + n + "?" : "Really delete?") : (n > 1 ? "Delete " + n : "Delete");
        fLoeschen = knopfAuto(ctx, x, y, del, n > 0, false, bestaetigen, akzent, al);
        x += fLoeschen.w + 6;
        fTp = knopfAuto(ctx, x, y, "Teleport", eins && istHier(w), false, false, akzent, al);
        x += fTp.w + 6;
        fTeilen = knopfAuto(ctx, x, y, "Share", eins, false, false, akzent, al);
        x += fTeilen.w + 6;

        String ein = "Paste";
        int eb = Glatt.breite(ein, Schrift.NORMAL) + 18;
        fEinfuegen = new Feld(wx + ww - 14 - eb, y, eb, 22);
        if (fEinfuegen.x > x + 4) {
            knopf(ctx, fEinfuegen, ein, ablageHatMarker && w != null, false, false, akzent, al);
        } else {
            fEinfuegen = null;
        }

        if (!status.isEmpty() && System.currentTimeMillis() < statusBis) {
            int sb = Glatt.breite(status, Schrift.NORMAL) + 20;
            float sx = wx + (ww - sb) / 2f, sy = y - 30;
            Glatt.rund(ctx, sx, sy, sb, 18, 9, Glatt.alpha(0xF0221C33, al));
            Glatt.rahmen(ctx, sx, sy, sb, 18, 9, 1, Glatt.alpha(akzent, al * 0.5f));
            Glatt.text(ctx, status, sx + 10, sy + 5, Glatt.alpha(VortexStyle.TEXT, al), Schrift.NORMAL);
        }
    }

    private void zeichnePanel(GuiGraphicsExtractor ctx, Minecraft mc, int akzent, float al) {
        panelZeilen.clear();
        int pw = 280;
        int px = fWelt.x, py = fWelt.y + fWelt.h + 4;
        int maxH = wy + wh - 10 - py;

        String ort = WaypointWorlds.aktuellerOrt(mc);
        Welt hierWelt = hier() == null ? null : hier().welt();
        List<Object[]> eintraege = new ArrayList<>();
        if (ort != null) {
            eintraege.add(new Object[] { "kopf", WaypointWorlds.einzelspieler(ort) ? "This world" : "Worlds on " + WaypointWorlds.ortName(ort) });
            for (Welt w : WaypointWorlds.amOrt(ort)) eintraege.add(new Object[] { "welt", w });
            if (!WaypointWorlds.einzelspieler(ort)) eintraege.add(new Object[] { "neu", "+ New world here" });
        }
        boolean andereKopf = false;
        for (String o : WaypointWorlds.orte()) {
            if (o.equals(ort)) continue;
            for (Welt w : WaypointWorlds.amOrt(o)) {
                if (WaypointManager.anzahlIn(w.id) == 0 && WaypointWorlds.OHNE_ORT.equals(o)) continue;
                if (!andereKopf) {
                    eintraege.add(new Object[] { "kopf", "Other servers and worlds" });
                    andereKopf = true;
                }
                eintraege.add(new Object[] { "welt", w });
            }
        }

        int inhaltH = 0;
        for (Object[] e : eintraege) inhaltH += "kopf".equals(e[0]) ? 18 : 22;
        Welt v = welt();
        boolean aktionen = v != null;
        int unten = aktionen ? 30 : 6;
        int ph = Math.min(maxH, inhaltH + unten + 8);
        fPanel = new Feld(px, py, pw, ph);
        float maxScroll = Math.max(0, inhaltH - (ph - unten - 8));
        panelScroll = Math.max(0, Math.min(panelScroll, maxScroll));

        Glatt.schatten(ctx, px, py + 2, pw, ph, 7, 8, Glatt.alpha(0xFF000000, al * 0.6f));
        Glatt.rund(ctx, px, py, pw, ph, 7, Glatt.alpha(0xFF15111F, al));
        Glatt.rahmen(ctx, px, py, pw, ph, 7, 1, Glatt.alpha(0x22FFFFFF, al));

        ctx.enableScissor(px, py + 4, px + pw, py + ph - unten);
        int y = py + 6 - Math.round(panelScroll);
        for (Object[] e : eintraege) {
            if ("kopf".equals(e[0])) {
                Glatt.text(ctx, ((String) e[1]).toUpperCase(Locale.ROOT), px + 10, y + 6, Glatt.alpha(VortexStyle.TEXT_DIM, al), Schrift.FETT);
                y += 18;
                continue;
            }
            Feld f = new Feld(px + 4, y, pw - 8, 20);
            boolean sichtbar = y + 20 > py + 4 && y < py + ph - unten;
            boolean hov = sichtbar && f.drin(mx, my);
            if ("neu".equals(e[0])) {
                if (hov) Glatt.rund(ctx, f.x, f.y, f.w, f.h, 4, Glatt.alpha(VortexStyle.HOV, al));
                Glatt.text(ctx, (String) e[1], f.x + 8, y + 6, Glatt.alpha(akzent, al), Schrift.FETT);
                if (sichtbar) panelZeilen.add(new Object[] { "neu", f });
                y += 22;
                continue;
            }
            Welt w = (Welt) e[1];
            boolean gezeigt = w == v;
            if (gezeigt) Glatt.rund(ctx, f.x, f.y, f.w, f.h, 4, Glatt.alpha(VortexStyle.mix(VortexStyle.HOV, akzent, 0.25f), al));
            else if (hov) Glatt.rund(ctx, f.x, f.y, f.w, f.h, 4, Glatt.alpha(VortexStyle.HOV, al));
            String name = ort != null && ort.equals(w.ort) ? w.name : WaypointWorlds.anzeige(w);
            if (WaypointWorlds.einzelspieler(w.ort) && ort != null && ort.equals(w.ort)) name = WaypointWorlds.ortName(w.ort);
            Glatt.text(ctx, Glatt.kuerzen(name, pw - 110, Schrift.NORMAL), f.x + 8, y + 6, Glatt.alpha(VortexStyle.TEXT, al), Schrift.NORMAL);
            int anz = WaypointManager.anzahlIn(w.id);
            String rechts = anz == 1 ? "1 waypoint" : anz + " waypoints";
            Glatt.textRechts(ctx, rechts, f.x + f.w - 8, y + 6, Glatt.alpha(VortexStyle.TEXT_DIM, al), Schrift.NORMAL);
            if (w == hierWelt) {
                Glatt.kreis(ctx, f.x + f.w - 14 - Glatt.breite(rechts, Schrift.NORMAL), y + 10, 5, Glatt.alpha(0xFF4ADE80, al));
            }
            if (sichtbar) panelZeilen.add(new Object[] { "welt", f, w });
            y += 22;
        }
        ctx.disableScissor();

        if (aktionen) {
            int ay = py + ph - 26;
            Glatt.rund(ctx, px + 1, ay - 3, pw - 2, 1, 0, Glatt.alpha(VortexStyle.LINE, al));
            int ax = px + 6;
            Feld fu = knopfAuto(ctx, ax, ay, "Rename", true, false, false, akzent, al, 20);
            panelZeilen.add(new Object[] { "umbenennen", fu, v });
            ax += fu.w + 4;
            if (!istHier(v) && WaypointManager.anzahlIn(v.id) > 0 && hierWelt != null) {
                Feld fv = knopfAuto(ctx, ax, ay, "Move all here", true, false, false, akzent, al, 20);
                panelZeilen.add(new Object[] { "verschieben", fv, v });
                ax += fv.w + 4;
            }
            boolean best = System.currentTimeMillis() < weltLoeschenBis;
            Feld fl = knopfAuto(ctx, ax, ay, best ? "Really?" : "Delete", !istHier(v) || WaypointManager.anzahlIn(v.id) > 0, false, best, akzent, al, 20);
            if (fl.x + fl.w <= px + pw - 4) panelZeilen.add(new Object[] { "loeschen", fl, v });
        }
    }

    private void schalter(GuiGraphicsExtractor ctx, int x, int y, boolean an, int akzent, float al) {
        Glatt.rund(ctx, x, y, 22, 11, 5.5f, Glatt.alpha(an ? akzent : VortexStyle.TRACK, al));
        Glatt.kreis(ctx, x + (an ? 16.5f : 5.5f), y + 5.5f, 8, Glatt.alpha(an ? 0xFFFFFFFF : 0xFFB8B2CC, al));
    }

    private Feld knopfAuto(GuiGraphicsExtractor ctx, int x, int y, String text, boolean aktiv, boolean haupt, boolean gefahr, int akzent, float al) {
        return knopfAuto(ctx, x, y, text, aktiv, haupt, gefahr, akzent, al, 22);
    }

    private Feld knopfAuto(GuiGraphicsExtractor ctx, int x, int y, String text, boolean aktiv, boolean haupt, boolean gefahr, int akzent, float al, int h) {
        Feld f = new Feld(x, y, Glatt.breite(text, Schrift.NORMAL) + 18, h);
        knopf(ctx, f, text, aktiv, haupt, gefahr, akzent, al);
        return f;
    }

    private void knopf(GuiGraphicsExtractor ctx, Feld f, String text, boolean aktiv, boolean haupt, boolean gefahr, int akzent, float al) {
        boolean hov = aktiv && f.drin(mx, my) && !(weltPanel && fPanel != null && fPanel.drin(mx, my) && f.y < fPanel.y);
        int grund = gefahr ? 0xFFB91C1C : haupt ? akzent : VortexStyle.CARD;
        if (hov) grund = VortexStyle.mix(grund, 0xFFFFFFFF, haupt || gefahr ? 0.12f : 0.06f);
        if (!aktiv) grund = VortexStyle.mix(VortexStyle.CARD, VortexStyle.WINDOW, 0.5f);
        Glatt.rund(ctx, f.x, f.y, f.w, f.h, 5, Glatt.alpha(grund, al));
        if (!haupt && !gefahr && aktiv) Glatt.rahmen(ctx, f.x, f.y, f.w, f.h, 5, 1, Glatt.alpha(0x14FFFFFF, al));
        int tf = !aktiv ? 0xFF5E5872 : 0xFFFFFFFF;
        Glatt.textMitte(ctx, text, f.x + f.w / 2f, f.y + (f.h - 9) / 2f + 1, Glatt.alpha(tf, al), Schrift.NORMAL);
    }

    /* ---------------------------------------------------------------- Bedienung */

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double cx = click.x(), cy = click.y();
        Minecraft mc = Minecraft.getInstance();

        if (weltPanel) {
            if (fPanel != null && fPanel.drin(cx, cy)) {
                panelKlick(mc, cx, cy);
                return true;
            }
            weltPanel = false;
            if (fWelt != null && fWelt.drin(cx, cy)) return true;
        }

        if (super.mouseClicked(click, doubled)) return true;
        if (suche != null && suche.isFocused()) suche.setFocused(false);

        WaypointSettings cfg = WaypointSettings.INSTANCE;
        if (fAnzeige != null && fAnzeige.drin(cx, cy)) {
            cfg.enabled.toggle();
            ConfigManager.save();
            meldung(cfg.enabled.get() ? "Waypoints are shown in the world." : "Waypoints are hidden in the world.");
            return true;
        }
        if (fZahnrad != null && fZahnrad.drin(cx, cy)) {
            mc.gui.setScreen(new SettingsScreen(this, "Waypoint Settings", cfg.getSettings()));
            return true;
        }
        if (fWelt != null && fWelt.drin(cx, cy)) {
            weltPanel = true;
            panelScroll = 0;
            return true;
        }
        if (fZurueck != null && fZurueck.drin(cx, cy)) {
            ansicht = null;
            auswahl.clear();
            WaypointWorlds.Hier h = hier();
            if (h != null) dim = h.dim();
            return true;
        }
        for (int i = 0; i < 3; i++) {
            if (fDims[i] != null && fDims[i].drin(cx, cy)) {
                dim = DIMS[i];
                auswahl.clear();
                scroll = scrollZiel = 0;
                return true;
            }
        }

        if (cy >= listeY && cy < listeY + listeH) {
            for (Object[] z : zeilen) {
                Waypoint p = (Waypoint) z[0];
                Feld f = (Feld) z[1], sch = (Feld) z[2];
                if (!f.drin(cx, cy)) continue;
                if (sch.drin(cx, cy)) {
                    p.visible = !p.visible;
                    ConfigManager.save();
                    return true;
                }
                if (doubled) {
                    bearbeiten(p);
                    return true;
                }
                waehlen(p, click.hasControlDown(), click.hasShiftDown());
                return true;
            }
            auswahl.clear();
            return true;
        }

        Welt w = welt();
        if (fNeu != null && fNeu.drin(cx, cy) && w != null) {
            mc.gui.setScreen(new WaypointEditScreen(this, null, w.id, dim));
            return true;
        }
        if (fBearbeiten != null && fBearbeiten.drin(cx, cy) && auswahl.size() == 1) {
            bearbeiten(auswahl.iterator().next());
            return true;
        }
        if (fLoeschen != null && fLoeschen.drin(cx, cy) && !auswahl.isEmpty()) {
            loeschen();
            return true;
        }
        if (fTp != null && fTp.drin(cx, cy) && auswahl.size() == 1 && istHier(w)) {
            teleport(auswahl.iterator().next());
            return true;
        }
        if (fTeilen != null && fTeilen.drin(cx, cy) && auswahl.size() == 1) {
            mc.keyboardHandler.setClipboard(WaypointManager.export(auswahl.iterator().next()));
            ablageGeprueft = 0;
            meldung("Copied. Others paste it with the Paste button.");
            return true;
        }
        if (fEinfuegen != null && fEinfuegen.drin(cx, cy) && w != null && ablageHatMarker) {
            Waypoint neu = WaypointManager.importFrom(mc.keyboardHandler.getClipboard(), w.id, dim);
            if (neu == null) {
                meldung("The clipboard holds no waypoint.");
            } else {
                ConfigManager.save();
                dim = neu.dim;
                auswahl.clear();
                auswahl.add(neu);
                meldung("Added: " + neu.name);
            }
            return true;
        }
        return false;
    }

    private void waehlen(Waypoint p, boolean strg, boolean umschalt) {
        if (umschalt && anker != null) {
            List<Waypoint> l = liste();
            int a = l.indexOf(anker), b = l.indexOf(p);
            if (a >= 0 && b >= 0) {
                if (!strg) auswahl.clear();
                for (int i = Math.min(a, b); i <= Math.max(a, b); i++) auswahl.add(l.get(i));
                return;
            }
        }
        if (strg) {
            if (!auswahl.remove(p)) auswahl.add(p);
        } else {
            auswahl.clear();
            auswahl.add(p);
        }
        anker = p;
        loeschenBis = 0;
    }

    private void panelKlick(Minecraft mc, double cx, double cy) {
        for (Object[] z : panelZeilen) {
            Feld f = (Feld) z[1];
            if (!f.drin(cx, cy)) continue;
            String art = (String) z[0];
            switch (art) {
                case "neu" -> {
                    Welt w = WaypointWorlds.neueHier(mc);
                    if (w != null) {
                        ConfigManager.save();
                        ansicht = null;
                        auswahl.clear();
                        meldung("New world \"" + w.name + "\" -- this server now uses it.");
                    }
                    weltPanel = false;
                }
                case "welt" -> {
                    Welt w = (Welt) z[2];
                    String ort = WaypointWorlds.aktuellerOrt(mc);
                    if (ort != null && ort.equals(w.ort)) {
                        if (!istHier(w)) {
                            WaypointWorlds.hierIst(mc, w);
                            ConfigManager.save();
                            meldung("You are in \"" + w.name + "\" now. Remembered for next time.");
                        }
                        ansicht = null;
                        WaypointWorlds.Hier h = hier();
                        if (h != null) dim = h.dim();
                    } else {
                        ansicht = w;
                        List<Waypoint> l = WaypointManager.inWelt(w.id, null);
                        if (!l.isEmpty() && WaypointManager.inWelt(w.id, dim).isEmpty()) dim = l.get(0).dim;
                    }
                    auswahl.clear();
                    scroll = scrollZiel = 0;
                    weltPanel = false;
                }
                case "umbenennen" -> {
                    Welt w = (Welt) z[2];
                    weltPanel = false;
                    mc.gui.setScreen(new TextEingabeScreen(this, "Rename world", w.name,
                            "Only you see this name.", s -> {
                                WaypointWorlds.umbenennen(w, s);
                                ConfigManager.save();
                            }));
                }
                case "verschieben" -> {
                    Welt von = (Welt) z[2];
                    WaypointWorlds.Hier h = hier();
                    if (h == null) return;
                    int n = WaypointManager.verschieben(von.id, h.welt().id);
                    WaypointWorlds.leereAufraeumen(von);
                    ConfigManager.save();
                    ansicht = null;
                    dim = h.dim();
                    weltPanel = false;
                    meldung(n + (n == 1 ? " waypoint" : " waypoints") + " moved to this world.");
                }
                case "loeschen" -> {
                    Welt w = (Welt) z[2];
                    if (System.currentTimeMillis() < weltLoeschenBis) {
                        weltLoeschenBis = 0;
                        int n = WaypointManager.anzahlIn(w.id);
                        WaypointWorlds.loeschen(w);
                        ConfigManager.save();
                        ansicht = null;
                        weltPanel = false;
                        meldung("World deleted" + (n > 0 ? " with " + n + (n == 1 ? " waypoint." : " waypoints.") : "."));
                    } else {
                        weltLoeschenBis = System.currentTimeMillis() + 3000;
                    }
                }
                default -> { }
            }
            return;
        }
    }

    private void bearbeiten(Waypoint p) {
        Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(this, p, p.welt, p.dim));
    }

    private void loeschen() {
        if (System.currentTimeMillis() >= loeschenBis) {
            loeschenBis = System.currentTimeMillis() + 3000;
            return;
        }
        loeschenBis = 0;
        int n = auswahl.size();
        for (Waypoint p : auswahl) {
            if (WaypointActions.getBlockGroup() == p) WaypointActions.setBlockGroup(null);
            WaypointManager.remove(p);
        }
        auswahl.clear();
        ConfigManager.save();
        meldung(n == 1 ? "Waypoint deleted." : n + " waypoints deleted.");
    }

    /**
     * Teleport: ein Client kann nicht selbst teleportieren, es geht nur der
     * Befehl an den Server. Ohne Rechte lehnt er ab; die Koordinaten liegen dann
     * in der Zwischenablage.
     */
    private void teleport(Waypoint p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        mc.keyboardHandler.setClipboard(p.x + " " + p.y + " " + p.z);
        try {
            mc.getConnection().sendCommand("tp " + p.x + " " + p.y + " " + p.z);
            onClose();
        } catch (Throwable e) {
            meldung("Teleport not possible -- coordinates copied.");
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (suche != null && suche.isFocused()) {
            if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
                suche.setFocused(false);
                return true;
            }
            return super.keyPressed(e);
        }
        if (weltPanel && e.key() == GLFW.GLFW_KEY_ESCAPE) {
            weltPanel = false;
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_DELETE && !auswahl.isEmpty()) {
            if (System.currentTimeMillis() >= loeschenBis) loeschenBis = System.currentTimeMillis() + 3000;
            else loeschen();
            return true;
        }
        if ((e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) && auswahl.size() == 1) {
            bearbeiten(auswahl.iterator().next());
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_A && e.hasControlDown()) {
            auswahl.clear();
            auswahl.addAll(liste());
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_F && e.hasControlDown() && suche != null) {
            setFocused(suche);
            suche.setFocused(true);
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (weltPanel && fPanel != null && fPanel.drin(mouseX, mouseY)) {
            panelScroll -= (float) vertical * 22f;
            return true;
        }
        scrollZiel -= (float) vertical * (ZEILE + 4);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
