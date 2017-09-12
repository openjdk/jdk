/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.Variable;

import jdk.vm.ci.meta.Value;

/**
 * Stores live in/live out variables and locations for each basic block.
 *
 * <em>Live variable information</em> is stored as an integer array containing <em>variable
 * indices</em>. The information is only stored once per <em>control-flow split</em> or
 * <em>control-merge</em>. In other words the live sets at the end of the source block and the
 * beginning of the target block of an edge are the same.
 */
public final class GlobalLivenessInfo {

    public static final class Builder {
        private GlobalLivenessInfo info;
        public final int[] emptySet;

        public Builder(LIR lir) {
            info = new GlobalLivenessInfo(lir);
            emptySet = new int[0];
        }

        public GlobalLivenessInfo createLivenessInfo() {
            GlobalLivenessInfo livenessInfo = info;
            info = null;
            return livenessInfo;
        }

        public void setIncoming(AbstractBlockBase<?> block, int[] varsIn) {
            assert info.blockToVarIn[block.getId()] == null;
            assert verifyVars(varsIn);
            assert storesIncoming(block) || info.blockToVarOut[block.getPredecessors()[0].getId()] == varsIn;
            info.blockToVarIn[block.getId()] = varsIn;
        }

        public void setOutgoing(AbstractBlockBase<?> block, int[] varsOut) {
            assert info.blockToVarOut[block.getId()] == null;
            assert verifyVars(varsOut);
            assert storesOutgoing(block) || info.blockToVarIn[block.getSuccessors()[0].getId()] == varsOut;
            info.blockToVarOut[block.getId()] = varsOut;
        }

        private static boolean verifyVars(int[] vars) {
            for (int var : vars) {
                assert var >= 0;
            }
            return true;
        }

        @SuppressWarnings("unused")
        public void addVariable(LIRInstruction op, Variable var) {
            info.variables[var.index] = var;
        }

    }

    private final Variable[] variables;
    private final int[][] blockToVarIn;
    private final int[][] blockToVarOut;
    private final Value[][] blockToLocIn;
    private final Value[][] blockToLocOut;

    private GlobalLivenessInfo(LIR lir) {
        int numVariables = lir.numVariables();
        variables = new Variable[numVariables];
        int numBlocks = lir.getControlFlowGraph().getBlocks().length;
        blockToVarIn = new int[numBlocks][];
        blockToVarOut = new int[numBlocks][];
        blockToLocIn = new Value[numBlocks][];
        blockToLocOut = new Value[numBlocks][];
    }

    public Variable getVariable(int varNum) {
        return variables[varNum];
    }

    public int[] getBlockOut(AbstractBlockBase<?> block) {
        return blockToVarOut[block.getId()];
    }

    public int[] getBlockIn(AbstractBlockBase<?> block) {
        return blockToVarIn[block.getId()];
    }

    public void setInLocations(AbstractBlockBase<?> block, Value[] values) {
        blockToLocIn[block.getId()] = values;
    }

    public void setOutLocations(AbstractBlockBase<?> block, Value[] values) {
        blockToLocOut[block.getId()] = values;
    }

    public Value[] getInLocation(AbstractBlockBase<?> block) {
        return blockToLocIn[block.getId()];
    }

    public Value[] getOutLocation(AbstractBlockBase<?> block) {
        return blockToLocOut[block.getId()];
    }

    public static boolean storesIncoming(AbstractBlockBase<?> block) {
        assert block.getPredecessorCount() >= 0;
        return block.getPredecessorCount() != 1;
    }

    public static boolean storesOutgoing(AbstractBlockBase<?> block) {
        assert block.getSuccessorCount() >= 0;
        /*
         * The second condition handles non-critical empty blocks, introduced, e.g., by two
         * consecutive loop-exits.
         */
        return block.getSuccessorCount() != 1 || block.getSuccessors()[0].getPredecessorCount() == 1;
    }

    /**
     * Verifies that the local liveness information is correct, i.e., that all variables used in a
     * block {@code b} are either defined in {@code b} or in the incoming live set.
     */
    @SuppressWarnings("try")
    public boolean verify(LIR lir) {
        DebugContext debug = lir.getDebug();
        try (DebugContext.Scope s = debug.scope("Verify GlobalLivenessInfo", this)) {
            for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
                assert verifyBlock(block, lir);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        return true;
    }

    private boolean verifyBlock(AbstractBlockBase<?> block, LIR lir) {
        BitSet liveSet = new BitSet(lir.numVariables());
        int[] liveIn = getBlockIn(block);
        for (int varNum : liveIn) {
            liveSet.set(varNum);
        }
        ValueConsumer proc = new ValueConsumer() {

            @Override
            public void visitValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (LIRValueUtil.isVariable(value)) {
                    Variable var = LIRValueUtil.asVariable(value);
                    if (mode == OperandMode.DEF) {
                        liveSet.set(var.index);
                    } else {
                        assert liveSet.get(var.index) : String.format("Variable %s but not defined in block %s (liveIn: %s)", var, block, Arrays.toString(liveIn));
                    }
                }
            }

        };
        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            op.visitEachInput(proc);
            op.visitEachAlive(proc);
            op.visitEachState(proc);
            op.visitEachOutput(proc);
            // no need for checking temp
        }
        return true;
    }
}
