// Copyright (C) 2022 BloCamLimb. All rights reserved.
#version 450 core

layout(std140, binding = 0) uniform MatrixBlock {
    mat4 u_Projection;
    mat4 u_ModelView;
    vec4 u_Color;
};

layout(location = 0) smooth in vec2 f_Position;
layout(location = 1) smooth in vec4 f_Color;

layout(location = 0, index = 0) out vec4 fragColor;

float rand(vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 12.1414))) * 83758.5453);
}

void main() {
    vec2 pos = f_Position;

    vec4 col;

    float mt = mod(u_Color.x / 16.0, 2.0);
    if (mt >= 1.0) {
        float dist = abs(pos.y-sin(pos.x*10.0-u_Color.x*5.0)*0.1-cos(pos.x*5.0)*0.05);
        dist = pow(0.1/dist, 0.8);

        col = vec4(mix(vec3(0.2, 0.85, 0.95), vec3(0.85, 0.5, 0.75), pos.x*0.5+0.5), 1.0);
        col *= dist;
    } else {
        float f = 0.0;

        for (float i = 0.0; i < 30; i++) {
            float t=mod(i/2.0 + u_Color.x, 15.0),
            r=sqrt(t)/12.0,
            s=sin(t),
            c=cos(t);
            f += smoothstep(0.0, 1.0, t)*(1.0-smoothstep(14.0, 15.0, t))*0.01 / abs(distance(pos*0.5, vec2(s, -c)*r));
        }

        col = vec4(0.3, 0.6, 1.0, 1.0)*f;
    }
    col += (rand(pos.yx)-0.5)*0.02;
    col = 1.0 - exp(-col*0.5);

    fragColor = col;
}