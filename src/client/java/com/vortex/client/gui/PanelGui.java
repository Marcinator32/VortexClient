package com.vortex.client.gui;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.core.setting.Setting;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Klassisches Spaltenmenue.
 *
 * Eine Spalte je Kategorie, der Name oben, die Module darunter -- so wie bei
 * den grossen Clients. Alles auf einen Blick, kein Umschalten zwischen
 * Seiten, kein Detailfeld.
 *
 * BEDIENUNG
 *   Linksklick auf ein Modul     ein- und ausschalten
 *   Rechtsklick auf ein Modul    Einstellungen darunter auf- und zuklappen
 *   Klick auf das Plus rechts    ebenfalls auf- und zuklappen
 *   Mausrad ueber einer Spalte   diese Spalte scrollen
 *   Suchfeld oben                filtert alle Spalten gleichzeitig
 *   Klick auf einen Spaltenkopf  Kategorie ein- und ausklappen
 *   Spaltenkopf ziehen           Kategorie frei verschieben (4.6.2)
 *   Rechten Rand des Kopfes ziehen  Spalte breiter/schmaler (4.6.2)
 *   Leiste oben links            ganzes Menue kleiner/groesser, "Auto"
 *   "Reset Layout" oben rechts   zurueck zur automatischen Anordnung
 *
 * GROESSE (4.6.2): Passt nicht jede Kategorie in eine Reihe, verkleinert
 * sich das Menue von selbst ("Auto"), statt eine Spalte in eine zweite Reihe
 * zu schieben. Vorher sah das auf 1920x1080 (GUI-Skalierung 4) so aus: vier
 * Spalten oben, eine allein darunter.
 *
 * Die Einstellungen werden ueber genau dieselben Aufrufe geaendert wie im
 * bisherigen Menue (ClickGui) -- dort sind sie erprobt. Gespeichert wird beim
 * Schliessen, ebenfalls wie dort.
 */
public class PanelGui extends Screen {

    private static final int SPALTE_B = 118;
    private static final int SPALTE_MIN = 92;
    private static final int KOPF_H   = 20;
    private static final int ZEILE_H  = 16;
    private static final int EINST_H  = 14;
    private static final int INNEN_RAND = 4;
    private static final int AUSWAHL_H = 15;
    private static final int ABSTAND  = 8;
    private static final int OBEN     = 34;
    private static final int PLUS_B   = 16;

    /** Nur diese Kategorie zeigen (fuer die Bots-Kachel), sonst null. */
    private final Module.Category nur;

    private EditBox search;
    private int mx, my;
    /** Echte (unskalierte) Mauskoordinaten -- fuer die Leiste oben. */
    private int rmx, rmy;
    private long letzteZeit = 0;
    private float oeffnen = 0f;
    /** Sekunden seit dem Oeffnen -- fuer das gestaffelte Erscheinen. */
    private float seit = 0f;

    /** Aufgeklappte Module und ihr Klappzustand 0..1. */
    private final Map<Module, Float> klapp = new HashMap<>();
    private final Map<Module, Boolean> offen = new HashMap<>();
    /** Modul unter dem Zeiger (fuer die Beschreibung) und seit wann. */
    private Module tipModul = null;
    private long tipSeit = 0;
    private float tipA = 0f;
    private Module tipZuletzt = null;

    /** Hervorhebung je Modul beim Ueberfahren, 0..1. */
    private final Map<Module, Float> hover = new HashMap<>();
    /** Ein/Aus je Modul, gleitend 0..1. */
    private final Map<Module, Float> anAnim = new HashMap<>();
    /** Gleitende Werte je Einstellung (Schalter, Schieberegler). */
    private final Map<Setting, Float> einstAnim = new HashMap<>();
    /** Zeit des letzten Bildes, fuer Animationen in Hilfsmethoden. */
    private float letzteDt = 0.016f;
    /** Bildlauf je Spalte. */
    private final Map<Module.Category, float[]> lauf = new HashMap<>();
    /**
     * Eingeklappte Kategorien. Beim Oeffnen des Menues ist alles aufgeklappt;
     * der Zustand bleibt erhalten, solange das Menue offen ist (auch beim
     * Zurueckkommen aus Farbwaehler oder Auswahlliste).
     */
    private final Map<Module.Category, Boolean> katZu = new HashMap<>();
    /** Klappzustand je Kategorie, gleitend: 1 = offen, 0 = zu. */
    private final Map<Module.Category, Float> katAnim = new HashMap<>();
    /** Hervorhebung des Kopfes beim Ueberfahren, 0..1. */
    private final Map<Module.Category, Float> kopfHover = new HashMap<>();

    /** Tatsaechliche Spaltenbreite dieses Bildes (<= SPALTE_B). */
    private int spalteB = SPALTE_B;
    /** Unterkante der aktuellen Spalte (Ende ihrer Reihe). */
    private int spalteBoden = 0;

    private NumberSetting zieht = null;
    private int ziehX, ziehB;

    /** Massstab dieses Bildes; die Spalten werden damit verkleinert gezeichnet. */
    private float masstab = 1f;
    /** Logische Groesse (Bildschirm geteilt durch Massstab). */
    private int W = 1, H = 1;
    /** Aktuelle Lage jeder Spalte: x, y, Breite (logisch). */
    private final Map<Module.Category, int[]> spaltenRect = new HashMap<>();
    /** Kopf wird gezogen: welche Kategorie, und hat sie sich schon bewegt? */
    private Module.Category ziehtKat = null;
    private boolean kopfBewegt = false;
    private int kopfOffX, kopfOffY, kopfStartX, kopfStartY;
    /** Breite wird gezogen. */
    private Module.Category ziehtBreite = null;
    private int breiteStart, breiteStartX;
    /** Klickflaechen der Leiste oben (echte Bildschirmkoordinaten): x, y, b, h, art. */
    private final List<int[]> leiste = new ArrayList<>();
    private static final int L_KLEINER = 1, L_AUTO = 2, L_GROESSER = 3, L_RESET = 4, L_SETTINGS = 5;

    private enum Art { KOPF, MODUL, BOOL, NUM, MODUS, FARBE, TASTE, AUSWAHL, RESET, GRIFF, TEXT, GRUPPE, HUDSTIL }

    /** Hoehe der Look-Leiste oben in der HUD-Spalte (seit 4.76). */
    private static final int STIL_H = 28;

