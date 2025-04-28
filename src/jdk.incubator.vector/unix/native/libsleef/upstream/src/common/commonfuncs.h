//   Copyright Naoki Shibata and contributors 2010 - 2023.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if !(defined(ENABLE_SVE) || defined(ENABLE_SVENOFMA) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
typedef struct {
  vdouble x, y, z;
} vdouble3;

static INLINE CONST VECTOR_CC vdouble  vd3getx_vd_vd3(vdouble3 v) { return v.x; }
static INLINE CONST VECTOR_CC vdouble  vd3gety_vd_vd3(vdouble3 v) { return v.y; }
static INLINE CONST VECTOR_CC vdouble  vd3getz_vd_vd3(vdouble3 v) { return v.z; }
static INLINE CONST VECTOR_CC vdouble3 vd3setxyz_vd3_vd_vd_vd(vdouble x, vdouble y, vdouble z)  {
  vdouble3 v = { x, y, z };
  return v;
}
static INLINE CONST VECTOR_CC vdouble3 vd3setx_vd3_vd3_vd(vdouble3 v, vdouble d) { v.x = d; return v; }
static INLINE CONST VECTOR_CC vdouble3 vd3sety_vd3_vd3_vd(vdouble3 v, vdouble d) { v.y = d; return v; }
static INLINE CONST VECTOR_CC vdouble3 vd3setz_vd3_vd3_vd(vdouble3 v, vdouble d) { v.z = d; return v; }

//

typedef struct {
  vdouble2 a, b;
} dd2;

static dd2 dd2setab_dd2_vd2_vd2(vdouble2 a, vdouble2 b) {
  dd2 r = { a, b };
  return r;
}
static vdouble2 dd2geta_vd2_dd2(dd2 d) { return d.a; }
static vdouble2 dd2getb_vd2_dd2(dd2 d) { return d.b; }

//

typedef struct {
  vmask e;
  vdouble3 d3;
} tdx;

static INLINE CONST VECTOR_CC vmask tdxgete_vm_tdx(tdx t) { return t.e; }
static INLINE CONST VECTOR_CC vdouble3 tdxgetd3_vd3_tdx(tdx t) { return t.d3; }
static INLINE CONST VECTOR_CC vdouble tdxgetd3x_vd_tdx(tdx t) { return t.d3.x; }
static INLINE CONST VECTOR_CC vdouble tdxgetd3y_vd_tdx(tdx t) { return t.d3.y; }
static INLINE CONST VECTOR_CC vdouble tdxgetd3z_vd_tdx(tdx t) { return t.d3.z; }
static INLINE CONST VECTOR_CC tdx tdxsete_tdx_tdx_vm(tdx t, vmask e) { t.e = e; return t; }
static INLINE CONST VECTOR_CC tdx tdxsetd3_tdx_tdx_vd3(tdx t, vdouble3 d3) { t.d3 = d3; return t; }
static INLINE CONST VECTOR_CC tdx tdxsetx_tdx_tdx_vd(tdx t, vdouble x) { t.d3.x = x; return t; }
static INLINE CONST VECTOR_CC tdx tdxsety_tdx_tdx_vd(tdx t, vdouble y) { t.d3.y = y; return t; }
static INLINE CONST VECTOR_CC tdx tdxsetz_tdx_tdx_vd(tdx t, vdouble z) { t.d3.z = z; return t; }
static INLINE CONST VECTOR_CC tdx tdxsetxyz_tdx_tdx_vd_vd_vd(tdx t, vdouble x, vdouble y, vdouble z) {
  t.d3 = (vdouble3) { x, y, z };
  return t;
}

static INLINE CONST VECTOR_CC tdx tdxseted3_tdx_vm_vd3(vmask e, vdouble3 d3) { return (tdx) { e, d3 }; }
static INLINE CONST VECTOR_CC tdx tdxsetexyz_tdx_vm_vd_vd_vd(vmask e, vdouble x, vdouble y, vdouble z) {
  return (tdx) { e, (vdouble3) { x, y, z } };
}

