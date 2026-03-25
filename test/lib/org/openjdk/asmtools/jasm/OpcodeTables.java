/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.asmtools.jasm;

import java.util.HashMap;

/**
 *
 * OpcodeTables
 *
 * The OpcodeTables class follows a Singleton Pattern. This class contains Enums, that are
 * contained in private hash maps (lookup tables and reverse lookup tables). These hash
 * maps all have public accessors, which clients use to look-up opcodes.
 *
 * Tokens in this table carry no external state, and are typically treated as constants.
 * They do not need to be reset.
 *
 */
public class OpcodeTables {

    /**
     * Initialized keyword and token Hash Maps (and Reverse Tables)
     */
    static private final int MaxOpcodes = 301;
    static private HashMap<Integer, Opcode> IntToNormalOpcodes = new HashMap<>(MaxOpcodes);
    static private HashMap<Integer, Opcode> IntToAllOpcodes = new HashMap<>(MaxOpcodes);
    static private HashMap<String, Opcode> mnemocodes = new HashMap<>(MaxOpcodes);

    static private HashMap<Integer, Opcode> IntToPrivOpcode = new HashMap<>(MaxOpcodes);
    static private HashMap<String, Opcode> PrivMnemocodes = new HashMap<>(MaxOpcodes);

    static private HashMap<Integer, Opcode> IntToNonPrivOpcode = new HashMap<>(MaxOpcodes);
    static private HashMap<String, Opcode> NonPrivMnemocodes = new HashMap<>(MaxOpcodes);

    static {
        // register all of the tokens
        for (Opcode opc : Opcode.values()) {
            registerOpcode(opc);
        }

    }

    private static void registerOpcode(Opcode opc) {
        IntToAllOpcodes.put(opc.value, opc);
        mnemocodes.put(opc.parsekey, opc);
        if (opc.alias != null) {
            mnemocodes.put(opc.alias, opc);
        }

        if (opc.type == OpcodeType.PRIVELEGED) {
            PrivMnemocodes.put(opc.parsekey, opc);
            IntToPrivOpcode.put(opc.baseVal, opc);
        } else if (opc.type == OpcodeType.NONPRIVELEGED) {
            NonPrivMnemocodes.put(opc.parsekey, opc);
            IntToNonPrivOpcode.put(opc.baseVal, opc);
        }

    }

    public static Opcode opcode(String mnemonic) {
        return mnemocodes.get(mnemonic);
    }

    public static Opcode opcode(Integer mnem_code) {
        return IntToAllOpcodes.get(mnem_code);
    }

    /*-------------------------------------------------------- */
    /**
     * Marker: describes the type of Opcode.
     *
     * certain types of Opcodes will be added to specific lookup tables.
     */
    static public enum OpcodeType {
        NORMAL            (0, "Normal"),
        NONPRIVELEGED     (1, "NonPriv"),
        PRIVELEGED        (2, "Priv"),
        WIDE              (3, "Wide");

        private final Integer value;
        private final String printval;

        OpcodeType(Integer val, String print) {
            value = val;
            printval = print;
        }

        public String printval() {
            return printval;
        }

    }

