/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Arm Limited. All rights reserved.
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
package jdk.internal.foreign.abi.aarch64.linux;

import java.lang.foreign.*;
import jdk.internal.foreign.abi.aarch64.TypeClass;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Scoped;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.misc.Unsafe;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static jdk.internal.foreign.PlatformLayouts.AArch64;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static jdk.internal.foreign.abi.SharedUtils.SimpleVaArg;
import static jdk.internal.foreign.abi.SharedUtils.THROWING_ALLOCATOR;
import static jdk.internal.foreign.abi.aarch64.CallArranger.MAX_REGISTER_ARGUMENTS;

/**
 * Standard va_list implementation as defined by AAPCS document and used on
 * Linux. Variadic parameters may be passed in registers or on the stack.
 */
public non-sealed class LinuxAArch64VaList implements VaList, Scoped {
    private static final Unsafe U = Unsafe.getUnsafe();

    // See AAPCS Appendix B "Variable Argument Lists" for definition of
    // va_list on AArch64.
    //
    // typedef struct __va_list {
    //     void *__stack;   // next stack param
    //     void *__gr_top;  // end of GP arg reg save area
    //     void *__vr_top;  // end of FP/SIMD arg reg save area
    //     int __gr_offs;   // offset from __gr_top to next GP register arg
    //     int __vr_offs;   // offset from __vr_top to next FP/SIMD register arg
    // } va_list;

    static final GroupLayout LAYOUT = MemoryLayout.structLayout(
        AArch64.C_POINTER.withName("__stack"),
        AArch64.C_POINTER.withName("__gr_top"),
        AArch64.C_POINTER.withName("__vr_top"),
        AArch64.C_INT.withName("__gr_offs"),
        AArch64.C_INT.withName("__vr_offs")
    ).withName("__va_list");

    private static final long STACK_SLOT_SIZE = 8;

    private static final MemoryLayout GP_REG
        = MemoryLayout.paddingLayout(64).withBitAlignment(64);
    private static final MemoryLayout FP_REG
        = MemoryLayout.paddingLayout(128).withBitAlignment(128);

    private static final MemoryLayout LAYOUT_GP_REGS
        = MemoryLayout.sequenceLayout(MAX_REGISTER_ARGUMENTS, GP_REG);
    private static final MemoryLayout LAYOUT_FP_REGS
        = MemoryLayout.sequenceLayout(MAX_REGISTER_ARGUMENTS, FP_REG);

    private static final int GP_SLOT_SIZE = (int) GP_REG.byteSize();
    private static final int FP_SLOT_SIZE = (int) FP_REG.byteSize();

    private static final int MAX_GP_OFFSET = (int) LAYOUT_GP_REGS.byteSize();
    private static final int MAX_FP_OFFSET = (int) LAYOUT_FP_REGS.byteSize();

    private static final VarHandle VH_stack = LAYOUT.varHandle(groupElement("__stack"));
    private static final VarHandle VH_gr_top = LAYOUT.varHandle(groupElement("__gr_top"));
    private static final VarHandle VH_vr_top = LAYOUT.varHandle(groupElement("__vr_top"));
    private static final VarHandle VH_gr_offs
        = LAYOUT.varHandle(groupElement("__gr_offs"));
    private static final VarHandle VH_vr_offs
        = LAYOUT.varHandle(groupElement("__vr_offs"));

    private static final VaList EMPTY
        = new SharedUtils.EmptyVaList(emptyListAddress());

    private final MemorySegment segment;
    private MemorySegment stack;
    private final MemorySegment gpRegsArea;
    private final long gpLimit;
    private final MemorySegment fpRegsArea;
    private final long fpLimit;

    private LinuxAArch64VaList(MemorySegment segment, MemorySegment stack,
                               MemorySegment gpRegsArea, long gpLimit, MemorySegment fpRegsArea, long fpLimit) {
        this.segment = segment;
        this.stack = stack;
        this.gpRegsArea = gpRegsArea;
        this.gpLimit = gpLimit;
        this.fpRegsArea = fpRegsArea;
        this.fpLimit = fpLimit;
    }

    private static LinuxAArch64VaList readFromSegment(MemorySegment segment) {
        MemorySegment stack = MemorySegment.ofAddress(stackPtr(segment),
                Long.MAX_VALUE, segment.session()); // size unknown

        MemorySegment gpRegsArea = MemorySegment.ofAddress(grTop(segment).addOffset(-MAX_GP_OFFSET),
                MAX_GP_OFFSET, segment.session());

        MemorySegment fpRegsArea = MemorySegment.ofAddress(vrTop(segment).addOffset(-MAX_FP_OFFSET),
                MAX_FP_OFFSET, segment.session());
        return new LinuxAArch64VaList(segment, stack, gpRegsArea, MAX_GP_OFFSET, fpRegsArea, MAX_FP_OFFSET);
    }

    private static MemoryAddress emptyListAddress() {
        long ptr = U.allocateMemory(LAYOUT.byteSize());
        MemorySession session = MemorySession.openImplicit();
        session.addCloseAction(() -> U.freeMemory(ptr));
        MemorySegment ms = MemorySegment.ofAddress(MemoryAddress.ofLong(ptr),
                LAYOUT.byteSize(), session);
        VH_stack.set(ms, MemoryAddress.NULL);
        VH_gr_top.set(ms, MemoryAddress.NULL);
        VH_vr_top.set(ms, MemoryAddress.NULL);
        VH_gr_offs.set(ms, 0);
        VH_vr_offs.set(ms, 0);
        return ms.address();
    }

    public static VaList empty() {
        return EMPTY;
    }

    private MemoryAddress grTop() {
        return grTop(segment);
    }

    private static MemoryAddress grTop(MemorySegment segment) {
        return (MemoryAddress) VH_gr_top.get(segment);
    }

    private MemoryAddress vrTop() {
        return vrTop(segment);
    }

    private static MemoryAddress vrTop(MemorySegment segment) {
        return (MemoryAddress) VH_vr_top.get(segment);
    }

    private int grOffs() {
        final int offs = (int) VH_gr_offs.get(segment);
        assert offs <= 0;
        return offs;
    }

    private int vrOffs() {
        final int offs = (int) VH_vr_offs.get(segment);
        assert offs <= 0;
        return offs;
    }

    private static MemoryAddress stackPtr(MemorySegment segment) {
        return (MemoryAddress) VH_stack.get(segment);
    }

    private MemoryAddress stackPtr() {
        return stackPtr(segment);
    }

    private void setStack(MemorySegment newStack) {
        stack = newStack;
        VH_stack.set(segment, stack.address());
    }

    private void consumeGPSlots(int num) {
        final int old = (int) VH_gr_offs.get(segment);
        VH_gr_offs.set(segment, old + num * GP_SLOT_SIZE);
    }

    private void consumeFPSlots(int num) {
        final int old = (int) VH_vr_offs.get(segment);
        VH_vr_offs.set(segment, old + num * FP_SLOT_SIZE);
    }

    private long currentGPOffset() {
        // Offset from start of GP register segment. __gr_top points to the top
        // (highest address) of the GP registers area. __gr_offs is the negative
        // offset of next saved register from the top.

        return gpRegsArea.byteSize() + grOffs();
    }

    private long currentFPOffset() {
        // Offset from start of FP register segment. __vr_top points to the top
        // (highest address) of the FP registers area. __vr_offs is the negative
        // offset of next saved register from the top.

        return fpRegsArea.byteSize() + vrOffs();
    }

    private long preAlignOffset(MemoryLayout layout) {
        long alignmentOffset = 0;
        if (layout.byteAlignment() > STACK_SLOT_SIZE) {
            long addr = stack.address().toRawLongValue();
            alignmentOffset = Utils.alignUp(addr, 16) - addr;
        }
        return alignmentOffset;
    }

    private void preAlignStack(MemoryLayout layout) {
        setStack(stack.asSlice(preAlignOffset(layout)));
    }

    private void postAlignStack(MemoryLayout layout) {
        setStack(stack.asSlice(Utils.alignUp(layout.byteSize(), STACK_SLOT_SIZE)));
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
        return (MemorySegment) read( layout, allocator);
    }

    private Object read(MemoryLayout layout) {
        return read(layout, THROWING_ALLOCATOR);
    }

    private Object read(MemoryLayout layout, SegmentAllocator allocator) {
        Objects.requireNonNull(layout);
        TypeClass typeClass = TypeClass.classifyLayout(layout);
        if (isRegOverflow(currentGPOffset(), currentFPOffset(), typeClass, layout)) {
            checkStackElement(layout);
            preAlignStack(layout);
            return switch (typeClass) {
                case STRUCT_REGISTER, STRUCT_HFA, STRUCT_REFERENCE -> {
                    MemorySegment slice = stack.asSlice(0, layout.byteSize());
                    MemorySegment seg = allocator.allocate(layout);
                    seg.copyFrom(slice);
                    postAlignStack(layout);
                    yield seg;
                }
                case POINTER, INTEGER, FLOAT -> {
                    VarHandle reader = layout.varHandle();
                    MemorySegment slice = stack.asSlice(0, layout.byteSize());
                    Object res = reader.get(slice);
                    postAlignStack(layout);
                    yield res;
                }
            };
        } else {
            return switch (typeClass) {
                case STRUCT_REGISTER -> {
                    checkGPElement(layout, numSlots(layout));
                    // Struct is passed packed in integer registers.
                    MemorySegment value = allocator.allocate(layout);
                    long offset = 0;
                    while (offset < layout.byteSize()) {
                        final long copy = Math.min(layout.byteSize() - offset, 8);
                        MemorySegment.copy(gpRegsArea, currentGPOffset(), value, offset, copy);
                        consumeGPSlots(1);
                        offset += copy;
                    }
                    yield value;
                }
                case STRUCT_HFA -> {
                    checkFPElement(layout, numSlots(layout));
                    // Struct is passed with each element in a separate floating
                    // point register.
                    MemorySegment value = allocator.allocate(layout);
                    GroupLayout group = (GroupLayout)layout;
                    long offset = 0;
                    for (MemoryLayout elem : group.memberLayouts()) {
                        assert elem.byteSize() <= 8;
                        final long copy = elem.byteSize();
                        MemorySegment.copy(fpRegsArea, currentFPOffset(), value, offset, copy);
                        consumeFPSlots(1);
                        offset += copy;
                    }
                    yield value;
                }
                case STRUCT_REFERENCE -> {
                    checkGPElement(layout, 1);
                    // Struct is passed indirectly via a pointer in an integer register.
                    VarHandle ptrReader = AArch64.C_POINTER.varHandle();
                    MemoryAddress ptr = (MemoryAddress) ptrReader.get(
                        gpRegsArea.asSlice(currentGPOffset()));
                    consumeGPSlots(1);

                    MemorySegment slice = MemorySegment.ofAddress(ptr, layout.byteSize(), session());
                    MemorySegment seg = allocator.allocate(layout);
                    seg.copyFrom(slice);
                    yield seg;
                }
                case POINTER, INTEGER -> {
                    checkGPElement(layout, 1);
                    VarHandle reader = layout.varHandle();
                    Object res = reader.get(gpRegsArea.asSlice(currentGPOffset()));
                    consumeGPSlots(1);
                    yield res;
                }
                case FLOAT -> {
                    checkFPElement(layout, 1);
                    VarHandle reader = layout.varHandle();
                    Object res = reader.get(fpRegsArea.asSlice(currentFPOffset()));
                    consumeFPSlots(1);
                    yield res;
                }
            };
        }
    }

    private void checkGPElement(MemoryLayout layout, long slots) {
        if ((grOffs() + MAX_GP_OFFSET) + (slots * GP_SLOT_SIZE) > gpLimit) {
            throw SharedUtils.newVaListNSEE(layout);
        }
    }

    private void checkFPElement(MemoryLayout layout, long slots) {
        if ((vrOffs() + MAX_FP_OFFSET) + (slots * FP_SLOT_SIZE) > fpLimit) {
            throw SharedUtils.newVaListNSEE(layout);
        }
    }

    private void checkStackElement(MemoryLayout layout) {
        if (preAlignOffset(layout) + layout.byteSize() > stack.byteSize()) {
            throw SharedUtils.newVaListNSEE(layout);
        }
    }

    @Override
    public void skip(MemoryLayout... layouts) {
        Objects.requireNonNull(layouts);
        sessionImpl().checkValidState();
        for (MemoryLayout layout : layouts) {
            Objects.requireNonNull(layout);
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            if (isRegOverflow(currentGPOffset(), currentFPOffset(), typeClass, layout)) {
                checkStackElement(layout);
                preAlignStack(layout);
                postAlignStack(layout);
            } else if (typeClass == TypeClass.FLOAT || typeClass == TypeClass.STRUCT_HFA) {
                long slots = numSlots(layout);
                checkFPElement(layout, slots);
                consumeFPSlots((int) slots);
            } else if (typeClass == TypeClass.STRUCT_REFERENCE) {
                checkGPElement(layout, 1);
                consumeGPSlots(1);
            } else {
                long slots = numSlots(layout);
                checkGPElement(layout, slots);
                consumeGPSlots((int) slots);
            }
        }
    }

    static LinuxAArch64VaList.Builder builder(MemorySession session) {
        return new LinuxAArch64VaList.Builder(session);
    }

    public static VaList ofAddress(MemoryAddress ma, MemorySession session) {
        return readFromSegment(MemorySegment.ofAddress(ma, LAYOUT.byteSize(), session));
    }

    @Override
    public MemorySession session() {
        return segment.session();
    }

    @Override
    public VaList copy() {
        MemorySegment copy = MemorySegment.allocateNative(LAYOUT, segment.session());
        copy.copyFrom(segment);
        return new LinuxAArch64VaList(copy, stack, gpRegsArea, gpLimit, fpRegsArea, fpLimit);
    }

    @Override
    public MemoryAddress address() {
        return segment.address();
    }

    private static long numSlots(MemoryLayout layout) {
        return Utils.alignUp(layout.byteSize(), STACK_SLOT_SIZE) / STACK_SLOT_SIZE;
    }

    private static boolean isRegOverflow(long currentGPOffset, long currentFPOffset,
                                         TypeClass typeClass, MemoryLayout layout) {
        if (typeClass == TypeClass.FLOAT || typeClass == TypeClass.STRUCT_HFA) {
            return currentFPOffset > MAX_FP_OFFSET - numSlots(layout) * FP_SLOT_SIZE;
        } else if (typeClass == TypeClass.STRUCT_REFERENCE) {
            return currentGPOffset > MAX_GP_OFFSET - GP_SLOT_SIZE;
        } else {
            return currentGPOffset > MAX_GP_OFFSET - numSlots(layout) * GP_SLOT_SIZE;
        }
    }

    @Override
    public String toString() {
        return "LinuxAArch64VaList{"
            + "__stack=" + stackPtr()
            + ", __gr_top=" + grTop()
            + ", __vr_top=" + vrTop()
            + ", __gr_offs=" + grOffs()
            + ", __vr_offs=" + vrOffs()
            + '}';
    }

    public static non-sealed class Builder implements VaList.Builder {
        private final MemorySession session;
        private final MemorySegment gpRegs;
        private final MemorySegment fpRegs;

        private long currentGPOffset = 0;
        private long currentFPOffset = 0;
        private final List<SimpleVaArg> stackArgs = new ArrayList<>();

        Builder(MemorySession session) {
            this.session = session;
            this.gpRegs = MemorySegment.allocateNative(LAYOUT_GP_REGS, session);
            this.fpRegs = MemorySegment.allocateNative(LAYOUT_FP_REGS, session);
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
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            if (isRegOverflow(currentGPOffset, currentFPOffset, typeClass, layout)) {
                stackArgs.add(new SimpleVaArg(layout, value));
            } else {
                switch (typeClass) {
                    case STRUCT_REGISTER -> {
                        // Struct is passed packed in integer registers.
                        MemorySegment valueSegment = (MemorySegment) value;
                        long offset = 0;
                        while (offset < layout.byteSize()) {
                            final long copy = Math.min(layout.byteSize() - offset, 8);
                            MemorySegment.copy(valueSegment, offset, gpRegs, currentGPOffset, copy);
                            currentGPOffset += GP_SLOT_SIZE;
                            offset += copy;
                        }
                    }
                    case STRUCT_HFA -> {
                        // Struct is passed with each element in a separate floating
                        // point register.
                        MemorySegment valueSegment = (MemorySegment) value;
                        GroupLayout group = (GroupLayout)layout;
                        long offset = 0;
                        for (MemoryLayout elem : group.memberLayouts()) {
                            assert elem.byteSize() <= 8;
                            final long copy = elem.byteSize();
                            MemorySegment.copy(valueSegment, offset, fpRegs, currentFPOffset, copy);
                            currentFPOffset += FP_SLOT_SIZE;
                            offset += copy;
                        }
                    }
                    case STRUCT_REFERENCE -> {
                        // Struct is passed indirectly via a pointer in an integer register.
                        MemorySegment valueSegment = (MemorySegment) value;
                        VarHandle writer = AArch64.C_POINTER.varHandle();
                        writer.set(gpRegs.asSlice(currentGPOffset),
                                   valueSegment.address());
                        currentGPOffset += GP_SLOT_SIZE;
                    }
                    case POINTER, INTEGER -> {
                        VarHandle writer = layout.varHandle();
                        writer.set(gpRegs.asSlice(currentGPOffset), value);
                        currentGPOffset += GP_SLOT_SIZE;
                    }
                    case FLOAT -> {
                        VarHandle writer = layout.varHandle();
                        writer.set(fpRegs.asSlice(currentFPOffset), value);
                        currentFPOffset += FP_SLOT_SIZE;
                    }
                }
            }
            return this;
        }

        private boolean isEmpty() {
            return currentGPOffset == 0 && currentFPOffset == 0 && stackArgs.isEmpty();
        }

        public VaList build() {
            if (isEmpty()) {
                return EMPTY;
            }

            SegmentAllocator allocator = SegmentAllocator.newNativeArena(session);
            MemorySegment vaListSegment = allocator.allocate(LAYOUT);
            MemorySegment stackArgsSegment;
            if (!stackArgs.isEmpty()) {
                long stackArgsSize = stackArgs.stream()
                    .reduce(0L, (acc, e) -> acc + Utils.alignUp(e.layout.byteSize(), STACK_SLOT_SIZE), Long::sum);
                stackArgsSegment = allocator.allocate(stackArgsSize, 16);
                MemorySegment writeCursor = stackArgsSegment;
                for (SimpleVaArg arg : stackArgs) {
                    final long alignedSize = Utils.alignUp(arg.layout.byteSize(), STACK_SLOT_SIZE);
                    writeCursor = Utils.alignUp(writeCursor, alignedSize);
                    VarHandle writer = arg.varHandle();
                    writer.set(writeCursor, arg.value);
                    writeCursor = writeCursor.asSlice(alignedSize);
                }
            } else {
                stackArgsSegment = MemorySegment.ofAddress(MemoryAddress.NULL, 0, session);
            }

            VH_gr_top.set(vaListSegment, gpRegs.asSlice(gpRegs.byteSize()).address());
            VH_vr_top.set(vaListSegment, fpRegs.asSlice(fpRegs.byteSize()).address());
            VH_stack.set(vaListSegment, stackArgsSegment.address());
            VH_gr_offs.set(vaListSegment, -MAX_GP_OFFSET);
            VH_vr_offs.set(vaListSegment, -MAX_FP_OFFSET);

            assert gpRegs.session().ownerThread() == vaListSegment.session().ownerThread();
            assert fpRegs.session().ownerThread() == vaListSegment.session().ownerThread();
            return new LinuxAArch64VaList(vaListSegment, stackArgsSegment, gpRegs, currentGPOffset, fpRegs, currentFPOffset);
        }
    }
}
