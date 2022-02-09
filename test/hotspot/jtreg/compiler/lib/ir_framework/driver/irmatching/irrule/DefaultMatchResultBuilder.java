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

/**
 * Class to create and add compile phase match result for the default phase which needs some special care.
 */
class DefaultMatchResultBuilder {

    private final CompilePhaseMatchResult defaultMatchResult;
    private final CompilePhaseMatchResult idealResult;
    private final CompilePhaseMatchResult optoAssemblyResult;
    private final int defaultMatchedNodesCount;
    private final int idealMatchedNodesCount;
    private final int optoAssemblyMatchedNodesCount;

    DefaultMatchResultBuilder(CompilePhaseMatchResult defaultMatchResult, CompilePhaseMatchResult idealResult, CompilePhaseMatchResult optoAssemblyResult) {
        this.defaultMatchResult = defaultMatchResult;
        this.idealResult = idealResult;
        this.optoAssemblyResult = optoAssemblyResult;
        this.defaultMatchedNodesCount = defaultMatchResult.getTotalMatchedNodesCount();
        this.idealMatchedNodesCount = idealResult.getTotalMatchedNodesCount();
        this.optoAssemblyMatchedNodesCount = optoAssemblyResult.getTotalMatchedNodesCount();
    }

    /**
     * Report either PrintIdeal, PrintOpto or both if there is at least one match or
     */
    public void createDefaultResult(IRRuleMatchResult irRuleMatchResult) {
        if (noDefaultRegexMatches() || someRegexMatchOnlyEntireOutput()) {
            // Report with default if single regex matches only both outputs or if we could not match anything (could
            // happen with a count constraint that expect a non-zero number of matches)
            irRuleMatchResult.addCompilePhaseMatchResult(defaultMatchResult);
        } else if (anyRegexMatchOnIdealAndOptoAssembly()) {
            if (idealResult.fail()) {
                addIdealResult(irRuleMatchResult);
            }
            if (optoAssemblyResult.fail()) {
                addOptoAssemblyResult(irRuleMatchResult);
            }
        } else if (noOptoAssemblyRegexMatches()) {
            // Report ideal result if no matches on PrintOptoAssembly. We assume no overlapping regexes.
            addIdealResult(irRuleMatchResult);
        } else {
            addOptoAssemblyResult(irRuleMatchResult);
        }
    }

    private boolean noDefaultRegexMatches() {
        return defaultMatchedNodesCount == 0;
    }

    private void addIdealResult(IRRuleMatchResult irRuleMatchResult) {
        irRuleMatchResult.addCompilePhaseMatchResult(idealResult);
    }


    private void addOptoAssemblyResult(IRRuleMatchResult irRuleMatchResult) {
        irRuleMatchResult.addCompilePhaseMatchResult(optoAssemblyResult);
    }

    private boolean noOptoAssemblyRegexMatches() {
        return optoAssemblyMatchedNodesCount == 0;
    }

    /**
     * Do we have a regex that is only matched on the entire ideal + opto assembly output?
     */
    private boolean someRegexMatchOnlyEntireOutput() {
        return defaultMatchedNodesCount != idealMatchedNodesCount + optoAssemblyMatchedNodesCount;
    }

    /**
     * Do we have a match on ideal and opto assembly for this rule?
     */
    private boolean anyRegexMatchOnIdealAndOptoAssembly() {
        return idealMatchedNodesCount > 0 && optoAssemblyMatchedNodesCount > 0;
    }
}
