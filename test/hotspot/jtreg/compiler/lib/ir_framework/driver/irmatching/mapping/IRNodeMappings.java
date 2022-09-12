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
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.regexes.IdealIndependentDefaultRegexes;
import compiler.lib.ir_framework.driver.irmatching.regexes.OptoAssemblyDefaultRegexes;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.util.HashMap;
import java.util.Map;

/**
 * This class defines in its constructor a mapping of each {@link IRNode} placeholder string to one or more default
 * regexes found in {@link compiler.lib.ir_framework.driver.irmatching.regexes}. The IR framework will automatically
 * replace each {@link IRNode} placeholder string in the user defined test with a default regex depending on the
 * selected compile phases in {@link IR#phase}.
 *
 * <p>
 * Each mapping must define a default compile phase which is applied when the user does not explicitly set the
 * {@link IR#phase()} attribute or when directly using {@link CompilePhase#DEFAULT}. In this case, the IR framework
 * falls back on the default compile phase of any {@link IRNodeMapEntry}.
 *
 * <p>
 * Each newly entered {@link IRNode} entry must be accompanied by a corresponding mapping in this class. If a user test
 * specifies a compile phase for which no mapping is defined in this class, a {@link TestFormatException} is thrown.
 *
 * @see IR
 * @see IRNode
 * @see IRNodeMapEntry
 */
public class IRNodeMappings {
    // Singleton
    private static final IRNodeMappings INSTANCE = new IRNodeMappings();
    private final Map<String, IRNodeMapEntry> irNodeMappings = new HashMap<>();

