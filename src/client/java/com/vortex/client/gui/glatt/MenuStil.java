package com.vortex.client.gui.glatt;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/**
 * Aussehen von Hauptmenue, Einzelspieler, Mehrspieler und Pausenmenue
 * (abschaltbar: Client Settings -> "Modern Menus").
 *
 * Seit 4.31 "Minecraft-nah": flache, halbdurchsichtige Flaechen mit
 * eckigen Kanten und einem feinen Pixelrahmen, Minecrafts eigene Schrift mit
 * Schatten, unter der Maus weisser Rahmen und gelbliche Schrift wie frueher
 * in Vanilla. Kein Glas, kein Leuchten, keine Verlaeufe.
 *
 * Gilt nur in diesen Bildschirmen -- andere Mods und Menues bleiben, wie sie
 * sind. Knoepfe anderer Mods in diesen Menues (z. B. Mod Menu) bekommen das
 * Aussehen automatisch mit.
 */
public final class MenuStil {

    private MenuStil() {}

    private static final Map<Object, float[]> HOVER = new WeakHashMap<>();
    private static final long START = System.nanoTime();

    public static final int FLAECHE = 0xA8101010;
    public static final int FLAECHE_HOVER = 0xC0383838;
    public static final int FLAECHE_AUS = 0x70101010;
    public static final int RAND = 0xFF000000;
    public static final int RAND_HOVER = 0xFFFFFFFF;
    public static final int LICHT = 0x26FFFFFF;
    public static final int SCHATTEN = 0x50000000;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_HOVER = 0xFFFFFFA0;
    public static final int TEXT_AUS = 0xFFA0A0A0;
    public static final int TEXT_LEISE = 0xFFB0B0B0;

    /**
     * Gilt der Stil im gerade offenen Bildschirm? Immer in Haupt-, Einzel-, Mehrspieler-,
     * Welt-erstellen- und ESC-Menue; seit 4.90 auf Wunsch (KnopfStil.ueberall) in allen
     * Minecraft-Menues -- nicht in Inventaren, im Chat und in Menues von Vortex oder anderen Mods.
     */
    public static boolean aktiv() {
        try {
            if (!com.vortex.client.core.ClientSettings.INSTANCE.modernMenus.get()) return false;
            if (KnopfStil.stil() == KnopfStil.Stil.ORIGINAL) return false;
            if (fremdeKnoepfe()) return false;
            Screen s = Minecraft.getInstance().gui.screen();
            if (s == null) return false;
            if (s instanceof TitleScreen || s instanceof SelectWorldScreen || s instanceof JoinMultiplayerScreen
                    || s instanceof PauseScreen || s instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen) return true;
            return KnopfStil.ueberall() && minecraftMenue(s);
        } catch (Throwable e) {
            return false;
        }
    }

    /** Gilt der Stil fuer diesen Knopf? Wie {@link #aktiv()}, aber nicht bei Beschriftungen aus Pack-Bildzeichen. */
    public static boolean aktiv(AbstractWidget w) {
        return aktiv() && (w == null || !sonderText(w.getMessage()));
    }

    private static java.util.List<String> packStand = java.util.List.of();
    private static long packGeprueft, packVoll;
    private static boolean fremdeKnoepfe;
    private static final String[] KNOPF_BILDER = { "button", "button_highlighted", "button_disabled", "slider", "slider_handle" };

