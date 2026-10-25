package com.vortex.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 3D-Modelle aller Cosmetics fuer die Launcher-Vorschau (seit 4.87).
 *
 * Die Teile werden genau so aufgenommen, wie ZubehoerEbene sie am Spieler zeichnet (nur ohne
 * Knochen und in Ruhe): als Liste von Vierecken mit Eckfarben. Der Launcher baut daraus
 * dieselben Kloetzchen und haengt sie an Kopf, Koerper oder Spieler.
 *
 * Format je Modell (kompakt, die Datei wird zusaetzlich gzip-gepackt):
 *   anchor  "head" | "body" | "root" | "arm"
 *   n       Anzahl Vierecke
 *   s       Massstab der Positionen (64 = 1/64 Pixel)
 *   p       Base64, int16 little endian: n * 4 Ecken * (x, y, z) / s -- Pixel, y nach oben, Hals = 0
 *   pal     Farbpalette (RGB)
 *   c       Base64, uint8 (oder "c16": uint16): n * 4 Eckfarben als Index in pal
 *   g       Base64, uint8: n Leuchtstaerken (0..255)
 * Auren: anchor "root", "aura" mit den Bewegungswerten und "sprites" (je ein Modell wie oben,
 * um den Mittelpunkt) bzw. "big" fuer die grossen Auren (rechnet der Launcher nach).
 */
public final class KatalogModelle {
    private KatalogModelle() {}

    /** Gesammelte Vierecke. */
    private static final class Sammler implements ZubehoerEbene.Aufnahme {
        final List<float[]> ecken = new ArrayList<>();
        final List<int[]> farben = new ArrayList<>();
        final List<Integer> leuchten = new ArrayList<>();
        private final Vector3f v = new Vector3f();

        @Override
        public void flaeche(Matrix4f m, Netz.Flaeche f) {
            float[] e = new float[12];
            for (int i = 0; i < 4; i++) {
                m.transformPosition(f.p[i * 3], f.p[i * 3 + 1], f.p[i * 3 + 2], v);
                e[i * 3] = v.x;
                e[i * 3 + 1] = v.y;
                e[i * 3 + 2] = v.z;
            }
            ecken.add(e);
            farben.add(f.farbe.clone());
            leuchten.add(Math.max(0, Math.min(255, Math.round(f.leuchten * 255f))));
        }

        JsonObject json(String anker) {
            int n = ecken.size();
            ByteBuffer p = ByteBuffer.allocate(n * 24).order(ByteOrder.LITTLE_ENDIAN);
            java.util.Map<Integer, Integer> palette = new java.util.LinkedHashMap<>();
            int[] index = new int[n * 4];
            byte[] g = new byte[n];
            for (int i = 0; i < n; i++) {
                for (float x : ecken.get(i)) p.putShort((short) Math.max(-32768, Math.min(32767, Math.round(x * MASSSTAB))));
                int[] f = farben.get(i);
                for (int k = 0; k < 4; k++) index[i * 4 + k] = palette.computeIfAbsent(f[k] & 0xFFFFFF, x -> palette.size());
                g[i] = (byte) (int) leuchten.get(i);
            }
            JsonObject o = new JsonObject();
            if (anker != null) o.addProperty("anchor", anker);
            o.addProperty("n", n);
            o.addProperty("s", MASSSTAB);
            o.addProperty("p", Base64.getEncoder().encodeToString(p.array()));
            JsonArray pal = new JsonArray();
            for (int k : palette.keySet()) pal.add(k);
            o.add("pal", pal);
            if (palette.size() <= 256) {
                byte[] c = new byte[index.length];
                for (int i = 0; i < index.length; i++) c[i] = (byte) index[i];
                o.addProperty("c", Base64.getEncoder().encodeToString(c));
            } else {
                ByteBuffer c = ByteBuffer.allocate(index.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (int i : index) c.putShort((short) i);
                o.addProperty("c16", Base64.getEncoder().encodeToString(c.array()));
            }
            o.addProperty("g", Base64.getEncoder().encodeToString(g));
            return o;
        }
    }

    private static final int MASSSTAB = 64;

    /** Wie GrossAura.ist -- hier ohne die Klasse zu laden (Texturen braucht der Export nicht). */
    private static boolean gross(String bewegung) {
        return bewegung != null && bewegung.startsWith("big_");
    }

    private static String anker(Zubehoer.Kategorie k) {
        return switch (k) {
            case HAT, FACE, BANDANA -> "head";
            case BACK, NECK -> "body";
            case SHIELD -> "arm";
            case AURA -> "root";
            case PET -> "pet";
        };
    }

    /** Modell eines Designs; null, wenn es nichts zu zeigen gibt. */
    public static JsonObject modell(Zubehoer.Design d) {
        if (d.kat() == Zubehoer.Kategorie.SHIELD) {
            JsonObject o = new JsonObject();
            o.addProperty("anchor", "arm");
            o.addProperty("shield", true);
            return o;
        }
        if (d.kat() == Zubehoer.Kategorie.AURA) {
            Zubehoer.Aura au = d.aura();
            if (au == null) return null;
            JsonObject o = new JsonObject();
            o.addProperty("anchor", "root");
            JsonObject a = new JsonObject();
            a.addProperty("move", au.bewegung());
            a.addProperty("count", au.anzahl());
            a.addProperty("px", au.px());
            a.addProperty("spin", au.drehen());
            a.addProperty("size", au.groesse());
            if (gross(au.bewegung())) a.addProperty("big", au.bewegung());
            o.add("aura", a);
            JsonArray sprites = new JsonArray();
            if (!gross(au.bewegung())) {
                for (Zubehoer.Teil sp : au.sprites()) {
                    Sammler s = new Sammler();
                    ZubehoerEbene.aufnehmen(Zubehoer.netz(sp), s);
                    sprites.add(s.json(null));
                }
            }
            o.add("sprites", sprites);
            return o;
        }
        Sammler s = new Sammler();
        ZubehoerEbene.aufnehmen(d, 0f, s);
        if (s.ecken.isEmpty()) return null;
        JsonObject o = s.json(anker(d.kat()));
        if (Zubehoer.amBoden(d)) o.addProperty("ground", true);
        return o;
    }
}
