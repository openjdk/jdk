/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

/**
 * This enum represents all available compile phases on which an IR matching can be done. There is a 1:1 mapping
 * between IGV phases as specified in phasetype.hpp.  Compile phases which are normally not emitted by C2 like FAILURE
 * or DEBUG are not listed. This enum should be kept in sync with phasetype.hpp.
 *
 * <p>
 * There are two additional compile phases PRINT_IDEAL and PRINT_OPTO_ASSEMBLY. PRINT_IDEAL is the output that is printed
 * when using -XX:+PrintIdeal and PRINT_OPTO_ASSEMBLY when using -XX:+PrintOptoAssembly. For simplicity, these two flags
 * are treated as a separated compile phase as well.
 */
public enum CompilePhase {
    DEFAULT("For IR node placeholder strings as defined in class IRNode only"),

    BEFORE_STRINGOPTS("Before StringOpts"),
    AFTER_STRINGOPTS("After StringOpts"),
    BEFORE_REMOVEUSELESS("Before RemoveUseless"),
    AFTER_PARSING("After Parsing"),
    BEFORE_ITER_GVN("Before Iter GVN"),
    ITER_GVN1("Iter GVN 1"),
    AFTER_ITER_GVN_STEP("After Iter GVN Step"),
    AFTER_ITER_GVN("After Iter GVN"),
    INCREMENTAL_INLINE_STEP("Incremental Inline Step"),
    INCREMENTAL_INLINE_CLEANUP("Incremental Inline Cleanup"),
    INCREMENTAL_INLINE("Incremental Inline"),
    INCREMENTAL_BOXING_INLINE("Incremental Boxing Inline"),
    EXPAND_VUNBOX("Expand VectorUnbox"),
    SCALARIZE_VBOX("Scalarize VectorBox"),
    INLINE_VECTOR_REBOX("Inline Vector Rebox Calls"),
    EXPAND_VBOX("Expand VectorBox"),
    ELIMINATE_VBOX_ALLOC("Eliminate VectorBoxAllocate"),
    ITER_GVN_BEFORE_EA("Iter GVN before EA"),
    ITER_GVN_AFTER_VECTOR("Iter GVN after vector box elimination"),
    BEFORE_BEAUTIFY_LOOPS("Before beautify loops"),
    AFTER_BEAUTIFY_LOOPS("After beautify loops"),
    BEFORE_LOOP_UNROLLING("Before Loop Unrolling"),
    AFTER_LOOP_UNROLLING("After Loop Unrolling"),
    BEFORE_SPLIT_IF("Before Split-If"),
    AFTER_SPLIT_IF("After Split-If"),
    BEFORE_LOOP_PREDICATION_IC("Before Loop Predication IC"),
    AFTER_LOOP_PREDICATION_IC("After Loop Predication IC"),
    BEFORE_LOOP_PREDICATION_RC("Before Loop Predication RC"),
    AFTER_LOOP_PREDICATION_RC("After Loop Predication RC"),
    BEFORE_PARTIAL_PEELING("Before Partial Peeling"),
    AFTER_PARTIAL_PEELING("After Partial Peeling"),
    BEFORE_LOOP_PEELING("Before Loop Peeling"),
    AFTER_LOOP_PEELING("After Loop Peeling"),
    BEFORE_LOOP_UNSWITCHING("Before Loop Unswitching"),
    AFTER_LOOP_UNSWITCHING("After Loop Unswitching"),
    BEFORE_RANGE_CHECK_ELIMINATION("Before Range Check Elimination"),
    AFTER_RANGE_CHECK_ELIMINATION("After Range Check Elimination"),
    BEFORE_PRE_MAIN_POST("Before Pre/Main/Post Loops"),
    AFTER_PRE_MAIN_POST("After Pre/Main/Post Loops"),
    SUPERWORD1_BEFORE_SCHEDULE("Superword 1, Before Schedule"),
    SUPERWORD2_BEFORE_OUTPUT("Superword 2, Before Output"),
    SUPERWORD3_AFTER_OUTPUT("Superword 3, After Output"),
    // Match on very first BEFORE_CLOOPS phase (there could be multiple phases for multiple loops in the code).
    BEFORE_CLOOPS("Before CountedLoop", RegexType.IDEAL_INDEPENDENT, ActionOnRepeat.KEEP_FIRST),
    AFTER_CLOOPS("After CountedLoop"),
    PHASEIDEAL_BEFORE_EA("PhaseIdealLoop before EA"),
    AFTER_EA("After Escape Analysis"),
    ITER_GVN_AFTER_EA("Iter GVN after EA"),
    ITER_GVN_AFTER_ELIMINATION("Iter GVN after eliminating allocations and locks"),
    PHASEIDEALLOOP1("PhaseIdealLoop 1"),
    PHASEIDEALLOOP2("PhaseIdealLoop 2"),
    PHASEIDEALLOOP3("PhaseIdealLoop 3"),
    BEFORE_CCP1("Before PhaseCCP 1"),
    CCP1("PhaseCCP 1"),
    ITER_GVN2("Iter GVN 2"),
    PHASEIDEALLOOP_ITERATIONS("PhaseIdealLoop iterations"),
    BEFORE_MACRO_EXPANSION("Before Macro Expansion"),
    AFTER_MACRO_EXPANSION_STEP("After Macro Expansion Step"),
    AFTER_MACRO_EXPANSION("After Macro Expansion"),
    BARRIER_EXPANSION("Barrier expand"),
    OPTIMIZE_FINISHED("Optimize finished"),
    PRINT_IDEAL("PrintIdeal"),
    BEFORE_MATCHING("Before matching"),
    MATCHING("After matching", RegexType.MACH),
    GLOBAL_CODE_MOTION("Global code motion", RegexType.MACH),
    REGISTER_ALLOCATION("Register Allocation", RegexType.MACH),
    BLOCK_ORDERING("Block Ordering", RegexType.MACH),
    PEEPHOLE("Peephole", RegexType.MACH),
    POSTALLOC_EXPAND("Post-Allocation Expand", RegexType.MACH),
    MACH_ANALYSIS("After mach analysis", RegexType.MACH),
    FINAL_CODE("Final Code", RegexType.MACH),
    END("End"),

