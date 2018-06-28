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


package org.graalvm.compiler.core.test;

import static org.graalvm.compiler.core.test.GraalCompilerTest.getInitialOptions;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.VerifyPhase.VerificationError;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.phases.verify.VerifyDebugUsage;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Test;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VerifyDebugUsageTest {

    private static class InvalidLogUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            for (Node n : graph.getNodes()) {
                debug.log("%s", n.toString());
            }
        }

    }

    private static class InvalidLogAndIndentUsagePhase extends Phase {

        @Override
        @SuppressWarnings("try")
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            try (Indent i = debug.logAndIndent("%s", graph.toString())) {
                for (Node n : graph.getNodes()) {
                    debug.log("%s", n);
                }
            }
        }

    }

    private static class InvalidDumpUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "%s", graph.toString());
        }
    }

    private static class InvalidDumpLevelPhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(DebugContext.VERY_DETAILED_LEVEL + 1, graph, "%s", graph);
        }
    }

    private static class NonConstantDumpLevelPhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(getLevel(), graph, "%s", graph);
        }

        int getLevel() {
            return 10;
        }
    }

    private static class InvalidVerifyUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.verify(graph, "%s", graph.toString());
        }

    }

    private static class InvalidConcatLogUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            for (Node n : graph.getNodes()) {
                debug.log("error " + n);
            }
        }

    }

    private static class InvalidConcatLogAndIndentUsagePhase extends Phase {

        @Override
        @SuppressWarnings("try")
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            try (Indent i = debug.logAndIndent("error " + graph)) {
                for (Node n : graph.getNodes()) {
                    debug.log("%s", n);
                }
            }
        }

    }

    private static class InvalidConcatDumpUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "error " + graph);
        }

    }

    private static class InvalidConcatVerifyUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.verify(graph, "error " + graph);
        }

    }

    private static class ValidLogUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            for (Node n : graph.getNodes()) {
                debug.log("%s", n);
            }
        }

    }

    private static class ValidLogAndIndentUsagePhase extends Phase {

        @Override
        @SuppressWarnings("try")
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            try (Indent i = debug.logAndIndent("%s", graph)) {
                for (Node n : graph.getNodes()) {
                    debug.log("%s", n);
                }
            }
        }

    }

    private static class ValidDumpUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "%s", graph);
        }

    }

    private static class ValidVerifyUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.verify(graph, "%s", graph);
        }

    }

    private static class InvalidGraalErrorGuaranteePhase extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            GraalError.guarantee(graph.getNodes().count() > 0, "Graph must contain nodes %s %s %s", graph, graph, graph.toString());
        }
    }

    private static class ValidGraalErrorGuaranteePhase extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            GraalError.guarantee(graph.getNodes().count() > 0, "Graph must contain nodes %s", graph);
        }
    }

    public static Object sideEffect;

    private static class InvalidGraalErrorCtorPhase extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            sideEffect = new GraalError("No Error %s", graph.toString());
        }
    }

    private static class ValidGraalErrorCtorPhase extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            sideEffect = new GraalError("Error %s", graph);
        }
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalid() {
        testDebugUsageClass(InvalidLogUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogAndIndentInvalid() {
        testDebugUsageClass(InvalidLogAndIndentUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testVerifyInvalid() {
        testDebugUsageClass(InvalidVerifyUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testDumpInvalid() {
        testDebugUsageClass(InvalidDumpUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testDumpLevelInvalid() {
        testDebugUsageClass(InvalidDumpLevelPhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testDumpNonConstantLevelInvalid() {
        testDebugUsageClass(NonConstantDumpLevelPhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat() {
        testDebugUsageClass(InvalidConcatLogUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogAndIndentInvalidConcat() {
        testDebugUsageClass(InvalidConcatLogAndIndentUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testVerifyInvalidConcat() {
        testDebugUsageClass(InvalidConcatVerifyUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testDumpInvalidConcat() {
        testDebugUsageClass(InvalidConcatDumpUsagePhase.class);
    }

    @Test
    public void testLogValid() {
        testDebugUsageClass(ValidLogUsagePhase.class);
    }

    @Test()
    public void testLogAndIndentValid() {
        testDebugUsageClass(ValidLogAndIndentUsagePhase.class);
    }

    @Test
    public void testVerifyValid() {
        testDebugUsageClass(ValidVerifyUsagePhase.class);
    }

    @Test
    public void testDumpValid() {
        testDebugUsageClass(ValidDumpUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testGraalGuaranteeInvalid() {
        testDebugUsageClass(InvalidGraalErrorGuaranteePhase.class);
    }

    @Test
    public void testGraalGuaranteeValid() {
        testDebugUsageClass(ValidGraalErrorGuaranteePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testGraalCtorInvalid() {
        testDebugUsageClass(InvalidGraalErrorCtorPhase.class);
    }

    @Test
    public void testGraalCtorValid() {
        testDebugUsageClass(ValidGraalErrorCtorPhase.class);
    }

    @SuppressWarnings("try")
    private static void testDebugUsageClass(Class<?> c) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        Plugins plugins = new Plugins(new InvocationPlugins());
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);
        OptionValues options = getInitialOptions();
        DebugContext debug = DebugContext.create(options, DebugHandlersFactory.LOADER);
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers())) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).build();
                graphBuilderSuite.apply(graph, context);
                try (DebugCloseable s = debug.disableIntercept()) {
                    new VerifyDebugUsage().apply(graph, context);
                }
            }
        }
    }
}
