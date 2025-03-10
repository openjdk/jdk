//   Copyright Naoki Shibata and contributors 2010 - 2024.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

//

#ifndef __MISC_H__
#define __MISC_H__

#if !defined(SLEEF_GENHEADER)
#include <stdint.h>
#include <string.h>
#endif

#ifndef M_PI
#define M_PI 3.141592653589793238462643383279502884
#endif

#ifndef M_PIl
#define M_PIl 3.141592653589793238462643383279502884L
#endif

#ifndef M_1_PI
#define M_1_PI 0.318309886183790671537767526745028724
#endif

#ifndef M_1_PIl
#define M_1_PIl 0.318309886183790671537767526745028724L
#endif

#ifndef M_2_PI
#define M_2_PI 0.636619772367581343075535053490057448
#endif

#ifndef M_2_PIl
#define M_2_PIl 0.636619772367581343075535053490057448L
#endif

#if !defined(SLEEF_GENHEADER)

#ifndef SLEEF_FP_ILOGB0
#define SLEEF_FP_ILOGB0 ((int)0x80000000)
#endif

#ifndef SLEEF_FP_ILOGBNAN
#define SLEEF_FP_ILOGBNAN ((int)2147483647)
#endif

#endif

#define SLEEF_SNAN (((union { long long int i; double d; }) { .i = INT64_C(0x7ff0000000000001) }).d)
#define SLEEF_SNANf (((union { long int i; float f; }) { .i = 0xff800001 }).f)

#define SLEEF_FLT_MIN 0x1p-126
#define SLEEF_DBL_MIN 0x1p-1022
#define SLEEF_INT_MAX 2147483647
#define SLEEF_DBL_DENORM_MIN 4.9406564584124654e-324
#define SLEEF_FLT_DENORM_MIN 1.40129846e-45F

//

/*
  PI_A to PI_D are constants that satisfy the following two conditions.

  * For PI_A, PI_B and PI_C, the last 28 bits are zero.
  * PI_A + PI_B + PI_C + PI_D is close to PI as much as possible.

  The argument of a trig function is multiplied by 1/PI, and the
  integral part is divided into two parts, each has at most 28
  bits. So, the maximum argument that could be correctly reduced
  should be 2^(28*2-1) PI = 1.1e+17. However, due to internal
  double precision calculation, the actual maximum argument that can
  be correctly reduced is around 2^47.
 */

#define PI_A 3.1415926218032836914
#define PI_B 3.1786509424591713469e-08
#define PI_C 1.2246467864107188502e-16
#define PI_D 1.2736634327021899816e-24
#define TRIGRANGEMAX 1e+14

/*
  PI_A2 and PI_B2 are constants that satisfy the following two conditions.

  * The last 3 bits of PI_A2 are zero.
  * PI_A2 + PI_B2 is close to PI as much as possible.

  The argument of a trig function is multiplied by 1/PI, and the
  integral part is multiplied by PI_A2. So, the maximum argument that
  could be correctly reduced should be 2^(3-1) PI = 12.6. By testing,
  we confirmed that it correctly reduces the argument up to around 15.
 */

#define PI_A2 3.141592653589793116
#define PI_B2 1.2246467991473532072e-16
#define TRIGRANGEMAX2 15

#define M_2_PI_H 0.63661977236758138243
#define M_2_PI_L -3.9357353350364971764e-17

#define SQRT_DBL_MAX 1.3407807929942596355e+154

#define TRIGRANGEMAX3 1e+9

#define M_4_PI 1.273239544735162542821171882678754627704620361328125

#define L2U .69314718055966295651160180568695068359375
#define L2L .28235290563031577122588448175013436025525412068e-12
#define R_LN2 1.442695040888963407359924681001892137426645954152985934135449406931

#define L10U 0.30102999566383914498 // log 2 / log 10
#define L10L 1.4205023227266099418e-13
#define LOG10_2 3.3219280948873623478703194294893901758648313930

#define L10Uf 0.3010253906f
#define L10Lf 4.605038981e-06f

