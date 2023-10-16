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
package jdk.internal.foreign.abi;

import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.BindingInterpreter.LoadFunc;
import jdk.internal.foreign.abi.BindingInterpreter.StoreFunc;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * The binding operators defined in the Binding class can be combined into argument and return value processing 'recipes'.
 *
 * The binding operators are interpreted using a stack-base interpreter. Operators can either consume operands from the
 * stack, or push them onto the stack.
 *
 * In the description of each binding we talk about 'boxing' and 'unboxing'.
 *  - Unboxing is the process of taking a Java value and decomposing it, and storing components into machine
 *    storage locations. As such, the binding interpreter stack starts with the Java value on it, and should end empty.
 *  - Boxing is the process of re-composing a Java value by pulling components from machine storage locations.
 *    If a MemorySegment is needed to store the result, one should be allocated using the ALLOCATE_BUFFER operator.
 *    The binding interpreter stack starts off empty, and ends with the value to be returned as the only value on it.
 * A binding operator can be interpreted differently based on whether we are boxing or unboxing a value. For example,
 * the CONVERT_ADDRESS operator 'unboxes' a MemoryAddress to a long, but 'boxes' a long to a MemoryAddress.
 *
 * Here are some examples of binding recipes derived from C declarations, and according to the Windows ABI (recipes are
 * ABI-specific). Note that each argument has it's own recipe, which is indicated by '[number]:' (though, the only
 * example that has multiple arguments is the one using varargs).
 *
 * --------------------
 *
 * void f(int i);
 *
 * Argument bindings:
 * 0: VM_STORE(rcx, int.class) // move an 'int' into the RCX register
 *
 * Return bindings:
 * none
 *
 * --------------------
 *
 * void f(int* i);
 *
 * Argument bindings:
 * 0: UNBOX_ADDRESS // the 'MemoryAddress' is converted into a 'long'
 *    VM_STORE(rcx, long.class) // the 'long' is moved into the RCX register
 *
 * Return bindings:
 * none
 *
 * --------------------
 *
 * int* f();
 *
 * Argument bindings:
 * none
 *
 * Return bindings:
 * 0: VM_LOAD(rax, long) // load a 'long' from the RAX register
 *    BOX_ADDRESS // convert the 'long' into a 'MemoryAddress'
 *
 * --------------------
 *
 * typedef struct { // fits into single register
 *   int x;
 *   int y;
 * } MyStruct;
 *
 * void f(MyStruct ms);
 *
 * Argument bindings:
 * 0: BUFFER_LOAD(0, long.class) // From the struct's memory region, load a 'long' from offset '0'
 *    VM_STORE(rcx, long.class) // and copy that into the RCX register
 *
 * Return bindings:
 * none
 *
 * --------------------
 *
 * typedef struct { // does not fit into single register
 *   long long x;
 *   long long y;
 * } MyStruct;
 *
 * void f(MyStruct ms);
 *
 * For the Windows ABI:
 *
 * Argument bindings:
 * 0: COPY(16, 8) // copy the memory region containing the struct
 *    BASE_ADDRESS // take the base address of the copy
 *    UNBOX_ADDRESS // converts the base address to a 'long'
 *    VM_STORE(rcx, long.class) // moves the 'long' into the RCX register
 *
 * Return bindings:
 * none
 *
 * For the SysV ABI:
 *
 * Argument bindings:
 * 0: DUP // duplicates the MemoryRegion operand
 *    BUFFER_LOAD(0, long.class) // loads a 'long' from offset '0'
 *    VM_STORE(rdx, long.class) // moves the long into the RDX register
 *    BUFFER_LOAD(8, long.class) // loads a 'long' from offset '8'
 *    VM_STORE(rcx, long.class) // moves the long into the RCX register
 *
 * Return bindings:
 * none
 *
 * --------------------
 *
 * typedef struct { // fits into single register
 *   int x;
 *   int y;
 * } MyStruct;
 *
 * MyStruct f();
 *
 * Argument bindings:
 * none
 *
 * Return bindings:
 * 0: ALLOCATE(GroupLayout(C_INT, C_INT)) // allocate a buffer with the memory layout of the struct
 *    DUP // duplicate the allocated buffer
 *    VM_LOAD(rax, long.class) // loads a 'long' from rax
 *    BUFFER_STORE(0, long.class) // stores a 'long' at offset 0
 *
 * --------------------
 *
 * typedef struct { // does not fit into single register
 *   long long x;
 *   long long y;
 * } MyStruct;
 *
 * MyStruct f();
 *
 * !! uses synthetic argument, which is a pointer to a pre-allocated buffer
 *
 * Argument bindings:
 * 0: UNBOX_ADDRESS // unbox the MemoryAddress synthetic argument
 *    VM_STORE(rcx, long.class) // moves the 'long' into the RCX register
 *
 * Return bindings:
 * none
 *
 * --------------------
 *
 * void f(int dummy, ...); // varargs
 *
 * f(0, 10f); // passing a float
 *
 * Argument bindings:
 * 0: VM_STORE(rcx, int.class) // moves the 'int dummy' into the RCX register
 *
 * 1: DUP // duplicates the '10f' argument
 *    VM_STORE(rdx, float.class) // move one copy into the RDX register
 *    VM_STORE(xmm1, float.class) // moves the other copy into the xmm2 register
 *
 * Return bindings:
 * none
 *
 * --------------------
 */
