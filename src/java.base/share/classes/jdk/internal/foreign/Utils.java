/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.vm.annotation.ForceInline;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * This class contains misc helper functions to support creation of memory segments.
 */
public final class Utils {
    private static final MethodHandle BYTE_TO_BOOL;
    private static final MethodHandle BOOL_TO_BYTE;
    private static final MethodHandle ADDRESS_TO_LONG;
    private static final MethodHandle LONG_TO_ADDRESS_SAFE;
    private static final MethodHandle LONG_TO_ADDRESS_UNSAFE;
    public static final MethodHandle MH_BITS_TO_BYTES_OR_THROW_FOR_OFFSET;

    public static final Supplier<RuntimeException> BITS_TO_BYTES_THROW_OFFSET
            = () -> new UnsupportedOperationException("Cannot compute byte offset; bit offset is not a multiple of 8");

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            BYTE_TO_BOOL = lookup.findStatic(Utils.class, "byteToBoolean",
                    MethodType.methodType(boolean.class, byte.class));
            BOOL_TO_BYTE = lookup.findStatic(Utils.class, "booleanToByte",
                    MethodType.methodType(byte.class, boolean.class));
            ADDRESS_TO_LONG = lookup.findStatic(SharedUtils.class, "unboxSegment",
                    MethodType.methodType(long.class, MemorySegment.class));
            LONG_TO_ADDRESS_SAFE = lookup.findStatic(Utils.class, "longToAddressSafe",
                    MethodType.methodType(MemorySegment.class, long.class));
            LONG_TO_ADDRESS_UNSAFE = lookup.findStatic(Utils.class, "longToAddressUnsafe",
                    MethodType.methodType(MemorySegment.class, long.class));
            MH_BITS_TO_BYTES_OR_THROW_FOR_OFFSET = MethodHandles.insertArguments(
                    lookup.findStatic(Utils.class, "bitsToBytesOrThrow",
                            MethodType.methodType(long.class, long.class, Supplier.class)),
                    1,
                    BITS_TO_BYTES_THROW_OFFSET);
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

    public static long bitsToBytesOrThrow(long bits, Supplier<RuntimeException> exFactory) {
        if (Utils.isAligned(bits, 8)) {
            return bits / 8;
        } else {
            throw exFactory.get();
        }
    }

    public static VarHandle makeSegmentViewVarHandle(ValueLayout layout) {
        class VarHandleCache {
            private static final Map<ValueLayout, VarHandle> handleMap = new ConcurrentHashMap<>();

            static VarHandle put(ValueLayout layout, VarHandle handle) {
                VarHandle prev = handleMap.putIfAbsent(layout, handle);
                return prev != null ? prev : handle;
            }
        }
        Class<?> baseCarrier = layout.carrier();
        if (layout.carrier() == MemorySegment.class) {
            baseCarrier = switch ((int) ValueLayout.ADDRESS.byteSize()) {
                case 8 -> long.class;
                case 4 -> int.class;
                default -> throw new UnsupportedOperationException("Unsupported address layout");
            };
        } else if (layout.carrier() == boolean.class) {
            baseCarrier = byte.class;
        }

        VarHandle handle = SharedSecrets.getJavaLangInvokeAccess().memorySegmentViewHandle(baseCarrier,
                layout.byteAlignment() - 1, layout.order());

        if (layout.carrier() == boolean.class) {
            handle = MethodHandles.filterValue(handle, BOOL_TO_BYTE, BYTE_TO_BOOL);
        } else if (layout instanceof ValueLayout.OfAddress addressLayout) {
            handle = MethodHandles.filterValue(handle,
                    MethodHandles.explicitCastArguments(ADDRESS_TO_LONG, MethodType.methodType(baseCarrier, MemorySegment.class)),
                    MethodHandles.explicitCastArguments(addressLayout.isUnbounded() ?
                            LONG_TO_ADDRESS_UNSAFE : LONG_TO_ADDRESS_SAFE, MethodType.methodType(MemorySegment.class, baseCarrier)));
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
    private static MemorySegment longToAddressSafe(long addr) {
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(addr, 0);
    }

    @ForceInline
    private static MemorySegment longToAddressUnsafe(long addr) {
        return NativeMemorySegmentImpl.makeNativeSegmentUnchecked(addr, Long.MAX_VALUE);
    }

    public static void copy(MemorySegment addr, byte[] bytes) {
        var heapSegment = MemorySegment.ofArray(bytes);
        addr.copyFrom(heapSegment);
        addr.set(JAVA_BYTE, bytes.length, (byte)0);
    }

    public static MemorySegment toCString(byte[] bytes, SegmentAllocator allocator) {
        MemorySegment addr = allocator.allocate(bytes.length + 1);
        copy(addr, bytes);
        return addr;
    }

    @ForceInline
    public static boolean isAligned(long offset, long align) {
        return (offset & (align - 1)) == 0;
    }

    @ForceInline
    public static void checkElementAlignment(MemoryLayout layout, String msg) {
        if (layout.bitAlignment() > layout.bitSize()) {
            throw new IllegalArgumentException(msg);
        }
    }

    public static long pointeeSize(MemoryLayout layout) {
        if (layout instanceof ValueLayout.OfAddress addressLayout) {
            return addressLayout.isUnbounded() ? Long.MAX_VALUE : 0L;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public static void checkAllocationSizeAndAlign(long byteSize, long byteAlignment, long maxAlignment) {
        checkAllocationSizeAndAlign(byteSize, byteAlignment);
        if (maxAlignment != 0 && byteAlignment > maxAlignment) {
            throw new IllegalArgumentException("Invalid alignment constraint : " + byteAlignment + " > " + maxAlignment);
        }
    }

    public static void checkAllocationSizeAndAlign(long byteSize, long byteAlignment) {
        // size should be >= 0
        if (byteSize < 0) {
            throw new IllegalArgumentException("Invalid allocation size : " + byteSize);
        }

        // alignment should be > 0, and power of two
        if (byteAlignment <= 0 ||
                ((byteAlignment & (byteAlignment - 1)) != 0L)) {
            throw new IllegalArgumentException("Invalid alignment constraint : " + byteAlignment);
        }
    }
}
