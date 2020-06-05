/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER;

import org.graalvm.compiler.core.amd64.AMD64NodeLIRBuilder;
import org.graalvm.compiler.core.amd64.AMD64NodeMatchRules;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.gen.DebugInfoBuilder;
import org.graalvm.compiler.hotspot.HotSpotDebugInfoBuilder;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.hotspot.HotSpotLockStack;
import org.graalvm.compiler.hotspot.HotSpotNodeLIRBuilder;
import org.graalvm.compiler.hotspot.nodes.HotSpotDirectCallTargetNode;
import org.graalvm.compiler.hotspot.nodes.HotSpotIndirectCallTargetNode;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.amd64.AMD64BreakpointOp;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeValueMap;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Value;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotNodeLIRBuilder extends AMD64NodeLIRBuilder implements HotSpotNodeLIRBuilder {

    public AMD64HotSpotNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AMD64NodeMatchRules nodeMatchRules) {
        super(graph, gen, nodeMatchRules);
        assert gen instanceof AMD64HotSpotLIRGenerator;
        assert getDebugInfoBuilder() instanceof HotSpotDebugInfoBuilder;
        ((AMD64HotSpotLIRGenerator) gen).setDebugInfoBuilder(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()));
    }

    private AMD64HotSpotLIRGenerator getGen() {
        return (AMD64HotSpotLIRGenerator) gen;
    }

    @Override
    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
        HotSpotLockStack lockStack = new HotSpotLockStack(gen.getResult().getFrameMapBuilder(), LIRKind.value(AMD64Kind.QWORD));
        return new HotSpotDebugInfoBuilder(nodeValueMap, lockStack, (HotSpotLIRGenerator) gen);
    }

    @Override
    protected void emitPrologue(StructuredGraph graph) {

        CallingConvention incomingArguments = gen.getResult().getCallingConvention();

        Value[] params = new Value[incomingArguments.getArgumentCount() + 1];
        for (int i = 0; i < params.length - 1; i++) {
            params[i] = incomingArguments.getArgument(i);
            if (isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !gen.getResult().getLIR().hasArgInCallerFrame()) {
                    gen.getResult().getLIR().setHasArgInCallerFrame();
                }
            }
        }
        params[params.length - 1] = rbp.asValue(LIRKind.value(AMD64Kind.QWORD));

        gen.emitIncomingValues(params);

        getGen().emitSaveRbp();

        getGen().append(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()).lockStack());

        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            Value paramValue = params[param.index()];
            assert paramValue.getValueKind().equals(getLIRGeneratorTool().getLIRKind(param.stamp(NodeView.DEFAULT))) : paramValue.getValueKind() + " != " + param.stamp(NodeView.DEFAULT);
            setResult(param, gen.emitMove(paramValue));
        }
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state(i);
        Register thread = getGen().getProviders().getRegisters().getThreadRegister();
        append(new AMD64HotSpotSafepointOp(info, getGen().config, this, thread));
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind.isIndirect()) {
            append(new AMD64HotspotDirectVirtualCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, getGen().config));
        } else {
            assert invokeKind.isDirect();
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
            assert resolvedMethod.isConcrete() : "Cannot make direct call to abstract method.";
            append(new AMD64HotSpotDirectStaticCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        if (callTarget instanceof HotSpotIndirectCallTargetNode) {
            Value metaspaceMethodSrc = operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod());
            Value targetAddressSrc = operand(callTarget.computedAddress());
            AllocatableValue metaspaceMethodDst = AMD64.rbx.asValue(metaspaceMethodSrc.getValueKind());
            AllocatableValue targetAddressDst = AMD64.rax.asValue(targetAddressSrc.getValueKind());
            gen.emitMove(metaspaceMethodDst, metaspaceMethodSrc);
            gen.emitMove(targetAddressDst, targetAddressSrc);
            append(new AMD64IndirectCallOp(callTarget.targetMethod(), result, parameters, temps, metaspaceMethodDst, targetAddressDst, callState));
        } else {
            super.emitIndirectCall(callTarget, result, parameters, temps, callState);
        }
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        append(new AMD64HotSpotPatchReturnAddressOp(gen.load(operand(address))));
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        Variable handler = gen.load(operand(handlerInCallerPc));
        ForeignCallLinkage linkage = gen.getForeignCalls().lookupForeignCall(EXCEPTION_HANDLER_IN_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionFixed = (RegisterValue) outgoingCc.getArgument(0);
        RegisterValue exceptionPcFixed = (RegisterValue) outgoingCc.getArgument(1);
        gen.emitMove(exceptionFixed, operand(exception));
        gen.emitMove(exceptionPcFixed, operand(exceptionPc));
        Register thread = getGen().getProviders().getRegisters().getThreadRegister();
        AMD64HotSpotJumpToExceptionHandlerInCallerOp op = new AMD64HotSpotJumpToExceptionHandlerInCallerOp(handler, exceptionFixed, exceptionPcFixed, getGen().config.threadIsMethodHandleReturnOffset,
                        thread);
        append(op);
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode i) {
        if (i.getState() != null && i.getState().bci == BytecodeFrame.AFTER_BCI) {
            i.getDebug().log("Ignoring InfopointNode for AFTER_BCI");
        } else {
            super.visitFullInfopointNode(i);
        }
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        JavaType[] sig = new JavaType[node.arguments().size()];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = node.arguments().get(i).stamp(NodeView.DEFAULT).javaType(gen.getMetaAccess());
        }

        Value[] parameters = visitInvokeArguments(gen.getRegisterConfig().getCallingConvention(HotSpotCallingConventionType.JavaCall, null, sig, gen), node.arguments());
        append(new AMD64BreakpointOp(parameters));
    }

    private ForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
        return getGen().getForeignCalls().lookupForeignCall(descriptor);
    }

    @Override
    public ForeignCallLinkage lookupGraalStub(ValueNode valueNode) {
        if (getGen().getResult().getStub() != null) {
            // Emit assembly for snippet stubs
            return null;
        }

        if (valueNode instanceof ArrayEqualsNode) {
            ArrayEqualsNode arrayEqualsNode = (ArrayEqualsNode) valueNode;
            JavaKind kind = arrayEqualsNode.getKind();
            ValueNode length = arrayEqualsNode.getLength();

            if (length.isConstant()) {
                int constantLength = length.asJavaConstant().asInt();
                if (constantLength >= 0 && constantLength * kind.getByteCount() < 2 * getGen().getMaxVectorSize()) {
                    // Yield constant-length arrays comparison assembly
                    return null;
                }
            }

            switch (kind) {
                case Boolean:
                    return lookupForeignCall(AMD64ArrayEqualsStub.STUB_BOOLEAN_ARRAY_EQUALS);
                case Byte:
                    return lookupForeignCall(AMD64ArrayEqualsStub.STUB_BYTE_ARRAY_EQUALS);
                case Char:
                    return lookupForeignCall(AMD64ArrayEqualsStub.STUB_CHAR_ARRAY_EQUALS);
                case Short:
                    return lookupForeignCall(AMD64ArrayEqualsStub.STUB_SHORT_ARRAY_EQUALS);
                case Int:
                    return lookupForeignCall(AMD64ArrayEqualsStub.STUB_INT_ARRAY_EQUALS);
                case Long:
                    return lookupForeignCall(AMD64ArrayEqualsStub.STUB_LONG_ARRAY_EQUALS);
                case Float:
                    return lookupForeignCall(AMD64ArrayEqualsStub.STUB_FLOAT_ARRAY_EQUALS);
                case Double:
                    return lookupForeignCall(AMD64ArrayEqualsStub.STUB_DOUBLE_ARRAY_EQUALS);
                default:
                    return null;
            }
        } else if (valueNode instanceof ArrayCompareToNode) {
            ArrayCompareToNode arrayCompareToNode = (ArrayCompareToNode) valueNode;
            JavaKind kind1 = arrayCompareToNode.getKind1();
            JavaKind kind2 = arrayCompareToNode.getKind2();

            if (kind1 == JavaKind.Byte) {
                if (kind2 == JavaKind.Byte) {
                    return lookupForeignCall(AMD64ArrayCompareToStub.STUB_BYTE_ARRAY_COMPARE_TO_BYTE_ARRAY);
                } else if (kind2 == JavaKind.Char) {
                    return lookupForeignCall(AMD64ArrayCompareToStub.STUB_BYTE_ARRAY_COMPARE_TO_CHAR_ARRAY);
                }
            } else if (kind1 == JavaKind.Char) {
                if (kind2 == JavaKind.Byte) {
                    return lookupForeignCall(AMD64ArrayCompareToStub.STUB_CHAR_ARRAY_COMPARE_TO_BYTE_ARRAY);
                } else if (kind2 == JavaKind.Char) {
                    return lookupForeignCall(AMD64ArrayCompareToStub.STUB_CHAR_ARRAY_COMPARE_TO_CHAR_ARRAY);
                }
            }
        } else if (valueNode instanceof ArrayRegionEqualsNode) {
            ArrayRegionEqualsNode arrayRegionEqualsNode = (ArrayRegionEqualsNode) valueNode;
            JavaKind kind1 = arrayRegionEqualsNode.getKind1();
            JavaKind kind2 = arrayRegionEqualsNode.getKind2();
            ValueNode length = arrayRegionEqualsNode.getLength();

            if (length.isConstant()) {
                int constantLength = length.asJavaConstant().asInt();
                if (constantLength >= 0 && constantLength * (Math.max(kind1.getByteCount(), kind2.getByteCount())) < 2 * getGen().getMaxVectorSize()) {
                    // Yield constant-length arrays comparison assembly
                    return null;
                }
            }

            if (kind1 == kind2) {
                switch (kind1) {
                    case Byte:
                        return lookupForeignCall(AMD64ArrayEqualsStub.STUB_BYTE_ARRAY_EQUALS_DIRECT);
                    case Char:
                        return lookupForeignCall(AMD64ArrayEqualsStub.STUB_CHAR_ARRAY_EQUALS_DIRECT);
                    default:
                        return null;
                }
            } else if (kind1 == JavaKind.Char && kind2 == JavaKind.Byte) {
                return lookupForeignCall(AMD64ArrayEqualsStub.STUB_CHAR_ARRAY_EQUALS_BYTE_ARRAY);
            }
        }

        return null;
    }
}
