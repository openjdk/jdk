//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdint.h>
#include <math.h>
#include "misc.h"

#ifndef CONFIG
#error CONFIG macro not defined
#endif

#define ENABLE_DP
//@#define ENABLE_DP
#define ENABLE_SP
//@#define ENABLE_SP

#define LOG2VECTLENDP CONFIG
//@#define LOG2VECTLENDP CONFIG
#define VECTLENDP (1 << LOG2VECTLENDP)
//@#define VECTLENDP (1 << LOG2VECTLENDP)
#define LOG2VECTLENSP (LOG2VECTLENDP+1)
//@#define LOG2VECTLENSP (LOG2VECTLENDP+1)
#define VECTLENSP (1 << LOG2VECTLENSP)
//@#define VECTLENSP (1 << LOG2VECTLENSP)

#define ACCURATE_SQRT
//@#define ACCURATE_SQRT

#define DFTPRIORITY LOG2VECTLENDP
#define ISANAME "Pure C Array"

typedef union {
  uint32_t u[VECTLENDP*2];
  uint64_t x[VECTLENDP];
  double d[VECTLENDP];
  float f[VECTLENDP*2];
  int32_t i[VECTLENDP*2];
} versatileVector;

typedef versatileVector vmask;
typedef versatileVector vopmask;
typedef versatileVector vdouble;
typedef versatileVector vint;
typedef versatileVector vfloat;
typedef versatileVector vint2;

typedef union {
  uint8_t u[sizeof(long double)*VECTLENDP];
  long double ld[VECTLENDP];
} longdoubleVector;

typedef longdoubleVector vmaskl;
typedef longdoubleVector vlongdouble;

#if defined(Sleef_quad2_DEFINED) && defined(ENABLEFLOAT128)
typedef union {
  uint8_t u[sizeof(Sleef_quad)*VECTLENDP];
  Sleef_quad q[VECTLENDP];
} quadVector;

typedef quadVector vmaskq;
typedef quadVector vquad;
#endif

//

static INLINE int vavailability_i(int name) { return -1; }
static INLINE void vprefetch_v_p(const void *ptr) { }

static INLINE int vtestallones_i_vo64(vopmask g) {
  int ret = 1; for(int i=0;i<VECTLENDP;i++) ret = ret && g.x[i]; return ret;
}

static INLINE int vtestallones_i_vo32(vopmask g) {
  int ret = 1; for(int i=0;i<VECTLENSP;i++) ret = ret && g.u[i]; return ret;
}

//

static vint2 vloadu_vi2_p(int32_t *p) {
  vint2 vi;
  for(int i=0;i<VECTLENSP;i++) vi.i[i] = p[i];
  return vi;
}

static void vstoreu_v_p_vi2(int32_t *p, vint2 v) {
  for(int i=0;i<VECTLENSP;i++) p[i] = v.i[i];
}

static vint vloadu_vi_p(int32_t *p) {
  vint vi;
  for(int i=0;i<VECTLENDP;i++) vi.i[i] = p[i];
  return vi;
}

static void vstoreu_v_p_vi(int32_t *p, vint v) {
  for(int i=0;i<VECTLENDP;i++) p[i] = v.i[i];
}

//

static INLINE vopmask vcast_vo32_vo64(vopmask m) {
  vopmask ret;
  for(int i=0;i<VECTLENDP;i++) ret.u[i] = m.u[i*2+1];
  for(int i=VECTLENDP;i<VECTLENDP*2;i++) ret.u[i] = 0;
  return ret;
}

static INLINE vopmask vcast_vo64_vo32(vopmask m) {
  vopmask ret;
  for(int i=0;i<VECTLENDP;i++) ret.u[i*2] = ret.u[i*2+1] = m.u[i];
  return ret;
}

static INLINE vmask vcast_vm_i_i(int h, int l) {
  vmask ret;
  for(int i=0;i<VECTLENDP;i++) {
    ret.u[i*2+0] = l;
    ret.u[i*2+1] = h;
  }
  return ret;
}

static INLINE vint2 vcastu_vi2_vi(vint vi) {
  vint2 ret;
  for(int i=0;i<VECTLENDP;i++) {
    ret.i[i*2+0] = 0;
    ret.i[i*2+1] = vi.i[i];
  }
  return ret;
}

static INLINE vint vcastu_vi_vi2(vint2 vi2) {
  vint ret;
  for(int i=0;i<VECTLENDP;i++) ret.i[i] = vi2.i[i*2+1];
  return ret;
}

static INLINE vint vreinterpretFirstHalf_vi_vi2(vint2 vi2) {
  vint ret;
  for(int i=0;i<VECTLENDP;i++) ret.i[i] = vi2.i[i];
  return ret;
}

static INLINE vint2 vreinterpretFirstHalf_vi2_vi(vint vi) {
  vint2 ret;
  for(int i=0;i<VECTLENDP;i++) ret.i[i] = vi.i[i];
  for(int i=VECTLENDP;i<VECTLENDP*2;i++) ret.i[i] = 0;
  return ret;
}

