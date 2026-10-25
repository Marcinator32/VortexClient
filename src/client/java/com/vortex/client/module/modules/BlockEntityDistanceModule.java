package com.vortex.client.module.modules;

import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Block Entity Distance: Truhen, Schilder, Banner, Koepfe und Co. nur in der
 * Naehe zeichnen.
 *
 * Block-Entities sind teuer -- jede Truhe hat ein eigenes Modell, jedes
 * Schild seinen Text. In Lagerhallen und Staedten sind es schnell tausende.
 * Weiter weg als die eingestellte Distanz werden sie einfach nicht mehr
 * gezeichnet; die Bloecke selbst bleiben normal.
 */
public class BlockEntityDistanceModule extends Module {

    public final NumberSetting distance = new NumberSetting("Distance", 48, 8, 128, 8);

    public BlockEntityDistanceModule() {
        super("Block Entity Distance", Category.PERFORMANCE);
        addSetting(distance);
    }
}
