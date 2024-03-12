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
package jdk.internal.classfile.impl.verifier;

import java.nio.ByteBuffer;

import java.lang.classfile.ClassFile;
import jdk.internal.classfile.impl.verifier.VerificationSignature.BasicType;
import static jdk.internal.classfile.impl.verifier.VerificationSignature.BasicType.*;

/**
 * @see <a href="https://raw.githubusercontent.com/openjdk/jdk/master/src/hotspot/share/interpreter/bytecodes.hpp">hotspot/share/interpreter/bytecodes.hpp</a>
 * @see <a href="https://raw.githubusercontent.com/openjdk/jdk/master/src/hotspot/share/interpreter/bytecodes.cpp">hotspot/share/interpreter/bytecodes.cpp</a>
 */
final class VerificationBytecodes {

    static final int _breakpoint = 202,
            _fast_agetfield = 203,
            _fast_bgetfield = 204,
            _fast_cgetfield = 205,
            _fast_dgetfield = 206,
            _fast_fgetfield = 207,
            _fast_igetfield = 208,
            _fast_lgetfield = 209,
            _fast_sgetfield = 210,
            _fast_aputfield = 211,
            _fast_bputfield = 212,
            _fast_zputfield = 213,
            _fast_cputfield = 214,
            _fast_dputfield = 215,
            _fast_fputfield = 216,
            _fast_iputfield = 217,
            _fast_lputfield = 218,
            _fast_sputfield = 219,
            _fast_aload_0 = 220,
            _fast_iaccess_0 = 221,
            _fast_aaccess_0 = 222,
            _fast_faccess_0 = 223,
            _fast_iload = 224,
            _fast_iload2 = 225,
            _fast_icaload = 226,
            _fast_invokevfinal = 227,
            _fast_linearswitch = 228,
            _fast_binaryswitch = 229,
            _fast_aldc = 230,
            _fast_aldc_w = 231,
            _return_register_finalizer = 232,
            _invokehandle = 233,
            _nofast_getfield = 234,
            _nofast_putfield = 235,
            _nofast_aload_0 = 236,
            _nofast_iload = 237,
            _shouldnotreachhere = 238,
            number_of_codes = 239;

    static int code_or_bp_at(byte[] code, int bci) {
        return code[bci] & 0xff;
    }

    static boolean is_valid(int code) {
        return 0 <= code && code < number_of_codes;
    }

    static int wide_length_for(int code) {
        return is_valid(code) ? _lengths[code] >> 4 : -1;
    }

    static boolean is_store_into_local(int code) {
        return (ClassFile.ISTORE <= code && code <= ClassFile.ASTORE_3);
    }

    static final int _lengths[] = new int[number_of_codes];

    static int special_length_at(int code, byte bytecode[], int bci, int end) {
        switch (code) {
            case ClassFile.WIDE:
                if (bci + 1 >= end) {
                    return -1;
                }
                return wide_length_for(bytecode[bci + 1] & 0xff);
            case ClassFile.TABLESWITCH:
                int aligned_bci = align(bci + 1);
                if (aligned_bci + 3 * 4 >= end) {
                    return -1;
                }
                ByteBuffer bb = ByteBuffer.wrap(bytecode, aligned_bci + 1 * 4, 2 * 4);
                int lo = bb.getInt();
                int hi = bb.getInt();
                int len = aligned_bci - bci + (3 + hi - lo + 1) * 4;
                return len > 0 ? len : -1;
            case ClassFile.LOOKUPSWITCH:
            case _fast_binaryswitch:
            case _fast_linearswitch:
                aligned_bci = align(bci + 1);
                if (aligned_bci + 2 * 4 >= end) {
                    return -1;
                }
                int npairs = ByteBuffer.wrap(bytecode, aligned_bci + 4, 4).getInt();
                len = aligned_bci - bci + (2 + 2 * npairs) * 4;
                return len > 0 ? len : -1;
            default:
                return 0;
        }
    }

    static int align(int n) {
        return (n + 3) & ~3;
    }

    static void def(int code, String name, String format, String wide_format, BasicType result_type, int depth) {
        def(code, name, format, wide_format, result_type, depth, code);
    }

