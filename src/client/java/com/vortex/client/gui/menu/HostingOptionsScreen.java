package com.vortex.client.gui.menu;

import com.vortex.client.gui.VortexStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * "Hosting Options" im Pausenmenue, solange der Launcher die Welt hostet:
 * Adresse fuer Freunde, Spielmodus, Schwierigkeit, PvP, Cheats, Whitelist,
 * Spieler (OP, Kick, Bann) und Neustart. Alles laeuft ueber den Launcher
 * ({@link LauncherHosting}) -- dieselben Aktionen wie dort.
 *
 * Seit 4.91: nur noch die Adresse fuer Freunde (keine LAN-Adresse), Plugins installieren,
 * Neustart jederzeit (man verbindet sich danach von selbst wieder), Server stoppen, und
 * wer zuletzt getrennt wurde -- mit Grund.
 */
public class HostingOptionsScreen extends Screen {
    private static final int FENSTER_W = 330, MAX_ZEILEN = 6;
    private static final String[] MODI = {"survival", "creative", "adventure", "spectator"};
    private static final String[] SCHWIERIG = {"peaceful", "easy", "normal", "hard"};

    private final Screen parent;
    private String zustand = "";
    private String meldung;
    private int fx, fy, fh, spielerY;

    public HostingOptionsScreen(Screen parent) {
        super(Component.literal("Hosting Options"));
        this.parent = parent;
    }

