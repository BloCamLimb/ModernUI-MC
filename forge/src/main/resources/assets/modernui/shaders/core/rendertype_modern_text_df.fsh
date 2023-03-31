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
    float dist = texColor.a - 0.5;

    /*vec2 grad = vec2(dFdx(dist), dFdy(dist));
    float afwidth = 0.7 * length(grad);*/ // L2 norm (exact)
    float afwidth = 0.5 * fwidth(dist);   // L1 norm (faster)

    // Minecraft uses non-premultiplied alpha blending
    texColor.a = smoothstep(-afwidth - 0.04, afwidth - 0.04, dist);

    vec4 color = texColor * vertexColor * ColorModulator;
    if (color.a < 0.01) discard; // requires alpha test
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
