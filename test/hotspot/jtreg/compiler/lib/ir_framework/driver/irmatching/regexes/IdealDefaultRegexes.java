/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.regexes;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;

import java.util.EnumMap;

import static compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexes.*;

/**
 * This class provides default regex strings for matches on PrintIdeal and all compile phases on the ideal graph (i.e.
 * before matching and code generation). These default regexes replace any usages of placeholder strings from {@link IRNode}
 * in check attributes {@link IR#failOn()} and {@link IR#counts()} depending on the specified compile phases in
 * {@link IR#phase()} and if the compile phase is returned in the list {@link CompilePhase#getIdealPhases}.
 * <p>
 *
 * Each new default regex for any node that needs to be matched on the ideal graph should be defined here together with
 * a mapping for which compile phase it can be used (defined with an entry in {@link DefaultRegexes#PLACEHOLDER_TO_REGEX_MAP}).
 * If a default regex can also be matched on the normal PrintIdeal output then a mapping for {@link CompilePhase#PRINT_IDEAL}
 * and {@link CompilePhase#DEFAULT} needs to be added as entry to {@link DefaultRegexes#PLACEHOLDER_TO_REGEX_MAP}.
 * <p>
 *
 * Not all regexes can be applied for all phases. For example, {@link IdealDefaultRegexes#LOOP} is not available in
 * {@link CompilePhase#AFTER_PARSING}. If such an unsupported mapping is used for a compile phase, a format violation is
 * reported.
 * <p>
 *
 * There are two types of default regexes:
 * <ul>
 *     <li><p>Standalone regexes: Replace the placeholder string from {@link IRNode} directly.</li>
 *     <li><p>Composite regexes: The placeholder string from {@link IRNode} contain an additional "{@code P#}" prefix.
 *                               This placeholder strings expect another user provided string in the constraint list of
 *                               {@link IR#failOn()} and {@link IR#counts()}. They cannot be use as standalone regex.
 *                               Trying to do so will result in a format violation error.</li>
 * </ul>
 *
 * @see IR
 * @see IRNode
 * @see CompilePhase
 * @see DefaultRegexes
 */
public class IdealDefaultRegexes {

    public static final String STORE = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + END;
    public static final String STORE_B = START + "StoreB" + MID + END; // Store to boolean is also mapped to byte
    public static final String STORE_C = START + "StoreC" + MID + END;
    public static final String STORE_I = START + "StoreI" + MID + END; // Store to short is also mapped to int
    public static final String STORE_L = START + "StoreL" + MID + END;
    public static final String STORE_F = START + "StoreF" + MID + END;
    public static final String STORE_D = START + "StoreD" + MID + END;
    public static final String STORE_P = START + "StoreP" + MID + END;
    public static final String STORE_N = START + "StoreN" + MID + END;
    public static final String STORE_VECTOR = START + "StoreVector" + MID + END;
    public static final String STORE_OF_CLASS = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_B_OF_CLASS = START + "StoreB" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_C_OF_CLASS = START + "StoreC" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_I_OF_CLASS = START + "StoreI" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_L_OF_CLASS = START + "StoreL" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_F_OF_CLASS = START + "StoreF" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_D_OF_CLASS = START + "StoreD" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_P_OF_CLASS = START + "StoreP" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_N_OF_CLASS = START + "StoreN" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_OF_FIELD = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + "@.*name=" + IS_REPLACED + ",.*" + END;

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
    public static final String LOAD_VECTOR = START + "LoadVector" + MID + END;
    public static final String LOAD_OF_CLASS = START + "Load(B|UB|S|US|I|L|F|D|P|N)" + MID + "@\\S*"+  IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_B_OF_CLASS = START + "LoadB" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_UB_OF_CLASS = START + "LoadUB" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_S_OF_CLASS = START + "LoadS" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_US_OF_CLASS = START + "LoadUS" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_I_OF_CLASS = START + "LoadI" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_L_OF_CLASS = START + "LoadL" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_F_OF_CLASS = START + "LoadF" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_D_OF_CLASS = START + "LoadD" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_P_OF_CLASS = START + "LoadP" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_N_OF_CLASS = START + "LoadN" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_OF_FIELD = START + "Load(B|C|S|I|L|F|D|P|N)" + MID + "@.*name=" + IS_REPLACED + ",.*" + END;
    public static final String LOAD_KLASS  = START + "LoadK" + MID + END;

