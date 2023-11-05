/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.foreign.abi.x64.windows;

import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.AbstractLinker.UpcallStubFactory;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.CallingSequenceBuilder;
import jdk.internal.foreign.abi.DowncallLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.VMStorage;
import jdk.internal.foreign.abi.x64.X86_64Architecture;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;

import static jdk.internal.foreign.abi.x64.X86_64Architecture.*;
import static jdk.internal.foreign.abi.x64.X86_64Architecture.Regs.*;

/**
 * For the Windowx x64 C ABI specifically, this class uses CallingSequenceBuilder
 * to translate a C FunctionDescriptor into a CallingSequence, which can then be turned into a MethodHandle.
 *
 * This includes taking care of synthetic arguments like pointers to return buffers for 'in-memory' returns.
 */
public class CallArranger {
    public static final int MAX_REGISTER_ARGUMENTS = 4;
    private static final int STACK_SLOT_SIZE = 8;

    private static final ABIDescriptor CWindows = X86_64Architecture.abiFor(
        new VMStorage[] { rcx, rdx, r8, r9 },
        new VMStorage[] { xmm0, xmm1, xmm2, xmm3 },
        new VMStorage[] { rax },
        new VMStorage[] { xmm0 },
        0,
        new VMStorage[] { rax, r10, r11 },
        new VMStorage[] { xmm4, xmm5,
                          xmm16, xmm17, xmm18, xmm19, xmm20, xmm21, xmm22, xmm23,
                          xmm24, xmm25, xmm26, xmm27, xmm28, xmm29, xmm30, xmm31 },
        16,
        32,
        r10, r11 // scratch 1 & 2
    );

    public record Bindings(
            CallingSequence callingSequence,
            boolean isInMemoryReturn) {
    }

