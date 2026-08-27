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

/*
 * @test
 * @bug 8387940
 * @requires vm.compiler2.enabled
 * @summary C2: Stress allocation elimination failures
 *
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.escapeAnalysis;

import compiler.lib.ir_framework.*;

public class StressEliminateAllocationsIRTest {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+UnlockDiagnosticVMOptions",
                                   "-XX:+StressEliminateAllocations",
                                   "-XX:StressEliminateAllocationsMean=1");
    }

    static class A {
        final int i;
        A(int i) {
            this.i = i;
        }
    }

    @Test
    @IR(counts = {IRNode.ALLOC, "1"})
    @Arguments(values = Argument.NUMBER_42)
    private static int test(int i) {
        // Even though the object is scalar replaceable,
        // allocation elimination unconditionally fails in stress mode.
        A a = new A(i);

        dontInline();

        return a.i;
    }

    @DontInline
    private static void dontInline() {}
}
