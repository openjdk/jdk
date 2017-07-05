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

import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.List;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.alloc.lsra.Interval;
import org.graalvm.compiler.lir.alloc.lsra.LinearScan;
import org.graalvm.compiler.lir.alloc.lsra.LinearScanResolveDataFlowPhase;
import org.graalvm.compiler.lir.alloc.lsra.MoveResolver;
import org.graalvm.compiler.lir.ssa.SSAUtil;
import org.graalvm.compiler.lir.ssa.SSAUtil.PhiValueVisitor;

import jdk.vm.ci.meta.Value;

class SSALinearScanResolveDataFlowPhase extends LinearScanResolveDataFlowPhase {

    private static final DebugCounter numPhiResolutionMoves = Debug.counter("SSA LSRA[numPhiResolutionMoves]");
    private static final DebugCounter numStackToStackMoves = Debug.counter("SSA LSRA[numStackToStackMoves]");

    SSALinearScanResolveDataFlowPhase(LinearScan allocator) {
        super(allocator);
    }

    @Override
    protected void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> midBlock, MoveResolver moveResolver) {
        super.resolveCollectMappings(fromBlock, toBlock, midBlock, moveResolver);

        if (toBlock.getPredecessorCount() > 1) {
            int toBlockFirstInstructionId = allocator.getFirstLirInstructionId(toBlock);
            int fromBlockLastInstructionId = allocator.getLastLirInstructionId(fromBlock) + 1;

            AbstractBlockBase<?> phiOutBlock = midBlock != null ? midBlock : fromBlock;
            List<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(phiOutBlock);
            int phiOutIdx = SSAUtil.phiOutIndex(allocator.getLIR(), phiOutBlock);
            int phiOutId = midBlock != null ? fromBlockLastInstructionId : instructions.get(phiOutIdx).id();
            assert phiOutId >= 0;

            PhiValueVisitor visitor = new PhiValueVisitor() {

                @Override
                public void visit(Value phiIn, Value phiOut) {
                    assert !isRegister(phiOut) : "phiOut is a register: " + phiOut;
                    assert !isRegister(phiIn) : "phiIn is a register: " + phiIn;
                    Interval toInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiIn), toBlockFirstInstructionId, LIRInstruction.OperandMode.DEF);
                    if (isConstantValue(phiOut)) {
                        numPhiResolutionMoves.increment();
                        moveResolver.addMapping(asConstant(phiOut), toInterval);
                    } else {
                        Interval fromInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiOut), phiOutId, LIRInstruction.OperandMode.DEF);
                        if (fromInterval != toInterval && !fromInterval.location().equals(toInterval.location())) {
                            numPhiResolutionMoves.increment();
                            if (!(isStackSlotValue(toInterval.location()) && isStackSlotValue(fromInterval.location()))) {
                                moveResolver.addMapping(fromInterval, toInterval);
                            } else {
                                numStackToStackMoves.increment();
                                moveResolver.addMapping(fromInterval, toInterval);
                            }
                        }
                    }
                }
            };

            SSAUtil.forEachPhiValuePair(allocator.getLIR(), toBlock, phiOutBlock, visitor);
            SSAUtil.removePhiOut(allocator.getLIR(), phiOutBlock);
        }
    }

}
