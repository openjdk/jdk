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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.OptimisticOptimizations.Optimization;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Assert;

/**
 * Collection of tests for {@link org.graalvm.compiler.phases.common.ConditionalEliminationPhase}
 * including those that triggered bugs in this phase.
 */
public class ConditionalEliminationTestBase extends GraalCompilerTest {
    protected static int sink0;
    protected static int sink1;
    protected static int sink2;

    /**
     * These tests assume all code paths in called routines are reachable so disable removal of dead
     * code based on method profiles.
     */
    @Override
    protected HighTierContext getDefaultHighTierContext() {
        return new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL.remove(Optimization.RemoveNeverExecutedCode));
    }

    protected void testConditionalElimination(String snippet, String referenceSnippet) {
        testConditionalElimination(snippet, referenceSnippet, false, false);
    }

    @SuppressWarnings("try")
    protected void testConditionalElimination(String snippet, String referenceSnippet, boolean applyConditionalEliminationOnReference, boolean applyLowering) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
        PhaseContext context = new PhaseContext(getProviders());
        CanonicalizerPhase canonicalizer1 = new CanonicalizerPhase();
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        try (DebugContext.Scope scope = debug.scope("ConditionalEliminationTest", graph)) {
            prepareGraph(graph, canonicalizer1, context, applyLowering);
            new IterativeConditionalEliminationPhase(canonicalizer, true).apply(graph, context);
            canonicalizer.apply(graph, context);
            canonicalizer.apply(graph, context);
        } catch (Throwable t) {
            debug.handle(t);
        }
        StructuredGraph referenceGraph = parseEager(referenceSnippet, AllowAssumptions.YES);
        try (DebugContext.Scope scope = debug.scope("ConditionalEliminationTest.ReferenceGraph", referenceGraph)) {
            prepareGraph(referenceGraph, canonicalizer, context, applyLowering);
            if (applyConditionalEliminationOnReference) {
                new ConditionalEliminationPhase(true).apply(referenceGraph, context);
            }
            canonicalizer.apply(referenceGraph, context);
            canonicalizer.apply(referenceGraph, context);
        } catch (Throwable t) {
            debug.handle(t);
        }
        assertEquals(referenceGraph, graph);
    }

    protected void prepareGraph(StructuredGraph graph, CanonicalizerPhase canonicalizer, PhaseContext context, boolean applyLowering) {
        if (applyLowering) {
            new ConvertDeoptimizeToGuardPhase().apply(graph, context);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            canonicalizer.apply(graph, context);
        }
        canonicalizer.apply(graph, context);
        new ConvertDeoptimizeToGuardPhase().apply(graph, context);
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
        new ConditionalEliminationPhase(true).apply(graph, context);
        canonicalizer.apply(graph, context);
        new SchedulePhase(graph.getOptions()).apply(graph, context);
        int actualProxiesCreated = graph.getNodes().filter(ProxyNode.class).count() - baseProxyCount;
        Assert.assertEquals(expectedProxiesCreated, actualProxiesCreated);
    }
}
