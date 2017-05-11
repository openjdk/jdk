/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.DominatorConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;

public class GuardEliminationCornerCasesTest extends GraalCompilerTest {

    static class A {

    }

    static class B extends A {

    }

    static class C extends B {

    }

    static class D extends C {

    }

    @SuppressWarnings({"static-method", "unused"})
    private int testMethod(Object a) {
        if (a instanceof A) {
            if (a instanceof C) {
                if (a instanceof B) {
                    B b = (B) a;
                    if (b instanceof C) {
                        return 1;
                    } else {
                        GraalDirectives.deoptimizeAndInvalidate();
                    }
                }
            } else {
                GraalDirectives.deoptimizeAndInvalidate();
            }
        }
        return 0;
    }

    @Test
    public void testFloatingGuards() {
        HighTierContext context = getDefaultHighTierContext();
        StructuredGraph graph = parseEager("testMethod", AllowAssumptions.YES);
        new ConvertDeoptimizeToGuardPhase().apply(graph, context);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "after parsing");

        GuardNode myGuardNode = null;
        for (Node n : graph.getNodes()) {
            if (n instanceof GuardNode) {
                GuardNode guardNode = (GuardNode) n;
                LogicNode condition = guardNode.getCondition();
                if (condition instanceof InstanceOfNode) {
                    InstanceOfNode instanceOfNode = (InstanceOfNode) condition;
                    if (instanceOfNode.getValue() instanceof ValueProxy) {
                        myGuardNode = guardNode;
                        break;
                    }
                }
            }
        }

        AbstractBeginNode myBegin = (AbstractBeginNode) myGuardNode.getAnchor();
        AbstractBeginNode prevBegin = BeginNode.prevBegin((FixedNode) myBegin.predecessor());
        myGuardNode.setAnchor(prevBegin);

        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "after manual modification");
        graph.reverseUsageOrder();
        new DominatorConditionalEliminationPhase(true).apply(graph, context);
        new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST).apply(graph);
    }
}
