//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if CONFIG == 1 || CONFIG == 2 || CONFIG == 3 || CONFIG == 4

#ifndef __VSX__
#error Please specify -mcpu=power8 or -mcpu=power9
#endif

#else
#error CONFIG macro invalid or not defined
#endif

#define ENABLE_DP
//@#define ENABLE_DP
#define LOG2VECTLENDP 1
//@#define LOG2VECTLENDP 1
#define VECTLENDP (1 << LOG2VECTLENDP)
//@#define VECTLENDP (1 << LOG2VECTLENDP)

#define ENABLE_SP
//@#define ENABLE_SP
#define LOG2VECTLENSP (LOG2VECTLENDP+1)
//@#define LOG2VECTLENSP (LOG2VECTLENDP+1)
#define VECTLENSP (1 << LOG2VECTLENSP)
//@#define VECTLENSP (1 << LOG2VECTLENSP)

#if CONFIG == 1 || CONFIG == 3
#define ENABLE_FMA_DP
//@#define ENABLE_FMA_DP
#define ENABLE_FMA_SP
//@#define ENABLE_FMA_SP
#endif

#define ACCURATE_SQRT
//@#define ACCURATE_SQRT
#define FULL_FP_ROUNDING
//@#define FULL_FP_ROUNDING

#if !defined(SLEEF_GENHEADER)
#include <altivec.h>
// undef altivec types since CPP and C99 use them as compiler tokens
// use __vector and __bool instead
#undef vector
#undef bool

#include <stdint.h>
#include "misc.h"
#endif // #if !defined(SLEEF_GENHEADER)

#if CONFIG == 1 || CONFIG == 2
#define ISANAME "VSX"
#else
#define ISANAME "VSX-3"
#endif

#define DFTPRIORITY 25

static INLINE int vavailability_i(int name) { return 3; }
static INLINE void vprefetch_v_p(const void *ptr) { }

/**********************************************
 ** Types
***********************************************/
typedef __vector unsigned int vmask;
// using __bool with typedef may cause ambiguous errors
#define vopmask __vector __bool int
//@#define vopmask __vector __bool int
typedef __vector signed int vint;
typedef __vector signed int vint2;
typedef __vector float  vfloat;
typedef __vector double vdouble;

// internal use types
typedef __vector unsigned int v__u32;
typedef __vector unsigned char v__u8;
typedef __vector signed long long  v__i64;
typedef __vector unsigned long long  v__u64;
#define v__b64 __vector __bool long long

typedef __vector long long vint64;
typedef __vector unsigned long long vuint64;

typedef struct {
  vmask x, y;
} vquad;

typedef vquad vargquad;

/**********************************************
 ** Utilities
***********************************************/
#define vset__vi(v0, v1) ((vint) {v0, v1, v0, v1})
#define vset__vi2(...) ((vint2) {__VA_ARGS__})
#define vset__vm(...) ((vmask) {__VA_ARGS__})
#define vset__vo(...) ((vopmask) {__VA_ARGS__})
#define vset__vf(...) ((vfloat) {__VA_ARGS__})
#define vset__vd(...) ((vdouble) {__VA_ARGS__})
#define vset__u8(...) ((v__u8) {__VA_ARGS__})
#define vset__u32(...) ((v__u32) {__VA_ARGS__})
#define vset__s64(...) ((v__i64) {__VA_ARGS__})
#define vset__u64(...) ((v__u64) {__VA_ARGS__})

#define vsetall__vi(v)  vset__vi(v, v)
#define vsetall__vi2(v) vset__vi2(v, v, v, v)
#define vsetall__vm(v)  vset__vm(v, v, v, v)
#define vsetall__vo(v)  vset__vo(v, v, v, v)
#define vsetall__vf(v)  vset__vf(v, v, v, v)
#define vsetall__vd(v)  vset__vd(v, v)
#define vsetall__u8(v)  vset__u8(v, v, v, v, v, v, v, v, v, v, v, v, v, v, v, v)
#define vsetall__u32(v) vset__u32(v, v, v, v)
#define vsetall__s64(v) vset__s64(v, v)
#define vsetall__u64(v) vset__u64(v, v)

#define vzero__vi()  vsetall__vi(0)
#define vzero__vi2() vsetall__vi2(0)
#define vzero__vm()  vsetall__vm(0)
#define vzero__vo()  vsetall__vo(0)
#define vzero__vf()  vsetall__vf(0)
#define vzero__vd()  vsetall__vd(0)
#define vzero__u8()  vsetall__u8(0)
#define vzero__u32() vsetall__u32(0)
#define vzero__s64() vsetall__s64(0)
#define vzero__u64() vsetall__u64(0)

