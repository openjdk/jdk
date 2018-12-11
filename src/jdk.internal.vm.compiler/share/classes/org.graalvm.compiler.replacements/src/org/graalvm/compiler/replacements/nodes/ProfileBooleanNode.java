/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public class ProfileBooleanNode extends MacroStateSplitNode implements Simplifiable {
    public static final NodeClass<ProfileBooleanNode> TYPE = NodeClass.create(ProfileBooleanNode.class);
    private final SnippetReflectionProvider snippetReflection;

    public ProfileBooleanNode(SnippetReflectionProvider snippetReflection, CallTargetNode.InvokeKind invokeKind, ResolvedJavaMethod targetMethod, int bci, StampPair returnStamp,
                    ValueNode... arguments) {
        super(TYPE, invokeKind, targetMethod, bci, returnStamp, arguments);
        this.snippetReflection = snippetReflection;
    }

    ValueNode getResult() {
        return getArgument(0);
    }

    ValueNode getCounters() {
        return getArgument(1);
    }

    @Override
    public void simplify(SimplifierTool b) {
        ValueNode result = getResult();
        if (result.isConstant()) {
            replaceAtUsages(result);
            graph().removeFixed(this);
            return;
        }
        ValueNode counters = getCounters();
        if (counters.isConstant()) {
            ValueNode newResult = result;
            int[] counts = snippetReflection.asObject(int[].class, (JavaConstant) counters.asConstant());
            if (counts != null && counts.length == 2) {
                int falseCount = counts[0];
                int trueCount = counts[1];
                int totalCount = trueCount + falseCount;

                if (totalCount == 0) {
                    graph().addBeforeFixed(this,
                                    graph().addOrUniqueWithInputs(
                                                    new FixedGuardNode(LogicConstantNode.contradiction(), DeoptimizationReason.TransferToInterpreter, DeoptimizationAction.InvalidateReprofile,
                                                                    false)));
                } else if (falseCount == 0 || trueCount == 0) {
                    boolean expected = falseCount == 0;
                    LogicNode condition = graph().addOrUniqueWithInputs(
                                    IntegerEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), null, result,
                                                    ConstantNode.forBoolean(!expected),
                                                    NodeView.DEFAULT));

                    graph().addBeforeFixed(this, graph().add(new FixedGuardNode(condition, DeoptimizationReason.UnreachedCode, DeoptimizationAction.InvalidateReprofile, true)));
                    newResult = graph().unique(ConstantNode.forBoolean(expected));
                } else {
                    // We cannot use BranchProbabilityNode here since there's no guarantee
                    // the result of MethodHandleImpl.profileBoolean() is used as the
                    // test in an `if` statement (as required by BranchProbabilityNode).
                }
            }
            replaceAtUsages(newResult);
            graph().removeFixed(this);
        }
    }
}
