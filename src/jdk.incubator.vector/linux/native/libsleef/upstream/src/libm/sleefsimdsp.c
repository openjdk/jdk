//   Copyright Naoki Shibata and contributors 2010 - 2024.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

// Always use -ffp-contract=off option to compile SLEEF.

#if !defined(SLEEF_GENHEADER)
#include <stdint.h>
#include <assert.h>
#include <limits.h>
#include <float.h>
#endif

#include "quaddef.h"
#include "misc.h"

#ifndef SLEEF_ENABLE_CUDA
extern const float Sleef_rempitabsp[];
#endif

#define __SLEEFSIMDSP_C__

#if defined(_MSC_VER) && !defined (__clang__)
#pragma fp_contract (off)
#else
#pragma STDC FP_CONTRACT OFF
#endif

// Intel

#ifdef ENABLE_SSE2
#define CONFIG 2
#if !defined(SLEEF_GENHEADER)
#include "helpersse2.h"
#else
#include "macroonlySSE2.h"
#endif
#ifdef DORENAME
#ifdef ENABLE_GNUABI
#include "renamesse2_gnuabi.h"
#else
#include "renamesse2.h"
#endif
#endif
#endif

#ifdef ENABLE_SSE4
#define CONFIG 4
#if !defined(SLEEF_GENHEADER)
#include "helpersse2.h"
#else
#include "macroonlySSE4.h"
#endif
#ifdef DORENAME
#include "renamesse4.h"
#endif
#endif

#ifdef ENABLE_AVX
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helperavx.h"
#else
#include "macroonlyAVX.h"
#endif
#ifdef DORENAME
#ifdef ENABLE_GNUABI
#include "renameavx_gnuabi.h"
#else
#include "renameavx.h"
#endif
#endif
#endif

#ifdef ENABLE_FMA4
#define CONFIG 4
#if !defined(SLEEF_GENHEADER)
#include "helperavx.h"
#else
#include "macroonlyFMA4.h"
#endif
#ifdef DORENAME
#ifdef ENABLE_GNUABI
#include "renamefma4_gnuabi.h"
#else
#include "renamefma4.h"
#endif
#endif
#endif

#ifdef ENABLE_AVX2
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helperavx2.h"
#else
#include "macroonlyAVX2.h"
#endif
#ifdef DORENAME
#ifdef ENABLE_GNUABI
#include "renameavx2_gnuabi.h"
#else
#include "renameavx2.h"
#endif
#endif
#endif

#ifdef ENABLE_AVX2128
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helperavx2_128.h"
#else
#include "macroonlyAVX2128.h"
#endif
#ifdef DORENAME
#include "renameavx2128.h"
#endif
#endif

#ifdef ENABLE_AVX512F
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helperavx512f.h"
#else
#include "macroonlyAVX512F.h"
#endif
#ifdef DORENAME
#ifdef ENABLE_GNUABI
#include "renameavx512f_gnuabi.h"
#else
#include "renameavx512f.h"
#endif
#endif
#endif

#ifdef ENABLE_AVX512FNOFMA
#define CONFIG 2
#if !defined(SLEEF_GENHEADER)
#include "helperavx512f.h"
#else
#include "macroonlyAVX512FNOFMA.h"
#endif
#ifdef DORENAME
#include "renameavx512fnofma.h"
#endif
#endif

// Arm

#ifdef ENABLE_ADVSIMD
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helperadvsimd.h"
#else
#include "macroonlyADVSIMD.h"
#endif
#ifdef DORENAME
#ifdef ENABLE_GNUABI
#include "renameadvsimd_gnuabi.h"
#else
#include "renameadvsimd.h"
#endif
#endif
#endif

#ifdef ENABLE_ADVSIMDNOFMA
#define CONFIG 2
#if !defined(SLEEF_GENHEADER)
#include "helperadvsimd.h"
#else
#include "macroonlyADVSIMDNOFMA.h"
#endif
#ifdef DORENAME
#include "renameadvsimdnofma.h"
#endif
#endif

#ifdef ENABLE_NEON32
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helperneon32.h"
#endif
#ifdef DORENAME
#include "renameneon32.h"
#endif
#endif

#ifdef ENABLE_NEON32VFPV4
#define CONFIG 4
#if !defined(SLEEF_GENHEADER)
#include "helperneon32.h"
#endif
#ifdef DORENAME
#include "renameneon32vfpv4.h"
#endif
#endif

#ifdef ENABLE_SVE
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helpersve.h"
#else
#include "macroonlySVE.h"
#endif
#ifdef DORENAME
#ifdef ENABLE_GNUABI
#include "renamesve_gnuabi.h"
#else
#include "renamesve.h"
#endif /* ENABLE_GNUABI */
#endif /* DORENAME */
#endif /* ENABLE_SVE */

#ifdef ENABLE_SVENOFMA
#define CONFIG 2
#if !defined(SLEEF_GENHEADER)
#include "helpersve.h"
#else
#include "macroonlySVENOFMA.h"
#endif
#ifdef DORENAME
#include "renamesvenofma.h"
#endif /* DORENAME */
#endif /* ENABLE_SVE */

// IBM

#ifdef ENABLE_VSX
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helperpower_128.h"
#else
#include "macroonlyVSX.h"
#endif
#ifdef DORENAME
#include "renamevsx.h"
#endif
#endif

#ifdef ENABLE_VSXNOFMA
#define CONFIG 2
#if !defined(SLEEF_GENHEADER)
#include "helperpower_128.h"
#else
#include "macroonlyVSXNOFMA.h"
#endif
#ifdef DORENAME
#include "renamevsxnofma.h"
#endif
#endif

#ifdef ENABLE_VSX3
#define CONFIG 3
#if !defined(SLEEF_GENHEADER)
#include "helperpower_128.h"
#else
#include "macroonlyVSX3.h"
#endif
#ifdef DORENAME
#include "renamevsx3.h"
#endif
#endif

#ifdef ENABLE_VSX3NOFMA
#define CONFIG 4
#if !defined(SLEEF_GENHEADER)
#include "helperpower_128.h"
#else
#include "macroonlyVSX3NOFMA.h"
#endif
#ifdef DORENAME
#include "renamevsx3nofma.h"
#endif
#endif

#ifdef ENABLE_VXE
#define CONFIG 140
#if !defined(SLEEF_GENHEADER)
#include "helpers390x_128.h"
#else
#include "macroonlyVXE.h"
#endif
#ifdef DORENAME
#include "renamevxe.h"
#endif
#endif

#ifdef ENABLE_VXENOFMA
#define CONFIG 141
#if !defined(SLEEF_GENHEADER)
#include "helpers390x_128.h"
#else
#include "macroonlyVXENOFMA.h"
#endif
#ifdef DORENAME
#include "renamevxenofma.h"
#endif
#endif

#ifdef ENABLE_VXE2
#define CONFIG 150
#if !defined(SLEEF_GENHEADER)
#include "helpers390x_128.h"
#else
#include "macroonlyVXE2.h"
#endif
#ifdef DORENAME
#include "renamevxe2.h"
#endif
#endif

#ifdef ENABLE_VXE2NOFMA
#define CONFIG 151
#if !defined(SLEEF_GENHEADER)
#include "helpers390x_128.h"
#else
#include "macroonlyVXE2NOFMA.h"
#endif
#ifdef DORENAME
#include "renamevxe2nofma.h"
#endif
#endif

// RISC-V
#ifdef ENABLE_RVVM1
#define CONFIG 1
#define ENABLE_RVV_SP
#if !defined(SLEEF_GENHEADER)
#include "helperrvv.h"
#else
#include "macroonlyRVVM1.h"
#endif
#ifdef DORENAME
#include "renamervvm1.h"
#endif
#endif

#ifdef ENABLE_RVVM1NOFMA
#define CONFIG 2
#define ENABLE_RVV_SP
#if !defined(SLEEF_GENHEADER)
#include "helperrvv.h"
#else
#include "macroonlyRVVM1NOFMA.h"
#endif
#ifdef DORENAME
#include "renamervvm1nofma.h"
#endif
#endif

#ifdef ENABLE_RVVM2
#define CONFIG 1
#define ENABLE_RVV_SP
#if !defined(SLEEF_GENHEADER)
#include "helperrvv.h"
#else
#include "macroonlyRVVM2.h"
#endif
#ifdef DORENAME
#include "renamervvm2.h"
#endif
#endif

#ifdef ENABLE_RVVM2NOFMA
#define CONFIG 2
#define ENABLE_RVV_SP
#if !defined(SLEEF_GENHEADER)
#include "helperrvv.h"
#else
#include "macroonlyRVVM2NOFMA.h"
#endif
#ifdef DORENAME
#include "renamervvm2nofma.h"
#endif
#endif

// Generic

#ifdef ENABLE_VECEXT
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helpervecext.h"
#endif
#ifdef DORENAME
#include "renamevecext.h"
#endif
#endif

#ifdef ENABLE_PUREC
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helperpurec.h"
#endif
#ifdef DORENAME
#include "renamepurec.h"
#endif
#endif

#ifdef ENABLE_PUREC_SCALAR
#define CONFIG 1
#if !defined(SLEEF_GENHEADER)
#include "helperpurec_scalar.h"
#else
#include "macroonlyPUREC_SCALAR.h"
#endif
#ifdef DORENAME
#include "renamepurec_scalar.h"
#endif
#endif

#ifdef ENABLE_PURECFMA_SCALAR
#define CONFIG 2
#if !defined(SLEEF_GENHEADER)
#include "helperpurec_scalar.h"
#else
#include "macroonlyPURECFMA_SCALAR.h"
#endif
#ifdef DORENAME
#include "renamepurecfma_scalar.h"
#endif
#endif

#ifdef SLEEF_ENABLE_CUDA
#define CONFIG 3
#if !defined(SLEEF_GENHEADER)
#include "helperpurec_scalar.h"
#else
#include "macroonlyCUDA.h"
#endif
#ifdef DORENAME
#include "renamecuda.h"
#endif
#endif

//

#define MLA(x, y, z) vmla_vf_vf_vf_vf((x), (y), (z))
#define C2V(c) vcast_vf_f(c)
#include "estrin.h"

//

#include "df.h"

static INLINE CONST VECTOR_CC vopmask visnegzero_vo_vf(vfloat d) {
  return veq_vo_vi2_vi2(vreinterpret_vi2_vf(d), vreinterpret_vi2_vf(vcast_vf_f(-0.0)));
}

static INLINE VECTOR_CC vopmask vnot_vo32_vo32(vopmask x) {
  return vxor_vo_vo_vo(x, veq_vo_vi2_vi2(vcast_vi2_i(0), vcast_vi2_i(0)));
}

static INLINE CONST VECTOR_CC vmask vsignbit_vm_vf(vfloat f) {
  return vand_vm_vm_vm(vreinterpret_vm_vf(f), vreinterpret_vm_vf(vcast_vf_f(-0.0f)));
}

#if !(defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
static INLINE CONST VECTOR_CC vfloat vmulsign_vf_vf_vf(vfloat x, vfloat y) {
  return vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(x), vsignbit_vm_vf(y)));
}

static INLINE CONST VECTOR_CC vfloat vcopysign_vf_vf_vf(vfloat x, vfloat y) {
  return vreinterpret_vf_vm(vxor_vm_vm_vm(vandnot_vm_vm_vm(vreinterpret_vm_vf(vcast_vf_f(-0.0f)), vreinterpret_vm_vf(x)),
                                          vand_vm_vm_vm   (vreinterpret_vm_vf(vcast_vf_f(-0.0f)), vreinterpret_vm_vf(y))));
}

static INLINE CONST VECTOR_CC vfloat vsign_vf_vf(vfloat f) {
  return vreinterpret_vf_vm(vor_vm_vm_vm(vreinterpret_vm_vf(vcast_vf_f(1.0f)), vand_vm_vm_vm(vreinterpret_vm_vf(vcast_vf_f(-0.0f)), vreinterpret_vm_vf(f))));
}
#endif

static INLINE CONST VECTOR_CC vopmask vsignbit_vo_vf(vfloat d) {
  return veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vreinterpret_vi2_vf(d), vcast_vi2_i(0x80000000)), vcast_vi2_i(0x80000000));
}

static INLINE CONST VECTOR_CC vint2 vsel_vi2_vf_vf_vi2_vi2(vfloat f0, vfloat f1, vint2 x, vint2 y) {
  return vsel_vi2_vo_vi2_vi2(vlt_vo_vf_vf(f0, f1), x, y);
}

static INLINE CONST VECTOR_CC vint2 vsel_vi2_vf_vi2(vfloat d, vint2 x) {
  return vand_vi2_vo_vi2(vsignbit_vo_vf(d), x);
}

static INLINE CONST VECTOR_CC vopmask visint_vo_vf(vfloat y) { return veq_vo_vf_vf(vtruncate_vf_vf(y), y); }

static INLINE CONST VECTOR_CC vopmask visnumber_vo_vf(vfloat x) { return vnot_vo32_vo32(vor_vo_vo_vo(visinf_vo_vf(x), visnan_vo_vf(x))); }

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
static INLINE CONST VECTOR_CC vint2 vilogbk_vi2_vf(vfloat d) {
  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(5.421010862427522E-20f));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(vcast_vf_f(1.8446744073709552E19f), d), d);
  vint2 q = vand_vi2_vi2_vi2(vsrl_vi2_vi2_i(vreinterpret_vi2_vf(d), 23), vcast_vi2_i(0xff));
  q = vsub_vi2_vi2_vi2(q, vsel_vi2_vo_vi2_vi2(o, vcast_vi2_i(64 + 0x7f), vcast_vi2_i(0x7f)));
  return q;
}

static INLINE CONST VECTOR_CC vint2 vilogb2k_vi2_vf(vfloat d) {
  vint2 q = vreinterpret_vi2_vf(d);
  q = vsrl_vi2_vi2_i(q, 23);
  q = vand_vi2_vi2_vi2(q, vcast_vi2_i(0xff));
  q = vsub_vi2_vi2_vi2(q, vcast_vi2_i(0x7f));
  return q;
}
#endif

//

EXPORT CONST VECTOR_CC vint2 xilogbf(vfloat d) {
  vint2 e = vilogbk_vi2_vf(vabs_vf_vf(d));
  e = vsel_vi2_vo_vi2_vi2(veq_vo_vf_vf(d, vcast_vf_f(0.0f)), vcast_vi2_i(SLEEF_FP_ILOGB0), e);
  e = vsel_vi2_vo_vi2_vi2(visnan_vo_vf(d), vcast_vi2_i(SLEEF_FP_ILOGBNAN), e);
  e = vsel_vi2_vo_vi2_vi2(visinf_vo_vf(d), vcast_vi2_i(SLEEF_INT_MAX), e);
  return e;
}

static INLINE CONST VECTOR_CC vfloat vpow2i_vf_vi2(vint2 q) {
  return vreinterpret_vf_vi2(vsll_vi2_vi2_i(vadd_vi2_vi2_vi2(q, vcast_vi2_i(0x7f)), 23));
}

static INLINE CONST VECTOR_CC vfloat vldexp_vf_vf_vi2(vfloat x, vint2 q) {
  vfloat u;
  vint2 m = vsra_vi2_vi2_i(q, 31);
  m = vsll_vi2_vi2_i(vsub_vi2_vi2_vi2(vsra_vi2_vi2_i(vadd_vi2_vi2_vi2(m, q), 6), m), 4);
  q = vsub_vi2_vi2_vi2(q, vsll_vi2_vi2_i(m, 2));
  m = vadd_vi2_vi2_vi2(m, vcast_vi2_i(0x7f));
  m = vand_vi2_vi2_vi2(vgt_vi2_vi2_vi2(m, vcast_vi2_i(0)), m);
  vint2 n = vgt_vi2_vi2_vi2(m, vcast_vi2_i(0xff));
  m = vor_vi2_vi2_vi2(vandnot_vi2_vi2_vi2(n, m), vand_vi2_vi2_vi2(n, vcast_vi2_i(0xff)));
  u = vreinterpret_vf_vi2(vsll_vi2_vi2_i(m, 23));
  x = vmul_vf_vf_vf(vmul_vf_vf_vf(vmul_vf_vf_vf(vmul_vf_vf_vf(x, u), u), u), u);
  u = vreinterpret_vf_vi2(vsll_vi2_vi2_i(vadd_vi2_vi2_vi2(q, vcast_vi2_i(0x7f)), 23));
  return vmul_vf_vf_vf(x, u);
}

static INLINE CONST VECTOR_CC vfloat vldexp2_vf_vf_vi2(vfloat d, vint2 e) {
  return vmul_vf_vf_vf(vmul_vf_vf_vf(d, vpow2i_vf_vi2(vsra_vi2_vi2_i(e, 1))), vpow2i_vf_vi2(vsub_vi2_vi2_vi2(e, vsra_vi2_vi2_i(e, 1))));
}

static INLINE CONST VECTOR_CC vfloat vldexp3_vf_vf_vi2(vfloat d, vint2 q) {
  return vreinterpret_vf_vi2(vadd_vi2_vi2_vi2(vreinterpret_vi2_vf(d), vsll_vi2_vi2_i(q, 23)));
}

EXPORT CONST VECTOR_CC vfloat xldexpf(vfloat x, vint2 q) { return vldexp_vf_vf_vi2(x, q); }

#if !(defined(ENABLE_SVE) || defined(ENABLE_SVENOFMA) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
typedef struct {
  vfloat d;
  vint2 i;
} fi_t;

static vfloat figetd_vf_di(fi_t d) { return d.d; }
static vint2 figeti_vi2_di(fi_t d) { return d.i; }
static fi_t fisetdi_fi_vf_vi2(vfloat d, vint2 i) {
  fi_t r = { d, i };
  return r;
}

typedef struct {
  vfloat2 df;
  vint2 i;
} dfi_t;

static vfloat2 dfigetdf_vf2_dfi(dfi_t d) { return d.df; }
static vint2 dfigeti_vi2_dfi(dfi_t d) { return d.i; }
static dfi_t dfisetdfi_dfi_vf2_vi2(vfloat2 v, vint2 i) {
  dfi_t r = { v, i };
  return r;
}
static dfi_t dfisetdf_dfi_dfi_vf2(dfi_t dfi, vfloat2 v) {
  dfi.df = v;
  return dfi;
}
#endif

#if !(defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
static INLINE CONST VECTOR_CC vfloat vorsign_vf_vf_vf(vfloat x, vfloat y) {
  return vreinterpret_vf_vm(vor_vm_vm_vm(vreinterpret_vm_vf(x), vsignbit_vm_vf(y)));
}
#endif

static INLINE CONST fi_t rempisubf(vfloat x) {
#ifdef FULL_FP_ROUNDING
  vfloat y = vrint_vf_vf(vmul_vf_vf_vf(x, vcast_vf_f(4)));
  vint2 vi = vtruncate_vi2_vf(vsub_vf_vf_vf(y, vmul_vf_vf_vf(vrint_vf_vf(x), vcast_vf_f(4))));
  return fisetdi_fi_vf_vi2(vsub_vf_vf_vf(x, vmul_vf_vf_vf(y, vcast_vf_f(0.25))), vi);
#else
  vfloat c = vmulsign_vf_vf_vf(vcast_vf_f(1 << 23), x);
  vfloat rint4x = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vabs_vf_vf(vmul_vf_vf_vf(vcast_vf_f(4), x)), vcast_vf_f(1 << 23)),
                                   vmul_vf_vf_vf(vcast_vf_f(4), x),
                                   vorsign_vf_vf_vf(vsub_vf_vf_vf(vmla_vf_vf_vf_vf(vcast_vf_f(4), x, c), c), x));
  vfloat rintx  = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(1 << 23)),
                                   x, vorsign_vf_vf_vf(vsub_vf_vf_vf(vadd_vf_vf_vf(x, c), c), x));
  return fisetdi_fi_vf_vi2(vmla_vf_vf_vf_vf(vcast_vf_f(-0.25), rint4x, x),
                           vtruncate_vi2_vf(vmla_vf_vf_vf_vf(vcast_vf_f(-4), rintx, rint4x)));
#endif
}

static INLINE CONST dfi_t rempif(vfloat a) {
  vfloat2 x, y;
  vint2 ex = vilogb2k_vi2_vf(a);
#if defined(ENABLE_AVX512F) || defined(ENABLE_AVX512FNOFMA)
  ex = vandnot_vi2_vi2_vi2(vsra_vi2_vi2_i(ex, 31), ex);
  ex = vand_vi2_vi2_vi2(ex, vcast_vi2_i(127));
#endif
  ex = vsub_vi2_vi2_vi2(ex, vcast_vi2_i(25));
  vint2 q = vand_vi2_vo_vi2(vgt_vo_vi2_vi2(ex, vcast_vi2_i(90-25)), vcast_vi2_i(-64));
  a = vldexp3_vf_vf_vi2(a, q);
  ex = vandnot_vi2_vi2_vi2(vsra_vi2_vi2_i(ex, 31), ex);
  ex = vsll_vi2_vi2_i(ex, 2);
  x = dfmul_vf2_vf_vf(a, vgather_vf_p_vi2(Sleef_rempitabsp, ex));
  fi_t di = rempisubf(vf2getx_vf_vf2(x));
  q = figeti_vi2_di(di);
  x = vf2setx_vf2_vf2_vf(x, figetd_vf_di(di));
  x = dfnormalize_vf2_vf2(x);
  y = dfmul_vf2_vf_vf(a, vgather_vf_p_vi2(Sleef_rempitabsp+1, ex));
  x = dfadd2_vf2_vf2_vf2(x, y);
  di = rempisubf(vf2getx_vf_vf2(x));
  q = vadd_vi2_vi2_vi2(q, figeti_vi2_di(di));
  x = vf2setx_vf2_vf2_vf(x, figetd_vf_di(di));
  x = dfnormalize_vf2_vf2(x);
  y = vcast_vf2_vf_vf(vgather_vf_p_vi2(Sleef_rempitabsp+2, ex), vgather_vf_p_vi2(Sleef_rempitabsp+3, ex));
  y = dfmul_vf2_vf2_vf(y, a);
  x = dfadd2_vf2_vf2_vf2(x, y);
  x = dfnormalize_vf2_vf2(x);
  x = dfmul_vf2_vf2_vf2(x, vcast_vf2_f_f(3.1415927410125732422f*2, -8.7422776573475857731e-08f*2));
  x = vsel_vf2_vo_vf2_vf2(vlt_vo_vf_vf(vabs_vf_vf(a), vcast_vf_f(0.7f)), vcast_vf2_vf_vf(a, vcast_vf_f(0)), x);
  return dfisetdfi_dfi_vf2_vi2(x, q);
}

EXPORT CONST VECTOR_CC vfloat xsinf(vfloat d) {
#if !defined(DETERMINISTIC)
  vint2 q;
  vfloat u, s, r = d;

  if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f))))) {
    q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_1_PI)));
    u = vcast_vf_vi2(q);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_B2f), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_C2f), d);
  } else if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAXf))))) {
    q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_1_PI)));
    u = vcast_vf_vi2(q);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Af), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Bf), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Cf), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Df), d);
  } else {
    dfi_t dfi = rempif(d);
    q = vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(3));
    q = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, q), vsel_vi2_vo_vi2_vi2(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vi2_i(2), vcast_vi2_i(1)));
    q = vsra_vi2_vi2_i(q, 2);
    vopmask o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(1)), vcast_vi2_i(1));
    vfloat2 x = vcast_vf2_vf_vf(vmulsign_vf_vf_vf(vcast_vf_f(3.1415927410125732422f*-0.5), vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi))),
                                vmulsign_vf_vf_vf(vcast_vf_f(-8.7422776573475857731e-08f*-0.5), vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi))));
    x = dfadd2_vf2_vf2_vf2(dfigetdf_vf2_dfi(dfi), x);
    dfi = dfisetdf_dfi_dfi_vf2(dfi, vsel_vf2_vo_vf2_vf2(o, x, dfigetdf_vf2_dfi(dfi)));
    d = vadd_vf_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vf2gety_vf_vf2(dfigetdf_vf2_dfi(dfi)));

    d = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(r), visnan_vo_vf(r)), vreinterpret_vm_vf(d)));
  }

  s = vmul_vf_vf_vf(d, d);

  d = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1)), vreinterpret_vm_vf(vcast_vf_f(-0.0f))), vreinterpret_vm_vf(d)));

  u = vcast_vf_f(2.6083159809786593541503e-06f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.0001981069071916863322258f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00833307858556509017944336f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.166666597127914428710938f));

  u = vadd_vf_vf_vf(vmul_vf_vf_vf(s, vmul_vf_vf_vf(u, d)), d);

  u = vsel_vf_vo_vf_vf(visnegzero_vo_vf(r), r, u);

  return u;

