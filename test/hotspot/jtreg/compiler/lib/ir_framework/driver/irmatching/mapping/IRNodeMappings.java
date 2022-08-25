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

package compiler.lib.ir_framework.driver.irmatching.mapping;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.regexes.IdealDefaultRegexes;
import compiler.lib.ir_framework.driver.irmatching.regexes.OptoAssemblyDefaultRegexes;
import compiler.lib.ir_framework.shared.TestFormat;

import java.util.HashMap;
import java.util.Map;

public class IRNodeMappings {
    // Singleton
    private static final IRNodeMappings INSTANCE = new IRNodeMappings();
    private final Map<String, IRNodeMapEntry> irNodeMappings = new HashMap<>();

    /**
     * Definition of IRNode placeholder strings to regex(es) together with a default phase.
     */
    private IRNodeMappings() {
        idealOnly(IRNode.ABS_D, IdealDefaultRegexes.ABS_D);
        idealOnly(IRNode.ABS_F, IdealDefaultRegexes.ABS_F);
        idealOnly(IRNode.ABS_I, IdealDefaultRegexes.ABS_I);
        idealOnly(IRNode.ABS_L, IdealDefaultRegexes.ABS_L);
        idealOnly(IRNode.ADD, IdealDefaultRegexes.ADD);
        idealOnly(IRNode.ADD_I, IdealDefaultRegexes.ADD_I);
        idealOnly(IRNode.ADD_L, IdealDefaultRegexes.ADD_L);
        idealOnly(IRNode.ADD_VD, IdealDefaultRegexes.ADD_VD);
        idealFromBeforeCountedLoops(IRNode.ADD_VI, IdealDefaultRegexes.ADD_VI);
        allocNodes(IRNode.ALLOC, IdealDefaultRegexes.ALLOC, OptoAssemblyDefaultRegexes.ALLOC);
        optoOnly(IRNode.ALLOC_OF, OptoAssemblyDefaultRegexes.ALLOC_OF);
        allocNodes(IRNode.ALLOC_ARRAY, IdealDefaultRegexes.ALLOC_ARRAY, OptoAssemblyDefaultRegexes.ALLOC_ARRAY);
        optoOnly(IRNode.ALLOC_ARRAY_OF, OptoAssemblyDefaultRegexes.ALLOC_ARRAY_OF);
        idealOnly(IRNode.AND, IdealDefaultRegexes.AND);
        idealOnly(IRNode.AND_I, IdealDefaultRegexes.AND_I);
        idealOnly(IRNode.AND_L, IdealDefaultRegexes.AND_L);
        idealOnly(IRNode.AND_V, IdealDefaultRegexes.AND_V);
        idealOnly(IRNode.AND_V_MASK, IdealDefaultRegexes.AND_V_MASK);
        idealOnly(IRNode.CALL, IdealDefaultRegexes.CALL);
        idealOnly(IRNode.CALL_OF_METHOD, IdealDefaultRegexes.CALL_OF_METHOD);
        idealOnly(IRNode.CAST_II, IdealDefaultRegexes.CAST_II);
        idealOnly(IRNode.CAST_LL, IdealDefaultRegexes.CAST_LL);
        optoOnly(IRNode.CHECKCAST_ARRAY, OptoAssemblyDefaultRegexes.CHECKCAST_ARRAY);
        optoOnly(IRNode.CHECKCAST_ARRAYCOPY, OptoAssemblyDefaultRegexes.CHECKCAST_ARRAYCOPY);
        optoOnly(IRNode.CHECKCAST_ARRAY_OF, OptoAssemblyDefaultRegexes.CHECKCAST_ARRAY_OF);
        idealOnly(IRNode.CLASS_CHECK_TRAP, IdealDefaultRegexes.CLASS_CHECK_TRAP);
        idealOnly(IRNode.CMOVEI, IdealDefaultRegexes.CMOVEI);
        idealOnly(IRNode.CMP_U, IdealDefaultRegexes.CMP_U);
        idealOnly(IRNode.CMP_U3, IdealDefaultRegexes.CMP_U3);
        idealOnly(IRNode.CMP_UL, IdealDefaultRegexes.CMP_UL);
        idealOnly(IRNode.CMP_UL3, IdealDefaultRegexes.CMP_UL3);
        idealOnly(IRNode.CONV_I2L, IdealDefaultRegexes.CONV_I2L);
        idealOnly(IRNode.CONV_L2I, IdealDefaultRegexes.CONV_L2I);
        idealOnly(IRNode.CON_I, IdealDefaultRegexes.CON_I);
        idealOnly(IRNode.CON_L, IdealDefaultRegexes.CON_L);
        fromAfterCountedLoops(IRNode.COUNTED_LOOP, IdealDefaultRegexes.COUNTED_LOOP);
        fromAfterCountedLoops(IRNode.COUNTED_LOOP_MAIN, IdealDefaultRegexes.COUNTED_LOOP_MAIN);
        idealOnly(IRNode.DIV, IdealDefaultRegexes.DIV);
        idealOnly(IRNode.DIV_BY_ZERO_TRAP, IdealDefaultRegexes.DIV_BY_ZERO_TRAP);
        idealOnly(IRNode.DIV_L, IdealDefaultRegexes.DIV_L);
        idealOnly(IRNode.DYNAMIC_CALL_OF_METHOD, IdealDefaultRegexes.DYNAMIC_CALL_OF_METHOD);
        idealOnly(IRNode.FAST_LOCK, IdealDefaultRegexes.FAST_LOCK);
        idealFromMacroExpansion(IRNode.FAST_UNLOCK, IdealDefaultRegexes.FAST_UNLOCK);
        optoOnly(IRNode.FIELD_ACCESS, OptoAssemblyDefaultRegexes.FIELD_ACCESS);
        idealOnly(IRNode.IF, IdealDefaultRegexes.IF);
        idealOnly(IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, IdealDefaultRegexes.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP);
        idealOnly(IRNode.INTRINSIC_TRAP, IdealDefaultRegexes.INTRINSIC_TRAP);
        idealOnly(IRNode.LOAD, IdealDefaultRegexes.LOAD);
        idealOnly(IRNode.LOAD_B, IdealDefaultRegexes.LOAD_B);
        idealOnly(IRNode.LOAD_B_OF_CLASS, IdealDefaultRegexes.LOAD_B_OF_CLASS);
        idealOnly(IRNode.LOAD_D, IdealDefaultRegexes.LOAD_D);
        idealOnly(IRNode.LOAD_D_OF_CLASS, IdealDefaultRegexes.LOAD_D_OF_CLASS);
        idealOnly(IRNode.LOAD_F, IdealDefaultRegexes.LOAD_F);
        idealOnly(IRNode.LOAD_F_OF_CLASS, IdealDefaultRegexes.LOAD_F_OF_CLASS);
        idealOnly(IRNode.LOAD_I, IdealDefaultRegexes.LOAD_I);
        idealOnly(IRNode.LOAD_I_OF_CLASS, IdealDefaultRegexes.LOAD_I_OF_CLASS);
        idealOnly(IRNode.LOAD_KLASS, IdealDefaultRegexes.LOAD_KLASS);
        idealOnly(IRNode.LOAD_L, IdealDefaultRegexes.LOAD_L);
        idealOnly(IRNode.LOAD_L_OF_CLASS, IdealDefaultRegexes.LOAD_L_OF_CLASS);
        idealOnly(IRNode.LOAD_N, IdealDefaultRegexes.LOAD_N);
        idealOnly(IRNode.LOAD_N_OF_CLASS, IdealDefaultRegexes.LOAD_N_OF_CLASS);
        idealOnly(IRNode.LOAD_OF_CLASS, IdealDefaultRegexes.LOAD_OF_CLASS);
        idealOnly(IRNode.LOAD_OF_FIELD, IdealDefaultRegexes.LOAD_OF_FIELD);
        idealOnly(IRNode.LOAD_P, IdealDefaultRegexes.LOAD_P);
        idealOnly(IRNode.LOAD_P_OF_CLASS, IdealDefaultRegexes.LOAD_P_OF_CLASS);
        idealOnly(IRNode.LOAD_S, IdealDefaultRegexes.LOAD_S);
        idealOnly(IRNode.LOAD_S_OF_CLASS, IdealDefaultRegexes.LOAD_S_OF_CLASS);
        idealOnly(IRNode.LOAD_UB, IdealDefaultRegexes.LOAD_UB);
        idealOnly(IRNode.LOAD_UB_OF_CLASS, IdealDefaultRegexes.LOAD_UB_OF_CLASS);
        idealOnly(IRNode.LOAD_US, IdealDefaultRegexes.LOAD_US);
        idealOnly(IRNode.LOAD_US_OF_CLASS, IdealDefaultRegexes.LOAD_US_OF_CLASS);
        idealFromBeforeCountedLoops(IRNode.LOAD_VECTOR, IdealDefaultRegexes.LOAD_VECTOR);
        fromAfterCountedLoops(IRNode.LONG_COUNTED_LOOP, IdealDefaultRegexes.LONG_COUNTED_LOOP);
        fromBeforeCountedLoops(IRNode.LOOP, IdealDefaultRegexes.LOOP);
        idealOnly(IRNode.LSHIFT, IdealDefaultRegexes.LSHIFT);
        idealOnly(IRNode.LSHIFT_I, IdealDefaultRegexes.LSHIFT_I);
        idealOnly(IRNode.LSHIFT_L, IdealDefaultRegexes.LSHIFT_L);
        idealOnly(IRNode.MAX_I, IdealDefaultRegexes.MAX_I);
        idealOnly(IRNode.MAX_V, IdealDefaultRegexes.MAX_V);
        idealOnly(IRNode.MEMBAR, IdealDefaultRegexes.MEMBAR);
        idealOnly(IRNode.MEMBAR_STORESTORE, IdealDefaultRegexes.MEMBAR_STORESTORE);
        idealOnly(IRNode.MIN_I, IdealDefaultRegexes.MIN_I);
        idealOnly(IRNode.MIN_V, IdealDefaultRegexes.MIN_V);
        idealOnly(IRNode.MUL, IdealDefaultRegexes.MUL);
        idealOnly(IRNode.MUL_F, IdealDefaultRegexes.MUL_F);
        idealOnly(IRNode.MUL_I, IdealDefaultRegexes.MUL_I);
        idealOnly(IRNode.MUL_L, IdealDefaultRegexes.MUL_L);
        idealOnly(IRNode.NULL_ASSERT_TRAP, IdealDefaultRegexes.NULL_ASSERT_TRAP);
        idealOnly(IRNode.NULL_CHECK_TRAP, IdealDefaultRegexes.NULL_CHECK_TRAP);
        idealFromBeforeCountedLoops(IRNode.OR_V, IdealDefaultRegexes.OR_V);
        idealFromBeforeCountedLoops(IRNode.OR_V_MASK, IdealDefaultRegexes.OR_V_MASK);
        fromAfterCountedLoops(IRNode.OUTER_STRIP_MINED_LOOP, IdealDefaultRegexes.OUTER_STRIP_MINED_LOOP);
        idealOnly(IRNode.PHI, IdealDefaultRegexes.PHI);
        idealOnly(IRNode.POPCOUNT_L, IdealDefaultRegexes.POPCOUNT_L);
        idealFromBeforeCountedLoops(IRNode.POPULATE_INDEX, IdealDefaultRegexes.POPULATE_INDEX);
        idealOnly(IRNode.PREDICATE_TRAP, IdealDefaultRegexes.PREDICATE_TRAP);
        idealOnly(IRNode.RANGE_CHECK_TRAP, IdealDefaultRegexes.RANGE_CHECK_TRAP);
        idealFromBeforeCountedLoops(IRNode.REVERSE_BYTES_V, IdealDefaultRegexes.REVERSE_BYTES_V);
        idealOnly(IRNode.RSHIFT, IdealDefaultRegexes.RSHIFT);
        idealOnly(IRNode.RSHIFT_I, IdealDefaultRegexes.RSHIFT_I);
        idealOnly(IRNode.RSHIFT_L, IdealDefaultRegexes.RSHIFT_L);
        idealFromBeforeCountedLoops(IRNode.RSHIFT_VB, IdealDefaultRegexes.RSHIFT_VB);
        idealFromBeforeCountedLoops(IRNode.RSHIFT_VS, IdealDefaultRegexes.RSHIFT_VS);
        idealOnly(IRNode.SAFEPOINT, IdealDefaultRegexes.SAFEPOINT);
        optoOnly(IRNode.SCOPE_OBJECT, OptoAssemblyDefaultRegexes.SCOPE_OBJECT);
        idealOnly(IRNode.STATIC_CALL_OF_METHOD, IdealDefaultRegexes.STATIC_CALL_OF_METHOD);
        idealOnly(IRNode.STORE, IdealDefaultRegexes.STORE);
        idealOnly(IRNode.STORE_B, IdealDefaultRegexes.STORE_B);
        idealOnly(IRNode.STORE_B_OF_CLASS, IdealDefaultRegexes.STORE_B_OF_CLASS);
        idealOnly(IRNode.STORE_C, IdealDefaultRegexes.STORE_C);
        idealOnly(IRNode.STORE_C_OF_CLASS, IdealDefaultRegexes.STORE_C_OF_CLASS);
        idealOnly(IRNode.STORE_D, IdealDefaultRegexes.STORE_D);
        idealOnly(IRNode.STORE_D_OF_CLASS, IdealDefaultRegexes.STORE_D_OF_CLASS);
        idealOnly(IRNode.STORE_F, IdealDefaultRegexes.STORE_F);
        idealOnly(IRNode.STORE_F_OF_CLASS, IdealDefaultRegexes.STORE_F_OF_CLASS);
        idealOnly(IRNode.STORE_I, IdealDefaultRegexes.STORE_I);
        idealOnly(IRNode.STORE_I_OF_CLASS, IdealDefaultRegexes.STORE_I_OF_CLASS);
        idealOnly(IRNode.STORE_L, IdealDefaultRegexes.STORE_L);
        idealOnly(IRNode.STORE_L_OF_CLASS, IdealDefaultRegexes.STORE_L_OF_CLASS);
        idealOnly(IRNode.STORE_N, IdealDefaultRegexes.STORE_N);
        idealOnly(IRNode.STORE_N_OF_CLASS, IdealDefaultRegexes.STORE_N_OF_CLASS);
        idealOnly(IRNode.STORE_OF_CLASS, IdealDefaultRegexes.STORE_OF_CLASS);
        idealOnly(IRNode.STORE_OF_FIELD, IdealDefaultRegexes.STORE_OF_FIELD);
        idealOnly(IRNode.STORE_P, IdealDefaultRegexes.STORE_P);
        idealOnly(IRNode.STORE_P_OF_CLASS, IdealDefaultRegexes.STORE_P_OF_CLASS);
        idealFromBeforeCountedLoops(IRNode.STORE_VECTOR, IdealDefaultRegexes.STORE_VECTOR);
        idealOnly(IRNode.SUB, IdealDefaultRegexes.SUB);
        idealOnly(IRNode.SUB_D, IdealDefaultRegexes.SUB_D);
        idealOnly(IRNode.SUB_F, IdealDefaultRegexes.SUB_F);
        idealOnly(IRNode.SUB_I, IdealDefaultRegexes.SUB_I);
        idealOnly(IRNode.SUB_L, IdealDefaultRegexes.SUB_L);
        idealOnly(IRNode.TRAP, IdealDefaultRegexes.TRAP);
        idealOnly(IRNode.UNHANDLED_TRAP, IdealDefaultRegexes.UNHANDLED_TRAP);
        idealOnly(IRNode.UNSTABLE_IF_TRAP, IdealDefaultRegexes.UNSTABLE_IF_TRAP);
        idealOnly(IRNode.URSHIFT, IdealDefaultRegexes.URSHIFT);
        idealOnly(IRNode.URSHIFT_I, IdealDefaultRegexes.URSHIFT_I);
        idealOnly(IRNode.URSHIFT_L, IdealDefaultRegexes.URSHIFT_L);
        idealFromBeforeCountedLoops(IRNode.VECTOR_BLEND, IdealDefaultRegexes.VECTOR_BLEND);
        idealFromBeforeCountedLoops(IRNode.VECTOR_CAST_B2X, IdealDefaultRegexes.VECTOR_CAST_B2X);
        idealFromBeforeCountedLoops(IRNode.VECTOR_CAST_D2X, IdealDefaultRegexes.VECTOR_CAST_D2X);
        idealFromBeforeCountedLoops(IRNode.VECTOR_CAST_F2X, IdealDefaultRegexes.VECTOR_CAST_F2X);
        idealFromBeforeCountedLoops(IRNode.VECTOR_CAST_I2X, IdealDefaultRegexes.VECTOR_CAST_I2X);
        idealFromBeforeCountedLoops(IRNode.VECTOR_CAST_L2X, IdealDefaultRegexes.VECTOR_CAST_L2X);
        idealFromBeforeCountedLoops(IRNode.VECTOR_CAST_S2X, IdealDefaultRegexes.VECTOR_CAST_S2X);
        idealFromBeforeCountedLoops(IRNode.VECTOR_REINTERPRET, IdealDefaultRegexes.VECTOR_REINTERPRET);
        idealFromBeforeCountedLoops(IRNode.VECTOR_UCAST_B2X, IdealDefaultRegexes.VECTOR_UCAST_B2X);
        idealFromBeforeCountedLoops(IRNode.VECTOR_UCAST_I2X, IdealDefaultRegexes.VECTOR_UCAST_I2X);
        idealFromBeforeCountedLoops(IRNode.VECTOR_UCAST_S2X, IdealDefaultRegexes.VECTOR_UCAST_S2X);
        idealOnly(IRNode.XOR_I, IdealDefaultRegexes.XOR_I);
        idealOnly(IRNode.XOR_L, IdealDefaultRegexes.XOR_L);
        idealFromBeforeCountedLoops(IRNode.XOR_V, IdealDefaultRegexes.XOR_V);
        idealFromBeforeCountedLoops(IRNode.XOR_V_MASK, IdealDefaultRegexes.XOR_V_MASK);
    }

