#version 400

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

/*void main() {
    vec4 texColor = textureLod(Sampler0, texCoord0, 0.0);
    float dist = texColor.a - 127./255.;
    dist = abs(dist + 0.1) - 0.2;
    float afwidth = 0.5 * fwidth(dist);
    texColor.a = 1.0 - smoothstep(-afwidth, afwidth, dist);
    vec4 color = texColor * vertexColor * ColorModulator;
    if (color.a < 0.01) discard;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}*/

// why not try gaussian filter?
void main() {
    float lod = textureQueryLod(Sampler0, texCoord0).y;
    if (lod > 1.0) discard;
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    float dsum = 0.0;
    float wsum = 0.0;
    const int nstep = 3;
    const float w[3] = float[3](1.0,2.0,1.0);
    for (int i=0; i<nstep; ++i) {
        for (int j=0; j<nstep; ++j) {
            vec2 delta = vec2(float(i-1), float(j-1))/texSize;
            float wij = w[i]*w[j];
            vec4 samp = textureLod(Sampler0,texCoord0-delta,0.0);
            float dist = samp.w - 127./255.;
            dsum += wij * dist;
            wsum += wij;
        }
    }
    float dist = dsum / wsum;
    dist = abs(dist + 0.15) - 0.2;
    vec4 color = vertexColor * ColorModulator;
    color.a *= 1.0 - clamp(dist / fwidth(dist) + 0.5, 0.0, 1.0);
    if (color.a < 0.01) discard;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
