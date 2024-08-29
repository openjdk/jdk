//   Copyright Naoki Shibata and contributors 2010 - 2024.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if !(defined(ENABLE_SVE) || defined(ENABLE_SVENOFMA) || defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA))
#if !defined(SLEEF_ENABLE_CUDA)
typedef struct {
  vfloat x, y;
} vfloat2;
#else
typedef float2 vfloat2;
#endif

static INLINE CONST VECTOR_CC vfloat  vf2getx_vf_vf2(vfloat2 v) { return v.x; }
static INLINE CONST VECTOR_CC vfloat  vf2gety_vf_vf2(vfloat2 v) { return v.y; }
static INLINE CONST VECTOR_CC vfloat2 vf2setxy_vf2_vf_vf(vfloat x, vfloat y)  { vfloat2 v; v.x = x; v.y = y; return v; }
static INLINE CONST VECTOR_CC vfloat2 vf2setx_vf2_vf2_vf(vfloat2 v, vfloat d) { v.x = d; return v; }
static INLINE CONST VECTOR_CC vfloat2 vf2sety_vf2_vf2_vf(vfloat2 v, vfloat d) { v.y = d; return v; }
#endif

static INLINE CONST VECTOR_CC vfloat vupper_vf_vf(vfloat d) {
  return vreinterpret_vf_vi2(vand_vi2_vi2_vi2(vreinterpret_vi2_vf(d), vcast_vi2_i(0xfffff000)));
}

static INLINE CONST VECTOR_CC vfloat2 vcast_vf2_vf_vf(vfloat h, vfloat l) {
  return vf2setxy_vf2_vf_vf(h, l);
}

static INLINE CONST VECTOR_CC vfloat2 vcast_vf2_f_f(float h, float l) {
  return vf2setxy_vf2_vf_vf(vcast_vf_f(h), vcast_vf_f(l));
}

static INLINE CONST VECTOR_CC vfloat2 vcast_vf2_d(double d) {
  return vf2setxy_vf2_vf_vf(vcast_vf_f(d), vcast_vf_f(d - (float)d));
}

static INLINE CONST VECTOR_CC vfloat2 vsel_vf2_vo_vf2_vf2(vopmask m, vfloat2 x, vfloat2 y) {
  return vf2setxy_vf2_vf_vf(vsel_vf_vo_vf_vf(m, vf2getx_vf_vf2(x), vf2getx_vf_vf2(y)), vsel_vf_vo_vf_vf(m, vf2gety_vf_vf2(x), vf2gety_vf_vf2(y)));
}

static INLINE CONST VECTOR_CC vfloat2 vsel_vf2_vo_f_f_f_f(vopmask o, float x1, float y1, float x0, float y0) {
  return vf2setxy_vf2_vf_vf(vsel_vf_vo_f_f(o, x1, x0), vsel_vf_vo_f_f(o, y1, y0));
}

static INLINE CONST VECTOR_CC vfloat2 vsel_vf2_vo_vo_d_d_d(vopmask o0, vopmask o1, double d0, double d1, double d2) {
  return vsel_vf2_vo_vf2_vf2(o0, vcast_vf2_d(d0), vsel_vf2_vo_vf2_vf2(o1, vcast_vf2_d(d1), vcast_vf2_d(d2)));
}

static INLINE CONST VECTOR_CC vfloat2 vsel_vf2_vo_vo_vo_d_d_d_d(vopmask o0, vopmask o1, vopmask o2, double d0, double d1, double d2, double d3) {
  return vsel_vf2_vo_vf2_vf2(o0, vcast_vf2_d(d0), vsel_vf2_vo_vf2_vf2(o1, vcast_vf2_d(d1), vsel_vf2_vo_vf2_vf2(o2, vcast_vf2_d(d2), vcast_vf2_d(d3))));
}

