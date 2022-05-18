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

import java.lang.foreign.Addressable;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import sun.security.action.GetPropertyAction;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodType.methodType;

/**
 * This class implements native call invocation through a so called 'universal adapter'. A universal adapter takes
 * an array of longs together with a call 'recipe', which is used to move the arguments in the right places as
 * expected by the system ABI.
 */
public class ProgrammableInvoker {
    private static final boolean USE_SPEC = Boolean.parseBoolean(
        GetPropertyAction.privilegedGetProperty("jdk.internal.foreign.ProgrammableInvoker.USE_SPEC", "true"));

    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();

    private static final MethodHandle MH_INVOKE_INTERP_BINDINGS;
    private static final MethodHandle MH_WRAP_ALLOCATOR;
    private static final MethodHandle MH_ALLOCATE_RETURN_BUFFER;
    private static final MethodHandle MH_CHECK_SYMBOL;

    private static final MethodHandle EMPTY_OBJECT_ARRAY_HANDLE = MethodHandles.constant(Object[].class, new Object[0]);

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_INVOKE_INTERP_BINDINGS = lookup.findVirtual(ProgrammableInvoker.class, "invokeInterpBindings",
                    methodType(Object.class, SegmentAllocator.class, Object[].class, InvocationData.class));
            MH_WRAP_ALLOCATOR = lookup.findStatic(Binding.Context.class, "ofAllocator",
                    methodType(Binding.Context.class, SegmentAllocator.class));
            MH_CHECK_SYMBOL = lookup.findStatic(SharedUtils.class, "checkSymbol",
                    methodType(void.class, Addressable.class));
            MH_ALLOCATE_RETURN_BUFFER = lookup.findStatic(ProgrammableInvoker.class, "allocateReturnBuffer",
                    methodType(MemorySegment.class, Binding.Context.class, long.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private final ABIDescriptor abi;
    private final CallingSequence callingSequence;

    public ProgrammableInvoker(ABIDescriptor abi, CallingSequence callingSequence) {
        this.abi = abi;
        this.callingSequence = callingSequence;
    }

    public MethodHandle getBoundMethodHandle() {
        Binding.VMStore[] argMoves = argMoveBindingsStream(callingSequence).toArray(Binding.VMStore[]::new);
        Class<?>[] argMoveTypes = Arrays.stream(argMoves).map(Binding.VMStore::type).toArray(Class<?>[]::new);

        Binding.VMLoad[] retMoves = retMoveBindings(callingSequence);
        Class<?> returnType = retMoves.length == 1 ? retMoves[0].type() : void.class;

        MethodType leafType = methodType(returnType, argMoveTypes);

        NativeEntryPoint nep = NativeEntryPoint.make(
            abi,
            toStorageArray(argMoves),
            toStorageArray(retMoves),
            leafType,
            callingSequence.needsReturnBuffer()
        );
        MethodHandle handle = JLIA.nativeMethodHandle(nep);

        if (USE_SPEC) {
            handle = specialize(handle);
         } else {
            Map<VMStorage, Integer> argIndexMap = SharedUtils.indexMap(argMoves);
            Map<VMStorage, Integer> retIndexMap = SharedUtils.indexMap(retMoves);

            InvocationData invData = new InvocationData(handle, argIndexMap, retIndexMap);
            handle = insertArguments(MH_INVOKE_INTERP_BINDINGS.bindTo(this), 2, invData);
            MethodType interpType = callingSequence.methodType();
            if (callingSequence.needsReturnBuffer()) {
                // Return buffer is supplied by invokeInterpBindings
                assert interpType.parameterType(0) == MemorySegment.class;
                interpType = interpType.dropParameterTypes(0, 1);
            }
            MethodHandle collectorInterp = makeCollectorHandle(interpType);
            handle = collectArguments(handle, 1, collectorInterp);
            handle = handle.asType(handle.type().changeReturnType(interpType.returnType()));
         }

        assert handle.type().parameterType(0) == SegmentAllocator.class;
        assert handle.type().parameterType(1) == Addressable.class;
        handle = foldArguments(handle, 1, MH_CHECK_SYMBOL);

        handle = SharedUtils.swapArguments(handle, 0, 1); // normalize parameter order

        return handle;
    }

    private static MemorySegment allocateReturnBuffer(Binding.Context context, long size) {
        return context.allocator().allocate(size);
    }

    // Funnel from type to Object[]
    private static MethodHandle makeCollectorHandle(MethodType type) {
        return type.parameterCount() == 0
            ? EMPTY_OBJECT_ARRAY_HANDLE
            : identity(Object[].class)
                .asCollector(Object[].class, type.parameterCount())
                .asType(type.changeReturnType(Object[].class));
    }

    private Stream<Binding.VMStore> argMoveBindingsStream(CallingSequence callingSequence) {
        return callingSequence.argumentBindings()
                .filter(Binding.VMStore.class::isInstance)
                .map(Binding.VMStore.class::cast);
    }

    private Binding.VMLoad[] retMoveBindings(CallingSequence callingSequence) {
        return retMoveBindingsStream(callingSequence).toArray(Binding.VMLoad[]::new);
    }

    private Stream<Binding.VMLoad> retMoveBindingsStream(CallingSequence callingSequence) {
        return callingSequence.returnBindings().stream()
                .filter(Binding.VMLoad.class::isInstance)
                .map(Binding.VMLoad.class::cast);
    }

    private VMStorage[] toStorageArray(Binding.Move[] moves) {
        return Arrays.stream(moves).map(Binding.Move::storage).toArray(VMStorage[]::new);
    }

    private MethodHandle specialize(MethodHandle leafHandle) {
        MethodType highLevelType = callingSequence.methodType();

        int argInsertPos = 0;
        int argContextPos = 0;

        MethodHandle specializedHandle = dropArguments(leafHandle, argContextPos, Binding.Context.class);
        for (int i = 0; i < highLevelType.parameterCount(); i++) {
            List<Binding> bindings = callingSequence.argumentBindings(i);
            argInsertPos += ((int) bindings.stream().filter(Binding.VMStore.class::isInstance).count()) + 1;
            // We interpret the bindings in reverse since we have to construct a MethodHandle from the bottom up
            for (int j = bindings.size() - 1; j >= 0; j--) {
                Binding binding = bindings.get(j);
                if (binding.tag() == Binding.Tag.VM_STORE) {
                    argInsertPos--;
                } else {
                    specializedHandle = binding.specialize(specializedHandle, argInsertPos, argContextPos);
                }
            }
        }

        if (highLevelType.returnType() != void.class) {
            MethodHandle returnFilter = identity(highLevelType.returnType());
            int retBufPos = -1;
            long retBufReadOffset = -1;
            int retContextPos = 0;
            int retInsertPos = 1;
            if (callingSequence.needsReturnBuffer()) {
                retBufPos = 0;
                retBufReadOffset = callingSequence.returnBufferSize();
                retContextPos++;
                retInsertPos++;
                returnFilter = dropArguments(returnFilter, retBufPos, MemorySegment.class);
            }
            returnFilter = dropArguments(returnFilter, retContextPos, Binding.Context.class);
            List<Binding> bindings = callingSequence.returnBindings();
            for (int j = bindings.size() - 1; j >= 0; j--) {
                Binding binding = bindings.get(j);
                if (callingSequence.needsReturnBuffer() && binding.tag() == Binding.Tag.VM_LOAD) {
                    // spacial case this, since we need to update retBufReadOffset as well
                    Binding.VMLoad load = (Binding.VMLoad) binding;
                    ValueLayout layout = MemoryLayout.valueLayout(load.type(), ByteOrder.nativeOrder()).withBitAlignment(8);
                    // since we iterate the bindings in reverse, we have to compute the offset in reverse as well
                    retBufReadOffset -= abi.arch.typeSize(load.storage().type());
                    MethodHandle loadHandle = MethodHandles.insertCoordinates(MethodHandles.memorySegmentViewVarHandle(layout), 1, retBufReadOffset)
                            .toMethodHandle(VarHandle.AccessMode.GET);

                    returnFilter = MethodHandles.collectArguments(returnFilter, retInsertPos, loadHandle);
                    assert returnFilter.type().parameterType(retInsertPos - 1) == MemorySegment.class;
                    assert returnFilter.type().parameterType(retInsertPos - 2) == MemorySegment.class;
                    returnFilter = SharedUtils.mergeArguments(returnFilter, retBufPos, retInsertPos);
                    // to (... MemorySegment, MemorySegment, <primitive>, ...)
                    // from (... MemorySegment, MemorySegment, ...)
                    retInsertPos -= 2; // set insert pos back to the first MS (later DUP binding will merge the 2 MS)
                } else {
                    returnFilter = binding.specialize(returnFilter, retInsertPos, retContextPos);
                    if (callingSequence.needsReturnBuffer() && binding.tag() == Binding.Tag.BUFFER_STORE) {
                        // from (... MemorySegment, ...)
                        // to (... MemorySegment, MemorySegment, <primitive>, ...)
                        retInsertPos += 2; // set insert pos to <primitive>
                        assert returnFilter.type().parameterType(retInsertPos - 1) == MemorySegment.class;
                        assert returnFilter.type().parameterType(retInsertPos - 2) == MemorySegment.class;
                    }
                }
            }
            // (R, Context (ret)) -> (MemorySegment?, Context (ret), MemorySegment?, Context (arg), ...)
            specializedHandle = MethodHandles.collectArguments(returnFilter, retInsertPos, specializedHandle);
            if (callingSequence.needsReturnBuffer()) {
                // (MemorySegment, Context (ret), Context (arg), MemorySegment,  ...) -> (MemorySegment, Context (ret), Context (arg), ...)
                specializedHandle = SharedUtils.mergeArguments(specializedHandle, retBufPos, retBufPos + 3);

                // allocate the return buffer from the binding context, and then merge the 2 allocator args
                MethodHandle retBufAllocHandle = MethodHandles.insertArguments(MH_ALLOCATE_RETURN_BUFFER, 1, callingSequence.returnBufferSize());
                // (MemorySegment, Context (ret), Context (arg), ...) -> (Context (arg), Context (ret), Context (arg), ...)
                specializedHandle = MethodHandles.filterArguments(specializedHandle, retBufPos, retBufAllocHandle);
                // (Context (arg), Context (ret), Context (arg), ...) -> (Context (ret), Context (arg), ...)
                specializedHandle = SharedUtils.mergeArguments(specializedHandle, argContextPos + 1, retBufPos); // +1 to skip return context
            }
            // (Context (ret), Context (arg), ...) -> (SegmentAllocator, Context (arg), ...)
            specializedHandle = MethodHandles.filterArguments(specializedHandle, 0, MH_WRAP_ALLOCATOR);
        } else {
            specializedHandle = MethodHandles.dropArguments(specializedHandle, 0, SegmentAllocator.class);
        }

        // now bind the internal context parameter

        argContextPos++; // skip over the return SegmentAllocator (inserted by the above code)
        specializedHandle = SharedUtils.wrapWithAllocator(specializedHandle, argContextPos, callingSequence.allocationSize(), false);
        return specializedHandle;
    }

    private record InvocationData(MethodHandle leaf, Map<VMStorage, Integer> argIndexMap, Map<VMStorage, Integer> retIndexMap) {}

    Object invokeInterpBindings(SegmentAllocator allocator, Object[] args, InvocationData invData) throws Throwable {
        Binding.Context unboxContext = callingSequence.allocationSize() != 0
                ? Binding.Context.ofBoundedAllocator(callingSequence.allocationSize())
                : Binding.Context.DUMMY;
        try (unboxContext) {
            MemorySegment returnBuffer = null;

            // do argument processing, get Object[] as result
            Object[] leafArgs = new Object[invData.leaf.type().parameterCount()];
            if (callingSequence.needsReturnBuffer()) {
                // we supply the return buffer (argument array does not contain it)
                Object[] prefixedArgs = new Object[args.length + 1];
                returnBuffer = unboxContext.allocator().allocate(callingSequence.returnBufferSize());
                prefixedArgs[0] = returnBuffer;
                System.arraycopy(args, 0, prefixedArgs, 1, args.length);
                args = prefixedArgs;
            }
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                BindingInterpreter.unbox(arg, callingSequence.argumentBindings(i),
                        (storage, type, value) -> {
                            leafArgs[invData.argIndexMap.get(storage)] = value;
                        }, unboxContext);
            }

            // call leaf
            Object o = invData.leaf.invokeWithArguments(leafArgs);

            // return value processing
            if (o == null) {
                if (!callingSequence.needsReturnBuffer()) {
                    return null;
                }
                MemorySegment finalReturnBuffer = returnBuffer;
                return BindingInterpreter.box(callingSequence.returnBindings(),
                        new BindingInterpreter.LoadFunc() {
                            int retBufReadOffset = 0;
                            @Override
                            public Object load(VMStorage storage, Class<?> type) {
                                Object result1 = SharedUtils.read(finalReturnBuffer.asSlice(retBufReadOffset), type);
                                retBufReadOffset += abi.arch.typeSize(storage.type());
                                return result1;
                            }
                        }, Binding.Context.ofAllocator(allocator));
            } else {
                return BindingInterpreter.box(callingSequence.returnBindings(), (storage, type) -> o,
                        Binding.Context.ofAllocator(allocator));
            }
        }
    }
}

