package com.vortex.client.hud;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Glatt.Schrift;
import com.vortex.client.module.modules.OpponentInfoModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Zeichnet die Gegner-Info-Karte (siehe {@link OpponentInfoModule}).
 *
 * Aufbau (bei Skalierung 1, 160 breit):
 *   [Kopf]  Name                    12 m · 45 ms
 *           ███████████████░░░  17.5 ♥ (+4)
 *   [Helm][Brust][Hose][Schuhe] [Hand][Neben]   2 pops  AHEAD
 */
public final class OpponentHud {

    private OpponentHud() {}

    public static final int BREITE = 168;

    public static int hoehe(OpponentInfoModule m) {
        return m.armor.get() ? 64 : 42;
    }

    private static LivingEntity ziel;
    private static long zielSeit;
    private static float angezeigt = -1f;
    private static float einblenden = 0f;
    private static long letzteZeit = 0;

    private static volatile int geschlagenId = -1, getroffenVonId = -1;
    private static volatile long geschlagenZeit, getroffenZeit;
    private static boolean angemeldet = false;

    /** Vom GameModeHookMixin: du hast gerade dieses Wesen geschlagen. */
    public static void schlag(Entity e) {
        if (e == null) return;
        geschlagenId = e.getId();
        geschlagenZeit = System.currentTimeMillis();
    }

