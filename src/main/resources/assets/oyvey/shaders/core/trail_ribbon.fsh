#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

void main() {
    float av = abs(vUv.y);
    if (av > 1.0) discard;

    // 核心（中心最亮）
    float core = exp(-av * 7.5);
    // 光晕（柔和扩散）
    float glow = exp(-av * 2.4) * 0.45;
    // 边缘亮环
    float edge = exp(-abs(av - 0.72) * 14.0) * 0.35;
    // 流动纹理（vUv.x 里带了时间偏移）
    float flow = 0.70 + 0.30 * sin(vUv.x * 14.0);

    float energy = core * (1.0 + flow * 0.4) + glow + edge;
    float alpha = energy * vColor.a;
    if (alpha < 0.005) discard;

    vec3 color = vColor.rgb * energy;
    color += vec3(1.0) * core * core * 0.30;
    fragColor = vec4(color, alpha) * ColorModulator;
}