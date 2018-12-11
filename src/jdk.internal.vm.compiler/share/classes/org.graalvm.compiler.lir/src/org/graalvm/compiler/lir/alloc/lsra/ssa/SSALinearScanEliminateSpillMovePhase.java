/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.StandardOp.MoveOp;
import org.graalvm.compiler.lir.alloc.lsra.Interval;
import org.graalvm.compiler.lir.alloc.lsra.LinearScan;
import org.graalvm.compiler.lir.alloc.lsra.LinearScanEliminateSpillMovePhase;

public class SSALinearScanEliminateSpillMovePhase extends LinearScanEliminateSpillMovePhase {

    SSALinearScanEliminateSpillMovePhase(LinearScan allocator) {
        super(allocator);
    }

    @Override
    protected int firstInstructionOfInterest() {
        // also look at Labels as they define PHI values
        return 0;
    }

    @Override
    protected boolean canEliminateSpillMove(AbstractBlockBase<?> block, MoveOp move) {
        if (super.canEliminateSpillMove(block, move)) {
            // SSA Linear Scan might introduce moves to stack slots
            Interval curInterval = allocator.intervalFor(move.getResult());
            assert !isRegister(curInterval.location()) && curInterval.alwaysInMemory();
            if (!isPhiResolutionMove(block, move, curInterval)) {
                assert isStackSlotValue(curInterval.location()) : "Not a stack slot: " + curInterval.location();
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("try")
    private boolean isPhiResolutionMove(AbstractBlockBase<?> block, MoveOp move, Interval toInterval) {
        if (!toInterval.isSplitParent()) {
            return false;
        }
        if ((toInterval.from() & 1) == 1) {
            // phi intervals start at even positions.
            return false;
        }
        if (block.getSuccessorCount() != 1) {
            return false;
        }
        LIRInstruction op = allocator.instructionForId(toInterval.from());
        if (!(op instanceof LabelOp)) {
            return false;
        }
        AbstractBlockBase<?> intStartBlock = allocator.blockForId(toInterval.from());
        assert allocator.getLIR().getLIRforBlock(intStartBlock).get(0).equals(op);
        if (!block.getSuccessors()[0].equals(intStartBlock)) {
            return false;
        }
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.indent()) {
            debug.log("Is a move (%s) to phi interval %s", move, toInterval);
        }
        return true;
    }
}
