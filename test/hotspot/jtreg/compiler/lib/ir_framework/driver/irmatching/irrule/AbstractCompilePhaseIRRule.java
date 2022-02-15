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

package compiler.lib.ir_framework.driver.irmatching.irrule;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.driver.irmatching.CompilePhaseMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;

abstract public class AbstractCompilePhaseIRRule {
    private final IRMethod irMethod;
    protected final CompilePhase compilePhase;

    public AbstractCompilePhaseIRRule(IRMethod irMethod, CompilePhase compilePhase) {
        this.irMethod = irMethod;
        this.compilePhase = compilePhase;
    }

    /**
     * Apply this IR rule by checking any failOn and counts attributes.
     */
    abstract CompilePhaseMatchResult applyCheckAttributes();

    protected NormalPhaseMatchResult applyCheckAttributes(FailOn failOn, Counts counts, CompilePhase compilePhase) {
        String compilationOutput = irMethod.getOutput(compilePhase);
        NormalPhaseMatchResult normalPhaseMatchResult = new NormalPhaseMatchResult(compilePhase, compilationOutput);
        applyFailOn(normalPhaseMatchResult, failOn, compilationOutput);
        applyCounts(normalPhaseMatchResult, counts, compilationOutput);
        return normalPhaseMatchResult;
    }

    private void applyFailOn(NormalPhaseMatchResult normalPhaseMatchResult, FailOn failOn, String compilationOutput) {
        if (failOn != null) {
            FailOnMatchResult matchResult = failOn.apply(compilationOutput);
            if (matchResult.fail()) {
                normalPhaseMatchResult.setFailOnMatchResult(matchResult);
            }
        }
    }

    private void applyCounts(NormalPhaseMatchResult normalPhaseMatchResult, Counts counts, String compilationOutput) {
        if (counts != null) {
            CountsMatchResult matchResult = counts.apply(compilationOutput);
            if (matchResult.fail()) {
                normalPhaseMatchResult.setCountsMatchResult(matchResult);
            }
        }
    }
}
