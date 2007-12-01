
/*
 * Copyright 1998-2001 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * wrapper of j1,y1
 */

#include "fdlibm.h"

#ifdef __STDC__
        double j1(double x)             /* wrapper j1 */
#else
        double j1(x)                    /* wrapper j1 */
        double x;
#endif
{
#ifdef _IEEE_LIBM
        return __ieee754_j1(x);
#else
        double z;
        z = __ieee754_j1(x);
        if(_LIB_VERSION == _IEEE_ || isnan(x) ) return z;
        if(fabs(x)>X_TLOSS) {
                return __kernel_standard(x,x,36); /* j1(|x|>X_TLOSS) */
        } else
            return z;
#endif
}

#ifdef __STDC__
        double y1(double x)             /* wrapper y1 */
#else
        double y1(x)                    /* wrapper y1 */
        double x;
#endif
{
#ifdef _IEEE_LIBM
        return __ieee754_y1(x);
#else
        double z;
        z = __ieee754_y1(x);
        if(_LIB_VERSION == _IEEE_ || isnan(x) ) return z;
        if(x <= 0.0){
                if(x==0.0)
                    /* d= -one/(x-x); */
                    return __kernel_standard(x,x,10);
                else
                    /* d = zero/(x-x); */
                    return __kernel_standard(x,x,11);
        }
        if(x>X_TLOSS) {
                return __kernel_standard(x,x,37); /* y1(x>X_TLOSS) */
        } else
            return z;
#endif
}
