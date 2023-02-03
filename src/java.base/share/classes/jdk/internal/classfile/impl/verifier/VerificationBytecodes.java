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

import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.impl.verifier.VerificationSignature.BasicType;
import static jdk.internal.classfile.impl.verifier.VerificationSignature.BasicType.*;

/**
 * @see <a href="https://raw.githubusercontent.com/openjdk/jdk/master/src/hotspot/share/interpreter/bytecodes.hpp">hotspot/share/interpreter/bytecodes.hpp</a>
 * @see <a href="https://raw.githubusercontent.com/openjdk/jdk/master/src/hotspot/share/interpreter/bytecodes.cpp">hotspot/share/interpreter/bytecodes.cpp</a>
 */
class VerificationBytecodes {

    static final int _breakpoint = 202,
            number_of_java_codes = 203,
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

    static int length_for(int code) {
        return is_valid(code) ? _lengths[code] & 0xf : -1;
    }

    static int wide_length_for(int code) {
        return is_valid(code) ? _lengths[code] >> 4 : -1;
    }

    static boolean is_store_into_local(int code) {
        return (Classfile.ISTORE <= code && code <= Classfile.ASTORE_3);
    }

    static final int _lengths[] = new int[number_of_codes];

    static int special_length_at(int code, byte bytecode[], int bci, int end) {
        switch (code) {
            case Classfile.WIDE:
                if (bci + 1 >= end) {
                    return -1;
                }
                return wide_length_for(bytecode[bci + 1] & 0xff);
            case Classfile.TABLESWITCH:
                int aligned_bci = align(bci + 1);
                if (aligned_bci + 3 * 4 >= end) {
                    return -1;
                }
                ByteBuffer bb = ByteBuffer.wrap(bytecode, aligned_bci + 1 * 4, 2 * 4);
                int lo = bb.getInt();
                int hi = bb.getInt();
                int len = aligned_bci - bci + (3 + hi - lo + 1) * 4;
                return len > 0 ? len : -1;
            case Classfile.LOOKUPSWITCH:
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

    static int raw_special_length_at(byte bytecode[], int bci, int end) {
        int code = code_or_bp_at(bytecode, bci);
        if (code == _breakpoint) {
            return 1;
        } else {
            return special_length_at(code, bytecode, bci, end);
        }
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
        def(Classfile.NOP, "nop", "b", null, T_VOID, 0);
        def(Classfile.ACONST_NULL, "aconst_null", "b", null, T_OBJECT, 1);
        def(Classfile.ICONST_M1, "iconst_m1", "b", null, T_INT, 1);
        def(Classfile.ICONST_0, "iconst_0", "b", null, T_INT, 1);
        def(Classfile.ICONST_1, "iconst_1", "b", null, T_INT, 1);
        def(Classfile.ICONST_2, "iconst_2", "b", null, T_INT, 1);
        def(Classfile.ICONST_3, "iconst_3", "b", null, T_INT, 1);
        def(Classfile.ICONST_4, "iconst_4", "b", null, T_INT, 1);
        def(Classfile.ICONST_5, "iconst_5", "b", null, T_INT, 1);
        def(Classfile.LCONST_0, "lconst_0", "b", null, T_LONG, 2);
        def(Classfile.LCONST_1, "lconst_1", "b", null, T_LONG, 2);
        def(Classfile.FCONST_0, "fconst_0", "b", null, T_FLOAT, 1);
        def(Classfile.FCONST_1, "fconst_1", "b", null, T_FLOAT, 1);
        def(Classfile.FCONST_2, "fconst_2", "b", null, T_FLOAT, 1);
        def(Classfile.DCONST_0, "dconst_0", "b", null, T_DOUBLE, 2);
        def(Classfile.DCONST_1, "dconst_1", "b", null, T_DOUBLE, 2);
        def(Classfile.BIPUSH, "bipush", "bc", null, T_INT, 1);
        def(Classfile.SIPUSH, "sipush", "bcc", null, T_INT, 1);
        def(Classfile.LDC, "ldc", "bk", null, T_ILLEGAL, 1);
        def(Classfile.LDC_W, "ldc_w", "bkk", null, T_ILLEGAL, 1);
        def(Classfile.LDC2_W, "ldc2_w", "bkk", null, T_ILLEGAL, 2);
        def(Classfile.ILOAD, "iload", "bi", "wbii", T_INT, 1);
        def(Classfile.LLOAD, "lload", "bi", "wbii", T_LONG, 2);
        def(Classfile.FLOAD, "fload", "bi", "wbii", T_FLOAT, 1);
        def(Classfile.DLOAD, "dload", "bi", "wbii", T_DOUBLE, 2);
        def(Classfile.ALOAD, "aload", "bi", "wbii", T_OBJECT, 1);
        def(Classfile.ILOAD_0, "iload_0", "b", null, T_INT, 1);
        def(Classfile.ILOAD_1, "iload_1", "b", null, T_INT, 1);
        def(Classfile.ILOAD_2, "iload_2", "b", null, T_INT, 1);
        def(Classfile.ILOAD_3, "iload_3", "b", null, T_INT, 1);
        def(Classfile.LLOAD_0, "lload_0", "b", null, T_LONG, 2);
        def(Classfile.LLOAD_1, "lload_1", "b", null, T_LONG, 2);
        def(Classfile.LLOAD_2, "lload_2", "b", null, T_LONG, 2);
        def(Classfile.LLOAD_3, "lload_3", "b", null, T_LONG, 2);
        def(Classfile.FLOAD_0, "fload_0", "b", null, T_FLOAT, 1);
        def(Classfile.FLOAD_1, "fload_1", "b", null, T_FLOAT, 1);
        def(Classfile.FLOAD_2, "fload_2", "b", null, T_FLOAT, 1);
        def(Classfile.FLOAD_3, "fload_3", "b", null, T_FLOAT, 1);
        def(Classfile.DLOAD_0, "dload_0", "b", null, T_DOUBLE, 2);
        def(Classfile.DLOAD_1, "dload_1", "b", null, T_DOUBLE, 2);
        def(Classfile.DLOAD_2, "dload_2", "b", null, T_DOUBLE, 2);
        def(Classfile.DLOAD_3, "dload_3", "b", null, T_DOUBLE, 2);
        def(Classfile.ALOAD_0, "aload_0", "b", null, T_OBJECT, 1);
        def(Classfile.ALOAD_1, "aload_1", "b", null, T_OBJECT, 1);
        def(Classfile.ALOAD_2, "aload_2", "b", null, T_OBJECT, 1);
        def(Classfile.ALOAD_3, "aload_3", "b", null, T_OBJECT, 1);
        def(Classfile.IALOAD, "iaload", "b", null, T_INT, -1);
        def(Classfile.LALOAD, "laload", "b", null, T_LONG, 0);
        def(Classfile.FALOAD, "faload", "b", null, T_FLOAT, -1);
        def(Classfile.DALOAD, "daload", "b", null, T_DOUBLE, 0);
        def(Classfile.AALOAD, "aaload", "b", null, T_OBJECT, -1);
        def(Classfile.BALOAD, "baload", "b", null, T_INT, -1);
        def(Classfile.CALOAD, "caload", "b", null, T_INT, -1);
        def(Classfile.SALOAD, "saload", "b", null, T_INT, -1);
        def(Classfile.ISTORE, "istore", "bi", "wbii", T_VOID, -1);
        def(Classfile.LSTORE, "lstore", "bi", "wbii", T_VOID, -2);
        def(Classfile.FSTORE, "fstore", "bi", "wbii", T_VOID, -1);
        def(Classfile.DSTORE, "dstore", "bi", "wbii", T_VOID, -2);
        def(Classfile.ASTORE, "astore", "bi", "wbii", T_VOID, -1);
        def(Classfile.ISTORE_0, "istore_0", "b", null, T_VOID, -1);
        def(Classfile.ISTORE_1, "istore_1", "b", null, T_VOID, -1);
        def(Classfile.ISTORE_2, "istore_2", "b", null, T_VOID, -1);
        def(Classfile.ISTORE_3, "istore_3", "b", null, T_VOID, -1);
        def(Classfile.LSTORE_0, "lstore_0", "b", null, T_VOID, -2);
        def(Classfile.LSTORE_1, "lstore_1", "b", null, T_VOID, -2);
        def(Classfile.LSTORE_2, "lstore_2", "b", null, T_VOID, -2);
        def(Classfile.LSTORE_3, "lstore_3", "b", null, T_VOID, -2);
        def(Classfile.FSTORE_0, "fstore_0", "b", null, T_VOID, -1);
        def(Classfile.FSTORE_1, "fstore_1", "b", null, T_VOID, -1);
        def(Classfile.FSTORE_2, "fstore_2", "b", null, T_VOID, -1);
        def(Classfile.FSTORE_3, "fstore_3", "b", null, T_VOID, -1);
        def(Classfile.DSTORE_0, "dstore_0", "b", null, T_VOID, -2);
        def(Classfile.DSTORE_1, "dstore_1", "b", null, T_VOID, -2);
        def(Classfile.DSTORE_2, "dstore_2", "b", null, T_VOID, -2);
        def(Classfile.DSTORE_3, "dstore_3", "b", null, T_VOID, -2);
        def(Classfile.ASTORE_0, "astore_0", "b", null, T_VOID, -1);
        def(Classfile.ASTORE_1, "astore_1", "b", null, T_VOID, -1);
        def(Classfile.ASTORE_2, "astore_2", "b", null, T_VOID, -1);
        def(Classfile.ASTORE_3, "astore_3", "b", null, T_VOID, -1);
        def(Classfile.IASTORE, "iastore", "b", null, T_VOID, -3);
        def(Classfile.LASTORE, "lastore", "b", null, T_VOID, -4);
        def(Classfile.FASTORE, "fastore", "b", null, T_VOID, -3);
        def(Classfile.DASTORE, "dastore", "b", null, T_VOID, -4);
        def(Classfile.AASTORE, "aastore", "b", null, T_VOID, -3);
        def(Classfile.BASTORE, "bastore", "b", null, T_VOID, -3);
        def(Classfile.CASTORE, "castore", "b", null, T_VOID, -3);
        def(Classfile.SASTORE, "sastore", "b", null, T_VOID, -3);
        def(Classfile.POP, "pop", "b", null, T_VOID, -1);
        def(Classfile.POP2, "pop2", "b", null, T_VOID, -2);
        def(Classfile.DUP, "dup", "b", null, T_VOID, 1);
        def(Classfile.DUP_X1, "dup_x1", "b", null, T_VOID, 1);
        def(Classfile.DUP_X2, "dup_x2", "b", null, T_VOID, 1);
        def(Classfile.DUP2, "dup2", "b", null, T_VOID, 2);
        def(Classfile.DUP2_X1, "dup2_x1", "b", null, T_VOID, 2);
        def(Classfile.DUP2_X2, "dup2_x2", "b", null, T_VOID, 2);
        def(Classfile.SWAP, "swap", "b", null, T_VOID, 0);
        def(Classfile.IADD, "iadd", "b", null, T_INT, -1);
        def(Classfile.LADD, "ladd", "b", null, T_LONG, -2);
        def(Classfile.FADD, "fadd", "b", null, T_FLOAT, -1);
        def(Classfile.DADD, "dadd", "b", null, T_DOUBLE, -2);
        def(Classfile.ISUB, "isub", "b", null, T_INT, -1);
        def(Classfile.LSUB, "lsub", "b", null, T_LONG, -2);
        def(Classfile.FSUB, "fsub", "b", null, T_FLOAT, -1);
        def(Classfile.DSUB, "dsub", "b", null, T_DOUBLE, -2);
        def(Classfile.IMUL, "imul", "b", null, T_INT, -1);
        def(Classfile.LMUL, "lmul", "b", null, T_LONG, -2);
        def(Classfile.FMUL, "fmul", "b", null, T_FLOAT, -1);
        def(Classfile.DMUL, "dmul", "b", null, T_DOUBLE, -2);
        def(Classfile.IDIV, "idiv", "b", null, T_INT, -1);
        def(Classfile.LDIV, "ldiv", "b", null, T_LONG, -2);
        def(Classfile.FDIV, "fdiv", "b", null, T_FLOAT, -1);
        def(Classfile.DDIV, "ddiv", "b", null, T_DOUBLE, -2);
        def(Classfile.IREM, "irem", "b", null, T_INT, -1);
        def(Classfile.LREM, "lrem", "b", null, T_LONG, -2);
        def(Classfile.FREM, "frem", "b", null, T_FLOAT, -1);
        def(Classfile.DREM, "drem", "b", null, T_DOUBLE, -2);
        def(Classfile.INEG, "ineg", "b", null, T_INT, 0);
        def(Classfile.LNEG, "lneg", "b", null, T_LONG, 0);
        def(Classfile.FNEG, "fneg", "b", null, T_FLOAT, 0);
        def(Classfile.DNEG, "dneg", "b", null, T_DOUBLE, 0);
        def(Classfile.ISHL, "ishl", "b", null, T_INT, -1);
        def(Classfile.LSHL, "lshl", "b", null, T_LONG, -1);
        def(Classfile.ISHR, "ishr", "b", null, T_INT, -1);
        def(Classfile.LSHR, "lshr", "b", null, T_LONG, -1);
        def(Classfile.IUSHR, "iushr", "b", null, T_INT, -1);
        def(Classfile.LUSHR, "lushr", "b", null, T_LONG, -1);
        def(Classfile.IAND, "iand", "b", null, T_INT, -1);
        def(Classfile.LAND, "land", "b", null, T_LONG, -2);
        def(Classfile.IOR, "ior", "b", null, T_INT, -1);
        def(Classfile.LOR, "lor", "b", null, T_LONG, -2);
        def(Classfile.IXOR, "ixor", "b", null, T_INT, -1);
        def(Classfile.LXOR, "lxor", "b", null, T_LONG, -2);
        def(Classfile.IINC, "iinc", "bic", "wbiicc", T_VOID, 0);
        def(Classfile.I2L, "i2l", "b", null, T_LONG, 1);
        def(Classfile.I2F, "i2f", "b", null, T_FLOAT, 0);
        def(Classfile.I2D, "i2d", "b", null, T_DOUBLE, 1);
        def(Classfile.L2I, "l2i", "b", null, T_INT, -1);
        def(Classfile.L2F, "l2f", "b", null, T_FLOAT, -1);
        def(Classfile.L2D, "l2d", "b", null, T_DOUBLE, 0);
        def(Classfile.F2I, "f2i", "b", null, T_INT, 0);
        def(Classfile.F2L, "f2l", "b", null, T_LONG, 1);
        def(Classfile.F2D, "f2d", "b", null, T_DOUBLE, 1);
        def(Classfile.D2I, "d2i", "b", null, T_INT, -1);
        def(Classfile.D2L, "d2l", "b", null, T_LONG, 0);
        def(Classfile.D2F, "d2f", "b", null, T_FLOAT, -1);
        def(Classfile.I2B, "i2b", "b", null, T_BYTE, 0);
        def(Classfile.I2C, "i2c", "b", null, T_CHAR, 0);
        def(Classfile.I2S, "i2s", "b", null, T_SHORT, 0);
        def(Classfile.LCMP, "lcmp", "b", null, T_VOID, -3);
        def(Classfile.FCMPL, "fcmpl", "b", null, T_VOID, -1);
        def(Classfile.FCMPG, "fcmpg", "b", null, T_VOID, -1);
        def(Classfile.DCMPL, "dcmpl", "b", null, T_VOID, -3);
        def(Classfile.DCMPG, "dcmpg", "b", null, T_VOID, -3);
        def(Classfile.IFEQ, "ifeq", "boo", null, T_VOID, -1);
        def(Classfile.IFNE, "ifne", "boo", null, T_VOID, -1);
        def(Classfile.IFLT, "iflt", "boo", null, T_VOID, -1);
        def(Classfile.IFGE, "ifge", "boo", null, T_VOID, -1);
        def(Classfile.IFGT, "ifgt", "boo", null, T_VOID, -1);
        def(Classfile.IFLE, "ifle", "boo", null, T_VOID, -1);
        def(Classfile.IF_ICMPEQ, "if_icmpeq", "boo", null, T_VOID, -2);
        def(Classfile.IF_ICMPNE, "if_icmpne", "boo", null, T_VOID, -2);
        def(Classfile.IF_ICMPLT, "if_icmplt", "boo", null, T_VOID, -2);
        def(Classfile.IF_ICMPGE, "if_icmpge", "boo", null, T_VOID, -2);
        def(Classfile.IF_ICMPGT, "if_icmpgt", "boo", null, T_VOID, -2);
        def(Classfile.IF_ICMPLE, "if_icmple", "boo", null, T_VOID, -2);
        def(Classfile.IF_ACMPEQ, "if_acmpeq", "boo", null, T_VOID, -2);
        def(Classfile.IF_ACMPNE, "if_acmpne", "boo", null, T_VOID, -2);
        def(Classfile.GOTO, "goto", "boo", null, T_VOID, 0);
        def(Classfile.JSR, "jsr", "boo", null, T_INT, 0);
        def(Classfile.RET, "ret", "bi", "wbii", T_VOID, 0);
        def(Classfile.TABLESWITCH, "tableswitch", "", null, T_VOID, -1); // may have backward branches
        def(Classfile.LOOKUPSWITCH, "lookupswitch", "", null, T_VOID, -1); // rewriting in interpreter
        def(Classfile.IRETURN, "ireturn", "b", null, T_INT, -1);
        def(Classfile.LRETURN, "lreturn", "b", null, T_LONG, -2);
        def(Classfile.FRETURN, "freturn", "b", null, T_FLOAT, -1);
        def(Classfile.DRETURN, "dreturn", "b", null, T_DOUBLE, -2);
        def(Classfile.ARETURN, "areturn", "b", null, T_OBJECT, -1);
        def(Classfile.RETURN, "return", "b", null, T_VOID, 0);
        def(Classfile.GETSTATIC, "getstatic", "bJJ", null, T_ILLEGAL, 1);
        def(Classfile.PUTSTATIC, "putstatic", "bJJ", null, T_ILLEGAL, -1);
        def(Classfile.GETFIELD, "getfield", "bJJ", null, T_ILLEGAL, 0);
        def(Classfile.PUTFIELD, "putfield", "bJJ", null, T_ILLEGAL, -2);
        def(Classfile.INVOKEVIRTUAL, "invokevirtual", "bJJ", null, T_ILLEGAL, -1);
        def(Classfile.INVOKESPECIAL, "invokespecial", "bJJ", null, T_ILLEGAL, -1);
        def(Classfile.INVOKESTATIC, "invokestatic", "bJJ", null, T_ILLEGAL, 0);
        def(Classfile.INVOKEINTERFACE, "invokeinterface", "bJJ__", null, T_ILLEGAL, -1);
        def(Classfile.INVOKEDYNAMIC, "invokedynamic", "bJJJJ", null, T_ILLEGAL, 0);
        def(Classfile.NEW, "new", "bkk", null, T_OBJECT, 1);
        def(Classfile.NEWARRAY, "newarray", "bc", null, T_OBJECT, 0);
        def(Classfile.ANEWARRAY, "anewarray", "bkk", null, T_OBJECT, 0);
        def(Classfile.ARRAYLENGTH, "arraylength", "b", null, T_VOID, 0);
        def(Classfile.ATHROW, "athrow", "b", null, T_VOID, -1);
        def(Classfile.CHECKCAST, "checkcast", "bkk", null, T_OBJECT, 0);
        def(Classfile.INSTANCEOF, "instanceof", "bkk", null, T_INT, 0);
        def(Classfile.MONITORENTER, "monitorenter", "b", null, T_VOID, -1);
        def(Classfile.MONITOREXIT, "monitorexit", "b", null, T_VOID, -1);
        def(Classfile.WIDE, "wide", "", null, T_VOID, 0);
        def(Classfile.MULTIANEWARRAY, "multianewarray", "bkkc", null, T_OBJECT, 1);
        def(Classfile.IFNULL, "ifnull", "boo", null, T_VOID, -1);
        def(Classfile.IFNONNULL, "ifnonnull", "boo", null, T_VOID, -1);
        def(Classfile.GOTO_W, "goto_w", "boooo", null, T_VOID, 0);
        def(Classfile.JSR_W, "jsr_w", "boooo", null, T_INT, 0);
        def(_breakpoint, "breakpoint", "", null, T_VOID, 0);
        def(_fast_agetfield, "fast_agetfield", "bJJ", null, T_OBJECT, 0, Classfile.GETFIELD);
        def(_fast_bgetfield, "fast_bgetfield", "bJJ", null, T_INT, 0, Classfile.GETFIELD);
        def(_fast_cgetfield, "fast_cgetfield", "bJJ", null, T_CHAR, 0, Classfile.GETFIELD);
        def(_fast_dgetfield, "fast_dgetfield", "bJJ", null, T_DOUBLE, 0, Classfile.GETFIELD);
        def(_fast_fgetfield, "fast_fgetfield", "bJJ", null, T_FLOAT, 0, Classfile.GETFIELD);
        def(_fast_igetfield, "fast_igetfield", "bJJ", null, T_INT, 0, Classfile.GETFIELD);
        def(_fast_lgetfield, "fast_lgetfield", "bJJ", null, T_LONG, 0, Classfile.GETFIELD);
        def(_fast_sgetfield, "fast_sgetfield", "bJJ", null, T_SHORT, 0, Classfile.GETFIELD);
        def(_fast_aputfield, "fast_aputfield", "bJJ", null, T_OBJECT, 0, Classfile.PUTFIELD);
        def(_fast_bputfield, "fast_bputfield", "bJJ", null, T_INT, 0, Classfile.PUTFIELD);
        def(_fast_zputfield, "fast_zputfield", "bJJ", null, T_INT, 0, Classfile.PUTFIELD);
        def(_fast_cputfield, "fast_cputfield", "bJJ", null, T_CHAR, 0, Classfile.PUTFIELD);
        def(_fast_dputfield, "fast_dputfield", "bJJ", null, T_DOUBLE, 0, Classfile.PUTFIELD);
        def(_fast_fputfield, "fast_fputfield", "bJJ", null, T_FLOAT, 0, Classfile.PUTFIELD);
        def(_fast_iputfield, "fast_iputfield", "bJJ", null, T_INT, 0, Classfile.PUTFIELD);
        def(_fast_lputfield, "fast_lputfield", "bJJ", null, T_LONG, 0, Classfile.PUTFIELD);
        def(_fast_sputfield, "fast_sputfield", "bJJ", null, T_SHORT, 0, Classfile.PUTFIELD);
        def(_fast_aload_0, "fast_aload_0", "b", null, T_OBJECT, 1, Classfile.ALOAD_0);
        def(_fast_iaccess_0, "fast_iaccess_0", "b_JJ", null, T_INT, 1, Classfile.ALOAD_0);
        def(_fast_aaccess_0, "fast_aaccess_0", "b_JJ", null, T_OBJECT, 1, Classfile.ALOAD_0);
        def(_fast_faccess_0, "fast_faccess_0", "b_JJ", null, T_OBJECT, 1, Classfile.ALOAD_0);
        def(_fast_iload, "fast_iload", "bi", null, T_INT, 1, Classfile.ILOAD);
        def(_fast_iload2, "fast_iload2", "bi_i", null, T_INT, 2, Classfile.ILOAD);
        def(_fast_icaload, "fast_icaload", "bi_", null, T_INT, 0, Classfile.ILOAD);
        def(_fast_invokevfinal, "fast_invokevfinal", "bJJ", null, T_ILLEGAL, -1, Classfile.INVOKEVIRTUAL);
        def(_fast_linearswitch, "fast_linearswitch", "", null, T_VOID, -1, Classfile.LOOKUPSWITCH);
        def(_fast_binaryswitch, "fast_binaryswitch", "", null, T_VOID, -1, Classfile.LOOKUPSWITCH);
        def(_return_register_finalizer, "return_register_finalizer", "b", null, T_VOID, 0, Classfile.RETURN);
        def(_invokehandle, "invokehandle", "bJJ", null, T_ILLEGAL, -1, Classfile.INVOKEVIRTUAL);
        def(_fast_aldc, "fast_aldc", "bj", null, T_OBJECT, 1, Classfile.LDC);
        def(_fast_aldc_w, "fast_aldc_w", "bJJ", null, T_OBJECT, 1, Classfile.LDC_W);
        def(_nofast_getfield, "nofast_getfield", "bJJ", null, T_ILLEGAL, 0, Classfile.GETFIELD);
        def(_nofast_putfield, "nofast PUTFIELD", "bJJ", null, T_ILLEGAL, -2, Classfile.PUTFIELD);
        def(_nofast_aload_0, "nofast_aload_0", "b", null, T_ILLEGAL, 1, Classfile.ALOAD_0);
        def(_nofast_iload, "nofast_iload", "bi", null, T_ILLEGAL, 1, Classfile.ILOAD);
        def(_shouldnotreachhere, "_shouldnotreachhere", "b", null, T_VOID, 0);
    }

    final int code;
    VerificationBytecodes(int code) {
        this.code = code;
    }
}
