/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.irmethod;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRule;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class to store information about a method that needs to be IR matched.
 */
public class IRMethod {
    private final Method method;
    private final List<IRRule> irRules;
    private String completeOutput;
    private String optoAssemblyOutput;
    private final Map<CompilePhase, String> outputMap;

    public IRMethod(Method method, int[] ruleIds, IR[] irAnnos) {
        this.method = method;
        this.irRules = new ArrayList<>();
        for (int ruleId : ruleIds) {
            irRules.add(new IRRule(this, ruleId, irAnnos[ruleId - 1]));
        }
        this.completeOutput = "";
        this.optoAssemblyOutput = "";
        this.outputMap = new LinkedHashMap<>(); // Keep order of insertion
    }

    public Method getMethod() {
        return method;
    }

    /**
     * Return the entire compilation output of all phases, regardless of IR matching failures.
     */
    public String getCompleteOutput() {
        if (completeOutput.isEmpty()) {
            completeOutput = createCompleteOutput();
        }
        return completeOutput;
    }

    private String createCompleteOutput() {
        String idealOutputs = outputMap.entrySet().stream()
                                       .filter(e -> e.getKey() == CompilePhase.PRINT_OPTO_ASSEMBLY)
                                       .map(Map.Entry::getValue)
                                       .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
        if (!optoAssemblyOutput.isEmpty()) {
            // PrintOptoAssembly output is reported before the PrintIdeal output of PHASE_FINAL.
            // Put PrintOptoAssembly output last.
            return idealOutputs + System.lineSeparator() + System.lineSeparator() + optoAssemblyOutput;
        }
        return idealOutputs;
    }

    public String getOutput(CompilePhase phase) {
        return outputMap.get(phase);
    }

    /**
     * We might parse multiple C2 compilations of this method. Only keep the very last one by overriding the outputMap.
     */
    public void setIdealOutput(String idealOutput, CompilePhase phase) {
        String idealOutputWithHeader = "PrintIdeal" + getPhaseNameString(phase) + ":" + System.lineSeparator() + idealOutput;
        outputMap.put(phase, idealOutputWithHeader);
        if (phase == CompilePhase.PRINT_IDEAL) {
            outputMap.put(CompilePhase.DEFAULT, idealOutputWithHeader);
        }
    }

    private String getPhaseNameString(CompilePhase phase) {
        return " - " + phase.getName();
    }

    /**
     * We might parse multiple C2 compilations of this method. Only keep the very last one by overriding the outputMap.
     */
    public void setOptoAssemblyOutput(String optoAssemblyOutput) {
        this.optoAssemblyOutput = "PrintOptoAssembly:" + System.lineSeparator() + optoAssemblyOutput;
        outputMap.put(CompilePhase.PRINT_OPTO_ASSEMBLY, this.optoAssemblyOutput);
        String idealOutput = outputMap.get(CompilePhase.DEFAULT);
        TestFramework.check(idealOutput != null && !idealOutput.isEmpty(), "must be non-empty");
        outputMap.put(CompilePhase.DEFAULT, idealOutput + System.lineSeparator() + System.lineSeparator()
                                            + this.optoAssemblyOutput);
    }

    /**
     * Apply all IR rules of this IR method on their specified compile phases.
     */
    public IRMethodMatchResult applyIRRules() {
        TestFramework.check(!irRules.isEmpty(), "IRMethod cannot be created if there are no IR rules to apply");
        List<IRRuleMatchResult> results = new ArrayList<>();
        if (getCompleteOutput().isEmpty()) {
            return new MissingCompilationResult(this, irRules.size());
        } else {
            return getNormalMatchResult(results);
        }
    }

    private NormalMatchResult getNormalMatchResult(List<IRRuleMatchResult> results) {
        for (IRRule irRule : irRules) {
            IRRuleMatchResult result = irRule.applyCheckAttributesForPhases();
            if (result.fail()) {
                results.add(result);
            }
        }
        return new NormalMatchResult(this, results);
    }
}