    public static Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall) {
        return getBindings(mt, cDesc, forUpcall, LinkerOptions.empty());
    }

    public static Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall, LinkerOptions options) {
        class CallingSequenceBuilderHelper {
            final CallingSequenceBuilder csb = new CallingSequenceBuilder(CWindows, forUpcall, options);
            final BindingCalculator argCalc =
                    forUpcall ? new BoxBindingCalculator(true) : new UnboxBindingCalculator(true);
            final BindingCalculator retCalc =
                    forUpcall ? new UnboxBindingCalculator(false) : new BoxBindingCalculator(false);

            void addArgumentBindings(Class<?> carrier, MemoryLayout layout, boolean isVararg) {
                csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout, isVararg));
            }

            void setReturnBindings(Class<?> carrier, MemoryLayout layout) {
                csb.setReturnBindings(carrier, layout, retCalc.getBindings(carrier, layout, false));
            }
        }
        var csb = new CallingSequenceBuilderHelper();

        boolean returnInMemory = isInMemoryReturn(cDesc.returnLayout());
        if (returnInMemory) {
            Class<?> carrier = MemorySegment.class;
            MemoryLayout layout = SharedUtils.C_POINTER;
            csb.addArgumentBindings(carrier, layout, false);
            if (forUpcall) {
                csb.setReturnBindings(carrier, layout);
            }
        } else if (cDesc.returnLayout().isPresent()) {
            csb.setReturnBindings(mt.returnType(), cDesc.returnLayout().get());
        }

        for (int i = 0; i < mt.parameterCount(); i++) {
            csb.addArgumentBindings(mt.parameterType(i), cDesc.argumentLayouts().get(i), options.isVarargsIndex(i));
        }

        return new Bindings(csb.csb.build(), returnInMemory);
    }

    public static MethodHandle arrangeDowncall(MethodType mt, FunctionDescriptor cDesc, LinkerOptions options) {
        Bindings bindings = getBindings(mt, cDesc, false, options);

        MethodHandle handle = new DowncallLinker(CWindows, bindings.callingSequence).getBoundMethodHandle();

        if (bindings.isInMemoryReturn) {
            handle = SharedUtils.adaptDowncallForIMR(handle, cDesc, bindings.callingSequence);
        }

        return handle;
    }

    public static UpcallStubFactory arrangeUpcall(MethodType mt, FunctionDescriptor cDesc, LinkerOptions options) {
        Bindings bindings = getBindings(mt, cDesc, true, options);
        final boolean dropReturn = false; /* need the return value as well */
        return SharedUtils.arrangeUpcallHelper(mt, bindings.isInMemoryReturn, dropReturn, CWindows,
                bindings.callingSequence);
    }

    private static boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout) {
        return returnLayout
                .filter(GroupLayout.class::isInstance)
                .filter(g -> !TypeClass.isRegisterAggregate(g))
                .isPresent();
    }

    static class StorageCalculator {
        private final boolean forArguments;

        private int nRegs = 0;
        private long stackOffset = 0;

        public StorageCalculator(boolean forArguments) {
            this.forArguments = forArguments;
        }

        VMStorage nextStorage(int type) {
            if (nRegs >= MAX_REGISTER_ARGUMENTS) {
                assert forArguments : "no stack returns";
                // stack
                assert stackOffset == Utils.alignUp(stackOffset, STACK_SLOT_SIZE); // should always be aligned

                VMStorage storage = X86_64Architecture.stackStorage((short) STACK_SLOT_SIZE, (int) stackOffset);
                stackOffset += STACK_SLOT_SIZE;
                return storage;
            }
            return (forArguments
                    ? CWindows.inputStorage
                    : CWindows.outputStorage)
                    [type][nRegs++];
        }

        public VMStorage extraVarargsStorage() {
            assert forArguments;
            return CWindows.inputStorage[StorageType.INTEGER][nRegs - 1];
        }
    }

    private interface BindingCalculator {
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVararg);
    }

    static class UnboxBindingCalculator implements BindingCalculator {
        private final StorageCalculator storageCalculator;

        UnboxBindingCalculator(boolean forArguments) {
            this.storageCalculator = new StorageCalculator(forArguments);
        }

        @Override
        public List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVararg) {
            TypeClass argumentClass = TypeClass.typeClassFor(layout, isVararg);
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case STRUCT_REGISTER -> {
                    assert carrier == MemorySegment.class;
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    Class<?> type = SharedUtils.primitiveCarrierForSize(layout.byteSize(), false);
                    bindings.bufferLoad(0, type)
                            .vmStore(storage, type);
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    bindings.copy(layout)
                            .unboxAddress();
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    bindings.vmStore(storage, long.class);
                }
                case POINTER -> {
                    bindings.unboxAddress();
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    bindings.vmStore(storage, long.class);
                }
                case INTEGER -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    bindings.vmStore(storage, carrier);
                }
                case FLOAT -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.VECTOR);
                    bindings.vmStore(storage, carrier);
                }
                case VARARG_FLOAT -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.VECTOR);
                    if (!INSTANCE.isStackType(storage.type())) { // need extra for register arg
                        VMStorage extraStorage = storageCalculator.extraVarargsStorage();
                        bindings.dup()
                                .vmStore(extraStorage, carrier);
                    }

                    bindings.vmStore(storage, carrier);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }

    static class BoxBindingCalculator implements BindingCalculator {
        private final StorageCalculator storageCalculator;

        BoxBindingCalculator(boolean forArguments) {
            this.storageCalculator = new StorageCalculator(forArguments);
        }

        @Override
        public List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVararg) {
            TypeClass argumentClass = TypeClass.typeClassFor(layout, isVararg);
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case STRUCT_REGISTER -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout)
                            .dup();
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    Class<?> type = SharedUtils.primitiveCarrierForSize(layout.byteSize(), false);
                    bindings.vmLoad(storage, type)
                            .bufferStore(0, type);
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    bindings.vmLoad(storage, long.class)
                            .boxAddress(layout);
                }
                case POINTER -> {
                    AddressLayout addressLayout = (AddressLayout) layout;
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    bindings.vmLoad(storage, long.class)
                            .boxAddressRaw(Utils.pointeeByteSize(addressLayout), Utils.pointeeByteAlign(addressLayout));
                }
                case INTEGER -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    bindings.vmLoad(storage, carrier);
                }
                case FLOAT -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.VECTOR);
                    bindings.vmLoad(storage, carrier);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }
}
