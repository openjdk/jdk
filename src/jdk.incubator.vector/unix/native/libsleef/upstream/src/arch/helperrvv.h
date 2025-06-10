//   Copyright Naoki Shibata and contributors 2023.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#ifndef HELPERRVV_H
#define HELPERRVV_H

#if !defined(SLEEF_GENHEADER)
#include <riscv_vector.h>
#include "misc.h"

#if defined(VECTLENDP) || defined(VECTLENSP)
#error VECTLENDP or VECTLENSP already defined
#endif
#endif // #if !defined(SLEEF_GENHEADER)

#if CONFIG == 1 || CONFIG == 2
#define ISANAME "RISC-V Vector Extension with Min. VLEN"
#define SLEEF_RVV_VLEN 0
#elif CONFIG == 7
// 128-bit vector length
#define ISANAME "RISC-V Vector Extension 128-bit"
#define LOG2VECTLENDP 1
#define SLEEF_RVV_VLEN ((1 << 7) / 8)
#define DFTPRIORITY 19
#elif CONFIG == 8
// 256-bit vector length
#define ISANAME "RISC-V Vector Extension 256-bit"
#define LOG2VECTLENDP 2
#define SLEEF_RVV_VLEN ((1 << 8) / 8)
#define DFTPRIORITY 20
#elif CONFIG == 9
// 512-bit vector length
#define ISANAME "RISC-V Vector Extension 512-bit"
#define LOG2VECTLENDP 3
#define SLEEF_RVV_VLEN ((1 << 9) / 8)
#define DFTPRIORITY 21
#elif CONFIG == 10
// 1024-bit vector length
#define ISANAME "RISC-V Vector Extension 1024-bit"
#define LOG2VECTLENDP 4
#define SLEEF_RVV_VLEN ((1 << 10) / 8)
#define DFTPRIORITY 22
#elif CONFIG == 11
// 2048-bit vector length
#define ISANAME "RISC-V Vector Extension 2048-bit"
#define LOG2VECTLENDP 5
#define SLEEF_RVV_VLEN ((1 << 11) / 8)
#define DFTPRIORITY 23
#else
#error CONFIG macro invalid or not defined
#endif

#define LOG2VECTLENSP (LOG2VECTLENDP+1)

#define ENABLE_SP
//@#define ENABLE_SP
#define ENABLE_DP
//@#define ENABLE_DP

#if CONFIG != 2
#if defined(ENABLE_RVVM1NOFMA) || defined(ENABLE_RVVM2NOFMA)
#error "RVV NOFMA only supported for CONFIG=2"
#else
#define ENABLE_FMA_SP
//@#define ENABLE_FMA_SP
#define ENABLE_FMA_DP
//@#define ENABLE_FMA_DP
#endif
#endif

#if __riscv_v_intrinsic < 1000000 && !(defined(__clang_major__) && __clang_major__ >= 18)
// __riscv_vcreate* intrinsics only showed up in v1.0-rc0 of the RVV intrinsics
// spec and have already been implemented in clang-18, but are useful for
// eliminating issues with uninitialised data because they are explicit that
// the whole result has defined values.  Here we do our best to offer fallback
// implementations where needed.
//
#define __riscv_vcreate_v_f32m1_f32m2(x, y) __riscv_vset(__riscv_vlmul_ext_v_f32m1_f32m2(x), 1, y)
#define __riscv_vcreate_v_f32m2_f32m4(x, y) __riscv_vset(__riscv_vlmul_ext_v_f32m2_f32m4(x), 1, y)
#define __riscv_vcreate_v_f32m4_f32m8(x, y) __riscv_vset(__riscv_vlmul_ext_v_f32m4_f32m8(x), 1, y)
#define __riscv_vcreate_v_f64m1_f64m2(x, y) __riscv_vset(__riscv_vlmul_ext_v_f64m1_f64m2(x), 1, y)
#define __riscv_vcreate_v_f64m2_f64m4(x, y) __riscv_vset(__riscv_vlmul_ext_v_f64m2_f64m4(x), 1, y)
#define __riscv_vcreate_v_f64m4_f64m8(x, y) __riscv_vset(__riscv_vlmul_ext_v_f64m4_f64m8(x), 1, y)
#define __riscv_vcreate_v_i32m1_i32m2(x, y) __riscv_vset(__riscv_vlmul_ext_v_i32m1_i32m2(x), 1, y)
#define __riscv_vcreate_v_i32m2_i32m4(x, y) __riscv_vset(__riscv_vlmul_ext_v_i32m2_i32m4(x), 1, y)
#define __riscv_vcreate_v_i32m4_i32m8(x, y) __riscv_vset(__riscv_vlmul_ext_v_i32m4_i32m8(x), 1, y)
#define __riscv_vcreate_v_u64m1_u64m2(x, y) __riscv_vset(__riscv_vlmul_ext_v_u64m1_u64m2(x), 1, y)
#define __riscv_vcreate_v_u64m2_u64m4(x, y) __riscv_vset(__riscv_vlmul_ext_v_u64m2_u64m4(x), 1, y)

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wuninitialized"
static INLINE vfloat64m1x4_t __riscv_vcreate_v_f64m1x4(vfloat64m1_t x, vfloat64m1_t y, vfloat64m1_t z, vfloat64m1_t w) {
    vfloat64m1x4_t unused;
    return __riscv_vset(__riscv_vset(__riscv_vset(__riscv_vset(unused, 0, x), 1, y), 2, z), 3, w);
}
static INLINE vfloat64m2x4_t __riscv_vcreate_v_f64m2x4(vfloat64m2_t x, vfloat64m2_t y, vfloat64m2_t z, vfloat64m2_t w) {
    vfloat64m2x4_t unused;
    return __riscv_vset(__riscv_vset(__riscv_vset(__riscv_vset(unused, 0, x), 1, y), 2, z), 3, w);
}
#pragma GCC diagnostic pop
#endif

#ifdef NDEBUG
#define SLEEF_RVV_VEXT(size, from_to, v) __riscv_vlmul_ext_v_##from_to(v)
#else
// In situations where we cast from wider to narrower types and then back again
// we should expect data loss, but it can too easily sneak through undisturbed.
//
// QEMU and some hardware have a feature to automatically wipe partial vectors
// when they get truncated this way, but for pure casts like vlmul_ext we need
// to insert a deliberate move operation to force that to happen.  Since it's
// extra work it's only enabled for debug builds.
//
#define SLEEF_RVV_VEXT(size, from_to, v) __riscv_vmv_v(__riscv_vlmul_ext_v_##from_to(v), __riscv_vsetvlmax_##size())
#endif

////////////////////////////////////////////////////////////////////////////////
// RISC-V Vector Types
////////////////////////////////////////////////////////////////////////////////

// LMUL-Dependent Type & Macro Definitions:
//
// Some SLEEF types are multi-value structs. RVV vectors have unknown length at
// compile time, so they cannote appear in a struct in Clang. They are instead
// represented as single vectors with "members" packed into the registers of a
// wide-LMUL register group. In the largest cases (ddi_t and ddf_t), this
// requires LMUL=8 if the base type (vfloat or vdouble) has LMUL=2, meaning
// LMUL=2 is currently the widest option for SLEEF function argument types.
#if defined(ENABLE_RVVM1) || defined(ENABLE_RVVM1NOFMA)

typedef vuint32m1_t rvv_vmask32;
typedef vuint64m1_t vmask;
typedef vbool32_t rvv_sp_vopmask;
typedef vbool64_t rvv_dp_vopmask;

typedef vint32mf2_t vint;
typedef vint64m1_t vint64;
typedef vuint64m1_t vuint64;
typedef vfloat64m1_t vdouble;
typedef vfloat64m2_t vdouble2;
typedef vfloat64m4_t vdouble3;
typedef vfloat64m4_t dd2;
typedef vuint64m2_t vquad;
typedef vint32m2_t di_t;
typedef vint32m4_t ddi_t;
typedef vfloat32m1_t vfloat;
typedef vfloat32m2_t vfloat2;
typedef vfloat32m4_t df2;
typedef vint32m1_t vint2;
typedef vint32m2_t fi_t;
typedef vint32m4_t dfi_t;
typedef vuint64m1_t rvv_dp_vuint2;

typedef vfloat64m1x4_t tdx;
typedef vfloat64m1x4_t tdi_t;

