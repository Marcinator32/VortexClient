package com.vortex.client.mixin.client;

import com.vortex.client.gui.Farbthema;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Client-Design (seit 4.69): an den vier Stellen, durch die JEDE GUI-Farbe geht (Text,
 * Flaechen/Verlaeufe, Bilder/Masken mit Toenung, gekachelte Bilder), wird die Farbe ins
 * gewaehlte Design umgerechnet (Farbthema.um). Mit dem Standard-Design passiert nichts.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class FarbthemaMixin {

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private int vortex$text(int farbe) { return Farbthema.um(farbe); }

    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 0)
    private int vortex$flaeche(int farbe) { return Farbthema.um(farbe); }

    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true, require = 0)
    private Integer vortex$flaeche2(Integer farbe) { return farbe == null ? null : Farbthema.um(farbe); }

    @ModifyVariable(method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 0)
    private int vortex$bild(int farbe) { return Farbthema.um(farbe); }

    @ModifyVariable(method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFFI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 0)
    private int vortex$bild2(int farbe) { return Farbthema.um(farbe); }

    @ModifyVariable(method = "innerTiledBlit", at = @At("HEAD"), argsOnly = true, ordinal = 6, require = 0)
    private int vortex$kacheln(int farbe) { return Farbthema.um(farbe); }
}
