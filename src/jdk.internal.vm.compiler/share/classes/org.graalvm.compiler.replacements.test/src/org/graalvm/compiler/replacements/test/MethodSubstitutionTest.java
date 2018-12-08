/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import java.lang.reflect.InvocationTargetException;

import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.replacements.nodes.MacroNode;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests if {@link MethodSubstitution}s are inlined correctly. Most test cases only assert that
 * there are no remaining invocations in the graph. This is sufficient if the method that is being
 * substituted is a native method. For Java methods, additional checks are necessary.
 */
public abstract class MethodSubstitutionTest extends GraalCompilerTest {

    protected StructuredGraph testGraph(final String snippet) {
        return testGraph(snippet, null);
    }

    @SuppressWarnings("try")
    protected StructuredGraph testGraph(final String snippet, String name) {
        return testGraph(getResolvedJavaMethod(snippet), name);
    }

    @SuppressWarnings("try")
    protected StructuredGraph testGraph(final ResolvedJavaMethod method, String name) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope("MethodSubstitutionTest", method)) {
            StructuredGraph graph = parseEager(method, AllowAssumptions.YES, debug);
            HighTierContext context = getDefaultHighTierContext();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
            createInliningPhase().apply(graph, context);
            debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
            new CanonicalizerPhase().apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);
            // Try to ensure any macro nodes are lowered to expose any resulting invokes
            if (graph.getNodes().filter(MacroNode.class).isNotEmpty()) {
                new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            }
            if (graph.getNodes().filter(MacroNode.class).isNotEmpty()) {
                new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, context);
            }
            assertNotInGraph(graph, MacroNode.class);
            if (name != null) {
                for (Node node : graph.getNodes()) {
                    if (node instanceof Invoke) {
                        Invoke invoke = (Invoke) node;
                        if (invoke.callTarget() instanceof MethodCallTargetNode) {
                            MethodCallTargetNode call = (MethodCallTargetNode) invoke.callTarget();
                            assertTrue(!call.targetMethod().getName().equals(name), "Unexpected invoke of intrinsic %s", call.targetMethod());
                        }
                    }

                }
            } else {
                assertNotInGraph(graph, Invoke.class);
            }
            return graph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected static StructuredGraph assertNotInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node : graph.getNodes()) {
            if (clazz.isInstance(node)) {
                fail(node.toString());
            }
        }
        return graph;
    }

    protected void testSubstitution(String testMethodName, Class<?> intrinsicClass, Class<?> holder, String methodName, Class<?>[] parameterTypes, boolean optional, boolean forceCompilation,
                    Object[] args1, Object[] args2) {
        ResolvedJavaMethod realMethod = getResolvedJavaMethod(holder, methodName, parameterTypes);
        ResolvedJavaMethod testMethod = getResolvedJavaMethod(testMethodName);
        StructuredGraph graph = testGraph(testMethodName);

        // Check to see if the resulting graph contains the expected node
        StructuredGraph replacement = getReplacements().getSubstitution(realMethod, -1, false, null);
        if (replacement == null && !optional) {
            assertInGraph(graph, intrinsicClass);
        }

        // Force compilation
        InstalledCode code = getCode(testMethod, null, forceCompilation);
        assert optional || code != null;

        for (int i = 0; i < args1.length; i++) {
            Object arg1 = args1[i];
            Object arg2 = args2[i];
            Object expected = invokeSafe(realMethod, null, arg1, arg2);
            // Verify that the original method and the substitution produce the same value
            assertDeepEquals(expected, invokeSafe(testMethod, null, arg1, arg2));
            // Verify that the generated code and the original produce the same value
            assertDeepEquals(expected, executeVarargsSafe(code, arg1, arg2));
        }
    }

    protected static StructuredGraph assertInGraph(StructuredGraph graph, Class<?> clazz) {
        for (Node node : graph.getNodes()) {
            if (clazz.isInstance(node)) {
                return graph;
            }
        }
        fail("Graph does not contain a node of class " + clazz.getName());
        return graph;
    }

    protected static Object executeVarargsSafe(InstalledCode code, Object... args) {
        try {
            return code.executeVarargs(args);
        } catch (InvalidInstalledCodeException e) {
            throw new RuntimeException(e);
        }
    }

    protected Object invokeSafe(ResolvedJavaMethod method, Object receiver, Object... args) {
        try {
            return invoke(method, receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

}