    /**
     * Mapping of IR nodes (IRNode placeholder strings) to regex(es) together with a default phase.
     */
    private IRNodeMappings() {
        idealIndependentOnly(IRNode.ABS_D, IdealIndependentDefaultRegexes.ABS_D);
        idealIndependentOnly(IRNode.ABS_F, IdealIndependentDefaultRegexes.ABS_F);
        idealIndependentOnly(IRNode.ABS_I, IdealIndependentDefaultRegexes.ABS_I);
        idealIndependentOnly(IRNode.ABS_L, IdealIndependentDefaultRegexes.ABS_L);
        idealIndependentOnly(IRNode.ADD, IdealIndependentDefaultRegexes.ADD);
        idealIndependentOnly(IRNode.ADD_I, IdealIndependentDefaultRegexes.ADD_I);
        idealIndependentOnly(IRNode.ADD_L, IdealIndependentDefaultRegexes.ADD_L);
        idealIndependentOnly(IRNode.ADD_VD, IdealIndependentDefaultRegexes.ADD_VD);
        idealIndependentOnly(IRNode.ADD_VI, IdealIndependentDefaultRegexes.ADD_VI);
        allocNodes(IRNode.ALLOC, IdealIndependentDefaultRegexes.ALLOC, OptoAssemblyDefaultRegexes.ALLOC);
        optoOnly(IRNode.ALLOC_OF, OptoAssemblyDefaultRegexes.ALLOC_OF);
        allocNodes(IRNode.ALLOC_ARRAY, IdealIndependentDefaultRegexes.ALLOC_ARRAY, OptoAssemblyDefaultRegexes.ALLOC_ARRAY);
        optoOnly(IRNode.ALLOC_ARRAY_OF, OptoAssemblyDefaultRegexes.ALLOC_ARRAY_OF);
        idealIndependentOnly(IRNode.AND, IdealIndependentDefaultRegexes.AND);
        idealIndependentOnly(IRNode.AND_I, IdealIndependentDefaultRegexes.AND_I);
        idealIndependentOnly(IRNode.AND_L, IdealIndependentDefaultRegexes.AND_L);
        idealIndependentOnly(IRNode.AND_V, IdealIndependentDefaultRegexes.AND_V);
        idealIndependentOnly(IRNode.AND_V_MASK, IdealIndependentDefaultRegexes.AND_V_MASK);
        idealIndependentOnly(IRNode.CALL, IdealIndependentDefaultRegexes.CALL);
        idealIndependentOnly(IRNode.CALL_OF_METHOD, IdealIndependentDefaultRegexes.CALL_OF_METHOD);
        idealIndependentOnly(IRNode.CAST_II, IdealIndependentDefaultRegexes.CAST_II);
        idealIndependentOnly(IRNode.CAST_LL, IdealIndependentDefaultRegexes.CAST_LL);
        optoOnly(IRNode.CHECKCAST_ARRAY, OptoAssemblyDefaultRegexes.CHECKCAST_ARRAY);
        optoOnly(IRNode.CHECKCAST_ARRAYCOPY, OptoAssemblyDefaultRegexes.CHECKCAST_ARRAYCOPY);
        optoOnly(IRNode.CHECKCAST_ARRAY_OF, OptoAssemblyDefaultRegexes.CHECKCAST_ARRAY_OF);
        idealIndependentOnly(IRNode.CLASS_CHECK_TRAP, IdealIndependentDefaultRegexes.CLASS_CHECK_TRAP);
        idealIndependentOnly(IRNode.CMOVE_I, IdealIndependentDefaultRegexes.CMOVE_I);
        idealIndependentOnly(IRNode.CMP_I, IdealIndependentDefaultRegexes.CMP_I);
        idealIndependentOnly(IRNode.CMP_L, IdealIndependentDefaultRegexes.CMP_L);
        idealIndependentOnly(IRNode.CMP_U, IdealIndependentDefaultRegexes.CMP_U);
        idealIndependentOnly(IRNode.CMP_U3, IdealIndependentDefaultRegexes.CMP_U3);
        idealIndependentOnly(IRNode.CMP_UL, IdealIndependentDefaultRegexes.CMP_UL);
        idealIndependentOnly(IRNode.CMP_UL3, IdealIndependentDefaultRegexes.CMP_UL3);
        idealIndependentOnly(IRNode.COMPRESS_BITS, IdealIndependentDefaultRegexes.COMPRESS_BITS);
        idealIndependentOnly(IRNode.CONV_I2L, IdealIndependentDefaultRegexes.CONV_I2L);
        idealIndependentOnly(IRNode.CONV_L2I, IdealIndependentDefaultRegexes.CONV_L2I);
        idealIndependentOnly(IRNode.CON_I, IdealIndependentDefaultRegexes.CON_I);
        idealIndependentOnly(IRNode.CON_L, IdealIndependentDefaultRegexes.CON_L);
        fromAfterCountedLoops(IRNode.COUNTED_LOOP, IdealIndependentDefaultRegexes.COUNTED_LOOP);
        fromAfterCountedLoops(IRNode.COUNTED_LOOP_MAIN, IdealIndependentDefaultRegexes.COUNTED_LOOP_MAIN);
        idealIndependentOnly(IRNode.DIV, IdealIndependentDefaultRegexes.DIV);
        idealIndependentOnly(IRNode.DIV_BY_ZERO_TRAP, IdealIndependentDefaultRegexes.DIV_BY_ZERO_TRAP);
        idealIndependentOnly(IRNode.DIV_L, IdealIndependentDefaultRegexes.DIV_L);
        idealIndependentOnly(IRNode.DYNAMIC_CALL_OF_METHOD, IdealIndependentDefaultRegexes.DYNAMIC_CALL_OF_METHOD);
        idealIndependentOnly(IRNode.EXPAND_BITS, IdealIndependentDefaultRegexes.EXPAND_BITS);
        idealIndependentOnly(IRNode.FAST_LOCK, IdealIndependentDefaultRegexes.FAST_LOCK);
        idealFromMacroExpansion(IRNode.FAST_UNLOCK, IdealIndependentDefaultRegexes.FAST_UNLOCK);
        optoOnly(IRNode.FIELD_ACCESS, OptoAssemblyDefaultRegexes.FIELD_ACCESS);
        idealIndependentOnly(IRNode.IF, IdealIndependentDefaultRegexes.IF);
        idealIndependentOnly(IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, IdealIndependentDefaultRegexes.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP);
        idealIndependentOnly(IRNode.INTRINSIC_TRAP, IdealIndependentDefaultRegexes.INTRINSIC_TRAP);
        idealIndependentOnly(IRNode.LOAD, IdealIndependentDefaultRegexes.LOAD);
        idealIndependentOnly(IRNode.LOAD_B, IdealIndependentDefaultRegexes.LOAD_B);
        idealIndependentOnly(IRNode.LOAD_B_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_B_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_D, IdealIndependentDefaultRegexes.LOAD_D);
        idealIndependentOnly(IRNode.LOAD_D_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_D_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_F, IdealIndependentDefaultRegexes.LOAD_F);
        idealIndependentOnly(IRNode.LOAD_F_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_F_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_I, IdealIndependentDefaultRegexes.LOAD_I);
        idealIndependentOnly(IRNode.LOAD_I_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_I_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_KLASS, IdealIndependentDefaultRegexes.LOAD_KLASS);
        idealIndependentOnly(IRNode.LOAD_L, IdealIndependentDefaultRegexes.LOAD_L);
        idealIndependentOnly(IRNode.LOAD_L_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_L_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_N, IdealIndependentDefaultRegexes.LOAD_N);
        idealIndependentOnly(IRNode.LOAD_N_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_N_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_OF_FIELD, IdealIndependentDefaultRegexes.LOAD_OF_FIELD);
        idealIndependentOnly(IRNode.LOAD_P, IdealIndependentDefaultRegexes.LOAD_P);
        idealIndependentOnly(IRNode.LOAD_P_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_P_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_S, IdealIndependentDefaultRegexes.LOAD_S);
        idealIndependentOnly(IRNode.LOAD_S_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_S_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_UB, IdealIndependentDefaultRegexes.LOAD_UB);
        idealIndependentOnly(IRNode.LOAD_UB_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_UB_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_US, IdealIndependentDefaultRegexes.LOAD_US);
        idealIndependentOnly(IRNode.LOAD_US_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_US_OF_CLASS);
        idealIndependentOnly(IRNode.LOAD_VECTOR, IdealIndependentDefaultRegexes.LOAD_VECTOR);
        fromAfterCountedLoops(IRNode.LONG_COUNTED_LOOP, IdealIndependentDefaultRegexes.LONG_COUNTED_LOOP);
        fromBeforeCountedLoops(IRNode.LOOP, IdealIndependentDefaultRegexes.LOOP);
        idealIndependentOnly(IRNode.LSHIFT, IdealIndependentDefaultRegexes.LSHIFT);
        idealIndependentOnly(IRNode.LSHIFT_I, IdealIndependentDefaultRegexes.LSHIFT_I);
        idealIndependentOnly(IRNode.LSHIFT_L, IdealIndependentDefaultRegexes.LSHIFT_L);
        idealIndependentOnly(IRNode.MAX_I, IdealIndependentDefaultRegexes.MAX_I);
        idealIndependentOnly(IRNode.MAX_V, IdealIndependentDefaultRegexes.MAX_V);
        idealIndependentOnly(IRNode.MEMBAR, IdealIndependentDefaultRegexes.MEMBAR);
        idealIndependentOnly(IRNode.MEMBAR_STORESTORE, IdealIndependentDefaultRegexes.MEMBAR_STORESTORE);
        idealIndependentOnly(IRNode.MIN_I, IdealIndependentDefaultRegexes.MIN_I);
        idealIndependentOnly(IRNode.MIN_V, IdealIndependentDefaultRegexes.MIN_V);
        idealIndependentOnly(IRNode.MUL, IdealIndependentDefaultRegexes.MUL);
        idealIndependentOnly(IRNode.MUL_F, IdealIndependentDefaultRegexes.MUL_F);
        idealIndependentOnly(IRNode.MUL_I, IdealIndependentDefaultRegexes.MUL_I);
        idealIndependentOnly(IRNode.MUL_L, IdealIndependentDefaultRegexes.MUL_L);
        idealIndependentOnly(IRNode.NULL_ASSERT_TRAP, IdealIndependentDefaultRegexes.NULL_ASSERT_TRAP);
        idealIndependentOnly(IRNode.NULL_CHECK_TRAP, IdealIndependentDefaultRegexes.NULL_CHECK_TRAP);
        idealIndependentOnly(IRNode.OR_V, IdealIndependentDefaultRegexes.OR_V);
        idealIndependentOnly(IRNode.OR_V_MASK, IdealIndependentDefaultRegexes.OR_V_MASK);
        fromAfterCountedLoops(IRNode.OUTER_STRIP_MINED_LOOP, IdealIndependentDefaultRegexes.OUTER_STRIP_MINED_LOOP);
        idealIndependentOnly(IRNode.PHI, IdealIndependentDefaultRegexes.PHI);
        idealIndependentOnly(IRNode.POPCOUNT_L, IdealIndependentDefaultRegexes.POPCOUNT_L);
        idealAfterCountedLoops(IRNode.POPULATE_INDEX, IdealIndependentDefaultRegexes.POPULATE_INDEX);
        idealIndependentOnly(IRNode.PREDICATE_TRAP, IdealIndependentDefaultRegexes.PREDICATE_TRAP);
        idealIndependentOnly(IRNode.RANGE_CHECK_TRAP, IdealIndependentDefaultRegexes.RANGE_CHECK_TRAP);
        idealIndependentOnly(IRNode.REVERSE_BYTES_V, IdealIndependentDefaultRegexes.REVERSE_BYTES_V);
        idealIndependentOnly(IRNode.REVERSE_I, IdealIndependentDefaultRegexes.REVERSE_I);
        idealIndependentOnly(IRNode.REVERSE_L, IdealIndependentDefaultRegexes.REVERSE_L);
        idealIndependentOnly(IRNode.REVERSE_V, IdealIndependentDefaultRegexes.REVERSE_V);
        idealIndependentOnly(IRNode.ROUND_VD, IdealIndependentDefaultRegexes.ROUND_VD);
        idealIndependentOnly(IRNode.ROUND_VF, IdealIndependentDefaultRegexes.ROUND_VF);
        idealIndependentOnly(IRNode.RSHIFT, IdealIndependentDefaultRegexes.RSHIFT);
        idealIndependentOnly(IRNode.RSHIFT_I, IdealIndependentDefaultRegexes.RSHIFT_I);
        idealIndependentOnly(IRNode.RSHIFT_L, IdealIndependentDefaultRegexes.RSHIFT_L);
        idealIndependentOnly(IRNode.RSHIFT_VB, IdealIndependentDefaultRegexes.RSHIFT_VB);
        idealIndependentOnly(IRNode.RSHIFT_VS, IdealIndependentDefaultRegexes.RSHIFT_VS);
        idealIndependentOnly(IRNode.SAFEPOINT, IdealIndependentDefaultRegexes.SAFEPOINT);
        optoOnly(IRNode.SCOPE_OBJECT, OptoAssemblyDefaultRegexes.SCOPE_OBJECT);
        idealIndependentOnly(IRNode.SIGNUM_VD, IdealIndependentDefaultRegexes.SIGNUM_VD);
        idealIndependentOnly(IRNode.SIGNUM_VF, IdealIndependentDefaultRegexes.SIGNUM_VF);
        idealIndependentOnly(IRNode.STATIC_CALL_OF_METHOD, IdealIndependentDefaultRegexes.STATIC_CALL_OF_METHOD);
        idealIndependentOnly(IRNode.STORE, IdealIndependentDefaultRegexes.STORE);
        idealIndependentOnly(IRNode.STORE_B, IdealIndependentDefaultRegexes.STORE_B);
        idealIndependentOnly(IRNode.STORE_B_OF_CLASS, IdealIndependentDefaultRegexes.STORE_B_OF_CLASS);
        idealIndependentOnly(IRNode.STORE_C, IdealIndependentDefaultRegexes.STORE_C);
        idealIndependentOnly(IRNode.STORE_C_OF_CLASS, IdealIndependentDefaultRegexes.STORE_C_OF_CLASS);
        idealIndependentOnly(IRNode.STORE_D, IdealIndependentDefaultRegexes.STORE_D);
        idealIndependentOnly(IRNode.STORE_D_OF_CLASS, IdealIndependentDefaultRegexes.STORE_D_OF_CLASS);
        idealIndependentOnly(IRNode.STORE_F, IdealIndependentDefaultRegexes.STORE_F);
        idealIndependentOnly(IRNode.STORE_F_OF_CLASS, IdealIndependentDefaultRegexes.STORE_F_OF_CLASS);
        idealIndependentOnly(IRNode.STORE_I, IdealIndependentDefaultRegexes.STORE_I);
        idealIndependentOnly(IRNode.STORE_I_OF_CLASS, IdealIndependentDefaultRegexes.STORE_I_OF_CLASS);
        idealIndependentOnly(IRNode.STORE_L, IdealIndependentDefaultRegexes.STORE_L);
        idealIndependentOnly(IRNode.STORE_L_OF_CLASS, IdealIndependentDefaultRegexes.STORE_L_OF_CLASS);
        idealIndependentOnly(IRNode.STORE_N, IdealIndependentDefaultRegexes.STORE_N);
        idealIndependentOnly(IRNode.STORE_N_OF_CLASS, IdealIndependentDefaultRegexes.STORE_N_OF_CLASS);
        idealIndependentOnly(IRNode.STORE_OF_CLASS, IdealIndependentDefaultRegexes.STORE_OF_CLASS);
        idealIndependentOnly(IRNode.STORE_OF_FIELD, IdealIndependentDefaultRegexes.STORE_OF_FIELD);
        idealIndependentOnly(IRNode.STORE_P, IdealIndependentDefaultRegexes.STORE_P);
        idealIndependentOnly(IRNode.STORE_P_OF_CLASS, IdealIndependentDefaultRegexes.STORE_P_OF_CLASS);
        idealIndependentOnly(IRNode.STORE_VECTOR, IdealIndependentDefaultRegexes.STORE_VECTOR);
        idealIndependentOnly(IRNode.SUB, IdealIndependentDefaultRegexes.SUB);
        idealIndependentOnly(IRNode.SUB_D, IdealIndependentDefaultRegexes.SUB_D);
        idealIndependentOnly(IRNode.SUB_F, IdealIndependentDefaultRegexes.SUB_F);
        idealIndependentOnly(IRNode.SUB_I, IdealIndependentDefaultRegexes.SUB_I);
        idealIndependentOnly(IRNode.SUB_L, IdealIndependentDefaultRegexes.SUB_L);
        idealIndependentOnly(IRNode.TRAP, IdealIndependentDefaultRegexes.TRAP);
        idealIndependentOnly(IRNode.UDIV_I, IdealIndependentDefaultRegexes.UDIV_I);
        idealIndependentOnly(IRNode.UDIV_L, IdealIndependentDefaultRegexes.UDIV_L);
        idealIndependentOnly(IRNode.UDIV_MOD_I, IdealIndependentDefaultRegexes.UDIV_MOD_I);
        idealIndependentOnly(IRNode.UDIV_MOD_L, IdealIndependentDefaultRegexes.UDIV_MOD_L);
        idealIndependentOnly(IRNode.UMOD_I, IdealIndependentDefaultRegexes.UMOD_I);
        idealIndependentOnly(IRNode.UMOD_L, IdealIndependentDefaultRegexes.UMOD_L);
        idealIndependentOnly(IRNode.UNHANDLED_TRAP, IdealIndependentDefaultRegexes.UNHANDLED_TRAP);
        idealIndependentOnly(IRNode.UNSTABLE_IF_TRAP, IdealIndependentDefaultRegexes.UNSTABLE_IF_TRAP);
        idealIndependentOnly(IRNode.URSHIFT, IdealIndependentDefaultRegexes.URSHIFT);
        idealIndependentOnly(IRNode.URSHIFT_I, IdealIndependentDefaultRegexes.URSHIFT_I);
        idealIndependentOnly(IRNode.URSHIFT_L, IdealIndependentDefaultRegexes.URSHIFT_L);
        idealIndependentOnly(IRNode.VECTOR_BLEND, IdealIndependentDefaultRegexes.VECTOR_BLEND);
        idealIndependentOnly(IRNode.VECTOR_CAST_B2X, IdealIndependentDefaultRegexes.VECTOR_CAST_B2X);
        idealIndependentOnly(IRNode.VECTOR_CAST_D2X, IdealIndependentDefaultRegexes.VECTOR_CAST_D2X);
        idealIndependentOnly(IRNode.VECTOR_CAST_F2X, IdealIndependentDefaultRegexes.VECTOR_CAST_F2X);
        idealIndependentOnly(IRNode.VECTOR_CAST_I2X, IdealIndependentDefaultRegexes.VECTOR_CAST_I2X);
        idealIndependentOnly(IRNode.VECTOR_CAST_L2X, IdealIndependentDefaultRegexes.VECTOR_CAST_L2X);
        idealIndependentOnly(IRNode.VECTOR_CAST_S2X, IdealIndependentDefaultRegexes.VECTOR_CAST_S2X);
        idealIndependentOnly(IRNode.VECTOR_REINTERPRET, IdealIndependentDefaultRegexes.VECTOR_REINTERPRET);
        idealIndependentOnly(IRNode.VECTOR_UCAST_B2X, IdealIndependentDefaultRegexes.VECTOR_UCAST_B2X);
        idealIndependentOnly(IRNode.VECTOR_UCAST_I2X, IdealIndependentDefaultRegexes.VECTOR_UCAST_I2X);
        idealIndependentOnly(IRNode.VECTOR_UCAST_S2X, IdealIndependentDefaultRegexes.VECTOR_UCAST_S2X);
        idealIndependentOnly(IRNode.XOR_I, IdealIndependentDefaultRegexes.XOR_I);
        idealIndependentOnly(IRNode.XOR_L, IdealIndependentDefaultRegexes.XOR_L);
        idealIndependentOnly(IRNode.XOR_V, IdealIndependentDefaultRegexes.XOR_V);
        idealIndependentOnly(IRNode.XOR_V_MASK, IdealIndependentDefaultRegexes.XOR_V_MASK);
    }

