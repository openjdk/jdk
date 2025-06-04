/*
 * Copyright (c) 2024, Red Hat and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8330163
 * @summary C2: improve CMoveNode::Value() when condition is always true or false
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestCMoveCCP
 */


public class TestCMoveCCP {
    private static volatile int volatileField;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = { IRNode.COUNTED_LOOP, IRNode.LOOP })
    private static void test1() {
        int i = -1;
        testHelper1(i);
    }

    @ForceInline
    private static void testHelper1(int i) {
        do {
            if (i != 1) {
                if (i < 0) { // Converted to CMoveI
                    i = 50;
                }
                volatileField = 42;
            }
            i *= 2;
            // i = 100 on first iteration when called from test1
        } while (i != 100);
    }

    @Run(test = "test1")
    @Warmup(10000)
    private static void testRunner1() {
        test1();
        testHelper1(1);
    }
}
