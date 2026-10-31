#version 150

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a < 0.004) discard;
    fragColor = color;
}