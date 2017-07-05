/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.sparc;

import static jdk.vm.ci.meta.JavaKind.Void;
import static jdk.vm.ci.meta.Value.ILLEGAL;
import static jdk.vm.ci.sparc.SPARC.REGISTER_SAFE_AREA_SIZE;
import static jdk.vm.ci.sparc.SPARC.d0;
import static jdk.vm.ci.sparc.SPARC.d2;
import static jdk.vm.ci.sparc.SPARC.d4;
import static jdk.vm.ci.sparc.SPARC.d6;
import static jdk.vm.ci.sparc.SPARC.f0;
import static jdk.vm.ci.sparc.SPARC.f1;
import static jdk.vm.ci.sparc.SPARC.f2;
import static jdk.vm.ci.sparc.SPARC.f3;
import static jdk.vm.ci.sparc.SPARC.f4;
import static jdk.vm.ci.sparc.SPARC.f5;
import static jdk.vm.ci.sparc.SPARC.f6;
import static jdk.vm.ci.sparc.SPARC.f7;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARC.g2;
import static jdk.vm.ci.sparc.SPARC.g6;
import static jdk.vm.ci.sparc.SPARC.i0;
import static jdk.vm.ci.sparc.SPARC.i1;
import static jdk.vm.ci.sparc.SPARC.i2;
import static jdk.vm.ci.sparc.SPARC.i3;
import static jdk.vm.ci.sparc.SPARC.i4;
import static jdk.vm.ci.sparc.SPARC.i5;
import static jdk.vm.ci.sparc.SPARC.i6;
import static jdk.vm.ci.sparc.SPARC.i7;
import static jdk.vm.ci.sparc.SPARC.l0;
import static jdk.vm.ci.sparc.SPARC.l1;
import static jdk.vm.ci.sparc.SPARC.l2;
import static jdk.vm.ci.sparc.SPARC.l3;
import static jdk.vm.ci.sparc.SPARC.l4;
import static jdk.vm.ci.sparc.SPARC.l5;
import static jdk.vm.ci.sparc.SPARC.l6;
import static jdk.vm.ci.sparc.SPARC.l7;
import static jdk.vm.ci.sparc.SPARC.o0;
import static jdk.vm.ci.sparc.SPARC.o1;
import static jdk.vm.ci.sparc.SPARC.o2;
import static jdk.vm.ci.sparc.SPARC.o3;
import static jdk.vm.ci.sparc.SPARC.o4;
import static jdk.vm.ci.sparc.SPARC.o5;
import static jdk.vm.ci.sparc.SPARC.sp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
import jdk.vm.ci.sparc.SPARC;

public class SPARCHotSpotRegisterConfig implements RegisterConfig {

    private final Architecture architecture;

    private final Register[] allocatable;

    private final RegisterAttributes[] attributesMap;

    /**
     * Does native code (C++ code) spill arguments in registers to the parent frame?
     */
    private final boolean addNativeRegisterArgumentSlots;

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

    private final Register[] cpuCallerParameterRegisters = {o0, o1, o2, o3, o4, o5};
    private final Register[] cpuCalleeParameterRegisters = {i0, i1, i2, i3, i4, i5};

    private final Register[] fpuFloatParameterRegisters = {f0, f1, f2, f3, f4, f5, f6, f7};
    private final Register[] fpuDoubleParameterRegisters = {d0, null, d2, null, d4, null, d6, null};

    // @formatter:off
    private final Register[] callerSaveRegisters;

    /**
     * Registers saved by the callee. This lists all L and I registers which are saved in the
     * register window.
     */
    private final Register[] calleeSaveRegisters = {
                    l0, l1, l2, l3, l4, l5, l6, l7,
                    i0, i1, i2, i3, i4, i5, i6, i7};
    // @formatter:on

    private static final Register[] reservedRegisters = {sp, g0, g2};

    private static Register[] initAllocatable(Architecture arch, boolean reserveForHeapBase) {
        Register[] allRegisters = arch.getAvailableValueRegisters();
        Register[] registers = new Register[allRegisters.length - reservedRegisters.length - (reserveForHeapBase ? 1 : 0)];
        List<Register> reservedRegistersList = Arrays.asList(reservedRegisters);

        int idx = 0;
        for (Register reg : allRegisters) {
            if (reservedRegistersList.contains(reg)) {
                // skip reserved registers
                continue;
            }
            if (reserveForHeapBase && reg.equals(g6)) {
                // skip heap base register
                continue;
            }

            registers[idx++] = reg;
        }

        assert idx == registers.length;
        return registers;
    }

