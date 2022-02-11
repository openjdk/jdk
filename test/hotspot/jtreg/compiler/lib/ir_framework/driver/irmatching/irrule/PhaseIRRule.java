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
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.parser.DefaultPhaseNodeRegexParser;
import compiler.lib.ir_framework.driver.irmatching.parser.NodeRegexParser;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PhaseIRRule {
    private final IRMethod irMethod;
    private final int ruleId;
    private final IR irAnno;
    private final FailOn failOn;
    private final Counts counts;
    private final CompilePhase compilePhase; // TODO: wrong, create new class IRRuleForPhase to have single failON
    private final NodeRegexParser nodeRegexParser;
    private final DefaultPhaseNodeRegexParser defaultPhaseNodeRegexParser;

    public PhaseIRRule(IRMethod irMethod, int ruleId, IR irAnno, CompilePhase compilePhase) {
        this.irMethod = irMethod;
        this.ruleId = ruleId;
        this.irAnno = irAnno;
        this.compilePhase = compilePhase;
        this.nodeRegexParser = new NodeRegexParser();
        this.defaultPhaseNodeRegexParser = new DefaultPhaseNodeRegexParser();
        this.failOnForPhases = initFailOnForPhases(irAnno);
        this.countsForPhases = initCountsForPhases(irAnno);
    }

    private List<FailOn> initFailOnForPhases(IR irAnno) {
        String[] failOnNodes = irAnno.failOn();
        if (failOnNodes.length != 0) {
            List<FailOn> failOnList = new ArrayList<>();
            for (CompilePhase compilePhase : this.compilePhases) {
                createFailOnForPhase(irAnno, failOnList, compilePhase);
            }
            TestFramework.check(!failOnList.isEmpty(), "must be non-empty");
            return failOnList;
        } else {
            return null;
        }
    }

    private void createFailOnForPhase(IR irAnno, List<FailOn> failOnList, CompilePhase compilePhase) {
        if (compilePhase == CompilePhase.DEFAULT) {
            createFailOnForDefaultPhase(irAnno, failOnList);
        } else {
            createFailOnForNonDefaultPhase(irAnno, failOnList, compilePhase);
        }
    }

    private void createFailOnForNonDefaultPhase(IR irAnno, List<FailOn> failOnList, CompilePhase compilePhase) {
        ParsedNodeList list = nodeRegexParser.getNodesFromFailOnRegexes(irAnno.failOn(), compilePhase);
        failOnList.add(createFailOn(list, compilePhase));
    }

    private void createFailOnForDefaultPhase(IR irAnno, List<FailOn> failOnList) {
        DefaultPhaseParsedNodeList list = defaultPhaseNodeRegexParser.getNodesFromFailOnRegexes(irAnno.failOn());
        if (list.requiresDefaultPhase()) {
            failOnList.add(createFailOn(list, CompilePhase.DEFAULT));
        } else {
            list.getCompilePhases().forEach(compilePhase -> failOnList.add(createFailOn(list, compilePhase)));
        }
    }

    private FailOn createFailOn(AbstractParsedNodeList list, CompilePhase phase) {
        return new FailOn(list.getParsedNodes(), phase);
    }


    private List<Counts> initCountsForPhases(IR irAnno) {
        String[] countsConstraints = irAnno.counts();
        if (countsConstraints.length != 0) {
            List<Counts> countsList = new ArrayList<>();
            for (CompilePhase compilePhase : this.compilePhases) {
                createCountsForPhase(irAnno, countsList, compilePhase);
            }
            TestFramework.check(!countsList.isEmpty(), "must be non-empty");
            return countsList;
        } else {
            return null;
        }
    }


    private void createCountsForPhase(IR irAnno, List<Counts> countsList, CompilePhase compilePhase) {
        if (compilePhase == CompilePhase.DEFAULT) {
            createCountsForDefaultPhase(irAnno, countsList);
        } else {
            createCountsForNonDefaultPhase(irAnno, countsList, compilePhase);
        }
    }

    private void createCountsForNonDefaultPhase(IR irAnno, List<Counts> countsList, CompilePhase compilePhase) {
        ParsedNodeList list = nodeRegexParser.getNodesFromCountsRegexes(irAnno.counts(), compilePhase);
        countsList.add(createCounts(list, compilePhase));
    }

    private void createCountsForDefaultPhase(IR irAnno, List<Counts> countsList) {
        DefaultPhaseParsedNodeList list = defaultPhaseNodeRegexParser.getNodesFromCountsRegexes(irAnno.counts());
        if (list.requiresDefaultPhase()) {
            countsList.add(createCounts(list, CompilePhase.DEFAULT));
        } else {
            list.getCompilePhases().forEach(compilePhase -> countsList.add(createCounts(list, compilePhase)));
        }
    }

    private Counts createCounts(AbstractParsedNodeList list, CompilePhase phase) {
        try {
            return Counts.create(list.getParsedNodes(), phase, this);
        } catch (TestFormatException e) {
            // Logged and reported later. Continue.
        }
        return null;
    }

    public int getRuleId() {
        return ruleId;
    }

    public IR getIRAnno() {
        return irAnno;
    }

    public Method getMethod() {
        return irMethod.getMethod();
    }

    /**
     * Apply this IR rule by checking any failOn and counts attributes.
     */
    public IRRuleMatchResult applyCheckAttributesForPhases() {
        IRRuleMatchResult irRuleMatchResult = new IRRuleMatchResult(this);
        for (int i = 0; i < compilePhases.size(); i++) {
            CompilePhase compilePhase = compilePhases.get(i);
            CompilePhaseMatchResult compilePhaseMatchResult = applyCheckAttributes(compilePhase, i);
            if (compilePhaseMatchResult.fail()) {
                if (compilePhase == CompilePhase.DEFAULT) {
                    addDefaultResult(irRuleMatchResult, compilePhaseMatchResult, i);
                } else {
                    irRuleMatchResult.addCompilePhaseMatchResult(compilePhaseMatchResult);
                }
            }
        }
        return irRuleMatchResult;
    }

    /**
     * Report either PrintIdeal, PrintOpto or both if there is at least one match or
     */
    private void addDefaultResult(IRRuleMatchResult irRuleMatchResult, CompilePhaseMatchResult defaultMatchResult, int phaseIndex) {
        CompilePhaseMatchResult idealResult = applyCheckAttributes(CompilePhase.PRINT_IDEAL, phaseIndex);
        CompilePhaseMatchResult optoAssemblyResult = applyCheckAttributes(CompilePhase.PRINT_OPTO_ASSEMBLY, phaseIndex);
        DefaultMatchResultBuilder resultBuilder = new DefaultMatchResultBuilder(defaultMatchResult, idealResult,
                                                                                optoAssemblyResult);
        resultBuilder.createDefaultResult(irRuleMatchResult);
    }


    private CompilePhaseMatchResult applyCheckAttributes(CompilePhase compilePhase, int phaseIndex) {
        CompilePhaseMatchResult compilePhaseMatchResult = new CompilePhaseMatchResult(compilePhase);
        if (failOnForPhases != null) {
            applyFailOn(failOnForPhases.get(phaseIndex), compilePhase, compilePhaseMatchResult);
        }
        if (countsForPhases != null) {
            applyCounts(countsForPhases.get(phaseIndex), compilePhase, compilePhaseMatchResult);
        }
        return compilePhaseMatchResult;
    }

    private void applyFailOn(FailOn failOn, CompilePhase compilePhase, CompilePhaseMatchResult compilePhaseMatchResult) {
        FailOnMatchResult matchResult = failOn.apply(irMethod.getOutput(compilePhase));
        if (matchResult.fail()) {
            compilePhaseMatchResult.setFailOnMatchResult(matchResult);
        }
    }

    private void applyCounts(Counts counts, CompilePhase compilePhase, CompilePhaseMatchResult compilePhaseMatchResult) {
        CountsMatchResult matchResult = counts.apply(irMethod.getOutput(compilePhase));
        if (matchResult.fail()) {
            compilePhaseMatchResult.setCountsMatchResult(matchResult);
        }
    }
}
