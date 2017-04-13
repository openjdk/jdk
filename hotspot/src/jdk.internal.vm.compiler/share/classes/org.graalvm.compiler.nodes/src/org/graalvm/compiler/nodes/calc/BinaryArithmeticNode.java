/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import java.io.Serializable;
import java.util.function.Function;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ArithmeticOperation;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeValueMap;

import jdk.vm.ci.meta.Constant;

@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public abstract class BinaryArithmeticNode<OP> extends BinaryNode implements ArithmeticOperation, ArithmeticLIRLowerable, Canonicalizable.Binary<ValueNode> {

    @SuppressWarnings("rawtypes") public static final NodeClass<BinaryArithmeticNode> TYPE = NodeClass.create(BinaryArithmeticNode.class);

    protected interface SerializableBinaryFunction<T> extends Function<ArithmeticOpTable, BinaryOp<T>>, Serializable {
    }

    protected final SerializableBinaryFunction<OP> getOp;

    protected BinaryArithmeticNode(NodeClass<? extends BinaryArithmeticNode<OP>> c, SerializableBinaryFunction<OP> getOp, ValueNode x, ValueNode y) {
        super(c, getOp.apply(ArithmeticOpTable.forStamp(x.stamp())).foldStamp(x.stamp(), y.stamp()), x, y);
        this.getOp = getOp;
    }

    protected final BinaryOp<OP> getOp(ValueNode forX, ValueNode forY) {
        ArithmeticOpTable table = ArithmeticOpTable.forStamp(forX.stamp());
        assert table.equals(ArithmeticOpTable.forStamp(forY.stamp()));
        return getOp.apply(table);
    }

    @Override
    public final BinaryOp<OP> getArithmeticOp() {
        return getOp(getX(), getY());
    }

    public boolean isAssociative() {
        return getArithmeticOp().isAssociative();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode result = tryConstantFold(getOp(forX, forY), forX, forY, stamp());
        if (result != null) {
            return result;
        }
        return this;
    }

    public static <OP> ConstantNode tryConstantFold(BinaryOp<OP> op, ValueNode forX, ValueNode forY, Stamp stamp) {
        if (forX.isConstant() && forY.isConstant()) {
            Constant ret = op.foldConstant(forX.asConstant(), forY.asConstant());
            return ConstantNode.forPrimitive(stamp, ret);
        }
        return null;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        assert stampX.isCompatible(x.stamp()) && stampY.isCompatible(y.stamp());
        return getArithmeticOp().foldStamp(stampX, stampY);
    }

    public static AddNode add(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return graph.unique(new AddNode(v1, v2));
    }

    public static AddNode add(ValueNode v1, ValueNode v2) {
        return new AddNode(v1, v2);
    }

    public static MulNode mul(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return graph.unique(new MulNode(v1, v2));
    }

    public static MulNode mul(ValueNode v1, ValueNode v2) {
        return new MulNode(v1, v2);
    }

    public static SubNode sub(StructuredGraph graph, ValueNode v1, ValueNode v2) {
        return graph.unique(new SubNode(v1, v2));
    }

    public static SubNode sub(ValueNode v1, ValueNode v2) {
        return new SubNode(v1, v2);
    }

    private enum ReassociateMatch {
        x,
        y;

        public ValueNode getValue(BinaryNode binary) {
            switch (this) {
                case x:
                    return binary.getX();
                case y:
                    return binary.getY();
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }

        public ValueNode getOtherValue(BinaryNode binary) {
            switch (this) {
                case x:
                    return binary.getY();
                case y:
                    return binary.getX();
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }
    }

    private static ReassociateMatch findReassociate(BinaryNode binary, NodePredicate criterion) {
        boolean resultX = criterion.apply(binary.getX());
        boolean resultY = criterion.apply(binary.getY());
        if (resultX && !resultY) {
            return ReassociateMatch.x;
        }
        if (!resultX && resultY) {
            return ReassociateMatch.y;
        }
        return null;
    }

    //@formatter:off
    /*
     * In reassociate, complexity comes from the handling of IntegerSub (non commutative) which can
     * be mixed with IntegerAdd. It first tries to find m1, m2 which match the criterion :
     * (a o m2) o m1
     * (m2 o a) o m1
     * m1 o (a o m2)
     * m1 o (m2 o a)
     * It then produces 4 boolean for the -/+ cases:
     * invertA : should the final expression be like *-a (rather than a+*)
     * aSub : should the final expression be like a-* (rather than a+*)
     * invertM1 : should the final expression contain -m1
     * invertM2 : should the final expression contain -m2
     *
     */
    //@formatter:on
    /**
     * Tries to re-associate values which satisfy the criterion. For example with a constantness
     * criterion: {@code (a + 2) + 1 => a + (1 + 2)}
     * <p>
     * This method accepts only {@linkplain BinaryOp#isAssociative() associative} operations such as
     * +, -, *, &amp;, | and ^
     *
     * @param forY
     * @param forX
     */
    public static BinaryArithmeticNode<?> reassociate(BinaryArithmeticNode<?> node, NodePredicate criterion, ValueNode forX, ValueNode forY) {
        assert node.getOp(forX, forY).isAssociative();
        ReassociateMatch match1 = findReassociate(node, criterion);
        if (match1 == null) {
            return node;
        }
        ValueNode otherValue = match1.getOtherValue(node);
        boolean addSub = false;
        boolean subAdd = false;
        if (otherValue.getClass() != node.getClass()) {
            if (node instanceof AddNode && otherValue instanceof SubNode) {
                addSub = true;
            } else if (node instanceof SubNode && otherValue instanceof AddNode) {
                subAdd = true;
            } else {
                return node;
            }
        }
        BinaryNode other = (BinaryNode) otherValue;
        ReassociateMatch match2 = findReassociate(other, criterion);
        if (match2 == null) {
            return node;
        }
        boolean invertA = false;
        boolean aSub = false;
        boolean invertM1 = false;
        boolean invertM2 = false;
        if (addSub) {
            invertM2 = match2 == ReassociateMatch.y;
            invertA = !invertM2;
        } else if (subAdd) {
            invertA = invertM2 = match1 == ReassociateMatch.x;
            invertM1 = !invertM2;
        } else if (node instanceof SubNode && other instanceof SubNode) {
            invertA = match1 == ReassociateMatch.x ^ match2 == ReassociateMatch.x;
            aSub = match1 == ReassociateMatch.y && match2 == ReassociateMatch.y;
            invertM1 = match1 == ReassociateMatch.y && match2 == ReassociateMatch.x;
            invertM2 = match1 == ReassociateMatch.x && match2 == ReassociateMatch.x;
        }
        assert !(invertM1 && invertM2) && !(invertA && aSub);
        ValueNode m1 = match1.getValue(node);
        ValueNode m2 = match2.getValue(other);
        ValueNode a = match2.getOtherValue(other);
        if (node instanceof AddNode || node instanceof SubNode) {
            BinaryNode associated;
            if (invertM1) {
                associated = BinaryArithmeticNode.sub(m2, m1);
            } else if (invertM2) {
                associated = BinaryArithmeticNode.sub(m1, m2);
            } else {
                associated = BinaryArithmeticNode.add(m1, m2);
            }
            if (invertA) {
                return BinaryArithmeticNode.sub(associated, a);
            }
            if (aSub) {
                return BinaryArithmeticNode.sub(a, associated);
            }
            return BinaryArithmeticNode.add(a, associated);
        } else if (node instanceof MulNode) {
            return BinaryArithmeticNode.mul(a, AddNode.mul(m1, m2));
        } else if (node instanceof AndNode) {
            return new AndNode(a, new AndNode(m1, m2));
        } else if (node instanceof OrNode) {
            return new OrNode(a, new OrNode(m1, m2));
        } else if (node instanceof XorNode) {
            return new XorNode(a, new XorNode(m1, m2));
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * Ensure a canonical ordering of inputs for commutative nodes to improve GVN results. Order the
     * inputs by increasing {@link Node#id} and call {@link Graph#findDuplicate(Node)} on the node
     * if it's currently in a graph. It's assumed that if there was a constant on the left it's been
     * moved to the right by other code and that ordering is left alone.
     *
     * @return the original node or another node with the same input ordering
     */
    @SuppressWarnings("deprecation")
    public BinaryNode maybeCommuteInputs() {
        assert this instanceof BinaryCommutative;
        if (!y.isConstant() && x.getId() > y.getId()) {
            ValueNode tmp = x;
            x = y;
            y = tmp;
            if (graph() != null) {
                // See if this node already exists
                BinaryNode duplicate = graph().findDuplicate(this);
                if (duplicate != null) {
                    return duplicate;
                }
            }
        }
        return this;
    }

    /**
     * Determines if it would be better to swap the inputs in order to produce better assembly code.
     * First we try to pick a value which is dead after this use. If both values are dead at this
     * use then we try pick an induction variable phi to encourage the phi to live in a single
     * register.
     *
     * @param nodeValueMap
     * @return true if inputs should be swapped, false otherwise
     */
    protected boolean shouldSwapInputs(NodeValueMap nodeValueMap) {
        final boolean xHasOtherUsages = getX().hasUsagesOtherThan(this, nodeValueMap);
        final boolean yHasOtherUsages = getY().hasUsagesOtherThan(this, nodeValueMap);

        if (!getY().isConstant() && !yHasOtherUsages) {
            if (xHasOtherUsages == yHasOtherUsages) {
                return getY() instanceof ValuePhiNode && getY().inputs().contains(this);
            } else {
                return true;
            }
        }
        return false;
    }

}
