/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.GraalError;
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
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Test;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VerifyBailoutUsageTest {

    private static class InvalidBailoutUsagePhase1 extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new BailoutException("Bailout in graph %s", graph);
        }
    }

    private static class InvalidBailoutUsagePhase2 extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new BailoutException(new GraalError("other cause"), "Bailout in graph %s", graph);
        }
    }

    private static class InvalidBailoutUsagePhase3 extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new BailoutException(true/* permanent */, "Bailout in graph %s", graph);
        }
    }

    private static class ValidPermanentBailoutUsage extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new PermanentBailoutException("Valid permanent bailout %s", graph);
        }
    }

    private static class ValidRetryableBailoutUsage extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new RetryableBailoutException("Valid retryable bailout %s", graph);
        }
    }

    @Test(expected = VerificationError.class)
    public void testInvalidBailout01() {
        testBailoutUsage(InvalidBailoutUsagePhase1.class);
    }

    @Test(expected = VerificationError.class)
    public void testInvalidBailout02() {
        testBailoutUsage(InvalidBailoutUsagePhase2.class);
    }

    @Test(expected = VerificationError.class)
    public void testInvalidBailout03() {
        testBailoutUsage(InvalidBailoutUsagePhase3.class);
    }

    @Test
    public void testValidPermanentBailout() {
        testBailoutUsage(ValidPermanentBailoutUsage.class);
    }

    @Test
    public void testValidRetryableBailout() {
        testBailoutUsage(ValidRetryableBailoutUsage.class);
    }

    @SuppressWarnings("try")
    private static void testBailoutUsage(Class<?> c) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        Plugins plugins = new Plugins(new InvocationPlugins());
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);
        OptionValues options = getInitialOptions();
        DebugContext debug = new Builder(options).build();
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers())) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).build();
                graphBuilderSuite.apply(graph, context);
                try (DebugCloseable s = debug.disableIntercept()) {
                    new VerifyBailoutUsage().apply(graph, context);
                }
            }
        }
    }
}
