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

import static jdk.vm.ci.sparc.SPARC.*;

import java.util.*;

import jdk.vm.ci.code.*;
import jdk.vm.ci.code.CallingConvention.*;
import jdk.vm.ci.common.*;
import jdk.vm.ci.hotspot.*;
import jdk.vm.ci.meta.*;
import jdk.vm.ci.sparc.*;

public class SPARCHotSpotRegisterConfig implements RegisterConfig {

    private final Architecture architecture;

    private final Register[] allocatable;

    private final RegisterAttributes[] attributesMap;

    @Override
    public Register[] getAllocatableRegisters() {
        return allocatable.clone();
    }

    public Register[] filterAllocatableRegisters(PlatformKind kind, Register[] registers) {
        ArrayList<Register> list = new ArrayList<>();
        for (Register reg : registers) {
            if (architecture.canStoreValue(reg.getRegisterCategory(), kind)) {
                // Special treatment for double precision
                // TODO: This is wasteful it uses only half of the registers as float.
                if (kind == JavaKind.Double) {
                    if (reg.getRegisterCategory().equals(FPUd)) {
                        list.add(reg);
                    }
                } else if (kind == JavaKind.Float) {
                    if (reg.getRegisterCategory().equals(FPUs)) {
                        list.add(reg);
                    }
                } else {
                    list.add(reg);
                }
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

    private final Register[] fpuParameterRegisters = {f0, f1, f2, f3, f4, f5, f6, f7};
    private final Register[] fpuDoubleParameterRegisters = {d0, null, d2, null, d4, null, d6, null};
    // @formatter:off
    private final Register[] callerSaveRegisters =
                   {g1, g2, g3, g4, g5, g6, g7,
                    o0, o1, o2, o3, o4, o5, o7,
                    f0,  f1,  f2,  f3,  f4,  f5,  f6,  f7,
                    f8,  f9,  f10, f11, f12, f13, f14, f15,
                    f16, f17, f18, f19, f20, f21, f22, f23,
                    f24, f25, f26, f27, f28, f29, f30, f31,
                    d32, d34, d36, d38, d40, d42, d44, d46,
                    d48, d50, d52, d54, d56, d58, d60, d62};
    // @formatter:on

    /**
     * Registers saved by the callee. This lists all L and I registers which are saved in the
     * register window.
     */
    private final Register[] calleeSaveRegisters = {l0, l1, l2, l3, l4, l5, l6, l7, i0, i1, i2, i3, i4, i5, i6, i7};

    private static Register[] initAllocatable(boolean reserveForHeapBase) {
        Register[] registers = null;
        if (reserveForHeapBase) {
            // @formatter:off
            registers = new Register[]{
                        // TODO this is not complete
                        // o7 cannot be used as register because it is always overwritten on call
                        // and the current register handler would ignore this fact if the called
                        // method still does not modify registers, in fact o7 is modified by the Call instruction
                        // There would be some extra handlin necessary to be able to handle the o7 properly for local usage
                        g1, g4, g5,
                        o0, o1, o2, o3, o4, o5, /*o6,o7,*/
                        l0, l1, l2, l3, l4, l5, l6, l7,
                        i0, i1, i2, i3, i4, i5, /*i6,*/ /*i7,*/
                        //f0, f1, f2, f3, f4, f5, f6, f7,
                        f8,  f9,  f10, f11, f12, f13, f14, f15,
                        f16, f17, f18, f19, f20, f21, f22, f23,
                        f24, f25, f26, f27, f28, f29, f30, f31,
                        d32, d34, d36, d38, d40, d42, d44, d46,
                        d48, d50, d52, d54, d56, d58, d60, d62
            };
            // @formatter:on
        } else {
            // @formatter:off
            registers = new Register[]{
                        // TODO this is not complete
                        g1, g4, g5,
                        o0, o1, o2, o3, o4, o5, /*o6, o7,*/
                        l0, l1, l2, l3, l4, l5, l6, l7,
                        i0, i1, i2, i3, i4, i5, /*i6,*/ /*i7,*/
//                        f0, f1, f2, f3, f4, f5, f6, f7
                        f8,  f9,  f10, f11, f12, f13, f14, f15,
                        f16, f17, f18, f19, f20, f21, f22, f23,
                        f24, f25, f26, f27, f28, f29, f30, f31,
                        d32, d34, d36, d38, d40, d42, d44, d46,
                        d48, d50, d52, d54, d56, d58, d60, d62
            };
            // @formatter:on
        }

        return registers;
    }

    public SPARCHotSpotRegisterConfig(TargetDescription target, HotSpotVMConfig config) {
        this(target, initAllocatable(config.useCompressedOops));
    }

    public SPARCHotSpotRegisterConfig(TargetDescription target, Register[] allocatable) {
        this.architecture = target.arch;
        this.allocatable = allocatable.clone();
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
    public CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] parameterTypes, TargetDescription target, boolean stackOnly) {
        if (type == Type.JavaCall || type == Type.NativeCall) {
            return callingConvention(cpuCallerParameterRegisters, returnType, parameterTypes, type, target, stackOnly);
        }
        if (type == Type.JavaCallee) {
            return callingConvention(cpuCalleeParameterRegisters, returnType, parameterTypes, type, target, stackOnly);
        }
        throw JVMCIError.shouldNotReachHere();
    }

    public Register[] getCallingConventionRegisters(Type type, JavaKind kind) {
        if (architecture.canStoreValue(FPUs, kind) || architecture.canStoreValue(FPUd, kind)) {
            return fpuParameterRegisters;
        }
        assert architecture.canStoreValue(CPU, kind);
        return type == Type.JavaCallee ? cpuCalleeParameterRegisters : cpuCallerParameterRegisters;
    }

    private CallingConvention callingConvention(Register[] generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, Type type, TargetDescription target, boolean stackOnly) {
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
                    if (!stackOnly && currentGeneral < generalParameterRegisters.length) {
                        Register register = generalParameterRegisters[currentGeneral++];
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                case Double:
                    if (!stackOnly && currentFloating < fpuParameterRegisters.length) {
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
                    if (!stackOnly && currentFloating < fpuParameterRegisters.length) {
                        Register register = fpuParameterRegisters[currentFloating++];
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                // Stack slot is always aligned to its size in bytes but minimum wordsize
                int typeSize = SPARC.spillSlotSize(target, kind);
                currentStackOffset = roundUp(currentStackOffset, typeSize);
                int slotOffset = currentStackOffset + SPARC.REGISTER_SAFE_AREA_SIZE;
                locations[i] = StackSlot.get(target.getLIRKind(kind.getStackKind()), slotOffset, !type.out);
                currentStackOffset += typeSize;
            }
        }

        JavaKind returnKind = returnType == null ? JavaKind.Void : returnType.getJavaKind();
        AllocatableValue returnLocation = returnKind == JavaKind.Void ? Value.ILLEGAL : getReturnRegister(returnKind, type).asValue(target.getLIRKind(returnKind.getStackKind()));
        // Space where callee may spill outgoing parameters o0...o5
        int lowerOutgoingSpace = Math.min(locations.length, 6) * target.wordSize;
        return new CallingConvention(currentStackOffset + lowerOutgoingSpace, returnLocation, locations);
    }

    private static int roundUp(int number, int mod) {
        return ((number + mod - 1) / mod) * mod;
    }

    @Override
    public Register getReturnRegister(JavaKind kind) {
        return getReturnRegister(kind, Type.JavaCallee);
    }

    private static Register getReturnRegister(JavaKind kind, Type type) {
        switch (kind) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
            case Long:
            case Object:
                return type == Type.JavaCallee ? i0 : o0;
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
