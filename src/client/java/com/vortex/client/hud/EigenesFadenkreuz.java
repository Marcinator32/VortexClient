package com.vortex.client.hud;

import com.vortex.client.module.modules.CrosshairModule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * Selbst gemaltes Fadenkreuz (Crosshair-Editor): 15 x 15 Pixel, 0 = leer,
 * 1 = Fadenkreuz-Farbe, 2 = Umrandungs-Farbe. Liegt als kleine Textdatei neben
 * der Config. Dazu die Teilen-Codes ("VXC1:...") mit allen Formeinstellungen.
 */
public final class EigenesFadenkreuz {

    private EigenesFadenkreuz() {}

    public static final int GROESSE = 15;
    private static byte[] pixel;

    public record Vorlage(String name, int stil, int groesse, int dicke, int luecke, int punkt, int farbe,
                          boolean rand, String[] bild) {}

    private static String[] b(String... zeilen) { return zeilen; }

    public static final List<Vorlage> VORLAGEN = List.of(
            new Vorlage("Classic", 0, 5, 1, 2, 1, 0xFFFFFFFF, true, null),
            new Vorlage("Tiny", 1, 2, 1, 1, 1, 0xFFFFFFFF, true, null),
            new Vorlage("Pro Dot", 2, 1, 2, 0, 2, 0xFF4CFF6A, true, null),
            new Vorlage("CS Green", 0, 4, 1, 1, 1, 0xFF4CFF4C, true, null),
            new Vorlage("Ring", 4, 6, 1, 2, 1, 0xFFFFFFFF, true, null),
            new Vorlage("Sniper", 8, 8, 1, 3, 1, 0xFFFF4D4D, true, null),
            new Vorlage("Heart", 9, 5, 1, 2, 1, 0xFFFF5C8A, false, b(
                    "...............",
                    "...............",
                    "...............",
                    "....22...22....",
                    "...2112.2112...",
                    "..21111111112..",
                    "..21111111112..",
                    "...211111112...",
                    "....2111112....",
                    ".....21112.....",
                    "......212......",
                    ".......2.......",
                    "...............",
                    "...............",
                    "...............")),
            new Vorlage("Diamond", 9, 5, 1, 2, 1, 0xFF7DE3FF, true, b(
                    "...............",
                    "...............",
                    ".......1.......",
                    "......1.1......",
                    ".....1...1.....",
                    "....1.....1....",
                    "...1.......1...",
                    "..1....1....1..",
                    "...1.......1...",
                    "....1.....1....",
                    ".....1...1.....",
                    "......1.1......",
                    ".......1.......",
                    "...............",
                    "...............")),
            new Vorlage("Brackets", 9, 5, 1, 2, 1, 0xFFFFFFFF, true, b(
                    "...............",
                    "...............",
                    "...............",
                    "...111...111...",
                    "...1.......1...",
                    "...1.......1...",
                    "...............",
                    ".......1.......",
                    "...............",
                    "...1.......1...",
                    "...1.......1...",
                    "...111...111...",
                    "...............",
                    "...............",
                    "...............")));

    private static Path datei() {
        return com.vortex.client.core.ConfigManager.dataDir().resolve("crosshair-custom.txt");
    }

    /** Die gemalten Pixel (Zeile fuer Zeile). Beim ersten Mal ein Kreuz als Vorlage. */
    public static synchronized byte[] pixel() {
        if (pixel == null) {
            pixel = new byte[GROESSE * GROESSE];
            boolean geladen = false;
            try {
                Path d = datei();
                if (Files.isRegularFile(d)) {
                    List<String> z = Files.readAllLines(d, StandardCharsets.UTF_8);
                    for (int y = 0; y < Math.min(GROESSE, z.size()); y++) {
                        String zeile = z.get(y);
                        for (int x = 0; x < Math.min(GROESSE, zeile.length()); x++) {
                            char c = zeile.charAt(x);
                            pixel[y * GROESSE + x] = (byte) (c == '1' ? 1 : c == '2' ? 2 : 0);
                        }
                    }
                    geladen = true;
                }
            } catch (Exception ignored) { }
            if (!geladen) {
                int m = GROESSE / 2;
                for (int i = 2; i < GROESSE - 2; i++) {
                    if (Math.abs(i - m) < 2) continue;
                    pixel[m * GROESSE + i] = 1;
                    pixel[i * GROESSE + m] = 1;
                }
                pixel[m * GROESSE + m] = 1;
            }
        }
        return pixel;
    }