    private static final class Treffer {
        final int x, y, w, h;
        final Art art;
        final Module modul;
        final Setting einst;
        Module.Category kat;
        com.vortex.client.module.Gruppe gruppe;
        int nr;
        Treffer(int x, int y, int w, int h, Art art, Module m, Setting s) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.art = art; this.modul = m; this.einst = s;
        }
        boolean in(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
    private final List<Treffer> treffer = new ArrayList<>();
    /** Spaltenbereiche fuer das Mausrad. */
    private final List<int[]> spaltenFlaeche = new ArrayList<>();
    private final List<Module.Category> spaltenKat = new ArrayList<>();

    public PanelGui() { this(null); }

    public PanelGui(Module.Category nur) {
        super(Component.literal("Vortex Client"));
        this.nur = nur;
    }

    @Override
    protected void init() {
        String bisher = (this.search != null) ? this.search.getValue() : "";
        int sw = sucheB();
        this.search = new EditBox(this.font, sucheX() + 8, 11, sw - 16, 12,
                Component.literal(""));
        this.search.setBordered(false);
        this.search.setMaxLength(32);
        this.search.setValue(bisher);
        this.search.setResponder(text -> lauf.clear());
        this.addRenderableWidget(this.search);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.rmx = mouseX;
        this.rmy = mouseY;
        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        oeffnen = weich(oeffnen, 1f, 10f, dt);
        seit += dt;
        letzteDt = dt;
        float a = oeffnen;

        treffer.clear();
        spaltenFlaeche.clear();
        spaltenKat.clear();
        tasteAufnehmen();
        tipKandidat = null;

        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(0x66000000, a));

        int rand = Math.max(40, this.height / 4);
        for (int i = 0; i < 12; i++) {
            float t = 1f - i / 12f;
            int al = (int) (70 * t * t * a);
            int h1 = rand * i / 12, h2 = rand * (i + 1) / 12;
            ctx.fill(0, h1, this.width, h2, al << 24);
            ctx.fill(0, this.height - h2, this.width, this.height - h1, al << 24);
        }

        zeichneSuche(ctx, a);

        List<Module.Category> kats = new ArrayList<>();
        for (Module.Category c : Module.Category.values()) {
            if (nur != null && c != nur) continue;
            if (nur == null && c == Module.Category.BOTS) continue;
            if (!module(c).isEmpty()) kats.add(c);
        }

        masstab = berechneMasstab(kats.size());
        this.mx = (int) (mouseX / masstab);
        this.my = (int) (mouseY / masstab);
        W = Math.max(1, (int) (this.width / masstab));
        H = Math.max(1, (int) (this.height / masstab));
        zeichneLeiste(ctx, a);
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.scale(masstab, masstab);

        int n = Math.max(1, kats.size());
        int verfuegbar = W - 12;
        int proReihe = Math.max(1, Math.min(n, (verfuegbar + ABSTAND) / (SPALTE_MIN + ABSTAND)));
        int autoB = Math.min(SPALTE_B, (verfuegbar - (proReihe - 1) * ABSTAND) / proReihe);
        int oben = (int) Math.ceil(OBEN / masstab);

        int[] bs = new int[kats.size()];
        for (int i = 0; i < kats.size(); i++) {
            int eigene = GuiState.getSpalteBreite(kats.get(i).name());
            bs[i] = eigene > 0 ? eigene : autoB;
        }
        List<List<Integer>> reihenL = new ArrayList<>();
        List<Integer> reihe = new ArrayList<>();
        int belegt = 0;
        for (int i = 0; i < kats.size(); i++) {
            int noetig = (reihe.isEmpty() ? 0 : ABSTAND) + bs[i];
            if (!reihe.isEmpty() && belegt + noetig > verfuegbar) {
                reihenL.add(reihe);
                reihe = new ArrayList<>();
                belegt = 0;
                noetig = bs[i];
            }
            reihe.add(i);
            belegt += noetig;
        }
        if (!reihe.isEmpty()) reihenL.add(reihe);
        int reiheH = (H - oben - 6) / Math.max(1, reihenL.size());
        int[] ax = new int[kats.size()], ay = new int[kats.size()];
        for (int r = 0; r < reihenL.size(); r++) {
            int rb = 0;
            for (int k : reihenL.get(r)) rb += bs[k] + (rb == 0 ? 0 : ABSTAND);
            int x = Math.max(6, (W - rb) / 2);
            for (int k : reihenL.get(r)) {
                ax[k] = x;
                ay[k] = oben + r * reiheH;
                x += bs[k] + ABSTAND;
            }
        }

        spaltenRect.clear();
        int zuletzt = -1;
        for (int durchgang = 0; durchgang < 2; durchgang++) {
            for (int i = 0; i < kats.size(); i++) {
                Module.Category kat = kats.get(i);
                boolean istGezogen = kat == ziehtKat && kopfBewegt;
                if ((durchgang == 0) == istGezogen) continue;
                int sx = ax[i], sy = ay[i], boden = ay[i] + reiheH - 6;
                float[] pos = GuiState.getSpaltePos(kat.name());
                if (pos != null) {
                    sx = Math.max(0, Math.min(W - bs[i], Math.round(pos[0] * W)));
                    sy = Math.max(oben, Math.min(H - KOPF_H - 2, Math.round(pos[1] * H)));
                    boden = H - 6;
                }
                spalteB = bs[i];
                spalteBoden = boden;
                spaltenRect.put(kat, new int[]{sx, sy, bs[i]});
                float t = Math.max(0f, Math.min(1f, (seit - i * 0.045f) / 0.32f));
                float e = 1f - (1f - t) * (1f - t) * (1f - t);
                int gleiten = (int) ((1f - e) * -16f);
                zeichneSpalte(ctx, kat, sx, sy + gleiten, a * e, dt);
                zuletzt = i;
            }
        }
        pose.popMatrix();

        super.extractRenderState(ctx, mouseX, mouseY, delta);
        pose.pushMatrix();
        pose.scale(masstab, masstab);
        if (com.vortex.client.core.ClientSettings.INSTANCE.moduleTooltips.get()) zeichneBeschreibung(ctx, dt);
        pose.popMatrix();
    }

    /**
     * Massstab: eingestellt, oder "Auto" -- dann so klein wie noetig, damit
     * alle Kategorien in EINE Reihe passen (hoechstens bis 60 %).
     */
    private float berechneMasstab(int n) {
        int p = GuiState.getPanelScale();
        if (p > 0) return p / 100f;
        n = Math.max(1, n);
        int noetig = n * SPALTE_MIN + (n - 1) * ABSTAND + 12;
        if (this.width >= noetig) return 1f;
        return Math.max(0.6f, this.width / (float) noetig);
    }

