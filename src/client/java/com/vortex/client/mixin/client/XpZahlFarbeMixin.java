package com.vortex.client.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vortex.client.styles.Farbe;
import com.vortex.client.styles.Kategorie;
import com.vortex.client.styles.Styles;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//#if 26.2
import net.minecraft.client.gui.Hud;
//#else
//$ import net.minecraft.client.gui.Gui;
//#endif

/**
 * Styles, XP Bar (seit 4.92): die Level-Zahl ueber der Leiste in der gewaehlten Farbe statt
 * Gruen. Gezeichnet wie bei Minecraft (vier schwarze Umrisse, dann die Zahl) -- nur mit der
 * Farbe des Styles. Ohne gewaehlte Farbe zeichnet Minecraft selbst.
 */
//#if 26.2
@Mixin(Hud.class)
//#else
//$ @Mixin(Gui.class)
//#endif
public abstract class XpZahlFarbeMixin {

    @WrapOperation(method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"),
            require = 0)
    private void vortex$levelFarbe(GuiGraphicsExtractor g, Font font, int level, Operation<Void> original) {
        int farbe = 0;
        try {
            String gew = Styles.gewaehlt(Kategorie.XP_BAR);
            Farbe f = gew != null && gew.startsWith("gen:") ? Farbe.von(gew.substring(4)) : null;
            if (f != null) farbe = 0xFF000000 | f.bei(0.35f);
        } catch (Throwable ignored) { }
        if (farbe == 0) {
            original.call(g, font, level);
            return;
        }
        Component text = Component.translatable("gui.experience.level", level);
        int x = (g.guiWidth() - font.width(text)) / 2;
        int y = g.guiHeight() - 24 - 9 - 2;
        g.text(font, text, x + 1, y, 0xFF000000, false);
        g.text(font, text, x - 1, y, 0xFF000000, false);
        g.text(font, text, x, y + 1, 0xFF000000, false);
        g.text(font, text, x, y - 1, 0xFF000000, false);
        g.text(font, text, x, y, farbe, false);
    }
}