#else // #if !defined(DETERMINISTIC)

  vint2 q;
  vfloat u, s, r = d;

  q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_1_PI)));
  u = vcast_vf_vi2(q);
  d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f), d);
  d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_B2f), d);
  d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_C2f), d);
  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(r), vcast_vf_f(TRIGRANGEMAX2f));

  if (!LIKELY(vtestallones_i_vo32(g))) {
    s = vcast_vf_vi2(q);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Af), r);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Bf), u);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Cf), u);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Df), u);

    d = vsel_vf_vo_vf_vf(g, d, u);
    g = vlt_vo_vf_vf(vabs_vf_vf(r), vcast_vf_f(TRIGRANGEMAXf));

    if (!LIKELY(vtestallones_i_vo32(g))) {
      dfi_t dfi = rempif(r);
      vint2 q2 = vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(3));
      q2 = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q2, q2), vsel_vi2_vo_vi2_vi2(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vi2_i(2), vcast_vi2_i(1)));
      q2 = vsra_vi2_vi2_i(q2, 2);
      vopmask o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(1)), vcast_vi2_i(1));
      vfloat2 x = vcast_vf2_vf_vf(vmulsign_vf_vf_vf(vcast_vf_f(3.1415927410125732422f*-0.5), vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi))),
                                  vmulsign_vf_vf_vf(vcast_vf_f(-8.7422776573475857731e-08f*-0.5), vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi))));
      x = dfadd2_vf2_vf2_vf2(dfigetdf_vf2_dfi(dfi), x);
      dfi = dfisetdf_dfi_dfi_vf2(dfi, vsel_vf2_vo_vf2_vf2(o, x, dfigetdf_vf2_dfi(dfi)));
      u = vadd_vf_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vf2gety_vf_vf2(dfigetdf_vf2_dfi(dfi)));

      u = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(r), visnan_vo_vf(r)), vreinterpret_vm_vf(u)));

      q = vsel_vi2_vo_vi2_vi2(g, q, q2);
      d = vsel_vf_vo_vf_vf(g, d, u);
    }
  }

  s = vmul_vf_vf_vf(d, d);

  d = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1)), vreinterpret_vm_vf(vcast_vf_f(-0.0f))), vreinterpret_vm_vf(d)));

  u = vcast_vf_f(2.6083159809786593541503e-06f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.0001981069071916863322258f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00833307858556509017944336f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.166666597127914428710938f));

  u = vadd_vf_vf_vf(vmul_vf_vf_vf(s, vmul_vf_vf_vf(u, d)), d);

  u = vsel_vf_vo_vf_vf(visnegzero_vo_vf(r), r, u);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

EXPORT CONST VECTOR_CC vfloat xcosf(vfloat d) {
#if !defined(DETERMINISTIC)
  vint2 q;
  vfloat u, s, r = d;

  if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f))))) {
    q = vrint_vi2_vf(vsub_vf_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_1_PI)), vcast_vf_f(0.5f)));
    q = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, q), vcast_vi2_i(1));

    u = vcast_vf_vi2(q);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f), d);
  } else if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAXf))))) {
    q = vrint_vi2_vf(vsub_vf_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_1_PI)), vcast_vf_f(0.5f)));
    q = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, q), vcast_vi2_i(1));

    u = vcast_vf_vi2(q);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Af*0.5f), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Bf*0.5f), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Cf*0.5f), d);
    d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Df*0.5f), d);
  } else {
    dfi_t dfi = rempif(d);
    q = vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(3));
    q = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, q), vsel_vi2_vo_vi2_vi2(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vi2_i(8), vcast_vi2_i(7)));
    q = vsra_vi2_vi2_i(q, 1);
    vopmask o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(1)), vcast_vi2_i(0));
    vfloat y = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vf_f(0), vcast_vf_f(-1));
    vfloat2 x = vcast_vf2_vf_vf(vmulsign_vf_vf_vf(vcast_vf_f(3.1415927410125732422f*-0.5), y),
                                vmulsign_vf_vf_vf(vcast_vf_f(-8.7422776573475857731e-08f*-0.5), y));
    x = dfadd2_vf2_vf2_vf2(dfigetdf_vf2_dfi(dfi), x);
    dfi = dfisetdf_dfi_dfi_vf2(dfi, vsel_vf2_vo_vf2_vf2(o, x, dfigetdf_vf2_dfi(dfi)));
    d = vadd_vf_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vf2gety_vf_vf2(dfigetdf_vf2_dfi(dfi)));

    d = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(r), visnan_vo_vf(r)), vreinterpret_vm_vf(d)));
  }

  s = vmul_vf_vf_vf(d, d);

  d = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(0)), vreinterpret_vm_vf(vcast_vf_f(-0.0f))), vreinterpret_vm_vf(d)));

  u = vcast_vf_f(2.6083159809786593541503e-06f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.0001981069071916863322258f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00833307858556509017944336f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.166666597127914428710938f));

  u = vadd_vf_vf_vf(vmul_vf_vf_vf(s, vmul_vf_vf_vf(u, d)), d);

  return u;

#else // #if !defined(DETERMINISTIC)

  vint2 q;
  vfloat u, s, r = d;

  q = vrint_vi2_vf(vsub_vf_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_1_PI)), vcast_vf_f(0.5f)));
  q = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, q), vcast_vi2_i(1));
  u = vcast_vf_vi2(q);
  d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), d);
  d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f), d);
  d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f), d);
  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(r), vcast_vf_f(TRIGRANGEMAX2f));

  if (!LIKELY(vtestallones_i_vo32(g))) {
    s = vcast_vf_vi2(q);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Af*0.5f), r);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Bf*0.5f), u);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Cf*0.5f), u);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Df*0.5f), u);

    d = vsel_vf_vo_vf_vf(g, d, u);
    g = vlt_vo_vf_vf(vabs_vf_vf(r), vcast_vf_f(TRIGRANGEMAXf));

    if (!LIKELY(vtestallones_i_vo32(g))) {
      dfi_t dfi = rempif(r);
      vint2 q2 = vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(3));
      q2 = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q2, q2), vsel_vi2_vo_vi2_vi2(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vi2_i(8), vcast_vi2_i(7)));
      q2 = vsra_vi2_vi2_i(q2, 1);
      vopmask o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(1)), vcast_vi2_i(0));
      vfloat y = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vf_f(0), vcast_vf_f(-1));
      vfloat2 x = vcast_vf2_vf_vf(vmulsign_vf_vf_vf(vcast_vf_f(3.1415927410125732422f*-0.5), y),
                                  vmulsign_vf_vf_vf(vcast_vf_f(-8.7422776573475857731e-08f*-0.5), y));
      x = dfadd2_vf2_vf2_vf2(dfigetdf_vf2_dfi(dfi), x);
      dfi = dfisetdf_dfi_dfi_vf2(dfi, vsel_vf2_vo_vf2_vf2(o, x, dfigetdf_vf2_dfi(dfi)));
      u = vadd_vf_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vf2gety_vf_vf2(dfigetdf_vf2_dfi(dfi)));

      u = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(r), visnan_vo_vf(r)), vreinterpret_vm_vf(u)));

      q = vsel_vi2_vo_vi2_vi2(g, q, q2);
      d = vsel_vf_vo_vf_vf(g, d, u);
    }
  }

  s = vmul_vf_vf_vf(d, d);

  d = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(0)), vreinterpret_vm_vf(vcast_vf_f(-0.0f))), vreinterpret_vm_vf(d)));

  u = vcast_vf_f(2.6083159809786593541503e-06f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.0001981069071916863322258f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00833307858556509017944336f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.166666597127914428710938f));

  u = vadd_vf_vf_vf(vmul_vf_vf_vf(s, vmul_vf_vf_vf(u, d)), d);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

EXPORT CONST VECTOR_CC vfloat xtanf(vfloat d) {
#if !defined(DETERMINISTIC)
  vint2 q;
  vopmask o;
  vfloat u, s, x;

  x = d;

  if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f*0.5f))))) {
    q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)(2 * M_1_PI))));
    u = vcast_vf_vi2(q);
    x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), x);
    x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f), x);
    x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f), x);
  } else if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAXf))))) {
    q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)(2 * M_1_PI))));
    u = vcast_vf_vi2(q);
    x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Af*0.5f), x);
    x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Bf*0.5f), x);
    x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Cf*0.5f), x);
    x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Df*0.5f), x);
  } else {
    dfi_t dfi = rempif(d);
    q = dfigeti_vi2_dfi(dfi);
    x = vadd_vf_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vf2gety_vf_vf2(dfigetdf_vf2_dfi(dfi)));
    x = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(x)));
    x = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), d, x);
  }

  s = vmul_vf_vf_vf(x, x);

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1));
  x = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0f))), vreinterpret_vm_vf(x)));

#if defined(ENABLE_NEON32)
  u = vcast_vf_f(0.00927245803177356719970703f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00331984995864331722259521f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.0242998078465461730957031f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.0534495301544666290283203f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.133383005857467651367188f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.333331853151321411132812f));
#else
  vfloat s2 = vmul_vf_vf_vf(s, s), s4 = vmul_vf_vf_vf(s2, s2);
  u = POLY6(s, s2, s4,
            0.00927245803177356719970703f,
            0.00331984995864331722259521f,
            0.0242998078465461730957031f,
            0.0534495301544666290283203f,
            0.133383005857467651367188f,
            0.333331853151321411132812f);
#endif

  u = vmla_vf_vf_vf_vf(s, vmul_vf_vf_vf(u, x), x);

  u = vsel_vf_vo_vf_vf(o, vrec_vf_vf(u), u);

  return u;

#else // #if !defined(DETERMINISTIC)

  vint2 q;
  vopmask o;
  vfloat u, s, x;

  q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)(2 * M_1_PI))));
  u = vcast_vf_vi2(q);
  x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), d);
  x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f), x);
  x = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f), x);
  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f*0.5f));

  if (!LIKELY(vtestallones_i_vo32(g))) {
    vint2 q2 = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)(2 * M_1_PI))));
    s = vcast_vf_vi2(q);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Af*0.5f), d);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Bf*0.5f), u);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Cf*0.5f), u);
    u = vmla_vf_vf_vf_vf(s, vcast_vf_f(-PI_Df*0.5f), u);

    q = vsel_vi2_vo_vi2_vi2(g, q, q2);
    x = vsel_vf_vo_vf_vf(g, x, u);
    g = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAXf));

    if (!LIKELY(vtestallones_i_vo32(g))) {
      dfi_t dfi = rempif(d);
      u = vadd_vf_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vf2gety_vf_vf2(dfigetdf_vf2_dfi(dfi)));
      u = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(u)));
      u = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), d, u);
      q = vsel_vi2_vo_vi2_vi2(g, q, dfigeti_vi2_dfi(dfi));
      x = vsel_vf_vo_vf_vf(g, x, u);
    }
  }

  s = vmul_vf_vf_vf(x, x);

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1));
  x = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0f))), vreinterpret_vm_vf(x)));

#if defined(ENABLE_NEON32)
  u = vcast_vf_f(0.00927245803177356719970703f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00331984995864331722259521f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.0242998078465461730957031f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.0534495301544666290283203f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.133383005857467651367188f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.333331853151321411132812f));
#else
  vfloat s2 = vmul_vf_vf_vf(s, s), s4 = vmul_vf_vf_vf(s2, s2);
  u = POLY6(s, s2, s4,
            0.00927245803177356719970703f,
            0.00331984995864331722259521f,
            0.0242998078465461730957031f,
            0.0534495301544666290283203f,
            0.133383005857467651367188f,
            0.333331853151321411132812f);
#endif

  u = vmla_vf_vf_vf_vf(s, vmul_vf_vf_vf(u, x), x);

  u = vsel_vf_vo_vf_vf(o, vrec_vf_vf(u), u);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

EXPORT CONST VECTOR_CC vfloat xsinf_u1(vfloat d) {
#if !defined(DETERMINISTIC)
  vint2 q;
  vfloat u, v;
  vfloat2 s, t, x;

  if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f))))) {
    u = vrint_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(M_1_PI)));
    q = vrint_vi2_vf(u);
    v = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f), d);
    s = dfadd2_vf2_vf_vf(v, vmul_vf_vf_vf(u, vcast_vf_f(-PI_B2f)));
    s = dfadd_vf2_vf2_vf(s, vmul_vf_vf_vf(u, vcast_vf_f(-PI_C2f)));
  } else {
    dfi_t dfi = rempif(d);
    q = vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(3));
    q = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, q), vsel_vi2_vo_vi2_vi2(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vi2_i(2), vcast_vi2_i(1)));
    q = vsra_vi2_vi2_i(q, 2);
    vopmask o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(1)), vcast_vi2_i(1));
    vfloat2 x = vcast_vf2_vf_vf(vmulsign_vf_vf_vf(vcast_vf_f(3.1415927410125732422f*-0.5), vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi))),
                                vmulsign_vf_vf_vf(vcast_vf_f(-8.7422776573475857731e-08f*-0.5), vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi))));
    x = dfadd2_vf2_vf2_vf2(dfigetdf_vf2_dfi(dfi), x);
    dfi = dfisetdf_dfi_dfi_vf2(dfi, vsel_vf2_vo_vf2_vf2(o, x, dfigetdf_vf2_dfi(dfi)));
    s = dfnormalize_vf2_vf2(dfigetdf_vf2_dfi(dfi));

#if !defined(_MSC_VER)
    s = vf2setx_vf2_vf2_vf(s, vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(vf2getx_vf_vf2(s)))));
#else
    s.x = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(s.x)));
#endif
  }

  t = s;
  s = dfsqu_vf2_vf2(s);

  u = vcast_vf_f(2.6083159809786593541503e-06f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.0001981069071916863322258f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.00833307858556509017944336f));

  x = dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf2_vf2(dfadd_vf2_vf_vf(vcast_vf_f(-0.166666597127914428710938f), vmul_vf_vf_vf(u, vf2getx_vf_vf2(s))), s));

  u = dfmul_vf_vf2_vf2(t, x);

  u = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1)), vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(u)));

  u = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), d, u);

  return u;

#else // #if !defined(DETERMINISTIC)

  vint2 q;
  vfloat u, v;
  vfloat2 s, t, x;

  u = vrint_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(M_1_PI)));
  q = vrint_vi2_vf(u);
  v = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f), d);
  s = dfadd2_vf2_vf_vf(v, vmul_vf_vf_vf(u, vcast_vf_f(-PI_B2f)));
  s = dfadd_vf2_vf2_vf(s, vmul_vf_vf_vf(u, vcast_vf_f(-PI_C2f)));
  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f));

  if (!LIKELY(vtestallones_i_vo32(g))) {
    dfi_t dfi = rempif(d);
    vint2 q2 = vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(3));
    q2 = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q2, q2), vsel_vi2_vo_vi2_vi2(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vi2_i(2), vcast_vi2_i(1)));
    q2 = vsra_vi2_vi2_i(q2, 2);
    vopmask o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(1)), vcast_vi2_i(1));
    vfloat2 x = vcast_vf2_vf_vf(vmulsign_vf_vf_vf(vcast_vf_f(3.1415927410125732422f*-0.5), vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi))),
                                vmulsign_vf_vf_vf(vcast_vf_f(-8.7422776573475857731e-08f*-0.5), vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi))));
    x = dfadd2_vf2_vf2_vf2(dfigetdf_vf2_dfi(dfi), x);
    dfi = dfisetdf_dfi_dfi_vf2(dfi, vsel_vf2_vo_vf2_vf2(o, x, dfigetdf_vf2_dfi(dfi)));
    t = dfnormalize_vf2_vf2(dfigetdf_vf2_dfi(dfi));

    t = vf2setx_vf2_vf2_vf(t, vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(vf2getx_vf_vf2(t)))));

    q = vsel_vi2_vo_vi2_vi2(g, q, q2);
    s = vsel_vf2_vo_vf2_vf2(g, s, t);
  }

  t = s;
  s = dfsqu_vf2_vf2(s);

  u = vcast_vf_f(2.6083159809786593541503e-06f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.0001981069071916863322258f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.00833307858556509017944336f));

  x = dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf2_vf2(dfadd_vf2_vf_vf(vcast_vf_f(-0.166666597127914428710938f), vmul_vf_vf_vf(u, vf2getx_vf_vf2(s))), s));

  u = dfmul_vf_vf2_vf2(t, x);

  u = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1)), vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(u)));

  u = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), d, u);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

EXPORT CONST VECTOR_CC vfloat xcosf_u1(vfloat d) {
#if !defined(DETERMINISTIC)
  vint2 q;
  vfloat u;
  vfloat2 s, t, x;

  if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f))))) {
    vfloat dq = vmla_vf_vf_vf_vf(vrint_vf_vf(vmla_vf_vf_vf_vf(d, vcast_vf_f(M_1_PI), vcast_vf_f(-0.5f))),
                                 vcast_vf_f(2), vcast_vf_f(1));
    q = vrint_vi2_vf(dq);
    s = dfadd2_vf2_vf_vf (d, vmul_vf_vf_vf(dq, vcast_vf_f(-PI_A2f*0.5f)));
    s = dfadd2_vf2_vf2_vf(s, vmul_vf_vf_vf(dq, vcast_vf_f(-PI_B2f*0.5f)));
    s = dfadd2_vf2_vf2_vf(s, vmul_vf_vf_vf(dq, vcast_vf_f(-PI_C2f*0.5f)));
  } else {
    dfi_t dfi = rempif(d);
    q = vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(3));
    q = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, q), vsel_vi2_vo_vi2_vi2(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vi2_i(8), vcast_vi2_i(7)));
    q = vsra_vi2_vi2_i(q, 1);
    vopmask o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(1)), vcast_vi2_i(0));
    vfloat y = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vf_f(0), vcast_vf_f(-1));
    vfloat2 x = vcast_vf2_vf_vf(vmulsign_vf_vf_vf(vcast_vf_f(3.1415927410125732422f*-0.5), y),
                                vmulsign_vf_vf_vf(vcast_vf_f(-8.7422776573475857731e-08f*-0.5), y));
    x = dfadd2_vf2_vf2_vf2(dfigetdf_vf2_dfi(dfi), x);
    dfi = dfisetdf_dfi_dfi_vf2(dfi, vsel_vf2_vo_vf2_vf2(o, x, dfigetdf_vf2_dfi(dfi)));
    s = dfnormalize_vf2_vf2(dfigetdf_vf2_dfi(dfi));

#if !defined(_MSC_VER)
    s = vf2setx_vf2_vf2_vf(s, vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(vf2getx_vf_vf2(s)))));
#else
    s.x = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(s.x)));
#endif
  }

  t = s;
  s = dfsqu_vf2_vf2(s);

  u = vcast_vf_f(2.6083159809786593541503e-06f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.0001981069071916863322258f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.00833307858556509017944336f));

  x = dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf2_vf2(dfadd_vf2_vf_vf(vcast_vf_f(-0.166666597127914428710938f), vmul_vf_vf_vf(u, vf2getx_vf_vf2(s))), s));

  u = dfmul_vf_vf2_vf2(t, x);

  u = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(0)), vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(u)));

  return u;

#else // #if !defined(DETERMINISTIC)

  vint2 q;
  vfloat u;
  vfloat2 s, t, x;

  vfloat dq = vmla_vf_vf_vf_vf(vrint_vf_vf(vmla_vf_vf_vf_vf(d, vcast_vf_f(M_1_PI), vcast_vf_f(-0.5f))),
                               vcast_vf_f(2), vcast_vf_f(1));
  q = vrint_vi2_vf(dq);
  s = dfadd2_vf2_vf_vf (d, vmul_vf_vf_vf(dq, vcast_vf_f(-PI_A2f*0.5f)));
  s = dfadd2_vf2_vf2_vf(s, vmul_vf_vf_vf(dq, vcast_vf_f(-PI_B2f*0.5f)));
  s = dfadd2_vf2_vf2_vf(s, vmul_vf_vf_vf(dq, vcast_vf_f(-PI_C2f*0.5f)));
  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f));

  if (!LIKELY(vtestallones_i_vo32(g))) {
    dfi_t dfi = rempif(d);
    vint2 q2 = vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(3));
    q2 = vadd_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q2, q2), vsel_vi2_vo_vi2_vi2(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vi2_i(8), vcast_vi2_i(7)));
    q2 = vsra_vi2_vi2_i(q2, 1);
    vopmask o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(dfigeti_vi2_dfi(dfi), vcast_vi2_i(1)), vcast_vi2_i(0));
    vfloat y = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vcast_vf_f(0)), vcast_vf_f(0), vcast_vf_f(-1));
    vfloat2 x = vcast_vf2_vf_vf(vmulsign_vf_vf_vf(vcast_vf_f(3.1415927410125732422f*-0.5), y),
                                vmulsign_vf_vf_vf(vcast_vf_f(-8.7422776573475857731e-08f*-0.5), y));
    x = dfadd2_vf2_vf2_vf2(dfigetdf_vf2_dfi(dfi), x);
    dfi = dfisetdf_dfi_dfi_vf2(dfi, vsel_vf2_vo_vf2_vf2(o, x, dfigetdf_vf2_dfi(dfi)));
    t = dfnormalize_vf2_vf2(dfigetdf_vf2_dfi(dfi));

    t = vf2setx_vf2_vf2_vf(t, vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(vf2getx_vf_vf2(t)))));

    q = vsel_vi2_vo_vi2_vi2(g, q, q2);
    s = vsel_vf2_vo_vf2_vf2(g, s, t);
  }

  t = s;
  s = dfsqu_vf2_vf2(s);

  u = vcast_vf_f(2.6083159809786593541503e-06f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.0001981069071916863322258f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.00833307858556509017944336f));

  x = dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf2_vf2(dfadd_vf2_vf_vf(vcast_vf_f(-0.166666597127914428710938f), vmul_vf_vf_vf(u, vf2getx_vf_vf2(s))), s));

  u = dfmul_vf_vf2_vf2(t, x);

  u = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(0)), vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(u)));

  return u;
#endif // #if !defined(DETERMINISTIC)
}

EXPORT CONST VECTOR_CC vfloat xfastsinf_u3500(vfloat d) {
  vint2 q;
  vfloat u, s, t = d;

  s = vmul_vf_vf_vf(d, vcast_vf_f((float)M_1_PI));
  u = vrint_vf_vf(s);
  q = vrint_vi2_vf(s);
  d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-(float)M_PI), d);

  s = vmul_vf_vf_vf(d, d);

  u = vcast_vf_f(-0.1881748176e-3);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.8323502727e-2));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.1666651368e+0));
  u = vmla_vf_vf_vf_vf(vmul_vf_vf_vf(s, d), u, d);

  u = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1)), vreinterpret_vm_vf(vcast_vf_f(-0.0f))), vreinterpret_vm_vf(u)));

  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(t), vcast_vf_f(30.0f));
  if (!LIKELY(vtestallones_i_vo32(g))) return vsel_vf_vo_vf_vf(g, u, xsinf(t));

  return u;
}

EXPORT CONST VECTOR_CC vfloat xfastcosf_u3500(vfloat d) {
  vint2 q;
  vfloat u, s, t = d;

  s = vmla_vf_vf_vf_vf(d, vcast_vf_f((float)M_1_PI), vcast_vf_f(-0.5f));
  u = vrint_vf_vf(s);
  q = vrint_vi2_vf(s);
  d = vmla_vf_vf_vf_vf(u, vcast_vf_f(-(float)M_PI), vsub_vf_vf_vf(d, vcast_vf_f((float)M_PI * 0.5f)));

  s = vmul_vf_vf_vf(d, d);

  u = vcast_vf_f(-0.1881748176e-3);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.8323502727e-2));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.1666651368e+0));
  u = vmla_vf_vf_vf_vf(vmul_vf_vf_vf(s, d), u, d);

  u = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(0)), vreinterpret_vm_vf(vcast_vf_f(-0.0f))), vreinterpret_vm_vf(u)));

  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(t), vcast_vf_f(30.0f));
  if (!LIKELY(vtestallones_i_vo32(g))) return vsel_vf_vo_vf_vf(g, u, xcosf(t));

  return u;
}

