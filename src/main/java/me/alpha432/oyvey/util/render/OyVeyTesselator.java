package me.alpha432.oyvey.util.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * 精简版 Tesselator 替代品
 * 1.21.11 里 Tesselator 已移除，RenderType.draw(MeshData) 已处理所有 GPU 上传
 */
public final class OyVeyTesselator {
    private static final int MAX_BYTES = 786_432;
    private static final OyVeyTesselator INSTANCE = new OyVeyTesselator();

    private final ByteBufferBuilder stagingBuffer = new ByteBufferBuilder(MAX_BYTES);

    private OyVeyTesselator() {}

    public static OyVeyTesselator getInstance() {
        return INSTANCE;
    }

    /** 创建 BufferBuilder（用 Mode 而不是 PrimitiveTopology） */
    public BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        return new BufferBuilder(this.stagingBuffer, mode, format);
    }

    /** 提交并绘制 */
    public static void draw(RenderType renderType, MeshData mesh) {
        renderType.draw(mesh);
    }
}