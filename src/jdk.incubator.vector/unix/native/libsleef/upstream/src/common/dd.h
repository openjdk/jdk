//   Copyright Naoki Shibata and contributors 2010 - 2024.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if !(defined(ENABLE_SVE) || defined(ENABLE_SVENOFMA) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
#if !defined(SLEEF_ENABLE_CUDA)
typedef struct {
  vdouble x, y;
} vdouble2;
#else
typedef double2 vdouble2;
#endif

static INLINE CONST VECTOR_CC vdouble  vd2getx_vd_vd2(vdouble2 v) { return v.x; }
static INLINE CONST VECTOR_CC vdouble  vd2gety_vd_vd2(vdouble2 v) { return v.y; }
static INLINE CONST VECTOR_CC vdouble2 vd2setxy_vd2_vd_vd(vdouble x, vdouble y)  { vdouble2 v; v.x = x; v.y = y; return v; }
static INLINE CONST VECTOR_CC vdouble2 vd2setx_vd2_vd2_vd(vdouble2 v, vdouble d) { v.x = d; return v; }
static INLINE CONST VECTOR_CC vdouble2 vd2sety_vd2_vd2_vd(vdouble2 v, vdouble d) { v.y = d; return v; }
#endif

#if !defined(SLEEF_ENABLE_CUDA)
typedef struct {
  double x, y;
} double2;
#endif

static INLINE CONST VECTOR_CC double2 dd(double h, double l) {
  double2 ret = { h, l };
  return ret;
}

static INLINE CONST VECTOR_CC vdouble vupper_vd_vd(vdouble d) {
  return vreinterpret_vd_vm(vand_vm_vm_vm(vreinterpret_vm_vd(d), vcast_vm_i_i(0xffffffff, 0xf8000000)));
}

static INLINE CONST VECTOR_CC vdouble2 vcast_vd2_vd_vd(vdouble h, vdouble l) {
  return vd2setxy_vd2_vd_vd(h, l);
}

static INLINE CONST VECTOR_CC vdouble2 vcast_vd2_d_d(double h, double l) {
  return vd2setxy_vd2_vd_vd(vcast_vd_d(h), vcast_vd_d(l));
}

static INLINE CONST VECTOR_CC vdouble2 vcast_vd2_d2(double2 dd) {
  return vd2setxy_vd2_vd_vd(vcast_vd_d(dd.x), vcast_vd_d(dd.y));
}

static INLINE CONST VECTOR_CC vdouble2 vsel_vd2_vo_vd2_vd2(vopmask m, vdouble2 x, vdouble2 y) {
  return vd2setxy_vd2_vd_vd(vsel_vd_vo_vd_vd(m, vd2getx_vd_vd2(x), vd2getx_vd_vd2(y)),
                            vsel_vd_vo_vd_vd(m, vd2gety_vd_vd2(x), vd2gety_vd_vd2(y)));
}

static INLINE CONST VECTOR_CC vdouble2 vsel_vd2_vo_d_d_d_d(vopmask o, double x1, double y1, double x0, double y0) {
  return vd2setxy_vd2_vd_vd(vsel_vd_vo_d_d(o, x1, x0),
                            vsel_vd_vo_d_d(o, y1, y0));
}

static INLINE CONST VECTOR_CC vdouble vadd_vd_3vd(vdouble v0, vdouble v1, vdouble v2) {
  return vadd_vd_vd_vd(vadd_vd_vd_vd(v0, v1), v2);
}

static INLINE CONST VECTOR_CC vdouble vadd_vd_4vd(vdouble v0, vdouble v1, vdouble v2, vdouble v3) {
  return vadd_vd_3vd(vadd_vd_vd_vd(v0, v1), v2, v3);
}

static INLINE CONST VECTOR_CC vdouble vadd_vd_5vd(vdouble v0, vdouble v1, vdouble v2, vdouble v3, vdouble v4) {
  return vadd_vd_4vd(vadd_vd_vd_vd(v0, v1), v2, v3, v4);
}

static INLINE CONST VECTOR_CC vdouble vadd_vd_6vd(vdouble v0, vdouble v1, vdouble v2, vdouble v3, vdouble v4, vdouble v5) {
  return vadd_vd_5vd(vadd_vd_vd_vd(v0, v1), v2, v3, v4, v5);
}

