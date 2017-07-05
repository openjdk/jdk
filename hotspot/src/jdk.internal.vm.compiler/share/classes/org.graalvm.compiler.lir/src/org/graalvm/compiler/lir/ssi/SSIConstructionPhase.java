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
package org.graalvm.compiler.lir.ssi;

import java.util.BitSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.alloc.lsra.LinearScanLifetimeAnalysisPhase;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase;
import org.graalvm.compiler.lir.ssa.SSAUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.StableOptionValue;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;

/**
 * Constructs {@linkplain SSIUtil SSI LIR} using a liveness analysis.
 *
 * Implementation derived from {@link LinearScanLifetimeAnalysisPhase}.
 *
 * @see SSIUtil
 */
public final class SSIConstructionPhase extends AllocationPhase {

    static class Options {

        //@formatter:off
        @Option(help = "Use fast SSI builder.", type = OptionType.Debug)
        public static final StableOptionValue<Boolean> TraceRAFastSSIBuilder = new StableOptionValue<>(true);
        //@formatter:on
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        assert SSAUtil.verifySSAForm(lirGenRes.getLIR());
        if (Options.TraceRAFastSSIBuilder.getValue()) {
            FastSSIBuilder fastSSIBuilder = new FastSSIBuilder(lirGenRes.getLIR());
            fastSSIBuilder.build();
            fastSSIBuilder.finish();
        } else {
            SSIBuilder ssiBuilder = new SSIBuilder(lirGenRes.getLIR());
            ssiBuilder.build();
            ssiBuilder.finish();
        }
    }

    static void check(AbstractBlockBase<?>[] blocks, SSIBuilderBase liveSets1, SSIBuilderBase liveSets2) {
        for (AbstractBlockBase<?> block : blocks) {
            check(block, liveSets1.getLiveIn(block), liveSets2.getLiveIn(block));
            check(block, liveSets1.getLiveOut(block), liveSets2.getLiveOut(block));
        }
    }

    private static void check(AbstractBlockBase<?> block, BitSet liveIn1, BitSet liveIn2) {
        if (!liveIn1.equals(liveIn2)) {
            throw JVMCIError.shouldNotReachHere(String.format("%s LiveSet differ: %s vs %s", block, liveIn1, liveIn2));
        }
    }
}
