/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2.gvn;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8312213
 * @summary Test value method of DivINode and DivLNode
 * @library /test/lib /
 * @run driver compiler.c2.gvn.IntegerDivValueTests
 */
// TODO bug number
public class IntegerDivValueTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public int testIntConstantFolding() {
        // All constants available during parsing
        return 50 / 25;
    }

    @Test
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public int testIntConstantFoldingSpecialCase() {
        // All constants available during parsing
        return Integer.MIN_VALUE / -1;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public int testIntRange(int in) {
        int a = (in & 7) + 16;
        return a / 12; // [16, 23] / 12 is constant 1
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public boolean testIntRange2(int in) {
        int a = (in & 7) + 16;
        return a / 4 > 3; // [16, 23] / 4 => [4, 5]
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.DIV_I, "1"})
    public boolean testIntRange3(int in, int in2) {
        int a = (in & 31) + 16;
        int b = (in2 & 3) + 5;
        return a / b > 4; // [16, 47] / [5, 8] => [2, 9]
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public boolean testIntRange4(int in, int in2) {
        int a = (in & 15); // [0, 15]
        int b = (in2 & 3) + 1; // [1, 4]
        return a / b >= 0;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public boolean testIntRange5(int in, int in2) {
        int a = (in & 15) + 5; // [5, 20]
        int b = (in2 & 3) + 1; // [1, 4]
        return a / b > 0;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public boolean testIntRange6(int in, int in2) {
        int a = (in & 15) + 5; // [5, 20]
        int b = (in2 & 7) - 1; // [-1, 5]
        if (b == 0) return false;
        return a / b >= -20;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.DIV_I, "1"})
    public boolean testIntRange7(int in, int in2) {
        int a = (in & 15) + 5; // [5, 20]
        int b = (in2 & 7) - 1; // [-1, 5]
        if (b == 0) return false;
        return a / b > 0;
    }


    // Long variants

    @Test
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public long testLongConstantFolding() {
        // All constants available during parsing
        return 50L / 25L;
    }

    @Test
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public long testLongConstantFoldingSpecialCase() {
        // All constants available during parsing
        return Long.MIN_VALUE / -1L;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public long testLongRange(long in) {
        long a = (in & 7L) + 16L;
        return a / 12L; // [16, 23] / 12 is constant 1
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public boolean testLongRange2(long in) {
        long a = (in & 7L) + 16L;
        return a / 4L > 3L; // [16, 23] / 4 => [4, 5]
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.DIV_L, "1"})
    public boolean testLongRange3(long in, long in2) {
        long a = (in & 31L) + 16L;
        long b = (in2 & 3L) + 5L;
        return a / b > 4L; // [16, 47] / [5, 8] => [2, 9]
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public boolean testLongRange4(long in, long in2) {
        long a = (in & 15L); // [0, 15]
        long b = (in2 & 3L) + 1L; // [1, 4]
        return a / b >= 0L;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public boolean testLongRange5(long in, long in2) {
        long a = (in & 15L) + 5L; // [5, 20]
        long b = (in2 & 3L) + 1L; // [1, 4]
        return a / b > 0L;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.DIV_I, IRNode.DIV_L})
    public boolean testLongRange6(long in, long in2) {
        long a = (in & 15L) + 5L; // [5, 20]
        long b = (in2 & 7L) - 1L; // [-1, 5]
        if (b == 0L) return false;
        return a / b >= -20L;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {IRNode.DIV_L, "1"})
    public boolean testLongRange7(long in, long in2) {
        long a = (in & 15L) + 5L; // [5, 20]
        long b = (in2 & 7L) - 1L; // [-1, 5]
        if (b == 0L) return false;
        return a / b > 0L;
    }
}
