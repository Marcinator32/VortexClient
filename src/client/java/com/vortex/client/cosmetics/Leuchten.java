package com.vortex.client.cosmetics;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
//#if 26.2
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;

import java.util.HashMap;
import java.util.Map;
//#else
//$ import net.minecraft.client.renderer.rendertype.RenderTypes;
//#endif

/**
 * Leuchtende Flaechen der Cosmetics (Auren, Schein um Pixel-Auren, glaenzende Federn) seit 4.85.
 *
 * Bisher: Minecrafts "Energy Swirl" (wie beim geladenen Creeper) -- additiv, aber MIT
 * Tiefenschreiben. Die dunklen Ecken der Leucht-Flaechen sind dabei unsichtbar, schrieben
 * aber trotzdem Tiefe: alles, was danach gezeichnet wurde (z. B. Gesicht und Haare des
 * Spielers, die halbdurchsichtig gezeichnet werden), fiel dort weg -- schwarze Vierecke
 * neben den Augen.
 *
 * Jetzt: dieselbe Pipeline Schritt fuer Schritt nachgebaut, nur ohne Tiefenschreiben und
 * (seit 4.87) ohne Alpha zu veraendern. Das Leuchten wird weiter von Waenden verdeckt,
 * deckt selbst aber nichts mehr zu. Aussehen sonst unveraendert.
 */
public final class Leuchten {
    private Leuchten() {}

    //#if 26.2
    /**
     * Farbe additiv (wie Minecrafts ADDITIVE), Alpha aber unveraendert. ADDITIVE addiert auch
     * Alpha: die dunklen Ecken der Leucht-Flaechen machten das Zwischenbild, in das das
     * Hauptmenue die Figur zeichnet, an diesen Stellen undurchsichtig -- schwarze Vierecke.
     */
    private static final BlendFunction NUR_FARBE = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE);

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("vortexclient", "pipeline/leuchten"))
                    .withVertexShader("core/entity")
                    .withFragmentShader("core/entity")
                    .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withShaderDefine("APPLY_TEXTURE_MATRIX")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withColorTargetState(new ColorTargetState(NUR_FARBE))
                    .withCull(false)
                    .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                    .build());

    private static final Map<Identifier, RenderType> TYPEN = new HashMap<>();
    //#endif

    /** Render-Art fuer leuchtende Flaechen mit dieser Textur (weiss = hell, schwarz = nichts). */
    public static RenderType typ(Identifier textur) {
        //#if 26.2
        return TYPEN.computeIfAbsent(textur, t -> RenderType.create("vortexclient_leuchten",
                RenderSetup.builder(PIPELINE)
                        .withTexture("Sampler0", t)
                        .setTextureTransform(new TextureTransform.OffsetTextureTransform(0f, 0f))
                        .useLightmap()
                        .useOverlay()
                        .sortOnUpload()
                        .createRenderSetup()));
        //#else
        //$ return RenderTypes.energySwirl(textur, 0f, 0f);
        //#endif
    }
}