    public SPARCHotSpotRegisterConfig(Architecture arch, HotSpotVMConfig config) {
        this(arch, initAllocatable(arch, config.useCompressedOops), config);
    }

    public SPARCHotSpotRegisterConfig(Architecture arch, Register[] allocatable, HotSpotVMConfig config) {
        this.architecture = arch;
        this.allocatable = allocatable.clone();
        this.addNativeRegisterArgumentSlots = config.linuxOs;
        HashSet<Register> callerSaveSet = new HashSet<>();
        Collections.addAll(callerSaveSet, arch.getAvailableValueRegisters());
        for (Register cs : calleeSaveRegisters) {
            callerSaveSet.remove(cs);
        }
        this.callerSaveRegisters = callerSaveSet.toArray(new Register[callerSaveSet.size()]);
        attributesMap = RegisterAttributes.createMap(this, SPARC.allRegisters);
    }

    @Override
    public Register[] getCallerSaveRegisters() {
        return callerSaveRegisters;
    }

    public Register[] getCalleeSaveRegisters() {
        return calleeSaveRegisters;
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        return false;
    }

    @Override
    public Register getRegisterForRole(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] parameterTypes, TargetDescription target) {
        HotSpotCallingConventionType hotspotType = (HotSpotCallingConventionType) type;
        if (type == HotSpotCallingConventionType.JavaCall || type == HotSpotCallingConventionType.NativeCall) {
            return callingConvention(cpuCallerParameterRegisters, returnType, parameterTypes, hotspotType, target);
        }
        if (type == HotSpotCallingConventionType.JavaCallee) {
            return callingConvention(cpuCalleeParameterRegisters, returnType, parameterTypes, hotspotType, target);
        }
        throw JVMCIError.shouldNotReachHere();
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
                return hotspotType == HotSpotCallingConventionType.JavaCallee ? cpuCalleeParameterRegisters : cpuCallerParameterRegisters;
            case Double:
            case Float:
                return fpuFloatParameterRegisters;
            default:
                throw JVMCIError.shouldNotReachHere("Unknown JavaKind " + kind);
        }
    }

    private CallingConvention callingConvention(Register[] generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, HotSpotCallingConventionType type, TargetDescription target) {
        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentGeneral = 0;
        int currentFloating = 0;
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
                case Double:
                    if (currentFloating < fpuFloatParameterRegisters.length) {
                        if (currentFloating % 2 != 0) {
                            // Make register number even to be a double reg
                            currentFloating++;
                        }
                        Register register = fpuDoubleParameterRegisters[currentFloating];
                        currentFloating += 2; // Only every second is a double register
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                case Float:
                    if (currentFloating < fpuFloatParameterRegisters.length) {
                        Register register = fpuFloatParameterRegisters[currentFloating++];
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                LIRKind lirKind = target.getLIRKind(kind);
                // Stack slot is always aligned to its size in bytes but minimum wordsize
                int typeSize = lirKind.getPlatformKind().getSizeInBytes();
                currentStackOffset = roundUp(currentStackOffset, typeSize);
                int slotOffset = currentStackOffset + REGISTER_SAFE_AREA_SIZE;
                locations[i] = StackSlot.get(lirKind, slotOffset, !type.out);
                currentStackOffset += typeSize;
            }
        }

        JavaKind returnKind = returnType == null ? Void : returnType.getJavaKind();
        AllocatableValue returnLocation = returnKind == Void ? ILLEGAL : getReturnRegister(returnKind, type).asValue(target.getLIRKind(returnKind.getStackKind()));

        int outArgSpillArea;
        if (type == HotSpotCallingConventionType.NativeCall && addNativeRegisterArgumentSlots) {
            // Space for native callee which may spill our outgoing arguments
            outArgSpillArea = Math.min(locations.length, generalParameterRegisters.length) * target.wordSize;
        } else {
            outArgSpillArea = 0;
        }
        return new CallingConvention(currentStackOffset + outArgSpillArea, returnLocation, locations);
    }

    private static int roundUp(int number, int mod) {
        return ((number + mod - 1) / mod) * mod;
    }

    @Override
    public Register getReturnRegister(JavaKind kind) {
        return getReturnRegister(kind, HotSpotCallingConventionType.JavaCallee);
    }

    private static Register getReturnRegister(JavaKind kind, HotSpotCallingConventionType type) {
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
            case Object:
                return type == HotSpotCallingConventionType.JavaCallee ? i0 : o0;
            case Float:
                return f0;
            case Double:
                return d0;
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
