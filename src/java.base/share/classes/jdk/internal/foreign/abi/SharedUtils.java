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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.CABI;
import jdk.internal.foreign.MemoryAddressImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Scoped;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64Linker;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64Linker;
import jdk.internal.foreign.abi.x64.sysv.SysVx64Linker;
import jdk.internal.foreign.abi.x64.windows.Windowsx64Linker;

import java.lang.foreign.Addressable;
import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.VaList;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

public class SharedUtils {

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();

    private static final MethodHandle MH_ALLOC_BUFFER;
    private static final MethodHandle MH_BASEADDRESS;
    private static final MethodHandle MH_BUFFER_COPY;
    private static final MethodHandle MH_REACHBILITY_FENCE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_ALLOC_BUFFER = lookup.findVirtual(SegmentAllocator.class, "allocate",
                    methodType(MemorySegment.class, MemoryLayout.class));
            MH_BASEADDRESS = lookup.findVirtual(MemorySegment.class, "address",
                    methodType(MemoryAddress.class));
            MH_BUFFER_COPY = lookup.findStatic(SharedUtils.class, "bufferCopy",
                    methodType(MemoryAddress.class, MemoryAddress.class, MemorySegment.class));
            MH_REACHBILITY_FENCE = lookup.findStatic(Reference.class, "reachabilityFence",
                    methodType(void.class, Object.class));
        } catch (ReflectiveOperationException e) {
            throw new BootstrapMethodError(e);
        }
    }

    // this allocator should be used when no allocation is expected
    public static final SegmentAllocator THROWING_ALLOCATOR = (size, align) -> {
        throw new IllegalStateException("Cannot get here");
    };

    /**
     * Align the specified type from a given address
     * @return The address the data should be at based on alignment requirement
     */
    public static long align(MemoryLayout t, boolean isVar, long addr) {
        return alignUp(addr, alignment(t, isVar));
    }

    public static long alignUp(long addr, long alignment) {
        return ((addr - 1) | (alignment - 1)) + 1;
    }

    /**
     * The alignment requirement for a given type
     * @param isVar indicate if the type is a standalone variable. This change how
     * array is aligned. for example.
     */
    public static long alignment(MemoryLayout t, boolean isVar) {
        if (t instanceof ValueLayout) {
            return alignmentOfScalar((ValueLayout) t);
        } else if (t instanceof SequenceLayout) {
            // when array is used alone
            return alignmentOfArray((SequenceLayout) t, isVar);
        } else if (t instanceof GroupLayout) {
            return alignmentOfContainer((GroupLayout) t);
        } else if (t.isPadding()) {
            return 1;
        } else {
            throw new IllegalArgumentException("Invalid type: " + t);
        }
    }

    private static long alignmentOfScalar(ValueLayout st) {
        return st.byteSize();
    }

    private static long alignmentOfArray(SequenceLayout ar, boolean isVar) {
        if (ar.elementCount() == 0) {
            // VLA or incomplete
            return 16;
        } else if ((ar.byteSize()) >= 16 && isVar) {
            return 16;
        } else {
            // align as element type
            MemoryLayout elementType = ar.elementLayout();
            return alignment(elementType, false);
        }
    }

    private static long alignmentOfContainer(GroupLayout ct) {
        // Most strict member
        return ct.memberLayouts().stream().mapToLong(t -> alignment(t, false)).max().orElse(1);
    }

    /**
     * Takes a MethodHandle that takes an input buffer as a first argument (a MemoryAddress), and returns nothing,
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
    public static MethodHandle adaptDowncallForIMR(MethodHandle handle, FunctionDescriptor cDesc) {
        if (handle.type().returnType() != void.class)
            throw new IllegalArgumentException("return expected to be void for in memory returns: " + handle.type());
        if (handle.type().parameterType(2) != MemoryAddress.class)
            throw new IllegalArgumentException("MemoryAddress expected as third param: " + handle.type());
        if (cDesc.returnLayout().isEmpty())
            throw new IllegalArgumentException("Return layout needed: " + cDesc);

        MethodHandle ret = identity(MemorySegment.class); // (MemorySegment) MemorySegment
        handle = collectArguments(ret, 1, handle); // (MemorySegment, Addressable, SegmentAllocator, MemoryAddress, ...) MemorySegment
        handle = collectArguments(handle, 3, MH_BASEADDRESS); // (MemorySegment, Addressable, SegmentAllocator, MemorySegment, ...) MemorySegment
        handle = mergeArguments(handle, 0, 3);  // (MemorySegment, Addressable, SegmentAllocator, ...) MemorySegment
        handle = collectArguments(handle, 0, insertArguments(MH_ALLOC_BUFFER, 1, cDesc.returnLayout().get())); // (SegmentAllocator, Addressable, SegmentAllocator, ...) MemoryAddress
        handle = mergeArguments(handle, 0, 2);  // (SegmentAllocator, Addressable, ...) MemoryAddress
        handle = swapArguments(handle, 0, 1); // (Addressable, SegmentAllocator, ...) MemoryAddress
        return handle;
    }

    /**
     * Takes a MethodHandle that returns a MemorySegment, and adapts it to take an input buffer as a first argument
     * (a MemoryAddress), and upon invocation, copies the contents of the returned MemorySegment into the input buffer
     * passed as the first argument.
     *
     * @param target the target handle to adapt
     * @return the adapted handle
     */
    public static MethodHandle adaptUpcallForIMR(MethodHandle target, boolean dropReturn) {
        if (target.type().returnType() != MemorySegment.class)
            throw new IllegalArgumentException("Must return MemorySegment for IMR");

        target = collectArguments(MH_BUFFER_COPY, 1, target); // (MemoryAddress, ...) MemoryAddress

        if (dropReturn) { // no handling for return value, need to drop it
            target = dropReturn(target);
        } else {
            // adjust return type so it matches the inferred type of the effective
            // function descriptor
            target = target.asType(target.type().changeReturnType(Addressable.class));
        }

        return target;
    }

    private static MemoryAddress bufferCopy(MemoryAddress dest, MemorySegment buffer) {
        MemoryAddressImpl.ofLongUnchecked(dest.toRawLongValue(), buffer.byteSize()).copyFrom(buffer);
        return dest;
    }

    public static Class<?> primitiveCarrierForSize(long size, boolean useFloat) {
        if (useFloat) {
            if (size == 4) {
                return float.class;
            } else if (size == 8) {
                return double.class;
            }
        } else {
            if (size == 1) {
                return byte.class;
            } else if (size == 2) {
                return short.class;
            } else if (size <= 4) {
                return int.class;
            } else if (size <= 8) {
                return long.class;
            }
        }

        throw new IllegalArgumentException("No type for size: " + size + " isFloat=" + useFloat);
    }

    public static Linker getSystemLinker() {
        return switch (CABI.current()) {
            case Win64 -> Windowsx64Linker.getInstance();
            case SysV -> SysVx64Linker.getInstance();
            case LinuxAArch64 -> LinuxAArch64Linker.getInstance();
            case MacOsAArch64 -> MacOsAArch64Linker.getInstance();
        };
    }

    public static String toJavaStringInternal(MemorySegment segment, long start) {
        int len = strlen(segment, start);
        byte[] bytes = new byte[len];
        MemorySegment.copy(segment, JAVA_BYTE, start, bytes, 0, len);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int strlen(MemorySegment segment, long start) {
        // iterate until overflow (String can only hold a byte[], whose length can be expressed as an int)
        for (int offset = 0; offset >= 0; offset++) {
            byte curr = segment.get(JAVA_BYTE, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw new IllegalArgumentException("String too large");
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


    static MethodHandle swapArguments(MethodHandle mh, int firstArg, int secondArg) {
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
        return MH_REACHBILITY_FENCE.asType(MethodType.methodType(void.class, type));
    }

    static void handleUncaughtException(Throwable t) {
        if (t != null) {
            t.printStackTrace();
            JLA.exit(1);
        }
    }

    public static void checkExceptions(MethodHandle target) {
        Class<?>[] exceptions = JLIA.exceptionTypes(target);
        if (exceptions != null && exceptions.length != 0) {
            throw new IllegalArgumentException("Target handle may throw exceptions: " + Arrays.toString(exceptions));
        }
    }

    public static MethodHandle maybeInsertAllocator(MethodHandle handle) {
        if (!handle.type().returnType().equals(MemorySegment.class)) {
            // not returning segment, just insert a throwing allocator
            handle = insertArguments(handle, 1, THROWING_ALLOCATOR);
        }
        return handle;
    }

    public static void checkSymbol(Addressable symbol) {
        checkAddressable(symbol, "Symbol is NULL");
    }

    public static void checkAddress(MemoryAddress address) {
        checkAddressable(address, "Address is NULL");
    }

    private static void checkAddressable(Addressable symbol, String msg) {
        Objects.requireNonNull(symbol);
        if (symbol.address().toRawLongValue() == 0)
            throw new IllegalArgumentException("Symbol is NULL: " + symbol);
    }

    public static VaList newVaList(Consumer<VaList.Builder> actions, MemorySession session) {
        return switch (CABI.current()) {
            case Win64 -> Windowsx64Linker.newVaList(actions, session);
            case SysV -> SysVx64Linker.newVaList(actions, session);
            case LinuxAArch64 -> LinuxAArch64Linker.newVaList(actions, session);
            case MacOsAArch64 -> MacOsAArch64Linker.newVaList(actions, session);
        };
    }

    public static VaList newVaListOfAddress(MemoryAddress ma, MemorySession session) {
        return switch (CABI.current()) {
            case Win64 -> Windowsx64Linker.newVaListOfAddress(ma, session);
            case SysV -> SysVx64Linker.newVaListOfAddress(ma, session);
            case LinuxAArch64 -> LinuxAArch64Linker.newVaListOfAddress(ma, session);
            case MacOsAArch64 -> MacOsAArch64Linker.newVaListOfAddress(ma, session);
        };
    }

    public static VaList emptyVaList() {
        return switch (CABI.current()) {
            case Win64 -> Windowsx64Linker.emptyVaList();
            case SysV -> SysVx64Linker.emptyVaList();
            case LinuxAArch64 -> LinuxAArch64Linker.emptyVaList();
            case MacOsAArch64 -> MacOsAArch64Linker.emptyVaList();
        };
    }

    static void checkType(Class<?> actualType, Class<?> expectedType) {
        if (expectedType != actualType) {
            throw new IllegalArgumentException(
                    String.format("Invalid operand type: %s. %s expected", actualType, expectedType));
        }
    }

    public static boolean isVarargsIndex(FunctionDescriptor descriptor, int argIndex) {
        int firstPos = descriptor.firstVariadicArgumentIndex();
        return firstPos != -1 && argIndex >= firstPos;
    }

    public static NoSuchElementException newVaListNSEE(MemoryLayout layout) {
        return new NoSuchElementException("No such element: " + layout);
    }

    public static class SimpleVaArg {
        public final MemoryLayout layout;
        public final Object value;

        public SimpleVaArg(MemoryLayout layout, Object value) {
            this.layout = layout;
            this.value = value;
        }

        public VarHandle varHandle() {
            return layout.varHandle();
        }
    }

    public static non-sealed class EmptyVaList implements VaList, Scoped {

        private final MemoryAddress address;

        public EmptyVaList(MemoryAddress address) {
            this.address = address;
        }

        private static UnsupportedOperationException uoe() {
            return new UnsupportedOperationException("Empty VaList");
        }

        @Override
        public int nextVarg(ValueLayout.OfInt layout) {
            throw uoe();
        }

        @Override
        public long nextVarg(ValueLayout.OfLong layout) {
            throw uoe();
        }

        @Override
        public double nextVarg(ValueLayout.OfDouble layout) {
            throw uoe();
        }

        @Override
        public MemoryAddress nextVarg(ValueLayout.OfAddress layout) {
            throw uoe();
        }

        @Override
        public MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator) {
            throw uoe();
        }

        @Override
        public void skip(MemoryLayout... layouts) {
            throw uoe();
        }

        @Override
        public MemorySession session() {
            return MemorySessionImpl.GLOBAL;
        }

        @Override
        public VaList copy() {
            return this;
        }

        @Override
        public MemoryAddress address() {
            return address;
        }
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

    static void write(MemorySegment ptr, Class<?> type, Object o) {
        if (type == long.class) {
            ptr.set(JAVA_LONG_UNALIGNED, 0, (long) o);
        } else if (type == int.class) {
            ptr.set(JAVA_INT_UNALIGNED, 0, (int) o);
        } else if (type == short.class) {
            ptr.set(JAVA_SHORT_UNALIGNED, 0, (short) o);
        } else if (type == char.class) {
            ptr.set(JAVA_CHAR_UNALIGNED, 0, (char) o);
        } else if (type == byte.class) {
            ptr.set(JAVA_BYTE, 0, (byte) o);
        } else if (type == float.class) {
            ptr.set(JAVA_FLOAT_UNALIGNED, 0, (float) o);
        } else if (type == double.class) {
            ptr.set(JAVA_DOUBLE_UNALIGNED, 0, (double) o);
        } else if (type == boolean.class) {
            ptr.set(JAVA_BOOLEAN, 0, (boolean) o);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + type);
        }
    }

    static Object read(MemorySegment ptr, Class<?> type) {
        if (type == long.class) {
            return ptr.get(JAVA_LONG_UNALIGNED, 0);
        } else if (type == int.class) {
            return ptr.get(JAVA_INT_UNALIGNED, 0);
        } else if (type == short.class) {
            return ptr.get(JAVA_SHORT_UNALIGNED, 0);
        } else if (type == char.class) {
            return ptr.get(JAVA_CHAR_UNALIGNED, 0);
        } else if (type == byte.class) {
            return ptr.get(JAVA_BYTE, 0);
        } else if (type == float.class) {
            return ptr.get(JAVA_FLOAT_UNALIGNED, 0);
        } else if (type == double.class) {
            return ptr.get(JAVA_DOUBLE_UNALIGNED, 0);
        } else if (type == boolean.class) {
            return ptr.get(JAVA_BOOLEAN, 0);
        } else {
            throw new IllegalArgumentException("Unsupported carrier: " + type);
        }
    }

    // unaligned constants
    public final static ValueLayout.OfShort JAVA_SHORT_UNALIGNED = JAVA_SHORT.withBitAlignment(8);
    public final static ValueLayout.OfChar JAVA_CHAR_UNALIGNED = JAVA_CHAR.withBitAlignment(8);
    public final static ValueLayout.OfInt JAVA_INT_UNALIGNED = JAVA_INT.withBitAlignment(8);
    public final static ValueLayout.OfLong JAVA_LONG_UNALIGNED = JAVA_LONG.withBitAlignment(8);
    public final static ValueLayout.OfFloat JAVA_FLOAT_UNALIGNED = JAVA_FLOAT.withBitAlignment(8);
    public final static ValueLayout.OfDouble JAVA_DOUBLE_UNALIGNED = JAVA_DOUBLE.withBitAlignment(8);

    public static MethodType inferMethodType(FunctionDescriptor descriptor, boolean upcall) {
        MethodType type = MethodType.methodType(descriptor.returnLayout().isPresent() ?
                carrierFor(descriptor.returnLayout().get(), upcall) : void.class);
        for (MemoryLayout argLayout : descriptor.argumentLayouts()) {
            type = type.appendParameterTypes(carrierFor(argLayout, !upcall));
        }
        return type;
    }

    static Class<?> carrierFor(MemoryLayout layout, boolean forArg) {
        if (layout instanceof ValueLayout valueLayout) {
            return (forArg && valueLayout.carrier().equals(MemoryAddress.class)) ?
                    Addressable.class : valueLayout.carrier();
        } else if (layout instanceof GroupLayout) {
            return MemorySegment.class;
        } else {
            throw new IllegalArgumentException("Unsupported layout: " + layout);
        }
    }
}
