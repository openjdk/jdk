//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdint.h>
#include "misc.h"

#ifndef CONFIG
#error CONFIG macro not defined
#endif

#define ENABLE_DP
#define ENABLE_SP

#define LOG2VECTLENDP CONFIG
#define VECTLENDP (1 << LOG2VECTLENDP)
#define LOG2VECTLENSP (LOG2VECTLENDP+1)
#define VECTLENSP (1 << LOG2VECTLENSP)

#define DFTPRIORITY LOG2VECTLENDP

#if defined(__clang__)
#define ISANAME "Clang Vector Extension"

typedef uint32_t vmask __attribute__((ext_vector_type(VECTLENDP*2)));
typedef uint32_t vopmask __attribute__((ext_vector_type(VECTLENDP*2)));

typedef double vdouble __attribute__((ext_vector_type(VECTLENDP)));
typedef int32_t vint __attribute__((ext_vector_type(VECTLENDP)));

typedef float vfloat __attribute__((ext_vector_type(VECTLENDP*2)));
typedef int32_t vint2 __attribute__((ext_vector_type(VECTLENDP*2)));

#ifdef ENABLE_LONGDOUBLE
typedef uint8_t vmaskl __attribute__((ext_vector_type(sizeof(long double)*VECTLENDP)));
typedef long double vlongdouble __attribute__((ext_vector_type(VECTLENDP)));
#endif

#if defined(Sleef_quad2_DEFINED) && defined(ENABLEFLOAT128)
typedef uint8_t vmaskq __attribute__((ext_vector_type(sizeof(Sleef_quad)*VECTLENDP)));
#ifdef ENABLE_LONGDOUBLE
typedef Sleef_quad vquad __attribute__((ext_vector_type(VECTLENDP)));
#endif
#endif
#elif defined(__GNUC__)
#define ISANAME "GCC Vector Extension"

typedef uint32_t vmask __attribute__((vector_size(sizeof(uint32_t)*VECTLENDP*2)));
typedef uint32_t vopmask __attribute__((vector_size(sizeof(uint32_t)*VECTLENDP*2)));

typedef double vdouble __attribute__((vector_size(sizeof(double)*VECTLENDP)));
typedef int32_t vint __attribute__((vector_size(sizeof(int32_t)*VECTLENDP)));

typedef float vfloat __attribute__((vector_size(sizeof(float)*VECTLENDP*2)));
typedef int32_t vint2 __attribute__((vector_size(sizeof(int32_t)*VECTLENDP*2)));

#ifdef ENABLE_LONGDOUBLE
typedef uint8_t vmaskl __attribute__((vector_size(sizeof(long double)*VECTLENDP)));
typedef long double vlongdouble __attribute__((vector_size(sizeof(long double)*VECTLENDP)));
#endif

#if defined(Sleef_quad2_DEFINED) && defined(ENABLEFLOAT128)
typedef uint8_t vmaskq __attribute__((vector_size(sizeof(Sleef_quad)*VECTLENDP)));
typedef Sleef_quad vquad __attribute__((vector_size(sizeof(Sleef_quad)*VECTLENDP)));
#endif
#endif

//

#if VECTLENDP == 2
static INLINE vopmask vcast_vo32_vo64(vopmask m) { return (vopmask){ m[1], m[3], 0, 0 }; }
static INLINE vopmask vcast_vo64_vo32(vopmask m) { return (vopmask){ m[0], m[0], m[1], m[1] }; }

static INLINE vint vcast_vi_i(int i) { return (vint) { i, i }; }
static INLINE vint2 vcast_vi2_i(int i) { return (vint2) { i, i, i, i }; }
static INLINE vfloat vcast_vf_f(float f) { return (vfloat) { f, f, f, f }; }
static INLINE vdouble vcast_vd_d(double d) { return (vdouble) { d, d }; }
#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vcast_vl_l(long double d) { return (vlongdouble) { d, d }; }
#endif
#if defined(Sleef_quad2_DEFINED) && defined(ENABLEFLOAT128)
static INLINE vquad vcast_vq_q(Sleef_quad d) { return (vquad) { d, d }; }
#endif

static INLINE vmask vcast_vm_i_i(int h, int l) { return (vmask){ l, h, l, h }; }
static INLINE vint2 vcastu_vi2_vi(vint vi) { return (vint2){ 0, vi[0], 0, vi[1] }; }
static INLINE vint vcastu_vi_vi2(vint2 vi2) { return (vint){ vi2[1], vi2[3] }; }

static INLINE vint vreinterpretFirstHalf_vi_vi2(vint2 vi2) { return (vint){ vi2[0], vi2[1] }; }
static INLINE vint2 vreinterpretFirstHalf_vi2_vi(vint vi) { return (vint2){ vi[0], vi[1], 0, 0 }; }

static INLINE vdouble vrev21_vd_vd(vdouble vd) { return (vdouble) { vd[1], vd[0] }; }
static INLINE vdouble vreva2_vd_vd(vdouble vd) { return vd; }
static INLINE vfloat vrev21_vf_vf(vfloat vd) { return (vfloat) { vd[1], vd[0], vd[3], vd[2] }; }
static INLINE vfloat vreva2_vf_vf(vfloat vd) { return (vfloat) { vd[2], vd[3], vd[0], vd[1] }; }
#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vrev21_vl_vl(vlongdouble vd) { return (vlongdouble) { vd[1], vd[0] }; }
static INLINE vlongdouble vreva2_vl_vl(vlongdouble vd) { return vd; }
static INLINE vlongdouble vposneg_vl_vl(vlongdouble vd) { return (vlongdouble) { +vd[0], -vd[1] }; }
static INLINE vlongdouble vnegpos_vl_vl(vlongdouble vd) { return (vlongdouble) { -vd[0], +vd[1] }; }
#endif

#if defined(Sleef_quad2_DEFINED) && defined(ENABLEFLOAT128)
static INLINE vquad vrev21_vq_vq(vquad vd) { return (vquad) { vd[1], vd[0] }; }
static INLINE vquad vreva2_vq_vq(vquad vd) { return vd; }
static INLINE vquad vposneg_vq_vq(vquad vd) { return (vquad) { +vd[0], -vd[1] }; }
static INLINE vquad vnegpos_vq_vq(vquad vd) { return (vquad) { -vd[0], +vd[1] }; }
#endif