    /**
     * Bringt ein Resource Pack eigene Knoepfe mit (seit 4.96)? Server-Packs (FUGO, Donut SMP ...)
     * bauen ihr ESC-Menue oft aus unsichtbaren Knoepfen und Bildzeichen in der Schrift; eigene
     * Packs koennen die Knopf-Grafik austauschen. Dann zeichnet Vortex nichts darueber -- die
     * Menues sehen aus, wie das Pack sie vorsieht. Pack-Wechsel fallen nach hoechstens 1/4 s auf, die Grafiken werden alle 3 s neu geprueft.
     */
    public static boolean fremdeKnoepfe() {
        try {
            long jetzt = System.currentTimeMillis();
            if (jetzt - packGeprueft < 250) return fremdeKnoepfe;
            Minecraft mc = Minecraft.getInstance();
            java.util.List<String> ids = new java.util.ArrayList<>(mc.getResourcePackRepository().getSelectedIds());
            if (!ids.equals(packStand) || jetzt - packVoll > 3000) {
                packStand = ids;
                packVoll = jetzt;
                fremdeKnoepfe = packPruefen(mc);
            }
            packGeprueft = jetzt;
            return fremdeKnoepfe;
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean packPruefen(Minecraft mc) {
        for (net.minecraft.server.packs.repository.Pack p : mc.getResourcePackRepository().getSelectedPacks()) {
            if (p.getPackSource() == net.minecraft.server.packs.repository.PackSource.SERVER) return true;
        }
        var rm = mc.getResourceManager();
        for (String b : KNOPF_BILDER) {
            var r = rm.getResource(net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/sprites/widget/" + b + ".png"));
            if (r.isPresent() && !"vanilla".equals(r.get().sourcePackId())) return true;
        }
        return false;
    }

    /** Beschriftung mit eigener Schrift oder Bildzeichen eines Packs (Private-Use-Zeichen)? */
    public static boolean sonderText(net.minecraft.network.chat.Component c) {
        if (c == null) return false;
        boolean[] ja = { false };
        c.visit((style, teil) -> {
            if (!net.minecraft.network.chat.FontDescription.DEFAULT.equals(style.getFont())
                    || teil.codePoints().anyMatch(cp -> (cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000)) {
                ja[0] = true;
                return net.minecraft.network.chat.FormattedText.STOP_ITERATION;
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return ja[0];
    }

    private static final Map<Class<?>, Boolean> MINECRAFT_MENUE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Ein Menue aus Minecraft selbst (kein Inventar, kein Chat)? */
    private static boolean minecraftMenue(Screen s) {
        return MINECRAFT_MENUE.computeIfAbsent(s.getClass(), k -> k.getName().startsWith("net.minecraft.")
                && !(s instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)
                && !(s instanceof net.minecraft.client.gui.screens.ChatScreen));
    }

    /** Runder Stil (Rounded oder Neon) gewaehlt? */
    public static boolean rund() {
        KnopfStil.Stil st = KnopfStil.stil();
        return st == KnopfStil.Stil.RUND || st == KnopfStil.Stil.NEON;
    }

    /**
     * ESC-Menue in Vortex-Farben (seit 4.53)? Dann bekommen die Knoepfe eine violette Flaeche,
     * unter der Maus einen violetten Rand mit Verlauf -- "Disconnect" in Rot.
     */
    public static boolean pauseFarben() {
        try {
            return com.vortex.client.core.ClientSettings.INSTANCE.vortexPauseMenu.get()
                    && Minecraft.getInstance().gui.screen() instanceof PauseScreen;
        } catch (Throwable e) {
            return false;
        }
    }

    private static final int P_FLAECHE = 0xB0150F26;
    private static final int P_FLAECHE_HOVER = 0xD02A1F4A;
    private static final int P_RAND = 0xFF06040C;
    private static final int P_RAND_HOVER = 0xFFA78BFA;
    private static final int P_TEXT = 0xFFEDE9FA;
    private static final int P_TEXT_HOVER = 0xFFFFFFFF;
    private static final int R_FLAECHE_HOVER = 0xD0401520;
    private static final int R_RAND_HOVER = 0xFFF87171;
    private static final int R_TEXT = 0xFFF5C2C7;
    private static final int R_TEXT_HOVER = 0xFFFFE4E6;

    /** Waagerechter Farbverlauf (in 2-Pixel-Streifen). */
    public static void verlaufW(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int links, int rechts) {
        int w = x1 - x0;
        if (w <= 0 || y1 <= y0) return;
        for (int x = x0; x < x1; x += 2) {
            float t = w <= 1 ? 0f : (x - x0) / (float) (w - 1);
            ctx.fill(x, y0, Math.min(x + 2, x1), y1, Glatt.mix(links, rechts, t));
        }
    }

    public static boolean titel() {
        try {
            return com.vortex.client.core.ClientSettings.INSTANCE.modernMenus.get()
                    && Minecraft.getInstance().gui.screen() instanceof TitleScreen;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Text in "Inter" umstellen? Seit 4.31 nicht mehr -- die Menues nutzen
     * wieder Minecrafts eigene Schrift (passt zum flachen Stil). Bleibt fuer
     * die Mixins bestehen, die danach fragen.
     */
    public static boolean textAktiv() {
        return false;
    }

    /** Listen (Welten, Server) -- nur noch fuer die Mixins, die das melden. */
    public static void listeBeginn() {}
    public static void listeEnde() {}

    /** Zeichen in Minecrafts Standardschrift auf "Inter" umstellen (von textAktiv() nicht mehr verlangt). */
    public static net.minecraft.util.FormattedCharSequence umschreiben(net.minecraft.util.FormattedCharSequence seq) {
        if (seq == null) return null;
        final net.minecraft.network.chat.FontDescription f = Glatt.Schrift.NORMAL.font(Glatt.skala());
        return sink -> seq.accept((i, st, cp) ->
                sink.accept(i, st.getFont().equals(net.minecraft.network.chat.FontDescription.DEFAULT) ? st.withFont(f) : st, cp));
    }

    /** Weicher Hover-Wert 0..1 je Knopf (fuer kurze Uebergaenge). */
    public static float hover(Object knopf, boolean ueber) {
        float[] z = HOVER.computeIfAbsent(knopf, k -> new float[]{0f, 0f});
        float jetzt = (System.nanoTime() - START) / 1e9f;
        float dt = z[1] == 0f ? 0.016f : Math.max(0f, Math.min(0.1f, jetzt - z[1]));
        z[1] = jetzt;
        float f = 1f - (float) Math.exp(-22f * dt);
        z[0] += ((ueber ? 1f : 0f) - z[0]) * f;
        return z[0];
    }

    /**
     * Flache Flaeche mit Pixelrahmen: aussen 1 GUI-Pixel Rand, innen oben/links
     * ein Hauch Licht, unten ein Hauch Schatten (wie Minecrafts Knoepfe, nur
     * flach und durchscheinend).
     */
    public static void kachel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float hv, boolean an, float alpha) {
        kachel(ctx, x, y, w, h, hv, an, alpha, 0);
    }

    /** stil: 0 = grau (Minecraft-nah), 1 = Vortex-Farben (ESC-Menue), 2 = Rot (Trennen im ESC-Menue). */
    public static void kachel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float hv, boolean an, float alpha, int stil) {
        if (w <= 2 || h <= 2) return;
        if (rund()) {
            rundKachel(ctx, x, y, w, h, hv, an, alpha, stil);
            return;
        }
        if (stil != 0) {
            farbKachel(ctx, x, y, w, h, hv, an, alpha, stil == 2);
            return;
        }
        int flaeche = an ? Glatt.mix(FLAECHE, FLAECHE_HOVER, hv) : FLAECHE_AUS;
        int rand = an ? Glatt.mix(RAND, RAND_HOVER, hv) : 0xFF202020;
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, Glatt.alpha(flaeche, alpha));
        int r = Glatt.alpha(rand, alpha * (an ? 0.85f + 0.15f * hv : 0.7f));
        ctx.fill(x, y, x + w, y + 1, r);
        ctx.fill(x, y + h - 1, x + w, y + h, r);
        ctx.fill(x, y + 1, x + 1, y + h - 1, r);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, r);
        if (an) {
            ctx.fill(x + 1, y + 1, x + w - 1, y + 2, Glatt.alpha(LICHT, alpha));
            ctx.fill(x + 1, y + 2, x + 2, y + h - 1, Glatt.alpha(LICHT, alpha * 0.6f));
            ctx.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Glatt.alpha(SCHATTEN, alpha));
        }
    }

    private static void farbKachel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float hv, boolean an, float alpha, boolean rot) {
        int flaeche = an ? Glatt.mix(P_FLAECHE, rot ? R_FLAECHE_HOVER : P_FLAECHE_HOVER, hv) : FLAECHE_AUS;
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, Glatt.alpha(flaeche, alpha));
        if (an) {
            ctx.fill(x + 1, y + 1, x + w - 1, y + 2, Glatt.alpha(0x30C4B5FD, alpha));
            ctx.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Glatt.alpha(SCHATTEN, alpha));
            if (hv > 0.01f) {
                int a = rot ? 0xFFF87171 : vortexViolett(), b = rot ? 0xFFFB923C : 0xFF3B82F6;
                verlaufW(ctx, x + 1, y + h - 3, x + w - 1, y + h - 1, Glatt.alpha(a, alpha * hv), Glatt.alpha(b, alpha * hv));
            }
        }
        int rand = an ? Glatt.mix(P_RAND, rot ? R_RAND_HOVER : P_RAND_HOVER, hv) : 0xFF202020;
        int r = Glatt.alpha(rand, alpha * (an ? 0.85f + 0.15f * hv : 0.7f));
        ctx.fill(x, y, x + w, y + 1, r);
        ctx.fill(x, y + h - 1, x + w, y + h, r);
        ctx.fill(x, y + 1, x + 1, y + h - 1, r);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, r);
    }

