#version 150

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, uvCoord);
    if (tex.a < 0.05) discard;
    fragColor = tex * vertexColor;
}