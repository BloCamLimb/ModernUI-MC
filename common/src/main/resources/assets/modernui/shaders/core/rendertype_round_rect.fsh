// This file is part of Modern UI.
// Copyright (C) 2024 BloCamLimb.
// Licensed under LGPL-3.0-or-later.
#version 150

uniform vec4 u_Rect;
uniform vec2 u_Radii;
uniform vec4 ColorModulator;

#define u_Center u_Rect.xy
#define u_Size u_Rect.zw
#define u_Radius u_Radii.x
#define u_Thickness u_Radii.y

in vec2 f_Position;
in vec4 f_Color;

out vec4 fragColor;

void main() {
    vec2 pos = f_Position - u_Center;
    vec2 d = abs(pos) - u_Size + u_Radius;
    float dis = length(max(d,0.0)) + min(max(d.x,d.y),0.0) - u_Radius;
    dis = mix(dis, abs(dis)-u_Thickness, float(u_Thickness>=0.0));

    vec4 color = f_Color * ColorModulator;
    // minecraft uses non-premultiplied alpha
    color.a *= 1.0-clamp(dis/fwidth(dis)+0.5, 0.0, 1.0);
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