#ifdef ENABLE_GNUABI
#define TYPE2_FUNCATR static INLINE CONST
#define TYPE6_FUNCATR static INLINE CONST
#define SQRTFU05_FUNCATR static INLINE CONST
#define XSINCOSF sincosfk
#define XSINCOSF_U1 sincosfk_u1
#define XSINCOSPIF_U05 sincospifk_u05
#define XSINCOSPIF_U35 sincospifk_u35
#define XMODFF modffk
#else
#define TYPE2_FUNCATR EXPORT CONST
#define TYPE6_FUNCATR EXPORT
#define SQRTFU05_FUNCATR EXPORT
#define XSINCOSF xsincosf
#define XSINCOSF_U1 xsincosf_u1
#define XSINCOSPIF_U05 xsincospif_u05
#define XSINCOSPIF_U35 xsincospif_u35
#define XMODFF xmodff
#endif

TYPE2_FUNCATR VECTOR_CC vfloat2 XSINCOSF(vfloat d) {
#if !defined(DETERMINISTIC)
  vint2 q;
  vopmask o;
  vfloat u, s, t, rx, ry;
  vfloat2 r;

  s = d;

  if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f))))) {
    q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_2_PI)));
    u = vcast_vf_vi2(q);
    s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), s);
    s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f), s);
    s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f), s);
  } else if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAXf))))) {
    q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_2_PI)));
    u = vcast_vf_vi2(q);
    s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Af*0.5f), s);
    s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Bf*0.5f), s);
    s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Cf*0.5f), s);
    s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Df*0.5f), s);
  } else {
    dfi_t dfi = rempif(d);
    q = dfigeti_vi2_dfi(dfi);
    s = vadd_vf_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vf2gety_vf_vf2(dfigetdf_vf2_dfi(dfi)));
    s = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(s)));
  }

  t = s;

  s = vmul_vf_vf_vf(s, s);

  u = vcast_vf_f(-0.000195169282960705459117889f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00833215750753879547119141f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.166666537523269653320312f));

  rx = vmla_vf_vf_vf_vf(vmul_vf_vf_vf(u, s), t, t);
  rx = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), vcast_vf_f(-0.0f), rx);

  u = vcast_vf_f(-2.71811842367242206819355e-07f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(2.47990446951007470488548e-05f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.00138888787478208541870117f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.0416666641831398010253906f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.5));

  ry = vmla_vf_vf_vf_vf(s, u, vcast_vf_f(1));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(0));
  r = vf2setxy_vf2_vf_vf(vsel_vf_vo_vf_vf(o, rx, ry), vsel_vf_vo_vf_vf(o, ry, rx));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(2));
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(2)), vcast_vi2_i(2));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  return r;

#else // #if !defined(DETERMINISTIC)

  vint2 q;
  vopmask o;
  vfloat u, s, t, rx, ry;
  vfloat2 r;

  q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_2_PI)));
  u = vcast_vf_vi2(q);
  s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), d);
  s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f), s);
  s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f), s);
  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f));

  if (!LIKELY(vtestallones_i_vo32(g))) {
    vint2 q2 = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f((float)M_2_PI)));
    u = vcast_vf_vi2(q2);
    t = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Af*0.5f), d);
    t = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Bf*0.5f), t);
    t = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Cf*0.5f), t);
    t = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_Df*0.5f), t);

    q = vsel_vi2_vo_vi2_vi2(g, q, q2);
    s = vsel_vf_vo_vf_vf(g, s, t);
    g = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAXf));

    if (!LIKELY(vtestallones_i_vo32(g))) {
      dfi_t dfi = rempif(d);
      t = vadd_vf_vf_vf(vf2getx_vf_vf2(dfigetdf_vf2_dfi(dfi)), vf2gety_vf_vf2(dfigetdf_vf2_dfi(dfi)));
      t = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d)), vreinterpret_vm_vf(t)));

      q = vsel_vi2_vo_vi2_vi2(g, q, dfigeti_vi2_dfi(dfi));
      s = vsel_vf_vo_vf_vf(g, s, t);
    }
  }

  t = s;

  s = vmul_vf_vf_vf(s, s);

  u = vcast_vf_f(-0.000195169282960705459117889f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00833215750753879547119141f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.166666537523269653320312f));

  rx = vmla_vf_vf_vf_vf(vmul_vf_vf_vf(u, s), t, t);
  rx = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), vcast_vf_f(-0.0f), rx);

  u = vcast_vf_f(-2.71811842367242206819355e-07f);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(2.47990446951007470488548e-05f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.00138888787478208541870117f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.0416666641831398010253906f));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.5));

  ry = vmla_vf_vf_vf_vf(s, u, vcast_vf_f(1));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(0));
  r = vf2setxy_vf2_vf_vf(vsel_vf_vo_vf_vf(o, rx, ry), vsel_vf_vo_vf_vf(o, ry, rx));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(2));
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(2)), vcast_vi2_i(2));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  return r;
#endif // #if !defined(DETERMINISTIC)
}

TYPE2_FUNCATR VECTOR_CC vfloat2 XSINCOSF_U1(vfloat d) {
#if !defined(DETERMINISTIC)
  vint2 q;
  vopmask o;
  vfloat u, v, rx, ry;
  vfloat2 r, s, t, x;

  if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f))))) {
    u = vrint_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(2 * M_1_PI)));
    q = vrint_vi2_vf(u);
    v = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), d);
    s = dfadd2_vf2_vf_vf(v, vmul_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f)));
    s = dfadd_vf2_vf2_vf(s, vmul_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f)));
  } else {
    dfi_t dfi = rempif(d);
    q = dfigeti_vi2_dfi(dfi);
    s = dfigetdf_vf2_dfi(dfi);
    o = vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d));
    s = vf2setx_vf2_vf2_vf(s, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2getx_vf_vf2(s)))));
  }

  t = s;

  s = vf2setx_vf2_vf2_vf(s, dfsqu_vf_vf2(s));

  u = vcast_vf_f(-0.000195169282960705459117889f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.00833215750753879547119141f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.166666537523269653320312f));

  u = vmul_vf_vf_vf(u, vmul_vf_vf_vf(vf2getx_vf_vf2(s), vf2getx_vf_vf2(t)));

  x = dfadd_vf2_vf2_vf(t, u);
  rx = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  rx = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), vcast_vf_f(-0.0f), rx);

  u = vcast_vf_f(-2.71811842367242206819355e-07f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(2.47990446951007470488548e-05f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.00138888787478208541870117f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.0416666641831398010253906f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.5));

  x = dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf_vf(vf2getx_vf_vf2(s), u));
  ry = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(0));
  r = vf2setxy_vf2_vf_vf(vsel_vf_vo_vf_vf(o, rx, ry), vsel_vf_vo_vf_vf(o, ry, rx));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(2));
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(2)), vcast_vi2_i(2));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  return r;

#else // #if !defined(DETERMINISTIC)

  vint2 q;
  vopmask o;
  vfloat u, v, rx, ry;
  vfloat2 r, s, t, x;

  u = vrint_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(2 * M_1_PI)));
  q = vrint_vi2_vf(u);
  v = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), d);
  s = dfadd2_vf2_vf_vf(v, vmul_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f)));
  s = dfadd_vf2_vf2_vf(s, vmul_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f)));
  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f));

  if (!LIKELY(vtestallones_i_vo32(g))) {
    dfi_t dfi = rempif(d);
    t = dfigetdf_vf2_dfi(dfi);
    o = vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d));
    t = vf2setx_vf2_vf2_vf(t, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2getx_vf_vf2(t)))));
    q = vsel_vi2_vo_vi2_vi2(g, q, dfigeti_vi2_dfi(dfi));
    s = vsel_vf2_vo_vf2_vf2(g, s, t);
  }

  t = s;

  s = vf2setx_vf2_vf2_vf(s, dfsqu_vf_vf2(s));

  u = vcast_vf_f(-0.000195169282960705459117889f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.00833215750753879547119141f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.166666537523269653320312f));

  u = vmul_vf_vf_vf(u, vmul_vf_vf_vf(vf2getx_vf_vf2(s), vf2getx_vf_vf2(t)));

  x = dfadd_vf2_vf2_vf(t, u);
  rx = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  rx = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), vcast_vf_f(-0.0f), rx);

  u = vcast_vf_f(-2.71811842367242206819355e-07f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(2.47990446951007470488548e-05f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.00138888787478208541870117f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.0416666641831398010253906f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-0.5));

  x = dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf_vf(vf2getx_vf_vf2(s), u));
  ry = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(0));
  r = vf2setxy_vf2_vf_vf(vsel_vf_vo_vf_vf(o, rx, ry), vsel_vf_vo_vf_vf(o, ry, rx));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(2));
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(2)), vcast_vi2_i(2));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  return r;
#endif // #if !defined(DETERMINISTIC)
}

#if !defined(DETERMINISTIC)
TYPE2_FUNCATR VECTOR_CC vfloat2 XSINCOSPIF_U05(vfloat d) {
  vopmask o;
  vfloat u, s, t, rx, ry;
  vfloat2 r, x, s2;

  u = vmul_vf_vf_vf(d, vcast_vf_f(4));
  vint2 q = vtruncate_vi2_vf(u);
  q = vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vxor_vi2_vi2_vi2(vsrl_vi2_vi2_i(q, 31), vcast_vi2_i(1))), vcast_vi2_i(~1));
  s = vsub_vf_vf_vf(u, vcast_vf_vi2(q));

  t = s;
  s = vmul_vf_vf_vf(s, s);
  s2 = dfmul_vf2_vf_vf(t, t);

  //

  u = vcast_vf_f(+0.3093842054e-6);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.3657307388e-4));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2490393585e-2));
  x = dfadd2_vf2_vf_vf2(vmul_vf_vf_vf(u, s), vcast_vf2_f_f(-0.080745510756969451904, -1.3373665339076936258e-09));
  x = dfadd2_vf2_vf2_vf2(dfmul_vf2_vf2_vf2(s2, x), vcast_vf2_f_f(0.78539818525314331055, -2.1857338617566484855e-08));

  x = dfmul_vf2_vf2_vf(x, t);
  rx = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  rx = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), vcast_vf_f(-0.0f), rx);

  //

  u = vcast_vf_f(-0.2430611801e-7);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.3590577080e-5));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.3259917721e-3));
  x = dfadd2_vf2_vf_vf2(vmul_vf_vf_vf(u, s), vcast_vf2_f_f(0.015854343771934509277, 4.4940051354032242811e-10));
  x = dfadd2_vf2_vf2_vf2(dfmul_vf2_vf2_vf2(s2, x), vcast_vf2_f_f(-0.30842512845993041992, -9.0728339030733922277e-09));

  x = dfadd2_vf2_vf2_vf(dfmul_vf2_vf2_vf2(x, s2), vcast_vf_f(1));
  ry = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  //

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(0));
  r = vf2setxy_vf2_vf_vf(vsel_vf_vo_vf_vf(o, rx, ry), vsel_vf_vo_vf_vf(o, ry, rx));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(4)), vcast_vi2_i(4));
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(4)), vcast_vi2_i(4));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  o = vgt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(1e+7f));
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vandnot_vm_vo32_vm(o, vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vandnot_vm_vo32_vm(o, vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  o = visinf_vo_vf(d);
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  return r;
}

TYPE2_FUNCATR VECTOR_CC vfloat2 XSINCOSPIF_U35(vfloat d) {
  vopmask o;
  vfloat u, s, t, rx, ry;
  vfloat2 r;

  u = vmul_vf_vf_vf(d, vcast_vf_f(4));
  vint2 q = vtruncate_vi2_vf(u);
  q = vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vxor_vi2_vi2_vi2(vsrl_vi2_vi2_i(q, 31), vcast_vi2_i(1))), vcast_vi2_i(~1));
  s = vsub_vf_vf_vf(u, vcast_vf_vi2(q));

  t = s;
  s = vmul_vf_vf_vf(s, s);

  //

  u = vcast_vf_f(-0.3600925265e-4);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2490088111e-2));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.8074551076e-1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.7853981853e+0));

  rx = vmul_vf_vf_vf(u, t);

  //

  u = vcast_vf_f(+0.3539815225e-5);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.3259574005e-3));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.1585431583e-1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(-0.3084251285e+0));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(1));

  ry = u;

  //

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(0));
  r = vf2setxy_vf2_vf_vf(vsel_vf_vo_vf_vf(o, rx, ry), vsel_vf_vo_vf_vf(o, ry, rx));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(4)), vcast_vi2_i(4));
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(4)), vcast_vi2_i(4));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  o = vgt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(1e+7f));
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vandnot_vm_vo32_vm(o, vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vandnot_vm_vo32_vm(o, vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  o = visinf_vo_vf(d);
  r = vf2setx_vf2_vf2_vf(r, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2getx_vf_vf2(r)))));
  r = vf2sety_vf2_vf2_vf(r, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2gety_vf_vf2(r)))));

  return r;
}

TYPE6_FUNCATR VECTOR_CC vfloat2 XMODFF(vfloat x) {
  vfloat fr = vsub_vf_vf_vf(x, vcast_vf_vi2(vtruncate_vi2_vf(x)));
  fr = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(INT64_C(1) << 23)), vcast_vf_f(0), fr);

  vfloat2 ret;

  ret = vf2setxy_vf2_vf_vf(vcopysign_vf_vf_vf(fr, x), vcopysign_vf_vf_vf(vsub_vf_vf_vf(x, fr), x));

  return ret;
}

#ifdef ENABLE_GNUABI
EXPORT VECTOR_CC void xsincosf(vfloat a, float *ps, float *pc) {
  vfloat2 r = sincosfk(a);
  vstoreu_v_p_vf(ps, vf2getx_vf_vf2(r));
  vstoreu_v_p_vf(pc, vf2gety_vf_vf2(r));
}

EXPORT VECTOR_CC void xsincosf_u1(vfloat a, float *ps, float *pc) {
  vfloat2 r = sincosfk_u1(a);
  vstoreu_v_p_vf(ps, vf2getx_vf_vf2(r));
  vstoreu_v_p_vf(pc, vf2gety_vf_vf2(r));
}

EXPORT VECTOR_CC void xsincospif_u05(vfloat a, float *ps, float *pc) {
  vfloat2 r = sincospifk_u05(a);
  vstoreu_v_p_vf(ps, vf2getx_vf_vf2(r));
  vstoreu_v_p_vf(pc, vf2gety_vf_vf2(r));
}

EXPORT VECTOR_CC void xsincospif_u35(vfloat a, float *ps, float *pc) {
  vfloat2 r = sincospifk_u35(a);
  vstoreu_v_p_vf(ps, vf2getx_vf_vf2(r));
  vstoreu_v_p_vf(pc, vf2gety_vf_vf2(r));
}

EXPORT CONST VECTOR_CC vfloat xmodff(vfloat a, float *iptr) {
  vfloat2 r = modffk(a);
  vstoreu_v_p_vf(iptr, vf2gety_vf_vf2(r));
  return vf2getx_vf_vf2(r);
}
#endif // #ifdef ENABLE_GNUABI
#endif // #if !defined(DETERMINISTIC)

EXPORT CONST VECTOR_CC vfloat xtanf_u1(vfloat d) {
#if !defined(DETERMINISTIC)
  vint2 q;
  vfloat u, v;
  vfloat2 s, t, x;
  vopmask o;

  if (LIKELY(vtestallones_i_vo32(vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f))))) {
    u = vrint_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(2 * M_1_PI)));
    q = vrint_vi2_vf(u);
    v = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), d);
    s = dfadd2_vf2_vf_vf(v, vmul_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f)));
    s = dfadd_vf2_vf2_vf(s, vmul_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f)));
  } else {
    dfi_t dfi = rempif(d);
    q = dfigeti_vi2_dfi(dfi);
    s = dfigetdf_vf2_dfi(dfi);
    o = vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d));
    s = vf2setx_vf2_vf2_vf(s, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2getx_vf_vf2(s)))));
    s = vf2sety_vf2_vf2_vf(s, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2gety_vf_vf2(s)))));
  }

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1));
  vmask n = vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0)));
#if !defined(_MSC_VER)
  s = vf2setx_vf2_vf2_vf(s, vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(vf2getx_vf_vf2(s)), n)));
  s = vf2sety_vf2_vf2_vf(s, vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(vf2gety_vf_vf2(s)), n)));
#else
  s.x = vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(s.x), n));
  s.y = vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(s.y), n));
#endif

  t = s;
  s = dfsqu_vf2_vf2(s);
  s = dfnormalize_vf2_vf2(s);

  u = vcast_vf_f(0.00446636462584137916564941f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-8.3920182078145444393158e-05f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.0109639242291450500488281f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.0212360303848981857299805f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.0540687143802642822265625f));

  x = dfadd_vf2_vf_vf(vcast_vf_f(0.133325666189193725585938f), vmul_vf_vf_vf(u, vf2getx_vf_vf2(s)));
  x = dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf2_vf2(dfadd_vf2_vf_vf2(vcast_vf_f(0.33333361148834228515625f), dfmul_vf2_vf2_vf2(s, x)), s));
  x = dfmul_vf2_vf2_vf2(t, x);

  x = vsel_vf2_vo_vf2_vf2(o, dfrec_vf2_vf2(x), x);

  u = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  u = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), d, u);

  return u;

#else // #if !defined(DETERMINISTIC)

  vint2 q;
  vfloat u, v;
  vfloat2 s, t, x;
  vopmask o;

  u = vrint_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(2 * M_1_PI)));
  q = vrint_vi2_vf(u);
  v = vmla_vf_vf_vf_vf(u, vcast_vf_f(-PI_A2f*0.5f), d);
  s = dfadd2_vf2_vf_vf(v, vmul_vf_vf_vf(u, vcast_vf_f(-PI_B2f*0.5f)));
  s = dfadd_vf2_vf2_vf(s, vmul_vf_vf_vf(u, vcast_vf_f(-PI_C2f*0.5f)));
  vopmask g = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX2f));

  if (!LIKELY(vtestallones_i_vo32(g))) {
    dfi_t dfi = rempif(d);
    t = dfigetdf_vf2_dfi(dfi);
    o = vor_vo_vo_vo(visinf_vo_vf(d), visnan_vo_vf(d));
    t = vf2setx_vf2_vf2_vf(t, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2getx_vf_vf2(t)))));
    t = vf2sety_vf2_vf2_vf(t, vreinterpret_vf_vm(vor_vm_vo32_vm(o, vreinterpret_vm_vf(vf2gety_vf_vf2(t)))));
    q = vsel_vi2_vo_vi2_vi2(g, q, dfigeti_vi2_dfi(dfi));
    s = vsel_vf2_vo_vf2_vf2(g, s, t);
  }

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1));
  vmask n = vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0)));
  s = vf2setx_vf2_vf2_vf(s, vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(vf2getx_vf_vf2(s)), n)));
  s = vf2sety_vf2_vf2_vf(s, vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(vf2gety_vf_vf2(s)), n)));

  t = s;
  s = dfsqu_vf2_vf2(s);
  s = dfnormalize_vf2_vf2(s);

  u = vcast_vf_f(0.00446636462584137916564941f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(-8.3920182078145444393158e-05f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.0109639242291450500488281f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.0212360303848981857299805f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.0540687143802642822265625f));

  x = dfadd_vf2_vf_vf(vcast_vf_f(0.133325666189193725585938f), vmul_vf_vf_vf(u, vf2getx_vf_vf2(s)));
  x = dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf2_vf2(dfadd_vf2_vf_vf2(vcast_vf_f(0.33333361148834228515625f), dfmul_vf2_vf2_vf2(s, x)), s));
  x = dfmul_vf2_vf2_vf2(t, x);

  x = vsel_vf2_vo_vf2_vf2(o, dfrec_vf2_vf2(x), x);

  u = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  u = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), d, u);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xatanf(vfloat d) {
  vfloat s, t, u;
  vint2 q;

  q = vsel_vi2_vf_vi2(d, vcast_vi2_i(2));
  s = vabs_vf_vf(d);

  q = vsel_vi2_vf_vf_vi2_vi2(vcast_vf_f(1.0f), s, vadd_vi2_vi2_vi2(q, vcast_vi2_i(1)), q);
  s = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(vcast_vf_f(1.0f), s), vrec_vf_vf(s), s);

  t = vmul_vf_vf_vf(s, s);

  vfloat t2 = vmul_vf_vf_vf(t, t), t4 = vmul_vf_vf_vf(t2, t2);
  u = POLY8(t, t2, t4,
            0.00282363896258175373077393f,
            -0.0159569028764963150024414f,
            0.0425049886107444763183594f,
            -0.0748900920152664184570312f,
            0.106347933411598205566406f,
            -0.142027363181114196777344f,
            0.199926957488059997558594f,
            -0.333331018686294555664062f);

  t = vmla_vf_vf_vf_vf(s, vmul_vf_vf_vf(t, u), s);

  t = vsel_vf_vo_vf_vf(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(1)), vcast_vi2_i(1)), vsub_vf_vf_vf(vcast_vf_f((float)(M_PI/2)), t), t);

  t = vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(2)), vreinterpret_vm_vf(vcast_vf_f(-0.0f))), vreinterpret_vm_vf(t)));

#if defined(ENABLE_NEON32) || defined(ENABLE_NEON32VFPV4)
  t = vsel_vf_vo_vf_vf(visinf_vo_vf(d), vmulsign_vf_vf_vf(vcast_vf_f(1.5874010519681994747517056f), d), t);
#endif

  return t;
}
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vfloat atan2kf(vfloat y, vfloat x) {
  vfloat s, t, u;
  vint2 q;
  vopmask p;

  q = vsel_vi2_vf_vi2(x, vcast_vi2_i(-2));
  x = vabs_vf_vf(x);

  q = vsel_vi2_vf_vf_vi2_vi2(x, y, vadd_vi2_vi2_vi2(q, vcast_vi2_i(1)), q);
  p = vlt_vo_vf_vf(x, y);
  s = vsel_vf_vo_vf_vf(p, vneg_vf_vf(x), y);
  t = vmax_vf_vf_vf(x, y);

  s = vdiv_vf_vf_vf(s, t);
  t = vmul_vf_vf_vf(s, s);

  vfloat t2 = vmul_vf_vf_vf(t, t), t4 = vmul_vf_vf_vf(t2, t2);
  u = POLY8(t, t2, t4,
            0.00282363896258175373077393f,
            -0.0159569028764963150024414f,
            0.0425049886107444763183594f,
            -0.0748900920152664184570312f,
            0.106347933411598205566406f,
            -0.142027363181114196777344f,
            0.199926957488059997558594f,
            -0.333331018686294555664062f);

  t = vmla_vf_vf_vf_vf(s, vmul_vf_vf_vf(t, u), s);
  t = vmla_vf_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f((float)(M_PI/2)), t);

  return t;
}

static INLINE CONST VECTOR_CC vfloat visinf2_vf_vf_vf(vfloat d, vfloat m) {
  return vreinterpret_vf_vm(vand_vm_vo32_vm(visinf_vo_vf(d), vor_vm_vm_vm(vsignbit_vm_vf(d), vreinterpret_vm_vf(m))));
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xatan2f(vfloat y, vfloat x) {
  vfloat r = atan2kf(vabs_vf_vf(y), x);

  r = vmulsign_vf_vf_vf(r, x);
  r = vsel_vf_vo_vf_vf(vor_vo_vo_vo(visinf_vo_vf(x), veq_vo_vf_vf(x, vcast_vf_f(0.0f))), vsub_vf_vf_vf(vcast_vf_f((float)(M_PI/2)), visinf2_vf_vf_vf(x, vmulsign_vf_vf_vf(vcast_vf_f((float)(M_PI/2)), x))), r);
  r = vsel_vf_vo_vf_vf(visinf_vo_vf(y), vsub_vf_vf_vf(vcast_vf_f((float)(M_PI/2)), visinf2_vf_vf_vf(x, vmulsign_vf_vf_vf(vcast_vf_f((float)(M_PI/4)), x))), r);

  r = vsel_vf_vo_vf_vf(veq_vo_vf_vf(y, vcast_vf_f(0.0f)), vreinterpret_vf_vm(vand_vm_vo32_vm(vsignbit_vo_vf(x), vreinterpret_vm_vf(vcast_vf_f((float)M_PI)))), r);

  r = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visnan_vo_vf(x), visnan_vo_vf(y)), vreinterpret_vm_vf(vmulsign_vf_vf_vf(r, y))));
  return r;
}

