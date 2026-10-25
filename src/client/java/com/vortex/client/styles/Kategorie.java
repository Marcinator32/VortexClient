package com.vortex.client.styles;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Die Teile, fuer die man im Styles-Menue einzeln ein Aussehen waehlt.
 *
 * Jede Kategorie weiss, welche Texturen (Pfad unter assets/minecraft/) zu
 * ihr gehoeren, wie die eingebauten Farb-Stile sie umfaerben und welches Bild
 * als Vorschau dient. Reihenfolge = Reihenfolge im Menue; beim Einsortieren
 * gewinnt die erste passende Kategorie (Schnee vor Gras wegen
 * grass_block_snow).
 */
public enum Kategorie {

    CRYSTAL("crystal", "Crystal", Modus.ALLES,
            List.of("textures/item/end_crystal.png", "textures/entity/end_crystal/end_crystal.png"),
            p -> p.startsWith("textures/entity/end_crystal/") || p.equals("textures/item/end_crystal.png")),
    ANCHOR("anchor", "Anchor", Modus.ALLES,
            List.of("textures/block/respawn_anchor_side4.png", "textures/block/respawn_anchor_top.png"),
            p -> p.startsWith("textures/block/respawn_anchor_")),
    TOTEM("totem", "Totem", Modus.ALLES,
            List.of("textures/item/totem_of_undying.png"),
            p -> p.equals("textures/item/totem_of_undying.png")),
    PEARL("pearl", "Pearl", Modus.ALLES,
            List.of("textures/item/ender_pearl.png"),
            p -> p.equals("textures/item/ender_pearl.png")),
    GAPPLE("gapple", "Gapples", Modus.ALLES,
            List.of("textures/item/golden_apple.png", "textures/item/enchanted_golden_apple.png"),
            p -> p.equals("textures/item/golden_apple.png") || p.equals("textures/item/enchanted_golden_apple.png")),
    SWORD("sword", "Sword", Modus.KLINGE,
            List.of("textures/item/diamond_sword.png", "textures/item/netherite_sword.png"),
            muster("textures/item/[a-z]+_sword\\.png")),
    PICKAXE("pickaxe", "Pickaxe", Modus.KLINGE,
            List.of("textures/item/diamond_pickaxe.png", "textures/item/netherite_pickaxe.png"),
            muster("textures/item/[a-z]+_pickaxe\\.png")),
    AXE("axe", "Axe", Modus.KLINGE,
            List.of("textures/item/diamond_axe.png", "textures/item/netherite_axe.png"),
            muster("textures/item/[a-z]+_axe\\.png")),
    ARMOR("armor", "Armor", Modus.ALLES,
            List.of("textures/item/diamond_chestplate.png", "textures/item/netherite_chestplate.png"),
            p -> p.startsWith("textures/entity/equipment/humanoid/") || p.startsWith("textures/entity/equipment/humanoid_leggings/")
                    || p.matches("textures/item/[a-z]+_(helmet|chestplate|leggings|boots)(_overlay)?\\.png")),
    FIRE("fire", "Fire", Modus.ALLES,
            List.of("textures/block/fire_0.png", "textures/block/fire_1.png"),
            muster("textures/block/fire_[01]\\.png(\\.mcmeta)?")),
    GLOWSTONE("glowstone", "Glowstone", Modus.ALLES,
            List.of("textures/block/glowstone.png"),
            p -> p.equals("textures/block/glowstone.png")),
    OBSIDIAN("obsidian", "Obsidian", Modus.ALLES,
            List.of("textures/block/obsidian.png", "textures/block/crying_obsidian.png"),
            muster("textures/block/(crying_)?obsidian\\.png(\\.mcmeta)?")),
    DEEPSLATE("deepslate", "Deepslate", Modus.GRAU,
            List.of("textures/block/deepslate.png", "textures/block/deepslate_top.png"),
            muster("textures/block/(?!reinforced_)[a-z_]*deepslate[a-z_]*\\.png")),
    STONE("stone", "Stone", Modus.GRAU,
            List.of("textures/block/stone.png", "textures/block/cobblestone.png"),
            muster("textures/block/(stone|cobblestone|(coal|iron|gold|copper|diamond|emerald|lapis|redstone)_ore)\\.png")),
    SNOW("snow", "Snow", Modus.ALLES,
            List.of("textures/block/snow.png", "textures/block/powder_snow.png"),
            muster("textures/block/(snow|powder_snow|grass_block_snow)\\.png")),
    GRASS("grass", "Grass", Modus.FLAECHE,
            List.of("textures/block/grass_block_top.png"),
            p -> p.equals("textures/colormap/grass.png") || p.matches("textures/block/grass_block_(top|side|side_overlay)\\.png")),
    HOTBAR("hotbar", "Hotbar", Modus.ALLES,
            List.of("textures/gui/sprites/hud/hotbar_selection.png", "textures/gui/sprites/hud/hotbar.png"),
            muster("textures/gui/sprites/hud/hotbar[a-z_]*\\.png(\\.mcmeta)?")),
    HEARTS("hearts", "Hearts", Modus.SYMBOL,
            List.of("textures/gui/sprites/hud/heart/full.png"),
            p -> p.startsWith("textures/gui/sprites/hud/heart/") && (p.endsWith(".png") || p.endsWith(".png.mcmeta"))),
    HUNGER("hunger", "Hunger", Modus.SYMBOL,
            List.of("textures/gui/sprites/hud/food_full.png"),
            muster("textures/gui/sprites/hud/food_[a-z_]+\\.png(\\.mcmeta)?")),
    ARMOR_BAR("armorbar", "Armor Bar", Modus.HELL,
            List.of("textures/gui/sprites/hud/armor_full.png"),
            muster("textures/gui/sprites/hud/armor_(full|half|empty)\\.png(\\.mcmeta)?")),
    XP_BAR("xpbar", "XP Bar", Modus.ALLES,
            List.of("textures/gui/sprites/hud/experience_bar_progress.png"),
            muster("textures/gui/sprites/hud/experience_bar_(progress|background)\\.png(\\.mcmeta)?")),
    INVENTORY("inventory", "Inventory", Modus.GRAU,
            List.of("textures/gui/container/inventory.png"),
            p -> (p.startsWith("textures/gui/container/") || p.startsWith("textures/gui/sprites/container/"))
                    && (p.endsWith(".png") || p.endsWith(".png.mcmeta")));

