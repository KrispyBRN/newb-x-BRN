#ifndef SKY_H
#define SKY_H

#include "detection.h"
#include "noise.h"

struct nl_skycolor {
  vec3 zenith;
  vec3 horizon;
  vec3 horizonEdge;
};

// rainbow spectrum
vec3 spectrum(float x) {
  vec3 s = vec3(x-0.5, x, x+0.5);
  s = smoothstep(1.0,0.0,abs(s));
  return s*s;
}

vec3 getUnderwaterCol(vec3 FOG_COLOR) {
  return 2.0*NL_UNDERWATER_TINT*FOG_COLOR*FOG_COLOR;
}

vec3 getEndZenithCol() {
  return NL_END_ZENITH_COL;
}

vec3 getEndHorizonCol() {
  return NL_END_HORIZON_COL;
}

nl_skycolor nlEndSkyColors(nl_environment env) {
  nl_skycolor s;
  s.zenith = getEndZenithCol();
  s.horizon = getEndHorizonCol();
  s.horizonEdge = s.horizon;
  return s;
}

nl_skycolor nlOverworldSkyColors(nl_environment env) {
  nl_skycolor s;
  float f = 1.0 + 2.0*(1.0-max(-env.dayFactor, 0.0));
  float nightFactor = step(env.dayFactor, 0.0);
  s.zenith = mix(NL_DAY_ZENITH_COL, NL_NIGHT_ZENITH_COL*f, nightFactor);
  s.horizon = mix(NL_DAY_HORIZON_COL, NL_NIGHT_HORIZON_COL*f, nightFactor);
  s.horizonEdge = mix(NL_DAY_EDGE_COL, NL_NIGHT_EDGE_COL*f, nightFactor);

  float dawnFactor = 1.0-env.dayFactor*env.dayFactor;
  dawnFactor *= dawnFactor*dawnFactor;
  dawnFactor *= mix(1.0, dawnFactor*dawnFactor, nightFactor);
  s.zenith = mix(s.zenith, NL_DAWN_ZENITH_COL, dawnFactor);
  s.horizon = mix(s.horizon, NL_DAWN_HORIZON_COL, dawnFactor);
  s.horizonEdge = mix(s.horizonEdge, NL_DAWN_EDGE_COL, dawnFactor);

  float zh = dot(s.zenith, vec3_splat(0.33));
  float hh = dot(s.horizon, vec3_splat(0.33));
  float rainMix = env.rainFactor*NL_SKY_RAIN_MIX_FACTOR;
  s.zenith = mix(s.zenith, NL_RAIN_ZENITH_COL*zh, rainMix);
  s.horizon = mix(s.horizon, NL_RAIN_HORIZON_COL*hh, rainMix);
  s.horizonEdge = mix(s.horizonEdge, s.horizon, env.rainFactor);

  if (env.underwater) {
    vec3 underwaterFog = env.fogCol*env.fogCol*NL_UNDERWATER_TINT;
    s.zenith = mix(2.0*underwaterFog, underwaterFog*zh, 0.8);
    s.horizon = mix(2.0*underwaterFog, underwaterFog*hh, 0.8);
    s.horizonEdge = s.horizon;
  }

  return s;
}

nl_skycolor nlSkyColors(nl_environment env) {
  if (env.end) {
    return nlEndSkyColors(env);
  }
  return nlOverworldSkyColors(env);
}


vec3 renderOverworldSky(nl_skycolor skyCol, nl_environment env, vec3 viewDir, bool isSkyPlane) {
  float avy = abs(viewDir.y);
  float mask = 0.5 + (0.5*viewDir.y/(0.4 + avy));

  vec2 g = clamp(0.5 - 0.5*vec2(dot(env.sunDir, viewDir), dot(env.moonDir, viewDir)), 0.0, 1.0);
  vec2 g1 = 1.0-mix(sqrt(g), g, env.rainFactor);
  vec2 g2 = g1*g1;
  vec2 g4 = g2*g2;
  vec2 g8 = g4*g4;
  float mg8 = (g8.x+g8.y)*mask*(1.0-0.9*env.rainFactor);

  float vh = 1.0 - viewDir.y*viewDir.y;
  float vh2 = vh*vh;
  vh2 = mix(vh2, mix(1.0, vh2*vh2, NL_SKY_VOID_FACTOR), step(viewDir.y, 0.0));
  vh2 = mix(vh2, 1.0, mg8);
  float vh4 = vh2*vh2;

  float gradient1 = vh4*vh4;
  float gradient2 = 0.8*gradient1 + 0.2*vh2;
  gradient1 *= gradient1;
  gradient1 = mix(gradient1*gradient1, 1.0, mg8);
  gradient2 = mix(gradient2, 1.0, mg8);

  float dawnFactor = 1.0-env.dayFactor*env.dayFactor;
  float df = mix(1.0, g2.x, dawnFactor*dawnFactor);
  vec3 sky = mix(skyCol.horizon, skyCol.horizonEdge, gradient1*df*df);
  sky = mix(skyCol.zenith, sky, gradient2*df);

  sky *= 0.5+0.5*gradient2;
  sky *= (1.0 + (2.0*mg8 + 7.0*mg8*mg8)*mask)*mix(1.0, mask, NL_SKY_VOID_DARKNESS);

  if (!isSkyPlane) {
    float source = max(0.0, (mg8-0.22)/0.78);
    source *= source;
    source *= source;
    sky *= 1.0 + 15.0*source*(1.0-env.rainFactor);
  }

  #ifdef NL_RAINBOW
    float rainbowFade = 0.5 + 0.5*viewDir.y;
    rainbowFade *= rainbowFade;
    rainbowFade *= mix(NL_RAINBOW_CLEAR, NL_RAINBOW_RAIN, env.rainFactor);
    rainbowFade *= 0.5+0.5*env.dayFactor;
    sky += spectrum(24.2*(0.85-g.x))*rainbowFade*skyCol.horizon;
  #endif

  return sky;
}