static INLINE CONST VECTOR_CC vmask vqgetx_vm_vq(vquad v) { return v.x; }
static INLINE CONST VECTOR_CC vmask vqgety_vm_vq(vquad v) { return v.y; }
static INLINE CONST VECTOR_CC vquad vqsetxy_vq_vm_vm(vmask x, vmask y) { return (vquad) { x, y }; }
static INLINE CONST VECTOR_CC vquad vqsetx_vq_vq_vm(vquad v, vmask x) { v.x = x; return v; }
static INLINE CONST VECTOR_CC vquad vqsety_vq_vq_vm(vquad v, vmask y) { v.y = y; return v; }

//

typedef struct {
  vdouble d;
  vint i;
} di_t;

static INLINE CONST VECTOR_CC vdouble digetd_vd_di(di_t d) { return d.d; }
static INLINE CONST VECTOR_CC vint digeti_vi_di(di_t d) { return d.i; }
static INLINE CONST VECTOR_CC di_t disetdi_di_vd_vi(vdouble d, vint i) {
  di_t r = { d, i };
  return r;
}

//

typedef struct {
  vdouble2 dd;
  vint i;
} ddi_t;

static INLINE CONST VECTOR_CC vdouble2 ddigetdd_vd2_ddi(ddi_t d) { return d.dd; }
static INLINE CONST VECTOR_CC vint ddigeti_vi_ddi(ddi_t d) { return d.i; }
static INLINE CONST VECTOR_CC ddi_t ddisetddi_ddi_vd2_vi(vdouble2 v, vint i) {
  ddi_t r = { v, i };
  return r;
}
static INLINE CONST VECTOR_CC ddi_t ddisetdd_ddi_ddi_vd2(ddi_t ddi, vdouble2 v) {
  ddi.dd = v;
  return ddi;
}

//

typedef struct {
  vdouble3 td;
  vint i;
} tdi_t;

static INLINE CONST VECTOR_CC vdouble3 tdigettd_vd3_tdi(tdi_t d) { return d.td; }
static INLINE CONST VECTOR_CC vdouble tdigetx_vd_tdi(tdi_t d) { return d.td.x; }
static INLINE CONST VECTOR_CC vint tdigeti_vi_tdi(tdi_t d) { return d.i; }
static INLINE CONST VECTOR_CC tdi_t tdisettdi_tdi_vd3_vi(vdouble3 v, vint i) {
  tdi_t r = { v, i };
  return r;
}
#endif

#if defined(ENABLE_MAIN)
// Functions for debugging
#include <stdio.h>
#include <wchar.h>

static void printvmask(char *mes, vmask g) {
  uint64_t u[VECTLENDP];
  vstoreu_v_p_vd((double *)u, vreinterpret_vd_vm(g));
  printf("%s ", mes);
  for(int i=0;i<VECTLENDP;i++) printf("%016lx : ", (unsigned long)u[i]);
  printf("\n");
}

#if !defined(ENABLE_SVE)
static void printvopmask(char *mes, vopmask g) {
  union {
    vopmask g;
    uint8_t u[sizeof(vopmask)];
  } cnv = { .g = g };
  printf("%s ", mes);
  for(int i=0;i<sizeof(vopmask);i++) printf("%02x", cnv.u[i]);
  printf("\n");
}
#else
static void printvopmask(char *mes, vopmask g) {
  vmask m = vand_vm_vo64_vm(g, vcast_vm_i64(-1));
  printvmask(mes, m);
}
#endif

static void printvdouble(char *mes, vdouble vd) {
  double u[VECTLENDP];
  vstoreu_v_p_vd((double *)u, vd);
  printf("%s ", mes);
  for(int i=0;i<VECTLENDP;i++) printf("%.20g : ", u[i]);
  printf("\n");
}

static void printvint(char *mes, vint vi) {
  uint32_t u[VECTLENDP];
  vstoreu_v_p_vi((int32_t *)u, vi);
  printf("%s ", mes);
  for(int i=0;i<VECTLENDP;i++) printf("%08x : ", (unsigned)u[i]);
  printf("\n");
}

