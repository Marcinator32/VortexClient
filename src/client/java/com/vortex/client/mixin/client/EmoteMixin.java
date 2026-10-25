package com.vortex.client.mixin.client;

import com.vortex.client.cosmetics.Emotes;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Emotes: nach Minecrafts eigener Animation die Emote-Pose setzen
 * (Arme, Beine, Kopf, Koerper). Am Ende der Methode, nichts wird ersetzt --
 * so bleibt Minecrafts Animation erhalten, wenn kein Emote laeuft.
 * Seit 4.97: Gegenstaende in der Hand folgen dem geknickten Unterarm (BiegeEbene).
 */
@Mixin(PlayerModel.class)
public abstract class EmoteMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 0)
    private void vortex$emote(AvatarRenderState state, CallbackInfo ci) {
        try {
            Emotes.anwenden((PlayerModel) (Object) this, state);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("EmoteMixin", t);
        }
    }

    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("TAIL"), require = 0)
    private void vortex$handKnick(AvatarRenderState state, HumanoidArm arm, PoseStack pose, CallbackInfo ci) {
        try {
            Emotes.handKnick(state, arm == HumanoidArm.RIGHT, pose);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("EmoteMixin.hand", t);
        }
    }
}
