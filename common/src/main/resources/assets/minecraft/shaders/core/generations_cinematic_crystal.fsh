#version 150

// Legendary cinematics: ice crystals. Fake refraction (the image behind, bent by the facet),
// a reflection, an inner glow and a bright rim. Opaque, writes the depth.

uniform sampler2D Sampler0; // the noise
uniform sampler2D Sampler1; // the image before the effects

uniform mat4 ModelViewMat;
uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
uniform float FxTime;
uniform vec4 CrystalTint;   // rgb, inner glow
// x: refraction (screen fraction); y: rim power; z: brightness; w: dissolve (0 = whole, 1 = gone).
uniform vec4 CrystalParams;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 relPos;
in vec3 worldNormal;

out vec4 fragColor;

void main() {
    vec3 normal = normalize(worldNormal + vec3(1.0e-4));
    vec3 view = normalize(-relPos);
    float facing = abs(dot(normal, view));
    float rim = pow(1.0 - facing, CrystalParams.y);
    float facet = texture(Sampler0, texCoord0 * 1.7 + vec2(FxTime * 0.03, 0.0)).r;
    float dissolve = CrystalParams.w + (1.0 - vertexColor.a);
    if (dissolve > 0.0 && texture(Sampler0, texCoord0 * 0.9 + vec2(0.31, 0.17)).g < dissolve) {
        discard;
    }
    vec3 viewNormal = (ModelViewMat * vec4(normal, 0.0)).xyz;
    vec2 screen = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    vec2 bent = clamp(screen + viewNormal.xy * CrystalParams.x * (0.6 + facet * 0.8), vec2(0.001), vec2(0.999));
    vec3 behind = texture(Sampler1, bent).rgb;
    vec3 tint = CrystalTint.rgb * vertexColor.rgb;
    vec3 inner = tint * (0.25 + facet * 0.75) * CrystalTint.a;
    vec3 light = normalize(vec3(0.35, 1.0, 0.25));
    float spec = pow(max(dot(reflect(-view, normal), light), 0.0), 28.0);
    vec3 colour = behind * mix(vec3(1.0), tint, 0.6) * (0.75 - rim * 0.35) + inner + rim * tint * 1.3 + vec3(spec) * 1.4;
    fragColor = vec4(colour * CrystalParams.z, 1.0) * ColorModulator;
}
