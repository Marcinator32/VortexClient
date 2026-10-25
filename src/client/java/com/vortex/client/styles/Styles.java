package com.vortex.client.styles;

import com.vortex.client.core.ConfigManager;
import com.vortex.client.core.Errors;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Styles: fuer jedes Teil (Kristall, Anker, Schwert, Deepslate ...) ein
 * eigenes Aussehen waehlen und frei mischen.
 *
 * Zur Wahl stehen:
 *  - "Vanilla" (nichts aendern),
 *  - die eingebauten Farben ({@link Farbe}) -- aus Minecrafts eigenen
 *    Texturen berechnet, nichts aus dem Netz, kein fremdes Bild,
 *  - importierte Styles: aus Resource Packs (.zip) oder Mods/Sammlungen mit
 *    mehreren Packs (.jar/.zip mit Unterordnern), die man selbst auf dem PC
 *    hat. Beim Import wird jedes Pack nach Kategorien zerlegt; abgelegt wird
 *    nur, was zu einer Kategorie gehoert (Texturen und deren .mcmeta).
 *
 * Anwenden: Aus der Auswahl wird der Ordner "resourcepacks/Vortex Styles"
 * gebaut und als oberstes Resource Pack eingeschaltet. Dadurch funktioniert
 * es in jeder Minecraft-Fassung gleich, ohne Eingriff in den Lader, und man
 * sieht und schaltet es auch im normalen Resource-Pack-Menue.
 *
 * Keine Netzwerkzugriffe, kein Ausfuehren fremden Codes: aus importierten
 * Dateien werden nur Bilddaten gelesen.
 */
public final class Styles {
    private Styles() {}

    public static final String PACK_ORDNER = "Vortex Styles";
    private static final String PACK_ID = "file/" + PACK_ORDNER;
    private static final long MAX_DATEI = 16L * 1024 * 1024;
    private static final long MAX_IMPORT = 400L * 1024 * 1024;

    /** Ein waehlbarer Style. id: "gen:<farbe>" oder "imp:<ordnername>". */
    public record Stil(Kategorie kat, String id, String name, Farbe farbe, Path ordner) {
        public boolean importiert() { return ordner != null; }
    }

    private static final Map<Kategorie, String> AUSWAHL = new EnumMap<>(Kategorie.class);
    /** Effekte (seit 4.66): je Teil Gruppe -> Option, nur was nicht Standard ist. In selection.txt als "fire.height=low". */
    private static final Map<Kategorie, Map<String, String>> EFFEKTE = new EnumMap<>(Kategorie.class);
    private static boolean geladen;
    private static final Map<String, List<String>> VANILLA_LISTE = new HashMap<>();

    public static Path ordner() {
        String test = System.getProperty("vortex.styles.dir", "");
        if (!test.isEmpty()) return Path.of(test);
        return ConfigManager.dataDir().resolve("styles");
    }

    private static Path auswahlDatei() {
        return ordner().resolve("selection.txt");
    }

    private static synchronized void laden() {
        if (geladen) return;
        geladen = true;
        try {
            Path d = auswahlDatei();
            if (!Files.exists(d)) return;
            for (String z : Files.readAllLines(d, StandardCharsets.UTF_8)) {
                int i = z.indexOf('=');
                if (i <= 0) continue;
                String schluessel = z.substring(0, i).trim();
                String v = z.substring(i + 1).trim();
                int punkt = schluessel.indexOf('.');
                if (punkt > 0) {
                    Kategorie k = Kategorie.vonId(schluessel.substring(0, punkt));
                    Effekte.Gruppe g = k == null ? null : Effekte.gruppe(k, schluessel.substring(punkt + 1));
                    if (g != null && g.option(v) != null && !g.standard().id().equals(v))
                        EFFEKTE.computeIfAbsent(k, x -> new LinkedHashMap<>()).put(g.id(), v);
                    continue;
                }
                Kategorie k = Kategorie.vonId(schluessel);
                if (k != null && !v.isEmpty()) AUSWAHL.put(k, v);
            }
        } catch (Throwable t) {
            Errors.report("Styles.laden", t);
        }
    }

