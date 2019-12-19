/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.common;

import java.util.ArrayDeque;

import jdk.internal.vm.compiler.collections.Pair;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Or;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.DeoptimizingGuard;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ShortCircuitOrNode;
import org.graalvm.compiler.nodes.UnaryOpLogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.TriState;

public class ConditionalEliminationUtil {

    public static final class Marks {

        final int infoElementOperations;
        final int conditions;

        public Marks(int infoElementOperations, int conditions) {
            this.infoElementOperations = infoElementOperations;
            this.conditions = conditions;
        }

        public int getInfoElementOperations() {
            return infoElementOperations;
        }

        public int getConditions() {
            return conditions;
        }
    }

    public static final class GuardedCondition {
        private final GuardingNode guard;
        private final LogicNode condition;
        private final boolean negated;

        public GuardedCondition(GuardingNode guard, LogicNode condition, boolean negated) {
            this.guard = guard;
            this.condition = condition;
            this.negated = negated;
        }

        public GuardingNode getGuard() {
            return guard;
        }

        public LogicNode getCondition() {
            return condition;
        }

        public boolean isNegated() {
            return negated;
        }
    }

    @FunctionalInterface
    public interface GuardRewirer {
        /**
         * Called if the condition could be proven to have a constant value ({@code result}) under
         * {@code guard}.
         *
         * @param guard the guard whose result is proven
         * @param result the known result of the guard
         * @param newInput new input to pi nodes depending on the new guard
         * @return whether the transformation could be applied
         */
        boolean rewire(GuardingNode guard, boolean result, Stamp guardedValueStamp, ValueNode newInput);
    }

    /**
     * Checks for safe nodes when moving pending tests up.
     */
    public static class InputFilter extends Node.EdgeVisitor {
        boolean ok;
        private ValueNode value;

        InputFilter(ValueNode value) {
            this.value = value;
            this.ok = true;
        }

        @Override
        public Node apply(Node node, Node curNode) {
            if (!ok) {
                // Abort the recursion
                return curNode;
            }
            if (!(curNode instanceof ValueNode)) {
                ok = false;
                return curNode;
            }
            ValueNode curValue = (ValueNode) curNode;
            if (curValue.isConstant() || curValue == value || curValue instanceof ParameterNode) {
                return curNode;
            }
            if (curValue instanceof BinaryNode || curValue instanceof UnaryNode) {
                curValue.applyInputs(this);
            } else {
                ok = false;
            }
            return curNode;
        }
    }

    public static final class InfoElement {
        private final Stamp stamp;
        private final GuardingNode guard;
        private final ValueNode proxifiedInput;
        private final InfoElement parent;

        public InfoElement(Stamp stamp, GuardingNode guard, ValueNode proxifiedInput, InfoElement parent) {
            this.stamp = stamp;
            this.guard = guard;
            this.proxifiedInput = proxifiedInput;
            this.parent = parent;
        }

        public InfoElement getParent() {
            return parent;
        }

        public Stamp getStamp() {
            return stamp;
        }

        public GuardingNode getGuard() {
            return guard;
        }

        public ValueNode getProxifiedInput() {
            return proxifiedInput;
        }

        @Override
        public String toString() {
            return stamp + " -> " + guard;
        }
    }

    /**
     * Get the stamp that may be used for the value for which we are registering the condition. We
     * may directly use the stamp here without restriction, because any later lookup of the
     * registered info elements is in the same chain of pi nodes.
     */
    public static Stamp getSafeStamp(ValueNode x) {
        return x.stamp(NodeView.DEFAULT);
    }

    /**
     * We can only use the stamp of a second value involved in the condition if we are sure that we
     * are not implicitly creating a dependency on a pi node that is responsible for that stamp. For
     * now, we are conservatively only using the stamps of constants. Under certain circumstances,
     * we may also be able to use the stamp of the value after skipping pi nodes (e.g., the stamp of
     * a parameter after inlining, or the stamp of a fixed node that can never be replaced with a pi
     * node via canonicalization).
     */
    public static Stamp getOtherSafeStamp(ValueNode x) {
        if (x.isConstant() || x.graph().isAfterFixedReadPhase()) {
            return x.stamp(NodeView.DEFAULT);
        }
        return x.stamp(NodeView.DEFAULT).unrestricted();
    }

    @FunctionalInterface
    public interface InfoElementProvider {
        InfoElement infoElements(ValueNode value);

        default InfoElement nextElement(InfoElement current) {
            InfoElement parent = current.getParent();
            if (parent != null) {
                return parent;
            } else {
                ValueNode proxifiedInput = current.getProxifiedInput();
                if (proxifiedInput instanceof PiNode) {
                    PiNode piNode = (PiNode) proxifiedInput;
                    return infoElements(piNode.getOriginalNode());
                }
            }
            return null;
        }
    }

