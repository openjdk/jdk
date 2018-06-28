/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.InputType.Condition;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;

import jdk.vm.ci.meta.TriState;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class ShortCircuitOrNode extends LogicNode implements IterableNodeType, Canonicalizable.Binary<LogicNode> {
    public static final NodeClass<ShortCircuitOrNode> TYPE = NodeClass.create(ShortCircuitOrNode.class);
    @Input(Condition) LogicNode x;
    @Input(Condition) LogicNode y;
    protected boolean xNegated;
    protected boolean yNegated;
    protected double shortCircuitProbability;

    public ShortCircuitOrNode(LogicNode x, boolean xNegated, LogicNode y, boolean yNegated, double shortCircuitProbability) {
        super(TYPE);
        this.x = x;
        this.xNegated = xNegated;
        this.y = y;
        this.yNegated = yNegated;
        this.shortCircuitProbability = shortCircuitProbability;
    }

    @Override
    public LogicNode getX() {
        return x;
    }

    @Override
    public LogicNode getY() {
        return y;
    }

    public boolean isXNegated() {
        return xNegated;
    }

    public boolean isYNegated() {
        return yNegated;
    }

    /**
     * Gets the probability that the {@link #getY() y} part of this binary node is <b>not</b>
     * evaluated. This is the probability that this operator will short-circuit its execution.
     */
    public double getShortCircuitProbability() {
        return shortCircuitProbability;
    }

    protected ShortCircuitOrNode canonicalizeNegation(LogicNode forX, LogicNode forY) {
        LogicNode xCond = forX;
        boolean xNeg = xNegated;
        while (xCond instanceof LogicNegationNode) {
            xCond = ((LogicNegationNode) xCond).getValue();
            xNeg = !xNeg;
        }

        LogicNode yCond = forY;
        boolean yNeg = yNegated;
        while (yCond instanceof LogicNegationNode) {
            yCond = ((LogicNegationNode) yCond).getValue();
            yNeg = !yNeg;
        }

        if (xCond != forX || yCond != forY) {
            return new ShortCircuitOrNode(xCond, xNeg, yCond, yNeg, shortCircuitProbability);
        } else {
            return this;
        }
    }

    @Override
    public LogicNode canonical(CanonicalizerTool tool, LogicNode forX, LogicNode forY) {
        ShortCircuitOrNode ret = canonicalizeNegation(forX, forY);
        if (ret != this) {
            return ret;
        }

        if (forX == forY) {
            // @formatter:off
            //  a ||  a = a
            //  a || !a = true
            // !a ||  a = true
            // !a || !a = !a
            // @formatter:on
            if (isXNegated()) {
                if (isYNegated()) {
                    // !a || !a = !a
                    return LogicNegationNode.create(forX);
                } else {
                    // !a || a = true
                    return LogicConstantNode.tautology();
                }
            } else {
                if (isYNegated()) {
                    // a || !a = true
                    return LogicConstantNode.tautology();
                } else {
                    // a || a = a
                    return forX;
                }
            }
        }
        if (forX instanceof LogicConstantNode) {
            if (((LogicConstantNode) forX).getValue() ^ isXNegated()) {
                return LogicConstantNode.tautology();
            } else {
                if (isYNegated()) {
                    return new LogicNegationNode(forY);
                } else {
                    return forY;
                }
            }
        }
        if (forY instanceof LogicConstantNode) {
            if (((LogicConstantNode) forY).getValue() ^ isYNegated()) {
                return LogicConstantNode.tautology();
            } else {
                if (isXNegated()) {
                    return new LogicNegationNode(forX);
                } else {
                    return forX;
                }
            }
        }

        if (forX instanceof ShortCircuitOrNode) {
            ShortCircuitOrNode inner = (ShortCircuitOrNode) forX;
            if (forY == inner.getX()) {
                return optimizeShortCircuit(inner, this.xNegated, this.yNegated, true);
            } else if (forY == inner.getY()) {
                return optimizeShortCircuit(inner, this.xNegated, this.yNegated, false);
            }
        } else if (forY instanceof ShortCircuitOrNode) {
            ShortCircuitOrNode inner = (ShortCircuitOrNode) forY;
            if (inner.getX() == forX) {
                return optimizeShortCircuit(inner, this.yNegated, this.xNegated, true);
            } else if (inner.getY() == forX) {
                return optimizeShortCircuit(inner, this.yNegated, this.xNegated, false);
            }
        }

        // !X => Y constant
        TriState impliedForY = forX.implies(!isXNegated(), forY);
        if (impliedForY.isKnown()) {
            boolean yResult = impliedForY.toBoolean() ^ isYNegated();
            return yResult
                            ? LogicConstantNode.tautology()
                            : (isXNegated()
                                            ? LogicNegationNode.create(forX)
                                            : forX);
        }

        // if X >= 0:
        // u < 0 || X < u ==>> X |<| u
        if (!isXNegated() && !isYNegated()) {
            LogicNode sym = simplifyComparison(forX, forY);
            if (sym != null) {
                return sym;
            }
        }

        // if X >= 0:
        // X |<| u || X < u ==>> X |<| u
        if (forX instanceof IntegerBelowNode && forY instanceof IntegerLessThanNode && !isXNegated() && !isYNegated()) {
            IntegerBelowNode xNode = (IntegerBelowNode) forX;
            IntegerLessThanNode yNode = (IntegerLessThanNode) forY;
            ValueNode xxNode = xNode.getX(); // X >= 0
            ValueNode yxNode = yNode.getX(); // X >= 0
            if (xxNode == yxNode && ((IntegerStamp) xxNode.stamp(NodeView.DEFAULT)).isPositive()) {
                ValueNode xyNode = xNode.getY(); // u
                ValueNode yyNode = yNode.getY(); // u
                if (xyNode == yyNode) {
                    return forX;
                }
            }
        }

        // if X >= 0:
        // u < 0 || (X < u || tail) ==>> X |<| u || tail
        if (forY instanceof ShortCircuitOrNode && !isXNegated() && !isYNegated()) {
            ShortCircuitOrNode yNode = (ShortCircuitOrNode) forY;
            if (!yNode.isXNegated()) {
                LogicNode sym = simplifyComparison(forX, yNode.getX());
                if (sym != null) {
                    double p1 = getShortCircuitProbability();
                    double p2 = yNode.getShortCircuitProbability();
                    return new ShortCircuitOrNode(sym, isXNegated(), yNode.getY(), yNode.isYNegated(), p1 + (1 - p1) * p2);
                }
            }
        }

        return this;
    }

    private static LogicNode simplifyComparison(LogicNode forX, LogicNode forY) {
        LogicNode sym = simplifyComparisonOrdered(forX, forY);
        if (sym == null) {
            return simplifyComparisonOrdered(forY, forX);
        }
        return sym;
    }

    private static LogicNode simplifyComparisonOrdered(LogicNode forX, LogicNode forY) {
        // if X is >= 0:
        // u < 0 || X < u ==>> X |<| u
        if (forX instanceof IntegerLessThanNode && forY instanceof IntegerLessThanNode) {
            IntegerLessThanNode xNode = (IntegerLessThanNode) forX;
            IntegerLessThanNode yNode = (IntegerLessThanNode) forY;
            ValueNode xyNode = xNode.getY(); // 0
            if (xyNode.isConstant() && IntegerStamp.OPS.getAdd().isNeutral(xyNode.asConstant())) {
                ValueNode yxNode = yNode.getX(); // X >= 0
                IntegerStamp stamp = (IntegerStamp) yxNode.stamp(NodeView.DEFAULT);
                if (stamp.isPositive()) {
                    if (xNode.getX() == yNode.getY()) {
                        ValueNode u = xNode.getX();
                        return IntegerBelowNode.create(yxNode, u, NodeView.DEFAULT);
                    }
                }
            }
        }

        return null;
    }

    private static LogicNode optimizeShortCircuit(ShortCircuitOrNode inner, boolean innerNegated, boolean matchNegated, boolean matchIsInnerX) {
        boolean innerMatchNegated;
        if (matchIsInnerX) {
            innerMatchNegated = inner.isXNegated();
        } else {
            innerMatchNegated = inner.isYNegated();
        }
        if (!innerNegated) {
            // The four digit results of the expression used in the 16 subsequent formula comments
            // correspond to results when using the following truth table for inputs a and b
            // and testing all 4 possible input combinations:
            // _ 1234
            // a 1100
            // b 1010
            if (innerMatchNegated == matchNegated) {
                // ( (!a ||!b) ||!a) => 0111 (!a ||!b)
                // ( (!a || b) ||!a) => 1011 (!a || b)
                // ( ( a ||!b) || a) => 1101 ( a ||!b)
                // ( ( a || b) || a) => 1110 ( a || b)
                // Only the inner or is relevant, the outer or never adds information.
                return inner;
            } else {
                // ( ( a || b) ||!a) => 1111 (true)
                // ( (!a ||!b) || a) => 1111 (true)
                // ( (!a || b) || a) => 1111 (true)
                // ( ( a ||!b) ||!a) => 1111 (true)
                // The result of the expression is always true.
                return LogicConstantNode.tautology();
            }
        } else {
            if (innerMatchNegated == matchNegated) {
                // (!(!a ||!b) ||!a) => 1011 (!a || b)
                // (!(!a || b) ||!a) => 0111 (!a ||!b)
                // (!( a ||!b) || a) => 1110 ( a || b)
                // (!( a || b) || a) => 1101 ( a ||!b)
                boolean newInnerXNegated = inner.isXNegated();
                boolean newInnerYNegated = inner.isYNegated();
                double newProbability = inner.getShortCircuitProbability();
                if (matchIsInnerX) {
                    newInnerYNegated = !newInnerYNegated;
                } else {
                    newInnerXNegated = !newInnerXNegated;
                    newProbability = 1.0 - newProbability;
                }
                // The expression can be transformed into a single or.
                return new ShortCircuitOrNode(inner.getX(), newInnerXNegated, inner.getY(), newInnerYNegated, newProbability);
            } else {
                // (!(!a ||!b) || a) => 1100 (a)
                // (!(!a || b) || a) => 1100 (a)
                // (!( a ||!b) ||!a) => 0011 (!a)
                // (!( a || b) ||!a) => 0011 (!a)
                LogicNode result = inner.getY();
                if (matchIsInnerX) {
                    result = inner.getX();
                }
                // Only the second part of the outer or is relevant.
                if (matchNegated) {
                    return LogicNegationNode.create(result);
                } else {
                    return result;
                }
            }
        }
    }
}