#define PNMASK ((vdouble) { +0.0, -0.0 })
#define NPMASK ((vdouble) { -0.0, +0.0 })
static INLINE vdouble vposneg_vd_vd(vdouble d) { return (vdouble)((vmask)d ^ (vmask)PNMASK); }
static INLINE vdouble vnegpos_vd_vd(vdouble d) { return (vdouble)((vmask)d ^ (vmask)NPMASK); }

#define PNMASKf ((vfloat) { +0.0f, -0.0f, +0.0f, -0.0f })
#define NPMASKf ((vfloat) { -0.0f, +0.0f, -0.0f, +0.0f })
static INLINE vfloat vposneg_vf_vf(vfloat d) { return (vfloat)((vmask)d ^ (vmask)PNMASKf); }
static INLINE vfloat vnegpos_vf_vf(vfloat d) { return (vfloat)((vmask)d ^ (vmask)NPMASKf); }
#elif VECTLENDP == 4
static INLINE vopmask vcast_vo32_vo64(vopmask m) { return (vopmask){ m[1], m[3], m[5], m[7], 0, 0, 0, 0 }; }
static INLINE vopmask vcast_vo64_vo32(vopmask m) { return (vopmask){ m[0], m[0], m[1], m[1], m[2], m[2], m[3], m[3] }; }

static INLINE vint vcast_vi_i(int i) { return (vint) { i, i, i, i }; }
static INLINE vint2 vcast_vi2_i(int i) { return (vint2) { i, i, i, i, i, i, i, i }; }
static INLINE vfloat vcast_vf_f(float f) { return (vfloat) { f, f, f, f, f, f, f, f }; }
static INLINE vdouble vcast_vd_d(double d) { return (vdouble) { d, d, d, d }; }
#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vcast_vl_l(long double d) { return (vlongdouble) { d, d, d, d }; }
#endif

static INLINE vmask vcast_vm_i_i(int h, int l) { return (vmask){ l, h, l, h, l, h, l, h }; }
static INLINE vint2 vcastu_vi2_vi(vint vi) { return (vint2){ 0, vi[0], 0, vi[1], 0, vi[2], 0, vi[3] }; }
static INLINE vint vcastu_vi_vi2(vint2 vi2) { return (vint){ vi2[1], vi2[3], vi2[5], vi2[7] }; }

static INLINE vint vreinterpretFirstHalf_vi_vi2(vint2 vi2) { return (vint){ vi2[0], vi2[1], vi2[2], vi2[3] }; }
static INLINE vint2 vreinterpretFirstHalf_vi2_vi(vint vi) { return (vint2){ vi[0], vi[1], vi[2], vi[3], 0, 0, 0, 0 }; }

#define PNMASK ((vdouble) { +0.0, -0.0, +0.0, -0.0 })
#define NPMASK ((vdouble) { -0.0, +0.0, -0.0, +0.0 })
static INLINE vdouble vposneg_vd_vd(vdouble d) { return (vdouble)((vmask)d ^ (vmask)PNMASK); }
static INLINE vdouble vnegpos_vd_vd(vdouble d) { return (vdouble)((vmask)d ^ (vmask)NPMASK); }

#define PNMASKf ((vfloat) { +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f })
#define NPMASKf ((vfloat) { -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f })
static INLINE vfloat vposneg_vf_vf(vfloat d) { return (vfloat)((vmask)d ^ (vmask)PNMASKf); }
static INLINE vfloat vnegpos_vf_vf(vfloat d) { return (vfloat)((vmask)d ^ (vmask)NPMASKf); }

static INLINE vdouble vrev21_vd_vd(vdouble vd) { return (vdouble) { vd[1], vd[0], vd[3], vd[2] }; }
static INLINE vdouble vreva2_vd_vd(vdouble vd) { return (vdouble) { vd[2], vd[3], vd[0], vd[1] }; }
static INLINE vfloat vrev21_vf_vf(vfloat vd) { return (vfloat) { vd[1], vd[0], vd[3], vd[2], vd[5], vd[4], vd[7], vd[6] }; }
static INLINE vfloat vreva2_vf_vf(vfloat vd) { return (vfloat) { vd[6], vd[7], vd[4], vd[5], vd[2], vd[3], vd[0], vd[1] }; }
#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vrev21_vl_vl(vlongdouble vd) { return (vlongdouble) { vd[1], vd[0], vd[3], vd[2] }; }
static INLINE vlongdouble vreva2_vl_vl(vlongdouble vd) { return (vlongdouble) { vd[2], vd[3], vd[0], vd[1] }; }
static INLINE vlongdouble vposneg_vl_vl(vlongdouble vd) { return (vlongdouble) { +vd[0], -vd[1], +vd[2], -vd[3] }; }
static INLINE vlongdouble vnegpos_vl_vl(vlongdouble vd) { return (vlongdouble) { -vd[0], +vd[1], -vd[2], +vd[3] }; }
#endif
#elif VECTLENDP == 8
static INLINE vopmask vcast_vo32_vo64(vopmask m) { return (vopmask){ m[1], m[3], m[5], m[7], m[9], m[11], m[13], m[15], 0, 0, 0, 0, 0, 0, 0, 0 }; }
static INLINE vopmask vcast_vo64_vo32(vopmask m) { return (vopmask){ m[0], m[0], m[1], m[1], m[2], m[2], m[3], m[3], m[4], m[4], m[5], m[5], m[6], m[6], m[7], m[7] }; }

static INLINE vint vcast_vi_i(int i) { return (vint) { i, i, i, i, i, i, i, i }; }
static INLINE vint2 vcast_vi2_i(int i) { return (vint2) { i, i, i, i, i, i, i, i, i, i, i, i, i, i, i, i }; }
static INLINE vfloat vcast_vf_f(float f) { return (vfloat) { f, f, f, f, f, f, f, f, f, f, f, f, f, f, f, f }; }
static INLINE vdouble vcast_vd_d(double d) { return (vdouble) { d, d, d, d, d, d, d, d }; }
#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vcast_vl_l(long double d) { return (vlongdouble) { d, d, d, d, d, d, d, d }; }
#endif