//

#define PI_Af 3.140625f
#define PI_Bf 0.0009670257568359375f
#define PI_Cf 6.2771141529083251953e-07f
#define PI_Df 1.2154201256553420762e-10f
#define TRIGRANGEMAXf 39000

#define PI_A2f 3.1414794921875f
#define PI_B2f 0.00011315941810607910156f
#define PI_C2f 1.9841872589410058936e-09f
#define TRIGRANGEMAX2f 125.0f

#define TRIGRANGEMAX4f 8e+6f

#define SQRT_FLT_MAX 18446743523953729536.0

#define L2Uf 0.693145751953125f
#define L2Lf 1.428606765330187045e-06f

#define R_LN2f 1.442695040888963407359924681001892137426645954152985934135449406931f
#ifndef M_PIf
# define M_PIf ((float)M_PI)
#endif

//

#ifndef MIN
#define MIN(x, y) ((x) < (y) ? (x) : (y))
#endif

#ifndef MAX
#define MAX(x, y) ((x) > (y) ? (x) : (y))
#endif

#ifndef ABS
#define ABS(x) ((x) < 0 ? -(x) : (x))
#endif

#define stringify(s) stringify_(s)
#define stringify_(s) #s

#if !defined(SLEEF_GENHEADER)
typedef long double longdouble;
#endif

#if !defined(Sleef_double2_DEFINED) && !defined(SLEEF_GENHEADER)
#define Sleef_double2_DEFINED
typedef struct {
  double x, y;
} Sleef_double2;
#endif

#if !defined(Sleef_float2_DEFINED) && !defined(SLEEF_GENHEADER)
#define Sleef_float2_DEFINED
typedef struct {
  float x, y;
} Sleef_float2;
#endif

#if !defined(Sleef_longdouble2_DEFINED) && !defined(SLEEF_GENHEADER)
#define Sleef_longdouble2_DEFINED
typedef struct {
  long double x, y;
} Sleef_longdouble2;
#endif

#if (defined (__GNUC__) || defined (__clang__) || defined(__INTEL_COMPILER)) && !defined(_MSC_VER)

#define LIKELY(condition) __builtin_expect(!!(condition), 1)
#define UNLIKELY(condition) __builtin_expect(!!(condition), 0)
#define RESTRICT __restrict__

#ifndef __arm__
#define ALIGNED(x) __attribute__((aligned(x)))
#else
#define ALIGNED(x)
#endif

#if defined(SLEEF_GENHEADER)

#define INLINE SLEEF_ALWAYS_INLINE
#define EXPORT SLEEF_INLINE
#define CONST SLEEF_CONST
#define NOEXPORT

#else // #if defined(SLEEF_GENHEADER)

#define CONST __attribute__((const))
#define INLINE __attribute__((always_inline))

#if defined(__MINGW32__) || defined(__MINGW64__) || defined(__CYGWIN__)
#ifndef SLEEF_STATIC_LIBS
#define EXPORT __stdcall __declspec(dllexport)
#define NOEXPORT
#else // #ifndef SLEEF_STATIC_LIBS
#define EXPORT
#define NOEXPORT
#endif // #ifndef SLEEF_STATIC_LIBS
#else // #if defined(__MINGW32__) || defined(__MINGW64__) || defined(__CYGWIN__)
#define EXPORT __attribute__((visibility("default")))
#define NOEXPORT __attribute__ ((visibility ("hidden")))
#endif // #if defined(__MINGW32__) || defined(__MINGW64__) || defined(__CYGWIN__)

#endif // #if defined(SLEEF_GENHEADER)

#define SLEEF_NAN __builtin_nan("")
#define SLEEF_NANf __builtin_nanf("")
#define SLEEF_NANl __builtin_nanl("")
#define SLEEF_INFINITY __builtin_inf()
#define SLEEF_INFINITYf __builtin_inff()
#define SLEEF_INFINITYl __builtin_infl()

