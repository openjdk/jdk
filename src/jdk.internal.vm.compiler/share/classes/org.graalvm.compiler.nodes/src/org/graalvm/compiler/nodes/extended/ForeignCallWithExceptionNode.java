/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.Arrays;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.MultiKillingBeginNode;
import org.graalvm.compiler.nodes.UnreachableBeginNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Node for a {@linkplain ForeignCallDescriptor foreign} call with an {@linkplain WithExceptionNode
 * exception edge}.
 */
// @formatter:off
@NodeInfo(nameTemplate = "ForeignCallWithException#{p#descriptor/s}",
          allowedUsageTypes = Memory,
          cycles = CYCLES_2,
          cyclesRationale = "Rough estimation of the call operation itself.",
          size = SIZE_2,
          sizeRationale = "Rough estimation of the call operation itself.")
// @formatter:on
public class ForeignCallWithExceptionNode extends WithExceptionNode implements ForeignCall, Simplifiable {
    public static final NodeClass<ForeignCallWithExceptionNode> TYPE = NodeClass.create(ForeignCallWithExceptionNode.class);

    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(State) protected FrameState stateDuring;
    @OptionalInput(State) protected FrameState stateAfter;

    protected final ForeignCallDescriptor descriptor;
    protected int bci = BytecodeFrame.UNKNOWN_BCI;

    public static boolean intrinsify(GraphBuilderContext b, ResolvedJavaMethod targetMethod, @InjectedNodeParameter Stamp returnStamp, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        return ForeignCallNode.doIntrinsify(b, targetMethod, returnStamp, descriptor, arguments, true);
    }

    public ForeignCallWithExceptionNode(ForeignCallDescriptor descriptor, ValueNode... arguments) {
        this(TYPE, descriptor, arguments);
    }

    protected ForeignCallWithExceptionNode(NodeClass<? extends ForeignCallWithExceptionNode> c, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(c, StampFactory.forKind(JavaKind.fromJavaClass(descriptor.getResultType())));
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        assert descriptor.getArgumentTypes().length == this.arguments.size() : "wrong number of arguments to " + this;
    }

    @Override
    public boolean hasSideEffect() {
        return !descriptor.isReexecutable();
    }

    @Override
    public ForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState stateAfter) {
        assert hasSideEffect() || stateAfter == null;
        updateUsages(this.stateAfter(), stateAfter);
        this.stateAfter = stateAfter;
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int bci) {
        assert this.bci == BytecodeFrame.UNKNOWN_BCI || this.bci == bci;
        this.bci = bci;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + descriptor;
        }
        return super.toString(verbosity);
    }

    @Override
    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (exceptionEdge instanceof UnreachableBeginNode) {
            FixedNode replacement = replaceWithNonThrowing();
            tool.addToWorkList(replacement);
        }
    }

    @Override
    public FixedNode replaceWithNonThrowing() {
        ForeignCallNode foreignCall = this.asNode().graph().add(new ForeignCallNode(descriptor, stamp, arguments));
        foreignCall.setStateAfter(stateAfter());
        foreignCall.setStateDuring(stateDuring());

        AbstractBeginNode nextBegin = this.next;
        AbstractBeginNode oldException = this.exceptionEdge;
        graph().replaceSplitWithFixed(this, foreignCall, nextBegin);
        GraphUtil.killCFG(oldException);
        if (nextBegin instanceof MultiKillingBeginNode && Arrays.equals(((MultiKillingBeginNode) nextBegin).getKilledLocationIdentities(), this.getKilledLocationIdentities())) {
            foreignCall.graph().removeFixed(nextBegin);
        }
        return foreignCall;
    }
}