static INLINE CONST VECTOR_CC vdouble vadd_vd_7vd(vdouble v0, vdouble v1, vdouble v2, vdouble v3, vdouble v4, vdouble v5, vdouble v6) {
  return vadd_vd_6vd(vadd_vd_vd_vd(v0, v1), v2, v3, v4, v5, v6);
}

static INLINE CONST VECTOR_CC vdouble vsub_vd_3vd(vdouble v0, vdouble v1, vdouble v2) {
  return vsub_vd_vd_vd(vsub_vd_vd_vd(v0, v1), v2);
}

static INLINE CONST VECTOR_CC vdouble vsub_vd_4vd(vdouble v0, vdouble v1, vdouble v2, vdouble v3) {
  return vsub_vd_3vd(vsub_vd_vd_vd(v0, v1), v2, v3);
}

static INLINE CONST VECTOR_CC vdouble vsub_vd_5vd(vdouble v0, vdouble v1, vdouble v2, vdouble v3, vdouble v4) {
  return vsub_vd_4vd(vsub_vd_vd_vd(v0, v1), v2, v3, v4);
}

static INLINE CONST VECTOR_CC vdouble vsub_vd_6vd(vdouble v0, vdouble v1, vdouble v2, vdouble v3, vdouble v4, vdouble v5) {
  return vsub_vd_5vd(vsub_vd_vd_vd(v0, v1), v2, v3, v4, v5);
}

//

static INLINE CONST VECTOR_CC vdouble2 ddneg_vd2_vd2(vdouble2 x) {
  return vcast_vd2_vd_vd(vneg_vd_vd(vd2getx_vd_vd2(x)), vneg_vd_vd(vd2gety_vd_vd2(x)));
}

static INLINE CONST VECTOR_CC vdouble2 ddabs_vd2_vd2(vdouble2 x) {
  return vcast_vd2_vd_vd(vabs_vd_vd(vd2getx_vd_vd2(x)),
                         vreinterpret_vd_vm(vxor_vm_vm_vm(vreinterpret_vm_vd(vd2gety_vd_vd2(x)),
                                                          vand_vm_vm_vm(vreinterpret_vm_vd(vd2getx_vd_vd2(x)),
                                                                        vreinterpret_vm_vd(vcast_vd_d(-0.0))))));
}

static INLINE CONST VECTOR_CC vdouble2 ddnormalize_vd2_vd2(vdouble2 t) {
  vdouble s = vadd_vd_vd_vd(vd2getx_vd_vd2(t), vd2gety_vd_vd2(t));
  return vd2setxy_vd2_vd_vd(s, vadd_vd_vd_vd(vsub_vd_vd_vd(vd2getx_vd_vd2(t), s), vd2gety_vd_vd2(t)));
}

static INLINE CONST VECTOR_CC vdouble2 ddscale_vd2_vd2_vd(vdouble2 d, vdouble s) {
  return vd2setxy_vd2_vd_vd(vmul_vd_vd_vd(vd2getx_vd_vd2(d), s), vmul_vd_vd_vd(vd2gety_vd_vd2(d), s));
}

static INLINE CONST VECTOR_CC vdouble2 ddscale_vd2_vd2_d(vdouble2 d, double s) { return ddscale_vd2_vd2_vd(d, vcast_vd_d(s)); }

static INLINE CONST VECTOR_CC vdouble2 ddadd_vd2_vd_vd(vdouble x, vdouble y) {
  vdouble s = vadd_vd_vd_vd(x, y);
  return vd2setxy_vd2_vd_vd(s, vadd_vd_vd_vd(vsub_vd_vd_vd(x, s), y));
}

static INLINE CONST VECTOR_CC vdouble2 ddadd2_vd2_vd_vd(vdouble x, vdouble y) {
  vdouble s = vadd_vd_vd_vd(x, y);
  vdouble v = vsub_vd_vd_vd(s, x);
  return vd2setxy_vd2_vd_vd(s, vadd_vd_vd_vd(vsub_vd_vd_vd(x, vsub_vd_vd_vd(s, v)), vsub_vd_vd_vd(y, v)));
}

static INLINE CONST VECTOR_CC vdouble2 ddadd_vd2_vd2_vd(vdouble2 x, vdouble y) {
  vdouble s = vadd_vd_vd_vd(vd2getx_vd_vd2(x), y);
  return vd2setxy_vd2_vd_vd(s, vadd_vd_3vd(vsub_vd_vd_vd(vd2getx_vd_vd2(x), s), y, vd2gety_vd_vd2(x)));
}

