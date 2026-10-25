package com.vortex.client.mixin.client;

import com.vortex.client.skin.ActiveSkin;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ersetzt ausschließlich den lokalen Spieler-Skin durch den in der Garderobe
 * gewählten Skin. Andere PlayerInfo-Einträge bleiben vollständig unverändert.
 */
@Mixin(PlayerInfo.class)
public abstract class SkinOverrideMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void vortex$applyCustomSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        try {
            if (com.vortex.client.module.modules.StreamerModeModule.skinVerstecken()) {
                Minecraft mc = Minecraft.getInstance();
                var profil = ((PlayerInfo) (Object) this).getProfile();
                if (mc != null && mc.getUser() != null && profil != null
                        && profil.name().equals(mc.getUser().getName())) {
                    cir.setReturnValue(net.minecraft.client.resources.DefaultPlayerSkin.get(profil));
                    return;
                }
            }

            Identifier textureId = ActiveSkin.textureId();
            if (textureId == null) return;

            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null || client.getConnection() == null) return;

            PlayerInfo localInfo = client.getConnection()
                    .getPlayerInfo(client.player.getName().getString());
            if (localInfo == null || localInfo != (Object) this) return;

            PlayerSkin original = cir.getReturnValue();
            if (original == null) return;

            PlayerModelType model = ActiveSkin.isSlim()
                    ? PlayerModelType.byLegacyServicesName("slim")
                    : PlayerModelType.byLegacyServicesName("default");

            ClientAsset.ResourceTexture customTexture =
                    new ClientAsset.ResourceTexture(textureId, textureId);
            PlayerSkin.Patch patch = PlayerSkin.Patch.create(
                    Optional.of(customTexture),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(model));
            cir.setReturnValue(original.with(patch));
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("SkinOverrideMixin", error);
        }
    }
}
