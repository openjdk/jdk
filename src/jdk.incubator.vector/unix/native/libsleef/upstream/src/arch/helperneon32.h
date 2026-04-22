//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if !defined(__ARM_NEON) && !defined(SLEEF_GENHEADER)
#error Please specify -mfpu=neon.
#endif

#ifdef __aarch64__
#warning This implementation is for AARCH32.
#endif

#define ENABLE_SP
//@#define ENABLE_SP
#define LOG2VECTLENSP 2
//@#define LOG2VECTLENSP 2
#define VECTLENSP (1 << LOG2VECTLENSP)
//@#define VECTLENSP (1 << LOG2VECTLENSP)

#if CONFIG == 4
#define ISANAME "AARCH32 NEON-VFPV4"
#define ENABLE_FMA_SP
//@#define ENABLE_FMA_SP
#else
#define ISANAME "AARCH32 NEON"
#endif
#define DFTPRIORITY 10

#define ENABLE_RECSQRT_SP
//@#define ENABLE_RECSQRT_SP

#include <arm_neon.h>
#include <stdint.h>

#include "misc.h"

typedef uint32x4_t vmask;
typedef uint32x4_t vopmask;

//typedef int32x4_t vint;

typedef float32x4_t vfloat;
typedef int32x4_t vint2;

//

static INLINE void vprefetch_v_p(const void *ptr) { }

static INLINE int vtestallones_i_vo32(vopmask g) {
  uint32x2_t x0 = vand_u32(vget_low_u32(g), vget_high_u32(g));
  uint32x2_t x1 = vpmin_u32(x0, x0);
  return vget_lane_u32(x1, 0);
}

static vfloat vloaduf(float *p) { return vld1q_f32(p); }
static void vstoreuf(float *p, vfloat v) { vst1q_f32(p, v); }

static vint2 vloadu_vi2_p(int32_t *p) { return vld1q_s32(p); }
static void vstoreu_v_p_vi2(int32_t *p, vint2 v) { vst1q_s32(p, v); }

//

static INLINE vmask vand_vm_vm_vm(vmask x, vmask y) { return vandq_u32(x, y); }
static INLINE vmask vandnot_vm_vm_vm(vmask x, vmask y) { return vbicq_u32(y, x); }
static INLINE vmask vor_vm_vm_vm(vmask x, vmask y) { return vorrq_u32(x, y); }
static INLINE vmask vxor_vm_vm_vm(vmask x, vmask y) { return veorq_u32(x, y); }

static INLINE vopmask vand_vo_vo_vo(vopmask x, vopmask y) { return vandq_u32(x, y); }
static INLINE vopmask vandnot_vo_vo_vo(vopmask x, vopmask y) { return vbicq_u32(y, x); }
static INLINE vopmask vor_vo_vo_vo(vopmask x, vopmask y) { return vorrq_u32(x, y); }
static INLINE vopmask vxor_vo_vo_vo(vopmask x, vopmask y) { return veorq_u32(x, y); }

static INLINE vmask vand_vm_vo64_vm(vopmask x, vmask y) { return vandq_u32(x, y); }
static INLINE vmask vandnot_vm_vo64_vm(vopmask x, vmask y) { return vbicq_u32(y, x); }
static INLINE vmask vor_vm_vo64_vm(vopmask x, vmask y) { return vorrq_u32(x, y); }
static INLINE vmask vxor_vm_vo64_vm(vopmask x, vmask y) { return veorq_u32(x, y); }

static INLINE vmask vand_vm_vo32_vm(vopmask x, vmask y) { return vandq_u32(x, y); }
static INLINE vmask vandnot_vm_vo32_vm(vopmask x, vmask y) { return vbicq_u32(y, x); }
static INLINE vmask vor_vm_vo32_vm(vopmask x, vmask y) { return vorrq_u32(x, y); }
static INLINE vmask vxor_vm_vo32_vm(vopmask x, vmask y) { return veorq_u32(x, y); }

