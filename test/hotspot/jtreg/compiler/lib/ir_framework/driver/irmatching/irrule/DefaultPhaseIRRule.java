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
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOn;

public class DefaultPhaseIRRule extends CompilePhaseIRRule {

    public DefaultPhaseIRRule(IRMethod irMethod, FailOn failOn, Counts counts) {
        super(irMethod, CompilePhase.DEFAULT, failOn, counts);
    }

    @Override
    public CompilePhaseMatchResult applyCheckAttributes() {
        CompilePhaseMatchResult compilePhaseMatchResult = applyCheckAttributes(CompilePhase.DEFAULT);
        if (compilePhaseMatchResult.fail()) {
            return replaceCompilePhaseMatchResult(compilePhaseMatchResult);
        }
        return compilePhaseMatchResult;
    }

    private CompilePhaseMatchResult replaceCompilePhaseMatchResult(CompilePhaseMatchResult resultDefault) {
        CompilePhaseMatchResult resultIdeal = applyCheckAttributes(CompilePhase.PRINT_IDEAL);
        CompilePhaseMatchResult resultOptoAssembly = applyCheckAttributes(CompilePhase.PRINT_OPTO_ASSEMBLY);
        int totalMatchesDefault = resultDefault.getTotalMatchedNodesCount();
        int totalMatchesIdeal = resultIdeal.getTotalMatchedNodesCount();
        int totalMatchesOptoAssembly = resultOptoAssembly.getTotalMatchedNodesCount();
        if (totalMatchesDefault == 0) {
            // No match? Report with PrintIdeal and PrintOptoAssembly (we do not know which should have been matched).
            return resultDefault;
        } else if (totalMatchesIdeal == totalMatchesDefault) {
            // Only PrintIdeal matches.
            return resultIdeal;
        } else if (totalMatchesOptoAssembly == totalMatchesDefault) {
            // Only PrintOptoAssembly matches
            return resultOptoAssembly;
        } else {
            // Either matched on PrintIdeal AND PrintOptoAssembly or on combined output (discouraged).
            return resultDefault;
        }
    }
}