static INLINE vmask vcast_vm_i_i(int h, int l) { return (vmask){ l, h, l, h, l, h, l, h, l, h, l, h, l, h, l, h }; }
static INLINE vint2 vcastu_vi2_vi(vint vi) { return (vint2){ 0, vi[0], 0, vi[1], 0, vi[2], 0, vi[3], 0, vi[4], 0, vi[5], 0, vi[6], 0, vi[7] }; }
static INLINE vint vcastu_vi_vi2(vint2 vi2) { return (vint){ vi2[1], vi2[3], vi2[5], vi2[7], vi2[9], vi2[11], vi2[13], vi2[15] }; }

static INLINE vint vreinterpretFirstHalf_vi_vi2(vint2 vi2) { return (vint){ vi2[0], vi2[1], vi2[2], vi2[3], vi2[4], vi2[5], vi2[6], vi2[7] }; }
static INLINE vint2 vreinterpretFirstHalf_vi2_vi(vint vi) { return (vint2){ vi[0], vi[1], vi[2], vi[3], vi[4], vi[5], vi[6], vi[7], 0, 0, 0, 0, 0, 0, 0, 0 }; }

#define PNMASK ((vdouble) { +0.0, -0.0, +0.0, -0.0, +0.0, -0.0, +0.0, -0.0 })
#define NPMASK ((vdouble) { -0.0, +0.0, -0.0, +0.0, -0.0, +0.0, -0.0, +0.0 })
static INLINE vdouble vposneg_vd_vd(vdouble d) { return (vdouble)((vmask)d ^ (vmask)PNMASK); }
static INLINE vdouble vnegpos_vd_vd(vdouble d) { return (vdouble)((vmask)d ^ (vmask)NPMASK); }

#define PNMASKf ((vfloat) { +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f })
#define NPMASKf ((vfloat) { -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f, -0.0f, +0.0f })
static INLINE vfloat vposneg_vf_vf(vfloat d) { return (vfloat)((vmask)d ^ (vmask)PNMASKf); }
static INLINE vfloat vnegpos_vf_vf(vfloat d) { return (vfloat)((vmask)d ^ (vmask)NPMASKf); }

static INLINE vdouble vrev21_vd_vd(vdouble vd) { return (vdouble) { vd[1], vd[0], vd[3], vd[2], vd[5], vd[4], vd[7], vd[6] }; }
static INLINE vdouble vreva2_vd_vd(vdouble vd) { return (vdouble) { vd[6], vd[7], vd[4], vd[5], vd[2], vd[3], vd[0], vd[1] }; }
static INLINE vfloat vrev21_vf_vf(vfloat vd) {
  return (vfloat) {
    vd[1], vd[0], vd[3], vd[2], vd[5], vd[4], vd[7], vd[6],
      vd[9], vd[8], vd[11], vd[10], vd[13], vd[12], vd[15], vd[14] };
}
static INLINE vfloat vreva2_vf_vf(vfloat vd) {
  return (vfloat) {
    vd[14], vd[15], vd[12], vd[13], vd[10], vd[11], vd[8], vd[9],
      vd[6], vd[7], vd[4], vd[5], vd[2], vd[3], vd[0], vd[1]};
}
#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vrev21_vl_vl(vlongdouble vd) { return (vlongdouble) { vd[1], vd[0], vd[3], vd[2], vd[5], vd[4], vd[7], vd[6] }; }
static INLINE vlongdouble vreva2_vl_vl(vlongdouble vd) { return (vlongdouble) { vd[6], vd[7], vd[4], vd[5], vd[2], vd[3], vd[0], vd[1] }; }
static INLINE vlongdouble vposneg_vl_vl(vlongdouble vd) { return (vlongdouble) { +vd[0], -vd[1], +vd[2], -vd[3], +vd[4], -vd[5], +vd[6], -vd[7] }; }
static INLINE vlongdouble vnegpos_vl_vl(vlongdouble vd) { return (vlongdouble) { -vd[0], +vd[1], -vd[2], +vd[3], -vd[4], +vd[5], -vd[6], +vd[7] }; }
#endif
#else
static INLINE vint vcast_vi_i(int k) {
  vint ret;
  for(int i=0;i<VECTLENDP;i++) ret[i] = k;
  return ret;
}

static INLINE vint2 vcast_vi2_i(int k) {
  vint2 ret;
  for(int i=0;i<VECTLENSP;i++) ret[i] = k;
  return ret;
}

static INLINE vdouble vcast_vd_d(double d) {
  vdouble ret;
  for(int i=0;i<VECTLENDP;i++) ret[i] = d;
  return ret;
}

static INLINE vfloat vcast_vf_f(float f) {
  vfloat ret;
  for(int i=0;i<VECTLENSP;i++) ret[i] = f;
  return ret;
}

#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vcast_vl_l(long double d) {
  vlongdouble ret;
  for(int i=0;i<VECTLENDP;i++) ret[i] = d;
  return ret;
}
#endif

static INLINE vopmask vcast_vo32_vo64(vopmask m) {
  vopmask ret;
  for(int i=0;i<VECTLENDP;i++) ret[i] = m[i*2+1];
  for(int i=VECTLENDP;i<VECTLENDP*2;i++) ret[i] = 0;
  return ret;
}

static INLINE vopmask vcast_vo64_vo32(vopmask m) {
  vopmask ret;
  for(int i=0;i<VECTLENDP;i++) ret[i*2] = ret[i*2+1] = m[i];
  return ret;
}

static INLINE vmask vcast_vm_i_i(int h, int l) {
  vmask ret;
  for(int i=0;i<VECTLENDP;i++) {
    ret[i*2+0] = l;
    ret[i*2+1] = h;
  }
  return ret;
}

static INLINE vint2 vcastu_vi2_vi(vint vi) {
  vint2 ret;
  for(int i=0;i<VECTLENDP;i++) {
    ret[i*2+0] = 0;
    ret[i*2+1] = vi[i];
  }
  return ret;
}

static INLINE vint vcastu_vi_vi2(vint2 vi2) {
  vint ret;
  for(int i=0;i<VECTLENDP;i++) ret[i] = vi2[i*2+1];
  return ret;
}

static INLINE vint vreinterpretFirstHalf_vi_vi2(vint2 vi2) {
  vint ret;
  for(int i=0;i<VECTLENDP;i++) ret[i] = vi2[i];
  return ret;
}

static INLINE vint2 vreinterpretFirstHalf_vi2_vi(vint vi) {
  vint2 ret;
  for(int i=0;i<VECTLENDP;i++) ret[i] = vi[i];
  for(int i=VECTLENDP;i<VECTLENDP*2;i++) ret[i] = 0;
  return ret;
}

