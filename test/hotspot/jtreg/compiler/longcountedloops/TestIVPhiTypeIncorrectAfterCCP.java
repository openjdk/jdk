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

/**
 * @test
 * @bug 8277906
 * @summary Incorrect type for IV phi of long counted loops after CCP
 *
 * @run main/othervm -XX:-TieredCompilation -XX:CompileCommand=compileonly,TestIVPhiTypeIncorrectAfterCCP::test -XX:-BackgroundCompilation TestIVPhiTypeIncorrectAfterCCP
 *
 */

public class TestIVPhiTypeIncorrectAfterCCP {

    static int test() {
        int array[] = new int[50];

        float f = 0;
        for (int i = 3; i < 49; i++) {
            for (long l = 1; l < i; l++) {
                array[(int)l] = i;
                f += l;
            }
        }
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }
        return sum;
    }

    public static void main(String[] args) {
        long expected = test();
        for (int i = 0; i < 10_000; i++) {
            int res = test();
            if (res != expected) {
                throw new RuntimeException("Unexpected result: " + res + " != " + expected);
            }
        }
    }
}
