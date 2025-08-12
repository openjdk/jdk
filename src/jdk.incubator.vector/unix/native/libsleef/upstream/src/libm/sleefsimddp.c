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
extern const double Sleef_rempitabdp[];
#endif

#define __SLEEFSIMDDP_C__

#if defined(_MSC_VER) && !defined (__clang__)
#pragma fp_contract (off)
#else
#pragma STDC FP_CONTRACT OFF
#endif

// Intel

#ifdef ENABLE_SSE2
#define CONFIG 2
#include "helpersse2.h"
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
#include "helpersse2.h"
#ifdef DORENAME
#include "renamesse4.h"
#endif
#endif

#ifdef ENABLE_AVX
#define CONFIG 1
#include "helperavx.h"
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
#include "helperavx.h"
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
#include "helperavx2.h"
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
#include "helperavx2_128.h"
#ifdef DORENAME
#include "renameavx2128.h"
#endif
#endif

#ifdef ENABLE_AVX512F
#define CONFIG 1
#include "helperavx512f.h"
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
#include "helperavx512f.h"
#ifdef DORENAME
#include "renameavx512fnofma.h"
#endif
#endif

// Arm

#ifdef ENABLE_ADVSIMD
#define CONFIG 1
#include "helperadvsimd.h"
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
#include "helperadvsimd.h"
#ifdef DORENAME
#include "renameadvsimdnofma.h"
#endif
#endif

#ifdef ENABLE_SVE
#define CONFIG 1
#include "helpersve.h"
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
#include "helpersve.h"
#ifdef DORENAME
#include "renamesvenofma.h"
#endif /* DORENAME */
#endif /* ENABLE_SVE */

// IBM

#ifdef ENABLE_VSX
#define CONFIG 1
#include "helperpower_128.h"
#ifdef DORENAME
#include "renamevsx.h"
#endif
#endif

#ifdef ENABLE_VSXNOFMA
#define CONFIG 2
#include "helperpower_128.h"
#ifdef DORENAME
#include "renamevsxnofma.h"
#endif
#endif

#ifdef ENABLE_VSX3
#define CONFIG 3
#include "helperpower_128.h"
#ifdef DORENAME
#include "renamevsx3.h"
#endif
#endif

#ifdef ENABLE_VSX3NOFMA
#define CONFIG 4
#include "helperpower_128.h"
#ifdef DORENAME
#include "renamevsx3nofma.h"
#endif
#endif

#ifdef ENABLE_VXE
#define CONFIG 140
#include "helpers390x_128.h"
#ifdef DORENAME
#include "renamevxe.h"
#endif
#endif

#ifdef ENABLE_VXENOFMA
#define CONFIG 141
#include "helpers390x_128.h"
#ifdef DORENAME
#include "renamevxenofma.h"
#endif
#endif

#ifdef ENABLE_VXE2
#define CONFIG 150
#include "helpers390x_128.h"
#ifdef DORENAME
#include "renamevxe2.h"
#endif
#endif

#ifdef ENABLE_VXE2NOFMA
#define CONFIG 151
#include "helpers390x_128.h"
#ifdef DORENAME
#include "renamevxe2nofma.h"
#endif
#endif

// RISC-V
#ifdef ENABLE_RVVM1
#define CONFIG 1
#define ENABLE_RVV_DP
#include "helperrvv.h"
#ifdef DORENAME
#include "renamervvm1.h"
#endif
#endif

#ifdef ENABLE_RVVM1NOFMA
#define CONFIG 2
#define ENABLE_RVV_DP
#include "helperrvv.h"
#ifdef DORENAME
#include "renamervvm1nofma.h"
#endif
#endif /* ENABLE_RVVM1NOFMA */

#ifdef ENABLE_RVVM2
#define CONFIG 1
#define ENABLE_RVV_DP
#include "helperrvv.h"
#ifdef DORENAME
#include "renamervvm2.h"
#endif
#endif

#ifdef ENABLE_RVVM2NOFMA
#define CONFIG 2
#define ENABLE_RVV_DP
#include "helperrvv.h"
#ifdef DORENAME
#include "renamervvm2nofma.h"
#endif
#endif /* ENABLE_RVVM2NOFMA */

// Generic

#ifdef ENABLE_VECEXT
#define CONFIG 1
#include "helpervecext.h"
#ifdef DORENAME
#include "renamevecext.h"
#endif
#endif

#ifdef ENABLE_PUREC
#define CONFIG 1
#include "helperpurec.h"
#ifdef DORENAME
#include "renamepurec.h"
#endif
#endif

#ifdef ENABLE_PUREC_SCALAR
#define CONFIG 1
#include "helperpurec_scalar.h"
#ifdef DORENAME
#include "renamepurec_scalar.h"
#endif
#endif

#ifdef ENABLE_PURECFMA_SCALAR
#define CONFIG 2
#include "helperpurec_scalar.h"
#ifdef DORENAME
#include "renamepurecfma_scalar.h"
#endif
#endif

#ifdef SLEEF_ENABLE_CUDA
#define CONFIG 3
#include "helperpurec_scalar.h"
#ifdef DORENAME
#include "renamecuda.h"
#endif
#endif

//

#define MLA(x, y, z) vmla_vd_vd_vd_vd((x), (y), (z))
#define C2V(c) vcast_vd_d(c)
#include "estrin.h"

//

#include "dd.h"
#include "commonfuncs.h"

// return d0 < d1 ? x : y
static INLINE CONST VECTOR_CC vint vsel_vi_vd_vd_vi_vi(vdouble d0, vdouble d1, vint x, vint y) { return vsel_vi_vo_vi_vi(vcast_vo32_vo64(vlt_vo_vd_vd(d0, d1)), x, y); }

// return d0 < 0 ? x : 0
static INLINE CONST VECTOR_CC vint vsel_vi_vd_vi(vdouble d, vint x) { return vand_vi_vo_vi(vcast_vo32_vo64(vsignbit_vo_vd(d)), x); }

//

EXPORT CONST VECTOR_CC vdouble xldexp(vdouble x, vint q) { return vldexp_vd_vd_vi(x, q); }

EXPORT CONST VECTOR_CC vint xilogb(vdouble d) {
  vdouble e = vcast_vd_vi(vilogbk_vi_vd(vabs_vd_vd(d)));
  e = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(SLEEF_FP_ILOGB0), e);
  e = vsel_vd_vo_vd_vd(visnan_vo_vd(d), vcast_vd_d(SLEEF_FP_ILOGBNAN), e);
  e = vsel_vd_vo_vd_vd(visinf_vo_vd(d), vcast_vd_d(SLEEF_INT_MAX), e);
  return vrint_vi_vd(e);
}

static INLINE CONST ddi_t rempi(vdouble a) {
  vdouble2 x, y;
  vint ex = vilogb2k_vi_vd(a);
#if defined(ENABLE_AVX512F) || defined(ENABLE_AVX512FNOFMA)
  ex = vandnot_vi_vi_vi(vsra_vi_vi_i(ex, 31), ex);
  ex = vand_vi_vi_vi(ex, vcast_vi_i(1023));
#endif
  ex = vsub_vi_vi_vi(ex, vcast_vi_i(55));
  vint q = vand_vi_vo_vi(vgt_vo_vi_vi(ex, vcast_vi_i(700-55)), vcast_vi_i(-64));
  a = vldexp3_vd_vd_vi(a, q);
  ex = vandnot_vi_vi_vi(vsra_vi_vi_i(ex, 31), ex);
  ex = vsll_vi_vi_i(ex, 2);
  x = ddmul_vd2_vd_vd(a, vgather_vd_p_vi(Sleef_rempitabdp, ex));
  di_t di = rempisub(vd2getx_vd_vd2(x));
  q = digeti_vi_di(di);
  x = vd2setx_vd2_vd2_vd(x, digetd_vd_di(di));
  x = ddnormalize_vd2_vd2(x);
  y = ddmul_vd2_vd_vd(a, vgather_vd_p_vi(Sleef_rempitabdp+1, ex));
  x = ddadd2_vd2_vd2_vd2(x, y);
  di = rempisub(vd2getx_vd_vd2(x));
  q = vadd_vi_vi_vi(q, digeti_vi_di(di));
  x = vd2setx_vd2_vd2_vd(x, digetd_vd_di(di));
  x = ddnormalize_vd2_vd2(x);
  y = vcast_vd2_vd_vd(vgather_vd_p_vi(Sleef_rempitabdp+2, ex), vgather_vd_p_vi(Sleef_rempitabdp+3, ex));
  y = ddmul_vd2_vd2_vd(y, a);
  x = ddadd2_vd2_vd2_vd2(x, y);
  x = ddnormalize_vd2_vd2(x);
  x = ddmul_vd2_vd2_vd2(x, vcast_vd2_d_d(3.141592653589793116*2, 1.2246467991473532072e-16*2));
  vopmask o = vlt_vo_vd_vd(vabs_vd_vd(a), vcast_vd_d(0.7));
  x = vd2setx_vd2_vd2_vd(x, vsel_vd_vo_vd_vd(o, a, vd2getx_vd_vd2(x)));
  x = vd2sety_vd2_vd2_vd(x, vreinterpret_vd_vm(vandnot_vm_vo64_vm(o, vreinterpret_vm_vd(vd2gety_vd_vd2(x)))));
  return ddisetddi_ddi_vd2_vi(x, q);
}

EXPORT CONST VECTOR_CC vdouble xsin(vdouble d) {
#if !defined(DETERMINISTIC)
// The SIMD source files(sleefsimd?p.c) are compiled twice for each
// vector extension, with DETERMINISTIC macro turned on and off.
// Below is the normal(faster) implementation of sin function.  The
// function name xsin will be renamed to Sleef_sind2_u35sse2 with
// renamesse2.h, for example.

  vdouble u, s, r = d;
  vint ql;

  if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2))))) {
    vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI)));
    ql = vrint_vi_vd(dql);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2), d);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B2), d);
  } else if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX))))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    vdouble dql = vrint_vd_vd(vmlapn_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI), dqh));
    ql = vrint_vi_vd(dql);

    d = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A), d);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A), d);
    d = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_B), d);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B), d);
    d = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_C), d);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_C), d);
    d = vmla_vd_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D), d);
  } else {
    ddi_t ddi = rempi(d);
    ql = vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(3));
    ql = vadd_vi_vi_vi(vadd_vi_vi_vi(ql, ql), vsel_vi_vo_vi_vi(vcast_vo32_vo64(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0))), vcast_vi_i(2), vcast_vi_i(1)));
    ql = vsra_vi_vi_i(ql, 2);
    vopmask o = veq_vo_vi_vi(vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(1)), vcast_vi_i(1));
    vdouble2 x = vcast_vd2_vd_vd(vmulsign_vd_vd_vd(vcast_vd_d(-3.141592653589793116 * 0.5), vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi))),
                                 vmulsign_vd_vd_vd(vcast_vd_d(-1.2246467991473532072e-16 * 0.5), vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi))));
    x = ddadd2_vd2_vd2_vd2(ddigetdd_vd2_ddi(ddi), x);
    ddi = ddisetdd_ddi_ddi_vd2(ddi, vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(o), x, ddigetdd_vd2_ddi(ddi)));
    d = vadd_vd_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vd2gety_vd_vd2(ddigetdd_vd2_ddi(ddi)));
    d = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(r), visnan_vo_vd(r)), vreinterpret_vm_vd(d)));
  }

  s = vmul_vd_vd_vd(d, d);

  d = vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(1))), vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(d)));

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY8(s, s2, s4,
            -7.97255955009037868891952e-18,
            2.81009972710863200091251e-15,
            -7.64712219118158833288484e-13,
            1.60590430605664501629054e-10,
            -2.50521083763502045810755e-08,
            2.75573192239198747630416e-06,
            -0.000198412698412696162806809,
            0.00833333333333332974823815);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.166666666666666657414808));

  u = vadd_vd_vd_vd(vmul_vd_vd_vd(s, vmul_vd_vd_vd(u, d)), d);

  u = vsel_vd_vo_vd_vd(visnegzero_vo_vd(r), r, u);

  return u;

#else // #if !defined(DETERMINISTIC)

// This is the deterministic implementation of sin function. Returned
// values from deterministic functions are bitwise consistent across
// all platforms. The function name xsin will be renamed to
// Sleef_cinz_sind2_u35sse2 with renamesse2.h, for example. The
// renaming by rename*.h is switched according to DETERMINISTIC macro.
  vdouble u, s, r = d;
  vint ql;

  vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI)));
  ql = vrint_vi_vd(dql);
  d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2), d);
  d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B2), d);
  vopmask g = vlt_vo_vd_vd(vabs_vd_vd(r), vcast_vd_d(TRIGRANGEMAX2));

  if (!LIKELY(vtestallones_i_vo64(g))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(r, vcast_vd_d(M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    vdouble dql = vrint_vd_vd(vmlapn_vd_vd_vd_vd(r, vcast_vd_d(M_1_PI), dqh));

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A), r);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A), u);
    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_B), u);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B), u);
    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_C), u);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_C), u);
    u = vmla_vd_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D), u);

    ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, vrint_vi_vd(dql));
    d = vsel_vd_vo_vd_vd(g, d, u);
    g = vlt_vo_vd_vd(vabs_vd_vd(r), vcast_vd_d(TRIGRANGEMAX));

    if (!LIKELY(vtestallones_i_vo64(g))) {
      ddi_t ddi = rempi(r);
      vint ql2 = vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(3));
      ql2 = vadd_vi_vi_vi(vadd_vi_vi_vi(ql2, ql2), vsel_vi_vo_vi_vi(vcast_vo32_vo64(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0))), vcast_vi_i(2), vcast_vi_i(1)));
      ql2 = vsra_vi_vi_i(ql2, 2);
      vopmask o = veq_vo_vi_vi(vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(1)), vcast_vi_i(1));
      vdouble2 x = vcast_vd2_vd_vd(vmulsign_vd_vd_vd(vcast_vd_d(-3.141592653589793116 * 0.5), vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi))),
                                   vmulsign_vd_vd_vd(vcast_vd_d(-1.2246467991473532072e-16 * 0.5), vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi))));
      x = ddadd2_vd2_vd2_vd2(ddigetdd_vd2_ddi(ddi), x);
      ddi = ddisetdd_ddi_ddi_vd2(ddi, vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(o), x, ddigetdd_vd2_ddi(ddi)));
      u = vadd_vd_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vd2gety_vd_vd2(ddigetdd_vd2_ddi(ddi)));
      ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ql2);
      d = vsel_vd_vo_vd_vd(g, d, u);
      d = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(r), visnan_vo_vd(r)), vreinterpret_vm_vd(d)));
    }
  }

  s = vmul_vd_vd_vd(d, d);

  d = vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(1))), vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(d)));

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY8(s, s2, s4,
            -7.97255955009037868891952e-18,
            2.81009972710863200091251e-15,
            -7.64712219118158833288484e-13,
            1.60590430605664501629054e-10,
            -2.50521083763502045810755e-08,
            2.75573192239198747630416e-06,
            -0.000198412698412696162806809,
            0.00833333333333332974823815);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.166666666666666657414808));

  u = vadd_vd_vd_vd(vmul_vd_vd_vd(s, vmul_vd_vd_vd(u, d)), d);

  u = vsel_vd_vo_vd_vd(visnegzero_vo_vd(r), r, u);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

EXPORT CONST VECTOR_CC vdouble xsin_u1(vdouble d) {
#if !defined(DETERMINISTIC)
  vdouble u;
  vdouble2 s, t, x;
  vint ql;

  if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2))))) {
    const vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI)));
    ql = vrint_vi_vd(dql);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2), d);
    s = ddadd_vd2_vd_vd (u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B2)));
  } else if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX))))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    const vdouble dql = vrint_vd_vd(vmlapn_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI), dqh));
    ql = vrint_vi_vd(dql);

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A), d);
    s = ddadd_vd2_vd_vd  (u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_B)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_C)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_C)));
    s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D)));
  } else {
    ddi_t ddi = rempi(d);
    ql = vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(3));
    ql = vadd_vi_vi_vi(vadd_vi_vi_vi(ql, ql), vsel_vi_vo_vi_vi(vcast_vo32_vo64(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0))), vcast_vi_i(2), vcast_vi_i(1)));
    ql = vsra_vi_vi_i(ql, 2);
    vopmask o = veq_vo_vi_vi(vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(1)), vcast_vi_i(1));
    vdouble2 x = vcast_vd2_vd_vd(vmulsign_vd_vd_vd(vcast_vd_d(-3.141592653589793116 * 0.5), vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi))),
                                 vmulsign_vd_vd_vd(vcast_vd_d(-1.2246467991473532072e-16 * 0.5), vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi))));
    x = ddadd2_vd2_vd2_vd2(ddigetdd_vd2_ddi(ddi), x);
    ddi = ddisetdd_ddi_ddi_vd2(ddi, vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(o), x, ddigetdd_vd2_ddi(ddi)));
    s = ddnormalize_vd2_vd2(ddigetdd_vd2_ddi(ddi));
    s = vd2setx_vd2_vd2_vd(s, vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d)), vreinterpret_vm_vd(vd2getx_vd_vd2(s)))));
  }

  t = s;
  s = ddsqu_vd2_vd2(s);

  vdouble s2 = vmul_vd_vd_vd(vd2getx_vd_vd2(s), vd2getx_vd_vd2(s)), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY6(vd2getx_vd_vd2(s), s2, s4,
            2.72052416138529567917983e-15,
            -7.6429259411395447190023e-13,
            1.60589370117277896211623e-10,
            -2.5052106814843123359368e-08,
            2.75573192104428224777379e-06,
            -0.000198412698412046454654947);
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(0.00833333333333318056201922));

  x = ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd2_vd2(ddadd_vd2_vd_vd(vcast_vd_d(-0.166666666666666657414808), vmul_vd_vd_vd(u, vd2getx_vd_vd2(s))), s));
  u = ddmul_vd_vd2_vd2(t, x);

  u = vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(1))),
                                                       vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(u)));
  u = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), d, u);

  return u;

#else // #if !defined(DETERMINISTIC)

  vdouble u;
  vdouble2 s, t, x;
  vint ql;

  vopmask g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2));
  vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI)));
  ql = vrint_vi_vd(dql);
  u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2), d);
  x = ddadd_vd2_vd_vd (u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B2)));

  if (!LIKELY(vtestallones_i_vo64(g))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    const vdouble dql = vrint_vd_vd(vmlapn_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI), dqh));

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A), d);
    s = ddadd_vd2_vd_vd  (u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_B)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_C)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_C)));
    s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D)));

    ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, vrint_vi_vd(dql));
    x = vsel_vd2_vo_vd2_vd2(g, x, s);
    g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX));

    if (!LIKELY(vtestallones_i_vo64(g))) {
      ddi_t ddi = rempi(d);
      vint ql2 = vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(3));
      ql2 = vadd_vi_vi_vi(vadd_vi_vi_vi(ql2, ql2), vsel_vi_vo_vi_vi(vcast_vo32_vo64(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0))), vcast_vi_i(2), vcast_vi_i(1)));
      ql2 = vsra_vi_vi_i(ql2, 2);
      vopmask o = veq_vo_vi_vi(vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(1)), vcast_vi_i(1));
      vdouble2 t = vcast_vd2_vd_vd(vmulsign_vd_vd_vd(vcast_vd_d(-3.141592653589793116 * 0.5), vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi))),
                                   vmulsign_vd_vd_vd(vcast_vd_d(-1.2246467991473532072e-16 * 0.5), vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi))));
      t = ddadd2_vd2_vd2_vd2(ddigetdd_vd2_ddi(ddi), t);
      ddi = ddisetdd_ddi_ddi_vd2(ddi, vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(o), t, ddigetdd_vd2_ddi(ddi)));
      s = ddnormalize_vd2_vd2(ddigetdd_vd2_ddi(ddi));
      ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ql2);
      x = vsel_vd2_vo_vd2_vd2(g, x, s);
      x = vd2setx_vd2_vd2_vd(x, vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d)), vreinterpret_vm_vd(vd2getx_vd_vd2(x)))));
    }
  }

  t = x;
  s = ddsqu_vd2_vd2(x);

  vdouble s2 = vmul_vd_vd_vd(vd2getx_vd_vd2(s), vd2getx_vd_vd2(s)), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY6(vd2getx_vd_vd2(s), s2, s4,
            2.72052416138529567917983e-15,
            -7.6429259411395447190023e-13,
            1.60589370117277896211623e-10,
            -2.5052106814843123359368e-08,
            2.75573192104428224777379e-06,
            -0.000198412698412046454654947);
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(0.00833333333333318056201922));

  x = ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd2_vd2(ddadd_vd2_vd_vd(vcast_vd_d(-0.166666666666666657414808), vmul_vd_vd_vd(u, vd2getx_vd_vd2(s))), s));
  u = ddmul_vd_vd2_vd2(t, x);

  u = vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(1))),
                                                       vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(u)));

  u = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), d, u);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

EXPORT CONST VECTOR_CC vdouble xcos(vdouble d) {
#if !defined(DETERMINISTIC)
  vdouble u, s, r = d;
  vint ql;

  if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2))))) {
    vdouble dql = vmla_vd_vd_vd_vd(vcast_vd_d(2),
                                   vrint_vd_vd(vmla_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI), vcast_vd_d(-0.5))),
                                   vcast_vd_d(1));
    ql = vrint_vi_vd(dql);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2 * 0.5), d);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B2 * 0.5), d);
  } else if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX))))) {
    vdouble dqh = vtruncate_vd_vd(vmla_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI / (1 << 23)), vcast_vd_d(-M_1_PI / (1 << 24))));
    ql = vrint_vi_vd(vadd_vd_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI)),
                                   vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-(1 << 23)), vcast_vd_d(-0.5))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    ql = vadd_vi_vi_vi(vadd_vi_vi_vi(ql, ql), vcast_vi_i(1));
    vdouble dql = vcast_vd_vi(ql);

    d = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A * 0.5), d);
    d = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_B * 0.5), d);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B * 0.5), d);
    d = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_C * 0.5), d);
    d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_C * 0.5), d);
    d = vmla_vd_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D * 0.5), d);
  } else {
    ddi_t ddi = rempi(d);
    ql = vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(3));
    ql = vadd_vi_vi_vi(vadd_vi_vi_vi(ql, ql), vsel_vi_vo_vi_vi(vcast_vo32_vo64(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0))), vcast_vi_i(8), vcast_vi_i(7)));
    ql = vsra_vi_vi_i(ql, 1);
    vopmask o = veq_vo_vi_vi(vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(1)), vcast_vi_i(0));
    vdouble y = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0)), vcast_vd_d(0), vcast_vd_d(-1));
    vdouble2 x = vcast_vd2_vd_vd(vmulsign_vd_vd_vd(vcast_vd_d(-3.141592653589793116 * 0.5), y),
                                 vmulsign_vd_vd_vd(vcast_vd_d(-1.2246467991473532072e-16 * 0.5), y));
    x = ddadd2_vd2_vd2_vd2(ddigetdd_vd2_ddi(ddi), x);
    ddi = ddisetdd_ddi_ddi_vd2(ddi, vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(o), x, ddigetdd_vd2_ddi(ddi)));
    d = vadd_vd_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vd2gety_vd_vd2(ddigetdd_vd2_ddi(ddi)));
    d = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(r), visnan_vo_vd(r)), vreinterpret_vm_vd(d)));
  }

  s = vmul_vd_vd_vd(d, d);

  d = vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(2)), vcast_vi_i(0))), vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(d)));

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY8(s, s2, s4,
            -7.97255955009037868891952e-18,
            2.81009972710863200091251e-15,
            -7.64712219118158833288484e-13,
            1.60590430605664501629054e-10,
            -2.50521083763502045810755e-08,
            2.75573192239198747630416e-06,
            -0.000198412698412696162806809,
            0.00833333333333332974823815);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.166666666666666657414808));

  u = vadd_vd_vd_vd(vmul_vd_vd_vd(s, vmul_vd_vd_vd(u, d)), d);

  return u;

