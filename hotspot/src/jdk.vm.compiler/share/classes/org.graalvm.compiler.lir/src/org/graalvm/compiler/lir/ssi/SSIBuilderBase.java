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
package org.graalvm.compiler.lir.ssi;

import java.util.BitSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;

import jdk.vm.ci.meta.Value;

public abstract class SSIBuilderBase {

    protected static final int LOG_LEVEL = Debug.INFO_LOG_LEVEL;
    protected final Value[] operands;
    protected final LIR lir;

    public SSIBuilderBase(LIR lir) {
        this.lir = lir;
        this.operands = new Value[lir.numVariables()];
    }

    protected LIR getLIR() {
        return lir;
    }

    abstract BitSet getLiveIn(AbstractBlockBase<?> block);

    abstract BitSet getLiveOut(AbstractBlockBase<?> block);

    public final void build() {
        buildIntern();
        // check that the liveIn set of the first block is empty
        AbstractBlockBase<?> startBlock = getLIR().getControlFlowGraph().getStartBlock();
        if (getLiveIn(startBlock).cardinality() != 0) {
            // bailout if this occurs in product mode.
            throw new GraalError("liveIn set of first block must be empty: " + getLiveIn(startBlock));
        }
    }

    protected abstract void buildIntern();

    @SuppressWarnings("try")
    public final void finish() {
        // iterate all blocks in reverse order
        for (AbstractBlockBase<?> block : (AbstractBlockBase<?>[]) lir.getControlFlowGraph().getBlocks()) {
            try (Indent indent = Debug.logAndIndent(LOG_LEVEL, "Finish Block %s", block)) {
                // set label
                buildOutgoing(block, getLiveOut(block));
                buildIncoming(block, getLiveIn(block));
            }
        }
    }

    private void buildIncoming(AbstractBlockBase<?> block, BitSet liveIn) {
        /*
         * Collect live out of predecessors since there might be values not used in this block which
         * might cause out/in mismatch.
         */
        BitSet predLiveOut = new BitSet(liveIn.length());
        for (AbstractBlockBase<?> pred : block.getPredecessors()) {
            predLiveOut.or(getLiveOut(pred));
        }
        if (predLiveOut.isEmpty()) {
            return;
        }

        Value[] values = new Value[predLiveOut.cardinality()];
        assert values.length > 0;
        int cnt = 0;
        for (int i = predLiveOut.nextSetBit(0); i >= 0; i = predLiveOut.nextSetBit(i + 1)) {
            values[cnt++] = liveIn.get(i) ? operands[i] : Value.ILLEGAL;
        }
        LabelOp label = SSIUtil.incoming(getLIR(), block);
        label.addIncomingValues(values);
    }

    private void buildOutgoing(AbstractBlockBase<?> block, BitSet liveOut) {
        if (liveOut.isEmpty()) {
            return;
        }
        Value[] values = new Value[liveOut.cardinality()];
        assert values.length > 0;
        int cnt = 0;
        for (int i = liveOut.nextSetBit(0); i >= 0; i = liveOut.nextSetBit(i + 1)) {
            values[cnt++] = operands[i];
        }
        BlockEndOp blockEndOp = SSIUtil.outgoing(getLIR(), block);
        blockEndOp.addOutgoingValues(values);
    }
}