static INLINE CONST VECTOR_CC vfloat2 vabs_vf2_vf2(vfloat2 x) {
  return vcast_vf2_vf_vf(vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vm_vm(vreinterpret_vm_vf(vcast_vf_f(-0.0)), vreinterpret_vm_vf(vf2getx_vf_vf2(x))), vreinterpret_vm_vf(vf2getx_vf_vf2(x)))),
			 vreinterpret_vf_vm(vxor_vm_vm_vm(vand_vm_vm_vm(vreinterpret_vm_vf(vcast_vf_f(-0.0)), vreinterpret_vm_vf(vf2getx_vf_vf2(x))), vreinterpret_vm_vf(vf2gety_vf_vf2(x)))));
}

static INLINE CONST VECTOR_CC vfloat vadd_vf_3vf(vfloat v0, vfloat v1, vfloat v2) {
  return vadd_vf_vf_vf(vadd_vf_vf_vf(v0, v1), v2);
}

static INLINE CONST VECTOR_CC vfloat vadd_vf_4vf(vfloat v0, vfloat v1, vfloat v2, vfloat v3) {
  return vadd_vf_3vf(vadd_vf_vf_vf(v0, v1), v2, v3);
}

static INLINE CONST VECTOR_CC vfloat vadd_vf_5vf(vfloat v0, vfloat v1, vfloat v2, vfloat v3, vfloat v4) {
  return vadd_vf_4vf(vadd_vf_vf_vf(v0, v1), v2, v3, v4);
}

static INLINE CONST VECTOR_CC vfloat vadd_vf_6vf(vfloat v0, vfloat v1, vfloat v2, vfloat v3, vfloat v4, vfloat v5) {
  return vadd_vf_5vf(vadd_vf_vf_vf(v0, v1), v2, v3, v4, v5);
}

static INLINE CONST VECTOR_CC vfloat vadd_vf_7vf(vfloat v0, vfloat v1, vfloat v2, vfloat v3, vfloat v4, vfloat v5, vfloat v6) {
  return vadd_vf_6vf(vadd_vf_vf_vf(v0, v1), v2, v3, v4, v5, v6);
}

static INLINE CONST VECTOR_CC vfloat vsub_vf_3vf(vfloat v0, vfloat v1, vfloat v2) {
  return vsub_vf_vf_vf(vsub_vf_vf_vf(v0, v1), v2);
}

static INLINE CONST VECTOR_CC vfloat vsub_vf_4vf(vfloat v0, vfloat v1, vfloat v2, vfloat v3) {
  return vsub_vf_3vf(vsub_vf_vf_vf(v0, v1), v2, v3);
}

static INLINE CONST VECTOR_CC vfloat vsub_vf_5vf(vfloat v0, vfloat v1, vfloat v2, vfloat v3, vfloat v4) {
  return vsub_vf_4vf(vsub_vf_vf_vf(v0, v1), v2, v3, v4);
}

//

static INLINE CONST VECTOR_CC vfloat2 dfneg_vf2_vf2(vfloat2 x) {
  return vcast_vf2_vf_vf(vneg_vf_vf(vf2getx_vf_vf2(x)), vneg_vf_vf(vf2gety_vf_vf2(x)));
}

static INLINE CONST VECTOR_CC vfloat2 dfabs_vf2_vf2(vfloat2 x) {
  return vcast_vf2_vf_vf(vabs_vf_vf(vf2getx_vf_vf2(x)),
			 vreinterpret_vf_vm(vxor_vm_vm_vm(vreinterpret_vm_vf(vf2gety_vf_vf2(x)), vand_vm_vm_vm(vreinterpret_vm_vf(vf2getx_vf_vf2(x)), vreinterpret_vm_vf(vcast_vf_f(-0.0f))))));
}

static INLINE CONST VECTOR_CC vfloat2 dfnormalize_vf2_vf2(vfloat2 t) {
  vfloat s = vadd_vf_vf_vf(vf2getx_vf_vf2(t), vf2gety_vf_vf2(t));
  return vf2setxy_vf2_vf_vf(s, vadd_vf_vf_vf(vsub_vf_vf_vf(vf2getx_vf_vf2(t), s), vf2gety_vf_vf2(t)));
}

