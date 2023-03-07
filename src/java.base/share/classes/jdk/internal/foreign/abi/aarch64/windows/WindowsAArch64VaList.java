/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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
package jdk.internal.foreign.abi.aarch64.windows;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.VaList;
import java.lang.foreign.ValueLayout;
import jdk.internal.foreign.abi.aarch64.TypeClass;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.SharedUtils.SimpleVaArg;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static jdk.internal.foreign.PlatformLayouts.AArch64.C_POINTER;
import static jdk.internal.foreign.abi.SharedUtils.alignUp;

// see vadefs.h (VC header) for the ARM64 va_arg impl
//
//    typedef char* va_list;
//
//    #define __crt_va_arg(ap, t)                                                \
//        ((sizeof(t) > (2 * sizeof(__int64)))                                   \
//            ? **(t**)((ap += sizeof(__int64)) - sizeof(__int64))               \
//            : *(t*)((ap += _SLOTSIZEOF(t) + _APALIGN(t,ap)) - _SLOTSIZEOF(t)))
//
public non-sealed class WindowsAArch64VaList implements VaList {
    private static final long VA_SLOT_SIZE_BYTES = 8;
    private static final VarHandle VH_address = C_POINTER.varHandle();

    private static final VaList EMPTY = new SharedUtils.EmptyVaList(MemorySegment.NULL);

    private MemorySegment segment;

    private WindowsAArch64VaList(MemorySegment segment) {
        this.segment = segment;
    }

    public static VaList empty() {
        return EMPTY;
    }

    @Override
    public int nextVarg(ValueLayout.OfInt layout) {
        return (int) read(layout);
    }

    @Override
    public long nextVarg(ValueLayout.OfLong layout) {
        return (long) read(layout);
    }

    @Override
    public double nextVarg(ValueLayout.OfDouble layout) {
        return (double) read(layout);
    }

    @Override
    public MemorySegment nextVarg(ValueLayout.OfAddress layout) {
        return (MemorySegment) read(layout);
    }

    @Override
    public MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator) {
        Objects.requireNonNull(allocator);
        return (MemorySegment) read(layout, allocator);
    }

    private Object read(MemoryLayout layout) {
        return read(layout, SharedUtils.THROWING_ALLOCATOR);
    }

    private Object read(MemoryLayout layout, SegmentAllocator allocator) {
        Objects.requireNonNull(layout);
        Object res;
        if (layout instanceof GroupLayout) {
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            res = switch (typeClass) {
                case STRUCT_REFERENCE -> {
                    checkElement(layout, VA_SLOT_SIZE_BYTES);
                    MemorySegment structAddr = (MemorySegment) VH_address.get(segment);
                    MemorySegment struct = MemorySegment.ofAddress(structAddr.address(), layout.byteSize(), segment.scope());
                    MemorySegment seg = allocator.allocate(layout);
                    seg.copyFrom(struct);
                    segment = segment.asSlice(VA_SLOT_SIZE_BYTES);
                    yield seg;
                }
                case STRUCT_REGISTER, STRUCT_HFA -> {
                    long size = alignUp(layout.byteSize(), VA_SLOT_SIZE_BYTES);
                    checkElement(layout, size);
                    MemorySegment struct = allocator.allocate(layout)
                            .copyFrom(segment.asSlice(0, layout.byteSize()));
                    segment = segment.asSlice(size);
                    yield struct;
                }
                default -> throw new IllegalStateException("Unexpected TypeClass: " + typeClass);
            };
        } else {
            checkElement(layout, VA_SLOT_SIZE_BYTES);
            VarHandle reader = layout.varHandle();
            res = reader.get(segment);
            segment = segment.asSlice(VA_SLOT_SIZE_BYTES);
        }
        return res;
    }

    private static long sizeOf(MemoryLayout layout) {
        return switch (TypeClass.classifyLayout(layout)) {
            case STRUCT_REGISTER, STRUCT_HFA -> alignUp(layout.byteSize(), VA_SLOT_SIZE_BYTES);
            default -> VA_SLOT_SIZE_BYTES;
        };
    }

    @Override
    public void skip(MemoryLayout... layouts) {
        Objects.requireNonNull(layouts);
        ((MemorySessionImpl) segment.scope()).checkValidState();

        for (MemoryLayout layout : layouts) {
            Objects.requireNonNull(layout);
            long size = sizeOf(layout);
            checkElement(layout, size);
            segment = segment.asSlice(size);
        }
    }

    private void checkElement(MemoryLayout layout, long size) {
        if (segment.byteSize() < size) {
            throw SharedUtils.newVaListNSEE(layout);
        }
    }

    static WindowsAArch64VaList ofAddress(long address, SegmentScope session) {
        MemorySegment segment = MemorySegment.ofAddress(address, Long.MAX_VALUE, session);
        return new WindowsAArch64VaList(segment);
    }

    static Builder builder(SegmentScope session) {
        return new Builder(session);
    }

    @Override
    public VaList copy() {
        ((MemorySessionImpl) segment.scope()).checkValidState();
        return new WindowsAArch64VaList(segment);
    }

    @Override
    public MemorySegment segment() {
        // make sure that returned segment cannot be accessed
        return segment.asSlice(0, 0);
    }

    public static non-sealed class Builder implements VaList.Builder {

        private final SegmentScope session;
        private final List<SimpleVaArg> args = new ArrayList<>();

        public Builder(SegmentScope session) {
            ((MemorySessionImpl) session).checkValidState();
            this.session = session;
        }

        private Builder arg(MemoryLayout layout, Object value) {
            Objects.requireNonNull(layout);
            Objects.requireNonNull(value);
            args.add(new SimpleVaArg(layout, value));
            return this;
        }

        @Override
        public Builder addVarg(ValueLayout.OfInt layout, int value) {
            return arg(layout, value);
        }

        @Override
        public Builder addVarg(ValueLayout.OfLong layout, long value) {
            return arg(layout, value);
        }

        @Override
        public Builder addVarg(ValueLayout.OfDouble layout, double value) {
            return arg(layout, value);
        }

        @Override
        public Builder addVarg(ValueLayout.OfAddress layout, MemorySegment value) {
            return arg(layout, value);
        }

        @Override
        public Builder addVarg(GroupLayout layout, MemorySegment value) {
            return arg(layout, value);
        }

        public VaList build() {
            if (args.isEmpty()) {
                return EMPTY;
            }

            long allocationSize = args.stream().reduce(0L, (acc, e) -> acc + sizeOf(e.layout), Long::sum);
            MemorySegment segment = MemorySegment.allocateNative(allocationSize, session);
            MemorySegment cursor = segment;

            for (SimpleVaArg arg : args) {
                if (arg.layout instanceof GroupLayout) {
                    MemorySegment msArg = ((MemorySegment) arg.value);
                    TypeClass typeClass = TypeClass.classifyLayout(arg.layout);
                    switch (typeClass) {
                        case STRUCT_REFERENCE -> {
                            MemorySegment copy = MemorySegment.allocateNative(arg.layout, session);
                            copy.copyFrom(msArg); // by-value
                            VH_address.set(cursor, copy);
                            cursor = cursor.asSlice(VA_SLOT_SIZE_BYTES);
                        }
                        case STRUCT_REGISTER, STRUCT_HFA ->
                            cursor = cursor.copyFrom(msArg.asSlice(0, arg.layout.byteSize()))
                                    .asSlice(alignUp(arg.layout.byteSize(), VA_SLOT_SIZE_BYTES));
                        default -> throw new IllegalStateException("Unexpected TypeClass: " + typeClass);
                    }
                } else {
                    VarHandle writer = arg.varHandle();
                    writer.set(cursor, arg.value);
                    cursor = cursor.asSlice(VA_SLOT_SIZE_BYTES);
                }
            }

            return new WindowsAArch64VaList(segment);
        }
    }
}