static void printvint64(char *mes, vint64 vi) {
  uint64_t u[VECTLENDP*2];
  vstoreu_v_p_vd((double *)u, vreinterpret_vd_vm(vreinterpret_vm_vi64(vi)));
  printf("%s ", mes);
  for(int i=0;i<VECTLENDP;i++) printf("%016lx : ", (unsigned long)u[i]);
  printf("\n");
}

static void printvquad(char *mes, vquad g) {
  uint64_t u[VECTLENDP*2];
  vstoreu_v_p_vd((double *)u, vreinterpret_vd_vm(vqgetx_vm_vq(g)));
  vstoreu_v_p_vd((double *)&u[VECTLENDP], vreinterpret_vd_vm(vqgety_vm_vq(g)));
  printf("%s ", mes);
  for(int i=0;i<VECTLENDP*2;i++) printf("%016lx : ", (unsigned long)(u[i]));
  printf("\n");
}
#endif // #if defined(ENABLE_MAIN)

///////////////////////////////////////////////////////////////////////////////////

// vdouble functions

static INLINE CONST VECTOR_CC vopmask visnegzero_vo_vd(vdouble d) {
  return veq64_vo_vm_vm(vreinterpret_vm_vd(d), vreinterpret_vm_vd(vcast_vd_d(-0.0)));
}

static INLINE CONST VECTOR_CC vopmask visnumber_vo_vd(vdouble x) {
  return vandnot_vo_vo_vo(visinf_vo_vd(x), veq_vo_vd_vd(x, x));
}

static INLINE CONST vopmask visnonfinite_vo_vd(vdouble x) {
  return veq64_vo_vm_vm(vand_vm_vm_vm(vreinterpret_vm_vd(x), vcast_vm_i64(INT64_C(0x7ff0000000000000))), vcast_vm_i64(INT64_C(0x7ff0000000000000)));
}

static INLINE CONST vmask vsignbit_vm_vd(vdouble d) {
  return vand_vm_vm_vm(vreinterpret_vm_vd(d), vreinterpret_vm_vd(vcast_vd_d(-0.0)));
}

static INLINE CONST vopmask vsignbit_vo_vd(vdouble d) {
  return veq64_vo_vm_vm(vand_vm_vm_vm(vreinterpret_vm_vd(d), vreinterpret_vm_vd(vcast_vd_d(-0.0))), vreinterpret_vm_vd(vcast_vd_d(-0.0)));
}

static INLINE CONST vdouble vclearlsb_vd_vd_i(vdouble d, int n) {
  return vreinterpret_vd_vm(vand_vm_vm_vm(vreinterpret_vm_vd(d), vcast_vm_u64((~UINT64_C(0)) << n)));
}

static INLINE CONST VECTOR_CC vdouble vtoward0_vd_vd(vdouble x) { // returns nextafter(x, 0)
  vdouble t = vreinterpret_vd_vm(vadd64_vm_vm_vm(vreinterpret_vm_vd(x), vcast_vm_i64(-1)));
  return vsel_vd_vo_vd_vd(veq_vo_vd_vd(x, vcast_vd_d(0)), vcast_vd_d(0), t);
}

#if !(defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
static INLINE CONST vdouble vmulsign_vd_vd_vd(vdouble x, vdouble y) {
  return vreinterpret_vd_vm(vxor_vm_vm_vm(vreinterpret_vm_vd(x), vsignbit_vm_vd(y)));
}
#endif

static INLINE CONST VECTOR_CC vdouble vsign_vd_vd(vdouble d) {
  return vmulsign_vd_vd_vd(vcast_vd_d(1.0), d);
}

#if !(defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
static INLINE CONST VECTOR_CC vdouble vorsign_vd_vd_vd(vdouble x, vdouble y) {
  return vreinterpret_vd_vm(vor_vm_vm_vm(vreinterpret_vm_vd(x), vsignbit_vm_vd(y)));
}

static INLINE CONST VECTOR_CC vdouble vcopysign_vd_vd_vd(vdouble x, vdouble y) {
  return vreinterpret_vd_vm(vxor_vm_vm_vm(vandnot_vm_vm_vm(vreinterpret_vm_vd(vcast_vd_d(-0.0)), vreinterpret_vm_vd(x)),
                                          vand_vm_vm_vm   (vreinterpret_vm_vd(vcast_vd_d(-0.0)), vreinterpret_vm_vd(y))));
}
#endif