static INLINE CONST VECTOR_CC vfloat2 dfscale_vf2_vf2_vf(vfloat2 d, vfloat s) {
  return vf2setxy_vf2_vf_vf(vmul_vf_vf_vf(vf2getx_vf_vf2(d), s), vmul_vf_vf_vf(vf2gety_vf_vf2(d), s));
}

static INLINE CONST VECTOR_CC vfloat2 dfadd_vf2_vf_vf(vfloat x, vfloat y) {
  vfloat s = vadd_vf_vf_vf(x, y);
  return vf2setxy_vf2_vf_vf(s, vadd_vf_vf_vf(vsub_vf_vf_vf(x, s), y));
}

static INLINE CONST VECTOR_CC vfloat2 dfadd2_vf2_vf_vf(vfloat x, vfloat y) {
  vfloat s = vadd_vf_vf_vf(x, y);
  vfloat v = vsub_vf_vf_vf(s, x);
  return vf2setxy_vf2_vf_vf(s, vadd_vf_vf_vf(vsub_vf_vf_vf(x, vsub_vf_vf_vf(s, v)), vsub_vf_vf_vf(y, v)));
}

static INLINE CONST VECTOR_CC vfloat2 dfadd2_vf2_vf_vf2(vfloat x, vfloat2 y) {
  vfloat s = vadd_vf_vf_vf(x, vf2getx_vf_vf2(y));
  vfloat v = vsub_vf_vf_vf(s, x);
  return vf2setxy_vf2_vf_vf(s, vadd_vf_vf_vf(vadd_vf_vf_vf(vsub_vf_vf_vf(x, vsub_vf_vf_vf(s, v)), vsub_vf_vf_vf(vf2getx_vf_vf2(y), v)), vf2gety_vf_vf2(y)));

}

static INLINE CONST VECTOR_CC vfloat2 dfadd_vf2_vf2_vf(vfloat2 x, vfloat y) {
  vfloat s = vadd_vf_vf_vf(vf2getx_vf_vf2(x), y);
  return vf2setxy_vf2_vf_vf(s, vadd_vf_3vf(vsub_vf_vf_vf(vf2getx_vf_vf2(x), s), y, vf2gety_vf_vf2(x)));
}

static INLINE CONST VECTOR_CC vfloat2 dfsub_vf2_vf2_vf(vfloat2 x, vfloat y) {
  vfloat s = vsub_vf_vf_vf(vf2getx_vf_vf2(x), y);
  return vf2setxy_vf2_vf_vf(s, vadd_vf_vf_vf(vsub_vf_vf_vf(vsub_vf_vf_vf(vf2getx_vf_vf2(x), s), y), vf2gety_vf_vf2(x)));
}

static INLINE CONST VECTOR_CC vfloat2 dfadd2_vf2_vf2_vf(vfloat2 x, vfloat y) {
  vfloat s = vadd_vf_vf_vf(vf2getx_vf_vf2(x), y);
  vfloat v = vsub_vf_vf_vf(s, vf2getx_vf_vf2(x));
  vfloat t = vadd_vf_vf_vf(vsub_vf_vf_vf(vf2getx_vf_vf2(x), vsub_vf_vf_vf(s, v)), vsub_vf_vf_vf(y, v));
  return vf2setxy_vf2_vf_vf(s, vadd_vf_vf_vf(t, vf2gety_vf_vf2(x)));
}

static INLINE CONST VECTOR_CC vfloat2 dfadd_vf2_vf_vf2(vfloat x, vfloat2 y) {
  vfloat s = vadd_vf_vf_vf(x, vf2getx_vf_vf2(y));
  return vf2setxy_vf2_vf_vf(s, vadd_vf_3vf(vsub_vf_vf_vf(x, s), vf2getx_vf_vf2(y), vf2gety_vf_vf2(y)));
}

static INLINE CONST VECTOR_CC vfloat2 dfadd_vf2_vf2_vf2(vfloat2 x, vfloat2 y) {
  // |x| >= |y|

  vfloat s = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(y));
  return vf2setxy_vf2_vf_vf(s, vadd_vf_4vf(vsub_vf_vf_vf(vf2getx_vf_vf2(x), s), vf2getx_vf_vf2(y), vf2gety_vf_vf2(x), vf2gety_vf_vf2(y)));
}

