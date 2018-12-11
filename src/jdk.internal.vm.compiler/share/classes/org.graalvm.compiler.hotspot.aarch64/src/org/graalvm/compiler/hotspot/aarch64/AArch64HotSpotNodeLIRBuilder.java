/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.fp;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.inlineCacheRegister;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.metaspaceMethodRegister;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER;

import org.graalvm.compiler.core.aarch64.AArch64NodeLIRBuilder;
import org.graalvm.compiler.core.aarch64.AArch64NodeMatchRules;
import org.graalvm.compiler.core.common.LIRKind;
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
import org.graalvm.compiler.lir.aarch64.AArch64BreakpointOp;
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

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Value;

/**
 * LIR generator specialized for AArch64 HotSpot.
 */
public class AArch64HotSpotNodeLIRBuilder extends AArch64NodeLIRBuilder implements HotSpotNodeLIRBuilder {

    public AArch64HotSpotNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AArch64NodeMatchRules nodeMatchRules) {
        super(graph, gen, nodeMatchRules);
        assert gen instanceof AArch64HotSpotLIRGenerator;
        assert getDebugInfoBuilder() instanceof HotSpotDebugInfoBuilder;
        ((AArch64HotSpotLIRGenerator) gen).setDebugInfoBuilder(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()));
    }

    @Override
    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
        HotSpotLockStack lockStack = new HotSpotLockStack(gen.getResult().getFrameMapBuilder(), LIRKind.value(AArch64Kind.QWORD));
        return new HotSpotDebugInfoBuilder(nodeValueMap, lockStack, (HotSpotLIRGenerator) gen);
    }

    private AArch64HotSpotLIRGenerator getGen() {
        return (AArch64HotSpotLIRGenerator) gen;
    }

    @Override
    protected void emitPrologue(StructuredGraph graph) {
        CallingConvention incomingArguments = gen.getResult().getCallingConvention();
        Value[] params = new Value[incomingArguments.getArgumentCount() + 2];
        for (int i = 0; i < incomingArguments.getArgumentCount(); i++) {
            params[i] = incomingArguments.getArgument(i);
            if (isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !gen.getResult().getLIR().hasArgInCallerFrame()) {
                    gen.getResult().getLIR().setHasArgInCallerFrame();
                }
            }
        }
        params[params.length - 2] = fp.asValue(LIRKind.value(AArch64Kind.QWORD));
        params[params.length - 1] = lr.asValue(LIRKind.value(AArch64Kind.QWORD));

        gen.emitIncomingValues(params);

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
        Variable scratch = gen.newVariable(LIRKind.value(getGen().target().arch.getWordKind()));
        append(new AArch64HotSpotSafepointOp(info, getGen().config, thread, scratch));
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind.isIndirect()) {
            append(new AArch64HotSpotDirectVirtualCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, getGen().config));
        } else {
            assert invokeKind.isDirect();
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
            assert resolvedMethod.isConcrete() : "Cannot make direct call to abstract method.";
            append(new AArch64HotSpotDirectStaticCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, getGen().config));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        Value metaspaceMethodSrc = operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod());
        Value targetAddressSrc = operand(callTarget.computedAddress());
        AllocatableValue metaspaceMethodDst = metaspaceMethodRegister.asValue(metaspaceMethodSrc.getValueKind());
        AllocatableValue targetAddressDst = inlineCacheRegister.asValue(targetAddressSrc.getValueKind());
        gen.emitMove(metaspaceMethodDst, metaspaceMethodSrc);
        gen.emitMove(targetAddressDst, targetAddressSrc);
        append(new AArch64IndirectCallOp(callTarget.targetMethod(), result, parameters, temps, metaspaceMethodDst, targetAddressDst, callState, getGen().config));
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        append(new AArch64HotSpotPatchReturnAddressOp(gen.load(operand(address))));
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
        AArch64HotSpotJumpToExceptionHandlerInCallerOp op = new AArch64HotSpotJumpToExceptionHandlerInCallerOp(handler, exceptionFixed, exceptionPcFixed,
                        getGen().config.threadIsMethodHandleReturnOffset, thread, getGen().config);
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
        append(new AArch64BreakpointOp(parameters));
    }
}