static INLINE vopmask vcast_vo32_vo64(vopmask m) { return vuzpq_u32(m, m).val[0]; }
static INLINE vopmask vcast_vo64_vo32(vopmask m) { return vzipq_u32(m, m).val[0]; }

//

static INLINE vmask vcast_vm_i_i(int i0, int i1) { return (vmask)vdupq_n_u64((uint64_t)i0 | (((uint64_t)i1) << 32)); }
static INLINE vopmask veq64_vo_vm_vm(vmask x, vmask y) {
  uint32x4_t t = vceqq_u32(x, y);
  return vandq_u32(t, vrev64q_u32(t));
}

//

static INLINE vint2 vcast_vi2_vm(vmask vm) { return (vint2)vm; }
static INLINE vmask vcast_vm_vi2(vint2 vi) { return (vmask)vi; }
static INLINE vint2 vrint_vi2_vf(vfloat d) {
  return vcvtq_s32_f32(vaddq_f32(d, (float32x4_t)vorrq_u32(vandq_u32((uint32x4_t)d, (uint32x4_t)vdupq_n_f32(-0.0f)), (uint32x4_t)vdupq_n_f32(0.5f))));
}
static INLINE vint2 vtruncate_vi2_vf(vfloat vf) { return vcvtq_s32_f32(vf); }
static INLINE vfloat vcast_vf_vi2(vint2 vi) { return vcvtq_f32_s32(vi); }

static INLINE vfloat vtruncate_vf_vf(vfloat vd) { return vcast_vf_vi2(vtruncate_vi2_vf(vd)); }
static INLINE vfloat vrint_vf_vf(vfloat vd) { return vcast_vf_vi2(vrint_vi2_vf(vd)); }

static INLINE vfloat vcast_vf_f(float f) { return vdupq_n_f32(f); }
static INLINE vint2 vcast_vi2_i(int i) { return vdupq_n_s32(i); }
static INLINE vmask vreinterpret_vm_vf(vfloat vf) { return (vmask)vf; }
static INLINE vfloat vreinterpret_vf_vm(vmask vm) { return (vfloat)vm; }
static INLINE vfloat vreinterpret_vf_vi2(vint2 vm) { return (vfloat)vm; }
static INLINE vint2 vreinterpret_vi2_vf(vfloat vf) { return (vint2)vf; }

static INLINE vfloat vadd_vf_vf_vf(vfloat x, vfloat y) { return vaddq_f32(x, y); }
static INLINE vfloat vsub_vf_vf_vf(vfloat x, vfloat y) { return vsubq_f32(x, y); }
static INLINE vfloat vmul_vf_vf_vf(vfloat x, vfloat y) { return vmulq_f32(x, y); }

static INLINE vfloat vabs_vf_vf(vfloat f) { return vabsq_f32(f); }
static INLINE vfloat vneg_vf_vf(vfloat f) { return vnegq_f32(f); }
#if CONFIG == 4
static INLINE vfloat vmla_vf_vf_vf_vf  (vfloat x, vfloat y, vfloat z) { return vfmaq_f32(z, x, y); }
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vfmsq_f32(z, x, y); }
static INLINE vfloat vfma_vf_vf_vf_vf  (vfloat x, vfloat y, vfloat z) { return vfmaq_f32(z, x, y); }
static INLINE vfloat vfmanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vfmsq_f32(z, x, y); }
static INLINE vfloat vfmapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vneg_vf_vf(vfmanp_vf_vf_vf_vf(x, y, z)); }
static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vneg_vf_vf(vfmanp_vf_vf_vf_vf(x, y, z)); }

static INLINE vfloat vdiv_vf_vf_vf(vfloat x, vfloat y) {
  float32x4_t t = vrecpeq_f32(y), u;
  t = vmulq_f32(t, vrecpsq_f32(y, t));
  t = vfmaq_f32(t, vfmsq_f32(vdupq_n_f32(1.0f), y, t), t);
  u = vmulq_f32(x, t);
  return vfmaq_f32(u, vfmsq_f32(x, y, u), t);
}