EXPORT CONST VECTOR_CC vfloat xasinf(vfloat d) {
  vopmask o = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(0.5f));
  vfloat x2 = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, d), vmul_vf_vf_vf(vsub_vf_vf_vf(vcast_vf_f(1), vabs_vf_vf(d)), vcast_vf_f(0.5f)));
  vfloat x = vsel_vf_vo_vf_vf(o, vabs_vf_vf(d), vsqrt_vf_vf(x2)), u;

  u = vcast_vf_f(+0.4197454825e-1);
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.2424046025e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.4547423869e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.7495029271e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.1666677296e+0));
  u = vmla_vf_vf_vf_vf(u, vmul_vf_vf_vf(x, x2), x);

  vfloat r = vsel_vf_vo_vf_vf(o, u, vmla_vf_vf_vf_vf(u, vcast_vf_f(-2), vcast_vf_f(M_PIf/2)));
  return vmulsign_vf_vf_vf(r, d);
}

EXPORT CONST VECTOR_CC vfloat xacosf(vfloat d) {
  vopmask o = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(0.5f));
  vfloat x2 = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, d),
                                vmul_vf_vf_vf(vsub_vf_vf_vf(vcast_vf_f(1), vabs_vf_vf(d)), vcast_vf_f(0.5f))), u;
  vfloat x = vsel_vf_vo_vf_vf(o, vabs_vf_vf(d), vsqrt_vf_vf(x2));
  x = vsel_vf_vo_vf_vf(veq_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(1.0f)), vcast_vf_f(0), x);

  u = vcast_vf_f(+0.4197454825e-1);
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.2424046025e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.4547423869e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.7495029271e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.1666677296e+0));
  u = vmul_vf_vf_vf(u, vmul_vf_vf_vf(x2, x));

  vfloat y = vsub_vf_vf_vf(vcast_vf_f(3.1415926535897932f/2), vadd_vf_vf_vf(vmulsign_vf_vf_vf(x, d), vmulsign_vf_vf_vf(u, d)));
  x = vadd_vf_vf_vf(x, u);
  vfloat r = vsel_vf_vo_vf_vf(o, y, vmul_vf_vf_vf(x, vcast_vf_f(2)));
  return vsel_vf_vo_vf_vf(vandnot_vo_vo_vo(o, vlt_vo_vf_vf(d, vcast_vf_f(0))),
                          vf2getx_vf_vf2(dfadd_vf2_vf2_vf(vcast_vf2_f_f(3.1415927410125732422f,-8.7422776573475857731e-08f),
                                                          vneg_vf_vf(r))), r);
}
#endif // #if !defined(DETERMINISTIC)

//

static INLINE CONST VECTOR_CC vfloat2 atan2kf_u1(vfloat2 y, vfloat2 x) {
  vfloat u;
  vfloat2 s, t;
  vint2 q;
  vopmask p;
  vmask r;

  q = vsel_vi2_vf_vf_vi2_vi2(vf2getx_vf_vf2(x), vcast_vf_f(0), vcast_vi2_i(-2), vcast_vi2_i(0));
  p = vlt_vo_vf_vf(vf2getx_vf_vf2(x), vcast_vf_f(0));
  r = vand_vm_vo32_vm(p, vreinterpret_vm_vf(vcast_vf_f(-0.0)));
  x = vf2setx_vf2_vf2_vf(x, vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(vf2getx_vf_vf2(x)), r)));
  x = vf2sety_vf2_vf2_vf(x, vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(vf2gety_vf_vf2(x)), r)));

  q = vsel_vi2_vf_vf_vi2_vi2(vf2getx_vf_vf2(x), vf2getx_vf_vf2(y), vadd_vi2_vi2_vi2(q, vcast_vi2_i(1)), q);
  p = vlt_vo_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(y));
  s = vsel_vf2_vo_vf2_vf2(p, dfneg_vf2_vf2(x), y);
  t = vsel_vf2_vo_vf2_vf2(p, y, x);

  s = dfdiv_vf2_vf2_vf2(s, t);
  t = dfsqu_vf2_vf2(s);
  t = dfnormalize_vf2_vf2(t);

  u = vcast_vf_f(-0.00176397908944636583328247f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(t), vcast_vf_f(0.0107900900766253471374512f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(t), vcast_vf_f(-0.0309564601629972457885742f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(t), vcast_vf_f(0.0577365085482597351074219f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(t), vcast_vf_f(-0.0838950723409652709960938f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(t), vcast_vf_f(0.109463557600975036621094f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(t), vcast_vf_f(-0.142626821994781494140625f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(t), vcast_vf_f(0.199983194470405578613281f));

  t = dfmul_vf2_vf2_vf2(t, dfadd_vf2_vf_vf(vcast_vf_f(-0.333332866430282592773438f), vmul_vf_vf_vf(u, vf2getx_vf_vf2(t))));
  t = dfmul_vf2_vf2_vf2(s, dfadd_vf2_vf_vf2(vcast_vf_f(1), t));
  t = dfadd_vf2_vf2_vf2(dfmul_vf2_vf2_vf(vcast_vf2_f_f(1.5707963705062866211f, -4.3711388286737928865e-08f), vcast_vf_vi2(q)), t);

  return t;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xatan2f_u1(vfloat y, vfloat x) {
  vopmask o = vlt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(2.9387372783541830947e-39f)); // nexttowardf((1.0 / FLT_MAX), 1)
  x = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(x, vcast_vf_f(1 << 24)), x);
  y = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(y, vcast_vf_f(1 << 24)), y);

  vfloat2 d = atan2kf_u1(vcast_vf2_vf_vf(vabs_vf_vf(y), vcast_vf_f(0)), vcast_vf2_vf_vf(x, vcast_vf_f(0)));
  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d));

  r = vmulsign_vf_vf_vf(r, x);
  r = vsel_vf_vo_vf_vf(vor_vo_vo_vo(visinf_vo_vf(x), veq_vo_vf_vf(x, vcast_vf_f(0))), vsub_vf_vf_vf(vcast_vf_f(M_PI/2), visinf2_vf_vf_vf(x, vmulsign_vf_vf_vf(vcast_vf_f(M_PI/2), x))), r);
  r = vsel_vf_vo_vf_vf(visinf_vo_vf(y), vsub_vf_vf_vf(vcast_vf_f(M_PI/2), visinf2_vf_vf_vf(x, vmulsign_vf_vf_vf(vcast_vf_f(M_PI/4), x))), r);
  r = vsel_vf_vo_vf_vf(veq_vo_vf_vf(y, vcast_vf_f(0.0f)), vreinterpret_vf_vm(vand_vm_vo32_vm(vsignbit_vo_vf(x), vreinterpret_vm_vf(vcast_vf_f((float)M_PI)))), r);

  r = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visnan_vo_vf(x), visnan_vo_vf(y)), vreinterpret_vm_vf(vmulsign_vf_vf_vf(r, y))));
  return r;
}

EXPORT CONST VECTOR_CC vfloat xasinf_u1(vfloat d) {
  vopmask o = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(0.5f));
  vfloat x2 = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, d), vmul_vf_vf_vf(vsub_vf_vf_vf(vcast_vf_f(1), vabs_vf_vf(d)), vcast_vf_f(0.5f))), u;
  vfloat2 x = vsel_vf2_vo_vf2_vf2(o, vcast_vf2_vf_vf(vabs_vf_vf(d), vcast_vf_f(0)), dfsqrt_vf2_vf(x2));
  x = vsel_vf2_vo_vf2_vf2(veq_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(1.0f)), vcast_vf2_f_f(0, 0), x);

  u = vcast_vf_f(+0.4197454825e-1);
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.2424046025e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.4547423869e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.7495029271e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.1666677296e+0));
  u = vmul_vf_vf_vf(u, vmul_vf_vf_vf(x2, vf2getx_vf_vf2(x)));

  vfloat2 y = dfsub_vf2_vf2_vf(dfsub_vf2_vf2_vf2(vcast_vf2_f_f(3.1415927410125732422f/4,-8.7422776573475857731e-08f/4), x), u);

  vfloat r = vsel_vf_vo_vf_vf(o, vadd_vf_vf_vf(u, vf2getx_vf_vf2(x)),
                               vmul_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(y), vf2gety_vf_vf2(y)), vcast_vf_f(2)));
  return vmulsign_vf_vf_vf(r, d);
}

EXPORT CONST VECTOR_CC vfloat xacosf_u1(vfloat d) {
  vopmask o = vlt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(0.5f));
  vfloat x2 = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, d), vmul_vf_vf_vf(vsub_vf_vf_vf(vcast_vf_f(1), vabs_vf_vf(d)), vcast_vf_f(0.5f))), u;
  vfloat2 x = vsel_vf2_vo_vf2_vf2(o, vcast_vf2_vf_vf(vabs_vf_vf(d), vcast_vf_f(0)), dfsqrt_vf2_vf(x2));
  x = vsel_vf2_vo_vf2_vf2(veq_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(1.0f)), vcast_vf2_f_f(0, 0), x);

  u = vcast_vf_f(+0.4197454825e-1);
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.2424046025e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.4547423869e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.7495029271e-1));
  u = vmla_vf_vf_vf_vf(u, x2, vcast_vf_f(+0.1666677296e+0));
  u = vmul_vf_vf_vf(u, vmul_vf_vf_vf(x2, vf2getx_vf_vf2(x)));

  vfloat2 y = dfsub_vf2_vf2_vf2(vcast_vf2_f_f(3.1415927410125732422f/2, -8.7422776573475857731e-08f/2),
                                 dfadd_vf2_vf_vf(vmulsign_vf_vf_vf(vf2getx_vf_vf2(x), d), vmulsign_vf_vf_vf(u, d)));
  x = dfadd_vf2_vf2_vf(x, u);

  y = vsel_vf2_vo_vf2_vf2(o, y, dfscale_vf2_vf2_vf(x, vcast_vf_f(2)));

  y = vsel_vf2_vo_vf2_vf2(vandnot_vo_vo_vo(o, vlt_vo_vf_vf(d, vcast_vf_f(0))),
                          dfsub_vf2_vf2_vf2(vcast_vf2_f_f(3.1415927410125732422f, -8.7422776573475857731e-08f), y), y);

  return vadd_vf_vf_vf(vf2getx_vf_vf2(y), vf2gety_vf_vf2(y));
}

EXPORT CONST VECTOR_CC vfloat xatanf_u1(vfloat d) {
  vfloat2 d2 = atan2kf_u1(vcast_vf2_vf_vf(vabs_vf_vf(d), vcast_vf_f(0)), vcast_vf2_f_f(1, 0));
  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(d2), vf2gety_vf_vf2(d2));
  r = vsel_vf_vo_vf_vf(visinf_vo_vf(d), vcast_vf_f(1.570796326794896557998982), r);
  return vmulsign_vf_vf_vf(r, d);
}
#endif // #if !defined(DETERMINISTIC)

//

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xlogf(vfloat d) {
  vfloat x, x2, t, m;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(SLEEF_FLT_MIN));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f((float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32))), d);
  vint2 e = vilogb2k_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0f/0.75f)));
  m = vldexp3_vf_vf_vi2(d, vneg_vi2_vi2(e));
  e = vsel_vi2_vo_vi2_vi2(o, vsub_vi2_vi2_vi2(e, vcast_vi2_i(64)), e);
#else
  vfloat e = vgetexp_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0f/0.75f)));
  e = vsel_vf_vo_vf_vf(vispinf_vo_vf(e), vcast_vf_f(128.0f), e);
  m = vgetmant_vf_vf(d);
#endif

  x = vdiv_vf_vf_vf(vsub_vf_vf_vf(m, vcast_vf_f(1.0f)), vadd_vf_vf_vf(vcast_vf_f(1.0f), m));
  x2 = vmul_vf_vf_vf(x, x);

  t = vcast_vf_f(0.2392828464508056640625f);
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(0.28518211841583251953125f));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(0.400005877017974853515625f));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(0.666666686534881591796875f));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(2.0f));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  x = vmla_vf_vf_vf_vf(x, t, vmul_vf_vf_vf(vcast_vf_f(0.693147180559945286226764f), vcast_vf_vi2(e)));
  x = vsel_vf_vo_vf_vf(vispinf_vo_vf(d), vcast_vf_f(SLEEF_INFINITYf), x);
  x = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vlt_vo_vf_vf(d, vcast_vf_f(0)), visnan_vo_vf(d)), vcast_vf_f(SLEEF_NANf), x);
  x = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(0)), vcast_vf_f(-SLEEF_INFINITYf), x);
#else
  x = vmla_vf_vf_vf_vf(x, t, vmul_vf_vf_vf(vcast_vf_f(0.693147180559945286226764f), e));
  x = vfixup_vf_vf_vf_vi2_i(x, d, vcast_vi2_i((5 << (5*4))), 0);
#endif

  return x;
}
#endif // #if !defined(DETERMINISTIC)

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xexpf(vfloat d) {
  vint2 q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(R_LN2f)));
  vfloat s, u;

  s = vmla_vf_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Uf), d);
  s = vmla_vf_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Lf), s);

  u = vcast_vf_f(0.000198527617612853646278381);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00139304355252534151077271));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00833336077630519866943359));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.0416664853692054748535156));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.166666671633720397949219));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.5));

  u = vadd_vf_vf_vf(vcast_vf_f(1.0f), vmla_vf_vf_vf_vf(vmul_vf_vf_vf(s, s), u, s));

  u = vldexp2_vf_vf_vi2(u, q);

  u = vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(d, vcast_vf_f(-104)), vreinterpret_vm_vf(u)));
  u = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(vcast_vf_f(100), d), vcast_vf_f(SLEEF_INFINITYf), u);

  return u;
}
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vfloat expm1fk(vfloat d) {
  vint2 q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(R_LN2f)));
  vfloat s, u;

  s = vmla_vf_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Uf), d);
  s = vmla_vf_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Lf), s);

  vfloat s2 = vmul_vf_vf_vf(s, s), s4 = vmul_vf_vf_vf(s2, s2);
  u = POLY6(s, s2, s4,
            0.000198527617612853646278381,
            0.00139304355252534151077271,
            0.00833336077630519866943359,
            0.0416664853692054748535156,
            0.166666671633720397949219,
            0.5);

  u = vmla_vf_vf_vf_vf(vmul_vf_vf_vf(s, s), u, s);

  u = vsel_vf_vo_vf_vf(veq_vo_vi2_vi2(q, vcast_vi2_i(0)), u,
                       vsub_vf_vf_vf(vldexp2_vf_vf_vi2(vadd_vf_vf_vf(u, vcast_vf_f(1)), q), vcast_vf_f(1)));

  return u;
}

#if defined(ENABLE_NEON32) || defined(ENABLE_NEON32VFPV4)
EXPORT CONST VECTOR_CC vfloat xsqrtf_u35(vfloat d) {
  vfloat e = vreinterpret_vf_vi2(vadd_vi2_vi2_vi2(vcast_vi2_i(0x20000000), vand_vi2_vi2_vi2(vcast_vi2_i(0x7f000000), vsrl_vi2_vi2_i(vreinterpret_vi2_vf(d), 1))));
  vfloat m = vreinterpret_vf_vi2(vadd_vi2_vi2_vi2(vcast_vi2_i(0x3f000000), vand_vi2_vi2_vi2(vcast_vi2_i(0x01ffffff), vreinterpret_vi2_vf(d))));
  float32x4_t x = vrsqrteq_f32(m);
  x = vmulq_f32(x, vrsqrtsq_f32(m, vmulq_f32(x, x)));
  float32x4_t u = vmulq_f32(x, m);
  u = vmlaq_f32(u, vmlsq_f32(m, u, u), vmulq_f32(x, vdupq_n_f32(0.5)));
  e = vreinterpret_vf_vm(vandnot_vm_vo32_vm(veq_vo_vf_vf(d, vcast_vf_f(0)), vreinterpret_vm_vf(e)));
  u = vmul_vf_vf_vf(e, u);

  u = vsel_vf_vo_vf_vf(visinf_vo_vf(d), vcast_vf_f(SLEEF_INFINITYf), u);
  u = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visnan_vo_vf(d), vlt_vo_vf_vf(d, vcast_vf_f(0))), vreinterpret_vm_vf(u)));
  u = vmulsign_vf_vf_vf(u, d);

  return u;
}
#elif defined(ENABLE_VECEXT)
EXPORT CONST VECTOR_CC vfloat xsqrtf_u35(vfloat d) {
  vfloat q = vsqrt_vf_vf(d);
  q = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), vcast_vf_f(-0.0), q);
  return vsel_vf_vo_vf_vf(vispinf_vo_vf(d), vcast_vf_f(SLEEF_INFINITYf), q);
}
#else
EXPORT CONST VECTOR_CC vfloat xsqrtf_u35(vfloat d) { return vsqrt_vf_vf(d); }
#endif

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xcbrtf(vfloat d) {
  vfloat x, y, q = vcast_vf_f(1.0), t;
  vint2 e, qu, re;

#if defined(ENABLE_AVX512F) || defined(ENABLE_AVX512FNOFMA)
  vfloat s = d;
#endif
  e = vadd_vi2_vi2_vi2(vilogbk_vi2_vf(vabs_vf_vf(d)), vcast_vi2_i(1));
  d = vldexp2_vf_vf_vi2(d, vneg_vi2_vi2(e));

  t = vadd_vf_vf_vf(vcast_vf_vi2(e), vcast_vf_f(6144));
  qu = vtruncate_vi2_vf(vmul_vf_vf_vf(t, vcast_vf_f(1.0f/3.0f)));
  re = vtruncate_vi2_vf(vsub_vf_vf_vf(t, vmul_vf_vf_vf(vcast_vf_vi2(qu), vcast_vf_f(3))));

  q = vsel_vf_vo_vf_vf(veq_vo_vi2_vi2(re, vcast_vi2_i(1)), vcast_vf_f(1.2599210498948731647672106f), q);
  q = vsel_vf_vo_vf_vf(veq_vo_vi2_vi2(re, vcast_vi2_i(2)), vcast_vf_f(1.5874010519681994747517056f), q);
  q = vldexp2_vf_vf_vi2(q, vsub_vi2_vi2_vi2(qu, vcast_vi2_i(2048)));

  q = vmulsign_vf_vf_vf(q, d);
  d = vabs_vf_vf(d);

  x = vcast_vf_f(-0.601564466953277587890625f);
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(2.8208892345428466796875f));
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(-5.532182216644287109375f));
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(5.898262500762939453125f));
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(-3.8095417022705078125f));
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(2.2241256237030029296875f));

  y = vmul_vf_vf_vf(vmul_vf_vf_vf(d, x), x);
  y = vmul_vf_vf_vf(vsub_vf_vf_vf(y, vmul_vf_vf_vf(vmul_vf_vf_vf(vcast_vf_f(2.0f / 3.0f), y), vmla_vf_vf_vf_vf(y, x, vcast_vf_f(-1.0f)))), q);

#if defined(ENABLE_AVX512F) || defined(ENABLE_AVX512FNOFMA)
  y = vsel_vf_vo_vf_vf(visinf_vo_vf(s), vmulsign_vf_vf_vf(vcast_vf_f(SLEEF_INFINITYf), s), y);
  y = vsel_vf_vo_vf_vf(veq_vo_vf_vf(s, vcast_vf_f(0)), vmulsign_vf_vf_vf(vcast_vf_f(0), s), y);
#endif

  return y;
}
#endif // #if !defined(DETERMINISTIC)

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xcbrtf_u1(vfloat d) {
  vfloat x, y, z, t;
  vfloat2 q2 = vcast_vf2_f_f(1, 0), u, v;
  vint2 e, qu, re;

#if defined(ENABLE_AVX512F) || defined(ENABLE_AVX512FNOFMA)
  vfloat s = d;
#endif
  e = vadd_vi2_vi2_vi2(vilogbk_vi2_vf(vabs_vf_vf(d)), vcast_vi2_i(1));
  d = vldexp2_vf_vf_vi2(d, vneg_vi2_vi2(e));

  t = vadd_vf_vf_vf(vcast_vf_vi2(e), vcast_vf_f(6144));
  qu = vtruncate_vi2_vf(vmul_vf_vf_vf(t, vcast_vf_f(1.0/3.0)));
  re = vtruncate_vi2_vf(vsub_vf_vf_vf(t, vmul_vf_vf_vf(vcast_vf_vi2(qu), vcast_vf_f(3))));

  q2 = vsel_vf2_vo_vf2_vf2(veq_vo_vi2_vi2(re, vcast_vi2_i(1)), vcast_vf2_f_f(1.2599210739135742188f, -2.4018701694217270415e-08), q2);
  q2 = vsel_vf2_vo_vf2_vf2(veq_vo_vi2_vi2(re, vcast_vi2_i(2)), vcast_vf2_f_f(1.5874010324478149414f,  1.9520385308169352356e-08), q2);

  q2 = vf2setx_vf2_vf2_vf(q2, vmulsign_vf_vf_vf(vf2getx_vf_vf2(q2), d));
  q2 = vf2sety_vf2_vf2_vf(q2, vmulsign_vf_vf_vf(vf2gety_vf_vf2(q2), d));
  d = vabs_vf_vf(d);

  x = vcast_vf_f(-0.601564466953277587890625f);
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(2.8208892345428466796875f));
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(-5.532182216644287109375f));
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(5.898262500762939453125f));
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(-3.8095417022705078125f));
  x = vmla_vf_vf_vf_vf(x, d, vcast_vf_f(2.2241256237030029296875f));

  y = vmul_vf_vf_vf(x, x); y = vmul_vf_vf_vf(y, y); x = vsub_vf_vf_vf(x, vmul_vf_vf_vf(vmlanp_vf_vf_vf_vf(d, y, x), vcast_vf_f(-1.0 / 3.0)));

  z = x;

  u = dfmul_vf2_vf_vf(x, x);
  u = dfmul_vf2_vf2_vf2(u, u);
  u = dfmul_vf2_vf2_vf(u, d);
  u = dfadd2_vf2_vf2_vf(u, vneg_vf_vf(x));
  y = vadd_vf_vf_vf(vf2getx_vf_vf2(u), vf2gety_vf_vf2(u));

  y = vmul_vf_vf_vf(vmul_vf_vf_vf(vcast_vf_f(-2.0 / 3.0), y), z);
  v = dfadd2_vf2_vf2_vf(dfmul_vf2_vf_vf(z, z), y);
  v = dfmul_vf2_vf2_vf(v, d);
  v = dfmul_vf2_vf2_vf2(v, q2);
  z = vldexp2_vf_vf_vi2(vadd_vf_vf_vf(vf2getx_vf_vf2(v), vf2gety_vf_vf2(v)), vsub_vi2_vi2_vi2(qu, vcast_vi2_i(2048)));

  z = vsel_vf_vo_vf_vf(visinf_vo_vf(d), vmulsign_vf_vf_vf(vcast_vf_f(SLEEF_INFINITYf), vf2getx_vf_vf2(q2)), z);
  z = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(0)), vreinterpret_vf_vm(vsignbit_vm_vf(vf2getx_vf_vf2(q2))), z);

#if defined(ENABLE_AVX512F) || defined(ENABLE_AVX512FNOFMA)
  z = vsel_vf_vo_vf_vf(visinf_vo_vf(s), vmulsign_vf_vf_vf(vcast_vf_f(SLEEF_INFINITYf), s), z);
  z = vsel_vf_vo_vf_vf(veq_vo_vf_vf(s, vcast_vf_f(0)), vmulsign_vf_vf_vf(vcast_vf_f(0), s), z);
#endif

  return z;
}
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vfloat2 logkf(vfloat d) {
  vfloat2 x, x2;
  vfloat t, m;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(SLEEF_FLT_MIN));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f((float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32))), d);
  vint2 e = vilogb2k_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0f/0.75f)));
  m = vldexp3_vf_vf_vi2(d, vneg_vi2_vi2(e));
  e = vsel_vi2_vo_vi2_vi2(o, vsub_vi2_vi2_vi2(e, vcast_vi2_i(64)), e);
#else
  vfloat e = vgetexp_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0f/0.75f)));
  e = vsel_vf_vo_vf_vf(vispinf_vo_vf(e), vcast_vf_f(128.0f), e);
  m = vgetmant_vf_vf(d);
#endif

  x = dfdiv_vf2_vf2_vf2(dfadd2_vf2_vf_vf(vcast_vf_f(-1), m), dfadd2_vf2_vf_vf(vcast_vf_f(1), m));
  x2 = dfsqu_vf2_vf2(x);

  t = vcast_vf_f(0.240320354700088500976562);
  t = vmla_vf_vf_vf_vf(t, vf2getx_vf_vf2(x2), vcast_vf_f(0.285112679004669189453125));
  t = vmla_vf_vf_vf_vf(t, vf2getx_vf_vf2(x2), vcast_vf_f(0.400007992982864379882812));
  vfloat2 c = vcast_vf2_f_f(0.66666662693023681640625f, 3.69183861259614332084311e-09f);

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vfloat2 s = dfmul_vf2_vf2_vf(vcast_vf2_f_f(0.69314718246459960938f, -1.904654323148236017e-09f), vcast_vf_vi2(e));
#else
  vfloat2 s = dfmul_vf2_vf2_vf(vcast_vf2_f_f(0.69314718246459960938f, -1.904654323148236017e-09f), e);
