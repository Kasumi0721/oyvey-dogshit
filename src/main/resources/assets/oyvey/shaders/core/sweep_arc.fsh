#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;

void main() {
    vec4 result;
    if (vColor.g < 0.5) {
        // arc 模式：vUv.y 横截面，vUv.x 长度方向
        float across = abs(vUv.y);
        if (across > 1.0) discard;
        float core = exp(-across * across * 8.0);
        float glow = exp(-across * 2.0) * 0.45;
        float edge = smoothstep(0.0, 0.06, vUv.x) * smoothstep(0.0, 0.08, 1.0 - vUv.x);
        float energy = (core + glow) * edge;
        vec3 color = vColor.rgb * energy;
        color += vec3(1.0) * core * core * 0.4;
        result = vec4(color, energy * vColor.a);
    } else if (vColor.g < 1.5) {
        // ring / burst 模式：vUv 是单位圆坐标
        vec2 p = (vUv - 0.5) * 2.0;
        float d = length(p);
        float ring = exp(-abs(d - 0.735) * 12.0);
        float outer = exp(-abs(d - 0.735) * 5.0) * 0.3;
        float core = exp(-d * d * 14.0);
        float energy = ring + outer + core * 0.6;
        vec3 color = vColor.rgb * energy;
        color += vec3(1.0) * core * 0.6;
        result = vec4(color, energy * vColor.a);
    } else {
        result = vec4(0.0);
    }

    fragColor = result * ColorModulator;
    if (fragColor.a < 0.002) discard;
}