static INLINE vdouble vrev21_vd_vd(vdouble d0) {
  vdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r.d[i*2+0] = d0.d[i*2+1];
    r.d[i*2+1] = d0.d[i*2+0];
  }
  return r;
}

static INLINE vdouble vreva2_vd_vd(vdouble d0) {
  vdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r.d[i*2+0] = d0.d[(VECTLENDP/2-1-i)*2+0];
    r.d[i*2+1] = d0.d[(VECTLENDP/2-1-i)*2+1];
  }
  return r;
}

static INLINE vfloat vrev21_vf_vf(vfloat d0) {
  vfloat r;
  for(int i=0;i<VECTLENSP/2;i++) {
    r.f[i*2+0] = d0.f[i*2+1];
    r.f[i*2+1] = d0.f[i*2+0];
  }
  return r;
}

static INLINE vfloat vreva2_vf_vf(vfloat d0) {
  vfloat r;
  for(int i=0;i<VECTLENSP/2;i++) {
    r.f[i*2+0] = d0.f[(VECTLENSP/2-1-i)*2+0];
    r.f[i*2+1] = d0.f[(VECTLENSP/2-1-i)*2+1];
  }
  return r;
}

static INLINE vdouble vcast_vd_d(double d) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = d; return ret; }

//

static INLINE vopmask vand_vo_vo_vo   (vopmask x, vopmask y) { vopmask ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] &  y.u[i]; return ret; }
static INLINE vopmask vandnot_vo_vo_vo(vopmask x, vopmask y) { vopmask ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = y.u[i] & ~x.u[i]; return ret; }
static INLINE vopmask vor_vo_vo_vo    (vopmask x, vopmask y) { vopmask ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] |  y.u[i]; return ret; }
static INLINE vopmask vxor_vo_vo_vo   (vopmask x, vopmask y) { vopmask ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] ^  y.u[i]; return ret; }

static INLINE vmask vand_vm_vm_vm     (vmask x, vmask y)     { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] &  y.u[i]; return ret; }
static INLINE vmask vandnot_vm_vm_vm  (vmask x, vmask y)     { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = y.u[i] & ~x.u[i]; return ret; }
static INLINE vmask vor_vm_vm_vm      (vmask x, vmask y)     { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] |  y.u[i]; return ret; }
static INLINE vmask vxor_vm_vm_vm     (vmask x, vmask y)     { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] ^  y.u[i]; return ret; }

static INLINE vmask vand_vm_vo64_vm(vopmask x, vmask y)      { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] &  y.u[i]; return ret; }
static INLINE vmask vandnot_vm_vo64_vm(vopmask x, vmask y)   { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = y.u[i] & ~x.u[i]; return ret; }
static INLINE vmask vor_vm_vo64_vm(vopmask x, vmask y)       { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] |  y.u[i]; return ret; }
static INLINE vmask vxor_vm_vo64_vm(vopmask x, vmask y)      { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] ^  y.u[i]; return ret; }

static INLINE vmask vand_vm_vo32_vm(vopmask x, vmask y)      { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] &  y.u[i]; return ret; }
static INLINE vmask vandnot_vm_vo32_vm(vopmask x, vmask y)   { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = y.u[i] & ~x.u[i]; return ret; }
static INLINE vmask vor_vm_vo32_vm(vopmask x, vmask y)       { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] |  y.u[i]; return ret; }
static INLINE vmask vxor_vm_vo32_vm(vopmask x, vmask y)      { vmask   ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = x.u[i] ^  y.u[i]; return ret; }

//

static INLINE vdouble vsel_vd_vo_vd_vd   (vopmask o, vdouble x, vdouble y) { vdouble ret; for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = (o.u[i] & x.u[i]) | (y.u[i] & ~o.u[i]); return ret; }
static INLINE vint2   vsel_vi2_vo_vi2_vi2(vopmask o, vint2 x, vint2 y)     { vint2 ret;   for(int i=0;i<VECTLENDP*2;i++) ret.u[i] = (o.u[i] & x.u[i]) | (y.u[i] & ~o.u[i]); return ret; }

static INLINE CONST vdouble vsel_vd_vo_d_d(vopmask o, double v1, double v0) {
  return vsel_vd_vo_vd_vd(o, vcast_vd_d(v1), vcast_vd_d(v0));
}

static INLINE vdouble vsel_vd_vo_vo_d_d_d(vopmask o0, vopmask o1, double d0, double d1, double d2) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_d_d(o1, d1, d2));
}

static INLINE vdouble vsel_vd_vo_vo_vo_d_d_d_d(vopmask o0, vopmask o1, vopmask o2, double d0, double d1, double d2, double d3) {
  return vsel_vd_vo_vd_vd(o0, vcast_vd_d(d0), vsel_vd_vo_vd_vd(o1, vcast_vd_d(d1), vsel_vd_vo_d_d(o2, d2, d3)));
}

