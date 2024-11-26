// This file is part of Modern UI.
// Copyright (C) 2024 BloCamLimb.
// Licensed under LGPL-3.0-or-later.
#version 150

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 Position;

out vec2 f_Position;

void main() {
    f_Position = Position.xy;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
