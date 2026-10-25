package com.vortex.client.hud;

import com.vortex.client.module.modules.CoordinatesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Koordinaten im HUD Design "Modern" (seit 4.56): eine Karte, je Dimension eine Zeile mit
 * Block davor (Gras = Oberwelt, Netherrack = Nether). Die Zeile, in der du gerade bist, ist
 * hell, die umgerechnete gedimmt. Rechts die Blickrichtung.
 */
public final class KoordKarte {
    private KoordKarte() {}

    private static final ItemStack GRAS = new ItemStack(Items.GRASS_BLOCK), NETHER = new ItemStack(Items.NETHERRACK),
            END = new ItemStack(Items.END_STONE);

    private record Zeile(ItemStack icon, String text, boolean hier) {}

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc, CoordinatesModule m) {
        long x = (long) Math.floor(mc.player.getX()), y = (long) Math.floor(mc.player.getY()), z = (long) Math.floor(mc.player.getZ());
        boolean versteckt = com.vortex.client.module.modules.StreamerModeModule.koordinatenVerstecken();
        var dim = mc.level.dimension();
        List<Zeile> zeilen = new ArrayList<>(2);
        if (dim == Level.END) {
            zeilen.add(new Zeile(END, text(x, y, z, versteckt), true));
        } else if (dim == Level.NETHER) {
            zeilen.add(new Zeile(NETHER, text(x, y, z, versteckt), true));
            if (m.otherDimension.get()) zeilen.add(new Zeile(GRAS, text(x * 8, y, z * 8, versteckt), false));
        } else {
            zeilen.add(new Zeile(GRAS, text(x, y, z, versteckt), true));
            if (m.otherDimension.get()) zeilen.add(new Zeile(NETHER, text(Math.floorDiv(x, 8), y, Math.floorDiv(z, 8), versteckt), false));
        }
        String richtung = switch (mc.player.getDirection()) {
            case NORTH -> "N"; case SOUTH -> "S"; case EAST -> "E"; case WEST -> "W"; default -> "";
        };
        final int zeileH = 16, icon = 12;
        int textB = 0;
        for (Zeile zl : zeilen) textB = Math.max(textB, HudKarte.breite(zl.text(), true));
        int rB = HudKarte.breite(richtung, false);
        int links = HudKarte.PAD_X + 2;
        int w = links + icon + 5 + textB + 8 + rB + HudKarte.PAD_X;
        int h = zeilen.size() * zeileH + 2 * 3;
        m.style.letzteB = Math.round(w * m.scale.getFloat());
        m.style.letzteH = Math.round(h * m.scale.getFloat());
        int px = m.x.getInt(), py = m.y.getInt();
        HudRenderer.pushScale(ctx, px, py, m.scale.getFloat());
        try {
            HudKarte.karte(ctx, px, py, w, h, 1f);
            int hell = m.color.get() | 0xFF000000, dunkel = HudKarte.labelFarbe();
            for (int i = 0; i < zeilen.size(); i++) {
                Zeile zl = zeilen.get(i);
                float zy = py + 3 + i * zeileH;
                var pose = ctx.pose();
                pose.pushMatrix();
                try {
                    pose.translate(px + links, zy + (zeileH - icon) / 2f);
                    pose.scale(icon / 16f, icon / 16f);
                    ctx.item(zl.icon(), 0, 0);
                } finally {
                    pose.popMatrix();
                }
                HudKarte.text(ctx, zl.text(), px + links + icon + 5, zy + (zeileH - 8) / 2f, zl.hier() ? hell : dunkel, true);
                if (i == 0) HudKarte.text(ctx, richtung, px + w - HudKarte.PAD_X - rB, zy + (zeileH - 8) / 2f, dunkel, false);
            }
        } finally {
            HudRenderer.popScale(ctx);
        }
    }

    private static String text(long x, long y, long z, boolean versteckt) {
        return versteckt ? "hidden" : x + "  " + y + "  " + z;
    }
}
