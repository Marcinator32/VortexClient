package com.vortex.client.macro;

import com.mojang.blaze3d.platform.InputConstants;
import com.vortex.client.core.Errors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Records and plays back macros.
 *
 * RECORDING captures what you actually do: clicks, keys held down, hotbar
 * switches, and the pauses between them. Timing is taken from the wall clock,
 * not from ticks, so a recording keeps its rhythm even if the frame rate moves
 * around.
 *
 * PLAYBACK walks the list step by step. Each step waits out its delay, then
 * runs, using the same calls the game itself uses for a click — so from the
 * server's point of view it looks like an ordinary action.
 *
 * ON DETECTION, PLAINLY: playback adds a little random spread to each delay,
 * because identical gaps are the single most obvious sign of automation. That
 * makes a macro look less mechanical. It does not make it invisible, and
 * nothing here is built to defeat any particular anti-cheat. A macro that
 * fights for you is automation, servers treat it as such, and the risk of a
 * ban is real.
 */
public final class MacroManager {

    private static final List<Macro> MACROS = new ArrayList<>();

    private static Macro recording = null;
    private static long lastEventTime = 0L;

    private static Macro playing = null;
    private static int stepIndex = 0;
    /** How many passes are done, for the repeat count. */
    private static int passesDone = 0;
    /**
     * When the next step is due, in nanoseconds.
     *
     * Milliseconds were too coarse: a delay of one was rounded up to a whole
     * tick, so the macro managed a single step every fifty milliseconds no
     * matter what was configured. A keyboard queues about fifty presses in
     * that time, and the game works through all of them at once -- which is
     * exactly why the hardware macro felt so much faster.
     */
    private static long nextStepAt = 0L;
    private static int heldKey = GLFW.GLFW_KEY_UNKNOWN;
    private static long releaseHeldAt = 0L;

    /** Which macro keys were down last tick, for edge detection. */
    private static final java.util.Map<String, Boolean> keyDown = new java.util.HashMap<>();

    private static final java.util.Random RANDOM = new java.util.Random();

    private MacroManager() {}

    public static synchronized List<Macro> all() {
        return MACROS;
    }

    public static synchronized Macro create(String name) {
        Macro m = new Macro((name == null || name.isBlank()) ? "Macro " + (MACROS.size() + 1) : name.trim());
        MACROS.add(m);
        return m;
    }

    public static synchronized void remove(Macro m) {
        if (playing == m) stop();
        if (recording == m) recording = null;
        MACROS.remove(m);
    }

    public static boolean isRecording() {
        return recording != null;
    }

    public static Macro recordingMacro() {
        return recording;
    }

    /** Starts recording into a macro, replacing whatever it held. */
    public static synchronized void startRecording(Macro m) {
        stop();
        recording = m;
        m.steps.clear();
        lastEventTime = System.currentTimeMillis();
    }

    public static synchronized void stopRecording() {
        recording = null;
    }

    /**
     * Records one action.
     *
     * The delay stored is the time since the previous action, which is what
     * makes a recording play back at the speed it was performed.
     */
    public static synchronized void record(Macro.Action action, int value, int hold) {
        if (recording == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui.screen() != null) return;

        long now = System.currentTimeMillis();
        int delay = (int) Math.min(now - lastEventTime, 60_000L);
        lastEventTime = now;
        if (recording.steps.isEmpty()) delay = 0;
        recording.steps.add(new Macro.Step(action, value, delay, hold));
    }

    public static boolean isPlaying() {
        return playing != null;
    }

    public static Macro playingMacro() {
        return playing;
    }

    public static synchronized void play(Macro m) {
        if (m == null || m.steps.isEmpty()) return;
        stopRecording();
        playing = m;
        stepIndex = 0;
        passesDone = 0;
        nextStepAt = System.nanoTime() + Math.max(0, m.startDelay) * 1_000_000L;
    }

    public static synchronized void stop() {
        releaseHeldKey();
        playing = null;
        stepIndex = 0;
    }

    public static synchronized void toggle(Macro m) {
        if (playing == m) {
            stop();
        } else {
            play(m);
        }
    }

