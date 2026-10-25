package com.vortex.client.cosmetics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die waehlbaren Huete & Co. -- seit 4.32 Voxel-Modelle wie die anderen
 * Pixel-Cosmetics (assets/vortexclient/cosmetics/designs.txt, Kategorie "hat",
 * gezeichnet von ZubehoerEbene). Die IDs der Huete aus 4.27 (halo, crown,
 * horns ...) sind geblieben, gespeicherte Auswahlen gelten weiter.
 */
public final class Huete {
    private Huete() {}

    public record Hut(String id, String name, String text) {}

    private static Map<String, Hut> alle;

    public static synchronized Map<String, Hut> alle() {
        if (alle == null) {
            Map<String, Hut> m = new LinkedHashMap<>();
            for (Zubehoer.Design d : Zubehoer.von(Zubehoer.Kategorie.HAT)) m.put(d.id(), new Hut(d.id(), d.name(), d.werte().getOrDefault("text", "")));
            alle = m;
        }
        return alle;
    }

    public static Hut get(String id) { return id == null ? null : alle().get(id); }
}
