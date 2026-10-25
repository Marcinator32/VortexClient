package com.vortex.client.gui;

import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.KnopfStil;
import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-Design (seit 4.69): Farben fuer den ganzen Client waehlen.
 *
 * Links fertige Designs (Vortex, Ocean, Sunset ... Rainbow) und "Custom" mit 2 oder 3
 * eigenen Farben aus dem Farbwaehler, darunter Schalter (fliessender Verlauf, HUD mit
 * umfaerben, getoente Hintergruende). Rechts eine Vorschau mit Logo, Knopf, Schalter und
 * Regler -- in den Farben, die gerade gelten (es ist dieselbe Umrechnung wie ueberall).
 * Jede Wahl gilt sofort in allen Menues.
 *
 * Seit 4.90 darunter "Minecraft menus": wie Minecrafts eigene Menues aussehen (Knopf-Stil,
 * Ecken, Fuellung, ob in allen Minecraft-Menues) -- mit drei Beispiel-Knoepfen.
 */
public class DesignScreen extends Screen {
    private static final int KOPF = 40, KARTE_W = 92, KARTE_H = 52, LUECKE = 6;

    private final Screen parent;
    private record Ziel(int x, int y, int w, int h, Runnable aktion) {}
    private final List<Ziel> ziele = new ArrayList<>();
    private final long offen = System.currentTimeMillis();
    /** Bildlauf (seit 4.79): bei kleinen Fenstern bzw. grosser GUI-Skalierung passt nicht alles. */
    private float lauf = 0f, laufZiel = 0f;
    private int inhaltH = 0;