    private static synchronized void speichern() {
        try {
            Files.createDirectories(ordner());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Kategorie, String> e : AUSWAHL.entrySet()) sb.append(e.getKey().id).append('=').append(e.getValue()).append('\n');
            for (Map.Entry<Kategorie, Map<String, String>> e : EFFEKTE.entrySet())
                for (Map.Entry<String, String> g : e.getValue().entrySet())
                    sb.append(e.getKey().id).append('.').append(g.getKey()).append('=').append(g.getValue()).append('\n');
            Files.writeString(auswahlDatei(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            Errors.report("Styles.speichern", t);
        }
    }

    /** Gewaehlter Style einer Kategorie (null = Vanilla). */
    public static synchronized String gewaehlt(Kategorie k) {
        laden();
        return AUSWAHL.get(k);
    }

    public static synchronized Map<Kategorie, String> auswahl() {
        laden();
        return new EnumMap<>(AUSWAHL);
    }

    /** id null = Vanilla. */
    public static synchronized void waehle(Kategorie k, String id) {
        laden();
        if (id == null) AUSWAHL.remove(k);
        else AUSWAHL.put(k, id);
        speichern();
    }

    public static synchronized void allesZuruecksetzen() {
        laden();
        AUSWAHL.clear();
        EFFEKTE.clear();
        speichern();
    }

    /** Gewaehlte Option einer Effekt-Gruppe (Standard, wenn nichts gewaehlt). */
    public static synchronized String effekt(Kategorie k, String gruppe) {
        laden();
        Map<String, String> m = EFFEKTE.get(k);
        String v = m == null ? null : m.get(gruppe);
        if (v != null) return v;
        Effekte.Gruppe g = Effekte.gruppe(k, gruppe);
        return g == null ? null : g.standard().id();
    }

    /** Alle Effekte eines Teils (nur die vom Standard abweichenden). */
    public static synchronized Map<String, String> effekte(Kategorie k) {
        laden();
        Map<String, String> m = EFFEKTE.get(k);
        return m == null ? Map.of() : new LinkedHashMap<>(m);
    }

    public static synchronized void setzeEffekt(Kategorie k, String gruppe, String option) {
        laden();
        Effekte.Gruppe g = Effekte.gruppe(k, gruppe);
        if (g == null) return;
        if (option == null || g.standard().id().equals(option)) {
            Map<String, String> m = EFFEKTE.get(k);
            if (m != null) { m.remove(gruppe); if (m.isEmpty()) EFFEKTE.remove(k); }
        } else if (g.option(option) != null) {
            EFFEKTE.computeIfAbsent(k, x -> new LinkedHashMap<>()).put(gruppe, option);
        }
        speichern();
    }

    /** Ist an diesem Teil irgendetwas geaendert (Farbe, Pack oder Effekt)? */
    public static synchronized boolean geaendert(Kategorie k) {
        laden();
        return AUSWAHL.containsKey(k) || EFFEKTE.containsKey(k);
    }

    /** Gesamter Stand als Text (zum Vergleichen: hat sich seit dem Oeffnen etwas geaendert?). */
    public static synchronized String stand() {
        laden();
        return AUSWAHL + "|" + EFFEKTE;
    }

    /** Alle Styles einer Kategorie: zuerst die eingebauten Farben, dann die importierten. */
    public static List<Stil> stile(Kategorie k) {
        List<Stil> l = new ArrayList<>();
        for (Farbe f : Farbe.ALLE) l.add(new Stil(k, "gen:" + f.id(), f.name(), f, null));
        Path d = ordner().resolve(k.id);
        if (Files.isDirectory(d)) {
            List<Stil> imp = new ArrayList<>();
            try (Stream<Path> s = Files.list(d)) {
                s.filter(Files::isDirectory).forEach(p -> {
                    String name = p.getFileName().toString();
                    try {
                        Path n = p.resolve("name.txt");
                        if (Files.exists(n)) name = Files.readString(n, StandardCharsets.UTF_8).trim();
                    } catch (IOException ignored) { }
                    imp.add(new Stil(k, "imp:" + p.getFileName(), name, null, p));
                });
            } catch (IOException e) {
                Errors.report("Styles.stile", e);
            }
            imp.sort(Comparator.comparing(st -> st.name().toLowerCase(Locale.ROOT)));
            l.addAll(imp);
        }
        return l;
    }

    public static int anzahlImportiert() {
        int n = 0;
        for (Kategorie k : Kategorie.values()) {
            Path d = ordner().resolve(k.id);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> s = Files.list(d)) {
                n += (int) s.filter(Files::isDirectory).count();
            } catch (IOException ignored) { }
        }
        return n;
    }

