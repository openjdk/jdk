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
    DEFAULT("PrintIdeal and PrintOptoAssembly", OutputType.DEFAULT),
    PRINT_IDEAL("PrintIdeal"),
    PRINT_OPTO_ASSEMBLY("PrintOptoAssembly", OutputType.OPTO_ASSEMBLY),

    // All available phases found in phasetype.hpp with the corresponding levels found throughout the C2 code
    BEFORE_STRINGOPTS("Before StringOpts"),
    AFTER_STRINGOPTS("After StringOpts"),
    BEFORE_REMOVEUSELESS("Before RemoveUseless"),
    AFTER_PARSING("After Parsing"),
    ITER_GVN1("Iter GVN 1"),
    EXPAND_VUNBOX("Expand VectorUnbox"),
    SCALARIZE_VBOX("Scalarize VectorBox"),
    INLINE_VECTOR_REBOX("Inline Vector Rebox Calls"),
    EXPAND_VBOX("Expand VectorBox"),
    ELIMINATE_VBOX_ALLOC("Eliminate VectorBoxAllocate"),
    PHASEIDEAL_BEFORE_EA("PhaseIdealLoop before EA"),
    ITER_GVN_AFTER_VECTOR("Iter GVN after vector box elimination"),
    ITER_GVN_BEFORE_EA("Iter GVN before EA"),
    ITER_GVN_AFTER_EA("Iter GVN after EA"),
    ITER_GVN_AFTER_ELIMINATION("Iter GVN after eliminating allocations and locks"),
    PHASEIDEALLOOP1("PhaseIdealLoop 1"),
    PHASEIDEALLOOP2("PhaseIdealLoop 2"),
    PHASEIDEALLOOP3("PhaseIdealLoop 3"),
    CCP1("PhaseCCP 1"),
    ITER_GVN2("Iter GVN 2"),
    PHASEIDEALLOOP_ITERATIONS("PhaseIdealLoop iterations"),
    OPTIMIZE_FINISHED("Optimize finished"),
    GLOBAL_CODE_MOTION("Global code motion", OutputType.MACH),
    FINAL_CODE("Final Code", OutputType.MACH),
    AFTER_EA("After Escape Analysis"),
    BEFORE_CLOOPS("Before CountedLoop"),
    AFTER_CLOOPS("After CountedLoop"),
    BEFORE_BEAUTIFY_LOOPS("Before beautify loops"),
    AFTER_BEAUTIFY_LOOPS("After beautify loops"),
    BEFORE_MATCHING("Before matching"),
    MATCHING("After matching", OutputType.MACH),
    INCREMENTAL_INLINE("Incremental Inline"),
    INCREMENTAL_INLINE_STEP("Incremental Inline Step"),
    INCREMENTAL_INLINE_CLEANUP("Incremental Inline Cleanup"),
    INCREMENTAL_BOXING_INLINE("Incremental Boxing Inline"),
    MACRO_EXPANSION("Macro expand", OutputType.MACH),
    BARRIER_EXPANSION("Barrier expand", OutputType.MACH),
    END("End"),
    ;

    private static final Map<String, CompilePhase> PHASES_BY_PARSED_NAME = new HashMap<>();
    private static final List<CompilePhase> IDEAL_PHASES;
    private static final List<CompilePhase> MACH_PHASES;

    static {
        for (CompilePhase phase : CompilePhase.values()) {
            if (phase == PRINT_IDEAL) {
                PHASES_BY_PARSED_NAME.put("print_ideal", phase);
            } else {
                PHASES_BY_PARSED_NAME.put(phase.name(), phase);
            }
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

    private enum OutputType {
        IDEAL, MACH, OPTO_ASSEMBLY, DEFAULT
    }

    private final String name;
    private final OutputType outputType;

    CompilePhase(String name) {
        this.name = name;
        this.outputType = OutputType.IDEAL;
    }

    CompilePhase(String name, OutputType outputType) {
        this.name = name;
        this.outputType = outputType;
    }

    public String getName() {
        return name;
    }

    public static CompilePhase forName(String phaseName) {
        CompilePhase phase = PHASES_BY_PARSED_NAME.get(phaseName);
        TestFramework.check(phase != null, "Could not find phase with name \"" + phaseName + "\"");
        return phase;
    }
}