static INLINE vdouble vcast_vd_vi(vint vi) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = vi.i[i]; return ret; }
static INLINE vint vtruncate_vi_vd(vdouble vd) { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = (int)vd.d[i]; return ret; }
static INLINE vint vrint_vi_vd(vdouble vd) { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = vd.d[i] > 0 ? (int)(vd.d[i] + 0.5) : (int)(vd.d[i] - 0.5); return ret; }
static INLINE vdouble vtruncate_vd_vd(vdouble vd) { return vcast_vd_vi(vtruncate_vi_vd(vd)); }
static INLINE vdouble vrint_vd_vd(vdouble vd) { return vcast_vd_vi(vrint_vi_vd(vd)); }
static INLINE vint vcast_vi_i(int j) { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = j; return ret; }

static INLINE vopmask veq64_vo_vm_vm(vmask x, vmask y) { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = x.x[i] == y.x[i] ? -1 : 0; return ret; }
static INLINE vmask vadd64_vm_vm_vm(vmask x, vmask y) { vmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = x.x[i] + y.x[i]; return ret; }

//

static INLINE vmask vreinterpret_vm_vd(vdouble vd) { union { vdouble vd; vmask vm; } cnv; cnv.vd = vd; return cnv.vm; }
static INLINE vint2 vreinterpret_vi2_vd(vdouble vd) { union { vdouble vd; vint2 vi2; } cnv; cnv.vd = vd; return cnv.vi2; }
static INLINE vdouble vreinterpret_vd_vi2(vint2 vi) { union { vint2 vi2; vdouble vd; } cnv; cnv.vi2 = vi; return cnv.vd; }
static INLINE vdouble vreinterpret_vd_vm(vmask vm) { union { vmask vm; vdouble vd; } cnv; cnv.vm = vm; return cnv.vd; }

static INLINE vdouble vadd_vd_vd_vd(vdouble x, vdouble y) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = x.d[i] + y.d[i]; return ret; }
static INLINE vdouble vsub_vd_vd_vd(vdouble x, vdouble y) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = x.d[i] - y.d[i]; return ret; }
static INLINE vdouble vmul_vd_vd_vd(vdouble x, vdouble y) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = x.d[i] * y.d[i]; return ret; }
static INLINE vdouble vdiv_vd_vd_vd(vdouble x, vdouble y) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = x.d[i] / y.d[i]; return ret; }
static INLINE vdouble vrec_vd_vd(vdouble x)               { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = 1.0 / x.d[i];    return ret; }

static INLINE vdouble vabs_vd_vd(vdouble d) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = d.x[i] & 0x7fffffffffffffffULL; return ret; }
static INLINE vdouble vneg_vd_vd(vdouble d) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = -d.d[i]; return ret; }
static INLINE vdouble vmla_vd_vd_vd_vd  (vdouble x, vdouble y, vdouble z) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = x.d[i] * y.d[i] + z.d[i]; return ret; }
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = x.d[i] * y.d[i] - z.d[i]; return ret; }
static INLINE vdouble vmax_vd_vd_vd(vdouble x, vdouble y) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = x.d[i] > y.d[i] ? x.d[i] : y.d[i]; return ret; }
static INLINE vdouble vmin_vd_vd_vd(vdouble x, vdouble y) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = x.d[i] < y.d[i] ? x.d[i] : y.d[i]; return ret; }

static INLINE vdouble vposneg_vd_vd(vdouble d) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = (i & 1) == 0 ?  d.d[i] : -d.d[i]; return ret; }
static INLINE vdouble vnegpos_vd_vd(vdouble d) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = (i & 1) == 0 ? -d.d[i] :  d.d[i]; return ret; }
static INLINE vdouble vsubadd_vd_vd_vd(vdouble x, vdouble y) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = (i & 1) == 0 ? x.d[i] - y.d[i] : x.d[i] + y.d[i]; return ret; }
static INLINE vdouble vmlsubadd_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vsubadd_vd_vd_vd(vmul_vd_vd_vd(x, y), z); }

static INLINE vopmask veq_vo_vd_vd(vdouble x, vdouble y)  { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = x.d[i] == y.d[i] ? -1 : 0; return ret; }
static INLINE vopmask vneq_vo_vd_vd(vdouble x, vdouble y) { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = x.d[i] != y.d[i] ? -1 : 0; return ret; }
static INLINE vopmask vlt_vo_vd_vd(vdouble x, vdouble y)  { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = x.d[i] <  y.d[i] ? -1 : 0; return ret; }
static INLINE vopmask vle_vo_vd_vd(vdouble x, vdouble y)  { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = x.d[i] <= y.d[i] ? -1 : 0; return ret; }
static INLINE vopmask vgt_vo_vd_vd(vdouble x, vdouble y)  { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = x.d[i] >  y.d[i] ? -1 : 0; return ret; }
static INLINE vopmask vge_vo_vd_vd(vdouble x, vdouble y)  { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = x.d[i] >= y.d[i] ? -1 : 0; return ret; }

