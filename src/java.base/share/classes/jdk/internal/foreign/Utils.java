/*
 *  Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.foreign;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import sun.invoke.util.Wrapper;

import static sun.security.action.GetPropertyAction.privilegedGetProperty;

/**
 * This class contains misc helper functions to support creation of memory segments.
 */
public final class Utils {

    public static final boolean IS_WINDOWS = privilegedGetProperty("os.name").startsWith("Windows");

    // Suppresses default constructor, ensuring non-instantiability.
    private Utils() {}

    private static final MethodHandle BYTE_TO_BOOL;
    private static final MethodHandle BOOL_TO_BYTE;
    private static final MethodHandle ADDRESS_TO_LONG;
    private static final MethodHandle LONG_TO_ADDRESS;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            BYTE_TO_BOOL = lookup.findStatic(Utils.class, "byteToBoolean",
                    MethodType.methodType(boolean.class, byte.class));
            BOOL_TO_BYTE = lookup.findStatic(Utils.class, "booleanToByte",
                    MethodType.methodType(byte.class, boolean.class));
            ADDRESS_TO_LONG = lookup.findStatic(SharedUtils.class, "unboxSegment",
                    MethodType.methodType(long.class, MemorySegment.class));
            LONG_TO_ADDRESS = lookup.findStatic(Utils.class, "longToAddress",
                    MethodType.methodType(MemorySegment.class, long.class, long.class, long.class));
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static long alignUp(long n, long alignment) {
        return (n + alignment - 1) & -alignment;
    }

    public static MemorySegment alignUp(MemorySegment ms, long alignment) {
        long offset = ms.address();
        return ms.asSlice(alignUp(offset, alignment) - offset);
    }

    public static VarHandle makeSegmentViewVarHandle(ValueLayout layout) {
        final class VarHandleCache {
            private static final Map<ValueLayout, VarHandle> HANDLE_MAP = new ConcurrentHashMap<>();

            static VarHandle put(ValueLayout layout, VarHandle handle) {
                VarHandle prev = HANDLE_MAP.putIfAbsent(layout, handle);
                return prev != null ? prev : handle;
            }

            static VarHandle get(ValueLayout layout) {
                return HANDLE_MAP.get(layout);
            }
        }
        layout = layout.withoutName(); // name doesn't matter
        // keep the addressee layout as it's used below

        VarHandle handle = VarHandleCache.get(layout);
        if (handle != null) {
            return handle;
        }

        Class<?> baseCarrier = layout.carrier();
        if (layout.carrier() == MemorySegment.class) {
            baseCarrier = switch ((int) ValueLayout.ADDRESS.byteSize()) {
                case Long.BYTES -> long.class;
                case Integer.BYTES -> int.class;
                default -> throw new UnsupportedOperationException("Unsupported address layout");
            };
        } else if (layout.carrier() == boolean.class) {
            baseCarrier = byte.class;
        }

        handle = SharedSecrets.getJavaLangInvokeAccess().memorySegmentViewHandle(baseCarrier,
                layout.byteAlignment() - 1, layout.order());

        if (layout.carrier() == boolean.class) {
            handle = MethodHandles.filterValue(handle, BOOL_TO_BYTE, BYTE_TO_BOOL);
        } else if (layout instanceof AddressLayout addressLayout) {
            handle = MethodHandles.filterValue(handle,
                    MethodHandles.explicitCastArguments(ADDRESS_TO_LONG, MethodType.methodType(baseCarrier, MemorySegment.class)),
                    MethodHandles.explicitCastArguments(MethodHandles.insertArguments(LONG_TO_ADDRESS, 1,
                            pointeeByteSize(addressLayout), pointeeByteAlign(addressLayout)),
                            MethodType.methodType(MemorySegment.class, baseCarrier)));
        }
        return VarHandleCache.put(layout, handle);
    }

    public static boolean byteToBoolean(byte b) {
        return b != 0;
    }

    private static byte booleanToByte(boolean b) {
        return b ? (byte)1 : (byte)0;
    }

    @ForceInline
    public static MemorySegment longToAddress(long addr, long size, long align) {
        if (!isAligned(addr, align)) {
            throw new IllegalArgumentException("Invalid alignment constraint for address: " + toHexString(addr));
        }
        return SegmentFactories.makeNativeSegmentUnchecked(addr, size);
    }

    @ForceInline
    public static MemorySegment longToAddress(long addr, long size, long align, MemorySessionImpl scope) {
        if (!isAligned(addr, align)) {
            throw new IllegalArgumentException("Invalid alignment constraint for address: " + toHexString(addr));
        }
        return SegmentFactories.makeNativeSegmentUnchecked(addr, size, scope);
    }

    @ForceInline
    public static boolean isAligned(long offset, long align) {
        return (offset & (align - 1)) == 0;
    }

    @ForceInline
    public static boolean isElementAligned(ValueLayout layout) {
        // Fast-path: if both size and alignment are powers of two, we can just
        // check if one is greater than the other.
        assert isPowerOfTwo(layout.byteSize());
        return layout.byteAlignment() <= layout.byteSize();
    }

