
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
 * wrapper j0(double x), y0(double x)
 */

#include "fdlibm.h"

#ifdef __STDC__
        double j0(double x)             /* wrapper j0 */
#else
        double j0(x)                    /* wrapper j0 */
        double x;
#endif
{
#ifdef _IEEE_LIBM
        return __ieee754_j0(x);
#else
        double z = __ieee754_j0(x);
        if(_LIB_VERSION == _IEEE_ || isnan(x)) return z;
        if(fabs(x)>X_TLOSS) {
                return __kernel_standard(x,x,34); /* j0(|x|>X_TLOSS) */
        } else
            return z;
#endif
}

#ifdef __STDC__
        double y0(double x)             /* wrapper y0 */
#else
        double y0(x)                    /* wrapper y0 */
        double x;
#endif
{
#ifdef _IEEE_LIBM
        return __ieee754_y0(x);
#else
        double z;
        z = __ieee754_y0(x);
        if(_LIB_VERSION == _IEEE_ || isnan(x) ) return z;
        if(x <= 0.0){
                if(x==0.0)
                    /* d= -one/(x-x); */
                    return __kernel_standard(x,x,8);
                else
                    /* d = zero/(x-x); */
                    return __kernel_standard(x,x,9);
        }
        if(x>X_TLOSS) {
                return __kernel_standard(x,x,35); /* y0(x>X_TLOSS) */
        } else
            return z;
#endif
}
