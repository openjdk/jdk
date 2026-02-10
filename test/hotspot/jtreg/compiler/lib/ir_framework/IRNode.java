/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework;

import compiler.lib.ir_framework.driver.irmatching.mapping.*;
import compiler.lib.ir_framework.driver.irmatching.parser.VMInfo;
import compiler.lib.ir_framework.shared.CheckedTestFrameworkException;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;
import jdk.test.lib.Platform;
import jdk.test.whitebox.WhiteBox;

import java.util.HashMap;
import java.util.Map;

/**
 * This class specifies IR node placeholder strings (also referred to as just "IR nodes") with mappings to regexes
 * depending on the selected compile phases. The mappings are stored in {@link #IR_NODE_MAPPINGS}. Each IR node
 * placeholder string is mapped to a {@link IRNodeMapEntry} instance defined in
 * {@link compiler.lib.ir_framework.driver.irmatching.mapping}.
 *
 * <p>
 * IR node placeholder strings can be used in {@link IR#failOn()} and/or {@link IR#counts()} attributes to define IR
 * constraints. They usually represent a single C2 IR node or a group of them.
 *
 * <p>
 * Each IR node placeholder string is accompanied by a static block that defines an IR node placeholder to regex(es)
 * mapping. The IR framework will automatically replace each IR node placeholder string in a user defined test with a
 * regex depending on the selected compile phases in {@link IR#phase} and the provided mapping.
 *
 * <p>
 * Each mapping must define a default compile phase which is applied when the user does not explicitly set the
 * {@link IR#phase()} attribute or when directly using {@link CompilePhase#DEFAULT}. In this case, the IR framework
 * falls back on the default compile phase of any {@link IRNodeMapEntry}.
 *
 * <p>
 * The IR framework reports a {@link TestFormatException} if:
 * <ul>
 *     <li><p> A user test specifies a compile phase for which no mapping is defined in this class.</li>
 *     <li><p> An IR node placeholder string is either missing a mapping or does not provide a regex for a specified
 *             compile phase in {@link IR#phase}.
 * </ul>
 *
 * <p>
 * There are two types of IR nodes:
 * <ul>
 *     <li><p>Normal IR nodes: The IR node placeholder string is directly replaced by a regex.</li>
 *     <li><p>Composite IR nodes:  The IR node placeholder string contains an additional {@link #COMPOSITE_PREFIX}.
 *                                 Using this IR node expects another user provided string in the constraint list of
 *                                 {@link IR#failOn()} and {@link IR#counts()}. They cannot be used as normal IR nodes.
 *                                 Trying to do so will result in a format violation error.</li>
 *     <li><p>Vector IR nodes:  The IR node placeholder string contains an additional {@link #VECTOR_PREFIX}.
 *                              Using this IR node, one can check for the type and size of a vector. The type must
 *                              be directly specified in {@link #vectorNode}. The size can be specified directly with
 *                              an additional argument using {@link #VECTOR_SIZE}, followed by a size tag or a comma
 *                              separated list of sizes. If the size argument is not given, then a default size of
 *                              {@link #VECTOR_SIZE_MAX} is taken, which is the number of elements that can fit in a
 *                              vector of the specified type (depends on the VM flag MaxVectorSize and CPU features).
 *                              However, when using {@link IR#failOn} or {@link IR#counts()} with comparison {@code <},
 *                              or {@code <=} or {@code =0}, the default size is {@link #VECTOR_SIZE_ANY}, allowing any
 *                              size. The motivation for these default values is that in most cases one wants to have
 *                              vectorization with maximal vector width, or no vectorization of any vector width.
 * </ul>
 */
public class IRNode {
    /**
     * Prefix for normal IR nodes.
     */
    private static final String PREFIX = "_#";
    /**
     * Prefix for composite IR nodes.
     */
    private static final String COMPOSITE_PREFIX = PREFIX + "C#";
    /**
     * Prefix for vector IR nodes.
     */
    private static final String VECTOR_PREFIX = PREFIX + "V#";

    private static final String POSTFIX = "#_";

    public static final String START = "(\\d+(\\s){2}(";
    public static final String MID = ".*)+(\\s){2}===.*";
    public static final String END = ")";

    public static final String IS_REPLACED = "#IS_REPLACED#"; // Is replaced by an additional user-defined string.

    public static final String VECTOR_SIZE = "_@";
    public static final String VECTOR_SIZE_TAG_ANY = "any";
    public static final String VECTOR_SIZE_TAG_MAX = "max_for_type";
    public static final String VECTOR_SIZE_ANY = VECTOR_SIZE + VECTOR_SIZE_TAG_ANY; // default for counts "=0" and failOn
    public static final String VECTOR_SIZE_MAX = VECTOR_SIZE + VECTOR_SIZE_TAG_MAX; // default in counts
    public static final String VECTOR_SIZE_2   = VECTOR_SIZE + "2";
    public static final String VECTOR_SIZE_4   = VECTOR_SIZE + "4";
    public static final String VECTOR_SIZE_8   = VECTOR_SIZE + "8";
    public static final String VECTOR_SIZE_16  = VECTOR_SIZE + "16";
    public static final String VECTOR_SIZE_32  = VECTOR_SIZE + "32";
    public static final String VECTOR_SIZE_64  = VECTOR_SIZE + "64";

    private static final String TYPE_BYTE   = "B";
    private static final String TYPE_CHAR   = "C";
    private static final String TYPE_SHORT  = "S";
    private static final String TYPE_INT    = "I";
    private static final String TYPE_LONG   = "J";
    private static final String TYPE_FLOAT  = "F";
    private static final String TYPE_DOUBLE = "D";

    /**
     * IR placeholder string to regex-for-compile-phase map.
     */
    private static final Map<String, IRNodeMapEntry> IR_NODE_MAPPINGS = new HashMap<>();

    /**
     * Map every vectorNode to a type string.
     */
    private static final Map<String, String> VECTOR_NODE_TYPE = new HashMap<>();

    /*
     * Start of IR placeholder string definitions followed by a static block defining the regex-for-compile-phase mapping.
     * An IR node placeholder string must start with PREFIX for normal IR nodes or COMPOSITE_PREFIX for composite IR
     * nodes, or VECTOR_PREFIX for vector nodes (see class description above).
     *
     * An IR node definition looks like this:
     *
     * public static final String IR_NODE = [PREFIX|COMPOSITE_PREFIX|VECTOR_PREFIX] + "IR_NODE" + POSTFIX;
     * static {
     *    // Define IR_NODE to regex-for-compile-phase mapping. Create a new IRNodeMapEntry object and add it to
     *    // IR_NODE_MAPPINGS. This can be done by using the helper methods defined after all IR node placeholder string
     *    // definitions.
     * }
     */

