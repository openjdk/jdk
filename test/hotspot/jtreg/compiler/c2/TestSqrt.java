/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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

package compiler.c2;

/*
 * @test
 * @bug 8279076
 * @summary SqrtD/SqrtF should be matched only on supported platforms
 * @requires vm.debug
 *
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:CompileOnly=compiler/c2/TestSqrt
 *                   -XX:CompileOnly=java/lang/Math
 *                   compiler.c2.TestSqrt
 */
public class TestSqrt {
    static float srcF = 42.0f;
    static double srcD = 42.0d;
    static float dstF;
    static double dstD;

    public static void test() {
        dstF = (float)Math.sqrt((double)srcF);
        dstD = Math.sqrt(srcD);
    }

    public static void main(String args[]) {
        for (int i = 0; i < 20_000; i++) {
            test();
        }
    }
}

