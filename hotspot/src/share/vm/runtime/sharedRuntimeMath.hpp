/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_SHAREDRUNTIMEMATH_HPP
#define SHARE_VM_RUNTIME_SHAREDRUNTIMEMATH_HPP

#include <math.h>

// VM_LITTLE_ENDIAN is #defined appropriately in the Makefiles
// [jk] this is not 100% correct because the float word order may different
// from the byte order (e.g. on ARM FPA)
#ifdef VM_LITTLE_ENDIAN
# define __HI(x) *(1+(int*)&x)
# define __LO(x) *(int*)&x
#else
# define __HI(x) *(int*)&x
# define __LO(x) *(1+(int*)&x)
#endif

static double copysignA(double x, double y) {
  __HI(x) = (__HI(x)&0x7fffffff)|(__HI(y)&0x80000000);
  return x;
}

/*
 * ====================================================
 * Copyright (c) 1998 Oracle and/or its affiliates. All rights reserved.
 *
 * Developed at SunSoft, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice
 * is preserved.
 * ====================================================
 */

/*
 * scalbn (double x, int n)
 * scalbn(x,n) returns x* 2**n  computed by  exponent
 * manipulation rather than by actually performing an
 * exponentiation or a multiplication.
 */

static const double
two54   =  1.80143985094819840000e+16, /* 0x43500000, 0x00000000 */
twom54  =  5.55111512312578270212e-17, /* 0x3C900000, 0x00000000 */
hugeX  = 1.0e+300,
tiny   = 1.0e-300;

static double scalbnA (double x, int n) {
  int  k,hx,lx;
  hx = __HI(x);
  lx = __LO(x);
  k = (hx&0x7ff00000)>>20;              /* extract exponent */
  if (k==0) {                           /* 0 or subnormal x */
    if ((lx|(hx&0x7fffffff))==0) return x; /* +-0 */
    x *= two54;
    hx = __HI(x);
    k = ((hx&0x7ff00000)>>20) - 54;
    if (n< -50000) return tiny*x;       /*underflow*/
  }
  if (k==0x7ff) return x+x;             /* NaN or Inf */
  k = k+n;
  if (k >  0x7fe) return hugeX*copysignA(hugeX,x); /* overflow  */
  if (k > 0)                            /* normal result */
    {__HI(x) = (hx&0x800fffff)|(k<<20); return x;}
  if (k <= -54) {
    if (n > 50000)      /* in case integer overflow in n+k */
      return hugeX*copysignA(hugeX,x);  /*overflow*/
    else return tiny*copysignA(tiny,x); /*underflow*/
  }
  k += 54;                              /* subnormal result */
  __HI(x) = (hx&0x800fffff)|(k<<20);
  return x*twom54;
}

#endif // SHARE_VM_RUNTIME_SHAREDRUNTIMEMATH_HPP
