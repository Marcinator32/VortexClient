package com.vortex.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vortex.client.social.Social;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Vortex Creator Program (seit 4.95): eigener Status, Code, Profil und die Liste der Creator.
 * Alles kommt vom Freunde-Server; hier wird nur geladen, angezeigt und weitergereicht.
 */
public final class Creators {
    private Creators() {}

    public static final String DISCORD = "https://discord.gg/EQYZTRRp6J";

    public record Eintrag(String uuid, String name, String code, String bio, long seit, int unterstuetzer) {}

    public record Stand(String status, String code, boolean codeAktiv, String bio, long beworben, long angenommen, int unterstuetzer,
                        String unterstuetztCode, String unterstuetztName, long wechselAb, int anteil, int wartetage, String discord,
                        List<String> creatorItems) {
        static final Stand LEER = new Stand("none", "", false, "", 0, 0, 0, "", "", 0, 5, 7, DISCORD, List.of());
    }

    private static volatile Stand stand = Stand.LEER;
    private static volatile List<Eintrag> liste = List.of();
    private static volatile boolean geladen;
    private static volatile String fehler;
    private static volatile int version;

    public static Stand stand() { return stand; }
    public static List<Eintrag> liste() { return liste; }
    public static boolean geladen() { return geladen; }
    public static String fehler() { return fehler; }
    public static int version() { return version; }

    /** Status und Liste neu holen. */
    public static void neuLaden() {
        Social.request("creator.me", new JsonObject()).whenComplete((r, e) -> {
            if (e != null) { fehler = String.valueOf(Social.errorOf(e)); version++; return; }
            fehler = null;
            stand = lesen(r);
            geladen = true;
            version++;
        });
        Social.request("creator.list", new JsonObject()).whenComplete((r, e) -> {
            if (e != null || r == null) return;
            List<Eintrag> l = new ArrayList<>();
            JsonArray a = r.has("creators") ? r.getAsJsonArray("creators") : new JsonArray();
            for (JsonElement el : a) {
                JsonObject o = el.getAsJsonObject();
                l.add(new Eintrag(Social.str(o, "uuid"), Social.str(o, "name"), Social.str(o, "code"), Social.str(o, "bio"),
                        o.has("since") ? o.get("since").getAsLong() : 0L, o.has("supporters") ? o.get("supporters").getAsInt() : 0));
            }
            liste = List.copyOf(l);
            version++;
        });
    }

    private static Stand lesen(JsonObject r) {
        String uc = "", un = "";
        long ab = 0;
        if (r.has("supporting") && r.get("supporting").isJsonObject()) {
            JsonObject s = r.getAsJsonObject("supporting");
            uc = Social.str(s, "code");
            un = Social.str(s, "name");
            ab = s.has("changeAt") ? s.get("changeAt").getAsLong() : 0L;
        }
        List<String> items = new ArrayList<>();
        if (r.has("profile") && r.get("profile").isJsonObject()) {
            JsonObject p = r.getAsJsonObject("profile");
            if (p.has("creatorItems")) for (JsonElement el : p.getAsJsonArray("creatorItems")) items.add(Social.str(el.getAsJsonObject(), "name"));
        }
        return new Stand(Social.str(r, "status"), Social.str(r, "code"), r.has("codeActive") && r.get("codeActive").getAsBoolean(),
                Social.str(r, "bio"), zahl(r, "applied"), zahl(r, "accepted"), (int) zahl(r, "supporters"), uc, un, ab,
                (int) zahl(r, "rewardPercent"), (int) zahl(r, "cooldownDays"), r.has("discord") ? Social.str(r, "discord") : DISCORD, items);
    }

    private static long zahl(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsLong() : 0L;
    }

    private static CompletableFuture<String> antwort(CompletableFuture<JsonObject> f) {
        return f.handle((r, e) -> {
            if (e != null) return String.valueOf(Social.errorOf(e));
            neuLaden();
            Shop.laden();
            return null;
        });
    }

    /** "Ich habe mich auf Discord beworben" -- Status pending. Ergebnis: null = ok, sonst Fehler. */
    public static CompletableFuture<String> bewerben(String discordName) {
        return antwort(Social.request("creator.apply", Social.args("discord", discordName == null ? "" : discordName)));
    }

    /** Creator-Code unterstuetzen ("" = keinen mehr). */
    public static CompletableFuture<String> unterstuetzen(String code) {
        return antwort(Social.request("creator.support", Social.args("code", code == null ? "" : code)));
    }

    /** Eigene Beschreibung (nur akzeptierte Creator). */
    public static CompletableFuture<String> beschreiben(String bio) {
        return antwort(Social.request("creator.edit", Social.args("bio", bio == null ? "" : bio)));
    }

    public static String statusName(String s) {
        return switch (s == null ? "" : s) {
            case "pending" -> "Pending";
            case "accepted" -> "Accepted";
            case "rejected" -> "Rejected";
            case "suspended" -> "Suspended";
            default -> "Not applied";
        };
    }

    public static int statusFarbe(String s) {
        return switch (s == null ? "" : s) {
            case "pending" -> 0xFFFBBF24;
            case "accepted" -> 0xFF4ADE80;
            case "rejected" -> 0xFFF87171;
            case "suspended" -> 0xFFFB923C;
            default -> 0xFF9CA3AF;
        };
    }
}
