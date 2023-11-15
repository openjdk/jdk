/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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

#include <sleef.h>

#define DEFINE_VECTOR_MATH_UNARY(op, type, cpu) \
type op##cpu(type input) {                      \
  return Sleef_##op##cpu(input);                \
}

DEFINE_VECTOR_MATH_UNARY(tanf4_u10,   float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(tanhf4_u10,  float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(sinf4_u10,   float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(sinhf4_u10,  float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(cosf4_u10,   float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(coshf4_u10,  float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(asinf4_u10,  float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(acosf4_u10,  float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(atanf4_u10,  float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(cbrtf4_u10,  float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(logf4_u10,   float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(log10f4_u10, float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(log1pf4_u10, float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(expf4_u10,   float32x4_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(expm1f4_u10, float32x4_t, advsimd)

DEFINE_VECTOR_MATH_UNARY(tand2_u10,   float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(tanhd2_u10,  float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(sind2_u10,   float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(sinhd2_u10,  float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(cosd2_u10,   float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(coshd2_u10,  float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(asind2_u10,  float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(acosd2_u10,  float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(atand2_u10,  float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(cbrtd2_u10,  float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(logd2_u10,   float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(log10d2_u10, float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(log1pd2_u10, float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(expd2_u10,   float64x2_t, advsimd)
DEFINE_VECTOR_MATH_UNARY(expm1d2_u10, float64x2_t, advsimd)

#ifdef __ARM_FEATURE_SVE
DEFINE_VECTOR_MATH_UNARY(tanfx_u10,   svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(sinfx_u10,   svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(sinhfx_u10,  svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(cosfx_u10,   svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(coshfx_u10,  svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(asinfx_u10,  svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(acosfx_u10,  svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(atanfx_u10,  svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(cbrtfx_u10,  svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(logfx_u10,   svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(log10fx_u10, svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(log1pfx_u10, svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(expfx_u10,   svfloat32_t, sve)
DEFINE_VECTOR_MATH_UNARY(expm1fx_u10, svfloat32_t, sve)

DEFINE_VECTOR_MATH_UNARY(tandx_u10,   svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(sindx_u10,   svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(sinhdx_u10,  svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(cosdx_u10,   svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(coshdx_u10,  svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(asindx_u10,  svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(acosdx_u10,  svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(atandx_u10,  svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(cbrtdx_u10,  svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(logdx_u10,   svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(log10dx_u10, svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(log1pdx_u10, svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(expdx_u10,   svfloat64_t, sve)
DEFINE_VECTOR_MATH_UNARY(expm1dx_u10, svfloat64_t, sve)
#endif /* __ARM_FEATURE_SVE */

#undef DEFINE_VECTOR_MATH_UNARY

#define DEFINE_VECTOR_MATH_BINARY(op, type, cpu) \
type op##cpu(type input1, type input2) {         \
  return Sleef_##op##cpu(input1, input2);        \
}

DEFINE_VECTOR_MATH_BINARY(atan2f4_u10, float32x4_t, advsimd)
DEFINE_VECTOR_MATH_BINARY(powf4_u10,   float32x4_t, advsimd)
DEFINE_VECTOR_MATH_BINARY(hypotf4_u05, float32x4_t, advsimd)

DEFINE_VECTOR_MATH_BINARY(atan2d2_u10, float64x2_t, advsimd)
DEFINE_VECTOR_MATH_BINARY(powd2_u10,   float64x2_t, advsimd)
DEFINE_VECTOR_MATH_BINARY(hypotd2_u05, float64x2_t, advsimd)

#ifdef __ARM_FEATURE_SVE
DEFINE_VECTOR_MATH_BINARY(atan2fx_u10, svfloat32_t, sve)
DEFINE_VECTOR_MATH_BINARY(powfx_u10,   svfloat32_t, sve)
DEFINE_VECTOR_MATH_BINARY(hypotfx_u05, svfloat32_t, sve)

DEFINE_VECTOR_MATH_BINARY(atan2dx_u10, svfloat64_t, sve)
DEFINE_VECTOR_MATH_BINARY(powdx_u10,   svfloat64_t, sve)
DEFINE_VECTOR_MATH_BINARY(hypotdx_u05, svfloat64_t, sve)
#endif /* __ARM_FEATURE_SVE */

#undef DEFINE_VECTOR_MATH_BINARY

