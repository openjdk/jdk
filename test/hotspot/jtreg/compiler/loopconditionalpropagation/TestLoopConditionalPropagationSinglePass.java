/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

package compiler.loopconditionalpropagation;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8275202
 * @summary C2: optimize out more redundant conditions
 * @library /test/lib /
 * @run driver compiler.loopconditionalpropagation.TestLoopConditionalPropagationSinglePass
 */

public class TestLoopConditionalPropagationSinglePass {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = {IRNode.IF, "2"})
    private static void test1(int i, int j) {
        if (i - 1 <= 0) {
            throw new RuntimeException("never taken");
        }
        test1Helper(i, j);
        if (j < 10) {
            throw new RuntimeException("never taken");
        }
    }

    private static void test1Helper(int i, int j) {
        if (i == 0) {
            if (j >= 42) {
                throw new RuntimeException("never taken");
            }
        } else {
            if (j < 42) {
                throw new RuntimeException("never taken");
            }
        }
    }

    @Run(test = "test1")
    @Warmup(10_000)
    public static void test1Runner() {
        test1Helper(0, 0);
        test1(42, 42);
    }

}
