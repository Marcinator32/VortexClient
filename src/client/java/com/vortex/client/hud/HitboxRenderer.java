package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HitboxModule;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet die Hitboxen aller Entities in der Welt (wie F3+B), aber
 * eingefaerbt nach Kategorie und einzeln im ClickGUI schaltbar.
 *
 * Verifiziert gegen die ECHTEN 1.21.11-Yarn-Mappings (build.4):
 *   - WorldRenderEvents liegt jetzt im Paket ...rendering.v1.world
 *   - Lines-RenderLayer: RenderTypes.lines() (frueher RenderLayer.getLines())
 *   - ShapeRenderer.renderShape(PoseStack, VertexConsumer, VoxelShape,
 *       double offsetX, offsetY, offsetZ, int color, float lineWidth)
 *     (frueher drawBox mit r,g,b,a -- in 1.21.11 ein gepackter int + lineWidth)
 *   - Shapes.create(AABB) wandelt die Entity-AABB in eine VoxelShape
 *   - Koordinaten relativ zur Kamera -> Kamera-Pos als Offset uebergeben
 */
public final class HitboxRenderer {

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            HitboxModule mod = (HitboxModule) find(HitboxModule.class);
            if (mod == null || !mod.isEnabled()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null) return;

            PoseStack matrices = context.poseStack();
            SubmitNodeCollector collector = context.submitNodeCollector();
            if (matrices == null || collector == null) return;

            long pvpT0 = System.nanoTime();
            try {
                float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

                Vec3 cam = EspRender.cameraOffset(client, tickDelta);
                if (com.vortex.client.freecam.Freecam.isActive()) {
                    cam = com.vortex.client.freecam.Freecam.getPos();
                }

                float lineWidth = mod.lineWidth.getFloat();

                double range = 128.0;
                double px = client.player.getX();
                double py = client.player.getY();
                double pz = client.player.getZ();

                for (Entity entity : com.vortex.client.core.EntityCache.all()) {
                    if (entity == client.player) continue;
                    if (Math.abs(entity.getX() - px) > range
                            || Math.abs(entity.getY() - py) > range
                            || Math.abs(entity.getZ() - pz) > range) continue;
                    int color;
                    if (entity instanceof Player) {
                        if (!mod.showPlayers.get()) continue;
                        color = com.vortex.client.core.Friends.markiert(entity)
                                ? com.vortex.client.core.Friends.farbe()
                                : mod.playerColor.get();
                    } else if (entity instanceof Enemy) {
                        if (!mod.showHostiles.get()) continue;
                        color = mod.hostileColor.get();
                    } else if (entity instanceof Animal || entity instanceof AgeableMob) {
                        if (!mod.showAnimals.get()) continue;
                        color = mod.animalColor.get();
                    } else {
                        if (!mod.showMisc.get()) continue;
                        color = mod.miscColor.get();
                    }

                    if ((color >>> 24) == 0) {
                        color = 0xFF000000 | color;
                    }

                    Vec3 pos = entity.getPosition(tickDelta);
                    double hw = entity.getBbWidth() / 2.0;
                    double h = entity.getBbHeight();
                    AABB box = new AABB(
                            pos.x - hw, pos.y, pos.z - hw,
                            pos.x + hw, pos.y + h, pos.z + hw);
                    EspRender.submitBox(collector, matrices, box, cam, color, lineWidth, false);
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("HitboxRenderer", pvpErr);
            } finally {
                com.vortex.client.core.Profiler.record("Hitboxes",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    private static Module find(Class<? extends Module> type) {
        return ModuleManager.INSTANCE.get(type);
    }
}
