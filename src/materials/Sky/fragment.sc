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

  #ifdef NL_DEBUG_MILKYWAY_TEST
    SAMPLER2D_AUTOREG(s_MilkyWayTex);
  #endif
#endif

void main() {
  #ifndef INSTANCING
    vec3 viewDir = normalize(v_worldPos);

    #ifdef NL_DEBUG_MILKYWAY_TEST
      // TEST ONLY: prove the buffers/*.json custom-sampler method works on Sky
      // before wiring the real feature in. Slaps the texture across the whole
      // dome with a crude equirect UV - not the final look, just a pass/fail check.
      vec2 debugUV = nlEndSkyUV(-viewDir, 0.0);
      vec4 debugTex = texture2D(s_MilkyWayTex, debugUV);
      gl_FragColor = vec4(debugTex.rgb, 1.0);
      return;
    #endif

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
    #ifdef NL_SKY_AURORA
      skyColor += NL_SKY_AURORA*nlRenderSkyAurora(viewDir, env, v_underwaterRainTimeDay.z);
    #endif

    skyColor = colorCorrection(skyColor);

    gl_FragColor = vec4(skyColor, 1.0);
  #else
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
