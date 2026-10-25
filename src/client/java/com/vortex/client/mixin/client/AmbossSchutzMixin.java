package com.vortex.client.mixin.client;

import com.vortex.client.core.Fingerabdruck;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Amboss: legt der Server ein Item mit Mod-Uebersetzung hinein, uebernimmt
 * Minecraft dessen Namen ins Feld und schickt ihn sofort zurueck. Waehrend
 * dieser Uebernahme laeuft der Schutz (siehe Fingerabdruck).
 */
@Mixin(AnvilScreen.class)
public abstract class AmbossSchutzMixin {

    @Inject(method = "slotChanged", at = @At("HEAD"), require = 0)
    private void vortex$an(AbstractContainerMenu menu, int slot, ItemStack stack, CallbackInfo ci) {
        Fingerabdruck.an();
    }

    @Inject(method = "slotChanged", at = @At("RETURN"), require = 0)
    private void vortex$aus(AbstractContainerMenu menu, int slot, ItemStack stack, CallbackInfo ci) {
        Fingerabdruck.aus();
    }
}