static INLINE vfloat vsqrt_vf_vf(vfloat d) {
  float32x4_t x = vrsqrteq_f32(d);
  x = vmulq_f32(x, vrsqrtsq_f32(d, vmulq_f32(x, x)));
  x = vmulq_f32(x, vrsqrtsq_f32(d, vmulq_f32(x, x)));
  float32x4_t u = vmulq_f32(x, d);
  u = vfmaq_f32(u, vfmsq_f32(d, u, u), vmulq_f32(x, vdupq_n_f32(0.5)));
  return vreinterpretq_f32_u32(vbicq_u32(vreinterpretq_u32_f32(u), vceqq_f32(d, vdupq_n_f32(0.0f))));
}

static INLINE vfloat vrec_vf_vf(vfloat y) {
  float32x4_t t = vrecpeq_f32(y);
  t = vmulq_f32(t, vrecpsq_f32(y, t));
  t = vfmaq_f32(t, vfmsq_f32(vdupq_n_f32(1.0f), y, t), t);
  return vfmaq_f32(t, vfmsq_f32(vdupq_n_f32(1.0f), y, t), t);
}

static INLINE vfloat vrecsqrt_vf_vf(vfloat d) {
  float32x4_t x = vrsqrteq_f32(d);
  x = vmulq_f32(x, vrsqrtsq_f32(d, vmulq_f32(x, x)));
  return vfmaq_f32(x, vfmsq_f32(vdupq_n_f32(1), x, vmulq_f32(x, d)), vmulq_f32(x, vdupq_n_f32(0.5)));
}
#else // #if CONFIG == 4
static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vmlaq_f32(z, x, y); }
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vmlsq_f32(z, x, y); }
static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vneg_vf_vf(vmlsq_f32(z, x, y)); }

static INLINE vfloat vdiv_vf_vf_vf(vfloat n, vfloat d) {
  float32x4_t x = vrecpeq_f32(d);
  x = vmulq_f32(x, vrecpsq_f32(d, x));
  float32x4_t t = vmulq_f32(n, x);
  return vmlsq_f32(vaddq_f32(t, t), vmulq_f32(t, x), d);
}

static INLINE vfloat vsqrt_vf_vf(vfloat d) {
  float32x4_t x = vrsqrteq_f32(d);
  x = vmulq_f32(x, vrsqrtsq_f32(d, vmulq_f32(x, x)));
  float32x4_t u = vmulq_f32(x, d);
  u = vmlaq_f32(u, vmlsq_f32(d, u, u), vmulq_f32(x, vdupq_n_f32(0.5)));
  return vreinterpretq_f32_u32(vbicq_u32(vreinterpretq_u32_f32(u), vceqq_f32(d, vdupq_n_f32(0.0f))));
}

static INLINE vfloat vrec_vf_vf(vfloat d) {
  float32x4_t x = vrecpeq_f32(d);
  x = vmulq_f32(x, vrecpsq_f32(d, x));
  return vmlsq_f32(vaddq_f32(x, x), vmulq_f32(x, x), d);
}

static INLINE vfloat vrecsqrt_vf_vf(vfloat d) {
  float32x4_t x = vrsqrteq_f32(d);
  x = vmulq_f32(x, vrsqrtsq_f32(d, vmulq_f32(x, x)));
  return vmlaq_f32(x, vmlsq_f32(vdupq_n_f32(1), x, vmulq_f32(x, d)), vmulq_f32(x, vdupq_n_f32(0.5)));
}
#endif // #if CONFIG == 4
static INLINE vfloat vmax_vf_vf_vf(vfloat x, vfloat y) { return vmaxq_f32(x, y); }
static INLINE vfloat vmin_vf_vf_vf(vfloat x, vfloat y) { return vminq_f32(x, y); }

