#ifndef INSTANCING
$input v_texcoord0, v_posTime
#endif

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/main.sh>

  SAMPLER2D_AUTOREG(s_SkyTexture);
#endif

void main() {
  #ifndef INSTANCING
    vec3 viewDir = normalize(v_posTime.xyz);
    vec2 uv = nlEndSkyUV(viewDir, NL_END_SKY_ROTATION);

    vec3 color = texture2D(s_SkyTexture, uv).rgb;

    // fake bloom: sample a ring of nearby points, add glow where they're bright
    vec3 bloom = vec3(0.0);
    const int NL_BLOOM_TAPS = 8;
    for (int i = 0; i < NL_BLOOM_TAPS; i++) {
      float ang = 6.28318530718 * (float(i) + 0.5) / float(NL_BLOOM_TAPS);
      vec2 offs = NL_END_BLOOM_RADIUS * vec2(cos(ang), sin(ang));
      vec3 s = texture2D(s_SkyTexture, uv + offs).rgb;
      float lum = dot(s, vec3(0.299,0.587,0.114));
      bloom += s * smoothstep(NL_END_BLOOM_THRESHOLD, 1.0, lum);
    }
    color += (bloom/float(NL_BLOOM_TAPS)) * NL_END_BLOOM_INTENSITY;

    color = colorCorrection(color);

    gl_FragColor = vec4(color, 1.0);
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  
#endif
}
