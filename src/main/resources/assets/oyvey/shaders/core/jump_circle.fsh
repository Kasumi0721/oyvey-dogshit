#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 uvCoord;
out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

void main() {
    vec2 p = uvCoord * 2.0 - 1.0;
    float d = length(p);
    if (d > 1.0) discard;

    float theta = atan(p.y, p.x);
    float progress = clamp(vertexColor.a, 0.0, 1.0);

    // ── 缓动 ──
    float eased = 1.0 - pow(1.0 - progress, 3.0);
    float front = mix(0.08, 0.85, eased);

    // ── 极细主环（宽 0.018，高斯分布） ──
    float ringW = 0.018;
    float core = exp(-pow((d - front) / ringW, 2.0));

    // ── 宽而柔的外光晕（指数衰减，比 core 弱） ──
    float halo = exp(-abs(d - front) * 9.0) * 0.22;

    // ── 分段符文感（更细，对比更柔和） ──
    float seg = fract(theta / TAU * 24.0 + progress * 2.5);
    float segMask = smoothstep(0.04, 0.22, seg) * (1.0 - smoothstep(0.78, 0.96, seg));
    core *= (0.78 + 0.22 * segMask);

    // ── 内层副环（同相位，慢一步） ──
    float inner = exp(-pow((d - front * 0.55) / 0.011, 2.0)) * 0.45;
    // 副环也分段
    float seg2 = fract(theta / TAU * 16.0 - progress * 4.0);
    float seg2Mask = smoothstep(0.05, 0.25, seg2) * (1.0 - smoothstep(0.7, 0.9, seg2));
    inner *= (0.5 + 0.5 * seg2Mask);

    // ── 起始闪光（短暂白色圆弧） ──
    float flash = (1.0 - smoothstep(0.0, 0.09, progress))
    * exp(-pow(d / 0.42, 2.0));

    // ── 时间淡入淡出 ──
    float fade = smoothstep(0.0, 0.06, progress)
    * (1.0 - smoothstep(0.50, 1.0, progress));

    // ── 合成 ──
    float energy = core * 0.95 + halo + inner + flash * 0.6;
    float alpha = clamp(energy * fade, 0.0, 1.0);
    if (alpha < 0.003) discard;

    // 颜色：主色 + 高光提亮
    vec3 color = vertexColor.rgb * (core * 1.1 + halo * 0.8 + inner * 0.9);
    color += vec3(1.0) * (flash * 0.7 + core * core * 0.15);

    fragColor = vec4(color, alpha) * ColorModulator;
}