    public static Pair<InfoElement, Stamp> recursiveFoldStamp(InfoElementProvider infoElementProvider, Node node) {
        if (node instanceof UnaryNode) {
            UnaryNode unary = (UnaryNode) node;
            ValueNode value = unary.getValue();
            InfoElement infoElement = infoElementProvider.infoElements(value);
            while (infoElement != null) {
                Stamp result = unary.foldStamp(infoElement.getStamp());
                if (result != null) {
                    return Pair.create(infoElement, result);
                }
                infoElement = infoElementProvider.nextElement(infoElement);
            }
        } else if (node instanceof BinaryNode) {
            BinaryNode binary = (BinaryNode) node;
            ValueNode y = binary.getY();
            ValueNode x = binary.getX();
            if (y.isConstant()) {
                InfoElement infoElement = infoElementProvider.infoElements(x);
                while (infoElement != null) {
                    Stamp result = binary.foldStamp(infoElement.getStamp(), y.stamp(NodeView.DEFAULT));
                    if (result != null) {
                        return Pair.create(infoElement, result);
                    }
                    infoElement = infoElementProvider.nextElement(infoElement);
                }
            }
        }
        return null;
    }

    /**
     * Recursively try to fold stamps within this expression using information from
     * {@link InfoElementProvider#infoElements(ValueNode)}. It's only safe to use constants and one
     * {@link InfoElement} otherwise more than one guard would be required.
     *
     * @param node
     * @return the pair of the @{link InfoElement} used and the stamp produced for the whole
     *         expression
     */
    public static Pair<InfoElement, Stamp> recursiveFoldStampFromInfo(InfoElementProvider infoElementProvider, Node node) {
        return recursiveFoldStamp(infoElementProvider, node);
    }

    public static boolean rewireGuards(GuardingNode guard, boolean result, ValueNode proxifiedInput, Stamp guardedValueStamp, GuardRewirer rewireGuardFunction) {
        return rewireGuardFunction.rewire(guard, result, guardedValueStamp, proxifiedInput);
    }

    @FunctionalInterface
    public interface GuardFolding {
        boolean foldGuard(DeoptimizingGuard thisGuard, ValueNode original, Stamp newStamp, GuardRewirer rewireGuardFunction);
    }

