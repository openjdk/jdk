/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import jdk.internal.misc.Unsafe;
import jdk.internal.util.Preconditions;
import jdk.internal.vm.annotation.Stable;

import static java.lang.classfile.ClassFile.*;

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

    /**
     * The length of opcodes, or -1 for no fixed length.
     * This is generated as if:
     * {@snippet lang=java :
     * var lengths = new byte[0x100];
     * Arrays.fill(lengths, (byte) -1);
     * for (var op : Opcode.values()) {
     *     if (!op.isWide()) {
     *         lengths[op.bytecode()] = (byte) op.sizeIfFixed();
     *     }
     * }
     * }
     * Tested in UtilTest::testOpcodeLengthTable.
     */
    // Note: Consider distinguishing non-opcode and non-fixed-length opcode
    public static final @Stable byte[] LENGTHS = new byte[] {
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            2, 3, 2, 3, 3, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 2, -1, -1, 1, 1, 1, 1,
            1, 1, 3, 3, 3, 3, 3, 3, 3, 5, 5, 3, 2, 3, 1, 1,
            3, 3, 1, 1, -1, 4, 3, 3, 5, 5, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    };

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

    /** {@return the end of the code array} */
    public int endBci() {
        return code.length;
    }

    // setup

    /**
     * Sets the starting bci for bytecode reading. Can be set to
     * {@link #endBci} to end scanning. Must be followed by a
     * {@link #next} before getter access.
     */
    public void reset(int nextBci) {
        Preconditions.checkIndex(nextBci, endBci() + 1, IAE_FORMATTER);
        this.nextBci = nextBci;
    }

    // getters after transition

    /**
     * Returns the current functional opcode, or {@link #ILLEGAL} if
     * the next instruction is invalid in format.
     * If this returns a valid opcode, that instruction's format must
     * be valid and can be accessed unchecked.
     */
    public int opcode() {
        return opcode;
    }

    /**
     * Returns whether the current functional opcode is in wide.
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

    // general utilities

    public int getU1(int bci) {
        Preconditions.checkIndex(bci, endBci(), IAE_FORMATTER);
        return getU1Unchecked(bci);
    }

    public int getU2(int bci) {
        Preconditions.checkFromIndexSize(bci, 2, endBci(), IAE_FORMATTER);
        return getU2Unchecked(bci);
    }

    public int getShort(int bci) {
        Preconditions.checkFromIndexSize(bci, 2, endBci(), IAE_FORMATTER);
        return getShortUnchecked(bci);
    }

    public int getInt(int bci) {
        Preconditions.checkFromIndexSize(bci, 4, endBci(), IAE_FORMATTER);
        return getIntUnchecked(bci);
    }

    // Unchecked accessors: only if opcode() is validated

    public int getU1Unchecked(int bci) {
        return Byte.toUnsignedInt(code.array[bci]);
    }

    public int getU2Unchecked(int bci) {
        return UNSAFE.getCharUnaligned(code.array, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + bci, true);
    }

    public int getShortUnchecked(int bci) {
        return UNSAFE.getShortUnaligned(code.array, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + bci, true);
    }

    // used after switch validation
    public int getIntUnchecked(int bci) {
        return UNSAFE.getIntUnaligned(code.array, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + bci, true);
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
     * Transitions to the next instruction and returns whether scanning should
     * continue. If the next instruction is malformed, {@link #opcode()} returns
     * {@link #ILLEGAL}, so we can perform value access without bound checks if
     * we have a valid opcode.
     */
    public boolean next() {
        var bci = nextBci;
        var end = endBci();
        if (bci >= end) {
            return false;
        }

        int code = getU1Unchecked(bci);
        int len = LENGTHS[code & 0xFF]; // & 0xFF eliminates bound check
        this.bci = bci;
        opcode = code;
        isWide = false;
        if (len <= 0) {
            len = checkSpecialInstruction(bci, end, code); // sets opcode
        }

        if (len <= 0 || (nextBci += len) > end) {
            opcode = ILLEGAL;
        }

        return true;
    }

    // Put rarely used code in another method to reduce code size
    private int checkSpecialInstruction(int bci, int end, int code) {
        if (code == WIDE) {
            if (bci + 1 >= end) {
                return -1;
            }
            opcode = code = getIndexU1();
            isWide = true;
            // Validated in UtilTest.testOpcodeLengthTable
            return LENGTHS[code] * 2;
        }
        if (code == TABLESWITCH) {
            int alignedBci = align(bci + 1);
            if (alignedBci + 3 * 4 >= end) {
                return -1;
            }
            int lo = getIntUnchecked(alignedBci + 1 * 4);
            int hi = getIntUnchecked(alignedBci + 2 * 4);
            long l = alignedBci - bci + (3L + (long) hi - lo + 1L) * 4L;
            return l > 0 && ((int) l == l) ? (int) l : -1;
        }
        if (code == LOOKUPSWITCH) {
            int alignedBci = align(bci + 1);
            if (alignedBci + 2 * 4 >= end) {
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
