/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class PushNodesThroughPiTest extends GraalCompilerTest {

    public static class A {

        public long x = 20;
    }

    public static class B extends A {

        public long y = 10;
    }

    public static class C extends B {

        public long z = 5;
    }

    public static long test1Snippet(A a) {
        C c = (C) a;
        long ret = c.x; // this can be pushed before the checkcast
        ret += c.y; // not allowed to push
        ret += c.z; // not allowed to push
        // the null-check should be canonicalized with the null-check of the checkcast
        ret += c != null ? 100 : 200;
        return ret;
    }

    @Ignore
    @Test
    @SuppressWarnings("try")
    public void test1() {
        final String snippet = "test1Snippet";
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope("PushThroughPi", new DebugDumpScope(snippet))) {
            StructuredGraph graph = compileTestSnippet(snippet);
            for (ReadNode rn : graph.getNodes().filter(ReadNode.class)) {
                OffsetAddressNode address = (OffsetAddressNode) rn.getAddress();
                long disp = address.getOffset().asJavaConstant().asLong();

                ResolvedJavaType receiverType = StampTool.typeOrNull(address.getBase());
                ResolvedJavaField field = receiverType.findInstanceFieldWithOffset(disp, rn.getStackKind());

                assert field != null : "Node " + rn + " tries to access a field which doesn't exists for this type";
                if (field.getName().equals("x")) {
                    Assert.assertTrue(address.getBase() instanceof ParameterNode);
                } else {
                    Assert.assertTrue(address.getBase().toString(), address.getBase() instanceof PiNode);
                }
            }

            Assert.assertTrue(graph.getNodes().filter(IsNullNode.class).count() == 1);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private StructuredGraph compileTestSnippet(final String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        PhaseContext context = new PhaseContext(getProviders());
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);
        canonicalizer.apply(graph, context);

        return graph;
    }
}
