/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Association;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.List;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

/**
 * Denotes the merging of multiple control-flow paths.
 */
@NodeInfo(allowedUsageTypes = Association, cycles = CYCLES_0, size = SIZE_0)
public abstract class AbstractMergeNode extends BeginStateSplitNode implements IterableNodeType, Simplifiable, LIRLowerable {
    public static final NodeClass<AbstractMergeNode> TYPE = NodeClass.create(AbstractMergeNode.class);

    protected AbstractMergeNode(NodeClass<? extends AbstractMergeNode> c) {
        super(c);
    }

    @Input(Association) protected NodeInputList<EndNode> ends = new NodeInputList<>(this);

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.visitMerge(this);
    }

    public int forwardEndIndex(EndNode end) {
        return ends.indexOf(end);
    }

    public void addForwardEnd(EndNode end) {
        ends.add(end);
    }

    public final int forwardEndCount() {
        return ends.size();
    }

    public final EndNode forwardEndAt(int index) {
        return ends.get(index);
    }

    @Override
    public NodeIterable<EndNode> cfgPredecessors() {
        return ends;
    }

    /**
     * Determines if a given node is a phi whose {@linkplain PhiNode#merge() merge} is this node.
     *
     * @param value the instruction to test
     * @return {@code true} if {@code value} is a phi and its merge is {@code this}
     */
    public boolean isPhiAtMerge(Node value) {
        return value instanceof PhiNode && ((PhiNode) value).merge() == this;
    }

    /**
     * Removes the given end from the merge, along with the entries corresponding to this end in the
     * phis connected to the merge.
     *
     * @param pred the end to remove
     */
    public void removeEnd(AbstractEndNode pred) {
        int predIndex = phiPredecessorIndex(pred);
        assert predIndex != -1;
        deleteEnd(pred);
        for (PhiNode phi : phis().snapshot()) {
            if (phi.isDeleted()) {
                continue;
            }
            ValueNode removedValue = phi.valueAt(predIndex);
            phi.removeInput(predIndex);
            if (removedValue != null && removedValue.isAlive() && removedValue.hasNoUsages() && GraphUtil.isFloatingNode(removedValue)) {
                GraphUtil.killWithUnusedFloatingInputs(removedValue);
            }
        }
    }

    protected void deleteEnd(AbstractEndNode end) {
        ends.remove(end);
    }

    public void clearEnds() {
        ends.clear();
    }

    public NodeInputList<EndNode> forwardEnds() {
        return ends;
    }

    public int phiPredecessorCount() {
        return forwardEndCount();
    }

    public int phiPredecessorIndex(AbstractEndNode pred) {
        return forwardEndIndex((EndNode) pred);
    }

    public AbstractEndNode phiPredecessorAt(int index) {
        return forwardEndAt(index);
    }

    public NodeIterable<PhiNode> phis() {
        return this.usages().filter(PhiNode.class).filter(this::isPhiAtMerge);
    }

    public NodeIterable<ValuePhiNode> valuePhis() {
        return this.usages().filter(ValuePhiNode.class).filter(this::isPhiAtMerge);
    }

    @Override
    public NodeIterable<Node> anchored() {
        return super.anchored().filter(n -> !isPhiAtMerge(n));
    }

    /**
     * This simplify method can deal with a null value for tool, so that it can be used outside of
     * canonicalization.
     */
    @Override
    public void simplify(SimplifierTool tool) {
        FixedNode currentNext = next();
        if (currentNext instanceof AbstractEndNode) {
            AbstractEndNode origLoopEnd = (AbstractEndNode) currentNext;
            AbstractMergeNode merge = origLoopEnd.merge();
            if (merge instanceof LoopBeginNode && !(origLoopEnd instanceof LoopEndNode)) {
                return;
            }
            // in order to move anchored values to the other merge we would need to check if the
            // anchors are used by phis of the other merge
            if (this.anchored().isNotEmpty()) {
                return;
            }
            if (merge.stateAfter() == null && this.stateAfter() != null) {
                // We hold a state, but the succeeding merge does not => do not combine.
                return;
            }
            for (PhiNode phi : phis()) {
                for (Node usage : phi.usages()) {
                    if (!(usage instanceof VirtualState) && !merge.isPhiAtMerge(usage)) {
                        return;
                    }
                }
            }
            Debug.log("Split %s into ends for %s.", this, merge);
            int numEnds = this.forwardEndCount();
            for (int i = 0; i < numEnds - 1; i++) {
                AbstractEndNode end = forwardEndAt(numEnds - 1 - i);
                if (tool != null) {
                    tool.addToWorkList(end);
                }
                AbstractEndNode newEnd;
                if (merge instanceof LoopBeginNode) {
                    newEnd = graph().add(new LoopEndNode((LoopBeginNode) merge));
                } else {
                    EndNode tmpEnd = graph().add(new EndNode());
                    merge.addForwardEnd(tmpEnd);
                    newEnd = tmpEnd;
                }
                for (PhiNode phi : merge.phis()) {
                    ValueNode v = phi.valueAt(origLoopEnd);
                    ValueNode newInput;
                    if (isPhiAtMerge(v)) {
                        PhiNode endPhi = (PhiNode) v;
                        newInput = endPhi.valueAt(end);
                    } else {
                        newInput = v;
                    }
                    phi.addInput(newInput);
                }
                this.removeEnd(end);
                end.replaceAtPredecessor(newEnd);
                end.safeDelete();
                if (tool != null) {
                    tool.addToWorkList(newEnd.predecessor());
                }
            }
            graph().reduceTrivialMerge(this);
        } else if (currentNext instanceof ReturnNode) {
            ReturnNode returnNode = (ReturnNode) currentNext;
            if (anchored().isNotEmpty() || returnNode.getMemoryMap() != null) {
                return;
            }
            List<PhiNode> phis = phis().snapshot();
            for (PhiNode phi : phis) {
                for (Node usage : phi.usages()) {
                    if (usage != returnNode && !(usage instanceof FrameState)) {
                        return;
                    }
                }
            }

            ValuePhiNode returnValuePhi = returnNode.result() == null || !isPhiAtMerge(returnNode.result()) ? null : (ValuePhiNode) returnNode.result();
            List<EndNode> endNodes = forwardEnds().snapshot();
            for (EndNode end : endNodes) {
                ReturnNode newReturn = graph().add(new ReturnNode(returnValuePhi == null ? returnNode.result() : returnValuePhi.valueAt(end)));
                if (tool != null) {
                    tool.addToWorkList(end.predecessor());
                }
                end.replaceAtPredecessor(newReturn);
            }
            GraphUtil.killCFG(this);
            for (EndNode end : endNodes) {
                end.safeDelete();
            }
            for (PhiNode phi : phis) {
                if (tool.allUsagesAvailable() && phi.isAlive() && phi.hasNoUsages()) {
                    GraphUtil.killWithUnusedFloatingInputs(phi);
                }
            }
        }
    }
}