static INLINE vint vadd_vi_vi_vi(vint x, vint y) { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = x.i[i] + y.i[i]; return ret; }
static INLINE vint vsub_vi_vi_vi(vint x, vint y) { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = x.i[i] - y.i[i]; return ret; }
static INLINE vint vneg_vi_vi   (vint x)         { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = -x.i[i];         return ret; }

static INLINE vint vand_vi_vi_vi(vint x, vint y)    { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = x.i[i] &  y.i[i]; return ret; }
static INLINE vint vandnot_vi_vi_vi(vint x, vint y) { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = y.i[i] & ~x.i[i]; return ret; }
static INLINE vint vor_vi_vi_vi(vint x, vint y)     { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = x.i[i] |  y.i[i]; return ret; }
static INLINE vint vxor_vi_vi_vi(vint x, vint y)    { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = x.i[i] ^  y.i[i]; return ret; }

static INLINE vint vand_vi_vo_vi(vopmask x, vint y)    { return vand_vi_vi_vi(vreinterpretFirstHalf_vi_vi2(x), y); }
static INLINE vint vandnot_vi_vo_vi(vopmask x, vint y) { return vandnot_vi_vi_vi(vreinterpretFirstHalf_vi_vi2(x), y); }

static INLINE vint vsll_vi_vi_i(vint x, int c) { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = x.i[i] << c; return ret; }
static INLINE vint vsrl_vi_vi_i(vint x, int c) { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = ((uint32_t)x.i[i]) >> c; return ret; }
static INLINE vint vsra_vi_vi_i(vint x, int c) { vint ret; for(int i=0;i<VECTLENDP;i++) ret.i[i] = x.i[i] >> c; return ret; }

static INLINE vopmask veq_vo_vi_vi(vint x, vint y) { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.u[i] = x.i[i] == y.i[i] ? -1 : 0; return ret; }
static INLINE vopmask vgt_vo_vi_vi(vint x, vint y) { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.u[i] = x.i[i] >  y.i[i] ? -1 : 0; return ret; }

static INLINE vint vsel_vi_vo_vi_vi(vopmask m, vint x, vint y) {
  union { vopmask vo; vint2 vi2; } cnv;
  cnv.vo = m;
  return vor_vi_vi_vi(vand_vi_vi_vi(vreinterpretFirstHalf_vi_vi2(cnv.vi2), x),
                      vandnot_vi_vi_vi(vreinterpretFirstHalf_vi_vi2(cnv.vi2), y));
}

static INLINE vopmask visinf_vo_vd(vdouble d)  { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = (d.d[i] == SLEEF_INFINITY || d.d[i] == -SLEEF_INFINITY) ? -1 : 0; return ret; }
static INLINE vopmask vispinf_vo_vd(vdouble d) { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = d.d[i] == SLEEF_INFINITY ? -1 : 0; return ret; }
static INLINE vopmask visminf_vo_vd(vdouble d) { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = d.d[i] == -SLEEF_INFINITY ? -1 : 0; return ret; }
static INLINE vopmask visnan_vo_vd(vdouble d)  { vopmask ret; for(int i=0;i<VECTLENDP;i++) ret.x[i] = d.d[i] != d.d[i] ? -1 : 0; return ret; }

static INLINE vdouble vsqrt_vd_vd(vdouble d) { vdouble ret; for(int i=0;i<VECTLENDP;i++) ret.d[i] = sqrt(d.d[i]); return ret; }

#if defined(_MSC_VER)
// This function is needed when debugging on MSVC.
static INLINE double vcast_d_vd(vdouble v) { return v.d[0]; }
#endif

static INLINE vdouble vload_vd_p(const double *ptr) { return *(vdouble *)ptr; }
static INLINE vdouble vloadu_vd_p(const double *ptr) { vdouble vd; for(int i=0;i<VECTLENDP;i++) vd.d[i] = ptr[i]; return vd; }

static INLINE vdouble vgather_vd_p_vi(const double *ptr, vint vi) {
  vdouble vd;
  for(int i=0;i<VECTLENDP;i++) vd.d[i] = ptr[vi.i[i]];
  return vd;
}

static INLINE void vstore_v_p_vd(double *ptr, vdouble v) { *(vdouble *)ptr = v; }
static INLINE void vstoreu_v_p_vd(double *ptr, vdouble v) { for(int i=0;i<VECTLENDP;i++) ptr[i] = v.d[i]; }
static INLINE void vstream_v_p_vd(double *ptr, vdouble v) { *(vdouble *)ptr = v; }

