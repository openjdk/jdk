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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.CABI;
import jdk.internal.foreign.abi.AbstractLinker.UpcallStubFactory;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64Linker;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64Linker;
import jdk.internal.foreign.abi.aarch64.windows.WindowsAArch64Linker;
import jdk.internal.foreign.abi.fallback.FallbackLinker;
import jdk.internal.foreign.abi.ppc64.aix.AixPPC64Linker;
import jdk.internal.foreign.abi.ppc64.linux.LinuxPPC64Linker;
import jdk.internal.foreign.abi.ppc64.linux.LinuxPPC64leLinker;
import jdk.internal.foreign.abi.riscv64.linux.LinuxRISCV64Linker;
import jdk.internal.foreign.abi.s390.linux.LinuxS390Linker;
import jdk.internal.foreign.abi.x64.sysv.SysVx64Linker;
import jdk.internal.foreign.abi.x64.windows.Windowsx64Linker;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySegment.Scope;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.Reference;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

public final class SharedUtils {

    private SharedUtils() {
    }

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();

    private static final MethodHandle MH_ALLOC_BUFFER;
    private static final MethodHandle MH_BUFFER_COPY;
    private static final MethodHandle MH_REACHABILITY_FENCE;
    public static final MethodHandle MH_CHECK_SYMBOL;
    private static final MethodHandle MH_CHECK_CAPTURE_SEGMENT;