static INLINE CONST VECTOR_CC vdouble2 ddsub_vd2_vd2_vd(vdouble2 x, vdouble y) {
  vdouble s = vsub_vd_vd_vd(vd2getx_vd_vd2(x), y);
  return vd2setxy_vd2_vd_vd(s, vadd_vd_vd_vd(vsub_vd_vd_vd(vsub_vd_vd_vd(vd2getx_vd_vd2(x), s), y), vd2gety_vd_vd2(x)));
}

static INLINE CONST VECTOR_CC vdouble2 ddadd2_vd2_vd2_vd(vdouble2 x, vdouble y) {
  vdouble s = vadd_vd_vd_vd(vd2getx_vd_vd2(x), y);
  vdouble v = vsub_vd_vd_vd(s, vd2getx_vd_vd2(x));
  vdouble w = vadd_vd_vd_vd(vsub_vd_vd_vd(vd2getx_vd_vd2(x), vsub_vd_vd_vd(s, v)), vsub_vd_vd_vd(y, v));
  return vd2setxy_vd2_vd_vd(s, vadd_vd_vd_vd(w, vd2gety_vd_vd2(x)));
}

static INLINE CONST VECTOR_CC vdouble2 ddadd_vd2_vd_vd2(vdouble x, vdouble2 y) {
  vdouble s = vadd_vd_vd_vd(x, vd2getx_vd_vd2(y));
  return vd2setxy_vd2_vd_vd(s, vadd_vd_3vd(vsub_vd_vd_vd(x, s), vd2getx_vd_vd2(y), vd2gety_vd_vd2(y)));
}

static INLINE CONST VECTOR_CC vdouble2 ddadd2_vd2_vd_vd2(vdouble x, vdouble2 y) {
  vdouble s = vadd_vd_vd_vd(x, vd2getx_vd_vd2(y));
  vdouble v = vsub_vd_vd_vd(s, x);
  return vd2setxy_vd2_vd_vd(s, vadd_vd_vd_vd(vadd_vd_vd_vd(vsub_vd_vd_vd(x, vsub_vd_vd_vd(s, v)),
                                                           vsub_vd_vd_vd(vd2getx_vd_vd2(y), v)), vd2gety_vd_vd2(y)));
}

static INLINE CONST VECTOR_CC vdouble2 ddadd_vd2_vd2_vd2(vdouble2 x, vdouble2 y) {
  // |x| >= |y|

  vdouble s = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(y));
  return vd2setxy_vd2_vd_vd(s, vadd_vd_4vd(vsub_vd_vd_vd(vd2getx_vd_vd2(x), s), vd2getx_vd_vd2(y), vd2gety_vd_vd2(x), vd2gety_vd_vd2(y)));
}

static INLINE CONST VECTOR_CC vdouble2 ddadd2_vd2_vd2_vd2(vdouble2 x, vdouble2 y) {
  vdouble s = vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(y));
  vdouble v = vsub_vd_vd_vd(s, vd2getx_vd_vd2(x));
  vdouble t = vadd_vd_vd_vd(vsub_vd_vd_vd(vd2getx_vd_vd2(x), vsub_vd_vd_vd(s, v)), vsub_vd_vd_vd(vd2getx_vd_vd2(y), v));
  return vd2setxy_vd2_vd_vd(s, vadd_vd_vd_vd(t, vadd_vd_vd_vd(vd2gety_vd_vd2(x), vd2gety_vd_vd2(y))));
}

static INLINE CONST VECTOR_CC vdouble2 ddsub_vd2_vd_vd(vdouble x, vdouble y) {
  // |x| >= |y|

  vdouble s = vsub_vd_vd_vd(x, y);
  return vd2setxy_vd2_vd_vd(s, vsub_vd_vd_vd(vsub_vd_vd_vd(x, s), y));
}

static INLINE CONST VECTOR_CC vdouble2 ddsub_vd2_vd2_vd2(vdouble2 x, vdouble2 y) {
  // |x| >= |y|

  vdouble s = vsub_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(y));
  vdouble t = vsub_vd_vd_vd(vd2getx_vd_vd2(x), s);
  t = vsub_vd_vd_vd(t, vd2getx_vd_vd2(y));
  t = vadd_vd_vd_vd(t, vd2gety_vd_vd2(x));
  return vd2setxy_vd2_vd_vd(s, vsub_vd_vd_vd(t, vd2gety_vd_vd2(y)));
}

