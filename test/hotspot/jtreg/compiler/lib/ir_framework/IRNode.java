/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
    private static final String POSTFIX = "#_";

    private static final String START = "(\\d+(\\s){2}(";
    private static final String MID = ".*)+(\\s){2}===.*";
    private static final String END = ")";
    private static final String STORE_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;
    private static final String LOAD_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;

    public static final String IS_REPLACED = "#IS_REPLACED#"; // Is replaced by an additional user-defined string.


    /**
     * IR placeholder string to regex-for-compile-phase map.
     */
    private static final Map<String, IRNodeMapEntry> IR_NODE_MAPPINGS = new HashMap<>();

    /*
     * Start of IR placeholder string definitions followed by a static block defining the regex-for-compile-phase mapping.
     * An IR node placeholder string must start with PREFIX for normal IR nodes or COMPOSITE_PREFIX for composite IR
     * nodes (see class description above).
     *
     * An IR node definition looks like this:
     *
     * public static final String IR_NODE = [PREFIX|COMPOSITE_PREFIX] + "IR_NODE" + POSTFIX;
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

    public static final String ABS_V = PREFIX + "ABS_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(ABS_V, "AbsV(B|S|I|L|F|D)");
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

    public static final String ADD_V = PREFIX + "ADD_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_V, "AddV(B|S|I|L|F|D)");
    }

    public static final String ADD_VD = PREFIX + "ADD_VD" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_VD, "AddVD");
    }

    public static final String ADD_VI = PREFIX + "ADD_VI" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_VI, "AddVI");
    }

    public static final String ADD_VF = PREFIX + "ADD_VF" + POSTFIX;
    static {
        beforeMatchingNameRegex(ADD_VF, "AddVF");
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

    public static final String ALLOC = PREFIX + "ALLOC" + POSTFIX;
    static {
        String optoRegex = "(.*precise .*\\R((.*(?i:mov|xorl|nop|spill).*|\\s*)\\R)*.*(?i:call,static).*wrapper for: _new_instance_Java" + END;
        allocNodes(ALLOC, "Allocate", optoRegex);
    }

    public static final String ALLOC_OF = COMPOSITE_PREFIX + "ALLOC_OF" + POSTFIX;
    static {
        String regex = "(.*precise .*" + IS_REPLACED + ":.*\\R((.*(?i:mov|xorl|nop|spill).*|\\s*)\\R)*.*(?i:call,static).*wrapper for: _new_instance_Java" + END;
        optoOnly(ALLOC_OF, regex);
    }

    public static final String ALLOC_ARRAY = PREFIX + "ALLOC_ARRAY" + POSTFIX;
    static {
        String optoRegex = "(.*precise \\[.*\\R((.*(?i:mov|xor|nop|spill).*|\\s*|.*(LGHI|LI).*)\\R)*.*(?i:call,static).*wrapper for: _new_array_Java" + END;
        allocNodes(ALLOC_ARRAY, "AllocateArray", optoRegex);
    }

    public static final String ALLOC_ARRAY_OF = COMPOSITE_PREFIX + "ALLOC_ARRAY_OF" + POSTFIX;
    static {
        String regex = "(.*precise \\[.*" + IS_REPLACED + ":.*\\R((.*(?i:mov|xorl|nop|spill).*|\\s*|.*(LGHI|LI).*)\\R)*.*(?i:call,static).*wrapper for: _new_array_Java" + END;
        optoOnly(ALLOC_ARRAY_OF, regex);
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

    public static final String AND_V = PREFIX + "AND_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(AND_V, "AndV");
    }

    public static final String AND_V_MASK = PREFIX + "AND_V_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(AND_V_MASK, "AndVMask");
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
        String regex = "(((?i:cmp|CLFI|CLR).*precise \\[.*:|.*(?i:mov|or).*precise \\[.*:.*\\R.*(cmp|CMP|CLR))" + END;
        optoOnly(CHECKCAST_ARRAY, regex);
    }

    public static final String CHECKCAST_ARRAY_OF = COMPOSITE_PREFIX + "CHECKCAST_ARRAY_OF" + POSTFIX;
    static {
        String regex = "(((?i:cmp|CLFI|CLR).*precise \\[.*" + IS_REPLACED + ":|.*(?i:mov|or).*precise \\[.*" + IS_REPLACED + ":.*\\R.*(cmp|CMP|CLR))" + END;
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

    public static final String CMOVE_VD = PREFIX + "CMOVE_VD" + POSTFIX;
    static {
        superWordNodes(CMOVE_VD, "CMoveVD");
    }

    public static final String CMOVE_VF = PREFIX + "CMOVE_VF" + POSTFIX;
    static {
        superWordNodes(CMOVE_VF, "CMoveVF");
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

    public static final String COMPRESS_BITS = PREFIX + "COMPRESS_BITS" + POSTFIX;
    static {
        beforeMatchingNameRegex(COMPRESS_BITS, "CompressBits");
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

    public static final String DIV_V = PREFIX + "DIV_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(DIV_V, "DivV(F|D)");
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

    public static final String FMA_V = PREFIX + "FMA_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(FMA_V, "FmaV(F|D)");
    }

    public static final String IF = PREFIX + "IF" + POSTFIX;
    static {
        beforeMatchingNameRegex(IF, "If\\b");
    }

    // Does not work for VM builds without JVMCI like x86_32 (a rule containing this regex will be skipped without having JVMCI built).
    public static final String INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP = PREFIX + "INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP" + POSTFIX;
    static {
        trapNodes(INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP,"intrinsic_or_type_checked_inlining");
    }

    public static final String INTRINSIC_TRAP = PREFIX + "INTRINSIC_TRAP" + POSTFIX;
    static {
        trapNodes(INTRINSIC_TRAP,"intrinsic");
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

    public static final String LOAD_VECTOR = PREFIX + "LOAD_VECTOR" + POSTFIX;
    static {
        beforeMatchingNameRegex(LOAD_VECTOR, "LoadVector");
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

    public static final String LSHIFT_V = PREFIX + "LSHIFT_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(LSHIFT_V, "LShiftV(B|S|I|L)");
    }

    public static final String MACRO_LOGIC_V = PREFIX + "MACRO_LOGIC_V" + POSTFIX;
    static {
        afterBarrierExpansionToBeforeMatching(MACRO_LOGIC_V, "MacroLogicV");
    }

    public static final String MAX = PREFIX + "MAX" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX, "Max(I|L)");
    }

    public static final String MAX_I = PREFIX + "MAX_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX_I, "MaxI");
    }

    public static final String MAX_V = PREFIX + "MAX_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(MAX_V, "MaxV");
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

    public static final String MIN_I = PREFIX + "MIN_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(MIN_I, "MinI");
    }

    public static final String MIN_V = PREFIX + "MIN_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(MIN_V, "MinV");
    }

    public static final String MUL = PREFIX + "MUL" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL, "Mul(I|L|F|D)");
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

    public static final String MUL_V = PREFIX + "MUL_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL_V, "MulV(B|S|I|L|F|D)");
    }

    public static final String MUL_VL = PREFIX + "MUL_VL" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL_VL, "MulVL");
    }

    public static final String MUL_VI = PREFIX + "MUL_VI" + POSTFIX;
    static {
        beforeMatchingNameRegex(MUL_VI, "MulVI");
    }

    public static final String MUL_REDUCTION_VD = PREFIX + "MUL_REDUCTION_VD" + POSTFIX;
    static {
        superWordNodes(MUL_REDUCTION_VD, "MulReductionVD");
    }

    public static final String MUL_REDUCTION_VF = PREFIX + "MUL_REDUCTION_VF" + POSTFIX;
    static {
        superWordNodes(MUL_REDUCTION_VF, "MulReductionVF");
    }

    public static final String NEG_V = PREFIX + "NEG_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(NEG_V, "NegV(F|D)");
    }

    public static final String NULL_ASSERT_TRAP = PREFIX + "NULL_ASSERT_TRAP" + POSTFIX;
    static {
        trapNodes(NULL_ASSERT_TRAP,"null_assert");
    }

    public static final String NULL_CHECK_TRAP = PREFIX + "NULL_CHECK_TRAP" + POSTFIX;
    static {
        trapNodes(NULL_CHECK_TRAP,"null_check");
    }

    public static final String OR_V = PREFIX + "OR_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(OR_V, "OrV");
    }

    public static final String OR_V_MASK = PREFIX + "OR_V_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(OR_V_MASK, "OrVMask");
    }

    public static final String OUTER_STRIP_MINED_LOOP = PREFIX + "OUTER_STRIP_MINED_LOOP" + POSTFIX;
    static {
        String regex = START + "OuterStripMinedLoop\\b" + MID + END;
        fromAfterCountedLoops(OUTER_STRIP_MINED_LOOP, regex);
    }

    public static final String PHI = PREFIX + "PHI" + POSTFIX;
    static {
        beforeMatchingNameRegex(PHI, "Phi");
    }

    public static final String POPCOUNT_L = PREFIX + "POPCOUNT_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(POPCOUNT_L, "PopCountL");
    }

    public static final String POPCOUNT_VI = PREFIX + "POPCOUNT_VI" + POSTFIX;
    static {
        superWordNodes(POPCOUNT_VI, "PopCountVI");
    }

    public static final String POPCOUNT_VL = PREFIX + "POPCOUNT_VL" + POSTFIX;
    static {
        superWordNodes(POPCOUNT_VL, "PopCountVL");
    }

    public static final String COUNTTRAILINGZEROS_VL = PREFIX + "COUNTTRAILINGZEROS_VL" + POSTFIX;
    static {
        superWordNodes(COUNTTRAILINGZEROS_VL, "CountTrailingZerosV");
    }

    public static final String COUNTLEADINGZEROS_VL = PREFIX + "COUNTLEADINGZEROS_VL" + POSTFIX;
    static {
        superWordNodes(COUNTLEADINGZEROS_VL, "CountLeadingZerosV");
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
        trapNodes(PREDICATE_TRAP,"predicate");
    }

    public static final String RANGE_CHECK_TRAP = PREFIX + "RANGE_CHECK_TRAP" + POSTFIX;
    static {
        trapNodes(RANGE_CHECK_TRAP,"range_check");
    }

    public static final String REPLICATE_B = PREFIX + "REPLICATE_B" + POSTFIX;
    static {
        String regex = START + "ReplicateB" + MID + END;
        IR_NODE_MAPPINGS.put(REPLICATE_B, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                    CompilePhase.AFTER_CLOOPS,
                                                                    CompilePhase.BEFORE_MATCHING));
    }

    public static final String REPLICATE_S = PREFIX + "REPLICATE_S" + POSTFIX;
    static {
        String regex = START + "ReplicateS" + MID + END;
        IR_NODE_MAPPINGS.put(REPLICATE_S, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                    CompilePhase.AFTER_CLOOPS,
                                                                    CompilePhase.BEFORE_MATCHING));
    }

    public static final String REPLICATE_I = PREFIX + "REPLICATE_I" + POSTFIX;
    static {
        String regex = START + "ReplicateI" + MID + END;
        IR_NODE_MAPPINGS.put(REPLICATE_I, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                    CompilePhase.AFTER_CLOOPS,
                                                                    CompilePhase.BEFORE_MATCHING));
    }

    public static final String REPLICATE_L = PREFIX + "REPLICATE_L" + POSTFIX;
    static {
        String regex = START + "ReplicateL" + MID + END;
        IR_NODE_MAPPINGS.put(REPLICATE_L, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                    CompilePhase.AFTER_CLOOPS,
                                                                    CompilePhase.BEFORE_MATCHING));
    }

    public static final String REPLICATE_F = PREFIX + "REPLICATE_F" + POSTFIX;
    static {
        String regex = START + "ReplicateF" + MID + END;
        IR_NODE_MAPPINGS.put(REPLICATE_F, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                    CompilePhase.AFTER_CLOOPS,
                                                                    CompilePhase.BEFORE_MATCHING));
    }

    public static final String REPLICATE_D = PREFIX + "REPLICATE_D" + POSTFIX;
    static {
        String regex = START + "ReplicateD" + MID + END;
        IR_NODE_MAPPINGS.put(REPLICATE_D, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                                    CompilePhase.AFTER_CLOOPS,
                                                                    CompilePhase.BEFORE_MATCHING));
    }

    public static final String REVERSE_BYTES_V = PREFIX + "REVERSE_BYTES_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_BYTES_V, "ReverseBytesV");
    }

    public static final String REVERSE_I = PREFIX + "REVERSE_I" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_I, "ReverseI");
    }

    public static final String REVERSE_L = PREFIX + "REVERSE_L" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_L, "ReverseL");
    }

    public static final String REVERSE_V = PREFIX + "REVERSE_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(REVERSE_V, "ReverseV");
    }

    public static final String ROUND_VD = PREFIX + "ROUND_VD" + POSTFIX;
    static {
        beforeMatchingNameRegex(ROUND_VD, "RoundVD");
    }

    public static final String ROUND_VF = PREFIX + "ROUND_VF" + POSTFIX;
    static {
        beforeMatchingNameRegex(ROUND_VF, "RoundVF");
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

    public static final String ROUND_DOUBLE_MODE_V = PREFIX + "ROUND_DOUBLE_MODE_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(ROUND_DOUBLE_MODE_V, "RoundDoubleModeV");
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

    public static final String RSHIFT_VB = PREFIX + "RSHIFT_VB" + POSTFIX;
    static {
        beforeMatchingNameRegex(RSHIFT_VB, "RShiftVB");
    }

    public static final String RSHIFT_VS = PREFIX + "RSHIFT_VS" + POSTFIX;
    static {
        beforeMatchingNameRegex(RSHIFT_VS, "RShiftVS");
    }

    public static final String RSHIFT_V = PREFIX + "RSHIFT_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(RSHIFT_V, "RShiftV(B|S|I|L)");
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

    public static final String SIGNUM_VD = PREFIX + "SIGNUM_VD" + POSTFIX;
    static {
        beforeMatchingNameRegex(SIGNUM_VD, "SignumVD");
    }

    public static final String SIGNUM_VF = PREFIX + "SIGNUM_VF" + POSTFIX;
    static {
        beforeMatchingNameRegex(SIGNUM_VF, "SignumVF");
    }

    public static final String SQRT_V = PREFIX + "SQRT_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(SQRT_V, "SqrtV(F|D)");
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

    public static final String SUB_V = PREFIX + "SUB_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(SUB_V, "SubV(B|S|I|L|F|D)");
    }

    public static final String TRAP = PREFIX + "TRAP" + POSTFIX;
    static {
        trapNodes(TRAP,"reason");
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
        trapNodes(UNHANDLED_TRAP,"unhandled");
    }

    public static final String UNSTABLE_IF_TRAP = PREFIX + "UNSTABLE_IF_TRAP" + POSTFIX;
    static {
        trapNodes(UNSTABLE_IF_TRAP,"unstable_if");
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

    public static final String URSHIFT_V = PREFIX + "URSHIFT_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(URSHIFT_V, "URShiftV(B|S|I|L)");
    }

    public static final String VAND_NOT_I = PREFIX + "VAND_NOT_I" + POSTFIX;
    static {
        machOnlyNameRegex(VAND_NOT_I, "vand_notI");
    }

    public static final String VAND_NOT_L = PREFIX + "VAND_NOT_L" + POSTFIX;
    static {
        machOnlyNameRegex(VAND_NOT_L, "vand_notL");
    }

    public static final String VECTOR_BLEND = PREFIX + "VECTOR_BLEND" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_BLEND, "VectorBlend");
    }

    public static final String VECTOR_CAST_B2X = PREFIX + "VECTOR_CAST_B2X" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_CAST_B2X, "VectorCastB2X");
    }

    public static final String VECTOR_CAST_D2X = PREFIX + "VECTOR_CAST_D2X" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_CAST_D2X, "VectorCastD2X");
    }

    public static final String VECTOR_CAST_F2X = PREFIX + "VECTOR_CAST_F2X" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_CAST_F2X, "VectorCastF2X");
    }

    public static final String VECTOR_CAST_I2X = PREFIX + "VECTOR_CAST_I2X" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_CAST_I2X, "VectorCastI2X");
    }

    public static final String VECTOR_CAST_L2X = PREFIX + "VECTOR_CAST_L2X" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_CAST_L2X, "VectorCastL2X");
    }

    public static final String VECTOR_CAST_S2X = PREFIX + "VECTOR_CAST_S2X" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_CAST_S2X, "VectorCastS2X");
    }

    public static final String VECTOR_CAST_F2HF = PREFIX + "VECTOR_CAST_F2HF" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_CAST_F2HF, "VectorCastF2HF");
    }

    public static final String VECTOR_CAST_HF2F = PREFIX + "VECTOR_CAST_HF2F" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_CAST_HF2F, "VectorCastHF2F");
    }

    public static final String VECTOR_MASK_CAST = PREFIX + "VECTOR_MASK_CAST" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_MASK_CAST, "VectorMaskCast");
    }

    public static final String VECTOR_REINTERPRET = PREFIX + "VECTOR_REINTERPRET" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_REINTERPRET, "VectorReinterpret");
    }

    public static final String VECTOR_UCAST_B2X = PREFIX + "VECTOR_UCAST_B2X" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_UCAST_B2X, "VectorUCastB2X");
    }

    public static final String VECTOR_UCAST_I2X = PREFIX + "VECTOR_UCAST_I2X" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_UCAST_I2X, "VectorUCastI2X");
    }

    public static final String VECTOR_UCAST_S2X = PREFIX + "VECTOR_UCAST_S2X" + POSTFIX;
    static {
        beforeMatchingNameRegex(VECTOR_UCAST_S2X, "VectorUCastS2X");
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

    public static final String VNOT_I_MASKED = PREFIX + "VNOT_I_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VNOT_I_MASKED, "vnotI_masked");
    }

    public static final String VNOT_L_MASKED = PREFIX + "VNOT_L_MASKED" + POSTFIX;
    static {
        machOnlyNameRegex(VNOT_L_MASKED, "vnotL_masked");
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

    public static final String XOR_V = PREFIX + "XOR_V" + POSTFIX;
    static {
        beforeMatchingNameRegex(XOR_V, "XorV");
    }

    public static final String XOR_V_MASK = PREFIX + "XOR_V_MASK" + POSTFIX;
    static {
        beforeMatchingNameRegex(XOR_V_MASK, "XorVMask");
    }

    public static final String XOR3_NEON = PREFIX + "XOR3_NEON" + POSTFIX;
    static {
        machOnlyNameRegex(XOR3_NEON, "veor3_neon");
    }

    public static final String XOR3_SVE = PREFIX + "XOR3_SVE" + POSTFIX;
    static {
        machOnlyNameRegex(XOR3_SVE, "veor3_sve");
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
                                                                          CompilePhase.MACRO_EXPANSION,
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
     * Returns "IRNode.XYZ", where XYZ is one of the IR node placeholder variable names defined above.
     */
    public static String getIRNodeAccessString(String irNodeString) {
        int prefixLength;
        if (isCompositeIRNode(irNodeString)) {
            TestFramework.check(irNodeString.length() > COMPOSITE_PREFIX.length() + POSTFIX.length(),
                                "Invalid composite node placeholder: " + irNodeString);
            prefixLength = COMPOSITE_PREFIX.length();
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
}
