package com.vortex.client.gui;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Glatt.Schrift;
import com.vortex.client.gui.glatt.Symbole.Symbol;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Startbildschirm des Clients (Rechtsshift).
 *
 * AB 4.48: LOGO + ZWEI LEISTEN
 *   Oben in der Mitte das grosse Vortex-Logo mit Schein, darunter "VORTEX"
 *   breit gesperrt und "CLIENT" klein in Akzentfarbe -- wie ein Titelbild.
 *   Darunter zwei zusammenhaengende Leisten statt vieler Einzelknoepfe:
 *     gross:  HUD Editor, Wardrobe | MODS | Friends, Music, Settings
 *             (jeweils mit Symbol; ab 4.51 sitzt Mods als groesserer,
 *             leicht herausstehender Knopf genau in der Mitte, beide Seiten
 *             gleich breit)
 *     klein:  Presets, Keybinds, Waypoints, Macros, Bots, Community, Pictures, Styles, Host, (Beta)
 *   In jeder Leiste gleitet eine Markierung weich zum Eintrag unter der Maus
 *   (Flaeche + Akzentlinie unten), die Eintraege sind nur durch feine
 *   Trennstriche getrennt.
 *   Oben links das eigene Profil, oben rechts Restart und Close als kleine
 *   Leiste (Restart fragt nach: zweiter Klick startet neu).
 *   Schrift wie im Modul-Menue (Minecraft-Schrift), Formen glatt gerundet.
 *   Pfeiltasten waehlen, Enter oeffnet.
 */
public class HomeScreen extends Screen {

    private static final int GROSS_H = 30;
    private static final int KLEIN_H = 18;
    private static final int SPALTE = 0xF5120E1B;
    private static final int ROT_TEXT = 0xFFFF8A8A;
    private static final int GRUEN = 0xFF4ADE80;

    private int mx, my;
    private long letzteZeit = 0;
    private float oeffnen = 0f, seit = 0f, dtJetzt = 0.016f;
    private float zoom = 1f;

    private boolean neustartFrage = false;
    private String fehler = null;
    /** Zeile, die per Tastatur gewaehlt ist (-1 = keine). */
    private int tastenZeile = -1;
    private int letzteMausX = Integer.MIN_VALUE, letzteMausY = Integer.MIN_VALUE;

    private record Knopf(String key, String text, String rechts, Symbol sym, Runnable run) {}
    private record Hit(int x, int y, int w, int h, Runnable run) {}
    private final List<Hit> hits = new ArrayList<>();
    private final List<Runnable> reihenfolge = new ArrayList<>();
    private final java.util.Map<String, Float> werte = new java.util.HashMap<>();

    /** Wohin "Zurueck" fuehrt (seit 4.53: aus dem ESC-Menue geoeffnet -> zurueck ins ESC-Menue). */
    private final net.minecraft.client.gui.screens.Screen zurueck;

    public HomeScreen() {
        this(null);
    }

    public HomeScreen(net.minecraft.client.gui.screens.Screen zurueck) {
        super(Component.literal("Vortex Client"));
        this.zurueck = zurueck;
    }

    /** Aus dem ESC-Menue geoeffnet? */
    public boolean ausPause() { return zurueck instanceof net.minecraft.client.gui.screens.PauseScreen; }

