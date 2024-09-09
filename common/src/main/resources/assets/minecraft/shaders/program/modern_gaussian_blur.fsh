#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform vec2 BlurDir;
uniform float Progress;

out vec4 fragColor;

void main() {
    vec4 blur = vec4(0.0);

    int radius = int(Progress);
    // sigma = radius / sqrt(3)
    // base = -0.5 / (sigma * sigma)
    // factor = 1.0 / (sigma * sqrt(2*PI))
    float base = -1.5 / (radius * radius);
    float factor = 0.6909883 / radius;
    ivec2 bound = ivec2(InSize) - 1;
    ivec2 basePos = ivec2(texCoord * InSize + 0.5);
    ivec2 blurDir = ivec2(BlurDir);
    float wsum = 0.0, w;
    for (int r = -radius; r <= radius; r += 1) {
        w = exp(r * r * base) * factor;
        blur += texelFetch(DiffuseSampler, clamp(basePos + r * blurDir, ivec2(0), bound), 0) * w;
        wsum += w;
    }

    fragColor = vec4(blur.rgb / wsum, 1.0);
}