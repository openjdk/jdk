/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @summary Test array filling optimization
 * @requires vm.compiler2.enabled & vm.debug
 * @library /test/lib
 * @run main/othervm -XX:+OptimizeFill -XX:LoopUnrollLimit=0 -XX:+TraceOptimizeFill compiler.c2.TestOptimizeFill
 */

package compiler.c2;

public class TestOptimizeFill {
    static int[] a = new int[500];
    static boolean[] b = new boolean[100];

    static int[] test1(int x) {
        float[] f = new float[100];
        byte[] t = new byte[600];

        for (int i = 0; i < a.length; i++) {
            a[i] = a.length;
        }
        for (int i = 0; i < b.length; i++) {
            b[i] =  true;
        }
        for (int i = 0; i < f.length; i++) {
            f[i] =  3.14f;
        }
        for (int i = 0; i < t.length; i++) {
            t[i] =  (byte)x;
        }
        java.util.Arrays.fill(a, x);
        return a;
    }

    static int[] test2(int x) {
        for (;;) {
            int[] k = new int[1024];
            for (int i = 0; i < k.length; i++) {
                k[i] =  k.length;
            }
            return k;
        }
    }

    public static void main(String[] args) {
        int k = 0;
        for (int i = 0; i < 10000; i++) {
            k += test1(i)[i%100];
            k += test2(i)[i%100];
        }
        System.out.println(k);
    }
}
