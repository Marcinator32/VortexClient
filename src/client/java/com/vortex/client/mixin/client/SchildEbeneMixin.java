package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.cosmetics.SchildDaten;
import com.vortex.client.cosmetics.SchildSkins;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponentMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shield Skins "ein fuer alle Mal" (seit 4.36): Hat der Schild einen Vortex-Skin, zeichnet
 * Vortex ihn schon hier -- ganz am Anfang der Item-Ebene, bevor der Schild-Renderer
 * ueberhaupt gefragt wird -- und ueberspringt den Rest.
 *
 * Grund: Mods wie "ShieldStatus" (Walksy) haken sich in den Schild-Renderer oder in genau
 * diesen Aufruf ein und zeichnen ihren eigenen (eingefaerbten) Schild. Je nach Version
 * an einer anderen Stelle -- bisher kam Vortex dann zu spaet. Hier kommt keine dieser
 * Stellen mehr dran. Ob ein Skin drauf gehoert, wurde schon beim Aufbereiten des Items
 * bestimmt (SchildBesitzerMixin), also auch dann, wenn eine Mod den Schild-Renderer ersetzt.
 */
@Mixin(value = ItemStackRenderState.LayerRenderState.class, priority = 500)
public abstract class SchildEbeneMixin {
    @Shadow private SpecialModelRenderer<Object> specialRenderer;
    @Shadow private ItemStackRenderState.FoilType foilType;
    //#if 26
    @Shadow @Final ItemStackRenderState this$0;

    @Shadow private void applyTransform(PoseStack.Pose pose) { throw new AssertionError(); }
    //#else
    //$ @Shadow @Final ItemStackRenderState field_55345;
    //$ @Shadow net.minecraft.client.renderer.block.model.ItemTransform transform;
    //#endif

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void vortex$schildSkin(PoseStack pose, SubmitNodeCollector c, int licht, int overlay, int umriss, CallbackInfo ci) {
        try {
            if (this.specialRenderer == null) return;
            //#if 26
            ItemStackRenderState aussen = this.this$0;
            //#else
            //$ ItemStackRenderState aussen = this.field_55345;
            //#endif
            DataComponentMap daten = ((SchildDaten) (Object) aussen).vortex$schild();
            if (daten == null || SchildSkins.textur(daten) == null) return;
            pose.pushPose();
            //#if 26
            applyTransform(pose.last());
            //#else
            //$ transform.apply(((SchildDaten) (Object) aussen).vortex$linkeHand(), pose.last());
            //#endif
            boolean ok = SchildSkins.zeichnen(daten, pose, c, licht, overlay, foilType != ItemStackRenderState.FoilType.NONE, umriss);
            pose.popPose();
            if (ok) ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("SchildEbene", t);
        }
    }
}