#endif

  s = dfadd_vf2_vf2_vf2(s, dfscale_vf2_vf2_vf(x, vcast_vf_f(2)));
  s = dfadd_vf2_vf2_vf2(s, dfmul_vf2_vf2_vf2(dfmul_vf2_vf2_vf2(x2, x),
                                             dfadd2_vf2_vf2_vf2(dfmul_vf2_vf2_vf(x2, t), c)));
  return s;
}

static INLINE CONST VECTOR_CC vfloat logk3f(vfloat d) {
  vfloat x, x2, t, m;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(SLEEF_FLT_MIN));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f((float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32))), d);
  vint2 e = vilogb2k_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0f/0.75f)));
  m = vldexp3_vf_vf_vi2(d, vneg_vi2_vi2(e));
  e = vsel_vi2_vo_vi2_vi2(o, vsub_vi2_vi2_vi2(e, vcast_vi2_i(64)), e);
#else
  vfloat e = vgetexp_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0f/0.75f)));
  e = vsel_vf_vo_vf_vf(vispinf_vo_vf(e), vcast_vf_f(128.0f), e);
  m = vgetmant_vf_vf(d);
#endif

  x = vdiv_vf_vf_vf(vsub_vf_vf_vf(m, vcast_vf_f(1.0f)), vadd_vf_vf_vf(vcast_vf_f(1.0f), m));
  x2 = vmul_vf_vf_vf(x, x);

  t = vcast_vf_f(0.2392828464508056640625f);
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(0.28518211841583251953125f));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(0.400005877017974853515625f));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(0.666666686534881591796875f));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(2.0f));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  x = vmla_vf_vf_vf_vf(x, t, vmul_vf_vf_vf(vcast_vf_f(0.693147180559945286226764f), vcast_vf_vi2(e)));
#else
  x = vmla_vf_vf_vf_vf(x, t, vmul_vf_vf_vf(vcast_vf_f(0.693147180559945286226764f), e));
#endif

  return x;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xlogf_u1(vfloat d) {
  vfloat2 x;
  vfloat t, m, x2;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(SLEEF_FLT_MIN));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f((float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32))), d);
  vint2 e = vilogb2k_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0f/0.75f)));
  m = vldexp3_vf_vf_vi2(d, vneg_vi2_vi2(e));
  e = vsel_vi2_vo_vi2_vi2(o, vsub_vi2_vi2_vi2(e, vcast_vi2_i(64)), e);
  vfloat2 s = dfmul_vf2_vf2_vf(vcast_vf2_f_f(0.69314718246459960938f, -1.904654323148236017e-09f), vcast_vf_vi2(e));
#else
  vfloat e = vgetexp_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0f/0.75f)));
  e = vsel_vf_vo_vf_vf(vispinf_vo_vf(e), vcast_vf_f(128.0f), e);
  m = vgetmant_vf_vf(d);
  vfloat2 s = dfmul_vf2_vf2_vf(vcast_vf2_f_f(0.69314718246459960938f, -1.904654323148236017e-09f), e);
#endif

  x = dfdiv_vf2_vf2_vf2(dfadd2_vf2_vf_vf(vcast_vf_f(-1), m), dfadd2_vf2_vf_vf(vcast_vf_f(1), m));
  x2 = vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(x));

  t = vcast_vf_f(+0.3027294874e+0f);
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(+0.3996108174e+0f));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(+0.6666694880e+0f));

  s = dfadd_vf2_vf2_vf2(s, dfscale_vf2_vf2_vf(x, vcast_vf_f(2)));
  s = dfadd_vf2_vf2_vf(s, vmul_vf_vf_vf(vmul_vf_vf_vf(x2, vf2getx_vf_vf2(x)), t));

  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(s), vf2gety_vf_vf2(s));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  r = vsel_vf_vo_vf_vf(vispinf_vo_vf(d), vcast_vf_f(SLEEF_INFINITYf), r);
  r = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vlt_vo_vf_vf(d, vcast_vf_f(0)), visnan_vo_vf(d)), vcast_vf_f(SLEEF_NANf), r);
  r = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(0)), vcast_vf_f(-SLEEF_INFINITYf), r);
#else
  r = vfixup_vf_vf_vf_vi2_i(r, d, vcast_vi2_i((4 << (2*4)) | (3 << (4*4)) | (5 << (5*4)) | (2 << (6*4))), 0);
#endif

  return r;
}
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vfloat expkf(vfloat2 d) {
  vfloat u = vmul_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d)), vcast_vf_f(R_LN2f));
  vint2 q = vrint_vi2_vf(u);
  vfloat2 s, t;

  s = dfadd2_vf2_vf2_vf(d, vmul_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Uf)));
  s = dfadd2_vf2_vf2_vf(s, vmul_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Lf)));

  s = dfnormalize_vf2_vf2(s);

  u = vcast_vf_f(0.00136324646882712841033936f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.00836596917361021041870117f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.0416710823774337768554688f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.166665524244308471679688f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(0.499999850988388061523438f));

  t = dfadd_vf2_vf2_vf2(s, dfmul_vf2_vf2_vf(dfsqu_vf2_vf2(s), u));

  t = dfadd_vf2_vf_vf2(vcast_vf_f(1), t);
  u = vadd_vf_vf_vf(vf2getx_vf_vf2(t), vf2gety_vf_vf2(t));
  u = vldexp_vf_vf_vi2(u, q);

  u = vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(vf2getx_vf_vf2(d), vcast_vf_f(-104)), vreinterpret_vm_vf(u)));

  return u;
}

static INLINE CONST VECTOR_CC vfloat expk3f(vfloat d) {
  vint2 q = vrint_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(R_LN2f)));
  vfloat s, u;

  s = vmla_vf_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Uf), d);
  s = vmla_vf_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Lf), s);

  u = vcast_vf_f(0.000198527617612853646278381);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00139304355252534151077271));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.00833336077630519866943359));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.0416664853692054748535156));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.166666671633720397949219));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(0.5));

  u = vmla_vf_vf_vf_vf(vmul_vf_vf_vf(s, s), u, vadd_vf_vf_vf(s, vcast_vf_f(1.0f)));
  u = vldexp2_vf_vf_vi2(u, q);

  u = vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(d, vcast_vf_f(-104)), vreinterpret_vm_vf(u)));

  return u;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xpowf(vfloat x, vfloat y) {
#if 1
  vopmask yisint = vor_vo_vo_vo(veq_vo_vf_vf(vtruncate_vf_vf(y), y), vgt_vo_vf_vf(vabs_vf_vf(y), vcast_vf_f(1 << 24)));
  vopmask yisodd = vand_vo_vo_vo(vand_vo_vo_vo(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vtruncate_vi2_vf(y), vcast_vi2_i(1)), vcast_vi2_i(1)), yisint),
                                 vlt_vo_vf_vf(vabs_vf_vf(y), vcast_vf_f(1 << 24)));

#if defined(ENABLE_NEON32) || defined(ENABLE_NEON32VFPV4)
  yisodd = vandnot_vm_vo32_vm(visinf_vo_vf(y), yisodd);
#endif

  vfloat result = expkf(dfmul_vf2_vf2_vf(logkf(vabs_vf_vf(x)), y));

  result = vsel_vf_vo_vf_vf(visnan_vo_vf(result), vcast_vf_f(SLEEF_INFINITYf), result);

  result = vmul_vf_vf_vf(result,
                         vsel_vf_vo_vf_vf(vgt_vo_vf_vf(x, vcast_vf_f(0)),
                                          vcast_vf_f(1),
                                          vsel_vf_vo_vf_vf(yisint, vsel_vf_vo_vf_vf(yisodd, vcast_vf_f(-1.0f), vcast_vf_f(1)), vcast_vf_f(SLEEF_NANf))));

  vfloat efx = vmulsign_vf_vf_vf(vsub_vf_vf_vf(vabs_vf_vf(x), vcast_vf_f(1)), y);

  result = vsel_vf_vo_vf_vf(visinf_vo_vf(y),
                            vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(efx, vcast_vf_f(0.0f)),
                                                                  vreinterpret_vm_vf(vsel_vf_vo_vf_vf(veq_vo_vf_vf(efx, vcast_vf_f(0.0f)),
                                                                                                      vcast_vf_f(1.0f),
                                                                                                      vcast_vf_f(SLEEF_INFINITYf))))),
                            result);

  result = vsel_vf_vo_vf_vf(vor_vo_vo_vo(visinf_vo_vf(x), veq_vo_vf_vf(x, vcast_vf_f(0.0))),
                            vmulsign_vf_vf_vf(vsel_vf_vo_vf_vf(vxor_vo_vo_vo(vsignbit_vo_vf(y), veq_vo_vf_vf(x, vcast_vf_f(0.0f))),
                                                               vcast_vf_f(0), vcast_vf_f(SLEEF_INFINITYf)),
                                              vsel_vf_vo_vf_vf(yisodd, x, vcast_vf_f(1))), result);

  result = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visnan_vo_vf(x), visnan_vo_vf(y)), vreinterpret_vm_vf(result)));

  result = vsel_vf_vo_vf_vf(vor_vo_vo_vo(veq_vo_vf_vf(y, vcast_vf_f(0)), veq_vo_vf_vf(x, vcast_vf_f(1))), vcast_vf_f(1), result);

  return result;
#else
  return expkf(dfmul_vf2_vf2_vf(logkf(x), y));
#endif
}

EXPORT CONST VECTOR_CC vfloat xfastpowf_u3500(vfloat x, vfloat y) {
  vfloat result = expk3f(vmul_vf_vf_vf(logk3f(vabs_vf_vf(x)), y));
  vopmask yisint = vor_vo_vo_vo(veq_vo_vf_vf(vtruncate_vf_vf(y), y), vgt_vo_vf_vf(vabs_vf_vf(y), vcast_vf_f(1 << 24)));
  vopmask yisodd = vand_vo_vo_vo(vand_vo_vo_vo(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vtruncate_vi2_vf(y), vcast_vi2_i(1)), vcast_vi2_i(1)), yisint),
                                 vlt_vo_vf_vf(vabs_vf_vf(y), vcast_vf_f(1 << 24)));

  result = vsel_vf_vo_vf_vf(vand_vo_vo_vo(vsignbit_vo_vf(x), yisodd), vneg_vf_vf(result), result);

  result = vsel_vf_vo_vf_vf(veq_vo_vf_vf(x, vcast_vf_f(0)), vcast_vf_f(0), result);
  result = vsel_vf_vo_vf_vf(veq_vo_vf_vf(y, vcast_vf_f(0)), vcast_vf_f(1), result);

  return result;
}
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vfloat2 expk2f(vfloat2 d) {
  vfloat u = vmul_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d)), vcast_vf_f(R_LN2f));
  vint2 q = vrint_vi2_vf(u);
  vfloat2 s, t;

  s = dfadd2_vf2_vf2_vf(d, vmul_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Uf)));
  s = dfadd2_vf2_vf2_vf(s, vmul_vf_vf_vf(vcast_vf_vi2(q), vcast_vf_f(-L2Lf)));

  u = vcast_vf_f(+0.1980960224e-3f);
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(+0.1394256484e-2f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(+0.8333456703e-2f));
  u = vmla_vf_vf_vf_vf(u, vf2getx_vf_vf2(s), vcast_vf_f(+0.4166637361e-1f));

  t = dfadd2_vf2_vf2_vf(dfmul_vf2_vf2_vf(s, u), vcast_vf_f(+0.166666659414234244790680580464e+0f));
  t = dfadd2_vf2_vf2_vf(dfmul_vf2_vf2_vf2(s, t), vcast_vf_f(0.5));
  t = dfadd2_vf2_vf2_vf2(s, dfmul_vf2_vf2_vf2(dfsqu_vf2_vf2(s), t));

  t = dfadd_vf2_vf_vf2(vcast_vf_f(1), t);

  t = vf2setx_vf2_vf2_vf(t, vldexp2_vf_vf_vi2(vf2getx_vf_vf2(t), q));
  t = vf2sety_vf2_vf2_vf(t, vldexp2_vf_vf_vi2(vf2gety_vf_vf2(t), q));

  t = vf2setx_vf2_vf2_vf(t, vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(vf2getx_vf_vf2(d), vcast_vf_f(-104)), vreinterpret_vm_vf(vf2getx_vf_vf2(t)))));
  t = vf2sety_vf2_vf2_vf(t, vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(vf2getx_vf_vf2(d), vcast_vf_f(-104)), vreinterpret_vm_vf(vf2gety_vf_vf2(t)))));

  return t;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xsinhf(vfloat x) {
  vfloat y = vabs_vf_vf(x);
  vfloat2 d = expk2f(vcast_vf2_vf_vf(y, vcast_vf_f(0)));
  d = dfsub_vf2_vf2_vf2(d, dfrec_vf2_vf2(d));
  y = vmul_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d)), vcast_vf_f(0.5));

  y = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(89)),
                                    visnan_vo_vf(y)), vcast_vf_f(SLEEF_INFINITYf), y);
  y = vmulsign_vf_vf_vf(y, x);
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(visnan_vo_vf(x), vreinterpret_vm_vf(y)));

  return y;
}

EXPORT CONST VECTOR_CC vfloat xcoshf(vfloat x) {
  vfloat y = vabs_vf_vf(x);
  vfloat2 d = expk2f(vcast_vf2_vf_vf(y, vcast_vf_f(0)));
  d = dfadd_vf2_vf2_vf2(d, dfrec_vf2_vf2(d));
  y = vmul_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d)), vcast_vf_f(0.5));

  y = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(89)),
                                    visnan_vo_vf(y)), vcast_vf_f(SLEEF_INFINITYf), y);
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(visnan_vo_vf(x), vreinterpret_vm_vf(y)));

  return y;
}

EXPORT CONST VECTOR_CC vfloat xtanhf(vfloat x) {
  vfloat y = vabs_vf_vf(x);
  vfloat2 d = expk2f(vcast_vf2_vf_vf(y, vcast_vf_f(0)));
  vfloat2 e = dfrec_vf2_vf2(d);
  d = dfdiv_vf2_vf2_vf2(dfadd_vf2_vf2_vf2(d, dfneg_vf2_vf2(e)), dfadd_vf2_vf2_vf2(d, e));
  y = vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d));

  y = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(8.664339742f)),
                                    visnan_vo_vf(y)), vcast_vf_f(1.0f), y);
  y = vmulsign_vf_vf_vf(y, x);
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(visnan_vo_vf(x), vreinterpret_vm_vf(y)));

  return y;
}

EXPORT CONST VECTOR_CC vfloat xsinhf_u35(vfloat x) {
  vfloat e = expm1fk(vabs_vf_vf(x));
  vfloat y = vdiv_vf_vf_vf(vadd_vf_vf_vf(e, vcast_vf_f(2)), vadd_vf_vf_vf(e, vcast_vf_f(1)));
  y = vmul_vf_vf_vf(y, vmul_vf_vf_vf(vcast_vf_f(0.5f), e));

  y = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(88)),
                                    visnan_vo_vf(y)), vcast_vf_f(SLEEF_INFINITYf), y);
  y = vmulsign_vf_vf_vf(y, x);
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(visnan_vo_vf(x), vreinterpret_vm_vf(y)));

  return y;
}

EXPORT CONST VECTOR_CC vfloat xcoshf_u35(vfloat x) {
  vfloat e = xexpf(vabs_vf_vf(x));
  vfloat y = vmla_vf_vf_vf_vf(vcast_vf_f(0.5f), e, vdiv_vf_vf_vf(vcast_vf_f(0.5), e));

  y = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(88)),
                                    visnan_vo_vf(y)), vcast_vf_f(SLEEF_INFINITYf), y);
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(visnan_vo_vf(x), vreinterpret_vm_vf(y)));

  return y;
}

EXPORT CONST VECTOR_CC vfloat xtanhf_u35(vfloat x) {
  vfloat d = expm1fk(vmul_vf_vf_vf(vcast_vf_f(2), vabs_vf_vf(x)));
  vfloat y = vdiv_vf_vf_vf(d, vadd_vf_vf_vf(vcast_vf_f(2), d));

  y = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(8.664339742f)),
                                    visnan_vo_vf(y)), vcast_vf_f(1.0f), y);
  y = vmulsign_vf_vf_vf(y, x);
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(visnan_vo_vf(x), vreinterpret_vm_vf(y)));

  return y;
}
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vfloat2 logk2f(vfloat2 d) {
  vfloat2 x, x2, m, s;
  vfloat t;
  vint2 e;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  e = vilogbk_vi2_vf(vmul_vf_vf_vf(vf2getx_vf_vf2(d), vcast_vf_f(1.0f/0.75f)));
#else
  e = vrint_vi2_vf(vgetexp_vf_vf(vmul_vf_vf_vf(vf2getx_vf_vf2(d), vcast_vf_f(1.0f/0.75f))));
#endif
  m = dfscale_vf2_vf2_vf(d, vpow2i_vf_vi2(vneg_vi2_vi2(e)));

  x = dfdiv_vf2_vf2_vf2(dfadd2_vf2_vf2_vf(m, vcast_vf_f(-1)), dfadd2_vf2_vf2_vf(m, vcast_vf_f(1)));
  x2 = dfsqu_vf2_vf2(x);

  t = vcast_vf_f(0.2392828464508056640625f);
  t = vmla_vf_vf_vf_vf(t, vf2getx_vf_vf2(x2), vcast_vf_f(0.28518211841583251953125f));
  t = vmla_vf_vf_vf_vf(t, vf2getx_vf_vf2(x2), vcast_vf_f(0.400005877017974853515625f));
  t = vmla_vf_vf_vf_vf(t, vf2getx_vf_vf2(x2), vcast_vf_f(0.666666686534881591796875f));

  s = dfmul_vf2_vf2_vf(vcast_vf2_vf_vf(vcast_vf_f(0.69314718246459960938f), vcast_vf_f(-1.904654323148236017e-09f)), vcast_vf_vi2(e));
  s = dfadd_vf2_vf2_vf2(s, dfscale_vf2_vf2_vf(x, vcast_vf_f(2)));
  s = dfadd_vf2_vf2_vf2(s, dfmul_vf2_vf2_vf(dfmul_vf2_vf2_vf2(x2, x), t));

  return s;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xasinhf(vfloat x) {
  vfloat y = vabs_vf_vf(x);
  vopmask o = vgt_vo_vf_vf(y, vcast_vf_f(1));
  vfloat2 d;

  d = vsel_vf2_vo_vf2_vf2(o, dfrec_vf2_vf(x), vcast_vf2_vf_vf(y, vcast_vf_f(0)));
  d = dfsqrt_vf2_vf2(dfadd2_vf2_vf2_vf(dfsqu_vf2_vf2(d), vcast_vf_f(1)));
  d = vsel_vf2_vo_vf2_vf2(o, dfmul_vf2_vf2_vf(d, y), d);

  d = logk2f(dfnormalize_vf2_vf2(dfadd2_vf2_vf2_vf(d, x)));
  y = vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d));

  y = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(SQRT_FLT_MAX)),
                                    visnan_vo_vf(y)),
                       vmulsign_vf_vf_vf(vcast_vf_f(SLEEF_INFINITYf), x), y);
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(visnan_vo_vf(x), vreinterpret_vm_vf(y)));
  y = vsel_vf_vo_vf_vf(visnegzero_vo_vf(x), vcast_vf_f(-0.0), y);

  return y;
}

EXPORT CONST VECTOR_CC vfloat xacoshf(vfloat x) {
  vfloat2 d = logk2f(dfadd2_vf2_vf2_vf(dfmul_vf2_vf2_vf2(dfsqrt_vf2_vf2(dfadd2_vf2_vf_vf(x, vcast_vf_f(1))), dfsqrt_vf2_vf2(dfadd2_vf2_vf_vf(x, vcast_vf_f(-1)))), x));
  vfloat y = vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d));

  y = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vgt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(SQRT_FLT_MAX)),
                                    visnan_vo_vf(y)),
                       vcast_vf_f(SLEEF_INFINITYf), y);

  y = vreinterpret_vf_vm(vandnot_vm_vo32_vm(veq_vo_vf_vf(x, vcast_vf_f(1.0f)), vreinterpret_vm_vf(y)));

  y = vreinterpret_vf_vm(vor_vm_vo32_vm(vlt_vo_vf_vf(x, vcast_vf_f(1.0f)), vreinterpret_vm_vf(y)));
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(visnan_vo_vf(x), vreinterpret_vm_vf(y)));

  return y;
}

EXPORT CONST VECTOR_CC vfloat xatanhf(vfloat x) {
  vfloat y = vabs_vf_vf(x);
  vfloat2 d = logk2f(dfdiv_vf2_vf2_vf2(dfadd2_vf2_vf_vf(vcast_vf_f(1), y), dfadd2_vf2_vf_vf(vcast_vf_f(1), vneg_vf_vf(y))));
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(vgt_vo_vf_vf(y, vcast_vf_f(1.0)), vreinterpret_vm_vf(vsel_vf_vo_vf_vf(veq_vo_vf_vf(y, vcast_vf_f(1.0)), vcast_vf_f(SLEEF_INFINITYf), vmul_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d)), vcast_vf_f(0.5))))));

  y = vreinterpret_vf_vm(vor_vm_vo32_vm(vor_vo_vo_vo(visinf_vo_vf(x), visnan_vo_vf(y)), vreinterpret_vm_vf(y)));
  y = vmulsign_vf_vf_vf(y, x);
  y = vreinterpret_vf_vm(vor_vm_vo32_vm(visnan_vo_vf(x), vreinterpret_vm_vf(y)));

  return y;
}
#endif // #if !defined(DETERMINISTIC)

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xexp2f(vfloat d) {
  vfloat u = vrint_vf_vf(d), s;
  vint2 q = vrint_vi2_vf(u);

  s = vsub_vf_vf_vf(d, u);

  u = vcast_vf_f(+0.1535920892e-3);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.1339262701e-2));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.9618384764e-2));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.5550347269e-1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2402264476e+0));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.6931471825e+0));

#ifdef ENABLE_FMA_SP
  u = vfma_vf_vf_vf_vf(u, s, vcast_vf_f(1));
#else
  u = vf2getx_vf_vf2(dfnormalize_vf2_vf2(dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf_vf(u, s))));
#endif

  u = vldexp2_vf_vf_vi2(u, q);

  u = vsel_vf_vo_vf_vf(vge_vo_vf_vf(d, vcast_vf_f(128)), vcast_vf_f(SLEEF_INFINITY), u);
  u = vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(d, vcast_vf_f(-150)), vreinterpret_vm_vf(u)));

  return u;
}

EXPORT CONST VECTOR_CC vfloat xexp2f_u35(vfloat d) {
  vfloat u = vrint_vf_vf(d), s;
  vint2 q = vrint_vi2_vf(u);

  s = vsub_vf_vf_vf(d, u);

  u = vcast_vf_f(+0.1535920892e-3);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.1339262701e-2));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.9618384764e-2));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.5550347269e-1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2402264476e+0));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.6931471825e+0));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.1000000000e+1));

  u = vldexp2_vf_vf_vi2(u, q);

  u = vsel_vf_vo_vf_vf(vge_vo_vf_vf(d, vcast_vf_f(128)), vcast_vf_f(SLEEF_INFINITY), u);
  u = vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(d, vcast_vf_f(-150)), vreinterpret_vm_vf(u)));

  return u;
}

EXPORT CONST VECTOR_CC vfloat xexp10f(vfloat d) {
  vfloat u = vrint_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(LOG10_2))), s;
  vint2 q = vrint_vi2_vf(u);

  s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-L10Uf), d);
  s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-L10Lf), s);

  u = vcast_vf_f(+0.6802555919e-1);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2078080326e+0));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.5393903852e+0));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.1171245337e+1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2034678698e+1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2650949001e+1));
  vfloat2 x = dfadd_vf2_vf2_vf(vcast_vf2_f_f(2.3025851249694824219, -3.1705172516493593157e-08), vmul_vf_vf_vf(u, s));
  u = vf2getx_vf_vf2(dfnormalize_vf2_vf2(dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf2_vf(x, s))));

  u = vldexp2_vf_vf_vi2(u, q);

  u = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(d, vcast_vf_f(38.5318394191036238941387f)), vcast_vf_f(SLEEF_INFINITYf), u);
  u = vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(d, vcast_vf_f(-50)), vreinterpret_vm_vf(u)));

  return u;
}