    public static void register() {
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES
                .register(context -> {
            try {
                Minecraft client = Minecraft.getInstance();
                if (client != null) tickPlayback(client);
            } catch (Throwable pvpErr) {
                Errors.report("MacroManager.frame", pvpErr);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long t0 = System.nanoTime();
            try {
                tickRecording(client);
                tickKeys(client);
                tickPlayback(client);
            } catch (Throwable pvpErr) {
                Errors.report("MacroManager", pvpErr);
            } finally {
                com.vortex.client.core.Profiler.record("Macros", System.nanoTime() - t0);
            }
        });
    }

    private static final java.util.Map<Integer, Long> recHeld = new java.util.HashMap<>();
    private static int recLastSlot = -1;

    /**
     * Watches keys and the hotbar while recording.
     *
     * Keys are stored with how long they were held, because "walk forward for
     * 400 ms" is a very different step from "tap forward". The hotbar is
     * recorded whenever the selected slot changes.
     */
    private static void tickRecording(Minecraft client) {
        if (recording == null || client.player == null) {
            recHeld.clear();
            recLastSlot = -1;
            return;
        }
        if (client.gui.screen() != null) return;

        long now = System.currentTimeMillis();
        for (int code = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
             code <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST; code++) {

            if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) continue;

            boolean down;
            try {
                down = InputConstants.isKeyDown(client.getWindow(), code);
            } catch (Throwable pvpErr) {
                continue;
            }

            Long since = recHeld.get(code);
            if (down && since == null) {
                recHeld.put(code, now);
            } else if (!down && since != null) {
                recHeld.remove(code);
                if (code >= org.lwjgl.glfw.GLFW.GLFW_KEY_1
                        && code <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
                    continue;
                }
                record(Macro.Action.KEY, code, (int) Math.min(now - since, 30_000L));
            }
        }

        for (int b = 2; b <= 7; b++) {
            int code = MOUSE_BASE + b;
            boolean down = isDown(client, code);
            Long since = recHeld.get(code);
            if (down && since == null) {
                recHeld.put(code, now);
            } else if (!down && since != null) {
                recHeld.remove(code);
                record(Macro.Action.KEY, code, (int) Math.min(now - since, 30_000L));
            }
        }

        int slot = client.player.getInventory().getSelectedSlot();
        if (recLastSlot < 0) {
            recLastSlot = slot;
        } else if (slot != recLastSlot) {
            recLastSlot = slot;
            record(Macro.Action.SLOT, slot + 1, 0);
        }
    }

    /** Starts and stops macros by their assigned keys. */
    private static void tickKeys(Minecraft client) {
        if (client.gui.screen() != null) {
            keyDown.clear();
            return;
        }
        for (Macro m : all()) {
            if (m.key == GLFW.GLFW_KEY_UNKNOWN) continue;
            boolean down = isDown(client, m.key);
            boolean was = Boolean.TRUE.equals(keyDown.get(m.name));

            switch (m.trigger) {
                case HOLD:
                    if (down && playing != m) {
                        play(m);
                    } else if (!down && playing == m) {
                        stop();
                    }
                    break;
                case ONCE:
                    if (down && !was) play(m);
                    break;
                case TOGGLE:
                default:
                    if (down && !was) toggle(m);
                    break;
            }
            keyDown.put(m.name, down);
        }
    }

    /** Runs the steps of the macro currently playing. */
    private static void tickPlayback(Minecraft client) {
        if (playing == null) return;
        if (client.player == null || client.level == null) {
            stop();
            return;
        }

        long now = System.nanoTime();

        if (heldKey != GLFW.GLFW_KEY_UNKNOWN && System.currentTimeMillis() >= releaseHeldAt) {
            releaseHeldKey();
        }

        if (client.gui.screen() != null) return;

        int budget = 100;

        while (playing != null && now >= nextStepAt && budget-- > 0) {
            if (stepIndex >= playing.steps.size()) {
                passesDone++;
                boolean again = (playing.repeat == 0) || (passesDone < playing.repeat);
                if (playing.trigger == Macro.Trigger.HOLD) again = true;
                if (playing.trigger == Macro.Trigger.ONCE) again = false;
                if (again) {
                    stepIndex = 0;
                } else {
                    stop();
                    return;
                }
            }
            Macro.Step step = playing.steps.get(stepIndex);
            stepIndex++;

            runStep(client, step);

            int speed = Math.max(10, Math.min(400, playing.speed));
            int base = (int) Math.round(step.delay * 100.0 / speed);
            int spread = Math.max(0, playing.jitter);
            int varied = base;
            if (spread > 0 && base > 0) {
                int range = Math.max(1, base * spread / 100);
                varied = base + RANDOM.nextInt(range * 2 + 1) - range;
                if (varied < 0) varied = 0;
            }
            nextStepAt = now + varied * 1_000_000L;
            now = System.nanoTime();
        }
    }

