/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.nodes.arithmetic;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;

@NodeInfo(cycles = CYCLES_2, cyclesRationale = "add+cmp", size = SIZE_2)
public abstract class IntegerExactArithmeticSplitNode extends ControlSplitNode implements Simplifiable, LIRLowerable {
    public static final NodeClass<IntegerExactArithmeticSplitNode> TYPE = NodeClass.create(IntegerExactArithmeticSplitNode.class);

    @Successor AbstractBeginNode next;
    @Successor AbstractBeginNode overflowSuccessor;
    @Input ValueNode x;
    @Input ValueNode y;

    protected IntegerExactArithmeticSplitNode(NodeClass<? extends IntegerExactArithmeticSplitNode> c, Stamp stamp, ValueNode x, ValueNode y, AbstractBeginNode next,
                    AbstractBeginNode overflowSuccessor) {
        super(c, stamp);
        this.x = x;
        this.y = y;
        this.overflowSuccessor = overflowSuccessor;
        this.next = next;
    }

    @Override
    public AbstractBeginNode getPrimarySuccessor() {
        return next;
    }

    @Override
    public double probability(AbstractBeginNode successor) {
        return successor == next ? 1 : 0;
    }

    @Override
    public boolean setProbability(AbstractBeginNode successor, double value) {
        // Successor probabilities for arithmetic split nodes are fixed.
        return false;
    }

    public AbstractBeginNode getNext() {
        return next;
    }

    public AbstractBeginNode getOverflowSuccessor() {
        return overflowSuccessor;
    }

    public void setNext(AbstractBeginNode next) {
        updatePredecessor(this.next, next);
        this.next = next;
    }

    public void setOverflowSuccessor(AbstractBeginNode overflowSuccessor) {
        updatePredecessor(this.overflowSuccessor, overflowSuccessor);
        this.overflowSuccessor = overflowSuccessor;
    }

    public ValueNode getX() {
        return x;
    }

    public ValueNode getY() {
        return y;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, generateArithmetic(generator));
        generator.emitOverflowCheckBranch(getOverflowSuccessor(), getNext(), stamp, probability(getOverflowSuccessor()));
    }

    protected abstract Value generateArithmetic(NodeLIRBuilderTool generator);

    @Override
    public int getSuccessorCount() {
        return 2;
    }
}