static INLINE CONST VECTOR_CC vfloat2 dfadd2_vf2_vf2_vf2(vfloat2 x, vfloat2 y) {
  vfloat s = vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(y));
  vfloat v = vsub_vf_vf_vf(s, vf2getx_vf_vf2(x));
  vfloat t = vadd_vf_vf_vf(vsub_vf_vf_vf(vf2getx_vf_vf2(x), vsub_vf_vf_vf(s, v)), vsub_vf_vf_vf(vf2getx_vf_vf2(y), v));
  return vf2setxy_vf2_vf_vf(s, vadd_vf_vf_vf(t, vadd_vf_vf_vf(vf2gety_vf_vf2(x), vf2gety_vf_vf2(y))));
}

static INLINE CONST VECTOR_CC vfloat2 dfsub_vf2_vf_vf(vfloat x, vfloat y) {
  // |x| >= |y|

  vfloat s = vsub_vf_vf_vf(x, y);
  return vf2setxy_vf2_vf_vf(s, vsub_vf_vf_vf(vsub_vf_vf_vf(x, s), y));
}

static INLINE CONST VECTOR_CC vfloat2 dfsub_vf2_vf2_vf2(vfloat2 x, vfloat2 y) {
  // |x| >= |y|

  vfloat s = vsub_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(y));
  vfloat t = vsub_vf_vf_vf(vf2getx_vf_vf2(x), s);
  t = vsub_vf_vf_vf(t, vf2getx_vf_vf2(y));
  t = vadd_vf_vf_vf(t, vf2gety_vf_vf2(x));
  return vf2setxy_vf2_vf_vf(s, vsub_vf_vf_vf(t, vf2gety_vf_vf2(y)));
}

#ifdef ENABLE_FMA_SP
static INLINE CONST VECTOR_CC vfloat2 dfdiv_vf2_vf2_vf2(vfloat2 n, vfloat2 d) {
  vfloat t = vrec_vf_vf(vf2getx_vf_vf2(d));
  vfloat s = vmul_vf_vf_vf(vf2getx_vf_vf2(n), t);
  vfloat u = vfmapn_vf_vf_vf_vf(t, vf2getx_vf_vf2(n), s);
  vfloat v = vfmanp_vf_vf_vf_vf(vf2gety_vf_vf2(d), t, vfmanp_vf_vf_vf_vf(vf2getx_vf_vf2(d), t, vcast_vf_f(1)));
  return vf2setxy_vf2_vf_vf(s, vfma_vf_vf_vf_vf(s, v, vfma_vf_vf_vf_vf(vf2gety_vf_vf2(n), t, u)));
}

static INLINE CONST VECTOR_CC vfloat2 dfmul_vf2_vf_vf(vfloat x, vfloat y) {
  vfloat s = vmul_vf_vf_vf(x, y);
  return vf2setxy_vf2_vf_vf(s, vfmapn_vf_vf_vf_vf(x, y, s));
}

static INLINE CONST VECTOR_CC vfloat2 dfsqu_vf2_vf2(vfloat2 x) {
  vfloat s = vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(x));
  return vf2setxy_vf2_vf_vf(s, vfma_vf_vf_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(x)), vf2gety_vf_vf2(x), vfmapn_vf_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(x), s)));
}

static INLINE CONST VECTOR_CC vfloat dfsqu_vf_vf2(vfloat2 x) {
  return vfma_vf_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(x), vadd_vf_vf_vf(vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x)), vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(x))));
}

static INLINE CONST VECTOR_CC vfloat2 dfmul_vf2_vf2_vf2(vfloat2 x, vfloat2 y) {
  vfloat s = vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(y));
  return vf2setxy_vf2_vf_vf(s, vfma_vf_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(y), vfma_vf_vf_vf_vf(vf2gety_vf_vf2(x), vf2getx_vf_vf2(y), vfmapn_vf_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(y), s))));
}

