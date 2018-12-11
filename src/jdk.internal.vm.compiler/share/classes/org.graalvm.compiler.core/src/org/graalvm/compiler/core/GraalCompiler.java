/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.LIRGenerationPhase.LIRGenerationContext;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.core.common.alloc.ComputeBlockOrder;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.alloc.OutOfRegistersException;
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
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
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

    private static final TimerKey CompilerTimer = DebugContext.timer("GraalCompiler").doc("Time spent in compilation (excludes code installation).");
    private static final TimerKey FrontEnd = DebugContext.timer("FrontEnd").doc("Time spent processing HIR.");
    private static final TimerKey EmitLIR = DebugContext.timer("EmitLIR").doc("Time spent generating LIR from HIR.");
    private static final TimerKey EmitCode = DebugContext.timer("EmitCode").doc("Time spent generating machine code from LIR.");
    private static final TimerKey BackEnd = DebugContext.timer("BackEnd").doc("Time spent in EmitLIR and EmitCode.");

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
        public final boolean verifySourcePositions;

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
                        OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, Suites suites, LIRSuites lirSuites, T compilationResult, CompilationResultBuilderFactory factory,
                        boolean verifySourcePositions) {
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
            this.verifySourcePositions = verifySourcePositions;
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
                    CompilationResultBuilderFactory factory, boolean verifySourcePositions) {
        return compile(new Request<>(graph, installedCodeOwner, providers, backend, graphBuilderSuite, optimisticOpts, profilingInfo, suites, lirSuites, compilationResult, factory,
                        verifySourcePositions));
    }

    /**
     * Services a given compilation request.
     *
     * @return the result of the compilation
     */
    @SuppressWarnings("try")
    public static <T extends CompilationResult> T compile(Request<T> r) {
        DebugContext debug = r.graph.getDebug();
        try (CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(r.graph.getOptions())) {
            assert !r.graph.isFrozen();
            try (DebugContext.Scope s0 = debug.scope("GraalCompiler", r.graph, r.providers.getCodeCache()); DebugCloseable a = CompilerTimer.start(debug)) {
                emitFrontEnd(r.providers, r.backend, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.suites);
                emitBackEnd(r.graph, null, r.installedCodeOwner, r.backend, r.compilationResult, r.factory, null, r.lirSuites);
                if (r.verifySourcePositions) {
                    assert r.graph.verifySourcePositions(true);
                }
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            checkForRequestedCrash(r.graph);
            return r.compilationResult;
        }
    }

    /**
     * Checks whether the {@link GraalCompilerOptions#CrashAt} option indicates that the compilation
     * of {@code graph} should result in an exception.
     *
     * @param graph a graph currently being compiled
     * @throws RuntimeException if the value of {@link GraalCompilerOptions#CrashAt} matches
     *             {@code graph.method()} or {@code graph.name}
     */
    private static void checkForRequestedCrash(StructuredGraph graph) {
        String value = GraalCompilerOptions.CrashAt.getValue(graph.getOptions());
        if (value != null) {
            boolean bailout = false;
            boolean permanentBailout = false;
            String methodPattern = value;
            if (value.endsWith(":Bailout")) {
                methodPattern = value.substring(0, value.length() - ":Bailout".length());
                bailout = true;
            } else if (value.endsWith(":PermanentBailout")) {
                methodPattern = value.substring(0, value.length() - ":PermanentBailout".length());
                permanentBailout = true;
            }
            String crashLabel = null;
            if (graph.name != null && graph.name.contains(methodPattern)) {
                crashLabel = graph.name;
            }
            if (crashLabel == null) {
                ResolvedJavaMethod method = graph.method();
                MethodFilter[] filters = MethodFilter.parse(methodPattern);
                for (MethodFilter filter : filters) {
                    if (filter.matches(method)) {
                        crashLabel = method.format("%H.%n(%p)");
                    }
                }
            }
            if (crashLabel != null) {
                if (permanentBailout) {
                    throw new PermanentBailoutException("Forced crash after compiling " + crashLabel);
                }
                if (bailout) {
                    throw new RetryableBailoutException("Forced crash after compiling " + crashLabel);
                }
                throw new RuntimeException("Forced crash after compiling " + crashLabel);
            }
        }
    }

    /**
     * Builds the graph, optimizes it.
     */
    @SuppressWarnings("try")
    public static void emitFrontEnd(Providers providers, TargetProvider target, StructuredGraph graph, PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts,
                    ProfilingInfo profilingInfo, Suites suites) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("FrontEnd"); DebugCloseable a = FrontEnd.start(debug)) {
            HighTierContext highTierContext = new HighTierContext(providers, graphBuilderSuite, optimisticOpts);
            if (graph.start().next() == null) {
                graphBuilderSuite.apply(graph, highTierContext);
                new DeadCodeEliminationPhase(DeadCodeEliminationPhase.Optionality.Optional).apply(graph);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "After parsing");
            } else {
                debug.dump(DebugContext.INFO_LEVEL, graph, "initial state");
            }

            suites.getHighTier().apply(graph, highTierContext);
            graph.maybeCompress();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After high tier");

            MidTierContext midTierContext = new MidTierContext(providers, target, optimisticOpts, profilingInfo);
            suites.getMidTier().apply(graph, midTierContext);
            graph.maybeCompress();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After mid tier");

            LowTierContext lowTierContext = new LowTierContext(providers, target);
            suites.getLowTier().apply(graph, lowTierContext);
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After low tier");

            debug.dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "Final HIR schedule");
            graph.logInliningTree();
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
        }
    }

    @SuppressWarnings("try")
    public static <T extends CompilationResult> void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, Backend backend, T compilationResult,
                    CompilationResultBuilderFactory factory, RegisterConfig registerConfig, LIRSuites lirSuites) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("BackEnd", graph.getLastSchedule()); DebugCloseable a = BackEnd.start(debug)) {
            LIRGenerationResult lirGen = null;
            lirGen = emitLIR(backend, graph, stub, registerConfig, lirSuites);
            try (DebugContext.Scope s2 = debug.scope("CodeGen", lirGen, lirGen.getLIR())) {
                int bytecodeSize = graph.method() == null ? 0 : graph.getBytecodeSize();
                compilationResult.setHasUnsafeAccess(graph.hasUnsafeAccess());
                emitCode(backend, graph.getAssumptions(), graph.method(), graph.getMethods(), graph.getFields(), bytecodeSize, lirGen, compilationResult, installedCodeOwner, factory);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
        }
    }

    @SuppressWarnings("try")
    public static LIRGenerationResult emitLIR(Backend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, LIRSuites lirSuites) {
        String registerPressure = GraalOptions.RegisterPressure.getValue(graph.getOptions());
        String[] allocationRestrictedTo = registerPressure == null ? null : registerPressure.split(",");
        try {
            return emitLIR0(backend, graph, stub, registerConfig, lirSuites, allocationRestrictedTo);
        } catch (OutOfRegistersException e) {
            if (allocationRestrictedTo != null) {
                allocationRestrictedTo = null;
                return emitLIR0(backend, graph, stub, registerConfig, lirSuites, allocationRestrictedTo);
            }
            /* If the re-execution fails we convert the exception into a "hard" failure */
            throw new GraalError(e);
        } finally {
            graph.checkCancellation();
        }
    }

    @SuppressWarnings("try")
    private static LIRGenerationResult emitLIR0(Backend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, LIRSuites lirSuites,
                    String[] allocationRestrictedTo) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope ds = debug.scope("EmitLIR"); DebugCloseable a = EmitLIR.start(debug)) {
            assert !graph.hasValueProxies();
            ScheduleResult schedule = graph.getLastSchedule();
            Block[] blocks = schedule.getCFG().getBlocks();
            Block startBlock = schedule.getCFG().getStartBlock();
            assert startBlock != null;
            assert startBlock.getPredecessorCount() == 0;

            AbstractBlockBase<?>[] codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.length, startBlock);
            AbstractBlockBase<?>[] linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.length, startBlock);
            LIR lir = new LIR(schedule.getCFG(), linearScanOrder, codeEmittingOrder, graph.getOptions(), graph.getDebug());

            FrameMapBuilder frameMapBuilder = backend.newFrameMapBuilder(registerConfig);
            LIRGenerationResult lirGenRes = backend.newLIRGenerationResult(graph.compilationId(), lir, frameMapBuilder, graph, stub);
            LIRGeneratorTool lirGen = backend.newLIRGenerator(lirGenRes);
            NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            LIRGenerationContext context = new LIRGenerationContext(lirGen, nodeLirGen, graph, schedule);
            new LIRGenerationPhase().apply(backend.getTarget(), lirGenRes, context);

            try (DebugContext.Scope s = debug.scope("LIRStages", nodeLirGen, lirGenRes, lir)) {
                // Dump LIR along with HIR (the LIR is looked up from context)
                debug.dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "After LIR generation");
                LIRGenerationResult result = emitLowLevel(backend.getTarget(), lirGenRes, lirGen, lirSuites, backend.newRegisterAllocationConfig(registerConfig, allocationRestrictedTo));
                return result;
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        } finally {
            graph.checkCancellation();
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
        DebugContext debug = lirGenRes.getLIR().getDebug();
        PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationContext(lirGen);
        lirSuites.getPreAllocationOptimizationStage().apply(target, lirGenRes, preAllocOptContext);
        debug.dump(DebugContext.BASIC_LEVEL, lirGenRes.getLIR(), "After PreAllocationOptimizationStage");

        AllocationContext allocContext = new AllocationContext(lirGen.getSpillMoveFactory(), registerAllocationConfig);
        lirSuites.getAllocationStage().apply(target, lirGenRes, allocContext);
        debug.dump(DebugContext.BASIC_LEVEL, lirGenRes.getLIR(), "After AllocationStage");

        PostAllocationOptimizationContext postAllocOptContext = new PostAllocationOptimizationContext(lirGen);
        lirSuites.getPostAllocationOptimizationStage().apply(target, lirGenRes, postAllocOptContext);
        debug.dump(DebugContext.BASIC_LEVEL, lirGenRes.getLIR(), "After PostAllocationOptimizationStage");

        return lirGenRes;
    }

    @SuppressWarnings("try")
    public static void emitCode(Backend backend, Assumptions assumptions, ResolvedJavaMethod rootMethod, Collection<ResolvedJavaMethod> inlinedMethods, EconomicSet<ResolvedJavaField> accessedFields,
                    int bytecodeSize, LIRGenerationResult lirGenRes,
                    CompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner, CompilationResultBuilderFactory factory) {
        DebugContext debug = lirGenRes.getLIR().getDebug();
        try (DebugCloseable a = EmitCode.start(debug)) {
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
            if (debug.isCountEnabled()) {
                List<DataPatch> ldp = compilationResult.getDataPatches();
                JavaKind[] kindValues = JavaKind.values();
                CounterKey[] dms = new CounterKey[kindValues.length];
                for (int i = 0; i < dms.length; i++) {
                    dms[i] = DebugContext.counter("DataPatches-%s", kindValues[i]);
                }

                for (DataPatch dp : ldp) {
                    JavaKind kind = JavaKind.Illegal;
                    if (dp.reference instanceof ConstantReference) {
                        VMConstant constant = ((ConstantReference) dp.reference).getConstant();
                        if (constant instanceof JavaConstant) {
                            kind = ((JavaConstant) constant).getJavaKind();
                        }
                    }
                    dms[kind.ordinal()].add(debug, 1);
                }

                DebugContext.counter("CompilationResults").increment(debug);
                DebugContext.counter("CodeBytesEmitted").add(debug, compilationResult.getTargetCodeSize());
                DebugContext.counter("InfopointsEmitted").add(debug, compilationResult.getInfopoints().size());
                DebugContext.counter("DataPatches").add(debug, ldp.size());
                DebugContext.counter("ExceptionHandlersEmitted").add(debug, compilationResult.getExceptionHandlers().size());
            }

            debug.dump(DebugContext.BASIC_LEVEL, compilationResult, "After code generation");
        }
    }
}
