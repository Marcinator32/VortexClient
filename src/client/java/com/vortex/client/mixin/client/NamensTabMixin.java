package com.vortex.client.mixin.client;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Animierte Namen in der Tab-Liste (seit 4.63): die Liste fragt jedes Bild neu, also laufen die Farben.
 * Seit 4.95 mit dem Abzeichen [V] bzw. [CREATOR] davor.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class NamensTabMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
    private void vortex$animierterName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        try {
            if (info == null) return;
            String name = info.getProfile().name();
            String stil = com.vortex.client.cosmetics.Cosmetics.namenZeigen() ? com.vortex.client.cosmetics.NamensEffekt.stilFuer(name) : null;
            Component text = stil == null ? cir.getReturnValue() : com.vortex.client.cosmetics.NamensEffekt.faerben(cir.getReturnValue(), name, stil);
            Component abzeichen = com.vortex.client.cosmetics.Abzeichen.prefix(info.getProfile().id());
            if (abzeichen != null) text = Component.empty().append(abzeichen).append(text);
            if (text != cir.getReturnValue()) cir.setReturnValue(text);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("NamensTab", t);
        }
    }
}
