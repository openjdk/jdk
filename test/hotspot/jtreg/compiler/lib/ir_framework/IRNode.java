/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.driver.irmatching.mapping.IRNodeMappings;
import compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexConstants;
import compiler.lib.ir_framework.driver.irmatching.regexes.IdealIndependentDefaultRegexes;
import compiler.lib.ir_framework.driver.irmatching.regexes.MachDefaultRegexes;
import compiler.lib.ir_framework.driver.irmatching.regexes.OptoAssemblyDefaultRegexes;
import compiler.lib.ir_framework.shared.CheckedTestFrameworkException;
import compiler.lib.ir_framework.shared.TestFormatException;
import jdk.test.lib.Platform;
import jdk.test.whitebox.WhiteBox;

/**
 * This class specifies IR node placeholder strings that can be used in {@link IR#failOn()} and/or {@link IR#counts()}
 * attributes to define IR constraints. These placeholder strings are replaced with default regexes (defined in package
 * {@link compiler.lib.ir_framework.driver.irmatching.regexes}) by the IR framework depending on the specified
 * compile phases in {@link IR#phase()} and the provided mapping in
 * {@link compiler.lib.ir_framework.driver.irmatching.mapping.IRNodeMappings}.
 *
 * <p>
 * If an IR node is either missing a mapping in {@link IRNodeMappings} or does not provide a default regex for a
 * specified compile phase in {@link IR#phase}, a {@link TestFormatException} is reported.
 *
 * <p>
 * There are two types of IR nodes:
 * <ul>
 *     <li><p>Standalone IR nodes: The IR node placeholder string is directly replaced by a default regex.</li>
 *     <li><p>Composite IR nodes:  The IR node placeholder string contains an additional {@link #COMPOSITE_PREFIX}.
 *                                 Using this IR node expects another user provided string in the constraint list of
 *                                 {@link IR#failOn()} and {@link IR#counts()}. They cannot be use as standalone IR nodes.
 *                                 Trying to do so will result in a format violation error.</li>
 * </ul>
 *
 * @see IRNodeMappings
 * @see DefaultRegexConstants
 * @see IdealIndependentDefaultRegexes
 * @see MachDefaultRegexes
 * @see OptoAssemblyDefaultRegexes
 */
public class IRNode {
    private static final String PREFIX = "_#";
    private static final String POSTFIX = "#_";
    private static final String COMPOSITE_PREFIX = PREFIX + "C#";

