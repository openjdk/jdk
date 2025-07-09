/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_RUNTIME_SHAREDRUNTIMEMATH_HPP
#define SHARE_RUNTIME_SHAREDRUNTIMEMATH_HPP

#include <math.h>

// Used to access the lower/higher 32 bits of a double
typedef union {
    double d;
    struct {
#ifdef VM_LITTLE_ENDIAN
      int lo;
      int hi;
#else
      int hi;
      int lo;
#endif
    } split;
} DoubleIntConv;

static inline int high(double d) {
  DoubleIntConv x;
  x.d = d;
  return x.split.hi;
}

static inline int low(double d) {
  DoubleIntConv x;
  x.d = d;
  return x.split.lo;
}

static inline void set_high(double* d, int high) {
  DoubleIntConv conv;
  conv.d = *d;
  conv.split.hi = high;
  *d = conv.d;
}

static inline void set_low(double* d, int low) {
  DoubleIntConv conv;
  conv.d = *d;
  conv.split.lo = low;
  *d = conv.d;
}

static const double
two54   =  1.80143985094819840000e+16, /* 0x43500000, 0x00000000 */
hugeX  = 1.0e+300,
tiny   = 1.0e-300;

#endif // SHARE_RUNTIME_SHAREDRUNTIMEMATH_HPP
