/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.Opcode;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import jdk.internal.misc.Unsafe;
import jdk.internal.util.Preconditions;
import jdk.internal.vm.annotation.Stable;

import static java.lang.classfile.ClassFile.ASTORE_3;
import static java.lang.classfile.ClassFile.ISTORE;
import static java.lang.classfile.ClassFile.LOOKUPSWITCH;
import static java.lang.classfile.ClassFile.TABLESWITCH;
import static java.lang.classfile.ClassFile.WIDE;

public final class RawBytecodeHelper {

    public static final BiFunction<String, List<Number>, IllegalArgumentException>
            IAE_FORMATTER = Preconditions.outOfBoundsExceptionFormatter(new Function<>() {
        @Override
        public IllegalArgumentException apply(String s) {
            return new IllegalArgumentException(s);
        }
    });

    public record CodeRange(byte[] array, int length) {
        public RawBytecodeHelper start() {
            return new RawBytecodeHelper(this);
        }
    }

    public static final int ILLEGAL = -1;

    private static final @Stable byte[] LENGTHS;

    static {
        var lengths = new byte[0x100];
        Arrays.fill(lengths, (byte) -1);
        for (var op : Opcode.values()) {
            if (!op.isWide()) {
                lengths[op.bytecode()] = (byte) op.sizeIfFixed();
            } else {
                // Wide pseudo-opcodes have double the length as normal variants
                // Must match logic in checkSpecialInstruction()
                assert lengths[(byte) op.bytecode()] * 2 == op.sizeIfFixed();
            }
        }
        LENGTHS = lengths;
    }

    public static boolean isStoreIntoLocal(int code) {
        return (ISTORE <= code && code <= ASTORE_3);
    }

    public static int align(int n) {
        return (n + 3) & ~3;
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    public final CodeRange code;
    private int nextBci;
    private int bci;
    private int opcode;
    private boolean isWide;

    public static CodeRange of(byte[] array) {
        return new CodeRange(array, array.length);
    }

    public static CodeRange of(byte[] array, int limit) {
        return new CodeRange(array, limit);
    }

    private RawBytecodeHelper(CodeRange range) {
        this.code = range;
    }

    // immutable states

    public byte[] array() {
        return code.array;
    }

    public int endBci() {
        return code.length;
    }

    // setup

    public void reset(int nextBci) {
        Preconditions.checkIndex(nextBci, endBci() + 1, IAE_FORMATTER);
        this.nextBci = nextBci;
    }

    // getters after transition

    /**
     * Returns the current functional opcode, or {@link #ILLEGAL} if the next instruction is invalid in format.
     * If this returns a valid opcode, that instruction's format must be valid and can be accessed unchecked.
     * Unspecified if called without a {@link #next} transition after object initialization or {@link #reset}.
     */
    public int opcode() {
        return opcode;
    }

    /**
     * Returns whether the current functional opcode is in wide.
     * Unspecified if called without a {@link #next} transition after object initialization or {@link #reset}.
     */
    public boolean isWide() {
        return isWide;
    }

    /**
     * Returns the last validated instruction's index.
     */
    public int bci() {
        return bci;
    }

    /**
     * Returns whether the end of code array is reached.
     */
    public boolean isLastInstruction() {
        return nextBci >= code.length;
    }

    // general utilities

    public int getU1(int bci) {
        Preconditions.checkIndex(bci, endBci(), IAE_FORMATTER);
        return getU1Unchecked(bci);
    }

    public int getU2(int bci) {
        Preconditions.checkFromIndexSize(bci, 2, endBci(), IAE_FORMATTER);
        return getU2Unchecked(bci);
    }

    public short getShort(int bci) {
        Preconditions.checkFromIndexSize(bci, 2, endBci(), IAE_FORMATTER);
        return getShortUnchecked(bci);
    }

    public int getInt(int bci) {
        Preconditions.checkFromIndexSize(bci, 4, endBci(), IAE_FORMATTER);
        return getIntUnchecked(bci);
    }

    // Unchecked accessors: only if opcode() is validated

    public int getU1Unchecked(int bci) {
        return Byte.toUnsignedInt(array()[bci]);
    }

    public int getU2Unchecked(int bci) {
        return UNSAFE.getCharUnaligned(array(), (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + bci, true);
    }

    public short getShortUnchecked(int bci) {
        return UNSAFE.getShortUnaligned(array(), (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + bci, true);
    }

    // used after switch validation
    public int getIntUnchecked(int bci) {
        return UNSAFE.getIntUnaligned(array(), (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + bci, true);
    }

    // non-wide branches
    public int dest() {
        return bci + getShortUnchecked(bci + 1);
    }

    // goto_w and jsr_w
    public int destW() {
        return bci + getIntUnchecked(bci + 1);
    }

    // *load, *store, iinc
    public int getIndex() {
        return isWide ? getU2Unchecked(bci + 2) : getIndexU1();
    }

    // ldc
    public int getIndexU1() {
        return getU1Unchecked(bci + 1);
    }

    // usually cp entry index
    public int getIndexU2() {
        return getU2Unchecked(bci + 1);
    }

    // Transition methods

    /**
     * Transition to the next instruction. If the next instruction is
     * malformed, returns {@link #ILLEGAL}, so we can perform value access
     * without bound checks if we have a valid opcode. Must be guarded by
     * {@link #isLastInstruction()} checks.
     */
    public void next() {
        bci = nextBci;
        int code = getU1Unchecked(bci);
        int len = LENGTHS[code];
        opcode = code;
        isWide = false;
        if (len < 0) {
            len = checkSpecialInstruction(code);
        }

        if (len < 0 || (nextBci += len) > endBci()) {
            opcode = ILLEGAL;
        }
    }

    // pulls out rarely used code blocks to reduce code size
    private int checkSpecialInstruction(int code) {
        if (code == WIDE) {
            if (bci + 1 >= endBci()) {
                return -1;
            }
            opcode = code = getIndexU1();
            isWide = true;
            return LENGTHS[code] * 2; // must match static block assertion
        }
        if (code == TABLESWITCH) {
            int alignedBci = align(bci + 1);
            if (alignedBci + 3 * 4 >= endBci()) {
                return -1;
            }
            int lo = getIntUnchecked(alignedBci + 1 * 4);
            int hi = getIntUnchecked(alignedBci + 2 * 4);
            long l = alignedBci - bci + (3L + (long) hi - lo + 1L) * 4L;
            return l > 0 && ((int) l == l) ? (int) l : -1;
        }
        if (code == LOOKUPSWITCH) {
            int alignedBci = align(bci + 1);
            if (alignedBci + 2 * 4 >= endBci()) {
                return -1;
            }
            int npairs = getIntUnchecked(alignedBci + 4);
            if (npairs < 0) {
                return -1;
            }
            long l = alignedBci - bci + (2L + 2L * npairs) * 4L;
            return l > 0 && ((int) l == l) ? (int) l : -1;
        }
        return -1;
    }
}
