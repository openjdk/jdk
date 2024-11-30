/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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
 *
 */

package jdk.internal.foreign.abi.riscv64.linux;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.AbstractLinker.UpcallStubFactory;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.CallingSequenceBuilder;
import jdk.internal.foreign.abi.DowncallLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.VMStorage;
import jdk.internal.foreign.Utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static jdk.internal.foreign.abi.riscv64.linux.TypeClass.*;
import static jdk.internal.foreign.abi.riscv64.RISCV64Architecture.*;
import static jdk.internal.foreign.abi.riscv64.RISCV64Architecture.Regs.*;

/**
 * For the RISCV64 C ABI specifically, this class uses CallingSequenceBuilder
 * to translate a C FunctionDescriptor into a CallingSequence, which can then be turned into a MethodHandle.
 *
 * This includes taking care of synthetic arguments like pointers to return buffers for 'in-memory' returns.
 */
public class LinuxRISCV64CallArranger {
    private static final int STACK_SLOT_SIZE = 8;
    public static final int MAX_REGISTER_ARGUMENTS = 8;
    private static final ABIDescriptor CLinux = abiFor(
            new VMStorage[]{x10, x11, x12, x13, x14, x15, x16, x17},
            new VMStorage[]{f10, f11, f12, f13, f14, f15, f16, f17},
            new VMStorage[]{x10, x11},
            new VMStorage[]{f10, f11},
            new VMStorage[]{x5, x6, x7, x28, x29, x30, x31},
            new VMStorage[]{f0, f1, f2, f3, f4, f5, f6, f7, f28, f29, f30, f31},
            16, // stackAlignment
            0,  // no shadow space
            x28, x29 // scratch 1 & 2
    );

    public record Bindings(CallingSequence callingSequence,
                           boolean isInMemoryReturn) {
    }