    /*-------------------------------------------------------- */
    /* Opcode Enums */
    static public enum Opcode {
     /* Opcodes */
    opc_dead                (-2, " opc_dead", 0),
    opc_label               (-1, "opc_label", 0),
    opc_nop                 (0, "nop", 1),
    opc_aconst_null         (1, "aconst_null", 1),
    opc_iconst_m1           (2, "iconst_m1", 1),
    opc_iconst_0            (3, "iconst_0", 1),
    opc_iconst_1            (4, "iconst_1", 1),
    opc_iconst_2            (5, "iconst_2", 1),
    opc_iconst_3            (6, "iconst_3", 1),
    opc_iconst_4            (7, "iconst_4", 1),
    opc_iconst_5            (8, "iconst_5", 1),
    opc_lconst_0            (9, "lconst_0", 1),
    opc_lconst_1            (10, "lconst_1", 1),
    opc_fconst_0            (11, "fconst_0", 1),
    opc_fconst_1            (12, "fconst_1", 1),
    opc_fconst_2            (13, "fconst_2", 1),
    opc_dconst_0            (14, "dconst_0", 1),
    opc_dconst_1            (15, "dconst_1", 1),
    opc_bipush              (16, "bipush", 2),
    opc_sipush              (17, "sipush", 3),
    opc_ldc                 (18, "ldc", 2),
    opc_ldc_w               (19, "ldc_w", 3),
    opc_ldc2_w              (20, "ldc2_w", 3),
    opc_iload               (21, "iload", 2),
    opc_lload               (22, "lload", 2),
    opc_fload               (23, "fload", 2),
    opc_dload               (24, "dload", 2),
    opc_aload               (25, "aload", 2),
    opc_iload_0            (26, "iload_0", 1),
    opc_iload_1            (27, "iload_1", 1),
    opc_iload_2            (28, "iload_2", 1),
    opc_iload_3            (29, "iload_3", 1),
    opc_lload_0            (30, "lload_0", 1),
    opc_lload_1            (31, "lload_1", 1),
    opc_lload_2            (32, "lload_2", 1),
    opc_lload_3            (33, "lload_3", 1),
    opc_fload_0            (34, "fload_0", 1),
    opc_fload_1            (35, "fload_1", 1),
    opc_fload_2            (36, "fload_2", 1),
    opc_fload_3            (37, "fload_3", 1),
    opc_dload_0            (38, "dload_0", 1),
    opc_dload_1            (39, "dload_1", 1),
    opc_dload_2            (40, "dload_2", 1),
    opc_dload_3            (41, "dload_3", 1),
    opc_aload_0            (42, "aload_0", 1),
    opc_aload_1            (43, "aload_1", 1),
    opc_aload_2            (44, "aload_2", 1),
    opc_aload_3            (45, "aload_3", 1),
    opc_iaload            (46, "iaload", 1),
    opc_laload            (47, "laload", 1),
    opc_faload            (48, "faload", 1),
    opc_daload            (49, "daload", 1),
    opc_aaload            (50, "aaload", 1),
    opc_baload            (51, "baload", 1),
    opc_caload            (52, "caload", 1),
    opc_saload            (53, "saload", 1),
    opc_istore            (54, "istore", 2),
    opc_lstore            (55, "lstore", 2),
    opc_fstore            (56, "fstore", 2),
    opc_dstore            (57, "dstore", 2),
    opc_astore            (58, "astore", 2),
    opc_istore_0            (59, "istore_0", 1),
    opc_istore_1            (60, "istore_1", 1),
    opc_istore_2            (61, "istore_2", 1),
    opc_istore_3            (62, "istore_3", 1),
    opc_lstore_0            (63, "lstore_0", 1),
    opc_lstore_1            (64, "lstore_1", 1),
    opc_lstore_2            (65, "lstore_2", 1),
    opc_lstore_3            (66, "lstore_3", 1),
    opc_fstore_0            (67, "fstore_0", 1),
    opc_fstore_1            (68, "fstore_1", 1),
    opc_fstore_2            (69, "fstore_2", 1),
    opc_fstore_3            (70, "fstore_3", 1),
    opc_dstore_0            (71, "dstore_0", 1),
    opc_dstore_1            (72, "dstore_1", 1),
    opc_dstore_2            (73, "dstore_2", 1),
    opc_dstore_3            (74, "dstore_3", 1),
    opc_astore_0            (75, "astore_0", 1),
    opc_astore_1            (76, "astore_1", 1),
    opc_astore_2            (77, "astore_2", 1),
    opc_astore_3            (78, "astore_3", 1),
    opc_iastore             (79, "iastore", 1),
    opc_lastore             (80, "lastore", 1),
    opc_fastore             (81, "fastore", 1),
    opc_dastore             (82, "dastore", 1),
    opc_aastore             (83, "aastore", 1),
    opc_bastore             (84, "bastore", 1),
    opc_castore             (85, "castore", 1),
    opc_sastore             (86, "sastore", 1),
    opc_pop                 (87, "pop", 1),
    opc_pop2                (88, "pop2", 1),
    opc_dup                 (89, "dup", 1),
    opc_dup_x1              (90, "dup_x1", 1),
    opc_dup_x2              (91, "dup_x2", 1),
    opc_dup2                (92, "dup2", 1),
    opc_dup2_x1             (93, "dup2_x1", 1),
    opc_dup2_x2             (94, "dup2_x2", 1),
    opc_swap                (95, "swap", 1),
    opc_iadd                (96, "iadd", 1),
    opc_ladd                (97, "ladd", 1),
    opc_fadd                (98, "fadd", 1),
    opc_dadd                (99, "dadd", 1),
    opc_isub                (100, "isub", 1),
    opc_lsub                (101, "lsub", 1),
    opc_fsub                (102, "fsub", 1),
    opc_dsub                (103, "dsub", 1),
    opc_imul                (104, "imul", 1),
    opc_lmul                (105, "lmul", 1),
    opc_fmul                (106, "fmul", 1),
    opc_dmul                (107, "dmul", 1),
    opc_idiv                (108, "idiv", 1),
    opc_ldiv                (109, "ldiv", 1),
    opc_fdiv                (110, "fdiv", 1),
    opc_ddiv                (111, "ddiv", 1),
    opc_irem                (112, "irem", 1),
    opc_lrem                (113, "lrem", 1),
    opc_frem                (114, "frem", 1),
    opc_drem                (115, "drem", 1),
    opc_ineg                (116, "ineg", 1),
    opc_lneg                (117, "lneg", 1),
    opc_fneg                (118, "fneg", 1),
    opc_dneg                (119, "dneg", 1),
    opc_ishl                (120, "ishl", 1),
    opc_lshl                (121, "lshl", 1),
    opc_ishr                (122, "ishr", 1),
    opc_lshr                (123, "lshr", 1),
    opc_iushr               (124, "iushr", 1),
    opc_lushr               (125, "lushr", 1),
    opc_iand                (126, "iand", 1),
    opc_land                (127, "land", 1),
    opc_ior                 (128, "ior", 1),
    opc_lor                 (129, "lor", 1),
    opc_ixor                (130, "ixor", 1),
    opc_lxor                (131, "lxor", 1),
    opc_iinc                (132, "iinc", 3),
    opc_i2l                 (133, "i2l", 1),
    opc_i2f                 (134, "i2f", 1),
    opc_i2d                 (135, "i2d", 1),
    opc_l2i                 (136, "l2i", 1),
    opc_l2f                 (137, "l2f", 1),
    opc_l2d                 (138, "l2d", 1),
    opc_f2i                 (139, "f2i", 1),
    opc_f2l                 (140, "f2l", 1),
    opc_f2d                 (141, "f2d", 1),
    opc_d2i                 (142, "d2i", 1),
    opc_d2l                 (143, "d2l", 1),
    opc_d2f                 (144, "d2f", 1),
    opc_i2b                 (145, "i2b", 1),
    opc_i2c                 (146, "i2c", 1),
    opc_i2s                 (147, "i2s", 1),
    opc_lcmp                (148, "lcmp", 1),
    opc_fcmpl               (149, "fcmpl", 1),
    opc_fcmpg               (150, "fcmpg", 1),
    opc_dcmpl               (151, "dcmpl", 1),
    opc_dcmpg               (152, "dcmpg", 1),
    opc_ifeq                (153, "ifeq", 3),
    opc_ifne                (154, "ifne", 3),
    opc_iflt                (155, "iflt", 3),
    opc_ifge                (156, "ifge", 3),
    opc_ifgt                (157, "ifgt", 3),
    opc_ifle                (158, "ifle", 3),
    opc_if_icmpeq           (159, "if_icmpeq", 3),
    opc_if_icmpne           (160, "if_icmpne", 3),
    opc_if_icmplt           (161, "if_icmplt", 3),
    opc_if_icmpge           (162, "if_icmpge", 3),
    opc_if_icmpgt           (163, "if_icmpgt", 3),
    opc_if_icmple           (164, "if_icmple", 3),
    opc_if_acmpeq           (165, "if_acmpeq", 3),
    opc_if_acmpne           (166, "if_acmpne", 3),
    opc_goto                (167, "goto", 3),
    opc_jsr                 (168, "jsr", 3),
    opc_ret                 (169, "ret", 2),
    opc_tableswitch         (170, "tableswitch", 99),
    opc_lookupswitch        (171, "lookupswitch", 99),
    opc_ireturn             (172, "ireturn", 1),
    opc_lreturn             (173, "lreturn", 1),
    opc_freturn             (174, "freturn", 1),
    opc_dreturn             (175, "dreturn", 1),
    opc_areturn             (176, "areturn", 1),
    opc_return              (177, "return", 1),
    opc_getstatic           (178, "getstatic", 3),
    opc_putstatic           (179, "putstatic", 3),
    opc_getfield            (180, "getfield", 3),
    opc_putfield            (181, "putfield", 3),
    opc_invokevirtual       (182, "invokevirtual", 3),
    opc_invokespecial       (183, "invokespecial", "invokenonvirtual", 3),
    opc_invokestatic        (184, "invokestatic", 3),
    opc_invokeinterface     (185, "invokeinterface", 5),
    opc_invokedynamic       (186, "invokedynamic", 5),
    opc_new                 (187, "new", 3),
    opc_newarray            (188, "newarray", 2),
    opc_anewarray           (189, "anewarray", 3),
    opc_arraylength         (190, "arraylength", 1),
    opc_athrow              (191, "athrow", 1),
    opc_checkcast           (192, "checkcast", 3),
    opc_instanceof          (193, "instanceof", 3),
    opc_monitorenter        (194, "monitorenter", 1),
    opc_monitorexit         (195, "monitorexit", 1),

