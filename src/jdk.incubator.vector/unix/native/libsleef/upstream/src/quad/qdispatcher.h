//   Copyright Naoki Shibata and contributors 2010 - 2025.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#if (defined (__GNUC__) || defined (__clang__)) && !defined(_MSC_VER)
#define CONST __attribute__((const))
#else
#define CONST
#endif

int Sleef_internal_cpuSupportsExt(void (*tryExt)(), int *cache);

#define DISPATCH_vq_vq(qtype, funcName, pfn, dfn, funcExt0, funcExt1)        \
  static CONST qtype (*pfn)(qtype arg0);                        \
  static CONST qtype dfn(qtype arg0) {                        \
    qtype CONST (*p)(qtype arg0) = funcExt0;                        \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0);                                                \
  }                                                                        \
  static CONST qtype (*pfn)(qtype arg0) = dfn;                \
  EXPORT CONST qtype funcName(qtype arg0) { return (*pfn)(arg0); }

#define DISPATCH_vq_vq_vq(qtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST qtype (*pfn)(qtype arg0, qtype arg1);                \
  static CONST qtype dfn(qtype arg0, qtype arg1) {                \
    qtype CONST (*p)(qtype arg0, qtype arg1) = funcExt0;        \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0, arg1);                                                \
  }                                                                        \
  static CONST qtype (*pfn)(qtype arg0, qtype arg1) = dfn;        \
  EXPORT CONST qtype funcName(qtype arg0, qtype arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_vq_vq_vq_vq(qtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST qtype (*pfn)(qtype arg0, qtype arg1, qtype arg2); \
  static CONST qtype dfn(qtype arg0, qtype arg1, qtype arg2) { \
    qtype CONST (*p)(qtype arg0, qtype arg1, qtype arg2) = funcExt0; \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0, arg1, arg2);                                        \
  }                                                                        \
  static CONST qtype (*pfn)(qtype arg0, qtype arg1, qtype arg2) = dfn; \
  EXPORT CONST qtype funcName(qtype arg0, qtype arg1, qtype arg2) { return (*pfn)(arg0, arg1, arg2); }

#define DISPATCH_vq_vq_vx(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST qtype (*pfn)(qtype arg0, xtype arg1);                \
  static CONST qtype dfn(qtype arg0, xtype arg1) {                \
    qtype CONST (*p)(qtype arg0, xtype arg1) = funcExt0;        \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0, arg1);                                                \
  }                                                                        \
  static CONST qtype (*pfn)(qtype arg0, xtype arg1) = dfn;        \
  EXPORT CONST qtype funcName(qtype arg0, xtype arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_vq_vq_pvx(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static qtype (*pfn)(qtype arg0, xtype *arg1);                \
  static qtype dfn(qtype arg0, xtype *arg1) {                \
    qtype (*p)(qtype arg0, xtype *arg1) = funcExt0;        \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0, arg1);                                                \
  }                                                                        \
  static qtype (*pfn)(qtype arg0, xtype *arg1) = dfn;        \
  EXPORT qtype funcName(qtype arg0, xtype *arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_vq_vx(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST qtype (*pfn)(xtype arg0);                        \
  static CONST qtype dfn(xtype arg0) {                        \
    qtype CONST (*p)(xtype arg0) = funcExt0;                        \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0);                                                \
  }                                                                        \
  static CONST qtype (*pfn)(xtype arg0) = dfn;                \
  EXPORT CONST qtype funcName(xtype arg0) { return (*pfn)(arg0); }

#define DISPATCH_vx_vq(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST xtype (*pfn)(qtype arg0);                        \
  static CONST xtype dfn(qtype arg0) {                        \
    xtype CONST (*p)(qtype arg0) = funcExt0;                        \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0);                                                \
  }                                                                        \
  static CONST xtype (*pfn)(qtype arg0) = dfn;                \
  EXPORT CONST xtype funcName(qtype arg0) { return (*pfn)(arg0); }

#define DISPATCH_vx_vq_vq(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST xtype (*pfn)(qtype arg0, qtype arg1);                \
  static CONST xtype dfn(qtype arg0, qtype arg1) {                \
    xtype CONST (*p)(qtype arg0, qtype arg1) = funcExt0;        \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0, arg1);                                                \
  }                                                                        \
  static CONST xtype (*pfn)(qtype arg0, qtype arg1) = dfn;        \
  EXPORT CONST xtype funcName(qtype arg0, qtype arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_q_vq_vx(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST Sleef_quad (*pfn)(qtype arg0, xtype arg1);        \
  static CONST Sleef_quad dfn(qtype arg0, xtype arg1) {        \
    Sleef_quad CONST (*p)(qtype arg0, xtype arg1) = funcExt0;        \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0, arg1);                                                \
  }                                                                        \
  static CONST Sleef_quad (*pfn)(qtype arg0, xtype arg1) = dfn; \
  EXPORT CONST Sleef_quad funcName(qtype arg0, xtype arg1) { return (*pfn)(arg0, arg1); }

#define DISPATCH_vq_vq_vi_q(qtype, xtype, funcName, pfn, dfn, funcExt0, funcExt1) \
  static CONST qtype (*pfn)(qtype arg0, xtype arg1, Sleef_quad arg2);        \
  static CONST qtype dfn(qtype arg0, xtype arg1, Sleef_quad arg2) { \
    qtype CONST (*p)(qtype arg0, xtype arg1, Sleef_quad arg2) = funcExt0; \
    SUBST_IF_EXT1(funcExt1);                                                \
    pfn = p;                                                                \
    return (*pfn)(arg0, arg1, arg2);                                        \
  }                                                                        \
  static CONST qtype (*pfn)(qtype arg0, xtype arg1, Sleef_quad arg2) = dfn; \
  EXPORT CONST qtype funcName(qtype arg0, xtype arg1, Sleef_quad arg2) { return (*pfn)(arg0, arg1, arg2); }

//