static INLINE void vscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) {
  for(int i=0;i<VECTLENDP/2;i++) {
    *(ptr+(offset + step * i)*2 + 0) = v.d[i*2+0];
    *(ptr+(offset + step * i)*2 + 1) = v.d[i*2+1];
  }
}

static INLINE void vsscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) { vscatter2_v_p_i_i_vd(ptr, offset, step, v); }

//

static INLINE vint2 vcast_vi2_vm(vmask vm) { union { vint2 vi2; vmask vm; } cnv; cnv.vm = vm; return cnv.vi2; }
static INLINE vmask vcast_vm_vi2(vint2 vi) { union { vint2 vi2; vmask vm; } cnv; cnv.vi2 = vi; return cnv.vm; }

static INLINE vfloat vcast_vf_vi2(vint2 vi) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = vi.i[i]; return ret; }
static INLINE vint2 vtruncate_vi2_vf(vfloat vf) { vint2 ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = (int)vf.f[i]; return ret; }
static INLINE vint2 vrint_vi2_vf(vfloat vf) { vint ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = vf.f[i] > 0 ? (int)(vf.f[i] + 0.5) : (int)(vf.f[i] - 0.5); return ret; }
static INLINE vint2 vcast_vi2_i(int j) { vint2 ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = j; return ret; }
static INLINE vfloat vtruncate_vf_vf(vfloat vd) { return vcast_vf_vi2(vtruncate_vi2_vf(vd)); }
static INLINE vfloat vrint_vf_vf(vfloat vd) { return vcast_vf_vi2(vrint_vi2_vf(vd)); }

static INLINE vfloat vcast_vf_f(float f) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = f; return ret; }
static INLINE vmask vreinterpret_vm_vf(vfloat vf) { union { vfloat vf; vmask vm; } cnv; cnv.vf = vf; return cnv.vm; }
static INLINE vfloat vreinterpret_vf_vm(vmask vm) { union { vfloat vf; vmask vm; } cnv; cnv.vm = vm; return cnv.vf; }
static INLINE vfloat vreinterpret_vf_vi2(vint2 vi) { union { vfloat vf; vint2 vi2; } cnv; cnv.vi2 = vi; return cnv.vf; }
static INLINE vint2 vreinterpret_vi2_vf(vfloat vf) { union { vfloat vf; vint2 vi2; } cnv; cnv.vf = vf; return cnv.vi2; }

static INLINE vfloat vadd_vf_vf_vf(vfloat x, vfloat y) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = x.f[i] + y.f[i]; return ret; }
static INLINE vfloat vsub_vf_vf_vf(vfloat x, vfloat y) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = x.f[i] - y.f[i]; return ret; }
static INLINE vfloat vmul_vf_vf_vf(vfloat x, vfloat y) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = x.f[i] * y.f[i]; return ret; }
static INLINE vfloat vdiv_vf_vf_vf(vfloat x, vfloat y) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = x.f[i] / y.f[i]; return ret; }
static INLINE vfloat vrec_vf_vf   (vfloat x)           { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = 1.0    / x.f[i]; return ret; }

static INLINE vfloat vabs_vf_vf(vfloat x) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] & 0x7fffffff; return ret; }
static INLINE vfloat vneg_vf_vf(vfloat x) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = -x.f[i]; return ret; }
static INLINE vfloat vmla_vf_vf_vf_vf  (vfloat x, vfloat y, vfloat z) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = x.f[i] * y.f[i] + z.f[i]; return ret; }
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = x.f[i] * y.f[i] - z.f[i]; return ret; }
static INLINE vfloat vmax_vf_vf_vf(vfloat x, vfloat y) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = x.f[i] > y.f[i] ? x.f[i] : y.f[i]; return ret; }
static INLINE vfloat vmin_vf_vf_vf(vfloat x, vfloat y) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = x.f[i] < y.f[i] ? x.f[i] : y.f[i]; return ret; }

static INLINE vfloat vposneg_vf_vf(vfloat x) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = (i & 1) == 0 ?  x.f[i] : -x.f[i]; return ret; }
static INLINE vfloat vnegpos_vf_vf(vfloat x) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = (i & 1) == 0 ? -x.f[i] :  x.f[i]; return ret; }
static INLINE vfloat vsubadd_vf_vf_vf(vfloat x, vfloat y) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = (i & 1) == 0 ? x.f[i] - y.f[i] : x.f[i] + y.f[i]; return ret; }
static INLINE vfloat vmlsubadd_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsubadd_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }

