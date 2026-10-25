package com.vortex.client.cosmetics;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Symbole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Shop und Umkleide (neu in 4.95), schlicht und dunkel wie ein Cosmetic-Client:
 *
 *   links    schmale Leiste mit den Kategorien (Shop: Featured ... Creator; Umkleide: Owned,
 *            Outfits, Kategorien, Emotes)
 *   Mitte    Kopfzeile (Kategorie, Suche, Sortierung, Filter) und das Raster gleich grosser
 *            Kacheln -- Vorschau, Name, Preis, Seltenheit, Besitz
 *   rechts   grosse 3D-Vorschau des eigenen Spielers (ziehen = drehen, Mausrad = Zoom), darunter
 *            das gewaehlte Teil mit Kaufen/Verschenken/Anlegen bzw. das aktuelle Loadout
 *
 * SHOP zeigt, was es zu kaufen gibt; ein Klick probiert an (nur fuer dich sichtbar), gekauft wird
 * unten rechts. UMKLEIDE zeigt nur, was dir gehoert; ein Klick zieht an. Guthaben, Besitz und
 * Creator-Rechte kommen vom Freunde-Server (Shop) -- ohne ihn ist alles frei wie frueher.
 */
public class CosmeticsScreen extends Screen {
    private enum Bereich { SHOP, WARDROBE }

    private enum Reiter {
        FEATURED("Featured", Symbole.Symbol.STERN, null), OWNED("Owned", Symbole.Symbol.HAKEN, null),
        OUTFITS("Outfits", Symbole.Symbol.REGLER, null),
        CAPES("Capes", Symbole.Symbol.HEMD, "cape"), BACK("Wings & Back", Symbole.Symbol.WELLEN, "back"),
        HATS("Hats", Symbole.Symbol.STERN, "hat"), FACE("Masks", Symbole.Symbol.PERSON, "face"),
        NECK("Neck", Symbole.Symbol.NADEL, "neck"), BANDANAS("Bandanas", Symbole.Symbol.HEMD, "bandana"),
        PETS("Pets", Symbole.Symbol.KOPFHOERER, "pet"), AURAS("Effects", Symbole.Symbol.BLITZ, "aura"),
        SHIELDS("Shields", Symbole.Symbol.HAKEN, "shield"), NAMETAG("Nametags", Symbole.Symbol.BLITZ, "nametag"),
        CREATOR("Creator", Symbole.Symbol.STERN, null), EMOTES("Emotes", Symbole.Symbol.PERSON, null);

        final String name;
        final Symbole.Symbol sym;
        final String kat;

        Reiter(String name, Symbole.Symbol sym, String kat) { this.name = name; this.sym = sym; this.kat = kat; }
    }

    private static final Reiter[] SHOP_REITER = { Reiter.FEATURED, Reiter.CAPES, Reiter.BACK, Reiter.HATS, Reiter.FACE, Reiter.NECK,
            Reiter.BANDANAS, Reiter.PETS, Reiter.AURAS, Reiter.SHIELDS, Reiter.NAMETAG, Reiter.CREATOR };
    private static final Reiter[] UMKLEIDE_REITER = { Reiter.OWNED, Reiter.OUTFITS, Reiter.CAPES, Reiter.BACK, Reiter.HATS, Reiter.FACE,
            Reiter.NECK, Reiter.BANDANAS, Reiter.PETS, Reiter.AURAS, Reiter.SHIELDS, Reiter.NAMETAG, Reiter.EMOTES };
    private static final String[] SORTIERUNG = { "Rarity", "Price", "Newest", "Name" };
    private static final String[] FILTER = { "All", "Owned", "Locked" };

    /** Eine Kachel: Kategorie im Shop ("cape", "hat" ...), Id, Name, Untertitel. Sonderkacheln: Id mit # oder @. */
    private record Kachel(String kat, String id, String name, String unter) {}
    private record Flaeche(int x, int y, int w, int h, Runnable aktion) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
    private record Opt(String text, boolean an, Runnable aktion) {}

    private static final int TEXT = 0xFFF1F1F4, DIM = 0xFF8C8C96, LEISE = 0xFF5E5E68;
    private static final int GRUND = 0xF5101013, LEISTE = 0xFF141418, KACHEL = 0xFF1A1A1F, KACHEL_HOV = 0xFF222228;
    private static final int LINIE = 0xFF26262D, FELD = 0xFF1D1D22, AKTIV = 0xFF26262E;
    private static final int GOLD = 0xFFFBBF24;

    private final Screen parent;
    private Bereich bereich;
    private Reiter reiter;
    private final List<Flaeche> flaechen = new ArrayList<>();
    private volatile List<String[]> katalog = null;
    private volatile String meldung = null;
    private long meldungBis;
    private boolean tasteWarten, kauftGerade, rechtsKlick;
    private float scroll, drehung = 160f, neigung = 0f, zoom = 1f;
    private boolean zieht;
    private int fx, fy, fw, fh, vx, vy, vw, vh;
    private int sortierung, filter;
    private String suche = "";
    private net.minecraft.client.gui.components.EditBox sucheFeld;
    private Kachel gewaehlt;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
        this.bereich = Shop.aktiv() ? Bereich.SHOP : Bereich.WARDROBE;
        this.reiter = bereich == Bereich.SHOP ? Reiter.FEATURED : Reiter.CAPES;
        Thread t = new Thread(() -> katalog = ActiveCape.katalog(), "Vortex-Cape-Catalogue");
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void init() {
        fw = Math.min(this.width - 16, 820);
        fh = Math.min(this.height - 16, 450);
        fx = (this.width - fw) / 2;
        fy = (this.height - fh) / 2;
        sucheFeld = new net.minecraft.client.gui.components.EditBox(this.font, fx, fy, 100, 12, Component.literal("Search"));
        sucheFeld.setBordered(false);
        sucheFeld.setMaxLength(40);
        sucheFeld.setValue(suche);
        sucheFeld.setResponder(v -> { suche = v.trim().toLowerCase(Locale.ROOT); scroll = 0; });
        addRenderableWidget(sucheFeld);
    }

    private void melden(String text) {
        meldung = text;
        meldungBis = System.currentTimeMillis() + 4500;
    }

    /* ------------------------------------------------------------------ Daten */

    private static boolean istSonder(String id) { return id.isEmpty() || id.startsWith("#") || id.startsWith("@"); }

    /** Alle Teile einer Kategorie, wie der Client sie kennt. */
    private List<Kachel> alleIn(String kat) {
        List<Kachel> k = new ArrayList<>();
        switch (kat) {
            case "cape" -> {
                for (CapeKunst.Design d : CapeKunst.alle().values()) k.add(new Kachel(kat, d.id(), d.name(), "Animated"));
                if (katalog != null) for (String[] c : katalog) k.add(new Kachel(kat, c[0], c[1], "Vortex cape"));
            }
            case "nametag" -> {
                for (int i = 0; i < NamensEffekt.IDS.length; i++) k.add(new Kachel(kat, NamensEffekt.IDS[i], NamensEffekt.NAMEN[i], NamensEffekt.TEXTE[i]));
            }
            default -> {
                Zubehoer.Kategorie z = zKat(kat);
                if (z != null) for (Zubehoer.Design d : Zubehoer.von(z)) k.add(new Kachel(kat, d.id(), d.name(), ""));
            }
        }
        return k;
    }

    private static Zubehoer.Kategorie zKat(String kat) {
        return switch (kat == null ? "" : kat) {
            case "hat" -> Zubehoer.Kategorie.HAT; case "bandana" -> Zubehoer.Kategorie.BANDANA; case "face" -> Zubehoer.Kategorie.FACE;
            case "neck" -> Zubehoer.Kategorie.NECK; case "back" -> Zubehoer.Kategorie.BACK; case "aura" -> Zubehoer.Kategorie.AURA;
            case "shield" -> Zubehoer.Kategorie.SHIELD; case "pet" -> Zubehoer.Kategorie.PET;
            default -> null;
        };
    }

    private static final String[] ALLE_KATS = { "cape", "back", "hat", "face", "neck", "bandana", "pet", "aura", "shield", "nametag" };