static INLINE vdouble vrev21_vd_vd(vdouble d0) {
  vdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r[i*2+0] = d0[i*2+1];
    r[i*2+1] = d0[i*2+0];
  }
  return r;
}

static INLINE vdouble vreva2_vd_vd(vdouble d0) {
  vdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r[i*2+0] = d0[(VECTLENDP/2-1-i)*2+0];
    r[i*2+1] = d0[(VECTLENDP/2-1-i)*2+1];
  }
  return r;
}

static INLINE vfloat vrev21_vf_vf(vfloat d0) {
  vfloat r;
  for(int i=0;i<VECTLENSP/2;i++) {
    r[i*2+0] = d0[i*2+1];
    r[i*2+1] = d0[i*2+0];
  }
  return r;
}

static INLINE vfloat vreva2_vf_vf(vfloat d0) {
  vfloat r;
  for(int i=0;i<VECTLENSP/2;i++) {
    r[i*2+0] = d0[(VECTLENSP/2-1-i)*2+0];
    r[i*2+1] = d0[(VECTLENSP/2-1-i)*2+1];
  }
  return r;
}

#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vrev21_vl_vl(vlongdouble d0) {
  vlongdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r[i*2+0] = d0[i*2+1];
    r[i*2+1] = d0[i*2+0];
  }
  return r;
}

static INLINE vlongdouble vreva2_vl_vl(vlongdouble d0) {
  vlongdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r[i*2+0] = d0[(VECTLENDP/2-1-i)*2+0];
    r[i*2+1] = d0[(VECTLENDP/2-1-i)*2+1];
  }
  return r;
}
#endif

static INLINE vdouble vposneg_vd_vd(vdouble d0) {
  vdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r[i*2+0] = +d0[i*2+0];
    r[i*2+1] = -d0[i*2+1];
  }
  return r;
}

static INLINE vdouble vnegpos_vd_vd(vdouble d0) {
  vdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r[i*2+0] = -d0[i*2+0];
    r[i*2+1] = +d0[i*2+1];
  }
  return r;
}

static INLINE vfloat vposneg_vf_vf(vfloat d0) {
  vfloat r;
  for(int i=0;i<VECTLENSP/2;i++) {
    r[i*2+0] = +d0[i*2+0];
    r[i*2+1] = -d0[i*2+1];
  }
  return r;
}

static INLINE vfloat vnegpos_vf_vf(vfloat d0) {
  vfloat r;
  for(int i=0;i<VECTLENSP/2;i++) {
    r[i*2+0] = -d0[i*2+0];
    r[i*2+1] = +d0[i*2+1];
  }
  return r;
}

#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vposneg_vl_vl(vlongdouble d0) {
  vlongdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r[i*2+0] = +d0[i*2+0];
    r[i*2+1] = -d0[i*2+1];
  }
  return r;
}

static INLINE vlongdouble vnegpos_vl_vl(vlongdouble d0) {
  vlongdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r[i*2+0] = -d0[i*2+0];
    r[i*2+1] = +d0[i*2+1];
  }
  return r;
}
#endif
#endif

//

static INLINE int vavailability_i(int name) { return -1; }
static INLINE void vprefetch_v_p(const void *ptr) { }

static INLINE int vtestallones_i_vo64(vopmask g) {
  int ret = 1; for(int i=0;i<VECTLENDP*2;i++) ret = ret && g[i]; return ret;
}

static INLINE int vtestallones_i_vo32(vopmask g) {
  int ret = 1; for(int i=0;i<VECTLENDP*2;i++) ret = ret && g[i]; return ret;
}

//

static vint2 vloadu_vi2_p(int32_t *p) {
  vint2 vi;
  for(int i=0;i<VECTLENSP;i++) vi[i] = p[i];
  return vi;
}

static void vstoreu_v_p_vi2(int32_t *p, vint2 v) {
  for(int i=0;i<VECTLENSP;i++) p[i] = v[i];
}

static vint vloadu_vi_p(int32_t *p) {
  vint vi;
  for(int i=0;i<VECTLENDP;i++) vi[i] = p[i];
  return vi;
}

static void vstoreu_v_p_vi(int32_t *p, vint v) {
  for(int i=0;i<VECTLENDP;i++) p[i] = v[i];
}

//

static INLINE vmask vand_vm_vm_vm(vmask x, vmask y) { return x & y; }
static INLINE vmask vandnot_vm_vm_vm(vmask x, vmask y) { return y & ~x; }
static INLINE vmask vor_vm_vm_vm(vmask x, vmask y) { return x | y; }
static INLINE vmask vxor_vm_vm_vm(vmask x, vmask y) { return x ^ y; }

static INLINE vopmask vand_vo_vo_vo(vopmask x, vopmask y) { return x & y; }
static INLINE vopmask vandnot_vo_vo_vo(vopmask x, vopmask y) { return y & ~x; }
static INLINE vopmask vor_vo_vo_vo(vopmask x, vopmask y) { return x | y; }
static INLINE vopmask vxor_vo_vo_vo(vopmask x, vopmask y) { return x ^ y; }

static INLINE vmask vand_vm_vo64_vm(vopmask x, vmask y) { return x & y; }
static INLINE vmask vandnot_vm_vo64_vm(vopmask x, vmask y) { return y & ~x; }
static INLINE vmask vor_vm_vo64_vm(vopmask x, vmask y) { return x | y; }
static INLINE vmask vxor_vm_vo64_vm(vopmask x, vmask y) { return x ^ y; }

static INLINE vmask vand_vm_vo32_vm(vopmask x, vmask y) { return x & y; }
static INLINE vmask vandnot_vm_vo32_vm(vopmask x, vmask y) { return y & ~x; }
static INLINE vmask vor_vm_vo32_vm(vopmask x, vmask y) { return x | y; }
static INLINE vmask vxor_vm_vo32_vm(vopmask x, vmask y) { return x ^ y; }

//

static INLINE vdouble vsel_vd_vo_vd_vd(vopmask o, vdouble x, vdouble y) { return (vdouble)(((vmask)o & (vmask)x) | ((vmask)y & ~(vmask)o)); }
static INLINE vint2 vsel_vi2_vo_vi2_vi2(vopmask o, vint2 x, vint2 y) { return (vint2)(((vmask)o & (vmask)x) | ((vmask)y & ~(vmask)o)); }

