/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.util.jar.pack;

import java.util.Arrays;
import java.util.List;

/**
 * Shared constants
 * @author John Rose
 */
interface Constants {
    public final static int JAVA_MAGIC = 0xCAFEBABE;

    /*
        Java Class Version numbers history
        1.0 to 1.3.X 45,3
        1.4 to 1.4.X 46,0
        1.5 to 1.5.X 49,0
        1.6 to 1.5.x 50,0 NOTE Assumed for now
    */

    public final static short JAVA_MIN_CLASS_MAJOR_VERSION = 45;
    public final static short JAVA_MIN_CLASS_MINOR_VERSION = 03;

    public final static short JAVA5_MAX_CLASS_MAJOR_VERSION = 49;
    public final static short JAVA5_MAX_CLASS_MINOR_VERSION = 0;

    public final static short JAVA6_MAX_CLASS_MAJOR_VERSION = 50;
    public final static short JAVA6_MAX_CLASS_MINOR_VERSION = 0;

    public final static short JAVA7_MAX_CLASS_MAJOR_VERSION = 51;
    public final static short JAVA7_MAX_CLASS_MINOR_VERSION = 0;

    public final static int JAVA_PACKAGE_MAGIC = 0xCAFED00D;
    public final static int JAVA5_PACKAGE_MAJOR_VERSION = 150;
    public final static int JAVA5_PACKAGE_MINOR_VERSION = 7;

    public final static int JAVA6_PACKAGE_MAJOR_VERSION = 160;
    public final static int JAVA6_PACKAGE_MINOR_VERSION = 1;

    public final static int CONSTANT_POOL_INDEX_LIMIT  = 0x10000;
    public final static int CONSTANT_POOL_NARROW_LIMIT = 0x00100;

    public final static String JAVA_SIGNATURE_CHARS = "BSCIJFDZLV([";

    public final static byte CONSTANT_Utf8 = 1;
    public final static byte CONSTANT_unused2 = 2;  // unused, was Unicode
    public final static byte CONSTANT_Integer = 3;
    public final static byte CONSTANT_Float = 4;
    public final static byte CONSTANT_Long = 5;
    public final static byte CONSTANT_Double = 6;
    public final static byte CONSTANT_Class = 7;
    public final static byte CONSTANT_String = 8;
    public final static byte CONSTANT_Fieldref = 9;
    public final static byte CONSTANT_Methodref = 10;
    public final static byte CONSTANT_InterfaceMethodref = 11;
    public final static byte CONSTANT_NameandType = 12;

    // pseudo-constants:
    public final static byte CONSTANT_None = 0;
    public final static byte CONSTANT_Signature = 13;
    public final static byte CONSTANT_Limit  = 14;

    public final static byte CONSTANT_All = 19;  // combined global map
    public final static byte CONSTANT_Literal = 20; // used only for ldc fields

    // pseudo-access bits
    public final static int ACC_IC_LONG_FORM   = (1<<16); //for ic_flags

    // attribute "context types"
    public static final int ATTR_CONTEXT_CLASS  = 0;
    public static final int ATTR_CONTEXT_FIELD  = 1;
    public static final int ATTR_CONTEXT_METHOD = 2;
    public static final int ATTR_CONTEXT_CODE   = 3;
    public static final int ATTR_CONTEXT_LIMIT  = 4;
    public static final String[] ATTR_CONTEXT_NAME
        = { "class", "field", "method", "code" };

    // predefined attr bits
    public static final int
        X_ATTR_OVERFLOW = 16,
        CLASS_ATTR_SourceFile = 17,
        METHOD_ATTR_Code = 17,
        FIELD_ATTR_ConstantValue = 17,
        CLASS_ATTR_EnclosingMethod = 18,
        METHOD_ATTR_Exceptions = 18,
        X_ATTR_Signature = 19,
        X_ATTR_Deprecated = 20,
        X_ATTR_RuntimeVisibleAnnotations = 21,
        X_ATTR_RuntimeInvisibleAnnotations = 22,
        METHOD_ATTR_RuntimeVisibleParameterAnnotations = 23,
        CLASS_ATTR_InnerClasses = 23,
        METHOD_ATTR_RuntimeInvisibleParameterAnnotations = 24,
        CLASS_ATTR_ClassFile_version = 24,
        METHOD_ATTR_AnnotationDefault = 25,
        CODE_ATTR_StackMapTable = 0,  // new in Java 6
        CODE_ATTR_LineNumberTable = 1,
        CODE_ATTR_LocalVariableTable = 2,
        CODE_ATTR_LocalVariableTypeTable = 3;

