/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.NativeMemorySegmentImpl;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
public interface Binding {

    /**
     * A binding context is used as an helper to carry out evaluation of certain bindings; for instance,
     * it helps {@link Allocate} bindings, by providing the {@link SegmentAllocator} that should be used for
     * the allocation operation, or {@link BoxAddress} bindings, by providing the {@link MemorySession} that
     * should be used to create an unsafe struct from a memory address.
     */
    class Context implements AutoCloseable {
        private final SegmentAllocator allocator;
        private final MemorySession session;

        private Context(SegmentAllocator allocator, MemorySession session) {
            this.allocator = allocator;
            this.session = session;
        }

        public SegmentAllocator allocator() {
            return allocator;
        }

        public MemorySession session() {
            return session;
        }

        @Override
        public void close() {
            session().close();
        }

        /**
         * Create a binding context from given native scope.
         */
        public static Context ofBoundedAllocator(long size) {
            MemorySession scope = MemorySession.openConfined();
            return new Context(SegmentAllocator.newNativeArena(size, scope), scope);
        }

        /**
         * Create a binding context from given segment allocator. The resulting context will throw when
         * the context's scope is accessed.
         */
        public static Context ofAllocator(SegmentAllocator allocator) {
            return new Context(allocator, null) {
                @Override
                public MemorySession session() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        /**
         * Create a binding context from given scope. The resulting context will throw when
         * the context's allocator is accessed.
         */
        public static Context ofSession() {
            MemorySession scope = MemorySession.openConfined();
            return new Context(null, scope) {
                @Override
                public SegmentAllocator allocator() { throw new UnsupportedOperationException(); }
            };
        }

        /**
         * Dummy binding context. Throws exceptions when attempting to access scope, return a throwing allocator, and has
         * an idempotent {@link #close()}.
         */
        public static final Context DUMMY = new Context(null, null) {
            @Override
            public SegmentAllocator allocator() {
                return SharedUtils.THROWING_ALLOCATOR;
            }

            @Override
            public MemorySession session() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                // do nothing
            }
        };
    }

    enum Tag {
        VM_STORE,
        VM_LOAD,
        BUFFER_STORE,
        BUFFER_LOAD,
        COPY_BUFFER,
        ALLOC_BUFFER,
        BOX_ADDRESS,
        UNBOX_ADDRESS,
        DUP,
        CAST
    }

    Tag tag();

    void verify(Deque<Class<?>> stack);

    void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                   BindingInterpreter.LoadFunc loadFunc, Context context);

    private static void checkType(Class<?> type) {
        if (!type.isPrimitive() || type == void.class)
            throw new IllegalArgumentException("Illegal type: " + type);
    }

    private static void checkOffset(long offset) {
        if (offset < 0)
            throw new IllegalArgumentException("Negative offset: " + offset);
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
        checkType(type);
        checkOffset(offset);
        return new BufferStore(offset, type);
    }

    static BufferLoad bufferLoad(long offset, Class<?> type) {
        checkType(type);
        checkOffset(offset);
        return new BufferLoad(offset, type);
    }

    static Copy copy(MemoryLayout layout) {
        return new Copy(layout.byteSize(), layout.byteAlignment());
    }

    static Allocate allocate(MemoryLayout layout) {
        return new Allocate(layout.byteSize(), layout.byteAlignment());
    }

    static BoxAddress boxAddressRaw(long size) {
        return new BoxAddress(size, false);
    }

    static BoxAddress boxAddress(MemoryLayout layout) {
        return new BoxAddress(layout.byteSize(), true);
    }

    static BoxAddress boxAddress(long byteSize) {
        return new BoxAddress(byteSize, true);
    }

    static UnboxAddress unboxAddress() {
        return UnboxAddress.INSTANCE;
    }

    static Dup dup() {
        return Dup.INSTANCE;
    }