//// Swap doubleword elements
#if defined(__clang__) || __GNUC__ >= 7
  static INLINE v__u64 v__swapd_u64(v__u64 v)
  { return vec_xxpermdi(v, v, 2); }
#else
  static INLINE v__u64 v__swapd_u64(v__u64 v)
  {
    __asm__ __volatile__("xxswapd %x0,%x1" : "=wa" (v) : "wa" (v));
    return v;
  }
#endif

/**********************************************
 ** Memory
***********************************************/

////////////// Unaligned memory access //////////////
/**
 * It's not safe to use vector assignment via (cast & dereference) for unaligned memory access
 * with almost all clang versions and gcc8 when VSX3 isn't enabled,
 * these compilers tends to generate instructions 'lvx/stvx' instead of 'lxvd2x/lxvw4x/stxvd2x/stxvw4x'
 * for more information check https://github.com/seiko2plus/vsx_mem_test
 *
 * TODO: check GCC(9, 10)
*/
//// load
#if defined(__POWER9_VECTOR__) || (!defined(__clang__) && defined(__GNUC__) && __GNUC__ < 8)
static vint vloadu_vi_p(const int32_t *ptr)
{ return *((vint*)ptr); }
static INLINE vint2 vloadu_vi2_p(const int32_t *ptr)
{ return *((vint2*)ptr); }
static INLINE vfloat vloadu_vf_p(const float *ptr)
{ return *((vfloat*)ptr); }
static INLINE vdouble vloadu_vd_p(const double *ptr)
{ return *((vdouble*)ptr); }
#else
static vint vloadu_vi_p(const int32_t *ptr)
{ return vec_vsx_ld(0, ptr); }
static INLINE vint2 vloadu_vi2_p(const int32_t *ptr)
{ return vec_vsx_ld(0, ptr); }
static INLINE vfloat vloadu_vf_p(const float *ptr)
{ return vec_vsx_ld(0, ptr); }
static INLINE vdouble vloadu_vd_p(const double *ptr)
{ return vec_vsx_ld(0, ptr); }
#endif

//// store
#if defined(__POWER9_VECTOR__) || (!defined(__clang__) && defined(__GNUC__) && __GNUC__ < 8)
static void vstoreu_v_p_vi(int32_t *ptr, vint v)
{ *((vint*)ptr) = v; }
static void vstoreu_v_p_vi2(int32_t *ptr, vint2 v)
{ *((vint2*)ptr) = v; }
static INLINE void vstoreu_v_p_vf(float *ptr, vfloat v)
{ *((vfloat*)ptr) = v; }
static INLINE void vstoreu_v_p_vd(double *ptr, vdouble v)
{ *((vdouble*)ptr) = v; }
#else
static void vstoreu_v_p_vi(int32_t *ptr, vint v)
{ vec_vsx_st(v, 0, ptr); }
static void vstoreu_v_p_vi2(int32_t *ptr, vint2 v)
{ vec_vsx_st(v, 0, ptr); }
static INLINE void vstoreu_v_p_vf(float *ptr, vfloat v)
{ vec_vsx_st(v, 0, ptr); }
static INLINE void vstoreu_v_p_vd(double *ptr, vdouble v)
{ vec_vsx_st(v, 0, ptr); }
#endif

////////////// aligned memory access //////////////
//// load
static INLINE vfloat vload_vf_p(const float *ptr)
{ return vec_ld(0, ptr); }
static INLINE vdouble vload_vd_p(const double *ptr)
{ return *((vdouble*)ptr); }

//// store
static INLINE void vstore_v_p_vf(float *ptr, vfloat v)
{ vec_st(v, 0, ptr); }
static INLINE void vstore_v_p_vd(double *ptr, vdouble v)
{ *((vdouble*)ptr) = v; }

////////////// non-temporal memory access //////////////
//// store
static INLINE void vstream_v_p_vf(float *ptr, vfloat v)
{ vstore_v_p_vf(ptr, v); }
static INLINE void vstream_v_p_vd(double *ptr, vdouble v)
{ vstore_v_p_vd(ptr, v); }

////////////// LUT //////////////
//// load
static INLINE vdouble vgather_vd_p_vi(const double *ptr, vint vi)
{ return vset__vd(ptr[vec_extract(vi, 0)], ptr[vec_extract(vi, 1)]); }

static INLINE vfloat vgather_vf_p_vi2(const float *ptr, vint2 vi2)
{
  return vset__vf(
    ptr[vec_extract(vi2, 0)], ptr[vec_extract(vi2, 1)],
    ptr[vec_extract(vi2, 2)], ptr[vec_extract(vi2, 3)]
  );
}

