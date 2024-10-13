/*
 *  Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign.abi.x64.sysv;

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
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;

import static jdk.internal.foreign.abi.Binding.vmStore;
import static jdk.internal.foreign.abi.x64.X86_64Architecture.*;
import static jdk.internal.foreign.abi.x64.X86_64Architecture.Regs.*;

/**
 * For the SysV x64 C ABI specifically, this class uses namely CallingSequenceBuilder
 * to translate a C FunctionDescriptor into a CallingSequence, which can then be turned into a MethodHandle.
 *
 * This includes taking care of synthetic arguments like pointers to return buffers for 'in-memory' returns.
 */
public class CallArranger {
    private static final int STACK_SLOT_SIZE = 8;
    private static final int MAX_INTEGER_ARGUMENT_REGISTERS = 6;
    private static final int MAX_VECTOR_ARGUMENT_REGISTERS = 8;

    /**
     * The {@code long} native type.
     */
    public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG;

    private static final ABIDescriptor CSysV = X86_64Architecture.abiFor(
        new VMStorage[] { rdi, rsi, rdx, rcx, r8, r9, rax },
        new VMStorage[] { xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7 },
        new VMStorage[] { rax, rdx },
        new VMStorage[] { xmm0, xmm1 },
        2,
        new VMStorage[] { r10, r11 },
        new VMStorage[] { xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15,
                          xmm16, xmm17, xmm18, xmm19, xmm20, xmm21, xmm22, xmm23,
                          xmm24, xmm25, xmm26, xmm27, xmm28, xmm29, xmm30, xmm31 },
        16,
        0, //no shadow space
        r10, r11 // scratch 1 & 2
    );

    public record Bindings(
            CallingSequence callingSequence,
            boolean isInMemoryReturn,
            int nVectorArgs) {
    }

