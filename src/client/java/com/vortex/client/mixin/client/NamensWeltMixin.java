package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animierte Namen ueber dem Kopf (seit 4.63): Hat der Spieler einen Namens-Effekt gewaehlt,
 * zeichnet NamensEffekt den Namen statt Minecraft -- Buchstabe fuer Buchstabe. Die
 * Zeile darunter (Punkte vom Scoreboard) bleibt, wie Minecraft sie zeichnet.
 *
 * Seit 4.95 steht davor das Abzeichen [V] bzw. [CREATOR]; bei einem Namens-Effekt in einer
 * eigenen Zeile darueber, damit der Effekt nur den Namen bewegt.
 */
@Mixin(AvatarRenderer.class)
public abstract class NamensWeltMixin {

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$animierterName(AvatarRenderState state, PoseStack pose, SubmitNodeCollector q, CameraRenderState cam, CallbackInfo ci) {
        try {
            if (state.nameTag == null || state.nameTagAttachment == null) return;
            net.minecraft.network.chat.Component abzeichen = com.vortex.client.cosmetics.Abzeichen.prefixFuerEntity(state.id);
            String stil = com.vortex.client.cosmetics.Cosmetics.namenZeigen() ? com.vortex.client.cosmetics.Cosmetics.fuerEntity(state.id).namensstil() : "";
            if (stil.isEmpty() || com.vortex.client.cosmetics.NamensEffekt.nummer(stil) < 0) {
                if (abzeichen != null) state.nameTag = net.minecraft.network.chat.Component.empty().append(abzeichen).append(state.nameTag);
                return;
            }
            pose.pushPose();
            if (state.scoreText != null) {
                q.submitNameTag(pose, state.nameTagAttachment, 0, state.scoreText, !state.isDiscrete, state.lightCoords, cam);
                pose.translate(0f, 9f * 1.15f * 0.025f, 0f);
            }
            com.vortex.client.cosmetics.NamensEffekt.welt(pose, q, cam, state.nameTagAttachment, state.nameTag.getString(), stil,
                    state.lightCoords, state.isDiscrete, 0f);
            if (abzeichen != null) {
                pose.translate(0f, 9f * 1.15f * 0.025f, 0f);
                q.submitNameTag(pose, state.nameTagAttachment, 0, abzeichen, !state.isDiscrete, state.lightCoords, cam);
            }
            pose.popPose();
            ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("NamensWelt", t);
        }
    }
}
