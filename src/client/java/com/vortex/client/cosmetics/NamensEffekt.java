package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Animierte Namen (seit 4.63): ueber dem Kopf, im Chat und in der Tab-Liste.
 *
 * Wer einen Effekt waehlt, dessen Name bewegt sich fuer alle Vortex-Spieler, die ihn
 * sehen (die Auswahl reist mit den anderen Cosmetics ueber den Freunde-Server). Ueber
 * dem Kopf wird jeder Buchstabe einzeln gezeichnet -- so koennen sie wackeln, wippen,
 * kippen und wachsen. Chat und Tab-Liste kennen nur Farben: dort laufen Farbverlaeufe,
 * und Bewegungs-Effekte zeigen einen wandernden Glanz.
 *
 * Alles laeuft auf derselben Uhr, damit ein Name an allen drei Stellen im Gleichtakt ist.
 */
public final class NamensEffekt {
    private NamensEffekt() {}

    /** Reihenfolge NIE aendern, nur hinten anhaengen -- die Nummer reist im Sync mit. */
    public static final String[] IDS = {
        "shaking", "waving", "iterating", "flipping", "skewing", "growing",
        "gradient", "rainbow", "metallic", "fade", "shimmer", "blinking",
        "chromatic", "fire", "outline", "glowing", "creator"
    };
    public static final String[] NAMEN = {
        "Shaking", "Waving", "Iterating", "Flipping", "Skewing", "Growing",
        "Gradient", "Rainbow", "Metallic", "Fade", "Shimmer", "Blinking",
        "Chromatic", "Fire", "Outline", "Glowing", "Creator Gold"
    };
    public static final String[] TEXTE = {
        "Letters tremble", "A wave runs through", "A spotlight hops along", "Letters flip over",
        "The name leans back and forth", "Letters swell one by one", "Violet to cyan, always moving",
        "Every colour, flowing", "Polished silver with a shine", "Breathes in and out", "Gold with a sparkle",
        "Blinks on and off", "Shifting colours with an RGB split", "Flickering flames", "Crisp black outline",
        "Glows in the dark", "Gold and violet, only for Vortex Creators"
    };

    private static final long START = System.nanoTime();

    /** Gemeinsame Uhr in Ticks (20 je Sekunde). */
    public static float zeit() {
        return (System.nanoTime() - START) / 5.0e7f;
    }

    public static int nummer(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    public static String id(int nr) {
        return nr >= 0 && nr < IDS.length ? IDS[nr] : "";
    }

    public static String name(String id) {
        int i = nummer(id);
        return i < 0 ? "" : NAMEN[i];
    }

    /** Bewegungs-Effekte (nur ueber dem Kopf); im Chat und Tab zeigen sie einen Glanz. */
    static boolean bewegt(String id) {
        return switch (id) { case "shaking", "waving", "iterating", "flipping", "skewing", "growing" -> true; default -> false; };
    }

    /** Farbe (RGB) von Buchstabe i (von n) zur Zeit t. */
    static int farbe(String stil, int i, int n, float t) {
        float u = n <= 1 ? 0 : i / (float) (n - 1);
        return switch (stil) {
            case "gradient" -> {
                float k = 0.5f + 0.5f * (float) Math.sin((u * 2.2f - t * 0.06f) * Math.PI);
                yield mix(0x8B5CF6, 0x22D3EE, k);
            }
            case "rainbow" -> hsv(frac(u * 0.6f - t * 0.012f), 0.72f, 1f);
            case "metallic" -> {
                float band = glanz(u, t, 0.045f);
                yield mix(mix(0x8A939E, 0xD9DEE4, 0.5f + 0.5f * (float) Math.sin(u * 7f)), 0xFFFFFF, band);
            }
            case "fade" -> mix(0x5B4E8A, 0xE9E3FF, 0.5f + 0.5f * (float) Math.sin(t * 0.12f));
            case "shimmer" -> mix(0xF5C542, 0xFFFFFF, glanz(u, t, 0.035f));
            case "blinking" -> ((int) (t / 10f)) % 2 == 0 ? 0xFFFFFF : 0x6B6480;
            case "chromatic" -> hsv(frac(t * 0.006f + u * 0.15f), 0.55f, 1f);
            case "fire" -> {
                float f = 0.5f + 0.5f * (float) Math.sin(t * 0.55f + i * 1.7f + (float) Math.sin(t * 0.21f + i) * 2f);
                yield mix(0xE8331C, mix(0xFF8A1F, 0xFFD65A, f), 0.35f + 0.65f * f);
            }
            case "glowing" -> mix(0x22D3EE, 0xA5F3FC, 0.5f + 0.5f * (float) Math.sin(t * 0.1f));
            case "outline" -> 0xFFFFFF;
            case "creator" -> mix(mix(0xFBBF24, 0xC084FC, 0.5f + 0.5f * (float) Math.sin((u * 1.6f - t * 0.05f) * Math.PI)), 0xFFFFFF, glanz(u, t, 0.04f));
            default -> mix(0xFFFFFF, 0xC4B5FD, glanz(u, t, 0.03f));
        };
    }

    /** Glanzstreifen, der alle ~5 s ueber den Namen laeuft (0..1 am Buchstaben u). */
    private static float glanz(float u, float t, float tempo) {
        float pos = frac(t * tempo) * 1.8f - 0.4f;
        float d = (u - pos) / 0.18f;
        return (float) Math.exp(-d * d);
    }

    /** Name mit Farbe je Buchstabe -- fuer die Tab-Liste (jedes Bild neu). */
    public static Component bunt(String name, String stil, Style basis) {
        float t = zeit();
        MutableComponent out = Component.empty();
        int[] cps = name.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            out.append(Component.literal(new String(Character.toChars(cps[i])))
                    .setStyle(basis.withColor(TextColor.fromRgb(farbe(stil, i, cps.length, t)))));
        }
        return out;
    }

