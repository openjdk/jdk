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

package compiler.loopopts.superword;

/*
 * @test
 * @bug 8333876
 * @summary Test parallel reductions.
 * @run main compiler.loopopts.superword.TestParallelReduction
 */

public class TestParallelReduction {
    static int RANGE = 10_000;

    public static void main(String[] args) {
        float[] a = new float[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = i;
        }

        float gold = test(a);

        for (int i = 0; i < 10_000; i++) {
            if (test(a) != gold) {
                throw new RuntimeException("wrong value");
            }
        }
    }

    static float test(float[] a) {
        float x = 0;
        float y = 0;
        for (int i = 0; i < a.length; i+=2) {
            x += a[i+0];
            y += a[i+1];
        }
        return x+y;
    }
}
