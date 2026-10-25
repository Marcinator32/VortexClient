package com.vortex.client.gui.menu;

import com.vortex.client.gui.VortexStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Eine Welt fuer Freunde hosten, direkt aus Minecraft (seit 4.91): "Host World" in der
 * Weltauswahl oder "Host for friends" im ESC-Menue einer Einzelspielerwelt.
 *
 * Der Launcher macht daraus einen Paper-Server (mit Plugins, Spielerverwaltung, Backup) --
 * OHNE dass Minecraft geschlossen wird. Sobald der Server laeuft, verbindet sich das Spiel
 * von selbst. Kommt man aus der offenen Welt, wird sie vorher gespeichert und verlassen
 * (der Server braucht die Welt fuer sich allein).
 */
public class WeltHostenScreen extends Screen {
    private enum Phase { FRAGE, STARTET, FEHLER }

    private static final int FENSTER_W = 320, FENSTER_H = 150;

    private final Screen parent;
    private final String ordner, name;
    private final boolean ausWelt;
    private Phase phase = Phase.FRAGE;
    private String fehler;
    private long angefragt;
    private int fx, fy;

    /**
     * @param ordner  Ordnername der Welt im saves-Ordner
     * @param name    angezeigter Name
     * @param ausWelt true = die Welt ist gerade offen (ESC-Menue) und wird vorher verlassen
     */
    public WeltHostenScreen(Screen parent, String ordner, String name, boolean ausWelt) {
        super(Component.literal("Host World"));
        this.parent = parent;
        this.ordner = ordner;
        this.name = name == null || name.isBlank() ? ordner : name;
        this.ausWelt = ausWelt;
    }

    @Override
    protected void init() {
        fx = (this.width - FENSTER_W) / 2;
        fy = Math.max(6, (this.height - FENSTER_H) / 2);
        int uy = fy + FENSTER_H - 28;
        if (phase == Phase.FRAGE) {
            addRenderableWidget(Button.builder(Component.literal("Host world"), b -> starten())
                    .bounds(fx + FENSTER_W - 14 - 110, uy, 110, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                    .bounds(fx + FENSTER_W - 14 - 110 - 4 - 70, uy, 70, 20).build());
        } else if (phase == Phase.FEHLER) {
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> zurueck())
                    .bounds(fx + FENSTER_W - 14 - 70, uy, 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Try again"), b -> starten())
                    .bounds(fx + FENSTER_W - 14 - 70 - 4 - 90, uy, 90, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
                abbrechen();
                zurueck();
            }).bounds(fx + FENSTER_W - 14 - 70, uy, 70, 20).build());
        }
    }

    private void wechseln(Phase p) {
        phase = p;
        rebuildWidgets();
    }

    private void starten() {
        if (ausWelt && this.minecraft.level != null) {
            this.minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
            this.minecraft.gui.setScreen(this);
        }
        fehler = null;
        angefragt = System.currentTimeMillis();
        wechseln(Phase.STARTET);
        Thread t = new Thread(() -> {
            try {
                LauncherHosting.starten(ordner);
            } catch (com.vortex.client.core.LauncherBruecke.Fehler e) {
                this.minecraft.execute(() -> {
                    fehler = e.getMessage();
                    wechseln(Phase.FEHLER);
                });
            }
        }, "Vortex-Host-Start");
        t.setDaemon(true);
        t.start();
    }

    private void abbrechen() {
        Thread t = new Thread(() -> {
            try { LauncherHosting.stoppen(); } catch (com.vortex.client.core.LauncherBruecke.Fehler ignored) { }
        }, "Vortex-Host-Stop");
        t.setDaemon(true);
        t.start();
    }

    /** Zurueck: aus der Welt gekommen = jetzt in die Weltauswahl, sonst zum vorherigen Bildschirm. */
    private void zurueck() {
        if (ausWelt) {
            this.minecraft.gui.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(new net.minecraft.client.gui.screens.TitleScreen()));
        } else {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (phase != Phase.STARTET) return;
        LauncherHosting.Stand s = LauncherHosting.stand();
        if (s.zeit < angefragt - 500) return;
        if ("off".equals(s.status) && s.fehler != null && System.currentTimeMillis() - angefragt > 600) {
            fehler = s.fehler;
            wechseln(Phase.FEHLER);
            return;
        }
        if ("running".equals(s.status) && s.port > 0) {
            com.vortex.client.social.Social.connectTo("127.0.0.1:" + s.port, s.welt.isEmpty() ? name : s.welt);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return phase != Phase.STARTET;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        VortexStyle.schatten(g, fx, fy, FENSTER_W, FENSTER_H, 1f);
        g.fill(fx, fy, fx + FENSTER_W, fy + FENSTER_H, VortexStyle.WINDOW);
        VortexStyle.akzentLinie(g, fx + 4, fy, FENSTER_W - 8, 1f);
        int x = fx + 14, y = fy + 12, breite = FENSTER_W - 28;
        g.text(this.font, "Host “" + name + "”", x, y, VortexStyle.TEXT, true);
        y += 18;
        String text;
        int farbe = VortexStyle.TEXT_DIM;
        if (phase == Phase.FRAGE) {
            text = "Your world becomes a server for your friends - with plugins and player controls. "
                    + "They get one address to join, you join by yourself.\n"
                    + (ausWelt ? "The world is saved and reopened as a server (a few seconds).\n" : "")
                    + "By hosting you accept the Minecraft EULA (aka.ms/MinecraftEULA).";
        } else if (phase == Phase.FEHLER) {
            text = fehler == null ? "Something went wrong." : fehler;
            farbe = 0xFFF87171;
        } else {
            LauncherHosting.Stand s = LauncherHosting.stand();
            String schritt = s.zeit >= angefragt - 500 && s.schritt != null ? s.schritt : "Asking the launcher";
            text = schritt + (s.prozent >= 0 && s.zeit >= angefragt - 500 ? "  " + s.prozent + "%" : "") + "\nMinecraft joins your world by itself in a moment.";
            int balkenY = fy + FENSTER_H - 46;
            g.fill(x, balkenY, x + breite, balkenY + 3, VortexStyle.TRACK);
            float t = (System.currentTimeMillis() % 1400L) / 1400f;
            int lauf = Math.round(breite * 0.3f);
            int start = x + Math.round((breite + lauf) * t) - lauf;
            int a = Math.max(x, start), b = Math.min(x + breite, start + lauf);
            if (b > a) g.fill(a, balkenY, b, balkenY + 3, VortexStyle.akzent(t));
        }
        for (String absatz : text.split("\n")) {
            for (var zeile : this.font.split(Component.literal(absatz), breite)) {
                g.text(this.font, zeile, x, y, farbe, false);
                y += 10;
            }
            y += 3;
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (phase == Phase.STARTET) return;
        zurueck();
    }
}