#define SLEEF_RVV_SP_LMUL 1
#define SLEEF_RVV_DP_LMUL 1
#define SLEEF_RVV_DP_RUNTIME_VL() __riscv_vsetvlmax_e64m1()
#if SLEEF_RVV_VLEN == 0
// The configuration didn't provide a constant vector length, meaning it'll
// have to be determined at run-time.  RVV offers per-data-width operations for
// this so the result doesn't need to be adjusted and that operation is likely
// to fold into the surrounding code for free.
//
#define VECTLENSP (__riscv_vsetvlmax_e32m1())
#define VECTLENDP SLEEF_RVV_DP_RUNTIME_VL()
//@#define VECTLENSP __riscv_vsetvlmax_e32m1()
//@#define VECTLENDP  __riscv_vsetvlmax_e64m1()
#else
#define VECTLENSP (SLEEF_RVV_SP_LMUL * SLEEF_RVV_VLEN / sizeof(float))
#define VECTLENDP (SLEEF_RVV_DP_LMUL * SLEEF_RVV_VLEN / sizeof(double))
//@#define VECTLENSP (SLEEF_RVV_SP_LMUL * SLEEF_RVV_VLEN / sizeof(float))
//@#define VECTLENDP (SLEEF_RVV_DP_LMUL * SLEEF_RVV_VLEN / sizeof(double))
#endif
#define SLEEF_RVV_SP_VCAST_VF_F __riscv_vfmv_v_f_f32m1
#define SLEEF_RVV_SP_VCAST_VI2_I __riscv_vmv_v_x_i32m1
#define SLEEF_RVV_SP_VREINTERPRET_VF __riscv_vreinterpret_f32m1
#define SLEEF_RVV_SP_VREINTERPRET_VF2 __riscv_vreinterpret_f32m2
#define SLEEF_RVV_SP_VREINTERPRET_VM __riscv_vreinterpret_u32m1
#define SLEEF_RVV_SP_VREINTERPRET_VI2 __riscv_vreinterpret_i32m1
#define SLEEF_RVV_SP_VREINTERPRET_2VI __riscv_vreinterpret_i32m2
#define SLEEF_RVV_SP_VREINTERPRET_VU __riscv_vreinterpret_u32m1
#define SLEEF_RVV_SP_VREINTERPRET_VU2 __riscv_vreinterpret_u32m1
#define SLEEF_RVV_SP_VREINTERPRET_VOM __riscv_vreinterpret_b32
#define SLEEF_RVV_SP_VID()  __riscv_vid_v_u32m1(VECTLENSP)
#define SLEEF_RVV_SP_VGET_VI2 __riscv_vget_i32m1
#define SLEEF_RVV_SP_VGET_2VI __riscv_vget_i32m2
#define SLEEF_RVV_SP_VGET_VF __riscv_vget_f32m1
#define SLEEF_RVV_SP_VGET_VF2 __riscv_vget_f32m2
#define SLEEF_RVV_SP_VCREATE_FI __riscv_vcreate_v_i32m1_i32m2
#define SLEEF_RVV_SP_VCREATE_DFI __riscv_vcreate_v_i32m2_i32m4
#define SLEEF_RVV_SP_VCREATE_DF2 __riscv_vcreate_v_f32m2_f32m4
#define SLEEF_RVV_SP_VCREATE_VF2 __riscv_vcreate_v_f32m1_f32m2
#define SLEEF_RVV_SP_VLMUL_EXT_VI2_TO_FI(v) SLEEF_RVV_VEXT(e32m1, i32m1_i32m2, v)
#define SLEEF_RVV_SP_LOAD_VF __riscv_vle32_v_f32m1
#define SLEEF_RVV_SP_LOAD_2VI __riscv_vle32_v_i32m2
#define SLEEF_RVV_SP_VFNCVT_X_F_VI __riscv_vfcvt_x_f_v_i32m1_rm
#define SLEEF_RVV_SP_VFCVT_F_X_VF __riscv_vfcvt_f_x_v_f32m1
#define SLEEF_RVV_SP_VFCVT_X_F_VF_RM __riscv_vfcvt_x_f_v_i32m1_rm
#define SLEEF_RVV_DP_VCAST_VD_D __riscv_vfmv_v_f_f64m1
#define SLEEF_RVV_DP_VCAST_VD_VI(x) __riscv_vfwcvt_f(x, VECTLENDP)
#define SLEEF_RVV_DP_VCAST_VI_I __riscv_vmv_v_x_i32mf2
#define SLEEF_RVV_DP_VCAST_VM_U __riscv_vmv_v_x_u64m1
#define SLEEF_RVV_DP_VREINTERPRET_VD(v) __riscv_vreinterpret_f64m1(__riscv_vreinterpret_i64m1(v))
#define SLEEF_RVV_DP_VREINTERPRET_4VD_8VI(x) \
  __riscv_vreinterpret_f64m4(__riscv_vreinterpret_v_i32m4_i64m4(x))
#define SLEEF_RVV_DP_VREINTERPRET_VM __riscv_vreinterpret_u64m1
#define SLEEF_RVV_DP_VREINTERPRET_VM_SIGNED __riscv_vreinterpret_i64m1
#define SLEEF_RVV_DP_VREINTERPRET_VI64 __riscv_vreinterpret_i64m1
#define SLEEF_RVV_DP_VREINTERPRET_VI __riscv_vreinterpret_i32mf2
#define SLEEF_RVV_DP_VREINTERPRET_VI2(v) __riscv_vreinterpret_i32m1(__riscv_vreinterpret_i64m1(v))
#define SLEEF_RVV_DP_VREINTERPRET_4VI(v) __riscv_vreinterpret_i32m2(__riscv_vreinterpret_i64m2(v))
#define SLEEF_RVV_DP_VREINTERPRET_VU __riscv_vreinterpret_u32mf2
#define SLEEF_RVV_DP_VREINTERPRET_4VU __riscv_vreinterpret_u32m2
#define SLEEF_RVV_DP_VREINTERPRET_VQ __riscv_vreinterpret_u64m2
#define SLEEF_RVV_DP_VREINTERPRET_VOM __riscv_vreinterpret_b64
#define SLEEF_RVV_DP_VID()  __riscv_vid_v_u64m1(VECTLENDP)
#define SLEEF_RVV_DP_VGET_VM __riscv_vget_u64m1
#define SLEEF_RVV_DP_VGET_VD __riscv_vget_f64m1
#define SLEEF_RVV_DP_VGET_VD2 __riscv_vget_f64m2
#define SLEEF_RVV_DP_VGET_4VD __riscv_vget_f64m2
#define SLEEF_RVV_DP_VGET_VI2 __riscv_vget_i32m1
#define SLEEF_RVV_DP_VCREATE_DI __riscv_vcreate_v_i32m1_i32m2
#define SLEEF_RVV_DP_VCREATE_DDI(x, y) __riscv_vcreate_v_i32m2_i32m4(x, SLEEF_RVV_VEXT(e32mf2, i32mf2_i32m2,  y))
#define SLEEF_RVV_DP_VCREATE_TD __riscv_vcreate_v_f64m1x4
#define SLEEF_RVV_DP_VCREATE_VD2 __riscv_vcreate_v_f64m1_f64m2
#define SLEEF_RVV_DP_VCREATE_VQ __riscv_vcreate_v_u64m1_u64m2
#define SLEEF_RVV_DP_VCREATE_3VD(x, y, z) __riscv_vcreate_v_f64m2_f64m4(__riscv_vcreate_v_f64m1_f64m2(x, y), SLEEF_RVV_VEXT(e64m1, f64m1_f64m2, z))
#define SLEEF_RVV_DP_VCREATE_4VD __riscv_vcreate_v_f64m2_f64m4
#define SLEEF_RVV_DP_VLMUL_TRUNC_VI2_TO_VI(v) __riscv_vlmul_trunc_i32mf2(v)
#define SLEEF_RVV_DP_VLMUL_EXT_VI_TO_VI2(v) SLEEF_RVV_VEXT(e32mf2, i32mf2_i32m1, v)
#define SLEEF_RVV_DP_LOAD_VD __riscv_vle64_v_f64m1
#define SLEEF_RVV_DP_LOAD_VI __riscv_vle32_v_i32mf2
#define SLEEF_RVV_DP_VFNCVT_X_F_VI __riscv_vfncvt_x_f_w_i32mf2_rm
#define SLEEF_RVV_DP_VFCVT_F_X_VD __riscv_vfcvt_f_x_v_f64m1
#define SLEEF_RVV_DP_VFCVT_X_F_VD_RM __riscv_vfcvt_x_f_v_i64m1_rm

#elif defined(ENABLE_RVVM2) || defined(ENABLE_RVVM2NOFMA)

typedef vuint32m2_t rvv_vmask32;
typedef vuint64m2_t vmask;
typedef vbool16_t rvv_sp_vopmask;
typedef vbool32_t rvv_dp_vopmask;

typedef vint32m1_t vint;
typedef vint64m2_t vint64;
typedef vuint64m2_t vuint64;
typedef vfloat64m2_t vdouble;
typedef vfloat64m4_t vdouble2;
typedef vfloat64m8_t vdouble3;
typedef vfloat64m8_t dd2;
typedef vuint64m4_t vquad;
typedef vint32m4_t di_t;
typedef vint32m8_t ddi_t;
typedef vfloat32m2_t vfloat;
typedef vfloat32m4_t vfloat2;
typedef vfloat32m8_t df2;
typedef vint32m2_t vint2;
typedef vint32m4_t fi_t;
typedef vint32m8_t dfi_t;
typedef vuint64m2_t rvv_dp_vuint2;

typedef vfloat64m2x4_t tdx;
typedef vfloat64m2x4_t tdi_t;

#define SLEEF_RVV_SP_LMUL 2
#define SLEEF_RVV_DP_LMUL 2
#define SLEEF_RVV_DP_RUNTIME_VL() __riscv_vsetvlmax_e64m2()
#if SLEEF_RVV_VLEN == 0
// The configuration didn't provide a constant vector length, meaning it'll
// have to be determined at run-time.  RVV offers per-data-width operations for
// this so the result doesn't need to be adjusted and that operation is likely
// to fold into the surrounding code for free.
//
#define VECTLENSP (__riscv_vsetvlmax_e32m2())
#define VECTLENDP SLEEF_RVV_DP_RUNTIME_VL()
//@#define VECTLENSP __riscv_vsetvlmax_e32m2()
//@#define VECTLENDP  __riscv_vsetvlmax_e64m2()
#else
#define VECTLENSP (SLEEF_RVV_SP_LMUL * SLEEF_RVV_VLEN / sizeof(float))
#define VECTLENDP (SLEEF_RVV_DP_LMUL * SLEEF_RVV_VLEN / sizeof(double))
//@#define VECTLENSP (SLEEF_RVV_SP_LMUL * SLEEF_RVV_VLEN / sizeof(float))
//@#define VECTLENDP (SLEEF_RVV_DP_LMUL * SLEEF_RVV_VLEN / sizeof(double))
#endif
#define SLEEF_RVV_SP_VCAST_VF_F __riscv_vfmv_v_f_f32m2
#define SLEEF_RVV_SP_VCAST_VI2_I __riscv_vmv_v_x_i32m2
#define SLEEF_RVV_SP_VREINTERPRET_VF __riscv_vreinterpret_f32m2
#define SLEEF_RVV_SP_VREINTERPRET_VF2 __riscv_vreinterpret_f32m4
#define SLEEF_RVV_SP_VREINTERPRET_VM __riscv_vreinterpret_u32m2
#define SLEEF_RVV_SP_VREINTERPRET_VI2 __riscv_vreinterpret_i32m2
#define SLEEF_RVV_SP_VREINTERPRET_2VI __riscv_vreinterpret_i32m4
#define SLEEF_RVV_SP_VREINTERPRET_VU __riscv_vreinterpret_u32m2
#define SLEEF_RVV_SP_VREINTERPRET_VU2 __riscv_vreinterpret_u32m2
#define SLEEF_RVV_SP_VREINTERPRET_VOM __riscv_vreinterpret_b16
#define SLEEF_RVV_SP_VID()  __riscv_vid_v_u32m2(VECTLENSP)
#define SLEEF_RVV_SP_VGET_VI2 __riscv_vget_i32m2
#define SLEEF_RVV_SP_VGET_2VI __riscv_vget_i32m4
#define SLEEF_RVV_SP_VGET_VF __riscv_vget_f32m2
#define SLEEF_RVV_SP_VGET_VF2 __riscv_vget_f32m4
#define SLEEF_RVV_SP_VCREATE_FI __riscv_vcreate_v_i32m2_i32m4
#define SLEEF_RVV_SP_VCREATE_DFI __riscv_vcreate_v_i32m4_i32m8
#define SLEEF_RVV_SP_VCREATE_DF2 __riscv_vcreate_v_f32m4_f32m8
#define SLEEF_RVV_SP_VCREATE_VF2 __riscv_vcreate_v_f32m2_f32m4
#define SLEEF_RVV_SP_VLMUL_EXT_VI2_TO_FI(v) SLEEF_RVV_VEXT(e32m2, i32m2_i32m4, v)
#define SLEEF_RVV_SP_LOAD_VF __riscv_vle32_v_f32m2
#define SLEEF_RVV_SP_LOAD_2VI __riscv_vle32_v_i32m4
#define SLEEF_RVV_SP_VFNCVT_X_F_VI __riscv_vfcvt_x_f_v_i32m2_rm
#define SLEEF_RVV_SP_VFCVT_F_X_VF __riscv_vfcvt_f_x_v_f32m2
#define SLEEF_RVV_SP_VFCVT_X_F_VF_RM __riscv_vfcvt_x_f_v_i32m2_rm
#define SLEEF_RVV_DP_VCAST_VD_D __riscv_vfmv_v_f_f64m2
#define SLEEF_RVV_DP_VCAST_VD_VI(x) __riscv_vfwcvt_f(x, VECTLENDP)
#define SLEEF_RVV_DP_VCAST_VI_I __riscv_vmv_v_x_i32m1
#define SLEEF_RVV_DP_VCAST_VM_U __riscv_vmv_v_x_u64m2
#define SLEEF_RVV_DP_VREINTERPRET_VD(v) __riscv_vreinterpret_f64m2(__riscv_vreinterpret_i64m2(v))
#define SLEEF_RVV_DP_VREINTERPRET_4VD_8VI(x) \
  __riscv_vreinterpret_f64m8(__riscv_vreinterpret_v_i32m8_i64m8(x))
