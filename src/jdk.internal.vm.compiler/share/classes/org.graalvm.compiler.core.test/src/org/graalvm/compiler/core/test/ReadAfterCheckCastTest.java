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
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Assert;
import org.junit.Test;

/* consider
 *     B b = (B) a;
 *     return b.x10;
 *
 * With snippets a typecheck is performed and if it was successful, a PiNode is created.
 * For the read node, however, there is only a dependency to the PiNode, but not to the
 * typecheck itself. With special crafting, it's possible to get the scheduler moving the
 * FloatingReadNode before the typecheck. Assuming the object is of the wrong type (here for
 * example A), an invalid field read is done.
 *
 * In order to avoid this situation, an anchor node is introduced in CheckCastSnippts.
 */

public class ReadAfterCheckCastTest extends GraphScheduleTest {

    public static long foo = 0;

    public static class A {

        public long x1;
    }

    public static class B extends A {

        public long x10;
    }

    public static long test1Snippet(A a) {
        if (foo > 4) {
            B b = (B) a;
            b.x10 += 1;
            return b.x10;
        } else {
            B b = (B) a;
            b.x10 += 1;
            return b.x10;
        }
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    @SuppressWarnings("try")
    private void test(final String snippet) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope("ReadAfterCheckCastTest", new DebugDumpScope(snippet))) {
            // check shape of graph, with lots of assumptions. will probably fail if graph
            // structure changes significantly
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
            PhaseContext context = new PhaseContext(getProviders());
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            new FloatingReadPhase().apply(graph);
            canonicalizer.apply(graph, context);

            debug.dump(DebugContext.BASIC_LEVEL, graph, "After lowering");

            for (FloatingReadNode node : graph.getNodes(ParameterNode.TYPE).first().usages().filter(FloatingReadNode.class)) {
                // Checking that the parameter a is not directly used for the access to field
                // x10 (because x10 must be guarded by the checkcast).
                Assert.assertTrue(node.getLocationIdentity().isImmutable());
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
