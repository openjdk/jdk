/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

public final class RawBytecodeHelper {

    public static final BiFunction<String, List<Number>, IllegalArgumentException>
            IAE_FORMATTER = Preconditions.outOfBoundsExceptionFormatter(new Function<>() {
        @Override
        public IllegalArgumentException apply(String s) {
            return new IllegalArgumentException(s);
        }
    });
    public static final int
            ILLEGAL = -1,
            NOP = 0,
            ACONST_NULL = 1,
            ICONST_M1 = 2,
            ICONST_0 = 3,
            ICONST_1 = 4,
            ICONST_2 = 5,
            ICONST_3 = 6,
            ICONST_4 = 7,
            ICONST_5 = 8,
            LCONST_0 = 9,
            LCONST_1 = 10,
            FCONST_0 = 11,
            FCONST_1 = 12,
            FCONST_2 = 13,
            DCONST_0 = 14,
            DCONST_1 = 15,
            BIPUSH = 16,
            SIPUSH = 17,
            LDC = 18,
            LDC_W = 19,
            LDC2_W = 20,
            ILOAD = 21,
            LLOAD = 22,
            FLOAD = 23,
            DLOAD = 24,
            ALOAD = 25,
            ILOAD_0 = 26,
            ILOAD_1 = 27,
            ILOAD_2 = 28,
            ILOAD_3 = 29,
            LLOAD_0 = 30,
            LLOAD_1 = 31,
            LLOAD_2 = 32,
            LLOAD_3 = 33,
            FLOAD_0 = 34,
            FLOAD_1 = 35,
            FLOAD_2 = 36,
            FLOAD_3 = 37,
            DLOAD_0 = 38,
            DLOAD_1 = 39,
            DLOAD_2 = 40,
            DLOAD_3 = 41,
            ALOAD_0 = 42,
            ALOAD_1 = 43,
            ALOAD_2 = 44,
            ALOAD_3 = 45,
            IALOAD = 46,
            LALOAD = 47,
            FALOAD = 48,
            DALOAD = 49,
            AALOAD = 50,
            BALOAD = 51,
            CALOAD = 52,
            SALOAD = 53,
            ISTORE = 54,
            LSTORE = 55,
            FSTORE = 56,
            DSTORE = 57,
            ASTORE = 58,
            ISTORE_0 = 59,
            ISTORE_1 = 60,
            ISTORE_2 = 61,
            ISTORE_3 = 62,
            LSTORE_0 = 63,
            LSTORE_1 = 64,
            LSTORE_2 = 65,
            LSTORE_3 = 66,
            FSTORE_0 = 67,
            FSTORE_1 = 68,
            FSTORE_2 = 69,
            FSTORE_3 = 70,
            DSTORE_0 = 71,
            DSTORE_1 = 72,
            DSTORE_2 = 73,
            DSTORE_3 = 74,
            ASTORE_0 = 75,
            ASTORE_1 = 76,
            ASTORE_2 = 77,
            ASTORE_3 = 78,
            IASTORE = 79,
            LASTORE = 80,
            FASTORE = 81,
            DASTORE = 82,
            AASTORE = 83,
            BASTORE = 84,
            CASTORE = 85,
            SASTORE = 86,
            POP = 87,
            POP2 = 88,
            DUP = 89,
            DUP_X1 = 90,
            DUP_X2 = 91,
            DUP2 = 92,
            DUP2_X1 = 93,
            DUP2_X2 = 94,
            SWAP = 95,
            IADD = 96,
            LADD = 97,
            FADD = 98,
            DADD = 99,
            ISUB = 100,
            LSUB = 101,
            FSUB = 102,
            DSUB = 103,
            IMUL = 104,
            LMUL = 105,
            FMUL = 106,
            DMUL = 107,
            IDIV = 108,
            LDIV = 109,
            FDIV = 110,
            DDIV = 111,
            IREM = 112,
            LREM = 113,
            FREM = 114,
            DREM = 115,
            INEG = 116,
            LNEG = 117,
            FNEG = 118,
            DNEG = 119,
            ISHL = 120,
            LSHL = 121,
            ISHR = 122,
            LSHR = 123,
            IUSHR = 124,
            LUSHR = 125,
            IAND = 126,
            LAND = 127,
            IOR = 128,
            LOR = 129,
            IXOR = 130,
            LXOR = 131,
            IINC = 132,
            I2L = 133,
            I2F = 134,
            I2D = 135,
            L2I = 136,
            L2F = 137,
            L2D = 138,
            F2I = 139,
            F2L = 140,
            F2D = 141,
            D2I = 142,
            D2L = 143,
            D2F = 144,
            I2B = 145,
            I2C = 146,
            I2S = 147,
            LCMP = 148,
            FCMPL = 149,
            FCMPG = 150,
            DCMPL = 151,
            DCMPG = 152,
            IFEQ = 153,
            IFNE = 154,
            IFLT = 155,
            IFGE = 156,
            IFGT = 157,
            IFLE = 158,
            IF_ICMPEQ = 159,
            IF_ICMPNE = 160,
            IF_ICMPLT = 161,
            IF_ICMPGE = 162,
            IF_ICMPGT = 163,
            IF_ICMPLE = 164,
            IF_ACMPEQ = 165,
            IF_ACMPNE = 166,
            GOTO = 167,
            JSR = 168,
            RET = 169,
            TABLESWITCH = 170,
            LOOKUPSWITCH = 171,
            IRETURN = 172,
            LRETURN = 173,
            FRETURN = 174,
            DRETURN = 175,
            ARETURN = 176,
            RETURN = 177,
            GETSTATIC = 178,
            PUTSTATIC = 179,
            GETFIELD = 180,
            PUTFIELD = 181,
            INVOKEVIRTUAL = 182,
            INVOKESPECIAL = 183,
            INVOKESTATIC = 184,
            INVOKEINTERFACE = 185,
            INVOKEDYNAMIC = 186,
            NEW = 187,
            NEWARRAY = 188,
            ANEWARRAY = 189,
            ARRAYLENGTH = 190,
            ATHROW = 191,
            CHECKCAST = 192,
            INSTANCEOF = 193,
            MONITORENTER = 194,
            MONITOREXIT = 195,
            WIDE = 196,
            MULTIANEWARRAY = 197,
            IFNULL = 198,
            IFNONNULL = 199,
            GOTO_W = 200,
            JSR_W = 201;

