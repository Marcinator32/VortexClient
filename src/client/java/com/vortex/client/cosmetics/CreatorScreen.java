package com.vortex.client.cosmetics;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Symbole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Vortex Creators (seit 4.95): das Programm, die Bewerbung ueber Discord, der eigene Status,
 * Code und das Profil, der Code, den man unterstuetzt, und die Liste aller Creator.
 * Status und Code vergeben nur Admins auf dem Freunde-Server.
 */
public class CreatorScreen extends Screen {
    private record Flaeche(int x, int y, int w, int h, Runnable aktion) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private static final int TEXT = 0xFFF1F1F4, DIM = 0xFF8C8C96, LEISE = 0xFF5E5E68;
    private static final int GRUND = 0xF5101013, KARTE = 0xFF17171B, FELD = 0xFF1D1D22, LINIE = 0xFF26262D, GOLD = 0xFFFBBF24;
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final List<Flaeche> flaechen = new ArrayList<>();
    private int fx, fy, fw, fh;
    private String meldung;
    private long meldungBis;
    private float scroll;

    public CreatorScreen(Screen parent) {
        super(Component.literal("Vortex Creators"));
        this.parent = parent;
        Creators.neuLaden();
    }

    @Override
    protected void init() {
        fw = Math.min(this.width - 16, 700);
        fh = Math.min(this.height - 16, 400);
        fx = (this.width - fw) / 2;
        fy = (this.height - fh) / 2;
    }

    private void melden(String s) {
        meldung = s;
        meldungBis = System.currentTimeMillis() + 5000;
    }

    private void antwort(String fehler, String ok) {
        this.minecraft.execute(() -> melden(fehler != null ? fehler : ok));
    }

