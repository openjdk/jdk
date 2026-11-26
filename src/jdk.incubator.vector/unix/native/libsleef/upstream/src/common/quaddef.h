//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if !defined(SLEEF_GENHEADER)

#include "sleef-config.h"

#if !defined(Sleef_quad_DEFINED)
#define Sleef_quad_DEFINED
typedef struct { uint64_t x, y; } Sleef_uint64_2t;
#if defined(SLEEF_FLOAT128_IS_IEEEQP) || defined(ENABLEFLOAT128)
typedef __float128 Sleef_quad;
#define SLEEF_QUAD_C(x) (x ## Q)
#elif defined(SLEEF_LONGDOUBLE_IS_IEEEQP)
typedef long double Sleef_quad;
#define SLEEF_QUAD_C(x) (x ## L)
#else
typedef Sleef_uint64_2t Sleef_quad;
#endif
#endif

#if !defined(Sleef_quad1_DEFINED)
#define Sleef_quad1_DEFINED
typedef union {
  struct {
    Sleef_quad x;
  };
  Sleef_quad s[1];
} Sleef_quad1;
#endif

#if !defined(Sleef_quad2_DEFINED)
#define Sleef_quad2_DEFINED
typedef union {
  struct {
    Sleef_quad x, y;
  };
  Sleef_quad s[2];
} Sleef_quad2;
#endif

#if !defined(Sleef_quad4_DEFINED)
#define Sleef_quad4_DEFINED
typedef union {
  struct {
    Sleef_quad x, y, z, w;
  };
  Sleef_quad s[4];
} Sleef_quad4;
#endif

#if !defined(Sleef_quad8_DEFINED)
#define Sleef_quad8_DEFINED
typedef union {
  Sleef_quad s[8];
} Sleef_quad8;
#endif

#if defined(__ARM_FEATURE_SVE) && !defined(Sleef_quadx_DEFINED)
#define Sleef_quadx_DEFINED
typedef union {
  Sleef_quad s[32];
} Sleef_quadx;
#endif


#else // #if !defined(SLEEF_GENHEADER)

SLEEFSHARPif !defined(SLEEFXXXSleef_quad_DEFINED)
SLEEFSHARPdefine SLEEFXXXSleef_quad_DEFINED
typedef struct { uint64_t x, y; } Sleef_uint64_2t;
SLEEFSHARPif defined(SLEEFXXXSLEEF_FLOAT128_IS_IEEEQP)
typedef __float128 Sleef_quad;
SLEEFSHARPdefine SLEEFXXXSLEEF_QUAD_C(x) (x ## Q)
SLEEFSHARPelif defined(SLEEFXXXSLEEF_LONGDOUBLE_IS_IEEEQP)
typedef long double Sleef_quad;
SLEEFSHARPdefine SLEEFXXXSLEEF_QUAD_C(x) (x ## L)
SLEEFSHARPelse
typedef Sleef_uint64_2t Sleef_quad;
SLEEFSHARPendif
SLEEFSHARPendif

#endif // #if !defined(SLEEF_GENHEADER)
