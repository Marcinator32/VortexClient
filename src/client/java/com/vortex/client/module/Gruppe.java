package com.vortex.client.module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unterbereiche der Kategorie "Cheats" (seit 4.39). Mit ueber 120 Modulen
 * war die eine lange Liste nicht mehr zu ueberblicken -- vor allem die vielen
 * "Auto ..."-Module. Jetzt gruppiert das Modulmenue sie hiernach, jede Gruppe
 * mit eigener Farbe.
 *
 * Die Zuordnung steht hier nach Modulnamen (auch fuer die Module aus dem Plus
 * Addon). Ein Addon kann eigene Module mit {@link #setze} einordnen; was
 * nirgends steht, landet unter "Other".
 */
public enum Gruppe {
    BASE("Base Finding", 0xFF34D399),
    PVP("PvP", 0xFFF87171),
    CRYSTAL("Crystal PvP", 0xFFF472B6),
    MOVEMENT("Movement", 0xFF60A5FA),
    WORLD("World", 0xFFFBBF24),
    RENDER("Render", 0xFFA78BFA),
    PLAYER("Player", 0xFF94A3B8),
    OTHER("Other", 0xFF6B7280);

    public final String titel;
    public final int farbe;

    Gruppe(String titel, int farbe) {
        this.titel = titel;
        this.farbe = farbe;
    }

    private static final Map<String, Gruppe> ZUORDNUNG = new HashMap<>();

    private static void z(Gruppe g, String... namen) {
        for (String n : namen) ZUORDNUNG.put(n, g);
    }

    static {
        z(BASE, "Stash Finder", "Sus Chunks", "New Chunks", "Block-ESP", "Container ESP", "Spawner ESP",
                "Tunnel Detector", "Xray", "Logout Spots", "Void ESP", "Freecam", "Ghost View", "Breadcrumbs");
        z(PVP, "Kill Aura", "Aimbot", "Auto Hit", "Criticals", "W-Tap", "Reach", "Hitbox Expand", "Auto Totem",
                "Auto Armor", "Auto Weapon", "Auto Gap", "Auto Pot", "Auto Mend", "Anti Knockback", "Mace Kill",
                "Attribute Swap", "Bow Aimbot", "Bow Spam", "Quiver", "Arrow Dodge", "Auto Clicker", "Multitask",
                "Middle Click Pearl", "Auto Log");
        z(CRYSTAL, "Crystal Aura", "Crystal Macro", "Surround", "Auto Anchor", "Anti Anchor", "Fast Anchor",
                "Bed Aura", "Hole ESP", "Hole Filler", "Auto Trap", "Self Trap", "Self Web", "Self Anvil", "Auto City",
                "City ESP", "Burrow", "Anti Bed", "Auto Anvil", "Anti Anvil", "Pop Chams", "Auto Web", "Anchor",
                "Break Indicators", "Offhand", "Instant Rebreak", "Packet Mine", "Fake Player");
        z(MOVEMENT, "Fly", "Speed", "Step", "Jesus", "Spider", "Safe Walk", "Parkour", "No Slow", "No Fall",
                "Boat Fly", "Elytra Fly", "Elytra Swap", "Blink", "Click TP", "Anti Void", "Inventory Move", "Air Jump",
                "Auto Jump", "Auto Walk", "Auto Wasp", "Entity Control", "Fast Climb", "High Jump", "Long Jump",
                "Reverse Step", "Slippy", "Trident Boost", "Timer");
        z(WORLD, "Nuker", "Scaffold", "Speed Mine", "Chest Stealer", "Ghost Hand", "Air Place", "Build Height",
                "Liquid Filler", "Liquid Interact", "No Ghost Blocks", "Vein Miner", "Spawn Proofer", "EChest Farmer",
                "Auto Breed", "Auto Mount", "Auto Nametag", "Auto Shearer", "Flamethrower", "Auto Fish", "Auto Tool");
        z(RENDER, "ESP", "Item ESP", "Tracers", "Radar");
        z(PLAYER, "Anti Hunger", "Auto Eat", "Auto Respawn", "Fast Use", "God Mode", "No Interact",
                "No Mining Trace", "No Rotate", "No Status Effects", "Portals", "Rotation", "Enderman Look");
    }

    /** Fuer Addons: eigenes Modul einordnen. */
    public static void setze(String modulName, Gruppe g) {
        if (modulName != null && g != null) ZUORDNUNG.put(modulName, g);
    }

    public static Gruppe von(Module m) {
        return m == null ? OTHER : ZUORDNUNG.getOrDefault(m.getName(), OTHER);
    }

    /** Module nach Gruppen, in Gruppen-Reihenfolge, je Gruppe alphabetisch. Leere Gruppen fehlen. */
    public static Map<Gruppe, List<Module>> aufteilen(List<Module> module) {
        Map<Gruppe, List<Module>> m = new EnumMap<>(Gruppe.class);
        for (Module mod : module) m.computeIfAbsent(von(mod), k -> new ArrayList<>()).add(mod);
        for (List<Module> l : m.values()) l.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return m;
    }
}