    private void allocNodes(String irNode, String idealRegex, String optoRegex) {
        Map<PhaseInterval, String> intervalToRegexMap = new HashMap<>();
        intervalToRegexMap.put(new PhaseInterval(CompilePhase.BEFORE_REMOVEUSELESS, CompilePhase.PHASEIDEALLOOP_ITERATIONS),
                               idealRegex);
        intervalToRegexMap.put(new PhaseSingletonSet(CompilePhase.PRINT_OPTO_ASSEMBLY), optoRegex);
        MultiPhaseRangeEntry entry = new MultiPhaseRangeEntry(CompilePhase.PRINT_OPTO_ASSEMBLY, intervalToRegexMap);
        irNodeMappings.put(irNode, entry);
    }

    private void idealIndependentOnly(String irNode, String regex) {
        irNodeMappings.put(irNode, new IdealIndependentEntry(CompilePhase.PRINT_IDEAL, regex));
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

    private void idealAfterCountedLoops(String irNode, String regex) {
        irNodeMappings.put(irNode, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                             CompilePhase.AFTER_CLOOPS, CompilePhase.BEFORE_MATCHING));
    }

    private void idealFromMacroExpansion(String irNode, String regex) {
        irNodeMappings.put(irNode, new SinglePhaseRangeEntry(CompilePhase.PRINT_IDEAL, regex,
                                                             CompilePhase.MACRO_EXPANSION, CompilePhase.BEFORE_MATCHING));
    }