    public static synchronized void setze(int x, int y, int wert) {
        if (x < 0 || y < 0 || x >= GROESSE || y >= GROESSE) return;
        pixel()[y * GROESSE + x] = (byte) wert;
    }

    public static synchronized void alles(byte[] neu) {
        if (neu == null || neu.length != GROESSE * GROESSE) return;
        pixel = neu.clone();
        speichern();
    }

    public static synchronized void speichern() {
        StringBuilder sb = new StringBuilder();
        byte[] p = pixel();
        for (int y = 0; y < GROESSE; y++) {
            for (int x = 0; x < GROESSE; x++) sb.append((char) ('0' + p[y * GROESSE + x]));
            sb.append('\n');
        }
        try {
            Files.createDirectories(datei().getParent());
            Files.writeString(datei(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("EigenesFadenkreuz.speichern", e);
        }
    }

    public static byte[] ausBild(String[] bild) {
        byte[] p = new byte[GROESSE * GROESSE];
        for (int y = 0; y < Math.min(GROESSE, bild.length); y++) {
            for (int x = 0; x < Math.min(GROESSE, bild[y].length()); x++) {
                char c = bild[y].charAt(x);
                p[y * GROESSE + x] = (byte) (c == '1' ? 1 : c == '2' ? 2 : 0);
            }
        }
        return p;
    }

    public static void anwenden(Vorlage v, CrosshairModule m) {
        m.style.setIndex(v.stil());
        m.size.set(v.groesse());
        m.thickness.set(v.dicke());
        m.gap.set(v.luecke());
        m.dotSize.set(v.punkt());
        m.color.set(v.farbe());
        m.rainbow.set(false);
        m.outline.set(v.rand());
        if (v.bild() != null) alles(ausBild(v.bild()));
    }

    /** Teilen-Code mit Form, Farben und den gemalten Pixeln. */
    public static String code(CrosshairModule m) {
        byte[] p = pixel();
        byte[] d = new byte[16 + (GROESSE * GROESSE + 3) / 4];
        d[0] = 1;
        d[1] = (byte) m.style.getIndex();
        d[2] = (byte) m.size.getInt();
        d[3] = (byte) m.thickness.getInt();
        d[4] = (byte) m.gap.getInt();
        d[5] = (byte) m.dotSize.getInt();
        d[6] = (byte) m.customScale.getInt();
        d[7] = (byte) ((m.outline.get() ? 1 : 0) | (m.rainbow.get() ? 2 : 0) | (m.hitMarker.get() ? 4 : 0));
        schreibeInt(d, 8, m.color.get());
        schreibeInt(d, 12, m.outlineColor.get());
        for (int i = 0; i < p.length; i++) d[16 + i / 4] |= (byte) ((p[i] & 3) << ((i % 4) * 2));
        return "VXC1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(d);
    }

    /** Code einlesen; false, wenn er nicht passt. */
    public static boolean ausCode(String code, CrosshairModule m) {
        if (code == null) return false;
        code = code.trim();
        if (!code.startsWith("VXC1:")) return false;
        byte[] d;
        try {
            d = Base64.getUrlDecoder().decode(code.substring(5));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (d.length < 16 + (GROESSE * GROESSE + 3) / 4 || d[0] != 1) return false;
        if (d[1] < 0 || d[1] >= m.style.size()) return false;
        m.style.setIndex(d[1]);
        m.size.set(d[2]);
        m.thickness.set(d[3]);
        m.gap.set(d[4]);
        m.dotSize.set(d[5]);
        m.customScale.set(d[6]);
        m.outline.set((d[7] & 1) != 0);
        m.rainbow.set((d[7] & 2) != 0);
        m.hitMarker.set((d[7] & 4) != 0);
        m.color.set(leseInt(d, 8));
        m.outlineColor.set(leseInt(d, 12));
        byte[] p = new byte[GROESSE * GROESSE];
        for (int i = 0; i < p.length; i++) {
            int w = (d[16 + i / 4] >> ((i % 4) * 2)) & 3;
            p[i] = (byte) (w > 2 ? 0 : w);
        }
        alles(p);
        return true;
    }

    private static void schreibeInt(byte[] d, int o, int v) {
        d[o] = (byte) (v >>> 24); d[o + 1] = (byte) (v >>> 16); d[o + 2] = (byte) (v >>> 8); d[o + 3] = (byte) v;
    }

    private static int leseInt(byte[] d, int o) {
        return (d[o] & 0xFF) << 24 | (d[o + 1] & 0xFF) << 16 | (d[o + 2] & 0xFF) << 8 | (d[o + 3] & 0xFF);
    }
}
