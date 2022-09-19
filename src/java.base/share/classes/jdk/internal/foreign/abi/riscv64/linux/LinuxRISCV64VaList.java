/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Institute of Software, Chinese Academy of Sciences. All rights reserved.
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
 *
 */

package jdk.internal.foreign.abi.riscv64.linux;

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Scoped;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.SharedUtils;

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static jdk.internal.foreign.abi.SharedUtils.SimpleVaArg;
import static jdk.internal.foreign.abi.SharedUtils.THROWING_ALLOCATOR;

// In some arch, like x86 or aarch64, va_list is implemented as a struct.
// In riscv64, its implementation is much simple, just a void*.
//
// See https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-cc.adoc#cc-type-representations
public non-sealed class LinuxRISCV64VaList implements VaList, Scoped {
    private final MemorySegment segment;
    private long offset;

    private static final long STACK_SLOT_SIZE = 8;
    private static final VaList EMPTY
            = new SharedUtils.EmptyVaList(MemoryAddress.NULL);

    public static VaList empty() {
        return EMPTY;
    }

    public LinuxRISCV64VaList(MemorySegment segment, long offset) {
        this.segment = segment;
        this.offset = offset;
    }

    @Override
    public MemorySession session() {
        return segment.session();
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
    public MemoryAddress nextVarg(ValueLayout.OfAddress layout) {
        return (MemoryAddress) read(layout);
    }

    @Override
    public MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator) {
        Objects.requireNonNull(allocator);
        return (MemorySegment) read(layout, allocator);
    }

    private Object read(MemoryLayout layout) {
        return read(layout, THROWING_ALLOCATOR);
    }

    private Object read(MemoryLayout layout, SegmentAllocator allocator) {
        Objects.requireNonNull(layout);
        TypeClass typeClass = TypeClass.classifyLayout(layout);
        checkStackElement(layout);
        preAlignStack();
        return switch (typeClass) {
            case INTEGER_8, INTEGER_16, INTEGER_32, INTEGER_64, FLOAT_32, FLOAT_64, POINTER -> {
                VarHandle reader = layout.varHandle();
                MemorySegment slice = segment.asSlice(offset, layout.byteSize());
                Object res = reader.get(slice);
                postAlignStack(layout);
                yield res;
            }
            case STRUCT_A, STRUCT_FA, STRUCT_BOTH -> {
                // Struct is passed indirectly via a pointer in an integer register.
                MemorySegment slice = segment.asSlice(offset, layout.byteSize());
                MemorySegment seg = allocator.allocate(layout);
                seg.copyFrom(slice);
                postAlignStack(layout);
                yield seg;
            }
            case STRUCT_REFERENCE -> {
                VarHandle addrReader = ADDRESS.varHandle();
                MemorySegment slice = segment.asSlice(offset, ADDRESS.byteSize());
                MemoryAddress addr = (MemoryAddress) addrReader.get(slice);
                postAlignStack(ADDRESS);
                MemorySegment seg = allocator.allocate(layout);
                seg.copyFrom(MemorySegment.ofAddress(addr, layout.byteSize(), session()));
                yield seg;
            }
        };
    }

    private void checkStackElement(MemoryLayout layout) {
        if (Utils.alignUp(layout.byteSize(), STACK_SLOT_SIZE) > segment.byteSize()) {
            throw SharedUtils.newVaListNSEE(layout);
        }
    }

    private void preAlignStack() {
        offset = Utils.alignUp(offset, STACK_SLOT_SIZE);
    }

    private void postAlignStack(MemoryLayout layout) {
        offset += Utils.alignUp(layout.byteSize(), STACK_SLOT_SIZE);
    }

    @Override
    public void skip(MemoryLayout... layouts) {
        Objects.requireNonNull(layouts);
        sessionImpl().checkValidState();
        for (MemoryLayout layout : layouts) {
            Objects.requireNonNull(layout);
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            switch (typeClass) {
                case INTEGER_8, INTEGER_16, INTEGER_32, INTEGER_64, FLOAT_32, FLOAT_64, POINTER, STRUCT_REFERENCE ->
                        offset += 8;
                case STRUCT_A, STRUCT_FA, STRUCT_BOTH -> offset += Utils.alignUp(layout.byteSize(), STACK_SLOT_SIZE);
            }
        }
    }

    @Override
    public VaList copy() {
        MemorySessionImpl sessionImpl = MemorySessionImpl.toSessionImpl(segment.session());
        sessionImpl.checkValidState();
        return new LinuxRISCV64VaList(segment, offset);
    }

    @Override
    public MemoryAddress address() {
        return segment.address().addOffset(offset);
    }

    @Override
    public String toString() {
        return "LinuxRISCV64VaList{" + "seg: " + address() + ", " + "offset: " + offset + '}';
    }

    public static non-sealed class Builder implements VaList.Builder {

        private final MemorySession session;
        private final List<SimpleVaArg> stackArgs = new ArrayList<>();

        Builder(MemorySession session) {
            this.session = session;
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
        public Builder addVarg(ValueLayout.OfAddress layout, Addressable value) {
            return arg(layout, value.address());
        }

        @Override
        public Builder addVarg(GroupLayout layout, MemorySegment value) {
            return arg(layout, value);
        }

        private Builder arg(MemoryLayout layout, Object value) {
            Objects.requireNonNull(layout);
            Objects.requireNonNull(value);
            stackArgs.add(new SimpleVaArg(layout, value));
            return this;
        }

        boolean isEmpty() {
            return stackArgs.isEmpty();
        }

        public VaList build() {
            if (isEmpty()) return EMPTY;
            SegmentAllocator allocator = SegmentAllocator.newNativeArena(session);
            long stackArgsSize = stackArgs.stream()
                                          .reduce(0L, (acc, e) -> {
                                              long elementSize = TypeClass.classifyLayout(e.layout) == TypeClass.STRUCT_REFERENCE ?
                                                      ADDRESS.byteSize() : e.layout.byteSize();
                                              return acc + Utils.alignUp(elementSize, STACK_SLOT_SIZE);
                                          }, Long::sum);
            MemorySegment argsSegment = allocator.allocate(stackArgsSize, 16);
            MemorySegment writeCursor = argsSegment;
            for (SimpleVaArg arg : stackArgs) {
                MemoryLayout layout;
                Object value;
                if (TypeClass.classifyLayout(arg.layout) == TypeClass.STRUCT_REFERENCE) {
                    layout = ADDRESS;
                    value = ((MemorySegment) arg.value).address();
                } else {
                    layout = arg.layout;
                    value = arg.value;
                }
                int alignUp = layout.byteSize() > 8 ? 16 : 8;
                writeCursor = Utils.alignUp(writeCursor, alignUp);
                if (value instanceof MemorySegment) {
                    writeCursor.copyFrom((MemorySegment) value);
                } else {
                    VarHandle writer = layout.varHandle();
                    writer.set(writeCursor, value);
                }
                long alignedSize = Utils.alignUp(layout.byteSize(), STACK_SLOT_SIZE);
                writeCursor = writeCursor.asSlice(alignedSize);
            }
            return new LinuxRISCV64VaList(argsSegment, 0L);
        }

    }

}
