package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Hit & Kill Effects: eigener Ton bei jedem Treffer, Ton und Effekt bei einem
 * Kill -- alles nur bei dir, niemand sonst sieht oder hoert es. Seit 4.75 auch
 * Partikel bei jedem Treffer, ein kurzes Aufleuchten am Bildschirmrand und ein
 * Kill-Banner mit Serie.
 *
 * Ein Treffer zaehlt erst, wenn der Server ihn bestaetigt (das Ziel zuckt
 * rot), nicht schon beim Klick. Ein Kill: ein Ziel, das du in den letzten
 * 3 Sekunden getroffen hast, stirbt.
 */
public class HitEffectsModule extends Module {

    public final ModeSetting hitSound = new ModeSetting("Hit Sound", 1, "Off", "Click", "Pling", "Bell", "Bass", "Hat", "Chime", "XP", "Crit");
    public final NumberSetting hitVolume = new NumberSetting("Hit Volume", 0.8, 0.1, 1.0, 0.05);
    public final NumberSetting hitPitch = new NumberSetting("Hit Pitch", 1.0, 0.5, 2.0, 0.05);
    public final ModeSetting killSound = new ModeSetting("Kill Sound", 1, "Off", "Level Up", "Challenge", "Totem", "Thunder", "Anvil", "Firework", "Bell");
    public final NumberSetting killVolume = new NumberSetting("Kill Volume", 0.8, 0.1, 1.0, 0.05);
    public final ModeSetting killEffect = new ModeSetting("Kill Effect", 1, "Off", "Lightning", "Totem", "Flames", "Soul Flames", "Hearts", "Explosion", "Blood", "Firework");
    public final BooleanSetting onlyPlayers = new BooleanSetting("Only Players", true);

    public final ModeSetting hitParticles = new ModeSetting("Hit Particles", 0, "Off", "Crit", "Magic", "Sparks", "Hearts", "Notes", "Color Dust", "Totem");
    public final ColorSetting particleColor = new ColorSetting("Particle Color", 0xFFF59E0B).noGradient();
    public final NumberSetting particleAmount = new NumberSetting("Particle Amount", 8, 1, 30, 1);
    public final ModeSetting hitFlash = new ModeSetting("Hit Flash", 0, "Off", "Screen Edges", "Soft Glow");
    public final ColorSetting flashColor = new ColorSetting("Flash Color", 0xA0FFFFFF).noGradient();
    public final BooleanSetting killBanner = new BooleanSetting("Kill Banner", false);
    public final ColorSetting bannerColor = new ColorSetting("Banner Color", 0xFFFF4D4D);
    public final BooleanSetting killStreak = new BooleanSetting("Kill Streak", true);

    public HitEffectsModule() {
        super("Hit & Kill Effects", Category.PVP);
        addSetting(hitSound); addSetting(hitVolume); addSetting(hitPitch);
        addSetting(killSound); addSetting(killVolume); addSetting(killEffect); addSetting(onlyPlayers);
        addSetting(hitParticles); addSetting(particleColor); addSetting(particleAmount);
        addSetting(hitFlash); addSetting(flashColor);
        addSetting(killBanner); addSetting(bannerColor); addSetting(killStreak);
    }
}
