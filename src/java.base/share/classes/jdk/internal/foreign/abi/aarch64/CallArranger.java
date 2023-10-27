/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2022, Arm Limited. All rights reserved.
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
package jdk.internal.foreign.abi.aarch64;

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
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64CallArranger;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64CallArranger;
import jdk.internal.foreign.abi.aarch64.windows.WindowsAArch64CallArranger;
import jdk.internal.foreign.Utils;

import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;

import static jdk.internal.foreign.abi.aarch64.AArch64Architecture.*;
import static jdk.internal.foreign.abi.aarch64.AArch64Architecture.Regs.*;

/**
 * For the AArch64 C ABI specifically, this class uses CallingSequenceBuilder
 * to translate a C FunctionDescriptor into a CallingSequence, which can then be turned into a MethodHandle.
 *
 * This includes taking care of synthetic arguments like pointers to return buffers for 'in-memory' returns.
 *
 * There are minor differences between the ABIs implemented on Linux, macOS, and Windows
 * which are handled in sub-classes. Clients should access these through the provided
 * public constants CallArranger.LINUX, CallArranger.MACOS, and CallArranger.WINDOWS.
 */
public abstract class CallArranger {
    private static final int STACK_SLOT_SIZE = 8;
    private static final int MAX_COPY_SIZE = 8;
    public static final int MAX_REGISTER_ARGUMENTS = 8;

    private static final VMStorage INDIRECT_RESULT = r8;

    // This is derived from the AAPCS64 spec, restricted to what's
    // possible when calling to/from C code.
    //
    // The indirect result register, r8, is used to return a large
    // struct by value. It's treated as an input here as the caller is
    // responsible for allocating storage and passing this into the
    // function.
    //
    // Although the AAPCS64 says r0-7 and v0-7 are all valid return
    // registers, it's not possible to generate a C function that uses
    // r2-7 and v4-7 so, they are omitted here.
    protected static final ABIDescriptor C = abiFor(
        new VMStorage[] { r0, r1, r2, r3, r4, r5, r6, r7, INDIRECT_RESULT},
        new VMStorage[] { v0, v1, v2, v3, v4, v5, v6, v7 },
        new VMStorage[] { r0, r1 },
        new VMStorage[] { v0, v1, v2, v3 },
        new VMStorage[] { r9, r10, r11, r12, r13, r14, r15 },
        new VMStorage[] { v16, v17, v18, v19, v20, v21, v22, v23, v24, v25,
                          v26, v27, v28, v29, v30, v31 },
        16,  // Stack is always 16 byte aligned on AArch64
        0,   // No shadow space
        r9, r10  // scratch 1 & 2
    );

    public record Bindings(CallingSequence callingSequence,
                           boolean isInMemoryReturn) {
    }

    public static final CallArranger LINUX = new LinuxAArch64CallArranger();
    public static final CallArranger MACOS = new MacOsAArch64CallArranger();
    public static final CallArranger WINDOWS = new WindowsAArch64CallArranger();

    /**
     * Are variadic arguments assigned to registers as in the standard calling
     * convention, or always passed on the stack?
     *
     * @return true if variadic arguments should be spilled to the stack.
      */
     protected abstract boolean varArgsOnStack();

    /**
     * {@return true if this ABI requires sub-slot (smaller than STACK_SLOT_SIZE) packing of arguments on the stack.}
     */
    protected abstract boolean requiresSubSlotStackPacking();

    /**
     * Are floating point arguments to variadic functions passed in general purpose registers
     * instead of floating point registers?
     *
     * {@return true if this ABI uses general purpose registers for variadic floating point arguments.}
     */
    protected abstract boolean useIntRegsForVariadicFloatingPointArgs();

    /**
     * Should some fields of structs that assigned to registers be passed in registers when there
     * are not enough registers for all the fields of the struct?
     *
     * {@return true if this ABI passes some fields of a struct in registers.}
     */
    protected abstract boolean spillsVariadicStructsPartially();

    /**
     * @return The ABIDescriptor used by the CallArranger for the current platform.
     */
    protected abstract ABIDescriptor abiDescriptor();

    protected TypeClass getArgumentClassForBindings(MemoryLayout layout, boolean forVariadicFunction) {
        return TypeClass.classifyLayout(layout);
    }

    protected CallArranger() {}

