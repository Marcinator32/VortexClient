package com.vortex.client.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Bruecke zum Vortex Launcher (seit 4.91, vorher nur in LauncherKonten): nur 127.0.0.1,
 * Port und Schluessel stehen in shared/account-bridge.json (der Launcher legt die Datei bei
 * jedem Start neu an, siehe kontobruecke.js). Konten wechseln, Welten hosten, Plugins.
 * Alle Aufrufe blockieren kurz -- nur aus einem eigenen Thread aufrufen.
 */
public final class LauncherBruecke {
    private LauncherBruecke() {}

    /** Fehler mit einer Meldung, die man so anzeigen kann. */
    public static final class Fehler extends Exception {
        public Fehler(String meldung) { super(meldung); }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** Start-ID dieses Spiels beim Launcher (leer = nicht vom Launcher gestartet). */
    public static String launchId() { return System.getProperty("vortex.launch.id", ""); }

    /** Vom Launcher gestartet und der Launcher laeuft gerade? */
    public static boolean da() {
        String dir = System.getProperty("vortex.shared.dir", "");
        return !dir.isEmpty() && !launchId().isEmpty() && Files.exists(Path.of(dir, "account-bridge.json"));
    }

    private static JsonObject datei() throws Fehler {
        String dir = System.getProperty("vortex.shared.dir", "");
        if (dir.isEmpty()) throw new Fehler("Start Minecraft from the Vortex launcher to use this.");
        Path f = Path.of(dir, "account-bridge.json");
        try {
            if (!Files.exists(f)) throw new Fehler("Open the Vortex launcher and try again.");
            return JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Fehler e) {
            throw e;
        } catch (Exception e) {
            throw new Fehler("Open the Vortex launcher and try again.");
        }
    }

    /**
     * Anfrage an den Launcher. body == null: GET, sonst POST mit JSON.
     * standardFehler: Meldung, wenn der Launcher keine eigene schickt.
     */
    public static JsonObject anfrage(String pfad, String body, int sekunden, String standardFehler) throws Fehler {
        JsonObject b = datei();
        int port = b.get("port").getAsInt();
        String key = b.get("key").getAsString();
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pfad))
                .timeout(Duration.ofSeconds(sekunden))
                .header("X-Vortex-Key", key);
        r = body == null ? r.GET() : r.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> res;
        try {
            res = HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new Fehler("The Vortex launcher is not reachable. Open it and try again.");
        }
        JsonObject o;
        try {
            o = JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (Exception e) {
            throw new Fehler("The Vortex launcher sent an unexpected answer.");
        }
        if (res.statusCode() != 200) {
            throw new Fehler(o.has("error") && o.get("error").isJsonPrimitive() ? o.get("error").getAsString() : standardFehler);
        }
        return o;
    }
}