    private List<Kachel> kacheln() {
        List<Kachel> k = new ArrayList<>();
        boolean shop = bereich == Bereich.SHOP;
        switch (reiter) {
            case FEATURED, CREATOR -> {
                for (String kat : ALLE_KATS) for (Kachel x : alleIn(kat)) {
                    Shop.Item it = Shop.item(kat, x.id());
                    if (it == null) continue;
                    if (reiter == Reiter.FEATURED ? it.featured() : it.creatorOnly()) k.add(x);
                }
            }
            case OWNED -> {
                for (String kat : ALLE_KATS) for (Kachel x : alleIn(kat)) if (Shop.besitzt(kat, x.id()) && !nurCreatorGesperrt(kat, x.id())) k.add(x);
            }
            case OUTFITS -> {
                for (int i = 1; i <= 4; i++) {
                    String n = "Outfit " + i;
                    Outfits.Outfit o = outfit(n);
                    k.add(new Kachel("", "@slot:" + n, n, o == null ? "Empty  ·  click saves your look" : Outfits.kurz(o.auswahl())));
                }
                for (Outfits.Outfit o : Outfits.alle()) {
                    if (o.name().matches("Outfit [1-4]")) continue;
                    k.add(new Kachel("", "@outfit:" + o.name(), o.name(), Outfits.kurz(o.auswahl()) + "  ·  right-click deletes"));
                }
                k.add(new Kachel("", "@outfit-neu", "Save as...", "Your current look under a name"));
                return k;
            }
            case EMOTES -> {
                k.add(new Kachel("", "#emotetaste", tasteWarten ? "Press a key..." : "Wheel key: " + emoteTaste(), tasteWarten ? "Esc cancels" : "Click, then press a key"));
                for (Emotes.Emote e : Emotes.alle().values()) k.add(new Kachel("emote", e.id(), e.name(), e.seite()));
                return k;
            }
            default -> {
                String kat = reiter.kat;
                if (!shop) k.add(new Kachel(kat, "", "None", "Take it off"));
                for (Kachel x : alleIn(kat)) {
                    if (shop) {
                        Shop.Item it = Shop.item(kat, x.id());
                        if (Shop.aktiv() && (it == null || (!it.imVerkauf() && !it.besitzt()))) continue;
                    } else if (!Shop.besitzt(kat, x.id()) || nurCreatorGesperrt(kat, x.id())) {
                        continue;
                    }
                    k.add(x);
                }
                if (!shop && kat.equals("cape")) {
                    if (EigenesCape.vorhanden()) k.add(new Kachel(kat, EigenesCape.ID, "Your picture", "Custom cape"));
                    k.add(new Kachel(kat, "#upload", EigenesCape.vorhanden() ? "Change picture" : "Upload picture", "PNG or JPG"));
                    k.add(new Kachel(kat, "#editor", "Cape editor", "Paint your own"));
                    if (katalog == null) k.add(new Kachel(kat, "#laedt", "Loading...", "Vortex capes"));
                }
            }
        }
        if (!suche.isEmpty()) k.removeIf(x -> !istSonder(x.id()) && !x.name().toLowerCase(Locale.ROOT).contains(suche));
        if (shop && Shop.aktiv() && filter != 0) k.removeIf(x -> !istSonder(x.id()) && (filter == 1) != Shop.besitzt(x.kat(), x.id()));
        sortieren(k);
        return k;
    }

    /** Creator-Items ohne Creator-Status: im Besitz, aber gerade nicht tragbar. */
    private static boolean nurCreatorGesperrt(String kat, String id) {
        Shop.Item it = Shop.item(kat, id);
        return it != null && it.creatorOnly() && !it.besitzt();
    }

    private static int rang(String seltenheit) {
        return switch (seltenheit == null ? "" : seltenheit) {
            case "mythic" -> 6; case "legendary" -> 5; case "epic" -> 4; case "rare" -> 3; case "uncommon" -> 2; case "common" -> 1;
            default -> 0;
        };
    }

