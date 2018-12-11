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

import static org.graalvm.compiler.nodeinfo.InputType.Guard;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;

@NodeInfo(nameTemplate = "FixedGuard(!={p#negated}) {p#reason/s}", allowedUsageTypes = Guard, size = SIZE_2, cycles = CYCLES_2)
public final class FixedGuardNode extends AbstractFixedGuardNode implements Lowerable, IterableNodeType {
    public static final NodeClass<FixedGuardNode> TYPE = NodeClass.create(FixedGuardNode.class);

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action) {
        this(condition, deoptReason, action, SpeculationLog.NO_SPECULATION, false);
    }

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated) {
        this(condition, deoptReason, action, SpeculationLog.NO_SPECULATION, negated);
    }

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated, NodeSourcePosition noDeoptSuccessorPosition) {
        this(condition, deoptReason, action, SpeculationLog.NO_SPECULATION, negated, noDeoptSuccessorPosition);
    }

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, SpeculationLog.Speculation speculation, boolean negated) {
        super(TYPE, condition, deoptReason, action, speculation, negated);
    }

    public FixedGuardNode(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, SpeculationLog.Speculation speculation, boolean negated,
                    NodeSourcePosition noDeoptSuccessorPosition) {
        super(TYPE, condition, deoptReason, action, speculation, negated, noDeoptSuccessorPosition);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        super.simplify(tool);

        if (getCondition() instanceof LogicConstantNode) {
            LogicConstantNode c = (LogicConstantNode) getCondition();
            if (c.getValue() == isNegated()) {
                FixedNode currentNext = this.next();
                if (currentNext != null) {
                    tool.deleteBranch(currentNext);
                }

                DeoptimizeNode deopt = graph().add(new DeoptimizeNode(getAction(), getReason(), getSpeculation()));
                deopt.setStateBefore(stateBefore());
                setNext(deopt);
            }
            this.replaceAtUsages(null);
            graph().removeFixed(this);
        } else if (getCondition() instanceof ShortCircuitOrNode) {
            ShortCircuitOrNode shortCircuitOr = (ShortCircuitOrNode) getCondition();
            if (isNegated() && hasNoUsages()) {
                graph().addAfterFixed(this,
                                graph().add(new FixedGuardNode(shortCircuitOr.getY(), getReason(), getAction(), getSpeculation(), !shortCircuitOr.isYNegated(), getNoDeoptSuccessorPosition())));
                graph().replaceFixedWithFixed(this,
                                graph().add(new FixedGuardNode(shortCircuitOr.getX(), getReason(), getAction(), getSpeculation(), !shortCircuitOr.isXNegated(), getNoDeoptSuccessorPosition())));
            }
        }
    }

    @SuppressWarnings("try")
    @Override
    public void lower(LoweringTool tool) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            if (graph().getGuardsStage().allowsFloatingGuards()) {
                if (getAction() != DeoptimizationAction.None) {
                    ValueNode guard = tool.createGuard(this, getCondition(), getReason(), getAction(), getSpeculation(), isNegated(), getNoDeoptSuccessorPosition()).asNode();
                    this.replaceAtUsages(guard);
                    graph().removeFixed(this);
                }
            } else {
                lowerToIf().lower(tool);
            }
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