    static void def(int code, String name, String format, String wide_format, BasicType result_type, int depth, int java_code) {
        if (wide_format != null && format == null) throw new IllegalArgumentException("short form must exist if there's a wide form");
        int len = format != null ? format.length() : 0;
        int wlen = wide_format != null ? wide_format.length() : 0;
        _lengths[code] = (wlen << 4) | (len & 0xf);
    }

    static {
        def(ClassFile.NOP, "nop", "b", null, T_VOID, 0);
        def(ClassFile.ACONST_NULL, "aconst_null", "b", null, T_OBJECT, 1);
        def(ClassFile.ICONST_M1, "iconst_m1", "b", null, T_INT, 1);
        def(ClassFile.ICONST_0, "iconst_0", "b", null, T_INT, 1);
        def(ClassFile.ICONST_1, "iconst_1", "b", null, T_INT, 1);
        def(ClassFile.ICONST_2, "iconst_2", "b", null, T_INT, 1);
        def(ClassFile.ICONST_3, "iconst_3", "b", null, T_INT, 1);
        def(ClassFile.ICONST_4, "iconst_4", "b", null, T_INT, 1);
        def(ClassFile.ICONST_5, "iconst_5", "b", null, T_INT, 1);
        def(ClassFile.LCONST_0, "lconst_0", "b", null, T_LONG, 2);
        def(ClassFile.LCONST_1, "lconst_1", "b", null, T_LONG, 2);
        def(ClassFile.FCONST_0, "fconst_0", "b", null, T_FLOAT, 1);
        def(ClassFile.FCONST_1, "fconst_1", "b", null, T_FLOAT, 1);
        def(ClassFile.FCONST_2, "fconst_2", "b", null, T_FLOAT, 1);
        def(ClassFile.DCONST_0, "dconst_0", "b", null, T_DOUBLE, 2);
        def(ClassFile.DCONST_1, "dconst_1", "b", null, T_DOUBLE, 2);
        def(ClassFile.BIPUSH, "bipush", "bc", null, T_INT, 1);
        def(ClassFile.SIPUSH, "sipush", "bcc", null, T_INT, 1);
        def(ClassFile.LDC, "ldc", "bk", null, T_ILLEGAL, 1);
        def(ClassFile.LDC_W, "ldc_w", "bkk", null, T_ILLEGAL, 1);
        def(ClassFile.LDC2_W, "ldc2_w", "bkk", null, T_ILLEGAL, 2);
        def(ClassFile.ILOAD, "iload", "bi", "wbii", T_INT, 1);
        def(ClassFile.LLOAD, "lload", "bi", "wbii", T_LONG, 2);
        def(ClassFile.FLOAD, "fload", "bi", "wbii", T_FLOAT, 1);
        def(ClassFile.DLOAD, "dload", "bi", "wbii", T_DOUBLE, 2);
        def(ClassFile.ALOAD, "aload", "bi", "wbii", T_OBJECT, 1);
        def(ClassFile.ILOAD_0, "iload_0", "b", null, T_INT, 1);
        def(ClassFile.ILOAD_1, "iload_1", "b", null, T_INT, 1);
        def(ClassFile.ILOAD_2, "iload_2", "b", null, T_INT, 1);
        def(ClassFile.ILOAD_3, "iload_3", "b", null, T_INT, 1);
        def(ClassFile.LLOAD_0, "lload_0", "b", null, T_LONG, 2);
        def(ClassFile.LLOAD_1, "lload_1", "b", null, T_LONG, 2);
        def(ClassFile.LLOAD_2, "lload_2", "b", null, T_LONG, 2);
        def(ClassFile.LLOAD_3, "lload_3", "b", null, T_LONG, 2);
        def(ClassFile.FLOAD_0, "fload_0", "b", null, T_FLOAT, 1);
        def(ClassFile.FLOAD_1, "fload_1", "b", null, T_FLOAT, 1);
        def(ClassFile.FLOAD_2, "fload_2", "b", null, T_FLOAT, 1);
        def(ClassFile.FLOAD_3, "fload_3", "b", null, T_FLOAT, 1);
        def(ClassFile.DLOAD_0, "dload_0", "b", null, T_DOUBLE, 2);
        def(ClassFile.DLOAD_1, "dload_1", "b", null, T_DOUBLE, 2);
        def(ClassFile.DLOAD_2, "dload_2", "b", null, T_DOUBLE, 2);
        def(ClassFile.DLOAD_3, "dload_3", "b", null, T_DOUBLE, 2);
        def(ClassFile.ALOAD_0, "aload_0", "b", null, T_OBJECT, 1);
        def(ClassFile.ALOAD_1, "aload_1", "b", null, T_OBJECT, 1);
        def(ClassFile.ALOAD_2, "aload_2", "b", null, T_OBJECT, 1);
        def(ClassFile.ALOAD_3, "aload_3", "b", null, T_OBJECT, 1);
        def(ClassFile.IALOAD, "iaload", "b", null, T_INT, -1);
        def(ClassFile.LALOAD, "laload", "b", null, T_LONG, 0);
        def(ClassFile.FALOAD, "faload", "b", null, T_FLOAT, -1);
        def(ClassFile.DALOAD, "daload", "b", null, T_DOUBLE, 0);
        def(ClassFile.AALOAD, "aaload", "b", null, T_OBJECT, -1);
        def(ClassFile.BALOAD, "baload", "b", null, T_INT, -1);
        def(ClassFile.CALOAD, "caload", "b", null, T_INT, -1);
        def(ClassFile.SALOAD, "saload", "b", null, T_INT, -1);
        def(ClassFile.ISTORE, "istore", "bi", "wbii", T_VOID, -1);
        def(ClassFile.LSTORE, "lstore", "bi", "wbii", T_VOID, -2);
        def(ClassFile.FSTORE, "fstore", "bi", "wbii", T_VOID, -1);
        def(ClassFile.DSTORE, "dstore", "bi", "wbii", T_VOID, -2);
        def(ClassFile.ASTORE, "astore", "bi", "wbii", T_VOID, -1);
        def(ClassFile.ISTORE_0, "istore_0", "b", null, T_VOID, -1);
        def(ClassFile.ISTORE_1, "istore_1", "b", null, T_VOID, -1);
        def(ClassFile.ISTORE_2, "istore_2", "b", null, T_VOID, -1);
        def(ClassFile.ISTORE_3, "istore_3", "b", null, T_VOID, -1);
        def(ClassFile.LSTORE_0, "lstore_0", "b", null, T_VOID, -2);
        def(ClassFile.LSTORE_1, "lstore_1", "b", null, T_VOID, -2);
        def(ClassFile.LSTORE_2, "lstore_2", "b", null, T_VOID, -2);
        def(ClassFile.LSTORE_3, "lstore_3", "b", null, T_VOID, -2);
        def(ClassFile.FSTORE_0, "fstore_0", "b", null, T_VOID, -1);
        def(ClassFile.FSTORE_1, "fstore_1", "b", null, T_VOID, -1);
        def(ClassFile.FSTORE_2, "fstore_2", "b", null, T_VOID, -1);
        def(ClassFile.FSTORE_3, "fstore_3", "b", null, T_VOID, -1);
        def(ClassFile.DSTORE_0, "dstore_0", "b", null, T_VOID, -2);
        def(ClassFile.DSTORE_1, "dstore_1", "b", null, T_VOID, -2);
        def(ClassFile.DSTORE_2, "dstore_2", "b", null, T_VOID, -2);
        def(ClassFile.DSTORE_3, "dstore_3", "b", null, T_VOID, -2);
        def(ClassFile.ASTORE_0, "astore_0", "b", null, T_VOID, -1);
        def(ClassFile.ASTORE_1, "astore_1", "b", null, T_VOID, -1);
        def(ClassFile.ASTORE_2, "astore_2", "b", null, T_VOID, -1);
        def(ClassFile.ASTORE_3, "astore_3", "b", null, T_VOID, -1);
        def(ClassFile.IASTORE, "iastore", "b", null, T_VOID, -3);
        def(ClassFile.LASTORE, "lastore", "b", null, T_VOID, -4);
        def(ClassFile.FASTORE, "fastore", "b", null, T_VOID, -3);
        def(ClassFile.DASTORE, "dastore", "b", null, T_VOID, -4);
        def(ClassFile.AASTORE, "aastore", "b", null, T_VOID, -3);
        def(ClassFile.BASTORE, "bastore", "b", null, T_VOID, -3);
        def(ClassFile.CASTORE, "castore", "b", null, T_VOID, -3);
        def(ClassFile.SASTORE, "sastore", "b", null, T_VOID, -3);
        def(ClassFile.POP, "pop", "b", null, T_VOID, -1);
        def(ClassFile.POP2, "pop2", "b", null, T_VOID, -2);
        def(ClassFile.DUP, "dup", "b", null, T_VOID, 1);
        def(ClassFile.DUP_X1, "dup_x1", "b", null, T_VOID, 1);
        def(ClassFile.DUP_X2, "dup_x2", "b", null, T_VOID, 1);
        def(ClassFile.DUP2, "dup2", "b", null, T_VOID, 2);
        def(ClassFile.DUP2_X1, "dup2_x1", "b", null, T_VOID, 2);
        def(ClassFile.DUP2_X2, "dup2_x2", "b", null, T_VOID, 2);
        def(ClassFile.SWAP, "swap", "b", null, T_VOID, 0);
        def(ClassFile.IADD, "iadd", "b", null, T_INT, -1);
        def(ClassFile.LADD, "ladd", "b", null, T_LONG, -2);
        def(ClassFile.FADD, "fadd", "b", null, T_FLOAT, -1);
        def(ClassFile.DADD, "dadd", "b", null, T_DOUBLE, -2);
        def(ClassFile.ISUB, "isub", "b", null, T_INT, -1);
        def(ClassFile.LSUB, "lsub", "b", null, T_LONG, -2);
        def(ClassFile.FSUB, "fsub", "b", null, T_FLOAT, -1);
        def(ClassFile.DSUB, "dsub", "b", null, T_DOUBLE, -2);
        def(ClassFile.IMUL, "imul", "b", null, T_INT, -1);
        def(ClassFile.LMUL, "lmul", "b", null, T_LONG, -2);
        def(ClassFile.FMUL, "fmul", "b", null, T_FLOAT, -1);
        def(ClassFile.DMUL, "dmul", "b", null, T_DOUBLE, -2);
        def(ClassFile.IDIV, "idiv", "b", null, T_INT, -1);
        def(ClassFile.LDIV, "ldiv", "b", null, T_LONG, -2);
        def(ClassFile.FDIV, "fdiv", "b", null, T_FLOAT, -1);
        def(ClassFile.DDIV, "ddiv", "b", null, T_DOUBLE, -2);
        def(ClassFile.IREM, "irem", "b", null, T_INT, -1);
        def(ClassFile.LREM, "lrem", "b", null, T_LONG, -2);
        def(ClassFile.FREM, "frem", "b", null, T_FLOAT, -1);
        def(ClassFile.DREM, "drem", "b", null, T_DOUBLE, -2);
        def(ClassFile.INEG, "ineg", "b", null, T_INT, 0);
        def(ClassFile.LNEG, "lneg", "b", null, T_LONG, 0);
        def(ClassFile.FNEG, "fneg", "b", null, T_FLOAT, 0);
        def(ClassFile.DNEG, "dneg", "b", null, T_DOUBLE, 0);
        def(ClassFile.ISHL, "ishl", "b", null, T_INT, -1);
        def(ClassFile.LSHL, "lshl", "b", null, T_LONG, -1);
        def(ClassFile.ISHR, "ishr", "b", null, T_INT, -1);
        def(ClassFile.LSHR, "lshr", "b", null, T_LONG, -1);
        def(ClassFile.IUSHR, "iushr", "b", null, T_INT, -1);
        def(ClassFile.LUSHR, "lushr", "b", null, T_LONG, -1);
        def(ClassFile.IAND, "iand", "b", null, T_INT, -1);
        def(ClassFile.LAND, "land", "b", null, T_LONG, -2);
        def(ClassFile.IOR, "ior", "b", null, T_INT, -1);
        def(ClassFile.LOR, "lor", "b", null, T_LONG, -2);
        def(ClassFile.IXOR, "ixor", "b", null, T_INT, -1);
        def(ClassFile.LXOR, "lxor", "b", null, T_LONG, -2);
        def(ClassFile.IINC, "iinc", "bic", "wbiicc", T_VOID, 0);
        def(ClassFile.I2L, "i2l", "b", null, T_LONG, 1);
        def(ClassFile.I2F, "i2f", "b", null, T_FLOAT, 0);
        def(ClassFile.I2D, "i2d", "b", null, T_DOUBLE, 1);
        def(ClassFile.L2I, "l2i", "b", null, T_INT, -1);
        def(ClassFile.L2F, "l2f", "b", null, T_FLOAT, -1);
        def(ClassFile.L2D, "l2d", "b", null, T_DOUBLE, 0);
        def(ClassFile.F2I, "f2i", "b", null, T_INT, 0);
        def(ClassFile.F2L, "f2l", "b", null, T_LONG, 1);
        def(ClassFile.F2D, "f2d", "b", null, T_DOUBLE, 1);
        def(ClassFile.D2I, "d2i", "b", null, T_INT, -1);
        def(ClassFile.D2L, "d2l", "b", null, T_LONG, 0);
        def(ClassFile.D2F, "d2f", "b", null, T_FLOAT, -1);
        def(ClassFile.I2B, "i2b", "b", null, T_BYTE, 0);
        def(ClassFile.I2C, "i2c", "b", null, T_CHAR, 0);
        def(ClassFile.I2S, "i2s", "b", null, T_SHORT, 0);
        def(ClassFile.LCMP, "lcmp", "b", null, T_VOID, -3);
        def(ClassFile.FCMPL, "fcmpl", "b", null, T_VOID, -1);
        def(ClassFile.FCMPG, "fcmpg", "b", null, T_VOID, -1);
        def(ClassFile.DCMPL, "dcmpl", "b", null, T_VOID, -3);
        def(ClassFile.DCMPG, "dcmpg", "b", null, T_VOID, -3);
        def(ClassFile.IFEQ, "ifeq", "boo", null, T_VOID, -1);
        def(ClassFile.IFNE, "ifne", "boo", null, T_VOID, -1);
        def(ClassFile.IFLT, "iflt", "boo", null, T_VOID, -1);
        def(ClassFile.IFGE, "ifge", "boo", null, T_VOID, -1);
        def(ClassFile.IFGT, "ifgt", "boo", null, T_VOID, -1);
        def(ClassFile.IFLE, "ifle", "boo", null, T_VOID, -1);
        def(ClassFile.IF_ICMPEQ, "if_icmpeq", "boo", null, T_VOID, -2);
        def(ClassFile.IF_ICMPNE, "if_icmpne", "boo", null, T_VOID, -2);
        def(ClassFile.IF_ICMPLT, "if_icmplt", "boo", null, T_VOID, -2);
        def(ClassFile.IF_ICMPGE, "if_icmpge", "boo", null, T_VOID, -2);
        def(ClassFile.IF_ICMPGT, "if_icmpgt", "boo", null, T_VOID, -2);
        def(ClassFile.IF_ICMPLE, "if_icmple", "boo", null, T_VOID, -2);
        def(ClassFile.IF_ACMPEQ, "if_acmpeq", "boo", null, T_VOID, -2);
        def(ClassFile.IF_ACMPNE, "if_acmpne", "boo", null, T_VOID, -2);
        def(ClassFile.GOTO, "goto", "boo", null, T_VOID, 0);
        def(ClassFile.JSR, "jsr", "boo", null, T_INT, 0);
        def(ClassFile.RET, "ret", "bi", "wbii", T_VOID, 0);
        def(ClassFile.TABLESWITCH, "tableswitch", "", null, T_VOID, -1); // may have backward branches
        def(ClassFile.LOOKUPSWITCH, "lookupswitch", "", null, T_VOID, -1); // rewriting in interpreter
        def(ClassFile.IRETURN, "ireturn", "b", null, T_INT, -1);
        def(ClassFile.LRETURN, "lreturn", "b", null, T_LONG, -2);
        def(ClassFile.FRETURN, "freturn", "b", null, T_FLOAT, -1);
        def(ClassFile.DRETURN, "dreturn", "b", null, T_DOUBLE, -2);
        def(ClassFile.ARETURN, "areturn", "b", null, T_OBJECT, -1);
        def(ClassFile.RETURN, "return", "b", null, T_VOID, 0);
        def(ClassFile.GETSTATIC, "getstatic", "bJJ", null, T_ILLEGAL, 1);
        def(ClassFile.PUTSTATIC, "putstatic", "bJJ", null, T_ILLEGAL, -1);
        def(ClassFile.GETFIELD, "getfield", "bJJ", null, T_ILLEGAL, 0);
        def(ClassFile.PUTFIELD, "putfield", "bJJ", null, T_ILLEGAL, -2);
        def(ClassFile.INVOKEVIRTUAL, "invokevirtual", "bJJ", null, T_ILLEGAL, -1);
        def(ClassFile.INVOKESPECIAL, "invokespecial", "bJJ", null, T_ILLEGAL, -1);
        def(ClassFile.INVOKESTATIC, "invokestatic", "bJJ", null, T_ILLEGAL, 0);
        def(ClassFile.INVOKEINTERFACE, "invokeinterface", "bJJ__", null, T_ILLEGAL, -1);
        def(ClassFile.INVOKEDYNAMIC, "invokedynamic", "bJJJJ", null, T_ILLEGAL, 0);
        def(ClassFile.NEW, "new", "bkk", null, T_OBJECT, 1);
        def(ClassFile.NEWARRAY, "newarray", "bc", null, T_OBJECT, 0);
        def(ClassFile.ANEWARRAY, "anewarray", "bkk", null, T_OBJECT, 0);
        def(ClassFile.ARRAYLENGTH, "arraylength", "b", null, T_VOID, 0);
        def(ClassFile.ATHROW, "athrow", "b", null, T_VOID, -1);
        def(ClassFile.CHECKCAST, "checkcast", "bkk", null, T_OBJECT, 0);
        def(ClassFile.INSTANCEOF, "instanceof", "bkk", null, T_INT, 0);
        def(ClassFile.MONITORENTER, "monitorenter", "b", null, T_VOID, -1);
        def(ClassFile.MONITOREXIT, "monitorexit", "b", null, T_VOID, -1);
        def(ClassFile.WIDE, "wide", "", null, T_VOID, 0);
        def(ClassFile.MULTIANEWARRAY, "multianewarray", "bkkc", null, T_OBJECT, 1);
        def(ClassFile.IFNULL, "ifnull", "boo", null, T_VOID, -1);
        def(ClassFile.IFNONNULL, "ifnonnull", "boo", null, T_VOID, -1);
        def(ClassFile.GOTO_W, "goto_w", "boooo", null, T_VOID, 0);
        def(ClassFile.JSR_W, "jsr_w", "boooo", null, T_INT, 0);
        def(_breakpoint, "breakpoint", "", null, T_VOID, 0);
        def(_fast_agetfield, "fast_agetfield", "bJJ", null, T_OBJECT, 0, ClassFile.GETFIELD);
        def(_fast_bgetfield, "fast_bgetfield", "bJJ", null, T_INT, 0, ClassFile.GETFIELD);
        def(_fast_cgetfield, "fast_cgetfield", "bJJ", null, T_CHAR, 0, ClassFile.GETFIELD);
        def(_fast_dgetfield, "fast_dgetfield", "bJJ", null, T_DOUBLE, 0, ClassFile.GETFIELD);
        def(_fast_fgetfield, "fast_fgetfield", "bJJ", null, T_FLOAT, 0, ClassFile.GETFIELD);
        def(_fast_igetfield, "fast_igetfield", "bJJ", null, T_INT, 0, ClassFile.GETFIELD);
        def(_fast_lgetfield, "fast_lgetfield", "bJJ", null, T_LONG, 0, ClassFile.GETFIELD);
        def(_fast_sgetfield, "fast_sgetfield", "bJJ", null, T_SHORT, 0, ClassFile.GETFIELD);
        def(_fast_aputfield, "fast_aputfield", "bJJ", null, T_OBJECT, 0, ClassFile.PUTFIELD);
        def(_fast_bputfield, "fast_bputfield", "bJJ", null, T_INT, 0, ClassFile.PUTFIELD);
        def(_fast_zputfield, "fast_zputfield", "bJJ", null, T_INT, 0, ClassFile.PUTFIELD);
        def(_fast_cputfield, "fast_cputfield", "bJJ", null, T_CHAR, 0, ClassFile.PUTFIELD);
        def(_fast_dputfield, "fast_dputfield", "bJJ", null, T_DOUBLE, 0, ClassFile.PUTFIELD);
        def(_fast_fputfield, "fast_fputfield", "bJJ", null, T_FLOAT, 0, ClassFile.PUTFIELD);
        def(_fast_iputfield, "fast_iputfield", "bJJ", null, T_INT, 0, ClassFile.PUTFIELD);
        def(_fast_lputfield, "fast_lputfield", "bJJ", null, T_LONG, 0, ClassFile.PUTFIELD);
        def(_fast_sputfield, "fast_sputfield", "bJJ", null, T_SHORT, 0, ClassFile.PUTFIELD);
        def(_fast_aload_0, "fast_aload_0", "b", null, T_OBJECT, 1, ClassFile.ALOAD_0);
        def(_fast_iaccess_0, "fast_iaccess_0", "b_JJ", null, T_INT, 1, ClassFile.ALOAD_0);
        def(_fast_aaccess_0, "fast_aaccess_0", "b_JJ", null, T_OBJECT, 1, ClassFile.ALOAD_0);
        def(_fast_faccess_0, "fast_faccess_0", "b_JJ", null, T_OBJECT, 1, ClassFile.ALOAD_0);
        def(_fast_iload, "fast_iload", "bi", null, T_INT, 1, ClassFile.ILOAD);
        def(_fast_iload2, "fast_iload2", "bi_i", null, T_INT, 2, ClassFile.ILOAD);
        def(_fast_icaload, "fast_icaload", "bi_", null, T_INT, 0, ClassFile.ILOAD);
        def(_fast_invokevfinal, "fast_invokevfinal", "bJJ", null, T_ILLEGAL, -1, ClassFile.INVOKEVIRTUAL);
        def(_fast_linearswitch, "fast_linearswitch", "", null, T_VOID, -1, ClassFile.LOOKUPSWITCH);
        def(_fast_binaryswitch, "fast_binaryswitch", "", null, T_VOID, -1, ClassFile.LOOKUPSWITCH);
        def(_return_register_finalizer, "return_register_finalizer", "b", null, T_VOID, 0, ClassFile.RETURN);
        def(_invokehandle, "invokehandle", "bJJ", null, T_ILLEGAL, -1, ClassFile.INVOKEVIRTUAL);
        def(_fast_aldc, "fast_aldc", "bj", null, T_OBJECT, 1, ClassFile.LDC);
        def(_fast_aldc_w, "fast_aldc_w", "bJJ", null, T_OBJECT, 1, ClassFile.LDC_W);
        def(_nofast_getfield, "nofast_getfield", "bJJ", null, T_ILLEGAL, 0, ClassFile.GETFIELD);
        def(_nofast_putfield, "nofast PUTFIELD", "bJJ", null, T_ILLEGAL, -2, ClassFile.PUTFIELD);
        def(_nofast_aload_0, "nofast_aload_0", "b", null, T_ILLEGAL, 1, ClassFile.ALOAD_0);
        def(_nofast_iload, "nofast_iload", "bi", null, T_ILLEGAL, 1, ClassFile.ILOAD);
        def(_shouldnotreachhere, "_shouldnotreachhere", "b", null, T_VOID, 0);
    }
}
