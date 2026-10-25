package com.vortex.client.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.StringSetting;
import com.vortex.client.gui.ColorPickerScreen;
import com.vortex.client.gui.TextEingabeScreen;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Symbole;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Cape-Editor (seit 4.38): ein eigenes Cape in zwei Schritten.
 *
 *   1. ZUSAMMENBAUEN -- Hintergrund (Farbe, Verlauf), Muster, Symbol, Text
 *      und Rand waehlen. Klick schaltet weiter, Rechtsklick zurueck,
 *      Farbfelder oeffnen den Farbwaehler.
 *   2. MALEN -- darueber pixelgenau mit Pinsel, Radierer, Farbeimer und
 *      Pipette (40 x 64 Malpunkte auf der Rueckseite des Capes).
 *
 * "Save & wear" baut daraus dieselbe Cape-Textur wie ein hochgeladenes Bild
 * (EigenesCape) -- sie wird also auch genauso an Freunde verteilt. Der
 * Entwurf bleibt gespeichert (cape-editor.json + cape-editor-paint.png), so
 * dass man spaeter weitermachen kann.
 */
public class CapeEditorScreen extends Screen {

    private static final int BW = 80, BH = 128;
    private static final int PW = 40, PH = 64;

    private static final int TEXT = 0xFFF2F3F7, DIM = 0xFF8C90A1, DUNKEL = 0xFF5D6172;
    private static final int FELD = 0xF5101117, KARTE = 0xFF171920, KARTE_HELL = 0xFF1F222B;

    private static final String[] HINTERGRUENDE = {"Solid", "Gradient down", "Gradient across", "Diagonal", "Glow"};
    private static final String[] MUSTER = {"None", "Stripes", "Diagonal stripes", "Chevrons", "Checker", "Dots", "Diamonds", "Waves", "Stars"};
    private static final String[] SYMBOLE = {"None", "Star", "Heart", "Lightning", "Moon", "Crown", "Sword", "Diamond", "Flame", "V"};
    private static final String[] GROESSEN = {"Small", "Medium", "Large"};
    private static final String[] ORTE = {"Top", "Middle", "Bottom"};
    private static final String[] WERKZEUGE = {"Brush", "Eraser", "Fill", "Picker"};

    private static boolean geladen = false;
    private static int hintergrund = 1, muster = 0, symbol = 1, groesse = 1, ort = 1, werkzeug = 0, pinsel = 1;
    private static boolean rand = true;
    private static final ColorSetting farbe1 = new ColorSetting("Color 1", 0xFF6D28D9);
    private static final ColorSetting farbe2 = new ColorSetting("Color 2", 0xFF111827);
    private static final ColorSetting musterFarbe = new ColorSetting("Pattern Color", 0x40FFFFFF);
    private static final ColorSetting symbolFarbe = new ColorSetting("Symbol Color", 0xFFFDE68A);
    private static final ColorSetting randFarbe = new ColorSetting("Border Color", 0xFFFDE68A);
    private static final ColorSetting malFarbe = new ColorSetting("Paint Color", 0xFFFFFFFF);
    private static final StringSetting text = new StringSetting("Text", "", "Up to 10 letters, shown under the symbol");
    private static int[] mal = new int[PW * PH];

    private final Screen parent;
    private final Deque<int[]> undo = new ArrayDeque<>();
    private BufferedImage bild;
    private boolean schmutzig = true;
    private long letzteTextur = 0;
    private Identifier textur;
    private int texturNr = 0;
    private String meldung = null;
    private boolean malt = false;

    private record Flaeche(float x, float y, float w, float h, Runnable links, Runnable rechts) {}
    private final List<Flaeche> flaechen = new ArrayList<>();
    private int fx, fy, fw, fh, cx, cy, cw, ch;

