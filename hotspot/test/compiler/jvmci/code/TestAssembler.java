/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package compiler.jvmci.code;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.CompilationResult.DataSectionReference;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Simple assembler used by the code installation tests.
 */
public abstract class TestAssembler {

    /**
     * Emit the method prologue code (e.g. building the new stack frame).
     */
    public abstract void emitPrologue();

    /**
     * Emit code to grow the stack frame.
     * @param size the size in bytes that the stack should grow
     */
    public abstract void emitGrowStack(int size);

    /**
     * Get the register containing the first 32-bit integer argument.
     */
    public abstract Register emitIntArg0();

    /**
     * Get the register containing the second 32-bit integer argument.
     */
    public abstract Register emitIntArg1();

    /**
     * Emit code to add two 32-bit integer registers. May reuse one of the argument registers.
     */
    public abstract Register emitIntAdd(Register a, Register b);

    /**
     * Emit code to load a constant 32-bit integer to a register.
     */
    public abstract Register emitLoadInt(int value);

    /**
     * Emit code to load a constant 64-bit integer to a register.
     */
    public abstract Register emitLoadLong(long value);

    /**
     * Emit code to load a constant single-precision float to a register.
     */
    public abstract Register emitLoadFloat(float value);

    /**
     * Emit code to load a constant oop or metaspace pointer to a register.
     * The pointer may be wide or narrow, depending on {@link HotSpotConstant#isCompressed() c.isCompressed()}.
     */
    public abstract Register emitLoadPointer(HotSpotConstant c);

    /**
     * Emit code to load a wide pointer from the {@link DataSection} to a register.
     */
    public abstract Register emitLoadPointer(DataSectionReference ref);

    /**
     * Emit code to load a narrow pointer from the {@link DataSection} to a register.
     */
    public abstract Register emitLoadNarrowPointer(DataSectionReference ref);

    /**
     * Emit code to load a (wide) pointer from a memory location to a register.
     */
    public abstract Register emitLoadPointer(Register base, int offset);

    /**
     * Emit code to store a 32-bit integer from a register to a new stack slot.
     */
    public abstract StackSlot emitIntToStack(Register a);

    /**
     * Emit code to store a 64-bit integer from a register to a new stack slot.
     */
    public abstract StackSlot emitLongToStack(Register a);

    /**
     * Emit code to store a single-precision float from a register to a new stack slot.
     */
    public abstract StackSlot emitFloatToStack(Register a);

    /**
     * Emit code to store a wide pointer from a register to a new stack slot.
     */
    public abstract StackSlot emitPointerToStack(Register a);

    /**
     * Emit code to store a narrow pointer from a register to a new stack slot.
     */
    public abstract StackSlot emitNarrowPointerToStack(Register a);

    /**
     * Emit code to uncompress a narrow pointer. The input pointer is guaranteed to be non-null.
     */
    public abstract Register emitUncompressPointer(Register compressed, long base, int shift);

    /**
     * Emit code to return from a function, returning a 32-bit integer.
     */
    public abstract void emitIntRet(Register a);

    /**
     * Emit code to return from a function, returning a wide oop pointer.
     */
    public abstract void emitPointerRet(Register a);

    /**
     * Emit code that traps, forcing a deoptimization.
     */
    public abstract void emitTrap(DebugInfo info);

    protected int position() {
        return data.position();
    }

    public final CompilationResult result;
    public final LIRKind narrowOopKind;

    private ByteBuffer data;
    protected final CodeCacheProvider codeCache;

    private final Register[] registers;
    private int nextRegister;

    protected int frameSize;
    private int stackAlignment;
    private int curStackSlot;

    protected TestAssembler(CompilationResult result, CodeCacheProvider codeCache, int initialFrameSize, int stackAlignment, PlatformKind narrowOopKind, Register... registers) {
        this.result = result;
        this.narrowOopKind = LIRKind.reference(narrowOopKind);

        this.data = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder());
        this.codeCache = codeCache;

        this.registers = registers;
        this.nextRegister = 0;

        this.frameSize = initialFrameSize;
        this.stackAlignment = stackAlignment;
        this.curStackSlot = initialFrameSize;
    }

    protected Register newRegister() {
        return registers[nextRegister++];
    }

    protected StackSlot newStackSlot(LIRKind kind) {
        curStackSlot += kind.getPlatformKind().getSizeInBytes();
        if (curStackSlot > frameSize) {
            int newFrameSize = curStackSlot;
            if (newFrameSize % stackAlignment != 0) {
                newFrameSize += stackAlignment - (newFrameSize % stackAlignment);
            }
            emitGrowStack(newFrameSize - frameSize);
            frameSize = newFrameSize;
        }
        return StackSlot.get(kind, -curStackSlot, true);
    }

    public void finish() {
        result.setTotalFrameSize(frameSize);
        result.setTargetCode(data.array(), data.position());
    }

    private void ensureSize(int length) {
        if (length >= data.limit()) {
            byte[] newBuf = Arrays.copyOf(data.array(), length * 4);
            ByteBuffer newData = ByteBuffer.wrap(newBuf);
            newData.order(data.order());
            newData.position(data.position());
            data = newData;
        }
    }

    protected void emitByte(int b) {
        ensureSize(data.position() + 1);
        data.put((byte) (b & 0xFF));
    }

    protected void emitShort(int b) {
        ensureSize(data.position() + 2);
        data.putShort((short) b);
    }

    protected void emitInt(int b) {
        ensureSize(data.position() + 4);
        data.putInt(b);
    }

    protected void emitLong(long b) {
        ensureSize(data.position() + 8);
        data.putLong(b);
    }
}