    private static String gross(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String naechster(String[] liste, String jetzt) {
        for (int i = 0; i < liste.length; i++) if (liste[i].equals(jetzt)) return liste[(i + 1) % liste.length];
        return liste[0];
    }

    private String zustandJetzt() {
        LauncherHosting.Stand s = LauncherHosting.stand();
        StringBuilder b = new StringBuilder(s.status).append('|').append(s.adresse).append('|').append(s.gamemode).append('|').append(s.difficulty)
                .append('|').append(s.pvp).append(s.cheats).append(s.whitelist).append(s.neustart);
        for (LauncherHosting.Spieler p : s.spieler) b.append('|').append(p.name()).append(p.op());
        return b.toString();
    }

    @Override
    protected void init() {
        zustand = zustandJetzt();
        LauncherHosting.Stand s = LauncherHosting.stand();
        int zeilen = Math.min(MAX_ZEILEN, Math.max(1, s.spieler.size()));
        fh = 184 + zeilen * 22;
        fx = (this.width - FENSTER_W) / 2;
        fy = Math.max(6, (this.height - fh) / 2);
        int x = fx + 14, bw = FENSTER_W - 28;
        boolean an = "running".equals(s.status);

        int y = fy + 42;
        addRenderableWidget(Button.builder(Component.literal("Copy"), b -> {
            if (s.adresse != null) {
                this.minecraft.keyboardHandler.setClipboard(s.adresse);
                meldung = "Address copied -- send it to your friends.";
            }
        }).bounds(fx + FENSTER_W - 14 - 50, y - 4, 50, 18).build()).active = s.adresse != null;

        y = fy + 70;
        int halb = (bw - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Game mode: " + gross(s.gamemode)),
                b -> LauncherHosting.einstellung("gamemode", naechster(MODI, s.gamemode)))
                .bounds(x, y, halb, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("For friends who join. You keep your own game mode."))).build()).active = an;
        addRenderableWidget(Button.builder(Component.literal("Difficulty: " + gross(s.difficulty)),
                b -> LauncherHosting.einstellung("difficulty", naechster(SCHWIERIG, s.difficulty)))
                .bounds(x + halb + 4, y, halb, 20).build()).active = an;
        y += 24;
        int drittel = (bw - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("PvP: " + (s.pvp ? "On" : "Off")),
                b -> LauncherHosting.einstellung("pvp", !s.pvp)).bounds(x, y, drittel, 20).build()).active = an;
        addRenderableWidget(Button.builder(Component.literal("Cheats: " + (s.cheats ? "On" : "Off")),
                b -> LauncherHosting.einstellung("cheats", !s.cheats)).bounds(x + drittel + 4, y, drittel, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Friends become operators and can use commands. You always can."))).build()).active = an;
        addRenderableWidget(Button.builder(Component.literal("Whitelist: " + (s.whitelist ? "On" : "Off")),
                b -> LauncherHosting.einstellung("whitelist", !s.whitelist)).bounds(x + 2 * (drittel + 4), y, bw - 2 * (drittel + 4), 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Only allowed players can join. Everyone online now is allowed automatically."))).build()).active = an;

        spielerY = y + 40;
        int py = spielerY;
        List<LauncherHosting.Spieler> liste = s.spieler;
        for (int i = 0; i < Math.min(MAX_ZEILEN, liste.size()); i++) {
            LauncherHosting.Spieler p = liste.get(i);
            if (!p.host()) {
                int bx = fx + FENSTER_W - 14;
                bx -= 40;
                addRenderableWidget(Button.builder(Component.literal("Ban"), b -> {
                    LauncherHosting.spieler(p.name(), "ban");
                    meldung = p.name() + " was banned.";
                }).bounds(bx, py, 40, 18).build());
                bx -= 44;
                addRenderableWidget(Button.builder(Component.literal("Kick"), b -> {
                    LauncherHosting.spieler(p.name(), "kick");
                    meldung = p.name() + " was kicked.";
                }).bounds(bx, py, 40, 18).build());
                int opW = Math.max(this.font.width("Remove OP"), this.font.width("Make OP")) + 12;
                bx -= opW + 4;
                addRenderableWidget(Button.builder(Component.literal(p.op() ? "Remove OP" : "Make OP"),
                        b -> LauncherHosting.spieler(p.name(), p.op() ? "deop" : "op")).bounds(bx, py, opW, 18).build());
            }
            py += 22;
        }

        int uy = fy + fh - 28;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(fx + FENSTER_W - 14 - 60, uy, 60, 20).build());
        boolean bruecke = LauncherHosting.kannHosten();
        int bx = x;
        addRenderableWidget(Button.builder(Component.literal("Plugins"), b -> this.minecraft.gui.setScreen(new HostPluginsScreen(this)))
                .bounds(bx, uy, 62, 20).build()).active = bruecke;
        bx += 66;
        addRenderableWidget(Button.builder(Component.literal(s.neustart ? "Restart now" : "Restart"), b -> {
            meldung = "Restarting -- you join again by yourself.";
            if (bruecke) {
                im(() -> LauncherHosting.neustartJetzt());
            } else {
                LauncherHosting.neustart();
            }
        }).bounds(bx, uy, 74, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Restarts the server, for example for new plugins. Everyone is disconnected for a moment."))).build()).active = an;
        bx += 78;
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> this.minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ja -> {
            if (ja) {
                im(() -> LauncherHosting.stoppen());
                this.minecraft.gui.setScreen(parent);
            } else {
                this.minecraft.gui.setScreen(this);
            }
        }, Component.literal("Stop the server?"), Component.literal("The world is saved and everyone on it -- you too -- is disconnected."))))
                .bounds(bx, uy, 56, 20).build()).active = bruecke && !"stopping".equals(s.status);
    }

    private interface Aktion { void los() throws com.vortex.client.core.LauncherBruecke.Fehler; }

    /** Aktion beim Launcher im Hintergrund; ein Fehler steht danach unten im Fenster. */
    private void im(Aktion a) {
        Thread t = new Thread(() -> {
            try {
                a.los();
            } catch (com.vortex.client.core.LauncherBruecke.Fehler e) {
                this.minecraft.execute(() -> meldung = e.getMessage());
            }
        }, "Vortex-Hosting");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void tick() {
        super.tick();
        if (!zustand.equals(zustandJetzt())) rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        LauncherHosting.Stand s = LauncherHosting.stand();
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        VortexStyle.schatten(g, fx, fy, FENSTER_W, fh, 1f);
        g.fill(fx, fy, fx + FENSTER_W, fy + fh, VortexStyle.WINDOW);
        VortexStyle.akzentLinie(g, fx + 4, fy, FENSTER_W - 8, 1f);

        int x = fx + 14, y = fy + 12;
        boolean an = "running".equals(s.status);
        g.fill(x, y + 2, x + 5, y + 7, an ? 0xFF22C55E : 0xFFF5B74A);
        g.text(this.font, "Hosting Options", x + 10, y, VortexStyle.TEXT, true);
        String sub = s.welt + (s.version.isEmpty() ? "" : "  ·  Minecraft " + s.version) + (an ? "" : "  ·  " + gross(s.status));
        g.text(this.font, sub, fx + FENSTER_W - 14 - this.font.width(sub), y, VortexStyle.TEXT_DIM, false);

        y = fy + 28;
        g.text(this.font, "Address for friends", x, y, VortexStyle.TEXT_DIM, false);
        y += 14;
        g.fill(x - 2, y - 4, fx + FENSTER_W - 14 - 54, y + 10, VortexStyle.INNER);
        String adresse = s.adresse != null && !"reconnecting".equals(s.netz) ? s.adresse
                : "reconnecting".equals(s.netz) ? "Reconnecting..." : !an && s.schritt != null ? s.schritt + "..." : "Getting the address...";
        g.text(this.font, adresse, x + 2, y, s.adresse != null ? 0xFF7EE2A0 : VortexStyle.TEXT_DIM, false);

        int py = spielerY - 14;
        g.text(this.font, "Players " + s.spieler.size() + " / " + s.maxPlayers, x, py, VortexStyle.TEXT_DIM, false);
        py += 14;
        if (s.spieler.isEmpty()) g.text(this.font, "Nobody is online yet.", x, py + 5, VortexStyle.TEXT_DIM, false);
        for (int i = 0; i < Math.min(MAX_ZEILEN, s.spieler.size()); i++) {
            LauncherHosting.Spieler p = s.spieler.get(i);
            g.fill(x - 2, py - 1, fx + FENSTER_W - 12, py + 19, VortexStyle.INNER);
            g.text(this.font, p.name(), x + 3, py + 5, VortexStyle.TEXT, false);
            int nx = x + 6 + this.font.width(p.name());
            if (p.host()) g.text(this.font, "(you)", nx, py + 5, VortexStyle.TEXT_DIM, false);
            else if (p.op()) g.text(this.font, "OP", nx, py + 5, VortexStyle.VIOLETT, false);
            py += 22;
        }
        if (s.spieler.size() > MAX_ZEILEN) g.text(this.font, "+" + (s.spieler.size() - MAX_ZEILEN) + " more -- see the launcher", x, py, VortexStyle.TEXT_DIM, false);

        int uy = fy + fh - 28;
        boolean frisch = s.trennung != null && System.currentTimeMillis() - s.trennung.zeit() < 5 * 60 * 1000;
        String hinweis = meldung != null ? meldung : s.neustart ? "Some changes need a restart." : frisch ? s.trennung.name() + " was disconnected: " + s.trennung.grund() : null;
        if (hinweis != null) {
            int ty = uy - 14;
            g.text(this.font, this.font.plainSubstrByWidth(hinweis, FENSTER_W - 28), x, ty,
                    meldung != null ? VortexStyle.VIOLETT : frisch && !s.neustart ? 0xFFF5B74A : VortexStyle.TEXT_DIM, false);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
