/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.BitSet;
import java.util.EnumSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.alloc.lsra.Interval;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterPriority;
import org.graalvm.compiler.lir.alloc.lsra.LinearScan;
import org.graalvm.compiler.lir.alloc.lsra.LinearScanLifetimeAnalysisPhase;
import org.graalvm.compiler.lir.ssa.SSAUtil;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public class SSALinearScanLifetimeAnalysisPhase extends LinearScanLifetimeAnalysisPhase {

    SSALinearScanLifetimeAnalysisPhase(LinearScan linearScan) {
        super(linearScan);
    }

    @Override
    protected void addRegisterHint(final LIRInstruction op, final Value targetValue, OperandMode mode, EnumSet<OperandFlag> flags, final boolean hintAtDef) {
        super.addRegisterHint(op, targetValue, mode, flags, hintAtDef);

        if (hintAtDef && op instanceof LabelOp) {
            LabelOp label = (LabelOp) op;
            if (!label.isPhiIn()) {
                return;
            }

            Interval to = allocator.getOrCreateInterval((AllocatableValue) targetValue);

            LIR lir = allocator.getLIR();
            AbstractBlockBase<?> block = allocator.blockForId(label.id());
            assert mode == OperandMode.DEF : "Wrong operand mode: " + mode;
            assert lir.getLIRforBlock(block).get(0).equals(label) : String.format("Block %s and Label %s do not match!", block, label);

            int idx = SSAUtil.indexOfValue(label, targetValue);
            assert idx >= 0 : String.format("Value %s not in label %s", targetValue, label);

            BitSet blockLiveIn = allocator.getBlockData(block).liveIn;

            AbstractBlockBase<?> selectedPredecessor = null;
            AllocatableValue selectedSource = null;
            for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                if (selectedPredecessor == null || pred.getRelativeFrequency() > selectedPredecessor.getRelativeFrequency()) {
                    StandardOp.JumpOp jump = SSAUtil.phiOut(lir, pred);
                    Value sourceValue = jump.getOutgoingValue(idx);
                    if (LinearScan.isVariableOrRegister(sourceValue) && !blockLiveIn.get(getOperandNumber(sourceValue))) {
                        selectedSource = (AllocatableValue) sourceValue;
                        selectedPredecessor = pred;
                    }
                }
            }
            if (selectedSource != null) {
                Interval from = allocator.getOrCreateInterval(selectedSource);
                setHint(debug, op, to, from);
                setHint(debug, op, from, to);
            }
        }
    }

    public static void setHint(DebugContext debug, final LIRInstruction op, Interval target, Interval source) {
        Interval currentHint = target.locationHint(false);
        if (currentHint == null || currentHint.from() > target.from()) {
            /*
             * Update hint if there was none or if the hint interval starts after the hinted
             * interval.
             */
            target.setLocationHint(source);
            if (debug.isLogEnabled()) {
                debug.log("operation at opId %d: added hint from interval %d to %d", op.id(), source.operandNumber, target.operandNumber);
            }
        }
    }

    @Override
    protected RegisterPriority registerPriorityOfOutputOperand(LIRInstruction op) {
        if (op instanceof LabelOp) {
            LabelOp label = (LabelOp) op;
            if (label.isPhiIn()) {
                return RegisterPriority.None;
            }
        }
        return super.registerPriorityOfOutputOperand(op);
    }
}
