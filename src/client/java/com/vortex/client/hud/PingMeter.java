package com.vortex.client.hud;

import java.net.InetSocketAddress;
import java.net.Socket;
import net.minecraft.client.Minecraft;

/**
 * Measures the round trip to the server ourselves.
 *
 * WHY THIS EXISTS: The number in the tab list comes from the server, and
 * vanilla only refreshes it every 600 ticks — thirty seconds. That is why the
 * reading felt stale and wrong: it was, for up to half a minute at a time.
 *
 * The protocol offers no way out. The server starts a ping and the client
 * answers; there is no packet a client may send to ask "how long does a round
 * trip take". So the measurement has to happen outside the game connection.
 *
 * What happens here: once per interval a plain connection to the server address
 * is opened and closed again, and the time that takes is the reading. It is the
 * same thing the multiplayer server list does when it shows a ping.
 *
 * HONEST ABOUT WHAT THIS IS: this measures the network round trip, not how long
 * the server takes to process your actions. When a server is overloaded its own
 * value can be much higher while this one stays low. For "is my connection bad
 * right now" it is the better number; for "is the server struggling" the server
 * value says more. Both are available, and the module lets you pick.
 */
public final class PingMeter {

    private static volatile int lastPing = -1;
    private static volatile long lastMeasured = 0L;

    /** Failed attempts in a row. */
    private static volatile int failures = 0;

    /** Do not try again before this time. */
    private static volatile long backoffUntil = 0L;
    private static Thread worker = null;
    private static volatile boolean running = false;

    /** How often to measure, in milliseconds. Set from the module. */
    private static volatile long intervalMs = 1000L;

    private PingMeter() {}

    /** Latest reading in milliseconds, or -1 if nothing has been measured yet. */
    public static int get() {
        return lastPing;
    }

    /** Age of the reading in milliseconds. */
    public static long age() {
        return (lastMeasured == 0L) ? Long.MAX_VALUE : System.currentTimeMillis() - lastMeasured;
    }

    public static void setInterval(long ms) {
        intervalMs = Math.max(200L, ms);
    }

    /**
     * Starts the background thread. Called once at startup; measuring only
     * actually happens while the module wants it.
     */
    public static synchronized void start() {
        running = true;
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(PingMeter::loop, "vortexclient-ping");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stop() {
        running = false;
        lastPing = -1;
    }

    private static void loop() {
        while (running) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                return;
            }
            try {
                measureOnce();
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("PingMeter", pvpErr);
            }
        }
    }

    private static void measureOnce() {
        if (System.currentTimeMillis() < backoffUntil) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;

        if (client.isLocalServer()) {
            lastPing = 0;
            lastMeasured = System.currentTimeMillis();
            return;
        }

        var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                com.vortex.client.module.modules.PingModule.class);
        if (mod == null || !mod.isEnabled() || !mod.measure.get()) return;

        var entry = client.getCurrentServer();
        if (entry == null || entry.ip == null) return;

        String host = entry.ip;
        int port = 25565;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            try {
                port = Integer.parseInt(host.substring(colon + 1).trim());
                host = host.substring(0, colon);
            } catch (NumberFormatException ignored) {
            }
        }

        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            long ms = (System.nanoTime() - start) / 1_000_000L;
            lastPing = (int) Math.min(ms, 9999);
            lastMeasured = System.currentTimeMillis();
            failures = 0;
        } catch (Throwable pvpErr) {
            failures++;

            if (failures >= 3) {
                backoffUntil = System.currentTimeMillis() + 30_000L;
            }
        }
    }
}
