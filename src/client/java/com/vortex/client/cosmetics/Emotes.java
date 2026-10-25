package com.vortex.client.cosmetics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emotes: kurze Animationen des Spielermodells (Winken, Tanzen ...).
 *
 * Die Pose wird nach Minecrafts eigener Animation gesetzt (EmoteMixin am Ende
 * von PlayerModel.setupAnim) und am Anfang/Ende weich ein- und ausgeblendet.
 * Wer losgeht, beendet sein Emote. Fuer dich selbst springt die Kamera
 * waehrenddessen in die Ansicht von vorne und danach zurueck.
 *
 * Seit 4.97 knicken Ellbogen und Knie (BiegeEbene): Klatschen, Achselzucken, Daumen hoch,
 * Hinsetzen und Tanzen sehen dadurch natuerlich aus statt mit steifen Armen.
 *
 * Seit 4.68: 32 Emotes auf vier Seiten (Greet, Dance, Fun, Taunt), alle neu animiert --
 * mit Springen, Wippen und Verbeugen (der ganze Koerper bewegt sich, nicht nur die Arme),
 * schnellen "Schnapp"-Bewegungen und weichen Uebergaengen. Die Ids der alten Emotes
 * bleiben, damit aeltere Clients sie weiter verstehen.
 *
 * Andere Vortex-Spieler sehen es ueber den Freunde-Server (CosmeticsSync
 * schickt "emote.play", Social reicht das Ereignis "emote" hierher).
 */
public final class Emotes {
    private Emotes() {}

    @FunctionalInterface
    interface Pose { void setzen(PlayerModel m, float t, float b); }

    public record Emote(String id, String name, String seite, float dauer, Pose pose) {}

    private record Laufend(Emote emote, long seit) {}

    /** Seiten im Emote-Rad, je 8 Emotes. */
    public static final String[] SEITEN = { "Greet", "Dance", "Fun", "Taunt" };

    private static final Map<String, Emote> ALLE = new LinkedHashMap<>();
    private static final Map<UUID, Laufend> AKTIV = new ConcurrentHashMap<>();
    private static CameraType kameraVorher;

    /** Renderzustand mit dieser Id = Vorschau-Figur im Emote-Rad (spielt {@link #vorschau}). */
    public static final int VORSCHAU_ID = Integer.MIN_VALUE + 7;
    private static volatile Laufend vorschau;

    public static Map<String, Emote> alle() { return ALLE; }

    /** Die Emotes einer Seite (0..3), in Reihenfolge. */
    public static List<Emote> seite(int nr) {
        List<Emote> l = new ArrayList<>();
        String s = SEITEN[Math.floorMod(nr, SEITEN.length)];
        for (Emote e : ALLE.values()) if (e.seite().equals(s)) l.add(e);
        return l;
    }

    private static String seiteJetzt = SEITEN[0];

    private static void neu(String id, String name, float dauer, Pose p) { ALLE.put(id, new Emote(id, name, seiteJetzt, dauer, p)); }

    /**
     * Wert weich Richtung Ziel (b = 0..1 Einblenden). Achtung bei erhobenen Armen:
     * Minecraft dreht erst um X, dann um Z -- zeigt der Arm nach oben, kehrt sich
     * die Wirkung von zRot um (positiv = zum Kopf hin). Arme nach vorne (xRot -1.57):
     * rechter Arm mit yRot negativ = zur Mitte, linker positiv.
     */
    private static void zu(ModelPart part, float b, float x, float y, float z) {
        part.xRot += (x - part.xRot) * b;
        part.yRot += (y - part.yRot) * b;
        part.zRot += (z - part.zRot) * b;
    }

    /* ------------------------------------------------------------------ Knicke (seit 4.97) */

    /** Knick je Glied (BiegeEbene.R_ARM ...): Drehvektor des unteren Teils um das Gelenk. */
    private static final float[][] KNICK = new float[4][3];
    private static float knickB;
    private static final java.util.Map<AvatarRenderState, float[][]> BIEGUNG = new java.util.WeakHashMap<>();
    private static final java.util.Map<PlayerModel, boolean[]> VERSTECKT = new java.util.WeakHashMap<>();

    /**
     * Ellbogen knicken: vor = Unterarm nach vorne (Bogenmass), seit = zur Koerpermitte hin
     * (negativ = nach aussen). Wird mit dem Einblenden des Emotes skaliert.
     */
    private static void ellbogen(boolean rechts, float vor, float seit) {
        float[] k = KNICK[rechts ? BiegeEbene.R_ARM : BiegeEbene.L_ARM];
        k[0] = -vor * knickB;
        k[1] = 0f;
        k[2] = (rechts ? -seit : seit) * knickB;
    }

    /** Knie beugen (Unterschenkel nach hinten, Bogenmass). */
    private static void knie(boolean rechts, float beuge) {
        float[] k = KNICK[rechts ? BiegeEbene.R_BEIN : BiegeEbene.L_BEIN];
        k[0] = beuge * knickB;
        k[1] = 0f;
        k[2] = 0f;
    }

