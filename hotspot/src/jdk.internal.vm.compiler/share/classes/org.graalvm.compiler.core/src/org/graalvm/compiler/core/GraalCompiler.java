/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core;

import static org.graalvm.compiler.core.GraalCompilerOptions.EmitLIRRepeatCount;
import static org.graalvm.compiler.core.common.GraalOptions.UseGraalInstrumentation;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.LIRGenerationPhase.LIRGenerationContext;
import org.graalvm.compiler.core.common.alloc.ComputeBlockOrder;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.DebugTimer;
import org.graalvm.compiler.debug.internal.method.MethodMetricsRootScopeInfo;
import org.graalvm.compiler.lir.BailoutAndRestartBackendException;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.instrumentation.ExtractInstrumentationPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.TargetProvider;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

/**
 * Static methods for orchestrating the compilation of a {@linkplain StructuredGraph graph}.
 */
public class GraalCompiler {

    private static final DebugTimer CompilerTimer = Debug.timer("GraalCompiler");
    private static final DebugTimer FrontEnd = Debug.timer("FrontEnd");
    private static final DebugTimer BackEnd = Debug.timer("BackEnd");
    private static final DebugTimer EmitLIR = Debug.timer("EmitLIR");
    private static final DebugTimer EmitCode = Debug.timer("EmitCode");
    private static final LIRGenerationPhase LIR_GENERATION_PHASE = new LIRGenerationPhase();

    /**
     * Encapsulates all the inputs to a {@linkplain GraalCompiler#compile(Request) compilation}.
     */
    public static class Request<T extends CompilationResult> {
        public final StructuredGraph graph;
        public final ResolvedJavaMethod installedCodeOwner;
        public final Providers providers;
        public final Backend backend;
        public final PhaseSuite<HighTierContext> graphBuilderSuite;
        public final OptimisticOptimizations optimisticOpts;
        public final ProfilingInfo profilingInfo;
        public final Suites suites;
        public final LIRSuites lirSuites;
        public final T compilationResult;
        public final CompilationResultBuilderFactory factory;

        /**
         * @param graph the graph to be compiled
         * @param installedCodeOwner the method the compiled code will be associated with once
         *            installed. This argument can be null.
         * @param providers
         * @param backend
         * @param graphBuilderSuite
         * @param optimisticOpts
         * @param profilingInfo
         * @param suites
         * @param lirSuites
         * @param compilationResult
         * @param factory
         */
        public Request(StructuredGraph graph, ResolvedJavaMethod installedCodeOwner, Providers providers, Backend backend, PhaseSuite<HighTierContext> graphBuilderSuite,
                        OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, Suites suites, LIRSuites lirSuites, T compilationResult, CompilationResultBuilderFactory factory) {
            this.graph = graph;
            this.installedCodeOwner = installedCodeOwner;
            this.providers = providers;
            this.backend = backend;
            this.graphBuilderSuite = graphBuilderSuite;
            this.optimisticOpts = optimisticOpts;
            this.profilingInfo = profilingInfo;
            this.suites = suites;
            this.lirSuites = lirSuites;
            this.compilationResult = compilationResult;
            this.factory = factory;
        }

        /**
         * Executes this compilation request.
         *
         * @return the result of the compilation
         */
        public T execute() {
            return GraalCompiler.compile(this);
        }
    }

    /**
     * Requests compilation of a given graph.
     *
     * @param graph the graph to be compiled
     * @param installedCodeOwner the method the compiled code will be associated with once
     *            installed. This argument can be null.
     * @return the result of the compilation
     */
    public static <T extends CompilationResult> T compileGraph(StructuredGraph graph, ResolvedJavaMethod installedCodeOwner, Providers providers, Backend backend,
                    PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, Suites suites, LIRSuites lirSuites, T compilationResult,
                    CompilationResultBuilderFactory factory) {
        return compile(new Request<>(graph, installedCodeOwner, providers, backend, graphBuilderSuite, optimisticOpts, profilingInfo, suites, lirSuites, compilationResult, factory));
    }

