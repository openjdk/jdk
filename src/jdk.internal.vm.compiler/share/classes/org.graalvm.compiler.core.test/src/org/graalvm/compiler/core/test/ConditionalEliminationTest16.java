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
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Check that multiple bounds checks are correctly grouped together.
 */
public class ConditionalEliminationTest16 extends ConditionalEliminationTestBase {

    @Before
    public void resetType() {
        parameterType = null;
    }

    Class<?> parameterType;

    public static int testCastExactInstance(Object object) {
        if (object.getClass() == Integer.class) {
            return ((Integer) object).intValue();
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return -1;
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        if (parameterType != null) {
            for (ParameterNode param : graph.getNodes().filter(ParameterNode.class)) {
                if (param.index() == 0) {
                    ParameterNode newParam = new ParameterNode(0, StampPair.createSingle(StampFactory.object(TypeReference.createExactTrusted(getMetaAccess().lookupJavaType(parameterType)))));
                    graph.addWithoutUnique(newParam);
                    param.replaceAtUsages(newParam);
                    param.safeDelete();
                    break;
                }
            }
            this.createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        }
        super.checkHighTierGraph(graph);
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        int count = 0;
        for (PiNode node : graph.getNodes().filter(PiNode.class)) {
            assertTrue(node.getGuard() != null, "must have guarding node");
            count++;
        }
        assertTrue(count > 0, "expected at least one Pi");
        super.checkMidTierGraph(graph);
    }

    @Test
    public void test1() {
        parameterType = Integer.class;
        ResolvedJavaMethod method = getResolvedJavaMethod("testCastExactInstance");
        StructuredGraph graph = parseForCompile(method);
        compile(method, graph);
    }

    static class Base {
        int getValue1() {
            return 0;
        }

        Base getBase() {
            return this;
        }
    }

    static class Box extends Base {
        int value1;

        @Override
        int getValue1() {
            return value1;
        }
    }

    static class BiggerBox extends Box {
        int value2;

        int getValue2() {
            return value2;
        }
    }

    public static int testCastExactTwiceInstance(Base base, boolean b) {
        if (!(base instanceof Box)) {
            GraalDirectives.deoptimizeAndInvalidate();
            return -1;
        }
        int total = 0;
        if (base instanceof Box) {
            Box box = (Box) base;
            total += box.value1;
            if (b) {
                total += System.identityHashCode(base);
            }
            total += ((BiggerBox) base).getValue2();
        }
        return total;
    }

    @Test
    public void test2() {
        BiggerBox box = new BiggerBox();
        ResolvedJavaMethod method = getResolvedJavaMethod("testCastExactTwiceInstance");
        StructuredGraph graph = parseForCompile(method);
        compile(method, graph);
        test("testCastExactTwiceInstance", box, false);
    }
}