    private static byte[] vanillaBytes(String pfad) {
        try {
            IoSupplier<InputStream> sup = Minecraft.getInstance().getVanillaPackResources()
                    .getResource(PackType.CLIENT_RESOURCES, Identifier.withDefaultNamespace(pfad));
            if (sup == null) return null;
            try (InputStream in = sup.get()) {
                return in.readAllBytes();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    static BufferedImage vanilla(String pfad) {
        byte[] b = vanillaBytes(pfad);
        return b == null ? null : bild(b);
    }

    /** Alle Pfade (unter assets/minecraft/) in einem Ordner von Minecrafts Texturen, mit Unterordnern. */
    private static synchronized List<String> vanillaListe(String ordner) {
        List<String> l = VANILLA_LISTE.get(ordner);
        if (l != null) return l;
        List<String> neu = new ArrayList<>();
        try {
            Minecraft.getInstance().getVanillaPackResources().listResources(PackType.CLIENT_RESOURCES, "minecraft", ordner,
                    (id, sup) -> neu.add(id.getPath()));
        } catch (Throwable t) {
            Errors.report("Styles.vanillaListe", t);
        }
        VANILLA_LISTE.put(ordner, neu);
        return neu;
    }

    private static BufferedImage bild(byte[] b) {
        try {
            BufferedImage i = ImageIO.read(new ByteArrayInputStream(b));
            if (i == null) return null;
            if (i.getType() == BufferedImage.TYPE_INT_ARGB) return i;
            BufferedImage a = new BufferedImage(i.getWidth(), i.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = a.createGraphics();
            g.drawImage(i, 0, 0, null);
            g.dispose();
            return a;
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage bild(Path p) {
        try {
            if (!Files.isRegularFile(p) || Files.size(p) > MAX_DATEI) return null;
            return bild(Files.readAllBytes(p));
        } catch (IOException e) {
            return null;
        }
    }

    public static byte[] png(BufferedImage i) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(i, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /** Vorschaubild eines Styles (null = Vanilla). Ergebnis hoechstens 64 Pixel gross, pixelgenau. */
    public static BufferedImage vorschau(Kategorie k, Stil s) {
        BufferedImage roh = null;
        String pfad = null;
        if (s != null && s.importiert()) {
            BufferedImage eigen = bild(s.ordner().resolve("preview.png"));
            if (eigen != null) return skalieren(eigen, 64);
            Path assets = s.ordner().resolve("assets/minecraft");
            for (String p : k.vorschau) {
                roh = bild(assets.resolve(p));
                if (roh != null) { pfad = p; break; }
            }
            if (roh == null) {
                try (Stream<Path> w = Files.walk(assets)) {
                    Path erstes = w.filter(p -> p.toString().endsWith(".png") && !p.toString().contains("colormap")).sorted().findFirst().orElse(null);
                    if (erstes != null) { roh = bild(erstes); pfad = assets.relativize(erstes).toString().replace('\\', '/'); }
                } catch (IOException ignored) { }
            }
            if (k == Kategorie.GRASS) {
                BufferedImage karte = bild(assets.resolve("textures/colormap/grass.png"));
                if (roh == null) roh = vanilla("textures/block/grass_block_top.png");
                return skalieren(toenen(roh, karte != null ? mitte(karte) : 0x91BD59), 64);
            }
        } else {
            if (k.leiste()) return skalieren(leistenVorschau(k, s == null ? null : s.farbe(), null), 64);
            for (String p : k.vorschau) {
                roh = vanilla(p);
                if (roh != null) { pfad = p; break; }
            }
            if (k == Kategorie.GRASS) {
                int c = s == null ? 0x91BD59 : s.farbe().bei(0.5f);
                return skalieren(toenen(roh, c), 64);
            }
            if (roh != null && s != null && s.farbe() != null) roh = s.farbe().faerben(roh, k.modus);
        }
        if (roh == null) return null;
        return skalieren(zuschneiden(roh, pfad), 64);
    }

    /**
     * Vorschau einer Effekt-Option: das Teil mit der gewaehlten Farbe bzw. dem Pack (falls
     * eins gewaehlt ist) und genau diesem Effekt.
     */
    public static BufferedImage vorschauEffekt(Kategorie k, String gruppe, String option) {
        Map<String, String> nur = new LinkedHashMap<>();
        nur.put(gruppe, option);
        if (gruppe.equals("number")) {
            Map<String, String> sonst = new LinkedHashMap<>(effekte(k));
            sonst.remove("number");
            BufferedImage basis = sonst.isEmpty() ? vorschau(k, stilVon(k)) : vorschauEffekt(k, sonst.keySet().iterator().next(), sonst.values().iterator().next());
            if (basis == null || option.equals("off")) return basis;
            String text = switch (option) {
                case "percent" -> "45%";
                case "points" -> "12";
                default -> "20";
            };
            return mitZahl(basis, text);
        }
        if (gruppe.equals("pop")) {
            BufferedImage t = vanilla("textures/item/totem_of_undying.png");
            if (t == null) return null;
            Float f = Effekte.totemGroesse(nur);
            float s = f == null ? 1f : f;
            BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            if (s > 0.01f) {
                Graphics2D g = out.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                int gr = Math.round(64 * s);
                g.drawImage(t, (64 - gr) / 2, (64 - gr) / 2 + Math.round(10 * (1 - s)), gr, gr, null);
                g.dispose();
            } else {
                for (int i = 20; i < 44; i++) { out.setRGB(i, i, 0xFF9CA3AF); out.setRGB(63 - i, i, 0xFF9CA3AF); }
            }
            return out;
        }
        if (k.leiste()) {
            String g0 = gewaehlt(k);
            Farbe fl = g0 != null && g0.startsWith("gen:") ? Farbe.von(g0.substring(4)) : null;
            if (!gruppe.equals("tint")) {
                String t = effekt(k, "tint");
                if (t != null) nur.put("tint", t);
            }
            return skalieren(leistenVorschau(k, fl, nur), 64);
        }
        String pfad = null;
        BufferedImage roh = null;
        for (String p : k.vorschau) {
            roh = vanilla(p);
            if (roh != null) { pfad = p; break; }
        }
        if (roh == null) return null;
        String gew = gewaehlt(k);
        if (gew != null && gew.startsWith("gen:") && k != Kategorie.GRASS) {
            Farbe f = Farbe.von(gew.substring(4));
            if (f != null && k.faerbt(pfad)) roh = f.faerben(roh, k.modus);
        }
        roh = Effekte.anwenden(k, nur, roh, pfad);
        if (k == Kategorie.GRASS) roh = toenen(roh, 0x91BD59);
        return skalieren(zuschneiden(roh, pfad), 64);
    }

    /** Gewaehlter Style eines Teils als Stil-Objekt (null = Vanilla). */
    private static Stil stilVon(Kategorie k) {
        String g = gewaehlt(k);
        if (g == null) return null;
        for (Stil s : stile(k)) if (s.id().equals(g)) return s;
        return null;
    }

    /** Kleine Pixelziffern (3 x 5) fuer die Vorschau der Zahl-Option. */
    private static final String[] ZIFFERN = {
            "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111" };
    private static final String PROZENT = "101001010100101";

    /** Zahl unten rechts auf ein Vorschaubild (weiss mit schwarzem Rand, doppelt gross). */
    private static BufferedImage mitZahl(BufferedImage basis, String text) {
        int s = 2, w = text.length() * 4 * s, h = 5 * s;
        int bw = Math.max(basis.getWidth(), w + 4), bh = Math.max(basis.getHeight(), h + 4);
        BufferedImage out = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(basis, 0, 0, null);
        g.dispose();
        int ox = bw - w - 1, oy = bh - h - 1;
        boolean[][] an = new boolean[h][w];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String m = c == '%' ? PROZENT : Character.isDigit(c) ? ZIFFERN[c - '0'] : null;
            if (m == null) continue;
            for (int y = 0; y < 5; y++) for (int x = 0; x < 3; x++) {
                if (m.charAt(y * 3 + x) != '1') continue;
                for (int dy = 0; dy < s; dy++) for (int dx = 0; dx < s; dx++) an[y * s + dy][(i * 4 + x) * s + dx] = true;
            }
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!an[y][x]) continue;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int px = ox + x + dx, py = oy + y + dy;
                if (px < 0 || py < 0 || px >= bw || py >= bh) continue;
                int yy = y + dy, xx = x + dx;
                boolean selbst = yy >= 0 && xx >= 0 && yy < h && xx < w && an[yy][xx];
                if (!selbst) out.setRGB(px, py, 0xFF000000);
            }
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (an[y][x]) out.setRGB(ox + x, oy + y, 0xFFFFFFFF);
        return out;
    }

    private static int mitte(BufferedImage karte) {
        return karte.getRGB(karte.getWidth() / 2, karte.getHeight() / 2) & 0xFFFFFF;
    }

    /** Graue Gras-Textur mit einer Grasfarbe toenen (wie im Spiel). */
    private static BufferedImage toenen(BufferedImage grau, int c) {
        if (grau == null) return null;
        BufferedImage out = new BufferedImage(grau.getWidth(), grau.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < grau.getHeight(); y++) {
            for (int x = 0; x < grau.getWidth(); x++) {
                int p = grau.getRGB(x, y);
                int r = ((p >> 16) & 0xFF) * ((c >> 16) & 0xFF) / 255;
                int g = ((p >> 8) & 0xFF) * ((c >> 8) & 0xFF) / 255;
                int b = (p & 0xFF) * (c & 0xFF) / 255;
                out.setRGB(x, y, (p & 0xFF000000) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /** Den sichtbaren Teil einer Textur waehlen (Animationsstreifen, Kristall-Atlas, Inventar). */
    private static BufferedImage zuschneiden(BufferedImage i, String pfad) {
        int w = i.getWidth(), h = i.getHeight();
        if (pfad != null && pfad.endsWith("entity/end_crystal/end_crystal.png") && w >= 64 && h >= 32) {
            int s = w / 64;
            BufferedImage out = new BufferedImage(8 * s, 8 * s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int m = s;
            g.drawImage(i.getSubimage(40 * s, 8 * s, 8 * s, 8 * s), m, m, 8 * s - 2 * m, 8 * s - 2 * m, null);
            g.drawImage(i.getSubimage(8 * s, 8 * s, 8 * s, 8 * s), 0, 0, null);
            g.dispose();
            return out;
        }
        if (pfad != null && pfad.contains("gui/container/") && w >= 256 && h >= 256) {
            return i.getSubimage(0, 0, w * 176 / 256, h * 166 / 256);
        }
        if (h > w && h % w == 0) return i.getSubimage(0, 0, w, w);
        if (pfad != null && pfad.contains("experience_bar_") && w > h * 7) return i.getSubimage(0, 0, Math.max(1, h * 32 / 5), h);
        return i;
    }

    /** Pixelgenau vergroessern bzw. verkleinern, laengste Seite = max. */
    private static BufferedImage skalieren(BufferedImage i, int max) {
        if (i == null) return null;
        int w = i.getWidth(), h = i.getHeight();
        float f = max / (float) Math.max(w, h);
        if (f >= 1f) f = (float) Math.floor(f);
        int nw = Math.max(1, Math.round(w * f)), nh = Math.max(1, Math.round(h * f));
        if (nw == w && nh == h) return i;
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, f >= 1f
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(i, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static volatile boolean laeuft;

    public static boolean wirdAngewendet() {
        return laeuft;
    }

    /**
     * Auswahl als Resource Pack bauen und einschalten. Laeuft im Hintergrund;
     * das Ergebnis (Meldung) kommt auf dem Spiel-Thread an.
     */
    public static CompletableFuture<String> anwenden() {
        CompletableFuture<String> ergebnis = new CompletableFuture<>();
        if (laeuft) { ergebnis.complete("Already applying..."); return ergebnis; }
        laeuft = true;
        Minecraft mc = Minecraft.getInstance();
        Map<Kategorie, String> wahl = auswahl();
        Map<Kategorie, Map<String, String>> eff = new EnumMap<>(Kategorie.class);
        for (Kategorie k : Kategorie.values()) { Map<String, String> m = effekte(k); if (!m.isEmpty()) eff.put(k, m); }
        boolean leer = wahl.isEmpty() && eff.isEmpty();
        int teile = 0;
        for (Kategorie k : Kategorie.values()) if (wahl.containsKey(k) || eff.containsKey(k)) teile++;
        final int anzahlTeile = teile;
        Path ziel = mc.getResourcePackDirectory().resolve(PACK_ORDNER);
        Thread t = new Thread(() -> {
            int dateien = 0;
            String fehler = null;
            try {
                if (!leer) dateien = bauen(ziel, wahl, eff);
            } catch (Throwable e) {
                Errors.report("Styles.bauen", e);
                fehler = "Could not build the styles: " + e.getMessage();
            }
            final int n = dateien;
            final String f = fehler;
            mc.execute(() -> {
                try {
                    if (f != null) { ergebnis.complete(f); return; }
                    einschalten(mc, !leer && n > 0);
                    if (leer || n == 0) loeschenOrdner(ziel);
                    ergebnis.complete(leer ? "All parts are vanilla again." : "Styles applied (" + anzahlTeile + (anzahlTeile == 1 ? " part)." : " parts)."));
                } catch (Throwable e) {
                    Errors.report("Styles.einschalten", e);
                    ergebnis.complete("Could not turn on the styles: " + e.getMessage());
                } finally {
                    laeuft = false;
                }
            });
        }, "Vortex-Styles");
        t.setDaemon(true);
        t.start();
        return ergebnis;
    }

    /** Pack-Ordner neu schreiben. Liefert die Zahl der Dateien. */
    private static int bauen(Path ziel, Map<Kategorie, String> wahl, Map<Kategorie, Map<String, String>> eff) throws IOException {
        loeschenOrdner(ziel);
        Files.createDirectories(ziel);
        Files.writeString(ziel.resolve("pack.mcmeta"),
                "{\"pack\":{\"description\":\"Vortex Client Styles\",\"min_format\":65,\"max_format\":9999}}\n",
                StandardCharsets.UTF_8);
        byte[] icon = png(packBild());
        if (icon != null) Files.write(ziel.resolve("pack.png"), icon);
        Path assets = ziel.resolve("assets/minecraft");
        int n = 0;
        for (Kategorie k : Kategorie.values()) {
            String id = wahl.get(k);
            Map<String, String> e = eff.getOrDefault(k, Map.of());
            boolean bilder = Effekte.aendertBilder(e);
            if (id != null && id.startsWith("imp:")) {
                Path quelle = ordner().resolve(k.id).resolve(id.substring(4)).resolve("assets/minecraft");
                if (Files.isDirectory(quelle)) {
                    try (Stream<Path> w = Files.walk(quelle)) {
                        for (Path q : (Iterable<Path>) w::iterator) {
                            if (!Files.isRegularFile(q)) continue;
                            String rel = quelle.relativize(q).toString().replace('\\', '/');
                            if (!k.gehoert(rel)) continue;
                            Path z = assets.resolve(rel);
                            Files.createDirectories(z.getParent());
                            BufferedImage bild = bilder && rel.endsWith(".png") ? bild(q) : null;
                            byte[] b = bild == null ? null : png(Effekte.anwenden(k, e, bild, rel));
                            if (b != null) Files.write(z, b);
                            else Files.copy(q, z, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            n++;
                        }
                    }
                }
                if (bilder) n += vanillaSchreiben(assets, k, null, e, true);
            } else {
                Farbe f = id != null && id.startsWith("gen:") ? Farbe.von(id.substring(4)) : null;
                if (f != null || bilder) n += vanillaSchreiben(assets, k, f, e, false);
            }
            Float totem = Effekte.totemGroesse(e);
            if (totem != null) {
                Path z = assets.resolve("models/item/totem_of_undying.json");
                Files.createDirectories(z.getParent());
                Files.writeString(z, Effekte.totemModell(totem), StandardCharsets.UTF_8);
                n++;
            }
        }
        return n;
    }

    /**
     * Minecrafts eigene Texturen eines Teils umfaerben und/oder mit Effekten schreiben.
     * nurFehlende: nichts ueberschreiben, was schon im Pack liegt (importierter Style).
     */
    private static int vanillaSchreiben(Path assets, Kategorie k, Farbe f, Map<String, String> e, boolean nurFehlende) throws IOException {
        boolean bilder = Effekte.aendertBilder(e);
        List<String> ordner = new ArrayList<>(k.ordner());
        if (k == Kategorie.GRASS && bilder) ordner.add("textures/block");
        int n = 0;
        java.util.Set<String> erledigt = new java.util.HashSet<>();
        for (String o : ordner) {
            for (String pfad : vanillaListe(o)) {
                if (!pfad.endsWith(".png") || !erledigt.add(pfad)) continue;
                boolean faerben = f != null && k.faerbt(pfad) && !(k == Kategorie.ARMOR_BAR && Effekte.ruestungNachGetragen(e));
                boolean effekt = bilder && (k == Kategorie.GRASS ? k.gehoert(pfad) && !pfad.contains("colormap") : k.effektDran(pfad));
                if (!faerben && !effekt) continue;
                Path z = assets.resolve(pfad);
                if (nurFehlende && Files.exists(z)) continue;
                BufferedImage src = leisteLaden(k, pfad, e, faerben ? f : null);
                if (src == null) continue;
                if (effekt) src = Effekte.anwenden(k, e, src, pfad);
                byte[] b = png(src);
                if (b == null) continue;
                Files.createDirectories(z.getParent());
                Files.write(z, b);
                byte[] meta = vanillaBytes(pfad + ".mcmeta");
                if (meta != null) Files.write(assets.resolve(pfad + ".mcmeta"), meta);
                n++;
            }
        }
        return n;
    }

    /**
     * Ein Vanilla-Bild laden und (falls f gesetzt) umfaerben. Blink-Bilder der Herzen:
     * bei "Damage blink: Off" wird das normale Bild genommen, sonst nach dem Umfaerben
     * wieder aufgehellt, damit das Aufblitzen sichtbar bleibt.
     */
    private static BufferedImage leisteLaden(Kategorie k, String pfad, Map<String, String> e, Farbe f) {
        boolean blink = k == Kategorie.HEARTS && pfad.endsWith("_blinking.png");
        boolean ohneBlink = blink && e != null && "hidden".equals(e.get("blink"));
        BufferedImage src = vanilla(ohneBlink ? pfad.replace("_blinking.png", ".png") : pfad);
        if (src == null) return null;
        if (f != null) {
            src = f.faerben(src, k.modus);
            if (blink && !ohneBlink) src = Effekte.aufhellen(src, 0.45f);
        }
        return src;
    }

    /** Die Bilder, aus denen die Vorschau einer Leiste besteht (je Feld: Hintergrund, Vordergrund). */
    private static String[][] leistenTeile(Kategorie k, boolean blinken) {
        String h = "textures/gui/sprites/hud/";
        return switch (k) {
            case HEARTS -> {
                String c = h + "heart/container" + (blinken ? "_blinking" : "") + ".png";
                String voll = h + "heart/full" + (blinken ? "_blinking" : "") + ".png";
                String halb = h + "heart/half" + (blinken ? "_blinking" : "") + ".png";
                yield new String[][] { {c, voll}, {c, voll}, {c, halb}, {c, null} };
            }
            case HUNGER -> new String[][] { {h + "food_empty.png", h + "food_full.png"}, {h + "food_empty.png", h + "food_full.png"},
                    {h + "food_empty.png", h + "food_half.png"}, {h + "food_empty.png", null} };
            default -> new String[][] { {h + "armor_full.png", null}, {h + "armor_full.png", null},
                    {h + "armor_half.png", null}, {h + "armor_empty.png", null} };
        };
    }

    /**
     * Vorschau einer Leiste: 2 x 2 Felder (voll, voll, halb, leer) -- so sieht man Farbe,
     * Look und versteckte leere Felder auf einen Blick. Bei "By armor" die Ruestungsfarben.
     */
    private static BufferedImage leistenVorschau(Kategorie k, Farbe f, Map<String, String> e) {
        boolean blinken = e != null && e.containsKey("blink");
        String[][] teile = leistenTeile(k, blinken);
        BufferedImage out = new BufferedImage(19, 19, BufferedImage.TYPE_INT_ARGB);
        int[] ruestung = { 0x56F0E4, 0x56F0E4, 0xFFD84A, 0xFFFFFF };
        for (int i = 0; i < teile.length; i++) {
            int ox = (i % 2) * 10, oy = (i / 2) * 10;
            for (String pfad : teile[i]) {
                if (pfad == null) continue;
                boolean faerben = f != null && k.faerbt(pfad) && !(k == Kategorie.ARMOR_BAR && Effekte.ruestungNachGetragen(e));
                BufferedImage b = leisteLaden(k, pfad, e, faerben ? f : null);
                if (b == null) continue;
                if (e != null && !e.isEmpty() && k.effektDran(pfad)) b = Effekte.anwenden(k, e, b, pfad);
                boolean toenen = k == Kategorie.ARMOR_BAR && Effekte.ruestungNachGetragen(e) && !pfad.endsWith("armor_empty.png");
                for (int y = 0; y < Math.min(9, b.getHeight()); y++) {
                    for (int x = 0; x < Math.min(9, b.getWidth()); x++) {
                        int p = b.getRGB(x, y);
                        int a = p >>> 24;
                        if (a == 0) continue;
                        if (toenen && (x < 4 || !pfad.endsWith("armor_half.png"))) p = multiplizieren(p, ruestung[i]);
                        int q = out.getRGB(ox + x, oy + y);
                        out.setRGB(ox + x, oy + y, a >= 250 ? p : ueber(q, p));
                    }
                }
            }
        }
        return out;
    }

    private static int multiplizieren(int p, int c) {
        int r = ((p >> 16) & 0xFF) * ((c >> 16) & 0xFF) / 255;
        int g = ((p >> 8) & 0xFF) * ((c >> 8) & 0xFF) / 255;
        int b = (p & 0xFF) * (c & 0xFF) / 255;
        return (p & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Pixel p mit seiner Deckkraft ueber q legen. */
    private static int ueber(int q, int p) {
        float a = (p >>> 24) / 255f, qa = (q >>> 24) / 255f;
        float na = a + qa * (1 - a);
        if (na <= 0f) return 0;
        int r = Math.round((((p >> 16) & 0xFF) * a + ((q >> 16) & 0xFF) * qa * (1 - a)) / na);
        int g = Math.round((((p >> 8) & 0xFF) * a + ((q >> 8) & 0xFF) * qa * (1 - a)) / na);
        int b = Math.round(((p & 0xFF) * a + (q & 0xFF) * qa * (1 - a)) / na);
        return Math.round(na * 255) << 24 | r << 16 | g << 8 | b;
    }

    /** Kleines Pack-Bild: der Vortex-Verlauf. */
    private static BufferedImage packBild() {
        BufferedImage i = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Farbe v = Farbe.ALLE.get(0);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                float dx = x - 31.5f, dy = y - 31.5f;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                int c = v.bei((x + y) / 126f);
                float f = r < 22 ? 1f : r < 30 ? 0.55f : 0.25f;
                int rr = (int) (((c >> 16) & 0xFF) * f), gg = (int) (((c >> 8) & 0xFF) * f), bb = (int) ((c & 0xFF) * f);
                i.setRGB(x, y, 0xFF000000 | (rr << 16) | (gg << 8) | bb);
            }
        }
        return i;
    }

    /** Pack ein- (oben auf) oder ausschalten und neu laden. Spiel-Thread. */
    private static void einschalten(Minecraft mc, boolean an) {
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        List<String> sel = new ArrayList<>(repo.getSelectedIds());
        sel.remove(PACK_ID);
        if (an && repo.getAvailableIds().contains(PACK_ID)) sel.add(PACK_ID);
        repo.setSelected(sel);
        List<String> vorher = new ArrayList<>(mc.options.resourcePacks);
        mc.options.updateResourcePacks(repo);
        if (vorher.equals(mc.options.resourcePacks)) mc.reloadResourcePacks();
    }

    /** Ist das Styles-Pack gerade eingeschaltet? */
    public static boolean aktiv() {
        try {
            return Minecraft.getInstance().getResourcePackRepository().getSelectedIds().contains(PACK_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void loeschenOrdner(Path p) {
        if (!Files.exists(p)) return;
        try (Stream<Path> w = Files.walk(p)) {
            List<Path> alle = w.sorted(Comparator.reverseOrder()).toList();
            for (Path q : alle) Files.deleteIfExists(q);
        } catch (IOException e) {
            Errors.report("Styles.loeschenOrdner", e);
        }
    }

    /** Importierten Style entfernen (und abwaehlen). */
    public static void entfernen(Stil s) {
        if (s == null || !s.importiert()) return;
        if (s.id().equals(gewaehlt(s.kat()))) waehle(s.kat(), null);
        loeschenOrdner(s.ordner());
    }

    private static final Pattern VORSCHAU = Pattern.compile("(?:^|/)preview/([a-z0-9_\\-]+)\\.png$");

    /**
     * Eine Datei importieren: ein Resource Pack (.zip) oder eine Sammlung mit
     * mehreren Packs in Unterordnern (.zip/.jar). Liefert eine Meldung fuer den
     * Spieler. Es werden nur Bilder gelesen und kopiert -- nichts ausgefuehrt.
     */
    public static String importieren(Path datei) {
        String dateiname = datei.getFileName().toString();
        try (ZipFile zip = new ZipFile(datei.toFile())) {
            Map<String, List<ZipEntry>> packs = new LinkedHashMap<>();
            Map<String, ZipEntry> vorschauen = new HashMap<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry z = en.nextElement();
                if (z.isDirectory()) continue;
                String n = z.getName().replace('\\', '/');
                Matcher vm = VORSCHAU.matcher(n.toLowerCase(Locale.ROOT));
                if (vm.find()) vorschauen.putIfAbsent(vm.group(1), z);
                int i = n.indexOf("assets/minecraft/");
                if (i < 0 || (i > 0 && n.charAt(i - 1) != '/')) continue;
                String wurzel = n.substring(0, i);
                packs.computeIfAbsent(wurzel, w -> new ArrayList<>()).add(z);
            }
            String basisDatei = dateiname.replaceAll("(?i)\\.(zip|jar)$", "");
            long gesamt = 0;
            int stile = 0;
            for (Map.Entry<String, List<ZipEntry>> pack : packs.entrySet()) {
                String wurzel = pack.getKey();
                String basis = wurzel.isEmpty() ? basisDatei : letzterOrdner(wurzel);
                String klein = basis.toLowerCase(Locale.ROOT);
                Kategorie hinweis = null;
                String name = basis;
                for (Kategorie k : Kategorie.values()) {
                    if (klein.startsWith(k.id + "_") && klein.length() > k.id.length() + 1) {
                        hinweis = k;
                        name = basis.substring(k.id.length() + 1);
                        break;
                    }
                }
                name = schoen(name);
                Map<Kategorie, Map<String, ZipEntry>> teile = new EnumMap<>(Kategorie.class);
                for (ZipEntry z : pack.getValue()) {
                    String n = z.getName().replace('\\', '/');
                    String rel = n.substring(wurzel.length() + "assets/minecraft/".length()).toLowerCase(Locale.ROOT);
                    if (!(rel.endsWith(".png") || rel.endsWith(".png.mcmeta"))) continue;
                    if (rel.contains("..") || rel.startsWith("/")) continue;
                    rel = neuerPfad(rel);
                    Kategorie k = Kategorie.von(rel);
                    if (k == null) continue;
                    teile.computeIfAbsent(k, x -> new LinkedHashMap<>()).put(rel, z);
                }
                for (Map.Entry<Kategorie, Map<String, ZipEntry>> t : teile.entrySet()) {
                    Kategorie k = t.getKey();
                    if (t.getValue().keySet().stream().noneMatch(r -> r.endsWith(".png"))) continue;
                    String slug = slug(name);
                    if (slug.isEmpty()) slug = "style";
                    Path ziel = ordner().resolve(k.id).resolve(slug);
                    loeschenOrdner(ziel);
                    Path assets = ziel.resolve("assets/minecraft");
                    int bilder = 0;
                    for (Map.Entry<String, ZipEntry> f : t.getValue().entrySet()) {
                        Path z = assets.resolve(f.getKey()).normalize();
                        if (!z.startsWith(assets)) continue;
                        byte[] b = lesen(zip, f.getValue());
                        if (b == null) continue;
                        gesamt += b.length;
                        if (gesamt > MAX_IMPORT) return "This file is too big to import (over 400 MB of textures).";
                        if (f.getKey().endsWith(".png") && bild(b) == null) continue;
                        Files.createDirectories(z.getParent());
                        Files.write(z, b);
                        if (f.getKey().endsWith(".png")) bilder++;
                    }
                    if (bilder == 0) { loeschenOrdner(ziel); continue; }
                    Files.writeString(ziel.resolve("name.txt"), name, StandardCharsets.UTF_8);
                    ZipEntry vz = vorschauen.get(klein);
                    if (vz != null && (hinweis == null || hinweis == k)) {
                        byte[] b = lesen(zip, vz);
                        if (b != null && bild(b) != null) Files.write(ziel.resolve("preview.png"), b);
                    }
                    stile++;
                }
            }
            if (stile == 0) return "No textures for any style part found in " + dateiname + ".";
            return "Imported " + stile + (stile == 1 ? " style" : " styles") + " from " + dateiname + ".";
        } catch (java.util.zip.ZipException e) {
            return dateiname + " is not a resource pack (.zip) or a mod (.jar).";
        } catch (Throwable e) {
            Errors.report("Styles.importieren", e);
            return "Import failed: " + e.getMessage();
        }
    }

    private static byte[] lesen(ZipFile zip, ZipEntry z) {
        if (z.getSize() > MAX_DATEI) return null;
        try (InputStream in = zip.getInputStream(z)) {
            byte[] b = in.readNBytes((int) MAX_DATEI + 1);
            return b.length > MAX_DATEI ? null : b;
        } catch (IOException e) {
            return null;
        }
    }

    private static String letzterOrdner(String wurzel) {
        String w = wurzel.endsWith("/") ? wurzel.substring(0, wurzel.length() - 1) : wurzel;
        int i = w.lastIndexOf('/');
        return i >= 0 ? w.substring(i + 1) : w;
    }

    /** "kesie_blueberry-light" -> "Kesie Blueberry Light" */
    static String schoen(String s) {
        String[] teile = s.replace('-', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String t : teile) {
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(t.charAt(0))).append(t.substring(1));
        }
        String r = sb.toString();
        return r.length() > 40 ? r.substring(0, 40) : r;
    }

    private static String slug(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Pfade aus alten Packs (1.8 bis 1.20) auf die heutigen Namen bringen. */
    static String neuerPfad(String p) {
        p = p.replace("textures/blocks/", "textures/block/").replace("textures/items/", "textures/item/");
        p = p.replace("textures/entity/endercrystal/endercrystal", "textures/entity/end_crystal/end_crystal");
        p = p.replaceAll("^textures/block/fire_layer_([01])", "textures/block/fire_$1");
        p = p.replaceAll("^textures/block/grass_top\\.png", "textures/block/grass_block_top.png");
        p = p.replaceAll("^textures/block/grass_side_overlay\\.png", "textures/block/grass_block_side_overlay.png");
        p = p.replaceAll("^textures/block/grass_side_snowed\\.png", "textures/block/grass_block_snow.png");
        p = p.replaceAll("^textures/block/grass_side\\.png", "textures/block/grass_block_side.png");
        p = p.replaceAll("^textures/item/(wood|gold)_(sword|pickaxe|axe)\\.png", "textures/item/$1en_$2.png");
        p = p.replaceAll("^textures/item/gold_(helmet|chestplate|leggings|boots)", "textures/item/golden_$1");
        p = p.replaceAll("^textures/item/apple_golden\\.png", "textures/item/golden_apple.png");
        p = p.replaceAll("^textures/item/totem\\.png", "textures/item/totem_of_undying.png");
        Matcher m = Pattern.compile("^textures/models/armor/([a-z]+)_layer_([12])(_overlay)?\\.png$").matcher(p);
        if (m.matches()) {
            String mat = m.group(1);
            String ord = m.group(2).equals("1") ? "humanoid" : "humanoid_leggings";
            p = "textures/entity/equipment/" + ord + "/" + mat + (m.group(3) != null ? "_overlay" : "") + ".png";
        }
        return p;
    }
}
