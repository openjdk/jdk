/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Check that multiple bounds checks are correctly grouped together.
 */
public class ConditionalEliminationTest16 extends ConditionalEliminationTestBase {

    public static int testCastExactInstance(Object object) {
        if (object.getClass() == Integer.class) {
            return ((Integer) object).intValue();
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return -1;
    }

    @Override
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        for (ParameterNode param : graph.getNodes().filter(ParameterNode.class)) {
            if (param.index() == 0) {
                ParameterNode newParam = new ParameterNode(0, StampPair.createSingle(StampFactory.object(TypeReference.createExactTrusted(getMetaAccess().lookupJavaType(Integer.class)))));
                graph.addWithoutUnique(newParam);
                param.replaceAtUsages(newParam);
                param.safeDelete();
                break;
            }
        }
        new CanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        return super.checkHighTierGraph(graph);
    }

    @Override
    protected boolean checkMidTierGraph(StructuredGraph graph) {
        int count = 0;
        for (PiNode node : graph.getNodes().filter(PiNode.class)) {
            assertTrue(node.getGuard() != null, "must have guarding node");
            count++;
        }
        assertTrue(count > 0, "expected at least one Pi");
        return super.checkMidTierGraph(graph);
    }

    @Test
    public void test1() {
        ResolvedJavaMethod method = getResolvedJavaMethod("testCastExactInstance");
        StructuredGraph graph = parseForCompile(method);
        compile(method, graph);
    }
}