static INLINE vopmask veq_vo_vf_vf(vfloat x, vfloat y)  { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = ((x.f[i] == y.f[i]) ? -1 : 0); return ret; }
static INLINE vopmask vneq_vo_vf_vf(vfloat x, vfloat y) { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = ((x.f[i] != y.f[i]) ? -1 : 0); return ret; }
static INLINE vopmask vlt_vo_vf_vf(vfloat x, vfloat y)  { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = ((x.f[i] <  y.f[i]) ? -1 : 0); return ret; }
static INLINE vopmask vle_vo_vf_vf(vfloat x, vfloat y)  { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = ((x.f[i] <= y.f[i]) ? -1 : 0); return ret; }
static INLINE vopmask vgt_vo_vf_vf(vfloat x, vfloat y)  { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = ((x.f[i] >  y.f[i]) ? -1 : 0); return ret; }
static INLINE vopmask vge_vo_vf_vf(vfloat x, vfloat y)  { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = ((x.f[i] >= y.f[i]) ? -1 : 0); return ret; }

static INLINE vint vadd_vi2_vi2_vi2(vint x, vint y) { vint ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] + y.i[i]; return ret; }
static INLINE vint vsub_vi2_vi2_vi2(vint x, vint y) { vint ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] - y.i[i]; return ret; }
static INLINE vint vneg_vi2_vi2(vint x)             { vint ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = -x.i[i]; return ret; }

static INLINE vint vand_vi2_vi2_vi2(vint x, vint y)    { vint ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] &  y.i[i]; return ret; }
static INLINE vint vandnot_vi2_vi2_vi2(vint x, vint y) { vint ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = y.i[i] & ~x.i[i]; return ret; }
static INLINE vint vor_vi2_vi2_vi2(vint x, vint y)     { vint ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] |  y.i[i]; return ret; }
static INLINE vint vxor_vi2_vi2_vi2(vint x, vint y)    { vint ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] ^  y.i[i]; return ret; }

static INLINE vfloat vsel_vf_vo_vf_vf(vopmask o, vfloat x, vfloat y) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = (o.u[i] & x.u[i]) | (y.u[i] & ~o.u[i]); return ret; }

static INLINE CONST vfloat vsel_vf_vo_f_f(vopmask o, float v1, float v0) {
  return vsel_vf_vo_vf_vf(o, vcast_vf_f(v1), vcast_vf_f(v0));
}

static INLINE vfloat vsel_vf_vo_vo_f_f_f(vopmask o0, vopmask o1, float d0, float d1, float d2) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_f_f(o1, d1, d2));
}

static INLINE vfloat vsel_vf_vo_vo_vo_f_f_f_f(vopmask o0, vopmask o1, vopmask o2, float d0, float d1, float d2, float d3) {
  return vsel_vf_vo_vf_vf(o0, vcast_vf_f(d0), vsel_vf_vo_vf_vf(o1, vcast_vf_f(d1), vsel_vf_vo_f_f(o2, d2, d3)));
}

static INLINE vint2 vand_vi2_vo_vi2(vopmask x, vint2 y) {
  union { vopmask vo; vint2 vi2; } cnv;
  cnv.vo = x;
  return vand_vi2_vi2_vi2(cnv.vi2, y);
}
static INLINE vint2 vandnot_vi2_vo_vi2(vopmask x, vint2 y) { return vandnot_vi2_vi2_vi2(x, y); }

static INLINE vint2 vsll_vi2_vi2_i(vint2 x, int c) { vint2 ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] << c; return ret; }
static INLINE vint2 vsrl_vi2_vi2_i(vint2 x, int c) { vint2 ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = ((uint32_t)x.i[i]) >> c; return ret; }
static INLINE vint2 vsra_vi2_vi2_i(vint2 x, int c) { vint2 ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] >> c; return ret; }

static INLINE vopmask visinf_vo_vf (vfloat d) { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = (d.f[i] == SLEEF_INFINITYf || d.f[i] == -SLEEF_INFINITYf) ? -1 : 0; return ret; }
static INLINE vopmask vispinf_vo_vf(vfloat d) { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = d.f[i] == SLEEF_INFINITYf ? -1 : 0; return ret; }
static INLINE vopmask visminf_vo_vf(vfloat d) { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = d.f[i] == -SLEEF_INFINITYf ? -1 : 0; return ret; }
static INLINE vopmask visnan_vo_vf (vfloat d) { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = d.f[i] != d.f[i] ? -1 : 0; return ret; }

static INLINE vopmask veq_vo_vi2_vi2 (vint2 x, vint2 y) { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = x.i[i] == y.i[i] ? -1 : 0; return ret; }
static INLINE vopmask vgt_vo_vi2_vi2 (vint2 x, vint2 y) { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.u[i] = x.i[i] >  y.i[i] ? -1 : 0; return ret; }
static INLINE vint2   veq_vi2_vi2_vi2(vint2 x, vint2 y) { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] == y.i[i] ? -1 : 0; return ret; }
static INLINE vint2   vgt_vi2_vi2_vi2(vint2 x, vint2 y) { vopmask ret; for(int i=0;i<VECTLENSP;i++) ret.i[i] = x.i[i] >  y.i[i] ? -1 : 0; return ret; }

static INLINE vfloat vsqrt_vf_vf(vfloat x) { vfloat ret; for(int i=0;i<VECTLENSP;i++) ret.f[i] = sqrtf(x.f[i]); return ret; }