static INLINE CONST vdouble vsel_vd_vo_d_d(vopmask o, double v1, double v0) {
  return vsel_vd_vo_vd_vd(o, vcast_vd_d(v1), vcast_vd_d(v0));
}

static INLINE vdouble vsel_vd_vo_vo_d_d_d(vopmask o0, vopmask o1, double d0, double d1, double d2) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_d_d(o1, d1, d2));
}

static INLINE vdouble vsel_vd_vo_vo_vo_d_d_d_d(vopmask o0, vopmask o1, vopmask o2, double d0, double d1, double d2, double d3) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_vd_vd(o1, vcast_vd_d(d1), vsel_vd_vo_d_d(o2, d2, d3)));
}

static INLINE vdouble vcast_vd_vi(vint vi) {
#if defined(__clang__)
  return __builtin_convertvector(vi, vdouble);
#else
  vdouble vd;
  for(int i=0;i<VECTLENDP;i++) vd[i] = vi[i];
  return vd;
#endif
}
static INLINE vint vtruncate_vi_vd(vdouble vd) {
#if defined(__clang__)
  return __builtin_convertvector(vd, vint);
#else
  vint vi;
  for(int i=0;i<VECTLENDP;i++) vi[i] = vd[i];
  return vi;
#endif
}
static INLINE vint vrint_vi_vd(vdouble vd) { return vtruncate_vi_vd(vsel_vd_vo_vd_vd((vopmask)(vd < 0.0), vd - 0.5, vd + 0.5)); }
static INLINE vdouble vtruncate_vd_vd(vdouble vd) { return vcast_vd_vi(vtruncate_vi_vd(vd)); }
static INLINE vdouble vrint_vd_vd(vdouble vd) { return vcast_vd_vi(vrint_vi_vd(vd)); }

static INLINE vopmask veq64_vo_vm_vm(vmask x, vmask y) {
#if defined(__clang__)
  typedef int64_t vi64 __attribute__((ext_vector_type(VECTLENDP)));
#else
  typedef int64_t vi64 __attribute__((vector_size(sizeof(int64_t)*VECTLENDP)));
#endif
  return (vopmask)((vi64)x == (vi64)y);
}

static INLINE vmask vadd64_vm_vm_vm(vmask x, vmask y) {
#if defined(__clang__)
  typedef int64_t vi64 __attribute__((ext_vector_type(VECTLENDP)));
#else
  typedef int64_t vi64 __attribute__((vector_size(sizeof(int64_t)*VECTLENDP)));
#endif
  return (vmask)((vi64)x + (vi64)y);
}

//

static INLINE vmask vreinterpret_vm_vd(vdouble vd) { return (vmask)vd; }
static INLINE vint2 vreinterpret_vi2_vd(vdouble vd) { return (vint2)vd; }
static INLINE vdouble vreinterpret_vd_vi2(vint2 vi) { return (vdouble)vi; }
static INLINE vdouble vreinterpret_vd_vm(vmask vm) { return (vdouble)vm; }

static INLINE vdouble vadd_vd_vd_vd(vdouble x, vdouble y) { return x + y; }
static INLINE vdouble vsub_vd_vd_vd(vdouble x, vdouble y) { return x - y; }
static INLINE vdouble vmul_vd_vd_vd(vdouble x, vdouble y) { return x * y; }
static INLINE vdouble vdiv_vd_vd_vd(vdouble x, vdouble y) { return x / y; }
static INLINE vdouble vrec_vd_vd(vdouble x) { return 1.0 / x; }

static INLINE vdouble vabs_vd_vd(vdouble d) { return (vdouble)((vmask)d & ~(vmask)vcast_vd_d(-0.0)); }
static INLINE vdouble vneg_vd_vd(vdouble d) { return -d; }
static INLINE vdouble vmla_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return x * y + z; }
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return x * y - z; }
static INLINE vdouble vmax_vd_vd_vd(vdouble x, vdouble y) { return vsel_vd_vo_vd_vd((vopmask)(x > y), x, y); }
static INLINE vdouble vmin_vd_vd_vd(vdouble x, vdouble y) { return vsel_vd_vo_vd_vd((vopmask)(x < y), x, y); }

static INLINE vdouble vsubadd_vd_vd_vd(vdouble x, vdouble y) { return vadd_vd_vd_vd(x, vnegpos_vd_vd(y)); }
static INLINE vdouble vmlsubadd_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vsubadd_vd_vd_vd(vmul_vd_vd_vd(x, y), z); }

static INLINE vopmask veq_vo_vd_vd(vdouble x, vdouble y) { return (vopmask)(x == y); }
static INLINE vopmask vneq_vo_vd_vd(vdouble x, vdouble y) { return (vopmask)(x != y); }
static INLINE vopmask vlt_vo_vd_vd(vdouble x, vdouble y) { return (vopmask)(x < y); }
static INLINE vopmask vle_vo_vd_vd(vdouble x, vdouble y) { return (vopmask)(x <= y); }
static INLINE vopmask vgt_vo_vd_vd(vdouble x, vdouble y) { return (vopmask)(x > y); }
static INLINE vopmask vge_vo_vd_vd(vdouble x, vdouble y) { return (vopmask)(x >= y); }

static INLINE vint vadd_vi_vi_vi(vint x, vint y) { return x + y; }
static INLINE vint vsub_vi_vi_vi(vint x, vint y) { return x - y; }
static INLINE vint vneg_vi_vi(vint e) { return -e; }

static INLINE vint vand_vi_vi_vi(vint x, vint y) { return x & y; }
static INLINE vint vandnot_vi_vi_vi(vint x, vint y) { return y & ~x; }
static INLINE vint vor_vi_vi_vi(vint x, vint y) { return x | y; }
static INLINE vint vxor_vi_vi_vi(vint x, vint y) { return x ^ y; }

static INLINE vint vand_vi_vo_vi(vopmask x, vint y) { return vreinterpretFirstHalf_vi_vi2((vint2)x) & y; }
static INLINE vint vandnot_vi_vo_vi(vopmask x, vint y) { return y & ~vreinterpretFirstHalf_vi_vi2((vint2)x); }

static INLINE vint vsll_vi_vi_i(vint x, int c) {
#if defined(__clang__)
  typedef uint32_t vu __attribute__((ext_vector_type(VECTLENDP)));
#else
  typedef uint32_t vu __attribute__((vector_size(sizeof(uint32_t)*VECTLENDP)));
#endif
  return (vint)(((vu)x) << c);
}

