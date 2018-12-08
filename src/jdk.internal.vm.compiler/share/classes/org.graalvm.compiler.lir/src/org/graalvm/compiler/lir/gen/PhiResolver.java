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


package org.graalvm.compiler.lir.gen;

import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.meta.Value.ILLEGAL;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;

import java.util.ArrayList;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIRInsertionBuffer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Converts phi instructions into moves.
 *
 * Resolves cycles:
 *
 * <pre>
 *
 *  r1 := r2  becomes  temp := r1
 *  r2 := r1           r1 := r2
 *                     r2 := temp
 * </pre>
 *
 * and orders moves:
 *
 * <pre>
 *  r2 := r3  becomes  r1 := r2
 *  r1 := r2           r2 := r3
 * </pre>
 */
public class PhiResolver {

    /**
     * Tracks a data flow dependency between a source operand and any number of the destination
     * operands.
     */
    static class PhiResolverNode {

        /**
         * A source operand whose value flows into the {@linkplain #destinations destination}
         * operands.
         */
        final Value operand;

        /**
         * The operands whose values are defined by the {@linkplain #operand source} operand.
         */
        final ArrayList<PhiResolverNode> destinations;

        /**
         * Denotes if a move instruction has already been emitted to initialize the value of
         * {@link #operand}.
         */
        boolean assigned;

        /**
         * Specifies if this operand been visited for the purpose of emitting a move instruction.
         */
        boolean visited;

        /**
         * Specifies if this is the initial definition in data flow path for a given value.
         */
        boolean startNode;

        PhiResolverNode(Value operand) {
            this.operand = operand;
            destinations = new ArrayList<>(4);
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder(operand.toString());
            if (!destinations.isEmpty()) {
                buf.append(" ->");
                for (PhiResolverNode node : destinations) {
                    buf.append(' ').append(node.operand);
                }
            }
            return buf.toString();
        }
    }

    private final LIRGeneratorTool gen;
    private final MoveFactory moveFactory;
    private final LIRInsertionBuffer buffer;
    private final int insertBefore;

    /**
     * The operand loop header phi for the operand currently being process in {@link #dispose()}.
     */
    private PhiResolverNode loop;

    private Value temp;

    private final ArrayList<PhiResolverNode> variableOperands = new ArrayList<>(3);
    private final ArrayList<PhiResolverNode> otherOperands = new ArrayList<>(3);

    /**
     * Maps operands to nodes.
     */
    private final EconomicMap<Value, PhiResolverNode> operandToNodeMap = EconomicMap.create(Equivalence.DEFAULT);

    public static PhiResolver create(LIRGeneratorTool gen) {
        AbstractBlockBase<?> block = gen.getCurrentBlock();
        assert block != null;
        ArrayList<LIRInstruction> instructions = gen.getResult().getLIR().getLIRforBlock(block);

        return new PhiResolver(gen, new LIRInsertionBuffer(), instructions, instructions.size());
    }

    public static PhiResolver create(LIRGeneratorTool gen, LIRInsertionBuffer buffer, List<LIRInstruction> instructions, int insertBefore) {
        return new PhiResolver(gen, buffer, instructions, insertBefore);
    }

    protected PhiResolver(LIRGeneratorTool gen, LIRInsertionBuffer buffer, List<LIRInstruction> instructions, int insertBefore) {
        this.gen = gen;
        moveFactory = gen.getSpillMoveFactory();
        temp = ILLEGAL;

        this.buffer = buffer;
        this.buffer.init(instructions);
        this.insertBefore = insertBefore;

    }

    public void dispose() {
        // resolve any cycles in moves from and to variables
        for (int i = variableOperands.size() - 1; i >= 0; i--) {
            PhiResolverNode node = variableOperands.get(i);
            if (!node.visited) {
                loop = null;
                move(node, null);
                node.startNode = true;
                assert isIllegal(temp) : "moveTempTo() call missing";
            }
        }

        // generate move for move from non variable to arbitrary destination
        for (int i = otherOperands.size() - 1; i >= 0; i--) {
            PhiResolverNode node = otherOperands.get(i);
            for (int j = node.destinations.size() - 1; j >= 0; j--) {
                emitMove(node.destinations.get(j).operand, node.operand);
            }
        }
        buffer.finish();
    }

    public void move(Value dest, Value src) {
        assert isVariable(dest) : "destination must be virtual";
        // tty.print("move "); src.print(); tty.print(" to "); dest.print(); tty.cr();
        assert isLegal(src) : "source for phi move is illegal";
        assert isLegal(dest) : "destination for phi move is illegal";
        PhiResolverNode srcNode = sourceNode(src);
        PhiResolverNode destNode = destinationNode(dest);
        srcNode.destinations.add(destNode);
    }

    private PhiResolverNode createNode(Value operand, boolean source) {
        PhiResolverNode node;
        if (isVariable(operand)) {
            node = operandToNodeMap.get(operand);
            assert node == null || node.operand.equals(operand);
            if (node == null) {
                node = new PhiResolverNode(operand);
                operandToNodeMap.put(operand, node);
            }
            // Make sure that all variables show up in the list when
            // they are used as the source of a move.
            if (source) {
                if (!variableOperands.contains(node)) {
                    variableOperands.add(node);
                }
            }
        } else {
            assert source;
            node = new PhiResolverNode(operand);
            otherOperands.add(node);
        }
        return node;
    }

    private PhiResolverNode destinationNode(Value opr) {
        return createNode(opr, false);
    }

    private void emitMove(Value dest, Value src) {
        assert isLegal(src);
        assert isLegal(dest);
        LIRInstruction move = moveFactory.createMove((AllocatableValue) dest, src);
        buffer.append(insertBefore, move);
    }

    // Traverse assignment graph in depth first order and generate moves in post order
    // ie. two assignments: b := c, a := b start with node c:
    // Call graph: move(c, NULL) -> move(b, c) -> move(a, b)
    // Generates moves in this order: move b to a and move c to b
    // ie. cycle a := b, b := a start with node a
    // Call graph: move(a, NULL) -> move(b, a) -> move(a, b)
    // Generates moves in this order: move b to temp, move a to b, move temp to a
    private void move(PhiResolverNode dest, PhiResolverNode src) {
        if (!dest.visited) {
            dest.visited = true;
            for (int i = dest.destinations.size() - 1; i >= 0; i--) {
                move(dest.destinations.get(i), dest);
            }
        } else if (!dest.startNode) {
            // cycle in graph detected
            assert loop == null : "only one loop valid!";
            loop = dest;
            moveToTemp(src.operand);
            return;
        } // else dest is a start node

        if (!dest.assigned) {
            if (loop == dest) {
                moveTempTo(dest.operand);
                dest.assigned = true;
            } else if (src != null) {
                emitMove(dest.operand, src.operand);
                dest.assigned = true;
            }
        }
    }

    private void moveTempTo(Value dest) {
        assert isLegal(temp);
        emitMove(dest, temp);
        temp = ILLEGAL;
    }

    private void moveToTemp(Value src) {
        assert isIllegal(temp);
        temp = gen.newVariable(src.getValueKind());
        emitMove(temp, src);
    }

    private PhiResolverNode sourceNode(Value opr) {
        return createNode(opr, true);
    }
}
