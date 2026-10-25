package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.waypoint.WaypointSettings;
import com.vortex.client.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet die Waypoints in der Welt: je Marker eine senkrechte Saeule aus vier
 * Linien, damit er auch aus der Ferne und ueber Gelaende hinweg auffaellt.
 *
 * Die Beschriftung und die Randpfeile laufen getrennt davon ueber das HUD (siehe
 * WaypointHud) -- dort laesst sich Text sauber und lesbar zeichnen.
 */
public final class WaypointRenderer {

    private WaypointRenderer() {}

    /** Aktuelle Dimension als Text, oder null wenn nicht ermittelbar. */
    public static String currentDimension(Minecraft client) {
        try {
            return client.level.dimension().identifier().toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Kennung der aktuellen Welt als Text "welt|dimension" (siehe
     * {@link com.vortex.client.waypoint.WaypointWorlds}). Solange die Welt nach
     * dem Beitritt noch erkannt wird: "?|dimension" -- dazu passt kein Marker.
     */
    private static String cachedKey = null;
    private static long cachedAt = 0L;
    private static Object cachedWorld = null;

    public static String currentWorldKey(Minecraft client) {
        Object world = client.level;
        long now = System.currentTimeMillis();
        if (cachedKey != null && world == cachedWorld && (now - cachedAt) < 500L) {
            return cachedKey;
        }
        var hier = com.vortex.client.waypoint.WaypointWorlds.hier(client, false);
        String key;
        if (hier != null) {
            key = hier.schluessel();
        } else {
            String d = currentDimension(client);
            key = "?|" + (d == null ? "?" : d);
        }
        if (hier != null) {
            cachedKey = key;
            cachedWorld = world;
            cachedAt = now;
        }
        return key;
    }

    /** Erzwingt eine Neuberechnung (z.B. beim Weltwechsel). */
    public static void invalidateWorldKey() {
        cachedKey = null;
        cachedWorld = null;
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            long pvpT0 = System.nanoTime();
            try {
            WaypointSettings mod = WaypointSettings.INSTANCE;
            if (!mod.isEnabled()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null) return;

            PoseStack matrices = context.poseStack();
            SubmitNodeCollector collector = context.submitNodeCollector();
            if (matrices == null || collector == null) return;

            try {
                float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                Vec3 cam = EspRender.cameraOffset(client, tickDelta);

                float lw = mod.lineWidth.getFloat();
                double maxDist = mod.maxDistance.get();

                Vec3 tracerStart = EspRender.tracerStart(client, cam, tickDelta);

                var hier = com.vortex.client.waypoint.WaypointWorlds.hier(client, false);
                if (hier == null) return;
                double blockRange = mod.blockRadius.get();
                double blockRangeSq = blockRange * blockRange;
                double pxx = client.player.getX();
                double pyy = client.player.getY();
                double pzz = client.player.getZ();

                for (WaypointManager.Waypoint w : WaypointManager.all()) {
                    if (!w.visible || !WaypointManager.matches(w, hier)) continue;
                    double cx = w.x + 0.5, cz = w.z + 0.5;
                    double dx = cx - client.player.getX();
                    double dz = cz - client.player.getZ();
                    double distSq = dx * dx + dz * dz;
                    if (maxDist > 0 && distSq > maxDist * maxDist) continue;

                    int color = w.color | 0xFF000000;

                    if (!w.blocks.isEmpty()) {
                        for (net.minecraft.core.BlockPos bp : w.blocks) {
                            double bdx = bp.getX() + 0.5 - pxx;
                            double bdy = bp.getY() + 0.5 - pyy;
                            double bdz = bp.getZ() + 0.5 - pzz;
                            if (bdx * bdx + bdy * bdy + bdz * bdz > blockRangeSq) continue;
                            net.minecraft.world.phys.AABB box =
                                    new net.minecraft.world.phys.AABB(
                                            bp.getX(), bp.getY(), bp.getZ(),
                                            bp.getX() + 1.0, bp.getY() + 1.0, bp.getZ() + 1.0);
                            int bc = mod.blockColor.get();
                            if ((bc >>> 24) == 0) bc = color;
                            EspRender.submitBox(collector, matrices, box, cam, bc,
                                    mod.blockLineWidth.getFloat());
                        }
                    }

                    if (tracerStart != null && (mod.tracers.get() || w.tracer)) {
                        final Vec3 renderStart = tracerStart;
                        final Vec3 renderTarget = new Vec3(cx, w.y, cz);
                        final Vec3 renderCam = cam;
                        final int renderColor = color;
                        final float renderWidth = lw;
                        EspRender.submitLines(collector, matrices,
                                (matrix, lines) -> EspRender.drawTracer(matrix, lines,
                                        renderStart, renderTarget, renderCam,
                                        renderColor, renderWidth));
                    }
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("Waypoints", pvpErr);
            }
                    } finally {
                com.vortex.client.core.Profiler.record("Waypoints",
                        System.nanoTime() - pvpT0);
            }
        });
    }
}
