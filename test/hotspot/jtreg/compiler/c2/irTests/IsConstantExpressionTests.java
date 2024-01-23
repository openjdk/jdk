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
package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.internal.misc.JitCompiler;

/*
 * @test
 * @bug 8324433
 * @summary Test that isConstantExpression is able to constant-fold the computation
 *          regarding constant inputs.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.c2.irTests.IsConstantExpressionTests
 */
public class IsConstantExpressionTests {
    private static final int CONSTANT = 3;
    private static final int[] LOOKUP_TABLE;
    static {
        LOOKUP_TABLE = new int[4];
        LOOKUP_TABLE[0] = 125;
        LOOKUP_TABLE[1] = 341;
        LOOKUP_TABLE[2] = 97;
        LOOKUP_TABLE[3] = 460;
    }

    private int variable = 3;

    @Test
    @IR(failOn = IRNode.LOAD_I)
    public int constant() {
        return process(CONSTANT);
    }

    @Test
    @IR(counts = {IRNode.LOAD_I, "2"})
    public int variable() {
        return process(variable);
    }

    @ForceInline
    public int process(int input) {
        if (JitCompiler.isConstantExpression(input)) {
            return switch(input) {
                case 0 -> 125;
                case 1 -> 341;
                case 2 -> 97;
                case 3 -> 460;
                default -> throw new AssertionError();
            };
        }

        return LOOKUP_TABLE[input];
    }

    public static void main(String[] args) {
        var test = new TestFramework(IsConstantExpressionTests.class);
        test.addFlags("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
        test.start();
    }
}
