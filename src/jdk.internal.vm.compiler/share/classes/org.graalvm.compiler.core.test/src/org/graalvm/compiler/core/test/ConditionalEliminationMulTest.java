/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Test;

public class ConditionalEliminationMulTest extends GraalCompilerTest {

    public static void snippet01(int a) {
        if (a == 3) {
            if (a * 11 != 33) {
                shouldBeOptimizedAway();
            }
        }
    }

    public static void snippet02(int a) {
        if (a == 0) {
            if (a * 11 != 0) {
                shouldBeOptimizedAway();
            }
        }
    }

    public static void snippet03(int a) {
        if (a * 0 == 6) {
            shouldBeOptimizedAway();
        }
    }

    @Test
    public void testConditionalEliminated01() {
        assertOptimized("snippet01");
    }

    @Test
    public void testConditionalEliminated02() {
        assertOptimized("snippet02");
    }

    @Test
    public void testConditionalEliminated03() {
        assertOptimized("snippet03");
    }

    private void assertOptimized(String snippet) {
        assertOptimizedAway(prepareGraph(snippet));
    }

    private StructuredGraph prepareGraph(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        new ConditionalEliminationPhase(false).apply(graph, context);
        CanonicalizerPhase c = new CanonicalizerPhase();
        c.apply(graph, context);
        new ConditionalEliminationPhase(false).apply(graph, context);
        c.apply(graph, context);
        return graph;
    }
}