//// store
static INLINE void vscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v)
{
  const v__u64 vll = (v__u64)v;
  float *ptr_low = ptr + offset*2;
  float *ptr_high = ptr + (offset + step)*2;
  *((uint64_t*)ptr_low) = vec_extract(vll, 0);
  *((uint64_t*)ptr_high) = vec_extract(vll, 1);
}

static INLINE void vsscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v)
{ vscatter2_v_p_i_i_vf(ptr, offset, step, v); }

static INLINE void vscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v)
{ vstore_v_p_vd((double *)(&ptr[2*offset]), v); }

static INLINE void vsscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v)
{ vscatter2_v_p_i_i_vd(ptr, offset, step, v); }

/**********************************************
 ** Misc
 **********************************************/

// vector with a specific value set to all lanes (Vector Splat)
static INLINE vint vcast_vi_i(int i)
{ return vsetall__vi(i); }
static INLINE vint2 vcast_vi2_i(int i)
{ return vsetall__vi2(i); }
static INLINE vfloat vcast_vf_f(float f)
{ return vsetall__vf(f); }
static INLINE vdouble vcast_vd_d(double d)
{ return vsetall__vd(d); }
// cast
static INLINE vint2 vcast_vi2_vm(vmask vm)
{ return (vint2)vm; }
static INLINE vmask vcast_vm_vi2(vint2 vi)
{ return (vmask)vi; }
// get the first element
static INLINE float vcast_f_vf(vfloat v)
{ return vec_extract(v, 0); }
static INLINE double vcast_d_vd(vdouble v)
{ return vec_extract(v, 0); }

static INLINE vmask vreinterpret_vm_vd(vdouble vd)
{ return (vmask)vd; }
static INLINE vdouble vreinterpret_vd_vm(vmask vm)
{ return (vdouble)vm; }

static INLINE vmask vreinterpret_vm_vf(vfloat vf)
{ return (vmask)vf; }
static INLINE vfloat vreinterpret_vf_vm(vmask vm)
{ return (vfloat)vm; }
static INLINE vfloat vreinterpret_vf_vi2(vint2 vi)
{ return (vfloat)vi; }
static INLINE vint2 vreinterpret_vi2_vf(vfloat vf)
{ return (vint2)vf; }

// per element select via mask (blend)
static INLINE vdouble vsel_vd_vo_vd_vd(vopmask o, vdouble x, vdouble y)
{ return vec_sel(y, x, (v__b64)o); }
static INLINE vfloat vsel_vf_vo_vf_vf(vopmask o, vfloat x, vfloat y)
{ return vec_sel(y, x, o); }

static INLINE vint vsel_vi_vo_vi_vi(vopmask o, vint x, vint y)
{ return vec_sel(y, x, o); }

static INLINE vint2 vsel_vi2_vo_vi2_vi2(vopmask o, vint2 x, vint2 y)
{ return vec_sel(y, x, o); }

static INLINE vfloat vsel_vf_vo_f_f(vopmask o, float v1, float v0)
{
  return vsel_vf_vo_vf_vf(o, vsetall__vf(v1), vsetall__vf(v0));
}
static INLINE vfloat vsel_vf_vo_vo_f_f_f(vopmask o0, vopmask o1, float d0, float d1, float d2)
{
  return vsel_vf_vo_vf_vf(o0, vsetall__vf(d0), vsel_vf_vo_f_f(o1, d1, d2));
}
static INLINE vfloat vsel_vf_vo_vo_vo_f_f_f_f(vopmask o0, vopmask o1, vopmask o2, float d0, float d1, float d2, float d3)
{
  return vsel_vf_vo_vf_vf(o0, vsetall__vf(d0), vsel_vf_vo_vf_vf(o1, vsetall__vf(d1), vsel_vf_vo_f_f(o2, d2, d3)));
}

static INLINE vdouble vsel_vd_vo_d_d(vopmask o, double v1, double v0)
{
  return vsel_vd_vo_vd_vd(o, vsetall__vd(v1), vsetall__vd(v0));
}
static INLINE vdouble vsel_vd_vo_vo_d_d_d(vopmask o0, vopmask o1, double d0, double d1, double d2)
{
  return vsel_vd_vo_vd_vd(o0, vsetall__vd(d0), vsel_vd_vo_d_d(o1, d1, d2));
}
static INLINE vdouble vsel_vd_vo_vo_vo_d_d_d_d(vopmask o0, vopmask o1, vopmask o2, double d0, double d1, double d2, double d3)
{
  return vsel_vd_vo_vd_vd(o0, vsetall__vd(d0), vsel_vd_vo_vd_vd(o1, vsetall__vd(d1), vsel_vd_vo_d_d(o2, d2, d3)));
}

static INLINE int vtestallones_i_vo32(vopmask g)
{ return vec_all_ne((vint2)g, vzero__vi2()); }
static INLINE int vtestallones_i_vo64(vopmask g)
{ return vec_all_ne((v__i64)g, vzero__s64()); }

