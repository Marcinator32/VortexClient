package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.cosmetics.AnimCapes;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leuchtende Capes auch auf der Elytra (seit 4.64).
 *
 * Mit Elytra zeichnet Minecraft statt des Capes die Fluegel -- mit der Cape-Textur,
 * aber ohne unsere Leuchtebene. Funken, Blitze und Glanz waren dann weg, nachts war
 * die Elytra fast schwarz. Hier kommt die Leuchtebene als zweite Schicht auf dasselbe
 * Modell, genau wie beim Cape (CapeEbene, RenderTypes.eyes).
 *
 * Eingriff am Ende von renderLayers: Modell, Zustand und Textur sind dort Argumente,
 * kein Feld mit versionsabhaengigem Typ noetig. Passt die Methode in einer Version
 * nicht, findet Mixin sie nicht (require = 0) und es bleibt beim Cape ohne Leuchten.
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class ElytraGlowMixin {

    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At("TAIL"), require = 0)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void vortex$elytraLeuchten(EquipmentClientInfo.LayerType typ, ResourceKey<?> asset, Model model, Object zustand,
                                       ItemStack stack, PoseStack pose, SubmitNodeCollector collector, int licht,
                                       Identifier textur, int umriss, int reihe, CallbackInfo ci) {
        try {
            if (typ != EquipmentClientInfo.LayerType.WINGS || textur == null) return;
            Identifier glow = AnimCapes.glowZu(textur);
            if (glow == null) return;
            collector.submitModel((Model) model, zustand, pose, RenderTypes.eyes(glow), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, -1, null, 0, null);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("ElytraGlowMixin", t);
        }
    }
}
