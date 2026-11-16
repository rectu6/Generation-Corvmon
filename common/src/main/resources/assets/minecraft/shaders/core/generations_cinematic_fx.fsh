#version 150

// Legendary cinematics: texture x scrolling noise x rim x colour, faded against the depth of
// the world (soft edges where a sheet meets the ground). Blending is set by the renderer.

uniform sampler2D Sampler0; // the shape
uniform sampler2D Sampler1; // the noise
uniform sampler2D Sampler2; // the depth of the world (a copy)

uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
uniform float FxTime;
// x, y: the scroll of the noise (per second); z: its scale; w: how much it erodes the shape (0 = none).
uniform vec4 FxNoise;
// x: rim power (0 = none); y: rim mix (1 = only the rim); z: soft depth distance (0 = none); w: brightness.
uniform vec4 FxShape;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 relPos;
in vec3 worldNormal;

out vec4 fragColor;

float eyeDistance(float depth) {
    float z = depth * 2.0 - 1.0;
    return ProjMat[3][2] / (z + ProjMat[2][2]);
}

void main() {
    vec4 colour = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (FxNoise.w > 0.0) {
        vec2 uv = texCoord0 * FxNoise.z + FxNoise.xy * FxTime;
        float n = texture(Sampler1, uv).r * 0.65 + texture(Sampler1, uv * 2.13 + vec2(0.37, 0.11) - FxNoise.xy * FxTime * 0.5).g * 0.35;
        float erosion = smoothstep(0.18, 0.82, n) * 1.6;
        colour.a *= mix(1.0, erosion, FxNoise.w);
    }
    if (FxShape.x > 0.0) {
        vec3 view = normalize(-relPos);
        vec3 normal = normalize(worldNormal + vec3(1.0e-4));
        float rim = pow(1.0 - abs(dot(normal, view)), FxShape.x);
        // A negative mix keeps the core instead (faded toward the edges).
        float keep = FxShape.y >= 0.0 ? rim : 1.0 - rim;
        colour.a *= mix(1.0, keep, abs(FxShape.y));
    }
    if (FxShape.z > 0.0) {
        vec2 screen = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
        float world = eyeDistance(texture(Sampler2, screen).r);
        float self = eyeDistance(gl_FragCoord.z);
        colour.a *= clamp((world - self) / FxShape.z, 0.0, 1.0);
    }
    colour.rgb *= max(FxShape.w, 0.0);
    if (colour.a < 0.002) {
        discard;
    }
    fragColor = colour;
}
