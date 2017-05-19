/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;

import java.lang.reflect.Method;
import java.util.List;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;

public class GraalUtil {

    public static Method getMethod(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        try {
            if (parameterTypes == null) {
                Method found = null;
                for (Method m : declaringClass.getDeclaredMethods()) {
                    if (m.getName().equals(name)) {
                        if (found != null) {
                            throw new RuntimeException("more than one method named " + name + " in " + declaringClass);
                        }
                        found = m;
                    }
                }
                if (found == null) {
                    throw new NoSuchMethodException(declaringClass.getName() + "." + name);
                }
                return found;
            } else {
                return declaringClass.getDeclaredMethod(name, parameterTypes);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the first {@link MethodSpec} annotation encountered in the class hierarchy terminated by
     * {@code startClass}.
     */
    public static MethodSpec getMethodSpec(Class<?> startClass) {
        Class<?> c = startClass;
        while (c != null) {
            MethodSpec methodSpec = c.getAnnotation(MethodSpec.class);
            if (methodSpec != null) {
                return methodSpec;
            }
            c = c.getSuperclass();
        }
        throw new RuntimeException("Could not find class annotated with " + MethodSpec.class.getSimpleName() + " in hierarchy of " + startClass);
    }

    /**
     * Gets the method specified by the first {@link MethodSpec} annotation encountered in the class
     * hierarchy terminated by {@code startClass}.
     */
    public static Method getMethodFromMethodSpec(Class<?> startClass) {
        MethodSpec methodSpec = getMethodSpec(startClass);
        Class<?> declaringClass = methodSpec.declaringClass();
        if (declaringClass == MethodSpec.class) {
            declaringClass = startClass;
        }
        String name = methodSpec.name();
        Class<?>[] parameters = methodSpec.parameters();
        if (parameters.length == 1 && parameters[0] == void.class) {
            parameters = null;
        }
        return getMethod(declaringClass, name, parameters);
    }

    /**
     * Gets the graph for the method specified by the first {@link MethodSpec} annotation
     * encountered in the class hierarchy terminated by {@code startClass}.
     */
    public static StructuredGraph getGraphFromMethodSpec(Class<?> startClass) {
        MethodSpec methodSpec = getMethodSpec(startClass);
        Class<?> declaringClass = methodSpec.declaringClass();
        if (declaringClass == MethodSpec.class) {
            declaringClass = startClass;
        }
        String name = methodSpec.name();
        Class<?>[] parameters = methodSpec.parameters();
        if (parameters.length == 1 && parameters[0] == void.class) {
            parameters = null;
        }
        return getGraph(declaringClass, name, parameters);
    }

    public static StructuredGraph getGraph(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        return getGraph(getMethod(declaringClass, name, parameterTypes));
    }

    public static StructuredGraph getGraph(Method method) {
        GraalState graal = new GraalState();
        ResolvedJavaMethod javaMethod = graal.metaAccess.lookupJavaMethod(method);
        return getGraph(graal, javaMethod);
    }

    public static StructuredGraph getGraph(GraalState graal, ResolvedJavaMethod javaMethod) {
        return getGraph(graal, javaMethod, StructuredGraph.USE_PROFILING_INFO);
    }

    public static StructuredGraph getGraph(GraalState graal, ResolvedJavaMethod javaMethod, boolean useProfilingInfo) {
        StructuredGraph graph = new StructuredGraph(javaMethod, StructuredGraph.AllowAssumptions.YES, useProfilingInfo, INVALID_COMPILATION_ID);
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        MetaAccessProvider metaAccess = graal.providers.getMetaAccess();
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault(new Plugins(new InvocationPlugins(metaAccess)))));
        graphBuilderSuite.apply(graph, new HighTierContext(graal.providers, graphBuilderSuite, OptimisticOptimizations.ALL));
        return graph;
    }

    public static Node[] getNodes(StructuredGraph graph) {
        List<Node> nodeList = graph.getNodes().snapshot();
        return nodeList.toArray(new Node[nodeList.size()]);
    }
}