    @Override
    public boolean isPauseScreen() {
        return ausPause();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(zurueck);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        dtJetzt = dt;
        oeffnen = weich(oeffnen, 1f, 10f, dt);
        seit += dt;
        float a = oeffnen;
        hits.clear();
        reihenfolge.clear();
        if (mouseX != letzteMausX || mouseY != letzteMausY) {
            if (letzteMausX != Integer.MIN_VALUE) tastenZeile = -1;
            letzteMausX = mouseX;
            letzteMausY = mouseY;
        }

        int w = this.width, h = this.height;
        ctx.fill(0, 0, w, h, VortexStyle.fade(0xD8070510, a));
        Glatt.licht(ctx, w * 0.30f, h * 0.30f, Math.max(w, h) * 0.45f, Glatt.alpha(VortexStyle.VIOLETT, a * 0.16f));
        Glatt.licht(ctx, w * 0.75f, h * 0.65f, Math.max(w, h) * 0.45f, Glatt.alpha(VortexStyle.BLAU, a * 0.10f));

        List<List<Knopf>> reihen = reihen();
        int heroH = 112;
        int gb = leistenBreite(reihen.get(0), true), kb = leistenBreite(reihen.get(1), false);
        int breite = Math.max(gb, kb);
        int hoehe = heroH + 18 + GROSS_H + 8 + MITTE_UEBER + KLEIN_H;
        zoom = Math.min(1f, Math.min((w - 16) / (float) breite, (h - 16) / (float) hoehe));
        zoom = Math.max(0.3f, zoom);
        int W = Math.round(w / zoom), H = Math.round(h / zoom);
        mx = Math.round(mouseX / zoom);
        my = Math.round(mouseY / zoom);

        var p = ctx.pose();
        p.pushMatrix();
        try {
            if (zoom < 0.999f) p.scale(zoom, zoom);
            int top = Math.max(8, (H - hoehe) / 2 - 6);
            titelbild(ctx, W / 2f, top, a);

            int ly = top + heroH + 18;
            int nr = leiste(ctx, "gross", reihen.get(0), W / 2, ly, true, a, 0.35f, 0);
            nr = leiste(ctx, "klein", reihen.get(1), W / 2, ly + GROSS_H + 8 + MITTE_UEBER, false, a, 0.45f, nr);
            nr = ecke(ctx, W - 8, 8, a, nr);
            profil(ctx, 8, 8, a);
            if (fehler != null) {
                ctx.text(this.font, Component.literal(fehler), (W - this.font.width(fehler)) / 2, H - 14, VortexStyle.fade(ROT_TEXT, a), false);
            }
        } finally {
            p.popMatrix();
        }
    }

    /** Logo mit Schein, darunter VORTEX und CLIENT. */
    private void titelbild(GuiGraphicsExtractor ctx, float cx, float y, float a) {
        float t = einblenden(0f, 0.55f);
        float al = a * t;
        float wachsen = 0.90f + 0.10f * t;
        float lg = 60 * wachsen;
        float schweben = (float) Math.sin(seit * 1.1f) * 1.5f;
        float ly = y + (60 - lg) / 2f + schweben;
        float puls = 0.5f + 0.5f * (float) Math.sin(seit * 1.6f);
        Glatt.licht(ctx, cx, ly + lg * 0.5f, lg * 1.1f, Glatt.alpha(VortexStyle.VIOLETT, al * (0.32f + 0.08f * puls)));
        Glatt.logo(ctx, cx - lg / 2f, ly, lg, al, t);

        float tt = einblenden(0.18f, 0.45f);
        float tal = a * tt;
        float ty = y + 66 + (1f - tt) * 6f;
        String titel = "VORTEX";
        float sperr = 7f;
        float tb = 0;
        for (int i = 0; i < titel.length(); i++) {
            tb += Glatt.breite(String.valueOf(titel.charAt(i)), Schrift.GROSS) + (i < titel.length() - 1 ? sperr : 0);
        }
        Glatt.licht(ctx, cx, ty + 11, tb * 0.7f, Glatt.alpha(0xFFFFFFFF, tal * 0.06f));
        float x = cx - tb / 2f;
        for (int i = 0; i < titel.length(); i++) {
            String c = String.valueOf(titel.charAt(i));
            Glatt.text(ctx, c, x, ty, Glatt.alpha(0xFFFFFFFF, tal), Schrift.GROSS);
            x += Glatt.breite(c, Schrift.GROSS) + sperr;
        }

        String unter = "CLIENT";
        float us = 5f, ub = 0;
        for (int i = 0; i < unter.length(); i++) {
            ub += Glatt.breite(String.valueOf(unter.charAt(i)), Schrift.FETT) + (i < unter.length() - 1 ? us : 0);
        }
        float ux = cx - ub / 2f, uy = ty + 30;
        int uf = Glatt.mix(VortexStyle.VIOLETT, VortexStyle.BLAU, 0.35f);
        for (int i = 0; i < unter.length(); i++) {
            String c = String.valueOf(unter.charAt(i));
            Glatt.text(ctx, c, ux, uy, Glatt.alpha(Glatt.mix(uf, 0xFFFFFFFF, 0.25f), tal), Schrift.FETT);
            ux += Glatt.breite(c, Schrift.FETT) + us;
        }
    }

