package com.vortex.client.waypoint;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Welten der Waypoints (seit 4.94), aufgebaut wie bei Xaero's Minimap.
 *
 * ORT ist, wo man spielt: eine Einzelspieler-Welt (ihr Ordner, eindeutig) oder
 * eine Server-Adresse. Auf einer Adresse kann es mehrere WELTEN geben -- Lobby
 * und Survival hinter einem Proxy, Multiverse-Welten, ein neuer Map-Reset. Jede
 * Welt hat eigene Waypoints; die Dimension (Oberwelt, Nether, End) steht am
 * Waypoint selbst.
 *
 * WELCHE WELT gerade laeuft, verraet der Server nicht direkt. Erkannt wird sie an
 * ihrer Signatur: dem (gehashten) Seed aus dem Login und dem Welt-Spawn. Beides
 * bleibt fuer eine Welt gleich und unterscheidet sich zwischen Welten fast immer.
 * Passt nichts, wird eine neue Welt angelegt und im Chat gemeldet. Waehlt man im
 * Menue eine andere Welt aus, merkt sie sich die Signatur -- beim naechsten Mal
 * stimmt es dann von selbst. Server, die den Seed bei jedem Beitritt wuerfeln,
 * erkennt das daran, dass dieselbe Welt zwei Seeds mit gleichem Spawn bekommt;
 * ab dann zaehlt dort nur noch der Spawn.
 */
public final class WaypointWorlds {

    /** Eine Welt mit ihren Waypoints. */
    public static final class Welt {
        public final String id;
        public String ort;
        public String name;
        /** Seed wird gewuerfelt: nur der Spawn zaehlt. */
        public boolean nurSpawn;
        public long zuletzt;
        /** Signaturen "seed@x,y,z" (Teile koennen leer sein). */
        public final Set<String> signaturen = new LinkedHashSet<>();

        Welt(String id, String ort, String name) {
            this.id = id;
            this.ort = ort;
            this.name = name;
        }
    }

    /** Wo man gerade ist: Welt und Dimension. */
    public record Hier(Welt welt, String dim) {
        public String schluessel() { return welt.id + "|" + dim; }
    }

    /** Ort fuer Waypoints, die keinem Ort zugeordnet sind (aus alten Versionen). */
    public static final String OHNE_ORT = "?";

    private static final List<Welt> WELTEN = new ArrayList<>();
    private static final int MAX_SIGNATUREN = 16;
    private static final long WARTEN_MS = 3000L;
    private static final int SPAWN_NAEHE = 32;

    private static Object stufe;
    private static long stufeSeit;
    private static Welt entschieden;
    private static String entschiedenOrt;
    private static String entschiedenSeed;
    private static String meldung;
    private static boolean spawnFertig;
    private static Hier letztes;

    private WaypointWorlds() {}

    public static synchronized List<Welt> alle() {
        return new ArrayList<>(WELTEN);
    }

    public static synchronized Welt finde(String id) {
        if (id == null) return null;
        for (Welt w : WELTEN) if (w.id.equals(id)) return w;
        return null;
    }

    /** Alle Welten eines Ortes, zuletzt benutzte zuerst. */
    public static synchronized List<Welt> amOrt(String ort) {
        List<Welt> out = new ArrayList<>();
        for (Welt w : WELTEN) if (w.ort.equals(ort)) out.add(w);
        out.sort((a, b) -> Long.compare(b.zuletzt, a.zuletzt));
        return out;
    }

    /** Alle Orte, an denen es Welten gibt. */
    public static synchronized List<String> orte() {
        List<String> out = new ArrayList<>();
        for (Welt w : WELTEN) if (!out.contains(w.ort)) out.add(w.ort);
        return out;
    }

    /** Lesbarer Name eines Ortes: Server-Adresse oder Name des Welt-Ordners. */
    public static String ortName(String ort) {
        if (ort == null || OHNE_ORT.equals(ort)) return "Unassigned";
        if (ort.startsWith("sp:")) return ort.substring(3);
        if (ort.startsWith("mp:")) return ort.substring(3);
        return ort;
    }

    public static boolean einzelspieler(String ort) {
        return ort != null && ort.startsWith("sp:");
    }

