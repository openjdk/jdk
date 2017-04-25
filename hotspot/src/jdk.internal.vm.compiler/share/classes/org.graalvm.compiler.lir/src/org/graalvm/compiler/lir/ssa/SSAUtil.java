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
package org.graalvm.compiler.lir.ssa;

import java.util.ArrayList;
import java.util.Arrays;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.JumpOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.ValueConsumer;

import jdk.vm.ci.meta.Value;

/**
 * Utilities for working with Static-Single-Assignment LIR form.
 *
 * <h2>Representation of <code>PHI</code>s</h2>
 *
 * There is no explicit <code>PHI</code> {@linkplain LIRInstruction}. Instead, they are implemented
 * as parallel copy that span across a control-flow edge.
 *
 * The variables introduced by <code>PHI</code>s of a specific {@linkplain AbstractBlockBase merge
 * block} are {@linkplain LabelOp#setIncomingValues attached} to the {@linkplain LabelOp} of the
 * block. The outgoing values from the predecessor are {@link JumpOp#getOutgoingValue input} to the
 * {@linkplain BlockEndOp} of the predecessor. Because there are no critical edges we know that the
 * {@link BlockEndOp} of the predecessor has to be a {@link JumpOp}.
 *
 * <h3>Example:</h3>
 *
 * <pre>
 * B0 -> B1
 *   ...
 *   v0|i = ...
 *   JUMP ~[v0|i, int[0|0x0]] destination: B0 -> B1
 * ________________________________________________
 *
 * B2 -> B1
 *   ...
 *   v1|i = ...
 *   v2|i = ...
 *   JUMP ~[v1|i, v2|i] destination: B2 -> B1
 * ________________________________________________
 *
 * B1 <- B0,B2
 *   [v3|i, v4|i] = LABEL
 *   ...
 * </pre>
 */
public final class SSAUtil {

    public interface PhiValueVisitor {
        /**
         * @param phiIn the incoming value at the merge block
         * @param phiOut the outgoing value from the predecessor block
         */
        void visit(Value phiIn, Value phiOut);
    }

    /**
     * Visits each phi value pair of an edge, i.e. the outgoing value from the predecessor and the
     * incoming value to the merge block.
     */
    public static void forEachPhiValuePair(LIR lir, AbstractBlockBase<?> merge, AbstractBlockBase<?> pred, PhiValueVisitor visitor) {
        if (merge.getPredecessorCount() < 2) {
            return;
        }
        assert Arrays.asList(merge.getPredecessors()).contains(pred) : String.format("%s not in predecessor list: %s", pred, Arrays.toString(merge.getPredecessors()));
        assert pred.getSuccessorCount() == 1 : String.format("Merge predecessor block %s has more than one successor? %s", pred, Arrays.toString(pred.getSuccessors()));
        assert pred.getSuccessors()[0] == merge : String.format("Predecessor block %s has wrong successor: %s, should be: %s", pred, pred.getSuccessors()[0], merge);

        JumpOp jump = phiOut(lir, pred);
        LabelOp label = phiIn(lir, merge);

        assert label.getPhiSize() == jump.getPhiSize() : String.format("Phi In/Out size mismatch: in=%d vs. out=%d", label.getPhiSize(), jump.getPhiSize());

        for (int i = 0; i < label.getPhiSize(); i++) {
            visitor.visit(label.getIncomingValue(i), jump.getOutgoingValue(i));
        }
    }

    public static JumpOp phiOut(LIR lir, AbstractBlockBase<?> block) {
        assert block.getSuccessorCount() == 1;
        ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
        int index = instructions.size() - 1;
        LIRInstruction op = instructions.get(index);
        return (JumpOp) op;
    }

    public static int phiOutIndex(LIR lir, AbstractBlockBase<?> block) {
        assert block.getSuccessorCount() == 1;
        ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
        int index = instructions.size() - 1;
        assert instructions.get(index) instanceof JumpOp;
        return index;
    }

    public static LabelOp phiIn(LIR lir, AbstractBlockBase<?> block) {
        assert block.getPredecessorCount() > 1;
        LabelOp label = (LabelOp) lir.getLIRforBlock(block).get(0);
        return label;
    }

    public static void removePhiOut(LIR lir, AbstractBlockBase<?> block) {
        JumpOp jump = phiOut(lir, block);
        jump.clearOutgoingValues();
    }

    public static void removePhiIn(LIR lir, AbstractBlockBase<?> block) {
        LabelOp label = phiIn(lir, block);
        label.clearIncomingValues();
    }

    public static boolean verifySSAForm(LIR lir) {
        return new SSAVerifier(lir).verify();
    }

    public static boolean isMerge(AbstractBlockBase<?> block) {
        return block.getPredecessorCount() > 1;
    }

    public static void verifyPhi(LIR lir, AbstractBlockBase<?> merge) {
        assert merge.getPredecessorCount() > 1;
        for (AbstractBlockBase<?> pred : merge.getPredecessors()) {
            forEachPhiValuePair(lir, merge, pred, (phiIn, phiOut) -> {
                assert phiIn.getValueKind().equals(phiOut.getValueKind()) ||
                                (phiIn.getPlatformKind().equals(phiOut.getPlatformKind()) && LIRKind.isUnknownReference(phiIn) && LIRKind.isValue(phiOut));
            });
        }
    }

    public static void forEachPhiRegisterHint(LIR lir, AbstractBlockBase<?> block, LabelOp label, Value targetValue, OperandMode mode, ValueConsumer valueConsumer) {
        assert mode == OperandMode.DEF : "Wrong operand mode: " + mode;
        assert lir.getLIRforBlock(block).get(0).equals(label) : String.format("Block %s and Label %s do not match!", block, label);

        if (!label.isPhiIn()) {
            return;
        }
        int idx = indexOfValue(label, targetValue);
        assert idx >= 0 : String.format("Value %s not in label %s", targetValue, label);

        for (AbstractBlockBase<?> pred : block.getPredecessors()) {
            JumpOp jump = phiOut(lir, pred);
            Value sourceValue = jump.getOutgoingValue(idx);
            valueConsumer.visitValue(jump, sourceValue, null, null);
        }

    }

    private static int indexOfValue(LabelOp label, Value value) {
        for (int i = 0; i < label.getIncomingSize(); i++) {
            if (label.getIncomingValue(i).equals(value)) {
                return i;
            }
        }
        return -1;
    }

}