    public static Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall) {
        return getBindings(mt, cDesc, forUpcall, LinkerOptions.empty());
    }

    public static Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall, LinkerOptions options) {
        CallingSequenceBuilder csb = new CallingSequenceBuilder(CLinux, forUpcall, options);
        BindingCalculator argCalc = forUpcall ? new BoxBindingCalculator(true) : new UnboxBindingCalculator(true, options.allowsHeapAccess());
        BindingCalculator retCalc = forUpcall ? new UnboxBindingCalculator(false, false) : new BoxBindingCalculator(false);

        boolean returnInMemory = isInMemoryReturn(cDesc.returnLayout());
        if (returnInMemory) {
            Class<?> carrier = MemorySegment.class;
            MemoryLayout layout = SharedUtils.C_POINTER;
            csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout, false));
        } else if (cDesc.returnLayout().isPresent()) {
            Class<?> carrier = mt.returnType();
            MemoryLayout layout = cDesc.returnLayout().get();
            csb.setReturnBindings(carrier, layout, retCalc.getBindings(carrier, layout, false));
        }

        for (int i = 0; i < mt.parameterCount(); i++) {
            Class<?> carrier = mt.parameterType(i);
            MemoryLayout layout = cDesc.argumentLayouts().get(i);
            boolean isVar = options.isVarargsIndex(i);
            csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout, isVar));
        }

        return new Bindings(csb.build(), returnInMemory);
    }

    public static MethodHandle arrangeDowncall(MethodType mt, FunctionDescriptor cDesc, LinkerOptions options) {
        Bindings bindings = getBindings(mt, cDesc, false, options);

        MethodHandle handle = new DowncallLinker(CLinux, bindings.callingSequence).getBoundMethodHandle();

        if (bindings.isInMemoryReturn) {
            handle = SharedUtils.adaptDowncallForIMR(handle, cDesc, bindings.callingSequence);
        }

        return handle;
    }

    public static UpcallStubFactory arrangeUpcall(MethodType mt, FunctionDescriptor cDesc, LinkerOptions options) {
        Bindings bindings = getBindings(mt, cDesc, true, options);
        final boolean dropReturn = true; /* drop return, since we don't have bindings for it */
        return SharedUtils.arrangeUpcallHelper(mt, bindings.isInMemoryReturn, dropReturn, CLinux,
                bindings.callingSequence);
    }

    private static boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout) {
        return returnLayout
                .filter(GroupLayout.class::isInstance)
                .filter(g -> TypeClass.classifyLayout(g) == TypeClass.STRUCT_REFERENCE)
                .isPresent();
    }

    static class StorageCalculator {
        private final boolean forArguments;
        // next available register index. 0=integerRegIdx, 1=floatRegIdx
        private final int IntegerRegIdx = 0;
        private final int FloatRegIdx = 1;
        private final int[] nRegs = {0, 0};

        private long stackOffset = 0;

        public StorageCalculator(boolean forArguments) {
            this.forArguments = forArguments;
        }

        // Aggregates or scalars passed on the stack are aligned to the greatest of
        // the type alignment and XLEN bits, but never more than the stack alignment.
        void alignStack(long alignment) {
            alignment = Utils.alignUp(Math.clamp(alignment, STACK_SLOT_SIZE, 16), STACK_SLOT_SIZE);
            stackOffset = Utils.alignUp(stackOffset, alignment);
        }

        VMStorage stackAlloc() {
            assert forArguments : "no stack returns";
            VMStorage storage = stackStorage((short) STACK_SLOT_SIZE, (int) stackOffset);
            stackOffset += STACK_SLOT_SIZE;
            return storage;
        }

        Optional<VMStorage> regAlloc(int storageClass) {
            if (nRegs[storageClass] < MAX_REGISTER_ARGUMENTS) {
                VMStorage[] source = (forArguments ? CLinux.inputStorage : CLinux.outputStorage)[storageClass];
                Optional<VMStorage> result = Optional.of(source[nRegs[storageClass]]);
                nRegs[storageClass] += 1;
                return result;
            }
            return Optional.empty();
        }

        VMStorage getStorage(int storageClass) {
            Optional<VMStorage> storage = regAlloc(storageClass);
            if (storage.isPresent()) {
                return storage.get();
            }
            // If storageClass is StorageType.FLOAT, and no floating-point register is available,
            // try to allocate an integer register.
            if (storageClass == StorageType.FLOAT) {
                storage = regAlloc(StorageType.INTEGER);
                if (storage.isPresent()) {
                    return storage.get();
                }
            }
            return stackAlloc();
        }

        VMStorage[] getStorages(MemoryLayout layout, boolean isVariadicArg) {
            int regCnt = (int) SharedUtils.alignUp(layout.byteSize(), 8) / 8;
            if (isVariadicArg && layout.byteAlignment() == 16 && layout.byteSize() <= 16) {
                alignStorage();
                // Two registers or stack slots will be allocated, even layout.byteSize <= 8B.
                regCnt = 2;
            }
            VMStorage[] storages = new VMStorage[regCnt];
            for (int i = 0; i < regCnt; i++) {
                // use integer calling convention.
                storages[i] = getStorage(StorageType.INTEGER);
            }
            return storages;
        }

        boolean regsAvailable(int integerRegs, int floatRegs) {
            return nRegs[IntegerRegIdx] + integerRegs <= MAX_REGISTER_ARGUMENTS &&
                   nRegs[FloatRegIdx] + floatRegs <= MAX_REGISTER_ARGUMENTS;
        }

        // Variadic arguments with 2 * XLEN-bit alignment and size at most 2 * XLEN bits
        // are passed in an aligned register pair (i.e., the first register in the pair
        // is even-numbered), or on the stack by value if none is available.
        // After a variadic argument has been passed on the stack, all future arguments
        // will also be passed on the stack.
        void alignStorage() {
            if (nRegs[IntegerRegIdx] + 2 <= MAX_REGISTER_ARGUMENTS) {
                nRegs[IntegerRegIdx] = (nRegs[IntegerRegIdx] + 1) & -2;
            } else {
                nRegs[IntegerRegIdx] = MAX_REGISTER_ARGUMENTS;
                stackOffset = Utils.alignUp(stackOffset, 16);
            }
        }

        @Override
        public String toString() {
            String nReg = "iReg: " + nRegs[IntegerRegIdx] + ", fReg: " + nRegs[FloatRegIdx];
            String stack = ", stackOffset: " + stackOffset;
            return "{" + nReg + stack + "}";
        }
    }

    abstract static class BindingCalculator {
        protected final StorageCalculator storageCalculator;

        @Override
        public String toString() {
            return storageCalculator.toString();
        }

        protected BindingCalculator(boolean forArguments) {
            this.storageCalculator = new LinuxRISCV64CallArranger.StorageCalculator(forArguments);
        }

        abstract List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVariadicArg);

        // When handling variadic part, integer calling convention should be used.
        static final Map<TypeClass, TypeClass> conventionConverterMap =
                Map.ofEntries(Map.entry(FLOAT, INTEGER),
                              Map.entry(STRUCT_REGISTER_F, STRUCT_REGISTER_X),
                              Map.entry(STRUCT_REGISTER_XF, STRUCT_REGISTER_X));
    }

    static final class UnboxBindingCalculator extends BindingCalculator {
        protected final boolean forArguments;
        private final boolean useAddressPairs;

        UnboxBindingCalculator(boolean forArguments, boolean useAddressPairs) {
            super(forArguments);
            this.forArguments = forArguments;
            this.useAddressPairs = useAddressPairs;
        }

        @Override
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVariadicArg) {
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            if (isVariadicArg) {
                typeClass = BindingCalculator.conventionConverterMap.getOrDefault(typeClass, typeClass);
            }
            return getBindings(carrier, layout, typeClass, isVariadicArg);
        }

        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, TypeClass argumentClass, boolean isVariadicArg) {
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case INTEGER -> {
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER);
                    bindings.vmStore(storage, carrier);
                }
                case FLOAT -> {
                    VMStorage storage = storageCalculator.getStorage(StorageType.FLOAT);
                    bindings.vmStore(storage, carrier);
                }
                case POINTER -> {
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER);
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
                case STRUCT_REGISTER_X -> {
                    assert carrier == MemorySegment.class;

                    // When no register is available, struct will be passed by stack.
                    // Before allocation, stack must be aligned.
                    if (!storageCalculator.regsAvailable(1, 0)) {
                        storageCalculator.alignStack(layout.byteAlignment());
                    }
                    VMStorage[] locations = storageCalculator.getStorages(layout, isVariadicArg);
                    int locIndex = 0;
                    long offset = 0;
                    while (offset < layout.byteSize()) {
                        final long copy = Math.min(layout.byteSize() - offset, 8);
                        VMStorage storage = locations[locIndex++];
                        Class<?> type = SharedUtils.primitiveCarrierForSize(copy, false);
                        if (offset + copy < layout.byteSize()) {
                            bindings.dup();
                        }
                        bindings.bufferLoad(offset, type, (int) copy)
                                .vmStore(storage, type);
                        offset += copy;
                    }
                }
                case STRUCT_REGISTER_F -> {
                    assert carrier == MemorySegment.class;
                    List<FlattenedFieldDesc> descs = getFlattenedFields((GroupLayout) layout);
                    if (storageCalculator.regsAvailable(0, descs.size())) {
                        for (int i = 0; i < descs.size(); i++) {
                            FlattenedFieldDesc desc = descs.get(i);
                            Class<?> type = desc.layout().carrier();
                            VMStorage storage = storageCalculator.getStorage(StorageType.FLOAT);
                            if (i < descs.size() - 1) {
                                bindings.dup();
                            }
                            bindings.bufferLoad(desc.offset(), type)
                                    .vmStore(storage, type);
                        }
                    } else {
                        // If there is not enough register can be used, then fall back to integer calling convention.
                        return getBindings(carrier, layout, STRUCT_REGISTER_X, isVariadicArg);
                    }
                }
                case STRUCT_REGISTER_XF -> {
                    assert carrier == MemorySegment.class;
                    if (storageCalculator.regsAvailable(1, 1)) {
                        List<FlattenedFieldDesc> descs = getFlattenedFields((GroupLayout) layout);
                        for (int i = 0; i < 2; i++) {
                            FlattenedFieldDesc desc = descs.get(i);
                            int storageClass;
                            if (desc.typeClass() == INTEGER) {
                                storageClass = StorageType.INTEGER;
                            } else {
                                storageClass = StorageType.FLOAT;
                            }
                            VMStorage storage = storageCalculator.getStorage(storageClass);
                            Class<?> type = desc.layout().carrier();
                            if (i < 1) {
                                bindings.dup();
                            }
                            bindings.bufferLoad(desc.offset(), type)
                                    .vmStore(storage, type);
                        }
                    } else {
                        return getBindings(carrier, layout, STRUCT_REGISTER_X, isVariadicArg);
                    }
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    bindings.copy(layout)
                            .unboxAddress();
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER);
                    bindings.vmStore(storage, long.class);
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
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVariadicArg) {
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            if (isVariadicArg) {
                typeClass = BindingCalculator.conventionConverterMap.getOrDefault(typeClass, typeClass);
            }
            return getBindings(carrier, layout, typeClass, isVariadicArg);
        }

        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, TypeClass argumentClass, boolean isVariadicArg) {
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case INTEGER -> {
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER);
                    bindings.vmLoad(storage, carrier);
                }
                case FLOAT -> {
                    VMStorage storage = storageCalculator.getStorage(StorageType.FLOAT);
                    bindings.vmLoad(storage, carrier);
                }
                case POINTER -> {
                    AddressLayout addressLayout = (AddressLayout) layout;
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER);
                    bindings.vmLoad(storage, long.class)
                            .boxAddressRaw(Utils.pointeeByteSize(addressLayout), Utils.pointeeByteAlign(addressLayout));
                }
                case STRUCT_REGISTER_X -> {
                    assert carrier == MemorySegment.class;

                    // When no register is available, struct will be passed by stack.
                    // Before allocation, stack must be aligned.
                    if (!storageCalculator.regsAvailable(1, 0)) {
                        storageCalculator.alignStack(layout.byteAlignment());
                    }
                    bindings.allocate(layout);
                    VMStorage[] locations = storageCalculator.getStorages(layout, isVariadicArg);
                    int locIndex = 0;
                    long offset = 0;
                    while (offset < layout.byteSize()) {
                        final long copy = Math.min(layout.byteSize() - offset, 8);
                        VMStorage storage = locations[locIndex++];
                        Class<?> type = SharedUtils.primitiveCarrierForSize(copy, false);
                        bindings.dup().vmLoad(storage, type)
                                .bufferStore(offset, type, (int) copy);
                        offset += copy;
                    }
                }
                case STRUCT_REGISTER_F -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout);
                    List<FlattenedFieldDesc> descs = getFlattenedFields((GroupLayout) layout);
                    if (storageCalculator.regsAvailable(0, descs.size())) {
                        for (FlattenedFieldDesc desc : descs) {
                            Class<?> type = desc.layout().carrier();
                            VMStorage storage = storageCalculator.getStorage(StorageType.FLOAT);
                            bindings.dup()
                                    .vmLoad(storage, type)
                                    .bufferStore(desc.offset(), type);
                        }
                    } else {
                        return getBindings(carrier, layout, STRUCT_REGISTER_X, isVariadicArg);
                    }
                }
                case STRUCT_REGISTER_XF -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout);
                    if (storageCalculator.regsAvailable(1, 1)) {
                        List<FlattenedFieldDesc> descs = getFlattenedFields((GroupLayout) layout);
                        for (int i = 0; i < 2; i++) {
                            FlattenedFieldDesc desc = descs.get(i);
                            int storageClass;
                            if (desc.typeClass() == INTEGER) {
                                storageClass = StorageType.INTEGER;
                            } else {
                                storageClass = StorageType.FLOAT;
                            }
                            VMStorage storage = storageCalculator.getStorage(storageClass);
                            Class<?> type = desc.layout().carrier();
                            bindings.dup()
                                    .vmLoad(storage, type)
                                    .bufferStore(desc.offset(), type);
                        }
                    } else {
                        return getBindings(carrier, layout, STRUCT_REGISTER_X, isVariadicArg);
                    }
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER);
                    bindings.vmLoad(storage, long.class)
                            .boxAddress(layout);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }

            return bindings.build();
        }
    }
}
