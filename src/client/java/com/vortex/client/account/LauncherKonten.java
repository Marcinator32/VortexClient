package com.vortex.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Konten aus dem Vortex Launcher (seit 4.86) -- ueber die Konto-Bruecke des Launchers
 * (nur 127.0.0.1, Schluessel aus shared/account-bridge.json, siehe kontobruecke.js).
 * Der Launcher erneuert beim Wechsel den Token selbst; im Spiel muss sich niemand anmelden.
 * Alle Aufrufe blockieren kurz -- nur aus einem eigenen Thread aufrufen.
 */
public final class LauncherKonten {
    private LauncherKonten() {}

    public record Konto(String id, String name, String uuid, byte[] gesicht, boolean spieltWoanders) {}
    public record Liste(String aktiv, List<Konto> konten) {}
    public record Sitzung(String name, String uuid, String token) {}

    /** Fehler mit einer Meldung, die man so anzeigen kann. */
    public static final class Fehler extends Exception {
        public Fehler(String meldung) { super(meldung); }
    }

    private static String launchId() { return com.vortex.client.core.LauncherBruecke.launchId(); }

    private static JsonObject anfrage(String pfad, String body) throws Fehler {
        try {
            return com.vortex.client.core.LauncherBruecke.anfrage(pfad, body, body == null ? 8 : 25, "The account could not be switched.");
        } catch (com.vortex.client.core.LauncherBruecke.Fehler e) {
            throw new Fehler(e.getMessage());
        }
    }

    private static String text(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    public static Liste liste() throws Fehler {
        JsonObject o = anfrage("/accounts?launch=" + launchId(), null);
        List<Konto> l = new ArrayList<>();
        JsonArray a = o.has("accounts") ? o.getAsJsonArray("accounts") : new JsonArray();
        for (JsonElement e : a) {
            JsonObject k = e.getAsJsonObject();
            byte[] bild = null;
            String face = text(k, "face");
            int komma = face.indexOf(',');
            if (face.startsWith("data:image/png;base64,") && komma > 0) {
                try { bild = Base64.getDecoder().decode(face.substring(komma + 1)); } catch (IllegalArgumentException ignored) { }
            }
            l.add(new Konto(text(k, "id"), text(k, "username"), text(k, "uuid"), bild,
                    k.has("playing") && k.get("playing").getAsBoolean()));
        }
        return new Liste(text(o, "active"), l);
    }

    public static Sitzung wechseln(String id) throws Fehler {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        body.addProperty("launch", launchId());
        JsonObject o = anfrage("/switch", body.toString());
        String token = text(o, "accessToken");
        if (token.isEmpty()) throw new Fehler("The account could not be switched.");
        return new Sitzung(text(o, "username"), text(o, "uuid"), token);
    }
}