/**********************************************
 ** Conversions
 **********************************************/

////////////// Numeric //////////////
// pack 64-bit mask to 32-bit
static INLINE vopmask vcast_vo32_vo64(vopmask m)
{ return (vopmask)vec_pack((v__u64)m, (v__u64)m); }
// clip 64-bit lanes to lower 32-bit
static INLINE vint vcastu_vi_vi2(vint2 vi2)
{ return vec_mergeo(vi2, vec_splat(vi2, 3)); }
static INLINE vint vcastu_vi_vm(vmask vi2)
{ return vec_mergeo((vint2)vi2, vec_splat((vint2)vi2, 3)); }


// expand lower 32-bit mask
static INLINE vopmask vcast_vo64_vo32(vopmask m)
{ return vec_mergeh(m, m); }
// unsigned expand lower 32-bit integer
static INLINE vint2 vcastu_vi2_vi(vint vi)
{ return vec_mergeh(vzero__vi(), vi); }
static INLINE vmask vcastu_vm_vi(vint vi)
{ return (vmask)vec_mergeh(vzero__vi(), vi); }

static INLINE vopmask vcast_vo_i(int i) {
  i = i ? -1 : 0;
  return (vopmask) { i, i, i, i };
}

// signed int to single-precision
static INLINE vfloat vcast_vf_vi2(vint2 vi)
{
  vfloat ret;
#if defined(__clang__) || __GNUC__ >= 9
  ret = __builtin_convertvector(vi, vfloat);
#else
  __asm__ __volatile__("xvcvsxwsp %x0,%x1" : "=wa" (ret) : "wa" (vi));
#endif
  return ret;
}

// lower signed int to double-precision
static INLINE vdouble vcast_vd_vi(vint vi)
{
  vdouble ret;
  vint swap = vec_mergeh(vi, vi);
#if defined(__clang__) || __GNUC__ >= 7
  ret = __builtin_vsx_xvcvsxwdp(swap);
#else
  __asm__ __volatile__("xvcvsxwdp %x0,%x1" : "=wa" (ret) : "wa" (swap));
#endif
  return ret;
}

// zip two scalars
static INLINE vmask vcast_vm_i_i(int l, int h)
{ return (vmask)vec_mergeh(vsetall__vi2(h), vsetall__vi2(l)); }

static INLINE vmask vcast_vm_i64(int64_t i) {
  return (vmask)vsetall__s64(i);
}
static INLINE vmask vcast_vm_u64(uint64_t i) {
  return (vmask)vsetall__u64(i);
}

////////////// Truncation //////////////

static INLINE vint2 vtruncate_vi2_vf(vfloat vf)
{
  vint2 ret;
#if defined(__clang__) || __GNUC__ >= 9
  ret = __builtin_convertvector(vf, vint2);
#else
  __asm__ __volatile__("xvcvspsxws %x0,%x1" : "=wa" (ret) : "wa" (vf));
#endif
  return ret;
}

static INLINE vint vtruncate_vi_vd(vdouble vd)
{
  vint ret;
#if defined(__clang__) || __GNUC__ >= 7
  ret = __builtin_vsx_xvcvdpsxws(vd);
#else
  __asm__ __volatile__("xvcvdpsxws %x0,%x1" : "=wa" (ret) : "wa" (vd));
#endif
  return vec_mergeo(ret, vec_splat(ret, 3));
}

static INLINE vdouble vtruncate_vd_vd(vdouble vd)
{ return vec_trunc(vd); }
static INLINE vfloat vtruncate_vf_vf(vfloat vf)
{ return vec_trunc(vf); }

////////////// Rounding //////////////

// towards the nearest even
static INLINE vint vrint_vi_vd(vdouble vd)
{ return vtruncate_vi_vd(vec_rint(vd)); }
static INLINE vint2 vrint_vi2_vf(vfloat vf)
{ return vtruncate_vi2_vf(vec_rint(vf)); }
static INLINE vdouble vrint_vd_vd(vdouble vd)
{ return vec_rint(vd); }
static INLINE vfloat vrint_vf_vf(vfloat vf)
{ return vec_rint(vf); }

/**********************************************
 ** Logical
 **********************************************/

////////////// And //////////////
static INLINE vint vand_vi_vi_vi(vint x, vint y)
{ return vec_and(x, y); }
static INLINE vint vand_vi_vo_vi(vopmask x, vint y)
{ return vec_and((vint)x, y); }
static INLINE vint2 vand_vi2_vi2_vi2(vint2 x, vint2 y)
{ return vec_and(x, y); }
static INLINE vint2 vand_vi2_vo_vi2(vopmask x, vint2 y)
{ return (vint2)vec_and((vint2)x, y); }

