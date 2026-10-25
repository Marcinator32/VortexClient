package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;
import com.vortex.client.util.Leistung;

/**
 * Dynamic FPS: weniger Bilder, wenn man sie nicht braucht.
 *
 * Vanilla bremst nur, wenn das Fenster minimiert ist. Dieses Modul bremst
 * zusaetzlich, wenn das Fenster im Hintergrund liegt (Alt-Tab, zweiter
 * Bildschirm) und wenn man eine Weile nichts tut. Spart Strom, Hitze und
 * Luefterlaerm -- und der Rechner hat mehr Luft fuer andere Programme.
 * Sobald man zurueck ist, laeuft alles wieder mit vollen FPS.
 */
public class DynamicFpsModule extends Module {

    public final BooleanSetting unfocused = new BooleanSetting("Limit In Background", true);
    public final NumberSetting unfocusedFps = new NumberSetting("Background FPS", 15, 1, 60, 1);
    public final BooleanSetting afk = new BooleanSetting("Limit When AFK", true);
    public final NumberSetting afkSeconds = new NumberSetting("AFK After (s)", 60, 10, 600, 10);
    public final NumberSetting afkFps = new NumberSetting("AFK FPS", 30, 5, 120, 5);

    public DynamicFpsModule() {
        super("Dynamic FPS", Category.PERFORMANCE);
        addSetting(unfocused);
        addSetting(unfocusedFps);
        addSetting(afk);
        addSetting(afkSeconds);
        addSetting(afkFps);
        Leistung.init();
    }
}
