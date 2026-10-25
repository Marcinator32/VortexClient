package com.vortex.client.cosmetics;

import com.vortex.client.gui.VortexStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Coins (seit 4.93): Guthaben, wie viel man insgesamt verdient hat, die Spielzeit der
 * letzten Tage (1 Coin pro Minute in einer Welt) und die letzten Kaeufe und Geschenke.
 */
public class ShopVerlaufScreen extends Screen {
    private static final int FENSTER_W = 360, FENSTER_H = 260;
    private static final DateTimeFormatter WANN = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private Shop.Verlauf verlauf;
    private String fehler;
    private int fx, fy;

    public ShopVerlaufScreen(Screen parent) {
        super(Component.literal("Coins"));
        this.parent = parent;
        Shop.verlauf().whenComplete((v, e) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (e != null) fehler = String.valueOf(com.vortex.client.social.Social.errorOf(e));
            else verlauf = v;
        }));
    }

    @Override
    protected void init() {
        fx = (this.width - FENSTER_W) / 2;
        fy = Math.max(6, (this.height - FENSTER_H) / 2);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(fx + FENSTER_W - 14 - 60, fy + FENSTER_H - 28, 60, 20).build());
    }

    private static String text(Shop.Buchung b) {
        return switch (b.art()) {
            case "buy" -> "Bought " + b.item();
            case "gift_sent" -> "Gift to " + b.anderer() + ": " + b.item();
            case "gift_received" -> "Gift from " + b.anderer() + ": " + b.item();
            case "starter" -> "Starter coins";
            case "admin" -> "From " + (b.anderer().isEmpty() ? "an admin" : b.anderer());
            default -> b.art();
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        VortexStyle.schatten(g, fx, fy, FENSTER_W, FENSTER_H, 1f);
        g.fill(fx, fy, fx + FENSTER_W, fy + FENSTER_H, VortexStyle.WINDOW);
        VortexStyle.akzentLinie(g, fx + 4, fy, FENSTER_W - 8, 1f);
        int x = fx + 14, y = fy + 12;
        g.text(this.font, "Coins", x, y, VortexStyle.TEXT, true);
        String stand = Shop.guthaben() + " coins";
        g.text(this.font, stand, fx + FENSTER_W - 14 - this.font.width(stand), y, 0xFFFDE68A, true);
        y += 14;
        int std = Shop.minuten() / 60, min = Shop.minuten() % 60;
        g.text(this.font, "Earned " + Shop.verdient() + " in total  ·  played " + std + " h " + min + " min", x, y, VortexStyle.TEXT_DIM, false);
        y += 11;
        g.text(this.font, "You get 1 coin for every minute you play in a world.", x, y, VortexStyle.TEXT_DIM, false);
        y += 18;
        int spalte = 110;
        g.text(this.font, "Last days", x, y, VortexStyle.TEXT, false);
        g.text(this.font, "Purchases and gifts", x + spalte + 10, y, VortexStyle.TEXT, false);
        y += 12;
        int unten = fy + FENSTER_H - 34;
        if (fehler != null) {
            g.text(this.font, fehler, x, y, 0xFFF87171, false);
        } else if (verlauf == null) {
            g.text(this.font, "Loading...", x, y, VortexStyle.TEXT_DIM, false);
        } else {
            int ty = y;
            if (verlauf.tage().isEmpty()) g.text(this.font, "Nothing yet", x, ty, VortexStyle.TEXT_DIM, false);
            for (Shop.Tag t : verlauf.tage()) {
                if (ty > unten - 10) break;
                g.text(this.font, t.tag().substring(5).replace('-', '.'), x, ty, VortexStyle.TEXT_DIM, false);
                String m = "+" + t.minuten();
                g.text(this.font, m, x + spalte - 6 - this.font.width(m), ty, 0xFF86EFAC, false);
                ty += 11;
            }
            int by = y, bx = x + spalte + 10, bw = fx + FENSTER_W - 14 - bx;
            if (verlauf.buchungen().isEmpty()) g.text(this.font, "Nothing yet", bx, by, VortexStyle.TEXT_DIM, false);
            for (Shop.Buchung b : verlauf.buchungen()) {
                if (by > unten - 10) break;
                String betrag = b.betrag() == 0 ? "" : (b.betrag() > 0 ? "+" : "") + b.betrag();
                g.text(this.font, this.font.plainSubstrByWidth(text(b), bw - 104), bx, by, VortexStyle.TEXT, false);
                g.text(this.font, WANN.format(Instant.ofEpochMilli(b.zeit())), bx + bw - this.font.width("00.00. 00:00"), by, VortexStyle.TEXT_DIM, false);
                if (!betrag.isEmpty()) g.text(this.font, betrag, bx + bw - this.font.width("00.00. 00:00") - 6 - this.font.width(betrag), by, b.betrag() > 0 ? 0xFF86EFAC : 0xFFFCA5A5, false);
                by += 11;
            }
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
