#version 150

// Legendary cinematics: one direction of a 9-tap Gaussian blur (linear sampling: 5 reads).

uniform sampler2D Sampler0;
uniform vec2 BlurDir;
uniform float BlurGain;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec3 sum = texture(Sampler0, texCoord0).rgb * 0.2270270270;
    sum += (texture(Sampler0, texCoord0 + BlurDir * 1.3846153846).rgb + texture(Sampler0, texCoord0 - BlurDir * 1.3846153846).rgb) * 0.3162162162;
    sum += (texture(Sampler0, texCoord0 + BlurDir * 3.2307692308).rgb + texture(Sampler0, texCoord0 - BlurDir * 3.2307692308).rgb) * 0.0702702703;
    fragColor = vec4(sum * BlurGain, 1.0);
}
