/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.riscv64;

import static jdk.vm.ci.riscv64.RISCV64.x0;
import static jdk.vm.ci.riscv64.RISCV64.x1;
import static jdk.vm.ci.riscv64.RISCV64.x2;
import static jdk.vm.ci.riscv64.RISCV64.x3;
import static jdk.vm.ci.riscv64.RISCV64.x4;
import static jdk.vm.ci.riscv64.RISCV64.x5;
import static jdk.vm.ci.riscv64.RISCV64.x6;
import static jdk.vm.ci.riscv64.RISCV64.x7;
import static jdk.vm.ci.riscv64.RISCV64.x8;
import static jdk.vm.ci.riscv64.RISCV64.x10;
import static jdk.vm.ci.riscv64.RISCV64.x11;
import static jdk.vm.ci.riscv64.RISCV64.x12;
import static jdk.vm.ci.riscv64.RISCV64.x13;
import static jdk.vm.ci.riscv64.RISCV64.x14;
import static jdk.vm.ci.riscv64.RISCV64.x15;
import static jdk.vm.ci.riscv64.RISCV64.x16;
import static jdk.vm.ci.riscv64.RISCV64.x17;
import static jdk.vm.ci.riscv64.RISCV64.x23;
import static jdk.vm.ci.riscv64.RISCV64.x27;
import static jdk.vm.ci.riscv64.RISCV64.f10;
import static jdk.vm.ci.riscv64.RISCV64.f11;
import static jdk.vm.ci.riscv64.RISCV64.f12;
import static jdk.vm.ci.riscv64.RISCV64.f13;
import static jdk.vm.ci.riscv64.RISCV64.f14;
import static jdk.vm.ci.riscv64.RISCV64.f15;
import static jdk.vm.ci.riscv64.RISCV64.f16;
import static jdk.vm.ci.riscv64.RISCV64.f17;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.vm.ci.riscv64.RISCV64;
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

public class RISCV64HotSpotRegisterConfig implements RegisterConfig {

    private final TargetDescription target;

    private final List<Register> allocatable;

    /**
     * The caller saved registers always include all parameter registers.
     */
    private final List<Register> callerSaved;

    private final boolean allAllocatableAreCallerSaved;

    private final List<RegisterAttributes> attributesMap;

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
    public List<RegisterAttributes> getAttributesMap() {
        return attributesMap;
    }

    private final List<Register> javaGeneralParameterRegisters = List.of(x11, x12, x13, x14, x15, x16, x17, x10);
    private final List<Register> nativeGeneralParameterRegisters = List.of(x10, x11, x12, x13, x14, x15, x16, x17);
    private final List<Register> fpParameterRegisters = List.of(f10, f11, f12, f13, f14, f15, f16, f17);

    public static final Register zero = x0;
    public static final Register ra = x1;
    public static final Register sp = x2;
    public static final Register gp = x3;
    public static final Register tp = x4;
    public static final Register t0 = x5;
    public static final Register t1 = x6;
    public static final Register t2 = x7;
    public static final Register fp = x8;
    public static final Register threadRegister = x23;
    public static final Register heapBaseRegister = x27;

    private static final List<Register> reservedRegisters =List.of(zero, ra, sp, gp, tp, t0, t1, t2, fp);

    private static List<Register> initAllocatable(Architecture arch, boolean reserveForHeapBase) {
        List<Register> allRegisters = arch.getAvailableValueRegisters();
        Register[] registers = new Register[allRegisters.size() - reservedRegisters.size() - (reserveForHeapBase ? 1 : 0)];

        int idx = 0;
        for (Register reg : allRegisters) {
            if (reservedRegisters.contains(reg)) {
                // skip reserved registers
                continue;
            }
            assert !(reg.equals(zero) || reg.equals(ra) || reg.equals(sp) || reg.equals(gp) || reg.equals(tp) ||
                     reg.equals(t0) || reg.equals(t1) || reg.equals(t2) || reg.equals(fp));
            if (reserveForHeapBase && reg.equals(heapBaseRegister)) {
                // skip heap base register
                continue;
            }

            registers[idx++] = reg;
        }

        assert idx == registers.length;
        return List.of(registers);
    }

    public RISCV64HotSpotRegisterConfig(TargetDescription target, boolean useCompressedOops, boolean linuxOs) {
        this(target, initAllocatable(target.arch, useCompressedOops));
        assert callerSaved.size() >= allocatable.size();
    }

    public RISCV64HotSpotRegisterConfig(TargetDescription target, List<Register> allocatable) {
        this.target = target;
        this.allocatable = allocatable;

        Set<Register> callerSaveSet = new HashSet<>();
        callerSaveSet.addAll(allocatable);
        callerSaveSet.addAll(fpParameterRegisters);
        callerSaveSet.addAll(javaGeneralParameterRegisters);
        callerSaveSet.addAll(nativeGeneralParameterRegisters);
        callerSaved = List.copyOf(callerSaveSet);

        allAllocatableAreCallerSaved = true;
        attributesMap = RegisterAttributes.createMap(this, RISCV64.allRegisters);
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
                return fpParameterRegisters;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private CallingConvention callingConvention(List<Register> generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, HotSpotCallingConventionType type,
                    ValueKindFactory<?> valueKindFactory) {
        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentGeneral = 0;
        int currentFP = 0;
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
                    if (currentFP < fpParameterRegisters.size()) {
                        Register register = fpParameterRegisters.get(currentFP++);
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind));
                    } else if (currentGeneral < generalParameterRegisters.size()) {
                        Register register = generalParameterRegisters.get(currentGeneral++);
                        locations[i] = register.asValue(valueKindFactory.getValueKind(kind));
                    }
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                ValueKind<?> valueKind = valueKindFactory.getValueKind(kind);
                locations[i] = StackSlot.get(valueKind, currentStackOffset, !type.out);
                currentStackOffset += Math.max(valueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
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
                return x10;
            case Float:
            case Double:
                return f10;
            case Void:
            case Illegal:
                return null;
            default:
                throw new UnsupportedOperationException("no return register for type " + kind);
        }
    }

    @Override
    public Register getFrameRegister() {
        return x2;
    }

    @Override
    public String toString() {
        return String.format("Allocatable: " + getAllocatableRegisters() + "%n" + "CallerSave:  " + getCallerSaveRegisters() + "%n");
    }
}
