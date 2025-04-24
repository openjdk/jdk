//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if (defined (__GNUC__) || defined (__clang__) || defined(__INTEL_COMPILER)) && !defined(_MSC_VER)
#define CONST __attribute__((const))
#else
#define CONST
#endif

#if defined(_MSC_VER) || defined(__MINGW32__) || defined(__MINGW64__)
static jmp_buf sigjmp;
#define SETJMP(x) setjmp(x)
#define LONGJMP longjmp
#else
static sigjmp_buf sigjmp;
#define SETJMP(x) sigsetjmp(x, 1)
#define LONGJMP siglongjmp
#endif

static void sighandler(int signum) {
  LONGJMP(sigjmp, 1);
}

static int cpuSupportsExt(void (*tryExt)()) {
  static int cache = -1;
  if (cache != -1) return cache;

  void (*org);
  org = signal(SIGILL, sighandler);

  if (SETJMP(sigjmp) == 0) {
    (*tryExt)();
    cache = 1;
  } else {
    cache = 0;
  }

  signal(SIGILL, org);
  return cache;
}

#ifndef VECALIAS_vf_vf
#define VECALIAS_vf_vf(fptype, funcNameS, funcName, veclen)
#define VECALIAS_vf_vf_vf(fptype, funcNameS, funcName, veclen)
#define VECALIAS_vf_vf_vf_vf(fptype, funcNameS, funcName, veclen)
#endif

/*
 * DISPATCH_R_X, DISPATCH_R_X_Y and DISPATCH_R_X_Y_Z are the macro for
 * defining dispatchers. R, X, Y and Z represent the data types of
 * return value, first argument, second argument and third argument,
 * respectively. vf, vi, i and p correspond to vector FP, vector
 * integer, scalar integer and scalar pointer types, respectively.
 *
 * The arguments for the macros are as follows:
 *   fptype       : FP type name
 *   veclen       : Vector length
 *   funcnameS    : First scalar function name
 *   funcnameS2   : Second scalar function name
 *   funcname     : Fundamental function name
 *   pfn          : Name of pointer of the function to the dispatcher
 *   dfn          : Name of the dispatcher function
 *   funcExt0     : Name of the function for vector extension 0
 *   funcExt1     : Name of the function for vector extension 1
 *   funcExt2     : Name of the function for vector extension 2
 */

#define DISPATCH_vf_vf(fptype, veclen, funcNameS, funcNameS2, funcName, pfn, dfn, funcExt0, funcExt1, funcExt2) \
  static CONST VECTOR_CC fptype (*pfn)(fptype arg0);                    \
  static CONST VECTOR_CC fptype dfn(fptype arg0) {                      \
    fptype CONST VECTOR_CC (*p)(fptype arg0) = funcExt0;                \
    SUBST_IF_EXT1(funcExt1);                                            \
    SUBST_IF_EXT2(funcExt2);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0);                                                \
  }                                                                     \
  static CONST VECTOR_CC fptype (*pfn)(fptype arg0) = dfn;              \
  EXPORT CONST VECTOR_CC fptype funcName(fptype arg0) { return (*pfn)(arg0); } \
  VECALIAS_vf_vf(fptype, funcNameS, funcName, veclen)                   \
  VECALIAS_vf_vf(fptype, funcNameS2, funcName, veclen)

#define DISPATCH_vf_vf_vf(fptype, veclen, funcNameS, funcNameS2, funcName, pfn, dfn, funcExt0, funcExt1, funcExt2) \
  static CONST VECTOR_CC fptype (*pfn)(fptype arg0, fptype arg1);       \
  static CONST VECTOR_CC fptype dfn(fptype arg0, fptype arg1) {         \
    fptype CONST VECTOR_CC (*p)(fptype arg0, fptype arg1) = funcExt0;   \
    SUBST_IF_EXT1(funcExt1);                                            \
    SUBST_IF_EXT2(funcExt2);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1);                                          \
  }                                                                     \
  static CONST VECTOR_CC fptype (*pfn)(fptype arg0, fptype arg1) = dfn; \
  EXPORT CONST VECTOR_CC fptype funcName(fptype arg0, fptype arg1) { return (*pfn)(arg0, arg1); } \
  VECALIAS_vf_vf_vf(fptype, funcNameS, funcName, veclen)                \
  VECALIAS_vf_vf_vf(fptype, funcNameS2, funcName, veclen)

