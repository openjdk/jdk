/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeScope;
import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.Utils;
import jdk.internal.invoke.NativeEntryPoint;
import jdk.internal.invoke.VMStorageProxy;
import sun.security.action.GetPropertyAction;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.empty;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.tryFinally;
import static java.lang.invoke.MethodType.methodType;
import static jdk.internal.foreign.abi.SharedUtils.Allocator.THROWING_ALLOCATOR;
import static jdk.internal.foreign.abi.SharedUtils.DEFAULT_ALLOCATOR;
import static sun.security.action.GetBooleanAction.privilegedGetProperty;

/**
 * This class implements native call invocation through a so called 'universal adapter'. A universal adapter takes
 * an array of longs together with a call 'recipe', which is used to move the arguments in the right places as
 * expected by the system ABI.
 */
public class ProgrammableInvoker {
    private static final boolean DEBUG =
        privilegedGetProperty("jdk.internal.foreign.ProgrammableInvoker.DEBUG");
    private static final boolean USE_SPEC = Boolean.parseBoolean(
        GetPropertyAction.privilegedGetProperty("jdk.internal.foreign.ProgrammableInvoker.USE_SPEC", "true"));
    private static final boolean USE_INTRINSICS = Boolean.parseBoolean(
        GetPropertyAction.privilegedGetProperty("jdk.internal.foreign.ProgrammableInvoker.USE_INTRINSICS", "true"));

    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();

    private static final VarHandle VH_LONG = MemoryLayouts.JAVA_LONG.varHandle(long.class);

    private static final MethodHandle MH_INVOKE_MOVES;
    private static final MethodHandle MH_INVOKE_INTERP_BINDINGS;

    private static final MethodHandle MH_MAKE_SCOPE;
    private static final MethodHandle MH_CLOSE_SCOPE;
    private static final MethodHandle MH_WRAP_SCOPE;

