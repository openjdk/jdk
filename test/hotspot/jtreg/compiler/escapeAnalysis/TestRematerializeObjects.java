/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8370405
 * @summary Test elimination of array allocation, and the rematerialization.
 * @library /test/lib /
 * @run driver compiler.escapeAnalysis.TestRematerializeObjects xxxx
 */

package compiler.escapeAnalysis;

import jdk.test.lib.Utils;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;

/**
 * More complicated test cases can be found in {@link TestRematerializeObjectsFuzzing}.
 */
public class TestRematerializeObjects {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestRematerializeObjects.class);
        switch (args[0]) {
            case "xxxx" -> { framework.addFlags("-XX:+MergeStores"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
    }

    @Warmup(100)
    @Run(test = {"test"})
    public void runTests() {
    }

    @Test
    // @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
    //               IRNode.STORE_VECTOR, "> 0",
    //               ".*multiversion.*", "= 0"},
    //     phase = CompilePhase.PRINT_IDEAL,
    //     applyIfPlatform = {"64-bit", "true"},
    //     applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // // Should always vectorize, no speculative runtime check required.
    static void test() {
    }
}
