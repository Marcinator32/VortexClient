package com.vortex.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vortex.client.social.Social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Coins und Shop (seit 4.93) -- der Stand kommt vom Freunde-Server, der allein entscheidet,
 * wie viele Coins jemand hat und was ihm gehoert. Hier wird nur angezeigt, gekauft
 * (Anfrage mit eigener ID, damit ein zweiter Klick nicht doppelt abbucht) und gefiltert:
 * was einem nicht gehoert, traegt man auch selbst nicht (Anprobe im Cosmetics-Menue ausgenommen).
 *
 * Kennt der Server den Shop noch nicht (alter Freunde-Server) oder ist keiner verbunden,
 * bleibt alles frei wie bisher.
 */
public final class Shop {
    private Shop() {}

    public record Item(String id, String cat, String key, String name, int preis, String seltenheit,
                       boolean verschenkbar, boolean limitiert, boolean imVerkauf, boolean besitzt,
                       String beschreibung, boolean featured, boolean creatorOnly, long neu, long bis) {}

    public record Buchung(String art, int betrag, String item, String anderer, long zeit) {}
    public record Tag(String tag, int minuten) {}
    public record Verlauf(List<Buchung> buchungen, List<Tag> tage) {}

    private static volatile boolean aktiv;
    private static volatile boolean admin;
    private static volatile boolean creator;
    private static volatile String creatorStatus = "none", unterstuetzt = "";
    private static volatile int proMinute = 1;
    private static volatile int guthaben, verdient, minuten;
    private static volatile Map<String, Item> items = Map.of();
    private static volatile int version;
    private static volatile Object verbindung;
    private static volatile boolean laedt;

    public static boolean aktiv() { return aktiv; }
    public static boolean admin() { return aktiv && admin; }
    /** Akzeptierter Vortex Creator (sagt der Server). */
    public static boolean creator() { return aktiv && creator; }
    public static String creatorStatus() { return creatorStatus; }
    /** Unterstuetzter Creator-Code oder "". */
    public static String unterstuetzt() { return unterstuetzt; }
    public static int proMinute() { return proMinute; }
    public static int guthaben() { return guthaben; }
    public static int verdient() { return verdient; }
    public static int minuten() { return minuten; }
    public static int version() { return version; }
    public static Map<String, Item> items() { return items; }

    /** Seltenheit -> Farbe (ARGB) fuer Text und Rahmen. */
    public static int farbe(String seltenheit) {
        return switch (seltenheit == null ? "" : seltenheit) {
            case "uncommon" -> 0xFF4ADE80;
            case "rare" -> 0xFF38BDF8;
            case "epic" -> 0xFFC084FC;
            case "legendary" -> 0xFFFBBF24;
            case "mythic" -> 0xFFF472B6;
            default -> 0xFFB8B4C8;
        };
    }

