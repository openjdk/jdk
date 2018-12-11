/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.vm.ci.meta.TriState;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node.IndirectCanonicalization;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;

@NodeInfo(allowedUsageTypes = {Condition}, size = SIZE_1)
public abstract class LogicNode extends FloatingNode implements IndirectCanonicalization {

    public static final NodeClass<LogicNode> TYPE = NodeClass.create(LogicNode.class);

    public LogicNode(NodeClass<? extends LogicNode> c) {
        super(c, StampFactory.forVoid());
    }

    public static LogicNode and(LogicNode a, LogicNode b, double shortCircuitProbability) {
        return and(a, false, b, false, shortCircuitProbability);
    }

    public static LogicNode and(LogicNode a, boolean negateA, LogicNode b, boolean negateB, double shortCircuitProbability) {
        StructuredGraph graph = a.graph();
        ShortCircuitOrNode notAorNotB = graph.unique(new ShortCircuitOrNode(a, !negateA, b, !negateB, shortCircuitProbability));
        return graph.unique(new LogicNegationNode(notAorNotB));
    }

    public static LogicNode or(LogicNode a, LogicNode b, double shortCircuitProbability) {
        return or(a, false, b, false, shortCircuitProbability);
    }

    public static LogicNode or(LogicNode a, boolean negateA, LogicNode b, boolean negateB, double shortCircuitProbability) {
        return a.graph().unique(new ShortCircuitOrNode(a, negateA, b, negateB, shortCircuitProbability));
    }

    public final boolean isTautology() {
        if (this instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) this;
            return logicConstantNode.getValue();
        }

        return false;
    }

    public final boolean isContradiction() {
        if (this instanceof LogicConstantNode) {
            LogicConstantNode logicConstantNode = (LogicConstantNode) this;
            return !logicConstantNode.getValue();
        }

        return false;
    }

    /**
     * Determines what this condition implies about the other.
     *
     * <ul>
     * <li>If negate(this, thisNegated) => other, returns {@link TriState#TRUE}</li>
     * <li>If negate(this, thisNegated) => !other, returns {@link TriState#FALSE}</li>
     * </ul>
     *
     * @param thisNegated whether this condition should be considered as false.
     * @param other the other condition.
     */
    public TriState implies(boolean thisNegated, LogicNode other) {
        return TriState.UNKNOWN;
    }
}
