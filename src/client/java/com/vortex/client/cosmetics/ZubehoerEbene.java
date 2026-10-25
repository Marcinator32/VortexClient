package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Zeichnet die Pixel-Cosmetics (seit 4.29) am Spieler:
 *   Bandana und Face am Kopf, Back am Oberkoerper, Aura um den ganzen Koerper.
 *
 * Gezeichnet wird in Pixeln mit y nach oben (siehe {@link Zubehoer}); die
 * Teile sind zwischengespeicherte Netze, bewegt wird nur ueber den PoseStack.
 */
public class ZubehoerEbene extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Weisse Textur, erst beim ersten Zeichnen angelegt (die Aufnahme fuer den Launcher-Katalog braucht sie nicht). */
    private static final class Weiss { static final Identifier ID = Identifier.fromNamespaceAndPath("vortexclient", "textures/cosmetics/white.png"); }
    private static final int HELL = 0xF000F0;
    /** y-Spiegelung kehrt die Umlaufrichtung um -- Ecken rueckwaerts. */
    private static final int[] REIHE = { 0, 3, 2, 1 };
    /** Wo ein Pet schwebt (Koerper-Raum, Pixel): neben der Schulter, etwas hoeher. Der Launcher nimmt dieselben Werte. */
    static final float PET_X = -13f, PET_Y = 3f, PET_Z = 1f;

    /** Statt zu zeichnen die Flaechen sammeln (Launcher-Katalog, seit 4.87). Nur ein Thread zur Zeit. */
    interface Aufnahme { void flaeche(org.joml.Matrix4f m, Netz.Flaeche f); }
    private static volatile Aufnahme AUFNAHME;

    /**
     * Ein Design so aufnehmen, wie es am Spieler gezeichnet wird -- ohne Knochen, also in den
     * Koordinaten der Kategorie (Kopf/Koerper: Hals = 0, Pixel, y nach oben). Grosse Auren und
     * Sprite-Bewegungen nicht (die rechnet der Launcher selbst nach).
     */
    static synchronized void aufnehmen(Zubehoer.Design d, float t, Aufnahme a) {
        ZubehoerEbene e = new ZubehoerEbene(null);
        PoseStack pose = new PoseStack();
        AvatarRenderState st = leererZustand();
        st.id = Integer.MIN_VALUE + 99;
        AUFNAHME = a;
        boolean glitzer = GLITZER;
        GLITZER = false;
        try {
            switch (d.kat()) {
                case BANDANA -> e.bandana(pose, null, HELL, d, t, st);
                case FACE -> e.face(pose, null, HELL, d, t);
                case HAT -> { for (Zubehoer.Teil teil : d.teile()) e.teil(pose, null, HELL, teil, t, 0); }
                case BACK -> {
                    for (Zubehoer.Teil teil : d.teile()) {
                        if (teil.anim().startsWith("wing")) e.fluegel(pose, null, HELL, teil, t, st, false, d);
                        else e.teil(pose, null, HELL, teil, t, 0);
                    }
                }
                case NECK -> e.kette(pose, null, HELL, d, t, st);
                case PET -> { for (Zubehoer.Teil teil : d.teile()) e.teil(pose, null, HELL, teil, t, 0); }
                default -> { }
            }
        } finally {
            AUFNAHME = null;
            GLITZER = glitzer;
            LEUCHT = 1f;
            FUNKEL = false;
        }
    }

    /**
     * Ruhender Spieler fuer die Aufnahme. Ausserhalb des Spiels (Katalog beim Bauen) laesst sich
     * der Zustand nicht normal anlegen -- dann ein leerer ohne Konstruktor (alle Werte 0).
     */
    private static AvatarRenderState leererZustand() {
        try {
            return new AvatarRenderState();
        } catch (Throwable e) {
            try {
                java.lang.reflect.Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
                f.setAccessible(true);
                Object u = f.get(null);
                return (AvatarRenderState) u.getClass().getMethod("allocateInstance", Class.class).invoke(u, AvatarRenderState.class);
            } catch (Throwable e2) {
                throw new IllegalStateException("no render state", e2);
            }
        }
    }

    /** Ein einzelnes Netz unbewegt aufnehmen (Aura-Bildchen). */
    static synchronized void aufnehmen(Netz netz, Aufnahme a) {
        AUFNAHME = a;
        try {
            zeichne(new PoseStack(), null, netz, HELL, 0);
        } finally {
            AUFNAHME = null;
        }
    }

    public ZubehoerEbene(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int licht, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        Cosmetics.Auswahl a = Cosmetics.fuerEntity(state.id);
        float t = state.ageInTicks;
        try {
            Zubehoer.Design bandana = Zubehoer.get(Zubehoer.Kategorie.BANDANA, a.bandana());
            Zubehoer.Design face = Zubehoer.get(Zubehoer.Kategorie.FACE, a.gesicht());
            Zubehoer.Design hut = Zubehoer.get(Zubehoer.Kategorie.HAT, a.hut());
            if (bandana != null || face != null || hut != null) {
                pose.pushPose();
                getParentModel().head.translateAndRotate(pose);
                pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
                GLITZER = Zubehoer.glitzert(a.bandana());
                if (bandana != null) bandana(pose, collector, licht, bandana, t, state);
                GLITZER = Zubehoer.glitzert(a.gesicht());
                if (face != null) face(pose, collector, licht, face, t);
                GLITZER = Zubehoer.glitzert(a.hut());
                if (hut != null) for (Zubehoer.Teil teil : hut.teile()) teil(pose, collector, licht, teil, t, 0);
                GLITZER = false;
                pose.popPose();
            }
            Zubehoer.Design back = Zubehoer.get(Zubehoer.Kategorie.BACK, a.ruecken());
            if (back != null) {
                pose.pushPose();
                getParentModel().body.translateAndRotate(pose);
                pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
                float wippen = (float) Math.sin(state.walkAnimationPos * 0.6662f * 2) * Math.min(1f, state.walkAnimationSpeed) * 0.35f;
                boolean cape = !a.cape().isEmpty() || (state.showCape && state.skin != null && state.skin.cape() != null);
                boolean elytra = state.chestEquipment != null && state.chestEquipment.has(net.minecraft.core.component.DataComponents.GLIDER);
                float abstand = elytra ? 4.25f : cape ? 1.25f : 0f;
                GLITZER = Zubehoer.glitzert(a.ruecken());
                for (Zubehoer.Teil teil : back.teile()) {
                    if (teil.anim().startsWith("wing")) { if (!elytra) fluegel(pose, collector, licht, teil, t, state, cape, back); continue; }
                    boolean gurt = teil.anim().equals("strap");
                    boolean weg = abstand > 0 && !gurt;
                    if (weg) { pose.pushPose(); pose.translate(0, 0, abstand); }
                    teil(pose, collector, licht, teil, t, wippen);
                    if (weg) pose.popPose();
                }
                GLITZER = false;
                pose.popPose();
            }
            Zubehoer.Design hals = Zubehoer.get(Zubehoer.Kategorie.NECK, a.hals());
            if (hals != null) {
                pose.pushPose();
                getParentModel().body.translateAndRotate(pose);
                pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
                GLITZER = Zubehoer.glitzert(a.hals());
                kette(pose, collector, licht, hals, t, state);
                GLITZER = false;
                pose.popPose();
            }
            Zubehoer.Design pet = Zubehoer.get(Zubehoer.Kategorie.PET, a.pet());
            if (pet != null && Zubehoer.amBoden(pet)) {
                if (!state.isFallFlying && !state.isVisuallySwimming && !state.isPassenger) bodenPet(pose, collector, licht, pet, t, state);
            } else if (pet != null) {
                pose.pushPose();
                getParentModel().body.translateAndRotate(pose);
                pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
                pose.translate(PET_X, PET_Y + (float) Math.sin(t * 0.09f) * 0.7f, PET_Z);
                pose.mulPose(Axis.YP.rotationDegrees((float) Math.sin(t * 0.03f) * 8f));
                for (Zubehoer.Teil teil : pet.teile()) teil(pose, collector, licht, teil, t, 0);
                pose.popPose();
            }
            Zubehoer.Design aura = Zubehoer.get(Zubehoer.Kategorie.AURA, a.partikel());
            if (aura != null && aura.aura() != null) {
                pose.pushPose();
                pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
                var mc = net.minecraft.client.Minecraft.getInstance();
                boolean fremd = mc.player == null || state.id != mc.player.getId();
                int modus = fremd && GrossAura.ist(aura.aura().bewegung()) ? Cosmetics.grosseFremde() : 0;
                if (modus == 1) {
                    pose.translate(0, -24, 0);
                    pose.scale(0.45f, 0.45f, 0.45f);
                    pose.translate(0, 24, 0);
                }
                if (modus != 2) aura(pose, collector, aura.aura(), t, state.id, modus == 1 ? 1 : a.dichte());
                pose.popPose();
            }
        } catch (Throwable e) {
            GLITZER = false;
            com.vortex.client.core.Errors.report("ZubehoerEbene", e);
        }
    }

    private void bandana(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Design d, float t, AvatarRenderState s) {
        Zubehoer.Ring r = d.ring();
        if (r == null) return;
        int versatz = r.flow() == 0 ? 0 : (int) Math.floor(t / 20f * r.flow());
        leuchtModus(r.fx(), t);
        try {
            zeichne(pose, c, Zubehoer.ringNetz(r, versatz), licht, t);
        } finally {
            LEUCHT = 1f; FUNKEL = false;
        }
        knoten(pose, c, licht, r, t, s);
    }

    /**
     * Knoten hinten und zwei Baender. Mit Bandana-Physik (seit 4.35) reagieren sie
     * auf die Bewegung: beim Laufen und Fallen heben sie sich und flattern schneller,
     * beim Drehen schwingen sie zur Seite (dieselben Werte wie beim Vanilla-Cape),
     * und jedes Glied biegt sich etwas mehr als das davor. Ohne Physik haengen sie ruhig.
     */
    private void knoten(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Ring r, float t, AvatarRenderState st) {
        if (r.baender().length == 0) return;
        float a = Zubehoer.BAND_AUSSEN + Zubehoer.BAND_DICKE;
        float mitte = r.y0() + r.farben().length * r.px() / 2f;
        Netz knoten = KNOTEN.computeIfAbsent(r, k -> {
            Netz n = new Netz();
            n.quader(-0.7f, -0.6f, 0, 0.7f, 0.6f, 0.6f, r.knoten(), 0x3F);
            return n;
        });
        Netz[] glieder = GLIEDER.computeIfAbsent(r, k -> {
            Netz[] g = new Netz[GLIED_ANZAHL];
            for (int i = 0; i < GLIED_ANZAHL; i++) {
                int f = r.baender()[(i / 2) % r.baender().length];
                g[i] = new Netz();
                float b = 0.35f - i * 0.025f;
                g[i].quader(-b, -GLIED - 0.04f, 0, b, 0.02f, 0.25f, f, 0x3F);
            }
            return g;
        });
        boolean physik = Cosmetics.bandanaPhysik();
        float tempo = Math.min(1f, st.walkAnimationSpeed);
        float heben = physik ? 10f + Math.max(0f, Math.min(36f, st.capeLean * 0.3f + st.capeFlap * 0.6f)) : 6f;
        float seite = physik ? Math.max(-30f, Math.min(30f, st.capeLean2 * 0.6f)) : 0f;
        pose.pushPose();
        pose.translate(0, mitte, a);
        zeichne(pose, c, knoten, licht, t);
        for (int s = -1; s <= 1; s += 2) {
            pose.pushPose();
            pose.translate(s * 0.35f, -0.2f, 0.35f);
            pose.mulPose(Axis.XP.rotationDegrees(heben));
            pose.mulPose(Axis.ZP.rotationDegrees(s * 16f + seite));
            for (int i = 0; i < GLIED_ANZAHL; i++) {
                zeichne(pose, c, glieder[i], licht, t);
                pose.translate(0, -GLIED, 0);
                if (physik) {
                    float anteil = (i + 1f) / GLIED_ANZAHL;
                    float boee = 0.6f + 0.4f * (float) Math.sin(t * 0.045f + s * 0.8f);
                    float flattern = (float) Math.sin(t * (0.2f + tempo * 0.55f) - i * 0.9f + s * 1.3f) * (5.5f * boee + tempo * 11f) * anteil;
                    pose.mulPose(Axis.XP.rotationDegrees(flattern + heben * 0.03f));
                    pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * 0.09f + i * 0.7f + s) * (1.5f + tempo * 3f) * anteil));
                }
            }
            pose.popPose();
        }
        pose.popPose();
    }

    private static final int GLIED_ANZAHL = 6;
    private static final float GLIED = 0.6f;
    private static final java.util.Map<Object, Netz[]> GLIEDER = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Object, Netz> KNOTEN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Object, Netz> BUEGEL = new java.util.concurrent.ConcurrentHashMap<>();

    private void face(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Design d, float t) {
        if (d.ring() != null) zeichne(pose, c, Zubehoer.ringNetz(d.ring()), licht, t);
        for (Zubehoer.Teil teil : d.teile()) teil(pose, c, licht, teil, t, 0);
        if (d.buegel() != 0 && !d.teile().isEmpty()) {
            Zubehoer.Teil vorne = d.teile().get(0);
            float y = vorne.at()[1] + vorne.farben().length * vorne.px() / 2f - vorne.px() * 0.5f;
            Netz b = BUEGEL.computeIfAbsent(d, k -> {
                Netz n = new Netz();
                float a = 4.55f;
                n.quader(a, -0.15f, -4.8f, a + 0.3f, 0.15f, 1.5f, d.buegel(), 0x3F);
                n.quader(-a - 0.3f, -0.15f, -4.8f, -a, 0.15f, 1.5f, d.buegel(), 0x3F);
                return n;
            });
            pose.pushPose();
            pose.translate(0, y, 0);
            zeichne(pose, c, b, licht, t);
            pose.popPose();
        }
    }

    /** Ein Pixelteil an seine Stelle setzen (at, Drehung, Groesse) und zeichnen. */
    /**
     * Ein Pixelteil an seine Stelle setzen (at, Drehung, Groesse) und zeichnen.
     * Animationen (anim=name[:wert]):
     *   bob        wippt beim Laufen            flicker   flackert (Flammen)
     *   pulse[:a]  pocht wie ein Herzschlag     twinkle[:phase] funkelt auf und ab
     *   glint:w    Glanz huscht ueber w Pixel (alle paar Sekunden)
     *   scan:w     Lauflicht hin und her ueber +-w Pixel
     *   swing:g    pendelt um den oberen Rand   sway:g    wiegt sich um die Mitte
     *   spin[:g]   dreht sich (g Grad je Tick)  hover[:h] schwebt auf und ab
     *   leg:ph,py  Bein beim Laufen (Boden-Pets) wag:g,v,pz  Schwanz wedelt
     *   hop:h      huepft (Schleim)              waddle:g  watschelt (Pinguin)
     */
    private void teil(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Teil teil, float t, float wippen) {
        float s = teil.px();
        float hoehe = teil.farben().length * s;
        float dx = 0, dy = 0, dz = 0, querX = 0, drehZ = 0, drehY = 0, gross = 1f, hochY = 1f;
        float beinX = 0, beinPy = 0, wedelY = 0, wedelPz = 0;
        boolean swing = false, unten = false;
        for (String teilAnim : teil.anim().split("\\+")) {
            String name = teilAnim;
            float[] w = KEINE;
            int dp = teilAnim.indexOf(':');
            if (dp >= 0) {
                name = teilAnim.substring(0, dp);
                String[] z = teilAnim.substring(dp + 1).split(",");
                w = new float[z.length];
                for (int i = 0; i < z.length; i++) { try { w[i] = Float.parseFloat(z[i]); } catch (NumberFormatException e) { w[i] = Float.NaN; } }
            }
            float w0 = wert(w, 0, Float.NaN);
            switch (name) {
                case "bob" -> dy += wippen;
                case "flicker" -> {
                    float ph = wert(w, 0, 0);
                    hochY *= 0.75f + 0.25f * (float) Math.abs(Math.sin(t * 1.7f + ph) * Math.cos(t * 0.9f + ph * 1.7f));
                }
                case "pulse" -> {
                    float amp = Float.isNaN(w0) ? 0.09f : w0;
                    float ph = (t % 24f) / 24f;
                    float schlag = (float) (Math.exp(-Math.pow((ph - 0.08) * 22, 2)) + 0.6 * Math.exp(-Math.pow((ph - 0.26) * 22, 2)));
                    gross *= 1 + amp * schlag;
                }
                case "breathe" -> gross *= 1 + (Float.isNaN(w0) ? 0.06f : w0) * (float) Math.sin(t * 0.1f + wert(w, 1, 0));
                case "twinkle" -> {
                    float ph = (t / 40f + (Float.isNaN(w0) ? 0 : w0)) % 1f;
                    float g = (float) Math.max(0, Math.sin(ph * Math.PI * 2));
                    gross *= g * g;
                    drehZ += t * 4f;
                }
                case "glint" -> {
                    float br = Float.isNaN(w0) ? 3f : w0;
                    float ph = (t % 70f) / 14f;
                    if (ph > 1f) gross = 0;
                    else querX += -br + 2 * br * ph;
                }
                case "scan" -> querX += (float) Math.sin(t * 0.09f) * (Float.isNaN(w0) ? 3f : w0);
                case "swing" -> { drehZ += (float) Math.sin(t * 0.11f + wert(w, 1, 0)) * (Float.isNaN(w0) ? 8f : w0); swing = true; }
                case "sway" -> drehZ += (float) Math.sin(t * 0.07f + wert(w, 1, 0)) * (Float.isNaN(w0) ? 6f : w0);
                case "flap" -> drehZ += (float) Math.sin(t * wert(w, 1, 0.3f) + wert(w, 2, 0)) * (Float.isNaN(w0) ? 25f : w0);
                case "spin" -> drehZ += t * (Float.isNaN(w0) ? 3f : w0);
                case "spinY" -> drehY += t * (Float.isNaN(w0) ? 2f : w0) + wert(w, 1, 0);
                case "hover" -> dy += (float) Math.sin(t * wert(w, 1, 0.08f) + wert(w, 2, 0)) * (Float.isNaN(w0) ? 0.3f : w0);
                case "orbit" -> {
                    float r = Float.isNaN(w0) ? 7f : w0;
                    double ww = t * wert(w, 1, 0.05f) + wert(w, 2, 0);
                    dx += (float) (Math.cos(ww) * r) - teil.at()[0];
                    dz += (float) (Math.sin(ww) * r) - teil.at()[2];
                }
                case "leg" -> {
                    beinX = (float) Math.cos(LAUF_POS * 0.6662f + wert(w, 0, 0)) * 50f * LAUF_TEMPO;
                    beinPy = wert(w, 1, 0);
                }
                case "wag" -> {
                    float tempoW = wert(w, 1, 0.2f) * (1f + LAUF_TEMPO);
                    wedelY = (float) Math.sin(t * tempoW) * (Float.isNaN(w0) ? 15f : w0);
                    wedelPz = wert(w, 2, 0);
                }
                case "hop" -> {
                    float h = Float.isNaN(w0) ? 2f : w0;
                    float ph = (t * (0.12f + 0.1f * LAUF_TEMPO)) % 1f;
                    float flug = (float) Math.sin(ph * Math.PI);
                    dy += flug * h * (0.45f + 0.55f * LAUF_TEMPO);
                    float landen = ph < 0.12f ? 1f - ph / 0.12f : 0f;
                    hochY *= 1f - 0.18f * landen + 0.06f * flug;
                }
                case "waddle" -> drehZ += (float) Math.sin(LAUF_POS * 0.6662f) * (Float.isNaN(w0) ? 10f : w0) * Math.max(LAUF_TEMPO, 0.15f)
                        + (float) Math.sin(t * 0.05f) * 2f * (1f - LAUF_TEMPO);
                case "twitch" -> {
                    float ph = (t + wert(w, 1, 0)) % 80f;
                    if (ph < 7) drehZ += (float) Math.sin(ph / 7f * Math.PI) * (Float.isNaN(w0) ? 14f : w0);
                    unten = true;
                }
                default -> {}
            }
        }
        if (gross <= 0.01f) return;
        pose.pushPose();
        float[] at = teil.at(), rot = teil.rot();
        pose.translate(at[0] + dx, at[1] + dy, at[2] + dz);
        if (drehY != 0) pose.mulPose(Axis.YP.rotationDegrees(drehY));
        if (rot[1] != 0) pose.mulPose(Axis.YP.rotationDegrees(rot[1]));
        if (rot[0] != 0) pose.mulPose(Axis.XP.rotationDegrees(rot[0]));
        if (rot[2] != 0) pose.mulPose(Axis.ZP.rotationDegrees(rot[2]));
        if (querX != 0) pose.translate(querX, 0, 0);
        if (beinX != 0) {
            pose.translate(0, beinPy, 0);
            pose.mulPose(Axis.XP.rotationDegrees(beinX));
            pose.translate(0, -beinPy, 0);
        }
        if (wedelY != 0) {
            pose.translate(0, 0, wedelPz);
            pose.mulPose(Axis.YP.rotationDegrees(wedelY));
            pose.translate(0, 0, -wedelPz);
        }
        if (drehZ != 0) {
            float piv = swing ? hoehe / 2f : unten ? -hoehe / 2f : 0;
            pose.translate(0, piv, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(drehZ));
            pose.translate(0, -piv, 0);
        }
        if (hochY != 1f) pose.translate(0, (1 - hochY) * hoehe / 2f, 0);
        pose.scale(s * gross, s * gross * hochY, s * gross);
        if (teil.vox() == null) flach(pose, c, teil.farben(), teil.glow(), teil.tiefe(), Zubehoer.netz(teil), licht, t);
        else zeichne(pose, c, Zubehoer.netz(teil), licht, t);
        pose.popPose();
    }

    private static final float[] KEINE = new float[0];

    /** Laufen fuer Boden-Pets (Beine, Watscheln): Position und Tempo der Laufanimation. */
    private static float LAUF_POS, LAUF_TEMPO;
    /** Wo ein Boden-Pet steht (Pixel, Modellraum des Spielers): neben dem rechten Fuss, etwas dahinter. */
    static final float BODEN_X = -11f, BODEN_Z = 3f;
    private static final java.util.Map<Integer, float[]> BODEN_ZUSTAND = new java.util.HashMap<>();

    /**
     * Boden-Pet (seit 4.98): steht neben den Fuessen, laeuft mit (Beine aus der Laufanimation
     * des Spielers), faellt beim Rennen ein Stueck zurueck und holt weich wieder auf.
     */
    private void bodenPet(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Design pet, float t, AvatarRenderState st) {
        float tempo = Math.min(1f, st.walkAnimationSpeed);
        float[] z = BODEN_ZUSTAND.computeIfAbsent(st.id, k -> new float[] { t, 0f });
        float dt = t - z[0];
        if (dt < 0f || dt > 40f) dt = 0f;
        if (dt > 0.02f) {
            z[0] = t;
            z[1] += (tempo - z[1]) * (1f - (float) Math.exp(-dt * 0.15f));
        }
        if (BODEN_ZUSTAND.size() > 200) BODEN_ZUSTAND.clear();
        pose.pushPose();
        pose.translate(BODEN_X / 16f, 24f / 16f, (BODEN_Z + z[1] * 5f) / 16f);
        pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
        float hopp = Math.abs((float) Math.sin(st.walkAnimationPos * 0.6662f)) * tempo * 0.5f;
        pose.translate(0, hopp, 0);
        pose.mulPose(Axis.YP.rotationDegrees((float) Math.sin(t * 0.025f) * 6f * (1f - tempo)));
        LAUF_POS = st.walkAnimationPos;
        LAUF_TEMPO = tempo;
        try {
            for (Zubehoer.Teil teil : pet.teile()) teil(pose, c, licht, teil, t, 0);
        } finally {
            LAUF_TEMPO = 0f;
            pose.popPose();
        }
    }

    private static final java.util.Map<Object, Netz> KETTEN = new java.util.concurrent.ConcurrentHashMap<>();

    private static float zahl(java.util.Map<String, String> w, String k, float d) {
        try { return w.containsKey(k) ? Float.parseFloat(w.get(k)) : d; } catch (NumberFormatException e) { return d; }
    }

    /**
     * Die Kette als Netz: vorne als U ueber die Brust, ueber beide Schultern und hinten quer.
     * Mit Brustpanzer etwas weiter aussen. Glieder je nach Art: link, cuban, rope, beads, ball.
     */
    private static Netz ketteNetz(Zubehoer.Design d, boolean ruestung) {
        return KETTEN.computeIfAbsent(java.util.List.of(d.id(), ruestung), k -> {
            String art = d.werte().getOrDefault("chain", "link");
            float tief = zahl(d.werte(), "drop", 4.6f);
            int c = d.farben().getOrDefault('c', 0xFFD9A82A), hell = d.farben().getOrDefault('C', 0xFFFFE17A);
            float breit = ruestung ? 3.7f : 3.3f, vorne = ruestung ? -3.25f : -2.18f, hinten = ruestung ? 3.2f : 2.18f, oben = ruestung ? 0.75f : 0.15f;
            java.util.List<float[]> pfad = new java.util.ArrayList<>();
            for (int i = 0; i <= 8; i++) { float u = i / 8f * 2 - 1; pfad.add(new float[] { u * (breit - 0.6f), oben - 0.35f, hinten }); }
            for (int i = 1; i <= 6; i++) { float u = i / 6f; pfad.add(new float[] { breit - 0.6f + 0.6f * u, oben + 0.2f * (float) Math.sin(u * Math.PI), hinten + (vorne - hinten) * u }); }
            for (int i = 1; i <= 26; i++) {
                float u = 1 - i / 26f * 2;
                float sag = 1 - u * u;
                pfad.add(new float[] { u * breit, oben - tief * sag, vorne - 0.2f * sag });
            }
            for (int i = 1; i < 6; i++) { float u = i / 6f; pfad.add(new float[] { -breit + 0.6f * u, oben + 0.2f * (float) Math.sin(u * Math.PI), vorne + (hinten - vorne) * u }); }
            float schritt = switch (art) { case "beads" -> 0.5f; case "ball" -> 0.38f; case "cuban" -> 0.55f; case "rope" -> 0.3f; default -> 0.5f; };
            Netz n = new Netz();
            float rest = 0;
            int nr = 0;
            boolean leuchtet = "1".equals(d.werte().get("chainglow"));
            for (int i = 0; i + 1 < pfad.size(); i++) {
                float[] p0 = pfad.get(i), p1 = pfad.get(i + 1);
                float dx = p1[0] - p0[0], dy = p1[1] - p0[1], dz = p1[2] - p0[2];
                float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-4f) continue;
                float yaw = (float) Math.atan2(-dz, dx), pitch = (float) Math.atan2(dy, (float) Math.sqrt(dx * dx + dz * dz));
                float s = rest;
                for (; s < len; s += schritt) {
                    float f = s / len;
                    n.push();
                    n.verschiebe(p0[0] + dx * f, p0[1] + dy * f, p0[2] + dz * f);
                    n.drehY(yaw);
                    n.drehZ(pitch);
                    boolean wechsel = (nr++ % 2) == 1;
                    n.leuchten(leuchtet && wechsel ? 1f : 0f);
                    switch (art) {
                        case "cuban" -> n.quader(-0.32f, -0.24f, -0.13f, 0.32f, 0.24f, 0.13f, wechsel ? hell : c, 0x3F);
                        case "rope" -> {
                            n.drehX(nr * 0.9f);
                            n.quader(-0.17f, -0.1f, -0.16f, 0.17f, 0.1f, 0.16f, wechsel ? hell : c, 0x3F);
                        }
                        case "beads" -> n.quader(-0.22f, -0.22f, -0.22f, 0.22f, 0.22f, 0.22f, nr % 5 == 0 ? hell : c, 0x3F);
                        case "ball" -> n.quader(-0.13f, -0.13f, -0.13f, 0.13f, 0.13f, 0.13f, c, 0x3F);
                        default -> {
                            if (wechsel) n.quader(-0.28f, -0.08f, -0.17f, 0.28f, 0.08f, 0.17f, hell, 0x3F);
                            else n.quader(-0.28f, -0.17f, -0.08f, 0.28f, 0.17f, 0.08f, c, 0x3F);
                        }
                    }
                    n.pop();
                }
                rest = s - len;
            }
            return n;
        });
    }

    /** Kette zeichnen, dazu der Anhaenger: haengt unten am U und pendelt beim Laufen und Drehen. */
    private void kette(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Design d, float t, AvatarRenderState st) {
        boolean ruestung = st.chestEquipment != null && !st.chestEquipment.isEmpty();
        zeichne(pose, c, ketteNetz(d, ruestung), licht, t);
        float tief = zahl(d.werte(), "drop", 4.6f);
        float vorne = ruestung ? -3.25f : -2.18f, oben = ruestung ? 0.75f : 0.15f;
        float tempo = Math.min(1f, st.walkAnimationSpeed);
        float schwingX = (float) Math.sin(st.walkAnimationPos * 0.6662f) * tempo * 14f + st.capeLean * 0.25f + (float) Math.sin(t * 0.05f) * 2.5f;
        float schwingZ = st.capeLean2 * 0.35f + (float) Math.sin(t * 0.037f + 1.3f) * 3f;
        for (Zubehoer.Teil teil : d.teile()) {
            if (!teil.anim().startsWith("pendant")) continue;
            float hoehe = teil.farben().length * teil.px();
            pose.pushPose();
            pose.translate(0, oben - tief - 0.15f, vorne - 0.35f);
            pose.mulPose(Axis.XP.rotationDegrees(-Math.max(-25f, Math.min(35f, schwingX))));
            pose.mulPose(Axis.ZP.rotationDegrees(Math.max(-25f, Math.min(25f, schwingZ))));
            pose.translate(0, -hoehe / 2f, -0.05f);
            pose.scale(teil.px(), teil.px(), teil.px());
            if (teil.vox() == null) flach(pose, c, teil.farben(), teil.glow(), teil.tiefe(), Zubehoer.netz(teil), licht, t);
            else zeichne(pose, c, Zubehoer.netz(teil), licht, t);
            pose.popPose();
        }
    }

    /** Netze der drei Glieder einer Seite und ihre Breite (in Bildpixeln). */
    private record FluegelTeile(Netz[] netze, int[] breiten, int[][][] farben, boolean[][][] glow, float[] versatzX, float versatzY, float tiefe, int[] schnitte) {}

    private static final java.util.Map<Object, FluegelTeile> FLUEGEL = new java.util.concurrent.ConcurrentHashMap<>();
    /** Knochen der Fledermaus- und Drachenfluegel je Teil und Seite (seit 4.82). */
    private static final java.util.Map<Object, Netz[]> KNOCHEN = new java.util.concurrent.ConcurrentHashMap<>();
    /** Je Spieler: letzte Zeit, letzte Hoehe, wie weit offen (geglaettet). */
    private static final java.util.Map<Integer, float[]> FLUEGEL_ZUSTAND = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Das Fluegelbild (rechter Fluegel, Spalte 0 am Ruecken) in Schulter, Mitte und Spitze
     * teilen. Fuer links (seite -1) gespiegelt. Ursprung jedes Glieds = sein Gelenk am
     * inneren Rand, auf Hoehe der Vorderkante.
     */
    /**
     * Seit 4.70: Gelenk bei einem Drittel der Bildhoehe (vorher ein Fuenftel) und der Ansatz
     * 2.4 Pixel hoeher -- die Fluegel sitzen an den Schulterblaettern und ragen ueber die
     * Schultern, statt am Steissbein zu haengen.
     */
    private static final float GELENK = 0.34f, HOEHER = 2.4f;

    private static FluegelTeile fluegelTeile(Zubehoer.Teil teil, int seite) {
        return FLUEGEL.computeIfAbsent(java.util.List.of(teil, seite), k -> {
            int[][] c = teil.anim().equals("wing:bat") && teil.vox() == null ? FledermausKunst.haut(teil.farben(), teil.glow()) : teil.farben();
            boolean[][] g = teil.glow();
            int h = c.length, w = 0;
            for (int[] r : c) w = Math.max(w, r.length);
            int[] grenzen = { 0, Math.round(w * 0.34f), Math.round(w * 0.67f), w };
            float gelenk = Math.round(h * GELENK);
            Netz[] netze = new Netz[3];
            int[] breiten = new int[3];
            int[][][] farben = new int[3][][];
            boolean[][][] glows = new boolean[3][][];
            float[] versatzX = new float[3];
            int[] schnitte = new int[3];
            for (int sgm = 0; sgm < 3; sgm++) {
                int a = grenzen[sgm], b = grenzen[sgm + 1], sw = b - a;
                breiten[sgm] = sw;
                int[][] teilC = new int[h][sw];
                boolean[][] teilG = new boolean[h][sw];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < sw; x++) {
                        int quelle = seite > 0 ? a + x : b - 1 - x;
                        teilC[y][x] = quelle < c[y].length ? c[y][quelle] : 0;
                        teilG[y][x] = g != null && quelle < g[y].length && g[y][quelle];
                    }
                }
                Netz n = new Netz();
                n.push();
                n.verschiebe(seite * sw / 2f, -(h / 2f - gelenk), 0);
                n.pixelbild(teilC, teilG, teil.tiefe());
                n.pop();
                netze[sgm] = n;
                farben[sgm] = teilC;
                glows[sgm] = teilG;
                versatzX[sgm] = seite * sw / 2f;
                boolean innen = sgm > 0, aussen = sgm < 2;
                schnitte[sgm] = seite > 0 ? (innen ? 1 : 0) | (aussen ? 2 : 0) : (aussen ? 1 : 0) | (innen ? 2 : 0);
            }
            return new FluegelTeile(netze, breiten, farben, glows, versatzX, -(h / 2f - gelenk), teil.tiefe(), schnitte);
        });
    }

    /**
     * Fluegel: angelegt beim Schleichen, halb offen im Stehen, weit offen beim Springen,
     * Fallen und mit Elytra-Flug -- dort mit kraeftigem Schlag. Jedes Glied folgt dem davor
     * etwas verzoegert, so wirkt der Schlag weich statt steif. Schmetterlinge schlagen schneller.
     * Seit 4.98 laeuft der Schlag ueber eine eigene Phase; Takt, Ausschlag und Offenheit gleiten
     * weich zu ihren Zielen -- vorher sprang die Phase bei jeder Tempo-Aenderung (Zappeln).
     */
    private void fluegel(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Teil teil, float t, AvatarRenderState st, boolean cape, Zubehoer.Design d) {
        String art = teil.anim().length() > 5 ? teil.anim().substring(5) : "feather";
        boolean falter = art.equals("butterfly"), haut = art.equals("bat");
        float[] z = FLUEGEL_ZUSTAND.computeIfAbsent(st.id, k -> new float[] { t, (float) st.y, 0.45f, 0f, 0.08f, 4f, 0f });
        float dt = t - z[0];
        if (dt < 0f || dt > 40f) { z[0] = t; z[1] = (float) st.y; dt = 0f; }
        if (dt > 0.02f) {
            float vy = (float) (st.y - z[1]) / dt;
            z[0] = t; z[1] = (float) st.y;
            z[6] += (vy - z[6]) * (1f - (float) Math.exp(-dt * 0.35f));
            float tempo = Math.min(1f, st.walkAnimationSpeed);
            boolean flug = st.isFallFlying;
            boolean luft = Math.abs(z[6]) > 0.14f;
            float ziel = st.isCrouching ? 0.05f : flug ? 1f : luft ? 0.85f : 0.42f + 0.18f * tempo;
            float zielTakt = flug ? 0.42f : luft ? 0.26f : 0.07f + tempo * 0.06f;
            float zielAmp = flug ? 24f : luft ? 16f : 4f + tempo * 4f;
            if (falter) { zielTakt *= 1.7f; zielAmp *= 1.35f; }
            if (haut) zielAmp *= 1.1f;
            z[2] += (ziel - z[2]) * (1f - (float) Math.exp(-dt * 0.22f));
            z[4] += (zielTakt - z[4]) * (1f - (float) Math.exp(-dt * 0.12f));
            z[5] += (zielAmp - z[5]) * (1f - (float) Math.exp(-dt * 0.12f));
            z[3] = (float) ((z[3] + dt * z[4]) % (Math.PI * 2));
        }
        if (FLUEGEL_ZUSTAND.size() > 200) FLUEGEL_ZUSTAND.clear();
        float offen = z[2];
        boolean flug = st.isFallFlying;
        float amp = z[5], phase = z[3];
        float schlag = (float) Math.sin(phase);

        Schwingen.Stil stil = Schwingen.stil(d);
        if (stil != null) {
            schwingen(pose, c, licht, teil, stil, t, offen, schlag, phase, amp, cape);
            return;
        }

        boolean federn = AUFNAHME == null && FederFluegel.passt(teil);
        int bildH = teil.farben().length, bildW = 0;
        for (int[] r : teil.farben()) bildW = Math.max(bildW, r.length);
        int[] grenzen = { 0, Math.round(bildW * 0.34f), Math.round(bildW * 0.67f), bildW };
        float gelenk = Math.round(bildH * GELENK);
        float flatter = 0.012f + 0.03f * offen + (flug ? 0.02f : 0f);
        for (int seite = -1; seite <= 1; seite += 2) {
            FluegelTeile ft = fluegelTeile(teil, seite);
            final int sd = seite;
            Netz[] knochen = haut && teil.vox() == null
                    ? KNOCHEN.computeIfAbsent(java.util.List.of(teil, seite), k -> FledermausKunst.bauen(teil.farben(), teil.glow(), grenzen, gelenk, sd))
                    : null;
            pose.pushPose();
            pose.translate(seite * teil.at()[0], teil.at()[1] + HOEHER, teil.at()[2] + (cape ? 1.25f : 0f));
            float spreiz = 22f - 10f * offen - schlag * amp * 0.25f;
            float heben = (falter ? 4f + 18f * offen : -48f + 62f * offen) + schlag * amp;
            pose.mulPose(Axis.YP.rotationDegrees(-seite * spreiz));
            pose.mulPose(Axis.ZP.rotationDegrees(seite * heben));
            pose.scale(teil.px(), teil.px(), teil.px());
            if (!federn || !FederFluegel.glied(pose, c, teil, grenzen, gelenk, 0, seite, licht, t, flatter, LEUCHT, GLITZER)) glied(pose, c, ft, 0, licht, t);
            if (knochen != null) zeichne(pose, c, knochen[0], licht, t);
            for (int k = 1; k < 3; k++) {
                pose.translate(seite * ft.breiten()[k - 1], 0, 0);
                float knick = (falter ? 3f : 16f - 12f * offen) * (k == 1 ? 1f : 0.75f);
                float nach = (float) Math.sin(phase - 0.8f * k) * amp * (falter ? 0.15f : 0.5f);
                pose.mulPose(Axis.YP.rotationDegrees(-seite * knick));
                pose.mulPose(Axis.ZP.rotationDegrees(seite * (nach - (k == 2 && !falter ? 8f * (1 - offen) : 0f))));
                if (!federn || !FederFluegel.glied(pose, c, teil, grenzen, gelenk, k, seite, licht, t, flatter, LEUCHT, GLITZER)) glied(pose, c, ft, k, licht, t);
                if (knochen != null) zeichne(pose, c, knochen[k], licht, t);
            }
            pose.popPose();
        }
    }

    /** Groesse der Schwingen am Spieler. */
    private static final float GROESSE = 0.85f;

    /**
     * Fluegel aus Schwingen (seit 4.88): Oberarm schraeg nach oben, Hand nach unten
     * abgeknickt -- in Ruhe stehen die Fluegel ueber den Schultern, die Schwungfedern haengen
     * bis zu den Knien. Offen (Springen, Fallen, Elytra-Flug) heben und spreizen sie sich,
     * beim Schleichen legen sie sich an den Ruecken.
     */
    private static void schwingen(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Teil teil, Schwingen.Stil stil,
                                  float t, float offen, float schlag, float phase, float amp, boolean cape) {
        boolean falter = stil.art() == Schwingen.Art.FALTER, haut = stil.art() == Schwingen.Art.HAUT;
        float[] len = Schwingen.laengen(stil);
        for (int seite = -1; seite <= 1; seite += 2) {
            Netz[] n = Schwingen.netze(stil, seite);
            pose.pushPose();
            pose.translate(seite * teil.at()[0], teil.at()[1] + HOEHER, teil.at()[2] + (cape ? 1.25f : 0f));
            pose.scale(GROESSE, GROESSE, GROESSE);
            if (falter) {
                float spreiz = 34f - 24f * offen - schlag * amp * 0.9f;
                pose.translate(-seite * 0.9f, -4.5f, 0);
                pose.mulPose(Axis.YP.rotationDegrees(-seite * spreiz));
                zeichne(pose, c, n[0], licht, t);
            } else {
                float spreiz = 34f - 20f * offen - schlag * amp * 0.25f;
                float heben = 4f + 36f * offen + schlag * amp;
                pose.mulPose(Axis.YP.rotationDegrees(-seite * spreiz));
                pose.mulPose(Axis.ZP.rotationDegrees(seite * heben));
                zeichne(pose, c, n[0], licht, t);
                float[] knickY = { 0, 22f - 16f * offen, 26f - 20f * offen };
                float[] knickZ = { 0, 10f - 4f * offen, haut ? -(18f - 12f * offen) : -(52f - 30f * offen) };
                for (int k = 1; k < 3; k++) {
                    pose.translate(seite * len[k - 1], 0, 0);
                    float nach = (float) Math.sin(phase - 0.8f * k) * amp * 0.5f;
                    pose.mulPose(Axis.YP.rotationDegrees(-seite * knickY[k]));
                    pose.mulPose(Axis.ZP.rotationDegrees(seite * (knickZ[k] + nach)));
                    zeichne(pose, c, n[k], licht, t);
                }
            }
            pose.popPose();
        }
    }

    /** Ein Fluegelglied: geglaettet (FlachBilder), solange das Bild noch rechnet wie bisher. */
    private static void glied(PoseStack pose, SubmitNodeCollector c, FluegelTeile ft, int k, int licht, float t) {
        int[][] f = ft.farben()[k];
        boolean[][] g = ft.glow()[k];
        Netz seiten = AUFNAHME != null ? null : FlachBilder.seiten(f, g, ft.tiefe(), ft.schnitte()[k]);
        if (seiten != null) {
            if (GLITZER) glitzern(pose, c, ft.netze()[k], t);
            pose.pushPose();
            pose.translate(ft.versatzX()[k], ft.versatzY(), 0);
            boolean ok = FlachBilder.flaechen(pose, c, f, g, ft.tiefe(), ft.schnitte()[k], licht, LEUCHT);
            if (ok) ohneGlitzer(pose, c, seiten, licht, t);
            pose.popPose();
            if (ok) return;
        }
        zeichne(pose, c, ft.netze()[k], licht, t);
    }

    /**
     * Flaches Teil (Pixelbild): Seitenwaende aus dem Pixel-Umriss, Vorder- und Rueckseite als
     * geglaettetes Bild (seit 4.81). Bis das Bild fertig ist, das alte Netz.
     */
    private static void flach(PoseStack pose, SubmitNodeCollector c, int[][] farben, boolean[][] glow, float tiefe, Netz alt, int licht, float t) {
        Netz seiten = AUFNAHME != null ? null : FlachBilder.seiten(farben, glow, tiefe, 0);
        if (seiten != null && FlachBilder.flaechen(pose, c, farben, glow, tiefe, 0, licht, LEUCHT)) {
            if (GLITZER) glitzern(pose, c, alt, t);
            ohneGlitzer(pose, c, seiten, licht, t);
            return;
        }
        zeichne(pose, c, alt, licht, t);
    }

    private static void ohneGlitzer(PoseStack pose, SubmitNodeCollector c, Netz netz, int licht, float t) {
        boolean g = GLITZER;
        GLITZER = false;
        try {
            zeichne(pose, c, netz, licht, t);
        } finally {
            GLITZER = g;
        }
    }

    private static float wert(float[] w, int i, float standard) {
        return i < w.length && !Float.isNaN(w[i]) ? w[i] : standard;
    }

    private void aura(PoseStack pose, SubmitNodeCollector c, Zubehoer.Aura au, float t, int seed, int dichte) {
        if (GrossAura.ist(au.bewegung())) { GrossAura.zeichnen(pose, c, au.bewegung(), t, seed, dichte); return; }
        if (au.sprites().isEmpty()) return;
        int n = Math.max(1, Math.round(au.anzahl() * (dichte == 1 ? 0.6f : dichte == 3 ? 1.5f : 1f)));
        if (au.bewegung().equals("twin")) n = 2;
        int licht = HELL;
        for (int i = 0; i < n; i++) {
            float r1 = zufall(seed * 31 + i * 7), r2 = zufall(seed * 17 + i * 13 + 3), r3 = zufall(seed * 5 + i * 29 + 11);
            Zubehoer.Teil sp = au.sprites().get(i % au.sprites().size());
            float x, y, z, groesse = 1f, drehY = 0, drehZ = 0, drehX = 0, fluegel = 1f;
            switch (au.bewegung()) {
                case "fall", "rise" -> {
                    float dauer = 90 + r1 * 40;
                    float ph = ((t / dauer) + i / (float) n + r2) % 1f;
                    float w = r3 * 6.283f + t * 0.012f;
                    float rad = 9 + r1 * 5;
                    y = au.bewegung().equals("fall") ? 12 - ph * 38 : -25 + ph * 38;
                    if (au.bewegung().equals("rise")) w += ph * 2.5f;
                    x = (float) Math.cos(w) * rad + (float) Math.sin(t * 0.05f + i) * 1.5f;
                    z = (float) Math.sin(w) * rad;
                    groesse = rampe(ph);
                    drehY = -w * 57.3f + 90;
                    drehZ = t * 3f * au.drehen() + i * 47;
                    drehX = (float) Math.sin(t * 0.07f + i) * 35f * au.drehen();
                }
                case "flutter" -> {
                    float w = t * 0.025f + i * 6.283f / n;
                    float rad = 11 + (float) Math.sin(t * 0.03f + i) * 2;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -6 + (float) Math.sin(t * 0.06f + i * 1.9f) * 7 + r1 * 6 - 3;
                    drehY = -w * 57.3f;
                    fluegel = 0.25f + 0.75f * Math.abs((float) Math.sin(t * 0.55f + i));
                }
                case "twin" -> {
                    float w = (i == 0 ? 1 : -1) * t * 0.07f + i * 3.1416f;
                    float rad = 12;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -6 + (float) Math.sin(t * 0.05f + i * 3.1416f) * 5;
                    drehY = -w * 57.3f - 90;
                }
                case "rings" -> {
                    float ph = ((t / 70f) + i / (float) n) % 1f;
                    x = 0; z = 0;
                    y = -25 + ph * 34;
                    drehX = 72;
                    drehY = t * 1.5f + i * 40;
                    groesse = rampe(ph) * (0.8f + 0.25f * (float) Math.sin(ph * 3.1416f));
                }
                case "twinkle", "flicker" -> {
                    float dauer = au.bewegung().equals("flicker") ? 22 + r1 * 10 : 50 + r1 * 30;
                    float zeit = t / dauer + r2;
                    int runde = (int) Math.floor(zeit);
                    float ph = zeit - runde;
                    float q1 = zufall(seed * 3 + i * 41 + runde * 101), q2 = zufall(seed * 7 + i * 43 + runde * 103), q3 = zufall(seed * 11 + i * 47 + runde * 107);
                    float w = q1 * 6.283f, rad = 8 + q2 * 6;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -22 + q3 * 30;
                    drehY = -w * 57.3f + 90;
                    if (au.bewegung().equals("flicker")) {
                        groesse = ph < 0.55f ? 1f : 0f;
                        drehZ = (q1 - 0.5f) * 40;
                    } else {
                        groesse = (float) Math.sin(ph * 3.1416f);
                        drehZ = t * 2f;
                    }
                }
                case "spiral" -> {
                    float ph = ((t / 110f) + i / (float) n) % 1f;
                    float w = ph * 12.566f + (i % 2) * 3.1416f + t * 0.01f;
                    float rad = 10 + (float) Math.sin(ph * 3.1416f) * 2;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -25 + ph * 38;
                    groesse = rampe(ph);
                    drehY = -w * 57.3f + 90;
                    drehZ = t * 2f * au.drehen();
                }
                case "halo" -> {
                    float w = t * 0.03f + i * 6.283f / n;
                    float rad = 5.2f;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = 11.5f + (float) Math.sin(t * 0.06f) * 0.6f;
                    drehY = -w * 57.3f + 90;
                    groesse = 0.75f + 0.25f * (float) Math.sin(t * 0.12f + i);
                }
                case "vortex" -> {
                    float ph = ((t / 55f) + i / (float) n + r1 * 0.1f) % 1f;
                    float w = t * 0.16f + i * 6.283f / n * 3 + ph * 7f;
                    float rad = 3 + ph * 11;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -25 + ph * 36;
                    groesse = rampe(ph) * (0.6f + 0.5f * ph);
                    drehY = -w * 57.3f + 90;
                    drehZ = t * 4f * au.drehen();
                }
                case "swarm" -> {
                    x = (float) Math.sin(t * 0.013f * (1 + r1) + r2 * 6.283f) * 11;
                    z = (float) Math.cos(t * 0.011f * (1 + r2) + r3 * 6.283f) * 11;
                    y = -8 + (float) Math.sin(t * 0.017f * (1 + r3) + r1 * 6.283f) * 12;
                    groesse = 0.35f + 0.65f * Math.max(0f, (float) Math.sin(t * 0.09f * (1 + r1) + i * 1.7f));
                    drehY = t * 2f + i * 40;
                }
                case "atom" -> {
                    int ebene = i % 3;
                    float w = t * 0.06f * (ebene == 1 ? -1 : 1) + i * 2.399f;
                    float rad = 11;
                    float px0 = (float) Math.cos(w) * rad, pz0 = (float) Math.sin(w) * rad;
                    float kipp = (float) Math.toRadians(62);
                    float py0 = pz0 * (float) Math.sin(kipp);
                    pz0 = pz0 * (float) Math.cos(kipp);
                    float drehung = ebene * 2.094f;
                    x = px0 * (float) Math.cos(drehung) - pz0 * (float) Math.sin(drehung);
                    z = px0 * (float) Math.sin(drehung) + pz0 * (float) Math.cos(drehung);
                    y = -6 + py0;
                    drehY = t * 3f + i * 30;
                }
                case "rain" -> {
                    float dauer = 26 + r1 * 10;
                    float zeit = t / dauer + r2;
                    int runde = (int) Math.floor(zeit);
                    float ph = zeit - runde;
                    float q1 = zufall(seed * 13 + i * 53 + runde * 109), q2 = zufall(seed * 19 + i * 59 + runde * 113);
                    float w = q1 * 6.283f, rad = 4 + q2 * 9;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = 16 - ph * 42;
                    groesse = rampe(ph);
                    drehY = -w * 57.3f + 90;
                }
                default -> {
                    float w = t * 0.035f + i * 6.283f / n;
                    float rad = 10.5f + r1 * 2;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -10 + (float) Math.sin(t * 0.045f + i * 2.1f) * 8 + (r2 - 0.5f) * 6;
                    drehY = -w * 57.3f + 90;
                    drehZ = (float) Math.sin(t * 0.08f + i) * 15 * au.drehen();
                }
            }
            if (groesse <= 0.02f) continue;
            float s = au.px() * au.groesse() * groesse;
            String bw = au.bewegung();
            boolean kreuz = !(bw.equals("rings") || bw.equals("flutter") || bw.equals("twin"));
            if (AUFNAHME == null && SpriteBilder.zeichnen(pose, c, sp, x, y, z, s, drehY, drehX, drehZ, fluegel, kreuz)) continue;
            for (int k = 0; k < (kreuz ? 2 : 1); k++) {
                pose.pushPose();
                pose.translate(x, y, z);
                pose.mulPose(Axis.YP.rotationDegrees(drehY + k * 90));
                if (drehX != 0) pose.mulPose(Axis.XP.rotationDegrees(drehX));
                if (drehZ != 0) pose.mulPose(Axis.ZP.rotationDegrees(drehZ));
                pose.scale(s * fluegel, s, s);
                zeichne(pose, c, Zubehoer.netz(sp), licht, t);
                pose.popPose();
            }
        }
    }

    /** Ein- und Ausblenden am Anfang und Ende der Bahn (ueber die Groesse). */
    private static float rampe(float ph) {
        float a = Math.min(1f, ph / 0.12f), b = Math.min(1f, (1 - ph) / 0.12f);
        return Math.min(a, b);
    }

    private static float zufall(int s) {
        int x = s * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return (x & 0xFFFF) / 65535f;
    }

    /** Glitzern fuer das, was gerade gezeichnet wird (Render-Thread, je Cosmetic gesetzt). */
    private static boolean GLITZER;
    /** Staerke des Leuchtens (1 = wie gebaut) und einzeln funkelnde Flaechen -- je Cosmetic gesetzt. */
    private static float LEUCHT = 1f;
    private static boolean FUNKEL = false;
    private static float FUNKEL_T = 0f;

    /** Leucht-Effekt eines Bandanas fuer diesen Moment einstellen. */
    private static void leuchtModus(String fx, float t) {
        LEUCHT = 1f; FUNKEL = false;
        if (fx == null || fx.isEmpty()) return;
        switch (fx) {
            case "pulse" -> LEUCHT = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(t * 0.14f));
            case "flash" -> {
                int fenster = (int) Math.floor(t / 9f);
                float ph = (t / 9f) - fenster;
                boolean blitz = zufall(fenster * 97 + 13) < 0.32f;
                LEUCHT = 0.35f + (blitz ? 1.5f * (float) Math.exp(-ph * 5f) * (ph < 0.12f || ph > 0.22f ? 1f : 0.3f) : 0f);
            }
            case "twinkle" -> { FUNKEL = true; FUNKEL_T = t; }
            default -> {}
        }
    }

    private static final java.util.Map<Netz, float[]> RAHMEN = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Groesse eines Netzes (min x/y, Breite, Hoehe) -- fuer den Glanzstreifen. */
    private static float[] rahmen(Netz n) {
        return RAHMEN.computeIfAbsent(n, k -> {
            float x0 = 1e9f, y0 = 1e9f, x1 = -1e9f, y1 = -1e9f;
            for (Netz.Flaeche f : k.flaechen) for (int i = 0; i < 4; i++) {
                x0 = Math.min(x0, f.p[i * 3]); x1 = Math.max(x1, f.p[i * 3]);
                y0 = Math.min(y0, f.p[i * 3 + 1]); y1 = Math.max(y1, f.p[i * 3 + 1]);
            }
            return new float[] { x0, y0, Math.max(1e-3f, x1 - x0), Math.max(1e-3f, y1 - y0), (k.flaechen.size() * 7919) % 997 };
        });
    }

    /**
     * Glitzern (seit 4.35): ein heller Lichtstreif wandert schraeg ueber das Teil (alle ~5 s),
     * dazwischen blitzen einzelne Flaechen kurz auf -- wie der Glanz und die Funkel-Sterne
     * auf den Capes. Als leuchtende Ebene ueber dem Teil, also auch nachts sichtbar.
     */
    private static void glitzern(PoseStack pose, SubmitNodeCollector c, Netz netz, float t) {
        float[] b = rahmen(netz);
        float pos = (t + b[4]) % 100f / 100f * 2.6f - 0.4f;
        c.submitCustomGeometry(pose, RenderTypes.eyes(Weiss.ID), (p, vc) -> {
            int nr = 0;
            int[] k = new int[4];
            for (Netz.Flaeche f : netz.flaechen) {
                nr++;
                float h = zufall(nr * 7919 + (int) b[4]);
                float funkel = 0;
                if (h < 0.3f) {
                    float zyklus = 45f + h * 160f;
                    float ph = ((t + h * 977f) % zyklus) / zyklus;
                    if (ph < 0.14f) funkel = (float) Math.sin(ph / 0.14f * Math.PI);
                }
                boolean da = false;
                for (int i = 0; i < 4; i++) {
                    float u = (f.p[i * 3] - b[0]) / b[2] * 0.6f + (f.p[i * 3 + 1] - b[1]) / b[3];
                    float d = (u - pos) / 0.16f;
                    float a = Math.min(1f, (float) Math.exp(-d * d) * 0.6f + funkel * 0.9f);
                    if (a > 0.02f) da = true;
                    int fc = f.farbe[i];
                    int rr = ((fc >> 16) & 255) + (255 - ((fc >> 16) & 255)) * 2 / 3, gg = ((fc >> 8) & 255) + (255 - ((fc >> 8) & 255)) * 2 / 3, bb = (fc & 255) + (255 - (fc & 255)) * 2 / 3;
                    k[i] = (Math.round(a * 255) << 24) | (rr << 16) | (gg << 8) | bb;
                }
                if (da) flaeche(p, vc, f, HELL, k);
            }
        });
    }

    private static void zeichne(PoseStack pose, SubmitNodeCollector c, Netz netz, int licht, float t) {
        Aufnahme a = AUFNAHME;
        if (a != null) {
            org.joml.Matrix4f m = pose.last().pose();
            for (Netz.Flaeche f : netz.flaechen) a.flaeche(m, f);
            return;
        }
        if (GLITZER) glitzern(pose, c, netz, t);
        boolean leuchtet = false;
        for (Netz.Flaeche f : netz.flaechen) if (f.leuchten > 0.3f) { leuchtet = true; break; }
        c.submitCustomGeometry(pose, RenderTypes.entitySolid(Weiss.ID), (p, vc) -> {
            for (Netz.Flaeche f : netz.flaechen) flaeche(p, vc, f, f.leuchten > 0.3f ? HELL : licht, f.farbe);
        });
        if (leuchtet) {
            final float leucht = LEUCHT, ft = FUNKEL_T;
            final boolean funkel = FUNKEL;
            c.submitCustomGeometry(pose, Leuchten.typ(Weiss.ID), (p, vc) -> {
                for (Netz.Flaeche f : netz.flaechen) {
                    if (f.leuchten <= 0.3f) continue;
                    int[] k = new int[4];
                    float st = f.leuchten * 0.7f * leucht;
                    if (funkel) {
                        float h = zufall(System.identityHashCode(f));
                        st *= 0.25f + 0.75f * Math.max(0f, (float) Math.sin(ft * (0.12f + h * 0.15f) + h * 6.283f));
                    }
                    int alpha = Math.max(0, Math.min(255, Math.round(st * 255)));
                    if (alpha == 0) continue;
                    for (int i = 0; i < 4; i++) k[i] = 0xFF000000 | (Netz.mische(0xFF000000, f.farbe[i], alpha / 255f * 0.5f) & 0xFFFFFF);
                    flaeche(p, vc, f, HELL, k);
                }
            });
        }
    }

    private static void flaeche(PoseStack.Pose p, VertexConsumer vc, Netz.Flaeche f, int licht, int[] farbe) {
        for (int i : REIHE) {
            vc.addVertex(p, f.p[i * 3], f.p[i * 3 + 1], f.p[i * 3 + 2]).setColor(farbe[i] | (farbe == f.farbe ? 0xFF000000 : 0)).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(licht).setNormal(p, f.n[i * 3], f.n[i * 3 + 1], f.n[i * 3 + 2]);
        }
    }
}
