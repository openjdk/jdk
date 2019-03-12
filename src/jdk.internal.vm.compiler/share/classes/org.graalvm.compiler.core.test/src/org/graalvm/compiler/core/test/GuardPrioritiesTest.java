/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.graph.test.matchers.NodeIterableCount.hasCount;
import static org.graalvm.compiler.graph.test.matchers.NodeIterableIsEmpty.isNotEmpty;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import java.util.Iterator;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.IntegerLowerThanNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Test;

import jdk.vm.ci.meta.SpeculationLog;

public class GuardPrioritiesTest extends GraphScheduleTest {
    private int[] array;
    private int size;

    public void growing(int e) {
        if (size >= array.length) {
            // grow
            GraalDirectives.deoptimizeAndInvalidateWithSpeculation();
        }
        array[size++] = e;
    }

    @Test
    public void growingTest() {
        assumeTrue("GuardPriorities must be turned one", GraalOptions.GuardPriorities.getValue(getInitialOptions()));
        StructuredGraph graph = prepareGraph("growing");

        NodeIterable<GuardNode> guards = graph.getNodes(GuardNode.TYPE).filter(n -> n.inputs().filter(i -> i instanceof IntegerLowerThanNode).isNotEmpty());
        assertThat(guards, isNotEmpty());
        assumeThat(guards, hasCount(2));

        Iterator<GuardNode> iterator = guards.iterator();
        GuardNode g1 = iterator.next();
        GuardNode g2 = iterator.next();
        assertTrue("There should be one guard with speculation, the other one without",
                        (g1.getSpeculation().equals(SpeculationLog.NO_SPECULATION)) ^ (g2.getSpeculation().equals(SpeculationLog.NO_SPECULATION)));
        GuardNode withSpeculation = g1.getSpeculation().equals(SpeculationLog.NO_SPECULATION) ? g2 : g1;
        GuardNode withoutSpeculation = g1.getSpeculation().equals(SpeculationLog.NO_SPECULATION) ? g1 : g2;

        assertOrderedAfterSchedule(graph, SchedulePhase.SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER, withSpeculation, withoutSpeculation);
    }

    private StructuredGraph prepareGraph(String method) {
        StructuredGraph graph = parseEager(method, StructuredGraph.AllowAssumptions.YES);
        HighTierContext highTierContext = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        new ConvertDeoptimizeToGuardPhase().apply(graph, highTierContext);
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, highTierContext);
        new FloatingReadPhase().apply(graph);
        return graph;
    }

    public int unknownCondition(Integer c, Object o, int[] a, Integer i) {
        if (o != null) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if (i > 5560) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        if (c >= 10) {
            GraalDirectives.deoptimizeAndInvalidateWithSpeculation();
        }
        return array[8] + a[i];
    }

    @Test
    public void unknownTest() {
        assumeTrue("GuardPriorities must be turned one", GraalOptions.GuardPriorities.getValue(getInitialOptions()));
        StructuredGraph graph = prepareGraph("unknownCondition");

        new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER).apply(graph);
        for (GuardNode g1 : graph.getNodes(GuardNode.TYPE)) {
            for (GuardNode g2 : graph.getNodes(GuardNode.TYPE)) {
                if (g1.getSpeculation().equals(SpeculationLog.NO_SPECULATION) ^ g2.getSpeculation().equals(SpeculationLog.NO_SPECULATION)) {
                    GuardNode withSpeculation = g1.getSpeculation().equals(SpeculationLog.NO_SPECULATION) ? g2 : g1;
                    GuardNode withoutSpeculation = g1.getSpeculation().equals(SpeculationLog.NO_SPECULATION) ? g1 : g2;

                    if (withoutSpeculation.isNegated() && withoutSpeculation.getCondition() instanceof IsNullNode) {
                        IsNullNode isNullNode = (IsNullNode) withoutSpeculation.getCondition();
                        if (isNullNode.getValue() instanceof ParameterNode && ((ParameterNode) isNullNode.getValue()).index() == 1) {
                            // this is the null check before the speculative guard, it's the only
                            // one that should be above
                            assertOrderedAfterLastSchedule(graph, withoutSpeculation, withSpeculation);
                            continue;
                        }
                    }

                    assertOrderedAfterLastSchedule(graph, withSpeculation, withoutSpeculation);
                }
            }
        }
    }
}
