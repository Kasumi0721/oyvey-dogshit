#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 uvCoord;
out vec4 fragColor;

void main() {
    vec2 p = uvCoord * 2.0 - 1.0;
    float d = length(p);
    if (d > 1.0) discard;

    float progress = clamp(vertexColor.a, 0.0, 1.0);
    float eased = 1.0 - pow(1.0 - progress, 2.5);
    float radius = mix(0.22, 1.0, eased);

    // 更柔的边缘，中心稍暗
    float body = 1.0 - smoothstep(radius * 0.35, radius, d);
    body *= mix(0.55, 1.0, smoothstep(0.0, radius * 0.6, d));

    float fade = (1.0 - progress) * (1.0 - progress);
    float alpha = body * fade;
    if (alpha < 0.003) discard;

    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
}