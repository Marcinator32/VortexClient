package com.vortex.client.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Animierte Namen im Chat (seit 4.63): jede Zeile wird beim Zeichnen durchgesehen, Namen
 * von Spielern mit Effekt laufen bunt. Gespeichert bleibt die Zeile, wie sie kam.
 */
@Mixin(ChatComponent.class)
public abstract class NamensChatMixin {

    //#if 26.2
    @ModifyArg(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"),
            index = 2, require = 0)
    private FormattedCharSequence vortex$animierteNamen(FormattedCharSequence zeile) {
        try {
            return com.vortex.client.cosmetics.NamensEffekt.chatZeile(zeile);
        } catch (Throwable t) {
            return zeile;
        }
    }
    //#endif
}
