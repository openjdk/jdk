/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase.TraceAllocationContext;
import org.graalvm.compiler.lir.alloc.trace.TraceRegisterAllocationPolicy.AllocationStrategy;
import org.graalvm.compiler.lir.alloc.trace.bu.BottomUpAllocator;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceLinearScanPhase;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Manages the selection of allocation strategies.
 */
public final class DefaultTraceRegisterAllocationPolicy {

    public enum TraceRAPolicies {
        Default,
        LinearScanOnly,
        BottomUpOnly,
        AlmostTrivial,
        NumVariables,
        Ratio,
        Loops,
        MaxFreq,
        FreqBudget,
        LoopRatio,
        LoopBudget,
        LoopMaxFreq
    }

    public static class Options {
        // @formatter:off
        @Option(help = "Use special allocator for trivial blocks.", type = OptionType.Debug)
        public static final OptionKey<Boolean> TraceRAtrivialBlockAllocator = new OptionKey<>(true);
        @Option(help = "Use BottomUp if there is only one block with at most this number of instructions", type = OptionType.Debug)
        public static final OptionKey<Integer> TraceRAalmostTrivialSize = new OptionKey<>(2);
        @Option(help = "Use BottomUp for traces with low number of variables at block boundaries", type = OptionType.Debug)
        public static final OptionKey<Integer> TraceRAnumVariables = new OptionKey<>(null);
        @Option(help = "Use LSRA / BottomUp ratio", type = OptionType.Debug)
        public static final OptionKey<Double> TraceRAbottomUpRatio = new OptionKey<>(0.0);
        @Option(help = "Probability Threshold", type = OptionType.Debug)
        public static final OptionKey<Double> TraceRAprobalilityThreshold = new OptionKey<>(0.8);
        @Option(help = "Sum Probability Budget Threshold", type = OptionType.Debug)
        public static final OptionKey<Double> TraceRAsumBudget = new OptionKey<>(0.5);
        @Option(help = "TraceRA allocation policy to use.", type = OptionType.Debug)
        public static final EnumOptionKey<TraceRAPolicies> TraceRAPolicy = new EnumOptionKey<>(TraceRAPolicies.Default);
        // @formatter:on
    }

    public static final class TrivialTraceStrategy extends AllocationStrategy {

        public TrivialTraceStrategy(TraceRegisterAllocationPolicy plan) {
            plan.super();
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            return TraceUtil.isTrivialTrace(getLIR(), trace);
        }

        @Override
        protected TraceAllocationPhase<TraceAllocationContext> initAllocator(TargetDescription target, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory,
                        RegisterAllocationConfig registerAllocationConfig, AllocatableValue[] cachedStackSlots, TraceBuilderResult resultTraces, boolean neverSpillConstant,
                        GlobalLivenessInfo livenessInfo, ArrayList<AllocationStrategy> strategies) {
            return new TrivialTraceAllocator();
        }
    }

    public static class BottomUpStrategy extends AllocationStrategy {

        public BottomUpStrategy(TraceRegisterAllocationPolicy plan) {
            // explicitly specify the enclosing instance for the superclass constructor call
            plan.super();
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            return !containsExceptionEdge(trace);
        }

