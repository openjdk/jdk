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

import static compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexConstants.*;

/**
 * This class provides default regex strings for matches on the machine independent ideal graph including PrintIdeal
 * (i.e. {@link CompilePhase compile phases} before matching and code generation). These default regexes are used to
 * replace IR node placeholder strings (defined in {@link IRNode}) found in check attributes {@link IR#failOn()} and
 * {@link IR#counts()}. The mappings in {@link compiler.lib.ir_framework.driver.irmatching.mapping.IRNodeMappings}
 * specify on which compile phases a default regex is applied.
 *
 * @see IR
 * @see IRNode
 * @see compiler.lib.ir_framework.driver.irmatching.mapping.IRNodeMappings
 */
public class IdealIndependentDefaultRegexes {
    public static final String ABS_D = START + "AbsD" + MID + END;
    public static final String ABS_F = START + "AbsF" + MID + END;
    public static final String ABS_I = START + "AbsI" + MID + END;
    public static final String ABS_L = START + "AbsL" + MID + END;
    public static final String ADD = START + "Add(I|L|F|D|P)" + MID + END;
    public static final String ADD_I = START + "AddI" + MID + END;
    public static final String ADD_L = START + "AddL" + MID + END;
    public static final String ADD_VD = START + "AddVD" + MID + END;
    public static final String ADD_VI = START + "AddVI" + MID + END;
    public static final String ALLOC = START + "Allocate" + MID + END;
    public static final String ALLOC_ARRAY = START + "AllocateArray" + MID + END;
    public static final String AND = START + "And(I|L)" + MID + END;
    public static final String AND_I = START + "AndI" + MID + END;
    public static final String AND_L = START + "AndL" + MID + END;
    public static final String AND_V = START + "AndV" + MID + END;
    public static final String AND_V_MASK = START + "AndVMask" + MID + END;
    public static final String CALL = START + "Call.*Java" + MID + END;
    public static final String CALL_OF_METHOD = START + "Call.*Java" + MID + IS_REPLACED + " " +  END;
    public static final String CAST_II = START + "CastII" + MID + END;
    public static final String CAST_LL = START + "CastLL" + MID + END;
    public static final String CLASS_CHECK_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*class_check" + END;
    public static final String CMOVE_I = START + "CMoveI" + MID + END;
    public static final String CMP_I = START + "CmpI" + MID + END;
    public static final String CMP_L = START + "CmpL" + MID + END;
    public static final String CMP_U = START + "CmpU" + MID + END;
    public static final String CMP_U3 = START + "CmpU3" + MID + END;
    public static final String CMP_UL = START + "CmpUL" + MID + END;
    public static final String CMP_UL3 = START + "CmpUL3" + MID + END;
    public static final String COMPRESS_BITS = START + "CompressBits" + MID + END;
    public static final String CONV_I2L = START + "ConvI2L" + MID + END;
    public static final String CONV_L2I = START + "ConvL2I" + MID + END;
    public static final String CON_I = START + "ConI" + MID + END;
    public static final String CON_L = START + "ConL" + MID + END;
    public static final String COUNTED_LOOP = START + "CountedLoop\\b" + MID + END;
    public static final String COUNTED_LOOP_MAIN = START + "CountedLoop\\b" + MID + "main" + END;
    public static final String DIV = START + "Div(I|L|F|D)" + MID + END;
    public static final String DIV_BY_ZERO_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*div0_check" + END;
    public static final String DIV_L = START + "DivL" + MID + END;
    public static final String DYNAMIC_CALL_OF_METHOD = START + "CallDynamicJava" + MID + IS_REPLACED + " " + END;
    public static final String EXPAND_BITS = START + "ExpandBits" + MID + END;
    public static final String FAST_LOCK   = START + "FastLock" + MID + END;
    public static final String FAST_UNLOCK = START + "FastUnlock" + MID + END;
    public static final String IF = START + "If\\b" + MID + END;
    // Does not work for VM builds without JVMCI like x86_32 (a rule containing this regex will be skipped without having JVMCI built).
    public static final String INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*intrinsic_or_type_checked_inlining" + END;
    public static final String INTRINSIC_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*intrinsic" + END;
    public static final String LOAD = START + "Load(B|UB|S|US|I|L|F|D|P|N)" + MID + END;
    public static final String LOAD_B = START + "LoadB" + MID + END;
    public static final String LOAD_B_OF_CLASS = START + "LoadB" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_D = START + "LoadD" + MID + END;
    public static final String LOAD_D_OF_CLASS = START + "LoadD" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_F = START + "LoadF" + MID + END;
    public static final String LOAD_F_OF_CLASS = START + "LoadF" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_I = START + "LoadI" + MID + END;
    public static final String LOAD_I_OF_CLASS = START + "LoadI" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_KLASS  = START + "LoadK" + MID + END;
    public static final String LOAD_L = START + "LoadL" + MID + END;
    public static final String LOAD_L_OF_CLASS = START + "LoadL" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_N = START + "LoadN" + MID + END;
    public static final String LOAD_N_OF_CLASS = START + "LoadN" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_OF_CLASS = START + "Load(B|UB|S|US|I|L|F|D|P|N)" + MID + "@\\S*"+  IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_OF_FIELD = START + "Load(B|C|S|I|L|F|D|P|N)" + MID + "@.*name=" + IS_REPLACED + ",.*" + END;
    public static final String LOAD_P = START + "LoadP" + MID + END;
    public static final String LOAD_P_OF_CLASS = START + "LoadP" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_S = START + "LoadS" + MID + END;
    public static final String LOAD_S_OF_CLASS = START + "LoadS" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_UB = START + "LoadUB" + MID + END; // Load from boolean
    public static final String LOAD_UB_OF_CLASS = START + "LoadUB" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_US = START + "LoadUS" + MID + END; // Load from char
    public static final String LOAD_US_OF_CLASS = START + "LoadUS" + MID + "@\\S*" + IS_REPLACED + LOAD_OF_CLASS_POSTFIX;
    public static final String LOAD_VECTOR = START + "LoadVector" + MID + END;
    public static final String LONG_COUNTED_LOOP = START + "LongCountedLoop\\b" + MID + END;
    public static final String LOOP   = START + "Loop" + MID + END;
    public static final String LSHIFT = START + "LShift(I|L)" + MID + END;
    public static final String LSHIFT_I = START + "LShiftI" + MID + END;
    public static final String LSHIFT_L = START + "LShiftL" + MID + END;
    public static final String MAX_I = START + "MaxI" + MID + END;
    public static final String MAX_V = START + "MaxV" + MID + END;
    public static final String MEMBAR = START + "MemBar" + MID + END;
    public static final String MEMBAR_STORESTORE = START + "MemBarStoreStore" + MID + END;
    public static final String MIN_I = START + "MinI" + MID + END;
    public static final String MIN_V = START + "MinV" + MID + END;
    public static final String MUL = START + "Mul(I|L|F|D)" + MID + END;
    public static final String MUL_F = START + "MulF" + MID + END;
    public static final String MUL_I = START + "MulI" + MID + END;
    public static final String MUL_L = START + "MulL" + MID + END;
    public static final String NULL_ASSERT_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*null_assert" + END;
    public static final String NULL_CHECK_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*null_check" + END;
    public static final String OR_V = START + "OrV" + MID + END;
    public static final String OR_V_MASK = START + "OrVMask" + MID + END;
    public static final String OUTER_STRIP_MINED_LOOP = START + "OuterStripMinedLoop\\b" + MID + END;
    public static final String PHI = START + "Phi" + MID + END;
    public static final String POPCOUNT_L = START + "PopCountL" + MID + END;
    public static final String POPULATE_INDEX = START + "PopulateIndex" + MID + END;
    public static final String PREDICATE_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*predicate" + END;
    public static final String RANGE_CHECK_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*range_check" + END;
    public static final String REVERSE_BYTES_V = START + "ReverseBytesV" + MID + END;
    public static final String REVERSE_I = START + "ReverseI" + MID + END;
    public static final String REVERSE_L = START + "ReverseL" + MID + END;
    public static final String REVERSE_V = START + "ReverseV" + MID + END;
    public static final String ROUND_VD = START + "RoundVD" + MID + END;
    public static final String ROUND_VF = START + "RoundVF" + MID + END;
    public static final String RSHIFT = START + "RShift(I|L)" + MID + END;
    public static final String RSHIFT_I = START + "RShiftI" + MID + END;
    public static final String RSHIFT_L = START + "RShiftL" + MID + END;
    public static final String RSHIFT_VB = START + "RShiftVB" + MID + END;
    public static final String RSHIFT_VS = START + "RShiftVS" + MID + END;
    public static final String SAFEPOINT = START + "SafePoint" + MID + END;
    public static final String SIGNUM_VD = START + "SignumVD" + MID + END;
    public static final String SIGNUM_VF = START + "SignumVF" + MID + END;
    public static final String STATIC_CALL_OF_METHOD = START + "CallStaticJava" + MID + IS_REPLACED + " " +  END;
    public static final String STORE = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + END;
    public static final String STORE_B = START + "StoreB" + MID + END; // Store to boolean is also mapped to byte
    public static final String STORE_B_OF_CLASS = START + "StoreB" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_C = START + "StoreC" + MID + END;
    public static final String STORE_C_OF_CLASS = START + "StoreC" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_D = START + "StoreD" + MID + END;
    public static final String STORE_D_OF_CLASS = START + "StoreD" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_F = START + "StoreF" + MID + END;
    public static final String STORE_F_OF_CLASS = START + "StoreF" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_I = START + "StoreI" + MID + END; // Store to short is also mapped to int
    public static final String STORE_I_OF_CLASS = START + "StoreI" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_L = START + "StoreL" + MID + END;
    public static final String STORE_L_OF_CLASS = START + "StoreL" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_N = START + "StoreN" + MID + END;
    public static final String STORE_N_OF_CLASS = START + "StoreN" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_OF_CLASS = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_OF_FIELD = START + "Store(B|C|S|I|L|F|D|P|N)" + MID + "@.*name=" + IS_REPLACED + ",.*" + END;
    public static final String STORE_P = START + "StoreP" + MID + END;
    public static final String STORE_P_OF_CLASS = START + "StoreP" + MID + "@\\S*" + IS_REPLACED + STORE_OF_CLASS_POSTFIX;
    public static final String STORE_VECTOR = START + "StoreVector" + MID + END;
    public static final String SUB = START + "Sub(I|L|F|D)" + MID + END;
    public static final String SUB_D = START + "SubD" + MID + END;
    public static final String SUB_F = START + "SubF" + MID + END;
    public static final String SUB_I = START + "SubI" + MID + END;
    public static final String SUB_L = START + "SubL" + MID + END;
    public static final String TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*reason" + END;
    public static final String UDIV_I = START + "UDivI" + MID + END;
    public static final String UDIV_L = START + "UDivL" + MID + END;
    public static final String UDIV_MOD_I = START + "UDivModI" + MID + END;
    public static final String UDIV_MOD_L = START + "UDivModL" + MID + END;
    public static final String UMOD_I = START + "UModI" + MID + END;
    public static final String UMOD_L = START + "UModL" + MID + END;
    public static final String UNHANDLED_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*unhandled" + END;
    public static final String UNSTABLE_IF_TRAP = START + "CallStaticJava" + MID + "uncommon_trap.*unstable_if" + END;
    public static final String URSHIFT = START + "URShift(B|S|I|L)" + MID + END;
    public static final String URSHIFT_I = START + "URShiftI" + MID + END;
    public static final String URSHIFT_L = START + "URShiftL" + MID + END;
    public static final String VECTOR_BLEND = START + "VectorBlend" + MID + END;
    public static final String VECTOR_CAST_B2X = START + "VectorCastB2X" + MID + END;
    public static final String VECTOR_CAST_D2X = START + "VectorCastD2X" + MID + END;
    public static final String VECTOR_CAST_F2X = START + "VectorCastF2X" + MID + END;
    public static final String VECTOR_CAST_I2X = START + "VectorCastI2X" + MID + END;
    public static final String VECTOR_CAST_L2X = START + "VectorCastL2X" + MID + END;
    public static final String VECTOR_CAST_S2X = START + "VectorCastS2X" + MID + END;
    public static final String VECTOR_REINTERPRET = START + "VectorReinterpret" + MID + END;
    public static final String VECTOR_UCAST_B2X = START + "VectorUCastB2X" + MID + END;
    public static final String VECTOR_UCAST_I2X = START + "VectorUCastI2X" + MID + END;
    public static final String VECTOR_UCAST_S2X = START + "VectorUCastS2X" + MID + END;
    public static final String XOR_I = START + "XorI" + MID + END;
    public static final String XOR_L = START + "XorL" + MID + END;
    public static final String XOR_V = START + "XorV" + MID + END;
    public static final String XOR_V_MASK = START + "XorVMask" + MID + END;
}
