/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import static java.lang.invoke.MethodHandles.foldArguments;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.CapturableState;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.SharedUtils;

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
        MemorySegment cif = makeCif(inferredMethodType, function, options, Arena.ofAuto());

        int capturedStateMask = options.capturedCallState()
                .mapToInt(CapturableState::mask)
                .reduce(0, (a, b) -> a | b);
        DowncallData invData = new DowncallData(cif, function.returnLayout().orElse(null),
                function.argumentLayouts(), capturedStateMask, options.allowsHeapAccess());

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
        MemorySegment cif = makeCif(targetType, function, options, Arena.ofAuto());

        UpcallData invData = new UpcallData(function.returnLayout().orElse(null), function.argumentLayouts(), cif);
        MethodHandle doUpcallMH = MethodHandles.insertArguments(MH_DO_UPCALL, 3, invData);

        return (target, scope) -> {
            target = MethodHandles.insertArguments(doUpcallMH, 0, target);
            return LibFallback.createClosure(cif, target, scope);
        };
    }

    private static MemorySegment makeCif(MethodType methodType, FunctionDescriptor function, LinkerOptions options, Arena scope) {
        FFIABI abi = FFIABI.DEFAULT;

        MemorySegment argTypes = scope.allocate(function.argumentLayouts().size() * ADDRESS.byteSize());
        List<MemoryLayout> argLayouts = function.argumentLayouts();
        for (int i = 0; i < argLayouts.size(); i++) {
            MemoryLayout layout = argLayouts.get(i);
            argTypes.setAtIndex(ADDRESS, i, FFIType.toFFIType(layout, abi, scope));
        }

        MemorySegment returnType = methodType.returnType() != void.class
                ? FFIType.toFFIType(function.returnLayout().orElseThrow(), abi, scope)
                : LibFallback.voidType();

        if (options.isVariadicFunction()) {
            int numFixedArgs = options.firstVariadicArgIndex();
            int numTotalArgs = argLayouts.size();
            return LibFallback.prepCifVar(returnType, numFixedArgs, numTotalArgs, argTypes, abi, scope);
        } else {
            return LibFallback.prepCif(returnType, argLayouts.size(), argTypes, abi, scope);
        }
    }

    private record DowncallData(MemorySegment cif, MemoryLayout returnLayout, List<MemoryLayout> argLayouts,
                                int capturedStateMask, boolean allowsHeapAccess) {}

    private static Object doDowncall(SegmentAllocator returnAllocator, Object[] args, DowncallData invData) {
        List<MemorySessionImpl> acquiredSessions = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            int argStart = 0;
            Object[] heapBases = invData.allowsHeapAccess() ? new Object[args.length] : null;

            MemorySegment target = (MemorySegment) args[argStart++];
            MemorySessionImpl targetImpl = ((AbstractMemorySegmentImpl) target).sessionImpl();
            targetImpl.acquire0();
            acquiredSessions.add(targetImpl);

            MemorySegment capturedState = null;
            if (invData.capturedStateMask() != 0) {
                capturedState = SharedUtils.checkCaptureSegment((MemorySegment) args[argStart++]);
                MemorySessionImpl capturedStateImpl = ((AbstractMemorySegmentImpl) capturedState).sessionImpl();
                capturedStateImpl.acquire0();
                acquiredSessions.add(capturedStateImpl);
            }

            List<MemoryLayout> argLayouts = invData.argLayouts();
            MemorySegment argPtrs = arena.allocate(argLayouts.size() * ADDRESS.byteSize());
            for (int i = 0; i < argLayouts.size(); i++) {
                Object arg = args[argStart + i];
                MemoryLayout layout = argLayouts.get(i);

                if (layout instanceof AddressLayout) {
                    AbstractMemorySegmentImpl ms = (AbstractMemorySegmentImpl) arg;
                    MemorySessionImpl sessionImpl = ms.sessionImpl();
                    sessionImpl.acquire0();
                    acquiredSessions.add(sessionImpl);
                    if (invData.allowsHeapAccess() && !ms.isNative()) {
                        heapBases[i] = ms.unsafeGetBase();
                        // write the offset to the arg segment, add array ptr to it in native code
                        layout = JAVA_LONG;
                        arg = ms.address();
                    }
                }

                MemorySegment argSeg = arena.allocate(layout);
                writeValue(arg, layout, argSeg);
                argPtrs.setAtIndex(ADDRESS, i, argSeg);
            }

            MemorySegment retSeg = null;
            if (invData.returnLayout() != null) {
                retSeg = (invData.returnLayout() instanceof GroupLayout ? returnAllocator : arena).allocate(invData.returnLayout);
            }

            LibFallback.doDowncall(invData.cif, target, retSeg, argPtrs, capturedState, invData.capturedStateMask(),
                                   heapBases, args.length);

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

    @SuppressWarnings("restricted")
    private static void doUpcall(MethodHandle target, MemorySegment retPtr, MemorySegment argPtrs, UpcallData data) throws Throwable {
        List<MemoryLayout> argLayouts = data.argLayouts();
        int numArgs = argLayouts.size();
        MemoryLayout retLayout = data.returnLayout();
        try (Arena upcallArena = Arena.ofConfined()) {
            MemorySegment argsSeg = argPtrs.reinterpret(numArgs * ADDRESS.byteSize(), upcallArena, null);
            MemorySegment retSeg = retLayout != null
                ? retPtr.reinterpret(retLayout.byteSize(), upcallArena, null) // restricted
                : null;

            Object[] args = new Object[numArgs];
            for (int i = 0; i < numArgs; i++) {
                MemoryLayout argLayout = argLayouts.get(i);
                MemorySegment argPtr = argsSeg.getAtIndex(ADDRESS, i)
                        .reinterpret(argLayout.byteSize(), upcallArena, null); // restricted
                args[i] = readValue(argPtr, argLayout);
            }

            Object result = target.invokeWithArguments(args);

            writeValue(result, data.returnLayout(), retSeg);
        }
    }

    // where
    private static void writeValue(Object arg, MemoryLayout layout, MemorySegment argSeg) {
        switch (layout) {
            case ValueLayout.OfBoolean bl -> argSeg.set(bl, 0, (Boolean) arg);
            case ValueLayout.OfByte    bl -> argSeg.set(bl, 0, (Byte) arg);
            case ValueLayout.OfShort   sl -> argSeg.set(sl, 0, (Short) arg);
            case ValueLayout.OfChar    cl -> argSeg.set(cl, 0, (Character) arg);
            case ValueLayout.OfInt     il -> argSeg.set(il, 0, (Integer) arg);
            case ValueLayout.OfLong    ll -> argSeg.set(ll, 0, (Long) arg);
            case ValueLayout.OfFloat   fl -> argSeg.set(fl, 0, (Float) arg);
            case ValueLayout.OfDouble  dl -> argSeg.set(dl, 0, (Double) arg);
            case AddressLayout         al -> argSeg.set(al, 0, (MemorySegment) arg);
            case GroupLayout            _ ->
                    MemorySegment.copy((MemorySegment) arg, 0, argSeg, 0, argSeg.byteSize()); // by-value struct
            case null, default -> {
                assert layout == null;
            }
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

    @Override
    public Map<String, MemoryLayout> canonicalLayouts() {
        // Avoid eager dependency on LibFallback, so we can safely check LibFallback.SUPPORTED
        class Holder {
            static final Map<String, MemoryLayout> CANONICAL_LAYOUTS;

            static {
                int wchar_size = LibFallback.wcharSize();
                MemoryLayout wchartLayout = switch(wchar_size) {
                    case 2 -> JAVA_CHAR; // prefer JAVA_CHAR
                    default -> FFIType.layoutFor(wchar_size);
                };

                CANONICAL_LAYOUTS = Map.ofEntries(
                    // specified canonical layouts
                    Map.entry("bool", JAVA_BOOLEAN),
                    Map.entry("char", JAVA_BYTE),
                    Map.entry("float", JAVA_FLOAT),
                    Map.entry("long long", JAVA_LONG.withByteAlignment(LibFallback.longLongAlign())),
                    Map.entry("double", JAVA_DOUBLE.withByteAlignment(LibFallback.doubleAlign())),
                    Map.entry("void*", ADDRESS),
                    // platform-dependent sizes
                    Map.entry("size_t", FFIType.SIZE_T),
                    Map.entry("short", FFIType.layoutFor(LibFallback.shortSize())),
                    Map.entry("int", FFIType.layoutFor(LibFallback.intSize())),
                    Map.entry("long", FFIType.layoutFor(LibFallback.longSize())),
                    Map.entry("wchar_t", wchartLayout),
                    // JNI types
                    Map.entry("jboolean", JAVA_BOOLEAN),
                    Map.entry("jchar", JAVA_CHAR),
                    Map.entry("jbyte", JAVA_BYTE),
                    Map.entry("jshort", JAVA_SHORT),
                    Map.entry("jint", JAVA_INT),
                    Map.entry("jlong", JAVA_LONG),
                    Map.entry("jfloat", JAVA_FLOAT),
                    Map.entry("jdouble", JAVA_DOUBLE)
                );
            }
        }

        return Holder.CANONICAL_LAYOUTS;
    }
}
