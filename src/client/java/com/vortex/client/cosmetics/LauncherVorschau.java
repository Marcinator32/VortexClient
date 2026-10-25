package com.vortex.client.cosmetics;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

/**
 * Vorschau fuer den Launcher (seit 4.38): nach jeder Aenderung an den
 * Cosmetics legt der Client ein Bild des Capes und eine kleine Liste des
 * Looks (mit Bildchen) ab. Der Launcher zeigt das auf der Skins-Seite am
 * 3D-Modell -- ohne dass Minecraft laufen muss.
 *
 * Ordner: config/vortex-client/launcher-preview/
 *   look.json   {"updated":..., "cape":{"id","name"}, "items":[{"slot","id","name","icon"}]}
 *   cape.png    Cape-Textur im Minecraft-Layout (64:32), fehlt ohne Cape
 *   SLOT.png    Bildchen je Teil (hat.png, aura.png, ...)
 */
public final class LauncherVorschau {

    private LauncherVorschau() {}

    private static volatile boolean geplant = false;

    /** Im Hintergrund neu schreiben (mehrere Aenderungen kurz nacheinander = einmal). */
    public static void spaeter() {
        if (geplant) return;
        geplant = true;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) { }
            geplant = false;
            try {
                schreiben();
            } catch (Throwable e) {
                com.vortex.client.core.Errors.report("LauncherVorschau", e);
            }
        }, "Vortex-Launcher-Preview");
        t.setDaemon(true);
        t.start();
    }

    private static Path ordner() {
        return Cosmetics.ordner().resolve("launcher-preview");
    }

    static void schreiben() throws Exception {
        Cosmetics.Auswahl a = Cosmetics.eigene();
        Path o = ordner();
        Files.createDirectories(o);
        JsonObject look = new JsonObject();
        look.addProperty("updated", System.currentTimeMillis());

        Path cape = o.resolve("cape.png");
        String capeName = "";
        boolean capeDa = false;
        String id = a.cape();
        if (!id.isEmpty()) {
            CapeKunst.Design d = CapeKunst.design(id);
            if (d != null) {
                int[] basis = new int[CapeKunst.W * CapeKunst.H], glow = new int[CapeKunst.W * CapeKunst.H];
                CapeKunst.rendern(d, CapeKunst.Stil.aus(id), 6.5f, basis, glow);
                BufferedImage img = new BufferedImage(CapeKunst.W, CapeKunst.H, BufferedImage.TYPE_INT_ARGB);
                for (int i = 0; i < basis.length; i++) {
                    int q = basis[i];
                    int argb = (q & 0xFF00FF00) | ((q & 0xFF) << 16) | ((q >> 16) & 0xFF);
                    img.setRGB(i % CapeKunst.W, i / CapeKunst.W, argb);
                }
                ImageIO.write(img, "png", cape.toFile());
                capeName = d.name();
                capeDa = true;
            } else if (EigenesCape.ID.equals(id) && EigenesCape.vorhanden()) {
                Files.copy(EigenesCape.datei(), cape, StandardCopyOption.REPLACE_EXISTING);
                capeName = "Your picture";
                capeDa = true;
            } else if (Files.exists(ActiveCape.cacheDatei(id))) {
                Files.copy(ActiveCape.cacheDatei(id), cape, StandardCopyOption.REPLACE_EXISTING);
                capeName = id;
                capeDa = true;
            }
        }
        if (!capeDa) Files.deleteIfExists(cape);
        JsonObject c = new JsonObject();
        c.addProperty("id", capeDa ? id : "");
        c.addProperty("name", capeName);
        look.add("cape", c);

        JsonArray items = new JsonArray();
        teil(items, o, "Hat", "hat", Zubehoer.Kategorie.HAT, a.hut());
        teil(items, o, "Face", "face", Zubehoer.Kategorie.FACE, a.gesicht());
        teil(items, o, "Bandana", "bandana", Zubehoer.Kategorie.BANDANA, a.bandana());
        teil(items, o, "Back", "back", Zubehoer.Kategorie.BACK, a.ruecken());
        teil(items, o, "Neck", "neck", Zubehoer.Kategorie.NECK, a.hals());
        teil(items, o, "Aura", "aura", Zubehoer.Kategorie.AURA, a.partikel());
        teil(items, o, "Shield", "shield", Zubehoer.Kategorie.SHIELD, a.schild());
        look.add("items", items);

        Path tmp = o.resolve("look.json.tmp");
        Files.writeString(tmp, new GsonBuilder().setPrettyPrinting().create().toJson(look), StandardCharsets.UTF_8);
        Files.move(tmp, o.resolve("look.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void teil(JsonArray items, Path o, String slot, String datei, Zubehoer.Kategorie kat, String id) {
        Path bild = o.resolve(datei + ".png");
        try {
            Zubehoer.Design d = id == null || id.isEmpty() ? null : Zubehoer.get(kat, id);
            if (d == null) {
                Files.deleteIfExists(bild);
                return;
            }
            JsonObject j = new JsonObject();
            j.addProperty("slot", slot);
            j.addProperty("id", id);
            j.addProperty("name", d.name());
            boolean mitBild = false;
            int[][] px = CosmeticsScreen.vorschauBild(d);
            int h = px.length, w = 0;
            for (int[] z : px) w = Math.max(w, z.length);
            if (h > 0 && w > 0) {
                int s = Math.max(1, 64 / Math.max(w, h));
                BufferedImage img = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < px[y].length; x++) {
                        int f = px[y][x];
                        if ((f >>> 24) == 0) continue;
                        for (int yy = 0; yy < s; yy++) for (int xx = 0; xx < s; xx++) img.setRGB(x * s + xx, y * s + yy, f);
                    }
                ImageIO.write(img, "png", bild.toFile());
                mitBild = true;
            } else {
                Files.deleteIfExists(bild);
            }
            j.addProperty("icon", mitBild ? datei + ".png" : "");
            items.add(j);
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("LauncherVorschau." + slot, e);
        }
    }
}
