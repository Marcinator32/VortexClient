package com.vortex.client.cosmetics;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Bewegung fuer die Elytra (seit 4.67). Bei Minecraft haengt sie beim Gehen starr am
 * Ruecken -- nur Schleichen und Fliegen aendern sie. Hier bekommt sie dieselben Werte wie
 * das Cape (capeLean, capeFlap, capeLean2 -- Minecrafts eigene Cape-Physik):
 *
 *   - nach hinten wehen, je schneller man laeuft (wie das Cape, etwas weniger)
 *   - mit den Schritten wippen, im Stehen ganz leicht atmen
 *   - beim Laufen ein wenig abspreizen, beim Drehen zur Seite mitgehen
 *
 * Im Flug (Elytra offen) aendert sich nichts. Abschaltbar mit "Cape physics: Off".
 */
public final class ElytraPhysik {
    private ElytraPhysik() {}

    private static final float GRAD = (float) Math.PI / 180f;

    public static void anwenden(HumanoidRenderState s, ModelPart links, ModelPart rechts) {
        if (s.isFallFlying || !(s instanceof AvatarRenderState a) || !Cosmetics.capePhysik()) return;
        float tempo = Math.min(1f, a.walkAnimationSpeed);
        float zeit = a.ageInTicks;
        float lean = Math.max(0f, Math.min(55f, a.capeLean / 2f + a.capeFlap));
        float atmen = (float) Math.sin(zeit * 0.09f) * (0.8f + 1.4f * tempo);
        float wippen = (float) Math.sin(a.walkAnimationPos * 0.6662f * 2f) * tempo * 3f;
        float hinten = (lean * 0.6f + atmen + wippen) * GRAD;
        float spreizen = (tempo * 6f + Math.abs((float) Math.sin(zeit * 0.07f)) * 1.2f) * GRAD;
        float seite = Math.max(-12f, Math.min(12f, a.capeLean2 / 4f)) * GRAD;
        links.xRot += hinten;
        rechts.xRot += hinten;
        links.zRot -= spreizen;
        rechts.zRot += spreizen;
        links.yRot += seite;
        rechts.yRot += seite;
    }
}