    // File option bits, from LSB in ascending bit position.
    public static final int FO_DEFLATE_HINT           = 1<<0;
    public static final int FO_IS_CLASS_STUB          = 1<<1;

    // Archive option bits, from LSB in ascending bit position:
    public static final int AO_HAVE_SPECIAL_FORMATS   = 1<<0;
    public static final int AO_HAVE_CP_NUMBERS        = 1<<1;
    public static final int AO_HAVE_ALL_CODE_FLAGS    = 1<<2;
    public static final int AO_3_UNUSED_MBZ           = 1<<3;
    public static final int AO_HAVE_FILE_HEADERS      = 1<<4;
    public static final int AO_DEFLATE_HINT           = 1<<5;
    public static final int AO_HAVE_FILE_MODTIME      = 1<<6;
    public static final int AO_HAVE_FILE_OPTIONS      = 1<<7;
    public static final int AO_HAVE_FILE_SIZE_HI      = 1<<8;
    public static final int AO_HAVE_CLASS_FLAGS_HI    = 1<<9;
    public static final int AO_HAVE_FIELD_FLAGS_HI    = 1<<10;
    public static final int AO_HAVE_METHOD_FLAGS_HI   = 1<<11;
    public static final int AO_HAVE_CODE_FLAGS_HI     = 1<<12;

    public static final int LG_AO_HAVE_XXX_FLAGS_HI   = 9;

    // visitRefs modes:
    static final int VRM_CLASSIC = 0;
    static final int VRM_PACKAGE = 1;

    public static final int NO_MODTIME = 0;  // null modtime value

    // some comstantly empty containers
    public final static int[]    noInts = {};
    public final static byte[]   noBytes = {};
    public final static Object[] noValues = {};
    public final static String[] noStrings = {};
    public final static List     emptyList = Arrays.asList(noValues);

    // meta-coding
    public final static int
        _meta_default = 0,
        _meta_canon_min = 1,
        _meta_canon_max = 115,
        _meta_arb = 116,
        _meta_run = 117,
        _meta_pop = 141,
        _meta_limit = 189;

