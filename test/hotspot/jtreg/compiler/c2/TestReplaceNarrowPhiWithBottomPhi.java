/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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

/**
 * @test
 * @bug 8370200
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.c2;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.Test;

public class TestReplaceNarrowPhiWithBottomPhi {
    private int field1;
    private volatile int field2;

    static public void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = { IRNode.PHI, "2" })
    public void test1() {
        int j;
        for (j = 0; j < 10; j++) {

        }
        inlined1(j);

        // Initially, there are 2 memory Phis: one for bottom, one for field1. After loop opts, both
        // Phis have the same inputs and the narrower Phi should be replaced by the bottom Phi.
        for (int i = 1; i < 100; i *= 2) {
            field2 = 42;
        }
    }

    private void inlined1(int j) {
        if (j == 42) {
            field1 = 42;
        }
    }

    @Run(test = "test1")
    private void test1Runner() {
        test1();
        inlined1(42);
    }
}