#define DISPATCH_vf2_vf(fptype, fptype2, veclen, funcNameS, funcNameS2, funcName, pfn, dfn, funcExt0, funcExt1, funcExt2) \
  static CONST VECTOR_CC fptype2 (*pfn)(fptype arg0);                   \
  static CONST VECTOR_CC fptype2 dfn(fptype arg0) {                     \
    fptype2 CONST VECTOR_CC (*p)(fptype arg0) = funcExt0;               \
    SUBST_IF_EXT1(funcExt1);                                            \
    SUBST_IF_EXT2(funcExt2);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0);                                                \
  }                                                                     \
  static CONST VECTOR_CC fptype2 (*pfn)(fptype arg0) = dfn;             \
  EXPORT CONST VECTOR_CC fptype2 funcName(fptype arg0) { return (*pfn)(arg0); }

#define DISPATCH_vf_vf_vi(fptype, itype, veclen, funcNameS, funcNameS2, funcName, pfn, dfn, funcExt0, funcExt1, funcExt2) \
  static CONST VECTOR_CC fptype (*pfn)(fptype arg0, itype arg1);        \
  static CONST VECTOR_CC fptype dfn(fptype arg0, itype arg1) {          \
    fptype CONST VECTOR_CC (*p)(fptype arg0, itype arg1) = funcExt0;    \
    SUBST_IF_EXT1(funcExt1);                                            \
    SUBST_IF_EXT2(funcExt2);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1);                                          \
  }                                                                     \
  static CONST VECTOR_CC fptype (*pfn)(fptype arg0, itype arg1) = dfn;  \
  EXPORT CONST VECTOR_CC fptype funcName(fptype arg0, itype arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_vi_vf(fptype, itype, veclen, funcNameS, funcNameS2, funcName, pfn, dfn, funcExt0, funcExt1, funcExt2) \
  static CONST VECTOR_CC itype (*pfn)(fptype arg0);                     \
  static CONST VECTOR_CC itype dfn(fptype arg0) {                       \
    itype CONST VECTOR_CC (*p)(fptype arg0) = funcExt0;                 \
    SUBST_IF_EXT1(funcExt1);                                            \
    SUBST_IF_EXT2(funcExt2);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0);                                                \
  }                                                                     \
  static CONST VECTOR_CC itype (*pfn)(fptype arg0) = dfn;               \
  EXPORT CONST VECTOR_CC itype funcName(fptype arg0) { return (*pfn)(arg0); }

#define DISPATCH_vf_vf_vf_vf(fptype, veclen, funcNameS, funcNameS2, funcName, pfn, dfn, funcExt0, funcExt1, funcExt2) \
  static CONST VECTOR_CC fptype (*pfn)(fptype arg0, fptype arg1, fptype arg2); \
  static CONST VECTOR_CC fptype dfn(fptype arg0, fptype arg1, fptype arg2) { \
    fptype CONST VECTOR_CC (*p)(fptype arg0, fptype arg1, fptype arg2) = funcExt0; \
    SUBST_IF_EXT1(funcExt1);                                            \
    SUBST_IF_EXT2(funcExt2);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1, arg2);                                    \
  }                                                                     \
  static CONST VECTOR_CC fptype (*pfn)(fptype arg0, fptype arg1, fptype arg2) = dfn; \
  EXPORT CONST VECTOR_CC fptype funcName(fptype arg0, fptype arg1, fptype arg2) { return (*pfn)(arg0, arg1, arg2); } \
  VECALIAS_vf_vf_vf_vf(fptype, funcNameS, funcName, veclen)             \
  VECALIAS_vf_vf_vf_vf(fptype, funcNameS2, funcName, veclen)

#define DISPATCH_i_i(veclen, funcNameS, funcNameS2, funcName, pfn, dfn, funcExt0, funcExt1, funcExt2) \
  static CONST int (*pfn)(int arg0);                                    \
  static CONST int dfn(int arg0) {                                      \
    int CONST (*p)(int) = funcExt0;                                     \
    SUBST_IF_EXT1(funcExt1);                                            \
    SUBST_IF_EXT2(funcExt2);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0);                                                \
  }                                                                     \
  static CONST int (*pfn)(int arg0) = dfn;                              \
  EXPORT CONST int funcName(int arg0) { return (*pfn)(arg0); }

#define DISPATCH_p_i(veclen, funcNameS, funcNameS2, funcName, pfn, dfn, funcExt0, funcExt1, funcExt2) \
  static CONST void *(*pfn)(int arg0);                                  \
  static CONST void *dfn(int arg0) {                                    \
    CONST void *(*p)(int) = funcExt0;                                   \
    SUBST_IF_EXT1(funcExt1);                                            \
    SUBST_IF_EXT2(funcExt2);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0);                                                \
  }                                                                     \
  static CONST void *(*pfn)(int arg0) = dfn;                            \
  EXPORT CONST void *funcName(int arg0) { return (*pfn)(arg0); }

//
