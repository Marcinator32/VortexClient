package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.module.Module;

/**
 * Shield Status: faerbt Schilde nach Zustand ein (eigene Umsetzung seit 4.30,
 * siehe cosmetics/SchildStatus und SchildSkinMixin):
 *   gruen = blockt, gelb = wird hochgenommen, rot = von einer Axt gebrochen.
 *
 * Ist die Mod "ShieldStatus" (Walksy) installiert, wird ihre Faerbung immer
 * abgeschaltet -- sie wuerde die Vortex Shield Skins verdecken.
 */
public class ShieldStatusModule extends Module {

    public final BooleanSetting grayscaleBroken =
        new BooleanSetting("Grey Out Broken", false);
    public final BooleanSetting opponentShields =
        new BooleanSetting("Enemy Shields", true);
    public final BooleanSetting smoothColor =
        new BooleanSetting("Smooth Gradient", false);

    public ShieldStatusModule() {
        super("Shield Status", Category.PVP);
        addSetting(grayscaleBroken);
        addSetting(opponentShields);
        addSetting(smoothColor);
        enabledByDefault();
        if (!TICK) {
            TICK = true;
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> fremdeModAus());
        }
    }

    private static boolean TICK;

    /**
     * Ist zusaetzlich die Mod "ShieldStatus" (Walksy) installiert, zeichnet sie
     * Schilde selbst und verdeckt dabei die Vortex Shield Skins. Unser Modul
     * macht dasselbe -- deshalb schalten wir ihre Faerbung ab (sicher per
     * Reflection, ohne die Mod: nichts passiert).
     */
    @Override
    protected void onEnable() { fremdeModAus(); }

    @Override
    protected void onDisable() { fremdeModAus(); }

    /** Einstellungs-Klassen der Mod "ShieldStatus" je Version (neueste zuerst). */
    private static final String[] FREMDE_KLASSEN = {
            "walksy.shieldstatus.config.ShieldStatusConfig", "walksy.shieldstatus.config.Config" };

    private static volatile java.lang.reflect.Field FREMD;
    /** 0 = noch nicht gesucht, 1 = gefunden/keine Mod, ab 2 = Fehlversuche (wird spaeter nochmal versucht). */
    private static volatile int gesucht;

    /** Nur fuer Tests: die fremde Mod an lassen (prueft, dass Skins auch dann sichtbar bleiben). */
    public static volatile boolean fremdeModLassen;

    /**
     * Faerbung der Mod "ShieldStatus" (Walksy) abschalten. Laeuft jeden Tick (billig: ein
     * statisches Feld), weil die Mod ihre Einstellung spaeter neu laden und wieder
     * einschalten kann. Die Klasse heisst je nach Version anders (bis 4.1: Config, ab 4.2:
     * ShieldStatusConfig); kennen wir keinen Namen, suchen wir in der Mod selbst nach einer
     * Klasse mit "static boolean modEnabled".
     */
    /** Rueckfall fuer unbekannte Versionen: alle Klassen der Mod unter walksy/shieldstatus durchsehen. */
    private static java.lang.reflect.Field suchen() {
        try {
            var mod = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("shieldstatus").orElse(null);
            if (mod == null) return null;
            for (java.nio.file.Path wurzel : mod.getRootPaths()) {
                java.nio.file.Path ordner = wurzel.resolve("walksy/shieldstatus");
                if (!java.nio.file.Files.isDirectory(ordner)) continue;
                try (var dateien = java.nio.file.Files.walk(ordner)) {
                    for (java.nio.file.Path d : (Iterable<java.nio.file.Path>) dateien::iterator) {
                        String n = wurzel.relativize(d).toString().replace('\\', '/');
                        if (!n.endsWith(".class") || n.contains("$") || n.contains("/mixin/")) continue;
                        try {
                            Class<?> k = Class.forName(n.substring(0, n.length() - 6).replace('/', '.'));
                            var f = k.getDeclaredField("modEnabled");
                            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == boolean.class) {
                                f.setAccessible(true);
                                return f;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void fremdeModAus() {
        if (fremdeModLassen) return;
        try {
            if (gesucht != 1) {
                if (gesucht > 200) return;
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("shieldstatus")) { gesucht = 1; return; }
                gesucht = gesucht == 0 ? 2 : gesucht + 1;
                for (String klasse : FREMDE_KLASSEN) {
                    Class<?> cfg;
                    try { cfg = Class.forName(klasse); } catch (ClassNotFoundException e) { continue; }
                    for (String n : new String[] { "modEnabled", "enabled" }) {
                        try {
                            var f = cfg.getDeclaredField(n);
                            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != boolean.class) continue;
                            f.setAccessible(true); FREMD = f; break;
                        } catch (NoSuchFieldException ignored) {}
                    }
                    if (FREMD != null) break;
                }
                if (FREMD == null) FREMD = suchen();
                if (FREMD == null) return;
                gesucht = 1;
                com.vortex.client.core.Errors.note("ShieldStatus", "Mod ShieldStatus (Walksy) gefunden -- ihre Faerbung wird abgeschaltet, Vortex Shield Status uebernimmt");
            }
            var f = FREMD;
            if (f != null && f.getBoolean(null)) f.setBoolean(null, false);
        } catch (Throwable ignored) {
        }
    }

}