    public DesignScreen(Screen parent) {
        super(Component.literal("Client Design"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(this.width - 70, 10, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> { Farbthema.zuruecksetzen(); KnopfStil.zuruecksetzen(); }).bounds(this.width - 134, 10, 58, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        ziele.clear();
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        g.text(this.font, "Client Design", 12, 12, VortexStyle.TEXT, true);
        g.text(this.font, "Colors for every menu, the logo and the HUD", 12, 24, VortexStyle.TEXT_DIM, false);
        VortexStyle.akzentLinie(g, 8, KOPF - 4, this.width - 16, 1f);

        int vorschauW = this.width >= 520 ? 190 : 0;
        int sicht = this.height - KOPF - 4;
        float maxLauf = Math.max(0, inhaltH - sicht + 10);
        laufZiel = Math.max(0, Math.min(maxLauf, laufZiel));
        lauf += (laufZiel - lauf) * 0.35f;
        if (Math.abs(laufZiel - lauf) < 0.5f) lauf = laufZiel;
        int start = KOPF + 6 - Math.round(lauf);
        g.enableScissor(0, KOPF - 2, this.width, this.height);
        int x0 = 12, y = start, breite = this.width - 24 - (vorschauW > 0 ? vorschauW + 12 : 0);
        int spalten = Math.max(1, (breite + LUECKE) / (KARTE_W + LUECKE));

        g.text(this.font, "Designs", x0, y, VortexStyle.TEXT, true);
        y += 13;
        String gew = Farbthema.vorlage();
        int i = 0;
        for (Farbthema.Vorlage v : Farbthema.VORLAGEN) {
            int x = x0 + (i % spalten) * (KARTE_W + LUECKE), yy = y + (i / spalten) * (KARTE_H + LUECKE);
            karte(g, x, yy, v.name(), v.farben(), v.fliessend() || v.id().equals("rainbow"), v.id().equals(gew), mouseX, mouseY,
                    () -> Farbthema.waehle(v.id()));
            i++;
        }
        y += ((Farbthema.VORLAGEN.size() + spalten - 1) / spalten) * (KARTE_H + LUECKE) + 6;

        g.text(this.font, "Custom", x0, y, VortexStyle.TEXT, true);
        g.text(this.font, "click a color to change it", x0 + this.font.width("Custom") + 8, y, VortexStyle.TEXT_DIM, false);
        y += 13;
        int[] eigene = Farbthema.eigeneFarben();
        boolean custom = "custom".equals(gew);
        karte(g, x0, y, "Custom", eigene, Farbthema.fliessend(), custom, mouseX, mouseY, () -> Farbthema.setzeEigene(Farbthema.eigeneFarben()));
        int sx = x0 + KARTE_W + LUECKE + 4;
        for (int k = 0; k < eigene.length; k++) {
            final int nr = k;
            int bx = sx + k * 40;
            boolean ueber = drin(mouseX, mouseY, bx, y + 6, 32, 32);
            g.fill(bx - 1, y + 5, bx + 33, y + 39, ueber ? VortexStyle.TEXT : VortexStyle.LINE);
            Farbthema.rohAn();
            try { g.fill(bx, y + 6, bx + 32, y + 38, 0xFF000000 | eigene[k]); } finally { Farbthema.rohAus(); }
            g.text(this.font, String.valueOf(k + 1), bx + 13, y + 42, VortexStyle.TEXT_DIM, false);
            ziele.add(new Ziel(bx, y + 6, 32, 32, () -> farbeWaehlen(nr)));
        }
        int px = sx + eigene.length * 40 + 4;
        String plus = eigene.length < 3 ? "+ color" : "- color";
        knopf(g, px, y + 14, plus, mouseX, mouseY, () -> {
            int[] f = Farbthema.eigeneFarben();
            if (f.length < 3) Farbthema.setzeEigene(new int[] { f[0], Farbthema.verlauf(f, 0.5f) | 0x101010, f[1] });
            else Farbthema.setzeEigene(new int[] { f[0], f[2] });
        });
        y += KARTE_H + 12;

        g.text(this.font, "Options", x0, y, VortexStyle.TEXT, true);
        y += 14;
        int sxx = x0;
        String[] namen = { "Flowing gradient", "Theme the HUD", "Tinted backgrounds" };
        boolean[] an = { Farbthema.fliessend(), Farbthema.mitHud(), Farbthema.getoent() };
        Runnable[] tun = { () -> Farbthema.setzeFliessend(!Farbthema.fliessend()), () -> Farbthema.setzeHud(!Farbthema.mitHud()),
                () -> Farbthema.setzeGetoent(!Farbthema.getoent()) };
        for (int k = 0; k < namen.length; k++) {
            int bw = 22 + 6 + this.font.width(namen[k]);
            if (sxx > x0 && sxx + bw > x0 + breite) { sxx = x0; y += 18; }
            sxx = schalter(g, sxx, y, namen[k], an[k], mouseX, mouseY, tun[k]);
        }
        y += 18;

        y += 8;
        g.text(this.font, "Minecraft menus", x0, y, VortexStyle.TEXT, true);
        g.text(this.font, "Singleplayer, Multiplayer, pause menu, options ...", x0 + this.font.width("Minecraft menus") + 8, y, VortexStyle.TEXT_DIM, false);
        y += 14;
        String[] stile = new String[KnopfStil.Stil.values().length];
        for (int k = 0; k < stile.length; k++) stile[k] = KnopfStil.Stil.values()[k].name;
        y = reihe(g, x0, y, breite, "Buttons", stile, KnopfStil.stil().ordinal(), mouseX, mouseY, k -> KnopfStil.setzeStil(KnopfStil.Stil.values()[k]));
        boolean rund = KnopfStil.stil() == KnopfStil.Stil.RUND || KnopfStil.stil() == KnopfStil.Stil.NEON;
        if (rund) {
            y = reihe(g, x0, y, breite, "Corners", KnopfStil.ECKEN, KnopfStil.ecken(), mouseX, mouseY, KnopfStil::setzeEcken);
            String[] fuell = new String[KnopfStil.Fuellung.values().length];
            for (int k = 0; k < fuell.length; k++) fuell[k] = KnopfStil.Fuellung.values()[k].name;
            y = reihe(g, x0, y, breite, "Fill", fuell, KnopfStil.fuellung().ordinal(), mouseX, mouseY, k -> KnopfStil.setzeFuellung(KnopfStil.Fuellung.values()[k]));
        }
        if (KnopfStil.stil() != KnopfStil.Stil.ORIGINAL) {
            schalter(g, x0, y, "In every Minecraft menu (options, controls, video ...)", KnopfStil.ueberall(), mouseX, mouseY,
                    () -> KnopfStil.setzeUeberall(!KnopfStil.ueberall()));
            y += 20;
        }
        int bw = Math.max(60, Math.min(120, (breite - 12) / 3));
        String[] beispiel = { "Singleplayer", "Multiplayer", "Realms" };
        for (int k = 0; k < 3; k++) {
            int bx = x0 + k * (bw + 6);
            boolean aktiv = k != 2;
            float hv = MenuStil.hover(beispielHover[k], aktiv && drin(mouseX, mouseY, bx, y, bw, 20));
            MenuStil.beispielKnopf(g, bx, y, bw, 20, beispiel[k], hv, aktiv);
        }
        y += 24;
        g.text(this.font, "Point at a button to see it light up. The colors come from your design above.", x0, y, VortexStyle.TEXT_DIM, false);
        y += 12;
        if (!com.vortex.client.core.ClientSettings.INSTANCE.modernMenus.get()) {
            g.text(this.font, "\"Modern Menus\" is off in Client Settings - Minecraft keeps its own look.", x0, y, 0xFFF59E0B, false);
            y += 12;
        }
        inhaltH = y - start;

        if (vorschauW > 0) vorschau(g, this.width - 12 - vorschauW, start, vorschauW, Math.min(this.height - KOPF - 30, 230));
        g.disableScissor();
        if (maxLauf > 0) {
            int spur = sicht - 8;
            int balken = Math.max(16, Math.round(spur * sicht / (float) (inhaltH + 10)));
            int by = KOPF + 2 + Math.round((spur - balken) * (lauf / maxLauf));
            g.fill(this.width - 4, by, this.width - 2, by + balken, VortexStyle.akzent(0.5f));
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /** Eine Design-Karte: Verlauf als Streifen (echte Farben), Name darunter. */
    private void karte(GuiGraphicsExtractor g, int x, int y, String name, int[] farben, boolean fliessend, boolean an, int mx, int my, Runnable aktion) {
        boolean ueber = drin(mx, my, x, y, KARTE_W, KARTE_H);
        g.fill(x - 1, y - 1, x + KARTE_W + 1, y + KARTE_H + 1, an ? VortexStyle.akzent(0.3f) : ueber ? VortexStyle.VIOLETT : VortexStyle.LINE);
        g.fill(x, y, x + KARTE_W, y + KARTE_H, an || ueber ? VortexStyle.HOV : VortexStyle.CARD);
        float zeit = (System.currentTimeMillis() % 100000L) / 1000f;
        Farbthema.rohAn();
        try {
            int sw = KARTE_W - 12;
            for (int k = 0; k < sw; k += 2) {
                float t = k / (float) (sw - 1);
                int c;
                if (name.equals("Rainbow")) c = Farbthema.hslZuRgb(((t * 300f + zeit * 45f) % 360f), 0.85f, 0.6f);
                else c = Farbthema.verlauf(farben, fliessend ? 0.5f - 0.5f * (float) Math.cos(Math.PI * 2 * (t * 0.5f + zeit * 0.12f)) : t);
                g.fill(x + 6 + k, y + 6, x + 6 + Math.min(sw, k + 2), y + 26, 0xFF000000 | c);
            }
        } finally {
            Farbthema.rohAus();
        }
        g.text(this.font, name, x + 6, y + 32, an ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, false);
        if (an) g.text(this.font, "Selected", x + 6, y + 42, VortexStyle.akzent(0.5f), false);
        else if (fliessend) g.text(this.font, "Flowing", x + 6, y + 42, VortexStyle.fade(VortexStyle.TEXT_DIM, 0.7f), false);
        ziele.add(new Ziel(x, y, KARTE_W, KARTE_H, aktion));
    }

    /** Platzhalter fuer den weichen Hover der Beispiel-Knoepfe. */
    private final Object[] beispielHover = { new Object(), new Object(), new Object() };

    /** Eine Reihe zum Auswaehlen (Titel links, Chips rechts); liefert das y darunter. */
    private int reihe(GuiGraphicsExtractor g, int x0, int y, int breite, String titel, String[] namen, int gewaehlt, int mx, int my,
                      java.util.function.IntConsumer waehlen) {
        g.text(this.font, titel, x0, y + 5, VortexStyle.TEXT_DIM, false);
        int links = x0 + 58, x = links;
        for (int k = 0; k < namen.length; k++) {
            final int nr = k;
            int w = this.font.width(namen[k]) + 16;
            if (x > links && x + w > x0 + breite) { x = links; y += 20; }
            boolean an = k == gewaehlt, ueber = drin(mx, my, x, y, w, 16);
            g.fill(x - 1, y - 1, x + w + 1, y + 17, an ? VortexStyle.akzent(0.3f) : ueber ? VortexStyle.VIOLETT : VortexStyle.LINE);
            g.fill(x, y, x + w, y + 16, an || ueber ? VortexStyle.HOV : VortexStyle.CARD);
            g.text(this.font, namen[k], x + 8, y + 4, an ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, false);
            ziele.add(new Ziel(x, y, w, 16, () -> waehlen.accept(nr)));
            x += w + 5;
        }
        return y + 22;
    }

    private void knopf(GuiGraphicsExtractor g, int x, int y, String text, int mx, int my, Runnable aktion) {
        int w = this.font.width(text) + 14;
        boolean ueber = drin(mx, my, x, y, w, 18);
        g.fill(x, y, x + w, y + 18, ueber ? VortexStyle.HOV : VortexStyle.CARD);
        g.fill(x, y + 17, x + w, y + 18, VortexStyle.akzent(0.4f));
        g.text(this.font, text, x + 7, y + 5, VortexStyle.TEXT, false);
        ziele.add(new Ziel(x, y, w, 18, aktion));
    }

    /** Schalter mit Beschriftung; liefert die x-Position fuer den naechsten. */
    private int schalter(GuiGraphicsExtractor g, int x, int y, String text, boolean an, int mx, int my, Runnable aktion) {
        int w = 22 + 6 + this.font.width(text);
        boolean ueber = drin(mx, my, x, y, w, 12);
        Glatt.rund(g, x, y, 22, 12, 6, an ? VortexStyle.akzent(0.2f) : VortexStyle.TRACK);
        Glatt.kreis(g, an ? x + 16 : x + 6, y + 6, 9, VortexStyle.TEXT);
        g.text(this.font, text, x + 28, y + 2, ueber ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, false);
        ziele.add(new Ziel(x, y, w, 12, aktion));
        return x + w + 16;
    }

    /** So sieht es aus: Logo, Akzentlinie, Knopf, Schalter, Regler, Liste -- mit dem aktuellen Design. */
    private void vorschau(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, VortexStyle.WINDOW);
        VortexStyle.akzentLinie(g, x, y, w, 1f);
        float auf = Math.min(1f, (System.currentTimeMillis() - offen) / 600f);
        Glatt.logo(g, x + w / 2f - 22, y + 12, 44, 1f, auf);
        Glatt.textMitte(g, "Vortex", x + w / 2f, y + 62, VortexStyle.TEXT, Glatt.Schrift.FETT);
        int yy = y + 80;
        Glatt.rund(g, x + 12, yy, w - 24, 20, 5, VortexStyle.CARD);
        Glatt.rund(g, x + 12, yy, 4, 20, 2, VortexStyle.akzent(0f));
        g.text(this.font, "Mods", x + 22, yy + 6, VortexStyle.TEXT, false);
        g.text(this.font, "41/97", x + w - 16 - this.font.width("41/97"), yy + 6, VortexStyle.akzent(0.6f), false);
        yy += 28;
        Glatt.rund(g, x + 12, yy, 22, 12, 6, VortexStyle.akzent(0.2f));
        Glatt.kreis(g, x + 28, yy + 6, 9, VortexStyle.TEXT);
        g.text(this.font, "Enabled", x + 40, yy + 2, VortexStyle.TEXT, false);
        yy += 20;
        g.fill(x + 12, yy + 3, x + w - 12, yy + 5, VortexStyle.TRACK);
        float wert = 0.5f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 900.0);
        int sl = x + 12 + Math.round((w - 24) * wert);
        for (int k = x + 12; k < sl; k += 2) g.fill(k, yy + 3, Math.min(sl, k + 2), yy + 5, VortexStyle.akzent((k - x - 12) / (float) (w - 24)));
        Glatt.kreis(g, sl, yy + 4, 9, VortexStyle.TEXT);
        yy += 16;
        String[] namen = { "Keystrokes", "Armor HUD", "Freelook" };
        for (int k = 0; k < namen.length && yy + 16 < y + h; k++) {
            boolean an = k != 1;
            g.fill(x + 12, yy, x + w - 12, yy + 14, k == 0 ? VortexStyle.HOV : VortexStyle.INNER);
            g.text(this.font, namen[k], x + 18, yy + 3, an ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, false);
            if (an) g.fill(x + w - 20, yy + 4, x + w - 14, yy + 10, VortexStyle.akzent(k / 2f));
            yy += 16;
        }
    }

    private void farbeWaehlen(int nr) {
        int[] f = Farbthema.eigeneFarben();
        if (nr >= f.length) return;
        ColorSetting s = new ColorSetting("Color " + (nr + 1), 0xFF000000 | f[nr]).noGradient();
        this.minecraft.gui.setScreen(new ColorPickerScreen(this, s, () -> {
            int[] neu = Farbthema.eigeneFarben();
            if (nr < neu.length) { neu[nr] = s.get() & 0xFFFFFF; Farbthema.setzeEigene(neu); }
        }));
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
        for (Ziel z : ziele) {
            if (drin(click.x(), click.y(), z.x(), z.y(), z.w(), z.h())) { z.aktion().run(); return true; }
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
