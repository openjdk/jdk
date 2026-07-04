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
 * or visit www.oracle.com if you need help or have any questions.
 */

/**
 * @test
 * @bug 8388924
 * @key randomness
 * @summary Test that C2 selects the Zbb xnor instruction for ~(a ^ b)
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*zbb.*"
 * @run driver compiler.c2.riscv64.TestXnor
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.DontCompile;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.RunInfo;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;

public class TestXnor {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:+UnlockDiagnosticVMOptions", "-XX:+UseZbb");
    }

    @Test
    @IR(counts = {"xnorI_reg_reg_b", "1"}, phase = CompilePhase.FINAL_CODE)
    static int xnorInt(int a, int b) {
        return ~(a ^ b);
    }

    @Test
    @IR(counts = {"xnorL_reg_reg_b", "1"}, phase = CompilePhase.FINAL_CODE)
    static long xnorLong(long a, long b) {
        return ~(a ^ b);
    }

    @Test
    @IR(counts = {"xnorI_reg_reg_b", "1"}, phase = CompilePhase.FINAL_CODE)
    static int xnorIntCommuted(int a, int b) {
        return a ^ ~b;
    }

    @Test
    @IR(counts = {"xnorI_reg_reg_b", "1"}, phase = CompilePhase.FINAL_CODE)
    static int xnorIntCommuted2(int a, int b) {
        return ~a ^ b;
    }

    @Run(test = "xnorIntCommuted")
    static void runXnorIntCommuted() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();
        verifyXnorInt(a, b);
    }

    @Run(test = "xnorIntCommuted2")
    static void runXnorIntCommuted2() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();
        verifyXnorInt(a, b);
    }

    @Run(test = "xnorInt")
    static void runXnorInt() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();
        verifyXnorInt(a, b);
    }

    @Run(test = "xnorLong")
    static void runXnorLong() {
        long a = RunInfo.getRandom().nextLong();
        long b = RunInfo.getRandom().nextLong();
        verifyXnorLong(a, b);
    }

    @DontCompile
    static void verifyXnorInt(int a, int b) {
        Asserts.assertEQ(xnorInt(a, b), ~(a ^ b));
    }

    @DontCompile
    static void verifyXnorLong(long a, long b) {
        Asserts.assertEQ(xnorLong(a, b), ~(a ^ b));
    }
}