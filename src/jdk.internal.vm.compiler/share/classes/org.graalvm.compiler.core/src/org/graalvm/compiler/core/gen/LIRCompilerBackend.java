/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.gen;

import java.util.Collection;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.LIRGenerationPhase;
import org.graalvm.compiler.core.LIRGenerationPhase.LIRGenerationContext;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.alloc.ComputeBlockOrder;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.CompilerPhaseScope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.alloc.OutOfRegistersException;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.framemap.FrameMap;
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

import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.VMConstant;

public class LIRCompilerBackend {
    private static final TimerKey EmitLIR = DebugContext.timer("EmitLIR").doc("Time spent generating LIR from HIR.");
    private static final TimerKey EmitCode = DebugContext.timer("EmitCode").doc("Time spent generating machine code from LIR.");
    private static final TimerKey BackEnd = DebugContext.timer("BackEnd").doc("Time spent in EmitLIR and EmitCode.");

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
                emitCode(backend,
                                graph.getAssumptions(),
                                graph.method(),
                                graph.getMethods(),
                                graph.getFields(),
                                graph.getSpeculationLog(),
                                bytecodeSize,
                                lirGen,
                                compilationResult,
                                installedCodeOwner,
                                factory);
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
    private static LIRGenerationResult emitLIR0(Backend backend,
                    StructuredGraph graph,
                    Object stub,
                    RegisterConfig registerConfig,
                    LIRSuites lirSuites,
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

            LIRGenerationProvider lirBackend = (LIRGenerationProvider) backend;
            RegisterAllocationConfig registerAllocationConfig = backend.newRegisterAllocationConfig(registerConfig, allocationRestrictedTo);
            LIRGenerationResult lirGenRes = lirBackend.newLIRGenerationResult(graph.compilationId(), lir, registerAllocationConfig, graph, stub);
            LIRGeneratorTool lirGen = lirBackend.newLIRGenerator(lirGenRes);
            NodeLIRBuilderTool nodeLirGen = lirBackend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            LIRGenerationContext context = new LIRGenerationContext(lirGen, nodeLirGen, graph, schedule);
            new LIRGenerationPhase().apply(backend.getTarget(), lirGenRes, context);

            try (DebugContext.Scope s = debug.scope("LIRStages", nodeLirGen, lirGenRes, lir)) {
                // Dump LIR along with HIR (the LIR is looked up from context)
                debug.dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "After LIR generation");
                LIRGenerationResult result = emitLowLevel(backend.getTarget(), lirGenRes, lirGen, lirSuites, registerAllocationConfig);
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

    private static LIRGenerationResult emitLowLevel(TargetDescription target, LIRGenerationResult lirGenRes, LIRGeneratorTool lirGen, LIRSuites lirSuites,
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
    public static void emitCode(Backend backend,
                    Assumptions assumptions,
                    ResolvedJavaMethod rootMethod,
                    Collection<ResolvedJavaMethod> inlinedMethods,
                    EconomicSet<ResolvedJavaField> accessedFields,
                    SpeculationLog speculationLog,
                    int bytecodeSize,
                    LIRGenerationResult lirGenRes,
                    CompilationResult compilationResult,
                    ResolvedJavaMethod installedCodeOwner,
                    CompilationResultBuilderFactory factory) {
        DebugContext debug = lirGenRes.getLIR().getDebug();
        try (DebugCloseable a = EmitCode.start(debug); CompilerPhaseScope cps = debug.enterCompilerPhase("Emit code");) {
            LIRGenerationProvider lirBackend = (LIRGenerationProvider) backend;

            FrameMap frameMap = lirGenRes.getFrameMap();
            CompilationResultBuilder crb = lirBackend.newCompilationResultBuilder(lirGenRes, frameMap, compilationResult, factory);
            lirBackend.emitCode(crb, lirGenRes.getLIR(), installedCodeOwner);
            if (assumptions != null && !assumptions.isEmpty()) {
                compilationResult.setAssumptions(assumptions.toArray());
            }
            if (rootMethod != null) {
                compilationResult.setMethods(rootMethod, inlinedMethods);
                compilationResult.setFields(accessedFields);
                compilationResult.setBytecodeSize(bytecodeSize);
            }
            if (speculationLog != null) {
                compilationResult.setSpeculationLog(speculationLog);
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