    /** Wie die eingebauten Farb-Stile umfaerben. */
    public enum Modus {
        /** Jedes sichtbare Pixel. */
        ALLES,
        /** Nur graue Pixel -- Erz-Flecken und bunte Teile bleiben. */
        GRAU,
        /** Alles ausser dem braunen Griff (Werkzeuge). */
        KLINGE,
        /** Grasfarbe: die Farbtabelle wird mit der Farbe gefuellt. */
        FLAECHE,
        /** Nur helle Pixel -- schwarze Umrandung und leere Teile bleiben (Ruestungsleiste). */
        HELL,
        /** Kleine Symbole (Herzen, Keulen): der haeufigste Ton wird genau die Farbe, Glanzpunkte heller. */
        SYMBOL
    }

    public final String id;
    public final String name;
    public final Modus modus;
    /** Bilder fuer die Vorschau, in dieser Reihenfolge gesucht. */
    public final List<String> vorschau;
    private final Predicate<String> gehoert;

    Kategorie(String id, String name, Modus modus, List<String> vorschau, Predicate<String> gehoert) {
        this.id = id;
        this.name = name;
        this.modus = modus;
        this.vorschau = vorschau;
        this.gehoert = gehoert;
    }

    private static Predicate<String> muster(String regex) {
        Pattern p = Pattern.compile(regex);
        return s -> p.matcher(s).matches();
    }

    /** Gehoert diese Textur (Pfad unter assets/minecraft/) hierher? */
    public boolean gehoert(String pfad) {
        return gehoert.test(pfad);
    }

    /** Zu welcher Kategorie gehoert ein Pfad (oder null)? */
    public static Kategorie von(String pfad) {
        for (Kategorie k : values()) if (k.gehoert(pfad)) return k;
        return null;
    }

    public static Kategorie vonId(String id) {
        for (Kategorie k : values()) if (k.id.equals(id)) return k;
        return null;
    }

    /** Was die eingebauten Farben in dieser Kategorie umfaerben (Teilmenge von gehoert()). */
    public boolean faerbt(String pfad) {
        if (!gehoert(pfad) || pfad.endsWith(".mcmeta")) return false;
        return switch (this) {
            case SWORD, PICKAXE, AXE -> !pfad.contains("/wooden_");
            case ARMOR -> pfad.contains("diamond") || pfad.contains("netherite");
            case GRASS -> pfad.equals("textures/colormap/grass.png");
            case HEARTS -> pfad.matches("textures/gui/sprites/hud/heart/(hardcore_)?(full|half)(_blinking)?\\.png");
            case HUNGER -> pfad.matches("textures/gui/sprites/hud/food_(full|half)\\.png");
            case ARMOR_BAR -> pfad.matches("textures/gui/sprites/hud/armor_(full|half)\\.png");
            case XP_BAR -> pfad.endsWith("experience_bar_progress.png");
            default -> true;
        };
    }

    /** Was die Effekte (Look, Leer, Blinken) veraendern: bei den Leisten alle ihre Bilder. */
    public boolean effektDran(String pfad) {
        return switch (this) {
            case HEARTS, HUNGER, ARMOR_BAR, XP_BAR -> gehoert(pfad) && !pfad.endsWith(".mcmeta");
            default -> faerbt(pfad);
        };
    }

    /** Leisten, neben denen auf Wunsch eine Zahl steht (Styles, Gruppe "number", seit 4.92). */
    public boolean mitZahl() {
        return this == HEARTS || this == HUNGER || this == ARMOR_BAR || this == XP_BAR;
    }

    /** Teile der Lebens-/Hunger-/Ruestungsleiste (Vorschau als kleines Raster). */
    public boolean leiste() {
        return this == HEARTS || this == HUNGER || this == ARMOR_BAR;
    }

    /** Ordner, in denen die eingebauten Farben nach Texturen suchen. */
    public List<String> ordner() {
        return switch (this) {
            case CRYSTAL -> List.of("textures/entity/end_crystal", "textures/item");
            case SWORD, PICKAXE, AXE, TOTEM, PEARL, GAPPLE -> List.of("textures/item");
            case ARMOR -> List.of("textures/item", "textures/entity/equipment/humanoid", "textures/entity/equipment/humanoid_leggings");
            case GRASS -> List.of("textures/colormap");
            case HOTBAR, HUNGER, ARMOR_BAR, XP_BAR -> List.of("textures/gui/sprites/hud");
            case HEARTS -> List.of("textures/gui/sprites/hud/heart");
            case INVENTORY -> List.of("textures/gui/container", "textures/gui/sprites/container");
            default -> List.of("textures/block");
        };
    }
}