static INLINE CONST VECTOR_CC vdouble vtruncate2_vd_vd(vdouble x) {
#ifdef FULL_FP_ROUNDING
  return vtruncate_vd_vd(x);
#else
  vdouble fr = vsub_vd_vd_vd(x, vmul_vd_vd_vd(vcast_vd_d(INT64_C(1) << 31), vcast_vd_vi(vtruncate_vi_vd(vmul_vd_vd_vd(x, vcast_vd_d(1.0 / (INT64_C(1) << 31)))))));
  fr = vsub_vd_vd_vd(fr, vcast_vd_vi(vtruncate_vi_vd(fr)));
  return vsel_vd_vo_vd_vd(vor_vo_vo_vo(visinf_vo_vd(x), vge_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(INT64_C(1) << 52))), x, vcopysign_vd_vd_vd(vsub_vd_vd_vd(x, fr), x));
#endif
}

static INLINE CONST VECTOR_CC vdouble vfloor2_vd_vd(vdouble x) {
  vdouble fr = vsub_vd_vd_vd(x, vmul_vd_vd_vd(vcast_vd_d(INT64_C(1) << 31), vcast_vd_vi(vtruncate_vi_vd(vmul_vd_vd_vd(x, vcast_vd_d(1.0 / (INT64_C(1) << 31)))))));
  fr = vsub_vd_vd_vd(fr, vcast_vd_vi(vtruncate_vi_vd(fr)));
  fr = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(fr, vcast_vd_d(0)), vadd_vd_vd_vd(fr, vcast_vd_d(1.0)), fr);
  return vsel_vd_vo_vd_vd(vor_vo_vo_vo(visinf_vo_vd(x), vge_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(INT64_C(1) << 52))), x, vcopysign_vd_vd_vd(vsub_vd_vd_vd(x, fr), x));
}

static INLINE CONST VECTOR_CC vdouble vceil2_vd_vd(vdouble x) {
  vdouble fr = vsub_vd_vd_vd(x, vmul_vd_vd_vd(vcast_vd_d(INT64_C(1) << 31), vcast_vd_vi(vtruncate_vi_vd(vmul_vd_vd_vd(x, vcast_vd_d(1.0 / (INT64_C(1) << 31)))))));
  fr = vsub_vd_vd_vd(fr, vcast_vd_vi(vtruncate_vi_vd(fr)));
  fr = vsel_vd_vo_vd_vd(vle_vo_vd_vd(fr, vcast_vd_d(0)), fr, vsub_vd_vd_vd(fr, vcast_vd_d(1.0)));
  return vsel_vd_vo_vd_vd(vor_vo_vo_vo(visinf_vo_vd(x), vge_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(INT64_C(1) << 52))), x, vcopysign_vd_vd_vd(vsub_vd_vd_vd(x, fr), x));
}

static INLINE CONST VECTOR_CC vdouble vround2_vd_vd(vdouble d) {
  vdouble x = vadd_vd_vd_vd(d, vcast_vd_d(0.5));
  vdouble fr = vsub_vd_vd_vd(x, vmul_vd_vd_vd(vcast_vd_d(INT64_C(1) << 31), vcast_vd_vi(vtruncate_vi_vd(vmul_vd_vd_vd(x, vcast_vd_d(1.0 / (INT64_C(1) << 31)))))));
  fr = vsub_vd_vd_vd(fr, vcast_vd_vi(vtruncate_vi_vd(fr)));
  x = vsel_vd_vo_vd_vd(vand_vo_vo_vo(vle_vo_vd_vd(x, vcast_vd_d(0)), veq_vo_vd_vd(fr, vcast_vd_d(0))), vsub_vd_vd_vd(x, vcast_vd_d(1.0)), x);
  fr = vsel_vd_vo_vd_vd(vlt_vo_vd_vd(fr, vcast_vd_d(0)), vadd_vd_vd_vd(fr, vcast_vd_d(1.0)), fr);
  x = vsel_vd_vo_vd_vd(veq_vo_vd_vd(d, vcast_vd_d(0.49999999999999994449)), vcast_vd_d(0), x);
  return vsel_vd_vo_vd_vd(vor_vo_vo_vo(visinf_vo_vd(d), vge_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(INT64_C(1) << 52))), d, vcopysign_vd_vd_vd(vsub_vd_vd_vd(x, fr), d));
}