    PRINT_OPTO_ASSEMBLY("PrintOptoAssembly", RegexType.OPTO_ASSEMBLY),
    ;

    private static final Map<String, CompilePhase> PHASES_BY_PARSED_NAME = new HashMap<>();

    static {
        for (CompilePhase phase : CompilePhase.values()) {
            if (phase == PRINT_IDEAL) {
                PHASES_BY_PARSED_NAME.put("print_ideal", phase);
            } else {
                PHASES_BY_PARSED_NAME.put(phase.name(), phase);
            }
        }
    }
    private enum ActionOnRepeat {
        KEEP_FIRST, KEEP_LAST
    }

    private final String name;
    private final RegexType regexType;
    private final ActionOnRepeat actionOnRepeat;

    CompilePhase(String name) {
        this.name = name;
        this.regexType = RegexType.IDEAL_INDEPENDENT;
        this.actionOnRepeat = ActionOnRepeat.KEEP_LAST;
    }

    CompilePhase(String name, RegexType regexType) {
        this.name = name;
        this.regexType = regexType;
        this.actionOnRepeat = ActionOnRepeat.KEEP_LAST;
    }

    CompilePhase(String name, RegexType regexType, ActionOnRepeat actionOnRepeat) {
        this.name = name;
        this.regexType = regexType;
        this.actionOnRepeat = actionOnRepeat;
    }

    public String getName() {
        return name;
    }

    public RegexType regexType() {
        return regexType;
    }

    public static CompilePhase forName(String phaseName) {
        CompilePhase phase = PHASES_BY_PARSED_NAME.get(phaseName);
        TestFramework.check(phase != null, "Could not find phase with name \"" + phaseName + "\"");
        return phase;
    }

    public boolean overrideRepeatedPhase() {
        return actionOnRepeat == ActionOnRepeat.KEEP_LAST;
    }
}