static INLINE vint vsrl_vi_vi_i(vint x, int c) {
#if defined(__clang__)
  typedef uint32_t vu __attribute__((ext_vector_type(VECTLENDP)));
#else
  typedef uint32_t vu __attribute__((vector_size(sizeof(uint32_t)*VECTLENDP)));
#endif
  return (vint)(((vu)x) >> c);
}

static INLINE vint vsra_vi_vi_i(vint x, int c) { return x >> c; }

static INLINE vint veq_vi_vi_vi(vint x, vint y) { return x == y; }
static INLINE vint vgt_vi_vi_vi(vint x, vint y) { return x > y; }

static INLINE vopmask veq_vo_vi_vi(vint x, vint y) { return (vopmask)vreinterpretFirstHalf_vi2_vi(x == y); }
static INLINE vopmask vgt_vo_vi_vi(vint x, vint y) { return (vopmask)vreinterpretFirstHalf_vi2_vi(x > y);}

static INLINE vint vsel_vi_vo_vi_vi(vopmask m, vint x, vint y) {
  return vor_vi_vi_vi(vand_vi_vi_vi(vreinterpretFirstHalf_vi_vi2((vint2)m), x),
                      vandnot_vi_vi_vi(vreinterpretFirstHalf_vi_vi2((vint2)m), y));
}

static INLINE vopmask visinf_vo_vd(vdouble d) { return (vopmask)(vabs_vd_vd(d) == SLEEF_INFINITY); }
static INLINE vopmask vispinf_vo_vd(vdouble d) { return (vopmask)(d == SLEEF_INFINITY); }
static INLINE vopmask visminf_vo_vd(vdouble d) { return (vopmask)(d == -SLEEF_INFINITY); }
static INLINE vopmask visnan_vo_vd(vdouble d) { return (vopmask)(d != d); }

static INLINE vdouble vsqrt_vd_vd(vdouble d) {
#if defined(__clang__)
  typedef int64_t vi64 __attribute__((ext_vector_type(VECTLENDP)));
#else
  typedef int64_t vi64 __attribute__((vector_size(sizeof(int64_t)*VECTLENDP)));
#endif

  vdouble q = vcast_vd_d(1);

  vopmask o = (vopmask)(d < 8.636168555094445E-78);
  d = (vdouble)((o & (vmask)(d * 1.157920892373162E77)) | (~o & (vmask)d));

  q = (vdouble)((o & (vmask)vcast_vd_d(2.9387358770557188E-39)) | (~o & (vmask)vcast_vd_d(1)));

  q = (vdouble)vor_vm_vm_vm(vlt_vo_vd_vd(d, vcast_vd_d(0)), (vmask)q);

  vdouble x = (vdouble)(0x5fe6ec85e7de30daLL - ((vi64)(d + 1e-320) >> 1));
  x = x * (  3 - d * x * x);
  x = x * ( 12 - d * x * x);
  x = x * (768 - d * x * x);
  x *= 1.0 / (1 << 13);
  x = (d - (d * x) * (d * x)) * (x * 0.5) + d * x;

  return x * q;
}

static INLINE double vcast_d_vd(vdouble v) { return v[0]; }
static INLINE float vcast_f_vf(vfloat v) { return v[0]; }

static INLINE vdouble vload_vd_p(const double *ptr) { return *(vdouble *)ptr; }
static INLINE vdouble vloadu_vd_p(const double *ptr) {
  vdouble vd;
  for(int i=0;i<VECTLENDP;i++) vd[i] = ptr[i];
  return vd;
}

static INLINE vdouble vgather_vd_p_vi(const double *ptr, vint vi) {
  vdouble vd;
  for(int i=0;i<VECTLENDP;i++) vd[i] = ptr[vi[i]];
  return vd;
}

static INLINE void vstore_v_p_vd(double *ptr, vdouble v) { *(vdouble *)ptr = v; }
static INLINE void vstoreu_v_p_vd(double *ptr, vdouble v) {
  for(int i=0;i<VECTLENDP;i++) ptr[i] = v[i];
}
static INLINE void vstream_v_p_vd(double *ptr, vdouble v) { *(vdouble *)ptr = v; }

static INLINE void vscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) {
  for(int i=0;i<VECTLENDP/2;i++) {
    *(ptr+(offset + step * i)*2 + 0) = v[i*2+0];
    *(ptr+(offset + step * i)*2 + 1) = v[i*2+1];
  }
}

static INLINE void vsscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) { vscatter2_v_p_i_i_vd(ptr, offset, step, v); }

//

static INLINE vfloat vsel_vf_vo_vf_vf(vopmask o, vfloat x, vfloat y) { return (vfloat)(((vmask)o & (vmask)x) | (~(vmask)o & (vmask)y)); }

static INLINE CONST vfloat vsel_vf_vo_f_f(vopmask o, float v1, float v0) {
  return vsel_vf_vo_vf_vf(o, vcast_vf_f(v1), vcast_vf_f(v0));
}

static INLINE vfloat vsel_vf_vo_vo_f_f_f(vopmask o0, vopmask o1, float d0, float d1, float d2) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_f_f(o1, d1, d2));
}

static INLINE vfloat vsel_vf_vo_vo_vo_f_f_f_f(vopmask o0, vopmask o1, vopmask o2, float d0, float d1, float d2, float d3) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_vf_vf(o1, vcast_vf_f(d1), vsel_vf_vo_f_f(o2, d2, d3)));
}

static INLINE vint2 vcast_vi2_vm(vmask vm) { return (vint2)vm; }
static INLINE vmask vcast_vm_vi2(vint2 vi) { return (vmask)vi; }

static INLINE vfloat vcast_vf_vi2(vint2 vi) {
#if defined(__clang__)
  return __builtin_convertvector(vi, vfloat);
#else
  vfloat vf;
  for(int i=0;i<VECTLENDP*2;i++) vf[i] = vi[i];
  return vf;
#endif
}

static INLINE vint2 vtruncate_vi2_vf(vfloat vf) {
#if defined(__clang__)
  return __builtin_convertvector(vf, vint2);
#else
  vint2 vi;
  for(int i=0;i<VECTLENDP*2;i++) vi[i] = vf[i];
  return vi;
#endif
}

