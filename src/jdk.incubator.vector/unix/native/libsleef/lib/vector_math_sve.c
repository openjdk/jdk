/*
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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

#if defined(__ARM_FEATURE_SVE)

#include <stdint.h>
#include <arm_sve.h>

#include "../generated/misc.h"
#include "../generated/sleefinline_sve.h"


#include <jni.h>

#define DEFINE_VECTOR_MATH_UNARY_SVE(op, type) \
JNIEXPORT                                      \
type op##sve(type input) {                     \
  return Sleef_##op##sve(input);               \
}

#define DEFINE_VECTOR_MATH_BINARY_SVE(op, type) \
JNIEXPORT                                       \
type op##sve(type input1, type input2) {        \
  return Sleef_##op##sve(input1, input2);       \
}

DEFINE_VECTOR_MATH_UNARY_SVE(tanfx_u10,   svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(sinfx_u10,   svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(sinhfx_u10,  svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(cosfx_u10,   svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(coshfx_u10,  svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(asinfx_u10,  svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(acosfx_u10,  svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(atanfx_u10,  svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(cbrtfx_u10,  svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(logfx_u10,   svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(log10fx_u10, svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(log1pfx_u10, svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(expfx_u10,   svfloat32_t)
DEFINE_VECTOR_MATH_UNARY_SVE(expm1fx_u10, svfloat32_t)

DEFINE_VECTOR_MATH_UNARY_SVE(tandx_u10,   svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(sindx_u10,   svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(sinhdx_u10,  svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(cosdx_u10,   svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(coshdx_u10,  svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(asindx_u10,  svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(acosdx_u10,  svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(atandx_u10,  svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(cbrtdx_u10,  svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(logdx_u10,   svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(log10dx_u10, svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(log1pdx_u10, svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(expdx_u10,   svfloat64_t)
DEFINE_VECTOR_MATH_UNARY_SVE(expm1dx_u10, svfloat64_t)

DEFINE_VECTOR_MATH_BINARY_SVE(atan2fx_u10, svfloat32_t)
DEFINE_VECTOR_MATH_BINARY_SVE(powfx_u10,   svfloat32_t)
DEFINE_VECTOR_MATH_BINARY_SVE(hypotfx_u05, svfloat32_t)

DEFINE_VECTOR_MATH_BINARY_SVE(atan2dx_u10, svfloat64_t)
DEFINE_VECTOR_MATH_BINARY_SVE(powdx_u10,   svfloat64_t)
DEFINE_VECTOR_MATH_BINARY_SVE(hypotdx_u05, svfloat64_t)

#undef DEFINE_VECTOR_MATH_UNARY_SVE

#undef DEFINE_VECTOR_MATH_BINARY_SVE

#endif // __ARM_FEATURE_SVE