    public static String seltenheitName(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Item-ID im Shop, z. B. "hat:crown" (Stil-Zusaetze wie "--s" fallen weg). */
    public static String id(String cat, String key) {
        String k = key == null ? "" : key;
        int i = k.indexOf("--");
        if (i >= 0) k = k.substring(0, i);
        return cat + ":" + k;
    }

    public static Item item(String cat, String key) {
        if (key == null || key.isEmpty()) return null;
        return items.get(id(cat, key));
    }

    /** Darf man das tragen? Ohne Shop, fuer Unbekanntes und Gratis-Items immer. */
    public static boolean besitzt(String cat, String key) {
        if (!aktiv || key == null || key.isEmpty()) return true;
        Item it = items.get(id(cat, key));
        return it == null || it.preis() <= 0 || it.besitzt();
    }

    /** Kontowechsel: alles vergessen, beim naechsten Verbinden neu laden. */
    public static void zuruecksetzen() {
        aktiv = false;
        admin = false;
        creator = false;
        creatorStatus = "none";
        unterstuetzt = "";
        items = Map.of();
        guthaben = verdient = minuten = 0;
        verbindung = null;
        version++;
    }

    /** Alle zwei Sekunden aus dem Client-Tick: nach dem (Wieder-)Verbinden den Stand holen. */
    public static void tick() {
        if (Social.conn() != Social.Conn.ONLINE) {
            if (Social.conn() != Social.Conn.CONNECTING) verbindung = null;
            return;
        }
        if (verbindung != Social.data() && !laedt) {
            verbindung = Social.data();
            laden();
        }
    }

    /** Stand neu vom Server holen. */
    public static void laden() {
        laedt = true;
        Social.request("shop.state", new JsonObject()).whenComplete((r, e) -> {
            laedt = false;
            if (e != null) {
                if (String.valueOf(Social.errorOf(e)).contains("Unknown request")) { aktiv = false; version++; }
                return;
            }
            uebernehmen(r);
        });
    }

    /** Ereignis "shop" vom Server: neues Guthaben, neues Item oder geaenderter Katalog. */
    public static void ereignis(JsonObject d) {
        if (d.has("balance")) guthaben = d.get("balance").getAsInt();
        if (d.has("earned")) verdient = d.get("earned").getAsInt();
        if (d.has("minutes")) minuten = d.get("minutes").getAsInt();
        if (d.has("owned") || d.has("itemsChanged")) laden();
        if (d.has("gift") && d.get("gift").isJsonObject()) {
            JsonObject g = d.getAsJsonObject("gift");
            String von = Social.str(g, "from"), was = Social.str(g, "item");
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§d[Vortex] §f" + von + " §7gifted you §f" + was + "§7!"));
            } catch (Throwable ignored) { }
        }
        version++;
    }

