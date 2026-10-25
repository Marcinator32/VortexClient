package com.vortex.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vortex.client.social.Social;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cosmetics ueber den Freunde-Server austauschen, damit andere Vortex-
 * Spieler sie sehen (und du ihre).
 *
 *   - Eigene Auswahl: nach jeder Aenderung und nach jedem (Wieder-)Verbinden
 *     hochladen; ein eigenes Cape-Bild als JPEG (klein genug fuers Netz).
 *   - Andere: alle 2 s die Spieler in der Naehe ansehen; wer laenger als eine
 *     Minute nicht abgefragt wurde, wird (gebuendelt) abgefragt.
 *
 * Ohne Freunde-Server passiert nichts -- dann sieht jeder nur seine eigenen.
 */
public final class CosmeticsSync {
    private CosmeticsSync() {}

    private static final long NEU_FRAGEN_MS = 60_000;
    private static final Map<UUID, String> BILD = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GEFRAGT = new ConcurrentHashMap<>();
    private static Object verbindung;
    private static volatile String hochgeladenesBild = "";
    private static volatile boolean serverKannEs = true;
    private static int tick;
    /** CS-Knife-Auswahl (seit 4.65, kommt vom Vortex Plus Addon): -1 = keins. */
    private static volatile int messer = -1;
    private static volatile boolean messerNeu;
    private static final Map<UUID, Integer> MESSER = new ConcurrentHashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (++tick % 40 != 0) return;
            Cosmetics.vonAussen();
            try { Shop.tick(); } catch (Throwable t) { com.vortex.client.core.Errors.report("Shop", t); }
            try { abgleichen(mc); } catch (Throwable t) { com.vortex.client.core.Errors.report("CosmeticsSync", t); }
        });
    }

    static String bildVon(UUID spieler) { return BILD.get(spieler); }

    /** Bild vom Server holen (blockiert -- nur aus Hintergrund-Threads aufrufen). */
    static String bildHolen(String hash) {
        try {
            JsonObject r = Social.request("cosmetics.imageGet", Social.args("hash", hash)).get(20, TimeUnit.SECONDS);
            return r.has("image") ? r.get("image").getAsString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Letzte Weitergabe (fuer /vf emotes): Emote, an wie viele, wann; -1 = Fehler. */
    private static volatile String letztesEmote = "";
    private static volatile int letzteAnzahl = -2;
    private static volatile long letzteZeit;
    private static volatile String letzterFehler = "";

    public static String letztesEmote() { return letztesEmote; }
    public static int letzteAnzahl() { return letzteAnzahl; }
    public static long letzteZeit() { return letzteZeit; }
    public static String letzterFehler() { return letzterFehler; }
    public static boolean serverKannEs() { return serverKannEs; }

    private static boolean hinweisGezeigt;

    /** Emote erreichte niemanden, obwohl ein Freund in der Naehe ist: einmal sagen, wie man es prueft. */
    private static void hinweisWennFreundDa() {
        Minecraft mc = Minecraft.getInstance();
        if (hinweisGezeigt || mc.level == null || mc.player == null) return;
        java.util.Set<String> freunde = new java.util.HashSet<>();
        for (JsonObject f : Social.friends()) freunde.add(Social.str(f, "uuid").replace("-", ""));
        for (Player p : mc.level.players()) {
            if (p == mc.player || !freunde.contains(p.getUUID().toString().replace("-", ""))) continue;
            hinweisGezeigt = true;
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "\u00a7d[Emotes] \u00a77" + p.getName().getString() + " did not get your emote. Type \u00a7f/vf emotes\u00a77 to see why."));
            return;
        }
    }

    /** Eigenes Emote an die anderen Vortex-Spieler schicken (der Server reicht es weiter). */
    static void emoteGespielt(String id) {
        letztesEmote = id;
        letzteZeit = System.currentTimeMillis();
        if (Social.conn() != Social.Conn.ONLINE) { letzteAnzahl = -1; letzterFehler = "not connected to the friends server"; return; }
        Social.aktivitaetJetzt();
        letztesEmote = id;
        letzteZeit = System.currentTimeMillis();
        Social.request("emote.play", Social.args("emote", id)).whenComplete((r, e) -> {
            if (e != null) { letzteAnzahl = -1; letzterFehler = String.valueOf(Social.errorOf(e)); pruefeFehler(e); return; }
            int an = r != null && r.has("sent") ? r.get("sent").getAsInt() : -1;
            letzteAnzahl = an;
            letzterFehler = "";
            if (an == 0 && !hinweisGezeigt) Minecraft.getInstance().execute(CosmeticsSync::hinweisWennFreundDa);
            com.vortex.client.core.Errors.note("Emotes", id + " an " + an + " Vortex-Spieler weitergereicht");
        });
    }

    /**
     * Fuer Erweiterungen (Vortex Plus Addon): ein kurzes Signal an die Vortex-Spieler auf
     * demselben Server schicken, z. B. "knife_spin". Geht ueber denselben Weg wie Emotes
     * (Kleinbuchstaben und _, hoechstens eins pro Sekunde); beim Empfaenger landet es in
     * Emotes.signalHoerer.
     */
    public static void signal(String id) { emoteGespielt(id); }

    /**
     * Eigenes CS Knife (Nummer vom Addon, -1 = keins). Wird mit den Cosmetics hochgeladen,
     * gebuendelt beim naechsten Abgleich (Durchklicken der Lackierungen schickt nicht jedes Mal).
     */
    public static void setzeMesser(int nr) {
        if (nr == messer) return;
        messer = nr;
        messerNeu = true;
    }

    /** CS Knife eines anderen Vortex-Spielers (-1 = keins oder unbekannt). */
    public static int messerVon(UUID spieler) { return spieler == null ? -1 : MESSER.getOrDefault(spieler, -1); }

    /**
     * Kontowechsel im Spiel (seit 4.89): alles vergessen, was zum alten Account gehoerte.
     * Beim naechsten Verbinden laedt der neue Account dieselbe Auswahl hoch (die Auswahl
     * steht in cosmetics.json und gilt fuer alle Accounts), auch das eigene Cape-Bild, und
     * die anderen Spieler werden sofort neu abgefragt.
     */
    public static void kontoGewechselt() {
        verbindung = null;
        hochgeladenesBild = "";
        serverKannEs = true;
        hinweisGezeigt = false;
        messerNeu = false;
        GEFRAGT.clear();
        BILD.clear();
        MESSER.clear();
        for (UUID u : List.copyOf(Cosmetics.fremde().keySet())) Cosmetics.setzeFremde(u, null);
        Shop.zuruecksetzen();
        Abzeichen.vergessen();
    }

    /** Aus Cosmetics.speichern: geaenderte Auswahl hochladen. */
    static void geaendert() {
        if (Social.conn() == Social.Conn.ONLINE && serverKannEs) hochladen();
    }

    private static void abgleichen(Minecraft mc) {
        if (Social.conn() != Social.Conn.ONLINE) return;
        if (verbindung != Social.data()) {
            verbindung = Social.data();
            serverKannEs = true;
            messerNeu = false;
            hochladen();
        } else if (messerNeu) {
            messerNeu = false;
            hochladen();
        }
        if (!serverKannEs) return;
        if (mc.level == null || mc.player == null) return;
        long jetzt = System.currentTimeMillis();
        List<UUID> fragen = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            Long zuletzt = GEFRAGT.get(p.getUUID());
            if (zuletzt == null || jetzt - zuletzt > NEU_FRAGEN_MS) fragen.add(p.getUUID());
            if (fragen.size() >= 100) break;
        }
        if (fragen.isEmpty()) return;
        for (UUID u : fragen) GEFRAGT.put(u, jetzt);
        JsonObject args = new JsonObject();
        JsonArray liste = new JsonArray();
        for (UUID u : fragen) liste.add(u.toString().replace("-", ""));
        args.add("uuids", liste);
        Social.request("cosmetics.get", args).whenComplete((r, fehler) -> {
            if (fehler != null) { pruefeFehler(fehler); return; }
            JsonObject spieler = r.getAsJsonObject("players");
            for (UUID u : fragen) {
                JsonElement e = spieler == null ? null : spieler.get(u.toString().replace("-", ""));
                if (e == null || !e.isJsonObject()) { Cosmetics.setzeFremde(u, null); BILD.remove(u); MESSER.remove(u); Abzeichen.setzen(u, ""); continue; }
                JsonObject o = e.getAsJsonObject();
                String[] extra = auspacken(text(o, "face"));
                String hals = text(o, "neck").isEmpty() ? extra[1] : text(o, "neck");
                String stil = text(o, "nametag").isEmpty() ? extra[2] : text(o, "nametag");
                if (extra[3].isEmpty()) MESSER.remove(u); else MESSER.put(u, Integer.parseInt(extra[3]));
                Cosmetics.setzeFremde(u, new Cosmetics.Auswahl(text(o, "cape"), text(o, "hat"), Zubehoer.auraFuer(text(o, "particles")),
                        o.has("density") ? o.get("density").getAsInt() : 2,
                        text(o, "bandana"), extra[0], text(o, "back"), text(o, "shield"), hals, stil, text(o, "pet")));
                Abzeichen.setzen(u, text(o, "badge"));
                if (o.has("image") && o.get("image").isJsonPrimitive()) BILD.put(u, o.get("image").getAsString());
                else BILD.remove(u);
            }
        });
    }

    /**
     * Kette und Namens-Effekt (seit 4.63) und das CS Knife (seit 4.65, m) reisen zusaetzlich
     * hinten am Face-Feld mit, als Nummern ("visor--sk3n5m12": Kette 3, Effekt 5, Messer 12). Der Freunde-Server kennt die neuen Felder
     * erst nach einem Update -- so kommen sie trotzdem an. Aeltere Clients lesen nur den Teil
     * vor "--" (und das "s" fuers Glitzern) und sehen ein ganz normales Face-Cosmetic.
     * Neue Designs werden deshalb immer HINTEN angehaengt, damit die Nummern stimmen.
     */
    static String einpacken(String face, String hals, String stil) {
        return einpacken(face, hals, stil, messer);
    }

    static String einpacken(String face, String hals, String stil, int m) {
        int k = hals.isEmpty() ? -1 : nummer(Zubehoer.von(Zubehoer.Kategorie.NECK).stream().map(Zubehoer.Design::id).toList(), Zubehoer.basisId(hals));
        int n = stil.isEmpty() ? -1 : NamensEffekt.nummer(stil);
        if (k < 0 && n < 0 && m < 0) return face;
        String f = face == null ? "" : face;
        StringBuilder b = new StringBuilder(f);
        if (!f.contains("--")) b.append("--");
        if (k >= 0) b.append('k').append(k);
        if (n >= 0) b.append('n').append(n);
        if (m >= 0 && m < 1000) b.append('m').append(m);
        return b.length() <= 40 ? b.toString() : f;
    }

    /** {face ohne Zusatz (Glitzern bleibt), Kette, Namens-Effekt, Messer-Nummer} */
    static String[] auspacken(String face) {
        String[] r = { face == null ? "" : face, "", "", "" };
        int i = r[0].indexOf("--");
        if (i < 0) return r;
        String zusatz = r[0].substring(i + 2);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([knm])(\\d{1,3})").matcher(zusatz);
        StringBuilder rest = new StringBuilder();
        int letzte = 0;
        while (m.find()) {
            rest.append(zusatz, letzte, m.start());
            letzte = m.end();
            int nr = Integer.parseInt(m.group(2));
            if (m.group(1).equals("k")) {
                var ketten = Zubehoer.von(Zubehoer.Kategorie.NECK);
                if (nr < ketten.size()) r[1] = ketten.get(nr).id();
            } else if (m.group(1).equals("n")) {
                r[2] = NamensEffekt.id(nr);
            } else {
                r[3] = String.valueOf(nr);
            }
        }
        rest.append(zusatz.substring(letzte));
        String basis = r[0].substring(0, i);
        r[0] = rest.length() == 0 ? basis : basis + "--" + rest;
        return r;
    }

    private static int nummer(java.util.List<String> ids, String id) {
        return ids.indexOf(id);
    }

    private static String text(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    /** Alter Freunde-Server ohne Cosmetics: nicht weiter fragen. */
    private static void pruefeFehler(Throwable fehler) {
        if (String.valueOf(Social.errorOf(fehler)).contains("Unknown request")) serverKannEs = false;
    }

    private static void hochladen() {
        Cosmetics.Auswahl a = Cosmetics.eigene();
        Thread t = new Thread(() -> {
            try {
                if (EigenesCape.ID.equals(a.cape()) && EigenesCape.vorhanden()) {
                    byte[] jpeg = alsJpeg();
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jpeg)).substring(0, 32);
                    if (!hash.equals(hochgeladenesBild)) {
                        Social.request("cosmetics.image", Social.args("image", Base64.getEncoder().encodeToString(jpeg))).get(30, TimeUnit.SECONDS);
                        hochgeladenesBild = hash;
                    }
                }
                Social.request("cosmetics.set", Social.args("cape", a.cape(), "hat", a.hut(), "particles", a.partikel(), "density", a.dichte(),
                        "bandana", a.bandana(), "face", einpacken(a.gesicht(), a.hals(), a.namensstil()), "back", a.ruecken(), "shield", a.schild(),
                        "neck", a.hals(), "nametag", a.namensstil(), "pet", a.pet()))
                        .get(20, TimeUnit.SECONDS);
            } catch (Throwable e) {
                pruefeFehler(e.getCause() != null ? e.getCause() : e);
                com.vortex.client.core.Errors.note("CosmeticsSync", "Upload failed: " + Social.errorOf(e));
            }
        }, "Vortex-Cosmetics-Upload");
        t.setDaemon(true);
        t.start();
    }

    /** Das fertige Cape (512x256) als JPEG -- passt sicher unter die 80 KB des Servers. */
    private static byte[] alsJpeg() throws Exception {
        BufferedImage argb = ImageIO.read(EigenesCape.datei().toFile());
        BufferedImage rgb = new BufferedImage(argb.getWidth(), argb.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(argb, 0, 0, null);
        for (float q : new float[] { 0.88f, 0.7f, 0.5f }) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                w.setOutput(ios);
                ImageWriteParam p = w.getDefaultWriteParam();
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(q);
                w.write(null, new IIOImage(rgb, null, null), p);
            } finally {
                w.dispose();
            }
            if (out.size() <= 78 * 1024) return out.toByteArray();
        }
        throw new IllegalStateException("The cape picture is too detailed to upload.");
    }
}