static INLINE vopmask veq_vo_vf_vf(vfloat x, vfloat y) { return vceqq_f32(x, y); }
static INLINE vopmask vneq_vo_vf_vf(vfloat x, vfloat y) { return vmvnq_u32(vceqq_f32(x, y)); }
static INLINE vopmask vlt_vo_vf_vf(vfloat x, vfloat y) { return vcltq_f32(x, y); }
static INLINE vopmask vle_vo_vf_vf(vfloat x, vfloat y) { return vcleq_f32(x, y); }
static INLINE vopmask vgt_vo_vf_vf(vfloat x, vfloat y) { return vcgtq_f32(x, y); }
static INLINE vopmask vge_vo_vf_vf(vfloat x, vfloat y) { return vcgeq_f32(x, y); }

static INLINE vint2 vadd_vi2_vi2_vi2(vint2 x, vint2 y) { return vaddq_s32(x, y); }
static INLINE vint2 vsub_vi2_vi2_vi2(vint2 x, vint2 y) { return vsubq_s32(x, y); }
static INLINE vint2 vneg_vi2_vi2(vint2 e) { return vnegq_s32(e); }

static INLINE vint2 vand_vi2_vi2_vi2(vint2 x, vint2 y) { return vandq_s32(x, y); }
static INLINE vint2 vandnot_vi2_vi2_vi2(vint2 x, vint2 y) { return vbicq_s32(y, x); }
static INLINE vint2 vor_vi2_vi2_vi2(vint2 x, vint2 y) { return vorrq_s32(x, y); }
static INLINE vint2 vxor_vi2_vi2_vi2(vint2 x, vint2 y) { return veorq_s32(x, y); }

static INLINE vint2 vand_vi2_vo_vi2(vopmask x, vint2 y) { return (vint2)vandq_u32(x, (vopmask)y); }
static INLINE vint2 vandnot_vi2_vo_vi2(vopmask x, vint2 y) { return (vint2)vbicq_u32((vopmask)y, x); }

#define vsll_vi2_vi2_i(x, c) vshlq_n_s32(x, c)
#define vsrl_vi2_vi2_i(x, c) vreinterpretq_s32_u32(vshrq_n_u32(vreinterpretq_u32_s32(x), c))
#define vsra_vi2_vi2_i(x, c) vshrq_n_s32(x, c)
//@#define vsll_vi2_vi2_i(x, c) vshlq_n_s32(x, c)
//@#define vsrl_vi2_vi2_i(x, c) vreinterpretq_s32_u32(vshrq_n_u32(vreinterpretq_u32_s32(x), c))
//@#define vsra_vi2_vi2_i(x, c) vshrq_n_s32(x, c)

static INLINE vopmask veq_vo_vi2_vi2(vint2 x, vint2 y) { return vceqq_s32(x, y); }
static INLINE vopmask vgt_vo_vi2_vi2(vint2 x, vint2 y) { return vcgtq_s32(x, y); }
static INLINE vint2 veq_vi2_vi2_vi2(vint2 x, vint2 y) { return (vint2)vceqq_s32(x, y); }
static INLINE vint2 vgt_vi2_vi2_vi2(vint2 x, vint2 y) { return (vint2)vcgtq_s32(x, y); }

static INLINE vint2 vsel_vi2_vo_vi2_vi2(vopmask m, vint2 x, vint2 y) { return (vint2)vbslq_u32(m, (vmask)x, (vmask)y); }

static INLINE vfloat vsel_vf_vo_vf_vf(vopmask mask, vfloat x, vfloat y) {
  return (vfloat)vbslq_u32(mask, (vmask)x, (vmask)y);
}

static INLINE CONST vfloat vsel_vf_vo_f_f(vopmask o, float v1, float v0) {
  return vsel_vf_vo_vf_vf(o, vcast_vf_f(v1), vcast_vf_f(v0));
}

static INLINE vfloat vsel_vf_vo_vo_f_f_f(vopmask o0, vopmask o1, float d0, float d1, float d2) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_f_f(o1, d1, d2));
}