    // bytecodes
    public final static int
        _nop                  =   0, // 0x00
        _aconst_null          =   1, // 0x01
        _iconst_m1            =   2, // 0x02
        _iconst_0             =   3, // 0x03
        _iconst_1             =   4, // 0x04
        _iconst_2             =   5, // 0x05
        _iconst_3             =   6, // 0x06
        _iconst_4             =   7, // 0x07
        _iconst_5             =   8, // 0x08
        _lconst_0             =   9, // 0x09
        _lconst_1             =  10, // 0x0a
        _fconst_0             =  11, // 0x0b
        _fconst_1             =  12, // 0x0c
        _fconst_2             =  13, // 0x0d
        _dconst_0             =  14, // 0x0e
        _dconst_1             =  15, // 0x0f
        _bipush               =  16, // 0x10
        _sipush               =  17, // 0x11
        _ldc                  =  18, // 0x12
        _ldc_w                =  19, // 0x13
        _ldc2_w               =  20, // 0x14
        _iload                =  21, // 0x15
        _lload                =  22, // 0x16
        _fload                =  23, // 0x17
        _dload                =  24, // 0x18
        _aload                =  25, // 0x19
        _iload_0              =  26, // 0x1a
        _iload_1              =  27, // 0x1b
        _iload_2              =  28, // 0x1c
        _iload_3              =  29, // 0x1d
        _lload_0              =  30, // 0x1e
        _lload_1              =  31, // 0x1f
        _lload_2              =  32, // 0x20
        _lload_3              =  33, // 0x21
        _fload_0              =  34, // 0x22
        _fload_1              =  35, // 0x23
        _fload_2              =  36, // 0x24
        _fload_3              =  37, // 0x25
        _dload_0              =  38, // 0x26
        _dload_1              =  39, // 0x27
        _dload_2              =  40, // 0x28
        _dload_3              =  41, // 0x29
        _aload_0              =  42, // 0x2a
        _aload_1              =  43, // 0x2b
        _aload_2              =  44, // 0x2c
        _aload_3              =  45, // 0x2d
        _iaload               =  46, // 0x2e
        _laload               =  47, // 0x2f
        _faload               =  48, // 0x30
        _daload               =  49, // 0x31
        _aaload               =  50, // 0x32
        _baload               =  51, // 0x33
        _caload               =  52, // 0x34
        _saload               =  53, // 0x35
        _istore               =  54, // 0x36
        _lstore               =  55, // 0x37
        _fstore               =  56, // 0x38
        _dstore               =  57, // 0x39
        _astore               =  58, // 0x3a
        _istore_0             =  59, // 0x3b
        _istore_1             =  60, // 0x3c
        _istore_2             =  61, // 0x3d
        _istore_3             =  62, // 0x3e
        _lstore_0             =  63, // 0x3f
        _lstore_1             =  64, // 0x40
        _lstore_2             =  65, // 0x41
        _lstore_3             =  66, // 0x42
        _fstore_0             =  67, // 0x43
        _fstore_1             =  68, // 0x44
        _fstore_2             =  69, // 0x45
        _fstore_3             =  70, // 0x46
        _dstore_0             =  71, // 0x47
        _dstore_1             =  72, // 0x48
        _dstore_2             =  73, // 0x49
        _dstore_3             =  74, // 0x4a
        _astore_0             =  75, // 0x4b
        _astore_1             =  76, // 0x4c
        _astore_2             =  77, // 0x4d
        _astore_3             =  78, // 0x4e
        _iastore              =  79, // 0x4f
        _lastore              =  80, // 0x50
        _fastore              =  81, // 0x51
        _dastore              =  82, // 0x52
        _aastore              =  83, // 0x53
        _bastore              =  84, // 0x54
        _castore              =  85, // 0x55
        _sastore              =  86, // 0x56
        _pop                  =  87, // 0x57
        _pop2                 =  88, // 0x58
        _dup                  =  89, // 0x59
        _dup_x1               =  90, // 0x5a
        _dup_x2               =  91, // 0x5b
        _dup2                 =  92, // 0x5c
        _dup2_x1              =  93, // 0x5d
        _dup2_x2              =  94, // 0x5e
        _swap                 =  95, // 0x5f
        _iadd                 =  96, // 0x60
        _ladd                 =  97, // 0x61
        _fadd                 =  98, // 0x62
        _dadd                 =  99, // 0x63
        _isub                 = 100, // 0x64
        _lsub                 = 101, // 0x65
        _fsub                 = 102, // 0x66
        _dsub                 = 103, // 0x67
        _imul                 = 104, // 0x68
        _lmul                 = 105, // 0x69
        _fmul                 = 106, // 0x6a
        _dmul                 = 107, // 0x6b
        _idiv                 = 108, // 0x6c
        _ldiv                 = 109, // 0x6d
        _fdiv                 = 110, // 0x6e
        _ddiv                 = 111, // 0x6f
        _irem                 = 112, // 0x70
        _lrem                 = 113, // 0x71
        _frem                 = 114, // 0x72
        _drem                 = 115, // 0x73
        _ineg                 = 116, // 0x74
        _lneg                 = 117, // 0x75
        _fneg                 = 118, // 0x76
        _dneg                 = 119, // 0x77
        _ishl                 = 120, // 0x78
        _lshl                 = 121, // 0x79
        _ishr                 = 122, // 0x7a
        _lshr                 = 123, // 0x7b
        _iushr                = 124, // 0x7c
        _lushr                = 125, // 0x7d
        _iand                 = 126, // 0x7e
        _land                 = 127, // 0x7f
        _ior                  = 128, // 0x80
        _lor                  = 129, // 0x81
        _ixor                 = 130, // 0x82
        _lxor                 = 131, // 0x83
        _iinc                 = 132, // 0x84
        _i2l                  = 133, // 0x85
        _i2f                  = 134, // 0x86
        _i2d                  = 135, // 0x87
        _l2i                  = 136, // 0x88
        _l2f                  = 137, // 0x89
        _l2d                  = 138, // 0x8a
        _f2i                  = 139, // 0x8b
        _f2l                  = 140, // 0x8c
        _f2d                  = 141, // 0x8d
        _d2i                  = 142, // 0x8e
        _d2l                  = 143, // 0x8f
        _d2f                  = 144, // 0x90
        _i2b                  = 145, // 0x91
        _i2c                  = 146, // 0x92
        _i2s                  = 147, // 0x93
        _lcmp                 = 148, // 0x94
        _fcmpl                = 149, // 0x95
        _fcmpg                = 150, // 0x96
        _dcmpl                = 151, // 0x97
        _dcmpg                = 152, // 0x98
        _ifeq                 = 153, // 0x99
        _ifne                 = 154, // 0x9a
        _iflt                 = 155, // 0x9b
        _ifge                 = 156, // 0x9c
        _ifgt                 = 157, // 0x9d
        _ifle                 = 158, // 0x9e
        _if_icmpeq            = 159, // 0x9f
        _if_icmpne            = 160, // 0xa0
        _if_icmplt            = 161, // 0xa1
        _if_icmpge            = 162, // 0xa2
        _if_icmpgt            = 163, // 0xa3
        _if_icmple            = 164, // 0xa4
        _if_acmpeq            = 165, // 0xa5
        _if_acmpne            = 166, // 0xa6
        _goto                 = 167, // 0xa7
        _jsr                  = 168, // 0xa8
        _ret                  = 169, // 0xa9
        _tableswitch          = 170, // 0xaa
        _lookupswitch         = 171, // 0xab
        _ireturn              = 172, // 0xac
        _lreturn              = 173, // 0xad
        _freturn              = 174, // 0xae
        _dreturn              = 175, // 0xaf
        _areturn              = 176, // 0xb0
        _return               = 177, // 0xb1
        _getstatic            = 178, // 0xb2
        _putstatic            = 179, // 0xb3
        _getfield             = 180, // 0xb4
        _putfield             = 181, // 0xb5
        _invokevirtual        = 182, // 0xb6
        _invokespecial        = 183, // 0xb7
        _invokestatic         = 184, // 0xb8
        _invokeinterface      = 185, // 0xb9
        _xxxunusedxxx         = 186, // 0xba
        _new                  = 187, // 0xbb
        _newarray             = 188, // 0xbc
        _anewarray            = 189, // 0xbd
        _arraylength          = 190, // 0xbe
        _athrow               = 191, // 0xbf
        _checkcast            = 192, // 0xc0
        _instanceof           = 193, // 0xc1
        _monitorenter         = 194, // 0xc2
        _monitorexit          = 195, // 0xc3
        _wide                 = 196, // 0xc4
        _multianewarray       = 197, // 0xc5
        _ifnull               = 198, // 0xc6
        _ifnonnull            = 199, // 0xc7
        _goto_w               = 200, // 0xc8
        _jsr_w                = 201, // 0xc9
        _bytecode_limit       = 202; // 0xca