    private static void runStep(Minecraft client, Macro.Step step) {
        try {
            switch (step.action) {
                case LEFT_CLICK:
                    ((com.vortex.client.mixin.client.MinecraftClientAccessor) client)
                            .pvpclient$invokeDoAttack();
                    break;

                case RIGHT_CLICK:
                    ((com.vortex.client.mixin.client.MinecraftClientAccessor) client)
                            .vortex$invokeDoItemUse();
                    break;

                case SLOT: {
                    int slot = Math.max(1, Math.min(9, step.value)) - 1;
                    client.player.getInventory().setSelectedSlot(slot);
                    break;
                }

                case KEY:
                    holdKey(client, step.value, step.hold);
                    break;

                case WAIT:
                default:
                    break;
            }
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.step", pvpErr);
        }
    }

    /**
     * Feeds a single press into a key binding.
     *
     * onKeyPressed is what the keyboard handler calls when a key really goes
     * down: it queues one press, which the game then works through on its next
     * tick, with everything it normally does. The result is indistinguishable
     * from a key that was actually pressed -- because as far as the game is
     * concerned, one was.
     */
    private static void pressBinding(net.minecraft.client.KeyMapping binding) {
        if (binding == null) return;
        try {
            net.minecraft.client.KeyMapping.click(
                    InputConstants.getKey(binding.saveString()));
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.press", pvpErr);
        }
    }

    /**
     * Holds a key down for a while.
     *
     * Done through the game's own key bindings rather than by faking input at
     * the window level: that way movement, sneaking and jumping behave exactly
     * as if the key were really down, including everything the game does about
     * it.
     */
    private static void holdKey(Minecraft client, int code, int ms) {
        releaseHeldKey();
        try {
            net.minecraft.client.KeyMapping.set(keyOf(code), true);
            heldKey = code;
            releaseHeldAt = System.currentTimeMillis() + Math.max(20, ms);
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.holdKey", pvpErr);
        }
    }

    /**
     * Codes at or above this belong to the mouse: MOUSE_BASE + button number.
     *
     * Keyboard and mouse share one number space here so that a step, a macro
     * key and the saved file all work the same way for both. Keyboard codes
     * stop well below this, so the two can never collide.
     */
    public static final int MOUSE_BASE = 1000;

    public static boolean isMouse(int code) {
        return code >= MOUSE_BASE;
    }

    /** Turns a code into the key object the game expects. */
    private static com.mojang.blaze3d.platform.InputConstants.Key keyOf(int code) {
        return isMouse(code)
                ? InputConstants.Type.MOUSE.getOrCreate(code - MOUSE_BASE)
                : InputConstants.Type.KEYSYM.getOrCreate(code);
    }

    /** Is this code currently held down? Works for keyboard and mouse. */
    public static boolean isDown(Minecraft client, int code) {
        try {
            if (isMouse(code)) {
                return GLFW.glfwGetMouseButton(client.getWindow().handle(),
                        code - MOUSE_BASE) == GLFW.GLFW_PRESS;
            }
            return InputConstants.isKeyDown(client.getWindow(), code);
        } catch (Throwable pvpErr) {
            return false;
        }
    }

    private static void releaseHeldKey() {
        if (heldKey == GLFW.GLFW_KEY_UNKNOWN) return;
        try {
            net.minecraft.client.KeyMapping.set(keyOf(heldKey), false);
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.releaseKey", pvpErr);
        }
        heldKey = GLFW.GLFW_KEY_UNKNOWN;
    }

    /**
     * Puts one macro on the clipboard as text.
     *
     * The same form used in the config file, with a short marker in front so a
     * pasted line can be recognised for what it is. Everything is there --
     * steps, delays, trigger, speed -- so what your friend gets behaves exactly
     * like yours.
     */
    public static synchronized String export(Macro m) {
        if (m == null) return "";
        return "vortex-macro:" + m.serialize();
    }

    /**
     * Reads a macro from text and adds it.
     *
     * @return the new macro, or null if the text was not one
     */
    public static synchronized Macro importFrom(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (!t.startsWith("vortex-macro:")) return null;
        Macro m = Macro.deserialize(t.substring("vortex-macro:".length()));
        if (m == null) return null;

        m.key = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;

        String base = m.name;
        int n = 2;
        while (nameTaken(m.name)) {
            m.name = base + " " + n;
            n++;
        }
        MACROS.add(m);
        return m;
    }

    private static boolean nameTaken(String name) {
        for (Macro m : MACROS) {
            if (m.name.equals(name)) return true;
        }
        return false;
    }

    public static synchronized String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Macro m : MACROS) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(m.serialize());
        }
        return sb.toString().replace("\n", "%0A");
    }

    public static synchronized void deserialize(String data) {
        MACROS.clear();
        if (data == null || data.isEmpty()) return;
        for (String line : data.split("%0A")) {
            Macro m = Macro.deserialize(line);
            if (m != null) MACROS.add(m);
        }
    }
}