#define SLEEF_RVV_DP_VREINTERPRET_VM __riscv_vreinterpret_u64m2
#define SLEEF_RVV_DP_VREINTERPRET_VM_SIGNED __riscv_vreinterpret_i64m2
#define SLEEF_RVV_DP_VREINTERPRET_VI64 __riscv_vreinterpret_i64m2
#define SLEEF_RVV_DP_VREINTERPRET_VI __riscv_vreinterpret_i32m1
#define SLEEF_RVV_DP_VREINTERPRET_VI2(v) __riscv_vreinterpret_i32m2(__riscv_vreinterpret_i64m2(v))
#define SLEEF_RVV_DP_VREINTERPRET_4VI(v) __riscv_vreinterpret_i32m4(__riscv_vreinterpret_i64m4(v))
#define SLEEF_RVV_DP_VREINTERPRET_VU __riscv_vreinterpret_u32m1
#define SLEEF_RVV_DP_VREINTERPRET_4VU __riscv_vreinterpret_u32m4
#define SLEEF_RVV_DP_VREINTERPRET_VQ __riscv_vreinterpret_u64m4
#define SLEEF_RVV_DP_VREINTERPRET_VOM __riscv_vreinterpret_b32
#define SLEEF_RVV_DP_VID()  __riscv_vid_v_u64m2(VECTLENDP)
#define SLEEF_RVV_DP_VGET_VM __riscv_vget_u64m2
#define SLEEF_RVV_DP_VGET_VD __riscv_vget_f64m2
#define SLEEF_RVV_DP_VGET_VD2 __riscv_vget_f64m4
#define SLEEF_RVV_DP_VGET_4VD __riscv_vget_f64m4
#define SLEEF_RVV_DP_VGET_VI2 __riscv_vget_i32m2
#define SLEEF_RVV_DP_VCREATE_DI __riscv_vcreate_v_i32m2_i32m4
#define SLEEF_RVV_DP_VCREATE_DDI(x, y) __riscv_vcreate_v_i32m4_i32m8(x, SLEEF_RVV_VEXT(e32m1, i32m1_i32m4, y))
#define SLEEF_RVV_DP_VCREATE_TD __riscv_vcreate_v_f64m2x4
#define SLEEF_RVV_DP_VCREATE_VD2 __riscv_vcreate_v_f64m2_f64m4
#define SLEEF_RVV_DP_VCREATE_VQ __riscv_vcreate_v_u64m2_u64m4
#define SLEEF_RVV_DP_VCREATE_3VD(x, y, z) __riscv_vcreate_v_f64m4_f64m8(__riscv_vcreate_v_f64m2_f64m4(x, y), SLEEF_RVV_VEXT(e64m2, f64m2_f64m4, z))
#define SLEEF_RVV_DP_VCREATE_4VD __riscv_vcreate_v_f64m4_f64m8
#define SLEEF_RVV_DP_VLMUL_TRUNC_VI2_TO_VI(v) __riscv_vlmul_trunc_i32m1(v)
#define SLEEF_RVV_DP_VLMUL_EXT_VI_TO_VI2(v) SLEEF_RVV_VEXT(e32m1, i32m1_i32m2, v)
#define SLEEF_RVV_DP_LOAD_VD __riscv_vle64_v_f64m2
#define SLEEF_RVV_DP_LOAD_VI __riscv_vle32_v_i32m1
#define SLEEF_RVV_DP_VFNCVT_X_F_VI __riscv_vfncvt_x_f_w_i32m1_rm
#define SLEEF_RVV_DP_VFCVT_F_X_VD __riscv_vfcvt_f_x_v_f64m2
#define SLEEF_RVV_DP_VFCVT_X_F_VD_RM __riscv_vfcvt_x_f_v_i64m2_rm

#else
#error "unknown rvv lmul"
#endif // ENABLE_RVVM1

typedef vquad vargquad;

static INLINE int vavailability_i(int name) {
  // Note that in some cases VECTLENDP is defined as SLEEF_RVV_DP_RUNTIME_VL(),
  // which makes this kind of a redundant operation.  It's still preferable to
  // issue the instructions, though, because if it's not available then it'll
  // raise an illegal instruction exception which is trapped by the caller for
  // proper error handling in the expected place.
  //
  return (SLEEF_RVV_DP_RUNTIME_VL() >= VECTLENDP) ? 3 : 0;
}

////////////////////////////////////////////////////////////////////////////////
// Single-Precision Functions
////////////////////////////////////////////////////////////////////////////////

/****************************************/
/* Multi-value and multi-word types     */
/****************************************/
// fi type
static INLINE vfloat figetd_vf_di(fi_t d) {
  return SLEEF_RVV_SP_VREINTERPRET_VF(SLEEF_RVV_SP_VGET_VI2(d, 0));
}
static INLINE vint2 figeti_vi2_di(fi_t d) {
  return SLEEF_RVV_SP_VGET_VI2(d, 1);
}
static INLINE fi_t fisetdi_fi_vf_vi2(vfloat d, vint2 i) {
  vint2 vdi = SLEEF_RVV_SP_VREINTERPRET_VI2(d);
  return SLEEF_RVV_SP_VCREATE_FI(vdi, i);
}
static INLINE vfloat2 dfigetdf_vf2_dfi(dfi_t d) {
  return SLEEF_RVV_SP_VREINTERPRET_VF2(SLEEF_RVV_SP_VGET_2VI(d, 0));
}
static INLINE vint2 dfigeti_vi2_dfi(dfi_t d) {
  return SLEEF_RVV_SP_VGET_VI2(d, 2);
}
static INLINE dfi_t dfisetdfi_dfi_vf2_vi2(vfloat2 v, vint2 i) {
  fi_t vi = SLEEF_RVV_SP_VREINTERPRET_2VI(v);
  fi_t ix = SLEEF_RVV_SP_VLMUL_EXT_VI2_TO_FI(i);
  return SLEEF_RVV_SP_VCREATE_DFI(vi, ix);
}
static INLINE dfi_t dfisetdf_dfi_dfi_vf2(dfi_t dfi, vfloat2 v) {
  return __riscv_vset(dfi, 0, SLEEF_RVV_SP_VREINTERPRET_2VI(v));
}
// vfloat2 type
static INLINE vfloat vf2getx_vf_vf2(vfloat2 v) {
  return SLEEF_RVV_SP_VGET_VF(v, 0);
}
static INLINE vfloat vf2gety_vf_vf2(vfloat2 v) {
  return SLEEF_RVV_SP_VGET_VF(v, 1);
}
static INLINE vfloat2 vf2setxy_vf2_vf_vf(vfloat x, vfloat y) {
  return SLEEF_RVV_SP_VCREATE_VF2(x, y);
}
static INLINE vfloat2 vf2setx_vf2_vf2_vf(vfloat2 v, vfloat d) {
  return __riscv_vset(v, 0, d);
}
static INLINE vfloat2 vf2sety_vf2_vf2_vf(vfloat2 v, vfloat d) {
  return __riscv_vset(v, 1, d);
}
// df2 type
static df2 df2setab_df2_vf2_vf2(vfloat2 a, vfloat2 b) {
  return SLEEF_RVV_SP_VCREATE_DF2(a, b);
}
static vfloat2 df2geta_vf2_df2(df2 d) { return SLEEF_RVV_SP_VGET_VF2(d, 0); }
static vfloat2 df2getb_vf2_df2(df2 d) { return SLEEF_RVV_SP_VGET_VF2(d, 1); }
static INLINE vint2 vreinterpret_vi2_vf(vfloat vf) {
  return SLEEF_RVV_SP_VREINTERPRET_VI2(vf);
}
static INLINE vfloat vreinterpret_vf_vi2(vint2 vi) {
  return SLEEF_RVV_SP_VREINTERPRET_VF(vi);
}


/****************************************/
/* Type Conversions and Broadcasts      */
/****************************************/
static INLINE vfloat vcast_vf_f(float f) {
  return SLEEF_RVV_SP_VCAST_VF_F(f, VECTLENSP);
}
static INLINE vfloat vrint_vf_vf(vfloat vd) {
  return SLEEF_RVV_SP_VFCVT_F_X_VF(SLEEF_RVV_SP_VFCVT_X_F_VF_RM(vd, __RISCV_FRM_RNE, VECTLENSP), VECTLENSP);
}
static INLINE vfloat vcast_vf_vi2(vint2 vi) {
  return __riscv_vfcvt_f(vi, VECTLENSP);
}
static INLINE vint2 vcast_vi2_i(int i) {
  return SLEEF_RVV_SP_VCAST_VI2_I(i, VECTLENSP);
}
static INLINE vint2 vrint_vi2_vf(vfloat vf) {
  return SLEEF_RVV_SP_VFNCVT_X_F_VI(vf, __RISCV_FRM_RNE, VECTLENSP);
}
static INLINE vint2 vtruncate_vi2_vf(vfloat vf) {
  return __riscv_vfcvt_rtz_x(vf, VECTLENSP);
}
static INLINE vfloat vtruncate_vf_vf(vfloat vf) {
  return vcast_vf_vi2(vtruncate_vi2_vf(vf));
}


