package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Module List (seit 4.55): alle Module, die gerade an sind, als Liste im HUD -- vom
 * laengsten zum kuerzesten Namen, in Vortex-Farben. Neu eingeschaltete gleiten herein,
 * ausgeschaltete gleiten hinaus. Zeichnen: hud/ModulListe.
 *
 * Steht die Liste in der rechten Bildschirmhaelfte, ist sie rechtsbuendig; in der unteren
 * Haelfte waechst sie nach oben.
 */
public class ModuleListModule extends Module implements HudElement {

    /** Seit 4.58: altes ("Classic") oder neues Aussehen ("Modern"); "Auto" folgt HUD Design. */
    public final com.vortex.client.core.setting.ModeSetting look = com.vortex.client.hud.HudKarte.look();

    public final NumberSetting x = new NumberSetting("X", 2000, 0, 3840, 1);
    public final NumberSetting y = new NumberSetting("Y", 2, 0, 2160, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final ModeSetting sort = new ModeSetting("Sort", 0, "Long to Short", "Short to Long", "A-Z");
    public final ModeSetting colorMode = new ModeSetting("Color Mode", 0, "Vortex", "Rainbow", "Static", "Category");
    public final ColorSetting color = new ColorSetting("Color", 0xFF8B5CF6);
    public final NumberSetting speed = new NumberSetting("Color Speed", 1.0, 0.0, 5.0, 0.1);
    public final BooleanSetting info = new BooleanSetting("Show Info", true);
    public final BooleanSetting background = new BooleanSetting("Background", true);
    public final NumberSetting backgroundAlpha = new NumberSetting("Background Opacity", 0.45, 0.0, 1.0, 0.05);
    public final ModeSetting bar = new ModeSetting("Side Bar", 1, "None", "Outer", "Inner");
    public final ModeSetting textCase = new ModeSetting("Text Case", 0, "Normal", "lowercase", "UPPERCASE");
    public final BooleanSetting shadow = new BooleanSetting("Text Shadow", true);
    public final BooleanSetting animate = new BooleanSetting("Animation", true);
    /**
     * Seit 4.61: fuer jede Kategorie einstellbar, ob ihre Module in der Liste stehen
     * (ersetzt "Hide HUD Modules" und "Hide Cheats"). HUD-Module (FPS, Koordinaten ...)
     * sieht man ohnehin -- standardmaessig nicht in der Liste.
     */
    public final BooleanSetting showHud = new BooleanSetting("Show HUD", false);
    public final BooleanSetting showPvp = new BooleanSetting("Show PvP", true);
    public final BooleanSetting showCheats = new BooleanSetting("Show Cheats", true);
    public final BooleanSetting showBots = new BooleanSetting("Show Bots", true);
    public final BooleanSetting showPerformance = new BooleanSetting("Show Performance", true);
    public final BooleanSetting showMisc = new BooleanSetting("Show Misc", true);
    public final BooleanSetting showMusic = new BooleanSetting("Show Music", true);

    /** Steht diese Kategorie in der Liste? */
    public boolean zeigt(Category c) {
        return switch (c) {
            case HUD -> showHud.get();
            case PVP -> showPvp.get();
            case CHEATS -> showCheats.get();
            case BOTS -> showBots.get();
            case PERFORMANCE -> showPerformance.get();
            case MISC -> showMisc.get();
            case MUSIC -> showMusic.get();
        };
    }

    public ModuleListModule() {
        super("Module List", Category.HUD);
        addSetting(look);
        addSetting(x);
        addSetting(y);
        addSetting(scale);
        addSetting(sort);
        addSetting(colorMode);
        addSetting(color);
        addSetting(speed);
        addSetting(info);
        addSetting(background);
        addSetting(backgroundAlpha);
        addSetting(bar);
        addSetting(textCase);
        addSetting(shadow);
        addSetting(animate);
        addSetting(showHud);
        addSetting(showPvp);
        addSetting(showCheats);
        addSetting(showBots);
        addSetting(showPerformance);
        addSetting(showMisc);
        addSetting(showMusic);
    }

    @Override public String hudName() { return "Module List"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return Math.max(60, com.vortex.client.hud.ModulListe.breite()); }
    @Override public int hudHeight() { return Math.max(12, com.vortex.client.hud.ModulListe.hoehe()); }
}
