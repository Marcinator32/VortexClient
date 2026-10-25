package com.vortex.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.vortex.client.styles.Effekte;
import com.vortex.client.styles.Kategorie;
import com.vortex.client.styles.Styles;
import com.vortex.client.styles.Styles.Stil;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Styles-Menue ("Styles" in der kleinen Leiste des Rechtsshift-Menues).
 *
 * Links die Teile (Crystal, Anchor, Sword, Deepslate ...), rechts die Styles
 * dafuer als Karten mit Vorschau: oben die Effekte des Teils (seit 4.66: Look wie
 * Potato/Smooth/Edges, Feuer-Hoehe, Totem-Pop-Groesse, Kristall-Rahmen), darunter
 * Vanilla, die eingebauten Farben und alles, was man selbst importiert hat. Ein
 * Klick waehlt; Effekte und Farben lassen sich kombinieren, jedes Teil hat seine
 * eigene Wahl, so dass man frei mischt.
 *
 * Angewendet wird beim Verlassen des Menues (oder mit "Apply") -- einmal
 * neu laden statt bei jedem Klick.
 */
public class StylesScreen extends Screen {
    private static final int KOPF = 40, LEISTE_W = 118, ZEILE_H = 20, KARTE_W = 70, KARTE_H = 82, BILD = 48, LUECKE = 6;

    private record Textur(Identifier id, int w, int h) {}

