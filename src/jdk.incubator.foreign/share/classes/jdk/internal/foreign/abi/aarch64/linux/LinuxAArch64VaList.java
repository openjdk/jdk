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

import jdk.incubator.foreign.*;
import jdk.internal.foreign.ResourceScopeImpl;
import jdk.internal.foreign.Scoped;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.aarch64.*;
import jdk.internal.misc.Unsafe;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static jdk.internal.foreign.PlatformLayouts.AArch64;

import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;
import static jdk.internal.foreign.abi.SharedUtils.SimpleVaArg;
import static jdk.internal.foreign.abi.SharedUtils.THROWING_ALLOCATOR;
import static jdk.internal.foreign.abi.aarch64.CallArranger.MAX_REGISTER_ARGUMENTS;

/**
 * Standard va_list implementation as defined by AAPCS document and used on
 * Linux. Variadic parameters may be passed in registers or on the stack.
 */
public non-sealed class LinuxAArch64VaList implements VaList, Scoped {
    private static final Unsafe U = Unsafe.getUnsafe();

    static final Class<?> CARRIER = MemoryAddress.class;

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
    private final MemorySegment gpRegsArea;
    private final MemorySegment fpRegsArea;

    private LinuxAArch64VaList(MemorySegment segment, MemorySegment gpRegsArea, MemorySegment fpRegsArea) {
        this.segment = segment;
        this.gpRegsArea = gpRegsArea;
        this.fpRegsArea = fpRegsArea;
    }

    private static LinuxAArch64VaList readFromSegment(MemorySegment segment) {
        MemorySegment gpRegsArea = MemorySegment.ofAddress(grTop(segment).addOffset(-MAX_GP_OFFSET),
                MAX_GP_OFFSET, segment.scope());

        MemorySegment fpRegsArea = MemorySegment.ofAddress(vrTop(segment).addOffset(-MAX_FP_OFFSET),
                MAX_FP_OFFSET, segment.scope());
        return new LinuxAArch64VaList(segment, gpRegsArea, fpRegsArea);
    }

    private static MemoryAddress emptyListAddress() {
        long ptr = U.allocateMemory(LAYOUT.byteSize());
        ResourceScope scope = ResourceScope.newImplicitScope();
        scope.addCloseAction(() -> U.freeMemory(ptr));
        MemorySegment ms = MemorySegment.ofAddress(MemoryAddress.ofLong(ptr),
                LAYOUT.byteSize(), scope);
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

    private MemoryAddress stackPtr() {
        return (MemoryAddress) VH_stack.get(segment);
    }

    private void stackPtr(MemoryAddress ptr) {
        VH_stack.set(segment, ptr);
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

    private void preAlignStack(MemoryLayout layout) {
        if (layout.byteAlignment() > 8) {
            stackPtr(Utils.alignUp(stackPtr(), 16));
        }
    }

    private void postAlignStack(MemoryLayout layout) {
        stackPtr(Utils.alignUp(stackPtr().addOffset(layout.byteSize()), 8));
    }

    @Override
    public int nextVarg(ValueLayout.OfInt layout) {
        return (int) read(int.class, layout);
    }

    @Override
    public long nextVarg(ValueLayout.OfLong layout) {
        return (long) read(long.class, layout);
    }

    @Override
    public double nextVarg(ValueLayout.OfDouble layout) {
        return (double) read(double.class, layout);
    }

    @Override
    public MemoryAddress nextVarg(ValueLayout.OfAddress layout) {
        return (MemoryAddress) read(MemoryAddress.class, layout);
    }

    @Override
    public MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator) {
        Objects.requireNonNull(allocator);
        return (MemorySegment) read(MemorySegment.class, layout, allocator);
    }

    private Object read(Class<?> carrier, MemoryLayout layout) {
        return read(carrier, layout, THROWING_ALLOCATOR);
    }

    private Object read(Class<?> carrier, MemoryLayout layout, SegmentAllocator allocator) {
        Objects.requireNonNull(layout);
        TypeClass typeClass = TypeClass.classifyLayout(layout);
        if (isRegOverflow(currentGPOffset(), currentFPOffset(), typeClass, layout)) {
            preAlignStack(layout);
            return switch (typeClass) {
                case STRUCT_REGISTER, STRUCT_HFA, STRUCT_REFERENCE -> {
                    MemorySegment slice = MemorySegment.ofAddress(stackPtr(), layout.byteSize(), scope());
                    MemorySegment seg = allocator.allocate(layout);
                    seg.copyFrom(slice);
                    postAlignStack(layout);
                    yield seg;
                }
                case POINTER, INTEGER, FLOAT -> {
                    VarHandle reader = layout.varHandle();
                    MemorySegment slice = MemorySegment.ofAddress(stackPtr(), layout.byteSize(), scope());
                    Object res = reader.get(slice);
                    postAlignStack(layout);
                    yield res;
                }
            };
        } else {
            return switch (typeClass) {
                case STRUCT_REGISTER -> {
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
                    // Struct is passed indirectly via a pointer in an integer register.
                    VarHandle ptrReader = AArch64.C_POINTER.varHandle();
                    MemoryAddress ptr = (MemoryAddress) ptrReader.get(
                        gpRegsArea.asSlice(currentGPOffset()));
                    consumeGPSlots(1);

                    MemorySegment slice = MemorySegment.ofAddress(ptr, layout.byteSize(), scope());
                    MemorySegment seg = allocator.allocate(layout);
                    seg.copyFrom(slice);
                    yield seg;
                }
                case POINTER, INTEGER -> {
                    VarHandle reader = layout.varHandle();
                    Object res = reader.get(gpRegsArea.asSlice(currentGPOffset()));
                    consumeGPSlots(1);
                    yield res;
                }
                case FLOAT -> {
                    VarHandle reader = layout.varHandle();
                    Object res = reader.get(fpRegsArea.asSlice(currentFPOffset()));
                    consumeFPSlots(1);
                    yield res;
                }
            };
        }
    }

    @Override
    public void skip(MemoryLayout... layouts) {
        Objects.requireNonNull(layouts);
        ((ResourceScopeImpl)segment.scope()).checkValidStateSlow();
        for (MemoryLayout layout : layouts) {
            Objects.requireNonNull(layout);
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            if (isRegOverflow(currentGPOffset(), currentFPOffset(), typeClass, layout)) {
                preAlignStack(layout);
                postAlignStack(layout);
            } else if (typeClass == TypeClass.FLOAT || typeClass == TypeClass.STRUCT_HFA) {
                consumeFPSlots(numSlots(layout));
            } else if (typeClass == TypeClass.STRUCT_REFERENCE) {
                consumeGPSlots(1);
            } else {
                consumeGPSlots(numSlots(layout));
            }
        }
    }

    static LinuxAArch64VaList.Builder builder(ResourceScope scope) {
        return new LinuxAArch64VaList.Builder(scope);
    }

    public static VaList ofAddress(MemoryAddress ma, ResourceScope scope) {
        return readFromSegment(MemorySegment.ofAddress(ma, LAYOUT.byteSize(), scope));
    }

    @Override
    public ResourceScope scope() {
        return segment.scope();
    }

    @Override
    public VaList copy() {
        MemorySegment copy = MemorySegment.allocateNative(LAYOUT, segment.scope());
        copy.copyFrom(segment);
        return new LinuxAArch64VaList(copy, gpRegsArea, fpRegsArea);
    }

    @Override
    public MemoryAddress address() {
        return segment.address();
    }

    private static int numSlots(MemoryLayout layout) {
        return (int) Utils.alignUp(layout.byteSize(), 8) / 8;
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
        private final ResourceScope scope;
        private final MemorySegment gpRegs;
        private final MemorySegment fpRegs;

        private long currentGPOffset = 0;
        private long currentFPOffset = 0;
        private final List<SimpleVaArg> stackArgs = new ArrayList<>();

        Builder(ResourceScope scope) {
            this.scope = scope;
            this.gpRegs = MemorySegment.allocateNative(LAYOUT_GP_REGS, scope);
            this.fpRegs = MemorySegment.allocateNative(LAYOUT_FP_REGS, scope);
        }

        @Override
        public Builder addVarg(ValueLayout.OfInt layout, int value) {
            return arg(int.class, layout, value);
        }

        @Override
        public Builder addVarg(ValueLayout.OfLong layout, long value) {
            return arg(long.class, layout, value);
        }

        @Override
        public Builder addVarg(ValueLayout.OfDouble layout, double value) {
            return arg(double.class, layout, value);
        }

        @Override
        public Builder addVarg(ValueLayout.OfAddress layout, Addressable value) {
            return arg(MemoryAddress.class, layout, value.address());
        }

        @Override
        public Builder addVarg(GroupLayout layout, MemorySegment value) {
            return arg(MemorySegment.class, layout, value);
        }

        private Builder arg(Class<?> carrier, MemoryLayout layout, Object value) {
            Objects.requireNonNull(layout);
            Objects.requireNonNull(value);
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            if (isRegOverflow(currentGPOffset, currentFPOffset, typeClass, layout)) {
                stackArgs.add(new SimpleVaArg(carrier, layout, value));
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

            SegmentAllocator allocator = SegmentAllocator.newNativeArena(scope);
            MemorySegment vaListSegment = allocator.allocate(LAYOUT);
            MemoryAddress stackArgsPtr = MemoryAddress.NULL;
            if (!stackArgs.isEmpty()) {
                long stackArgsSize = stackArgs.stream()
                    .reduce(0L, (acc, e) -> acc + Utils.alignUp(e.layout.byteSize(), 8), Long::sum);
                MemorySegment stackArgsSegment = allocator.allocate(stackArgsSize, 16);
                stackArgsPtr = stackArgsSegment.address();
                for (SimpleVaArg arg : stackArgs) {
                    final long alignedSize = Utils.alignUp(arg.layout.byteSize(), 8);
                    stackArgsSegment = Utils.alignUp(stackArgsSegment, alignedSize);
                    VarHandle writer = arg.varHandle();
                    writer.set(stackArgsSegment, arg.value);
                    stackArgsSegment = stackArgsSegment.asSlice(alignedSize);
                }
            }

            VH_gr_top.set(vaListSegment, gpRegs.asSlice(gpRegs.byteSize()).address());
            VH_vr_top.set(vaListSegment, fpRegs.asSlice(fpRegs.byteSize()).address());
            VH_stack.set(vaListSegment, stackArgsPtr);
            VH_gr_offs.set(vaListSegment, -MAX_GP_OFFSET);
            VH_vr_offs.set(vaListSegment, -MAX_FP_OFFSET);

            assert gpRegs.scope().ownerThread() == vaListSegment.scope().ownerThread();
            assert fpRegs.scope().ownerThread() == vaListSegment.scope().ownerThread();
            return new LinuxAArch64VaList(vaListSegment, gpRegs, fpRegs);
        }
    }
}
