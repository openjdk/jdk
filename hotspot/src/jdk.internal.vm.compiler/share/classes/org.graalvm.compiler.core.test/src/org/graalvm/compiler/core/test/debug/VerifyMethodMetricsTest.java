/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.debug;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugConfigScope;
import org.graalvm.compiler.debug.DebugMethodMetrics;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.VerifyPhase.VerificationError;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.phases.verify.VerifyDebugUsage;
import org.graalvm.compiler.runtime.RuntimeProvider;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 *
 * Tests to verify that the usage of method metrics does not generate compile time overhead through
 * eager evaluation of arguments.
 */
public class VerifyMethodMetricsTest {

    private static class InvalidCCP_ToString01Inc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric(n.toString());
            }
        }
    }

    private static class InvalidCCP_Concat01Inc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric("a" + n.toString());
            }
        }
    }

    private static class InvalidCCP_ToString02Inc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric("%s", n.toString());
            }
        }
    }

    private static class InvalidCCP_Concat02Inc extends Phase {
        private final String s = this.getClass().toGenericString();

        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric("%s%s", "a" + s, n);
            }
        }
    }

    private static class ValidCCP_ToStringInc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s", n);
            }
        }
    }

    private static class ValidCCP_ConcatInc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric("%s%s", "a", n);
            }
        }
    }

    private static class InvalidCCP_ToString01Add extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, n.toString());
            }
        }
    }

    private static class InvalidCCP_Concat01Add extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "a" + n.toString());
            }
        }
    }

    private static class InvalidCCP_ToString02Add extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s", n.toString());
            }
        }
    }

    private static class InvalidCCP_Concat02Add extends Phase {
        private final String s = this.getClass().toGenericString();

        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s%s", "a" + s, n);
            }
        }
    }

    private static class ValidCCP_ToStringAdd extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s", n);
            }
        }
    }

    private static class ValidCCP_ConcatAdd extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s%s", "a", n);
            }
        }
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidToString01Add() {
        testDebugUsageClass(InvalidCCP_ToString01Add.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat01Add() {
        testDebugUsageClass(InvalidCCP_Concat01Add.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidToString02Add() {
        testDebugUsageClass(InvalidCCP_ToString02Add.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat02Add() {
        testDebugUsageClass(InvalidCCP_Concat02Add.class);
    }

    @Test
    public void testLogValidToStringAdd() {
        testDebugUsageClass(ValidCCP_ToStringAdd.class);
    }

    @Test
    public void testLogValidConcatAdd() {
        testDebugUsageClass(ValidCCP_ConcatAdd.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidToString01Inc() {
        testDebugUsageClass(InvalidCCP_ToString01Inc.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat01Inc() {
        testDebugUsageClass(InvalidCCP_Concat01Inc.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidToString02Inc() {
        testDebugUsageClass(InvalidCCP_ToString02Inc.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat02Inc() {
        testDebugUsageClass(InvalidCCP_Concat02Inc.class);
    }

    @Test
    public void testLogValidToStringInc() {
        testDebugUsageClass(ValidCCP_ToStringInc.class);
    }

    @Test
    public void testLogValidConcatInc() {
        testDebugUsageClass(ValidCCP_ConcatInc.class);
    }

    @SuppressWarnings("try")
    private static void testDebugUsageClass(Class<?> c) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        Plugins plugins = new Plugins(new InvocationPlugins());
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers())) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO, INVALID_COMPILATION_ID);
                graphBuilderSuite.apply(graph, context);
                try (DebugConfigScope s = Debug.disableIntercept()) {
                    new VerifyDebugUsage().apply(graph, context);
                }
            }
        }
    }
}
