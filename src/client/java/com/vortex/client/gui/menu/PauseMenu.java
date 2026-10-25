package com.vortex.client.gui.menu;

import com.vortex.client.gui.HomeScreen;
import com.vortex.client.gui.VortexStyle;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.MenuStil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;

/**
 * ESC-Menue (seit 4.53): Minecrafts Pausenmenue mit seinem ganz normalen Aufbau -- nur ein
 * Knopf "Vortex" direkt unter "Back to Game" kommt dazu (oeffnet das Rechtsshift-Menue, ESC
 * fuehrt von dort zurueck hierher). Alles darunter rueckt eine Zeile nach unten.
 *
 * Dazu Vortex-Farben: violett-blauer Schimmer ueber dem weichgezeichneten Hintergrund, Titel
 * in Lavendel, Knoepfe mit violettem Rand unter der Maus, "Disconnect" / "Save and Quit" in
 * Rot (die Knopf-Farben stehen in MenuStil, siehe pauseFarben()).
 *
 * Abschaltbar: Settings -> "Vortex Pause Menu" (aus = Minecrafts Pausenmenue ohne Aenderung).
 * Seit 4.96 bleibt das Pausenmenue unveraendert, wenn ein Server-Pack oder ein Pack mit eigenen
 * Knoepfen aktiv ist (siehe MenuStil.fremdeKnoepfe) -- deren Menues haengen an Minecrafts Aufbau.
 */
public final class PauseMenu {
    private PauseMenu() {}

    /** Hoehe einer Zeile im Pausenmenue (Knopf 20 + Abstand 4). */
    private static final int ZEILE = 24;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, breite, hoehe) -> {
            if (!(screen instanceof PauseScreen p) || !p.showsPauseMenu()) return;
            if (!com.vortex.client.core.ClientSettings.INSTANCE.vortexPauseMenu.get()) return;
            if (MenuStil.fremdeKnoepfe()) return;
            try {
                anlegen(p, breite, hoehe);
                ScreenEvents.afterBackground(screen).register((s, ctx, mx, my, delta) -> hintergrund(ctx, s.width, s.height));
            } catch (Throwable t) {
                com.vortex.client.core.Errors.report("PauseMenu", t);
            }
        });
    }

    private static void anlegen(PauseScreen p, int breite, int hoehe) {
        AbstractWidget zurueck = null;
        List<AbstractWidget> alle = new ArrayList<>();
        for (var c : p.children()) {
            if (!(c instanceof AbstractWidget w)) continue;
            alle.add(w);
            if ("menu.returnToGame".equals(schluessel(w))) zurueck = w;
            if (c instanceof StringWidget t && t.getMessage().getString().equals(p.getTitle().getString())) {
                t.setMessage(p.getTitle().copy().withColor(0xD8CCFF));
            }
        }
        if (zurueck == null) return;

        int unterkante = 0;
        for (AbstractWidget w : alle) {
            if (w.getY() > zurueck.getY()) w.setY(w.getY() + ZEILE);
            if (!(w instanceof StringWidget)) unterkante = Math.max(unterkante, w.getY() + w.getHeight());
        }
        int zuViel = unterkante - (hoehe - 4);
        if (zuViel > 0) {
            int platzOben = Math.max(0, zurueck.getY() - 54);
            int hoch = Math.min(zuViel, platzOben);
            for (AbstractWidget w : alle) if (!(w instanceof StringWidget)) w.setY(w.getY() - hoch);
        }

        VortexKnopf v = new VortexKnopf(zurueck.getX(), zurueck.getY() + ZEILE, zurueck.getWidth(), 20, p);
        Screens.getWidgets(p).add(v);
    }

    /** Sprachschluessel der Beschriftung (menu.returnToGame, menu.disconnect ...) oder null. */
    public static String schluessel(AbstractWidget w) {
        return w.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }

    /** "Disconnect" / "Save and Quit to Title"? (in Rot) */
    public static boolean trennen(AbstractWidget w) {
        String k = schluessel(w);
        return "menu.disconnect".equals(k) || "menu.returnToMenu".equals(k) || "menu.quit".equals(k);
    }

    /** Violett-blauer Schimmer ueber Minecrafts weichgezeichnetem Hintergrund. */
    private static void hintergrund(GuiGraphicsExtractor ctx, int w, int h) {
        try {
            ctx.fillGradient(0, 0, w, h, 0x38140A30, 0x700A0618);
            float r = Math.max(w, h) * 0.55f;
            Glatt.licht(ctx, w * 0.18f, h * 0.12f, r, Glatt.alpha(VortexStyle.VIOLETT, 0.20f));
            Glatt.licht(ctx, w * 0.86f, h * 0.92f, r, Glatt.alpha(VortexStyle.BLAU, 0.16f));
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("PauseMenu.hintergrund", t);
        }
    }

    /** Der Knopf ins Vortex-Menue: violett-blauer Verlauf mit Logo. */
    public static final class VortexKnopf extends AbstractButton {
        private final Screen pause;

        VortexKnopf(int x, int y, int w, int h, Screen pause) {
            super(x, y, w, h, Component.literal("Vortex"));
            this.pause = pause;
            setTooltip(Tooltip.create(Component.literal("Mods, HUD Editor, Cosmetics, Waypoints, Friends ... (Right Shift)")));
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            try {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setScreen(com.vortex.client.core.ClientSettings.INSTANCE.rightShiftOpens.getIndex() == 1
                        ? new com.vortex.client.gui.PanelGui() : new HomeScreen(pause));
            } catch (Throwable t) {
                com.vortex.client.core.Errors.report("PauseMenu.vortex", t);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            float alpha = Math.max(0f, Math.min(1f, getAlpha()));
            float hv = MenuStil.hover(this, active && isHoveredOrFocused());
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            int links = Glatt.mix(0xFF4A2A9E, 0xFF7C4DEB, hv), rechts = Glatt.mix(0xFF1F4FB8, 0xFF3B82F6, hv);
            MenuStil.verlaufW(g, x + 1, y + 1, x + w - 1, y + h - 1, Glatt.alpha(links, alpha * 0.92f), Glatt.alpha(rechts, alpha * 0.92f));
            g.fill(x + 1, y + 1, x + w - 1, y + 2, Glatt.alpha(0x40FFFFFF, alpha));
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, Glatt.alpha(0x40000000, alpha));
            int rand = Glatt.alpha(Glatt.mix(0xFF000000, 0xFFFFFFFF, hv), alpha);
            g.fill(x, y, x + w, y + 1, rand);
            g.fill(x, y + h - 1, x + w, y + h, rand);
            g.fill(x, y + 1, x + 1, y + h - 1, rand);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, rand);
            String text = getMessage().getString();
            int lg = 12, tb = MenuStil.breite(text);
            int gesamt = lg + 4 + tb;
            int lx = x + (w - gesamt) / 2;
            Glatt.logo(g, lx, y + (h - lg) / 2f, lg, alpha, 1f);
            MenuStil.text(g, text, lx + lg + 4, y + (h - 8) / 2, Glatt.alpha(0xFFFFFFFF, alpha), true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
