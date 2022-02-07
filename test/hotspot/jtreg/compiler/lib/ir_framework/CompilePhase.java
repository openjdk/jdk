package compiler.lib.ir_framework;

import java.util.HashMap;
import java.util.Map;

public enum CompilePhase {
    DEFAULT("print_ideal", 0), // PrintIdeal/PrintOptoAssembly flags, as we know it today

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
    GLOBAL_CODE_MOTION("Global code motion", 2),
    FINAL_CODE("Final Code", 1),
    AFTER_EA("After Escape Analysis", 2),
    BEFORE_CLOOPS("Before CountedLoop", 3),
    AFTER_CLOOPS("After CountedLoop", 3),
    BEFORE_BEAUTIFY_LOOPS("Before beautify loops", 3),
    AFTER_BEAUTIFY_LOOPS("After beautify loops", 3),
    BEFORE_MATCHING("Before matching", 1),
    MATCHING("After matching", 2),
    INCREMENTAL_INLINE("Incremental Inline", 2),
    INCREMENTAL_INLINE_STEP("Incremental Inline Step", 3),
    INCREMENTAL_INLINE_CLEANUP("Incremental Inline Cleanup", 3),
    INCREMENTAL_BOXING_INLINE("Incremental Boxing Inline", 2),
    MACRO_EXPANSION("Macro expand", 2),
    BARRIER_EXPANSION("Barrier expand", 2),
    END("End", 3),

    ALL("All", 3), // Apply for all phases if custom regex or all applicable phases if default regex (some might be unsupported, skip in this case)
    ;

    private static final Map<String, CompilePhase> PHASES_BY_NAME = new HashMap<>();

    static {
        for (CompilePhase phase : CompilePhase.values()) {
            PHASES_BY_NAME.put(phase.name, phase);
        }
    }

    private final String name;
    private final int level;

    CompilePhase(String name, int level) {
        this.name = name;
        this.level = level;
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