    private void sortieren(List<Kachel> k) {
        Comparator<Kachel> c = switch (sortierung) {
            case 1 -> Comparator.comparingInt((Kachel x) -> -preis(x)).thenComparing(Kachel::name, String.CASE_INSENSITIVE_ORDER);
            case 2 -> Comparator.comparingLong((Kachel x) -> { Shop.Item it = Shop.item(x.kat(), x.id()); return it == null ? 0L : -it.neu(); })
                    .thenComparing(Kachel::name, String.CASE_INSENSITIVE_ORDER);
            case 3 -> Comparator.comparing(Kachel::name, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparingInt((Kachel x) -> { Shop.Item it = Shop.item(x.kat(), x.id()); return it == null ? 0 : -rang(it.seltenheit()); })
                    .thenComparing(Kachel::name, String.CASE_INSENSITIVE_ORDER);
        };
        List<Kachel> sonder = new ArrayList<>(), rest = new ArrayList<>();
        for (Kachel x : k) (x.id().isEmpty() ? sonder : rest).add(x);
        rest.sort(c);
        k.clear();
        k.addAll(sonder);
        k.addAll(rest);
    }

    private static int preis(Kachel x) {
        Shop.Item it = Shop.item(x.kat(), x.id());
        return it == null ? 0 : it.preis();
    }

    private static Outfits.Outfit outfit(String name) {
        for (Outfits.Outfit o : Outfits.alle()) if (o.name().equals(name)) return o;
        return null;
    }

    /** Was in einer Kategorie gerade getragen wird. */
    private static String getragen(Cosmetics.Auswahl a, String kat) {
        return switch (kat == null ? "" : kat) {
            case "cape" -> a.cape(); case "hat" -> a.hut(); case "bandana" -> a.bandana(); case "face" -> a.gesicht();
            case "neck" -> a.hals(); case "nametag" -> a.namensstil(); case "back" -> a.ruecken(); case "aura" -> a.partikel();
            case "shield" -> a.schild(); case "pet" -> a.pet();
            default -> "";
        };
    }

    private static String basis(String kat, String id) {
        if (id == null) return "";
        if ("cape".equals(kat)) return CapeKunst.design(id) != null ? CapeKunst.basisId(id) : id;
        return Zubehoer.basisId(id);
    }

    /** Auswahl mit diesem Teil (Stil und Glitzern bleiben wie eingestellt). */
    private Cosmetics.Auswahl mit(Cosmetics.Auswahl a, String kat, String id) {
        return switch (kat) {
            case "cape" -> a.mitCape(CapeKunst.design(id) != null ? CapeKunst.mitStil(id, stil()) : id);
            case "hat" -> a.mitHut(Zubehoer.mitGlitzern(id, glitzern(kat)));
            case "bandana" -> a.mitBandana(Zubehoer.mitGlitzern(id, glitzern(kat)));
            case "face" -> a.mitGesicht(Zubehoer.mitGlitzern(id, glitzern(kat)));
            case "neck" -> a.mitHals(Zubehoer.mitGlitzern(id, glitzern(kat)));
            case "back" -> a.mitRuecken(Zubehoer.mitGlitzern(id, glitzern(kat)));
            case "shield" -> a.mitSchild(Zubehoer.mitGlitzern(id, glitzern(kat)));
            case "nametag" -> a.mitNamensstil(id);
            case "aura" -> a.mitAura(id);
            case "pet" -> a.mitPet(id);
            default -> a;
        };
    }

    private boolean traegt(Kachel k) {
        if (istSonder(k.id()) && !k.id().isEmpty()) return false;
        String g = basis(k.kat(), getragen(Cosmetics.eigene(), k.kat()));
        return g.equals(k.id());
    }

    /* ------------------------------------------------------------------ Aktionen */

    private void klick(Kachel k) {
        String id = k.id();
        if ("#laedt".equals(id)) return;
        if ("#upload".equals(id)) {
            melden("Choose a picture in the window that opened...");
            EigenesCape.waehlen(fehler -> this.minecraft.execute(() -> {
                if (fehler != null) { melden(fehler); return; }
                if (!EigenesCape.vorhanden()) return;
                Cosmetics.speichern(Cosmetics.eigene().mitCape(EigenesCape.ID), Cosmetics.partikelErstePerson());
                ActiveCape.neuLaden();
                melden("Your picture is on the cape now.");
            }));
            return;
        }
        if ("#editor".equals(id)) { this.minecraft.gui.setScreen(new CapeEditorScreen(this)); return; }
        if ("#emotetaste".equals(id)) { tasteWarten = true; melden("Press the key that opens the emote wheel (Esc cancels)."); return; }
        if (id.startsWith("@")) { outfitKlick(id); return; }
        if ("emote".equals(k.kat())) { Emotes.spielen(id); return; }
        gewaehlt = k;
        if (bereich == Bereich.SHOP) {
            anprobieren(k);
        } else {
            anprobeEnde();
            anlegen(k);
        }
    }

    private void anlegen(Kachel k) {
        if (!id(k).isEmpty() && !Shop.besitzt(k.kat(), k.id())) { melden("You do not own this yet."); return; }
        Cosmetics.speichern(mit(Cosmetics.eigene(), k.kat(), k.id()), Cosmetics.partikelErstePerson());
        if (k.id().isEmpty()) melden("Taken off.");
    }

    private static String id(Kachel k) { return k.id(); }

    private void ablegen(String kat) {
        Cosmetics.speichern(mit(Cosmetics.eigene(), kat, ""), Cosmetics.partikelErstePerson());
        melden("Taken off.");
    }

    /** Anprobe: nur fuer dich, bis du das Menue verlaesst oder etwas anderes waehlst. */
    private void anprobieren(Kachel k) {
        Cosmetics.Auswahl a = Shop.filtern(Cosmetics.eigene());
        Cosmetics.setzeAnprobe(mit(a, k.kat(), k.id()));
        if ("cape".equals(k.kat())) ActiveCape.neuLaden();
    }

    private void anprobeEnde() {
        boolean cape = Cosmetics.anprobe() != null;
        Cosmetics.setzeAnprobe(null);
        if (cape) ActiveCape.neuLaden();
    }

    private void kaufen(Kachel k) {
        Shop.Item it = Shop.item(k.kat(), k.id());
        if (it == null || kauftGerade) return;
        if (Shop.guthaben() < it.preis()) { melden("You need " + (it.preis() - Shop.guthaben()) + " more coins."); return; }
        kauftGerade = true;
        Shop.kaufen(it.id()).thenAccept(fehler -> this.minecraft.execute(() -> {
            kauftGerade = false;
            if (fehler != null) { melden(fehler); return; }
            anprobeEnde();
            anlegen(k);
            melden(it.preis() > 0 ? "Bought " + it.name() + "!" : "Unlocked " + it.name() + "!");
        }));
    }

    private void verschenken(Kachel k) {
        Shop.Item it = Shop.item(k.kat(), k.id());
        if (it == null) return;
        if (!it.verschenkbar()) { melden(it.name() + " cannot be gifted."); return; }
        this.minecraft.gui.setScreen(new com.vortex.client.gui.TextEingabeScreen(this, "Gift " + it.name() + " to", "",
                "Minecraft name of a Vortex player", name -> {
                    String n = name == null ? "" : name.trim();
                    if (n.isEmpty()) return;
                    melden("Sending the gift...");
                    Shop.schenken(it.id(), n).thenAccept(fehler -> this.minecraft.execute(() ->
                            melden(fehler != null ? fehler : "You gifted " + it.name() + " to " + n + ".")));
                }));
    }

    /** Outfit-Plaetze: leerer Platz speichert, voller zieht an; Rechtsklick speichert drueber bzw. loescht. */
    private void outfitKlick(String id) {
        if ("@outfit-neu".equals(id)) {
            this.minecraft.gui.setScreen(new com.vortex.client.gui.TextEingabeScreen(this, "Outfit name", "",
                    "For example PvP, Winter, Party", name -> {
                        String n = Outfits.sauber(name);
                        if (n.isEmpty()) { melden("The name needs letters or numbers."); return; }
                        melden(Outfits.speichern(n) ? "Saved outfit \"" + n + "\"." : "Could not save the outfit.");
                    }));
            return;
        }
        String name = id.startsWith("@slot:") ? id.substring(6) : id.startsWith("@outfit:") ? id.substring(8) : "";
        if (name.isEmpty()) return;
        Outfits.Outfit o = outfit(name);
        if (rechtsKlick) {
            if (id.startsWith("@slot:")) melden(Outfits.speichern(name) ? "Saved your look as " + name + "." : "Could not save.");
            else { Outfits.loeschen(name); melden("Deleted \"" + name + "\"."); }
            return;
        }
        if (o == null) {
            melden(Outfits.speichern(name) ? "Saved your look as " + name + ". Right-click saves over it." : "Could not save.");
            return;
        }
        Outfits.anziehen(o);
        melden("Wearing " + name + ".");
    }

    private static String emoteTaste() {
        return com.vortex.client.VortexClientMod.emoteKeyName();
    }

    /* ------------------------------------------------------------------ Zeichnen */

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        flaechen.clear();
        g.fill(0, 0, this.width, this.height, 0xB8050507);
        Glatt.rund(g, fx, fy, fw, fh, 3, GRUND);
        Glatt.rahmen(g, fx, fy, fw, fh, 3, 1, LINIE);

        kopf(g, mouseX, mouseY);

        boolean schmal = fw < 560;
        int sb = schmal ? 30 : 116;
        int oben = fy + 34;
        int hoehe = fh - 34;
        g.fill(fx + 1, oben, fx + sb, fy + fh - 1, LEISTE);
        g.fill(fx + sb, oben, fx + sb + 1, fy + fh - 1, LINIE);
        leiste(g, fx, oben, sb, hoehe, schmal, mouseX, mouseY);

        vw = Math.max(150, Math.min(300, (int) (fw * 0.34f)));
        vx = fx + fw - vw;
        vy = oben;
        vh = hoehe - 1;
        g.fill(vx, oben, vx + 1, fy + fh - 1, LINIE);
        vorschauSpalte(g, mouseX, mouseY);

        int mx0 = fx + sb + 1 + 10, mw = vx - mx0 - 10;
        mitte(g, mx0, oben + 8, mw, hoehe - 14, mouseX, mouseY);

        if (meldung != null && System.currentTimeMillis() < meldungBis) {
            float b = Glatt.breite(meldung, Glatt.Schrift.NORMAL) + 20;
            float mx = mx0 + mw / 2f - b / 2f, my = fy + fh - 26;
            Glatt.rund(g, mx, my, b, 17, 2, 0xF0202026);
            Glatt.rahmen(g, mx, my, b, 17, 2, 1, LINIE);
            Glatt.text(g, meldung, mx + 10, my + 5, TEXT, Glatt.Schrift.NORMAL);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void kopf(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y = fy, h = 34;
        g.fill(fx + 1, y + h - 1, fx + fw - 1, y + h, LINIE);
        Glatt.symbol(g, Symbole.Symbol.STERN, fx + 12, y + 11, 12, VortexAkzent());
        Glatt.text(g, "Vortex", fx + 30, y + 13, TEXT, Glatt.Schrift.FETT);
        int tx = fx + 30 + (int) Glatt.breite("Vortex", Glatt.Schrift.FETT) + 18;
        for (Bereich b : Bereich.values()) {
            String n = b == Bereich.SHOP ? "Shop" : "Wardrobe";
            int bw = (int) Glatt.breite(n, Glatt.Schrift.FETT) + 20;
            boolean an = b == bereich, hov = !an && mouseX >= tx && mouseX < tx + bw && mouseY >= y + 6 && mouseY < y + 28;
            if (an || hov) Glatt.rund(g, tx, y + 7, bw, 20, 2, an ? AKTIV : KACHEL);
            Glatt.text(g, n, tx + 10, y + 13, an ? TEXT : DIM, Glatt.Schrift.FETT);
            if (an) g.fill(tx + 6, y + 31, tx + bw - 6, y + 33, VortexAkzent());
            final Bereich ziel = b;
            flaechen.add(new Flaeche(tx, y + 6, bw, 22, () -> bereichWechseln(ziel)));
            tx += bw + 4;
        }

        int rx = fx + fw - 10;
        rx = kopfKnopf(g, rx, y + 8, "x", mouseX, mouseY, this::onClose);
        if (Shop.admin()) rx = kopfKnopf(g, rx, y + 8, "Admin", mouseX, mouseY, () -> this.minecraft.gui.setScreen(new ShopAdminScreen(this)));
        rx = kopfKnopf(g, rx, y + 8, "Creators", mouseX, mouseY, () -> this.minecraft.gui.setScreen(new CreatorScreen(this)));
        if (Shop.aktiv()) {
            rx = kopfKnopf(g, rx, y + 8, "History", mouseX, mouseY, () -> this.minecraft.gui.setScreen(new ShopVerlaufScreen(this)));
            String coins = String.valueOf(Shop.guthaben());
            String rate = "+" + Shop.proMinute() + "/min";
            float cw = Glatt.breite(coins, Glatt.Schrift.FETT) + Glatt.breite(rate, Glatt.Schrift.NORMAL) + 30;
            float cx = rx - cw - 4;
            if (cx > tx + 8) {
                Glatt.rund(g, cx, y + 8, cw, 18, 2, FELD);
                Glatt.kreis(g, cx + 10, y + 17, 8, GOLD);
                Glatt.text(g, coins, cx + 18, y + 13, 0xFFFDE68A, Glatt.Schrift.FETT);
                Glatt.text(g, rate, cx + 22 + Glatt.breite(coins, Glatt.Schrift.FETT), y + 13, LEISE, Glatt.Schrift.NORMAL);
            }
        }
    }

    private int kopfKnopf(GuiGraphicsExtractor g, int rechts, int y, String text, int mouseX, int mouseY, Runnable aktion) {
        int w = (int) Glatt.breite(text, Glatt.Schrift.NORMAL) + 16;
        int x = rechts - w;
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        Glatt.rund(g, x, y, w, 18, 2, hov ? KACHEL_HOV : KACHEL);
        Glatt.textMitte(g, text, x + w / 2f, y + 5, hov ? TEXT : DIM, Glatt.Schrift.NORMAL);
        flaechen.add(new Flaeche(x, y, w, 18, aktion));
        return x - 4;
    }

    private static int VortexAkzent() {
        return com.vortex.client.gui.Theme.INSTANCE.accent.get() | 0xFF000000;
    }

    private void bereichWechseln(Bereich b) {
        if (b == bereich) return;
        anprobeEnde();
        bereich = b;
        reiter = b == Bereich.SHOP ? Reiter.FEATURED : Reiter.OWNED;
        gewaehlt = null;
        scroll = 0;
        filter = 0;
    }

    private void leiste(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean schmal, int mouseX, int mouseY) {
        Reiter[] liste = bereich == Bereich.SHOP ? SHOP_REITER : UMKLEIDE_REITER;
        int rh = Math.max(16, Math.min(22, (h - 16) / liste.length));
        int ry = y + 8;
        for (Reiter r : liste) {
            boolean an = r == reiter, hov = !an && mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rh;
            if (an || hov) g.fill(x + 1, ry, x + w, ry + rh, an ? AKTIV : 0xFF1A1A1F);
            if (an) g.fill(x + 1, ry, x + 3, ry + rh, VortexAkzent());
            float sy = ry + (rh - 11) / 2f;
            Glatt.symbol(g, r.sym, schmal ? x + (w - 11) / 2f : x + 10, sy, 11, an ? TEXT : DIM);
            if (!schmal) {
                Glatt.text(g, r.name, x + 27, ry + (rh - 8) / 2f, an ? TEXT : DIM, Glatt.Schrift.NORMAL);
                int n = anzahl(r);
                if (n > 0) Glatt.textRechts(g, String.valueOf(n), x + w - 8, ry + (rh - 8) / 2f, LEISE, Glatt.Schrift.NORMAL);
            }
            final Reiter ziel = r;
            flaechen.add(new Flaeche(x, ry, w, rh, () -> reiterWechseln(ziel)));
            ry += rh;
        }
    }

    private int anzahl(Reiter r) {
        if (r == Reiter.OUTFITS || r == Reiter.EMOTES) return 0;
        Reiter alt = reiter;
        String s = suche;
        int f = filter;
        reiter = r;
        suche = "";
        filter = 0;
        try {
            int n = 0;
            for (Kachel k : kacheln()) if (!istSonder(k.id())) n++;
            return n;
        } finally {
            reiter = alt;
            suche = s;
            filter = f;
        }
    }

    private void reiterWechseln(Reiter r) {
        if (r == reiter) return;
        reiter = r;
        scroll = 0;
        gewaehlt = null;
        if (bereich == Bereich.SHOP) anprobeEnde();
    }

    /* ------------------------------------------------------------------ Mitte */

    private void mitte(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        Glatt.text(g, reiter.name, x, y + 4, TEXT, Glatt.Schrift.TITEL);
        int rechts = x + w;
        boolean hatListe = reiter != Reiter.OUTFITS && reiter != Reiter.EMOTES;
        if (hatListe) {
            if (bereich == Bereich.SHOP && Shop.aktiv()) {
                for (int i = FILTER.length - 1; i >= 0; i--) {
                    int bw = (int) Glatt.breite(FILTER[i], Glatt.Schrift.NORMAL) + 14;
                    rechts -= bw;
                    final int f = i;
                    chip(g, rechts, y + 2, bw, FILTER[i], filter == i, mouseX, mouseY, () -> { filter = f; scroll = 0; });
                    rechts -= 2;
                }
                rechts -= 6;
            }
            String so = "Sort: " + SORTIERUNG[sortierung];
            int sw = (int) Glatt.breite(so, Glatt.Schrift.NORMAL) + 14;
            rechts -= sw;
            chip(g, rechts, y + 2, sw, so, false, mouseX, mouseY, () -> { sortierung = (sortierung + 1) % SORTIERUNG.length; scroll = 0; });
            rechts -= 6;
            int tw = (int) Glatt.breite(reiter.name, Glatt.Schrift.TITEL);
            int suchW = Math.min(150, rechts - (x + tw + 12));
            if (suchW >= 60) {
                int sx = rechts - suchW;
                Glatt.rund(g, sx, y + 2, suchW, 16, 2, FELD);
                Glatt.symbol(g, Symbole.Symbol.SUCHE, sx + 4, y + 5, 10, LEISE);
                sucheFeld.visible = true;
                sucheFeld.setX(sx + 18);
                sucheFeld.setY(y + 6);
                sucheFeld.setWidth(suchW - 22);
                if (suche.isEmpty() && !sucheFeld.isFocused()) Glatt.text(g, "Search", sx + 18, y + 6, LEISE, Glatt.Schrift.NORMAL);
            } else {
                sucheFeld.visible = false;
            }
        } else {
            sucheFeld.visible = false;
        }
        int ly = y + 24;
        int lh = h - 24;
        if (bereich == Bereich.SHOP && !Shop.aktiv()) {
            Glatt.text(g, "The shop needs the Vortex friends server.", x, ly + 4, DIM, Glatt.Schrift.NORMAL);
            Glatt.text(g, "Everything is free until it is online -- pick your look in the Wardrobe.", x, ly + 16, LEISE, Glatt.Schrift.NORMAL);
            ly += 32;
            lh -= 32;
        }
        if (bereich == Bereich.WARDROBE) {
            int oh = optionen(g, x, ly, w, mouseX, mouseY);
            ly += oh;
            lh -= oh;
        }
        raster(g, x, ly, w, lh, mouseX, mouseY);
    }

    private void chip(GuiGraphicsExtractor g, int x, int y, int w, String text, boolean an, int mouseX, int mouseY, Runnable aktion) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        Glatt.rund(g, x, y, w, 16, 2, an ? AKTIV : hov ? KACHEL_HOV : FELD);
        if (an) Glatt.rahmen(g, x, y, w, 16, 2, 1, VortexAkzent());
        Glatt.textMitte(g, text, x + w / 2f, y + 4, an || hov ? TEXT : DIM, Glatt.Schrift.NORMAL);
        flaechen.add(new Flaeche(x, y, w, 16, aktion));
    }

    /** Einstellungen der Kategorie (nur Umkleide): Cape-Physik, Glitzern, Aura-Menge, Namen ... */
    private int optionen(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
        List<Opt> opts = new ArrayList<>();
        switch (reiter) {
            case CAPES -> {
                CapeKunst.Stil s = stil();
                opts.add(new Opt(anAus("Physics", Cosmetics.capePhysik()), Cosmetics.capePhysik(), () -> Cosmetics.setzeCapePhysik(!Cosmetics.capePhysik())));
                opts.add(new Opt(anAus("Waves", Cosmetics.capeWellen()), Cosmetics.capeWellen(), () -> Cosmetics.setzeCapeWellen(!Cosmetics.capeWellen())));
                opts.add(new Opt(anAus("Border", s.rand()), s.rand(), () -> setzeStil(stil().mitRand(!stil().rand()))));
                opts.add(new Opt(anAus("Shine", s.glanz()), s.glanz(), () -> setzeStil(stil().mitGlanz(!stil().glanz()))));
                opts.add(new Opt(anAus("Glow", s.leuchten()), s.leuchten(), () -> setzeStil(stil().mitLeuchten(!stil().leuchten()))));
                opts.add(new Opt(anAus("Motion", s.bewegt()), s.bewegt(), () -> setzeStil(stil().mitBewegt(!stil().bewegt()))));
                opts.add(new Opt("Color: " + FARBTOENE[s.farbton()], s.farbton() != 0, () -> setzeStil(stil().mitFarbton(stil().farbton() + 1))));
            }
            case AURAS -> {
                Cosmetics.Auswahl a = Cosmetics.eigene();
                int gf = Cosmetics.grosseFremde();
                opts.add(new Opt("Amount: " + (a.dichte() == 1 ? "Low" : a.dichte() == 3 ? "High" : "Medium"), a.dichte() != 2, () -> {
                    Cosmetics.Auswahl b = Cosmetics.eigene();
                    Cosmetics.speichern(b.mitDichte(b.dichte() % 3 + 1), Cosmetics.partikelErstePerson());
                }));
                opts.add(new Opt("Others' big auras: " + (gf == 1 ? "Small" : gf == 2 ? "Off" : "On"), gf == 0, () -> Cosmetics.setzeGrosseFremde(Cosmetics.grosseFremde() + 1)));
            }
            case NAMETAG -> {
                opts.add(new Opt(anAus("Name effects", Cosmetics.namenZeigen()), Cosmetics.namenZeigen(), () -> Cosmetics.setzeNamenZeigen(!Cosmetics.namenZeigen())));
                opts.add(new Opt(anAus("[V] badges", Cosmetics.abzeichenZeigen()), Cosmetics.abzeichenZeigen(), () -> Cosmetics.setzeAbzeichenZeigen(!Cosmetics.abzeichenZeigen())));
            }
            case HATS, BANDANAS, FACE, NECK, BACK, SHIELDS -> {
                final String kat = reiter.kat;
                if (reiter == Reiter.BANDANAS) opts.add(new Opt(anAus("Physics", Cosmetics.bandanaPhysik()), Cosmetics.bandanaPhysik(), () -> Cosmetics.setzeBandanaPhysik(!Cosmetics.bandanaPhysik())));
                boolean gl = glitzern(kat);
                opts.add(new Opt(anAus("Sparkle", gl), gl, () -> setzeGlitzern(kat, !glitzern(kat))));
            }
            default -> { }
        }
        if (opts.isEmpty()) return 0;
        int cx = x, cy = y, h = 15;
        for (Opt o : opts) {
            int bw = (int) Glatt.breite(o.text(), Glatt.Schrift.NORMAL) + 14;
            if (cx > x && cx + bw > x + w) { cx = x; cy += h + 3; }
            boolean hov = mouseX >= cx && mouseX < cx + bw && mouseY >= cy && mouseY < cy + h;
            Glatt.rund(g, cx, cy, bw, h, 2, hov ? KACHEL_HOV : FELD);
            if (o.an()) Glatt.rund(g, cx, cy + h - 2, bw, 2, 0, VortexAkzent());
            Glatt.textMitte(g, o.text(), cx + bw / 2f, cy + 4, o.an() ? TEXT : DIM, Glatt.Schrift.NORMAL);
            flaechen.add(new Flaeche(cx, cy, bw, h, o.aktion()));
            cx += bw + 3;
        }
        return cy - y + h + 8;
    }

    private static String anAus(String name, boolean an) { return name + ": " + (an ? "On" : "Off"); }

    private void raster(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<Kachel> liste = kacheln();
        boolean breit = reiter == Reiter.OUTFITS || reiter == Reiter.EMOTES;
        int spalten = breit ? Math.max(1, Math.min(3, w / 150)) : Math.max(2, Math.min(7, (w + 6) / 82));
        int kw = (w - (spalten - 1) * 6) / spalten;
        int kh = breit ? 34 : kw + 26;
        int zeilen = (liste.size() + spalten - 1) / spalten;
        float max = Math.max(0, zeilen * (kh + 6) - 6 - h);
        scroll = Math.max(0, Math.min(scroll, max));
        if (liste.isEmpty()) {
            String a = bereich == Bereich.WARDROBE ? "Nothing here yet" : !suche.isEmpty() ? "Nothing found" : "Nothing in this category";
            String b = bereich == Bereich.WARDROBE ? "Buy cosmetics in the Shop, they show up here." : "Try another category or filter.";
            Glatt.textMitte(g, a, x + w / 2f, y + h / 2f - 10, TEXT, Glatt.Schrift.FETT);
            Glatt.textMitte(g, b, x + w / 2f, y + h / 2f + 3, DIM, Glatt.Schrift.NORMAL);
            return;
        }
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < liste.size(); i++) {
            Kachel k = liste.get(i);
            int kx = x + (i % spalten) * (kw + 6), ky = y + (i / spalten) * (kh + 6) - Math.round(scroll);
            if (ky + kh < y || ky > y + h) continue;
            boolean hov = mouseX >= kx && mouseX < kx + kw && mouseY >= ky && mouseY < ky + kh && mouseY >= y && mouseY < y + h;
            if (breit) breiteKachel(g, k, kx, ky, kw, kh, hov);
            else kachel(g, k, kx, ky, kw, kh, hov);
            int ob = Math.max(ky, y), un = Math.min(ky + kh, y + h);
            if (un > ob) flaechen.add(new Flaeche(kx, ob, kw, un - ob, () -> klick(k)));
        }
        g.disableScissor();
        if (max > 0) {
            int bh = Math.max(20, (int) (h * h / (float) (h + max)));
            int by = y + (int) ((h - bh) * (scroll / max));
            g.fill(x + w + 4, by, x + w + 6, by + bh, 0x40FFFFFF);
        }
    }

