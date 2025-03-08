/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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


import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.stream.Collectors;

import jdk.internal.misc.Unsafe;

@SuppressWarnings("restricted")
public final class FFMUtils {

    private FFMUtils() {
    }

    /**
     *
     * @param byteSize
     * @param byteAlignment
     * @return
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
     *
     * @param memorySegment
     */
    public static void free(MemorySegment memorySegment) {
        UNSAFE.freeMemory(memorySegment.address());
    }

    // SegmentAllocator that delegates to Unsafe for memory allocation
    // TODO: Anonymous class is used instead of lambda since the class can be
    //       used early in the boot sequence.
    public static final SegmentAllocator SEGMENT_ALLOCATOR = new SegmentAllocator() {
        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return malloc(byteSize, byteAlignment);
        }
    };

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Variables and methods below are extracted from jextract generated
    // code and used by native bindings on all platforms
    // TODO: Un-stream the code below since it could be used early in the boot sequence
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

    // TODO: The same concern with switch pattern matching as with streams above
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
