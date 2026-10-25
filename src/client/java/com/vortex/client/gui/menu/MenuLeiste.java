package com.vortex.client.gui.menu;

import com.vortex.client.account.AccountScreen;
import com.vortex.client.gui.FriendsScreen;
import com.vortex.client.gui.HomeScreen;
import com.vortex.client.gui.SkinScreen;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.MenuStil;
import com.vortex.client.gui.glatt.Symbole;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Vortex-Teile im Hauptmenue.
 *
 * Seit 4.84: Minecrafts Knoepfe und das Logo bleiben in der Mitte. Links steht dein
 * Charakter groesser auf einer Umlaufbahn, mit Namensschild, Cape und zwei Neon-Knoepfen
 * darunter (Cosmetics, Wardrobe). Die uebrigen Vortex-Bildschirme (Social, Pictures,
 * Settings, Account) sitzen als schlanke Leiste unten in der Mitte -- statt der alten
 * Kachelreihe. Ist links kein Platz fuer die Figur, kommen Cosmetics und Wardrobe mit in
 * die Leiste.
 *
 * Reicht die Hoehe nicht, rueckt Minecrafts Menue nach oben; reicht die Breite der Leiste
 * nicht, fallen die Namen weg (Tooltip).
 */
public final class MenuLeiste {
    private MenuLeiste() {}

    /** Abstand der Leiste zum unteren Rand (darunter stehen Version und Copyright). */
    private static final int UNTEN = 13;
    /** Breite des Vanilla-Menues in der Mitte (Knoepfe sind 200 breit). */
    private static final int MITTE = 204;
    private static final int SYMBOL = 20, RAND = 2, ABSTAND = 2;

    /**
     * Lage im zuletzt aufgebauten Hauptmenue.
     * groesse = 0: keine Figur. knopfY = -1: keine Neon-Knoepfe.
     */
    public record Lage(int logoOben, int leisteX, int leisteY, int leisteB, int leisteH,
                       float figurX, float fuss, float groesse, int schildY, int knopfY) {}

    private static volatile Lage lage;

    public static Lage lage() { return lage; }

    /** Oberkante des Logos (-1 = noch nicht bekannt). */
    public static int logoOben() { Lage l = lage; return l == null ? -1 : l.logoOben(); }