#else // #if !defined(DETERMINISTIC)

  vdouble u, s, r = d;
  vint ql;

  vopmask g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2));
  vdouble dql = vmla_vd_vd_vd_vd(vcast_vd_d(2),
                                 vrint_vd_vd(vmla_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI), vcast_vd_d(-0.5))),
                                 vcast_vd_d(1));
  ql = vrint_vi_vd(dql);
  d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2 * 0.5), d);
  d = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B2 * 0.5), d);

  if (!LIKELY(vtestallones_i_vo64(g))) {
    vdouble dqh = vtruncate_vd_vd(vmla_vd_vd_vd_vd(r, vcast_vd_d(M_1_PI / (1 << 23)), vcast_vd_d(-M_1_PI / (1 << 24))));
    vint ql2 = vrint_vi_vd(vadd_vd_vd_vd(vmul_vd_vd_vd(r, vcast_vd_d(M_1_PI)),
                                         vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-(1 << 23)), vcast_vd_d(-0.5))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    ql2 = vadd_vi_vi_vi(vadd_vi_vi_vi(ql2, ql2), vcast_vi_i(1));
    vdouble dql = vcast_vd_vi(ql2);

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), r);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A * 0.5), u);
    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_B * 0.5), u);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B * 0.5), u);
    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_C * 0.5), u);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_C * 0.5), u);
    u = vmla_vd_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D * 0.5), u);

    ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ql2);
    d = vsel_vd_vo_vd_vd(g, d, u);
    g = vlt_vo_vd_vd(vabs_vd_vd(r), vcast_vd_d(TRIGRANGEMAX));

    if (!LIKELY(vtestallones_i_vo64(g))) {
      ddi_t ddi = rempi(r);
      vint ql2 = vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(3));
      ql2 = vadd_vi_vi_vi(vadd_vi_vi_vi(ql2, ql2), vsel_vi_vo_vi_vi(vcast_vo32_vo64(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0))), vcast_vi_i(8), vcast_vi_i(7)));
      ql2 = vsra_vi_vi_i(ql2, 1);
      vopmask o = veq_vo_vi_vi(vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(1)), vcast_vi_i(0));
      vdouble y = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0)), vcast_vd_d(0), vcast_vd_d(-1));
      vdouble2 x = vcast_vd2_vd_vd(vmulsign_vd_vd_vd(vcast_vd_d(-3.141592653589793116 * 0.5), y),
                                   vmulsign_vd_vd_vd(vcast_vd_d(-1.2246467991473532072e-16 * 0.5), y));
      x = ddadd2_vd2_vd2_vd2(ddigetdd_vd2_ddi(ddi), x);
      ddi = ddisetdd_ddi_ddi_vd2(ddi, vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(o), x, ddigetdd_vd2_ddi(ddi)));
      u = vadd_vd_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vd2gety_vd_vd2(ddigetdd_vd2_ddi(ddi)));
      ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ql2);
      d = vsel_vd_vo_vd_vd(g, d, u);
      d = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(r), visnan_vo_vd(r)), vreinterpret_vm_vd(d)));
    }
  }

  s = vmul_vd_vd_vd(d, d);

  d = vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(2)), vcast_vi_i(0))), vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(d)));

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY8(s, s2, s4,
            -7.97255955009037868891952e-18,
            2.81009972710863200091251e-15,
            -7.64712219118158833288484e-13,
            1.60590430605664501629054e-10,
            -2.50521083763502045810755e-08,
            2.75573192239198747630416e-06,
            -0.000198412698412696162806809,
            0.00833333333333332974823815);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.166666666666666657414808));

  u = vadd_vd_vd_vd(vmul_vd_vd_vd(s, vmul_vd_vd_vd(u, d)), d);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

EXPORT CONST VECTOR_CC vdouble xcos_u1(vdouble d) {
#if !defined(DETERMINISTIC)
  vdouble u;
  vdouble2 s, t, x;
  vint ql;

  if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2))))) {
    vdouble dql = vrint_vd_vd(vmla_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI), vcast_vd_d(-0.5)));
    dql = vmla_vd_vd_vd_vd(vcast_vd_d(2), dql, vcast_vd_d(1));
    ql = vrint_vi_vd(dql);
    s = ddadd2_vd2_vd_vd(d, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A2*0.5)));
    s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B2*0.5)));
  } else if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX))))) {
    vdouble dqh = vtruncate_vd_vd(vmla_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI / (1 << 23)), vcast_vd_d(-M_1_PI / (1 << 24))));
    ql = vrint_vi_vd(vadd_vd_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI)),
                                        vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-(1 << 23)), vcast_vd_d(-0.5))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    ql = vadd_vi_vi_vi(vadd_vi_vi_vi(ql, ql), vcast_vi_i(1));
    const vdouble dql = vcast_vd_vi(ql);

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    s = ddadd2_vd2_vd_vd(u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_B*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_C*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_C*0.5)));
    s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D*0.5)));
  } else {
    ddi_t ddi = rempi(d);
    ql = vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(3));
    ql = vadd_vi_vi_vi(vadd_vi_vi_vi(ql, ql), vsel_vi_vo_vi_vi(vcast_vo32_vo64(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0))), vcast_vi_i(8), vcast_vi_i(7)));
    ql = vsra_vi_vi_i(ql, 1);
    vopmask o = veq_vo_vi_vi(vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(1)), vcast_vi_i(0));
    vdouble y = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0)), vcast_vd_d(0), vcast_vd_d(-1));
    vdouble2 x = vcast_vd2_vd_vd(vmulsign_vd_vd_vd(vcast_vd_d(-3.141592653589793116 * 0.5), y),
                                 vmulsign_vd_vd_vd(vcast_vd_d(-1.2246467991473532072e-16 * 0.5), y));
    x = ddadd2_vd2_vd2_vd2(ddigetdd_vd2_ddi(ddi), x);
    ddi = ddisetdd_ddi_ddi_vd2(ddi, vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(o), x, ddigetdd_vd2_ddi(ddi)));
    s = ddnormalize_vd2_vd2(ddigetdd_vd2_ddi(ddi));
    s = vd2setx_vd2_vd2_vd(s, vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d)), vreinterpret_vm_vd(vd2getx_vd_vd2(s)))));
  }

  t = s;
  s = ddsqu_vd2_vd2(s);

  vdouble s2 = vmul_vd_vd_vd(vd2getx_vd_vd2(s), vd2getx_vd_vd2(s)), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY6(vd2getx_vd_vd2(s), s2, s4,
            2.72052416138529567917983e-15,
            -7.6429259411395447190023e-13,
            1.60589370117277896211623e-10,
            -2.5052106814843123359368e-08,
            2.75573192104428224777379e-06,
            -0.000198412698412046454654947);
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(0.00833333333333318056201922));

  x = ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd2_vd2(ddadd_vd2_vd_vd(vcast_vd_d(-0.166666666666666657414808), vmul_vd_vd_vd(u, vd2getx_vd_vd2(s))), s));
  u = ddmul_vd_vd2_vd2(t, x);

  u = vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(2)), vcast_vi_i(0))), vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(u)));

  return u;

#else // #if !defined(DETERMINISTIC)

  vdouble u;
  vdouble2 s, t, x;
  vint ql;

  vopmask g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2));
  vdouble dql = vrint_vd_vd(vmla_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI), vcast_vd_d(-0.5)));
  dql = vmla_vd_vd_vd_vd(vcast_vd_d(2), dql, vcast_vd_d(1));
  ql = vrint_vi_vd(dql);
  x = ddadd2_vd2_vd_vd(d, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A2*0.5)));
  x = ddadd_vd2_vd2_vd(x, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B2*0.5)));

  if (!LIKELY(vtestallones_i_vo64(g))) {
    vdouble dqh = vtruncate_vd_vd(vmla_vd_vd_vd_vd(d, vcast_vd_d(M_1_PI / (1 << 23)), vcast_vd_d(-M_1_PI / (1 << 24))));
    vint ql2 = vrint_vi_vd(vadd_vd_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(M_1_PI)),
                                         vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-(1 << 23)), vcast_vd_d(-0.5))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    ql2 = vadd_vi_vi_vi(vadd_vi_vi_vi(ql2, ql2), vcast_vi_i(1));
    const vdouble dql = vcast_vd_vi(ql2);

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    s = ddadd2_vd2_vd_vd(u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_B*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_C*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_C*0.5)));
    s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D*0.5)));

    ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ql2);
    x = vsel_vd2_vo_vd2_vd2(g, x, s);
    g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX));

    if (!LIKELY(vtestallones_i_vo64(g))) {
      ddi_t ddi = rempi(d);
      vint ql2 = vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(3));
      ql2 = vadd_vi_vi_vi(vadd_vi_vi_vi(ql2, ql2), vsel_vi_vo_vi_vi(vcast_vo32_vo64(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0))), vcast_vi_i(8), vcast_vi_i(7)));
      ql2 = vsra_vi_vi_i(ql2, 1);
      vopmask o = veq_vo_vi_vi(vand_vi_vi_vi(ddigeti_vi_ddi(ddi), vcast_vi_i(1)), vcast_vi_i(0));
      vdouble y = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vcast_vd_d(0)), vcast_vd_d(0), vcast_vd_d(-1));
      vdouble2 t = vcast_vd2_vd_vd(vmulsign_vd_vd_vd(vcast_vd_d(-3.141592653589793116 * 0.5), y),
                                   vmulsign_vd_vd_vd(vcast_vd_d(-1.2246467991473532072e-16 * 0.5), y));
      t = ddadd2_vd2_vd2_vd2(ddigetdd_vd2_ddi(ddi), t);
      ddi = ddisetdd_ddi_ddi_vd2(ddi, vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(o), t, ddigetdd_vd2_ddi(ddi)));
      s = ddnormalize_vd2_vd2(ddigetdd_vd2_ddi(ddi));
      ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ql2);
      x = vsel_vd2_vo_vd2_vd2(g, x, s);
      x = vd2setx_vd2_vd2_vd(x, vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d)), vreinterpret_vm_vd(vd2getx_vd_vd2(x)))));
    }
  }

  t = x;
  s = ddsqu_vd2_vd2(x);

  vdouble s2 = vmul_vd_vd_vd(vd2getx_vd_vd2(s), vd2getx_vd_vd2(s)), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY6(vd2getx_vd_vd2(s), s2, s4,
            2.72052416138529567917983e-15,
            -7.6429259411395447190023e-13,
            1.60589370117277896211623e-10,
            -2.5052106814843123359368e-08,
            2.75573192104428224777379e-06,
            -0.000198412698412046454654947);
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(0.00833333333333318056201922));

  x = ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd2_vd2(ddadd_vd2_vd_vd(vcast_vd_d(-0.166666666666666657414808), vmul_vd_vd_vd(u, vd2getx_vd_vd2(s))), s));
  u = ddmul_vd_vd2_vd2(t, x);

  u = vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(2)), vcast_vi_i(0))), vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(u)));

  return u;
#endif // #if !defined(DETERMINISTIC)
}

#ifdef ENABLE_GNUABI
#define TYPE2_FUNCATR static INLINE CONST
#define TYPE6_FUNCATR static INLINE CONST
#define SQRTU05_FUNCATR static INLINE CONST
#define XSINCOS sincosk
#define XSINCOS_U1 sincosk_u1
#define XSINCOSPI_U05 sincospik_u05
#define XSINCOSPI_U35 sincospik_u35
#define XMODF modfk
#else
#define TYPE2_FUNCATR EXPORT
#define TYPE6_FUNCATR EXPORT CONST
#define SQRTU05_FUNCATR EXPORT CONST
#define XSINCOS xsincos
#define XSINCOS_U1 xsincos_u1
#define XSINCOSPI_U05 xsincospi_u05
#define XSINCOSPI_U35 xsincospi_u35
#define XMODF xmodf
#endif

TYPE2_FUNCATR VECTOR_CC vdouble2 XSINCOS(vdouble d) {
#if !defined(DETERMINISTIC)
  vopmask o;
  vdouble u, t, rx, ry, s;
  vdouble2 r;
  vint ql;

  if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2))))) {
    vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2 * M_1_PI)));
    ql = vrint_vi_vd(dql);
    s = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2 * 0.5), d);
    s = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B2 * 0.5), s);
  } else if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX))))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    vdouble dql = vrint_vd_vd(vsub_vd_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI)), dqh));
    ql = vrint_vi_vd(dql);

    s = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    s = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A * 0.5), s);
    s = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_B * 0.5), s);
    s = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B * 0.5), s);
    s = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_C * 0.5), s);
    s = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_C * 0.5), s);
    s = vmla_vd_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D * 0.5), s);
  } else {
    ddi_t ddi = rempi(d);
    ql = ddigeti_vi_ddi(ddi);
    s = vadd_vd_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vd2gety_vd_vd2(ddigetdd_vd2_ddi(ddi)));
    s = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d)), vreinterpret_vm_vd(s)));
  }

  t = s;

  s = vmul_vd_vd_vd(s, s);

  u = vcast_vd_d(1.58938307283228937328511e-10);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-2.50506943502539773349318e-08));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(2.75573131776846360512547e-06));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.000198412698278911770864914));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(0.0083333333333191845961746));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.166666666666666130709393));

  rx = vmla_vd_vd_vd_vd(vmul_vd_vd_vd(u, s), t, t);
  rx = vsel_vd_vo_vd_vd(visnegzero_vo_vd(d), vcast_vd_d(-0.0), rx);

  u = vcast_vd_d(-1.13615350239097429531523e-11);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(2.08757471207040055479366e-09));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-2.75573144028847567498567e-07));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(2.48015872890001867311915e-05));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.00138888888888714019282329));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(0.0416666666666665519592062));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.5));

  ry = vmla_vd_vd_vd_vd(s, u, vcast_vd_d(1));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(0)));
  r = vd2setxy_vd2_vd_vd(vsel_vd_vo_vd_vd(o, rx, ry), vsel_vd_vo_vd_vd(o, ry, rx));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(2)), vcast_vi_i(2)));
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(vadd_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(2)), vcast_vi_i(2)));
  r = vd2sety_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2gety_vd_vd2(r)))));

  return r;

#else // #if !defined(DETERMINISTIC)

  vopmask o;
  vdouble u, t, rx, ry, s = d;
  vdouble2 r;
  vint ql;

  vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(s, vcast_vd_d(2 * M_1_PI)));
  ql = vrint_vi_vd(dql);
  s = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2 * 0.5), s);
  s = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B2 * 0.5), s);
  vopmask g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2));

  if (!LIKELY(vtestallones_i_vo64(g))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    vdouble dql = vrint_vd_vd(vsub_vd_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI)), dqh));

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A * 0.5), u);
    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_B * 0.5), u);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B * 0.5), u);
    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_C * 0.5), u);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_C * 0.5), u);
    u = vmla_vd_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D * 0.5), u);

    ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, vrint_vi_vd(dql));
    s = vsel_vd_vo_vd_vd(g, s, u);
    g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX));

    if (!LIKELY(vtestallones_i_vo64(g))) {
      ddi_t ddi = rempi(d);
      u = vadd_vd_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vd2gety_vd_vd2(ddigetdd_vd2_ddi(ddi)));
      u = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d)), vreinterpret_vm_vd(u)));

      ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ddigeti_vi_ddi(ddi));
      s = vsel_vd_vo_vd_vd(g, s, u);
    }
  }

  t = s;

  s = vmul_vd_vd_vd(s, s);

  u = vcast_vd_d(1.58938307283228937328511e-10);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-2.50506943502539773349318e-08));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(2.75573131776846360512547e-06));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.000198412698278911770864914));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(0.0083333333333191845961746));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.166666666666666130709393));

  rx = vmla_vd_vd_vd_vd(vmul_vd_vd_vd(u, s), t, t);
  rx = vsel_vd_vo_vd_vd(visnegzero_vo_vd(d), vcast_vd_d(-0.0), rx);

  u = vcast_vd_d(-1.13615350239097429531523e-11);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(2.08757471207040055479366e-09));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-2.75573144028847567498567e-07));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(2.48015872890001867311915e-05));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.00138888888888714019282329));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(0.0416666666666665519592062));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.5));

  ry = vmla_vd_vd_vd_vd(s, u, vcast_vd_d(1));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(0)));
  r = vd2setxy_vd2_vd_vd(vsel_vd_vo_vd_vd(o, rx, ry), vsel_vd_vo_vd_vd(o, ry, rx));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(2)), vcast_vi_i(2)));
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(vadd_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(2)), vcast_vi_i(2)));
  r = vd2sety_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2gety_vd_vd2(r)))));

  return r;
#endif // #if !defined(DETERMINISTIC)
}

TYPE2_FUNCATR VECTOR_CC vdouble2 XSINCOS_U1(vdouble d) {
#if !defined(DETERMINISTIC)
  vopmask o;
  vdouble u, rx, ry;
  vdouble2 r, s, t, x;
  vint ql;

  if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2))))) {
    const vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2 * M_1_PI)));
    ql = vrint_vi_vd(dql);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2*0.5), d);
    s = ddadd_vd2_vd_vd (u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B2*0.5)));
  } else if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX))))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    const vdouble dql = vrint_vd_vd(vsub_vd_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI)), dqh));
    ql = vrint_vi_vd(dql);

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    s = ddadd_vd2_vd_vd(u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_B*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_C*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_C*0.5)));
    s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D*0.5)));
  } else {
    ddi_t ddi = rempi(d);
    ql = ddigeti_vi_ddi(ddi);
    s = ddigetdd_vd2_ddi(ddi);
    o = vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d));
    s = vd2setxy_vd2_vd_vd(vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2getx_vd_vd2(s)))),
                           vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2gety_vd_vd2(s)))));
  }

  t = s;

  s = vd2setx_vd2_vd2_vd(s, ddsqu_vd_vd2(s));

  u = vcast_vd_d(1.58938307283228937328511e-10);
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-2.50506943502539773349318e-08));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(2.75573131776846360512547e-06));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-0.000198412698278911770864914));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(0.0083333333333191845961746));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-0.166666666666666130709393));

  u = vmul_vd_vd_vd(u, vmul_vd_vd_vd(vd2getx_vd_vd2(s), vd2getx_vd_vd2(t)));

  x = ddadd_vd2_vd2_vd(t, u);
  rx = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  rx = vsel_vd_vo_vd_vd(visnegzero_vo_vd(d), vcast_vd_d(-0.0), rx);

  u = vcast_vd_d(-1.13615350239097429531523e-11);
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(2.08757471207040055479366e-09));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-2.75573144028847567498567e-07));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(2.48015872890001867311915e-05));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-0.00138888888888714019282329));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(0.0416666666666665519592062));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-0.5));

  x = ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd_vd(vd2getx_vd_vd2(s), u));
  ry = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(0)));
  r = vd2setxy_vd2_vd_vd(vsel_vd_vo_vd_vd(o, rx, ry), vsel_vd_vo_vd_vd(o, ry, rx));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(2)), vcast_vi_i(2)));
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(vadd_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(2)), vcast_vi_i(2)));
  r = vd2sety_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2gety_vd_vd2(r)))));

  return r;

#else // #if !defined(DETERMINISTIC)

  vopmask o;
  vdouble u, rx, ry;
  vdouble2 r, s, t, x;
  vint ql;

  const vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2 * M_1_PI)));
  ql = vrint_vi_vd(dql);
  u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2*0.5), d);
  s = ddadd_vd2_vd_vd (u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B2*0.5)));
  vopmask g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2));

  if (!LIKELY(vtestallones_i_vo64(g))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    const vdouble dql = vrint_vd_vd(vsub_vd_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI)), dqh));

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    x = ddadd_vd2_vd_vd(u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A*0.5)));
    x = ddadd2_vd2_vd2_vd(x, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_B*0.5)));
    x = ddadd2_vd2_vd2_vd(x, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B*0.5)));
    x = ddadd2_vd2_vd2_vd(x, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_C*0.5)));
    x = ddadd2_vd2_vd2_vd(x, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_C*0.5)));
    x = ddadd_vd2_vd2_vd(x, vmul_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D*0.5)));

    ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, vrint_vi_vd(dql));
    s = vsel_vd2_vo_vd2_vd2(g, s, x);
    g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX));

    if (!LIKELY(vtestallones_i_vo64(g))) {
      ddi_t ddi = rempi(d);
      x = ddigetdd_vd2_ddi(ddi);
      o = vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d));
      x = vd2setx_vd2_vd2_vd(x, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2getx_vd_vd2(x)))));
      x = vd2sety_vd2_vd2_vd(x, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2gety_vd_vd2(x)))));

      ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ddigeti_vi_ddi(ddi));
      s = vsel_vd2_vo_vd2_vd2(g, s, x);
    }
  }

  t = s;

  s = vd2setx_vd2_vd2_vd(s, ddsqu_vd_vd2(s));

  u = vcast_vd_d(1.58938307283228937328511e-10);
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-2.50506943502539773349318e-08));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(2.75573131776846360512547e-06));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-0.000198412698278911770864914));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(0.0083333333333191845961746));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-0.166666666666666130709393));

  u = vmul_vd_vd_vd(u, vmul_vd_vd_vd(vd2getx_vd_vd2(s), vd2getx_vd_vd2(t)));

  x = ddadd_vd2_vd2_vd(t, u);
  rx = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  rx = vsel_vd_vo_vd_vd(visnegzero_vo_vd(d), vcast_vd_d(-0.0), rx);

  u = vcast_vd_d(-1.13615350239097429531523e-11);
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(2.08757471207040055479366e-09));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-2.75573144028847567498567e-07));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(2.48015872890001867311915e-05));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-0.00138888888888714019282329));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(0.0416666666666665519592062));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(-0.5));

  x = ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd_vd(vd2getx_vd_vd2(s), u));
  ry = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(0)));
  r = vd2setxy_vd2_vd_vd(vsel_vd_vo_vd_vd(o, rx, ry), vsel_vd_vo_vd_vd(o, ry, rx));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(2)), vcast_vi_i(2)));
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(vadd_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(2)), vcast_vi_i(2)));
  r = vd2sety_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2gety_vd_vd2(r)))));

  return r;
#endif // #if !defined(DETERMINISTIC)
}

#if !defined(DETERMINISTIC)
TYPE2_FUNCATR VECTOR_CC vdouble2 XSINCOSPI_U05(vdouble d) {
  vopmask o;
  vdouble u, s, t, rx, ry;
  vdouble2 r, x, s2;

  u = vmul_vd_vd_vd(d, vcast_vd_d(4.0));
  vint q = vtruncate_vi_vd(u);
  q = vand_vi_vi_vi(vadd_vi_vi_vi(q, vxor_vi_vi_vi(vsrl_vi_vi_i(q, 31), vcast_vi_i(1))), vcast_vi_i(~1));
  s = vsub_vd_vd_vd(u, vcast_vd_vi(q));

  t = s;
  s = vmul_vd_vd_vd(s, s);
  s2 = ddmul_vd2_vd_vd(t, t);

  //

  u = vcast_vd_d(-2.02461120785182399295868e-14);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(6.94821830580179461327784e-12));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-1.75724749952853179952664e-09));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(3.13361688966868392878422e-07));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-3.6576204182161551920361e-05));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(0.00249039457019271850274356));
  x = ddadd2_vd2_vd_vd2(vmul_vd_vd_vd(u, s), vcast_vd2_d_d(-0.0807455121882807852484731, 3.61852475067037104849987e-18));
  x = ddadd2_vd2_vd2_vd2(ddmul_vd2_vd2_vd2(s2, x), vcast_vd2_d_d(0.785398163397448278999491, 3.06287113727155002607105e-17));

  x = ddmul_vd2_vd2_vd(x, t);
  rx = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  rx = vsel_vd_vo_vd_vd(visnegzero_vo_vd(d), vcast_vd_d(-0.0), rx);

  //

  u = vcast_vd_d(9.94480387626843774090208e-16);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-3.89796226062932799164047e-13));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(1.15011582539996035266901e-10));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-2.4611369501044697495359e-08));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(3.59086044859052754005062e-06));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.000325991886927389905997954));
  x = ddadd2_vd2_vd_vd2(vmul_vd_vd_vd(u, s), vcast_vd2_d_d(0.0158543442438155018914259, -1.04693272280631521908845e-18));
  x = ddadd2_vd2_vd2_vd2(ddmul_vd2_vd2_vd2(s2, x), vcast_vd2_d_d(-0.308425137534042437259529, -1.95698492133633550338345e-17));

  x = ddadd2_vd2_vd2_vd(ddmul_vd2_vd2_vd2(x, s2), vcast_vd_d(1));
  ry = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  //

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(q, vcast_vi_i(2)), vcast_vi_i(0)));
  r = vd2setxy_vd2_vd_vd(vsel_vd_vo_vd_vd(o, rx, ry), vsel_vd_vo_vd_vd(o, ry, rx));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(q, vcast_vi_i(4)), vcast_vi_i(4)));
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(vadd_vi_vi_vi(q, vcast_vi_i(2)), vcast_vi_i(4)), vcast_vi_i(4)));
  r = vd2sety_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2gety_vd_vd2(r)))));

  o = vgt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX3/4));
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vandnot_vm_vo64_vm(o, vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));
  r = vd2sety_vd2_vd2_vd(r, vsel_vd_vo_vd_vd(o, vcast_vd_d(1), vd2gety_vd_vd2(r)));

  o = visinf_vo_vd(d);
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));
  r = vd2sety_vd2_vd2_vd(r, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2gety_vd_vd2(r)))));

  return r;
}

