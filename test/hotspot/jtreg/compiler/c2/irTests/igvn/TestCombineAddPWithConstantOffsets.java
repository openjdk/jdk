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
package compiler.c2.irTests.igvn;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8343067
 * @requires os.simpleArch == "x64" | os.simpleArch == "aarch64"
 * @requires vm.compiler2.enabled
 * @summary Test that chains of AddP nodes with constant offsets are idealized
 *          when their offset input changes.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.igvn.TestCombineAddPWithConstantOffsets
 */
public class TestCombineAddPWithConstantOffsets {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(applyIfPlatform = {"x64", "true"}, failOn = {IRNode.ADD_P_OF, ".*"})
    @IR(applyIfPlatform = {"aarch64", "true"}, failOn = {IRNode.ADD_P_OF, "reg_imm"})
    static void testCombineAddPWithConstantOffsets(int[] arr) {
        for (long i = 6; i < 14; i++) {
            arr[(int)i] = 1;
        }
    }

    @Run(test = {"testCombineAddPWithConstantOffsets"})
    public void runTests() {
        testCombineAddPWithConstantOffsets(new int[14]);
    }
}