#ifdef ENABLE_FMA_DP
static INLINE CONST VECTOR_CC vdouble2 dddiv_vd2_vd2_vd2(vdouble2 n, vdouble2 d) {
  vdouble t = vrec_vd_vd(vd2getx_vd_vd2(d));
  vdouble s = vmul_vd_vd_vd(vd2getx_vd_vd2(n), t);
  vdouble u = vfmapn_vd_vd_vd_vd(t, vd2getx_vd_vd2(n), s);
  vdouble v = vfmanp_vd_vd_vd_vd(vd2gety_vd_vd2(d), t, vfmanp_vd_vd_vd_vd(vd2getx_vd_vd2(d), t, vcast_vd_d(1)));
  return vd2setxy_vd2_vd_vd(s, vfma_vd_vd_vd_vd(s, v, vfma_vd_vd_vd_vd(vd2gety_vd_vd2(n), t, u)));
}

static INLINE CONST VECTOR_CC vdouble2 ddmul_vd2_vd_vd(vdouble x, vdouble y) {
  vdouble s = vmul_vd_vd_vd(x, y);
  return vd2setxy_vd2_vd_vd(s, vfmapn_vd_vd_vd_vd(x, y, s));
}

static INLINE CONST VECTOR_CC vdouble2 ddsqu_vd2_vd2(vdouble2 x) {
  vdouble s = vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(x));
  return vd2setxy_vd2_vd_vd(s, vfma_vd_vd_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(x)), vd2gety_vd_vd2(x), vfmapn_vd_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(x), s)));
}

static INLINE CONST VECTOR_CC vdouble2 ddmul_vd2_vd2_vd2(vdouble2 x, vdouble2 y) {
  vdouble s = vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(y));
  return vd2setxy_vd2_vd_vd(s, vfma_vd_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(y), vfma_vd_vd_vd_vd(vd2gety_vd_vd2(x), vd2getx_vd_vd2(y), vfmapn_vd_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(y), s))));
}

static INLINE CONST VECTOR_CC vdouble ddmul_vd_vd2_vd2(vdouble2 x, vdouble2 y) {
  return vfma_vd_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(y), vfma_vd_vd_vd_vd(vd2gety_vd_vd2(x), vd2getx_vd_vd2(y), vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(y))));
}

static INLINE CONST VECTOR_CC vdouble ddsqu_vd_vd2(vdouble2 x) {
  return vfma_vd_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(x), vadd_vd_vd_vd(vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x)), vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(x))));
}

static INLINE CONST VECTOR_CC vdouble2 ddmul_vd2_vd2_vd(vdouble2 x, vdouble y) {
  vdouble s = vmul_vd_vd_vd(vd2getx_vd_vd2(x), y);
  return vd2setxy_vd2_vd_vd(s, vfma_vd_vd_vd_vd(vd2gety_vd_vd2(x), y, vfmapn_vd_vd_vd_vd(vd2getx_vd_vd2(x), y, s)));
}

static INLINE CONST VECTOR_CC vdouble2 ddrec_vd2_vd(vdouble d) {
  vdouble s = vrec_vd_vd(d);
  return vd2setxy_vd2_vd_vd(s, vmul_vd_vd_vd(s, vfmanp_vd_vd_vd_vd(d, s, vcast_vd_d(1))));
}

static INLINE CONST VECTOR_CC vdouble2 ddrec_vd2_vd2(vdouble2 d) {
  vdouble s = vrec_vd_vd(vd2getx_vd_vd2(d));
  return vd2setxy_vd2_vd_vd(s, vmul_vd_vd_vd(s, vfmanp_vd_vd_vd_vd(vd2gety_vd_vd2(d), s, vfmanp_vd_vd_vd_vd(vd2getx_vd_vd2(d), s, vcast_vd_d(1)))));
}
#else // #ifdef ENABLE_FMA_DP
static INLINE CONST VECTOR_CC vdouble2 dddiv_vd2_vd2_vd2(vdouble2 n, vdouble2 d) {
  vdouble t = vrec_vd_vd(vd2getx_vd_vd2(d));
  vdouble dh  = vupper_vd_vd(vd2getx_vd_vd2(d)), dl  = vsub_vd_vd_vd(vd2getx_vd_vd2(d),  dh);
  vdouble th  = vupper_vd_vd(t  ), tl  = vsub_vd_vd_vd(t  ,  th);
  vdouble nhh = vupper_vd_vd(vd2getx_vd_vd2(n)), nhl = vsub_vd_vd_vd(vd2getx_vd_vd2(n), nhh);

  vdouble s = vmul_vd_vd_vd(vd2getx_vd_vd2(n), t);

  vdouble u = vadd_vd_5vd(vsub_vd_vd_vd(vmul_vd_vd_vd(nhh, th), s), vmul_vd_vd_vd(nhh, tl), vmul_vd_vd_vd(nhl, th), vmul_vd_vd_vd(nhl, tl),
                    vmul_vd_vd_vd(s, vsub_vd_5vd(vcast_vd_d(1), vmul_vd_vd_vd(dh, th), vmul_vd_vd_vd(dh, tl), vmul_vd_vd_vd(dl, th), vmul_vd_vd_vd(dl, tl))));

  return vd2setxy_vd2_vd_vd(s, vmla_vd_vd_vd_vd(t, vsub_vd_vd_vd(vd2gety_vd_vd2(n), vmul_vd_vd_vd(s, vd2gety_vd_vd2(d))), u));
}