TYPE2_FUNCATR VECTOR_CC vdouble2 XSINCOSPI_U35(vdouble d) {
  vopmask o;
  vdouble u, s, t, rx, ry;
  vdouble2 r;

  u = vmul_vd_vd_vd(d, vcast_vd_d(4.0));
  vint q = vtruncate_vi_vd(u);
  q = vand_vi_vi_vi(vadd_vi_vi_vi(q, vxor_vi_vi_vi(vsrl_vi_vi_i(q, 31), vcast_vi_i(1))), vcast_vi_i(~1));
  s = vsub_vd_vd_vd(u, vcast_vd_vi(q));

  t = s;
  s = vmul_vd_vd_vd(s, s);

  //

  u = vcast_vd_d(+0.6880638894766060136e-11);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.1757159564542310199e-8));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.3133616327257867311e-6));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.3657620416388486452e-4));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.2490394570189932103e-2));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.8074551218828056320e-1));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.7853981633974482790e+0));

  rx = vmul_vd_vd_vd(u, t);

  //

  u = vcast_vd_d(-0.3860141213683794352e-12);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.1150057888029681415e-9));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.2461136493006663553e-7));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.3590860446623516713e-5));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.3259918869269435942e-3));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.1585434424381541169e-1));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(-0.3084251375340424373e+0));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(1));

  ry = u;

  //

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(q, vcast_vi_i(2)), vcast_vi_i(0)));
  r = vd2setxy_vd2_vd_vd(vsel_vd_vo_vd_vd(o, rx, ry), vsel_vd_vo_vd_vd(o, ry, rx));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(q, vcast_vi_i(4)), vcast_vi_i(4)));
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(vadd_vi_vi_vi(q, vcast_vi_i(2)), vcast_vi_i(4)), vcast_vi_i(4)));
  r = vd2sety_vd2_vd2_vd(r, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2gety_vd_vd2(r)))));

  o = vgt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX3/4));
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vandnot_vm_vo64_vm(o, vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));
  r = vd2sety_vd2_vd2_vd(r, vreinterpret_vd_vm(vandnot_vm_vo64_vm(o, vreinterpret_vm_vd(vd2gety_vd_vd2(r)))));

  o = visinf_vo_vd(d);
  r = vd2setx_vd2_vd2_vd(r, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2getx_vd_vd2(r)))));
  r = vd2sety_vd2_vd2_vd(r, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2gety_vd_vd2(r)))));

  return r;
}

TYPE6_FUNCATR VECTOR_CC vdouble2 XMODF(vdouble x) {
  vdouble fr = vsub_vd_vd_vd(x, vmul_vd_vd_vd(vcast_vd_d(INT64_C(1) << 31), vcast_vd_vi(vtruncate_vi_vd(vmul_vd_vd_vd(x, vcast_vd_d(1.0 / (INT64_C(1) << 31)))))));
  fr = vsub_vd_vd_vd(fr, vcast_vd_vi(vtruncate_vi_vd(fr)));
  fr = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(INT64_C(1) << 52)), vcast_vd_d(0), fr);

  vdouble2 ret;

  ret = vd2setxy_vd2_vd_vd(vcopysign_vd_vd_vd(fr, x), vcopysign_vd_vd_vd(vsub_vd_vd_vd(x, fr), x));

  return ret;
}

#ifdef ENABLE_GNUABI
EXPORT VECTOR_CC void xsincos(vdouble a, double *ps, double *pc) {
  vdouble2 r = sincosk(a);
  vstoreu_v_p_vd(ps, vd2getx_vd_vd2(r));
  vstoreu_v_p_vd(pc, vd2gety_vd_vd2(r));
}

EXPORT VECTOR_CC void xsincos_u1(vdouble a, double *ps, double *pc) {
  vdouble2 r = sincosk_u1(a);
  vstoreu_v_p_vd(ps, vd2getx_vd_vd2(r));
  vstoreu_v_p_vd(pc, vd2gety_vd_vd2(r));
}

EXPORT VECTOR_CC void xsincospi_u05(vdouble a, double *ps, double *pc) {
  vdouble2 r = sincospik_u05(a);
  vstoreu_v_p_vd(ps, vd2getx_vd_vd2(r));
  vstoreu_v_p_vd(pc, vd2gety_vd_vd2(r));
}

EXPORT VECTOR_CC void xsincospi_u35(vdouble a, double *ps, double *pc) {
  vdouble2 r = sincospik_u35(a);
  vstoreu_v_p_vd(ps, vd2getx_vd_vd2(r));
  vstoreu_v_p_vd(pc, vd2gety_vd_vd2(r));
}

EXPORT CONST VECTOR_CC vdouble xmodf(vdouble a, double *iptr) {
  vdouble2 r = modfk(a);
  vstoreu_v_p_vd(iptr, vd2gety_vd_vd2(r));
  return vd2getx_vd_vd2(r);
}
#endif // #ifdef ENABLE_GNUABI
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vdouble2 sinpik(vdouble d) {
  vopmask o;
  vdouble u, s, t;
  vdouble2 x, s2;

  u = vmul_vd_vd_vd(d, vcast_vd_d(4.0));
  vint q = vtruncate_vi_vd(u);
  q = vand_vi_vi_vi(vadd_vi_vi_vi(q, vxor_vi_vi_vi(vsrl_vi_vi_i(q, 31), vcast_vi_i(1))), vcast_vi_i(~1));
  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(q, vcast_vi_i(2)), vcast_vi_i(2)));

  s = vsub_vd_vd_vd(u, vcast_vd_vi(q));
  t = s;
  s = vmul_vd_vd_vd(s, s);
  s2 = ddmul_vd2_vd_vd(t, t);

  //

  u = vsel_vd_vo_d_d(o, 9.94480387626843774090208e-16, -2.02461120785182399295868e-14);
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, -3.89796226062932799164047e-13, 6.948218305801794613277840e-12));
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, 1.150115825399960352669010e-10, -1.75724749952853179952664e-09));
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, -2.46113695010446974953590e-08, 3.133616889668683928784220e-07));
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, 3.590860448590527540050620e-06, -3.65762041821615519203610e-05));
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, -0.000325991886927389905997954, 0.0024903945701927185027435600));
  x = ddadd2_vd2_vd_vd2(vmul_vd_vd_vd(u, s),
                        vsel_vd2_vo_d_d_d_d(o, 0.0158543442438155018914259, -1.04693272280631521908845e-18,
                                            -0.0807455121882807852484731, 3.61852475067037104849987e-18));
  x = ddadd2_vd2_vd2_vd2(ddmul_vd2_vd2_vd2(s2, x),
                         vsel_vd2_vo_d_d_d_d(o, -0.308425137534042437259529, -1.95698492133633550338345e-17,
                                             0.785398163397448278999491, 3.06287113727155002607105e-17));

  x = ddmul_vd2_vd2_vd2(x, vsel_vd2_vo_vd2_vd2(o, s2, vcast_vd2_vd_vd(t, vcast_vd_d(0))));
  x = vsel_vd2_vo_vd2_vd2(o, ddadd2_vd2_vd2_vd(x, vcast_vd_d(1)), x);

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(q, vcast_vi_i(4)), vcast_vi_i(4)));
  x = vd2setx_vd2_vd2_vd(x, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2getx_vd_vd2(x)))));
  x = vd2sety_vd2_vd2_vd(x, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2gety_vd_vd2(x)))));

  return x;
}

EXPORT CONST VECTOR_CC vdouble xsinpi_u05(vdouble d) {
  vdouble2 x = sinpik(d);
  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  r = vsel_vd_vo_vd_vd(visnegzero_vo_vd(d), vcast_vd_d(-0.0), r);
  r = vreinterpret_vd_vm(vandnot_vm_vo64_vm(vgt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX3/4)), vreinterpret_vm_vd(r)));
  r = vreinterpret_vd_vm(vor_vm_vo64_vm(visinf_vo_vd(d), vreinterpret_vm_vd(r)));

  return r;
}

static INLINE CONST VECTOR_CC vdouble2 cospik(vdouble d) {
  vopmask o;
  vdouble u, s, t;
  vdouble2 x, s2;

  u = vmul_vd_vd_vd(d, vcast_vd_d(4.0));
  vint q = vtruncate_vi_vd(u);
  q = vand_vi_vi_vi(vadd_vi_vi_vi(q, vxor_vi_vi_vi(vsrl_vi_vi_i(q, 31), vcast_vi_i(1))), vcast_vi_i(~1));
  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(q, vcast_vi_i(2)), vcast_vi_i(0)));

  s = vsub_vd_vd_vd(u, vcast_vd_vi(q));
  t = s;
  s = vmul_vd_vd_vd(s, s);
  s2 = ddmul_vd2_vd_vd(t, t);

  //

  u = vsel_vd_vo_d_d(o, 9.94480387626843774090208e-16, -2.02461120785182399295868e-14);
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, -3.89796226062932799164047e-13, 6.948218305801794613277840e-12));
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, 1.150115825399960352669010e-10, -1.75724749952853179952664e-09));
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, -2.46113695010446974953590e-08, 3.133616889668683928784220e-07));
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, 3.590860448590527540050620e-06, -3.65762041821615519203610e-05));
  u = vmla_vd_vd_vd_vd(u, s, vsel_vd_vo_d_d(o, -0.000325991886927389905997954, 0.0024903945701927185027435600));
  x = ddadd2_vd2_vd_vd2(vmul_vd_vd_vd(u, s),
                        vsel_vd2_vo_d_d_d_d(o, 0.0158543442438155018914259, -1.04693272280631521908845e-18,
                                            -0.0807455121882807852484731, 3.61852475067037104849987e-18));
  x = ddadd2_vd2_vd2_vd2(ddmul_vd2_vd2_vd2(s2, x),
                         vsel_vd2_vo_d_d_d_d(o, -0.308425137534042437259529, -1.95698492133633550338345e-17,
                                             0.785398163397448278999491, 3.06287113727155002607105e-17));

  x = ddmul_vd2_vd2_vd2(x, vsel_vd2_vo_vd2_vd2(o, s2, vcast_vd2_vd_vd(t, vcast_vd_d(0))));
  x = vsel_vd2_vo_vd2_vd2(o, ddadd2_vd2_vd2_vd(x, vcast_vd_d(1)), x);

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(vadd_vi_vi_vi(q, vcast_vi_i(2)), vcast_vi_i(4)), vcast_vi_i(4)));
  x = vd2setx_vd2_vd2_vd(x, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2getx_vd_vd2(x)))));
  x = vd2sety_vd2_vd2_vd(x, vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(o, vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vd2gety_vd_vd2(x)))));

  return x;
}

EXPORT CONST VECTOR_CC vdouble xcospi_u05(vdouble d) {
  vdouble2 x = cospik(d);
  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  r = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX3/4)), vcast_vd_d(1), r);
  r = vreinterpret_vd_vm(vor_vm_vo64_vm(visinf_vo_vd(d), vreinterpret_vm_vd(r)));

  return r;
}

EXPORT CONST VECTOR_CC vdouble xtan(vdouble d) {
#if !defined(DETERMINISTIC)
  vdouble u, s, x, y;
  vopmask o;
  vint ql;

  if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2))))) {
    vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2 * M_1_PI)));
    ql = vrint_vi_vd(dql);
    x = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2 * 0.5), d);
    x = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B2 * 0.5), x);
  } else if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(1e+6))))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    vdouble dql = vrint_vd_vd(vsub_vd_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI)), dqh));
    ql = vrint_vi_vd(dql);

    x = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    x = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A * 0.5), x);
    x = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_B * 0.5), x);
    x = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B * 0.5), x);
    x = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_C * 0.5), x);
    x = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_C * 0.5), x);
    x = vmla_vd_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D * 0.5), x);
  } else {
    ddi_t ddi = rempi(d);
    ql = ddigeti_vi_ddi(ddi);
    x = vadd_vd_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vd2gety_vd_vd2(ddigetdd_vd2_ddi(ddi)));
    x = vreinterpret_vd_vm(vor_vm_vo64_vm(visinf_vo_vd(d), vreinterpret_vm_vd(x)));
    x = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d)), vreinterpret_vm_vd(x)));
  }

  x = vmul_vd_vd_vd(x, vcast_vd_d(0.5));
  s = vmul_vd_vd_vd(x, x);

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY8(s, s2, s4,
             +0.3245098826639276316e-3,
             +0.5619219738114323735e-3,
             +0.1460781502402784494e-2,
             +0.3591611540792499519e-2,
             +0.8863268409563113126e-2,
             +0.2186948728185535498e-1,
             +0.5396825399517272970e-1,
             +0.1333333333330500581e+0);

  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.3333333333333343695e+0));
  u = vmla_vd_vd_vd_vd(s, vmul_vd_vd_vd(u, x), x);

  y = vmla_vd_vd_vd_vd(u, u, vcast_vd_d(-1));
  x = vmul_vd_vd_vd(u, vcast_vd_d(-2));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(1)));
  u = vdiv_vd_vd_vd(vsel_vd_vo_vd_vd(o, vneg_vd_vd(y), x),
                    vsel_vd_vo_vd_vd(o, x, y));
  u = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), d, u);

  return u;

#else // #if !defined(DETERMINISTIC)

  vdouble u, s, x, y;
  vopmask o;
  vint ql;

  vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2 * M_1_PI)));
  ql = vrint_vi_vd(dql);
  s = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2 * 0.5), d);
  s = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B2 * 0.5), s);
  vopmask g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2));

  if (!LIKELY(vtestallones_i_vo64(g))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    vdouble dql = vrint_vd_vd(vsub_vd_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI)), dqh));

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A * 0.5), u);
    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_B * 0.5), u);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_B * 0.5), u);
    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_C * 0.5), u);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_C * 0.5), u);
    u = vmla_vd_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D * 0.5), u);

    ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, vrint_vi_vd(dql));
    s = vsel_vd_vo_vd_vd(g, s, u);
    g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(1e+6));

    if (!LIKELY(vtestallones_i_vo64(g))) {
      ddi_t ddi = rempi(d);
      vint ql2 = ddigeti_vi_ddi(ddi);
      u = vadd_vd_vd_vd(vd2getx_vd_vd2(ddigetdd_vd2_ddi(ddi)), vd2gety_vd_vd2(ddigetdd_vd2_ddi(ddi)));
      u = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d)), vreinterpret_vm_vd(u)));

      ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ql2);
      s = vsel_vd_vo_vd_vd(g, s, u);
    }
  }

  x = vmul_vd_vd_vd(s, vcast_vd_d(0.5));
  s = vmul_vd_vd_vd(x, x);

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY8(s, s2, s4,
             +0.3245098826639276316e-3,
             +0.5619219738114323735e-3,
             +0.1460781502402784494e-2,
             +0.3591611540792499519e-2,
             +0.8863268409563113126e-2,
             +0.2186948728185535498e-1,
             +0.5396825399517272970e-1,
             +0.1333333333330500581e+0);

  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.3333333333333343695e+0));
  u = vmla_vd_vd_vd_vd(s, vmul_vd_vd_vd(u, x), x);

  y = vmla_vd_vd_vd_vd(u, u, vcast_vd_d(-1));
  x = vmul_vd_vd_vd(u, vcast_vd_d(-2));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(1)));
  u = vdiv_vd_vd_vd(vsel_vd_vo_vd_vd(o, vneg_vd_vd(y), x),
                    vsel_vd_vo_vd_vd(o, x, y));
  u = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), d, u);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

EXPORT CONST VECTOR_CC vdouble xtan_u1(vdouble d) {
#if !defined(DETERMINISTIC)
  vdouble u;
  vdouble2 s, t, x, y;
  vopmask o;
  vint ql;

  if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2))))) {
    vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2 * M_1_PI)));
    ql = vrint_vi_vd(dql);
    u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2*0.5), d);
    s = ddadd_vd2_vd_vd (u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B2*0.5)));
  } else if (LIKELY(vtestallones_i_vo64(vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX))))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    s = ddadd2_vd2_vd2_vd(ddmul_vd2_vd2_vd(vcast_vd2_d_d(M_2_PI_H, M_2_PI_L), d),
                          vsub_vd_vd_vd(vsel_vd_vo_vd_vd(vlt_vo_vd_vd(d, vcast_vd_d(0)),
                                                         vcast_vd_d(-0.5), vcast_vd_d(0.5)), dqh));
    const vdouble dql = vtruncate_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(s), vd2gety_vd_vd2(s)));
    ql = vrint_vi_vd(dql);

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    s = ddadd_vd2_vd_vd(u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A*0.5            )));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_B*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B*0.5            )));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_C*0.5)));
    s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_C*0.5            )));
    s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D*0.5)));
  } else {
    ddi_t ddi = rempi(d);
    ql = ddigeti_vi_ddi(ddi);
    s = ddigetdd_vd2_ddi(ddi);
    o = vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d));
    s = vd2setx_vd2_vd2_vd(s, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2getx_vd_vd2(s)))));
    s = vd2sety_vd2_vd2_vd(s, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2gety_vd_vd2(s)))));
  }

  t = ddscale_vd2_vd2_vd(s, vcast_vd_d(0.5));
  s = ddsqu_vd2_vd2(t);

  vdouble s2 = vmul_vd_vd_vd(vd2getx_vd_vd2(s), vd2getx_vd_vd2(s)), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY8(vd2getx_vd_vd2(s), s2, s4,
             +0.3245098826639276316e-3,
             +0.5619219738114323735e-3,
             +0.1460781502402784494e-2,
             +0.3591611540792499519e-2,
             +0.8863268409563113126e-2,
             +0.2186948728185535498e-1,
             +0.5396825399517272970e-1,
             +0.1333333333330500581e+0);

  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(+0.3333333333333343695e+0));
  x = ddadd_vd2_vd2_vd2(t, ddmul_vd2_vd2_vd(ddmul_vd2_vd2_vd2(s, t), u));

  y = ddadd_vd2_vd_vd2(vcast_vd_d(-1), ddsqu_vd2_vd2(x));
  x = ddscale_vd2_vd2_vd(x, vcast_vd_d(-2));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(1)));

  x = dddiv_vd2_vd2_vd2(vsel_vd2_vo_vd2_vd2(o, ddneg_vd2_vd2(y), x),
                        vsel_vd2_vo_vd2_vd2(o, x, y));

  u = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  u = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), d, u);

  return u;

#else // #if !defined(DETERMINISTIC)

  vdouble u;
  vdouble2 s, t, x, y;
  vopmask o;
  vint ql;

  const vdouble dql = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2 * M_1_PI)));
  ql = vrint_vi_vd(dql);
  u = vmla_vd_vd_vd_vd(dql, vcast_vd_d(-PI_A2*0.5), d);
  s = ddadd_vd2_vd_vd (u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B2*0.5)));
  vopmask g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX2));

  if (!LIKELY(vtestallones_i_vo64(g))) {
    vdouble dqh = vtruncate_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(2*M_1_PI / (1 << 24))));
    dqh = vmul_vd_vd_vd(dqh, vcast_vd_d(1 << 24));
    x = ddadd2_vd2_vd2_vd(ddmul_vd2_vd2_vd(vcast_vd2_d_d(M_2_PI_H, M_2_PI_L), d),
                          vsub_vd_vd_vd(vsel_vd_vo_vd_vd(vlt_vo_vd_vd(d, vcast_vd_d(0)),
                                                         vcast_vd_d(-0.5), vcast_vd_d(0.5)), dqh));
    const vdouble dql = vtruncate_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x)));

    u = vmla_vd_vd_vd_vd(dqh, vcast_vd_d(-PI_A * 0.5), d);
    x = ddadd_vd2_vd_vd(u, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_A*0.5            )));
    x = ddadd2_vd2_vd2_vd(x, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_B*0.5)));
    x = ddadd2_vd2_vd2_vd(x, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_B*0.5            )));
    x = ddadd2_vd2_vd2_vd(x, vmul_vd_vd_vd(dqh, vcast_vd_d(-PI_C*0.5)));
    x = ddadd2_vd2_vd2_vd(x, vmul_vd_vd_vd(dql, vcast_vd_d(-PI_C*0.5            )));
    x = ddadd_vd2_vd2_vd(x, vmul_vd_vd_vd(vadd_vd_vd_vd(dqh, dql), vcast_vd_d(-PI_D*0.5)));

    ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, vrint_vi_vd(dql));
    s = vsel_vd2_vo_vd2_vd2(g, s, x);
    g = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(TRIGRANGEMAX));

    if (!LIKELY(vtestallones_i_vo64(g))) {
      ddi_t ddi = rempi(d);
      x = ddigetdd_vd2_ddi(ddi);
      o = vor_vo_vo_vo(visinf_vo_vd(d), visnan_vo_vd(d));
      x = vd2setx_vd2_vd2_vd(x, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2getx_vd_vd2(x)))));
      x = vd2sety_vd2_vd2_vd(x, vreinterpret_vd_vm(vor_vm_vo64_vm(o, vreinterpret_vm_vd(vd2gety_vd_vd2(x)))));

      ql = vsel_vi_vo_vi_vi(vcast_vo32_vo64(g), ql, ddigeti_vi_ddi(ddi));
      s = vsel_vd2_vo_vd2_vd2(g, s, x);
    }
  }

  t = ddscale_vd2_vd2_vd(s, vcast_vd_d(0.5));
  s = ddsqu_vd2_vd2(t);

  vdouble s2 = vmul_vd_vd_vd(vd2getx_vd_vd2(s), vd2getx_vd_vd2(s)), s4 = vmul_vd_vd_vd(s2, s2);
  u = POLY8(vd2getx_vd_vd2(s), s2, s4,
             +0.3245098826639276316e-3,
             +0.5619219738114323735e-3,
             +0.1460781502402784494e-2,
             +0.3591611540792499519e-2,
             +0.8863268409563113126e-2,
             +0.2186948728185535498e-1,
             +0.5396825399517272970e-1,
             +0.1333333333330500581e+0);

  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(s), vcast_vd_d(+0.3333333333333343695e+0));
  x = ddadd_vd2_vd2_vd2(t, ddmul_vd2_vd2_vd(ddmul_vd2_vd2_vd2(s, t), u));

  y = ddadd_vd2_vd_vd2(vcast_vd_d(-1), ddsqu_vd2_vd2(x));
  x = ddscale_vd2_vd2_vd(x, vcast_vd_d(-2));

  o = vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(ql, vcast_vi_i(1)), vcast_vi_i(1)));

  x = dddiv_vd2_vd2_vd2(vsel_vd2_vo_vd2_vd2(o, ddneg_vd2_vd2(y), x),
                        vsel_vd2_vo_vd2_vd2(o, x, y));

  u = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x));

  u = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), d, u);

  return u;
#endif // #if !defined(DETERMINISTIC)
}

static INLINE CONST VECTOR_CC vdouble atan2k(vdouble y, vdouble x) {
  vdouble s, t, u;
  vint q;
  vopmask p;

  q = vsel_vi_vd_vi(x, vcast_vi_i(-2));
  x = vabs_vd_vd(x);

  q = vsel_vi_vd_vd_vi_vi(x, y, vadd_vi_vi_vi(q, vcast_vi_i(1)), q);
  p = vlt_vo_vd_vd(x, y);
  s = vsel_vd_vo_vd_vd(p, vneg_vd_vd(x), y);
  t = vmax_vd_vd_vd(x, y);

  s = vdiv_vd_vd_vd(s, t);
  t = vmul_vd_vd_vd(s, s);

  vdouble t2 = vmul_vd_vd_vd(t, t), t4 = vmul_vd_vd_vd(t2, t2), t8 = vmul_vd_vd_vd(t4, t4), t16 = vmul_vd_vd_vd(t8, t8);
  u = POLY19(t, t2, t4, t8, t16,
             -1.88796008463073496563746e-05,
             0.000209850076645816976906797,
             -0.00110611831486672482563471,
             0.00370026744188713119232403,
             -0.00889896195887655491740809,
             0.016599329773529201970117,
             -0.0254517624932312641616861,
             0.0337852580001353069993897,
             -0.0407629191276836500001934,
             0.0466667150077840625632675,
             -0.0523674852303482457616113,
             0.0587666392926673580854313,
             -0.0666573579361080525984562,
             0.0769219538311769618355029,
             -0.090908995008245008229153,
             0.111111105648261418443745,
             -0.14285714266771329383765,
             0.199999999996591265594148,
             -0.333333333333311110369124);

  t = vmla_vd_vd_vd_vd(s, vmul_vd_vd_vd(t, u), s);
  t = vmla_vd_vd_vd_vd(vcast_vd_vi(q), vcast_vd_d(M_PI/2), t);

  return t;
}

