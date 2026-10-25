package com.vortex.client.mixin.client;

import com.vortex.client.cosmetics.ElytraPhysik;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Elytra mit Bewegung (seit 4.67): nach Minecrafts eigener Haltung kommt die Cape-Physik
 * dazu (ElytraPhysik) -- beim Laufen weht sie nach hinten, wippt mit den Schritten und
 * folgt Drehungen. Im Flug bleibt alles, wie Minecraft es macht.
 */
@Mixin(ElytraModel.class)
public abstract class ElytraPhysikMixin {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"), require = 0)
    private void vortex$wehen(HumanoidRenderState zustand, CallbackInfo ci) {
        try {
            ElytraPhysik.anwenden(zustand, leftWing, rightWing);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("ElytraPhysik", t);
        }
    }
}