    private final Screen parent;
    private Kategorie kat = Kategorie.CRYSTAL;
    private List<Stil> stile = List.of();
    private String beimOeffnen;
    private final Map<String, Textur> vorschau = new ConcurrentHashMap<>();
    private final Set<String> angefragt = ConcurrentHashMap.newKeySet();
    private final Set<String> ohneBild = ConcurrentHashMap.newKeySet();
    private final ExecutorService lader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vortex-Styles-Preview");
        t.setDaemon(true);
        return t;
    });
    private int naechsteId;
    private float scroll, leisteScroll;
    private String meldung = "";
    private long meldungBis;
    private String entfernenFrage;
    private boolean importLaeuft;

    private int rx, ry, rw, rh, spalten = 1;

    public StylesScreen(Screen parent) {
        super(Component.literal("Styles"));
        this.parent = parent;
        this.beimOeffnen = Styles.stand();
        neuEinlesen();
    }

    /** Fuer Tests und Sprung von aussen. */
    public void zeigeKategorie(Kategorie k) {
        this.kat = k;
        this.scroll = 0;
        neuEinlesen();
    }

    private void neuEinlesen() {
        stile = Styles.stile(kat);
        entfernenFrage = null;
    }

    @Override
    protected void init() {
        int y = 10;
        boolean eng = this.width < 420;
        int x = this.width - (eng ? 6 : 12);
        int luecke = eng ? 2 : 4;
        x -= eng ? 40 : 58;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y, eng ? 40 : 58, 20).build());
        x -= (eng ? 44 : 58) + luecke;
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> anwenden()).bounds(x, y, eng ? 44 : 58, 20).build());
        x -= (eng ? 46 : 78) + luecke;
        addRenderableWidget(Button.builder(Component.literal(eng ? "Folder" : "Open folder"), b -> ordnerOeffnen()).bounds(x, y, eng ? 46 : 78, 20).build());
        x -= (eng ? 46 : 68) + luecke;
        addRenderableWidget(Button.builder(Component.literal(eng ? "Import" : "Import..."), b -> importieren()).bounds(x, y, eng ? 46 : 68, 20).build());
        x -= (eng ? 42 : 68) + luecke;
        addRenderableWidget(Button.builder(Component.literal(eng ? "Reset" : "Reset all"), b -> {
            Styles.allesZuruecksetzen();
            neuEinlesen();
            melde("Everything is vanilla now -- press Apply or leave the menu.");
        }).bounds(x, y, eng ? 42 : 68, 20).build());
        knoepfeLinks = x;
    }

    private int knoepfeLinks;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        if (knoepfeLinks > 12 + this.font.width("Styles") + 8) {
            g.text(this.font, "Styles", 12, 12, VortexStyle.TEXT, true);
            String unter = "Pick a look for every part and mix them";
            if (12 + this.font.width(unter) < knoepfeLinks - 6) g.text(this.font, unter, 12, 24, VortexStyle.TEXT_DIM, false);
        }
        VortexStyle.akzentLinie(g, 8, KOPF - 4, this.width - 16, 1f);

        zeichneLeiste(g, mouseX, mouseY);
        zeichneRaster(g, mouseX, mouseY);

        String fuss;
        if (Styles.wirdAngewendet()) fuss = "Applying...";
        else if (importLaeuft) fuss = "Importing...";
        else if (!meldung.isEmpty() && System.currentTimeMillis() < meldungBis) fuss = meldung;
        else if (geaendert()) fuss = "Changes are applied when you leave this menu or press Apply.";
        else fuss = "Import resource packs (.zip) or style collections (.jar) to add your own styles.";
        fuss = kuerzen(fuss, this.width - 24);
        g.text(this.font, fuss, 12, this.height - 14, geaendert() && fuss.startsWith("Changes") ? VortexStyle.akzent(0.3f) : VortexStyle.TEXT_DIM, false);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void zeichneLeiste(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = 8, y0 = KOPF + 2, h = this.height - KOPF - 22;
        g.fill(x, y0, x + LEISTE_W, y0 + h, VortexStyle.BAR);
        Kategorie[] alle = Kategorie.values();
        float max = Math.max(0, alle.length * ZEILE_H + 4 - h);
        leisteScroll = Math.max(0, Math.min(leisteScroll, max));
        g.enableScissor(x, y0, x + LEISTE_W, y0 + h);
        for (int i = 0; i < alle.length; i++) {
            Kategorie k = alle[i];
            int y = y0 + 2 + i * ZEILE_H - Math.round(leisteScroll);
            boolean an = k == kat;
            boolean hover = mouseX >= x && mouseX < x + LEISTE_W && mouseY >= y && mouseY < y + ZEILE_H && mouseY >= y0 && mouseY < y0 + h;
            if (an) {
                g.fill(x + 2, y, x + LEISTE_W - 2, y + ZEILE_H - 1, VortexStyle.HOV);
                g.fill(x + 2, y, x + 4, y + ZEILE_H - 1, VortexStyle.akzent(i / (float) alle.length));
            } else if (hover) {
                g.fill(x + 2, y, x + LEISTE_W - 2, y + ZEILE_H - 1, VortexStyle.CARD);
            }
            Textur sym = vorschau.get(k.id + "|vanilla");
            if (sym == null) laden(k, k.id + "|vanilla", null);
            else {
                float f = Math.min(16f / sym.w(), 16f / sym.h());
                int sw = Math.max(1, Math.round(sym.w() * f)), sh = Math.max(1, Math.round(sym.h() * f));
                g.blit(RenderPipelines.GUI_TEXTURED, sym.id(), x + 7 + (16 - sw) / 2, y + 1 + (16 - sh) / 2, 0f, 0f, sw, sh, sym.w(), sym.h(), sym.w(), sym.h());
            }
            g.text(this.font, k.name, x + 27, y + 6, an ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, false);
            if (Styles.geaendert(k)) {
                int px = x + LEISTE_W - 10, py = y + 8;
                g.fill(px, py, px + 4, py + 4, VortexStyle.akzent(0.4f));
            }
        }
        g.disableScissor();
    }

    /** Eine Karte: Style (stil, null + vanilla = Vanilla) oder Effekt-Option. */
    private record Eintrag(int x, int y, Stil stil, boolean vanilla, Effekte.Gruppe gruppe, Effekte.Option option) {}
    private record Titel(String text, String rechts, int y) {}
    private final List<Eintrag> eintraege = new ArrayList<>();
    private final List<Titel> titel = new ArrayList<>();
    private int inhaltH, keineImporteY = -1;

    private void aufbauen() {
        eintraege.clear();
        titel.clear();
        keineImporteY = -1;
        int y = 18;
        for (Effekte.Gruppe gr : Effekte.fuer(kat)) {
            titel.add(new Titel(gr.name(), "", y));
            y += 13;
            List<Effekte.Option> opt = gr.optionen();
            for (int i = 0; i < opt.size(); i++)
                eintraege.add(new Eintrag((i % spalten) * (KARTE_W + LUECKE), y + (i / spalten) * (KARTE_H + LUECKE), null, false, gr, opt.get(i)));
            y += ((opt.size() + spalten - 1) / spalten) * (KARTE_H + LUECKE) + 6;
        }
        List<Stil> eingebaut = new ArrayList<>();
        List<Stil> importiert = new ArrayList<>();
        for (Stil st : stile) (st.importiert() ? importiert : eingebaut).add(st);
        titel.add(new Titel("Colors", "", y));
        y += 13;
        for (int i = 0; i <= eingebaut.size(); i++) {
            Stil st = i == 0 ? null : eingebaut.get(i - 1);
            eintraege.add(new Eintrag((i % spalten) * (KARTE_W + LUECKE), y + (i / spalten) * (KARTE_H + LUECKE), st, i == 0, null, null));
        }
        y += ((eingebaut.size() + 1 + spalten - 1) / spalten) * (KARTE_H + LUECKE) + 6;
        titel.add(new Titel("Imported", importiert.isEmpty() ? "" : String.valueOf(importiert.size()), y));
        y += 13;
        if (importiert.isEmpty()) {
            keineImporteY = y + 2;
            y += 14;
        } else {
            for (int i = 0; i < importiert.size(); i++)
                eintraege.add(new Eintrag((i % spalten) * (KARTE_W + LUECKE), y + (i / spalten) * (KARTE_H + LUECKE), importiert.get(i), false, null, null));
            y += ((importiert.size() + spalten - 1) / spalten) * (KARTE_H + LUECKE);
        }
        inhaltH = y + 8;
    }

    private void zeichneRaster(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        rx = 8 + LEISTE_W + 10;
        ry = KOPF + 2;
        rw = this.width - rx - 8;
        rh = this.height - KOPF - 22;
        spalten = Math.max(1, (rw + LUECKE) / (KARTE_W + LUECKE));
        aufbauen();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        g.enableScissor(rx, ry, rx + rw, ry + rh);
        int oben = ry + 2 - Math.round(scroll);
        String gewaehlt = Styles.gewaehlt(kat);

        g.text(this.font, kat.name, rx, oben + 2, VortexStyle.TEXT, true);
        g.text(this.font, "-  " + zusammenfassung(gewaehlt), rx + this.font.width(kat.name) + 6, oben + 2, VortexStyle.TEXT_DIM, false);
        for (Titel t : titel) {
            g.text(this.font, t.text(), rx, oben + t.y(), VortexStyle.TEXT, true);
            if (!t.rechts().isEmpty()) g.text(this.font, t.rechts(), rx + this.font.width(t.text()) + 6, oben + t.y(), VortexStyle.TEXT_DIM, false);
        }
        if (keineImporteY >= 0)
            g.text(this.font, "None yet -- use Import... to add resource packs or style collections.", rx, oben + keineImporteY, VortexStyle.TEXT_DIM, false);
        for (Eintrag e : eintraege) {
            int x = rx + e.x(), yy = oben + e.y();
            if (yy + KARTE_H < ry || yy > ry + rh) continue;
            if (e.gruppe() != null) effektKarte(g, e.gruppe(), e.option(), x, yy, mouseX, mouseY);
            else karte(g, e.stil(), x, yy, gewaehlt, mouseX, mouseY);
        }
        g.disableScissor();
    }

    /** "Purple, Potato, Low" -- was an diesem Teil gerade gewaehlt ist. */
    private String zusammenfassung(String gewaehlt) {
        StringBuilder b = new StringBuilder(gewaehlt == null ? "Vanilla" : name(gewaehlt));
        for (Effekte.Gruppe gr : Effekte.fuer(kat)) {
            String v = Styles.effekt(kat, gr.id());
            if (v != null && !v.equals(gr.standard().id())) b.append(", ").append(gr.option(v).name());
        }
        return b.toString();
    }

    private void effektKarte(GuiGraphicsExtractor g, Effekte.Gruppe gr, Effekte.Option o, int x, int y, int mouseX, int mouseY) {
        boolean an = o.id().equals(Styles.effekt(kat, gr.id()));
        boolean hover = drin(mouseX, mouseY, x, y, KARTE_W, KARTE_H) && mouseY >= ry && mouseY < ry + rh;
        rahmen(g, x, y, an, hover);
        int bx = x + (KARTE_W - BILD) / 2, by = y + 6;
        g.fill(bx - 2, by - 2, bx + BILD + 2, by + BILD + 2, VortexStyle.INNER);
        String gew = Styles.gewaehlt(kat);
        String key = kat.id + "|fx:" + gr.id() + ":" + o.id() + "|" + (gew == null ? "" : gew) + (kat.leiste() ? "|" + Styles.effekt(kat, "tint") : "");
        Textur t = vorschau.get(key);
        if (t == null) {
            if (!ohneBild.contains(key)) {
                final Kategorie k = kat;
                laden(key, () -> Styles.vorschauEffekt(k, gr.id(), o.id()));
                g.text(this.font, "...", bx + BILD / 2 - 6, by + BILD / 2 - 4, VortexStyle.TEXT_DIM, false);
            }
        } else {
            bild(g, t, bx, by);
        }
        String name = kuerzen(o.name(), KARTE_W - 6);
        g.text(this.font, name, x + (KARTE_W - this.font.width(name)) / 2, y + KARTE_H - 22, an ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, false);
        String art = kuerzen(an ? "Selected" : o.info(), KARTE_W - 4);
        g.text(this.font, art, x + (KARTE_W - this.font.width(art)) / 2, y + KARTE_H - 11, an ? VortexStyle.akzent(0.5f) : VortexStyle.fade(VortexStyle.TEXT_DIM, 0.7f), false);
    }

    private void rahmen(GuiGraphicsExtractor g, int x, int y, boolean an, boolean hover) {
        if (an) {
            g.fill(x - 1, y - 1, x + KARTE_W + 1, y + KARTE_H + 1, VortexStyle.akzent(0.2f));
            g.fill(x, y, x + KARTE_W, y + KARTE_H, VortexStyle.HOV);
        } else {
            g.fill(x - 1, y - 1, x + KARTE_W + 1, y + KARTE_H + 1, hover ? VortexStyle.VIOLETT : VortexStyle.LINE);
            g.fill(x, y, x + KARTE_W, y + KARTE_H, hover ? VortexStyle.HOV : VortexStyle.CARD);
        }
    }

    private void bild(GuiGraphicsExtractor g, Textur t, int bx, int by) {
        float f = Math.min(BILD / (float) t.w(), BILD / (float) t.h());
        int w = Math.max(1, Math.round(t.w() * f)), h = Math.max(1, Math.round(t.h() * f));
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), bx + (BILD - w) / 2, by + (BILD - h) / 2, 0f, 0f, w, h, t.w(), t.h(), t.w(), t.h());
    }

    private void karte(GuiGraphicsExtractor g, Stil s, int x, int y, String gewaehlt, int mouseX, int mouseY) {
        String id = s == null ? null : s.id();
        boolean an = id == null ? gewaehlt == null : id.equals(gewaehlt);
        boolean hover = drin(mouseX, mouseY, x, y, KARTE_W, KARTE_H) && mouseY >= ry && mouseY < ry + rh;
        rahmen(g, x, y, an, hover);
        int bx = x + (KARTE_W - BILD) / 2, by = y + 6;
        g.fill(bx - 2, by - 2, bx + BILD + 2, by + BILD + 2, VortexStyle.INNER);
        String key = kat.id + "|" + (id == null ? "vanilla" : id);
        Textur t = vorschau.get(key);
        if (t == null) {
            if (!ohneBild.contains(key)) {
                laden(kat, key, s);
                g.text(this.font, "...", bx + BILD / 2 - 6, by + BILD / 2 - 4, VortexStyle.TEXT_DIM, false);
            }
        } else {
            bild(g, t, bx, by);
        }
        String name = kuerzen(s == null ? "Vanilla" : s.name(), KARTE_W - 6);
        g.text(this.font, name, x + (KARTE_W - this.font.width(name)) / 2, y + KARTE_H - 22, an ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, false);
        String art = s == null ? "Default" : s.importiert() ? "Imported" : "Color";
        if (an) art = "Selected";
        g.text(this.font, art, x + (KARTE_W - this.font.width(art)) / 2, y + KARTE_H - 11, an ? VortexStyle.akzent(0.5f) : VortexStyle.fade(VortexStyle.TEXT_DIM, 0.7f), false);
        if (s != null && s.importiert() && (hover || s.id().equals(entfernenFrage))) {
            boolean frage = s.id().equals(entfernenFrage);
            String txt = frage ? "Remove?" : "x";
            int tw = this.font.width(txt) + 6;
            int kx = x + KARTE_W - tw - 2, ky = y + 2;
            boolean ueber = drin(mouseX, mouseY, kx, ky, tw, 11);
            g.fill(kx, ky, kx + tw, ky + 11, frage || ueber ? 0xFFB3263A : 0xCC000000);
            g.text(this.font, txt, kx + 3, ky + 2, VortexStyle.TEXT, false);
        }
    }

    private String name(String id) {
        for (Stil s : stile) if (s.id().equals(id)) return s.name();
        return id.startsWith("imp:") ? id.substring(4) : id;
    }

    private String kuerzen(String s, int max) {
        if (this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private static boolean drin(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void laden(Kategorie k, String key, Stil s) {
        laden(key, () -> Styles.vorschau(k, s));
    }

    private void laden(String key, java.util.function.Supplier<BufferedImage> macher) {
        if (!angefragt.add(key)) return;
        lader.submit(() -> {
            byte[] png = null;
            try {
                BufferedImage b = macher.get();
                if (b != null) png = Styles.png(b);
            } catch (Throwable t) {
                com.vortex.client.core.Errors.report("StylesScreen.vorschau", t);
            }
            final byte[] fertig = png;
            this.minecraft.execute(() -> {
                if (fertig == null) { ohneBild.add(key); return; }
                Textur t = anlegen(fertig);
                if (t == null) { ohneBild.add(key); return; }
                if (lader.isShutdown()) { this.minecraft.getTextureManager().release(t.id()); return; }
                vorschau.put(key, t);
            });
        });
    }

    private Textur anlegen(byte[] png) {
        try (InputStream in = new ByteArrayInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            int w = image.getWidth(), h = image.getHeight();
            Identifier id = Identifier.fromNamespaceAndPath("vortexclient", "styles/preview_" + (naechsteId++));
            this.minecraft.getTextureManager().register(id, new DynamicTexture(() -> "vortexclient-style", image));
            return new Textur(id, w, h);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("StylesScreen.anlegen", t);
            return null;
        }
    }

    private void vorschauLeeren() {
        for (Textur t : vorschau.values()) this.minecraft.getTextureManager().release(t.id());
        vorschau.clear();
        angefragt.clear();
        ohneBild.clear();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        double mx = click.x(), my = click.y();
        int lx = 8, ly0 = KOPF + 2, lh = this.height - KOPF - 22;
        if (drin(mx, my, lx, ly0, LEISTE_W, lh)) {
            int i = (int) ((my - ly0 - 2 + leisteScroll) / ZEILE_H);
            Kategorie[] alle = Kategorie.values();
            if (i >= 0 && i < alle.length && alle[i] != kat) zeigeKategorie(alle[i]);
            return true;
        }
        if (!drin(mx, my, rx, ry, rw, rh)) return false;
        int oben = ry + 2 - Math.round(scroll);
        for (Eintrag e : eintraege) {
            int x = rx + e.x(), yy = oben + e.y();
            if (!drin(mx, my, x, yy, KARTE_W, KARTE_H)) continue;
            if (e.gruppe() != null) {
                Styles.setzeEffekt(kat, e.gruppe().id(), e.option().id());
                entfernenFrage = null;
                return true;
            }
            Stil s = e.stil();
            if (s != null && s.importiert()) {
                boolean frage = s.id().equals(entfernenFrage);
                int tw = this.font.width(frage ? "Remove?" : "x") + 6;
                if (drin(mx, my, x + KARTE_W - tw - 2, yy + 2, tw, 11)) {
                    if (frage) {
                        Styles.entfernen(s);
                        vorschauLeeren();
                        neuEinlesen();
                        melde("Removed " + s.name() + ".");
                    } else {
                        entfernenFrage = s.id();
                    }
                    return true;
                }
            }
            entfernenFrage = null;
            Styles.waehle(kat, s == null ? null : s.id());
            return true;
        }
        entfernenFrage = null;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX < 8 + LEISTE_W + 4) leisteScroll -= (float) vertical * 20f;
        else scroll = Math.max(0, Math.min(maxScroll(), scroll - (float) vertical * 30f));
        return true;
    }

    private float maxScroll() {
        return Math.max(0, inhaltH - rh);
    }

    private boolean geaendert() {
        return !Styles.stand().equals(beimOeffnen);
    }

    private void anwenden() {
        if (Styles.wirdAngewendet()) return;
        beimOeffnen = Styles.stand();
        Styles.anwenden().thenAccept(this::melde);
    }

    private void melde(String s) {
        meldung = s == null ? "" : s;
        meldungBis = System.currentTimeMillis() + 6000;
    }

    private void importieren() {
        if (importLaeuft) return;
        importLaeuft = true;
        Thread t = new Thread(() -> {
            String pfad;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filter = stack.mallocPointer(2);
                filter.put(stack.UTF8("*.zip")).put(stack.UTF8("*.jar")).flip();
                pfad = TinyFileDialogs.tinyfd_openFileDialog("Import styles (resource pack or style collection)", null, filter,
                        "Resource packs and style collections", false);
            } catch (Throwable e) {
                pfad = null;
                this.minecraft.execute(() -> melde("The file picker could not be opened -- put the file into the styles folder instead."));
            }
            String msg = null;
            if (pfad != null) msg = Styles.importieren(Path.of(pfad));
            final String m = msg;
            this.minecraft.execute(() -> {
                importLaeuft = false;
                if (m != null) {
                    melde(m);
                    vorschauLeeren();
                    neuEinlesen();
                }
            });
        }, "Vortex-Styles-Import");
        t.setDaemon(true);
        t.start();
    }

    private void ordnerOeffnen() {
        try {
            Path o = Styles.ordner();
            Files.createDirectories(o);
            Util.getPlatform().openUri(o.toUri());
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("StylesScreen.ordner", e);
        }
    }

    @Override
    public void removed() {
        lader.shutdownNow();
        for (Textur t : vorschau.values()) this.minecraft.getTextureManager().release(t.id());
        vorschau.clear();
        super.removed();
    }

    @Override
    public void onClose() {
        if (geaendert()) anwenden();
        this.minecraft.gui.setScreen(parent);
    }
}