static INLINE vmask vand_vm_vm_vm(vmask x, vmask y)
{ return vec_and(x, y); }
static INLINE vmask vand_vm_vo32_vm(vopmask x, vmask y)
{ return vec_and((vmask)x, y); }
static INLINE vmask vand_vm_vo64_vm(vopmask x, vmask y)
{ return vec_and((vmask)x, y); }
static INLINE vopmask vand_vo_vo_vo(vopmask x, vopmask y)
{ return vec_and(x, y); }

////////////// Or //////////////
static INLINE vint vor_vi_vi_vi(vint x, vint y)
{ return vec_or(x, y); }
static INLINE vint2 vor_vi2_vi2_vi2(vint2 x, vint2 y)
{ return vec_or(x, y); }

static INLINE vmask vor_vm_vm_vm(vmask x, vmask y)
{ return vec_or(x, y); }
static INLINE vmask vor_vm_vo32_vm(vopmask x, vmask y)
{ return vec_or((vmask)x, y); }
static INLINE vmask vor_vm_vo64_vm(vopmask x, vmask y)
{ return vec_or((vmask)x, y); }
static INLINE vopmask vor_vo_vo_vo(vopmask x, vopmask y)
{ return vec_or(x, y); }

////////////// Xor //////////////
static INLINE vint vxor_vi_vi_vi(vint x, vint y)
{ return vec_xor(x, y); }
static INLINE vint2 vxor_vi2_vi2_vi2(vint2 x, vint2 y)
{ return vec_xor(x, y); }

static INLINE vmask vxor_vm_vm_vm(vmask x, vmask y)
{ return vec_xor(x, y); }
static INLINE vmask vxor_vm_vo32_vm(vopmask x, vmask y)
{ return vec_xor((vmask)x, y); }
static INLINE vmask vxor_vm_vo64_vm(vopmask x, vmask y)
{ return vec_xor((vmask)x, y); }
static INLINE vopmask vxor_vo_vo_vo(vopmask x, vopmask y)
{ return vec_xor(x, y); }

////////////// Not //////////////
static INLINE vopmask vnot_vo_vo(vopmask o)
{ return vec_nor(o, o); }

////////////// And Not ((~x) & y) //////////////
static INLINE vint vandnot_vi_vi_vi(vint x, vint y)
{ return vec_andc(y, x); }
static INLINE vint vandnot_vi_vo_vi(vopmask x, vint y)
{ return vec_andc(y, (vint)x); }
static INLINE vint2 vandnot_vi2_vi2_vi2(vint2 x, vint2 y)
{ return vec_andc(y, x); }
static INLINE vmask vandnot_vm_vm_vm(vmask x, vmask y)
{ return vec_andc(y, x); }
static INLINE vmask vandnot_vm_vo64_vm(vopmask x, vmask y)
{ return vec_andc(y, x); }
static INLINE vmask vandnot_vm_vo32_vm(vopmask x, vmask y)
{ return vec_andc(y, x); }
static INLINE vopmask vandnot_vo_vo_vo(vopmask x, vopmask y)
{ return vec_andc(y, x); }
static INLINE vint2 vandnot_vi2_vo_vi2(vopmask x, vint2 y)
{ return vec_andc(y, (vint2)x); }

/**********************************************
 ** Comparison
 **********************************************/

////////////// Equal //////////////
static INLINE vint veq_vi_vi_vi(vint x, vint y)
{ return (vint)vec_cmpeq(x, y); }
static INLINE vopmask veq_vo_vi_vi(vint x, vint y)
{ return vec_cmpeq(x, y); }

static INLINE vopmask veq_vo_vi2_vi2(vint2 x, vint2 y)
{ return vec_cmpeq(x, y); }
static INLINE vint2 veq_vi2_vi2_vi2(vint2 x, vint2 y)
{ return (vint2)vec_cmpeq(x, y); }

static INLINE vopmask veq64_vo_vm_vm(vmask x, vmask y)
{ return (vopmask)vec_cmpeq((v__u64)x, (v__u64)y); }

static INLINE vopmask veq_vo_vf_vf(vfloat x, vfloat y)
{ return vec_cmpeq(x, y); }
static INLINE vopmask veq_vo_vd_vd(vdouble x, vdouble y)
{ return (vopmask)vec_cmpeq(x, y); }

////////////// Not Equal //////////////
static INLINE vopmask vneq_vo_vf_vf(vfloat x, vfloat y)
{ return vnot_vo_vo(vec_cmpeq(x, y)); }
static INLINE vopmask vneq_vo_vd_vd(vdouble x, vdouble y)
{ return vnot_vo_vo((vopmask)vec_cmpeq(x, y)); }

