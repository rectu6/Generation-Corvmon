#version 150

// Legendary cinematics: the 3D effects (Generations-Core, client/cinematic/fx). Positions are
// relative to the camera; the normal (for the rim) is in the same space.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 relPos;
out vec3 worldNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    relPos = Position;
    worldNormal = Normal;
}