static INLINE CONST VECTOR_CC vdouble2 atan2k_u1(vdouble2 y, vdouble2 x) {
  vdouble u;
  vdouble2 s, t;
  vint q;
  vopmask p;

  q = vsel_vi_vd_vi(vd2getx_vd_vd2(x), vcast_vi_i(-2));
  p = vlt_vo_vd_vd(vd2getx_vd_vd2(x), vcast_vd_d(0));
  vmask b = vand_vm_vo64_vm(p, vreinterpret_vm_vd(vcast_vd_d(-0.0)));
  x = vd2setx_vd2_vd2_vd(x, vreinterpret_vd_vm(vxor_vm_vm_vm(b, vreinterpret_vm_vd(vd2getx_vd_vd2(x)))));
  x = vd2sety_vd2_vd2_vd(x, vreinterpret_vd_vm(vxor_vm_vm_vm(b, vreinterpret_vm_vd(vd2gety_vd_vd2(x)))));

  q = vsel_vi_vd_vd_vi_vi(vd2getx_vd_vd2(x), vd2getx_vd_vd2(y), vadd_vi_vi_vi(q, vcast_vi_i(1)), q);
  p = vlt_vo_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(y));
  s = vsel_vd2_vo_vd2_vd2(p, ddneg_vd2_vd2(x), y);
  t = vsel_vd2_vo_vd2_vd2(p, y, x);

  s = dddiv_vd2_vd2_vd2(s, t);
  t = ddsqu_vd2_vd2(s);
  t = ddnormalize_vd2_vd2(t);

  vdouble t2 = vmul_vd_vd_vd(vd2getx_vd_vd2(t), vd2getx_vd_vd2(t)), t4 = vmul_vd_vd_vd(t2, t2), t8 = vmul_vd_vd_vd(t4, t4);
  u = POLY16(vd2getx_vd_vd2(t), t2, t4, t8,
             1.06298484191448746607415e-05,
             -0.000125620649967286867384336,
             0.00070557664296393412389774,
             -0.00251865614498713360352999,
             0.00646262899036991172313504,
             -0.0128281333663399031014274,
             0.0208024799924145797902497,
             -0.0289002344784740315686289,
             0.0359785005035104590853656,
             -0.041848579703592507506027,
             0.0470843011653283988193763,
             -0.0524914210588448421068719,
             0.0587946590969581003860434,
             -0.0666620884778795497194182,
             0.0769225330296203768654095,
             -0.0909090442773387574781907);
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(t), vcast_vd_d(0.111111108376896236538123));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(t), vcast_vd_d(-0.142857142756268568062339));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(t), vcast_vd_d(0.199999999997977351284817));
  u = vmla_vd_vd_vd_vd(u, vd2getx_vd_vd2(t), vcast_vd_d(-0.333333333333317605173818));

  t = ddadd_vd2_vd2_vd2(s, ddmul_vd2_vd2_vd(ddmul_vd2_vd2_vd2(s, t), u));

  t = ddadd_vd2_vd2_vd2(ddmul_vd2_vd2_vd(vcast_vd2_d_d(1.570796326794896557998982, 6.12323399573676603586882e-17), vcast_vd_vi(q)), t);

  return t;
}

static INLINE CONST VECTOR_CC vdouble visinf2_vd_vd_vd(vdouble d, vdouble m) {
  return vreinterpret_vd_vm(vand_vm_vo64_vm(visinf_vo_vd(d), vor_vm_vm_vm(vand_vm_vm_vm(vreinterpret_vm_vd(d), vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(m))));
}

EXPORT CONST VECTOR_CC vdouble xatan2(vdouble y, vdouble x) {
  vdouble r = atan2k(vabs_vd_vd(y), x);

  r = vmulsign_vd_vd_vd(r, x);
  r = vsel_vd_vo_vd_vd(vor_vo_vo_vo(visinf_vo_vd(x), veq_vo_vd_vd(x, vcast_vd_d(0))), vsub_vd_vd_vd(vcast_vd_d(M_PI/2), visinf2_vd_vd_vd(x, vmulsign_vd_vd_vd(vcast_vd_d(M_PI/2), x))), r);
  r = vsel_vd_vo_vd_vd(visinf_vo_vd(y), vsub_vd_vd_vd(vcast_vd_d(M_PI/2), visinf2_vd_vd_vd(x, vmulsign_vd_vd_vd(vcast_vd_d(M_PI/4), x))), r);
  r = vsel_vd_vo_vd_vd(veq_vo_vd_vd(y, vcast_vd_d(0.0)), vreinterpret_vd_vm(vand_vm_vo64_vm(vsignbit_vo_vd(x), vreinterpret_vm_vd(vcast_vd_d(M_PI)))), r);

  r = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visnan_vo_vd(x), visnan_vo_vd(y)), vreinterpret_vm_vd(vmulsign_vd_vd_vd(r, y))));
  return r;
}

EXPORT CONST VECTOR_CC vdouble xatan2_u1(vdouble y, vdouble x) {
  vopmask o = vlt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(5.5626846462680083984e-309)); // nexttoward((1.0 / DBL_MAX), 1)
  x = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(x, vcast_vd_d(UINT64_C(1) << 53)), x);
  y = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(y, vcast_vd_d(UINT64_C(1) << 53)), y);

  vdouble2 d = atan2k_u1(vcast_vd2_vd_vd(vabs_vd_vd(y), vcast_vd_d(0)), vcast_vd2_vd_vd(x, vcast_vd_d(0)));
  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d));

  r = vmulsign_vd_vd_vd(r, x);
  r = vsel_vd_vo_vd_vd(vor_vo_vo_vo(visinf_vo_vd(x), veq_vo_vd_vd(x, vcast_vd_d(0))), vsub_vd_vd_vd(vcast_vd_d(M_PI/2), visinf2_vd_vd_vd(x, vmulsign_vd_vd_vd(vcast_vd_d(M_PI/2), x))), r);
  r = vsel_vd_vo_vd_vd(visinf_vo_vd(y), vsub_vd_vd_vd(vcast_vd_d(M_PI/2), visinf2_vd_vd_vd(x, vmulsign_vd_vd_vd(vcast_vd_d(M_PI/4), x))), r);
  r = vsel_vd_vo_vd_vd(veq_vo_vd_vd(y, vcast_vd_d(0.0)), vreinterpret_vd_vm(vand_vm_vo64_vm(vsignbit_vo_vd(x), vreinterpret_vm_vd(vcast_vd_d(M_PI)))), r);

  r = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visnan_vo_vd(x), visnan_vo_vd(y)), vreinterpret_vm_vd(vmulsign_vd_vd_vd(r, y))));
  return r;
}

EXPORT CONST VECTOR_CC vdouble xasin(vdouble d) {
  vopmask o = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(0.5));
  vdouble x2 = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, d), vmul_vd_vd_vd(vsub_vd_vd_vd(vcast_vd_d(1), vabs_vd_vd(d)), vcast_vd_d(0.5)));
  vdouble x = vsel_vd_vo_vd_vd(o, vabs_vd_vd(d), vsqrt_vd_vd(x2)), u;

  vdouble x4 = vmul_vd_vd_vd(x2, x2), x8 = vmul_vd_vd_vd(x4, x4), x16 = vmul_vd_vd_vd(x8, x8);
  u = POLY12(x2, x4, x8, x16,
             +0.3161587650653934628e-1,
             -0.1581918243329996643e-1,
             +0.1929045477267910674e-1,
             +0.6606077476277170610e-2,
             +0.1215360525577377331e-1,
             +0.1388715184501609218e-1,
             +0.1735956991223614604e-1,
             +0.2237176181932048341e-1,
             +0.3038195928038132237e-1,
             +0.4464285681377102438e-1,
             +0.7500000000378581611e-1,
             +0.1666666666666497543e+0);

  u = vmla_vd_vd_vd_vd(u, vmul_vd_vd_vd(x, x2), x);

  vdouble r = vsel_vd_vo_vd_vd(o, u, vmla_vd_vd_vd_vd(u, vcast_vd_d(-2), vcast_vd_d(M_PI/2)));
  return vmulsign_vd_vd_vd(r, d);
}

EXPORT CONST VECTOR_CC vdouble xasin_u1(vdouble d) {
  vopmask o = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(0.5));
  vdouble x2 = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, d), vmul_vd_vd_vd(vsub_vd_vd_vd(vcast_vd_d(1), vabs_vd_vd(d)), vcast_vd_d(0.5))), u;
  vdouble2 x = vsel_vd2_vo_vd2_vd2(o, vcast_vd2_vd_vd(vabs_vd_vd(d), vcast_vd_d(0)), ddsqrt_vd2_vd(x2));
  x = vsel_vd2_vo_vd2_vd2(veq_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(1.0)), vcast_vd2_d_d(0, 0), x);

  vdouble x4 = vmul_vd_vd_vd(x2, x2), x8 = vmul_vd_vd_vd(x4, x4), x16 = vmul_vd_vd_vd(x8, x8);
  u = POLY12(x2, x4, x8, x16,
             +0.3161587650653934628e-1,
             -0.1581918243329996643e-1,
             +0.1929045477267910674e-1,
             +0.6606077476277170610e-2,
             +0.1215360525577377331e-1,
             +0.1388715184501609218e-1,
             +0.1735956991223614604e-1,
             +0.2237176181932048341e-1,
             +0.3038195928038132237e-1,
             +0.4464285681377102438e-1,
             +0.7500000000378581611e-1,
             +0.1666666666666497543e+0);

  u = vmul_vd_vd_vd(u, vmul_vd_vd_vd(x2, vd2getx_vd_vd2(x)));

  vdouble2 y = ddsub_vd2_vd2_vd(ddsub_vd2_vd2_vd2(vcast_vd2_d_d(3.141592653589793116/4, 1.2246467991473532072e-16/4), x), u);

  vdouble r = vsel_vd_vo_vd_vd(o, vadd_vd_vd_vd(u, vd2getx_vd_vd2(x)),
                               vmul_vd_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(y), vd2gety_vd_vd2(y)), vcast_vd_d(2)));
  return vmulsign_vd_vd_vd(r, d);
}

EXPORT CONST VECTOR_CC vdouble xacos(vdouble d) {
  vopmask o = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(0.5));
  vdouble x2 = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, d),
                                vmul_vd_vd_vd(vsub_vd_vd_vd(vcast_vd_d(1), vabs_vd_vd(d)), vcast_vd_d(0.5))), u;
  vdouble x = vsel_vd_vo_vd_vd(o, vabs_vd_vd(d), vsqrt_vd_vd(x2));
  x = vsel_vd_vo_vd_vd(veq_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(1.0)), vcast_vd_d(0), x);

  vdouble x4 = vmul_vd_vd_vd(x2, x2), x8 = vmul_vd_vd_vd(x4, x4), x16 = vmul_vd_vd_vd(x8, x8);
  u = POLY12(x2, x4, x8, x16,
             +0.3161587650653934628e-1,
             -0.1581918243329996643e-1,
             +0.1929045477267910674e-1,
             +0.6606077476277170610e-2,
             +0.1215360525577377331e-1,
             +0.1388715184501609218e-1,
             +0.1735956991223614604e-1,
             +0.2237176181932048341e-1,
             +0.3038195928038132237e-1,
             +0.4464285681377102438e-1,
             +0.7500000000378581611e-1,
             +0.1666666666666497543e+0);

  u = vmul_vd_vd_vd(u, vmul_vd_vd_vd(x2, x));

  vdouble y = vsub_vd_vd_vd(vcast_vd_d(M_PI/2), vadd_vd_vd_vd(vmulsign_vd_vd_vd(x, d), vmulsign_vd_vd_vd(u, d)));
  x = vadd_vd_vd_vd(x, u);
  vdouble r = vsel_vd_vo_vd_vd(o, y, vmul_vd_vd_vd(x, vcast_vd_d(2)));
  return vsel_vd_vo_vd_vd(vandnot_vo_vo_vo(o, vlt_vo_vd_vd(d, vcast_vd_d(0))),
                          vd2getx_vd_vd2(ddadd_vd2_vd2_vd(vcast_vd2_d_d(3.141592653589793116, 1.2246467991473532072e-16),
                                                          vneg_vd_vd(r))), r);
}

EXPORT CONST VECTOR_CC vdouble xacos_u1(vdouble d) {
  vopmask o = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(0.5));
  vdouble x2 = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, d), vmul_vd_vd_vd(vsub_vd_vd_vd(vcast_vd_d(1), vabs_vd_vd(d)), vcast_vd_d(0.5))), u;
  vdouble2 x = vsel_vd2_vo_vd2_vd2(o, vcast_vd2_vd_vd(vabs_vd_vd(d), vcast_vd_d(0)), ddsqrt_vd2_vd(x2));
  x = vsel_vd2_vo_vd2_vd2(veq_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(1.0)), vcast_vd2_d_d(0, 0), x);

  vdouble x4 = vmul_vd_vd_vd(x2, x2), x8 = vmul_vd_vd_vd(x4, x4), x16 = vmul_vd_vd_vd(x8, x8);
  u = POLY12(x2, x4, x8, x16,
             +0.3161587650653934628e-1,
             -0.1581918243329996643e-1,
             +0.1929045477267910674e-1,
             +0.6606077476277170610e-2,
             +0.1215360525577377331e-1,
             +0.1388715184501609218e-1,
             +0.1735956991223614604e-1,
             +0.2237176181932048341e-1,
             +0.3038195928038132237e-1,
             +0.4464285681377102438e-1,
             +0.7500000000378581611e-1,
             +0.1666666666666497543e+0);

  u = vmul_vd_vd_vd(u, vmul_vd_vd_vd(x2, vd2getx_vd_vd2(x)));

  vdouble2 y = ddsub_vd2_vd2_vd2(vcast_vd2_d_d(3.141592653589793116/2, 1.2246467991473532072e-16/2),
                                 ddadd_vd2_vd_vd(vmulsign_vd_vd_vd(vd2getx_vd_vd2(x), d), vmulsign_vd_vd_vd(u, d)));
  x = ddadd_vd2_vd2_vd(x, u);

  y = vsel_vd2_vo_vd2_vd2(o, y, ddscale_vd2_vd2_vd(x, vcast_vd_d(2)));

  y = vsel_vd2_vo_vd2_vd2(vandnot_vo_vo_vo(o, vlt_vo_vd_vd(d, vcast_vd_d(0))),
                          ddsub_vd2_vd2_vd2(vcast_vd2_d_d(3.141592653589793116, 1.2246467991473532072e-16), y), y);

  return vadd_vd_vd_vd(vd2getx_vd_vd2(y), vd2gety_vd_vd2(y));
}

EXPORT CONST VECTOR_CC vdouble xatan_u1(vdouble d) {
  vdouble2 d2 = atan2k_u1(vcast_vd2_vd_vd(vabs_vd_vd(d), vcast_vd_d(0)), vcast_vd2_d_d(1, 0));
  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(d2), vd2gety_vd_vd2(d2));
  r = vsel_vd_vo_vd_vd(visinf_vo_vd(d), vcast_vd_d(1.570796326794896557998982), r);
  return vmulsign_vd_vd_vd(r, d);
}

EXPORT CONST VECTOR_CC vdouble xatan(vdouble s) {
  vdouble t, u;
  vint q;
#if defined(__INTEL_COMPILER) && defined(ENABLE_PURECFMA_SCALAR)
  vdouble w = s;
#endif

  q = vsel_vi_vd_vi(s, vcast_vi_i(2));
  s = vabs_vd_vd(s);

  q = vsel_vi_vd_vd_vi_vi(vcast_vd_d(1), s, vadd_vi_vi_vi(q, vcast_vi_i(1)), q);
  s = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(vcast_vd_d(1), s), vrec_vd_vd(s), s);

  t = vmul_vd_vd_vd(s, s);

  vdouble t2 = vmul_vd_vd_vd(t, t), t4 = vmul_vd_vd_vd(t2, t2), t8 = vmul_vd_vd_vd(t4, t4), t16 = vmul_vd_vd_vd(t8, t8);
  u = POLY19(t, t2, t4, t8, t16,
             -1.88796008463073496563746e-05,
             0.000209850076645816976906797,
             -0.00110611831486672482563471,
             0.00370026744188713119232403,
             -0.00889896195887655491740809,
             0.016599329773529201970117,
             -0.0254517624932312641616861,
             0.0337852580001353069993897,
             -0.0407629191276836500001934,
             0.0466667150077840625632675,
             -0.0523674852303482457616113,
             0.0587666392926673580854313,
             -0.0666573579361080525984562,
             0.0769219538311769618355029,
             -0.090908995008245008229153,
             0.111111105648261418443745,
             -0.14285714266771329383765,
             0.199999999996591265594148,
             -0.333333333333311110369124);

  t = vmla_vd_vd_vd_vd(s, vmul_vd_vd_vd(t, u), s);

  t = vsel_vd_vo_vd_vd(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(q, vcast_vi_i(1)), vcast_vi_i(1))), vsub_vd_vd_vd(vcast_vd_d(M_PI/2), t), t);
  t = vreinterpret_vd_vm(vxor_vm_vm_vm(vand_vm_vo64_vm(vcast_vo64_vo32(veq_vo_vi_vi(vand_vi_vi_vi(q, vcast_vi_i(2)), vcast_vi_i(2))), vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(t)));

#if defined(__INTEL_COMPILER) && defined(ENABLE_PURECFMA_SCALAR)
  t = vsel_vd_vo_vd_vd(veq_vo_vd_vd(w, vcast_vd_d(0)), w, t);
#endif

  return t;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vdouble xlog(vdouble d) {
  vdouble x, x2;
  vdouble t, m;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(SLEEF_DBL_MIN));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d((double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32))), d);
  vint e = vilogb2k_vi_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  m = vldexp3_vd_vd_vi(d, vneg_vi_vi(e));
  e = vsel_vi_vo_vi_vi(vcast_vo32_vo64(o), vsub_vi_vi_vi(e, vcast_vi_i(64)), e);
#else
  vdouble e = vgetexp_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  e = vsel_vd_vo_vd_vd(vispinf_vo_vd(e), vcast_vd_d(1024.0), e);
  m = vgetmant_vd_vd(d);
#endif

  x = vdiv_vd_vd_vd(vsub_vd_vd_vd(m, vcast_vd_d(1)), vadd_vd_vd_vd(vcast_vd_d(1), m));
  x2 = vmul_vd_vd_vd(x, x);

  vdouble x4 = vmul_vd_vd_vd(x2, x2), x8 = vmul_vd_vd_vd(x4, x4), x3 = vmul_vd_vd_vd(x, x2);
  t = POLY7(x2, x4, x8,
            0.153487338491425068243146,
            0.152519917006351951593857,
            0.181863266251982985677316,
            0.222221366518767365905163,
            0.285714294746548025383248,
            0.399999999950799600689777,
            0.6666666666667778740063);

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  x = vmla_vd_vd_vd_vd(x, vcast_vd_d(2), vmul_vd_vd_vd(vcast_vd_d(0.693147180559945286226764), vcast_vd_vi(e)));
  x = vmla_vd_vd_vd_vd(x3, t, x);

  x = vsel_vd_vo_vd_vd(vispinf_vo_vd(d), vcast_vd_d(SLEEF_INFINITY), x);
  x = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vlt_vo_vd_vd(d, vcast_vd_d(0)), visnan_vo_vd(d)), vcast_vd_d(SLEEF_NAN), x);
  x = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(-SLEEF_INFINITY), x);
#else
  x = vmla_vd_vd_vd_vd(x, vcast_vd_d(2), vmul_vd_vd_vd(vcast_vd_d(0.693147180559945286226764), e));
  x = vmla_vd_vd_vd_vd(x3, t, x);

  x = vfixup_vd_vd_vd_vi2_i(x, d, vcast_vi2_i((5 << (5*4))), 0);
#endif

  return x;
}
#endif // #if !defined(DETERMINISTIC)

EXPORT CONST VECTOR_CC vdouble xexp(vdouble d) {
  vdouble u = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(R_LN2))), s;
  vint q = vrint_vi_vd(u);

  s = vmla_vd_vd_vd_vd(u, vcast_vd_d(-L2U), d);
  s = vmla_vd_vd_vd_vd(u, vcast_vd_d(-L2L), s);

#ifdef ENABLE_FMA_DP
  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2), s8 = vmul_vd_vd_vd(s4, s4);
  u = POLY10(s, s2, s4, s8,
             +0.2081276378237164457e-8,
             +0.2511210703042288022e-7,
             +0.2755762628169491192e-6,
             +0.2755723402025388239e-5,
             +0.2480158687479686264e-4,
             +0.1984126989855865850e-3,
             +0.1388888888914497797e-2,
             +0.8333333333314938210e-2,
             +0.4166666666666602598e-1,
             +0.1666666666666669072e+0);
  u = vfma_vd_vd_vd_vd(u, s, vcast_vd_d(+0.5000000000000000000e+0));
  u = vfma_vd_vd_vd_vd(u, s, vcast_vd_d(+0.1000000000000000000e+1));
  u = vfma_vd_vd_vd_vd(u, s, vcast_vd_d(+0.1000000000000000000e+1));
#else // #ifdef ENABLE_FMA_DP
  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2), s8 = vmul_vd_vd_vd(s4, s4);
  u = POLY10(s, s2, s4, s8,
             2.08860621107283687536341e-09,
             2.51112930892876518610661e-08,
             2.75573911234900471893338e-07,
             2.75572362911928827629423e-06,
             2.4801587159235472998791e-05,
             0.000198412698960509205564975,
             0.00138888888889774492207962,
             0.00833333333331652721664984,
             0.0416666666666665047591422,
             0.166666666666666851703837);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.5000000000000000000e+0));

  u = vadd_vd_vd_vd(vcast_vd_d(1), vmla_vd_vd_vd_vd(vmul_vd_vd_vd(s, s), u, s));
#endif // #ifdef ENABLE_FMA_DP

  u = vldexp2_vd_vd_vi(u, q);

  u = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(d, vcast_vd_d(709.78271114955742909217217426)), vcast_vd_d(SLEEF_INFINITY), u);
  u = vreinterpret_vd_vm(vandnot_vm_vo64_vm(vlt_vo_vd_vd(d, vcast_vd_d(-1000)), vreinterpret_vm_vd(u)));

  return u;
}

static INLINE CONST VECTOR_CC vdouble expm1k(vdouble d) {
  vdouble u = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(R_LN2))), s;
  vint q = vrint_vi_vd(u);

  s = vmla_vd_vd_vd_vd(u, vcast_vd_d(-L2U), d);
  s = vmla_vd_vd_vd_vd(u, vcast_vd_d(-L2L), s);

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2), s8 = vmul_vd_vd_vd(s4, s4);
  u = POLY10(s, s2, s4, s8,
             2.08860621107283687536341e-09,
             2.51112930892876518610661e-08,
             2.75573911234900471893338e-07,
             2.75572362911928827629423e-06,
             2.4801587159235472998791e-05,
             0.000198412698960509205564975,
             0.00138888888889774492207962,
             0.00833333333331652721664984,
             0.0416666666666665047591422,
             0.166666666666666851703837);

  u = vadd_vd_vd_vd(vmla_vd_vd_vd_vd(s2, vcast_vd_d(0.5), vmul_vd_vd_vd(vmul_vd_vd_vd(s2, s), u)), s);

  u = vsel_vd_vo_vd_vd(vcast_vo64_vo32(veq_vo_vi_vi(q, vcast_vi_i(0))), u,
                       vsub_vd_vd_vd(vldexp2_vd_vd_vi(vadd_vd_vd_vd(u, vcast_vd_d(1)), q), vcast_vd_d(1)));

  return u;
}

static INLINE CONST VECTOR_CC vdouble2 logk(vdouble d) {
  vdouble2 x, x2, s;
  vdouble t, m;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(SLEEF_DBL_MIN));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d((double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32))), d);
  vint e = vilogb2k_vi_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  m = vldexp3_vd_vd_vi(d, vneg_vi_vi(e));
  e = vsel_vi_vo_vi_vi(vcast_vo32_vo64(o), vsub_vi_vi_vi(e, vcast_vi_i(64)), e);
#else
  vdouble e = vgetexp_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  e = vsel_vd_vo_vd_vd(vispinf_vo_vd(e), vcast_vd_d(1024.0), e);
  m = vgetmant_vd_vd(d);
#endif

  x = dddiv_vd2_vd2_vd2(ddadd2_vd2_vd_vd(vcast_vd_d(-1), m), ddadd2_vd2_vd_vd(vcast_vd_d(1), m));
  x2 = ddsqu_vd2_vd2(x);

  vdouble x4 = vmul_vd_vd_vd(vd2getx_vd_vd2(x2), vd2getx_vd_vd2(x2)), x8 = vmul_vd_vd_vd(x4, x4), x16 = vmul_vd_vd_vd(x8, x8);
  t = POLY9(vd2getx_vd_vd2(x2), x4, x8, x16,
            0.116255524079935043668677,
            0.103239680901072952701192,
            0.117754809412463995466069,
            0.13332981086846273921509,
            0.153846227114512262845736,
            0.181818180850050775676507,
            0.222222222230083560345903,
            0.285714285714249172087875,
            0.400000000000000077715612);

  vdouble2 c = vcast_vd2_d_d(0.666666666666666629659233, 3.80554962542412056336616e-17);
#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  s = ddmul_vd2_vd2_vd(vcast_vd2_d_d(0.693147180559945286226764, 2.319046813846299558417771e-17), vcast_vd_vi(e));
#else
  s = ddmul_vd2_vd2_vd(vcast_vd2_d_d(0.693147180559945286226764, 2.319046813846299558417771e-17), e);
#endif
  s = ddadd_vd2_vd2_vd2(s, ddscale_vd2_vd2_vd(x, vcast_vd_d(2)));
  x = ddmul_vd2_vd2_vd2(x2, x);
  s = ddadd_vd2_vd2_vd2(s, ddmul_vd2_vd2_vd2(x, c));
  x = ddmul_vd2_vd2_vd2(x2, x);
  s = ddadd_vd2_vd2_vd2(s, ddmul_vd2_vd2_vd(x, t));

  return s;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vdouble xlog_u1(vdouble d) {
  vdouble2 x;
  vdouble t, m, x2;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(SLEEF_DBL_MIN));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d((double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32))), d);
  vint e = vilogb2k_vi_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  m = vldexp3_vd_vd_vi(d, vneg_vi_vi(e));
  e = vsel_vi_vo_vi_vi(vcast_vo32_vo64(o), vsub_vi_vi_vi(e, vcast_vi_i(64)), e);
#else
  vdouble e = vgetexp_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  e = vsel_vd_vo_vd_vd(vispinf_vo_vd(e), vcast_vd_d(1024.0), e);
  m = vgetmant_vd_vd(d);
