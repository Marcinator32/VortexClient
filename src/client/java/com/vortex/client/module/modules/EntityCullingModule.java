package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Entity Culling: zeichnet Entities nicht, die hinter Waenden stecken.
 *
 * Minecraft zeichnet jedes Tier, jede Lore und jeden Rahmen im Sichtfeld --
 * auch wenn ein Berg dazwischen liegt. In Farmen, Staedten und unter der Erde
 * ist das oft der groesste Teil der Arbeit. Hier wird von der Kamera aus
 * geprueft, ob ein Entity ueberhaupt zu sehen ist (Strahlen zu Mitte, Kopf
 * und Fuessen, nur durch undurchsichtige Bloecke blockiert; Glas, Laub und
 * Zaeune lassen durch). Das Ergebnis wird ein paar Ticks gemerkt, damit die
 * Pruefung selbst nichts kostet.
 *
 * Spieler werden standardmaessig nie versteckt, damit Namensschilder durch
 * Waende sichtbar bleiben wie gewohnt.
 */
public class EntityCullingModule extends Module {

    public final NumberSetting minDistance = new NumberSetting("Min Distance", 8, 2, 32, 1);
    public final BooleanSetting cullPlayers = new BooleanSetting("Also Players", false);

    public EntityCullingModule() {
        super("Entity Culling", Category.PERFORMANCE);
        addSetting(minDistance);
        addSetting(cullPlayers);
    }
}