    /** Der grosse Mitte-Knopf (Mods): Schrift, Symbol und Polster groesser. */
    private static final float MITTE_SKALA = 1.25f;
    private static final int MITTE_UEBER = 5;

    private static boolean mitte(Knopf k, boolean gross) {
        return gross && k.key().equals("mods");
    }

    private int segmentBreite(Knopf k, boolean gross) {
        if (mitte(k, gross)) {
            int w = 18 + 16 + 7 + Math.round(this.font.width(k.text()) * MITTE_SKALA) + 18;
            if (!k.rechts().isEmpty()) w += this.font.width(k.rechts()) + 6;
            return w;
        }
        int pad = gross ? 12 : 9;
        int w = pad + this.font.width(k.text()) + pad;
        if (gross && k.sym() != null) w += 12 + 6;
        if (!k.rechts().isEmpty()) w += this.font.width(k.rechts()) + 5;
        return w;
    }

    /**
     * Breiten aller Eintraege einer Leiste. In der grossen Leiste werden die
     * Eintraege der schmaleren Seite etwas breiter, damit Mods genau in der
     * Mitte sitzt.
     */
    private int[] breiten(List<Knopf> l, boolean gross) {
        int[] b = new int[l.size()];
        int m = -1;
        for (int i = 0; i < l.size(); i++) {
            b[i] = segmentBreite(l.get(i), gross);
            if (mitte(l.get(i), gross)) m = i;
        }
        if (m > 0 && m < l.size() - 1) {
            int links = 0, rechts = 0;
            for (int i = 0; i < m; i++) links += b[i];
            for (int i = m + 1; i < b.length; i++) rechts += b[i];
            int von = links < rechts ? 0 : m + 1, bis = links < rechts ? m : b.length;
            int fehlt = Math.abs(links - rechts), n = bis - von;
            for (int i = von; i < bis; i++) b[i] += fehlt / n + (i - von < fehlt % n ? 1 : 0);
        }
        return b;
    }

    private int leistenBreite(List<Knopf> l, boolean gross) {
        int w = 0;
        for (int x : breiten(l, gross)) w += x;
        return w;
    }

