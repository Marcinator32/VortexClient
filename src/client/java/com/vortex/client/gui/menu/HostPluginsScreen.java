package com.vortex.client.gui.menu;

import com.vortex.client.gui.VortexStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugins fuer den gehosteten Server, direkt im Spiel (seit 4.91): oben die installierten
 * (Entfernen), darunter Suche auf Modrinth (Installieren). Installiert wird vom Launcher in
 * den Server-Ordner; wirksam nach "Restart" -- danach verbindet sich das Spiel von selbst.
 */
public class HostPluginsScreen extends Screen {
    private static final int FENSTER_W = 360, ZEILE = 22;

    private final Screen parent;
    private List<LauncherHosting.Plugin> installiert = new ArrayList<>();
    private List<LauncherHosting.Fund> funde = new ArrayList<>();
    private String suche = "", entwurf = "";
    private String meldung;
    private boolean laedt;
    private final java.util.Set<String> laeuft = new java.util.HashSet<>();
    private EditBox feld;
    private int fx, fy, fh, listeY, maxInstalliert, maxFunde;
    private boolean geladen;

    public HostPluginsScreen(Screen parent) {
        super(Component.literal("Server plugins"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        fh = Math.min(this.height - 12, 320);
        fx = (this.width - FENSTER_W) / 2;
        fy = Math.max(6, (this.height - fh) / 2);
        int x = fx + 14, bw = FENSTER_W - 28;
        int y = fy + 40;
        feld = new EditBox(this.font, x, y, bw - 70, 18, Component.literal("Search plugins"));
        feld.setMaxLength(80);
        feld.setValue(entwurf);
        feld.setResponder(v -> entwurf = v);
        feld.setHint(Component.literal("Search, e.g. EssentialsX, LuckPerms, WorldEdit"));
        addRenderableWidget(feld);
        addRenderableWidget(Button.builder(Component.literal("Search"), b -> suchen(feld.getValue())).bounds(x + bw - 66, y - 1, 66, 20).build());

        listeY = y + 30;
        int frei = fh - (listeY - fy) - 40;
        int zeilen = Math.max(2, frei / ZEILE - 2);
        maxInstalliert = Math.min(installiert.size(), Math.max(1, zeilen / 3));
        maxFunde = Math.max(0, zeilen - maxInstalliert);

        int py = listeY + 12;
        for (int i = 0; i < maxInstalliert; i++) {
            LauncherHosting.Plugin p = installiert.get(i);
            addRenderableWidget(Button.builder(Component.literal("Remove"), b -> entfernen(p))
                    .bounds(fx + FENSTER_W - 14 - 60, py, 60, 18).build()).active = !laeuft.contains(p.datei());
            py += ZEILE;
        }
        if (installiert.isEmpty()) py += ZEILE;
        py += 14;
        for (int i = 0; i < Math.min(maxFunde, funde.size()); i++) {
            LauncherHosting.Fund f = funde.get(i);
            boolean da = f.installiert() || installiert.stream().anyMatch(p -> p.projekt().equals(f.projekt()));
            Button knopf = Button.builder(Component.literal(laeuft.contains(f.projekt()) ? "..." : da ? "Installed" : "Install"), b -> installieren(f))
                    .bounds(fx + FENSTER_W - 14 - 60, py, 60, 18).build();
            knopf.active = !da && !laeuft.contains(f.projekt());
            addRenderableWidget(knopf);
            py += ZEILE;
        }

        int uy = fy + fh - 28;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(fx + FENSTER_W - 14 - 60, uy, 60, 20).build());
        boolean neustart = LauncherHosting.stand().neustart;
        addRenderableWidget(Button.builder(Component.literal("Restart server"), b -> {
            meldung = "Restarting -- you join again by yourself.";
            hintergrund(() -> LauncherHosting.neustartJetzt(), null);
        }).bounds(x, uy, 100, 20).build()).active = neustart && "running".equals(LauncherHosting.stand().status);

        if (!geladen) {
            geladen = true;
            laden();
            suchen("");
        }
    }

    private interface Aktion { void los() throws com.vortex.client.core.LauncherBruecke.Fehler; }

    private void hintergrund(Aktion a, Runnable danach) {
        Thread t = new Thread(() -> {
            String fehler = null;
            try { a.los(); } catch (com.vortex.client.core.LauncherBruecke.Fehler e) { fehler = e.getMessage(); }
            final String f = fehler;
            this.minecraft.execute(() -> {
                if (f != null) meldung = f;
                if (danach != null) danach.run();
                rebuildWidgets();
            });
        }, "Vortex-Plugins");
        t.setDaemon(true);
        t.start();
    }

    private void laden() {
        List<List<LauncherHosting.Plugin>> box = new ArrayList<>();
        hintergrund(() -> box.add(LauncherHosting.plugins()), () -> { if (!box.isEmpty()) installiert = box.get(0); });
    }

    private void suchen(String text) {
        suche = text == null ? "" : text.trim();
        laedt = true;
        List<List<LauncherHosting.Fund>> box = new ArrayList<>();
        hintergrund(() -> box.add(LauncherHosting.suchen(suche)), () -> {
            laedt = false;
            if (!box.isEmpty()) funde = box.get(0);
        });
    }

    private void installieren(LauncherHosting.Fund f) {
        laeuft.add(f.projekt());
        rebuildWidgets();
        hintergrund(() -> LauncherHosting.installieren(f.projekt()), () -> {
            laeuft.remove(f.projekt());
            if (meldung == null || !meldung.contains(f.titel())) meldung = f.titel() + " installed -- restart the server to use it.";
            laden();
        });
    }

    private void entfernen(LauncherHosting.Plugin p) {
        laeuft.add(p.datei());
        rebuildWidgets();
        hintergrund(() -> LauncherHosting.entfernen(p.datei()), () -> {
            laeuft.remove(p.datei());
            meldung = p.titel() + " removed -- restart the server to apply.";
            laden();
        });
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (feld != null && feld.isFocused() && (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            suchen(feld.getValue());
            return true;
        }
        return super.keyPressed(e);
    }

    private static String zahl(long n) {
        if (n >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", n / 1e6);
        if (n >= 1_000) return String.format(java.util.Locale.ROOT, "%.0fk", n / 1e3);
        return String.valueOf(n);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        VortexStyle.schatten(g, fx, fy, FENSTER_W, fh, 1f);
        g.fill(fx, fy, fx + FENSTER_W, fy + fh, VortexStyle.WINDOW);
        VortexStyle.akzentLinie(g, fx + 4, fy, FENSTER_W - 8, 1f);
        int x = fx + 14, rechts = fx + FENSTER_W - 14;
        g.text(this.font, "Server plugins", x, fy + 12, VortexStyle.TEXT, true);
        g.text(this.font, "From Modrinth. New plugins work after a restart.", x, fy + 24, VortexStyle.TEXT_DIM, false);

        int py = listeY;
        g.text(this.font, "Installed (" + installiert.size() + ")", x, py, VortexStyle.TEXT_DIM, false);
        py += 12;
        if (installiert.isEmpty()) {
            g.text(this.font, "No plugins yet.", x + 3, py + 5, VortexStyle.TEXT_DIM, false);
            py += ZEILE;
        }
        for (int i = 0; i < maxInstalliert; i++) {
            LauncherHosting.Plugin p = installiert.get(i);
            g.fill(x - 2, py - 1, rechts + 2, py + 19, VortexStyle.INNER);
            String t = p.titel() + (p.version().isEmpty() ? "" : "  " + p.version());
            g.text(this.font, this.font.plainSubstrByWidth(t, rechts - x - 70), x + 3, py + 5, VortexStyle.TEXT, false);
            py += ZEILE;
        }
        if (installiert.size() > maxInstalliert) g.text(this.font, "+" + (installiert.size() - maxInstalliert) + " more in the launcher", x + 120, listeY, VortexStyle.TEXT_DIM, false);
        py += 2;
        g.text(this.font, laedt ? "Searching..." : suche.isEmpty() ? "Popular" : "Results", x, py, VortexStyle.TEXT_DIM, false);
        py += 12;
        for (int i = 0; i < Math.min(maxFunde, funde.size()); i++) {
            LauncherHosting.Fund f = funde.get(i);
            g.fill(x - 2, py - 1, rechts + 2, py + 19, VortexStyle.INNER);
            g.text(this.font, this.font.plainSubstrByWidth(f.titel(), 150), x + 3, py + 1, VortexStyle.TEXT, false);
            String unter = (f.autor().isEmpty() ? "" : "by " + f.autor() + "  ·  ") + zahl(f.downloads()) + " downloads";
            g.text(this.font, this.font.plainSubstrByWidth(unter, rechts - x - 70), x + 3, py + 10, VortexStyle.TEXT_DIM, false);
            py += ZEILE;
        }
        if (!laedt && funde.isEmpty()) g.text(this.font, "Nothing found.", x + 3, py + 5, VortexStyle.TEXT_DIM, false);

        if (meldung != null) {
            int uy = fy + fh - 28;
            g.text(this.font, this.font.plainSubstrByWidth(meldung, FENSTER_W - 28 - 104 - 64), x + 106, uy + 6, VortexStyle.VIOLETT, false);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
