package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Item Limiter: herumliegende Items (und XP-Kugeln) nur in der Naehe zeichnen.
 *
 * Mobfarmen, Kaktusfarmen und Item-Lagmaschinen werfen hunderte Items auf
 * einen Fleck. Weiter weg als die eingestellte Distanz werden sie nicht mehr
 * gezeichnet -- aufheben, einsammeln und alles andere funktioniert normal.
 */
public class ItemLimiterModule extends Module {

    public final NumberSetting distance = new NumberSetting("Distance", 24, 4, 64, 2);
    public final BooleanSetting xpOrbs = new BooleanSetting("Also XP Orbs", true);

    public ItemLimiterModule() {
        super("Item Limiter", Category.PERFORMANCE);
        addSetting(distance);
        addSetting(xpOrbs);
    }
}
