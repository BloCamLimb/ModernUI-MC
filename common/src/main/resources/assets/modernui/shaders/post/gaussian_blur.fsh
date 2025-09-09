// This file is part of Modern UI.
// Copyright (C) 2024 BloCamLimb.
// Licensed under LGPL-3.0-or-later.
#version 150

#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurInfo {
    vec2 BlurDir;
};

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

void main() {
    vec4 blur = vec4(0.0);

    int radius = MenuBlurRadius;
    // sigma = radius / 2.0
    // base = -0.5 / (sigma * sigma)
    // factor = 1.0 / (sigma * sqrt(2*PI))
    float base = -2.0 / (radius * radius);
    float factor = 0.79788456 / radius;
    ivec2 bound = ivec2(InSize) - 1;
    ivec2 basePos = ivec2(texCoord * InSize);
    ivec2 blurDir = ivec2(BlurDir);
    float wsum = 0.0, w;
    for (int r = -radius; r <= radius; r += 1) {
        w = exp(r * r * base) * factor;
        blur += texelFetch(InSampler, clamp(basePos + r * blurDir, ivec2(0), bound), 0) * w;
        wsum += w;
    }

    fragColor = vec4(blur.rgb / wsum, 1.0);
}