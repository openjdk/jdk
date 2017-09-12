/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.microbenchmarks.lir;

import static org.graalvm.compiler.microbenchmarks.graal.util.GraalUtil.getGraph;
import static org.graalvm.compiler.microbenchmarks.graal.util.GraalUtil.getMethodFromMethodSpec;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.GraalCompiler.Request;
import org.graalvm.compiler.core.LIRGenerationPhase;
import org.graalvm.compiler.core.LIRGenerationPhase.LIRGenerationContext;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.ComputeBlockOrder;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import org.graalvm.compiler.microbenchmarks.graal.util.GraalState;
import org.graalvm.compiler.microbenchmarks.graal.util.GraalUtil;
import org.graalvm.compiler.microbenchmarks.graal.util.MethodSpec;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.TargetProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * State providing a new copy of a graph for each invocation of a benchmark. Subclasses of this
 * class are annotated with {@link MethodSpec} to specify the Java method that will be parsed to
 * obtain the original graph.
 */
@State(Scope.Thread)
public abstract class GraalCompilerState {

    /**
     * Original graph from which the per-benchmark invocation {@link #graph} is cloned.
     */
    private StructuredGraph originalGraph;

    /**
     * The graph processed by the benchmark.
     */
    private final OptionValues options;
    private final DebugContext debug;
    private StructuredGraph graph;
    private final Backend backend;
    private final Providers providers;

    /**
     * We only allow inner classes to subclass this to ensure that the {@link Setup} methods are
     * executed in the right order.
     */
    @SuppressWarnings("try")
    protected GraalCompilerState() {
        this.options = Graal.getRequiredCapability(OptionValues.class);
        this.backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        this.providers = backend.getProviders();
        this.debug = DebugContext.create(options, DebugHandlersFactory.LOADER);
    }

    protected boolean useProfilingInfo() {
        return false;
    }

    @SuppressWarnings("try")
    protected void initializeMethod() {
        GraalState graal = new GraalState();
        ResolvedJavaMethod method = graal.metaAccess.lookupJavaMethod(getMethod());
        StructuredGraph structuredGraph = null;
        try (DebugContext.Scope s = debug.scope("GraphState", method)) {
            structuredGraph = preprocessOriginal(getGraph(graal, method, useProfilingInfo()));
        } catch (Throwable t) {
            debug.handle(t);
        }
        this.originalGraph = structuredGraph;
    }

    protected Method getMethod() {
        Class<?> c = getClass();
        if (isMethodSpecAnnotationPresent(c)) {
            return getMethodFromMethodSpec(c);
        }
        return findParamField(this);
    }