    /** Leiste oben: Menuegroesse links, "Reset Layout" rechts (echte Pixel). */
    private void zeichneLeiste(GuiGraphicsExtractor ctx, float a) {
        leiste.clear();
        int y = 7, h = 18;
        boolean auto = GuiState.getPanelScale() == 0;
        String wert = (auto ? "Auto " : "") + Math.round(masstab * 100) + "%";
        int wb = Math.max(58, this.font.width(wert) + 10);
        int[][] teile = {
            {6, y, 16, h, L_KLEINER},
            {24, y, wb, h, L_AUTO},
            {26 + wb, y, 16, h, L_GROESSER},
            {this.width - 6 - resetB(), y, resetB(), h, L_RESET},
            {this.width - 6 - resetB() - 4 - settingsB(), y, settingsB(), h, L_SETTINGS}
        };
        for (int[] t : teile) {
            boolean hov = rmx >= t[0] && rmx < t[0] + t[2] && rmy >= t[1] && rmy < t[1] + t[3];
            roundRect(ctx, t[0], t[1], t[2], t[3], VortexStyle.fade(hov
                    ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.35f) : VortexStyle.CARD, a));
            String txt = t[4] == L_KLEINER ? "-" : t[4] == L_GROESSER ? "+" : t[4] == L_AUTO ? wert
                    : t[4] == L_SETTINGS ? "Settings" : "Reset Layout";
            int farbe = (t[4] == L_AUTO && auto) ? VortexStyle.akzent(0.5f) : (hov ? 0xFFFFFFFF : VortexStyle.TEXT_DIM);
            ctx.text(this.font, Component.literal(txt), t[0] + (t[2] - this.font.width(txt)) / 2, t[1] + 5,
                    VortexStyle.fade(farbe, a), false);
            leiste.add(t);
        }
    }

    /** Klick in die Leiste oben? Dann erledigt. */
    private boolean leisteGeklickt(double px, double py) {
        for (int[] t : leiste) {
            if (px < t[0] || px >= t[0] + t[2] || py < t[1] || py >= t[1] + t[3]) continue;
            int jetzt = Math.round(masstab * 100 / 10f) * 10;
            switch (t[4]) {
                case L_KLEINER:  GuiState.setPanelScale(Math.max(50, jetzt - 10)); break;
                case L_GROESSER: GuiState.setPanelScale(Math.min(150, jetzt + 10)); break;
                case L_AUTO:
                    GuiState.setPanelScale(GuiState.getPanelScale() == 0 ? Math.round(masstab * 100) : 0);
                    break;
                case L_RESET:
                    GuiState.resetSpalten();
                    lauf.clear();
                    break;
                case L_SETTINGS:
                    Minecraft.getInstance().gui.setScreen(new SettingsScreen(this, "Client Settings",
                            com.vortex.client.core.ClientSettings.INSTANCE.all()));
                    break;
                default: break;
            }
            return true;
        }
        return false;
    }

    /** Modul, ueber dem der Zeiger in diesem Bild steht. */
    private Module tipKandidat = null;

    /**
     * BESCHREIBUNG BEIM UEBERFAHREN.
     *
     * Bleibt der Zeiger kurz (0,45 s) auf einem Modul, blendet ein kleines
     * Feld mit der Beschreibung ein (ModuleInfo -- dieselben Texte fuer
     * Client und Addon). Nicht sofort: sonst flackert beim Ueberfahren der
     * Liste staendig etwas auf.
     */
    private void zeichneBeschreibung(GuiGraphicsExtractor ctx, float dt) {
        long jetzt = System.currentTimeMillis();
        if (tipKandidat != tipModul) {
            tipModul = tipKandidat;
            tipSeit = jetzt;
        }
        boolean zeigen = tipModul != null && jetzt - tipSeit > 450;
        if (zeigen) tipZuletzt = tipModul;
        tipA = weich(tipA, zeigen ? 1f : 0f, zeigen ? 14f : 22f, dt);
        if (tipA < 0.02f || tipZuletzt == null) return;

        String text = ModuleInfo.get(tipZuletzt.getName());
        if (text == null || text.isBlank()) return;

        int maxB = Math.min(200, W - 20);
        List<String> zeilen = new ArrayList<>();
        StringBuilder z = new StringBuilder();
        for (String wort : text.split(" ")) {
            String probe = z.length() == 0 ? wort : z + " " + wort;
            if (this.font.width(probe) > maxB && z.length() > 0) {
                zeilen.add(z.toString());
                z = new StringBuilder(wort);
            } else {
                z = new StringBuilder(probe);
            }
        }
        if (z.length() > 0) zeilen.add(z.toString());

        int b = 0;
        for (String l : zeilen) b = Math.max(b, this.font.width(l));
        int kopf = 12;
        int w = Math.max(b, this.font.width(tipZuletzt.getName())) + 12;
        int h = kopf + zeilen.size() * 10 + 8;
        int x = mx + 12, y = my + 10;
        if (x + w > W - 4) x = mx - w - 8;
        if (y + h > H - 4) y = H - 4 - h;
        if (x < 4) x = 4;
        if (y < 4) y = 4;

        float a = tipA;
        VortexStyle.schatten(ctx, x, y, w, h, a * 0.8f);
        roundRect(ctx, x, y, w, h, VortexStyle.fade(0xF5120E1B, a));
        ctx.fill(x + 2, y, x + w - 2, y + 1, VortexStyle.fade(VortexStyle.akzent(0.4f), a));
        ctx.text(this.font, Component.literal(tipZuletzt.getName()), x + 6, y + 5,
                VortexStyle.fade(0xFFFFFFFF, a), false);
        int ty = y + 5 + kopf;
        for (String l : zeilen) {
            ctx.text(this.font, Component.literal(l), x + 6, ty, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
            ty += 10;
        }
    }

    /** Suchfeld: mittig, aber nie ueber der Leiste links/rechts. */
    private int sucheX() {
        return Math.max(this.width / 2 - 90, 108);
    }

    private int sucheB() {
        int rechts = this.width - 6 - resetB() - 4 - settingsB() - 6;
        return Math.max(60, Math.min(180, rechts - sucheX()));
    }

    private int settingsB() {
        return this.font.width("Settings") + 12;
    }

    private int resetB() {
        return this.font.width("Reset Layout") + 12;
    }

    private void zeichneSuche(GuiGraphicsExtractor ctx, float a) {
        int sw = sucheB(), sx = sucheX(), sy = 7;
        boolean tippt = search != null && !search.getValue().isEmpty();
        roundRect(ctx, sx, sy, sw, 18, VortexStyle.fade(VortexStyle.CARD, a));
        if (tippt) {
            ctx.fill(sx + 3, sy + 17, sx + sw - 3, sy + 18, VortexStyle.fade(VortexStyle.akzent(0.5f), a));
        }
        if (search != null && search.getValue().isEmpty()) {
            ctx.text(this.font, Component.literal("Search..."), sx + 8, sy + 5,
                    VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
        }
    }

    private void zeichneSpalte(GuiGraphicsExtractor ctx, Module.Category kat,
                               int x, int y, float a, float dt) {
        List<Module> liste = gefiltert(kat);

        int an = 0;
        for (Module m : module(kat)) if (m.isEnabled()) an++;
        boolean sucht = search != null && !search.getValue().trim().isEmpty();
        boolean zu = katZu.getOrDefault(kat, false) && !sucht;
        float ka = weich(katAnim.getOrDefault(kat, zu ? 0f : 1f), zu ? 0f : 1f, 13f, dt);
        katAnim.put(kat, ka);
        boolean kopfHov = mx >= x && mx < x + spalteB && my >= y && my < y + KOPF_H;
        float kh = weich(kopfHover.getOrDefault(kat, 0f), kopfHov ? 1f : 0f, 18f, dt);
        kopfHover.put(kat, kh);

        roundRect(ctx, x, y, spalteB, KOPF_H, VortexStyle.fade(
                VortexStyle.mix(VortexStyle.BAR, VortexStyle.HOV, kh * 0.8f), a));
        verlaufBand(ctx, x + 2, y + 1, spalteB - 4, KOPF_H - 3,
                VortexStyle.fade(VortexStyle.VIOLETT, a * (0.16f + 0.10f * kh)),
                VortexStyle.fade(VortexStyle.BLAU, 0));
        VortexStyle.akzentLinie(ctx, x + 3, y + KOPF_H - 2, spalteB - 6, a * (0.45f + 0.55f * ka));
        ctx.text(this.font, Component.literal(schoen(kat.name())), x + 8, y + 6,
                VortexStyle.fade(VortexStyle.TEXT, a), false);
        pfeil(ctx, x + spalteB - 12, y + 7, ka,
                VortexStyle.fade(VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, kh), a));
        String zahl = an + "/" + module(kat).size();
        int zahlX = x + spalteB - 17 - this.font.width(zahl);
        if (zahlX > x + 8 + this.font.width(schoen(kat.name())) + 4) {
            ctx.text(this.font, Component.literal(zahl), zahlX,
                    y + 6, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
        }
        Treffer kopf = new Treffer(x, y, spalteB, KOPF_H, Art.KOPF, null, null);
        kopf.kat = kat;
        treffer.add(kopf);
        boolean griffHov = mx >= x + spalteB - 3 && mx < x + spalteB + 2 && my >= y && my < y + KOPF_H;
        if (griffHov || ziehtBreite == kat) {
            ctx.fill(x + spalteB - 2, y + 3, x + spalteB, y + KOPF_H - 3,
                    VortexStyle.fade(VortexStyle.akzent(0.5f), a));
        }
        Treffer griff = new Treffer(x + spalteB - 3, y, 5, KOPF_H, Art.GRIFF, null, null);
        griff.kat = kat;
        treffer.add(griff);

        int maxH = Math.max(ZEILE_H, spalteBoden - y - KOPF_H);
        boolean stil = kat == Module.Category.HUD && !sucht;
        int inhaltH = (kat == Module.Category.CHEATS ? gruppenHoehe(liste, sucht) : inhaltHoehe(liste)) + (stil ? STIL_H : 0);
        int sichtbar = Math.min(inhaltH, maxH);
        float[] l = lauf.computeIfAbsent(kat, k -> new float[2]);
        float maxLauf = Math.max(0, inhaltH - sichtbar);
        if (l[1] > maxLauf) l[1] = maxLauf;
        l[0] = weich(l[0], l[1], 16f, dt);

        int top = y + KOPF_H;
        int koerper = (int) ((Math.max(4, sichtbar + 2)) * ka);
        if (koerper < 2) {
            VortexStyle.schatten(ctx, x, y, spalteB, KOPF_H, a * 0.9f);
            spaltenFlaeche.add(new int[]{x, y, spalteB, KOPF_H});
            spaltenKat.add(kat);
            return;
        }
        VortexStyle.schatten(ctx, x, y, spalteB, KOPF_H + koerper, a * 0.9f);
        roundRect(ctx, x, top, spalteB, koerper,
                VortexStyle.fade(VortexStyle.WINDOW, a * 0.94f));
        ctx.fill(x + 3, top, x + spalteB - 3, top + 1,
                VortexStyle.fade(VortexStyle.mix(VortexStyle.WINDOW, VortexStyle.TEXT, 0.06f), a));
        spaltenFlaeche.add(new int[]{x, y, spalteB, KOPF_H + koerper});
        spaltenKat.add(kat);

        float ia = a * Math.min(1f, ka * 1.4f);
        ctx.enableScissor(x, top, x + spalteB, top + koerper);
        int cy = top + 1 - (int) l[0];
        if (kat == Module.Category.CHEATS) {
            for (var e : com.vortex.client.module.Gruppe.aufteilen(liste).entrySet()) {
                cy = zeichneGruppe(ctx, e.getKey(), e.getValue(), x, cy, top, top + koerper, ia, dt, sucht);
                if (sucht || GuiState.gruppeOffen(e.getKey().name())) {
                    for (Module m : e.getValue()) cy = zeichneModul(ctx, m, x, cy, top, top + koerper, ia, dt);
                }
            }
        } else {
            if (stil) cy = zeichneHudStil(ctx, x, cy, top, top + koerper, ia);
            for (Module m : liste) {
                cy = zeichneModul(ctx, m, x, cy, top, top + koerper, ia, dt);
            }
        }
        ctx.disableScissor();

        if (ka > 0.98f && inhaltH > sichtbar && sichtbar > 20) {
            int spur = sichtbar - 6;
            int balken = Math.max(14, (int) (spur * (sichtbar / (float) inhaltH)));
            float pos = maxLauf <= 0 ? 0f : l[0] / maxLauf;
            int by = top + 3 + (int) ((spur - balken) * Math.max(0f, Math.min(1f, pos)));
            ctx.fill(x + spalteB - 3, by, x + spalteB - 1, by + balken,
                    VortexStyle.fade(VortexStyle.mix(VortexStyle.akzent(0.5f), VortexStyle.TEXT, 0.2f), a * 0.7f));
        }

        if (liste.isEmpty()) {
            ctx.text(this.font, Component.literal("-"), x + 8, top + 4,
                    VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
        }
    }

    /**
     * Look-Leiste der HUD-Spalte: alle HUD-Looks als kleine Karten, ein Klick stellt jedes
     * HUD-Element um; darunter der Name und "Edit" fuer den HUD-Style-Bildschirm.
     */
    private int zeichneHudStil(GuiGraphicsExtractor ctx, int x, int y, int oben, int unten, float a) {
        java.util.List<com.vortex.client.hud.HudLook.Look> looks = com.vortex.client.hud.HudLook.LOOKS;
        int gew = com.vortex.client.hud.HudLook.aktuell();
        int innen = spalteB - 8, n = looks.size(), luecke = 2;
        int kw = Math.max(8, (innen - luecke * (n - 1)) / n), kh = 12;
        ctx.fill(x + 3, y + 1, x + spalteB - 3, y + STIL_H - 2, VortexStyle.fade(VortexStyle.INNER, a));
        String name = gew >= 0 ? looks.get(gew).name() : "Custom";
        for (int i = 0; i < n; i++) {
            int kx = x + 4 + i * (kw + luecke), ky = y + 3;
            boolean hov = mx >= kx && mx < kx + kw && my >= ky && my < ky + kh;
            if (hov) name = looks.get(i).name();
            int rand = i == gew ? VortexStyle.akzent(i / (float) n) : hov ? VortexStyle.TEXT_DIM : VortexStyle.LINE;
            ctx.fill(kx - 1, ky - 1, kx + kw + 1, ky + kh + 1, VortexStyle.fade(rand, a));
            Farbthema.rohAn();
            try {
                ctx.fill(kx, ky, kx + kw, ky + kh, VortexStyle.fade(0xFF3A5A2A, a));
                com.vortex.client.hud.HudLook.mini(ctx, looks.get(i), kx + 2, ky + 2, kw - 4, kh - 4, a);
            } finally {
                Farbthema.rohAus();
            }
            if (ky >= oben && ky + kh <= unten) {
                Treffer t = new Treffer(kx, ky, kw, kh, Art.HUDSTIL, null, null);
                t.nr = i;
                treffer.add(t);
            }
        }
        int ty = y + kh + 6;
        ctx.text(this.font, Component.literal("Look: " + name), x + 6, ty, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
        String edit = "Edit ›";
        int ew = this.font.width(edit);
        boolean eHov = mx >= x + spalteB - 8 - ew && mx < x + spalteB - 4 && my >= ty - 2 && my < ty + 9;
        ctx.text(this.font, Component.literal(edit), x + spalteB - 6 - ew, ty,
                VortexStyle.fade(eHov ? VortexStyle.TEXT : VortexStyle.akzent(0.5f), a), false);
        if (ty - 2 >= oben && ty + 9 <= unten) {
            Treffer t = new Treffer(x + spalteB - 8 - ew, ty - 2, ew + 4, 11, Art.HUDSTIL, null, null);
            t.nr = -1;
            treffer.add(t);
        }
        return y + STIL_H;
    }

    private static final int GRUPPE_H = 16;
    private final Map<com.vortex.client.module.Gruppe, Float> gruppeHover = new HashMap<>();
    private final Map<com.vortex.client.module.Gruppe, Float> gruppeAnim = new HashMap<>();

    /** Kopfzeile einer Cheat-Gruppe: Farbstreifen, Name, Zaehler, Pfeil. Klick klappt auf/zu. */
    private int zeichneGruppe(GuiGraphicsExtractor ctx, com.vortex.client.module.Gruppe g, List<Module> mods,
                              int x, int y, int clipOben, int clipUnten, float a, float dt, boolean sucht) {
        boolean imBild = y + GRUPPE_H > clipOben && y < clipUnten;
        boolean offenJetzt = sucht || GuiState.gruppeOffen(g.name());
        float oa = weich(gruppeAnim.getOrDefault(g, offenJetzt ? 1f : 0f), offenJetzt ? 1f : 0f, 16f, dt);
        gruppeAnim.put(g, oa);
        boolean hov = imBild && mx >= x && mx < x + spalteB && my >= Math.max(y, clipOben) && my < Math.min(y + GRUPPE_H, clipUnten);
        float hv = weich(gruppeHover.getOrDefault(g, 0f), hov ? 1f : 0f, 18f, dt);
        gruppeHover.put(g, hv);
        if (imBild) {
            int f = g.farbe;
            ctx.fill(x + 2, y + 1, x + spalteB - 2, y + GRUPPE_H - 1,
                    VortexStyle.fade((f & 0x00FFFFFF) | 0xFF000000, a * (0.10f + 0.10f * hv)));
            ctx.fill(x + 2, y + 1, x + 4, y + GRUPPE_H - 1, VortexStyle.fade(f, a));
            ctx.text(this.font, Component.literal(g.titel.toUpperCase(Locale.ROOT)), x + 8, y + 4,
                    VortexStyle.fade(VortexStyle.mix(f, 0xFFFFFFFF, 0.25f + 0.3f * hv), a), false);
            int an = 0;
            for (Module m : mods) if (m.isEnabled()) an++;
            String zahl = (an > 0 ? an + "/" : "") + mods.size();
            int zx = x + spalteB - 17 - this.font.width(zahl);
            if (zx > x + 8 + this.font.width(g.titel.toUpperCase(Locale.ROOT)) + 4) {
                ctx.text(this.font, Component.literal(zahl), zx, y + 4,
                        VortexStyle.fade(an > 0 ? VortexStyle.mix(f, 0xFFFFFFFF, 0.4f) : VortexStyle.TEXT_DIM, a), false);
            }
            pfeil(ctx, x + spalteB - 12, y + 5, oa, VortexStyle.fade(VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, hv), a));
            if (!sucht) {
                Treffer t = new Treffer(x, Math.max(y, clipOben), spalteB,
                        Math.min(y + GRUPPE_H, clipUnten) - Math.max(y, clipOben), Art.GRUPPE, null, null);
                t.gruppe = g;
                treffer.add(t);
            }
        }
        return y + GRUPPE_H;
    }

    private int gruppenHoehe(List<Module> liste, boolean sucht) {
        int h = 0;
        for (var e : com.vortex.client.module.Gruppe.aufteilen(liste).entrySet()) {
            h += GRUPPE_H;
            if (sucht || GuiState.gruppeOffen(e.getKey().name())) h += inhaltHoehe(e.getValue());
        }
        return h + 2;
    }

    private int zeichneModul(GuiGraphicsExtractor ctx, Module m, int x, int y,
                             int clipOben, int clipUnten, float a, float dt) {
        boolean imBild = y + ZEILE_H > clipOben && y < clipUnten;
        boolean hov = imBild && mx >= x && mx < x + spalteB && my >= Math.max(y, clipOben)
                && my < Math.min(y + ZEILE_H, clipUnten);
        float hv = weich(hover.getOrDefault(m, 0f), hov ? 1f : 0f, 18f, dt);
        hover.put(m, hv);

        if (imBild) {
            float an = weich(anAnim.getOrDefault(m, m.isEnabled() ? 1f : 0f),
                    m.isEnabled() ? 1f : 0f, 12f, dt);
            anAnim.put(m, an);

            float offenAnim = klapp.getOrDefault(m, 0f);
            if (offenAnim > 0.01f) {
                ctx.fill(x + 3, y, x + spalteB - 3, y + ZEILE_H,
                        VortexStyle.fade(VortexStyle.BAR, a * offenAnim));
                ctx.fill(x + 3, y, x + 4, y + ZEILE_H,
                        VortexStyle.fade(VortexStyle.akzent(0.4f), a * 0.8f * offenAnim));
            }

            if (hv > 0.01f) {
                ctx.fill(x + 2, y, x + spalteB - 2, y + ZEILE_H,
                        VortexStyle.fade(VortexStyle.HOV, a * hv * (1f - an * 0.5f)));
            }
            if (an > 0.01f) {
                verlaufBand(ctx, x + 2, y, spalteB - 4, ZEILE_H,
                        VortexStyle.fade(VortexStyle.akzent(0f), a * an * (0.42f + 0.18f * hv)),
                        VortexStyle.fade(VortexStyle.akzent(1f), a * an * 0.06f));
                int halb = (int) ((ZEILE_H / 2 - 3) * an);
                int mitte = y + ZEILE_H / 2;
                ctx.fill(x + 2, mitte - halb, x + 4, mitte + halb,
                        VortexStyle.fade(VortexStyle.akzent(0.2f), a));
            }

            int farbe = VortexStyle.mix(VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, hv),
                    0xFFFFFFFF, an);
            int tx = x + 9 + (int) (hv * 2f);
            ctx.text(this.font, Component.literal(m.getName()), tx, y + 4,
                    VortexStyle.fade(farbe, a), false);
            if (hatEinstellungen(m)) {
                boolean auf = offen.getOrDefault(m, false);
                boolean plusHov = hov && mx >= x + spalteB - PLUS_B;
                if (plusHov) {
                    ctx.fill(x + spalteB - PLUS_B + 1, y + 2, x + spalteB - 3, y + ZEILE_H - 2,
                            VortexStyle.fade(VortexStyle.mix(VortexStyle.HOV, VortexStyle.akzent(0.5f), 0.35f), a));
                }
                ctx.text(this.font, Component.literal(auf ? "-" : "+"),
                        x + spalteB - 12, y + 4,
                        VortexStyle.fade(auf || plusHov ? VortexStyle.akzent(0.5f) : VortexStyle.TEXT_DIM, a), false);
            }
            treffer.add(new Treffer(x, Math.max(y, clipOben), spalteB,
                    Math.min(y + ZEILE_H, clipUnten) - Math.max(y, clipOben),
                    Art.MODUL, m, null));
            if (hov) tipKandidat = m;
        }
        y += ZEILE_H;

        float k = weich(klapp.getOrDefault(m, 0f), offen.getOrDefault(m, false) ? 1f : 0f, 14f, dt);
        klapp.put(m, k);
        if (k > 0.01f) {
            int voll = einstellungsHoehe(m);
            int h = (int) (voll * k);
            int oben = Math.max(y, clipOben), unten = Math.min(y + h, clipUnten);
            if (unten > oben) {
                ctx.enableScissor(x, oben, x + spalteB, unten);

                ctx.fill(x + 3, y, x + spalteB - 3, y + h,
                        VortexStyle.fade(VortexStyle.BAR, a));
                ctx.fill(x + 3, y, x + 4, y + h,
                        VortexStyle.fade(VortexStyle.akzent(0.4f), a * 0.8f));
                ctx.fill(x + 3, y + h - 1, x + spalteB - 3, y + h,
                        VortexStyle.fade(VortexStyle.LINE, a));

                int sy = y + INNEN_RAND;

                if (m instanceof com.vortex.client.module.HasOwnScreen hos) {
                    if (sy + AUSWAHL_H > oben && sy < unten) {
                        zeichneAuswahlKnopf(ctx, m, hos.screenButtonLabel(),
                                x + 7, sy, spalteB - 14, a);
                    }
                    sy += AUSWAHL_H + 2;
                }

                for (Setting s : m.getSettings()) {
                    if (s == m.getEnabledSetting()) continue;
                    int eh = einstHoehe(s, spalteB - 14);
                    if (sy + eh > oben && sy < unten) {
                        zeichneEinstellung(ctx, m, s, x + 8, sy, spalteB - 14, a);
                    }
                    sy += eh;
                }
                if (hatZuruecksetzbare(m) && sy + EINST_H > oben && sy < unten) {
                    zeichneReset(ctx, m, x + 8, sy, spalteB - 14, a);
                }
                ctx.disableScissor();
            }
            y += h;
        }
        return y;
    }

    private void zeichneEinstellung(GuiGraphicsExtractor ctx, Module m, Setting s,
                                    int x, int y, int w, float a) {
        int dim = VortexStyle.fade(VortexStyle.TEXT_DIM, a);
        int txt = VortexStyle.fade(VortexStyle.TEXT, a);
        int akz = VortexStyle.fade(VortexStyle.akzent(0.5f), a);
        int eh = einstHoehe(s, w);
        java.util.List<String> namen = nameZeilen(s, w);
        if (mx >= x && mx < x + w && my >= y && my < y + eh) {
            ctx.fill(x - 2, y, x + w + 2, y + eh,
                    VortexStyle.fade(VortexStyle.HOV, a * 0.7f));
        }


        if (s instanceof BooleanSetting b) {
            nameZeichnen(ctx, namen, x + 2, y + 3, dim);
            float an = weich(einstAnim.getOrDefault(s, b.get() ? 1f : 0f),
                    b.get() ? 1f : 0f, 16f, letzteDt);
            einstAnim.put(s, an);
            int bx = x + w - 16;
            ctx.fill(bx, y + 4, bx + 16, y + 10,
                    VortexStyle.fade(VortexStyle.mix(VortexStyle.TRACK, VortexStyle.akzent(0.5f), an), a));
            int kx = bx + 1 + (int) (an * 9f);
            ctx.fill(kx, y + 3, kx + 6, y + 11,
                    VortexStyle.fade(VortexStyle.mix(0xFFB8B2CC, 0xFFFFFFFF, an), a));
            treffer.add(new Treffer(x, y, w, eh, Art.BOOL, m, s));

        } else if (s instanceof NumberSetting n) {
            String wert = zahl(n);
            int wertB = this.font.width(wert);
            nameZeichnen(ctx, namen, x + 2, y + 1, dim);
            ctx.text(this.font, Component.literal(wert), x + w - wertB, y + 1, txt, false);
            int ty = y + eh - 3;
            ctx.fill(x + 2, ty, x + w, ty + 2, VortexStyle.fade(VortexStyle.TRACK, a));
            double ziel = (n.get() - n.getMin()) / Math.max(1e-9, n.getMax() - n.getMin());
            ziel = Math.max(0, Math.min(1, ziel));
            float p = weich(einstAnim.getOrDefault(s, (float) ziel), (float) ziel,
                    zieht == n ? 40f : 14f, letzteDt);
            einstAnim.put(s, p);
            int fx = x + 2 + (int) ((w - 2) * p);
            verlaufBand(ctx, x + 2, ty, fx - x - 2, 2,
                    VortexStyle.fade(VortexStyle.akzent(0f), a), VortexStyle.fade(VortexStyle.akzent(1f), a));
            ctx.fill(fx - 2, ty - 2, fx + 2, ty + 4, VortexStyle.fade(0xFFFFFFFF, a));
            treffer.add(new Treffer(x + 2, y, w - 2, eh, Art.NUM, m, s));

        } else if (s instanceof ModeSetting mode) {
            String wert = kuerzen(String.valueOf(mode.get()), Math.max(24, w / 2));
            int wertB = this.font.width(wert);
            nameZeichnen(ctx, namen, x + 2, y + 3, dim);
            ctx.text(this.font, Component.literal(wert), x + w - wertB, y + 3, akz, false);
            treffer.add(new Treffer(x, y, w, eh, Art.MODUS, m, s));

        } else if (s instanceof ColorSetting c) {
            nameZeichnen(ctx, namen, x + 2, y + 3, dim);
            int bx = x + w - 12;
            ctx.fill(bx, y + 2, bx + 10, y + 12, VortexStyle.fade(0xFF000000, a));
            Farbthema.rohAn();
            try {
                for (int i = 0; i < 8; i++) {
                    ctx.fill(bx + 1 + i, y + 3, bx + 2 + i, y + 11,
                            VortexStyle.fade(c.at((i + 0.5f) / 8f) | 0xFF000000, a));
                }
            } finally { Farbthema.rohAus(); }
            treffer.add(new Treffer(x, y, w, eh, Art.FARBE, m, s));

        } else if (s instanceof KeySetting k) {
            boolean lauscht = k.isListening();
            String wert = lauscht ? "..." : (k.isBound() ? kuerzen(k.getKeyName(), Math.max(20, w / 2 - 8)) : "None");
            int kappeB = this.font.width(wert) + 8;
            int kx = x + w - kappeB;
            ctx.fill(kx, y + 1, kx + kappeB, y + EINST_H - 1,
                    VortexStyle.fade(lauscht ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.45f)
                                             : VortexStyle.CARD, a));
            ctx.fill(kx, y + EINST_H - 2, kx + kappeB, y + EINST_H - 1,
                    VortexStyle.fade(lauscht ? VortexStyle.akzent(0.5f) : VortexStyle.LINE, a));
            ctx.text(this.font, Component.literal(wert), kx + 4, y + 3,
                    lauscht ? txt : (k.isBound() ? txt : dim), false);
            nameZeichnen(ctx, namen, x + 2, y + 3, dim);
            treffer.add(new Treffer(x, y, w, eh, Art.TASTE, m, s));
        } else if (s instanceof com.vortex.client.core.setting.StringSetting st) {
            String wert = textWert(st, w);
            int wertB = this.font.width(wert);
            ctx.text(this.font, Component.literal(wert), x + w - wertB, y + 3,
                    st.get() == null || st.get().isEmpty() ? dim : akz, false);
            nameZeichnen(ctx, namen, x + 2, y + 3, dim);
            treffer.add(new Treffer(x, y, w, eh, Art.TEXT, m, s));
        }
    }


    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (leisteGeklickt(click.x(), click.y())) return true;
        if (super.mouseClicked(click, doubled)) return true;
        int knopf = click.button();
        mx = (int) (click.x() / masstab);
        my = (int) (click.y() / masstab);
        for (int i = treffer.size() - 1; i >= 0; i--) {
            Treffer t = treffer.get(i);
            if (!t.in(mx, my)) continue;
            switch (t.art) {
                case GRUPPE: {
                    GuiState.gruppeUmschalten(t.gruppe.name());
                    return true;
                }
                case HUDSTIL: {
                    if (t.nr >= 0) com.vortex.client.hud.HudLook.anwenden(com.vortex.client.hud.HudLook.LOOKS.get(t.nr));
                    else Minecraft.getInstance().gui.setScreen(new HudStyleScreen(this));
                    return true;
                }
                case KOPF: {
                    if (knopf == 1) {
                        katZu.put(t.kat, !katZu.getOrDefault(t.kat, false));
                        return true;
                    }
                    int[] r = spaltenRect.get(t.kat);
                    ziehtKat = t.kat;
                    kopfBewegt = false;
                    kopfStartX = mx;
                    kopfStartY = my;
                    kopfOffX = r == null ? 0 : mx - r[0];
                    kopfOffY = r == null ? 0 : my - r[1];
                    return true;
                }
                case GRIFF: {
                    int[] r = spaltenRect.get(t.kat);
                    ziehtBreite = t.kat;
                    breiteStart = r == null ? SPALTE_B : r[2];
                    breiteStartX = mx;
                    return true;
                }
                case MODUL:
                    boolean aufsPlus = mx >= t.x + t.w - PLUS_B;
                    if (knopf == 1 || (aufsPlus && hatEinstellungen(t.modul))) {
                        if (hatEinstellungen(t.modul)) {
                            offen.put(t.modul, !offen.getOrDefault(t.modul, false));
                        }
                    } else {
                        t.modul.toggle();
                        com.vortex.client.core.ClientSettings.umgeschaltet(t.modul, false);
                    }
                    return true;
                case BOOL:
                    schalte(t.modul, (BooleanSetting) t.einst);
                    return true;
                case NUM:
                    zieht = (NumberSetting) t.einst;
                    ziehX = t.x;
                    ziehB = t.w;
                    schieber(zieht, mx);
                    return true;
                case MODUS:
                    if (knopf == 1) zurueck((ModeSetting) t.einst);
                    else ((ModeSetting) t.einst).cycle();
                    return true;
                case FARBE:
                    farbe(t.modul, (ColorSetting) t.einst);
                    return true;
                case TEXT:
                    Minecraft.getInstance().gui.setScreen(new TextEingabeScreen(this,
                            (com.vortex.client.core.setting.StringSetting) t.einst));
                    return true;
                case AUSWAHL:
                    if (t.modul instanceof com.vortex.client.module.HasOwnScreen hos) {
                        Minecraft.getInstance().gui.setScreen(hos.createScreen(this));
                    }
                    return true;
                case TASTE:
                    ((KeySetting) t.einst).setListening(true);
                    return true;
                case RESET:
                    long jetztMs = System.currentTimeMillis();
                    if (resetFrage == t.modul && jetztMs - resetFrageZeit < 3000) {
                        zuruecksetzen(t.modul);
                        resetFrage = null;
                    } else {
                        resetFrage = t.modul;
                        resetFrageZeit = jetztMs;
                    }
                    return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        mx = (int) (click.x() / masstab);
        my = (int) (click.y() / masstab);
        if (ziehtBreite != null) {
            GuiState.setSpalteBreite(ziehtBreite.name(), breiteStart + (mx - breiteStartX));
            return true;
        }
        if (ziehtKat != null) {
            if (!kopfBewegt && Math.abs(mx - kopfStartX) + Math.abs(my - kopfStartY) > 3) {
                kopfBewegt = true;
                if (!GuiState.hatFreieAnordnung()) {
                    for (Map.Entry<Module.Category, int[]> e : spaltenRect.entrySet()) {
                        GuiState.setSpaltePos(e.getKey().name(),
                                e.getValue()[0] / (float) W, e.getValue()[1] / (float) H);
                    }
                }
            }
            if (kopfBewegt) {
                GuiState.setSpaltePos(ziehtKat.name(), (mx - kopfOffX) / (float) W, (my - kopfOffY) / (float) H);
            }
            return true;
        }
        if (zieht != null) {
            schieber(zieht, mx);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        zieht = null;
        if (ziehtKat != null && !kopfBewegt) {
            katZu.put(ziehtKat, !katZu.getOrDefault(ziehtKat, false));
        }
        ziehtKat = null;
        kopfBewegt = false;
        ziehtBreite = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        mouseX /= masstab;
        mouseY /= masstab;
        for (int i = 0; i < spaltenFlaeche.size(); i++) {
            int[] f = spaltenFlaeche.get(i);
            if (mouseX >= f[0] && mouseX < f[0] + f[2] && mouseY >= f[1] && mouseY < f[1] + f[3]) {
                float[] l = lauf.computeIfAbsent(spaltenKat.get(i), k -> new float[2]);
                l[1] = Math.max(0f, l[1] - (float) vertical * 24f);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return lauschend() == null;
    }

    @Override
    public void removed() {
        com.vortex.client.core.ConfigManager.save();
        super.removed();
    }


    private void schalte(Module m, BooleanSetting b) {
        if (m instanceof com.vortex.client.module.modules.GlobalHudColorModule ghc) {
            if (b == ghc.apply) { ghc.applyToAll(); return; }
            if (b == ghc.reset) { ghc.resetToWhite(); return; }
        }
        if (b == m.getEnabledSetting()) m.toggle();
        else b.toggle();
    }

    private void schieber(NumberSetting n, int mausX) {
        if (ziehB <= 0) return;
        float p = (mausX - ziehX) / (float) ziehB;
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;
        double roh = n.getMin() + p * (n.getMax() - n.getMin());
        double schritt = n.getStep();
        if (schritt > 0) roh = Math.round(roh / schritt) * schritt;
        roh = Math.round(roh * 1000.0) / 1000.0;
        if (roh < n.getMin()) roh = n.getMin();
        if (roh > n.getMax()) roh = n.getMax();
        n.set(roh);
    }

    private void zurueck(ModeSetting mode) {
        int start = mode.getIndex();
        int anzahl = 0;
        do { mode.cycle(); anzahl++; } while (mode.getIndex() != start && anzahl < 64);
        if (anzahl <= 1) return;
        for (int i = 0; i < anzahl - 1; i++) mode.cycle();
    }

    private void farbe(Module m, ColorSetting c) {
        if (m instanceof com.vortex.client.module.modules.GlobalHudColorModule ghc && c == ghc.color) {
            Minecraft.getInstance().gui.setScreen(new ColorPickerScreen(this, c, ghc::applyToAll));
            return;
        }
        Minecraft.getInstance().gui.setScreen(new ColorPickerScreen(this, c));
    }

    private KeySetting lauschend() {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            for (Setting s : m.getSettings()) {
                if (s instanceof KeySetting k && k.isListening()) return k;
            }
        }
        return null;
    }

    /** Nimmt die naechste gedrueckte Taste auf -- wortgleich zum bisherigen Menue. */
    private void tasteAufnehmen() {
        KeySetting k = lauschend();
        if (k == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            k.setKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
            k.setListening(false);
            com.vortex.client.core.ConfigManager.save();
            return;
        }
        for (int code = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
             code <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST; code++) {
            if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), code)) {
                k.setKeyCode(code);
                k.setListening(false);
                com.vortex.client.core.ConfigManager.save();
                return;
            }
        }
    }


    private List<Module> module(Module.Category c) {
        List<Module> l = ModuleManager.INSTANCE.getByCategory(c);
        if (com.vortex.client.core.CleanModules.aktiv()) l.removeIf(com.vortex.client.core.CleanModules::versteckt);
        return l;
    }

    private List<Module> gefiltert(Module.Category c) {
        String q = (search == null) ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return module(c);
        List<Module> out = new ArrayList<>();
        for (Module m : module(c)) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        return out;
    }

    private boolean hatEinstellungen(Module m) {
        if (m instanceof com.vortex.client.module.HasOwnScreen) return true;
        for (Setting s : m.getSettings()) if (s != m.getEnabledSetting()) return true;
        return false;
    }


    /** Wert einer Text-Einstellung, gekuerzt auf die halbe Breite. */
    private String textWert(com.vortex.client.core.setting.StringSetting st, int w) {
        String v = st.get() == null || st.get().isEmpty() ? "(empty)" : st.get();
        return kuerzen(v, Math.max(24, w / 2));
    }

    /** Platz, den der Wert rechts braucht. */
    private int wertBreite(Setting s, int w) {
        if (s instanceof BooleanSetting) return 16;
        if (s instanceof NumberSetting n) return this.font.width(zahl(n));
        if (s instanceof ModeSetting mode) return this.font.width(kuerzen(String.valueOf(mode.get()), Math.max(24, w / 2)));
        if (s instanceof ColorSetting) return 12;
        if (s instanceof com.vortex.client.core.setting.StringSetting st) return this.font.width(textWert(st, w));
        if (s instanceof KeySetting k) {
            String wert = k.isListening() ? "..." : (k.isBound() ? kuerzen(k.getKeyName(), Math.max(20, w / 2 - 8)) : "None");
            return this.font.width(wert) + 8;
        }
        return 0;
    }

    /** Der Name in hoechstens zwei Zeilen, passend links neben den Wert. */
    private java.util.List<String> nameZeilen(Setting s, int w) {
        String name = s.getName();
        int platz = Math.max(20, w - wertBreite(s, w) - 8);
        if (this.font.width(name) <= platz) return java.util.List.of(name);
        StringBuilder eins = new StringBuilder();
        String[] woerter = name.split(" ");
        int i = 0;
        for (; i < woerter.length; i++) {
            String probe = eins.length() == 0 ? woerter[i] : eins + " " + woerter[i];
            if (this.font.width(probe) > platz && eins.length() > 0) break;
            eins = new StringBuilder(probe);
        }
        String zwei = String.join(" ", java.util.Arrays.copyOfRange(woerter, i, woerter.length));
        if (zwei.isEmpty()) return java.util.List.of(kuerzen(eins.toString(), platz));
        return java.util.List.of(kuerzen(eins.toString(), platz), kuerzen(zwei, w - 4));
    }

    private int einstHoehe(Setting s, int w) {
        return EINST_H + (nameZeilen(s, w).size() - 1) * 9;
    }

    private void nameZeichnen(GuiGraphicsExtractor ctx, java.util.List<String> zeilen, int x, int y, int farbe) {
        for (int i = 0; i < zeilen.size(); i++) {
            ctx.text(this.font, Component.literal(zeilen.get(i)), x, y + i * 9, farbe, false);
        }
    }

    private int einstellungsHoehe(Module m) {
        int h = 2 * INNEN_RAND;
        for (Setting s : m.getSettings()) if (s != m.getEnabledSetting()) h += einstHoehe(s, spalteB - 14);
        if (hatZuruecksetzbare(m)) h += EINST_H;
        if (m instanceof com.vortex.client.module.HasOwnScreen) h += AUSWAHL_H + 2;
        return h;
    }


    /** Modul, bei dem "Reset" einmal angeklickt wurde und auf Bestaetigung wartet. */
    private Module resetFrage = null;
    private long resetFrageZeit = 0;

    /** Gibt es ausser An/Aus und Taste etwas zum Zuruecksetzen? */
    private boolean hatZuruecksetzbare(Module m) {
        for (Setting s : m.getSettings()) {
            if (s != m.getEnabledSetting() && s != m.getToggleKey()) return true;
        }
        return false;
    }

    /**
     * Alle Einstellungen des Moduls auf Werkseinstellung. An/Aus und die
     * Tastenbelegung bleiben -- die hat man bewusst gesetzt, und wer z. B.
     * das Scoreboard verschoben hat, will die Position zurueck, nicht seine
     * Taste verlieren.
     */
    private void zuruecksetzen(Module m) {
        for (Setting s : m.getSettings()) {
            if (s == m.getEnabledSetting() || s == m.getToggleKey()) continue;
            s.resetToDefault();
            einstAnim.remove(s);
        }
        com.vortex.client.core.ConfigManager.save();
    }

    /** Kleine Zeile "Reset" rechtsbuendig unter den Einstellungen. */
    private void zeichneReset(GuiGraphicsExtractor ctx, Module m, int x, int y, int w, float a) {
        boolean frage = resetFrage == m && System.currentTimeMillis() - resetFrageZeit < 3000;
        String text = frage ? "Click again to reset" : "Reset to default";
        int tw = this.font.width(text);
        int bx = x + w - tw - 6;
        boolean hov = mx >= bx && mx < x + w && my >= y && my < y + EINST_H;
        if (hov || frage) {
            ctx.fill(bx, y + 1, x + w, y + EINST_H - 1, VortexStyle.fade(
                    frage ? VortexStyle.mix(VortexStyle.CARD, 0xFFEF4444, 0.35f) : VortexStyle.HOV, a));
        }
        ctx.text(this.font, Component.literal(text), bx + 3, y + 3,
                VortexStyle.fade(frage ? 0xFFFF8A8A : (hov ? VortexStyle.TEXT : VortexStyle.TEXT_DIM), a), false);
        treffer.add(new Treffer(bx, y, x + w - bx, EINST_H, Art.RESET, m, null));
    }

    private int inhaltHoehe(List<Module> liste) {
        int h = 0;
        for (Module m : liste) {
            h += ZEILE_H;
            float k = klapp.getOrDefault(m, 0f);
            if (k > 0.01f) h += (int) (einstellungsHoehe(m) * k);
        }
        return h;
    }

    private String zahl(NumberSetting n) {
        double v = n.get();
        if (n.getStep() >= 1 && v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private String kuerzen(String s, int max) {
        if (this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private static String schoen(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        if (s.equals("pvp")) return "PvP";
        if (s.equals("hud")) return "HUD";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Weiche, bildratenunabhaengige Annaeherung -- wie ueberall im Client. */
    private static float weich(float ist, float ziel, float tempo, float dt) {
        float f = 1f - (float) Math.exp(-tempo * dt);
        float neu = ist + (ziel - ist) * f;
        return Math.abs(ziel - neu) < 0.005f ? ziel : neu;
    }

    /**
     * Klapp-Pfeil, 5 Pixel gross. Offen zeigt er nach unten, zu nach rechts.
     * Beide Formen werden je nach Klappzustand ueberblendet -- eine echte
     * Drehung ginge nur ueber die Matrix, das Ueberblenden wirkt genauso weich.
     */
    private static void pfeil(GuiGraphicsExtractor ctx, int x, int y, float offen, int farbe) {
        int alpha = (farbe >>> 24) & 0xFF;
        int rgb = farbe & 0x00FFFFFF;
        int aUnten = (int) (alpha * offen);
        int aRechts = (int) (alpha * (1f - offen));
        if (aUnten > 3) {
            int c = (aUnten << 24) | rgb;
            ctx.fill(x, y, x + 5, y + 1, c);
            ctx.fill(x + 1, y + 1, x + 4, y + 2, c);
            ctx.fill(x + 2, y + 2, x + 3, y + 3, c);
        }
        if (aRechts > 3) {
            int c = (aRechts << 24) | rgb;
            ctx.fill(x + 1, y - 1, x + 2, y + 4, c);
            ctx.fill(x + 2, y, x + 3, y + 3, c);
            ctx.fill(x + 3, y + 1, x + 4, y + 2, c);
        }
    }

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(2, Math.min(w / 2, h / 2));
        ctx.fill(x, y + r, x + w, y + h - r, c);
        ctx.fill(x + r, y, x + w - r, y + r, c);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, c);
        if (r >= 2) {
            ctx.fill(x + 1, y + 1, x + r, y + r, c);
            ctx.fill(x + w - r, y + 1, x + w - 1, y + r, c);
            ctx.fill(x + 1, y + h - r, x + r, y + h - 1, c);
            ctx.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, c);
        }
    }

    /**
     * Waagerechter Verlauf als EIN Zeichenbefehl: der senkrechte Verlauf von
     * Minecraft, um 90 Grad gedreht. Vorher waren es Baender von 6 Pixeln --
     * bei jeder aktiven Zeile Dutzende Rechtecke pro Bild.
     */
    private static void verlaufBand(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                    int von, int bis) {
        if (w <= 0 || h <= 0) return;
        var p = ctx.pose();
        p.pushMatrix();
        try {
            p.translate(x, y + h);
            p.rotate((float) (-Math.PI / 2));
            ctx.fillGradient(0, 0, h, w, von, bis);
        } finally {
            p.popMatrix();
        }
    }


    /**
     * Knopf, der die Auswahlliste eines Moduls oeffnet ("Select blocks" ...).
     *
     * Hebt sich deutlich von den Einstellungen ab: eigene Flaeche, Pfeil
     * rechts, heller beim Ueberfahren. Es ist der wichtigste Eintrag dieser
     * Module, deshalb steht er ganz oben im Feld.
     */
    private void zeichneAuswahlKnopf(GuiGraphicsExtractor ctx, Module m, String text,
                                     int x, int y, int w, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + AUSWAHL_H;
        roundRect(ctx, x, y, w, AUSWAHL_H, VortexStyle.fade(
                hov ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.35f)
                    : VortexStyle.CARD, a));
        ctx.text(this.font, Component.literal(kuerzen(text, w - 20)), x + 6, y + 4,
                VortexStyle.fade(hov ? 0xFFFFFFFF : VortexStyle.TEXT, a), false);
        ctx.text(this.font, Component.literal(">"), x + w - 9, y + 4,
                VortexStyle.fade(VortexStyle.akzent(0.5f), a), false);
        treffer.add(new Treffer(x, y, w, AUSWAHL_H, Art.AUSWAHL, m, null));
    }

}