static INLINE CONST VECTOR_CC vdouble2 ddmul_vd2_vd_vd(vdouble x, vdouble y) {
  vdouble xh = vupper_vd_vd(x), xl = vsub_vd_vd_vd(x, xh);
  vdouble yh = vupper_vd_vd(y), yl = vsub_vd_vd_vd(y, yh);

  vdouble s = vmul_vd_vd_vd(x, y);
  return vd2setxy_vd2_vd_vd(s, vadd_vd_5vd(vmul_vd_vd_vd(xh, yh), vneg_vd_vd(s), vmul_vd_vd_vd(xl, yh), vmul_vd_vd_vd(xh, yl), vmul_vd_vd_vd(xl, yl)));
}

static INLINE CONST VECTOR_CC vdouble2 ddmul_vd2_vd2_vd(vdouble2 x, vdouble y) {
  vdouble xh = vupper_vd_vd(vd2getx_vd_vd2(x)), xl = vsub_vd_vd_vd(vd2getx_vd_vd2(x), xh);
  vdouble yh = vupper_vd_vd(y  ), yl = vsub_vd_vd_vd(y  , yh);

  vdouble s = vmul_vd_vd_vd(vd2getx_vd_vd2(x), y);
  return vd2setxy_vd2_vd_vd(s, vadd_vd_6vd(vmul_vd_vd_vd(xh, yh), vneg_vd_vd(s), vmul_vd_vd_vd(xl, yh), vmul_vd_vd_vd(xh, yl), vmul_vd_vd_vd(xl, yl), vmul_vd_vd_vd(vd2gety_vd_vd2(x), y)));
}

static INLINE CONST VECTOR_CC vdouble2 ddmul_vd2_vd2_vd2(vdouble2 x, vdouble2 y) {
  vdouble xh = vupper_vd_vd(vd2getx_vd_vd2(x)), xl = vsub_vd_vd_vd(vd2getx_vd_vd2(x), xh);
  vdouble yh = vupper_vd_vd(vd2getx_vd_vd2(y)), yl = vsub_vd_vd_vd(vd2getx_vd_vd2(y), yh);

  vdouble s = vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(y));
  return vd2setxy_vd2_vd_vd(s, vadd_vd_7vd(vmul_vd_vd_vd(xh, yh), vneg_vd_vd(s), vmul_vd_vd_vd(xl, yh), vmul_vd_vd_vd(xh, yl), vmul_vd_vd_vd(xl, yl), vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2gety_vd_vd2(y)), vmul_vd_vd_vd(vd2gety_vd_vd2(x), vd2getx_vd_vd2(y))));
}

static INLINE CONST VECTOR_CC vdouble ddmul_vd_vd2_vd2(vdouble2 x, vdouble2 y) {
  vdouble xh = vupper_vd_vd(vd2getx_vd_vd2(x)), xl = vsub_vd_vd_vd(vd2getx_vd_vd2(x), xh);
  vdouble yh = vupper_vd_vd(vd2getx_vd_vd2(y)), yl = vsub_vd_vd_vd(vd2getx_vd_vd2(y), yh);

  return vadd_vd_6vd(vmul_vd_vd_vd(vd2gety_vd_vd2(x), yh), vmul_vd_vd_vd(xh, vd2gety_vd_vd2(y)), vmul_vd_vd_vd(xl, yl), vmul_vd_vd_vd(xh, yl), vmul_vd_vd_vd(xl, yh), vmul_vd_vd_vd(xh, yh));
}

