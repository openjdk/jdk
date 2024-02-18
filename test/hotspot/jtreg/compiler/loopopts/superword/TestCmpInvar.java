/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8309268
 * @summary Test loop invariant input to Cmp.
 *
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=compileonly,compiler.loopopts.superword.TestCmpInvar::test*
 *      compiler.loopopts.superword.TestCmpInvar
 */
package compiler.loopopts.superword;

public class TestCmpInvar {
    static int N = 400;
    static long myInvar;

    static void test1(int limit, float fcon) {
        boolean a[] = new boolean[1000];
        for (int i = 0; i < limit; i++) {
            a[i] = fcon > i;
        }
    }

    static void test2(int limit, float fcon) {
        boolean a[] = new boolean[1000];
        for (int i = 0; i < limit; i++) {
            a[i] = i > fcon;
        }
    }

    static int test3() {
        int[] a = new int[N];
        int acc = 0;
        for (int i = 1; i < 63; i++) {
            acc += Math.min(myInvar, a[i]--);
        }
        return acc;
    }

    static int test4() {
        int[] a = new int[N];
        int acc = 0;
        for (int i = 1; i < 63; i++) {
            acc += Math.min(a[i]--, myInvar);
        }
        return acc;
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 10_100; i++) {
            test1(500, 80.1f);
        }

        for (int i = 0; i < 10_100; i++) {
            test2(500, 80.1f);
        }

        for (int i = 0; i < 10_000; i++) {
            test3();
        }

        for (int i = 0; i < 10_000; i++) {
            test4();
        }
    }
}