    /** Kurzer Text fuer eine Welt: "server.net · World 2" bzw. nur der Ordner. */
    public static String anzeige(Welt w) {
        if (w == null) return "Detecting world...";
        if (einzelspieler(w.ort)) return ortName(w.ort);
        return ortName(w.ort) + " · " + w.name;
    }

    /** Lesbarer Text zu einem Schluessel "welt|dimension". */
    public static String anzeigeSchluessel(String schluessel) {
        if (schluessel == null) return "";
        int i = schluessel.indexOf('|');
        Welt w = finde(i < 0 ? schluessel : schluessel.substring(0, i));
        String dim = i < 0 ? "" : schluessel.substring(i + 1);
        String d = dimName(dim);
        return (w == null ? "?" : anzeige(w)) + (d.isEmpty() ? "" : " / " + d);
    }

    /** "minecraft:the_nether" -> "Nether". */
    public static String dimName(String dim) {
        if (dim == null || dim.isEmpty()) return "";
        if (dim.endsWith(":overworld")) return "Overworld";
        if (dim.endsWith(":the_nether")) return "Nether";
        if (dim.endsWith(":the_end")) return "End";
        int i = dim.indexOf(':');
        String n = i < 0 ? dim : dim.substring(i + 1);
        return n.isEmpty() ? dim : Character.toUpperCase(n.charAt(0)) + n.substring(1).replace('_', ' ');
    }

    /* ---------------------------------------------------------------- Erkennung */

    /** Ort, an dem man gerade spielt, oder null ohne Welt. */
    public static String aktuellerOrt(net.minecraft.client.Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        try {
            if (mc.isLocalServer()) {
                var server = mc.getSingleplayerServer();
                if (server != null) {
                    java.nio.file.Path p = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .toAbsolutePath().normalize();
                    if (p.getFileName() != null) return "sp:" + sauber(p.getFileName().toString());
                }
                return "sp:" + sauber(levelName(mc));
            }
            var entry = mc.getCurrentServer();
            if (entry != null && entry.ip != null) return "mp:" + adresse(entry.ip);
            return "mp:unknown";
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("WaypointWorlds.ort", e);
            return "mp:unknown";
        }
    }

    private static String levelName(net.minecraft.client.Minecraft mc) {
        try {
            var server = mc.getSingleplayerServer();
            if (server != null && server.getWorldData() != null) return server.getWorldData().getLevelName();
        } catch (Throwable ignored) { }
        return "Singleplayer";
    }

    /** Adresse vereinheitlichen: klein, ohne Standard-Port, ohne Punkt am Ende. */
    public static String adresse(String ip) {
        String a = ip == null ? "" : ip.trim().toLowerCase(Locale.ROOT);
        if (a.endsWith(":25565")) a = a.substring(0, a.length() - 6);
        while (a.endsWith(".")) a = a.substring(0, a.length() - 1);
        return sauber(a.isEmpty() ? "unknown" : a);
    }

    private static String sauber(String s) {
        return s.replace('|', '_').replace(';', '_').replace('%', '_');
    }

    /**
     * Wo man gerade ist.
     *
     * @param erzwingen true = sofort entscheiden, auch wenn der Spawn noch fehlt
     *                  (beim Anlegen eines Waypoints); sonst null, solange die
     *                  Welt noch erkannt wird
     */
    public static synchronized Hier hier(net.minecraft.client.Minecraft mc, boolean erzwingen) {
        if (mc == null || mc.level == null) return null;
        String dim = com.vortex.client.hud.WaypointRenderer.currentDimension(mc);
        if (dim == null) dim = "minecraft:overworld";
        Object level = mc.level;
        if (level != stufe) {
            stufe = level;
            stufeSeit = System.currentTimeMillis();
            spawnFertig = false;
            String ort = aktuellerOrt(mc);
            String seed = seed(mc);
            boolean gleicheWelt = entschieden != null && ort != null && ort.equals(entschiedenOrt)
                    && (einzelspieler(ort) || (seed != null && seed.equals(entschiedenSeed)));
            if (!gleicheWelt) entschieden = null;
        }
        if (entschieden == null || !WELTEN.contains(entschieden)) {
            entschieden = null;
            String ort = aktuellerOrt(mc);
            if (ort == null) return null;
            String seed = seed(mc);
            String spawn = spawn(mc);
            boolean bereit = einzelspieler(ort) || spawn != null || erzwingen
                    || System.currentTimeMillis() - stufeSeit > WARTEN_MS;
            if (!bereit) return null;
            Welt w = zuordnen(ort, seed, spawn, serverName(mc), levelName(mc));
            entschieden = w;
            entschiedenOrt = ort;
            entschiedenSeed = seed;
            spawnFertig = spawn != null;
        } else if (!spawnFertig) {
            if (einzelspieler(entschieden.ort)) {
                spawnFertig = true;
            } else {
                String spawn = spawn(mc);
                if (spawn != null) {
                    merken(entschieden, entschiedenSeed, spawn);
                    spawnFertig = true;
                }
            }
        }
        entschieden.zuletzt = System.currentTimeMillis();
        if (letztes == null || letztes.welt() != entschieden || !letztes.dim().equals(dim)) letztes = new Hier(entschieden, dim);
        return letztes;
    }

