package com.vortex.client.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menue-Waechter: wer schliesst Kisten und Server-Menues? (siehe MenueWaechter) */
@Mixin(AbstractContainerScreen.class)
public abstract class MenueWaechterMixin {

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void vortex$zu(CallbackInfo ci) {
        com.vortex.client.core.MenueWaechter.zu((AbstractContainerScreen<?>) (Object) this);
    }
}
