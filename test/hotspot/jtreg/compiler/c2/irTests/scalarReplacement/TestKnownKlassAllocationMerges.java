/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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

package compiler.c2.irTests.scalarReplacement;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Test known klass loads from allocation merges.
 * @library /test/lib /
 * @requires vm.debug & vm.flagless & vm.bits == 64 & vm.compiler2.enabled & vm.opt.final.EliminateAllocations
 * @run driver compiler.c2.irTests.scalarReplacement.TestKnownKlassAllocationMerges
 */
public class TestKnownKlassAllocationMerges {
    public static void main(String[] args) {
        new TestFramework().start();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC, IRNode.LOAD_KLASS_OR_NKLASS})
    public Class<?> testDirect(boolean chooseFirst) {
        Base value = chooseFirst ? new First() : new Second();
        return value.getClass();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC, IRNode.LOAD_KLASS_OR_NKLASS})
    public Class<?> testNullCast(int choice) {
        Base value;
        if (choice == 0) {
            value = null;
        } else if (choice == 1) {
            value = new First();
        } else {
            value = new Second();
        }
        return value == null ? null : value.getClass();
    }

    @Test
    @IR(failOn = {IRNode.ALLOC, IRNode.LOAD_KLASS_OR_NKLASS})
    public Class<?> testExactInput(boolean allocate, First input) {
        Base value = allocate ? new Second() : input;
        return value == null ? null : value.getClass();
    }

    @Test
    @IR(counts = {IRNode.ALLOC, "1"})
    // `Base` is not a final class, so EA doesn't know the exact klass, thus
    // leaving the allocation as it is.
    public Class<?> testUnknownInput(boolean allocate, Base input) {
        Base value = allocate ? new Second() : input;
        return value == null ? null : value.getClass();
    }

    @Test
    public Class<?> testNullException(boolean allocate) {
        Base value = allocate ? new First() : null;
        try {
            return value.getClass();
        } catch (NullPointerException expected) {
            return null;
        }
    }

    @Run(test = {"testDirect", "testNullCast", "testExactInput", "testUnknownInput", "testNullException"})
    public void runTests() {
        Asserts.assertEQ(testDirect(true), First.class);
        Asserts.assertEQ(testDirect(false), Second.class);

        Asserts.assertEQ(testNullCast(0), null);
        Asserts.assertEQ(testNullCast(1), First.class);
        Asserts.assertEQ(testNullCast(2), Second.class);

        Asserts.assertEQ(testNullException(true), First.class);
        Asserts.assertEQ(testNullException(false), null);

        First first = new First();
        Asserts.assertEQ(testExactInput(true, first), Second.class);
        Asserts.assertEQ(testExactInput(false, first), First.class);
        Asserts.assertEQ(testExactInput(true, null), Second.class);
        Asserts.assertEQ(testExactInput(false, null), null);

        Base[] inputs = {first, new Second(), new Base(), null};
        for (Base input : inputs) {
            Asserts.assertEQ(testUnknownInput(true, input), Second.class);
            Asserts.assertEQ(testUnknownInput(false, input), input == null ? null : input.getClass());
        }
    }

    static class Base {}
    static final class First extends Base {}
    static final class Second extends Base {}
}