#endif

  x = dddiv_vd2_vd2_vd2(ddadd2_vd2_vd_vd(vcast_vd_d(-1), m), ddadd2_vd2_vd_vd(vcast_vd_d(1), m));
  x2 = vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(x));

  vdouble x4 = vmul_vd_vd_vd(x2, x2), x8 = vmul_vd_vd_vd(x4, x4);
  t = POLY7(x2, x4, x8,
            0.1532076988502701353e+0,
            0.1525629051003428716e+0,
            0.1818605932937785996e+0,
            0.2222214519839380009e+0,
            0.2857142932794299317e+0,
            0.3999999999635251990e+0,
            0.6666666666667333541e+0);

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vdouble2 s = ddmul_vd2_vd2_vd(vcast_vd2_d_d(0.693147180559945286226764, 2.319046813846299558417771e-17), vcast_vd_vi(e));
#else
  vdouble2 s = ddmul_vd2_vd2_vd(vcast_vd2_d_d(0.693147180559945286226764, 2.319046813846299558417771e-17), e);
#endif

  s = ddadd_vd2_vd2_vd2(s, ddscale_vd2_vd2_vd(x, vcast_vd_d(2)));
  s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(vmul_vd_vd_vd(x2, vd2getx_vd_vd2(x)), t));

  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(s), vd2gety_vd_vd2(s));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  r = vsel_vd_vo_vd_vd(vispinf_vo_vd(d), vcast_vd_d(SLEEF_INFINITY), r);
  r = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vlt_vo_vd_vd(d, vcast_vd_d(0)), visnan_vo_vd(d)), vcast_vd_d(SLEEF_NAN), r);
  r = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(-SLEEF_INFINITY), r);
#else
  r = vfixup_vd_vd_vd_vi2_i(r, d, vcast_vi2_i((4 << (2*4)) | (3 << (4*4)) | (5 << (5*4)) | (2 << (6*4))), 0);
#endif

  return r;
}
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vdouble expk(vdouble2 d) {
  vdouble u = vmul_vd_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d)), vcast_vd_d(R_LN2));
  vdouble dq = vrint_vd_vd(u);
  vint q = vrint_vi_vd(dq);
  vdouble2 s, t;

  s = ddadd2_vd2_vd2_vd(d, vmul_vd_vd_vd(dq, vcast_vd_d(-L2U)));
  s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dq, vcast_vd_d(-L2L)));

  s = ddnormalize_vd2_vd2(s);

  vdouble s2 = vmul_vd_vd_vd(vd2getx_vd_vd2(s), vd2getx_vd_vd2(s)), s4 = vmul_vd_vd_vd(s2, s2), s8 = vmul_vd_vd_vd(s4, s4);
  u = POLY10(vd2getx_vd_vd2(s), s2, s4, s8,
             2.51069683420950419527139e-08,
             2.76286166770270649116855e-07,
             2.75572496725023574143864e-06,
             2.48014973989819794114153e-05,
             0.000198412698809069797676111,
             0.0013888888939977128960529,
             0.00833333333332371417601081,
             0.0416666666665409524128449,
             0.166666666666666740681535,
             0.500000000000000999200722);

  t = ddadd_vd2_vd_vd2(vcast_vd_d(1), s);
  t = ddadd_vd2_vd2_vd2(t, ddmul_vd2_vd2_vd(ddsqu_vd2_vd2(s), u));

  u = vadd_vd_vd_vd(vd2getx_vd_vd2(t), vd2gety_vd_vd2(t));
  u = vldexp2_vd_vd_vi(u, q);

  u = vreinterpret_vd_vm(vandnot_vm_vo64_vm(vlt_vo_vd_vd(vd2getx_vd_vd2(d), vcast_vd_d(-1000)), vreinterpret_vm_vd(u)));

  return u;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vdouble xpow(vdouble x, vdouble y) {
#if 1
  vopmask yisint = visint_vo_vd(y);
  vopmask yisodd = vand_vo_vo_vo(visodd_vo_vd(y), yisint);

  vdouble2 d = ddmul_vd2_vd2_vd(logk(vabs_vd_vd(x)), y);
  vdouble result = expk(d);
  result = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vd2getx_vd_vd2(d), vcast_vd_d(709.78271114955742909217217426)), vcast_vd_d(SLEEF_INFINITY), result);

  result = vmul_vd_vd_vd(result,
                         vsel_vd_vo_vd_vd(vgt_vo_vd_vd(x, vcast_vd_d(0)),
                                          vcast_vd_d(1),
                                          vsel_vd_vo_vd_vd(yisint, vsel_vd_vo_vd_vd(yisodd, vcast_vd_d(-1.0), vcast_vd_d(1)), vcast_vd_d(SLEEF_NAN))));

  vdouble efx = vmulsign_vd_vd_vd(vsub_vd_vd_vd(vabs_vd_vd(x), vcast_vd_d(1)), y);

  result = vsel_vd_vo_vd_vd(visinf_vo_vd(y),
                            vreinterpret_vd_vm(vandnot_vm_vo64_vm(vlt_vo_vd_vd(efx, vcast_vd_d(0.0)),
                                                                  vreinterpret_vm_vd(vsel_vd_vo_vd_vd(veq_vo_vd_vd(efx, vcast_vd_d(0.0)),
                                                                                                      vcast_vd_d(1.0),
                                                                                                      vcast_vd_d(SLEEF_INFINITY))))),
                            result);

  result = vsel_vd_vo_vd_vd(vor_vo_vo_vo(visinf_vo_vd(x), veq_vo_vd_vd(x, vcast_vd_d(0.0))),
                            vmulsign_vd_vd_vd(vsel_vd_vo_vd_vd(vxor_vo_vo_vo(vsignbit_vo_vd(y), veq_vo_vd_vd(x, vcast_vd_d(0.0))),
                                                               vcast_vd_d(0), vcast_vd_d(SLEEF_INFINITY)),
                                              vsel_vd_vo_vd_vd(yisodd, x, vcast_vd_d(1))), result);

  result = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visnan_vo_vd(x), visnan_vo_vd(y)), vreinterpret_vm_vd(result)));

  result = vsel_vd_vo_vd_vd(vor_vo_vo_vo(veq_vo_vd_vd(y, vcast_vd_d(0)), veq_vo_vd_vd(x, vcast_vd_d(1))), vcast_vd_d(1), result);

  return result;
#else
  return expk(ddmul_vd2_vd2_vd(logk(x), y));
#endif
}
#endif // #if !defined(DETERMINISTIC)

static INLINE CONST VECTOR_CC vdouble2 expk2(vdouble2 d) {
  vdouble u = vmul_vd_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d)), vcast_vd_d(R_LN2));
  vdouble dq = vrint_vd_vd(u);
  vint q = vrint_vi_vd(dq);
  vdouble2 s, t;

  s = ddadd2_vd2_vd2_vd(d, vmul_vd_vd_vd(dq, vcast_vd_d(-L2U)));
  s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(dq, vcast_vd_d(-L2L)));

  vdouble2 s2 = ddsqu_vd2_vd2(s), s4 = ddsqu_vd2_vd2(s2);
  vdouble s8 = vmul_vd_vd_vd(vd2getx_vd_vd2(s4), vd2getx_vd_vd2(s4));
  u = POLY10(vd2getx_vd_vd2(s), vd2getx_vd_vd2(s2), vd2getx_vd_vd2(s4), s8,
             +0.1602472219709932072e-9,
             +0.2092255183563157007e-8,
             +0.2505230023782644465e-7,
             +0.2755724800902135303e-6,
             +0.2755731892386044373e-5,
             +0.2480158735605815065e-4,
             +0.1984126984148071858e-3,
             +0.1388888888886763255e-2,
             +0.8333333333333347095e-2,
             +0.4166666666666669905e-1);

  t = ddadd_vd2_vd_vd2(vcast_vd_d(0.5), ddmul_vd2_vd2_vd(s, vcast_vd_d(+0.1666666666666666574e+0)));
  t = ddadd_vd2_vd_vd2(vcast_vd_d(1.0), ddmul_vd2_vd2_vd2(t, s));
  t = ddadd_vd2_vd_vd2(vcast_vd_d(1.0), ddmul_vd2_vd2_vd2(t, s));
  t = ddadd_vd2_vd2_vd2(t, ddmul_vd2_vd2_vd(s4, u));

  t = vd2setx_vd2_vd2_vd(t, vldexp2_vd_vd_vi(vd2getx_vd_vd2(t), q));
  t = vd2sety_vd2_vd2_vd(t, vldexp2_vd_vd_vi(vd2gety_vd_vd2(t), q));

  t = vd2setx_vd2_vd2_vd(t, vreinterpret_vd_vm(vandnot_vm_vo64_vm(vlt_vo_vd_vd(vd2getx_vd_vd2(d), vcast_vd_d(-1000)), vreinterpret_vm_vd(vd2getx_vd_vd2(t)))));
  t = vd2sety_vd2_vd2_vd(t, vreinterpret_vd_vm(vandnot_vm_vo64_vm(vlt_vo_vd_vd(vd2getx_vd_vd2(d), vcast_vd_d(-1000)), vreinterpret_vm_vd(vd2gety_vd_vd2(t)))));

  return t;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vdouble xsinh(vdouble x) {
  vdouble y = vabs_vd_vd(x);
  vdouble2 d = expk2(vcast_vd2_vd_vd(y, vcast_vd_d(0)));
  d = ddsub_vd2_vd2_vd2(d, ddrec_vd2_vd2(d));
  y = vmul_vd_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d)), vcast_vd_d(0.5));

  y = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(710)), visnan_vo_vd(y)), vcast_vd_d(SLEEF_INFINITY), y);
  y = vmulsign_vd_vd_vd(y, x);
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(visnan_vo_vd(x), vreinterpret_vm_vd(y)));

  return y;
}

EXPORT CONST VECTOR_CC vdouble xcosh(vdouble x) {
  vdouble y = vabs_vd_vd(x);
  vdouble2 d = expk2(vcast_vd2_vd_vd(y, vcast_vd_d(0)));
  d = ddadd_vd2_vd2_vd2(d, ddrec_vd2_vd2(d));
  y = vmul_vd_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d)), vcast_vd_d(0.5));

  y = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(710)), visnan_vo_vd(y)), vcast_vd_d(SLEEF_INFINITY), y);
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(visnan_vo_vd(x), vreinterpret_vm_vd(y)));

  return y;
}

EXPORT CONST VECTOR_CC vdouble xtanh(vdouble x) {
  vdouble y = vabs_vd_vd(x);
  vdouble2 d = expk2(vcast_vd2_vd_vd(y, vcast_vd_d(0)));
  vdouble2 e = ddrec_vd2_vd2(d);
  d = dddiv_vd2_vd2_vd2(ddadd2_vd2_vd2_vd2(d, ddneg_vd2_vd2(e)), ddadd2_vd2_vd2_vd2(d, e));
  y = vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d));

  y = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(18.714973875)), visnan_vo_vd(y)), vcast_vd_d(1.0), y);
  y = vmulsign_vd_vd_vd(y, x);
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(visnan_vo_vd(x), vreinterpret_vm_vd(y)));

  return y;
}

EXPORT CONST VECTOR_CC vdouble xsinh_u35(vdouble x) {
  vdouble e = expm1k(vabs_vd_vd(x));

  vdouble y = vdiv_vd_vd_vd(vadd_vd_vd_vd(e, vcast_vd_d(2)), vadd_vd_vd_vd(e, vcast_vd_d(1)));
  y = vmul_vd_vd_vd(y, vmul_vd_vd_vd(vcast_vd_d(0.5), e));

  y = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(709)), visnan_vo_vd(y)), vcast_vd_d(SLEEF_INFINITY), y);
  y = vmulsign_vd_vd_vd(y, x);
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(visnan_vo_vd(x), vreinterpret_vm_vd(y)));

  return y;
}

EXPORT CONST VECTOR_CC vdouble xcosh_u35(vdouble x) {
  vdouble e = xexp(vabs_vd_vd(x));
  vdouble y = vmla_vd_vd_vd_vd(vcast_vd_d(0.5), e, vdiv_vd_vd_vd(vcast_vd_d(0.5), e));

  y = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(709)), visnan_vo_vd(y)), vcast_vd_d(SLEEF_INFINITY), y);
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(visnan_vo_vd(x), vreinterpret_vm_vd(y)));

  return y;
}

EXPORT CONST VECTOR_CC vdouble xtanh_u35(vdouble x) {
  vdouble d = expm1k(vmul_vd_vd_vd(vcast_vd_d(2), vabs_vd_vd(x)));
  vdouble y = vdiv_vd_vd_vd(d, vadd_vd_vd_vd(vcast_vd_d(2), d));

  y = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(18.714973875)), visnan_vo_vd(y)), vcast_vd_d(1.0), y);
  y = vmulsign_vd_vd_vd(y, x);
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(visnan_vo_vd(x), vreinterpret_vm_vd(y)));

  return y;
}

static INLINE CONST VECTOR_CC vdouble2 logk2(vdouble2 d) {
  vdouble2 x, x2, m, s;
  vdouble t;
  vint e;

  e = vilogbk_vi_vd(vmul_vd_vd_vd(vd2getx_vd_vd2(d), vcast_vd_d(1.0/0.75)));

  m = vd2setxy_vd2_vd_vd(vldexp2_vd_vd_vi(vd2getx_vd_vd2(d), vneg_vi_vi(e)),
                         vldexp2_vd_vd_vi(vd2gety_vd_vd2(d), vneg_vi_vi(e)));

  x = dddiv_vd2_vd2_vd2(ddadd2_vd2_vd2_vd(m, vcast_vd_d(-1)), ddadd2_vd2_vd2_vd(m, vcast_vd_d(1)));
  x2 = ddsqu_vd2_vd2(x);

  vdouble x4 = vmul_vd_vd_vd(vd2getx_vd_vd2(x2), vd2getx_vd_vd2(x2)), x8 = vmul_vd_vd_vd(x4, x4);
  t = POLY7(vd2getx_vd_vd2(x2), x4, x8,
            0.13860436390467167910856,
            0.131699838841615374240845,
            0.153914168346271945653214,
            0.181816523941564611721589,
            0.22222224632662035403996,
            0.285714285511134091777308,
            0.400000000000914013309483);
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(x2), vcast_vd_d(0.666666666666664853302393));

  s = ddmul_vd2_vd2_vd(vcast_vd2_d_d(0.693147180559945286226764, 2.319046813846299558417771e-17), vcast_vd_vi(e));
  s = ddadd_vd2_vd2_vd2(s, ddscale_vd2_vd2_vd(x, vcast_vd_d(2)));
  s = ddadd_vd2_vd2_vd2(s, ddmul_vd2_vd2_vd(ddmul_vd2_vd2_vd2(x2, x), t));

  return  s;
}

EXPORT CONST VECTOR_CC vdouble xasinh(vdouble x) {
  vdouble y = vabs_vd_vd(x);
  vopmask o = vgt_vo_vd_vd(y, vcast_vd_d(1));
  vdouble2 d;

  d = vsel_vd2_vo_vd2_vd2(o, ddrec_vd2_vd(x), vcast_vd2_vd_vd(y, vcast_vd_d(0)));
  d = ddsqrt_vd2_vd2(ddadd2_vd2_vd2_vd(ddsqu_vd2_vd2(d), vcast_vd_d(1)));
  d = vsel_vd2_vo_vd2_vd2(o, ddmul_vd2_vd2_vd(d, y), d);

  d = logk2(ddnormalize_vd2_vd2(ddadd2_vd2_vd2_vd(d, x)));
  y = vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d));

  y = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(SQRT_DBL_MAX)),
                                    visnan_vo_vd(y)),
                       vmulsign_vd_vd_vd(vcast_vd_d(SLEEF_INFINITY), x), y);

  y = vreinterpret_vd_vm(vor_vm_vo64_vm(visnan_vo_vd(x), vreinterpret_vm_vd(y)));
  y = vsel_vd_vo_vd_vd(visnegzero_vo_vd(x), vcast_vd_d(-0.0), y);

  return y;
}

EXPORT CONST VECTOR_CC vdouble xacosh(vdouble x) {
  vdouble2 d = logk2(ddadd2_vd2_vd2_vd(ddmul_vd2_vd2_vd2(ddsqrt_vd2_vd2(ddadd2_vd2_vd_vd(x, vcast_vd_d(1))), ddsqrt_vd2_vd2(ddadd2_vd2_vd_vd(x, vcast_vd_d(-1)))), x));
  vdouble y = vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d));

  y = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(SQRT_DBL_MAX)),
                                    visnan_vo_vd(y)),
                       vcast_vd_d(SLEEF_INFINITY), y);
  y = vreinterpret_vd_vm(vandnot_vm_vo64_vm(veq_vo_vd_vd(x, vcast_vd_d(1.0)), vreinterpret_vm_vd(y)));

  y = vreinterpret_vd_vm(vor_vm_vo64_vm(vlt_vo_vd_vd(x, vcast_vd_d(1.0)), vreinterpret_vm_vd(y)));
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(visnan_vo_vd(x), vreinterpret_vm_vd(y)));

  return y;
}

EXPORT CONST VECTOR_CC vdouble xatanh(vdouble x) {
  vdouble y = vabs_vd_vd(x);
  vdouble2 d = logk2(dddiv_vd2_vd2_vd2(ddadd2_vd2_vd_vd(vcast_vd_d(1), y), ddadd2_vd2_vd_vd(vcast_vd_d(1), vneg_vd_vd(y))));
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(vgt_vo_vd_vd(y, vcast_vd_d(1.0)), vreinterpret_vm_vd(vsel_vd_vo_vd_vd(veq_vo_vd_vd(y, vcast_vd_d(1.0)), vcast_vd_d(SLEEF_INFINITY), vmul_vd_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d)), vcast_vd_d(0.5))))));

  y = vmulsign_vd_vd_vd(y, x);
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(vor_vo_vo_vo(visinf_vo_vd(x), visnan_vo_vd(y)), vreinterpret_vm_vd(y)));
  y = vreinterpret_vd_vm(vor_vm_vo64_vm(visnan_vo_vd(x), vreinterpret_vm_vd(y)));

  return y;
}

EXPORT CONST VECTOR_CC vdouble xcbrt(vdouble d) {
  vdouble x, y, q = vcast_vd_d(1.0);
  vint e, qu, re;
  vdouble t;

#if defined(ENABLE_AVX512F) || defined(ENABLE_AVX512FNOFMA)
  vdouble s = d;
#endif
  e = vadd_vi_vi_vi(vilogbk_vi_vd(vabs_vd_vd(d)), vcast_vi_i(1));
  d = vldexp2_vd_vd_vi(d, vneg_vi_vi(e));

  t = vadd_vd_vd_vd(vcast_vd_vi(e), vcast_vd_d(6144));
  qu = vtruncate_vi_vd(vmul_vd_vd_vd(t, vcast_vd_d(1.0/3.0)));
  re = vtruncate_vi_vd(vsub_vd_vd_vd(t, vmul_vd_vd_vd(vcast_vd_vi(qu), vcast_vd_d(3))));

  q = vsel_vd_vo_vd_vd(vcast_vo64_vo32(veq_vo_vi_vi(re, vcast_vi_i(1))), vcast_vd_d(1.2599210498948731647672106), q);
  q = vsel_vd_vo_vd_vd(vcast_vo64_vo32(veq_vo_vi_vi(re, vcast_vi_i(2))), vcast_vd_d(1.5874010519681994747517056), q);
  q = vldexp2_vd_vd_vi(q, vsub_vi_vi_vi(qu, vcast_vi_i(2048)));

  q = vmulsign_vd_vd_vd(q, d);

  d = vabs_vd_vd(d);

  x = vcast_vd_d(-0.640245898480692909870982);
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(2.96155103020039511818595));
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(-5.73353060922947843636166));
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(6.03990368989458747961407));
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(-3.85841935510444988821632));
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(2.2307275302496609725722));

  y = vmul_vd_vd_vd(x, x); y = vmul_vd_vd_vd(y, y); x = vsub_vd_vd_vd(x, vmul_vd_vd_vd(vmlapn_vd_vd_vd_vd(d, y, x), vcast_vd_d(1.0 / 3.0)));
  y = vmul_vd_vd_vd(vmul_vd_vd_vd(d, x), x);
  y = vmul_vd_vd_vd(vsub_vd_vd_vd(y, vmul_vd_vd_vd(vmul_vd_vd_vd(vcast_vd_d(2.0 / 3.0), y), vmla_vd_vd_vd_vd(y, x, vcast_vd_d(-1.0)))), q);

#if defined(ENABLE_AVX512F) || defined(ENABLE_AVX512FNOFMA)
  y = vsel_vd_vo_vd_vd(visinf_vo_vd(s), vmulsign_vd_vd_vd(vcast_vd_d(SLEEF_INFINITY), s), y);
  y = vsel_vd_vo_vd_vd(veq_vo_vd_vd(s, vcast_vd_d(0)), vmulsign_vd_vd_vd(vcast_vd_d(0), s), y);
#endif

  return y;
}

EXPORT CONST VECTOR_CC vdouble xcbrt_u1(vdouble d) {
  vdouble x, y, z, t;
  vdouble2 q2 = vcast_vd2_d_d(1, 0), u, v;
  vint e, qu, re;

#if defined(ENABLE_AVX512F) || defined(ENABLE_AVX512FNOFMA)
  vdouble s = d;
#endif
  e = vadd_vi_vi_vi(vilogbk_vi_vd(vabs_vd_vd(d)), vcast_vi_i(1));
  d = vldexp2_vd_vd_vi(d, vneg_vi_vi(e));

  t = vadd_vd_vd_vd(vcast_vd_vi(e), vcast_vd_d(6144));
  qu = vtruncate_vi_vd(vmul_vd_vd_vd(t, vcast_vd_d(1.0/3.0)));
  re = vtruncate_vi_vd(vsub_vd_vd_vd(t, vmul_vd_vd_vd(vcast_vd_vi(qu), vcast_vd_d(3))));

  q2 = vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(veq_vo_vi_vi(re, vcast_vi_i(1))), vcast_vd2_d_d(1.2599210498948731907, -2.5899333753005069177e-17), q2);
  q2 = vsel_vd2_vo_vd2_vd2(vcast_vo64_vo32(veq_vo_vi_vi(re, vcast_vi_i(2))), vcast_vd2_d_d(1.5874010519681995834, -1.0869008194197822986e-16), q2);

  q2 = vd2setxy_vd2_vd_vd(vmulsign_vd_vd_vd(vd2getx_vd_vd2(q2), d), vmulsign_vd_vd_vd(vd2gety_vd_vd2(q2), d));
  d = vabs_vd_vd(d);

  x = vcast_vd_d(-0.640245898480692909870982);
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(2.96155103020039511818595));
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(-5.73353060922947843636166));
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(6.03990368989458747961407));
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(-3.85841935510444988821632));
  x = vmla_vd_vd_vd_vd(x, d, vcast_vd_d(2.2307275302496609725722));

  y = vmul_vd_vd_vd(x, x); y = vmul_vd_vd_vd(y, y); x = vsub_vd_vd_vd(x, vmul_vd_vd_vd(vmlapn_vd_vd_vd_vd(d, y, x), vcast_vd_d(1.0 / 3.0)));

  z = x;

  u = ddmul_vd2_vd_vd(x, x);
  u = ddmul_vd2_vd2_vd2(u, u);
  u = ddmul_vd2_vd2_vd(u, d);
  u = ddadd2_vd2_vd2_vd(u, vneg_vd_vd(x));
  y = vadd_vd_vd_vd(vd2getx_vd_vd2(u), vd2gety_vd_vd2(u));

  y = vmul_vd_vd_vd(vmul_vd_vd_vd(vcast_vd_d(-2.0 / 3.0), y), z);
  v = ddadd2_vd2_vd2_vd(ddmul_vd2_vd_vd(z, z), y);
  v = ddmul_vd2_vd2_vd(v, d);
  v = ddmul_vd2_vd2_vd2(v, q2);
  z = vldexp2_vd_vd_vi(vadd_vd_vd_vd(vd2getx_vd_vd2(v), vd2gety_vd_vd2(v)), vsub_vi_vi_vi(qu, vcast_vi_i(2048)));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  z = vsel_vd_vo_vd_vd(visinf_vo_vd(d), vmulsign_vd_vd_vd(vcast_vd_d(SLEEF_INFINITY), vd2getx_vd_vd2(q2)), z);
  z = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), vreinterpret_vd_vm(vsignbit_vm_vd(vd2getx_vd_vd2(q2))), z);
#else
  z = vsel_vd_vo_vd_vd(visinf_vo_vd(s), vmulsign_vd_vd_vd(vcast_vd_d(SLEEF_INFINITY), s), z);
  z = vsel_vd_vo_vd_vd(veq_vo_vd_vd(s, vcast_vd_d(0)), vmulsign_vd_vd_vd(vcast_vd_d(0), s), z);
#endif

  return z;
}
#endif // #if !defined(DETERMINISTIC)

