package com.vortex.client.gui.glatt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wie Minecrafts eigene Menues aussehen (seit 4.90, einstellbar unter Client Design):
 * Knoepfe, Regler, Eingabefelder und Listen im Hauptmenue, in Einzel- und Mehrspieler, beim
 * Welt-Erstellen, im ESC-Menue -- und auf Wunsch in ALLEN Minecraft-Menues (Optionen,
 * Steuerung, Grafik, Sprache ...). Menues anderer Mods und die Vortex-Menues bleiben, wie sie sind.
 *
 * Die Farben kommen aus dem Client-Design (gezeichnet wird in Vortex-Lila/Blau, das Design
 * rechnet es in die gewaehlten Farben um).
 */
public final class KnopfStil {
    private KnopfStil() {}

    public enum Stil {
        RUND("Rounded"), NEON("Neon"), FLACH("Minecraft"), ORIGINAL("Original");
        public final String name;
        Stil(String name) { this.name = name; }
    }

    public enum Fuellung {
        DUNKEL("Dark"), GETOENT("Tinted"), GLAS("Glass");
        public final String name;
        Fuellung(String name) { this.name = name; }
    }

    /** Ecken: eckig, leicht, rund, ganz rund (Pille). */
    public static final String[] ECKEN = { "Square", "Soft", "Round", "Pill" };

    private static Stil stil = Stil.RUND;
    private static Fuellung fuellung = Fuellung.DUNKEL;
    private static int ecken = 2;
    private static boolean ueberall = true;
    private static boolean geladen;

    public static Stil stil() { laden(); return stil; }
    public static Fuellung fuellung() { laden(); return fuellung; }
    public static int ecken() { laden(); return ecken; }
    public static boolean ueberall() { laden(); return ueberall; }

    public static void setzeStil(Stil s) { laden(); stil = s; speichern(); }
    public static void setzeFuellung(Fuellung f) { laden(); fuellung = f; speichern(); }
    public static void setzeEcken(int e) { laden(); ecken = Math.max(0, Math.min(ECKEN.length - 1, e)); speichern(); }
    public static void setzeUeberall(boolean an) { laden(); ueberall = an; speichern(); }

    public static void zuruecksetzen() {
        laden();
        stil = Stil.RUND;
        fuellung = Fuellung.DUNKEL;
        ecken = 2;
        ueberall = true;
        speichern();
    }

    /** Eckenradius fuer ein Teil dieser Hoehe (GUI-Einheiten). */
    public static float radius(float hoehe) {
        return switch (ecken()) {
            case 0 -> 0f;
            case 1 -> Math.min(3f, hoehe / 2f);
            case 2 -> Math.min(6f, hoehe / 2f);
            default -> hoehe / 2f;
        };
    }

    private static Path datei() {
        return com.vortex.client.core.ConfigManager.dataDir().resolve("menu-buttons.txt");
    }

    private static synchronized void laden() {
        if (geladen) return;
        geladen = true;
        try {
            Path d = datei();
            if (!Files.exists(d)) return;
            for (String z : Files.readAllLines(d, StandardCharsets.UTF_8)) {
                int i = z.indexOf('=');
                if (i <= 0) continue;
                String k = z.substring(0, i).trim(), v = z.substring(i + 1).trim();
                switch (k) {
                    case "style" -> { for (Stil s : Stil.values()) if (s.name().equalsIgnoreCase(v)) stil = s; }
                    case "fill" -> { for (Fuellung f : Fuellung.values()) if (f.name().equalsIgnoreCase(v)) fuellung = f; }
                    case "corners" -> { try { ecken = Math.max(0, Math.min(ECKEN.length - 1, Integer.parseInt(v))); } catch (NumberFormatException ignored) { } }
                    case "everywhere" -> ueberall = Boolean.parseBoolean(v);
                    default -> { }
                }
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("KnopfStil.laden", t);
        }
    }

    private static void speichern() {
        try {
            String text = "style=" + stil.name().toLowerCase() + "\nfill=" + fuellung.name().toLowerCase()
                    + "\ncorners=" + ecken + "\neverywhere=" + ueberall + "\n";
            Files.createDirectories(datei().getParent());
            Files.writeString(datei(), text, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("KnopfStil.speichern", t);
        }
    }
}