    /** Knicke fuer diesen Renderzustand (fuer BiegeEbene), null = keine. */
    public static float[][] biegung(AvatarRenderState s) {
        return s == null ? null : BIEGUNG.get(s);
    }

    public static boolean geknickt(float[] k) {
        return k != null && k[0] * k[0] + k[1] * k[1] + k[2] * k[2] > 0.0004f;
    }

    /** Aus EmoteMixin (translateToHand): Gegenstand in der Hand mit dem Unterarm mitdrehen. */
    public static void handKnick(AvatarRenderState s, boolean rechts, com.mojang.blaze3d.vertex.PoseStack pose) {
        float[][] b = biegung(s);
        if (b == null) return;
        float[] k = b[rechts ? BiegeEbene.R_ARM : BiegeEbene.L_ARM];
        if (!geknickt(k)) return;
        float a = (float) Math.sqrt(k[0] * k[0] + k[1] * k[1] + k[2] * k[2]);
        float y = BiegeEbene.gelenkY(BiegeEbene.R_ARM) / 16f;
        pose.translate(0f, y, 0f);
        pose.mulPose(new org.joml.Quaternionf().rotationAxis(a, k[0] / a, k[1] / a, k[2] / a));
        pose.translate(0f, -y, 0f);
    }

    private static ModelPart glied(PlayerModel m, int g) {
        return g == BiegeEbene.R_ARM ? m.rightArm : g == BiegeEbene.L_ARM ? m.leftArm : g == BiegeEbene.R_BEIN ? m.rightLeg : m.leftLeg;
    }

    /** Glieder, die wir fuer den letzten Zustand versteckt haben, wieder zeigen. */
    private static void zeigen(PlayerModel m) {
        boolean[] v = VERSTECKT.get(m);
        if (v == null) return;
        for (int g = 0; g < 4; g++) if (v[g]) { glied(m, g).visible = true; v[g] = false; }
    }

    /** Ganzen Koerper anheben (Pixel, positiv = nach oben) -- Springen, Wippen. */
    private static void hoch(PlayerModel m, float b, float px) {
        float d = px * b;
        m.head.y -= d; m.body.y -= d; m.rightArm.y -= d; m.leftArm.y -= d; m.rightLeg.y -= d; m.leftLeg.y -= d;
    }

    /**
     * Vorbeugen wie beim Schleichen (k = 1 ist Minecrafts Schleich-Haltung, mehr = tiefer):
     * Oberkoerper kippt nach vorne, Kopf und Arme gehen mit nach unten, die Beine nach hinten.
     */
    private static void buecken(PlayerModel m, float b, float k) {
        float f = k * b;
        m.body.xRot += (0.5f * k - m.body.xRot) * b;
        m.body.y += 3.2f * f;
        m.head.y += 4.2f * f;
        m.rightArm.y += 3.2f * f;
        m.leftArm.y += 3.2f * f;
        m.rightLeg.z += 4f * f;
        m.leftLeg.z += 4f * f;
        m.rightLeg.y += 0.2f * f;
        m.leftLeg.y += 0.2f * f;
    }

    private static float sin(float v) { return (float) Math.sin(v); }
    private static float cos(float v) { return (float) Math.cos(v); }

    private static float glatt(float v) {
        v = Math.max(0f, Math.min(1f, v));
        return v * v * (3f - 2f * v);
    }

    /** 0 vor start, dann weich auf 1 in dauer Sekunden. */
    private static float ein(float t, float start, float dauer) { return glatt((t - start) / dauer); }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    /** Sprungkurve: 0 -> 1 -> 0 ueber eine Periode p (Sekunden). */
    private static float sprung(float t, float p) {
        float u = (t % p) / p;
        return 4f * u * (1f - u);
    }

