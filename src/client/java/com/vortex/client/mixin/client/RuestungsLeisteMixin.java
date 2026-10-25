package com.vortex.client.mixin.client;

import com.vortex.client.hud.RuestungsLeiste;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#if 26.2
import net.minecraft.client.gui.Hud;
//#else
//$ import net.minecraft.client.gui.Gui;
//#endif
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Styles, Armor Bar "By armor": die Ruestungsleiste in den Farben der getragenen Ruestung. */
//#if 26.2
@Mixin(Hud.class)
//#else
//$ @Mixin(Gui.class)
//#endif
public abstract class RuestungsLeisteMixin {

    @Inject(method = { "extractArmor", "renderArmor" }, at = @At("HEAD"), cancellable = true, require = 0)
    private static void vortex$ruestungFarbig(GuiGraphicsExtractor g, Player p, int unten, int reihen, int abstand, int x0, CallbackInfo ci) {
        try {
            if (!RuestungsLeiste.aktiv()) return;
            RuestungsLeiste.zeichnen(g, p, unten, reihen, abstand, x0);
            ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("RuestungsLeiste", t);
        }
    }
}