    public static final String LOOP   = START + "Loop" + MID + END;
    public static final String COUNTEDLOOP = START + "CountedLoop\\b" + MID + END;
    public static final String COUNTEDLOOP_MAIN = START + "CountedLoop\\b" + MID + "main" + END;
    public static final String IF = START + "If\\b" + MID + END;

    public static final String CALL = START + "Call.*Java" + MID + END;
    public static final String CALL_OF_METHOD = START + "Call.*Java" + MID + IS_REPLACED + " " +  END;
    public static final String DYNAMIC_CALL_OF_METHOD = START + "CallDynamicJava" + MID + IS_REPLACED + " " + END;
    public static final String STATIC_CALL_OF_METHOD = START + "CallStaticJava" + MID + IS_REPLACED + " " +  END;
    public static final String TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*reason" + END;
    public static final String PREDICATE_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*predicate" + END;
    public static final String UNSTABLE_IF_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*unstable_if" + END;
    public static final String CLASS_CHECK_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*class_check" + END;
    public static final String NULL_CHECK_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*null_check" + END;
    public static final String NULL_ASSERT_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*null_assert" + END;
    public static final String RANGE_CHECK_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*range_check" + END;
    public static final String UNHANDLED_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*unhandled" + END;
    public static final String INTRINSIC_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*intrinsic" + END;
    // Does not work for VM builds without JVMCI like x86_32 (a rule containing this regex will be skipped without having JVMCI built).
    public static final String INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*intrinsic_or_type_checked_inlining" + END;

    public static final String MEMBAR = START + "MemBar" + MID + END;

    public static final String ABS_I = START + "AbsI" + MID + END;
    public static final String ABS_L = START + "AbsL" + MID + END;
    public static final String ABS_F = START + "AbsF" + MID + END;
    public static final String ABS_D = START + "AbsD" + MID + END;
    public static final String AND_I = START + "AndI" + MID + END;
    public static final String AND_L = START + "AndL" + MID + END;
    public static final String XOR_I = START + "XorI" + MID + END;
    public static final String XOR_L = START + "XorL" + MID + END;
    public static final String LSHIFT_I = START + "LShiftI" + MID + END;
    public static final String LSHIFT_L = START + "LShiftL" + MID + END;
    public static final String ADD_I = START + "AddI" + MID + END;
    public static final String ADD_L = START + "AddL" + MID + END;
    public static final String ADD_VD = START + "AddVD" + MID + END;
    public static final String SUB_I = START + "SubI" + MID + END;
    public static final String SUB_L = START + "SubL" + MID + END;
    public static final String SUB_F = START + "SubF" + MID + END;
    public static final String SUB_D = START + "SubD" + MID + END;
    public static final String MUL_I = START + "MulI" + MID + END;
    public static final String MUL_L = START + "MulL" + MID + END;
    public static final String CONV_I2L = START + "ConvI2L" + MID + END;

    public static final String VECTOR_CAST_B2X = START + "VectorCastB2X" + MID + END;
    public static final String VECTOR_CAST_S2X = START + "VectorCastS2X" + MID + END;
    public static final String VECTOR_CAST_I2X = START + "VectorCastI2X" + MID + END;
    public static final String VECTOR_CAST_L2X = START + "VectorCastL2X" + MID + END;
    public static final String VECTOR_CAST_F2X = START + "VectorCastF2X" + MID + END;
    public static final String VECTOR_CAST_D2X = START + "VectorCastD2X" + MID + END;
    public static final String VECTOR_REINTERPRET = START + "VectorReinterpret" + MID + END;

