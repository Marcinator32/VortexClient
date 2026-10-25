package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ArmorHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * ArmorHUD -- Ruestungsteile + Waffe mit Haltbarkeit.
 *
 * Liest jetzt alle Settings aus dem ArmorHudModule:
 *  - Basis-Position (baseX/baseY) verschiebt die ganze HUD
 *  - Einzel-Offsets verschieben jedes Teil zusaetzlich
 *  - textColor faerbt die Prozentanzeige
 *  - durabilityMode waehlt die Haltbarkeits-Anzeige (Prozent/Schlaege/Balken)
 *
 * Hinweis zum Scale: Echtes Icon-Skalieren braucht die MatrixStack-API,
 * die in 1.21.11 nicht sicher verfuegbar ist. Daher wirkt 'scale' hier
 * auf den ABSTAND der Teile (groesserer Abstand = HUD wirkt groesser),
 * nicht auf die Icon-Pixelgroesse. Das ist der stabile Weg.
 */
public final class ArmorHud {

    private static final int SLOT = 16;
    private static final int PADDING = 2;

    /**
     * Reused offset array, so no new one is built every frame.
     *
     * Its contents only change when a setting is touched, but a fresh two
     * dimensional array was being built for every single frame.
     */
    private static final int[][] OFFSETS = new int[5][2];

    private static ArmorHudModule module() {
        return ModuleManager.INSTANCE.get(ArmorHudModule.class);
    }