    static Binding cast(Class<?> fromType, Class<?> toType) {
        return new Cast(fromType, toType);
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

        public Binding.Builder bufferLoad(long offset, Class<?> type) {
            bindings.add(Binding.bufferLoad(offset, type));
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

        public Binding.Builder boxAddressRaw(long size) {
            bindings.add(Binding.boxAddressRaw(size));
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

        public List<Binding> build() {
            return List.copyOf(bindings);
        }
    }

    interface Move extends Binding {
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
        public Tag tag() {
            return Tag.VM_STORE;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            Class<?> expectedType = type();
            SharedUtils.checkType(actualType, expectedType);
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
            storeFunc.store(storage(), type(), stack.pop());
        }
    }

    /**
     * VM_LOAD([storage location], [type])
     * Loads a [type] from [storage location], and pushes it onto the operand stack.
     * The [type] must be one of byte, short, char, int, long, float, or double
     */
    record VMLoad(VMStorage storage, Class<?> type) implements Move {
        @Override
        public Tag tag() {
            return Tag.VM_LOAD;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            stack.push(type());
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
            stack.push(loadFunc.load(storage(), type()));
        }
    }

    interface Dereference extends Binding {
        long offset();
        Class<?> type();
    }

    /**
     * BUFFER_STORE([offset into memory region], [type])
     * Pops a [type] from the operand stack, then pops a MemorySegment from the operand stack.
     * Stores the [type] to [offset into memory region].
     * The [type] must be one of byte, short, char, int, long, float, or double
     */
    record BufferStore(long offset, Class<?> type) implements Dereference {
        @Override
        public Tag tag() {
            return Tag.BUFFER_STORE;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> storeType = stack.pop();
            SharedUtils.checkType(storeType, type());
            Class<?> segmentType = stack.pop();
            SharedUtils.checkType(segmentType, MemorySegment.class);
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
            Object value = stack.pop();
            MemorySegment operand = (MemorySegment) stack.pop();
            MemorySegment writeAddress = operand.asSlice(offset());
            SharedUtils.write(writeAddress, type(), value);
        }
    }

    /**
     * BUFFER_LOAD([offset into memory region], [type])
     * Pops a [type], and then a MemorySegment from the operand stack,
     * and then stores [type] to [offset into memory region] of the MemorySegment.
     * The [type] must be one of byte, short, char, int, long, float, or double
     */
    record BufferLoad(long offset, Class<?> type) implements Dereference {
        @Override
        public Tag tag() {
            return Tag.BUFFER_LOAD;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, MemorySegment.class);
            Class<?> newType = type();
            stack.push(newType);
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
            MemorySegment operand = (MemorySegment) stack.pop();
            MemorySegment readAddress = operand.asSlice(offset());
            stack.push(SharedUtils.read(readAddress, type()));
        }
    }

    /**
     * COPY([size], [alignment])
     *   Creates a new MemorySegment with the given [size] and [alignment],
     *     and copies contents from a MemorySegment popped from the top of the operand stack into this new buffer,
     *     and pushes the new buffer onto the operand stack
     */
    record Copy(long size, long alignment) implements Binding {
        private static MemorySegment copyBuffer(MemorySegment operand, long size, long alignment, Context context) {
            return context.allocator().allocate(size, alignment)
                            .copyFrom(operand.asSlice(0, size));
        }

        @Override
        public Tag tag() {
            return Tag.COPY_BUFFER;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, MemorySegment.class);
            stack.push(MemorySegment.class);
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
            MemorySegment operand = (MemorySegment) stack.pop();
            MemorySegment copy = copyBuffer(operand, size, alignment, context);
            stack.push(copy);
        }
    }

    /**
     * ALLOCATE([size], [alignment])
     *   Creates a new MemorySegment with the give [size] and [alignment], and pushes it onto the operand stack.
     */
    record Allocate(long size, long alignment) implements Binding {
        private static MemorySegment allocateBuffer(long size, long alignment, Context context) {
            return context.allocator().allocate(size, alignment);
        }

        @Override
        public Tag tag() {
            return Tag.ALLOC_BUFFER;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            stack.push(MemorySegment.class);
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
            stack.push(allocateBuffer(size, alignment, context));
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
        public Tag tag() {
            return Tag.UNBOX_ADDRESS;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, MemorySegment.class);
            stack.push(long.class);
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
            stack.push(((MemorySegment)stack.pop()).address());
        }
    }

    /**
     * BOX_ADDRESS()
     * Pops a 'long' from the operand stack, converts it to a 'MemorySegment', with the given size and memory session
     * (either the context session, or the global session), and pushes that onto the operand stack.
     */
    record BoxAddress(long size, boolean needsSession) implements Binding {

        @Override
        public Tag tag() {
            return Tag.BOX_ADDRESS;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, long.class);
            stack.push(MemorySegment.class);
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
            MemorySession session = needsSession ?
                    context.session() : MemorySession.global();
            stack.push(NativeMemorySegmentImpl.makeNativeSegmentUnchecked((long) stack.pop(), size, session));
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
        public Tag tag() {
            return Tag.DUP;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            stack.push(stack.peekLast());
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
            stack.push(stack.peekLast());
        }
    }

    /**
     * CAST([fromType], [toType])
     *   Pop a [fromType] from the stack, convert it to [toType], and push the resulting
     *   value onto the stack.
     *
     */
    record Cast(Class<?> fromType, Class<?> toType) implements Binding {

        @Override
        public Tag tag() {
            return Tag.CAST;
        }

        @Override
        public void verify(Deque<Class<?>> stack) {
            Class<?> actualType = stack.pop();
            SharedUtils.checkType(actualType, fromType);
            stack.push(toType);
        }

        @Override
        public void interpret(Deque<Object> stack, BindingInterpreter.StoreFunc storeFunc,
                              BindingInterpreter.LoadFunc loadFunc, Context context) {
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
