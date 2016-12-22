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

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.EarlyReadEliminationPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class RecursiveInliningTest extends GraalCompilerTest {

    public static int SideEffectI;
    public static int[] Memory = new int[]{1, 2};

    public static final Unsafe UNSAFE;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("Exception while trying to get Unsafe", e);
        }
    }

    public static void recursiveLoopMethodUnsafeLoad(int a) {
        if (UNSAFE.getInt(Memory, (long) Unsafe.ARRAY_LONG_BASE_OFFSET) == 0) {
            return;
        }
        for (int i = 0; i < a; i++) {
            recursiveLoopMethodUnsafeLoad(i);
        }
    }

    public static void recursiveLoopMethodFieldLoad(int a) {
        if (SideEffectI == 0) {
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

    public static final boolean LOG = false;

    public static int IterationsStart = 1;
    public static int IterationsEnd = 128;

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
        for (int i = IterationsStart; i < IterationsEnd; i++) {
            StructuredGraph graph = getGraph(snippet, i);
            long elapsed = runAndTimeEarlyReadEliminationPhase(graph);
            if (LOG) {
                System.out.printf("Needed %dms to run early read elimination on a graph with %d recursive inlined calls of method %s\n", elapsed, i, graph.method());
            }
        }
        for (int i = IterationsStart; i < IterationsEnd; i++) {
            StructuredGraph graph = getGraph(snippet, i);
            long elapsed = runAndTimePartialEscapeAnalysis(graph);
            if (LOG) {
                System.out.printf("Needed %dms to run early partial escape analysis on a graph with %d recursive inlined calls of method %s\n", elapsed, i, graph.method());
            }
        }
    }

    private long runAndTimePartialEscapeAnalysis(StructuredGraph g) {
        PartialEscapePhase p = new PartialEscapePhase(true, new CanonicalizerPhase());
        HighTierContext context = getDefaultHighTierContext();
        long start = System.currentTimeMillis();
        p.apply(g, context);
        long end = System.currentTimeMillis();
        Debug.dump(Debug.BASIC_LOG_LEVEL, g, "After PEA");
        return end - start;
    }

    private long runAndTimeEarlyReadEliminationPhase(StructuredGraph g) {
        EarlyReadEliminationPhase er = new EarlyReadEliminationPhase(new CanonicalizerPhase());
        HighTierContext context = getDefaultHighTierContext();
        long start = System.currentTimeMillis();
        er.apply(g, context);
        long end = System.currentTimeMillis();
        Debug.dump(Debug.BASIC_LOG_LEVEL, g, "After Early Read Elimination");
        return end - start;
    }

    @SuppressWarnings("try")
    private StructuredGraph getGraph(final String snippet, int nrOfInlinings) {
        try (Scope s = Debug.scope("RecursiveInliningTest", new DebugDumpScope(snippet, true))) {
            ResolvedJavaMethod callerMethod = getResolvedJavaMethod(snippet);
            StructuredGraph callerGraph = parseEager(callerMethod, AllowAssumptions.YES);
            PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
            HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase();

            for (int i = 0; i < nrOfInlinings; i++) {
                InvokeNode next = getNextInvoke(callerGraph);
                ResolvedJavaMethod calleeMethod = next.callTarget().targetMethod();
                StructuredGraph calleeGraph = getInlineeGraph(next, callerGraph, context, canonicalizer);
                List<Node> canonicalizeNodes = new ArrayList<>();
                InliningUtil.inline(next, calleeGraph, false, canonicalizeNodes, calleeMethod);
                canonicalizer.applyIncremental(callerGraph, context, canonicalizeNodes);
                Debug.dump(Debug.BASIC_LOG_LEVEL, callerGraph, "After inlining %s into %s iteration %d", calleeMethod, callerMethod, i);
            }
            new SchedulePhase().apply(callerGraph);
            return callerGraph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private static StructuredGraph getInlineeGraph(InvokeNode invoke, StructuredGraph caller, HighTierContext context, CanonicalizerPhase canonicalizer) {
        StructuredGraph result = InliningUtil.getIntrinsicGraph(context.getReplacements(), invoke.callTarget().targetMethod(), invoke.bci());
        if (result != null) {
            return result;
        }
        return parseBytecodes(invoke.callTarget().targetMethod(), context, canonicalizer, caller);
    }

    @SuppressWarnings("try")
    private static StructuredGraph parseBytecodes(ResolvedJavaMethod method, HighTierContext context, CanonicalizerPhase canonicalizer, StructuredGraph caller) {
        StructuredGraph newGraph = new StructuredGraph(method, AllowAssumptions.from(caller.getAssumptions() != null), INVALID_COMPILATION_ID);
        if (!caller.isUnsafeAccessTrackingEnabled()) {
            newGraph.disableUnsafeAccessTracking();
        }
        if (context.getGraphBuilderSuite() != null) {
            context.getGraphBuilderSuite().apply(newGraph, context);
        }
        assert newGraph.start().next() != null : "graph needs to be populated by the GraphBuilderSuite " + method + ", " + method.canBeInlined();
        new DeadCodeEliminationPhase(Optional).apply(newGraph);
        canonicalizer.apply(newGraph, context);
        return newGraph;
    }

    private static InvokeNode getNextInvoke(StructuredGraph graph) {
        return graph.getNodes().filter(InvokeNode.class).first();
    }
}