    /*
     * List of IR node placeholder strings for which at least one default regex exists. Such an IR node must start with
     * PREFIX (normal IR nodes) or COMPOSITE_PREFIX (for composite IR nodes) and end with POSTFIX.
     * The mappings from these placeholder strings to regexes are defined in class
     * compiler.lib.ir_framework.driver.irmatching.mapping.IRNodeMappings.
     */
    public static final String ABS_D = PREFIX + "ABS_D" + POSTFIX;
    public static final String ABS_F = PREFIX + "ABS_F" + POSTFIX;
    public static final String ABS_I = PREFIX + "ABS_I" + POSTFIX;
    public static final String ABS_L = PREFIX + "ABS_L" + POSTFIX;
    public static final String ADD = PREFIX + "ADD" + POSTFIX;
    public static final String ADD_I = PREFIX + "ADD_I" + POSTFIX;
    public static final String ADD_L = PREFIX + "ADD_L" + POSTFIX;
    public static final String ADD_VD = PREFIX + "ADD_VD" + POSTFIX;
    public static final String ADD_VI = PREFIX + "ADD_VI" + POSTFIX;
    public static final String ALLOC = PREFIX + "ALLOC" + POSTFIX;
    public static final String ALLOC_ARRAY = PREFIX + "ALLOC_ARRAY" + POSTFIX;
    public static final String ALLOC_ARRAY_OF = COMPOSITE_PREFIX + "ALLOC_ARRAY_OF" + POSTFIX;
    public static final String ALLOC_OF = COMPOSITE_PREFIX + "ALLOC_OF" + POSTFIX;
    public static final String AND = PREFIX + "AND" + POSTFIX;
    public static final String AND_I = PREFIX + "AND_I" + POSTFIX;
    public static final String AND_L = PREFIX + "AND_L" + POSTFIX;
    public static final String AND_V = PREFIX + "AND_V" + POSTFIX;
    public static final String AND_V_MASK = PREFIX + "AND_V_MASK" + POSTFIX;
    public static final String CALL = PREFIX + "CALL" + POSTFIX;
    public static final String CALL_OF_METHOD = COMPOSITE_PREFIX + "CALL_OF_METHOD" + POSTFIX;
    public static final String CAST_II = PREFIX + "CAST_II" + POSTFIX;
    public static final String CAST_LL = PREFIX + "CAST_LL" + POSTFIX;
    public static final String CHECKCAST_ARRAY = PREFIX + "CHECKCAST_ARRAY" + POSTFIX;
    // Does not work on s390 (a rule containing this regex will be skipped on s390).
    public static final String CHECKCAST_ARRAYCOPY = PREFIX + "CHECKCAST_ARRAYCOPY" + POSTFIX;
    public static final String CHECKCAST_ARRAY_OF = COMPOSITE_PREFIX + "CHECKCAST_ARRAY_OF" + POSTFIX;
    public static final String CLASS_CHECK_TRAP = PREFIX + "CLASS_CHECK_TRAP" + POSTFIX;
    public static final String CMOVE_I = PREFIX + "CMOVE_I" + POSTFIX;
    public static final String CMP_I = PREFIX + "CMP_I" + POSTFIX;
    public static final String CMP_L = PREFIX + "CMP_L" + POSTFIX;
    public static final String CMP_U = PREFIX + "CMP_U" + POSTFIX;
    public static final String CMP_U3 = PREFIX + "CMP_U3" + POSTFIX;
    public static final String CMP_UL = PREFIX + "CMP_UL" + POSTFIX;
    public static final String CMP_UL3 = PREFIX + "CMP_UL3" + POSTFIX;
    public static final String COMPRESS_BITS = PREFIX + "COMPRESS_BITS" + POSTFIX;
    public static final String CONV_I2L = PREFIX + "CONV_I2L" + POSTFIX;
    public static final String CONV_L2I = PREFIX + "CONV_L2I" + POSTFIX;
    public static final String CON_I = PREFIX + "CON_I" + POSTFIX;
    public static final String CON_L = PREFIX + "CON_L" + POSTFIX;
    public static final String COUNTED_LOOP = PREFIX + "COUNTED_LOOP" + POSTFIX;
    public static final String COUNTED_LOOP_MAIN = PREFIX + "COUNTED_LOOP_MAIN" + POSTFIX;
    public static final String DIV = PREFIX + "DIV" + POSTFIX;
    public static final String DIV_BY_ZERO_TRAP = PREFIX + "DIV_BY_ZERO_TRAP" + POSTFIX;
    public static final String DIV_L = PREFIX + "DIV_L" + POSTFIX;
    public static final String DYNAMIC_CALL_OF_METHOD = COMPOSITE_PREFIX + "DYNAMIC_CALL_OF_METHOD" + POSTFIX;
    public static final String EXPAND_BITS = PREFIX + "EXPAND_BITS" + POSTFIX;
    public static final String FAST_LOCK = PREFIX + "FAST_LOCK" + POSTFIX;
    public static final String FAST_UNLOCK = PREFIX + "FAST_UNLOCK" + POSTFIX;
    public static final String FIELD_ACCESS = PREFIX + "FIELD_ACCESS" + POSTFIX;
    public static final String IF = PREFIX + "IF" + POSTFIX;
    // Does not work for VM builds without JVMCI like x86_32 (a rule containing this regex will be skipped without having JVMCI built).
    public static final String INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP = PREFIX + "INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP" + POSTFIX;
    public static final String INTRINSIC_TRAP = PREFIX + "INTRINSIC_TRAP" + POSTFIX;
    public static final String LOAD = PREFIX + "LOAD" + POSTFIX;
    public static final String LOAD_B = PREFIX + "LOAD_B" + POSTFIX;
    public static final String LOAD_B_OF_CLASS = COMPOSITE_PREFIX + "LOAD_B_OF_CLASS" + POSTFIX;
    public static final String LOAD_D = PREFIX + "LOAD_D" + POSTFIX;
    public static final String LOAD_D_OF_CLASS = COMPOSITE_PREFIX + "LOAD_D_OF_CLASS" + POSTFIX;
    public static final String LOAD_F = PREFIX + "LOAD_F" + POSTFIX;
    public static final String LOAD_F_OF_CLASS = COMPOSITE_PREFIX + "LOAD_F_OF_CLASS" + POSTFIX;
    public static final String LOAD_I = PREFIX + "LOAD_I" + POSTFIX;
    public static final String LOAD_I_OF_CLASS = COMPOSITE_PREFIX + "LOAD_I_OF_CLASS" + POSTFIX;
    public static final String LOAD_KLASS = PREFIX + "LOAD_KLASS" + POSTFIX;
    public static final String LOAD_L = PREFIX + "LOAD_L" + POSTFIX;
    public static final String LOAD_L_OF_CLASS = COMPOSITE_PREFIX + "LOAD_L_OF_CLASS" + POSTFIX;
    public static final String LOAD_N = PREFIX + "LOAD_N" + POSTFIX;
    public static final String LOAD_N_OF_CLASS = COMPOSITE_PREFIX + "LOAD_N_OF_CLASS" + POSTFIX;
    public static final String LOAD_OF_CLASS = COMPOSITE_PREFIX + "LOAD_OF_CLASS" + POSTFIX;
    public static final String LOAD_OF_FIELD = COMPOSITE_PREFIX + "LOAD_OF_FIELD" + POSTFIX;
    public static final String LOAD_P = PREFIX + "LOAD_P" + POSTFIX;
    public static final String LOAD_P_OF_CLASS = COMPOSITE_PREFIX + "LOAD_P_OF_CLASS" + POSTFIX;
    public static final String LOAD_S = PREFIX + "LOAD_S" + POSTFIX;
    public static final String LOAD_S_OF_CLASS = COMPOSITE_PREFIX + "LOAD_S_OF_CLASS" + POSTFIX;
    public static final String LOAD_UB = PREFIX + "LOAD_UB" + POSTFIX;
    public static final String LOAD_UB_OF_CLASS = COMPOSITE_PREFIX + "LOAD_UB_OF_CLASS" + POSTFIX;
    public static final String LOAD_US = PREFIX + "LOAD_US" + POSTFIX;
    public static final String LOAD_US_OF_CLASS = COMPOSITE_PREFIX + "LOAD_US_OF_CLASS" + POSTFIX;
    public static final String LOAD_VECTOR = PREFIX + "LOAD_VECTOR" + POSTFIX;
    public static final String LONG_COUNTED_LOOP = PREFIX + "LONG_COUNTED_LOOP" + POSTFIX;
    public static final String LOOP = PREFIX + "LOOP" + POSTFIX;
    public static final String LSHIFT = PREFIX + "LSHIFT" + POSTFIX;
    public static final String LSHIFT_I = PREFIX + "LSHIFT_I" + POSTFIX;
    public static final String LSHIFT_L = PREFIX + "LSHIFT_L" + POSTFIX;
    public static final String MAX_I = PREFIX + "MAX_I" + POSTFIX;
    public static final String MAX_V = PREFIX + "MAX_V" + POSTFIX;
    public static final String MEMBAR = PREFIX + "MEMBAR" + POSTFIX;
    public static final String MEMBAR_STORESTORE = PREFIX + "MEMBAR_STORESTORE" + POSTFIX;
    public static final String MIN_I = PREFIX + "MIN_I" + POSTFIX;
    public static final String MIN_V = PREFIX + "MIN_V" + POSTFIX;
    public static final String MUL = PREFIX + "MUL" + POSTFIX;
    public static final String MUL_F = PREFIX + "MUL_F" + POSTFIX;
    public static final String MUL_I = PREFIX + "MUL_I" + POSTFIX;
    public static final String MUL_L = PREFIX + "MUL_L" + POSTFIX;
    public static final String NULL_ASSERT_TRAP = PREFIX + "NULL_ASSERT_TRAP" + POSTFIX;
    public static final String NULL_CHECK_TRAP = PREFIX + "NULL_CHECK_TRAP" + POSTFIX;
    public static final String OR_V = PREFIX + "OR_V" + POSTFIX;
    public static final String OR_V_MASK = PREFIX + "OR_V_MASK" + POSTFIX;
    public static final String OUTER_STRIP_MINED_LOOP = PREFIX + "OUTER_STRIP_MINED_LOOP" + POSTFIX;
    public static final String PHI = PREFIX + "PHI" + POSTFIX;
    public static final String POPCOUNT_L = PREFIX + "POPCOUNT_L" + POSTFIX;
    public static final String POPULATE_INDEX = PREFIX + "POPULATE_INDEX" + POSTFIX;
    public static final String PREDICATE_TRAP = PREFIX + "PREDICATE_TRAP" + POSTFIX;
    public static final String RANGE_CHECK_TRAP = PREFIX + "RANGE_CHECK_TRAP" + POSTFIX;
    public static final String REVERSE_BYTES_V = PREFIX + "REVERSE_BYTES_V" + POSTFIX;
    public static final String REVERSE_I = PREFIX + "REVERSE_I" + POSTFIX;
    public static final String REVERSE_L = PREFIX + "REVERSE_L" + POSTFIX;
    public static final String REVERSE_V = PREFIX + "REVERSE_V" + POSTFIX;
    public static final String ROUND_VD = PREFIX + "ROUND_VD" + POSTFIX;
    public static final String ROUND_VF = PREFIX + "ROUND_VF" + POSTFIX;
    public static final String RSHIFT = PREFIX + "RSHIFT" + POSTFIX;
    public static final String RSHIFT_I = PREFIX + "RSHIFT_I" + POSTFIX;
    public static final String RSHIFT_L = PREFIX + "RSHIFT_L" + POSTFIX;
    public static final String RSHIFT_VB = PREFIX + "RSHIFT_VB" + POSTFIX;
    public static final String RSHIFT_VS = PREFIX + "RSHIFT_VS" + POSTFIX;
    public static final String SAFEPOINT = PREFIX + "SAFEPOINT" + POSTFIX;
    public static final String SCOPE_OBJECT = PREFIX + "SCOPE_OBJECT" + POSTFIX;
    public static final String SIGNUM_VD = PREFIX + "SIGNUM_VD" + POSTFIX;
    public static final String SIGNUM_VF = PREFIX + "SIGNUM_VF" + POSTFIX;
    public static final String STATIC_CALL_OF_METHOD = COMPOSITE_PREFIX + "STATIC_CALL_OF_METHOD" + POSTFIX;
    public static final String STORE = PREFIX + "STORE" + POSTFIX;
    public static final String STORE_B = PREFIX + "STORE_B" + POSTFIX;
    public static final String STORE_B_OF_CLASS = COMPOSITE_PREFIX + "STORE_B_OF_CLASS" + POSTFIX;
    public static final String STORE_C = PREFIX + "STORE_C" + POSTFIX;
    public static final String STORE_C_OF_CLASS = COMPOSITE_PREFIX + "STORE_C_OF_CLASS" + POSTFIX;
    public static final String STORE_D = PREFIX + "STORE_D" + POSTFIX;
    public static final String STORE_D_OF_CLASS = COMPOSITE_PREFIX + "STORE_D_OF_CLASS" + POSTFIX;
    public static final String STORE_F = PREFIX + "STORE_F" + POSTFIX;
    public static final String STORE_F_OF_CLASS = COMPOSITE_PREFIX + "STORE_F_OF_CLASS" + POSTFIX;
    public static final String STORE_I = PREFIX + "STORE_I" + POSTFIX;
    public static final String STORE_I_OF_CLASS = COMPOSITE_PREFIX + "STORE_I_OF_CLASS" + POSTFIX;
    public static final String STORE_L = PREFIX + "STORE_L" + POSTFIX;
    public static final String STORE_L_OF_CLASS = COMPOSITE_PREFIX + "STORE_L_OF_CLASS" + POSTFIX;
    public static final String STORE_N = PREFIX + "STORE_N" + POSTFIX;
    public static final String STORE_N_OF_CLASS = COMPOSITE_PREFIX + "STORE_N_OF_CLASS" + POSTFIX;
    public static final String STORE_OF_CLASS = COMPOSITE_PREFIX + "STORE_OF_CLASS" + POSTFIX;
    public static final String STORE_OF_FIELD = COMPOSITE_PREFIX + "STORE_OF_FIELD" + POSTFIX;
    public static final String STORE_P = PREFIX + "STORE_P" + POSTFIX;
    public static final String STORE_P_OF_CLASS = COMPOSITE_PREFIX + "STORE_P_OF_CLASS" + POSTFIX;
    public static final String STORE_VECTOR = PREFIX + "STORE_VECTOR" + POSTFIX;
    public static final String SUB = PREFIX + "SUB" + POSTFIX;
    public static final String SUB_D = PREFIX + "SUB_D" + POSTFIX;
    public static final String SUB_F = PREFIX + "SUB_F" + POSTFIX;
    public static final String SUB_I = PREFIX + "SUB_I" + POSTFIX;
    public static final String SUB_L = PREFIX + "SUB_L" + POSTFIX;
    public static final String TRAP = PREFIX + "TRAP" + POSTFIX;
    public static final String UDIV_I = PREFIX + "UDIV_I" + POSTFIX;
    public static final String UDIV_L = PREFIX + "UDIV_L" + POSTFIX;
    public static final String UDIV_MOD_I = PREFIX + "UDIV_MOD_I" + POSTFIX;
    public static final String UDIV_MOD_L = PREFIX + "UDIV_MOD_L" + POSTFIX;
    public static final String UMOD_I = PREFIX + "UMOD_I" + POSTFIX;
    public static final String UMOD_L = PREFIX + "UMOD_L" + POSTFIX;
    public static final String UNHANDLED_TRAP = PREFIX + "UNHANDLED_TRAP" + POSTFIX;
    public static final String UNSTABLE_IF_TRAP = PREFIX + "UNSTABLE_IF_TRAP" + POSTFIX;
    public static final String URSHIFT = PREFIX + "URSHIFT" + POSTFIX;
    public static final String URSHIFT_I = PREFIX + "URSHIFT_I" + POSTFIX;
    public static final String URSHIFT_L = PREFIX + "URSHIFT_L" + POSTFIX;
    public static final String VECTOR_BLEND = PREFIX + "VECTOR_BLEND" + POSTFIX;
    public static final String VECTOR_CAST_B2X = PREFIX + "VECTOR_CAST_B2X" + POSTFIX;
    public static final String VECTOR_CAST_D2X = PREFIX + "VECTOR_CAST_D2X" + POSTFIX;
    public static final String VECTOR_CAST_F2X = PREFIX + "VECTOR_CAST_F2X" + POSTFIX;
    public static final String VECTOR_CAST_I2X = PREFIX + "VECTOR_CAST_I2X" + POSTFIX;
    public static final String VECTOR_CAST_L2X = PREFIX + "VECTOR_CAST_L2X" + POSTFIX;
    public static final String VECTOR_CAST_S2X = PREFIX + "VECTOR_CAST_S2X" + POSTFIX;
    public static final String VECTOR_REINTERPRET = PREFIX + "VECTOR_REINTERPRET" + POSTFIX;
    public static final String VECTOR_UCAST_B2X = PREFIX + "VECTOR_UCAST_B2X" + POSTFIX;
    public static final String VECTOR_UCAST_I2X = PREFIX + "VECTOR_UCAST_I2X" + POSTFIX;
    public static final String VECTOR_UCAST_S2X = PREFIX + "VECTOR_UCAST_S2X" + POSTFIX;
    public static final String XOR_I = PREFIX + "XOR_I" + POSTFIX;
    public static final String XOR_L = PREFIX + "XOR_L" + POSTFIX;
    public static final String XOR_V = PREFIX + "XOR_V" + POSTFIX;
    public static final String XOR_V_MASK = PREFIX + "XOR_V_MASK" + POSTFIX;