static INLINE CONST VECTOR_CC vfloat dfmul_vf_vf2_vf2(vfloat2 x, vfloat2 y) {
  return vfma_vf_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(y), vfma_vf_vf_vf_vf(vf2gety_vf_vf2(x), vf2getx_vf_vf2(y), vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(y))));
}

static INLINE CONST VECTOR_CC vfloat2 dfmul_vf2_vf2_vf(vfloat2 x, vfloat y) {
  vfloat s = vmul_vf_vf_vf(vf2getx_vf_vf2(x), y);
  return vf2setxy_vf2_vf_vf(s, vfma_vf_vf_vf_vf(vf2gety_vf_vf2(x), y, vfmapn_vf_vf_vf_vf(vf2getx_vf_vf2(x), y, s)));
}

static INLINE CONST VECTOR_CC vfloat2 dfrec_vf2_vf(vfloat d) {
  vfloat s = vrec_vf_vf(d);
  return vf2setxy_vf2_vf_vf(s, vmul_vf_vf_vf(s, vfmanp_vf_vf_vf_vf(d, s, vcast_vf_f(1))));
}

static INLINE CONST VECTOR_CC vfloat2 dfrec_vf2_vf2(vfloat2 d) {
  vfloat s = vrec_vf_vf(vf2getx_vf_vf2(d));
  return vf2setxy_vf2_vf_vf(s, vmul_vf_vf_vf(s, vfmanp_vf_vf_vf_vf(vf2gety_vf_vf2(d), s, vfmanp_vf_vf_vf_vf(vf2getx_vf_vf2(d), s, vcast_vf_f(1)))));
}
#else
static INLINE CONST VECTOR_CC vfloat2 dfdiv_vf2_vf2_vf2(vfloat2 n, vfloat2 d) {
  vfloat t = vrec_vf_vf(vf2getx_vf_vf2(d));
  vfloat dh  = vupper_vf_vf(vf2getx_vf_vf2(d)), dl  = vsub_vf_vf_vf(vf2getx_vf_vf2(d),  dh);
  vfloat th  = vupper_vf_vf(t  ), tl  = vsub_vf_vf_vf(t  ,  th);
  vfloat nhh = vupper_vf_vf(vf2getx_vf_vf2(n)), nhl = vsub_vf_vf_vf(vf2getx_vf_vf2(n), nhh);

  vfloat s = vmul_vf_vf_vf(vf2getx_vf_vf2(n), t);

  vfloat u, w;
  w = vcast_vf_f(-1);
  w = vmla_vf_vf_vf_vf(dh, th, w);
  w = vmla_vf_vf_vf_vf(dh, tl, w);
  w = vmla_vf_vf_vf_vf(dl, th, w);
  w = vmla_vf_vf_vf_vf(dl, tl, w);
  w = vneg_vf_vf(w);

  u = vmla_vf_vf_vf_vf(nhh, th, vneg_vf_vf(s));
  u = vmla_vf_vf_vf_vf(nhh, tl, u);
  u = vmla_vf_vf_vf_vf(nhl, th, u);
  u = vmla_vf_vf_vf_vf(nhl, tl, u);
  u = vmla_vf_vf_vf_vf(s, w, u);

  return vf2setxy_vf2_vf_vf(s, vmla_vf_vf_vf_vf(t, vsub_vf_vf_vf(vf2gety_vf_vf2(n), vmul_vf_vf_vf(s, vf2gety_vf_vf2(d))), u));
}

static INLINE CONST VECTOR_CC vfloat2 dfmul_vf2_vf_vf(vfloat x, vfloat y) {
  vfloat xh = vupper_vf_vf(x), xl = vsub_vf_vf_vf(x, xh);
  vfloat yh = vupper_vf_vf(y), yl = vsub_vf_vf_vf(y, yh);

  vfloat s = vmul_vf_vf_vf(x, y), t;

  t = vmla_vf_vf_vf_vf(xh, yh, vneg_vf_vf(s));
  t = vmla_vf_vf_vf_vf(xl, yh, t);
  t = vmla_vf_vf_vf_vf(xh, yl, t);
  t = vmla_vf_vf_vf_vf(xl, yl, t);

  return vf2setxy_vf2_vf_vf(s, t);
}