    private record Eintrag(String text, Symbole.Symbol symbol, Runnable aktion) {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, breite, hoehe) -> {
            if (!(screen instanceof TitleScreen)) return;
            try {
                if (!com.vortex.client.core.ClientSettings.INSTANCE.modernMenus.get()) {
                    lage = null;
                    return;
                }
                anlegen(screen, breite, hoehe);
            } catch (Throwable t) {
                lage = null;
                com.vortex.client.core.Errors.report("MenuLeiste", t);
            }
        });
    }

    private static void anlegen(Screen screen, int breite, int hoehe) {
        Minecraft mc = Minecraft.getInstance();
        int seite = (breite - MITTE) / 2;
        boolean figur = seite >= 100 && hoehe >= 200;

        Eintrag cosmetics = new Eintrag("Cosmetics", Symbole.Symbol.STERN, () -> mc.gui.setScreen(new com.vortex.client.cosmetics.CosmeticsScreen(screen)));
        Eintrag wardrobe = new Eintrag("Wardrobe", Symbole.Symbol.HEMD, () -> mc.gui.setScreen(new SkinScreen(screen)));
        List<Eintrag> eintraege = new ArrayList<>();
        if (!figur) {
            eintraege.add(cosmetics);
            eintraege.add(wardrobe);
        }
        eintraege.add(new Eintrag("Social", Symbole.Symbol.LEUTE, () -> mc.gui.setScreen(new FriendsScreen(screen))));
        eintraege.add(new Eintrag("Pictures", Symbole.Symbol.BILD, () -> mc.gui.setScreen(new PicturesScreen(screen))));
        eintraege.add(new Eintrag("Settings", Symbole.Symbol.REGLER, () -> mc.gui.setScreen(new HomeScreen())));
        eintraege.add(new Eintrag("Account", Symbole.Symbol.PERSON, () -> mc.gui.setScreen(new AccountScreen(screen))));

        boolean mitText = true;
        int leisteB = leistenBreite(eintraege, true);
        if (leisteB > breite - 16) {
            mitText = false;
            leisteB = leistenBreite(eintraege, false);
        }
        int leisteH = SYMBOL + RAND * 2;
        int leisteY = hoehe - UNTEN - leisteH;
        int leisteX = (breite - leisteB) / 2;

        List<AbstractWidget> mitte = new ArrayList<>();
        int oben = Integer.MAX_VALUE, untenMenue = Integer.MIN_VALUE;
        for (var c : screen.children()) {
            if (!(c instanceof AbstractWidget w) || !w.visible) continue;
            int cx = w.getX() + w.getWidth() / 2;
            if (Math.abs(cx - breite / 2) > MITTE / 2 + 4) continue;
            if (w.getY() >= hoehe - 14) continue;
            mitte.add(w);
            oben = Math.min(oben, w.getY());
            untenMenue = Math.max(untenMenue, w.getY() + w.getHeight());
        }
        int logoH = MenuStil.logoHoehe(breite) + 10;
        int ganzOben = 4 + logoH;
        if (!mitte.isEmpty()) {
            int zuViel = untenMenue - (leisteY - 8);
            if (zuViel > 0) {
                int schieben = Math.min(zuViel, Math.max(0, oben - ganzOben));
                for (AbstractWidget w : mitte) w.setY(w.getY() - schieben);
                oben -= schieben;
            }
        }
        int logoOben = mitte.isEmpty() ? 30 : Math.max(4, Math.min(30, oben - logoH));

        int x = leisteX + RAND;
        for (Eintrag e : eintraege) {
            int w = eintragBreite(e, mitText);
            Screens.getWidgets(screen).add(new TitelSymbol(x, leisteY + RAND, w, SYMBOL, e.text(), e.symbol(), mitText, e.aktion()));
            x += w + ABSTAND;
        }

        float figurX = 0, fuss = 0, groesse = 0;
        int schildY = 0, knopfY = -1;
        if (figur) {
            figurX = seite / 2f + 4;
            int kb = Math.min(78, (seite - 24 - 4) / 2), kh = 18;
            boolean untereinander = kb < 60;
            if (untereinander) {
                kb = Math.min(96, seite - 16);
                kh = 16;
            }
            int blockB = untereinander ? kb : kb * 2 + 4, blockH = untereinander ? kh * 2 + 4 : kh;
            int kx = Math.round(figurX - blockB / 2f);
            int boden = Math.min(leisteY - 10, Math.round(hoehe * 0.90f));
            if (leisteX > kx + blockB + 4) boden = Math.round(hoehe * 0.92f) - 4;
            knopfY = boden - blockH;
            int x2 = untereinander ? kx : kx + kb + 4, y2 = untereinander ? knopfY + kh + 4 : knopfY;
            Screens.getWidgets(screen).add(new NeonKnopf(kx, knopfY, kb, kh, cosmetics.text(), cosmetics.symbol(), false, cosmetics.aktion()));
            Screens.getWidgets(screen).add(new NeonKnopf(x2, y2, kb, kh, wardrobe.text(), wardrobe.symbol(), true, wardrobe.aktion()));
            schildY = knopfY - 9 - 24;
            fuss = schildY - 8;
            groesse = Math.min(Math.min(hoehe * 0.25f, seite * 0.45f), 92f);
            groesse = Math.min(groesse, (fuss - 14) / 2.4f);
            if (groesse < 24) groesse = 0;
        }
        lage = new Lage(logoOben, leisteX, leisteY, leisteB, leisteH, figurX, fuss, groesse, schildY, knopfY);
    }

    private static int eintragBreite(Eintrag e, boolean mitText) {
        return mitText ? 8 + 12 + 5 + Glatt.breite(e.text(), Glatt.Schrift.FETT) + 9 : SYMBOL;
    }

    private static int leistenBreite(List<Eintrag> eintraege, boolean mitText) {
        int b = RAND * 2 + (eintraege.size() - 1) * ABSTAND;
        for (Eintrag e : eintraege) b += eintragBreite(e, mitText);
        return b;
    }
}