static INLINE vint2 vrint_vi2_vf(vfloat vf) { return vtruncate_vi2_vf(vsel_vf_vo_vf_vf((vopmask)(vf < 0), vf - 0.5f, vf + 0.5)); }
static INLINE vfloat vtruncate_vf_vf(vfloat vd) { return vcast_vf_vi2(vtruncate_vi2_vf(vd)); }
static INLINE vfloat vrint_vf_vf(vfloat vd) { return vcast_vf_vi2(vrint_vi2_vf(vd)); }

static INLINE vmask vreinterpret_vm_vf(vfloat vf) { return (vmask)vf; }
static INLINE vfloat vreinterpret_vf_vm(vmask vm) { return (vfloat)vm; }
static INLINE vfloat vreinterpret_vf_vi2(vint2 vi) { return (vfloat)vi; }
static INLINE vint2 vreinterpret_vi2_vf(vfloat vf) { return (vint2)vf; }

static INLINE vfloat vadd_vf_vf_vf(vfloat x, vfloat y) { return x + y; }
static INLINE vfloat vsub_vf_vf_vf(vfloat x, vfloat y) { return x - y; }
static INLINE vfloat vmul_vf_vf_vf(vfloat x, vfloat y) { return x * y; }
static INLINE vfloat vdiv_vf_vf_vf(vfloat x, vfloat y) { return x / y; }
static INLINE vfloat vrec_vf_vf(vfloat x) { return 1.0f / x; }

static INLINE vfloat vabs_vf_vf(vfloat f) { return (vfloat)vandnot_vm_vm_vm((vmask)vcast_vf_f(-0.0f), (vmask)f); }
static INLINE vfloat vneg_vf_vf(vfloat d) { return -d; }
static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return x*y+z; }
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return z-x*y; }
static INLINE vfloat vmax_vf_vf_vf(vfloat x, vfloat y) { return vsel_vf_vo_vf_vf((vopmask)(x > y), x, y); }
static INLINE vfloat vmin_vf_vf_vf(vfloat x, vfloat y) { return vsel_vf_vo_vf_vf((vopmask)(x < y), x, y); }

static INLINE vfloat vsubadd_vf_vf_vf(vfloat x, vfloat y) { return vadd_vf_vf_vf(x, vnegpos_vf_vf(y)); }
static INLINE vfloat vmlsubadd_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsubadd_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }

static INLINE vopmask veq_vo_vf_vf(vfloat x, vfloat y) { return (vopmask)(x == y); }
static INLINE vopmask vneq_vo_vf_vf(vfloat x, vfloat y) { return (vopmask)(x != y); }
static INLINE vopmask vlt_vo_vf_vf(vfloat x, vfloat y) { return (vopmask)(x < y); }
static INLINE vopmask vle_vo_vf_vf(vfloat x, vfloat y) { return (vopmask)(x <= y); }
static INLINE vopmask vgt_vo_vf_vf(vfloat x, vfloat y) { return (vopmask)(x > y); }
static INLINE vopmask vge_vo_vf_vf(vfloat x, vfloat y) { return (vopmask)(x >= y); }

static INLINE vint2 vadd_vi2_vi2_vi2(vint2 x, vint2 y) { return x + y; }
static INLINE vint2 vsub_vi2_vi2_vi2(vint2 x, vint2 y) { return x - y; }
static INLINE vint2 vneg_vi2_vi2(vint2 e) { return -e; }

static INLINE vint2 vand_vi2_vi2_vi2(vint2 x, vint2 y) { return x & y; }
static INLINE vint2 vandnot_vi2_vi2_vi2(vint2 x, vint2 y) { return  y & ~x; }
static INLINE vint2 vor_vi2_vi2_vi2(vint2 x, vint2 y) { return x | y; }
static INLINE vint2 vxor_vi2_vi2_vi2(vint2 x, vint2 y) { return x ^ y; }

static INLINE vint2 vand_vi2_vo_vi2(vopmask x, vint2 y) { return (vint2)x & y; }
static INLINE vint2 vandnot_vi2_vo_vi2(vopmask x, vint2 y) { return y & ~(vint2)x; }

static INLINE vint2 vsll_vi2_vi2_i(vint2 x, int c) {
#if defined(__clang__)
  typedef uint32_t vu __attribute__((ext_vector_type(VECTLENDP*2)));
#else
  typedef uint32_t vu __attribute__((vector_size(sizeof(uint32_t)*VECTLENDP*2)));
#endif
  return (vint2)(((vu)x) << c);
}
static INLINE vint2 vsrl_vi2_vi2_i(vint2 x, int c) {
#if defined(__clang__)
  typedef uint32_t vu __attribute__((ext_vector_type(VECTLENDP*2)));
#else
  typedef uint32_t vu __attribute__((vector_size(sizeof(uint32_t)*VECTLENDP*2)));
#endif
  return (vint2)(((vu)x) >> c);
}
static INLINE vint2 vsra_vi2_vi2_i(vint2 x, int c) { return x >> c; }

static INLINE vopmask veq_vo_vi2_vi2(vint2 x, vint2 y) { return (vopmask)(x == y); }
static INLINE vopmask vgt_vo_vi2_vi2(vint2 x, vint2 y) { return (vopmask)(x > y); }
static INLINE vint2 veq_vi2_vi2_vi2(vint2 x, vint2 y) { return x == y; }
static INLINE vint2 vgt_vi2_vi2_vi2(vint2 x, vint2 y) { return x > y; }

static INLINE vopmask visinf_vo_vf(vfloat d) { return (vopmask)(vabs_vf_vf(d) == SLEEF_INFINITYf); }
static INLINE vopmask vispinf_vo_vf(vfloat d) { return (vopmask)(d == SLEEF_INFINITYf); }
static INLINE vopmask visminf_vo_vf(vfloat d) { return (vopmask)(d == -SLEEF_INFINITYf); }
static INLINE vopmask visnan_vo_vf(vfloat d) { return (vopmask)(d != d); }

