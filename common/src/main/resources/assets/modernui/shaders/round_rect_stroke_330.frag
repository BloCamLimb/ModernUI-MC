#version 330 core

layout(std140) uniform SmoothBlock {
    float u_SmoothRadius;
};
layout(std140) uniform PaintBlock {
    vec4 u_InnerRect;
    float u_Radius;
    float u_StrokeRadius;
};

in vec2 f_Position;
in vec4 f_Color;

out vec4 fragColor;

float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

void main() {
    vec2 tl = u_InnerRect.xy - f_Position;
    vec2 br = f_Position - u_InnerRect.zw;

    vec2 dis = max(br, tl);

    float v = length(max(vec2(0.0), dis)) - u_Radius;
    v = abs(v) - u_StrokeRadius;

    float a = u_SmoothRadius > 0.0
    ? 1.0 - smoothstep(-u_SmoothRadius, 0.0, v)
    : 1.0 - aastep(v);

    fragColor = f_Color * a;
}