static INLINE CONST VECTOR_CC vfloat2 dfmul_vf2_vf2_vf(vfloat2 x, vfloat y) {
  vfloat xh = vupper_vf_vf(vf2getx_vf_vf2(x)), xl = vsub_vf_vf_vf(vf2getx_vf_vf2(x), xh);
  vfloat yh = vupper_vf_vf(y  ), yl = vsub_vf_vf_vf(y  , yh);

  vfloat s = vmul_vf_vf_vf(vf2getx_vf_vf2(x), y), t;

  t = vmla_vf_vf_vf_vf(xh, yh, vneg_vf_vf(s));
  t = vmla_vf_vf_vf_vf(xl, yh, t);
  t = vmla_vf_vf_vf_vf(xh, yl, t);
  t = vmla_vf_vf_vf_vf(xl, yl, t);
  t = vmla_vf_vf_vf_vf(vf2gety_vf_vf2(x), y, t);

  return vf2setxy_vf2_vf_vf(s, t);
}

static INLINE CONST VECTOR_CC vfloat2 dfmul_vf2_vf2_vf2(vfloat2 x, vfloat2 y) {
  vfloat xh = vupper_vf_vf(vf2getx_vf_vf2(x)), xl = vsub_vf_vf_vf(vf2getx_vf_vf2(x), xh);
  vfloat yh = vupper_vf_vf(vf2getx_vf_vf2(y)), yl = vsub_vf_vf_vf(vf2getx_vf_vf2(y), yh);

  vfloat s = vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(y)), t;

  t = vmla_vf_vf_vf_vf(xh, yh, vneg_vf_vf(s));
  t = vmla_vf_vf_vf_vf(xl, yh, t);
  t = vmla_vf_vf_vf_vf(xh, yl, t);
  t = vmla_vf_vf_vf_vf(xl, yl, t);
  t = vmla_vf_vf_vf_vf(vf2getx_vf_vf2(x), vf2gety_vf_vf2(y), t);
  t = vmla_vf_vf_vf_vf(vf2gety_vf_vf2(x), vf2getx_vf_vf2(y), t);

  return vf2setxy_vf2_vf_vf(s, t);
}

static INLINE CONST VECTOR_CC vfloat dfmul_vf_vf2_vf2(vfloat2 x, vfloat2 y) {
  vfloat xh = vupper_vf_vf(vf2getx_vf_vf2(x)), xl = vsub_vf_vf_vf(vf2getx_vf_vf2(x), xh);
  vfloat yh = vupper_vf_vf(vf2getx_vf_vf2(y)), yl = vsub_vf_vf_vf(vf2getx_vf_vf2(y), yh);

  return vadd_vf_6vf(vmul_vf_vf_vf(vf2gety_vf_vf2(x), yh), vmul_vf_vf_vf(xh, vf2gety_vf_vf2(y)), vmul_vf_vf_vf(xl, yl), vmul_vf_vf_vf(xh, yl), vmul_vf_vf_vf(xl, yh), vmul_vf_vf_vf(xh, yh));
}

static INLINE CONST VECTOR_CC vfloat2 dfsqu_vf2_vf2(vfloat2 x) {
  vfloat xh = vupper_vf_vf(vf2getx_vf_vf2(x)), xl = vsub_vf_vf_vf(vf2getx_vf_vf2(x), xh);

  vfloat s = vmul_vf_vf_vf(vf2getx_vf_vf2(x), vf2getx_vf_vf2(x)), t;

  t = vmla_vf_vf_vf_vf(xh, xh, vneg_vf_vf(s));
  t = vmla_vf_vf_vf_vf(vadd_vf_vf_vf(xh, xh), xl, t);
  t = vmla_vf_vf_vf_vf(xl, xl, t);
  t = vmla_vf_vf_vf_vf(vf2getx_vf_vf2(x), vadd_vf_vf_vf(vf2gety_vf_vf2(x), vf2gety_vf_vf2(x)), t);

  return vf2setxy_vf2_vf_vf(s, t);
}