static INLINE CONST VECTOR_CC vdouble2 ddsqu_vd2_vd2(vdouble2 x) {
  vdouble xh = vupper_vd_vd(vd2getx_vd_vd2(x)), xl = vsub_vd_vd_vd(vd2getx_vd_vd2(x), xh);

  vdouble s = vmul_vd_vd_vd(vd2getx_vd_vd2(x), vd2getx_vd_vd2(x));
  return vd2setxy_vd2_vd_vd(s, vadd_vd_5vd(vmul_vd_vd_vd(xh, xh), vneg_vd_vd(s), vmul_vd_vd_vd(vadd_vd_vd_vd(xh, xh), xl), vmul_vd_vd_vd(xl, xl), vmul_vd_vd_vd(vd2getx_vd_vd2(x), vadd_vd_vd_vd(vd2gety_vd_vd2(x), vd2gety_vd_vd2(x)))));
}

static INLINE CONST VECTOR_CC vdouble ddsqu_vd_vd2(vdouble2 x) {
  vdouble xh = vupper_vd_vd(vd2getx_vd_vd2(x)), xl = vsub_vd_vd_vd(vd2getx_vd_vd2(x), xh);

  return vadd_vd_5vd(vmul_vd_vd_vd(xh, vd2gety_vd_vd2(x)), vmul_vd_vd_vd(xh, vd2gety_vd_vd2(x)), vmul_vd_vd_vd(xl, xl), vadd_vd_vd_vd(vmul_vd_vd_vd(xh, xl), vmul_vd_vd_vd(xh, xl)), vmul_vd_vd_vd(xh, xh));
}

static INLINE CONST VECTOR_CC vdouble2 ddrec_vd2_vd(vdouble d) {
  vdouble t = vrec_vd_vd(d);
  vdouble dh = vupper_vd_vd(d), dl = vsub_vd_vd_vd(d, dh);
  vdouble th = vupper_vd_vd(t), tl = vsub_vd_vd_vd(t, th);

  return vd2setxy_vd2_vd_vd(t, vmul_vd_vd_vd(t, vsub_vd_5vd(vcast_vd_d(1), vmul_vd_vd_vd(dh, th), vmul_vd_vd_vd(dh, tl), vmul_vd_vd_vd(dl, th), vmul_vd_vd_vd(dl, tl))));
}

static INLINE CONST VECTOR_CC vdouble2 ddrec_vd2_vd2(vdouble2 d) {
  vdouble t = vrec_vd_vd(vd2getx_vd_vd2(d));
  vdouble dh = vupper_vd_vd(vd2getx_vd_vd2(d)), dl = vsub_vd_vd_vd(vd2getx_vd_vd2(d), dh);
  vdouble th = vupper_vd_vd(t  ), tl = vsub_vd_vd_vd(t  , th);

  return vd2setxy_vd2_vd_vd(t, vmul_vd_vd_vd(t, vsub_vd_6vd(vcast_vd_d(1), vmul_vd_vd_vd(dh, th), vmul_vd_vd_vd(dh, tl), vmul_vd_vd_vd(dl, th), vmul_vd_vd_vd(dl, tl), vmul_vd_vd_vd(vd2gety_vd_vd2(d), t))));
}
#endif // #ifdef ENABLE_FMA_DP

static INLINE CONST VECTOR_CC vdouble2 ddsqrt_vd2_vd2(vdouble2 d) {
  vdouble t = vsqrt_vd_vd(vadd_vd_vd_vd(vd2getx_vd_vd2(d), vd2gety_vd_vd2(d)));
  return ddscale_vd2_vd2_vd(ddmul_vd2_vd2_vd2(ddadd2_vd2_vd2_vd2(d, ddmul_vd2_vd_vd(t, t)), ddrec_vd2_vd(t)), vcast_vd_d(0.5));
}

static INLINE CONST VECTOR_CC vdouble2 ddsqrt_vd2_vd(vdouble d) {
  vdouble t = vsqrt_vd_vd(d);
  return ddscale_vd2_vd2_vd(ddmul_vd2_vd2_vd2(ddadd2_vd2_vd_vd2(d, ddmul_vd2_vd_vd(t, t)), ddrec_vd2_vd(t)), vcast_vd_d(0.5));
}

static INLINE CONST VECTOR_CC vdouble2 ddmla_vd2_vd2_vd2_vd2(vdouble2 x, vdouble2 y, vdouble2 z) {
  return ddadd_vd2_vd2_vd2(z, ddmul_vd2_vd2_vd2(x, y));
}