// maps a view direction onto a full 360 equirectangular image (u wraps around, v = pole to pole)
vec2 nlEndSkyUV(vec3 viewDir, float rotation) {
  float u = atan2(viewDir.x, viewDir.z)*0.15915494 + 0.5 + rotation; // 1/(2*pi)
  float v = acos(clamp(viewDir.y,-1.0,1.0))*0.31830989; // 1/pi
  return vec2(u, v);
}

vec3 renderEndSky(vec3 horizonCol, vec3 zenithCol, vec3 viewDir, float t) {
  return vec3(0.0); // actual End sky is the cubemap image, sampled directly in EndSky/fragment.sc
}

vec3 nlRenderSky(nl_skycolor skycol, nl_environment env, vec3 viewDir, float t, bool isSkyPlane) {
  vec3 sky;
  viewDir.y = -viewDir.y;

  if (env.end) {
    sky = renderEndSky(skycol.horizon, skycol.zenith, viewDir, t);
  } else {
    sky = renderOverworldSky(skycol, env, viewDir, isSkyPlane);
    #ifdef NL_UNDERWATER_STREAKS
      // if (env.underwater) {
      //   float a = atan2(viewDir.x, viewDir.z);
      //   float grad = 0.5 + 0.5*viewDir.y;
      //   grad *= grad;
      //   float spread = (0.5 + 0.5*sin(3.0*a + 0.2*t + 2.0*sin(5.0*a - 0.4*t)));
      //   spread *= (0.5 + 0.5*sin(3.0*a - sin(0.5*t)))*grad;
      //   spread += (1.0-spread)*grad;
      //   float streaks = spread*spread;
      //   streaks *= streaks;
      //   streaks = (spread + 3.0*grad*grad + 4.0*streaks*streaks);
      //   sky += 2.0*streaks*skycol.horizon;
      // }
    #endif
  }

  return sky;
}

// shooting star
vec3 nlRenderShootingStar(vec3 viewDir, vec3 FOG_COLOR, float t) {
  // transition vars
  float h = t / (NL_SHOOTING_STAR_DELAY + NL_SHOOTING_STAR_PERIOD);
  float h0 = floor(h);
  t = (NL_SHOOTING_STAR_DELAY + NL_SHOOTING_STAR_PERIOD) * (h-h0);
  t = min(t/NL_SHOOTING_STAR_PERIOD, 1.0);
  float t0 = t*t;
  float t1 = 1.0-t0;
  t1 *= t1; t1 *= t1; t1 *= t1;

  // randomize size, rotation, add motion, add skew
  float r = fract(sin(h0) * 43758.545313);
  float a = 6.2831*r;
  float cosa = cos(a);
  float sina = sin(a);
  vec2 uv = viewDir.xz * (6.0 + 4.0*r);
  uv = vec2(cosa*uv.x + sina*uv.y, -sina*uv.x + cosa*uv.y);
  uv.x += t1 - t;
  uv.x -= 2.0*r + 3.5;
  uv.y += viewDir.y * 3.0;

  // draw star
  float g = 1.0-min(abs((uv.x-0.95))*20.0, 1.0); // source glow
  float s = 1.0-min(abs(8.0*uv.y), 1.0); // line
  s *= s*s*smoothstep(-1.0+1.96*t1, 0.98-t, uv.x); // decay tail
  s *= s*s*smoothstep(1.0, 0.98-t0, uv.x); // decay source
  s *= 1.0-t1; // fade in
  s *= 1.0-t0; // fade out
  s *= 0.7 + 16.0*g*g;
  s *= max(1.0-FOG_COLOR.r-FOG_COLOR.g-FOG_COLOR.b, 0.0); // fade out during day
  return s*vec3(0.8, 0.9, 1.0);
}

