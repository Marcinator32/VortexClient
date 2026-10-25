package com.vortex.client.util;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.BlockEntityDistanceModule;
import com.vortex.client.module.modules.DynamicFpsModule;
import com.vortex.client.module.modules.EntityCullingModule;
import com.vortex.client.module.modules.ItemLimiterModule;
import com.vortex.client.module.modules.ParticleLimiterModule;
import com.vortex.client.module.modules.PotatoModeModule;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Gemeinsame Rechnung fuer alle Performance-Module.
 *
 * Jedes Modul kann einzeln an sein -- und Potato Mode schaltet sie alle mit
 * eigenen, je nach Staerke strengeren Werten zu. Die Mixins fragen nur hier
 * nach, damit die Regel "Modul ODER Potato, das Strengere gewinnt" an genau
 * einer Stelle steht.
 */
public final class Leistung {

    private Leistung() {}

    /** -1 = Potato aus, sonst 0 Balanced, 1 Aggressive, 2 Maximum. */
    public static int potatoStufe() {
        PotatoModeModule p = ModuleManager.INSTANCE.get(PotatoModeModule.class);
        if (p == null || !p.isEnabled()) return -1;
        return p.strength.getIndex();
    }

    private static int nachStufe(int stufe, int balanced, int aggressive, int maximum) {
        return stufe <= 0 ? balanced : stufe == 1 ? aggressive : maximum;
    }

    /** Maximale Zeichendistanz fuer Block-Entities in Bloecken (0 = unbegrenzt). */
    public static int blockEntityDistanz() {
        int d = 0;
        BlockEntityDistanceModule m = ModuleManager.INSTANCE.get(BlockEntityDistanceModule.class);
        if (m != null && m.isEnabled()) d = m.distance.getInt();
        int st = potatoStufe();
        if (st >= 0) {
            int p = nachStufe(st, 64, 40, 24);
            d = d == 0 ? p : Math.min(d, p);
        }
        return d;
    }

    public static boolean blockEntityZuWeit(BlockPos pos, double cx, double cy, double cz) {
        int d = blockEntityDistanz();
        if (d <= 0 || pos == null) return false;
        double dx = pos.getX() + 0.5 - cx, dy = pos.getY() + 0.5 - cy, dz = pos.getZ() + 0.5 - cz;
        return dx * dx + dy * dy + dz * dz > (double) d * d;
    }

    /** true = dieses Entity in diesem Bild nicht zeichnen. */
    public static boolean entityVerstecken(Entity e, double cx, double cy, double cz) {
        if (e == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.getCameraEntity() || e == mc.player) return false;
        int st = potatoStufe();

        boolean item = e instanceof ItemEntity, orb = e instanceof ExperienceOrb;
        if (item || orb) {
            int d = 0;
            boolean mitOrbs = false;
            ItemLimiterModule m = ModuleManager.INSTANCE.get(ItemLimiterModule.class);
            if (m != null && m.isEnabled()) { d = m.distance.getInt(); mitOrbs = m.xpOrbs.get(); }
            if (st >= 0) {
                int p = nachStufe(st, 32, 24, 12);
                d = d == 0 ? p : Math.min(d, p);
                mitOrbs = true;
            }
            if (d > 0 && (item || mitOrbs) && e.distanceToSqr(cx, cy, cz) > (double) d * d) return true;
        }

        EntityCullingModule ec = ModuleManager.INSTANCE.get(EntityCullingModule.class);
        boolean an = ec != null && ec.isEnabled();
        if (!an && st < 0) return false;
        if (e instanceof Player && !(an && ec.cullPlayers.get())) return false;
        double min = an ? ec.minDistance.get() : 8;
        if (e.distanceToSqr(cx, cy, cz) < min * min) return false;
        if (e.isCurrentlyGlowing()) return false;
        return !Sicht.sichtbar(e, cx, cy, cz);
    }

    private static int partikelDiesenTick = 0;

    /** true = diesen neuen Partikel verwerfen. */
    public static boolean partikelVerwerfen() {
        int anteil = 100, max = 0;
        ParticleLimiterModule m = ModuleManager.INSTANCE.get(ParticleLimiterModule.class);
        if (m != null && m.isEnabled()) { anteil = m.amount.getInt(); max = m.maxPerTick.getInt(); }
        int st = potatoStufe();
        if (st >= 0) {
            anteil = Math.min(anteil, nachStufe(st, 60, 35, 15));
            int pm = nachStufe(st, 300, 150, 60);
            max = max == 0 ? pm : Math.min(max, pm);
        }
        if (anteil >= 100 && max == 0) return false;
        if (max > 0 && partikelDiesenTick >= max) return true;
        if (anteil < 100 && ThreadLocalRandom.current().nextInt(100) >= anteil) return true;
        partikelDiesenTick++;
        return false;
    }

    private static double letzteX, letzteY, letzteZ;
    private static float letzteYaw, letztePitch;
    private static long letzteBewegung = System.currentTimeMillis();