    public static String getRegexForPhaseOfIRNode(String irNode, CompilePhase compilePhase) {
        IRNodeMapEntry entry = INSTANCE.irNodeMappings.get(irNode);
        String failMsg = "IR Node \"" + irNode + "\" defined in class IRNode has no mapping in class IRNodeMappings " +
                         "(i.e. an entry in the constructor of class IRNodeMappings)." + System.lineSeparator() +
                         "   Have you just created the entry \"" + irNode + "\" in class IRNode and forgot to add a " +
                         "mapping in class IRNodeMappings?" + System.lineSeparator() +
                         "   Violation";
        TestFormat.checkNoReport(entry != null, failMsg);
        String regex = entry.getRegexForPhase(compilePhase);
        failMsg = "IR Node \"" + irNode + "\" defined in class IRNode has no regex defined for compile phase "
                  + compilePhase + "." + System.lineSeparator() +
                  "   If you think this compile phase should be " +
                  "supported, add a mapping in class IRNodeMappings (i.e an entry in the constructor of class " +
                  "IRNodeMappings)." + System.lineSeparator() +
                  "   Violation";
        TestFormat.checkNoReport(regex != null, failMsg);
        return regex;
    }

    public static CompilePhase getDefaultPhaseForIRNode(String irNode) {
        IRNodeMapEntry entry = INSTANCE.irNodeMappings.get(irNode);
        String failMsg = "\"" + irNode + "\" is not an IR node defined in class IRNode and " +
                         "has therefore no default compile phase specified." + System.lineSeparator() +
                         "   If your regex represents a C2 IR node, consider adding an entry to class IRNode with a " +
                         "mapping in class IRNodeMappings." + System.lineSeparator() +
                         "   Otherwise, set the @IR \"phase\" attribute to a compile phase different from " +
                         "CompilePhase.DEFAULT to explicitly tell the IR framework on which compile phase your rule" +
                         " should be applied on." + System.lineSeparator() +
                         "   Violation";
        TestFormat.checkNoReport(entry != null, failMsg);
        return entry.getDefaultCompilePhase();
    }
}