////////////// Less Than //////////////
static INLINE vopmask vlt_vo_vf_vf(vfloat x, vfloat y)
{ return vec_cmplt(x, y); }
static INLINE vopmask vlt_vo_vd_vd(vdouble x, vdouble y)
{ return (vopmask)vec_cmplt(x, y); }

////////////// Greater Than //////////////
static INLINE vint vgt_vi_vi_vi(vint x, vint y)
{ return (vint)vec_cmpgt(x, y); }
static INLINE vopmask vgt_vo_vi_vi(vint x, vint y)
{ return vec_cmpgt(x, y);}

static INLINE vint2 vgt_vi2_vi2_vi2(vint2 x, vint2 y)
{ return (vint2)vec_cmpgt(x, y); }
static INLINE vopmask vgt_vo_vi2_vi2(vint2 x, vint2 y)
{ return vec_cmpgt(x, y); }

static INLINE vopmask vgt_vo_vf_vf(vfloat x, vfloat y)
{ return vec_cmpgt(x, y); }
static INLINE vopmask vgt_vo_vd_vd(vdouble x, vdouble y)
{ return (vopmask)vec_cmpgt(x, y); }

////////////// Less Than Or Equal //////////////
static INLINE vopmask vle_vo_vf_vf(vfloat x, vfloat y)
{ return vec_cmple(x, y); }
static INLINE vopmask vle_vo_vd_vd(vdouble x, vdouble y)
{ return (vopmask)vec_cmple(x, y); }

////////////// Greater Than Or Equal //////////////
static INLINE vopmask vge_vo_vf_vf(vfloat x, vfloat y)
{ return vec_cmpge(x, y); }
static INLINE vopmask vge_vo_vd_vd(vdouble x, vdouble y)
{ return (vopmask)vec_cmpge(x, y); }

////////////// Special Cases //////////////
static INLINE vopmask visinf_vo_vf(vfloat d)
{ return vec_cmpeq(vec_abs(d), vsetall__vf(SLEEF_INFINITYf)); }
static INLINE vopmask visinf_vo_vd(vdouble d)
{ return (vopmask)vec_cmpeq(vec_abs(d), vsetall__vd(SLEEF_INFINITY)); }

static INLINE vopmask vispinf_vo_vf(vfloat d)
{ return vec_cmpeq(d, vsetall__vf(SLEEF_INFINITYf)); }
static INLINE vopmask vispinf_vo_vd(vdouble d)
{ return (vopmask)vec_cmpeq(d, vsetall__vd(SLEEF_INFINITY)); }

static INLINE vopmask visminf_vo_vf(vfloat d)
{ return vec_cmpeq(d, vsetall__vf(-SLEEF_INFINITYf)); }
static INLINE vopmask visminf_vo_vd(vdouble d)
{ return (vopmask)vec_cmpeq(d, vsetall__vd(-SLEEF_INFINITY)); }

static INLINE vopmask visnan_vo_vf(vfloat d)
{ return vnot_vo_vo(vec_cmpeq(d, d)); }
static INLINE vopmask visnan_vo_vd(vdouble d)
{ return vnot_vo_vo((vopmask)vec_cmpeq(d, d)); }

/**********************************************
 ** Shift
 **********************************************/
////////////// Left //////////////
static INLINE vint vsll_vi_vi_i(vint x, int c)
{ return vec_sl (x, vsetall__u32(c)); }
static INLINE vint2 vsll_vi2_vi2_i(vint2 x, int c)
{ return vec_sl(x, vsetall__u32(c)); }

////////////// Right //////////////
static INLINE vint vsrl_vi_vi_i(vint x, int c)
{ return vec_sr(x, vsetall__u32(c)); }
static INLINE vint2 vsrl_vi2_vi2_i(vint2 x, int c)
{ return vec_sr(x, vsetall__u32(c)); }

////////////// Algebraic Right //////////////
static INLINE vint vsra_vi_vi_i(vint x, int c)
{ return vec_sra(x, vsetall__u32(c)); }
static INLINE vint2 vsra_vi2_vi2_i(vint2 x, int c)
{ return vec_sra(x, vsetall__u32(c)); }

/**********************************************
 ** Reorder
 **********************************************/

////////////// Reverse //////////////
// Reverse elements order inside the lower and higher parts
static INLINE vint2 vrev21_vi2_vi2(vint2 vi)
{ return vec_mergee(vec_mergeo(vi, vi), vi); }
static INLINE vfloat vrev21_vf_vf(vfloat vf)
{ return (vfloat)vrev21_vi2_vi2((vint2)vf); }