    /** Neue Welt bemerkt? Einmal abholen, fuer die Chat-Meldung. */
    public static synchronized String meldungAbholen() {
        String m = meldung;
        meldung = null;
        return m;
    }

    /** Beim Verlassen eines Servers: neu erkennen. */
    public static synchronized void vergessen() {
        stufe = null;
        entschieden = null;
    }

    /**
     * Der Kern: passende Welt am Ort suchen, sonst eine neue anlegen.
     * Paketsichtbar fuer den Test.
     */
    static synchronized Welt zuordnen(String ort, String seed, String spawn, String serverName, String levelName) {
        if (einzelspieler(ort)) {
            List<Welt> da = amOrt(ort);
            if (!da.isEmpty()) return da.get(0);
            Welt alt = levelName == null ? null : altEinzel(levelName);
            if (alt != null) {
                alt.ort = ort;
                return alt;
            }
            return neu(ort, ortName(ort));
        }
        List<Welt> da = amOrt(ort);
        String sig = signatur(seed, spawn);

        for (Welt w : da) if (w.signaturen.contains(sig)) return w;
        if (seed != null) {
            for (Welt w : da) {
                if (w.nurSpawn) continue;
                for (String s : w.signaturen) if (seed.equals(seedVon(s))) return merken(w, seed, spawn);
            }
        }
        if (spawn != null) {
            for (Welt w : da) {
                for (String s : w.signaturen) {
                    String sp = spawnVon(s);
                    if (sp == null || !nah(sp, spawn)) continue;
                    if (w.nurSpawn || seed == null || seedVon(s) == null) return merken(w, seed, spawn);
                }
            }
        }
        if (seed == null && spawn == null && !da.isEmpty()) return da.get(0);
        for (Welt w : da) {
            if (w.signaturen.isEmpty()) return merken(w, seed, spawn);
        }
        String name;
        if (da.isEmpty()) name = serverName == null || serverName.isBlank() ? "Main" : sauber(serverName.trim());
        else name = "World " + (da.size() + 1);
        Welt w = merken(neu(ort, name), seed, spawn);
        if (!da.isEmpty()) {
            meldung = "New world detected: \"" + name + "\". Wrong? Pick the right one in the waypoint menu.";
        }
        return w;
    }

    private static Welt altEinzel(String levelName) {
        String ort = "sp:" + sauber(levelName);
        List<Welt> da = amOrt(ort);
        return da.isEmpty() ? null : da.get(0);
    }

    private static Welt merken(Welt w, String seed, String spawn) {
        if (seed == null && spawn == null) return w;
        String sig = signatur(seed, spawn);
        if (spawn != null) w.signaturen.remove(signatur(seed, null));
        if (w.signaturen.add(sig)) {
            while (w.signaturen.size() > MAX_SIGNATUREN) {
                w.signaturen.remove(w.signaturen.iterator().next());
            }
        }
        return w;
    }

    static String signatur(String seed, String spawn) {
        return (seed == null ? "" : seed) + "@" + (spawn == null ? "" : spawn);
    }

    static String seedVon(String sig) {
        int i = sig.indexOf('@');
        String s = i < 0 ? sig : sig.substring(0, i);
        return s.isEmpty() ? null : s;
    }

    static String spawnVon(String sig) {
        int i = sig.indexOf('@');
        String s = i < 0 ? "" : sig.substring(i + 1);
        return s.isEmpty() ? null : s;
    }

