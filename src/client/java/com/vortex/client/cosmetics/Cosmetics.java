package com.vortex.client.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die gewaehlten Cosmetics: Cape, Hut, Partikel.
 *
 * Gespeichert in config/vortex-client/cosmetics.json -- dieselbe Datei, aus
 * der ActiveCape schon das Cape ("cape") liest; die neuen Felder kommen dazu.
 *
 * Cape: Id aus dem Cosmetics-Verzeichnis (siehe ActiveCape) oder "custom"
 * fuer ein eigenes Bild (EigenesCape). Leer = kein Vortex-Cape.
 *
 * Andere Spieler: {@link #setzeFremde} nimmt ihre Auswahl entgegen
 * (CosmeticsSync, ueber den Freunde-Server). Ohne den sieht jeder nur seine eigenen.
 */
public final class Cosmetics {
    private Cosmetics() {}

    /** Auswahl eines Spielers. Leere Strings = nichts gewaehlt. */
    /**
     * Was ein Spieler traegt. 'partikel' ist seit 4.29 die Aura (alte Partikel-IDs
     * werden auf die passende Aura abgebildet, siehe Zubehoer); dichte = Menge 1..3.
     */
    public record Auswahl(String cape, String hut, String partikel, int dichte,
                          String bandana, String gesicht, String ruecken, String schild,
                          String hals, String namensstil, String pet) {
        public static final Auswahl LEER = new Auswahl("", "", "", 2);

        public Auswahl {
            cape = n(cape); hut = n(hut); partikel = n(partikel);
            bandana = n(bandana); gesicht = n(gesicht); ruecken = n(ruecken); schild = n(schild);
            hals = n(hals); namensstil = n(namensstil); pet = n(pet);
            dichte = Math.max(1, Math.min(3, dichte));
        }

        public Auswahl(String cape, String hut, String partikel, int dichte,
                       String bandana, String gesicht, String ruecken, String schild, String hals, String namensstil) {
            this(cape, hut, partikel, dichte, bandana, gesicht, ruecken, schild, hals, namensstil, "");
        }

        public Auswahl(String cape, String hut, String partikel, int dichte,
                       String bandana, String gesicht, String ruecken, String schild) {
            this(cape, hut, partikel, dichte, bandana, gesicht, ruecken, schild, "", "", "");
        }

        public Auswahl(String cape, String hut, String partikel, int dichte) {
            this(cape, hut, partikel, dichte, "", "", "", "", "", "", "");
        }

        private static String n(String s) { return s == null ? "" : s; }

        public Auswahl mitCape(String v) { return new Auswahl(v, hut, partikel, dichte, bandana, gesicht, ruecken, schild, hals, namensstil, pet); }
        public Auswahl mitHut(String v) { return new Auswahl(cape, v, partikel, dichte, bandana, gesicht, ruecken, schild, hals, namensstil, pet); }
        public Auswahl mitAura(String v) { return new Auswahl(cape, hut, v, dichte, bandana, gesicht, ruecken, schild, hals, namensstil, pet); }
        public Auswahl mitDichte(int v) { return new Auswahl(cape, hut, partikel, v, bandana, gesicht, ruecken, schild, hals, namensstil, pet); }
        public Auswahl mitBandana(String v) { return new Auswahl(cape, hut, partikel, dichte, v, gesicht, ruecken, schild, hals, namensstil, pet); }
        public Auswahl mitGesicht(String v) { return new Auswahl(cape, hut, partikel, dichte, bandana, v, ruecken, schild, hals, namensstil, pet); }
        public Auswahl mitRuecken(String v) { return new Auswahl(cape, hut, partikel, dichte, bandana, gesicht, v, schild, hals, namensstil, pet); }
        public Auswahl mitSchild(String v) { return new Auswahl(cape, hut, partikel, dichte, bandana, gesicht, ruecken, v, hals, namensstil, pet); }
        /** Seit 4.63: Kette um den Hals. */
        public Auswahl mitHals(String v) { return new Auswahl(cape, hut, partikel, dichte, bandana, gesicht, ruecken, schild, v, namensstil, pet); }
        /** Seit 4.63: animierter Name (ueber dem Kopf, im Chat, in der Tab-Liste). */
        public Auswahl mitNamensstil(String v) { return new Auswahl(cape, hut, partikel, dichte, bandana, gesicht, ruecken, schild, hals, v, pet); }
        /** Seit 4.95: Pet neben der Schulter. */
        public Auswahl mitPet(String v) { return new Auswahl(cape, hut, partikel, dichte, bandana, gesicht, ruecken, schild, hals, namensstil, v); }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Auswahl eigene = null;
    private static volatile boolean partikelErstePerson = false;
    private static volatile boolean capePhysik = true;
    /** Seit 4.35: seitliche Wellen im Cape (CapeEbene) und wehende Bandana-Baender -- je fuer alles, was du siehst. */
    private static volatile boolean capeWellen = true, bandanaPhysik = true;
    /** Seit 4.63: animierte Namen (eigener und die anderer) anzeigen. */
    private static volatile boolean namenZeigen = true;
    private static volatile boolean abzeichenZeigen = true;
    /** Seit 4.81: grosse Auren anderer Spieler -- 0 = normal, 1 = klein, 2 = aus (Fairness im PvP). */
    private static volatile int grosseFremde = 0;
    private static final Map<UUID, Auswahl> FREMDE = new ConcurrentHashMap<>();
    /** Aenderungszeit von cosmetics.json, die wir kennen (selbst geschrieben oder gelesen). */
    private static volatile long bekannteZeit = -1;

    /** Einmal beim Start: Hut-Ebene am Spieler-Renderer und Partikel anmelden. */
    public static void register() {
        //#if 26
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((typ, renderer, helfer, ctx) -> {
        //#else
        //$ net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((typ, renderer, helfer, ctx) -> {
        //#endif
            if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> spieler) {
                helfer.register(new CapeEbene(spieler));
                helfer.register(new ZubehoerEbene(spieler));
                helfer.register(new BiegeEbene(spieler));
            }
        });
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(s -> !capePhysik());
        CosmeticsSync.register();
        Emotes.register();
        LauncherVorschau.spaeter();
        LauncherKatalog.spaeter();
    }

    public static Path ordner() {
        return FabricLoader.getInstance().getConfigDir().resolve("vortex-client");
    }

    private static Path datei() { return ordner().resolve("cosmetics.json"); }

    public static Auswahl eigene() {
        if (eigene == null) laden();
        return eigene;
    }

    public static boolean partikelErstePerson() {
        if (eigene == null) laden();
        return partikelErstePerson;
    }

    private static synchronized void laden() {
        if (eigene != null) return;
        Auswahl a = Auswahl.LEER;
        try {
            if (Files.exists(datei())) {
                JsonObject o = JsonParser.parseString(Files.readString(datei(), StandardCharsets.UTF_8)).getAsJsonObject();
                a = new Auswahl(text(o, "cape"), text(o, "hat"), Zubehoer.auraFuer(text(o, "particles")),
                        o.has("particleDensity") ? Math.max(1, Math.min(3, o.get("particleDensity").getAsInt())) : 2,
                        text(o, "bandana"), text(o, "face"), text(o, "back"), text(o, "shield"), text(o, "neck"), text(o, "nametag"), text(o, "pet"));
                partikelErstePerson = o.has("particlesFirstPerson") && o.get("particlesFirstPerson").getAsBoolean();
                capePhysik = !o.has("capePhysics") || o.get("capePhysics").getAsBoolean();
                capeWellen = !o.has("capeWaves") || o.get("capeWaves").getAsBoolean();
                bandanaPhysik = !o.has("bandanaPhysics") || o.get("bandanaPhysics").getAsBoolean();
                namenZeigen = !o.has("nameEffects") || o.get("nameEffects").getAsBoolean();
                abzeichenZeigen = !o.has("badges") || o.get("badges").getAsBoolean();
                bekannteZeit = Files.getLastModifiedTime(datei()).toMillis();
                String gf = text(o, "othersBigAuras");
                grosseFremde = gf.equals("small") ? 1 : gf.equals("off") ? 2 : 0;
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("Cosmetics.laden", t);
        }
        eigene = a;
    }

    private static String text(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    /** Neue Auswahl speichern. Ein geaendertes Cape wird sofort neu geladen. */
    public static synchronized void speichern(Auswahl neu, boolean erstePerson) {
        Auswahl alt = eigene();
        eigene = neu;
        partikelErstePerson = erstePerson;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("cape", neu.cape());
            o.addProperty("hat", neu.hut());
            o.addProperty("particles", neu.partikel());
            o.addProperty("particleDensity", neu.dichte());
            o.addProperty("bandana", neu.bandana());
            o.addProperty("face", neu.gesicht());
            o.addProperty("back", neu.ruecken());
            o.addProperty("shield", neu.schild());
            o.addProperty("neck", neu.hals());
            o.addProperty("nametag", neu.namensstil());
            o.addProperty("pet", neu.pet());
            o.addProperty("particlesFirstPerson", erstePerson);
            o.addProperty("capePhysics", capePhysik);
            o.addProperty("capeWaves", capeWellen);
            o.addProperty("bandanaPhysics", bandanaPhysik);
            o.addProperty("nameEffects", namenZeigen);
            o.addProperty("badges", abzeichenZeigen);
            o.addProperty("othersBigAuras", grosseFremde == 1 ? "small" : grosseFremde == 2 ? "off" : "on");
            Files.createDirectories(ordner());
            Path tmp = datei().resolveSibling("cosmetics.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, datei(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            bekannteZeit = Files.getLastModifiedTime(datei()).toMillis();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("Cosmetics.speichern", t);
        }
        if (!alt.cape().equals(neu.cape())) ActiveCape.neuLaden();
        CosmeticsSync.geaendert();
        LauncherVorschau.spaeter();
    }

    /**
     * Seit 4.83: hat jemand anderes (der Launcher) cosmetics.json geaendert? Dann die Auswahl
     * neu laden -- ein neues Cape erscheint sofort, ohne Neustart. Wird regelmaessig aus dem
     * Client-Tick aufgerufen und kostet nur einen Blick auf die Aenderungszeit.
     */
    public static void vonAussen() {
        try {
            Path d = datei();
            if (!Files.exists(d)) return;
            long m = Files.getLastModifiedTime(d).toMillis();
            if (bekannteZeit < 0) { bekannteZeit = m; return; }
            if (m == bekannteZeit) return;
            Auswahl alt = eigene();
            synchronized (Cosmetics.class) {
                eigene = null;
                laden();
            }
            bekannteZeit = m;
            if (!alt.cape().equals(eigene().cape()) || EigenesCape.ID.equals(eigene().cape())) ActiveCape.neuLaden();
            CosmeticsSync.geaendert();
            LauncherVorschau.spaeter();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("Cosmetics.vonAussen", t);
        }
    }

    /** Realistische Capes (CapeEbene) statt der starren Vanilla-Platte -- gilt fuer alle Capes, die du siehst. */
    public static boolean capePhysik() {
        if (eigene == null) laden();
        return capePhysik;
    }

    public static void setzeCapePhysik(boolean an) {
        capePhysik = an;
        speichern(eigene(), partikelErstePerson());
    }

    public static boolean capeWellen() {
        if (eigene == null) laden();
        return capeWellen;
    }

    public static void setzeCapeWellen(boolean an) {
        capeWellen = an;
        speichern(eigene(), partikelErstePerson());
    }

    public static boolean namenZeigen() {
        if (eigene == null) laden();
        return namenZeigen;
    }

    public static void setzeNamenZeigen(boolean an) {
        namenZeigen = an;
        speichern(eigene(), partikelErstePerson());
    }

    /** [V] und [CREATOR] vor Namen zeigen (seit 4.95). */
    public static boolean abzeichenZeigen() {
        if (eigene == null) laden();
        return abzeichenZeigen;
    }

    public static void setzeAbzeichenZeigen(boolean an) {
        abzeichenZeigen = an;
        speichern(eigene(), partikelErstePerson());
    }

    /** Grosse Auren anderer Spieler: 0 = normal, 1 = klein, 2 = aus. */
    public static int grosseFremde() {
        if (eigene == null) laden();
        return grosseFremde;
    }

    public static void setzeGrosseFremde(int modus) {
        grosseFremde = Math.floorMod(modus, 3);
        speichern(eigene(), partikelErstePerson());
    }

    public static boolean bandanaPhysik() {
        if (eigene == null) laden();
        return bandanaPhysik;
    }

    public static void setzeBandanaPhysik(boolean an) {
        bandanaPhysik = an;
        speichern(eigene(), partikelErstePerson());
    }

    /** Alle bekannten Auswahlen anderer Spieler (fuer Chat und Tab-Liste). */
    public static Map<UUID, Auswahl> fremde() {
        return java.util.Collections.unmodifiableMap(FREMDE);
    }

    /** Auswahl anderer Spieler (vom Server). null = entfernen. */
    public static void setzeFremde(UUID spieler, Auswahl a) {
        if (a == null) FREMDE.remove(spieler); else FREMDE.put(spieler, a);
    }

    /** Anprobe im Cosmetics-Menue (seit 4.93): so sieht man ein Item vor dem Kauf an sich selbst. */
    private static volatile Auswahl anprobe;

    public static void setzeAnprobe(Auswahl a) { anprobe = a; }
    public static Auswahl anprobe() { return anprobe; }

    /** Was man selbst gerade traegt: die Anprobe, sonst die Auswahl ohne nicht gekaufte Teile. */
    public static Auswahl getragen() {
        Auswahl a = anprobe;
        if (a != null && !(Minecraft.getInstance().gui.screen() instanceof CosmeticsScreen)
                && !(Minecraft.getInstance().gui.screen() instanceof com.vortex.client.gui.TextEingabeScreen)) {
            anprobe = null;
            a = null;
            ActiveCape.neuLaden();
        }
        return a != null ? a : Shop.filtern(eigene());
    }

    /** Auswahl fuer einen Spieler: der eigene aus der Datei, andere vom Server. */
    public static Auswahl fuer(UUID spieler) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(spieler)) return getragen();
        return FREMDE.getOrDefault(spieler, Auswahl.LEER);
    }

    /** Auswahl fuer die Entity mit dieser Id (aus dem Renderzustand). */
    public static Auswahl fuerEntity(int id) {
        if (id == TitelFigur.ID) return getragen();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Auswahl.LEER;
        Entity e = mc.level.getEntity(id);
        return e instanceof Player p ? fuer(p.getUUID()) : Auswahl.LEER;
    }
}
