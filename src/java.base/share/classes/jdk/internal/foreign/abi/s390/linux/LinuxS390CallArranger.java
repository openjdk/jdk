/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023 IBM Corp. All rights reserved.
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
package jdk.internal.foreign.abi.s390.linux;

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
import java.util.Optional;

import static jdk.internal.foreign.abi.s390.S390Architecture.*;
import static jdk.internal.foreign.abi.s390.S390Architecture.Regs.*;

/**
 * For the S390 C ABI specifically, this class uses CallingSequenceBuilder
 * to translate a C FunctionDescriptor into a CallingSequence, which can then be turned into a MethodHandle.
 *
 * This includes taking care of synthetic arguments like pointers to return buffers for 'in-memory' returns.
 */
public class LinuxS390CallArranger {

    private static final int STACK_SLOT_SIZE = 8;
    public static final int MAX_REGISTER_ARGUMENTS = 5;
    public static final int MAX_FLOAT_REGISTER_ARGUMENTS = 4;

    private static final ABIDescriptor CLinux = abiFor(
            new VMStorage[] { r2, r3, r4, r5, r6, }, // GP input
            new VMStorage[] { f0, f2, f4, f6 }, // FP input
            new VMStorage[] { r2, }, // GP output
            new VMStorage[] { f0, }, // FP output
            new VMStorage[] { r0, r1, r2, r3, r4, r5, r14 }, // volatile GP
            new VMStorage[] { f1, f3, f5, f7 }, // volatile FP (excluding argument registers)
            8, // Stack is always 8 byte aligned on S390
            160, // ABI header
            r0, r1 // scratch reg r0 & r1
            );

    public record Bindings(CallingSequence callingSequence, boolean isInMemoryReturn) {}

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
            MemoryLayout layout =SharedUtils.C_POINTER;
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
            .filter(layout -> layout instanceof GroupLayout)
            .isPresent();
    }

    static class StorageCalculator {
        private final boolean forArguments;

        private final int[] nRegs = new int[] { 0, 0 };
        private long stackOffset = 0;

        public StorageCalculator(boolean forArguments) {
            this.forArguments = forArguments;
        }

        VMStorage stackAlloc(long size, long alignment) {
            long alignedStackOffset = Utils.alignUp(stackOffset, alignment);

            short encodedSize = (short) size;
            assert (encodedSize & 0xFFFF) == size;

            VMStorage storage = stackStorage(encodedSize, (int) alignedStackOffset);
            stackOffset = alignedStackOffset + size;
            return storage;
        }

        VMStorage regAlloc(int type) {
            int gpRegCnt = (type == StorageType.INTEGER) ? 1 : 0;
            int fpRegCnt = (type == StorageType.FLOAT) ? 1 : 0;

            // Use stack if not enough registers available.
            if ((type == StorageType.FLOAT && (nRegs[StorageType.FLOAT] + fpRegCnt) > MAX_FLOAT_REGISTER_ARGUMENTS)
                    || (type == StorageType.INTEGER && (nRegs[StorageType.INTEGER] + gpRegCnt) > MAX_REGISTER_ARGUMENTS)) return null;

            VMStorage[] source = (forArguments ? CLinux.inputStorage : CLinux.outputStorage)[type];
            VMStorage result = source[nRegs[type]];

            nRegs[StorageType.INTEGER] += gpRegCnt;
            nRegs[StorageType.FLOAT] += fpRegCnt;
            return result;

        }
        VMStorage getStorage(int type, boolean is32Bit) {
            VMStorage reg = regAlloc(type);
            if (reg != null) {
                if (is32Bit) {
                    reg = new VMStorage(reg.type(), REG32_MASK, reg.indexOrOffset());
                }
                return reg;
            }
            VMStorage stack;
            if (is32Bit) {
                stackAlloc(4, STACK_SLOT_SIZE); // Skip first half of stack slot.
                stack = stackAlloc(4, 4);
            } else
                stack = stackAlloc(8, STACK_SLOT_SIZE);

            return stack;
        }
    }

    abstract static class BindingCalculator {
        protected final StorageCalculator storageCalculator;

        protected BindingCalculator(boolean forArguments) {
            this.storageCalculator = new LinuxS390CallArranger.StorageCalculator(forArguments);
        }

        abstract List<Binding> getBindings(Class<?> carrier, MemoryLayout layout);
    }

    // Compute recipe for transferring arguments / return values to C from Java.
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
            switch (argumentClass) {
                case STRUCT_REGISTER -> {
                    assert carrier == MemorySegment.class;
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER, false);
                    Class<?> type = SharedUtils.primitiveCarrierForSize(layout.byteSize(), false);
                    bindings.bufferLoad(0, type)
                            .vmStore(storage, type);
                }
                case STRUCT_SFA -> {
                    assert carrier == MemorySegment.class;
                    VMStorage storage = storageCalculator.getStorage(StorageType.FLOAT, layout.byteSize() == 4);
                    Class<?> type = SharedUtils.primitiveCarrierForSize(layout.byteSize(), true);
                    bindings.bufferLoad(0, type)
                            .vmStore(storage, type);
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    bindings.copy(layout)
                            .unboxAddress();
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER, false);
                    bindings.vmStore(storage, long.class);
                }
                case POINTER -> {
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER, false);
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
                    // ABI requires all int types to get extended to 64 bit.
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER, false);
                    bindings.vmStore(storage, carrier);
                }
                case FLOAT -> {
                    VMStorage storage = storageCalculator.getStorage(StorageType.FLOAT, carrier == float.class);
                    bindings.vmStore(storage, carrier);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }

    // Compute recipe for transferring arguments / return values from C to Java.
    static class BoxBindingCalculator extends BindingCalculator {
        BoxBindingCalculator(boolean forArguments) {
            super(forArguments);
        }

        @Override
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout) {
            TypeClass argumentClass = TypeClass.classifyLayout(layout);
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case STRUCT_REGISTER -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout)
                            .dup();
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER, false);
                    Class<?> type = SharedUtils.primitiveCarrierForSize(layout.byteSize(), false);
                    bindings.vmLoad(storage, type)
                            .bufferStore(0, type);
                }
                case STRUCT_SFA -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout)
                            .dup();
                    VMStorage storage = storageCalculator.getStorage(StorageType.FLOAT, layout.byteSize() == 4);
                    Class<?> type = SharedUtils.primitiveCarrierForSize(layout.byteSize(), true);
                    bindings.vmLoad(storage, type)
                            .bufferStore(0, type);
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER, false);
                    bindings.vmLoad(storage, long.class)
                            .boxAddress(layout);
                }
                case POINTER -> {
                    AddressLayout addressLayout = (AddressLayout) layout;
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER, false);
                    bindings.vmLoad(storage, long.class)
                            .boxAddressRaw(Utils.pointeeByteSize(addressLayout), Utils.pointeeByteAlign(addressLayout));
                }
                case INTEGER -> {
                    // We could use carrier != long.class for BoxBindingCalculator, but C always uses 64 bit slots.
                    VMStorage storage = storageCalculator.getStorage(StorageType.INTEGER, false);
                    bindings.vmLoad(storage, carrier);
                }
                case FLOAT -> {
                    VMStorage storage = storageCalculator.getStorage(StorageType.FLOAT, carrier == float.class);
                    bindings.vmLoad(storage, carrier);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }
}
