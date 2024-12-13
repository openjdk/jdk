/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8309978
 * @summary [x64] Fix useless padding
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @requires (os.simpleArch == "x64")
 * @run driver compiler.c2.irTests.TestPadding
 */

public class TestPadding {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+IntelJccErratumMitigation");
    }

    @Run(test = "test")
    public static void test_runner() {
        test(42);
        tpf.b1++; // to take both branches in test()
    }

    @Test
    @IR(counts = { IRNode.NOP, "<=1" })
    static int test(int i) {
        TestPadding tp = tpf;
        if (tp.b1 > 42) { // Big 'cmpb' instruction at offset 0x30
          tp.i1 = i;
        }
        return i;
    }

    static TestPadding t1;
    static TestPadding t2;
    static TestPadding t3;
    static TestPadding t4;

    static TestPadding tpf = new TestPadding(); // Static field offset > 128

    int i1;

    long l1;
    long l2;
    long l3;
    long l4;
    long l5;
    long l6;
    long l7;
    long l8;
    long l9;
    long l10;
    long l11;
    long l12;
    long l13;
    long l14;
    long l15;
    long l16;

    byte b1 = 1; // Field offset > 128
}