EXPORT CONST VECTOR_CC vfloat xexp10f_u35(vfloat d) {
  vfloat u = vrint_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(LOG10_2))), s;
  vint2 q = vrint_vi2_vf(u);

  s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-L10Uf), d);
  s = vmla_vf_vf_vf_vf(u, vcast_vf_f(-L10Lf), s);

  u = vcast_vf_f(+0.2064004987e+0);
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.5417877436e+0));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.1171286821e+1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2034656048e+1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2650948763e+1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.2302585125e+1));
  u = vmla_vf_vf_vf_vf(u, s, vcast_vf_f(+0.1000000000e+1));

  u = vldexp2_vf_vf_vi2(u, q);

  u = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(d, vcast_vf_f(38.5318394191036238941387f)), vcast_vf_f(SLEEF_INFINITYf), u);
  u = vreinterpret_vf_vm(vandnot_vm_vo32_vm(vlt_vo_vf_vf(d, vcast_vf_f(-50)), vreinterpret_vm_vf(u)));

  return u;
}

EXPORT CONST VECTOR_CC vfloat xexpm1f(vfloat a) {
  vfloat2 d = dfadd2_vf2_vf2_vf(expk2f(vcast_vf2_vf_vf(a, vcast_vf_f(0))), vcast_vf_f(-1.0));
  vfloat x = vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d));
  x = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(a, vcast_vf_f(88.72283172607421875f)), vcast_vf_f(SLEEF_INFINITYf), x);
  x = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(a, vcast_vf_f(-16.635532333438687426013570f)), vcast_vf_f(-1), x);
  x = vsel_vf_vo_vf_vf(visnegzero_vo_vf(a), vcast_vf_f(-0.0f), x);
  return x;
}
#endif // #if !defined(DETERMINISTIC)

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xlog10f(vfloat d) {
  vfloat2 x;
  vfloat t, m, x2;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(SLEEF_FLT_MIN));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f((float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32))), d);
  vint2 e = vilogb2k_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0/0.75)));
  m = vldexp3_vf_vf_vi2(d, vneg_vi2_vi2(e));
  e = vsel_vi2_vo_vi2_vi2(o, vsub_vi2_vi2_vi2(e, vcast_vi2_i(64)), e);
#else
  vfloat e = vgetexp_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0/0.75)));
  e = vsel_vf_vo_vf_vf(vispinf_vo_vf(e), vcast_vf_f(128.0f), e);
  m = vgetmant_vf_vf(d);
#endif

  x = dfdiv_vf2_vf2_vf2(dfadd2_vf2_vf_vf(vcast_vf_f(-1), m), dfadd2_vf2_vf_vf(vcast_vf_f(1), m));
  x2 = vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(x));

  t = vcast_vf_f(+0.1314289868e+0);
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f( +0.1735493541e+0));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f( +0.2895309627e+0));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vfloat2 s = dfmul_vf2_vf2_vf(vcast_vf2_f_f(0.30103001, -1.432098889e-08), vcast_vf_vi2(e));
#else
  vfloat2 s = dfmul_vf2_vf2_vf(vcast_vf2_f_f(0.30103001, -1.432098889e-08), e);
#endif

  s = dfadd_vf2_vf2_vf2(s, dfmul_vf2_vf2_vf2(x, vcast_vf2_f_f(0.868588984, -2.170757285e-08)));
  s = dfadd_vf2_vf2_vf(s, vmul_vf_vf_vf(vmul_vf_vf_vf(x2, vf2getx_vf_vf2(x)), t));

  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(s), vf2gety_vf_vf2(s));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  r = vsel_vf_vo_vf_vf(vispinf_vo_vf(d), vcast_vf_f(SLEEF_INFINITY), r);
  r = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vlt_vo_vf_vf(d, vcast_vf_f(0)), visnan_vo_vf(d)), vcast_vf_f(SLEEF_NAN), r);
  r = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(0)), vcast_vf_f(-SLEEF_INFINITY), r);
#else
  r = vfixup_vf_vf_vf_vi2_i(r, d, vcast_vi2_i((4 << (2*4)) | (3 << (4*4)) | (5 << (5*4)) | (2 << (6*4))), 0);
#endif

  return r;
}

EXPORT CONST VECTOR_CC vfloat xlog2f(vfloat d) {
  vfloat2 x;
  vfloat t, m, x2;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(SLEEF_FLT_MIN));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f((float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32))), d);
  vint2 e = vilogb2k_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0/0.75)));
  m = vldexp3_vf_vf_vi2(d, vneg_vi2_vi2(e));
  e = vsel_vi2_vo_vi2_vi2(o, vsub_vi2_vi2_vi2(e, vcast_vi2_i(64)), e);
#else
  vfloat e = vgetexp_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0/0.75)));
  e = vsel_vf_vo_vf_vf(vispinf_vo_vf(e), vcast_vf_f(128.0f), e);
  m = vgetmant_vf_vf(d);
#endif

  x = dfdiv_vf2_vf2_vf2(dfadd2_vf2_vf_vf(vcast_vf_f(-1), m), dfadd2_vf2_vf_vf(vcast_vf_f(1), m));
  x2 = vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(x));

  t = vcast_vf_f(+0.4374550283e+0f);
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(+0.5764790177e+0f));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(+0.9618012905120f));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vfloat2 s = dfadd2_vf2_vf_vf2(vcast_vf_vi2(e),
                                dfmul_vf2_vf2_vf2(x, vcast_vf2_f_f(2.8853900432586669922, 3.2734474483568488616e-08)));
#else
  vfloat2 s = dfadd2_vf2_vf_vf2(e,
                                dfmul_vf2_vf2_vf2(x, vcast_vf2_f_f(2.8853900432586669922, 3.2734474483568488616e-08)));
#endif

  s = dfadd2_vf2_vf2_vf(s, vmul_vf_vf_vf(vmul_vf_vf_vf(x2, vf2getx_vf_vf2(x)), t));

  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(s), vf2gety_vf_vf2(s));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  r = vsel_vf_vo_vf_vf(vispinf_vo_vf(d), vcast_vf_f(SLEEF_INFINITY), r);
  r = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vlt_vo_vf_vf(d, vcast_vf_f(0)), visnan_vo_vf(d)), vcast_vf_f(SLEEF_NAN), r);
  r = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(0)), vcast_vf_f(-SLEEF_INFINITY), r);
#else
  r = vfixup_vf_vf_vf_vi2_i(r, d, vcast_vi2_i((4 << (2*4)) | (3 << (4*4)) | (5 << (5*4)) | (2 << (6*4))), 0);
#endif

  return r;
}

EXPORT CONST VECTOR_CC vfloat xlog2f_u35(vfloat d) {
  vfloat m, t, x, x2;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(SLEEF_FLT_MIN));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f((float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32))), d);
  vint2 e = vilogb2k_vi2_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0/0.75)));
  m = vldexp3_vf_vf_vi2(d, vneg_vi2_vi2(e));
  e = vsel_vi2_vo_vi2_vi2(o, vsub_vi2_vi2_vi2(e, vcast_vi2_i(64)), e);
#else
  vfloat e = vgetexp_vf_vf(vmul_vf_vf_vf(d, vcast_vf_f(1.0/0.75)));
  e = vsel_vf_vo_vf_vf(vispinf_vo_vf(e), vcast_vf_f(128.0f), e);
  m = vgetmant_vf_vf(d);
#endif

  x = vdiv_vf_vf_vf(vsub_vf_vf_vf(m, vcast_vf_f(1)), vadd_vf_vf_vf(m, vcast_vf_f(1)));
  x2 = vmul_vf_vf_vf(x, x);

  t = vcast_vf_f(+0.4374088347e+0);
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(+0.5764843822e+0));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(+0.9618024230e+0));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vfloat r = vmla_vf_vf_vf_vf(vmul_vf_vf_vf(x2, x), t,
                              vmla_vf_vf_vf_vf(x, vcast_vf_f(+0.2885390043e+1), vcast_vf_vi2(e)));

  r = vsel_vf_vo_vf_vf(vispinf_vo_vf(d), vcast_vf_f(SLEEF_INFINITY), r);
  r = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vlt_vo_vf_vf(d, vcast_vf_f(0)), visnan_vo_vf(d)), vcast_vf_f(SLEEF_NAN), r);
  r = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(0)), vcast_vf_f(-SLEEF_INFINITY), r);
#else
  vfloat r = vmla_vf_vf_vf_vf(vmul_vf_vf_vf(x2, x), t,
                              vmla_vf_vf_vf_vf(x, vcast_vf_f(+0.2885390043e+1), e));

  r = vfixup_vf_vf_vf_vi2_i(r, d, vcast_vi2_i((4 << (2*4)) | (3 << (4*4)) | (5 << (5*4)) | (2 << (6*4))), 0);
#endif

  return r;
}

EXPORT CONST VECTOR_CC vfloat xlog1pf(vfloat d) {
  vfloat2 x;
  vfloat t, m, x2;

  vfloat dp1 = vadd_vf_vf_vf(d, vcast_vf_f(1));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vf_vf(dp1, vcast_vf_f(SLEEF_FLT_MIN));
  dp1 = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(dp1, vcast_vf_f((float)(INT64_C(1) << 32) * (float)(INT64_C(1) << 32))), dp1);
  vint2 e = vilogb2k_vi2_vf(vmul_vf_vf_vf(dp1, vcast_vf_f(1.0f/0.75f)));
  t = vldexp3_vf_vf_vi2(vcast_vf_f(1), vneg_vi2_vi2(e));
  m = vmla_vf_vf_vf_vf(d, t, vsub_vf_vf_vf(t, vcast_vf_f(1)));
  e = vsel_vi2_vo_vi2_vi2(o, vsub_vi2_vi2_vi2(e, vcast_vi2_i(64)), e);
  vfloat2 s = dfmul_vf2_vf2_vf(vcast_vf2_f_f(0.69314718246459960938f, -1.904654323148236017e-09f), vcast_vf_vi2(e));
#else
  vfloat e = vgetexp_vf_vf(vmul_vf_vf_vf(dp1, vcast_vf_f(1.0f/0.75f)));
  e = vsel_vf_vo_vf_vf(vispinf_vo_vf(e), vcast_vf_f(128.0f), e);
  t = vldexp3_vf_vf_vi2(vcast_vf_f(1), vneg_vi2_vi2(vrint_vi2_vf(e)));
  m = vmla_vf_vf_vf_vf(d, t, vsub_vf_vf_vf(t, vcast_vf_f(1)));
  vfloat2 s = dfmul_vf2_vf2_vf(vcast_vf2_f_f(0.69314718246459960938f, -1.904654323148236017e-09f), e);
#endif

  x = dfdiv_vf2_vf2_vf2(vcast_vf2_vf_vf(m, vcast_vf_f(0)), dfadd_vf2_vf_vf(vcast_vf_f(2), m));
  x2 = vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(x));

  t = vcast_vf_f(+0.3027294874e+0f);
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(+0.3996108174e+0f));
  t = vmla_vf_vf_vf_vf(t, x2, vcast_vf_f(+0.6666694880e+0f));

  s = dfadd_vf2_vf2_vf2(s, dfscale_vf2_vf2_vf(x, vcast_vf_f(2)));
  s = dfadd_vf2_vf2_vf(s, vmul_vf_vf_vf(vmul_vf_vf_vf(x2, vf2getx_vf_vf2(x)), t));

  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(s), vf2gety_vf_vf2(s));

  r = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(d, vcast_vf_f(1e+38)), vcast_vf_f(SLEEF_INFINITYf), r);
  r = vreinterpret_vf_vm(vor_vm_vo32_vm(vgt_vo_vf_vf(vcast_vf_f(-1), d), vreinterpret_vm_vf(r)));
  r = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(-1)), vcast_vf_f(-SLEEF_INFINITYf), r);
  r = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), vcast_vf_f(-0.0f), r);

  return r;
}
#endif // #if !defined(DETERMINISTIC)

//

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xfabsf(vfloat x) { return vabs_vf_vf(x); }

EXPORT CONST VECTOR_CC vfloat xcopysignf(vfloat x, vfloat y) { return vcopysign_vf_vf_vf(x, y); }

EXPORT CONST VECTOR_CC vfloat xfmaxf(vfloat x, vfloat y) {
#if (defined(__x86_64__) || defined(__i386__)) && !defined(ENABLE_VECEXT) && !defined(ENABLE_PUREC)
  return vsel_vf_vo_vf_vf(visnan_vo_vf(y), x, vmax_vf_vf_vf(x, y));
#else
  return vsel_vf_vo_vf_vf(visnan_vo_vf(y), x, vsel_vf_vo_vf_vf(vgt_vo_vf_vf(x, y), x, y));
#endif
}

EXPORT CONST VECTOR_CC vfloat xfminf(vfloat x, vfloat y) {
#if (defined(__x86_64__) || defined(__i386__)) && !defined(ENABLE_VECEXT) && !defined(ENABLE_PUREC)
  return vsel_vf_vo_vf_vf(visnan_vo_vf(y), x, vmin_vf_vf_vf(x, y));
#else
  return vsel_vf_vo_vf_vf(visnan_vo_vf(y), x, vsel_vf_vo_vf_vf(vgt_vo_vf_vf(y, x), x, y));
#endif
}

EXPORT CONST VECTOR_CC vfloat xfdimf(vfloat x, vfloat y) {
  vfloat ret = vsub_vf_vf_vf(x, y);
  ret = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vlt_vo_vf_vf(ret, vcast_vf_f(0)), veq_vo_vf_vf(x, y)), vcast_vf_f(0), ret);
  return ret;
}

EXPORT CONST VECTOR_CC vfloat xtruncf(vfloat x) {
#ifdef FULL_FP_ROUNDING
  return vtruncate_vf_vf(x);
#else
  vfloat fr = vsub_vf_vf_vf(x, vcast_vf_vi2(vtruncate_vi2_vf(x)));
  return vsel_vf_vo_vf_vf(vor_vo_vo_vo(visinf_vo_vf(x), vge_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(INT64_C(1) << 23))), x, vcopysign_vf_vf_vf(vsub_vf_vf_vf(x, fr), x));
#endif
}

EXPORT CONST VECTOR_CC vfloat xfloorf(vfloat x) {
  vfloat fr = vsub_vf_vf_vf(x, vcast_vf_vi2(vtruncate_vi2_vf(x)));
  fr = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(fr, vcast_vf_f(0)), vadd_vf_vf_vf(fr, vcast_vf_f(1.0f)), fr);
  return vsel_vf_vo_vf_vf(vor_vo_vo_vo(visinf_vo_vf(x), vge_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(INT64_C(1) << 23))), x, vcopysign_vf_vf_vf(vsub_vf_vf_vf(x, fr), x));
}

EXPORT CONST VECTOR_CC vfloat xceilf(vfloat x) {
  vfloat fr = vsub_vf_vf_vf(x, vcast_vf_vi2(vtruncate_vi2_vf(x)));
  fr = vsel_vf_vo_vf_vf(vle_vo_vf_vf(fr, vcast_vf_f(0)), fr, vsub_vf_vf_vf(fr, vcast_vf_f(1.0f)));
  return vsel_vf_vo_vf_vf(vor_vo_vo_vo(visinf_vo_vf(x), vge_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(INT64_C(1) << 23))), x, vcopysign_vf_vf_vf(vsub_vf_vf_vf(x, fr), x));
}

EXPORT CONST VECTOR_CC vfloat xroundf(vfloat d) {
  vfloat x = vadd_vf_vf_vf(d, vcast_vf_f(0.5f));
  vfloat fr = vsub_vf_vf_vf(x, vcast_vf_vi2(vtruncate_vi2_vf(x)));
  x = vsel_vf_vo_vf_vf(vand_vo_vo_vo(vle_vo_vf_vf(x, vcast_vf_f(0)), veq_vo_vf_vf(fr, vcast_vf_f(0))), vsub_vf_vf_vf(x, vcast_vf_f(1.0f)), x);
  fr = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(fr, vcast_vf_f(0)), vadd_vf_vf_vf(fr, vcast_vf_f(1.0f)), fr);
  x = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(0.4999999701976776123f)), vcast_vf_f(0), x);
  return vsel_vf_vo_vf_vf(vor_vo_vo_vo(visinf_vo_vf(d), vge_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(INT64_C(1) << 23))), d, vcopysign_vf_vf_vf(vsub_vf_vf_vf(x, fr), d));
}

EXPORT CONST VECTOR_CC vfloat xrintf(vfloat d) {
#ifdef FULL_FP_ROUNDING
  return vrint_vf_vf(d);
#else
  vfloat c = vmulsign_vf_vf_vf(vcast_vf_f(1 << 23), d);
  return vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(1 << 23)),
                          d, vorsign_vf_vf_vf(vsub_vf_vf_vf(vadd_vf_vf_vf(d, c), c), d));
#endif
}

EXPORT CONST VECTOR_CC vfloat xfmaf(vfloat x, vfloat y, vfloat z) {
#ifdef ENABLE_FMA_SP
  return vfma_vf_vf_vf_vf(x, y, z);
#else
  vfloat h2 = vadd_vf_vf_vf(vmul_vf_vf_vf(x, y), z), q = vcast_vf_f(1);
  vopmask o = vlt_vo_vf_vf(vabs_vf_vf(h2), vcast_vf_f(1e-38f));
  {
    const float c0 = UINT64_C(1) << 25, c1 = c0 * c0, c2 = c1 * c1;
    x = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(x, vcast_vf_f(c1)), x);
    y = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(y, vcast_vf_f(c1)), y);
    z = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(z, vcast_vf_f(c2)), z);
    q = vsel_vf_vo_vf_vf(o, vcast_vf_f(1.0f / c2), q);
  }
  o = vgt_vo_vf_vf(vabs_vf_vf(h2), vcast_vf_f(1e+38f));
  {
    const float c0 = UINT64_C(1) << 25, c1 = c0 * c0, c2 = c1 * c1;
    x = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(x, vcast_vf_f(1.0f / c1)), x);
    y = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(y, vcast_vf_f(1.0f / c1)), y);
    z = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(z, vcast_vf_f(1.0f / c2)), z);
    q = vsel_vf_vo_vf_vf(o, vcast_vf_f(c2), q);
  }
  vfloat2 d = dfmul_vf2_vf_vf(x, y);
  d = dfadd2_vf2_vf2_vf(d, z);
  vfloat ret = vsel_vf_vo_vf_vf(vor_vo_vo_vo(veq_vo_vf_vf(x, vcast_vf_f(0)), veq_vo_vf_vf(y, vcast_vf_f(0))), z, vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d)));
  o = visinf_vo_vf(z);
  o = vandnot_vo_vo_vo(visinf_vo_vf(x), o);
  o = vandnot_vo_vo_vo(visnan_vo_vf(x), o);
  o = vandnot_vo_vo_vo(visinf_vo_vf(y), o);
  o = vandnot_vo_vo_vo(visnan_vo_vf(y), o);
  h2 = vsel_vf_vo_vf_vf(o, z, h2);

  o = vor_vo_vo_vo(visinf_vo_vf(h2), visnan_vo_vf(h2));

  return vsel_vf_vo_vf_vf(o, h2, vmul_vf_vf_vf(ret, q));
#endif
}
#endif // #if !defined(DETERMINISTIC)

SQRTFU05_FUNCATR VECTOR_CC vfloat xsqrtf_u05(vfloat d) {
#if defined(ENABLE_FMA_SP)
  vfloat q, w, x, y, z;

  d = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(d, vcast_vf_f(0)), vcast_vf_f(SLEEF_NANf), d);

  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(5.2939559203393770e-23f));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f(1.8889465931478580e+22f)), d);
  q = vsel_vf_vo_vf_vf(o, vcast_vf_f(7.2759576141834260e-12f), vcast_vf_f(1.0f));

  y = vreinterpret_vf_vi2(vsub_vi2_vi2_vi2(vcast_vi2_i(0x5f3759df), vsrl_vi2_vi2_i(vreinterpret_vi2_vf(d), 1)));

  x = vmul_vf_vf_vf(d, y);         w = vmul_vf_vf_vf(vcast_vf_f(0.5), y);
  y = vfmanp_vf_vf_vf_vf(x, w, vcast_vf_f(0.5));
  x = vfma_vf_vf_vf_vf(x, y, x);   w = vfma_vf_vf_vf_vf(w, y, w);
  y = vfmanp_vf_vf_vf_vf(x, w, vcast_vf_f(0.5));
  x = vfma_vf_vf_vf_vf(x, y, x);   w = vfma_vf_vf_vf_vf(w, y, w);

  y = vfmanp_vf_vf_vf_vf(x, w, vcast_vf_f(1.5));  w = vadd_vf_vf_vf(w, w);
  w = vmul_vf_vf_vf(w, y);
  x = vmul_vf_vf_vf(w, d);
  y = vfmapn_vf_vf_vf_vf(w, d, x); z = vfmanp_vf_vf_vf_vf(w, x, vcast_vf_f(1));

  z = vfmanp_vf_vf_vf_vf(w, y, z); w = vmul_vf_vf_vf(vcast_vf_f(0.5), x);
  w = vfma_vf_vf_vf_vf(w, z, y);
  w = vadd_vf_vf_vf(w, x);

  w = vmul_vf_vf_vf(w, q);

  w = vsel_vf_vo_vf_vf(vor_vo_vo_vo(veq_vo_vf_vf(d, vcast_vf_f(0)),
                                    veq_vo_vf_vf(d, vcast_vf_f(SLEEF_INFINITYf))), d, w);

  w = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(d, vcast_vf_f(0)), vcast_vf_f(SLEEF_NANf), w);

  return w;
#else
  vfloat q;
  vopmask o;

  d = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(d, vcast_vf_f(0)), vcast_vf_f(SLEEF_NANf), d);

  o = vlt_vo_vf_vf(d, vcast_vf_f(5.2939559203393770e-23f));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f(1.8889465931478580e+22f)), d);
  q = vsel_vf_vo_vf_vf(o, vcast_vf_f(7.2759576141834260e-12f*0.5f), vcast_vf_f(0.5f));

  o = vgt_vo_vf_vf(d, vcast_vf_f(1.8446744073709552e+19f));
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f(5.4210108624275220e-20f)), d);
  q = vsel_vf_vo_vf_vf(o, vcast_vf_f(4294967296.0f * 0.5f), q);

  vfloat x = vreinterpret_vf_vi2(vsub_vi2_vi2_vi2(vcast_vi2_i(0x5f375a86), vsrl_vi2_vi2_i(vreinterpret_vi2_vf(vadd_vf_vf_vf(d, vcast_vf_f(1e-45f))), 1)));

  x = vmul_vf_vf_vf(x, vsub_vf_vf_vf(vcast_vf_f(1.5f), vmul_vf_vf_vf(vmul_vf_vf_vf(vmul_vf_vf_vf(vcast_vf_f(0.5f), d), x), x)));
  x = vmul_vf_vf_vf(x, vsub_vf_vf_vf(vcast_vf_f(1.5f), vmul_vf_vf_vf(vmul_vf_vf_vf(vmul_vf_vf_vf(vcast_vf_f(0.5f), d), x), x)));
  x = vmul_vf_vf_vf(x, vsub_vf_vf_vf(vcast_vf_f(1.5f), vmul_vf_vf_vf(vmul_vf_vf_vf(vmul_vf_vf_vf(vcast_vf_f(0.5f), d), x), x)));
  x = vmul_vf_vf_vf(x, d);

  vfloat2 d2 = dfmul_vf2_vf2_vf2(dfadd2_vf2_vf_vf2(d, dfmul_vf2_vf_vf(x, x)), dfrec_vf2_vf(x));

  x = vmul_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(d2), vf2gety_vf_vf2(d2)), q);

  x = vsel_vf_vo_vf_vf(vispinf_vo_vf(d), vcast_vf_f(SLEEF_INFINITYf), x);
  x = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(0)), d, x);

  return x;
#endif
}

