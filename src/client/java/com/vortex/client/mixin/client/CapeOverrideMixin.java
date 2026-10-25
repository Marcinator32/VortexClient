package com.vortex.client.mixin.client;

import com.vortex.client.cosmetics.ActiveCape;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Setzt das im Launcher gewaehlte Cape beim eigenen Spieler ein.
 *
 * Gleicher Ansatzpunkt wie SkinOverrideMixin: PlayerInfo.getSkin liefert das
 * PlayerSkin-Objekt, dessen Patch neben Koerper- auch Cape- und
 * Elytra-Textur traegt. Die zweite Stelle ist das Cape -- ablesbar daran,
 * dass SkinOverrideMixin dort Optional.empty() uebergibt.
 *
 * Das Vanilla-Cape wird dadurch automatisch verdeckt: die Cape-Textur des
 * Kontos wird ja gerade ueberschrieben. Ohne gewaehltes Cape erscheint das
 * eigene wieder.
 */
@Mixin(PlayerInfo.class)
public abstract class CapeOverrideMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void vortex$applyCustomCape(CallbackInfoReturnable<PlayerSkin> cir) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null || client.getConnection() == null) return;

            PlayerInfo localInfo = client.getConnection()
                    .getPlayerInfo(client.player.getName().getString());
            Identifier capeTexture = localInfo == (Object) this
                    ? ActiveCape.textureId()
                    : com.vortex.client.cosmetics.FremdeCapes.textur(((PlayerInfo) (Object) this).getProfile().id());
            if (capeTexture == null) return;

            PlayerSkin original = cir.getReturnValue();
            if (original == null) return;

            ClientAsset.ResourceTexture cape =
                    new ClientAsset.ResourceTexture(capeTexture, capeTexture);

            PlayerSkin.Patch patch = PlayerSkin.Patch.create(
                    Optional.empty(),
                    Optional.of(cape),
                    Optional.of(cape),
                    Optional.empty());
            cir.setReturnValue(original.with(patch));
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("CapeOverrideMixin", error);
        }
    }
}
