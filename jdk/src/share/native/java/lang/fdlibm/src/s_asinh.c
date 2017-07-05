
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

/* asinh(x)
 * Method :
 *      Based on
 *              asinh(x) = sign(x) * log [ |x| + sqrt(x*x+1) ]
 *      we have
 *      asinh(x) := x  if  1+x*x=1,
 *               := sign(x)*(log(x)+ln2)) for large |x|, else
 *               := sign(x)*log(2|x|+1/(|x|+sqrt(x*x+1))) if|x|>2, else
 *               := sign(x)*log1p(|x| + x^2/(1 + sqrt(1+x^2)))
 */

#include "fdlibm.h"

#ifdef __STDC__
static const double
#else
static double
#endif
one =  1.00000000000000000000e+00, /* 0x3FF00000, 0x00000000 */
ln2 =  6.93147180559945286227e-01, /* 0x3FE62E42, 0xFEFA39EF */
huge=  1.00000000000000000000e+300;

#ifdef __STDC__
        double asinh(double x)
#else
        double asinh(x)
        double x;
#endif
{
        double t,w;
        int hx,ix;
        hx = __HI(x);
        ix = hx&0x7fffffff;
        if(ix>=0x7ff00000) return x+x;  /* x is inf or NaN */
        if(ix< 0x3e300000) {    /* |x|<2**-28 */
            if(huge+x>one) return x;    /* return x inexact except 0 */
        }
        if(ix>0x41b00000) {     /* |x| > 2**28 */
            w = __ieee754_log(fabs(x))+ln2;
        } else if (ix>0x40000000) {     /* 2**28 > |x| > 2.0 */
            t = fabs(x);
            w = __ieee754_log(2.0*t+one/(sqrt(x*x+one)+t));
        } else {                /* 2.0 > |x| > 2**-28 */
            t = x*x;
            w =log1p(fabs(x)+t/(one+sqrt(one+t)));
        }
        if(hx>0) return w; else return -w;
}
