$input a_position, a_texcoord0
$output v_texcoord0, v_isSun

#include <bgfx_shader.sh>

#ifndef INSTANCING
  #include <newb/config.h>
  #include <newb/functions/utils.h>

  uniform vec4 TimeOfDay;
  uniform vec4 Day;
#endif

void main() {
  v_texcoord0 = a_texcoord0;
  #ifndef INSTANCING
    vec3 pos = a_position;
    pos.x = -pos.x;

    mat4 model = u_model[0];
    #if BGFX_SHADER_LANGUAGE_HLSL 
      vec2 dir = vec2(model[0][3], model[1][3]);
    #else
      vec2 dir = vec2(model[3][0], model[3][1]);
    #endif
    float st = 2.0*PI*TimeOfDay.x + 0.5*PI;
    bool isSun = dot(vec2(cos(st), sin(st)), dir) > 0.0;
    v_isSun = isSun ? 1.0 : 0.0;

    // horizon proximity, split into dawn side vs dusk side
    float t = 2.0*PI*TimeOfDay.x;
    float horizonFactor = smoothstep(0.6, 1.0, 1.0 - abs(cos(t)));
    float dawnFactor = horizonFactor * step(sin(t), 0.0);
    float duskFactor = horizonFactor * step(0.0, sin(t));

    float dist = 300.0;
    float angle = 0.0;
    float tilt = 0.0;
    float yaw = 0.0;
    if (isSun) {
      dist = -dist;
      pos.x = -pos.x;
      float sunSize = mix(NL_SUN_SIZE, NL_SUN_SIZE_DAWN, dawnFactor);
      sunSize = mix(sunSize, NL_SUN_SIZE_DUSK, duskFactor);
      pos.xz *= sunSize;
      float sunTilt = mix(NL_SUN_TILT, NL_SUN_TILT_DAWN, dawnFactor);
      sunTilt = mix(sunTilt, NL_SUN_TILT_DUSK, duskFactor);
      angle = degToRad(sunTilt);
      tilt = degToRad( NL_SUN_PATH_TILT);
      yaw = degToRad(NL_SUN_PATH_YAW);
    } else {
      float moonSize = mix(NL_MOON_SIZE, NL_MOON_SIZE_DAWN, dawnFactor);
      moonSize = mix(moonSize, NL_MOON_SIZE_DUSK, duskFactor);
      pos.xz *= moonSize;
      float moonTilt = mix(NL_MOON_TILT, NL_MOON_TILT_DAWN, dawnFactor);
      moonTilt = mix(moonTilt, NL_MOON_TILT_DUSK, duskFactor);
      angle = degToRad(moonTilt);
      tilt = degToRad( NL_MOON_PATH_TILT);
      yaw = degToRad(NL_MOON_PATH_YAW);
    }
    pos.xz = mul(rmat2(angle + 0.5*st), pos.xz);

    vec4 wpos = vec4(dist, 70.0*pos.xz, 1.0);

    // TODO: Combine these into single mat?
    wpos.xy = mul(rmat2(st), -wpos.xy);
    wpos.yz = mul(rmat2(tilt), wpos.yz);
    wpos.xz = mul(rmat2(yaw), wpos.xz);

    gl_Position = mul(u_viewProj, wpos);
  #else
    gl_Position = vec4(0.0, 0.0, 0.0, 0.0);
  #endif
}