    @ForceInline
    public static void checkElementAlignment(ValueLayout layout, String msg) {
        if (!isElementAligned(layout)) {
            throw new IllegalArgumentException(msg);
        }
    }

    @ForceInline
    public static void checkElementAlignment(MemoryLayout layout, String msg) {
        if (layout.byteSize() % layout.byteAlignment() != 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    public static long pointeeByteSize(AddressLayout addressLayout) {
        return addressLayout.targetLayout()
                .map(MemoryLayout::byteSize)
                .orElse(0L);
    }

    public static long pointeeByteAlign(AddressLayout addressLayout) {
        return addressLayout.targetLayout()
                .map(MemoryLayout::byteAlignment)
                .orElse(1L);
    }

    public static void checkAllocationSizeAndAlign(long byteSize, long byteAlignment) {
        // size should be >= 0
        if (byteSize < 0) {
            throw new IllegalArgumentException("Invalid allocation size : " + byteSize);
        }

        checkAlign(byteAlignment);
    }

    public static void checkAlign(long byteAlignment) {
        // alignment should be > 0, and power of two
        if (byteAlignment <= 0 ||
                ((byteAlignment & (byteAlignment - 1)) != 0L)) {
            throw new IllegalArgumentException("Invalid alignment constraint : " + byteAlignment);
        }
    }

    private static long computePadding(long offset, long align) {
        boolean isAligned = offset == 0 || offset % align == 0;
        if (isAligned) {
            return 0;
        } else {
            long gap = offset % align;
            return align - gap;
        }
    }

    /**
     * {@return return a struct layout constructed from the given elements, with padding
     * computed automatically so that they are naturally aligned}.
     *
     * @param elements the structs' fields
     */
    public static StructLayout computePaddedStructLayout(MemoryLayout... elements) {
        long offset = 0L;
        List<MemoryLayout> layouts = new ArrayList<>();
        long align = 0;
        for (MemoryLayout l : elements) {
            long padding = computePadding(offset, l.byteAlignment());
            if (padding != 0) {
                layouts.add(MemoryLayout.paddingLayout(padding));
                offset += padding;
            }
            layouts.add(l);
            align = Math.max(align, l.byteAlignment());
            offset += l.byteSize();
        }
        long padding = computePadding(offset, align);
        if (padding != 0) {
            layouts.add(MemoryLayout.paddingLayout(padding));
        }
        return MemoryLayout.structLayout(layouts.toArray(MemoryLayout[]::new));
    }

    public static int byteWidthOfPrimitive(Class<?> primitive) {
        return Wrapper.forPrimitiveType(primitive).bitWidth() / 8;
    }

    public static boolean isPowerOfTwo(long value) {
        return (value & (value - 1)) == 0L;
    }

    public static <L extends MemoryLayout> L wrapOverflow(Supplier<L> layoutSupplier) {
        try {
            return layoutSupplier.get();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Layout size exceeds Long.MAX_VALUE");
        }
    }

    public static boolean containsNullChars(String s) {
        return s.indexOf('\u0000') >= 0;
    }

    public static String toHexString(long value) {
        return "0x" + Long.toHexString(value);
    }

    public record BaseAndScale(int base, long scale) {

        public static final BaseAndScale BYTE =
                new BaseAndScale(Unsafe.ARRAY_BYTE_BASE_OFFSET, Unsafe.ARRAY_BYTE_INDEX_SCALE);
        public static final BaseAndScale CHAR =
                new BaseAndScale(Unsafe.ARRAY_CHAR_BASE_OFFSET, Unsafe.ARRAY_CHAR_INDEX_SCALE);
        public static final BaseAndScale SHORT =
                new BaseAndScale(Unsafe.ARRAY_SHORT_BASE_OFFSET, Unsafe.ARRAY_SHORT_INDEX_SCALE);
        public static final BaseAndScale INT =
                new BaseAndScale(Unsafe.ARRAY_INT_BASE_OFFSET, Unsafe.ARRAY_INT_INDEX_SCALE);
        public static final BaseAndScale FLOAT =
                new BaseAndScale(Unsafe.ARRAY_FLOAT_BASE_OFFSET, Unsafe.ARRAY_FLOAT_INDEX_SCALE);
        public static final BaseAndScale LONG =
                new BaseAndScale(Unsafe.ARRAY_LONG_BASE_OFFSET, Unsafe.ARRAY_LONG_INDEX_SCALE);
        public static final BaseAndScale DOUBLE =
                new BaseAndScale(Unsafe.ARRAY_DOUBLE_BASE_OFFSET, Unsafe.ARRAY_DOUBLE_INDEX_SCALE);

        public static BaseAndScale of(Object array) {
            return switch (array) {
                case byte[]   _ -> BaseAndScale.BYTE;
                case char[]   _ -> BaseAndScale.CHAR;
                case short[]  _ -> BaseAndScale.SHORT;
                case int[]    _ -> BaseAndScale.INT;
                case float[]  _ -> BaseAndScale.FLOAT;
                case long[]   _ -> BaseAndScale.LONG;
                case double[] _ -> BaseAndScale.DOUBLE;
                default -> throw new IllegalArgumentException("Not a supported array class: " + array.getClass().getSimpleName());
            };
        }

    }

}
