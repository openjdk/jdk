/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.ffi.util;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.stream.Collectors;

import jdk.internal.misc.Unsafe;

@SuppressWarnings("restricted")
public final class FFMUtils {

    public static final ValueLayout.OfBoolean C_BOOL =
            (ValueLayout.OfBoolean) Linker.nativeLinker().canonicalLayouts().get("bool");

    public static final ValueLayout.OfByte C_CHAR =
            (ValueLayout.OfByte)Linker.nativeLinker().canonicalLayouts().get("char");

    public static final ValueLayout.OfShort C_SHORT =
            (ValueLayout.OfShort) Linker.nativeLinker().canonicalLayouts().get("short");

    public static final ValueLayout.OfInt C_INT =
            (ValueLayout.OfInt) Linker.nativeLinker().canonicalLayouts().get("int");

    public static final ValueLayout.OfLong C_LONG_LONG =
            (ValueLayout.OfLong) Linker.nativeLinker().canonicalLayouts().get("long long");

    public static final ValueLayout.OfFloat C_FLOAT =
            (ValueLayout.OfFloat) Linker.nativeLinker().canonicalLayouts().get("float");

    public static final ValueLayout.OfDouble C_DOUBLE =
            (ValueLayout.OfDouble) Linker.nativeLinker().canonicalLayouts().get("double");

    public static final AddressLayout C_POINTER =
            ((AddressLayout) Linker.nativeLinker().canonicalLayouts().get("void*"))
            .withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, C_CHAR));

    public static final ValueLayout.OfLong C_LONG =
            (ValueLayout.OfLong) Linker.nativeLinker().canonicalLayouts().get("long");

    private FFMUtils() {
    }

    /**
     * Returns a {@code MemorySegment} set to the size of byteSize
     *
     * @param byteSize the size in bytes to be allocated
     * @param byteAlignment the size in bytes for the memory alignment
     *
     * @throws IllegalArgumentException if the maxByteAlignment of the created
     * MemorySegment is less than the provided byteAlignment
     *
     * @return the newly created {@code MemorySegment}
     */
    public static MemorySegment malloc(long byteSize, long byteAlignment) {
        long allocatedMemory = UNSAFE.allocateMemory(byteSize);
        MemorySegment result = MemorySegment.ofAddress(allocatedMemory).reinterpret(byteSize);
        if (result.maxByteAlignment() < byteAlignment) {
            throw new IllegalArgumentException();
        }
        return result;
    }

    /**
     * Takes a {@code MemorySegment} and deallocates the memory at that address
     * @param memorySegment the {@code MemorySegment} that will be deallocated
     */
    public static void free(MemorySegment memorySegment) {
        UNSAFE.freeMemory(memorySegment.address());
    }

    // SegmentAllocator that delegates to Unsafe for memory allocation
    public static final SegmentAllocator SEGMENT_ALLOCATOR = new SegmentAllocator() {
        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return malloc(byteSize, byteAlignment);
        }
    };

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Variables and methods below are extracted from jextract generated
    // code and used by native bindings on all platforms
    public static final boolean TRACE_DOWNCALLS = false;
    static final SymbolLookup SYMBOL_LOOKUP = SymbolLookup.loaderLookup()
            .or(Linker.nativeLinker().defaultLookup());

    public static void traceDowncall(String name, Object... args) {
        String traceArgs = Arrays.stream(args)
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        System.out.printf("%s(%s)\n", name, traceArgs);
    }

    public static MemorySegment findOrThrow(String symbol) {
        return SYMBOL_LOOKUP.findOrThrow(symbol);
    }

    public static MethodHandle upcallHandle(Class<?> fi, String name, FunctionDescriptor fdesc) {
        try {
            return MethodHandles.lookup().findVirtual(fi, name, fdesc.toMethodType());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    public static MemoryLayout align(MemoryLayout layout, long align) {
        return switch (layout) {
            case PaddingLayout p -> p;
            case ValueLayout v -> v.withByteAlignment(align);
            case GroupLayout g -> {
                MemoryLayout[] alignedMembers = g.memberLayouts().stream()
                        .map(m -> align(m, align)).toArray(MemoryLayout[]::new);
                yield g instanceof StructLayout ?
                        MemoryLayout.structLayout(alignedMembers) : MemoryLayout.unionLayout(alignedMembers);
            }
            case SequenceLayout s -> MemoryLayout.sequenceLayout(s.elementCount(), align(s.elementLayout(), align));
        };
    }
}
