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

#if defined(__ARM_NEON__) || defined(__ARM_NEON)

#include <stdint.h>
#include <arm_neon.h>

#include "../generated/misc.h"
#include "../generated/sleefinline_advsimd.h"


#include <jni.h>

#define DEFINE_VECTOR_MATH_UNARY(op, type) \
JNIEXPORT                                  \
type op##advsimd(type input) {             \
  return Sleef_##op##advsimd(input);       \
}

#define DEFINE_VECTOR_MATH_BINARY(op, type)   \
JNIEXPORT                                     \
type op##advsimd(type input1, type input2) {  \
  return Sleef_##op##advsimd(input1, input2); \
}

DEFINE_VECTOR_MATH_UNARY(tanf4_u10,   float32x4_t)
DEFINE_VECTOR_MATH_UNARY(tanhf4_u10,  float32x4_t)
DEFINE_VECTOR_MATH_UNARY(sinf4_u10,   float32x4_t)
DEFINE_VECTOR_MATH_UNARY(sinhf4_u10,  float32x4_t)
DEFINE_VECTOR_MATH_UNARY(cosf4_u10,   float32x4_t)
DEFINE_VECTOR_MATH_UNARY(coshf4_u10,  float32x4_t)
DEFINE_VECTOR_MATH_UNARY(asinf4_u10,  float32x4_t)
DEFINE_VECTOR_MATH_UNARY(acosf4_u10,  float32x4_t)
DEFINE_VECTOR_MATH_UNARY(atanf4_u10,  float32x4_t)
DEFINE_VECTOR_MATH_UNARY(cbrtf4_u10,  float32x4_t)
DEFINE_VECTOR_MATH_UNARY(logf4_u10,   float32x4_t)
DEFINE_VECTOR_MATH_UNARY(log10f4_u10, float32x4_t)
DEFINE_VECTOR_MATH_UNARY(log1pf4_u10, float32x4_t)
DEFINE_VECTOR_MATH_UNARY(expf4_u10,   float32x4_t)
DEFINE_VECTOR_MATH_UNARY(expm1f4_u10, float32x4_t)

DEFINE_VECTOR_MATH_UNARY(tand2_u10,   float64x2_t)
DEFINE_VECTOR_MATH_UNARY(tanhd2_u10,  float64x2_t)
DEFINE_VECTOR_MATH_UNARY(sind2_u10,   float64x2_t)
DEFINE_VECTOR_MATH_UNARY(sinhd2_u10,  float64x2_t)
DEFINE_VECTOR_MATH_UNARY(cosd2_u10,   float64x2_t)
DEFINE_VECTOR_MATH_UNARY(coshd2_u10,  float64x2_t)
DEFINE_VECTOR_MATH_UNARY(asind2_u10,  float64x2_t)
DEFINE_VECTOR_MATH_UNARY(acosd2_u10,  float64x2_t)
DEFINE_VECTOR_MATH_UNARY(atand2_u10,  float64x2_t)
DEFINE_VECTOR_MATH_UNARY(cbrtd2_u10,  float64x2_t)
DEFINE_VECTOR_MATH_UNARY(logd2_u10,   float64x2_t)
DEFINE_VECTOR_MATH_UNARY(log10d2_u10, float64x2_t)
DEFINE_VECTOR_MATH_UNARY(log1pd2_u10, float64x2_t)
DEFINE_VECTOR_MATH_UNARY(expd2_u10,   float64x2_t)
DEFINE_VECTOR_MATH_UNARY(expm1d2_u10, float64x2_t)

DEFINE_VECTOR_MATH_BINARY(atan2f4_u10, float32x4_t)
DEFINE_VECTOR_MATH_BINARY(powf4_u10,   float32x4_t)
DEFINE_VECTOR_MATH_BINARY(hypotf4_u05, float32x4_t)

DEFINE_VECTOR_MATH_BINARY(atan2d2_u10, float64x2_t)
DEFINE_VECTOR_MATH_BINARY(powd2_u10,   float64x2_t)
DEFINE_VECTOR_MATH_BINARY(hypotd2_u05, float64x2_t)

#undef DEFINE_VECTOR_MATH_UNARY

#undef DEFINE_VECTOR_MATH_BINARY

#endif // defined(__ARM_NEON__) || defined(__ARM_NEON)
