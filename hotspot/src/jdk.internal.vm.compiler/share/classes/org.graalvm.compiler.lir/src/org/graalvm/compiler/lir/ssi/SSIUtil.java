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
package org.graalvm.compiler.lir.ssi;

import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.ssa.SSAUtil.PhiValueVisitor;

import jdk.vm.ci.meta.Value;

/**
 * Utilities for working with Static-Single-Information LIR form.
 *
 * <h2>Representation of &phi; and &sigma;</h2>
 *
 * There are no explicit &phi;/&sigma; {@link LIRInstruction}s. Instead, they are implemented as
 * parallel copy that spans across a control-flow edge.
 *
 * The variables introduced by &phi;/&sigma; of a specific {@linkplain AbstractBlockBase block} are
 * {@linkplain LabelOp#setIncomingValues attached} to the {@link LabelOp} of the block. The outgoing
 * values from the predecessor are {@linkplain BlockEndOp#setOutgoingValues input} to the
 * {@link BlockEndOp} of the predecessor.
 *
 * When it does not matter whether we are talking about a &phi; or a &sigma; we call the values that
 * are defined by a label {@linkplain LabelOp#setIncomingValues incoming} and the values that are
 * input to the {@link BlockEndOp} of the predecessor {@linkplain BlockEndOp#setOutgoingValues
 * outgoing}.
 *
 * <h2>Implementation Details</h2>
 *
 * For our purposes we want a <em>maximal</em> SSI form, which means that all values that are alive
 * across basic block boundaries are gated with a &phi;/&sigma;. In other words the outgoing and
 * incoming values of the {@link BlockEndOp} and {@link LabelOp} are equivalent to the live-out and
 * live-in set of the corresponding block.
 *
 * As a side effect variables are local to a block. We reuse the name of the predecessor if they
 * represent the same value (i.e. not a real &phi; definition).
 *
 * <h2>Examples</h2>
 *
 * <h3>Merge (&phi;)</h3>
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
 *
 * Note: the outgoing values of a block can contain constants (see <code>B0</code>).
 *
 * <h3>Split (&sigma;)</h3>
 *
 * <pre>
 * B0 -> B1,B2
 *   ...
 *   v0|i = ...
 *   v1|i = ...
 *   v2|i = ...
 *   TEST (x: v1|i, y: v1|i)
 *   BRANCH ~[v2|i, v0|j] condition: <, true: B1 false: B2
 * ________________________________________________
 *
 * B1 <- B0
 *   [-, v0|j] = LABEL
 *   ...
 * ________________________________________________
 *
 * B2 <- B0
 *   [v2|i, v0|j] = LABEL
 *   ...
 * </pre>
 *
 * Note: If a incoming value is not needed in a branch it is {@link Value#ILLEGAL ignored} (see
 * <code>B1<code>).
 */
public final class SSIUtil {

    public static BlockEndOp outgoing(LIR lir, AbstractBlockBase<?> block) {
        return (BlockEndOp) outgoingInst(lir, block);
    }

    public static LIRInstruction outgoingInst(LIR lir, AbstractBlockBase<?> block) {
        List<LIRInstruction> instructions = lir.getLIRforBlock(block);
        int index = instructions.size() - 1;
        LIRInstruction op = instructions.get(index);
        return op;
    }

    public static LabelOp incoming(LIR lir, AbstractBlockBase<?> block) {
        return (LabelOp) incomingInst(lir, block);
    }

    private static LIRInstruction incomingInst(LIR lir, AbstractBlockBase<?> block) {
        return lir.getLIRforBlock(block).get(0);
    }

    public static void removeIncoming(LIR lir, AbstractBlockBase<?> block) {
        incoming(lir, block).clearIncomingValues();
    }

    public static void removeOutgoing(LIR lir, AbstractBlockBase<?> block) {
        outgoing(lir, block).clearOutgoingValues();
    }

    /**
     * Visits each SIGMA/PHI value pair of an edge, i.e. the outgoing value from the predecessor and
     * the incoming value to the merge block.
     */
    public static void forEachValuePair(LIR lir, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> fromBlock, PhiValueVisitor visitor) {
        assert Arrays.asList(toBlock.getPredecessors()).contains(fromBlock) : String.format("%s not in predecessor list: %s", fromBlock, Arrays.toString(toBlock.getPredecessors()));
        assert fromBlock.getSuccessorCount() == 1 || toBlock.getPredecessorCount() == 1 : String.format("Critical Edge? %s has %d successors and %s has %d predecessors", fromBlock,
                        fromBlock.getSuccessorCount(), toBlock, toBlock.getPredecessorCount());
        assert Arrays.asList(fromBlock.getSuccessors()).contains(toBlock) : String.format("Predecessor block %s has wrong successor: %s, should contain: %s", fromBlock,
                        Arrays.toString(fromBlock.getSuccessors()), toBlock);

        BlockEndOp blockEnd = outgoing(lir, fromBlock);
        LabelOp label = incoming(lir, toBlock);

        assert label.getIncomingSize() == blockEnd.getOutgoingSize() : String.format("In/Out size mismatch: in=%d vs. out=%d, blocks %s vs. %s", label.getIncomingSize(), blockEnd.getOutgoingSize(),
                        toBlock, fromBlock);
        assert label.getPhiSize() == blockEnd.getPhiSize() : String.format("Phi In/Out size mismatch: in=%d vs. out=%d", label.getPhiSize(), blockEnd.getPhiSize());

        for (int i = 0; i < label.getIncomingSize(); i++) {
            visitor.visit(label.getIncomingValue(i), blockEnd.getOutgoingValue(i));
        }
    }

    public static void forEachRegisterHint(LIR lir, AbstractBlockBase<?> block, LabelOp label, Value targetValue, OperandMode mode, ValueConsumer valueConsumer) {
        assert mode == OperandMode.DEF : "Wrong operand mode: " + mode;
        assert lir.getLIRforBlock(block).get(0).equals(label) : String.format("Block %s and Label %s do not match!", block, label);

        if (!label.isPhiIn()) {
            return;
        }
        int idx = indexOfValue(label, targetValue);
        assert idx >= 0 : String.format("Value %s not in label %s", targetValue, label);

        for (AbstractBlockBase<?> pred : block.getPredecessors()) {
            BlockEndOp blockEnd = outgoing(lir, pred);
            Value sourceValue = blockEnd.getOutgoingValue(idx);
            valueConsumer.visitValue((LIRInstruction) blockEnd, sourceValue, null, null);
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
