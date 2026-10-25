package com.vortex.client.cosmetics;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

/**
 * Katalog aller Cosmetics fuer den Launcher (seit 4.83): einmal je Client-Version legt der
 * Client Bilder und eine Liste ab, damit der Launcher eine eigene Cosmetics-Seite zeigen
 * kann (Capes in 3D, alle anderen Teile als Bild) -- ohne dass Minecraft laufen muss.
 *
 * Ordner: config/vortex-client/launcher-preview/catalog/
 *   catalog.json  {"format":1, "client":"4.83.0", "categories":[...], "items":[{"cat","id","name","img"}]}
 *   capes/ID.png  Cape-Textur im Minecraft-Layout (64:32), 512 x 256
 *   CAT/ID.png    Pixelbild des Teils, vergroessert
 *   models/CAT/ID.json.gz  3D-Modell fuer die Vorschau (seit 4.87, siehe KatalogModelle)
 *   aura-tex/ART.png    Leucht-Texturen der grossen Auren
 *
 * Der Launcher schreibt eine neue Auswahl in cosmetics.json; der Client merkt das
 * (Cosmetics.vonAussen) und uebernimmt sie sofort.
 */
public final class LauncherKatalog {
    private LauncherKatalog() {}

    static final int FORMAT = 1;
    private static volatile boolean laeuft;

