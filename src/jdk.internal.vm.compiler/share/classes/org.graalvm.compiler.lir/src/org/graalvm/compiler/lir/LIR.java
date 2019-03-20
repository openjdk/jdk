/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.StandardOp.LabelHoldingOp;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.options.OptionValues;

/**
 * This class implements the overall container for the LIR graph and directs its construction,
 * optimization, and finalization.
 */
public final class LIR extends LIRGenerator.VariableProvider {

    private final AbstractControlFlowGraph<?> cfg;

    /**
     * The linear-scan ordered list of blocks.
     */
    private final AbstractBlockBase<?>[] linearScanOrder;

    /**
     * The order in which the code is emitted.
     */
    private final AbstractBlockBase<?>[] codeEmittingOrder;

    /**
     * Map from {@linkplain AbstractBlockBase block} to {@linkplain LIRInstruction}s. Note that we
     * are using {@link ArrayList} instead of {@link List} to avoid interface dispatch.
     */
    private final BlockMap<ArrayList<LIRInstruction>> lirInstructions;

    private boolean hasArgInCallerFrame;

    private final OptionValues options;

    private final DebugContext debug;

    /**
     * Creates a new LIR instance for the specified compilation.
     */
    public LIR(AbstractControlFlowGraph<?> cfg, AbstractBlockBase<?>[] linearScanOrder, AbstractBlockBase<?>[] codeEmittingOrder, OptionValues options, DebugContext debug) {
        this.cfg = cfg;
        this.codeEmittingOrder = codeEmittingOrder;
        this.linearScanOrder = linearScanOrder;
        this.lirInstructions = new BlockMap<>(cfg);
        this.options = options;
        this.debug = debug;
    }

    public AbstractControlFlowGraph<?> getControlFlowGraph() {
        return cfg;
    }

    public OptionValues getOptions() {
        return options;
    }

    public DebugContext getDebug() {
        return debug;
    }

    /**
     * Determines if any instruction in the LIR has debug info associated with it.
     */
    public boolean hasDebugInfo() {
        for (AbstractBlockBase<?> b : linearScanOrder()) {
            for (LIRInstruction op : getLIRforBlock(b)) {
                if (op.hasState()) {
                    return true;
                }
            }
        }
        return false;
    }

    public ArrayList<LIRInstruction> getLIRforBlock(AbstractBlockBase<?> block) {
        return lirInstructions.get(block);
    }

    public void setLIRforBlock(AbstractBlockBase<?> block, ArrayList<LIRInstruction> list) {
        assert getLIRforBlock(block) == null : "lir instruction list should only be initialized once";
        lirInstructions.put(block, list);
    }

    /**
     * Gets the linear scan ordering of blocks as an array.
     *
     * @return the blocks in linear scan order
     */
    public AbstractBlockBase<?>[] linearScanOrder() {
        return linearScanOrder;
    }

    public AbstractBlockBase<?>[] codeEmittingOrder() {
        return codeEmittingOrder;
    }

    public void setHasArgInCallerFrame() {
        hasArgInCallerFrame = true;
    }

    /**
     * Determines if any of the parameters to the method are passed via the stack where the
     * parameters are located in the caller's frame.
     */
    public boolean hasArgInCallerFrame() {
        return hasArgInCallerFrame;
    }

    /**
     * Gets the next non-{@code null} block in a list.
     *
     * @param blocks list of blocks
     * @param blockIndex index of the current block
     * @return the next block in the list that is none {@code null} or {@code null} if there is no
     *         such block
     */
    public static AbstractBlockBase<?> getNextBlock(AbstractBlockBase<?>[] blocks, int blockIndex) {
        for (int nextIndex = blockIndex + 1; nextIndex > 0 && nextIndex < blocks.length; nextIndex++) {
            AbstractBlockBase<?> nextBlock = blocks[nextIndex];
            if (nextBlock != null) {
                return nextBlock;
            }
        }
        return null;
    }

    /**
     * Gets the exception edge (if any) originating at a given operation.
     */
    public static LabelRef getExceptionEdge(LIRInstruction op) {
        final LabelRef[] exceptionEdge = {null};
        op.forEachState(state -> {
            if (state.exceptionEdge != null) {
                assert exceptionEdge[0] == null;
                exceptionEdge[0] = state.exceptionEdge;
            }
        });
        return exceptionEdge[0];
    }

    /**
     * The maximum distance an operation with an {@linkplain #getExceptionEdge(LIRInstruction)
     * exception edge} can be from the last instruction of a LIR block. The value of 3 is based on a
     * non-void call operation that has an exception edge. Such a call may move the result to
     * another register and then spill it.
     * <p>
     * The rationale for such a constant is to limit the search for an insertion point when adding
     * move operations at the end of a block. Such moves must be inserted before all control flow
     * instructions.
     */
    public static final int MAX_EXCEPTION_EDGE_OP_DISTANCE_FROM_END = 3;

    public static boolean verifyBlock(LIR lir, AbstractBlockBase<?> block) {
        ArrayList<LIRInstruction> ops = lir.getLIRforBlock(block);
        if (ops.size() == 0) {
            return false;
        }
        assert ops.get(0) instanceof LabelOp : String.format("Not a Label %s (Block %s)", ops.get(0).getClass(), block);
        LIRInstruction opWithExceptionEdge = null;
        int index = 0;
        int lastIndex = ops.size() - 1;
        for (LIRInstruction op : ops.subList(0, lastIndex)) {
            assert !(op instanceof BlockEndOp) : String.format("BlockEndOp %s (Block %s)", op.getClass(), block);
            LabelRef exceptionEdge = getExceptionEdge(op);
            if (exceptionEdge != null) {
                assert opWithExceptionEdge == null : "multiple ops with an exception edge not allowed";
                opWithExceptionEdge = op;
                int distanceFromEnd = lastIndex - index;
                assert distanceFromEnd <= MAX_EXCEPTION_EDGE_OP_DISTANCE_FROM_END;
            }
            index++;
        }
        LIRInstruction end = ops.get(lastIndex);
        assert end instanceof BlockEndOp : String.format("Not a BlockEndOp %s (Block %s)", end.getClass(), block);
        return true;
    }

    public static boolean verifyBlocks(LIR lir, AbstractBlockBase<?>[] blocks) {
        for (AbstractBlockBase<?> block : blocks) {
            if (block == null) {
                continue;
            }
            for (AbstractBlockBase<?> sux : block.getSuccessors()) {
                assert Arrays.asList(blocks).contains(sux) : "missing successor from: " + block + "to: " + sux;
            }
            for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                assert Arrays.asList(blocks).contains(pred) : "missing predecessor from: " + block + "to: " + pred;
            }
            if (!verifyBlock(lir, block)) {
                return false;
            }
        }
        return true;
    }

    public void resetLabels() {

        for (AbstractBlockBase<?> block : codeEmittingOrder()) {
            if (block == null) {
                continue;
            }
            for (LIRInstruction inst : lirInstructions.get(block)) {
                if (inst instanceof LabelHoldingOp) {
                    Label label = ((LabelHoldingOp) inst).getLabel();
                    if (label != null) {
                        label.reset();
                    }
                }
            }
        }
    }

}