EXPORT CONST VECTOR_CC vdouble xexp2(vdouble d) {
  vdouble u = vrint_vd_vd(d), s;
  vint q = vrint_vi_vd(u);

  s = vsub_vd_vd_vd(d, u);

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2), s8 = vmul_vd_vd_vd(s4, s4);
  u = POLY10(s, s2, s4, s8,
             +0.4434359082926529454e-9,
             +0.7073164598085707425e-8,
             +0.1017819260921760451e-6,
             +0.1321543872511327615e-5,
             +0.1525273353517584730e-4,
             +0.1540353045101147808e-3,
             +0.1333355814670499073e-2,
             +0.9618129107597600536e-2,
             +0.5550410866482046596e-1,
             +0.2402265069591012214e+0);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.6931471805599452862e+0));

#ifdef ENABLE_FMA_DP
  u = vfma_vd_vd_vd_vd(u, s, vcast_vd_d(1));
#else
  u = vd2getx_vd_vd2(ddnormalize_vd2_vd2(ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd_vd(u, s))));
#endif

  u = vldexp2_vd_vd_vi(u, q);

  u = vsel_vd_vo_vd_vd(vge_vo_vd_vd(d, vcast_vd_d(1024)), vcast_vd_d(SLEEF_INFINITY), u);
  u = vreinterpret_vd_vm(vandnot_vm_vo64_vm(vlt_vo_vd_vd(d, vcast_vd_d(-2000)), vreinterpret_vm_vd(u)));

  return u;
}

EXPORT CONST VECTOR_CC vdouble xexp2_u35(vdouble d) {
  vdouble u = vrint_vd_vd(d), s;
  vint q = vrint_vi_vd(u);

  s = vsub_vd_vd_vd(d, u);

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2), s8 = vmul_vd_vd_vd(s4, s4);
  u = POLY10(s, s2, s4, s8,
             +0.4434359082926529454e-9,
             +0.7073164598085707425e-8,
             +0.1017819260921760451e-6,
             +0.1321543872511327615e-5,
             +0.1525273353517584730e-4,
             +0.1540353045101147808e-3,
             +0.1333355814670499073e-2,
             +0.9618129107597600536e-2,
             +0.5550410866482046596e-1,
             +0.2402265069591012214e+0);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.6931471805599452862e+0));

  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(1));

  u = vldexp2_vd_vd_vi(u, q);

  u = vsel_vd_vo_vd_vd(vge_vo_vd_vd(d, vcast_vd_d(1024)), vcast_vd_d(SLEEF_INFINITY), u);
  u = vreinterpret_vd_vm(vandnot_vm_vo64_vm(vlt_vo_vd_vd(d, vcast_vd_d(-2000)), vreinterpret_vm_vd(u)));

  return u;
}

EXPORT CONST VECTOR_CC vdouble xexp10(vdouble d) {
  vdouble u = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(LOG10_2))), s;
  vint q = vrint_vi_vd(u);

  s = vmla_vd_vd_vd_vd(u, vcast_vd_d(-L10U), d);
  s = vmla_vd_vd_vd_vd(u, vcast_vd_d(-L10L), s);

  u = vcast_vd_d(+0.2411463498334267652e-3);
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.1157488415217187375e-2));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.5013975546789733659e-2));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.1959762320720533080e-1));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.6808936399446784138e-1));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.2069958494722676234e+0));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.5393829292058536229e+0));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.1171255148908541655e+1));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.2034678592293432953e+1));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.2650949055239205876e+1));
  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(+0.2302585092994045901e+1));

#ifdef ENABLE_FMA_DP
  u = vfma_vd_vd_vd_vd(u, s, vcast_vd_d(1));
#else
  u = vd2getx_vd_vd2(ddnormalize_vd2_vd2(ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd_vd(u, s))));
#endif

  u = vldexp2_vd_vd_vi(u, q);

  u = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(d, vcast_vd_d(308.25471555991671)), vcast_vd_d(SLEEF_INFINITY), u);
  u = vreinterpret_vd_vm(vandnot_vm_vo64_vm(vlt_vo_vd_vd(d, vcast_vd_d(-350)), vreinterpret_vm_vd(u)));

  return u;
}

EXPORT CONST VECTOR_CC vdouble xexp10_u35(vdouble d) {
  vdouble u = vrint_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(LOG10_2))), s;
  vint q = vrint_vi_vd(u);

  s = vmla_vd_vd_vd_vd(u, vcast_vd_d(-L10U), d);
  s = vmla_vd_vd_vd_vd(u, vcast_vd_d(-L10L), s);

  vdouble s2 = vmul_vd_vd_vd(s, s), s4 = vmul_vd_vd_vd(s2, s2), s8 = vmul_vd_vd_vd(s4, s4);
  u = POLY11(s, s2, s4, s8,
             +0.2411463498334267652e-3,
             +0.1157488415217187375e-2,
             +0.5013975546789733659e-2,
             +0.1959762320720533080e-1,
             +0.6808936399446784138e-1,
             +0.2069958494722676234e+0,
             +0.5393829292058536229e+0,
             +0.1171255148908541655e+1,
             +0.2034678592293432953e+1,
             +0.2650949055239205876e+1,
             +0.2302585092994045901e+1);

  u = vmla_vd_vd_vd_vd(u, s, vcast_vd_d(1));

  u = vldexp2_vd_vd_vi(u, q);

  u = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(d, vcast_vd_d(308.25471555991671)), vcast_vd_d(SLEEF_INFINITY), u);
  u = vreinterpret_vd_vm(vandnot_vm_vo64_vm(vlt_vo_vd_vd(d, vcast_vd_d(-350)), vreinterpret_vm_vd(u)));

  return u;
}

#if !defined(DETERMINISTIC)
EXPORT CONST VECTOR_CC vdouble xexpm1(vdouble a) {
  vdouble2 d = ddadd2_vd2_vd2_vd(expk2(vcast_vd2_vd_vd(a, vcast_vd_d(0))), vcast_vd_d(-1.0));
  vdouble x = vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d));
  x = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(a, vcast_vd_d(709.782712893383996732223)), vcast_vd_d(SLEEF_INFINITY), x);
  x = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(a, vcast_vd_d(-36.736800569677101399113302437)), vcast_vd_d(-1), x);
  x = vsel_vd_vo_vd_vd(visnegzero_vo_vd(a), vcast_vd_d(-0.0), x);
  return x;
}

EXPORT CONST VECTOR_CC vdouble xlog10(vdouble d) {
  vdouble2 x;
  vdouble t, m, x2;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(SLEEF_DBL_MIN));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d((double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32))), d);
  vint e = vilogb2k_vi_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  m = vldexp3_vd_vd_vi(d, vneg_vi_vi(e));
  e = vsel_vi_vo_vi_vi(vcast_vo32_vo64(o), vsub_vi_vi_vi(e, vcast_vi_i(64)), e);
#else
  vdouble e = vgetexp_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  e = vsel_vd_vo_vd_vd(vispinf_vo_vd(e), vcast_vd_d(1024.0), e);
  m = vgetmant_vd_vd(d);
#endif

  x = dddiv_vd2_vd2_vd2(ddadd2_vd2_vd_vd(vcast_vd_d(-1), m), ddadd2_vd2_vd_vd(vcast_vd_d(1), m));
  x2 = vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(x));

  vdouble x4 = vmul_vd_vd_vd(x2, x2), x8 = vmul_vd_vd_vd(x4, x4);
  t = POLY7(x2, x4, x8,
            +0.6653725819576758460e-1,
            +0.6625722782820833712e-1,
            +0.7898105214313944078e-1,
            +0.9650955035715275132e-1,
            +0.1240841409721444993e+0,
            +0.1737177927454605086e+0,
            +0.2895296546021972617e+0);

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vdouble2 s = ddmul_vd2_vd2_vd(vcast_vd2_d_d(0.30102999566398119802, -2.803728127785170339e-18), vcast_vd_vi(e));
#else
  vdouble2 s = ddmul_vd2_vd2_vd(vcast_vd2_d_d(0.30102999566398119802, -2.803728127785170339e-18), e);
#endif

  s = ddadd_vd2_vd2_vd2(s, ddmul_vd2_vd2_vd2(x, vcast_vd2_d_d(0.86858896380650363334, 1.1430059694096389311e-17)));
  s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(vmul_vd_vd_vd(x2, vd2getx_vd_vd2(x)), t));

  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(s), vd2gety_vd_vd2(s));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  r = vsel_vd_vo_vd_vd(vispinf_vo_vd(d), vcast_vd_d(SLEEF_INFINITY), r);
  r = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vlt_vo_vd_vd(d, vcast_vd_d(0)), visnan_vo_vd(d)), vcast_vd_d(SLEEF_NAN), r);
  r = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(-SLEEF_INFINITY), r);
#else
  r = vfixup_vd_vd_vd_vi2_i(r, d, vcast_vi2_i((4 << (2*4)) | (3 << (4*4)) | (5 << (5*4)) | (2 << (6*4))), 0);
#endif

  return r;
}

EXPORT CONST VECTOR_CC vdouble xlog2(vdouble d) {
  vdouble2 x;
  vdouble t, m, x2;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(SLEEF_DBL_MIN));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d((double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32))), d);
  vint e = vilogb2k_vi_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  m = vldexp3_vd_vd_vi(d, vneg_vi_vi(e));
  e = vsel_vi_vo_vi_vi(vcast_vo32_vo64(o), vsub_vi_vi_vi(e, vcast_vi_i(64)), e);
#else
  vdouble e = vgetexp_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  e = vsel_vd_vo_vd_vd(vispinf_vo_vd(e), vcast_vd_d(1024.0), e);
  m = vgetmant_vd_vd(d);
#endif

  x = dddiv_vd2_vd2_vd2(ddadd2_vd2_vd_vd(vcast_vd_d(-1), m), ddadd2_vd2_vd_vd(vcast_vd_d(1), m));
  x2 = vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(x));

  vdouble x4 = vmul_vd_vd_vd(x2, x2), x8 = vmul_vd_vd_vd(x4, x4);
  t = POLY7(x2, x4, x8,
            +0.2211941750456081490e+0,
            +0.2200768693152277689e+0,
            +0.2623708057488514656e+0,
            +0.3205977477944495502e+0,
            +0.4121985945485324709e+0,
            +0.5770780162997058982e+0,
            +0.96179669392608091449);

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vdouble2 s = ddadd2_vd2_vd_vd2(vcast_vd_vi(e),
                                 ddmul_vd2_vd2_vd2(x, vcast_vd2_d_d(2.885390081777926774, 6.0561604995516736434e-18)));
#else
  vdouble2 s = ddadd2_vd2_vd_vd2(e,
                                 ddmul_vd2_vd2_vd2(x, vcast_vd2_d_d(2.885390081777926774, 6.0561604995516736434e-18)));
#endif

  s = ddadd2_vd2_vd2_vd(s, vmul_vd_vd_vd(vmul_vd_vd_vd(x2, vd2getx_vd_vd2(x)), t));

  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(s), vd2gety_vd_vd2(s));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  r = vsel_vd_vo_vd_vd(vispinf_vo_vd(d), vcast_vd_d(SLEEF_INFINITY), r);
  r = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vlt_vo_vd_vd(d, vcast_vd_d(0)), visnan_vo_vd(d)), vcast_vd_d(SLEEF_NAN), r);
  r = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(-SLEEF_INFINITY), r);
#else
  r = vfixup_vd_vd_vd_vi2_i(r, d, vcast_vi2_i((4 << (2*4)) | (3 << (4*4)) | (5 << (5*4)) | (2 << (6*4))), 0);
#endif

  return r;
}

EXPORT CONST VECTOR_CC vdouble xlog2_u35(vdouble d) {
  vdouble m, t, x, x2;

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(SLEEF_DBL_MIN));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d((double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32))), d);
  vint e = vilogb2k_vi_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  m = vldexp3_vd_vd_vi(d, vneg_vi_vi(e));
  e = vsel_vi_vo_vi_vi(vcast_vo32_vo64(o), vsub_vi_vi_vi(e, vcast_vi_i(64)), e);
#else
  vdouble e = vgetexp_vd_vd(vmul_vd_vd_vd(d, vcast_vd_d(1.0/0.75)));
  e = vsel_vd_vo_vd_vd(vispinf_vo_vd(e), vcast_vd_d(1024.0), e);
  m = vgetmant_vd_vd(d);
#endif

  x = vdiv_vd_vd_vd(vsub_vd_vd_vd(m, vcast_vd_d(1)), vadd_vd_vd_vd(m, vcast_vd_d(1)));
  x2 = vmul_vd_vd_vd(x, x);

  t = vcast_vd_d(+0.2211941750456081490e+0);
  t = vmla_vd_vd_vd_vd(t, x2, vcast_vd_d(+0.2200768693152277689e+0));
  t = vmla_vd_vd_vd_vd(t, x2, vcast_vd_d(+0.2623708057488514656e+0));
  t = vmla_vd_vd_vd_vd(t, x2, vcast_vd_d(+0.3205977477944495502e+0));
  t = vmla_vd_vd_vd_vd(t, x2, vcast_vd_d(+0.4121985945485324709e+0));
  t = vmla_vd_vd_vd_vd(t, x2, vcast_vd_d(+0.5770780162997058982e+0));
  t = vmla_vd_vd_vd_vd(t, x2, vcast_vd_d(+0.96179669392608091449  ));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vdouble2 s = ddadd_vd2_vd_vd2(vcast_vd_vi(e),
                                ddmul_vd2_vd_vd(x, vcast_vd_d(2.885390081777926774)));
#else
  vdouble2 s = ddadd_vd2_vd_vd2(e,
                                ddmul_vd2_vd_vd(x, vcast_vd_d(2.885390081777926774)));
#endif

  vdouble r = vmla_vd_vd_vd_vd(t, vmul_vd_vd_vd(x, x2), vadd_vd_vd_vd(vd2getx_vd_vd2(s), vd2gety_vd_vd2(s)));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  r = vsel_vd_vo_vd_vd(vispinf_vo_vd(d), vcast_vd_d(SLEEF_INFINITY), r);
  r = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vlt_vo_vd_vd(d, vcast_vd_d(0)), visnan_vo_vd(d)), vcast_vd_d(SLEEF_NAN), r);
  r = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(-SLEEF_INFINITY), r);
#else
  r = vfixup_vd_vd_vd_vi2_i(r, d, vcast_vi2_i((4 << (2*4)) | (3 << (4*4)) | (5 << (5*4)) | (2 << (6*4))), 0);
#endif

  return r;
}

EXPORT CONST VECTOR_CC vdouble xlog1p(vdouble d) {
  vdouble2 x;
  vdouble t, m, x2;

  vdouble dp1 = vadd_vd_vd_vd(d, vcast_vd_d(1));

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
  vopmask o = vlt_vo_vd_vd(dp1, vcast_vd_d(SLEEF_DBL_MIN));
  dp1 = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(dp1, vcast_vd_d((double)(INT64_C(1) << 32) * (double)(INT64_C(1) << 32))), dp1);
  vint e = vilogb2k_vi_vd(vmul_vd_vd_vd(dp1, vcast_vd_d(1.0/0.75)));
  t = vldexp3_vd_vd_vi(vcast_vd_d(1), vneg_vi_vi(e));
  m = vmla_vd_vd_vd_vd(d, t, vsub_vd_vd_vd(t, vcast_vd_d(1)));
  e = vsel_vi_vo_vi_vi(vcast_vo32_vo64(o), vsub_vi_vi_vi(e, vcast_vi_i(64)), e);
  vdouble2 s = ddmul_vd2_vd2_vd(vcast_vd2_d_d(0.693147180559945286226764, 2.319046813846299558417771e-17), vcast_vd_vi(e));
#else
  vdouble e = vgetexp_vd_vd(vmul_vd_vd_vd(dp1, vcast_vd_d(1.0/0.75)));
  e = vsel_vd_vo_vd_vd(vispinf_vo_vd(e), vcast_vd_d(1024.0), e);
  t = vldexp3_vd_vd_vi(vcast_vd_d(1), vneg_vi_vi(vrint_vi_vd(e)));
  m = vmla_vd_vd_vd_vd(d, t, vsub_vd_vd_vd(t, vcast_vd_d(1)));
  vdouble2 s = ddmul_vd2_vd2_vd(vcast_vd2_d_d(0.693147180559945286226764, 2.319046813846299558417771e-17), e);
#endif

  x = dddiv_vd2_vd2_vd2(vcast_vd2_vd_vd(m, vcast_vd_d(0)), ddadd_vd2_vd_vd(vcast_vd_d(2), m));
  x2 = vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(x));

  vdouble x4 = vmul_vd_vd_vd(x2, x2), x8 = vmul_vd_vd_vd(x4, x4);
  t = POLY7(x2, x4, x8,
            0.1532076988502701353e+0,
            0.1525629051003428716e+0,
            0.1818605932937785996e+0,
            0.2222214519839380009e+0,
            0.2857142932794299317e+0,
            0.3999999999635251990e+0,
            0.6666666666667333541e+0);

  s = ddadd_vd2_vd2_vd2(s, ddscale_vd2_vd2_vd(x, vcast_vd_d(2)));
  s = ddadd_vd2_vd2_vd(s, vmul_vd_vd_vd(vmul_vd_vd_vd(x2, vd2getx_vd_vd2(x)), t));

  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(s), vd2gety_vd_vd2(s));

  r = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(d, vcast_vd_d(1e+307)), vcast_vd_d(SLEEF_INFINITY), r);
  r = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vlt_vo_vd_vd(d, vcast_vd_d(-1)), visnan_vo_vd(d)), vcast_vd_d(SLEEF_NAN), r);
  r = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(-1)), vcast_vd_d(-SLEEF_INFINITY), r);
  r = vsel_vd_vo_vd_vd(visnegzero_vo_vd(d), vcast_vd_d(-0.0), r);

  return r;
}

//

EXPORT CONST VECTOR_CC vdouble xfabs(vdouble x) { return vabs_vd_vd(x); }

EXPORT CONST VECTOR_CC vdouble xcopysign(vdouble x, vdouble y) { return vcopysign_vd_vd_vd(x, y); }

EXPORT CONST VECTOR_CC vdouble xfmax(vdouble x, vdouble y) {
#if (defined(__x86_64__) || defined(__i386__)) && !defined(ENABLE_VECEXT) && !defined(ENABLE_PUREC)
  return vsel_vd_vo_vd_vd(visnan_vo_vd(y), x, vmax_vd_vd_vd(x, y));
#else
  return vsel_vd_vo_vd_vd(visnan_vo_vd(y), x, vsel_vd_vo_vd_vd(vgt_vo_vd_vd(x, y), x, y));
#endif
}

EXPORT CONST VECTOR_CC vdouble xfmin(vdouble x, vdouble y) {
#if (defined(__x86_64__) || defined(__i386__)) && !defined(ENABLE_VECEXT) && !defined(ENABLE_PUREC)
  return vsel_vd_vo_vd_vd(visnan_vo_vd(y), x, vmin_vd_vd_vd(x, y));
#else
  return vsel_vd_vo_vd_vd(visnan_vo_vd(y), x, vsel_vd_vo_vd_vd(vgt_vo_vd_vd(y, x), x, y));
#endif
}

EXPORT CONST VECTOR_CC vdouble xfdim(vdouble x, vdouble y) {
  vdouble ret = vsub_vd_vd_vd(x, y);
  ret = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vlt_vo_vd_vd(ret, vcast_vd_d(0)), veq_vo_vd_vd(x, y)), vcast_vd_d(0), ret);
  return ret;
}

EXPORT CONST VECTOR_CC vdouble xtrunc(vdouble x) { return vtruncate2_vd_vd(x); }
EXPORT CONST VECTOR_CC vdouble xfloor(vdouble x) { return vfloor2_vd_vd(x); }
EXPORT CONST VECTOR_CC vdouble xceil(vdouble x) { return vceil2_vd_vd(x); }
EXPORT CONST VECTOR_CC vdouble xround(vdouble x) { return vround2_vd_vd(x); }
EXPORT CONST VECTOR_CC vdouble xrint(vdouble x) { return vrint2_vd_vd(x); }

EXPORT CONST VECTOR_CC vdouble xnextafter(vdouble x, vdouble y) {
  x = vsel_vd_vo_vd_vd(veq_vo_vd_vd(x, vcast_vd_d(0)), vmulsign_vd_vd_vd(vcast_vd_d(0), y), x);
  vmask xi2 = vreinterpret_vm_vd(x);
  vopmask c = vxor_vo_vo_vo(vsignbit_vo_vd(x), vge_vo_vd_vd(y, x));

  xi2 = vsel_vm_vo64_vm_vm(c, vneg64_vm_vm(vxor_vm_vm_vm(xi2, vcast_vm_i_i((int)(1U << 31), 0))), xi2);

  xi2 = vsel_vm_vo64_vm_vm(vneq_vo_vd_vd(x, y), vsub64_vm_vm_vm(xi2, vcast_vm_i_i(0, 1)), xi2);

  xi2 = vsel_vm_vo64_vm_vm(c, vneg64_vm_vm(vxor_vm_vm_vm(xi2, vcast_vm_i_i((int)(1U << 31), 0))), xi2);

  vdouble ret = vreinterpret_vd_vm(xi2);

  ret = vsel_vd_vo_vd_vd(vand_vo_vo_vo(veq_vo_vd_vd(ret, vcast_vd_d(0)), vneq_vo_vd_vd(x, vcast_vd_d(0))),
                         vmulsign_vd_vd_vd(vcast_vd_d(0), x), ret);

  ret = vsel_vd_vo_vd_vd(vand_vo_vo_vo(veq_vo_vd_vd(x, vcast_vd_d(0)), veq_vo_vd_vd(y, vcast_vd_d(0))), y, ret);

  ret = vsel_vd_vo_vd_vd(vor_vo_vo_vo(visnan_vo_vd(x), visnan_vo_vd(y)), vcast_vd_d(SLEEF_NAN), ret);

  return ret;
}

EXPORT CONST VECTOR_CC vdouble xfrfrexp(vdouble x) {
  x = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(SLEEF_DBL_MIN)), vmul_vd_vd_vd(x, vcast_vd_d(UINT64_C(1) << 63)), x);

  vmask xm = vreinterpret_vm_vd(x);
  xm = vand_vm_vm_vm(xm, vcast_vm_i64(~INT64_C(0x7ff0000000000000)));
  xm = vor_vm_vm_vm (xm, vcast_vm_i64( INT64_C(0x3fe0000000000000)));

  vdouble ret = vreinterpret_vd_vm(xm);

  ret = vsel_vd_vo_vd_vd(visinf_vo_vd(x), vmulsign_vd_vd_vd(vcast_vd_d(SLEEF_INFINITY), x), ret);
  ret = vsel_vd_vo_vd_vd(veq_vo_vd_vd(x, vcast_vd_d(0)), x, ret);

  return ret;
}

EXPORT CONST VECTOR_CC vint xexpfrexp(vdouble x) {
  x = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(SLEEF_DBL_MIN)), vmul_vd_vd_vd(x, vcast_vd_d(UINT64_C(1) << 63)), x);

  vint ret = vcastu_vi_vm(vreinterpret_vm_vd(x));
  ret = vsub_vi_vi_vi(vand_vi_vi_vi(vsrl_vi_vi_i(ret, 20), vcast_vi_i(0x7ff)), vcast_vi_i(0x3fe));

  ret = vsel_vi_vo_vi_vi(vor_vo_vo_vo(vor_vo_vo_vo(veq_vo_vd_vd(x, vcast_vd_d(0)), visnan_vo_vd(x)), visinf_vo_vd(x)), vcast_vi_i(0), ret);

  return ret;
}

EXPORT CONST VECTOR_CC vdouble xfma(vdouble x, vdouble y, vdouble z) {
#ifdef ENABLE_FMA_DP
  return vfma_vd_vd_vd_vd(x, y, z);
#else
  vdouble h2 = vadd_vd_vd_vd(vmul_vd_vd_vd(x, y), z), q = vcast_vd_d(1);
  vopmask o = vlt_vo_vd_vd(vabs_vd_vd(h2), vcast_vd_d(1e-300));
  {
    const double c0 = UINT64_C(1) << 54, c1 = c0 * c0, c2 = c1 * c1;
    x = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(x, vcast_vd_d(c1)), x);
    y = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(y, vcast_vd_d(c1)), y);
    z = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(z, vcast_vd_d(c2)), z);
    q = vsel_vd_vo_vd_vd(o, vcast_vd_d(1.0 / c2), q);
  }
  o = vgt_vo_vd_vd(vabs_vd_vd(h2), vcast_vd_d(1e+300));
  {
    const double c0 = UINT64_C(1) << 54, c1 = c0 * c0, c2 = c1 * c1;
    x = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(x, vcast_vd_d(1.0 / c1)), x);
    y = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(y, vcast_vd_d(1.0 / c1)), y);
    z = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(z, vcast_vd_d(1.0 / c2)), z);
    q = vsel_vd_vo_vd_vd(o, vcast_vd_d(c2), q);
  }
  vdouble2 d = ddmul_vd2_vd_vd(x, y);
  d = ddadd2_vd2_vd2_vd(d, z);
  vdouble ret = vsel_vd_vo_vd_vd(vor_vo_vo_vo(veq_vo_vd_vd(x, vcast_vd_d(0)), veq_vo_vd_vd(y, vcast_vd_d(0))), z, vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d)));
  o = visinf_vo_vd(z);
  o = vandnot_vo_vo_vo(visinf_vo_vd(x), o);
  o = vandnot_vo_vo_vo(visnan_vo_vd(x), o);
  o = vandnot_vo_vo_vo(visinf_vo_vd(y), o);
  o = vandnot_vo_vo_vo(visnan_vo_vd(y), o);
  h2 = vsel_vd_vo_vd_vd(o, z, h2);

  o = vor_vo_vo_vo(visinf_vo_vd(h2), visnan_vo_vd(h2));

  return vsel_vd_vo_vd_vd(o, h2, vmul_vd_vd_vd(ret, q));
