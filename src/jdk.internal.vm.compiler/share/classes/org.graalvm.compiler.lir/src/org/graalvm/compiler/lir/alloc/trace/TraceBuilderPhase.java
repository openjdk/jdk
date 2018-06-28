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

import static org.graalvm.compiler.lir.alloc.trace.TraceUtil.isTrivialTrace;

import java.util.ArrayList;

import org.graalvm.compiler.core.common.alloc.BiDirectionalTraceBuilder;
import org.graalvm.compiler.core.common.alloc.SingleBlockTraceBuilder;
import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult.TrivialTracePredicate;
import org.graalvm.compiler.core.common.alloc.TraceStatisticsPrinter;
import org.graalvm.compiler.core.common.alloc.UniDirectionalTraceBuilder;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.TargetDescription;

public class TraceBuilderPhase extends AllocationPhase {

    public enum TraceBuilder {
        UniDirectional,
        BiDirectional,
        SingleBlock
    }

    public static class Options {
        // @formatter:off
        @Option(help = "Trace building algorithm.", type = OptionType.Debug)
        public static final EnumOptionKey<TraceBuilder> TraceBuilding = new EnumOptionKey<>(TraceBuilder.UniDirectional);
        @Option(help = "Schedule trivial traces as early as possible.", type = OptionType.Debug)
        public static final OptionKey<Boolean> TraceRAScheduleTrivialTracesEarly = new OptionKey<>(true);
        // @formatter:on
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        AbstractBlockBase<?>[] linearScanOrder = lirGenRes.getLIR().linearScanOrder();
        AbstractBlockBase<?> startBlock = linearScanOrder[0];
        LIR lir = lirGenRes.getLIR();
        assert startBlock.equals(lir.getControlFlowGraph().getStartBlock());

        final TraceBuilderResult traceBuilderResult = getTraceBuilderResult(lir, startBlock, linearScanOrder);

        DebugContext debug = lir.getDebug();
        if (debug.isLogEnabled(DebugContext.BASIC_LEVEL)) {
            ArrayList<Trace> traces = traceBuilderResult.getTraces();
            for (int i = 0; i < traces.size(); i++) {
                Trace trace = traces.get(i);
                debug.log(DebugContext.BASIC_LEVEL, "Trace %5d: %s%s", i, trace, isTrivialTrace(lirGenRes.getLIR(), trace) ? " (trivial)" : "");
            }
        }
        TraceStatisticsPrinter.printTraceStatistics(debug, traceBuilderResult, lirGenRes.getCompilationUnitName());
        debug.dump(DebugContext.VERBOSE_LEVEL, traceBuilderResult, "TraceBuilderResult");
        context.contextAdd(traceBuilderResult);
    }

    private static TraceBuilderResult getTraceBuilderResult(LIR lir, AbstractBlockBase<?> startBlock, AbstractBlockBase<?>[] linearScanOrder) {
        TraceBuilderResult.TrivialTracePredicate pred = getTrivialTracePredicate(lir);

        OptionValues options = lir.getOptions();
        TraceBuilder selectedTraceBuilder = Options.TraceBuilding.getValue(options);
        DebugContext debug = lir.getDebug();
        debug.log(DebugContext.BASIC_LEVEL, "Building Traces using %s", selectedTraceBuilder);
        switch (Options.TraceBuilding.getValue(options)) {
            case SingleBlock:
                return SingleBlockTraceBuilder.computeTraces(debug, startBlock, linearScanOrder, pred);
            case BiDirectional:
                return BiDirectionalTraceBuilder.computeTraces(debug, startBlock, linearScanOrder, pred);
            case UniDirectional:
                return UniDirectionalTraceBuilder.computeTraces(debug, startBlock, linearScanOrder, pred);
        }
        throw GraalError.shouldNotReachHere("Unknown trace building algorithm: " + Options.TraceBuilding.getValue(options));
    }

    public static TraceBuilderResult.TrivialTracePredicate getTrivialTracePredicate(LIR lir) {
        if (!Options.TraceRAScheduleTrivialTracesEarly.getValue(lir.getOptions())) {
            return null;
        }
        return new TrivialTracePredicate() {
            @Override
            public boolean isTrivialTrace(Trace trace) {
                return TraceUtil.isTrivialTrace(lir, trace);
            }
        };
    }
}