#ifdef _MSC_VER
// This function is needed when debugging on MSVC.
static INLINE float vcast_f_vf(vfloat v) { return v.f[0]; }
#endif

static INLINE vfloat vload_vf_p(const float *ptr) { return *(vfloat *)ptr; }
static INLINE vfloat vloadu_vf_p(const float *ptr) {
  vfloat vf;
  for(int i=0;i<VECTLENSP;i++) vf.f[i] = ptr[i];
  return vf;
}

static INLINE vfloat vgather_vf_p_vi2(const float *ptr, vint2 vi2) {
  vfloat vf;
  for(int i=0;i<VECTLENSP;i++) vf.f[i] = ptr[vi2.i[i]];
  return vf;
}

static INLINE void vstore_v_p_vf(float *ptr, vfloat v) { *(vfloat *)ptr = v; }
static INLINE void vstoreu_v_p_vf(float *ptr, vfloat v) {
  for(int i=0;i<VECTLENSP;i++) ptr[i] = v.f[i];
}
static INLINE void vstream_v_p_vf(float *ptr, vfloat v) { *(vfloat *)ptr = v; }

static INLINE void vscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) {
  for(int i=0;i<VECTLENSP/2;i++) {
    *(ptr+(offset + step * i)*2 + 0) = v.f[i*2+0];
    *(ptr+(offset + step * i)*2 + 1) = v.f[i*2+1];
  }
}

static INLINE void vsscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) { vscatter2_v_p_i_i_vf(ptr, offset, step, v); }

//

static INLINE vlongdouble vcast_vl_l(long double d) { vlongdouble ret; for(int i=0;i<VECTLENDP;i++) ret.ld[i] = d; return ret; }

static INLINE vlongdouble vrev21_vl_vl(vlongdouble d0) {
  vlongdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r.ld[i*2+0] = d0.ld[i*2+1];
    r.ld[i*2+1] = d0.ld[i*2+0];
  }
  return r;
}

static INLINE vlongdouble vreva2_vl_vl(vlongdouble d0) {
  vlongdouble r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r.ld[i*2+0] = d0.ld[(VECTLENDP/2-1-i)*2+0];
    r.ld[i*2+1] = d0.ld[(VECTLENDP/2-1-i)*2+1];
  }
  return r;
}

static INLINE vlongdouble vadd_vl_vl_vl(vlongdouble x, vlongdouble y) { vlongdouble ret; for(int i=0;i<VECTLENDP;i++) ret.ld[i] = x.ld[i] + y.ld[i]; return ret; }
static INLINE vlongdouble vsub_vl_vl_vl(vlongdouble x, vlongdouble y) { vlongdouble ret; for(int i=0;i<VECTLENDP;i++) ret.ld[i] = x.ld[i] - y.ld[i]; return ret; }
static INLINE vlongdouble vmul_vl_vl_vl(vlongdouble x, vlongdouble y) { vlongdouble ret; for(int i=0;i<VECTLENDP;i++) ret.ld[i] = x.ld[i] * y.ld[i]; return ret; }

static INLINE vlongdouble vneg_vl_vl(vlongdouble x) { vlongdouble ret; for(int i=0;i<VECTLENDP;i++) ret.ld[i] = -x.ld[i]; return ret; }
static INLINE vlongdouble vsubadd_vl_vl_vl(vlongdouble x, vlongdouble y) { vlongdouble ret; for(int i=0;i<VECTLENDP;i++) ret.ld[i] = (i & 1) == 0 ? x.ld[i] - y.ld[i] : x.ld[i] + y.ld[i]; return ret; }
static INLINE vlongdouble vmlsubadd_vl_vl_vl_vl(vlongdouble x, vlongdouble y, vlongdouble z) { return vsubadd_vl_vl_vl(vmul_vl_vl_vl(x, y), z); }
static INLINE vlongdouble vposneg_vl_vl(vlongdouble x) { vlongdouble ret; for(int i=0;i<VECTLENDP;i++) ret.ld[i] = (i & 1) == 0 ?  x.ld[i] : -x.ld[i]; return ret; }
static INLINE vlongdouble vnegpos_vl_vl(vlongdouble x) { vlongdouble ret; for(int i=0;i<VECTLENDP;i++) ret.ld[i] = (i & 1) == 0 ? -x.ld[i] :  x.ld[i]; return ret; }

static INLINE vlongdouble vload_vl_p(const long double *ptr) { return *(vlongdouble *)ptr; }
static INLINE vlongdouble vloadu_vl_p(const long double *ptr) {
  vlongdouble vd;
  for(int i=0;i<VECTLENDP;i++) vd.ld[i] = ptr[i];
  return vd;
}

