/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.extended.MonitorExit;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class FloatingReadTest extends GraphScheduleTest {

    public static class Container {

        public int a;
    }

    public static void changeField(Container c) {
        c.a = 0xcafebabe;
    }

    public static synchronized int test1Snippet() {
        Container c = new Container();
        return c.a;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    @SuppressWarnings("try")
    private void test(final String snippet) {
        try (Scope s = Debug.scope("FloatingReadTest", new DebugDumpScope(snippet))) {

            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
            PhaseContext context = new PhaseContext(getProviders());
            new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            new FloatingReadPhase().apply(graph);

            ReturnNode returnNode = null;
            MonitorExit monitorexit = null;

            for (Node n : graph.getNodes()) {
                if (n instanceof ReturnNode) {
                    assert returnNode == null;
                    returnNode = (ReturnNode) n;
                } else if (n instanceof MonitorExit) {
                    monitorexit = (MonitorExit) n;
                }
            }

            Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "After lowering");

            Assert.assertNotNull(returnNode);
            Assert.assertNotNull(monitorexit);
            Assert.assertTrue(returnNode.result() instanceof FloatingReadNode);

            FloatingReadNode read = (FloatingReadNode) returnNode.result();

            assertOrderedAfterSchedule(graph, read, (Node) monitorexit);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
