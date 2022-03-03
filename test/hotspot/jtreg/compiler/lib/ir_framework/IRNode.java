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

import compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexes;
import compiler.lib.ir_framework.driver.irmatching.regexes.IdealDefaultRegexes;
import compiler.lib.ir_framework.driver.irmatching.regexes.OptoAssemblyDefaultRegexes;
import compiler.lib.ir_framework.shared.CheckedTestFrameworkException;
import jdk.test.lib.Platform;
import sun.hotspot.WhiteBox;

/**
 * This class specifies placeholder strings for IR nodes that can be used in {@link IR#failOn()} and {@link IR#counts()}
 * attributes to define IR constraints. These placeholder strings are replaced with default regexes (defined in
 * {@link IdealDefaultRegexes} and {@link OptoAssemblyDefaultRegexes}) by the IR framework depending on the specified
 * compile phases in {@link IR#phase()}. If a compile phase does not provide a default string for placeholder string,
 * a test format violation is reported.
 *
 * @see DefaultRegexes
 * @see IdealDefaultRegexes
 * @see OptoAssemblyDefaultRegexes
 */
public class IRNode {

    private static final String PREFIX = "_#";
    private static final String POSTFIX = "#_";
    private static final String COMPOSITE_PREFIX = "P#";
    private static final String COMPOSITE_PREFIX_NODE = PREFIX + COMPOSITE_PREFIX;

    public static final String RSHIFT_VB = START + "RShiftVB" + MID + END;
    public static final String RSHIFT_VS = START + "RShiftVS" + MID + END;
    public static final String ADD_VI = START + "AddVI" + MID + END;
    public static final String CMP_U = START + "CmpU" + MID + END;
    public static final String CMP_UL = START + "CmpUL" + MID + END;
    public static final String CMP_U3 = START + "CmpU3" + MID + END;
    public static final String CMP_UL3 = START + "CmpUL3" + MID + END;
    public static final String CAST_II = START + "CastII" + MID + END;
    public static final String CAST_LL = START + "CastLL" + MID + END;
    public static final String PHI = START + "Phi" + MID + END;

    /*
     * List of placeholder strings for which at least one default regex exists.
     */
    public static final String ALLOC = PREFIX + "ALLOC" + POSTFIX;
    public static final String ALLOC_OF = PREFIX + COMPOSITE_PREFIX + "ALLOC_OF" + POSTFIX;
    public static final String ALLOC_ARRAY = PREFIX + "ALLOC_ARRAY" + POSTFIX;
    public static final String ALLOC_ARRAY_OF = PREFIX + COMPOSITE_PREFIX + "ALLOC_ARRAY_OF" + POSTFIX;
    public static final String CHECKCAST_ARRAY = PREFIX + "CHECKCAST_ARRAY" + POSTFIX;
    public static final String CHECKCAST_ARRAY_OF = PREFIX + COMPOSITE_PREFIX + "CHECKCAST_ARRAY_OF" + POSTFIX;
    public static final String CHECKCAST_ARRAYCOPY = PREFIX + "CHECKCAST_ARRAYCOPY" + POSTFIX;
    public static final String FIELD_ACCESS = PREFIX + "FIELD_ACCESS" + POSTFIX;

    public static final String STORE = PREFIX + "STORE" + POSTFIX;
    public static final String STORE_B = PREFIX + "STORE_B" + POSTFIX;
    public static final String STORE_C = PREFIX + "STORE_C" + POSTFIX;
    public static final String STORE_I = PREFIX + "STORE_I" + POSTFIX;
    public static final String STORE_L = PREFIX + "STORE_L" + POSTFIX;
    public static final String STORE_F = PREFIX + "STORE_F" + POSTFIX;
    public static final String STORE_D = PREFIX + "STORE_D" + POSTFIX;
    public static final String STORE_P = PREFIX + "STORE_P" + POSTFIX;
    public static final String STORE_N = PREFIX + "STORE_N" + POSTFIX;
    public static final String STORE_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "STORE_OF_CLASS" + POSTFIX;
    public static final String STORE_B_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "STORE_B_OF_CLASS" + POSTFIX;
    public static final String STORE_C_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "STORE_C_OF_CLASS" + POSTFIX;
    public static final String STORE_I_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "STORE_I_OF_CLASS" + POSTFIX;
    public static final String STORE_L_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "STORE_L_OF_CLASS" + POSTFIX;
    public static final String STORE_F_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "STORE_F_OF_CLASS" + POSTFIX;
    public static final String STORE_D_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "STORE_D_OF_CLASS" + POSTFIX;
    public static final String STORE_P_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "STORE_P_OF_CLASS" + POSTFIX;
    public static final String STORE_N_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "STORE_N_OF_CLASS" + POSTFIX;
    public static final String STORE_OF_FIELD = PREFIX + COMPOSITE_PREFIX + "STORE_OF_FIELD" + POSTFIX;

