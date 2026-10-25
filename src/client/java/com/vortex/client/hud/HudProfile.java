package com.vortex.client.hud;

import com.vortex.client.core.setting.Setting;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/**
 * HUD-Profile: ein ganzes HUD (welche Anzeigen an sind, wo sie sitzen, wie
 * gross und in welcher Farbe) unter einem Namen speichern und spaeter mit
 * einem Klick zurueckholen -- z. B. "PvP", "Bauen", "Aufnahme".
 *
 * Datei je Profil: config/vortexclient/hud-profiles/NAME.txt, eine Zeile je
 * Wert: Modul TAB Einstellung TAB Wert ("@on" = ob das Modul an ist).
 * Module, die es beim Laden nicht (mehr) gibt, werden uebergangen.
 */
public final class HudProfile {

    private HudProfile() {}

    private static Path ordner() {
        return FabricLoader.getInstance().getConfigDir().resolve("vortexclient").resolve("hud-profiles");
    }

    /** Gehoert das Modul zum HUD-Layout? */
    public static boolean istHud(Module m) {
        return m instanceof HudElement
                || m instanceof com.vortex.client.module.modules.ArmorHudModule
                || m instanceof com.vortex.client.module.modules.ItemCounterModule
                || m.getCategory() == Module.Category.HUD;
    }

    /** Erlaubte Dateinamen: Buchstaben, Ziffern, Leer-, Binde- und Unterstrich. */
    public static String sauber(String name) {
        if (name == null) return "";
        String s = name.trim().replaceAll("[^A-Za-z0-9 _\\-]", "");
        return s.length() > 24 ? s.substring(0, 24).trim() : s;
    }

    public static List<String> alle() {
        List<String> l = new ArrayList<>();
        try {
            if (!Files.isDirectory(ordner())) return l;
            try (var st = Files.list(ordner())) {
                st.filter(p -> p.getFileName().toString().endsWith(".txt"))
                        .forEach(p -> l.add(p.getFileName().toString().replaceAll("\\.txt$", "")));
            }
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("HudProfile.alle", e);
        }
        l.sort(String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    public static boolean speichern(String name) {
        String n = sauber(name);
        if (n.isEmpty()) return false;
        List<String> zeilen = new ArrayList<>();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (!istHud(m)) continue;
            zeilen.add(m.getName() + "\t@on\t" + m.isEnabled());
            for (Setting s : m.getSettings()) {
                if (s.getName().equals("Enabled") || s instanceof com.vortex.client.core.setting.KeySetting) continue;
                String v = s.serialize();
                if (v == null || v.contains("\n") || v.contains("\t")) continue;
                zeilen.add(m.getName() + "\t" + s.getName() + "\t" + v);
            }
        }
        try {
            Files.createDirectories(ordner());
            Files.write(ordner().resolve(n + ".txt"), zeilen, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("HudProfile.speichern", e);
            return false;
        }
    }

    public static boolean laden(String name) {
        Path f = ordner().resolve(sauber(name) + ".txt");
        try {
            if (!Files.exists(f)) return false;
            for (String z : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String[] t = z.split("\t", 3);
                if (t.length < 3) continue;
                Module m = finde(t[0]);
                if (m == null || !istHud(m)) continue;
                if (t[1].equals("@on")) {
                    boolean an = Boolean.parseBoolean(t[2]);
                    if (m.isEnabled() != an) m.setEnabled(an);
                    continue;
                }
                for (Setting s : m.getSettings()) {
                    if (s.getName().equals(t[1])) {
                        try { s.deserialize(t[2]); } catch (Throwable ignored) { }
                        break;
                    }
                }
            }
            com.vortex.client.core.ConfigManager.save();
            return true;
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("HudProfile.laden", e);
            return false;
        }
    }

    public static void loeschen(String name) {
        try {
            Files.deleteIfExists(ordner().resolve(sauber(name) + ".txt"));
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("HudProfile.loeschen", e);
        }
    }

    private static Module finde(String name) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.getName().equalsIgnoreCase(name.toLowerCase(Locale.ROOT)) || m.getName().equals(name)) return m;
        }
        return null;
    }
}
