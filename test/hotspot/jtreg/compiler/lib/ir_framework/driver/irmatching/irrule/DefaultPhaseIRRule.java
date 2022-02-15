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
import compiler.lib.ir_framework.driver.irmatching.parser.CountsNodeRegex;
import compiler.lib.ir_framework.driver.irmatching.parser.FailOnNodeRegex;

import java.util.List;

public class DefaultPhaseIRRule extends AbstractCompilePhaseIRRule {

    private final FailOn defaultFailOn;
    private final Counts defaultCounts;
    private final FailOn idealFailOn;
    private final Counts idealCounts;
    private final FailOn optoAssemblyFailOn;
    private final Counts optoAssemblyCounts;

    public DefaultPhaseIRRule(IRMethod irMethod, List<FailOnNodeRegex> failOnNodeRegexes, List<CountsNodeRegex> countsNodeRegexes) {
        super(irMethod, CompilePhase.DEFAULT);
        NodeRegexFilter<FailOnNodeRegex> failOnFilter = new NodeRegexFilter<>(failOnNodeRegexes);
        NodeRegexFilter<CountsNodeRegex> countsFilter = new NodeRegexFilter<>(countsNodeRegexes);
        defaultFailOn = initFailOn(failOnFilter, CompilePhase.DEFAULT);
        idealFailOn = initFailOn(failOnFilter, CompilePhase.PRINT_IDEAL);
        optoAssemblyFailOn = initFailOn(failOnFilter, CompilePhase.PRINT_OPTO_ASSEMBLY);
        defaultCounts = initCounts(countsFilter, CompilePhase.DEFAULT);
        idealCounts = initCounts(countsFilter, CompilePhase.PRINT_IDEAL);
        optoAssemblyCounts = initCounts(countsFilter, CompilePhase.PRINT_OPTO_ASSEMBLY);
    }

    private FailOn initFailOn(NodeRegexFilter<FailOnNodeRegex> failOnFilter, CompilePhase compilePhase) {
        return FailOnNodeRegexParser.parse(failOnFilter.getList(compilePhase), compilePhase);
    }


    private Counts initCounts(NodeRegexFilter<CountsNodeRegex> countsFilter, CompilePhase compilePhase) {
        return CountsNodeRegexParser.parse(countsFilter.getList(compilePhase), compilePhase);
    }

    /**
     * Apply this IR rule by checking any failOn and counts attributes.
     */
    @Override
    public DefaultPhaseMatchResult applyCheckAttributes() {
        DefaultPhaseMatchResult defaultPhaseMatchResult = new DefaultPhaseMatchResult();
        applyPhase(defaultPhaseMatchResult, idealFailOn, idealCounts, CompilePhase.PRINT_IDEAL);
        applyPhase(defaultPhaseMatchResult, optoAssemblyFailOn, optoAssemblyCounts, CompilePhase.PRINT_OPTO_ASSEMBLY);
        applyDefaultPhase(defaultPhaseMatchResult);
        return defaultPhaseMatchResult;
    }

    private void applyPhase(DefaultPhaseMatchResult defaultPhaseMatchResult, FailOn failOn, Counts counts, CompilePhase compilePhase) {
        NormalPhaseMatchResult normalPhaseMatchResult = applyCheckAttributes(failOn, counts, compilePhase);
        if (normalPhaseMatchResult.fail()) {
            defaultPhaseMatchResult.addResult(normalPhaseMatchResult);
        }
    }

    private void applyDefaultPhase(DefaultPhaseMatchResult defaultPhaseMatchResult) {
        NormalPhaseMatchResult normalPhaseMatchResult = applyCheckAttributes(defaultFailOn, defaultCounts, CompilePhase.DEFAULT);
        if (normalPhaseMatchResult.fail()) {
            addDefaultMatchResult(defaultPhaseMatchResult, normalPhaseMatchResult);
        }
    }


    /**
     * Report either PrintIdeal, PrintOpto or both if there is at least one match or
     */
    private void addDefaultMatchResult(DefaultPhaseMatchResult defaultPhaseMatchResult, NormalPhaseMatchResult failedDefaultPhaseMatchResult) {
        NormalPhaseMatchResult idealResult = applyCheckAttributes(defaultFailOn, defaultCounts, CompilePhase.PRINT_IDEAL);
        NormalPhaseMatchResult optoAssemblyResult = applyCheckAttributes(defaultFailOn, defaultCounts, CompilePhase.PRINT_OPTO_ASSEMBLY);
        DefaultMatchResultMerger merger = new DefaultMatchResultMerger(failedDefaultPhaseMatchResult, idealResult, optoAssemblyResult);
        merger.mergeDefaultMatchResults(defaultPhaseMatchResult);
    }
}
