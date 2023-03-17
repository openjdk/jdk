/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi.fallback;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.CapturableState;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.SharedUtils;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.invoke.MethodHandles.foldArguments;

public final class FallbackLinker extends AbstractLinker {

    private static final MethodHandle MH_DO_DOWNCALL;
    private static final MethodHandle MH_DO_UPCALL;

    static {
        try {
            MH_DO_DOWNCALL = MethodHandles.lookup().findStatic(FallbackLinker.class, "doDowncall",
                    MethodType.methodType(Object.class, SegmentAllocator.class, Object[].class, FallbackLinker.DowncallData.class));
            MH_DO_UPCALL = MethodHandles.lookup().findStatic(FallbackLinker.class, "doUpcall",
                    MethodType.methodType(void.class, MethodHandle.class, MemorySegment.class, MemorySegment.class, UpcallData.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static FallbackLinker getInstance() {
        class Holder {
            static final FallbackLinker INSTANCE = new FallbackLinker();
        }
        return Holder.INSTANCE;
    }

    public static boolean isSupported() {
        return LibFallback.SUPPORTED;
    }

    @Override
    protected MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function, LinkerOptions options) {
        MemorySegment cif = makeCif(inferredMethodType, function, FFIABI.DEFAULT, Arena.ofAuto());

        int capturedStateMask = options.capturedCallState()
                .mapToInt(CapturableState::mask)
                .reduce(0, (a, b) -> a | b);
        DowncallData invData = new DowncallData(cif, function.returnLayout().orElse(null),
                function.argumentLayouts(), capturedStateMask);

        MethodHandle target = MethodHandles.insertArguments(MH_DO_DOWNCALL, 2, invData);

        int leadingArguments = 1; // address
        MethodType type = inferredMethodType.insertParameterTypes(0, SegmentAllocator.class, MemorySegment.class);
        if (capturedStateMask != 0) {
            leadingArguments++;
            type = type.insertParameterTypes(2, MemorySegment.class);
        }
        target = target.asCollector(1, Object[].class, inferredMethodType.parameterCount() + leadingArguments);
        target = target.asType(type);
        target = foldArguments(target, 1, SharedUtils.MH_CHECK_SYMBOL);
        target = SharedUtils.swapArguments(target, 0, 1); // normalize parameter order

        return target;
    }

    @Override
    protected UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options) {
        MemorySegment cif = makeCif(targetType, function, FFIABI.DEFAULT, Arena.ofAuto());

        UpcallData invData = new UpcallData(function.returnLayout().orElse(null), function.argumentLayouts(), cif);
        MethodHandle doUpcallMH = MethodHandles.insertArguments(MH_DO_UPCALL, 3, invData);

        return (target, scope) -> {
            target = MethodHandles.insertArguments(doUpcallMH, 0, target);
            return LibFallback.createClosure(cif, target, scope);
        };
    }

    private static MemorySegment makeCif(MethodType methodType, FunctionDescriptor function, FFIABI abi, Arena scope) {
        MemorySegment argTypes = scope.allocate(function.argumentLayouts().size() * ADDRESS.byteSize());
        List<MemoryLayout> argLayouts = function.argumentLayouts();
        for (int i = 0; i < argLayouts.size(); i++) {
            MemoryLayout layout = argLayouts.get(i);
            argTypes.setAtIndex(ADDRESS, i, FFIType.toFFIType(layout, abi, scope));
        }

        MemorySegment returnType = methodType.returnType() != void.class
                ? FFIType.toFFIType(function.returnLayout().orElseThrow(), abi, scope)
                : LibFallback.VOID_TYPE;
        return LibFallback.prepCif(returnType, argLayouts.size(), argTypes, abi, scope);
    }

    private record DowncallData(MemorySegment cif, MemoryLayout returnLayout, List<MemoryLayout> argLayouts,
                                int capturedStateMask) {}

    private static Object doDowncall(SegmentAllocator returnAllocator, Object[] args, DowncallData invData) {
        List<MemorySessionImpl> acquiredSessions = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            int argStart = 0;

            MemorySegment target = (MemorySegment) args[argStart++];
            MemorySessionImpl targetImpl = ((AbstractMemorySegmentImpl) target).sessionImpl();
            targetImpl.acquire0();
            acquiredSessions.add(targetImpl);

            MemorySegment capturedState = null;
            if (invData.capturedStateMask() != 0) {
                capturedState = (MemorySegment) args[argStart++];
                MemorySessionImpl capturedStateImpl = ((AbstractMemorySegmentImpl) capturedState).sessionImpl();
                capturedStateImpl.acquire0();
                acquiredSessions.add(capturedStateImpl);
            }

            List<MemoryLayout> argLayouts = invData.argLayouts();
            MemorySegment argPtrs = arena.allocate(argLayouts.size() * ADDRESS.byteSize());
            for (int i = 0; i < argLayouts.size(); i++) {
                Object arg = args[argStart + i];
                MemoryLayout layout = argLayouts.get(i);
                MemorySegment argSeg = arena.allocate(layout);
                writeValue(arg, layout, argSeg, addr -> {
                    MemorySessionImpl sessionImpl = ((AbstractMemorySegmentImpl) addr).sessionImpl();
                    sessionImpl.acquire0();
                    acquiredSessions.add(sessionImpl);
                });
                argPtrs.setAtIndex(ADDRESS, i, argSeg);
            }

            MemorySegment retSeg = null;
            if (invData.returnLayout() != null) {
                retSeg = (invData.returnLayout() instanceof GroupLayout ? returnAllocator : arena).allocate(invData.returnLayout);
            }

            LibFallback.doDowncall(invData.cif, target, retSeg, argPtrs, capturedState, invData.capturedStateMask());

            Reference.reachabilityFence(invData.cif());

            return readValue(retSeg, invData.returnLayout());
        } finally {
            for (MemorySessionImpl session : acquiredSessions) {
                session.release0();
            }
        }
    }

    // note that cif is not used, but we store it here to keep it alive
    private record UpcallData(MemoryLayout returnLayout, List<MemoryLayout> argLayouts, MemorySegment cif) {}

    private static void doUpcall(MethodHandle target, MemorySegment retPtr, MemorySegment argPtrs, UpcallData data) throws Throwable {
        List<MemoryLayout> argLayouts = data.argLayouts();
        int numArgs = argLayouts.size();
        MemoryLayout retLayout = data.returnLayout();
        try (Arena upcallArena = Arena.ofConfined()) {
            MemorySegment argsSeg = argPtrs.reinterpret(numArgs * ADDRESS.byteSize(), upcallArena, null);
            MemorySegment retSeg = retLayout != null
                ? retPtr.reinterpret(retLayout.byteSize(), upcallArena, null)
                : null;

            Object[] args = new Object[numArgs];
            for (int i = 0; i < numArgs; i++) {
                MemoryLayout argLayout = argLayouts.get(i);
                MemorySegment argPtr = argsSeg.getAtIndex(ADDRESS, i)
                        .reinterpret(argLayout.byteSize(), upcallArena, null);
                args[i] = readValue(argPtr, argLayout);
            }

            Object result = target.invokeWithArguments(args);

            writeValue(result, data.returnLayout(), retSeg);
        }
    }

    // where
    private static void writeValue(Object arg, MemoryLayout layout, MemorySegment argSeg) {
        writeValue(arg, layout, argSeg, addr -> {});
    }

    private static void writeValue(Object arg, MemoryLayout layout, MemorySegment argSeg,
                                   Consumer<MemorySegment> acquireCallback) {
        if (layout instanceof ValueLayout.OfBoolean bl) {
            argSeg.set(bl, 0, (Boolean) arg);
        } else if (layout instanceof ValueLayout.OfByte bl) {
            argSeg.set(bl, 0, (Byte) arg);
        } else if (layout instanceof ValueLayout.OfShort sl) {
            argSeg.set(sl, 0, (Short) arg);
        } else if (layout instanceof ValueLayout.OfChar cl) {
            argSeg.set(cl, 0, (Character) arg);
        } else if (layout instanceof ValueLayout.OfInt il) {
            argSeg.set(il, 0, (Integer) arg);
        } else if (layout instanceof ValueLayout.OfLong ll) {
            argSeg.set(ll, 0, (Long) arg);
        } else if (layout instanceof ValueLayout.OfFloat fl) {
            argSeg.set(fl, 0, (Float) arg);
        } else if (layout instanceof ValueLayout.OfDouble dl) {
            argSeg.set(dl, 0, (Double) arg);
        } else if (layout instanceof AddressLayout al) {
            MemorySegment addrArg = (MemorySegment) arg;
            acquireCallback.accept(addrArg);
            argSeg.set(al, 0, addrArg);
        } else if (layout instanceof GroupLayout) {
            argSeg.copyFrom((MemorySegment) arg); // by-value struct
        } else {
            assert layout == null;
        }
    }

    private static Object readValue(MemorySegment seg, MemoryLayout layout) {
        if (layout instanceof ValueLayout.OfBoolean bl) {
            return seg.get(bl, 0);
        } else if (layout instanceof ValueLayout.OfByte bl) {
            return seg.get(bl, 0);
        } else if (layout instanceof ValueLayout.OfShort sl) {
            return seg.get(sl, 0);
        } else if (layout instanceof ValueLayout.OfChar cl) {
            return seg.get(cl, 0);
        } else if (layout instanceof ValueLayout.OfInt il) {
            return seg.get(il, 0);
        } else if (layout instanceof ValueLayout.OfLong ll) {
            return seg.get(ll, 0);
        } else if (layout instanceof ValueLayout.OfFloat fl) {
            return seg.get(fl, 0);
        } else if (layout instanceof ValueLayout.OfDouble dl) {
            return seg.get(dl, 0);
        } else if (layout instanceof AddressLayout al) {
            return seg.get(al, 0);
        } else if (layout instanceof GroupLayout) {
            return seg;
        }
        assert layout == null;
        return null;
    }
}
