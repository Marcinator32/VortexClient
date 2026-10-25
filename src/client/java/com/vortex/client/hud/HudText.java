package com.vortex.client.hud;

import com.vortex.client.core.setting.ColorSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Zeichnet Text-HUD-Elemente mit HudStyle und Farbverlauf (neu in 4.6.0).
 *
 * Eine Zeile besteht aus Beschriftung ("FPS") und Wert ("144"). Wie beides
 * zusammengesetzt wird, entscheidet der Label Style: die gewohnte Form des
 * Elements, "Label: Value", "Value Label" oder nur der Wert.
 *
 * VERLAUF IM TEXT
 * Ist die Textfarbe ein Verlauf, bekommt jedes Zeichen die Farbe an seiner
 * Stelle -- ueber die ganze Breite des Elements, auch ueber mehrere Zeilen
 * hinweg gleich ausgerichtet. Einfarbig wird wie bisher jedes Stueck in einem
 * Rutsch gezeichnet; das kostet nichts extra.
 */
public final class HudText {

    private HudText() {}

    private static final int ZEILE_H = 10;

    /** Eine Zeile: Beschriftung (darf null sein), Wert, feste Farbe (0 = keine). */
    public static final class Zeile {
        final String label, wert;
        final int farbe;
        public Zeile(String label, String wert) { this(label, wert, 0); }
        public Zeile(String label, String wert, int farbe) {
            this.label = label;
            this.wert = wert;
            this.farbe = farbe;
        }
    }

    /** Eine Zeile. */
    public static void zeile(GuiGraphicsExtractor ctx, Font font, int x, int y, float scale,
                             HudStyle stil, ColorSetting farbe,
                             String label, String wert, int standardForm) {
        zeile(ctx, font, x, y, scale, stil, farbe, label, wert, standardForm, 0, 1f);
    }

    /**
     * Eine Zeile mit fester Wertfarbe (override != 0, z. B. TPS nach Wert
     * gefaerbt) und/oder Ausblenden (alpha 0..1).
     */
    public static void zeile(GuiGraphicsExtractor ctx, Font font, int x, int y, float scale,
                             HudStyle stil, ColorSetting farbe,
                             String label, String wert, int standardForm, int override, float alpha) {
        List<Zeile> l = new ArrayList<>(1);
        l.add(new Zeile(label, wert));
        block(ctx, font, x, y, scale, stil, farbe, l, standardForm, override, alpha, null);
    }

    /** Eine Zeile mit einem Gegenstand links davor (Totem-Zaehler). */
    public static void mitIcon(GuiGraphicsExtractor ctx, Font font, int x, int y, float scale,
                               HudStyle stil, ColorSetting farbe, String wert,
                               net.minecraft.world.item.ItemStack icon) {
        List<Zeile> l = new ArrayList<>(1);
        l.add(new Zeile(null, wert));
        block(ctx, font, x, y, scale, stil, farbe, l, HudStyle.FORM_VALUE_ONLY, 0, 1f, icon);
    }

