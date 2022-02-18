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

package compiler.lib.ir_framework;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public enum CompilePhase {
    DEFAULT("PrintIdeal and PrintOptoAssembly", 0, OutputType.DEFAULT),
    PRINT_IDEAL("print_ideal", 0), // TODO: change to PrintIdeal
    PRINT_OPTO_ASSEMBLY("PrintOptoAssembly", 0, OutputType.OPTO_ASSEMBLY),

    // All available phases found in phasetype.hpp with the corresponding levels found throughout the C2 code
    BEFORE_STRINGOPTS("Before StringOpts", 3),
    AFTER_STRINGOPTS("After StringOpts", 3),
    BEFORE_REMOVEUSELESS("Before RemoveUseless", 3),
    AFTER_PARSING("After Parsing", 1),
    ITER_GVN1("Iter GVN 1", 2),
    EXPAND_VUNBOX("Expand VectorUnbox", 3),
    SCALARIZE_VBOX("Scalarize VectorBox", 3),
    INLINE_VECTOR_REBOX("Inline Vector Rebox Calls", 3),
    EXPAND_VBOX("Expand VectorBox", 3),
    ELIMINATE_VBOX_ALLOC("Eliminate VectorBoxAllocate", 3),
    PHASEIDEAL_BEFORE_EA("PhaseIdealLoop before EA", 2),
    ITER_GVN_AFTER_VECTOR("Iter GVN after vector box elimination", 3),
    ITER_GVN_BEFORE_EA("Iter GVN before EA", 3),
    ITER_GVN_AFTER_EA("Iter GVN after EA", 2),
    ITER_GVN_AFTER_ELIMINATION("Iter GVN after eliminating allocations and locks", 2),
    PHASEIDEALLOOP1("PhaseIdealLoop 1", 2),
    PHASEIDEALLOOP2("PhaseIdealLoop 2", 2),
    PHASEIDEALLOOP3("PhaseIdealLoop 3", 2),
    CCP1("PhaseCCP 1", 2),
    ITER_GVN2("Iter GVN 2", 2),
    PHASEIDEALLOOP_ITERATIONS("PhaseIdealLoop iterations", 2),
    OPTIMIZE_FINISHED("Optimize finished", 2),
    GLOBAL_CODE_MOTION("Global code motion", 2, OutputType.MACH),
    FINAL_CODE("Final Code", 1, OutputType.MACH),
    AFTER_EA("After Escape Analysis", 2),
    BEFORE_CLOOPS("Before CountedLoop", 3),
    AFTER_CLOOPS("After CountedLoop", 3),
    BEFORE_BEAUTIFY_LOOPS("Before beautify loops", 3),
    AFTER_BEAUTIFY_LOOPS("After beautify loops", 3),
    BEFORE_MATCHING("Before matching", 1),
    MATCHING("After matching", 2, OutputType.MACH),
    INCREMENTAL_INLINE("Incremental Inline", 2),
    INCREMENTAL_INLINE_STEP("Incremental Inline Step", 3),
    INCREMENTAL_INLINE_CLEANUP("Incremental Inline Cleanup", 3),
    INCREMENTAL_BOXING_INLINE("Incremental Boxing Inline", 2),
    MACRO_EXPANSION("Macro expand", 2, OutputType.MACH),
    BARRIER_EXPANSION("Barrier expand", 2, OutputType.MACH),
    END("End", 3),

//    ALL("All", 3), // Apply for all phases if custom regex or all applicable phases if default regex (some might be unsupported, skip in this case) TODO
    ;

    private static final Map<String, CompilePhase> PHASES_BY_NAME = new HashMap<>();
    private static final List<CompilePhase> IDEAL_PHASES;
    private static final List<CompilePhase> MACH_PHASES;

    static {
        for (CompilePhase phase : CompilePhase.values()) {
            PHASES_BY_NAME.put(phase.name, phase);
        }
        IDEAL_PHASES = initIdealPhases();
        MACH_PHASES = initMachPhases();
    }

    private static List<CompilePhase> initIdealPhases() {
        return Arrays.stream(CompilePhase.values())
                     .filter(phase -> phase.outputType == OutputType.IDEAL)
                     .collect(Collectors.toList());
    }

    private static List<CompilePhase> initMachPhases() {
        return Arrays.stream(CompilePhase.values())
                     .filter(phase -> phase.outputType == OutputType.MACH)
                     .collect(Collectors.toList());
    }

    public static List<CompilePhase> getIdealPhases() {
        return IDEAL_PHASES;
    }

    public static List<CompilePhase> getMachPhases() {
        return MACH_PHASES;
    }

    public static boolean isDefaultPhase(CompilePhase compilePhase) {
        return compilePhase == PRINT_IDEAL || compilePhase == PRINT_OPTO_ASSEMBLY;
    }

    private enum OutputType {
        IDEAL, MACH, OPTO_ASSEMBLY, DEFAULT;
    }

    private final String name;
    private final int level;
    private final OutputType outputType;

    CompilePhase(String name, int level) {
        this.name = name;
        this.level = level;
        this.outputType = OutputType.IDEAL;
    }

    CompilePhase(String name, int level, OutputType outputType) {
        this.name = name;
        this.level = level;
        this.outputType = outputType;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public static CompilePhase forName(String phaseName) {
        CompilePhase phase = PHASES_BY_NAME.get(phaseName);
        TestFramework.check(phase != null, "Could not find phase with name \"" + phaseName + "\"");
        return phase;
    }
}