    public Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall) {
        return getBindings(mt, cDesc, forUpcall, LinkerOptions.empty());
    }

    public Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall, LinkerOptions options) {
        CallingSequenceBuilder csb = new CallingSequenceBuilder(abiDescriptor(), forUpcall, options);

        boolean forVariadicFunction = options.isVariadicFunction();

        BindingCalculator argCalc = forUpcall ? new BoxBindingCalculator(true) : new UnboxBindingCalculator(true, forVariadicFunction);
        BindingCalculator retCalc = forUpcall ? new UnboxBindingCalculator(false, forVariadicFunction) : new BoxBindingCalculator(false);

        boolean returnInMemory = isInMemoryReturn(cDesc.returnLayout());
        if (returnInMemory) {
            csb.addArgumentBindings(MemorySegment.class, SharedUtils.C_POINTER,
                    argCalc.getIndirectBindings());
        } else if (cDesc.returnLayout().isPresent()) {
            Class<?> carrier = mt.returnType();
            MemoryLayout layout = cDesc.returnLayout().get();
            csb.setReturnBindings(carrier, layout, retCalc.getBindings(carrier, layout));
        }

        for (int i = 0; i < mt.parameterCount(); i++) {
            Class<?> carrier = mt.parameterType(i);
            MemoryLayout layout = cDesc.argumentLayouts().get(i);
            if (varArgsOnStack() && options.isVarargsIndex(i)) {
                argCalc.storageCalculator.adjustForVarArgs();
            }
            csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout));
        }

        return new Bindings(csb.build(), returnInMemory);
    }

    public MethodHandle arrangeDowncall(MethodType mt, FunctionDescriptor cDesc, LinkerOptions options) {
        Bindings bindings = getBindings(mt, cDesc, false, options);

        MethodHandle handle = new DowncallLinker(abiDescriptor(), bindings.callingSequence).getBoundMethodHandle();

        if (bindings.isInMemoryReturn) {
            handle = SharedUtils.adaptDowncallForIMR(handle, cDesc, bindings.callingSequence);
        }

        return handle;
    }

    public UpcallStubFactory arrangeUpcall(MethodType mt, FunctionDescriptor cDesc,
                                                          LinkerOptions options) {
        Bindings bindings = getBindings(mt, cDesc, true, options);
        final boolean dropReturn = true; /* drop return, since we don't have bindings for it */
        return SharedUtils.arrangeUpcallHelper(mt, bindings.isInMemoryReturn, dropReturn, abiDescriptor(),
                bindings.callingSequence);
    }

    private static boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout) {
        return returnLayout
            .filter(GroupLayout.class::isInstance)
            .filter(g -> TypeClass.classifyLayout(g) == TypeClass.STRUCT_REFERENCE)
            .isPresent();
    }

    class StorageCalculator {
        private final boolean forArguments;
        private final boolean forVariadicFunction;
        private boolean forVarArgs = false;

        private final int[] nRegs = new int[] { 0, 0 };
        private long stackOffset = 0;

        public StorageCalculator(boolean forArguments, boolean forVariadicFunction) {
            this.forArguments = forArguments;
            this.forVariadicFunction = forVariadicFunction;
        }

        private boolean hasRegister(int type) {
            return hasEnoughRegisters(type, 1);
        }

        private boolean hasEnoughRegisters(int type, int count) {
            return nRegs[type] + count <= MAX_REGISTER_ARGUMENTS;
        }

        private static Class<?> adjustCarrierForStack(Class<?> carrier) {
            if (carrier == float.class) {
                carrier = int.class;
            } else if (carrier == double.class) {
                carrier = long.class;
            }
            return carrier;
        }

        record StructStorage(long offset, Class<?> carrier, int byteWidth, VMStorage storage) {}

        /*
        In the simplest case structs are copied in chunks. i.e. the fields don't matter, just the size.
        The struct is split into 8-byte chunks, and those chunks are either passed in registers and/or on the stack.

        Homogeneous float aggregates (HFAs) can be copied in a field-wise manner, i.e. the struct is split into it's
        fields and those fields are the chunks which are passed. For HFAs the rules are more complicated and ABI based:

                        | enough registers | some registers, but not enough  | no registers
        ----------------+------------------+---------------------------------+-------------------------
        Linux           | FW in regs       | CW on the stack                 | CW on the stack
        macOS, non-VA   | FW in regs       | FW on the stack                 | FW on the stack
        macOS, VA       | FW in regs       | CW on the stack                 | CW on the stack
        Windows, non-VF | FW in regs       | CW on the stack                 | CW on the stack
        Windows, VF     | FW in regs       | CW split between regs and stack | CW on the stack
        (where FW = Field-wise copy, CW = Chunk-wise copy, VA is a variadic argument, and VF is a variadic function)

        For regular structs, the rules are as follows:

                        | enough registers | some registers, but not enough  | no registers
        ----------------+------------------+---------------------------------+-------------------------
        Linux           | CW in regs       | CW on the stack                 | CW on the stack
        macOS           | CW in regs       | CW on the stack                 | CW on the stack
        Windows, non-VF | CW in regs       | CW on the stack                 | CW on the stack
        Windows, VF     | CW in regs       | CW split between regs and stack | CW on the stack
         */
        StructStorage[] structStorages(GroupLayout layout, boolean forHFA) {
            int numChunks = (int)Utils.alignUp(layout.byteSize(), MAX_COPY_SIZE) / MAX_COPY_SIZE;

            int regType = StorageType.INTEGER;
            List<MemoryLayout> scalarLayouts = null;
            int requiredStorages = numChunks;
            if (forHFA) {
                regType = StorageType.VECTOR;
                scalarLayouts = TypeClass.scalarLayouts(layout);
                requiredStorages = scalarLayouts.size();
            }

            boolean hasEnoughRegisters = hasEnoughRegisters(regType, requiredStorages);

            // For the ABI variants that pack arguments spilled to the
            // stack, HFA arguments are spilled as if their individual
            // fields had been allocated separately rather than as if the
            // struct had been spilled as a whole.
            boolean useFieldWiseSpill = requiresSubSlotStackPacking() && !forVarArgs;
            boolean isFieldWise = forHFA && (hasEnoughRegisters || useFieldWiseSpill);
            if (!isFieldWise) {
                requiredStorages = numChunks;
            }

            boolean spillPartially = forVariadicFunction && spillsVariadicStructsPartially();
            boolean furtherAllocationFromTheStack = !hasEnoughRegisters && !spillPartially;
            if (furtherAllocationFromTheStack) {
                // Any further allocations for this register type must
                // be from the stack.
                nRegs[regType] = MAX_REGISTER_ARGUMENTS;
            }

            if (requiresSubSlotStackPacking() && !isFieldWise) {
                // Pad to the next stack slot boundary instead of packing
                // additional arguments into the unused space.
                alignStack(STACK_SLOT_SIZE);
            }

            StructStorage[] structStorages = new StructStorage[requiredStorages];
            long offset = 0;
            for (int i = 0; i < structStorages.length; i++) {
                ValueLayout copyLayout;
                long copySize;
                if (isFieldWise) {
                    // We should only get here for HFAs, which can't have padding
                    copyLayout = (ValueLayout) scalarLayouts.get(i);
                    copySize = Utils.byteWidthOfPrimitive(copyLayout.carrier());
                } else {
                    // chunk-wise copy
                    copySize = Math.min(layout.byteSize() - offset, MAX_COPY_SIZE);
                    boolean useFloat = false; // never use float for chunk-wise copies
                    copyLayout = SharedUtils.primitiveLayoutForSize(copySize, useFloat);
                }

                VMStorage storage = nextStorage(regType, copyLayout);
                Class<?> carrier = copyLayout.carrier();
                if (isFieldWise && storage.type() == StorageType.STACK) {
                    // copyLayout is a field of an HFA
                    // Don't use floats on the stack
                    carrier = adjustCarrierForStack(carrier);
                }
                structStorages[i] = new StructStorage(offset, carrier, (int) copySize, storage);
                offset += copyLayout.byteSize();
            }

            if (requiresSubSlotStackPacking() && !isFieldWise) {
                // Pad to the next stack slot boundary instead of packing
                // additional arguments into the unused space.
                alignStack(STACK_SLOT_SIZE);
            }

            return structStorages;
        }

        private void alignStack(long alignment) {
            stackOffset = Utils.alignUp(stackOffset, alignment);
        }

        // allocate a single ValueLayout, either in a register or on the stack
        VMStorage nextStorage(int type, ValueLayout layout) {
            return hasRegister(type) ? regAlloc(type) : stackAlloc(layout);
        }

        private VMStorage regAlloc(int type) {
            ABIDescriptor abiDescriptor = abiDescriptor();
            VMStorage[] source = (forArguments ? abiDescriptor.inputStorage : abiDescriptor.outputStorage)[type];
            return source[nRegs[type]++];
        }

        private VMStorage stackAlloc(ValueLayout layout) {
            assert forArguments : "no stack returns";
            long stackSlotAlignment = requiresSubSlotStackPacking() && !forVarArgs
                    ? layout.byteAlignment()
                    : Math.max(layout.byteAlignment(), STACK_SLOT_SIZE);
            long alignedStackOffset = Utils.alignUp(stackOffset, stackSlotAlignment);

            short encodedSize = (short) layout.byteSize();
            assert (encodedSize & 0xFFFF) == layout.byteSize();

            VMStorage storage = AArch64Architecture.stackStorage(encodedSize, (int)alignedStackOffset);
            stackOffset = alignedStackOffset + layout.byteSize();
            return storage;
        }

        void adjustForVarArgs() {
            // This system passes all variadic parameters on the stack. Ensure
            // no further arguments are allocated to registers.
            nRegs[StorageType.INTEGER] = MAX_REGISTER_ARGUMENTS;
            nRegs[StorageType.VECTOR] = MAX_REGISTER_ARGUMENTS;
            forVarArgs = true;
        }
    }

    abstract class BindingCalculator {
        protected final StorageCalculator storageCalculator;

        protected BindingCalculator(boolean forArguments, boolean forVariadicFunction) {
            this.storageCalculator = new StorageCalculator(forArguments, forVariadicFunction);
        }

        abstract List<Binding> getBindings(Class<?> carrier, MemoryLayout layout);

        abstract List<Binding> getIndirectBindings();
    }

    class UnboxBindingCalculator extends BindingCalculator {
        protected final boolean forArguments;
        protected final boolean forVariadicFunction;

        UnboxBindingCalculator(boolean forArguments, boolean forVariadicFunction) {
            super(forArguments, forVariadicFunction);
            this.forArguments = forArguments;
            this.forVariadicFunction = forVariadicFunction;
        }

        @Override
        List<Binding> getIndirectBindings() {
            return Binding.builder()
                .unboxAddress()
                .vmStore(INDIRECT_RESULT, long.class)
                .build();
        }

        @Override
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout) {
            TypeClass argumentClass = getArgumentClassForBindings(layout, forVariadicFunction);
            Binding.Builder bindings = Binding.builder();

            switch (argumentClass) {
                case STRUCT_REGISTER, STRUCT_HFA -> {
                    assert carrier == MemorySegment.class;
                    boolean forHFA = argumentClass == TypeClass.STRUCT_HFA;
                    StorageCalculator.StructStorage[] structStorages
                            = storageCalculator.structStorages((GroupLayout) layout, forHFA);

                    for (int i = 0; i < structStorages.length; i++) {
                        StorageCalculator.StructStorage structStorage = structStorages[i];
                        if (i < structStorages.length - 1) {
                            bindings.dup();
                        }
                        bindings.bufferLoad(structStorage.offset(), structStorage.carrier(), structStorage.byteWidth())
                                .vmStore(structStorage.storage(), structStorage.carrier());
                    }
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    bindings.copy(layout)
                            .unboxAddress();
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER, SharedUtils.C_POINTER);
                    bindings.vmStore(storage, long.class);
                }
                case POINTER -> {
                    bindings.unboxAddress();
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER, (ValueLayout) layout);
                    bindings.vmStore(storage, long.class);
                }
                case INTEGER -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER, (ValueLayout) layout);
                    bindings.vmStore(storage, carrier);
                }
                case FLOAT -> {
                    boolean forVariadicFunctionArgs = forArguments && forVariadicFunction;
                    boolean useIntReg = forVariadicFunctionArgs && useIntRegsForVariadicFloatingPointArgs();

                    int type = useIntReg ? StorageType.INTEGER : StorageType.VECTOR;
                    VMStorage storage = storageCalculator.nextStorage(type, (ValueLayout) layout);
                    bindings.vmStore(storage, carrier);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }

    class BoxBindingCalculator extends BindingCalculator {
        BoxBindingCalculator(boolean forArguments) {
            super(forArguments, false);
        }

        @Override
        List<Binding> getIndirectBindings() {
            return Binding.builder()
                .vmLoad(INDIRECT_RESULT, long.class)
                .boxAddressRaw(Long.MAX_VALUE, 1)
                .build();
        }

        @Override
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout) {
            TypeClass argumentClass = TypeClass.classifyLayout(layout);
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case STRUCT_REGISTER, STRUCT_HFA -> {
                    assert carrier == MemorySegment.class;
                    boolean forHFA = argumentClass == TypeClass.STRUCT_HFA;
                    bindings.allocate(layout);
                    StorageCalculator.StructStorage[] structStorages
                            = storageCalculator.structStorages((GroupLayout) layout, forHFA);

                    for (StorageCalculator.StructStorage structStorage : structStorages) {
                        bindings.dup();
                        bindings.vmLoad(structStorage.storage(), structStorage.carrier())
                                .bufferStore(structStorage.offset(), structStorage.carrier(), structStorage.byteWidth());
                    }
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER, SharedUtils.C_POINTER);
                    bindings.vmLoad(storage, long.class)
                            .boxAddress(layout);
                }
                case POINTER -> {
                    AddressLayout addressLayout = (AddressLayout) layout;
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER, addressLayout);
                    bindings.vmLoad(storage, long.class)
                            .boxAddressRaw(Utils.pointeeByteSize(addressLayout), Utils.pointeeByteAlign(addressLayout));
                }
                case INTEGER -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.INTEGER, (ValueLayout) layout);
                    bindings.vmLoad(storage, carrier);
                }
                case FLOAT -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageType.VECTOR, (ValueLayout) layout);
                    bindings.vmLoad(storage, carrier);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }
}
