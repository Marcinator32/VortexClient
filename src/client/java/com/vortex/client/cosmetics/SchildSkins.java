package com.vortex.client.cosmetics;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Shield Skins (seit 4.29): ein Schild in der Hand eines Vortex-Spielers
 * bekommt dessen gewaehlte Textur (assets/vortexclient/textures/cosmetics/shield).
 *
 * Ablauf: Beim Aufbereiten eines Items (ItemModelResolver) merken wir uns,
 * wem es gehoert. Der Schild-Renderer haengt dann eine Marke an seine Daten,
 * und beim Zeichnen wird statt der Vanilla-Textur unsere genommen. Muster von
 * Bannern auf dem Schild bleiben dann unsichtbar -- der Skin ersetzt das Ganze.
 */
public final class SchildSkins {
    private SchildSkins() {}

    private static final String MARKE = "vortex_shield_skin", FARBE = "vortex_shield_tint";
    private static final ThreadLocal<LivingEntity> AKTUELL = new ThreadLocal<>();

    /** Vor/nach dem Aufbereiten eines Items: wem gehoert es? */
    public static void besitzer(ItemOwner owner) {
        if (owner == null) { AKTUELL.remove(); return; }
        try {
            LivingEntity e = owner.asLivingEntity();
            if (e != null) { AKTUELL.set(e); return; }
        } catch (Throwable ignored) {
        }
        AKTUELL.remove();
    }

    private static String skinVon(LivingEntity e) {
        if (!(e instanceof Player p)) return null;
        String id = Zubehoer.basisId(Cosmetics.fuer(p.getUUID()).schild());
        return !id.isEmpty() && Zubehoer.get(Zubehoer.Kategorie.SHIELD, id) != null ? id : null;
    }

    private static final String GEBROCHEN = "vortex_shield_broken", GLITZERN = "vortex_shield_sparkle";

    /**
     * Daten des Schilds mit Skin- und Status-Marke (oder unveraendert).
     * Skin und Status werden getrennt bestimmt -- geht beim Status etwas schief,
     * bleibt der Skin trotzdem sichtbar (und umgekehrt).
     */
    public static DataComponentMap markieren(DataComponentMap daten, ItemStack stack) {
        LivingEntity e = AKTUELL.get();
        if (e == null || daten == null) return daten;
        String id = null;
        try { id = skinVon(e); } catch (Throwable t) { com.vortex.client.core.Errors.report("SchildSkins.skin", t); }
        SchildStatus.Zustand z = SchildStatus.zustand(e, stack);
        if (id == null && z.farbe() == 0) return daten;
        CompoundTag tag = new CompoundTag();
        if (id != null) tag.putString(MARKE, id);
        if (z.farbe() != 0) tag.putInt(FARBE, z.farbe());
        if (z.gebrochen()) tag.putBoolean(GEBROCHEN, true);
        if (id != null && e instanceof Player sp && Zubehoer.glitzert(Cosmetics.fuer(sp.getUUID()).schild())) tag.putBoolean(GLITZERN, true);
        return DataComponentMap.builder().addAll(daten).set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build();
    }

    /** Glitzert der Schild-Skin (Einstellung "Sparkle", seit 4.35)? Dann bekommt er den Verzauberungs-Glanz. */
    public static boolean glitzert(DataComponentMap daten) {
        if (daten == null) return false;
        CustomData cd = daten.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().getBooleanOr(GLITZERN, false);
    }

    /** Ist der markierte Schild gerade gebrochen (Shield Status)? */
    public static boolean gebrochen(DataComponentMap daten) {
        if (daten == null) return false;
        CustomData cd = daten.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().getBooleanOr(GEBROCHEN, false);
    }

    /** Maske fuer den Rahmen (Shield Status auf Skins). */
    public static final Identifier STATUS_RAHMEN = Identifier.fromNamespaceAndPath("vortexclient", "textures/cosmetics/shield/status_rim.png");

    /** Bilder je animiertem Skin (Datei id_0.png ... id_{n-1}.png); sonst eine Datei id.png. */
    private static final java.util.Map<String, Integer> BILDER = new java.util.HashMap<>();

    private static int bilder(Zubehoer.Design d) {
        return BILDER.computeIfAbsent(d.id(), k -> {
            try { return Math.max(1, Integer.parseInt(d.werte().getOrDefault("frames", "1"))); } catch (Exception x) { return 1; }
        });
    }