static INLINE vfloat vsel_vf_vo_vo_vo_f_f_f_f(vopmask o0, vopmask o1, vopmask o2, float d0, float d1, float d2, float d3) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_vf_vf(o1, vcast_vf_f(d1), vsel_vf_vo_f_f(o2, d2, d3)));
}

static INLINE vopmask visinf_vo_vf(vfloat d) { return veq_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(SLEEF_INFINITYf)); }
static INLINE vopmask vispinf_vo_vf(vfloat d) { return veq_vo_vf_vf(d, vcast_vf_f(SLEEF_INFINITYf)); }
static INLINE vopmask visminf_vo_vf(vfloat d) { return veq_vo_vf_vf(d, vcast_vf_f(-SLEEF_INFINITYf)); }
static INLINE vopmask visnan_vo_vf(vfloat d) { return vneq_vo_vf_vf(d, d); }

// This function is needed when debugging on MSVC.
static INLINE float vcast_f_vf(vfloat v) {
  float p[4];
  vst1q_f32 (p, v);
  return p[0];
}

static INLINE int vavailability_i(int name) {
  if (name != 2) return 0;
  return vcast_f_vf(vadd_vf_vf_vf(vcast_vf_f(name), vcast_vf_f(name))) != 0.0;
}


static INLINE vfloat vload_vf_p(const float *ptr) { return vld1q_f32(__builtin_assume_aligned(ptr, 16)); }
static INLINE vfloat vloadu_vf_p(const float *ptr) { return vld1q_f32(ptr); }

static INLINE void vstore_v_p_vf(float *ptr, vfloat v) { vst1q_f32(__builtin_assume_aligned(ptr, 16), v); }
static INLINE void vstoreu_v_p_vf(float *ptr, vfloat v) { vst1q_f32(ptr, v); }

static INLINE vfloat vgather_vf_p_vi2(const float *ptr, vint2 vi2) {
  return ((vfloat) {
      ptr[vgetq_lane_s32(vi2, 0)],
      ptr[vgetq_lane_s32(vi2, 1)],
      ptr[vgetq_lane_s32(vi2, 2)],
      ptr[vgetq_lane_s32(vi2, 3)]
    });
}

#define PNMASKf ((vfloat) { +0.0f, -0.0f, +0.0f, -0.0f })
#define NPMASKf ((vfloat) { -0.0f, +0.0f, -0.0f, +0.0f })

static INLINE vfloat vposneg_vf_vf(vfloat d) { return (vfloat)vxor_vm_vm_vm((vmask)d, (vmask)PNMASKf); }
static INLINE vfloat vnegpos_vf_vf(vfloat d) { return (vfloat)vxor_vm_vm_vm((vmask)d, (vmask)NPMASKf); }

static INLINE vfloat vsubadd_vf_vf_vf(vfloat d0, vfloat d1) { return vadd_vf_vf_vf(d0, vnegpos_vf_vf(d1)); }
static INLINE vfloat vmlsubadd_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsubadd_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }

static INLINE vfloat vrev21_vf_vf(vfloat d0) { return vrev64q_f32(d0); }
static INLINE vfloat vreva2_vf_vf(vfloat d0) { return vcombine_f32(vget_high_f32(d0), vget_low_f32(d0)); }

static INLINE void vstream_v_p_vf(float *ptr, vfloat v) { vstore_v_p_vf(ptr, v); }

static INLINE void vscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) {
  vst1_f32((float *)(ptr+(offset + step * 0)*2), vget_low_f32(v));
  vst1_f32((float *)(ptr+(offset + step * 1)*2), vget_high_f32(v));
}

static INLINE void vsscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) {
  vst1_f32((float *)(ptr+(offset + step * 0)*2), vget_low_f32(v));
  vst1_f32((float *)(ptr+(offset + step * 1)*2), vget_high_f32(v));
}
