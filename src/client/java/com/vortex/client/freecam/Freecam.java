package com.vortex.client.freecam;

import com.mojang.blaze3d.platform.InputConstants;
import com.vortex.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Verwaltet den Freecam-Zustand: ob aktiv, die freie Kamera-Position und die
 * Bewegung per WASD/Leertaste/Shift.
 *
 * WICHTIG fuer fluessige Bewegung: Die Position wird pro RENDER-FRAME aktualisiert
 * (nicht pro Tick), mit Delta-Zeit-Skalierung. So ist die Bewegung bei jeder
 * Framerate gleich schnell und ruckelt nicht. Der CameraMixin ruft updateFrame()
 * jeden Frame auf.
 *
 * Die Rotation kommt aus der normalen Spieler-Blickrichtung (die Maus dreht also
 * die Kamera), die Position ist unabhaengig.
 *
 * Seit 4.15 wieder der schlanke Stand von vor 4.8 (auf Wunsch): keine
 * Info-Zeile, kein Mausrad-Tempo, keine Extra-Flugarten ausser "Smooth
 * Movement", kein Abschalten bei Schaden. Geblieben sind "Show Player",
 * F5 in der Freecam, die bessere Sicht unter Tage -- und der Spieler wird
 * NICHT mehr eingefroren (Sprung und Elytra-Flug laufen normal weiter).
 */
public final class Freecam {

    private static boolean active = false;
    private static double x, y, z;
    private static Object freecamWelt = null, freecamSpieler = null;
    private static double velX, velY, velZ;

    private static FreeCamera cameraEntity = null;

    private static float yaw = 0f;
    private static float pitch = 0f;

    private static final double SPEED = 10.0;
    private static final double SPRINT_MULT = 3.0;
    private static final double DAMPING_PER_SEC = 0.0025;

    private static long lastFrameNano = 0L;



    private Freecam() {}

    public static boolean isActive() {
        return active;
    }

    public static Vec3 getPos() {
        return new Vec3(x, y, z);
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    /**
     * Dreht die Freecam-Blickrichtung (von der Maus aufgerufen). cursorDeltaX/Y
     * kommen aus changeLookDirection schon sensitivity-skaliert; Vanilla
     * multipliziert danach intern mit 0.15. Genau diesen Faktor wenden wir hier
     * an, damit die Freecam-Empfindlichkeit der normalen Spiel-Sensitivitaet
     * entspricht.
     */
    public static void addRotation(double cursorDeltaX, double cursorDeltaY) {
        yaw += (float) (cursorDeltaX * 0.15);
        pitch += (float) (cursorDeltaY * 0.15);
        if (pitch > 90f) pitch = 90f;
        if (pitch < -90f) pitch = -90f;
        yaw %= 360f;
    }

    /** Schaltet die Freecam an/aus. Beim Anschalten startet sie an der Spielerposition. */
    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        active = !active;
        if (active) {
            Vec3 eye = mc.player.getEyePosition();
            x = eye.x; y = eye.y; z = eye.z;
            velX = velY = velZ = 0;
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            lastFrameNano = System.nanoTime();
            spawnCameraEntity(mc);
            gesundheitBeimStart = mc.player.getHealth();
            seitAfk = System.currentTimeMillis();
            warGeduckt = mc.player.isShiftKeyDown() || mc.player.isCrouching();
        } else {
            removeCameraEntity(mc);
        }
        chunksNeuLaden(mc);
    }

    /** Gesundheit beim Einschalten -- "Toggle On Damage" vergleicht damit. */
    private static float gesundheitBeimStart;
    /** Wann zuletzt ein Anti-AFK-Dreher geschickt wurde. */
    private static long seitAfk;

    /**
     * "Reload Chunks": nach dem Umschalten alle Chunks neu aufbauen lassen.
     * Sonst bleiben Hoehlen, die vom Spieler aus verdeckt waren, aus der
     * Freecam heraus manchmal unsichtbar (Minecrafts Sichtbarkeitsberechnung
     * gilt noch fuer den alten Standpunkt).
     */
    private static void chunksNeuLaden(Minecraft mc) {
        if (!schalter("Reload Chunks", false)) return;
        try {
            //#if 26.2
            mc.execute(mc.levelExtractor::allChanged);
            //#else
            //$ mc.execute(mc.levelRenderer::allChanged);
            //#endif
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("Freecam.reload", e);
        }
    }

