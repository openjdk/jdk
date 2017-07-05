
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

/* __ieee754_acosh(x)
 * Method :
 *      Based on
 *              acosh(x) = log [ x + sqrt(x*x-1) ]
 *      we have
 *              acosh(x) := log(x)+ln2, if x is large; else
 *              acosh(x) := log(2x-1/(sqrt(x*x-1)+x)) if x>2; else
 *              acosh(x) := log1p(t+sqrt(2.0*t+t*t)); where t=x-1.
 *
 * Special cases:
 *      acosh(x) is NaN with signal if x<1.
 *      acosh(NaN) is NaN without signal.
 */

#include "fdlibm.h"

#ifdef __STDC__
static const double
#else
static double
#endif
one     = 1.0,
ln2     = 6.93147180559945286227e-01;  /* 0x3FE62E42, 0xFEFA39EF */

#ifdef __STDC__
        double __ieee754_acosh(double x)
#else
        double __ieee754_acosh(x)
        double x;
#endif
{
        double t;
        int hx;
        hx = __HI(x);
        if(hx<0x3ff00000) {             /* x < 1 */
            return (x-x)/(x-x);
        } else if(hx >=0x41b00000) {    /* x > 2**28 */
            if(hx >=0x7ff00000) {       /* x is inf of NaN */
                return x+x;
            } else
                return __ieee754_log(x)+ln2;    /* acosh(huge)=log(2x) */
        } else if(((hx-0x3ff00000)|__LO(x))==0) {
            return 0.0;                 /* acosh(1) = 0 */
        } else if (hx > 0x40000000) {   /* 2**28 > x > 2 */
            t=x*x;
            return __ieee754_log(2.0*x-one/(x+sqrt(t-one)));
        } else {                        /* 1<x<2 */
            t = x-one;
            return log1p(t+sqrt(2.0*t+t*t));
        }
}
