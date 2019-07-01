/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited and affiliates. All rights reserved.
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



package org.graalvm.compiler.core.aarch64.test;

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64BitFieldOp;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64BitFieldTest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> predicate = op -> (op instanceof AArch64BitFieldOp);

    private void testAndCheckLIR(String method, String negativeMethod, Object input) {
        test(method, input);
        checkLIR(method, predicate, 1);
        test(negativeMethod, input);
        checkLIR(negativeMethod, predicate, 0);
    }

    /**
     * unsigned bit field extract int.
     */
    public static int extractInt(int input) {
        return (input >>> 6) & 0xffff;
    }

    /**
     * unsigned bit field extract int (negative cases).
     */
    public static int invalidExtractInt(int input) {
        int result = 0;
        result += ((input >>> 10) & 0xfff0);    // Invalid mask
        result += ((input >>> 2) & 0x7fffffff); // Bit field width too large
        result += ((input >>> 16) & 0x1ffff);   // Width + lsb exceeds limit
        return result;
    }

    @Test
    public void testExtractInt() {
        testAndCheckLIR("extractInt", "invalidExtractInt", 0x12345678);
    }

    /**
     * unsigned bit field extract long.
     */
    public static long extractLong(long input) {
        return (input >>> 25) & 0xffffffffL;
    }

    /**
     * unsigned bit field extract long (negative cases).
     */
    public static long invalidExtractLong(long input) {
        long result = 0L;
        result += ((input >>> 10) & 0x230L);                // Invalid mask
        result += ((input >>> 2) & 0x7fffffffffffffffL);    // Bit field width too large
        result += ((input >>> 62) & 0x7L);                  // Width + lsb exceeds limit
        return result;
    }

    @Test
    public void testExtractLong() {
        testAndCheckLIR("extractLong", "invalidExtractLong", 0xfedcba9876543210L);
    }

    /**
     * unsigned bit field insert int.
     */
    public static int insertInt(int input) {
        return (input & 0xfff) << 10;
    }

    /**
     * unsigned bit field insert int (negative cases).
     */
    public static int invalidInsertInt(int input) {
        int result = 0;
        result += ((input & 0xe) << 25);        // Invalid mask
        result += ((input & 0x7fffffff) << 1);  // Bit field width too large
        result += ((input & 0x1ffff) << 16);    // Width + lsb exceeds limit
        return result;
    }

    @Test
    public void testInsertInt() {
        testAndCheckLIR("insertInt", "invalidInsertInt", 0xcafebabe);
    }

    /**
     * unsigned bit field insert long.
     */
    public static long insertLong(long input) {
        return (input & 0x3fffffffffffL) << 7;
    }

    /**
     * unsigned bit field insert long (negative cases).
     */
    public static long invalidInsertLong(long input) {
        long result = 0L;
        result += ((input & 0x1a) << 39);                   // Invalid mask
        result += ((input & 0x7fffffffffffffffL) << 1);     // Bit field width too large
        result += ((input & 0x3fffffff) << 52);             // Width + lsb exceeds limit
        return result;
    }

    @Test
    public void testInsertLong() {
        testAndCheckLIR("insertLong", "invalidInsertLong", 0xdeadbeefdeadbeefL);
    }
}