// Swap the lower and higher parts
static INLINE vfloat vreva2_vf_vf(vfloat vf)
{ return (vfloat)v__swapd_u64((v__u64)vf); }
static INLINE vdouble vrev21_vd_vd(vdouble vd)
{ return (vdouble)v__swapd_u64((v__u64)vd); }
static INLINE vdouble vreva2_vd_vd(vdouble vd)
{ return vd; }

/**********************************************
 ** Arithmetic
 **********************************************/

////////////// Negation //////////////
static INLINE vint vneg_vi_vi(vint e) {
#if defined(__clang__) || __GNUC__ >= 9
  return vec_neg(e);
#else
  return vec_sub(vzero__vi(), e);
#endif
}
static INLINE vint2 vneg_vi2_vi2(vint2 e)
{ return vneg_vi_vi(e); }

static INLINE vfloat vneg_vf_vf(vfloat d)
{
  vfloat ret;
#if defined(__clang__) || __GNUC__ >= 9
  ret = vec_neg(d);
#else
  __asm__ __volatile__("xvnegsp %x0,%x1" : "=wa" (ret) : "wa" (d));
#endif
  return ret;
}

static INLINE vdouble vneg_vd_vd(vdouble d)
{
  vdouble ret;
#if defined(__clang__) || __GNUC__ >= 9
  ret = vec_neg(d);
#else
  __asm__ __volatile__("xvnegdp %x0,%x1" : "=wa" (ret) : "wa" (d));
#endif
  return ret;
}

static INLINE vfloat vposneg_vf_vf(vfloat d)
{ return vec_xor(d, vset__vf(+0.0f, -0.0f, +0.0f, -0.0f)); }
static INLINE vdouble vposneg_vd_vd(vdouble d)
{ return vec_xor(d, vset__vd(+0.0, -0.0)); }

static INLINE vfloat vnegpos_vf_vf(vfloat d)
{ return vec_xor(d, vset__vf(-0.0f, +0.0f, -0.0f, +0.0f)); }
static INLINE vdouble vnegpos_vd_vd(vdouble d)
{ return vec_xor(d, vset__vd(-0.0, +0.0)); }

////////////// Addition //////////////
static INLINE vint vadd_vi_vi_vi(vint x, vint y)
{ return vec_add(x, y); }
static INLINE vint2 vadd_vi2_vi2_vi2(vint2 x, vint2 y)
{ return vec_add(x, y); }

static INLINE vfloat vadd_vf_vf_vf(vfloat x, vfloat y)
{ return vec_add(x, y); }
static INLINE vdouble vadd_vd_vd_vd(vdouble x, vdouble y)
{ return vec_add(x, y); }

static INLINE vmask vadd64_vm_vm_vm(vmask x, vmask y)
{ return (vmask)vec_add((v__i64)x, (v__i64)y); }

////////////// Subtraction //////////////
static INLINE vint vsub_vi_vi_vi(vint x, vint y)
{ return vec_sub(x, y); }
static INLINE vint2 vsub_vi2_vi2_vi2(vint2 x, vint2 y)
{ return vec_sub(x, y); }

static INLINE vfloat vsub_vf_vf_vf(vfloat x, vfloat y)
{ return vec_sub(x, y); }
static INLINE vdouble vsub_vd_vd_vd(vdouble x, vdouble y)
{ return vec_sub(x, y); }

static INLINE vdouble vsubadd_vd_vd_vd(vdouble x, vdouble y)
{ return vec_add(x, vnegpos_vd_vd(y)); }
static INLINE vfloat vsubadd_vf_vf_vf(vfloat x, vfloat y)
{ return vec_add(x, vnegpos_vf_vf(y)); }

////////////// Multiplication //////////////
static INLINE vfloat vmul_vf_vf_vf(vfloat x, vfloat y)
{ return vec_mul(x, y); }
static INLINE vdouble vmul_vd_vd_vd(vdouble x, vdouble y)
{ return vec_mul(x, y); }

static INLINE vfloat vdiv_vf_vf_vf(vfloat x, vfloat y)
{ return vec_div(x, y); }
static INLINE vdouble vdiv_vd_vd_vd(vdouble x, vdouble y)
{ return vec_div(x, y); }

static INLINE vfloat vrec_vf_vf(vfloat x)
{ return vec_div(vsetall__vf(1.0f), x); }
static INLINE vdouble vrec_vd_vd(vdouble x)
{ return vec_div(vsetall__vd(1.0), x); }

/**********************************************
 ** Math
 **********************************************/

static INLINE vfloat vmax_vf_vf_vf(vfloat x, vfloat y)
{ return vec_max(x, y); }
static INLINE vdouble vmax_vd_vd_vd(vdouble x, vdouble y)
{ return vec_max(x, y); }

