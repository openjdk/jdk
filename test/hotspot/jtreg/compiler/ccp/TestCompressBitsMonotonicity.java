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
package compiler.ccp;

/*
 * @test
 * @bug 8379555
 * @summary Test that CompressBitsNode::Value does not violate monotonicity
 * @run main/othervm -Xbatch -XX:CompileOnly=${test.main.class}::test* ${test.main.class}
 */
public class TestCompressBitsMonotonicity {
    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            testInt(0);
            testLong(0);
        }
    }

    private static int testInt(int v) {
        v &= 0b1101;
        int mask = 0b1111;
        int sum = 0;
        for (int i = 1; i < 10; i *= 2) {
            mask = Integer.compress(v, mask);
            sum += mask;
        }
        return sum;
    }

    private static long testLong(long v) {
        v &= 0b1101;
        long mask = 0b1111;
        long sum = 0;
        for (int i = 1; i < 10; i *= 2) {
            mask = Long.compress(v, mask);
            sum += mask;
        }
        return sum;
    }
}
