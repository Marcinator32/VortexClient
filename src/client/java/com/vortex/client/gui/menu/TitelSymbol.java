package com.vortex.client.gui.menu;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.MenuStil;
import com.vortex.client.gui.glatt.Symbole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Ein Eintrag der schlanken Vortex-Leiste unten im Hauptmenue (seit 4.84): Symbol und Name
 * in einer runden Flaeche, die erst unter der Maus aufleuchtet. Ohne Platz fuer den
 * Namen nur das Symbol, der Name kommt dann als Tooltip.
 */
public class TitelSymbol extends AbstractButton {
    private final Symbole.Symbol symbol;
    private final Runnable aktion;
    private final boolean mitText;

    public TitelSymbol(int x, int y, int w, int h, String text, Symbole.Symbol symbol, boolean mitText, Runnable aktion) {
        super(x, y, w, h, Component.literal(text));
        this.symbol = symbol;
        this.aktion = aktion;
        this.mitText = mitText;
        if (!mitText) setTooltip(Tooltip.create(Component.literal(text)));
    }

    @Override
    public void onPress(net.minecraft.client.input.InputWithModifiers input) {
        try {
            aktion.run();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("TitelSymbol", t);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        float alpha = Math.max(0f, Math.min(1f, getAlpha()));
        float hv = MenuStil.hover(this, active && isHoveredOrFocused());
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (hv > 0.01f) Glatt.rund(g, x, y, w, h, h / 2f, Glatt.alpha(TitelStil.A, 0.30f * hv * alpha));
        int farbe = Glatt.alpha(Glatt.mix(0xFFD6D2E4, 0xFFFFFFFF, hv), alpha);
        float s = 12f;
        if (!mitText) {
            Glatt.symbol(g, symbol, x + (w - s) / 2f, y + (h - s) / 2f, s, farbe);
            return;
        }
        Glatt.symbol(g, symbol, x + 8, y + (h - s) / 2f, s, Glatt.alpha(Glatt.mix(0xFFB9B4CC, 0xFFFFFFFF, hv), alpha));
        Glatt.text(g, getMessage().getString(), x + 8 + s + 5, y + (h - 8) / 2f, farbe, Glatt.Schrift.FETT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