    protected boolean isMethodSpecAnnotationPresent(Class<?> startClass) {
        Class<?> c = startClass;
        while (c != null) {
            if (c.isAnnotationPresent(MethodSpec.class)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    /**
     * Declares {@link GraalCompilerState#getMethodFromString(String) method description field}. The
     * field must be a {@link String} and have a {@link Param} annotation.
     */
    @Inherited
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MethodDescString {
    }

    private static Method findParamField(Object obj) {
        Class<?> c = obj.getClass();
        Class<? extends Annotation> annotationClass = MethodDescString.class;
        try {
            for (Field f : c.getFields()) {
                if (f.isAnnotationPresent(annotationClass)) {
                    // these checks could be done by an annotation processor
                    if (!f.getType().equals(String.class)) {
                        throw new RuntimeException("Found a field annotated with " + annotationClass.getSimpleName() + " in " + c + " which is not a " + String.class.getSimpleName());
                    }
                    if (!f.isAnnotationPresent(Param.class)) {
                        throw new RuntimeException("Found a field annotated with " + annotationClass.getSimpleName() + " in " + c + " which is not annotated with " + Param.class.getSimpleName());
                    }
                    String methodName;
                    methodName = (String) f.get(obj);
                    assert methodName != null;
                    return getMethodFromString(methodName);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Could not find class annotated with " + annotationClass.getSimpleName() + " in hierarchy of " + c);
    }

    /**
     * Gets a {@link Method} from a method description string. The format is as follows:
     *
     * <pre>
     * ClassName#MethodName
     * ClassName#MethodName(ClassName, ClassName, ...)
     * </pre>
     *
     * <code>CodeName</code> is passed to {@link Class#forName(String)}. <br>
     * <b>Examples:</b>
     *
     * <pre>
     * java.lang.String#equals
     * java.lang.String#equals(java.lang.Object)
     * </pre>
     */
    protected static Method getMethodFromString(String methodDesc) {
        try {
            String[] s0 = methodDesc.split("#", 2);
            if (s0.length != 2) {
                throw new RuntimeException("Missing method description? " + methodDesc);
            }
            String className = s0[0];
            Class<?> clazz = Class.forName(className);
            String[] s1 = s0[1].split("\\(", 2);
            String name = s1[0];
            Class<?>[] parameters = null;
            if (s1.length > 1) {
                String parametersPart = s1[1];
                if (parametersPart.charAt(parametersPart.length() - 1) != ')') {
                    throw new RuntimeException("Missing closing ')'? " + methodDesc);
                }
                String[] s2 = parametersPart.substring(0, parametersPart.length() - 1).split(",");
                parameters = new Class<?>[s2.length];
                for (int i = 0; i < s2.length; i++) {
                    parameters[i] = Class.forName(s2[i]);
                }
            }
            return GraalUtil.getMethod(clazz, name, parameters);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected StructuredGraph preprocessOriginal(StructuredGraph structuredGraph) {
        return structuredGraph;
    }

    protected OptionValues getOptions() {
        return options;
    }

    protected Suites createSuites(OptionValues opts) {
        return backend.getSuites().getDefaultSuites(opts).copy();
    }

    protected LIRSuites createLIRSuites(OptionValues opts) {
        return backend.getSuites().getDefaultLIRSuites(opts).copy();
    }

    protected Backend getBackend() {
        return backend;
    }

    protected Providers getProviders() {
        return providers;
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

    protected PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        // defensive copying
        return backend.getSuites().getDefaultGraphBuilderSuite().copy();
    }

    protected LIRSuites getLIRSuites() {
        return request.lirSuites;
    }

    private Request<CompilationResult> request;
    private LIRGenerationResult lirGenRes;
    private LIRGeneratorTool lirGenTool;
    private NodeLIRBuilderTool nodeLirGen;
    private RegisterConfig registerConfig;
    private ScheduleResult schedule;
    private AbstractBlockBase<?>[] codeEmittingOrder;
    private AbstractBlockBase<?>[] linearScanOrder;

    /**
     * Copies the {@link #originalGraph original graph} and prepares the {@link #request}.
     *
     * The {@link Suites} can be changed by overriding {@link #createSuites}. {@link LIRSuites} can
     * be changed by overriding {@link #createLIRSuites}.
     */
    protected final void prepareRequest() {
        assert originalGraph != null : "call initialzeMethod first";
        CompilationIdentifier compilationId = backend.getCompilationIdentifier(originalGraph.method());
        graph = originalGraph.copyWithIdentifier(compilationId, originalGraph.getDebug());
        assert !graph.isFrozen();
        ResolvedJavaMethod installedCodeOwner = graph.method();
        request = new Request<>(graph, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL,
                        graph.getProfilingInfo(), createSuites(getOptions()), createLIRSuites(getOptions()), new CompilationResult(graph.compilationId()), CompilationResultBuilderFactory.Default);
    }

    /**
     * Executes the high-level (FrontEnd) part of the compiler.
     */
    protected final void emitFrontEnd() {
        GraalCompiler.emitFrontEnd(request.providers, request.backend, request.graph, request.graphBuilderSuite, request.optimisticOpts, request.profilingInfo, request.suites);
        request.graph.freeze();
    }

    /**
     * Executes the low-level (BackEnd) part of the compiler.
     */
    protected final void emitBackEnd() {
        emitLIR();
        emitCode();
    }

    /**
     * Generates {@link LIR} and executes the {@link LIR} pipeline.
     */
    protected final void emitLIR() {
        generateLIR();
        emitLowLevel();
    }

    /**
     * Generates the initial {@link LIR}.
     */
    protected final void generateLIR() {
        preLIRGeneration();
        lirGeneration();
    }

    /**
     * Sets up {@link LIR} generation.
     */
    protected final void preLIRGeneration() {
        assert request.graph.isFrozen() : "Graph not frozen.";
        Object stub = null;
        schedule = request.graph.getLastSchedule();
        ControlFlowGraph cfg = deepCopy(schedule.getCFG());
        Block[] blocks = cfg.getBlocks();
        Block startBlock = cfg.getStartBlock();
        assert startBlock != null;
        assert startBlock.getPredecessorCount() == 0;

        codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.length, startBlock);
        linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.length, startBlock);

        LIR lir = new LIR(cfg, linearScanOrder, codeEmittingOrder, getGraphOptions(), getGraphDebug());
        FrameMapBuilder frameMapBuilder = request.backend.newFrameMapBuilder(registerConfig);
        lirGenRes = request.backend.newLIRGenerationResult(graph.compilationId(), lir, frameMapBuilder, request.graph, stub);
        lirGenTool = request.backend.newLIRGenerator(lirGenRes);
        nodeLirGen = request.backend.newNodeLIRBuilder(request.graph, lirGenTool);
    }

    protected OptionValues getGraphOptions() {
        return graph.getOptions();
    }

    protected DebugContext getGraphDebug() {
        return graph.getDebug();
    }

    private static ControlFlowGraph deepCopy(ControlFlowGraph cfg) {
        return ControlFlowGraph.compute(cfg.graph, true, true, true, true);
    }

    /**
     * Executes the {@link LIRGenerationPhase}.
     */
    protected final void lirGeneration() {
        LIRGenerationContext context = new LIRGenerationContext(lirGenTool, nodeLirGen, request.graph, schedule);
        new LIRGenerationPhase().apply(request.backend.getTarget(), lirGenRes, context);
    }

    /**
     * Executes the low-level compiler stages.
     */
    protected final void emitLowLevel() {
        preAllocationStage();
        allocationStage();
        postAllocationStage();
    }

    /**
     * Executes a {@link LIRPhase} within a given {@code context}.
     */
    protected <C> void applyLIRPhase(LIRPhase<C> phase, C context) {
        phase.apply(request.backend.getTarget(), lirGenRes, context);
    }

    /**
     * Executes the {@link PreAllocationStage}.
     *
     * {@link LIRPhase phases} can be changed by overriding {@link #createLIRSuites}.
     */
    protected final void preAllocationStage() {
        applyLIRPhase(getLIRSuites().getPreAllocationOptimizationStage(), createPreAllocationOptimizationContext());
    }

    protected PreAllocationOptimizationContext createPreAllocationOptimizationContext() {
        return new PreAllocationOptimizationContext(lirGenTool);
    }

    /**
     * Executes the {@link AllocationStage}.
     *
     * {@link LIRPhase phases} can be changed by overriding {@link #createLIRSuites}.
     */
    protected final void allocationStage() {
        applyLIRPhase(getLIRSuites().getAllocationStage(), createAllocationContext());
    }

    protected AllocationContext createAllocationContext() {
        return new AllocationContext(lirGenTool.getSpillMoveFactory(), request.backend.newRegisterAllocationConfig(registerConfig, null));
    }

    /**
     * Executes the {@link PostAllocationStage}.
     *
     * {@link LIRPhase phases} can be changed by overriding {@link #createLIRSuites}.
     */
    protected final void postAllocationStage() {
        applyLIRPhase(getLIRSuites().getPostAllocationOptimizationStage(), createPostAllocationOptimizationContext());
    }

    protected PostAllocationOptimizationContext createPostAllocationOptimizationContext() {
        return new PostAllocationOptimizationContext(lirGenTool);
    }

    /**
     * Emits the machine code.
     */
    protected final void emitCode() {
        int bytecodeSize = request.graph.method() == null ? 0 : request.graph.getBytecodeSize();
        request.compilationResult.setHasUnsafeAccess(request.graph.hasUnsafeAccess());
        GraalCompiler.emitCode(request.backend, request.graph.getAssumptions(), request.graph.method(), request.graph.getMethods(), request.graph.getFields(), bytecodeSize, lirGenRes,
                        request.compilationResult, request.installedCodeOwner, request.factory);
    }

    protected StructuredGraph graph() {
        return graph;
    }

    protected LIR getLIR() {
        return lirGenRes.getLIR();
    }

    public abstract static class Compile extends GraalCompilerState {

        @Setup(Level.Trial)
        public void init() {
            initializeMethod();
        }

        @Setup(Level.Invocation)
        public void setup() {
            prepareRequest();
        }

        public CompilationResult compile() {
            emitFrontEnd();
            emitBackEnd();
            return super.request.compilationResult;
        }

    }

    public abstract static class FrontEndOnly extends GraalCompilerState {

        @Setup(Level.Trial)
        public void init() {
            initializeMethod();
        }

        @Setup(Level.Invocation)
        public void setup() {
            prepareRequest();
        }

        public StructuredGraph compile() {
            emitFrontEnd();
            return super.graph;
        }

    }

    public abstract static class BackEndOnly extends GraalCompilerState {

        @Setup(Level.Trial)
        public void init() {
            initializeMethod();
        }

        /**
         * Cannot do this {@link Level#Trial only once} since {@link #emitCode()} closes the
         * {@link CompilationResult}.
         */
        @Setup(Level.Invocation)
        public void setupGraph() {
            prepareRequest();
            emitFrontEnd();
        }

        public CompilationResult compile() {
            emitBackEnd();
            return super.request.compilationResult;
        }
    }

    public abstract static class PreAllocationStage extends GraalCompilerState {
        /**
         * No need to rebuild the graph for every invocation since it is not altered by the backend.
         */
        @Setup(Level.Trial)
        public void setupGraph() {
            initializeMethod();
            prepareRequest();
            emitFrontEnd();
        }

        @Setup(Level.Invocation)
        public void setup() {
            generateLIR();
        }

        public LIRGenerationResult compile() {
            preAllocationStage();
            return super.lirGenRes;
        }
    }

    public abstract static class AllocationStage extends GraalCompilerState {
        /**
         * No need to rebuild the graph for every invocation since it is not altered by the backend.
         */
        @Setup(Level.Trial)
        public void setupGraph() {
            initializeMethod();
            prepareRequest();
            emitFrontEnd();
        }

        @Setup(Level.Invocation)
        public void setup() {
            generateLIR();
            preAllocationStage();
        }

        public LIRGenerationResult compile() {
            allocationStage();
            return super.lirGenRes;
        }
    }

    public abstract static class PostAllocationStage extends GraalCompilerState {
        /**
         * No need to rebuild the graph for every invocation since it is not altered by the backend.
         */
        @Setup(Level.Trial)
        public void setupGraph() {
            initializeMethod();
            prepareRequest();
            emitFrontEnd();
        }

        @Setup(Level.Invocation)
        public void setup() {
            generateLIR();
            preAllocationStage();
            allocationStage();
        }

        public LIRGenerationResult compile() {
            postAllocationStage();
            return super.lirGenRes;
        }
    }
}
