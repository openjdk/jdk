/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
import java.util.Objects;

/*
 * @test
 * @summary C2: optimize long range checks in long counted loops
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.TestTypeSpeculation
 */

public class TestTypeSpeculation {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:TypeProfileLevel=222");
    }

    private static final Integer[] testIntegerArray = new Integer[] {42};
    private static final Long[] testLongArray = new Long[] {42L};
    private static final Double[] testDoubleArray = new Double[] {42.0D};
    private static final Integer testInteger = 42;
    private static final Long testLong = 42L;
    private static final Double testDouble = 42.0D;

    
    @DontInline
    public void test1_no_inline() {
    }

    public void test1_helper(Number[] arg) {
        if (arg instanceof Long[]) {
            test1_no_inline();
        }
    }

    @Test
    @IR(counts = {IRNode.CALL, "= 2", IRNode.CLASS_CHECK_TRAP, "= 1", IRNode.NULL_CHECK_TRAP, "= 1"})
    public void test1(Number[] array) {
        test1_helper(array);
    }

    @Run(test = "test1")
    @Warmup(10000)
    public void test1_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            // pollute profile
            test1_helper(testLongArray);
            test1_helper(testDoubleArray);
        }
        test1(testIntegerArray);
    }

    @DontInline
    public void test2_no_inline() {
    }

    public void test2_helper(Number arg) {
        if (arg instanceof Long) {
            test2_no_inline();
        }
    }

    @Test
    @IR(counts = {IRNode.CALL, "= 2", IRNode.CLASS_CHECK_TRAP, "= 1", IRNode.NULL_CHECK_TRAP, "= 1"})
    public void test2(Number array) {
        test2_helper(array);
    }

    @Run(test = "test2")
    @Warmup(10000)
    public void test2_verifier(RunInfo info) {
        if (info.isWarmUp()) {
            // pollute profile
            test2_helper(testLong);
            test2_helper(testDouble);
        }
        test2(testInteger);
    }
}
