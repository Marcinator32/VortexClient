package com.vortex.client.gui.menu;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.MenuStil;
import com.vortex.client.gui.glatt.Symbole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Neon-Knopf unter der Figur im Hauptmenue (seit 4.84): dunkle Flaeche, leuchtender Rand
 * in der Vortex-Farbe, unter der Maus heller und mit mehr Schein. "zweite" nimmt das Ende
 * des Farbverlaufs statt des Anfangs.
 */
public class NeonKnopf extends AbstractButton {
    private final Symbole.Symbol symbol;
    private final Runnable aktion;
    private final boolean zweite;
    private final long start = System.nanoTime();

    public NeonKnopf(int x, int y, int w, int h, String text, Symbole.Symbol symbol, boolean zweite, Runnable aktion) {
        super(x, y, w, h, Component.literal(text));
        this.symbol = symbol;
        this.aktion = aktion;
        this.zweite = zweite;
    }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        try {
            aktion.run();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("NeonKnopf", t);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        float alpha = Math.max(0f, Math.min(1f, getAlpha()));
        float hv = MenuStil.hover(this, active && isHoveredOrFocused());
        float t = (System.nanoTime() - start) / 1e9f;
        float puls = 0.5f + 0.5f * (float) Math.sin(t * 2.2f + (zweite ? 1.6f : 0f));
        int farbe = zweite ? TitelStil.B : TitelStil.A;
        int hell = zweite ? 0xFF93C5FD : 0xFFC4B5FD;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float r = h / 2f;

        Glatt.schatten(g, x, y, w, h, r, 5 + 3 * hv, Glatt.alpha(farbe, alpha * (0.28f + 0.12f * puls + 0.35f * hv)));
        Glatt.rund(g, x, y, w, h, r, Glatt.alpha(Glatt.mix(0xE00A0814, 0xE81A1430, hv), alpha));
        Glatt.rund(g, x + 1, y + 1, w - 2, h / 2f - 1, r - 1, Glatt.alpha(farbe, alpha * (0.10f + 0.10f * hv)));
        Glatt.rahmen(g, x, y, w, h, r, 2, Glatt.alpha(Glatt.mix(farbe, hell, hv), alpha * (0.85f + 0.15f * hv)));

        String text = getMessage().getString();
        float s = 10f;
        int tb = Glatt.breite(text, Glatt.Schrift.FETT);
        boolean symbolPasst = s + 4 + tb <= w - 10;
        float gesamt = symbolPasst ? s + 4 + tb : tb;
        float sx = x + (w - gesamt) / 2f;
        int textFarbe = Glatt.alpha(Glatt.mix(0xFFF2EFFA, 0xFFFFFFFF, hv), alpha);
        if (symbolPasst) {
            Glatt.symbol(g, symbol, sx, y + (h - s) / 2f, s, Glatt.alpha(Glatt.mix(hell, 0xFFFFFFFF, hv), alpha));
            sx += s + 4;
        }
        Glatt.text(g, Glatt.kuerzen(text, w - 8, Glatt.Schrift.FETT), sx, y + (h - 8) / 2f, textFarbe, Glatt.Schrift.FETT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
