// Copyright (C) 2024 BloCamLimb. All rights reserved.
// Licensed under LGPL-3.0-or-later.
#version 150

uniform vec4 u_PushData0;
uniform vec4 u_PushData1;
uniform vec4 u_PushData2;
uniform vec4 u_PushData3;
uniform vec4 u_PushData4;
uniform vec4 u_PushData5;

#define u_Size u_PushData0.xy
#define u_Radius u_PushData0.z
#define u_Thickness u_PushData0.w
#define u_ShadowAlpha u_PushData1.x
#define u_ShadowSpread u_PushData1.y
#define u_BackgroundAlpha u_PushData1.z
#define u_RainbowOffset u_PushData1.w

in vec2 f_Position;

out vec4 fragColor;

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

    float shadow = u_ShadowAlpha * exp(-u_ShadowSpread * abs(dis-u_Thickness));
    float dstA = max(shadow, step(dis,0.0)) * u_BackgroundAlpha;
    float f = abs(dis)-u_Thickness;
    float afwidth = 0.7 * length(vec2(dFdx(f),dFdy(f)));
    float srcA = border.a * (1.0-smoothstep(-afwidth, afwidth, f));
    float alpha = srcA + (1.0-srcA) * dstA;
    if (alpha < 0.002) {
        discard;
    }
    // minecraft uses non-premultiplied alpha
    fragColor = vec4(border.rgb * srcA / alpha, alpha);
}
