package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * ArmorHUD als Modul.
 *
 * Kann jetzt:
 *  - die ganze HUD verschieben (baseX / baseY)
 *  - skalieren (scale)
 *  - die Haltbarkeits-Anzeige waehlen: Prozent / Schlaege / Balken
 *  - die Textfarbe der Prozentanzeige waehlen (textColor)
 *  - JEDES Teil einzeln verschieben (Offsets je Helm/Brust/Hose/Schuhe/Waffe)
 *
 * Die Einzel-Offsets werden ZUSAETZLICH zur Basisposition angewendet.
 */
public class ArmorHudModule extends Module {

    public final ModeSetting durabilityMode =
        new ModeSetting("Durability", 0, "Prozent", "Schlaege", "Balken");
    public final ColorSetting textColor = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    /**
     * Seit 4.57: "Card" = alle Teile in einer Karte mit Ampelfarbe und Balken, "Classic" = die
     * einzelnen Gegenstaende wie bis 4.55, "Auto" (seit 4.58) folgt HUD Design.
     */
    public final ModeSetting look = new ModeSetting("Look", 0, "Auto", "Card", "Classic");
    /** Zahlen links oder rechts vom Gegenstand ("Auto": Karte rechts, Classic links). */
    public final ModeSetting numberSide = new ModeSetting("Numbers Side", 0, "Auto", "Left", "Right");
    /** Groesse der Zahlen (1 = normal). */
    public final NumberSetting numberSize = new NumberSetting("Number Size", 1.0, 0.5, 2.0, 0.1);

    public final NumberSetting baseX = new NumberSetting("X", 0, -1920, 1920, 1);
    public final NumberSetting baseY = new NumberSetting("Y", 0, -1080, 1080, 1);

    public final NumberSetting helmetOffsetX = new NumberSetting("Helmet X", 0, -400, 400, 1);
    public final NumberSetting helmetOffsetY = new NumberSetting("Helmet Y", 0, -400, 400, 1);
    public final NumberSetting chestOffsetX  = new NumberSetting("Chestplate X", 0, -400, 400, 1);
    public final NumberSetting chestOffsetY  = new NumberSetting("Chestplate Y", 0, -400, 400, 1);
    public final NumberSetting legsOffsetX   = new NumberSetting("Leggings X", 0, -400, 400, 1);
    public final NumberSetting legsOffsetY   = new NumberSetting("Leggings Y", 0, -400, 400, 1);
    public final NumberSetting bootsOffsetX  = new NumberSetting("Boots X", 0, -400, 400, 1);
    public final NumberSetting bootsOffsetY  = new NumberSetting("Boots Y", 0, -400, 400, 1);
    public final NumberSetting handOffsetX   = new NumberSetting("Weapon X", 0, -400, 400, 1);
    public final NumberSetting handOffsetY   = new NumberSetting("Weapon Y", 0, -400, 400, 1);

    public ArmorHudModule() {
        super("ArmorHUD", Category.HUD);
        enabledByDefault();
        addSetting(durabilityMode);
        addSetting(textColor);
        addSetting(scale);
        addSetting(look);
        addSetting(numberSide);
        addSetting(numberSize);
        addSetting(baseX);
        addSetting(baseY);
        addSetting(helmetOffsetX);
        addSetting(helmetOffsetY);
        addSetting(chestOffsetX);
        addSetting(chestOffsetY);
        addSetting(legsOffsetX);
        addSetting(legsOffsetY);
        addSetting(bootsOffsetX);
        addSetting(bootsOffsetY);
        addSetting(handOffsetX);
        addSetting(handOffsetY);
    }

    /**
     * Ein einzelnes ArmorHUD-Teil fuer den HUD-Editor: Name, aktuelle
     * Bildschirmposition (linke obere Ecke) und die zugehoerigen Offset-
     * Settings, die beim Ziehen veraendert werden.
     */
    public static final class ArmorPart {
        public final String name;
        public final int x;
        public final int y;
        public final int size;
        public final NumberSetting offsetX;
        public final NumberSetting offsetY;

        ArmorPart(String name, int x, int y, int size,
                  NumberSetting offsetX, NumberSetting offsetY) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.size = size;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    /** Die Einzel-Versaetze {x, y} in der Reihenfolge Helm, Brust, Hose, Schuhe, Waffe. */
    public int[][] offsets() {
        return new int[][] {
            { helmetOffsetX.getInt(), helmetOffsetY.getInt() },
            { chestOffsetX.getInt(),  chestOffsetY.getInt()  },
            { legsOffsetX.getInt(),   legsOffsetY.getInt()   },
            { bootsOffsetX.getInt(),  bootsOffsetY.getInt()  },
            { handOffsetX.getInt(),   handOffsetY.getInt()   }
        };
    }

    /** Wurde mindestens ein Teil einzeln verschoben? */
    public boolean einzelnVerschoben() {
        for (int[] o : offsets()) if (o[0] != 0 || o[1] != 0) return true;
        return false;
    }

    /** Alle Einzel-Versaetze zuruecksetzen (die Teile sitzen dann wieder sauber untereinander). */
    public void einzelnZuruecksetzen() {
        for (NumberSetting n : new NumberSetting[] { helmetOffsetX, helmetOffsetY, chestOffsetX, chestOffsetY,
                legsOffsetX, legsOffsetY, bootsOffsetX, bootsOffsetY, handOffsetX, handOffsetY }) n.set(0);
    }

    /** Konstanten, identisch zur Zeichen-Logik in ArmorHud. */
    private static final int SLOT = 16;
    private static final int PADDING = 2;

    /**
     * Berechnet die aktuelle Bildschirmposition ALLER 5 Teile -- exakt so wie
     * ArmorHud sie zeichnet. Der HUD-Editor nutzt das, um verschiebbare Kaesten
     * an die richtige Stelle zu legen. screenW/screenH = Bildschirmgroesse.
     *
     * Reihenfolge: Helm, Brust, Hose, Schuhe, Waffe.
     */
    public java.util.List<ArmorPart> computeParts(int screenW, int screenH) {
        double sc = scale.get();
        int step = (int) Math.round((SLOT + PADDING) * sc);
        int totalHeight = 5 * step;
        int textPlatz = 0;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (numberSide.getIndex() == 2 && mc.player != null) {
            for (var st : new net.minecraft.world.item.ItemStack[] {
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS),
                    mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET),
                    mc.player.getMainHandItem() })
                if (st.isDamageableItem()) textPlatz = Math.max(textPlatz, Math.round(mc.font.width("100%") * numberSize.getFloat()) + 4);
        }
        int bX = screenW - SLOT - 6 - textPlatz + baseX.getInt();
        int startY = (screenH - totalHeight) / 2 + baseY.getInt();

        NumberSetting[][] offs = new NumberSetting[][] {
            { helmetOffsetX, helmetOffsetY },
            { chestOffsetX,  chestOffsetY  },
            { legsOffsetX,   legsOffsetY   },
            { bootsOffsetX,  bootsOffsetY  },
            { handOffsetX,   handOffsetY   }
        };
        String[] names = { "Helm", "Brust", "Hose", "Schuhe", "Waffe" };

        java.util.List<ArmorPart> parts = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int x = bX + offs[i][0].getInt();
            int y = startY + i * step + offs[i][1].getInt();
            parts.add(new ArmorPart(names[i], x, y, SLOT, offs[i][0], offs[i][1]));
        }
        return parts;
    }
}