    public static Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall) {
        return getBindings(mt, cDesc, forUpcall, LinkerOptions.empty());
    }

    public static Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall, LinkerOptions options) {
        CallingSequenceBuilder csb = new CallingSequenceBuilder(CSysV, forUpcall, options);

        BindingCalculator argCalc = forUpcall ? new BoxBindingCalculator(true) : new UnboxBindingCalculator(true, options.allowsHeapAccess());
        BindingCalculator retCalc = forUpcall ? new UnboxBindingCalculator(false, false) : new BoxBindingCalculator(false);

        boolean returnInMemory = isInMemoryReturn(cDesc.returnLayout());
        if (returnInMemory) {
            Class<?> carrier = MemorySegment.class;
            MemoryLayout layout = SharedUtils.C_POINTER;
            csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout));
        } else if (cDesc.returnLayout().isPresent()) {
            Class<?> carrier = mt.returnType();
            MemoryLayout layout = cDesc.returnLayout().get();
            csb.setReturnBindings(carrier, layout, retCalc.getBindings(carrier, layout));
        }

        for (int i = 0; i < mt.parameterCount(); i++) {
            Class<?> carrier = mt.parameterType(i);
            MemoryLayout layout = cDesc.argumentLayouts().get(i);
            csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout));
        }

        if (!forUpcall && options.isVariadicFunction()) {
            //add extra binding for number of used vector registers (used for variadic calls)
            csb.addArgumentBindings(long.class, C_LONG,
                    List.of(vmStore(rax, long.class)));
        }

        return new Bindings(csb.build(), returnInMemory, argCalc.storageCalculator.nVectorReg);
    }

    public static MethodHandle arrangeDowncall(MethodType mt, FunctionDescriptor cDesc, LinkerOptions options) {
        Bindings bindings = getBindings(mt, cDesc, false, options);

        MethodHandle handle = new DowncallLinker(CSysV, bindings.callingSequence).getBoundMethodHandle();
        if (options.isVariadicFunction()) {
            handle = MethodHandles.insertArguments(handle, handle.type().parameterCount() - 1, bindings.nVectorArgs);
        }

        if (bindings.isInMemoryReturn) {
            handle = SharedUtils.adaptDowncallForIMR(handle, cDesc, bindings.callingSequence);
        }

        return handle;
    }

    public static UpcallStubFactory arrangeUpcall(MethodType mt, FunctionDescriptor cDesc, LinkerOptions options) {
        Bindings bindings = getBindings(mt, cDesc, true, options);
        final boolean dropReturn = true; /* drop return, since we don't have bindings for it */
        return SharedUtils.arrangeUpcallHelper(mt, bindings.isInMemoryReturn, dropReturn, CSysV,
                bindings.callingSequence);
    }

    private static boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout) {
        return returnLayout
                .filter(GroupLayout.class::isInstance)
                .filter(g -> TypeClass.classifyLayout(g).inMemory())
                .isPresent();
    }

    static class StorageCalculator {
        private final boolean forArguments;

        private int nVectorReg = 0;
        private int nIntegerReg = 0;
        private long stackOffset = 0;

        public StorageCalculator(boolean forArguments) {
            this.forArguments = forArguments;
        }

        private int maxRegisterArguments(int type) {
            return type == StorageType.INTEGER ?
                    MAX_INTEGER_ARGUMENT_REGISTERS :
                    MAX_VECTOR_ARGUMENT_REGISTERS;
        }

        VMStorage stackAlloc() {
            assert forArguments : "no stack returns";
            VMStorage storage = X86_64Architecture.stackStorage((short) STACK_SLOT_SIZE, (int)stackOffset);
            stackOffset += STACK_SLOT_SIZE;
            return storage;
        }

        VMStorage nextStorage(int type) {
            int registerCount = registerCount(type);
            if (registerCount < maxRegisterArguments(type)) {
                VMStorage[] source =
                    (forArguments ? CSysV.inputStorage : CSysV.outputStorage)[type];
                incrementRegisterCount(type);
                return source[registerCount];
            } else {
                return stackAlloc();
            }
        }

        VMStorage[] structStorages(TypeClass typeClass) {
            if (typeClass.inMemory()) {
                return typeClass.classes.stream().map(c -> stackAlloc()).toArray(VMStorage[]::new);
            }
            long nIntegerReg = typeClass.nIntegerRegs();

            if (this.nIntegerReg + nIntegerReg > MAX_INTEGER_ARGUMENT_REGISTERS) {
                //not enough registers - pass on stack
                return typeClass.classes.stream().map(c -> stackAlloc()).toArray(VMStorage[]::new);
            }

            long nVectorReg = typeClass.nVectorRegs();

            if (this.nVectorReg + nVectorReg > MAX_VECTOR_ARGUMENT_REGISTERS) {
                //not enough registers - pass on stack
                return typeClass.classes.stream().map(c -> stackAlloc()).toArray(VMStorage[]::new);
            }

            //ok, let's pass on registers
            VMStorage[] storage = new VMStorage[(int)(nIntegerReg + nVectorReg)];
            for (int i = 0 ; i < typeClass.classes.size() ; i++) {
                boolean sse = typeClass.classes.get(i) == ArgumentClassImpl.SSE;
                storage[i] = nextStorage(sse ? StorageType.VECTOR : StorageType.INTEGER);
            }
            return storage;
        }

        int registerCount(int type) {
            return switch (type) {
                case StorageType.INTEGER -> nIntegerReg;
                case StorageType.VECTOR -> nVectorReg;
                default -> throw new IllegalStateException();
            };
        }

        void incrementRegisterCount(int type) {
            switch (type) {
                case StorageType.INTEGER -> nIntegerReg++;
                case StorageType.VECTOR -> nVectorReg++;
                default -> throw new IllegalStateException();
            }
        }
    }

    abstract static class BindingCalculator {
        protected final StorageCalculator storageCalculator;

        protected BindingCalculator(boolean forArguments) {
            this.storageCalculator = new StorageCalculator(forArguments);
        }

        abstract List<Binding> getBindings(Class<?> carrier, MemoryLayout layout);
    }

    static class UnboxBindingCalculator extends BindingCalculator {
        private final boolean useAddressPairs;

        UnboxBindingCalculator(boolean forArguments, boolean useAddressPairs) {
            super(forArguments);
            this.useAddressPairs = useAddressPairs;
        }

        @Override
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout) {
            TypeClass argumentClass = TypeClass.classifyLayout(layout);
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass.kind()) {
                case STRUCT -> {
                    assert carrier == MemorySegment.class;
                    VMStorage[] regs = storageCalculator.structStorages(argumentClass);
                    int regIndex = 0;
                    long offset = 0;
                    while (offset < layout.byteSize()) {
                        final long copy = Math.min(layout.byteSize() - offset, 8);
                        VMStorage storage = regs[regIndex++];
                        if (offset + copy < layout.byteSize()) {
                            bindings.dup();
                        }
                        boolean useFloat = storage.type() == StorageType.VECTOR;
                        Class<?> type = SharedUtils.primitiveCarrierForSize(copy, useFloat);
                        bindings.bufferLoad(offset, type, (int) copy)
                                .vmStore(storage, type);
                        offset += copy;
                    }
                }
                case POINTER -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    if (useAddressPairs) {
                        bindings.dup()
                                .segmentBase()
                                .vmStore(storage, Object.class)
                                .segmentOffsetAllowHeap()
                                .vmStore(null, long.class);
                    } else {
                        bindings.unboxAddress();
                        bindings.vmStore(storage, long.class);
                    }
                }
                case INTEGER -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER);
                    bindings.vmStore(storage, carrier);
                }
                case FLOAT -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.VECTOR);
                    bindings.vmStore(storage, carrier);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }

    static class BoxBindingCalculator extends BindingCalculator {

        BoxBindingCalculator(boolean forArguments) {
            super(forArguments);
        }

        @Override
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout) {
            TypeClass argumentClass = TypeClass.classifyLayout(layout);
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass.kind()) {
                case STRUCT -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout);
                    VMStorage[] regs = storageCalculator.structStorages(argumentClass);
                    int regIndex = 0;
                    long offset = 0;
                    while (offset < layout.byteSize()) {
                        final long copy = Math.min(layout.byteSize() - offset, 8);
                        VMStorage storage = regs[regIndex++];
                        bindings.dup();
                        boolean useFloat = storage.type() == StorageType.VECTOR;
                        Class<?> type = SharedUtils.primitiveCarrierForSize(copy, useFloat);
                        bindings.vmLoad(storage, type)
                                .bufferStore(offset, type, (int) copy);
                        offset += copy;
                    }
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