#if defined(__INTEL_COMPILER) || defined (__clang__)
#define SLEEF_INFINITYq __builtin_inf()
#define SLEEF_NANq __builtin_nan("")
#else
#define SLEEF_INFINITYq __builtin_infq()
#define SLEEF_NANq (SLEEF_INFINITYq - SLEEF_INFINITYq)
#endif

#elif defined(_MSC_VER) // #if (defined (__GNUC__) || defined (__clang__) || defined(__INTEL_COMPILER)) && !defined(_MSC_VER)

#if defined(SLEEF_GENHEADER)

#define INLINE SLEEF_ALWAYS_INLINE
#define CONST SLEEF_CONST
#define EXPORT SLEEF_INLINE
#define NOEXPORT

#else // #if defined(SLEEF_GENHEADER)

#define INLINE __forceinline
#define CONST
#ifndef SLEEF_STATIC_LIBS
#define EXPORT __declspec(dllexport)
#define NOEXPORT
#else
#define EXPORT
#define NOEXPORT
#endif

#endif // #if defined(SLEEF_GENHEADER)

#define RESTRICT
#define ALIGNED(x)
#define LIKELY(condition) (condition)
#define UNLIKELY(condition) (condition)

#if (defined(__GNUC__) || defined(__CLANG__)) && (defined(__i386__) || defined(__x86_64__)) && !defined(SLEEF_GENHEADER)
#include <x86intrin.h>
#endif

#define SLEEF_INFINITY (1e+300 * 1e+300)
#define SLEEF_NAN (SLEEF_INFINITY - SLEEF_INFINITY)
#define SLEEF_INFINITYf ((float)SLEEF_INFINITY)
#define SLEEF_NANf ((float)SLEEF_NAN)
#define SLEEF_INFINITYl ((long double)SLEEF_INFINITY)
#define SLEEF_NANl ((long double)SLEEF_NAN)

#if (defined(_M_AMD64) || defined(_M_X64))
#ifndef __SSE2__
#define __SSE2__
#define __SSE3__
#define __SSE4_1__
#endif
#elif _M_IX86_FP == 2
#ifndef __SSE2__
#define __SSE2__
#define __SSE3__
#define __SSE4_1__
#endif
#elif _M_IX86_FP == 1
#ifndef __SSE__
#define __SSE__
#endif
#endif

#endif // #elif defined(_MSC_VER) // #if (defined (__GNUC__) || defined (__clang__) || defined(__INTEL_COMPILER)) && !defined(_MSC_VER)

#if !defined(__linux__)
#define isinff(x) ((x) == SLEEF_INFINITYf || (x) == -SLEEF_INFINITYf)
#define isinfl(x) ((x) == SLEEF_INFINITYl || (x) == -SLEEF_INFINITYl)
#define isnanf(x) ((x) != (x))
#define isnanl(x) ((x) != (x))
#endif

#endif // #ifndef __MISC_H__

#ifdef ENABLE_AAVPCS
#define VECTOR_CC __attribute__((aarch64_vector_pcs))
#else
#define VECTOR_CC
#endif

//

#if defined (__GNUC__) && !defined(__INTEL_COMPILER)
#pragma GCC diagnostic ignored "-Wpragmas"
#pragma GCC diagnostic ignored "-Wunknown-pragmas"
#if !defined (__clang__)
#pragma GCC diagnostic ignored "-Wattribute-alias"
#pragma GCC diagnostic ignored "-Wlto-type-mismatch"
#pragma GCC diagnostic ignored "-Wstringop-overflow"
#endif
#endif

#if defined(_MSC_VER)
#pragma warning(disable:4101) // warning C4101: 'v': unreferenced local variable
#pragma warning(disable:4116) // warning C4116: unnamed type definition in parentheses
#pragma warning(disable:4244) // warning C4244: 'function': conversion from 'vopmask' to '__mmask8', possible loss of data
#pragma warning(disable:4267) // warning C4267: 'initializing': conversion from 'size_t' to 'const int', possible loss of data
#pragma warning(disable:4305) // warning C4305: 'function': truncation from 'double' to 'float'
#endif
