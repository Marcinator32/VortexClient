package com.vortex.client.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Schutz gegen Mod-Erkennung ueber Uebersetzungsschluessel (seit 4.31).
 *
 * SO ERKENNEN SERVER MODS: Der Server legt ein Schild (oder ein Item im
 * Amboss) mit einem Text wie {"translate":"key.meteor-client.open-gui"}
 * oder {"keybind":"key.vortexclient.clickgui"} hin und oeffnet den Editor.
 * Der Client wandelt den Text dabei in Klartext um und schickt ihn beim
 * Speichern zurueck. Ein Vanilla-Client kennt den Schluessel nicht und
 * schickt ihn unveraendert; ein Client mit der Mod schickt "Open Vortex
 * Menu" bzw. "Right Shift" -- und ist erkannt.
 *
 * GEGENMITTEL: Genau in diesen Momenten (Schild-Editor oeffnet, Amboss
 * uebernimmt einen Namen) loesen wir Schluessel, die nur aus Mods stammen,
 * wie Vanilla auf: gar nicht. Fuer alles, was Vanilla kennt, aendert sich
 * nichts. Abschaltbar: Client Settings -> "Hide Mods From Servers".
 *
 * Gilt fuer ALLE geladenen Mods, nicht nur fuer Vortex -- auch eine Minimap
 * oder Sodium sollen sich nicht auf diesem Weg verraten.
 */
public final class Fingerabdruck {

    private Fingerabdruck() {}

    private static volatile Set<String> modSchluessel;
    private static int tiefe;
    private static long seit;

    /** Ist der Schutz gerade aktiv (Text wird fuer den Server umgewandelt)? */
    public static boolean aktiv() {
        if (tiefe <= 0) return false;
        if (System.nanoTime() - seit > 2_000_000_000L) {
            tiefe = 1;
            aus();
            return false;
        }
        return true;
    }

    public static boolean eingeschaltet() {
        try {
            return ClientSettings.INSTANCE.hideModsFromServers.get();
        } catch (Throwable e) {
            return true;
        }
    }

    /** Stammt dieser Schluessel nur aus einer Mod (Vanilla kennt ihn nicht)? */
    public static boolean vonMod(String schluessel) {
        if (schluessel == null) return false;
        return schluessel().contains(schluessel);
    }

    /** Schutz einschalten (Gegenstueck: {@link #aus()}). */
    public static void an() {
        if (!eingeschaltet()) return;
        if (tiefe <= 0) seit = System.nanoTime();
        tiefe++;
        Language echt = Language.getInstance();
        if (!(echt instanceof Gefiltert)) {
            Language.inject(new Gefiltert(echt));
        }
    }

    public static void aus() {
        if (tiefe <= 0) return;
        tiefe--;
        if (tiefe == 0) {
            Language l = Language.getInstance();
            if (l instanceof Gefiltert g) Language.inject(g.echt);
        }
    }

    /** Wert mit eingeschaltetem Schutz berechnen. */
    public static <T> T mitSchutz(Supplier<T> wert) {
        an();
        try {
            return wert.get();
        } finally {
            aus();
        }
    }

    /**
     * Sprache, die Mod-Schluessel nicht kennt -- alles andere geht an die
     * echte Sprache. Eine neue Instanz zwingt Minecraft ausserdem, schon
     * aufgeloeste Texte neu aufzuloesen (der Zwischenspeicher haengt an der
     * Sprach-Instanz).
     */
    static final class Gefiltert extends Language {
        final Language echt;

        Gefiltert(Language echt) {
            this.echt = echt;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            if (vonMod(key)) return fallback;
            return echt.getOrDefault(key, fallback);
        }

        @Override
        public boolean has(String key) {
            return !vonMod(key) && echt.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return echt.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return echt.getVisualOrder(text);
        }
    }

    private static Set<String> schluessel() {
        Set<String> s = modSchluessel;
        if (s == null) {
            synchronized (Fingerabdruck.class) {
                if (modSchluessel == null) modSchluessel = einsammeln();
                s = modSchluessel;
            }
        }
        return s;
    }

    /**
     * Alle Schluessel aus den Sprachdateien der Mods, ohne die, die Vanilla
     * selbst hat (Mods duerfen Vanilla-Texte ueberschreiben -- die kennt ein
     * Vanilla-Client auch). Dazu die Namen aller Tastenbelegungen von Mods.
     */
    private static Set<String> einsammeln() {
        Set<String> vanilla = new HashSet<>();
        Set<String> mods = new HashSet<>();
        try {
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId();
                boolean istVanilla = id.equals("minecraft");
                if (id.equals("java") || id.equals("fabricloader")) continue;
                for (Path wurzel : mod.getRootPaths()) {
                    Path assets = wurzel.resolve("assets");
                    if (!Files.isDirectory(assets)) continue;
                    try (var namensraeume = Files.list(assets)) {
                        for (Path ns : (Iterable<Path>) namensraeume::iterator) {
                            Path lang = ns.resolve("lang");
                            if (!Files.isDirectory(lang)) continue;
                            if (istVanilla) {
                                lesen(lang.resolve("en_us.json"), vanilla);
                            } else {
                                try (var dateien = Files.list(lang)) {
                                    for (Path d : (Iterable<Path>) dateien::iterator) {
                                        if (d.getFileName().toString().endsWith(".json")) lesen(d, mods);
                                    }
                                }
                            }
                        }
                    } catch (Throwable e) {
                    }
                }
            }
        } catch (Throwable e) {
            Errors.report("Fingerabdruck.einsammeln", e);
        }
        mods.removeAll(vanilla);
        try {
            for (var km : net.minecraft.client.Minecraft.getInstance().options.keyMappings) {
                String name = km.getName();
                if (!vanilla.isEmpty() && !vanilla.contains(name)) mods.add(name);
            }
        } catch (Throwable ignored) {
        }
        return Set.copyOf(mods);
    }

    private static void lesen(Path datei, Set<String> ziel) {
        if (!Files.isRegularFile(datei)) return;
        try (Reader r = Files.newBufferedReader(datei)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            ziel.addAll(o.keySet());
        } catch (Throwable ignored) {
        }
    }
}
