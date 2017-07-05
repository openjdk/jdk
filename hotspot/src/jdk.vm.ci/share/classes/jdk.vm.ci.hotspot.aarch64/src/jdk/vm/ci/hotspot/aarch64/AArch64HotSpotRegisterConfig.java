/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r27;
import static jdk.vm.ci.aarch64.AArch64.r28;
import static jdk.vm.ci.aarch64.AArch64.r29;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.r9;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AArch64HotSpotRegisterConfig implements RegisterConfig {

    private final Architecture architecture;

    private final Register[] allocatable;

    private final int maxFrameSize;

    /**
     * The caller saved registers always include all parameter registers.
     */
    private final Register[] callerSaved;

    private final boolean allAllocatableAreCallerSaved;

    private final RegisterAttributes[] attributesMap;

    public int getMaximumFrameSize() {
        return maxFrameSize;
    }

    @Override
    public Register[] getAllocatableRegisters() {
        return allocatable.clone();
    }

    @Override
    public Register[] filterAllocatableRegisters(PlatformKind kind, Register[] registers) {
        ArrayList<Register> list = new ArrayList<>();
        for (Register reg : registers) {
            if (architecture.canStoreValue(reg.getRegisterCategory(), kind)) {
                list.add(reg);
            }
        }

        Register[] ret = list.toArray(new Register[list.size()]);
        return ret;
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        return attributesMap.clone();
    }

    private final Register[] javaGeneralParameterRegisters = {r1, r2, r3, r4, r5, r6, r7, r0};
    private final Register[] nativeGeneralParameterRegisters = {r0, r1, r2, r3, r4, r5, r6, r7};
    private final Register[] simdParameterRegisters = {v0, v1, v2, v3, v4, v5, v6, v7};

    public static final Register inlineCacheRegister = r9;

    /**
     * Vtable stubs expect the metaspace Method in r12.
     */
    public static final Register metaspaceMethodRegister = r12;

    public static final Register heapBaseRegister = r27;
    public static final Register threadRegister = r28;
    public static final Register fp = r29;

    private static Register[] initAllocatable(Architecture arch, boolean reserveForHeapBase) {
        Register[] allRegisters = arch.getAvailableValueRegisters();
        Register[] registers = new Register[allRegisters.length - (reserveForHeapBase ? 5 : 4)];

        int idx = 0;
        for (Register reg : allRegisters) {
            if (reg.equals(threadRegister) || reg.equals(fp) || reg.equals(lr) || reg.equals(sp)) {
                // skip thread register, frame pointer, link register and stack pointer
                continue;
            }
            if (reserveForHeapBase && reg.equals(heapBaseRegister)) {
                // skip heap base register
                continue;
            }

            registers[idx++] = reg;
        }

        assert idx == registers.length;
        return registers;
    }

    public AArch64HotSpotRegisterConfig(Architecture architecture, HotSpotVMConfig config) {
        this(architecture, config, initAllocatable(architecture, config.useCompressedOops));
        assert callerSaved.length >= allocatable.length;
    }

    public AArch64HotSpotRegisterConfig(Architecture architecture, HotSpotVMConfig config, Register[] allocatable) {
        this.architecture = architecture;
        this.maxFrameSize = config.maxFrameSize;

        this.allocatable = allocatable.clone();
        Set<Register> callerSaveSet = new HashSet<>();
        Collections.addAll(callerSaveSet, allocatable);
        Collections.addAll(callerSaveSet, simdParameterRegisters);
        Collections.addAll(callerSaveSet, javaGeneralParameterRegisters);
        Collections.addAll(callerSaveSet, nativeGeneralParameterRegisters);
        callerSaved = callerSaveSet.toArray(new Register[callerSaveSet.size()]);

        allAllocatableAreCallerSaved = true;
        attributesMap = RegisterAttributes.createMap(this, AArch64.allRegisters);
    }

    @Override
    public Register[] getCallerSaveRegisters() {
        return callerSaved;
    }

    public Register[] getCalleeSaveRegisters() {
        return null;
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return allAllocatableAreCallerSaved;
    }

    @Override
    public Register getRegisterForRole(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] parameterTypes, TargetDescription target) {
        HotSpotCallingConventionType hotspotType = (HotSpotCallingConventionType) type;
        if (type == HotSpotCallingConventionType.NativeCall) {
            return callingConvention(nativeGeneralParameterRegisters, returnType, parameterTypes, hotspotType, target);
        }
        // On x64, parameter locations are the same whether viewed
        // from the caller or callee perspective
        return callingConvention(javaGeneralParameterRegisters, returnType, parameterTypes, hotspotType, target);
    }

    @Override
    public Register[] getCallingConventionRegisters(Type type, JavaKind kind) {
        HotSpotCallingConventionType hotspotType = (HotSpotCallingConventionType) type;
        switch (kind) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
            case Long:
            case Object:
                return hotspotType == HotSpotCallingConventionType.NativeCall ? nativeGeneralParameterRegisters : javaGeneralParameterRegisters;
            case Float:
            case Double:
                return simdParameterRegisters;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private CallingConvention callingConvention(Register[] generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, HotSpotCallingConventionType type, TargetDescription target) {
        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentGeneral = 0;
        int currentSIMD = 0;
        int currentStackOffset = 0;

        for (int i = 0; i < parameterTypes.length; i++) {
            final JavaKind kind = parameterTypes[i].getJavaKind().getStackKind();

            switch (kind) {
                case Byte:
                case Boolean:
                case Short:
                case Char:
                case Int:
                case Long:
                case Object:
                    if (currentGeneral < generalParameterRegisters.length) {
                        Register register = generalParameterRegisters[currentGeneral++];
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                case Float:
                case Double:
                    if (currentSIMD < simdParameterRegisters.length) {
                        Register register = simdParameterRegisters[currentSIMD++];
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                LIRKind lirKind = target.getLIRKind(kind);
                locations[i] = StackSlot.get(lirKind, currentStackOffset, !type.out);
                currentStackOffset += Math.max(lirKind.getPlatformKind().getSizeInBytes(), target.wordSize);
            }
        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : returnType.getJavaKind();
        AllocatableValue returnLocation = returnKind == JavaKind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(target.getLIRKind(returnKind.getStackKind()));
        return new CallingConvention(currentStackOffset, returnLocation, locations);
    }

    @Override
    public Register getReturnRegister(JavaKind kind) {
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
            case Object:
                return r0;
            case Float:
            case Double:
                return v0;
            case Void:
            case Illegal:
                return null;
            default:
                throw new UnsupportedOperationException("no return register for type " + kind);
        }
    }

    @Override
    public Register getFrameRegister() {
        return sp;
    }

    @Override
    public String toString() {
        return String.format("Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" + "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n");
    }
}
