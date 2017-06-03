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

import org.junit.Assert;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.DominatorConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

/**
 * Collection of tests for
 * {@link org.graalvm.compiler.phases.common.DominatorConditionalEliminationPhase} including those
 * that triggered bugs in this phase.
 */
public class ConditionalEliminationTestBase extends GraalCompilerTest {

    private final boolean disableSimplification;

    protected ConditionalEliminationTestBase() {
        disableSimplification = true;
    }

    protected ConditionalEliminationTestBase(boolean disableSimplification) {
        this.disableSimplification = disableSimplification;
    }

    protected void testConditionalElimination(String snippet, String referenceSnippet) {
        testConditionalElimination(snippet, referenceSnippet, false);
    }

    @SuppressWarnings("try")
    protected void testConditionalElimination(String snippet, String referenceSnippet, boolean applyConditionalEliminationOnReference) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "Graph");
        PhaseContext context = new PhaseContext(getProviders());
        CanonicalizerPhase canonicalizer1 = new CanonicalizerPhase();
        if (disableSimplification) {
            /**
             * Some tests break if simplification is done so only do it when needed.
             */
            canonicalizer1.disableSimplification();
        }
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        try (Debug.Scope scope = Debug.scope("ConditionalEliminationTest", graph)) {
            canonicalizer1.apply(graph, context);
            new ConvertDeoptimizeToGuardPhase().apply(graph, context);
            // new DominatorConditionalEliminationPhase(true).apply(graph, context);
            new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);
            canonicalizer.apply(graph, context);
            canonicalizer.apply(graph, context);
            new ConvertDeoptimizeToGuardPhase().apply(graph, context);
        } catch (Throwable t) {
            Debug.handle(t);
        }
        StructuredGraph referenceGraph = parseEager(referenceSnippet, AllowAssumptions.YES);
        try (Debug.Scope scope = Debug.scope("ConditionalEliminationTest.ReferenceGraph", referenceGraph)) {

            new ConvertDeoptimizeToGuardPhase().apply(referenceGraph, context);
            if (applyConditionalEliminationOnReference) {
                new DominatorConditionalEliminationPhase(true).apply(referenceGraph, context);
                canonicalizer.apply(referenceGraph, context);
                canonicalizer.apply(referenceGraph, context);
            } else {
                canonicalizer.apply(referenceGraph, context);
            }
        } catch (Throwable t) {
            Debug.handle(t);
        }
        assertEquals(referenceGraph, graph);
    }

    public void testProxies(String snippet, int expectedProxiesCreated) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        PhaseContext context = new PhaseContext(getProviders());
        CanonicalizerPhase canonicalizer1 = new CanonicalizerPhase();
        canonicalizer1.disableSimplification();
        canonicalizer1.apply(graph, context);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);

        int baseProxyCount = graph.getNodes().filter(ProxyNode.class).count();
        new DominatorConditionalEliminationPhase(true).apply(graph, context);
        canonicalizer.apply(graph, context);
        new SchedulePhase().apply(graph, context);
        int actualProxiesCreated = graph.getNodes().filter(ProxyNode.class).count() - baseProxyCount;
        Assert.assertEquals(expectedProxiesCreated, actualProxiesCreated);
    }
}