    private static int vortexViolett() { return 0xFF8B5CF6; }

    /**
     * Runder Knopf (seit 4.90): Flaeche je nach Fuellung (dunkel, in der Akzentfarbe getoent,
     * Glas), feiner Rand, unter der Maus ein Schein in der Akzentfarbe. Neon: kraeftiger
     * Rand und Schein schon ohne Maus. stil 2 = Rot (Trennen im ESC-Menue).
     */
    private static void rundKachel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float hv, boolean an, float alpha, int stil) {
        boolean neon = KnopfStil.stil() == KnopfStil.Stil.NEON;
        float r = KnopfStil.radius(h);
        int akzent = stil == 2 ? 0xFFF87171 : vortexViolett();
        int akzent2 = stil == 2 ? 0xFFFB923C : 0xFF3B82F6;
        if (!an) {
            Glatt.rund(ctx, x, y, w, h, r, Glatt.alpha(0x80101016, alpha));
            Glatt.rahmen(ctx, x, y, w, h, r, 1, Glatt.alpha(0x26FFFFFF, alpha));
            return;
        }
        float glanz = neon ? 0.30f + 0.35f * hv : 0.40f * hv;
        if (glanz > 0.01f) Glatt.schatten(ctx, x, y, w, h, r, neon ? 5f : 4f, Glatt.alpha(akzent, alpha * glanz));
        int grund = switch (KnopfStil.fuellung()) {
            case DUNKEL -> Glatt.mix(0xD80E0B16, 0xE81E1930, hv);
            case GETOENT -> Glatt.mix(Glatt.mix(0xE00E0B16, akzent, 0.20f), Glatt.mix(0xE81E1930, akzent, 0.34f), hv);
            case GLAS -> Glatt.mix(0x46FFFFFF, 0x66FFFFFF, hv);
        };
        Glatt.rund(ctx, x, y, w, h, r, Glatt.alpha(grund, alpha));
        int licht = KnopfStil.fuellung() == KnopfStil.Fuellung.GLAS ? 0x1CFFFFFF : 0x10FFFFFF;
        if (h > 6) Glatt.rund(ctx, x + 1, y + 1, w - 2, h / 2f - 1, Math.max(0f, r - 1), Glatt.alpha(licht, alpha));
        if (!neon && hv > 0.01f && w > 12) {
            verlaufW(ctx, Math.round(x + r), y + h - 2, Math.round(x + w - r), y + h - 1, Glatt.alpha(akzent, alpha * hv), Glatt.alpha(akzent2, alpha * hv));
        }
        int rand = neon ? Glatt.mix(akzent, Glatt.mix(akzent, 0xFFFFFFFF, 0.4f), hv) : Glatt.mix(0x30FFFFFF, Glatt.mix(akzent, 0xFFFFFFFF, 0.25f), hv);
        Glatt.rahmen(ctx, x, y, w, h, r, neon ? 2 : 1, Glatt.alpha(rand, alpha));
    }

    /** Ein Knopf zum Ansehen (Client Design): hv = wie weit die Maus darueber ist. */
    public static void beispielKnopf(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String text, float hv, boolean an) {
        boolean original = KnopfStil.stil() == KnopfStil.Stil.ORIGINAL;
        if (original) {
            ctx.fill(x, y, x + w, y + h, an ? Glatt.mix(0xFF6F6F6F, 0xFF8A8AA8, hv) : 0xFF3A3A3A);
            ctx.fill(x, y, x + w, y + 1, an && hv > 0.5f ? 0xFFFFFFFF : 0xFF000000);
            ctx.fill(x, y + h - 1, x + w, y + h, 0xFF000000);
            ctx.fill(x, y, x + 1, y + h, an && hv > 0.5f ? 0xFFFFFFFF : 0xFF000000);
            ctx.fill(x + w - 1, y, x + w, y + h, an && hv > 0.5f ? 0xFFFFFFFF : 0xFF000000);
        } else {
            kachel(ctx, x, y, w, h, hv, an, 1f, 0);
        }
        int farbe = !an ? TEXT_AUS : Glatt.mix(TEXT, rund() || original ? 0xFFFFFFFF : TEXT_HOVER, hv);
        textMitte(ctx, kuerzen(text, w - 8), x + w / 2, y + (h - 8) / 2, farbe, an);
    }

    /** Text in Minecrafts Schrift. */
    public static void text(GuiGraphicsExtractor ctx, String s, int x, int y, int farbe, boolean schatten) {
        if (s == null || s.isEmpty() || (farbe >>> 24) < 5) return;
        ctx.text(Minecraft.getInstance().font, s, x, y, farbe, schatten);
    }

    public static void textMitte(GuiGraphicsExtractor ctx, String s, int cx, int y, int farbe, boolean schatten) {
        text(ctx, s, cx - breite(s) / 2, y, farbe, schatten);
    }

    public static int breite(String s) {
        return s == null ? 0 : Minecraft.getInstance().font.width(s);
    }

    /** Text in ganzzahliger Vergroesserung (bleibt scharf). */
    public static void textGross(GuiGraphicsExtractor ctx, String s, float x, float y, int faktor, int farbe, boolean schatten) {
        var p = ctx.pose();
        p.pushMatrix();
        try {
            p.translate(x, y);
            p.scale(faktor, faktor);
            text(ctx, s, 0, 0, farbe, schatten);
        } finally {
            p.popMatrix();
        }
    }

    /** Kuerzt auf hoechstens max GUI-Pixel ("..." am Ende). */
    public static String kuerzen(String s, int max) {
        if (s == null) return "";
        Font f = Minecraft.getInstance().font;
        if (f.width(s) <= max) return s;
        String c = s;
        while (c.length() > 1 && f.width(c + "...") > max) c = c.substring(0, c.length() - 1);
        return c.stripTrailing() + "...";
    }

    /** Hintergrund eines Knopfs (ohne Text). */
    public static float grund(GuiGraphicsExtractor ctx, AbstractWidget w) {
        boolean an = w.active;
        float hv = hover(w, an && w.isHoveredOrFocused());
        kachel(ctx, w.getX(), w.getY(), w.getWidth(), w.getHeight(), hv, an, Masken.klemme(widgetAlpha(w)), stil(w));
        return hv;
    }

    /** Ganzer Knopf: Grund + Text in Minecrafts Schrift. */
    public static void knopf(GuiGraphicsExtractor ctx, AbstractWidget w) {
        float hv = grund(ctx, w);
        boolean an = w.active;
        float alpha = Masken.klemme(widgetAlpha(w));
        int st = stil(w);
        int farbe = !an ? TEXT_AUS : st == 2 ? Glatt.mix(R_TEXT, R_TEXT_HOVER, hv)
                : st == 1 ? Glatt.mix(P_TEXT, P_TEXT_HOVER, hv) : Glatt.mix(TEXT, TEXT_HOVER, hv);
        Font f = Minecraft.getInstance().font;
        net.minecraft.util.FormattedCharSequence text = beschriftung(f, w.getMessage(), w.getWidth() - 8);
        if ((farbe >>> 24) < 5) return;
        ctx.text(f, text, w.getX() + w.getWidth() / 2 - f.width(text) / 2, w.getY() + (w.getHeight() - 8) / 2, Glatt.alpha(farbe, alpha), an);
    }

    /** Beschriftung mit allen Stilen (Farben, Fett ...), zu lang: gekuerzt mit "...". */
    private static net.minecraft.util.FormattedCharSequence beschriftung(Font f, net.minecraft.network.chat.Component msg, int max) {
        if (f.width(msg) <= max) return msg.getVisualOrderText();
        net.minecraft.network.chat.FormattedText teil = f.substrByWidth(msg, Math.max(0, max - f.width("...")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(
                net.minecraft.network.chat.FormattedText.composite(teil, net.minecraft.network.chat.FormattedText.of("...")));
    }

    /** Farbstil eines Knopfs: 0 grau, 1 Vortex (ESC-Menue), 2 Rot ("Disconnect" im ESC-Menue). */
    private static int stil(AbstractWidget w) {
        if (!pauseFarben()) return 0;
        return com.vortex.client.gui.menu.PauseMenu.trennen(w) ? 2 : 1;
    }

    /** Ausgewaehlter Eintrag einer Liste (Welten, Server). */
    public static void auswahl(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        if (rund()) {
            float r = Math.min(KnopfStil.radius(h), 8f);
            Glatt.rund(ctx, x, y, w, h, r, KnopfStil.fuellung() == KnopfStil.Fuellung.GLAS ? 0x40FFFFFF : 0xA00E0B16);
            Glatt.rahmen(ctx, x, y, w, h, r, KnopfStil.stil() == KnopfStil.Stil.NEON ? 2 : 1, Glatt.mix(vortexViolett(), 0xFFFFFFFF, 0.2f));
            return;
        }
        ctx.fill(x, y, x + w, y + h, 0x90000000);
        ctx.fill(x, y, x + w, y + 1, 0xFFC0C0C0);
        ctx.fill(x, y + h - 1, x + w, y + h, 0xFFC0C0C0);
        ctx.fill(x, y + 1, x + 1, y + h - 1, 0xFFC0C0C0);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, 0xFFC0C0C0);
    }

    /** Trennlinien ueber und unter einer Liste. */
    public static void trenner(GuiGraphicsExtractor ctx, int x, int oben, int w, int unten) {
        ctx.fill(x, oben, x + w, oben + 1, 0x40FFFFFF);
        ctx.fill(x, unten - 1, x + w, unten, 0x40FFFFFF);
    }

    /** Eingabefeld-Hintergrund. */
    public static void eingabe(GuiGraphicsExtractor ctx, AbstractWidget w, int x, int y, int bw, int bh) {
        boolean fokus = w.isFocused();
        if (rund()) {
            float r = KnopfStil.radius(bh);
            Glatt.rund(ctx, x, y, bw, bh, r, w.active ? 0xE0050409 : 0xA0050409);
            int rr = fokus ? Glatt.mix(vortexViolett(), 0xFFFFFFFF, 0.2f) : w.isHovered() ? 0x80FFFFFF : 0x40FFFFFF;
            Glatt.rahmen(ctx, x, y, bw, bh, r, fokus && KnopfStil.stil() == KnopfStil.Stil.NEON ? 2 : 1, rr);
            return;
        }
        ctx.fill(x, y, x + bw, y + bh, w.active ? 0xE0000000 : 0xA0000000);
        int rand = fokus ? 0xFFFFFFFF : w.isHovered() ? 0xFFC0C0C0 : 0xFF707070;
        ctx.fill(x, y, x + bw, y + 1, rand);
        ctx.fill(x, y + bh - 1, x + bw, y + bh, rand);
        ctx.fill(x, y + 1, x + 1, y + bh - 1, rand);
        ctx.fill(x + bw - 1, y + 1, x + bw, y + bh - 1, rand);
    }

    /**
     * Symbol-Knopf (Sprache, Barrierefreiheit): Minecrafts eigenes Symbol
     * bleibt -- passt zum Stil.
     * @return true, wenn gezeichnet (sonst bleibt Minecrafts Bildchen)
     */
    public static boolean symbolKnopf(GuiGraphicsExtractor ctx, AbstractWidget w, String pfad, int x, int y, int sw, int sh) {
        return false;
    }

    /**
     * Regler (seit 4.90): Spur wie ein Knopf ohne Schein, bis zum Griff in der Akzentfarbe
     * gefuellt. Liefert false, wenn der Stil hier nicht gilt (dann zeichnet Minecraft).
     */
    public static boolean reglerSpur(GuiGraphicsExtractor ctx, AbstractWidget w, int x, int y, int bw, int bh, float wert) {
        if (!rund() && KnopfStil.stil() != KnopfStil.Stil.FLACH) return false;
        float alpha = Masken.klemme(widgetAlpha(w));
        if (!rund()) {
            kachel(ctx, x, y, bw, bh, 0f, w.active, alpha, 0);
            return true;
        }
        float r = KnopfStil.radius(bh);
        int grund = KnopfStil.fuellung() == KnopfStil.Fuellung.GLAS ? 0x46FFFFFF : 0xD80E0B16;
        Glatt.rund(ctx, x, y, bw, bh, r, Glatt.alpha(w.active ? grund : 0x80101016, alpha));
        int bis = Math.round(x + 4 + (bw - 8) * Math.max(0f, Math.min(1f, wert)));
        if (w.active && bis > x + 2) {
            Glatt.rund(ctx, x, y, bis - x, bh, r, Glatt.alpha(vortexViolett(), 0.38f * alpha));
        }
        Glatt.rahmen(ctx, x, y, bw, bh, r, KnopfStil.stil() == KnopfStil.Stil.NEON ? 2 : 1,
                Glatt.alpha(KnopfStil.stil() == KnopfStil.Stil.NEON ? vortexViolett() : 0x30FFFFFF, alpha));
        return true;
    }

    /** Griff des Reglers: runder Knopf in der Akzentfarbe, unter der Maus heller. */
    public static boolean reglerGriff(GuiGraphicsExtractor ctx, AbstractWidget w, int x, int y, int bw, int bh) {
        if (!rund() && KnopfStil.stil() != KnopfStil.Stil.FLACH) return false;
        float alpha = Masken.klemme(widgetAlpha(w));
        float hv = hover(w, w.active && w.isHoveredOrFocused());
        if (!rund()) {
            kachel(ctx, x, y, bw, bh, hv, w.active, alpha, 0);
            return true;
        }
        float r = Math.min(KnopfStil.radius(bh), bw / 2f);
        if (hv > 0.01f) Glatt.schatten(ctx, x, y, bw, bh, r, 3f, Glatt.alpha(vortexViolett(), alpha * 0.45f * hv));
        int f = w.active ? Glatt.mix(Glatt.mix(vortexViolett(), 0xFFFFFFFF, 0.15f), 0xFFFFFFFF, 0.35f * hv) : 0xFF505058;
        Glatt.rund(ctx, x, y, bw, bh, r, Glatt.alpha(f, alpha));
        Glatt.rahmen(ctx, x, y, bw, bh, r, 1, Glatt.alpha(0x50FFFFFF, alpha));
        return true;
    }

    private static float widgetAlpha(AbstractWidget w) {
        try {
            return w.getAlpha();
        } catch (Throwable e) {
            return 1f;
        }
    }

    /**
     * Ruhige Abdunklung ueber dem Panorama: oben und unten etwas mehr, damit
     * Logo, Knoepfe und die Vortex-Leiste lesbar sind. Kein Farbschein.
     */
    public static void titelGrund(GuiGraphicsExtractor ctx, int breite, int hoehe) {
        ctx.fillGradient(0, 0, breite, hoehe / 3, 0x70000000, 0x18000000);
        ctx.fillGradient(0, hoehe / 3, breite, hoehe, 0x18000000, 0x98000000);
    }

    /** Hoehe des Logo-Blocks (Zeichen, Schriftzug, Versionszeile). */
    public static int logoHoehe(int breite) {
        int faktor = breite >= 300 ? 3 : 2;
        return 9 * faktor + 6 + 5 + 9;
    }

    /**
     * Logo: Vortex-Zeichen und "VORTEX" in Minecrafts Schrift (dreifach, mit
     * Schatten), darunter klein Client- und Minecraft-Version.
     */
    public static void titelLogo(GuiGraphicsExtractor ctx, int breite, float y, float alpha) {
        if (alpha <= 0.01f) return;
        int weiss = Glatt.alpha(0xFFFFFFFF, alpha);
        String name = "VORTEX";
        int faktor = breite >= 300 ? 3 : 2;
        int nameW = breite(name) * faktor;
        int lg = 9 * faktor + 6;
        int gesamt = lg + 6 + nameW;
        float x = Math.round((breite - gesamt) / 2f);
        Glatt.logo(ctx, x, y, lg, alpha, alpha);
        textGross(ctx, name, x + lg + 6, Math.round(y + (lg - 8 * faktor) / 2f), faktor, weiss, true);
        String unter = untertitel();
        if (!unter.isEmpty()) {
            textMitte(ctx, unter, Math.round(breite / 2f), Math.round(y + lg + 5), Glatt.alpha(TEXT_LEISE, alpha), true);
        }
    }

    private static String untertitelCache;

    private static String untertitel() {
        if (untertitelCache == null) {
            String client = "";
            try {
                client = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("vortexclient")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("").replaceAll("\\+.*$", "");
            } catch (Throwable ignored) {}
            String mc = mcVersion();
            untertitelCache = (client.isEmpty() ? "Client" : "Client " + client) + (mc.isEmpty() ? "" : "  ·  Minecraft " + mc);
        }
        return untertitelCache;
    }

    private static String mcVersion() {
        try {
            return net.minecraft.SharedConstants.getCurrentVersion().name();
        } catch (Throwable e) {
            return "";
        }
    }
}
