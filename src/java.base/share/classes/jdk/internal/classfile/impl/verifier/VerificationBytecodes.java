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
package jdk.internal.classfile.impl.verifier;

import jdk.internal.classfile.impl.verifier.VerificationSignature.BasicType;

import static jdk.internal.classfile.impl.RawBytecodeHelper.*;
import static jdk.internal.classfile.impl.verifier.VerificationSignature.BasicType.*;

/// From `bytecodes.cpp`.
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

    static boolean is_store_into_local(int code) {
        return (ISTORE <= code && code <= ASTORE_3);
    }

    static final int _lengths[] = new int[number_of_codes];

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
        def(NOP, "nop", "b", null, T_VOID, 0);
        def(ACONST_NULL, "aconst_null", "b", null, T_OBJECT, 1);
        def(ICONST_M1, "iconst_m1", "b", null, T_INT, 1);
        def(ICONST_0, "iconst_0", "b", null, T_INT, 1);
        def(ICONST_1, "iconst_1", "b", null, T_INT, 1);
        def(ICONST_2, "iconst_2", "b", null, T_INT, 1);
        def(ICONST_3, "iconst_3", "b", null, T_INT, 1);
        def(ICONST_4, "iconst_4", "b", null, T_INT, 1);
        def(ICONST_5, "iconst_5", "b", null, T_INT, 1);
        def(LCONST_0, "lconst_0", "b", null, T_LONG, 2);
        def(LCONST_1, "lconst_1", "b", null, T_LONG, 2);
        def(FCONST_0, "fconst_0", "b", null, T_FLOAT, 1);
        def(FCONST_1, "fconst_1", "b", null, T_FLOAT, 1);
        def(FCONST_2, "fconst_2", "b", null, T_FLOAT, 1);
        def(DCONST_0, "dconst_0", "b", null, T_DOUBLE, 2);
        def(DCONST_1, "dconst_1", "b", null, T_DOUBLE, 2);
        def(BIPUSH, "bipush", "bc", null, T_INT, 1);
        def(SIPUSH, "sipush", "bcc", null, T_INT, 1);
        def(LDC, "ldc", "bk", null, T_ILLEGAL, 1);
        def(LDC_W, "ldc_w", "bkk", null, T_ILLEGAL, 1);
        def(LDC2_W, "ldc2_w", "bkk", null, T_ILLEGAL, 2);
        def(ILOAD, "iload", "bi", "wbii", T_INT, 1);
        def(LLOAD, "lload", "bi", "wbii", T_LONG, 2);
        def(FLOAD, "fload", "bi", "wbii", T_FLOAT, 1);
        def(DLOAD, "dload", "bi", "wbii", T_DOUBLE, 2);
        def(ALOAD, "aload", "bi", "wbii", T_OBJECT, 1);
        def(ILOAD_0, "iload_0", "b", null, T_INT, 1);
        def(ILOAD_1, "iload_1", "b", null, T_INT, 1);
        def(ILOAD_2, "iload_2", "b", null, T_INT, 1);
        def(ILOAD_3, "iload_3", "b", null, T_INT, 1);
        def(LLOAD_0, "lload_0", "b", null, T_LONG, 2);
        def(LLOAD_1, "lload_1", "b", null, T_LONG, 2);
        def(LLOAD_2, "lload_2", "b", null, T_LONG, 2);
        def(LLOAD_3, "lload_3", "b", null, T_LONG, 2);
        def(FLOAD_0, "fload_0", "b", null, T_FLOAT, 1);
        def(FLOAD_1, "fload_1", "b", null, T_FLOAT, 1);
        def(FLOAD_2, "fload_2", "b", null, T_FLOAT, 1);
        def(FLOAD_3, "fload_3", "b", null, T_FLOAT, 1);
        def(DLOAD_0, "dload_0", "b", null, T_DOUBLE, 2);
        def(DLOAD_1, "dload_1", "b", null, T_DOUBLE, 2);
        def(DLOAD_2, "dload_2", "b", null, T_DOUBLE, 2);
        def(DLOAD_3, "dload_3", "b", null, T_DOUBLE, 2);
        def(ALOAD_0, "aload_0", "b", null, T_OBJECT, 1);
        def(ALOAD_1, "aload_1", "b", null, T_OBJECT, 1);
        def(ALOAD_2, "aload_2", "b", null, T_OBJECT, 1);
        def(ALOAD_3, "aload_3", "b", null, T_OBJECT, 1);
        def(IALOAD, "iaload", "b", null, T_INT, -1);
        def(LALOAD, "laload", "b", null, T_LONG, 0);
        def(FALOAD, "faload", "b", null, T_FLOAT, -1);
        def(DALOAD, "daload", "b", null, T_DOUBLE, 0);
        def(AALOAD, "aaload", "b", null, T_OBJECT, -1);
        def(BALOAD, "baload", "b", null, T_INT, -1);
        def(CALOAD, "caload", "b", null, T_INT, -1);
        def(SALOAD, "saload", "b", null, T_INT, -1);
        def(ISTORE, "istore", "bi", "wbii", T_VOID, -1);
        def(LSTORE, "lstore", "bi", "wbii", T_VOID, -2);
        def(FSTORE, "fstore", "bi", "wbii", T_VOID, -1);
        def(DSTORE, "dstore", "bi", "wbii", T_VOID, -2);
        def(ASTORE, "astore", "bi", "wbii", T_VOID, -1);
        def(ISTORE_0, "istore_0", "b", null, T_VOID, -1);
        def(ISTORE_1, "istore_1", "b", null, T_VOID, -1);
        def(ISTORE_2, "istore_2", "b", null, T_VOID, -1);
        def(ISTORE_3, "istore_3", "b", null, T_VOID, -1);
        def(LSTORE_0, "lstore_0", "b", null, T_VOID, -2);
        def(LSTORE_1, "lstore_1", "b", null, T_VOID, -2);
        def(LSTORE_2, "lstore_2", "b", null, T_VOID, -2);
        def(LSTORE_3, "lstore_3", "b", null, T_VOID, -2);
        def(FSTORE_0, "fstore_0", "b", null, T_VOID, -1);
        def(FSTORE_1, "fstore_1", "b", null, T_VOID, -1);
        def(FSTORE_2, "fstore_2", "b", null, T_VOID, -1);
        def(FSTORE_3, "fstore_3", "b", null, T_VOID, -1);
        def(DSTORE_0, "dstore_0", "b", null, T_VOID, -2);
        def(DSTORE_1, "dstore_1", "b", null, T_VOID, -2);
        def(DSTORE_2, "dstore_2", "b", null, T_VOID, -2);
        def(DSTORE_3, "dstore_3", "b", null, T_VOID, -2);
        def(ASTORE_0, "astore_0", "b", null, T_VOID, -1);
        def(ASTORE_1, "astore_1", "b", null, T_VOID, -1);
        def(ASTORE_2, "astore_2", "b", null, T_VOID, -1);
        def(ASTORE_3, "astore_3", "b", null, T_VOID, -1);
        def(IASTORE, "iastore", "b", null, T_VOID, -3);
        def(LASTORE, "lastore", "b", null, T_VOID, -4);
        def(FASTORE, "fastore", "b", null, T_VOID, -3);
        def(DASTORE, "dastore", "b", null, T_VOID, -4);
        def(AASTORE, "aastore", "b", null, T_VOID, -3);
        def(BASTORE, "bastore", "b", null, T_VOID, -3);
        def(CASTORE, "castore", "b", null, T_VOID, -3);
        def(SASTORE, "sastore", "b", null, T_VOID, -3);
        def(POP, "pop", "b", null, T_VOID, -1);
        def(POP2, "pop2", "b", null, T_VOID, -2);
        def(DUP, "dup", "b", null, T_VOID, 1);
        def(DUP_X1, "dup_x1", "b", null, T_VOID, 1);
        def(DUP_X2, "dup_x2", "b", null, T_VOID, 1);
        def(DUP2, "dup2", "b", null, T_VOID, 2);
        def(DUP2_X1, "dup2_x1", "b", null, T_VOID, 2);
        def(DUP2_X2, "dup2_x2", "b", null, T_VOID, 2);
        def(SWAP, "swap", "b", null, T_VOID, 0);
        def(IADD, "iadd", "b", null, T_INT, -1);
        def(LADD, "ladd", "b", null, T_LONG, -2);
        def(FADD, "fadd", "b", null, T_FLOAT, -1);
        def(DADD, "dadd", "b", null, T_DOUBLE, -2);
        def(ISUB, "isub", "b", null, T_INT, -1);
        def(LSUB, "lsub", "b", null, T_LONG, -2);
        def(FSUB, "fsub", "b", null, T_FLOAT, -1);
        def(DSUB, "dsub", "b", null, T_DOUBLE, -2);
        def(IMUL, "imul", "b", null, T_INT, -1);
        def(LMUL, "lmul", "b", null, T_LONG, -2);
        def(FMUL, "fmul", "b", null, T_FLOAT, -1);
        def(DMUL, "dmul", "b", null, T_DOUBLE, -2);
        def(IDIV, "idiv", "b", null, T_INT, -1);
        def(LDIV, "ldiv", "b", null, T_LONG, -2);
        def(FDIV, "fdiv", "b", null, T_FLOAT, -1);
        def(DDIV, "ddiv", "b", null, T_DOUBLE, -2);
        def(IREM, "irem", "b", null, T_INT, -1);
        def(LREM, "lrem", "b", null, T_LONG, -2);
        def(FREM, "frem", "b", null, T_FLOAT, -1);
        def(DREM, "drem", "b", null, T_DOUBLE, -2);
        def(INEG, "ineg", "b", null, T_INT, 0);
        def(LNEG, "lneg", "b", null, T_LONG, 0);
        def(FNEG, "fneg", "b", null, T_FLOAT, 0);
        def(DNEG, "dneg", "b", null, T_DOUBLE, 0);
        def(ISHL, "ishl", "b", null, T_INT, -1);
        def(LSHL, "lshl", "b", null, T_LONG, -1);
        def(ISHR, "ishr", "b", null, T_INT, -1);
        def(LSHR, "lshr", "b", null, T_LONG, -1);
        def(IUSHR, "iushr", "b", null, T_INT, -1);
        def(LUSHR, "lushr", "b", null, T_LONG, -1);
        def(IAND, "iand", "b", null, T_INT, -1);
        def(LAND, "land", "b", null, T_LONG, -2);
        def(IOR, "ior", "b", null, T_INT, -1);
        def(LOR, "lor", "b", null, T_LONG, -2);
        def(IXOR, "ixor", "b", null, T_INT, -1);
        def(LXOR, "lxor", "b", null, T_LONG, -2);
        def(IINC, "iinc", "bic", "wbiicc", T_VOID, 0);
        def(I2L, "i2l", "b", null, T_LONG, 1);
        def(I2F, "i2f", "b", null, T_FLOAT, 0);
        def(I2D, "i2d", "b", null, T_DOUBLE, 1);
        def(L2I, "l2i", "b", null, T_INT, -1);
        def(L2F, "l2f", "b", null, T_FLOAT, -1);
        def(L2D, "l2d", "b", null, T_DOUBLE, 0);
        def(F2I, "f2i", "b", null, T_INT, 0);
        def(F2L, "f2l", "b", null, T_LONG, 1);
        def(F2D, "f2d", "b", null, T_DOUBLE, 1);
        def(D2I, "d2i", "b", null, T_INT, -1);
        def(D2L, "d2l", "b", null, T_LONG, 0);
        def(D2F, "d2f", "b", null, T_FLOAT, -1);
        def(I2B, "i2b", "b", null, T_BYTE, 0);
        def(I2C, "i2c", "b", null, T_CHAR, 0);
        def(I2S, "i2s", "b", null, T_SHORT, 0);
        def(LCMP, "lcmp", "b", null, T_VOID, -3);
        def(FCMPL, "fcmpl", "b", null, T_VOID, -1);
        def(FCMPG, "fcmpg", "b", null, T_VOID, -1);
        def(DCMPL, "dcmpl", "b", null, T_VOID, -3);
        def(DCMPG, "dcmpg", "b", null, T_VOID, -3);
        def(IFEQ, "ifeq", "boo", null, T_VOID, -1);
        def(IFNE, "ifne", "boo", null, T_VOID, -1);
        def(IFLT, "iflt", "boo", null, T_VOID, -1);
        def(IFGE, "ifge", "boo", null, T_VOID, -1);
        def(IFGT, "ifgt", "boo", null, T_VOID, -1);
        def(IFLE, "ifle", "boo", null, T_VOID, -1);
        def(IF_ICMPEQ, "if_icmpeq", "boo", null, T_VOID, -2);
        def(IF_ICMPNE, "if_icmpne", "boo", null, T_VOID, -2);
        def(IF_ICMPLT, "if_icmplt", "boo", null, T_VOID, -2);
        def(IF_ICMPGE, "if_icmpge", "boo", null, T_VOID, -2);
        def(IF_ICMPGT, "if_icmpgt", "boo", null, T_VOID, -2);
        def(IF_ICMPLE, "if_icmple", "boo", null, T_VOID, -2);
        def(IF_ACMPEQ, "if_acmpeq", "boo", null, T_VOID, -2);
        def(IF_ACMPNE, "if_acmpne", "boo", null, T_VOID, -2);
        def(GOTO, "goto", "boo", null, T_VOID, 0);
        def(JSR, "jsr", "boo", null, T_INT, 0);
        def(RET, "ret", "bi", "wbii", T_VOID, 0);
        def(TABLESWITCH, "tableswitch", "", null, T_VOID, -1); // may have backward branches
        def(LOOKUPSWITCH, "lookupswitch", "", null, T_VOID, -1); // rewriting in interpreter
        def(IRETURN, "ireturn", "b", null, T_INT, -1);
        def(LRETURN, "lreturn", "b", null, T_LONG, -2);
        def(FRETURN, "freturn", "b", null, T_FLOAT, -1);
        def(DRETURN, "dreturn", "b", null, T_DOUBLE, -2);
        def(ARETURN, "areturn", "b", null, T_OBJECT, -1);
        def(RETURN, "return", "b", null, T_VOID, 0);
        def(GETSTATIC, "getstatic", "bJJ", null, T_ILLEGAL, 1);
        def(PUTSTATIC, "putstatic", "bJJ", null, T_ILLEGAL, -1);
        def(GETFIELD, "getfield", "bJJ", null, T_ILLEGAL, 0);
        def(PUTFIELD, "putfield", "bJJ", null, T_ILLEGAL, -2);
        def(INVOKEVIRTUAL, "invokevirtual", "bJJ", null, T_ILLEGAL, -1);
        def(INVOKESPECIAL, "invokespecial", "bJJ", null, T_ILLEGAL, -1);
        def(INVOKESTATIC, "invokestatic", "bJJ", null, T_ILLEGAL, 0);
        def(INVOKEINTERFACE, "invokeinterface", "bJJ__", null, T_ILLEGAL, -1);
        def(INVOKEDYNAMIC, "invokedynamic", "bJJJJ", null, T_ILLEGAL, 0);
        def(NEW, "new", "bkk", null, T_OBJECT, 1);
        def(NEWARRAY, "newarray", "bc", null, T_OBJECT, 0);
        def(ANEWARRAY, "anewarray", "bkk", null, T_OBJECT, 0);
        def(ARRAYLENGTH, "arraylength", "b", null, T_VOID, 0);
        def(ATHROW, "athrow", "b", null, T_VOID, -1);
        def(CHECKCAST, "checkcast", "bkk", null, T_OBJECT, 0);
        def(INSTANCEOF, "instanceof", "bkk", null, T_INT, 0);
        def(MONITORENTER, "monitorenter", "b", null, T_VOID, -1);
        def(MONITOREXIT, "monitorexit", "b", null, T_VOID, -1);
        def(WIDE, "wide", "", null, T_VOID, 0);
        def(MULTIANEWARRAY, "multianewarray", "bkkc", null, T_OBJECT, 1);
        def(IFNULL, "ifnull", "boo", null, T_VOID, -1);
        def(IFNONNULL, "ifnonnull", "boo", null, T_VOID, -1);
        def(GOTO_W, "goto_w", "boooo", null, T_VOID, 0);
        def(JSR_W, "jsr_w", "boooo", null, T_INT, 0);
        def(_breakpoint, "breakpoint", "", null, T_VOID, 0);
        def(_fast_agetfield, "fast_agetfield", "bJJ", null, T_OBJECT, 0, GETFIELD);
        def(_fast_bgetfield, "fast_bgetfield", "bJJ", null, T_INT, 0, GETFIELD);
        def(_fast_cgetfield, "fast_cgetfield", "bJJ", null, T_CHAR, 0, GETFIELD);
        def(_fast_dgetfield, "fast_dgetfield", "bJJ", null, T_DOUBLE, 0, GETFIELD);
        def(_fast_fgetfield, "fast_fgetfield", "bJJ", null, T_FLOAT, 0, GETFIELD);
        def(_fast_igetfield, "fast_igetfield", "bJJ", null, T_INT, 0, GETFIELD);
        def(_fast_lgetfield, "fast_lgetfield", "bJJ", null, T_LONG, 0, GETFIELD);
        def(_fast_sgetfield, "fast_sgetfield", "bJJ", null, T_SHORT, 0, GETFIELD);
        def(_fast_aputfield, "fast_aputfield", "bJJ", null, T_OBJECT, 0, PUTFIELD);
        def(_fast_bputfield, "fast_bputfield", "bJJ", null, T_INT, 0, PUTFIELD);
        def(_fast_zputfield, "fast_zputfield", "bJJ", null, T_INT, 0, PUTFIELD);
        def(_fast_cputfield, "fast_cputfield", "bJJ", null, T_CHAR, 0, PUTFIELD);
        def(_fast_dputfield, "fast_dputfield", "bJJ", null, T_DOUBLE, 0, PUTFIELD);
        def(_fast_fputfield, "fast_fputfield", "bJJ", null, T_FLOAT, 0, PUTFIELD);
        def(_fast_iputfield, "fast_iputfield", "bJJ", null, T_INT, 0, PUTFIELD);
        def(_fast_lputfield, "fast_lputfield", "bJJ", null, T_LONG, 0, PUTFIELD);
        def(_fast_sputfield, "fast_sputfield", "bJJ", null, T_SHORT, 0, PUTFIELD);
        def(_fast_aload_0, "fast_aload_0", "b", null, T_OBJECT, 1, ALOAD_0);
        def(_fast_iaccess_0, "fast_iaccess_0", "b_JJ", null, T_INT, 1, ALOAD_0);
        def(_fast_aaccess_0, "fast_aaccess_0", "b_JJ", null, T_OBJECT, 1, ALOAD_0);
        def(_fast_faccess_0, "fast_faccess_0", "b_JJ", null, T_OBJECT, 1, ALOAD_0);
        def(_fast_iload, "fast_iload", "bi", null, T_INT, 1, ILOAD);
        def(_fast_iload2, "fast_iload2", "bi_i", null, T_INT, 2, ILOAD);
        def(_fast_icaload, "fast_icaload", "bi_", null, T_INT, 0, ILOAD);
        def(_fast_invokevfinal, "fast_invokevfinal", "bJJ", null, T_ILLEGAL, -1, INVOKEVIRTUAL);
        def(_fast_linearswitch, "fast_linearswitch", "", null, T_VOID, -1, LOOKUPSWITCH);
        def(_fast_binaryswitch, "fast_binaryswitch", "", null, T_VOID, -1, LOOKUPSWITCH);
        def(_return_register_finalizer, "return_register_finalizer", "b", null, T_VOID, 0, RETURN);
        def(_invokehandle, "invokehandle", "bJJ", null, T_ILLEGAL, -1, INVOKEVIRTUAL);
        def(_fast_aldc, "fast_aldc", "bj", null, T_OBJECT, 1, LDC);
        def(_fast_aldc_w, "fast_aldc_w", "bJJ", null, T_OBJECT, 1, LDC_W);
        def(_nofast_getfield, "nofast_getfield", "bJJ", null, T_ILLEGAL, 0, GETFIELD);
        def(_nofast_putfield, "nofast PUTFIELD", "bJJ", null, T_ILLEGAL, -2, PUTFIELD);
        def(_nofast_aload_0, "nofast_aload_0", "b", null, T_ILLEGAL, 1, ALOAD_0);
        def(_nofast_iload, "nofast_iload", "bi", null, T_ILLEGAL, 1, ILOAD);
        def(_shouldnotreachhere, "_shouldnotreachhere", "b", null, T_VOID, 0);
    }
}