static INLINE  CONST VECTOR_CC vdouble vrint2_vd_vd(vdouble d) {
#ifdef FULL_FP_ROUNDING
  return vrint_vd_vd(d);
#else
  vdouble c = vmulsign_vd_vd_vd(vcast_vd_d(INT64_C(1) << 52), d);
  return vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(INT64_C(1) << 52)),
                          d, vorsign_vd_vd_vd(vsub_vd_vd_vd(vadd_vd_vd_vd(d, c), c), d));
#endif
}

static INLINE CONST VECTOR_CC vopmask visint_vo_vd(vdouble d) {
  return veq_vo_vd_vd(vrint2_vd_vd(d), d);
}

static INLINE CONST VECTOR_CC vopmask visodd_vo_vd(vdouble d) {
  vdouble x = vmul_vd_vd_vd(d, vcast_vd_d(0.5));
  return vneq_vo_vd_vd(vrint2_vd_vd(x), x);
}

// ilogb

#if !defined(ENABLE_AVX512F) && !defined(ENABLE_AVX512FNOFMA)
static INLINE CONST VECTOR_CC vint vilogbk_vi_vd(vdouble d) {
  vopmask o = vlt_vo_vd_vd(d, vcast_vd_d(4.9090934652977266E-91));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(vcast_vd_d(2.037035976334486E90), d), d);
  vint q = vcastu_vi_vm(vreinterpret_vm_vd(d));
  q = vand_vi_vi_vi(q, vcast_vi_i((int)(((1U << 12) - 1) << 20)));
  q = vsrl_vi_vi_i(q, 20);
  q = vsub_vi_vi_vi(q, vsel_vi_vo_vi_vi(vcast_vo32_vo64(o), vcast_vi_i(300 + 0x3ff), vcast_vi_i(0x3ff)));
  return q;
}

static INLINE CONST VECTOR_CC vint vilogb2k_vi_vd(vdouble d) {
  vint q = vcastu_vi_vm(vreinterpret_vm_vd(d));
  q = vsrl_vi_vi_i(q, 20);
  q = vand_vi_vi_vi(q, vcast_vi_i(0x7ff));
  q = vsub_vi_vi_vi(q, vcast_vi_i(0x3ff));
  return q;
}
#endif

static INLINE CONST vmask vilogb2k_vm_vd(vdouble d) {
  vmask m = vreinterpret_vm_vd(d);
  m = vsrl64_vm_vm_i(m, 20 + 32);
  m = vand_vm_vm_vm(m, vcast_vm_i64(0x7ff));
  m = vsub64_vm_vm_vm(m, vcast_vm_i64(0x3ff));
  return m;
}

static INLINE CONST vmask vilogb3k_vm_vd(vdouble d) {
  vmask m = vreinterpret_vm_vd(d);
  m = vsrl64_vm_vm_i(m, 20 + 32);
  m = vand_vm_vm_vm(m, vcast_vm_i64(0x7ff));
  return m;
}

// ldexp

static INLINE CONST VECTOR_CC vdouble vpow2i_vd_vi(vint q) {
  q = vadd_vi_vi_vi(vcast_vi_i(0x3ff), q);
  vmask r = vcastu_vm_vi(vsll_vi_vi_i(q, 20));
  return vreinterpret_vd_vm(r);
}

static INLINE CONST VECTOR_CC vdouble vpow2i_vd_vm(vmask q) {
  q = vadd64_vm_vm_vm(vcast_vm_i64(0x3ff), q);
  return vreinterpret_vd_vm(vsll64_vm_vm_i(q, 52));
}

