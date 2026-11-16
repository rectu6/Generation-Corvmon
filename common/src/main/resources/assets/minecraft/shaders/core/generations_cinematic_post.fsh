#version 150

// Legendary cinematics: the final composition over the frame. Shock waves and heat bend the
// image (medium and high); the light of the effects and its bloom are added, with chromatic
// aberration and light rays (high); then the grade, the vignette and the (limited) flash.

uniform sampler2D Sampler0; // the frame
uniform sampler2D Sampler1; // the light of the effects (high)
uniform sampler2D Sampler2; // its bloom, half size
uniform sampler2D Sampler3; // its bloom, quarter size
uniform sampler2D Sampler4; // the noise

uniform float FxTime;
uniform float Quality;      // 1 medium, 2 high
uniform float Aspect;       // width / height
uniform vec4 Wave0;         // centre (uv), radius (screen heights), strength
uniform vec4 Wave1;
uniform vec2 WaveWidth;
uniform vec4 Heat;          // amount, speed, scale, lowest screen height
uniform vec4 Tint;          // rgb, exposure
uniform vec4 Look;          // saturation, contrast, vignette, aberration
uniform vec4 Flash;         // rgb, amount
uniform vec4 Rays;          // light (uv), strength, decay
uniform vec2 Glow;          // light, bloom
uniform vec4 Radial;        // radial blur: centre (uv), strength (high)

in vec2 texCoord0;

out vec4 fragColor;

vec2 wave(vec4 w, float width, vec2 uv) {
    if (w.w <= 0.0) {
        return vec2(0.0);
    }
    vec2 d = uv - w.xy;
    d.x *= Aspect;
    float dist = length(d);
    float k = (dist - w.z) / max(width, 1.0e-4);
    float ring = exp(-k * k * 2.0) * w.w;
    vec2 dir = d / max(dist, 1.0e-4);
    dir.x /= Aspect;
    return dir * ring * 0.035;
}

void main() {
    vec2 uv = texCoord0;
    vec2 offset = wave(Wave0, WaveWidth.x, uv) + wave(Wave1, WaveWidth.y, uv);
    if (Heat.x > 0.0) {
        vec2 n = texture(Sampler4, uv * Heat.z * vec2(Aspect, 1.0) + vec2(0.0, -FxTime * Heat.y)).rg - 0.5;
        offset += n * Heat.x * 0.012 * smoothstep(Heat.w, Heat.w + 0.3, 1.0 - uv.y);
    }
    vec2 suv = clamp(uv + offset, vec2(0.0005), vec2(0.9995));

    vec3 colour;
    if (Quality > 1.5 && Look.w > 0.0) {
        vec2 shift = (suv - 0.5) * Look.w * 0.012;
        colour = vec3(texture(Sampler0, suv + shift).r, texture(Sampler0, suv).g, texture(Sampler0, suv - shift).b);
    } else {
        colour = texture(Sampler0, suv).rgb;
    }
    if (Radial.z > 0.0) {
        vec2 toward = suv - Radial.xy;
        vec3 sum = colour;
        for (int i = 1; i <= 8; i++) {
            sum += texture(Sampler0, clamp(suv - toward * (float(i) / 8.0) * Radial.z * 0.09, vec2(0.0005), vec2(0.9995))).rgb;
        }
        colour = sum / 9.0;
    }

    if (Quality > 1.5) {
        colour += texture(Sampler1, suv).rgb * Glow.x;
        colour += (texture(Sampler2, suv).rgb * 0.55 + texture(Sampler3, suv).rgb * 0.85) * Glow.y;
        if (Rays.z > 0.0) {
            vec2 rayStep = (Rays.xy - suv) / 24.0;
            vec2 p = suv;
            float fade = 1.0;
            vec3 rays = vec3(0.0);
            for (int i = 0; i < 24; i++) {
                p += rayStep;
                rays += texture(Sampler2, clamp(p, vec2(0.0), vec2(1.0))).rgb * fade;
                fade *= Rays.w;
            }
            colour += rays / 24.0 * Rays.z;
        }
    }

    colour *= Tint.rgb * Tint.a;
    float luma = dot(colour, vec3(0.2126, 0.7152, 0.0722));
    if (Quality > 1.5) {
        colour = mix(vec3(luma), colour, Look.x);
        colour = (colour - 0.5) * Look.y + 0.5;
        // Cold shadows, a little warmth kept in the lights.
        colour += (vec3(-0.02, 0.0, 0.035) * (1.0 - luma) + vec3(0.01, 0.005, -0.01) * luma) * (Look.y - 1.0) * 6.0;
    } else {
        colour = mix(vec3(luma), colour, mix(1.0, Look.x, 0.5));
    }
    vec2 v = (uv - 0.5) * vec2(Aspect / max(Aspect, 1.0), 1.0) * 1.35;
    float vignette = 1.0 - smoothstep(0.35, 1.05, length(v));
    colour *= mix(1.0, vignette, Look.z);
    colour = mix(colour, Flash.rgb, clamp(Flash.a, 0.0, 1.0));
    fragColor = vec4(max(colour, vec3(0.0)), 1.0);
}