    public static boolean tryProveGuardCondition(InfoElementProvider infoElementProvider, ArrayDeque<GuardedCondition> conditions, GuardFolding guardFolding, DeoptimizingGuard thisGuard,
                    LogicNode node,
                    GuardRewirer rewireGuardFunction) {
        InfoElement infoElement = infoElementProvider.infoElements(node);
        while (infoElement != null) {
            Stamp stamp = infoElement.getStamp();
            JavaConstant constant = (JavaConstant) stamp.asConstant();
            if (constant != null) {
                // No proxified input and stamp required.
                return rewireGuards(infoElement.getGuard(), constant.asBoolean(), null, null, rewireGuardFunction);
            }
            infoElement = infoElementProvider.nextElement(infoElement);
        }

        for (GuardedCondition guardedCondition : conditions) {
            TriState result = guardedCondition.getCondition().implies(guardedCondition.isNegated(), node);
            if (result.isKnown()) {
                return rewireGuards(guardedCondition.getGuard(), result.toBoolean(), null, null, rewireGuardFunction);
            }
        }

        if (node instanceof UnaryOpLogicNode) {
            UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) node;
            ValueNode value = unaryLogicNode.getValue();
            infoElement = infoElementProvider.infoElements(value);
            while (infoElement != null) {
                Stamp stamp = infoElement.getStamp();
                TriState result = unaryLogicNode.tryFold(stamp);
                if (result.isKnown()) {
                    return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), infoElement.getStamp(), rewireGuardFunction);
                }
                infoElement = infoElementProvider.nextElement(infoElement);
            }
            Pair<InfoElement, Stamp> foldResult = recursiveFoldStampFromInfo(infoElementProvider, value);
            if (foldResult != null) {
                TriState result = unaryLogicNode.tryFold(foldResult.getRight());
                if (result.isKnown()) {
                    return rewireGuards(foldResult.getLeft().getGuard(), result.toBoolean(), foldResult.getLeft().getProxifiedInput(), foldResult.getRight(), rewireGuardFunction);
                }
            }
            if (thisGuard != null && guardFolding != null) {
                Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(thisGuard.isNegated());
                if (newStamp != null && guardFolding.foldGuard(thisGuard, value, newStamp, rewireGuardFunction)) {
                    return true;
                }

            }
        } else if (node instanceof BinaryOpLogicNode) {
            BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) node;
            ValueNode x = binaryOpLogicNode.getX();
            ValueNode y = binaryOpLogicNode.getY();
            infoElement = infoElementProvider.infoElements(x);
            while (infoElement != null) {
                TriState result = binaryOpLogicNode.tryFold(infoElement.getStamp(), y.stamp(NodeView.DEFAULT));
                if (result.isKnown()) {
                    return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), infoElement.getStamp(), rewireGuardFunction);
                }
                infoElement = infoElementProvider.nextElement(infoElement);
            }

            if (y.isConstant()) {
                Pair<InfoElement, Stamp> foldResult = recursiveFoldStampFromInfo(infoElementProvider, x);
                if (foldResult != null) {
                    TriState result = binaryOpLogicNode.tryFold(foldResult.getRight(), y.stamp(NodeView.DEFAULT));
                    if (result.isKnown()) {
                        return rewireGuards(foldResult.getLeft().getGuard(), result.toBoolean(), foldResult.getLeft().getProxifiedInput(), foldResult.getRight(), rewireGuardFunction);
                    }
                }
            } else {
                infoElement = infoElementProvider.infoElements(y);
                while (infoElement != null) {
                    TriState result = binaryOpLogicNode.tryFold(x.stamp(NodeView.DEFAULT), infoElement.getStamp());
                    if (result.isKnown()) {
                        return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), infoElement.getStamp(), rewireGuardFunction);
                    }
                    infoElement = infoElementProvider.nextElement(infoElement);
                }
            }

            /*
             * For complex expressions involving constants, see if it's possible to fold the tests
             * by using stamps one level up in the expression. For instance, (x + n < y) might fold
             * if something is known about x and all other values are constants. The reason for the
             * constant restriction is that if more than 1 real value is involved the code might
             * need to adopt multiple guards to have proper dependences.
             */
            if (x instanceof BinaryArithmeticNode<?> && y.isConstant()) {
                BinaryArithmeticNode<?> binary = (BinaryArithmeticNode<?>) x;
                if (binary.getY().isConstant()) {
                    infoElement = infoElementProvider.infoElements(binary.getX());
                    while (infoElement != null) {
                        Stamp newStampX = binary.foldStamp(infoElement.getStamp(), binary.getY().stamp(NodeView.DEFAULT));
                        TriState result = binaryOpLogicNode.tryFold(newStampX, y.stamp(NodeView.DEFAULT));
                        if (result.isKnown()) {
                            return rewireGuards(infoElement.getGuard(), result.toBoolean(), infoElement.getProxifiedInput(), newStampX, rewireGuardFunction);
                        }
                        infoElement = infoElementProvider.nextElement(infoElement);
                    }
                }
            }

            if (thisGuard != null && guardFolding != null && binaryOpLogicNode instanceof IntegerEqualsNode && !thisGuard.isNegated()) {
                if (y.isConstant() && x instanceof AndNode) {
                    AndNode and = (AndNode) x;
                    if (and.getY() == y) {
                        /*
                         * This 'and' proves something about some of the bits in and.getX(). It's
                         * equivalent to or'ing in the mask value since those values are known to be
                         * set.
                         */
                        BinaryOp<Or> op = ArithmeticOpTable.forStamp(x.stamp(NodeView.DEFAULT)).getOr();
                        IntegerStamp newStampX = (IntegerStamp) op.foldStamp(getSafeStamp(and.getX()), getOtherSafeStamp(y));
                        if (guardFolding.foldGuard(thisGuard, and.getX(), newStampX, rewireGuardFunction)) {
                            return true;
                        }
                    }
                }
            }

            if (thisGuard != null && guardFolding != null) {
                if (!x.isConstant()) {
                    Stamp newStampX = binaryOpLogicNode.getSucceedingStampForX(thisGuard.isNegated(), getSafeStamp(x), getOtherSafeStamp(y));
                    if (newStampX != null && guardFolding.foldGuard(thisGuard, x, newStampX, rewireGuardFunction)) {
                        return true;
                    }
                }
                if (!y.isConstant() && guardFolding != null) {
                    Stamp newStampY = binaryOpLogicNode.getSucceedingStampForY(thisGuard.isNegated(), getOtherSafeStamp(x), getSafeStamp(y));
                    if (newStampY != null && guardFolding.foldGuard(thisGuard, y, newStampY, rewireGuardFunction)) {
                        return true;
                    }
                }
            }
        } else if (node instanceof ShortCircuitOrNode) {
            final ShortCircuitOrNode shortCircuitOrNode = (ShortCircuitOrNode) node;
            return tryProveGuardCondition(infoElementProvider, conditions, guardFolding, null, shortCircuitOrNode.getX(), (guard, result, guardedValueStamp, newInput) -> {
                if (result == !shortCircuitOrNode.isXNegated()) {
                    return rewireGuards(guard, true, newInput, guardedValueStamp, rewireGuardFunction);
                } else {
                    return tryProveGuardCondition(infoElementProvider, conditions, guardFolding, null, shortCircuitOrNode.getY(), (innerGuard, innerResult, innerGuardedValueStamp, innerNewInput) -> {
                        ValueNode proxifiedInput = newInput;
                        if (proxifiedInput == null) {
                            proxifiedInput = innerNewInput;
                        } else if (innerNewInput != null) {
                            if (innerNewInput != newInput) {
                                // Cannot canonicalize due to different proxied inputs.
                                return false;
                            }
                        }
                        // Can only canonicalize if the guards are equal.
                        if (innerGuard == guard) {
                            return rewireGuards(guard, innerResult ^ shortCircuitOrNode.isYNegated(), proxifiedInput, guardedValueStamp, rewireGuardFunction);
                        }
                        return false;
                    });
                }
            });
        }

        return false;
    }

}
