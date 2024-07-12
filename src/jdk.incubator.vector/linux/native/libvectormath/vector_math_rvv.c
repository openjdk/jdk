/*
 * Copyright (c) 2024, Rivos Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

// #if defined(__riscv) && defined(__riscv_v)
// #if defined(__riscv_vector)
//
#include <stdint.h>

#include <riscv_vector.h>

#include "misc.h"
#include "sleefinline_rvvm1.h"

// #endif // defined(__riscv) && defined(__riscv_v)

#include <jni.h>

#define DEFINE_VECTOR_MATH_UNARY_RVV(op, type) \
JNIEXPORT                                      \
type op##rvv(type input) {                     \
  return Sleef_##op##rvvm1(input);             \
}

#define DEFINE_VECTOR_MATH_BINARY_RVV(op, type) \
JNIEXPORT                                       \
type op##rvv(type input1, type input2) {        \
  return Sleef_##op##rvvm1(input1, input2);     \
}

// #if defined(__riscv) && defined(__riscv_v)
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
// #endif // defined(__riscv) && defined(__riscv_v)

#undef DEFINE_VECTOR_MATH_UNARY_RVV

#undef DEFINE_VECTOR_MATH_BINARY_RVV