EXPORT CONST VECTOR_CC vfloat xsqrtf(vfloat d) {
#ifdef ACCURATE_SQRT
  return vsqrt_vf_vf(d);
#else
  // fall back to approximation if ACCURATE_SQRT is undefined
  return xsqrtf_u05(d);
#endif
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xhypotf_u05(vfloat x, vfloat y) {
  x = vabs_vf_vf(x);
  y = vabs_vf_vf(y);
  vfloat min = vmin_vf_vf_vf(x, y), n = min;
  vfloat max = vmax_vf_vf_vf(x, y), d = max;

  vopmask o = vlt_vo_vf_vf(max, vcast_vf_f(SLEEF_FLT_MIN));
  n = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(n, vcast_vf_f(UINT64_C(1) << 24)), n);
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f(UINT64_C(1) << 24)), d);

  vfloat2 t = dfdiv_vf2_vf2_vf2(vcast_vf2_vf_vf(n, vcast_vf_f(0)), vcast_vf2_vf_vf(d, vcast_vf_f(0)));
  t = dfmul_vf2_vf2_vf(dfsqrt_vf2_vf2(dfadd2_vf2_vf2_vf(dfsqu_vf2_vf2(t), vcast_vf_f(1))), max);
  vfloat ret = vadd_vf_vf_vf(vf2getx_vf_vf2(t), vf2gety_vf_vf2(t));
  ret = vsel_vf_vo_vf_vf(visnan_vo_vf(ret), vcast_vf_f(SLEEF_INFINITYf), ret);
  ret = vsel_vf_vo_vf_vf(veq_vo_vf_vf(min, vcast_vf_f(0)), max, ret);
  ret = vsel_vf_vo_vf_vf(vor_vo_vo_vo(visnan_vo_vf(x), visnan_vo_vf(y)), vcast_vf_f(SLEEF_NANf), ret);
  ret = vsel_vf_vo_vf_vf(vor_vo_vo_vo(veq_vo_vf_vf(x, vcast_vf_f(SLEEF_INFINITYf)), veq_vo_vf_vf(y, vcast_vf_f(SLEEF_INFINITYf))), vcast_vf_f(SLEEF_INFINITYf), ret);

  return ret;
}

EXPORT CONST VECTOR_CC vfloat xhypotf_u35(vfloat x, vfloat y) {
  x = vabs_vf_vf(x);
  y = vabs_vf_vf(y);
  vfloat min = vmin_vf_vf_vf(x, y);
  vfloat max = vmax_vf_vf_vf(x, y);

  vfloat t = vdiv_vf_vf_vf(min, max);
  vfloat ret = vmul_vf_vf_vf(max, vsqrt_vf_vf(vmla_vf_vf_vf_vf(t, t, vcast_vf_f(1))));
  ret = vsel_vf_vo_vf_vf(veq_vo_vf_vf(min, vcast_vf_f(0)), max, ret);
  ret = vsel_vf_vo_vf_vf(vor_vo_vo_vo(visnan_vo_vf(x), visnan_vo_vf(y)), vcast_vf_f(SLEEF_NANf), ret);
  ret = vsel_vf_vo_vf_vf(vor_vo_vo_vo(veq_vo_vf_vf(x, vcast_vf_f(SLEEF_INFINITYf)), veq_vo_vf_vf(y, vcast_vf_f(SLEEF_INFINITYf))), vcast_vf_f(SLEEF_INFINITYf), ret);

  return ret;
}

EXPORT CONST VECTOR_CC vfloat xnextafterf(vfloat x, vfloat y) {
  x = vsel_vf_vo_vf_vf(veq_vo_vf_vf(x, vcast_vf_f(0)), vmulsign_vf_vf_vf(vcast_vf_f(0), y), x);
  vint2 xi2 = vreinterpret_vi2_vf(x);
  vopmask c = vxor_vo_vo_vo(vsignbit_vo_vf(x), vge_vo_vf_vf(y, x));

  xi2 = vsel_vi2_vo_vi2_vi2(c, vsub_vi2_vi2_vi2(vcast_vi2_i(0), vxor_vi2_vi2_vi2(xi2, vcast_vi2_i((int)(1U << 31)))), xi2);

  xi2 = vsel_vi2_vo_vi2_vi2(vneq_vo_vf_vf(x, y), vsub_vi2_vi2_vi2(xi2, vcast_vi2_i(1)), xi2);

  xi2 = vsel_vi2_vo_vi2_vi2(c, vsub_vi2_vi2_vi2(vcast_vi2_i(0), vxor_vi2_vi2_vi2(xi2, vcast_vi2_i((int)(1U << 31)))), xi2);

  vfloat ret = vreinterpret_vf_vi2(xi2);

  ret = vsel_vf_vo_vf_vf(vand_vo_vo_vo(veq_vo_vf_vf(ret, vcast_vf_f(0)), vneq_vo_vf_vf(x, vcast_vf_f(0))),
                         vmulsign_vf_vf_vf(vcast_vf_f(0), x), ret);

  ret = vsel_vf_vo_vf_vf(vand_vo_vo_vo(veq_vo_vf_vf(x, vcast_vf_f(0)), veq_vo_vf_vf(y, vcast_vf_f(0))), y, ret);

  ret = vsel_vf_vo_vf_vf(vor_vo_vo_vo(visnan_vo_vf(x), visnan_vo_vf(y)), vcast_vf_f(SLEEF_NANf), ret);

  return ret;
}

EXPORT CONST VECTOR_CC vfloat xfrfrexpf(vfloat x) {
  x = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(SLEEF_FLT_MIN)), vmul_vf_vf_vf(x, vcast_vf_f(UINT64_C(1) << 30)), x);

  vmask xm = vreinterpret_vm_vf(x);
  xm = vand_vm_vm_vm(xm, vcast_vm_i_i(~0x7f800000U, ~0x7f800000U));
  xm = vor_vm_vm_vm (xm, vcast_vm_i_i( 0x3f000000U,  0x3f000000U));

  vfloat ret = vreinterpret_vf_vm(xm);

  ret = vsel_vf_vo_vf_vf(visinf_vo_vf(x), vmulsign_vf_vf_vf(vcast_vf_f(SLEEF_INFINITYf), x), ret);
  ret = vsel_vf_vo_vf_vf(veq_vo_vf_vf(x, vcast_vf_f(0)), x, ret);

  return ret;
}
#endif // #if !defined(DETERMINISTIC)

EXPORT CONST VECTOR_CC vint2 xexpfrexpf(vfloat x) {
  /*
  x = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(SLEEF_FLT_MIN)), vmul_vf_vf_vf(x, vcast_vf_f(UINT64_C(1) << 63)), x);

  vint ret = vcastu_vi_vi2(vreinterpret_vi2_vf(x));
  ret = vsub_vi_vi_vi(vand_vi_vi_vi(vsrl_vi_vi_i(ret, 20), vcast_vi_i(0x7ff)), vcast_vi_i(0x3fe));

  ret = vsel_vi_vo_vi_vi(vor_vo_vo_vo(vor_vo_vo_vo(veq_vo_vf_vf(x, vcast_vf_f(0)), visnan_vo_vf(x)), visinf_vo_vf(x)), vcast_vi_i(0), ret);

  return ret;
  */
  return vcast_vi2_i(0);
}

static INLINE CONST VECTOR_CC vfloat vtoward0_vf_vf(vfloat x) {
  vfloat t = vreinterpret_vf_vi2(vsub_vi2_vi2_vi2(vreinterpret_vi2_vf(x), vcast_vi2_i(1)));
  return vsel_vf_vo_vf_vf(veq_vo_vf_vf(x, vcast_vf_f(0)), vcast_vf_f(0), t);
}

static INLINE CONST VECTOR_CC vfloat vptrunc_vf_vf(vfloat x) {
#ifdef FULL_FP_ROUNDING
  return vtruncate_vf_vf(x);
#else
  vfloat fr = vsub_vf_vf_vf(x, vcast_vf_vi2(vtruncate_vi2_vf(x)));
  return vsel_vf_vo_vf_vf(vge_vo_vf_vf(vabs_vf_vf(x), vcast_vf_f(INT64_C(1) << 23)), x, vsub_vf_vf_vf(x, fr));
#endif
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xfmodf(vfloat x, vfloat y) {
  vfloat nu = vabs_vf_vf(x), de = vabs_vf_vf(y), s = vcast_vf_f(1), q;
  vopmask o = vlt_vo_vf_vf(de, vcast_vf_f(SLEEF_FLT_MIN));
  nu = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(nu, vcast_vf_f(UINT64_C(1) << 25)), nu);
  de = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(de, vcast_vf_f(UINT64_C(1) << 25)), de);
  s  = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(s , vcast_vf_f(1.0f / (UINT64_C(1) << 25))), s);
  vfloat rde = vtoward0_vf_vf(vrec_vf_vf(de));
#if defined(ENABLE_NEON32) || defined(ENABLE_NEON32VFPV4)
  rde = vtoward0_vf_vf(rde);
#endif
  vfloat2 r = vcast_vf2_vf_vf(nu, vcast_vf_f(0));

  for(int i=0;i<8;i++) { // ceil(log2(FLT_MAX) / 22)+1
    q = vptrunc_vf_vf(vmul_vf_vf_vf(vtoward0_vf_vf(vf2getx_vf_vf2(r)), rde));
    q = vsel_vf_vo_vf_vf(vand_vo_vo_vo(vgt_vo_vf_vf(vmul_vf_vf_vf(vcast_vf_f(3), de), vf2getx_vf_vf2(r)),
                                       vge_vo_vf_vf(vf2getx_vf_vf2(r), de)),
                         vcast_vf_f(2), q);
    q = vsel_vf_vo_vf_vf(vand_vo_vo_vo(vgt_vo_vf_vf(vmul_vf_vf_vf(vcast_vf_f(2), de), vf2getx_vf_vf2(r)),
                                       vge_vo_vf_vf(vf2getx_vf_vf2(r), de)),
                         vcast_vf_f(1), q);
    r = dfnormalize_vf2_vf2(dfadd2_vf2_vf2_vf2(r, dfmul_vf2_vf_vf(vptrunc_vf_vf(q), vneg_vf_vf(de))));
    if (vtestallones_i_vo32(vlt_vo_vf_vf(vf2getx_vf_vf2(r), de))) break;
  }

  vfloat ret = vmul_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(r), vf2gety_vf_vf2(r)), s);
  ret = vsel_vf_vo_vf_vf(veq_vo_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(r), vf2gety_vf_vf2(r)), de), vcast_vf_f(0), ret);

  ret = vmulsign_vf_vf_vf(ret, x);

  ret = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(nu, de), x, ret);
  ret = vsel_vf_vo_vf_vf(veq_vo_vf_vf(de, vcast_vf_f(0)), vcast_vf_f(SLEEF_NANf), ret);

  return ret;
}

static INLINE CONST VECTOR_CC vfloat vrintfk2_vf_vf(vfloat d) {
#ifdef FULL_FP_ROUNDING
  return vrint_vf_vf(d);
#else
  vfloat c = vmulsign_vf_vf_vf(vcast_vf_f(1 << 23), d);
  return vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(1 << 23)),
                          d, vorsign_vf_vf_vf(vsub_vf_vf_vf(vadd_vf_vf_vf(d, c), c), d));
#endif
}

EXPORT CONST VECTOR_CC vfloat xremainderf(vfloat x, vfloat y) {
  vfloat n = vabs_vf_vf(x), d = vabs_vf_vf(y), s = vcast_vf_f(1), q;
  vopmask o = vlt_vo_vf_vf(d, vcast_vf_f(SLEEF_FLT_MIN*2));
  n = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(n, vcast_vf_f(UINT64_C(1) << 25)), n);
  d = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(d, vcast_vf_f(UINT64_C(1) << 25)), d);
  s  = vsel_vf_vo_vf_vf(o, vmul_vf_vf_vf(s , vcast_vf_f(1.0f / (UINT64_C(1) << 25))), s);
  vfloat2 r = vcast_vf2_vf_vf(n, vcast_vf_f(0));
  vfloat rd = vrec_vf_vf(d);
  vopmask qisodd = vneq_vo_vf_vf(vcast_vf_f(0), vcast_vf_f(0));

  for(int i=0;i<8;i++) { // ceil(log2(FLT_MAX) / 22)+1
    q = vrintfk2_vf_vf(vmul_vf_vf_vf(vf2getx_vf_vf2(r), rd));
    q = vsel_vf_vo_vf_vf(vlt_vo_vf_vf(vabs_vf_vf(vf2getx_vf_vf2(r)), vmul_vf_vf_vf(d, vcast_vf_f(1.5f))), vmulsign_vf_vf_vf(vcast_vf_f(1.0f), vf2getx_vf_vf2(r)), q);
    q = vsel_vf_vo_vf_vf(vor_vo_vo_vo(vlt_vo_vf_vf(vabs_vf_vf(vf2getx_vf_vf2(r)), vmul_vf_vf_vf(d, vcast_vf_f(0.5f))),
                                      vandnot_vo_vo_vo(qisodd, veq_vo_vf_vf(vabs_vf_vf(vf2getx_vf_vf2(r)), vmul_vf_vf_vf(d, vcast_vf_f(0.5f))))),
                         vcast_vf_f(0.0), q);
    if (vtestallones_i_vo32(veq_vo_vf_vf(q, vcast_vf_f(0)))) break;
    q = vsel_vf_vo_vf_vf(visinf_vo_vf(vmul_vf_vf_vf(q, vneg_vf_vf(d))), vadd_vf_vf_vf(q, vmulsign_vf_vf_vf(vcast_vf_f(-1), vf2getx_vf_vf2(r))), q);
    qisodd = vxor_vo_vo_vo(qisodd, vand_vo_vo_vo(veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vtruncate_vi2_vf(q), vcast_vi2_i(1)), vcast_vi2_i(1)),
                                                 vlt_vo_vf_vf(vabs_vf_vf(q), vcast_vf_f(1 << 24))));
    r = dfnormalize_vf2_vf2(dfadd2_vf2_vf2_vf2(r, dfmul_vf2_vf_vf(q, vneg_vf_vf(d))));
  }

  vfloat ret = vmul_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(r), vf2gety_vf_vf2(r)), s);
  ret = vmulsign_vf_vf_vf(ret, x);
  ret = vsel_vf_vo_vf_vf(visinf_vo_vf(y), vsel_vf_vo_vf_vf(visinf_vo_vf(x), vcast_vf_f(SLEEF_NANf), x), ret);
  ret = vsel_vf_vo_vf_vf(veq_vo_vf_vf(d, vcast_vf_f(0)), vcast_vf_f(SLEEF_NANf), ret);
  return ret;
}
#endif // #if !defined(DETERMINISTIC)

//

static INLINE CONST VECTOR_CC vfloat2 sinpifk(vfloat d) {
  vopmask o;
  vfloat u, s, t;
  vfloat2 x, s2;

  u = vmul_vf_vf_vf(d, vcast_vf_f(4.0));
  vint2 q = vtruncate_vi2_vf(u);
  q = vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vxor_vi2_vi2_vi2(vsrl_vi2_vi2_i(q, 31), vcast_vi2_i(1))), vcast_vi2_i(~1));
  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(2));

  s = vsub_vf_vf_vf(u, vcast_vf_vi2(q));
  t = s;
  s = vmul_vf_vf_vf(s, s);
  s2 = dfmul_vf2_vf_vf(t, t);

  //

  u = vsel_vf_vo_f_f(o, -0.2430611801e-7f, +0.3093842054e-6f);
  u = vmla_vf_vf_vf_vf(u, s, vsel_vf_vo_f_f(o, +0.3590577080e-5f, -0.3657307388e-4f));
  u = vmla_vf_vf_vf_vf(u, s, vsel_vf_vo_f_f(o, -0.3259917721e-3f, +0.2490393585e-2f));
  x = dfadd2_vf2_vf_vf2(vmul_vf_vf_vf(u, s),
                        vsel_vf2_vo_f_f_f_f(o, 0.015854343771934509277, 4.4940051354032242811e-10,
                                            -0.080745510756969451904, -1.3373665339076936258e-09));
  x = dfadd2_vf2_vf2_vf2(dfmul_vf2_vf2_vf2(s2, x),
                         vsel_vf2_vo_f_f_f_f(o, -0.30842512845993041992, -9.0728339030733922277e-09,
                                             0.78539818525314331055, -2.1857338617566484855e-08));

  x = dfmul_vf2_vf2_vf2(x, vsel_vf2_vo_vf2_vf2(o, s2, vcast_vf2_vf_vf(t, vcast_vf_f(0))));
  x = vsel_vf2_vo_vf2_vf2(o, dfadd2_vf2_vf2_vf(x, vcast_vf_f(1)), x);

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(4)), vcast_vi2_i(4));
  x = vf2setx_vf2_vf2_vf(x, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2getx_vf_vf2(x)))));
  x = vf2sety_vf2_vf2_vf(x, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2gety_vf_vf2(x)))));

  return x;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xsinpif_u05(vfloat d) {
  vfloat2 x = sinpifk(d);
  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  r = vsel_vf_vo_vf_vf(visnegzero_vo_vf(d), vcast_vf_f(-0.0), r);
  r = vreinterpret_vf_vm(vandnot_vm_vo32_vm(vgt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX4f)), vreinterpret_vm_vf(r)));
  r = vreinterpret_vf_vm(vor_vm_vo32_vm(visinf_vo_vf(d), vreinterpret_vm_vf(r)));

  return r;
}
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vfloat2 cospifk(vfloat d) {
  vopmask o;
  vfloat u, s, t;
  vfloat2 x, s2;

  u = vmul_vf_vf_vf(d, vcast_vf_f(4.0));
  vint2 q = vtruncate_vi2_vf(u);
  q = vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vxor_vi2_vi2_vi2(vsrl_vi2_vi2_i(q, 31), vcast_vi2_i(1))), vcast_vi2_i(~1));
  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(0));

  s = vsub_vf_vf_vf(u, vcast_vf_vi2(q));
  t = s;
  s = vmul_vf_vf_vf(s, s);
  s2 = dfmul_vf2_vf_vf(t, t);

  //

  u = vsel_vf_vo_f_f(o, -0.2430611801e-7f, +0.3093842054e-6f);
  u = vmla_vf_vf_vf_vf(u, s, vsel_vf_vo_f_f(o, +0.3590577080e-5f, -0.3657307388e-4f));
  u = vmla_vf_vf_vf_vf(u, s, vsel_vf_vo_f_f(o, -0.3259917721e-3f, +0.2490393585e-2f));
  x = dfadd2_vf2_vf_vf2(vmul_vf_vf_vf(u, s),
                        vsel_vf2_vo_f_f_f_f(o, 0.015854343771934509277, 4.4940051354032242811e-10,
                                            -0.080745510756969451904, -1.3373665339076936258e-09));
  x = dfadd2_vf2_vf2_vf2(dfmul_vf2_vf2_vf2(s2, x),
                         vsel_vf2_vo_f_f_f_f(o, -0.30842512845993041992, -9.0728339030733922277e-09,
                                             0.78539818525314331055, -2.1857338617566484855e-08));

  x = dfmul_vf2_vf2_vf2(x, vsel_vf2_vo_vf2_vf2(o, s2, vcast_vf2_vf_vf(t, vcast_vf_f(0))));
  x = vsel_vf2_vo_vf2_vf2(o, dfadd2_vf2_vf2_vf(x, vcast_vf_f(1)), x);

  o = veq_vo_vi2_vi2(vand_vi2_vi2_vi2(vadd_vi2_vi2_vi2(q, vcast_vi2_i(2)), vcast_vi2_i(4)), vcast_vi2_i(4));
  x = vf2setx_vf2_vf2_vf(x, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2getx_vf_vf2(x)))));
  x = vf2sety_vf2_vf2_vf(x, vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vo32_vm(o, vreinterpret_vm_vf(vcast_vf_f(-0.0))), vreinterpret_vm_vf(vf2gety_vf_vf2(x)))));

  return x;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xcospif_u05(vfloat d) {
  vfloat2 x = cospifk(d);
  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x));

  r = vsel_vf_vo_vf_vf(vgt_vo_vf_vf(vabs_vf_vf(d), vcast_vf_f(TRIGRANGEMAX4f)), vcast_vf_f(1), r);
  r = vreinterpret_vf_vm(vor_vm_vo32_vm(visinf_vo_vf(d), vreinterpret_vm_vf(r)));

  return r;
}
#endif // #if !defined(DETERMINISTIC)

#if !(defined(ENABLE_SVE) || defined(ENABLE_SVENOFMA) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
  typedef struct {
    vfloat2 a, b;
  } df2;

static df2 df2setab_df2_vf2_vf2(vfloat2 a, vfloat2 b) {
  df2 r = { a, b };
  return r;
}
static vfloat2 df2geta_vf2_df2(df2 d) { return d.a; }
static vfloat2 df2getb_vf2_df2(df2 d) { return d.b; }
#endif

