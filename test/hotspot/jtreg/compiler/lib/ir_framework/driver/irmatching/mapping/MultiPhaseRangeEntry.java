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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class represents a mapping entry for an IR node that maps to different regexes depending on the compile phase.
 * This is done by providing a specific {@link PhaseInterval} (i.e. a continuous compile phase range) for each regex.
 * If there is only one {@link PhaseInterval}, a {@link SinglePhaseRangeEntry} should be used.
 *
 * @see PhaseInterval
 * @see SinglePhaseRangeEntry
 */
public class MultiPhaseRangeEntry implements IRNodeMapEntry {
    private final Map<PhaseInterval, String> intervalToRegex;
    private final CompilePhase defaultCompilePhase;

    public MultiPhaseRangeEntry(CompilePhase defaultCompilePhase, Map<PhaseInterval, String> intervalToRegex) {
        checkOverlap(new ArrayList<>(intervalToRegex.keySet()));
        this.intervalToRegex = intervalToRegex;
        this.defaultCompilePhase = defaultCompilePhase;
    }

    /**
     * Checks that there is no compile phase overlap of any {@link PhaseInterval} objects in {@code phaseRanges}.
     */
    private static void checkOverlap(List<PhaseInterval> phaseRanges) {
        // Sort ascending by start field of phase range.
        phaseRanges.sort((i1, i2) -> i1.start().ordinal() - i2.end().ordinal());
        for (int i = 1; i < phaseRanges.size(); i++) {
            if (phaseRanges.get(i - 1).startIndex() > phaseRanges.get(i).endIndex()) {
                // Previous phase range ends after start of current phase range -> overlap
                throw new OverlappingPhaseRangesException(phaseRanges.get(i - 1), phaseRanges.get(i));
            }
        }
    }

    @Override
    public String regexForCompilePhase(CompilePhase compilePhase) {
        for (var entry : intervalToRegex.entrySet()) {
            PhaseInterval interval = entry.getKey();
            if (interval.includes(compilePhase)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public CompilePhase defaultCompilePhase() {
        return defaultCompilePhase;
    }
}