/****************************************/
/* Memory Operations                    */
/****************************************/
static INLINE vfloat vload_vf_p(const float *ptr) {
  return SLEEF_RVV_SP_LOAD_VF(ptr, VECTLENSP);
}
static INLINE vfloat vloadu_vf_p(const float *ptr) {
  return SLEEF_RVV_SP_LOAD_VF(ptr, VECTLENSP);
}
static INLINE void vstore_v_p_vf(float *ptr, vfloat v) {
  __riscv_vse32(ptr, v, VECTLENSP);
}
static INLINE void vstoreu_v_p_vf(float *ptr, vfloat v) {
  __riscv_vse32(ptr, v, VECTLENSP);
}
static INLINE void vstoreu_v_p_vi2(int32_t *ptr, vint2 v) {
  __riscv_vse32(ptr, v, VECTLENSP);
}
static INLINE vfloat vgather_vf_p_vi2(const float *ptr, vint2 vi2) {
  return __riscv_vluxei32(ptr, __riscv_vmul(SLEEF_RVV_SP_VREINTERPRET_VU(vi2), sizeof(float), VECTLENSP), VECTLENSP);
}


/****************************************/
/* Floating-Point Arithmetic            */
/****************************************/
static INLINE vfloat vadd_vf_vf_vf(vfloat x, vfloat y) {
  return __riscv_vfadd(x, y, VECTLENSP);
}
static INLINE vfloat vsub_vf_vf_vf(vfloat x, vfloat y) {
  return __riscv_vfsub(x, y, VECTLENSP);
}
static INLINE vfloat vmul_vf_vf_vf(vfloat x, vfloat y) {
  return __riscv_vfmul(x, y, VECTLENSP);
}
static INLINE vfloat vdiv_vf_vf_vf(vfloat x, vfloat y) {
  return __riscv_vfdiv(x, y, VECTLENSP);
}
static INLINE vfloat vmax_vf_vf_vf(vfloat x, vfloat y) {
  return __riscv_vfmax(x, y, VECTLENSP);
}
static INLINE vfloat vmin_vf_vf_vf(vfloat x, vfloat y) {
  return __riscv_vfmin(x, y, VECTLENSP);
}
static INLINE vfloat vrec_vf_vf(vfloat d) {
  return __riscv_vfdiv(vcast_vf_f(1.0f), d, VECTLENSP);
}
static INLINE vfloat vsqrt_vf_vf(vfloat d) {
  return __riscv_vfsqrt(d, VECTLENSP);
}
#if defined(ENABLE_FMA_SP)
// Multiply accumulate: z = z + x * y
static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) {
  return __riscv_vfmadd(x, y, z, VECTLENSP);
}
// Multiply subtract: z = z - x * y
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) {
  return __riscv_vfnmsub(x, y, z, VECTLENSP);
}
static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) {
  return __riscv_vfmsub(x, y, z, VECTLENSP);
}
#else
static INLINE vfloat vmla_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vadd_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }
static INLINE vfloat vmlanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsub_vf_vf_vf(z, vmul_vf_vf_vf(x, y)); }
static INLINE vfloat vmlapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vsub_vf_vf_vf(vmul_vf_vf_vf(x, y), z); }
#endif
// fused multiply add / sub
static INLINE vfloat vfma_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { // (x * y) + z
  return __riscv_vfmadd(x, y, z, VECTLENSP);
}
static INLINE vfloat vfmanp_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { // -(x * y) + z
  return __riscv_vfnmsub(x, y, z, VECTLENSP);
}
static INLINE vfloat vfmapn_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { // (x * y) - z
  return __riscv_vfmsub(x, y, z, VECTLENSP);
}
// sign manipulation
static INLINE vfloat vmulsign_vf_vf_vf(vfloat x, vfloat y) {
  return __riscv_vfsgnjx(x, y, VECTLENSP);
}
static INLINE vfloat vcopysign_vf_vf_vf(vfloat x, vfloat y) {
  return __riscv_vfsgnj(x, y, VECTLENSP);
}
static INLINE vfloat vsign_vf_vf(vfloat f) {
  return __riscv_vfsgnj(SLEEF_RVV_SP_VCAST_VF_F(1.0f, VECTLENSP), f, VECTLENSP);
}
static INLINE vfloat vorsign_vf_vf_vf(vfloat x, vfloat y) {
  vint2 xi = SLEEF_RVV_SP_VREINTERPRET_VI2(x);
  vint2 yi = SLEEF_RVV_SP_VREINTERPRET_VI2(y);
  vint2 xioryi = __riscv_vor(xi, yi, VECTLENSP);
  vfloat xory = SLEEF_RVV_SP_VREINTERPRET_VF(xioryi);
  return __riscv_vfsgnj(x, xory, VECTLENSP);
}
static INLINE vfloat vabs_vf_vf(vfloat f) {
  return __riscv_vfabs(f, VECTLENSP);
}
static INLINE vfloat vneg_vf_vf(vfloat f) {
  return __riscv_vfneg(f, VECTLENSP);
}


/****************************************/
/* Integer Arithmetic and Logic         */
/****************************************/
static INLINE vint2 vadd_vi2_vi2_vi2(vint2 x, vint2 y) {
  return __riscv_vadd(x, y, VECTLENSP);
}
static INLINE vint2 vsub_vi2_vi2_vi2(vint2 x, vint2 y) {
  return __riscv_vsub(x, y, VECTLENSP);
}
static INLINE vint2 vneg_vi2_vi2(vint2 x) {
  return __riscv_vneg(x, VECTLENSP);
}
static INLINE vint2 vand_vi2_vi2_vi2(vint2 x, vint2 y) {
  return __riscv_vand(x, y, VECTLENSP);
}
static INLINE vint2 vandnot_vi2_vi2_vi2(vint2 x, vint2 y) {
  return __riscv_vand(__riscv_vnot(x, VECTLENSP), y, VECTLENSP);
}
static INLINE vint2 vor_vi2_vi2_vi2(vint2 x, vint2 y) {
  return __riscv_vor(x, y, VECTLENSP);
}
static INLINE vint2 vxor_vi2_vi2_vi2(vint2 x, vint2 y) {
  return __riscv_vxor(x, y, VECTLENSP);
}
static INLINE vint2 vsll_vi2_vi2_i(vint2 x, int c) {
  return __riscv_vsll(x, c, VECTLENSP);
}
static INLINE vint2 vsra_vi2_vi2_i(vint2 x, int c) {
  return __riscv_vsra(x, c, VECTLENSP);
}
static INLINE vint2 vsrl_vi2_vi2_i(vint2 x, int c) {
  return SLEEF_RVV_SP_VREINTERPRET_VI2(__riscv_vsrl(SLEEF_RVV_SP_VREINTERPRET_VU2(x), c, VECTLENSP));
}

/****************************************/
/* Bitmask Operations                   */
/****************************************/
static INLINE vfloat vreinterpret_vf_vm(vmask vm) {
  return SLEEF_RVV_SP_VREINTERPRET_VF(SLEEF_RVV_SP_VREINTERPRET_VM(vm));
}
static INLINE vmask vreinterpret_vm_vf(vfloat vf) {
  return SLEEF_RVV_DP_VREINTERPRET_VM(SLEEF_RVV_SP_VREINTERPRET_VM(vf));
}

// These are implementations involving the vopmask type which only work in the
// single-precision case.  Unfortunately this has a type conflict with the
// double-precision implemention, and so a temporary rvv_sp_vopmask type is
// used here and then macro-ed back to vopmask at the end of the file if
// needed.
//
static INLINE int vtestallones_i_vo32(rvv_sp_vopmask g) {
  return __riscv_vcpop(g, VECTLENSP) == VECTLENSP;
}
static INLINE vmask vor_vm_vo32_vm(rvv_sp_vopmask x, vmask y) {
  rvv_vmask32 y32 = SLEEF_RVV_SP_VREINTERPRET_VM(y);
  return SLEEF_RVV_DP_VREINTERPRET_VM(__riscv_vmerge(y32, -1, x, VECTLENSP));
}
static INLINE vmask vand_vm_vo32_vm(rvv_sp_vopmask x, vmask y) {
  rvv_vmask32 y32 = SLEEF_RVV_SP_VREINTERPRET_VM(y);
  return SLEEF_RVV_DP_VREINTERPRET_VM(__riscv_vmerge(y32, 0, __riscv_vmnot(x, VECTLENSP), VECTLENSP));
}
static INLINE vmask vandnot_vm_vo32_vm(rvv_sp_vopmask x, vmask y) {
  rvv_vmask32 y32 = SLEEF_RVV_SP_VREINTERPRET_VM(y);
  return SLEEF_RVV_DP_VREINTERPRET_VM(__riscv_vmerge(y32, 0, x, VECTLENSP));
}


