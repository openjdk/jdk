/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.calc.FloatingNode;

/**
 * {@code PhiNode}s represent the merging of edges at a control flow merges (
 * {@link AbstractMergeNode} or {@link LoopBeginNode}). For a {@link AbstractMergeNode}, the order
 * of the values corresponds to the order of the ends. For {@link LoopBeginNode}s, the first value
 * corresponds to the loop's predecessor, while the rest of the values correspond to the
 * {@link LoopEndNode}s.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_1)
public abstract class PhiNode extends FloatingNode implements Canonicalizable {

    public static final NodeClass<PhiNode> TYPE = NodeClass.create(PhiNode.class);
    @Input(Association) protected AbstractMergeNode merge;

    protected PhiNode(NodeClass<? extends PhiNode> c, Stamp stamp, AbstractMergeNode merge) {
        super(c, stamp);
        this.merge = merge;
    }

    public abstract NodeInputList<ValueNode> values();

    public AbstractMergeNode merge() {
        return merge;
    }

    public void setMerge(AbstractMergeNode x) {
        updateUsages(merge, x);
        merge = x;
    }

    @Override
    public boolean verify() {
        assertTrue(merge() != null, "missing merge");
        assertTrue(merge().phiPredecessorCount() == valueCount(), "mismatch between merge predecessor count and phi value count: %d != %d", merge().phiPredecessorCount(), valueCount());
        return super.verify();
    }

    /**
     * Get the instruction that produces the value associated with the i'th predecessor of the
     * merge.
     *
     * @param i the index of the predecessor
     * @return the instruction that produced the value in the i'th predecessor
     */
    public ValueNode valueAt(int i) {
        return values().get(i);
    }

    /**
     * Sets the value at the given index and makes sure that the values list is large enough.
     *
     * @param i the index at which to set the value
     * @param x the new phi input value for the given location
     */
    public void initializeValueAt(int i, ValueNode x) {
        while (values().size() <= i) {
            values().add(null);
        }
        values().set(i, x);
    }

    public void setValueAt(int i, ValueNode x) {
        values().set(i, x);
    }

    public void setValueAt(AbstractEndNode end, ValueNode x) {
        setValueAt(merge().phiPredecessorIndex(end), x);
    }

    public ValueNode valueAt(AbstractEndNode pred) {
        return valueAt(merge().phiPredecessorIndex(pred));
    }

    /**
     * Get the number of inputs to this phi (i.e. the number of predecessors to the merge).
     *
     * @return the number of inputs in this phi
     */
    public int valueCount() {
        return values().size();
    }

    public void clearValues() {
        values().clear();
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < valueCount(); ++i) {
                if (i != 0) {
                    str.append(' ');
                }
                str.append(valueAt(i) == null ? "-" : valueAt(i).toString(Verbosity.Id));
            }
            String description = valueDescription();
            if (description.length() > 0) {
                str.append(", ").append(description);
            }
            return super.toString(Verbosity.Name) + "(" + str + ")";
        } else {
            return super.toString(verbosity);
        }
    }

    /**
     * String describing the kind of value this Phi merges. Used by {@link #toString(Verbosity)} and
     * dumping.
     */
    protected String valueDescription() {
        return "";
    }

    public void addInput(ValueNode x) {
        assert !(x instanceof ValuePhiNode) || ((ValuePhiNode) x).merge() instanceof LoopBeginNode || ((ValuePhiNode) x).merge() != this.merge();
        assert !(this instanceof ValuePhiNode) || x.stamp(NodeView.DEFAULT).isCompatible(stamp(NodeView.DEFAULT));
        values().add(x);
    }

    public void removeInput(int index) {
        values().remove(index);
    }

    public NodeIterable<ValueNode> backValues() {
        return values().subList(merge().forwardEndCount());
    }

    /**
     * If all inputs are the same value, this value is returned, otherwise {@code this}. Note that
     * {@code null} is a valid return value, since {@link GuardPhiNode}s can have {@code null}
     * inputs.
     */
    public ValueNode singleValueOrThis() {
        ValueNode singleValue = valueAt(0);
        int count = valueCount();
        for (int i = 1; i < count; ++i) {
            ValueNode value = valueAt(i);
            if (value != this) {
                if (value != singleValue) {
                    return this;
                }
            }
        }
        return singleValue;
    }

    /**
     * If all inputs (but the first one) are the same value, the value is returned, otherwise
     * {@code this}. Note that {@code null} is a valid return value, since {@link GuardPhiNode}s can
     * have {@code null} inputs.
     */
    public ValueNode singleBackValueOrThis() {
        int valueCount = valueCount();
        assert merge() instanceof LoopBeginNode && valueCount >= 2;
        // Skip first value, assume second value as single value.
        ValueNode singleValue = valueAt(1);
        for (int i = 2; i < valueCount; ++i) {
            ValueNode value = valueAt(i);
            if (value != singleValue) {
                return this;
            }
        }
        return singleValue;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {

        if (isLoopPhi()) {

            int valueCount = valueCount();
            assert valueCount >= 2;
            int i;
            for (i = 1; i < valueCount; ++i) {
                ValueNode value = valueAt(i);
                if (value != this) {
                    break;
                }
            }

            // All back edges are self-references => return forward edge input value.
            if (i == valueCount) {
                return firstValue();
            }

            boolean onlySelfUsage = true;
            for (Node n : this.usages()) {
                if (n != this) {
                    onlySelfUsage = false;
                    break;
                }
            }

            if (onlySelfUsage) {
                return null;
            }
        }

        return singleValueOrThis();
    }

    public ValueNode firstValue() {
        return valueAt(0);
    }

    public boolean isLoopPhi() {
        return merge() instanceof LoopBeginNode;
    }
}