    private void breiteKachel(GuiGraphicsExtractor g, Kachel k, int x, int y, int w, int h, boolean hov) {
        boolean an = k.id().startsWith("@") && aktivesOutfit(k);
        Glatt.rund(g, x, y, w, h, 2, hov ? KACHEL_HOV : KACHEL);
        if (an) Glatt.rahmen(g, x, y, w, h, 2, 1, VortexAkzent());
        Glatt.text(g, Glatt.kuerzen(k.name(), w - 20, Glatt.Schrift.FETT), x + 8, y + 7, TEXT, Glatt.Schrift.FETT);
        Glatt.text(g, Glatt.kuerzen(k.unter(), w - 16, Glatt.Schrift.NORMAL), x + 8, y + 19, DIM, Glatt.Schrift.NORMAL);
        if (an) Glatt.symbol(g, Symbole.Symbol.HAKEN, x + w - 16, y + 11, 10, VortexAkzent());
    }

    private static boolean aktivesOutfit(Kachel k) {
        String n = k.id().startsWith("@slot:") ? k.id().substring(6) : k.id().startsWith("@outfit:") ? k.id().substring(8) : "";
        Outfits.Outfit o = outfit(n);
        return o != null && Outfits.traegt(o);
    }

    private void kachel(GuiGraphicsExtractor g, Kachel k, int x, int y, int w, int h, boolean hov) {
        Shop.Item it = istSonder(k.id()) ? null : Shop.item(k.kat(), k.id());
        boolean shop = bereich == Bereich.SHOP && Shop.aktiv();
        boolean besitzt = istSonder(k.id()) || Shop.besitzt(k.kat(), k.id());
        boolean an = traegt(k);
        boolean gewaehltHier = gewaehlt != null && gewaehlt.kat().equals(k.kat()) && gewaehlt.id().equals(k.id());
        Glatt.rund(g, x, y, w, h, 2, hov ? KACHEL_HOV : KACHEL);
        int bild = w - 12;
        g.fill(x + 6, y + 6, x + 6 + bild, y + 6 + bild, 0xFF141418);
        vorschauBild(g, k, x + 8, y + 8, bild - 4, bild - 4);
        if (shop && !besitzt) {
            g.fill(x + 6, y + 6, x + 6 + bild, y + 6 + bild, 0x55000000);
            schloss(g, x + 9, y + 9, it == null ? DIM : Shop.farbe(it.seltenheit()));
        }
        Glatt.text(g, Glatt.kuerzen(k.name(), w - 12, Glatt.Schrift.FETT), x + 6, y + bild + 9, an ? TEXT : 0xFFD8D8DE, Glatt.Schrift.FETT);
        int zy = y + bild + 19;
        if (it != null && shop) {
            if (besitzt) {
                Glatt.text(g, an ? "EQUIPPED" : "OWNED", x + 6, zy, an ? VortexAkzent() : 0xFF4ADE80, Glatt.Schrift.FETT);
            } else if (it.creatorOnly()) {
                Glatt.text(g, "CREATOR", x + 6, zy, GOLD, Glatt.Schrift.FETT);
            } else {
                Glatt.kreis(g, x + 10, zy + 4, 7, GOLD);
                Glatt.text(g, String.valueOf(it.preis()), x + 16, zy, 0xFFFDE68A, Glatt.Schrift.FETT);
            }
            String sel = Shop.seltenheitName(it.seltenheit()).toUpperCase(Locale.ROOT);
            Glatt.textRechts(g, Glatt.kuerzen(sel, w / 2, Glatt.Schrift.NORMAL), x + w - 6, zy, Shop.farbe(it.seltenheit()), Glatt.Schrift.NORMAL);
        } else {
            Glatt.text(g, Glatt.kuerzen(an ? "Equipped" : k.unter(), w - 12, Glatt.Schrift.NORMAL), x + 6, zy, an ? VortexAkzent() : DIM, Glatt.Schrift.NORMAL);
        }
        if (it != null) g.fill(x + 1, y + h - 2, x + w - 1, y + h, Shop.farbe(it.seltenheit()));
        if (an && !shop) Glatt.symbol(g, Symbole.Symbol.HAKEN, x + w - 17, y + 9, 10, VortexAkzent());
        if (gewaehltHier || (an && bereich == Bereich.WARDROBE)) Glatt.rahmen(g, x, y, w, h, 2, 1, gewaehltHier && it != null ? Shop.farbe(it.seltenheit()) : VortexAkzent());
    }