#endif
}

SQRTU05_FUNCATR VECTOR_CC vdouble xsqrt_u05(vdouble d) {
#if defined(ENABLE_FMA_DP)
  vdouble q, w, x, y, z;

  d = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(SLEEF_NAN), d);

  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(8.636168555094445E-78));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d(1.157920892373162E77)), d);
  q = vsel_vd_vo_vd_vd(o, vcast_vd_d(2.9387358770557188E-39), vcast_vd_d(1));

  y = vreinterpret_vd_vm(vsub64_vm_vm_vm(vcast_vm_i_i(0x5fe6ec85, 0xe7de30da), vsrl64_vm_vm_i(vreinterpret_vm_vd(d), 1)));

  x = vmul_vd_vd_vd(d, y);         w = vmul_vd_vd_vd(vcast_vd_d(0.5), y);
  y = vfmanp_vd_vd_vd_vd(x, w, vcast_vd_d(0.5));
  x = vfma_vd_vd_vd_vd(x, y, x);   w = vfma_vd_vd_vd_vd(w, y, w);
  y = vfmanp_vd_vd_vd_vd(x, w, vcast_vd_d(0.5));
  x = vfma_vd_vd_vd_vd(x, y, x);   w = vfma_vd_vd_vd_vd(w, y, w);
  y = vfmanp_vd_vd_vd_vd(x, w, vcast_vd_d(0.5));
  x = vfma_vd_vd_vd_vd(x, y, x);   w = vfma_vd_vd_vd_vd(w, y, w);

  y = vfmanp_vd_vd_vd_vd(x, w, vcast_vd_d(1.5));  w = vadd_vd_vd_vd(w, w);
  w = vmul_vd_vd_vd(w, y);
  x = vmul_vd_vd_vd(w, d);
  y = vfmapn_vd_vd_vd_vd(w, d, x); z = vfmanp_vd_vd_vd_vd(w, x, vcast_vd_d(1));

  z = vfmanp_vd_vd_vd_vd(w, y, z); w = vmul_vd_vd_vd(vcast_vd_d(0.5), x);
  w = vfma_vd_vd_vd_vd(w, z, y);
  w = vadd_vd_vd_vd(w, x);

  w = vmul_vd_vd_vd(w, q);

  w = vsel_vd_vo_vd_vd(vor_vo_vo_vo(veq_vo_vd_vd(d, vcast_vd_d(0)),
                                    veq_vo_vd_vd(d, vcast_vd_d(SLEEF_INFINITY))), d, w);

  w = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(SLEEF_NAN), w);

  return w;
#else
  vdouble q;
  vopmask o;

  d = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(SLEEF_NAN), d);

  o = vlt_vo_vd_vd(d, vcast_vd_d(8.636168555094445E-78));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d(1.157920892373162E77)), d);
  q = vsel_vd_vo_vd_vd(o, vcast_vd_d(2.9387358770557188E-39*0.5), vcast_vd_d(0.5));

  o = vgt_vo_vd_vd(d, vcast_vd_d(1.3407807929942597e+154));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d(7.4583407312002070e-155)), d);
  q = vsel_vd_vo_vd_vd(o, vcast_vd_d(1.1579208923731620e+77*0.5), q);

  vdouble x = vreinterpret_vd_vm(vsub64_vm_vm_vm(vcast_vm_i_i(0x5fe6ec86, 0), vsrl64_vm_vm_i(vreinterpret_vm_vd(vadd_vd_vd_vd(d, vcast_vd_d(1e-320))), 1)));

  x = vmul_vd_vd_vd(x, vsub_vd_vd_vd(vcast_vd_d(1.5), vmul_vd_vd_vd(vmul_vd_vd_vd(vmul_vd_vd_vd(vcast_vd_d(0.5), d), x), x)));
  x = vmul_vd_vd_vd(x, vsub_vd_vd_vd(vcast_vd_d(1.5), vmul_vd_vd_vd(vmul_vd_vd_vd(vmul_vd_vd_vd(vcast_vd_d(0.5), d), x), x)));
  x = vmul_vd_vd_vd(x, vsub_vd_vd_vd(vcast_vd_d(1.5), vmul_vd_vd_vd(vmul_vd_vd_vd(vmul_vd_vd_vd(vcast_vd_d(0.5), d), x), x)));
  x = vmul_vd_vd_vd(x, d);

  vdouble2 d2 = ddmul_vd2_vd2_vd2(ddadd2_vd2_vd_vd2(d, ddmul_vd2_vd_vd(x, x)), ddrec_vd2_vd(x));

  x = vmul_vd_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(d2), vd2gety_vd_vd2(d2)), q);

  x = vsel_vd_vo_vd_vd(vispinf_vo_vd(d), vcast_vd_d(SLEEF_INFINITY), x);
  x = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), d, x);

  return x;
#endif
}

EXPORT CONST VECTOR_CC vdouble xsqrt(vdouble d) {
#if defined(ACCURATE_SQRT)
  return vsqrt_vd_vd(d);
#else
  // fall back to approximation if ACCURATE_SQRT is undefined
  return xsqrt_u05(d);
#endif
}

EXPORT CONST VECTOR_CC vdouble xsqrt_u35(vdouble d) { return xsqrt_u05(d); }

EXPORT CONST VECTOR_CC vdouble xhypot_u05(vdouble x, vdouble y) {
  x = vabs_vd_vd(x);
  y = vabs_vd_vd(y);
  vdouble min = vmin_vd_vd_vd(x, y), n = min;
  vdouble max = vmax_vd_vd_vd(x, y), d = max;

  vopmask o = vlt_vo_vd_vd(max, vcast_vd_d(SLEEF_DBL_MIN));
  n = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(n, vcast_vd_d(UINT64_C(1) << 54)), n);
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d(UINT64_C(1) << 54)), d);

  vdouble2 t = dddiv_vd2_vd2_vd2(vcast_vd2_vd_vd(n, vcast_vd_d(0)), vcast_vd2_vd_vd(d, vcast_vd_d(0)));
  t = ddmul_vd2_vd2_vd(ddsqrt_vd2_vd2(ddadd2_vd2_vd2_vd(ddsqu_vd2_vd2(t), vcast_vd_d(1))), max);
  vdouble ret = vadd_vd_vd_vd(vd2getx_vd_vd2(t), vd2gety_vd_vd2(t));
  ret = vsel_vd_vo_vd_vd(visnan_vo_vd(ret), vcast_vd_d(SLEEF_INFINITY), ret);
  ret = vsel_vd_vo_vd_vd(veq_vo_vd_vd(min, vcast_vd_d(0)), max, ret);
  ret = vsel_vd_vo_vd_vd(vor_vo_vo_vo(visnan_vo_vd(x), visnan_vo_vd(y)), vcast_vd_d(SLEEF_NAN), ret);
  ret = vsel_vd_vo_vd_vd(vor_vo_vo_vo(veq_vo_vd_vd(x, vcast_vd_d(SLEEF_INFINITY)), veq_vo_vd_vd(y, vcast_vd_d(SLEEF_INFINITY))), vcast_vd_d(SLEEF_INFINITY), ret);

  return ret;
}

EXPORT CONST VECTOR_CC vdouble xhypot_u35(vdouble x, vdouble y) {
  x = vabs_vd_vd(x);
  y = vabs_vd_vd(y);
  vdouble min = vmin_vd_vd_vd(x, y);
  vdouble max = vmax_vd_vd_vd(x, y);

  vdouble t = vdiv_vd_vd_vd(min, max);
  vdouble ret = vmul_vd_vd_vd(max, vsqrt_vd_vd(vmla_vd_vd_vd_vd(t, t, vcast_vd_d(1))));
  ret = vsel_vd_vo_vd_vd(veq_vo_vd_vd(min, vcast_vd_d(0)), max, ret);
  ret = vsel_vd_vo_vd_vd(vor_vo_vo_vo(visnan_vo_vd(x), visnan_vo_vd(y)), vcast_vd_d(SLEEF_NAN), ret);
  ret = vsel_vd_vo_vd_vd(vor_vo_vo_vo(veq_vo_vd_vd(x, vcast_vd_d(SLEEF_INFINITY)), veq_vo_vd_vd(y, vcast_vd_d(SLEEF_INFINITY))), vcast_vd_d(SLEEF_INFINITY), ret);

  return ret;
}

static INLINE CONST VECTOR_CC vdouble vptrunc_vd_vd(vdouble x) { // round to integer toward 0, positive argument only
#ifdef FULL_FP_ROUNDING
  return vtruncate_vd_vd(x);
#else
  vdouble fr = vmla_vd_vd_vd_vd(vcast_vd_d(-(double)(INT64_C(1) << 31)), vcast_vd_vi(vtruncate_vi_vd(vmul_vd_vd_vd(x, vcast_vd_d(1.0 / (INT64_C(1) << 31))))), x);
  fr = vsub_vd_vd_vd(fr, vcast_vd_vi(vtruncate_vi_vd(fr)));
  return vsel_vd_vo_vd_vd(vge_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(INT64_C(1) << 52)), x, vsub_vd_vd_vd(x, fr));
#endif
}

/* TODO AArch64: potential optimization by using `vfmad_lane_f64` */
EXPORT CONST VECTOR_CC vdouble xfmod(vdouble x, vdouble y) {
  vdouble n = vabs_vd_vd(x), d = vabs_vd_vd(y), s = vcast_vd_d(1), q;
  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(SLEEF_DBL_MIN));
  n = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(n, vcast_vd_d(UINT64_C(1) << 54)), n);
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d(UINT64_C(1) << 54)), d);
  s  = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(s , vcast_vd_d(1.0 / (UINT64_C(1) << 54))), s);
  vdouble2 r = vcast_vd2_vd_vd(n, vcast_vd_d(0));
  vdouble rd = vtoward0_vd_vd(vrec_vd_vd(d));

  for(int i=0;i<21;i++) { // ceil(log2(DBL_MAX) / 52)
    q = vptrunc_vd_vd(vmul_vd_vd_vd(vtoward0_vd_vd(vd2getx_vd_vd2(r)), rd));
#ifndef ENABLE_FMA_DP
    q = vreinterpret_vd_vm(vand_vm_vm_vm(vreinterpret_vm_vd(q), vcast_vm_u64(UINT64_C(0xfffffffffffffffe))));
#endif
    q = vsel_vd_vo_vd_vd(vand_vo_vo_vo(vgt_vo_vd_vd(vmul_vd_vd_vd(vcast_vd_d(3), d), vd2getx_vd_vd2(r)),
                                       vge_vo_vd_vd(vd2getx_vd_vd2(r), d)),
                         vcast_vd_d(2), q);
    q = vsel_vd_vo_vd_vd(vand_vo_vo_vo(vgt_vo_vd_vd(vadd_vd_vd_vd(d, d), vd2getx_vd_vd2(r)),
                                       vge_vo_vd_vd(vd2getx_vd_vd2(r), d)),
                         vcast_vd_d(1), q);
    r = ddnormalize_vd2_vd2(ddadd2_vd2_vd2_vd2(r, ddmul_vd2_vd_vd(q, vneg_vd_vd(d))));
    if (vtestallones_i_vo64(vlt_vo_vd_vd(vd2getx_vd_vd2(r), d))) break;
  }

  vdouble ret = vmul_vd_vd_vd(vd2getx_vd_vd2(r), s);
  ret = vsel_vd_vo_vd_vd(veq_vo_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(r), vd2gety_vd_vd2(r)), d), vcast_vd_d(0), ret);

  ret = vmulsign_vd_vd_vd(ret, x);

  ret = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(n, d), x, ret);
  ret = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(SLEEF_NAN), ret);

  return ret;
}

static INLINE VECTOR_CC vdouble vrintk2_vd_vd(vdouble d) {
#ifdef FULL_FP_ROUNDING
  return vrint_vd_vd(d);
#else
  vdouble c = vmulsign_vd_vd_vd(vcast_vd_d(INT64_C(1) << 52), d);
  return vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(INT64_C(1) << 52)),
                          d, vorsign_vd_vd_vd(vsub_vd_vd_vd(vadd_vd_vd_vd(d, c), c), d));
#endif
}

EXPORT CONST VECTOR_CC vdouble xremainder(vdouble x, vdouble y) {
  vdouble n = vabs_vd_vd(x), d = vabs_vd_vd(y), s = vcast_vd_d(1), q;
  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(SLEEF_DBL_MIN*2));
  n = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(n, vcast_vd_d(UINT64_C(1) << 54)), n);
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(d, vcast_vd_d(UINT64_C(1) << 54)), d);
  s  = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(s , vcast_vd_d(1.0 / (UINT64_C(1) << 54))), s);
  vdouble rd = vrec_vd_vd(d);
  vdouble2 r = vcast_vd2_vd_vd(n, vcast_vd_d(0));
  vopmask qisodd = vneq_vo_vd_vd(vcast_vd_d(0), vcast_vd_d(0));

  for(int i=0;i<21;i++) { // ceil(log2(DBL_MAX) / 52)
    q = vrintk2_vd_vd(vmul_vd_vd_vd(vd2getx_vd_vd2(r), rd));
#ifndef ENABLE_FMA_DP
    q = vreinterpret_vd_vm(vand_vm_vm_vm(vreinterpret_vm_vd(q), vcast_vm_u64(UINT64_C(0xfffffffffffffffe))));
#endif
    q = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(vabs_vd_vd(vd2getx_vd_vd2(r)), vmul_vd_vd_vd(d, vcast_vd_d(1.5))), vmulsign_vd_vd_vd(vcast_vd_d(1.0), vd2getx_vd_vd2(r)), q);
    q = vsel_vd_vo_vd_vd(vor_vo_vo_vo(vlt_vo_vd_vd(vabs_vd_vd(vd2getx_vd_vd2(r)), vmul_vd_vd_vd(d, vcast_vd_d(0.5))),
                                      vandnot_vo_vo_vo(qisodd, veq_vo_vd_vd(vabs_vd_vd(vd2getx_vd_vd2(r)), vmul_vd_vd_vd(d, vcast_vd_d(0.5))))),
                         vcast_vd_d(0.0), q);
    if (vtestallones_i_vo64(veq_vo_vd_vd(q, vcast_vd_d(0)))) break;
    q = vsel_vd_vo_vd_vd(visinf_vo_vd(vmul_vd_vd_vd(q, vneg_vd_vd(d))), vadd_vd_vd_vd(q, vmulsign_vd_vd_vd(vcast_vd_d(-1), vd2getx_vd_vd2(r))), q);
    qisodd = vxor_vo_vo_vo(qisodd, visodd_vo_vd(q));
    r = ddnormalize_vd2_vd2(ddadd2_vd2_vd2_vd2(r, ddmul_vd2_vd_vd(q, vneg_vd_vd(d))));
  }

  vdouble ret = vmul_vd_vd_vd(vd2getx_vd_vd2(r), s);
  ret = vmulsign_vd_vd_vd(ret, x);
  ret = vsel_vd_vo_vd_vd(visinf_vo_vd(y), vsel_vd_vo_vd_vd(visinf_vo_vd(x), vcast_vd_d(SLEEF_NAN), x), ret);
  ret = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0)), vcast_vd_d(SLEEF_NAN), ret);
  return ret;
}

/* TODO AArch64: potential optimization by using `vfmad_lane_f64` */
static CONST dd2 gammak(vdouble a) {
  vdouble2 clc = vcast_vd2_d_d(0, 0), clln = vcast_vd2_d_d(1, 0), clld = vcast_vd2_d_d(1, 0);
  vdouble2 x, y, z;
  vdouble t, u;

  vopmask otiny = vlt_vo_vd_vd(vabs_vd_vd(a), vcast_vd_d(1e-306)), oref = vlt_vo_vd_vd(a, vcast_vd_d(0.5));

  x = vsel_vd2_vo_vd2_vd2(otiny, vcast_vd2_d_d(0, 0),
                          vsel_vd2_vo_vd2_vd2(oref, ddadd2_vd2_vd_vd(vcast_vd_d(1), vneg_vd_vd(a)),
                                              vcast_vd2_vd_vd(a, vcast_vd_d(0))));

  vopmask o0 = vand_vo_vo_vo(vle_vo_vd_vd(vcast_vd_d(0.5), vd2getx_vd_vd2(x)), vle_vo_vd_vd(vd2getx_vd_vd2(x), vcast_vd_d(1.1)));
  vopmask o2 = vle_vo_vd_vd(vcast_vd_d(2.3), vd2getx_vd_vd2(x));

  y = ddnormalize_vd2_vd2(ddmul_vd2_vd2_vd2(ddadd2_vd2_vd2_vd(x, vcast_vd_d(1)), x));
  y = ddnormalize_vd2_vd2(ddmul_vd2_vd2_vd2(ddadd2_vd2_vd2_vd(x, vcast_vd_d(2)), y));
  y = ddnormalize_vd2_vd2(ddmul_vd2_vd2_vd2(ddadd2_vd2_vd2_vd(x, vcast_vd_d(3)), y));
  y = ddnormalize_vd2_vd2(ddmul_vd2_vd2_vd2(ddadd2_vd2_vd2_vd(x, vcast_vd_d(4)), y));

  vopmask o = vand_vo_vo_vo(o2, vle_vo_vd_vd(vd2getx_vd_vd2(x), vcast_vd_d(7)));
  clln = vsel_vd2_vo_vd2_vd2(o, y, clln);

  x = vsel_vd2_vo_vd2_vd2(o, ddadd2_vd2_vd2_vd(x, vcast_vd_d(5)), x);

  t = vsel_vd_vo_vd_vd(o2, vrec_vd_vd(vd2getx_vd_vd2(x)), vd2getx_vd_vd2(ddnormalize_vd2_vd2(ddadd2_vd2_vd2_vd(x, vsel_vd_vo_d_d(o0, -1, -2)))));

  u = vsel_vd_vo_vo_d_d_d(o2, o0, -156.801412704022726379848862, +0.2947916772827614196e+2, +0.7074816000864609279e-7);
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +1.120804464289911606838558160000, +0.1281459691827820109e+3, +0.4009244333008730443e-6));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +13.39798545514258921833306020000, +0.2617544025784515043e+3, +0.1040114641628246946e-5));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -0.116546276599463200848033357000, +0.3287022855685790432e+3, +0.1508349150733329167e-5));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -1.391801093265337481495562410000, +0.2818145867730348186e+3, +0.1288143074933901020e-5));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +0.015056113040026424412918973400, +0.1728670414673559605e+3, +0.4744167749884993937e-6));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +0.179540117061234856098844714000, +0.7748735764030416817e+2, -0.6554816306542489902e-7));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -0.002481743600264997730942489280, +0.2512856643080930752e+2, -0.3189252471452599844e-6));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -0.029527880945699120504851034100, +0.5766792106140076868e+1, +0.1358883821470355377e-6));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +0.000540164767892604515196325186, +0.7270275473996180571e+0, -0.4343931277157336040e-6));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +0.006403362833808069794787256200, +0.8396709124579147809e-1, +0.9724785897406779555e-6));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -0.000162516262783915816896611252, -0.8211558669746804595e-1, -0.2036886057225966011e-5));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -0.001914438498565477526465972390, +0.6828831828341884458e-1, +0.4373363141819725815e-5));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +7.20489541602001055898311517e-05, -0.7712481339961671511e-1, -0.9439951268304008677e-5));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +0.000839498720672087279971000786, +0.8337492023017314957e-1, +0.2050727030376389804e-4));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -5.17179090826059219329394422e-05, -0.9094964931456242518e-1, -0.4492620183431184018e-4));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -0.000592166437353693882857342347, +0.1000996313575929358e+0, +0.9945751236071875931e-4));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +6.97281375836585777403743539e-05, -0.1113342861544207724e+0, -0.2231547599034983196e-3));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +0.000784039221720066627493314301, +0.1255096673213020875e+0, +0.5096695247101967622e-3));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -0.000229472093621399176949318732, -0.1440498967843054368e+0, -0.1192753911667886971e-2));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, -0.002681327160493827160473958490, +0.1695571770041949811e+0, +0.2890510330742210310e-2));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +0.003472222222222222222175164840, -0.2073855510284092762e+0, -0.7385551028674461858e-2));
  u = vmla_vd_vd_vd_vd(u, t, vsel_vd_vo_vo_d_d_d(o2, o0, +0.083333333333333333335592087900, +0.2705808084277815939e+0, +0.2058080842778455335e-1));

  y = ddmul_vd2_vd2_vd2(ddadd2_vd2_vd2_vd(x, vcast_vd_d(-0.5)), logk2(x));
  y = ddadd2_vd2_vd2_vd2(y, ddneg_vd2_vd2(x));
  y = ddadd2_vd2_vd2_vd2(y, vcast_vd2_d_d(0.91893853320467278056, -3.8782941580672414498e-17)); // 0.5*log(2*M_PI)

  z = ddadd2_vd2_vd2_vd(ddmul_vd2_vd_vd (u, t), vsel_vd_vo_d_d(o0, -0.4006856343865314862e+0, -0.6735230105319810201e-1));
  z = ddadd2_vd2_vd2_vd(ddmul_vd2_vd2_vd(z, t), vsel_vd_vo_d_d(o0, +0.8224670334241132030e+0, +0.3224670334241132030e+0));
  z = ddadd2_vd2_vd2_vd(ddmul_vd2_vd2_vd(z, t), vsel_vd_vo_d_d(o0, -0.5772156649015328655e+0, +0.4227843350984671345e+0));
  z = ddmul_vd2_vd2_vd(z, t);

  clc = vsel_vd2_vo_vd2_vd2(o2, y, z);

  clld = vsel_vd2_vo_vd2_vd2(o2, ddadd2_vd2_vd2_vd(ddmul_vd2_vd_vd(u, t), vcast_vd_d(1)), clld);

  y = clln;

  clc = vsel_vd2_vo_vd2_vd2(otiny, vcast_vd2_d_d(83.1776616671934334590333, 3.67103459631568507221878e-15), // log(2^120)
                            vsel_vd2_vo_vd2_vd2(oref, ddadd2_vd2_vd2_vd2(vcast_vd2_d_d(1.1447298858494001639, 1.026595116270782638e-17), ddneg_vd2_vd2(clc)), clc)); // log(M_PI)
  clln = vsel_vd2_vo_vd2_vd2(otiny, vcast_vd2_d_d(1, 0), vsel_vd2_vo_vd2_vd2(oref, clln, clld));

  if (!vtestallones_i_vo64(vnot_vo64_vo64(oref))) {
    t = vsub_vd_vd_vd(a, vmul_vd_vd_vd(vcast_vd_d(INT64_C(1) << 28), vcast_vd_vi(vtruncate_vi_vd(vmul_vd_vd_vd(a, vcast_vd_d(1.0 / (INT64_C(1) << 28)))))));
    x = ddmul_vd2_vd2_vd2(clld, sinpik(t));
  }

  clld = vsel_vd2_vo_vd2_vd2(otiny, vcast_vd2_vd_vd(vmul_vd_vd_vd(a, vcast_vd_d((INT64_C(1) << 60)*(double)(INT64_C(1) << 60))), vcast_vd_d(0)),
                             vsel_vd2_vo_vd2_vd2(oref, x, y));

  return dd2setab_dd2_vd2_vd2(clc, dddiv_vd2_vd2_vd2(clln, clld));
}

EXPORT CONST VECTOR_CC vdouble xtgamma_u1(vdouble a) {
  dd2 d = gammak(a);
  vdouble2 y = ddmul_vd2_vd2_vd2(expk2(dd2geta_vd2_dd2(d)), dd2getb_vd2_dd2(d));
  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(y), vd2gety_vd_vd2(y));
  vopmask o;

  o = vor_vo_vo_vo(vor_vo_vo_vo(veq_vo_vd_vd(a, vcast_vd_d(-SLEEF_INFINITY)),
                                vand_vo_vo_vo(vlt_vo_vd_vd(a, vcast_vd_d(0)), visint_vo_vd(a))),
                   vand_vo_vo_vo(vand_vo_vo_vo(visnumber_vo_vd(a), vlt_vo_vd_vd(a, vcast_vd_d(0))), visnan_vo_vd(r)));
  r = vsel_vd_vo_vd_vd(o, vcast_vd_d(SLEEF_NAN), r);

  o = vand_vo_vo_vo(vand_vo_vo_vo(vor_vo_vo_vo(veq_vo_vd_vd(a, vcast_vd_d(SLEEF_INFINITY)), visnumber_vo_vd(a)),
                                  vge_vo_vd_vd(a, vcast_vd_d(-SLEEF_DBL_MIN))),
                    vor_vo_vo_vo(vor_vo_vo_vo(veq_vo_vd_vd(a, vcast_vd_d(0)), vgt_vo_vd_vd(a, vcast_vd_d(200))), visnan_vo_vd(r)));
  r = vsel_vd_vo_vd_vd(o, vmulsign_vd_vd_vd(vcast_vd_d(SLEEF_INFINITY), a), r);

  return r;
}