static INLINE CONST VECTOR_CC vfloat dfsqu_vf_vf2(vfloat2 x) {
  vfloat xh = vupper_vf_vf(vf2getx_vf_vf2(x)), xl = vsub_vf_vf_vf(vf2getx_vf_vf2(x), xh);

  return vadd_vf_5vf(vmul_vf_vf_vf(xh, vf2gety_vf_vf2(x)), vmul_vf_vf_vf(xh, vf2gety_vf_vf2(x)), vmul_vf_vf_vf(xl, xl), vadd_vf_vf_vf(vmul_vf_vf_vf(xh, xl), vmul_vf_vf_vf(xh, xl)), vmul_vf_vf_vf(xh, xh));
}

static INLINE CONST VECTOR_CC vfloat2 dfrec_vf2_vf(vfloat d) {
  vfloat t = vrec_vf_vf(d);
  vfloat dh = vupper_vf_vf(d), dl = vsub_vf_vf_vf(d, dh);
  vfloat th = vupper_vf_vf(t), tl = vsub_vf_vf_vf(t, th);

  vfloat u = vcast_vf_f(-1);
  u = vmla_vf_vf_vf_vf(dh, th, u);
  u = vmla_vf_vf_vf_vf(dh, tl, u);
  u = vmla_vf_vf_vf_vf(dl, th, u);
  u = vmla_vf_vf_vf_vf(dl, tl, u);

  return vf2setxy_vf2_vf_vf(t, vmul_vf_vf_vf(vneg_vf_vf(t), u));
}

static INLINE CONST VECTOR_CC vfloat2 dfrec_vf2_vf2(vfloat2 d) {
  vfloat t = vrec_vf_vf(vf2getx_vf_vf2(d));
  vfloat dh = vupper_vf_vf(vf2getx_vf_vf2(d)), dl = vsub_vf_vf_vf(vf2getx_vf_vf2(d), dh);
  vfloat th = vupper_vf_vf(t  ), tl = vsub_vf_vf_vf(t  , th);

  vfloat u = vcast_vf_f(-1);
  u = vmla_vf_vf_vf_vf(dh, th, u);
  u = vmla_vf_vf_vf_vf(dh, tl, u);
  u = vmla_vf_vf_vf_vf(dl, th, u);
  u = vmla_vf_vf_vf_vf(dl, tl, u);
  u = vmla_vf_vf_vf_vf(vf2gety_vf_vf2(d), t, u);

  return vf2setxy_vf2_vf_vf(t, vmul_vf_vf_vf(vneg_vf_vf(t), u));
}
#endif

static INLINE CONST VECTOR_CC vfloat2 dfsqrt_vf2_vf2(vfloat2 d) {
#ifdef ENABLE_RECSQRT_SP
  vfloat x = vrecsqrt_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d)));
  vfloat2 r = dfmul_vf2_vf2_vf(d, x);
  return dfscale_vf2_vf2_vf(dfmul_vf2_vf2_vf2(r, dfadd2_vf2_vf2_vf(dfmul_vf2_vf2_vf(r, x), vcast_vf_f(-3.0))), vcast_vf_f(-0.5));
#else
  vfloat t = vsqrt_vf_vf(vadd_vf_vf_vf(vf2getx_vf_vf2(d), vf2gety_vf_vf2(d)));
  return dfscale_vf2_vf2_vf(dfmul_vf2_vf2_vf2(dfadd2_vf2_vf2_vf2(d, dfmul_vf2_vf_vf(t, t)), dfrec_vf2_vf(t)), vcast_vf_f(0.5));
#endif
}

static INLINE CONST VECTOR_CC vfloat2 dfsqrt_vf2_vf(vfloat d) {
  vfloat t = vsqrt_vf_vf(d);
  return dfscale_vf2_vf2_vf(dfmul_vf2_vf2_vf2(dfadd2_vf2_vf_vf2(d, dfmul_vf2_vf_vf(t, t)), dfrec_vf2_vf(t)), vcast_vf_f(0.5f));
}
