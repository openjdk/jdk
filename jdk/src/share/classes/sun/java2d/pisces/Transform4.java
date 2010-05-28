/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.pisces;

public class Transform4 {

    public int m00, m01, m10, m11;
//     double det; // det*65536

    public Transform4() {
        this(1 << 16, 0, 0, 1 << 16);
    }

    public Transform4(int m00, int m01,
                      int m10, int m11) {
        this.m00 = m00;
        this.m01 = m01;
        this.m10 = m10;
        this.m11 = m11;

//         this.det = (double)m00*m11 - (double)m01*m10;
    }

//     public Transform4 createInverse() {
//         double dm00 = m00/65536.0;
//         double dm01 = m01/65536.0;
//         double dm10 = m10/65536.0;
//         double dm11 = m11/65536.0;

//         double invdet = 65536.0/(dm00*dm11 - dm01*dm10);

//         int im00 = (int)( dm11*invdet);
//         int im01 = (int)(-dm01*invdet);
//         int im10 = (int)(-dm10*invdet);
//         int im11 = (int)( dm00*invdet);

//         return new Transform4(im00, im01, im10, im11);
//     }

//     public void transform(int[] point) {
//     }

//     /**
//      * Returns the length of the line segment obtained by inverse
//      * transforming the points <code>(x0, y0)</code> and <code>(x1,
//      * y1)</code>.
//      */
//     public int getTransformedLength(int x0, int x1, int y0, int y1) {
//         int lx = x1 - x0;
//         int ly = y1 - y0;

//         double a = (double)m00*ly - (double)m10*lx;
//         double b = (double)m01*ly - (double)m11*lx;
//         double len = PiscesMath.sqrt((a*a + b*b)/(det*det));
//         return (int)(len*65536.0);
//     }

//     public int getType() {
//     }

}
