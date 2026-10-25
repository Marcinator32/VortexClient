package com.vortex.client.gui;

import com.vortex.client.core.setting.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Eingabe fuer Text-Einstellungen (seit 4.32): Namen, Nachrichten und Listen
 * wie "minecraft:obsidian, minecraft:ender_chest". Oeffnet sich aus jedem
 * Einstellungsmenue mit einem Klick auf den Wert. Enter oder "Done"
 * uebernimmt, ESC oder "Cancel" verwirft.
 */
public class TextEingabeScreen extends Screen {

    private final Screen parent;
    private final StringSetting setting;
    private final String hinweis;
    private final java.util.function.Consumer<String> fertig;
    private EditBox feld;

    public TextEingabeScreen(Screen parent, StringSetting setting) {
        super(Component.literal(setting.getName()));
        this.parent = parent;
        this.setting = setting;
        this.hinweis = setting.hinweis();
        this.fertig = null;
    }

    /** Freie Eingabe ohne Einstellung: "fertig" bekommt den Text (nicht bei Abbrechen). */
    public TextEingabeScreen(Screen parent, String titel, String vorgabe, String hinweis, java.util.function.Consumer<String> fertig) {
        super(Component.literal(titel));
        this.parent = parent;
        this.setting = new StringSetting(titel, vorgabe == null ? "" : vorgabe, hinweis);
        this.hinweis = hinweis;
        this.fertig = fertig;
    }

    @Override
    protected void init() {
        int w = Math.min(320, this.width - 30);
        int x = (this.width - w) / 2, y = this.height / 2 - 20;
        feld = new EditBox(this.font, x, y, w, 20, Component.literal(setting.getName()));
        feld.setMaxLength(4096);
        feld.setValue(setting.get() == null ? "" : setting.get());
        addRenderableWidget(feld);
        setInitialFocus(feld);
        int bw = Math.min(100, (w - 6) / 2);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> fertig())
                .bounds(this.width / 2 - bw - 3, y + 30, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 + 3, y + 30, bw, 20).build());
    }

    private void fertig() {
        if (fertig != null) {
            String t = feld.getValue().trim();
            onClose();
            try { fertig.accept(t); } catch (Throwable e) { com.vortex.client.core.Errors.report("TextEingabe.fertig", e); }
            return;
        }
        setting.set(feld.getValue().trim());
        try {
            com.vortex.client.core.ConfigManager.save();
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("TextEingabe.save", e);
        }
        onClose();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || k == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            fertig();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        int y = this.height / 2 - 20;
        String titel = setting.getName();
        ctx.text(this.font, titel, (this.width - this.font.width(titel)) / 2, y - 26, 0xFFFFFFFF, true);
        if (hinweis != null && !hinweis.isEmpty()) {
            String h = Passend.kuerzen(this.font, hinweis, this.width - 20);
            ctx.text(this.font, h, (this.width - this.font.width(h)) / 2, y - 13, 0xFFA0A0A0, true);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