// Aurora (sky layer) - pure sin/cos curtain effect, no noise3D/noise2D at all
vec3 nlRenderSkyAurora(vec3 vdir, nl_environment env, float t) {
  if (env.underwater) {
    return vec3_splat(0.0);
  }

  t *= NL_SKY_AURORA_SPEED;

  // domain-warped sine ribbons: feeding one sine into another gives wavy,
  // curtain-like bands instead of straight sine stripes
  float warp = 0.3*sin(vdir.x*3.0 + t*0.6);
  float ribbon = sin(vdir.x*6.0 + warp*4.0 + t*0.9) * sin(vdir.z*4.0 - t*0.5 + warp*3.0);
  ribbon = 0.5 + 0.5*ribbon;

  // finer ripple detail layered on top, still just sine
  float ripple = 0.5 + 0.5*sin(vdir.x*14.0 + t*1.4)*sin(vdir.z*10.0 - t*1.1);

  // vertical band mask: aurora sits in the upper sky, fades out toward
  // the zenith and fades out before the horizon
  float band = smoothstep(0.05, 0.35, vdir.y) * (1.0 - smoothstep(0.55, 0.9, vdir.y));

  float intensity = band*ribbon*(0.6 + 0.4*ripple);
  intensity *= intensity; // sharpen into ribbon-like streaks

  vec3 tint = mix(NL_SKY_AURORA_COLOR, NL_SKY_AURORA_COLOR2, ribbon);
  vec3 aurora = intensity*tint;

  // dayFactor = sunDir.y: negative once the sun is below the horizon (night),
  // positive once it's up (day). fade the aurora in/out smoothly around the
  // horizon crossing so it's only really visible at night, with a tiny
  // configurable floor so it doesn't hard-cut at the exact transition.
  float night = 1.0 - smoothstep(-0.1, 0.05, env.dayFactor);
  aurora *= max(night, NL_SKY_AURORA_DAY_VISIBILITY);

  return aurora*(1.0-env.rainFactor);
}

// Note: the "3D" volumetric aurora now lives directly in Sky/fragment.sc,
// not here. It needs its own global sampler declaration (s_AuroraNoiseTex),
// and sky.h is a shared header included by other materials too - a global
// sampler declared here would break their builds since they have no
// matching buffers/*.json for it.

// Milky way band helpers - visible only when looking roughly south.
// The actual texture2D() sample happens in Sky/fragment.sc (where the
// sampler is declared); these just compute where to sample and how
// strongly it should show.
// rotationDeg: degrees to nudge which way "south" points, since compass
//   alignment can only be confirmed empirically in-game - tune this until
//   the band lines up with the real south direction.
vec2 nlMilkyWayUV(vec3 vdir, float rotationDeg, float elevationDeg, float heightDeg) {
  float angle = atan2(vdir.x, vdir.z) - radians(rotationDeg);
  angle = mod(angle + 3.14159265, 6.2831853) - 3.14159265; // wrap to [-pi, pi]
  float halfWidth = radians(NL_MILKYWAY_WIDTH*0.5);
  float u = 0.5 + 0.5*(angle/halfWidth);

  float elevAngle = asin(clamp(vdir.y, -1.0, 1.0)) - radians(elevationDeg);
  float halfHeight = radians(heightDeg*0.5);
  float v = 0.5 - 0.5*(elevAngle/halfHeight);

  return vec2(u, v);
}

float nlMilkyWayMask(vec3 vdir, nl_environment env, float rotationDeg, float elevationDeg, float heightDeg) {
  if (env.underwater) {
    return 0.0;
  }
  float angle = atan2(vdir.x, vdir.z) - radians(rotationDeg);
  angle = mod(angle + 3.14159265, 6.2831853) - 3.14159265;
  float halfWidth = radians(NL_MILKYWAY_WIDTH*0.5);
  float edgeMaskH = 1.0 - smoothstep(halfWidth*0.7, halfWidth, abs(angle));

  float elevAngle = asin(clamp(vdir.y, -1.0, 1.0)) - radians(elevationDeg);
  float halfHeight = radians(heightDeg*0.5);
  float edgeMaskV = 1.0 - smoothstep(halfHeight*0.7, halfHeight, abs(elevAngle));

  float night = 1.0 - smoothstep(-0.1, 0.05, env.dayFactor);
  return edgeMaskH*edgeMaskV*night*(1.0-env.rainFactor);
}



#endif
