/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;

/**
 * Node for a {@linkplain ForeignCallDescriptor foreign} call.
 */
// @formatter:off
@NodeInfo(nameTemplate = "ForeignCall#{p#descriptor/s}",
          allowedUsageTypes = Memory,
          cycles = CYCLES_2,
          cyclesRationale = "Rough estimation of the call operation itself.",
          size = SIZE_2,
          sizeRationale = "Rough estimation of the call operation itself.")
// @formatter:on
public class ForeignCallNode extends AbstractMemoryCheckpoint implements LIRLowerable, DeoptimizingNode.DeoptDuring, MemoryCheckpoint.Multi {
    public static final NodeClass<ForeignCallNode> TYPE = NodeClass.create(ForeignCallNode.class);

    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(State) protected FrameState stateDuring;
    protected final ForeignCallsProvider foreignCalls;

    protected final ForeignCallDescriptor descriptor;
    protected int bci = BytecodeFrame.UNKNOWN_BCI;

    public static boolean intrinsify(GraphBuilderContext b, ResolvedJavaMethod targetMethod, @InjectedNodeParameter Stamp returnStamp, @InjectedNodeParameter ForeignCallsProvider foreignCalls,
                    ForeignCallDescriptor descriptor, ValueNode... arguments) {
        ForeignCallNode node = new ForeignCallNode(foreignCalls, descriptor, arguments);
        node.setStamp(returnStamp);

        assert verifyDescriptor(b, targetMethod, descriptor);

        /*
         * Need to update the BCI of a ForeignCallNode so that it gets the stateDuring in the case
         * that the foreign call can deoptimize. As with all deoptimization, we need a state in a
         * non-intrinsic method.
         */
        GraphBuilderContext nonIntrinsicAncestor = b.getNonIntrinsicAncestor();
        if (nonIntrinsicAncestor != null) {
            node.setBci(nonIntrinsicAncestor.bci());
        }

        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        if (returnKind == JavaKind.Void) {
            b.add(node);
        } else {
            b.addPush(returnKind, node);
        }

        return true;
    }

    static boolean verifyDescriptor(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ForeignCallDescriptor descriptor) {
        int parameters = 1;
        for (Class<?> arg : descriptor.getArgumentTypes()) {
            ResolvedJavaType res = b.getMetaAccess().lookupJavaType(arg);
            ResolvedJavaType parameterType = (ResolvedJavaType) targetMethod.getSignature().getParameterType(parameters, targetMethod.getDeclaringClass());
            assert parameterType.equals(res) : descriptor + ": parameter " + parameters + " mismatch: " + res + " != " + parameterType;
            parameters++;
        }
        return true;
    }

    public ForeignCallNode(ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        this(TYPE, foreignCalls, descriptor, arguments);
    }

    public ForeignCallNode(ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, Stamp stamp, List<ValueNode> arguments) {
        super(TYPE, stamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
        assert descriptor.getArgumentTypes().length == this.arguments.size() : "wrong number of arguments to " + this;
    }

    public ForeignCallNode(ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, Stamp stamp) {
        super(TYPE, stamp);
        this.arguments = new NodeInputList<>(this);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
    }

    protected ForeignCallNode(NodeClass<? extends ForeignCallNode> c, ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(c, StampFactory.forKind(JavaKind.fromJavaClass(descriptor.getResultType())));
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
        assert descriptor.getArgumentTypes().length == this.arguments.size() : "wrong number of arguments to " + this;
    }

    @Override
    public boolean hasSideEffect() {
        return !foreignCalls.isReexecutable(descriptor);
    }

    public ForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public LocationIdentity[] getLocationIdentities() {
        return foreignCalls.getKilledLocations(descriptor);
    }

    protected Value[] operands(NodeLIRBuilderTool gen) {
        Value[] operands = new Value[arguments.size()];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = gen.operand(arguments.get(i));
        }
        return operands;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        ForeignCallLinkage linkage = gen.getLIRGeneratorTool().getForeignCalls().lookupForeignCall(descriptor);
        Value[] operands = operands(gen);
        Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, gen.state(this), operands);
        if (result != null) {
            gen.setResult(this, result);
        }
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert hasSideEffect() || x == null;
        super.setStateAfter(x);
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

    public int getBci() {
        return bci;
    }

    /**
     * Set the {@code bci} of the invoke bytecode for use when converting a stateAfter into a
     * stateDuring.
     */
    public void setBci(int bci) {
        assert this.bci == BytecodeFrame.UNKNOWN_BCI || this.bci == bci;
        this.bci = bci;
    }

    @Override
    public void computeStateDuring(FrameState currentStateAfter) {
        FrameState newStateDuring;
        if ((currentStateAfter.stackSize() > 0 && currentStateAfter.stackAt(currentStateAfter.stackSize() - 1) == this) ||
                        (currentStateAfter.stackSize() > 1 && currentStateAfter.stackAt(currentStateAfter.stackSize() - 2) == this)) {
            // The result of this call is on the top of stack, so roll back to the previous bci.
            assert bci != BytecodeFrame.UNKNOWN_BCI : this;
            newStateDuring = currentStateAfter.duplicateModifiedDuringCall(bci, this.getStackKind());
        } else {
            newStateDuring = currentStateAfter;
        }
        setStateDuring(newStateDuring);
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + descriptor;
        }
        return super.toString(verbosity);
    }

    @Override
    public boolean canDeoptimize() {
        return foreignCalls.canDeoptimize(descriptor);
    }

    public boolean isGuaranteedSafepoint() {
        return foreignCalls.isGuaranteedSafepoint(descriptor);
    }

    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }
}
