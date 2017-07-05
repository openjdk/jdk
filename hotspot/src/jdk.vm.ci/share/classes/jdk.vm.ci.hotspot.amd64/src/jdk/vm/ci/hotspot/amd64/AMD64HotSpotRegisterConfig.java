/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.*;

import java.util.*;

import jdk.vm.ci.amd64.*;
import jdk.vm.ci.code.*;
import jdk.vm.ci.code.CallingConvention.*;
import jdk.vm.ci.common.*;
import jdk.vm.ci.hotspot.*;
import jdk.vm.ci.meta.*;

public class AMD64HotSpotRegisterConfig implements RegisterConfig {

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

    private final Register[] javaGeneralParameterRegisters;
    private final Register[] nativeGeneralParameterRegisters;
    private final Register[] xmmParameterRegisters = {xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7};

    /*
     * Some ABIs (e.g. Windows) require a so-called "home space", that is a save area on the stack
     * to store the argument registers
     */
    private final boolean needsNativeStackHomeSpace;

    private static Register[] initAllocatable(boolean reserveForHeapBase) {
        Register[] registers = null;
        // @formatter:off
        if (reserveForHeapBase) {
            registers = new Register[] {
                        rax, rbx, rcx, rdx, /*rsp,*/ rbp, rsi, rdi, r8, r9,  r10, r11, /*r12,*/ r13, r14, /*r15, */
                        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
                        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
                      };
        } else {
            registers = new Register[] {
                        rax, rbx, rcx, rdx, /*rsp,*/ rbp, rsi, rdi, r8, r9,  r10, r11, r12, r13, r14, /*r15, */
                        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
                        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
                      };
        }
       // @formatter:on
        return registers;
    }

    public AMD64HotSpotRegisterConfig(Architecture architecture, HotSpotVMConfig config) {
        this(architecture, config, initAllocatable(config.useCompressedOops));
        assert callerSaved.length >= allocatable.length;
    }

    public AMD64HotSpotRegisterConfig(Architecture architecture, HotSpotVMConfig config, Register[] allocatable) {
        this.architecture = architecture;
        this.maxFrameSize = config.maxFrameSize;

        if (config.windowsOs) {
            javaGeneralParameterRegisters = new Register[]{rdx, r8, r9, rdi, rsi, rcx};
            nativeGeneralParameterRegisters = new Register[]{rcx, rdx, r8, r9};
            this.needsNativeStackHomeSpace = true;
        } else {
            javaGeneralParameterRegisters = new Register[]{rsi, rdx, rcx, r8, r9, rdi};
            nativeGeneralParameterRegisters = new Register[]{rdi, rsi, rdx, rcx, r8, r9};
            this.needsNativeStackHomeSpace = false;
        }

        this.allocatable = allocatable.clone();
        Set<Register> callerSaveSet = new HashSet<>();
        Collections.addAll(callerSaveSet, allocatable);
        Collections.addAll(callerSaveSet, xmmParameterRegisters);
        Collections.addAll(callerSaveSet, javaGeneralParameterRegisters);
        Collections.addAll(callerSaveSet, nativeGeneralParameterRegisters);
        callerSaved = callerSaveSet.toArray(new Register[callerSaveSet.size()]);

        allAllocatableAreCallerSaved = true;
        attributesMap = RegisterAttributes.createMap(this, AMD64.allRegisters);
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
    public CallingConvention getCallingConvention(Type type, JavaType returnType, JavaType[] parameterTypes, TargetDescription target, boolean stackOnly) {
        if (type == Type.NativeCall) {
            return callingConvention(nativeGeneralParameterRegisters, returnType, parameterTypes, type, target, stackOnly);
        }
        // On x64, parameter locations are the same whether viewed
        // from the caller or callee perspective
        return callingConvention(javaGeneralParameterRegisters, returnType, parameterTypes, type, target, stackOnly);
    }

    public Register[] getCallingConventionRegisters(Type type, JavaKind kind) {
        switch (kind) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
            case Long:
            case Object:
                return type == Type.NativeCall ? nativeGeneralParameterRegisters : javaGeneralParameterRegisters;
            case Float:
            case Double:
                return xmmParameterRegisters;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private CallingConvention callingConvention(Register[] generalParameterRegisters, JavaType returnType, JavaType[] parameterTypes, Type type, TargetDescription target, boolean stackOnly) {
        AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];

        int currentGeneral = 0;
        int currentXMM = 0;
        int currentStackOffset = type == Type.NativeCall && needsNativeStackHomeSpace ? generalParameterRegisters.length * target.wordSize : 0;

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
                case Float:
                case Double:
                    if (!stackOnly && currentXMM < xmmParameterRegisters.length) {
                        Register register = xmmParameterRegisters[currentXMM++];
                        locations[i] = register.asValue(target.getLIRKind(kind));
                    }
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere();
            }

            if (locations[i] == null) {
                LIRKind lirKind = target.getLIRKind(kind);
                locations[i] = StackSlot.get(lirKind, currentStackOffset, !type.out);
                currentStackOffset += Math.max(target.getSizeInBytes(lirKind.getPlatformKind()), target.wordSize);
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
                return rax;
            case Float:
            case Double:
                return xmm0;
            case Void:
            case Illegal:
                return null;
            default:
                throw new UnsupportedOperationException("no return register for type " + kind);
        }
    }

    @Override
    public Register getFrameRegister() {
        return rsp;
    }

    @Override
    public String toString() {
        return String.format("Allocatable: " + Arrays.toString(getAllocatableRegisters()) + "%n" + "CallerSave:  " + Arrays.toString(getCallerSaveRegisters()) + "%n");
    }
}
