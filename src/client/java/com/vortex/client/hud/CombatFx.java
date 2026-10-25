package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HitEffectsModule;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Treffer- und Kill-Effekte (siehe HitEffectsModule). Nur lokal: Toene
 * direkt ueber den Sound-Manager, Effekte als Client-Partikel bzw. ein Blitz,
 * den nur diese Welt-Kopie kennt (negative ID, kein Schaden, kein Feuer).
 */
public final class CombatFx {

    private CombatFx() {}

    private static final class Hit { long at; boolean confirmed; int hurtBefore; }
    private static final Map<Integer, Hit> HITS = new HashMap<>();
    private static final Map<Integer, Long> KILLED = new HashMap<>();
    private static Object letzteWelt = null;
    private static int nextId = -2_000_000;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CombatFx::tick);
    }

    /** Aus GameModeHookMixin: Moment des Schlags. */
    public static void schlag(Entity target) {
        try { AutoGG.schlag(target); } catch (Throwable e) { com.vortex.client.core.Errors.report("AutoGG.hit", e); }
        if (!(target instanceof LivingEntity le)) return;
        Hit h = new Hit();
        h.at = System.currentTimeMillis();
        h.hurtBefore = le.hurtTime;
        HITS.put(le.getId(), h);
    }

    /** Wurde dieses Wesen in den letzten ms Millisekunden von mir geschlagen? */
    public static boolean hitRecently(Entity e, long ms) {
        Hit h = HITS.get(e.getId());
        return h != null && System.currentTimeMillis() - h.at <= ms;
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { HITS.clear(); KILLED.clear(); serie = 0; return; }
        if (mc.level != letzteWelt) { letzteWelt = mc.level; HITS.clear(); KILLED.clear(); serie = 0; }
        if (mc.player.isDeadOrDying()) serie = 0;
        HitEffectsModule m = ModuleManager.INSTANCE.get(HitEffectsModule.class);
        if (m == null || !m.isEnabled()) { HITS.clear(); return; }
        long now = System.currentTimeMillis();
        KILLED.values().removeIf(t -> now - t > 10000);
        for (Iterator<Map.Entry<Integer, Hit>> it = HITS.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            Hit h = e.getValue();
            if (now - h.at > 3000) { it.remove(); continue; }
            Entity ent = mc.level.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity le)) continue;
            if (m.onlyPlayers.get() && !(le instanceof Player)) { it.remove(); continue; }
            if (!h.confirmed && now - h.at < 500 && le.hurtTime > 0 && le.hurtTime >= h.hurtBefore) {
                h.confirmed = true;
                hitSound(mc, m);
                hitParticles(mc, m, le);
                if (m.hitFlash.getIndex() > 0) blitzSeit = now;
            }
            if (h.confirmed && (le.isDeadOrDying() || le.getHealth() <= 0f) && !KILLED.containsKey(le.getId())) {
                KILLED.put(le.getId(), now);
                it.remove();
                killSound(mc, m);
                killEffect(mc, m, le);
                serie++;
                bannerName = le.getName().getString();
                bannerSerie = serie;
                bannerSeit = now;
            }
        }
    }

    private static long blitzSeit = 0;
    private static long bannerSeit = 0;
    private static String bannerName = "";
    private static int bannerSerie = 0;
    private static int serie = 0;

    private static void hitParticles(Minecraft mc, HitEffectsModule m, LivingEntity le) {
        int n = m.particleAmount.getInt();
        ParticleOptions p = switch (m.hitParticles.getIndex()) {
            case 1 -> ParticleTypes.CRIT;
            case 2 -> ParticleTypes.ENCHANTED_HIT;
            case 3 -> ParticleTypes.ELECTRIC_SPARK;
            case 4 -> ParticleTypes.HEART;
            case 5 -> ParticleTypes.NOTE;
            case 6 -> new net.minecraft.core.particles.DustParticleOptions(m.particleColor.get() & 0xFFFFFF, 1.2f);
            case 7 -> ParticleTypes.TOTEM_OF_UNDYING;
            default -> null;
        };
        if (p == null) return;
        double speed = m.hitParticles.getIndex() == 4 || m.hitParticles.getIndex() == 5 ? 0.05 : 0.18;
        burst(mc.level, p, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), m.hitParticles.getIndex() == 4 ? Math.max(1, n / 3) : n, speed);
    }

    /** Aufleuchten am Rand und Kill-Banner (aus ExtraHud, jedes Bild). */
    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor ctx, Minecraft mc) {
        HitEffectsModule m = ModuleManager.INSTANCE.get(HitEffectsModule.class);
        if (m == null || !m.isEnabled()) return;
        long now = System.currentTimeMillis();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        long ba = now - blitzSeit;
        if (m.hitFlash.getIndex() > 0 && ba < 260) {
            float a = 1f - ba / 260f;
            a *= a;
            int c = m.flashColor.get();
            int ca = Math.round(((c >>> 24) & 0xFF) * a);
            boolean weich = m.hitFlash.getIndex() == 2;
            int breite = Math.max(8, (int) (Math.min(w, h) * (weich ? 0.22f : 0.09f)));
            int schritte = 10;
            for (int i = 0; i < schritte; i++) {
                float t = 1f - i / (float) schritte;
                int al = Math.round(ca * t * t * (weich ? 0.55f : 1f));
                if (al <= 0) continue;
                int f = al << 24 | (c & 0xFFFFFF);
                int d0 = i * breite / schritte, d1 = (i + 1) * breite / schritte;
                ctx.fill(0, d0, w, d1, f);
                ctx.fill(0, h - d1, w, h - d0, f);
                ctx.fill(d0, d1, d1, h - d1, f);
                ctx.fill(w - d1, d1, w - d0, h - d1, f);
            }
        }
        long ka = now - bannerSeit;
        if (m.killBanner.get() && bannerSeit > 0 && ka < 2200) {
            float rein = Math.min(1f, ka / 160f), raus = ka > 1800 ? 1f - (ka - 1800) / 400f : 1f;
            float a = Math.max(0f, Math.min(rein, raus));
            int alpha = Math.max(4, Math.round(255 * a));
            int farbe = (alpha << 24) | (m.bannerColor.get() & 0xFFFFFF);
            int weiss = (alpha << 24) | 0xFFFFFF;
            float gross = 1.6f + 0.25f * (1f - rein);
            int y = (int) (h * 0.26f);
            String titel = "ELIMINATED";
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate(w / 2f, y);
            pose.scale(gross, gross);
            ctx.text(mc.font, titel, -mc.font.width(titel) / 2, 0, farbe, true);
            pose.popMatrix();
            int ny = y + Math.round(9 * gross) + 3;
            ctx.text(mc.font, bannerName, w / 2 - mc.font.width(bannerName) / 2, ny, weiss, true);
            if (m.killStreak.get() && bannerSerie >= 2) {
                String s = bannerSerie + " KILL STREAK";
                ctx.text(mc.font, s, w / 2 - mc.font.width(s) / 2, ny + 11, farbe, true);
            }
        }
    }

    private static void hitSound(Minecraft mc, HitEffectsModule m) {
        Object s = switch (m.hitSound.getIndex()) {
            case 1 -> SoundEvents.UI_BUTTON_CLICK;
            case 2 -> SoundEvents.NOTE_BLOCK_PLING;
            case 3 -> SoundEvents.NOTE_BLOCK_BELL;
            case 4 -> SoundEvents.NOTE_BLOCK_BASS;
            case 5 -> SoundEvents.NOTE_BLOCK_HAT;
            case 6 -> SoundEvents.NOTE_BLOCK_CHIME;
            case 7 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case 8 -> SoundEvents.PLAYER_ATTACK_CRIT;
            default -> null;
        };
        play(mc, s, m.hitPitch.getFloat(), m.hitVolume.getFloat());
    }

    private static void killSound(Minecraft mc, HitEffectsModule m) {
        Object s = switch (m.killSound.getIndex()) {
            case 1 -> SoundEvents.PLAYER_LEVELUP;
            case 2 -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case 3 -> SoundEvents.TOTEM_USE;
            case 4 -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case 5 -> SoundEvents.ANVIL_LAND;
            case 6 -> SoundEvents.FIREWORK_ROCKET_BLAST;
            case 7 -> SoundEvents.NOTE_BLOCK_BELL;
            default -> null;
        };
        play(mc, s, 1.0f, m.killVolume.getFloat());
    }

    @SuppressWarnings("unchecked")
    private static void play(Minecraft mc, Object s, float pitch, float volume) {
        if (s == null) return;
        SoundEvent ev = s instanceof Holder<?> h ? ((Holder<SoundEvent>) h).value() : (SoundEvent) s;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
    }

    private static void killEffect(Minecraft mc, HitEffectsModule m, LivingEntity le) {
        ClientLevel level = mc.level;
        double x = le.getX(), y = le.getY(), z = le.getZ(), hgt = le.getBbHeight();
        switch (m.killEffect.getIndex()) {
            case 1 -> lightning(level, x, y, z);
            case 2 -> burst(level, ParticleTypes.TOTEM_OF_UNDYING, x, y + hgt / 2, z, 60, 0.6);
            case 3 -> burst(level, ParticleTypes.FLAME, x, y + hgt / 2, z, 40, 0.12);
            case 4 -> burst(level, ParticleTypes.SOUL_FIRE_FLAME, x, y + hgt / 2, z, 40, 0.12);
            case 5 -> burst(level, ParticleTypes.HEART, x, y + hgt, z, 12, 0.15);
            case 6 -> burst(level, ParticleTypes.EXPLOSION, x, y + hgt / 2, z, 4, 0.0);
            case 7 -> burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), x, y + hgt / 2, z, 50, 0.2);
            case 8 -> burst(level, ParticleTypes.FIREWORK, x, y + hgt, z, 50, 0.25);
            default -> { }
        }
    }

    private static void burst(ClientLevel level, ParticleOptions p, double x, double y, double z, int n, double speed) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            level.addParticle(p, x + r.nextDouble(-0.3, 0.3), y + r.nextDouble(-0.5, 0.5), z + r.nextDouble(-0.3, 0.3),
                    r.nextDouble(-1, 1) * speed, r.nextDouble(0, 1) * speed + 0.05, r.nextDouble(-1, 1) * speed);
        }
    }

    /** Ein Blitz nur fuer dich: visualOnly, eigene negative ID (kollidiert nie mit Server-IDs). */
    private static void lightning(ClientLevel level, double x, double y, double z) {
        try {
            @SuppressWarnings("unchecked")
            EntityType<LightningBolt> type = (EntityType<LightningBolt>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("lightning_bolt"));
            var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (type == null || key == null || !key.getPath().startsWith("lightning")) {
                burst(level, ParticleTypes.END_ROD, x, y + 1, z, 30, 0.2);
                return;
            }
            LightningBolt bolt = new LightningBolt(type, level);
            bolt.setVisualOnly(true);
            bolt.setId(nextId--);
            bolt.snapTo(x, y, z);
            level.addEntity(bolt);
        } catch (Throwable e) {
            burst(level, ParticleTypes.END_ROD, x, y + 1, z, 30, 0.2);
        }
    }
}
