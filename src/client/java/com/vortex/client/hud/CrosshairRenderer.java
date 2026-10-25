package com.vortex.client.hud;

import com.vortex.client.module.modules.CrosshairModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet das Fadenkreuz (Modul Crosshair).
 *
 * Alles aus gefuellten Rechtecken, keine Textur -- jede Groesse und Farbe
 * geht ohne Bilddatei. ZWEI DURCHGAENGE: erst alle Umrandungen, dann alle
 * Flaechen. Frueher bekam jedes Stueck seine eigene Umrandung direkt vor sich
 * -- beim Kreis ueberdeckte dann der Rand des naechsten Punkts den vorigen,
 * und der Kreis sah ausgefranst aus.
 */
public final class CrosshairRenderer {

    private CrosshairRenderer() {}

    /** Gesammelte Rechtecke eines Bildes: x, y, b, h. */
    private static final List<int[]> TEILE = new ArrayList<>();

    private static float gapGlatt = 0f;
    private static long letzteZeit = 0;
    private static float letzteStaerke = 1f;
    private static long vollSeit = 0;

    public static void draw(GuiGraphicsExtractor ctx, Minecraft mc, CrosshairModule m) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        long jetzt = System.currentTimeMillis();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1000f);
        letzteZeit = jetzt;

        int farbe = m.color.get();
        if (m.rainbow.get()) {
            float ton = (jetzt % 4000L) / 4000f;
            farbe = (farbe & 0xFF000000) | (hsv(ton) & 0x00FFFFFF);
        }
        int ziel = m.targetMode.getIndex();
        if (ziel > 0 && mc.hitResult instanceof EntityHitResult ehr) {
            if (ziel == 1 || ehr.getEntity() instanceof Player) farbe = m.targetColor.get();
        }

        float staerke = p.getAttackStrengthScale(0f);
        if (staerke >= 1f && letzteStaerke < 1f) vollSeit = jetzt;
        letzteStaerke = staerke;

        float extra = 0f;
        if (m.dynamicGap.get()) {
            Vec3 v = p.getDeltaMovement();
            double tempo = Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
            extra += (float) Math.min(5.0, tempo * 0.7);
            if (!p.onGround()) extra += 3f;
            extra += (1f - staerke) * 4f;
        }
        gapGlatt += (extra - gapGlatt) * (1f - (float) Math.exp(-14f * dt));
        int gap = m.gap.getInt() + Math.round(gapGlatt);
        int len = m.size.getInt();
        int th = m.thickness.getInt();
        int dot = m.dotSize.getInt();

        form(ctx, m, cx, cy, gap, len, th, dot, farbe);

        if (m.hitMarker.get()) {
            long alter = CombatTracker.trefferAlter();
            if (alter < 350) hitMarker(ctx, m, cx, cy, gap, len, 1f - alter / 350f);
        }

        int modus = m.indicator.getIndex();

        if (modus > 0) {
            boolean laedt = staerke < 1f;
            long seitVoll = jetzt - vollSeit;
            boolean blitz = m.readyFlash.get() && !laedt && seitVoll < 200;
            boolean zielt = mc.hitResult instanceof EntityHitResult;
            boolean zeigen = laedt || blitz || m.indicatorAlways.get();
            if (m.indicatorOnlyTarget.get() && !zielt) zeigen = false;
            if (zeigen) {
                int ausdehnung = aussenmass(m.style.getIndex(), gap, len, th, dot);
                anzeige(ctx, mc, m, modus, cx, cy, ausdehnung, staerke, blitz ? 1f - seitVoll / 200f : 0f);
            }
        }
    }

    /** Die Form selbst (ohne Anzeige und Hitmarker) um cx/cy zeichnen. */
    private static void form(GuiGraphicsExtractor ctx, CrosshairModule m, int cx, int cy, int gap, int len, int th, int dot, int farbe) {
        TEILE.clear();
        switch (m.style.getIndex()) {
            case 0: kreuz(cx, cy, gap, len, th, true); break;
            case 1: kreuz(cx, cy, gap, len, th, true); punkt(cx, cy, dot); break;
            case 2: punkt(cx, cy, Math.max(dot, th)); break;
            case 3: kreis(cx, cy, gap + len / 2 + 1, th); break;
            case 4: kreis(cx, cy, gap + len / 2 + 1, th); punkt(cx, cy, dot); break;
            case 5: kreuz(cx, cy, gap, len, th, false); break;
            case 6: diagonal(cx, cy, gap, len, th); break;
            case 7: quadrat(cx, cy, gap + len / 2 + 1, th); break;
            case 8: quadrat(cx, cy, gap + len / 2 + 1, th); punkt(cx, cy, dot); break;
            case 9: eigenes(ctx, m, cx, cy, farbe); return;
            default: kreuz(cx, cy, gap, len, th, true);
        }
        zeichne(ctx, farbe, m.outline.get(), m.outlineColor.get());
    }

    /**
     * Eigenes Fadenkreuz aus dem Editor: Pixel 1 = Fadenkreuz-Farbe, Pixel 2 = Umrandungs-
     * Farbe (selbst gemalter Rand). Die automatische Umrandung gilt nur fuer Pixel 1.
     */
    private static void eigenes(GuiGraphicsExtractor ctx, CrosshairModule m, int cx, int cy, int farbe) {
        byte[] px = EigenesFadenkreuz.pixel();
        int n = EigenesFadenkreuz.GROESSE, mitte = n / 2;
        int s = Math.max(1, m.customScale.getInt());
        int x0 = cx - mitte * s - (s - 1) / 2, y0 = cy - mitte * s - (s - 1) / 2;
        TEILE.clear();
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            if (px[y * n + x] == 1) rechteck(x0 + x * s, y0 + y * s, s, s);
        }
        if (m.outline.get() && (m.outlineColor.get() >>> 24) != 0) {
            for (int[] t : TEILE) ctx.fill(t[0] - 1, t[1] - 1, t[0] + t[2] + 1, t[1] + t[3] + 1, m.outlineColor.get());
        }
        int rand = (m.outlineColor.get() >>> 24) != 0 ? m.outlineColor.get() : 0xFF000000;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
            if (px[y * n + x] == 2) ctx.fill(x0 + x * s, y0 + y * s, x0 + x * s + s, y0 + y * s + s, rand);
        }
        for (int[] t : TEILE) ctx.fill(t[0], t[1], t[0] + t[2], t[1] + t[3], farbe);
    }

    private static void hitMarker(GuiGraphicsExtractor ctx, CrosshairModule m, int cx, int cy, int gap, int len, float a) {
        int hm = m.hitMarkerColor.get();
        int al = (int) (((hm >>> 24) & 0xFF) * a);
        TEILE.clear();
        int innen = gap + 3, aussen = gap + 3 + Math.max(3, len / 2 + 1);
        for (int i = innen; i <= aussen; i++) {
            TEILE.add(new int[]{cx + i, cy + i, 1, 1});
            TEILE.add(new int[]{cx - i - 1, cy + i, 1, 1});
            TEILE.add(new int[]{cx + i, cy - i - 1, 1, 1});
            TEILE.add(new int[]{cx - i - 1, cy - i - 1, 1, 1});
        }
        zeichne(ctx, (al << 24) | (hm & 0x00FFFFFF), m.outline.get(),
                ((int) (((m.outlineColor.get() >>> 24) & 0xFF) * a) << 24) | (m.outlineColor.get() & 0x00FFFFFF));
    }

    /** Fuer den Editor: das Fadenkreuz um cx/cy, ohne Bewegung; treffer 0..1 zeigt den Hitmarker. */
    public static void vorschau(GuiGraphicsExtractor ctx, CrosshairModule m, int cx, int cy, float treffer) {
        int farbe = m.color.get();
        if (m.rainbow.get()) {
            float ton = (System.currentTimeMillis() % 4000L) / 4000f;
            farbe = (farbe & 0xFF000000) | (hsv(ton) & 0x00FFFFFF);
        }
        int gap = m.gap.getInt(), len = m.size.getInt();
        form(ctx, m, cx, cy, gap, len, m.thickness.getInt(), m.dotSize.getInt(), farbe);
        if (treffer > 0f && m.hitMarker.get()) hitMarker(ctx, m, cx, cy, gap, len, treffer);
    }

    /** Wie weit das Fadenkreuz vom Mittelpunkt reicht -- darunter kommt die Anzeige. */
    private static int aussenmass(int stil, int gap, int len, int th, int dot) {
        switch (stil) {
            case 2: return Math.max(dot, th) + 1;
            case 9: return EigenesFadenkreuz.GROESSE / 2 + 1;
            case 3: case 4: case 7: case 8: return gap + len / 2 + 1 + th;
            default: return gap + len;
        }
    }

    private static void rechteck(int x, int y, int b, int h) {
        if (b > 0 && h > 0) TEILE.add(new int[]{x, y, b, h});
    }

    /** Kreuz; oben = false gibt die T-Form (ohne Arm nach oben). */
    private static void kreuz(int cx, int cy, int gap, int len, int th, boolean oben) {
        int h = th / 2;
        if (oben) rechteck(cx - h, cy - gap - len, th, len);
        rechteck(cx - h, cy + gap + (th % 2), th, len);
        rechteck(cx - gap - len, cy - h, len, th);
        rechteck(cx + gap + (th % 2), cy - h, len, th);
    }

    private static void punkt(int cx, int cy, int r) {
        int s = r * 2 - 1;
        rechteck(cx - (r - 1), cy - (r - 1), s, s);
    }

    private static void diagonal(int cx, int cy, int gap, int len, int th) {
        for (int i = gap + 1; i <= gap + len; i++) {
            rechteck(cx + i, cy + i, th, th);
            rechteck(cx - i - th + 1, cy + i, th, th);
            rechteck(cx + i, cy - i - th + 1, th, th);
            rechteck(cx - i - th + 1, cy - i - th + 1, th, th);
        }
    }

    private static void quadrat(int cx, int cy, int r, int th) {
        rechteck(cx - r, cy - r, 2 * r + 1, th);
        rechteck(cx - r, cy + r - th + 1, 2 * r + 1, th);
        rechteck(cx - r, cy - r + th, th, 2 * r + 1 - 2 * th);
        rechteck(cx + r - th + 1, cy - r + th, th, 2 * r + 1 - 2 * th);
    }

    /** Kreis aus Punkten; doppelte Punkte werden nur einmal gezeichnet. */
    private static void kreis(int cx, int cy, int r, int th) {
        java.util.Set<Long> schon = new java.util.HashSet<>();
        int schritte = Math.max(24, r * 8);
        for (int i = 0; i < schritte; i++) {
            double w = Math.PI * 2 * i / schritte;
            int px = cx + (int) Math.round(Math.cos(w) * r) - th / 2;
            int py = cy + (int) Math.round(Math.sin(w) * r) - th / 2;
            if (schon.add(((long) px << 32) ^ (py & 0xFFFFFFFFL))) rechteck(px, py, th, th);
        }
    }

    /** Erst alle Umrandungen, dann alle Flaechen. */
    private static void zeichne(GuiGraphicsExtractor ctx, int farbe, boolean rand, int randFarbe) {
        if (rand && (randFarbe >>> 24) != 0) {
            for (int[] t : TEILE) {
                ctx.fill(t[0] - 1, t[1] - 1, t[0] + t[2] + 1, t[1] + t[3] + 1, randFarbe);
            }
        }
        for (int[] t : TEILE) {
            ctx.fill(t[0], t[1], t[0] + t[2], t[1] + t[3], farbe);
        }
    }

    private static void anzeige(GuiGraphicsExtractor ctx, Minecraft mc, CrosshairModule m, int modus,
                                int cx, int cy, int ausdehnung, float staerke, float blitz) {
        float voll = Math.min(1f, Math.max(0f, staerke));
        com.vortex.client.core.setting.ColorSetting fs = m.indicatorColor;
        boolean nachLadung = m.colorByCharge.get();
        int einfarbig = nachLadung ? mischen(m.chargingColor.get(), fs.get(), voll) : fs.get();
        if (blitz > 0f) einfarbig = mischen(einfarbig, 0xFFFFFFFF, blitz);
        boolean verlauf = !nachLadung && blitz <= 0f && fs.isGradient();

        int lage = m.indicatorPosition.getIndex();
        int abstand = m.indicatorDistance.getInt();
        int laenge = m.indicatorLength.getInt();
        int dicke = m.indicatorThickness.getInt();
        int spur = m.indicatorTrack.get();
        int grund = m.indicatorBackground.get();

        switch (modus) {
            case 1: {
                boolean senkrecht = lage >= 2;
                int b = senkrecht ? dicke : laenge;
                int h = senkrecht ? laenge : dicke;
                int x, y;
                switch (lage) {
                    case 1:  x = cx - b / 2; y = cy - ausdehnung - abstand - h; break;
                    case 2:  x = cx - ausdehnung - abstand - b; y = cy - h / 2; break;
                    case 3:  x = cx + ausdehnung + abstand + 1; y = cy - h / 2; break;
                    default: x = cx - b / 2; y = cy + ausdehnung + abstand; break;
                }
                if ((grund >>> 24) != 0) ctx.fill(x - 1, y - 1, x + b + 1, y + h + 1, grund);
                if ((spur >>> 24) != 0) ctx.fill(x, y, x + b, y + h, spur);
                if (!senkrecht) {
                    int gefuellt = Math.round(b * voll);
                    for (int i = 0; i < gefuellt; i += 2) {
                        int c = verlauf ? fs.at((i + 1f) / b) : einfarbig;
                        ctx.fill(x + i, y, x + Math.min(i + 2, gefuellt), y + h, c);
                    }
                } else {
                    int gefuellt = Math.round(h * voll);
                    for (int i = 0; i < gefuellt; i += 2) {
                        int c = verlauf ? fs.at((i + 1f) / h) : einfarbig;
                        int unten = y + h - i;
                        ctx.fill(x, unten - Math.min(2, gefuellt - i), x + b, unten, c);
                    }
                }
                break;
            }
            case 2: {
                int r = ausdehnung + Math.max(1, abstand - 1);
                int schritte = Math.max(32, r * 8);
                int bis = Math.round(schritte * voll);
                int halb = dicke / 2;
                if ((spur >>> 24) != 0) {
                    TEILE.clear();
                    java.util.Set<Long> schon = new java.util.HashSet<>();
                    for (int i = bis; i < schritte; i++) {
                        double w = -Math.PI / 2 + Math.PI * 2 * i / schritte;
                        int px = cx + (int) Math.round(Math.cos(w) * r) - halb;
                        int py = cy + (int) Math.round(Math.sin(w) * r) - halb;
                        if (schon.add(((long) px << 32) ^ (py & 0xFFFFFFFFL))) rechteck(px, py, dicke, dicke);
                    }
                    zeichne(ctx, spur, false, 0);
                }
                java.util.Set<Long> schon = new java.util.HashSet<>();
                for (int i = 0; i < bis; i++) {
                    double w = -Math.PI / 2 + Math.PI * 2 * i / schritte;
                    int px = cx + (int) Math.round(Math.cos(w) * r) - halb;
                    int py = cy + (int) Math.round(Math.sin(w) * r) - halb;
                    if (!schon.add(((long) px << 32) ^ (py & 0xFFFFFFFFL))) continue;
                    int c = verlauf ? fs.at(i / (float) schritte) : einfarbig;
                    if ((grund >>> 24) != 0) ctx.fill(px - 1, py - 1, px + dicke + 1, py + dicke + 1, grund);
                    ctx.fill(px, py, px + dicke, py + dicke, c);
                }
                break;
            }
            case 3: {
                String t = String.format(Locale.ROOT, "%d%%", Math.round(voll * 100));
                float sk = (float) m.indicatorTextScale.get();
                int tw = Math.round(mc.font.width(t) * sk);
                int th = Math.round(8 * sk);
                int x, y;
                switch (lage) {
                    case 1:  x = cx - tw / 2; y = cy - ausdehnung - abstand - th; break;
                    case 2:  x = cx - ausdehnung - abstand - tw; y = cy - th / 2; break;
                    case 3:  x = cx + ausdehnung + abstand + 1; y = cy - th / 2; break;
                    default: x = cx - tw / 2; y = cy + ausdehnung + abstand; break;
                }
                var p = ctx.pose();
                p.pushMatrix();
                p.translate(x, y);
                p.scale(sk, sk);
                if ((grund >>> 24) != 0) {
                    ctx.fill(-2, -2, mc.font.width(t) + 1, 9, grund);
                }
                ctx.text(mc.font, Component.literal(t), 0, 0, verlauf ? fs.at(0.5f) : einfarbig, true);
                p.popMatrix();
                break;
            }
            default: break;
        }
    }

    private static int mischen(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Farbton 0..1 -> kraeftige RGB-Farbe. */
    private static int hsv(float h) {
        float r = Math.abs(h * 6f - 3f) - 1f;
        float g = 2f - Math.abs(h * 6f - 2f);
        float b = 2f - Math.abs(h * 6f - 4f);
        r = Math.max(0f, Math.min(1f, r));
        g = Math.max(0f, Math.min(1f, g));
        b = Math.max(0f, Math.min(1f, b));
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
