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
    #ifdef NL_SKY_AURORA
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