/****************************************/
/* Logical Mask Operations              */
/****************************************/
static INLINE rvv_sp_vopmask rvv_sp_vand_vo_vo_vo(rvv_sp_vopmask x, rvv_sp_vopmask y) {
  return __riscv_vmand(x, y, VECTLENSP);
}
static INLINE rvv_sp_vopmask rvv_sp_vandnot_vo_vo_vo(rvv_sp_vopmask x, rvv_sp_vopmask y) {
  return __riscv_vmandn(y, x, VECTLENSP);
}
static INLINE rvv_sp_vopmask rvv_sp_vor_vo_vo_vo(rvv_sp_vopmask x, rvv_sp_vopmask y) {
  return __riscv_vmor(x, y, VECTLENSP);
}
static INLINE rvv_sp_vopmask rvv_sp_vxor_vo_vo_vo(rvv_sp_vopmask x, rvv_sp_vopmask y) {
  return __riscv_vmxor(x, y, VECTLENSP);
}
// single precision FP comparison
static INLINE rvv_sp_vopmask veq_vo_vf_vf(vfloat x, vfloat y) {
  return __riscv_vmfeq(x, y, VECTLENSP);
}
static INLINE rvv_sp_vopmask vneq_vo_vf_vf(vfloat x, vfloat y) {
  return __riscv_vmfne(x, y, VECTLENSP);
}
static INLINE rvv_sp_vopmask vgt_vo_vf_vf(vfloat x, vfloat y) {
  return __riscv_vmfgt(x, y, VECTLENSP);
}
static INLINE rvv_sp_vopmask vge_vo_vf_vf(vfloat x, vfloat y) {
  return __riscv_vmfge(x, y, VECTLENSP);
}
static INLINE rvv_sp_vopmask vlt_vo_vf_vf(vfloat x, vfloat y) {
  return __riscv_vmflt(x, y, VECTLENSP);
}
static INLINE rvv_sp_vopmask vle_vo_vf_vf(vfloat x, vfloat y) {
  return __riscv_vmfle(x, y, VECTLENSP);
}
static INLINE rvv_sp_vopmask visnan_vo_vf(vfloat d) {
  return __riscv_vmfne(d, d, VECTLENSP);
}
static INLINE rvv_sp_vopmask visinf_vo_vf(vfloat d) {
  return __riscv_vmfeq(__riscv_vfabs(d, VECTLENSP), SLEEF_INFINITYf, VECTLENSP);
}
static INLINE rvv_sp_vopmask vispinf_vo_vf(vfloat d) {
  return __riscv_vmfeq(d, SLEEF_INFINITYf, VECTLENSP);
}
// conditional select
static INLINE vfloat vsel_vf_vo_vf_vf(rvv_sp_vopmask mask, vfloat x, vfloat y) {
  return __riscv_vmerge(y, x, mask, VECTLENSP);
}
static INLINE vfloat vsel_vf_vo_f_f(rvv_sp_vopmask mask, float v1, float v0) {
  return __riscv_vfmerge(vcast_vf_f(v0), v1, mask, VECTLENSP);
}
static INLINE vfloat vsel_vf_vo_vo_f_f_f(rvv_sp_vopmask o0, rvv_sp_vopmask o1, float d0, float d1, float d2) {
  return __riscv_vfmerge(__riscv_vfmerge(vcast_vf_f(d2), d1, o1, VECTLENSP), d0, o0, VECTLENSP);
}
static INLINE vfloat vsel_vf_vo_vo_vo_f_f_f_f(rvv_sp_vopmask o0, rvv_sp_vopmask o1, rvv_sp_vopmask o2, float d0, float d1, float d2, float d3) {
  return __riscv_vfmerge(__riscv_vfmerge(__riscv_vfmerge(vcast_vf_f(d3), d2, o2, VECTLENSP), d1, o1, VECTLENSP), d0, o0, VECTLENSP);
}
// integer comparison
static INLINE rvv_sp_vopmask veq_vo_vi2_vi2(vint2 x, vint2 y) {
  return __riscv_vmseq(x, y, VECTLENSP);
}
static INLINE rvv_sp_vopmask vgt_vo_vi2_vi2(vint2 x, vint2 y) {
  return __riscv_vmsgt(x, y, VECTLENSP);
}
static INLINE vint2 vgt_vi2_vi2_vi2(vint2 x, vint2 y) {
  vint2 zero = vcast_vi2_i(0);
  return __riscv_vmerge(zero, -1, __riscv_vmsgt(x, y, VECTLENSP), VECTLENSP);
}
// integer conditional select
static INLINE vint2 vsel_vi2_vo_vi2_vi2(rvv_sp_vopmask m, vint2 x, vint2 y) {
  return __riscv_vmerge(y, x, m, VECTLENSP);
}
static INLINE vint2 vand_vi2_vo_vi2(rvv_sp_vopmask x, vint2 y) {
  return __riscv_vmerge(y, 0, __riscv_vmnot(x, VECTLENSP), VECTLENSP);
}


////////////////////////////////////////////////////////////////////////////////
// Double-Precision Functions
////////////////////////////////////////////////////////////////////////////////

/****************************************/
/* Multi-value and multi-word types     */
/****************************************/
// vdouble2 type
static INLINE const vdouble vd2getx_vd_vd2(vdouble2 v) {
  return SLEEF_RVV_DP_VGET_VD(v, 0);
}
static INLINE const vdouble vd2gety_vd_vd2(vdouble2 v) {
  return SLEEF_RVV_DP_VGET_VD(v, 1);
}
static INLINE const vdouble2 vd2setxy_vd2_vd_vd(vdouble x, vdouble y) {
  return SLEEF_RVV_DP_VCREATE_VD2(x, y);
}
static INLINE const vdouble2 vd2setx_vd2_vd2_vd(vdouble2 v, vdouble d) {
  return __riscv_vset(v, 0, d);
}
static INLINE const vdouble2 vd2sety_vd2_vd2_vd(vdouble2 v, vdouble d) {
  return __riscv_vset(v, 1, d);
}
// dd2 type
static dd2 dd2setab_dd2_vd2_vd2(vdouble2 a, vdouble2 b) {
  return SLEEF_RVV_DP_VCREATE_4VD(a, b);
}
static vdouble2 dd2geta_vd2_dd2(dd2 d) { return SLEEF_RVV_DP_VGET_4VD(d, 0); }
static vdouble2 dd2getb_vd2_dd2(dd2 d) { return SLEEF_RVV_DP_VGET_4VD(d, 1); }
// vdouble3 type
static INLINE vdouble vd3getx_vd_vd3(vdouble3 v) { return SLEEF_RVV_DP_VGET_VD(v, 0); }
static INLINE vdouble vd3gety_vd_vd3(vdouble3 v) { return SLEEF_RVV_DP_VGET_VD(v, 1); }
static INLINE vdouble vd3getz_vd_vd3(vdouble3 v) { return SLEEF_RVV_DP_VGET_VD(v, 2); }
static INLINE vdouble3 vd3setxyz_vd3_vd_vd_vd(vdouble x, vdouble y, vdouble z) {
  return SLEEF_RVV_DP_VCREATE_3VD(x, y, z);
}
static INLINE vdouble3 vd3setx_vd3_vd3_vd(vdouble3 v, vdouble d) { return __riscv_vset(v, 0, d); }
static INLINE vdouble3 vd3sety_vd3_vd3_vd(vdouble3 v, vdouble d) { return __riscv_vset(v, 1, d); }
static INLINE vdouble3 vd3setz_vd3_vd3_vd(vdouble3 v, vdouble d) { return __riscv_vset(v, 2, d); }
// di type
static INLINE vdouble digetd_vd_di(di_t d) {
  vint2 vi = SLEEF_RVV_DP_VGET_VI2(d, 0);
  return SLEEF_RVV_DP_VREINTERPRET_VD(vi);
}
static INLINE vint digeti_vi_di(di_t d) {
  vint2 vi = SLEEF_RVV_DP_VGET_VI2(d, 1);
  return SLEEF_RVV_DP_VLMUL_TRUNC_VI2_TO_VI(vi);
}
static INLINE di_t disetdi_di_vd_vi(vdouble d, vint i) {
  vint2 vd = SLEEF_RVV_DP_VREINTERPRET_VI2(d);
  vint2 vi = SLEEF_RVV_DP_VLMUL_EXT_VI_TO_VI2(i);
  return SLEEF_RVV_DP_VCREATE_DI(vd, vi);
}
// ddi type
static INLINE vdouble2 ddigetdd_vd2_ddi(ddi_t d) {
  return SLEEF_RVV_DP_VGET_VD2(SLEEF_RVV_DP_VREINTERPRET_4VD_8VI(d), 0);
}
static INLINE vint ddigeti_vi_ddi(ddi_t d) {
  vint2 vi2 = SLEEF_RVV_DP_VGET_VI2(d, 2);
  return SLEEF_RVV_DP_VLMUL_TRUNC_VI2_TO_VI(vi2);
}
static INLINE ddi_t ddisetddi_ddi_vd2_vi(vdouble2 v, vint i) {
  di_t vdi = SLEEF_RVV_DP_VREINTERPRET_4VI(v);
  return SLEEF_RVV_DP_VCREATE_DDI(vdi, i);
}
static INLINE ddi_t ddisetdd_ddi_ddi_vd2(ddi_t ddi, vdouble2 v) {
  di_t vdi = SLEEF_RVV_DP_VREINTERPRET_4VI(v);
  return __riscv_vset(ddi, 0, vdi);
}

/****************************************/
/* Type Conversions and Broadcasts      */
/****************************************/
static INLINE vdouble vcast_vd_d(double d) {
  return SLEEF_RVV_DP_VCAST_VD_D(d, VECTLENDP);
}
static INLINE vdouble vcast_vd_vi(vint i) {
  return SLEEF_RVV_DP_VCAST_VD_VI(i);
}
static INLINE vint vcast_vi_i(int32_t i) {
  return SLEEF_RVV_DP_VCAST_VI_I(i, VECTLENDP);
}
static INLINE vint vrint_vi_vd(vdouble vd) {
  return SLEEF_RVV_DP_VFNCVT_X_F_VI(vd, __RISCV_FRM_RNE, VECTLENDP);
}
static INLINE vdouble vrint_vd_vd(vdouble vd) {
  return SLEEF_RVV_DP_VFCVT_F_X_VD(SLEEF_RVV_DP_VFCVT_X_F_VD_RM(vd, __RISCV_FRM_RNE, VECTLENDP), VECTLENDP);
}
static INLINE vint vtruncate_vi_vd(vdouble vd) {
  return __riscv_vfncvt_rtz_x(vd, VECTLENDP);
}
static INLINE vdouble vtruncate_vd_vd(vdouble vd) {
  return vcast_vd_vi(vtruncate_vi_vd(vd));
}


/****************************************/
/* Memory Operations                    */
/****************************************/
static INLINE vdouble vload_vd_p(const double *ptr) {
  return SLEEF_RVV_DP_LOAD_VD(ptr, VECTLENDP);
}
static INLINE vdouble vloadu_vd_p(const double *ptr) {
  return SLEEF_RVV_DP_LOAD_VD(ptr, VECTLENDP);
}
static INLINE vint vloadu_vi_p(int32_t *p) {
  return SLEEF_RVV_DP_LOAD_VI(p, VECTLENDP);
}
static INLINE void vstore_v_p_vd(double *ptr, vdouble v) {
  __riscv_vse64(ptr, v, VECTLENDP);
}
static INLINE void vstoreu_v_p_vd(double *ptr, vdouble v) {
  __riscv_vse64(ptr, v, VECTLENDP);
}
static INLINE void vstoreu_v_p_vi(int32_t *ptr, vint v) {
  __riscv_vse32(ptr, v, VECTLENDP);
}
static INLINE vdouble vgather_vd_p_vi(const double *ptr, vint vi) {
  return __riscv_vluxei64(ptr, __riscv_vwmulu(SLEEF_RVV_DP_VREINTERPRET_VU(vi), sizeof(double), VECTLENDP), VECTLENDP);
}


