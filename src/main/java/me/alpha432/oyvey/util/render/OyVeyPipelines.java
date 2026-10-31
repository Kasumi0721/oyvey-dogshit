package me.alpha432.oyvey.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

public final class OyVeyPipelines {

    /** 与 Gemini ADDITIVE 等价（SRC_ALPHA + ONE 加色混合） */
    private static final BlendFunction ADDITIVE = BlendFunction.OVERLAY;

    public static final RenderPipeline RIBBON_PIPELINE =
            ribbonPipeline("trajectory_ribbon", DepthTestFunction.LEQUAL_DEPTH_TEST);
    public static final RenderPipeline RIBBON_XRAY_PIPELINE =
            ribbonPipeline("trajectory_ribbon_xray", DepthTestFunction.NO_DEPTH_TEST);

    public static final RenderPipeline MARKER_PIPELINE =
            effectPipeline("trajectory_marker", "core/trajectory_landing", DepthTestFunction.LEQUAL_DEPTH_TEST);
    public static final RenderPipeline MARKER_XRAY_PIPELINE =
            effectPipeline("trajectory_marker_xray", "core/trajectory_landing", DepthTestFunction.NO_DEPTH_TEST);

    public static final RenderPipeline FLARE_PIPELINE =
            effectPipeline("projectile_flare", "core/projectile_flare", DepthTestFunction.LEQUAL_DEPTH_TEST);
    public static final RenderPipeline FLARE_XRAY_PIPELINE =
            effectPipeline("projectile_flare_xray", "core/projectile_flare", DepthTestFunction.NO_DEPTH_TEST);

    private OyVeyPipelines() {}

    private static RenderPipeline ribbonPipeline(String path, DepthTestFunction depth) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("oyvey", "pipeline/" + path))
                .withVertexShader(Identifier.fromNamespaceAndPath("oyvey", "core/trajectory"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("oyvey", "core/trajectory_ribbon"))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(depth)
                .withBlend(ADDITIVE)
                .withCull(false)
                .build();
    }

    private static RenderPipeline effectPipeline(String path, String fragmentPath, DepthTestFunction depth) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("oyvey", "pipeline/" + path))
                .withVertexShader(Identifier.fromNamespaceAndPath("oyvey", "core/trajectory"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("oyvey", fragmentPath))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(depth)
                .withBlend(ADDITIVE)
                .withCull(false)
                .build();
    }
}