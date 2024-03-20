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
    // lodBias should be -0.5/guiScale in Minecraft normalized GUI coordinates
    // lodBias guarantees NEAREST sampling (sharpen) in despite of float errors
    vec4 texColor = texture(Sampler0, texCoord0, -0.2375);
    vec4 color = texColor * vertexColor * ColorModulator;
    if (color.a < 0.01) discard;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
