/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.igvn;

/*
 * @test
 * @bug 8394889
 * @summary C2 IGVN should idealize: (0 - x) * (0 - y) -> x * y
 * @library /test/lib /
 * @run main/othervm -Xbatch
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestMissingSubMulIdealization {
    static int xI;
    static int yI;
    static long xL;
    static long yL;
    static float xF;
    static float yF;
    static double xD;
    static double yD;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            testI();
            testL();
            testF();
            testD();
        }
    }

    public static void testI() {
        yI = 0;
        for (int i = 0; i < 1; i++) {}
        int v = yI - xI;
        yI = v * v;
    }

    public static void testL() {
        yL = 0;
        for (int i = 0; i < 1; i++) {}
        long v = yL - xL;
        yL = v * v;
    }

    public static void testF() {
        yF = 0;
        for (int i = 0; i < 1; i++) {}
        float v = yF - xF;
        yF = v * v;
    }

    public static void testD() {
        yD = 0;
        for (int i = 0; i < 1; i++) {}
        double v = yD - xD;
        yD = v * v;
    }
}