    public CapeEditorScreen(Screen parent) {
        super(Component.literal("Cape Editor"));
        this.parent = parent;
        laden();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Das komplette Bild der Rueckseite: Entwurf + Malschicht. */
    static BufferedImage bauen() {
        BufferedImage img = new BufferedImage(BW, BH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        Color c1 = farbe(farbe1.get()), c2 = farbe(farbe2.get());

        switch (hintergrund) {
            case 1 -> g.setPaint(new GradientPaint(0, 0, c1, 0, BH, c2));
            case 2 -> g.setPaint(new GradientPaint(0, 0, c1, BW, 0, c2));
            case 3 -> g.setPaint(new GradientPaint(0, 0, c1, BW, BH, c2));
            case 4 -> g.setPaint(new RadialGradientPaint(BW / 2f, BH * 0.42f, BH * 0.7f, new float[]{0f, 1f}, new Color[]{c1, c2}));
            default -> g.setPaint(c1);
        }
        g.fillRect(0, 0, BW, BH);

        if (muster > 0) {
            g.setColor(farbe(musterFarbe.get()));
            switch (muster) {
                case 1 -> { for (int x = 4; x < BW; x += 16) g.fillRect(x, 0, 6, BH); }
                case 2 -> {
                    g.setStroke(new BasicStroke(5f));
                    for (int k = -BH; k < BW + BH; k += 16) g.drawLine(k, 0, k - BH, BH);
                }
                case 3 -> {
                    g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                    for (int y = -8; y < BH + 16; y += 18) {
                        Path2D p = new Path2D.Float();
                        p.moveTo(0, y);
                        p.lineTo(BW / 2f, y + 14);
                        p.lineTo(BW, y);
                        g.draw(p);
                    }
                }
                case 4 -> {
                    for (int y = 0; y < BH; y += 16) for (int x = 0; x < BW; x += 16)
                        if (((x + y) / 16) % 2 == 0) g.fillRect(x, y, 16, 16);
                }
                case 5 -> {
                    for (int y = 6; y < BH; y += 14) for (int x = (y / 14) % 2 == 0 ? 6 : 13; x < BW; x += 14)
                        g.fill(new Ellipse2D.Float(x - 3, y - 3, 6, 6));
                }
                case 6 -> {
                    for (int y = 0; y < BH + 20; y += 20) for (int x = (y / 20) % 2 == 0 ? 0 : 10; x < BW + 20; x += 20) {
                        Path2D p = new Path2D.Float();
                        p.moveTo(x, y - 7);
                        p.lineTo(x + 6, y);
                        p.lineTo(x, y + 7);
                        p.lineTo(x - 6, y);
                        p.closePath();
                        g.fill(p);
                    }
                }
                case 7 -> {
                    g.setStroke(new BasicStroke(3.5f));
                    for (int y = 6; y < BH; y += 14) {
                        Path2D p = new Path2D.Float();
                        p.moveTo(0, y);
                        for (int x = 0; x <= BW; x += 2) p.lineTo(x, y + Math.sin(x / 80.0 * Math.PI * 4) * 4);
                        g.draw(p);
                    }
                }
                case 8 -> {
                    java.util.Random r = new java.util.Random(7);
                    for (int i = 0; i < 26; i++) {
                        float sx = r.nextFloat() * BW, sy = r.nextFloat() * BH, sg = 2 + r.nextFloat() * 3;
                        g.fill(stern(sx, sy, sg, sg * 0.45f, 4));
                    }
                }
                default -> { }
            }
        }

        float gr = switch (groesse) { case 0 -> 26; case 2 -> 58; default -> 42; };
        String t = text.get() == null ? "" : text.get().trim();
        if (t.length() > 10) t = t.substring(0, 10);
        float textH = t.isEmpty() ? 0 : 16;
        float block = (symbol > 0 ? gr : 0) + (symbol > 0 && !t.isEmpty() ? 6 : 0) + textH;
        float oben = switch (ort) {
            case 0 -> 12;
            case 2 -> BH - 12 - block;
            default -> (BH - block) / 2f;
        };
        if (symbol > 0) {
            g.setColor(farbe(symbolFarbe.get()));
            AffineTransform alt = g.getTransform();
            g.translate(BW / 2f, oben + gr / 2f);
            g.scale(gr / 100f, gr / 100f);
            g.fill(symbolForm(symbol));
            g.setTransform(alt);
        }
        if (!t.isEmpty()) {
            g.setColor(farbe(symbolFarbe.get()));
            int fs = 15;
            Font f = new Font(Font.SANS_SERIF, Font.BOLD, fs);
            g.setFont(f);
            while (fs > 7 && g.getFontMetrics().stringWidth(t) > BW - 8) {
                fs--;
                f = new Font(Font.SANS_SERIF, Font.BOLD, fs);
                g.setFont(f);
            }
            int tw = g.getFontMetrics().stringWidth(t);
            float ty = oben + (symbol > 0 ? gr + 6 : 0) + g.getFontMetrics().getAscent() - 2;
            g.drawString(t, (BW - tw) / 2f, ty);
        }

        if (rand) {
            g.setColor(farbe(randFarbe.get()));
            g.setStroke(new BasicStroke(4f));
            g.drawRect(2, 2, BW - 4, BH - 4);
        }

        for (int y = 0; y < PH; y++) for (int x = 0; x < PW; x++) {
            int f = mal[y * PW + x];
            if ((f >>> 24) == 0) continue;
            g.setColor(farbe(f));
            g.fillRect(x * 2, y * 2, 2, 2);
        }
        g.dispose();
        return img;
    }

    private static Color farbe(int argb) {
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    /** Symbole in einem Feld von -50..50. */
    private static java.awt.Shape symbolForm(int s) {
        switch (s) {
            case 1: return stern(0, 0, 50, 21, 5);
            case 2: {
                GeneralPath p = new GeneralPath();
                p.moveTo(0, 42);
                p.curveTo(-48, 8, -40, -38, -12, -36);
                p.curveTo(-4, -35, 0, -26, 0, -22);
                p.curveTo(0, -26, 4, -35, 12, -36);
                p.curveTo(40, -38, 48, 8, 0, 42);
                p.closePath();
                return p;
            }
            case 3: {
                GeneralPath p = new GeneralPath();
                p.moveTo(10, -50); p.lineTo(-26, 6); p.lineTo(-2, 6); p.lineTo(-12, 50);
                p.lineTo(28, -10); p.lineTo(4, -10); p.closePath();
                return p;
            }
            case 4: {
                java.awt.geom.Area a = new java.awt.geom.Area(new Ellipse2D.Float(-42, -42, 84, 84));
                a.subtract(new java.awt.geom.Area(new Ellipse2D.Float(-22, -52, 84, 84)));
                return a;
            }
            case 5: {
                GeneralPath p = new GeneralPath();
                p.moveTo(-44, 30); p.lineTo(-48, -22); p.lineTo(-22, 2); p.lineTo(0, -34);
                p.lineTo(22, 2); p.lineTo(48, -22); p.lineTo(44, 30); p.closePath();
                java.awt.geom.Area a = new java.awt.geom.Area(p);
                a.add(new java.awt.geom.Area(new java.awt.Rectangle(-44, 34, 88, 10)));
                return a;
            }
            case 6: {
                GeneralPath p = new GeneralPath();
                p.moveTo(0, -50); p.lineTo(8, -40); p.lineTo(8, 18); p.lineTo(24, 18); p.lineTo(24, 26);
                p.lineTo(6, 26); p.lineTo(6, 44); p.lineTo(10, 50); p.lineTo(-10, 50); p.lineTo(-6, 44);
                p.lineTo(-6, 26); p.lineTo(-24, 26); p.lineTo(-24, 18); p.lineTo(-8, 18); p.lineTo(-8, -40);
                p.closePath();
                return p;
            }
            case 7: {
                GeneralPath p = new GeneralPath();
                p.moveTo(-30, -24); p.lineTo(30, -24); p.lineTo(44, -6); p.lineTo(0, 46);
                p.lineTo(-44, -6); p.closePath();
                return p;
            }
            case 8: {
                GeneralPath p = new GeneralPath();
                p.moveTo(0, -50);
                p.curveTo(20, -20, 40, -2, 32, 22);
                p.curveTo(26, 42, 8, 48, 0, 48);
                p.curveTo(-8, 48, -26, 42, -32, 22);
                p.curveTo(-38, 2, -22, -12, -16, -24);
                p.curveTo(-10, -8, -2, -6, 0, -50);
                p.closePath();
                return p;
            }
            case 9: {
                GeneralPath p = new GeneralPath();
                p.moveTo(-46, -40); p.lineTo(-22, -40); p.lineTo(0, 18); p.lineTo(22, -40);
                p.lineTo(46, -40); p.lineTo(10, 46); p.lineTo(-10, 46); p.closePath();
                return p;
            }
            default: return new java.awt.Rectangle(0, 0, 0, 0);
        }
    }

    private static java.awt.Shape stern(float cx, float cy, float aussen, float innen, int zacken) {
        GeneralPath p = new GeneralPath();
        for (int i = 0; i < zacken * 2; i++) {
            double w = Math.PI / zacken * i - Math.PI / 2;
            float r = i % 2 == 0 ? aussen : innen;
            float x = cx + (float) Math.cos(w) * r, y = cy + (float) Math.sin(w) * r;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }

    /** Vorschau-Textur neu bauen (hoechstens zehnmal pro Sekunde). */
    private void texturAktualisieren() {
        long jetzt = System.currentTimeMillis();
        if (!schmutzig || jetzt - letzteTextur < 100) return;
        letzteTextur = jetzt;
        schmutzig = false;
        try {
            bild = bauen();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bild, "png", out);
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
            var tm = Minecraft.getInstance().getTextureManager();
            Identifier neu = Identifier.fromNamespaceAndPath("vortexclient", "cape/editor_" + (texturNr++));
            tm.register(neu, new DynamicTexture(() -> "vortexclient-cape-editor", ni));
            if (textur != null) tm.release(textur);
            textur = neu;
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("CapeEditor.textur", e);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        flaechen.clear();
        texturAktualisieren();
        int akzent = com.vortex.client.gui.Theme.INSTANCE.accent.get() | 0xFF000000;
        g.fill(0, 0, this.width, this.height, 0xC8060409);
        fw = Math.min(this.width - 16, 560);
        fh = Math.min(this.height - 16, 340);
        fx = (this.width - fw) / 2;
        fy = (this.height - fh) / 2;
        Glatt.schatten(g, fx, fy + 4, fw, fh, 12, 16, 0x90000000);
        Glatt.rund(g, fx, fy, fw, fh, 12, FELD);
        Glatt.rahmen(g, fx, fy, fw, fh, 12, 1, 0x16FFFFFF);
        Glatt.symbol(g, Symbole.Symbol.REGLER, fx + 14, fy + 11, 12, akzent);
        Glatt.text(g, "Cape Editor", fx + 32, fy + 12, TEXT, Glatt.Schrift.FETT);

        int innenH = fh - 44 - 34;
        ch = innenH;
        cw = Math.round(ch * 10f / 16f);
        cx = fx + 16;
        cy = fy + 34;
        Glatt.rund(g, cx - 6, cy - 6, cw + 12, ch + 12, 8, KARTE);
        if (textur != null) {
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, textur, cx, cy, 0, 0, cw, ch, BW, BH, BW, BH, 0xFFFFFFFF);
        }
        if (mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch) {
            int[] p = punkt(mouseX, mouseY);
            float zellW = cw / (float) PW, zellH = ch / (float) PH;
            int r = werkzeug <= 1 ? pinsel - 1 : 0;
            float x0 = cx + (p[0] - r) * zellW, y0 = cy + (p[1] - r) * zellH;
            float s = (2 * r + 1);
            Glatt.rahmen(g, x0, y0, zellW * s, zellH * s, 0, 1, 0xCCFFFFFF);
        }

        int px = cx + cw + 22, pw = fx + fw - 16 - px;
        int y = cy - 2;
        int verf = (fy + fh - 36) - y;
        kompakt = verf < 2 * 13 + 6 + 8 * 24;
        zeilenH = kompakt ? Math.max(13, verf / 8 - 2) : 20;
        luecke = kompakt ? 2 : 4;
        if (!kompakt) y = abschnitt(g, "DESIGN", px, y);
        y = zeile(g, px, y, pw, "Background", HINTERGRUENDE[hintergrund], mouseX, mouseY,
                () -> hintergrund = (hintergrund + 1) % HINTERGRUENDE.length, () -> hintergrund = (hintergrund + HINTERGRUENDE.length - 1) % HINTERGRUENDE.length,
                hintergrund == 0 ? new ColorSetting[]{farbe1} : new ColorSetting[]{farbe1, farbe2});
        y = zeile(g, px, y, pw, "Pattern", MUSTER[muster], mouseX, mouseY,
                () -> muster = (muster + 1) % MUSTER.length, () -> muster = (muster + MUSTER.length - 1) % MUSTER.length,
                muster == 0 ? new ColorSetting[0] : new ColorSetting[]{musterFarbe});
        y = zeile(g, px, y, pw, "Symbol", SYMBOLE[symbol] + (symbol > 0 ? "  ·  " + GROESSEN[groesse] : ""), mouseX, mouseY,
                () -> symbol = (symbol + 1) % SYMBOLE.length, () -> symbol = (symbol + SYMBOLE.length - 1) % SYMBOLE.length,
                new ColorSetting[]{symbolFarbe});
        y = zeile(g, px, y, pw, "Size, place", GROESSEN[groesse] + "  ·  " + ORTE[ort], mouseX, mouseY,
                () -> { groesse = (groesse + 1) % GROESSEN.length; if (groesse == 0) ort = (ort + 1) % ORTE.length; },
                () -> ort = (ort + 1) % ORTE.length, new ColorSetting[0]);
        String tx = text.get() == null || text.get().isBlank() ? "None" : "\"" + text.get().trim() + "\"";
        y = zeile(g, px, y, pw, "Text", tx, mouseX, mouseY,
                () -> this.minecraft.gui.setScreen(new TextEingabeScreen(this, "Cape text", text.get(), "Up to 10 letters", t -> {
                    text.set(t.length() > 10 ? t.substring(0, 10) : t);
                    schmutzig = true;
                })), () -> { text.set(""); schmutzig = true; }, new ColorSetting[0]);
        y = zeile(g, px, y, pw, "Border", rand ? "On" : "Off", mouseX, mouseY, () -> rand = !rand, () -> rand = !rand,
                rand ? new ColorSetting[]{randFarbe} : new ColorSetting[0]);

        if (!kompakt) {
            y += 6;
            y = abschnitt(g, "PAINT", px, y);
        }
        float bw = (pw - 3 * 4) / 4f;
        int th = zeilenH - 2;
        for (int i = 0; i < WERKZEUGE.length; i++) {
            float bx = px + i * (bw + 4);
            boolean an = werkzeug == i, hov = in(mouseX, mouseY, bx, y, bw, th);
            Glatt.rund(g, bx, y, bw, th, 6, an ? akzent : hov ? KARTE_HELL : KARTE);
            Glatt.textMitte(g, WERKZEUGE[i], bx + bw / 2f, y + (th - 9) / 2f + 0.5f, an || hov ? TEXT : DIM, Glatt.Schrift.FETT);
            final int w = i;
            flaechen.add(new Flaeche(bx, y, bw, th, () -> werkzeug = w, () -> werkzeug = w));
        }
        y += th + luecke + (kompakt ? 0 : 2);
        y = zeile(g, px, y, pw, "Brush size", pinsel + " px", mouseX, mouseY, () -> pinsel = pinsel % 3 + 1,
                () -> pinsel = (pinsel + 1) % 3 + 1, new ColorSetting[]{malFarbe});

        float ky = fy + fh - 30;
        float kx = fx + fw - 16;
        kx = knopf(g, kx, ky, "Save & wear", true, akzent, mouseX, mouseY, this::speichernUndTragen);
        kx = knopf(g, kx - 6, ky, "Cancel", false, akzent, mouseX, mouseY, this::onClose);
        kx = knopf(g, kx - 6, ky, "Clear paint", false, akzent, mouseX, mouseY, () -> { merken(); mal = new int[PW * PH]; schmutzig = true; });
        kx = knopf(g, kx - 6, ky, "Undo", false, akzent, mouseX, mouseY, () -> {
            if (!undo.isEmpty()) { mal = undo.pop(); schmutzig = true; }
        });
        String hin = meldung != null ? meldung : "Click a value to change it  ·  right-click goes back";
        Glatt.text(g, Glatt.kuerzen(hin, (int) (kx - 12 - (fx + 16)), Glatt.Schrift.NORMAL), fx + 16, ky + 6, DUNKEL, Glatt.Schrift.NORMAL);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private int abschnitt(GuiGraphicsExtractor g, String titel, int x, int y) {
        Glatt.text(g, titel, x + 2, y, DUNKEL, Glatt.Schrift.FETT);
        return y + 13;
    }

    /** Eine Einstellungszeile: Name links, Wert (Klick/Rechtsklick), rechts Farbfelder. */
    private boolean kompakt = false;
    private int zeilenH = 20, luecke = 4;

    private int zeile(GuiGraphicsExtractor g, int x, int y, int w, String name, String wert, int mx, int my,
                      Runnable weiter, Runnable zurueck, ColorSetting[] farben) {
        int h = zeilenH;
        boolean hov = in(mx, my, x, y, w, h);
        Glatt.rund(g, x, y, w, h, 6, hov ? KARTE_HELL : KARTE);
        float ty = y + (h - 9) / 2f + 0.5f;
        Glatt.text(g, name, x + 8, ty, DIM, Glatt.Schrift.NORMAL);
        float fb = farben.length * 16;
        float wx = x + 82;
        Glatt.text(g, Glatt.kuerzen(wert, (int) (x + w - fb - 10 - wx), Glatt.Schrift.FETT), wx, ty, TEXT, Glatt.Schrift.FETT);
        flaechen.add(new Flaeche(x, y, w - fb - 4, h, () -> { weiter.run(); schmutzig = true; }, () -> { zurueck.run(); schmutzig = true; }));
        for (int i = 0; i < farben.length; i++) {
            ColorSetting cs = farben[i];
            float sx = x + w - (farben.length - i) * 16 - 2;
            float sy = y + (h - 12) / 2f;
            Glatt.rund(g, sx, sy, 12, 12, 3, 0xFFFFFFFF);
            Glatt.rund(g, sx + 1, sy + 1, 10, 10, 2.5f, cs.get() | 0xFF000000);
            flaechen.add(new Flaeche(sx - 1, y, 14, h,
                    () -> this.minecraft.gui.setScreen(new ColorPickerScreen(this, cs, () -> schmutzig = true)),
                    () -> this.minecraft.gui.setScreen(new ColorPickerScreen(this, cs, () -> schmutzig = true))));
        }
        return y + h + luecke;
    }

    private float knopf(GuiGraphicsExtractor g, float rechts, float y, String t, boolean haupt, int akzent, int mx, int my, Runnable r) {
        float w = Glatt.breite(t, Glatt.Schrift.FETT) + 20, x = rechts - w, h = 20;
        boolean hov = in(mx, my, x, y, w, h);
        Glatt.rund(g, x, y, w, h, 7, haupt ? (hov ? Glatt.mix(akzent, 0xFFFFFFFF, 0.15f) : akzent) : (hov ? 0x26FFFFFF : 0x12FFFFFF));
        Glatt.textMitte(g, t, x + w / 2f, y + 6, haupt || hov ? TEXT : 0xFFA4A8B8, Glatt.Schrift.FETT);
        flaechen.add(new Flaeche(x, y, w, h, r, r));
        return x;
    }

    private static boolean in(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int[] punkt(double mx, double my) {
        int x = (int) Math.floor((mx - cx) / cw * PW), y = (int) Math.floor((my - cy) / ch * PH);
        return new int[]{Math.max(0, Math.min(PW - 1, x)), Math.max(0, Math.min(PH - 1, y))};
    }

    private void merken() {
        undo.push(mal.clone());
        while (undo.size() > 30) undo.removeLast();
    }

    private void malen(double mx, double my, boolean neu) {
        int[] p = punkt(mx, my);
        switch (werkzeug) {
            case 0, 1 -> {
                if (neu) merken();
                int f = werkzeug == 0 ? malFarbe.get() : 0;
                int r = pinsel - 1;
                for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                    int x = p[0] + dx, y = p[1] + dy;
                    if (x >= 0 && y >= 0 && x < PW && y < PH) mal[y * PW + x] = f;
                }
            }
            case 2 -> {
                if (!neu) return;
                merken();
                fuellen(p[0], p[1], malFarbe.get());
            }
            case 3 -> {
                if (!neu) return;
                if (bild == null) bild = bauen();
                int f = bild.getRGB(p[0] * 2, p[1] * 2);
                malFarbe.set(f | 0xFF000000);
                werkzeug = 0;
                meldung = "Picked a colour -- back to the brush.";
            }
            default -> { }
        }
        schmutzig = true;
    }

    /** Farbeimer: zusammenhaengende Flaeche gleicher Farbe (wie sie zu sehen ist) fuellen. */
    private void fuellen(int sx, int sy, int neu) {
        if (bild == null) bild = bauen();
        int[] sicht = new int[PW * PH];
        for (int y = 0; y < PH; y++) for (int x = 0; x < PW; x++) sicht[y * PW + x] = bild.getRGB(x * 2, y * 2);
        int alt = sicht[sy * PW + sx];
        boolean[] gesehen = new boolean[PW * PH];
        Deque<int[]> offen = new ArrayDeque<>();
        offen.add(new int[]{sx, sy});
        while (!offen.isEmpty()) {
            int[] q = offen.poll();
            int x = q[0], y = q[1];
            if (x < 0 || y < 0 || x >= PW || y >= PH) continue;
            int i = y * PW + x;
            if (gesehen[i] || !aehnlich(sicht[i], alt)) continue;
            gesehen[i] = true;
            mal[i] = neu;
            offen.add(new int[]{x + 1, y});
            offen.add(new int[]{x - 1, y});
            offen.add(new int[]{x, y + 1});
            offen.add(new int[]{x, y - 1});
        }
    }

    /** Verlaeufe sind nie ganz gleich -- kleine Unterschiede zaehlen als dieselbe Farbe. */
    private static boolean aehnlich(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return dr + dg + db <= 24;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();
        if (click.button() == 0 && in(mx, my, cx, cy, cw, ch)) {
            malt = true;
            meldung = null;
            malen(mx, my, true);
            return true;
        }
        for (int i = flaechen.size() - 1; i >= 0; i--) {
            Flaeche f = flaechen.get(i);
            if (in(mx, my, f.x(), f.y(), f.w(), f.h())) {
                meldung = null;
                if (click.button() == 1) f.rechts().run(); else if (click.button() == 0) f.links().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (malt && werkzeug <= 1) {
            malen(event.x(), event.y(), false);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        malt = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == 90 && (e.modifiers() & 2) != 0) {
            if (!undo.isEmpty()) { mal = undo.pop(); schmutzig = true; }
            return true;
        }
        return super.keyPressed(e);
    }

    private void speichernUndTragen() {
        BufferedImage fertig = bauen();
        String fehler = EigenesCape.bauenAus(fertig);
        if (fehler != null) {
            meldung = fehler;
            return;
        }
        sichern();
        Cosmetics.speichern(Cosmetics.eigene().mitCape(EigenesCape.ID), Cosmetics.partikelErstePerson());
        ActiveCape.neuLaden();
        meldung = "Saved -- you are wearing your cape now.";
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        sichern();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        if (textur != null && !(this.minecraft.gui.screen() instanceof ColorPickerScreen) && !(this.minecraft.gui.screen() instanceof TextEingabeScreen)) {
            try { this.minecraft.getTextureManager().release(textur); } catch (Throwable ignored) { }
            textur = null;
            schmutzig = true;
        }
        super.removed();
    }

    private static Path entwurf() { return Cosmetics.ordner().resolve("cape-editor.json"); }
    private static Path malDatei() { return Cosmetics.ordner().resolve("cape-editor-paint.png"); }

    private static void sichern() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("background", hintergrund);
            o.addProperty("pattern", muster);
            o.addProperty("symbol", symbol);
            o.addProperty("size", groesse);
            o.addProperty("place", ort);
            o.addProperty("border", rand);
            o.addProperty("color1", farbe1.get());
            o.addProperty("color2", farbe2.get());
            o.addProperty("patternColor", musterFarbe.get());
            o.addProperty("symbolColor", symbolFarbe.get());
            o.addProperty("borderColor", randFarbe.get());
            o.addProperty("paintColor", malFarbe.get());
            o.addProperty("text", text.get() == null ? "" : text.get());
            Files.createDirectories(Cosmetics.ordner());
            Files.writeString(entwurf(), o.toString(), StandardCharsets.UTF_8);
            BufferedImage m = new BufferedImage(PW, PH, BufferedImage.TYPE_INT_ARGB);
            m.setRGB(0, 0, PW, PH, mal, 0, PW);
            ImageIO.write(m, "png", malDatei().toFile());
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("CapeEditor.sichern", e);
        }
    }

    private static void laden() {
        if (geladen) return;
        geladen = true;
        try {
            if (Files.exists(entwurf())) {
                JsonObject o = JsonParser.parseString(Files.readString(entwurf(), StandardCharsets.UTF_8)).getAsJsonObject();
                hintergrund = zahl(o, "background", hintergrund, HINTERGRUENDE.length);
                muster = zahl(o, "pattern", muster, MUSTER.length);
                symbol = zahl(o, "symbol", symbol, SYMBOLE.length);
                groesse = zahl(o, "size", groesse, GROESSEN.length);
                ort = zahl(o, "place", ort, ORTE.length);
                rand = !o.has("border") || o.get("border").getAsBoolean();
                if (o.has("color1")) farbe1.set(o.get("color1").getAsInt());
                if (o.has("color2")) farbe2.set(o.get("color2").getAsInt());
                if (o.has("patternColor")) musterFarbe.set(o.get("patternColor").getAsInt());
                if (o.has("symbolColor")) symbolFarbe.set(o.get("symbolColor").getAsInt());
                if (o.has("borderColor")) randFarbe.set(o.get("borderColor").getAsInt());
                if (o.has("paintColor")) malFarbe.set(o.get("paintColor").getAsInt());
                if (o.has("text")) text.set(o.get("text").getAsString());
            }
            if (Files.exists(malDatei())) {
                BufferedImage m = ImageIO.read(malDatei().toFile());
                if (m != null && m.getWidth() == PW && m.getHeight() == PH) mal = m.getRGB(0, 0, PW, PH, null, 0, PW);
            }
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("CapeEditor.laden", e);
        }
    }

    private static int zahl(JsonObject o, String k, int vorgabe, int max) {
        try { return o.has(k) ? Math.max(0, Math.min(max - 1, o.get(k).getAsInt())) : vorgabe; }
        catch (Throwable e) { return vorgabe; }
    }
}
