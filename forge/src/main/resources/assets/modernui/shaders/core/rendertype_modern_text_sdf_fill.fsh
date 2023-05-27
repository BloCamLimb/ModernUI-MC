#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // must be BILINEAR sampling
    vec4 texColor = textureLod(Sampler0, texCoord0, 0.0);

    // apply distance field
    float dist = texColor.a - 127./255. + 0.04;

    /*vec2 grad = vec2(dFdx(dist), dFdy(dist));
    float afwidth = 0.7 * length(grad);*/ // L2 norm (exact)

    // Minecraft uses non-premultiplied alpha blending
    texColor.a = clamp(dist / fwidth(dist) + 0.5, 0.0, 1.0);

    vec4 color = texColor * vertexColor * ColorModulator;
    if (color.a < 0.01) discard; // requires alpha test
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