    static {
        seiteJetzt = "Greet";
        neu("wave", "Wave", 2.8f, (m, t, b) -> {
            float s = sin(t * 10f), r = ein(t, 0f, 0.3f);
            zu(m.rightArm, b * r, -2.5f, 0f, -0.45f + s * 0.3f);
            zu(m.leftArm, b, 0.05f, 0f, -0.08f);
            ellbogen(true, r * (0.55f + 0.35f * s), r * 0.25f * s);
            zu(m.head, b, -0.1f, -0.25f, 0.06f);
            zu(m.body, b, 0f, -0.08f, s * 0.025f);
        });
        neu("salute", "Salute", 2.6f, (m, t, b) -> {
            float r = ein(t, 0f, 0.22f);
            zu(m.rightArm, b * r, -2.6f, -0.45f, 0.65f);
            zu(m.leftArm, b, 0f, 0f, -0.04f);
            ellbogen(true, r * 0.35f, 0f);
            zu(m.head, b, -0.12f, 0f, 0f);
            zu(m.rightLeg, b, 0f, 0f, 0f);
            zu(m.leftLeg, b, 0f, 0f, 0f);
            hoch(m, b, 0.3f * r);
        });
        neu("bow", "Bow", 3.0f, (m, t, b) -> {
            float k = ein(t, 0.25f, 0.5f) * (1f - ein(t, 2.1f, 0.5f));
            buecken(m, b, 1.25f * k);
            zu(m.head, b, 0.35f * k, 0f, 0f);
            zu(m.rightArm, b, -0.45f * k, -0.35f * k, 0f);
            zu(m.leftArm, b, 0.35f * k, 0.3f * k, 0f);
            ellbogen(true, 1.45f * k, 0.6f * k);
            ellbogen(false, -0.9f * k, 0.9f * k);
        });
        neu("clap", "Clap", 3.0f, (m, t, b) -> {
            float c = (float) Math.pow(0.5f + 0.5f * sin(t * 14f), 3);
            zu(m.rightArm, b, -0.9f, -(0.12f + 0.2f * c), 0f);
            zu(m.leftArm, b, -0.9f, 0.12f + 0.2f * c, 0f);
            ellbogen(true, 0.75f, 0.25f + 0.3f * c);
            ellbogen(false, 0.75f, 0.25f + 0.3f * c);
            zu(m.head, b, 0.12f * c - 0.05f, 0f, 0f);
            hoch(m, b, 0.35f * c);
        });
        neu("cheer", "Cheer", 3.0f, (m, t, b) -> {
            float j = Math.abs(sin(t * 7f));
            hoch(m, b, j * 2.6f);
            zu(m.rightArm, b, -2.95f, 0f, -0.25f - 0.25f * j);
            zu(m.leftArm, b, -2.95f, 0f, 0.25f + 0.25f * j);
            ellbogen(true, 0f, 0.35f * (1f - j));
            ellbogen(false, 0f, 0.35f * (1f - j));
            knie(true, 0.7f * (1f - j));
            knie(false, 0.7f * (1f - j));
            zu(m.head, b, -0.35f, 0f, 0f);
            zu(m.rightLeg, b, 0f, 0f, 0.1f * (1f - j));
            zu(m.leftLeg, b, 0f, 0f, -0.1f * (1f - j));
        });
        neu("thumbs_up", "Thumbs Up", 2.4f, (m, t, b) -> {
            float r = ein(t, 0f, 0.25f), w = sin(t * 6f) * ein(t, 0.4f, 0.3f);
            zu(m.rightArm, b * r, -0.55f + w * 0.06f, -0.2f, 0.1f);
            zu(m.leftArm, b, -0.15f, 0f, -0.35f);
            ellbogen(true, r * 1.25f, 0.1f * r);
            ellbogen(false, 0f, -0.25f);
            zu(m.head, b, 0.08f * w, -0.1f, 0.12f);
        });
        neu("point", "Point", 2.4f, (m, t, b) -> {
            float r = ein(t, 0f, 0.2f);
            zu(m.rightArm, b * r, -1.6f + sin(t * 3f) * 0.03f, -0.08f, 0f);
            zu(m.leftArm, b, -0.25f, 0f, -0.55f);
            zu(m.head, b, 0f, -0.08f, 0f);
            zu(m.body, b, 0f, -0.12f, 0f);
        });
        neu("shrug", "Shrug", 2.0f, (m, t, b) -> {
            float s = ein(t, 0.08f, 0.25f) * (1f - ein(t, 1.3f, 0.4f));
            zu(m.rightArm, b, -0.15f * s, 0f, 0.35f * s);
            zu(m.leftArm, b, -0.15f * s, 0f, -0.35f * s);
            ellbogen(true, 1.35f * s, -0.45f * s);
            ellbogen(false, 1.35f * s, -0.45f * s);
            m.rightArm.y -= 1.2f * s * b;
            m.leftArm.y -= 1.2f * s * b;
            zu(m.head, b, -0.05f * s, 0f, 0.22f * s);
        });

        seiteJetzt = "Dance";
        neu("dance", "Groove", 8.0f, (m, t, b) -> {
            float s = sin(t * 6.9f), w = Math.abs(sin(t * 6.9f));
            hoch(m, b, w * 1.2f);
            zu(m.rightArm, b, -0.9f + s * 0.6f, 0f, 0.25f);
            zu(m.leftArm, b, -0.9f - s * 0.6f, 0f, -0.25f);
            ellbogen(true, 1.1f - 0.4f * s, 0.2f);
            ellbogen(false, 1.1f + 0.4f * s, 0.2f);
            zu(m.rightLeg, b, s * 0.3f - 0.15f * w, 0f, 0.05f);
            zu(m.leftLeg, b, -s * 0.3f - 0.15f * w, 0f, -0.05f);
            knie(true, 0.45f * w);
            knie(false, 0.45f * w);
            zu(m.body, b, 0f, s * 0.22f, 0f);
            zu(m.head, b, w * 0.2f, s * 0.25f, 0f);
        });
        neu("disco", "Disco", 6.0f, (m, t, b) -> {
            float u = 0.5f + 0.5f * sin(t * 4.2f);
            zu(m.rightArm, b, mix(-0.6f, -2.85f, u), mix(-0.7f, 0f, u), mix(0f, -0.45f, u));
            zu(m.leftArm, b, -0.1f, 0.2f, -0.55f);
            ellbogen(false, 0.3f, 1.7f);
            ellbogen(true, 0.35f * (1f - u), 0f);
            knie(true, 0.55f * u);
            zu(m.body, b, 0f, 0f, sin(t * 4.2f) * 0.06f);
            zu(m.rightLeg, b, -0.15f * u, 0f, 0.12f);
            zu(m.leftLeg, b, 0f, 0f, -0.05f);
            zu(m.head, b, -0.3f * u, -0.3f + 0.3f * u, 0f);
            hoch(m, b, Math.abs(sin(t * 8.4f)) * 0.6f);
        });
        neu("floss", "Floss", 6.0f, (m, t, b) -> {
            float f = sin(t * 9f), g = cos(t * 9f);
            zu(m.rightArm, b, 0.35f * g, 0f, f * 0.55f);
            zu(m.leftArm, b, -0.35f * g, 0f, f * 0.55f);
            zu(m.body, b, 0f, -f * 0.15f, 0f);
            zu(m.rightLeg, b, 0f, 0f, -f * 0.08f);
            zu(m.leftLeg, b, 0f, 0f, -f * 0.08f);
            zu(m.head, b, 0f, -f * 0.12f, 0f);
            hoch(m, b, Math.abs(f) * 0.4f);
        });
        neu("headbang", "Headbang", 5.0f, (m, t, b) -> {
            float nick = 0.5f + 0.5f * sin(t * 12f);
            buecken(m, b, 0.25f + 0.15f * nick);
            zu(m.head, b, -0.25f + 0.75f * nick, 0f, 0f);
            zu(m.rightArm, b, -0.9f, -0.35f, -0.2f + 0.25f * sin(t * 24f));
            zu(m.leftArm, b, -0.9f, 0.55f, 0.2f);
            ellbogen(false, 0.9f, 0.6f);
            ellbogen(true, 0.4f, 0f);
            knie(true, 0.3f);
            knie(false, 0.3f);
        });
        neu("robot", "Robot", 6.0f, (m, t, b) -> {
            float[][] p = {
                    { -1.57f, 0f, 0f, 0f, 0f, 0f, 0f },
                    { -1.57f, 0f, 0f, -1.57f, 0f, 0f, 0.6f },
                    { 0f, 0f, 1.57f, -1.57f, 0f, 0f, 0f },
                    { -3.0f, 0f, 0f, -1.57f, 0f, 0f, -0.6f },
                    { -1.57f, 0f, 0f, 0f, 0f, -1.57f, 0f },
                    { -1.57f, 0.5f, 0f, -1.57f, -0.5f, 0f, 0f } };
            float f = t * 2.2f;
            int i = (int) f % p.length, v = (i + p.length - 1) % p.length;
            float k = glatt((f - (int) f) / 0.18f);
            float[] a = p[v], c = p[i];
            zu(m.rightArm, b, mix(a[0], c[0], k), mix(a[1], c[1], k), mix(a[2], c[2], k));
            zu(m.leftArm, b, mix(a[3], c[3], k), mix(a[4], c[4], k), mix(a[5], c[5], k));
            zu(m.head, b, 0f, mix(a[6], c[6], k), 0f);
            ellbogen(true, 0f, i % 2 == 0 ? 0f : 1.57f * k);
            ellbogen(false, i % 3 == 1 ? 1.57f * k : 0f, 0f);
            zu(m.rightLeg, b, 0f, 0f, 0f);
            zu(m.leftLeg, b, 0f, 0f, 0f);
            zu(m.body, b, 0f, 0f, 0f);
        });
        neu("sway", "Sway", 7.0f, (m, t, b) -> {
            float s = sin(t * 2.2f);
            zu(m.body, b, 0f, 0f, s * 0.06f);
            zu(m.head, b, 0.05f, s * 0.2f, -s * 0.12f);
            zu(m.rightArm, b, -2.6f, 0f, -0.35f + s * 0.25f);
            zu(m.leftArm, b, -2.6f, 0f, 0.35f + s * 0.25f);
            ellbogen(true, 0.5f + 0.2f * s, 0.2f);
            ellbogen(false, 0.5f - 0.2f * s, 0.2f);
            zu(m.rightLeg, b, 0f, 0f, 0.04f + s * 0.04f);
            zu(m.leftLeg, b, 0f, 0f, -0.04f + s * 0.04f);
            hoch(m, b, (0.5f + 0.5f * sin(t * 4.4f)) * 0.5f);
        });
        neu("hype", "Hype", 4.0f, (m, t, b) -> {
            float h = sprung(t, 0.55f), u = (t % 0.55f) / 0.55f;
            hoch(m, b, h * 3.5f);
            zu(m.rightArm, b, -3.0f + 0.5f * cos(u * 6.283f), 0f, -0.2f);
            zu(m.leftArm, b, 0.4f * sin(u * 6.283f), 0f, -0.2f);
            zu(m.rightLeg, b, -0.4f * h, 0f, 0.05f);
            zu(m.leftLeg, b, -0.4f * h, 0f, -0.05f);
            knie(true, 1.1f * h);
            knie(false, 1.1f * h);
            ellbogen(false, 0.9f, 0f);
            zu(m.head, b, -0.25f, 0f, 0f);
        });
        neu("shuffle", "Shuffle", 6.0f, (m, t, b) -> {
            float st = sin(t * 10f);
            zu(m.rightLeg, b, -0.55f * Math.max(0f, st), 0f, 0.15f * Math.max(0f, st));
            zu(m.leftLeg, b, -0.55f * Math.max(0f, -st), 0f, -0.15f * Math.max(0f, -st));
            hoch(m, b, Math.abs(st) * 1.0f);
            zu(m.rightArm, b, -0.7f + st * 0.4f, 0f, 0.25f);
            zu(m.leftArm, b, -0.7f - st * 0.4f, 0f, -0.25f);
            ellbogen(true, 1.0f, 0.15f);
            ellbogen(false, 1.0f, 0.15f);
            knie(true, 0.6f * Math.max(0f, st));
            knie(false, 0.6f * Math.max(0f, -st));
            zu(m.body, b, 0f, st * 0.15f, 0f);
            zu(m.head, b, 0.1f, -st * 0.1f, 0f);
        });

        seiteJetzt = "Fun";
        neu("dab", "Dab", 2.4f, (m, t, b) -> {
            float r = ein(t, 0f, 0.16f);
            zu(m.head, b * r, 0.78f, 0.35f, 0.2f);
            zu(m.rightArm, b * r, -2.05f, 0.5f, 0.25f);
            zu(m.leftArm, b * r, -0.25f, 0f, -2.2f);
            ellbogen(true, 0f, 2.5f * r);
            zu(m.body, b * r, 0.12f, -0.15f, 0f);
        });
        neu("tpose", "T-Pose", 3.0f, (m, t, b) -> {
            float r = ein(t, 0f, 0.3f);
            zu(m.rightArm, b * r, 0f, 0f, 1.57f);
            zu(m.leftArm, b * r, 0f, 0f, -1.57f);
            zu(m.head, b, 0f, 0f, 0f);
            zu(m.body, b, 0f, 0f, 0f);
            zu(m.rightLeg, b, 0f, 0f, 0f);
            zu(m.leftLeg, b, 0f, 0f, 0f);
        });
        neu("facepalm", "Facepalm", 2.8f, (m, t, b) -> {
            float r = ein(t, 0.05f, 0.3f), sch = ein(t, 0.6f, 0.3f);
            zu(m.rightArm, b * r, -1.2f, -0.35f, 0.25f);
            zu(m.leftArm, b, -0.2f, 0f, -0.5f);
            ellbogen(true, 1.7f * r, 0.35f * r);
            ellbogen(false, 0.25f, -0.3f);
            zu(m.head, b, 0.5f * r, 0.12f * sin(t * 7f) * sch, 0.08f);
            buecken(m, b, 0.15f * r);
        });
        neu("think", "Think", 3.2f, (m, t, b) -> {
            float tap = sin(t * 8f) * ein(t, 1.0f, 0.3f);
            zu(m.rightArm, b, -0.75f + tap * 0.04f, -0.3f, 0.1f);
            zu(m.leftArm, b, -0.55f, 0.6f, 0f);
            ellbogen(true, 1.85f, 0.3f);
            ellbogen(false, 1.5f, 0.15f);
            zu(m.head, b, 0.2f, 0.25f, 0.12f);
        });
        neu("laugh", "Laugh", 3.0f, (m, t, b) -> {
            float sh = sin(t * 22f);
            zu(m.head, b, -0.45f + sh * 0.05f, 0f, 0f);
            zu(m.body, b, -0.08f, 0f, 0f);
            zu(m.rightArm, b, -0.35f, -0.3f, 0f);
            zu(m.leftArm, b, -0.35f, 0.3f, 0f);
            ellbogen(true, 1.35f, 0.7f);
            ellbogen(false, 1.35f, 0.7f);
            hoch(m, b, (0.5f + 0.5f * sh) * 0.6f);
        });
        neu("cry", "Cry", 3.5f, (m, t, b) -> {
            float sob = 0.5f + 0.5f * sin(t * 9f);
            buecken(m, b, 0.25f + 0.08f * sob);
            zu(m.head, b, 0.55f, 0f, 0.05f * sin(t * 3f));
            zu(m.rightArm, b, -1.45f, -0.4f, 0.15f);
            zu(m.leftArm, b, -1.45f, 0.4f, -0.15f);
            ellbogen(true, 1.4f, 0.35f);
            ellbogen(false, 1.4f, 0.35f);
        });
        neu("zombie", "Zombie", 5.0f, (m, t, b) -> {
            zu(m.rightArm, b, -1.6f + 0.08f * sin(t * 2f), 0.05f, 0.05f);
            zu(m.leftArm, b, -1.55f + 0.08f * sin(t * 2f + 1f), -0.05f, -0.05f);
            zu(m.head, b, 0.2f, 0.15f * sin(t * 0.9f), 0.25f);
            zu(m.rightLeg, b, 0.25f * sin(t * 2.2f), 0f, 0f);
            zu(m.leftLeg, b, -0.25f * sin(t * 2.2f), 0f, 0f);
            ellbogen(true, 0.25f + 0.1f * sin(t * 2f), 0f);
            ellbogen(false, 0.15f, 0f);
            knie(true, 0.35f * Math.max(0f, -sin(t * 2.2f)));
            knie(false, 0.35f * Math.max(0f, sin(t * 2.2f)));
            zu(m.body, b, 0.1f, 0f, 0.05f);
        });
        neu("sit", "Sit", 10.0f, (m, t, b) -> {
            float k = ein(t, 0f, 0.45f), f = k * b;
            zu(m.rightLeg, f, -1.5f, 0.12f, 0.05f);
            zu(m.leftLeg, f, -1.5f, -0.12f, -0.05f);
            zu(m.rightArm, f, -0.35f, 0f, 0.15f);
            zu(m.leftArm, f, -0.35f, 0f, -0.15f);
            ellbogen(true, 0.9f * k, 0.2f * k);
            ellbogen(false, 0.9f * k, 0.2f * k);
            knie(true, 1.5f * k);
            knie(false, 1.5f * k);
            zu(m.body, f, -0.12f, 0f, 0f);
            zu(m.head, b, -0.05f, 0.2f * sin(t * 0.7f), 0f);
            hoch(m, b, -10f * k);
        });

        seiteJetzt = "Taunt";
        neu("gg", "GG", 3.2f, (m, t, b) -> {
            float u = ein(t, 1.3f, 0.3f);
            float c = (float) Math.pow(0.5f + 0.5f * sin(t * 14f), 3) * (1f - u);
            float k = u * (1f - ein(t, 2.5f, 0.4f));
            zu(m.rightArm, b, mix(-1.3f, -0.6f, u), mix(-(0.22f + 0.33f * c), -0.5f, u), 0f);
            zu(m.leftArm, b, mix(-1.3f, 0.5f, u), mix(0.22f + 0.33f * c, 0.4f, u), 0f);
            buecken(m, b, 0.9f * k);
            zu(m.head, b, 0.3f * k, 0f, 0f);
        });
        neu("flex", "Flex", 3.0f, (m, t, b) -> {
            float p = 0.5f + 0.5f * sin(t * 6f), r = ein(t, 0f, 0.25f);
            zu(m.rightArm, b * r, -0.1f, 0f, 1.5f + 0.1f * p);
            zu(m.leftArm, b * r, -0.1f, 0f, -1.5f - 0.1f * p);
            ellbogen(true, 0.15f * r, -(1.75f + 0.25f * p) * r);
            ellbogen(false, 0.15f * r, -(1.75f + 0.25f * p) * r);
            zu(m.body, b, -0.08f, 0f, 0f);
            zu(m.head, b, -0.15f, 0f, 0f);
            hoch(m, b, 0.3f * p);
        });
        neu("come_here", "Come Here", 2.6f, (m, t, b) -> {
            float w = Math.max(0f, sin(t * 9f));
            zu(m.rightArm, b, -1.25f, -0.05f, 0.1f);
            ellbogen(true, 0.3f + 1.2f * w, 0f);
            zu(m.leftArm, b, -0.6f, 0f, -0.9f);
            zu(m.head, b, -0.18f, 0.1f, 0.1f);
            zu(m.body, b, -0.06f, 0f, 0f);
        });
        neu("victory", "Victory", 3.0f, (m, t, b) -> {
            float h = t < 0.7f ? sprung(t, 0.7f) : 0f, pump = sin(t * 8f) * ein(t, 0.8f, 0.2f);
            hoch(m, b, h * 5f);
            zu(m.rightArm, b, -3.05f + pump * 0.2f, 0f, -0.15f);
            zu(m.leftArm, b, -0.3f, 0f, -0.35f);
            ellbogen(true, 0.35f * (1f - pump), 0f);
            knie(true, 1.3f * h);
            knie(false, 0.6f * h);
            zu(m.head, b, -0.3f, 0f, 0f);
            zu(m.rightLeg, b, -0.5f * h, 0f, 0f);
            zu(m.leftLeg, b, 0.2f * h, 0f, 0f);
        });
        neu("arms_crossed", "Crossed", 4.0f, (m, t, b) -> {
            float r = ein(t, 0f, 0.3f);
            zu(m.rightArm, b * r, -0.55f, -0.35f, 0.05f);
            zu(m.leftArm, b * r, -0.65f, 0.35f, -0.05f);
            ellbogen(true, 0.4f * r, 1.55f * r);
            ellbogen(false, 0.4f * r, 1.55f * r);
            zu(m.head, b, -0.05f, 0.18f * sin(t * 1.5f), 0f);
            zu(m.rightLeg, b, -0.15f * Math.max(0f, sin(t * 7f)), 0f, 0.05f);
        });
        neu("yawn", "Yawn", 3.5f, (m, t, b) -> {
            float s = ein(t, 0.2f, 0.6f) * (1f - ein(t, 2.4f, 0.6f));
            zu(m.rightArm, b, -3.0f * s, 0f, -0.25f * s);
            zu(m.leftArm, b, -3.0f * s, 0f, 0.25f * s);
            ellbogen(true, 0f, 1.2f * s);
            ellbogen(false, 0f, 1.2f * s);
            zu(m.head, b, -0.45f * s, 0f, 0.1f * sin(t * 2f) * s);
            hoch(m, b, 0.8f * s);
        });
        neu("jumping_jacks", "Jacks", 5.0f, (m, t, b) -> {
            float p = 0.5f - 0.5f * cos(t * 7f);
            hoch(m, b, Math.abs(sin(t * 7f)) * 1.6f);
            zu(m.rightArm, b, 0f, 0f, 0.25f + 2.6f * p);
            zu(m.leftArm, b, 0f, 0f, -(0.25f + 2.6f * p));
            zu(m.rightLeg, b, 0f, 0f, 0.05f + 0.3f * p);
            zu(m.leftLeg, b, 0f, 0f, -(0.05f + 0.3f * p));
            knie(true, 0.4f * (1f - Math.abs(sin(t * 7f))));
            knie(false, 0.4f * (1f - Math.abs(sin(t * 7f))));
        });
        neu("heart", "Heart", 3.2f, (m, t, b) -> {
            float r = ein(t, 0f, 0.3f);
            zu(m.rightArm, b * r, -2.75f, 0f, -0.55f);
            zu(m.leftArm, b * r, -2.75f, 0f, 0.55f);
            ellbogen(true, 0.2f * r, 1.6f * r);
            ellbogen(false, 0.2f * r, 1.6f * r);
            zu(m.head, b, -0.15f, 0f, 0.1f * sin(t * 3f));
            hoch(m, b, 0.3f * (0.5f + 0.5f * sin(t * 6f)));
        });
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); } catch (Throwable t) { com.vortex.client.core.Errors.report("Emotes", t); }
        });
    }

    /** Eigenes Emote starten (Rad, Cosmetics-Menue) -- und an die anderen schicken. */
    public static void spielen(String id) {
        Minecraft mc = Minecraft.getInstance();
        Emote e = ALLE.get(id);
        UUID ich = mc.player != null ? mc.player.getUUID() : mc.getGameProfile().id();
        if (e == null || ich == null) return;
        AKTIV.put(ich, new Laufend(e, System.currentTimeMillis()));
        // Seit 4.71: laufendes Freelook vorher beenden (stellt seine Ansicht zurueck), sonst
        // streiten sich beide um die Kamera und man haengt danach im Freelook fest
        if (com.vortex.client.hud.Freelook.aktiv()) com.vortex.client.hud.Freelook.beenden();
        if (mc.player != null && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            kameraVorher = CameraType.FIRST_PERSON;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        CosmeticsSync.emoteGespielt(id);
    }

    /** Kontowechsel (seit 4.89): laufende Emotes beenden, sie gehoerten zur alten UUID. */
    public static void kontoGewechselt() {
        AKTIV.clear();
    }

    /** Vorschau im Emote-Rad: dieses Emote laeuft auf der Figur mit VORSCHAU_ID (null = keins). */
    public static void vorschau(String id) {
        Emote e = id == null ? null : ALLE.get(id);
        Laufend v = vorschau;
        if (e == null) { vorschau = null; return; }
        if (v == null || v.emote() != e) vorschau = new Laufend(e, System.currentTimeMillis());
    }

    /**
     * Empfaenger fuer Signale, die kein Emote sind (CosmeticsSync.signal, z. B. "knife_spin"
     * vom Vortex Plus Addon). Wird im Render-Thread aufgerufen.
     */
    public static volatile java.util.function.BiConsumer<UUID, String> signalHoerer;

    /** Emote eines anderen Spielers (vom Freunde-Server). */
    public static void fremd(UUID spieler, String id) {
        Emote e = ALLE.get(id);
        if (e != null) { AKTIV.put(spieler, new Laufend(e, System.currentTimeMillis())); return; }
        var h = signalHoerer;
        if (h != null && id != null) Minecraft.getInstance().execute(() -> {
            try { h.accept(spieler, id); } catch (Throwable t) { com.vortex.client.core.Errors.report("Emotes.signal", t); }
        });
    }

    public static boolean laeuftBeiMir() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && AKTIV.containsKey(mc.player.getUUID());
    }

    private static void tick(Minecraft mc) {
        long jetzt = System.currentTimeMillis();
        AKTIV.entrySet().removeIf(en -> jetzt - en.getValue().seit() > en.getValue().emote().dauer() * 1000f);
        if (mc.player == null) return;
        if (AKTIV.containsKey(mc.player.getUUID()) && jetzt - AKTIV.get(mc.player.getUUID()).seit() > 300
                && mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.004) {
            AKTIV.remove(mc.player.getUUID());
        }
        if (kameraVorher != null && !AKTIV.containsKey(mc.player.getUUID())) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) mc.options.setCameraType(kameraVorher);
            kameraVorher = null;
        }
    }

    /** Aus EmoteMixin: Pose fuer diesen Renderzustand setzen, falls ein Emote laeuft. */
    public static void anwenden(PlayerModel m, AvatarRenderState s) {
        zeigen(m);
        if (!posieren(m, s) || aktGlieder == 0) { BIEGUNG.remove(s); return; }
        float[][] b = new float[4][];
        for (int g = 0; g < 4; g++) if ((aktGlieder & (1 << g)) != 0) b[g] = KNICK[g].clone();
        BIEGUNG.put(s, b);
        boolean[] v = VERSTECKT.computeIfAbsent(m, k -> new boolean[4]);
        for (int g = 0; g < 4; g++) {
            if (b[g] == null) continue;
            ModelPart teil = glied(m, g);
            if (teil.visible) { teil.visible = false; v[g] = true; }
        }
    }

    /** Glieder (Bitmaske), die das laufende Emote irgendwann knickt -- gesetzt von posieren. */
    private static int aktGlieder;
    private static final java.util.Map<Emote, Integer> GLIEDER = new java.util.HashMap<>();

    /**
     * Welche Glieder knickt dieses Emote irgendwann (seit 4.99)? Diese Glieder zeichnet waehrend
     * des ganzen Emotes die BiegeEbene -- auch wenn der Knick gerade 0 ist. Vorher wurde das je
     * Bild neu entschieden; ging ein Knie durch 0, konnten Vorbereiten und Zeichnen verschieden
     * entscheiden, und das Bein verschwand kurz. Einmal je Emote aus 48 Proben bestimmt.
     */
    private static int glieder(Emote e, PlayerModel m) {
        Integer v = GLIEDER.get(e);
        if (v != null) return v;
        ModelPart[] teile = { m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg };
        net.minecraft.client.model.geom.PartPose[] alt = new net.minecraft.client.model.geom.PartPose[teile.length];
        for (int i = 0; i < teile.length; i++) alt[i] = teile[i].storePose();
        float altB = knickB;
        int bits = 0;
        try {
            knickB = 1f;
            for (int i = 0; i < 48; i++) {
                for (float[] k : KNICK) { k[0] = 0f; k[1] = 0f; k[2] = 0f; }
                e.pose().setzen(m, e.dauer() * i / 48f, 1f);
                for (int g = 0; g < 4; g++) if (geknickt(KNICK[g])) bits |= 1 << g;
            }
        } finally {
            for (int i = 0; i < teile.length; i++) teile[i].loadPose(alt[i]);
            knickB = altB;
            for (float[] k : KNICK) { k[0] = 0f; k[1] = 0f; k[2] = 0f; }
        }
        GLIEDER.put(e, bits);
        return bits;
    }

    /** Pose setzen; true, wenn ein Emote lief (KNICK ist dann gefuellt). */
    private static boolean posieren(PlayerModel m, AvatarRenderState s) {
        for (float[] k : KNICK) { k[0] = 0f; k[1] = 0f; k[2] = 0f; }
        Laufend l;
        boolean schleife = false;
        if (s.id == VORSCHAU_ID) {
            l = vorschau;
            schleife = true;
        } else {
            UUID wer = spielerVon(s.id);
            if (wer == null) return false;
            l = AKTIV.get(wer);
            if (l != null && s.walkAnimationSpeed > 0.25f && s.id != TitelFigur.ID) return false;
        }
        if (l == null) return false;
        float dauer = l.emote().dauer();
        float t = (System.currentTimeMillis() - l.seit()) / 1000f;
        if (schleife) t = t % (dauer + 0.4f);
        float b = glatt(Math.min(t / 0.25f, (dauer - t) / 0.35f));
        if (b <= 0f) return false;
        aktGlieder = glieder(l.emote(), m);
        knickB = b;
        l.emote().pose().setzen(m, t, b);
        return true;
    }

    private static UUID spielerVon(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (id == TitelFigur.ID) return mc.getGameProfile().id();
        if (mc.level == null) return null;
        Entity e = mc.level.getEntity(id);
        return e instanceof Player p ? p.getUUID() : null;
    }
}
