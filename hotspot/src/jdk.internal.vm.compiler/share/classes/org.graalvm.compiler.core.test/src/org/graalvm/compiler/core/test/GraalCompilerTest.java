/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.GraalCompilerOptions.PrintCompilation;
import static org.graalvm.compiler.nodes.ConstantNode.getConstantNodes;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.GraalCompiler.Request;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.ComputeLoopFrequenciesClosure;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.DerivedOptionValue;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.TargetProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.test.GraalTest;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Base class for Graal compiler unit tests.
 * <p>
 * White box tests for Graal compiler transformations use this pattern:
 * <ol>
 * <li>Create a graph by {@linkplain #parseEager(String, AllowAssumptions) parsing} a method.</li>
 * <li>Manually modify the graph (e.g. replace a parameter node with a constant).</li>
 * <li>Apply a transformation to the graph.</li>
 * <li>Assert that the transformed graph is equal to an expected graph.</li>
 * </ol>
 * <p>
 * See {@link InvokeHintsTest} as an example of a white box test.
 * <p>
 * Black box tests use the {@link #test(String, Object...)} or
 * {@link #testN(int, String, Object...)} to execute some method in the interpreter and compare its
 * result against that produced by a Graal compiled version of the method.
 * <p>
 * These tests will be run by the {@code mx unittest} command.
 */
public abstract class GraalCompilerTest extends GraalTest {

    private final Providers providers;
    private final Backend backend;
    private final DerivedOptionValue<Suites> suites;
    private final DerivedOptionValue<LIRSuites> lirSuites;

    /**
     * Denotes a test method that must be inlined by the {@link BytecodeParser}.
     */
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface BytecodeParserForceInline {
    }

    /**
     * Denotes a test method that must never be inlined by the {@link BytecodeParser}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    public @interface BytecodeParserNeverInline {
        /**
         * Specifies if the call should be implemented with {@link InvokeWithExceptionNode} instead
         * of {@link InvokeNode}.
         */
        boolean invokeWithException() default false;
    }

    /**
     * Can be overridden by unit tests to verify properties of the graph.
     *
     * @param graph the graph at the end of HighTier
     */
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        return true;
    }

    /**
     * Can be overridden by unit tests to verify properties of the graph.
     *
     * @param graph the graph at the end of MidTier
     */
    protected boolean checkMidTierGraph(StructuredGraph graph) {
        return true;
    }

    /**
     * Can be overridden by unit tests to verify properties of the graph.
     *
     * @param graph the graph at the end of LowTier
     */
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        return true;
    }

    protected static void breakpoint() {
    }

    @SuppressWarnings("unused")
    protected static void breakpoint(int arg0) {
    }

    protected static void shouldBeOptimizedAway() {
    }

    protected Suites createSuites() {
        Suites ret = backend.getSuites().getDefaultSuites().copy();
        ListIterator<BasePhase<? super HighTierContext>> iter = ret.getHighTier().findPhase(ConvertDeoptimizeToGuardPhase.class, true);
        if (iter == null) {
            /*
             * in the economy configuration, we don't have the ConvertDeoptimizeToGuard phase, so we
             * just select the first CanonicalizerPhase in HighTier
             */
            iter = ret.getHighTier().findPhase(CanonicalizerPhase.class);
        }
        iter.add(new Phase() {

            @Override
            protected void run(StructuredGraph graph) {
                ComputeLoopFrequenciesClosure.compute(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            protected CharSequence getName() {
                return "ComputeLoopFrequenciesPhase";
            }
        });
        ret.getHighTier().appendPhase(new Phase() {

            @Override
            protected void run(StructuredGraph graph) {
                assert checkHighTierGraph(graph) : "failed HighTier graph check";
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            protected CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        ret.getMidTier().appendPhase(new Phase() {

            @Override
            protected void run(StructuredGraph graph) {
                assert checkMidTierGraph(graph) : "failed MidTier graph check";
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            protected CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        ret.getLowTier().appendPhase(new Phase() {

            @Override
            protected void run(StructuredGraph graph) {
                assert checkLowTierGraph(graph) : "failed LowTier graph check";
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            protected CharSequence getName() {
                return "CheckGraphPhase";
            }
        });
        return ret;
    }

    protected LIRSuites createLIRSuites() {
        LIRSuites ret = backend.getSuites().getDefaultLIRSuites().copy();
        return ret;
    }

    public GraalCompilerTest() {
        this.backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        this.providers = getBackend().getProviders();
        this.suites = new DerivedOptionValue<>(this::createSuites);
        this.lirSuites = new DerivedOptionValue<>(this::createLIRSuites);
    }

    /**
     * Set up a test for a non-default backend. The test should check (via {@link #getBackend()} )
     * whether the desired backend is available.
     *
     * @param arch the name of the desired backend architecture
     */
    public GraalCompilerTest(Class<? extends Architecture> arch) {
        RuntimeProvider runtime = Graal.getRequiredCapability(RuntimeProvider.class);
        Backend b = runtime.getBackend(arch);
        if (b != null) {
            this.backend = b;
        } else {
            // Fall back to the default/host backend
            this.backend = runtime.getHostBackend();
        }
        this.providers = backend.getProviders();
        this.suites = new DerivedOptionValue<>(this::createSuites);
        this.lirSuites = new DerivedOptionValue<>(this::createLIRSuites);
    }

    /**
     * Set up a test for a non-default backend.
     *
     * @param backend the desired backend
     */
    public GraalCompilerTest(Backend backend) {
        this.backend = backend;
        this.providers = backend.getProviders();
        this.suites = new DerivedOptionValue<>(this::createSuites);
        this.lirSuites = new DerivedOptionValue<>(this::createLIRSuites);
    }

    private Scope debugScope;

    @Before
    public void beforeTest() {
        assert debugScope == null;
        debugScope = Debug.scope(getClass());
    }

    @After
    public void afterTest() {
        if (debugScope != null) {
            debugScope.close();
        }
        debugScope = null;
    }

    protected void assertEquals(StructuredGraph expected, StructuredGraph graph) {
        assertEquals(expected, graph, false, true);
    }

    protected int countUnusedConstants(StructuredGraph graph) {
        int total = 0;
        for (ConstantNode node : getConstantNodes(graph)) {
            if (node.hasNoUsages()) {
                total++;
            }
        }
        return total;
    }

    protected int getNodeCountExcludingUnusedConstants(StructuredGraph graph) {
        return graph.getNodeCount() - countUnusedConstants(graph);
    }

    protected void assertEquals(StructuredGraph expected, StructuredGraph graph, boolean excludeVirtual, boolean checkConstants) {
        String expectedString = getCanonicalGraphString(expected, excludeVirtual, checkConstants);
        String actualString = getCanonicalGraphString(graph, excludeVirtual, checkConstants);
        String mismatchString = compareGraphStrings(expected, expectedString, graph, actualString);

        if (!excludeVirtual && getNodeCountExcludingUnusedConstants(expected) != getNodeCountExcludingUnusedConstants(graph)) {
            Debug.dump(Debug.BASIC_LOG_LEVEL, expected, "Node count not matching - expected");
            Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "Node count not matching - actual");
            Assert.fail("Graphs do not have the same number of nodes: " + expected.getNodeCount() + " vs. " + graph.getNodeCount() + "\n" + mismatchString);
        }
        if (!expectedString.equals(actualString)) {
            Debug.dump(Debug.BASIC_LOG_LEVEL, expected, "mismatching graphs - expected");
            Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "mismatching graphs - actual");
            Assert.fail(mismatchString);
        }
    }

    private static String compareGraphStrings(StructuredGraph expectedGraph, String expectedString, StructuredGraph actualGraph, String actualString) {
        if (!expectedString.equals(actualString)) {
            String[] expectedLines = expectedString.split("\n");
            String[] actualLines = actualString.split("\n");
            int diffIndex = -1;
            int limit = Math.min(actualLines.length, expectedLines.length);
            String marker = " <<<";
            for (int i = 0; i < limit; i++) {
                if (!expectedLines[i].equals(actualLines[i])) {
                    diffIndex = i;
                    break;
                }
            }
            if (diffIndex == -1) {
                // Prefix is the same so add some space after the prefix
                diffIndex = limit;
                if (actualLines.length == limit) {
                    actualLines = Arrays.copyOf(actualLines, limit + 1);
                    actualLines[diffIndex] = "";
                } else {
                    assert expectedLines.length == limit;
                    expectedLines = Arrays.copyOf(expectedLines, limit + 1);
                    expectedLines[diffIndex] = "";
                }
            }
            // Place a marker next to the first line that differs
            expectedLines[diffIndex] = expectedLines[diffIndex] + marker;
            actualLines[diffIndex] = actualLines[diffIndex] + marker;
            String ediff = String.join("\n", expectedLines);
            String adiff = String.join("\n", actualLines);
            return "mismatch in graphs:\n========= expected (" + expectedGraph + ") =========\n" + ediff + "\n\n========= actual (" + actualGraph + ") =========\n" + adiff;
        } else {
            return "mismatch in graphs";
        }
    }

    protected void assertOptimizedAway(StructuredGraph g) {
        Assert.assertEquals(0, g.getNodes().filter(NotOptimizedNode.class).count());
    }

    protected void assertConstantReturn(StructuredGraph graph, int value) {
        String graphString = getCanonicalGraphString(graph, false, true);
        Assert.assertEquals("unexpected number of ReturnNodes: " + graphString, graph.getNodes(ReturnNode.TYPE).count(), 1);
        ValueNode result = graph.getNodes(ReturnNode.TYPE).first().result();
        Assert.assertTrue("unexpected ReturnNode result node: " + graphString, result.isConstant());
        Assert.assertEquals("unexpected ReturnNode result kind: " + graphString, result.asJavaConstant().getJavaKind(), JavaKind.Int);
        Assert.assertEquals("unexpected ReturnNode result: " + graphString, result.asJavaConstant().asInt(), value);
    }

    protected static String getCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual, boolean checkConstants) {
        SchedulePhase schedule = new SchedulePhase(SchedulingStrategy.EARLIEST);
        schedule.apply(graph);
        ScheduleResult scheduleResult = graph.getLastSchedule();

        NodeMap<Integer> canonicalId = graph.createNodeMap();
        int nextId = 0;

        List<String> constantsLines = new ArrayList<>();

        StringBuilder result = new StringBuilder();
        for (Block block : scheduleResult.getCFG().getBlocks()) {
            result.append("Block " + block + " ");
            if (block == scheduleResult.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (Block succ : block.getSuccessors()) {
                result.append(succ + " ");
            }
            result.append("\n");
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                if (node instanceof ValueNode && node.isAlive()) {
                    if (!excludeVirtual || !(node instanceof VirtualObjectNode || node instanceof ProxyNode || node instanceof FullInfopointNode)) {
                        if (node instanceof ConstantNode) {
                            String name = checkConstants ? node.toString(Verbosity.Name) : node.getClass().getSimpleName();
                            String str = name + (excludeVirtual ? "\n" : "    (" + filteredUsageCount(node) + ")\n");
                            constantsLines.add(str);
                        } else {
                            int id;
                            if (canonicalId.get(node) != null) {
                                id = canonicalId.get(node);
                            } else {
                                id = nextId++;
                                canonicalId.set(node, id);
                            }
                            String name = node.getClass().getSimpleName();
                            String str = "  " + id + "|" + name + (excludeVirtual ? "\n" : "    (" + filteredUsageCount(node) + ")\n");
                            result.append(str);
                        }
                    }
                }
            }
        }

        StringBuilder constantsLinesResult = new StringBuilder();
        constantsLinesResult.append(constantsLines.size() + " constants:\n");
        Collections.sort(constantsLines);
        for (String s : constantsLines) {
            constantsLinesResult.append(s);
            constantsLinesResult.append("\n");
        }

        return constantsLines.toString() + result.toString();
    }

    /**
     * @return usage count excluding {@link FrameState} usages
     */
    private static int filteredUsageCount(Node node) {
        return node.usages().filter(n -> !(n instanceof FrameState)).count();
    }

    /**
     * @param graph
     * @return a scheduled textual dump of {@code graph} .
     */
    protected static String getScheduledGraphString(StructuredGraph graph) {
        SchedulePhase schedule = new SchedulePhase(SchedulingStrategy.EARLIEST);
        schedule.apply(graph);
        ScheduleResult scheduleResult = graph.getLastSchedule();

        StringBuilder result = new StringBuilder();
        Block[] blocks = scheduleResult.getCFG().getBlocks();
        for (Block block : blocks) {
            result.append("Block " + block + " ");
            if (block == scheduleResult.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (Block succ : block.getSuccessors()) {
                result.append(succ + " ");
            }
            result.append("\n");
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                result.append(String.format("%1S\n", node));
            }
        }
        return result.toString();
    }

    protected Backend getBackend() {
        return backend;
    }

    protected Suites getSuites() {
        return suites.getValue();
    }

    protected LIRSuites getLIRSuites() {
        return lirSuites.getValue();
    }

    protected final Providers getProviders() {
        return providers;
    }

    protected HighTierContext getDefaultHighTierContext() {
        return new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
    }

    protected SnippetReflectionProvider getSnippetReflection() {
        return Graal.getRequiredCapability(SnippetReflectionProvider.class);
    }

    protected TargetDescription getTarget() {
        return getTargetProvider().getTarget();
    }

    protected TargetProvider getTargetProvider() {
        return getBackend();
    }

    protected CodeCacheProvider getCodeCache() {
        return getProviders().getCodeCache();
    }

    protected ConstantReflectionProvider getConstantReflection() {
        return getProviders().getConstantReflection();
    }

    protected MetaAccessProvider getMetaAccess() {
        return getProviders().getMetaAccess();
    }

    protected LoweringProvider getLowerer() {
        return getProviders().getLowerer();
    }

    protected CompilationIdentifier getCompilationId(ResolvedJavaMethod method) {
        return getBackend().getCompilationIdentifier(method);
    }

    protected CompilationIdentifier getOrCreateCompilationId(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph) {
        if (graph != null) {
            return graph.compilationId();
        }
        return getCompilationId(installedCodeOwner);
    }

    protected void testN(int n, final String name, final Object... args) {
        final List<Throwable> errors = new ArrayList<>(n);
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(i + ":" + name) {

                @Override
                public void run() {
                    try {
                        test(name, args);
                    } catch (Throwable e) {
                        errors.add(e);
                    }
                }
            };
            threads[i] = t;
            t.start();
        }
        for (int i = 0; i < n; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                errors.add(e);
            }
        }
        if (!errors.isEmpty()) {
            throw new MultiCauseAssertionError(errors.size() + " failures", errors.toArray(new Throwable[errors.size()]));
        }
    }

    protected Object referenceInvoke(ResolvedJavaMethod method, Object receiver, Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return invoke(method, receiver, args);
    }

    protected static class Result {

        public final Object returnValue;
        public final Throwable exception;

        public Result(Object returnValue, Throwable exception) {
            this.returnValue = returnValue;
            this.exception = exception;
        }

        @Override
        public String toString() {
            return exception == null ? returnValue == null ? "null" : returnValue.toString() : "!" + exception;
        }
    }

    /**
     * Called before a test is executed.
     */
    protected void before(@SuppressWarnings("unused") ResolvedJavaMethod method) {
    }

    /**
     * Called after a test is executed.
     */
    protected void after() {
    }

    protected Result executeExpected(ResolvedJavaMethod method, Object receiver, Object... args) {
        before(method);
        try {
            // This gives us both the expected return value as well as ensuring that the method to
            // be compiled is fully resolved
            return new Result(referenceInvoke(method, receiver, args), null);
        } catch (InvocationTargetException e) {
            return new Result(null, e.getTargetException());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            after();
        }
    }

    protected Result executeActual(ResolvedJavaMethod method, Object receiver, Object... args) {
        before(method);
        Object[] executeArgs = argsWithReceiver(receiver, args);

        checkArgs(method, executeArgs);

        InstalledCode compiledMethod = getCode(method);
        try {
            return new Result(compiledMethod.executeVarargs(executeArgs), null);
        } catch (Throwable e) {
            return new Result(null, e);
        } finally {
            after();
        }
    }

    protected void checkArgs(ResolvedJavaMethod method, Object[] args) {
        JavaType[] sig = method.toParameterTypes();
        Assert.assertEquals(sig.length, args.length);
        for (int i = 0; i < args.length; i++) {
            JavaType javaType = sig[i];
            JavaKind kind = javaType.getJavaKind();
            Object arg = args[i];
            if (kind == JavaKind.Object) {
                if (arg != null && javaType instanceof ResolvedJavaType) {
                    ResolvedJavaType resolvedJavaType = (ResolvedJavaType) javaType;
                    Assert.assertTrue(resolvedJavaType + " from " + getMetaAccess().lookupJavaType(arg.getClass()), resolvedJavaType.isAssignableFrom(getMetaAccess().lookupJavaType(arg.getClass())));
                }
            } else {
                Assert.assertNotNull(arg);
                Assert.assertEquals(kind.toBoxedJavaClass(), arg.getClass());
            }
        }
    }

    /**
     * Prepends a non-null receiver argument to a given list or args.
     *
     * @param receiver the receiver argument to prepend if it is non-null
     */
    protected Object[] argsWithReceiver(Object receiver, Object... args) {
        Object[] executeArgs;
        if (receiver == null) {
            executeArgs = args;
        } else {
            executeArgs = new Object[args.length + 1];
            executeArgs[0] = receiver;
            for (int i = 0; i < args.length; i++) {
                executeArgs[i + 1] = args[i];
            }
        }
        return applyArgSuppliers(executeArgs);
    }

    protected void test(String name, Object... args) {
        try {
            ResolvedJavaMethod method = getResolvedJavaMethod(name);
            Object receiver = method.isStatic() ? null : this;
            test(method, receiver, args);
        } catch (AssumptionViolatedException e) {
            // Suppress so that subsequent calls to this method within the
            // same Junit @Test annotated method can proceed.
        }
    }

    /**
     * Type denoting a lambda that supplies a fresh value each time it is called. This is useful
     * when supplying an argument to {@link GraalCompilerTest#test(String, Object...)} where the
     * test modifies the state of the argument (e.g., updates a field).
     */
    @FunctionalInterface
    public interface ArgSupplier extends Supplier<Object> {
    }

    /**
     * Convenience method for using an {@link ArgSupplier} lambda in a varargs list.
     */
    public static Object supply(ArgSupplier supplier) {
        return supplier;
    }

    protected void test(ResolvedJavaMethod method, Object receiver, Object... args) {
        Result expect = executeExpected(method, receiver, args);
        if (getCodeCache() == null) {
            return;
        }
        testAgainstExpected(method, expect, receiver, args);
    }

    /**
     * Process a given set of arguments, converting any {@link ArgSupplier} argument to the argument
     * it supplies.
     */
    protected Object[] applyArgSuppliers(Object... args) {
        Object[] res = args;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof ArgSupplier) {
                if (res == args) {
                    res = args.clone();
                }
                res[i] = ((ArgSupplier) args[i]).get();
            }
        }
        return res;
    }

    protected void testAgainstExpected(ResolvedJavaMethod method, Result expect, Object receiver, Object... args) {
        testAgainstExpected(method, expect, Collections.<DeoptimizationReason> emptySet(), receiver, args);
    }

    protected Result executeActualCheckDeopt(ResolvedJavaMethod method, Set<DeoptimizationReason> shouldNotDeopt, Object receiver, Object... args) {
        Map<DeoptimizationReason, Integer> deoptCounts = new EnumMap<>(DeoptimizationReason.class);
        ProfilingInfo profile = method.getProfilingInfo();
        for (DeoptimizationReason reason : shouldNotDeopt) {
            deoptCounts.put(reason, profile.getDeoptimizationCount(reason));
        }
        Result actual = executeActual(method, receiver, args);
        profile = method.getProfilingInfo(); // profile can change after execution
        for (DeoptimizationReason reason : shouldNotDeopt) {
            Assert.assertEquals((int) deoptCounts.get(reason), profile.getDeoptimizationCount(reason));
        }
        return actual;
    }

    protected void assertEquals(Result expect, Result actual) {
        if (expect.exception != null) {
            Assert.assertTrue("expected " + expect.exception, actual.exception != null);
            Assert.assertEquals("Exception class", expect.exception.getClass(), actual.exception.getClass());
            Assert.assertEquals("Exception message", expect.exception.getMessage(), actual.exception.getMessage());
        } else {
            if (actual.exception != null) {
                throw new AssertionError("expected " + expect.returnValue + " but got an exception", actual.exception);
            }
            assertDeepEquals(expect.returnValue, actual.returnValue);
        }
    }

    protected void testAgainstExpected(ResolvedJavaMethod method, Result expect, Set<DeoptimizationReason> shouldNotDeopt, Object receiver, Object... args) {
        Result actual = executeActualCheckDeopt(method, shouldNotDeopt, receiver, args);
        assertEquals(expect, actual);
    }

    private Map<ResolvedJavaMethod, InstalledCode> cache = new HashMap<>();

    /**
     * Gets installed code for a given method, compiling it first if necessary. The graph is parsed
     * {@link #parseEager(ResolvedJavaMethod, AllowAssumptions) eagerly}.
     */
    protected InstalledCode getCode(ResolvedJavaMethod method) {
        return getCode(method, null);
    }

    /**
     * Gets installed code for a given method, compiling it first if necessary.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled. If null, a graph will be obtained from
     *            {@code installedCodeOwner} via {@link #parseForCompile(ResolvedJavaMethod)}.
     */
    protected InstalledCode getCode(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph) {
        return getCode(installedCodeOwner, graph, false);
    }

    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph0, boolean forceCompile) {
        return getCode(installedCodeOwner, graph0, forceCompile, false);
    }

    /**
     * Gets installed code for a given method and graph, compiling it first if necessary.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled. If null, a graph will be obtained from
     *            {@code installedCodeOwner} via {@link #parseForCompile(ResolvedJavaMethod)}.
     * @param forceCompile specifies whether to ignore any previous code cached for the (method,
     *            key) pair
     * @param installDefault specifies whether to install as the default implementation
     */
    @SuppressWarnings("try")
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installDefault) {
        if (!forceCompile) {
            InstalledCode cached = cache.get(installedCodeOwner);
            if (cached != null) {
                if (cached.isValid()) {
                    return cached;
                }
            }
        }

        final CompilationIdentifier id = getOrCreateCompilationId(installedCodeOwner, graph);

        InstalledCode installedCode = null;
        try (AllocSpy spy = AllocSpy.open(installedCodeOwner); Scope ds = Debug.scope("Compiling", new DebugDumpScope(id.toString(CompilationIdentifier.Verbosity.ID), true))) {
            final boolean printCompilation = PrintCompilation.getValue() && !TTY.isSuppressed();
            if (printCompilation) {
                TTY.println(String.format("@%-6s Graal %-70s %-45s %-50s ...", id, installedCodeOwner.getDeclaringClass().getName(), installedCodeOwner.getName(), installedCodeOwner.getSignature()));
            }
            long start = System.currentTimeMillis();
            CompilationResult compResult = compile(installedCodeOwner, graph, id);
            if (printCompilation) {
                TTY.println(String.format("@%-6s Graal %-70s %-45s %-50s | %4dms %5dB", id, "", "", "", System.currentTimeMillis() - start, compResult.getTargetCodeSize()));
            }

            try (Scope s = Debug.scope("CodeInstall", getCodeCache(), installedCodeOwner, compResult)) {
                if (installDefault) {
                    installedCode = addDefaultMethod(installedCodeOwner, compResult);
                } else {
                    installedCode = addMethod(installedCodeOwner, compResult);
                }
                if (installedCode == null) {
                    throw new GraalError("Could not install code for " + installedCodeOwner.format("%H.%n(%p)"));
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        if (!forceCompile) {
            cache.put(installedCodeOwner, installedCode);
        }
        return installedCode;
    }

    /**
     * Used to produce a graph for a method about to be compiled by
     * {@link #compile(ResolvedJavaMethod, StructuredGraph)} if the second parameter to that method
     * is null.
     *
     * The default implementation in {@link GraalCompilerTest} is to call
     * {@link #parseEager(ResolvedJavaMethod, AllowAssumptions)}.
     */
    protected final StructuredGraph parseForCompile(ResolvedJavaMethod method) {
        return parseEager(method, AllowAssumptions.YES);
    }

    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId) {
        return parseEager(method, AllowAssumptions.YES, compilationId);
    }

    /**
     * Compiles a given method.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled for {@code installedCodeOwner}. If null, a graph will
     *            be obtained from {@code installedCodeOwner} via
     *            {@link #parseForCompile(ResolvedJavaMethod)}.
     */
    protected final CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph) {
        return compile(installedCodeOwner, graph, getOrCreateCompilationId(installedCodeOwner, graph));
    }

    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationIdentifier compilationId) {
        return compile(installedCodeOwner, graph, new CompilationResult(), compilationId);
    }

    /**
     * Compiles a given method.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled for {@code installedCodeOwner}. If null, a graph will
     *            be obtained from {@code installedCodeOwner} via
     *            {@link #parseForCompile(ResolvedJavaMethod)}.
     * @param compilationResult
     * @param compilationId
     */
    @SuppressWarnings("try")
    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId) {
        StructuredGraph graphToCompile = graph == null ? parseForCompile(installedCodeOwner, compilationId) : graph;
        lastCompiledGraph = graphToCompile;
        try (Scope s = Debug.scope("Compile", graphToCompile)) {
            Request<CompilationResult> request = new Request<>(graphToCompile, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL,
                            graphToCompile.getProfilingInfo(), getSuites(), getLIRSuites(), compilationResult, CompilationResultBuilderFactory.Default);
            return GraalCompiler.compile(request);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    protected StructuredGraph lastCompiledGraph;

    protected SpeculationLog getSpeculationLog() {
        return null;
    }

    protected InstalledCode addMethod(final ResolvedJavaMethod method, final CompilationResult compilationResult) {
        return backend.addInstalledCode(method, null, compilationResult);
    }

    protected InstalledCode addDefaultMethod(final ResolvedJavaMethod method, final CompilationResult compilationResult) {
        return backend.createDefaultInstalledCode(method, compilationResult);
    }

    private final Map<ResolvedJavaMethod, Method> methodMap = new HashMap<>();

    /**
     * Converts a reflection {@link Method} to a {@link ResolvedJavaMethod}.
     */
    protected ResolvedJavaMethod asResolvedJavaMethod(Method method) {
        ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(method);
        methodMap.put(javaMethod, method);
        return javaMethod;
    }

    protected ResolvedJavaMethod getResolvedJavaMethod(String methodName) {
        return asResolvedJavaMethod(getMethod(methodName));
    }

    protected ResolvedJavaMethod getResolvedJavaMethod(Class<?> clazz, String methodName) {
        return asResolvedJavaMethod(getMethod(clazz, methodName));
    }

    protected ResolvedJavaMethod getResolvedJavaMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        return asResolvedJavaMethod(getMethod(clazz, methodName, parameterTypes));
    }

    /**
     * Gets the reflection {@link Method} from which a given {@link ResolvedJavaMethod} was created
     * or null if {@code javaMethod} does not correspond to a reflection method.
     */
    protected Method lookupMethod(ResolvedJavaMethod javaMethod) {
        return methodMap.get(javaMethod);
    }

    protected Object invoke(ResolvedJavaMethod javaMethod, Object receiver, Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        Method method = lookupMethod(javaMethod);
        Assert.assertTrue(method != null);
        if (!method.isAccessible()) {
            method.setAccessible(true);
        }
        return method.invoke(receiver, applyArgSuppliers(args));
    }

    /**
     * Parses a Java method in {@linkplain GraphBuilderConfiguration#getDefault default} mode to
     * produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     */
    protected StructuredGraph parseProfiled(String methodName, AllowAssumptions allowAssumptions) {
        return parseProfiled(getResolvedJavaMethod(methodName), allowAssumptions);
    }

    /**
     * Parses a Java method in {@linkplain GraphBuilderConfiguration#getDefault default} mode to
     * produce a graph.
     */
    protected final StructuredGraph parseProfiled(ResolvedJavaMethod m, AllowAssumptions allowAssumptions) {
        return parseProfiled(m, allowAssumptions, getCompilationId(m));
    }

    protected StructuredGraph parseProfiled(ResolvedJavaMethod m, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId) {
        return parse1(m, getDefaultGraphBuilderSuite(), allowAssumptions, compilationId);
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     */
    protected final StructuredGraph parseEager(String methodName, AllowAssumptions allowAssumptions) {
        return parseEager(getResolvedJavaMethod(methodName), allowAssumptions);
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     */
    protected final StructuredGraph parseEager(ResolvedJavaMethod m, AllowAssumptions allowAssumptions) {
        return parseEager(m, allowAssumptions, getCompilationId(m));
    }

    protected StructuredGraph parseEager(ResolvedJavaMethod m, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId) {
        return parse1(m, getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withEagerResolving(true)), allowAssumptions, compilationId);
    }

    /**
     * Parses a Java method using {@linkplain GraphBuilderConfiguration#withFullInfopoints(boolean)
     * full debug} set to true to produce a graph.
     */
    protected final StructuredGraph parseDebug(ResolvedJavaMethod m, AllowAssumptions allowAssumptions) {
        return parseDebug(m, allowAssumptions, getCompilationId(m));
    }

    protected StructuredGraph parseDebug(ResolvedJavaMethod m, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId) {
        return parse1(m, getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withFullInfopoints(true)), allowAssumptions, compilationId);
    }

    @SuppressWarnings("try")
    private StructuredGraph parse1(ResolvedJavaMethod javaMethod, PhaseSuite<HighTierContext> graphBuilderSuite, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId) {
        assert javaMethod.getAnnotation(Test.class) == null : "shouldn't parse method with @Test annotation: " + javaMethod;
        StructuredGraph graph = new StructuredGraph(javaMethod, allowAssumptions, getSpeculationLog(), compilationId);
        try (Scope ds = Debug.scope("Parsing", javaMethod, graph)) {
            graphBuilderSuite.apply(graph, getDefaultHighTierContext());
            return graph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    protected Plugins getDefaultGraphBuilderPlugins() {
        PhaseSuite<HighTierContext> suite = backend.getSuites().getDefaultGraphBuilderSuite();
        Plugins defaultPlugins = ((GraphBuilderPhase) suite.findPhase(GraphBuilderPhase.class).previous()).getGraphBuilderConfig().getPlugins();
        // defensive copying
        return new Plugins(defaultPlugins);
    }

    protected PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        // defensive copying
        return backend.getSuites().getDefaultGraphBuilderSuite().copy();
    }

    protected PhaseSuite<HighTierContext> getCustomGraphBuilderSuite(GraphBuilderConfiguration gbConf) {
        PhaseSuite<HighTierContext> suite = getDefaultGraphBuilderSuite();
        ListIterator<BasePhase<? super HighTierContext>> iterator = suite.findPhase(GraphBuilderPhase.class);
        GraphBuilderConfiguration gbConfCopy = editGraphBuilderConfiguration(gbConf.copy());
        iterator.remove();
        iterator.add(new GraphBuilderPhase(gbConfCopy));
        return suite;
    }

    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugins invocationPlugins = conf.getPlugins().getInvocationPlugins();
        invocationPlugins.register(new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new BreakpointNode());
                return true;
            }
        }, GraalCompilerTest.class, "breakpoint");
        invocationPlugins.register(new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg0) {
                b.add(new BreakpointNode(arg0));
                return true;
            }
        }, GraalCompilerTest.class, "breakpoint", int.class);
        invocationPlugins.register(new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new NotOptimizedNode());
                return true;
            }
        }, GraalCompilerTest.class, "shouldBeOptimizedAway");

        conf.getPlugins().prependInlineInvokePlugin(new InlineInvokePlugin() {

            @Override
            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                BytecodeParserNeverInline neverInline = method.getAnnotation(BytecodeParserNeverInline.class);
                if (neverInline != null) {
                    return neverInline.invokeWithException() ? DO_NOT_INLINE_WITH_EXCEPTION : DO_NOT_INLINE_NO_EXCEPTION;
                }
                if (method.getAnnotation(BytecodeParserForceInline.class) != null) {
                    return InlineInfo.createStandardInlineInfo(method);
                }
                return bytecodeParserShouldInlineInvoke(b, method, args);
            }
        });
        return conf;
    }

    /**
     * Supplements {@link BytecodeParserForceInline} and {@link BytecodeParserNeverInline} in terms
     * of allowing a test to influence the inlining decision made during bytecode parsing.
     *
     * @see InlineInvokePlugin#shouldInlineInvoke(GraphBuilderContext, ResolvedJavaMethod,
     *      ValueNode[])
     */
    @SuppressWarnings("unused")
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return null;
    }

    @NodeInfo
    public static class NotOptimizedNode extends FixedWithNextNode {
        private static final NodeClass<NotOptimizedNode> TYPE = NodeClass.create(NotOptimizedNode.class);

        protected NotOptimizedNode() {
            super(TYPE, StampFactory.forVoid());
        }

    }

    protected Replacements getReplacements() {
        return getProviders().getReplacements();
    }

    /**
     * Inject a probability for a branch condition into the profiling information of this test case.
     *
     * @param p the probability that cond is true
     * @param cond the condition of the branch
     * @return cond
     */
    protected static boolean branchProbability(double p, boolean cond) {
        return GraalDirectives.injectBranchProbability(p, cond);
    }

    /**
     * Inject an iteration count for a loop condition into the profiling information of this test
     * case.
     *
     * @param i the iteration count of the loop
     * @param cond the condition of the loop
     * @return cond
     */
    protected static boolean iterationCount(double i, boolean cond) {
        return GraalDirectives.injectIterationCount(i, cond);
    }

    /**
     * Test if the current test runs on the given platform. The name must match the name given in
     * the {@link Architecture#getName()}.
     *
     * @param name The name to test
     * @return true if we run on the architecture given by name
     */
    protected boolean isArchitecture(String name) {
        return name.equals(backend.getTarget().arch.getName());
    }
}