    public static boolean isCompositeIRNode(String node) {
        return node.startsWith(COMPOSITE_PREFIX);
    }

    public static boolean isIRNode(String node) {
        return node.startsWith(PREFIX);
    }

    public static String getCompositeNodeName(String irNodeString) {
        TestFramework.check(irNodeString.length() > COMPOSITE_PREFIX.length() + POSTFIX.length(),
                            "Invalid composite node placeholder: " + irNodeString);
        return irNodeString.substring(COMPOSITE_PREFIX.length(), irNodeString.length() - POSTFIX.length());
    }

    /**
     * Is this IR node supported on current platform, used VM build, etc.?
     * Throws a {@link CheckedTestFrameworkException} if the default regex is unsupported.
     */
    public static void checkDefaultIRNodeSupported(String node) throws CheckedTestFrameworkException {
        switch (node) {
            case INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP -> {
                if (!WhiteBox.getWhiteBox().isJVMCISupportedByGC()) {
                    throw new CheckedTestFrameworkException("INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP is unsupported in builds without JVMCI.");
                }
            }
            case CHECKCAST_ARRAYCOPY -> {
                if (Platform.isS390x()) {
                    throw new CheckedTestFrameworkException("CHECKCAST_ARRAYCOPY is unsupported on s390.");
                }
            }
            // default: do nothing -> IR node is supported and can be used by the user.
        }
    }
}
