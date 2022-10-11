/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Institute of Software, Chinese Academy of Sciences. All rights reserved.
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
 *
 */

package jdk.internal.foreign.abi.riscv64.linux;

import jdk.internal.foreign.PlatformLayouts;
import jdk.internal.foreign.abi.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static jdk.internal.foreign.abi.riscv64.RISCV64Architecture.*;
import static jdk.internal.foreign.abi.riscv64.linux.TypeClass.*;

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
            0, // no shadow space
            x29, // target addr reg
            x30  // ret buf addr reg
    );

    // Make registers as prototype, create vmstorages with same index with registers.
    static Map.Entry<Integer, VMStorage[]> buildStorageEntry(int storageClass, VMStorage[][] storagePrototypes) {
        VMStorage[] prototypes = storagePrototypes[storageClass >> 8];
        VMStorage[] result = new VMStorage[prototypes.length];
        for (int i = 0; i < prototypes.length; i++) {
            result[i] = new VMStorage(storageClass, prototypes[i].index(), prototypes[i].name());
        }
        return Map.entry(storageClass, result);
    }

    // Code below will not declare new registers.
    static final Map<Integer, VMStorage[]> inputStorage = Map.ofEntries(
            buildStorageEntry(StorageClasses.INTEGER_8, CLinux.inputStorage),
            buildStorageEntry(StorageClasses.INTEGER_16, CLinux.inputStorage),
            buildStorageEntry(StorageClasses.INTEGER_32, CLinux.inputStorage),
            buildStorageEntry(StorageClasses.INTEGER_64, CLinux.inputStorage),
            buildStorageEntry(StorageClasses.FLOAT_32, CLinux.inputStorage),
            buildStorageEntry(StorageClasses.FLOAT_64, CLinux.inputStorage)
    );

    static Map<Integer, VMStorage[]> outputStorage = inputStorage;

    static int regType(int storageClass) {
        return (storageClass >> 8) << 8;
    }

    public static class Bindings {
        public final CallingSequence callingSequence;
        public final boolean isInMemoryReturn;

        Bindings(CallingSequence callingSequence, boolean isInMemoryReturn) {
            this.callingSequence = callingSequence;
            this.isInMemoryReturn = isInMemoryReturn;
        }
    }

    public static LinuxRISCV64CallArranger.Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall) {
        CallingSequenceBuilder csb = new CallingSequenceBuilder(CLinux, forUpcall);
        BindingCalculator argCalc = forUpcall ? new BoxBindingCalculator(true) : new UnboxBindingCalculator(true);
        BindingCalculator retCalc = forUpcall ? new UnboxBindingCalculator(false) : new BoxBindingCalculator(false);

        // When return struct is classified as STRUCT_REFERENCE, it will be true.
        boolean returnInMemory = isInMemoryReturn(cDesc.returnLayout());
        if (returnInMemory) {
            Class<?> carrier = MemoryAddress.class;
            MemoryLayout layout = PlatformLayouts.LinuxRISCV64.C_POINTER;
            csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout, false));
        } else if (cDesc.returnLayout().isPresent()) {
            Class<?> carrier = mt.returnType();
            MemoryLayout layout = cDesc.returnLayout().get();
            csb.setReturnBindings(carrier, layout, retCalc.getBindings(carrier, layout, false));
        }

        for (int i = 0; i < mt.parameterCount(); i++) {
            Class<?> carrier = mt.parameterType(i);
            MemoryLayout layout = cDesc.argumentLayouts().get(i);
            boolean isVar = cDesc.firstVariadicArgumentIndex() != -1 && i >= cDesc.firstVariadicArgumentIndex();
            csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout, isVar));
        }

        return new LinuxRISCV64CallArranger.Bindings(csb.build(), returnInMemory);
    }

    public static MethodHandle arrangeDowncall(MethodType mt, FunctionDescriptor cDesc) {
        LinuxRISCV64CallArranger.Bindings bindings = getBindings(mt, cDesc, false);

        MethodHandle handle = new DowncallLinker(CLinux, bindings.callingSequence).getBoundMethodHandle();

        if (bindings.isInMemoryReturn) {
            handle = SharedUtils.adaptDowncallForIMR(handle, cDesc);
        }

        return handle;
    }

    public static MemorySegment arrangeUpcall(MethodHandle target, MethodType mt, FunctionDescriptor cDesc, MemorySession session) {

        LinuxRISCV64CallArranger.Bindings bindings = getBindings(mt, cDesc, true);

        if (bindings.isInMemoryReturn) {
            target = SharedUtils.adaptUpcallForIMR(target, true /* drop return, since we don't have bindings for it */);
        }

        return UpcallLinker.make(CLinux, target, bindings.callingSequence, session);
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

        VMStorage stackAlloc() {
            assert forArguments : "no stack returns";
            VMStorage storage = stackStorage((int) (stackOffset / STACK_SLOT_SIZE));
            stackOffset += STACK_SLOT_SIZE;
            return storage;
        }

        Optional<VMStorage> regAlloc(int storageClass) {
            int nRegsIdx = storageClass >> 8;
            var availableRegs = MAX_REGISTER_ARGUMENTS - nRegs[nRegsIdx];
            if (availableRegs > 0) {
                VMStorage[] source =
                        (forArguments ? inputStorage : outputStorage).get(storageClass);
                Optional<VMStorage> result =
                        Optional.of(source[nRegs[nRegsIdx]]);
                nRegs[nRegsIdx] += 1;
                return result;
            }
            return Optional.empty();
        }

        // Try to get Storage corresponding to storageClass,
        VMStorage getStorage(int storageClass) {
            Optional<VMStorage> storage = regAlloc(storageClass);
            if (storage.isPresent()) return storage.get();
            // If storageClass is RegTypes.FLOAT, and no floating-point register is available,
            // try to allocate an integer register.
            if (regType(storageClass) == RegTypes.FLOAT) {
                storage = regAlloc(StorageClasses.toIntegerClass(storageClass));
                if (storage.isPresent()) return storage.get();
            }
            return stackAlloc();
        }

        VMStorage[] getStorages(MemoryLayout layout) {
            int regCnt = (int) SharedUtils.alignUp(layout.byteSize(), 8) / 8;
            VMStorage[] storages = new VMStorage[regCnt];
            for (int i = 0; i < regCnt; i++) {
                // use integer calling convention.
                storages[i] = getStorage(StorageClasses.INTEGER_64);
            }
            return storages;
        }

        boolean availableRegs(int integerReg, int floatReg) {
            return nRegs[IntegerRegIdx] + integerReg <= MAX_REGISTER_ARGUMENTS &&
                   nRegs[FloatRegIdx] + floatReg <= MAX_REGISTER_ARGUMENTS;
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

        // Variadic arguments are passed according to the integer calling convention.
        // When handling variadic part, integer calling convention should be used.
        static final Map<TypeClass, TypeClass> conventionConverterMap =
                Map.ofEntries(Map.entry(FLOAT_32, INTEGER_32),
                              Map.entry(FLOAT_64, INTEGER_64),
                              Map.entry(STRUCT_FA, STRUCT_A),
                              Map.entry(STRUCT_BOTH, STRUCT_A));
    }

    static class UnboxBindingCalculator extends BindingCalculator {
        boolean forArguments;

        UnboxBindingCalculator(boolean forArguments) {
            super(forArguments);
            this.forArguments = forArguments;
        }

        @Override
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVariadicArg) {
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            if (isVariadicArg) {
                typeClass = BindingCalculator.conventionConverterMap.getOrDefault(typeClass, typeClass);
            }
            return getBindings(carrier, layout, typeClass);
        }

        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, TypeClass argumentClass) {
            // Binding.Builder will build a series of operation. Its working style like a stack interpreter.
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case INTEGER_8, INTEGER_16, INTEGER_32, INTEGER_64, FLOAT_32, FLOAT_64 -> {
                    VMStorage storage = storageCalculator.getStorage(StorageClasses.fromTypeClass(argumentClass));
                    bindings.vmStore(storage, carrier);
                }

                case POINTER -> {
                    bindings.unboxAddress(carrier);
                    VMStorage storage = storageCalculator.getStorage(StorageClasses.INTEGER_64);
                    bindings.vmStore(storage, long.class);
                }

                case STRUCT_A -> {
                    assert carrier == MemorySegment.class;
                    VMStorage[] locations = storageCalculator.getStorages(
                            layout);
                    int locIndex = 0;
                    long offset = 0;
                    while (offset < layout.byteSize()) {
                        final long copy = Math.min(layout.byteSize() - offset, 8);
                        VMStorage storage = locations[locIndex++];
                        boolean useFloat = regType(storage.type()) == RegTypes.FLOAT;
                        Class<?> type = SharedUtils.primitiveCarrierForSize(copy, useFloat);
                        if (offset + copy < layout.byteSize()) {
                            bindings.dup();
                        }
                        bindings.bufferLoad(offset, type)
                                .vmStore(storage, type);
                        offset += copy;
                    }
                }

                case STRUCT_FA -> {
                    assert carrier == MemorySegment.class;
                    List<FlattenedFieldDesc> descs = getFlattenedFields((GroupLayout) layout);
                    if (storageCalculator.availableRegs(0, descs.size())) {
                        for (int i = 0; i < descs.size(); i++) {
                            FlattenedFieldDesc desc = descs.get(i);
                            Class<?> type = desc.layout().carrier();
                            VMStorage storage = storageCalculator.getStorage(
                                    StorageClasses.fromTypeClass(desc.typeClass()));
                            if (i < descs.size() - 1) bindings.dup();
                            bindings.bufferLoad(desc.offset(), type)
                                    .vmStore(storage, type);
                        }
                    } else {
                        // If there is not enough register can be used, then fall back to integer calling convention.
                        return getBindings(carrier, layout, STRUCT_A);
                    }
                }

                case STRUCT_BOTH -> {
                    assert carrier == MemorySegment.class;
                    if (storageCalculator.availableRegs(1, 1)) {
                        List<FlattenedFieldDesc> descs = getFlattenedFields((GroupLayout) layout);
                        for (int i = 0; i < 2; i++) {
                            int storageClass = StorageClasses.fromTypeClass(descs.get(i).typeClass());
                            FlattenedFieldDesc desc = descs.get(i);
                            VMStorage storage = storageCalculator.getStorage(storageClass);
                            Class<?> type = desc.layout().carrier();
                            if (i < 1) bindings.dup();
                            bindings.bufferLoad(desc.offset(), type)
                                    .vmStore(storage, type);
                        }
                    } else {
                        return getBindings(carrier, layout, STRUCT_A);
                    }
                }

                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    bindings.copy(layout)
                            .unboxAddress(MemorySegment.class);
                    VMStorage storage = storageCalculator.getStorage(
                            StorageClasses.INTEGER_64);
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
            return getBindings(carrier, layout, typeClass);
        }

        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, TypeClass argumentClass) {
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case INTEGER_8, INTEGER_16, INTEGER_32, INTEGER_64, FLOAT_32, FLOAT_64 -> {
                    VMStorage storage = storageCalculator.getStorage(StorageClasses.fromTypeClass(argumentClass));
                    bindings.vmLoad(storage, carrier);
                }

                case POINTER -> {
                    VMStorage storage = storageCalculator.getStorage(StorageClasses.INTEGER_64);
                    bindings.vmLoad(storage, long.class)
                            .boxAddress();
                }

                case STRUCT_A -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout);
                    VMStorage[] locations = storageCalculator.getStorages(
                            layout);
                    int locIndex = 0;
                    long offset = 0;
                    while (offset < layout.byteSize()) {
                        final long copy = Math.min(layout.byteSize() - offset, 8);
                        VMStorage storage = locations[locIndex++];
                        boolean useFloat = regType(storage.type()) == RegTypes.FLOAT;
                        Class<?> type = SharedUtils.primitiveCarrierForSize(copy, useFloat);
                        bindings.dup().vmLoad(storage, type)
                                .bufferStore(offset, type);
                        offset += copy;
                    }
                }

                case STRUCT_FA -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout);
                    List<FlattenedFieldDesc> descs = getFlattenedFields((GroupLayout) layout);
                    if (storageCalculator.availableRegs(0, descs.size())) {
                        for (FlattenedFieldDesc desc : descs) {
                            Class<?> type = desc.layout().carrier();
                            VMStorage storage = storageCalculator.getStorage(
                                    StorageClasses.fromTypeClass(desc.typeClass()));
                            bindings.dup()
                                    .vmLoad(storage, type)
                                    .bufferStore(desc.offset(), type);
                        }
                    } else {
                        return getBindings(carrier, layout, STRUCT_A);
                    }
                }

                case STRUCT_BOTH -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout);
                    if (storageCalculator.availableRegs(1, 1)) {
                        List<FlattenedFieldDesc> descs = getFlattenedFields((GroupLayout) layout);
                        for (int i = 0; i < 2; i++) {
                            FlattenedFieldDesc desc = descs.get(i);
                            int storageClass = StorageClasses.fromTypeClass(desc.typeClass());
                            VMStorage storage = storageCalculator.getStorage(storageClass);
                            Class<?> type = desc.layout().carrier();
                            bindings.dup()
                                    .vmLoad(storage, type)
                                    .bufferStore(desc.offset(), type);
                        }
                    } else {
                        return getBindings(carrier, layout, STRUCT_A);
                    }
                }

                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    VMStorage storage = storageCalculator.getStorage(
                            StorageClasses.INTEGER_64);
                    bindings.vmLoad(storage, long.class)
                            .boxAddress()
                            .toSegment(layout);
                }

                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }
}
