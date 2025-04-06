/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.foreign;

import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import sun.invoke.util.Wrapper;

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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class contains misc helper functions to support creation of memory segments.
 */
public final class Utils {

    // Suppresses default constructor, ensuring non-instantiability.
    private Utils() {}

    private static final Class<?> ADDRESS_CARRIER_TYPE;
    private static final MethodHandle LONG_TO_CARRIER;
    private static final MethodHandle LONG_TO_ADDRESS_TARGET;
    private static final MethodHandle LONG_TO_ADDRESS_NO_TARGET;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        String unboxSegmentName;
        Class<?> rawAddressType;
        if (Unsafe.getUnsafe().addressSize() == 8) {
            unboxSegmentName = "unboxSegment";
            rawAddressType = long.class;
        } else {
            assert Unsafe.getUnsafe().addressSize() == 4 : Unsafe.getUnsafe().addressSize();
            unboxSegmentName = "unboxSegment32";
            rawAddressType = int.class;
        }
        ADDRESS_CARRIER_TYPE = rawAddressType;
        try {
            LONG_TO_CARRIER = lookup.findStatic(SharedUtils.class, unboxSegmentName,
                    MethodType.methodType(rawAddressType, MemorySegment.class));
            LONG_TO_ADDRESS_TARGET = lookup.findStatic(Utils.class, "longToAddress",
                    MethodType.methodType(MemorySegment.class, rawAddressType, AddressLayout.class));
            LONG_TO_ADDRESS_NO_TARGET = lookup.findStatic(Utils.class, "longToAddress",
                    MethodType.methodType(MemorySegment.class, rawAddressType));
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

    /**
     * This method returns a var handle that accesses a target layout in an enclosing layout, taking the memory offset
     * and the base offset of the enclosing layout in the segment.
     * <p>
     * If the offset of the target layout in the enclosing layout is constant, the coordinates are (MS, long).
     * If the offset of the target layout in the enclosing layout is variable, the coordinates are (MS, long, long).
     * The trailing long is a pre-validated, variable extra offset, which the var handle does not perform any size or
     * alignment checks against. Such checks are added (using adaptation) by {@link LayoutPath#dereferenceHandle()}.
     * <p>
     * We provide two level of caching of the generated var handles. First, the var handle associated
     * with a {@link ValueLayout#varHandle()} call is cached inside a stable field of the value layout implementation.
     * This optimizes common code idioms like {@code JAVA_INT.varHandle().getInt(...)}. A second layer of caching
     * is then provided by this method, so different value layouts with same effects can reuse var handle instances.
     * (The 2nd layer may be redundant in the long run)
     *
     * @param enclosing the enclosing context of the value layout
     * @param layout the value layout for which a raw memory segment var handle is to be created
     * @param constantOffset if the VH carries a constant offset instead of taking a variable offset
     * @param offset the offset if it is a constant
     * @return a raw memory segment var handle
     */
    public static VarHandle makeRawSegmentViewVarHandle(MemoryLayout enclosing, ValueLayout layout, boolean constantOffset, long offset) {
        if (enclosing instanceof ValueLayout direct) {
            assert direct.equals(layout) && constantOffset && offset == 0;
            record VarHandleCache() implements Function<ValueLayout, VarHandle> {
                private static final Map<ValueLayout, VarHandle> HANDLE_MAP = new ConcurrentHashMap<>();
                private static final VarHandleCache INSTANCE = new VarHandleCache();

                @Override
                public VarHandle apply(ValueLayout valueLayout) {
                    return Utils.makeRawSegmentViewVarHandleInternal(valueLayout, valueLayout, true, 0);
                }
            }
            return VarHandleCache.HANDLE_MAP.computeIfAbsent(direct.withoutName(), VarHandleCache.INSTANCE);
        }
        return makeRawSegmentViewVarHandleInternal(enclosing, layout, constantOffset, offset);
    }

    private static VarHandle makeRawSegmentViewVarHandleInternal(MemoryLayout enclosing, ValueLayout layout, boolean constantOffset, long offset) {
        Class<?> baseCarrier = layout.carrier();
        if (layout.carrier() == MemorySegment.class) {
            baseCarrier = ADDRESS_CARRIER_TYPE;
        }

        VarHandle handle = SharedSecrets.getJavaLangInvokeAccess().memorySegmentViewHandle(baseCarrier,
                enclosing, layout.byteAlignment() - 1, layout.order(), constantOffset, offset);

        if (layout instanceof AddressLayout addressLayout) {
            MethodHandle longToAddressAdapter = addressLayout.targetLayout().isPresent() ?
                    MethodHandles.insertArguments(LONG_TO_ADDRESS_TARGET, 1, addressLayout) :
                    LONG_TO_ADDRESS_NO_TARGET;
            handle = MethodHandles.filterValue(handle, LONG_TO_CARRIER, longToAddressAdapter);
        }
        return handle;
    }

    public static boolean byteToBoolean(byte b) {
        return b != 0;
    }

    private static byte booleanToByte(boolean b) {
        return b ? (byte)1 : (byte)0;
    }

    @ForceInline
    public static MemorySegment longToAddress(long addr) {
        return longToAddress(addr, 0, 1);
    }

    // 32 bit
    @ForceInline
    public static MemorySegment longToAddress(int addr) {
        return longToAddress(addr, 0, 1);
    }

    @ForceInline
    public static MemorySegment longToAddress(long addr, AddressLayout layout) {
        return longToAddress(addr, pointeeByteSize(layout), pointeeByteAlign(layout));
    }

    // 32 bit
    @ForceInline
    public static MemorySegment longToAddress(int addr, AddressLayout layout) {
        return longToAddress(addr, pointeeByteSize(layout), pointeeByteAlign(layout));
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
        // byteSize should be >= 0
        Utils.checkNonNegativeArgument(byteSize, "allocation size");
        checkAlign(byteAlignment);
    }

    public static void checkAlign(long byteAlignment) {
        // alignment should be > 0, and power of two
        if (byteAlignment <= 0 ||
                ((byteAlignment & (byteAlignment - 1)) != 0L)) {
            throw new IllegalArgumentException("Invalid alignment constraint : " + byteAlignment);
        }
    }

    @ForceInline
    public static void checkNonNegativeArgument(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException("The provided " + name + " is negative: " + value);
        }
    }

    @ForceInline
    public static void checkNonNegativeIndex(long value, String name) {
        if (value < 0) {
            throw new IndexOutOfBoundsException("The provided " + name + " is negative: " + value);
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

    public record BaseAndScale(long base, long scale) {

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