    public static final String LOAD = PREFIX + "LOAD" + POSTFIX;
    public static final String LOAD_B = PREFIX + "LOAD_B" + POSTFIX;
    public static final String LOAD_UB = PREFIX + "LOAD_UB" + POSTFIX;
    public static final String LOAD_S = PREFIX + "LOAD_S" + POSTFIX;
    public static final String LOAD_US = PREFIX + "LOAD_US" + POSTFIX;
    public static final String LOAD_I = PREFIX + "LOAD_I" + POSTFIX;
    public static final String LOAD_L = PREFIX + "LOAD_L" + POSTFIX;
    public static final String LOAD_F = PREFIX + "LOAD_F" + POSTFIX;
    public static final String LOAD_D = PREFIX + "LOAD_D" + POSTFIX;
    public static final String LOAD_P = PREFIX + "LOAD_P" + POSTFIX;
    public static final String LOAD_N = PREFIX + "LOAD_N" + POSTFIX;
    public static final String LOAD_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_OF_CLASS" + POSTFIX;
    public static final String LOAD_B_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_B_OF_CLASS" + POSTFIX;
    public static final String LOAD_UB_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_UB_OF_CLASS" + POSTFIX;
    public static final String LOAD_S_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_S_OF_CLASS" + POSTFIX;
    public static final String LOAD_US_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_US_OF_CLASS" + POSTFIX;
    public static final String LOAD_I_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_I_OF_CLASS" + POSTFIX;
    public static final String LOAD_L_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_L_OF_CLASS" + POSTFIX;
    public static final String LOAD_F_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_F_OF_CLASS" + POSTFIX;
    public static final String LOAD_D_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_D_OF_CLASS" + POSTFIX;
    public static final String LOAD_P_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_P_OF_CLASS" + POSTFIX;
    public static final String LOAD_N_OF_CLASS = PREFIX + COMPOSITE_PREFIX + "LOAD_N_OF_CLASS" + POSTFIX;
    public static final String LOAD_OF_FIELD = PREFIX + COMPOSITE_PREFIX + "LOAD_OF_FIELD" + POSTFIX;
    public static final String LOAD_KLASS = PREFIX + "LOAD_KLASS" + POSTFIX;

    public static final String LOOP = PREFIX + "LOOP" + POSTFIX;
    public static final String COUNTEDLOOP = PREFIX + "COUNTEDLOOP" + POSTFIX;
    public static final String COUNTEDLOOP_MAIN = PREFIX + "COUNTEDLOOP_MAIN" + POSTFIX;
    public static final String IF = PREFIX + "IF" + POSTFIX;

    public static final String CALL = PREFIX + "CALL" + POSTFIX;
    public static final String CALL_OF_METHOD = PREFIX + COMPOSITE_PREFIX + "CALL_OF_METHOD" + POSTFIX;
    public static final String DYNAMIC_CALL_OF_METHOD = PREFIX + COMPOSITE_PREFIX + "DYNAMIC_CALL_OF_METHOD" + POSTFIX;
    public static final String STATIC_CALL_OF_METHOD = PREFIX + COMPOSITE_PREFIX + "STATIC_CALL_OF_METHOD" + POSTFIX;
    public static final String TRAP = PREFIX + "TRAP" + POSTFIX;
    public static final String PREDICATE_TRAP = PREFIX + "PREDICATE_TRAP" + POSTFIX;
    public static final String UNSTABLE_IF_TRAP = PREFIX + "UNSTABLE_IF_TRAP" + POSTFIX;
    public static final String CLASS_CHECK_TRAP = PREFIX + "CLASS_CHECK_TRAP" + POSTFIX;
    public static final String NULL_CHECK_TRAP = PREFIX + "NULL_CHECK_TRAP" + POSTFIX;
    public static final String NULL_ASSERT_TRAP = PREFIX + "NULL_ASSERT_TRAP" + POSTFIX;
    public static final String RANGE_CHECK_TRAP = PREFIX + "RANGE_CHECK_TRAP" + POSTFIX;
    public static final String UNHANDLED_TRAP = PREFIX + "UNHANDLED_TRAP" + POSTFIX;
    public static final String INTRINSIC_TRAP = PREFIX + "INTRINSIC_TRAP" + POSTFIX;
    public static final String INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP = PREFIX + "INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP" + POSTFIX;