    /** Begrenzt die von Vanilla gewuenschte FPS-Grenze weiter, wenn passend. */
    public static int fpsGrenze(int vanilla) {
        Minecraft mc = Minecraft.getInstance();
        DynamicFpsModule m = ModuleManager.INSTANCE.get(DynamicFpsModule.class);
        boolean an = m != null && m.isEnabled();
        boolean potato = potatoStufe() >= 0;
        if (!an && !potato) return vanilla;
        int grenze = vanilla;
        boolean hintergrund = an ? m.unfocused.get() : true;
        if (hintergrund && !mc.isWindowActive()) {
            grenze = Math.min(grenze, an ? m.unfocusedFps.getInt() : 15);
        }
        boolean afk = an ? m.afk.get() : true;
        long nach = (long) ((an ? m.afkSeconds.get() : 60) * 1000);
        if (afk && mc.player != null && System.currentTimeMillis() - letzteBewegung > nach) {
            grenze = Math.min(grenze, an ? m.afkFps.getInt() : 30);
        }
        return Math.max(1, grenze);
    }

    private static boolean registriert = false;

    /** Einmal registrieren: Partikelzaehler pro Tick, AFK-Erkennung. */
    public static synchronized void init() {
        if (registriert) return;
        registriert = true;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            partikelDiesenTick = 0;
            Sicht.tick(mc);
            var p = mc.player;
            if (p == null) return;
            if (Math.abs(p.getX() - letzteX) > 0.01 || Math.abs(p.getY() - letzteY) > 0.01
                    || Math.abs(p.getZ() - letzteZ) > 0.01 || Math.abs(p.getYRot() - letzteYaw) > 0.1
                    || Math.abs(p.getXRot() - letztePitch) > 0.1 || mc.gui.screen() != null) {
                letzteBewegung = System.currentTimeMillis();
            }
            letzteX = p.getX(); letzteY = p.getY(); letzteZ = p.getZ();
            letzteYaw = p.getYRot(); letztePitch = p.getXRot();
        });
    }

    static final class Sicht {
        private record Eintrag(boolean sichtbar, long tick) {}

        private static final Map<Integer, Eintrag> CACHE = new HashMap<>();
        private static long tickNr = 0;
        private static int budget = 0;
        private static Level welt = null;
        private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();

        static void tick(Minecraft mc) {
            tickNr++;
            budget = 400;
            if (mc.level != welt) {
                welt = mc.level;
                CACHE.clear();
            }
            if (tickNr % 200 == 0) CACHE.entrySet().removeIf(en -> tickNr - en.getValue().tick() > 100);
        }

        static boolean sichtbar(Entity e, double cx, double cy, double cz) {
            Level l = e.level();
            if (l == null) return true;
            AABB bb = e.getBoundingBox();
            if (bb.getXsize() > 4 || bb.getYsize() > 4) return true;
            int id = e.getId();
            Eintrag alt = CACHE.get(id);
            int alter = 3 + (id & 1);
            if (alt != null && tickNr - alt.tick() < alter) return alt.sichtbar();
            if (budget <= 0) return alt == null || alt.sichtbar();
            budget--;
            POS.set(cx, cy, cz);
            if (l.getBlockState(POS).isSolidRender()) {
                CACHE.put(id, new Eintrag(true, tickNr));
                return true;
            }
            double mx = (bb.minX + bb.maxX) / 2, mz = (bb.minZ + bb.maxZ) / 2;
            boolean frei = frei(l, cx, cy, cz, mx, (bb.minY + bb.maxY) / 2, mz)
                    || frei(l, cx, cy, cz, mx, bb.maxY - 0.05, mz)
                    || frei(l, cx, cy, cz, mx, bb.minY + 0.05, mz)
                    || frei(l, cx, cy, cz, bb.minX, bb.maxY - 0.05, bb.minZ)
                    || frei(l, cx, cy, cz, bb.maxX, bb.maxY - 0.05, bb.maxZ);
            CACHE.put(id, new Eintrag(frei, tickNr));
            return frei;
        }

        /**
         * Gitterlauf (Amanatides & Woo) von der Kamera zum Ziel. Blockiert nur
         * durch voll undurchsichtige Bloecke; der Startblock und der Zielblock
         * zaehlen nicht.
         */
        private static boolean frei(Level l, double x0, double y0, double z0, double x1, double y1, double z1) {
            double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
            int x = (int) Math.floor(x0), y = (int) Math.floor(y0), z = (int) Math.floor(z0);
            int ex = (int) Math.floor(x1), ey = (int) Math.floor(y1), ez = (int) Math.floor(z1);
            int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
            double tdx = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
            double tdy = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
            double tdz = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
            double tmx = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? (x + 1 - x0) : (x0 - x)) * tdx);
            double tmy = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? (y + 1 - y0) : (y0 - y)) * tdy);
            double tmz = dz == 0 ? Double.MAX_VALUE : ((dz > 0 ? (z + 1 - z0) : (z0 - z)) * tdz);
            for (int schritt = 0; schritt < 320; schritt++) {
                if (Math.min(tmx, Math.min(tmy, tmz)) >= 1.0) return true;
                if (tmx < tmy && tmx < tmz) { x += sx; tmx += tdx; }
                else if (tmy < tmz) { y += sy; tmy += tdy; }
                else { z += sz; tmz += tdz; }
                if (x == ex && y == ey && z == ez) return true;
                POS.set(x, y, z);
                if (l.getBlockState(POS).isSolidRender()) return false;
            }
            return true;
        }
    }
}