        // Wide Marker (not really an opcode)
        opc_wide            (196, null, 0),
    opc_multianewarray      (197, "multianewarray", 4),
    opc_ifnull              (198, "ifnull", 3),
    opc_ifnonnull           (199, "ifnonnull", 3),
    opc_goto_w              (200, "goto_w", 5),
    opc_jsr_w               (201, "jsr_w", 5),

        /* Pseudo-instructions */
    opc_bytecode            (210, "bytecode", 1),
    opc_try                 (211, "try", 0),
    opc_endtry              (212, "endtry", 0),
    opc_catch               (213, "catch", 0),
    opc_var                 (214, "var", 0),
    opc_endvar              (215, "endvar", 0),
    opc_locals_map          (216, "locals_map", 0),
    opc_stack_map           (217, "stack_map", 0),
    opc_stack_frame_type    (218, "stack_frame_type", 0),


        // Priv/NonPriv Marker (not really an opcode)
        opc_nonpriv         (254, "priv", 0),
        opc_priv            (255, "nonpriv", 0),


        /* Wide instructions */
        opc_iload_w                     (opc_iload.value, "iload_w", 4, OpcodeType.WIDE),
        opc_lload_w                     (opc_lload.value, "lload_w", 4, OpcodeType.WIDE),
        opc_fload_w                     (opc_fload.value, "fload_w", 4, OpcodeType.WIDE),
        opc_dload_w                     (opc_dload.value, "dload_w", 4, OpcodeType.WIDE),
        opc_aload_w                     (opc_aload.value, "aload_w", 4, OpcodeType.WIDE),
        opc_istore_w                    (opc_istore.value, "istore_w", 4, OpcodeType.WIDE),
        opc_lstore_w                    (opc_lstore.value, "lstore_w", 4, OpcodeType.WIDE),
        opc_fstore_w                    (opc_fstore.value, "fstore_w", 4, OpcodeType.WIDE),
        opc_dstore_w                    (opc_dstore.value, "dstore_w", 4, OpcodeType.WIDE),
        opc_astore_w                    (opc_astore.value, "astore_w", 4, OpcodeType.WIDE),
        opc_ret_w                       (opc_ret.value, "ret_w", 4, OpcodeType.WIDE),
        opc_iinc_w                      (opc_iinc.value, "iinc_w", 6, OpcodeType.WIDE),


