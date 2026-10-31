package me.alpha432.oyvey.util.render;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import static me.alpha432.oyvey.util.render.Pipelines.GLOBAL_GLOW_PIPELINE;
import static me.alpha432.oyvey.util.render.Pipelines.GLOBAL_LINES_PIPELINE;
import static me.alpha432.oyvey.util.render.Pipelines.GLOBAL_QUADS_PIPELINE;
import static me.alpha432.oyvey.util.render.Pipelines.GLOW_QUAD_PIPELINE;

public class Layers {
    private static final RenderType GLOBAL_QUADS = RenderType.create("global_fill",
            RenderSetup.builder(GLOBAL_QUADS_PIPELINE).createRenderSetup());
    private static final RenderType GLOBAL_LINES = RenderType.create("global_lines",
            RenderSetup.builder(GLOBAL_LINES_PIPELINE).createRenderSetup());
    private static final RenderType GLOBAL_GLOW = RenderType.create("global_glow",
            RenderSetup.builder(GLOBAL_GLOW_PIPELINE).createRenderSetup());

    private static final RenderType GLOW_QUAD = RenderType.create("glow_quad",
            RenderSetup.builder(GLOW_QUAD_PIPELINE)
                    .withTexture("Sampler0",
                            Identifier.fromNamespaceAndPath("oyvey", "textures/hud/elements/capture.png"))
                    .createRenderSetup());

    public static RenderType quads() {
        return GLOBAL_QUADS;
    }

    public static RenderType lines() {
        return GLOBAL_LINES;
    }

    public static RenderType glow() {
        return GLOBAL_GLOW;
    }

    public static RenderType glowQuad() {
        return GLOW_QUAD;
    }
}