package com.vortex.client.core;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Menue-Waechter (seit 4.84): schreibt ins Log, WER eine Kiste oder ein Server-Menue
 * geschlossen hat -- der Server, eine Taste, ein Klick oder Code von Vortex.
 *
 * Anlass: auf Servern gingen Menues wie /ah scheinbar von selbst zu. Steht im Log
 * "closed by the server", war es der Server (oft ausgeloest durch Bewegung oder
 * Pakete bei offenem Menue; die aktiven Module stehen dann mit dabei). Steht dort
 * "closed by Vortex code", ist es ein Fehler bei uns -- mit der genauen Stelle.
 *
 * Suchen im Launcher: Logs -> "Menu watch".
 */
public final class MenueWaechter {
    private MenueWaechter() {}

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("vortexclient");
    private static final long FENSTER = 1_500_000_000L;

    private static volatile long serverZu, serverAuf;
    private static long letzteMeldung;

    public static void register() {
        PacketHooks.onReceive(p -> {
            if (p instanceof ClientboundContainerClosePacket) serverZu = System.nanoTime();
            else if (p instanceof ClientboundOpenScreenPacket) serverAuf = System.nanoTime();
            return false;
        });
    }

    /** Aus AbstractContainerScreen.removed(): das Menue geht gerade zu. */
    public static void zu(Screen screen) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null) return;
            StackTraceElement[] st = new Throwable().getStackTrace();
            String grund = grund(st);
            if (grund == null) return;
            long jetzt = System.nanoTime();
            if (jetzt - letzteMeldung < 50_000_000L) return;
            letzteMeldung = jetzt;
            String titel = screen.getTitle() == null ? "" : screen.getTitle().getString();
            if (titel.length() > 40) titel = titel.substring(0, 40) + "...";
            LOGGER.info("[Menu watch] \"{}\" ({}) {}", titel, screen.getClass().getSimpleName(), grund);
        } catch (Throwable t) {
            Errors.report("MenueWaechter", t);
        }
    }

    /**
     * null = gewollt (Taste, Klick, neues Server-Menue) und nicht der Rede wert.
     * Klassen werden ueber ihre Laufzeit-Namen verglichen -- so klappt es auch in Versionen,
     * in denen Minecraft beim Spielen andere Namen traegt.
     */
    private static String grund(StackTraceElement[] st) {
        long jetzt = System.nanoTime();
        String tastatur = net.minecraft.client.KeyboardHandler.class.getName();
        String mausK = net.minecraft.client.MouseHandler.class.getName();
        String netz = net.minecraft.client.multiplayer.ClientPacketListener.class.getName();
        String vortex = null;
        boolean taste = false, maus = false, paket = false;
        for (int i = 2; i < st.length; i++) {
            String k = st[i].getClassName(), m = st[i].getMethodName();
            if (vortex == null && !k.equals(MenueWaechter.class.getName())
                    && (k.startsWith("com.vortex.") || k.startsWith("de.vortexplus.") || m.contains("vortex$"))
                    && !k.startsWith("com.vortex.client.mixin.") && !m.contains("vortex$zu")) {
                vortex = kurz(k) + "." + m + (st[i].getLineNumber() > 0 ? ":" + st[i].getLineNumber() : "");
            }
            if (k.equals(tastatur)) taste = true;
            if (k.equals(mausK)) maus = true;
            if (k.equals(netz)) paket = true;
        }
        boolean zu = jetzt - serverZu < FENSTER, auf = jetzt - serverAuf < FENSTER;
        if (vortex != null && (!paket || vortex.contains("vortex$"))) return "closed by Vortex code at " + vortex;
        if (taste || maus) return null;
        if (auf && !(paket && zu && serverZu > serverAuf)) return null;
        if (paket && zu || zu && vortex == null) {
            String mods = aktiveModule();
            return "closed by the server" + (mods.isEmpty() ? "" : " -- active modules: " + mods);
        }
        if (paket) return "closed by the server (respawn, world change or disconnect)";
        for (int i = 2; i < st.length; i++) {
            String k = st[i].getClassName();
            if (k.startsWith("net.minecraft.") && !k.contains("Screen")) return "closed by Minecraft at " + kurz(k) + "." + st[i].getMethodName();
        }
        return "closed for an unknown reason";
    }

    private static String kurz(String klasse) {
        int p = klasse.lastIndexOf('.');
        return p < 0 ? klasse : klasse.substring(p + 1);
    }

    private static String aktiveModule() {
        List<String> namen = new ArrayList<>();
        try {
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                if (!m.isEnabled()) continue;
                Module.Category c = m.getCategory();
                if (c == Module.Category.CHEATS || c == Module.Category.PVP || c.ordinal() > Module.Category.MUSIC.ordinal()) namen.add(m.getName());
            }
        } catch (Throwable ignored) { }
        return String.join(", ", namen);
    }
}
