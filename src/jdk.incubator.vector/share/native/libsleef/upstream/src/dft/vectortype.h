//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#ifndef __VECTORTYPE_H__
#define __VECTORTYPE_H__

#include <math.h>
#include "sleef.h"

#ifdef ENABLE_SSE2
#include "helpersse2.h"
#endif

#ifdef ENABLE_AVX
#include "helperavx.h"
#endif

#ifdef ENABLE_AVX2
#include "helperavx2.h"
#endif

#ifdef ENABLE_AVX512F
#include "helperavx512f.h"
#endif

#ifdef ENABLE_NEON32
#include "helperneon32.h"
#endif

#ifdef ENABLE_ADVSIMD
#include "helperadvsimd.h"
#endif

#ifdef ENABLE_SVE
#include "helpersve.h"
#endif

#if defined(ENABLE_RVVM1) || defined(ENABLE_RVVM2)
#include "helperrvv.h"
#endif

#ifdef ENABLE_VSX
#include "helperpower_128.h"
#endif

#ifdef ENABLE_VSX3
#include "helperpower_128.h"
#endif

#ifdef ENABLE_VXE
#include "helpers390x_128.h"
#endif

#ifdef ENABLE_VXE2
#include "helpers390x_128.h"
#endif

#ifdef ENABLE_VECEXT
#include "helpervecext.h"
#endif

#ifdef ENABLE_PUREC
#include "helperpurec.h"
#endif

#define IMPORT_IS_EXPORT
#include "sleefdft.h"

#if BASETYPEID == 1
#define LOG2VECWIDTH (LOG2VECTLENDP-1)
#define VECWIDTH (1 << LOG2VECWIDTH)

typedef double real;
typedef vdouble real2;

static int available(int name) { return vavailability_i(name); }

static INLINE real2 uminus(real2 d0) { return vneg_vd_vd(d0); }
static INLINE real2 uplusminus(real2 d0) { return vposneg_vd_vd(d0); }
static INLINE real2 uminusplus(real2 d0) { return vnegpos_vd_vd(d0); }

static INLINE real2 plus(real2 d0, real2 d1) { return vadd_vd_vd_vd(d0, d1); }
static INLINE real2 minus(real2 d0, real2 d1) { return vsub_vd_vd_vd(d0, d1); }
static INLINE real2 minusplus(real2 d0, real2 d1) { return vsubadd_vd_vd_vd(d0, d1); }
static INLINE real2 times(real2 d0, real2 d1) { return vmul_vd_vd_vd(d0, d1); }
static INLINE real2 timesminusplus(real2 d0, real2 d2, real2 d1) { return vmlsubadd_vd_vd_vd_vd(d0, d2, d1); }
static INLINE real2 ctimes(real2 d0, real d) { return vmul_vd_vd_vd(d0, vcast_vd_d(d)); }
static INLINE real2 ctimesminusplus(real2 d0, real c, real2 d1) { return vmlsubadd_vd_vd_vd_vd(d0, vcast_vd_d(c), d1); }

static INLINE real2 reverse(real2 d0) { return vrev21_vd_vd(d0); }
static INLINE real2 reverse2(real2 d0) { return vreva2_vd_vd(d0); }

static INLINE real2 loadc(real c) { return vcast_vd_d(c); }

static INLINE real2 load(const real *ptr, int offset) { return vload_vd_p(&ptr[2*offset]); }
static INLINE real2 loadu(const real *ptr, int offset) { return vloadu_vd_p(&ptr[2*offset]); }
static INLINE void store(real *ptr, int offset, real2 v) { vstore_v_p_vd(&ptr[2*offset], v); }
static INLINE void storeu(real *ptr, int offset, real2 v) { vstoreu_v_p_vd(&ptr[2*offset], v); }
static INLINE void stream(real *ptr, int offset, real2 v) { vstream_v_p_vd(&ptr[2*offset], v); }
static INLINE void scatter(real *ptr, int offset, int step, real2 v) { vscatter2_v_p_i_i_vd(ptr, offset, step, v); }
static INLINE void scstream(real *ptr, int offset, int step, real2 v) { vsscatter2_v_p_i_i_vd(ptr, offset, step, v); }

static INLINE void prefetch(real *ptr, int offset) { vprefetch_v_p(&ptr[2*offset]); }
#elif BASETYPEID == 2
#define LOG2VECWIDTH (LOG2VECTLENSP-1)
#define VECWIDTH (1 << LOG2VECWIDTH)

typedef float real;
typedef vfloat real2;

static int available(int name) { return vavailability_i(name); }

static INLINE real2 uminus(real2 d0) { return vneg_vf_vf(d0); }
static INLINE real2 uplusminus(real2 d0) { return vposneg_vf_vf(d0); }
static INLINE real2 uminusplus(real2 d0) { return vnegpos_vf_vf(d0); }

static INLINE real2 plus(real2 d0, real2 d1) { return vadd_vf_vf_vf(d0, d1); }
static INLINE real2 minus(real2 d0, real2 d1) { return vsub_vf_vf_vf(d0, d1); }
static INLINE real2 minusplus(real2 d0, real2 d1) { return vsubadd_vf_vf_vf(d0, d1); }
static INLINE real2 times(real2 d0, real2 d1) { return vmul_vf_vf_vf(d0, d1); }
static INLINE real2 ctimes(real2 d0, real d) { return vmul_vf_vf_vf(d0, vcast_vf_f(d)); }
static INLINE real2 timesminusplus(real2 d0, real2 d2, real2 d1) { return vmlsubadd_vf_vf_vf_vf(d0, d2, d1); }
static INLINE real2 ctimesminusplus(real2 d0, real c, real2 d1) { return vmlsubadd_vf_vf_vf_vf(d0, vcast_vf_f(c), d1); }

static INLINE real2 reverse(real2 d0) { return vrev21_vf_vf(d0); }
static INLINE real2 reverse2(real2 d0) { return vreva2_vf_vf(d0); }

static INLINE real2 loadc(real c) { return vcast_vf_f(c); }

static INLINE real2 load(const real *ptr, int offset) { return vload_vf_p(&ptr[2*offset]); }
static INLINE real2 loadu(const real *ptr, int offset) { return vloadu_vf_p(&ptr[2*offset]); }
static INLINE void store(real *ptr, int offset, real2 v) { vstore_v_p_vf(&ptr[2*offset], v); }
static INLINE void storeu(real *ptr, int offset, real2 v) { vstoreu_v_p_vf(&ptr[2*offset], v); }
static INLINE void stream(real *ptr, int offset, real2 v) { vstream_v_p_vf(&ptr[2*offset], v); }
static INLINE void scatter(real *ptr, int offset, int step, real2 v) { vscatter2_v_p_i_i_vf(ptr, offset, step, v); }
static INLINE void scstream(real *ptr, int offset, int step, real2 v) { vsscatter2_v_p_i_i_vf(ptr, offset, step, v); }

static INLINE void prefetch(real *ptr, int offset) { vprefetch_v_p(&ptr[2*offset]); }
#else
#error No BASETYPEID specified
#endif

#endif
