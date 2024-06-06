/*
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8321308
 * @summary AArch64: Fix matching predication for cbz/cbnz
 * @requires os.arch=="aarch64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestArrLenCheckOptimization
 */

public class TestArrLenCheckOptimization {

    int result = 0;

    @Test
    @IR(counts = {IRNode.CBZW_LS, "1"})
    void test_le_int(int ia[]) {
        result += ia[0];
    }

    @Test
    @IR(counts = {IRNode.CBNZW_HI, "1"})
    void test_gt_int(int ia[]) {
        if (ia.length > 0) {
            result += 0x88;
        } else {
            result -= 1;
        }
    }

    @Test
    @IR(counts = {IRNode.CBZ_LS, "1"})
    void test_le_long(int ia[]) {
        if (Long.compareUnsigned(ia.length, 0) > 0) {
            result += 0x80;
        } else {
            result -= 1;
        }
    }

    @Test
    @IR(counts = {IRNode.CBZ_HI, "1"})
    void test_gt_long(int ia[]) {
        if (Long.compareUnsigned(ia.length, 0) > 0) {
            result += 0x82;
        } else {
            result -= 1;
        }
    }

    @Run(test = {"test_le_int", "test_gt_int", "test_le_long", "test_gt_long"},
         mode = RunMode.STANDALONE)
    public void test_runner() {
        for (int i = 0; i < 10_000; i++) {
            test_le_int(new int[1]);
            test_gt_int(new int[0]);
            test_le_long(new int[1]);
            test_gt_long(new int[0]);
        }
    }

    public static void main(String [] args) {
        TestFramework.run();
    }
 }
