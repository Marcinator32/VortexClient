package com.vortex.client.mixin.client;

import com.vortex.client.core.Fingerabdruck;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {"keybind":"key.mod.irgendwas"}: Vanilla kennt die Taste nicht und zeigt
 * den Schluessel selbst. Solange der Schutz laeuft, machen wir es genauso
 * (siehe Fingerabdruck) -- sonst verriete der Tastenname die Mod.
 */
@Mixin(KeybindContents.class)
public abstract class TastenNameSchutzMixin {

    @Shadow @Final private String name;

    @Inject(method = "getNestedComponent", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$schutz(CallbackInfoReturnable<Component> cir) {
        if (Fingerabdruck.aktiv() && Fingerabdruck.vonMod(this.name)) {
            cir.setReturnValue(Component.literal(this.name));
        }
    }
}
