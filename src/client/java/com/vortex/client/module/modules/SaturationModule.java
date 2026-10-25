package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.module.Module;

/**
 * AppleSkin-Steuerung -- schaltet AppleSkins echte HUD-Overlays an/aus.
 *
 * AppleSkin ist als Mod fest in den Client eingebettet (Jar-in-Jar) und
 * zeichnet die Saturation direkt auf die Hunger-Keulen, optional die
 * Food-/Health-Vorschau. Dieses Modul legt nur AppleSkins eigene
 * Config-Schalter um -- ueber ModConfig.getInstance().
 *
 * Verifiziert aus AppleSkin-Bytecode (squeek/appleskin/ModConfig):
 *   Felder showSaturationHudOverlay, showFoodValuesHudOverlay,
 *   showFoodHealthHudOverlay; getInstance(); save().
 *
 * Die Felder werden per Reflection gesetzt, damit unser Code nicht hart
 * gegen AppleSkins Klassen kompiliert (die erst zur Laufzeit da sind).
 */
public class SaturationModule extends Module {

    public final BooleanSetting foodPreview =
        new BooleanSetting("Food Preview", true);
    public final BooleanSetting healthPreview =
        new BooleanSetting("Health Preview", true);

    public SaturationModule() {
        super("AppleSkin", Category.HUD);
        addSetting(foodPreview);
        addSetting(healthPreview);
        enabledByDefault();
    }

    @Override
    protected void onEnable() {
        apply(true);
    }

    @Override
    protected void onDisable() {
        apply(false);
    }

    /** Legt AppleSkins Config-Schalter per Reflection um. */
    private void apply(boolean on) {
        try {
            Class<?> cfgClass = Class.forName("squeek.appleskin.ModConfig");

            Object cfg;
            try {
                cfg = cfgClass.getMethod("getInstance").invoke(null);
            } catch (Throwable t) {
                var inst = cfgClass.getDeclaredField("INSTANCE");
                inst.setAccessible(true);
                cfg = inst.get(null);
            }
            if (cfg == null) return;

            setField(cfgClass, cfg, "showSaturationHudOverlay", on);
            setField(cfgClass, cfg, "showFoodValuesHudOverlay", on && foodPreview.get());
            setField(cfgClass, cfg, "showFoodHealthHudOverlay", on && healthPreview.get());

            try {
                cfgClass.getMethod("save").invoke(cfg);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    private static void setField(Class<?> cls, Object obj, String name, boolean value) {
        try {
            var f = cls.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(obj, value);
        } catch (Throwable ignored) {
        }
    }
}
