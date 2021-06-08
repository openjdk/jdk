/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.driver.IRMatcher;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class provides default regex strings that can be used in {@link IR @IR} annotations to specify IR constraints.
 * <p>
 * There are two types of default regexes:
 * <ul>
 *     <li><p>Standalone regexes: Use them directly.</li>
 *     <li><p>Composite regexes: Their names contain "{@code _OF}" and expect another string in a list in
 *            {@link IR#failOn()} and {@link IR#counts()}. They cannot be use as standalone regex and will result in a
 *            {@link TestFormatException} when doing so.</li>
 * </ul>
 *
 * @see IR
 */
public class IRNode {
    private static final String START = "(\\d+(\\s){2}(";
    private static final String MID = ".*)+(\\s){2}===.*";
    private static final String END = ")";

    public static final String ALLOC = "(.*precise klass .*\\R(.*(movl|xorl|nop|spill).*\\R)*.*call,static  wrapper for: _new_instance_Java" + END;
    public static final String ALLOC_OF = "(.*precise klass .*";
    public static final String ALLOC_ARRAY = "(.*precise klass \\[L.*\\R(.*(movl|xorl|nop|spill).*\\R)*.*call,static  wrapper for: _new_array_Java" + END;
    public static final String ALLOC_ARRAY_OF = "(.*precise klass \\[L.*";

    public static final String CHECKCAST_ARRAY = "(cmp.*precise klass \\[.*;:" + END;
    public static final String CHECKCAST_ARRAY_OF = "(cmp.*precise klass \\[.*";
    public static final String CHECKCAST_ARRAYCOPY = "(.*call_leaf_nofp,runtime  checkcast_arraycopy.*" + END;

    public static final String FIELD_ACCESS = "(.*Field: *" + END;

    public static final String STORE = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + END;
    public static final String STORE_B = START + "StoreB" + MID + END; // Store to boolean is also mapped to byte
    public static final String STORE_C = START + "StoreC" + MID + END;
    public static final String STORE_I = START + "StoreI" + MID + END; // Store to short is also mapped to int
    public static final String STORE_L = START + "StoreL" + MID + END;
    public static final String STORE_F = START + "StoreF" + MID + END;
    public static final String STORE_D = START + "StoreD" + MID + END;
    public static final String STORE_P = START + "StoreP" + MID + END;
    public static final String STORE_N = START + "StoreN" + MID + END;
    public static final String STORE_OF_CLASS = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + "@\\S*";
    public static final String STORE_B_OF_CLASS = START + "StoreB" + MID + "@\\S*";
    public static final String STORE_C_OF_CLASS = START + "StoreC" + MID + "@\\S*";
    public static final String STORE_I_OF_CLASS = START + "StoreI" + MID + "@\\S*";
    public static final String STORE_L_OF_CLASS = START + "StoreL" + MID + "@\\S*";
    public static final String STORE_F_OF_CLASS = START + "StoreF" + MID + "@\\S*";
    public static final String STORE_D_OF_CLASS = START + "StoreD" + MID + "@\\S*";
    public static final String STORE_P_OF_CLASS = START + "StoreP" + MID + "@\\S*";
    public static final String STORE_N_OF_CLASS = START + "StoreN" + MID + "@\\S*";
    public static final String STORE_OF_FIELD = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + "@.*name=";

    public static final String LOAD = START + "Load(B|UB|S|US|I|L|F|D|P|N)" + MID + END;
    public static final String LOAD_B = START + "LoadB" + MID + END;
    public static final String LOAD_UB = START + "LoadUB" + MID + END; // Load from boolean
    public static final String LOAD_S = START + "LoadS" + MID + END;
    public static final String LOAD_US = START + "LoadUS" + MID + END; // Load from char
    public static final String LOAD_I = START + "LoadI" + MID + END;
    public static final String LOAD_L = START + "LoadL" + MID + END;
    public static final String LOAD_F = START + "LoadF" + MID + END;
    public static final String LOAD_D = START + "LoadD" + MID + END;
    public static final String LOAD_P = START + "LoadP" + MID + END;
    public static final String LOAD_N = START + "LoadN" + MID + END;
    public static final String LOAD_OF_CLASS = START + "Load(B|UB|S|US|I|L|F|D|P|N)" + MID + "@\\S*";
    public static final String LOAD_B_OF_CLASS = START + "LoadB" + MID + "@\\S*";
    public static final String LOAD_UB_OF_CLASS = START + "LoadUB" + MID + "@\\S*";
    public static final String LOAD_S_OF_CLASS = START + "LoadS" + MID + "@\\S*";
    public static final String LOAD_US_OF_CLASS = START + "LoadUS" + MID + "@\\S*";
    public static final String LOAD_I_OF_CLASS = START + "LoadI" + MID + "@\\S*";
    public static final String LOAD_L_OF_CLASS = START + "LoadL" + MID + "@\\S*";
    public static final String LOAD_F_OF_CLASS = START + "LoadF" + MID + "@\\S*";
    public static final String LOAD_D_OF_CLASS = START + "LoadD" + MID + "@\\S*";
    public static final String LOAD_P_OF_CLASS = START + "LoadP" + MID + "@\\S*";
    public static final String LOAD_N_OF_CLASS = START + "LoadN" + MID + "@\\S*";
    public static final String LOAD_OF_FIELD = START + "Load(B|C|S|I|L|F|D|P|N)" + MID + "@.*name=";
    public static final String LOAD_KLASS  = START + "LoadK" + MID + END;

    public static final String LOOP   = START + "Loop" + MID + "" + END;
    public static final String COUNTEDLOOP = START + "CountedLoop\\b" + MID + "" + END;
    public static final String COUNTEDLOOP_MAIN = START + "CountedLoop\\b" + MID + "main" + END;

    public static final String CALL = START + "CallStaticJava" + MID + END;
    public static final String TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*reason" + END;
    public static final String PREDICATE_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*predicate" + END;
    public static final String UNSTABLE_IF_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*unstable_if" + END;
    public static final String CLASS_CHECK_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*class_check" + END;
    public static final String NULL_CHECK_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*null_check" + END;
    public static final String NULL_ASSERT_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*null_assert" + END;
    public static final String RANGE_CHECK_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*range_check" + END;
    public static final String UNHANDLED_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*unhandled" + END;
    public static final String INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*intrinsic_or_type_checked_inlining" + END;

    public static final String SCOPE_OBJECT = "(.*# ScObj.*" + END;
    public static final String MEMBAR = START + "MemBar" + MID + END;


    private static final String ALLOC_OF_POSTFIX =  ":.*\\R(.*(movl|xorl|nop|spill).*\\R)*.*call,static  wrapper for: _new_instance_Java" + END;
    private static final String ALLOC_ARRAY_OF_POSTFIX = ";:.*\\R(.*(movl|xorl|nop|spill).*\\R)*.*call,static  wrapper for: _new_array_Java" + END;
    private static final String CHECKCAST_ARRAY_OF_POSTFIX = ";:" + END;
    private static final String STORE_OF_FIELD_POSTFIX = ",.*" + END;
    private static final String STORE_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;
    private static final String LOAD_OF_CLASS_POSTFIX = "(:|\\+)\\S* \\*" + END;
    private static final String LOAD_OF_FIELD_POSTFIX = ",.*" + END;

    /**
     * Called by {@link IRMatcher} to merge special composite nodes together with additional user-defined input.
     */
    public static List<String> mergeNodes(String[] nodes) {
        List<String> mergedNodes = new ArrayList<>();
        for (int i = 0; i < nodes.length; i += 2) {
            String node = nodes[i];
            switch (node) {
                case ALLOC_OF -> mergeCompositeNodes(nodes, mergedNodes, i, node, ALLOC_OF_POSTFIX, "ALLOC_OF");
                case ALLOC_ARRAY_OF -> mergeCompositeNodes(nodes, mergedNodes, i, node, ALLOC_ARRAY_OF_POSTFIX, "ALLOC_ARRAY_OF");
                case CHECKCAST_ARRAY_OF -> mergeCompositeNodes(nodes, mergedNodes, i, node, CHECKCAST_ARRAY_OF_POSTFIX, "CHECKCAST_ARRAY_OF");
                case STORE_OF_CLASS, STORE_B_OF_CLASS, STORE_C_OF_CLASS, STORE_D_OF_CLASS, STORE_F_OF_CLASS, STORE_I_OF_CLASS,
                        STORE_L_OF_CLASS, STORE_N_OF_CLASS, STORE_P_OF_CLASS
                        -> mergeCompositeNodes(nodes, mergedNodes, i, node, STORE_OF_CLASS_POSTFIX, "STORE_OF_CLASS");
                case STORE_OF_FIELD -> mergeCompositeNodes(nodes, mergedNodes, i, node, STORE_OF_FIELD_POSTFIX, "STORE_OF_FIELD");
                case LOAD_OF_CLASS, LOAD_B_OF_CLASS, LOAD_UB_OF_CLASS, LOAD_D_OF_CLASS, LOAD_F_OF_CLASS, LOAD_I_OF_CLASS, LOAD_L_OF_CLASS,
                        LOAD_N_OF_CLASS, LOAD_P_OF_CLASS, LOAD_S_OF_CLASS, LOAD_US_OF_CLASS
                        -> mergeCompositeNodes(nodes, mergedNodes, i, node, LOAD_OF_CLASS_POSTFIX, "LOAD_OF_CLASS");
                case LOAD_OF_FIELD -> mergeCompositeNodes(nodes, mergedNodes, i, node, LOAD_OF_FIELD_POSTFIX, "LOAD_OF_FIELD");
                default -> {
                    i--; // No composite node, do not increment by 2.
                    mergedNodes.add(node);
                }
            }
        }
        return mergedNodes;
    }

    private static void mergeCompositeNodes(String[] nodes, List<String> mergedNodes, int i, String node, String postFix, String varName) {
        TestFormat.check(i + 1 < nodes.length, "Must provide class name at index " + (i + 1) + " right after " + varName);
        mergedNodes.add(node + Pattern.quote(nodes[i + 1]) + postFix);
    }
}
