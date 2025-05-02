/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @summary Test that patterns leading to Conv2B are correctly expanded.
 * @bug 8051725
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @requires os.arch == "x86_64" | os.arch == "amd64" | os.arch == "aarch64"
 * @run driver compiler.c2.irTests.TestConv2BExpansion
 */
public class TestConv2BExpansion {
    public static void main(String[] args) {
        TestFramework.run();
    }

    // These IR checks do not apply on riscv64, as riscv64 supports Conv2B, e.g. for `return x == 0`,
    // the graph looks like:
    //      Return (XorI (Conv2B ConI(#int: 1)))
    // On other platforms, e.g. x86_64 which does not supports Conv2B, the graph looks like:
    //      Return (CMoveI (Bool (CompI (Param1 ConI(#int: 0))) ConI(#int: 1) ConI(#int: 0)))
    // On riscv64, current graph is more efficient than `CMoveI`, as it
    //      1. generates less code
    //      2. even when zicond is not supported, it does not introduce branches.
    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"}, failOn = {IRNode.XOR})
    public boolean testIntEquals0(int x) {
        return x == 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public boolean testIntNotEquals0(int x) {
        return x != 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"}, failOn = {IRNode.XOR})
    public boolean testObjEqualsNull(Object o) {
        return o == null;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public boolean testObjNotEqualsNull(Object o) {
        return o != null;
    }

    @Run(test = {"testIntEquals0", "testIntNotEquals0"})
    public void runTestInts() {
        assertResult(0);
        assertResult(1);
    }

    @Run(test = {"testObjEqualsNull", "testObjNotEqualsNull"})
    public void runTestObjs() {
        assertResult(new Object());
        assertResult(null);
    }

    @DontCompile
    public void assertResult(int x) {
        Asserts.assertEQ(x == 0, testIntEquals0(x));
        Asserts.assertEQ(x != 0, testIntNotEquals0(x));
    }

    @DontCompile
    public void assertResult(Object o) {
        Asserts.assertEQ(o == null, testObjEqualsNull(o));
        Asserts.assertEQ(o != null, testObjNotEqualsNull(o));
    }
}