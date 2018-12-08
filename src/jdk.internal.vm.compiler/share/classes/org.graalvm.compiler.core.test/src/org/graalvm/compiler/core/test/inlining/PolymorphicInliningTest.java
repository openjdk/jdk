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


package org.graalvm.compiler.core.test.inlining;

import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.java;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.IOException;
import java.util.List;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.java.TypeSwitchNode;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.test.SubprocessUtil;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PolymorphicInliningTest extends GraalCompilerTest {

    @Before
    public void checkJavaAgent() {
        Assume.assumeFalse("Java Agent found -> skipping", SubprocessUtil.isJavaAgentAttached());
    }

    @Test
    public void testInSubprocess() throws InterruptedException, IOException {
        String recursionPropName = getClass().getName() + ".recursion";
        if (Boolean.getBoolean(recursionPropName)) {
            testPolymorphicInlining();
            testPolymorphicNotInlining();
            testMegamorphicInlining();
            testMegamorphicNotInlining();
        } else {
            List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
            NotInlinableSubClass.class.getCanonicalName();
            vmArgs.add("-XX:CompileCommand=dontinline,org/graalvm/compiler/core/test/inlining/PolymorphicInliningTest$NotInlinableSubClass.publicOverriddenMethod");
            vmArgs.add("-D" + recursionPropName + "=true");
            SubprocessUtil.Subprocess proc = java(vmArgs, "com.oracle.mxtool.junit.MxJUnitWrapper", getClass().getName());
            if (proc.exitCode != 0) {
                Assert.fail(String.format("non-zero exit code %d for command:%n%s", proc.exitCode, proc));
            }
        }
    }

    public int polymorphicCallsite(SuperClass receiver) {
        return receiver.publicOverriddenMethod();
    }

    public void testPolymorphicInlining() {
        for (int i = 0; i < 10000; i++) {
            if (i % 2 == 0) {
                polymorphicCallsite(Receivers.subClassA);
            } else {
                polymorphicCallsite(Receivers.subClassB);
            }
        }
        StructuredGraph graph = getGraph("polymorphicCallsite", false);
        // This callsite should be inlined with a TypeCheckedInliningViolated deoptimization.
        assertTrue(getNodeCount(graph, InvokeNode.class) == 0);
        assertTrue(getNodeCount(graph, TypeSwitchNode.class) == 1);
        assertTrue(getNodeCount(graph, DeoptimizeNode.class) >= 1);
    }

    /**
     * This snippet is identical to {@link #polymorphicCallsite(SuperClass)}, and is for avoiding
     * interference of the receiver type profile from different unit tests.
     */
    public int polymorphicCallsite1(SuperClass receiver) {
        return receiver.publicOverriddenMethod();
    }

    public void testPolymorphicNotInlining() {
        for (int i = 0; i < 10000; i++) {
            if (i % 2 == 0) {
                polymorphicCallsite1(Receivers.subClassA);
            } else {
                polymorphicCallsite1(Receivers.notInlinableSubClass);
            }
        }
        StructuredGraph graph = getGraph("polymorphicCallsite1", false);
        // This callsite should not be inlined due to one of the potential callee method is not
        // inlinable.
        assertTrue(getNodeCount(graph, InvokeNode.class) == 1);
        assertTrue(getNodeCount(graph, TypeSwitchNode.class) == 0);
    }

    /**
     * This snippet is identical to {@link #polymorphicCallsite(SuperClass)}, and is for avoiding
     * interference of the receiver type profile from different unit tests.
     */
    public int polymorphicCallsite2(SuperClass receiver) {
        return receiver.publicOverriddenMethod();
    }

    public void testMegamorphicInlining() {
        // Construct a receiver type profile that exceeds the max type width (by default 8 in JVMCI,
        // specified by -XX:TypeProfileWidth).
        for (int i = 0; i < 2000; i++) {
            // Ensure the following receiver type is within the type profile.
            polymorphicCallsite2(Receivers.subClassA);
        }
        for (int i = 0; i < 10000; i++) {
            switch (i % 20) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    // Probability: 40%
                    // Ensure the probability is greater than
                    // GraalOptions.MegamorphicInliningMinMethodProbability (by default 0.33D);
                    polymorphicCallsite2(Receivers.subClassA);
                    break;
                case 8:
                    polymorphicCallsite2(Receivers.subClassB);
                    break;
                case 9:
                    polymorphicCallsite2(Receivers.subClassC);
                    break;
                case 10:
                    polymorphicCallsite2(Receivers.subClassD);
                    break;
                case 11:
                    polymorphicCallsite2(Receivers.subClassE);
                    break;
                case 12:
                    polymorphicCallsite2(Receivers.subClassF);
                    break;
                case 13:
                    polymorphicCallsite2(Receivers.subClassG);
                    break;
                case 14:
                    polymorphicCallsite2(Receivers.subClassH);
                    break;
                default:
                    // Probability: 25%
                    polymorphicCallsite2(Receivers.notInlinableSubClass);
                    break;
            }
        }
        StructuredGraph graph = getGraph("polymorphicCallsite2", false);
        // This callsite should be inlined with a fallback invocation.
        assertTrue(getNodeCount(graph, InvokeNode.class) == 1);
        assertTrue(getNodeCount(graph, TypeSwitchNode.class) == 1);
    }

    /**
     * This snippet is identical to {@link #polymorphicCallsite(SuperClass)}, and is for avoiding
     * interference of the receiver type profile from different unit tests.
     */
    public int polymorphicCallsite3(SuperClass receiver) {
        return receiver.publicOverriddenMethod();
    }

    public void testMegamorphicNotInlining() {
        for (int i = 0; i < 10000; i++) {
            switch (i % 10) {
                case 0:
                case 1:
                    polymorphicCallsite3(Receivers.subClassA);
                    break;
                case 2:
                    polymorphicCallsite3(Receivers.subClassB);
                    break;
                case 3:
                    polymorphicCallsite3(Receivers.subClassC);
                    break;
                case 4:
                    polymorphicCallsite3(Receivers.subClassD);
                    break;
                case 5:
                    polymorphicCallsite3(Receivers.subClassE);
                    break;
                case 6:
                    polymorphicCallsite3(Receivers.subClassF);
                    break;
                case 7:
                    polymorphicCallsite3(Receivers.subClassG);
                    break;
                case 8:
                    polymorphicCallsite3(Receivers.subClassH);
                    break;
                default:
                    polymorphicCallsite3(Receivers.notInlinableSubClass);
                    break;
            }
        }
        StructuredGraph graph = getGraph("polymorphicCallsite3", false);
        // This callsite should not be inlined due to non of the potential callee method exceeds the
        // probability specified by GraalOptions.MegamorphicInliningMinMethodProbability.
        assertTrue(getNodeCount(graph, InvokeNode.class) == 1);
        assertTrue(getNodeCount(graph, TypeSwitchNode.class) == 0);
    }

    @SuppressWarnings("try")
    private StructuredGraph getGraph(final String snippet, final boolean eagerInfopointMode) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope("InliningTest", new DebugDumpScope(snippet, true))) {
            ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
            Builder builder = builder(method, AllowAssumptions.YES, debug);
            StructuredGraph graph = eagerInfopointMode ? parse(builder, getDebugGraphBuilderSuite()) : parse(builder, getEagerGraphBuilderSuite());
            try (DebugContext.Scope s2 = debug.scope("Inlining", graph)) {
                PhaseSuite<HighTierContext> graphBuilderSuite = eagerInfopointMode
                                ? getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withFullInfopoints(true))
                                : getDefaultGraphBuilderSuite();
                HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                new CanonicalizerPhase().apply(graph, context);
                createInliningPhase().apply(graph, context);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                new CanonicalizerPhase().apply(graph, context);
                new DeadCodeEliminationPhase().apply(graph);
                return graph;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private static int getNodeCount(StructuredGraph graph, Class<? extends Node> nodeClass) {
        return graph.getNodes().filter(nodeClass).count();
    }

    private static final class Receivers {
        static final SubClassA subClassA = new SubClassA();
        static final SubClassB subClassB = new SubClassB();
        static final SubClassC subClassC = new SubClassC();
        static final SubClassD subClassD = new SubClassD();
        static final SubClassE subClassE = new SubClassE();
        static final SubClassF subClassF = new SubClassF();
        static final SubClassG subClassG = new SubClassG();
        static final SubClassH subClassH = new SubClassH();

        static final NotInlinableSubClass notInlinableSubClass = new NotInlinableSubClass();
    }

    private abstract static class SuperClass {

        public abstract int publicOverriddenMethod();

    }

    private static class SubClassA extends SuperClass {

        @Override
        public int publicOverriddenMethod() {
            return 'A';
        }

    }

    private static class SubClassB extends SuperClass {

        @Override
        public int publicOverriddenMethod() {
            return 'B';
        }

    }

    private static class SubClassC extends SuperClass {

        @Override
        public int publicOverriddenMethod() {
            return 'C';
        }

    }

    private static class SubClassD extends SuperClass {

        @Override
        public int publicOverriddenMethod() {
            return 'D';
        }

    }

    private static class SubClassE extends SuperClass {

        @Override
        public int publicOverriddenMethod() {
            return 'E';
        }

    }

    private static class SubClassF extends SuperClass {

        @Override
        public int publicOverriddenMethod() {
            return 'F';
        }

    }

    private static class SubClassG extends SuperClass {

        @Override
        public int publicOverriddenMethod() {
            return 'G';
        }

    }

    private static class SubClassH extends SuperClass {

        @Override
        public int publicOverriddenMethod() {
            return 'H';
        }

    }

    private static final class NotInlinableSubClass extends SuperClass {

        @Override
        public int publicOverriddenMethod() {
            return 'X';
        }

    }

}