static INLINE vfloat vsqrt_vf_vf(vfloat d) {
  vfloat q = vcast_vf_f(1);

  vopmask o = (vopmask)(d < 5.4210108624275221700372640043497e-20f); // 2^-64
  d = (vfloat)((o & (vmask)(d * vcast_vf_f(18446744073709551616.0f))) | (~o & (vmask)d)); // 2^64
  q = (vfloat)((o & (vmask)vcast_vf_f(0.00000000023283064365386962890625f)) | (~o & (vmask)vcast_vf_f(1))); // 2^-32
  q = (vfloat)vor_vm_vm_vm(vlt_vo_vf_vf(d, vcast_vf_f(0)), (vmask)q);

  vfloat x = (vfloat)(0x5f330de2 - (((vint2)d) >> 1));
  x = x * ( 3.0f - d * x * x);
  x = x * (12.0f - d * x * x);
  x *= 0.0625f;
  x = (d - (d * x) * (d * x)) * (x * 0.5) + d * x;

  return x * q;
}

static INLINE vfloat vload_vf_p(const float *ptr) { return *(vfloat *)ptr; }
static INLINE vfloat vloadu_vf_p(const float *ptr) {
  vfloat vf;
  for(int i=0;i<VECTLENSP;i++) vf[i] = ptr[i];
  return vf;
}

static INLINE vfloat vgather_vf_p_vi2(const float *ptr, vint2 vi2) {
  vfloat vf;
  for(int i=0;i<VECTLENSP;i++) vf[i] = ptr[vi2[i]];
  return vf;
}

static INLINE void vstore_v_p_vf(float *ptr, vfloat v) { *(vfloat *)ptr = v; }
static INLINE void vstoreu_v_p_vf(float *ptr, vfloat v) {
  for(int i=0;i<VECTLENSP;i++) ptr[i] = v[i];
}
static INLINE void vstream_v_p_vf(float *ptr, vfloat v) { *(vfloat *)ptr = v; }

static INLINE void vscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) {
  for(int i=0;i<VECTLENSP/2;i++) {
    *(ptr+(offset + step * i)*2 + 0) = v[i*2+0];
    *(ptr+(offset + step * i)*2 + 1) = v[i*2+1];
  }
}

static INLINE void vsscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) { vscatter2_v_p_i_i_vf(ptr, offset, step, v); }

//

#ifdef ENABLE_LONGDOUBLE
static INLINE vlongdouble vadd_vl_vl_vl(vlongdouble x, vlongdouble y) { return x + y; }
static INLINE vlongdouble vsub_vl_vl_vl(vlongdouble x, vlongdouble y) { return x - y; }
static INLINE vlongdouble vmul_vl_vl_vl(vlongdouble x, vlongdouble y) { return x * y; }

static INLINE vlongdouble vneg_vl_vl(vlongdouble d) { return -d; }
static INLINE vlongdouble vsubadd_vl_vl_vl(vlongdouble x, vlongdouble y) { return vadd_vl_vl_vl(x, vnegpos_vl_vl(y)); }
static INLINE vlongdouble vmlsubadd_vl_vl_vl_vl(vlongdouble x, vlongdouble y, vlongdouble z) { return vsubadd_vl_vl_vl(vmul_vl_vl_vl(x, y), z); }

static INLINE vlongdouble vload_vl_p(const long double *ptr) { return *(vlongdouble *)ptr; }
static INLINE vlongdouble vloadu_vl_p(const long double *ptr) {
  vlongdouble vd;
  for(int i=0;i<VECTLENDP;i++) vd[i] = ptr[i];
  return vd;
}

static INLINE void vstore_v_p_vl(long double *ptr, vlongdouble v) { *(vlongdouble *)ptr = v; }
static INLINE void vstoreu_v_p_vl(long double *ptr, vlongdouble v) {
  for(int i=0;i<VECTLENDP;i++) ptr[i] = v[i];
}
static INLINE void vstream_v_p_vl(long double *ptr, vlongdouble v) { *(vlongdouble *)ptr = v; }

static INLINE void vscatter2_v_p_i_i_vl(long double *ptr, int offset, int step, vlongdouble v) {
  for(int i=0;i<VECTLENDP/2;i++) {
    *(ptr+(offset + step * i)*2 + 0) = v[i*2+0];
    *(ptr+(offset + step * i)*2 + 1) = v[i*2+1];
  }
}

static INLINE void vsscatter2_v_p_i_i_vl(long double *ptr, int offset, int step, vlongdouble v) { vscatter2_v_p_i_i_vl(ptr, offset, step, v); }
#endif

#if defined(Sleef_quad2_DEFINED) && defined(ENABLEFLOAT128)
static INLINE vquad vadd_vq_vq_vq(vquad x, vquad y) { return x + y; }
static INLINE vquad vsub_vq_vq_vq(vquad x, vquad y) { return x - y; }
static INLINE vquad vmul_vq_vq_vq(vquad x, vquad y) { return x * y; }

static INLINE vquad vneg_vq_vq(vquad d) { return -d; }
static INLINE vquad vsubadd_vq_vq_vq(vquad x, vquad y) { return vadd_vq_vq_vq(x, vnegpos_vq_vq(y)); }
static INLINE vquad vmlsubadd_vq_vq_vq_vq(vquad x, vquad y, vquad z) { return vsubadd_vq_vq_vq(vmul_vq_vq_vq(x, y), z); }

static INLINE vquad vload_vq_p(const Sleef_quad *ptr) { return *(vquad *)ptr; }
static INLINE vquad vloadu_vq_p(const Sleef_quad *ptr) {
  vquad vd;
  for(int i=0;i<VECTLENDP;i++) vd[i] = ptr[i];
  return vd;
}

static INLINE void vstore_v_p_vq(Sleef_quad *ptr, vquad v) { *(vquad *)ptr = v; }
static INLINE void vstoreu_v_p_vq(Sleef_quad *ptr, vquad v) {
  for(int i=0;i<VECTLENDP;i++) ptr[i] = v[i];
}
static INLINE void vstream_v_p_vq(Sleef_quad *ptr, vquad v) { *(vquad *)ptr = v; }

static INLINE void vscatter2_v_p_i_i_vq(Sleef_quad *ptr, int offset, int step, vquad v) {
  for(int i=0;i<VECTLENDP/2;i++) {
    *(ptr+(offset + step * i)*2 + 0) = v[i*2+0];
    *(ptr+(offset + step * i)*2 + 1) = v[i*2+1];
  }
}

static INLINE void vsscatter2_v_p_i_i_vq(Sleef_quad *ptr, int offset, int step, vquad v) { vscatter2_v_p_i_i_vq(ptr, offset, step, v); }
#endif