public sealed interface Binding {

    void verify(Deque<Class<?>> stack);

    void interpret(Deque<Object> stack, StoreFunc storeFunc,
                   LoadFunc loadFunc, SegmentAllocator allocator);

    private static void checkType(Class<?> type) {
        if (!type.isPrimitive() || type == void.class)
            throw new IllegalArgumentException("Illegal type: " + type);
    }

    private static void checkOffset(long offset) {
        if (offset < 0)
            throw new IllegalArgumentException("Negative offset: " + offset);
    }

    private static void checkByteWidth(int byteWidth, Class<?> type) {
        if (byteWidth < 0 || byteWidth > Utils.byteWidthOfPrimitive(type))
            throw new IllegalArgumentException("Illegal byteWidth: " + byteWidth);
    }

    static VMStore vmStore(VMStorage storage, Class<?> type) {
        checkType(type);
        return new VMStore(storage, type);
    }

    static VMLoad vmLoad(VMStorage storage, Class<?> type) {
        checkType(type);
        return new VMLoad(storage, type);
    }

    static BufferStore bufferStore(long offset, Class<?> type) {
        return bufferStore(offset, type, Utils.byteWidthOfPrimitive(type));
    }

    static BufferStore bufferStore(long offset, Class<?> type, int byteWidth) {
        checkType(type);
        checkOffset(offset);
        checkByteWidth(byteWidth, type);
        return new BufferStore(offset, type, byteWidth);
    }

    static BufferLoad bufferLoad(long offset, Class<?> type) {
        return Binding.bufferLoad(offset, type, Utils.byteWidthOfPrimitive(type));
    }

    static BufferLoad bufferLoad(long offset, Class<?> type, int byteWidth) {
        checkType(type);
        checkOffset(offset);
        checkByteWidth(byteWidth, type);
        return new BufferLoad(offset, type, byteWidth);
    }

    static Copy copy(MemoryLayout layout) {
        return new Copy(layout.byteSize(), layout.byteAlignment());
    }

    static Allocate allocate(MemoryLayout layout) {
        return new Allocate(layout.byteSize(), layout.byteAlignment());
    }

    static BoxAddress boxAddressRaw(long size, long align) {
        return new BoxAddress(size, align, false);
    }

    static BoxAddress boxAddress(MemoryLayout layout) {
        return new BoxAddress(layout.byteSize(), layout.byteAlignment(), true);
    }

    static BoxAddress boxAddress(long byteSize) {
        return new BoxAddress(byteSize, 1, true);
    }

    static UnboxAddress unboxAddress() {
        return UnboxAddress.INSTANCE;
    }

    static Dup dup() {
        return Dup.INSTANCE;
    }

    static ShiftLeft shiftLeft(int shiftAmount) {
        if (shiftAmount <= 0)
            throw new IllegalArgumentException("shiftAmount must be positive");
        return new ShiftLeft(shiftAmount);
    }