    public static void render(GuiGraphicsExtractor context, Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;

        ArmorHudModule mod = module();
        if (mod == null) return;

        ItemStack[] stacks = new ItemStack[] {
            player.getItemBySlot(EquipmentSlot.HEAD),
            player.getItemBySlot(EquipmentSlot.CHEST),
            player.getItemBySlot(EquipmentSlot.LEGS),
            player.getItemBySlot(EquipmentSlot.FEET),
            player.getMainHandItem()
        };

        int[][] offsets = OFFSETS;
        offsets[0][0] = mod.helmetOffsetX.getInt(); offsets[0][1] = mod.helmetOffsetY.getInt();
        offsets[1][0] = mod.chestOffsetX.getInt();  offsets[1][1] = mod.chestOffsetY.getInt();
        offsets[2][0] = mod.legsOffsetX.getInt();   offsets[2][1] = mod.legsOffsetY.getInt();
        offsets[3][0] = mod.bootsOffsetX.getInt();  offsets[3][1] = mod.bootsOffsetY.getInt();
        offsets[4][0] = mod.handOffsetX.getInt();   offsets[4][1] = mod.handOffsetY.getInt();

        int screenW = context.guiWidth();
        int screenH = context.guiHeight();

        if (karteAktiv(mod)) {
            karte(context, client, mod, stacks, screenW, screenH);
            return;
        }

        double scale = mod.scale.get();
        int step = (int) Math.round((SLOT + PADDING) * scale);

        boolean rechts = mod.numberSide.getIndex() == 2;
        float g = mod.numberSize.getFloat();
        int textPlatz = 0;
        if (rechts) for (ItemStack st : stacks) if (st != null && st.isDamageableItem()) textPlatz = Math.max(textPlatz, Math.round(client.font.width("100%") * g) + 4);

        int totalHeight = stacks.length * step;
        int baseX = screenW - SLOT - 6 - textPlatz + mod.baseX.getInt();
        int startY = (screenH - totalHeight) / 2 + mod.baseY.getInt();

        String mode = mod.durabilityMode.get();
        int textColor = mod.textColor.get();

        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            if (stack == null || stack.isEmpty()) continue;

            int x = baseX + offsets[i][0];
            int y = startY + i * step + offsets[i][1];

            context.item(stack, x, y);

            if (mode.equals("Balken")) {
                context.itemDecorations(client.font, stack, x, y);
            }

            if (!mode.equals("Balken") && stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int leftDur = max - stack.getDamageValue();

                String txt;
                if (mode.equals("Schlaege")) {
                    txt = Integer.toString(leftDur);
                } else {
                    int percent = (int) Math.round((leftDur / (double) max) * 100.0);
                    txt = percent + "%";
                }

                float textW = client.font.width(txt) * g;
                float tx = rechts ? x + SLOT + 4 : x - textW - 4;
                float ty = y + (SLOT - 8 * g) / 2f;
                var pose = context.pose();
                pose.pushMatrix();
                try {
                    pose.translate(tx, ty);
                    pose.scale(g, g);
                    context.text(client.font, txt, 0, 0, textColor);
                } finally {
                    pose.popMatrix();
                }
            }
        }
    }


    /**
     * Wird gerade die Karte gezeichnet? Einzeln verschobene Teile oder der Balken-Modus
     * schalten auf die einzelnen Gegenstaende zurueck.
     */
    public static boolean karteAktiv(ArmorHudModule mod) {
        boolean karte = mod.look.getIndex() == 1 || (mod.look.getIndex() == 0 && HudKarte.modern());
        return karte && !mod.einzelnVerschoben() && !mod.durabilityMode.get().equals("Balken");
    }

    private static int karteX(int sw, int w, float s, ArmorHudModule mod) {
        return Math.round(sw - w * s - 4 + mod.baseX.getInt());
    }

    private static int karteY(int sh, int h, float s, ArmorHudModule mod) {
        return Math.round((sh - h * s) / 2f + mod.baseY.getInt());
    }

    /**
     * Der Bereich, den die ArmorHUD gerade wirklich auf dem Bildschirm belegt, als
     * {x, y, breite, hoehe} -- fuer den HUD-Editor, damit man die ganze Anzeige greifen kann.
     * Ohne Spieler oder ohne Ruestung gibt es einen Platzhalter in Kartengroesse.
     */
    public static int[] bereich(Minecraft client, ArmorHudModule mod, int sw, int sh) {
        LocalPlayer player = client.player;
        ItemStack[] stacks = new ItemStack[5];
        if (player != null) {
            stacks[0] = player.getItemBySlot(EquipmentSlot.HEAD);
            stacks[1] = player.getItemBySlot(EquipmentSlot.CHEST);
            stacks[2] = player.getItemBySlot(EquipmentSlot.LEGS);
            stacks[3] = player.getItemBySlot(EquipmentSlot.FEET);
            stacks[4] = player.getMainHandItem();
        }
        boolean leer = true;
        for (ItemStack st : stacks) if (st != null && !st.isEmpty()) leer = false;
        if (leer) {
            float s = mod.scale.getFloat();
            int w = HudKarte.PAD_X * 2 + 2 + 16 + 5 + 30, h = 5 * 20 + 4;
            return new int[] { karteX(sw, w, s, mod), karteY(sh, h, s, mod), Math.round(w * s), Math.round(h * s) };
        }
        if (karteAktiv(mod)) {
            float g = mod.numberSize.getFloat();
            int zeile = Math.max(20, Math.round(9 * g + 11));
            int innen = Math.round(30 * Math.max(1f, g)), n = 0;
            boolean schlaege = mod.durabilityMode.get().equals("Schlaege");
            for (ItemStack st : stacks) {
                if (st == null || st.isEmpty()) continue;
                n++;
                String t;
                if (st.isDamageableItem()) {
                    int max = st.getMaxDamage(), rest = max - st.getDamageValue();
                    t = schlaege ? Integer.toString(rest) : Math.round(rest * 100f / max) + "%";
                } else t = st.getCount() > 1 ? "x" + st.getCount() : "";
                innen = Math.max(innen, Math.round(HudKarte.breite(t, true) * g));
            }
            int w = HudKarte.PAD_X + 2 + 16 + 5 + innen + HudKarte.PAD_X;
            int h = n * zeile + 4;
            float s = mod.scale.getFloat();
            return new int[] { karteX(sw, w, s, mod), karteY(sh, h, s, mod), Math.round(w * s), Math.round(h * s) };
        }
        int step = (int) Math.round((SLOT + PADDING) * mod.scale.get());
        boolean rechts = mod.numberSide.getIndex() == 2;
        boolean zahlen = !mod.durabilityMode.get().equals("Balken");
        float g = mod.numberSize.getFloat();
        int textPlatz = 0;
        if (rechts) for (ItemStack st : stacks) if (st != null && st.isDamageableItem()) textPlatz = Math.max(textPlatz, Math.round(client.font.width("100%") * g) + 4);
        int baseX = sw - SLOT - 6 - textPlatz + mod.baseX.getInt();
        int startY = (sh - stacks.length * step) / 2 + mod.baseY.getInt();
        int[][] offs = mod.offsets();
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int i = 0; i < stacks.length; i++) {
            ItemStack st = stacks[i];
            if (st == null || st.isEmpty()) continue;
            int x = baseX + offs[i][0], y = startY + i * step + offs[i][1];
            int l = x, r = x + SLOT;
            if (zahlen && st.isDamageableItem()) {
                int tw = Math.round(client.font.width("100%") * g) + 4;
                if (rechts) r += tw; else l -= tw;
            }
            x0 = Math.min(x0, l); x1 = Math.max(x1, r);
            y0 = Math.min(y0, y); y1 = Math.max(y1, y + SLOT);
        }
        return new int[] { x0, y0, x1 - x0, y1 - y0 };
    }

    /**
     * HUD Design "Modern" (seit 4.56): alle Teile in einer Karte untereinander -- Gegenstand,
     * Haltbarkeit als Zahl in Ampelfarbe und ein schmaler Balken darunter.
     */
    private static void karte(GuiGraphicsExtractor ctx, Minecraft client, ArmorHudModule mod, ItemStack[] stacks, int sw, int sh) {
        java.util.List<ItemStack> teile = new java.util.ArrayList<>();
        for (ItemStack st : stacks) if (st != null && !st.isEmpty()) teile.add(st);
        if (teile.isEmpty()) return;
        boolean schlaege = mod.durabilityMode.get().equals("Schlaege");
        float g = mod.numberSize.getFloat();
        boolean links = mod.numberSide.getIndex() == 1;
        final int icon = 16;
        final int zeile = Math.max(20, Math.round(9 * g + 11));
        final int balkenB = Math.round(30 * Math.max(1f, g));
        int textB = 0;
        String[] texte = new String[teile.size()];
        float[] anteile = new float[teile.size()];
        for (int i = 0; i < teile.size(); i++) {
            ItemStack st = teile.get(i);
            if (st.isDamageableItem()) {
                int max = st.getMaxDamage(), rest = max - st.getDamageValue();
                anteile[i] = rest / (float) max;
                texte[i] = schlaege ? Integer.toString(rest) : Math.round(anteile[i] * 100) + "%";
            } else {
                anteile[i] = -1;
                texte[i] = st.getCount() > 1 ? "x" + st.getCount() : "";
            }
            textB = Math.max(textB, Math.round(HudKarte.breite(texte[i], true) * g));
        }
        int innen = Math.max(textB, balkenB);
        int w = HudKarte.PAD_X + 2 + icon + 5 + innen + HudKarte.PAD_X;
        int h = teile.size() * zeile + 4;
        float s = mod.scale.getFloat();
        int x = karteX(sw, w, s, mod);
        int y = karteY(sh, h, s, mod);
        HudRenderer.pushScale(ctx, x, y, s);
        try {
            HudKarte.karte(ctx, x, y, w, h, 1f);
            for (int i = 0; i < teile.size(); i++) {
                float zy = y + 2 + i * zeile;
                int links0 = x + HudKarte.PAD_X + 2;
                int ix = links ? links0 + innen + 5 : links0;
                ctx.item(teile.get(i), ix, Math.round(zy + (zeile - icon) / 2f));
                float tx = links ? links0 : ix + icon + 5;
                float a = anteile[i];
                int farbe = a < 0 ? (mod.textColor.get() | 0xFF000000) : ampel(a);
                float tw = HudKarte.breite(texte[i], true) * g;
                float txt = links ? tx + innen - tw : tx;
                if (a >= 0) {
                    float textY = zy + (zeile - 6 - 8 * g) / 2f;
                    zahl(ctx, texte[i], txt, textY, farbe, g);
                    float by = textY + 8 * g + 3;
                    com.vortex.client.gui.glatt.Glatt.rund(ctx, tx, by, innen, 2, 1, 0x30FFFFFF);
                    if (a > 0) {
                        float bw = Math.max(2, innen * a);
                        com.vortex.client.gui.glatt.Glatt.rund(ctx, links ? tx + innen - bw : tx, by, bw, 2, 1, farbe);
                    }
                } else {
                    zahl(ctx, texte[i], txt, zy + (zeile - 8 * g) / 2f, farbe, g);
                }
            }
        } finally {
            HudRenderer.popScale(ctx);
        }
    }

    /** Zahl in Groesse g zeichnen (glatte Schrift). */
    private static void zahl(GuiGraphicsExtractor ctx, String t, float x, float y, int farbe, float g) {
        var pose = ctx.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(g, g);
            HudKarte.text(ctx, t, 0, 0, farbe, true);
        } finally {
            pose.popMatrix();
        }
    }

    /** Gruen (voll) -> Gelb -> Rot (fast kaputt). */
    static int ampel(float a) {
        return a > 0.5f ? com.vortex.client.gui.glatt.Glatt.mix(0xFFFACC15, 0xFF4ADE80, (a - 0.5f) * 2f)
                : com.vortex.client.gui.glatt.Glatt.mix(0xFFF87171, 0xFFFACC15, a * 2f);
    }
}