    /**
     * Den Namen in einem fertigen Text (z. B. "[Admin] Name" in der Tab-Liste) einfaerben --
     * Rang, Farben und alles andere bleiben, nur die Buchstaben des Namens laufen bunt.
     */
    public static Component faerben(Component text, String name, String stil) {
        if (text == null || name == null || name.isEmpty()) return text;
        String ganz = text.getString();
        int von = ganz.indexOf(name);
        if (von < 0) return text;
        int a = ganz.codePointCount(0, von), n = name.codePointCount(0, name.length());
        float t = zeit();
        MutableComponent out = Component.empty();
        int[] pos = { 0 };
        text.visit((style, teil) -> {
            teil.codePoints().forEach(cp -> {
                int i = pos[0]++;
                String z = new String(Character.toChars(cp));
                if (i >= a && i < a + n) out.append(Component.literal(z).setStyle(style.withColor(TextColor.fromRgb(farbe(stil, i - a, n, t)))));
                else out.append(Component.literal(z).setStyle(style));
            });
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static Map<String, String> namen = Map.of();
    private static long namenZeit = 0;

    /** Namen der Spieler mit Effekt (Name -> Effekt), hoechstens einmal je Sekunde neu gesucht. */
    public static Map<String, String> aktiveNamen() {
        long jetzt = System.currentTimeMillis();
        if (jetzt - namenZeit < 1000) return namen;
        namenZeit = jetzt;
        Map<String, String> m = new HashMap<>();
        try {
            if (!Cosmetics.namenZeigen()) { namen = m; return m; }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String eigen = Cosmetics.getragen().namensstil();
                if (!eigen.isEmpty()) m.put(mc.player.getGameProfile().name(), eigen);
            }
            var con = mc.getConnection();
            if (con != null) {
                for (var info : con.getOnlinePlayers()) {
                    UUID u = info.getProfile().id();
                    String s = Cosmetics.fuer(u).namensstil();
                    if (!s.isEmpty() && nummer(s) >= 0) m.put(info.getProfile().name(), s);
                }
            }
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("NamensEffekt.namen", e);
        }
        namen = m;
        return m;
    }

    /** Effekt fuer einen Spielernamen (oder null). */
    public static String stilFuer(String name) {
        return name == null ? null : aktiveNamen().get(name);
    }

    /**
     * Chat-Zeile: Namen von Spielern mit Effekt bekommen beim Zeichnen ihre Farben.
     * Die gespeicherte Zeile bleibt, wie sie ist -- nur was gezeichnet wird, aendert sich.
     */
    public static FormattedCharSequence chatZeile(FormattedCharSequence zeile) {
        Map<String, String> m = aktiveNamen();
        if (m.isEmpty() || zeile == null) return zeile;
        return sink -> {
            java.util.List<int[]> cps = new java.util.ArrayList<>();
            java.util.List<Style> stile = new java.util.ArrayList<>();
            StringBuilder text = new StringBuilder();
            zeile.accept((idx, style, cp) -> { cps.add(new int[] { idx, cp }); stile.add(style); text.appendCodePoint(cp); return true; });
            String s = text.toString();
            int[] cpVonChar = new int[s.length() + 1];
            for (int ci = 0, k = 0; ci < s.length(); ) { int c = s.codePointAt(ci); int l = Character.charCount(c); for (int j = 0; j < l; j++) cpVonChar[ci + j] = k; ci += l; k++; }
            String[] stilJe = new String[cps.size()];
            int[] posJe = new int[cps.size()], laengeJe = new int[cps.size()];
            for (var e : m.entrySet()) {
                String name = e.getKey();
                int von = 0;
                while ((von = s.indexOf(name, von)) >= 0) {
                    int bis = von + name.length();
                    boolean links = von == 0 || !Character.isLetterOrDigit(s.charAt(von - 1)) && s.charAt(von - 1) != '_';
                    boolean rechts = bis >= s.length() || !Character.isLetterOrDigit(s.charAt(bis)) && s.charAt(bis) != '_';
                    if (links && rechts) {
                        int a = cpVonChar[von], n = name.codePointCount(0, name.length());
                        for (int j = 0; j < n && a + j < stilJe.length; j++) { stilJe[a + j] = e.getValue(); posJe[a + j] = j; laengeJe[a + j] = n; }
                    }
                    von = bis;
                }
            }
            float t = zeit();
            for (int i = 0; i < cps.size(); i++) {
                Style st = stile.get(i);
                if (stilJe[i] != null) st = st.withColor(TextColor.fromRgb(farbe(stilJe[i], posJe[i], laengeJe[i], t)));
                if (!sink.accept(cps.get(i)[0], st, cps.get(i)[1])) return false;
            }
            return true;
        };
    }

    /**
     * Name ueber dem Kopf, Buchstabe fuer Buchstabe. Lage wie das normale Namensschild
     * (0,5 ueber dem Anker, zur Kamera gedreht, 0,025 gross), mit Hintergrund und -- wenn
     * man nicht schleicht -- durch Waende sichtbar wie bei Minecraft.
     */
    public static void welt(PoseStack pose, SubmitNodeCollector q, CameraRenderState cam, Vec3 anker, String name, String stil,
                            int licht, boolean diskret, float yVersatz) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (cam == null || cam.orientation == null || anker == null || name.isEmpty()) return;
        float t = zeit();
        int[] cps = name.codePoints().toArray();
        String[] z = new String[cps.length];
        float[] breite = new float[cps.length];
        float gesamt = 0;
        for (int i = 0; i < cps.length; i++) { z[i] = new String(Character.toChars(cps[i])); breite[i] = font.width(z[i]); gesamt += breite[i]; }
        final float x0 = -gesamt / 2f, y0 = yVersatz;
        int hintergrund = mc.options.getBackgroundColor(0.25f);

        pose.pushPose();
        pose.translate(anker.x, anker.y + 0.5, anker.z);
        pose.mulPose(cam.orientation);
        pose.scale(0.025f, -0.025f, 0.025f);
        final float bg0 = x0 - 1, bg1 = x0 + gesamt + 1;
        q.submitCustomGeometry(pose, diskret ? RenderTypes.textBackground() : RenderTypes.textBackgroundSeeThrough(), (p, vc) -> {
            rechteck(p, vc, bg0, y0 - 1, bg1, y0 + 9, hintergrund, licht);
        });
        var text = q.order(1);
        float x = x0;
        boolean umriss = stil.equals("outline"), leuchten = stil.equals("glowing");
        for (int i = 0; i < cps.length; i++) {
            float w = breite[i];
            float u = cps.length <= 1 ? 0 : i / (float) (cps.length - 1);
            float dx = 0, dy = 0, sx = 1, sy = 1, schraeg = 0;
            float alpha = 1f;
            switch (stil) {
                case "shaking" -> {
                    int k = (int) (t / 1.5f);
                    dx = (zufall(i * 31 + k * 7) - 0.5f) * 1.4f;
                    dy = (zufall(i * 17 + k * 13 + 5) - 0.5f) * 1.4f;
                }
                case "waving" -> dy = (float) Math.sin(t * 0.28f - i * 0.65f) * 1.8f;
                case "iterating" -> {
                    float ph = (t * 0.35f) % (cps.length + 4);
                    float d = Math.abs(ph - i);
                    float hoch = d < 1 ? 1 - d : 0;
                    sx = sy = 1 + 0.35f * hoch;
                    dy = -1.5f * hoch;
                }
                case "flipping" -> {
                    float ph = frac(t * 0.012f - i * 0.06f);
                    if (ph < 0.12f) sx = (float) Math.cos(ph / 0.12f * Math.PI * 2);
                }
                case "skewing" -> schraeg = (float) Math.sin(t * 0.1f) * 0.35f;
                case "growing" -> {
                    float g = Math.max(0f, (float) Math.sin(t * 0.16f - i * 0.55f));
                    sx = sy = 1 + 0.3f * g * g;
                }
                case "fade" -> alpha = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 0.12f));
                case "blinking" -> alpha = ((int) (t / 10f)) % 2 == 0 ? 1f : 0.2f;
                case "fire" -> dy = -Math.max(0f, (float) Math.sin(t * 0.6f + i * 1.3f)) * 0.7f;
                default -> {}
            }
            int rgb = farbe(stil, i, cps.length, t);
            int a = Math.max(8, Math.round(alpha * 255));
            int farbe = (a << 24) | rgb;
            FormattedCharSequence zeichen = Component.literal(z[i]).getVisualOrderText();
            pose.pushPose();
            pose.translate(x + w / 2f + dx, y0 + 4 + dy, 0);
            if (schraeg != 0) pose.last().pose().mul(new Matrix4f().m10(-schraeg));
            pose.scale(sx, sy, 1);
            pose.translate(-w / 2f, -4, 0);
            if (stil.equals("chromatic")) {
                float v = 0.6f + 0.4f * (float) Math.sin(t * 0.2f);
                text.submitText(pose, -v, 0, zeichen, false, Font.DisplayMode.POLYGON_OFFSET, licht, 0x90FF3355, 0, 0);
                text.submitText(pose, v, 0, zeichen, false, Font.DisplayMode.POLYGON_OFFSET, licht, 0x9033CCFF, 0, 0);
            }
            if (!diskret) text.submitText(pose, 0, 0, zeichen, false, Font.DisplayMode.SEE_THROUGH, licht, (Math.min(a, 0x20) << 24) | rgb, 0, 0);
            int rand = umriss ? 0xFF000000 : leuchten ? (0xFF000000 | mix(rgb, 0x000000, 0.35f)) : 0;
            text.submitText(pose, 0, 0, zeichen, false, Font.DisplayMode.POLYGON_OFFSET, licht, farbe, 0, rand);
            pose.popPose();
            x += w;
        }
        pose.popPose();
    }

    private static void rechteck(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float x1, float y1, int farbe, int licht) {
        vc.addVertex(p, x0, y0, 0).setColor(farbe).setLight(licht);
        vc.addVertex(p, x0, y1, 0).setColor(farbe).setLight(licht);
        vc.addVertex(p, x1, y1, 0).setColor(farbe).setLight(licht);
        vc.addVertex(p, x1, y0, 0).setColor(farbe).setLight(licht);
    }

    static int mix(int a, int b, float k) {
        k = Math.max(0, Math.min(1, k));
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    static int hsv(float h, float s, float v) {
        float r, g, b;
        int i = (int) Math.floor(h * 6);
        float f = h * 6 - i, p = v * (1 - s), qq = v * (1 - f * s), tt = v * (1 - (1 - f) * s);
        switch (((i % 6) + 6) % 6) {
            case 0 -> { r = v; g = tt; b = p; }
            case 1 -> { r = qq; g = v; b = p; }
            case 2 -> { r = p; g = v; b = tt; }
            case 3 -> { r = p; g = qq; b = v; }
            case 4 -> { r = tt; g = p; b = v; }
            default -> { r = v; g = p; b = qq; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float zufall(int s) {
        int x = s * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return (x & 0xFFFF) / 65535f;
    }
}
