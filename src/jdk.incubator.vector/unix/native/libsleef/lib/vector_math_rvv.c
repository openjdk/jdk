/*
 * Copyright (c) 2024, Rivos Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

// On riscv, sleef vector apis depend on native vector intrinsic, which is supported on
// some compiler, e.g. gcc 14+.
// __riscv_v_intrinsic is used to tell if the compiler supports vector intrinsic.
//
// At compile-time, if the current compiler does support vector intrinsics, bridge
// functions will be built in the library. In case the current compiler doesn't support
// vector intrinsics (gcc < 14), then the bridge functions won't be compiled.
// At run-time, if the library is found and the bridge functions are available in the
// library, then the java vector API will call into the bridge functions and sleef.

#if __GNUC__ >= 14 || (defined(__clang_major__) && __clang_major__ >= 17)

#ifdef __riscv_v_intrinsic

#include <stdint.h>

#include <riscv_vector.h>

#include "../generated/misc.h"
#include "../generated/sleefinline_rvvm1.h"

#include <jni.h>

// We maintain an invariant in java world that default dynamic rounding mode is RNE,
// please check JDK-8330094, JDK-8330266 for more details.
// Currently, sleef source on riscv does not change rounding mode to others except
// of RNE. But we still think it's safer to make sure that after calling into sleef
// the dynamic rounding mode is always RNE.

#ifdef DEBUG
#define CHECK_FRM   __asm__ __volatile__ (     \
    "    frrm   t0              \n\t"          \
    "    beqz   t0, 2f          \n\t"          \
    "    csrrw  x0, cycle, x0   \n\t"          \
    "2:                         \n\t"          \
    : : : "memory" );
#else
#define CHECK_FRM
#endif

#define DEFINE_VECTOR_MATH_UNARY_RVV(op, type) \
JNIEXPORT                                      \
type op##rvv(type input) {                     \
  type res = Sleef_##op##rvvm1(input);         \
  CHECK_FRM                                    \
  return res;                                  \
}

#define DEFINE_VECTOR_MATH_BINARY_RVV(op, type) \
JNIEXPORT                                       \
type op##rvv(type input1, type input2) {        \
  type res = Sleef_##op##rvvm1(input1, input2); \
  CHECK_FRM                                     \
  return res;                                   \
}

DEFINE_VECTOR_MATH_UNARY_RVV(tanfx_u10,   vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(sinfx_u10,   vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(sinhfx_u10,  vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(cosfx_u10,   vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(coshfx_u10,  vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(asinfx_u10,  vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(acosfx_u10,  vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(atanfx_u10,  vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(cbrtfx_u10,  vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(logfx_u10,   vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(log10fx_u10, vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(log1pfx_u10, vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(expfx_u10,   vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(expm1fx_u10, vfloat_rvvm1_sleef)

DEFINE_VECTOR_MATH_UNARY_RVV(tandx_u10,   vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(sindx_u10,   vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(sinhdx_u10,  vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(cosdx_u10,   vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(coshdx_u10,  vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(asindx_u10,  vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(acosdx_u10,  vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(atandx_u10,  vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(cbrtdx_u10,  vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(logdx_u10,   vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(log10dx_u10, vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(log1pdx_u10, vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(expdx_u10,   vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_UNARY_RVV(expm1dx_u10, vdouble_rvvm1_sleef)

DEFINE_VECTOR_MATH_BINARY_RVV(atan2fx_u10, vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_BINARY_RVV(powfx_u10,   vfloat_rvvm1_sleef)
DEFINE_VECTOR_MATH_BINARY_RVV(hypotfx_u05, vfloat_rvvm1_sleef)

DEFINE_VECTOR_MATH_BINARY_RVV(atan2dx_u10, vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_BINARY_RVV(powdx_u10,   vdouble_rvvm1_sleef)
DEFINE_VECTOR_MATH_BINARY_RVV(hypotdx_u05, vdouble_rvvm1_sleef)

#undef DEFINE_VECTOR_MATH_UNARY_RVV

#undef DEFINE_VECTOR_MATH_BINARY_RVV

#endif  /* __riscv_v_intrinsic */

#endif  /* check gcc and clang version */
