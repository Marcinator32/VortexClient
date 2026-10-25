package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * HUD Design (seit 4.56): wie alle HUD-Elemente aussehen.
 *
 * "Modern" (Standard): jedes Element ist eine dunkle, runde Karte mit weichem Schatten, Werte
 * in glatter Schrift hell, Einheiten/Beschriftungen gedimmt daneben ("144 FPS"), feine
 * Trennstriche, ein schmaler Akzentstreifen in Vortex-Farben. "Classic" ist das Aussehen bis
 * 4.55 (Minecraft-Schrift, Einstellungen je Element: Hintergrund, Rahmen ...).
 *
 * Gezeichnet wird in hud/HudKarte.
 */
public class HudDesignModule extends Module {

    public final ModeSetting style = new ModeSetting("Style", 0, "Modern", "Classic");
    public final NumberSetting radius = new NumberSetting("Corner Radius", 5, 0, 10, 1);
    public final ColorSetting cardColor = new ColorSetting("Card Color", 0xFF14151B);
    public final NumberSetting opacity = new NumberSetting("Card Opacity", 0.82, 0.2, 1.0, 0.02);
    public final ColorSetting accent = new ColorSetting("Accent", 0xFF8B5CF6, 0xFF3B82F6, ColorSetting.GRADIENT);
    public final BooleanSetting accentBar = new BooleanSetting("Accent Bar", true);
    public final BooleanSetting shadow = new BooleanSetting("Card Shadow", true);
    public final ColorSetting labelColor = new ColorSetting("Label Color", 0xFF8E93A3);
    /** Glatte Schrift (Inter) oder Minecrafts Pixelschrift in den Karten. */
    public final ModeSetting font = new ModeSetting("Font", 0, "Smooth", "Minecraft");

    public HudDesignModule() {
        super("HUD Design", Category.HUD);
        enabledByDefault();
        addSetting(style);
        addSetting(radius);
        addSetting(cardColor);
        addSetting(opacity);
        addSetting(accent);
        addSetting(accentBar);
        addSetting(shadow);
        addSetting(labelColor);
        addSetting(font);
    }

    /** Kein eigener Eintrag im Mod-Menue mehr: eingestellt wird ueber HUD Style (HudLook). */
    @Override
    public boolean imMenue() { return false; }
}
