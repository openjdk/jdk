/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.inlining;

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.virtual.phases.ea.EarlyReadEliminationPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.util.EconomicSet;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class NestedLoopEffectsPhaseComplexityTest extends GraalCompilerTest {

    public static int IntSideEffect;
    public static int[] Memory = new int[]{0};

    public static void recursiveLoopMethodUnsafeLoad(int a) {
        if (UNSAFE.getInt(Memory, (long) Unsafe.ARRAY_INT_BASE_OFFSET) == 0) {
            return;
        }
        for (int i = 0; i < a; i++) {
            recursiveLoopMethodUnsafeLoad(i);
        }
    }

    public static void recursiveLoopMethodFieldLoad(int a) {
        if (IntSideEffect == 0) {
            return;
        }
        for (int i = 0; i < a; i++) {
            recursiveLoopMethodFieldLoad(i);
        }
    }

    public static void recursiveLoopMethod(int a) {
        if (a == 0) {
            return;
        }
        for (int i = 0; i < a; i++) {
            recursiveLoopMethod(i);
        }
    }

    private static final boolean LOG_PHASE_TIMINGS = false;
    private static int InliningCountLowerBound = 1;
    private static int InliningCountUpperBound = 32;

    @Test(timeout = 120_000)
    public void inlineDirectRecursiveLoopCallUnsafeLoad() {
        testAndTime("recursiveLoopMethodUnsafeLoad");
    }

    @Test(timeout = 120_000)
    public void inlineDirectRecursiveLoopCallFieldLoad() {
        testAndTime("recursiveLoopMethodFieldLoad");
    }

    @Test(timeout = 120_000)
    public void inlineDirectRecursiveLoopCallNoReads() {
        testAndTime("recursiveLoopMethod");
    }

    private void testAndTime(String snippet) {
        for (int i = InliningCountLowerBound; i < InliningCountUpperBound; i++) {
            StructuredGraph g1 = prepareGraph(snippet, i);
            StructuredGraph g2 = (StructuredGraph) g1.copy(g1.getDebug());
            ResolvedJavaMethod method = g1.method();
            long elapsedRE = runAndTimePhase(g1, new EarlyReadEliminationPhase(new CanonicalizerPhase()));
            long elapsedPEA = runAndTimePhase(g2, new PartialEscapePhase(true, new CanonicalizerPhase(), g1.getOptions()));
            if (LOG_PHASE_TIMINGS) {
                TTY.printf("Needed %dms to run early partial escape analysis on a graph with %d nested loops compiling method %s\n", elapsedPEA, i, method);
            }
            if (LOG_PHASE_TIMINGS) {
                TTY.printf("Needed %dms to run early read elimination on a graph with %d nested loops compiling method %s\n", elapsedRE, i, method);
            }
        }
    }

    private long runAndTimePhase(StructuredGraph g, BasePhase<? super PhaseContext> phase) {
        HighTierContext context = getDefaultHighTierContext();
        long start = System.currentTimeMillis();
        phase.apply(g, context);
        long end = System.currentTimeMillis();
        DebugContext debug = g.getDebug();
        debug.dump(DebugContext.DETAILED_LEVEL, g, "After %s", phase.contractorName());
        return end - start;
    }

    private StructuredGraph prepareGraph(String snippet, int inliningCount) {
        ResolvedJavaMethod callerMethod = getResolvedJavaMethod(snippet);
        StructuredGraph callerGraph = parseEager(callerMethod, AllowAssumptions.YES);
        PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
        HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        Invoke next = callerGraph.getNodes(MethodCallTargetNode.TYPE).first().invoke();
        StructuredGraph calleeGraph = parseBytecodes(next.callTarget().targetMethod(), context, canonicalizer);
        ResolvedJavaMethod calleeMethod = next.callTarget().targetMethod();
        for (int i = 0; i < inliningCount; i++) {
            next = callerGraph.getNodes(MethodCallTargetNode.TYPE).first().invoke();
            EconomicSet<Node> canonicalizeNodes = InliningUtil.inlineForCanonicalization(next, calleeGraph, false, calleeMethod);
            canonicalizer.applyIncremental(callerGraph, context, canonicalizeNodes);
            callerGraph.getDebug().dump(DebugContext.DETAILED_LEVEL, callerGraph, "After inlining %s into %s iteration %d", calleeMethod, callerMethod, i);
        }
        return callerGraph;
    }

    private StructuredGraph parseBytecodes(ResolvedJavaMethod method, HighTierContext context, CanonicalizerPhase canonicalizer) {
        OptionValues options = getInitialOptions();
        StructuredGraph newGraph = new StructuredGraph.Builder(options, getDebugContext(options, null, method), AllowAssumptions.NO).method(method).build();
        context.getGraphBuilderSuite().apply(newGraph, context);
        new DeadCodeEliminationPhase(Optional).apply(newGraph);
        canonicalizer.apply(newGraph, context);
        return newGraph;
    }

}