/****************************************/
/* Floating-Point Arithmetic            */
/****************************************/
static INLINE vdouble vadd_vd_vd_vd(vdouble x, vdouble y) {
  return __riscv_vfadd(x, y, VECTLENDP);
}
static INLINE vdouble vsub_vd_vd_vd(vdouble x, vdouble y) {
  return __riscv_vfsub(x, y, VECTLENDP);
}
static INLINE vdouble vrec_vd_vd(vdouble d) {
  return __riscv_vfdiv(vcast_vd_d(1.0), d, VECTLENDP);
}
static INLINE vdouble vabs_vd_vd(vdouble d) {
  return __riscv_vfabs(d, VECTLENDP);
}
static INLINE vdouble vsqrt_vd_vd(vdouble d) {
  return __riscv_vfsqrt(d, VECTLENDP);
}
static INLINE vdouble vmul_vd_vd_vd(vdouble x, vdouble y) {
  return __riscv_vfmul(x, y, VECTLENDP);
}
static INLINE vdouble vdiv_vd_vd_vd(vdouble x, vdouble y) {
  return __riscv_vfdiv(x, y, VECTLENDP);
}
static INLINE vdouble vmax_vd_vd_vd(vdouble x, vdouble y) {
  return __riscv_vfmax(x, y, VECTLENDP);
}
static INLINE vdouble vmin_vd_vd_vd(vdouble x, vdouble y) {
  return __riscv_vfmin(x, y, VECTLENDP);
}
#if defined(ENABLE_FMA_DP)
// Multiply accumulate: z = z + x * y
static INLINE vdouble vmla_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) {
  return __riscv_vfmadd(x, y, z, VECTLENDP);
}
// Multiply subtract: z = x * y - z
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) {
  return __riscv_vfmsub(x, y, z, VECTLENDP);
}
// z = z - x * y
static INLINE vdouble vmlanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) {
  return __riscv_vfnmsac(z, x, y, VECTLENDP);
}
#else
static INLINE vdouble vmla_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vadd_vd_vd_vd(vmul_vd_vd_vd(x, y), z); }
static INLINE vdouble vmlapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vsub_vd_vd_vd(vmul_vd_vd_vd(x, y), z); }
static INLINE vdouble vmlanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vsub_vd_vd_vd(z, vmul_vd_vd_vd(x, y)); }
#endif
// fused multiply add / sub
static INLINE vdouble vfma_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) {
  return __riscv_vfmadd(x, y, z, VECTLENDP);
}
static INLINE vdouble vfmanp_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) {
  return __riscv_vfnmsub(x, y, z, VECTLENDP);
}
static INLINE vdouble vfmapn_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) {
  return __riscv_vfmsub(x, y, z, VECTLENDP);
}
// sign manipulation
static INLINE vdouble vmulsign_vd_vd_vd(vdouble x, vdouble y) {
  return __riscv_vfsgnjx(x, y, VECTLENDP);
}
static INLINE vdouble vcopysign_vd_vd_vd(vdouble x, vdouble y) {
  return __riscv_vfsgnj(x, y, VECTLENDP);
}
static INLINE vdouble vorsign_vd_vd_vd(vdouble x, vdouble y) {
  return __riscv_vfsgnj(x, SLEEF_RVV_DP_VREINTERPRET_VD(__riscv_vor(SLEEF_RVV_DP_VREINTERPRET_VM(x), SLEEF_RVV_DP_VREINTERPRET_VM(y), VECTLENDP)), VECTLENDP);
}
static INLINE vdouble vneg_vd_vd(vdouble d) {
  return __riscv_vfneg(d, VECTLENDP);
}


/****************************************/
/* Integer Arithmetic and Logic         */
/****************************************/
static INLINE vint vadd_vi_vi_vi(vint x, vint y) {
  return __riscv_vadd(x, y, VECTLENDP);
}
static INLINE vint vsub_vi_vi_vi(vint x, vint y) {
  return __riscv_vsub(x, y, VECTLENDP);
}
static INLINE vint vneg_vi_vi(vint x) {
  return __riscv_vneg(x, VECTLENDP);
}
static INLINE vint vand_vi_vi_vi(vint x, vint y) {
  return __riscv_vand(x, y, VECTLENDP);
}
static INLINE vint vandnot_vi_vi_vi(vint x, vint y) {
  return __riscv_vand(__riscv_vnot(x, VECTLENDP), y, VECTLENDP);
}
static INLINE vint vor_vi_vi_vi(vint x, vint y) {
  return __riscv_vor(x, y, VECTLENDP);
}
static INLINE vint vxor_vi_vi_vi(vint x, vint y) {
  return __riscv_vxor(x, y, VECTLENDP);
}
static INLINE vint vsll_vi_vi_i(vint x, int c) {
  return __riscv_vsll(x, c, VECTLENDP);
}
static INLINE vint vsra_vi_vi_i(vint x, int c) {
  return __riscv_vsra(x, c, VECTLENDP);
}
static INLINE vint vsrl_vi_vi_i(vint x, int c) {
  return SLEEF_RVV_DP_VREINTERPRET_VI(__riscv_vsrl(SLEEF_RVV_DP_VREINTERPRET_VU(x), c, VECTLENDP));
}


/****************************************/
/* Bitmask Operations                   */
/****************************************/
static INLINE vmask vcast_vm_i64(int64_t c) {
  return SLEEF_RVV_DP_VCAST_VM_U(c, VECTLENDP);
}
static INLINE vmask vcast_vm_u64(uint64_t c) {
  return SLEEF_RVV_DP_VCAST_VM_U(c, VECTLENDP);
}
static INLINE vmask vcast_vm_i_i(int64_t h, int64_t l) {
  return SLEEF_RVV_DP_VCAST_VM_U((((uint64_t)h) << 32) | (uint32_t) l, VECTLENDP);
}
static INLINE vmask vcast_vm_vi(vint vi) {
  return SLEEF_RVV_DP_VREINTERPRET_VM(__riscv_vwcvt_x(vi, VECTLENDP));
}
static INLINE vmask vcastu_vm_vi(vint vi) {
  return __riscv_vsll(SLEEF_RVV_DP_VREINTERPRET_VM(__riscv_vwcvt_x(vi, VECTLENDP)), 32, VECTLENDP);
}
static INLINE vint vcastu_vi_vm(vmask vm) {
  return SLEEF_RVV_DP_VREINTERPRET_VI(__riscv_vnsrl(vm, 32, VECTLENDP));
}
static INLINE vint vcast_vi_vm(vmask vm) {
  return SLEEF_RVV_DP_VREINTERPRET_VI(__riscv_vncvt_x(vm, VECTLENDP));
}

// These are the complementary case to the earlier comment about
// rvv_sp_vopmask.
//
static INLINE vmask vand_vm_vo64_vm(rvv_dp_vopmask x, vmask y) {
  return __riscv_vmerge(y, 0, __riscv_vmnot(x, VECTLENDP), VECTLENDP);
}
static INLINE vmask vand_vm_vm_vm(vmask x, vmask y) {
  return __riscv_vand(x, y, VECTLENDP);
}
static INLINE vmask vor_vm_vm_vm(vmask x, vmask y) {
  return __riscv_vor(x, y, VECTLENDP);
}
static INLINE vmask vxor_vm_vm_vm(vmask x, vmask y) {
  return __riscv_vxor(x, y, VECTLENDP);
}
static INLINE vmask vandnot_vm_vm_vm(vmask x, vmask y) {
  return __riscv_vand(__riscv_vnot(x, VECTLENDP), y, VECTLENDP);
}
static INLINE vmask vandnot_vm_vo64_vm(rvv_dp_vopmask x, vmask y) {
  return __riscv_vmerge(y, 0, x, VECTLENDP);
}
static INLINE vmask vsll64_vm_vm_i(vmask mask, int64_t c) {
  return __riscv_vsll(mask, c, VECTLENDP);
}
static INLINE vmask vsub64_vm_vm_vm(vmask x, vmask y) {
  return __riscv_vsub(x, y, VECTLENDP);
}
static INLINE vmask vsrl64_vm_vm_i(vmask mask, int64_t c) {
  return __riscv_vsrl(mask, c, VECTLENDP);
}
static INLINE vmask vadd64_vm_vm_vm(vmask x, vmask y) {
  return __riscv_vadd(x, y, VECTLENDP);
}
static INLINE vmask vor_vm_vo64_vm(rvv_dp_vopmask x, vmask y) {
  return __riscv_vmerge(y, -1, x, VECTLENDP);
}
static INLINE vmask vsel_vm_vo64_vm_vm(rvv_dp_vopmask mask, vmask x, vmask y) {
  return __riscv_vmerge(y, x, mask, VECTLENDP);
}
static INLINE vmask vneg64_vm_vm(vmask mask) {
  return SLEEF_RVV_DP_VREINTERPRET_VM(__riscv_vneg(SLEEF_RVV_DP_VREINTERPRET_VM_SIGNED(mask), VECTLENDP));
}
static INLINE vdouble vreinterpret_vd_vm(vmask vm) {
  return SLEEF_RVV_DP_VREINTERPRET_VD(vm);
}
static INLINE vmask vreinterpret_vm_vd(vdouble vd) {
  return SLEEF_RVV_DP_VREINTERPRET_VM(vd);
}

// vquad type
static INLINE const vmask vqgetx_vm_vq(vquad v) { return SLEEF_RVV_DP_VGET_VM(v, 0); }
static INLINE const vmask vqgety_vm_vq(vquad v) { return SLEEF_RVV_DP_VGET_VM(v, 1); }
static INLINE vquad vqsetxy_vq_vm_vm(vmask x, vmask y) {
  return SLEEF_RVV_DP_VCREATE_VQ(x, y);
}
static INLINE vquad vqsetx_vq_vq_vm(vquad v, vmask x) { return __riscv_vset(v, 0, x); }
static INLINE vquad vqsety_vq_vq_vm(vquad v, vmask y) { return __riscv_vset(v, 1, y); }


