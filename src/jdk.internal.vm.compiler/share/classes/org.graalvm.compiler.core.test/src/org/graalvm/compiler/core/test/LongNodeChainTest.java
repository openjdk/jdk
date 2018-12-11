/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.JavaConstant;

import org.junit.Assert;
import org.junit.Test;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.extended.OpaqueNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.HighTierContext;

public class LongNodeChainTest extends GraalCompilerTest {

    public static final int N = 10000;

    private static final SchedulingStrategy[] Strategies = new SchedulingStrategy[]{SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER};

    @Test
    public void testLongAddChain() {
        longAddChain(true);
        longAddChain(false);
    }

    private void longAddChain(boolean reverse) {
        HighTierContext context = getDefaultHighTierContext();
        OptionValues options = getInitialOptions();
        StructuredGraph graph = new StructuredGraph.Builder(options, DebugContext.create(options, DebugHandlersFactory.LOADER)).build();
        ValueNode constant = graph.unique(ConstantNode.forPrimitive(JavaConstant.INT_1));
        ValueNode value = null;
        if (reverse) {
            // Make sure the constant's stamp is not used to infer the add node's stamp.
            OpaqueNode opaque = graph.unique(new OpaqueNode(constant));
            constant = opaque;
            AddNode addNode = graph.unique(new AddNode(constant, constant));
            value = addNode;
            for (int i = 1; i < N; ++i) {
                AddNode newAddNode = graph.addWithoutUnique(new AddNode(constant, constant));
                addNode.setY(newAddNode);
                addNode = newAddNode;
            }
            opaque.remove();
        } else {
            value = constant;
            for (int i = 0; i < N; ++i) {
                value = graph.unique(new AddNode(constant, value));
            }
        }
        ReturnNode returnNode = graph.add(new ReturnNode(value));
        graph.start().setNext(returnNode);

        for (SchedulingStrategy s : Strategies) {
            new SchedulePhase(s).apply(graph);
        }

        new CanonicalizerPhase().apply(graph, context);
        JavaConstant asConstant = (JavaConstant) returnNode.result().asConstant();
        Assert.assertEquals(N + 1, asConstant.asInt());
    }
}
