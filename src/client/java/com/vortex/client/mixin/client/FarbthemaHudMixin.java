package com.vortex.client.mixin.client;

import com.vortex.client.gui.Farbthema;
//#if 26.2
import net.minecraft.client.gui.Hud;
//#else
//$ import net.minecraft.client.gui.Gui;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-Design: merkt sich, wann der HUD gezeichnet wird -- dort wird nur umgefaerbt,
 * wenn "Theme the HUD" an ist (Farbthema.mitHud).
 */
//#if 26.2
@Mixin(Hud.class)
//#else
//$ @Mixin(Gui.class)
//#endif
public abstract class FarbthemaHudMixin {

    @Inject(method = { "extractRenderState", "render" }, at = @At("HEAD"), require = 0)
    private void vortex$hudAn(CallbackInfo ci) { Farbthema.hudAn(); }

    @Inject(method = { "extractRenderState", "render" }, at = @At("RETURN"), require = 0)
    private void vortex$hudAus(CallbackInfo ci) { Farbthema.hudAus(); }
}
