/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.meta.HotSpotConstantLoadAction;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.DeoptimizationFetchUnrollInfoCallNode;
import org.graalvm.compiler.hotspot.nodes.EnterUnpackFramesStackFrameNode;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.LeaveCurrentStackFrameNode;
import org.graalvm.compiler.hotspot.nodes.LeaveDeoptimizedStackFrameNode;
import org.graalvm.compiler.hotspot.nodes.LeaveUnpackFramesStackFrameNode;
import org.graalvm.compiler.hotspot.nodes.PushInterpreterFrameNode;
import org.graalvm.compiler.hotspot.nodes.SaveAllRegistersNode;
import org.graalvm.compiler.hotspot.nodes.UncommonTrapCallNode;
import org.graalvm.compiler.hotspot.nodes.aot.LoadConstantIndirectlyNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveConstantNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveMethodAndLoadCountersNode;
import org.graalvm.compiler.hotspot.nodes.profiling.RandomSeedNode;
import org.graalvm.compiler.hotspot.replacements.EncodedSymbolConstant;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

/**
 * This interface defines the contract a HotSpot backend LIR generator needs to fulfill in addition
 * to abstract methods from {@link LIRGenerator} and {@link LIRGeneratorTool}.
 */
public interface HotSpotLIRGenerator extends LIRGeneratorTool {

    /**
     * Emits an operation to make a tail call.
     *
     * @param args the arguments of the call
     * @param address the target address of the call
     */
    void emitTailcall(Value[] args, Value address);

    void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason);

    /**
     * Emits code for a {@link SaveAllRegistersNode}.
     *
     * @return a {@link SaveRegistersOp} operation
     */
    SaveRegistersOp emitSaveAllRegisters();

    /**
     * Emits code for a {@link LeaveCurrentStackFrameNode}.
     *
     * @param saveRegisterOp saved registers
     */
    default void emitLeaveCurrentStackFrame(SaveRegistersOp saveRegisterOp) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link LeaveDeoptimizedStackFrameNode}.
     *
     * @param frameSize
     * @param initialInfo
     */
    default void emitLeaveDeoptimizedStackFrame(Value frameSize, Value initialInfo) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link EnterUnpackFramesStackFrameNode}.
     *
     * @param framePc
     * @param senderSp
     * @param senderFp
     * @param saveRegisterOp
     */
    default void emitEnterUnpackFramesStackFrame(Value framePc, Value senderSp, Value senderFp, SaveRegistersOp saveRegisterOp) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link LeaveUnpackFramesStackFrameNode}.
     *
     * @param saveRegisterOp
     */
    default void emitLeaveUnpackFramesStackFrame(SaveRegistersOp saveRegisterOp) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link PushInterpreterFrameNode}.
     *
     * @param frameSize
     * @param framePc
     * @param senderSp
     * @param initialInfo
     */
    default void emitPushInterpreterFrame(Value frameSize, Value framePc, Value senderSp, Value initialInfo) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link LoadConstantIndirectlyNode}.
     *
     * @param constant
     * @return value of loaded address in register
     */
    default Value emitLoadObjectAddress(Constant constant) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link LoadConstantIndirectlyNode}.
     *
     * @param constant original constant
     * @param action action to perform on the metaspace object
     * @return Value of loaded address in register
     */
    default Value emitLoadMetaspaceAddress(Constant constant, HotSpotConstantLoadAction action) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link GraalHotSpotVMConfigNode}.
     *
     * @param markId id of the value to load
     * @param kind type of the value to load
     * @return value of loaded global in register
     */
    default Value emitLoadConfigValue(int markId, LIRKind kind) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link ResolveConstantNode} to resolve a {@link HotSpotObjectConstant}.
     *
     * @param constant original constant
     * @param constantDescription a description of the string that need to be materialized (and
     *            interned) as java.lang.String, generated with {@link EncodedSymbolConstant}
     * @param frameState frame state for the runtime call
     * @return Returns the address of the requested constant.
     */
    default Value emitObjectConstantRetrieval(Constant constant, Value constantDescription, LIRFrameState frameState) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link ResolveConstantNode} to resolve a {@link HotSpotMetaspaceConstant}.
     *
     * @param constant original constant
     * @param constantDescription a symbolic description of the {@link HotSpotMetaspaceConstant}
     *            generated by {@link EncodedSymbolConstant}
     * @param frameState frame state for the runtime call
     * @return Returns the address of the requested constant.
     */
    default Value emitMetaspaceConstantRetrieval(Constant constant, Value constantDescription, LIRFrameState frameState) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link ResolveMethodAndLoadCountersNode} to resolve a
     * {@link HotSpotMetaspaceConstant} that represents a {@link ResolvedJavaMethod} and return the
     * corresponding MethodCounters object.
     *
     * @param method original constant
     * @param klassHint a klass in which the method is declared
     * @param methodDescription is symbolic description of the constant generated by
     *            {@link EncodedSymbolConstant}
     * @param frameState frame state for the runtime call
     * @return Returns the address of the requested constant.
     */
    default Value emitResolveMethodAndLoadCounters(Constant method, Value klassHint, Value methodDescription, LIRFrameState frameState) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link ResolveConstantNode} to resolve a klass
     * {@link HotSpotMetaspaceConstant} and run static initializer.
     *
     *
     * @param constant original constant
     * @param constantDescription a symbolic description of the {@link HotSpotMetaspaceConstant}
     *            generated by {@link EncodedSymbolConstant}
     * @param frameState frame state for the runtime call
     * @return Returns the address of the requested constant.
     */
    default Value emitKlassInitializationAndRetrieval(Constant constant, Value constantDescription, LIRFrameState frameState) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link RandomSeedNode}.
     *
     * @return value of the counter
     */
    default Value emitRandomSeed() {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link UncommonTrapCallNode}.
     *
     * @param trapRequest
     * @param mode
     * @param saveRegisterOp
     * @return a {@code Deoptimization::UnrollBlock} pointer
     */
    default Value emitUncommonTrapCall(Value trapRequest, Value mode, SaveRegistersOp saveRegisterOp) {
        throw GraalError.unimplemented();
    }

    /**
     * Emits code for a {@link DeoptimizationFetchUnrollInfoCallNode}.
     *
     * @param mode
     * @param saveRegisterOp
     * @return a {@code Deoptimization::UnrollBlock} pointer
     */
    default Value emitDeoptimizationFetchUnrollInfoCall(Value mode, SaveRegistersOp saveRegisterOp) {
        throw GraalError.unimplemented();
    }

    /**
     * Gets a stack slot for a lock at a given lock nesting depth.
     */
    VirtualStackSlot getLockSlot(int lockDepth);

    @Override
    HotSpotProviders getProviders();

    Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull);

    Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull);

}
