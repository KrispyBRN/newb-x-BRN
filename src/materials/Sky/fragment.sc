#ifndef INSTANCING
  $input v_worldPos, v_underwaterRainTimeDay
#endif

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/main.sh>
  uniform vec4 TimeOfDay;
  uniform vec4 Day;
  uniform vec4 FogColor;
  uniform vec4 FogAndDistanceControl;

  #ifdef NL_MILKYWAY
    SAMPLER2D_AUTOREG(s_MilkyWayTex);
  #endif
  #if defined(NL_SKY_AURORA_3D) || defined(NL_DEBUG_AURORATEX_TEST)
    SAMPLER2D_AUTOREG(s_AuroraNoiseTex);

    float nlAurora_pow2(float x) { return x*x; }
    float nlAurora_clamp01(float x) { return clamp(x, 0.0, 1.0); }
    float nlAurora_sqrt1(float x) { return sqrt(max(x, 0.0)); }

    vec3 nlGetAurora3D(vec3 vDir, float time, float dither) {
      float VdotU = clamp(vDir.y, 0.0, 1.0);
      float visibility = nlAurora_sqrt1(nlAurora_clamp01(VdotU*4.5 - 0.225));
      visibility *= 4.0 - VdotU*0.9;
      if (visibility <= 1.0) return vec3(0.0, 0.0, 0.0);

      vec3 aurora = vec3(0.0, 0.0, 0.0);
      vec3 wpos = vDir;
      wpos.xz /= max(wpos.y, 0.1);
      vec2 cameraPosM = vec2(0.0, 0.0);
      cameraPosM.x += time*NL_SKY_AURORA_3D_SPEED;

      const int sampleCount = NL_SKY_AURORA_3D_SAMPLES;
      const int sampleCountP = sampleCount + 10;

      float ditherM = dither + 10.0;
      float auroraAnimate = time*NL_SKY_AURORA_3D_DETAIL_ANIMATE;

      for (int i = 0; i < sampleCount; i++) {
        float current = nlAurora_pow2((float(i) + ditherM) / float(sampleCountP));
        vec2 planePos = wpos.xz*(0.8 + current)*10.0 + cameraPosM;
        planePos *= 0.0007;
        float noise = texture2D(s_AuroraNoiseTex, planePos).r;
        noise = nlAurora_pow2(nlAurora_pow2(nlAurora_pow2(nlAurora_pow2(1.0 - 0.8*abs(noise - 0.5)))));
        noise *= texture2D(s_AuroraNoiseTex, planePos*8.0 + auroraAnimate).b;
        noise *= texture2D(s_AuroraNoiseTex, planePos*1.0 - auroraAnimate).g;
        float currentM = 1.0 - current;
        aurora += noise*currentM*mix(NL_SKY_AURORA_3D_COLOR_A, NL_SKY_AURORA_3D_COLOR_B, nlAurora_pow2(nlAurora_pow2(currentM)));
      }

      aurora *= NL_SKY_AURORA_3D_INTENSITY;
      return aurora*visibility/float(sampleCount);
    }
  #endif
#endif

void main() {
  #ifndef INSTANCING
    vec3 viewDir = normalize(v_worldPos);

    nl_environment env;
    env.end = false;
    env.nether = false;
    env.underwater = v_underwaterRainTimeDay.x > 0.5;
    env.rainFactor = v_underwaterRainTimeDay.y;
    env.dayFactor = v_underwaterRainTimeDay.w;
    env.fogCol = FogColor.rgb;
    env = calculateSunParams(env, TimeOfDay.x, Day.x);

    nl_skycolor skycol = nlOverworldSkyColors(env);

    vec3 skyColor = nlRenderSky(skycol, env, -viewDir, v_underwaterRainTimeDay.z, true);
    #ifdef NL_SHOOTING_STAR
      skyColor += NL_SHOOTING_STAR*nlRenderShootingStar(viewDir, env.fogCol, v_underwaterRainTimeDay.z);
    #endif
    #ifdef NL_DEBUG_AURORATEX_TEST
      // TEST ONLY: show the ACTUAL aurora function's output, heavily
      // amplified, to check whether it's a hard zero (real logic bug)
      // or just too dim/sparse to notice normally (tuning issue).
      float dbgDither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
      vec3 auroraDbg = nlGetAurora3D(viewDir, v_underwaterRainTimeDay.z, dbgDither);
      gl_FragColor = vec4(clamp(auroraDbg*20.0, 0.0, 1.0), 1.0);
      return;
    #endif
    #ifdef NL_SKY_AURORA_3D
      float auroraDither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
      vec3 aurora3D = nlGetAurora3D(viewDir, v_underwaterRainTimeDay.z, auroraDither);
      float auroraNight = 1.0 - smoothstep(-0.1, 0.05, env.dayFactor);
      aurora3D *= max(auroraNight, NL_SKY_AURORA_DAY_VISIBILITY)*(1.0-env.rainFactor);
      skyColor += NL_SKY_AURORA*aurora3D;
    #elif defined(NL_SKY_AURORA)
      skyColor += NL_SKY_AURORA*nlRenderSkyAurora(viewDir, env, v_underwaterRainTimeDay.z);
    #endif
    #ifdef NL_MILKYWAY
      float mwMask = nlMilkyWayMask(viewDir, env, NL_MILKYWAY_ROTATION, NL_MILKYWAY_ELEVATION, NL_MILKYWAY_HEIGHT);
      if (mwMask > 0.0) {
        vec2 mwUV = nlMilkyWayUV(viewDir, NL_MILKYWAY_ROTATION, NL_MILKYWAY_ELEVATION, NL_MILKYWAY_HEIGHT);
        vec4 mwTex = texture2D(s_MilkyWayTex, mwUV);
        skyColor = mix(skyColor, NL_MILKYWAY*mwTex.rgb, mwTex.a*mwMask);
      }
    #endif

    skyColor = colorCorrection(skyColor);

    gl_FragColor = vec4(skyColor, 1.0);
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