    private static final Map<ABIDescriptor, Long> adapterStubs = new ConcurrentHashMap<>();

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_INVOKE_MOVES = lookup.findVirtual(ProgrammableInvoker.class, "invokeMoves",
                    methodType(Object.class, Object[].class, Binding.VMStore[].class, Binding.VMLoad[].class));
            MH_INVOKE_INTERP_BINDINGS = lookup.findVirtual(ProgrammableInvoker.class, "invokeInterpBindings",
                    methodType(Object.class, Object[].class, MethodHandle.class, Map.class, Map.class));
            MH_MAKE_SCOPE = lookup.findStatic(NativeScope.class, "boundedScope",
                    methodType(NativeScope.class, long.class));
            MH_CLOSE_SCOPE = lookup.findVirtual(NativeScope.class, "close",
                    methodType(void.class));
            MH_WRAP_SCOPE = lookup.findStatic(SharedUtils.Allocator.class, "ofScope",
                    methodType(SharedUtils.Allocator.class, NativeScope.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private final ABIDescriptor abi;
    private final BufferLayout layout;
    private final long stackArgsBytes;

    private final CallingSequence callingSequence;

    private final Addressable addr;
    private final long stubAddress;

    private final long bufferCopySize;

    public ProgrammableInvoker(ABIDescriptor abi, Addressable addr, CallingSequence callingSequence) {
        this.abi = abi;
        this.layout = BufferLayout.of(abi);
        this.stubAddress = adapterStubs.computeIfAbsent(abi, key -> generateAdapter(key, layout));

        this.addr = addr;
        this.callingSequence = callingSequence;

        this.stackArgsBytes = argMoveBindingsStream(callingSequence)
                .map(Binding.VMStore::storage)
                .filter(s -> abi.arch.isStackType(s.type()))
                .count()
                * abi.arch.typeSize(abi.arch.stackType());

        this.bufferCopySize = bufferCopySize(callingSequence);
    }

    private static long bufferCopySize(CallingSequence callingSequence) {
        // FIXME: > 16 bytes alignment might need extra space since the
        // starting address of the allocator might be un-aligned.
        long size = 0;
        for (int i = 0; i < callingSequence.argumentCount(); i++) {
            List<Binding> bindings = callingSequence.argumentBindings(i);
            for (Binding b : bindings) {
                if (b instanceof Binding.Copy) {
                    Binding.Copy c = (Binding.Copy) b;
                    size = Utils.alignUp(size, c.alignment());
                    size += c.size();
                }
            }
        }
        return size;
    }

    public MethodHandle getBoundMethodHandle() {
        Binding.VMStore[] argMoves = argMoveBindingsStream(callingSequence).toArray(Binding.VMStore[]::new);
        Class<?>[] argMoveTypes = Arrays.stream(argMoves).map(Binding.VMStore::type).toArray(Class<?>[]::new);

        Binding.VMLoad[] retMoves = retMoveBindings(callingSequence);
        Class<?> returnType = retMoves.length == 0
                ? void.class
                : retMoves.length == 1
                    ? retMoves[0].type()
                    : Object[].class;

        MethodType leafType = methodType(returnType, argMoveTypes);

        MethodHandle handle = insertArguments(MH_INVOKE_MOVES.bindTo(this), 1, argMoves, retMoves)
                                            .asCollector(Object[].class, leafType.parameterCount())
                                            .asType(leafType);

        boolean isSimple = !(retMoves.length > 1);
        boolean usesStackArgs = stackArgsBytes != 0;
        if (USE_INTRINSICS && isSimple && !usesStackArgs) {
            NativeEntryPoint nep = NativeEntryPoint.make(
                addr.address().toRawLongValue(),
                "native_call",
                abi,
                toStorageArray(argMoves),
                toStorageArray(retMoves),
                !callingSequence.isTrivial(),
                leafType
            );

            handle = JLIA.nativeMethodHandle(nep, handle);
        }

        if (USE_SPEC && isSimple) {
            handle = specialize(handle);
         } else {
            Map<VMStorage, Integer> argIndexMap = indexMap(argMoves);
            Map<VMStorage, Integer> retIndexMap = indexMap(retMoves);

            handle = insertArguments(MH_INVOKE_INTERP_BINDINGS.bindTo(this), 1, handle, argIndexMap, retIndexMap);
            handle = handle.asCollector(Object[].class, callingSequence.methodType().parameterCount())
                                             .asType(callingSequence.methodType());
         }

        return handle;
    }

    private Stream<Binding.VMStore> argMoveBindingsStream(CallingSequence callingSequence) {
        return callingSequence.argumentBindings()
                .filter(Binding.VMStore.class::isInstance)
                .map(Binding.VMStore.class::cast);
    }

    private Binding.VMLoad[] retMoveBindings(CallingSequence callingSequence) {
        return callingSequence.returnBindings().stream()
                .filter(Binding.VMLoad.class::isInstance)
                .map(Binding.VMLoad.class::cast)
                .toArray(Binding.VMLoad[]::new);
    }


    private VMStorageProxy[] toStorageArray(Binding.Move[] moves) {
        return Arrays.stream(moves).map(Binding.Move::storage).toArray(VMStorage[]::new);
    }

    private MethodHandle specialize(MethodHandle leafHandle) {
        MethodType highLevelType = callingSequence.methodType();
        MethodType leafType = leafHandle.type();

        MethodHandle specializedHandle = leafHandle; // initial

        int argInsertPos = -1;
        int argAllocatorPos = -1;
        if (bufferCopySize > 0) {
            argAllocatorPos = 0;
            specializedHandle = dropArguments(specializedHandle, argAllocatorPos, SharedUtils.Allocator.class);
            argInsertPos++;
        }
        for (int i = 0; i < highLevelType.parameterCount(); i++) {
            List<Binding> bindings = callingSequence.argumentBindings(i);
            argInsertPos += bindings.stream().filter(Binding.VMStore.class::isInstance).count() + 1;
            // We interpret the bindings in reverse since we have to construct a MethodHandle from the bottom up
            for (int j = bindings.size() - 1; j >= 0; j--) {
                Binding binding = bindings.get(j);
                if (binding.tag() == Binding.Tag.VM_STORE) {
                    argInsertPos--;
                } else {
                    specializedHandle = binding.specialize(specializedHandle, argInsertPos, argAllocatorPos);
                }
            }
        }

        if (highLevelType.returnType() != void.class) {
            MethodHandle returnFilter = identity(highLevelType.returnType());
            int retAllocatorPos = 0;
            int retInsertPos = 1;
            returnFilter = dropArguments(returnFilter, retAllocatorPos, SharedUtils.Allocator.class);
            List<Binding> bindings = callingSequence.returnBindings();
            for (int j = bindings.size() - 1; j >= 0; j--) {
                Binding binding = bindings.get(j);
                returnFilter = binding.specialize(returnFilter, retInsertPos, retAllocatorPos);
            }
            returnFilter = insertArguments(returnFilter, retAllocatorPos, DEFAULT_ALLOCATOR);
            specializedHandle = MethodHandles.filterReturnValue(specializedHandle, returnFilter);
        }

        if (bufferCopySize > 0) {
            // insert try-finally to close the NativeScope used for Binding.Copy
            MethodHandle closer = leafType.returnType() == void.class
                  // (Throwable, NativeScope) -> void
                ? collectArguments(empty(methodType(void.class, Throwable.class)), 1, MH_CLOSE_SCOPE)
                  // (Throwable, V, NativeScope) -> V
                : collectArguments(dropArguments(identity(specializedHandle.type().returnType()), 0, Throwable.class),
                                   2, MH_CLOSE_SCOPE);
            // Handle takes a SharedUtils.Allocator, so need to wrap our NativeScope
            specializedHandle = filterArguments(specializedHandle, argAllocatorPos, MH_WRAP_SCOPE);
            specializedHandle = tryFinally(specializedHandle, closer);
            MethodHandle makeScopeHandle = insertArguments(MH_MAKE_SCOPE, 0, bufferCopySize);
            specializedHandle = collectArguments(specializedHandle, argAllocatorPos, makeScopeHandle);
        }
        return specializedHandle;
    }

    private static Map<VMStorage, Integer> indexMap(Binding.Move[] moves) {
        return IntStream.range(0, moves.length)
                        .boxed()
                        .collect(Collectors.toMap(i -> moves[i].storage(), i -> i));
    }

    /**
     * Does a native invocation by moving primitive values from the arg array into an intermediate buffer
     * and calling the assembly stub that forwards arguments from the buffer to the target function
     *
     * @param args an array of primitive values to be copied in to the buffer
     * @param argBindings Binding.Move values describing how arguments should be copied
     * @param returnBindings Binding.Move values describing how return values should be copied
     * @return null, a single primitive value, or an Object[] of primitive values
     */
    Object invokeMoves(Object[] args, Binding.VMStore[] argBindings, Binding.VMLoad[] returnBindings) {
        MemorySegment stackArgsSeg = null;
        try (MemorySegment argBuffer = MemorySegment.allocateNative(layout.size, 64)) {
            if (stackArgsBytes > 0) {
                stackArgsSeg = MemorySegment.allocateNative(stackArgsBytes, 8);
            }

            VH_LONG.set(argBuffer.asSlice(layout.arguments_next_pc), addr.address().toRawLongValue());
            VH_LONG.set(argBuffer.asSlice(layout.stack_args_bytes), stackArgsBytes);
            VH_LONG.set(argBuffer.asSlice(layout.stack_args), stackArgsSeg == null ? 0L : stackArgsSeg.address().toRawLongValue());

            for (int i = 0; i < argBindings.length; i++) {
                Binding.VMStore binding = argBindings[i];
                VMStorage storage = binding.storage();
                MemorySegment ptr = abi.arch.isStackType(storage.type())
                    ? stackArgsSeg.asSlice(storage.index() * abi.arch.typeSize(abi.arch.stackType()))
                    : argBuffer.asSlice(layout.argOffset(storage));
                SharedUtils.writeOverSized(ptr, binding.type(), args[i]);
            }

            if (DEBUG) {
                System.err.println("Buffer state before:");
                layout.dump(abi.arch, argBuffer, System.err);
            }

            invokeNative(stubAddress, argBuffer.address().toRawLongValue());

            if (DEBUG) {
                System.err.println("Buffer state after:");
                layout.dump(abi.arch, argBuffer, System.err);
            }

            if (returnBindings.length == 0) {
                return null;
            } else if (returnBindings.length == 1) {
                Binding.VMLoad move = returnBindings[0];
                VMStorage storage = move.storage();
                return SharedUtils.read(argBuffer.asSlice(layout.retOffset(storage)), move.type());
            } else { // length > 1
                Object[] returns = new Object[returnBindings.length];
                for (int i = 0; i < returnBindings.length; i++) {
                    Binding.VMLoad move = returnBindings[i];
                    VMStorage storage = move.storage();
                    returns[i] = SharedUtils.read(argBuffer.asSlice(layout.retOffset(storage)), move.type());
                }
                return returns;
            }
        } finally {
            if (stackArgsSeg != null) {
                stackArgsSeg.close();
            }
        }
    }

    Object invokeInterpBindings(Object[] args, MethodHandle leaf,
                                Map<VMStorage, Integer> argIndexMap,
                                Map<VMStorage, Integer> retIndexMap) throws Throwable {
        SharedUtils.Allocator unboxAllocator = bufferCopySize != 0
                ? SharedUtils.Allocator.ofScope(NativeScope.boundedScope(bufferCopySize))
                : THROWING_ALLOCATOR;
        try (unboxAllocator) {
            // do argument processing, get Object[] as result
            Object[] moves = new Object[leaf.type().parameterCount()];
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                BindingInterpreter.unbox(arg, callingSequence.argumentBindings(i),
                        (storage, type, value) -> {
                            moves[argIndexMap.get(storage)] = value;
                        }, unboxAllocator);
            }

            // call leaf
            Object o = leaf.invokeWithArguments(moves);

            // return value processing
            if (o == null) {
                return null;
            } else if (o instanceof Object[]) {
                Object[] oArr = (Object[]) o;
                return BindingInterpreter.box(callingSequence.returnBindings(),
                        (storage, type) -> oArr[retIndexMap.get(storage)], DEFAULT_ALLOCATOR);
            } else {
                return BindingInterpreter.box(callingSequence.returnBindings(), (storage, type) -> o,
                        DEFAULT_ALLOCATOR);
            }
        }
    }

    //natives

    static native void invokeNative(long adapterStub, long buff);
    static native long generateAdapter(ABIDescriptor abi, BufferLayout layout);

    private static native void registerNatives();
    static {
        registerNatives();
    }
}