    /** Vorschaubild einer Kachel je Kategorie. */
    private void vorschauBild(GuiGraphicsExtractor g, Kachel k, int x, int y, int w, int h) {
        String id = k.id();
        if (id.isEmpty()) { Glatt.symbol(g, Symbole.Symbol.KREUZ, x + w / 2f - 8, y + h / 2f - 8, 16, LEISE); return; }
        if (id.startsWith("#")) {
            Symbole.Symbol sym = id.equals("#upload") ? Symbole.Symbol.PLUS : id.equals("#editor") ? Symbole.Symbol.REGLER : Symbole.Symbol.NEUSTART;
            Glatt.symbol(g, sym, x + w / 2f - 9, y + h / 2f - 9, 18, VortexAkzent());
            return;
        }
        switch (k.kat()) {
            case "cape" -> {
                int th = Math.min(h, Math.round(w * 1.6f)), tw = Math.round(th / 1.6f);
                int bx = x + (w - tw) / 2, by = y + (h - th) / 2;
                CapeKunst.Design d = CapeKunst.design(id);
                if (d != null) {
                    String mitStil = CapeKunst.mitStil(d.id(), stil());
                    var basis = AnimCapes.basis(mitStil);
                    var glow = AnimCapes.glow(mitStil);
                    if (basis != null) {
                        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, basis, bx, by, CapeKunst.S, CapeKunst.S, tw, th, 10 * CapeKunst.S, 16 * CapeKunst.S, CapeKunst.W, CapeKunst.H, 0xFFFFFFFF);
                        if (glow != null) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, glow, bx, by, CapeKunst.S, CapeKunst.S, tw, th, 10 * CapeKunst.S, 16 * CapeKunst.S, CapeKunst.W, CapeKunst.H, 0xFFFFFFFF);
                    }
                } else {
                    Glatt.rund(g, bx, by, tw, th, 2, 0xFF22222A);
                    Glatt.symbol(g, Symbole.Symbol.HEMD, x + w / 2f - 8, y + h / 2f - 8, 16, DIM);
                }
            }
            case "nametag" -> {
                String n = this.minecraft.player != null ? this.minecraft.player.getGameProfile().name() : "Vortex";
                float b = Glatt.breite(n, Glatt.Schrift.FETT);
                namensVorschau(g, n, id, x + w / 2f - b / 2f, y + h / 2f - 4);
            }
            default -> {
                Zubehoer.Kategorie z = zKat(k.kat());
                Zubehoer.Design d = z == null ? null : Zubehoer.get(z, id);
                if (d == null) return;
                if (z == Zubehoer.Kategorie.SHIELD) {
                    int sh = h, sw = Math.round(sh * 24f / 44f);
                    var tex = SchildSkins.texturFuer(d.id());
                    if (tex != null) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex, x + (w - sw) / 2, y, 4, 4, sw, sh, 48, 88, 256, 256, 0xFFFFFFFF);
                } else {
                    pixelBild(g, vorschauBild(d), x + 2, y + 2, w - 4, h - 4);
                }
            }
        }
    }

    /* ------------------------------------------------------------------ Rechts: Vorschau */

    private void vorschauSpalte(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = vx + 1, w = vw - 2;
        int infoH = infoHoehe();
        int ph = vh - infoH;
        g.fill(x, vy, x + w, vy + ph, 0xFF0C0C0F);
        vorschau(g, x, vy, w, ph);
        int rb = (int) Glatt.breite("Reset", Glatt.Schrift.NORMAL) + 12;
        boolean rh = mouseX >= x + w - rb - 6 && mouseX < x + w - 6 && mouseY >= vy + 6 && mouseY < vy + 20;
        Glatt.rund(g, x + w - rb - 6, vy + 6, rb, 14, 2, rh ? KACHEL_HOV : KACHEL);
        Glatt.textMitte(g, "Reset", x + w - rb / 2f - 6, vy + 9, rh ? TEXT : DIM, Glatt.Schrift.NORMAL);
        flaechen.add(new Flaeche(x + w - rb - 6, vy + 6, rb, 14, () -> { drehung = 160f; neigung = 0f; zoom = 1f; }));
        g.fill(x, vy + ph, x + w, vy + ph + 1, LINIE);
        info(g, x, vy + ph + 1, w, infoH - 1, mouseX, mouseY);
    }

    private int infoHoehe() {
        return gewaehlt != null ? 92 : 78;
    }

    private void info(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, LEISTE);
        if (gewaehlt == null) { loadout(g, x, y, w, mouseX, mouseY); return; }
        Kachel k = gewaehlt;
        Shop.Item it = istSonder(k.id()) ? null : Shop.item(k.kat(), k.id());
        int px = x + 10, py = y + 8;
        Glatt.text(g, Glatt.kuerzen(k.name(), w - 20, Glatt.Schrift.FETT), px, py, TEXT, Glatt.Schrift.FETT);
        py += 12;
        if (it != null) {
            String z = Shop.seltenheitName(it.seltenheit()) + (it.limitiert() ? "  ·  Limited" : "") + (it.creatorOnly() ? "  ·  Creator" : "");
            Glatt.text(g, z, px, py, Shop.farbe(it.seltenheit()), Glatt.Schrift.NORMAL);
            py += 11;
            String b = it.beschreibung().isEmpty() ? k.unter() : it.beschreibung();
            if (b != null && !b.isEmpty()) Glatt.text(g, Glatt.kuerzen(b, w - 20, Glatt.Schrift.NORMAL), px, py, DIM, Glatt.Schrift.NORMAL);
            if (it.bis() > 0 && it.imVerkauf()) {
                long tage = Math.max(0, (it.bis() - System.currentTimeMillis()) / 86400000L);
                Glatt.textRechts(g, tage <= 0 ? "Ends today" : "Ends in " + tage + "d", x + w - 10, y + 8, 0xFFFB923C, Glatt.Schrift.NORMAL);
            }
        } else if (k.unter() != null && !k.unter().isEmpty()) {
            Glatt.text(g, Glatt.kuerzen(k.unter(), w - 20, Glatt.Schrift.NORMAL), px, py, DIM, Glatt.Schrift.NORMAL);
        }
        int by = y + h - 26;
        int bx = x + 10;
        boolean besitzt = it == null || Shop.besitzt(k.kat(), k.id());
        boolean an = traegt(k);
        if (bereich == Bereich.SHOP && Shop.aktiv() && it != null && !besitzt) {
            if (it.creatorOnly() && !Shop.creator()) {
                bx = knopf(g, bx, by, "Creator program", true, false, mouseX, mouseY, () -> this.minecraft.gui.setScreen(new CreatorScreen(this)));
            } else if (!it.imVerkauf()) {
                Glatt.text(g, "Not for sale right now", bx, by + 6, DIM, Glatt.Schrift.NORMAL);
            } else {
                String kauf = kauftGerade ? "..." : it.preis() > 0 ? "Buy  " + it.preis() : "Unlock";
                bx = knopf(g, bx, by, kauf, Shop.guthaben() >= it.preis(), true, mouseX, mouseY, () -> kaufen(k));
                if (it.verschenkbar() && it.preis() > 0) bx = knopf(g, bx + 4, by, "Gift", true, false, mouseX, mouseY, () -> verschenken(k));
            }
        } else if (an) {
            bx = knopf(g, bx, by, "Take off", true, false, mouseX, mouseY, () -> ablegen(k.kat()));
        } else if (!istSonder(k.id()) || k.id().isEmpty()) {
            bx = knopf(g, bx, by, "Equip", true, true, mouseX, mouseY, () -> { anprobeEnde(); anlegen(k); });
            if (bereich == Bereich.SHOP && it != null && it.verschenkbar() && it.preis() > 0 && it.imVerkauf())
                bx = knopf(g, bx + 4, by, "Gift", true, false, mouseX, mouseY, () -> verschenken(k));
        }
        knopf(g, x + w - 10 - 22, by, "x", true, false, mouseX, mouseY, () -> { gewaehlt = null; anprobeEnde(); });
    }

    /** Ohne Auswahl: was du gerade traegst, mit x zum Ablegen. */
    private void loadout(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
        Glatt.text(g, "Current loadout", x + 10, y + 8, TEXT, Glatt.Schrift.FETT);
        Cosmetics.Auswahl a = Cosmetics.eigene();
        int cx = x + 10, cy = y + 22;
        boolean leer = true;
        for (String kat : ALLE_KATS) {
            String id = basis(kat, getragen(a, kat));
            if (id.isEmpty()) continue;
            leer = false;
            String name = name(kat, id);
            int bw = (int) Glatt.breite(name, Glatt.Schrift.NORMAL) + 22;
            if (cx > x + 10 && cx + bw > x + w - 10) { cx = x + 10; cy += 17; }
            if (cy > y + 70) break;
            boolean hov = mouseX >= cx && mouseX < cx + bw && mouseY >= cy && mouseY < cy + 14;
            Glatt.rund(g, cx, cy, bw, 14, 2, hov ? KACHEL_HOV : FELD);
            Glatt.text(g, name, cx + 6, cy + 3, TEXT, Glatt.Schrift.NORMAL);
            Glatt.text(g, "x", cx + bw - 11, cy + 3, hov ? 0xFFF87171 : LEISE, Glatt.Schrift.NORMAL);
            final String kk = kat;
            flaechen.add(new Flaeche(cx, cy, bw, 14, () -> ablegen(kk)));
            cx += bw + 3;
        }
        if (leer) Glatt.text(g, "Nothing equipped. Pick something in the Wardrobe.", x + 10, cy + 2, DIM, Glatt.Schrift.NORMAL);
    }

    private String name(String kat, String id) {
        Shop.Item it = Shop.item(kat, id);
        if (it != null) return it.name();
        for (Kachel k : alleIn(kat)) if (k.id().equals(id)) return k.name();
        if (EigenesCape.ID.equals(id)) return "Your picture";
        return id;
    }

    private int knopf(GuiGraphicsExtractor g, int x, int y, String text, boolean aktiv, boolean haupt, int mouseX, int mouseY, Runnable aktion) {
        int w = Math.max(22, (int) Glatt.breite(text, Glatt.Schrift.NORMAL) + 18);
        boolean hov = aktiv && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
        int grund = !aktiv ? 0xFF1A1A1E : haupt ? VortexAkzent() : FELD;
        if (hov) grund = Glatt.mix(grund, 0xFFFFFFFF, 0.1f);
        Glatt.rund(g, x, y, w, 18, 2, grund);
        if (!haupt) Glatt.rahmen(g, x, y, w, 18, 2, 1, LINIE);
        Glatt.textMitte(g, text, x + w / 2f, y + 5, aktiv ? TEXT : LEISE, Glatt.Schrift.NORMAL);
        if (aktiv) flaechen.add(new Flaeche(x, y, w, 18, aktion));
        return x + w;
    }

    /** Eigener Spieler, frei drehbar und zoombar. Ohne Welt (Hauptmenue) das Cape als Bild. */
    private void vorschau(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Player p = this.minecraft.player;
        String cape = Cosmetics.getragen().cape();
        CapeKunst.Design gross = CapeKunst.design(cape);
        if (p == null) {
            if (gross != null) {
                int th = Math.min(h - 50, Math.round((w - 60) * 1.6f)), tw = Math.round(th / 1.6f);
                int bx = x + (w - tw) / 2, by = y + 26;
                var basis = AnimCapes.basis(cape);
                var glow = AnimCapes.glow(cape);
                if (basis != null) {
                    g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, basis, bx, by, CapeKunst.S, CapeKunst.S, tw, th, 10 * CapeKunst.S, 16 * CapeKunst.S, CapeKunst.W, CapeKunst.H, 0xFFFFFFFF);
                    if (glow != null) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, glow, bx, by, CapeKunst.S, CapeKunst.S, tw, th, 10 * CapeKunst.S, 16 * CapeKunst.S, CapeKunst.W, CapeKunst.H, 0xFFFFFFFF);
                }
            }
            Glatt.textMitte(g, "Join a world for the 3D preview", x + w / 2f, y + h - 16, DIM, Glatt.Schrift.NORMAL);
            return;
        }
        Glatt.textMitte(g, "Drag to turn  ·  scroll to zoom", x + w / 2f, y + h - 13, LEISE, Glatt.Schrift.NORMAL);
        String nstil = Cosmetics.getragen().namensstil();
        String nm = p.getGameProfile().name();
        Component abz = Abzeichen.prefix(p.getUUID());
        float ny = y + 10;
        if (abz != null) {
            Glatt.textMitte(g, abz.getString().trim(), x + w / 2f, ny, Abzeichen.von(p.getUUID()).equals("creator") ? GOLD : 0xFF8B5CF6, Glatt.Schrift.FETT);
            ny += 11;
        }
        if (!nstil.isEmpty() && NamensEffekt.nummer(nstil) >= 0) namensVorschau(g, nm, nstil, x + w / 2f - Glatt.breite(nm, Glatt.Schrift.FETT) / 2f, ny);
        else Glatt.textMitte(g, nm, x + w / 2f, ny, TEXT, Glatt.Schrift.FETT);
        float bodyRot = p.yBodyRot, bodyRotO = p.yBodyRotO, yRot = p.getYRot(), yRotO = p.yRotO, xRot = p.getXRot(), xRotO = p.xRotO, head = p.yHeadRot, headO = p.yHeadRotO;
        try {
            p.yBodyRot = p.yBodyRotO = 180f;
            p.setYRot(180f); p.yRotO = 180f;
            p.setXRot(0f); p.xRotO = 0f;
            p.yHeadRot = p.yHeadRotO = 180f;
            var state = this.minecraft.getEntityRenderDispatcher().extractEntity(p, 1f);
            state.lightCoords = 0xF000F0;
            state.shadowPieces.clear();
            state.outlineColor = 0;
            state.nameTag = null;
            Quaternionf drehen = new Quaternionf().rotateZ((float) Math.PI)
                    .mul(new Quaternionf().rotateX((float) Math.toRadians(neigung)))
                    .mul(new Quaternionf().rotateY((float) Math.toRadians(drehung - 180f)));
            float groesse = Math.min(w * 0.9f, h - 40) * 0.40f * zoom;
            Vector3f mitte = new Vector3f(0, p.getBbHeight() / 2f + 0.06f, 0);
            //#if 26
            g.entity(state, groesse, mitte, drehen, null, x, y + 20, x + w, y + h - 18);
            //#else
            //$ g.submitEntityRenderState(state, groesse, mitte, drehen, null, x, y + 20, x + w, y + h - 18);
            //#endif
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("CosmeticsScreen.vorschau", t);
        } finally {
            p.yBodyRot = bodyRot; p.yBodyRotO = bodyRotO; p.setYRot(yRot); p.yRotO = yRotO;
            p.setXRot(xRot); p.xRotO = xRotO; p.yHeadRot = head; p.yHeadRotO = headO;
        }
    }

    /* ------------------------------------------------------------------ Stil, Glitzern (wie bisher) */

    private static CapeKunst.Stil letzterStil = CapeKunst.Stil.STANDARD;

    private static CapeKunst.Stil stil() {
        String c = Cosmetics.eigene().cape();
        if (CapeKunst.design(c) != null) letzterStil = CapeKunst.Stil.aus(c);
        return letzterStil;
    }

    private static void setzeStil(CapeKunst.Stil s) {
        letzterStil = s;
        Cosmetics.Auswahl a = Cosmetics.eigene();
        if (CapeKunst.design(a.cape()) != null) Cosmetics.speichern(a.mitCape(CapeKunst.mitStil(a.cape(), s)), Cosmetics.partikelErstePerson());
    }

    private static final String[] FARBTOENE = { "Original", "Shift 1", "Shift 2", "Shift 3", "Shift 4", "Shift 5", "Shift 6",
            "Shift 7", "Shift 8", "Shift 9", "Shift 10", "Shift 11" };

    private static final java.util.Map<String, Boolean> LETZTES_GLITZERN = new java.util.HashMap<>();

    private static boolean glitzern(String kat) {
        String id = getragen(Cosmetics.eigene(), kat);
        if (!id.isEmpty()) LETZTES_GLITZERN.put(kat, Zubehoer.glitzert(id));
        return LETZTES_GLITZERN.getOrDefault(kat, false);
    }

    private void setzeGlitzern(String kat, boolean an) {
        LETZTES_GLITZERN.put(kat, an);
        String id = getragen(Cosmetics.eigene(), kat);
        if (id.isEmpty()) return;
        Cosmetics.Auswahl a = Cosmetics.eigene();
        String neu = Zubehoer.mitGlitzern(id, an);
        Cosmetics.Auswahl b = switch (kat) {
            case "hat" -> a.mitHut(neu); case "bandana" -> a.mitBandana(neu); case "face" -> a.mitGesicht(neu); case "neck" -> a.mitHals(neu);
            case "back" -> a.mitRuecken(neu); case "shield" -> a.mitSchild(neu); default -> a;
        };
        Cosmetics.speichern(b, Cosmetics.partikelErstePerson());
    }

    /* ------------------------------------------------------------------ Bilder (wie bisher) */

    private static void schloss(GuiGraphicsExtractor g, float x, float y, int farbe) {
        Glatt.rahmen(g, x + 1.5f, y, 6, 7, 3, 1, 0xFFE8E4F4);
        Glatt.rund(g, x, y + 4, 9, 7, 1.5f, 0xFFE8E4F4);
        Glatt.kreis(g, x + 4.5f, y + 7.3f, 2.2f, Glatt.alpha(farbe, 1f));
    }

    private static void namensVorschau(GuiGraphicsExtractor g, String text, String stil, float x, float y) {
        com.vortex.client.gui.Farbthema.rohAn();
        try { namensVorschauRoh(g, text, stil, x, y); } finally { com.vortex.client.gui.Farbthema.rohAus(); }
    }

    private static void namensVorschauRoh(GuiGraphicsExtractor g, String text, String stil, float x, float y) {
        float t = NamensEffekt.zeit();
        int[] cps = text.codePoints().toArray();
        float cx = x;
        for (int i = 0; i < cps.length; i++) {
            String z = new String(Character.toChars(cps[i]));
            float dy = switch (stil) {
                case "waving" -> (float) Math.sin(t * 0.28f - i * 0.65f) * 1.5f;
                case "shaking" -> ((((i * 31 + (int) (t / 1.5f) * 7) * 0x45d9f3b) >>> 20) & 3) - 1.5f;
                case "growing", "iterating" -> -Math.max(0f, (float) Math.sin(t * 0.16f - i * 0.55f)) * 1.5f;
                default -> 0f;
            };
            int farbe = 0xFF000000 | NamensEffekt.farbe(stil, i, cps.length, t);
            if (stil.equals("blinking") && ((int) (t / 10f)) % 2 == 1) farbe = 0xFF5D5772;
            Glatt.text(g, z, cx, y + dy, farbe, Glatt.Schrift.FETT);
            cx += Glatt.breite(z, Glatt.Schrift.FETT);
        }
    }

    /** Bild fuer die Karte: Bandana = Band vorne, Aura = erstes Sprite, sonst das groesste Teil. */
    static int[][] vorschauBild(Zubehoer.Design d) {
        for (Zubehoer.Teil t : d.teile()) {
            if (t.anim().startsWith("icon")) return t.farben();
            if (t.anim().startsWith("wing")) {
                int[][] f = t.farben();
                int h = f.length, w = 0;
                for (int[] r : f) w = Math.max(w, r.length);
                int[][] o = new int[h][2 * w + 2];
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < f[y].length; x++) { o[y][w + 2 + x] = f[y][x]; o[y][w - 1 - x] = f[y][x]; }
                return o;
            }
        }
        if (d.ring() != null && d.teile().isEmpty()) {
            int[][] r = d.ring().farben();
            int[][] o = new int[r.length * 2][];
            for (int y = 0; y < r.length; y++) {
                o[y * 2] = java.util.Arrays.copyOfRange(r[y], 0, Math.max(1, r[y].length / 4));
                o[y * 2 + 1] = o[y * 2];
            }
            return o;
        }
        if (d.aura() != null && !d.aura().sprites().isEmpty()) return d.aura().sprites().get(0).farben();
        Zubehoer.Teil best = null;
        int groesse = -1;
        for (Zubehoer.Teil t : d.teile()) {
            int n = 0;
            for (int[] z : t.farben()) n += z.length;
            if (n > groesse) { groesse = n; best = t; }
        }
        if (best == null) return new int[0][];
        return zusammensetzen(d, best);
    }

    /** Ungedrehte Teile gleicher Groesse wie das Hauptteil zu einem Bild zusammenlegen (z. B. Teddy: Kopf + Koerper). */
    private static int[][] zusammensetzen(Zubehoer.Design d, Zubehoer.Teil haupt) {
        if (haupt.rot()[0] != 0 || haupt.rot()[1] != 0 || haupt.rot()[2] != 0) return haupt.farben();
        List<Zubehoer.Teil> teile = new ArrayList<>();
        for (Zubehoer.Teil t : d.teile()) {
            String an = t.anim();
            if (t.px() != haupt.px() || t.rot()[0] != 0 || t.rot()[1] != 0 || t.rot()[2] != 0) continue;
            if (an.startsWith("strap") || an.startsWith("glint") || an.startsWith("scan") || an.startsWith("twinkle")) continue;
            teile.add(t);
        }
        if (teile.size() < 2) return haupt.farben();
        teile.sort((a, b) -> Float.compare(a.at()[2], b.at()[2]));
        if (d.kat() == Zubehoer.Kategorie.FACE) java.util.Collections.reverse(teile);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float px = haupt.px();
        for (Zubehoer.Teil t : teile) {
            int w = 0;
            for (int[] z : t.farben()) w = Math.max(w, z.length);
            float cx = t.at()[0] / px, cy = -t.at()[1] / px;
            minX = Math.min(minX, cx - w / 2f); maxX = Math.max(maxX, cx + w / 2f);
            minY = Math.min(minY, cy - t.farben().length / 2f); maxY = Math.max(maxY, cy + t.farben().length / 2f);
        }
        int bw = Math.round(maxX - minX), bh = Math.round(maxY - minY);
        if (bw <= 0 || bh <= 0 || bw > 128 || bh > 128) return haupt.farben();
        int[][] o = new int[bh][bw];
        for (Zubehoer.Teil t : teile) {
            int w = 0;
            for (int[] z : t.farben()) w = Math.max(w, z.length);
            int ox = Math.round(t.at()[0] / px - w / 2f - minX), oy = Math.round(-t.at()[1] / px - t.farben().length / 2f - minY);
            for (int y = 0; y < t.farben().length; y++)
                for (int x = 0; x < t.farben()[y].length; x++) {
                    int f = t.farben()[y][x];
                    int yy = oy + y, xx = ox + x;
                    if ((f >>> 24) != 0 && yy >= 0 && yy < bh && xx >= 0 && xx < bw) o[yy][xx] = f;
                }
        }
        return o;
    }

    private static void pixelBild(GuiGraphicsExtractor g, int[][] bild, int x, int y, int w, int h) {
        int bh = bild.length, bw = 0;
        for (int[] z : bild) bw = Math.max(bw, z.length);
        if (bh == 0 || bw == 0) return;
        float s = Math.min(w / (float) bw, h / (float) bh);
        float ox = x + (w - bw * s) / 2f, oy = y + (h - bh * s) / 2f;
        for (int yy = 0; yy < bh; yy++) {
            for (int xx = 0; xx < bild[yy].length; xx++) {
                int f = bild[yy][xx];
                if ((f >>> 24) == 0) continue;
                int x0 = Math.round(ox + xx * s), y0 = Math.round(oy + yy * s), x1 = Math.round(ox + (xx + 1) * s), y1 = Math.round(oy + (yy + 1) * s);
                if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, f);
            }
        }
    }

    /* ------------------------------------------------------------------ Eingaben */

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (tasteWarten) {
            tasteWarten = false;
            if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { meldung = null; return true; }
            com.vortex.client.VortexClientMod.setzeEmoteTaste(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(e.key()));
            melden("The emote wheel opens with " + emoteTaste() + " now.");
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (sucheFeld != null && sucheFeld.isFocused() && !sucheFeld.isMouseOver(click.x(), click.y())) sucheFeld.setFocused(false);
        if (click.button() == 1) {
            for (int i = flaechen.size() - 1; i >= 0; i--) {
                if (flaechen.get(i).in(click.x(), click.y())) {
                    rechtsKlick = true;
                    try { if (reiter == Reiter.OUTFITS) flaechen.get(i).aktion().run(); } finally { rechtsKlick = false; }
                    return true;
                }
            }
            return false;
        }
        if (click.button() != 0) return false;
        for (int i = flaechen.size() - 1; i >= 0; i--) {
            if (flaechen.get(i).in(click.x(), click.y())) { flaechen.get(i).aktion().run(); return true; }
        }
        zieht = click.x() >= vx && click.x() < vx + vw && click.y() >= vy && click.y() < vy + vh - infoHoehe();
        return zieht;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (!zieht) return super.mouseDragged(event, dx, dy);
        drehung = (drehung + (float) dx * 2.2f) % 360f;
        neigung = Math.max(-30f, Math.min(30f, neigung - (float) dy * 1.2f));
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        zieht = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= vx && mouseX < vx + vw && mouseY >= vy && mouseY < vy + vh - infoHoehe()) {
            zoom = Math.max(0.6f, Math.min(2.2f, zoom + (float) vertical * 0.12f));
            return true;
        }
        scroll -= (float) vertical * 30f;
        return true;
    }

    /** Vorschau auf einen Winkel drehen (Grad; 160 = schraeg von vorne). Auch fuer den Cosmetics-Test. */
    public void vorschauDrehen(float grad, float neigen) {
        drehung = grad;
        neigung = neigen;
    }

    /** Kategorie waehlen (0 = Capes, 1 = Hats, 2 = Bandanas, 3 = Face, 4 = Back, 5 = Auras, ...). Auch fuer den Cosmetics-Test. */
    public void zeigeReiter(int i) {
        Reiter[] alt = { Reiter.CAPES, Reiter.HATS, Reiter.BANDANAS, Reiter.FACE, Reiter.BACK, Reiter.AURAS, Reiter.SHIELDS, Reiter.EMOTES };
        bereich = Bereich.WARDROBE;
        reiter = alt[Math.max(0, Math.min(alt.length - 1, i))];
        scroll = 0;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        anprobeEnde();
        this.minecraft.gui.setScreen(parent);
    }
}
