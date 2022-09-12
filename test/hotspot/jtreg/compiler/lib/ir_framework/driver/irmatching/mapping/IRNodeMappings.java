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
        idealindependentOnly(IRNode.ABS_D, IdealIndependentDefaultRegexes.ABS_D);
        idealindependentOnly(IRNode.ABS_F, IdealIndependentDefaultRegexes.ABS_F);
        idealindependentOnly(IRNode.ABS_I, IdealIndependentDefaultRegexes.ABS_I);
        idealindependentOnly(IRNode.ABS_L, IdealIndependentDefaultRegexes.ABS_L);
        idealindependentOnly(IRNode.ADD, IdealIndependentDefaultRegexes.ADD);
        idealindependentOnly(IRNode.ADD_I, IdealIndependentDefaultRegexes.ADD_I);
        idealindependentOnly(IRNode.ADD_L, IdealIndependentDefaultRegexes.ADD_L);
        idealindependentOnly(IRNode.ADD_VD, IdealIndependentDefaultRegexes.ADD_VD);
        idealindependentOnly(IRNode.ADD_VI, IdealIndependentDefaultRegexes.ADD_VI);
        allocNodes(IRNode.ALLOC, IdealIndependentDefaultRegexes.ALLOC, OptoAssemblyDefaultRegexes.ALLOC);
        optoOnly(IRNode.ALLOC_OF, OptoAssemblyDefaultRegexes.ALLOC_OF);
        allocNodes(IRNode.ALLOC_ARRAY, IdealIndependentDefaultRegexes.ALLOC_ARRAY, OptoAssemblyDefaultRegexes.ALLOC_ARRAY);
        optoOnly(IRNode.ALLOC_ARRAY_OF, OptoAssemblyDefaultRegexes.ALLOC_ARRAY_OF);
        idealindependentOnly(IRNode.AND, IdealIndependentDefaultRegexes.AND);
        idealindependentOnly(IRNode.AND_I, IdealIndependentDefaultRegexes.AND_I);
        idealindependentOnly(IRNode.AND_L, IdealIndependentDefaultRegexes.AND_L);
        idealindependentOnly(IRNode.AND_V, IdealIndependentDefaultRegexes.AND_V);
        idealindependentOnly(IRNode.AND_V_MASK, IdealIndependentDefaultRegexes.AND_V_MASK);
        idealindependentOnly(IRNode.CALL, IdealIndependentDefaultRegexes.CALL);
        idealindependentOnly(IRNode.CALL_OF_METHOD, IdealIndependentDefaultRegexes.CALL_OF_METHOD);
        idealindependentOnly(IRNode.CAST_II, IdealIndependentDefaultRegexes.CAST_II);
        idealindependentOnly(IRNode.CAST_LL, IdealIndependentDefaultRegexes.CAST_LL);
        optoOnly(IRNode.CHECKCAST_ARRAY, OptoAssemblyDefaultRegexes.CHECKCAST_ARRAY);
        optoOnly(IRNode.CHECKCAST_ARRAYCOPY, OptoAssemblyDefaultRegexes.CHECKCAST_ARRAYCOPY);
        optoOnly(IRNode.CHECKCAST_ARRAY_OF, OptoAssemblyDefaultRegexes.CHECKCAST_ARRAY_OF);
        idealindependentOnly(IRNode.CLASS_CHECK_TRAP, IdealIndependentDefaultRegexes.CLASS_CHECK_TRAP);
        idealindependentOnly(IRNode.CMOVE_I, IdealIndependentDefaultRegexes.CMOVE_I);
        idealindependentOnly(IRNode.CMP_I, IdealIndependentDefaultRegexes.CMP_I);
        idealindependentOnly(IRNode.CMP_L, IdealIndependentDefaultRegexes.CMP_L);
        idealindependentOnly(IRNode.CMP_U, IdealIndependentDefaultRegexes.CMP_U);
        idealindependentOnly(IRNode.CMP_U3, IdealIndependentDefaultRegexes.CMP_U3);
        idealindependentOnly(IRNode.CMP_UL, IdealIndependentDefaultRegexes.CMP_UL);
        idealindependentOnly(IRNode.CMP_UL3, IdealIndependentDefaultRegexes.CMP_UL3);
        idealindependentOnly(IRNode.COMPRESS_BITS, IdealIndependentDefaultRegexes.COMPRESS_BITS);
        idealindependentOnly(IRNode.CONV_I2L, IdealIndependentDefaultRegexes.CONV_I2L);
        idealindependentOnly(IRNode.CONV_L2I, IdealIndependentDefaultRegexes.CONV_L2I);
        idealindependentOnly(IRNode.CON_I, IdealIndependentDefaultRegexes.CON_I);
        idealindependentOnly(IRNode.CON_L, IdealIndependentDefaultRegexes.CON_L);
        fromAfterCountedLoops(IRNode.COUNTED_LOOP, IdealIndependentDefaultRegexes.COUNTED_LOOP);
        fromAfterCountedLoops(IRNode.COUNTED_LOOP_MAIN, IdealIndependentDefaultRegexes.COUNTED_LOOP_MAIN);
        idealindependentOnly(IRNode.DIV, IdealIndependentDefaultRegexes.DIV);
        idealindependentOnly(IRNode.DIV_BY_ZERO_TRAP, IdealIndependentDefaultRegexes.DIV_BY_ZERO_TRAP);
        idealindependentOnly(IRNode.DIV_L, IdealIndependentDefaultRegexes.DIV_L);
        idealindependentOnly(IRNode.DYNAMIC_CALL_OF_METHOD, IdealIndependentDefaultRegexes.DYNAMIC_CALL_OF_METHOD);
        idealindependentOnly(IRNode.EXPAND_BITS, IdealIndependentDefaultRegexes.EXPAND_BITS);
        idealindependentOnly(IRNode.FAST_LOCK, IdealIndependentDefaultRegexes.FAST_LOCK);
        idealFromMacroExpansion(IRNode.FAST_UNLOCK, IdealIndependentDefaultRegexes.FAST_UNLOCK);
        optoOnly(IRNode.FIELD_ACCESS, OptoAssemblyDefaultRegexes.FIELD_ACCESS);
        idealindependentOnly(IRNode.IF, IdealIndependentDefaultRegexes.IF);
        idealindependentOnly(IRNode.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP, IdealIndependentDefaultRegexes.INTRINSIC_OR_TYPE_CHECKED_INLINING_TRAP);
        idealindependentOnly(IRNode.INTRINSIC_TRAP, IdealIndependentDefaultRegexes.INTRINSIC_TRAP);
        idealindependentOnly(IRNode.LOAD, IdealIndependentDefaultRegexes.LOAD);
        idealindependentOnly(IRNode.LOAD_B, IdealIndependentDefaultRegexes.LOAD_B);
        idealindependentOnly(IRNode.LOAD_B_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_B_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_D, IdealIndependentDefaultRegexes.LOAD_D);
        idealindependentOnly(IRNode.LOAD_D_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_D_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_F, IdealIndependentDefaultRegexes.LOAD_F);
        idealindependentOnly(IRNode.LOAD_F_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_F_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_I, IdealIndependentDefaultRegexes.LOAD_I);
        idealindependentOnly(IRNode.LOAD_I_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_I_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_KLASS, IdealIndependentDefaultRegexes.LOAD_KLASS);
        idealindependentOnly(IRNode.LOAD_L, IdealIndependentDefaultRegexes.LOAD_L);
        idealindependentOnly(IRNode.LOAD_L_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_L_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_N, IdealIndependentDefaultRegexes.LOAD_N);
        idealindependentOnly(IRNode.LOAD_N_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_N_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_OF_FIELD, IdealIndependentDefaultRegexes.LOAD_OF_FIELD);
        idealindependentOnly(IRNode.LOAD_P, IdealIndependentDefaultRegexes.LOAD_P);
        idealindependentOnly(IRNode.LOAD_P_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_P_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_S, IdealIndependentDefaultRegexes.LOAD_S);
        idealindependentOnly(IRNode.LOAD_S_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_S_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_UB, IdealIndependentDefaultRegexes.LOAD_UB);
        idealindependentOnly(IRNode.LOAD_UB_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_UB_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_US, IdealIndependentDefaultRegexes.LOAD_US);
        idealindependentOnly(IRNode.LOAD_US_OF_CLASS, IdealIndependentDefaultRegexes.LOAD_US_OF_CLASS);
        idealindependentOnly(IRNode.LOAD_VECTOR, IdealIndependentDefaultRegexes.LOAD_VECTOR);
        fromAfterCountedLoops(IRNode.LONG_COUNTED_LOOP, IdealIndependentDefaultRegexes.LONG_COUNTED_LOOP);
        fromBeforeCountedLoops(IRNode.LOOP, IdealIndependentDefaultRegexes.LOOP);
        idealindependentOnly(IRNode.LSHIFT, IdealIndependentDefaultRegexes.LSHIFT);
        idealindependentOnly(IRNode.LSHIFT_I, IdealIndependentDefaultRegexes.LSHIFT_I);
        idealindependentOnly(IRNode.LSHIFT_L, IdealIndependentDefaultRegexes.LSHIFT_L);
        idealindependentOnly(IRNode.MAX_I, IdealIndependentDefaultRegexes.MAX_I);
        idealindependentOnly(IRNode.MAX_V, IdealIndependentDefaultRegexes.MAX_V);
        idealindependentOnly(IRNode.MEMBAR, IdealIndependentDefaultRegexes.MEMBAR);
        idealindependentOnly(IRNode.MEMBAR_STORESTORE, IdealIndependentDefaultRegexes.MEMBAR_STORESTORE);
        idealindependentOnly(IRNode.MIN_I, IdealIndependentDefaultRegexes.MIN_I);
        idealindependentOnly(IRNode.MIN_V, IdealIndependentDefaultRegexes.MIN_V);
        idealindependentOnly(IRNode.MUL, IdealIndependentDefaultRegexes.MUL);
        idealindependentOnly(IRNode.MUL_F, IdealIndependentDefaultRegexes.MUL_F);
        idealindependentOnly(IRNode.MUL_I, IdealIndependentDefaultRegexes.MUL_I);
        idealindependentOnly(IRNode.MUL_L, IdealIndependentDefaultRegexes.MUL_L);
        idealindependentOnly(IRNode.NULL_ASSERT_TRAP, IdealIndependentDefaultRegexes.NULL_ASSERT_TRAP);
        idealindependentOnly(IRNode.NULL_CHECK_TRAP, IdealIndependentDefaultRegexes.NULL_CHECK_TRAP);
        idealindependentOnly(IRNode.OR_V, IdealIndependentDefaultRegexes.OR_V);
        idealindependentOnly(IRNode.OR_V_MASK, IdealIndependentDefaultRegexes.OR_V_MASK);
        fromAfterCountedLoops(IRNode.OUTER_STRIP_MINED_LOOP, IdealIndependentDefaultRegexes.OUTER_STRIP_MINED_LOOP);
        idealindependentOnly(IRNode.PHI, IdealIndependentDefaultRegexes.PHI);
        idealindependentOnly(IRNode.POPCOUNT_L, IdealIndependentDefaultRegexes.POPCOUNT_L);
        idealAfterCountedLoops(IRNode.POPULATE_INDEX, IdealIndependentDefaultRegexes.POPULATE_INDEX);
        idealindependentOnly(IRNode.PREDICATE_TRAP, IdealIndependentDefaultRegexes.PREDICATE_TRAP);
        idealindependentOnly(IRNode.RANGE_CHECK_TRAP, IdealIndependentDefaultRegexes.RANGE_CHECK_TRAP);
        idealindependentOnly(IRNode.REVERSE_BYTES_V, IdealIndependentDefaultRegexes.REVERSE_BYTES_V);
        idealindependentOnly(IRNode.REVERSE_I, IdealIndependentDefaultRegexes.REVERSE_I);
        idealindependentOnly(IRNode.REVERSE_L, IdealIndependentDefaultRegexes.REVERSE_L);
        idealindependentOnly(IRNode.REVERSE_V, IdealIndependentDefaultRegexes.REVERSE_V);
        idealindependentOnly(IRNode.ROUND_VD, IdealIndependentDefaultRegexes.ROUND_VD);
        idealindependentOnly(IRNode.ROUND_VF, IdealIndependentDefaultRegexes.ROUND_VF);
        idealindependentOnly(IRNode.RSHIFT, IdealIndependentDefaultRegexes.RSHIFT);
        idealindependentOnly(IRNode.RSHIFT_I, IdealIndependentDefaultRegexes.RSHIFT_I);
        idealindependentOnly(IRNode.RSHIFT_L, IdealIndependentDefaultRegexes.RSHIFT_L);
        idealindependentOnly(IRNode.RSHIFT_VB, IdealIndependentDefaultRegexes.RSHIFT_VB);
        idealindependentOnly(IRNode.RSHIFT_VS, IdealIndependentDefaultRegexes.RSHIFT_VS);
        idealindependentOnly(IRNode.SAFEPOINT, IdealIndependentDefaultRegexes.SAFEPOINT);
        optoOnly(IRNode.SCOPE_OBJECT, OptoAssemblyDefaultRegexes.SCOPE_OBJECT);
        idealindependentOnly(IRNode.SIGNUM_VD, IdealIndependentDefaultRegexes.SIGNUM_VD);
        idealindependentOnly(IRNode.SIGNUM_VF, IdealIndependentDefaultRegexes.SIGNUM_VF);
        idealindependentOnly(IRNode.STATIC_CALL_OF_METHOD, IdealIndependentDefaultRegexes.STATIC_CALL_OF_METHOD);
        idealindependentOnly(IRNode.STORE, IdealIndependentDefaultRegexes.STORE);
        idealindependentOnly(IRNode.STORE_B, IdealIndependentDefaultRegexes.STORE_B);
        idealindependentOnly(IRNode.STORE_B_OF_CLASS, IdealIndependentDefaultRegexes.STORE_B_OF_CLASS);
        idealindependentOnly(IRNode.STORE_C, IdealIndependentDefaultRegexes.STORE_C);
        idealindependentOnly(IRNode.STORE_C_OF_CLASS, IdealIndependentDefaultRegexes.STORE_C_OF_CLASS);
        idealindependentOnly(IRNode.STORE_D, IdealIndependentDefaultRegexes.STORE_D);
        idealindependentOnly(IRNode.STORE_D_OF_CLASS, IdealIndependentDefaultRegexes.STORE_D_OF_CLASS);
        idealindependentOnly(IRNode.STORE_F, IdealIndependentDefaultRegexes.STORE_F);
        idealindependentOnly(IRNode.STORE_F_OF_CLASS, IdealIndependentDefaultRegexes.STORE_F_OF_CLASS);
        idealindependentOnly(IRNode.STORE_I, IdealIndependentDefaultRegexes.STORE_I);
        idealindependentOnly(IRNode.STORE_I_OF_CLASS, IdealIndependentDefaultRegexes.STORE_I_OF_CLASS);
        idealindependentOnly(IRNode.STORE_L, IdealIndependentDefaultRegexes.STORE_L);
        idealindependentOnly(IRNode.STORE_L_OF_CLASS, IdealIndependentDefaultRegexes.STORE_L_OF_CLASS);
        idealindependentOnly(IRNode.STORE_N, IdealIndependentDefaultRegexes.STORE_N);
        idealindependentOnly(IRNode.STORE_N_OF_CLASS, IdealIndependentDefaultRegexes.STORE_N_OF_CLASS);
        idealindependentOnly(IRNode.STORE_OF_CLASS, IdealIndependentDefaultRegexes.STORE_OF_CLASS);
        idealindependentOnly(IRNode.STORE_OF_FIELD, IdealIndependentDefaultRegexes.STORE_OF_FIELD);
        idealindependentOnly(IRNode.STORE_P, IdealIndependentDefaultRegexes.STORE_P);
        idealindependentOnly(IRNode.STORE_P_OF_CLASS, IdealIndependentDefaultRegexes.STORE_P_OF_CLASS);
        idealindependentOnly(IRNode.STORE_VECTOR, IdealIndependentDefaultRegexes.STORE_VECTOR);
        idealindependentOnly(IRNode.SUB, IdealIndependentDefaultRegexes.SUB);
        idealindependentOnly(IRNode.SUB_D, IdealIndependentDefaultRegexes.SUB_D);
        idealindependentOnly(IRNode.SUB_F, IdealIndependentDefaultRegexes.SUB_F);
        idealindependentOnly(IRNode.SUB_I, IdealIndependentDefaultRegexes.SUB_I);
        idealindependentOnly(IRNode.SUB_L, IdealIndependentDefaultRegexes.SUB_L);
        idealindependentOnly(IRNode.TRAP, IdealIndependentDefaultRegexes.TRAP);
        idealindependentOnly(IRNode.UDIV_I, IdealIndependentDefaultRegexes.UDIV_I);
        idealindependentOnly(IRNode.UDIV_L, IdealIndependentDefaultRegexes.UDIV_L);
        idealindependentOnly(IRNode.UDIV_MOD_I, IdealIndependentDefaultRegexes.UDIV_MOD_I);
        idealindependentOnly(IRNode.UDIV_MOD_L, IdealIndependentDefaultRegexes.UDIV_MOD_L);
        idealindependentOnly(IRNode.UMOD_I, IdealIndependentDefaultRegexes.UMOD_I);
        idealindependentOnly(IRNode.UMOD_L, IdealIndependentDefaultRegexes.UMOD_L);
        idealindependentOnly(IRNode.UNHANDLED_TRAP, IdealIndependentDefaultRegexes.UNHANDLED_TRAP);
        idealindependentOnly(IRNode.UNSTABLE_IF_TRAP, IdealIndependentDefaultRegexes.UNSTABLE_IF_TRAP);
        idealindependentOnly(IRNode.URSHIFT, IdealIndependentDefaultRegexes.URSHIFT);
        idealindependentOnly(IRNode.URSHIFT_I, IdealIndependentDefaultRegexes.URSHIFT_I);
        idealindependentOnly(IRNode.URSHIFT_L, IdealIndependentDefaultRegexes.URSHIFT_L);
        idealindependentOnly(IRNode.VECTOR_BLEND, IdealIndependentDefaultRegexes.VECTOR_BLEND);
        idealindependentOnly(IRNode.VECTOR_CAST_B2X, IdealIndependentDefaultRegexes.VECTOR_CAST_B2X);
        idealindependentOnly(IRNode.VECTOR_CAST_D2X, IdealIndependentDefaultRegexes.VECTOR_CAST_D2X);
        idealindependentOnly(IRNode.VECTOR_CAST_F2X, IdealIndependentDefaultRegexes.VECTOR_CAST_F2X);
        idealindependentOnly(IRNode.VECTOR_CAST_I2X, IdealIndependentDefaultRegexes.VECTOR_CAST_I2X);
        idealindependentOnly(IRNode.VECTOR_CAST_L2X, IdealIndependentDefaultRegexes.VECTOR_CAST_L2X);
        idealindependentOnly(IRNode.VECTOR_CAST_S2X, IdealIndependentDefaultRegexes.VECTOR_CAST_S2X);
        idealindependentOnly(IRNode.VECTOR_REINTERPRET, IdealIndependentDefaultRegexes.VECTOR_REINTERPRET);
        idealindependentOnly(IRNode.VECTOR_UCAST_B2X, IdealIndependentDefaultRegexes.VECTOR_UCAST_B2X);
        idealindependentOnly(IRNode.VECTOR_UCAST_I2X, IdealIndependentDefaultRegexes.VECTOR_UCAST_I2X);
        idealindependentOnly(IRNode.VECTOR_UCAST_S2X, IdealIndependentDefaultRegexes.VECTOR_UCAST_S2X);
        idealindependentOnly(IRNode.XOR_I, IdealIndependentDefaultRegexes.XOR_I);
        idealindependentOnly(IRNode.XOR_L, IdealIndependentDefaultRegexes.XOR_L);
        idealindependentOnly(IRNode.XOR_V, IdealIndependentDefaultRegexes.XOR_V);
        idealindependentOnly(IRNode.XOR_V_MASK, IdealIndependentDefaultRegexes.XOR_V_MASK);
    }

    private void allocNodes(String irNode, String idealRegex, String optoRegex) {
        Map<PhaseInterval, String> intervalToRegexMap = new HashMap<>();
        intervalToRegexMap.put(new PhaseInterval(CompilePhase.BEFORE_REMOVEUSELESS, CompilePhase.PHASEIDEALLOOP_ITERATIONS),
                               idealRegex);
        intervalToRegexMap.put(new PhaseSingletonSet(CompilePhase.PRINT_OPTO_ASSEMBLY), optoRegex);
        MultiPhaseRangeEntry entry = new MultiPhaseRangeEntry(CompilePhase.PRINT_OPTO_ASSEMBLY, intervalToRegexMap);
        irNodeMappings.put(irNode, entry);
    }

    private void idealindependentOnly(String irNode, String regex) {
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