        /* Priveleged instructions */
    opc_load_ubyte                  (0, "load_ubyte", OpcodeType.NONPRIVELEGED),
    opc_priv_load_ubyte        (0, "priv_load_ubyte", OpcodeType.PRIVELEGED),
    opc_load_byte            (1, "load_byte", OpcodeType.NONPRIVELEGED),
    opc_priv_load_byte        (1, "priv_load_byte", OpcodeType.PRIVELEGED),
    opc_load_char            (2, "load_char", OpcodeType.NONPRIVELEGED),
    opc_priv_load_char        (2, "priv_load_char", OpcodeType.PRIVELEGED),
    opc_load_short            (3, "load_short", OpcodeType.NONPRIVELEGED),
    opc_priv_load_short        (3, "priv_load_short", OpcodeType.PRIVELEGED),
    opc_load_word            (4, "load_word", OpcodeType.NONPRIVELEGED),
    opc_priv_load_word        (4, "priv_load_word", OpcodeType.PRIVELEGED),
    opc_load_char_oe            (10, "load_char_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_load_char_oe        (10, "priv_load_char_oe", OpcodeType.PRIVELEGED),
    opc_load_short_oe        (11, "load_short_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_load_short_oe        (11, "priv_load_short_oe", OpcodeType.PRIVELEGED),
    opc_load_word_oe            (12, "load_word_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_load_word_oe        (12, "priv_load_word_oe", OpcodeType.PRIVELEGED),
    opc_ncload_ubyte            (16, "ncload_ubyte", OpcodeType.NONPRIVELEGED),
    opc_priv_ncload_ubyte        (16, "priv_ncload_ubyte", OpcodeType.PRIVELEGED),
    opc_ncload_byte            (17, "ncload_byte", OpcodeType.NONPRIVELEGED),
    opc_priv_ncload_byte        (17, "priv_ncload_byte", OpcodeType.PRIVELEGED),
    opc_ncload_char            (18, "ncload_char", OpcodeType.NONPRIVELEGED),
    opc_priv_ncload_char        (18, "priv_ncload_char", OpcodeType.PRIVELEGED),
    opc_ncload_short            (19, "ncload_short", OpcodeType.NONPRIVELEGED),
    opc_priv_ncload_short        (19, "priv_ncload_short", OpcodeType.PRIVELEGED),
    opc_ncload_word            (20, "ncload_word", OpcodeType.NONPRIVELEGED),
    opc_priv_ncload_word        (20, "priv_ncload_word", OpcodeType.PRIVELEGED),
    opc_ncload_char_oe        (26, "ncload_char_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_ncload_char_oe        (26, "priv_ncload_char_oe", OpcodeType.PRIVELEGED),
    opc_ncload_short_oe        (27, "ncload_short_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_ncload_short_oe        (27, "priv_ncload_short_oe", OpcodeType.PRIVELEGED),
    opc_ncload_word_oe        (28, "ncload_word_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_ncload_word_oe        (28, "priv_ncload_word_oe", OpcodeType.PRIVELEGED),
    opc_cache_flush            (30, "cache_flush", OpcodeType.NONPRIVELEGED),
    opc_priv_cache_flush        (30, "priv_cache_flush", OpcodeType.PRIVELEGED),
    opc_store_byte            (32, "store_byte", OpcodeType.NONPRIVELEGED),
    opc_priv_store_byte        (32, "priv_store_byte", OpcodeType.PRIVELEGED),
    opc_store_short            (34, "store_short", OpcodeType.NONPRIVELEGED),
    opc_priv_store_short        (34, "priv_store_short", OpcodeType.PRIVELEGED),
    opc_store_word            (36, "store_word", OpcodeType.NONPRIVELEGED),
    opc_priv_store_word        (36, "priv_store_word", OpcodeType.PRIVELEGED),
    opc_store_short_oe        (42, "store_short_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_store_short_oe        (42, "priv_store_short_oe", OpcodeType.PRIVELEGED),
    opc_store_word_oe        (44, "store_word_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_store_word_oe        (44, "priv_store_word_oe", OpcodeType.PRIVELEGED),
    opc_ncstore_byte            (48, "ncstore_byte", OpcodeType.NONPRIVELEGED),
    opc_priv_ncstore_byte        (48, "priv_ncstore_byte", OpcodeType.PRIVELEGED),
    opc_ncstore_short        (50, "ncstore_short", OpcodeType.NONPRIVELEGED),
    opc_priv_ncstore_short        (50, "priv_ncstore_short", OpcodeType.PRIVELEGED),
    opc_ncstore_word        (52, "ncstore_word", OpcodeType.NONPRIVELEGED),
    opc_priv_ncstore_word        (52, "priv_ncstore_word", OpcodeType.PRIVELEGED),
    opc_ncstore_short_oe        (58, "ncstore_short_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_ncstore_short_oe    (58, "priv_ncstore_short_oe", OpcodeType.PRIVELEGED),
    opc_ncstore_word_oe        (60, "ncstore_word_oe", OpcodeType.NONPRIVELEGED),
    opc_priv_ncstore_word_oe        (60, "priv_ncstore_word_oe", OpcodeType.PRIVELEGED),
    opc_zero_line            (62, "zero_line", OpcodeType.NONPRIVELEGED),
    opc_priv_zero_line        (62, "priv_zero_line", OpcodeType.PRIVELEGED),
    opc_ret_from_sub            (5, "ret_from_sub", OpcodeType.NONPRIVELEGED),
    opc_enter_sync_method        (63, "enter_sync_method", OpcodeType.NONPRIVELEGED),
    opc_priv_ret_from_trap        (5, "priv_ret_from_trap", OpcodeType.PRIVELEGED),
    opc_priv_read_dcache_tag    (6, "priv_read_dcache_tag", OpcodeType.PRIVELEGED),
    opc_priv_read_dcache_data    (7, "priv_read_dcache_data", OpcodeType.PRIVELEGED),
    opc_priv_read_icache_tag    (14, "priv_read_icache_tag", OpcodeType.PRIVELEGED),
    opc_priv_read_icache_data    (15, "priv_read_icache_data", OpcodeType.PRIVELEGED),
    opc_priv_powerdown        (22, "priv_powerdown", OpcodeType.PRIVELEGED),
    opc_priv_read_scache_data    (23, "priv_read_scache_data", OpcodeType.PRIVELEGED),
    opc_priv_cache_index_flush    (31, "priv_cache_index_flush", OpcodeType.PRIVELEGED),
    opc_priv_write_dcache_tag    (38, "priv_write_dcache_tag", OpcodeType.PRIVELEGED),
    opc_priv_write_dcache_data    (39, "priv_write_dcache_data", OpcodeType.PRIVELEGED),
    opc_priv_write_icache_tag    (46, "priv_write_icache_tag", OpcodeType.PRIVELEGED),
    opc_priv_write_icache_data    (47, "priv_write_icache_data", OpcodeType.PRIVELEGED),
    opc_priv_reset            (54, "priv_reset", OpcodeType.PRIVELEGED),
    opc_priv_write_scache_data    (55, "priv_write_scache_data", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_0        (64, "priv_read_reg_0", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_1        (65, "priv_read_reg_1", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_2        (66, "priv_read_reg_2", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_3        (67, "priv_read_reg_3", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_4        (68, "priv_read_reg_4", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_5        (69, "priv_read_reg_5", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_6        (70, "priv_read_reg_6", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_7        (71, "priv_read_reg_7", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_8        (72, "priv_read_reg_8", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_9        (73, "priv_read_reg_9", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_10        (74, "priv_read_reg_10", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_11        (75, "priv_read_reg_11", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_12        (76, "priv_read_reg_12", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_13        (77, "priv_read_reg_13", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_14        (78, "priv_read_reg_14", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_15        (79, "priv_read_reg_15", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_16        (80, "priv_read_reg_16", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_17        (81, "priv_read_reg_17", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_18        (82, "priv_read_reg_18", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_19        (83, "priv_read_reg_19", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_20        (84, "priv_read_reg_20", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_21        (85, "priv_read_reg_21", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_22        (86, "priv_read_reg_22", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_23        (87, "priv_read_reg_23", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_24        (88, "priv_read_reg_24", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_25        (89, "priv_read_reg_25", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_26        (90, "priv_read_reg_26", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_27        (91, "priv_read_reg_27", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_28        (92, "priv_read_reg_28", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_29        (93, "priv_read_reg_29", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_30        (94, "priv_read_reg_30", OpcodeType.PRIVELEGED),
    opc_priv_read_reg_31        (95, "priv_read_reg_31", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_0        (96, "priv_write_reg_0", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_1        (97, "priv_write_reg_1", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_2        (98, "priv_write_reg_2", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_3        (99, "priv_write_reg_3", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_4        (100, "priv_write_reg_4", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_5        (101, "priv_write_reg_5", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_6        (102, "priv_write_reg_6", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_7        (103, "priv_write_reg_7", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_8        (104, "priv_write_reg_8", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_9        (105, "priv_write_reg_9", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_10        (106, "priv_write_reg_10", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_11        (107, "priv_write_reg_11", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_12        (108, "priv_write_reg_12", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_13        (109, "priv_write_reg_13", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_14        (110, "priv_write_reg_14", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_15        (111, "priv_write_reg_15", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_16        (112, "priv_write_reg_16", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_17        (113, "priv_write_reg_17", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_18        (114, "priv_write_reg_18", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_19        (115, "priv_write_reg_19", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_20        (116, "priv_write_reg_20", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_21        (117, "priv_write_reg_21", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_22        (118, "priv_write_reg_22", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_23        (119, "priv_write_reg_23", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_24        (120, "priv_write_reg_24", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_25        (121, "priv_write_reg_25", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_26        (122, "priv_write_reg_26", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_27        (123, "priv_write_reg_27", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_28        (124, "priv_write_reg_28", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_29        (125, "priv_write_reg_29", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_30        (126, "priv_write_reg_30", OpcodeType.PRIVELEGED),
    opc_priv_write_reg_31        (127, "priv_write_reg_31", OpcodeType.PRIVELEGED);

        private Integer value;
        private String parsekey;
        private String alias;
        private Integer length;
        private Integer baseVal;
        private OpcodeType type;

        Opcode(Integer val, String parse, OpcodeType tp) {
            init(val, parse, null, 2, tp);
        }

        Opcode(Integer val, String parse, int len, OpcodeType tp) {
            init(val, parse, null, len, tp);
        }

        Opcode(Integer val, String parse) {
            init(val, parse, null, 2, OpcodeType.NORMAL);
        }

        Opcode(Integer val, String parse, int len) {
            init(val, parse, null, len, OpcodeType.NORMAL);
        }

        Opcode(Integer val, String parse, String als, int len) {
            init(val, parse, als, len, OpcodeType.NORMAL);
        }

        Opcode(Integer val, String parse, String als, int len, OpcodeType tp) {
            init(val, parse, als, len, tp);
        }

        private void init(Integer val, String parse, String als, int len, OpcodeType tp) {
            type = tp;
            baseVal = null;
            switch (tp) {
                case NORMAL:
                    value = val;
                    break;
                case WIDE:
                    value = (opc_wide.value << 8) | val;
                    break;
                case PRIVELEGED:
                    value = (opc_priv.value * 0xFF) + val;
                    baseVal = val;
                    break;
                case NONPRIVELEGED:
                    value = (opc_nonpriv.value * 0xFF) + val;
                    baseVal = val;
                    break;
            }
            parsekey = parse;
            alias = als;
            length = len;
        }

        public Integer value() {
            return value;
        }

        public int length() {
            return length;
        }

        public String parsekey() {
            return parsekey;
        }

        public OpcodeType type() {
            return type;
        }
    }

}
