
/*
 * Copyright (c) 1998, 2001, Oracle and/or its affiliates. All rights reserved.
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

/*
 * wrapper jn(int n, double x), yn(int n, double x)
 * floating point Bessel's function of the 1st and 2nd kind
 * of order n
 *
 * Special cases:
 *      y0(0)=y1(0)=yn(n,0) = -inf with division by zero signal;
 *      y0(-ve)=y1(-ve)=yn(n,-ve) are NaN with invalid signal.
 * Note 2. About jn(n,x), yn(n,x)
 *      For n=0, j0(x) is called,
 *      for n=1, j1(x) is called,
 *      for n<x, forward recursion us used starting
 *      from values of j0(x) and j1(x).
 *      for n>x, a continued fraction approximation to
 *      j(n,x)/j(n-1,x) is evaluated and then backward
 *      recursion is used starting from a supposed value
 *      for j(n,x). The resulting value of j(0,x) is
 *      compared with the actual value to correct the
 *      supposed value of j(n,x).
 *
 *      yn(n,x) is similar in all respects, except
 *      that forward recursion is used for all
 *      values of n>1.
 *
 */

#include "fdlibm.h"

#ifdef __STDC__
        double jn(int n, double x)      /* wrapper jn */
#else
        double jn(n,x)                  /* wrapper jn */
        double x; int n;
#endif
{
#ifdef _IEEE_LIBM
        return __ieee754_jn(n,x);
#else
        double z;
        z = __ieee754_jn(n,x);
        if(_LIB_VERSION == _IEEE_ || isnan(x) ) return z;
        if(fabs(x)>X_TLOSS) {
            return __kernel_standard((double)n,x,38); /* jn(|x|>X_TLOSS,n) */
        } else
            return z;
#endif
}

#ifdef __STDC__
        double yn(int n, double x)      /* wrapper yn */
#else
        double yn(n,x)                  /* wrapper yn */
        double x; int n;
#endif
{
#ifdef _IEEE_LIBM
        return __ieee754_yn(n,x);
#else
        double z;
        z = __ieee754_yn(n,x);
        if(_LIB_VERSION == _IEEE_ || isnan(x) ) return z;
        if(x <= 0.0){
                if(x==0.0)
                    /* d= -one/(x-x); */
                    return __kernel_standard((double)n,x,12);
                else
                    /* d = zero/(x-x); */
                    return __kernel_standard((double)n,x,13);
        }
        if(x>X_TLOSS) {
            return __kernel_standard((double)n,x,39); /* yn(x>X_TLOSS,n) */
        } else
            return z;
#endif
}
