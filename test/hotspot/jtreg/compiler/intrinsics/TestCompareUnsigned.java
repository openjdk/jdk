/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package compiler.intrinsics;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @key randomness
 * @bug 8283726 8287925
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64" | os.arch=="riscv64" | os.arch=="ppc64" | os.arch=="ppc64le"

 * @summary Test the intrinsics implementation of Integer/Long::compareUnsigned
 * @library /test/lib /
 * @run driver compiler.intrinsics.TestCompareUnsigned
 */
public class TestCompareUnsigned {
    static final int TRUE_VALUE = 10;
    static final int FALSE_VALUE = 4;

    public static void main(String[] args) {
        var test = new TestFramework(TestCompareUnsigned.class);
        test.setDefaultWarmup(1);
        test.start();
    }

    static int expectedResult(int x, int y) {
        return Integer.compare(x + Integer.MIN_VALUE, y + Integer.MIN_VALUE);
    }

    static int expectedResult(long x, long y) {
        return Long.compare(x + Long.MIN_VALUE, y + Long.MIN_VALUE);
    }

    @Test
    @IR(failOn = {IRNode.CMP_U3})
    @IR(counts = {IRNode.CMP_U, "1"})
    public int lessThanInt(int x, int y) {
        return Integer.compareUnsigned(x, y) < 0 ? TRUE_VALUE : FALSE_VALUE;
    }

    @Test
    @IR(failOn = {IRNode.CMP_UL3})
    @IR(counts = {IRNode.CMP_UL, "1"})
    public int lessThanLong(long x, long y) {
        return Long.compareUnsigned(x, y) < 0 ? TRUE_VALUE : FALSE_VALUE;
    }

    @Test
    @IR(counts = {IRNode.CMP_U3, "1"})
    public int compareInt(int x, int y) {
        return Integer.compareUnsigned(x, y);
    }

    @Test
    @IR(counts = {IRNode.CMP_U3, "1"})
    public int compareIntWithImm1(int x) {
        return Integer.compareUnsigned(x, 42);
    }

    @Test
    @IR(counts = {IRNode.CMP_U3, "1"})
    public int compareIntWithImm2(int x) {
        return Integer.compareUnsigned(x, 42 << 12);
    }

    @Test
    @IR(counts = {IRNode.CMP_U3, "1"})
    public int compareIntWithImm3(int x) {
        return Integer.compareUnsigned(x, 42 << 24);
    }

    @Test
    @IR(counts = {IRNode.CMP_U3, "1"})
    public int compareIntWithImm4(int x) {
        return Integer.compareUnsigned(x, Integer.MIN_VALUE);
    }

    @Test
    @IR(counts = {IRNode.CMP_UL3, "1"})
    public int compareLong(long x, long y) {
        return Long.compareUnsigned(x, y);
    }

    @Test
    @IR(counts = {IRNode.CMP_UL3, "1"})
    public int compareLongWithImm1(long x) {
        return Long.compareUnsigned(x, 42);
    }

    @Test
    @IR(counts = {IRNode.CMP_UL3, "1"})
    public int compareLongWithImm2(long x) {
        return Long.compareUnsigned(x, 42 << 12);
    }

    @Test
    @IR(counts = {IRNode.CMP_UL3, "1"})
    public int compareLongWithImm3(long x) {
        return Long.compareUnsigned(x, 42 << 24);
    }

    @Test
    @IR(counts = {IRNode.CMP_UL3, "1"})
    public int compareLongWithImm4(long x) {
        return Long.compareUnsigned(x, Integer.MIN_VALUE);
    }

    @Test
    @IR(counts = {IRNode.CMP_UL3, "1"})
    public int compareLongWithImm5(long x) {
        return Long.compareUnsigned(x, Long.MIN_VALUE);
    }

    @Run(test = {"lessThanInt", "lessThanLong",
                 "compareInt",
                 "compareIntWithImm1", "compareIntWithImm2",
                 "compareIntWithImm3", "compareIntWithImm4",
                 "compareLong",
                 "compareLongWithImm1", "compareLongWithImm2",
                 "compareLongWithImm3", "compareLongWithImm4",
                 "compareLongWithImm5"})
    public void runTests() {
        var random = Utils.getRandomInstance();
        for (int i = 0; i < 1000; i++) {
            int x = random.nextInt();
            int y = random.nextInt();
            Asserts.assertEquals(lessThanInt(x, x), FALSE_VALUE);
            Asserts.assertEquals(compareInt(x, x), 0);
            Asserts.assertEquals(lessThanInt(x, y), expectedResult(x, y) < 0 ? TRUE_VALUE : FALSE_VALUE);
            Asserts.assertEquals(compareInt(x, y), expectedResult(x, y));
            Asserts.assertEquals(compareIntWithImm1(x), expectedResult(x, 42));
            Asserts.assertEquals(compareIntWithImm2(x), expectedResult(x, 42 << 12));
            Asserts.assertEquals(compareIntWithImm3(x), expectedResult(x, 42 << 24));
            Asserts.assertEquals(compareIntWithImm4(x), expectedResult(x, Integer.MIN_VALUE));
        }
        for (int i = 0; i < 1000; i++) {
            long x = random.nextLong();
            long y = random.nextLong();
            Asserts.assertEquals(lessThanLong(x, x), FALSE_VALUE);
            Asserts.assertEquals(compareLong(x, x), 0);
            Asserts.assertEquals(lessThanLong(x, y), expectedResult(x, y) < 0 ? TRUE_VALUE : FALSE_VALUE);
            Asserts.assertEquals(compareLong(x, y), expectedResult(x, y));
            Asserts.assertEquals(compareLongWithImm1(x), expectedResult(x, 42));
            Asserts.assertEquals(compareLongWithImm2(x), expectedResult(x, 42 << 12));
            Asserts.assertEquals(compareLongWithImm3(x), expectedResult(x, 42 << 24));
            Asserts.assertEquals(compareLongWithImm4(x), expectedResult(x, Integer.MIN_VALUE));
            Asserts.assertEquals(compareLongWithImm5(x), expectedResult(x, Long.MIN_VALUE));
        }
    }
}