    /**
     * Services a given compilation request.
     *
     * @return the result of the compilation
     */
    @SuppressWarnings("try")
    public static <T extends CompilationResult> T compile(Request<T> r) {
        try (Scope s = MethodMetricsRootScopeInfo.createRootScopeIfAbsent(r.installedCodeOwner);
                        CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod()) {
            assert !r.graph.isFrozen();
            try (Scope s0 = Debug.scope("GraalCompiler", r.graph, r.providers.getCodeCache()); DebugCloseable a = CompilerTimer.start()) {
                emitFrontEnd(r.providers, r.backend, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.suites);
                emitBackEnd(r.graph, null, r.installedCodeOwner, r.backend, r.compilationResult, r.factory, null, r.lirSuites);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            return r.compilationResult;
        }
    }

    /**
     * Builds the graph, optimizes it.
     */
    @SuppressWarnings("try")
    public static void emitFrontEnd(Providers providers, TargetProvider target, StructuredGraph graph, PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts,
                    ProfilingInfo profilingInfo, Suites suites) {
        try (Scope s = Debug.scope("FrontEnd"); DebugCloseable a = FrontEnd.start()) {
            HighTierContext highTierContext = new HighTierContext(providers, graphBuilderSuite, optimisticOpts);
            if (graph.start().next() == null) {
                graphBuilderSuite.apply(graph, highTierContext);
                new DeadCodeEliminationPhase(Optional).apply(graph);
            } else {
                Debug.dump(Debug.INFO_LOG_LEVEL, graph, "initial state");
            }
            if (UseGraalInstrumentation.getValue()) {
                new ExtractInstrumentationPhase().apply(graph, highTierContext);
            }

            suites.getHighTier().apply(graph, highTierContext);
            graph.maybeCompress();

            MidTierContext midTierContext = new MidTierContext(providers, target, optimisticOpts, profilingInfo);
            suites.getMidTier().apply(graph, midTierContext);
            graph.maybeCompress();

            LowTierContext lowTierContext = new LowTierContext(providers, target);
            suites.getLowTier().apply(graph, lowTierContext);

            Debug.dump(Debug.BASIC_LOG_LEVEL, graph.getLastSchedule(), "Final HIR schedule");
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    @SuppressWarnings("try")
    public static <T extends CompilationResult> void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, Backend backend, T compilationResult,
                    CompilationResultBuilderFactory factory, RegisterConfig registerConfig, LIRSuites lirSuites) {
        try (Scope s = Debug.scope("BackEnd", graph.getLastSchedule()); DebugCloseable a = BackEnd.start()) {
            // Repeatedly run the LIR code generation pass to improve statistical profiling results.
            for (int i = 0; i < EmitLIRRepeatCount.getValue(); i++) {
                SchedulePhase dummySchedule = new SchedulePhase();
                dummySchedule.apply(graph);
                emitLIR(backend, graph, stub, registerConfig, lirSuites);
            }

            LIRGenerationResult lirGen = null;
            lirGen = emitLIR(backend, graph, stub, registerConfig, lirSuites);
            try (Scope s2 = Debug.scope("CodeGen", lirGen, lirGen.getLIR())) {
                int bytecodeSize = graph.method() == null ? 0 : graph.getBytecodeSize();
                compilationResult.setHasUnsafeAccess(graph.hasUnsafeAccess());
                emitCode(backend, graph.getAssumptions(), graph.method(), graph.getMethods(), graph.getFields(), bytecodeSize, lirGen, compilationResult, installedCodeOwner, factory);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    @SuppressWarnings("try")
    public static LIRGenerationResult emitLIR(Backend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, LIRSuites lirSuites) {
        OverrideScope overrideScope = null;
        LIRSuites lirSuites0 = lirSuites;
        while (true) {
            try (OverrideScope scope = overrideScope) {
                return emitLIR0(backend, graph, stub, registerConfig, lirSuites0);
            } catch (BailoutAndRestartBackendException e) {
                if (BailoutAndRestartBackendException.Options.LIRUnlockBackendRestart.getValue() && e.shouldRestart()) {
                    overrideScope = e.getOverrideScope();
                    lirSuites0 = e.updateLIRSuites(lirSuites);
                    if (lirSuites0 != null) {
                        continue;
                    }
                }
                /*
                 * The BailoutAndRestartBackendException is permanent. If restart fails or is
                 * disabled we throw the bailout.
                 */
                throw e;
            }
        }
    }

    @SuppressWarnings("try")
    private static LIRGenerationResult emitLIR0(Backend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, LIRSuites lirSuites) {
        try (Scope ds = Debug.scope("EmitLIR"); DebugCloseable a = EmitLIR.start()) {
            ScheduleResult schedule = graph.getLastSchedule();
            Block[] blocks = schedule.getCFG().getBlocks();
            Block startBlock = schedule.getCFG().getStartBlock();
            assert startBlock != null;
            assert startBlock.getPredecessorCount() == 0;

            LIR lir = null;
            AbstractBlockBase<?>[] codeEmittingOrder = null;
            AbstractBlockBase<?>[] linearScanOrder = null;
            try (Scope s = Debug.scope("ComputeLinearScanOrder", lir)) {
                codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.length, startBlock);
                linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.length, startBlock);

                lir = new LIR(schedule.getCFG(), linearScanOrder, codeEmittingOrder);
                Debug.dump(Debug.INFO_LOG_LEVEL, lir, "After linear scan order");
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            FrameMapBuilder frameMapBuilder = backend.newFrameMapBuilder(registerConfig);
            LIRGenerationResult lirGenRes = backend.newLIRGenerationResult(graph.compilationId(), lir, frameMapBuilder, graph, stub);
            LIRGeneratorTool lirGen = backend.newLIRGenerator(lirGenRes);
            NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            LIRGenerationContext context = new LIRGenerationContext(lirGen, nodeLirGen, graph, schedule);
            LIR_GENERATION_PHASE.apply(backend.getTarget(), lirGenRes, context);

            try (Scope s = Debug.scope("LIRStages", nodeLirGen, lir)) {
                Debug.dump(Debug.BASIC_LOG_LEVEL, lir, "After LIR generation");
                LIRGenerationResult result = emitLowLevel(backend.getTarget(), lirGenRes, lirGen, lirSuites, backend.newRegisterAllocationConfig(registerConfig));
                Debug.dump(Debug.BASIC_LOG_LEVEL, lir, "Before code generation");
                return result;
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    protected static <T extends CompilationResult> String getCompilationUnitName(StructuredGraph graph, T compilationResult) {
        if (compilationResult != null && compilationResult.getName() != null) {
            return compilationResult.getName();
        }
        ResolvedJavaMethod method = graph.method();
        if (method == null) {
            return "<unknown>";
        }
        return method.format("%H.%n(%p)");
    }

    public static LIRGenerationResult emitLowLevel(TargetDescription target, LIRGenerationResult lirGenRes, LIRGeneratorTool lirGen, LIRSuites lirSuites,
                    RegisterAllocationConfig registerAllocationConfig) {
        PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(lirGen);
        lirSuites.getPreAllocationOptimizationStage().apply(target, lirGenRes, preAllocOptContext);

        AllocationContext allocContext = new AllocationContext(lirGen.getSpillMoveFactory(), registerAllocationConfig);
        lirSuites.getAllocationStage().apply(target, lirGenRes, allocContext);

        PostAllocationOptimizationContext postAllocOptContext = new PostAllocationOptimizationContext(lirGen);
        lirSuites.getPostAllocationOptimizationStage().apply(target, lirGenRes, postAllocOptContext);

        return lirGenRes;
    }

    @SuppressWarnings("try")
    public static void emitCode(Backend backend, Assumptions assumptions, ResolvedJavaMethod rootMethod, Collection<ResolvedJavaMethod> inlinedMethods, Collection<ResolvedJavaField> accessedFields,
                    int bytecodeSize, LIRGenerationResult lirGenRes,
                    CompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner, CompilationResultBuilderFactory factory) {
        try (DebugCloseable a = EmitCode.start()) {
            FrameMap frameMap = lirGenRes.getFrameMap();
            CompilationResultBuilder crb = backend.newCompilationResultBuilder(lirGenRes, frameMap, compilationResult, factory);
            backend.emitCode(crb, lirGenRes.getLIR(), installedCodeOwner);
            if (assumptions != null && !assumptions.isEmpty()) {
                compilationResult.setAssumptions(assumptions.toArray());
            }
            if (rootMethod != null) {
                compilationResult.setMethods(rootMethod, inlinedMethods);
                compilationResult.setFields(accessedFields);
                compilationResult.setBytecodeSize(bytecodeSize);
            }
            crb.finish();
            if (Debug.isCountEnabled()) {
                List<DataPatch> ldp = compilationResult.getDataPatches();
                JavaKind[] kindValues = JavaKind.values();
                DebugCounter[] dms = new DebugCounter[kindValues.length];
                for (int i = 0; i < dms.length; i++) {
                    dms[i] = Debug.counter("DataPatches-%s", kindValues[i]);
                }

                for (DataPatch dp : ldp) {
                    JavaKind kind = JavaKind.Illegal;
                    if (dp.reference instanceof ConstantReference) {
                        VMConstant constant = ((ConstantReference) dp.reference).getConstant();
                        if (constant instanceof JavaConstant) {
                            kind = ((JavaConstant) constant).getJavaKind();
                        }
                    }
                    dms[kind.ordinal()].add(1);
                }

                Debug.counter("CompilationResults").increment();
                Debug.counter("CodeBytesEmitted").add(compilationResult.getTargetCodeSize());
                Debug.counter("InfopointsEmitted").add(compilationResult.getInfopoints().size());
                Debug.counter("DataPatches").add(ldp.size());
                Debug.counter("ExceptionHandlersEmitted").add(compilationResult.getExceptionHandlers().size());
            }

            Debug.dump(Debug.BASIC_LOG_LEVEL, compilationResult, "After code generation");
        }
    }
}