    public static final AddressLayout C_POINTER = ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE));

    public static final Arena DUMMY_ARENA = new Arena() {
        @Override
        public Scope scope() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // do nothing
        }
    };

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_ALLOC_BUFFER = lookup.findVirtual(SegmentAllocator.class, "allocate",
                    methodType(MemorySegment.class, MemoryLayout.class));
            MH_BUFFER_COPY = lookup.findStatic(SharedUtils.class, "bufferCopy",
                    methodType(MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MH_REACHABILITY_FENCE = lookup.findStatic(Reference.class, "reachabilityFence",
                    methodType(void.class, Object.class));
            MH_CHECK_SYMBOL = lookup.findStatic(SharedUtils.class, "checkSymbol",
                    methodType(void.class, MemorySegment.class));
            MH_CHECK_CAPTURE_SEGMENT = lookup.findStatic(SharedUtils.class, "checkCaptureSegment",
                    methodType(MemorySegment.class, MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new BootstrapMethodError(e);
        }
    }

    // this allocator should be used when no allocation is expected
    public static final SegmentAllocator THROWING_ALLOCATOR = (size, align) -> {
        throw new IllegalStateException("Cannot get here");
    };

    public static long alignUp(long addr, long alignment) {
        return ((addr - 1) | (alignment - 1)) + 1;
    }

    public static long remainsToAlignment(long addr, long alignment) {
        return alignUp(addr, alignment) - addr;
    }

    /**
     * Takes a MethodHandle that takes an input buffer as a first argument (a MemorySegment), and returns nothing,
     * and adapts it to return a MemorySegment, by allocating a MemorySegment for the input
     * buffer, calling the target MethodHandle, and then returning the allocated MemorySegment.
     *
     * This allows viewing a MethodHandle that makes use of in memory return (IMR) as a MethodHandle that just returns
     * a MemorySegment without requiring a pre-allocated buffer as an explicit input.
     *
     * @param handle the target handle to adapt
     * @param cDesc the function descriptor of the native function (with actual return layout)
     * @return the adapted handle
     */
    public static MethodHandle adaptDowncallForIMR(MethodHandle handle, FunctionDescriptor cDesc, CallingSequence sequence) {
        if (handle.type().returnType() != void.class)
            throw new IllegalArgumentException("return expected to be void for in memory returns: " + handle.type());
        int imrAddrIdx = sequence.numLeadingParams();
        if (handle.type().parameterType(imrAddrIdx) != MemorySegment.class)
            throw new IllegalArgumentException("MemorySegment expected as third param: " + handle.type());
        if (cDesc.returnLayout().isEmpty())
            throw new IllegalArgumentException("Return layout needed: " + cDesc);

        MethodHandle ret = identity(MemorySegment.class); // (MemorySegment) MemorySegment
        handle = collectArguments(ret, 1, handle); // (MemorySegment, MemorySegment, SegmentAllocator, MemorySegment, ...) MemorySegment
        handle = mergeArguments(handle, 0, 1 + imrAddrIdx);  // (MemorySegment, MemorySegment, SegmentAllocator, ...) MemorySegment
        handle = collectArguments(handle, 0, insertArguments(MH_ALLOC_BUFFER, 1, cDesc.returnLayout().get())); // (SegmentAllocator, MemorySegment, SegmentAllocator, ...) MemorySegment
        handle = mergeArguments(handle, 0, 2);  // (SegmentAllocator, MemorySegment, ...) MemorySegment
        handle = swapArguments(handle, 0, 1); // (MemorySegment, SegmentAllocator, ...) MemorySegment
        return handle;
    }

    /**
     * Takes a MethodHandle that returns a MemorySegment, and adapts it to take an input buffer as a first argument
     * (a MemorySegment), and upon invocation, copies the contents of the returned MemorySegment into the input buffer
     * passed as the first argument.
     *
     * @param target the target handle to adapt
     * @return the adapted handle
     */
    private static MethodHandle adaptUpcallForIMR(MethodHandle target, boolean dropReturn) {
        if (target.type().returnType() != MemorySegment.class)
            throw new IllegalArgumentException("Must return MemorySegment for IMR");

        target = collectArguments(MH_BUFFER_COPY, 1, target); // (MemorySegment, ...) MemorySegment

        if (dropReturn) { // no handling for return value, need to drop it
            target = dropReturn(target);
        } else {
            // adjust return type so that it matches the inferred type of the effective
            // function descriptor
            target = target.asType(target.type().changeReturnType(MemorySegment.class));
        }

        return target;
    }

    public static UpcallStubFactory arrangeUpcallHelper(MethodType targetType, boolean isInMemoryReturn, boolean dropReturn,
                                                        ABIDescriptor abi, CallingSequence callingSequence) {
        if (isInMemoryReturn) {
            // simulate the adaptation to get the type
            MethodHandle fakeTarget = MethodHandles.empty(targetType);
            targetType = adaptUpcallForIMR(fakeTarget, dropReturn).type();
        }

        UpcallStubFactory factory = UpcallLinker.makeFactory(targetType, abi, callingSequence);

        if (isInMemoryReturn) {
            final UpcallStubFactory finalFactory = factory;
            factory = (target, scope) -> {
                target = adaptUpcallForIMR(target, dropReturn);
                return finalFactory.makeStub(target, scope);
            };
        }

        return factory;
    }

    private static MemorySegment bufferCopy(MemorySegment dest, MemorySegment buffer) {
        return dest.copyFrom(buffer);
    }

    public static Class<?> primitiveCarrierForSize(long size, boolean useFloat) {
        return primitiveLayoutForSize(size, useFloat).carrier();
    }

    public static ValueLayout primitiveLayoutForSize(long size, boolean useFloat) {
        if (useFloat) {
            if (size == 4) {
                return JAVA_FLOAT;
            } else if (size == 8) {
                return JAVA_DOUBLE;
            }
        } else {
            if (size == 1) {
                return JAVA_BYTE;
            } else if (size == 2) {
                return JAVA_SHORT;
            } else if (size <= 4) {
                return JAVA_INT;
            } else if (size <= 8) {
                return JAVA_LONG;
            }
        }

        throw new IllegalArgumentException("No layout for size: " + size + " isFloat=" + useFloat);
    }

    public static Linker getSystemLinker() {
        return switch (CABI.current()) {
            case WIN_64 -> Windowsx64Linker.getInstance();
            case SYS_V -> SysVx64Linker.getInstance();
            case LINUX_AARCH_64 -> LinuxAArch64Linker.getInstance();
            case MAC_OS_AARCH_64 -> MacOsAArch64Linker.getInstance();
            case WIN_AARCH_64 -> WindowsAArch64Linker.getInstance();
            case AIX_PPC_64 -> AixPPC64Linker.getInstance();
            case LINUX_PPC_64 -> LinuxPPC64Linker.getInstance();
            case LINUX_PPC_64_LE -> LinuxPPC64leLinker.getInstance();
            case LINUX_RISCV_64 -> LinuxRISCV64Linker.getInstance();
            case LINUX_S390 -> LinuxS390Linker.getInstance();
            case FALLBACK -> FallbackLinker.getInstance();
            case UNSUPPORTED -> throw new UnsupportedOperationException("Platform does not support native linker");
        };
    }

    static Map<VMStorage, Integer> indexMap(Binding.Move[] moves) {
        return IntStream.range(0, moves.length)
                        .boxed()
                        .collect(Collectors.toMap(i -> moves[i].storage(), i -> i));
    }

    static MethodHandle mergeArguments(MethodHandle mh, int sourceIndex, int destIndex) {
        MethodType oldType = mh.type();
        Class<?> sourceType = oldType.parameterType(sourceIndex);
        Class<?> destType = oldType.parameterType(destIndex);
        if (sourceType != destType) {
            // TODO meet?
            throw new IllegalArgumentException("Parameter types differ: " + sourceType + " != " + destType);
        }
        MethodType newType = oldType.dropParameterTypes(destIndex, destIndex + 1);
        int[] reorder = new int[oldType.parameterCount()];
        if (destIndex < sourceIndex) {
            sourceIndex--;
        }
        for (int i = 0, index = 0; i < reorder.length; i++) {
            if (i != destIndex) {
                reorder[i] = index++;
            } else {
                reorder[i] = sourceIndex;
            }
        }
        return permuteArguments(mh, newType, reorder);
    }


    public static MethodHandle swapArguments(MethodHandle mh, int firstArg, int secondArg) {
        MethodType mtype = mh.type();
        int[] perms = new int[mtype.parameterCount()];
        MethodType swappedType = MethodType.methodType(mtype.returnType());
        for (int i = 0 ; i < perms.length ; i++) {
            int dst = i;
            if (i == firstArg) dst = secondArg;
            if (i == secondArg) dst = firstArg;
            perms[i] = dst;
            swappedType = swappedType.appendParameterTypes(mtype.parameterType(dst));
        }
        return permuteArguments(mh, swappedType, perms);
    }

    private static MethodHandle reachabilityFenceHandle(Class<?> type) {
        return MH_REACHABILITY_FENCE.asType(MethodType.methodType(void.class, type));
    }

    public static void handleUncaughtException(Throwable t) {
        if (t != null) {
            try {
                t.printStackTrace();
                System.err.println("Unrecoverable uncaught exception encountered. The VM will now exit");
            } finally {
                JLA.exit(1);
            }
        }
    }

    public static long unboxSegment(MemorySegment segment) {
        if (!segment.isNative()) {
            throw new IllegalArgumentException("Heap segment not allowed: " + segment);
        }
        return segment.address();
    }

    public static void checkExceptions(MethodHandle target) {
        Class<?>[] exceptions = JLIA.exceptionTypes(target);
        if (exceptions != null && exceptions.length != 0) {
            throw new IllegalArgumentException("Target handle may throw exceptions: " + Arrays.toString(exceptions));
        }
    }

    public static MethodHandle maybeInsertAllocator(FunctionDescriptor descriptor, MethodHandle handle) {
        if (descriptor.returnLayout().isEmpty() || !(descriptor.returnLayout().get() instanceof GroupLayout)) {
            // not returning segment, just insert a throwing allocator
            handle = insertArguments(handle, 1, THROWING_ALLOCATOR);
        }
        return handle;
    }

    public static MethodHandle maybeCheckCaptureSegment(MethodHandle handle, LinkerOptions options) {
        if (options.hasCapturedCallState()) {
            // (<target address>, SegmentAllocator, <capture segment>, ...) -> ...
            handle = MethodHandles.filterArguments(handle, 2, MH_CHECK_CAPTURE_SEGMENT);
        }
        return handle;
    }

    @ForceInline
    public static MemorySegment checkCaptureSegment(MemorySegment captureSegment) {
        Objects.requireNonNull(captureSegment);
        if (captureSegment.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("Capture segment is NULL: " + captureSegment);
        }
        return captureSegment.asSlice(0, CapturableState.LAYOUT);
    }

    @ForceInline
    public static void checkSymbol(MemorySegment symbol) {
        Objects.requireNonNull(symbol);
        if (symbol.equals(MemorySegment.NULL))
            throw new IllegalArgumentException("Symbol is NULL: " + symbol);
    }

    static void checkType(Class<?> actualType, Class<?> expectedType) {
        if (expectedType != actualType) {
            throw new IllegalArgumentException(
                    String.format("Invalid operand type: %s. %s expected", actualType, expectedType));
        }
    }

    public static boolean isPowerOfTwo(int width) {
        return Integer.bitCount(width) == 1;
    }

    static long pickChunkOffset(long chunkOffset, long byteWidth, int chunkWidth) {
        return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
                ? byteWidth - chunkWidth - chunkOffset
                : chunkOffset;
    }

    public static Arena newBoundedArena(long size) {
        return new Arena() {
            final Arena arena = Arena.ofConfined();
            final SegmentAllocator slicingAllocator = SegmentAllocator.slicingAllocator(arena.allocate(size));

            @Override
            public Scope scope() {
                return arena.scope();
            }

            @Override
            public void close() {
                arena.close();
            }

            @Override
            public MemorySegment allocate(long byteSize, long byteAlignment) {
                return slicingAllocator.allocate(byteSize, byteAlignment);
            }
        };
    }

    public static Arena newEmptyArena() {
        return new Arena() {
            final Arena arena = Arena.ofConfined();

            @Override
            public Scope scope() {
                return arena.scope();
            }

            @Override
            public void close() {
                arena.close();
            }

            @Override
            public MemorySegment allocate(long byteSize, long byteAlignment) {
                throw new UnsupportedOperationException();
            }
        };
    }

    static void writeOverSized(MemorySegment ptr, Class<?> type, Object o) {
        // use VH_LONG for integers to zero out the whole register in the process
        if (type == long.class) {
            ptr.set(JAVA_LONG_UNALIGNED, 0, (long) o);
        } else if (type == int.class) {
            ptr.set(JAVA_LONG_UNALIGNED, 0, (int) o);
        } else if (type == short.class) {
            ptr.set(JAVA_LONG_UNALIGNED, 0, (short) o);
        } else if (type == char.class) {
            ptr.set(JAVA_LONG_UNALIGNED, 0, (char) o);
        } else if (type == byte.class) {
            ptr.set(JAVA_LONG_UNALIGNED, 0, (byte) o);
        } else if (type == float.class) {
            ptr.set(JAVA_FLOAT_UNALIGNED, 0, (float) o);
        } else if (type == double.class) {
            ptr.set(JAVA_DOUBLE_UNALIGNED, 0, (double) o);
        } else if (type == boolean.class) {
            boolean b = (boolean)o;
            ptr.set(JAVA_LONG_UNALIGNED, 0, b ? (long)1 : (long)0);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + type);
        }
    }

    static void write(MemorySegment ptr, long offset, Class<?> type, Object o) {
        if (type == long.class) {
            ptr.set(JAVA_LONG_UNALIGNED, offset, (long) o);
        } else if (type == int.class) {
            ptr.set(JAVA_INT_UNALIGNED, offset, (int) o);
        } else if (type == short.class) {
            ptr.set(JAVA_SHORT_UNALIGNED, offset, (short) o);
        } else if (type == char.class) {
            ptr.set(JAVA_CHAR_UNALIGNED, offset, (char) o);
        } else if (type == byte.class) {
            ptr.set(JAVA_BYTE, offset, (byte) o);
        } else if (type == float.class) {
            ptr.set(JAVA_FLOAT_UNALIGNED, offset, (float) o);
        } else if (type == double.class) {
            ptr.set(JAVA_DOUBLE_UNALIGNED, offset, (double) o);
        } else if (type == boolean.class) {
            ptr.set(JAVA_BOOLEAN, offset, (boolean) o);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + type);
        }
    }

    static Object read(MemorySegment ptr, long offset, Class<?> type) {
        if (type == long.class) {
            return ptr.get(JAVA_LONG_UNALIGNED, offset);
        } else if (type == int.class) {
            return ptr.get(JAVA_INT_UNALIGNED, offset);
        } else if (type == short.class) {
            return ptr.get(JAVA_SHORT_UNALIGNED, offset);
        } else if (type == char.class) {
            return ptr.get(JAVA_CHAR_UNALIGNED, offset);
        } else if (type == byte.class) {
            return ptr.get(JAVA_BYTE, offset);
        } else if (type == float.class) {
            return ptr.get(JAVA_FLOAT_UNALIGNED, offset);
        } else if (type == double.class) {
            return ptr.get(JAVA_DOUBLE_UNALIGNED, offset);
        } else if (type == boolean.class) {
            return ptr.get(JAVA_BOOLEAN, offset);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + type);
        }
    }

    public static Map<String, MemoryLayout> canonicalLayouts(ValueLayout longLayout, ValueLayout sizetLayout, ValueLayout wchartLayout) {
        return Map.ofEntries(
                // specified canonical layouts
                Map.entry("bool", ValueLayout.JAVA_BOOLEAN),
                Map.entry("char", ValueLayout.JAVA_BYTE),
                Map.entry("short", ValueLayout.JAVA_SHORT),
                Map.entry("int", ValueLayout.JAVA_INT),
                Map.entry("float", ValueLayout.JAVA_FLOAT),
                Map.entry("long", longLayout),
                Map.entry("long long", ValueLayout.JAVA_LONG),
                Map.entry("double", ValueLayout.JAVA_DOUBLE),
                Map.entry("void*", ValueLayout.ADDRESS),
                Map.entry("size_t", sizetLayout),
                Map.entry("wchar_t", wchartLayout),
                // unspecified size-dependent layouts
                Map.entry("int8_t", ValueLayout.JAVA_BYTE),
                Map.entry("int16_t", ValueLayout.JAVA_SHORT),
                Map.entry("int32_t", ValueLayout.JAVA_INT),
                Map.entry("int64_t", ValueLayout.JAVA_LONG),
                // unspecified JNI layouts
                Map.entry("jboolean", ValueLayout.JAVA_BOOLEAN),
                Map.entry("jchar", ValueLayout.JAVA_CHAR),
                Map.entry("jbyte", ValueLayout.JAVA_BYTE),
                Map.entry("jshort", ValueLayout.JAVA_SHORT),
                Map.entry("jint", ValueLayout.JAVA_INT),
                Map.entry("jlong", ValueLayout.JAVA_LONG),
                Map.entry("jfloat", ValueLayout.JAVA_FLOAT),
                Map.entry("jdouble", ValueLayout.JAVA_DOUBLE)
        );
    }
}