    /** Im Hintergrund pruefen und bei Bedarf neu schreiben (beim Start, etwas verzoegert). */
    public static void spaeter() {
        if (laeuft) return;
        laeuft = true;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(6000);
                schreiben();
            } catch (InterruptedException ignored) {
            } catch (Throwable e) {
                com.vortex.client.core.Errors.report("LauncherKatalog", e);
            } finally {
                laeuft = false;
            }
        }, "Vortex-Launcher-Catalog");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static String clientVersion() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("vortexclient")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    static void schreiben() throws Exception {
        Path dir = Cosmetics.ordner().resolve("launcher-preview").resolve("catalog");
        Path datei = dir.resolve("catalog.json");
        String version = clientVersion();
        if (Files.exists(datei)) {
            try {
                JsonObject alt = JsonParser.parseString(Files.readString(datei, StandardCharsets.UTF_8)).getAsJsonObject();
                if (alt.has("format") && alt.get("format").getAsInt() == FORMAT && alt.has("client") && version.equals(alt.get("client").getAsString())) return;
            } catch (Throwable ignored) { }
        }
        Files.createDirectories(dir.resolve("capes"));
        JsonObject j = new JsonObject();
        j.addProperty("format", FORMAT);
        j.addProperty("client", version);
        j.addProperty("written", System.currentTimeMillis());
        JsonArray kats = new JsonArray();
        JsonArray items = new JsonArray();
        kat(kats, "cape", "Capes");
        for (CapeKunst.Design d : CapeKunst.alle().values()) {
            String datei2 = "capes/" + d.id() + ".png";
            try {
                capeBild(d, dir.resolve(datei2));
                item(items, "cape", d.id(), d.name(), datei2, d.text());
            } catch (Throwable e) {
                com.vortex.client.core.Errors.report("LauncherKatalog.cape", e);
            }
        }
        for (Zubehoer.Kategorie k : Zubehoer.Kategorie.values()) {
            String cat = k.name().toLowerCase(java.util.Locale.ROOT);
            kat(kats, cat, katName(k));
            Files.createDirectories(dir.resolve(cat));
            for (Zubehoer.Design d : Zubehoer.von(k)) {
                String datei2 = cat + "/" + d.id() + ".png";
                try {
                    boolean bild = teilBild(d, dir.resolve(datei2));
                    JsonObject it = item(items, cat, d.id(), d.name(), bild ? datei2 : "", "");
                    String modell = modellSchreiben(d, dir, cat);
                    if (modell != null) it.addProperty("model", modell);
                } catch (Throwable e) {
                    com.vortex.client.core.Errors.report("LauncherKatalog.item", e);
                }
            }
        }
        try { auraTexturen(dir); j.addProperty("auraTextures", "aura-tex"); } catch (Throwable e) { com.vortex.client.core.Errors.report("LauncherKatalog.auraTex", e); }
        j.add("categories", kats);
        j.add("items", items);
        Path tmp = dir.resolve("catalog.json.tmp");
        Files.writeString(tmp, new GsonBuilder().create().toJson(j), StandardCharsets.UTF_8);
        Files.move(tmp, datei, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String katName(Zubehoer.Kategorie k) {
        return switch (k) {
            case HAT -> "Hats";
            case BANDANA -> "Bandanas";
            case FACE -> "Face";
            case BACK -> "Back";
            case NECK -> "Neck";
            case AURA -> "Auras";
            case SHIELD -> "Shields";
            case PET -> "Pets";
        };
    }

    private static void kat(JsonArray a, String id, String name) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        a.add(o);
    }

    private static JsonObject item(JsonArray a, String cat, String id, String name, String img, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("cat", cat);
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("img", img);
        if (text != null && !text.isEmpty()) o.addProperty("text", text);
        a.add(o);
        return o;
    }

    /** 3D-Modell fuer die Vorschau im Launcher (seit 4.87), siehe KatalogModelle. */
    private static String modellSchreiben(Zubehoer.Design d, Path dir, String cat) {
        try {
            JsonObject m = KatalogModelle.modell(d);
            if (m == null) return null;
            String datei = "models/" + cat + "/" + d.id() + ".json.gz";
            Files.createDirectories(dir.resolve("models").resolve(cat));
            try (java.io.OutputStream out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(dir.resolve(datei)))) {
                out.write(new GsonBuilder().create().toJson(m).getBytes(StandardCharsets.UTF_8));
            }
            return datei;
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("LauncherKatalog.modell", e);
            return null;
        }
    }

    /** Weiche Leucht-Texturen der grossen Auren (fuer die Launcher-Vorschau). */
    private static void auraTexturen(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("aura-tex"));
        for (AuraTexturen.Art a : AuraTexturen.Art.values()) {
            int[] px = AuraTexturen.bild(a);
            BufferedImage img = new BufferedImage(a.w, a.h, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < px.length; i++) {
                int k = px[i] & 0xFF;
                img.setRGB(i % a.w, i / a.w, 0xFF000000 | k << 16 | k << 8 | k);
            }
            ImageIO.write(img, "png", dir.resolve("aura-tex").resolve(a.name() + ".png").toFile());
        }
    }

    /** Cape in Standardform, Leuchten eingerechnet, auf 512 x 256 verkleinert. */
    private static void capeBild(CapeKunst.Design d, Path ziel) throws Exception {
        int[] basis = new int[CapeKunst.W * CapeKunst.H], glow = new int[CapeKunst.W * CapeKunst.H];
        CapeKunst.rendern(d, CapeKunst.Stil.STANDARD, 6.5f, basis, glow);
        BufferedImage img = new BufferedImage(CapeKunst.W, CapeKunst.H, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < basis.length; i++) {
            int q = basis[i], g = glow[i];
            int a = q >>> 24;
            int r = q & 255, gr = (q >> 8) & 255, b = (q >> 16) & 255;
            float ga = (g >>> 24) / 255f * 0.6f;
            r = Math.min(255, Math.round(r + (g & 255) * ga));
            gr = Math.min(255, Math.round(gr + ((g >> 8) & 255) * ga));
            b = Math.min(255, Math.round(b + ((g >> 16) & 255) * ga));
            img.setRGB(i % CapeKunst.W, i / CapeKunst.W, a << 24 | r << 16 | gr << 8 | b);
        }
        BufferedImage klein = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = klein.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(img.getScaledInstance(512, 256, java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g2.dispose();
        ImageIO.write(klein, "png", ziel.toFile());
    }

    /** Schild: die Vorderseite aus der Schild-Textur (Minecraft-Layout, Platte bei 2..12 / 2..22 von 64). */
    private static boolean schildBild(Zubehoer.Design d, Path ziel) throws Exception {
        BufferedImage tex = null;
        for (String name : new String[] { d.id() + ".png", d.id() + "_0.png" }) {
            try (java.io.InputStream in = LauncherKatalog.class.getResourceAsStream("/assets/vortexclient/textures/cosmetics/shield/" + name)) {
                if (in != null) { tex = ImageIO.read(in); break; }
            }
        }
        if (tex == null) return false;
        int k = Math.max(1, tex.getWidth() / 64);
        BufferedImage platte = tex.getSubimage(2 * k, 2 * k, 10 * k, 20 * k);
        int s = Math.max(1, 160 / (20 * k));
        BufferedImage img = new BufferedImage(10 * k * s, 20 * k * s, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.drawImage(platte, 0, 0, img.getWidth(), img.getHeight(), null);
        g.dispose();
        ImageIO.write(img, "png", ziel.toFile());
        return true;
    }

    /** Pixelbild des Teils, scharf vergroessert (der Launcher zeigt es pixelgenau). */
    private static boolean teilBild(Zubehoer.Design d, Path ziel) throws Exception {
        if (d.kat() == Zubehoer.Kategorie.SHIELD && schildBild(d, ziel)) return true;
        int[][] px = CosmeticsScreen.vorschauBild(d);
        int h = px.length, w = 0;
        for (int[] z : px) w = Math.max(w, z.length);
        if (h == 0 || w == 0) {
            Files.deleteIfExists(ziel);
            return false;
        }
        int s = Math.max(1, 128 / Math.max(w, h));
        BufferedImage img = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < px[y].length; x++) {
                int f = px[y][x];
                if ((f >>> 24) == 0) continue;
                for (int yy = 0; yy < s; yy++) for (int xx = 0; xx < s; xx++) img.setRGB(x * s + xx, y * s + yy, f);
            }
        }
        ImageIO.write(img, "png", ziel.toFile());
        return true;
    }
}