    /** Wer dich trifft, steht im Schadens-Paket des Servers. */
    private static void anmelden() {
        if (angemeldet) return;
        angemeldet = true;
        com.vortex.client.core.PacketHooks.onReceive(p -> {
            if (p instanceof net.minecraft.network.protocol.game.ClientboundDamageEventPacket d) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && d.entityId() == mc.player.getId() && d.sourceCauseId() > 0) {
                    getroffenVonId = d.sourceCauseId();
                    getroffenZeit = System.currentTimeMillis();
                }
            }
            return false;
        });
    }

    private static LivingEntity lebend(Minecraft mc, int id) {
        if (id < 0 || mc.level == null) return null;
        Entity e = mc.level.getEntity(id);
        return e instanceof LivingEntity le ? le : null;
    }

    /** Wer ist gerade der Gegner? Null = keiner. */
    private static LivingEntity suche(Minecraft mc, OpponentInfoModule m) {
        anmelden();
        LocalPlayer ich = mc.player;
        if (ich == null || mc.level == null) return null;
        long jetzt = System.currentTimeMillis();
        long keepMs = m.keep.getInt() * 1000L;
        LivingEntity kandidat = null;
        if (jetzt - geschlagenZeit <= keepMs) kandidat = lebend(mc, geschlagenId);
        if (kandidat == null && jetzt - getroffenZeit <= keepMs) kandidat = lebend(mc, getroffenVonId);
        if (kandidat == null && m.crosshair.get() && mc.hitResult instanceof EntityHitResult eh
                && eh.getEntity() instanceof LivingEntity le) kandidat = le;
        if (kandidat != null && (!kandidat.isAlive() || kandidat.isRemoved() || kandidat == ich)) kandidat = null;
        if (kandidat != null && m.playersOnly.get() && !(kandidat instanceof Player)) kandidat = null;
        if (kandidat != null && kandidat.distanceTo(ich) > 64) kandidat = null;
        if (kandidat != null) {
            ziel = kandidat;
            zielSeit = jetzt;
        } else if (ziel != null && (jetzt - zielSeit > m.keep.getInt() * 1000L || !ziel.isAlive() || ziel.isRemoved())) {
            ziel = null;
        }
        return ziel;
    }

    public static void zeichnen(GuiGraphicsExtractor g, Minecraft mc, OpponentInfoModule m) {
        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        LivingEntity e = suche(mc, m);
        boolean editor = mc.gui.screen() instanceof com.vortex.client.gui.HudEditorScreen;
        einblenden = naeher(einblenden, e != null || editor ? 1f : 0f, 10f, dt);
        if (einblenden < 0.01f) {
            angezeigt = -1f;
            return;
        }
        float a = einblenden;
        float s = m.scale.getFloat();
        var p = g.pose();
        p.pushMatrix();
        try {
            p.translate(m.x.getInt(), m.y.getInt());
            p.scale(s, s);
            karte(g, mc, m, e, a, dt);
        } finally {
            p.popMatrix();
        }
    }

    private static void karte(GuiGraphicsExtractor g, Minecraft mc, OpponentInfoModule m, LivingEntity e, float a, float dt) {
        int akzent = m.color.get() | 0xFF000000;
        float w = BREITE, h = hoehe(m);
        Glatt.schatten(g, 0, 2, w, h, 9, 8, Glatt.alpha(0xFF000000, a * 0.45f));
        Glatt.rund(g, 0, 0, w, h, 9, Glatt.alpha(0xE8111218, a));
        Glatt.rahmen(g, 0, 0, w, h, 9, 1, Glatt.alpha(0x16FFFFFF, a));

        String name = e == null ? "Opponent" : e.getName().getString();
        float leben = e == null ? 16 : e.getHealth();
        float max = e == null ? 20 : Math.max(1, e.getMaxHealth());
        float abs = e == null ? 4 : e.getAbsorptionAmount();
        angezeigt = angezeigt < 0 ? leben : naeher(angezeigt, leben, 8f, dt);

        kopf(g, mc, e, 8, 8, 26, a);

        float tx = 42;
        String info = "";
        if (e != null && mc.player != null) {
            info = Math.round(e.distanceTo(mc.player)) + " m";
            if (e instanceof Player pl && mc.getConnection() != null) {
                var pi = mc.getConnection().getPlayerInfo(pl.getUUID());
                if (pi != null) info += "  ·  " + pi.getLatency() + " ms";
            }
        } else if (e == null) {
            info = "12 m  ·  45 ms";
        }
        float infoB = Glatt.breite(info, Schrift.NORMAL);
        Glatt.text(g, Glatt.kuerzen(name, (int) (w - tx - infoB - 14), Schrift.FETT), tx, 9, Glatt.alpha(0xFFF2F3F7, a), Schrift.FETT);
        Glatt.textRechts(g, info, w - 8, 9, Glatt.alpha(0xFF8C90A1, a), Schrift.NORMAL);

        float bx = tx, by = 23, bw = w - tx - 8, bh = 6;
        Glatt.rund(g, bx, by, bw, bh, 3, Glatt.alpha(0x22FFFFFF, a));
        float anteil = Math.max(0, Math.min(1, angezeigt / max));
        int farbe = anteil > 0.5f ? Glatt.mix(0xFFFACC15, 0xFF4ADE80, (anteil - 0.5f) * 2f) : Glatt.mix(0xFFF87171, 0xFFFACC15, anteil * 2f);
        if (anteil > 0) Glatt.rund(g, bx, by, Math.max(bh, bw * anteil), bh, 3, Glatt.alpha(farbe, a));
        if (abs > 0) {
            float aw = Math.min(bw, bw * abs / max);
            Glatt.rund(g, bx + bw - aw, by, aw, bh, 3, Glatt.alpha(0xCCFBBF24, a));
        }
        String lebenText = fmt(leben / 2f) + " ❤" + (abs > 0 ? "  +" + fmt(abs / 2f) : "");
        Glatt.text(g, lebenText, bx, by + 9, Glatt.alpha(0xFFCDD0DC, a), Schrift.NORMAL);

        float rechts = w - 8;
        if (m.lead.get() && mc.player != null) {
            float meins = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            float seins = leben + abs;
            String t = Math.abs(meins - seins) < 1f ? "EVEN" : (meins > seins ? "AHEAD" : "BEHIND");
            int f = t.equals("AHEAD") ? 0xFF4ADE80 : t.equals("BEHIND") ? 0xFFF87171 : 0xFFA4A8B8;
            float pw = Glatt.breite(t, Schrift.FETT) + 10;
            Glatt.rund(g, rechts - pw, by + 8, pw, 12, 6, Glatt.alpha((f & 0xFFFFFF) | 0x26000000, a));
            Glatt.textMitte(g, t, rechts - pw / 2f, by + 9.5f, Glatt.alpha(f, a), Schrift.FETT);
            rechts -= pw + 6;
        }
        if (m.pops.get()) {
            int n = e == null ? 2 : TotemPops.countFor(e.getName().getString());
            if (n > 0) {
                String t = n + (n == 1 ? " pop" : " pops");
                Glatt.textRechts(g, t, rechts, by + 9, Glatt.alpha(akzent, a), Schrift.FETT);
            }
        }

        if (m.armor.get()) {
            Glatt.linieW(g, 8, w - 8, 44, Glatt.alpha(0x10FFFFFF, a));
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                    EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
            float ix = 8;
            for (int i = 0; i < slots.length; i++) {
                if (i == 4) ix += 8;
                ItemStack st = e == null ? ItemStack.EMPTY : e.getItemBySlot(slots[i]);
                int sx = Math.round(ix), sy = 46;
                if (st.isEmpty()) {
                    Glatt.rund(g, sx + 2, sy + 2, 12, 12, 3, Glatt.alpha(0x0EFFFFFF, a));
                } else if (a > 0.6f) {
                    g.item(st, sx, sy);
                    g.itemDecorations(mc.font, st, sx, sy);
                }
                ix += 18;
            }
        }
    }

    private static void kopf(GuiGraphicsExtractor g, Minecraft mc, LivingEntity e, int x, int y, int gr, float a) {
        Glatt.rund(g, x - 1, y - 1, gr + 2, gr + 2, 5, Glatt.alpha(0x30FFFFFF, a));
        if (a < 0.6f) return;
        try {
            if (e instanceof Player pl && mc.getConnection() != null) {
                var pi = mc.getConnection().getPlayerInfo(pl.getUUID());
                if (pi != null) {
                    var tex = pi.getSkin().body().texturePath();
                    g.blit(tex, x, y, x + gr, y + gr, 0.125F, 0.25F, 0.125F, 0.25F);
                    g.blit(tex, x, y, x + gr, y + gr, 0.625F, 0.75F, 0.125F, 0.25F);
                    return;
                }
            }
            if (e == null && mc.player != null) {
                var tex = mc.player.getSkin().body().texturePath();
                g.blit(tex, x, y, x + gr, y + gr, 0.125F, 0.25F, 0.125F, 0.25F);
                g.blit(tex, x, y, x + gr, y + gr, 0.625F, 0.75F, 0.125F, 0.25F);
                return;
            }
            Glatt.symbol(g, com.vortex.client.gui.glatt.Symbole.Symbol.PERSON, x + gr * 0.2f, y + gr * 0.2f, gr * 0.6f, Glatt.alpha(0xFFA4A8B8, a));
        } catch (Throwable ignored) {
        }
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static float naeher(float ist, float ziel, float tempo, float dt) {
        float f = 1f - (float) Math.exp(-tempo * dt);
        float neu = ist + (ziel - ist) * f;
        return Math.abs(ziel - neu) < 0.002f ? ziel : neu;
    }
}
