/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.rscratch1;
import static jdk.vm.ci.aarch64.AArch64.rscratch2;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r18;
import static jdk.vm.ci.aarch64.AArch64.r27;
import static jdk.vm.ci.aarch64.AArch64.r28;
import static jdk.vm.ci.aarch64.AArch64.r29;
import static jdk.vm.ci.aarch64.AArch64.r31;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.zr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class AArch64HotSpotRegisterConfig implements RegisterConfig {

    private final TargetDescription target;

    private final List<Register> allocatable;

    /**
     * The caller saved registers always include all parameter registers.
     */
    private final List<Register> callerSaved;

    private final boolean allAllocatableAreCallerSaved;

    private final RegisterAttributes[] attributesMap;

    @Override
    public List<Register> getAllocatableRegisters() {
        return allocatable;
    }

    @Override
    public List<Register> filterAllocatableRegisters(PlatformKind kind, List<Register> registers) {
        ArrayList<Register> list = new ArrayList<>();
        for (Register reg : registers) {
            if (target.arch.canStoreValue(reg.getRegisterCategory(), kind)) {
                list.add(reg);
            }
        }

        return List.copyOf(list);
    }

    @Override
    public RegisterAttributes[] getAttributesMap() {
        return attributesMap.clone();
    }

    private final List<Register> javaGeneralParameterRegisters = List.of(r1, r2, r3, r4, r5, r6, r7, r0);
    private final List<Register> nativeGeneralParameterRegisters = List.of(r0, r1, r2, r3, r4, r5, r6, r7);
    private final List<Register> simdParameterRegisters = List.of(v0, v1, v2, v3, v4, v5, v6, v7);

    public static final Register inlineCacheRegister = rscratch2;

    /**
     * Vtable stubs expect the metaspace Method in r12.
     */
    public static final Register metaspaceMethodRegister = r12;

    /**
     * The platform ABI can use r18 to carry inter-procedural state (e.g. thread
     * context). If not defined as such by the platform ABI, it can be used as
     * additional temporary register.
     */
    public static final Register platformRegister = r18;
    public static final Register heapBaseRegister = r27;
    public static final Register threadRegister = r28;
    public static final Register fp = r29;

    private static final List<Register> reservedRegisters = List.of(rscratch1, rscratch2, threadRegister, fp, lr, r31, zr, sp);

    private static List<Register> initAllocatable(Architecture arch, boolean reserveForHeapBase, boolean canUsePlatformRegister) {
        List<Register> allRegisters = arch.getAvailableValueRegisters();
        Register[] registers = new Register[allRegisters.size() - reservedRegisters.size() - (reserveForHeapBase ? 1 : 0) - (!canUsePlatformRegister ? 1 : 0)];

        int idx = 0;
        for (Register reg : allRegisters) {
            if (reservedRegisters.contains(reg)) {
                // skip reserved registers
                continue;
            }
            if (!canUsePlatformRegister && reg.equals(platformRegister)) {
                continue;
            }
            assert !(reg.equals(threadRegister) || reg.equals(fp) || reg.equals(lr) || reg.equals(r31) || reg.equals(zr) || reg.equals(sp));
            if (reserveForHeapBase && reg.equals(heapBaseRegister)) {
                // skip heap base register
                continue;
            }

            registers[idx++] = reg;
        }

        assert idx == registers.length;
        return List.of(registers);
    }

    public AArch64HotSpotRegisterConfig(TargetDescription target, boolean useCompressedOops, boolean canUsePlatformRegister) {
        this(target, initAllocatable(target.arch, useCompressedOops, canUsePlatformRegister));
        assert callerSaved.size() >= allocatable.size();
    }

    public AArch64HotSpotRegisterConfig(TargetDescription target, List<Register> allocatable) {
        this.target = target;

        this.allocatable = allocatable;
        Set<Register> callerSaveSet = new HashSet<>();
        callerSaveSet.addAll(allocatable);
        callerSaveSet.addAll(simdParameterRegisters);
        callerSaveSet.addAll(javaGeneralParameterRegisters);
        callerSaveSet.addAll(nativeGeneralParameterRegisters);
        callerSaved = List.copyOf(callerSaveSet);

        allAllocatableAreCallerSaved = true;
        attributesMap = RegisterAttributes.createMap(this, AArch64.allRegisters);
    }

    @Override
    public List<Register> getCallerSaveRegisters() {
        return callerSaved;
    }

    @Override
    public List<Register> getCalleeSaveRegisters() {
        return null;
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return allAllocatableAreCallerSaved;
    }

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        HotSpotCallingConventionType hotspotType = (HotSpotCallingConventionType) type;
        if (type == HotSpotCallingConventionType.NativeCall) {
            return callingConvention(nativeGeneralParameterRegisters, returnType, parameterTypes, hotspotType, valueKindFactory);
        }
        // On x64, parameter locations are the same whether viewed
        // from the caller or callee perspective
        return callingConvention(javaGeneralParameterRegisters, returnType, parameterTypes, hotspotType, valueKindFactory);
    }

    @Override
    public List<Register> getCallingConventionRegisters(Type type, JavaKind kind) {
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

    private int parseStackArg(ValueKind<?> valueKind, AllocatableValue[] locations, int index, int currentStackOffset, HotSpotCallingConventionType type) {
        int kindSize = valueKind.getPlatformKind().getSizeInBytes();
        locations[index] = StackSlot.get(valueKind, currentStackOffset, !type.out);
        currentStackOffset += Math.max(kindSize, target.wordSize);
        return currentStackOffset;
    }

    private int parseDarwinNativeStackArg(ValueKind<?> valueKind, AllocatableValue[] locations, int index, int currentStackOffset, HotSpotCallingConventionType type) {
        int kindSize = valueKind.getPlatformKind().getSizeInBytes();
        if (currentStackOffset % kindSize != 0) {
            // In MacOS natural alignment is used
            // See https://developer.apple.com/documentation/xcode/writing-arm64-code-for-apple-platforms
            currentStackOffset += kindSize - currentStackOffset % kindSize;
        }
        locations[index] = StackSlot.get(valueKind, currentStackOffset, !type.out);
        // In MacOS "Function arguments may consume slots on the stack that are not multiples of 8 bytes"
        // See https://developer.apple.com/documentation/xcode/writing-arm64-code-for-apple-platforms
        currentStackOffset += kindSize;
        return currentStackOffset;
    }

    private CallingConvention callingConvention(List<Register> generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, HotSpotCallingConventionType type,
                    ValueKindFactory<?> valueKindFactory) {
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
                    if (currentGeneral < generalParameterRegisters.size()) {
                        Register register = generalParameterRegisters.get(currentGeneral++);
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind));
                    }
                    break;
                case Float:
                case Double:
                    if (currentSIMD < simdParameterRegisters.size()) {
                        Register register = simdParameterRegisters.get(currentSIMD++);
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind));
                    }
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                if (target.macOs && type == HotSpotCallingConventionType.NativeCall) {
                    currentStackOffset = parseDarwinNativeStackArg(valueKindFactory.getValueKind(kind), locations, i, currentStackOffset, type);
                } else {
                    currentStackOffset = parseStackArg(valueKindFactory.getValueKind(kind), locations, i, currentStackOffset, type);
                }
            }
        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : returnType.getJavaKind();
        AllocatableValue returnLocation = returnKind == JavaKind.Void ? Value.ILLEGAL : getReturnRegister(returnKind).asValue(valueKindFactory.getValueKind(returnKind.getStackKind()));
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
        return String.format("Allocatable: " + getAllocatableRegisters() + "%n" + "CallerSave:  " + getCallerSaveRegisters() + "%n");
    }
}
