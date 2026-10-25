package com.vortex.client.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vortex.client.gui.VortexStyle;
import com.vortex.client.social.Social;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Admin-Schnellmenue im Spiel (seit 4.93, erweitert in 4.95; nur fuer Vortex-Admins -- der Server
 * prueft das selbst nach UUID). Links alle Items mit Suche, rechts das gewaehlte Item: Name, Preis,
 * Seltenheit, verschenkbar, limitiert, im Verkauf, Featured, nur fuer Creator -- speichern oder aus
 * dem Shop nehmen. Darunter Coins geben/nehmen und den Creator-Status eines Spielers setzen.
 * Jede Aenderung braucht einen Grund (unten) und steht im Admin-Protokoll. Alles Weitere
 * (Spieler, Wirtschaft, Protokoll) hat das Admin-Panel im Launcher.
 */
public class ShopAdminScreen extends Screen {
    private static final String[] SELTEN = { "common", "uncommon", "rare", "epic", "legendary", "mythic" };
    private static final int ZEILE = 12;

    private record Eintrag(String id, String name, int preis, String selten, boolean geschenk, boolean limitiert, boolean aktiv, boolean weg,
                           boolean featured, boolean creator) {}
    private static final String[] STATUS = { "pending", "accepted", "rejected", "suspended", "none" };

    private final Screen parent;
    private final List<Eintrag> alle = new ArrayList<>();
    private String suche = "", meldung, idWert = "", nameWert = "", preisWert = "", coinsWer = "", coinsWieviel = "";
    private String grundWert = "", creatorWer = "", creatorCode = "", creatorStatus = "accepted", coinsAnfrage = Shop.neueAnfrageId();
    private boolean featured, nurCreator;
    private Eintrag gewaehlt;
    private boolean neu;
    private String selten = "common";
    private boolean geschenk = true, limitiert, aktiv = true;
    private int scroll;
    private int lx, ly, lw, lh, rx;
    private EditBox sucheFeld, idFeld, nameFeld, preisFeld, coinsName, coinsBetrag, grundFeld, creatorName, creatorCodeFeld;

    public ShopAdminScreen(Screen parent) {
        super(Component.literal("Shop Admin"));
        this.parent = parent;
        laden();
    }

