$input v_texcoord0, v_isSun

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/config.h>
  #include <newb/functions/tonemap.h>

  uniform vec4 SunMoonColor;

  SAMPLER2D_AUTOREG(s_SunMoonTexture);
#endif

void main() {
  #ifndef INSTANCING
    vec4 color = texture2D(s_SunMoonTexture, v_texcoord0);
    color.rgb *= SunMoonColor.rgb;
    // moon: keep the old bright glow boost. sun: keep the actual sun.png colors/detail, just a mild lift
    color.rgb = mix(color.rgb*1.5, 4.4*color.rgb*color.rgb, 1.0-v_isSun);
    // sun: sharpen alpha so the hex shape reads instead of the texture's soft round halo
    float sunAlpha = smoothstep(0.2, 0.6, color.a);
    color.a = mix(color.a, sunAlpha, v_isSun);
    float tr = 1.0 - SunMoonColor.a;
    color.a *= 1.0 - tr*tr;
    color.rgb = colorCorrection(color.rgb);
    gl_FragColor = color;
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