/****************************************/
/* Logical Mask Operations              */
/****************************************/
static INLINE rvv_dp_vopmask vcast_vo64_vo32(rvv_dp_vopmask vo) {
  return vo;
}
static INLINE rvv_dp_vopmask vcast_vo32_vo64(rvv_dp_vopmask vo) {
  return vo;
}
static INLINE rvv_dp_vopmask rvv_dp_vand_vo_vo_vo(rvv_dp_vopmask x, rvv_dp_vopmask y) {
  return __riscv_vmand(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask rvv_dp_vandnot_vo_vo_vo(rvv_dp_vopmask x, rvv_dp_vopmask y) {
  return __riscv_vmandn(y, x, VECTLENDP);
}
static INLINE rvv_dp_vopmask rvv_dp_vor_vo_vo_vo(rvv_dp_vopmask x, rvv_dp_vopmask y) {
  return __riscv_vmor(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask rvv_dp_vxor_vo_vo_vo(rvv_dp_vopmask x, rvv_dp_vopmask y) {
  return __riscv_vmxor(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask veq64_vo_vm_vm(vmask x, vmask y) {
  return __riscv_vmseq(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask vgt64_vo_vm_vm(vmask x, vmask y) {
  return __riscv_vmsgt(SLEEF_RVV_DP_VREINTERPRET_VM_SIGNED(x), SLEEF_RVV_DP_VREINTERPRET_VM_SIGNED(y), VECTLENDP);
}
// double-precision comparison
static INLINE rvv_dp_vopmask visinf_vo_vd(vdouble d) {
  return __riscv_vmfeq(__riscv_vfabs(d, VECTLENDP), SLEEF_INFINITY, VECTLENDP);
}
static INLINE rvv_dp_vopmask vispinf_vo_vd(vdouble d) {
  return __riscv_vmfeq(d, SLEEF_INFINITY, VECTLENDP);
}
static INLINE rvv_dp_vopmask veq_vo_vd_vd(vdouble x, vdouble y) {
  return __riscv_vmfeq(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask vneq_vo_vd_vd(vdouble x, vdouble y) {
  return __riscv_vmfne(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask vlt_vo_vd_vd(vdouble x, vdouble y) {
  return __riscv_vmflt(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask vle_vo_vd_vd(vdouble x, vdouble y) {
  return __riscv_vmfle(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask vgt_vo_vd_vd(vdouble x, vdouble y) {
  return __riscv_vmfgt(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask vge_vo_vd_vd(vdouble x, vdouble y) {
  return __riscv_vmfge(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask visnan_vo_vd(vdouble d) {
  return __riscv_vmfne(d, d, VECTLENDP);
}
// double-precision conditional select
static INLINE vdouble vsel_vd_vo_vd_vd(rvv_dp_vopmask mask, vdouble x, vdouble y) {
  return __riscv_vmerge(y, x, mask, VECTLENDP);
}
static INLINE vdouble vsel_vd_vo_d_d(rvv_dp_vopmask mask, double v0, double v1) {
  return __riscv_vfmerge(vcast_vd_d(v1), v0, mask, VECTLENDP);
}
static INLINE vdouble vsel_vd_vo_vo_d_d_d(rvv_dp_vopmask o0, rvv_dp_vopmask o1, double d0, double d1, double d2) {
  return __riscv_vfmerge(__riscv_vfmerge(vcast_vd_d(d2), d1, o1, VECTLENDP), d0, o0, VECTLENDP);
}
static INLINE vdouble vsel_vd_vo_vo_vo_d_d_d_d(rvv_dp_vopmask o0, rvv_dp_vopmask o1, rvv_dp_vopmask o2, double d0, double d1, double d2, double d3) {
  return __riscv_vfmerge(__riscv_vfmerge(__riscv_vfmerge(vcast_vd_d(d3), d2, o2, VECTLENDP), d1, o1, VECTLENDP), d0, o0, VECTLENDP);
}
static INLINE int vtestallones_i_vo64(rvv_dp_vopmask g) {
  return __riscv_vcpop(g, VECTLENDP) == VECTLENDP;
}
// integer comparison
static INLINE rvv_dp_vopmask veq_vo_vi_vi(vint x, vint y) {
  return __riscv_vmseq(x, y, VECTLENDP);
}
static INLINE rvv_dp_vopmask vgt_vo_vi_vi(vint x, vint y) {
  return __riscv_vmsgt(x, y, VECTLENDP);
}
static INLINE vint vgt_vi_vi_vi(vint x, vint y) {
  vint zero = vcast_vi_i(0);
  return __riscv_vmerge(zero, -1, __riscv_vmsgt(x, y, VECTLENDP), VECTLENDP);
}
// integer conditional select
static INLINE vint vsel_vi_vo_vi_vi(rvv_dp_vopmask m, vint x, vint y) {
  return __riscv_vmerge(y, x, m, VECTLENDP);
}
static INLINE vint vandnot_vi_vo_vi(rvv_dp_vopmask mask, vint vi) {
  return __riscv_vmerge(vi, 0, mask, VECTLENDP);
}
static INLINE vint vand_vi_vo_vi(rvv_dp_vopmask x, vint y) {
  return __riscv_vmerge(y, 0, __riscv_vmnot(x, VECTLENDP), VECTLENDP);
}

/****************************************/
/* DFT Operations                       */
/****************************************/

static INLINE vdouble vposneg_vd_vd(vdouble d) {
  rvv_dp_vopmask mask = SLEEF_RVV_DP_VREINTERPRET_VOM(__riscv_vmv_v_x_u8m1(0x55, __riscv_vsetvlmax_e8m1()));
  vdouble nd = __riscv_vfneg(d, VECTLENDP);
  return __riscv_vmerge(nd, d, mask, VECTLENDP);
}

static INLINE vdouble vnegpos_vd_vd(vdouble d) {
  rvv_dp_vopmask mask = SLEEF_RVV_DP_VREINTERPRET_VOM(__riscv_vmv_v_x_u8m1(0xaa, __riscv_vsetvlmax_e8m1()));
  vdouble nd = __riscv_vfneg(d, VECTLENDP);
  return __riscv_vmerge(nd, d, mask, VECTLENDP);
}

static INLINE vfloat vposneg_vf_vf(vfloat d) {
  rvv_sp_vopmask mask = SLEEF_RVV_SP_VREINTERPRET_VOM(__riscv_vmv_v_x_u8m1(0x55, __riscv_vsetvlmax_e8m1()));
  vfloat nd = __riscv_vfneg(d, VECTLENSP);
  return __riscv_vmerge(nd, d, mask, VECTLENSP);
}

static INLINE vfloat vnegpos_vf_vf(vfloat d) {
  rvv_sp_vopmask mask = SLEEF_RVV_SP_VREINTERPRET_VOM(__riscv_vmv_v_x_u8m1(0xaa, __riscv_vsetvlmax_e8m1()));
  vfloat nd = __riscv_vfneg(d, VECTLENSP);
  return __riscv_vmerge(nd, d, mask, VECTLENSP);
}

static INLINE vdouble vsubadd_vd_vd_vd(vdouble x, vdouble y) { return vadd_vd_vd_vd(x, vnegpos_vd_vd(y)); }
static INLINE vfloat vsubadd_vf_vf_vf(vfloat d0, vfloat d1) { return vadd_vf_vf_vf(d0, vnegpos_vf_vf(d1)); }
static INLINE vdouble vmlsubadd_vd_vd_vd_vd(vdouble x, vdouble y, vdouble z) { return vfma_vd_vd_vd_vd(x, y, vnegpos_vd_vd(z)); }
static INLINE vfloat vmlsubadd_vf_vf_vf_vf(vfloat x, vfloat y, vfloat z) { return vfma_vf_vf_vf_vf(x, y, vnegpos_vf_vf(z)); }

//

static INLINE vdouble vrev21_vd_vd(vdouble vd) {
  rvv_dp_vuint2 id = SLEEF_RVV_DP_VID();
  id = __riscv_vxor(id, 1, VECTLENDP);
  return __riscv_vrgather(vd, id, VECTLENDP);
}

static INLINE vfloat vrev21_vf_vf(vfloat vf) {
  vint2 id = SLEEF_RVV_SP_VREINTERPRET_VI2(SLEEF_RVV_SP_VID());
  id = __riscv_vxor(id, 1, VECTLENSP);
  return __riscv_vrgather(vf, SLEEF_RVV_SP_VREINTERPRET_VU2(id), VECTLENSP);
}

static INLINE vdouble vreva2_vd_vd(vdouble vd) {
  rvv_dp_vuint2 id = SLEEF_RVV_DP_VID();
  id = __riscv_vxor(id, VECTLENDP - 2, VECTLENDP);
  return __riscv_vrgather(vd, id, VECTLENDP);
}

static INLINE vfloat vreva2_vf_vf(vfloat vf) {
  vint2 id = SLEEF_RVV_SP_VREINTERPRET_VI2(SLEEF_RVV_SP_VID());
  id = __riscv_vxor(id, VECTLENSP - 2, VECTLENSP);
  return __riscv_vrgather(vf, SLEEF_RVV_SP_VREINTERPRET_VU2(id), VECTLENSP);
}

//

static INLINE void vscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) {
  // Address generation for this operation turned out to be overly complex when
  // you consider that the loop processes 128 bits per iteration and will
  // probably only iterate 2 or 4 times.
  //
  ptr += offset * 2;
  for (int i = 0; i < VECTLENDP; i += 2) {
    // PROTIP: Avoid modifying `v` within the loop, and just extract the useful
    // part directly in each iteration, because we can.  This avoids a
    // loop-carried dependency.
    //
    vdouble vv = __riscv_vslidedown(v, i, 2);
    __riscv_vse64(ptr, vv, 2);
    ptr += step * 2;
  }
}

static INLINE void vscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) {
  // as above re: looping
  ptr += offset * 2;
  for (int i = 0; i < VECTLENSP; i += 2) {
    vfloat vv = __riscv_vslidedown(v, i, 2);
    __riscv_vse32(ptr, vv, 2);
    ptr += step * 2;
  }
}


/****************************************/
/* Quad Operations                      */
/****************************************/


static INLINE vmask tdxgete_vm_tdx(tdx t) {
  return SLEEF_RVV_DP_VREINTERPRET_VM(SLEEF_RVV_DP_VGET_VD(t, 0));
}
static INLINE vdouble tdxgetd3x_vd_tdx(tdx t) {
  return SLEEF_RVV_DP_VGET_VD(t, 1);
}
static INLINE vdouble tdxgetd3y_vd_tdx(tdx t) {
  return SLEEF_RVV_DP_VGET_VD(t, 2);
}
static INLINE vdouble tdxgetd3z_vd_tdx(tdx t) {
  return SLEEF_RVV_DP_VGET_VD(t, 3);
}
static INLINE tdx tdxsete_tdx_tdx_vm(tdx t, vmask e) {
  return __riscv_vset(t, 0, SLEEF_RVV_DP_VREINTERPRET_VD(e));
}
static INLINE tdx tdxsetx_tdx_tdx_vd(tdx t, vdouble x) {
  return __riscv_vset(t, 1, x);
}
static INLINE tdx tdxsety_tdx_tdx_vd(tdx t, vdouble y) {
  return __riscv_vset(t, 2, y);
}
static INLINE tdx tdxsetz_tdx_tdx_vd(tdx t, vdouble z) {
  return __riscv_vset(t, 3, z);
}

static INLINE vdouble3 tdxgetd3_vd3_tdx(tdx t) {
  return vd3setxyz_vd3_vd_vd_vd(tdxgetd3x_vd_tdx(t), tdxgetd3y_vd_tdx(t), tdxgetd3z_vd_tdx(t));
}

static INLINE tdx tdxsetxyz_tdx_tdx_vd_vd_vd(tdx t, vdouble x, vdouble y, vdouble z) {
  t = tdxsetx_tdx_tdx_vd(t, x);
  t = tdxsety_tdx_tdx_vd(t, y);
  t = tdxsetz_tdx_tdx_vd(t, z);
  return t;
}
static INLINE tdx tdxsetd3_tdx_tdx_vd3(tdx t, vdouble3 d3) {
  return tdxsetxyz_tdx_tdx_vd_vd_vd(t, vd3getx_vd_vd3(d3), vd3gety_vd_vd3(d3), vd3getz_vd_vd3(d3));
}

static INLINE tdx tdxseted3_tdx_vm_vd3(vmask e, vdouble3 d3) {
  return SLEEF_RVV_DP_VCREATE_TD(SLEEF_RVV_DP_VREINTERPRET_VD(e),
            vd3getx_vd_vd3(d3), vd3gety_vd_vd3(d3), vd3getz_vd_vd3(d3));
}
static INLINE tdx tdxsetexyz_tdx_vm_vd_vd_vd(vmask e, vdouble x, vdouble y, vdouble z) {
  return SLEEF_RVV_DP_VCREATE_TD(SLEEF_RVV_DP_VREINTERPRET_VD(e), x, y, z);
}

static INLINE vdouble tdigetx_vd_tdi(tdi_t d) {
  return SLEEF_RVV_DP_VGET_VD(d, 0);
}
static INLINE vdouble tdigety_vd_tdi(tdi_t d) {
  return SLEEF_RVV_DP_VGET_VD(d, 1);
}
static INLINE vdouble tdigetz_vd_tdi(tdi_t d) {
  return SLEEF_RVV_DP_VGET_VD(d, 2);
}

static INLINE vint tdigeti_vi_tdi(tdi_t d) {
  vdouble vd = SLEEF_RVV_DP_VGET_VD(d, 3);
  vint2 vi2 = SLEEF_RVV_DP_VREINTERPRET_VI2(vd);
  vint vi = SLEEF_RVV_DP_VLMUL_TRUNC_VI2_TO_VI(vi2);
  return vi;
}
static INLINE tdi_t tdisetx_tdi_tdi_vd(tdi_t t, vdouble x) {
  return __riscv_vset(t, 0, x);
}
static INLINE tdi_t tdisety_tdi_tdi_vd(tdi_t t, vdouble y) {
  return __riscv_vset(t, 1, y);
}
static INLINE tdi_t tdisetz_tdi_tdi_vd(tdi_t t, vdouble z) {
  return __riscv_vset(t, 2, z);
}
static INLINE tdi_t tdiseti_tdi_tdi_vi(tdi_t t, vint i) {
  vint2 vi2 = SLEEF_RVV_DP_VLMUL_EXT_VI_TO_VI2(i);
  vdouble vd = SLEEF_RVV_DP_VREINTERPRET_VD(vi2);
  return __riscv_vset(t, 3, vd);
}

static INLINE vdouble3 tdigettd_vd3_tdi(tdi_t d) {
  return vd3setxyz_vd3_vd_vd_vd(tdigetx_vd_tdi(d), tdigety_vd_tdi(d), tdigetz_vd_tdi(d));
}
static INLINE tdi_t tdisettd_tdi_tdi_vd3(tdi_t tdi, vdouble3 v) {
  tdi = tdisetx_tdi_tdi_vd(tdi, vd3getx_vd_vd3(v));
  tdi = tdisety_tdi_tdi_vd(tdi, vd3gety_vd_vd3(v));
  tdi = tdisetz_tdi_tdi_vd(tdi, vd3getz_vd_vd3(v));
  return tdi;
}
static INLINE tdi_t tdisettdi_tdi_vd3_vi(vdouble3 v, vint i) {
  tdi_t ret = SLEEF_RVV_DP_VCREATE_TD(vd3getx_vd_vd3(v), vd3gety_vd_vd3(v), vd3getz_vd_vd3(v), vd3getz_vd_vd3(v));
  return tdiseti_tdi_tdi_vi(ret, i);
}


static INLINE rvv_dp_vopmask vcast_vo_i(int i) {
  return SLEEF_RVV_DP_VREINTERPRET_VOM(__riscv_vmv_v_x_u32m1(i, VECTLENSP));
}
static INLINE vmask vreinterpret_vm_vi64(vint64 v)  {
  return SLEEF_RVV_DP_VREINTERPRET_VM(v);
}
static INLINE vint64 vreinterpret_vi64_vm(vmask m)  {
  return SLEEF_RVV_DP_VREINTERPRET_VI64(m);
}
static INLINE vmask vreinterpret_vm_vu64(vuint64 v) {
  return v;
}
static INLINE vuint64 vreinterpret_vu64_vm(vmask m) {
  return m;
}
static INLINE int vtestallzeros_i_vo64(rvv_dp_vopmask g) {
   return __riscv_vcpop(g, VECTLENDP) == 0;
}


static INLINE void vstream_v_p_vd(double *ptr, vdouble v) { vstore_v_p_vd(ptr, v); }
static INLINE void vstream_v_p_vf(float *ptr, vfloat v) { vstore_v_p_vf(ptr, v); }
static INLINE void vsscatter2_v_p_i_i_vd(double *ptr, int offset, int step, vdouble v) { vscatter2_v_p_i_i_vd(ptr, offset, step, v); }
static INLINE void vsscatter2_v_p_i_i_vf(float *ptr, int offset, int step, vfloat v) { vscatter2_v_p_i_i_vf(ptr, offset, step, v); }

// These functions are for debugging
static double vcast_d_vd(vdouble v) {
  return __riscv_vfmv_f(v);
}

static float vcast_f_vf(vfloat v) {
  return __riscv_vfmv_f(v);
}

static int vcast_i_vi(vint v) {
  return __riscv_vmv_x(v);
}

static int vcast_i_vi2(vint2 v) {
  return __riscv_vmv_x(v);
}

//

static vquad loadu_vq_p(const int32_t *ptr) {
  // We have a lot of vreinterprets, here.  It's a side effect of this being a
  // corner case, and the intrinsics specification not supporting direct
  // casting between arbitrary types.  It's necessary to take several
  // deliberate steps; first switching signed to unsigned, then changing the
  // data width of the lanes.
  //
  return SLEEF_RVV_DP_VREINTERPRET_VQ(SLEEF_RVV_DP_VREINTERPRET_4VU(SLEEF_RVV_SP_LOAD_2VI(ptr, VECTLENSP * 2)));
}

static INLINE vquad cast_vq_aq(vargquad aq) { return aq; }
static INLINE vargquad cast_aq_vq(vquad vq) { return vq; }

static INLINE void vprefetch_v_p(const void *ptr) {}


/****************************************/
/* RVV_SP and RVV_DP reconciliation     */
/****************************************/

// About the RISC-V Vector type translations:
//
// Because the single- and double-precision versions of the RVV port have
// conflicting definitions of the vopmask type, they can only
// be defined for at most one precision level in a single translation unit.
// Any functions that use vopmask type are thus given unique names and
// then mapped to the public interface according to the corresponding
// ENABLE_RVV_SP or ENABLE_RVV_DP macro guards.
//
// This is done at the end of the file to avoid unintentional references to
// the public names internally.

#if defined(ENABLE_RVV_SP) && defined(ENABLE_RVV_DP)
#error Cannot simultaneously define ENABLE_RVV_SP and ENABLE_RVV_DP
#endif

// Types and functions that conflict with ENABLE_RVV_DP
#ifdef ENABLE_RVV_SP
#define vopmask rvv_sp_vopmask

#define vand_vo_vo_vo    rvv_sp_vand_vo_vo_vo
#define vandnot_vo_vo_vo rvv_sp_vandnot_vo_vo_vo
#define vor_vo_vo_vo     rvv_sp_vor_vo_vo_vo
#define vxor_vo_vo_vo    rvv_sp_vxor_vo_vo_vo
#endif  // ENABLE_RVV_SP

//@#ifdef ENABLE_RVV_SP
//@#define vopmask rvv_sp_vopmask
//
//@#define vand_vo_vo_vo    rvv_sp_vand_vo_vo_vo
//@#define vandnot_vo_vo_vo rvv_sp_vandnot_vo_vo_vo
//@#define vor_vo_vo_vo     rvv_sp_vor_vo_vo_vo
//@#define vxor_vo_vo_vo    rvv_sp_vxor_vo_vo_vo
//@#endif  // ENABLE_RVV_SP

// Types and functions that conflict with ENABLE_RVV_SP
#ifdef ENABLE_RVV_DP
#define vopmask rvv_dp_vopmask

#define vand_vo_vo_vo    rvv_dp_vand_vo_vo_vo
#define vandnot_vo_vo_vo rvv_dp_vandnot_vo_vo_vo
#define vor_vo_vo_vo     rvv_dp_vor_vo_vo_vo
#define vxor_vo_vo_vo    rvv_dp_vxor_vo_vo_vo
#endif  // ENABLE_RVV_DP

//@#ifdef ENABLE_RVV_DP
//@#define vopmask rvv_dp_vopmask
//
//@#define vand_vo_vo_vo    rvv_dp_vand_vo_vo_vo
//@#define vandnot_vo_vo_vo rvv_dp_vandnot_vo_vo_vo
//@#define vor_vo_vo_vo     rvv_dp_vor_vo_vo_vo
//@#define vxor_vo_vo_vo    rvv_dp_vxor_vo_vo_vo
//@#endif  // ENABLE_RVV_DP

#endif // HELPERRVV_H
