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


package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

@NodeInfo
public abstract class AbstractFixedGuardNode extends DeoptimizingFixedWithNextNode implements Simplifiable, GuardingNode, DeoptimizingGuard {

    public static final NodeClass<AbstractFixedGuardNode> TYPE = NodeClass.create(AbstractFixedGuardNode.class);
    @Input(InputType.Condition) protected LogicNode condition;
    protected DeoptimizationReason reason;
    protected DeoptimizationAction action;
    protected Speculation speculation;
    protected boolean negated;
    protected NodeSourcePosition noDeoptSuccessorPosition;

    @Override
    public LogicNode getCondition() {
        return condition;
    }

    public LogicNode condition() {
        return getCondition();
    }

    @Override
    public void setCondition(LogicNode x, boolean negated) {
        updateUsages(condition, x);
        condition = x;
        this.negated = negated;
    }

    protected AbstractFixedGuardNode(NodeClass<? extends AbstractFixedGuardNode> c, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, Speculation speculation,
                    boolean negated) {
        super(c, StampFactory.forVoid());
        this.action = action;
        assert speculation != null;
        this.speculation = speculation;
        this.negated = negated;
        this.condition = condition;
        this.reason = deoptReason;
    }

    protected AbstractFixedGuardNode(NodeClass<? extends AbstractFixedGuardNode> c, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, Speculation speculation,
                    boolean negated, NodeSourcePosition noDeoptSuccessorPosition) {
        this(c, condition, deoptReason, action, speculation, negated);
        this.noDeoptSuccessorPosition = noDeoptSuccessorPosition;
    }

    @Override
    public DeoptimizationReason getReason() {
        return reason;
    }

    @Override
    public DeoptimizationAction getAction() {
        return action;
    }

    @Override
    public Speculation getSpeculation() {
        return speculation;
    }

    @Override
    public boolean isNegated() {
        return negated;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name && negated) {
            return "!" + super.toString(verbosity);
        } else {
            return super.toString(verbosity);
        }
    }

    @Override
    public void simplify(SimplifierTool tool) {
        while (condition instanceof LogicNegationNode) {
            LogicNegationNode negation = (LogicNegationNode) condition;
            setCondition(negation.getValue(), !negated);
        }
    }

    @SuppressWarnings("try")
    public DeoptimizeNode lowerToIf() {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            FixedNode currentNext = next();
            setNext(null);
            if (currentNext instanceof AbstractBeginNode && currentNext instanceof StateSplit && ((StateSplit) currentNext).stateAfter() != null) {
                // Force an extra BeginNode in case any guarded Nodes are inputs to the StateSplit
                BeginNode begin = graph().add(new BeginNode());
                begin.setNodeSourcePosition(getNoDeoptSuccessorPosition());
                begin.setNext(currentNext);
                currentNext = begin;
            }

            DeoptimizeNode deopt = graph().add(new DeoptimizeNode(action, reason, speculation));
            deopt.setStateBefore(stateBefore());
            IfNode ifNode;
            AbstractBeginNode noDeoptSuccessor;
            if (negated) {
                ifNode = graph().add(new IfNode(condition, deopt, currentNext, 0));
                noDeoptSuccessor = ifNode.falseSuccessor();
            } else {
                ifNode = graph().add(new IfNode(condition, currentNext, deopt, 1));
                noDeoptSuccessor = ifNode.trueSuccessor();
            }
            noDeoptSuccessor.setNodeSourcePosition(getNoDeoptSuccessorPosition());
            ((FixedWithNextNode) predecessor()).setNext(ifNode);
            this.replaceAtUsages(noDeoptSuccessor);
            GraphUtil.killWithUnusedFloatingInputs(this);

            return deopt;
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void setAction(DeoptimizationAction action) {
        this.action = action;
    }

    @Override
    public void setReason(DeoptimizationReason reason) {
        this.reason = reason;
    }

    @Override
    public NodeSourcePosition getNoDeoptSuccessorPosition() {
        return noDeoptSuccessorPosition;
    }

    @Override
    public void setNoDeoptSuccessorPosition(NodeSourcePosition noDeoptSuccessorPosition) {
        this.noDeoptSuccessorPosition = noDeoptSuccessorPosition;
    }
}