    public record CodeRange(byte[] array, int length) {
        public RawBytecodeHelper start() {
            return new RawBytecodeHelper(this);
        }
    }

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
        return opcode & 0xFF;
    }

    /**
     * Returns whether the current functional opcode is in wide.
     */
    public boolean isWide() {
        return (opcode & (WIDE << 8)) != 0;
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
        return UNSAFE.getCharUnaligned(code.array, Unsafe.ARRAY_BYTE_BASE_OFFSET + bci, true);
    }

    public int getShortUnchecked(int bci) {
        return UNSAFE.getShortUnaligned(code.array, Unsafe.ARRAY_BYTE_BASE_OFFSET + bci, true);
    }

    // used after switch validation
    public int getIntUnchecked(int bci) {
        return UNSAFE.getIntUnaligned(code.array, Unsafe.ARRAY_BYTE_BASE_OFFSET + bci, true);
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
        return isWide() ? getU2Unchecked(bci + 2) : getIndexU1();
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
        if (len <= 0) {
            len = checkSpecialInstruction(bci, end, code); // sets opcode
        }

        if ((nextBci += len) > end) {
            opcode = ILLEGAL;
        }

        return true;
    }

    // Put rarely used code in another method to reduce code size
    private int checkSpecialInstruction(int bci, int end, int code) {
        int len = -1;
        if (code == WIDE) {
            if (bci + 1 < end) {
                opcode = (WIDE << 8) | (code = getIndexU1());
                // Validated in UtilTest.testOpcodeLengthTable
                len = LENGTHS[code] * 2;
            }
        } else if (code == TABLESWITCH) {
            int alignedBci = align(bci + 1);
            if (alignedBci + 3 * 4 < end) {
                int lo = getIntUnchecked(alignedBci + 1 * 4);
                int hi = getIntUnchecked(alignedBci + 2 * 4);
                long l = alignedBci - bci + (3L + (long) hi - lo + 1L) * 4L;
                len = l > 0 && ((int) l == l) ? (int) l : -1;
            }
        } else if (code == LOOKUPSWITCH) {
            int alignedBci = align(bci + 1);
            if (alignedBci + 2 * 4 < end) {
                int npairs = getIntUnchecked(alignedBci + 4);
                if (npairs >= 0) {
                    long l = alignedBci - bci + (2L + 2L * npairs) * 4L;
                    len = l > 0 && ((int) l == l) ? (int) l : -1;
                }
            }
        }
        if (len <= 0) {
            opcode = ILLEGAL;
        }
        return len;
    }
}
