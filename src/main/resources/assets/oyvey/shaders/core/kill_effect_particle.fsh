#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 uvCoord;
out vec4 fragColor;

// ═══════════════════════════════════════════════════════════════════
//  Kill Effect Particle fragment shader
//
//  vertexColor.r = lifeRatio (0→1)
//  vertexColor.g = mode: 0=accretion, 0.5=burst, 1=sky
//  vertexColor.b = unused
//  vertexColor.a = master alpha
// ═══════════════════════════════════════════════════════════════════

void main() {
    // Sphere impostor: UV centered, circular mask
    vec2 p = (uvCoord - 0.5) * 2.0;
    float d = length(p);

    float sphere = 1.0 - smoothstep(0.7, 1.0, d);
    float core = exp(-d * d * 6.0);
    float brightness = core * 0.8 + sphere * 0.4;

    float lifeRatio = vertexColor.r;
    float masterAlpha = vertexColor.a;
    bool skyMode   = vertexColor.g > 0.75;
    bool burstMode = vertexColor.g > 0.24 && vertexColor.g <= 0.75;

    float diffraction = (exp(-abs(p.x) * 30.0) * exp(-abs(p.y) * 3.5)
    + exp(-abs(p.y) * 30.0) * exp(-abs(p.x) * 3.5)) * 0.28;
    if (skyMode) brightness += diffraction;
    if (burstMode) brightness += exp(-d * d * 1.15) * 0.18;

    vec3 color;
    if (skyMode) {
        // Sky particle: cool celestial tones
        vec3 hotColor   = vec3(1.0, 1.0, 1.0);
        vec3 midColor   = vec3(0.55, 0.75, 1.0);
        vec3 coolColor  = vec3(0.15, 0.30, 0.80);
        vec3 deadColor  = vec3(0.02, 0.04, 0.10);

        float t = lifeRatio;
        if (t < 0.4) {
            float p0 = t / 0.4;
            color = mix(hotColor, midColor, p0);
        } else if (t < 0.7) {
            float p1 = (t - 0.4) / 0.3;
            color = mix(midColor, coolColor, p1);
        } else {
            float p2 = (t - 0.7) / 0.3;
            color = mix(coolColor, deadColor, p2);
        }
    } else if (burstMode) {
        // Burst mode: explosion debris, HDR hot → cool smoke
        vec3 c0 = vec3(1.00, 1.00, 0.95);
        vec3 c1 = vec3(1.00, 0.80, 0.35);
        vec3 c2 = vec3(1.00, 0.42, 0.08);
        vec3 c3 = vec3(0.30, 0.05, 0.02);

        float t = lifeRatio;
        color = mix(c0, c1, smoothstep(0.0, 0.30, t));
        color = mix(color, c2, smoothstep(0.30, 0.65, t));
        color = mix(color, c3, smoothstep(0.65, 1.0, t));

        // HDR at birth
        float heat = 2.6 * (1.0 - t) * (1.0 - t) + 0.7;
        color *= heat;
    } else {
        // Accretion mode: hot debris tones
        vec3 hotColor   = vec3(1.0, 0.95, 0.70);
        vec3 midColor   = vec3(1.0, 0.55, 0.08);
        vec3 coolColor  = vec3(0.60, 0.08, 0.02);
        vec3 deadColor  = vec3(0.15, 0.02, 0.00);

        float t = lifeRatio;
        if (t < 0.4) {
            float p0 = t / 0.4;
            color = mix(hotColor, midColor, p0);
        } else if (t < 0.7) {
            float p1 = (t - 0.4) / 0.3;
            color = mix(midColor, coolColor, p1);
        } else {
            float p2 = (t - 0.7) / 0.3;
            color = mix(coolColor, deadColor, p2);
        }
    }

    // Flicker for non-sky particles
    float flicker = 1.0;
    if (!skyMode) {
        flicker = 1.0 + sin(lifeRatio * 30.0 + uvCoord.x * 10.0) * 0.2;
    }
    color *= flicker;

    // Fade in/out based on life
    float fadeIn  = smoothstep(0.0, 0.1, lifeRatio);
    float fadeOut = 1.0 - smoothstep(0.7, 1.0, lifeRatio);
    float fade    = min(fadeIn, fadeOut);

    float finalAlpha = brightness * masterAlpha * fade;

    // Glow halo
    vec3 glow = color * exp(-d * d * 1.5) * 0.15;

    fragColor = vec4(color * brightness + glow, finalAlpha) * ColorModulator;

    if (fragColor.a < 0.002) {
        discard;
    }
}