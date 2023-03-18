/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.VaList;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Utils;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static jdk.internal.foreign.abi.SharedUtils.SimpleVaArg;
import static jdk.internal.foreign.abi.SharedUtils.THROWING_ALLOCATOR;

/**
 * Standard va_list implementation as defined by RISC-V ABI document and used on Linux.
 * In the base integer calling convention, variadic arguments are passed in the same
 * manner as named arguments, with one exception. Variadic arguments with 2 * XLEN-bit
 * alignment and size at most 2 * XLEN bits are passed in an aligned register pair
 * (i.e., the first register in the pair is even-numbered), or on the stack by value
 * if none is available. After a variadic argument has been passed on the stack, all
 * future arguments will also be passed on the stack (i.e. the last argument register
 * may be left unused due to the aligned register pair rule).
 */

public non-sealed class LinuxRISCV64VaList implements VaList {
    // The va_list type is void* on RISCV64.
    // See https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-cc.adoc#cc-type-representations

    private final MemorySegment segment;
    private long offset;

    private static final long STACK_SLOT_SIZE = 8;
    private static final VaList EMPTY
            = new SharedUtils.EmptyVaList(MemorySegment.NULL);

    public static VaList empty() {
        return EMPTY;
    }

    public LinuxRISCV64VaList(MemorySegment segment, long offset) {
        this.segment = segment;
        this.offset = offset;
    }

    private static LinuxRISCV64VaList readFromAddress(long address, SegmentScope scope) {
        MemorySegment segment = MemorySegment.ofAddress(address, Long.MAX_VALUE, scope); // size unknown
        return new LinuxRISCV64VaList(segment, 0);
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
        return read(layout, THROWING_ALLOCATOR);
    }

    private Object read(MemoryLayout layout, SegmentAllocator allocator) {
        Objects.requireNonNull(layout);
        TypeClass typeClass = TypeClass.classifyLayout(layout);
        preAlignStack(layout);

        return switch (typeClass) {
            case INTEGER, FLOAT, POINTER -> {
                checkStackElement(layout);
                VarHandle reader = layout.varHandle();
                MemorySegment slice = segment.asSlice(offset, layout.byteSize());
                Object res = reader.get(slice);
                postAlignStack(layout);
                yield res;
            }
            case STRUCT_REGISTER_X, STRUCT_REGISTER_F, STRUCT_REGISTER_XF -> {
                checkStackElement(layout);
                // Struct is passed indirectly via a pointer in an integer register.
                MemorySegment slice = segment.asSlice(offset, layout.byteSize());
                MemorySegment seg = allocator.allocate(layout);
                seg.copyFrom(slice);
                postAlignStack(layout);
                yield seg;
            }
            case STRUCT_REFERENCE -> {
                checkStackElement(ADDRESS);
                VarHandle addrReader = ADDRESS.varHandle();
                MemorySegment slice = segment.asSlice(offset, ADDRESS.byteSize());
                MemorySegment addr = (MemorySegment) addrReader.get(slice);
                MemorySegment seg = allocator.allocate(layout);
                seg.copyFrom(MemorySegment.ofAddress(addr.address(), layout.byteSize(), segment.scope()));
                postAlignStack(ADDRESS);
                yield seg;
            }
        };
    }

    private void checkStackElement(MemoryLayout layout) {
        if (Utils.alignUp(layout.byteSize(), STACK_SLOT_SIZE) > segment.byteSize()) {
            throw SharedUtils.newVaListNSEE(layout);
        }
    }

    private void preAlignStack(MemoryLayout layout) {
        if (layout.byteSize() <= 16 && layout.byteAlignment() == 16) {
            offset = Utils.alignUp(offset, 16);
        } else {
            offset = Utils.alignUp(offset, STACK_SLOT_SIZE);
        }
    }

    private void postAlignStack(MemoryLayout layout) {
        if (layout.byteSize() <= 16 && layout.byteAlignment() == 16) {
            offset += 16;
        } else {
            offset += Utils.alignUp(layout.byteSize(), STACK_SLOT_SIZE);
        }
    }

    @Override
    public void skip(MemoryLayout... layouts) {
        Objects.requireNonNull(layouts);
        ((MemorySessionImpl) segment.scope()).checkValidState();
        for (MemoryLayout layout : layouts) {
            Objects.requireNonNull(layout);
            preAlignStack(layout);
            postAlignStack(layout);
        }
    }

    static LinuxRISCV64VaList.Builder builder(SegmentScope scope) {
        return new LinuxRISCV64VaList.Builder(scope);
    }

    public static VaList ofAddress(long address, SegmentScope scope) {
        return readFromAddress(address, scope);
    }

    @Override
    public VaList copy() {
        MemorySessionImpl sessionImpl = (MemorySessionImpl) segment.scope();
        sessionImpl.checkValidState();
        return new LinuxRISCV64VaList(segment, offset);
    }

    @Override
    public MemorySegment segment() {
        // make sure that returned segment cannot be accessed
        return segment.asSlice(0, 0);
    }

    public long address() {
        return segment.address() + offset;
    }

    @Override
    public String toString() {
        return "LinuxRISCV64VaList{" + "seg: " + address() + ", " + "offset: " + offset + '}';
    }

    public static non-sealed class Builder implements VaList.Builder {

        private final SegmentScope scope;
        private final List<SimpleVaArg> stackArgs = new ArrayList<>();

        Builder(SegmentScope scope) {
            this.scope = scope;
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
            if (isEmpty()) {
                return EMPTY;
            }
            long stackArgsSize = 0;
            for (SimpleVaArg arg : stackArgs) {
                MemoryLayout layout = arg.layout;
                long elementSize = TypeClass.classifyLayout(layout) == TypeClass.STRUCT_REFERENCE ?
                    ADDRESS.byteSize() : layout.byteSize();
                // arguments with 2 * XLEN-bit alignment and size at most 2 * XLEN bits
                // are saved on memory aligned with 2 * XLEN (XLEN=64 for RISCV64).
                if (layout.byteSize() <= 16 && layout.byteAlignment() == 16) {
                    stackArgsSize = Utils.alignUp(stackArgsSize, 16);
                    elementSize = 16;
                }
                stackArgsSize += Utils.alignUp(elementSize, STACK_SLOT_SIZE);
            }
            MemorySegment argsSegment = MemorySegment.allocateNative(stackArgsSize, 16, scope);
            MemorySegment writeCursor = argsSegment;
            for (SimpleVaArg arg : stackArgs) {
                MemoryLayout layout;
                Object value = arg.value;
                if (TypeClass.classifyLayout(arg.layout) == TypeClass.STRUCT_REFERENCE) {
                    layout = ADDRESS;
                } else {
                    layout = arg.layout;
                }
                long alignedSize = Utils.alignUp(layout.byteSize(), STACK_SLOT_SIZE);
                if (layout.byteSize() <= 16 && layout.byteAlignment() == 16) {
                    writeCursor = Utils.alignUp(writeCursor, 16);
                    alignedSize = 16;
                }
                if (layout instanceof GroupLayout) {
                    writeCursor.copyFrom((MemorySegment) value);
                } else {
                    VarHandle writer = layout.varHandle();
                    writer.set(writeCursor, value);
                }
                writeCursor = writeCursor.asSlice(alignedSize);
            }
            return new LinuxRISCV64VaList(argsSegment, 0L);
        }
    }
}
