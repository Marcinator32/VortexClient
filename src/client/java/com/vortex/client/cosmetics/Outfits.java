package com.vortex.client.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Outfits: ein ganzer Look (Cape samt Stil, Hut, Bandana, Gesicht, Ruecken,
 * Aura mit Dichte, Schild) unter einem Namen -- mit einem Klick wieder an.
 *
 * Ein eigenes Cape-Bild (Upload oder Cape-Editor) wird mit dem Outfit
 * gesichert, sonst waere es nach dem naechsten Upload weg.
 *
 * Datei: config/vortex-client/outfits.json, Bilder in outfits/NAME.png.
 */
public final class Outfits {

    private Outfits() {}

    public record Outfit(String name, Cosmetics.Auswahl auswahl) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path datei() { return Cosmetics.ordner().resolve("outfits.json"); }
    private static Path bild(String name) { return Cosmetics.ordner().resolve("outfits").resolve(sauber(name) + ".png"); }

    public static String sauber(String name) {
        if (name == null) return "";
        String s = name.trim().replaceAll("[^A-Za-z0-9 _\\-]", "");
        return s.length() > 24 ? s.substring(0, 24).trim() : s;
    }

    public static synchronized List<Outfit> alle() {
        List<Outfit> l = new ArrayList<>();
        try {
            if (!Files.exists(datei())) return l;
            JsonArray arr = JsonParser.parseString(Files.readString(datei(), StandardCharsets.UTF_8)).getAsJsonArray();
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                l.add(new Outfit(t(o, "name"), new Cosmetics.Auswahl(t(o, "cape"), t(o, "hat"), t(o, "aura"),
                        o.has("density") ? o.get("density").getAsInt() : 2, t(o, "bandana"), t(o, "face"), t(o, "back"), t(o, "shield"), t(o, "neck"), t(o, "nametag"), t(o, "pet"))));
            }
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("Outfits.alle", e);
        }
        return l;
    }

    private static String t(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static void schreiben(List<Outfit> l) throws Exception {
        JsonArray arr = new JsonArray();
        for (Outfit of : l) {
            Cosmetics.Auswahl a = of.auswahl();
            JsonObject o = new JsonObject();
            o.addProperty("name", of.name());
            o.addProperty("cape", a.cape());
            o.addProperty("hat", a.hut());
            o.addProperty("aura", a.partikel());
            o.addProperty("density", a.dichte());
            o.addProperty("bandana", a.bandana());
            o.addProperty("face", a.gesicht());
            o.addProperty("back", a.ruecken());
            o.addProperty("shield", a.schild());
            o.addProperty("neck", a.hals());
            o.addProperty("nametag", a.namensstil());
            o.addProperty("pet", a.pet());
            arr.add(o);
        }
        Files.createDirectories(Cosmetics.ordner());
        Path tmp = datei().resolveSibling("outfits.json.tmp");
        Files.writeString(tmp, GSON.toJson(arr), StandardCharsets.UTF_8);
        Files.move(tmp, datei(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Den aktuellen Look unter diesem Namen sichern (gleicher Name = ersetzen). */
    public static synchronized boolean speichern(String name) {
        String n = sauber(name);
        if (n.isEmpty()) return false;
        try {
            List<Outfit> l = alle();
            l.removeIf(o -> o.name().equalsIgnoreCase(n));
            Cosmetics.Auswahl a = Cosmetics.eigene();
            l.add(new Outfit(n, a));
            schreiben(l);
            if (EigenesCape.ID.equals(a.cape()) && EigenesCape.vorhanden()) {
                Files.createDirectories(bild(n).getParent());
                Files.copy(EigenesCape.datei(), bild(n), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("Outfits.speichern", e);
            return false;
        }
    }

    /** Outfit anziehen: alles auf einmal, ein eigenes Cape-Bild kommt mit. */
    public static synchronized void anziehen(Outfit o) {
        try {
            boolean eigenes = EigenesCape.ID.equals(o.auswahl().cape());
            if (eigenes && Files.exists(bild(o.name()))) {
                Files.createDirectories(EigenesCape.datei().getParent());
                Files.copy(bild(o.name()), EigenesCape.datei(), StandardCopyOption.REPLACE_EXISTING);
            }
            Cosmetics.speichern(o.auswahl(), Cosmetics.partikelErstePerson());
            if (eigenes) ActiveCape.neuLaden();
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("Outfits.anziehen", e);
        }
    }

    public static synchronized void loeschen(String name) {
        try {
            List<Outfit> l = alle();
            l.removeIf(o -> o.name().equalsIgnoreCase(name));
            schreiben(l);
            Files.deleteIfExists(bild(name));
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("Outfits.loeschen", e);
        }
    }

    /** Ist der aktuelle Look genau dieses Outfit? */
    public static boolean traegt(Outfit o) {
        return Cosmetics.eigene().equals(o.auswahl());
    }

    /** Kurzbeschreibung: wie viele Teile, z. B. "Cape, hat, aura + 2". */
    public static String kurz(Cosmetics.Auswahl a) {
        List<String> t = new ArrayList<>();
        if (!a.cape().isEmpty()) t.add("Cape");
        if (!a.hut().isEmpty()) t.add("Hat");
        if (!a.partikel().isEmpty()) t.add("Aura");
        if (!a.bandana().isEmpty()) t.add("Bandana");
        if (!a.gesicht().isEmpty()) t.add("Face");
        if (!a.ruecken().isEmpty()) t.add("Back");
        if (!a.schild().isEmpty()) t.add("Shield");
        if (!a.pet().isEmpty()) t.add("Pet");
        if (t.isEmpty()) return "Nothing";
        if (t.size() <= 3) return String.join(", ", t);
        return String.join(", ", t.subList(0, 3)) + " + " + (t.size() - 3);
    }
}