    static void uebernehmen(JsonObject r) {
        if (r == null) return;
        Map<String, Item> neu = new LinkedHashMap<>();
        JsonArray a = r.has("items") && r.get("items").isJsonArray() ? r.getAsJsonArray("items") : new JsonArray();
        for (JsonElement e : a) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            Item it = new Item(Social.str(o, "id"), Social.str(o, "cat"), Social.str(o, "key"), Social.str(o, "name"),
                    o.has("price") ? o.get("price").getAsInt() : 0, Social.str(o, "rarity"),
                    !o.has("giftable") || o.get("giftable").getAsBoolean(), o.has("limited") && o.get("limited").getAsBoolean(),
                    !o.has("forSale") || o.get("forSale").getAsBoolean(), o.has("owned") && o.get("owned").getAsBoolean(),
                    Social.str(o, "description"), o.has("featured") && o.get("featured").getAsBoolean(),
                    o.has("creatorOnly") && o.get("creatorOnly").getAsBoolean(),
                    o.has("created") ? o.get("created").getAsLong() : 0L, o.has("ends") ? o.get("ends").getAsLong() : 0L);
            neu.put(it.id(), it);
        }
        items = Collections.unmodifiableMap(neu);
        guthaben = r.has("balance") ? r.get("balance").getAsInt() : 0;
        verdient = r.has("earned") ? r.get("earned").getAsInt() : 0;
        minuten = r.has("minutes") ? r.get("minutes").getAsInt() : 0;
        admin = r.has("admin") && r.get("admin").getAsBoolean();
        creator = r.has("creator") && r.get("creator").getAsBoolean();
        creatorStatus = r.has("creatorStatus") ? Social.str(r, "creatorStatus") : "none";
        unterstuetzt = Social.str(r, "supporting");
        proMinute = r.has("coinsPerMinute") ? r.get("coinsPerMinute").getAsInt() : 1;
        aktiv = r.has("enabled") && r.get("enabled").getAsBoolean();
        version++;
        try { ActiveCape.neuLaden(); } catch (Throwable ignored) { }
    }

    private static String anfrageId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /** Kaufen. Ergebnis: null = gekauft, sonst die Fehlermeldung. */
    public static CompletableFuture<String> kaufen(String itemId) {
        return antwort(Social.request("shop.buy", Social.args("item", itemId, "requestId", anfrageId())));
    }

    /** Verschenken an einen Spieler (Name). Ergebnis: null = geschenkt, sonst die Fehlermeldung. */
    public static CompletableFuture<String> schenken(String itemId, String an) {
        return antwort(Social.request("shop.gift", Social.args("item", itemId, "to", an, "requestId", anfrageId())));
    }

    private static CompletableFuture<String> antwort(CompletableFuture<JsonObject> f) {
        return f.handle((r, e) -> {
            if (e != null) return String.valueOf(Social.errorOf(e));
            uebernehmen(r);
            return null;
        });
    }

    public static CompletableFuture<Verlauf> verlauf() {
        return Social.request("shop.history", new JsonObject()).thenApply(r -> {
            List<Buchung> b = new ArrayList<>();
            if (r.has("tx")) for (JsonElement e : r.getAsJsonArray("tx")) {
                JsonObject o = e.getAsJsonObject();
                b.add(new Buchung(Social.str(o, "kind"), o.get("amount").getAsInt(), Social.str(o, "itemName"), Social.str(o, "other"), o.get("at").getAsLong()));
            }
            List<Tag> t = new ArrayList<>();
            if (r.has("days")) for (JsonElement e : r.getAsJsonArray("days")) {
                JsonObject o = e.getAsJsonObject();
                t.add(new Tag(Social.str(o, "day"), o.get("minutes").getAsInt()));
            }
            return new Verlauf(b, t);
        });
    }

    private static volatile Cosmetics.Auswahl gefiltertVon, gefiltert;
    private static volatile int gefiltertVersion = -1;

    /** Auswahl ohne die Teile, die einem (noch) nicht gehoeren. Zwischengespeichert. */
    public static Cosmetics.Auswahl filtern(Cosmetics.Auswahl a) {
        if (!aktiv || a == null) return a;
        if (a == gefiltertVon && gefiltertVersion == version) return gefiltert;
        Cosmetics.Auswahl b = new Cosmetics.Auswahl(
                besitzt("cape", a.cape()) ? a.cape() : "",
                besitzt("hat", a.hut()) ? a.hut() : "",
                besitzt("aura", a.partikel()) ? a.partikel() : "",
                a.dichte(),
                besitzt("bandana", a.bandana()) ? a.bandana() : "",
                besitzt("face", a.gesicht()) ? a.gesicht() : "",
                besitzt("back", a.ruecken()) ? a.ruecken() : "",
                besitzt("shield", a.schild()) ? a.schild() : "",
                besitzt("neck", a.hals()) ? a.hals() : "",
                besitzt("nametag", a.namensstil()) ? a.namensstil() : "",
                besitzt("pet", a.pet()) ? a.pet() : "");
        gefiltertVon = a;
        gefiltert = b;
        gefiltertVersion = version;
        return b;
    }

    /** Admin: alle Items (auch versteckte) als JSON vom Server. */
    public static CompletableFuture<JsonObject> adminItems() { return Social.request("shop.admin.items", new JsonObject()); }

    public static CompletableFuture<String> adminSpeichern(JsonObject item) {
        return Social.request("admin.item.save", item).handle((r, e) -> e != null ? String.valueOf(Social.errorOf(e)) : null);
    }

    public static CompletableFuture<String> adminLoeschen(String id, String grund) {
        return Social.request("admin.item.delete", Social.args("id", id, "reason", grund)).handle((r, e) -> e != null ? String.valueOf(Social.errorOf(e)) : null);
    }

    /** Coins geben oder nehmen; die Anfrage-ID sorgt dafuer, dass ein zweiter Klick nicht doppelt bucht. */
    public static CompletableFuture<String> adminCoins(String an, int betrag, String grund, String anfrage) {
        return Social.request("admin.coins", Social.args("name", an, "amount", betrag, "reason", grund, "requestId", anfrage))
                .handle((r, e) -> e != null ? String.valueOf(Social.errorOf(e)) : null);
    }

    /** Creator-Status und Code eines Spielers setzen (nur Admins; der Server prueft das). */
    public static CompletableFuture<String> adminCreator(String name, String status, String code, String grund) {
        JsonObject a = Social.args("name", name, "reason", grund);
        if (status != null) a.addProperty("status", status);
        if (code != null) a.addProperty("code", code);
        return Social.request("admin.creator.set", a).handle((r, e) -> e != null ? String.valueOf(Social.errorOf(e)) : null);
    }

    public static String neueAnfrageId() {
        return anfrageId();
    }
}