EXPORT CONST VECTOR_CC vdouble xlgamma_u1(vdouble a) {
  dd2 d = gammak(a);
  vdouble2 y = ddadd2_vd2_vd2_vd2(dd2geta_vd2_dd2(d), logk2(ddabs_vd2_vd2(dd2getb_vd2_dd2(d))));
  vdouble r = vadd_vd_vd_vd(vd2getx_vd_vd2(y), vd2gety_vd_vd2(y));
  vopmask o;

  o = vor_vo_vo_vo(visinf_vo_vd(a),
                   vor_vo_vo_vo(vand_vo_vo_vo(vle_vo_vd_vd(a, vcast_vd_d(0)), visint_vo_vd(a)),
                                vand_vo_vo_vo(visnumber_vo_vd(a), visnan_vo_vd(r))));
  r = vsel_vd_vo_vd_vd(o, vcast_vd_d(SLEEF_INFINITY), r);

  return r;
}

static INLINE CONST vdouble2 ddmla_vd2_vd_vd2_vd2(vdouble x, vdouble2 y, vdouble2 z) {
  return ddadd_vd2_vd2_vd2(z, ddmul_vd2_vd2_vd(y, x));
}

static INLINE CONST VECTOR_CC vdouble2 poly2dd_b(vdouble x, vdouble2 c1, vdouble2 c0) { return ddmla_vd2_vd_vd2_vd2(x, c1, c0); }
static INLINE CONST VECTOR_CC vdouble2 poly2dd(vdouble x, vdouble c1, vdouble2 c0) { return ddmla_vd2_vd_vd2_vd2(x, vcast_vd2_vd_vd(c1, vcast_vd_d(0)), c0); }
static INLINE CONST VECTOR_CC vdouble2 poly4dd(vdouble x, vdouble c3, vdouble2 c2, vdouble2 c1, vdouble2 c0) {
  return ddmla_vd2_vd_vd2_vd2(vmul_vd_vd_vd(x, x), poly2dd(x, c3, c2), poly2dd_b(x, c1, c0));
}

EXPORT CONST VECTOR_CC vdouble xerf_u1(vdouble a) {
  vdouble t, x = vabs_vd_vd(a);
  vdouble2 t2;
  vdouble x2 = vmul_vd_vd_vd(x, x), x4 = vmul_vd_vd_vd(x2, x2);
  vdouble x8 = vmul_vd_vd_vd(x4, x4), x16 = vmul_vd_vd_vd(x8, x8);
  vopmask o25 = vle_vo_vd_vd(x, vcast_vd_d(2.5));

  if (LIKELY(vtestallones_i_vo64(o25))) {
    // Abramowitz and Stegun
    t = POLY21(x, x2, x4, x8, x16,
               -0.2083271002525222097e-14,
               +0.7151909970790897009e-13,
               -0.1162238220110999364e-11,
               +0.1186474230821585259e-10,
               -0.8499973178354613440e-10,
               +0.4507647462598841629e-9,
               -0.1808044474288848915e-8,
               +0.5435081826716212389e-8,
               -0.1143939895758628484e-7,
               +0.1215442362680889243e-7,
               +0.1669878756181250355e-7,
               -0.9808074602255194288e-7,
               +0.1389000557865837204e-6,
               +0.2945514529987331866e-6,
               -0.1842918273003998283e-5,
               +0.3417987836115362136e-5,
               +0.3860236356493129101e-5,
               -0.3309403072749947546e-4,
               +0.1060862922597579532e-3,
               +0.2323253155213076174e-3,
               +0.1490149719145544729e-3);
    t2 = poly4dd(x, t,
                 vcast_vd2_d_d(0.0092877958392275604405, 7.9287559463961107493e-19),
                 vcast_vd2_d_d(0.042275531758784692937, 1.3785226620501016138e-19),
                 vcast_vd2_d_d(0.07052369794346953491, 9.5846628070792092842e-19));
    t2 = ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd2_vd(t2, x));
    t2 = ddsqu_vd2_vd2(t2);
    t2 = ddsqu_vd2_vd2(t2);
    t2 = ddsqu_vd2_vd2(t2);
    t2 = ddsqu_vd2_vd2(t2);
    t2 = ddrec_vd2_vd2(t2);
  } else {
#undef C2V
#define C2V(c) (c)
    t = POLY21(x, x2, x4, x8, x16,
               vsel_vd_vo_d_d(o25, -0.2083271002525222097e-14, -0.4024015130752621932e-18),
               vsel_vd_vo_d_d(o25, +0.7151909970790897009e-13, +0.3847193332817048172e-16),
               vsel_vd_vo_d_d(o25, -0.1162238220110999364e-11, -0.1749316241455644088e-14),
               vsel_vd_vo_d_d(o25, +0.1186474230821585259e-10, +0.5029618322872872715e-13),
               vsel_vd_vo_d_d(o25, -0.8499973178354613440e-10, -0.1025221466851463164e-11),
               vsel_vd_vo_d_d(o25, +0.4507647462598841629e-9, +0.1573695559331945583e-10),
               vsel_vd_vo_d_d(o25, -0.1808044474288848915e-8, -0.1884658558040203709e-9),
               vsel_vd_vo_d_d(o25, +0.5435081826716212389e-8, +0.1798167853032159309e-8),
               vsel_vd_vo_d_d(o25, -0.1143939895758628484e-7, -0.1380745342355033142e-7),
               vsel_vd_vo_d_d(o25, +0.1215442362680889243e-7, +0.8525705726469103499e-7),
               vsel_vd_vo_d_d(o25, +0.1669878756181250355e-7, -0.4160448058101303405e-6),
               vsel_vd_vo_d_d(o25, -0.9808074602255194288e-7, +0.1517272660008588485e-5),
               vsel_vd_vo_d_d(o25, +0.1389000557865837204e-6, -0.3341634127317201697e-5),
               vsel_vd_vo_d_d(o25, +0.2945514529987331866e-6, -0.2515023395879724513e-5),
               vsel_vd_vo_d_d(o25, -0.1842918273003998283e-5, +0.6539731269664907554e-4),
               vsel_vd_vo_d_d(o25, +0.3417987836115362136e-5, -0.3551065097428388658e-3),
               vsel_vd_vo_d_d(o25, +0.3860236356493129101e-5, +0.1210736097958368864e-2),
               vsel_vd_vo_d_d(o25, -0.3309403072749947546e-4, -0.2605566912579998680e-2),
               vsel_vd_vo_d_d(o25, +0.1060862922597579532e-3, +0.1252823202436093193e-2),
               vsel_vd_vo_d_d(o25, +0.2323253155213076174e-3, +0.1820191395263313222e-1),
               vsel_vd_vo_d_d(o25, +0.1490149719145544729e-3, -0.1021557155453465954e+0));
    t2 = poly4dd(x, t,
                 vsel_vd2_vo_vd2_vd2(o25, vcast_vd2_d_d(0.0092877958392275604405, 7.9287559463961107493e-19),
                                     vcast_vd2_d_d(-0.63691044383641748361, -2.4249477526539431839e-17)),
                 vsel_vd2_vo_vd2_vd2(o25, vcast_vd2_d_d(0.042275531758784692937, 1.3785226620501016138e-19),
                                     vcast_vd2_d_d(-1.1282926061803961737, -6.2970338860410996505e-17)),
                 vsel_vd2_vo_vd2_vd2(o25, vcast_vd2_d_d(0.07052369794346953491, 9.5846628070792092842e-19),
                                     vcast_vd2_d_d(-1.2261313785184804967e-05, -5.5329707514490107044e-22)));
    vdouble2 s2 = ddadd_vd2_vd_vd2(vcast_vd_d(1), ddmul_vd2_vd2_vd(t2, x));
    s2 = ddsqu_vd2_vd2(s2);
    s2 = ddsqu_vd2_vd2(s2);
    s2 = ddsqu_vd2_vd2(s2);
    s2 = ddsqu_vd2_vd2(s2);
    s2 = ddrec_vd2_vd2(s2);
    t2 = vsel_vd2_vo_vd2_vd2(o25, s2, vcast_vd2_vd_vd(expk(t2), vcast_vd_d(0)));
  }

  t2 = ddadd2_vd2_vd2_vd(t2, vcast_vd_d(-1));

  vdouble z = vneg_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(t2), vd2gety_vd_vd2(t2)));
  z = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(x, vcast_vd_d(1e-8)), vmul_vd_vd_vd(x, vcast_vd_d(1.12837916709551262756245475959)), z);
  z = vsel_vd_vo_vd_vd(vge_vo_vd_vd(x, vcast_vd_d(6)), vcast_vd_d(1), z);
  z = vsel_vd_vo_vd_vd(visinf_vo_vd(a), vcast_vd_d(1), z);
  z = vsel_vd_vo_vd_vd(veq_vo_vd_vd(a, vcast_vd_d(0)), vcast_vd_d(0), z);
  z = vmulsign_vd_vd_vd(z, a);

  return z;
}

/* TODO AArch64: potential optimization by using `vfmad_lane_f64` */
EXPORT CONST VECTOR_CC vdouble xerfc_u15(vdouble a) {
  vdouble s = a, r = vcast_vd_d(0), t;
  vdouble2 u, d, x;
  a = vabs_vd_vd(a);
  vopmask o0 = vlt_vo_vd_vd(a, vcast_vd_d(1.0));
  vopmask o1 = vlt_vo_vd_vd(a, vcast_vd_d(2.2));
  vopmask o2 = vlt_vo_vd_vd(a, vcast_vd_d(4.2));
  vopmask o3 = vlt_vo_vd_vd(a, vcast_vd_d(27.3));

  u = vsel_vd2_vo_vd2_vd2(o0, ddmul_vd2_vd_vd(a, a), vsel_vd2_vo_vd2_vd2(o1, vcast_vd2_vd_vd(a, vcast_vd_d(0)), dddiv_vd2_vd2_vd2(vcast_vd2_d_d(1, 0), vcast_vd2_vd_vd(a, vcast_vd_d(0)))));

  t = vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, +0.6801072401395386139e-20, +0.3438010341362585303e-12, -0.5757819536420710449e+2, +0.2334249729638701319e+5);
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.2161766247570055669e-18, -0.1237021188160598264e-10, +0.4669289654498104483e+3, -0.4695661044933107769e+5));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, +0.4695919173301595670e-17, +0.2117985839877627852e-09, -0.1796329879461355858e+4, +0.3173403108748643353e+5));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.9049140419888007122e-16, -0.2290560929177369506e-08, +0.4355892193699575728e+4, +0.3242982786959573787e+4));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, +0.1634018903557410728e-14, +0.1748931621698149538e-07, -0.7456258884965764992e+4, -0.2014717999760347811e+5));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.2783485786333451745e-13, -0.9956602606623249195e-07, +0.9553977358167021521e+4, +0.1554006970967118286e+5));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, +0.4463221276786415752e-12, +0.4330010240640327080e-06, -0.9470019905444229153e+4, -0.6150874190563554293e+4));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.6711366622850136563e-11, -0.1435050600991763331e-05, +0.7387344321849855078e+4, +0.1240047765634815732e+4));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, +0.9422759050232662223e-10, +0.3460139479650695662e-05, -0.4557713054166382790e+4, -0.8210325475752699731e+2));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.1229055530100229098e-08, -0.4988908180632898173e-05, +0.2207866967354055305e+4, +0.3242443880839930870e+2));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, +0.1480719281585086512e-07, -0.1308775976326352012e-05, -0.8217975658621754746e+3, -0.2923418863833160586e+2));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.1636584469123399803e-06, +0.2825086540850310103e-04, +0.2268659483507917400e+3, +0.3457461732814383071e+0));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, +0.1646211436588923575e-05, -0.6393913713069986071e-04, -0.4633361260318560682e+2, +0.5489730155952392998e+1));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.1492565035840623511e-04, -0.2566436514695078926e-04, +0.9557380123733945965e+1, +0.1559934132251294134e-2));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, +0.1205533298178967851e-03, +0.5895792375659440364e-03, -0.2958429331939661289e+1, -0.1541741566831520638e+1));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.8548327023450850081e-03, -0.1695715579163588598e-02, +0.1670329508092765480e+0, +0.2823152230558364186e-5));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, +0.5223977625442187932e-02, +0.2089116434918055149e-03, +0.6096615680115419211e+0, +0.6249999184195342838e+0));
  t = vmla_vd_vd_vd_vd(t, vd2getx_vd_vd2(u), vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.2686617064513125222e-01, +0.1912855949584917753e-01, +0.1059212443193543585e-2, +0.1741749416408701288e-8));

  d = ddmul_vd2_vd2_vd(u, t);
  d = ddadd2_vd2_vd2_vd2(d, vcast_vd2_vd_vd(vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, 0.11283791670955126141, -0.10277263343147646779, -0.50005180473999022439, -0.5000000000258444377),
                                            vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -4.0175691625932118483e-18, -6.2338714083404900225e-18, 2.6362140569041995803e-17, -4.0074044712386992281e-17)));
  d = ddmul_vd2_vd2_vd2(d, u);
  d = ddadd2_vd2_vd2_vd2(d, vcast_vd2_vd_vd(vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, -0.37612638903183753802, -0.63661976742916359662, 1.601106273924963368e-06, 2.3761973137523364792e-13),
                                            vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, 1.3391897206042552387e-17, 7.6321019159085724662e-18, 1.1974001857764476775e-23, -1.1670076950531026582e-29)));
  d = ddmul_vd2_vd2_vd2(d, u);
  d = ddadd2_vd2_vd2_vd2(d, vcast_vd2_vd_vd(vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, 1.1283791670955125586, -1.1283791674717296161, -0.57236496645145429341, -0.57236494292470108114),
                                            vsel_vd_vo_vo_vo_d_d_d_d(o0, o1, o2, 1.5335459613165822674e-17, 8.0896847755965377194e-17, 3.0704553245872027258e-17, -2.3984352208056898003e-17)));

  x = ddmul_vd2_vd2_vd(vsel_vd2_vo_vd2_vd2(o1, d, vcast_vd2_vd_vd(vneg_vd_vd(a), vcast_vd_d(0))), a);
  x = vsel_vd2_vo_vd2_vd2(o1, x, ddadd2_vd2_vd2_vd2(x, d));
  x = vsel_vd2_vo_vd2_vd2(o0, ddsub_vd2_vd2_vd2(vcast_vd2_d_d(1, 0), x), expk2(x));
  x = vsel_vd2_vo_vd2_vd2(o1, x, ddmul_vd2_vd2_vd2(x, u));

  r = vsel_vd_vo_vd_vd(o3, vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x)), vcast_vd_d(0));
  r = vsel_vd_vo_vd_vd(vsignbit_vo_vd(s), vsub_vd_vd_vd(vcast_vd_d(2), r), r);
  r = vsel_vd_vo_vd_vd(visnan_vo_vd(s), vcast_vd_d(SLEEF_NAN), r);
  return r;
}
#endif // #if !defined(DETERMINISTIC)

#if !defined(DETERMINISTIC) && !defined(ENABLE_GNUABI) && !defined(SLEEF_GENHEADER)
// The normal and deterministic versions of implementations are common
// for the functions like sincospi_u05. Aliases are defined by
// DALIAS_* macros for such functions. The defined aliases
// (e.g. ysincospi_u05) are renamed(e.g. to
// Sleef_cinz_sincospid2_u05sse2) by rename*.h.

#ifdef ENABLE_ALIAS
#define DALIAS_vd_vd(FUNC) EXPORT CONST VECTOR_CC vdouble y ## FUNC(vdouble) __attribute__((alias( stringify(x ## FUNC) )));
#define DALIAS_vd2_vd(FUNC) EXPORT CONST VECTOR_CC vdouble2 y ## FUNC(vdouble) __attribute__((alias( stringify(x ## FUNC) )));
#define DALIAS_vi_vd(FUNC) EXPORT CONST VECTOR_CC vint y ## FUNC(vdouble) __attribute__((alias( stringify(x ## FUNC) )));
#define DALIAS_vd_vd_vd(FUNC) EXPORT CONST VECTOR_CC vdouble y ## FUNC(vdouble, vdouble) __attribute__((alias( stringify(x ## FUNC) )));
#define DALIAS_vd_vd_vd_vd(FUNC) EXPORT CONST VECTOR_CC vdouble y ## FUNC(vdouble, vdouble, vdouble) __attribute__((alias( stringify(x ## FUNC) )));
#else
#define DALIAS_vd_vd(FUNC) EXPORT CONST VECTOR_CC vdouble y ## FUNC(vdouble d) { return x ## FUNC (d); }
#define DALIAS_vd2_vd(FUNC) EXPORT CONST VECTOR_CC vdouble2 y ## FUNC(vdouble d) { return x ## FUNC (d); }
#define DALIAS_vi_vd(FUNC) EXPORT CONST VECTOR_CC vint y ## FUNC(vdouble d) { return x ## FUNC (d); }
#define DALIAS_vd_vd_vd(FUNC) EXPORT CONST VECTOR_CC vdouble y ## FUNC(vdouble x, vdouble y) { return x ## FUNC (x, y); }
#define DALIAS_vd_vd_vd_vd(FUNC) EXPORT CONST VECTOR_CC vdouble y ## FUNC(vdouble x, vdouble y, vdouble z) { return x ## FUNC (x, y, z); }
#endif

DALIAS_vd2_vd(sincospi_u05)
DALIAS_vd2_vd(sincospi_u35)
DALIAS_vd2_vd(modf)
DALIAS_vd_vd(log)
DALIAS_vd_vd(log_u1)
DALIAS_vd_vd_vd(pow)
DALIAS_vd_vd(sinh)
DALIAS_vd_vd(cosh)
DALIAS_vd_vd(tanh)
DALIAS_vd_vd(sinh_u35)
DALIAS_vd_vd(cosh_u35)
DALIAS_vd_vd(tanh_u35)
DALIAS_vd_vd(asinh)
DALIAS_vd_vd(acosh)
DALIAS_vd_vd(atanh)
DALIAS_vd_vd(cbrt)
DALIAS_vd_vd(cbrt_u1)
DALIAS_vd_vd(expm1)
DALIAS_vd_vd(log10)
DALIAS_vd_vd(log2)
DALIAS_vd_vd(log2_u35)
DALIAS_vd_vd(log1p)
DALIAS_vd_vd(fabs)
DALIAS_vd_vd_vd(copysign)
DALIAS_vd_vd_vd(fmax)
DALIAS_vd_vd_vd(fmin)
DALIAS_vd_vd_vd(fdim)
DALIAS_vd_vd(trunc)
DALIAS_vd_vd(floor)
DALIAS_vd_vd(ceil)
DALIAS_vd_vd(round)
DALIAS_vd_vd(rint)
DALIAS_vd_vd_vd(nextafter)
DALIAS_vd_vd(frfrexp)
DALIAS_vi_vd(expfrexp)
DALIAS_vd_vd_vd_vd(fma)
DALIAS_vd_vd(sqrt_u05)
DALIAS_vd_vd(sqrt_u35)
DALIAS_vd_vd_vd(hypot_u05)
DALIAS_vd_vd_vd(hypot_u35)
DALIAS_vd_vd_vd(fmod)
DALIAS_vd_vd_vd(remainder)
DALIAS_vd_vd(tgamma_u1)
DALIAS_vd_vd(lgamma_u1)
DALIAS_vd_vd(erf_u1)
DALIAS_vd_vd(erfc_u15)
#endif // #if !defined(DETERMINISTIC) && !defined(ENABLE_GNUABI) && !defined(SLEEF_GENHEADER)

#if !defined(ENABLE_GNUABI) && !defined(SLEEF_GENHEADER)
EXPORT CONST int xgetInt(int name) {
  if (1 <= name && name <= 10) return vavailability_i(name);
  return 0;
}

EXPORT CONST void *xgetPtr(int name) {
  if (name == 0) return ISANAME;
  return (void *)0;
}
#endif

#if defined(ALIAS_NO_EXT_SUFFIX) && !defined(DETERMINISTIC)
#include ALIAS_NO_EXT_SUFFIX
#endif

#ifdef ENABLE_MAIN
// gcc -DENABLE_MAIN -Wno-attributes -I../common -I../arch -DENABLE_AVX2 -mavx2 -mfma sleefsimddp.c rempitab.c ../common/common.c -lm
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
int main(int argc, char **argv) {
  vdouble d1 = vcast_vd_d(atof(argv[1]));
  //vdouble d2 = vcast_vd_d(atof(argv[2]));
  //vdouble d3 = vcast_vd_d(atof(argv[3]));
  //vdouble r = xnextafter(d1, d2);
  //int i;
  //double fr = frexp(atof(argv[1]), &i);
  //printf("%.20g\n", xfma(d1, d2, d3)[0]);;
  //printf("test %.20g\n", xtgamma_u1(d1)[0]);
  //printf("corr %.20g\n", tgamma(d1[0]));
  printf("test %.20g\n", xerf_u1(d1)[0]);
  printf("corr %.20g\n", erf(d1[0]));
  //printf("test %.20g\n", xerfc_u15(d1)[0]);
  //printf("corr %.20g\n", erfc(d1[0]));
  //printf("%.20g\n", nextafter(d1[0], d2[0]));;
  //printf("%.20g\n", vcast_d_vd(xhypot_u05(d1, d2)));
  //printf("%.20g\n", fr);
  //printf("%.20g\n", fmod(atof(argv[1]), atof(argv[2])));
  //printf("%.20g\n", xfmod(d1, d2)[0]);
  //vdouble2 r = xsincospi_u35(a);
  //printf("%g, %g\n", vcast_d_vd(r.x), vcast_d_vd(r.y));
}
#endif

#ifdef ENABLE_GNUABI
/* "finite" aliases for compatibility with GLIBC */
EXPORT CONST VECTOR_CC vdouble __acos_finite     (vdouble)          __attribute__((weak, alias(str_xacos     )));
EXPORT CONST VECTOR_CC vdouble __acosh_finite    (vdouble)          __attribute__((weak, alias(str_xacosh    )));
EXPORT CONST VECTOR_CC vdouble __asin_finite     (vdouble)          __attribute__((weak, alias(str_xasin_u1  )));
EXPORT CONST VECTOR_CC vdouble __atan2_finite    (vdouble, vdouble) __attribute__((weak, alias(str_xatan2_u1 )));
EXPORT CONST VECTOR_CC vdouble __atanh_finite    (vdouble)          __attribute__((weak, alias(str_xatanh    )));
EXPORT CONST VECTOR_CC vdouble __cosh_finite     (vdouble)          __attribute__((weak, alias(str_xcosh     )));
EXPORT CONST VECTOR_CC vdouble __exp10_finite    (vdouble)          __attribute__((weak, alias(str_xexp10    )));
EXPORT CONST VECTOR_CC vdouble __exp2_finite     (vdouble)          __attribute__((weak, alias(str_xexp2     )));
EXPORT CONST VECTOR_CC vdouble __exp_finite      (vdouble)          __attribute__((weak, alias(str_xexp      )));
EXPORT CONST VECTOR_CC vdouble __fmod_finite     (vdouble, vdouble) __attribute__((weak, alias(str_xfmod     )));
EXPORT CONST VECTOR_CC vdouble __remainder_finite(vdouble, vdouble) __attribute__((weak, alias(str_xremainder)));
EXPORT CONST VECTOR_CC vdouble __modf_finite     (vdouble, vdouble *) __attribute__((weak, alias(str_xmodf   )));
EXPORT CONST VECTOR_CC vdouble __hypot_u05_finite(vdouble, vdouble) __attribute__((weak, alias(str_xhypot_u05)));
EXPORT CONST VECTOR_CC vdouble __lgamma_u1_finite(vdouble)          __attribute__((weak, alias(str_xlgamma_u1)));
EXPORT CONST VECTOR_CC vdouble __log10_finite    (vdouble)          __attribute__((weak, alias(str_xlog10    )));
EXPORT CONST VECTOR_CC vdouble __log_finite      (vdouble)          __attribute__((weak, alias(str_xlog_u1   )));
EXPORT CONST VECTOR_CC vdouble __pow_finite      (vdouble, vdouble) __attribute__((weak, alias(str_xpow      )));
EXPORT CONST VECTOR_CC vdouble __sinh_finite     (vdouble)          __attribute__((weak, alias(str_xsinh     )));
EXPORT CONST VECTOR_CC vdouble __sqrt_finite     (vdouble)          __attribute__((weak, alias(str_xsqrt     )));
EXPORT CONST VECTOR_CC vdouble __tgamma_u1_finite(vdouble)          __attribute__((weak, alias(str_xtgamma_u1)));

#ifdef HEADER_MASKED
#include HEADER_MASKED
#endif
#endif /* #ifdef ENABLE_GNUABI */
