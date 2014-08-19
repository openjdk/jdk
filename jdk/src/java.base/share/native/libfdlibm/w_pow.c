

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
 * wrapper pow(x,y) return x**y
 */

#include "fdlibm.h"


#ifdef __STDC__
        double pow(double x, double y)  /* wrapper pow */
#else
        double pow(x,y)                 /* wrapper pow */
        double x,y;
#endif
{
#ifdef _IEEE_LIBM
        return  __ieee754_pow(x,y);
#else
        double z;
        z=__ieee754_pow(x,y);
        if(_LIB_VERSION == _IEEE_|| isnan(y)) return z;
        if(isnan(x)) {
            if(y==0.0)
                return __kernel_standard(x,y,42); /* pow(NaN,0.0) */
            else
                return z;
        }
        if(x==0.0){
            if(y==0.0)
                return __kernel_standard(x,y,20); /* pow(0.0,0.0) */
            if(finite(y)&&y<0.0)
                return __kernel_standard(x,y,23); /* pow(0.0,negative) */
            return z;
        }
        if(!finite(z)) {
            if(finite(x)&&finite(y)) {
                if(isnan(z))
                    return __kernel_standard(x,y,24); /* pow neg**non-int */
                else
                    return __kernel_standard(x,y,21); /* pow overflow */
            }
        }
        if(z==0.0&&finite(x)&&finite(y))
            return __kernel_standard(x,y,22); /* pow underflow */
        return z;
#endif
}
