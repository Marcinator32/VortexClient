package com.vortex.client.cosmetics;

import net.minecraft.core.component.DataComponentMap;

/**
 * Am ItemStackRenderState (per Mixin): Vortex-Schild-Daten (Skin, Shield Status) fuer
 * das Item, das gerade aufbereitet wurde -- oder null. Seit 4.36 zeichnet Vortex
 * Schild-Skins direkt an der Item-Ebene (SchildEbeneMixin), unabhaengig davon, was
 * andere Mods mit dem Schild-Renderer machen.
 */
public interface SchildDaten {
    DataComponentMap vortex$schild();
    void vortex$setzeSchild(DataComponentMap daten);
    /** Wird das Item in der linken Hand gezeichnet? (Der Anzeige-Kontext ist nicht oeffentlich.) */
    boolean vortex$linkeHand();
}