    private static int akzent() { return com.vortex.client.gui.Theme.INSTANCE.accent.get() | 0xFF000000; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        flaechen.clear();
        g.fill(0, 0, this.width, this.height, 0xB8050507);
        Glatt.rund(g, fx, fy, fw, fh, 3, GRUND);
        Glatt.rahmen(g, fx, fy, fw, fh, 3, 1, LINIE);
        g.fill(fx + 1, fy + 33, fx + fw - 1, fy + 34, LINIE);
        Glatt.symbol(g, Symbole.Symbol.STERN, fx + 12, fy + 11, 12, GOLD);
        Glatt.text(g, "Vortex Creators", fx + 30, fy + 13, TEXT, Glatt.Schrift.FETT);
        knopf(g, fx + fw - 30, fy + 8, "x", false, true, mouseX, mouseY, this::onClose);

        Creators.Stand s = Creators.stand();
        int lx = fx + 14, lw = (fw - 42) / 2, rx = lx + lw + 14, rw = fw - 28 - lw - 14;
        int y = fy + 44;

        if (!Shop.aktiv() && Creators.fehler() != null) {
            Glatt.text(g, "The creator program needs the Vortex friends server.", lx, y, DIM, Glatt.Schrift.NORMAL);
        }

        int h1 = 96;
        karte(g, lx, y, lw, h1);
        Glatt.text(g, "Want to become a Vortex Creator?", lx + 10, y + 9, TEXT, Glatt.Schrift.FETT);
        zeilen(g, lx + 10, y + 23, lw - 20, "Make videos or streams with Vortex, get your own creator code, a [CREATOR] badge and creator-only cosmetics. Applications go through our Discord.");
        boolean kannBewerben = s.status().equals("none") || s.status().equals("rejected");
        knopf(g, lx + 10, y + h1 - 26, "Apply on Discord", true, true, mouseX, mouseY, () -> {
            net.minecraft.util.Util.getPlatform().openUri(s.discord().isEmpty() ? Creators.DISCORD : s.discord());
            if (kannBewerben) Creators.bewerben("").thenAccept(f -> antwort(f, "Discord opened. Your status is now Pending."));
        });
        y += h1 + 8;

        int h2 = 74;
        karte(g, lx, y, lw, h2);
        Glatt.text(g, "Your status", lx + 10, y + 9, TEXT, Glatt.Schrift.FETT);
        String st = Creators.statusName(s.status());
        float sb = Glatt.breite(st, Glatt.Schrift.FETT) + 16;
        Glatt.rund(g, lx + lw - 10 - sb, y + 6, sb, 15, 2, Glatt.alpha(Creators.statusFarbe(s.status()), 0.18f));
        Glatt.text(g, st, lx + lw - 10 - sb + 8, y + 10, Creators.statusFarbe(s.status()), Glatt.Schrift.FETT);
        String unter = switch (s.status()) {
            case "pending" -> "Applied on " + datum(s.beworben()) + ". An admin will update your status.";
            case "accepted" -> "Creator since " + datum(s.angenommen()) + ". " + s.unterstuetzer() + (s.unterstuetzer() == 1 ? " player supports" : " players support") + " you.";
            case "rejected" -> "Your last application was not accepted. You can apply again.";
            case "suspended" -> "Your creator status is paused. Ask an admin on Discord.";
            default -> "You have not applied yet.";
        };
        zeilen(g, lx + 10, y + 26, lw - 20, unter);
        if (s.status().equals("accepted")) {
            Glatt.text(g, "Your code", lx + 10, y + h2 - 20, DIM, Glatt.Schrift.NORMAL);
            String code = s.code().isEmpty() ? "none yet" : s.code() + (s.codeAktiv() ? "" : " (inactive)");
            Glatt.text(g, code, lx + 62, y + h2 - 21, s.code().isEmpty() ? LEISE : GOLD, Glatt.Schrift.FETT);
        }
        y += h2 + 8;

        int h3 = fy + fh - 12 - y;
        karte(g, lx, y, lw, h3);
        Glatt.text(g, "Benefits", lx + 10, y + 9, TEXT, Glatt.Schrift.FETT);
        String[] vorteile = {
            "[CREATOR] badge next to your name for every Vortex player",
            "Your own creator code -- supporters earn you " + s.anteil() + " % of every purchase",
            "Creator-only cosmetics, like the Creator Phoenix pet",
            "A public creator profile in the launcher and in game"
        };
        int vy = y + 24;
        for (String v : vorteile) {
            if (vy + 10 > y + h3) break;
            Glatt.kreis(g, lx + 13, vy + 4, 4, GOLD);
            Glatt.text(g, Glatt.kuerzen(v, lw - 30, Glatt.Schrift.NORMAL), lx + 20, vy, DIM, Glatt.Schrift.NORMAL);
            vy += 12;
        }

        y = fy + 44;
        int h4 = s.status().equals("accepted") ? 104 : 0;
        if (h4 > 0) {
            karte(g, rx, y, rw, h4);
            Glatt.text(g, "Your creator profile", rx + 10, y + 9, TEXT, Glatt.Schrift.FETT);
            String name = this.minecraft.player != null ? this.minecraft.player.getGameProfile().name() : "You";
            Glatt.text(g, "[CREATOR]", rx + 10, y + 24, GOLD, Glatt.Schrift.FETT);
            Glatt.text(g, name, rx + 14 + Glatt.breite("[CREATOR]", Glatt.Schrift.FETT), y + 24, TEXT, Glatt.Schrift.FETT);
            zeilen(g, rx + 10, y + 38, rw - 20, s.bio().isEmpty() ? "No description yet." : s.bio());
            String items = s.creatorItems().isEmpty() ? "-" : String.join(", ", s.creatorItems());
            Glatt.text(g, Glatt.kuerzen("Creator cosmetics: " + items, rw - 20, Glatt.Schrift.NORMAL), rx + 10, y + h4 - 40, DIM, Glatt.Schrift.NORMAL);
            int bx = knopf(g, rx + 10, y + h4 - 26, "Edit description", false, true, mouseX, mouseY, () ->
                    this.minecraft.gui.setScreen(new com.vortex.client.gui.TextEingabeScreen(this, "Creator description", s.bio(),
                            "Up to 200 characters, shown on your profile", t -> Creators.beschreiben(t).thenAccept(f -> antwort(f, "Description saved.")))));
            knopf(g, bx + 6, y + h4 - 26, "Creator cosmetics", false, true, mouseX, mouseY, () -> this.minecraft.gui.setScreen(new CosmeticsScreen(this)));
            y += h4 + 8;
        }

        int h5 = 74;
        karte(g, rx, y, rw, h5);
        Glatt.text(g, "Support a creator", rx + 10, y + 9, TEXT, Glatt.Schrift.FETT);
        if (s.unterstuetztCode().isEmpty()) {
            zeilen(g, rx + 10, y + 24, rw - 20, "Enter a creator code. Every purchase you make gives them " + s.anteil() + " % on top -- it costs you nothing.");
        } else {
            Glatt.text(g, "You support", rx + 10, y + 24, DIM, Glatt.Schrift.NORMAL);
            Glatt.text(g, s.unterstuetztCode(), rx + 14 + Glatt.breite("You support", Glatt.Schrift.NORMAL), y + 23, GOLD, Glatt.Schrift.FETT);
            Glatt.text(g, "(" + s.unterstuetztName() + ")", rx + 20 + Glatt.breite("You support", Glatt.Schrift.NORMAL) + Glatt.breite(s.unterstuetztCode(), Glatt.Schrift.FETT),
                    y + 24, LEISE, Glatt.Schrift.NORMAL);
            if (s.wechselAb() > System.currentTimeMillis()) Glatt.text(g, "You can change it on " + datum(s.wechselAb()) + ".", rx + 10, y + 37, LEISE, Glatt.Schrift.NORMAL);
        }
        int bx = knopf(g, rx + 10, y + h5 - 26, s.unterstuetztCode().isEmpty() ? "Enter code" : "Change code", true, true, mouseX, mouseY, () ->
                this.minecraft.gui.setScreen(new com.vortex.client.gui.TextEingabeScreen(this, "Creator code", "", "For example VORTEX", t ->
                        Creators.unterstuetzen(t).thenAccept(f -> antwort(f, "Thank you for supporting " + t.trim().toUpperCase(java.util.Locale.ROOT) + "!")))));
        if (!s.unterstuetztCode().isEmpty()) knopf(g, bx + 6, y + h5 - 26, "Remove", false, true, mouseX, mouseY, () ->
                Creators.unterstuetzen("").thenAccept(f -> antwort(f, "You no longer support a creator.")));
        y += h5 + 8;

        int h6 = fy + fh - 12 - y;
        karte(g, rx, y, rw, h6);
        Glatt.text(g, "Creators", rx + 10, y + 9, TEXT, Glatt.Schrift.FETT);
        List<Creators.Eintrag> liste = Creators.liste();
        int ly = y + 24, lh = h6 - 30;
        if (liste.isEmpty()) {
            Glatt.text(g, "No creators yet -- be the first!", rx + 10, ly, DIM, Glatt.Schrift.NORMAL);
        } else {
            float max = Math.max(0, liste.size() * 14 - lh);
            scroll = Math.max(0, Math.min(scroll, max));
            g.enableScissor(rx, ly, rx + rw, ly + lh);
            int zy = ly - Math.round(scroll);
            for (Creators.Eintrag e : liste) {
                if (zy + 12 > ly && zy < ly + lh) {
                    Glatt.text(g, Glatt.kuerzen(e.name(), rw / 2, Glatt.Schrift.FETT), rx + 10, zy, TEXT, Glatt.Schrift.FETT);
                    Glatt.text(g, e.code(), rx + rw / 2f, zy, GOLD, Glatt.Schrift.NORMAL);
                    Glatt.textRechts(g, e.unterstuetzer() + " supporters", rx + rw - 10, zy, LEISE, Glatt.Schrift.NORMAL);
                }
                zy += 14;
            }
            g.disableScissor();
        }

        if (meldung != null && System.currentTimeMillis() < meldungBis) {
            float b = Glatt.breite(meldung, Glatt.Schrift.NORMAL) + 20;
            Glatt.rund(g, fx + fw / 2f - b / 2f, fy + fh - 26, b, 17, 2, 0xF0202026);
            Glatt.text(g, meldung, fx + fw / 2f - b / 2f + 10, fy + fh - 21, TEXT, Glatt.Schrift.NORMAL);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private static String datum(long t) {
        return t <= 0 ? "-" : DATUM.format(Instant.ofEpochMilli(t));
    }

    private void karte(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (h <= 0) return;
        Glatt.rund(g, x, y, w, h, 2, KARTE);
        Glatt.rahmen(g, x, y, w, h, 2, 1, LINIE);
    }

    /** Text auf hoechstens drei Zeilen umbrechen. */
    private void zeilen(GuiGraphicsExtractor g, int x, int y, int w, String text) {
        String[] woerter = text.split(" ");
        StringBuilder z = new StringBuilder();
        int n = 0;
        for (String wort : woerter) {
            String probe = z.length() == 0 ? wort : z + " " + wort;
            if (Glatt.breite(probe, Glatt.Schrift.NORMAL) > w && z.length() > 0) {
                Glatt.text(g, z.toString(), x, y + n * 11, DIM, Glatt.Schrift.NORMAL);
                if (++n >= 3) return;
                z = new StringBuilder(wort);
            } else {
                z = new StringBuilder(probe);
            }
        }
        if (z.length() > 0) Glatt.text(g, Glatt.kuerzen(z.toString(), w, Glatt.Schrift.NORMAL), x, y + n * 11, DIM, Glatt.Schrift.NORMAL);
    }

    private int knopf(GuiGraphicsExtractor g, int x, int y, String text, boolean haupt, boolean aktiv, int mouseX, int mouseY, Runnable aktion) {
        int w = Math.max(20, (int) Glatt.breite(text, Glatt.Schrift.NORMAL) + 18);
        boolean hov = aktiv && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        int grund = haupt ? akzent() : FELD;
        if (hov) grund = Glatt.mix(grund, 0xFFFFFFFF, 0.1f);
        Glatt.rund(g, x, y, w, 18, 2, grund);
        if (!haupt) Glatt.rahmen(g, x, y, w, 18, 2, 1, LINIE);
        Glatt.textMitte(g, text, x + w / 2f, y + 5, TEXT, Glatt.Schrift.NORMAL);
        flaechen.add(new Flaeche(x, y, w, 18, aktion));
        return x + w;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        for (int i = flaechen.size() - 1; i >= 0; i--) {
            if (flaechen.get(i).in(click.x(), click.y())) { flaechen.get(i).aktion().run(); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= (float) vertical * 14f;
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