    private void allocNodes(String irNode, String idealRegex, String optoRegex) {
        Map<PhaseInterval, String> intervalToRegexMap = new HashMap<>();
        intervalToRegexMap.put(new PhaseInterval(CompilePhase.BEFORE_REMOVEUSELESS, CompilePhase.PHASEIDEALLOOP_ITERATIONS),
                               idealRegex);
        intervalToRegexMap.put(new PhaseSingletonSet(CompilePhase.PRINT_OPTO_ASSEMBLY), optoRegex);
        MultiPhaseRangeEntry entry = new MultiPhaseRangeEntry(CompilePhase.PRINT_OPTO_ASSEMBLY, intervalToRegexMap);
        irNodeMappings.put(irNode, entry);
    }

    private void idealOnly(String irNode, String regex) {
        irNodeMappings.put(irNode, new IdealOnlyEntry(CompilePhase.PRINT_IDEAL, regex));
    }

    private void optoOnly(String irNode, String regex) {
        irNodeMappings.put(irNode, new SinglePhaseEntry(CompilePhase.PRINT_OPTO_ASSEMBLY, regex));
    }

    private void machOnly(String irNode, String regex) {
        irNodeMappings.put(irNode, new MachOnlyEntry(CompilePhase.FINAL_CODE, regex));
    }

