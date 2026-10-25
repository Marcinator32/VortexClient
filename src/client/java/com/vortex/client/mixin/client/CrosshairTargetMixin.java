package com.vortex.client.mixin.client;

import com.vortex.client.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Laesst in der Freecam Abbau/Angriff/Benutzen vom ECHTEN SPIELER ausgehen statt
 * von der Kamera.
 *
 * Hintergrund: Vanilla berechnet das Crosshair-Ziel (worauf Abbau/Angriff
 * zielt) ueber die aktuelle Kamera-Entity. In der Freecam ist das unsere
 * Kamera-Entity -- das Ziel laege also dort, wo die Freecam hinschaut. Das ist
 * nicht gewuenscht (und auf Servern ein Anticheat-Risiko).
 *
 * Loesung: Nach dem normalen updateCrosshairTarget berechnen wir bei aktiver
 * Freecam das Ziel NEU -- per Raycast vom echten Spieler mit dessen
 * (eingefrorener) Blickrichtung -- und ueberschreiben damit client.crosshairTarget.
 * Der echte Spieler steht still, schaut in seine fixe Richtung und alle
 * Interaktionen kommen von ihm: fuer den Server ganz normales Verhalten,
 * waehrend die Kamera frei bleibt.
 *
 * So gilt: aus der Freecam selbst geht NICHTS aus, alles kommt vom Spieler.
 *
 *   updateCrosshairTarget = method_3190 (float tickDelta)
 *   player.raycast        = method_5745 (double, float, boolean) -> HitResult
 */
//#if 26
@Mixin(Minecraft.class)
//#else
//$ @Mixin(net.minecraft.client.renderer.GameRenderer.class)
//#endif
public abstract class CrosshairTargetMixin {

    @Inject(method = "pick", at = @At("TAIL"))
    private void pvpclient$retargetFromPlayer(float tickDelta, CallbackInfo ci) {
        if (!Freecam.isActive()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        try {
            double reach = player.blockInteractionRange();
            HitResult target = player.pick(reach, 1.0F, false);
            if (target != null) {
                ((MinecraftClientAccessor) client).pvpclient$setCrosshairTarget(target);
            }
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("CrosshairTargetMixin", pvpErr);
            }
    }
}
