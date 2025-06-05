/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.openjdk.bench.java.lang.foreign.pointers;


import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

public class Pointer<X> {

    final MemorySegment segment;

    Pointer(MemorySegment segment) {
        this.segment = segment;
    }

    public <Z extends NativeType.OfInt<X>> int get(Z type, long index) {
        return segment.getAtIndex(type.layout(), index);
    }

    public <Z extends NativeType.OfDouble<X>> double get(Z type, long index) {
        return segment.getAtIndex(type.layout(), index);
    }

    public <Z extends NativeType.OfStruct<X>> X get(Z type, long index) {
        return type.make(addOffset(index * type.layout().byteSize()));
    }

    public Pointer<X> addOffset(long offset) {
        return new Pointer<>(segment.asSlice(offset));
    }

    @SuppressWarnings("unchecked")
    public <Z extends NativeType.OfPointer<X>> X get(Z type, long index) {
        MemorySegment address = segment.getAtIndex(type.layout(), index);
        return (X)new Pointer<>(address);
    }

    @SuppressWarnings("unchecked")
    public X get(NativeType<X> type, long offset) {
        if (type instanceof NativeType.OfInt intType) {
            return (X) (Object) get(intType, offset);
        } else if (type instanceof NativeType.OfDouble doubleType) {
            return (X) (Object) get(doubleType, offset);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public MemorySegment segment() {
        return segment;
    }

    public static <X> Pointer<X> allocate(NativeType<X> type, SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(type.layout());
        return new Pointer<>(segment);
    }

    public static <X> Pointer<X> allocate(NativeType<X> type, long size, SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(type.layout(), size);
        return new Pointer<>(segment);
    }

    public static <X> Pointer<X> wrap(NativeType<X> type, MemorySegment segment) {
        return new Pointer<>(segment);
    }
}
