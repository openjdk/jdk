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
package org.graalvm.compiler.microbenchmarks.graal.util;

import static org.graalvm.compiler.microbenchmarks.graal.util.GraalUtil.getGraph;
import static org.graalvm.compiler.microbenchmarks.graal.util.GraalUtil.getMethodFromMethodSpec;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugEnvironment;
import org.graalvm.compiler.debug.internal.DebugScope;
import org.graalvm.compiler.nodes.StructuredGraph;

/**
 * State providing a new copy of a graph for each invocation of a benchmark. Subclasses of this
 * class are annotated with {@link MethodSpec} to specify the Java method that will be parsed to
 * obtain the original graph.
 */
@State(Scope.Thread)
public abstract class GraphState {

    @SuppressWarnings("try")
    public GraphState() {
        // Ensure a debug configuration for this thread is initialized
        if (Debug.isEnabled() && DebugScope.getConfig() == null) {
            DebugEnvironment.initialize(System.out);
        }

        GraalState graal = new GraalState();
        ResolvedJavaMethod method = graal.metaAccess.lookupJavaMethod(getMethodFromMethodSpec(getClass()));
        StructuredGraph structuredGraph = null;
        try (Debug.Scope s = Debug.scope("GraphState", method)) {
            structuredGraph = preprocessOriginal(getGraph(graal, method));
        } catch (Throwable t) {
            Debug.handle(t);
        }
        this.originalGraph = structuredGraph;
    }

    protected StructuredGraph preprocessOriginal(StructuredGraph structuredGraph) {
        return structuredGraph;
    }

    /**
     * Original graph from which the per-benchmark invocation {@link #graph} is cloned.
     */
    private final StructuredGraph originalGraph;

    /**
     * The graph processed by the benchmark.
     */
    public StructuredGraph graph;

    @Setup(Level.Invocation)
    public void beforeInvocation() {
        graph = (StructuredGraph) originalGraph.copy();
    }
}
