package com.vortex.client.module.modules;

import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;
import com.vortex.client.util.Leistung;

/**
 * Particle Limiter: weniger Partikel, ohne sie ganz abzuschalten.
 *
 * "Amount" laesst nur einen Teil aller neuen Partikel zu (Explosionen,
 * Kristalle und Regen wirken dann duenner), "Max Per Tick" deckelt Spitzen
 * wie TNT-Ketten oder Partikel-Lagmaschinen hart nach oben.
 */
public class ParticleLimiterModule extends Module {

    public final NumberSetting amount = new NumberSetting("Amount %", 50, 0, 100, 5);
    public final NumberSetting maxPerTick = new NumberSetting("Max Per Tick", 200, 10, 1000, 10);

    public ParticleLimiterModule() {
        super("Particle Limiter", Category.PERFORMANCE);
        addSetting(amount);
        addSetting(maxPerTick);
        Leistung.init();
    }
}