    /** Textur fuer markierte Daten, sonst null (dann zeichnet Minecraft selbst). Animierte Skins wechseln das Bild. */
    public static Identifier textur(DataComponentMap daten) {
        if (daten == null) return null;
        CustomData cd = daten.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        String id = cd.copyTag().getStringOr(MARKE, "");
        if (id.isEmpty() || !id.matches("[a-z0-9_]{1,40}")) return null;
        return texturFuer(id);
    }

    /** Textur eines Skins (animierte wechseln mit der Zeit das Bild); null = unbekannt. Auch fuer die Menue-Vorschau. */
    public static Identifier texturFuer(String id) {
        Zubehoer.Design d = Zubehoer.get(Zubehoer.Kategorie.SHIELD, id);
        if (d == null) return null;
        int n = bilder(d);
        if (n > 1) {
            long ms = net.minecraft.util.Util.getMillis();
            int ms_bild = 1000 / Math.max(1, (int) zahl(d.werte().get("fps"), 8));
            id = id + "_" + (int) ((ms / ms_bild) % n);
        }
        return Identifier.fromNamespaceAndPath("vortexclient", "textures/cosmetics/shield/" + id + ".png");
    }

    private static float zahl(String s, float d) {
        try { return s == null ? d : Float.parseFloat(s); } catch (NumberFormatException e) { return d; }
    }

    private static net.minecraft.client.model.object.equipment.ShieldModel MODELL;

    private static net.minecraft.client.model.object.equipment.ShieldModel modell() {
        if (MODELL == null) MODELL = new net.minecraft.client.model.object.equipment.ShieldModel(
                net.minecraft.client.Minecraft.getInstance().getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.SHIELD));
        return MODELL;
    }

    /**
     * Schild mit Vortex-Skin zeichnen (Pose = wie fuer den Schild-Renderer). Motiv unveraendert
     * (gebrochen leicht rot/grau), Shield Status als leuchtender Rahmen darueber, dazu Glanz
     * bei Verzauberung oder "Sparkle". false = kein Skin (dann zeichnet Minecraft).
     */
    public static boolean zeichnen(DataComponentMap daten, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector c,
                                   int licht, int overlay, boolean glanz, int umriss) {
        Identifier tex = textur(daten);
        if (tex == null) return false;
        var model = modell();
        int farbe = farbe(daten);
        int motiv = farbe != -1 && gebrochen(daten) ? SchildStatus.mischen(0xFFFFFFFF, farbe, 0.4f) : -1;
        boolean funkeln = glanz || glitzert(daten);
        //#if 26
        c.submitModel(model, net.minecraft.util.Unit.INSTANCE, pose, model.renderType(tex), licht, overlay, motiv, null, umriss, null);
        if (farbe != -1) c.order(1).submitModel(model, net.minecraft.util.Unit.INSTANCE, pose,
                net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(STATUS_RAHMEN), 0xF000F0, overlay, farbe, null, 0, null);
        if (funkeln) c.order(2).submitModel(model, net.minecraft.util.Unit.INSTANCE, pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityGlint(), licht, overlay, -1, null, 0, null);
        //#else
        //$ pose.pushPose();
        //$ pose.scale(1.0F, -1.0F, -1.0F);
        //$ c.submitModel(model, net.minecraft.util.Unit.INSTANCE, pose, model.renderType(tex), licht, overlay, motiv, null, umriss, null);
        //$ if (farbe != -1) c.order(1).submitModel(model, net.minecraft.util.Unit.INSTANCE, pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(STATUS_RAHMEN), 0xF000F0, overlay, farbe, null, 0, null);
        //$ if (funkeln) c.order(2).submitModel(model, net.minecraft.util.Unit.INSTANCE, pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityGlint(), licht, overlay, -1, null, 0, null);
        //$ pose.popPose();
        //#endif
        return true;
    }

    /** Shield-Status-Farbe (ARGB) oder -1 = keine Faerbung. */
    public static int farbe(DataComponentMap daten) {
        if (daten == null) return -1;
        CustomData cd = daten.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return -1;
        int f = cd.copyTag().getIntOr(FARBE, 0);
        return f == 0 ? -1 : f;
    }
}
