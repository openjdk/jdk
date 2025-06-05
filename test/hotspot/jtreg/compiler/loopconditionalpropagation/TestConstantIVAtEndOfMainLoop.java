/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8275202
 * @summary C2: optimize out more redundant conditions
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:+LoopConditionalPropagationALot -XX:-UseLoopPredicate TestConstantIVAtEndOfMainLoop
 *                   
 *
 */
public class TestConstantIVAtEndOfMainLoop {
    private static volatile int volatileField;
    private static int field;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            final int res = test1(0, 0);
            if (res != 2 + 999) {
                throw new RuntimeException(res + " != " + 1001);
            }
        }
    }

    private static int test1(int parallel_iv, int loop_invariant) {
        int l;
        for (l = 0; l < 10; l++) {

        }
        l = l /10;
        int k;
        for (k = 0; k < 10; k+=l) {

        }
        k = k /10;
        int j;
        for (j = 0; j < 10; j+=k) {

        }
        j = j /10;
        int m = 2;
        for (int i = 0; i < 1000; i+=k) {
            if ((k - 1) * i + loop_invariant != 0) {

            }
            volatileField = parallel_iv;
            parallel_iv += m;
            volatileField = parallel_iv - (i + 1) * j;
            m = j;
        }
        return parallel_iv;
    }
}