static INLINE void vstore_v_p_vl(long double *ptr, vlongdouble v) { *(vlongdouble *)ptr = v; }
static INLINE void vstoreu_v_p_vl(long double *ptr, vlongdouble v) {
  for(int i=0;i<VECTLENDP;i++) ptr[i] = v.ld[i];
}
static INLINE void vstream_v_p_vl(long double *ptr, vlongdouble v) { *(vlongdouble *)ptr = v; }

static INLINE void vscatter2_v_p_i_i_vl(long double *ptr, int offset, int step, vlongdouble v) {
  for(int i=0;i<VECTLENDP/2;i++) {
    *(ptr+(offset + step * i)*2 + 0) = v.ld[i*2+0];
    *(ptr+(offset + step * i)*2 + 1) = v.ld[i*2+1];
  }
}

static INLINE void vsscatter2_v_p_i_i_vl(long double *ptr, int offset, int step, vlongdouble v) { vscatter2_v_p_i_i_vl(ptr, offset, step, v); }

#if defined(Sleef_quad2_DEFINED) && defined(ENABLEFLOAT128)
static INLINE vquad vcast_vq_q(Sleef_quad d) { vquad ret; for(int i=0;i<VECTLENDP;i++) ret.q[i] = d; return ret; }

static INLINE vquad vrev21_vq_vq(vquad d0) {
  vquad r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r.q[i*2+0] = d0.q[i*2+1];
    r.q[i*2+1] = d0.q[i*2+0];
  }
  return r;
}

static INLINE vquad vreva2_vq_vq(vquad d0) {
  vquad r;
  for(int i=0;i<VECTLENDP/2;i++) {
    r.q[i*2+0] = d0.q[(VECTLENDP/2-1-i)*2+0];
    r.q[i*2+1] = d0.q[(VECTLENDP/2-1-i)*2+1];
  }
  return r;
}

static INLINE vquad vadd_vq_vq_vq(vquad x, vquad y) { vquad ret; for(int i=0;i<VECTLENDP;i++) ret.q[i] = x.q[i] + y.q[i]; return ret; }
static INLINE vquad vsub_vq_vq_vq(vquad x, vquad y) { vquad ret; for(int i=0;i<VECTLENDP;i++) ret.q[i] = x.q[i] - y.q[i]; return ret; }
static INLINE vquad vmul_vq_vq_vq(vquad x, vquad y) { vquad ret; for(int i=0;i<VECTLENDP;i++) ret.q[i] = x.q[i] * y.q[i]; return ret; }

static INLINE vquad vneg_vq_vq(vquad x) { vquad ret; for(int i=0;i<VECTLENDP;i++) ret.q[i] = -x.q[i]; return ret; }
static INLINE vquad vsubadd_vq_vq_vq(vquad x, vquad y) { vquad ret; for(int i=0;i<VECTLENDP;i++) ret.q[i] = (i & 1) == 0 ? x.q[i] - y.q[i] : x.q[i] + y.q[i]; return ret; }
static INLINE vquad vmlsubadd_vq_vq_vq_vq(vquad x, vquad y, vquad z) { return vsubadd_vq_vq_vq(vmul_vq_vq_vq(x, y), z); }
static INLINE vquad vposneg_vq_vq(vquad x) { vquad ret; for(int i=0;i<VECTLENDP;i++) ret.q[i] = (i & 1) == 0 ?  x.q[i] : -x.q[i]; return ret; }
static INLINE vquad vnegpos_vq_vq(vquad x) { vquad ret; for(int i=0;i<VECTLENDP;i++) ret.q[i] = (i & 1) == 0 ? -x.q[i] :  x.q[i]; return ret; }

static INLINE vquad vload_vq_p(const Sleef_quad *ptr) { return *(vquad *)ptr; }
static INLINE vquad vloadu_vq_p(const Sleef_quad *ptr) {
  vquad vd;
  for(int i=0;i<VECTLENDP;i++) vd.q[i] = ptr[i];
  return vd;
}

static INLINE void vstore_v_p_vq(Sleef_quad *ptr, vquad v) { *(vquad *)ptr = v; }
static INLINE void vstoreu_v_p_vq(Sleef_quad *ptr, vquad v) {
  for(int i=0;i<VECTLENDP;i++) ptr[i] = v.q[i];
}
static INLINE void vstream_v_p_vq(Sleef_quad *ptr, vquad v) { *(vquad *)ptr = v; }

static INLINE void vscatter2_v_p_i_i_vq(Sleef_quad *ptr, int offset, int step, vquad v) {
  for(int i=0;i<VECTLENDP/2;i++) {
    *(ptr+(offset + step * i)*2 + 0) = v.q[i*2+0];
    *(ptr+(offset + step * i)*2 + 1) = v.q[i*2+1];
  }
}

static INLINE void vsscatter2_v_p_i_i_vq(Sleef_quad *ptr, int offset, int step, vquad v) { vscatter2_v_p_i_i_vq(ptr, offset, step, v); }
#endif