    static boolean nah(String a, String b) {
        try {
            String[] p = a.split(","), q = b.split(",");
            if (p.length != 3 || q.length != 3) return a.equals(b);
            long dx = Long.parseLong(p[0]) - Long.parseLong(q[0]);
            long dz = Long.parseLong(p[2]) - Long.parseLong(q[2]);
            return dx * dx + dz * dz <= (long) SPAWN_NAEHE * SPAWN_NAEHE;
        } catch (NumberFormatException e) {
            return a.equals(b);
        }
    }

    private static Welt neu(String ort, String name) {
        String id;
        do {
            id = "w" + String.format(Locale.ROOT, "%016x", Double.doubleToLongBits(Math.random()) ^ System.nanoTime()).substring(8);
        } while (finde(id) != null);
        Welt w = new Welt(id, ort, name);
        w.zuletzt = System.currentTimeMillis();
        WELTEN.add(w);
        return w;
    }

    private static String serverName(net.minecraft.client.Minecraft mc) {
        try {
            var e = mc.getCurrentServer();
            return e == null ? null : e.name;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Gehashter Seed aus dem Login, oder null wenn unbekannt bzw. 0. */
    private static String seed(net.minecraft.client.Minecraft mc) {
        try {
            var access = mc.level.getBiomeManager();
            if (access == null) return null;
            long s = ((com.vortex.client.mixin.client.BiomeAccessSeedAccessor) (Object) access).vortex$getSeed();
            if (s == 0L) return null;
            String h = Long.toHexString(s);
            return h.substring(0, Math.min(8, h.length()));
        } catch (Throwable e) {
            return null;
        }
    }

    /** Welt-Spawn "x,y,z", oder null solange der Server ihn nicht geschickt hat. */
    private static String spawn(net.minecraft.client.Minecraft mc) {
        try {
            var p = mc.level.getLevelData().getRespawnData().pos();
            if (p == null || (p.getX() == 0 && p.getY() == 0 && p.getZ() == 0)) return null;
            return p.getX() + "," + p.getY() + "," + p.getZ();
        } catch (Throwable e) {
            return null;
        }
    }

    /* ---------------------------------------------------------------- Bedienung */

    /**
     * Im Menue gewaehlt: Hier ist ab jetzt diese Welt. Die aktuelle Signatur
     * wandert zu ihr, damit es beim naechsten Beitritt von selbst stimmt.
     */
    public static synchronized void hierIst(net.minecraft.client.Minecraft mc, Welt w) {
        if (w == null || mc == null || mc.level == null) return;
        String ort = aktuellerOrt(mc);
        if (ort == null || !ort.equals(w.ort)) return;
        String seed = seed(mc), spawn = spawn(mc);
        String sig = signatur(seed, spawn);
        for (Welt andere : amOrt(ort)) {
            if (andere != w) {
                andere.signaturen.remove(sig);
                andere.signaturen.remove(signatur(seed, null));
            }
        }
        if (seed != null && spawn != null) {
            for (String s : w.signaturen) {
                String sp = spawnVon(s), se = seedVon(s);
                if (sp != null && se != null && !se.equals(seed) && nah(sp, spawn)) w.nurSpawn = true;
            }
        }
        merken(w, seed, spawn);
        entschieden = w;
        entschiedenOrt = ort;
        entschiedenSeed = seed;
        spawnFertig = spawn != null;
        stufe = mc.level;
        w.zuletzt = System.currentTimeMillis();
        com.vortex.client.hud.WaypointRenderer.invalidateWorldKey();
    }

    /** Neue, leere Welt am aktuellen Ort, sofort ausgewaehlt. */
    public static synchronized Welt neueHier(net.minecraft.client.Minecraft mc) {
        String ort = aktuellerOrt(mc);
        if (ort == null) return null;
        Welt w = neu(ort, "World " + (amOrt(ort).size() + 1));
        hierIst(mc, w);
        return w;
    }

    public static synchronized void umbenennen(Welt w, String name) {
        if (w == null || name == null || name.isBlank()) return;
        w.name = sauber(name.trim());
    }

    /** Welt samt ihrer Waypoints loeschen. */
    public static synchronized void loeschen(Welt w) {
        if (w == null) return;
        WaypointManager.alleLoeschenIn(w.id);
        WELTEN.remove(w);
        if (entschieden == w) entschieden = null;
        com.vortex.client.hud.WaypointRenderer.invalidateWorldKey();
    }

    /** Leere Welten ohne Signatur aufraeumen (nach dem Verschieben aller Waypoints). */
    public static synchronized void leereAufraeumen(Welt w) {
        if (w == null || w == entschieden) return;
        if (WaypointManager.anzahlIn(w.id) == 0 && (OHNE_ORT.equals(w.ort) || w.signaturen.isEmpty())) {
            WELTEN.remove(w);
        }
    }

    /* ---------------------------------------------------------------- Alte Schluessel */

    /**
     * Alter Welt-Schluessel (bis 4.93: "mp:host|s1a2b3c4|f3-64|minecraft:overworld",
     * "sp:Name|minecraft:the_nether", nur "minecraft:overworld" oder
     * "pr:Profil|dim") -> Welt und Dimension.
     */
    public static synchronized String[] ausAltemSchluessel(String alt) {
        if (alt == null || alt.isBlank()) return new String[] { welt(OHNE_ORT, "Unassigned", null).id, "minecraft:overworld" };
        String[] parts = alt.split("\\|");
        String dim = parts[parts.length - 1];
        if (!dim.contains(":")) dim = dim.equals("?") ? "minecraft:overworld" : "minecraft:" + dim;
        if (parts.length == 1) return new String[] { welt(OHNE_ORT, "Unassigned", null).id, dim };
        String place = parts[0];
        String seed = null;
        for (int i = 1; i < parts.length - 1; i++) {
            if (parts[i].startsWith("s") && parts[i].length() > 1) seed = parts[i].substring(1);
        }
        if (place.startsWith("pr:")) return new String[] { welt(OHNE_ORT, "Profile " + sauber(place.substring(3)), null).id, dim };
        if (place.startsWith("sp:")) return new String[] { welt("sp:" + sauber(place.substring(3)), sauber(place.substring(3)), null).id, dim };
        if (place.startsWith("mp:")) {
            String ort = "mp:" + adresse(place.substring(3));
            return new String[] { welt(ort, null, seed).id, dim };
        }
        return new String[] { welt(OHNE_ORT, "Unassigned", null).id, dim };
    }

    /** Welt fuer die Umstellung finden oder anlegen (per Name, bei Servern per Seed). */
    private static Welt welt(String ort, String name, String seed) {
        List<Welt> da = amOrt(ort);
        if (name != null) {
            for (Welt w : da) if (w.name.equals(name)) return w;
            return neu(ort, name);
        }
        for (Welt w : da) {
            for (String s : w.signaturen) {
                if (seed == null ? w.signaturen.isEmpty() : seed.equals(seedVon(s))) return w;
            }
            if (seed == null && w.signaturen.isEmpty()) return w;
        }
        Welt w = neu(ort, "World " + (da.size() + 1));
        if (seed != null) w.signaturen.add(signatur(seed, null));
        return w;
    }

    /* ---------------------------------------------------------------- Speichern */

    public static synchronized String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Welt w : WELTEN) {
            if (sb.length() > 0) sb.append(';');
            sb.append(w.id).append('|').append(esc(w.ort)).append('|').append(esc(w.name)).append('|')
              .append(w.nurSpawn ? "s" : "").append('|').append(w.zuletzt).append('|')
              .append(String.join(" ", w.signaturen));
        }
        return sb.toString();
    }

    public static synchronized void deserialize(String data) {
        WELTEN.clear();
        vergessen();
        if (data == null || data.isEmpty()) return;
        for (String e : data.split(";")) {
            String[] p = e.split("\\|", -1);
            if (p.length < 6 || p[0].isEmpty()) continue;
            Welt w = new Welt(p[0], unesc(p[1]), unesc(p[2]));
            w.nurSpawn = p[3].contains("s");
            try { w.zuletzt = Long.parseLong(p[4]); } catch (NumberFormatException ignored) { }
            for (String s : p[5].split(" ")) if (!s.isBlank()) w.signaturen.add(s);
            WELTEN.add(w);
        }
    }

    public static synchronized void clear() {
        WELTEN.clear();
        vergessen();
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("%", "%25").replace("|", "%7C").replace(";", "%3B");
    }

    private static String unesc(String v) {
        if (v == null) return "";
        return v.replace("%7C", "|").replace("%3B", ";").replace("%25", "%");
    }
}
