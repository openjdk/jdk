/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.aot.LoadConstantIndirectlyNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveConstantNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveMethodAndLoadCountersNode;
import org.graalvm.compiler.hotspot.nodes.profiling.RandomSeedNode;
import org.graalvm.compiler.hotspot.replacements.EncodedSymbolConstant;
import org.graalvm.compiler.lir.LIRFrameState;
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
     * Emits code for a {@link LoadConstantIndirectlyNode}.
     *
     * @param constant
     * @return value of loaded address in register
     */
    default Value emitLoadObjectAddress(Constant constant) {
        throw new GraalError("Emitting code to load an object address is not currently supported on %s", target().arch);
    }

    /**
     * Emits code for a {@link LoadConstantIndirectlyNode}.
     *
     * @param constant original constant
     * @param action action to perform on the metaspace object
     * @return Value of loaded address in register
     */
    default Value emitLoadMetaspaceAddress(Constant constant, HotSpotConstantLoadAction action) {
        throw new GraalError("Emitting code to load a metaspace address is not currently supported on %s", target().arch);
    }

    /**
     * Emits code for a {@link GraalHotSpotVMConfigNode}.
     *
     * @param markId id of the value to load
     * @param kind type of the value to load
     * @return value of loaded global in register
     */
    default Value emitLoadConfigValue(int markId, LIRKind kind) {
        throw new GraalError("Emitting code to load a config value is not currently supported on %s", target().arch);
    }

    /**
     * Emits code for a {@link ResolveConstantNode} to resolve a {@link HotSpotObjectConstant}.
     *
     * @param constant original constant
     * @param constantDescription a description of the string that need to be materialized (and
     *            interned) as java.lang.String, generated with {@link EncodedSymbolConstant}
     * @param frameState frame state for the runtime call
     * @return the address of the requested constant.
     */
    default Value emitObjectConstantRetrieval(Constant constant, Value constantDescription, LIRFrameState frameState) {
        throw new GraalError("Emitting code to resolve an object constant is not currently supported on %s", target().arch);
    }

    /**
     * Emits code to resolve a dynamic constant.
     *
     * @param constant original constant
     * @param frameState frame state for the runtime call
     * @return the address of the requested constant.
     */
    default Value emitResolveDynamicInvoke(Constant constant, LIRFrameState frameState) {
        throw new GraalError("Emitting code to resolve a dynamic constant is not currently supported on %s", target().arch);
    }

    /**
     * Emits code for a {@link ResolveConstantNode} to resolve a {@link HotSpotMetaspaceConstant}.
     *
     * @param constant original constant
     * @param constantDescription a symbolic description of the {@link HotSpotMetaspaceConstant}
     *            generated by {@link EncodedSymbolConstant}
     * @param frameState frame state for the runtime call
     * @return the address of the requested constant.
     */
    default Value emitMetaspaceConstantRetrieval(Constant constant, Value constantDescription, LIRFrameState frameState) {
        throw new GraalError("Emitting code to resolve a metaspace constant is not currently supported on %s", target().arch);
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
     * @return the address of the requested constant.
     */
    default Value emitResolveMethodAndLoadCounters(Constant method, Value klassHint, Value methodDescription, LIRFrameState frameState) {
        throw new GraalError("Emitting code to resolve a method and load counters is not currently supported on %s", target().arch);
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
     * @return the address of the requested constant.
     */
    default Value emitKlassInitializationAndRetrieval(Constant constant, Value constantDescription, LIRFrameState frameState) {
        throw new GraalError("Emitting code to initialize a class is not currently supported on %s", target().arch);
    }

    /**
     * Emits code for a {@link RandomSeedNode}.
     *
     * @return value of the counter
     */
    default Value emitRandomSeed() {
        throw new GraalError("Emitting code to return a random seed is not currently supported on %s", target().arch);
    }

    /**
     * Gets a stack slot for a lock at a given lock nesting depth.
     */
    VirtualStackSlot getLockSlot(int lockDepth);

    @Override
    HotSpotProviders getProviders();

}