    private void laden() {
        Shop.adminItems().whenComplete((r, e) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (e != null) { meldung = String.valueOf(Social.errorOf(e)); return; }
            alle.clear();
            for (JsonElement el : r.getAsJsonArray("items")) {
                JsonObject o = el.getAsJsonObject();
                alle.add(new Eintrag(Social.str(o, "id"), Social.str(o, "name"), o.get("price").getAsInt(), Social.str(o, "rarity"),
                        o.get("giftable").getAsBoolean(), o.get("limited").getAsBoolean(), o.get("active").getAsBoolean(), o.get("deleted").getAsBoolean(),
                        o.has("featured") && o.get("featured").getAsBoolean(), o.has("creatorOnly") && o.get("creatorOnly").getAsBoolean()));
            }
            alle.sort((a, b) -> a.id().compareTo(b.id()));
            if (gewaehlt != null) for (Eintrag x : alle) if (x.id().equals(gewaehlt.id())) waehlen(x);
        }));
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 20, 560), h = Math.min(this.height - 20, 360);
        int x0 = (this.width - w) / 2, y0 = (this.height - h) / 2;
        lx = x0 + 10; ly = y0 + 46; lw = Math.max(150, w / 2 - 30); lh = h - 46 - 40;
        rx = lx + lw + 12;
        int rw = x0 + w - 10 - rx;
        sucheFeld = feld(lx, y0 + 26, lw, "Search items", suche, v -> { suche = v.toLowerCase(Locale.ROOT); scroll = 0; });
        idFeld = feld(rx, y0 + 36, rw, "id, e.g. hat:crown", idWert, v -> idWert = v);
        idFeld.setEditable(neu);
        nameFeld = feld(rx, y0 + 66, rw, "Name", nameWert, v -> nameWert = v);
        preisFeld = feld(rx, y0 + 96, 70, "Price", preisWert, v -> preisWert = v);
        addRenderableWidget(Button.builder(Component.literal("Rarity: " + gross(selten)), b -> {
            int i = java.util.Arrays.asList(SELTEN).indexOf(selten);
            selten = SELTEN[(i + 1) % SELTEN.length];
            rebuildWidgets();
        }).bounds(rx + 76, y0 + 95, rw - 76, 18).build());
        int dw = (rw - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Gift: " + (geschenk ? "Yes" : "No")), b -> { geschenk = !geschenk; rebuildWidgets(); })
                .bounds(rx, y0 + 116, dw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Limited: " + (limitiert ? "Yes" : "No")), b -> { limitiert = !limitiert; rebuildWidgets(); })
                .bounds(rx + dw + 4, y0 + 116, dw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("For sale: " + (aktiv ? "Yes" : "No")), b -> { aktiv = !aktiv; rebuildWidgets(); })
                .bounds(rx + 2 * (dw + 4), y0 + 116, rw - 2 * (dw + 4), 18).build());
        int hw = (rw - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Featured: " + (featured ? "Yes" : "No")), b -> { featured = !featured; rebuildWidgets(); })
                .bounds(rx, y0 + 137, hw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Creator only: " + (nurCreator ? "Yes" : "No")), b -> { nurCreator = !nurCreator; rebuildWidgets(); })
                .bounds(rx + hw + 4, y0 + 137, rw - hw - 4, 18).build());
        addRenderableWidget(Button.builder(Component.literal(neu ? "Create" : "Save"), b -> speichern())
                .bounds(rx, y0 + 159, 70, 20).build()).active = neu || gewaehlt != null;
        addRenderableWidget(Button.builder(Component.literal("Remove from shop"), b -> loeschen())
                .bounds(rx + 74, y0 + 159, 110, 20).build()).active = !neu && gewaehlt != null && !gewaehlt.weg();
        addRenderableWidget(Button.builder(Component.literal("New"), b -> {
                    neu = true; gewaehlt = null; selten = "common"; geschenk = true; limitiert = false; aktiv = true; featured = false; nurCreator = false;
                    idWert = ""; nameWert = ""; preisWert = "100"; rebuildWidgets(); })
                .bounds(rx + rw - 44, y0 + 159, 44, 20).build());
        coinsName = feld(rx, y0 + 206, rw - 104, "Player", coinsWer, v -> { coinsWer = v; coinsAnfrage = Shop.neueAnfrageId(); });
        coinsBetrag = feld(rx + rw - 100, y0 + 206, 52, "+100", coinsWieviel, v -> { coinsWieviel = v; coinsAnfrage = Shop.neueAnfrageId(); });
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> coinsGeben()).bounds(rx + rw - 44, y0 + 205, 44, 18).build());
        creatorName = feld(rx, y0 + 246, rw - 166, "Player", creatorWer, v -> creatorWer = v);
        creatorCodeFeld = feld(rx + rw - 162, y0 + 246, 60, "CODE", creatorCode, v -> creatorCode = v);
        addRenderableWidget(Button.builder(Component.literal(gross(creatorStatus)), b -> {
            int i = java.util.Arrays.asList(STATUS).indexOf(creatorStatus);
            creatorStatus = STATUS[(i + 1) % STATUS.length];
            rebuildWidgets();
        }).bounds(rx + rw - 98, y0 + 245, 54, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Set"), b -> creatorSetzen()).bounds(rx + rw - 40, y0 + 245, 40, 18).build());
        grundFeld = feld(x0 + 10 + 50, y0 + h - 28, w - 150, "Why? (required, saved in the admin log)", grundWert, v -> grundWert = v);
        grundFeld.setMaxLength(200);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x0 + w - 70, y0 + h - 29, 60, 20).build());
    }

    private EditBox feld(int x, int y, int w, String hinweis, String wert, java.util.function.Consumer<String> aenderung) {
        EditBox e = new EditBox(this.font, x, y, w, 16, Component.literal(hinweis));
        e.setMaxLength(60);
        e.setHint(Component.literal(hinweis));
        e.setValue(wert == null ? "" : wert);
        if (aenderung != null) e.setResponder(aenderung);
        addRenderableWidget(e);
        return e;
    }

    private static String gross(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private void waehlen(Eintrag e) {
        gewaehlt = e;
        neu = false;
        idWert = e.id();
        nameWert = e.name();
        preisWert = String.valueOf(e.preis());
        selten = e.selten();
        geschenk = e.geschenk();
        limitiert = e.limitiert();
        aktiv = e.aktiv();
        featured = e.featured();
        nurCreator = e.creator();
        rebuildWidgets();
    }

    private void speichern() {
        JsonObject o = new JsonObject();
        String id = neu ? idFeld.getValue().trim().toLowerCase(Locale.ROOT) : gewaehlt.id();
        o.addProperty("id", id);
        o.addProperty("name", nameFeld.getValue().trim());
        int preis;
        try { preis = Integer.parseInt(preisFeld.getValue().trim()); } catch (NumberFormatException ex) { meldung = "The price must be a number."; return; }
        o.addProperty("price", preis);
        o.addProperty("rarity", selten);
        o.addProperty("giftable", geschenk);
        o.addProperty("limited", limitiert);
        o.addProperty("active", aktiv);
        o.addProperty("featured", featured);
        o.addProperty("creatorOnly", nurCreator);
        String grund = grund();
        if (grund == null) return;
        o.addProperty("reason", grund);
        meldung = "Saving...";
        Shop.adminSpeichern(o).thenAccept(f -> this.minecraft.execute(() -> {
            meldung = f != null ? f : "Saved " + id + ".";
            if (f == null) { neu = false; gewaehlt = new Eintrag(id, "", preis, selten, geschenk, limitiert, aktiv, false, featured, nurCreator); laden(); }
        }));
    }

    private void loeschen() {
        if (gewaehlt == null) return;
        String id = gewaehlt.id();
        String grund = grund();
        if (grund == null) return;
        Shop.adminLoeschen(id, grund).thenAccept(f -> this.minecraft.execute(() -> {
            meldung = f != null ? f : id + " is no longer in the shop. Owners keep it.";
            laden();
        }));
    }

    private void coinsGeben() {
        int betrag;
        try { betrag = Integer.parseInt(coinsBetrag.getValue().trim().replace("+", "")); } catch (NumberFormatException ex) { meldung = "Enter an amount like 100 or -50."; return; }
        String an = coinsName.getValue().trim();
        if (an.isEmpty()) { meldung = "Enter a player name."; return; }
        String grund = grund();
        if (grund == null) return;
        String anfrage = coinsAnfrage;
        Shop.adminCoins(an, betrag, grund, anfrage).thenAccept(f -> this.minecraft.execute(() -> {
            meldung = f != null ? f : (betrag > 0 ? "Gave " : "Took ") + Math.abs(betrag) + " coins " + (betrag > 0 ? "to " : "from ") + an + ".";
            if (f == null) coinsAnfrage = Shop.neueAnfrageId();
        }));
    }

    private void creatorSetzen() {
        String an = creatorName.getValue().trim();
        if (an.isEmpty()) { meldung = "Enter a player name."; return; }
        String grund = grund();
        if (grund == null) return;
        String code = creatorCodeFeld.getValue().trim();
        String status = creatorStatus;
        Shop.adminCreator(an, status, code.isEmpty() ? null : code, grund).thenAccept(f -> this.minecraft.execute(() ->
                meldung = f != null ? f : an + " is now " + gross(status) + (code.isEmpty() ? "." : " with code " + code.toUpperCase(Locale.ROOT) + ".")));
    }

    /** Grund fuer das Protokoll; ohne Grund passiert nichts. */
    private String grund() {
        String g = grundFeld == null ? "" : grundFeld.getValue().trim();
        if (g.length() < 3) { meldung = "Write a reason at the bottom first (saved in the admin log)."; return null; }
        return g;
    }

    private List<Eintrag> sichtbar() {
        List<Eintrag> l = new ArrayList<>();
        for (Eintrag e : alle) if (suche.isEmpty() || e.id().contains(suche) || e.name().toLowerCase(Locale.ROOT).contains(suche)) l.add(e);
        return l;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int w = Math.min(this.width - 20, 560), h = Math.min(this.height - 20, 360);
        int x0 = (this.width - w) / 2, y0 = (this.height - h) / 2;
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        VortexStyle.schatten(g, x0, y0, w, h, 1f);
        g.fill(x0, y0, x0 + w, y0 + h, VortexStyle.WINDOW);
        VortexStyle.akzentLinie(g, x0 + 4, y0, w - 8, 1f);
        g.text(this.font, "Admin quick menu", x0 + 10, y0 + 10, VortexStyle.TEXT, true);
        g.text(this.font, alle.size() + " items  \u00b7  more in the launcher's Admin panel", x0 + 10 + this.font.width("Admin quick menu") + 8, y0 + 10, VortexStyle.TEXT_DIM, false);

        List<Eintrag> l = sichtbar();
        int max = Math.max(0, l.size() * ZEILE - lh);
        scroll = Math.max(0, Math.min(scroll, max));
        g.fill(lx, ly, lx + lw, ly + lh, VortexStyle.INNER);
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        int y = ly + 2 - scroll;
        for (Eintrag e : l) {
            if (y + ZEILE >= ly && y <= ly + lh) {
                boolean an = gewaehlt != null && gewaehlt.id().equals(e.id());
                boolean hov = mouseX >= lx && mouseX < lx + lw && mouseY >= y && mouseY < y + ZEILE && mouseY >= ly && mouseY < ly + lh;
                if (an || hov) g.fill(lx, y, lx + lw, y + ZEILE, an ? VortexStyle.HOV : 0x18FFFFFF);
                int farbe = e.weg() ? 0xFF6B6680 : Shop.farbe(e.selten());
                g.text(this.font, this.font.plainSubstrByWidth(e.id(), lw - 50), lx + 4, y + 2, farbe, false);
                String p = e.weg() ? "gone" : !e.aktiv() ? "off" : String.valueOf(e.preis());
                g.text(this.font, p, lx + lw - 4 - this.font.width(p), y + 2, VortexStyle.TEXT_DIM, false);
            }
            y += ZEILE;
        }
        g.disableScissor();

        int rw = x0 + w - 10 - rx;
        g.text(this.font, neu ? "New item" : gewaehlt != null ? "Edit item" : "Pick an item on the left", rx, y0 + 25, VortexStyle.TEXT, false);
        g.text(this.font, "Name", rx, y0 + 56, VortexStyle.TEXT_DIM, false);
        g.text(this.font, "Price (coins) and rarity", rx, y0 + 86, VortexStyle.TEXT_DIM, false);
        g.text(this.font, "Give (+) or take (-) coins", rx, y0 + 195, VortexStyle.TEXT_DIM, false);
        g.text(this.font, "Creator status and code", rx, y0 + 235, VortexStyle.TEXT_DIM, false);
        g.text(this.font, "Reason", x0 + 10, y0 + h - 24, VortexStyle.TEXT_DIM, false);
        if (meldung != null) g.text(this.font, this.font.plainSubstrByWidth(meldung, rw), rx, y0 + 183, VortexStyle.VIOLETT, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.x() >= lx && click.x() < lx + lw && click.y() >= ly && click.y() < ly + lh) {
            int i = (int) ((click.y() - ly - 2 + scroll) / ZEILE);
            List<Eintrag> l = sichtbar();
            if (i >= 0 && i < l.size()) { waehlen(l.get(i)); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= (int) (vertical * 24);
        return true;
    }

    @Override
    public void onClose() {
        Shop.laden();
        this.minecraft.gui.setScreen(parent);
    }
}
