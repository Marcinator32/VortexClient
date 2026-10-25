package com.vortex.client.account;

import com.mojang.blaze3d.platform.NativeImage;
import com.vortex.client.gui.VortexStyle;
import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Account-Switcher (seit 4.86 neu): zeigt die Konten aus dem Vortex Launcher, ein Klick
 * auf "Switch" wechselt -- Name, Skin, Chat-Signatur, alles (siehe KontoWechsel). Kein
 * Microsoft-Code und kein Browser mehr; neue Konten kommen im Launcher dazu.
 */
public class AccountScreen extends Screen {
    private static final int FENSTER_W = 300, ZEILE = 30;
    private static final Map<String, Identifier> GESICHTER = new HashMap<>();
    private static int naechstes;

    private final Screen parent;
    private volatile List<LauncherKonten.Konto> konten;
    private volatile String aktiv = "";
    private volatile String meldung = "Loading your accounts...";
    private volatile boolean fehler;
    private volatile String wechselt;
    private int fx, fy, fh;

    public AccountScreen() { this(null); }

    public AccountScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
        laden();
    }

    private void laden() {
        Thread t = new Thread(() -> {
            try {
                LauncherKonten.Liste l = LauncherKonten.liste();
                konten = l.konten();
                aktiv = l.aktiv() == null ? "" : l.aktiv();
                meldung = konten.isEmpty() ? "No accounts in the launcher yet. Add one there (top right)." : null;
                fehler = false;
            } catch (LauncherKonten.Fehler e) {
                meldung = e.getMessage();
                fehler = true;
            }
            Minecraft.getInstance().execute(this::neuAufbauen);
        }, "vortexclient-accounts");
        t.setDaemon(true);
        t.start();
    }

    private void neuAufbauen() {
        if (this.minecraft != null && this.minecraft.gui.screen() == this) rebuildWidgets();
    }

    private boolean inWelt() {
        return this.minecraft != null && this.minecraft.level != null;
    }

    @Override
    protected void init() {
        List<LauncherKonten.Konto> l = konten;
        int n = l == null ? 0 : Math.min(l.size(), Math.max(1, (this.height - 120) / ZEILE));
        fh = 74 + Math.max(1, n) * ZEILE + 34;
        fx = (this.width - FENSTER_W) / 2;
        fy = Math.max(8, (this.height - fh) / 2);
        int y = fy + 50;
        for (int i = 0; i < n; i++) {
            LauncherKonten.Konto k = l.get(i);
            addRenderableWidget(new KontoZeile(fx + 12, y, FENSTER_W - 24, ZEILE - 4, k));
            y += ZEILE;
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(fx + FENSTER_W - 12 - 70, fy + fh - 28, 70, 20).build());
        if (fehler) {
            addRenderableWidget(Button.builder(Component.literal("Try again"), b -> {
                meldung = "Loading your accounts...";
                fehler = false;
                laden();
                rebuildWidgets();
            }).bounds(fx + 12, fy + fh - 28, 90, 20).build());
        }
    }

    private void wechseln(LauncherKonten.Konto k) {
        if (wechselt != null) return;
        if (inWelt()) {
            meldung = "Leave the world first, then switch.";
            fehler = true;
            return;
        }
        wechselt = k.id();
        meldung = "Switching to " + k.name() + "...";
        fehler = false;
        Thread t = new Thread(() -> {
            try {
                LauncherKonten.Sitzung s = LauncherKonten.wechseln(k.id());
                Minecraft.getInstance().execute(() -> {
                    try {
                        KontoWechsel.anwenden(s.name(), s.uuid(), s.token());
                        aktiv = k.id();
                        meldung = "You are now playing as " + s.name() + ".";
                        fehler = false;
                    } catch (Throwable e) {
                        com.vortex.client.core.Errors.report("KontoWechsel", e);
                        meldung = "The switch did not work in this Minecraft version.";
                        fehler = true;
                    }
                    wechselt = null;
                    neuAufbauen();
                });
            } catch (LauncherKonten.Fehler e) {
                meldung = e.getMessage();
                fehler = true;
                wechselt = null;
                Minecraft.getInstance().execute(this::neuAufbauen);
            }
        }, "vortexclient-account-switch");
        t.setDaemon(true);
        t.start();
    }

    /** Gesicht (8x8 vom Launcher) als Textur, einmal je Konto. */
    private static Identifier gesicht(LauncherKonten.Konto k) {
        if (k.gesicht() == null) return null;
        Identifier id = GESICHTER.get(k.id());
        if (id != null) return id;
        try {
            NativeImage bild = NativeImage.read(new ByteArrayInputStream(k.gesicht()));
            id = Identifier.fromNamespaceAndPath("vortexclient", "account_face/" + (naechstes++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "vortexclient-account-face", bild));
            GESICHTER.put(k.id(), id);
            return id;
        } catch (Throwable e) {
            GESICHTER.put(k.id(), null);
            return null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        VortexStyle.schatten(g, fx, fy, FENSTER_W, fh, 1f);
        g.fill(fx, fy, fx + FENSTER_W, fy + fh, VortexStyle.WINDOW);
        VortexStyle.akzentLinie(g, fx + 4, fy, FENSTER_W - 8, 1f);

        int x = fx + 16;
        g.text(this.font, "Accounts", x, fy + 14, VortexStyle.TEXT, true);
        String jetzt = "Playing as " + this.minecraft.getUser().getName();
        g.text(this.font, jetzt, fx + FENSTER_W - 16 - this.font.width(jetzt), fy + 14, VortexStyle.TEXT_DIM, false);
        g.text(this.font, "Your accounts from the Vortex launcher", x, fy + 30, VortexStyle.TEXT_DIM, false);

        String m = meldung;
        if (m != null) {
            int y = konten == null || konten.isEmpty() ? fy + 56 : fy + fh - 48;
            for (var zeile : this.font.split(Component.literal(m), FENSTER_W - 32)) {
                g.text(this.font, zeile, x, y, fehler ? 0xFFF87171 : VortexStyle.TEXT_DIM, false);
                y += 10;
            }
        } else if (konten != null && !konten.isEmpty()) {
            g.text(this.font, "New accounts: add them in the launcher.", x, fy + fh - 48, VortexStyle.TEXT_DIM, false);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Eine Zeile: Gesicht, Name, Zustand -- Klick wechselt. */
    private class KontoZeile extends AbstractButton {
        private final LauncherKonten.Konto k;

        KontoZeile(int x, int y, int w, int h, LauncherKonten.Konto k) {
            super(x, y, w, h, Component.literal(k.name()));
            this.k = k;
            this.active = !k.id().equals(aktiv) && !k.spieltWoanders();
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            wechseln(k);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            boolean ist = k.id().equals(aktiv);
            float hv = MenuStil.hover(this, active && isHoveredOrFocused());
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, VortexStyle.mix(VortexStyle.INNER, 0xFF2A2140, hv));
            if (ist) g.fill(x, y, x + 2, y + h, VortexStyle.VIOLETT);

            int kopf = h - 8, kx = x + 6, ky = y + 4;
            Identifier id = gesicht(k);
            if (id != null) g.blit(RenderPipelines.GUI_TEXTURED, id, kx, ky, 0f, 0f, kopf, kopf, 8, 8, 8, 8);
            else {
                g.fill(kx, ky, kx + kopf, ky + kopf, 0xFF3A3150);
                String b = k.name().isEmpty() ? "?" : k.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                g.text(font, b, kx + (kopf - font.width(b)) / 2, ky + (kopf - 8) / 2, VortexStyle.TEXT, false);
            }

            int tx = kx + kopf + 8;
            g.text(font, k.name(), tx, y + 5, VortexStyle.TEXT, false);
            String zustand = ist ? "Active" : k.spieltWoanders() ? "Playing in another window" : k.id().equals(wechselt) ? "Switching..." : "";
            if (!zustand.isEmpty()) g.text(font, zustand, tx, y + 15, ist ? 0xFF7EE2A0 : VortexStyle.TEXT_DIM, false);

            if (!ist && !k.spieltWoanders()) {
                String knopf = "Switch";
                int bw = font.width(knopf) + 16, bx = x + w - bw - 6, by = y + (h - 16) / 2;
                g.fill(bx, by, bx + bw, by + 16, VortexStyle.mix(0xFF3B2F5E, VortexStyle.VIOLETT, hv));
                g.text(font, knopf, bx + 8, by + 4, 0xFFFFFFFF, false);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