    /** Erstellt die stumme Kamera-Entity und macht sie zur aktiven Kamera. */
    private static void spawnCameraEntity(Minecraft mc) {
        boolean brauchtKamera = schalter("Render Anchor", false);
        if (!brauchtKamera) {
            cameraEntity = null;
            return;
        }
        try {
            if (mc.level == null || mc.getConnection() == null) return;
            cameraEntity = new FreeCamera();
            cameraEntity.snapTo(x, y, z, yaw, pitch);
            cameraEntity.spawn();
            mc.setCameraEntity(cameraEntity);
        } catch (Throwable t) {
            cameraEntity = null;
        }
    }

    /** Entfernt die Kamera-Entity und setzt die Kamera zurueck auf den Spieler. */
    private static void removeCameraEntity(Minecraft mc) {
        try {
            mc.setCameraEntity(mc.player);
            if (cameraEntity != null) {
                cameraEntity.despawn();
            }
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("Freecam", pvpErr);
            } finally {
            cameraEntity = null;
        }
    }

    public static void disable() {
        if (!active) return;
        active = false;
        removeCameraEntity(Minecraft.getInstance());
    }

    /**
     * Freecam beenden UND das Modul ausschalten.
     *
     * Nur disable() reichte nicht: das Modul blieb an, und der Client schaltete
     * die Kamera im naechsten Tick wieder ein (VortexClientMod gleicht beide ab).
     * Nach dem Tod lief das jeden Tick im Kreis.
     */
    public static void beenden(String grund) {
        disable();
        try {
            com.vortex.client.module.Module m = module();
            if (m != null && m.isEnabled()) m.setEnabled(false);
        } catch (Throwable ignored) { }
        Minecraft mc = Minecraft.getInstance();
        if (grund != null && mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7d[Freecam] \u00a7f" + grund));
        }
    }

    /** Freecam: Verdeckungsberechnung aus (FreecamCullMixin). */
    public static boolean ohneCulling() {
        return active && schalter("No Culling", true);
    }

    public static boolean ohneNebel() { return false; }
    public static boolean ohneOverlays() { return false; }
    /**
     * Geduckt bleiben (seit 4.54, abschaltbar: "Keep Sneaking"): war der Spieler beim
     * Einschalten der Freecam geduckt, bleibt er es, bis die Freecam aus ist -- auch wenn
     * man Shift loslaesst (Shift fliegt in der Freecam nach unten). Sonst stand er an
     * einer Kante ploetzlich auf und fiel hinunter.
     */
    public static boolean schleichen() { return active && warGeduckt && schalter("Keep Sneaking", true); }

