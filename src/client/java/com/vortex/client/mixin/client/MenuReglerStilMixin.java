package com.vortex.client.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Regler (Lautstaerke, Sichtweite, FOV ...) im Menue-Stil (seit 4.90): Spur und Griff wie
 * die Knoepfe, statt Minecrafts Steinbild. Die Beschriftung zeichnet Minecraft wie immer.
 */
@Mixin(AbstractSliderButton.class)
public abstract class MenuReglerStilMixin {

    @Shadow protected double value;

    @WrapOperation(method = "extractWidgetRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", ordinal = 0),
            require = 0)
    private void vortex$spur(GuiGraphicsExtractor ctx, RenderPipeline p, Identifier id, int x, int y, int w, int h, int farbe, Operation<Void> original) {
        try {
            if (MenuStil.aktiv((AbstractWidget) (Object) this) && MenuStil.reglerSpur(ctx, (AbstractWidget) (Object) this, x, y, w, h, (float) value)) return;
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuReglerStil", t);
        }
        original.call(ctx, p, id, x, y, w, h, farbe);
    }

    @WrapOperation(method = "extractWidgetRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", ordinal = 1),
            require = 0)
    private void vortex$griff(GuiGraphicsExtractor ctx, RenderPipeline p, Identifier id, int x, int y, int w, int h, int farbe, Operation<Void> original) {
        try {
            if (MenuStil.aktiv((AbstractWidget) (Object) this) && MenuStil.reglerGriff(ctx, (AbstractWidget) (Object) this, x, y, w, h)) return;
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuReglerStil", t);
        }
        original.call(ctx, p, id, x, y, w, h, farbe);
    }
}
