//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if !defined(SLEEF_GENHEADER)

#if (defined(__SIZEOF_FLOAT128__) && __SIZEOF_FLOAT128__ == 16) || (defined(__linux__) && defined(__GNUC__) && (defined(__i386__) || defined(__x86_64__))) || (defined(__PPC64__) && defined(__GNUC__) && !defined(__clang__) && __GNUC__ >= 8)
#define SLEEF_FLOAT128_IS_IEEEQP
#endif

#if !defined(SLEEF_FLOAT128_IS_IEEEQP) && defined(__SIZEOF_LONG_DOUBLE__) && __SIZEOF_LONG_DOUBLE__ == 16 && (defined(__aarch64__) || defined(__zarch__))
#define SLEEF_LONGDOUBLE_IS_IEEEQP
#endif

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

SLEEFSHARPif !defined(SLEEFXXX__NVCC__) && ((defined(SLEEFXXX__SIZEOF_FLOAT128__) && SLEEFXXX__SIZEOF_FLOAT128__ == 16) || (defined(SLEEFXXX__linux__) && defined(SLEEFXXX__GNUC__) && (defined(SLEEFXXX__i386__) || defined(SLEEFXXX__x86_64__))) || (defined(SLEEFXXX__PPC64__) && defined(SLEEFXXX__GNUC__) && !defined(SLEEFXXX__clang__) && SLEEFXXX__GNUC__ >= 8))
SLEEFSHARPdefine SLEEFXXXSLEEF_FLOAT128_IS_IEEEQP
SLEEFSHARPendif

SLEEFSHARPif !defined(SLEEFXXXSLEEF_FLOAT128_IS_IEEEQP) && !defined(SLEEFXXX__NVCC__) && defined(SLEEFXXX__SIZEOF_LONG_DOUBLE__) && SLEEFXXX__SIZEOF_LONG_DOUBLE__ == 16 && (defined(SLEEFXXX__aarch64__) || defined(SLEEFXXX__zarch__))
SLEEFSHARPdefine SLEEFXXXSLEEF_LONGDOUBLE_IS_IEEEQP
SLEEFSHARPendif

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