static INLINE vfloat vmin_vf_vf_vf(vfloat x, vfloat y)
{ return vec_min(x, y); }
static INLINE vdouble vmin_vd_vd_vd(vdouble x, vdouble y)
{ return vec_min(x, y); }

static INLINE vfloat vabs_vf_vf(vfloat f)
{ return vec_abs(f); }
static INLINE vdouble vabs_vd_vd(vdouble d)
{ return vec_abs(d); }

static INLINE vfloat vsqrt_vf_vf(vfloat f)
{ return vec_sqrt(f); }
static INLINE vdouble vsqrt_vd_vd(vdouble d)
{ return vec_sqrt(d); }


/**********************************************
 ** FMA3
 **********************************************/
#if CONFIG == 1 || CONFIG == 3

static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_madd(x, y, z); }
static INLINE vdouble vmla_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_madd(x, y, z); }

static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_msub(x, y, z); }
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_msub(x, y, z); }

static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_nmsub(x, y, z); }
static INLINE vdouble vmlanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_nmsub(x, y, z); }

#else

static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_add(vec_mul(x, y), z); }
static INLINE vdouble vmla_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_add(vec_mul(x, y), z); }

static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_sub(vec_mul(x, y), z); }
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_sub(vec_mul(x, y), z); }

static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_sub(z, vec_mul(x, y)); }
static INLINE vdouble vmlanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_sub(z, vec_mul(x, y)); }

#endif

static INLINE vfloat vfma_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_madd(x, y, z); }
static INLINE vdouble vfma_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_madd(x, y, z); }
static INLINE vfloat vfmapp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_madd(x, y, z); }
static INLINE vdouble vfmapp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_madd(x, y, z); }

static INLINE vfloat vfmapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_msub(x, y, z); }
static INLINE vdouble vfmapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_msub(x, y, z); }

static INLINE vfloat vfmanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_nmsub(x, y, z); }
static INLINE vdouble vfmanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_nmsub(x, y, z); }

static INLINE vfloat vfmann_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vec_nmadd(x, y, z); }
static INLINE vdouble vfmann_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vec_nmadd(x, y, z); }

static INLINE vfloat vmlsubadd_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z)
{ return vmla_vf_vf_vf_vf(x, y, vnegpos_vf_vf(z)); }
static INLINE vdouble vmlsubadd_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z)
{ return vmla_vd_vd_vd_vd(x, y, vnegpos_vd_vd(z)); }

//

static vquad loadu_vq_p(void *p) {
  vquad vq;
  memcpy(&vq, p, VECTLENDP * 16);
  return vq;
}

static INLINE vquad cast_vq_aq(vargquad aq) {
  vquad vq;
  memcpy(&vq, &aq, VECTLENDP * 16);
  return vq;
}

static INLINE vargquad cast_aq_vq(vquad vq) {
  vargquad aq;
  memcpy(&aq, &vq, VECTLENDP * 16);
  return aq;
}

static INLINE int vtestallzeros_i_vo64(vopmask g) {
  return vec_all_eq((__vector signed long long)g, vzero__s64());
}

static INLINE vmask vsel_vm_vo64_vm_vm(vopmask o, vmask x, vmask y) {
  return (vmask)vec_sel((__vector signed long long)y, (__vector signed long long)x, (v__b64)o);
}

static INLINE vmask vsub64_vm_vm_vm(vmask x, vmask y) {
  return (vmask)vec_sub((__vector signed long long)x, (__vector signed long long)y);
}

static INLINE vmask vneg64_vm_vm(vmask x) {
  return (vmask)vec_sub((__vector signed long long) {0, 0}, (__vector signed long long)x);
}

static INLINE vopmask vgt64_vo_vm_vm(vmask x, vmask y) {
  return (vopmask)vec_cmpgt((__vector signed long long)x, (__vector signed long long)y);
}

#define vsll64_vm_vm_i(x, c) ((vmask)vec_sl((__vector signed long long)x, (__vector unsigned long long)vsetall__vm(c)))
#define vsrl64_vm_vm_i(x, c) ((vmask)vec_sr((__vector signed long long)x, (__vector unsigned long long)vsetall__vm(c)))

static INLINE vint vcast_vi_vm(vmask vm) {
  return (vint) { vm[0], vm[2] };
}

static INLINE vmask vcast_vm_vi(vint vi) {
  return (vmask) (__vector signed long long) { vi[0], vi[1] };
}

static INLINE vmask vreinterpret_vm_vi64(vint64 v) { return (vmask)v; }
static INLINE vint64 vreinterpret_vi64_vm(vmask m) { return (vint64)m; }
static INLINE vmask vreinterpret_vm_vu64(vuint64 v) { return (vmask)v; }
static INLINE vuint64 vreinterpret_vu64_vm(vmask m) { return (vuint64)m; }