    public static final String ABS_D = PREFIX + "ABS_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(ABS_D, "AbsD");
    }

    public static final String ABS_F = PREFIX + "ABS_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(ABS_F, "AbsF");
    }

    public static final String ABS_I = PREFIX + "ABS_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(ABS_I, "AbsI");
    }

    public static final String ABS_L = PREFIX + "ABS_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(ABS_L, "AbsL");
    }

    public static final String ABS_VB = VECTOR_PREFIX + "ABS_VB" + POSTFIX;
    static {
        vectorNode(ABS_VB, "AbsVB", TYPE_BYTE);
    }

    // ABS_VC / AbsVC does not exist (char is 2 byte unsigned)

    public static final String ABS_VS = VECTOR_PREFIX + "ABS_VS" + POSTFIX;
    static {
        vectorNode(ABS_VS, "AbsVS", TYPE_SHORT);
    }

    public static final String ABS_VI = VECTOR_PREFIX + "ABS_VI" + POSTFIX;
    static {
        vectorNode(ABS_VI, "AbsVI", TYPE_INT);
    }

    public static final String ABS_VL = VECTOR_PREFIX + "ABS_VL" + POSTFIX;
    static {
        vectorNode(ABS_VL, "AbsVL", TYPE_LONG);
    }

    public static final String ABS_VF = VECTOR_PREFIX + "ABS_VF" + POSTFIX;
    static {
        vectorNode(ABS_VF, "AbsVF", TYPE_FLOAT);
    }

    public static final String ABS_VD = VECTOR_PREFIX + "ABS_VD" + POSTFIX;
    static {
        vectorNode(ABS_VD, "AbsVD", TYPE_DOUBLE);
    }

    public static final String ADD = PREFIX + "ADD" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD, "Add(I|L|F|D|P)");
    }

    public static final String ADD_F = PREFIX + "ADD_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_F, "AddF");
    }

    public static final String ADD_I = PREFIX + "ADD_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_I, "AddI");
    }

    public static final String ADD_L = PREFIX + "ADD_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_L, "AddL");
    }

    public static final String ADD_HF = PREFIX + "ADD_HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_HF, "AddHF");
    }

    public static final String ADD_P = PREFIX + "ADD_P" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_P, "AddP");
    }

    public static final String ADD_VD = VECTOR_PREFIX + "ADD_VD" + POSTFIX;
    static {
        vectorNode(ADD_VD, "AddVD", TYPE_DOUBLE);
    }

    public static final String ADD_VI = VECTOR_PREFIX + "ADD_VI" + POSTFIX;
    static {
        vectorNode(ADD_VI, "AddVI", TYPE_INT);
    }

    public static final String ADD_VHF = VECTOR_PREFIX + "ADD_VHF" + POSTFIX;
    static {
        vectorNode(ADD_VHF, "AddVHF", TYPE_SHORT);
    }

    public static final String ADD_VF = VECTOR_PREFIX + "ADD_VF" + POSTFIX;
    static {
        vectorNode(ADD_VF, "AddVF", TYPE_FLOAT);
    }

    public static final String ADD_VB = VECTOR_PREFIX + "ADD_VB" + POSTFIX;
    static {
        vectorNode(ADD_VB, "AddVB", TYPE_BYTE);
    }

    public static final String ADD_VS = VECTOR_PREFIX + "ADD_VS" + POSTFIX;
    static {
        vectorNode(ADD_VS, "AddVS", TYPE_SHORT);
    }

    public static final String ADD_VL = VECTOR_PREFIX + "ADD_VL" + POSTFIX;
    static {
        vectorNode(ADD_VL, "AddVL", TYPE_LONG);
    }

    public static final String SATURATING_ADD_VB = VECTOR_PREFIX + "SATURATING_ADD_VB" + POSTFIX;
    static {
        vectorNode(SATURATING_ADD_VB, "SaturatingAddV", TYPE_BYTE);
    }

    public static final String SATURATING_ADD_VS = VECTOR_PREFIX + "SATURATING_ADD_VS" + POSTFIX;
    static {
        vectorNode(SATURATING_ADD_VS, "SaturatingAddV", TYPE_SHORT);
    }

    public static final String SATURATING_ADD_VI = VECTOR_PREFIX + "SATURATING_ADD_VI" + POSTFIX;
    static {
        vectorNode(SATURATING_ADD_VI, "SaturatingAddV", TYPE_INT);
    }

    public static final String SATURATING_ADD_VL = VECTOR_PREFIX + "SATURATING_ADD_VL" + POSTFIX;
    static {
        vectorNode(SATURATING_ADD_VL, "SaturatingAddV", TYPE_LONG);
    }

    public static final String SATURATING_SUB_VB = VECTOR_PREFIX + "SATURATING_SUB_VB" + POSTFIX;
    static {
        vectorNode(SATURATING_SUB_VB, "SaturatingSubV", TYPE_BYTE);
    }

    public static final String SATURATING_SUB_VS = VECTOR_PREFIX + "SATURATING_SUB_VS" + POSTFIX;
    static {
        vectorNode(SATURATING_SUB_VS, "SaturatingSubV", TYPE_SHORT);
    }

    public static final String SATURATING_SUB_VI = VECTOR_PREFIX + "SATURATING_SUB_VI" + POSTFIX;
    static {
        vectorNode(SATURATING_SUB_VI, "SaturatingSubV", TYPE_INT);
    }

    public static final String SATURATING_SUB_VL = VECTOR_PREFIX + "SATURATING_SUB_VL" + POSTFIX;
    static {
        vectorNode(SATURATING_SUB_VL, "SaturatingSubV", TYPE_LONG);
    }

    public static final String ADD_REDUCTION_V = PREFIX + "ADD_REDUCTION_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_REDUCTION_V, "AddReductionV(B|S|I|L|F|D)");
    }

    public static final String ADD_REDUCTION_VD = PREFIX + "ADD_REDUCTION_VD" + POSTFIX;
    static {
        superWordNodes(ADD_REDUCTION_VD, "AddReductionVD");
    }

    public static final String ADD_REDUCTION_VF = PREFIX + "ADD_REDUCTION_VF" + POSTFIX;
    static {
        superWordNodes(ADD_REDUCTION_VF, "AddReductionVF");
    }

    public static final String ADD_REDUCTION_VI = PREFIX + "ADD_REDUCTION_VI" + POSTFIX;
    static {
        superWordNodes(ADD_REDUCTION_VI, "AddReductionVI");
    }

    public static final String ADD_REDUCTION_VL = PREFIX + "ADD_REDUCTION_VL" + POSTFIX;
    static {
        superWordNodes(ADD_REDUCTION_VL, "AddReductionVL");
    }

    public static final String OPAQUE_MULTIVERSIONING = PREFIX + "OPAQUE_MULTIVERSIONING" + POSTFIX;
    static {
        beforeMatchingNameRegex(OPAQUE_MULTIVERSIONING, "OpaqueMultiversioning");
    }

    public static final String REARRANGE_VB = VECTOR_PREFIX + "REARRANGE_VB" + POSTFIX;
    static {
        vectorNode(REARRANGE_VB, "VectorRearrange", TYPE_BYTE);
    }

    public static final String REARRANGE_VS = VECTOR_PREFIX + "REARRANGE_VS" + POSTFIX;
    static {
        vectorNode(REARRANGE_VS, "VectorRearrange", TYPE_SHORT);
    }

    public static final String REARRANGE_VI = VECTOR_PREFIX + "REARRANGE_VI" + POSTFIX;
    static {
        vectorNode(REARRANGE_VI, "VectorRearrange", TYPE_INT);
    }

    public static final String REARRANGE_VL = VECTOR_PREFIX + "REARRANGE_VL" + POSTFIX;
    static {
        vectorNode(REARRANGE_VL, "VectorRearrange", TYPE_LONG);
    }

    public static final String REARRANGE_VF = VECTOR_PREFIX + "REARRANGE_VF" + POSTFIX;
    static {
        vectorNode(REARRANGE_VF, "VectorRearrange", TYPE_FLOAT);
    }

    public static final String REARRANGE_VD = VECTOR_PREFIX + "REARRANGE_VD" + POSTFIX;
    static {
        vectorNode(REARRANGE_VD, "VectorRearrange", TYPE_DOUBLE);
    }

    public static final String ADD_P_OF = COMPOSITE_PREFIX + "ADD_P_OF" + POSTFIX;
    static {
        String regex = START + "addP_" + IS_REPLACED + MID + ".*" + END;
        machOnly(ADD_P_OF, regex);
    }

    public static final String ALLOC = PREFIX + "ALLOC" + POSTFIX;
    static {
        String regex = START + "Allocate\\b" + MID + END;
        macroNodes(ALLOC, regex);
    }

    public static final String ALLOC_OF = COMPOSITE_PREFIX + "ALLOC_OF" + POSTFIX;
    static {
        String regex = START + "Allocate\\b" + MID + "allocationKlass:.*\\b" + IS_REPLACED + "\\s.*" + END;
        macroNodes(ALLOC_OF, regex);
    }

    public static final String ALLOC_ARRAY = PREFIX + "ALLOC_ARRAY" + POSTFIX;
    static {
        String regex = START + "AllocateArray\\b" + MID + END;
        macroNodes(ALLOC_ARRAY,  regex);
    }

    public static final String ALLOC_ARRAY_OF = COMPOSITE_PREFIX + "ALLOC_ARRAY_OF" + POSTFIX;
    static {
        // Assuming we are looking for an array of "some/package/MyClass". The printout is
        // [Lsome/package/MyClass;
        // or, with more dimensions
        // [[[Lsome/package/MyClass;

        // Case where the searched string is a not fully qualified name (but maybe partially qualified):
        // package/MyClass or MyClass
        // The ".*\\b" will eat the "some/" and "some/package/" resp.
        String partial_name_prefix = ".+\\b";

        // The thing after "allocationKlass:" (the name of the allocated class) is a sequence of:
        // - a non-empty sequence of "["
        // - a single character ("L"),
        // - maybe a non-empty sequence of characters ending on a word boundary
        //   this sequence is omitted if the given name is already fully qualified (exact match)
        //   but will eat the package path prefix in the cases described above
        // - the name we are looking for
        // - the final ";".
        String name_part = "\\[+.(" + partial_name_prefix + ")?" + IS_REPLACED + ";";
        String regex = START + "AllocateArray\\b" + MID + "allocationKlass:" + name_part + ".*" + END;
        macroNodes(ALLOC_ARRAY_OF, regex);
    }

    public static final String OR = PREFIX + "OR" + POSTFIX;
    static {
        beforeMatchingNameRegex(OR, "Or(I|L)");
    }

    public static final String AND = PREFIX + "AND" + POSTFIX;
    static {
        beforeMatchingNameRegex(AND, "And(I|L)");
    }

    public static final String AND_I = PREFIX + "AND_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(AND_I, "AndI");
    }

    public static final String AND_L = PREFIX + "AND_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(AND_L, "AndL");
    }

    public static final String AND_VB = VECTOR_PREFIX + "AND_VB" + POSTFIX;
    static {
        vectorNode(AND_VB, "AndV", TYPE_BYTE);
    }

    public static final String AND_VC = VECTOR_PREFIX + "AND_VC" + POSTFIX;
    static {
        vectorNode(AND_VC, "AndV", TYPE_CHAR);
    }

    public static final String AND_VS = VECTOR_PREFIX + "AND_VS" + POSTFIX;
    static {
        vectorNode(AND_VS, "AndV", TYPE_SHORT);
    }

    public static final String AND_VI = VECTOR_PREFIX + "AND_VI" + POSTFIX;
    static {
        vectorNode(AND_VI, "AndV", TYPE_INT);
    }

    public static final String AND_VL = VECTOR_PREFIX + "AND_VL" + POSTFIX;
    static {
        vectorNode(AND_VL, "AndV", TYPE_LONG);
    }

    public static final String AND_V_MASK = PREFIX + "AND_V_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(AND_V_MASK, "AndVMask");
    }

    public static final String AND_REDUCTION_V = PREFIX + "AND_REDUCTION_V" + POSTFIX;
    static {
        superWordNodes(AND_REDUCTION_V, "AndReductionV");
    }

    public static final String CALL = PREFIX + "CALL" + POSTFIX;
    static {
        beforeMatchingNameRegex(CALL, "Call.*Java");
    }

    public static final String CALL_OF = COMPOSITE_PREFIX + "CALL_OF" + POSTFIX;
    static {
        callOfNodes(CALL_OF, "Call.*");
    }

    public static final String CALL_OF_METHOD = COMPOSITE_PREFIX + "CALL_OF_METHOD" + POSTFIX;
    static {
        callOfNodes(CALL_OF_METHOD, "Call.*Java");
    }

    public static final String STATIC_CALL_OF_METHOD = COMPOSITE_PREFIX + "STATIC_CALL_OF_METHOD" + POSTFIX;
    static {
        callOfNodes(STATIC_CALL_OF_METHOD, "CallStaticJava");
    }

    public static final String CAST_II = PREFIX + "CAST_II" + POSTFIX;
    static {
        beforeMatchingNameRegex(CAST_II, "CastII");
    }

    public static final String CAST_LL = PREFIX + "CAST_LL" + POSTFIX;
    static {
        beforeMatchingNameRegex(CAST_LL, "CastLL");
    }

    public static final String CBNZW_HI = PREFIX + "CBNZW_HI" + POSTFIX;
    static {
        optoOnly(CBNZW_HI, "cbwhi");
    }

    public static final String CBZW_LS = PREFIX + "CBZW_LS" + POSTFIX;
    static {
        optoOnly(CBZW_LS, "cbwls");
    }

    public static final String CBZ_LS = PREFIX + "CBZ_LS" + POSTFIX;
    static {
        optoOnly(CBZ_LS, "cbls");
    }

    public static final String CBZ_HI = PREFIX + "CBZ_HI" + POSTFIX;
    static {
        optoOnly(CBZ_HI, "cbhi");
    }

    public static final String CHECKCAST_ARRAY = PREFIX + "CHECKCAST_ARRAY" + POSTFIX;
    static {
        String regex = "(((?i:cmp|CLFI|CLR).*aryklassptr:\\[.*:Constant|.*(?i:mov|mv|or).*aryklassptr:\\[.*:Constant.*\\R.*(cmp|CMP|CLR))" + END;
        optoOnly(CHECKCAST_ARRAY, regex);
    }

    public static final String CHECKCAST_ARRAY_OF = COMPOSITE_PREFIX + "CHECKCAST_ARRAY_OF" + POSTFIX;
    static {
        String regex = "(((?i:cmp|CLFI|CLR).*aryklassptr:\\[.*" + IS_REPLACED + ":.*:Constant|.*(?i:mov|mv|or).*aryklassptr:\\[.*" + IS_REPLACED + ":.*:Constant.*\\R.*(cmp|CMP|CLR))" + END;
        optoOnly(CHECKCAST_ARRAY_OF, regex);
    }

    // Does not work on s390 (a rule containing this regex will be skipped on s390).
    public static final String CHECKCAST_ARRAYCOPY = PREFIX + "CHECKCAST_ARRAYCOPY" + POSTFIX;
    static {
        String regex = "(.*((?i:call_leaf_nofp,runtime)|CALL,\\s?runtime leaf nofp|BCTRL.*.leaf call).*checkcast_arraycopy.*" + END;
        optoOnly(CHECKCAST_ARRAYCOPY, regex);
    }

    public static final String CLASS_CHECK_TRAP = PREFIX + "CLASS_CHECK_TRAP" + POSTFIX;
    static {
        trapNodes(CLASS_CHECK_TRAP, "class_check");
    }

    public static final String CMOVE_F = PREFIX + "CMOVE_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMOVE_F, "CMoveF");
    }

    public static final String CMOVE_D = PREFIX + "CMOVE_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMOVE_D, "CMoveD");
    }

    public static final String CMOVE_I = PREFIX + "CMOVE_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMOVE_I, "CMoveI");
    }

    public static final String CMOVE_L = PREFIX + "CMOVE_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMOVE_L, "CMoveL");
    }

    public static final String CMP_F = PREFIX + "CMP_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_F, "CmpF");
    }

    public static final String CMP_D = PREFIX + "CMP_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_D, "CmpD");
    }

    public static final String CMP_I = PREFIX + "CMP_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_I, "CmpI");
    }

    public static final String CMP_L = PREFIX + "CMP_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_L, "CmpL");
    }

    public static final String CMP_U = PREFIX + "CMP_U" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_U, "CmpU\\b");
    }

    public static final String CMP_U3 = PREFIX + "CMP_U3" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_U3, "CmpU3");
    }

    public static final String CMP_UL = PREFIX + "CMP_UL" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_UL, "CmpUL");
    }

    public static final String CMP_UL3 = PREFIX + "CMP_UL3" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_UL3, "CmpUL3");
    }

    public static final String CMP_P = PREFIX + "CMP_P" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_P, "CmpP");
    }

    public static final String CMP_N = PREFIX + "CMP_N" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_N, "CmpN");
    }

    public static final String CMP_LT_MASK = PREFIX + "CMP_LT_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMP_LT_MASK, "CmpLTMask");
    }

    public static final String ROUND_F = PREFIX + "ROUND_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(ROUND_F, "RoundF");
    }

    public static final String ROUND_D = PREFIX + "ROUND_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(ROUND_D, "RoundD");
    }

    public static final String COMPRESS_BITS = PREFIX + "COMPRESS_BITS" + POSTFIX;
    static {
        beforeMatchingNameRegex(COMPRESS_BITS, "CompressBits");
    }

    public static final String CONV = PREFIX + "CONV" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV, "Conv");
    }

    public static final String CONV_D2I = PREFIX + "CONV_D2I" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_D2I, "ConvD2I");
    }

    public static final String CONV_D2L = PREFIX + "CONV_D2L" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_D2L, "ConvD2L");
    }

    public static final String CONV_F2HF = PREFIX + "CONV_F2HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_F2HF, "ConvF2HF");
    }

    public static final String CONV_F2I = PREFIX + "CONV_F2I" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_F2I, "ConvF2I");
    }

    public static final String CONV_F2L = PREFIX + "CONV_F2L" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_F2L, "ConvF2L");
    }

    public static final String CONV_I2L = PREFIX + "CONV_I2L" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_I2L, "ConvI2L");
    }

    public static final String CONV_L2I = PREFIX + "CONV_L2I" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_L2I, "ConvL2I");
    }

    public static final String CONV_HF2F = PREFIX + "CONV_HF2F" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_HF2F, "ConvHF2F");
    }

    public static final String CON_I = PREFIX + "CON_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(CON_I, "ConI");
    }

    public static final String CON_L = PREFIX + "CON_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(CON_L, "ConL");
    }

    public static final String COUNTED_LOOP = PREFIX + "COUNTED_LOOP" + POSTFIX;
    static {
        String regex = START + "CountedLoop\\b" + MID + END;
        fromAfterCountedLoops(COUNTED_LOOP, regex);
    }

    public static final String COUNTED_LOOP_MAIN = PREFIX + "COUNTED_LOOP_MAIN" + POSTFIX;
    static {
        String regex = START + "CountedLoop\\b" + MID + "main" + END;
        fromAfterCountedLoops(COUNTED_LOOP_MAIN, regex);
    }

    public static final String DECODE_HEAP_OOP_NOT_NULL = PREFIX + "DECODE_HEAP_OOP_NOT_NULL" + POSTFIX;
    static {
        machOnly(DECODE_HEAP_OOP_NOT_NULL, "decodeHeapOop_not_null");
    }

    public static final String DIV = PREFIX + "DIV" + POSTFIX;
    static {
        beforeMatchingNameRegex(DIV, "Div(I|L|F|D)");
    }

    public static final String UDIV = PREFIX + "UDIV" + POSTFIX;
    static {
        beforeMatchingNameRegex(UDIV, "UDiv(I|L|F|D)");
    }

    public static final String DIV_BY_ZERO_TRAP = PREFIX + "DIV_BY_ZERO_TRAP" + POSTFIX;
    static {
        trapNodes(DIV_BY_ZERO_TRAP, "div0_check");
    }

    public static final String DIV_I = PREFIX + "DIV_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(DIV_I, "DivI");
    }

    public static final String DIV_L = PREFIX + "DIV_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(DIV_L, "DivL");
    }

    public static final String DIV_MOD_I = PREFIX + "DIV_MOD_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(DIV_MOD_I, "DivModI");
    }

    public static final String DIV_MOD_L = PREFIX + "DIV_MOD_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(DIV_MOD_L, "DivModL");
    }

    public static final String DIV_VHF = VECTOR_PREFIX + "DIV_VHF" + POSTFIX;
    static {
        vectorNode(DIV_VHF, "DivVHF", TYPE_SHORT);
    }

    public static final String DIV_VF = VECTOR_PREFIX + "DIV_VF" + POSTFIX;
    static {
        vectorNode(DIV_VF, "DivVF", TYPE_FLOAT);
    }

    public static final String DIV_VD = VECTOR_PREFIX + "DIV_VD" + POSTFIX;
    static {
        vectorNode(DIV_VD, "DivVD", TYPE_DOUBLE);
    }

    public static final String DYNAMIC_CALL_OF_METHOD = COMPOSITE_PREFIX + "DYNAMIC_CALL_OF_METHOD" + POSTFIX;
    static {
        callOfNodes(DYNAMIC_CALL_OF_METHOD, "CallDynamicJava");
    }

    public static final String EXPAND_BITS = PREFIX + "EXPAND_BITS" + POSTFIX;
    static {
        beforeMatchingNameRegex(EXPAND_BITS, "ExpandBits");
    }

    public static final String FAST_LOCK = PREFIX + "FAST_LOCK" + POSTFIX;
    static {
        beforeMatchingNameRegex(FAST_LOCK, "FastLock");
    }

    public static final String FAST_UNLOCK = PREFIX + "FAST_UNLOCK" + POSTFIX;
    static {
        String regex = START + "FastUnlock" + MID + END;
        fromMacroToBeforeMatching(FAST_UNLOCK, regex);
    }

    public static final String FIELD_ACCESS = PREFIX + "FIELD_ACCESS" + POSTFIX;
    static {
        String regex = "(.*Field: *" + END;
        optoOnly(FIELD_ACCESS, regex);
    }

    public static final String FMA_VHF = VECTOR_PREFIX + "FMA_VHF" + POSTFIX;
    static {
        vectorNode(FMA_VHF, "FmaVHF", TYPE_SHORT);
    }

    public static final String FMA_VF = VECTOR_PREFIX + "FMA_VF" + POSTFIX;
    static {
        vectorNode(FMA_VF, "FmaVF", TYPE_FLOAT);
    }

    public static final String FMA_VD = VECTOR_PREFIX + "FMA_VD" + POSTFIX;
    static {
        vectorNode(FMA_VD, "FmaVD", TYPE_DOUBLE);
    }

    public static final String FMA_HF = PREFIX + "FMA_HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(FMA_HF, "FmaHF");
    }

    public static final String G1_COMPARE_AND_EXCHANGE_N_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_COMPARE_AND_EXCHANGE_N_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1CompareAndExchangeN\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_COMPARE_AND_EXCHANGE_N_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_COMPARE_AND_EXCHANGE_P_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_COMPARE_AND_EXCHANGE_P_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1CompareAndExchangeP\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_COMPARE_AND_EXCHANGE_P_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_COMPARE_AND_SWAP_N_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_COMPARE_AND_SWAP_N_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1CompareAndSwapN\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_COMPARE_AND_SWAP_N_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1CompareAndSwapP\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_ENCODE_P_AND_STORE_N = PREFIX + "G1_ENCODE_P_AND_STORE_N" + POSTFIX;
    static {
        machOnlyNameRegex(G1_ENCODE_P_AND_STORE_N, "g1EncodePAndStoreN");
    }

    public static final String G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1EncodePAndStoreN\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_GET_AND_SET_N_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_GET_AND_SET_N_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1GetAndSetN\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_GET_AND_SET_N_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_GET_AND_SET_P_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_GET_AND_SET_P_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1GetAndSetP\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_GET_AND_SET_P_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_LOAD_N = PREFIX + "G1_LOAD_N" + POSTFIX;
    static {
        machOnlyNameRegex(G1_LOAD_N, "g1LoadN");
    }

    public static final String G1_LOAD_N_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_LOAD_N_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1LoadN\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_LOAD_N_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_LOAD_P_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_LOAD_P_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1LoadP\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_LOAD_P_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_STORE_N = PREFIX + "G1_STORE_N" + POSTFIX;
    static {
        machOnlyNameRegex(G1_STORE_N, "g1StoreN");
    }

    public static final String G1_STORE_N_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_STORE_N_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1StoreN\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_STORE_N_WITH_BARRIER_FLAG, regex);
    }

    public static final String G1_STORE_P = PREFIX + "G1_STORE_P" + POSTFIX;
    static {
        machOnlyNameRegex(G1_STORE_P, "g1StoreP");
    }

    public static final String G1_STORE_P_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "G1_STORE_P_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "g1StoreP\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(G1_STORE_P_WITH_BARRIER_FLAG, regex);
    }

    public static final String IF = PREFIX + "IF" + POSTFIX;
    static {
        beforeMatchingNameRegex(IF, "If\\b");
    }

    // Does not work for VM builds without JVMCI like x86_32 (a rule containing this regex will be skipped without having JVMCI built).
    public static final String INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP = PREFIX + "INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP" + POSTFIX;
    static {
        trapNodes(INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, "intrinsic_or_type_checked_inlining");
    }

    public static final String INTRINSIC_TRAP = PREFIX + "INTRINSIC_TRAP" + POSTFIX;
    static {
        trapNodes(INTRINSIC_TRAP, "intrinsic");
    }

    // Is only supported on riscv64.
    public static final String IS_FINITE_D = PREFIX + "IS_FINITE_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(IS_FINITE_D, "IsFiniteD");
    }

    // Is only supported on riscv64.
    public static final String IS_FINITE_F = PREFIX + "IS_FINITE_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(IS_FINITE_F, "IsFiniteF");
    }

    public static final String IS_INFINITE_D = PREFIX + "IS_INFINITE_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(IS_INFINITE_D, "IsInfiniteD");
    }

    public static final String IS_INFINITE_F = PREFIX + "IS_INFINITE_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(IS_INFINITE_F, "IsInfiniteF");
    }

    // Only supported on x86.
    public static final String LEA_P = PREFIX + "LEA_P" + POSTFIX;
    static {
        machOnly(LEA_P, "leaP(CompressedOopOffset|(8|32)Narrow)");
    }

    public static final String LOAD = PREFIX + "LOAD" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD, "Load(B|UB|S|US|I|L|F|D|P|N)");
    }

    public static final String LOAD_OF_CLASS = COMPOSITE_PREFIX + "LOAD_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_OF_CLASS, "Load(B|UB|S|US|I|L|F|D|P|N)");
    }

    public static final String LOAD_B = PREFIX + "LOAD_B" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_B, "LoadB");
    }

    public static final String LOAD_B_OF_CLASS = COMPOSITE_PREFIX + "LOAD_B_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_B_OF_CLASS, "LoadB");
    }

    public static final String LOAD_D = PREFIX + "LOAD_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_D, "LoadD");
    }

    public static final String LOAD_D_OF_CLASS = COMPOSITE_PREFIX + "LOAD_D_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_D_OF_CLASS, "LoadD");
    }

    public static final String LOAD_F = PREFIX + "LOAD_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_F, "LoadF");
    }

    public static final String LOAD_F_OF_CLASS = COMPOSITE_PREFIX + "LOAD_F_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_F_OF_CLASS, "LoadF");
    }

    public static final String LOAD_I = PREFIX + "LOAD_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_I, "LoadI");
    }

    public static final String LOAD_I_OF_CLASS = COMPOSITE_PREFIX + "LOAD_I_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_I_OF_CLASS, "LoadI");
    }

    public static final String LOAD_KLASS = PREFIX + "LOAD_KLASS" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_KLASS, "LoadKlass");
    }

    public static final String LOAD_NKLASS = PREFIX + "LOAD_NKLASS" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_NKLASS, "LoadNKlass");
    }

    public static final String LOAD_KLASS_OR_NKLASS = PREFIX + "LOAD_KLASS_OR_NKLASS" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_KLASS_OR_NKLASS, "LoadN?Klass");
    }

    public static final String LOAD_L = PREFIX + "LOAD_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_L, "LoadL");
    }

    public static final String LOAD_L_OF_CLASS = COMPOSITE_PREFIX + "LOAD_L_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_L_OF_CLASS, "LoadL");
    }

    public static final String LOAD_N = PREFIX + "LOAD_N" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_N, "LoadN");
    }

    public static final String LOAD_N_OF_CLASS = COMPOSITE_PREFIX + "LOAD_N_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_N_OF_CLASS, "LoadN");
    }

    public static final String LOAD_OF_FIELD = COMPOSITE_PREFIX + "LOAD_OF_FIELD" + POSTFIX;
    static {
        String regex = START + "Load(B|C|S|I|L|F|D|P|N)" + MID + "@.*name=" + IS_REPLACED + ",.*" + END;
        beforeMatching(LOAD_OF_FIELD, regex);
    }

    public static final String LOAD_P = PREFIX + "LOAD_P" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_P, "LoadP");
    }

    public static final String LOAD_P_OF_CLASS = COMPOSITE_PREFIX + "LOAD_P_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_P_OF_CLASS, "LoadP");
    }

    public static final String LOAD_S = PREFIX + "LOAD_S" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_S, "LoadS");
    }

    public static final String LOAD_S_OF_CLASS = COMPOSITE_PREFIX + "LOAD_S_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_S_OF_CLASS, "LoadS");
    }

    public static final String LOAD_UB = PREFIX + "LOAD_UB" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_UB, "LoadUB");
    }

    public static final String LOAD_UB_OF_CLASS = COMPOSITE_PREFIX + "LOAD_UB_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_UB_OF_CLASS, "LoadUB");
    }

    public static final String LOAD_US = PREFIX + "LOAD_US" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_US, "LoadUS");
    }

    public static final String LOAD_US_OF_CLASS = COMPOSITE_PREFIX + "LOAD_US_OF_CLASS" + POSTFIX;
    static {
        loadOfNodes(LOAD_US_OF_CLASS, "LoadUS");
    }

    public static final String LOAD_VECTOR_B = VECTOR_PREFIX + "LOAD_VECTOR_B" + POSTFIX;
    static {
        vectorNode(LOAD_VECTOR_B, "LoadVector", TYPE_BYTE);
    }

    public static final String LOAD_VECTOR_C = VECTOR_PREFIX + "LOAD_VECTOR_C" + POSTFIX;
    static {
        vectorNode(LOAD_VECTOR_C, "LoadVector", TYPE_CHAR);
    }

    public static final String LOAD_VECTOR_S = VECTOR_PREFIX + "LOAD_VECTOR_S" + POSTFIX;
    static {
        vectorNode(LOAD_VECTOR_S, "LoadVector", TYPE_SHORT);
    }

    public static final String LOAD_VECTOR_I = VECTOR_PREFIX + "LOAD_VECTOR_I" + POSTFIX;
    static {
        vectorNode(LOAD_VECTOR_I, "LoadVector", TYPE_INT);
    }

    public static final String LOAD_VECTOR_L = VECTOR_PREFIX + "LOAD_VECTOR_L" + POSTFIX;
    static {
        vectorNode(LOAD_VECTOR_L, "LoadVector", TYPE_LONG);
    }

    public static final String LOAD_VECTOR_F = VECTOR_PREFIX + "LOAD_VECTOR_F" + POSTFIX;
    static {
        vectorNode(LOAD_VECTOR_F, "LoadVector", TYPE_FLOAT);
    }

    public static final String LOAD_VECTOR_D = VECTOR_PREFIX + "LOAD_VECTOR_D" + POSTFIX;
    static {
        vectorNode(LOAD_VECTOR_D, "LoadVector", TYPE_DOUBLE);
    }

    public static final String LOAD_VECTOR_GATHER = PREFIX + "LOAD_VECTOR_GATHER" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_VECTOR_GATHER, "LoadVectorGather");
    }

    public static final String LOAD_VECTOR_MASKED = PREFIX + "LOAD_VECTOR_MASKED" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_VECTOR_MASKED, "LoadVectorMasked");
    }

    public static final String LOAD_VECTOR_GATHER_MASKED = PREFIX + "LOAD_VECTOR_GATHER_MASKED" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_VECTOR_GATHER_MASKED, "LoadVectorGatherMasked");
    }

    public static final String LONG_COUNTED_LOOP = PREFIX + "LONG_COUNTED_LOOP" + POSTFIX;
    static {
        String regex = START + "LongCountedLoop\\b" + MID + END;
        fromAfterCountedLoops(LONG_COUNTED_LOOP, regex);
    }

    public static final String LOOP = PREFIX + "LOOP" + POSTFIX;
    static {
        String regex = START + "Loop" + MID + END;
        fromBeforeCountedLoops(LOOP, regex);
    }

    public static final String LSHIFT = PREFIX + "LSHIFT" + POSTFIX;
    static {
        beforeMatchingNameRegex(LSHIFT, "LShift(I|L)");
    }

    public static final String LSHIFT_I = PREFIX + "LSHIFT_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(LSHIFT_I, "LShiftI");
    }

    public static final String LSHIFT_L = PREFIX + "LSHIFT_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(LSHIFT_L, "LShiftL");
    }

    public static final String LSHIFT_VB = VECTOR_PREFIX + "LSHIFT_VB" + POSTFIX;
    static {
        vectorNode(LSHIFT_VB, "LShiftVB", TYPE_BYTE);
    }

    public static final String LSHIFT_VS = VECTOR_PREFIX + "LSHIFT_VS" + POSTFIX;
    static {
        vectorNode(LSHIFT_VS, "LShiftVS", TYPE_SHORT);
    }

    public static final String LSHIFT_VC = VECTOR_PREFIX + "LSHIFT_VC" + POSTFIX;
    static {
        vectorNode(LSHIFT_VC, "LShiftVS", TYPE_CHAR); // using short op with char type
    }

    public static final String LSHIFT_VI = VECTOR_PREFIX + "LSHIFT_VI" + POSTFIX;
    static {
        vectorNode(LSHIFT_VI, "LShiftVI", TYPE_INT);
    }

    public static final String LSHIFT_VL = VECTOR_PREFIX + "LSHIFT_VL" + POSTFIX;
    static {
        vectorNode(LSHIFT_VL, "LShiftVL", TYPE_LONG);
    }

    public static final String MACH_TEMP = PREFIX + "MACH_TEMP" + POSTFIX;
    static {
        machOnlyNameRegex(MACH_TEMP, "MachTemp");
    }

    public static final String MACRO_LOGIC_V = PREFIX + "MACRO_LOGIC_V" + POSTFIX;
    static {
        afterBarrierExpansionToBeforeMatching(MACRO_LOGIC_V, "MacroLogicV");
    }

    public static final String MAX = PREFIX + "MAX" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX, "Max(I|L|F|D)");
    }

    public static final String MAX_D = PREFIX + "MAX_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX_D, "MaxD");
    }

    public static final String MAX_D_REDUCTION_REG = PREFIX + "MAX_D_REDUCTION_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MAX_D_REDUCTION_REG, "maxD_reduction_reg");
    }

    public static final String MAX_D_REG = PREFIX + "MAX_D_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MAX_D_REG, "maxD_reg");
    }

    public static final String MAX_F = PREFIX + "MAX_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX_F, "MaxF");
    }

    public static final String MAX_F_REDUCTION_REG = PREFIX + "MAX_F_REDUCTION_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MAX_F_REDUCTION_REG, "maxF_reduction_reg");
    }

    public static final String MAX_F_REG = PREFIX + "MAX_F_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MAX_F_REG, "maxF_reg");
    }

    public static final String MAX_I = PREFIX + "MAX_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX_I, "MaxI");
    }

    public static final String MAX_L = PREFIX + "MAX_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX_L, "MaxL");
    }

    public static final String MAX_VI = VECTOR_PREFIX + "MAX_VI" + POSTFIX;
    static {
        vectorNode(MAX_VI, "MaxV", TYPE_INT);
    }

    public static final String MAX_VHF = VECTOR_PREFIX + "MAX_VHF" + POSTFIX;
    static {
        vectorNode(MAX_VHF, "MaxVHF", TYPE_SHORT);
    }

    public static final String MAX_VF = VECTOR_PREFIX + "MAX_VF" + POSTFIX;
    static {
        vectorNode(MAX_VF, "MaxV", TYPE_FLOAT);
    }

    public static final String MAX_VD = VECTOR_PREFIX + "MAX_VD" + POSTFIX;
    static {
        vectorNode(MAX_VD, "MaxV", TYPE_DOUBLE);
    }

    public static final String MAX_VL = VECTOR_PREFIX + "MAX_VL" + POSTFIX;
    static {
        vectorNode(MAX_VL, "MaxV", TYPE_LONG);
    }

    public static final String MEMBAR = PREFIX + "MEMBAR" + POSTFIX;
    static {
        beforeMatchingNameRegex(MEMBAR, "MemBar");
    }

    public static final String MEMBAR_ACQUIRE = PREFIX + "MEMBAR_ACQUIRE" + POSTFIX;
    static {
        beforeMatchingNameRegex(MEMBAR_ACQUIRE, "MemBarAcquire");
    }

    public static final String MEMBAR_RELEASE = PREFIX + "MEMBAR_RELEASE" + POSTFIX;
    static {
        beforeMatchingNameRegex(MEMBAR_RELEASE, "MemBarRelease");
    }

    public static final String MEMBAR_STORESTORE = PREFIX + "MEMBAR_STORESTORE" + POSTFIX;
    static {
        beforeMatchingNameRegex(MEMBAR_STORESTORE, "MemBarStoreStore");
    }

    public static final String MEMBAR_VOLATILE = PREFIX + "MEMBAR_VOLATILE" + POSTFIX;
    static {
        beforeMatchingNameRegex(MEMBAR_VOLATILE, "MemBarVolatile");
    }

    public static final String MEM_TO_REG_SPILL_COPY = PREFIX + "MEM_TO_REG_SPILL_COPY" + POSTFIX;
    static {
        machOnly(MEM_TO_REG_SPILL_COPY, "MemToRegSpillCopy");
    }

    public static final String MEM_TO_REG_SPILL_COPY_TYPE = COMPOSITE_PREFIX + "MEM_TO_REG_SPILL_COPY_TYPE" + POSTFIX;
    static {
        String regex = START + "MemToRegSpillCopy" + MID + IS_REPLACED + ".*" + END;
        machOnly(MEM_TO_REG_SPILL_COPY_TYPE, regex);
    }

    public static final String MIN = PREFIX + "MIN" + POSTFIX;
    static {
        beforeMatchingNameRegex(MIN, "Min(I|L)");
    }

    public static final String MIN_D = PREFIX + "MIN_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(MIN_D, "MinD");
    }

    public static final String MIN_D_REDUCTION_REG = PREFIX + "MIN_D_REDUCTION_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MIN_D_REDUCTION_REG, "minD_reduction_reg");
    }

    public static final String MIN_D_REG = PREFIX + "MIN_D_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MIN_D_REG, "minD_reg");
    }

    public static final String MIN_F = PREFIX + "MIN_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(MIN_F, "MinF");
    }

    public static final String MIN_F_REDUCTION_REG = PREFIX + "MIN_F_REDUCTION_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MIN_F_REDUCTION_REG, "minF_reduction_reg");
    }

    public static final String MIN_F_REG = PREFIX + "MIN_F_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MIN_F_REG, "minF_reg");
    }

    public static final String MIN_I = PREFIX + "MIN_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(MIN_I, "MinI");
    }

    public static final String MIN_L = PREFIX + "MIN_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(MIN_L, "MinL");
    }

    public static final String MIN_HF = PREFIX + "MIN_HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(MIN_HF, "MinHF");
    }

    public static final String MAX_HF = PREFIX + "MAX_HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX_HF, "MaxHF");
    }

    public static final String MIN_VI = VECTOR_PREFIX + "MIN_VI" + POSTFIX;
    static {
        vectorNode(MIN_VI, "MinV", TYPE_INT);
    }

    public static final String MIN_VHF = VECTOR_PREFIX + "MIN_VHF" + POSTFIX;
    static {
        vectorNode(MIN_VHF, "MinVHF", TYPE_SHORT);
    }

    public static final String MIN_VF = VECTOR_PREFIX + "MIN_VF" + POSTFIX;
    static {
        vectorNode(MIN_VF, "MinV", TYPE_FLOAT);
    }

    public static final String MIN_VD = VECTOR_PREFIX + "MIN_VD" + POSTFIX;
    static {
        vectorNode(MIN_VD, "MinV", TYPE_DOUBLE);
    }

    public static final String MIN_VL = VECTOR_PREFIX + "MIN_VL" + POSTFIX;
    static {
        vectorNode(MIN_VL, "MinV", TYPE_LONG);
    }

    public static final String MOD_I = PREFIX + "MOD_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(MOD_I, "ModI");
    }

    public static final String MOD_L = PREFIX + "MOD_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(MOD_L, "ModL");
    }

    public static final String MOV_F2I = PREFIX + "MOV_F2I" + POSTFIX;
    static {
        beforeMatchingNameRegex(MOV_F2I, "MoveF2I");
    }

    public static final String MOV_I2F = PREFIX + "MOV_I2F" + POSTFIX;
    static {
        beforeMatchingNameRegex(MOV_I2F, "MoveI2F");
    }

    public static final String MOV_D2L = PREFIX + "MOV_D2L" + POSTFIX;
    static {
        beforeMatchingNameRegex(MOV_D2L, "MoveD2L");
    }

    public static final String MOV_L2D = PREFIX + "MOD_L2D" + POSTFIX;
    static {
        beforeMatchingNameRegex(MOV_L2D, "MoveL2D");
    }

    public static final String MUL = PREFIX + "MUL" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL, "Mul(I|L|F|D)");
    }

    public static final String MUL_ADD_S2I = PREFIX + "MUL_ADD_S2I" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL_ADD_S2I, "MulAddS2I");
    }

    public static final String MUL_ADD_VS2VI = VECTOR_PREFIX + "MUL_ADD_VS2VI" + POSTFIX;
    static {
        vectorNode(MUL_ADD_VS2VI, "MulAddVS2VI", TYPE_INT);
    }

    public static final String UMIN_VB = VECTOR_PREFIX + "UMIN_VB" + POSTFIX;
    static {
        vectorNode(UMIN_VB, "UMinV", TYPE_BYTE);
    }

    public static final String UMIN_VS = VECTOR_PREFIX + "UMIN_VS" + POSTFIX;
    static {
        vectorNode(UMIN_VS, "UMinV", TYPE_SHORT);
    }

    public static final String UMIN_VI = VECTOR_PREFIX + "UMIN_VI" + POSTFIX;
    static {
        vectorNode(UMIN_VI, "UMinV", TYPE_INT);
    }

    public static final String UMIN_VL = VECTOR_PREFIX + "UMIN_VL" + POSTFIX;
    static {
        vectorNode(UMIN_VL, "UMinV", TYPE_LONG);
    }

    public static final String UMAX_VB = VECTOR_PREFIX + "UMAX_VB" + POSTFIX;
    static {
        vectorNode(UMAX_VB, "UMaxV", TYPE_BYTE);
    }

    public static final String UMAX_VS = VECTOR_PREFIX + "UMAX_VS" + POSTFIX;
    static {
        vectorNode(UMAX_VS, "UMaxV", TYPE_SHORT);
    }

    public static final String UMAX_VI = VECTOR_PREFIX + "UMAX_VI" + POSTFIX;
    static {
        vectorNode(UMAX_VI, "UMaxV", TYPE_INT);
    }

    public static final String UMAX_VL = VECTOR_PREFIX + "UMAX_VL" + POSTFIX;
    static {
        vectorNode(UMAX_VL, "UMaxV", TYPE_LONG);
    }

    public static final String MASK_ALL = PREFIX + "MASK_ALL" + POSTFIX;
    static {
        beforeMatchingNameRegex(MASK_ALL, "MaskAll");
    }

    public static final String VECTOR_LONG_TO_MASK = PREFIX + "VECTOR_LONG_TO_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_LONG_TO_MASK, "VectorLongToMask");
    }

    public static final String VECTOR_MASK_TO_LONG = PREFIX + "VECTOR_MASK_TO_LONG" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_MASK_TO_LONG, "VectorMaskToLong");
    }

    public static final String VECTOR_MASK_LANE_IS_SET = PREFIX + "VECTOR_MASK_LANE_IS_SET" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_MASK_LANE_IS_SET, "ExtractUB");
    }

    public static final String VECTOR_MASK_GEN = PREFIX + "VECTOR_MASK_GEN" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_MASK_GEN, "VectorMaskGen");
    }

    public static final String VECTOR_MASK_FIRST_TRUE = PREFIX + "VECTOR_MASK_FIRST_TRUE" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_MASK_FIRST_TRUE, "VectorMaskFirstTrue");
    }

    // Can only be used if avx512_vnni is available.
    public static final String MUL_ADD_VS2VI_VNNI = PREFIX + "MUL_ADD_VS2VI_VNNI" + POSTFIX;
    static {
        machOnly(MUL_ADD_VS2VI_VNNI, "vmuladdaddS2I_reg");
    }

    public static final String MUL_D = PREFIX + "MUL_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL_D, "MulD");
    }

    public static final String MUL_F = PREFIX + "MUL_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL_F, "MulF");
    }

    public static final String MUL_HF = PREFIX + "MUL_HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL_HF, "MulHF");
    }

    public static final String MUL_I = PREFIX + "MUL_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL_I, "MulI");
    }

    public static final String MUL_L = PREFIX + "MUL_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL_L, "MulL");
    }

    public static final String MUL_VL = VECTOR_PREFIX + "MUL_VL" + POSTFIX;
    static {
        vectorNode(MUL_VL, "MulVL", TYPE_LONG);
    }

    public static final String MUL_VI = VECTOR_PREFIX + "MUL_VI" + POSTFIX;
    static {
        vectorNode(MUL_VI, "MulVI", TYPE_INT);
    }

    public static final String MUL_VHF = VECTOR_PREFIX + "MUL_VHF" + POSTFIX;
    static {
        vectorNode(MUL_VHF, "MulVHF", TYPE_SHORT);
    }

    public static final String MUL_VF = VECTOR_PREFIX + "MUL_VF" + POSTFIX;
    static {
        vectorNode(MUL_VF, "MulVF", TYPE_FLOAT);
    }

    public static final String MUL_VD = VECTOR_PREFIX + "MUL_VD" + POSTFIX;
    static {
        vectorNode(MUL_VD, "MulVD", TYPE_DOUBLE);
    }

    public static final String MUL_VB = VECTOR_PREFIX + "MUL_VB" + POSTFIX;
    static {
        vectorNode(MUL_VB, "MulVB", TYPE_BYTE);
    }

    public static final String MUL_VS = VECTOR_PREFIX + "MUL_VS" + POSTFIX;
    static {
        vectorNode(MUL_VS, "MulVS", TYPE_SHORT);
    }

    public static final String MUL_REDUCTION_VD = PREFIX + "MUL_REDUCTION_VD" + POSTFIX;
    static {
        superWordNodes(MUL_REDUCTION_VD, "MulReductionVD");
    }

    public static final String MUL_REDUCTION_VF = PREFIX + "MUL_REDUCTION_VF" + POSTFIX;
    static {
        superWordNodes(MUL_REDUCTION_VF, "MulReductionVF");
    }

    public static final String MUL_REDUCTION_VI = PREFIX + "MUL_REDUCTION_VI" + POSTFIX;
    static {
        superWordNodes(MUL_REDUCTION_VI, "MulReductionVI");
    }

    public static final String MUL_REDUCTION_VL = PREFIX + "MUL_REDUCTION_VL" + POSTFIX;
    static {
        superWordNodes(MUL_REDUCTION_VL, "MulReductionVL");
    }

    public static final String MIN_REDUCTION_V = PREFIX + "MIN_REDUCTION_V" + POSTFIX;
    static {
        superWordNodes(MIN_REDUCTION_V, "MinReductionV");
    }

    public static final String MAX_REDUCTION_V = PREFIX + "MAX_REDUCTION_V" + POSTFIX;
    static {
        superWordNodes(MAX_REDUCTION_V, "MaxReductionV");
    }

    public static final String UMIN_REDUCTION_V = PREFIX + "UMIN_REDUCTION_V" + POSTFIX;
    static {
        superWordNodes(UMIN_REDUCTION_V, "UMinReductionV");
    }

    public static final String UMAX_REDUCTION_V = PREFIX + "UMAX_REDUCTION_V" + POSTFIX;
    static {
        superWordNodes(UMAX_REDUCTION_V, "UMaxReductionV");
    }

    public static final String NEG_VF = VECTOR_PREFIX + "NEG_VF" + POSTFIX;
    static {
        vectorNode(NEG_VF, "NegVF", TYPE_FLOAT);
    }

    public static final String NEG_VD = VECTOR_PREFIX + "NEG_VD" + POSTFIX;
    static {
        vectorNode(NEG_VD, "NegVD", TYPE_DOUBLE);
    }

    public static final String NOP = PREFIX + "NOP" + POSTFIX;
    static {
        machOnlyNameRegex(NOP, "Nop");
    }

    public static final String NULL_ASSERT_TRAP = PREFIX + "NULL_ASSERT_TRAP" + POSTFIX;
    static {
        trapNodes(NULL_ASSERT_TRAP, "null_assert");
    }

    public static final String NULL_CHECK = PREFIX + "NULL_CHECK" + POSTFIX;
    static {
        machOnlyNameRegex(NULL_CHECK, "NullCheck");
    }

    public static final String NULL_CHECK_TRAP = PREFIX + "NULL_CHECK_TRAP" + POSTFIX;
    static {
        trapNodes(NULL_CHECK_TRAP, "null_check");
    }

    public static final String OOPMAP_WITH = COMPOSITE_PREFIX + "OOPMAP_WITH" + POSTFIX;
    static {
        String regex = "(#\\s*OopMap\\s*\\{.*" + IS_REPLACED + ".*\\})";
        optoOnly(OOPMAP_WITH, regex);
    }

    public static final String OPAQUE_TEMPLATE_ASSERTION_PREDICATE = PREFIX + "OPAQUE_TEMPLATE_ASSERTION_PREDICATE" + POSTFIX;
    static {
        duringLoopOpts(OPAQUE_TEMPLATE_ASSERTION_PREDICATE, "OpaqueTemplateAssertionPredicate");
    }

    public static final String OR_I = PREFIX + "OR_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(OR_I, "OrI");
    }

    public static final String OR_L = PREFIX + "OR_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(OR_L, "OrL");
    }

    public static final String OR_VB = VECTOR_PREFIX + "OR_VB" + POSTFIX;
    static {
        vectorNode(OR_VB, "OrV", TYPE_BYTE);
    }

    public static final String OR_VS = VECTOR_PREFIX + "OR_VS" + POSTFIX;
    static {
        vectorNode(OR_VS, "OrV", TYPE_SHORT);
    }

    public static final String OR_VI = VECTOR_PREFIX + "OR_VI" + POSTFIX;
    static {
        vectorNode(OR_VI, "OrV", TYPE_INT);
    }

    public static final String OR_VL = VECTOR_PREFIX + "OR_VL" + POSTFIX;
    static {
        vectorNode(OR_VL, "OrV", TYPE_LONG);
    }

    public static final String OR_V_MASK = PREFIX + "OR_V_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(OR_V_MASK, "OrVMask");
    }

    public static final String OR_REDUCTION_V = PREFIX + "OR_REDUCTION_V" + POSTFIX;
    static {
        superWordNodes(OR_REDUCTION_V, "OrReductionV");
    }

    public static final String OUTER_STRIP_MINED_LOOP = PREFIX + "OUTER_STRIP_MINED_LOOP" + POSTFIX;
    static {
        String regex = START + "OuterStripMinedLoop\\b" + MID + END;
        fromAfterCountedLoops(OUTER_STRIP_MINED_LOOP, regex);
    }

    public static final String PARTIAL_SUBTYPE_CHECK = PREFIX + "PARTIAL_SUBTYPE_CHECK" + POSTFIX;
    static {
        beforeMatchingNameRegex(PARTIAL_SUBTYPE_CHECK, "PartialSubtypeCheck");
    }

    public static final String PHI = PREFIX + "PHI" + POSTFIX;
    static {
        beforeMatchingNameRegex(PHI, "Phi");
    }

    public static final String POPCOUNT_I = PREFIX + "POPCOUNT_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(POPCOUNT_I, "PopCountI");
    }

    public static final String POPCOUNT_L = PREFIX + "POPCOUNT_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(POPCOUNT_L, "PopCountL");
    }

    public static final String POPCOUNT_VI = VECTOR_PREFIX + "POPCOUNT_VI" + POSTFIX;
    static {
        vectorNode(POPCOUNT_VI, "PopCountVI", TYPE_INT);
    }

    public static final String POPCOUNT_VL = VECTOR_PREFIX + "POPCOUNT_VL" + POSTFIX;
    static {
        vectorNode(POPCOUNT_VL, "PopCountVL", TYPE_LONG);
    }

    public static final String COUNT_TRAILING_ZEROS_I = PREFIX + "COUNT_TRAILING_ZEROS_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(COUNT_TRAILING_ZEROS_I, "CountTrailingZerosI");
    }

    public static final String COUNT_TRAILING_ZEROS_L = PREFIX + "COUNT_TRAILING_ZEROS_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(COUNT_TRAILING_ZEROS_L, "CountTrailingZerosL");
    }

    public static final String COUNT_TRAILING_ZEROS_VL = VECTOR_PREFIX + "COUNT_TRAILING_ZEROS_VL" + POSTFIX;
    static {
        vectorNode(COUNT_TRAILING_ZEROS_VL, "CountTrailingZerosV", TYPE_LONG);
    }

    public static final String COUNT_TRAILING_ZEROS_VI = VECTOR_PREFIX + "COUNT_TRAILING_ZEROS_VI" + POSTFIX;
    static {
        vectorNode(COUNT_TRAILING_ZEROS_VI, "CountTrailingZerosV", TYPE_INT);
    }

    public static final String COUNT_LEADING_ZEROS_I = PREFIX + "COUNT_LEADING_ZEROS_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(COUNT_LEADING_ZEROS_I, "CountLeadingZerosI");
    }

    public static final String COUNT_LEADING_ZEROS_L = PREFIX + "COUNT_LEADING_ZEROS_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(COUNT_LEADING_ZEROS_L, "CountLeadingZerosL");
    }

    public static final String COUNT_LEADING_ZEROS_VL = VECTOR_PREFIX + "COUNT_LEADING_ZEROS_VL" + POSTFIX;
    static {
        vectorNode(COUNT_LEADING_ZEROS_VL, "CountLeadingZerosV", TYPE_LONG);
    }

    public static final String COUNT_LEADING_ZEROS_VI = VECTOR_PREFIX + "COUNT_LEADING_ZEROS_VI" + POSTFIX;
    static {
        vectorNode(COUNT_LEADING_ZEROS_VI, "CountLeadingZerosV", TYPE_INT);
    }

    public static final String POPULATE_INDEX = PREFIX + "POPULATE_INDEX" + POSTFIX;
    static {
        String regex = START + "PopulateIndex" + MID + END;
        IR_NODE_MAPPINGS.put(POPULATE_INDEX, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                       CompilePhase.AFTER_CLOOPS,
                                                                       CompilePhase.BEFORE_MATCHING));
    }

    public static final String LOOP_PARSE_PREDICATE = PREFIX + "LOOP_PARSE_PREDICATE" + POSTFIX;
    static {
        parsePredicateNodes(LOOP_PARSE_PREDICATE, "Loop");
    }

    public static final String LOOP_LIMIT_CHECK_PARSE_PREDICATE = PREFIX + "LOOP_LIMIT_CHECK_PARSE_PREDICATE" + POSTFIX;
    static {
        parsePredicateNodes(LOOP_LIMIT_CHECK_PARSE_PREDICATE, "Loop_Limit_Check");
    }

    public static final String PROFILED_LOOP_PARSE_PREDICATE = PREFIX + "PROFILED_LOOP_PARSE_PREDICATE" + POSTFIX;
    static {
        parsePredicateNodes(PROFILED_LOOP_PARSE_PREDICATE, "Profiled_Loop");
    }

    public static final String AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE = PREFIX + "AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE" + POSTFIX;
    static {
        parsePredicateNodes(AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "Auto_Vectorization_Check");
    }

    public static final String PREDICATE_TRAP = PREFIX + "PREDICATE_TRAP" + POSTFIX;
    static {
        trapNodes(PREDICATE_TRAP, "predicate");
    }

    public static final String RANGE_CHECK_TRAP = PREFIX + "RANGE_CHECK_TRAP" + POSTFIX;
    static {
        trapNodes(RANGE_CHECK_TRAP, "range_check");
    }

    public static final String SHORT_RUNNING_LOOP_TRAP = PREFIX + "SHORT_RUNNING_LOOP_TRAP" + POSTFIX;
    static {
        trapNodes(SHORT_RUNNING_LOOP_TRAP, "short_running_loop");
    }

    public static final String REINTERPRET_S2HF = PREFIX + "REINTERPRET_S2HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(REINTERPRET_S2HF, "ReinterpretS2HF");
    }

    public static final String REINTERPRET_HF2S = PREFIX + "REINTERPRET_HF2S" + POSTFIX;
    static {
        beforeMatchingNameRegex(REINTERPRET_HF2S, "ReinterpretHF2S");
    }

    public static final String REPLICATE_B = VECTOR_PREFIX + "REPLICATE_B" + POSTFIX;
    static {
        vectorNode(REPLICATE_B, "Replicate", TYPE_BYTE);
    }

    public static final String REPLICATE_S = VECTOR_PREFIX + "REPLICATE_S" + POSTFIX;
    static {
        vectorNode(REPLICATE_S, "Replicate", TYPE_SHORT);
    }

    public static final String REPLICATE_I = VECTOR_PREFIX + "REPLICATE_I" + POSTFIX;
    static {
        vectorNode(REPLICATE_I, "Replicate", TYPE_INT);
    }

    public static final String REPLICATE_L = VECTOR_PREFIX + "REPLICATE_L" + POSTFIX;
    static {
        vectorNode(REPLICATE_L, "Replicate", TYPE_LONG);
    }

    public static final String REPLICATE_F = VECTOR_PREFIX + "REPLICATE_F" + POSTFIX;
    static {
        vectorNode(REPLICATE_F, "Replicate", TYPE_FLOAT);
    }

    public static final String REPLICATE_D = VECTOR_PREFIX + "REPLICATE_D" + POSTFIX;
    static {
        vectorNode(REPLICATE_D, "Replicate", TYPE_DOUBLE);
    }

    public static final String REVERSE_BYTES_I = PREFIX + "REVERSE_BYTES_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_BYTES_I, "ReverseBytesI");
    }

    public static final String REVERSE_BYTES_L = PREFIX + "REVERSE_BYTES_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_BYTES_L, "ReverseBytesL");
    }

    public static final String REVERSE_BYTES_S = PREFIX + "REVERSE_BYTES_S" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_BYTES_S, "ReverseBytesS");
    }

    public static final String REVERSE_BYTES_US = PREFIX + "REVERSE_BYTES_US" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_BYTES_US, "ReverseBytesUS");
    }

    public static final String REVERSE_BYTES_VB = VECTOR_PREFIX + "REVERSE_BYTES_VB" + POSTFIX;
    static {
        vectorNode(REVERSE_BYTES_VB, "ReverseBytesV", TYPE_BYTE);
    }

    public static final String REVERSE_BYTES_VS = VECTOR_PREFIX + "REVERSE_BYTES_VS" + POSTFIX;
    static {
        vectorNode(REVERSE_BYTES_VS, "ReverseBytesV", TYPE_SHORT);
    }

    public static final String REVERSE_BYTES_VI = VECTOR_PREFIX + "REVERSE_BYTES_VI" + POSTFIX;
    static {
        vectorNode(REVERSE_BYTES_VI, "ReverseBytesV", TYPE_INT);
    }

    public static final String REVERSE_BYTES_VL = VECTOR_PREFIX + "REVERSE_BYTES_VL" + POSTFIX;
    static {
        vectorNode(REVERSE_BYTES_VL, "ReverseBytesV", TYPE_LONG);
    }

    public static final String REVERSE_I = PREFIX + "REVERSE_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_I, "ReverseI");
    }

    public static final String REVERSE_L = PREFIX + "REVERSE_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_L, "ReverseL");
    }

    public static final String REVERSE_VI = VECTOR_PREFIX + "REVERSE_VI" + POSTFIX;
    static {
        vectorNode(REVERSE_VI, "ReverseV", TYPE_INT);
    }

    public static final String REVERSE_VL = VECTOR_PREFIX + "REVERSE_VL" + POSTFIX;
    static {
        vectorNode(REVERSE_VL, "ReverseV", TYPE_LONG);
    }

    public static final String ROUND_VD = VECTOR_PREFIX + "ROUND_VD" + POSTFIX;
    static {
        vectorNode(ROUND_VD, "RoundVD", TYPE_LONG);
    }

    public static final String ROUND_VF = VECTOR_PREFIX + "ROUND_VF" + POSTFIX;
    static {
        vectorNode(ROUND_VF, "RoundVF", TYPE_INT);
    }

    public static final String ROTATE_LEFT = PREFIX + "ROTATE_LEFT" + POSTFIX;
    static {
        beforeMatchingNameRegex(ROTATE_LEFT, "RotateLeft");
    }

    public static final String ROTATE_RIGHT = PREFIX + "ROTATE_RIGHT" + POSTFIX;
    static {
        beforeMatchingNameRegex(ROTATE_RIGHT, "RotateRight");
    }

    public static final String ROTATE_LEFT_V = PREFIX + "ROTATE_LEFT_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(ROTATE_LEFT_V, "RotateLeftV");
    }

    public static final String ROTATE_RIGHT_V = PREFIX + "ROTATE_RIGHT_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(ROTATE_RIGHT_V, "RotateRightV");
    }

    public static final String ROUND_DOUBLE_MODE_V = VECTOR_PREFIX + "ROUND_DOUBLE_MODE_V" + POSTFIX;
    static {
        vectorNode(ROUND_DOUBLE_MODE_V, "RoundDoubleModeV", TYPE_DOUBLE);
    }

    public static final String RSHIFT = PREFIX + "RSHIFT" + POSTFIX;
    static {
        beforeMatchingNameRegex(RSHIFT, "RShift(I|L)");
    }

    public static final String RSHIFT_I = PREFIX + "RSHIFT_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(RSHIFT_I, "RShiftI");
    }

    public static final String RSHIFT_L = PREFIX + "RSHIFT_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(RSHIFT_L, "RShiftL");
    }

    public static final String RSHIFT_VB = VECTOR_PREFIX + "RSHIFT_VB" + POSTFIX;
    static {
        vectorNode(RSHIFT_VB, "RShiftVB", TYPE_BYTE);
    }

    public static final String RSHIFT_VS = VECTOR_PREFIX + "RSHIFT_VS" + POSTFIX;
    static {
        vectorNode(RSHIFT_VS, "RShiftVS", TYPE_SHORT);
    }

    public static final String RSHIFT_VC = VECTOR_PREFIX + "RSHIFT_VC" + POSTFIX;
    static {
        vectorNode(RSHIFT_VC, "RShiftVS", TYPE_CHAR); // short computation with char type
    }

    public static final String RSHIFT_VI = VECTOR_PREFIX + "RSHIFT_VI" + POSTFIX;
    static {
        vectorNode(RSHIFT_VI, "RShiftVI", TYPE_INT);
    }

    public static final String RSHIFT_VL = VECTOR_PREFIX + "RSHIFT_VL" + POSTFIX;
    static {
        vectorNode(RSHIFT_VL, "RShiftVL", TYPE_LONG);
    }

    public static final String SAFEPOINT = PREFIX + "SAFEPOINT" + POSTFIX;
    static {
        beforeMatchingNameRegex(SAFEPOINT, "SafePoint");
    }

    public static final String SCOPE_OBJECT = PREFIX + "SCOPE_OBJECT" + POSTFIX;
    static {
        String regex = "(.*# ScObj.*" + END;
        optoOnly(SCOPE_OBJECT, regex);
    }

    public static final String SAFEPOINT_SCALAROBJECT_OF = COMPOSITE_PREFIX + "SAFEPOINT_SCALAROBJECT_OF" + POSTFIX;
    static {
        safepointScalarobjectOfNodes(SAFEPOINT_SCALAROBJECT_OF, "SafePointScalarObject");
    }

    public static final String SIGNUM_VD = VECTOR_PREFIX + "SIGNUM_VD" + POSTFIX;
    static {
        vectorNode(SIGNUM_VD, "SignumVD", TYPE_DOUBLE);
    }

    public static final String SIGNUM_VF = VECTOR_PREFIX + "SIGNUM_VF" + POSTFIX;
    static {
        vectorNode(SIGNUM_VF, "SignumVF", TYPE_FLOAT);
    }

    public static final String SQRT_VHF = VECTOR_PREFIX + "SQRT_VHF" + POSTFIX;
    static {
        vectorNode(SQRT_VHF, "SqrtVHF", TYPE_SHORT);
    }

    public static final String SQRT_HF = PREFIX + "SQRT_HF" + POSTFIX;
    static {
       beforeMatchingNameRegex(SQRT_HF, "SqrtHF");
    }

    public static final String SQRT_F = PREFIX + "SQRT_F" + POSTFIX;
    static {
       beforeMatchingNameRegex(SQRT_F, "SqrtF");
    }

    public static final String SQRT_VF = VECTOR_PREFIX + "SQRT_VF" + POSTFIX;
    static {
        vectorNode(SQRT_VF, "SqrtVF", TYPE_FLOAT);
    }

    public static final String SQRT_VD = VECTOR_PREFIX + "SQRT_VD" + POSTFIX;
    static {
        vectorNode(SQRT_VD, "SqrtVD", TYPE_DOUBLE);
    }

    public static final String STORE = PREFIX + "STORE" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE, "Store(B|C|S|I|L|F|D|P|N)");
    }

    public static final String STORE_B = PREFIX + "STORE_B" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_B, "StoreB");
    }

    public static final String STORE_B_OF_CLASS = COMPOSITE_PREFIX + "STORE_B_OF_CLASS" + POSTFIX;
    static {
        storeOfNodes(STORE_B_OF_CLASS, "StoreB");
    }

    public static final String STORE_C = PREFIX + "STORE_C" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_C, "StoreC");
    }

    public static final String STORE_C_OF_CLASS = COMPOSITE_PREFIX + "STORE_C_OF_CLASS" + POSTFIX;
    static {
        storeOfNodes(STORE_C_OF_CLASS, "StoreC");
    }

    public static final String STORE_D = PREFIX + "STORE_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_D, "StoreD");
    }

    public static final String STORE_D_OF_CLASS = COMPOSITE_PREFIX + "STORE_D_OF_CLASS" + POSTFIX;
    static {
        storeOfNodes(STORE_D_OF_CLASS, "StoreD");
    }

    public static final String STORE_F = PREFIX + "STORE_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_F, "StoreF");
    }

    public static final String STORE_F_OF_CLASS = COMPOSITE_PREFIX + "STORE_F_OF_CLASS" + POSTFIX;
    static {
        storeOfNodes(STORE_F_OF_CLASS, "StoreF");
    }

    public static final String STORE_I = PREFIX + "STORE_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_I, "StoreI");
    }

    public static final String STORE_I_OF_CLASS = COMPOSITE_PREFIX + "STORE_I_OF_CLASS" + POSTFIX;
    static {
        storeOfNodes(STORE_I_OF_CLASS, "StoreI");
    }

    public static final String STORE_L = PREFIX + "STORE_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_L, "StoreL");
    }

    public static final String STORE_L_OF_CLASS = COMPOSITE_PREFIX + "STORE_L_OF_CLASS" + POSTFIX;
    static {
        storeOfNodes(STORE_L_OF_CLASS, "StoreL");
    }

    public static final String STORE_N = PREFIX + "STORE_N" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_N, "StoreN");
    }

    public static final String STORE_N_OF_CLASS = COMPOSITE_PREFIX + "STORE_N_OF_CLASS" + POSTFIX;
    static {
        storeOfNodes(STORE_N_OF_CLASS, "StoreN");
    }

    public static final String STORE_OF_CLASS = COMPOSITE_PREFIX + "STORE_OF_CLASS" + POSTFIX;
    static {
        storeOfNodes(STORE_OF_CLASS, "Store(B|C|S|I|L|F|D|P|N)");
    }

    public static final String STORE_OF_FIELD = COMPOSITE_PREFIX + "STORE_OF_FIELD" + POSTFIX;
    static {
        String regex = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + "@.*name=" + IS_REPLACED + ",.*" + END;
        beforeMatching(STORE_OF_FIELD, regex);
    }

    public static final String STORE_P = PREFIX + "STORE_P" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_P, "StoreP");
    }

    public static final String STORE_P_OF_CLASS = COMPOSITE_PREFIX + "STORE_P_OF_CLASS" + POSTFIX;
    static {
        storeOfNodes(STORE_P_OF_CLASS, "StoreP");
    }

    public static final String STORE_VECTOR = PREFIX + "STORE_VECTOR" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_VECTOR, "StoreVector");
    }

    public static final String STORE_VECTOR_SCATTER = PREFIX + "STORE_VECTOR_SCATTER" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_VECTOR_SCATTER, "StoreVectorScatter");
    }

    public static final String STORE_VECTOR_MASKED = PREFIX + "STORE_VECTOR_MASKED" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_VECTOR_MASKED, "StoreVectorMasked");
    }

    public static final String STORE_VECTOR_SCATTER_MASKED = PREFIX + "STORE_VECTOR_SCATTER_MASKED" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_VECTOR_SCATTER_MASKED, "StoreVectorScatterMasked");
    }

    public static final String VECTOR_LOAD_MASK = PREFIX + "VECTOR_LOAD_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_LOAD_MASK, "VectorLoadMask");
    }

    public static final String VECTOR_STORE_MASK = PREFIX + "VECTOR_STORE_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_STORE_MASK, "VectorStoreMask");
    }

    public static final String SUB = PREFIX + "SUB" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB, "Sub(I|L|F|D|HF)");
    }

    public static final String SUB_D = PREFIX + "SUB_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB_D, "SubD");
    }

    public static final String SUB_F = PREFIX + "SUB_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB_F, "SubF");
    }

    public static final String SUB_HF = PREFIX + "SUB_HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB_HF, "SubHF");
    }

    public static final String SUB_I = PREFIX + "SUB_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB_I, "SubI");
    }

    public static final String SUB_L = PREFIX + "SUB_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB_L, "SubL");
    }

    public static final String SUB_VB = VECTOR_PREFIX + "SUB_VB" + POSTFIX;
    static {
        vectorNode(SUB_VB, "SubVB", TYPE_BYTE);
    }

    public static final String SUB_VS = VECTOR_PREFIX + "SUB_VS" + POSTFIX;
    static {
        vectorNode(SUB_VS, "SubVS", TYPE_SHORT);
    }

    public static final String SUB_VI = VECTOR_PREFIX + "SUB_VI" + POSTFIX;
    static {
        vectorNode(SUB_VI, "SubVI", TYPE_INT);
    }

    public static final String SUB_VL = VECTOR_PREFIX + "SUB_VL" + POSTFIX;
    static {
        vectorNode(SUB_VL, "SubVL", TYPE_LONG);
    }

    public static final String SUB_VHF = VECTOR_PREFIX + "SUB_VHF" + POSTFIX;
    static {
        vectorNode(SUB_VHF, "SubVHF", TYPE_SHORT);
    }

    public static final String SUB_VF = VECTOR_PREFIX + "SUB_VF" + POSTFIX;
    static {
        vectorNode(SUB_VF, "SubVF", TYPE_FLOAT);
    }

    public static final String SUB_VD = VECTOR_PREFIX + "SUB_VD" + POSTFIX;
    static {
        vectorNode(SUB_VD, "SubVD", TYPE_DOUBLE);
    }

    public static final String SUBTYPE_CHECK = PREFIX + "SUBTYPE_CHECK" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUBTYPE_CHECK, "SubTypeCheck");
    }

    public static final String TRAP = PREFIX + "TRAP" + POSTFIX;
    static {
        trapNodes(TRAP, "reason");
    }

    public static final String DIV_HF = PREFIX + "DIV_HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(DIV_HF, "DivHF");
    }

    public static final String UDIV_I = PREFIX + "UDIV_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(UDIV_I, "UDivI");
    }

    public static final String UDIV_L = PREFIX + "UDIV_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(UDIV_L, "UDivL");
    }

    public static final String UDIV_MOD_I = PREFIX + "UDIV_MOD_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(UDIV_MOD_I, "UDivModI");
    }

    public static final String UDIV_MOD_L = PREFIX + "UDIV_MOD_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(UDIV_MOD_L, "UDivModL");
    }

    public static final String UMOD_I = PREFIX + "UMOD_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(UMOD_I, "UModI");
    }

    public static final String UMOD_L = PREFIX + "UMOD_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(UMOD_L, "UModL");
    }

    public static final String UNHANDLED_TRAP = PREFIX + "UNHANDLED_TRAP" + POSTFIX;
    static {
        trapNodes(UNHANDLED_TRAP, "unhandled");
    }

    public static final String UNSTABLE_IF_TRAP = PREFIX + "UNSTABLE_IF_TRAP" + POSTFIX;
    static {
        trapNodes(UNSTABLE_IF_TRAP, "unstable_if");
    }

    public static final String UNREACHED_TRAP = PREFIX + "UNREACHED_TRAP" + POSTFIX;
    static {
        trapNodes(UNREACHED_TRAP, "unreached");
    }

    public static final String URSHIFT = PREFIX + "URSHIFT" + POSTFIX;
    static {
        beforeMatchingNameRegex(URSHIFT, "URShift(B|S|I|L)");
    }

    public static final String URSHIFT_B = PREFIX + "URSHIFT_B" + POSTFIX;
    static {
        beforeMatchingNameRegex(URSHIFT_B, "URShiftB");
    }

    public static final String URSHIFT_I = PREFIX + "URSHIFT_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(URSHIFT_I, "URShiftI");
    }

    public static final String URSHIFT_L = PREFIX + "URSHIFT_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(URSHIFT_L, "URShiftL");
    }

    public static final String URSHIFT_S = PREFIX + "URSHIFT_S" + POSTFIX;
    static {
        beforeMatchingNameRegex(URSHIFT_S, "URShiftS");
    }

    public static final String URSHIFT_VB = VECTOR_PREFIX + "URSHIFT_VB" + POSTFIX;
    static {
        vectorNode(URSHIFT_VB, "URShiftVB", TYPE_BYTE);
    }

    public static final String URSHIFT_VS = VECTOR_PREFIX + "URSHIFT_VS" + POSTFIX;
    static {
        vectorNode(URSHIFT_VS, "URShiftVS", TYPE_SHORT);
    }

    public static final String URSHIFT_VC = VECTOR_PREFIX + "URSHIFT_VC" + POSTFIX;
    static {
        vectorNode(URSHIFT_VC, "URShiftVS", TYPE_CHAR); // short computation with char type
    }

    public static final String URSHIFT_VI = VECTOR_PREFIX + "URSHIFT_VI" + POSTFIX;
    static {
        vectorNode(URSHIFT_VI, "URShiftVI", TYPE_INT);
    }

    public static final String URSHIFT_VL = VECTOR_PREFIX + "URSHIFT_VL" + POSTFIX;
    static {
        vectorNode(URSHIFT_VL, "URShiftVL", TYPE_LONG);
    }

    public static final String VAND_NOT_I = PREFIX + "VAND_NOT_I" + POSTFIX;
    static {
        machOnlyNameRegex(VAND_NOT_I, "vand_notI");
    }

    public static final String VAND_NOT_L = PREFIX + "VAND_NOT_L" + POSTFIX;
    static {
        machOnlyNameRegex(VAND_NOT_L, "vand_notL");
    }

    public static final String VAND_NOT_I_MASKED = PREFIX + "VAND_NOT_I_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VAND_NOT_I_MASKED, "vand_notI_masked");
    }

    public static final String VAND_NOT_L_MASKED = PREFIX + "VAND_NOT_L_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VAND_NOT_L_MASKED, "vand_notL_masked");
    }

    public static final String RISCV_VAND_NOTI_VX = PREFIX + "RISCV_VAND_NOTI_VX" + POSTFIX;
    static {
        machOnlyNameRegex(RISCV_VAND_NOTI_VX, "vand_notI_vx");
    }

    public static final String RISCV_VAND_NOTL_VX = PREFIX + "RISCV_VAND_NOTL_VX" + POSTFIX;
    static {
        machOnlyNameRegex(RISCV_VAND_NOTL_VX, "vand_notL_vx");
    }

    public static final String RISCV_VAND_NOTI_VX_MASKED = PREFIX + "RISCV_VAND_NOTI_VX_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(RISCV_VAND_NOTI_VX_MASKED, "vand_notI_vx_masked");
    }

    public static final String RISCV_VAND_NOTL_VX_MASKED = PREFIX + "RISCV_VAND_NOTL_VX_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(RISCV_VAND_NOTL_VX_MASKED, "vand_notL_vx_masked");
    }

    public static final String VECTOR_BLEND_B = VECTOR_PREFIX + "VECTOR_BLEND_B" + POSTFIX;
    static {
        vectorNode(VECTOR_BLEND_B, "VectorBlend", TYPE_BYTE);
    }

    public static final String VECTOR_BLEND_S = VECTOR_PREFIX + "VECTOR_BLEND_S" + POSTFIX;
    static {
        vectorNode(VECTOR_BLEND_S, "VectorBlend", TYPE_SHORT);
    }

    public static final String VECTOR_BLEND_I = VECTOR_PREFIX + "VECTOR_BLEND_I" + POSTFIX;
    static {
        vectorNode(VECTOR_BLEND_I, "VectorBlend", TYPE_INT);
    }

    public static final String VECTOR_BLEND_L = VECTOR_PREFIX + "VECTOR_BLEND_L" + POSTFIX;
    static {
        vectorNode(VECTOR_BLEND_L, "VectorBlend", TYPE_LONG);
    }

    public static final String VECTOR_BLEND_F = VECTOR_PREFIX + "VECTOR_BLEND_F" + POSTFIX;
    static {
        vectorNode(VECTOR_BLEND_F, "VectorBlend", TYPE_FLOAT);
    }

    public static final String VECTOR_BLEND_D = VECTOR_PREFIX + "VECTOR_BLEND_D" + POSTFIX;
    static {
        vectorNode(VECTOR_BLEND_D, "VectorBlend", TYPE_DOUBLE);
    }

    public static final String VECTOR_MASK_CMP_I = VECTOR_PREFIX + "VECTOR_MASK_CMP_I" + POSTFIX;
    static {
        vectorNode(VECTOR_MASK_CMP_I, "VectorMaskCmp", TYPE_INT);
    }

    public static final String VECTOR_MASK_CMP_L = VECTOR_PREFIX + "VECTOR_MASK_CMP_L" + POSTFIX;
    static {
        vectorNode(VECTOR_MASK_CMP_L, "VectorMaskCmp", TYPE_LONG);
    }

    public static final String VECTOR_MASK_CMP_F = VECTOR_PREFIX + "VECTOR_MASK_CMP_F" + POSTFIX;
    static {
        vectorNode(VECTOR_MASK_CMP_F, "VectorMaskCmp", TYPE_FLOAT);
    }

    public static final String VECTOR_MASK_CMP_D = VECTOR_PREFIX + "VECTOR_MASK_CMP_D" + POSTFIX;
    static {
        vectorNode(VECTOR_MASK_CMP_D, "VectorMaskCmp", TYPE_DOUBLE);
    }

    public static final String VECTOR_MASK_CMP = PREFIX + "VECTOR_MASK_CMP" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_MASK_CMP, "VectorMaskCmp");
    }

    public static final String VECTOR_CAST_B2S = VECTOR_PREFIX + "VECTOR_CAST_B2S" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_B2S, "VectorCastB2X", TYPE_SHORT);
    }

    public static final String VECTOR_CAST_B2I = VECTOR_PREFIX + "VECTOR_CAST_B2I" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_B2I, "VectorCastB2X", TYPE_INT);
    }

    public static final String VECTOR_CAST_B2L = VECTOR_PREFIX + "VECTOR_CAST_B2L" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_B2L, "VectorCastB2X", TYPE_LONG);
    }

    public static final String VECTOR_CAST_B2F = VECTOR_PREFIX + "VECTOR_CAST_B2F" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_B2F, "VectorCastB2X", TYPE_FLOAT);
    }

    public static final String VECTOR_CAST_B2D = VECTOR_PREFIX + "VECTOR_CAST_B2D" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_B2D, "VectorCastB2X", TYPE_DOUBLE);
    }

    public static final String VECTOR_CAST_D2B = VECTOR_PREFIX + "VECTOR_CAST_D2B" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_D2B, "VectorCastD2X", TYPE_BYTE);
    }

    public static final String VECTOR_CAST_D2S = VECTOR_PREFIX + "VECTOR_CAST_D2S" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_D2S, "VectorCastD2X", TYPE_SHORT);
    }

    public static final String VECTOR_CAST_D2I = VECTOR_PREFIX + "VECTOR_CAST_D2I" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_D2I, "VectorCastD2X", TYPE_INT);
    }

    public static final String VECTOR_CAST_D2L = VECTOR_PREFIX + "VECTOR_CAST_D2L" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_D2L, "VectorCastD2X", TYPE_LONG);
    }

    public static final String VECTOR_CAST_D2F = VECTOR_PREFIX + "VECTOR_CAST_D2F" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_D2F, "VectorCastD2X", TYPE_FLOAT);
    }

    public static final String VECTOR_CAST_F2B = VECTOR_PREFIX + "VECTOR_CAST_F2B" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_F2B, "VectorCastF2X", TYPE_BYTE);
    }

    public static final String VECTOR_CAST_F2S = VECTOR_PREFIX + "VECTOR_CAST_F2S" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_F2S, "VectorCastF2X", TYPE_SHORT);
    }

    public static final String VECTOR_CAST_F2I = VECTOR_PREFIX + "VECTOR_CAST_F2I" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_F2I, "VectorCastF2X", TYPE_INT);
    }

    public static final String VECTOR_CAST_F2L = VECTOR_PREFIX + "VECTOR_CAST_F2L" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_F2L, "VectorCastF2X", TYPE_LONG);
    }

    public static final String VECTOR_CAST_F2D = VECTOR_PREFIX + "VECTOR_CAST_F2D" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_F2D, "VectorCastF2X", TYPE_DOUBLE);
    }

    public static final String VECTOR_CAST_I2B = VECTOR_PREFIX + "VECTOR_CAST_I2B" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_I2B, "VectorCastI2X", TYPE_BYTE);
    }

    public static final String VECTOR_CAST_I2S = VECTOR_PREFIX + "VECTOR_CAST_I2S" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_I2S, "VectorCastI2X", TYPE_SHORT);
    }

    public static final String VECTOR_CAST_I2L = VECTOR_PREFIX + "VECTOR_CAST_I2L" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_I2L, "VectorCastI2X", TYPE_LONG);
    }

    public static final String VECTOR_CAST_I2F = VECTOR_PREFIX + "VECTOR_CAST_I2F" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_I2F, "VectorCastI2X", TYPE_FLOAT);
    }

    public static final String VECTOR_CAST_I2D = VECTOR_PREFIX + "VECTOR_CAST_I2D" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_I2D, "VectorCastI2X", TYPE_DOUBLE);
    }

    public static final String VECTOR_CAST_L2B = VECTOR_PREFIX + "VECTOR_CAST_L2B" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_L2B, "VectorCastL2X", TYPE_BYTE);
    }

    public static final String VECTOR_CAST_L2S = VECTOR_PREFIX + "VECTOR_CAST_L2S" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_L2S, "VectorCastL2X", TYPE_SHORT);
    }

    public static final String VECTOR_CAST_L2I = VECTOR_PREFIX + "VECTOR_CAST_L2I" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_L2I, "VectorCastL2X", TYPE_INT);
    }

    public static final String VECTOR_CAST_L2F = VECTOR_PREFIX + "VECTOR_CAST_L2F" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_L2F, "VectorCastL2X", TYPE_FLOAT);
    }

    public static final String VECTOR_CAST_L2D = VECTOR_PREFIX + "VECTOR_CAST_L2D" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_L2D, "VectorCastL2X", TYPE_DOUBLE);
    }

    public static final String VECTOR_CAST_S2B = VECTOR_PREFIX + "VECTOR_CAST_S2B" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_S2B, "VectorCastS2X", TYPE_BYTE);
    }

    public static final String VECTOR_CAST_S2I = VECTOR_PREFIX + "VECTOR_CAST_S2I" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_S2I, "VectorCastS2X", TYPE_INT);
    }

    public static final String VECTOR_CAST_S2L = VECTOR_PREFIX + "VECTOR_CAST_S2L" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_S2L, "VectorCastS2X", TYPE_LONG);
    }

    public static final String VECTOR_CAST_S2F = VECTOR_PREFIX + "VECTOR_CAST_S2F" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_S2F, "VectorCastS2X", TYPE_FLOAT);
    }

    public static final String VECTOR_CAST_S2D = VECTOR_PREFIX + "VECTOR_CAST_S2D" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_S2D, "VectorCastS2X", TYPE_DOUBLE);
    }

    public static final String VECTOR_CAST_F2HF = VECTOR_PREFIX + "VECTOR_CAST_F2HF" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_F2HF, "VectorCastF2HF", TYPE_SHORT);
    }

    public static final String VECTOR_CAST_HF2F = VECTOR_PREFIX + "VECTOR_CAST_HF2F" + POSTFIX;
    static {
        vectorNode(VECTOR_CAST_HF2F, "VectorCastHF2F", TYPE_FLOAT);
    }

    public static final String VECTOR_MASK_CAST = PREFIX + "VECTOR_MASK_CAST" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_MASK_CAST, "VectorMaskCast");
    }

    public static final String VECTOR_REINTERPRET = PREFIX + "VECTOR_REINTERPRET" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_REINTERPRET, "VectorReinterpret");
    }

    public static final String VECTOR_UCAST_B2S = VECTOR_PREFIX + "VECTOR_UCAST_B2S" + POSTFIX;
    static {
        vectorNode(VECTOR_UCAST_B2S, "VectorUCastB2X", TYPE_SHORT);
    }

    public static final String VECTOR_UCAST_B2I = VECTOR_PREFIX + "VECTOR_UCAST_B2I" + POSTFIX;
    static {
        vectorNode(VECTOR_UCAST_B2I, "VectorUCastB2X", TYPE_INT);
    }

    public static final String VECTOR_UCAST_B2L = VECTOR_PREFIX + "VECTOR_UCAST_B2L" + POSTFIX;
    static {
        vectorNode(VECTOR_UCAST_B2L, "VectorUCastB2X", TYPE_LONG);
    }

    public static final String VECTOR_UCAST_I2L = VECTOR_PREFIX + "VECTOR_UCAST_I2L" + POSTFIX;
    static {
        vectorNode(VECTOR_UCAST_I2L, "VectorUCastI2X", TYPE_LONG);
    }

    public static final String VECTOR_UCAST_S2I = VECTOR_PREFIX + "VECTOR_UCAST_S2I" + POSTFIX;
    static {
        vectorNode(VECTOR_UCAST_S2I, "VectorUCastS2X", TYPE_INT);
    }

    public static final String VECTOR_UCAST_S2L = VECTOR_PREFIX + "VECTOR_UCAST_S2L" + POSTFIX;
    static {
        vectorNode(VECTOR_UCAST_S2L, "VectorUCastS2X", TYPE_LONG);
    }

    public static final String VECTOR_TEST = PREFIX + "VECTOR_TEST" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_TEST, "VectorTest");
    }

    public static final String VFABD = PREFIX + "VFABD" + POSTFIX;
    static {
        machOnlyNameRegex(VFABD, "vfabd");
    }

    public static final String VFABD_MASKED = PREFIX + "VFABD_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VFABD_MASKED, "vfabd_masked");
    }

    public static final String VFMSB_MASKED = PREFIX + "VFMSB_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VFMSB_MASKED, "vfmsb_masked");
    }

    public static final String RISCV_VFNMSUB_MASKED = PREFIX + "RISCV_VFNMSUB_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(RISCV_VFNMSUB_MASKED, "vfnmsub_masked");
    }

    public static final String VFNMAD_MASKED = PREFIX + "VFNMAD_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VFNMAD_MASKED, "vfnmad_masked");
    }

    public static final String RISCV_VFNMADD_MASKED = PREFIX + "RISCV_VFNMADD_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(RISCV_VFNMADD_MASKED, "vfnmadd_masked");
    }

    public static final String VFNMSB_MASKED = PREFIX + "VFNMSB_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VFNMSB_MASKED, "vfnmsb_masked");
    }

    public static final String RISCV_VFMSUB_MASKED = PREFIX + "RISCV_VFMSUB_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(RISCV_VFMSUB_MASKED, "vfmsub_masked");
    }

    public static final String VFMAD_MASKED = PREFIX + "VFMAD_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VFMAD_MASKED, "vfmad_masked");
    }

    public static final String RISCV_VFMADD_MASKED = PREFIX + "RISCV_VFMADD_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(RISCV_VFMADD_MASKED, "vfmadd_masked");
    }

    public static final String VMASK_AND_NOT_L = PREFIX + "VMASK_AND_NOT_L" + POSTFIX;
    static {
        machOnlyNameRegex(VMASK_AND_NOT_L, "vmask_and_notL");
    }

    public static final String VMLA = PREFIX + "VMLA" + POSTFIX;
    static {
        machOnlyNameRegex(VMLA, "vmla");
    }

    public static final String VMLA_MASKED = PREFIX + "VMLA_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VMLA_MASKED, "vmla_masked");
    }

    public static final String FMSUB = PREFIX + "FMSUB" + POSTFIX;
    static {
        machOnlyNameRegex(FMSUB, "msub(F|D)_reg_reg");
    }

    public static final String FNMADD = PREFIX + "FNMADD" + POSTFIX;
    static {
        machOnlyNameRegex(FNMADD, "mnadd(F|D)_reg_reg");
    }

    public static final String FNMSUB = PREFIX + "FNMSUB" + POSTFIX;
    static {
        machOnlyNameRegex(FNMSUB, "mnsub(F|D)_reg_reg");
    }

    public static final String FMA_F = PREFIX + "FMA_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(FMA_F, "FmaF");
    }

    public static final String FMA_D = PREFIX + "FMA_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(FMA_D, "FmaD");
    }

    public static final String VFMLA = PREFIX + "VFMLA" + POSTFIX;
    static {
        machOnlyNameRegex(VFMLA, "vfmla");
    }

    public static final String VFMLS = PREFIX + "VFMLS" + POSTFIX;
    static {
        machOnlyNameRegex(VFMLS, "vfmls");
    }

    public static final String VFNMLA = PREFIX + "VFNMLA" + POSTFIX;
    static {
        machOnlyNameRegex(VFNMLA, "vfnmla");
    }

    public static final String VMLS = PREFIX + "VMLS" + POSTFIX;
    static {
        machOnlyNameRegex(VMLS, "vmls");
    }

    public static final String VMLS_MASKED = PREFIX + "VMLS_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VMLS_MASKED, "vmls_masked");
    }

    public static final String VMASK_CMP_ZERO_I_NEON = PREFIX + "VMASK_CMP_ZERO_I_NEON" + POSTFIX;
    static {
        machOnlyNameRegex(VMASK_CMP_ZERO_I_NEON, "vmaskcmp_zeroI_neon");
    }

    public static final String VMASK_CMP_ZERO_L_NEON = PREFIX + "VMASK_CMP_ZERO_L_NEON" + POSTFIX;
    static {
        machOnlyNameRegex(VMASK_CMP_ZERO_L_NEON, "vmaskcmp_zeroL_neon");
    }

    public static final String VMASK_CMP_ZERO_F_NEON = PREFIX + "VMASK_CMP_ZERO_F_NEON" + POSTFIX;
    static {
        machOnlyNameRegex(VMASK_CMP_ZERO_F_NEON, "vmaskcmp_zeroF_neon");
    }

    public static final String VMASK_CMP_ZERO_D_NEON = PREFIX + "VMASK_CMP_ZERO_D_NEON" + POSTFIX;
    static {
        machOnlyNameRegex(VMASK_CMP_ZERO_D_NEON, "vmaskcmp_zeroD_neon");
    }

    public static final String VMASK_CMP_IMM_I_SVE = PREFIX + "VMASK_CMP_IMM_I_SVE" + POSTFIX;
    static {
        machOnlyNameRegex(VMASK_CMP_IMM_I_SVE, "vmaskcmp_immI_sve");
    }

    public static final String VMASK_CMPU_IMM_I_SVE = PREFIX + "VMASK_CMPU_IMM_I_SVE" + POSTFIX;
    static {
        machOnlyNameRegex(VMASK_CMPU_IMM_I_SVE, "vmaskcmpU_immI_sve");
    }

    public static final String VMASK_CMP_IMM_L_SVE = PREFIX + "VMASK_CMP_IMM_L_SVE" + POSTFIX;
    static {
        machOnlyNameRegex(VMASK_CMP_IMM_L_SVE, "vmaskcmp_immL_sve");
    }

    public static final String VMASK_CMPU_IMM_L_SVE = PREFIX + "VMASK_CMPU_IMM_L_SVE" + POSTFIX;
    static {
        machOnlyNameRegex(VMASK_CMPU_IMM_L_SVE, "vmaskcmpU_immL_sve");
    }

    public static final String VNOT_I_MASKED = PREFIX + "VNOT_I_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VNOT_I_MASKED, "vnotI_masked");
    }

    public static final String VNOT_L_MASKED = PREFIX + "VNOT_L_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VNOT_L_MASKED, "vnotL_masked");
    }

    public static final String VSTOREMASK_TRUECOUNT = PREFIX + "VSTOREMASK_TRUECOUNT" + POSTFIX;
    static {
        machOnlyNameRegex(VSTOREMASK_TRUECOUNT, "vstoremask_truecount_neon");
    }

    public static final String X86_SCONV_D2I = PREFIX + "X86_SCONV_D2I" + POSTFIX;
    static {
        machOnlyNameRegex(X86_SCONV_D2I, "convD2I_reg_reg");
    }

    public static final String X86_SCONV_D2L = PREFIX + "X86_SCONV_D2L" + POSTFIX;
    static {
        machOnlyNameRegex(X86_SCONV_D2L, "convD2L_reg_reg");
    }

    public static final String X86_SCONV_F2I = PREFIX + "X86_SCONV_F2I" + POSTFIX;
    static {
        machOnlyNameRegex(X86_SCONV_F2I, "convF2I_reg_reg");
    }

    public static final String X86_SCONV_F2L = PREFIX + "X86_SCONV_F2L" + POSTFIX;
    static {
        machOnlyNameRegex(X86_SCONV_F2L, "convF2L_reg_reg");
    }

    public static final String X86_SCONV_D2I_AVX10_2 = PREFIX + "X86_SCONV_D2I_AVX10_2" + POSTFIX;
    static {
        machOnlyNameRegex(X86_SCONV_D2I_AVX10_2, "convD2I_(reg_reg|reg_mem)_avx10_2");
    }

    public static final String X86_SCONV_D2L_AVX10_2 = PREFIX + "X86_SCONV_D2L_AVX10_2" + POSTFIX;
    static {
        machOnlyNameRegex(X86_SCONV_D2L_AVX10_2, "convD2L_(reg_reg|reg_mem)_avx10_2");
    }

    public static final String X86_SCONV_F2I_AVX10_2 = PREFIX + "X86_SCONV_F2I_AVX10_2" + POSTFIX;
    static {
        machOnlyNameRegex(X86_SCONV_F2I_AVX10_2, "convF2I_(reg_reg|reg_mem)_avx10_2");
    }

    public static final String X86_SCONV_F2L_AVX10_2 = PREFIX + "X86_SCONV_F2L_AVX10_2" + POSTFIX;
    static {
        machOnlyNameRegex(X86_SCONV_F2L_AVX10_2, "convF2L_(reg_reg|reg_mem)_avx10_2");
    }

    public static final String X86_VCAST_F2X = PREFIX + "X86_VCAST_F2X" + POSTFIX;
    static {
        machOnlyNameRegex(X86_VCAST_F2X, "castFtoX_reg_(av|eve)x");
    }

    public static final String X86_VCAST_D2X = PREFIX + "X86_VCAST_D2X" + POSTFIX;
    static {
        machOnlyNameRegex(X86_VCAST_D2X, "castDtoX_reg_(av|eve)x");
    }

    public static final String X86_VCAST_F2X_AVX10_2 = PREFIX + "X86_VCAST_F2X_AVX10_2" + POSTFIX;
    static {
        machOnlyNameRegex(X86_VCAST_F2X_AVX10_2, "castFtoX_(reg|mem)_avx10_2");
    }

    public static final String X86_VCAST_D2X_AVX10_2 = PREFIX + "X86_VCAST_D2X_AVX10_2" + POSTFIX;
    static {
        machOnlyNameRegex(X86_VCAST_D2X_AVX10_2, "castDtoX_(reg|mem)_avx10_2");
    }

    public static final String XOR = PREFIX + "XOR" + POSTFIX;
    static {
        beforeMatchingNameRegex(XOR, "Xor(I|L)");
    }

    public static final String XOR_I = PREFIX + "XOR_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(XOR_I, "XorI");
    }

    public static final String XOR_L = PREFIX + "XOR_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(XOR_L, "XorL");
    }

    public static final String XOR_VB = VECTOR_PREFIX + "XOR_VB" + POSTFIX;
    static {
        vectorNode(XOR_VB, "XorV", TYPE_BYTE);
    }

    public static final String XOR_VS = VECTOR_PREFIX + "XOR_VS" + POSTFIX;
    static {
        vectorNode(XOR_VS, "XorV", TYPE_SHORT);
    }

    public static final String XOR_VI = VECTOR_PREFIX + "XOR_VI" + POSTFIX;
    static {
        vectorNode(XOR_VI, "XorV", TYPE_INT);
    }

    public static final String XOR_VL = VECTOR_PREFIX + "XOR_VL" + POSTFIX;
    static {
        vectorNode(XOR_VL, "XorV", TYPE_LONG);
    }

    public static final String XOR_V = PREFIX + "XOR_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(XOR_V, "XorV");
    }

    public static final String XOR_V_MASK = PREFIX + "XOR_V_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(XOR_V_MASK, "XorVMask");
    }

    public static final String XOR_REDUCTION_V = PREFIX + "XOR_REDUCTION_V" + POSTFIX;
    static {
        superWordNodes(XOR_REDUCTION_V, "XorReductionV");
    }

    public static final String XOR3_NEON = PREFIX + "XOR3_NEON" + POSTFIX;
    static {
        machOnlyNameRegex(XOR3_NEON, "veor3_neon");
    }

    public static final String XOR3_SVE = PREFIX + "XOR3_SVE" + POSTFIX;
    static {
        machOnlyNameRegex(XOR3_SVE, "veor3_sve");
    }

    public static final String COMPRESS_BITS_VI = VECTOR_PREFIX + "COMPRESS_BITS_VI" + POSTFIX;
    static {
        vectorNode(COMPRESS_BITS_VI, "CompressBitsV", TYPE_INT);
    }

    public static final String COMPRESS_BITS_VL = VECTOR_PREFIX + "COMPRESS_BITS_VL" + POSTFIX;
    static {
        vectorNode(COMPRESS_BITS_VL, "CompressBitsV", TYPE_LONG);
    }

    public static final String EXPAND_BITS_VI = VECTOR_PREFIX + "EXPAND_BITS_VI" + POSTFIX;
    static {
        vectorNode(EXPAND_BITS_VI, "ExpandBitsV", TYPE_INT);
    }

    public static final String EXPAND_BITS_VL = VECTOR_PREFIX + "EXPAND_BITS_VL" + POSTFIX;
    static {
        vectorNode(EXPAND_BITS_VL, "ExpandBitsV", TYPE_LONG);
    }

    public static final String COMPRESS_VB = VECTOR_PREFIX + "COMPRESS_VB" + POSTFIX;
    static {
        vectorNode(COMPRESS_VB, "CompressV", TYPE_BYTE);
    }

    public static final String COMPRESS_VS = VECTOR_PREFIX + "COMPRESS_VS" + POSTFIX;
    static {
        vectorNode(COMPRESS_VS, "CompressV", TYPE_SHORT);
    }

    public static final String COMPRESS_VI = VECTOR_PREFIX + "COMPRESS_VI" + POSTFIX;
    static {
        vectorNode(COMPRESS_VI, "CompressV", TYPE_INT);
    }

    public static final String COMPRESS_VL = VECTOR_PREFIX + "COMPRESS_VL" + POSTFIX;
    static {
        vectorNode(COMPRESS_VL, "CompressV", TYPE_LONG);
    }

    public static final String COMPRESS_VF = VECTOR_PREFIX + "COMPRESS_VF" + POSTFIX;
    static {
        vectorNode(COMPRESS_VF, "CompressV", TYPE_FLOAT);
    }

    public static final String COMPRESS_VD = VECTOR_PREFIX + "COMPRESS_VD" + POSTFIX;
    static {
        vectorNode(COMPRESS_VD, "CompressV", TYPE_DOUBLE);
    }

    public static final String EXPAND_VB = VECTOR_PREFIX + "EXPAND_VB" + POSTFIX;
    static {
        vectorNode(EXPAND_VB, "ExpandV", TYPE_BYTE);
    }

    public static final String EXPAND_VS = VECTOR_PREFIX + "EXPAND_VS" + POSTFIX;
    static {
        vectorNode(EXPAND_VS, "ExpandV", TYPE_SHORT);
    }

    public static final String EXPAND_VI = VECTOR_PREFIX + "EXPAND_VI" + POSTFIX;
    static {
        vectorNode(EXPAND_VI, "ExpandV", TYPE_INT);
    }

    public static final String EXPAND_VL = VECTOR_PREFIX + "EXPAND_VL" + POSTFIX;
    static {
        vectorNode(EXPAND_VL, "ExpandV", TYPE_LONG);
    }

    public static final String EXPAND_VF = VECTOR_PREFIX + "EXPAND_VF" + POSTFIX;
    static {
        vectorNode(EXPAND_VF, "ExpandV", TYPE_FLOAT);
    }

    public static final String EXPAND_VD = VECTOR_PREFIX + "EXPAND_VD" + POSTFIX;
    static {
        vectorNode(EXPAND_VD, "ExpandV", TYPE_DOUBLE);
    }

    public static final String Z_LOAD_P_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "Z_LOAD_P_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "zLoadP\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(Z_LOAD_P_WITH_BARRIER_FLAG, regex);
    }

    public static final String Z_STORE_P_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "Z_STORE_P_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "zStoreP\\S*" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(Z_STORE_P_WITH_BARRIER_FLAG, regex);
    }

    public static final String Z_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "Z_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "zCompareAndSwapP" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(Z_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG, regex);
    }

    public static final String Z_GET_AND_SET_P_WITH_BARRIER_FLAG = COMPOSITE_PREFIX + "Z_GET_AND_SET_P_WITH_BARRIER_FLAG" + POSTFIX;
    static {
        String regex = START + "(zXChgP)|(zGetAndSetP\\S*)" + MID + "barrier\\(\\s*" + IS_REPLACED + "\\s*\\)" + END;
        machOnly(Z_GET_AND_SET_P_WITH_BARRIER_FLAG, regex);
    }

    public static final String X86_LOCK_ADDB_REG = PREFIX + "X86_LOCK_ADDB_REG" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_ADDB_REG, "xaddB_reg_no_res");
    }

    public static final String X86_LOCK_ADDB_IMM = PREFIX + "X86_LOCK_ADDB_IMM" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_ADDB_IMM, "xaddB_imm_no_res");
    }

    public static final String X86_LOCK_XADDB = PREFIX + "X86_LOCK_XADDB" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_XADDB, "xaddB");
    }

    public static final String X86_LOCK_ADDS_REG = PREFIX + "X86_LOCK_ADDS_REG" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_ADDS_REG, "xaddS_reg_no_res");
    }

    public static final String X86_LOCK_ADDS_IMM = PREFIX + "X86_LOCK_ADDS_IMM" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_ADDS_IMM, "xaddS_imm_no_res");
    }

    public static final String X86_LOCK_XADDS = PREFIX + "X86_LOCK_XADDS" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_XADDS, "xaddS");
    }

    public static final String X86_LOCK_ADDI_REG = PREFIX + "X86_LOCK_ADDI_REG" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_ADDI_REG, "xaddI_reg_no_res");
    }

    public static final String X86_LOCK_ADDI_IMM = PREFIX + "X86_LOCK_ADDI_IMM" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_ADDI_IMM, "xaddI_imm_no_res");
    }

    public static final String X86_LOCK_XADDI = PREFIX + "X86_LOCK_XADDI" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_XADDI, "xaddI");
    }

    public static final String X86_LOCK_ADDL_REG = PREFIX + "X86_LOCK_ADDL_REG" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_ADDL_REG, "xaddL_reg_no_res");
    }

    public static final String X86_LOCK_ADDL_IMM = PREFIX + "X86_LOCK_ADDL_IMM" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_ADDL_IMM, "xaddL_imm_no_res");
    }

    public static final String X86_LOCK_XADDL = PREFIX + "X86_LOCK_XADDL" + POSTFIX;
    static {
        machOnlyNameRegex(X86_LOCK_XADDL, "xaddL");
    }

    public static final String X86_TESTI_REG = PREFIX + "X86_TESTI_REG" + POSTFIX;
    static {
        machOnlyNameRegex(X86_TESTI_REG, "testI_reg");
    }

    public static final String X86_TESTL_REG = PREFIX + "X86_TESTL_REG" + POSTFIX;
    static {
        machOnlyNameRegex(X86_TESTL_REG, "testL_reg");
    }

    public static final String X86_CMOVEL_IMM01 = PREFIX + "X86_CMOVEL_IMM01" + POSTFIX;
    static {
        machOnlyNameRegex(X86_CMOVEL_IMM01, "cmovL_imm_01");
    }

    public static final String X86_CMOVEL_IMM01U = PREFIX + "X86_CMOVEL_IMM01U" + POSTFIX;
    static {
        machOnlyNameRegex(X86_CMOVEL_IMM01U, "cmovL_imm_01U");
    }

    public static final String X86_CMOVEL_IMM01UCF = PREFIX + "X86_CMOVEL_IMM01UCF" + POSTFIX;
    static {
        machOnlyNameRegex(X86_CMOVEL_IMM01UCF, "cmovL_imm_01UCF");
    }

    public static final String X86_CMOVEL_IMM01UCFE = PREFIX + "X86_CMOVEL_IMM01UCFE" + POSTFIX;
    static {
        machOnlyNameRegex(X86_CMOVEL_IMM01UCFE, "cmovL_imm_01UCFE");
    }

    public static final String MOD_F = PREFIX + "MOD_F" + POSTFIX;
    static {
        String regex = START + "ModF" + MID + END;
        macroNodes(MOD_F, regex);
    }

    public static final String MOD_D = PREFIX + "MOD_D" + POSTFIX;
    static {
        String regex = START + "ModD" + MID + END;
        macroNodes(MOD_D, regex);
    }

    public static final String BLACKHOLE = PREFIX + "BLACKHOLE" + POSTFIX;
    static {
        fromBeforeRemoveUselessToFinalCode(BLACKHOLE, "Blackhole");
    }

    public static final String SELECT_FROM_TWO_VECTOR_VB = VECTOR_PREFIX + "SELECT_FROM_TWO_VECTOR_VB" + POSTFIX;
    static {
        vectorNode(SELECT_FROM_TWO_VECTOR_VB, "SelectFromTwoVector", TYPE_BYTE);
    }

    public static final String SELECT_FROM_TWO_VECTOR_VS = VECTOR_PREFIX + "SELECT_FROM_TWO_VECTOR_VS" + POSTFIX;
    static {
        vectorNode(SELECT_FROM_TWO_VECTOR_VS, "SelectFromTwoVector", TYPE_SHORT);
    }

    public static final String SELECT_FROM_TWO_VECTOR_VI = VECTOR_PREFIX + "SELECT_FROM_TWO_VECTOR_VI" + POSTFIX;
    static {
        vectorNode(SELECT_FROM_TWO_VECTOR_VI, "SelectFromTwoVector", TYPE_INT);
    }

    public static final String SELECT_FROM_TWO_VECTOR_VF = VECTOR_PREFIX + "SELECT_FROM_TWO_VECTOR_VF" + POSTFIX;
    static {
        vectorNode(SELECT_FROM_TWO_VECTOR_VF, "SelectFromTwoVector", TYPE_FLOAT);
    }

    public static final String SELECT_FROM_TWO_VECTOR_VD = VECTOR_PREFIX + "SELECT_FROM_TWO_VECTOR_VD" + POSTFIX;
    static {
        vectorNode(SELECT_FROM_TWO_VECTOR_VD, "SelectFromTwoVector", TYPE_DOUBLE);
    }

    public static final String SELECT_FROM_TWO_VECTOR_VL = VECTOR_PREFIX + "SELECT_FROM_TWO_VECTOR_VL" + POSTFIX;
    static {
        vectorNode(SELECT_FROM_TWO_VECTOR_VL, "SelectFromTwoVector", TYPE_LONG);
    }

    public static final String REPLICATE_HF = PREFIX + "REPLICATE_HF" + POSTFIX;
    static {
        machOnlyNameRegex(REPLICATE_HF, "replicateHF");
    }

    public static final String REPLICATE_HF_IMM8 = PREFIX + "REPLICATE_HF_IMM8" + POSTFIX;
    static {
        machOnlyNameRegex(REPLICATE_HF_IMM8, "replicateHF_imm8_gt128b");
    }

    public static final String OPAQUE_CONSTANT_BOOL = PREFIX + "OPAQUE_CONSTANT_BOOL" + POSTFIX;
    static {
        beforeMatchingNameRegex(OPAQUE_CONSTANT_BOOL, "OpaqueConstantBool");
    }

    /*
     * Utility methods to set up IR_NODE_MAPPINGS.
     */

    /**
     * Apply {@code regex} on all machine independent ideal graph phases up to and including
     * {@link CompilePhase#BEFORE_MATCHING}.
     */
    private static void beforeMatching(String irNodePlaceholder, String regex) {
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new RegexTypeEntry(RegexType.IDEAL_INDEPENDENT, regex));
    }

    /**
     * Apply {@code irNodeRegex} as regex for the IR node name on all machine independent ideal graph phases up to and
     * including {@link CompilePhase#BEFORE_MATCHING}.
     */
    private static void beforeMatchingNameRegex(String irNodePlaceholder, String irNodeRegex) {
        String regex = START + irNodeRegex + MID + END;
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new RegexTypeEntry(RegexType.IDEAL_INDEPENDENT, regex));
    }

    /**
     * Apply {@code irNodeRegex} as regex for the IR vector node name on all machine independent ideal graph phases up to and
     * including {@link CompilePhase#BEFORE_MATCHING}. Since this is a vector node, we can also check the vector element
     * type {@code typeString} and the vector size (number of elements), {@see VECTOR_SIZE}.
     */
    private static void vectorNode(String irNodePlaceholder, String irNodeRegex, String typeString) {
        TestFramework.check(isVectorIRNode(irNodePlaceholder), "vectorNode: failed prefix check for irNodePlaceholder "
                                                               + irNodePlaceholder + " -> did you use VECTOR_PREFIX?");
        // IS_REPLACED is later replaced with the specific type and size of the vector.
        String regex = START + irNodeRegex + MID  + IS_REPLACED + END;
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new RegexTypeEntry(RegexType.IDEAL_INDEPENDENT, regex));
        VECTOR_NODE_TYPE.put(irNodePlaceholder, typeString);
    }

    /**
     * Apply {@code regex} on all ideal graph phases up to and including {@link CompilePhase#BEFORE_MACRO_EXPANSION}.
     */
    private static void macroNodes(String irNodePlaceholder, String regex) {
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new SinglePhaseRangeEntry(CompilePhase.BEFORE_MACRO_EXPANSION, regex,
                                                                          CompilePhase.BEFORE_STRINGOPTS,
                                                                          CompilePhase.BEFORE_MACRO_EXPANSION));
    }

    private static void callOfNodes(String irNodePlaceholder, String callRegex) {
        String regex = START + callRegex + MID + IS_REPLACED + " " +  END;
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new RegexTypeEntry(RegexType.IDEAL_INDEPENDENT, regex));
    }

    /**
     * Apply {@code regex} on all machine dependant ideal graph phases (i.e. on the mach graph) starting from
     * {@link CompilePhase#MATCHING}.
     */
    private static void optoOnly(String irNodePlaceholder, String regex) {
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new RegexTypeEntry(RegexType.OPTO_ASSEMBLY, regex));
    }

    private static void machOnly(String irNodePlaceholder, String regex) {
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new RegexTypeEntry(RegexType.MACH, regex));
    }

    private static void machOnlyNameRegex(String irNodePlaceholder, String irNodeRegex) {
        String regex = START + irNodeRegex + MID + END;
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new RegexTypeEntry(RegexType.MACH, regex));
    }

    /**
     * Apply {@code regex} on all ideal graph phases starting from {@link CompilePhase#AFTER_CLOOPS}.
     */
    private static void fromAfterCountedLoops(String irNodePlaceholder, String regex) {
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                          CompilePhase.AFTER_CLOOPS,
                                                                          CompilePhase.FINAL_CODE));
    }

    /**
     * Apply {@code regex} on all ideal graph phases starting from {@link CompilePhase#BEFORE_CLOOPS}.
     */
    private static void fromBeforeCountedLoops(String irNodePlaceholder, String regex) {
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                          CompilePhase.BEFORE_CLOOPS,
                                                                          CompilePhase.FINAL_CODE));
    }

    /**
     * Apply {@code regex} on all ideal graph phases starting from {@link CompilePhase#BEFORE_CLOOPS} up to and
     * including {@link CompilePhase#BEFORE_MATCHING}
     */
    private static void fromMacroToBeforeMatching(String irNodePlaceholder, String regex) {
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                          CompilePhase.AFTER_MACRO_EXPANSION,
                                                                          CompilePhase.BEFORE_MATCHING));
    }

    /**
     * Apply {@code regex} on all ideal graph phases starting from {@link CompilePhase#BEFORE_CLOOPS} up to and
     * including {@link CompilePhase#BEFORE_MATCHING}
     */
    private static void afterBarrierExpansionToBeforeMatching(String irNodePlaceholder, String regex) {
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                          CompilePhase.OPTIMIZE_FINISHED,
                                                                          CompilePhase.BEFORE_MATCHING));
    }

    /**
     * Apply {@code regex} on all ideal graph phases starting from {@link CompilePhase#BEFORE_LOOP_OPTS}
     * up to and including {@link CompilePhase#AFTER_LOOP_OPTS}.
     */
    private static void duringLoopOpts(String irNodePlaceholder, String regex) {
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new SinglePhaseRangeEntry(CompilePhase.AFTER_LOOP_OPTS, regex,
                                                                          CompilePhase.BEFORE_LOOP_OPTS,
                                                                          CompilePhase.AFTER_LOOP_OPTS));
    }

    private static void trapNodes(String irNodePlaceholder, String trapReason) {
        String regex = START + "CallStaticJava" + MID + "uncommon_trap.*" + trapReason + END;
        beforeMatching(irNodePlaceholder, regex);
    }

    private static void parsePredicateNodes(String irNodePlaceholder, String label) {
        String regex = START + "ParsePredicate" + MID + "#" + label + " " + END;
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new SinglePhaseRangeEntry(CompilePhase.AFTER_PARSING, regex,
                                                                          CompilePhase.AFTER_PARSING,
                                                                          CompilePhase.AFTER_LOOP_OPTS));
    }

    // Typename in load/store have the structure:
    // @ptrtype:fully/qualified/package/name/to/TheClass:ptrlattice+12
    // with ptrtype being the kind of the type such as instptr, aryptr, etc, and ptrlattice being
    // the kind of the value such as BotPTR, NotNull, etc.
    // And variation:
    // - after ptrtype, we can have "stable:" or other labels, with optional space after ':'
    // - the class can actually be a nested class, with $ separator (and it must be ok to give only the deepest one
    // - after the class name, we can have a comma-separated list of implemented interfaces enclosed in parentheses
    // Worst case, it can be something like:
    // @bla: bli:a/b/c$d$e (f/g,h/i/j):NotNull+24

    // @ matches the start character of the pattern
    // (\w+: ?)+ tries to match the pattern 'ptrtype:' or 'stable:' with optional trailing whitespaces
    // [\\w/\\$] tries to match the pattern such as 'a/b/', 'a/b', or '/b' but also nested class such as '$c' or '$c$d'
    // \b asserts that the next character is a word character
    private static final String LOAD_STORE_PREFIX = "@(\\w+: ?)+[\\w/\\$]*\\b";
    // ( \([^\)]+\))? tries to match the pattern ' (f/g,h/i/j)'
    // :\w+ tries to match the pattern ':NotNull'
    // .* tries to match the remaining of the pattern
    private static final String LOAD_STORE_SUFFIX = "( \\([^\\)]+\\))?:\\w+.*";

    private static void loadOfNodes(String irNodePlaceholder, String irNodeRegex) {
        String regex = START + irNodeRegex + MID + LOAD_STORE_PREFIX + IS_REPLACED + LOAD_STORE_SUFFIX + END;
        beforeMatching(irNodePlaceholder, regex);
    }

    private static void storeOfNodes(String irNodePlaceholder, String irNodeRegex) {
        String regex = START + irNodeRegex + MID + LOAD_STORE_PREFIX + IS_REPLACED + LOAD_STORE_SUFFIX + END;
        beforeMatching(irNodePlaceholder, regex);
    }

    private static void safepointScalarobjectOfNodes(String irNodePlaceholder, String irNodeRegex) {
        String regex = START + irNodeRegex + MID + ".*" + IS_REPLACED + ".*" + END;
        beforeMatching(irNodePlaceholder, regex);
    }

    private static void fromBeforeRemoveUselessToFinalCode(String irNodePlaceholder, String irNodeRegex) {
        String regex = START + irNodeRegex + MID + END;
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                CompilePhase.BEFORE_REMOVEUSELESS,
                CompilePhase.FINAL_CODE));
    }

    /**
     * Apply {@code regex} on all ideal graph phases starting from {@link CompilePhase#PHASEIDEALLOOP1} which is the
     * first phase that could contain vector nodes from super word.
     */
    private static void superWordNodes(String irNodePlaceholder, String irNodeRegex) {
        String regex = START + irNodeRegex + MID + END;
        IR_NODE_MAPPINGS.put(irNodePlaceholder, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                          CompilePhase.PHASEIDEALLOOP1,
                                                                          CompilePhase.BEFORE_MATCHING));
    }


    /*
     * Methods used internally by the IR framework.
     */

    /**
     * Is {@code irNodeString} an IR node placeholder string?
     */
    public static boolean isIRNode(String irNodeString) {
        return irNodeString.startsWith(PREFIX);
    }

    /**
     * Is {@code irCompositeNodeString} an IR composite node placeholder string?
     */
    public static boolean isCompositeIRNode(String irCompositeNodeString) {
        return irCompositeNodeString.startsWith(COMPOSITE_PREFIX);
    }

    /**
     * Is {@code irVectorNodeString} an IR vector node placeholder string?
     */
    public static boolean isVectorIRNode(String irVectorNodeString) {
        return irVectorNodeString.startsWith(VECTOR_PREFIX);
    }

    /**
     * Is {@code irVectorSizeString} a vector size string?
     */
    public static boolean isVectorSize(String irVectorSizeString) {
        return irVectorSizeString.startsWith(VECTOR_SIZE);
    }

    /**
     * Parse {@code sizeString} and generate a regex pattern to match for the size in the IR dump.
     */
    public static String parseVectorNodeSize(String sizeString, String typeString, VMInfo vmInfo) {
        if (sizeString.equals(VECTOR_SIZE_TAG_ANY)) {
            return "\\\\d+"; // match with any number
        }
        // Try to parse any tags, convert to comma separated list of ints
        sizeString = parseVectorNodeSizeTag(sizeString, typeString, vmInfo);
        // Parse comma separated list of numbers
        String[] sizes = sizeString.split(",");
        String regex = "";
        for (int i = 0; i < sizes.length; i++) {
            int size = 0;
            try {
                size = Integer.parseInt(sizes[i]);
            } catch (NumberFormatException e) {
                throw new TestFormatException("Vector node has invalid size \"" + sizes[i] + "\", in \"" + sizeString + "\"");
            }
            TestFormat.checkNoReport(size > 1, "Vector node size must be 2 or larger, but got \"" + sizes[i] + "\", in \"" + sizeString + "\"");
            regex += ((i > 0) ? "|" : "") + size;
        }
        if (sizes.length > 1) {
           regex = "(" + regex + ")";
        }
        return regex;
    }

    /**
     * If {@code sizeTagString} is a size tag, return the list of accepted sizes, else return sizeTagString.
     */
    public static String parseVectorNodeSizeTag(String sizeTagString, String typeString, VMInfo vmInfo) {
        // Parse out "min(a,b,c,...)"
        if (sizeTagString.startsWith("min(") && sizeTagString.endsWith(")")) {
            return parseVectorNodeSizeTagMin(sizeTagString, typeString, vmInfo);
        }

        // Parse individual tags
        return switch (sizeTagString) {
            case VECTOR_SIZE_TAG_MAX -> String.valueOf(getMaxElementsForType(typeString, vmInfo));
            case "max_byte"          -> String.valueOf(getMaxElementsForType(TYPE_BYTE, vmInfo));
            case "max_char"          -> String.valueOf(getMaxElementsForType(TYPE_CHAR, vmInfo));
            case "max_short"         -> String.valueOf(getMaxElementsForType(TYPE_SHORT, vmInfo));
            case "max_int"           -> String.valueOf(getMaxElementsForType(TYPE_INT, vmInfo));
            case "max_long"          -> String.valueOf(getMaxElementsForType(TYPE_LONG, vmInfo));
            case "max_float"         -> String.valueOf(getMaxElementsForType(TYPE_FLOAT, vmInfo));
            case "max_double"        -> String.valueOf(getMaxElementsForType(TYPE_DOUBLE, vmInfo));
            case "LoopMaxUnroll"     -> String.valueOf(vmInfo.getLongValue("LoopMaxUnroll"));
            default                  -> sizeTagString;
        };
    }

    /**
     * Parse {@code sizeTagString}, which must be a min-clause.
     */
    public static String parseVectorNodeSizeTagMin(String sizeTagString, String typeString, VMInfo vmInfo) {
        String[] tags = sizeTagString.substring(4, sizeTagString.length() - 1).split(",");
        TestFormat.checkNoReport(tags.length > 1, "Vector node size \"min(...)\" must have at least 2 comma separated arguments, got \"" + sizeTagString + "\"");
        int minVal = 1024;
        for (int i = 0; i < tags.length; i++) {
            String tag = parseVectorNodeSizeTag(tags[i].trim(), typeString, vmInfo);
            int tag_val = 0;
            try {
                tag_val = Integer.parseInt(tag);
            } catch (NumberFormatException e) {
                throw new TestFormatException("Vector node has invalid size in \"min(...)\", argument " + i + ", \"" + tag + "\", in \"" + sizeTagString + "\"");
            }
            minVal = Math.min(minVal, tag_val);
        }
        return String.valueOf(minVal);
    }

    /**
     * Return maximal number of elements that can fit in a vector of the specified type.
     */
    public static long getMaxElementsForType(String typeString, VMInfo vmInfo) {
        long maxVectorSize = vmInfo.getLongValue("MaxVectorSize");
        TestFormat.checkNoReport(maxVectorSize > 0, "VMInfo: MaxVectorSize is not larger than zero");
        long maxBytes = maxVectorSize;

        if (Platform.isX64() || Platform.isX86()) {
            maxBytes = Math.min(maxBytes, getMaxElementsForTypeOnX86(typeString, vmInfo));
        }

        // compute elements per vector: vector bytes divided by bytes per element
        int bytes = getTypeSizeInBytes(typeString);
        return maxBytes / bytes;
    }

    /**
     * Return maximal number of elements that can fit in a vector of the specified type, on x86 / x64.
     */
    public static long getMaxElementsForTypeOnX86(String typeString, VMInfo vmInfo) {
        // restrict maxBytes for specific features, see Matcher::vector_width_in_bytes in x86.ad:
        boolean avx1 = vmInfo.hasCPUFeature("avx");
        boolean avx2 = vmInfo.hasCPUFeature("avx2");
        boolean avx512 = vmInfo.hasCPUFeature("avx512f");
        boolean avx512bw = vmInfo.hasCPUFeature("avx512bw");
        long maxBytes;
        if (avx512) {
            maxBytes = 64;
        } else if (avx2) {
            maxBytes = 32;
        } else {
            maxBytes = 16;
        }
        if (avx1 && (typeString.equals(TYPE_FLOAT) || typeString.equals(TYPE_DOUBLE))) {
            maxBytes = avx512 ? 64 : 32;
        }
        if (avx512 && (typeString.equals(TYPE_BYTE) || typeString.equals(TYPE_SHORT) || typeString.equals(TYPE_CHAR))) {
            maxBytes = avx512bw ? 64 : 32;
        }

        return maxBytes;
    }

    /**
     * Return size in bytes of type named by {@code typeString}, return 0 if it does not name a type.
     */
    public static int getTypeSizeInBytes(String typeString) {
        return switch (typeString) {
            case TYPE_BYTE              -> 1;
            case TYPE_CHAR, TYPE_SHORT  -> 2;
            case TYPE_INT, TYPE_FLOAT   -> 4;
            case TYPE_LONG, TYPE_DOUBLE -> 8;
            default                     -> 0;
        };
    }

    /**
     * Returns "IRNode.XYZ", where XYZ is one of the IR node placeholder variable names defined above.
     */
    public static String getIRNodeAccessString(String irNodeString) {
        int prefixLength;
        if (isCompositeIRNode(irNodeString)) {
            TestFramework.check(irNodeString.length() > COMPOSITE_PREFIX.length() + POSTFIX.length(),
                                "Invalid composite node placeholder: " + irNodeString);
            prefixLength = COMPOSITE_PREFIX.length();
        } else if (isVectorIRNode(irNodeString)) {
            TestFramework.check(irNodeString.length() > VECTOR_PREFIX.length() + POSTFIX.length(),
                                "Invalid vector node placeholder: " + irNodeString);
            prefixLength = VECTOR_PREFIX.length();
        } else {
            prefixLength = PREFIX.length();
        }
        return "IRNode." + irNodeString.substring(prefixLength, irNodeString.length() - POSTFIX.length());
    }

    /**
     * Is this IR node supported on current platform, used VM build, etc.?
     * Throws a {@link CheckedTestFrameworkException} if the IR node is unsupported.
     */
    public static void checkIRNodeSupported(String node) throws CheckedTestFrameworkException {
        switch (node) {
            case INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP -> {
                if (!WhiteBox.getWhiteBox().isJVMCISupportedByGC()) {
                    throw new CheckedTestFrameworkException("INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP is unsupported " +
                                                            "in builds without JVMCI.");
                }
            }
            case CHECKCAST_ARRAYCOPY -> {
                if (Platform.isS390x()) {
                    throw new CheckedTestFrameworkException("CHECKCAST_ARRAYCOPY is unsupported on s390.");
                }
            }
            case IS_FINITE_D, IS_FINITE_F -> {
                if (!Platform.isRISCV64()) {
                    throw new CheckedTestFrameworkException("IS_FINITE_* is only supported on riscv64.");
                }
            }
            // default: do nothing -> IR node is supported and can be used by the user.
        }
    }

    /**
     * Get the regex of an IR node for a specific compile phase. If {@code irNode} is not an IR node placeholder string
     * or if there is no regex specified for {@code compilePhase}, a {@link TestFormatException} is reported.
     */
    public static String getRegexForCompilePhase(String irNode, CompilePhase compilePhase) {
        IRNodeMapEntry entry = IR_NODE_MAPPINGS.get(irNode);
        String failMsg = "IR Node \"" + irNode + "\" defined in class IRNode has no regex/compiler phase mapping " +
                         "(i.e. no static initializer block that adds a mapping entry to IRNode.IR_NODE_MAPPINGS)." +
                         System.lineSeparator() +
                         "   Have you just created the entry \"" + irNode + "\" in class IRNode and forgot to add a " +
                         "mapping?" + System.lineSeparator() +
                         "   Violation";
        TestFormat.checkNoReport(entry != null, failMsg);
        String regex = entry.regexForCompilePhase(compilePhase);
        failMsg = "IR Node \"" + irNode + "\" defined in class IRNode has no regex defined for compile phase "
                  + compilePhase + "." + System.lineSeparator() +
                  "   If you think this compile phase should be supported, update the mapping for \"" + irNode +
                  "\" in class IRNode (i.e the static initializer block immediately following the definition of \"" +
                  irNode + "\")." + System.lineSeparator() +
                  "   Violation";
        TestFormat.checkNoReport(regex != null, failMsg);
        return regex;
    }

    /**
     * Get the default phase of an IR node. If {@code irNode} is not an IR node placeholder string, a
     * {@link TestFormatException} is reported.
     */
    public static CompilePhase getDefaultPhase(String irNode) {
        IRNodeMapEntry entry = IR_NODE_MAPPINGS.get(irNode);
        String failMsg = "\"" + irNode + "\" is not an IR node defined in class IRNode and " +
                         "has therefore no default compile phase specified." + System.lineSeparator() +
                         "   If your regex represents a C2 IR node, consider adding an entry to class IRNode together " +
                         "with a static initializer block that adds a mapping to IRNode.IR_NODE_MAPPINGS." +
                         System.lineSeparator() +
                         "   Otherwise, set the @IR \"phase\" attribute to a compile phase different from " +
                         "CompilePhase.DEFAULT to explicitly tell the IR framework on which compile phase your rule" +
                         " should be applied on." + System.lineSeparator() +
                         "   Violation";
        TestFormat.checkNoReport(entry != null, failMsg);
        return entry.defaultCompilePhase();
    }

    public static String getVectorNodeType(String irNode) {
        String typeString = VECTOR_NODE_TYPE.get(irNode);
        String failMsg = "\"" + irNode + "\" is not a Vector IR node defined in class IRNode";
        TestFormat.check(typeString != null, failMsg);
        return typeString;
    }
}