    /**
     * Mehrere Zeilen als ein Element (Session Stats, Totem-Liste).
     */
    public static void block(GuiGraphicsExtractor ctx, Font font, int x, int y, float scale,
                             HudStyle stil, ColorSetting farbe, List<Zeile> zeilen,
                             int standardForm, int override, float alpha,
                             net.minecraft.world.item.ItemStack icon) {
        if (zeilen.isEmpty() || font == null) return;
        if (HudKarte.modern(stil.look)) {
            int f = stil.hatBeschriftung() && stil.labelStyle.getIndex() != 0 ? stil.labelStyle.getIndex() : standardForm;
            karte(ctx, x, y, scale, stil, farbe, zeilen, f, override, alpha, icon);
            return;
        }

        int form = stil.hatBeschriftung() && stil.labelStyle.getIndex() != 0
                ? stil.labelStyle.getIndex() : standardForm;
        List<List<Stueck>> teile = new ArrayList<>(zeilen.size());
        int textB = 0;
        for (Zeile z : zeilen) {
            List<Stueck> st = zusammensetzen(z.label, z.wert, form, stil);
            int w = 0;
            for (Stueck s : st) w += font.width(s.text);
            textB = Math.max(textB, w);
            teile.add(st);
        }
        int textH = zeilen.size() * ZEILE_H - 1;
        int iconB = icon != null ? 20 : 0;
        int innenH = icon != null ? Math.max(16, textH) : textH;
        int textOben = (innenH - textH) / 2;

        int pad = stil.hatRahmen() ? stil.padding.getInt() : 0;
        int rahmen = stil.border.getIndex();
        int links = pad + (rahmen == 1 ? 3 : 0);
        int w = textB + links + iconB + pad;
        int h = innenH + 2 * pad;
        stil.letzteB = Math.round(w * scale);
        stil.letzteH = Math.round(h * scale);

        HudRenderer.pushScale(ctx, x, y, scale);
        try {
            if (stil.background.get()) {
                band(ctx, x, y, w, h, stil.backgroundColor, alpha);
            }
            switch (rahmen) {
                case 1:
                    for (int i = 0; i < h; i += 2) {
                        int c = mitAlpha(stil.borderColor.at((i + 1f) / h), alpha);
                        ctx.fill(x, y + i, x + 2, y + Math.min(i + 2, h), c);
                    }
                    break;
                case 2:
                    band(ctx, x, y + h, w, 1, stil.borderColor, alpha);
                    break;
                case 3:
                    band(ctx, x, y, w, 1, stil.borderColor, alpha);
                    band(ctx, x, y + h - 1, w, 1, stil.borderColor, alpha);
                    ctx.fill(x, y + 1, x + 1, y + h - 1, mitAlpha(stil.borderColor.at(0f), alpha));
                    ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, mitAlpha(stil.borderColor.at(1f), alpha));
                    break;
                default:
                    break;
            }

            if (icon != null) ctx.item(icon, x + links, y + pad);
            boolean schatten = stil.shadow.get();
            int tx0 = x + links + iconB;
            int ty = y + pad + textOben;
            ColorSetting labelFarbe = stil.ownLabelColor.get() ? stil.labelColor : farbe;
            for (int zi = 0; zi < teile.size(); zi++) {
                List<Stueck> st = teile.get(zi);
                int zeilenFarbe = zeilen.get(zi).farbe;
                int cx = tx0;
                for (Stueck s : st) {
                    ColorSetting f = s.beschriftung ? labelFarbe : farbe;
                    int festC = zeilenFarbe != 0 ? zeilenFarbe : (!s.beschriftung ? override : 0);
                    boolean fest = festC != 0;
                    if (fest || !f.isGradient()) {
                        int c = fest ? festC : f.get();
                        ctx.text(font, Component.literal(s.text), cx, ty, mitAlpha(c, alpha), schatten);
                        cx += font.width(s.text);
                    } else {
                        int i = 0;
                        while (i < s.text.length()) {
                            int cp = s.text.codePointAt(i);
                            String ch = new String(Character.toChars(cp));
                            int cw = font.width(ch);
                            float pos = textB <= 0 ? 0f : (cx - tx0 + cw / 2f) / textB;
                            ctx.text(font, Component.literal(ch), cx, ty, mitAlpha(f.at(pos), alpha), schatten);
                            cx += cw;
                            i += Character.charCount(cp);
                        }
                    }
                }
                ty += ZEILE_H;
            }
        } finally {
            HudRenderer.popScale(ctx);
        }
    }

    private static final java.util.regex.Pattern EINHEIT = java.util.regex.Pattern.compile("^([-+]?[0-9][0-9.,:]*|--)\\s?([a-zA-Z/%*]{1,7})$");

    private static void karte(GuiGraphicsExtractor ctx, int x, int y, float scale, HudStyle stil, ColorSetting farbe,
                              List<Zeile> zeilen, int form, int override, float alpha, net.minecraft.world.item.ItemStack icon) {
        final int luecke = 4;
        List<Stueck[]> teile = new ArrayList<>(zeilen.size());
        int textB = 0;
        for (Zeile z : zeilen) {
            String label = fall(z.label, stil), wert = fall(z.wert, stil);
            Stueck[] st;
            java.util.regex.Matcher em = wert == null ? null : EINHEIT.matcher(wert);
            if ((label == null || label.isEmpty()) && em != null && em.matches()) st = new Stueck[]{new Stueck(em.group(1), false), new Stueck(em.group(2), true)};
            else if (label == null || label.isEmpty()) st = new Stueck[]{new Stueck(wert == null ? "" : wert, false)};
            else if (wert == null || wert.isEmpty()) st = new Stueck[]{new Stueck(label, false)};
            else if (form == HudStyle.FORM_VALUE_ONLY) st = em != null && em.matches()
                    ? new Stueck[]{new Stueck(em.group(1), false), new Stueck(em.group(2), true)} : new Stueck[]{new Stueck(wert, false)};
            else if (form == HudStyle.FORM_LABEL_LAST) st = new Stueck[]{new Stueck(wert, false), new Stueck(label, true)};
            else st = new Stueck[]{new Stueck(label, true), new Stueck(wert, false)};
            int w = 0;
            for (int i = 0; i < st.length; i++) w += HudKarte.breite(st[i].text, !st[i].beschriftung) + (i > 0 ? luecke : 0);
            textB = Math.max(textB, w);
            teile.add(st);
        }
        int textH = zeilen.size() * HudKarte.ZEILE - 2;
        int iconB = icon != null ? 19 : 0;
        int innenH = icon != null ? Math.max(16, textH) : textH;
        int links = HudKarte.PAD_X + 2;
        int w = links + iconB + textB + HudKarte.PAD_X;
        int h = innenH + 2 * HudKarte.PAD_Y;
        stil.letzteB = Math.round(w * scale);
        stil.letzteH = Math.round(h * scale);
        HudRenderer.pushScale(ctx, x, y, scale);
        try {
            HudKarte.karte(ctx, x, y, w, h, alpha);
            if (icon != null) ctx.item(icon, x + links, y + (h - 16) / 2);
            int wertFarbe = farbe.isGradient() ? farbe.at(0.5f) : farbe.get();
            int labelF = stil.ownLabelColor.get() ? stil.labelColor.get() : HudKarte.labelFarbe();
            float ty = y + HudKarte.PAD_Y + (innenH - textH) / 2f;
            for (int zi = 0; zi < teile.size(); zi++) {
                Stueck[] st = teile.get(zi);
                int zf = zeilen.get(zi).farbe;
                float cx = x + links + iconB;
                for (int i = 0; i < st.length; i++) {
                    Stueck s = st[i];
                    if (i > 0) cx += luecke;
                    int c = s.beschriftung ? labelF : (zf != 0 ? zf : override != 0 ? override : wertFarbe);
                    HudKarte.text(ctx, s.text, cx, ty, mitAlpha(c | 0xFF000000, alpha), !s.beschriftung);
                    cx += HudKarte.breite(s.text, !s.beschriftung);
                }
                ty += HudKarte.ZEILE;
            }
        } finally {
            HudRenderer.popScale(ctx);
        }
    }

    private static final class Stueck {
        final String text;
        final boolean beschriftung;
        Stueck(String text, boolean beschriftung) {
            this.text = text;
            this.beschriftung = beschriftung;
        }
    }

    private static List<Stueck> zusammensetzen(String label, String wert, int form, HudStyle stil) {
        List<Stueck> out = new ArrayList<>(4);
        label = fall(label, stil);
        wert = fall(wert, stil);
        boolean klammern = stil.brackets.get();
        if (klammern) out.add(new Stueck("[", true));
        if (label == null || label.isEmpty()) {
            if (wert != null) out.add(new Stueck(wert, false));
        } else if (wert == null || wert.isEmpty()) {
            out.add(new Stueck(label, true));
        } else {
            switch (form) {
                case HudStyle.FORM_LABEL_LAST:
                    out.add(new Stueck(wert, false));
                    out.add(new Stueck(" " + label, true));
                    break;
                case HudStyle.FORM_VALUE_ONLY:
                    out.add(new Stueck(wert, false));
                    break;
                default:
                    out.add(new Stueck(label + ": ", true));
                    out.add(new Stueck(wert, false));
                    break;
            }
        }
        if (klammern) out.add(new Stueck("]", true));
        return out;
    }

    private static String fall(String s, HudStyle stil) {
        if (s == null) return null;
        switch (stil.textCase.getIndex()) {
            case 1: return s.toUpperCase(Locale.ROOT);
            case 2: return s.toLowerCase(Locale.ROOT);
            default: return s;
        }
    }

    /** Waagerechte Flaeche, einfarbig oder als Verlauf in schmalen Baendern. */
    static void band(GuiGraphicsExtractor ctx, int x, int y, int w, int h, ColorSetting f, float alpha) {
        if (w <= 0 || h <= 0) return;
        if (!f.isGradient()) {
            ctx.fill(x, y, x + w, y + h, mitAlpha(f.get(), alpha));
            return;
        }
        for (int i = 0; i < w; i += 3) {
            ctx.fill(x + i, y, x + Math.min(i + 3, w), y + h, mitAlpha(f.at((i + 1.5f) / w), alpha));
        }
    }

    static int mitAlpha(int argb, float alpha) {
        if (alpha >= 1f) return argb;
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, alpha));
        return (Math.max(4, a) << 24) | (argb & 0x00FFFFFF);
    }
}
