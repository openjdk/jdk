/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @bug 8343944
 * @summary Test that _widen is set correctly in MinL::add_ring() to prevent an endless widening in CCP.
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,compiler.ccp.TestWrongMinLWiden::*
 *                   compiler.ccp.TestWrongMinLWiden
 */

package compiler.ccp;

public class TestWrongMinLWiden {
    static long lFld;
    static short sFld;

    public static void main(String[] strArr) {
        Math.min(3,3); // Make sure Math class is loaded.
        test();
        testWithMathMin();
    }


    static long test() {
        long x = 50398;
        for (int i = 1; i < 100; i++) {
            long xMinus1 = x - 1; // x is a phi with type #long
            // Long comparison:
            //     ConvI2L(sFld) <= xMinus1
            // First converted to
            //     CMoveL(sFld <= xMinus1, sFld, xMinus1)
            //     CMoveL(ConvI2L(sFld) <= long_phi, ConvI2L(sFld), long_phi)
            // with types
            //     CMoveL(#short <= #long, #short, #long)
            // And then converted in CMoveNode::Ideal() to
            //     MinL(sFld, xMinus1)
            //     MinL(ConvI2L(sFld), long_phi)
            //
            // We wrongly set the _widen of the new type for MinL in MinL::add_ring() to the minimum of both types which
            // is always 0 because the _widen of sFld is 0. As a result, we will endlessly widen the involved types
            // because the new type for MinL keeps resetting _widen to 0.
            // Instead, we should choose the maximum of both _widen fields for the new type for MinL which will then
            // select the _widen of xMinus1 which will grow each time we set a new type for the long_phi. Eventually,
            // we will saturate the type of long_phi to min_long to avoid an endless widening.
            x = sFld <= xMinus1 ? sFld : xMinus1;
        }
        return x;
    }

    // Same as test() but with Math.min() which internally uses x <= y ? x : y which allows the CMoveL pattern to be
    // replaced with MinL.
    static long testWithMathMin() {
        long x = 50398;
        for (int i = 1; i < 100; i++) {
            x = Math.min(sFld, x - 1);
        }
        return x;
    }
}
