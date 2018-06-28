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


package org.graalvm.compiler.lir.alloc.lsra.ssa;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.alloc.lsra.LinearScan;
import org.graalvm.compiler.lir.alloc.lsra.LinearScanEliminateSpillMovePhase;
import org.graalvm.compiler.lir.alloc.lsra.LinearScanLifetimeAnalysisPhase;
import org.graalvm.compiler.lir.alloc.lsra.LinearScanResolveDataFlowPhase;
import org.graalvm.compiler.lir.alloc.lsra.MoveResolver;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.lir.ssa.SSAUtil;

import jdk.vm.ci.code.TargetDescription;

public final class SSALinearScan extends LinearScan {

    public SSALinearScan(TargetDescription target, LIRGenerationResult res, MoveFactory spillMoveFactory, RegisterAllocationConfig regAllocConfig, AbstractBlockBase<?>[] sortedBlocks,
                    boolean neverSpillConstants) {
        super(target, res, spillMoveFactory, regAllocConfig, sortedBlocks, neverSpillConstants);
    }

    @Override
    protected MoveResolver createMoveResolver() {
        SSAMoveResolver moveResolver = new SSAMoveResolver(this);
        assert moveResolver.checkEmpty();
        return moveResolver;
    }

    @Override
    protected LinearScanLifetimeAnalysisPhase createLifetimeAnalysisPhase() {
        return new SSALinearScanLifetimeAnalysisPhase(this);
    }

    @Override
    protected LinearScanResolveDataFlowPhase createResolveDataFlowPhase() {
        return new SSALinearScanResolveDataFlowPhase(this);
    }

    @Override
    protected LinearScanEliminateSpillMovePhase createSpillMoveEliminationPhase() {
        return new SSALinearScanEliminateSpillMovePhase(this);
    }

    @Override
    @SuppressWarnings("try")
    protected void beforeSpillMoveElimination() {
        /*
         * PHI Ins are needed for the RegisterVerifier, otherwise PHIs where the Out and In value
         * matches (ie. there is no resolution move) are falsely detected as errors.
         */
        try (DebugContext.Scope s1 = debug.scope("Remove Phi In")) {
            for (AbstractBlockBase<?> toBlock : sortedBlocks()) {
                if (toBlock.getPredecessorCount() > 1) {
                    SSAUtil.removePhiIn(getLIR(), toBlock);
                }
            }
        }
    }

}
