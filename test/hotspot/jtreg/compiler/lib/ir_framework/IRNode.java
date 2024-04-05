/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

    private static final String START = "(\\d+(\\s){2}(";
    private static final String MID = ".*)+(\\s){2}===.*";
    private static final String END = ")";
    private static final String STORE_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;
    private static final String LOAD_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;

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

    private static final String TYPE_BYTE   = "byte";
    private static final String TYPE_CHAR   = "char";
    private static final String TYPE_SHORT  = "short";
    private static final String TYPE_INT    = "int";
    private static final String TYPE_LONG   = "long";
    private static final String TYPE_FLOAT  = "float";
    private static final String TYPE_DOUBLE = "double";

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

    public static final String ADD_I = PREFIX + "ADD_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_I, "AddI");
    }

    public static final String ADD_L = PREFIX + "ADD_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_L, "AddL");
    }

    public static final String ADD_VD = VECTOR_PREFIX + "ADD_VD" + POSTFIX;
    static {
        vectorNode(ADD_VD, "AddVD", TYPE_DOUBLE);
    }

    public static final String ADD_VI = VECTOR_PREFIX + "ADD_VI" + POSTFIX;
    static {
        vectorNode(ADD_VI, "AddVI", TYPE_INT);
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

    public static final String ALLOC = PREFIX + "ALLOC" + POSTFIX;
    static {
        String optoRegex = "(.*precise .*\\R((.*(?i:mov|mv|xorl|nop|spill).*|\\s*)\\R)*.*(?i:call,static).*wrapper for: _new_instance_Java" + END;
        allocNodes(ALLOC, "Allocate", optoRegex);
    }

    public static final String ALLOC_OF = COMPOSITE_PREFIX + "ALLOC_OF" + POSTFIX;
    static {
        String regex = "(.*precise .*" + IS_REPLACED + ":.*\\R((.*(?i:mov|mv|xorl|nop|spill).*|\\s*)\\R)*.*(?i:call,static).*wrapper for: _new_instance_Java" + END;
        optoOnly(ALLOC_OF, regex);
    }

    public static final String ALLOC_ARRAY = PREFIX + "ALLOC_ARRAY" + POSTFIX;
    static {
        String optoRegex = "(.*precise \\[.*\\R((.*(?i:mov|mv|xor|nop|spill).*|\\s*|.*(LGHI|LI).*)\\R)*.*(?i:call,static).*wrapper for: _new_array_Java" + END;
        allocNodes(ALLOC_ARRAY, "AllocateArray", optoRegex);
    }

    public static final String ALLOC_ARRAY_OF = COMPOSITE_PREFIX + "ALLOC_ARRAY_OF" + POSTFIX;
    static {
        String regex = "(.*precise \\[.*" + IS_REPLACED + ":.*\\R((.*(?i:mov|mv|xorl|nop|spill).*|\\s*|.*(LGHI|LI).*)\\R)*.*(?i:call,static).*wrapper for: _new_array_Java" + END;
        optoOnly(ALLOC_ARRAY_OF, regex);
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

    public static final String CHECKCAST_ARRAY = PREFIX + "CHECKCAST_ARRAY" + POSTFIX;
    static {
        String regex = "(((?i:cmp|CLFI|CLR).*precise \\[.*:|.*(?i:mov|mv|or).*precise \\[.*:.*\\R.*(cmp|CMP|CLR))" + END;
        optoOnly(CHECKCAST_ARRAY, regex);
    }

    public static final String CHECKCAST_ARRAY_OF = COMPOSITE_PREFIX + "CHECKCAST_ARRAY_OF" + POSTFIX;
    static {
        String regex = "(((?i:cmp|CLFI|CLR).*precise \\[.*" + IS_REPLACED + ":|.*(?i:mov|mv|or).*precise \\[.*" + IS_REPLACED + ":.*\\R.*(cmp|CMP|CLR))" + END;
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

    public static final String CMOVE_I = PREFIX + "CMOVE_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(CMOVE_I, "CMoveI");
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
        beforeMatchingNameRegex(CMP_U, "CmpU");
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

    public static final String COMPRESS_BITS = PREFIX + "COMPRESS_BITS" + POSTFIX;
    static {
        beforeMatchingNameRegex(COMPRESS_BITS, "CompressBits");
    }

    public static final String CONV = PREFIX + "CONV" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV, "Conv");
    }

    public static final String CONV_I2L = PREFIX + "CONV_I2L" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_I2L, "ConvI2L");
    }

    public static final String CONV_L2I = PREFIX + "CONV_L2I" + POSTFIX;
    static {
        beforeMatchingNameRegex(CONV_L2I, "ConvL2I");
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

    public static final String DIV = PREFIX + "DIV" + POSTFIX;
    static {
        beforeMatchingNameRegex(DIV, "Div(I|L|F|D)");
    }

    public static final String DIV_BY_ZERO_TRAP = PREFIX + "DIV_BY_ZERO_TRAP" + POSTFIX;
    static {
        trapNodes(DIV_BY_ZERO_TRAP, "div0_check");
    }

    public static final String DIV_L = PREFIX + "DIV_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(DIV_L, "DivL");
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

    public static final String FMA_VF = VECTOR_PREFIX + "FMA_VF" + POSTFIX;
    static {
        vectorNode(FMA_VF, "FmaVF", TYPE_FLOAT);
    }

    public static final String FMA_VD = VECTOR_PREFIX + "FMA_VD" + POSTFIX;
    static {
        vectorNode(FMA_VD, "FmaVD", TYPE_DOUBLE);
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

    public static final String MACRO_LOGIC_V = PREFIX + "MACRO_LOGIC_V" + POSTFIX;
    static {
        afterBarrierExpansionToBeforeMatching(MACRO_LOGIC_V, "MacroLogicV");
    }

    public static final String MAX = PREFIX + "MAX" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX, "Max(I|L)");
    }

    public static final String MAX_D_REDUCTION_REG = PREFIX + "MAX_D_REDUCTION_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MAX_D_REDUCTION_REG, "maxD_reduction_reg");
    }

    public static final String MAX_D_REG = PREFIX + "MAX_D_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MAX_D_REG, "maxD_reg");
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

    public static final String MAX_VF = VECTOR_PREFIX + "MAX_VF" + POSTFIX;
    static {
        vectorNode(MAX_VF, "MaxV", TYPE_FLOAT);
    }

    public static final String MAX_VD = VECTOR_PREFIX + "MAX_VD" + POSTFIX;
    static {
        vectorNode(MAX_VD, "MaxV", TYPE_DOUBLE);
    }

    public static final String MEMBAR = PREFIX + "MEMBAR" + POSTFIX;
    static {
        beforeMatchingNameRegex(MEMBAR, "MemBar");
    }

    public static final String MEMBAR_STORESTORE = PREFIX + "MEMBAR_STORESTORE" + POSTFIX;
    static {
        beforeMatchingNameRegex(MEMBAR_STORESTORE, "MemBarStoreStore");
    }

    public static final String MIN = PREFIX + "MIN" + POSTFIX;
    static {
        beforeMatchingNameRegex(MIN, "Min(I|L)");
    }

    public static final String MIN_D_REDUCTION_REG = PREFIX + "MIN_D_REDUCTION_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MIN_D_REDUCTION_REG, "minD_reduction_reg");
    }

    public static final String MIN_D_REG = PREFIX + "MIN_D_REG" + POSTFIX;
    static {
        machOnlyNameRegex(MIN_D_REG, "minD_reg");
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

    public static final String MIN_VI = VECTOR_PREFIX + "MIN_VI" + POSTFIX;
    static {
        vectorNode(MIN_VI, "MinV", TYPE_INT);
    }

    public static final String MIN_VF = VECTOR_PREFIX + "MIN_VF" + POSTFIX;
    static {
        vectorNode(MIN_VF, "MinV", TYPE_FLOAT);
    }

    public static final String MIN_VD = VECTOR_PREFIX + "MIN_VD" + POSTFIX;
    static {
        vectorNode(MIN_VD, "MinV", TYPE_DOUBLE);
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

    public static final String NULL_CHECK_TRAP = PREFIX + "NULL_CHECK_TRAP" + POSTFIX;
    static {
        trapNodes(NULL_CHECK_TRAP, "null_check");
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

    public static final String COUNTTRAILINGZEROS_VL = VECTOR_PREFIX + "COUNTTRAILINGZEROS_VL" + POSTFIX;
    static {
        vectorNode(COUNTTRAILINGZEROS_VL, "CountTrailingZerosV", TYPE_LONG);
    }

    public static final String COUNTLEADINGZEROS_VL = VECTOR_PREFIX + "COUNTLEADINGZEROS_VL" + POSTFIX;
    static {
        vectorNode(COUNTLEADINGZEROS_VL, "CountLeadingZerosV", TYPE_LONG);
    }

    public static final String POPULATE_INDEX = PREFIX + "POPULATE_INDEX" + POSTFIX;
    static {
        String regex = START + "PopulateIndex" + MID + END;
        IR_NODE_MAPPINGS.put(POPULATE_INDEX, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                       CompilePhase.AFTER_CLOOPS,
                                                                       CompilePhase.BEFORE_MATCHING));
    }

    public static final String PREDICATE_TRAP = PREFIX + "PREDICATE_TRAP" + POSTFIX;
    static {
        trapNodes(PREDICATE_TRAP, "predicate");
    }

    public static final String RANGE_CHECK_TRAP = PREFIX + "RANGE_CHECK_TRAP" + POSTFIX;
    static {
        trapNodes(RANGE_CHECK_TRAP, "range_check");
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

    public static final String SIGNUM_VD = VECTOR_PREFIX + "SIGNUM_VD" + POSTFIX;
    static {
        vectorNode(SIGNUM_VD, "SignumVD", TYPE_DOUBLE);
    }

    public static final String SIGNUM_VF = VECTOR_PREFIX + "SIGNUM_VF" + POSTFIX;
    static {
        vectorNode(SIGNUM_VF, "SignumVF", TYPE_FLOAT);
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

    public static final String STORE_VECTOR_SCATTER_MASKED = PREFIX + "STORE_VECTOR_SCATTER_MASKED" + POSTFIX;
    static {
        beforeMatchingNameRegex(STORE_VECTOR_SCATTER_MASKED, "StoreVectorScatterMasked");
    }

    public static final String SUB = PREFIX + "SUB" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB, "Sub(I|L|F|D)");
    }

    public static final String SUB_D = PREFIX + "SUB_D" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB_D, "SubD");
    }

    public static final String SUB_F = PREFIX + "SUB_F" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB_F, "SubF");
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

    public static final String VECTOR_BLEND_B = VECTOR_PREFIX + "VECTOR_BLEND_B" + POSTFIX;
    static {
        vectorNode(VECTOR_BLEND_B, "VectorBlend", TYPE_BYTE);
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

    public static final String VFNMAD_MASKED = PREFIX + "VFNMAD_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VFNMAD_MASKED, "vfnmad_masked");
    }

    public static final String VFNMSB_MASKED = PREFIX + "VFNMSB_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VFNMSB_MASKED, "vfnmsb_masked");
    }

    public static final String VFMAD_MASKED = PREFIX + "VFMAD_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VFMAD_MASKED, "vfmad_masked");
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

    private static void allocNodes(String irNode, String irNodeName, String optoRegex) {
        String idealIndependentRegex = START + irNodeName + "\\b" + MID + END;
        Map<PhaseInterval, String> intervalToRegexMap = new HashMap<>();
        intervalToRegexMap.put(new PhaseInterval(CompilePhase.BEFORE_REMOVEUSELESS, CompilePhase.PHASEIDEALLOOP_ITERATIONS),
                               idealIndependentRegex);
        intervalToRegexMap.put(new PhaseInterval(CompilePhase.PRINT_OPTO_ASSEMBLY), optoRegex);
        MultiPhaseRangeEntry entry = new MultiPhaseRangeEntry(CompilePhase.PRINT_OPTO_ASSEMBLY, intervalToRegexMap);
        IR_NODE_MAPPINGS.put(irNode, entry);
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

    private static void trapNodes(String irNodePlaceholder, String trapReason) {
        String regex = START + "CallStaticJava" + MID + "uncommon_trap.*" + trapReason + END;
        beforeMatching(irNodePlaceholder, regex);
    }

    private static void loadOfNodes(String irNodePlaceholder, String irNodeRegex) {
        String regex = START + irNodeRegex + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
        beforeMatching(irNodePlaceholder, regex);
    }

    private static void storeOfNodes(String irNodePlaceholder, String irNodeRegex) {
        String regex = START + irNodeRegex + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
        beforeMatching(irNodePlaceholder, regex);
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
