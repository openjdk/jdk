//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

typedef struct {
  char *name;
  int ulp;
  int ulpSuffix;
  int funcType;
  int flags;
} funcSpec;

/*
  ulp : (error bound in ulp) * 10

  ulpSuffix:
  0 : ""
  1 : "_u1"
  2 : "_u05"
  3 : "_u35"
  4 : "_u15"
  5 : "_u3500"

  funcType:
  0 : vdouble func(vdouble);
  1 : vdouble func(vdouble, vdouble);
  2 : vdouble2 func(vdouble);   GNUABI : void func(vdouble, double *, double *);
  3 : vdouble func(vdouble, vint);
  4 : vint func(vdouble);
  5 : vdouble func(vdouble, vdouble, vdouble);
  6 : vdouble2 func(vdouble);   GNUABI : vdouble func(vdouble, double *);
  7 : int func(int);
  8 : void *func(int);

  flags:
  1 : No GNUABI
  2 : No double func
 */

funcSpec funcList[] = {
  { "sin", 35, 0, 0, 0 },
  { "cos", 35, 0, 0, 0 },
  { "sincos", 35, 0, 2, 0 },
  { "tan", 35, 0, 0, 0 },
  { "asin", 35, 0, 0, 0 },
  { "acos", 35, 0, 0, 0 },
  { "atan", 35, 0, 0, 0 },
  { "atan2", 35, 0, 1, 0 },
  { "log", 35, 0, 0, 0 },
  { "cbrt", 35, 0, 0, 0 },
  { "sin", 10, 1, 0, 0 },
  { "cos", 10, 1, 0, 0 },
  { "sincos", 10, 1, 2, 0 },
  { "tan", 10, 1, 0, 0 },
  { "asin", 10, 1, 0, 0 },
  { "acos", 10, 1, 0, 0 },
  { "atan", 10, 1, 0, 0 },
  { "atan2", 10, 1, 1, 0 },
  { "log", 10, 1, 0, 0 },
  { "cbrt", 10, 1, 0, 0 },
  { "exp", 10, 0, 0, 0 },
  { "pow", 10, 0, 1, 0 },
  { "sinh", 10, 0, 0, 0 },
  { "cosh", 10, 0, 0, 0 },
  { "tanh", 10, 0, 0, 0 },
  { "sinh", 35, 3, 0, 0 },
  { "cosh", 35, 3, 0, 0 },
  { "tanh", 35, 3, 0, 0 },

  { "fastsin", 3500, 5, 0, 2 },
  { "fastcos", 3500, 5, 0, 2 },
  { "fastpow", 3500, 5, 1, 2 },

  { "asinh", 10, 0, 0, 0 },
  { "acosh", 10, 0, 0, 0 },
  { "atanh", 10, 0, 0, 0 },
  { "exp2", 10, 0, 0, 0 },
  { "exp2", 35, 3, 0, 0 },
  { "exp10", 10, 0, 0, 0 },
  { "exp10", 35, 3, 0, 0 },
  { "expm1", 10, 0, 0, 0 },
  { "log10", 10, 0, 0, 0 },
  { "log2", 10, 0, 0, 0 },
  { "log2", 35, 3, 0, 0 },
  { "log1p", 10, 0, 0, 0 },
  { "sincospi", 5, 2, 2, 0 },
  { "sincospi", 35, 3, 2, 0 },
  { "sinpi", 5, 2, 0, 0 },
  { "cospi", 5, 2, 0, 0 },
  { "ldexp", -1, 0, 3, 0 },
  { "ilogb", -1, 0, 4, 0 },

  { "fma", -1, 0, 5, 0 },
  { "sqrt", -1, 0, 0, 0 },
  { "sqrt", 5, 2, 0, 1 },
  { "sqrt", 35, 3, 0, 0 },
  { "hypot", 5, 2, 1, 0 },
  { "hypot", 35, 3, 1, 0 },
  { "fabs", -1, 0, 0, 0 },
  { "copysign", -1, 0, 1, 0 },
  { "fmax", -1, 0, 1, 0 },
  { "fmin", -1, 0, 1, 0 },
  { "fdim", -1, 0, 1, 0 },
  { "trunc", -1, 0, 0, 0 },
  { "floor", -1, 0, 0, 0 },
  { "ceil", -1, 0, 0, 0 },
  { "round", -1, 0, 0, 0 },
  { "rint", -1, 0, 0, 0 },
  { "nextafter", -1, 0, 1, 0 },
  { "frfrexp", -1, 0, 0, 0 },
  { "expfrexp", -1, 0, 4, 0 },
  { "fmod", -1, 0, 1, 0 },
  { "remainder", -1, 0, 1, 0 },
  { "modf", -1, 0, 6, 0 },

  { "lgamma", 10, 1, 0, 0 },
  { "tgamma", 10, 1, 0, 0 },
  { "erf", 10, 1, 0, 0 },
  { "erfc", 15, 4, 0, 0 },

  { "getInt", -1, 0, 7, 1},
  { "getPtr", -1, 0, 8, 1},

  { NULL, -1, 0, 0, 0 },
};
