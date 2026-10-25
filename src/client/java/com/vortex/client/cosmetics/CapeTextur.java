package com.vortex.client.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.system.MemoryUtil;

/**
 * Textur der animierten Capes (seit 4.33) -- mit Mipmaps und weicher Filterung.
 *
 * Minecraft-Texturen werden ohne Mipmaps und "pixelig" (NEAREST) abgetastet. Unsere
 * Capes haben 12-fache Aufloesung; auf dem Bildschirm ist ein Cape aber oft nur 30-80
 * Pixel breit. Ohne Mipmaps pickt die Grafikkarte dann einzelne Texel heraus: Sterne
 * flimmern, Kanten sind treppig, alles wirkt verpixelt. Mit Mip-Stufen (jede halb so
 * gross, sauber gemittelt) und linearer Filterung bleibt das Cape in jeder Entfernung glatt.
 */
public final class CapeTextur extends AbstractTexture {

    /** 1536x768 -> 768 -> 384 -> 192 -> 96 -> 48 (seit 4.77 doppelt so fein) */
    public static final int STUFEN = 6;

    private final NativeImage[] bilder = new NativeImage[STUFEN];

    public CapeTextur(String name, int breite, int hoehe) {
        var device = RenderSystem.getDevice();
        //#if 26.2
        this.texture = device.createTexture(() -> name, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, breite, hoehe, 1, STUFEN);
        //#else
        //$ this.texture = device.createTexture(() -> name, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
        //$         com.mojang.blaze3d.textures.TextureFormat.RGBA8, breite, hoehe, 1, STUFEN);
        //#endif
        this.textureView = device.createTextureView(this.texture);
        this.sampler = abtaster();
        for (int s = 0; s < STUFEN; s++) bilder[s] = new NativeImage(Math.max(1, breite >> s), Math.max(1, hoehe >> s), false);
    }

    private static com.mojang.blaze3d.textures.GpuSampler ABTASTER;

    /**
     * Weich (linear) mit Mipmaps, dazu anisotrope Filterung: ein Cape wird oft schraeg
     * gesehen, dort bleibt es so scharf statt verwaschen. Kann die Grafikkarte das
     * nicht, nehmen wir weniger bzw. den normalen weichen Abtaster.
     */
    private static com.mojang.blaze3d.textures.GpuSampler abtaster() {
        if (ABTASTER != null) return ABTASTER;
        for (int aniso : new int[] { 8, 4, 2 }) {
            try {
                ABTASTER = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                        FilterMode.LINEAR, FilterMode.LINEAR, aniso, java.util.OptionalDouble.empty());
                return ABTASTER;
            } catch (Throwable ignored) {
            }
        }
        ABTASTER = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, true);
        return ABTASTER;
    }

    /** Alle Stufen hochladen (Render-Thread). stufen[s] = ABGR-Pixel der Stufe s. */
    public void hochladen(int[][] stufen) {
        if (this.texture == null || this.texture.isClosed()) return;
        var enc = RenderSystem.getDevice().createCommandEncoder();
        for (int s = 0; s < STUFEN; s++) {
            NativeImage b = bilder[s];
            MemoryUtil.memIntBuffer(b.getPointer(), stufen[s].length).put(0, stufen[s]);
            //#if 26.2
            enc.writeToTexture(this.texture, b, s, 0, 0, 0);
            //#else
            //$ enc.writeToTexture(this.texture, b, s, 0, 0, 0, b.getWidth(), b.getHeight(), 0, 0);
            //#endif
        }
    }

    @Override
    public void close() {
        for (NativeImage b : bilder) if (b != null) b.close();
        super.close();
    }
}
