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
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase.TraceAllocationContext;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.lir.phases.AllocationPhase;
import org.graalvm.compiler.lir.ssi.SSIUtil;
import org.graalvm.compiler.lir.ssi.SSIVerifier;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.StableOptionValue;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Implements the Trace Register Allocation approach as described in
 * <a href="http://dx.doi.org/10.1145/2972206.2972211">"Trace-based Register Allocation in a JIT
 * Compiler"</a> by Josef Eisl et al.
 */
public final class TraceRegisterAllocationPhase extends AllocationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Use inter-trace register hints.", type = OptionType.Debug)
        public static final StableOptionValue<Boolean> TraceRAuseInterTraceHints = new StableOptionValue<>(true);
        @Option(help = "Share information about spilled values to other traces.", type = OptionType.Debug)
        public static final StableOptionValue<Boolean> TraceRAshareSpillInformation = new StableOptionValue<>(true);
        @Option(help = "Reuse spill slots for global move resolution cycle breaking.", type = OptionType.Debug)
        public static final StableOptionValue<Boolean> TraceRAreuseStackSlotsForMoveResolutionCycleBreaking = new StableOptionValue<>(true);
        @Option(help = "Cache stack slots globally (i.e. a variable always gets the same slot in every trace).", type = OptionType.Debug)
        public static final StableOptionValue<Boolean> TraceRACacheStackSlots = new StableOptionValue<>(true);
        // @formatter:on
    }

    private static final TraceGlobalMoveResolutionPhase TRACE_GLOBAL_MOVE_RESOLUTION_PHASE = new TraceGlobalMoveResolutionPhase();

    private static final DebugCounter tracesCounter = Debug.counter("TraceRA[traces]");

    public static final DebugCounter globalStackSlots = Debug.counter("TraceRA[GlobalStackSlots]");
    public static final DebugCounter allocatedStackSlots = Debug.counter("TraceRA[AllocatedStackSlots]");

    @Override
    @SuppressWarnings("try")
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        MoveFactory spillMoveFactory = context.spillMoveFactory;
        RegisterAllocationConfig registerAllocationConfig = context.registerAllocationConfig;
        LIR lir = lirGenRes.getLIR();
        assert SSIVerifier.verify(lir) : "LIR not in SSI form.";
        TraceBuilderResult resultTraces = context.contextLookup(TraceBuilderResult.class);

        TraceAllocationContext traceContext = new TraceAllocationContext(spillMoveFactory, registerAllocationConfig, resultTraces);
        AllocatableValue[] cachedStackSlots = Options.TraceRACacheStackSlots.getValue() ? new AllocatableValue[lir.numVariables()] : null;

        // currently this is not supported
        boolean neverSpillConstant = false;

        final TraceRegisterAllocationPolicy plan = DefaultTraceRegisterAllocationPolicy.allocationPolicy(target, lirGenRes, spillMoveFactory, registerAllocationConfig, cachedStackSlots,
                        resultTraces, neverSpillConstant);

        Debug.dump(Debug.INFO_LOG_LEVEL, lir, "Before TraceRegisterAllocation");
        try (Scope s0 = Debug.scope("AllocateTraces", resultTraces)) {
            for (Trace trace : resultTraces.getTraces()) {
                tracesCounter.increment();
                TraceAllocationPhase<TraceAllocationContext> allocator = plan.selectStrategy(trace);
                try (Indent i = Debug.logAndIndent("Allocating Trace%d: %s (%s)", trace.getId(), trace, allocator); Scope s = Debug.scope("AllocateTrace", trace)) {
                    allocator.apply(target, lirGenRes, trace, traceContext);
                }
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        if (Debug.isDumpEnabled(Debug.INFO_LOG_LEVEL)) {
            unnumberInstructions(lir);
            Debug.dump(Debug.INFO_LOG_LEVEL, lir, "After trace allocation");
        }

        TRACE_GLOBAL_MOVE_RESOLUTION_PHASE.apply(target, lirGenRes, traceContext);
        deconstructSSIForm(lir);
    }

    /**
     * Remove Phi/Sigma In/Out.
     *
     * Note: Incoming Values are needed for the RegisterVerifier, otherwise SIGMAs/PHIs where the
     * Out and In value matches (ie. there is no resolution move) are falsely detected as errors.
     */
    @SuppressWarnings("try")
    private static void deconstructSSIForm(LIR lir) {
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            try (Indent i = Debug.logAndIndent("Fixup Block %s", block)) {
                if (block.getPredecessorCount() != 0) {
                    SSIUtil.removeIncoming(lir, block);
                } else {
                    assert lir.getControlFlowGraph().getStartBlock().equals(block);
                }
                SSIUtil.removeOutgoing(lir, block);
            }
        }
    }

    private static void unnumberInstructions(LIR lir) {
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            for (LIRInstruction op : lir.getLIRforBlock(block)) {
                op.setId(-1);
            }
        }
    }
}