    static ShiftRight shiftRight(int shiftAmount) {
        if (shiftAmount <= 0)
            throw new IllegalArgumentException("shiftAmount must be positive");
        return new ShiftRight(shiftAmount);
    }

    static Binding cast(Class<?> fromType, Class<?> toType) {
        if (fromType == int.class) {
            if (toType == boolean.class) {
                return Cast.INT_TO_BOOLEAN;
            } else if (toType == byte.class) {
                return Cast.INT_TO_BYTE;
            } else if (toType == short.class) {
                return Cast.INT_TO_SHORT;
            } else if (toType == char.class) {
                return Cast.INT_TO_CHAR;
            } else if (toType == long.class) {
                return Cast.INT_TO_LONG;
            }
        } else if (toType == int.class) {
            if (fromType == boolean.class) {
                return Cast.BOOLEAN_TO_INT;
            } else if (fromType == byte.class) {
                return Cast.BYTE_TO_INT;
            } else if (fromType == short.class) {
                return Cast.SHORT_TO_INT;
            } else if (fromType == char.class) {
                return Cast.CHAR_TO_INT;
            } else if (fromType == long.class) {
                return Cast.LONG_TO_INT;
            }
        } else if (fromType == long.class) {
            if (toType == byte.class) {
                return Cast.LONG_TO_BYTE;
            } else if (toType == short.class) {
                return Cast.LONG_TO_SHORT;
            } else if (toType == char.class) {
                return Cast.LONG_TO_CHAR;
            }
        } else if (toType == long.class) {
            if (fromType == byte.class) {
                return Cast.BYTE_TO_LONG;
            } else if (fromType == short.class) {
                return Cast.SHORT_TO_LONG;
            } else if (fromType == char.class) {
                return Cast.CHAR_TO_LONG;
            }
        }
        throw new IllegalArgumentException("Unknown conversion: " + fromType + " -> " + toType);
    }


    static Binding.Builder builder() {
        return new Binding.Builder();
    }

    /**
     * A builder helper class for generating lists of Bindings
     */
    class Builder {
        private final List<Binding> bindings = new ArrayList<>();

        private static boolean isSubIntType(Class<?> type) {
            return type == boolean.class || type == byte.class || type == short.class || type == char.class;
        }

        public Binding.Builder vmStore(VMStorage storage, Class<?> type) {
            if (isSubIntType(type)) {
                bindings.add(Binding.cast(type, int.class));
                type = int.class;
            }
            bindings.add(Binding.vmStore(storage, type));
            return this;
        }

        public Binding.Builder vmLoad(VMStorage storage, Class<?> type) {
            Class<?> loadType = type;
            if (isSubIntType(type)) {
                loadType = int.class;
            }
            bindings.add(Binding.vmLoad(storage, loadType));
            if (isSubIntType(type)) {
                bindings.add(Binding.cast(int.class, type));
            }
            return this;
        }

        public Binding.Builder bufferStore(long offset, Class<?> type) {
            bindings.add(Binding.bufferStore(offset, type));
            return this;
        }

        public Binding.Builder bufferStore(long offset, Class<?> type, int byteWidth) {
            bindings.add(Binding.bufferStore(offset, type, byteWidth));
            return this;
        }

        public Binding.Builder bufferLoad(long offset, Class<?> type) {
            bindings.add(Binding.bufferLoad(offset, type));
            return this;
        }

        public Binding.Builder bufferLoad(long offset, Class<?> type, int byteWidth) {
            bindings.add(Binding.bufferLoad(offset, type, byteWidth));
            return this;
        }

        public Binding.Builder copy(MemoryLayout layout) {
            bindings.add(Binding.copy(layout));
            return this;
        }

        public Binding.Builder allocate(MemoryLayout layout) {
            bindings.add(Binding.allocate(layout));
            return this;
        }

        public Binding.Builder boxAddressRaw(long size, long align) {
            bindings.add(Binding.boxAddressRaw(size, align));
            return this;
        }

        public Binding.Builder boxAddress(MemoryLayout layout) {
            bindings.add(Binding.boxAddress(layout));
            return this;
        }