static INLINE CONST VECTOR_CC vdouble vldexp_vd_vd_vi(vdouble x, vint q) {
  vint m = vsra_vi_vi_i(q, 31);
  m = vsll_vi_vi_i(vsub_vi_vi_vi(vsra_vi_vi_i(vadd_vi_vi_vi(m, q), 9), m), 7);
  q = vsub_vi_vi_vi(q, vsll_vi_vi_i(m, 2));
  m = vadd_vi_vi_vi(vcast_vi_i(0x3ff), m);
  m = vandnot_vi_vo_vi(vgt_vo_vi_vi(vcast_vi_i(0), m), m);
  m = vsel_vi_vo_vi_vi(vgt_vo_vi_vi(m, vcast_vi_i(0x7ff)), vcast_vi_i(0x7ff), m);
  vmask r = vcastu_vm_vi(vsll_vi_vi_i(m, 20));
  vdouble y = vreinterpret_vd_vm(r);
  return vmul_vd_vd_vd(vmul_vd_vd_vd(vmul_vd_vd_vd(vmul_vd_vd_vd(vmul_vd_vd_vd(x, y), y), y), y), vpow2i_vd_vi(q));
}

static INLINE CONST VECTOR_CC vdouble vldexp2_vd_vd_vi(vdouble d, vint e) {
  return vmul_vd_vd_vd(vmul_vd_vd_vd(d, vpow2i_vd_vi(vsra_vi_vi_i(e, 1))), vpow2i_vd_vi(vsub_vi_vi_vi(e, vsra_vi_vi_i(e, 1))));
}

static INLINE CONST VECTOR_CC vdouble vldexp3_vd_vd_vi(vdouble d, vint q) {
  return vreinterpret_vd_vm(vadd64_vm_vm_vm(vreinterpret_vm_vd(d), vcastu_vm_vi(vsll_vi_vi_i(q, 20))));
}

static INLINE CONST vdouble vldexp1_vd_vd_vm(vdouble d, vmask e) {
  vmask m = vsrl64_vm_vm_i(e, 2);
  e = vsub64_vm_vm_vm(vsub64_vm_vm_vm(vsub64_vm_vm_vm(e, m), m), m);
  d = vmul_vd_vd_vd(d, vpow2i_vd_vm(m));
  d = vmul_vd_vd_vd(d, vpow2i_vd_vm(m));
  d = vmul_vd_vd_vd(d, vpow2i_vd_vm(m));
  d = vmul_vd_vd_vd(d, vpow2i_vd_vm(e));
  return d;
}

static INLINE CONST vdouble vldexp2_vd_vd_vm(vdouble d, vmask e) {
  return vmul_vd_vd_vd(vmul_vd_vd_vd(d, vpow2i_vd_vm(vsrl64_vm_vm_i(e, 1))), vpow2i_vd_vm(vsub64_vm_vm_vm(e, vsrl64_vm_vm_i(e, 1))));
}

static INLINE CONST vdouble vldexp3_vd_vd_vm(vdouble d, vmask q) {
  return vreinterpret_vd_vm(vadd64_vm_vm_vm(vreinterpret_vm_vd(d), vsll64_vm_vm_i(q, 52)));
}

// vmask functions

static INLINE CONST vdouble vcast_vd_vm(vmask m) { return vcast_vd_vi(vcast_vi_vm(m)); } // 32 bit only
static INLINE CONST vmask vtruncate_vm_vd(vdouble d) { return vcast_vm_vi(vtruncate_vi_vd(d)); }

static INLINE CONST vopmask vlt64_vo_vm_vm(vmask x, vmask y) { return vgt64_vo_vm_vm(y, x); }

static INLINE CONST vopmask vnot_vo64_vo64(vopmask x) {
  return vxor_vo_vo_vo(x, veq64_vo_vm_vm(vcast_vm_i64(0), vcast_vm_i64(0)));
}

static INLINE CONST vopmask vugt64_vo_vm_vm(vmask x, vmask y) { // unsigned compare
  x = vxor_vm_vm_vm(vcast_vm_u64(UINT64_C(0x8000000000000000)), x);
  y = vxor_vm_vm_vm(vcast_vm_u64(UINT64_C(0x8000000000000000)), y);
  return vgt64_vo_vm_vm(x, y);
}

