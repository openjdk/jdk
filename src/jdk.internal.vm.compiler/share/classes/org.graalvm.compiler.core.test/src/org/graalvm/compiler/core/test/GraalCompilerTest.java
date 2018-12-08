/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.reflect.Modifier.isStatic;
import static jdk.vm.ci.runtime.JVMCICompiler.INVOCATION_ENTRY_BCI;
import static org.graalvm.compiler.nodes.ConstantNode.getConstantNodes;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationPrinter;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.GraalCompiler.Request;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.DebugHandlersFactory;
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
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;
import org.graalvm.compiler.phases.common.inlining.policy.GreedyInliningPolicy;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.TargetProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.test.AddExports;
import org.graalvm.compiler.test.GraalTest;
import org.graalvm.compiler.test.JLModule;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
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
 * <li>Create a graph by {@linkplain #parseEager parsing} a method.</li>
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
@AddExports({"java.base/jdk.internal.org.objectweb.asm", "java.base/jdk.internal.org.objectweb.asm.tree"})
public abstract class GraalCompilerTest extends GraalTest {

    /**
     * Gets the initial option values provided by the Graal runtime. These are option values
     * typically parsed from the command line.
     */
    public static OptionValues getInitialOptions() {
        return Graal.getRequiredCapability(OptionValues.class);
    }

    private static final int BAILOUT_RETRY_LIMIT = 1;
    private final Providers providers;
    private final Backend backend;

    /**
     * Representative class for the {@code java.base} module.
     */
    public static final Class<?> JAVA_BASE = Class.class;

    /**
     * Exports the package named {@code packageName} declared in {@code moduleMember}'s module to
     * this object's module. This must be called before accessing packages that are no longer public
     * as of JDK 9.
     */
    protected final void exportPackage(Class<?> moduleMember, String packageName) {
        if (!Java8OrEarlier) {
            JLModule.exportPackageTo(moduleMember, packageName, getClass());
        }
    }

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

    protected Suites createSuites(OptionValues opts) {
        Suites ret = backend.getSuites().getDefaultSuites(opts).copy();
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

    protected LIRSuites createLIRSuites(OptionValues opts) {
        LIRSuites ret = backend.getSuites().getDefaultLIRSuites(opts).copy();
        return ret;
    }

    public GraalCompilerTest() {
        this.backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        this.providers = getBackend().getProviders();
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
    }

    /**
     * Set up a test for a non-default backend.
     *
     * @param backend the desired backend
     */
    public GraalCompilerTest(Backend backend) {
        this.backend = backend;
        this.providers = backend.getProviders();
    }

    @Override
    @After
    public void afterTest() {
        if (invocationPluginExtensions != null) {
            synchronized (this) {
                if (invocationPluginExtensions != null) {
                    extendedInvocationPlugins.removeTestPlugins(invocationPluginExtensions);
                    extendedInvocationPlugins = null;
                    invocationPluginExtensions = null;
                }
            }
        }
        super.afterTest();
    }

    /**
     * Gets a {@link DebugContext} object corresponding to {@code options}, creating a new one if
     * none currently exists. Debug contexts created by this method will have their
     * {@link DebugDumpHandler}s closed in {@link #afterTest()}.
     */
    protected DebugContext getDebugContext() {
        return getDebugContext(getInitialOptions(), null, null);
    }

    @Override
    protected Collection<DebugHandlersFactory> getDebugHandlersFactories() {
        return Collections.singletonList(new GraalDebugHandlersFactory(getSnippetReflection()));
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
            expected.getDebug().dump(DebugContext.BASIC_LEVEL, expected, "Node count not matching - expected");
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "Node count not matching - actual");
            Assert.fail("Graphs do not have the same number of nodes: " + expected.getNodeCount() + " vs. " + graph.getNodeCount() + "\n" + mismatchString);
        }
        if (!expectedString.equals(actualString)) {
            expected.getDebug().dump(DebugContext.BASIC_LEVEL, expected, "mismatching graphs - expected");
            graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "mismatching graphs - actual");
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
            result.append("Block ").append(block).append(' ');
            if (block == scheduleResult.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (Block succ : block.getSuccessors()) {
                result.append(succ).append(' ');
            }
            result.append('\n');
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                if (node instanceof ValueNode && node.isAlive()) {
                    if (!excludeVirtual || !(node instanceof VirtualObjectNode || node instanceof ProxyNode || node instanceof FullInfopointNode || node instanceof ParameterNode)) {
                        if (node instanceof ConstantNode) {
                            String name = checkConstants ? node.toString(Verbosity.Name) : node.getClass().getSimpleName();
                            if (excludeVirtual) {
                                constantsLines.add(name);
                            } else {
                                constantsLines.add(name + "    (" + filteredUsageCount(node) + ")");
                            }
                        } else {
                            int id;
                            if (canonicalId.get(node) != null) {
                                id = canonicalId.get(node);
                            } else {
                                id = nextId++;
                                canonicalId.set(node, id);
                            }
                            String name = node.getClass().getSimpleName();
                            result.append("  ").append(id).append('|').append(name);
                            if (node instanceof AccessFieldNode) {
                                result.append('#');
                                result.append(((AccessFieldNode) node).field());
                            }
                            if (!excludeVirtual) {
                                result.append("    (");
                                result.append(filteredUsageCount(node));
                                result.append(')');
                            }
                            result.append('\n');
                        }
                    }
                }
            }
        }

        StringBuilder constantsLinesResult = new StringBuilder();
        constantsLinesResult.append(constantsLines.size()).append(" constants:\n");
        Collections.sort(constantsLines);
        for (String s : constantsLines) {
            constantsLinesResult.append(s);
            constantsLinesResult.append('\n');
        }

        return constantsLinesResult.toString() + result.toString();
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
        SchedulePhase schedule = new SchedulePhase(SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER);
        schedule.apply(graph);
        ScheduleResult scheduleResult = graph.getLastSchedule();

        StringBuilder result = new StringBuilder();
        Block[] blocks = scheduleResult.getCFG().getBlocks();
        for (Block block : blocks) {
            result.append("Block ").append(block).append(' ');
            if (block == scheduleResult.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (Block succ : block.getSuccessors()) {
                result.append(succ).append(' ');
            }
            result.append('\n');
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                result.append(String.format("%1S\n", node));
            }
        }
        return result.toString();
    }

    protected Backend getBackend() {
        return backend;
    }

    protected final Providers getProviders() {
        return providers;
    }

    protected HighTierContext getDefaultHighTierContext() {
        return new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), getOptimisticOptimizations());
    }

    protected MidTierContext getDefaultMidTierContext() {
        return new MidTierContext(getProviders(), getTargetProvider(), getOptimisticOptimizations(), null);
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

    protected final BasePhase<HighTierContext> createInliningPhase() {
        return createInliningPhase(new CanonicalizerPhase());
    }

    protected BasePhase<HighTierContext> createInliningPhase(CanonicalizerPhase canonicalizer) {
        return createInliningPhase(null, canonicalizer);
    }

    static class GreedyTestInliningPolicy extends GreedyInliningPolicy {
        GreedyTestInliningPolicy(Map<Invoke, Double> hints) {
            super(hints);
        }

        @Override
        protected int previousLowLevelGraphSize(InlineInfo info) {
            // Ignore previous compiles for tests
            return 0;
        }
    }

    protected BasePhase<HighTierContext> createInliningPhase(Map<Invoke, Double> hints, CanonicalizerPhase canonicalizer) {
        return new InliningPhase(new GreedyTestInliningPolicy(hints), canonicalizer);
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

    protected Object referenceInvoke(ResolvedJavaMethod method, Object receiver, Object... args)
                    throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        return invoke(method, receiver, args);
    }

    public static class Result {

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
        return executeActual(getInitialOptions(), method, receiver, args);
    }

    protected Result executeActual(OptionValues options, ResolvedJavaMethod method, Object receiver, Object... args) {
        before(method);
        Object[] executeArgs = argsWithReceiver(receiver, args);

        checkArgs(method, executeArgs);

        InstalledCode compiledMethod = getCode(method, options);
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

    protected final Result test(String name, Object... args) {
        return test(getInitialOptions(), name, args);
    }

    protected final Result test(OptionValues options, String name, Object... args) {
        try {
            ResolvedJavaMethod method = getResolvedJavaMethod(name);
            Object receiver = method.isStatic() ? null : this;
            return test(options, method, receiver, args);
        } catch (AssumptionViolatedException e) {
            // Suppress so that subsequent calls to this method within the
            // same Junit @Test annotated method can proceed.
            return null;
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

    protected Result test(ResolvedJavaMethod method, Object receiver, Object... args) {
        return test(getInitialOptions(), method, receiver, args);
    }

    protected Result test(OptionValues options, ResolvedJavaMethod method, Object receiver, Object... args) {
        Result expect = executeExpected(method, receiver, args);
        if (getCodeCache() != null) {
            testAgainstExpected(options, method, expect, receiver, args);
        }
        return expect;
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

    protected final void testAgainstExpected(ResolvedJavaMethod method, Result expect, Object receiver, Object... args) {
        testAgainstExpected(getInitialOptions(), method, expect, Collections.<DeoptimizationReason> emptySet(), receiver, args);
    }

    protected void testAgainstExpected(ResolvedJavaMethod method, Result expect, Set<DeoptimizationReason> shouldNotDeopt, Object receiver, Object... args) {
        testAgainstExpected(getInitialOptions(), method, expect, shouldNotDeopt, receiver, args);
    }

    protected final void testAgainstExpected(OptionValues options, ResolvedJavaMethod method, Result expect, Object receiver, Object... args) {
        testAgainstExpected(options, method, expect, Collections.<DeoptimizationReason> emptySet(), receiver, args);
    }

    protected void testAgainstExpected(OptionValues options, ResolvedJavaMethod method, Result expect, Set<DeoptimizationReason> shouldNotDeopt, Object receiver, Object... args) {
        Result actual = executeActualCheckDeopt(options, method, shouldNotDeopt, receiver, args);
        assertEquals(expect, actual);
    }

    protected Result executeActualCheckDeopt(OptionValues options, ResolvedJavaMethod method, Set<DeoptimizationReason> shouldNotDeopt, Object receiver, Object... args) {
        Map<DeoptimizationReason, Integer> deoptCounts = new EnumMap<>(DeoptimizationReason.class);
        ProfilingInfo profile = method.getProfilingInfo();
        for (DeoptimizationReason reason : shouldNotDeopt) {
            deoptCounts.put(reason, profile.getDeoptimizationCount(reason));
        }
        Result actual = executeActual(options, method, receiver, args);
        profile = method.getProfilingInfo(); // profile can change after execution
        for (DeoptimizationReason reason : shouldNotDeopt) {
            Assert.assertEquals("wrong number of deopt counts for " + reason, (int) deoptCounts.get(reason), profile.getDeoptimizationCount(reason));
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

    private Map<ResolvedJavaMethod, InstalledCode> cache = new ConcurrentHashMap<>();

    /**
     * Gets installed code for a given method, compiling it first if necessary. The graph is parsed
     * {@link #parseEager eagerly}.
     */
    protected final InstalledCode getCode(ResolvedJavaMethod method) {
        return getCode(method, null, false, false, getInitialOptions());
    }

    protected final InstalledCode getCode(ResolvedJavaMethod method, OptionValues options) {
        return getCode(method, null, false, false, options);
    }

    /**
     * Gets installed code for a given method, compiling it first if necessary.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled. If null, a graph will be obtained from
     *            {@code installedCodeOwner} via {@link #parseForCompile(ResolvedJavaMethod)}.
     */
    protected final InstalledCode getCode(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph) {
        return getCode(installedCodeOwner, graph, false, false, graph == null ? getInitialOptions() : graph.getOptions());
    }

    /**
     * Gets installed code for a given method and graph, compiling it first if necessary.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled. If null, a graph will be obtained from
     *            {@code installedCodeOwner} via {@link #parseForCompile(ResolvedJavaMethod)}.
     * @param forceCompile specifies whether to ignore any previous code cached for the (method,
     *            key) pair
     */
    protected final InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile) {
        return getCode(installedCodeOwner, graph, forceCompile, false, graph == null ? getInitialOptions() : graph.getOptions());
    }

    /**
     * Gets installed code for a given method and graph, compiling it first if necessary.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled. If null, a graph will be obtained from
     *            {@code installedCodeOwner} via {@link #parseForCompile(ResolvedJavaMethod)}.
     * @param forceCompile specifies whether to ignore any previous code cached for the (method,
     *            key) pair
     * @param installAsDefault specifies whether to install as the default implementation
     * @param options the options that will be used in {@link #parseForCompile(ResolvedJavaMethod)}
     */
    @SuppressWarnings("try")
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        boolean useCache = !forceCompile && getArgumentToBind() == null;
        if (useCache && graph == null) {
            InstalledCode cached = cache.get(installedCodeOwner);
            if (cached != null) {
                if (cached.isValid()) {
                    return cached;
                }
            }
        }
        // loop for retrying compilation
        for (int retry = 0; retry <= BAILOUT_RETRY_LIMIT; retry++) {
            final CompilationIdentifier id = getOrCreateCompilationId(installedCodeOwner, graph);

            InstalledCode installedCode = null;
            StructuredGraph graphToCompile = graph == null ? parseForCompile(installedCodeOwner, id, options) : graph;
            DebugContext debug = graphToCompile.getDebug();

            try (AllocSpy spy = AllocSpy.open(installedCodeOwner); DebugContext.Scope ds = debug.scope("Compiling", new DebugDumpScope(id.toString(CompilationIdentifier.Verbosity.ID), true))) {
                CompilationPrinter printer = CompilationPrinter.begin(options, id, installedCodeOwner, INVOCATION_ENTRY_BCI);
                CompilationResult compResult = compile(installedCodeOwner, graphToCompile, new CompilationResult(graphToCompile.compilationId()), id, options);
                printer.finish(compResult);

                try (DebugContext.Scope s = debug.scope("CodeInstall", getCodeCache(), installedCodeOwner, compResult);
                                DebugContext.Activation a = debug.activate()) {
                    try {
                        if (installAsDefault) {
                            installedCode = addDefaultMethod(debug, installedCodeOwner, compResult);
                        } else {
                            installedCode = addMethod(debug, installedCodeOwner, compResult);
                        }
                        if (installedCode == null) {
                            throw new GraalError("Could not install code for " + installedCodeOwner.format("%H.%n(%p)"));
                        }
                    } catch (BailoutException e) {
                        if (retry < BAILOUT_RETRY_LIMIT && graph == null && !e.isPermanent()) {
                            // retry (if there is no predefined graph)
                            TTY.println(String.format("Restart compilation %s (%s) due to a non-permanent bailout!", installedCodeOwner, id));
                            continue;
                        }
                        throw e;
                    }
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
            } catch (Throwable e) {
                throw debug.handle(e);
            }

            if (useCache) {
                cache.put(installedCodeOwner, installedCode);
            }
            return installedCode;
        }
        throw GraalError.shouldNotReachHere();
    }

    /**
     * Used to produce a graph for a method about to be compiled by
     * {@link #compile(ResolvedJavaMethod, StructuredGraph)} if the second parameter to that method
     * is null.
     *
     * The default implementation in {@link GraalCompilerTest} is to call {@link #parseEager}.
     */
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, OptionValues options) {
        return parseEager(method, AllowAssumptions.YES, getCompilationId(method), options);
    }

    protected final StructuredGraph parseForCompile(ResolvedJavaMethod method, DebugContext debug) {
        return parseEager(method, AllowAssumptions.YES, debug);
    }

    protected final StructuredGraph parseForCompile(ResolvedJavaMethod method) {
        return parseEager(method, AllowAssumptions.YES, getCompilationId(method), getInitialOptions());
    }

    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        return parseEager(method, AllowAssumptions.YES, compilationId, options);
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
        OptionValues options = graph == null ? getInitialOptions() : graph.getOptions();
        CompilationIdentifier compilationId = getOrCreateCompilationId(installedCodeOwner, graph);
        return compile(installedCodeOwner, graph, new CompilationResult(compilationId), compilationId, options);
    }

    protected final CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationIdentifier compilationId) {
        OptionValues options = graph == null ? getInitialOptions() : graph.getOptions();
        return compile(installedCodeOwner, graph, new CompilationResult(compilationId), compilationId, options);
    }

    protected final CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, OptionValues options) {
        assert graph == null || graph.getOptions() == options;
        CompilationIdentifier compilationId = getOrCreateCompilationId(installedCodeOwner, graph);
        return compile(installedCodeOwner, graph, new CompilationResult(compilationId), compilationId, options);
    }

    protected OptimisticOptimizations getOptimisticOptimizations() {
        return OptimisticOptimizations.ALL;
    }

    /**
     * Compiles a given method.
     *
     * @param installedCodeOwner the method the compiled code will be associated with when installed
     * @param graph the graph to be compiled for {@code installedCodeOwner}. If null, a graph will
     *            be obtained from {@code installedCodeOwner} via
     *            {@link #parseForCompile(ResolvedJavaMethod)}.
     * @param compilationId
     */
    @SuppressWarnings("try")
    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId, OptionValues options) {
        StructuredGraph graphToCompile = graph == null ? parseForCompile(installedCodeOwner, compilationId, options) : graph;
        lastCompiledGraph = graphToCompile;
        DebugContext debug = graphToCompile.getDebug();
        try (DebugContext.Scope s = debug.scope("Compile", graphToCompile)) {
            assert options != null;
            Request<CompilationResult> request = new Request<>(graphToCompile, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), getOptimisticOptimizations(),
                            graphToCompile.getProfilingInfo(), createSuites(options), createLIRSuites(options), compilationResult, CompilationResultBuilderFactory.Default, true);
            return GraalCompiler.compile(request);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected StructuredGraph getFinalGraph(String method) {
        return getFinalGraph(getResolvedJavaMethod(method));
    }

    protected StructuredGraph getFinalGraph(ResolvedJavaMethod method) {
        StructuredGraph graph = parseForCompile(method);
        applyFrontEnd(graph);
        return graph;
    }

    protected void applyFrontEnd(StructuredGraph graph) {
        GraalCompiler.emitFrontEnd(getProviders(), getBackend(), graph, getDefaultGraphBuilderSuite(), getOptimisticOptimizations(), graph.getProfilingInfo(), createSuites(graph.getOptions()));
    }

    protected StructuredGraph lastCompiledGraph;

    protected SpeculationLog getSpeculationLog() {
        return null;
    }

    protected InstalledCode addMethod(DebugContext debug, final ResolvedJavaMethod method, final CompilationResult compilationResult) {
        return backend.addInstalledCode(debug, method, null, compilationResult);
    }

    protected InstalledCode addDefaultMethod(DebugContext debug, final ResolvedJavaMethod method, final CompilationResult compilationResult) {
        return backend.createDefaultInstalledCode(debug, method, compilationResult);
    }

    private final Map<ResolvedJavaMethod, Executable> methodMap = new ConcurrentHashMap<>();

    /**
     * Converts a reflection {@link Method} to a {@link ResolvedJavaMethod}.
     */
    protected ResolvedJavaMethod asResolvedJavaMethod(Executable method) {
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
    protected Executable lookupMethod(ResolvedJavaMethod javaMethod) {
        return methodMap.get(javaMethod);
    }

    @SuppressWarnings("deprecation")
    protected Object invoke(ResolvedJavaMethod javaMethod, Object receiver, Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        Executable method = lookupMethod(javaMethod);
        Assert.assertTrue(method != null);
        if (!method.isAccessible()) {
            method.setAccessible(true);
        }
        if (method instanceof Method) {
            return ((Method) method).invoke(receiver, applyArgSuppliers(args));
        }
        assert receiver == null : "no receiver for constructor invokes";
        return ((Constructor<?>) method).newInstance(applyArgSuppliers(args));
    }

    /**
     * Parses a Java method in {@linkplain GraphBuilderConfiguration#getDefault default} mode to
     * produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     */
    protected final StructuredGraph parseProfiled(String methodName, AllowAssumptions allowAssumptions) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        return parse(builder(method, allowAssumptions), getDefaultGraphBuilderSuite());
    }

    /**
     * Parses a Java method in {@linkplain GraphBuilderConfiguration#getDefault default} mode to
     * produce a graph.
     *
     * @param method the method to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     */
    protected final StructuredGraph parseProfiled(ResolvedJavaMethod method, AllowAssumptions allowAssumptions) {
        return parse(builder(method, allowAssumptions), getDefaultGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     */
    protected final StructuredGraph parseEager(String methodName, AllowAssumptions allowAssumptions) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        return parse(builder(method, allowAssumptions), getEagerGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param methodName the name of the method in {@code this.getClass()} to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     * @param options the option values to be used when compiling the graph
     */
    protected final StructuredGraph parseEager(String methodName, AllowAssumptions allowAssumptions, OptionValues options) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        return parse(builder(method, allowAssumptions, options), getEagerGraphBuilderSuite());
    }

    protected final StructuredGraph parseEager(String methodName, AllowAssumptions allowAssumptions, DebugContext debug) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        return parse(builder(method, allowAssumptions, debug), getEagerGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param method the method to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     */
    protected final StructuredGraph parseEager(ResolvedJavaMethod method, AllowAssumptions allowAssumptions) {
        return parse(builder(method, allowAssumptions), getEagerGraphBuilderSuite());
    }

    protected final StructuredGraph parseEager(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, DebugContext debug) {
        return parse(builder(method, allowAssumptions, debug), getEagerGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param method the method to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     * @param options the option values to be used when compiling the graph
     */
    protected final StructuredGraph parseEager(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, OptionValues options) {
        return parse(builder(method, allowAssumptions, options), getEagerGraphBuilderSuite());
    }

    /**
     * Parses a Java method with {@linkplain GraphBuilderConfiguration#withEagerResolving(boolean)}
     * set to true to produce a graph.
     *
     * @param method the method to be parsed
     * @param allowAssumptions specifies if {@link Assumption}s can be made compiling the graph
     * @param compilationId the compilation identifier to be associated with the graph
     * @param options the option values to be used when compiling the graph
     */
    protected final StructuredGraph parseEager(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId, OptionValues options) {
        return parse(builder(method, allowAssumptions, compilationId, options), getEagerGraphBuilderSuite());
    }

    protected final Builder builder(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, DebugContext debug) {
        OptionValues options = debug.getOptions();
        return new Builder(options, debug, allowAssumptions).method(method).compilationId(getCompilationId(method));
    }

    protected final Builder builder(ResolvedJavaMethod method, AllowAssumptions allowAssumptions) {
        OptionValues options = getInitialOptions();
        return new Builder(options, getDebugContext(options, null, method), allowAssumptions).method(method).compilationId(getCompilationId(method));
    }

    protected final Builder builder(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, CompilationIdentifier compilationId, OptionValues options) {
        return new Builder(options, getDebugContext(options, compilationId.toString(CompilationIdentifier.Verbosity.ID), method), allowAssumptions).method(method).compilationId(compilationId);
    }

    protected final Builder builder(ResolvedJavaMethod method, AllowAssumptions allowAssumptions, OptionValues options) {
        return new Builder(options, getDebugContext(options, null, method), allowAssumptions).method(method).compilationId(getCompilationId(method));
    }

    protected PhaseSuite<HighTierContext> getDebugGraphBuilderSuite() {
        return getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withFullInfopoints(true));
    }

    @SuppressWarnings("try")
    protected StructuredGraph parse(StructuredGraph.Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        ResolvedJavaMethod javaMethod = builder.getMethod();
        builder.speculationLog(getSpeculationLog());
        if (builder.getCancellable() == null) {
            builder.cancellable(getCancellable(javaMethod));
        }
        assert javaMethod.getAnnotation(Test.class) == null : "shouldn't parse method with @Test annotation: " + javaMethod;
        StructuredGraph graph = builder.build();
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope ds = debug.scope("Parsing", javaMethod, graph)) {
            graphBuilderSuite.apply(graph, getDefaultHighTierContext());
            Object[] args = getArgumentToBind();
            if (args != null) {
                bindArguments(graph, args);
            }
            return graph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected void bindArguments(StructuredGraph graph, Object[] argsToBind) {
        ResolvedJavaMethod m = graph.method();
        Object receiver = isStatic(m.getModifiers()) ? null : this;
        Object[] args = argsWithReceiver(receiver, argsToBind);
        JavaType[] parameterTypes = m.toParameterTypes();
        assert parameterTypes.length == args.length;
        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            JavaConstant c = getSnippetReflection().forBoxed(parameterTypes[param.index()].getJavaKind(), args[param.index()]);
            ConstantNode replacement = ConstantNode.forConstant(c, getMetaAccess(), graph);
            param.replaceAtUsages(replacement);
        }
    }

    protected Object[] getArgumentToBind() {
        return null;
    }

    protected PhaseSuite<HighTierContext> getEagerGraphBuilderSuite() {
        return getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withEagerResolving(true).withUnresolvedIsError(true));
    }

    /**
     * Gets the cancellable that should be associated with a graph being created by any of the
     * {@code parse...()} methods.
     *
     * @param method the method being parsed into a graph
     */
    protected Cancellable getCancellable(ResolvedJavaMethod method) {
        return null;
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

    /**
     * Registers extra invocation plugins for this test. The extra plugins are removed in the
     * {@link #afterTest()} method.
     *
     * Subclasses overriding this method should always call the same method on the super class in
     * case it wants to register plugins.
     *
     * @param invocationPlugins
     */
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
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
    }

    /**
     * The {@link #testN(int, String, Object...)} method means multiple threads trying to initialize
     * this field.
     */
    private volatile InvocationPlugins invocationPluginExtensions;

    private InvocationPlugins extendedInvocationPlugins;

    protected PhaseSuite<HighTierContext> getCustomGraphBuilderSuite(GraphBuilderConfiguration gbConf) {
        PhaseSuite<HighTierContext> suite = getDefaultGraphBuilderSuite();
        ListIterator<BasePhase<? super HighTierContext>> iterator = suite.findPhase(GraphBuilderPhase.class);
        initializeInvocationPluginExtensions();
        GraphBuilderConfiguration gbConfCopy = editGraphBuilderConfiguration(gbConf.copy());
        iterator.remove();
        iterator.add(new GraphBuilderPhase(gbConfCopy));
        return suite;
    }

    private void initializeInvocationPluginExtensions() {
        if (invocationPluginExtensions == null) {
            synchronized (this) {
                if (invocationPluginExtensions == null) {
                    InvocationPlugins invocationPlugins = new InvocationPlugins();
                    registerInvocationPlugins(invocationPlugins);
                    extendedInvocationPlugins = getReplacements().getGraphBuilderPlugins().getInvocationPlugins();
                    extendedInvocationPlugins.addTestPlugins(invocationPlugins, null);
                    invocationPluginExtensions = invocationPlugins;
                }
            }
        }
    }

    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
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
