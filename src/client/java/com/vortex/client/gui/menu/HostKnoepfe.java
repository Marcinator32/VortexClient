package com.vortex.client.gui.menu;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * Hosten direkt aus Minecraft (seit 4.91, braucht den Vortex Launcher):
 *   Weltauswahl  zwischen "Play Selected World" und "Create New World" ein Knopf
 *                "Host World" fuer die gewaehlte Welt.
 *   ESC-Menue    in einer Einzelspielerwelt wird aus "Open to LAN" -> "Host for friends":
 *                die Welt wird gespeichert und als Server fuer Freunde neu geoeffnet.
 * Ohne Launcher bleibt alles wie in Minecraft.
 */
public final class HostKnoepfe {
    private HostKnoepfe() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { LauncherHosting.tick(mc); } catch (Throwable t) { com.vortex.client.core.Errors.report("LauncherHosting.tick", t); }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, breite, hoehe) -> {
            try {
                if (!LauncherHosting.kannHosten()) return;
                if (screen instanceof SelectWorldScreen) weltliste(screen);
                else if (screen instanceof PauseScreen p && p.showsPauseMenu()) pause(p);
            } catch (Throwable t) {
                com.vortex.client.core.Errors.report("HostKnoepfe", t);
            }
        });
    }

    private static String schluessel(AbstractWidget w) {
        return w.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : null;
    }

    private static AbstractWidget finde(Screen s, String key) {
        for (var c : s.children()) if (c instanceof AbstractWidget w && key.equals(schluessel(w))) return w;
        return null;
    }

    private static WorldSelectionList liste(Screen s) {
        for (var c : s.children()) if (c instanceof WorldSelectionList l) return l;
        return null;
    }

    /** Gewaehlte Welt: {Ordner, Name} oder null. */
    private static String[] gewaehlt(Screen s) {
        WorldSelectionList l = liste(s);
        if (l == null) return null;
        var e = l.getSelectedOpt();
        if (e.isEmpty()) return null;
        var sum = e.get().getLevelSummary();
        return new String[] { sum.getLevelId(), sum.getLevelName() };
    }

    private static void weltliste(Screen screen) {
        AbstractWidget spielen = finde(screen, "selectWorld.select");
        AbstractWidget neu = finde(screen, "selectWorld.create");
        if (spielen == null || neu == null) return;
        Button host = Button.builder(Component.literal("Host World"), b -> {
            String[] w = gewaehlt(screen);
            if (w != null) Minecraft.getInstance().gui.setScreen(new WeltHostenScreen(screen, w[0], w[1], false));
        }).bounds(spielen.getX(), spielen.getY(), 60, spielen.getHeight())
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Make this world a server for your friends - they get one address to join.")))
                .build();
        Screens.getWidgets(screen).add(host);
        anordnen(spielen, host, neu);
        ScreenEvents.afterTick(screen).register(s -> {
            try {
                anordnen(spielen, host, neu);
                LauncherHosting.Stand st = LauncherHosting.stand();
                host.active = gewaehlt(s) != null && "off".equals(st.status);
            } catch (Throwable t) {
                com.vortex.client.core.Errors.report("HostKnoepfe.tick", t);
            }
        });
    }

    /** Drei Knoepfe in der Breite von bisher zwei: Spielen, Hosten, Neue Welt. */
    private static void anordnen(AbstractWidget spielen, Button host, AbstractWidget neu) {
        int links = Math.min(spielen.getX(), host.getX());
        int rechts = neu.getX() + neu.getWidth();
        if (host.getX() > spielen.getX() && host.getX() + host.getWidth() <= neu.getX() && spielen.getY() == host.getY()) return;
        int luecke = 4;
        int b = (rechts - links - 2 * luecke) / 3;
        if (b < 40) return;
        spielen.setX(links);
        spielen.setWidth(b);
        host.setX(links + b + luecke);
        host.setY(spielen.getY());
        host.setWidth(b);
        neu.setX(links + 2 * (b + luecke));
        neu.setWidth(rechts - neu.getX());
    }

    /** ESC-Menue in der Einzelspielerwelt: "Open to LAN" wird "Host for friends". */
    private static void pause(PauseScreen p) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || server.isPublished()) return;
        AbstractWidget lan = finde(p, "menu.shareToLan");
        if (lan == null) return;
        String ordner = ordner(server);
        if (ordner == null) return;
        String name = server.getWorldData().getLevelName();
        Button host = Button.builder(Component.literal("Host for friends"), b -> mc.gui.setScreen(new WeltHostenScreen(p, ordner, name, true)))
                .bounds(lan.getX(), lan.getY(), lan.getWidth(), lan.getHeight())
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Your friends join this world from anywhere with one address.")))
                .build();
        Screens.getWidgets(p).remove(lan);
        Screens.getWidgets(p).add(host);
    }

    /** Ordnername der offenen Einzelspielerwelt. */
    private static String ordner(IntegratedServer server) {
        try {
            Path p = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path n = p.getFileName();
            return n == null ? null : n.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
