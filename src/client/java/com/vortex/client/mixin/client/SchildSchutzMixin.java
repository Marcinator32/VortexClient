package com.vortex.client.mixin.client;

import com.vortex.client.core.Fingerabdruck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Schild-Editor: die Zeilen, die beim Speichern an den Server gehen, ohne
 * Mod-Uebersetzungen und Mod-Tastennamen neu aufbauen (siehe Fingerabdruck).
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class SchildSchutzMixin {

    @Shadow @Final private String[] messages;
    @Shadow private SignText text;

    @Inject(method = "<init>*", at = @At("TAIL"), require = 0)
    private void vortex$schutz(CallbackInfo ci) {
        try {
            if (!Fingerabdruck.eingeschaltet() || this.text == null || this.messages == null) return;
            boolean gefiltert = Minecraft.getInstance().isTextFilteringEnabled();
            Fingerabdruck.an();
            try {
                for (int i = 0; i < this.messages.length; i++) {
                    this.messages[i] = this.text.getMessage(i, gefiltert).getString();
                }
            } finally {
                Fingerabdruck.aus();
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("SchildSchutz", t);
        }
    }
}
