package com.vortex.client.hud;

import com.vortex.client.styles.Effekte;
import com.vortex.client.styles.Kategorie;
import com.vortex.client.styles.Styles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Ruestungsleiste "By armor" (Styles, Armor Bar): jeder Punkt der Leiste bekommt die
 * Farbe des Ruestungsteils, von dem er kommt -- Helm, Brust, Hose, Schuhe von links nach
 * rechts. Gezeichnet wird wie bei Vanilla, nur mit Toenung.
 */
public final class RuestungsLeiste {

    private RuestungsLeiste() {}

    private static final Identifier VOLL = Identifier.withDefaultNamespace("hud/armor_full");
    private static final Identifier HALB = Identifier.withDefaultNamespace("hud/armor_half");
    private static final Identifier LEER = Identifier.withDefaultNamespace("hud/armor_empty");
    private static final EquipmentSlot[] REIHE = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private static long geprueft;
    private static boolean an;

    /** Ist "By armor" gewaehlt? (hoechstens einmal pro Sekunde nachgesehen) */
    public static boolean aktiv() {
        long jetzt = System.currentTimeMillis();
        if (jetzt - geprueft > 1000) {
            geprueft = jetzt;
            try {
                an = Effekte.ruestungNachGetragen(java.util.Map.of("tint", Styles.effekt(Kategorie.ARMOR_BAR, "tint")));
            } catch (Throwable t) {
                an = false;
            }
        }
        return an;
    }

    /** Wie Hud.extractArmor, nur getoent. y/x wie bei Vanilla berechnet. */
    public static void zeichnen(GuiGraphicsExtractor g, Player p, int unten, int reihen, int abstand, int x0) {
        int ruestung = p.getArmorValue();
        if (ruestung <= 0) return;
        int[] farben = punktFarben(p, ruestung);
        int y = unten - (reihen - 1) * abstand - 10;
        for (int i = 0; i < 10; i++) {
            int x = x0 + i * 8;
            int punkt = i * 2;
            int c = punkt < farben.length ? farben[punkt] : 0xFFFFFFFF;
            if (i * 2 + 1 < ruestung) g.blitSprite(RenderPipelines.GUI_TEXTURED, VOLL, x, y, 9, 9, c);
            if (i * 2 + 1 == ruestung) g.blitSprite(RenderPipelines.GUI_TEXTURED, HALB, x, y, 9, 9, c);
            if (i * 2 + 1 > ruestung) g.blitSprite(RenderPipelines.GUI_TEXTURED, LEER, x, y, 9, 9);
        }
    }

    /** Farbe je Ruestungspunkt (ARGB); Punkte ohne bekanntes Teil bleiben weiss. */
    private static int[] punktFarben(Player p, int gesamt) {
        int[] f = new int[Math.max(gesamt, 20)];
        java.util.Arrays.fill(f, 0xFFFFFFFF);
        int pos = 0;
        for (EquipmentSlot s : REIHE) {
            ItemStack st = p.getItemBySlot(s);
            if (st.isEmpty()) continue;
            int punkte = (int) Math.round(punkte(st, s));
            int c = toenung(st);
            for (int k = 0; k < punkte && pos < f.length; k++) f[pos++] = c;
        }
        return f;
    }

    private static double punkte(ItemStack st, EquipmentSlot s) {
        double[] summe = { 0 };
        try {
            st.forEachModifier(s, (attr, mod) -> {
                if (attr.value() == Attributes.ARMOR.value() && mod.operation() == AttributeModifier.Operation.ADD_VALUE) summe[0] += mod.amount();
            });
        } catch (Throwable ignored) { }
        return summe[0];
    }

    /** Toenung fuer das Material; das Styles-Pack macht die Leiste dafuer fast weiss. */
    private static int toenung(ItemStack st) {
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        int c;
        if (id.startsWith("leather_")) c = 0xC8824E;
        else if (id.startsWith("chainmail_")) c = 0xA4ABB6;
        else if (id.startsWith("iron_")) c = 0xE4E6EC;
        else if (id.startsWith("golden_")) c = 0xFFD84A;
        else if (id.startsWith("diamond_")) c = 0x56F0E4;
        else if (id.startsWith("netherite_")) c = 0x7A6C78;
        else if (id.startsWith("copper_")) c = 0xF0975A;
        else if (id.startsWith("turtle_")) c = 0x72D85E;
        else c = 0xFFFFFF;
        return 0xFF000000 | c;
    }
}