        public Binding.Builder unboxAddress() {
            bindings.add(Binding.unboxAddress());
            return this;
        }

        public Binding.Builder dup() {
            bindings.add(Binding.dup());
            return this;
        }

        // Converts to long if needed then shifts left by the given number of Bytes.
        public Binding.Builder shiftLeft(int shiftAmount, Class<?> type) {
            if (type != long.class) {
                bindings.add(Binding.cast(type, long.class));
            }
            bindings.add(Binding.shiftLeft(shiftAmount));
            return this;
        }

        // Shifts right by the given number of Bytes then converts from long if needed.
        public Binding.Builder shiftRight(int shiftAmount, Class<?> type) {
            bindings.add(Binding.shiftRight(shiftAmount));
            if (type != long.class) {
                bindings.add(Binding.cast(long.class, type));
            }
            return this;
        }

        public List<Binding> build() {
            return List.copyOf(bindings);
        }
    }

    sealed interface Move extends Binding {
        VMStorage storage();
        Class<?> type();
    }

    /**
     * VM_STORE([storage location], [type])
     * Pops a [type] from the operand stack, and moves it to [storage location]
     * The [type] must be one of byte, short, char, int, long, float, or double
     */
    record VMStore(VMStorage storage, Class<?> type) implements Move {

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            Class<?> expectedType = type();
            SharedUtils.checkType(actualType, expectedType);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            storeFunc.store(storage(), stack.pop());
        }
    }

    /**
     * VM_LOAD([storage location], [type])
     * Loads a [type] from [storage location], and pushes it onto the operand stack.
     * The [type] must be one of byte, short, char, int, long, float, or double
     */
    record VMLoad(VMStorage storage, Class<?> type) implements Move {

        @Override
        public void verify(Deque<Class<?>> stack) {
            stack.push(type());
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            stack.push(loadFunc.load(storage(), type()));
        }
    }

    sealed interface Dereference extends Binding {
        long offset();
        Class<?> type();
    }

    /**
     * BUFFER_STORE([offset into memory region], [type], [width])
     * Pops a [type] from the operand stack, then pops a MemorySegment from the operand stack.
     * Stores [width] bytes of the value contained in the [type] to [offset into memory region].
     * The [type] must be one of byte, short, char, int, long, float, or double
     */
    record BufferStore(long offset, Class<?> type, int byteWidth) implements Dereference {

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> storeType = stack.pop();
            SharedUtils.checkType(storeType, type());
            Class<?> segmentType = stack.pop();
            SharedUtils.checkType(segmentType, MemorySegment.class);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            Object value = stack.pop();
            MemorySegment writeAddress = (MemorySegment) stack.pop();
            if (SharedUtils.isPowerOfTwo(byteWidth())) {
                // exact size match
                SharedUtils.write(writeAddress, offset(), type(), value);
            } else {
                // non-exact match, need to do chunked load
                long longValue = ((Number) value).longValue();
                // byteWidth is smaller than the width of 'type', so it will always be < 8 here
                int remaining = byteWidth();
                int chunkOffset = 0;
                do {
                    int chunkSize = Integer.highestOneBit(remaining); // next power of 2, in bytes
                    long writeOffset = offset() + SharedUtils.pickChunkOffset(chunkOffset, byteWidth(), chunkSize);
                    int shiftAmount = chunkOffset * Byte.SIZE;
                    switch (chunkSize) {
                        case 4 -> {
                            int writeChunk = (int) (((0xFFFF_FFFFL << shiftAmount) & longValue) >>> shiftAmount);
                            writeAddress.set(JAVA_INT_UNALIGNED, writeOffset, writeChunk);
                        }
                        case 2 -> {
                            short writeChunk = (short) (((0xFFFFL << shiftAmount) & longValue) >>> shiftAmount);
                            writeAddress.set(JAVA_SHORT_UNALIGNED, writeOffset, writeChunk);
                        }
                        case 1 -> {
                            byte writeChunk = (byte) (((0xFFL << shiftAmount) & longValue) >>> shiftAmount);
                            writeAddress.set(JAVA_BYTE, writeOffset, writeChunk);
                        }
                        default ->
                           throw new IllegalStateException("Unexpected chunk size for chunked write: " + chunkSize);
                    }
                    remaining -= chunkSize;
                    chunkOffset += chunkSize;
                } while (remaining != 0);
            }
        }
    }

    /**
     * BUFFER_LOAD([offset into memory region], [type], [width])
     * Pops a MemorySegment from the operand stack,
     * and then loads [width] bytes from it at [offset into memory region], into a [type].
     * The [type] must be one of byte, short, char, int, long, float, or double
     */
    record BufferLoad(long offset, Class<?> type, int byteWidth) implements Dereference {

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, MemorySegment.class);
            Class<?> newType = type();
            stack.push(newType);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            MemorySegment readAddress = (MemorySegment) stack.pop();
            if (SharedUtils.isPowerOfTwo(byteWidth())) {
                // exact size match
                stack.push(SharedUtils.read(readAddress, offset(), type()));
            } else {
                // non-exact match, need to do chunked load
                long result = 0;
                // byteWidth is smaller than the width of 'type', so it will always be < 8 here
                int remaining = byteWidth();
                int chunkOffset = 0;
                do {
                    int chunkSize = Integer.highestOneBit(remaining); // next power of 2
                    long readOffset = offset() + SharedUtils.pickChunkOffset(chunkOffset, byteWidth(), chunkSize);
                    long readChunk = switch (chunkSize) {
                        case 4 -> Integer.toUnsignedLong(readAddress.get(JAVA_INT_UNALIGNED, readOffset));
                        case 2 -> Short.toUnsignedLong(readAddress.get(JAVA_SHORT_UNALIGNED, readOffset));
                        case 1 -> Byte.toUnsignedLong(readAddress.get(JAVA_BYTE, readOffset));
                        default ->
                            throw new IllegalStateException("Unexpected chunk size for chunked write: " + chunkSize);
                    };
                    result |= readChunk << (chunkOffset * Byte.SIZE);
                    remaining -= chunkSize;
                    chunkOffset += chunkSize;
                } while (remaining != 0);

                if (type() == int.class) { // 3 byte write
                    stack.push((int) result);
                } else if (type() == long.class) { // 5, 6, 7 byte write
                    stack.push(result);
                } else {
                    throw new IllegalStateException("Unexpected type for chunked load: " + type());
                }
            }
        }
    }

    /**
     * COPY([size], [alignment])
     *   Creates a new MemorySegment with the given [size] and [alignment],
     *     and copies contents from a MemorySegment popped from the top of the operand stack into this new buffer,
     *     and pushes the new buffer onto the operand stack
     */
    record Copy(long size, long alignment) implements Binding {
        private static MemorySegment copyBuffer(MemorySegment operand, long size, long alignment, SegmentAllocator allocator) {
            return allocator.allocate(size, alignment)
                            .copyFrom(operand.asSlice(0, size));
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, MemorySegment.class);
            stack.push(MemorySegment.class);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            MemorySegment operand = (MemorySegment) stack.pop();
            MemorySegment copy = copyBuffer(operand, size, alignment, allocator);
            stack.push(copy);
        }
    }

    /**
     * ALLOCATE([size], [alignment])
     *   Creates a new MemorySegment with the give [size] and [alignment], and pushes it onto the operand stack.
     */
    record Allocate(long size, long alignment) implements Binding {
        private static MemorySegment allocateBuffer(long size, long alignment, SegmentAllocator allocator) {
            return allocator.allocate(size, alignment);
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            stack.push(MemorySegment.class);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            stack.push(allocateBuffer(size, alignment, allocator));
        }
    }

    /**
     * UNBOX_ADDRESS()
     * Pops a 'MemoryAddress' from the operand stack, converts it to a 'long',
     * with the given size, and pushes that onto the operand stack
     */
    record UnboxAddress() implements Binding {
        static final UnboxAddress INSTANCE = new UnboxAddress();

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, MemorySegment.class);
            stack.push(long.class);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            stack.push(SharedUtils.unboxSegment((MemorySegment)stack.pop()));
        }
    }

    /**
     * BOX_ADDRESS()
     * Pops a 'long' from the operand stack, converts it to a 'MemorySegment', with the given size and memory scope
     * (either the context scope, or the global scope), and pushes that onto the operand stack.
     */
    record BoxAddress(long size, long align, boolean needsScope) implements Binding {

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, long.class);
            stack.push(MemorySegment.class);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            MemorySegment segment = Utils.longToAddress((long) stack.pop(), size, align);
            if (needsScope) {
                segment = segment.reinterpret((Arena) allocator, null);
            }
            stack.push(segment);
        }
    }

    /**
     * DUP()
     *   Duplicates the value on the top of the operand stack (without popping it!),
     *   and pushes the duplicate onto the operand stack
     */
    record Dup() implements Binding {
        static final Dup INSTANCE = new Dup();

        @Override
        public void verify(Deque<Class<?>> stack) {
            stack.push(stack.peekLast());
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            stack.push(stack.peekLast());
        }
    }

    /**
     * ShiftLeft([shiftAmount])
     *   Shifts the Bytes on the top of the operand stack (64 bit unsigned).
     *   Shifts left by the given number of Bytes.
     */
    record ShiftLeft(int shiftAmount) implements Binding {

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> last = stack.pop();
            SharedUtils.checkType(last, long.class);
            stack.push(long.class);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            long l = (long) stack.pop();
            l <<= (shiftAmount * Byte.SIZE);
            stack.push(l);
        }
    }

    /**
     * ShiftRight([shiftAmount])
     *   Shifts the Bytes on the top of the operand stack (64 bit unsigned).
     *   Shifts right by the given number of Bytes.
     */
    record ShiftRight(int shiftAmount) implements Binding {

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> last = stack.pop();
            SharedUtils.checkType(last, long.class);
            stack.push(long.class);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            long l = (long) stack.pop();
            l >>>= (shiftAmount * Byte.SIZE);
            stack.push(l);
        }
    }

    /**
     * CAST([fromType], [toType])
     *   Pop a [fromType] from the stack, convert it to [toType], and push the resulting
     *   value onto the stack.
     *
     */
    enum Cast implements Binding {
        INT_TO_BOOLEAN(int.class, boolean.class) {
            @Override
            public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                                  LoadFunc loadFunc, SegmentAllocator allocator) {
                // implement least significant byte non-zero test
                int arg = (int) stack.pop();
                boolean result = Utils.byteToBoolean((byte) arg);
                stack.push(result);
            }
        },
        INT_TO_BYTE(int.class, byte.class),
        INT_TO_CHAR(int.class, char.class),
        INT_TO_SHORT(int.class, short.class),
        INT_TO_LONG(int.class, long.class),

        BOOLEAN_TO_INT(boolean.class, int.class),
        BYTE_TO_INT(byte.class, int.class),
        CHAR_TO_INT(char.class, int.class),
        SHORT_TO_INT(short.class, int.class),
        LONG_TO_INT(long.class, int.class),

        LONG_TO_BYTE(long.class, byte.class),
        LONG_TO_SHORT(long.class, short.class),
        LONG_TO_CHAR(long.class, char.class),

        BYTE_TO_LONG(byte.class, long.class),
        SHORT_TO_LONG(short.class, long.class),
        CHAR_TO_LONG(char.class, long.class);

        private final Class<?> fromType;
        private final Class<?> toType;

        Cast(Class<?> fromType, Class<?> toType) {
            this.fromType = fromType;
            this.toType = toType;
        }

        public Class<?> fromType() {
            return fromType;
        }

        public Class<?> toType() {
            return toType;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, fromType);
            stack.push(toType);
        }

        @Override
        public void interpret(Deque<Object> stack, StoreFunc storeFunc,
                              LoadFunc loadFunc, SegmentAllocator allocator) {
            Object arg = stack.pop();
            MethodHandle converter = MethodHandles.explicitCastArguments(MethodHandles.identity(toType),
                    MethodType.methodType(toType, fromType));
            try {
                Object result = converter.invoke(arg);
                stack.push(result);
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }
    }
}