static INLINE CONST vmask vilogbk_vm_vd(vdouble d) {
  vopmask o = vlt_vo_vd_vd(vabs_vd_vd(d), vcast_vd_d(4.9090934652977266E-91));
  d = vsel_vd_vo_vd_vd(o, vmul_vd_vd_vd(vcast_vd_d(2.037035976334486E90), d), d);
  vmask q = vreinterpret_vm_vd(d);
  q = vsrl64_vm_vm_i(q, 20 + 32);
  q = vand_vm_vm_vm(q, vcast_vm_i64(0x7ff));
  q = vsub64_vm_vm_vm(q, vsel_vm_vo64_vm_vm(o, vcast_vm_i64(300 + 0x3ff), vcast_vm_i64(0x3ff)));
  return q;
}

// vquad functions

static INLINE CONST vquad sel_vq_vo_vq_vq(vopmask o, vquad x, vquad y) {
  return vqsetxy_vq_vm_vm(vsel_vm_vo64_vm_vm(o, vqgetx_vm_vq(x), vqgetx_vm_vq(y)), vsel_vm_vo64_vm_vm(o, vqgety_vm_vq(x), vqgety_vm_vq(y)));
}

static INLINE CONST vquad add128_vq_vq_vq(vquad x, vquad y) {
  vquad r = vqsetxy_vq_vm_vm(vadd64_vm_vm_vm(vqgetx_vm_vq(x), vqgetx_vm_vq(y)), vadd64_vm_vm_vm(vqgety_vm_vq(x), vqgety_vm_vq(y)));
  r = vqsety_vq_vq_vm(r, vadd64_vm_vm_vm(vqgety_vm_vq(r), vand_vm_vo64_vm(vugt64_vo_vm_vm(vqgetx_vm_vq(x), vqgetx_vm_vq(r)), vcast_vm_i64(1))));
  return r;
}


static INLINE CONST vquad imdvq_vq_vm_vm(vmask x, vmask y) { vquad r = vqsetxy_vq_vm_vm(x, y); return r; }

// imm must be smaller than 64
#define srl128_vq_vq_i(m, imm)                                  \
  imdvq_vq_vm_vm(vor_vm_vm_vm(vsrl64_vm_vm_i(vqgetx_vm_vq(m), imm), vsll64_vm_vm_i(vqgety_vm_vq(m), 64-imm)), vsrl64_vm_vm_i(vqgety_vm_vq(m), imm))

// This function is equivalent to :
// di_t ret = { x - rint(4 * x) * 0.25, (int32_t)(rint(4 * x) - rint(x) * 4) };
static INLINE CONST di_t rempisub(vdouble x) {
#ifdef FULL_FP_ROUNDING
  vdouble y = vrint_vd_vd(vmul_vd_vd_vd(x, vcast_vd_d(4)));
  vint vi = vtruncate_vi_vd(vsub_vd_vd_vd(y, vmul_vd_vd_vd(vrint_vd_vd(x), vcast_vd_d(4))));
  return disetdi_di_vd_vi(vsub_vd_vd_vd(x, vmul_vd_vd_vd(y, vcast_vd_d(0.25))), vi);
#else
  vdouble c = vmulsign_vd_vd_vd(vcast_vd_d(INT64_C(1) << 52), x);
  vdouble rint4x = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vabs_vd_vd(vmul_vd_vd_vd(vcast_vd_d(4), x)), vcast_vd_d(INT64_C(1) << 52)),
                                    vmul_vd_vd_vd(vcast_vd_d(4), x),
                                    vorsign_vd_vd_vd(vsub_vd_vd_vd(vmla_vd_vd_vd_vd(vcast_vd_d(4), x, c), c), x));
  vdouble rintx  = vsel_vd_vo_vd_vd(vgt_vo_vd_vd(vabs_vd_vd(x), vcast_vd_d(INT64_C(1) << 52)),
                                    x, vorsign_vd_vd_vd(vsub_vd_vd_vd(vadd_vd_vd_vd(x, c), c), x));
  return disetdi_di_vd_vi(vmla_vd_vd_vd_vd(vcast_vd_d(-0.25), rint4x, x),
                          vtruncate_vi_vd(vmla_vd_vd_vd_vd(vcast_vd_d(-4), rintx, rint4x)));
#endif
}