    /** Eine Leiste: gemeinsame Flaeche, feine Trennstriche, gleitende Markierung. */
    private int leiste(GuiGraphicsExtractor ctx, String id, List<Knopf> l, int cx, int y, boolean gross,
                       float a, float start, int nr) {
        int h = gross ? GROSS_H : KLEIN_H;
        float r = gross ? 6 : 5;
        int[] b = breiten(l, gross);
        int gesamt = 0;
        for (int v : b) gesamt += v;
        int x0 = cx - gesamt / 2;
        float t = einblenden(start, 0.4f);
        float al = a * t;
        float fy = y + (1f - t) * 8f;

        int ziel = -1, zx = 0, zw = 0;
        int x = x0;
        int mitteNr = -1, mitteX = 0;
        boolean mitteDrauf = false;
        for (int i = 0; i < l.size(); i++) {
            int sw = b[i];
            if (mitte(l.get(i), gross)) {
                mitteNr = i;
                mitteX = x;
                mitteDrauf = over(x, y - MITTE_UEBER, sw, h + 2 * MITTE_UEBER) || tastenZeile == nr + i;
            } else if (over(x, y, sw, h) || tastenZeile == nr + i) { ziel = i; zx = x; zw = sw; }
            x += sw;
        }
        float ia = wert("ia:" + id, ziel >= 0, 12f);
        float ix = werte.getOrDefault("ix:" + id, (float) zx), iw = werte.getOrDefault("iw:" + id, (float) zw);
        if (ziel >= 0) {
            if (ia < 0.08f) { ix = zx; iw = zw; }
            ix = weich(ix, zx, 16f, dtJetzt);
            iw = weich(iw, zw, 16f, dtJetzt);
        }
        werte.put("ix:" + id, ix);
        werte.put("iw:" + id, iw);

        Glatt.schatten(ctx, x0, fy + 2, gesamt, h, r, 8, Glatt.alpha(0xFF000000, al * 0.35f));
        Glatt.rund(ctx, x0, fy, gesamt, h, r, Glatt.alpha(SPALTE, al));
        Glatt.rahmen(ctx, x0, fy, gesamt, h, r, 1, Glatt.alpha(0x16FFFFFF, al));
        x = x0;
        for (int i = 0; i < l.size() - 1; i++) {
            x += b[i];
            if (i == mitteNr || i + 1 == mitteNr) continue;
            Glatt.flaeche(ctx, x, fy + (gross ? 8 : 5), x + 1f / Glatt.skala(), fy + h - (gross ? 8 : 5), Glatt.alpha(0x1CFFFFFF, al));
        }
        if (ia > 0.01f) {
            Glatt.rund(ctx, ix + 2, fy + 2, iw - 4, h - 4, r - 1.5f,
                    Glatt.alpha(Glatt.mix(SPALTE, VortexStyle.VIOLETT, 0.30f), al * ia));
            Glatt.rund(ctx, ix + 8, fy + h - 3, iw - 16, 1.5f, 0.75f, Glatt.alpha(VortexStyle.akzent(0.5f), al * ia));
        }
        if (mitteNr >= 0) {
            Knopf k = l.get(mitteNr);
            int sw = b[mitteNr];
            float sel = wert("s:" + k.key(), mitteDrauf, 14f);
            float puls = 0.5f + 0.5f * (float) Math.sin(seit * 1.6f);
            float mx0 = mitteX + 3, my0 = fy - MITTE_UEBER, mw = sw - 6, mh = h + 2 * MITTE_UEBER;
            Glatt.licht(ctx, mx0 + mw / 2f, my0 + mh / 2f, mw * 0.55f, Glatt.alpha(VortexStyle.VIOLETT, al * (0.10f + 0.05f * puls + 0.10f * sel)));
            Glatt.schatten(ctx, mx0, my0 + 2, mw, mh, r + 2, 8, Glatt.alpha(0xFF000000, al * 0.45f));
            Glatt.rund(ctx, mx0, my0, mw, mh, r + 2, Glatt.alpha(Glatt.mix(Glatt.mix(SPALTE, VortexStyle.VIOLETT, 0.26f), VortexStyle.BLAU, 0.10f + 0.12f * sel), al));
            Glatt.rahmen(ctx, mx0, my0, mw, mh, r + 2, 1, Glatt.alpha(Glatt.mix(0x40FFFFFF, VortexStyle.akzent(0.4f), 0.5f + 0.5f * sel), al));
            Glatt.rund(ctx, mx0 + 12, my0 + mh - 3, mw - 24, 1.5f, 0.75f, Glatt.alpha(VortexStyle.akzent(0.5f), al * (0.55f + 0.45f * sel)));
            float tx = mitteX + 18;
            Glatt.symbol(ctx, k.sym(), tx, my0 + (mh - 16) / 2f, 16, Glatt.alpha(0xFFFFFFFF, al));
            tx += 16 + 7;
            float th = 8 * MITTE_SKALA;
            var pz = ctx.pose();
            pz.pushMatrix();
            pz.translate(tx, my0 + (mh - th) / 2f + 0.5f);
            pz.scale(MITTE_SKALA, MITTE_SKALA);
            ctx.text(this.font, Component.literal(k.text()), 0, 0, VortexStyle.fade(0xFFFFFFFF, al), false);
            pz.popMatrix();
            if (!k.rechts().isEmpty()) {
                float rx = tx + this.font.width(k.text()) * MITTE_SKALA + 6;
                ctx.text(this.font, Component.literal(k.rechts()), Math.round(rx), Math.round(my0 + (mh - 8) / 2f + 1),
                        VortexStyle.fade(Glatt.mix(0xFFC4B5FD, 0xFFFFFFFF, sel), al), false);
            }
        }
        x = x0;
        for (int i = 0; i < l.size(); i++) {
            Knopf k = l.get(i);
            int sw = b[i];
            if (i == mitteNr) {
                hits.add(new Hit(x, y - MITTE_UEBER, sw, h + 2 * MITTE_UEBER, k.run()));
                reihenfolge.add(k.run());
                x += sw;
                continue;
            }
            float sel = wert("s:" + k.key(), i == ziel, 14f);
            int pad = gross ? 12 : 9;
            int tx = x + pad + Math.max(0, (sw - segmentBreite(k, gross)) / 2);
            int farbe = Glatt.mix(gross ? 0xFFC9C4DA : 0xFF9E98B4, 0xFFFFFFFF, sel);
            if (gross && k.sym() != null) {
                Glatt.symbol(ctx, k.sym(), tx, fy + (h - 12) / 2f, 12, Glatt.alpha(Glatt.mix(0xFFA79FD0, 0xFFFFFFFF, sel), al));
                tx += 12 + 6;
            }
            int ty = Math.round(fy) + (h - 8) / 2;
            ctx.text(this.font, Component.literal(k.text()), tx, ty, VortexStyle.fade(farbe, al), false);
            if (!k.rechts().isEmpty()) {
                int rf = k.key().equals("mods") ? Glatt.mix(VortexStyle.VIOLETT, 0xFFFFFFFF, 0.35f)
                        : VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, sel);
                ctx.text(this.font, Component.literal(k.rechts()), tx + this.font.width(k.text()) + 5, ty, VortexStyle.fade(rf, al), false);
            }
            hits.add(new Hit(x, y, sw, h, k.run()));
            reihenfolge.add(k.run());
            x += sw;
        }
        return nr + l.size();
    }

    /** Oben rechts: Restart und Close als kleine Leiste. */
    private int ecke(GuiGraphicsExtractor ctx, int rechts, int y, float a, int nr) {
        float al = a * einblenden(0.2f, 0.3f);
        int s = 22;
        int x0 = rechts - 2 * s;
        Glatt.rund(ctx, x0, y, 2 * s, s, 5, Glatt.alpha(SPALTE, al));
        Glatt.rahmen(ctx, x0, y, 2 * s, s, 5, 1, Glatt.alpha(0x16FFFFFF, al));
        Glatt.flaeche(ctx, x0 + s, y + 5, x0 + s + 1f / Glatt.skala(), y + s - 5, Glatt.alpha(0x1CFFFFFF, al));
        Symbol[] sym = {Symbol.NEUSTART,
                Symbol.KREUZ};
        Runnable[] run = {this::neustartAnfragen, this::onClose};
        String tipp = null;
        for (int i = 0; i < 2; i++) {
            int bx = x0 + i * s;
            boolean drauf = over(bx, y, s, s) || tastenZeile == nr + i;
            if (i == 0 && !drauf) neustartFrage = false;
            float hv = wert("e:" + i, drauf, 14f);
            float rot = i == 0 ? wert("e:rot", neustartFrage, 12f) : 0f;
            int grund = Glatt.mix(Glatt.mix(SPALTE, VortexStyle.VIOLETT, 0.30f), Glatt.mix(SPALTE, 0xFFEF4444, 0.45f), Math.max(rot, i == 1 ? hv * 0.6f : 0f));
            if (hv > 0.01f || rot > 0.01f) Glatt.rund(ctx, bx + 2, y + 2, s - 4, s - 4, 3.5f, Glatt.alpha(grund, al * Math.max(hv, rot)));
            int f = Glatt.mix(0xFFA79FD0, 0xFFFFFFFF, hv);
            if (i == 0) f = Glatt.mix(f, ROT_TEXT, rot);
            Glatt.symbol(ctx, sym[i], bx + 6, y + 6, 10, Glatt.alpha(f, al));
            hits.add(new Hit(bx, y, s, s, run[i]));
            reihenfolge.add(run[i]);
            if (drauf) tipp = i == 0 ? (neustartFrage ? "Click again to restart" : "Restart") : "Close";
        }
        if (tipp != null) {
            int tw = this.font.width(tipp);
            ctx.text(this.font, Component.literal(tipp), rechts - tw, y + s + 5,
                    VortexStyle.fade(neustartFrage ? ROT_TEXT : VortexStyle.TEXT_DIM, al), false);
        }
        return nr + 2;
    }

    private void profil(GuiGraphicsExtractor ctx, int x, int y, float a) {
        Minecraft mc = Minecraft.getInstance();
        float al = a * einblenden(0.2f, 0.2f);
        String name = mc.getUser() == null ? "Player" : mc.getUser().getName();
        boolean online = com.vortex.client.social.Social.conn() == com.vortex.client.social.Social.Conn.ONLINE;
        String status = online ? "Online" : "Offline";
        int w = 12 + 4 + 16 + Math.max(this.font.width(name), this.font.width(status) + 6) + 8;
        int h = 26;
        float hv = wert("profil", over(x, y, w, h), 12f);
        Glatt.rund(ctx, x, y, w, h, 4, Glatt.alpha(Glatt.mix(SPALTE, VortexStyle.HOV, hv), al));
        kopfBild(ctx, x + 5, y + 5, 16);
        ctx.text(this.font, Component.literal(name), x + 26, y + 4, VortexStyle.fade(VortexStyle.TEXT, al), false);
        ctx.fill(x + 26, y + 16, x + 29, y + 19, VortexStyle.fade(online ? GRUEN : 0xFF6B6680, al));
        ctx.text(this.font, Component.literal(status), x + 32, y + 14, VortexStyle.fade(VortexStyle.TEXT_DIM, al), false);
        hits.add(new Hit(x, y, w, h, () -> mc.gui.setScreen(new FriendsScreen(this))));
    }

    private List<List<Knopf>> reihen() {
        Minecraft mc = Minecraft.getInstance();
        int[] z = zaehle();
        int bots = 0;
        for (Module mod : ModuleManager.INSTANCE.getModules()) {
            if (!com.vortex.client.core.CleanModules.versteckt(mod) && mod.getCategory() == Module.Category.BOTS) bots++;
        }
        int neu = com.vortex.client.social.Social.data() == null ? 0 : com.vortex.client.social.Social.unreadTotal();
        int wp = zahl(() -> com.vortex.client.waypoint.WaypointManager.all().size());
        int ma = zahl(() -> com.vortex.client.macro.MacroManager.all().size());
        int[] beta = betaDaten();

        List<Knopf> gross = new ArrayList<>();
        gross.add(new Knopf("hud", "HUD Editor", "", Symbol.HUD, () -> mc.gui.setScreen(new HudEditorScreen())));
        gross.add(new Knopf("wardrobe", "Wardrobe", "", Symbol.HEMD, () -> mc.gui.setScreen(new SkinScreen(this))));
        gross.add(new Knopf("cosmetics", "Cosmetics", "", Symbol.STERN,
                () -> mc.gui.setScreen(new com.vortex.client.cosmetics.CosmeticsScreen(this))));
        gross.add(new Knopf("mods", "Mods", z[0] + "/" + z[1], Symbol.RASTER, () -> mc.gui.setScreen(new PanelGui())));
        gross.add(new Knopf("friends", "Friends", neu > 0 ? String.valueOf(Math.min(neu, 99)) : "", Symbol.LEUTE,
                () -> mc.gui.setScreen(new FriendsScreen(this))));
        gross.add(new Knopf("music", "Music", "", Symbol.NOTE, () -> mc.gui.setScreen(new com.vortex.client.musik.MusikScreen(this))));
        gross.add(new Knopf("settings", "Settings", "", Symbol.ZAHNRAD, () -> mc.gui.setScreen(new SettingsScreen(this, "Client Settings",
                com.vortex.client.core.ClientSettings.INSTANCE.all()))));

        List<Knopf> klein = new ArrayList<>();
        klein.add(new Knopf("presets", "Presets", "", null, () -> mc.gui.setScreen(new PresetScreen(this))));
        klein.add(new Knopf("keys", "Keybinds", "", null, () -> mc.gui.setScreen(new KeyListScreen(this))));
        klein.add(new Knopf("waypoints", "Waypoints", wp > 0 ? String.valueOf(wp) : "", null, () -> mc.gui.setScreen(new WaypointScreen(this))));
        klein.add(new Knopf("macros", "Macros", ma > 0 ? String.valueOf(ma) : "", null, () -> mc.gui.setScreen(new MacroScreen(this))));
        if (!com.vortex.client.core.CleanModules.aktiv()) {
            klein.add(new Knopf("bots", "Bots", bots > 0 ? String.valueOf(bots) : "", null, () -> mc.gui.setScreen(new BotScreen(this))));
        }
        klein.add(new Knopf("community", "Community", "", null, () -> mc.gui.setScreen(new CommunityScreen(this))));
        klein.add(new Knopf("creators", "Creators", "", null, () -> mc.gui.setScreen(new com.vortex.client.cosmetics.CreatorScreen(this))));
        klein.add(new Knopf("pictures", "Pictures", "", null, () -> mc.gui.setScreen(new com.vortex.client.gui.menu.PicturesScreen(this))));
        klein.add(new Knopf("styles", "Styles", "", null, () -> mc.gui.setScreen(new StylesScreen(this))));
        klein.add(new Knopf("design", "Design", "", null, () -> mc.gui.setScreen(new DesignScreen(this))));
        if (com.vortex.client.gui.menu.LauncherHosting.aktiv()) {
            klein.add(new Knopf("host", "Hosting", "", null, () -> mc.gui.setScreen(new com.vortex.client.gui.menu.HostingOptionsScreen(this))));
        } else if (mc.hasSingleplayerServer() && com.vortex.client.gui.menu.LauncherHosting.kannHosten() && mc.getSingleplayerServer() != null) {
            var sp = mc.getSingleplayerServer();
            String ordner;
            try { ordner = sp.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString(); }
            catch (Throwable t) { ordner = null; }
            final String o = ordner;
            if (o != null) klein.add(new Knopf("host", "Host", "", null,
                    () -> mc.gui.setScreen(new com.vortex.client.gui.menu.WeltHostenScreen(this, o, sp.getWorldData().getLevelName(), true))));
        } else if (mc.hasSingleplayerServer()) {
            klein.add(new Knopf("host", "Host", "", null, () -> mc.gui.setScreen(new com.vortex.client.gui.menu.HostScreen(this))));
        }
        if (beta[1] > 0) {
            klein.add(new Knopf("beta", "Beta", beta[0] + "/" + beta[1], null,
                    () -> mc.gui.setScreen(new com.vortex.client.beta.BetaScreen(this))));
        }
        return List.of(gross, klein);
    }

    private void neustartAnfragen() {
        if (neustartFrage || !com.vortex.client.core.ClientSettings.INSTANCE.confirmRestart.get()) {
            neustartFrage = false;
            neustarten();
        } else {
            neustartFrage = true;
            fehler = null;
        }
    }

    private static int zahl(java.util.function.IntSupplier n) {
        try { return n.getAsInt(); } catch (Throwable e) { return 0; }
    }

    private static int[] zaehle() {
        int aktiv = 0, alle = 0;
        for (Module mod : ModuleManager.INSTANCE.getModules()) {
            if (com.vortex.client.core.CleanModules.versteckt(mod) || !mod.imMenue()) continue;
            if (mod.getCategory() == Module.Category.BOTS) continue;
            alle++;
            if (mod.isEnabled()) aktiv++;
        }
        return new int[]{aktiv, alle};
    }

    private static final String VERSION = version();

    private static String version() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("vortexclient")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("").replaceAll("\\+.*$", "");
        } catch (Throwable e) { return ""; }
    }

    private static int[] betaDaten() {
        try {
            return com.vortex.client.beta.BetaTest.aktiv() ? com.vortex.client.beta.BetaTest.fortschritt() : new int[]{0, 0, 0};
        } catch (Throwable e) {
            return new int[]{0, 0, 0};
        }
    }

    private static net.minecraft.world.entity.player.PlayerSkin skin;
    private static boolean skinAngefragt;

    /** Nach einem Accountwechsel: Kopf des neuen Kontos zeigen. */
    public static void kopfNeu() {
        skin = null;
        skinAngefragt = false;
    }

    /** Gesicht + Hutschicht des eigenen Skins. */
    private static void kopfBild(GuiGraphicsExtractor ctx, int x, int y, int g) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!skinAngefragt) {
                skinAngefragt = true;
                mc.getSkinManager().get(mc.getGameProfile()).thenAccept(o -> o.ifPresent(s -> skin = s));
            }
            var s = skin != null ? skin : net.minecraft.client.resources.DefaultPlayerSkin.get(mc.getGameProfile());
            ctx.blit(s.body().texturePath(), x, y, x + g, y + g, 0.125F, 0.25F, 0.125F, 0.25F);
            ctx.blit(s.body().texturePath(), x, y, x + g, y + g, 0.625F, 0.75F, 0.125F, 0.25F);
        } catch (Throwable ignored) {
        }
    }

    private static void verlaufBand(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int von, int bis) {
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

    private String kuerzen(String s, int max) {
        if (max <= 6) return "";
        if (this.font.width(s) <= max) return s;
        return this.font.plainSubstrByWidth(s, max - this.font.width("..")) + "..";
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        int x = (int) (click.x() / zoom), y = (int) (click.y() / zoom);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (x >= h.x() && x < h.x() + h.w() && y >= h.y() && y < h.y() + h.h()) { h.run().run(); return true; }
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int k = e.key();
        int n = reihenfolge.size();
        if (n > 0) {
            if (k == 264 || k == 262) { tastenZeile = (tastenZeile + 1) % n; neustartFrage = false; return true; }
            if (k == 265 || k == 263) { tastenZeile = tastenZeile <= 0 ? n - 1 : tastenZeile - 1; neustartFrage = false; return true; }
            if ((k == 257 || k == 335) && tastenZeile >= 0 && tastenZeile < n) { reihenfolge.get(tastenZeile).run(); return true; }
        }
        return super.keyPressed(e);
    }

    private void neustarten() {
        try {
            com.vortex.client.util.GameRestarter.restart();
        } catch (Throwable err) {
            fehler = "Restart failed: " + (err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage());
            com.vortex.client.core.Errors.report("HomeScreen.restart", err);
        }
    }

    private float wert(String key, boolean an, float tempo) {
        float v = weich(werte.getOrDefault(key, 0f), an ? 1f : 0f, tempo, dtJetzt);
        werte.put(key, v);
        return v;
    }

    private boolean over(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 0..1 ab "start" Sekunden nach dem Oeffnen, ueber "dauer" Sekunden, mit Ausklang. */
    private float einblenden(float start, float dauer) {
        float t = Math.max(0f, Math.min(1f, (seit - start) / dauer));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float weich(float ist, float ziel, float tempo, float dt) {
        float f = 1f - (float) Math.exp(-tempo * dt);
        float neu = ist + (ziel - ist) * f;
        return Math.abs(ziel - neu) < 0.003f ? ziel : neu;
    }
}
