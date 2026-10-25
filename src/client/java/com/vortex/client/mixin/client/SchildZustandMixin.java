package com.vortex.client.mixin.client;

import com.vortex.client.cosmetics.SchildDaten;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponentMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Traegt die Vortex-Schild-Daten am ItemStackRenderState (siehe SchildDaten). */
@Mixin(ItemStackRenderState.class)
public abstract class SchildZustandMixin implements SchildDaten {
    @Unique private DataComponentMap vortex$schild;
    @Shadow net.minecraft.world.item.ItemDisplayContext displayContext;

    @Override
    public boolean vortex$linkeHand() { return displayContext != null && displayContext.leftHand(); }

    @Override
    public DataComponentMap vortex$schild() { return vortex$schild; }

    @Override
    public void vortex$setzeSchild(DataComponentMap daten) { vortex$schild = daten; }
}
