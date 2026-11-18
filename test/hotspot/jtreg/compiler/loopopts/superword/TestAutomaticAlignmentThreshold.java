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
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @bug 8376728
 * @summary IR tests for SuperWordAutomaticAlignmentThreshold.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.loopopts.superword;

import java.util.Set;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

public class TestAutomaticAlignmentThreshold {
    public static int[] ARRAY = new int[1024];

    public static void main(String[] args) {
        TestFramework f = new TestFramework();
        f.addScenarios(
            new Scenario(0),
            new Scenario(1, "-XX:SuperWordAutomaticAlignmentThreshold=0"),
            new Scenario(2, "-XX:SuperWordAutomaticAlignmentThreshold=1"),
            new Scenario(3, "-XX:SuperWordAutomaticAlignmentThreshold=10000"),
            new Scenario(4, "-XX:SuperWordAutomaticAlignment=0"),
            new Scenario(5, "-XX:SuperWordAutomaticAlignment=1"),
            new Scenario(6, "-XX:SuperWordAutomaticAlignment=2"),
            new Scenario(7, "-XX:+AlignVector")
        );
        f.addCrossProductScenarios(Set.of("-XX:-AlignVector",
                                          "-XX:+AlignVector"),
                                   Set.of("-XX:SuperWordAutomaticAlignment=0",
                                          "-XX:SuperWordAutomaticAlignment=1"),
                                   Set.of("-XX:SuperWordAutomaticAlignmentThreshold=0",
                                          "-XX:SuperWordAutomaticAlignmentThreshold=10000"));
        f.start();
    }

    @Test
    // +AlignVector -> always align
    // -AlignVector:
    //     SuperWordAutomaticAlignment=0 -> no align
    //     SuperWordAutomaticAlignmentThreshold large -> no align
    //     else: align
    //
    // It would be nice to be able to express this directly, but the condition
    // requires both AND and OR, so I have to dissect it into multiple checks.
    //
    // No Alignment -> no AndI
    @IR(counts = {IRNode.AND_I, "= 0",
                  IRNode.LOAD_VECTOR_I, "> 0"},
        applyIfAnd = {"AlignVector", "false",
                      "SuperWordAutomaticAlignmentThreshold", ">= 4096"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    @IR(counts = {IRNode.AND_I, "= 0",
                  IRNode.LOAD_VECTOR_I, "> 0"},
        applyIfAnd = {"AlignVector", "false",
                      "SuperWordAutomaticAlignment", "= 0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    // Alignment -> AndI emitted
    @IR(counts = {IRNode.AND_I, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0"},
        applyIf = {"AlignVector", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    @IR(counts = {IRNode.AND_I, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0"},
        applyIfAnd = {"AlignVector", "false",
                      "SuperWordAutomaticAlignment", "> 0",
                      "SuperWordAutomaticAlignmentThreshold", "< 4096"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public static void test() {
        for (int i = 0; i < ARRAY.length; i++) {
            ARRAY[i]++;
        }
    }
}
