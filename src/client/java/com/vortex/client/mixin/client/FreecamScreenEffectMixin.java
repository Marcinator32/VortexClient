package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if 26.2
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Redirect;
//#endif

/**
 * Freecam: Feuer-, Wasser- und "Kopf im Block"-Bild des Spielers weglassen.
 *
 * Brannte der Spieler, stand er im Wasser oder steckte sein Kopf in einem
 * Block, lag dieses Bild auch in der Freecam ueber dem halben Bildschirm --
 * obwohl die Kamera ganz woanders war.
 *
 * 26.2 hat renderScreenEffect in submit(...) umgebaut, der alte Eingriff ging dort ins
 * Leere (Kopf-im-Block-Bild blieb). Seit 4.60 werden in 26.2 die drei Aufrufe in submit
 * einzeln uebersprungen -- das Totem-Bild (gleiche Methode) bleibt, und LowFire, das in
 * submitFire selbst eingreift, kommt nicht durcheinander.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class FreecamScreenEffectMixin {

    @Inject(method = "renderScreenEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$freecamOhneEffekte(CallbackInfo ci) {
        if (com.vortex.client.freecam.Freecam.ohneOverlays()) ci.cancel();
    }

    //#if 26.2
    @Redirect(method = "submit", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitBlockSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void vortex$freecamOhneBlock(TextureAtlasSprite sprite, PoseStack pose, SubmitNodeCollector out, int farbe) {
        if (!com.vortex.client.freecam.Freecam.ohneOverlays()) submitBlockSprite(sprite, pose, out, farbe);
    }

    @Redirect(method = "submit", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitWater(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void vortex$freecamOhneWasser(Minecraft mc, PoseStack pose, SubmitNodeCollector out) {
        if (!com.vortex.client.freecam.Freecam.ohneOverlays()) submitWater(mc, pose, out);
    }

    @Redirect(method = "submit", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void vortex$freecamOhneFeuer(PoseStack pose, SubmitNodeCollector out, TextureAtlasSprite sprite) {
        if (!com.vortex.client.freecam.Freecam.ohneOverlays()) submitFire(pose, out, sprite);
    }

    @Shadow private static void submitBlockSprite(TextureAtlasSprite sprite, PoseStack pose, SubmitNodeCollector out, int farbe) { }
    @Shadow private static void submitWater(Minecraft mc, PoseStack pose, SubmitNodeCollector out) { }
    @Shadow private static void submitFire(PoseStack pose, SubmitNodeCollector out, TextureAtlasSprite sprite) { }

    //#endif
}