    // End marker, used to terminate bytecode sequences:
    public final static int _end_marker = 255;
    // Escapes:
    public final static int _byte_escape = 254;
    public final static int _ref_escape = 253;

    // Self-relative pseudo-opcodes for better compression.
    // A "linker op" is a bytecode which links to a class member.
    // (But in what follows, "invokeinterface" ops are excluded.)
    //
    // A "self linker op" is a variant bytecode which works only
    // with the current class or its super.  Because the number of
    // possible targets is small, it admits a more compact encoding.
    // Self linker ops are allowed to absorb a previous "aload_0" op.
    // There are (7 * 4) self linker ops (super or not, aload_0 or not).
    //
    // For simplicity, we define the full symmetric set of variants.
    // However, some of them are relatively useless.
    // Self linker ops are enabled by Pack.selfCallVariants (true).
    public final static int _first_linker_op = _getstatic;
    public final static int _last_linker_op  = _invokestatic;
    public final static int _num_linker_ops  = (_last_linker_op - _first_linker_op) + 1;
    public final static int _self_linker_op  = _bytecode_limit;
    public final static int _self_linker_aload_flag = 1*_num_linker_ops;
    public final static int _self_linker_super_flag = 2*_num_linker_ops;
    public final static int _self_linker_limit = _self_linker_op + 4*_num_linker_ops;
    // An "invoke init" op is a variant of invokespecial which works
    // only with the method name "<init>".  There are variants which
    // link to the current class, the super class, or the class of the
    // immediately previous "newinstance" op.  There are 3 of these ops.
    // They all take method signature references as operands.
    // Invoke init ops are enabled by Pack.initCallVariants (true).
    public final static int _invokeinit_op = _self_linker_limit;
    public final static int _invokeinit_self_option = 0;
    public final static int _invokeinit_super_option = 1;
    public final static int _invokeinit_new_option = 2;
    public final static int _invokeinit_limit = _invokeinit_op+3;

    public final static int _pseudo_instruction_limit = _invokeinit_limit;
    // linker variant limit == 202+(7*4)+3 == 233

    // Ldc variants support strongly typed references to constants.
    // This lets us index constant pool entries completely according to tag,
    // which is a great simplification.
    // Ldc variants gain us only 0.007% improvement in compression ratio,
    // but they simplify the file format greatly.
    public final static int _xldc_op = _invokeinit_limit;
    public final static int _aldc = _ldc;
    public final static int _cldc = _xldc_op+0;
    public final static int _ildc = _xldc_op+1;
    public final static int _fldc = _xldc_op+2;
    public final static int _aldc_w = _ldc_w;
    public final static int _cldc_w = _xldc_op+3;
    public final static int _ildc_w = _xldc_op+4;
    public final static int _fldc_w = _xldc_op+5;
    public final static int _lldc2_w = _ldc2_w;
    public final static int _dldc2_w = _xldc_op+6;
    public final static int _xldc_limit = _xldc_op+7;
}
