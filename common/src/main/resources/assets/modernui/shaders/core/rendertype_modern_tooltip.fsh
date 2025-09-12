// This file is part of Modern UI.
// Copyright (C) 2024 BloCamLimb.
// Licensed under LGPL-3.0-or-later.
#version 150

layout(std140) uniform ModernTooltip {
    mat4 u_LocalMat;
    vec4 u_PushData0;
    vec3 u_PushData1;
    vec4 u_PushData2;
    vec4 u_PushData3;
    vec4 u_PushData4;
    vec4 u_PushData5;
    float u_RainbowOffset;
};

#define u_Size u_PushData0.xy
#define u_Radius u_PushData0.z
#define u_Thickness u_PushData0.w
#define u_ShadowAlpha u_PushData1.x
#define u_ShadowSpread u_PushData1.y
#define u_BackgroundAlpha u_PushData1.z

in vec2 f_Position;

out vec4 fragColor;

float noise1(float seed1, float seed2) {
    return(
    fract(seed1+12.34567*
    fract(100.*(abs(seed1*0.91)+seed2+94.68)*
    fract((abs(seed2*0.41)+45.46)*
    fract((abs(seed2)+757.21)*
    fract(seed1*0.0171))))))
    * 1.0038 - 0.00185;
}

vec4 dither(vec4 color) {
    // Unrolled 8x8 Bayer matrix
    vec2 A = gl_FragCoord.xy;
    vec2 B = floor(A);
    float U = fract(B.x * 0.5 + B.y * B.y * 0.75);
    vec2 C = A * 0.5;
    vec2 D = floor(C);
    float V = fract(D.x * 0.5 + D.y * D.y * 0.75);
    vec2 E = C * 0.5;
    vec2 F = floor(E);
    float W = fract(F.x * 0.5 + F.y * F.y * 0.75);
    float dithering = ((W * 0.25 + V) * 0.25 + U) - (63.0 / 128.0);
    return vec4(clamp(color.rgb + dithering * (1.0 / 255.0), 0.0, 1.0), color.a);
}

void main() {
    vec2 pos = f_Position;
    vec2 d = abs(pos) - u_Size + u_Radius;
    float dis = length(max(d,0.0)) + min(max(d.x,d.y),0.0) - u_Radius;

    vec4 border;

    if (!bool(u_RainbowOffset)) {
        // clamp01 for AA bloat
        vec2 t = clamp(0.5*pos/(u_Size+u_Thickness)+0.5,0.0,1.0);
        vec3 q11 = pow(u_PushData2.rgb,vec3(2.2));
        vec3 q21 = pow(u_PushData3.rgb,vec3(2.2));
        vec3 q12 = pow(u_PushData4.rgb,vec3(2.2));
        vec3 q22 = pow(u_PushData5.rgb,vec3(2.2));
        vec3 col = mix(mix(q11,q21,t.x),mix(q12,q22,t.x),t.y);
        border = vec4(pow(col,vec3(1.0/2.2)),1.0);
    } else {
        float t = atan(-pos.y, -pos.x) * 0.1591549430918;
        float hue = mod(t+u_RainbowOffset,1.0);
        const vec4 K = vec4(1,2./3.,1./3.,3);
        vec3 rgb = clamp(abs(fract(hue+K.xyz)*6.-K.w)-K.x,0.,1.);
        // reduce brightness
        border = vec4(rgb*vec3(0.9,0.85,0.9),1.0);
    }

    //float shadow = u_ShadowAlpha * exp(-u_ShadowSpread * abs(dis-u_Thickness));
    float shadow = pow(1.0-(u_ShadowSpread*clamp(dis-u_Thickness,0.0,1.0/u_ShadowSpread)),3.0);
    shadow = u_ShadowAlpha * (shadow + (noise1(gl_FragCoord.x,gl_FragCoord.y)-1.0) * 0.05);
    float dstA = max(shadow, step(dis,0.0)) * u_BackgroundAlpha;
    float f = abs(dis)-u_Thickness;
    float afwidth = fwidth(f);
    float srcA = border.a * (1.0-clamp(f/afwidth+0.5, 0.0, 1.0));
    float alpha = srcA + (1.0-srcA) * dstA;
    if (alpha < 0.002) {
        discard;
    }
    // minecraft uses non-premultiplied alpha
    fragColor = dither(vec4(border.rgb * srcA / alpha, alpha));
}
