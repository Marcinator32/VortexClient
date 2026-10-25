package com.vortex.client.mixin.client;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dynamic FPS / Potato Mode: die von Vanilla berechnete FPS-Grenze weiter
 * senken, wenn das Fenster im Hintergrund liegt oder man AFK ist.
 */
@Mixin(FramerateLimitTracker.class)
public abstract class DynamicFpsMixin {

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void vortex$dynamischeFps(CallbackInfoReturnable<Integer> cir) {
        try {
            int vanilla = cir.getReturnValueI();
            int neu = com.vortex.client.util.Leistung.fpsGrenze(vanilla);
            if (neu != vanilla) cir.setReturnValue(neu);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("DynamicFpsMixin", t);
        }
    }
}
