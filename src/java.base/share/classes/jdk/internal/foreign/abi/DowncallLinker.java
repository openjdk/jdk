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

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import sun.security.action.GetPropertyAction;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;

import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodType.methodType;

public class DowncallLinker {
    private static final boolean USE_SPEC = Boolean.parseBoolean(
        GetPropertyAction.privilegedGetProperty("jdk.internal.foreign.DowncallLinker.USE_SPEC", "true"));

    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();

    private static final MethodHandle MH_INVOKE_INTERP_BINDINGS;
    private static final MethodHandle EMPTY_OBJECT_ARRAY_HANDLE = MethodHandles.constant(Object[].class, new Object[0]);

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_INVOKE_INTERP_BINDINGS = lookup.findVirtual(DowncallLinker.class, "invokeInterpBindings",
                    methodType(Object.class, SegmentAllocator.class, Object[].class, InvocationData.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private final ABIDescriptor abi;
    private final CallingSequence callingSequence;

    public DowncallLinker(ABIDescriptor abi, CallingSequence callingSequence) {
        this.abi = abi;
        assert callingSequence.forDowncall();
        this.callingSequence = callingSequence;
    }

    public MethodHandle getBoundMethodHandle() {
        Binding.VMStore[] argMoves = argMoveBindingsStream(callingSequence).toArray(Binding.VMStore[]::new);
        Binding.VMLoad[] retMoves = retMoveBindings(callingSequence);

        MethodType leafType = callingSequence.calleeMethodType();

        NativeEntryPoint nep = NativeEntryPoint.make(
            abi,
            toStorageArray(argMoves),
            toStorageArray(retMoves),
            leafType,
            callingSequence.needsReturnBuffer(),
            callingSequence.capturedStateMask(),
            callingSequence.needsTransition()
        );
        MethodHandle handle = JLIA.nativeMethodHandle(nep);

        if (USE_SPEC) {
            handle = BindingSpecializer.specializeDowncall(handle, callingSequence, abi);
         } else {
            InvocationData invData = new InvocationData(handle, callingSequence);
            handle = insertArguments(MH_INVOKE_INTERP_BINDINGS.bindTo(this), 2, invData);
            MethodType interpType = callingSequence.callerMethodType();
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
        assert handle.type().parameterType(1) == MemorySegment.class;
        handle = foldArguments(handle, 1, SharedUtils.MH_CHECK_SYMBOL);

        handle = SharedUtils.swapArguments(handle, 0, 1); // normalize parameter order

        return handle;
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

    private record InvocationData(MethodHandle leaf, CallingSequence callingSequence) {}

    Object invokeInterpBindings(SegmentAllocator allocator, Object[] args, InvocationData invData) throws Throwable {
        Arena unboxArena = callingSequence.allocationSize() != 0
                ? SharedUtils.newBoundedArena(callingSequence.allocationSize())
                : SharedUtils.DUMMY_ARENA;
        List<MemorySessionImpl> acquiredScopes = new ArrayList<>();
        try (unboxArena) {
            MemorySegment returnBuffer = null;

            // do argument processing, get Object[] as result
            if (callingSequence.needsReturnBuffer()) {
                // we supply the return buffer (argument array does not contain it)
                Object[] prefixedArgs = new Object[args.length + 1];
                returnBuffer = unboxArena.allocate(callingSequence.returnBufferSize());
                prefixedArgs[0] = returnBuffer;
                System.arraycopy(args, 0, prefixedArgs, 1, args.length);
                args = prefixedArgs;
            }

            Object[] leafArgs = new Object[invData.leaf.type().parameterCount()];
            BindingInterpreter.StoreFunc storeFunc = new BindingInterpreter.StoreFunc() {
                    int argOffset = 0;
                    @Override
                    public void store(VMStorage storage, Object o) {
                        leafArgs[argOffset++] = o;
                    }
                };
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (callingSequence.functionDesc().argumentLayouts().get(i) instanceof AddressLayout) {
                    MemorySessionImpl sessionImpl = ((AbstractMemorySegmentImpl) arg).sessionImpl();
                    if (!(callingSequence.needsReturnBuffer() && i == 0)) { // don't acquire unboxArena's scope
                        sessionImpl.acquire0();
                        // add this scope _after_ we acquire, so we only release scopes we actually acquired
                        // in case an exception occurs
                        acquiredScopes.add(sessionImpl);
                    }
                }
                BindingInterpreter.unbox(arg, callingSequence.argumentBindings(i), storeFunc, unboxArena);
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
                                Object result1 = SharedUtils.read(finalReturnBuffer, retBufReadOffset, type);
                                retBufReadOffset += abi.arch.typeSize(storage.type());
                                return result1;
                            }
                        }, allocator);
            } else {
                return BindingInterpreter.box(callingSequence.returnBindings(), (storage, type) -> o,
                        allocator);
            }
        } finally {
            for (MemorySessionImpl sessionImpl : acquiredScopes) {
                sessionImpl.release0();
            }
        }
    }
}