    private void fromAfterCountedLoops(String irNode, String regex) {
        irNodeMappings.put(irNode, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                             CompilePhase.AFTER_CLOOPS, CompilePhase.FINAL_CODE));
    }

    private void fromBeforeCountedLoops(String irNode, String regex) {
        irNodeMappings.put(irNode, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                             CompilePhase.BEFORE_CLOOPS, CompilePhase.FINAL_CODE));
    }

    private void idealFromBeforeCountedLoops(String irNode, String regex) {
        irNodeMappings.put(irNode, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                             CompilePhase.BEFORE_CLOOPS, CompilePhase.BEFORE_MATCHING));
    }

    private void idealFromMacroExpansion(String irNode, String regex) {
        irNodeMappings.put(irNode, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                             CompilePhase.MACRO_EXPANSION, CompilePhase.BEFORE_MATCHING));
    }


    public static String getRegexForPhaseOfIRNode(String irNode, CompilePhase compilePhase) {
        IRNodeMapEntry entry = INSTANCE.irNodeMappings.get(irNode);
        TestFormat.checkNoReport(entry != null,
                                 "IR Node \"" + irNode + "\" defined in class IRNode has no entry in the " +
                                 "constructor of IRNodeMappings. Have you just created the entry \"" + irNode + "\" in" +
                                 "class IRNode and forgot to add a mapping? Violation");
        String regex = entry.getRegexForPhase(compilePhase);
        TestFormat.checkNoReport(regex != null,
                                 "IR Node \"" + irNode + "\" defined in class IRNode has no regex " +
                                 "defined for compile phase " + compilePhase + ". If you think it should be supported, " +
                                 "add a mapping in IRNodeMappings. Violation");
        return regex;
    }

    public static CompilePhase getDefaultPhaseForIRNode(String irNode) {
        IRNodeMapEntry entry = INSTANCE.irNodeMappings.get(irNode);
        TestFormat.checkNoReport(entry != null,
                                 "\"" + irNode + "\" is not an IRNode defined in class IRNode and " +
                                 "has therefore no default compile phase specified. Set the @IR phase attribute for " +
                                 "user defined regexes to a value different from CompilePhase.DEFAULT. Violation");
        return entry.getDefaultCompilePhase();
    }
}
