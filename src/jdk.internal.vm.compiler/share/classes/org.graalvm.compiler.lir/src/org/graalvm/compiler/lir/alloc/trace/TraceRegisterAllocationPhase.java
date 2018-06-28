/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.alloc.trace;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.alloc.RegisterAllocationPhase;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase.TraceAllocationContext;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.lir.ssa.SSAUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Implements the Trace Register Allocation approach as described in
 * <a href="http://dx.doi.org/10.1145/2972206.2972211">"Trace-based Register Allocation in a JIT
 * Compiler"</a> by Josef Eisl et al.
 */
public final class TraceRegisterAllocationPhase extends RegisterAllocationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Use inter-trace register hints.", type = OptionType.Debug)
        public static final OptionKey<Boolean> TraceRAuseInterTraceHints = new OptionKey<>(true);
        @Option(help = "Share information about spilled values to other traces.", type = OptionType.Debug)
        public static final OptionKey<Boolean> TraceRAshareSpillInformation = new OptionKey<>(true);
        @Option(help = "Reuse spill slots for global move resolution cycle breaking.", type = OptionType.Debug)
        public static final OptionKey<Boolean> TraceRAreuseStackSlotsForMoveResolutionCycleBreaking = new OptionKey<>(true);
        @Option(help = "Cache stack slots globally (i.e. a variable always gets the same slot in every trace).", type = OptionType.Debug)
        public static final OptionKey<Boolean> TraceRACacheStackSlots = new OptionKey<>(true);
        // @formatter:on
    }

    private static final CounterKey tracesCounter = DebugContext.counter("TraceRA[traces]");

    public static final CounterKey globalStackSlots = DebugContext.counter("TraceRA[GlobalStackSlots]");
    public static final CounterKey allocatedStackSlots = DebugContext.counter("TraceRA[AllocatedStackSlots]");

    private final TraceBuilderPhase traceBuilder;
    private final GlobalLivenessAnalysisPhase livenessAnalysis;

    public TraceRegisterAllocationPhase() {
        this.traceBuilder = new TraceBuilderPhase();
        this.livenessAnalysis = new GlobalLivenessAnalysisPhase();
    }

    @Override
    @SuppressWarnings("try")
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        traceBuilder.apply(target, lirGenRes, context);
        livenessAnalysis.apply(target, lirGenRes, context);

        MoveFactory spillMoveFactory = context.spillMoveFactory;
        RegisterAllocationConfig registerAllocationConfig = context.registerAllocationConfig;
        LIR lir = lirGenRes.getLIR();
        DebugContext debug = lir.getDebug();
        TraceBuilderResult resultTraces = context.contextLookup(TraceBuilderResult.class);
        GlobalLivenessInfo livenessInfo = context.contextLookup(GlobalLivenessInfo.class);
        assert livenessInfo != null;
        TraceAllocationContext traceContext = new TraceAllocationContext(spillMoveFactory, registerAllocationConfig, resultTraces, livenessInfo);
        AllocatableValue[] cachedStackSlots = Options.TraceRACacheStackSlots.getValue(lir.getOptions()) ? new AllocatableValue[lir.numVariables()] : null;

        boolean neverSpillConstant = getNeverSpillConstants();
        assert !neverSpillConstant : "currently this is not supported";

        final TraceRegisterAllocationPolicy plan = DefaultTraceRegisterAllocationPolicy.allocationPolicy(target, lirGenRes, spillMoveFactory, registerAllocationConfig, cachedStackSlots, resultTraces,
                        neverSpillConstant, livenessInfo, lir.getOptions());

        try (DebugContext.Scope s0 = debug.scope("AllocateTraces", resultTraces, livenessInfo)) {
            for (Trace trace : resultTraces.getTraces()) {
                tracesCounter.increment(debug);
                TraceAllocationPhase<TraceAllocationContext> allocator = plan.selectStrategy(trace);
                try (Indent i = debug.logAndIndent("Allocating Trace%d: %s (%s)", trace.getId(), trace, allocator); DebugContext.Scope s = debug.scope("AllocateTrace", trace)) {
                    allocator.apply(target, lirGenRes, trace, traceContext);
                }
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        TraceGlobalMoveResolutionPhase.resolve(target, lirGenRes, traceContext);
        deconstructSSAForm(lir);
    }

    /**
     * Remove Phi In/Out.
     */
    private static void deconstructSSAForm(LIR lir) {
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            if (SSAUtil.isMerge(block)) {
                SSAUtil.phiIn(lir, block).clearIncomingValues();
                for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                    SSAUtil.phiOut(lir, pred).clearOutgoingValues();
                }
            }
        }
    }

}
