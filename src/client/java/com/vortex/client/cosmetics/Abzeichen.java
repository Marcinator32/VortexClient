package com.vortex.client.cosmetics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Abzeichen vor dem Namen (seit 4.95): [V] fuer jeden Vortex-Spieler, [CREATOR] fuer
 * akzeptierte Vortex Creator. Wer was ist, sagt der Freunde-Server (mit den Cosmetics der
 * anderen Spieler, fuer sich selbst mit dem Shop-Stand) -- der Client entscheidet es nie selbst.
 */
public final class Abzeichen {
    private Abzeichen() {}

    private static final Map<UUID, String> FREMDE = new ConcurrentHashMap<>();
    private static final MutableComponent V = Component.literal("[V] ")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x8B5CF6)).withBold(true));
    private static final MutableComponent CREATOR = Component.literal("[CREATOR] ")
            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFBBF24)).withBold(true));

    public static void setzen(UUID u, String abzeichen) {
        if (u == null) return;
        if (abzeichen == null || abzeichen.isEmpty()) FREMDE.remove(u);
        else FREMDE.put(u, abzeichen);
    }

    public static void vergessen() {
        FREMDE.clear();
    }

    /** "creator", "vortex" oder "" (kein Vortex-Spieler bzw. unbekannt). */
    public static String von(UUID u) {
        if (u == null) return "";
        var mc = Minecraft.getInstance();
        if (mc.player != null && u.equals(mc.player.getUUID())) {
            if (!Shop.aktiv()) return com.vortex.client.social.Social.conn() == com.vortex.client.social.Social.Conn.ONLINE ? "vortex" : "";
            return Shop.creator() ? "creator" : "vortex";
        }
        return FREMDE.getOrDefault(u, "");
    }

    /** Abzeichen als Text vor dem Namen, oder null. */
    public static Component prefix(UUID u) {
        if (!Cosmetics.abzeichenZeigen()) return null;
        return switch (von(u)) {
            case "creator" -> CREATOR;
            case "vortex" -> V;
            default -> null;
        };
    }

    /** Fuer den Namen ueber dem Kopf: Abzeichen des Spielers mit dieser Entity-Id. */
    public static Component prefixFuerEntity(int id) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        var e = mc.level.getEntity(id);
        return e instanceof net.minecraft.world.entity.player.Player p ? prefix(p.getUUID()) : null;
    }
}