    private static boolean warGeduckt;
    /**
     * Mausrad in der Freecam (seit 4.32, abschaltbar: "Scroll Speed"): regelt
     * die Fluggeschwindigkeit statt den Hotbar-Platz zu wechseln.
     */
    public static boolean onScroll(double amount) {
        if (!active || amount == 0 || !schalter("Scroll Speed", true)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return false;
        com.vortex.client.module.Module m = module();
        if (m == null) return false;
        for (com.vortex.client.core.setting.Setting st : m.getSettings()) {
            if (st.getName().equals("Speed") && st instanceof com.vortex.client.core.setting.NumberSetting n) {
                double f = amount > 0 ? 1.15 : 1 / 1.15;
                double neu = Math.max(n.getMin(), Math.min(n.getMax(), n.get() * f));
                n.set(Math.round(neu * 10) / 10.0);
                if (mc.player != null) {
                    mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                            "Freecam: " + String.format(java.util.Locale.ROOT, "%.1f", n.get()) + " blocks/s"));
                }
                return true;
            }
        }
        return false;
    }

    /** Aufhellung in der Freecam (GammaMixin) -- wie frueher immer an. */
    public static boolean hell() {
        return active;
    }

    /**
     * Sicherheitsnetz gegen "Spieler haengt fest".
     *
     * Wenn die Freecam AUS ist, muss der echte Spieler die aktive Kamera sein.
     * Bleibt aus irgendeinem Grund (Fehler beim Beenden, Weltwechsel, Tod) eine
     * andere Kamera-Entity gesetzt, gilt der Spieler intern nicht als "Kamera" --
     * daran haengt u.a. das Senden der Bewegungspakete, und man steht fest.
     * Dieser Tick-Check setzt das automatisch zurueck.
     */
    public static void registerSafety() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
                .END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;

            if (active) {
                if (freecamWelt == null) { freecamWelt = mc.level; freecamSpieler = mc.player; }
                if (mc.level != freecamWelt || mc.player != freecamSpieler || mc.player.isDeadOrDying()) {
                    freecamWelt = null;
                    freecamSpieler = null;
                    beenden(mc.player.isDeadOrDying() ? "Off -- you died." : null);
                    return;
                }
            } else {
                freecamWelt = null;
                freecamSpieler = null;
            }

            if (active) {
                if (schalter("Toggle On Damage", false) && mc.player.getHealth() < gesundheitBeimStart - 0.01f) {
                    beenden("Off -- you took damage.");
                    return;
                }
                gesundheitBeimStart = Math.max(gesundheitBeimStart, mc.player.getHealth());
                gesundheitBeimStart = Math.min(gesundheitBeimStart, mc.player.getHealth() + 0.5f);
                if (schalter("Anti AFK", false) && System.currentTimeMillis() - seitAfk > 45_000) {
                    seitAfk = System.currentTimeMillis();
                    float dreh = (mc.player.tickCount & 1) == 0 ? 0.1f : -0.1f;
                    mc.player.setYRot(mc.player.getYRot() + dreh);
                }
                return;
            }

            if (mc.getCameraEntity() != mc.player) {
                mc.setCameraEntity(mc.player);
            }
        });
    }

    /**
     * Pro RENDER-FRAME aufrufen (aus dem CameraMixin): liest Eingaben, bewegt die
     * Freecam mit Delta-Zeit. So ist die Bewegung fluessig und framerate-unabhaengig.
     */
    public static void updateFrame() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { active = false; return; }

        long now = System.nanoTime();
        double dt = (now - lastFrameNano) / 1_000_000_000.0;
        lastFrameNano = now;
        if (dt <= 0) return;
        if (dt > 0.1) dt = 0.1;

        boolean brauchtJetzt = schalter("Render Anchor", false);
        if (brauchtJetzt && cameraEntity == null) {
            spawnCameraEntity(mc);
        } else if (!brauchtJetzt && cameraEntity != null) {
            removeCameraEntity(mc);
        }

        if (schalter("Rotate Player", false) && mc.player != null) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            mc.player.setYHeadRot(yaw);
            mc.player.yHeadRotO = yaw;
        }

        boolean inputAllowed = (mc.gui.screen() == null);

        double accel = 0;
        double fx = 0, fy = 0, fz = 0, rx = 0, rz = 0;
        boolean up = false, down = false;
        boolean anyMove = false;

        if (inputAllowed) {
            boolean fwd   = taste(mc, mc.options.keyUp, org.lwjgl.glfw.GLFW.GLFW_KEY_W);
            boolean back  = taste(mc, mc.options.keyDown, org.lwjgl.glfw.GLFW.GLFW_KEY_S);
            boolean left  = taste(mc, mc.options.keyLeft, org.lwjgl.glfw.GLFW.GLFW_KEY_A);
            boolean right = taste(mc, mc.options.keyRight, org.lwjgl.glfw.GLFW.GLFW_KEY_D);
            up    = taste(mc, mc.options.keyJump, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
            down  = taste(mc, mc.options.keyShift, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);

            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            fx = -Math.sin(yawRad) * Math.cos(pitchRad);
            fy = -Math.sin(pitchRad);
            fz =  Math.cos(yawRad) * Math.cos(pitchRad);
            rx = -Math.cos(yawRad);
            rz = -Math.sin(yawRad);

            double speed = zahl("Speed", SPEED);
            double sprintFactor = zahl("Sprint Multiplier", SPRINT_MULT);
            if (mc.options.keySprint.isDown()) speed *= sprintFactor;
            accel = speed;

            double tvx = 0, tvy = 0, tvz = 0;
            if (fwd)   { tvx += fx; tvy += fy; tvz += fz; anyMove = true; }
            if (back)  { tvx -= fx; tvy -= fy; tvz -= fz; anyMove = true; }
            if (right) { tvx += rx; tvz += rz; anyMove = true; }
            if (left)  { tvx -= rx; tvz -= rz; anyMove = true; }
            if (up)    { tvy += 1; anyMove = true; }
            if (down)  { tvy -= 1; anyMove = true; }

            double len = Math.sqrt(tvx*tvx + tvy*tvy + tvz*tvz);
            if (len > 0.0001) {
                tvx = tvx/len * speed;
                tvy = tvy/len * speed;
                tvz = tvz/len * speed;
            }
            if (schalter("Smooth Movement", false)) {
                double k = 1.0 - Math.exp(-4.0 * dt);
                velX += (tvx - velX) * k; velY += (tvy - velY) * k; velZ += (tvz - velZ) * k;
                anyMove = true;
            } else {
                velX = tvx; velY = tvy; velZ = tvz;
            }
        }

        if (!anyMove) {
            double factor = Math.pow(DAMPING_PER_SEC, dt);
            velX *= factor; velY *= factor; velZ *= factor;
        }

        x += velX * dt;
        y += velY * dt;
        z += velZ * dt;

        if (cameraEntity != null) {
            cameraEntity.snapTo(x, y, z, yaw, pitch);
            cameraEntity.xOld = x;
            cameraEntity.yOld = y;
            cameraEntity.zOld = z;
        }
    }

    private static boolean isDown(Minecraft mc, int key) {
        return InputConstants.isKeyDown(mc.getWindow(), key);
    }

    /**
     * Die Taste, die der Spieler in den Steuerungs-Optionen belegt hat (AZERTY,
     * ESDF ...), nicht fest W/A/S/D. Direkt am Fenster abgefragt, weil die
     * Freecam die Tastenbelegungen des Spielers absichtlich neutralisiert.
     */
    private static boolean taste(Minecraft mc, net.minecraft.client.KeyMapping km, int fallback) {
        try {
            InputConstants.Key key = InputConstants.getKey(km.saveString());
            if (key.getType() == InputConstants.Type.KEYSYM) return isDown(mc, key.getValue());
            if (key.getType() == InputConstants.Type.MOUSE) {
                return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
        } catch (Throwable ignored) { }
        return isDown(mc, fallback);
    }

    /** Liefert das Freecam-Modul (oder null). */
    /**
     * Das Freecam-Modul, falls geladen.
     *
     * Gesucht wird ueber den NAMEN, nicht die Klasse: das Modul liegt seit
     * dem Umzug im Addon. Waere der Klassenname hier fest verdrahtet, liesse
     * sich der Client ohne Addon nicht mehr uebersetzen. Ohne Addon bleibt
     * die Kamera einfach aus.
     */
    public static com.vortex.client.module.Module module() {
        try {
            for (com.vortex.client.module.Module m
                    : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                if ("Freecam".equals(m.getName())) return m;
            }
        } catch (Throwable ignored) { }
        return null;
    }


    private static boolean schalter(String name, boolean standard) {
        try {
            com.vortex.client.module.Module m = module();
            if (m == null) return standard;
            for (com.vortex.client.core.setting.Setting st : m.getSettings()) {
                if (st.getName().equals(name)
                        && st instanceof com.vortex.client.core.setting.BooleanSetting b) {
                    return b.get();
                }
            }
        } catch (Throwable ignored) { }
        return standard;
    }

    private static double zahl(String name, double standard) {
        try {
            com.vortex.client.module.Module m = module();
            if (m == null) return standard;
            for (com.vortex.client.core.setting.Setting st : m.getSettings()) {
                if (st.getName().equals(name)
                        && st instanceof com.vortex.client.core.setting.NumberSetting n) {
                    return n.get();
                }
            }
        } catch (Throwable ignored) { }
        return standard;
    }

    /**
     * Soll der eigene Spieler in der Freecam gezeichnet werden?
     *
     * Wird vom EntityRenderShouldRenderMixin abgefragt. Standard AN: sonst
     * weiss man nicht, wo der eigene Koerper steht, und fliegt beim Beenden
     * ueberraschend dorthin zurueck.
     */
    public static boolean zeigeSpieler() {
        return schalter("Show Player", true);
    }

}