    public static void initMaps() {
        initMaps(IRNode.STORE, STORE);
        initMaps(IRNode.STORE_B, STORE_B);
        initMaps(IRNode.STORE_C, STORE_C);
        initMaps(IRNode.STORE_I, STORE_I);
        initMaps(IRNode.STORE_L, STORE_L);
        initMaps(IRNode.STORE_F, STORE_F);
        initMaps(IRNode.STORE_D, STORE_D);
        initMaps(IRNode.STORE_P, STORE_P);
        initMaps(IRNode.STORE_N, STORE_N);
        initMaps(IRNode.STORE_VECTOR, STORE_VECTOR);
        initMaps(IRNode.STORE_OF_CLASS, STORE_OF_CLASS);
        initMaps(IRNode.STORE_B_OF_CLASS, STORE_B_OF_CLASS);
        initMaps(IRNode.STORE_C_OF_CLASS, STORE_C_OF_CLASS);
        initMaps(IRNode.STORE_I_OF_CLASS, STORE_I_OF_CLASS);
        initMaps(IRNode.STORE_L_OF_CLASS, STORE_L_OF_CLASS);
        initMaps(IRNode.STORE_F_OF_CLASS, STORE_F_OF_CLASS);
        initMaps(IRNode.STORE_D_OF_CLASS, STORE_D_OF_CLASS);
        initMaps(IRNode.STORE_P_OF_CLASS, STORE_P_OF_CLASS);
        initMaps(IRNode.STORE_N_OF_CLASS, STORE_N_OF_CLASS);
        initMaps(IRNode.STORE_OF_FIELD, STORE_OF_FIELD);
        initMaps(IRNode.LOAD, LOAD);
        initMaps(IRNode.LOAD_B, LOAD_B);
        initMaps(IRNode.LOAD_UB, LOAD_UB);
        initMaps(IRNode.LOAD_S, LOAD_S);
        initMaps(IRNode.LOAD_US, LOAD_US);
        initMaps(IRNode.LOAD_I, LOAD_I);
        initMaps(IRNode.LOAD_L, LOAD_L);
        initMaps(IRNode.LOAD_F, LOAD_F);
        initMaps(IRNode.LOAD_D, LOAD_D);
        initMaps(IRNode.LOAD_P, LOAD_P);
        initMaps(IRNode.LOAD_N, LOAD_N);
        initMaps(IRNode.LOAD_VECTOR, LOAD_VECTOR);
        initMaps(IRNode.LOAD_OF_CLASS, LOAD_OF_CLASS);
        initMaps(IRNode.LOAD_B_OF_CLASS, LOAD_B_OF_CLASS);
        initMaps(IRNode.LOAD_UB_OF_CLASS, LOAD_UB_OF_CLASS);
        initMaps(IRNode.LOAD_S_OF_CLASS, LOAD_S_OF_CLASS);
        initMaps(IRNode.LOAD_US_OF_CLASS, LOAD_US_OF_CLASS);
        initMaps(IRNode.LOAD_I_OF_CLASS, LOAD_I_OF_CLASS);
        initMaps(IRNode.LOAD_L_OF_CLASS, LOAD_L_OF_CLASS);
        initMaps(IRNode.LOAD_F_OF_CLASS, LOAD_F_OF_CLASS);
        initMaps(IRNode.LOAD_D_OF_CLASS, LOAD_D_OF_CLASS);
        initMaps(IRNode.LOAD_P_OF_CLASS, LOAD_P_OF_CLASS);
        initMaps(IRNode.LOAD_N_OF_CLASS, LOAD_N_OF_CLASS);
        initMaps(IRNode.LOAD_OF_FIELD, LOAD_OF_FIELD);
        initMaps(IRNode.LOAD_KLASS, LOAD_KLASS);
        initMaps(IRNode.LOOP, LOOP);
        initMaps(IRNode.COUNTEDLOOP, COUNTEDLOOP);
        initMaps(IRNode.COUNTEDLOOP_MAIN, COUNTEDLOOP_MAIN);
        initMaps(IRNode.IF, IF);
        initMaps(IRNode.CALL, CALL);
        initMaps(IRNode.CALL_OF_METHOD, CALL_OF_METHOD);
        initMaps(IRNode.DYNAMIC_CALL_OF_METHOD, DYNAMIC_CALL_OF_METHOD);
        initMaps(IRNode.STATIC_CALL_OF_METHOD, STATIC_CALL_OF_METHOD);
        initMaps(IRNode.TRAP, TRAP);
        initMaps(IRNode.PREDICATE_TRAP, PREDICATE_TRAP);
        initMaps(IRNode.UNSTABLE_IF_TRAP, UNSTABLE_IF_TRAP);
        initMaps(IRNode.CLASS_CHECK_TRAP, CLASS_CHECK_TRAP);
        initMaps(IRNode.NULL_CHECK_TRAP, NULL_CHECK_TRAP);
        initMaps(IRNode.NULL_ASSERT_TRAP, NULL_ASSERT_TRAP);
        initMaps(IRNode.RANGE_CHECK_TRAP, RANGE_CHECK_TRAP);
        initMaps(IRNode.UNHANDLED_TRAP, UNHANDLED_TRAP);
        initMaps(IRNode.INTRINSIC_TRAP, INTRINSIC_TRAP);
        initMaps(IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP);
        initMaps(IRNode.MEMBAR, MEMBAR);
        initMaps(IRNode.ABS_I, ABS_I);
        initMaps(IRNode.ABS_L, ABS_L);
        initMaps(IRNode.ABS_F, ABS_F);
        initMaps(IRNode.ABS_D, ABS_D);
        initMaps(IRNode.AND_I, AND_I);
        initMaps(IRNode.AND_L, AND_L);
        initMaps(IRNode.XOR_I, XOR_I);
        initMaps(IRNode.XOR_L, XOR_L);
        initMaps(IRNode.LSHIFT_I, LSHIFT_I);
        initMaps(IRNode.LSHIFT_L, LSHIFT_L);
        initMaps(IRNode.ADD_I, ADD_I);
        initMaps(IRNode.ADD_L, ADD_L);
        initMaps(IRNode.ADD_VD, ADD_VD);
        initMaps(IRNode.SUB_I, SUB_I);
        initMaps(IRNode.SUB_L, SUB_L);
        initMaps(IRNode.SUB_F, SUB_F);
        initMaps(IRNode.SUB_D, SUB_D);
        initMaps(IRNode.MUL_I, MUL_I);
        initMaps(IRNode.MUL_L, MUL_L);
        initMaps(IRNode.CONV_I2L, CONV_I2L);
        initMaps(IRNode.VECTOR_CAST_B2X, VECTOR_CAST_B2X);
        initMaps(IRNode.VECTOR_CAST_S2X, VECTOR_CAST_S2X);
        initMaps(IRNode.VECTOR_CAST_I2X, VECTOR_CAST_I2X);
        initMaps(IRNode.VECTOR_CAST_L2X, VECTOR_CAST_L2X);
        initMaps(IRNode.VECTOR_CAST_F2X, VECTOR_CAST_F2X);
        initMaps(IRNode.VECTOR_CAST_D2X, VECTOR_CAST_D2X);
        initMaps(IRNode.VECTOR_REINTERPRET, VECTOR_REINTERPRET);
    }

    private static void initMaps(String defaultRegexString, String idealString) {
        DEFAULT_TO_PHASE_MAP.put(defaultRegexString, CompilePhase.PRINT_IDEAL);
        EnumMap<CompilePhase, String> enumMap = new EnumMap<>(CompilePhase.class);
        CompilePhase.getIdealPhases().forEach(phase -> enumMap.put(phase, idealString));
        enumMap.put(CompilePhase.DEFAULT, idealString);
        PLACEHOLDER_TO_REGEX_MAP.put(defaultRegexString, enumMap);
    }
}