        private static boolean containsExceptionEdge(Trace trace) {
            for (AbstractBlockBase<?> block : trace.getBlocks()) {
                // check if one of the successors is an exception handler
                for (AbstractBlockBase<?> succ : block.getSuccessors()) {
                    if (succ.isExceptionEntry()) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        protected TraceAllocationPhase<TraceAllocationContext> initAllocator(TargetDescription target, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory,
                        RegisterAllocationConfig registerAllocationConfig, AllocatableValue[] cachedStackSlots, TraceBuilderResult resultTraces, boolean neverSpillConstant,
                        GlobalLivenessInfo livenessInfo, ArrayList<AllocationStrategy> strategies) {
            return new BottomUpAllocator(target, lirGenRes, spillMoveFactory, registerAllocationConfig, cachedStackSlots, resultTraces, neverSpillConstant, livenessInfo);
        }
    }

    public static final class BottomUpAlmostTrivialStrategy extends BottomUpStrategy {

        private final int trivialTraceSize;

        public BottomUpAlmostTrivialStrategy(TraceRegisterAllocationPolicy plan) {
            // explicitly specify the enclosing instance for the superclass constructor call
            super(plan);
            trivialTraceSize = Options.TraceRAalmostTrivialSize.getValue(plan.getOptions());
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            if (!super.shouldApplyTo(trace)) {
                return false;
            }
            if (trace.size() != 1) {
                return false;
            }
            return getLIR().getLIRforBlock(trace.getBlocks()[0]).size() <= trivialTraceSize;
        }

    }

    public static final class BottomUpNumVariablesStrategy extends BottomUpStrategy {

        private final int numVarLimit;

        public BottomUpNumVariablesStrategy(TraceRegisterAllocationPolicy plan) {
            // explicitly specify the enclosing instance for the superclass constructor call
            super(plan);
            Integer value = Options.TraceRAnumVariables.getValue(plan.getOptions());
            if (value != null) {
                numVarLimit = value;
            } else {
                /* Default to the number of allocatable word registers. */
                PlatformKind wordKind = getTarget().arch.getWordKind();
                int numWordRegisters = getRegisterAllocationConfig().getAllocatableRegisters(wordKind).allocatableRegisters.length;
                numVarLimit = numWordRegisters;
            }
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            if (!super.shouldApplyTo(trace)) {
                return false;
            }
            GlobalLivenessInfo livenessInfo = getGlobalLivenessInfo();
            int maxNumVars = livenessInfo.getBlockIn(trace.getBlocks()[0]).length;
            for (AbstractBlockBase<?> block : trace.getBlocks()) {
                maxNumVars = Math.max(maxNumVars, livenessInfo.getBlockOut(block).length);
            }
            return maxNumVars <= numVarLimit;
        }

    }

    public static final class BottomUpRatioStrategy extends BottomUpStrategy {

        private final double ratio;

        public BottomUpRatioStrategy(TraceRegisterAllocationPolicy plan) {
            // explicitly specify the enclosing instance for the superclass constructor call
            super(plan);
            ratio = Options.TraceRAbottomUpRatio.getValue(plan.getOptions());
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            if (!super.shouldApplyTo(trace)) {
                return false;
            }
            double numTraces = getTraceBuilderResult().getTraces().size();
            double traceId = trace.getId();
            assert ratio >= 0 && ratio <= 1.0 : "Ratio out of range: " + ratio;
            return (traceId / numTraces) >= ratio;
        }

    }

    public abstract static class BottomUpLoopStrategyBase extends BottomUpStrategy {

        public BottomUpLoopStrategyBase(TraceRegisterAllocationPolicy plan) {
            // explicitly specify the enclosing instance for the superclass constructor call
            super(plan);
        }

        @Override
        public final boolean shouldApplyTo(Trace trace) {
            if (!super.shouldApplyTo(trace)) {
                return false;
            }
            if (getLIR().getControlFlowGraph().getLoops().isEmpty()) {
                return shouldApplyToNoLoop(trace);
            }
            for (AbstractBlockBase<?> block : trace.getBlocks()) {
                if (block.getLoopDepth() > 0) {
                    return false;
                }
            }
            return true;
        }

        protected abstract boolean shouldApplyToNoLoop(Trace trace);

    }

    public static final class BottomUpLoopStrategy extends BottomUpLoopStrategyBase {

        public BottomUpLoopStrategy(TraceRegisterAllocationPolicy plan) {
            // explicitly specify the enclosing instance for the superclass constructor call
            super(plan);
        }

        @Override
        protected boolean shouldApplyToNoLoop(Trace trace) {
            // no loops at all -> use LSRA
            return false;
        }

    }

    public static final class BottomUpDelegatingLoopStrategy extends BottomUpLoopStrategyBase {

        private final BottomUpStrategy delegate;

        public BottomUpDelegatingLoopStrategy(TraceRegisterAllocationPolicy plan, BottomUpStrategy delegate) {
            // explicitly specify the enclosing instance for the superclass constructor call
            super(plan);
            this.delegate = delegate;
        }

        @Override
        protected boolean shouldApplyToNoLoop(Trace trace) {
            return delegate.shouldApplyTo(trace);
        }

    }

    public static final class BottomUpMaxFrequencyStrategy extends BottomUpStrategy {

        private final double maxMethodProbability;
        private final double probabilityThreshold;

        public BottomUpMaxFrequencyStrategy(TraceRegisterAllocationPolicy plan) {
            // explicitly specify the enclosing instance for the superclass constructor call
            super(plan);
            maxMethodProbability = maxProbability(getLIR().getControlFlowGraph().getBlocks());
            probabilityThreshold = Options.TraceRAprobalilityThreshold.getValue(plan.getOptions());
        }

        private static double maxProbability(AbstractBlockBase<?>[] blocks) {
            double max = 0;
            for (AbstractBlockBase<?> block : blocks) {
                double probability = block.probability();
                if (probability > max) {
                    max = probability;
                }
            }
            return max;
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            if (!super.shouldApplyTo(trace)) {
                return false;
            }
            return maxProbability(trace.getBlocks()) / maxMethodProbability <= probabilityThreshold;
        }

    }

    public static final class BottomUpFrequencyBudgetStrategy extends BottomUpStrategy {

        private final double[] cumulativeTraceProbability;
        private final double budget;

        public BottomUpFrequencyBudgetStrategy(TraceRegisterAllocationPolicy plan) {
            // explicitly specify the enclosing instance for the superclass constructor call
            super(plan);
            ArrayList<Trace> traces = getTraceBuilderResult().getTraces();
            this.cumulativeTraceProbability = new double[traces.size()];
            double sumMethodProbability = init(traces, this.cumulativeTraceProbability);
            this.budget = sumMethodProbability * Options.TraceRAsumBudget.getValue(plan.getOptions());
        }

        private static double init(ArrayList<Trace> traces, double[] sumTraces) {
            double sumMethod = 0;
            for (Trace trace : traces) {
                double traceSum = 0;
                for (AbstractBlockBase<?> block : trace.getBlocks()) {
                    traceSum += block.probability();
                }
                sumMethod += traceSum;
                // store cumulative sum for trace
                sumTraces[trace.getId()] = sumMethod;
            }
            return sumMethod;
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            if (!super.shouldApplyTo(trace)) {
                return false;
            }
            double cumTraceProb = cumulativeTraceProbability[trace.getId()];
            return cumTraceProb > budget;
        }

    }

    public static final class TraceLinearScanStrategy extends AllocationStrategy {

        public TraceLinearScanStrategy(TraceRegisterAllocationPolicy plan) {
            // explicitly specify the enclosing instance for the superclass constructor call
            plan.super();
        }

        @Override
        public boolean shouldApplyTo(Trace trace) {
            return true;
        }

        @Override
        protected TraceAllocationPhase<TraceAllocationContext> initAllocator(TargetDescription target, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory,
                        RegisterAllocationConfig registerAllocationConfig, AllocatableValue[] cachedStackSlots, TraceBuilderResult resultTraces, boolean neverSpillConstant,
                        GlobalLivenessInfo livenessInfo, ArrayList<AllocationStrategy> strategies) {
            return new TraceLinearScanPhase(target, lirGenRes, spillMoveFactory, registerAllocationConfig, resultTraces, neverSpillConstant, cachedStackSlots, livenessInfo);
        }

    }

    public static TraceRegisterAllocationPolicy allocationPolicy(TargetDescription target, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig, AllocatableValue[] cachedStackSlots, TraceBuilderResult resultTraces, boolean neverSpillConstant,
                    GlobalLivenessInfo livenessInfo, OptionValues options) {
        TraceRegisterAllocationPolicy plan = new TraceRegisterAllocationPolicy(target, lirGenRes, spillMoveFactory, registerAllocationConfig, cachedStackSlots, resultTraces, neverSpillConstant,
                        livenessInfo);
        if (Options.TraceRAtrivialBlockAllocator.getValue(options)) {
            plan.appendStrategy(new TrivialTraceStrategy(plan));
        }
        switch (Options.TraceRAPolicy.getValue(options)) {
            case Default:
            case LinearScanOnly:
                break;
            case BottomUpOnly:
                plan.appendStrategy(new BottomUpStrategy(plan));
                break;
            case AlmostTrivial:
                plan.appendStrategy(new BottomUpAlmostTrivialStrategy(plan));
                break;
            case NumVariables:
                plan.appendStrategy(new BottomUpNumVariablesStrategy(plan));
                break;
            case Ratio:
                plan.appendStrategy(new BottomUpRatioStrategy(plan));
                break;
            case Loops:
                plan.appendStrategy(new BottomUpLoopStrategy(plan));
                break;
            case MaxFreq:
                plan.appendStrategy(new BottomUpMaxFrequencyStrategy(plan));
                break;
            case FreqBudget:
                plan.appendStrategy(new BottomUpFrequencyBudgetStrategy(plan));
                break;
            case LoopRatio:
                plan.appendStrategy(new BottomUpDelegatingLoopStrategy(plan, new BottomUpRatioStrategy(plan)));
                break;
            case LoopMaxFreq:
                plan.appendStrategy(new BottomUpDelegatingLoopStrategy(plan, new BottomUpMaxFrequencyStrategy(plan)));
                break;
            case LoopBudget:
                plan.appendStrategy(new BottomUpDelegatingLoopStrategy(plan, new BottomUpFrequencyBudgetStrategy(plan)));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        // Fallback
        plan.appendStrategy(new TraceLinearScanStrategy(plan));
        return plan;
    }
}