/* TODO AArch64: potential optimization by using `vfmad_lane_f64` */
static CONST df2 gammafk(vfloat a) {
  vfloat2 clc = vcast_vf2_f_f(0, 0), clln = vcast_vf2_f_f(1, 0), clld = vcast_vf2_f_f(1, 0);
  vfloat2 x, y, z;
  vfloat t, u;

  vopmask otiny = vlt_vo_vf_vf(vabs_vf_vf(a), vcast_vf_f(1e-30f)), oref = vlt_vo_vf_vf(a, vcast_vf_f(0.5));

  x = vsel_vf2_vo_vf2_vf2(otiny, vcast_vf2_f_f(0, 0),
                          vsel_vf2_vo_vf2_vf2(oref, dfadd2_vf2_vf_vf(vcast_vf_f(1), vneg_vf_vf(a)),
                                              vcast_vf2_vf_vf(a, vcast_vf_f(0))));

  vopmask o0 = vand_vo_vo_vo(vle_vo_vf_vf(vcast_vf_f(0.5), vf2getx_vf_vf2(x)), vle_vo_vf_vf(vf2getx_vf_vf2(x), vcast_vf_f(1.2)));
  vopmask o2 = vle_vo_vf_vf(vcast_vf_f(2.3), vf2getx_vf_vf2(x));

  y = dfnormalize_vf2_vf2(dfmul_vf2_vf2_vf2(dfadd2_vf2_vf2_vf(x, vcast_vf_f(1)), x));
  y = dfnormalize_vf2_vf2(dfmul_vf2_vf2_vf2(dfadd2_vf2_vf2_vf(x, vcast_vf_f(2)), y));

  vopmask o = vand_vo_vo_vo(o2, vle_vo_vf_vf(vf2getx_vf_vf2(x), vcast_vf_f(7)));
  clln = vsel_vf2_vo_vf2_vf2(o, y, clln);

  x = vsel_vf2_vo_vf2_vf2(o, dfadd2_vf2_vf2_vf(x, vcast_vf_f(3)), x);
  t = vsel_vf_vo_vf_vf(o2, vrec_vf_vf(vf2getx_vf_vf2(x)), vf2getx_vf_vf2(dfnormalize_vf2_vf2(dfadd2_vf2_vf2_vf(x, vsel_vf_vo_f_f(o0, -1, -2)))));

  u = vsel_vf_vo_vo_f_f_f(o2, o0, +0.000839498720672087279971000786, +0.9435157776e+0f, +0.1102489550e-3f);
  u = vmla_vf_vf_vf_vf(u, t, vsel_vf_vo_vo_f_f_f(o2, o0, -5.17179090826059219329394422e-05, +0.8670063615e+0f, +0.8160019934e-4f));
  u = vmla_vf_vf_vf_vf(u, t, vsel_vf_vo_vo_f_f_f(o2, o0, -0.000592166437353693882857342347, +0.4826702476e+0f, +0.1528468856e-3f));
  u = vmla_vf_vf_vf_vf(u, t, vsel_vf_vo_vo_f_f_f(o2, o0, +6.97281375836585777403743539e-05, -0.8855129778e-1f, -0.2355068718e-3f));
  u = vmla_vf_vf_vf_vf(u, t, vsel_vf_vo_vo_f_f_f(o2, o0, +0.000784039221720066627493314301, +0.1013825238e+0f, +0.4962242092e-3f));
  u = vmla_vf_vf_vf_vf(u, t, vsel_vf_vo_vo_f_f_f(o2, o0, -0.000229472093621399176949318732, -0.1493408978e+0f, -0.1193488017e-2f));
  u = vmla_vf_vf_vf_vf(u, t, vsel_vf_vo_vo_f_f_f(o2, o0, -0.002681327160493827160473958490, +0.1697509140e+0f, +0.2891599433e-2f));
  u = vmla_vf_vf_vf_vf(u, t, vsel_vf_vo_vo_f_f_f(o2, o0, +0.003472222222222222222175164840, -0.2072454542e+0f, -0.7385451812e-2f));
  u = vmla_vf_vf_vf_vf(u, t, vsel_vf_vo_vo_f_f_f(o2, o0, +0.083333333333333333335592087900, +0.2705872357e+0f, +0.2058077045e-1f));

  y = dfmul_vf2_vf2_vf2(dfadd2_vf2_vf2_vf(x, vcast_vf_f(-0.5)), logk2f(x));
  y = dfadd2_vf2_vf2_vf2(y, dfneg_vf2_vf2(x));
  y = dfadd2_vf2_vf2_vf2(y, vcast_vf2_d(0.91893853320467278056)); // 0.5*log(2*M_PI)

  z = dfadd2_vf2_vf2_vf(dfmul_vf2_vf_vf (u, t), vsel_vf_vo_f_f(o0, -0.400686534596170958447352690395e+0f, -0.673523028297382446749257758235e-1f));
  z = dfadd2_vf2_vf2_vf(dfmul_vf2_vf2_vf(z, t), vsel_vf_vo_f_f(o0, +0.822466960142643054450325495997e+0f, +0.322467033928981157743538726901e+0f));
  z = dfadd2_vf2_vf2_vf(dfmul_vf2_vf2_vf(z, t), vsel_vf_vo_f_f(o0, -0.577215665946766039837398973297e+0f, +0.422784335087484338986941629852e+0f));
  z = dfmul_vf2_vf2_vf(z, t);

  clc = vsel_vf2_vo_vf2_vf2(o2, y, z);

  clld = vsel_vf2_vo_vf2_vf2(o2, dfadd2_vf2_vf2_vf(dfmul_vf2_vf_vf(u, t), vcast_vf_f(1)), clld);

  y = clln;

  clc = vsel_vf2_vo_vf2_vf2(otiny, vcast_vf2_d(41.58883083359671856503), // log(2^60)
                            vsel_vf2_vo_vf2_vf2(oref, dfadd2_vf2_vf2_vf2(vcast_vf2_d(1.1447298858494001639), dfneg_vf2_vf2(clc)), clc)); // log(M_PI)
  clln = vsel_vf2_vo_vf2_vf2(otiny, vcast_vf2_f_f(1, 0), vsel_vf2_vo_vf2_vf2(oref, clln, clld));

  if (!vtestallones_i_vo32(vnot_vo32_vo32(oref))) {
    t = vsub_vf_vf_vf(a, vmul_vf_vf_vf(vcast_vf_f(INT64_C(1) << 12), vcast_vf_vi2(vtruncate_vi2_vf(vmul_vf_vf_vf(a, vcast_vf_f(1.0 / (INT64_C(1) << 12)))))));
    x = dfmul_vf2_vf2_vf2(clld, sinpifk(t));
  }

  clld = vsel_vf2_vo_vf2_vf2(otiny, vcast_vf2_vf_vf(vmul_vf_vf_vf(a, vcast_vf_f((INT64_C(1) << 30)*(float)(INT64_C(1) << 30))), vcast_vf_f(0)),
                             vsel_vf2_vo_vf2_vf2(oref, x, y));

  return df2setab_df2_vf2_vf2(clc, dfdiv_vf2_vf2_vf2(clln, clld));
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vfloat xtgammaf_u1(vfloat a) {
  df2 d = gammafk(a);
  vfloat2 y = dfmul_vf2_vf2_vf2(expk2f(df2geta_vf2_df2(d)), df2getb_vf2_df2(d));
  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(y), vf2gety_vf_vf2(y));
  vopmask o;

  o = vor_vo_vo_vo(vor_vo_vo_vo(veq_vo_vf_vf(a, vcast_vf_f(-SLEEF_INFINITYf)),
                                vand_vo_vo_vo(vlt_vo_vf_vf(a, vcast_vf_f(0)), visint_vo_vf(a))),
                   vand_vo_vo_vo(vand_vo_vo_vo(visnumber_vo_vf(a), vlt_vo_vf_vf(a, vcast_vf_f(0))), visnan_vo_vf(r)));
  r = vsel_vf_vo_vf_vf(o, vcast_vf_f(SLEEF_NANf), r);

  o = vand_vo_vo_vo(vand_vo_vo_vo(vor_vo_vo_vo(veq_vo_vf_vf(a, vcast_vf_f(SLEEF_INFINITYf)), visnumber_vo_vf(a)),
                                  vge_vo_vf_vf(a, vcast_vf_f(-SLEEF_FLT_MIN))),
                    vor_vo_vo_vo(vor_vo_vo_vo(veq_vo_vf_vf(a, vcast_vf_f(0)), vgt_vo_vf_vf(a, vcast_vf_f(36))), visnan_vo_vf(r)));
  r = vsel_vf_vo_vf_vf(o, vmulsign_vf_vf_vf(vcast_vf_f(SLEEF_INFINITYf), a), r);

  return r;
}

EXPORT CONST VECTOR_CC vfloat xlgammaf_u1(vfloat a) {
  df2 d = gammafk(a);
  vfloat2 y = dfadd2_vf2_vf2_vf2(df2geta_vf2_df2(d), logk2f(dfabs_vf2_vf2(df2getb_vf2_df2(d))));
  vfloat r = vadd_vf_vf_vf(vf2getx_vf_vf2(y), vf2gety_vf_vf2(y));
  vopmask o;

  o = vor_vo_vo_vo(visinf_vo_vf(a),
                   vor_vo_vo_vo(vand_vo_vo_vo(vle_vo_vf_vf(a, vcast_vf_f(0)), visint_vo_vf(a)),
                                vand_vo_vo_vo(visnumber_vo_vf(a), visnan_vo_vf(r))));
  r = vsel_vf_vo_vf_vf(o, vcast_vf_f(SLEEF_INFINITYf), r);

  return r;
}

static INLINE CONST vfloat2 dfmla_vf2_vf_vf2_vf2(vfloat x, vfloat2 y, vfloat2 z) {
  return dfadd_vf2_vf2_vf2(z, dfmul_vf2_vf2_vf(y, x));
}

static INLINE CONST vfloat2 poly2df_b(vfloat x, vfloat2 c1, vfloat2 c0) { return dfmla_vf2_vf_vf2_vf2(x, c1, c0); }
static INLINE CONST vfloat2 poly2df(vfloat x, vfloat c1, vfloat2 c0) { return dfmla_vf2_vf_vf2_vf2(x, vcast_vf2_vf_vf(c1, vcast_vf_f(0)), c0); }
static INLINE CONST vfloat2 poly4df(vfloat x, vfloat c3, vfloat2 c2, vfloat2 c1, vfloat2 c0) {
  return dfmla_vf2_vf_vf2_vf2(vmul_vf_vf_vf(x, x), poly2df(x, c3, c2), poly2df_b(x, c1, c0));
}

EXPORT CONST VECTOR_CC vfloat xerff_u1(vfloat a) {
  vfloat t, x = vabs_vf_vf(a);
  vfloat2 t2;
  vfloat x2 = vmul_vf_vf_vf(x, x), x4 = vmul_vf_vf_vf(x2, x2);
  vopmask o25 = vle_vo_vf_vf(x, vcast_vf_f(2.5));

  if (LIKELY(vtestallones_i_vo32(o25))) {
    // Abramowitz and Stegun
    t = POLY6(x, x2, x4,
              -0.4360447008e-6,
              +0.6867515367e-5,
              -0.3045156700e-4,
              +0.9808536561e-4,
              +0.2395523916e-3,
              +0.1459901541e-3);
    t2 = poly4df(x, t,
                 vcast_vf2_f_f(0.0092883445322513580322, -2.7863745897025330755e-11),
                 vcast_vf2_f_f(0.042275499552488327026, 1.3461399289988106057e-09),
                 vcast_vf2_f_f(0.070523701608180999756, -3.6616309318707365163e-09));
    t2 = dfadd_vf2_vf_vf2(vcast_vf_f(1), dfmul_vf2_vf2_vf(t2, x));
    t2 = dfsqu_vf2_vf2(t2);
    t2 = dfsqu_vf2_vf2(t2);
    t2 = dfsqu_vf2_vf2(t2);
    t2 = dfsqu_vf2_vf2(t2);
    t2 = dfrec_vf2_vf2(t2);
  } else {
#undef C2V
#define C2V(c) (c)
    t = POLY6(x, x2, x4,
               vsel_vf_vo_f_f(o25, -0.4360447008e-6, -0.1130012848e-6),
               vsel_vf_vo_f_f(o25, +0.6867515367e-5, +0.4115272986e-5),
               vsel_vf_vo_f_f(o25, -0.3045156700e-4, -0.6928304356e-4),
               vsel_vf_vo_f_f(o25, +0.9808536561e-4, +0.7172692567e-3),
               vsel_vf_vo_f_f(o25, +0.2395523916e-3, -0.5131045356e-2),
               vsel_vf_vo_f_f(o25, +0.1459901541e-3, +0.2708637156e-1));
    t2 = poly4df(x, t,
                 vsel_vf2_vo_vf2_vf2(o25, vcast_vf2_f_f(0.0092883445322513580322, -2.7863745897025330755e-11),
                                     vcast_vf2_f_f(-0.11064319312572479248, 3.7050452777225283007e-09)),
                 vsel_vf2_vo_vf2_vf2(o25, vcast_vf2_f_f(0.042275499552488327026, 1.3461399289988106057e-09),
                                     vcast_vf2_f_f(-0.63192230463027954102, -2.0200432585073177859e-08)),
                 vsel_vf2_vo_vf2_vf2(o25, vcast_vf2_f_f(0.070523701608180999756, -3.6616309318707365163e-09),
                                     vcast_vf2_f_f(-1.1296638250350952148, 2.5515120196453259252e-08)));
    t2 = dfmul_vf2_vf2_vf(t2, x);
    vfloat2 s2 = dfadd_vf2_vf_vf2(vcast_vf_f(1), t2);
    s2 = dfsqu_vf2_vf2(s2);
    s2 = dfsqu_vf2_vf2(s2);
    s2 = dfsqu_vf2_vf2(s2);
    s2 = dfsqu_vf2_vf2(s2);
    s2 = dfrec_vf2_vf2(s2);
    t2 = vsel_vf2_vo_vf2_vf2(o25, s2, vcast_vf2_vf_vf(expkf(t2), vcast_vf_f(0)));
  }

  t2 = dfadd2_vf2_vf2_vf(t2, vcast_vf_f(-1));
  t2 = vsel_vf2_vo_vf2_vf2(vlt_vo_vf_vf(x, vcast_vf_f(1e-4)), dfmul_vf2_vf2_vf(vcast_vf2_f_f(-1.1283792257308959961, 5.8635383422197591097e-08), x), t2);

  vfloat z = vneg_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(t2), vf2gety_vf_vf2(t2)));
  z = vsel_vf_vo_vf_vf(vge_vo_vf_vf(x, vcast_vf_f(6)), vcast_vf_f(1), z);
  z = vsel_vf_vo_vf_vf(visinf_vo_vf(a), vcast_vf_f(1), z);
  z = vsel_vf_vo_vf_vf(veq_vo_vf_vf(a, vcast_vf_f(0)), vcast_vf_f(0), z);
  z = vmulsign_vf_vf_vf(z, a);

  return z;
}

/* TODO AArch64: potential optimization by using `vfmad_lane_f64` */
EXPORT CONST VECTOR_CC vfloat xerfcf_u15(vfloat a) {
  vfloat s = a, r = vcast_vf_f(0), t;
  vfloat2 u, d, x;
  a = vabs_vf_vf(a);
  vopmask o0 = vlt_vo_vf_vf(a, vcast_vf_f(1.0));
  vopmask o1 = vlt_vo_vf_vf(a, vcast_vf_f(2.2));
  vopmask o2 = vlt_vo_vf_vf(a, vcast_vf_f(4.3));
  vopmask o3 = vlt_vo_vf_vf(a, vcast_vf_f(10.1));

  u = vsel_vf2_vo_vf2_vf2(o1, vcast_vf2_vf_vf(a, vcast_vf_f(0)), dfdiv_vf2_vf2_vf2(vcast_vf2_f_f(1, 0), vcast_vf2_vf_vf(a, vcast_vf_f(0))));

  t = vsel_vf_vo_vo_vo_f_f_f_f(o0, o1, o2, -0.8638041618e-4f, -0.6236977242e-5f, -0.3869504035e+0f, +0.1115344167e+1f);
  t = vmla_vf_vf_vf_vf(t, vf2getx_vf_vf2(u), vsel_vf_vo_vo_vo_f_f_f_f(o0, o1, o2, +0.6000166177e-3f, +0.5749821503e-4f, +0.1288077235e+1f, -0.9454904199e+0f));
  t = vmla_vf_vf_vf_vf(t, vf2getx_vf_vf2(u), vsel_vf_vo_vo_vo_f_f_f_f(o0, o1, o2, -0.1665703603e-2f, +0.6002851478e-5f, -0.1816803217e+1f, -0.3667259514e+0f));
  t = vmla_vf_vf_vf_vf(t, vf2getx_vf_vf2(u), vsel_vf_vo_vo_vo_f_f_f_f(o0, o1, o2, +0.1795156277e-3f, -0.2851036377e-2f, +0.1249150872e+1f, +0.7155663371e+0f));
  t = vmla_vf_vf_vf_vf(t, vf2getx_vf_vf2(u), vsel_vf_vo_vo_vo_f_f_f_f(o0, o1, o2, +0.1914106123e-1f, +0.2260518074e-1f, -0.1328857988e+0f, -0.1262947265e-1f));

  d = dfmul_vf2_vf2_vf(u, t);
  d = dfadd2_vf2_vf2_vf2(d, vsel_vf2_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.102775359343930288081655368891e+0, -0.105247583459338632253369014063e+0, -0.482365310333045318680618892669e+0, -0.498961546254537647970305302739e+0));
  d = dfmul_vf2_vf2_vf2(d, u);
  d = dfadd2_vf2_vf2_vf2(d, vsel_vf2_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.636619483208481931303752546439e+0, -0.635609463574589034216723775292e+0, -0.134450203224533979217859332703e-2, -0.471199543422848492080722832666e-4));
  d = dfmul_vf2_vf2_vf2(d, u);
  d = dfadd2_vf2_vf2_vf2(d, vsel_vf2_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.112837917790537404939545770596e+1, -0.112855987376668622084547028949e+1, -0.572319781150472949561786101080e+0, -0.572364030327966044425932623525e+0));

  x = dfmul_vf2_vf2_vf(vsel_vf2_vo_vf2_vf2(o1, d, vcast_vf2_vf_vf(vneg_vf_vf(a), vcast_vf_f(0))), a);
  x = vsel_vf2_vo_vf2_vf2(o1, x, dfadd2_vf2_vf2_vf2(x, d));

  x = expk2f(x);
  x = vsel_vf2_vo_vf2_vf2(o1, x, dfmul_vf2_vf2_vf2(x, u));

  r = vsel_vf_vo_vf_vf(o3, vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x)), vcast_vf_f(0));
  r = vsel_vf_vo_vf_vf(vsignbit_vo_vf(s), vsub_vf_vf_vf(vcast_vf_f(2), r), r);
  r = vsel_vf_vo_vf_vf(visnan_vo_vf(s), vcast_vf_f(SLEEF_NANf), r);
  return r;
}
#endif // #if !defined(DETERMINISTIC)

#if !defined(DETERMINISTIC) && !defined(ENABLE_GNUABI) && !defined(SLEEF_GENHEADER)
// See sleefsimddp.c for explanation of these macros

#ifdef ENABLE_ALIAS
#define DALIAS_vf_vf(FUNC) EXPORT CONST VECTOR_CC vfloat y ## FUNC(vfloat) __attribute__((alias( stringify(x ## FUNC) )));
#define DALIAS_vf2_vf(FUNC) EXPORT CONST VECTOR_CC vfloat2 y ## FUNC(vfloat) __attribute__((alias( stringify(x ## FUNC) )));
#define DALIAS_vf_vf_vf(FUNC) EXPORT CONST VECTOR_CC vfloat y ## FUNC(vfloat, vfloat) __attribute__((alias( stringify(x ## FUNC) )));
#define DALIAS_vf_vf_vf_vf(FUNC) EXPORT CONST VECTOR_CC vfloat y ## FUNC(vfloat, vfloat, vfloat) __attribute__((alias( stringify(x ## FUNC) )));
#else
#define DALIAS_vf_vf(FUNC) EXPORT CONST VECTOR_CC vfloat y ## FUNC(vfloat d) { return x ## FUNC (d); }
#define DALIAS_vf2_vf(FUNC) EXPORT CONST VECTOR_CC vfloat2 y ## FUNC(vfloat d) { return x ## FUNC (d); }
#define DALIAS_vf_vf_vf(FUNC) EXPORT CONST VECTOR_CC vfloat y ## FUNC(vfloat x, vfloat y) { return x ## FUNC (x, y); }
#define DALIAS_vf_vf_vf_vf(FUNC) EXPORT CONST VECTOR_CC vfloat y ## FUNC(vfloat x, vfloat y, vfloat z) { return x ## FUNC (x, y, z); }
#endif

DALIAS_vf2_vf(sincospif_u05)
DALIAS_vf2_vf(sincospif_u35)
DALIAS_vf2_vf(modff)
DALIAS_vf_vf(atanf)
DALIAS_vf_vf_vf(atan2f)
DALIAS_vf_vf(asinf)
DALIAS_vf_vf(acosf)
DALIAS_vf_vf_vf(atan2f_u1)
DALIAS_vf_vf(asinf_u1)
DALIAS_vf_vf(acosf_u1)
DALIAS_vf_vf(atanf_u1)
DALIAS_vf_vf(logf)
DALIAS_vf_vf(expf)
DALIAS_vf_vf(cbrtf)
DALIAS_vf_vf(cbrtf_u1)
DALIAS_vf_vf(logf_u1)
DALIAS_vf_vf_vf(powf)
DALIAS_vf_vf(sinhf)
DALIAS_vf_vf(coshf)
DALIAS_vf_vf(tanhf)
DALIAS_vf_vf(sinhf_u35)
DALIAS_vf_vf(coshf_u35)
DALIAS_vf_vf(tanhf_u35)
DALIAS_vf_vf(asinhf)
DALIAS_vf_vf(acoshf)
DALIAS_vf_vf(atanhf)
DALIAS_vf_vf(exp2f)
DALIAS_vf_vf(exp2f_u35)
DALIAS_vf_vf(exp10f)
DALIAS_vf_vf(exp10f_u35)
DALIAS_vf_vf(expm1f)
DALIAS_vf_vf(log10f)
DALIAS_vf_vf(log2f)
DALIAS_vf_vf(log2f_u35)
DALIAS_vf_vf(log1pf)
DALIAS_vf_vf(fabsf)
DALIAS_vf_vf_vf(copysignf)
DALIAS_vf_vf_vf(fmaxf)
DALIAS_vf_vf_vf(fminf)
DALIAS_vf_vf_vf(fdimf)
DALIAS_vf_vf(truncf)
DALIAS_vf_vf(floorf)
DALIAS_vf_vf(ceilf)
DALIAS_vf_vf(roundf)
DALIAS_vf_vf(rintf)
DALIAS_vf_vf_vf_vf(fmaf)
DALIAS_vf_vf_vf(hypotf_u05)
DALIAS_vf_vf_vf(hypotf_u35)
DALIAS_vf_vf_vf(nextafterf)
DALIAS_vf_vf(frfrexpf)
DALIAS_vf_vf_vf(fmodf)
DALIAS_vf_vf_vf(remainderf)
DALIAS_vf_vf(sinpif_u05)
DALIAS_vf_vf(cospif_u05)
DALIAS_vf_vf(tgammaf_u1)
DALIAS_vf_vf(lgammaf_u1)
DALIAS_vf_vf(erff_u1)
DALIAS_vf_vf(erfcf_u15)
DALIAS_vf_vf_vf(fastpowf_u3500)
#endif // #if !defined(DETERMINISTIC) && !defined(ENABLE_GNUABI) && !defined(SLEEF_GENHEADER)

#if !defined(ENABLE_GNUABI) && !defined(SLEEF_GENHEADER)
EXPORT CONST int xgetIntf(int name) {
  if (1 <= name && name <= 10) return vavailability_i(name);
  return 0;
}

EXPORT CONST void *xgetPtrf(int name) {
  if (name == 0) return ISANAME;
  return (void *)0;
}
#endif

#if defined(ALIAS_NO_EXT_SUFFIX) && !defined(DETERMINISTIC)
#include ALIAS_NO_EXT_SUFFIX
#endif

#ifdef ENABLE_GNUABI
EXPORT CONST VECTOR_CC vfloat __acosf_finite     (vfloat)         __attribute__((weak, alias(str_xacosf_u1  )));
EXPORT CONST VECTOR_CC vfloat __acoshf_finite    (vfloat)         __attribute__((weak, alias(str_xacoshf    )));
EXPORT CONST VECTOR_CC vfloat __asinf_finite     (vfloat)         __attribute__((weak, alias(str_xasinf_u1  )));
EXPORT CONST VECTOR_CC vfloat __atan2f_finite    (vfloat, vfloat) __attribute__((weak, alias(str_xatan2f_u1 )));
EXPORT CONST VECTOR_CC vfloat __atanhf_finite    (vfloat)         __attribute__((weak, alias(str_xatanhf    )));
EXPORT CONST VECTOR_CC vfloat __coshf_finite     (vfloat)         __attribute__((weak, alias(str_xcoshf     )));
EXPORT CONST VECTOR_CC vfloat __exp10f_finite    (vfloat)         __attribute__((weak, alias(str_xexp10f    )));
EXPORT CONST VECTOR_CC vfloat __exp2f_finite     (vfloat)         __attribute__((weak, alias(str_xexp2f     )));
EXPORT CONST VECTOR_CC vfloat __expf_finite      (vfloat)         __attribute__((weak, alias(str_xexpf      )));
EXPORT CONST VECTOR_CC vfloat __fmodf_finite     (vfloat, vfloat) __attribute__((weak, alias(str_xfmodf     )));
EXPORT CONST VECTOR_CC vfloat __remainderf_finite(vfloat, vfloat) __attribute__((weak, alias(str_xremainderf)));
EXPORT CONST VECTOR_CC vfloat __modff_finite      (vfloat, vfloat *) __attribute__((weak, alias(str_xmodff  )));
EXPORT CONST VECTOR_CC vfloat __hypotf_u05_finite(vfloat, vfloat) __attribute__((weak, alias(str_xhypotf_u05)));
EXPORT CONST VECTOR_CC vfloat __lgammaf_u1_finite(vfloat)         __attribute__((weak, alias(str_xlgammaf_u1)));
EXPORT CONST VECTOR_CC vfloat __log10f_finite    (vfloat)         __attribute__((weak, alias(str_xlog10f    )));
EXPORT CONST VECTOR_CC vfloat __logf_finite      (vfloat)         __attribute__((weak, alias(str_xlogf_u1   )));
EXPORT CONST VECTOR_CC vfloat __powf_finite      (vfloat, vfloat) __attribute__((weak, alias(str_xpowf      )));
EXPORT CONST VECTOR_CC vfloat __sinhf_finite     (vfloat)         __attribute__((weak, alias(str_xsinhf     )));
EXPORT CONST VECTOR_CC vfloat __sqrtf_finite     (vfloat)         __attribute__((weak, alias(str_xsqrtf     )));
EXPORT CONST VECTOR_CC vfloat __tgammaf_u1_finite(vfloat)         __attribute__((weak, alias(str_xtgammaf_u1)));

#ifdef HEADER_MASKED
#include HEADER_MASKED
#endif
#endif /* #ifdef ENABLE_GNUABI */

#ifdef ENABLE_MAIN
// gcc -DENABLE_MAIN -Wno-attributes -I../common -I../arch -DENABLE_AVX2 -mavx2 -mfma sleefsimdsp.c rempitab.c ../common/common.c -lm
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
int main(int argc, char **argv) {
  vfloat vf1 = vcast_vf_f(atof(argv[1]));
  //vfloat vf2 = vcast_vf_f(atof(argv[2]));

  //vfloat r = xpowf(vf1, vf2);
  //vfloat r = xsqrtf_u05(vf1);
  //printf("%g\n", xnextafterf(vf1, vf2)[0]);
  //printf("%g\n", nextafterf(atof(argv[1]), atof(argv[2])));
  printf("t = %.20g\n", xerff_u1(vf1)[0]);
  printf("c = %.20g\n", erff(atof(argv[1])));

}
#endif