    public static final String SCOPE_OBJECT = PREFIX + "SCOPE_OBJECT" + POSTFIX;
    public static final String MEMBAR = PREFIX + "MEMBAR" + POSTFIX;

    public static final String ABS_I = PREFIX + "ABS_I" + POSTFIX;
    public static final String ABS_L = PREFIX + "ABS_L" + POSTFIX;
    public static final String ABS_F = PREFIX + "ABS_F" + POSTFIX;
    public static final String ABS_D = PREFIX + "ABS_D" + POSTFIX;
    public static final String AND_I = PREFIX + "AND_I" + POSTFIX;
    public static final String AND_L = PREFIX + "AND_L" + POSTFIX;
    public static final String XOR_I = PREFIX + "XOR_I" + POSTFIX;
    public static final String XOR_L = PREFIX + "XOR_L" + POSTFIX;
    public static final String LSHIFT_I = PREFIX + "LSHIFT_I" + POSTFIX;
    public static final String LSHIFT_L = PREFIX + "LSHIFT_L" + POSTFIX;
    public static final String ADD_I = PREFIX + "ADD_I" + POSTFIX;
    public static final String ADD_L = PREFIX + "ADD_L" + POSTFIX;
    public static final String ADD_VD = PREFIX + "ADD_VD" + POSTFIX;
    public static final String SUB_I = PREFIX + "SUB_I" + POSTFIX;
    public static final String SUB_L = PREFIX + "SUB_L" + POSTFIX;
    public static final String SUB_F = PREFIX + "SUB_F" + POSTFIX;
    public static final String SUB_D = PREFIX + "SUB_D" + POSTFIX;
    public static final String MUL_I = PREFIX + "MUL_I" + POSTFIX;
    public static final String MUL_L = PREFIX + "MUL_L" + POSTFIX;
    public static final String CONV_I2L = PREFIX + "CONV_I2L" + POSTFIX;

    // Vector Nodes
    public static final String STORE_VECTOR = PREFIX + "STORE_VECTOR" + POSTFIX;
    public static final String LOAD_VECTOR = PREFIX + "LOAD_VECTOR" + POSTFIX;
    public static final String VECTOR_CAST_B2X = PREFIX + "VECTOR_CAST_B2X" + POSTFIX;
    public static final String VECTOR_CAST_S2X = PREFIX + "VECTOR_CAST_S2X" + POSTFIX;
    public static final String VECTOR_CAST_I2X = PREFIX + "VECTOR_CAST_I2X" + POSTFIX;
    public static final String VECTOR_CAST_L2X = PREFIX + "VECTOR_CAST_L2X" + POSTFIX;
    public static final String VECTOR_CAST_F2X = PREFIX + "VECTOR_CAST_F2X" + POSTFIX;
    public static final String VECTOR_CAST_D2X = PREFIX + "VECTOR_CAST_D2X" + POSTFIX;
    public static final String VECTOR_UCAST_B2X = PREFIX + "VECTOR_UCAST_B2X" + POSTFIX;
    public static final String VECTOR_UCAST_S2X = PREFIX + "VECTOR_UCAST_S2X" + POSTFIX;
    public static final String VECTOR_UCAST_I2X = PREFIX + "VECTOR_UCAST_I2X" + POSTFIX;
    public static final String VECTOR_REINTERPRET = PREFIX + "VECTOR_REINTERPRET" + POSTFIX;


    public static boolean isCompositeIRNode(String node) {
        return node.startsWith(COMPOSITE_PREFIX_NODE);
    }

    public static boolean isDefaultIRNode(String node) {
        return node.startsWith(PREFIX);
    }

    public static String getCompositeNodeName(String irNodeString) {

        TestFramework.check(irNodeString.length() > PREFIX.length() + COMPOSITE_PREFIX.length() + POSTFIX.length(),
                            "Invalid composite node placeholder: " + irNodeString);
        return irNodeString.substring(PREFIX.length() + COMPOSITE_PREFIX.length(), irNodeString.length() - POSTFIX.length());
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
