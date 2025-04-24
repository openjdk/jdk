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

#define DISPATCH_vq_vq(qtype, funcName, pfn, dfn, funcExt0, funcExt1)   \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0);                      \
  static CONST VECTOR_CC qtype dfn(qtype arg0) {                        \
    qtype CONST VECTOR_CC (*p)(qtype arg0) = funcExt0;                  \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0);                                                \
  }                                                                     \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0) = dfn;                \
  EXPORT CONST VECTOR_CC qtype funcName(qtype arg0) { return (*pfn)(arg0); }

#define DISPATCH_vq_vq_vq(qtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0, qtype arg1);          \
  static CONST VECTOR_CC qtype dfn(qtype arg0, qtype arg1) {            \
    qtype CONST VECTOR_CC (*p)(qtype arg0, qtype arg1) = funcExt0;      \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1);                                          \
  }                                                                     \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0, qtype arg1) = dfn;    \
  EXPORT CONST VECTOR_CC qtype funcName(qtype arg0, qtype arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_vq_vq_vq_vq(qtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0, qtype arg1, qtype arg2); \
  static CONST VECTOR_CC qtype dfn(qtype arg0, qtype arg1, qtype arg2) { \
    qtype CONST VECTOR_CC (*p)(qtype arg0, qtype arg1, qtype arg2) = funcExt0; \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1, arg2);                                    \
  }                                                                     \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0, qtype arg1, qtype arg2) = dfn; \
  EXPORT CONST VECTOR_CC qtype funcName(qtype arg0, qtype arg1, qtype arg2) { return (*pfn)(arg0, arg1, arg2); }

#define DISPATCH_vq_vq_vx(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0, xtype arg1);          \
  static CONST VECTOR_CC qtype dfn(qtype arg0, xtype arg1) {            \
    qtype CONST VECTOR_CC (*p)(qtype arg0, xtype arg1) = funcExt0;      \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1);                                          \
  }                                                                     \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0, xtype arg1) = dfn;    \
  EXPORT CONST VECTOR_CC qtype funcName(qtype arg0, xtype arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_vq_vq_pvx(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static VECTOR_CC qtype (*pfn)(qtype arg0, xtype *arg1);               \
  static VECTOR_CC qtype dfn(qtype arg0, xtype *arg1) {         \
    qtype VECTOR_CC (*p)(qtype arg0, xtype *arg1) = funcExt0;   \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1);                                          \
  }                                                                     \
  static VECTOR_CC qtype (*pfn)(qtype arg0, xtype *arg1) = dfn; \
  EXPORT VECTOR_CC qtype funcName(qtype arg0, xtype *arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_vq_vx(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST VECTOR_CC qtype (*pfn)(xtype arg0);                      \
  static CONST VECTOR_CC qtype dfn(xtype arg0) {                        \
    qtype CONST VECTOR_CC (*p)(xtype arg0) = funcExt0;                  \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0);                                                \
  }                                                                     \
  static CONST VECTOR_CC qtype (*pfn)(xtype arg0) = dfn;                \
  EXPORT CONST VECTOR_CC qtype funcName(xtype arg0) { return (*pfn)(arg0); }

#define DISPATCH_vx_vq(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST VECTOR_CC xtype (*pfn)(qtype arg0);                      \
  static CONST VECTOR_CC xtype dfn(qtype arg0) {                        \
    xtype CONST VECTOR_CC (*p)(qtype arg0) = funcExt0;                  \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0);                                                \
  }                                                                     \
  static CONST VECTOR_CC xtype (*pfn)(qtype arg0) = dfn;                \
  EXPORT CONST VECTOR_CC xtype funcName(qtype arg0) { return (*pfn)(arg0); }

#define DISPATCH_vx_vq_vq(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST VECTOR_CC xtype (*pfn)(qtype arg0, qtype arg1);          \
  static CONST VECTOR_CC xtype dfn(qtype arg0, qtype arg1) {            \
    xtype CONST VECTOR_CC (*p)(qtype arg0, qtype arg1) = funcExt0;      \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1);                                          \
  }                                                                     \
  static CONST VECTOR_CC xtype (*pfn)(qtype arg0, qtype arg1) = dfn;    \
  EXPORT CONST VECTOR_CC xtype funcName(qtype arg0, qtype arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_q_vq_vx(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST VECTOR_CC Sleef_quad (*pfn)(qtype arg0, xtype arg1);     \
  static CONST VECTOR_CC Sleef_quad dfn(qtype arg0, xtype arg1) {       \
    Sleef_quad CONST VECTOR_CC (*p)(qtype arg0, xtype arg1) = funcExt0; \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1);                                          \
  }                                                                     \
  static CONST VECTOR_CC Sleef_quad (*pfn)(qtype arg0, xtype arg1) = dfn; \
  EXPORT CONST VECTOR_CC Sleef_quad funcName(qtype arg0, xtype arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_vq_vq_vi_q(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0, xtype arg1, Sleef_quad arg2); \
  static CONST VECTOR_CC qtype dfn(qtype arg0, xtype arg1, Sleef_quad arg2) { \
    qtype CONST VECTOR_CC (*p)(qtype arg0, xtype arg1, Sleef_quad arg2) = funcExt0; \
    SUBST_IF_EXT1(funcExt1);                                            \
    pfn = p;                                                            \
    return (*pfn)(arg0, arg1, arg2);                                    \
  }                                                                     \
  static CONST VECTOR_CC qtype (*pfn)(qtype arg0, xtype arg1, Sleef_quad arg2) = dfn; \
  EXPORT CONST VECTOR_CC qtype funcName(qtype arg0, xtype arg1, Sleef_quad arg2) { return (*pfn)(arg0, arg1, arg2); }

//
