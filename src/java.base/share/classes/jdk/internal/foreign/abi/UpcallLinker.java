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

import jdk.internal.foreign.abi.AbstractLinker.UpcallStubFactory;
import sun.security.action.GetPropertyAction;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandles.exactInvoker;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static sun.security.action.GetBooleanAction.privilegedGetProperty;

public class UpcallLinker {
    private static final boolean DEBUG =
        privilegedGetProperty("jdk.internal.foreign.UpcallLinker.DEBUG");
    private static final boolean USE_SPEC = Boolean.parseBoolean(
        GetPropertyAction.privilegedGetProperty("jdk.internal.foreign.UpcallLinker.USE_SPEC", "true"));

    private static final MethodHandle MH_invokeInterpBindings;

    static {
        try {
            MethodHandles.Lookup lookup = lookup();
            MH_invokeInterpBindings = lookup.findStatic(UpcallLinker.class, "invokeInterpBindings",
                    methodType(Object.class, MethodHandle.class, Object[].class, InvocationData.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    public static UpcallStubFactory makeFactory(MethodType targetType, ABIDescriptor abi, CallingSequence callingSequence) {
        assert callingSequence.forUpcall();
        Binding.VMLoad[] argMoves = argMoveBindings(callingSequence);
        Binding.VMStore[] retMoves = retMoveBindings(callingSequence);

        MethodType llType = callingSequence.callerMethodType();

        UnaryOperator<MethodHandle> doBindingsMaker;
        if (USE_SPEC) {
            MethodHandle doBindings = BindingSpecializer.specializeUpcall(targetType, callingSequence, abi);
            doBindingsMaker = target -> {
                MethodHandle handle = MethodHandles.insertArguments(doBindings, 0, target);
                assert handle.type() == llType;
                return handle;
            };
        } else {
            Map<VMStorage, Integer> argIndices = SharedUtils.indexMap(argMoves);
            Map<VMStorage, Integer> retIndices = SharedUtils.indexMap(retMoves);
            int spreaderCount = callingSequence.calleeMethodType().parameterCount();
            if (callingSequence.needsReturnBuffer()) {
                spreaderCount--; // return buffer is dropped from the argument list
            }
            final int finalSpreaderCount = spreaderCount;
            InvocationData invData = new InvocationData(argIndices, retIndices, callingSequence, retMoves, abi);
            MethodHandle doBindings = insertArguments(MH_invokeInterpBindings, 2, invData);
            doBindingsMaker = target -> {
                target = target.asSpreader(Object[].class, finalSpreaderCount);
                MethodHandle handle = MethodHandles.insertArguments(doBindings, 0, target);
                handle = handle.asCollector(Object[].class, llType.parameterCount());
                return handle.asType(llType);
            };
        }

        VMStorage[] args = Arrays.stream(argMoves).map(Binding.Move::storage).toArray(VMStorage[]::new);
        VMStorage[] rets = Arrays.stream(retMoves).map(Binding.Move::storage).toArray(VMStorage[]::new);
        CallRegs conv = new CallRegs(args, rets);
        return (target, scope) -> {
            assert target.type() == targetType;
            MethodHandle doBindings = doBindingsMaker.apply(target);
            checkPrimitive(doBindings.type());
            doBindings = insertArguments(exactInvoker(doBindings.type()), 0, doBindings);
            long entryPoint = makeUpcallStub(doBindings, abi, conv,
                    callingSequence.needsReturnBuffer(), callingSequence.returnBufferSize());
            return UpcallStubs.makeUpcall(entryPoint, scope);
        };
    }

    private static void checkPrimitive(MethodType type) {
        if (!type.returnType().isPrimitive()
                || type.parameterList().stream().anyMatch(p -> !p.isPrimitive()))
            throw new IllegalArgumentException("MethodHandle type must be primitive: " + type);
    }

    private static Stream<Binding.VMLoad> argMoveBindingsStream(CallingSequence callingSequence) {
        return callingSequence.argumentBindings()
                .filter(Binding.VMLoad.class::isInstance)
                .map(Binding.VMLoad.class::cast);
    }

    private static Binding.VMLoad[] argMoveBindings(CallingSequence callingSequence) {
        return argMoveBindingsStream(callingSequence)
                .toArray(Binding.VMLoad[]::new);
    }

    private static Binding.VMStore[] retMoveBindings(CallingSequence callingSequence) {
        return callingSequence.returnBindings().stream()
                .filter(Binding.VMStore.class::isInstance)
                .map(Binding.VMStore.class::cast)
                .toArray(Binding.VMStore[]::new);
    }

    private record InvocationData(Map<VMStorage, Integer> argIndexMap,
                                  Map<VMStorage, Integer> retIndexMap,
                                  CallingSequence callingSequence,
                                  Binding.VMStore[] retMoves,
                                  ABIDescriptor abi) {}

    private static Object invokeInterpBindings(MethodHandle leaf, Object[] lowLevelArgs, InvocationData invData) throws Throwable {
        Arena allocator = invData.callingSequence.allocationSize() != 0
                ? SharedUtils.newBoundedArena(invData.callingSequence.allocationSize())
                : SharedUtils.newEmptyArena();
        try (allocator) {
            /// Invoke interpreter, got array of high-level arguments back
            Object[] highLevelArgs = new Object[invData.callingSequence.calleeMethodType().parameterCount()];
            for (int i = 0; i < highLevelArgs.length; i++) {
                highLevelArgs[i] = BindingInterpreter.box(invData.callingSequence.argumentBindings(i),
                        (storage, type) -> lowLevelArgs[invData.argIndexMap.get(storage)], allocator);
            }

            MemorySegment returnBuffer = null;
            if (invData.callingSequence.needsReturnBuffer()) {
                // this one is for us
                returnBuffer = (MemorySegment) highLevelArgs[0];
                Object[] newArgs = new Object[highLevelArgs.length - 1];
                System.arraycopy(highLevelArgs, 1, newArgs, 0, newArgs.length);
                highLevelArgs = newArgs;
            }

            if (DEBUG) {
                System.err.println("Java arguments:");
                System.err.println(Arrays.toString(highLevelArgs).indent(2));
            }

            // invoke our target
            Object o = leaf.invoke(highLevelArgs);

            if (DEBUG) {
                System.err.println("Java return:");
                System.err.println(Objects.toString(o).indent(2));
            }

            Object[] returnValues = new Object[invData.retIndexMap.size()];
            if (leaf.type().returnType() != void.class) {
                BindingInterpreter.unbox(o, invData.callingSequence.returnBindings(),
                        (storage, value) -> returnValues[invData.retIndexMap.get(storage)] = value, null);
            }

            if (returnValues.length == 0) {
                return null;
            } else if (returnValues.length == 1) {
                return returnValues[0];
            } else {
                assert invData.callingSequence.needsReturnBuffer();

                assert returnValues.length == invData.retMoves().length;
                int retBufWriteOffset = 0;
                for (int i = 0; i < invData.retMoves().length; i++) {
                    Binding.VMStore store = invData.retMoves()[i];
                    Object value = returnValues[i];
                    SharedUtils.writeOverSized(returnBuffer.asSlice(retBufWriteOffset), store.type(), value);
                    retBufWriteOffset += invData.abi.arch.typeSize(store.storage().type());
                }
                return null;
            }
        } catch(Throwable t) {
            SharedUtils.handleUncaughtException(t);
            return null;
        }
    }

    // used for transporting data into native code
    private record CallRegs(VMStorage[] argRegs, VMStorage[] retRegs) {}

    static native long makeUpcallStub(MethodHandle mh, ABIDescriptor abi, CallRegs conv,
                                      boolean needsReturnBuffer, long returnBufferSize);

    private static native void registerNatives();
    static {
        registerNatives();
    }
}
